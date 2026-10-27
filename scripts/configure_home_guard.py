#!/usr/bin/env python3
"""Preview or explicitly configure Quiet TV guard through an already-authorized computer ADB."""

import argparse
import ipaddress
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


def legacy_tcp_port(serial, sdk):
    if not 21 <= sdk < 30:
        return None
    host, separator, port = serial.rpartition(":")
    if not separator or not port.isdecimal():
        return None
    try:
        address = ipaddress.ip_address(host[1:-1] if host.startswith("[") and host.endswith("]") else host)
    except ValueError:
        return None
    if address.version == 6 and not (host.startswith("[") and host.endswith("]")):
        return None
    return int(port) if 0 < int(port) <= 65535 else None


class ComputerAdb:
    def __init__(self, executable, serial):
        self.executable, self.serial = executable, serial

    def _result(self, *arguments, timeout=20):
        return subprocess.run([self.executable, "-s", self.serial, *arguments],
                              capture_output=True, text=True, encoding="utf-8",
                              errors="replace", timeout=timeout, check=False)

    def run(self, *arguments, timeout=20):
        result = self._result(*arguments, timeout=timeout)
        output = (result.stdout + "\n" + result.stderr).strip()
        if result.returncode or ERROR_OUTPUT.search(output):
            raise CommandFailure(result.returncode, output or "ADB 命令失败，状态未确认")
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


HISENSE_REFRESH_PACKAGE = "com.hisense.vidaaassistant"
HISENSE_REFRESH_FINGERPRINT = "Hisense/VIDAA_TV/MSD6A828:5.1.1/LVY48C/01.02.40.01:user/release-keys"


def hisense_refresh_supported(adb, sdk, user, brand, manufacturer):
    return (sdk == 22 and user == 0 and brand.lower() == "hisense" and manufacturer.lower() == "hisense"
            and adb.shell("getprop", "ro.build.fingerprint") == HISENSE_REFRESH_FINGERPRINT)


def hisense_hidden(dump):
    rows = re.findall(r"(?m)^\s*User 0:([^\r\n]+)", dump)
    if (len(rows) != 1 or not re.search(r"\binstalled=true\b", rows[0])
            or not re.search(r"\benabled=[01]\b", rows[0])):
        raise ValueError("上游刷新包未安装、已禁用或当前用户状态不明，未修改厂商包")
    state = re.search(r"\bhidden=(true|false)\b", rows[0])
    if state is None:
        raise ValueError("上游刷新包隐藏状态不明，未修改厂商包")
    return state[1] == "true"


def repair_hisense_accessibility(adb, package, pm_help, record):
    """Explicit Aerial #285 workaround; restore the verified hidden baseline even on failure."""
    own = package + "/home.quiet.tv.HomeGuardService"

    def check():
        if adb.current_user() != 0:
            raise ValueError("前台用户已改变，停止海信恢复；保留恢复记录")

    def run(*command):
        try:
            output = adb.shell(*command)
        except (ValueError, subprocess.TimeoutExpired) as error:
            record({"command": command, "status": "failed", "exit": getattr(error, "exit", None), "output": str(error)})
            raise
        record({"command": command, "output": output})
        return output

    def get(key):
        return run("settings", "--user", 0, "get", "secure", key).strip()

    def accepted():
        current = get("enabled_accessibility_services")
        entries = [canonical_home(entry) for entry in current.split(":")]
        return get("accessibility_enabled") == "1" and own in entries

    def write():
        check()
        current = get("enabled_accessibility_services")
        entries = [entry for entry in current.split(":") if entry and entry != "null" and canonical_home(entry) != own]
        entries.append(own)
        run("settings", "--user", 0, "put", "secure", "enabled_accessibility_services", ":".join(dict.fromkeys(entries)))
        check()
        run("settings", "--user", 0, "put", "secure", "accessibility_enabled", 1)

    check()
    if accepted():
        return
    write()
    if accepted():
        return
    if get("accessibility_enabled") != "0":
        raise ValueError("无障碍配置读回不一致，未修改厂商包")
    check()
    before = run("dumpsys", "package", HISENSE_REFRESH_PACKAGE)
    if not hisense_hidden(before):
        raise ValueError("固件未接受无障碍设置；上游刷新包原本未隐藏，未修改厂商包")
    unhide = ["pm", "unhide", *user_options(pm_help, "unhide", 22, 0), HISENSE_REFRESH_PACKAGE]
    hide = ["pm", "hide", *user_options(pm_help, "hide", 22, 0), HISENSE_REFRESH_PACKAGE]
    check()
    record({"hisense_refresh": "unhide_pending", "package": HISENSE_REFRESH_PACKAGE, "hidden_before": True,
            "package_before": before, "restore_command": hide})
    failure = None
    try:
        run(*unhide)
        if hisense_hidden(run("dumpsys", "package", HISENSE_REFRESH_PACKAGE)):
            raise ValueError("刷新包未解除隐藏")
        write()
        if not accepted():
            raise ValueError("包状态刷新后固件仍未接受无障碍设置")
    except (ValueError, subprocess.TimeoutExpired) as error:
        failure = error
    finally:
        try:
            check()
            run(*hide)
            if not hisense_hidden(run("dumpsys", "package", HISENSE_REFRESH_PACKAGE)):
                raise ValueError("刷新包恢复隐藏未读回")
            record({"hisense_refresh": "restored", "hidden_after": True})
        except (ValueError, subprocess.TimeoutExpired) as restore:
            raise ValueError("刷新包恢复隐藏未确认，请保留恢复记录并重新连接；原错误：" + str(failure or "无")) from restore
    if failure is not None:
        raise failure
    if not accepted():
        raise ValueError("恢复包隐藏状态后无障碍设置未保持，守护未确认")


def configure(args, adb):
    if adb.run("get-state") != "device":
        raise ValueError("需要已授权的 ADB 连接")
    sdk = int(adb.shell("getprop", "ro.build.version.sdk"))
    profile = verify_identity(adb, sdk)
    if adb.current_user() != args.user:
        raise ValueError("指定用户不是当前前台用户")
    if sdk < 21:
        raise ValueError("Android 4.4 档位未开放桌面守护")
    keep_adb = getattr(args, "keep_adb", None)
    if args.pause and keep_adb is not None:
        raise ValueError("暂停不能同时修改 ADB 保持选项")
    if keep_adb:
        vendor = " ".join(adb.shell("getprop", key) for key in ("ro.product.manufacturer", "ro.product.brand")).lower().split()
        if not set(vendor).intersection(("xiaomi", "redmi")):
            raise ValueError("当前 ADB 设置恢复只支持小米／Redmi，未修改权限")
    package = adb.shell("dumpsys", "package", args.package)
    user_state = re.search(r"(?m)^\s*User " + str(args.user) + r":([^\n]+)", package)
    if not user_state or not re.search(r"\binstalled=true\b", user_state[1]) or re.search(r"\b(?:hidden|suspended)=true\b|\benabled=[2-9]\b", user_state[1]):
        raise ValueError("当前用户上的 Quiet TV 未安装、被隐藏或已停用")
    if not re.search(r"(?<![\w.$])home\.quiet\.tv(?:\.|/\.)HomeGuardSetupReceiver(?![\w.$])", package):
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
    port_candidate = legacy_tcp_port(args.serial, sdk) if keep_adb else None
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
    port_to_write = port_candidate if inspection and inspection.get("adbPortSupported") is True else None
    if keep_adb and port_candidate and not args.apply:
        print("已识别传统 TCP 端口 " + str(port_candidate) + "；执行时核对 APK 端口保存能力。")
    if keep_adb and args.apply and port_candidate and port_to_write is None:
        print("ADB 端口恢复未配置：已安装 APK 不支持电脑保存端口，需要包含该修复的新包；继续配置已有调试设置恢复。")
    elif keep_adb and args.apply and port_candidate is None:
        print("未取得适用的传统 TCP 端口（USB、未知标识或 Android11+）；不猜测端口，仅配置调试设置恢复。")
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
    hisense = hisense_refresh_supported(adb, sdk, args.user, brand, manufacturer)
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
    if keep_adb is not None:
        broadcast += ["--ez", "keep_adb", str(keep_adb).lower()]
    if port_to_write is not None:
        broadcast += ["--ei", "adb_port", port_to_write]
    plan.append(broadcast)
    if not args.apply:
        print("仅预览，未修改电视。确认原主页及授权范围后，使用 --apply --record <新的私有记录路径> 执行：")
        if not selected and not direct_home:
            print("执行时先核对应用所选原主页；VIDAA 原主页可能还需要 GET_USAGE_STATS。")
        if hisense:
            print("若无障碍写入被固件丢弃，仅对原本隐藏的 com.hisense.vidaaassistant 临时 unhide，恢复无障碍后还原 hide；原桌面保留。")
        for command in plan:
            print("shell " + " ".join(shlex.quote(str(value)) for value in command))
        return
    # Save original settings and grant state before the first write, without replacing any baseline.
    record = {"serial": args.serial, "user": args.user, "package": args.package, "sdk": sdk, "identity_profile": profile,
              "tcl_appops_before": vendor_before, "inspection": inspection,
              "hisense_refresh_fingerprint": HISENSE_REFRESH_FINGERPRINT if hisense else None,
              "package_before": package, "plan": plan,
              "adb_port_candidate": port_candidate, "adb_port_to_write": port_to_write,
              "accessibility_enabled_before": adb.shell("settings", "--user", args.user, "get", "secure", "accessibility_enabled"),
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
    def record_result(value):
        with results_path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(value, ensure_ascii=False) + "\n")

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
            confirmed = broadcast_data(output)
            if (hisense and confirmed and confirmed.get("problem") == "无障碍权限未开启"
                    and confirmed.get("enabled") and not confirmed.get("accessibilityAllowed")):
                repair_hisense_accessibility(adb, args.package, pm_help, record_result)
                if adb.current_user() != args.user:
                    raise ValueError("前台用户已改变，停止后续配置；已完成的授权保留")
                output = adb.shell(*broadcast)
                record_result({"command": broadcast, "after_hisense_refresh": True, "output": output})
                print(output)
            if not re.search(r"\bresult=-1\b", output):
                raise ValueError("应用未确认全部权限配置完成；已完成的授权保留，请检查上方结果")
            confirmed = broadcast_data(output)
            if keep_adb is not None and (not confirmed or confirmed.get("keepAdb") is not keep_adb):
                raise ValueError("应用未读回确认 ADB 保持选项；已完成的授权保留")
            if port_to_write is not None and (not confirmed or confirmed.get("adbPort") != port_to_write
                    or confirmed.get("adbPortSupported") is not True):
                raise ValueError("ADB 恢复端口未读回确认；已完成的授权保留")
            if port_to_write is not None:
                print("ADB 恢复端口已保存并读回：" + str(port_to_write) + "；重启／唤醒后的网络重连仍待验收。")
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
    adb_choice = parser.add_mutually_exclusive_group()
    adb_choice.add_argument("--keep-adb", dest="keep_adb", action="store_const", const=True, default=None,
                            help="明确开启小米／Redmi ADB 设置恢复，端口持续可用性另行验收")
    adb_choice.add_argument("--no-keep-adb", dest="keep_adb", action="store_const", const=False,
                            help="明确关闭 ADB 设置恢复；省略两项则保持原选择")
    parser.add_argument("--record", type=Path, help="新的本机私有授权前记录，不覆盖")
    args = parser.parse_args()
    if args.user < 0 or (args.home and not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+", args.home)):
        parser.error("用户或原主页组件格式无效")
    if args.apply and not args.record:
        parser.error("执行配置必须使用 --record 保存原始状态")
    if args.pause and (args.home or args.direct_home):
        parser.error("暂停不能同时选择原主页或直接 HOME 补偿")
    if args.pause and args.keep_adb is not None:
        parser.error("暂停不能同时修改 ADB 保持选项")
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
