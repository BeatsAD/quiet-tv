#!/usr/bin/env python3
"""Firmware-scoped boot-ad maintenance. Preview by default; never replay shell from records."""
import argparse
import copy
import json
from pathlib import Path
import subprocess
import sys

import configure_boot_home as boot
import configure_home_guard as guard
import device_adaptation as adaptation
import device_maintenance as maintenance
import install_tcl as tcl

CATALOG = Path(__file__).with_name('device-presets.json')
# A rule selects an implemented operation, never a downloaded command or arbitrary key.
SETTINGS = {
    'xiaomi-boot-ad-switch': ('system', 'boot_ad_switch', '0', 'com.mitv.tvhome'),
    'hisense-ad-block': ('global', 'com.hisense.service.ad_block', '1', 'com.hisense.service.ad'),
}
PROPERTY = 'skyworth-bootvideo-time'
AD_PACKAGES = {'com.tcl.bootadservice', 'com.tianci.ad', 'com.hisense.service.ad',
               'com.hisense.business.ad', 'com.yunos.advert.service'}


def rules(device):
    entries = copy.deepcopy(device.get('bootAds', []))
    if not isinstance(entries, list):
        raise ValueError('开机广告规则须为数组')
    for app in device.get('apps', []):
        if app.get('category') == 'bootAd':
            if set(app.get('actions', [])) != {'ENABLE', 'DISABLE'} or app.get('recommendDisable'):
                raise ValueError('广告服务仅允许独立停用及恢复，不作为默认精简')
            entries.append({**app, 'id': 'disable:' + app['package']})
    seen = set()
    for rule in entries:
        if not all(device.get(k) for k in ('brand', 'model', 'fingerprint')):
            raise ValueError('广告规则必须精确限定品牌、型号和固件指纹')
        name, package = rule.get('id'), rule.get('package')
        if name in SETTINGS:
            valid = package == SETTINGS[name][3]
        elif name == PROPERTY:
            valid = package == 'com.skyworth.tv'
        else:
            valid = package in AD_PACKAGES and name == 'disable:' + str(package)
        if not valid or name in seen:
            raise ValueError('未实现或重复的广告操作')
        seen.add(name)
        if (rule.get('verified') is not True or type(rule.get('versionCode')) is not int
                or rule['versionCode'] < 0 or not all(rule.get(k) for k in ('purpose', 'impact', 'evidence'))
                or not rule.get('source', '').startswith('https://')):
            raise ValueError('广告执行规则缺少本项目验收、精确包版本、影响或证据')
    return entries


def catalog(path=CATALOG):
    root = adaptation.catalog(path)
    for device in root['devices']:
        rules(device)
    for ref in root.get('references', []):
        if 'bootAds' in ref:
            raise ValueError('调查候选不能携带广告执行规则')
    return root


def identity(adb, user):
    scope = boot.identity(adb, user)
    scope.update(brand=adb.shell('getprop', 'ro.product.brand').strip(),
                 model=adb.shell('getprop', 'ro.product.model').strip(),
                 hardwareSerial=(adb.shell('getprop', 'ro.serialno').strip()
                                 or adb.shell('getprop', 'ro.boot.serialno').strip()))
    return scope


def selected_rule(device, name):
    found = [rule for rule in rules(device) if rule['id'] == name]
    if len(found) != 1:
        raise ValueError('没有匹配的已验收广告规则；调查候选不能执行')
    return found[0]


def package_state(adb, rule, user):
    state = maintenance.package_state(adb, rule['package'], user)
    if (state['versionCode'] != rule['versionCode'] or state.get('installed') != 'true'
            or state.get('hidden') == 'true' or state.get('suspended') == 'true'):
        raise ValueError('广告相关包版本、安装或用户状态不符合规则')
    return state


def read_value(adb, user, rule):
    name = rule['id']
    if name in SETTINGS:
        namespace, key, _, _ = SETTINGS[name]
        if namespace == 'global' and user != 0:
            raise ValueError('全局广告设置仅允许已核对的主用户')
        # Keep only this key, including presence. Do not log the user's whole settings table.
        lines = adb.shell('settings', '--user', user, 'list', namespace).splitlines()
        if any(line and '=' not in line for line in lines):
            raise ValueError('系统设置查询结果无法确认')
        values = [line.split('=', 1)[1] for line in lines if line.startswith(key + '=')]
        if len(values) > 1 or values and values[0] not in ('0', '1'):
            raise ValueError('广告设置原值不在可精确恢复的范围内')
        return dict(present=bool(values), value=values[0] if values else None)
    if name == PROPERTY:
        if user != 0:
            raise ValueError('厂商全局属性仅允许已核对的主用户')
        value = adb.shell('getprop', 'persist.sys.bootvideo.time').strip()
        # An absent persistent property cannot be faithfully removed by ordinary shell.
        if not value.isascii() or not value.isdecimal() or len(value) > 5:
            raise ValueError('启动视频属性原值缺失或无法精确恢复，保持只读')
        return dict(present=True, value=value)
    return dict(present=True, value=package_state(adb, rule, user)['enabled'])


def desired(rule):
    return dict(present=True, value=SETTINGS[rule['id']][2] if rule['id'] in SETTINGS
                else '1' if rule['id'] == PROPERTY else 3)


def command(adb, scope, rule, value):
    name, user = rule['id'], scope['user']
    if name in SETTINGS:
        namespace, key, _, _ = SETTINGS[name]
        if namespace == 'global' and user != 0:
            raise ValueError('全局广告设置仅允许已核对的主用户')
        if value not in (dict(present=False, value=None), dict(present=True, value='0'), dict(present=True, value='1')):
            raise ValueError('设置恢复值无效')
        return ['settings', '--user', user, 'put' if value['present'] else 'delete', namespace, key,
                *([value['value']] if value['present'] else [])]
    if name == PROPERTY:
        text = value.get('value')
        if user != 0 or value.get('present') is not True or not isinstance(text, str) or not text.isascii() or not text.isdecimal() or len(text) > 5:
            raise ValueError('属性恢复值无效')
        return ['setprop', 'persist.sys.bootvideo.time', text]
    setting = value.get('value')
    if value.get('present') is not True or type(setting) is not int or setting not in (0, 1, 3):
        raise ValueError('包启用状态无效')
    verb = {0: 'default-state', 1: 'enable', 3: 'disable-user'}[setting]
    return maintenance.command(adb, scope['sdk'], user, verb, rule['package'])


def prepare(adb, scope, device, name):
    rule = selected_rule(device, name)
    app = package_state(adb, rule, scope['user'])
    before, after = read_value(adb, scope['user'], rule), desired(rule)
    if name.startswith('disable:') and before['value'] not in (0, 1):
        raise ValueError('广告服务原来不是默认启用或手动启用，不重复停用')
    command(adb, scope, rule, before)  # Recovery capability must exist before the first write.
    command(adb, scope, rule, after)
    return dict(schema=1, scope=scope, device=device, ruleId=name, packageState=app, before=before, after=after)


def validate_plan(plan):
    if plan.get('schema') != 1:
        raise ValueError('广告恢复记录版本无效')
    scope, device = plan['scope'], plan['device']
    rule = selected_rule(device, plan['ruleId'])
    if (adaptation.match({'devices': [device]}, scope['brand'], scope['model'], scope['fingerprint']) is None
            or plan['packageState']['versionCode'] != rule['versionCode'] or plan['after'] != desired(rule)):
        raise ValueError('广告恢复记录与原规则不符')
    if rule['id'].startswith('disable:') and (type(plan['before'].get('value')) is not int or plan['before']['value'] not in (0, 1)):
        raise ValueError('广告服务恢复基线无效')
    return rule


def restore_plan(path):
    events = [json.loads(line) for line in path.read_text(encoding='utf-8').splitlines() if line.strip()]
    plans = [event['plan'] for event in events if event.get('event') == 'boot_ad_plan']
    if len(plans) != 1 or not any(event.get('event') == 'boot_ad_attempt' and event.get('restoring') is False
                                  and event.get('ruleId') == plans[0]['ruleId'] for event in events):
        raise ValueError('需要有实际修改尝试的原始广告记录，不能用预览或恢复记录代替')
    validate_plan(plans[0])
    return plans[0]


def execute(adb, plan, journal, apply=False, restoring=False):
    rule = validate_plan(plan)
    scope, user = plan['scope'], plan['scope']['user']
    if identity(adb, user) != scope:
        raise ValueError('设备、固件、用户或硬件身份已变化')
    app = package_state(adb, rule, user)
    expected = {**plan['packageState'], 'enabled': app['enabled']} if rule['id'].startswith('disable:') else plan['packageState']
    if app != expected:
        raise ValueError('相关包状态已被其他操作改变')
    current, target = read_value(adb, user, rule), plan['before'] if restoring else plan['after']
    allowed = (plan['before'], plan['after']) if restoring else (plan['before'],)
    if current not in allowed:
        raise ValueError('广告状态已被其他操作改变，未覆盖')
    mutation = command(adb, scope, rule, target)
    command(adb, scope, rule, plan['before'])
    journal.event('boot_ad_restore_plan' if restoring else 'boot_ad_plan', plan=plan, current=current, command=mutation)
    print(json.dumps(dict(rule=rule, before=current, after=target, command=mutation), ensure_ascii=False, indent=2))
    if not apply:
        print('仅预览；以新记录目录加 --apply 执行。')
        return
    if identity(adb, user) != scope or package_state(adb, rule, user) != app or read_value(adb, user, rule) != current:
        raise ValueError('执行前设备、包版本或广告状态发生变化')
    if current != target:
        journal.event('boot_ad_attempt', restoring=restoring, ruleId=rule['id'])
        output = adb.shell(*mutation)
        journal.event('boot_ad_command', command=mutation, output=output)
    after_app = package_state(adb, rule, user)
    expected = {**app, 'enabled': target['value']} if rule['id'].startswith('disable:') else app
    if identity(adb, user) != scope or after_app != expected or read_value(adb, user, rule) != target:
        raise ValueError('广告状态读回未确认；保留记录，不重放命令')
    journal.event('boot_ad_result', verified=True, restoring=restoring, ruleId=rule['id'], actual=target)
    print('已读回确认原值恢复。' if restoring else '已读回确认配置；重启、冷启动和唤醒广告效果仍待用户验收。')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--action', choices=('inspect', 'disable', 'restore'), default='inspect')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--user', required=True, type=int)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--record', required=True, type=Path)
    parser.add_argument('--rule')
    parser.add_argument('--from-record', type=Path)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args(argv)
    if (args.user < 0 or args.action == 'disable' and not args.rule
            or args.action == 'restore' and not args.from_record
            or args.from_record and args.action != 'restore' or args.rule and args.action != 'disable'
            or args.apply and args.action == 'inspect'):
        parser.error('停用须指定 --rule；恢复须指定 --from-record；检查不接受 --apply')
    journal = tcl.Journal(args.record)
    try:
        adb = guard.ComputerAdb(args.adb, args.serial)
        scope = identity(adb, args.user)
        if args.action == 'restore':
            original = restore_plan(args.from_record)
            journal.event('boot_ad_restore_source', path=str(args.from_record.resolve()), sha256=tcl.digest(args.from_record))
            execute(adb, original, journal, args.apply, True)
        else:
            root = catalog()
            device = adaptation.match(root, scope['brand'], scope['model'], scope['fingerprint'])
            if args.action == 'inspect':
                refs = [r for r in root.get('references', []) if r.get('area') == 'bootAds'
                        and scope['brand'].lower() in [brand.lower() for brand in r.get('brands', [])]]
                report = dict(scope=scope, rules=rules(device) if device else [], references=refs,
                              note='调查候选不授予执行资格。优先官方开关或客服；无精确验收规则时保持只读。')
                journal.event('boot_ad_inspection', report=report)
                print(json.dumps(report, ensure_ascii=False, indent=2))
            else:
                execute(adb, prepare(adb, scope, device or {}, args.rule), journal, args.apply)
        return 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.TimeoutExpired, KeyboardInterrupt) as error:
        journal.event('boot_ad_incomplete', error=str(error))
        print('开机广告处理未完成：' + (str(error) or '用户中断') + '；保留原始记录，不自动重试。', file=sys.stderr)
        return 1
    finally:
        journal.close()


if __name__ == '__main__':
    sys.exit(main())
