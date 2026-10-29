#!/usr/bin/env python3
"""Ordered physical-remote calibration through Quiet TV's permission-protected maintenance API."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import configure_home_guard as guard
import device_maintenance as maintenance
import install_tcl as tcl

STEPS = ('home', 'inputs', 'menu')
LABELS = {'home': 'HOME', 'inputs': '信号源', 'menu': '菜单'}
ACTIONS = {'home': ('home', 'system'), 'inputs': ('inputs', 'system'),
           'menu': ('settings', 'desktop_settings', 'system')}


def read(adb, package, user):
    maintenance.unchanged_user(adb, user)
    snapshot = maintenance.inspect(adb, package, user, 'remote_status')
    if 'remote-keymap-v1' not in snapshot.get('capabilities', []) or 'remoteKeys' not in snapshot:
        raise ValueError('已安装 APK 缺少逐键校准能力；保留原有配置，不猜测键码')
    return snapshot['remoteKeys']


def previous(path, current, args):
    record = json.loads(path.read_text(encoding='utf-8'))
    if (record.get('serial') != args.serial or record.get('package') != args.package
            or record.get('remoteKeys', {}).get('scope') != current['scope']):
        raise ValueError('原校准记录的连接、应用、机型、固件或用户不同')
    old = record['remoteKeys'].get('session', {})
    new = current.get('session', {})
    if not old.get('id') or old.get('id') != new.get('id') or old.get('step') != new.get('step'):
        raise ValueError('校准任务或按键步骤已变化；先读取现状，不重放旧步骤')
    if getattr(args, 'action', '') != 'cancel' and (old.get('previewId') != new.get('previewId') or old.get('candidate') != new.get('candidate')):
        raise ValueError('试用动作已变化，不能使用旧确认记录')
    if record['remoteKeys']['revision'] != current['revision']:
        raise ValueError('映射配置已变化，不能沿用旧记录')
    return new


def store(journal, args, remote):
    path = journal.directory / 'calibration.json'
    # This directory is new for each command; no old confirmation or result is overwritten.
    with path.open('x', encoding='utf-8') as stream:
        os.chmod(path, 0o600)
        json.dump({'schemaVersion': 1, 'serial': args.serial, 'package': args.package,
                   'remoteKeys': remote, 'physicalEvidence': '用户现场确认另见各键observed；采键本身不证明实体操作'},
                  stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print('本次记录：' + str(path), flush=True)


def run(args, adb, journal):
    if adb.run('get-state') != 'device':
        raise ValueError('目标未连接或未授权')
    maintenance.unchanged_user(adb, args.user)
    guard.verify_identity(adb, int(adb.shell('getprop', 'ro.build.version.sdk')))
    current = read(adb, args.package, args.user)
    journal.event('remote_before', snapshot=current)
    if args.action == 'status':
        store(journal, args, current)
        print(json.dumps(current, ensure_ascii=False, indent=2))
        return current
    extras = {}
    if args.action == 'begin':
        extras['revision'] = current['revision']
    else:
        session = previous(args.from_record, current, args)
        extras.update(calibration_id=session['id'], key=session['step'])
        if args.action == 'capture':
            extras['seconds'] = args.seconds
        elif args.action == 'preview':
            if args.mapping not in ACTIONS[session['step']]:
                raise ValueError('所选动作不适用于当前按键')
            extras['action'] = args.mapping
        elif args.action == 'confirm':
            extras.update(confirmed=True, observed=args.observed, preview_id=session.get('previewId', ''))
    journal.event('remote_plan', action=args.action, parameters=extras)
    if not args.apply:
        print(json.dumps({'action': args.action, 'parameters': extras}, ensure_ascii=False, indent=2))
        print('仅预览，未开始采键或保存配置。按授权加 --apply，并使用新记录目录。')
        store(journal, args, current)
        return current
    fresh = read(adb, args.package, args.user)
    if fresh != current:
        raise ValueError('执行前校准现场已变化，请重新读取，不自动重试')
    journal.event('remote_attempt', action=args.action)
    response = maintenance.inspect(adb, args.package, args.user, 'remote_' + args.action, extras=extras)
    remote = response['remoteKeys']
    if remote['scope'] != current['scope']:
        raise ValueError('执行后设备范围不同，结果未确认')
    session = remote['session']
    if args.action in ('capture', 'preview'):
        label = LABELS[session['step']]
        print(('请按一次遥控器' if args.action == 'capture' else '请再次按遥控器') + label + '键并松开。不要使用 ADB 注入代替用户实体按键。', flush=True)
        if args.action == 'preview' and extras['action'] == 'system':
            print('保留厂商系统处理；请用户确认实际画面，不补造未采到的键码。', flush=True)
        else:
            deadline = time.monotonic() + (args.seconds + 2 if args.action == 'capture' else 120)
            while time.monotonic() < deadline:
                if args.action == 'capture' and session.get('phase') != 'capture' or args.action == 'preview' and session.get('pressed'):
                    break
                if session.get('phase') == 'expired':
                    break
                time.sleep(0.5)
                remote = read(adb, args.package, args.user)
                session = remote.get('session', {})
                if (remote['scope'] != current['scope'] or session.get('id') != response['remoteKeys']['session']['id']
                        or session.get('step') != extras['key'] or remote['revision'] != current['revision']):
                    raise ValueError('采集期间机型、固件、用户、任务或映射已变化')
    journal.event('remote_result', snapshot=remote)
    store(journal, args, remote)
    print(json.dumps(remote, ensure_ascii=False, indent=2))
    if args.action == 'capture' and session.get('phase') != 'captured':
        print('没有完整且唯一的按键事件。核对用户观察；无键码时只能选择system保留系统处理，或显式重新采集。')
    elif args.action == 'preview':
        print('按键收到或请求发出均不代表画面正确。先询问用户实际结果，再以confirm --observed保存确认。')
    elif args.action == 'confirm':
        if session.get('phase') == 'saved':
            if len(remote.get('profile', {}).get('bindings', [])) != 3:
                raise ValueError('三键映射未读回，保存结果未确认')
            print('三个按键已按当前机型、固件及用户保存并读回；电脑断开后仍可使用。')
        else:
            print('当前按键已确认；下一步：' + LABELS[session['step']] + '。全部确认前不替换已有映射。')
    return remote


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('action', choices=('status', 'begin', 'capture', 'preview', 'confirm', 'cancel'))
    parser.add_argument('--serial', required=True)
    parser.add_argument('--user', required=True, type=int)
    parser.add_argument('--package', default=tcl.PACKAGE, choices=(tcl.PACKAGE, 'com.ts.launcher', 'com.coocaa.quiettv'))
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--record', required=True, type=Path)
    parser.add_argument('--from-record', type=Path, help='上一校准步骤的calibration.json，不重放其中的命令')
    parser.add_argument('--mapping', choices=('home', 'inputs', 'settings', 'desktop_settings', 'system'))
    parser.add_argument('--observed', help='用户确认的实际页面或结果，不得由工具成功代填')
    parser.add_argument('--seconds', type=int, default=20)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args(argv)
    if args.user < 0 or not 5 <= args.seconds <= 30:
        parser.error('用户无效或采集时长不在5至30秒内')
    if args.action not in ('status', 'begin') and not args.from_record:
        parser.error('此步骤需要上一阶段的 --from-record')
    if args.action == 'preview' and not args.mapping or args.action == 'confirm' and (not args.observed or not args.observed.strip() or len(args.observed)>300):
        parser.error('preview需要--mapping；confirm需要用户实际结果--observed（最多300字）')
    if args.action == 'status' and args.apply:
        parser.error('status只读，不使用--apply')
    journal = None
    try:
        journal = tcl.Journal(args.record)
        run(args, maintenance.RecordedAdb(args.adb, args.serial, journal), journal)
        return 0
    except (ValueError, KeyError, TypeError, OSError, subprocess.TimeoutExpired, KeyboardInterrupt) as error:
        if journal:
            journal.event('failed', error=str(error))
        print('遥控器校准未完成：' + (str(error) or '用户中断') + '；保留原记录，不重放操作。', file=sys.stderr)
        return 1
    finally:
        if journal:
            journal.close()


if __name__ == '__main__':
    sys.exit(main())
