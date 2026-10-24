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


def command_signature(help_text, name):
    lines = help_text.splitlines()
    for index, line in enumerate(lines):
        if not re.match(r"^\s*(?:pm\s+|appops\s+)?" + re.escape(name) + r"(?:\s|$)", line):
            continue
        indent = len(line) - len(line.lstrip())
        entry = [line.strip()]
        for following in lines[index + 1:]:
            if not following.strip():
                continue
            if len(following) - len(following.lstrip()) <= indent:
                break
            entry.append(following.strip())
        return " ".join(entry)
    return ""


class ComputerAdb:
    def __init__(self, executable, serial):
        self.executable, self.serial = executable, serial

    def run(self, *arguments):
        result = subprocess.run([self.executable, "-s", self.serial, *arguments],
                                capture_output=True, text=True, encoding="utf-8",
                                errors="replace", timeout=20, check=False)
        output = (result.stdout + "\n" + result.stderr).strip()
        if result.returncode or re.search(r"(?im)^\s*(?:error:|exception|securityexception|failure|unknown (?:command|operation))", output):
            raise ValueError(output or "ADB 命令失败，状态未确认")
        return output

    def shell(self, *arguments):
        # adb joins shell arguments; quote for Android's POSIX shell, including on Windows hosts.
        return self.run("shell", " ".join(shlex.quote(str(value)) for value in arguments))

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
    if adb.run("get-state") != "device" or adb.shell("id", "-u") != "2000":
        raise ValueError("需要已授权的普通 ADB shell；端口连通或 root 调试模式不能代替")
    if adb.current_user() != args.user:
        raise ValueError("指定用户不是当前前台用户")
    sdk = int(adb.shell("getprop", "ro.build.version.sdk"))
    if sdk < 21:
        raise ValueError("Android 4.4 档位未开放桌面守护")
    package = adb.shell("dumpsys", "package", args.package)
    user_state = re.search(r"(?m)^\s*User " + str(args.user) + r":([^\n]+)", package)
    if not user_state or not re.search(r"\binstalled=true\b", user_state[1]) or re.search(r"\b(?:hidden|suspended)=true\b|\benabled=[2-9]\b", user_state[1]):
        raise ValueError("当前用户上的 Quiet TV 未安装、被隐藏或已停用")
    if "home.quiet.tv.HomeGuardSetupReceiver" not in package:
        raise ValueError("已安装 APK 不包含电脑守护配置入口；请先核对并更新到包含本修复的版本")
    pm_help = adb.shell("pm", "help")
    ops_help = adb.shell("appops", "help")
    if "--user" not in command_signature(pm_help, "grant") or "--user" not in command_signature(ops_help, "set"):
        raise ValueError("本机帮助未确认授权命令支持目标用户，未修改权限")
    plan = [["pm", "grant", "--user", args.user, args.package, "android.permission.WRITE_SECURE_SETTINGS"]]
    if sdk >= 23:
        plan.append(["appops", "set", "--user", args.user, args.package, "SYSTEM_ALERT_WINDOW", "allow"])
    if sdk <= 23:
        plan.append(["appops", "set", "--user", args.user, args.package, "GET_USAGE_STATS", "allow"])
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
    if args.home:
        broadcast += ["--es", "home", args.home]
    plan.append(broadcast)
    if not args.apply:
        print("仅预览，未修改电视。确认原主页及授权范围后，使用 --apply --record <新的私有记录路径> 执行：")
        for command in plan:
            print("shell " + " ".join(shlex.quote(str(value)) for value in command))
        return
    # Save original settings and grant state before the first write, without replacing any baseline.
    record = {"serial": args.serial, "user": args.user, "package": args.package, "sdk": sdk,
              "package_before": package, "plan": plan,
              "accessibility_before": adb.shell("settings", "--user", args.user, "get", "secure", "enabled_accessibility_services"),
              "notification_before": adb.shell("settings", "--user", args.user, "get", "secure", "enabled_notification_listeners"),
              "appops_before": adb.shell("appops", "get", "--user", args.user, args.package)}
    descriptor = os.open(args.record, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        json.dump(record, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    for command in plan:
        if adb.current_user() != args.user:
            raise ValueError("前台用户已改变，停止后续配置；已完成的授权保留")
        try:
            output = adb.shell(*command)
        except ValueError as error:
            if command[0] == "appops" and "GET_USAGE_STATS" in command and "Unknown operation" in str(error):
                output = adb.shell(*(value if value != "GET_USAGE_STATS" else "android:get_usage_stats" for value in command))
            else:
                raise
        if command is broadcast:
            print(output)
            if not re.search(r"\bresult=-1\b", output):
                raise ValueError("应用未确认全部权限配置完成；已完成的授权保留，请检查上方结果")
    print("权限已配置；实际绑定、实体 HOME、冷启动及待机唤醒仍需逐项验证。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="本次明确选定且已授权的电视")
    parser.add_argument("--user", type=int, required=True, help="已经核对的前台用户，不预设 0")
    parser.add_argument("--package", choices=["home.quiet.tv", "com.coocaa.quiettv"], default="home.quiet.tv")
    parser.add_argument("--home", help="本机实际检测到的原主页组件；多候选时指定")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--apply", action="store_true", help="明确执行首次授权并启用守护")
    parser.add_argument("--record", type=Path, help="新的本机私有授权前记录，不覆盖")
    args = parser.parse_args()
    if args.user < 0 or (args.home and not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+", args.home)):
        parser.error("用户或原主页组件格式无效")
    if args.apply and not args.record:
        parser.error("执行配置必须使用 --record 保存原始状态")
    try:
        configure(args, ComputerAdb(args.adb, args.serial))
    except (OSError, ValueError, subprocess.TimeoutExpired) as error:
        print("配置未完成：" + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
