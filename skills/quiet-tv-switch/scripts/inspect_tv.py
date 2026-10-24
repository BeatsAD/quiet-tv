#!/usr/bin/env python3
"""Read one explicitly selected, connected TV; never change its state."""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def command_signature(help_text, name):
    """Return a help entry, including indented option lines."""
    lines = help_text.splitlines()
    for index, line in enumerate(lines):
        if not re.match(r"^\s*(?:pm\s+)?" + re.escape(name) + r"(?:\s|$)", line):
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


def inspect(adb, serial):
    queries = {}

    def read(label, *arguments):
        try:
            result = subprocess.run(
                [adb, "-s", serial, *arguments], capture_output=True,
                text=True, encoding="utf-8", errors="replace", timeout=15,
                check=False,
            )
            queries[label] = {
                "returncode": result.returncode,
                "stdout": result.stdout.strip(), "stderr": result.stderr.strip(),
            }
            return result.stdout.strip() if result.returncode == 0 else ""
        except subprocess.TimeoutExpired:
            queries[label] = {"error": "read timed out; state unknown"}
            return ""

    if read("transport", "get-state") != "device":
        raise ValueError("目标未连接或未授权；请核对所选电视并在电视端授权。")

    for key in ("ro.product.manufacturer", "ro.product.model", "ro.build.version.release",
                "ro.build.version.sdk", "ro.build.fingerprint", "ro.product.cpu.abilist",
                "ro.product.cpu.abi"):
        read(key, "shell", "getprop", key)
    read("shell_uid", "shell", "id", "-u")
    read("features", "shell", "pm", "list", "features")
    user_text = read("current_user", "shell", "am", "get-current-user")
    user = int(user_text) if re.fullmatch(r"[0-9]+", user_text) else None
    if user is None:
        activity = read("foreground_activity", "shell", "dumpsys", "activity", "activities")
        users = set(re.findall(r"mResumedActivity:\s*ActivityRecord\{[^\r\n}]*\bu([0-9]+)\b", activity))
        if len(users) == 1:
            user = int(users.pop())

    help_by_prefix = [
        (["cmd", "package"], read("cmd_help", "shell", "cmd", "package", "help")),
        (["pm"], read("pm_help", "shell", "pm", "help")),
    ]
    scoped = {}
    for name in ("resolve-activity", "query-activities", "set-home-activity",
                 "enable", "default-state", "disable-user"):
        for prefix, help_text in help_by_prefix:
            if "--user" in command_signature(help_text, name):
                scoped[name] = prefix + [name]
                break

    for name in ("resolve-activity", "query-activities"):
        if user is not None and name in scoped:
            read(name, "shell", *scoped[name], "--brief", "--user", str(user),
                 "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")

    # Only valid component lines from this device may become package arguments.
    home_output = "\n".join(queries.get(name, {}).get("stdout", "")
                            for name in ("resolve-activity", "query-activities"))
    packages = {"home.quiet.tv"}
    for line in home_output.splitlines():
        match = re.fullmatch(r"([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+)/[A-Za-z0-9_.$]+", line.strip())
        if match:
            packages.add(match.group(1))
    for package in sorted(packages):
        read("package:" + package, "shell", "dumpsys", "package", package)

    return {
        "recorded_at_utc": datetime.now(timezone.utc).isoformat(),
        "serial": serial, "current_user": user,
        "help_advertised_user_commands": scoped, "queries": queries,
        "limitations": [
            "Private device record; do not publish or commit.",
            "Help advertisement is not proof of firmware permission or successful execution.",
            "Read raw outputs; missing, failed or ambiguous queries remain unknown.",
            "Verify original HOME and exact user/package/component state before mutation.",
            "Physical remote, reboot and wake behavior have not been tested by this script.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit user-selected TV serial")
    parser.add_argument("--adb", default="adb", help="ADB executable path")
    parser.add_argument("--output", type=Path, help="New private JSON file; never overwritten")
    args = parser.parse_args()
    try:
        record = inspect(args.adb, args.serial)
        if args.output:
            # Exclusive creation preserves existing baselines, including symbolic links.
            descriptor = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
                json.dump(record, handle, ensure_ascii=False, indent=2)
                handle.write("\n")
            print("只读记录已保存；请在本机检查缺失项，不要公开或提交此文件。")
        else:
            print(json.dumps(record, ensure_ascii=False, indent=2))
    except (OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
