#!/usr/bin/env python3
"""Read and print installation evidence. Never inject HOME, launch an app or reboot."""
import argparse
import json
import re
import shlex
import subprocess
import sys
from pathlib import Path
from configure_home_guard import ComputerAdb, CommandFailure, ERROR_OUTPUT, canonical_home, user_options
from install_tcl import Journal, PACKAGE

HOME_ARGS = ("-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
HOME = PACKAGE + "/home.quiet.tv.HomeActivity"
GUARD = PACKAGE + "/home.quiet.tv.HomeGuardService"


def help_text(adb, *tool):
    try:
        return adb.shell(*tool, "help")
    except CommandFailure as error:
        return error.output  # Some old tools print valid usage with a nonzero exit.


def resolver(adb, sdk, user):
    for tool in (("cmd", "package"), ("pm",)):
        try:
            options = user_options(help_text(adb, *tool), "resolve-activity", sdk, user)
            return [*tool, "resolve-activity", "--brief", *options, *HOME_ARGS]
        except ValueError:
            continue
    raise ValueError("本机帮助未确认针对当前用户的 HOME 查询命令")


def component(output):
    entries = re.findall(r"(?m)^\s*([\w.]+/[\w.$]+)\s*$", output)
    return canonical_home(entries[0]) if len(entries) == 1 else ""


def bound_guard(output):
    # Only a bound-services section counts. Enabled/installed entries and labels do not.
    sections = []
    for match in re.finditer(r"(?i)\bbound\s*services\s*[:=]\s*([\[{])", output):
        opening, depth = match[1], 1
        closing = "]" if opening == "[" else "}"
        for index in range(match.end(), len(output)):
            if output[index] == opening:
                depth += 1
            elif output[index] == closing:
                depth -= 1
                if depth == 0:
                    sections.append(output[match.end():index])
                    break
    if not sections:
        return None
    return any(GUARD in text or PACKAGE + "/.HomeGuardService" in text for text in sections)


def capture(adb, label, command, journal):
    shell = " ".join(shlex.quote(str(value)) for value in command)
    try:
        result = adb._result("shell", shell)
        value = {"label": label, "command": shell, "exit": result.returncode,
                 "stdout": result.stdout, "stderr": result.stderr}
    except subprocess.TimeoutExpired as error:
        def decoded(text):
            return text.decode("utf-8", "replace") if isinstance(text, bytes) else text or ""
        value = {"label": label, "command": shell, "exit": None, "error": "查询超时，结果未确认",
                 "stdout": decoded(error.stdout), "stderr": decoded(error.stderr)}
    journal.event("acceptance_raw", **value)
    print("\n" + label + "\n$ " + shlex.join([adb.executable, "-s", adb.serial, "shell", shell]), flush=True)
    # Preserve stdout and stderr verbatim, including whitespace; labels are outside their blocks.
    print("[stdout]", flush=True)
    sys.stdout.write(value["stdout"])
    sys.stdout.flush()
    print("\n[stderr]", flush=True)
    sys.stdout.write(value["stderr"])
    sys.stdout.flush()
    print("\n[exit] " + str(value["exit"]), flush=True)
    if value.get("error"):
        print(value["error"], flush=True)
    return value


def collect(adb, user, journal, physical_home=False):
    if adb.run("get-state") != "device" or adb.current_user() != user:
        raise ValueError("目标未授权或前台用户与记录不符，未验收")
    sdk = int(adb.shell("getprop", "ro.build.version.sdk"))
    queries = [("已安装版本", ["sh", "-c", "dumpsys package home.quiet.tv | grep -E 'versionName=|versionCode='"])]
    try:
        queries.append(("系统 HOME", resolver(adb, sdk, user)))
    except ValueError as error:
        journal.event("home_query_unavailable", error=str(error))
        print("系统 HOME：未知；" + str(error), flush=True)
    queries += [("无障碍配置", ["settings", "--user", user, "get", "secure", "enabled_accessibility_services"]),
                ("无障碍绑定", ["dumpsys", "accessibility"]),
                ("当前焦点", ["sh", "-c", "dumpsys window | grep mCurrentFocus"])]
    raw = {label: capture(adb, label, command, journal) for label, command in queries}
    if adb.current_user() != user:
        raise ValueError("验收期间前台用户改变；原始输出已保留，不能据此确认结果")

    def output(label):
        item = raw.get(label, {})
        text = item.get("stdout", "")
        return text if item.get("exit") == 0 and not ERROR_OUTPUT.search(text + "\n" + item.get("stderr", "")) else ""

    focus = output("当前焦点")
    focused = bool(re.search(r"\bhome\.quiet\.tv/(?:home\.quiet\.tv\.|\.)HomeActivity(?=[\s}])", focus))
    services = [canonical_home(entry) for entry in output("无障碍配置").strip().split(":")]
    result = {"systemHome": component(output("系统 HOME")) or None,
              "accessibilityEnabled": GUARD in services if output("无障碍配置") else None,
              "accessibilityBound": bound_guard(output("无障碍绑定")),
              "homeFocused": focused if focus else None,
              "physicalHome": focused if physical_home and focus else None,
              "reboot": "未执行", "coldBoot": "未执行", "standbyWake": "未执行"}
    journal.event("acceptance", **result)
    print("\n分项结果：" + json.dumps(result, ensure_ascii=False), flush=True)
    if not physical_home:
        print("请用实体遥控器按主页键并观察画面，再用统一入口的 --verify-only --physical-home 采集焦点；当前焦点不代表实体 HOME 验收。", flush=True)
    print("重启、断电冷启动、待机唤醒及 ADB 重连均未执行，不能据此报告开机直达或 ADB 持续可用。", flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--user", type=int, required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--record", type=Path, required=True, help="全新私有记录目录")
    parser.add_argument("--physical-home", action="store_true", help="用户已实际按实体 HOME；此工具不会代按")
    args = parser.parse_args()
    if args.user < 0:
        parser.error("用户不能为负数")
    journal = None
    try:
        journal = Journal(args.record)
        result = collect(ComputerAdb(args.adb, args.serial), args.user, journal, args.physical_home)
        return 0 if result["physicalHome"] is True else 1 if result["physicalHome"] is False else 2
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        print("验收未完成：" + str(error), file=sys.stderr)
        return 1
    finally:
        if journal:
            journal.close()


if __name__ == "__main__":
    sys.exit(main())
