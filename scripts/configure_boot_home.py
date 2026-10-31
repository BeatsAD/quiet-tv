#!/usr/bin/env python3
"""Configure boot HOME and the detected VIDAA route; preserve stock apps and recovery evidence."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile

import configure_home_guard as guard
import install_tcl as tcl
from home_capabilities import real_home
from verify_install import HOME, HOME_ARGS, component, help_text, resolver

VIDAA = 'persist.vt.use_home_pkg'
STOCK = 'com.jamdeo.tv.vod'


class NoNativeRoute(ValueError):
    """No native route is available, with no change made or HOME confirmed unchanged."""


def direct_boot_home(tree):
    """Read the actual HomeActivity declaration, with application-level inheritance."""
    stack, application, activities = [], {}, []
    for line in tree.splitlines():
        element = re.match(r'(\s*)E: ([\w-]+)(?:\s|$)', line)
        if element:
            depth = len(element[1])
            while stack and stack[-1][0] >= depth:
                stack.pop()
            node = {}
            if element[2] == 'application':
                application = node
            elif element[2] == 'activity' and any(tag == 'application' for _, tag, _ in stack):
                activities.append(node)
            stack.append((depth, element[2], node))
        elif stack:
            attr = re.search(r'A: (?:android|http://schemas\.android\.com/apk/res/android):(name|directBootAware)[^=]*=(.*)', line)
            if attr:
                value = attr[2]
                if attr[1] == 'name':
                    name = re.match(r'"([^\"]+)"', value)
                    if name:
                        stack[-1][2]['name'] = name[1]
                else:
                    stack[-1][2]['aware'] = bool(re.fullmatch(r'(?:\(type 0x12\))?(?:0xffffffff|0x1|true)', value.strip()))
    homes = [a for a in activities if a.get('name') in ('.HomeActivity', 'home.quiet.tv.HomeActivity')]
    return len(homes) == 1 and homes[0].get('aware', application.get('aware', False))


def apk_boot_capability(tools, apk):
    tree = tcl.checked(subprocess.run([tools.aapt2, 'dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml'],
        capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=60, check=False))
    return direct_boot_home(tree)


def ensure_user(adb, user):
    if adb.current_user() != user:
        raise ValueError('前台用户已变化，停止修改；请保留记录并重新检查')


def identity(adb, user):
    if adb.run('get-state') != 'device':
        raise ValueError('需要已选定并授权的 ADB 目标')
    ensure_user(adb, user)
    sdk = int(adb.shell('getprop', 'ro.build.version.sdk'))
    profile = guard.verify_identity(adb, sdk)
    fingerprint = adb.shell('getprop', 'ro.build.fingerprint')
    if not fingerprint:
        raise ValueError('固件指纹未确认')
    return {'serial': adb.serial, 'user': user, 'sdk': sdk, 'fingerprint': fingerprint, 'identity': profile}


def home_commands(adb, sdk, user):
    for tool in (('cmd', 'package'), ('pm',)):
        try:
            usage = help_text(adb, *tool)
            query_options = guard.user_options(usage, 'query-activities', sdk, user)
            set_options = guard.user_options(usage, 'set-home-activity', sdk, user)
            candidates = adb.shell(*tool, 'query-activities', '--brief', *query_options, *HOME_ARGS)
        except ValueError:
            continue
        homes = {component(line) for line in candidates.splitlines()}
        homes.discard('')
        return [*tool, 'set-home-activity', *set_options], homes
    raise NoNativeRoute('未确认本机可用的 HOME 查询和设置命令；可独立评估已有兼容守护')


def pull_installed(adb, user, sdk, destination):
    options = guard.user_options(help_text(adb, 'pm'), 'path', sdk, user)
    paths = [s[8:] for s in adb.shell('pm', 'path', *options, tcl.PACKAGE).splitlines() if s.startswith('package:')]
    if len(paths) != 1 or not paths[0].startswith('/'):
        raise ValueError('未确认单 APK 安装路径，不处理 split 或缺失的安装')
    adb.run('pull', paths[0], str(destination), timeout=120)


def prepare(adb, user, tools, apk, journal):
    device = identity(adb, user)
    metadata = tools.inspect(apk)
    if metadata['package'] != tcl.PACKAGE or metadata['min_sdk'] > device['sdk']:
        raise ValueError('实际安装包身份或系统档位不符')
    package = adb.shell('dumpsys', 'package', tcl.PACKAGE)
    if not tcl.usable(tcl.user_state(package, user)):
        raise ValueError('当前用户上的 Quiet TV 不可用')
    if metadata['home_mode'] != 'standard':
        raise ValueError('当前为无 HOME 的 relay 包，应使用现有兼容守护')
    aware = apk_boot_capability(tools, apk)
    journal.event('boot_apk', metadata=metadata, directBootAware=aware)
    if device['sdk'] >= 24 and not aware:
        raise ValueError('实际 APK 未声明解锁前 HOME；需先更新到支持 Direct Boot 的适配发布包，未修改电视')
    setter, homes = home_commands(adb, device['sdk'], user)
    before = component(adb.shell(*resolver(adb, device['sdk'], user)))
    if HOME not in homes:
        raise NoNativeRoute('Quiet TV 不在本机 HOME 候选中，可独立评估已有兼容守护')
    if before not in homes or not real_home(before):
        raise ValueError('目标 HOME 候选或原默认 HOME 未确认，未修改电视')
    journal.event('boot_baseline', device=device, home_before=before, target_package=package,
                  original_package=adb.shell('dumpsys', 'package', before.split('/')[0]))
    steps = []
    vendor = ' '.join(adb.shell('getprop', key) for key in ('ro.product.brand', 'ro.product.manufacturer')).lower().split()
    stock_homes = {h for h in homes if h.startswith(STOCK + '/')}
    route = 'native-home'
    if set(vendor).intersection(('hisense', 'vidaa')) and user == 0 and stock_homes:
        stock = adb.shell('dumpsys', 'package', STOCK)
        value = adb.shell('getprop', VIDAA)
        system = bool(re.search(r'\b(?:pkgFlags|flags)=\[[^\]\n]*\bSYSTEM\b', stock))
        if system and tcl.usable(tcl.user_state(stock, user)) and value in (STOCK, tcl.PACKAGE):
            route = 'vidaa-property'
            if value != tcl.PACKAGE:
                steps.append({'kind': 'property', 'key': VIDAA, 'before': value, 'after': tcl.PACKAGE})
        else:
            journal.event('vidaa_route_unavailable', property=value, stock_state=stock)
            print('海信私有开机路由未匹配，仅配置标准 HOME；若仍回原桌面，需继续检查厂商路由。')
    if before != HOME:
        steps.append({'kind': 'home', 'before': before, 'after': HOME})
    plan = {'schemaVersion': 1, 'device': device, 'apkSha256': metadata['sha256'],
            'homeBefore': before, 'route': route, 'steps': steps}
    journal.event('boot_plan', plan=plan)
    print('开机桌面计划：' + json.dumps(plan, ensure_ascii=False))
    return plan, setter


def read_step(adb, step, device):
    if step['kind'] == 'property':
        return adb.shell('getprop', VIDAA)
    return component(adb.shell(*resolver(adb, device['sdk'], device['user'])))


def write_step(adb, setter, step, value):
    if step['kind'] == 'property':
        return adb.shell('setprop', VIDAA, value)
    return adb.shell(*setter, value)


def check_device(adb, expected):
    if identity(adb, expected['user']) != expected:
        raise ValueError('设备、用户或固件与记录不一致，未继续修改')


def apply_plan(adb, plan, setter, journal):
    device = plan['device']
    check_device(adb, device)
    for step in plan['steps']:
        if read_step(adb, step, device) != step['before']:
            raise ValueError('执行前状态变化，请重新预览')
    for index, step in enumerate(plan['steps']):
        check_device(adb, device)
        if read_step(adb, step, device) != step['before']:
            raise ValueError('配置状态变化，已执行部分请按记录核对')
        journal.event('boot_write_pending', index=index)
        output = write_step(adb, setter, step, step['after'])
        actual = read_step(adb, step, device)
        journal.event('boot_write_result', index=index, output=output, actual=actual)
        if actual != step['after']:
            if (plan['route'] == 'native-home' and len(plan['steps']) == 1
                    and step['kind'] == 'home' and actual == step['before']):
                check_device(adb, device)
                raise NoNativeRoute('系统仍保留原默认 HOME，且未修改厂商属性，可评估兼容守护')
            raise ValueError('开机路由写入未读回，停止后续操作；用 --restore-record 核对恢复')
    check_device(adb, device)
    if component(adb.shell(*resolver(adb, device['sdk'], device['user']))) != HOME:
        raise ValueError('最终 HOME 不是 Quiet TV，配置未完成')
    if plan['route'] == 'vidaa-property' and adb.shell('getprop', VIDAA) != tcl.PACKAGE:
        raise ValueError('海信主页属性在设置 HOME 后变化，配置未完成')
    journal.event('boot_configured', route=plan['route'], physicalHome=None, coldBoot=None, wake=None)
    print('开机桌面配置已写入并读回；实体 HOME、普通重启、断电开机及待机唤醒尚未验收。')


def configure(adb, user, tools, apk, journal, apply=False):
    plan, setter = prepare(adb, user, tools, apk, journal)
    if apply:
        # Detect an APK replacement between inspection and the first configuration write.
        fresh = journal.directory / 'boot-before-apply.apk'
        pull_installed(adb, user, plan['device']['sdk'], fresh)
        if tcl.digest(fresh) != plan['apkSha256']:
            raise ValueError('已安装 APK 在配置前改变，未执行')
        apply_plan(adb, plan, setter, journal)
    else:
        print('仅预览，未修改电视；同一已授权目标使用新记录目录加 --apply 执行。')
    return plan


def restore(adb, user, path, journal, apply=False):
    entries = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
    plans = [e['plan'] for e in entries if e.get('event') == 'boot_plan']
    if len(plans) != 1:
        raise ValueError('恢复记录必须包含唯一的开机桌面方案')
    plan = plans[0]
    if plan.get('schemaVersion') != 1 or plan['device']['user'] != user:
        raise ValueError('记录格式或用户不匹配')
    check_device(adb, plan['device'])
    setter, homes = home_commands(adb, plan['device']['sdk'], user)
    if plan['homeBefore'] not in homes or 'ResolverActivity' in plan['homeBefore']:
        raise ValueError('原 HOME 已不可用，未恢复')
    steps = plan['steps']
    if len(steps) > 2:
        raise ValueError('恢复记录步骤无效')
    for step in steps:
        if step.get('kind') == 'property':
            if user != 0 or step.get('key') != VIDAA or step.get('before') != STOCK or step.get('after') != tcl.PACKAGE:
                raise ValueError('恢复记录包含不支持的属性变更')
        elif step.get('kind') == 'home':
            if step.get('before') != plan['homeBefore'] or step.get('after') != HOME:
                raise ValueError('恢复 HOME 记录无效')
        else:
            raise ValueError('恢复记录步骤无效')
    attempted = {e['index'] for e in entries if e.get('event') == 'boot_write_pending'}
    if not attempted.issubset(set(range(len(steps)))):
        raise ValueError('恢复记录的执行序号无效')
    selected = [(i, s) for i, s in enumerate(steps) if i in attempted]
    for _, step in selected:
        if read_step(adb, step, plan['device']) not in (step['before'], step['after']):
            raise ValueError('现场状态已被其他操作改变，未覆盖')
    journal.event('boot_restore_plan', source=str(path), plan=plan, attempted=sorted(attempted))
    if not apply:
        print('恢复预览：仅撤销此记录中尝试过的 HOME／海信属性修改，不改应用权限或包状态。')
        return
    for index, step in selected:
        check_device(adb, plan['device'])
        current = read_step(adb, step, plan['device'])
        if current == step['before']:
            continue
        if current != step['after']:
            raise ValueError('恢复过程中状态改变，停止覆盖')
        journal.event('boot_restore_pending', index=index)
        output = write_step(adb, setter, step, step['before'])
        after = read_step(adb, step, plan['device'])
        journal.event('boot_restore_result', index=index, output=output, actual=after)
        if after != step['before']:
            raise ValueError('恢复未读回，保留记录继续核对')
    check_device(adb, plan['device'])
    if any(read_step(adb, step, plan['device']) != step['before'] for _, step in selected):
        raise ValueError('恢复后的最终状态不一致，保留记录继续核对')
    journal.event('boot_restored')
    print('本次开机路由修改已撤销；原桌面画面仍需实际验收。')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--user', required=True, type=int)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--record', required=True, type=Path, help='全新私有目录')
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--restore-record', type=Path, help='此前的 record.jsonl；加 --apply 才恢复')
    parser.add_argument('--aapt2')
    parser.add_argument('--apksigner')
    args = parser.parse_args()
    if args.user < 0:
        parser.error('用户必须为非负整数')
    journal = None
    try:
        journal = tcl.Journal(args.record)
        # Reuse the installer transport, which reports the exact failing command and output.
        from install_quiet_tv import RecordedAdb
        adb = RecordedAdb(args.adb, args.serial, journal)
        if args.restore_record:
            restore(adb, args.user, args.restore_record, journal, args.apply)
        else:
            device = identity(adb, args.user)
            apk = journal.directory / 'installed.apk'
            pull_installed(adb, args.user, device['sdk'], apk)
            tools = tcl.ApkTools(tcl.sdk_tool('aapt2', args.aapt2), tcl.sdk_tool('apksigner', args.apksigner))
            configure(adb, args.user, tools, apk, journal, args.apply)
        return 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.TimeoutExpired, zipfile.BadZipFile, KeyboardInterrupt) as error:
        if journal:
            journal.event('boot_incomplete', error=str(error) or '用户取消')
        print('开机配置未完成：' + (str(error) or '用户取消') + '；保留记录，未自动重试或重启。', file=sys.stderr)
        return 1
    finally:
        if journal:
            journal.close()


if __name__ == '__main__':
    sys.exit(main())
