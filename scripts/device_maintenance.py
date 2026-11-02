#!/usr/bin/env python3
"""Computer-side inspect, repair, restore and single-app disable/restore for Quiet TV."""
import argparse
import json
import re
import subprocess
import sys
import time
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


def desktop_status(snapshot):
    status = snapshot.get("desktopStatus")
    if "home-status-v1" in snapshot.get("capabilities", []) and isinstance(status, dict) and status.get("code") in {
            "unconfigured", "incomplete", "pending", "verified", "repair", "paused", "limited"}:
        return status
    return {"code": "unknown", "title": "此 APK 未提供接管状态", "next": "通过电脑逐项核对配置与实体结果，不能据此判定接管成功。"}


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


def input_profile_matches(desired, saved):
    if not isinstance(desired, dict) or not isinstance(saved, dict):
        return False
    if desired.get("schemaVersion") != 2 or "packageVersions" in desired:
        return saved == desired
    # Version 2 binds the requested mapping to the system packages actually read by the app.
    # Only this app-generated field may differ; never accept changed port records or aliases.
    versions = saved.get("packageVersions")
    return (isinstance(versions, dict)
            and all(isinstance(k, str) and isinstance(v, str) and v.startswith(k + ":") for k, v in versions.items())
            and {k: v for k, v in saved.items() if k != "packageVersions"} == desired)


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
        if desired.get("schemaVersion") == 2 and "input-profile-v2" not in before.get("capabilities", []):
            raise ValueError("当前 Quiet TV 不支持版本 2 的信号源配置")
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
    if args.action == "profile-set" and not input_profile_matches(desired, saved) or args.action == "profile-reset" and saved:
        raise ValueError("本机配置读回不匹配")
    print(json.dumps(result["inputProfile"], ensure_ascii=False, indent=2))
    if args.action == "input-open":
        print("已提交画面切换请求；请核对 lastInput 回执和实际画面，不能把请求当作切换成功。")


def entry_record(args, journal, snapshot):
    value = {"schemaVersion": 1, "serial": args.serial, "package": args.package, "user": args.user,
             "fingerprint": snapshot["fingerprint"], "systemEntry": snapshot["systemEntry"]}
    (journal.directory / "entry.json").write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(value["systemEntry"], ensure_ascii=False, indent=2))
    return value


def entry_action(args, adb, journal, snapshot):
    if "system-entry-test-v1" not in snapshot.get("capabilities", []):
        raise ValueError("已安装 APK 缺少系统入口测试接口；保留原配置，不能绕过应用改写入口")
    current = inspect(adb, args.package, args.user, "entry_list", extras={"entry": args.entry})
    if not same_scope(snapshot, current):
        raise ValueError("读取入口时设备或用户已变化")
    entry = current["systemEntry"]
    if args.action == "entry-list":
        entry_record(args, journal, current)
        return
    previous = json.loads(args.from_entry.read_text(encoding="utf-8"))
    if (previous.get("schemaVersion") != 1 or previous.get("serial") != args.serial
            or previous.get("package") != args.package or previous.get("user") != args.user
            or previous.get("fingerprint") != current["fingerprint"]
            or previous.get("systemEntry", {}).get("entry") != args.entry):
        raise ValueError("上一入口记录不属于当前连接、应用、用户或固件")
    before = previous["systemEntry"]
    if args.action == "entry-test":
        if before.get("scope") != entry["scope"]:
            raise ValueError("候选或成功记录已变化，请重新 entry-list")
        candidates = entry["candidates"]
        selected = args.candidate or (candidates[0]["id"] if candidates else "")
        if not any(item["id"] == selected for item in candidates):
            raise ValueError("没有对应的可调用入口；请检查本机系统组件和诊断，不能试写任意组件")
        extras = {"entry": args.entry, "candidate": selected, "scope": entry["scope"]}
    else:
        test = before.get("test", {})
        actual = entry.get("test", {})
        if not test.get("request") or actual.get("request") != test["request"]:
            raise ValueError("原测试已结束或被其他测试替换，请重新测试")
        if args.action == "entry-status":
            entry_record(args, journal, current)
            return
        if actual.get("state") != "DISPATCHED_UNVERIFIED":
            raise ValueError("当前测试尚未派发、已失败或过期，不能确认")
        if actual.get("fallback") and args.worked == "yes":
            raise ValueError("打开设置首页不能确认成网络设置成功")
        extras = {"entry": args.entry, "request": test["request"], "worked": args.worked == "yes", "source": args.evidence_source}
    journal.event("entry_plan", operation=args.action, parameters=extras)
    if not args.apply:
        print(json.dumps({"operation": args.action, "parameters": extras}, ensure_ascii=False, indent=2))
        print("仅预览；entry-test 会从 Quiet TV 打开指定页面，entry-confirm 才保存明确确认的结果。")
        return
    unchanged_user(adb, args.user)
    fresh = inspect(adb, args.package, args.user, "entry_status", extras={"entry": args.entry})
    if not same_scope(current, fresh) or fresh["systemEntry"] != entry:
        raise ValueError("执行前入口或测试状态已变化，请重新读取")
    result = inspect(adb, args.package, args.user, args.action.replace("-", "_"), extras=extras)
    if args.action == "entry-test":
        request = result["systemEntry"].get("test", {}).get("request")
        if not request or result["systemEntry"]["test"].get("candidate") != selected:
            raise ValueError("应用未确认本次入口请求")
        deadline = time.monotonic() + 10
        while result["systemEntry"]["test"]["state"] in ("PENDING", "PREPARING") and time.monotonic() < deadline:
            time.sleep(0.25)
            unchanged_user(adb, args.user)
            result = inspect(adb, args.package, args.user, "entry_status", extras={"entry": args.entry})
            if (not same_scope(current, result)
                    or result["systemEntry"].get("test", {}).get("request") != request):
                raise ValueError("测试期间设备或入口请求已变化")
    else:
        expected = "CONFIRMED" if args.worked == "yes" else "REJECTED"
        saved = result["systemEntry"].get("test", {})
        if saved.get("request") != extras["request"] or saved.get("state") != expected or saved.get("source") != args.evidence_source:
            raise ValueError("入口确认未读回")
        journal.event("entry_observation", source=args.evidence_source, worked=args.worked, observed=args.observed)
    entry_record(args, journal, result)
    state = result["systemEntry"].get("test", {}).get("state")
    journal.event("entry_result", result=result["systemEntry"])
    if state in ("FAILED", "CANCELLED_UNVERIFIED", "EXPIRED"):
        raise ValueError("入口测试未完成：" + state + "；已保存 entry.json，不自动重放请求")
    if args.action == "entry-test":
        print("请求状态已记录；请核对实际页面，再用 entry-confirm --worked yes/no --observed 描述实际结果。")
    else:
        print("已保存并读回入口选择。" if args.worked == "yes" else "已记录入口未正常打开；可从候选中明确选择下一项测试。")


SOURCE_ACTIONS = ("source-search", "source-status", "source-test", "source-confirm", "source-cancel",
                  "source-observe", "source-observe-stop", "source-evidence", "source-restore")


def source_operation(args, adb, journal, snapshot):
    if "source-pairing-v1" not in snapshot.get("capabilities", []):
        raise ValueError("已安装 APK 缺少 source-pairing-v1，不绕过接口改写配置")
    previous = None
    if args.from_source:
        previous = json.loads(args.from_source.read_text(encoding="utf-8"))
        if (previous.get("serial") != args.serial or previous.get("package") != args.package
                or previous.get("user") != args.user or previous.get("fingerprint") != snapshot.get("fingerprint")):
            raise ValueError("信号源记录不属于本机、用户或固件")
    extras = {}
    action = args.action
    if action in ("source-test", "source-confirm", "source-restore"):
        if not previous:
            raise ValueError("需要上一阶段的 --from-source source.json")
        state = previous["sourcePairing"]
        if action == "source-test":
            candidates = [item for item in state.get("candidates", []) if item.get("id") == args.candidate]
            if len(candidates) != 1:
                raise ValueError("需要用 --candidate 明确选择一个真实搜索候选")
            extras = {"candidate": args.candidate, "scope": candidates[0]["scope"], "revision": state["revision"]}
            if args.label:
                extras["label"] = args.label
        elif action == "source-confirm":
            test = state.get("test", {})
            if test.get("state") != "DISPATCHED_UNVERIFIED" or not test.get("request"):
                raise ValueError("需要已派发且尚未确认的测试记录")
            extras = {"request": test["request"], "worked": args.worked == "yes", "physical": args.physical_confirmed,
                      "source": args.evidence_source, "observed": args.observed}
        else:
            extras = {"revision": state["revision"], "defaults": args.defaults}
    elif action == "source-observe":
        if "source-observation-v1" not in snapshot.get("capabilities", []):
            raise ValueError("APK 缺少 source-observation-v1")
        extras = {"raw": args.include_raw}
    elif action == "source-observe-stop":
        extras = {"source": args.evidence_source, "observation_result": args.observation_result, "hdmi_port": args.hdmi_port or 0, "physical": args.physical_confirmed}
    readonly = action in ("source-search", "source-status", "source-evidence")
    journal.event("source_plan", action=action, extras=extras, scope=snapshot)
    if not readonly and not args.apply:
        print(json.dumps({"action": action, "parameters": extras}, ensure_ascii=False, indent=2))
        print("仅预览；测试会切换界面，确认会保存配置，采集会短时观察原厂操作；执行使用 --apply。")
        return
    unchanged_user(adb, args.user)
    result = inspect(adb, args.package, args.user, action.replace("-", "_"), extras=extras)
    record = {"schemaVersion": 1, "serial": args.serial, "package": args.package, "user": args.user,
              "fingerprint": result["fingerprint"], "sourcePairing": result["sourcePairing"]}
    if previous:
        record["expectedRequest"] = previous.get("sourcePairing", {}).get("test", {}).get("request")
    if "sourceEvidence" in result:
        record["sourceEvidence"] = result["sourceEvidence"]
    (journal.directory / "source.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    journal.event("source_result", result=record)
    print(json.dumps(record["sourcePairing"], ensure_ascii=False, indent=2))
    print("已保存 source.json；异步搜索、测试和保存用 source-status 读取最终状态，不重放操作。")


def restore_home(args, adb, journal, sdk, snapshot):
    if snapshot.get("takeoverRecord"):
        return restore_takeover(args, adb, journal, sdk, snapshot)
    if snapshot.get("windowHomeGuard", {}).get("configured"):
        home = snapshot["windowHomeGuard"].get("home")
        if snapshot.get("systemHome") not in (None, "", home):
            raise ValueError("系统 HOME 已变化，需先核对其他桌面改动")
        journal.event("restore_plan", before=snapshot, command="pause_window_guard")
        if not args.apply:
            print("计划：暂停窗口守护，保留系统原桌面；不调用桌面切换命令。")
            return
        unchanged_user(adb, args.user)
        result = inspect(adb, args.package, args.user, "pause")
        if not result.get("paused") or result.get("windowHomeGuard", {}).get("enabled"):
            raise ValueError("窗口守护暂停未确认")
        journal.event("restore_result", result=result)
        print("窗口守护已暂停，系统 HOME 保持原状；请按实体 HOME 验证。")
        return
    if (snapshot.get("adbHomeGuard", {}).get("configured")
            and snapshot.get("systemHome") in (None, "", snapshot["adbHomeGuard"].get("home"))):
        journal.event("restore_plan", before=snapshot, command="pause_adb_guard")
        if not args.apply:
            print("仅预览：停止本机 ADB 桌面守护；此路线未修改系统 HOME，不执行 HOME 写入。")
            return
        unchanged_user(adb, args.user)
        result = inspect(adb, args.package, args.user, "pause")
        if result.get("adbHomeGuard", {}).get("enabled") or not result.get("paused"):
            raise ValueError("ADB 守护暂停未确认")
        journal.event("restore_result", snapshot=result)
        print("本机 ADB 守护已暂停，系统 HOME 未写入；原桌面实体 HOME、开机及唤醒仍须分别验收。")
        return
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


def native_observer_needed(snapshot):
    routes = snapshot.get("routes", {})
    if routes.get("nativeObserverConfigured") or routes.get("nativeObserverSelected"):
        return True
    if routes.get("homeRoute") != "DIRECT":
        return False
    # Resolver/binding output is never scene evidence. Only current user-recorded failures qualify.
    return any(item.get("result") == "失败" and item.get("source") == "user"
               and item.get("recordedAt", 0) > 0 and item.get("evidenceId")
               for item in routes.get("sceneEvidence", {}).values() if isinstance(item, dict))


def configure_window_guard(args, adb, journal, sdk, snapshot):
    state = snapshot.get("windowHomeGuard", {})
    if sdk != 18 or "window-home-guard-v1" not in snapshot.get("capabilities", []) or state.get("supported") is not True:
        raise ValueError("需要 API18 且已安装包含 window-home-guard-v1 的新 APK")
    if snapshot.get("paused") and not args.resume:
        raise ValueError("接管已暂停，明确恢复时使用 --resume")
    home = guard.canonical_home(args.home or state.get("home") or snapshot.get("originalHome") or "")
    if not home or home not in {guard.canonical_home(value) for value in state.get("candidates", [])}:
        raise ValueError("需要用 --home 指定应用实际检测到的系统 HOME 完整组件")
    service = args.package + "/home.quiet.tv.LegacyWindowHomeGuardService"
    def setting(operation, key, *values):
        return adb.shell("settings", "--user", args.user, operation, "secure", key, *values)
    before = setting("get", "enabled_accessibility_services")
    enabled = setting("get", "accessibility_enabled")
    if enabled not in ("0", "1", "null"):
        raise ValueError("无障碍总开关原值未确认，未修改")
    entries = [entry for entry in before.split(":") if entry and entry != "null"
               and guard.canonical_home(entry) != service]
    desired = ":".join(dict.fromkeys([*entries, service]))
    journal.event("window_guard_plan", home=home, before=snapshot,
                  accessibility_before=before, accessibility_enabled_before=enabled,
                  accessibility_after=desired)
    print("计划：仅登记 API18 窗口守护并保存 " + home + "；保留其他无障碍服务、系统 HOME 和原桌面。")
    if not args.apply:
        print("仅预览；不授予应用写安全设置权限，不配置本机 ADB 或使用统计。")
        return
    unchanged_user(adb, args.user)
    if not same_scope(snapshot, inspect(adb, args.package, args.user)):
        raise ValueError("执行前设备或固件已变化")
    if setting("get", "enabled_accessibility_services") != before or setting("get", "accessibility_enabled") != enabled:
        raise ValueError("无障碍原值已变化，未覆盖")
    if desired != before:
        setting("put", "enabled_accessibility_services", desired)
    if setting("get", "enabled_accessibility_services") != desired:
        raise ValueError("窗口守护登记未读回；停止后续配置并保留记录")
    unchanged_user(adb, args.user)
    if enabled != "1":
        setting("put", "accessibility_enabled", "1")
    if setting("get", "accessibility_enabled") != "1":
        raise ValueError("无障碍总开关未读回；停止后续配置并保留记录")
    unchanged_user(adb, args.user)
    result = inspect(adb, args.package, args.user, "window_guard_configure", home=home, extras={"resume":args.resume})
    saved = result.get("windowHomeGuard", {})
    if not saved.get("enabled") or not saved.get("accessibilityAllowed") or saved.get("home") != home:
        raise ValueError("窗口守护配置读回不一致")
    journal.event("window_guard_configured", result=result)
    print(json.dumps(saved, ensure_ascii=False, indent=2))
    print("窗口守护配置已保存；服务绑定、实际页面事件、实体 HOME、开机及唤醒须分别验收。")


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
        print("桌面接管：" + desktop_status(snapshot)["title"], file=sys.stderr)
    elif args.action == "repair" and (getattr(args, "window_guard", False)
            or sdk == 18 and snapshot.get("windowHomeGuard", {}).get("configured")
                and not getattr(args, "adb_guard", False) and not getattr(args, "native_observer", False)):
        configure_window_guard(args, adb, journal, sdk, snapshot)
    elif args.action == "repair" and getattr(args, "adb_guard", False):
        if sdk != 19 or "adb-home-guard-v1" not in snapshot.get("capabilities", []):
            raise ValueError("需要 API19 且已安装包含 adb-home-guard-v1 的新 APK")
        if snapshot.get("paused") and not args.resume:
            raise ValueError("接管已暂停，明确恢复时使用 --resume")
        home = guard.canonical_home(args.home or "")
        if not home or args.port is None or not 1 <= args.port <= 65535:
            raise ValueError("需要实际原桌面组件 --home 和已核对的本机 TCP 端口 --port")
        journal.event("adb_guard_plan", home=home, port=args.port, before=snapshot)
        print("计划：由电视本机 ADB 观察 " + home + " 的真实前台；不修改 HOME 或原桌面启用状态。")
        if not args.apply:
            print("仅预览；应用首次连接需确认自己的 ADB 授权，重启后端口可用性另验。")
            return
        unchanged_user(adb, args.user)
        fresh = inspect(adb, args.package, args.user)
        if not same_scope(snapshot, fresh):
            raise ValueError("执行前设备或固件已变化")
        result = inspect(adb, args.package, args.user, "adb_guard_configure", home=home,
                         extras={"port":args.port, "resume":args.resume})
        saved = result.get("adbHomeGuard", {})
        if not saved.get("enabled") or not saved.get("bootEnabled") or saved.get("home") != home or saved.get("port") != args.port:
            raise ValueError("本机 ADB 守护配置读回不一致")
        journal.event("adb_guard_configured", result=result)
        print(json.dumps(saved, ensure_ascii=False, indent=2))
        print("本机 ADB 守护配置已保存；连接授权、真实前台跳转、实体 HOME、断开电脑后的开机及唤醒尚未验收。")
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
    elif args.action in SOURCE_ACTIONS:
        source_operation(args, adb, journal, snapshot)
    elif args.action.startswith("entry-"):
        entry_action(args, adb, journal, snapshot)
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
        native_observer = getattr(args, "native_observer", False)
        if snapshot.get("proxy", {}).get("selected") or routes.get("relaySource") == "PROXY":
            if native_observer:
                raise ValueError("已选择 HOME 代理，不能叠加原桌面观察")
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
        direct_home = snapshot.get("directHomeConfigured", False) or routes.get("directHomeSelected", False)
        native_observer = native_observer or (snapshot.get("systemHome") == own and native_observer_needed(snapshot))
        if native_observer and direct_home:
            raise ValueError("已选择直接 HOME 按键补偿，不能叠加原桌面观察")
        adb_only = (snapshot.get("systemHome") == own and not native_observer
                    and not direct_home
                    and (not snapshot.get("guardConfigured") or routes.get("observeHome") is False))
        repair = SimpleNamespace(serial=args.serial, user=args.user, package=args.package, adb=args.adb,
            apply=args.apply, pause=False, direct_home=direct_home,
            native_observer=native_observer, resume=args.resume,
            home=None if adb_only or direct_home else snapshot.get("originalHome") or None,
            keep_adb=True, adb_only=adb_only, record=journal.directory / "guard-before.json")
        guard.configure(repair, adb)
        print("配置流程结束；实体 HOME、重启、冷启动、待机唤醒须分别验收。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("management-config", "inspect", "repair", "pause", "restore", "disable", "restore-app", "input-list", "input-open", "profile-get", "profile-set", "profile-reset", "entry-list", "entry-status", "entry-test", "entry-confirm") + SOURCE_ACTIONS)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--user", required=True, type=int)
    parser.add_argument("--package", default=tcl.PACKAGE, choices=(tcl.PACKAGE, "com.ts.launcher", "com.coocaa.quiettv"))
    parser.add_argument("--port", type=int, help="management-config 或 --adb-guard 使用的实际本机 TCP ADB 端口")
    parser.add_argument("--target", help="本次明确选择的一个应用包名")
    parser.add_argument("--profile", type=Path, help="当前固件的本机信号源配置 JSON")
    parser.add_argument("--source", help="input-list 返回的完整信号源 ID")
    parser.add_argument("--entry", choices=("settings", "network", "inputs"), help="本次系统入口功能；inputs 测试原厂面板，不修改端口映射")
    parser.add_argument("--from-source", type=Path, help="上一阶段生成的 source.json")
    parser.add_argument("--observation-result", choices=("menu", "hdmi", "failed", "unknown"), default="unknown", help="辅助识别时用户实际执行的原厂操作结果")
    parser.add_argument("--hdmi-port", type=int, help="辅助识别中实际接线的 HDMI 插口编号")
    parser.add_argument("--label", help="本次校准后的实际端口名称，需重新测试")
    parser.add_argument("--physical-confirmed", action="store_true", help="用户实际确认了端口、画面与声音")
    parser.add_argument("--include-raw", action="store_true", help="辅助识别附加相关原始系统片段，导出前须检查私人信息")
    parser.add_argument("--defaults", action="store_true", help="source-restore 恢复配对默认设置")
    parser.add_argument("--from-entry", type=Path, help="前一步生成的 entry.json")
    parser.add_argument("--candidate", help="entry-list 返回的候选 ID；省略时使用当前优先候选")
    parser.add_argument("--worked", choices=("yes", "no"), help="实际功能是否正常")
    parser.add_argument("--observed", help="实际页面或结果摘要，最多300字，不含账号或网络信息")
    parser.add_argument("--evidence-source", choices=("user", "agent"), default="user")
    parser.add_argument("--from-record", type=Path, help="禁用时保存的原始 record.jsonl")
    parser.add_argument("--record", required=True, type=Path, help="全新的电脑私有记录目录")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--resume", action="store_true", help="用户明确要求重新启用已暂停的接管")
    parser.add_argument("--native-observer", action="store_true", help="已确认默认 HOME 被厂商绕过，补充原桌面观察")
    parser.add_argument("--adb-guard", action="store_true", help="显式配置 API19 本机 ADB 桌面守护")
    parser.add_argument("--window-guard", action="store_true", help="显式配置 API18 无障碍窗口桌面守护")
    parser.add_argument("--home", help="窗口守护或 adb-guard 使用的实际原桌面完整组件")
    parser.add_argument("--adb", default="adb")
    args = parser.parse_args()
    if args.user < 0 or args.target and not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+", args.target):
        parser.error("用户或包名无效")
    if (args.adb_guard or args.window_guard) and (args.action != "repair" or args.native_observer) or args.adb_guard and args.window_guard:
        parser.error("--adb-guard 与 --window-guard 仅用于 repair，不互相叠加或与 --native-observer 混用")
    if args.home and not (args.adb_guard or args.window_guard) or args.window_guard and args.port is not None:
        parser.error("--home 仅用于指定守护；--window-guard 不配置本机 ADB 端口")
    if args.action in ("disable", "restore-app") and not args.target:
        parser.error("需要 --target 指定单个应用")
    if args.action == "restore-app" and not args.from_record:
        parser.error("恢复需要禁用时的 --from-record")
    if (args.resume or args.native_observer) and args.action != "repair" or args.apply and args.action == "inspect":
        parser.error("inspect 只读；--resume 仅用于 repair")
    if args.action == "profile-set" and not args.profile or args.action == "input-open" and not args.source:
        parser.error("profile-set 需要 --profile；input-open 需要 --source")
    if args.apply and args.action in ("input-list", "profile-get"):
        parser.error("读取信号源和配置不使用 --apply")
    if args.action.startswith("entry-"):
        if not args.entry or args.action != "entry-list" and not args.from_entry:
            parser.error("入口操作需要 --entry；测试、状态和确认需要上一阶段 --from-entry")
        if args.action in ("entry-list", "entry-status") and args.apply:
            parser.error("读取入口不使用 --apply")
        if args.action == "entry-confirm" and (args.worked is None or not args.observed or not args.observed.strip() or len(args.observed) > 300):
            parser.error("确认需要 --worked yes/no 和实际结果 --observed（最多300字）")
    if args.action in SOURCE_ACTIONS:
        if args.action in ("source-search", "source-status", "source-evidence") and args.apply:
            parser.error("读取信号源不使用 --apply")
        if args.action in ("source-test", "source-confirm", "source-restore") and not args.from_source:
            parser.error("此操作需要上一阶段 --from-source source.json")
        if args.action == "source-test" and not args.candidate:
            parser.error("测试需要明确的 --candidate")
        if args.action == "source-confirm" and (args.worked is None or not args.observed or not args.observed.strip() or len(args.observed) > 300):
            parser.error("确认需要 --worked yes/no 和实际结果 --observed（最多300字）")
        if args.label and (args.action != "source-test" or len(args.label) > 40 or re.search(r"[\x00-\x1f]", args.label)):
            parser.error("--label 仅用于本次测试的端口校准，最多40字")
        if args.include_raw and args.action != "source-observe" or args.physical_confirmed and args.action not in ("source-confirm", "source-observe-stop") or args.defaults and args.action != "source-restore":
            parser.error("--include-raw、--physical-confirmed、--defaults 须用于对应操作")
        if args.action == "source-observe-stop" and args.observation_result == "hdmi" and (not args.hdmi_port or not 1 <= args.hdmi_port <= 32 or not args.physical_confirmed or args.evidence_source != "user"):
            parser.error("HDMI 观察需要用户确认实际插口 --hdmi-port、画面和声音 --physical-confirmed")
        if args.hdmi_port is not None and args.action != "source-observe-stop" or args.observation_result != "unknown" and args.action != "source-observe-stop":
            parser.error("原厂操作结果仅用于 source-observe-stop")
    elif args.from_source or args.label or args.include_raw or args.physical_confirmed or args.defaults or args.hdmi_port is not None or args.observation_result != "unknown":
        parser.error("信号源配对参数仅用于 source-* 操作")
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
