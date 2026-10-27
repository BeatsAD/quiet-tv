#!/usr/bin/env python3
"""Computer-side inspect, repair, restore and single-app disable/restore for Quiet TV."""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path
from types import SimpleNamespace
import configure_home_guard as guard
import install_tcl as tcl
from verify_install import component, help_text, resolver


def is_help_result(arguments, error):
    return (bool(arguments) and arguments[-1] == "help" and isinstance(error, guard.CommandFailure)
            and any(guard.command_signature(error.output, name) for name in ("resolve-activity", "grant", "set")))


class RecordedAdb(guard.ComputerAdb):
    def __init__(self, executable, serial, journal):
        super().__init__(executable, serial)
        self.journal = journal

    def shell(self, *arguments):
        try:
            output = super().shell(*arguments)
        except (ValueError, OSError, subprocess.TimeoutExpired) as error:
            # Android may return nonzero for valid help. Keep the raw evidence without
            # presenting accepted capability discovery as a failed device mutation.
            help_result = is_help_result(arguments, error)
            self.journal.event("help_result" if help_result else "command_failed", command=list(arguments), exit=getattr(error, "exit", None), output=str(error))
            if not help_result:
                print("命令失败：" + repr(arguments) + "\n" + str(error), file=sys.stderr)
            raise
        self.journal.event("command", command=list(arguments), output=output)
        return output


def inspect(adb, package, user, operation="inspect", target=None, home=None):
    arguments = ["am", "broadcast", "--include-stopped-packages", "--user", user,
                 "-a", package + ".DEVICE_MAINTENANCE", "-n", package + "/home.quiet.tv.DeviceMaintenanceReceiver",
                 "--ei", "user", user, "--es", "operation", operation]
    if target:
        arguments += ["--es", "target_package", target]
    if home:
        arguments += ["--es", "home", home]
    output = adb.shell(*arguments)
    result = guard.broadcast_data(output)
    if (not re.search(r"\bresult=-1\b", output) or not result or result.get("schemaVersion") != 1
            or result.get("package") != package or result.get("user") != user):
        raise ValueError("应用未确认维护接口；需包含本功能的 APK，不绕过应用规则：" + output)
    return result


def remember_home(adb, package, user, home):
    """Call only before changing native HOME; never replace an existing recovery baseline."""
    before = inspect(adb, package, user)
    if before.get("originalHome"):
        return before
    return inspect(adb, package, user, "remember_home", home=home)


def unchanged_user(adb, user):
    if adb.current_user() != user:
        raise ValueError("前台用户已改变，停止后续修改")


def package_state(adb, package, user):
    dump = adb.shell("dumpsys", "package", package)
    state = tcl.user_state(dump, user)
    versions = re.findall(r"(?m)^\s*versionCode=(\d+)\b", dump)
    if state is None or len(versions) != 1:
        raise ValueError("应用用户状态或版本未唯一确认")
    return {**state, "versionCode": int(versions[0])}


def command(adb, sdk, user, action, value):
    for tool in (("cmd", "package"), ("pm",)):
        try:
            options = guard.user_options(help_text(adb, *tool), action, sdk, user)
            return [*tool, action, *options, value]
        except ValueError:
            continue
    raise ValueError("固件未提供可核对的命令：" + action)


def same_scope(before, after):
    return all(before.get(key) == after.get(key) for key in ("package", "user", "fingerprint", "appVersion", "systemHome"))


def read_disable_record(path, args, snapshot):
    events = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    plans = [item for item in events if item.get("event") == "app_plan" and item.get("action") == "disable"]
    if len(plans) != 1:
        raise ValueError("需要一次禁用操作的原始 record.jsonl，不能猜测启用状态")
    plan = plans[0]
    if not isinstance(plan.get("scope"), dict) or not isinstance(plan.get("before"), dict):
        raise ValueError("恢复记录缺少设备或原状态")
    if not all(key in plan["before"] for key in ("enabled", "versionCode")):
        raise ValueError("恢复记录缺少原启用值或版本")
    if (plan.get("serial") != args.serial or plan.get("target") != args.target
            or not all(plan["scope"].get(key) == snapshot.get(key) for key in ("package", "user", "fingerprint", "appVersion"))):
        raise ValueError("恢复记录的设备、用户、固件或应用版本不符")
    if not any(item.get("event") == "app_attempt" and item.get("action") == "disable" for item in events):
        raise ValueError("记录只有预览，没有实际禁用尝试")
    return plan


def manage_app(args, adb, journal, sdk, snapshot):
    app = snapshot.get("app", {})
    if not app.get("canManage") or not app.get("installed"):
        raise ValueError("本机规则不允许管理此应用：" + app.get("reason", "用途或恢复资格未确认"))
    before = package_state(adb, args.target, args.user)
    if (before["versionCode"] != app["versionCode"] or before["enabled"] != app["enabledSetting"]
            or before["installed"] != "true" or before.get("hidden") == "true" or before.get("suspended") == "true"):
        raise ValueError("应用状态不一致、隐藏或挂起，未操作")
    if args.action == "disable":
        if not app.get("enabled") or before["enabled"] not in (0, 1):
            raise ValueError("应用不是已确认的启用状态，不重复禁用")
        # Require an exact recovery command before the first write.
        command(adb, sdk, args.user, "default-state" if before["enabled"] == 0 else "enable", args.target)
        desired, verb = 3, "disable-user"
    else:
        plan = read_disable_record(args.from_record, args, snapshot)
        if before["versionCode"] != plan["before"]["versionCode"]:
            raise ValueError("目标应用版本已改变，需重新核对恢复资格")
        desired = plan["before"]["enabled"]
        if desired not in (0, 1):
            raise ValueError("恢复基线无效")
        if before["enabled"] == desired:
            print("已处于记录中的原状态，无需重复恢复。")
            return
        if before["enabled"] != 3:
            raise ValueError("当前状态不是本工具的停用状态，未覆盖其他改动")
        verb = "default-state" if desired == 0 else "enable"
    mutation = command(adb, sdk, args.user, verb, args.target)
    journal.event("app_plan", action=args.action, serial=args.serial, target=args.target, scope=snapshot, before=before, command=mutation)
    print(json.dumps({"action": args.action, "app": app, "command": mutation}, ensure_ascii=False, indent=2))
    if not args.apply:
        print("仅预览；确认这一个应用及影响后，以新记录目录加 --apply 执行。")
        return
    unchanged_user(adb, args.user)
    fresh = inspect(adb, args.package, args.user, target=args.target)
    if not same_scope(snapshot, fresh) or fresh.get("app") != app or package_state(adb, args.target, args.user) != before:
        raise ValueError("执行前状态或规则已变化，未修改")
    unchanged_user(adb, args.user)
    journal.event("app_attempt", action=args.action)
    failure = None
    try:
        adb.shell(*mutation)
    except ValueError as error:
        failure = error
    unchanged_user(adb, args.user)
    after = package_state(adb, args.target, args.user)
    journal.event("app_result", after=after, command_error=str(failure) if failure else None)
    if (after["enabled"] != desired or after["installed"] != "true"
            or after["versionCode"] != before["versionCode"]):
        raise ValueError("状态读回不符合预期；保留记录，不重放操作")
    if failure:
        raise ValueError("命令报错但目标状态已观察到，请核对记录，不重复操作：" + str(failure))
    print("已读回确认应用状态；安装及数据保留。")


def restore_home(args, adb, journal, sdk, snapshot):
    if snapshot.get("takeoverRecord"):
        raise ValueError("存在历史专项接管记录，已保存在维护快照；需按记录逐项恢复系统属性及原桌面，不能只切换 HOME 后宣布完成")
    original = guard.canonical_home(snapshot.get("originalHome", ""))
    own = args.package + "/home.quiet.tv.HomeActivity"
    current = component(adb.shell(*resolver(adb, sdk, args.user)))
    if not original or not snapshot.get("originalUsable"):
        raise ValueError("缺少可用的原桌面记录；请按原安装／接管记录恢复应用状态后再继续，不猜原桌面")
    if current not in (own, original) and not snapshot.get("routes", {}).get("proxyReady"):
        raise ValueError("当前 HOME 不是本次 Quiet TV 路线，需先核对其他桌面改动")
    mutation = None if current == original else command(adb, sdk, args.user, "set-home-activity", original)
    journal.event("restore_plan", before=snapshot, command=mutation)
    print("计划：暂停接管；" + ("保留当前原桌面。" if mutation is None else "恢复系统 HOME 为 " + original))
    if not args.apply:
        return
    unchanged_user(adb, args.user)
    if not same_scope(snapshot, inspect(adb, args.package, args.user)):
        raise ValueError("执行前桌面状态已变化，未恢复")
    paused = inspect(adb, args.package, args.user, "pause")
    if not paused.get("paused"):
        raise ValueError("暂停未确认，未切换 HOME")
    if mutation:
        unchanged_user(adb, args.user)
        adb.shell(*mutation)
    actual = component(adb.shell(*resolver(adb, sdk, args.user)))
    journal.event("restore_result", actual=actual, expected=original)
    if actual != original:
        raise ValueError("原桌面 HOME 恢复未确认；守护保持暂停")
    inspect(adb, args.package, args.user, "finish_restore")
    print("系统 HOME 已恢复，守护保持暂停；请按实体 HOME 验证。重启与唤醒未验证。")


def run(args, adb, journal):
    if adb.run("get-state") != "device":
        raise ValueError("需要已授权的目标电视")
    unchanged_user(adb, args.user)
    sdk = int(adb.shell("getprop", "ro.build.version.sdk"))
    guard.verify_identity(adb, sdk)
    snapshot = inspect(adb, args.package, args.user, target=args.target)
    journal.event("maintenance_before", serial=args.serial, snapshot=snapshot)
    if args.action == "inspect":
        print(json.dumps(snapshot, ensure_ascii=False, indent=2))
    elif args.action in ("disable", "restore-app"):
        manage_app(args, adb, journal, sdk, snapshot)
    elif args.action == "restore":
        restore_home(args, adb, journal, sdk, snapshot)
    elif args.action == "pause":
        if args.apply:
            unchanged_user(adb, args.user)
            print(json.dumps(inspect(adb, args.package, args.user, "pause"), ensure_ascii=False))
        else:
            print("仅预览：暂停接管，保留系统 HOME 与应用数据。")
    else:
        if snapshot.get("paused") and not args.resume:
            raise ValueError("接管已被主动暂停；只有用户要求恢复时才使用 --resume")
        routes = snapshot.get("routes", {})
        if routes.get("proxyReady"):
            raise ValueError("当前使用独立 HOME 代理，请沿用原代理安装记录修复，不自动改为内置守护")
        own = args.package + "/home.quiet.tv.HomeActivity"
        adb_only = (snapshot.get("systemHome") == own and not snapshot.get("guardConfigured")
                    and not snapshot.get("directHomeConfigured"))
        repair = SimpleNamespace(serial=args.serial, user=args.user, package=args.package, adb=args.adb,
            apply=args.apply, pause=False, direct_home=snapshot.get("directHomeConfigured", False),
            home=None if adb_only or snapshot.get("directHomeConfigured") else snapshot.get("originalHome") or None,
            keep_adb=True, adb_only=adb_only, record=journal.directory / "guard-before.json")
        guard.configure(repair, adb)
        print("配置流程结束；实体 HOME、重启、冷启动、待机唤醒须分别验收。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("inspect", "repair", "pause", "restore", "disable", "restore-app"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--user", required=True, type=int)
    parser.add_argument("--package", default=tcl.PACKAGE, choices=(tcl.PACKAGE, "com.ts.launcher", "com.coocaa.quiettv"))
    parser.add_argument("--target", help="本次明确选择的一个应用包名")
    parser.add_argument("--from-record", type=Path, help="禁用时保存的原始 record.jsonl")
    parser.add_argument("--record", required=True, type=Path, help="全新的电脑私有记录目录")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--resume", action="store_true", help="用户明确要求重新启用已暂停的接管")
    parser.add_argument("--adb", default="adb")
    args = parser.parse_args()
    if args.user < 0 or args.target and not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+", args.target):
        parser.error("用户或包名无效")
    if args.action in ("disable", "restore-app") and not args.target:
        parser.error("需要 --target 指定单个应用")
    if args.action == "restore-app" and not args.from_record:
        parser.error("恢复需要禁用时的 --from-record")
    if args.resume and args.action != "repair" or args.apply and args.action == "inspect":
        parser.error("inspect 只读；--resume 仅用于 repair")
    journal = None
    try:
        journal = tcl.Journal(args.record)
        run(args, RecordedAdb(args.adb, args.serial, journal), journal)
        return 0
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        if journal:
            journal.event("failed", error=str(error))
        print("维护未完成：" + str(error), file=sys.stderr)
        return 1
    finally:
        if journal:
            journal.close()


if __name__ == "__main__":
    sys.exit(main())
