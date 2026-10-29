#!/usr/bin/env python3
"""Private, scoped Quiet TV acceptance report; active scenes require --apply."""
import argparse
from datetime import datetime, timezone
import ipaddress
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import device_maintenance as maintenance
import configure_home_guard as guard
import install_tcl as tcl
from verify_install import component, resolver


class TestAdb(maintenance.RecordedAdb):
    def _result(self, *arguments, timeout=8):
        return super()._result(*arguments, timeout=min(timeout, 8))


def network_endpoint(serial):
    host, sep, port = serial.rpartition(':')
    try:
        ipaddress.ip_address(host[1:-1] if host.startswith('[') and host.endswith(']') else host)
        return bool(sep and port.isdecimal() and 0 < int(port) < 65536)
    except ValueError:
        return False


def focused_activity(text):
    # Keep the Android user and full component; historical tasks are not evidence.
    patterns = (r'topResumedActivity=.*?\bu(\d+)\s+([\w.]+/[\w.$]+)',
                r'mResumedActivity:.*?\bu(\d+)\s+([\w.]+/[\w.$]+)',
                r'mFocusedActivity:.*?\bu(\d+)\s+([\w.]+/[\w.$]+)')
    for pattern in patterns:
        matches = {(int(user), guard.canonical_home(activity)) for user, activity in re.findall(pattern, text)}
        if matches:
            if len(matches) == 1:
                user, activity = matches.pop()
                return dict(user=user, component=activity, package=activity.split('/')[0])
            break
    return dict(user=None, component='', package='')


def focused_package(text):
    return focused_activity(text)['package']


def awake(text):
    match = re.search(r'\bmWakefulness=(Awake|Asleep|Dozing|Dreaming)\b', text)
    if match:
        return match[1] in ('Awake', 'Dreaming')
    match = re.search(r'\bmInteractive=(true|false)\b', text)
    return None if not match else match[1] == 'true'


def identity(adb):
    return {'fingerprint': adb.shell('getprop', 'ro.build.fingerprint'),
            'hardwareSerial': adb.shell('getprop', 'ro.serialno') or adb.shell('getprop', 'ro.boot.serialno'),
            'user': adb.current_user(), 'emulator': adb.shell('getprop', 'ro.kernel.qemu') == '1'}


def compare_reports(previous, current):
    keys = ('serial', 'identity', 'package')
    if any(previous.get(k) != current.get(k) for k in keys):
        raise ValueError('对比报告不属于同一连接、硬件、固件、用户或应用')
    old = {item['name']: item['status'] for item in previous['checks']}
    return [{'name': item['name'], 'before': old.get(item['name'], 'SKIP'), 'after': item['status']}
            for item in current['checks']]


class SelfTest:
    def __init__(self, args, adb, journal):
        self.args, self.adb, self.journal = args, adb, journal
        self.report = {'schemaVersion': 1, 'createdAt': datetime.now(timezone.utc).isoformat(),
                       'serial': args.serial, 'package': args.package, 'checks': []}

    def check(self, name, status, detail, **evidence):
        value = dict(name=name, status=status, detail=detail, **evidence)
        self.report['checks'].append(value)
        self.journal.event('check', **value)
        print(f'{status} {name}: {detail}', flush=True)

    def scope(self):
        if identity(self.adb) != self.report['identity']:
            raise ValueError('设备、固件或用户改变，停止后续操作')

    def focus(self):
        sample = focused_activity(self.adb.shell('dumpsys', 'activity', 'activities'))
        return sample['package'] if sample['user'] == self.args.user else ''

    def timeline(self, seconds):
        start = time.monotonic()
        result = []
        while True:
            sample = focused_activity(self.adb.shell('dumpsys', 'activity', 'activities'))
            result.append({'seconds': round(time.monotonic() - start, 1), **sample})
            if time.monotonic() - start >= seconds:
                return result
            time.sleep(1)

    def foreground_result(self, name, samples):
        last = samples[-1] if samples else {}
        known = last.get('component') and last.get('user') is not None
        target = self.args.package + '/home.quiet.tv.HomeActivity'
        status = ('SKIP' if not known else 'PASS' if last['component'] == target
                  and last['user'] == self.args.user else 'FAIL')
        self.check(name, status, '记录自动观察的前台；不代替实体遥控器或可见画面验收', timeline=samples)

    def home(self):
        self.scope()
        self.adb.shell('am', 'start', '--user', self.args.user, '-n', self.args.package + '/home.quiet.tv.HomeActivity')
        self.adb.shell('input', 'keyevent', '3')
        self.foreground_result('injected_home', self.timeline(8))
        self.scope()
        # Resolve the actual settings handler; do not guess an installed third-party app.
        sdk = self.report['sdk']
        query = resolver(self.adb, sdk, self.args.user)
        query = query[:query.index('-a')] + ['-a', 'android.settings.SETTINGS']
        target = component(self.adb.shell(*query))
        if not target or target.startswith(self.args.package + '/'):
            self.check('home_from_app', 'SKIP', '没有可确认的设置应用入口')
            return
        self.adb.shell('am', 'start', '--user', self.args.user, '-n', target)
        time.sleep(2)
        if self.focus() != target.split('/')[0]:
            self.check('home_from_app', 'SKIP', '未观察到设置应用在前台')
            return
        self.scope()
        self.adb.shell('input', 'keyevent', '3')
        self.foreground_result('home_from_app', self.timeline(8))

    def inputs(self):
        sources = maintenance.inspect(self.adb, self.args.package, self.args.user, 'input_list')['inputs']
        self.report['inputs'] = sources
        shown = [x for x in sources.get('sources', []) if x.get('shown')]
        if not shown:
            self.check('inputs', 'SKIP', '本机没有可测试的信号源')
        for index, source in enumerate(shown):
            self.scope()
            response = maintenance.inspect(self.adb, self.args.package, self.args.user, 'input_open', extras={'source':source['id']})
            request = response['inputProfile']['lastInput']['request']
            last = response['inputProfile']['lastInput']
            for _ in range(8):
                time.sleep(1)
                last = maintenance.inspect(self.adb, self.args.package, self.args.user, 'profile_get')['inputProfile']['lastInput']
                if last.get('request') == request and last.get('result') != 'pending':
                    break
            sent = last.get('request') == request and last.get('result') == 'dispatched'
            screenshot = None
            if sent:
                # exec-out transfers PNG bytes to the private computer record only.
                result = subprocess.run([self.args.adb, '-s', self.args.serial, 'exec-out', 'screencap', '-p'], capture_output=True, timeout=8)
                if result.returncode == 0 and result.stdout.startswith(b'\x89PNG\r\n\x1a\n'):
                    screenshot = f'input-{index + 1}.png'
                    (self.journal.directory / screenshot).write_bytes(result.stdout)
            self.check('input:' + source['id'], 'SKIP' if sent else 'FAIL',
                       '请求已发出，需核对输入名称、端口及实际画面' if sent else '请求未确认，不自动重试',
                       dispatch=last, screenshot=screenshot, physicalVerified=False)

    def reconnect(self, deadline):
        while time.monotonic() < deadline:
            try:
                if self.adb.run('get-state', timeout=3) == 'device':
                    return True
            except (ValueError, OSError, subprocess.TimeoutExpired):
                pass
            if network_endpoint(self.args.serial):
                # Reconnect only the exact user-selected address; never scan or pair.
                result = subprocess.run([self.args.adb, 'connect', self.args.serial], capture_output=True, text=True, timeout=8)
                self.journal.event('reconnect', output=result.stdout + result.stderr, exit=result.returncode)
            time.sleep(2)
        return False

    def reboot(self):
        self.scope()
        if not self.report['identity']['hardwareSerial']:
            self.check('reboot', 'SKIP', '硬件序列标识不可读，无法在重新连接后核对设备')
            return
        before = self.adb.shell('cat', '/proc/sys/kernel/random/boot_id')
        if not re.fullmatch(r'[0-9a-f-]{36}', before):
            self.check('reboot', 'SKIP', '无法读取启动标识，不能证明整机重启')
            return
        self.journal.event('reboot_attempt', bootId=before)
        self.adb.run('reboot')  # Issued once, never replayed after a connection error.
        deadline = time.monotonic() + 150
        completed = False
        while time.monotonic() < deadline:
            if not self.reconnect(deadline):
                break
            try:
                current_identity = identity(self.adb)
                current = self.adb.shell('cat', '/proc/sys/kernel/random/boot_id')
                boot_completed = self.adb.shell('getprop', 'sys.boot_completed')
            except (ValueError, subprocess.TimeoutExpired):
                time.sleep(2)  # Boot can interrupt read-only queries after get-state succeeds.
                continue
            if current_identity != self.report['identity']:
                raise ValueError('重连后设备、固件或用户改变')
            changed = current != before and bool(re.fullmatch(r'[0-9a-f-]{36}', current))
            if changed and boot_completed == '1':
                completed = True
                break
            time.sleep(2)
        if not completed:
            self.check('reboot', 'FAIL', '未确认新的完整启动，不重放重启命令')
            raise ValueError('重启或重新连接未确认，后续场景停止')
        # Read before any Quiet TV broadcast/start: inspection must not repair the tested app first.
        enabled = self.adb.shell('settings', 'get', 'global', 'adb_enabled')
        samples = self.timeline(60)
        self.scope()
        self.check('reboot', 'PASS', '启动标识改变，启动完成且硬件、固件、用户匹配', bootId=current)
        self.check('adb_after_reboot', 'PASS' if enabled == '1' else 'FAIL', '应用查询前读取 ADB 设置及实际连接', setting=enabled)
        network_verified = network_endpoint(self.args.serial) and not self.report['identity']['emulator']
        self.check('network_adb_after_reboot', 'PASS' if network_verified else 'SKIP',
                   '已在原网络地址重新连接' if network_verified else '本次是 USB、模拟器或动态标识，不能证明网络 ADB 常开')
        self.foreground_result('home_after_reboot', samples)
        self.report['afterReboot'] = maintenance.inspect(self.adb, self.args.package, self.args.user)

    def standby(self):
        self.scope()
        if awake(self.adb.shell('dumpsys', 'power')) is not True:
            self.check('standby', 'SKIP', '无法确认电视当前处于唤醒状态')
            return
        self.journal.event('standby_attempt')
        self.adb.shell('input', 'keyevent', '223')
        print('等待 70 秒待机；若 ADB 断连，请在提示后使用遥控器唤醒。', flush=True)
        time.sleep(3)
        try:
            sleeping = awake(self.adb.shell('dumpsys', 'power')) is False
        except (ValueError, subprocess.TimeoutExpired):
            sleeping = False
        time.sleep(67)
        try:
            self.scope()
            self.adb.shell('input', 'keyevent', '224')
        except (ValueError, subprocess.TimeoutExpired):
            print('ADB 不可达，请使用遥控器唤醒；仅等待原地址恢复，不重放电源命令。', flush=True)
        if not self.reconnect(time.monotonic() + 90):
            self.check('standby', 'FAIL', '未重新连接，停止后续场景')
            raise ValueError('待机后连接未恢复')
        self.scope()
        self.check('standby', 'PASS' if sleeping and awake(self.adb.shell('dumpsys', 'power')) is True else 'SKIP',
                   '分别记录睡眠与唤醒状态；没有两项证据时不判通过')
        self.foreground_result('home_after_standby', self.timeline(15))

    def run(self):
        if self.adb.run('get-state') != 'device':
            raise ValueError('目标未连接或未授权')
        self.report['identity'] = identity(self.adb)
        if self.report['identity']['user'] != self.args.user:
            raise ValueError('目标用户不匹配')
        self.report['sdk'] = int(self.adb.shell('getprop', 'ro.build.version.sdk'))
        guard.verify_identity(self.adb, self.report['sdk'])
        self.report['before'] = maintenance.inspect(self.adb, self.args.package, self.args.user)
        self.check('configuration', 'PASS', '只读快照取得；不代表接管、ADB 持久或实体操作成功')
        for flag, method in (('home_tests', self.home), ('inputs', self.inputs), ('standby', self.standby), ('reboot', self.reboot)):
            if getattr(self.args, flag):
                method()
            else:
                self.check(flag, 'SKIP', '未选择此场景')
        scenes = self.report['before'].get('routes', {}).get('sceneEvidence', {})
        for scene, name in (('home', 'physical_home'), ('coldBoot', 'cold_boot'),
                            ('reboot', 'user_reboot'), ('wake', 'user_wake')):
            evidence = scenes.get(scene, {})
            status = {'通过': 'PASS', '失败': 'FAIL'}.get(evidence.get('result'))
            if evidence.get('source') == 'user' and status:
                self.check(name, status, 'APK 当前配置的用户场景记录', source='user', evidence=evidence)
            elif name in ('physical_home', 'cold_boot'):
                self.check(name, 'SKIP', '需用户实际操作并反馈，电脑任务可用 accept 登记')

    def save(self):
        if self.args.compare:
            try:
                previous = json.loads(self.args.compare.read_text(encoding='utf-8'))
                self.report['comparison'] = compare_reports(previous, self.report)
            except (ValueError, KeyError, TypeError, OSError) as error:
                self.check('comparison', 'FAIL', str(error))
        (self.journal.directory / 'report.json').write_text(json.dumps(self.report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        lines = ['# Quiet TV 本机验收', '', '记录含设备信息和画面，仅保存在本机；公开反馈前脱敏。', '',
                 '| 项目 | 结果 | 说明 |', '| --- | --- | --- |']
        for item in self.report['checks']:
            cells = [str(item[key]).replace('|', '\\|').replace('\n', ' ') for key in ('name', 'status', 'detail')]
            lines.append('| ' + ' | '.join(cells) + ' |')
        (self.journal.directory / 'report.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--user', type=int, required=True)
    parser.add_argument('--package', default=tcl.PACKAGE, choices=(tcl.PACKAGE, 'com.ts.launcher', 'com.coocaa.quiettv'))
    parser.add_argument('--record', type=Path, required=True, help='全新的电脑私有目录，父目录须存在')
    parser.add_argument('--compare', type=Path, help='同机之前的 report.json')
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--apply', action='store_true')
    for flag in ('home-tests', 'inputs', 'standby', 'reboot'):
        parser.add_argument('--' + flag, action='store_true')
    args = parser.parse_args()
    active = args.home_tests or args.inputs or args.standby or args.reboot
    if args.user < 0 or active != args.apply:
        parser.error('有效用户必填；主动场景须同时指定 --apply；默认只读')
    journal = None
    test = None
    try:
        journal = tcl.Journal(args.record)
        test = SelfTest(args, TestAdb(args.adb, args.serial, journal), journal)
        test.run()
    except (ValueError, KeyError, TypeError, OSError, subprocess.TimeoutExpired, KeyboardInterrupt) as error:
        if test:
            test.check('execution', 'FAIL', str(error) or '用户中断，未重放操作')
        else:
            print(str(error), file=sys.stderr)
        if not test:
            return 1
    finally:
        if test:
            test.save()
        if journal:
            journal.close()
    print('报告：' + str(args.record / 'report.md'))
    return int(any(item['status'] == 'FAIL' for item in test.report['checks']))


if __name__ == '__main__':
    sys.exit(main())
