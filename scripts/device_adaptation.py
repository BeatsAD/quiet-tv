#!/usr/bin/env python3
"""Versioned computer rules and reversible OEM HOME recipes; no device discovery or arbitrary shell."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
from types import SimpleNamespace

import configure_boot_home as boot
import configure_home_guard as guard
import device_maintenance as maintenance
import install_tcl as tcl
import home_capabilities as home
from verify_install import HOME, HOME_ARGS, component, help_text, resolver

CATALOG = Path(__file__).with_name('device-presets.json')
GOOGLE = 'com.google.android.apps.tv.launcherx'
CLASSIC = 'com.google.android.tvlauncher'
SETUP = 'com.google.android.tungsten.setupwraith'
JM = 'persist.sys.home.selectpkg'
VIDAA = 'persist.vt.use_home_pkg'


def catalog(path=CATALOG):
    root = json.loads(Path(path).read_text(encoding='utf-8'))
    if root.get('schema') != 1 or not isinstance(root.get('devices'), list):
        raise ValueError('适配规则格式无效')
    seen = set()
    for device in root['devices']:
        fp = device.get('fingerprint')
        if not isinstance(fp, str) or not fp or fp in seen:
            raise ValueError('执行规则缺少唯一固件指纹')
        seen.add(fp)
        home = device.get('home', {})
        if home.get('preserveOriginal') and (home.get('disableVerified') or home.get('uninstallVerified')):
            raise ValueError('保留原桌面与禁用规则冲突')
        if home.get('directTakeover') and not all(k in home for k in ('versionCode', 'user', 'component')):
            raise ValueError('直接接管缺少精确身份')
    for ref in root.get('references', []):
        if any(k in ref for k in ('fingerprint', 'apps', 'home')):
            raise ValueError('未验收参考不能作为执行规则')
    return root


def match(root, brand, model, fingerprint):
    matches = [d for d in root['devices'] if d['fingerprint'] == fingerprint
               and ('brand' not in d or d['brand'].lower() == brand.lower())
               and ('model' not in d or d['model'] == model)]
    if len(matches) > 1:
        raise ValueError('机型规则冲突')
    return copy.deepcopy(matches[0]) if matches else None


def discover(adb, user, path=CATALOG):
    device = boot.identity(adb, user)
    brand = adb.shell('getprop', 'ro.product.brand').strip()
    model = adb.shell('getprop', 'ro.product.model').strip()
    rules = catalog(path)
    selected = match(rules, brand, model, device['fingerprint'])
    return device, selected


def sync_rules(adb, user, journal, path=CATALOG, apply=False):
    device, selected = discover(adb, user, path)
    snapshot = maintenance.inspect(adb, tcl.PACKAGE, user)
    if 'adaptation-rules-v1' not in snapshot.get('capabilities', []):
        raise ValueError('APK 缺少外置适配规则接口；需更新支持该能力的安装包')
    if selected is None:
        print('没有匹配的已收录规则，保留现有配置；可继续检测通用 HOME 和守护能力。')
        return snapshot
    desired = {'schema': 1, 'devices': [selected]}
    current = snapshot['adaptation']
    journal.event('adaptation_rules_plan', device=device, before=current, rules=desired,
                  catalogSha256=tcl.digest(path))
    if current['rules'] == desired or not apply:
        return snapshot
    boot.check_device(adb, device)
    fresh = maintenance.inspect(adb, tcl.PACKAGE, user)
    if not maintenance.same_scope(snapshot, fresh) or fresh['adaptation'] != current:
        raise ValueError('执行前规则或设备状态改变')
    journal.event('adaptation_rules_attempt')
    result = maintenance.inspect(adb, tcl.PACKAGE, user, 'rules_set',
        extras={'rules_json': json.dumps(desired, ensure_ascii=False), 'revision': current['revision']})
    if result['adaptation']['rules'] != desired:
        raise ValueError('规则写入未读回')
    journal.event('adaptation_rules_saved', result=result['adaptation'])
    return result


def package(adb, user, name):
    app = maintenance.inspect(adb, tcl.PACKAGE, user, target=name)['app']
    state = maintenance.package_state(adb, name, user)
    if (not app['system'] or not app['installed'] or state['installed'] != 'true'
            or state['versionCode'] != app['versionCode'] or state['enabled'] != app['enabledSetting']
            or state.get('hidden') == 'true' or state.get('suspended') == 'true'):
        raise ValueError('原桌面包身份或状态未确认：' + name)
    return app


def prepare(adb, user, journal, route, path=CATALOG):
    device, selected = discover(adb, user, path)
    snapshot = maintenance.inspect(adb, tcl.PACKAGE, user)
    if 'takeover-record-v1' not in snapshot.get('capabilities', []):
        raise ValueError('APK 缺少专项接管记录接口')
    if snapshot.get('takeoverRecord'):
        raise ValueError('已有专项记录；先检查或恢复，不覆盖原基线')
    if snapshot.get('proxy', {}).get('selected'):
        raise ValueError('请先恢复已有 HOME 代理，不覆盖现有路线')
    setter, homes = boot.home_commands(adb, device['sdk'], user)
    actual = component(adb.shell(*resolver(adb, device['sdk'], user)))
    original = guard.canonical_home(snapshot.get('originalHome', '')) or actual
    if original.startswith(SETUP + '/'):
        alternatives = [h for h in homes if h.startswith(GOOGLE + '/')]
        if len(alternatives) != 1:
            raise ValueError('Google 设置向导之后的真实原桌面未唯一确认')
        original = alternatives[0]
    if HOME not in homes or original not in homes or original == HOME or not actual or not home.real_home(original):
        raise ValueError('缺少可用且唯一的 HOME 与恢复基线')
    if actual not in (original, HOME) and not actual.startswith(SETUP + '/'):
        raise ValueError('当前 HOME 与恢复基线不一致')
    original_app = package(adb, user, original.split('/')[0])
    if not original_app['enabled']:
        raise ValueError('原桌面不是启用状态')
    policy = (selected or {}).get('home', {})
    steps = []
    if route == 'verified':
        if (not policy.get('disableVerified') or policy.get('preserveOriginal')
                or guard.canonical_home(policy.get('component', '')) != original
                or ('versionCode' in policy and policy['versionCode'] != original_app['versionCode'])
                or ('user' in policy and policy['user'] != user)):
            raise ValueError('原桌面、固件、版本或用户不符合已收录规则')
        targets = [original_app['package']]
    elif route == 'google':
        targets = ([SETUP] if any(h.startswith(SETUP + '/') for h in homes) else []) + [GOOGLE] if original_app['package'] == GOOGLE else [CLASSIC] if original_app['package'] == CLASSIC else []
        if not targets or policy.get('preserveOriginal'):
            raise ValueError('未检测到可恢复的 Google HOME 路线')
    elif route in ('jm', 'vidaa'):
        if user != 0:
            raise ValueError('全局厂商属性只适用于已核对的主用户')
        if route == 'jm':
            usage = help_text(adb, 'pm')
            options = guard.user_options(usage, 'query-activities', device['sdk'], user)
            candidates = adb.shell('pm', 'query-activities', '--brief', *options, '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME.JM', '-p', 'com.jm.jmlauncher')
            if not any(component(line).startswith('com.jm.jmlauncher/') for line in candidates.splitlines()):
                raise ValueError('未确认 HOME.JM 系统组件')
            package(adb, user, 'com.jm.jmlauncher')
        elif original_app['package'] != boot.STOCK:
            raise ValueError('原桌面不是已支持的 VIDAA 路线')
        key = JM if route == 'jm' else VIDAA
        before = adb.shell('getprop', key).strip()
        if before == tcl.PACKAGE or not (route == 'jm' and not before or re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+', before)):
            raise ValueError('厂商路由原值未知或已被配置，不覆盖')
        steps.append(dict(kind='property', key=key, before=before, after=tcl.PACKAGE))
        targets = []
    else:
        raise ValueError('未知路线')
    if targets and adb.shell('settings', 'get', 'global', 'device_provisioned').strip() != '1':
        raise ValueError('系统尚未完成初始化，不停用 HOME 候选')
    if targets:
        home.check_stock_change(adb, device, selected, catalog(path).get('references', []), original, targets)
    for name in targets:
        app = package(adb, user, name)
        if not any(h.startswith(name + '/') for h in homes) or not app['enabled'] or app['enabledSetting'] not in (0, 1):
            raise ValueError('待处理包不是已确认的启用 HOME 候选')
        maintenance.command(adb, device['sdk'], user, 'disable-user', name)
        maintenance.command(adb, device['sdk'], user, 'default-state' if app['enabledSetting'] == 0 else 'enable', name)
        steps.append(dict(kind='package', key=name, before=str(app['enabledSetting']), after='3', version=app['version'], versionCode=app['versionCode']))
    steps.append(dict(kind='home', key=HOME, before=actual, observed=actual, restore=original, after=HOME))
    rules = {'schema': 1, 'devices': [selected] if selected else []}
    plan = dict(recipe=route, original=original, user=user, fingerprint=device['fingerprint'], target=HOME,
                keepAdb=snapshot.get('keepAdb', False), state='running', steps=steps, rules=rules)
    journal.event('adaptation_takeover_plan', device=device, snapshot=snapshot, plan=plan)
    return device, snapshot, plan, setter


def read_step(adb, device, step):
    if step['kind'] == 'home':
        return component(adb.shell(*resolver(adb, device['sdk'], device['user'])))
    if step['kind'] == 'property':
        return adb.shell('getprop', step['key']).strip()
    app = package(adb, device['user'], step['key'])
    if app['versionCode'] != step['versionCode'] or app['version'] != step['version']:
        raise ValueError('原桌面包版本已改变')
    return str(app['enabledSetting'])


def apply_plan(adb, device, snapshot, plan, setter, journal):
    boot.check_device(adb, device)
    fresh = maintenance.inspect(adb, tcl.PACKAGE, device['user'])
    if not maintenance.same_scope(snapshot, fresh) or fresh.get('takeoverRecord'):
        raise ValueError('执行前设备或恢复记录变化')
    for step in plan['steps']:
        if read_step(adb, device, step) != step['before']:
            raise ValueError('执行前原状态变化')
    targets = [s['key'] for s in plan['steps'] if s['kind'] == 'package']
    if targets:
        selected = next(iter(plan['rules']['devices']), None)
        home.check_stock_change(adb, device, selected, catalog().get('references', []), plan['original'], targets)
    # Persist the real launcher while it is still available. A setup wizard baseline is
    # recorded explicitly by the app only after validating its HOME candidate.
    saved = maintenance.inspect(adb, tcl.PACKAGE, device['user'], 'remember_takeover_home', home=plan['original'])
    revision = saved['takeoverRevision']
    def save():
        nonlocal revision
        result = maintenance.inspect(adb, tcl.PACKAGE, device['user'], 'takeover_record',
            extras={'record_json': json.dumps(plan, ensure_ascii=False), 'revision': revision})
        if result.get('takeoverRecord') != plan:
            raise ValueError('电视恢复记录未读回')
        revision = result['takeoverRevision']
    # A durable record exists before pausing or changing any system state.
    save()
    if not maintenance.inspect(adb, tcl.PACKAGE, device['user'], 'pause').get('paused'):
        raise ValueError('原守护或代理暂停未确认')
    try:
        for i, step in enumerate(plan['steps']):
            boot.check_device(adb, device)
            current = maintenance.inspect(adb, tcl.PACKAGE, device['user'])
            if current['takeoverRevision'] != revision or current['appVersion'] != snapshot['appVersion']:
                raise ValueError('执行期间应用或记录改变')
            if step['kind'] != 'home' and read_step(adb, device, step) != step['before']:
                raise ValueError('执行期间状态变化')
            step['attempted'] = True
            save()
            journal.event('adaptation_takeover_attempt', index=i, step=step)
            if step['kind'] == 'property':
                adb.shell('setprop', step['key'], step['after'])
            elif step['kind'] == 'package':
                adb.shell(*maintenance.command(adb, device['sdk'], device['user'], 'disable-user', step['key']))
            else:
                adb.shell(*setter, HOME)
            actual = read_step(adb, device, step)
            journal.event('adaptation_takeover_result', index=i, actual=actual)
            if actual != step['after']:
                raise ValueError('专项步骤未读回，停止后续修改')
            step['verified'] = True
            save()
        if any(read_step(adb, device, s) != s['after'] for s in plan['steps']):
            raise ValueError('最终接管状态再次变化')
        plan['state'] = 'applied'
        save()
    except (ValueError, OSError, subprocess.TimeoutExpired, KeyboardInterrupt):
        plan['state'] = 'incomplete'
        journal.event('adaptation_takeover_incomplete', plan=plan)
        # Never reconnect or replay writes here. The app's last durable attempted record
        # and the computer journal are sufficient for the existing restore command.
        raise
    print('专项接管已读回；恢复使用 device_maintenance.py --action restore。实体 HOME、重启、唤醒与 ADB 重连待验收。')


def keep_adb(adb, user, journal):
    args = SimpleNamespace(serial=adb.serial, user=user, package=tcl.PACKAGE, apply=True, pause=False,
                           direct_home=False, home=None, keep_adb=True, adb_only=True,
                           record=journal.directory / 'adaptation-adb-before.json')
    guard.configure(args, adb)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--user', type=int, required=True)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--record', type=Path, required=True)
    parser.add_argument('--rules', type=Path, default=CATALOG)
    parser.add_argument('--action', choices=('inspect', 'rules-set', 'takeover'), default='inspect')
    parser.add_argument('--route', choices=('verified', 'google', 'jm', 'vidaa'))
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if args.user < 0 or args.action == 'takeover' and not args.route:
        parser.error('需要非负用户；专项接管需要明确 --route')
    journal = tcl.Journal(args.record)
    try:
        adb = maintenance.RecordedAdb(args.adb, args.serial, journal)
        device, selected = discover(adb, args.user, args.rules)
        advice = home.collect(adb, device, catalog(args.rules).get('references', []), selected)
        boot.check_device(adb, device)
        journal.event('adaptation_inspect', device=device, matched=selected, advice=advice)
        (journal.directory / 'home-routes.json').write_text(json.dumps(advice, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(json.dumps({'device': device, 'matched': selected, 'advice': advice}, ensure_ascii=False, indent=2))
        if args.action == 'rules-set':
            sync_rules(adb, args.user, journal, args.rules, args.apply)
        elif args.action == 'takeover':
            values = prepare(adb, args.user, journal, args.route, args.rules)
            if args.apply:
                keep_adb(adb, args.user, journal)
                values = prepare(adb, args.user, journal, args.route, args.rules)
                apply_plan(adb, *values[:3], values[3], journal)
            else:
                print(json.dumps(values[2], ensure_ascii=False, indent=2))
        return 0
    except (ValueError, KeyError, TypeError, OSError, subprocess.TimeoutExpired, KeyboardInterrupt) as error:
        journal.event('adaptation_incomplete', error=str(error))
        print('适配未完成：' + str(error) + '；保留恢复记录，不自动重试。', file=sys.stderr)
        return 1
    finally:
        journal.close()


if __name__ == '__main__':
    sys.exit(main())
