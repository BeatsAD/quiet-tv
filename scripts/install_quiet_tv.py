#!/usr/bin/env python3
"""Preview or install a published Quiet TV APK, optionally configure HOME, then print evidence."""
import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time
from types import SimpleNamespace
from urllib.request import Request, urlopen
import zipfile
import configure_home_guard as guard
import configure_boot_home as boot
import device_maintenance as maintenance
import install_tcl as tcl
from verify_install import HOME, collect, component, help_text, resolver

INDEX = "https://beatsad.github.io/quiet-tv/latest.json"
RELEASES = "https://github.com/BeatsAD/quiet-tv/releases/download/"


def read_index():
    request = Request(INDEX + "?check=" + str(time.time_ns()), headers={"Cache-Control": "no-cache"})
    with urlopen(request, timeout=30) as response:
        data = json.load(response)
    if data.get("schemaVersion") != 1 or not isinstance(data.get("releases"), list):
        raise ValueError("公开版本索引无效，未使用历史缓存")
    return data


def choose(index, sdk, abis, installed=None, mode="standard", version=None):
    candidates = []
    compatibility = "legacy19" if sdk < 21 else "standard"
    for release in index["releases"]:
        name = release.get("versionName", "")
        if not re.fullmatch(r"\d+\.\d+\.\d+", name) or version and name != version:
            continue
        for asset in release.get("assets", []):
            if (asset.get("packageName") != tcl.PACKAGE or asset.get("compatibility") != compatibility
                    or asset.get("homeMode") != mode or not isinstance(asset.get("minSdk"), int)
                    or asset["minSdk"] > sdk or not isinstance(asset.get("abis"), list)
                    or not tcl.compatible_abis(asset["abis"], abis)):
                continue
            if (not isinstance(asset.get("versionCode"), int) or asset["versionCode"] <= 0
                    or not re.fullmatch(r"[a-f0-9]{64}", asset.get("sha256") or "")
                    or not isinstance(asset.get("signerSha256"), list) or not asset["signerSha256"]
                    or any(not isinstance(value, str) or not re.fullmatch(r"[a-f0-9]{64}", value) for value in asset["signerSha256"])
                    or not isinstance(asset.get("bytes"), int) or asset["bytes"] <= 0
                    or not isinstance(asset.get("url"), str) or not asset["url"].startswith(RELEASES)):
                continue
            if installed and (asset["signerSha256"] != installed["signers"]
                              or asset["versionCode"] < installed["version_code"]
                              or asset["versionCode"] == installed["version_code"] and asset["sha256"] != installed["sha256"]):
                continue
            matched = next((i for i, abi in enumerate(abis) if abi in asset["abis"]), len(abis))
            # Prefer the system's first supported ABI; universal and single-ABI APKs are alternatives.
            rank = (tuple(map(int, name.split("."))), -matched, -len(asset["abis"]))
            candidates.append((rank, {**asset, "versionName": name}))
    if not candidates:
        raise ValueError("没有满足系统、架构、HOME 形态及覆盖升级条件的已发布 " + mode + " 包；未降级、构建或改装历史包")
    return max(candidates, key=lambda entry: entry[0])[1]


def download(asset, destination):
    with urlopen(Request(asset["url"], headers={"Cache-Control": "no-cache"}), timeout=60) as response:
        with destination.open("xb") as handle:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                handle.write(chunk)
    if destination.stat().st_size != asset["bytes"] or tcl.digest(destination) != asset["sha256"]:
        raise ValueError("下载文件大小或 SHA-256 不符；保留文件供核对，未安装")
    return destination


def pull(adb, sdk, user, destination, pm_help):
    options = guard.user_options(pm_help, "path", sdk, user)
    paths = [line[8:] for line in adb.shell("pm", "path", *options, tcl.PACKAGE).splitlines() if line.startswith("package:")]
    if len(paths) != 1 or not paths[0].startswith("/"):
        raise ValueError("已安装路径不明确或为 split APK，未执行单 APK 覆盖")
    adb.run("pull", paths[0], str(destination), timeout=120)


def check_apk(metadata, sha256, sdk, abis, installed=None, asset=None):
    if (metadata["package"] != tcl.PACKAGE or metadata["sha256"] != sha256
            or metadata["min_sdk"] > sdk or not tcl.compatible_abis(metadata["abis"], abis)):
        raise ValueError("APK 包名、校验值、系统或架构不符")
    if asset:
        fields = {"version_name": "versionName", "version_code": "versionCode", "min_sdk": "minSdk",
                  "signers": "signerSha256", "abis": "abis", "home_mode": "homeMode"}
        if any(metadata[key] != asset[field] for key, field in fields.items()):
            raise ValueError("APK 实际元数据与选定发布配置不符")
    if installed:
        if metadata["signers"] != installed["signers"] or metadata["version_code"] < installed["version_code"]:
            raise ValueError("签名冲突或需要降级，未覆盖")
        if metadata["version_code"] == installed["version_code"] and metadata["sha256"] != installed["sha256"]:
            raise ValueError("相同构建号文件不同，未覆盖")
        if metadata["home_mode"] != installed["home_mode"]:
            raise ValueError("已有包 HOME 形态不同，需单独确认迁移")


class RecordedAdb(guard.ComputerAdb):
    """Keep the failing command and original output visible, including legacy shell exits."""
    def __init__(self, executable, serial, journal):
        super().__init__(executable, serial)
        self.journal = journal

    def identity(self):
        return tcl.Adb.identity(self)

    def run(self, *arguments, timeout=20):
        try:
            return super().run(*map(str, arguments), timeout=timeout)
        except (ValueError, OSError, subprocess.TimeoutExpired) as error:
            self.failed(arguments, error)
            raise

    def shell(self, *arguments):
        try:
            return super().shell(*arguments)
        except (ValueError, OSError, subprocess.TimeoutExpired) as error:
            if maintenance.is_help_result(arguments, error):
                self.journal.event("help_result", command=list(arguments), exit=error.exit, output=error.output)
            else:
                self.failed(("shell", shlex.join([str(value) for value in arguments])), error)
            raise

    def failed(self, arguments, error):
        command = shlex.join([self.executable, "-s", self.serial, *map(str, arguments)])
        self.journal.event("command_failed", command=command, exit=getattr(error, "exit", None), output=str(error))
        print("命令失败：" + command + "\n原始错误：" + str(error), file=sys.stderr, flush=True)


def unchanged_user(adb, user):
    if adb.current_user() != user:
        raise ValueError("前台用户已变化，停止后续修改；已完成步骤保留在记录中")


def run_install(args, adb, tools, journal):
    if adb.run("get-state") != "device":
        raise ValueError("需要用户已选定并授权的 ADB 目标；本工具不扫描或自动连接电视")
    unchanged_user(adb, args.user)
    sdk = int(adb.shell("getprop", "ro.build.version.sdk"))
    identity = guard.verify_identity(adb, sdk)
    device = {name: adb.shell("getprop", name) for name in tcl.PROPERTIES}
    abis = [abi for abi in (device["ro.product.cpu.abilist"] or device["ro.product.cpu.abi"]).split(",") if abi]
    if not abis:
        journal.event("device_abis_unknown", fallback="universal")
        print("设备 ABI 未确认，仅选择完整通用备用包；实际兼容性仍须由安装器及安装后核验确认。", flush=True)
    pm_help = help_text(adb, "pm")
    try:
        home_before = adb.shell(*resolver(adb, sdk, args.user))
    except ValueError as error:
        home_before = ""
        journal.event("home_query_unavailable", error=str(error))
        print("原 HOME：未确认；仅试用／更新可继续，接管需先补齐恢复基线。", flush=True)
    original = component(home_before)
    package_before = adb.shell("dumpsys", "package", tcl.PACKAGE)
    state = tcl.user_state(package_before, args.user)
    if state and not tcl.usable(state) or not state and re.search(r"(?m)^\s*User \d+:", package_before):
        raise ValueError("当前用户未启用／安装此包或其他用户保留了安装；需先确认，不自动恢复")
    if original.split("/")[0] in ("com.ts.launcher", "com.coocaa.quiettv"):
        raise ValueError("当前使用专用包名桌面，需要先核对迁移；未另装普通包")
    for special in ("com.ts.launcher", "com.coocaa.quiettv"):
        special_dump = adb.shell("dumpsys", "package", special)
        if ("home.quiet.tv.HomeActivity" in special_dump
                and re.search(r"(?m)^\s*User \d+:[^\n]*\binstalled=true\b", special_dump)):
            raise ValueError("发现已安装的专用 Quiet TV（" + special + "），请沿用同包名更新／配置路径；未另装普通包")
    stock_dump = adb.shell("dumpsys", "package", original.split("/")[0]) if original else ""
    journal.event("baseline", serial=args.serial, user=args.user, identity=identity, device=device,
                  home_before=home_before, original_package=stock_dump, package_before=package_before,
                  goal=args.goal, keep_adb=args.keep_adb)
    installed = None
    if state:
        old = journal.directory / "installed-before.apk"
        pull(adb, sdk, args.user, old, pm_help)
        installed = tools.inspect(old)
    if args.goal == "update" and not installed:
        raise ValueError("未确认已有安装，不能将仅更新请求当作首次安装")
    index, asset = None, None
    if args.apk:
        apk_path, expected = args.apk, args.sha256
    else:
        index = read_index()
        asset = choose(index, sdk, abis, installed, installed["home_mode"] if installed else "standard")
        journal.event("selected_release", asset=asset)
        print("选定发布：" + json.dumps(asset, ensure_ascii=False), flush=True)
        apk_path, expected = download(asset, journal.directory / "selected.apk"), asset["sha256"]
    metadata = tools.inspect(apk_path)
    check_apk(metadata, expected, sdk, abis, installed, asset)
    journal.event("apk_verified", apk=metadata)
    vendor = (device["ro.product.brand"] + " " + device["ro.product.manufacturer"]).lower().split()
    is_tcl = bool(set(vendor).intersection(("tcl", "ffalcon", "iffalcon")))
    keep_adb = args.keep_adb and sdk >= 21
    if args.keep_adb and not keep_adb:
        journal.event("keep_adb_unsupported")
        print("保持 ADB：当前档位未支持；独立安装／HOME 步骤继续，整体常开目标仍未完成。", flush=True)
    if args.goal == "takeover":
        if not original or "ResolverActivity" in original or not tcl.usable(tcl.user_state(stock_dump, args.user)):
            raise ValueError("原桌面或恢复基线未确认；请先确认原主页，未执行安装及接管")
    same = installed is not None and installed["sha256"] == metadata["sha256"]
    install_options = guard.user_options(pm_help, "install", sdk, args.user)
    print("计划：" + ("沿用相同 APK" if same else "保留数据安装") + "；目标=" + args.goal + "；ADB 设置恢复=" + str(keep_adb), flush=True)
    if not args.apply:
        if same and metadata["home_mode"] == "standard" and args.goal == "takeover":
            try:
                boot.configure(adb, args.user, tools, apk_path, journal)
            except boot.NoNativeRoute as error:
                print("标准开机 HOME 路线不可用：" + str(error) + "；执行时评估兼容守护。", flush=True)
        print("仅预览，未修改电视。核对目标和授权后，以新的 --record 目录加 --apply 执行。", flush=True)
        return
    unchanged_user(adb, args.user)
    if tcl.digest(apk_path) != expected:
        raise ValueError("APK 在执行前改变，未安装")
    if not same:
        if is_tcl:
            # Reuse the complete TCL installer; never opt into stopping its package installer.
            relay_path, relay_sha = None, None
            if index and not installed and metadata["home_mode"] == "standard":
                try:
                    relay = choose(index, sdk, abis, mode="relay", version=metadata["version_name"])
                except ValueError:
                    print("当前发布没有适配 relay 候选；若普通安装被拒将停止，不构建或停用安装器。", flush=True)
                else:
                    relay_path = download(relay, journal.directory / "relay.apk")
                    relay_sha = relay["sha256"]
            tcl_args = SimpleNamespace(**{**vars(args), "apk": apk_path, "sha256": expected,
                "relay_apk": relay_path, "relay_sha256": relay_sha, "allow_temporary_installer_disable": False,
                "install_timeout": 180, "tcast_host": args.tcast_host})
            nested = tcl.Journal(journal.directory / "tcl")
            try:
                tcl.install(tcl_args, adb, tools, nested)
            finally:
                nested.close()
            # The TCL route may have installed the validated relay alternative.
            metadata, apk_path = tools.inspect(tcl_args.apk), tcl_args.apk
        else:
            output = adb.run("install", "-r", *install_options, str(apk_path), timeout=180)
            journal.event("install", output=output)
            if not re.search(r"(?m)^Success\s*$", output):
                raise ValueError("安装命令未返回 Success：" + output)
    unchanged_user(adb, args.user)
    actual_path = journal.directory / "installed-after.apk"
    pull(adb, sdk, args.user, actual_path, pm_help)
    actual = tools.inspect(actual_path)
    if actual != metadata:
        raise ValueError("已安装 APK 与目标文件不一致，未启动或配置桌面")
    journal.event("installed_apk_verified", actual=actual)
    # Baseline exists before the first launch, which can trigger app recovery of existing settings.
    unchanged_user(adb, args.user)
    output = adb.shell("am", "start", "--user", args.user, "-W", "-n", HOME)
    journal.event("launch", output=output)
    if "Status: ok" not in output:
        raise ValueError("未确认应用启动：" + output)
    if args.goal != "takeover":
        try:
            after_home = adb.shell(*resolver(adb, sdk, args.user))
        except ValueError as error:
            journal.event("home_query_unavailable_after_launch", error=str(error))
            after_home = ""
        if original and after_home and component(after_home) != original:
            journal.event("home_changed_after_launch", before=home_before, after=after_home)
            raise ValueError("安装／启动后 HOME 与原配置不同，结果需核对；未重新配置或自动恢复")
        print("安装核验完成，未主动配置 HOME 或守护；原 HOME 读回" + ("一致。" if original and after_home else "未确认。"), flush=True)
        return
    installed_dump = adb.shell("dumpsys", "package", tcl.PACKAGE)
    if "DeviceMaintenanceReceiver" in installed_dump:
        baseline = maintenance.inspect(adb, tcl.PACKAGE, args.user)
        if not baseline.get("originalHome") and original != HOME:
            unchanged_user(adb, args.user)
            baseline = maintenance.remember_home(adb, tcl.PACKAGE, args.user, original)
        journal.event("app_recovery_baseline", snapshot=baseline)
        if not baseline.get("originalHome"):
            print("应用内原桌面记录缺失，请保留历史电脑安装记录用于恢复。", flush=True)
    else:
        print("旧 APK 无电脑维护接口；恢复依赖电脑记录，新功能需更新 APK。", flush=True)
    native = False
    if metadata["home_mode"] == "standard":
        try:
            boot.configure(adb, args.user, tools, actual_path, journal, apply=True)
            native = True
        except boot.NoNativeRoute as error:
            journal.event("boot_native_unavailable", error=str(error))
            print("标准开机 HOME 路线不可用：" + str(error) + "；继续已有兼容守护。", flush=True)
    if not native or keep_adb:
        if sdk < 21:
            raise ValueError("Android 4.4 无兼容守护且默认 HOME 未确认，不能报告接管完成")
        guard_args = SimpleNamespace(serial=args.serial, user=args.user, package=tcl.PACKAGE,
            adb=args.adb, apply=True, pause=False, direct_home=False, adb_only=native,
            home=original if not native and original != HOME else None, keep_adb=True if keep_adb else None,
            record=journal.directory / "guard-before.json")
        guard.configure(guard_args, adb)
    journal.event("desktop_configured", route="native" if native else "guard", keep_adb_requested=keep_adb)
    if args.keep_adb and not keep_adb:
        raise ValueError("安装／桌面步骤已执行，但当前档位不支持 ADB 保持，未满足重启常开目标")
    print("桌面配置已执行；实体 HOME、开机、唤醒和 ADB 重连等待分别验收。", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="用户已确认并授权的 ADB 目标")
    parser.add_argument("--user", type=int, required=True, help="已核对的前台用户")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--record", type=Path, required=True, help="全新私有记录目录，不覆盖历史基线")
    parser.add_argument("--goal", choices=("trial", "takeover", "update"),
                        help="默认 takeover；只读验收默认 trial，仅更新或试用须明确指定")
    adb_choice = parser.add_mutually_exclusive_group()
    adb_choice.add_argument("--keep-adb", dest="keep_adb", action="store_const", const=True, default=None,
                            help="开启适用设备的 ADB 设置恢复；takeover 默认开启")
    adb_choice.add_argument("--no-keep-adb", dest="keep_adb", action="store_const", const=False,
                            help="仅配置桌面，保留已有 ADB 选择")
    parser.add_argument("--apply", action="store_true", help="执行本次目标；省略时只读预览")
    parser.add_argument("--apk", type=Path, help="Agent 已核验发布来源的本地 APK；省略时按实时索引选择")
    parser.add_argument("--sha256", type=str.lower, help="本地 APK 的可信发布校验值，必须与 --apk 一起给出")
    parser.add_argument("--aapt2")
    parser.add_argument("--apksigner")
    parser.add_argument("--tcast-host", help="USB 连接的 TCL 电视实际地址，交由现有安装助手核对")
    parser.add_argument("--verify-only", action="store_true", help="只采集原始验收输出，不启动应用或配置设备")
    parser.add_argument("--physical-home", action="store_true", help="用户刚按过实体 HOME；仅用于 --verify-only")
    args = parser.parse_args()
    if args.goal is None:
        args.goal = "trial" if args.verify_only else "takeover"
    if args.keep_adb is None:
        args.keep_adb = args.goal == "takeover" and not args.verify_only
    if args.user < 0 or bool(args.apk) != bool(args.sha256) or args.sha256 and not re.fullmatch(r"[a-f0-9]{64}", args.sha256):
        parser.error("用户无效，或本地 APK 未同时提供完整 SHA-256")
    if args.keep_adb and args.goal != "takeover":
        parser.error("保持 ADB 仅在明确选择 takeover 时启用；试用或仅更新保留原选择")
    if args.physical_home and not args.verify_only:
        parser.error("实体 HOME 证据须在安装／启动流程之外通过 --verify-only 单独采集")
    if args.verify_only and (args.apply or args.apk or args.keep_adb or args.goal != "trial"):
        parser.error("只读验收不能同时安装、配置或选择接管目标")
    journal = None
    adb = None
    status = 0
    try:
        journal = tcl.Journal(args.record)
        adb = RecordedAdb(args.adb, args.serial, journal)
        if args.verify_only:
            result = collect(adb, args.user, journal, args.physical_home)
            return 0 if result["physicalHome"] is True else 1 if result["physicalHome"] is False else 2
        tools = tcl.ApkTools(tcl.sdk_tool("aapt2", args.aapt2), tcl.sdk_tool("apksigner", args.apksigner))
        run_install(args, adb, tools, journal)
    except (OSError, ValueError, subprocess.TimeoutExpired, zipfile.BadZipFile, KeyboardInterrupt) as error:
        if journal:
            journal.event("incomplete", error=str(error) or "用户取消")
        print("安装流程未完成：" + (str(error) or "用户取消") + "；已完成变更请查本次私有记录，不自动重试。", file=sys.stderr)
        status = 1
    finally:
        if journal:
            if args.apply and not args.verify_only:
                try:
                    collect(adb, args.user, journal)
                except (OSError, ValueError, subprocess.TimeoutExpired) as error:
                    journal.event("acceptance_incomplete", error=str(error))
                    print("原始验收采集未完成：" + str(error), file=sys.stderr)
                    status = 1
            journal.close()
    return status


if __name__ == "__main__":
    sys.exit(main())
