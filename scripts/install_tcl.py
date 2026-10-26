#!/usr/bin/env python3
"""Preview/install Quiet TV on TCL; an explicit opt-in permits a temporary verifier stop."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import uuid
import zipfile

PACKAGE = "home.quiet.tv"
INSTALLER = "com.android.packageinstaller"
PROPERTIES = ("ro.product.brand", "ro.product.manufacturer", "ro.product.model",
              "ro.product.device", "ro.build.version.sdk", "ro.product.cpu.abilist",
              "ro.product.cpu.abi", "ro.build.display.id", "ro.build.id",
              "ro.build.fingerprint", "ro.serialno")


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def checked(result):
    output = (result.stdout + "\n" + result.stderr).strip()
    if result.returncode or re.search(r"(?im)^\s*(?:error:|failure\b|exception|securityexception|unknown command)", output):
        raise ValueError(output or f"命令失败，退出码 {result.returncode}")
    return output


class Adb:
    def __init__(self, executable, serial):
        self.executable, self.serial = executable, serial

    def run(self, *args, timeout=20):
        return checked(subprocess.run([self.executable, "-s", self.serial, *map(str, args)],
                                      capture_output=True, text=True, encoding="utf-8",
                                      errors="replace", timeout=timeout, check=False))

    def shell(self, *args):
        return self.run("shell", " ".join(shlex.quote(str(arg)) for arg in args))

    def current_user(self):
        value = self.shell("am", "get-current-user")
        if not re.fullmatch(r"\d+", value):
            raise ValueError("未能核对前台用户")
        return int(value)

    def identity(self):
        if self.run("get-state") != "device" or self.shell("id", "-u") != "2000":
            raise ValueError("需要已授权的普通 shell ADB 连接")
        return {name: self.shell("getprop", name) for name in PROPERTIES}


def command_supports_user(help_text, name):
    lines = help_text.splitlines()
    for index, line in enumerate(lines):
        if re.match(r"^\s*" + re.escape(name) + r"(?:\s|$)", line):
            indent = len(line) - len(line.lstrip())
            block = [line]
            for following in lines[index + 1:]:
                if following.strip() and len(following) - len(following.lstrip()) <= indent:
                    break
                block.append(following)
            return "--user" in " ".join(block)
    return False


def user_state(output, user):
    match = re.search(r"(?m)^\s*User " + str(user) + r":([^\n]+)", output)
    if not match:
        return None
    fields = dict(re.findall(r"\b(installed|hidden|suspended|enabled)=([^\s]+)", match[1]))
    if "enabled" not in fields or "installed" not in fields:
        raise ValueError("应用用户状态不完整")
    fields["enabled"] = int(fields["enabled"])
    return fields


def usable(state):
    return (state is not None and state["installed"] == "true"
            and state["enabled"] in (0, 1)
            and state.get("hidden") != "true" and state.get("suspended") != "true")


def sdk_tool(name, explicit=None):
    if explicit:
        return explicit
    found = shutil.which(name)
    if found:
        return found
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        local = Path(__file__).resolve().parents[1] / "local.properties"
        if local.exists():
            match = re.search(r"(?m)^sdk.dir=(.+)$", local.read_text())
            sdk = match[1] if match else None
    if sdk:
        suffix = ".bat" if os.name == "nt" and name == "apksigner" else ".exe" if os.name == "nt" else ""
        candidates = sorted((Path(sdk) / "build-tools").glob("*/" + name + suffix),
                            key=lambda path: [int(n) for n in re.findall(r"\d+", path.parent.name)])
        if candidates:
            return str(candidates[-1])
    raise ValueError(f"找不到 {name}；请用 --{name} 指定现有 Android build-tools 工具")


class ApkTools:
    def __init__(self, aapt2, apksigner):
        self.aapt2, self.apksigner = aapt2, apksigner

    def inspect(self, apk):
        def run(*args):
            return checked(subprocess.run(list(args), capture_output=True, text=True,
                                          encoding="utf-8", errors="replace", timeout=60, check=False))
        badging = run(self.aapt2, "dump", "badging", str(apk))
        package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
        minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
        if not package or not minimum:
            raise ValueError("APK 包名、版本或最低 API 无法核对")
        signature = run(self.apksigner, "verify", "--min-sdk-version", minimum[1], "--print-certs", str(apk))
        signers = sorted(re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})", signature))
        if not signers:
            raise ValueError("APK 签名验证未返回证书")
        with zipfile.ZipFile(apk) as archive:
            abis = sorted({name.split('/')[1] for name in archive.namelist()
                           if name.startswith("lib/") and name.endswith(".so")})
        return {"package": package[1], "version_code": int(package[2]), "version_name": package[3],
                "min_sdk": int(minimum[1]), "signers": signers, "abis": abis, "sha256": digest(apk)}


class Journal:
    def __init__(self, directory):
        self.directory = Path(directory)
        self.directory.mkdir(mode=0o700, parents=False, exist_ok=False)
        self.path = self.directory / "record.jsonl"
        self.fd = os.open(self.path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_APPEND, 0o600)

    def event(self, event, **values):
        data = (json.dumps({"event": event, **values}, ensure_ascii=False) + "\n").encode()
        with os.fdopen(os.dup(self.fd), "ab") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())

    def close(self):
        os.close(self.fd)


def installed_path(adb, user):
    lines = adb.shell("pm", "path", "--user", user, PACKAGE).splitlines()
    paths = [line[8:] for line in lines if line.startswith("package:")]
    if len(paths) != 1 or not paths[0].startswith("/"):
        raise ValueError("当前包路径未确认或包含 split APK，停止单 APK 安装")
    return paths[0]


def pull_apk(adb, user, destination):
    adb.run("pull", installed_path(adb, user), str(destination), timeout=120)


def launcher_rejection(logs, marker):
    # Only evidence after this run's marker, referring to this package, may authorize the fallback.
    if logs.count(marker) != 1:
        return False
    recent = logs.split(marker, 1)[1]
    return (PACKAGE in recent and "LauncherStrategy" in recent and "com.tcl.cyberui" in recent
            and bool(re.search(r"not support tcl ui tab|signature (?:does not|not) match", recent, re.I)))


def restore_installer(adb, user, before):
    command = "default-state" if before["enabled"] == 0 else "enable"
    adb.shell("pm", command, "--user", user, INSTALLER)
    after = user_state(adb.shell("dumpsys", "package", INSTALLER), user)
    if after != before:
        raise ValueError("安装器原状态读回不符")
    return after


def temporary_install(adb, args, before, journal):
    # Mark restoration pending on disk BEFORE the first possible device change.
    journal.event("installer_restore_pending", before=before)
    try:
        adb.shell("pm", "disable-user", "--user", args.user, INSTALLER)
        disabled = user_state(adb.shell("dumpsys", "package", INSTALLER), args.user)
        if not disabled or disabled["enabled"] != 3:
            raise ValueError("安装器停用未确认，未重试安装")
        if adb.current_user() != args.user:
            raise ValueError("前台用户已改变，未重试安装")
        if digest(args.apk) != args.sha256:
            raise ValueError("本地 APK 在执行期间发生变化，未重试安装")
        result = adb.run("install", "-r", "--user", args.user, str(args.apk), timeout=180)
        if not re.search(r"(?m)^Success\s*$", result):
            raise ValueError("安装未确认：" + result)
        journal.event("install_command_success", output=result)
    finally:
        # Includes failure, timeout, cancellation and an ambiguous disable response.
        try:
            after = restore_installer(adb, args.user, before)
        except BaseException as error:
            journal.event("installer_restore_unconfirmed", error=str(error))
            raise ValueError(f"安装器恢复待确认，请保留 {journal.path} 并用 --restore-record 优先恢复：{error}") from error
        journal.event("installer_restored", after=after)


def install(args, adb, tools, journal):
    device = adb.identity()
    if not re.search(r"\b(?:tcl|ffalcon|iffalcon)\b", device["ro.product.brand"] + " " + device["ro.product.manufacturer"], re.I):
        raise ValueError("此入口仅处理已读取为 TCL／雷鸟的设备")
    if adb.current_user() != args.user:
        raise ValueError("指定用户不是当前前台用户")
    help_text = adb.shell("pm", "help")
    for operation in ("install", "path", "resolve-activity"):
        if not command_supports_user(help_text, operation):
            raise ValueError(f"本机未确认 {operation} 支持指定用户")
    apk = tools.inspect(args.apk)
    if apk["package"] != PACKAGE or apk["sha256"] != args.sha256:
        raise ValueError("包名或 SHA-256 与选定 Quiet TV APK 不符")
    if apk["min_sdk"] > int(device["ro.build.version.sdk"]):
        raise ValueError("APK 最低 API 高于电视系统")
    abis = (device["ro.product.cpu.abilist"] or device["ro.product.cpu.abi"]).split(',')
    if apk["abis"] and not set(apk["abis"]).intersection(abis):
        raise ValueError("APK 原生库架构不匹配")
    package_before = adb.shell("dumpsys", "package", PACKAGE)
    state = user_state(package_before, args.user)
    if state and not usable(state):
        raise ValueError("Quiet TV 在该用户上被停用、隐藏或未安装；不自动恢复")
    if state:
        old = journal.directory / "installed-before.apk"
        pull_apk(adb, args.user, old)
        installed = tools.inspect(old)
        if (installed["package"] != PACKAGE or installed["signers"] != apk["signers"]
                or installed["version_code"] > apk["version_code"]):
            raise ValueError("已安装包签名不符或需要降级，未执行安装")
        if installed["version_code"] == apk["version_code"]:
            if installed["sha256"] == apk["sha256"]:
                print("已安装相同 APK，无需重装。")
                return
            raise ValueError("相同构建号但文件不同，未覆盖安装")
    elif re.search(r"(?m)^\s*User \d+:", package_before):
        raise ValueError("其他用户仍有 Quiet TV，不当作全新安装")
    installer_dump = adb.shell("dumpsys", "package", INSTALLER)
    before = user_state(installer_dump, args.user)
    if not usable(before) or not re.search(r"\bSYSTEM\b", installer_dump):
        raise ValueError("本机系统安装器未确认可用，未安装")
    restore = "default-state" if before["enabled"] == 0 else "enable"
    if args.allow_temporary_installer_disable:
        for operation in ("disable-user", restore):
            if not command_supports_user(help_text, operation):
                raise ValueError(f"本机未确认 {operation} 支持指定用户，不能安排安装器恢复")
    home_before = adb.shell("pm", "resolve-activity", "--brief", "--user", args.user,
                            "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
    journal.event("baseline", serial=args.serial, user=args.user, device=device, apk=apk,
                  installer_before=before, installer_dump=installer_dump,
                  package_before=package_before, home_before=home_before)
    if not args.apply:
        print("仅预览，未修改电视。品牌候选匹配；安装器临时处理" + ("已明确允许" if args.allow_temporary_installer_disable else "未授权") + "。")
        print("APK、设备、固件及恢复基线：" + str(journal.path))
        return
    if adb.current_user() != args.user or digest(args.apk) != args.sha256:
        raise ValueError("用户或 APK 已改变，未安装")
    marker = "QuietTVInstall-" + uuid.uuid4().hex
    adb.shell("log", "-p", "i", "-t", "QuietTVInstall", marker)
    journal.event("normal_install_started")
    try:
        output = adb.run("install", "-r", "--user", args.user, str(args.apk), timeout=180)
        if not re.search(r"(?m)^Success\s*$", output):
            raise ValueError(output or "安装结果未确认")
    except ValueError as error:
        journal.event("normal_install_failed", error=str(error))
        if "INSTALL_FAILED_VERIFICATION_FAILURE" not in str(error):
            raise
        logs = adb.run("logcat", "-d", "-v", "brief", "-s", "QuietTVInstall:I", "verifier:V",
                       "System.err:W", "PackageManager:I", "*:S")
        journal.event("verification_evidence", logs=logs)
        if not launcher_rejection(logs, marker):
            raise ValueError("未找到本次 Quiet TV 的 TCL LauncherStrategy 拒绝证据，未停用安装器") from error
        if not args.allow_temporary_installer_disable:
            raise ValueError("已识别 TCL 桌面校验拦截；需明确授权 --allow-temporary-installer-disable 才可临时处理安装器") from error
        current = user_state(adb.shell("dumpsys", "package", INSTALLER), args.user)
        if current != before or adb.current_user() != args.user:
            raise ValueError("安装器状态或前台用户已变化，未停用")
        temporary_install(adb, args, before, journal)
    installed_apk = journal.directory / "installed-after.apk"
    pull_apk(adb, args.user, installed_apk)
    actual = tools.inspect(installed_apk)
    if actual != apk:
        journal.event("installed_apk_mismatch", actual=actual)
        raise ValueError("已安装 APK 校验不符，未启动或切换 HOME")
    journal.event("installed_apk_verified", actual=actual)
    print("安装及已安装 APK 校验完成。未启动应用、未修改 HOME；实体 HOME、重启和待机唤醒未验证。")


def recover(args, adb):
    text = args.restore_record.read_text()
    lines = text.splitlines()
    # A killed process may leave a partial last event. The baseline and pending event were fsynced
    # before disabling, so retain those complete records without rewriting the damaged evidence.
    complete_tail = text.endswith("\n")
    records = [json.loads(line) for line in (lines if complete_tail else lines[:-1]) if line.strip()]
    baseline = next((item for item in records if item.get("event") == "baseline"), None)
    if not baseline or baseline["serial"] != args.serial or baseline["user"] != args.user:
        raise ValueError("恢复记录与指定设备或用户不符")
    pending = [index for index, item in enumerate(records) if item.get("event") == "installer_restore_pending"]
    if not pending:
        raise ValueError("记录中没有本次临时停用，不恢复其他来源的修改")
    device = adb.identity()
    if any(value and device.get(name) != value for name, value in baseline["device"].items()):
        raise ValueError("设备身份或固件与原始记录不符，未恢复")
    before = baseline["installer_before"]
    if not usable(before):
        raise ValueError("记录中的安装器原状态不可恢复")
    restore = "default-state" if before["enabled"] == 0 else "enable"
    if not command_supports_user(adb.shell("pm", "help"), restore):
        raise ValueError("未确认本机支持原状态恢复命令")
    current = user_state(adb.shell("dumpsys", "package", INSTALLER), args.user)
    if current == before:
        print("安装器已与原状态一致，无需修改。")
    elif any(item.get("event") == "installer_restored" for item in records[pending[-1] + 1:]):
        raise ValueError("本次停用已经恢复；当前差异属于后续修改，不自动覆盖")
    elif current is None or {**current, "enabled": before["enabled"]} != before or current["enabled"] != 3:
        raise ValueError("当前状态不只是本次安装器临时停用，未自动恢复")
    elif not args.apply:
        print(f"仅预览：将对记录用户 {args.user} 恢复安装器为 enabled={before['enabled']}；使用 --apply 执行。")
    else:
        after = restore_installer(adb, args.user, before)
        if complete_tail:
            with args.restore_record.open("a", encoding="utf-8") as handle:
                handle.write(json.dumps({"event": "installer_restored", "after": after}, ensure_ascii=False) + "\n")
                handle.flush()
                os.fsync(handle.fileno())
        print("安装器原状态已恢复并读回；未重放安装或修改 HOME。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="用户明确选定且已授权的目标")
    parser.add_argument("--user", type=int, required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--sha256", type=str.lower, help="可信发布来源给出的 APK SHA-256")
    parser.add_argument("--aapt2")
    parser.add_argument("--apksigner")
    parser.add_argument("--record", type=Path, help="新的私有记录目录，不覆盖；保留安装前后 APK")
    parser.add_argument("--restore-record", type=Path, help="只恢复 record.jsonl 中的安装器原状态")
    parser.add_argument("--apply", action="store_true", help="明确执行安装或记录中的安装器恢复；默认只预览")
    parser.add_argument("--allow-temporary-installer-disable", action="store_true",
                        help="仅在本次安装证实 LauncherStrategy 拦截后允许临时停用并恢复系统安装器")
    args = parser.parse_args()
    if args.user < 0:
        parser.error("用户不能为负数")
    if args.restore_record:
        if args.apk or args.sha256 or args.record or args.allow_temporary_installer_disable:
            parser.error("恢复模式不能同时安装或停用安装器")
    elif not (args.apk and args.record and args.sha256 and re.fullmatch(r"[a-f0-9]{64}", args.sha256)):
        parser.error("安装预览或执行均需 --apk、--sha256 和新的 --record 目录")
    journal = None
    try:
        adb = Adb(args.adb, args.serial)
        if args.restore_record:
            recover(args, adb)
        else:
            tools = ApkTools(sdk_tool("aapt2", args.aapt2), sdk_tool("apksigner", args.apksigner))
            journal = Journal(args.record)
            install(args, adb, tools, journal)
        return 0
    except (OSError, ValueError, subprocess.TimeoutExpired, zipfile.BadZipFile, KeyboardInterrupt) as error:
        if journal:
            journal.event("incomplete", error=str(error) or "用户取消")
        print("安装未完成：" + (str(error) or "用户取消"), file=sys.stderr)
        return 1
    finally:
        if journal:
            journal.close()


if __name__ == "__main__":
    sys.exit(main())
