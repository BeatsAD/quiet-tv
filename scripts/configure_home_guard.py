#!/usr/bin/env python3
"""Preview or explicitly configure Quiet TV guard through an already-authorized computer ADB."""

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import uuid

ERROR_OUTPUT = re.compile(r"(?im)^\s*(?:error:|exception|securityexception|failure|bad argument:|operation not allowed:|unknown (?:command|operation|op)\b|invalid operation)")


class CommandFailure(ValueError):
    def __init__(self, exit_code, output):
        self.exit, self.output = exit_code, output
        super().__init__(f"命令失败（{exit_code}）：{output}")


def command_signature(help_text, name):
    lines = help_text.splitlines()
    for index, line in enumerate(lines):
        if not re.match(r"^\s*(?:(?i:usage):\s*)?(?:(?:pm|appops)\s+)?" + re.escape(name) + r"(?:\s|$)", line):
            continue
        indent = len(line) - len(line.lstrip())
        entry = [line.strip()]
        for following in lines[index + 1:]:
            if not following.strip():
                continue
            if (len(following) - len(following.lstrip()) <= indent
                    or re.fullmatch(r"(?:(?i:usage):\s*)?(?:(?:pm|appops)\s+)?[a-z][a-z0-9-]*(?:\s.*)?", following.strip())):
                break
            entry.append(following.strip())
        return " ".join(entry)
    return ""


def identity_uid(output):
    value = output.strip()
    if re.fullmatch(r"[0-9]{1,6}", value):
        return int(value)
    match = re.fullmatch(r"uid=([0-9]{1,6})\([^\r\n)]*\)(?:[ \t]+[^\r\n]*)?", value)
    return int(match[1]) if match else -1


def verify_identity(adb, sdk):
    try:
        output = adb.shell("id", "-u")
    except ValueError:
        output = adb.shell("id")
    uid = identity_uid(output)
    if uid == 2000:
        return "shell"
    identity = adb.shell("id")
    if uid < 0:
        uid = identity_uid(identity)
    if uid == 2000:
        return "shell"
    # Match the submitted production VIDAA firmware, not arbitrary root/engineering ADB.
    if (uid == 0 and sdk == 22 and identity_uid(identity) == 0
            and re.search(r"\bcontext=u:r:hshell:s0\s*$", identity)
            and adb.shell("getprop", "ro.product.brand").lower() == "hisense"
            and adb.shell("getprop", "ro.product.manufacturer").lower() == "hisense"
            and adb.shell("getprop", "ro.product.model") == "VIDAA_TV"):
        fingerprint = adb.shell("getprop", "ro.build.fingerprint")
        if (fingerprint.startswith("Hisense/VIDAA_TV/MSD6A828:5.1.1/LVY48C/")
                and fingerprint.endswith(":user/release-keys")
                and adb.shell("getprop", "ro.secure") == "1"
                and adb.shell("getprop", "ro.debuggable") == "0"):
            return "hisense_hshell"
    raise ValueError("ADB 身份不受支持；仅接受普通 shell 或已匹配的海信 VIDAA 工厂接口")


def user_options(help_text, operation, sdk, user):
    signature = command_signature(help_text, operation)
    if not signature:
        raise ValueError("本机帮助未确认授权命令，未修改权限")
    if "--user" in signature:
        return ["--user", user]
    if sdk <= 23 and user == 0:
        return []  # Old tools operate on the verified owner user only.
    raise ValueError("本机命令不能明确作用于目标用户，未修改权限")


def broadcast_data(output):
    match = re.search(r'\bdata=(.*?)(?:, extras:.*)?$', output.strip())
    if not match:
        return None
    value = match[1]
    try:
        if value.startswith('"') and value.endswith('"'):
            try:
                value = json.loads(value)
            except ValueError:
                value = value[1:-1]
        result = json.loads(value)
        return result if isinstance(result, dict) else None
    except ValueError:
        return None


def appop_allowed(output, operation):
    return bool(re.search(r'(?m)^\s*' + re.escape(operation) + r':\s*allow(?:[;\s]|$)', output))


def canonical_home(value):
    if not value or "/" not in value:
        return value
    package, activity = value.split("/", 1)
    return package + "/" + (package + activity if activity.startswith(".") else activity)


class ComputerAdb:
    def __init__(self, executable, serial):
        self.executable, self.serial = executable, serial

    def _result(self, *arguments):
        return subprocess.run([self.executable, "-s", self.serial, *arguments],
                              capture_output=True, text=True, encoding="utf-8",
                              errors="replace", timeout=20, check=False)

    def run(self, *arguments):
        result = self._result(*arguments)
        output = (result.stdout + "\n" + result.stderr).strip()
        if result.returncode or ERROR_OUTPUT.search(output):
            raise ValueError(output or "ADB 命令失败，状态未确认")
        return output

    def shell(self, *arguments):
        # adb joins shell arguments; quote for Android's POSIX shell, including on Windows hosts.
        command = " ".join(shlex.quote(str(value)) for value in arguments)
        marker = "__QUIET_GUARD_EXIT_" + uuid.uuid4().hex + "="
        result = self._result("shell", "( " + command + " ); quiet_guard_status=$?; echo; echo \"" + marker + "$quiet_guard_status\"")
        output = (result.stdout + "\n" + result.stderr).strip()
        if result.returncode:
            raise ValueError(output or "ADB 传输失败，状态未确认")
        status = re.search(r"(?:^|\n)" + re.escape(marker) + r"([0-9]+)\s*$", output)
        if status is None:
            raise ValueError("ADB 结果不完整，停止后续操作；请重新查询状态")
        body, exit_code = output[:status.start()].strip(), int(status[1])
        if exit_code or ERROR_OUTPUT.search(body):
            raise CommandFailure(exit_code, body)
        return body

    def permission_help(self, tool):
        operation = {"pm": "grant", "appops": "set"}[tool]
        for arguments in ((tool, "help"), (tool,)):
            failure = None
            try:
                output = self.shell(*arguments)
            except CommandFailure as error:
                failure, output = error, error.output
            if command_signature(output, operation):
                return output
            if len(arguments) == 2 and re.search(r"(?i)\bunknown command\b[^\r\n]*\bhelp\b", output):
                continue
            if failure is not None:
                raise failure
            break
        raise ValueError("系统帮助未确认 " + tool + " " + operation + " 命令")

    def current_user(self):
        try:
            value = self.shell("am", "get-current-user")
        except ValueError:
            value = ""
        if re.fullmatch(r"[0-9]+", value):
            return int(value)
        activity = self.shell("dumpsys", "activity", "activities")
        users = set(re.findall(r"mResumedActivity:\s*ActivityRecord\{[^\r\n}]*\bu([0-9]+)\b", activity))
        if len(users) != 1:
            raise ValueError("无法唯一核对前台用户；请先在电视打开 Quiet TV")
        return int(users.pop())


def configure(args, adb):
    if adb.run("get-state") != "device":
        raise ValueError("需要已授权的 ADB 连接")
    sdk = int(adb.shell("getprop", "ro.build.version.sdk"))
    profile = verify_identity(adb, sdk)
    if adb.current_user() != args.user:
        raise ValueError("指定用户不是当前前台用户")
    if sdk < 21:
        raise ValueError("Android 4.4 档位未开放桌面守护")
    package = adb.shell("dumpsys", "package", args.package)
    user_state = re.search(r"(?m)^\s*User " + str(args.user) + r":([^\n]+)", package)
    if not user_state or not re.search(r"\binstalled=true\b", user_state[1]) or re.search(r"\b(?:hidden|suspended)=true\b|\benabled=[2-9]\b", user_state[1]):
        raise ValueError("当前用户上的 Quiet TV 未安装、被隐藏或已停用")
    if "home.quiet.tv.HomeGuardSetupReceiver" not in package:
        raise ValueError("已安装 APK 不包含电脑守护配置入口；请先核对并更新到包含本修复的版本")
    if args.pause:
        command = ["am", "broadcast", "--include-stopped-packages", "--user", args.user,
                   "-a", args.package + ".CONFIGURE_HOME_GUARD", "-n", args.package + "/home.quiet.tv.HomeGuardSetupReceiver",
                   "--ei", "user", args.user, "--ez", "pause", "true"]
        if not args.apply:
            print("仅预览，未修改电视：shell " + " ".join(shlex.quote(str(value)) for value in command))
            return
        descriptor = os.open(args.record, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump({"serial": args.serial, "user": args.user, "package_before": package, "plan": [command]}, handle, ensure_ascii=False, indent=2)
        if adb.current_user() != args.user:
            raise ValueError("前台用户已改变，未暂停")
        output = adb.shell(*command)
        print(output)
        if not re.search(r"\bresult=-1\b", output):
            raise ValueError("应用未确认暂停；已安装 APK 可能不支持该入口")
        print("中转已暂停；系统默认 HOME 保持原状，原桌面保护不解除。")
        return
    direct_home = getattr(args, "direct_home", False)
    if direct_home and args.home:
        raise ValueError("直接 HOME 补偿不能同时指定原桌面")
    selected = canonical_home(args.home or "")
    inspection = None
    if args.apply or direct_home:
        # New APKs answer without changing preferences or enabling the guard. Old APKs
        # reject inspect before the explicit enable branch; retain their existing setup path.
        output = adb.shell("am", "broadcast", "--include-stopped-packages", "--user", args.user,
                           "-a", args.package + ".CONFIGURE_HOME_GUARD",
                           "-n", args.package + "/home.quiet.tv.HomeGuardSetupReceiver",
                           "--ei", "user", args.user, "--ez", "inspect", "true")
        inspection = broadcast_data(output)
        if inspection and inspection.get("schemaVersion") == 1:
            if inspection.get("package") != args.package or not inspection.get("supported"):
                raise ValueError("应用身份或守护能力不匹配，未修改权限")
            if direct_home:
                if not inspection.get("directHomeAvailable"):
                    raise ValueError("本机设备或系统 HOME 不适用直接按键补偿；未修改权限")
            else:
                homes = inspection.get("candidates", [])
                resolved = inspection.get("resolvedHome", "")
                selected = selected or inspection.get("selected") or (resolved if resolved in homes else "") or (homes[0] if len(homes) == 1 else "")
                if not selected or selected not in homes:
                    raise ValueError("原主页未唯一确定或已失效，请使用 --home 指定本机候选；未修改权限")
        elif direct_home:
            raise ValueError("已安装 APK 未提供直接 HOME 能力核对；未修改权限")
    pm_help = adb.permission_help("pm")
    ops_help = adb.permission_help("appops")
    pm_user = user_options(pm_help, "grant", sdk, args.user)
    ops_user = user_options(ops_help, "set", sdk, args.user)
    ops_get_user = user_options(ops_help, "get", sdk, args.user)
    plan = [["pm", "grant", *pm_user, args.package, "android.permission.WRITE_SECURE_SETTINGS"]]
    if sdk >= 23:
        plan.append(["appops", "set", *ops_user, args.package, "WRITE_SETTINGS", "allow"])
        plan.append(["appops", "set", *ops_user, args.package, "SYSTEM_ALERT_WINDOW", "allow"])
    vendor = " ".join(adb.shell("getprop", key) for key in ("ro.product.manufacturer", "ro.product.brand")).lower().split()
    if not direct_home and (sdk <= 23 or "xiaomi" in vendor or "redmi" in vendor or selected.startswith("com.jamdeo.tv.vod/")
            or sdk == 25 and selected.startswith("com.amazon.tv.launcher/")):
        plan.append(["appops", "set", *ops_user, args.package, "GET_USAGE_STATS", "allow"])
    vendor_before = {}
    brand = adb.shell("getprop", "ro.product.brand")
    manufacturer = adb.shell("getprop", "ro.product.manufacturer")
    if re.search(r"\b(?:tcl|ffalcon|iffalcon)\b", brand + " " + manufacturer, re.I):
        # TCL's process-start gate is independent of accessibility. Probe names rather than
        # hard-coding numeric app-op IDs shared with unrelated Android permissions.
        for operation in ("APP_AUTO_START", "AUTO_START", "APP_ASSOC_START"):
            try:
                vendor_before[operation] = adb.shell("appops", "get", *ops_get_user, args.package, operation)
            except ValueError as error:
                if re.search(r"unknown (?:operation|op)|invalid operation", str(error), re.I):
                    continue
                raise
            plan.append(["appops", "set", *ops_user, args.package, operation, "allow"])
        if vendor_before and command_signature(ops_help, "write-settings"):
            plan.append(["appops", "write-settings"])
    # Old firmware has no cmd. Its notification list is merged by the app with its granted permission.
    if sdk >= 27:
        try:
            help_text = adb.shell("cmd", "notification", "help")
        except ValueError:
            help_text = ""
        if "user_id" in command_signature(help_text, "allow_listener"):
            plan.append(["cmd", "notification", "allow_listener",
                         args.package + "/home.quiet.tv.HomeGuardRevivalListener", args.user])
    broadcast = ["am", "broadcast", "--include-stopped-packages", "--user", args.user, "-a", args.package + ".CONFIGURE_HOME_GUARD",
                 "-n", args.package + "/home.quiet.tv.HomeGuardSetupReceiver", "--ei", "user", args.user,
                 "--ez", "enable", "true"]
    if direct_home:
        broadcast += ["--ez", "direct_home", "true"]
    if selected:
        broadcast += ["--es", "home", selected]
    plan.append(broadcast)
    if not args.apply:
        print("仅预览，未修改电视。确认原主页及授权范围后，使用 --apply --record <新的私有记录路径> 执行：")
        if not selected and not direct_home:
            print("执行时先核对应用所选原主页；VIDAA 原主页可能还需要 GET_USAGE_STATS。")
        for command in plan:
            print("shell " + " ".join(shlex.quote(str(value)) for value in command))
        return
    # Save original settings and grant state before the first write, without replacing any baseline.
    record = {"serial": args.serial, "user": args.user, "package": args.package, "sdk": sdk, "identity_profile": profile,
              "tcl_appops_before": vendor_before, "inspection": inspection,
              "package_before": package, "plan": plan,
              "accessibility_before": adb.shell("settings", "--user", args.user, "get", "secure", "enabled_accessibility_services"),
              "notification_before": adb.shell("settings", "--user", args.user, "get", "secure", "enabled_notification_listeners"),
              "appops_before": adb.shell("appops", "get", *ops_get_user, args.package)}
    descriptor = os.open(args.record, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        json.dump(record, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    results_path = args.record.with_name(args.record.name + ".results.jsonl")
    descriptor = os.open(results_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    os.close(descriptor)
    for command in plan:
        if adb.current_user() != args.user:
            raise ValueError("前台用户已改变，停止后续配置；已完成的授权保留")
        try:
            try:
                output = adb.shell(*command)
            except CommandFailure as error:
                if command[0] == "appops" and "GET_USAGE_STATS" in command and "Unknown operation" in error.output:
                    output = adb.shell(*(value if value != "GET_USAGE_STATS" else "android:get_usage_stats" for value in command))
                elif command[:2] == ["cmd", "notification"] and re.search(r"(?i)securityexception|unknown command|not allowed", error.output):
                    output = "通知授权不可用，继续核对应用内同步恢复"
                else:
                    raise
        except (ValueError, subprocess.TimeoutExpired) as error:
            detail = "ADB 命令超时，结果未确认" if isinstance(error, subprocess.TimeoutExpired) else str(error)
            with results_path.open("a", encoding="utf-8") as handle:
                handle.write(json.dumps({"command": command, "status": "failed", "exit": getattr(error, "exit", None), "output": detail}, ensure_ascii=False) + "\n")
            raise ValueError("配置步骤失败（" + " ".join(map(str, command)) + "）：" + detail
                             + "；已完成的授权保留") from error
        with results_path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps({"command": command, "output": output}, ensure_ascii=False) + "\n")
        if command is broadcast:
            print(output)
            if not re.search(r"\bresult=-1\b", output):
                raise ValueError("应用未确认全部权限配置完成；已完成的授权保留，请检查上方结果")
            if direct_home:
                confirmed = broadcast_data(output)
                if not confirmed or not confirmed.get("directHomeConfigured"):
                    raise ValueError("直接 HOME 路线未读回确认；已完成的授权保留")
        if command[:2] == ["appops", "set"] and command[-2] in vendor_before:
            operation = command[-2]
            if adb.current_user() != args.user:
                raise ValueError("前台用户已改变，停止后续配置；已完成的授权保留")
            after = adb.shell("appops", "get", *ops_get_user, args.package, operation)
            with results_path.open("a", encoding="utf-8") as handle:
                handle.write(json.dumps({"operation": operation, "readback": after,
                                         "allowed": appop_allowed(after, operation)}, ensure_ascii=False) + "\n")
            if not appop_allowed(after, operation):
                raise ValueError("厂商自启动权限未读回 allow：" + operation + "；已完成的授权保留")
    print("权限已配置；实际绑定、实体 HOME、冷启动及待机唤醒仍需逐项验证。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="本次明确选定且已授权的电视")
    parser.add_argument("--user", type=int, required=True, help="已经核对的前台用户，不预设 0")
    parser.add_argument("--package", choices=["home.quiet.tv", "com.coocaa.quiettv", "com.ts.launcher"], default="home.quiet.tv")
    parser.add_argument("--direct-home", action="store_true", help="配置已核对的 MiBOX3 直接 HOME 按键补偿，不要求原桌面")
    parser.add_argument("--home", help="本机实际检测到的原主页组件；多候选时指定")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--apply", action="store_true", help="明确执行首次授权并启用守护")
    parser.add_argument("--pause", action="store_true", help="暂停全部中转，不修改系统 HOME 或授予权限")
    parser.add_argument("--record", type=Path, help="新的本机私有授权前记录，不覆盖")
    args = parser.parse_args()
    if args.user < 0 or (args.home and not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+", args.home)):
        parser.error("用户或原主页组件格式无效")
    if args.apply and not args.record:
        parser.error("执行配置必须使用 --record 保存原始状态")
    if args.pause and (args.home or args.direct_home):
        parser.error("暂停不能同时选择原主页或直接 HOME 补偿")
    if args.direct_home and args.home:
        parser.error("直接 HOME 补偿不能同时指定原主页")
    try:
        configure(args, ComputerAdb(args.adb, args.serial))
    except (OSError, ValueError, subprocess.TimeoutExpired) as error:
        print("配置未完成：" + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
