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


def inspect(adb, package, user, operation="inspect", target=None, home=None, extras=None):
    arguments = ["am", "broadcast", "--include-stopped-packages", "--user", user,
                 "-a", package + ".DEVICE_MAINTENANCE", "-n", package + "/home.quiet.tv.DeviceMaintenanceReceiver",
                 "--ei", "user", user, "--es", "operation", operation]
    if target:
        arguments += ["--es", "target_package", target]
    if home:
        arguments += ["--es", "home", home]
    for key, value in (extras or {}).items():
        arguments += ["--ez" if isinstance(value, bool) else "--ei" if isinstance(value, int) else "--es", key,
                      str(value).lower() if isinstance(value, bool) else value]
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
            or not all(plan["scope"].get(key) == snapshot.get(key) for key in ("package", "user", "fingerprint"))):
        raise ValueError("恢复记录的设备、用户或固件不符")
    old_app = plan["scope"].get("app", {})
    if (not old_app.get("canManage") or old_app.get("package") != args.target
            or old_app.get("system") != snapshot.get("app", {}).get("system")):
        raise ValueError("原记录缺少此包的管理资格或包类型已改变")
    if not any(item.get("event") == "app_attempt" and item.get("action") == "disable" for item in events):
        raise ValueError("记录只有预览，没有实际禁用尝试")
    return plan


def manage_app(args, adb, journal, sdk, snapshot):
    app = snapshot.get("app", {})
    if (args.action == "disable" and not app.get("canManage")) or not app.get("installed"):
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


def restore_takeover(args, adb, journal, sdk, snapshot):
    plan = inspect(adb, args.package, args.user, "takeover_plan")["restorePlan"]
    steps = plan["steps"]
    if not isinstance(steps, list) or not 0 < len(steps) <= 16:
        raise ValueError("专项恢复步骤无效")
    allowed_properties = {"persist.sys.home.selectpkg", "persist.vt.use_home_pkg", "persist.adb.tcp.port"}
    original = guard.canonical_home(plan["original"])
    if not original or original.split("/")[0] == args.package:
        raise ValueError("原桌面恢复目标无效")
    own = args.package + "/home.quiet.tv.HomeActivity"
    known_packages = {"com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.tungsten.setupwraith", original.split("/")[0]}
    prepared = {}
    for step in steps:
        kind, key = step["kind"], step["key"]
        desired = step.get("restore", step["before"]) if kind == "home" else step["before"]
        if kind == "package":
            if key not in known_packages or desired not in ("0", "1") or step["after"] != "3":
                raise ValueError("包恢复步骤无效")
            prepared[step["index"]] = command(adb, sdk, args.user, "default-state" if desired == "0" else "enable", key)
        elif kind == "property":
            if key not in allowed_properties or args.user != 0 or not re.fullmatch(r"[A-Za-z0-9_.-]*", desired):
                raise ValueError("属性恢复步骤无效")
            prepared[step["index"]] = ["setprop", key, desired]
        elif kind == "permission":
            if key != "android.permission.WRITE_SECURE_SETTINGS" or desired != "denied":
                raise ValueError("权限恢复步骤无效")
            options = guard.user_options(adb.permission_help("pm"), "revoke", sdk, args.user)
            prepared[step["index"]] = ["pm", "revoke", *options, args.package, key]
        elif kind == "preference":
            if key != "keepAdb" or desired not in ("true", "false"):
                raise ValueError("偏好恢复步骤无效")
        elif kind == "home":
            if desired != original or key != own:
                raise ValueError("HOME 恢复步骤无效")
            prepared[step["index"]] = command(adb, sdk, args.user, "set-home-activity", original)
        else:
            raise ValueError("未知专项恢复步骤")
    journal.event("takeover_restore_plan", scope=snapshot, plan=plan, commands=prepared)
    print(json.dumps(plan, ensure_ascii=False, indent=2))
    if not args.apply:
        print("仅预览：按原记录恢复；当前 ADB 保持已开启时保留对应授权、端口和选项。")
        return
    fresh = inspect(adb, args.package, args.user, "takeover_plan")
    if not same_scope(snapshot, fresh) or fresh["restorePlan"] != plan:
        raise ValueError("执行前专项记录或设备状态已变化")
    unchanged_user(adb, args.user)
    if not inspect(adb, args.package, args.user, "pause").get("paused"):
        raise ValueError("暂停接管未确认")
    ordered = [x for x in reversed(steps) if x["kind"] not in ("home", "permission")]
    ordered += [x for x in steps if x["kind"] == "home"]
    ordered += [x for x in reversed(steps) if x["kind"] == "permission"]

    def read(step):
        kind, key = step["kind"], step["key"]
        if kind == "property":
            return adb.shell("getprop", key).strip()
        if kind == "home":
            return component(adb.shell(*resolver(adb, sdk, args.user)))
        if kind == "package":
            state = package_state(adb, key, args.user)
            if state["installed"] != "true" or state["versionCode"] != step["versionCode"]:
                raise ValueError("原包安装状态或版本已变化")
            return str(state["enabled"])
        state = inspect(adb, args.package, args.user)
        if kind == "permission":
            if "permissionGranted" not in state.get("adb", {}):
                raise ValueError("应用缺少权限读回能力")
            return "granted" if state["adb"]["permissionGranted"] else "denied"
        return str(state["keepAdb"]).lower()

    proof = {}
    for step in ordered:
        unchanged_user(adb, args.user)
        current_plan = inspect(adb, args.package, args.user, "takeover_plan")["restorePlan"]
        if current_plan != plan:
            raise ValueError("恢复期间专项记录、版本或 ADB 选择已变化")
        desired = step.get("restore", step["before"]) if step["kind"] == "home" else step["before"]
        actual = read(step)
        if step["kind"] == "home":
            allowed = {original, own, step["before"], step.get("observed", "")}
            # Enabling the recorded HOME packages may clear Android's preferred activity.
            chooser = actual.startswith("android/") and actual.endswith("ResolverActivity")
            if actual and actual not in allowed and actual.split("/")[0] not in known_packages and not chooser:
                raise ValueError("当前 HOME 已被其他操作改变")
        elif actual not in (desired, step["after"]):
            raise ValueError("状态被其他操作改变，停止覆盖：" + step["key"])
        if actual != desired or step["kind"] == "home":
            journal.event("takeover_restore_attempt", step=step, actual=actual, desired=desired)
            failure = None
            try:
                if step["kind"] == "preference":
                    inspect(adb, args.package, args.user, "takeover_preference", extras={"revision":plan["revision"], "index":step["index"]})
                else:
                    adb.shell(*prepared[step["index"]])
            except ValueError as error:
                failure = error
            after = read(step)
            journal.event("takeover_restore_result", index=step["index"], actual=after, error=str(failure) if failure else None)
            if after != desired or failure:
                raise ValueError("恢复未确认；保留记录，不重复修改：" + step["key"])
        proof[str(step["index"])] = desired
    for step in ordered:
        unchanged_user(adb, args.user)
        if read(step) != proof[str(step["index"])]:
            raise ValueError("最终恢复状态再次变化，未完成确认")
    inspect(adb, args.package, args.user, "takeover_finish", extras={"revision":plan["revision"], "proof":json.dumps(proof)})
    inspect(adb, args.package, args.user, "finish_restore")
    print("专项原桌面恢复已读回；保留已选择的 ADB 保持。实体 HOME、重启及唤醒待验收。")


def input_action(args, adb, journal, snapshot):
    operation = args.action.replace("-", "_")
    before = inspect(adb, args.package, args.user, "profile_get")
    if not same_scope(snapshot, before):
        raise ValueError("读取配置时设备、用户或桌面状态已变化")
    profile = before["inputProfile"]
    journal.event("profile_before", scope=before, configuration=profile)
    if args.action in ("input-list", "profile-get"):
        print(json.dumps(inspect(adb, args.package, args.user, operation), ensure_ascii=False, indent=2))
        return
    extras = {"revision":profile["revision"]}
    desired = None
    if args.action == "profile-set":
        text = args.profile.read_text(encoding="utf-8")
        desired = json.loads(text)
        if not isinstance(desired, dict) or desired.get("fingerprint") != snapshot["fingerprint"] or len(text) > 8192:
            raise ValueError("配置必须是当前固件的 JSON 对象，且不超过 8192 字符")
        extras["profile_json"] = text
    elif args.action == "input-open":
        extras["source"] = args.source
    journal.event("input_plan", operation=operation, extras=extras)
    print(json.dumps({"operation":operation, "parameters":extras}, ensure_ascii=False, indent=2))
    if not args.apply:
        print("仅预览；输入测试会切换画面，配置修改只影响本机信号源。")
        return
    unchanged_user(adb, args.user)
    fresh = inspect(adb, args.package, args.user, "profile_get")
    if not same_scope(before, fresh) or fresh["inputProfile"]["revision"] != profile["revision"]:
        raise ValueError("执行前设备或本机配置已改变")
    result = inspect(adb, args.package, args.user, operation, extras=extras)
    journal.event("input_result", result=result)
    saved = result["inputProfile"]["profile"]
    if args.action == "profile-set" and saved != desired or args.action == "profile-reset" and saved:
        raise ValueError("本机配置读回不匹配")
    print(json.dumps(result["inputProfile"], ensure_ascii=False, indent=2))
    if args.action == "input-open":
        print("已提交画面切换请求；请核对 lastInput 回执和实际画面，不能把请求当作切换成功。")


def restore_home(args, adb, journal, sdk, snapshot):
    if snapshot.get("takeoverRecord"):
        return restore_takeover(args, adb, journal, sdk, snapshot)
    original = guard.canonical_home(snapshot.get("originalHome", ""))
    own = args.package + "/home.quiet.tv.HomeActivity"
    current = component(adb.shell(*resolver(adb, sdk, args.user)))
    if not original or not snapshot.get("originalUsable"):
        raise ValueError("缺少可用的原桌面记录；请按原安装／接管记录恢复应用状态后再继续，不猜原桌面")
    proxy = snapshot.get("proxy", {})
    recorded_proxy = (proxy.get("selected") or proxy.get("pending") or proxy.get("pausePending")) and proxy.get("available") and proxy.get("component") == current
    if current not in (own, original) and not recorded_proxy:
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
    elif args.action == "management-config":
        if "daily-app-management-v1" not in snapshot.get("capabilities", []):
            raise ValueError("已安装 APK 尚不支持日常应用管理配置")
        if args.port is None or not 1 <= args.port <= 65535:
            raise ValueError("需要已核对的本机 TCP ADB 端口 --port")
        if args.apply:
            unchanged_user(adb, args.user)
            result = inspect(adb, args.package, args.user, "management_config", extras={"port": args.port})
            if result.get("dailyManagement", {}).get("port") != args.port:
                raise ValueError("应用管理端口保存未确认")
            journal.event("management_config", result=result)
            print("已保存本机 TCP 端口；须在电视应用管理连接并确认应用自己的 ADB 授权。端口配置不等于权限已可用。")
        else:
            print("仅预览：保存本机 TCP ADB 端口 " + str(args.port) + "，不打开端口、不修改应用。")
    elif args.action.startswith(("input-", "profile-")):
        input_action(args, adb, journal, snapshot)
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
        if snapshot.get("proxy", {}).get("selected") or routes.get("relaySource") == "PROXY":
            if args.apply:
                unchanged_user(adb, args.user)
                result = inspect(adb, args.package, args.user, "proxy_repair", extras={"resume":args.resume})
                journal.event("proxy_repair", result=result)
                if not result.get("proxy", {}).get("ready"):
                    raise ValueError("原 HOME 代理修复未确认")
            else:
                print("计划：修复已选择的同签名 HOME 代理，不切换为内置中转。")
            repair = SimpleNamespace(serial=args.serial, user=args.user, package=args.package, adb=args.adb,
                apply=args.apply, pause=False, direct_home=False, home=None, keep_adb=True, adb_only=True,
                record=journal.directory / "adb-before.json")
            guard.configure(repair, adb)
            return
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
    parser.add_argument("action", choices=("management-config", "inspect", "repair", "pause", "restore", "disable", "restore-app", "input-list", "input-open", "profile-get", "profile-set", "profile-reset"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--user", required=True, type=int)
    parser.add_argument("--package", default=tcl.PACKAGE, choices=(tcl.PACKAGE, "com.ts.launcher", "com.coocaa.quiettv"))
    parser.add_argument("--port", type=int, help="management-config 使用的本机 TCP ADB 端口")
    parser.add_argument("--target", help="本次明确选择的一个应用包名")
    parser.add_argument("--profile", type=Path, help="当前固件的本机信号源配置 JSON")
    parser.add_argument("--source", help="input-list 返回的完整信号源 ID")
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
    if args.action == "profile-set" and not args.profile or args.action == "input-open" and not args.source:
        parser.error("profile-set 需要 --profile；input-open 需要 --source")
    if args.apply and args.action in ("input-list", "profile-get"):
        parser.error("读取信号源和配置不使用 --apply")
    journal = None
    try:
        journal = tcl.Journal(args.record)
        run(args, RecordedAdb(args.adb, args.serial, journal), journal)
        return 0
    except (ValueError, KeyError, TypeError, OSError, subprocess.TimeoutExpired) as error:
        if journal:
            journal.event("failed", error=str(error))
        print("维护未完成：" + str(error), file=sys.stderr)
        return 1
    finally:
        if journal:
            journal.close()


if __name__ == "__main__":
    sys.exit(main())
