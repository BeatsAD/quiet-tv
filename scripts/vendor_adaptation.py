#!/usr/bin/env python3
"""Computer-side TCL policies, signed HOME proxy setup and pre-migration recovery."""
import argparse
import copy
import json
from pathlib import Path
import re
import subprocess
import sys

import configure_boot_home as boot
import configure_home_guard as guard
import device_maintenance as maintenance
import install_tcl as tcl
from verify_install import component, resolver

AUTHORITIES = ('com.tcl.config.ConfigProvider', 'com.tcl.providers.config')
MODULES = ('TGuardMemoryManager', 'BackgroundProcess', 'StandbyProcess', 'AppBootPolicy')


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'))


def change(items, own, include):
    if not isinstance(items, list) or any(not isinstance(x, str) for x in items):
        raise ValueError('后台名单结构不受支持')
    return [x for x in items if x != own or include] + ([own] if include and own not in items else [])


def merge_policy(module, text, own=tcl.PACKAGE):
    rows = json.loads(text)
    if not isinstance(rows, list):
        raise ValueError('后台策略不是数组')
    if module == 'AppBootPolicy':
        if len(rows) != 1 or not isinstance(rows[0], dict):
            raise ValueError('不支持的自启动策略结构')
        root = rows[0]
        white, black = 'serviceStartForegroundWhiteList', 'serviceStartForegroundBlackList'
        if (white in root) == (black in root):
            raise ValueError('自启动策略模式不明确')
        before = compact(rows)
        key = white if white in root else black
        root[key]['packageName'] = change(root[key]['packageName'], own, key == white)
        if 'procWhiteList' in root:
            root['procWhiteList']['packageName'] = change(root['procWhiteList']['packageName'], own, True)
        if compact(rows) != before:
            major, minor = root.get('version'), root.get('subVersion')
            if type(major) is not int or type(minor) is not int or not 0 <= major < 2147483647 or not 0 <= minor <= 511:
                raise ValueError('自启动策略版本不受支持')
            root.update(version=major + (minor == 511), subVersion=0 if minor == 511 else minor + 1)
    else:
        positive = {'TGuardMemoryManager': 'KillWhiteList', 'BackgroundProcess': 'backgroundProcessIgnoreList'}.get(module)
        negative = {'BackgroundProcess': ('backgroundProcessBlackList', 'backgroundProcesKillList'),
                    'StandbyProcess': ('standbyProcessBlackList', 'standbyWakeLockBlackList')}.get(module, ())
        if positive is None and not negative:
            raise ValueError('未知后台策略')
        seen, recognized = set(), False
        for row in rows:
            name = row['name']
            if name in seen:
                raise ValueError('后台策略字段重复')
            seen.add(name)
            if name == positive or name in negative:
                row['value'] = change(row['value'], own, name == positive)
                recognized = True
        if not recognized:
            raise ValueError('未识别后台策略字段')
    return compact(rows)


def policy_row(output):
    if output.count('Row:') != 1:
        raise ValueError('后台策略不是唯一记录')
    match = re.search(r'\bproject_id=(\d+), config_content=(.*)', output, re.S)
    if not match:
        raise ValueError('后台策略缺少项目或内容')
    raw = match[2].strip()
    if not isinstance(json.loads(raw), list):
        raise ValueError('后台策略内容不是数组')
    return match[1], raw


def query_policy(adb, uri):
    return policy_row(adb.shell('content', 'query', '--user', 0, '--uri', uri, '--projection', 'project_id:config_content'))


def policies(args, adb, journal, device, snapshot):
    if args.user != 0:
        raise ValueError('厂商后台策略只适用于主用户')
    restoring = args.action == 'tcl-restore'
    plans = []
    if args.from_record:
        entries = [json.loads(l) for l in args.from_record.read_text().splitlines() if l.strip()]
        source = [e for e in entries if e.get('event') == 'tcl_policy_plan']
        if len(source) != 1 or source[0]['device'] != device or source[0].get('restoring'):
            raise ValueError('原策略记录设备或固件不匹配')
        attempted = {e['key'] for e in entries if e.get('event') == 'tcl_policy_attempt'}
        records = {p['key']: dict(before=p['before'], requested=p['after'], user=0, firmware=device['fingerprint'])
                   for p in source[0]['plans'] if p['key'] in attempted}
    else:
        records = snapshot['legacyRecords'].get('tclPolicies', {})
    available = snapshot.get('tclProviders', [])
    for module in MODULES:
        if args.module and module != args.module:
            continue
        for authority in AUTHORITIES:
            if authority not in available:
                continue
            uri = 'content://' + authority + '/' + module
            try:
                project, before = query_policy(adb, uri)
            except ValueError as error:
                journal.event('tcl_policy_unavailable', uri=uri, error=str(error))
                continue
            key = authority + '/' + module + '/' + project
            if restoring:
                old = records.get(key)
                if not old:
                    continue
                if old.get('user') != 0 or old.get('firmware') != device['fingerprint']:
                    raise ValueError('旧 TCL 策略记录的用户或固件不符')
                after = old['before']
                if json.loads(before) not in (json.loads(after), json.loads(old['requested'])):
                    raise ValueError('TCL 策略已由其他操作修改，不覆盖')
            else:
                after = merge_policy(module, before)
            plans.append(dict(key=key, uri=uri, project=project, before=before, after=after))
            break
    journal.event('tcl_policy_plan', device=device, plans=plans, restoring=restoring)
    print(json.dumps(plans, ensure_ascii=False, indent=2))
    if not plans:
        raise ValueError('未找到适用的后台策略或原记录')
    if not args.apply:
        return
    for plan in plans:
        boot.check_device(adb, device)
        fresh = maintenance.inspect(adb, tcl.PACKAGE, args.user, 'legacy_records')
        if fresh.get('tclProviders') != available or fresh.get('appVersion') != snapshot.get('appVersion'):
            raise ValueError('策略提供者或应用版本已改变')
        if query_policy(adb, plan['uri']) != (plan['project'], plan['before']):
            raise ValueError('策略在执行前变化')
        if json.loads(plan['before']) == json.loads(plan['after']):
            continue
        where = 'project_id=' + plan['project'] + " AND config_content='" + plan['before'].replace("'", "''") + "'"
        journal.event('tcl_policy_attempt', key=plan['key'])
        adb.shell('content', 'update', '--user', 0, '--uri', plan['uri'], '--bind', 'config_content:s:' + plan['after'], '--where', where)
        project, actual = query_policy(adb, plan['uri'])
        journal.event('tcl_policy_result', key=plan['key'], actual=actual)
        if project != plan['project'] or json.loads(actual) != json.loads(plan['after']):
            raise ValueError('策略写入未读回')
    print('后台策略已读回；厂商守护是否采用这些值，须在重启与待机场景验证。')


def proxy(args, adb, journal, device, snapshot):
    state = snapshot.get('proxy', {})
    original = guard.canonical_home(snapshot.get('originalHome', ''))
    if not state.get('available') or not original or not snapshot.get('originalUsable') or snapshot.get('takeoverRecord'):
        raise ValueError('没有匹配签名的已安装代理、可用原桌面或存在未恢复专项记录')
    target = guard.canonical_home(state['component'])
    current = component(adb.shell(*resolver(adb, device['sdk'], args.user)))
    chooser = current.startswith('android/') and current.endswith('ResolverActivity')
    if current not in (original, tcl.PACKAGE + '/home.quiet.tv.HomeActivity') and not chooser:
        raise ValueError('HOME 已变化或已选择代理，先使用检查／修复流程')
    mutation = maintenance.command(adb, device['sdk'], args.user, 'set-home-activity', target)
    maintenance.command(adb, device['sdk'], args.user, 'set-home-activity', original)
    journal.event('proxy_plan', device=device, original=original, before=current, target=target)
    if not args.apply:
        return
    boot.check_device(adb, device)
    if not maintenance.same_scope(snapshot, maintenance.inspect(adb, tcl.PACKAGE, args.user)):
        raise ValueError('代理执行前状态变化')
    prepared = maintenance.inspect(adb, tcl.PACKAGE, args.user, 'proxy_prepare')
    journal.event('proxy_prepared', result=prepared)
    journal.event('proxy_home_attempt')
    adb.shell(*mutation)
    if component(adb.shell(*resolver(adb, device['sdk'], args.user))) != target:
        raise ValueError('代理 HOME 未读回；代理仍回退原桌面，按记录恢复')
    result = maintenance.inspect(adb, tcl.PACKAGE, args.user, 'proxy_finish', extras={'epoch': prepared['proxyEpoch']})
    if not result.get('proxy', {}).get('ready'):
        raise ValueError('代理未确认启用')
    journal.event('proxy_configured', result=result)


def legacy_app(args, adb, journal, device, snapshot):
    if not args.target or not re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+', args.target):
        raise ValueError('历史恢复需要指定一个应用包名')
    records = snapshot['legacyRecords']
    baseline = None
    for run in reversed(records.get('bootAds', [])):
        if run.get('user') != args.user or run.get('firmware') != device['fingerprint']:
            continue
        found = [a for a in run['apps'] if a['package'] == args.target]
        if found:
            item = found[0]
            if item.get('attempted') and item.get('state') != 'restored':
                baseline = item
            break
    original = records.get('originalState', {})
    if baseline is None and original.get('package') == args.target and snapshot.get('originalHome', '').startswith(args.target + '/'):
        baseline = original
    if not baseline or baseline.get('enabledSetting') not in (0, 1) or not baseline.get('installed') or not baseline.get('enabled'):
        raise ValueError('没有此应用的有效原始启用基线')
    app = maintenance.inspect(adb, tcl.PACKAGE, args.user, target=args.target)['app']
    if (not app['installed'] or ('versionCode' in baseline and baseline['versionCode'] != app['versionCode'])
            or ('version' in baseline and baseline['version'] != app['version'])
            or ('system' in baseline and baseline['system'] != app['system'])):
        raise ValueError('历史包身份改变或安装包缺失，不能声称可恢复')
    before = maintenance.package_state(adb, args.target, args.user)
    desired = baseline['enabledSetting']
    if before['enabled'] not in (desired, 3) or before['installed'] != 'true' or before.get('hidden') == 'true' or before.get('suspended') == 'true':
        raise ValueError('应用状态已由其他操作改变')
    mutation = maintenance.command(adb, device['sdk'], args.user, 'default-state' if desired == 0 else 'enable', args.target)
    journal.event('legacy_app_restore_plan', device=device, target=args.target, baseline=baseline, before=before)
    if not args.apply or before['enabled'] == desired:
        return
    boot.check_device(adb, device)
    fresh = maintenance.inspect(adb, tcl.PACKAGE, args.user, 'legacy_records')
    if fresh['legacyRecords'] != records or maintenance.package_state(adb, args.target, args.user) != before:
        raise ValueError('原记录或应用状态已变化')
    journal.event('legacy_app_restore_attempt')
    adb.shell(*mutation)
    after = maintenance.package_state(adb, args.target, args.user)
    if after != dict(before, enabled=desired):
        raise ValueError('历史应用恢复未读回')
    journal.event('legacy_app_restored', after=after)


def hisense_restore(args, adb, journal, device, snapshot):
    old = snapshot['legacyRecords'].get('hisenseGuardRefresh', {})
    if (device['fingerprint'] != guard.HISENSE_REFRESH_FINGERPRINT or args.user != 0
            or old.get('phase') != 'unhide_pending' or old.get('package') != guard.HISENSE_REFRESH_PACKAGE
            or old.get('user') != 0 or old.get('hiddenBefore') is not True):
        raise ValueError('没有匹配固件的未完成海信刷新记录')
    mutation = maintenance.command(adb, device['sdk'], 0, 'hide', guard.HISENSE_REFRESH_PACKAGE)
    hidden = guard.hisense_hidden(adb.shell('dumpsys', 'package', guard.HISENSE_REFRESH_PACKAGE))
    journal.event('hisense_legacy_restore', device=device, before=hidden)
    if args.apply and not hidden:
        boot.check_device(adb, device)
        adb.shell(*mutation)
        if not guard.hisense_hidden(adb.shell('dumpsys', 'package', guard.HISENSE_REFRESH_PACKAGE)):
            raise ValueError('原隐藏状态恢复未确认')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--user', type=int, required=True)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--record', type=Path, required=True)
    parser.add_argument('--action', choices=('inspect', 'tcl-configure', 'tcl-restore', 'proxy', 'legacy-app-restore', 'hisense-restore'), default='inspect')
    parser.add_argument('--module', choices=MODULES)
    parser.add_argument('--target')
    parser.add_argument('--from-record', type=Path)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if args.user < 0 or args.from_record and args.action != 'tcl-restore':
        parser.error('用户或恢复参数无效')
    journal = tcl.Journal(args.record)
    try:
        adb = maintenance.RecordedAdb(args.adb, args.serial, journal)
        device = boot.identity(adb, args.user)
        snapshot = maintenance.inspect(adb, tcl.PACKAGE, args.user, 'legacy_records')
        if 'legacy-records-v1' not in snapshot.get('capabilities', []):
            raise ValueError('当前 APK 缺少历史记录导出接口')
        journal.event('vendor_before', device=device, snapshot=snapshot)
        if args.action.startswith('tcl-'):
            policies(args, adb, journal, device, snapshot)
        elif args.action == 'proxy':
            proxy(args, adb, journal, device, snapshot)
        elif args.action == 'legacy-app-restore':
            legacy_app(args, adb, journal, device, snapshot)
        elif args.action == 'hisense-restore':
            hisense_restore(args, adb, journal, device, snapshot)
        else:
            print(json.dumps(snapshot, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, KeyError, TypeError, OSError, subprocess.TimeoutExpired, KeyboardInterrupt) as error:
        journal.event('vendor_incomplete', error=str(error))
        print('厂商配置未完成：' + str(error) + '；保留原记录。', file=sys.stderr)
        return 1
    finally:
        journal.close()


if __name__ == '__main__':
    sys.exit(main())
