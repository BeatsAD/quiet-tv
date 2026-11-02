#!/usr/bin/env python3
"""Collect a bounded, read-only LeTV HOME case on the owner's computer (Python 3 only)."""
import argparse
from datetime import datetime, timezone
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import threading
import time

PACKAGES = ('home.quiet.tv', 'com.stv.launcher', 'com.stv.deskplatform')
PROPERTIES = ('ro.product.brand', 'ro.product.manufacturer', 'ro.product.model',
              'ro.product.device', 'ro.build.version.sdk', 'ro.build.display.id', 'ro.build.fingerprint')
LIMIT = 1024 * 1024
LOG_LIMIT = 8 * LIMIT


def stamp():
    return datetime.now(timezone.utc).isoformat()


def redact(text, serial=''):
    if serial:
        text = text.replace(serial, '<device>')
    text = re.sub(r'(?i)\b(?:Bearer|Basic)\s+[^\s,;]+', '<credential>', text)
    text = re.sub(r'(?i)((?:token|password|passwd|pwd|secret|api[_-]?key|authorization|cookie|ssid|bssid|serial(?:no)?|android[_-]?id)\s*[=:]\s*)(?:"[^"]*"|\'[^\']*\'|[^\s,;]+)', r'\1<redacted>', text)
    text = re.sub(r'(?i)\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\b', '<mac>', text)
    text = re.sub(r'[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}', '<email>', text)
    def ipv6(match):
        try:
            return '<ipv6>' if ipaddress.ip_address(match[0].split('%')[0]).version == 6 else match[0]
        except ValueError:
            return match[0]
    text = re.sub(r'(?i)(?<!\w)(?:[0-9a-f]{0,4}:){2,}[0-9a-f:]+(?:%\w+)?', ipv6, text)
    # Keep explicitly labelled four-part app versions; they are not network addresses.
    def address(match):
        prefix = text[max(0, match.start()-35):match.start()]
        if re.search(r'(?i)(?:version(?:Name)?|Quiet\s*TV|Build)\s*[:=]?\s*$', prefix):
            return match[0]
        return '<ip>'
    text = re.sub(r'\b(?:\d{1,3}\.){3}\d{1,3}\b', address, text)
    return re.sub(r'([?&][\w.-]+=)[^\s&#]*', r'\1<redacted>', text)


def save(path, value):
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, indent=2) + '\n'
    path.write_text(text, encoding='utf-8')
    if os.name != 'nt':
        path.chmod(0o600)


def query(adb, *arguments):
    try:
        result = subprocess.run([*adb, *arguments], capture_output=True, timeout=10, check=False)
        data = result.stdout + b'\n' + result.stderr
        text = data[:LIMIT].decode('utf-8', errors='replace').strip()
        status = 'ok' if result.returncode == 0 else 'failed'
        if re.search(r'(?i)permission denial|permission denied|without permission|SecurityException', text):
            status = 'permission_denied'
        elif re.search(r'(?i)unknown command|not found|not available|can.t find service|^Killed$', text):
            status = 'unavailable'
        return dict(status=status, exit=result.returncode, truncated=len(data)>LIMIT,
                    text=redact(text, adb[2]))
    except subprocess.TimeoutExpired as error:
        return dict(status='timeout', text=redact((error.stdout or b'')[:LIMIT].decode('utf-8', errors='replace'), adb[2]))
    except OSError as error:
        return dict(status='failed', text=redact(str(error), adb[2]))


def shell(adb, *arguments):
    return query(adb, 'shell', ' '.join(shlex.quote(str(arg)) for arg in arguments))


def snapshot(adb, user):
    result = {name: shell(adb, 'getprop', name) for name in PROPERTIES}
    result['computerTime'] = dict(status='ok', text=stamp())
    result['deviceTime'] = shell(adb, 'date')
    result['adbIdentity'] = shell(adb, 'id')
    result['pmHelp'] = shell(adb, 'pm', 'help')
    result['amHelp'] = shell(adb, 'am', 'help')
    if 'get-current-user' in result['amHelp']['text']:
        result['currentUser'] = shell(adb, 'am', 'get-current-user')
    else:
        result['currentUser'] = dict(status='unavailable', text='帮助未提供 get-current-user；不猜用户0')
    for name in PACKAGES:
        result['package:' + name] = shell(adb, 'dumpsys', 'package', name)
        if re.search(r'(?m)^\s*(?:pm\s+)?path\s', result['pmHelp']['text']):
            result['path:' + name] = shell(adb, 'pm', 'path', name)
    for name, command in (
            ('activities', ('dumpsys', 'activity', 'activities')),
            ('windows', ('dumpsys', 'window', 'windows')),
            ('accessibility', ('dumpsys', 'accessibility')),
            ('input', ('dumpsys', 'input')),
            ('accessibilityEnabled', ('settings', 'get', 'secure', 'accessibility_enabled')),
            ('accessibilityServices', ('settings', 'get', 'secure', 'enabled_accessibility_services'))):
        result[name] = shell(adb, *command)
    # API18 commonly lacks the modern package commands. Only use advertised operations.
    for tool in (('cmd', 'package'), ('pm',)):
        usage = shell(adb, *tool, 'help')['text']
        for operation in ('query-activities', 'resolve-activity'):
            advertised = re.search(r'(?m)^\s*(?:pm\s+)?' + operation + r'\s+([^\n]*)', usage)
            if advertised and '--user' in advertised[1]:
                if result['currentUser']['status'] == 'ok' and result['currentUser']['text'] == str(user):
                    result[operation] = shell(adb, *tool, operation, '--user', user, '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
                else:
                    result[operation] = dict(status='unavailable', text='当前用户尚未核对，不猜用户0')
            elif advertised:
                result[operation] = shell(adb, *tool, operation, '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
    return result


class LogCapture:
    def __init__(self, adb, folder):
        self.adb, self.folder = adb, folder
        self.process = None
        self.bytes = 0
        self.reason = 'not_started'
        self.long_lines = 0

    def start(self):
        help_result = query(self.adb, 'logcat', '--help')
        save(self.folder/'logcat-help.json', help_result)
        arguments = ['logcat', '-v', 'threadtime']
        if re.search(r'(?m)^\s*-b\b', help_result['text']) and 'events' in help_result['text']:
            arguments += ['-b', 'main', '-b', 'events']
        self.process = subprocess.Popen([*self.adb, *arguments], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        self.reason = 'recording'
        self.thread = threading.Thread(target=self.read, daemon=True)
        self.thread.start()

    def read(self):
        with (self.folder/'logcat.txt').open('w', encoding='utf-8') as output:
            if os.name != 'nt':
                os.chmod(self.folder/'logcat.txt', 0o600)
            while True:
                line = self.process.stdout.readline(16384)
                if not line:
                    if self.reason == 'recording':
                        self.reason = 'stream_ended'
                    return
                if len(line) == 16384 and not line.endswith(b'\n'):
                    self.long_lines += 1
                    while line and not line.endswith(b'\n'):
                        line = self.process.stdout.readline(16384)
                    continue
                text = redact(line.decode('utf-8', errors='replace'), self.adb[2])
                size = len(text.encode('utf-8'))
                if self.bytes + size > LOG_LIMIT:
                    self.reason = 'size_limit'
                    return
                output.write(text)
                self.bytes += size

    def stop(self, reason):
        if self.reason == 'recording':
            self.reason = reason
        if self.process is not None:
            if self.process.poll() is None:
                self.process.terminate()
                try:
                    self.process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    self.process.kill()
                    self.process.wait(timeout=3)
            self.thread.join(timeout=3)
            self.process.stdout.close()


def pull_files(adb, folder, before):
    sources = ['/system/framework/framework-letv.jar', '/system/framework/framework-letv.odex']
    for package in PACKAGES:
        text = before.get('path:' + package, {}).get('text', '')
        sources += re.findall(r'(?m)^package:(/(?:system|vendor|product|data/app|data/app-private)/[^\r\n]*\.apk)$', text)
    records = []
    for index, source in enumerate(dict.fromkeys(sources)):
        target = folder / ('artifact-' + str(index) + '-' + source.rsplit('/', 1)[1])
        result = query(adb, 'pull', source, str(target))
        entry = dict(source=source, file=target.name, result=result)
        if target.is_file():
            if os.name != 'nt':
                target.chmod(0o600)
            entry.update(bytes=target.stat().st_size, sha256=hashlib.sha256(target.read_bytes()).hexdigest())
        records.append(entry)
    return records


def report(folder, session, before, after):
    failures = {stage: {name:value['status'] for name,value in snapshot.items()
                        if value['status'] != 'ok' or value.get('truncated')}
                for stage,snapshot in (('before',before), ('after',after))}
    observed = {}
    for stage, value in (('before',before), ('after',after)):
        text = value.get('windows', {}).get('text', '') + '\n' + value.get('activities', {}).get('text', '')
        observed[stage] = [line.strip() for line in text.splitlines()
                           if re.search(r'mCurrentFocus|mFocusedApp|mResumedActivity|mFocusedActivity', line)][:8]
    result = dict(mode='readonly_letv_home_evidence', requestedUser=session['requestedUser'],
                  currentUser=before.get('currentUser'), foreground=observed, unavailable=failures,
                  capture=session.get('capture'), missingSnapshots=[name for name,value in (('before',before),('after',after)) if not value],
                  rootCause=None, replacementEligible=False,
                  physicalHomeVerified=False,
                  limitations=['原始日志可能含采集开始前的缓冲区；用用户动作时间核对。',
                               '前台与包路径查询不能替代系统默认HOME或当前用户确认。',
                               '出现固件类名或包名字串仅是调查线索，不授予替换资格。',
                               '原厂APK、框架和日志仅私下保存；文件未上传。'])
    save(folder/'report.json', result)
    save(folder/'反馈报告.md', '# 乐视 HOME 只读取证\n\n'
         '本工具没有连接新地址、安装、启动应用、注入按键、root、改权限、停用原桌面或重启电视。\n\n'
         '请补充：用户按实体 HOME 的时间、操作前页面、实际返回页面；来源写为用户观察。\n\n'
         '证据见 before.json、after.json、logcat.txt、session.json 与可选 artifacts.json。'
         '当前用户查询失败时保持未知；--user 是操作者提供的目标，不能当作已核验值。\n\n'
         '分流：核对 com.stv.deskplatform 的 ExternalJumpActivity 与实际 Launcher 是否不同；'
         '另查 DesktopManagerService.startDockOrHome 是否显式启动 com.stv.launcher。'
         '原厂副本、完整组件、签名、依赖、分区及离线恢复未核实前，只报告候选，不替换。\n\n'
         '未做：默认HOME修改、守护配置、同包名替换、实体场景验收。'
         '无日志、权限拒绝、提前结束和截断详见 report.json；不能据此断言固件锁死或已修复。\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True, help='操作者已确认且已授权的设备；不自动连接')
    parser.add_argument('--user', type=int, help='已核对的目标用户；未知时省略，不猜用户0')
    parser.add_argument('--package', default='home.quiet.tv', choices=('home.quiet.tv',))
    parser.add_argument('--record', type=Path, required=True, help='全新电脑私有目录')
    parser.add_argument('--seconds', type=int, default=90)
    parser.add_argument('--pull-files', action='store_true', help='只读保存原厂APK及乐视框架候选文件，不执行或修改')
    args = parser.parse_args()
    if not 1 <= args.seconds <= 180 or args.user is not None and args.user < 0 or args.serial.startswith('-') or re.search(r'\s|[\x00-\x1f]', args.serial):
        parser.error('需要明确设备、非负用户和1至180秒采集时间')
    args.record.mkdir(parents=True, exist_ok=False, mode=0o700)
    adb = [args.adb, '-s', args.serial]
    before, after = {}, {}
    session = dict(startedAt=stamp(), target='<device>', requestedUser=args.user, seconds=args.seconds,
                   connection=query(adb, 'get-state'), mode='read_only', deviceWrites=False)
    capture = LogCapture(adb, args.record)
    save(args.record/'session.json', session)
    code = 2
    try:
        if session['connection']['status'] != 'ok' or session['connection']['text'] != 'device':
            raise ValueError('指定设备未在线授权，未启动采集；不自动连接或提权')
        capture.start()
        print('日志流已启动，正在读取设备快照；请等“已开始采集”提示后再复现。', flush=True)
        before = snapshot(adb, args.user)
        save(args.record/'before.json', before)
        current = before.get('currentUser', {})
        if args.user is not None and current.get('status') == 'ok' and re.fullmatch(r'\d+', current.get('text', '')) and int(current['text']) != args.user:
            raise ValueError('实际当前用户与指定用户不同，停止复现与后续文件采集')
        print('已开始采集。请记录时间，并让用户从一个应用按一次实体HOME，反馈回到哪个页面。', flush=True)
        print('本工具不注入按键、不重启电视；复现窗口为' + str(args.seconds) + '秒（不含快照），Ctrl+C可提前停止并保存。', flush=True)
        deadline = time.monotonic() + args.seconds
        while capture.reason == 'recording' and time.monotonic() < deadline:
            time.sleep(0.1)
        capture.stop('time_limit')
        after = snapshot(adb, args.user)
        save(args.record/'after.json', after)
        identity = ('ro.product.model', 'ro.build.fingerprint')
        unchanged = all(before[key]['status'] == after[key]['status'] == 'ok'
                        and before[key]['text'] and before[key]['text'] == after[key]['text'] for key in identity)
        session['identityUnchanged'] = unchanged
        if args.pull_files and unchanged:
            save(args.record/'artifacts.json', pull_files(adb, args.record, before))
        elif args.pull_files:
            session['artifactSkipReason'] = '设备身份未能在前后快照确认一致，未拉取文件'
        code = 0 if capture.bytes and capture.reason == 'time_limit' and unchanged else 2
    except KeyboardInterrupt:
        capture.stop('operator_stop')
    except (OSError, ValueError) as error:
        session['error'] = redact(str(error), args.serial)
    finally:
        capture.stop('collection_failed')
        session.update(endedAt=stamp(), capture=dict(reason=capture.reason, bytes=capture.bytes,
                                                   omittedLongLines=capture.long_lines))
        save(args.record/'session.json', session)
        report(args.record, session, before, after)
    print('取证结束，资料仅保存在电脑；请检查脱敏后私下提供反馈报告及证据。', flush=True)
    return code


if __name__ == '__main__':
    raise SystemExit(main())
