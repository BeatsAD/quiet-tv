#!/usr/bin/env python3
"""Bounded computer-side Bluetooth evidence across APK replacement; no device mutations."""
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
import sys
import threading
import time
import uuid

MIB = 1024 * 1024
ENV = 'QUIETTV_BLUETOOTH_CASE'
PACKAGES = ('home.quiet.tv', 'com.coocaa.quiettv', 'com.ts.launcher')
STEPS = ('baseline', 'upgrade', 'default-home', 'stock-disable', 'guard',
         'physical-key-settings', 'physical-key-quiet', 'failure', 'recovery')
NAV = {'KEY_UP', 'KEY_DOWN', 'KEY_LEFT', 'KEY_RIGHT', 'KEY_ENTER', 'KEY_OK', 'KEY_SELECT',
       'KEY_HOME', 'KEY_HOMEPAGE', 'KEY_BACK', 'KEY_MENU', 'KEY_ESC', 'KEY_KPENTER',
       '0067', '006c', '0069', '006a', '001c', '0160', '0161', '0066', '00ac', '009e', '008b', '0001', '0060'}
PACKAGE = r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+'


def foreground_record(text, packages):
    """Whitelist fields only; an observed start/exit is not attribution of the client or root cause."""
    if not re.search(r'(?:^|\s)(?:ActivityManager|ActivityTaskManager|WindowManager|AndroidRuntime|(?:am|wm)_[a-z_]+)\s*:', text):
        return None
    related = [pkg for pkg in packages if re.search(r'(?<![\w.])'+re.escape(pkg)+r'(?![\w.])', text)]
    if not related:
        return None
    kind = re.search(r'\b(START|am_proc_start|am_proc_died|am_kill|am_crash|am_anr|am_finish_activity|am_pause_activity|am_resume_activity|wm_finish_activity|wm_pause_activity|wm_resume_activity)\b', text)
    if kind is None:
        kind = re.search(r'\b(FATAL EXCEPTION\b|Process:|Killing\b|ANR in\b)', text)
    if kind is None:
        return None
    result = dict(kind=kind[1], packages=related,
                  components=re.findall(r'(?<![\w.])'+PACKAGE+r'/[\w.$]+', text)[:4],
                  clientAttribution='unknown', outcome='system_observation_unverified')
    for key, expression in (
            ('callerUid', r'\bfrom uid\s+(\d+)'), ('callerPid', r'\bfrom pid\s+(\d+)'),
            ('action', r'\bact=(android\.intent\.action\.[A-Za-z0-9_]+)'),
            ('flags', r'\bflg=(0x[0-9a-fA-F]+)'), ('processPid', r'\bPID:\s*(\d+)')):
        match = re.search(expression, text)
        if match:
            result[key] = match[1]
    event_values = re.search(r'\[([^\]]+)\]\s*$', text)
    if event_values:
        # Retain positions of numeric fields; do not interpret vendor-dependent layouts.
        result['numericFields'] = {str(i):value.strip() for i,value in enumerate(event_values[1].split(',')[:12])
                                   if re.fullmatch(r'-?\d+', value.strip())}
    return result


def process_rows(text, packages):
    rows = []
    header = []
    for line in text.splitlines():
        fields = line.split()
        if 'PID' in fields:
            header = fields
        elif fields and re.fullmatch(PACKAGE+r'(?::[\w.]{1,64})?',fields[-1]) and any(fields[-1]==pkg or fields[-1].startswith(pkg+':') for pkg in packages):
            row = dict(process=fields[-1])
            for key in ('PID', 'PPID', 'USER', 'UID'):
                if key in header and header.index(key)<len(fields):
                    value = fields[header.index(key)]
                    if re.fullmatch(r'[A-Za-z0-9_]+', value):
                        row[key.lower()] = value
            rows.append(row)
    return rows[:64], max(0, len(rows)-64)


def service_state(text):
    records, unparsed = [], 0
    for line in text.splitlines():
        fields = {}
        component = re.search(r'ServiceRecord\{[0-9a-fA-F]+\s+u(\d+)\s+('+PACKAGE+r'/[\w.$]+)',line)
        if component:
            fields.update(user=component[1],component=component[2])
        process = re.search(r'ProcessRecord\{[0-9a-fA-F]+\s+(\d+):('+PACKAGE+r'(?::[\w.]+)?)',line)
        if process:
            fields.update(pid=process[1],process=process[2])
        fields.update(dict(re.findall(r'\b(isForeground|startRequested|hasBound|mBound|bindCount|connectionCount)\s*[:=]\s*(true\b|false\b|\d+\b)',line)))
        if fields:
            records.append(fields)
        elif line.strip():
            unparsed += 1
    return dict(records=records[:64],recordsOmitted=max(0,len(records)-64),unparsedLines=unparsed,
                limits='Only known service/process/binding fields; empty records are not proof of an absent or unbound service.')


def exit_rows(text):
    records, current = [], None
    for number, line in enumerate(text.splitlines(), 1):
        header = re.search(r'\bApplicationExitInfo\s+#(\d+)', line)
        if header:
            current = dict(entryIndex=int(header[1]), sourceLine=number)
            records.append(current)
        fields = dict(re.findall(r'\b(pid|realUid|packageUid|definingUid|userId|reason|status|importance|pss|rss)\s*=\s*(-?\d+)\b', line))
        stamp = re.search(r'\btimestamp\s*=\s*(\d{4}-\d\d-\d\d[ T]\d\d:\d\d:\d\d(?:\.\d{1,9})?(?:[+-]\d\d:?\d\d|Z)?|\d{10,16})\b', line)
        if stamp:
            fields['timestamp'] = stamp[1]
        if fields:
            if current is not None:
                current.update(fields)
            else:
                # No known record boundary: do not merge possibly unrelated vendor rows.
                records.append(dict(sourceLine=number, entryIndex='unknown', **fields))
    return records[:64], max(0,len(records)-64)


def target_snapshot(adb, meta, serial):
    packages = meta.get('watchPackages', [])
    if not packages:
        return dict(status='not_requested')
    options = dict(salt=meta['salt'], serial=serial)
    processes = shell(adb, 'ps', **options)
    rows, omitted = process_rows(processes['text'], packages)
    tasks = shell(adb, 'dumpsys', 'activity', 'activities', **options)
    resumed = []
    for line in tasks['text'].splitlines():
        if re.search(r'\b(?:mResumedActivity|topResumedActivity|mFocusedActivity)\b', line):
            resumed.extend(re.findall(r'(?<![\w.])'+PACKAGE+r'/[\w.$]+', line))
    exits = {}
    help_result = shell(adb, 'dumpsys', 'activity', '-h', **options)
    supported = help_result['status']=='ok' and re.search(r'\bexit-info\b', help_result['text'])
    for package in packages:
        if not supported:
            exits[package] = dict(status='unsupported_or_unadvertised', records=[])
            continue
        value = shell(adb, 'dumpsys', 'activity', 'exit-info', package, **options)
        records, omitted_exits = exit_rows(value['text'])
        exits[package] = dict(status=value['status'], records=records, recordsOmitted=omitted_exits,
                              queryUserScope='system_reported_not_filtered', truncated=value['truncated'])
    return dict(processes=dict(status=processes['status'], records=rows, recordsOmitted=omitted, truncated=processes['truncated']),
                foreground=dict(status=tasks['status'], components=resumed[:16], componentsOmitted=max(0,len(resumed)-16), truncated=tasks['truncated']),
                exitInfoCapability=dict(helpStatus=help_result['status'],advertised=bool(supported)), exitInfo=exits)


def foreground_report(folder, meta):
    if not meta.get('watchPackages'):
        return dict(status='not_requested')
    source = folder/'logcat.jsonl'
    records, omitted, unrecognized, invalid = [], 0, 0, 0
    packages = list(dict.fromkeys([meta['package'], *meta['watchPackages']]))
    if source.is_file():
        with source.open(encoding='utf-8') as lines:
            for number, line in enumerate(lines, 1):
                try:
                    item = json.loads(line)
                except ValueError:
                    invalid += 1
                    continue
                text = item.get('text', '')
                record = foreground_record(text, packages)
                if record:
                    if len(records)>=2000:
                        omitted += 1
                    else:
                        clock = re.match(r'^(\d\d-\d\d \d\d:\d\d:\d\d\.\d+)', text)
                        records.append(dict(hostTime=item.get('hostTime'), deviceLogTime=clock[1] if clock else 'unknown',
                                            sourceLine=number, **record))
                elif any(pkg in text for pkg in packages):
                    unrecognized += 1
    result = dict(schemaVersion=1, source='logcat.jsonl', status='summary', watchPackages=meta['watchPackages'], records=records,
                  recordsOmitted=omitted, relatedUnparsedLines=unrecognized, invalidLines=invalid,
                  limits='Only known system event formats; UID 2000 does not identify an ADB client. No matching event is not proof of no exit or crash. Full redacted context remains in logcat.jsonl.')
    save(folder/'foreground.json', result)
    return {key:value for key,value in result.items() if key!='records'} | dict(records=len(records))


def now():
    return dict(hostTime=datetime.now(timezone.utc).isoformat(), hostWallMs=int(time.time()*1000),
                hostMonotonicMs=int(time.monotonic()*1000))


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


def redact(text, salt='', serial=''):
    # Never feed unbounded vendor payloads into text patterns. Preserve an explicit gap.
    text = '\n'.join(line if len(line)<=16384 else '<omitted_long_line>' for line in text.split('\n'))
    if serial:
        text = text.replace(serial, '<device>')
    text = re.sub(r'(?i)\b(?:Bearer|Basic)\s+[^\s,;]+', '<credential>', text)
    text = re.sub(r'''(?ix)((?<!\w)["']?(?:password|passwd|pwd|token|secret|api[_-]?key|authorization|cookie|ssid|bssid|serial(?:no)?|android[_-]?id|link[_\ -]?key|ltk|irk|csrk|pin[_-]?code|(?:pairing[_\ -]?)?(?:pin|pass[_\ -]?key|code))["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^\s,;}\]]+)''', r'\1<redacted>', text)
    text = re.sub(r'(?i)((?:pass[_ -]?key|pairing[_ -]?code|pin[_ -]?code)\s+(?:is\s+)?)[0-9]{4,16}', r'\1<redacted>', text)
    text = re.sub(r'''(?ix)(?<!\w)["']?(?:device[_\ ]?name|remote[_\ ]?name|local[_\ ]?name|alias|name)["']?\s*[:=]\s*(?:"[^"]*"|'[^']*'|[^,;\r\n]+)''', 'name=<redacted>', text)
    text = re.sub(r'(?im)^(\s*(?:[0-9a-f]{2}:){5}[0-9a-f]{2}(?:\s+\[(?:BR/EDR|LE|DUAL)\])?)[^\r\n]*', r'\1 <device_label_removed>', text)
    text = re.sub(r'(?i)\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\b',
                  lambda m: 'host-bt-'+digest(salt+':'+m[0].upper())[:12], text)
    text = re.sub(r'(?i)(?:https?://|www\.)\S+', '<url>', text)
    text = re.sub(r'(?<![\w.+-])[\w.+-]{1,128}@[\w.-]{1,128}\.[A-Za-z]{2,32}', '<email>', text)
    text = re.sub(r'(?im)^(\s*Device\s+\d+:)[^\r\n]*', r'\1 <redacted>', text)
    def address(match):
        prefix = text[max(0, match.start()-40):match.start()]
        return match[0] if re.search(r'(?i)(?:version(?:Name)?|Build)\s*[:=]?\s*$', prefix) else '<ip>'
    text = re.sub(r'(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])', address, text)
    def ipv6(match):
        try:
            return '<ipv6>' if ipaddress.ip_address(match[0].split('%')[0]).version==6 else match[0]
        except ValueError:
            return match[0]
    return re.sub(r'(?i)(?<!\w)(?:[0-9a-f]{0,4}:){2,}[0-9a-f:]+(?:%\w+)?', ipv6, text)


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    if os.name != 'nt':
        path.chmod(0o600)


def event(folder, name, **fields):
    path = folder/'timeline.jsonl'
    if path.exists() and path.stat().st_size >= 2*MIB:
        raise ValueError('诊断时间线达到2MiB上限')
    data = (json.dumps(dict(**now(), event=name, **fields), ensure_ascii=False)+'\n').encode()
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
    try:
        os.write(fd, data)
    finally:
        os.close(fd)


def query(adb, arguments, salt='', serial='', seconds=3):
    started = now()
    process = subprocess.Popen([*adb, *arguments], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    data = bytearray()
    limited = [False]
    def read():
        while True:
            part = process.stdout.read(65536)
            if not part:
                return
            remaining = MIB-len(data)
            data.extend(part[:remaining])
            if len(part)>remaining:
                limited[0] = True
                process.kill()
                return
    reader = threading.Thread(target=read, daemon=True)
    reader.start()
    status = 'ok'
    try:
        process.wait(timeout=seconds)
    except subprocess.TimeoutExpired:
        status = 'timeout'
        process.kill()
        process.wait(timeout=2)
    reader.join(timeout=2)
    process.stdout.close()
    text = data.decode('utf-8', errors='replace')
    if status == 'ok':
        status = 'truncated' if limited[0] else 'ok' if process.returncode == 0 else 'failed'
        if re.search(r'(?im)^\s*(?:(?:logcat|getevent|dumpsys|/system/bin/\w+):\s*)?(?:Permission Denial|Permission denied|(?:java\.lang\.)?SecurityException)\b', text):
            status = 'permission_denied'
        elif re.search(r"(?im)^\s*(?:Can't find service|Unknown command|.*: not found)\b", text):
            status = 'unsupported'
    return dict(start=started, end=now(), status=status, exit=process.returncode,
                truncated=limited[0], omittedLongLines=sum(len(line)>16384 for line in text.split('\n')), text=redact(text, salt, serial))


def shell(adb, *words, **kwargs):
    return query(adb, ['shell', ' '.join(shlex.quote(str(word)) for word in words)], **kwargs)


def package_state(text, user):
    version = re.search(r'\bversionCode=(\d+)', text)
    name = re.search(r'\bversionName=([^\s]+)', text)
    row = re.search(r'(?m)^\s*User '+str(user)+r':([^\n]*)', text)
    result = dict(versionCode=int(version[1]) if version else None, versionName=name[1] if name else None,
                  installed=None, enabledSetting=None)
    if row:
        installed = re.search(r'\binstalled=(true|false)', row[1])
        enabled = re.search(r'\benabled=(\d+)', row[1])
        result.update(installed=installed[1]=='true' if installed else None,
                      enabledSetting=int(enabled[1]) if enabled else None)
    return result


def binding(folder, serial=None):
    meta = json.loads((folder/'session.json').read_text(encoding='utf-8'))
    if meta.get('schema') != 'quiet-bluetooth-1' or serial is not None and meta.get('serialDigest') != digest(serial):
        raise ValueError('诊断案例与设备不一致')
    return meta


def configuration(adb, meta, serial, targets=()):
    options = dict(salt=meta['salt'], serial=serial)
    result = {}
    for package in dict.fromkeys([meta['package'], *meta['stockPackages'], *meta.get('watchPackages', []), *targets]):
        value = shell(adb, 'dumpsys', 'package', package, **options)
        result[package] = dict(status=value['status'], **package_state(value['text'], meta['user']))
    help_result = shell(adb, 'pm', 'help', **options)
    signature = re.search(r'(?m)^\s*(?:pm\s+)?resolve-activity\s+([^\n]*(?:\n[ \t]+[^\n]*)*)', help_result['text'])
    if signature and '--user' in signature[0]:
        brief = ['--brief'] if '--brief' in signature[0] else []
        value = shell(adb, 'pm', 'resolve-activity', *brief, '--user', meta['user'], '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME', **options)
        components = re.findall(r'(?m)^\s*([\w.]+/[\w.$]+)\s*$', value['text'])
        result['home'] = dict(status=value['status'], component=components[-1] if len(components)==1 else None)
    else:
        result['home'] = dict(status='unsupported', component=None)
    result['accessibility'] = {}
    for key in ('accessibility_enabled', 'enabled_accessibility_services'):
        value = shell(adb, 'settings', '--user', meta['user'], 'get', 'secure', key, **options)
        result['accessibility'][key] = dict(status=value['status'], value=value['text'].strip() if value['status']=='ok' else None)
    value = shell(adb, 'dumpsys', 'accessibility', **options)
    services = []
    for line in value['text'].splitlines():
        components = re.findall(r'(?:\bid=|ComponentInfo\{)\s*([\w.]+/[\w.$]+)', line)
        flags = re.findall(r'\b(?:flags|capabilities|eventTypes)\s*[:=]\s*(?:0x[\da-fA-F]+|\d+|\[[A-Z_, |]+\])', line)
        if components or flags:
            services.append(dict(components=components, fields=flags))
    result['accessibility']['serviceReadback'] = dict(status=value['status'], services=services)
    return result


def snapshot(adb, folder, meta, serial, label):
    result = dict(label=label, **now(), configuration=configuration(adb, meta, serial), queries={})
    commands = {
        'deviceClock': ('date', '+%s'), 'deviceUptime': ('cat', '/proc/uptime'),
        'currentUser': ('am', 'get-current-user'),
        'model': ('getprop', 'ro.product.model'), 'sdk': ('getprop', 'ro.build.version.sdk'),
        'fingerprint': ('getprop', 'ro.build.fingerprint'),
        'bluetooth_manager': ('dumpsys', 'bluetooth_manager'), 'bluetooth': ('dumpsys', 'bluetooth'),
        'input': ('dumpsys', 'input'), 'accessibility': ('dumpsys', 'accessibility'),
        'windows': ('dumpsys', 'window', 'windows')}
    commands['bluetooth_services'] = ('dumpsys','activity','services','com.android.bluetooth')
    for index, package in enumerate(meta['stockPackages'][:3]):
        commands['stock_services_'+str(index)] = ('dumpsys','activity','services',package)
    result['servicePackagesOmitted'] = max(0,len(meta['stockPackages'])-3)
    for name, words in commands.items():
        value = shell(adb, *words, salt=meta['salt'], serial=serial)
        if name=='windows':
            # Window titles can contain private page text. Keep identities and state only.
            records = []
            for key, text in re.findall(r'(?m)^\s*(mCurrentFocus|mFocusedApp|mTopFocusedDisplayId)=([^\n]*)', value['text']):
                records.append(key+' components='+','.join(re.findall(r'[\w.]+/[\w.$]+', text))+' user='+','.join(re.findall(r'\bu\d+\b', text)))
            value['text'] = '\n'.join(records)
        if name=='bluetooth_services' or name.startswith('stock_services_'):
            value['text'] = json.dumps(service_state(value['text']),ensure_ascii=False)
        result['queries'][name] = value
    result['targets'] = target_snapshot(adb, meta, serial)
    path = folder/(label+'-'+uuid.uuid4().hex[:8]+'.json')
    save(path, result)
    event(folder, 'system.snapshot', source='system_query', file=path.name, label=label,
          statuses={key:value['status'] for key,value in result['queries'].items()})
    return result


def scope(arguments, is_shell, package):
    args = [str(word) for word in arguments]
    if not args:
        return None
    if not is_shell:
        return dict(action='install', target=package) if args[0]=='install' else None
    core = args[2:] if args[:2]==['cmd', 'package'] else args[1:] if args[0]=='pm' else []
    if core and core[0] in ('disable-user', 'enable', 'default-state', 'uninstall', 'install-existing', 'set-home-activity', 'grant', 'revoke'):
        return dict(action=core[0], target=core[-2] if core[0] in ('grant','revoke') else core[-1])
    if args[0] in ('settings', 'appops') and any(word in args for word in ('put', 'delete', 'set')):
        return dict(action=args[0], target=package)
    if args[0]=='setprop' and len(args)>1 and re.fullmatch(r'(?:persist\.)?sys\.(?:home|launcher)[\w.]*|service\.adb\.tcp\.port', args[1]):
        return dict(action='setprop', target=package)
    if args[:2] in (['am','start'],['am','start-activity']):
        for flag in ('-n','-p'):
            if flag in args and args.index(flag)+1<len(args):
                target=args[args.index(flag)+1]
                if re.fullmatch(PACKAGE+r'(?:/[\w.$]+)?',target):
                    return dict(action='start_activity',target=target)
    if args[:2]==['am', 'broadcast'] and '--es' in args and 'operation' in args:
        index = args.index('operation')
        operation = args[index+1] if index+1<len(args) else ''
        if operation in ('pause', 'proxy_prepare', 'proxy_finish', 'proxy_repair', 'takeover_preference', 'takeover_finish', 'adb_guard_configure', 'window_guard_configure', 'rules_set', 'takeover_record', 'management_config', 'finish_restore', 'boot_home_reconcile'):
            return dict(action=operation, target=package)
    return None


def begin_command(executable, serial, arguments, is_shell):
    """Optional instrumentation for existing authorized tools, never execute their mutation."""
    directory = os.environ.get(ENV)
    if not directory:
        return None
    folder = Path(directory)
    meta = binding(folder, serial)
    command = scope(arguments, is_shell, meta['package'])
    if command is None:
        return None
    if meta.get('endedAt'):
        raise ValueError('诊断采集已结束，操作不再包含于本次录制')
    operation = uuid.uuid4().hex[:12]
    adb = [executable, '-s', serial]
    if '--user' in arguments and str(arguments[list(arguments).index('--user')+1]) != str(meta['user']):
        raise ValueError('操作用户与诊断案例不同')
    target = command['target'].split('/')[0]
    targets = [target] if re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+', target) else []
    before = configuration(adb, meta, serial, targets)
    event(folder, 'operation.before', operation=operation, source='computer_tool', command=command, readback=before)
    tool = Path(sys.argv[0]).name
    event(folder, 'operation.begin', operation=operation, source='computer_tool', command=command,
          hostProcess=os.getpid(), toolEntry=tool if re.fullmatch(r'[\w.-]{1,80}',tool) else 'unknown', capture=meta.get('captureId','unknown'))
    return folder, meta, serial, adb, operation, command, targets


def end_command(token, success):
    if token is None:
        return
    folder, meta, serial, adb, operation, command, targets = token
    event(folder, 'operation.return', operation=operation, source='computer_tool', success=success)
    after = configuration(adb, meta, serial, targets)
    event(folder, 'operation.result', operation=operation, source='computer_tool', command=command, commandSucceeded=success, readback=after)


def navigation(line):
    match = re.search(r'((?:/dev/input/event\d+:\s*)?)(EV_KEY|0001)\s+(\w+)\s+(UP|DOWN|REPEAT|[0-9a-fA-F]{8})\s*$', line)
    if not match or match[3] not in NAV and match[3].lower() not in NAV:
        return None
    return dict(device=match[1].strip(), code=match[3], value=match[4])


class Stream:
    def __init__(self, adb, folder, meta, serial, name, arguments, limit, seconds):
        self.folder, self.meta, self.serial, self.name = folder, meta, serial, name
        self.limit, self.bytes, self.omitted, self.reason = limit, 0, 0, 'recording'
        self.lock, self.stopped = threading.Lock(), None
        self.process = subprocess.Popen([*adb, *arguments], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        self.thread = threading.Thread(target=self.read, daemon=True)
        self.thread.start()
        self.timer = threading.Timer(seconds, lambda:self.stop('time_limit'))
        self.timer.daemon = True
        self.timer.start()

    def read(self):
        path = self.folder/(self.name+'.jsonl')
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            while True:
                line = self.process.stdout.readline(16384)
                if not line:
                    if self.reason=='recording':
                        self.reason = 'stream_ended'
                    return
                if len(line)==16384 and not line.endswith(b'\n'):
                    self.omitted += 1
                    while line and not line.endswith(b'\n'):
                        line = self.process.stdout.readline(16384)
                    continue
                text = line.decode('utf-8', errors='replace').rstrip()
                if self.name=='keys':
                    if re.search(r'(?i)permission denied|operation not permitted', text):
                        self.reason = 'permission_denied'
                        return
                    value = navigation(text)
                    if value is None:
                        self.omitted += 1
                        continue
                    value.update(deviceMonotonic=text[:text.find(']')+1] if text.startswith('[') else 'unknown')
                else:
                    value = dict(text=redact(text, self.meta['salt'], self.serial))
                data = (json.dumps(dict(**now(), **value), ensure_ascii=False)+'\n').encode()
                if self.bytes+len(data)>self.limit:
                    self.reason = 'size_limit'
                    return
                os.write(fd, data)
                self.bytes += len(data)
        except (OSError, ValueError):
            self.reason = 'read_failed'
        finally:
            os.close(fd)

    def stop(self, reason):
        with self.lock:
            if self.stopped is None:
                self.stopped = self._stop(reason)
            return self.stopped

    def _stop(self, reason):
        self.timer.cancel()
        if self.reason=='recording':
            self.reason = reason
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=2)
        self.thread.join(timeout=2)
        self.process.stdout.close()
        return dict(reason=self.reason, bytes=self.bytes, omitted=self.omitted, exit=self.process.returncode)


def report(folder, meta):
    events = [json.loads(line) for line in (folder/'timeline.jsonl').read_text(encoding='utf-8').splitlines()]
    steps = {step:[] for step in STEPS}
    for item in events:
        if item.get('event')=='user.mark':
            steps[item['step']].append(dict(phase=item['phase'], outcome=item['outcome'], time=item['hostTime']))
    foreground = foreground_report(folder, meta)
    save(folder/'coverage.json', dict(streams=meta.get('streams', {}), steps=steps, foreground=foreground,
         limits='日志可能受权限、掉线、容量和厂商限制；空记录不是没有按键或故障；用户结果不等于系统证据。'))
    text = ['# 蓝牙采集覆盖报告', '', '这是取证覆盖情况，不是根因结论或实体遥控器验收。', '',
            '停止原因：'+str(meta.get('stopReason','recording')),
            '前后设备／固件／用户一致：'+str(meta.get('identityUnchanged','unknown')),
            '前台用户已核对：'+str(meta.get('currentUserVerified','unknown')), '', '| 通道 | 状态 | 字节 | 未保存行 |', '| --- | --- | --- | --- |']
    if meta.get('error') or meta.get('afterError'):
        text[4:4] = ['采集异常：'+str(meta.get('error',''))+'  '+str(meta.get('afterError','')), '']
    for name, value in meta.get('streams', {}).items():
        text.append(f"| {name} | {value.get('reason','unknown')} | {value.get('bytes',0)} | {value.get('omitted',0)} |")
    text += ['', '| 场景 | 用户记录 |', '| --- | --- |']
    for step, values in steps.items():
        text.append('| '+step+' | '+('；'.join(value['phase']+' '+value['outcome']+' '+value['time'] for value in values) if values else '未执行／未记录')+' |')
    text += ['', '电脑时间、设备墙钟、设备uptime分别保存，不假定时钟一致。keys是系统可读的导航键摘录，不等于所有物理键；读取受限、被筛掉或没有事件均不能证明未按键。',
             '指定应用摘要：'+str(foreground),
             'operation.begin／return仅证明电脑工具调用和返回；before／result为只读状态采样。检查每项status，失败命令可能已经部分生效。',
             '任意终端或系统界面中的修改不自动获得电脑工具operation记录，应另作user.mark并核对现场。',
             '近期应用内摘要与本机完整上下文互补；检查敏感信息后私下共享，保留原始案例，不以报告自动认定Quiet TV或原桌面是根因。']
    (folder/'采集覆盖.md').write_text('\n'.join(text)+'\n', encoding='utf-8')
    if os.name != 'nt':
        (folder/'采集覆盖.md').chmod(0o600)


def collect(args):
    folder = args.output
    folder.mkdir(parents=True, exist_ok=False, mode=0o700)
    meta = dict(schema='quiet-bluetooth-1', captureId=uuid.uuid4().hex[:16], **now(), serialDigest=digest(args.serial), salt=uuid.uuid4().hex,
                package=args.package, stockPackages=args.stock_package, watchPackages=list(dict.fromkeys(getattr(args,'watch_package',[]))), user=args.user, seconds=args.seconds,
                mode='read_only', streams={}, endedAt=None)
    save(folder/'session.json', meta)
    adb = [args.adb, '-s', args.serial]
    streams = []
    before = None
    reason = 'collection_failed'
    try:
        state = query(adb, ['get-state'], serial=args.serial)
        if state['status']!='ok' or state['text'].strip()!='device':
            raise ValueError('指定设备未在线授权，不自动连接或选择其他设备')
        help_result = query(adb, ['logcat', '--help'], serial=args.serial)
        save(folder/'logcat-help.json', help_result)
        arguments = ['logcat', '-v', 'threadtime']
        if re.search(r'\s-b\b', help_result['text']):
            for name in ('main', 'system', 'events', 'crash'):
                if name in help_result['text']:
                    arguments += ['-b', name]
        deadline = time.monotonic()+args.seconds
        streams.append(Stream(adb, folder, meta, args.serial, 'logcat', arguments, 32*MIB, args.seconds))
        if args.keys:
            help_result = shell(adb, 'getevent', '-h', serial=args.serial)
            save(folder/'getevent-help.json', help_result)
            if '-t' in help_result['text'] or re.search(r'\[[^\]]*t[^\]]*\]', help_result['text']):
                streams.append(Stream(adb, folder, meta, args.serial, 'keys', ['shell', 'getevent -t'], MIB, max(.01,deadline-time.monotonic())))
            else:
                meta['streams']['keys'] = dict(reason='unsupported', bytes=0)
        event(folder, 'capture.begin', source='computer_collector', buffers=arguments, keysRequested=args.keys)
        before = snapshot(adb, folder, meta, args.serial, 'before')
        user = before['queries']['currentUser']
        if user['status']=='ok' and user['text'].strip().isdecimal() and int(user['text'].strip())!=args.user:
            raise ValueError('前台用户与指定用户不一致，停止复现')
        meta['currentUserVerified'] = user['status']=='ok' and user['text'].strip()==str(args.user)
        if time.monotonic()>=deadline or streams[0].reason!='recording':
            raise ValueError('前置采集期间录制已结束，未开始复现；请检查流状态或增加时限')
        print('已开始采集 READY：日志流独立于Quiet TV进程，升级或应用退出后继续录制。', flush=True)
        print('请保留现场；本工具不安装、不改HOME、不启停系统包。', flush=True)
        while time.monotonic()<deadline and not (folder/'stop.request').exists() and streams[0].reason=='recording':
            time.sleep(0.1)
        reason = 'operator_stop' if (folder/'stop.request').exists() else 'time_limit' if time.monotonic()>=deadline else 'stream_ended'
    except KeyboardInterrupt:
        reason = 'operator_interrupt'
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        meta['error'] = redact(str(error), meta['salt'], args.serial)
    finally:
        for stream in streams:
            meta['streams'][stream.name] = stream.stop(reason)
        try:
            if not streams:
                raise ValueError('日志流未启动，不重复查询离线或未授权设备')
            after = snapshot(adb, folder, meta, args.serial, 'after')
            if before is not None:
                meta['identityUnchanged'] = all(before['queries'][key]['status']==after['queries'][key]['status']=='ok'
                    and before['queries'][key]['text'].strip() and before['queries'][key]['text']==after['queries'][key]['text']
                    for key in ('model','fingerprint','currentUser'))
        except (OSError, ValueError, subprocess.SubprocessError) as error:
            meta['afterError'] = redact(str(error), meta['salt'], args.serial)
        meta.update(endedAt=now()['hostTime'], stopReason=reason)
        save(folder/'session.json', meta)
        event(folder, 'capture.end', source='computer_collector', reason=reason, streams=meta['streams'])
        report(folder, meta)
    print('采集已保存；检查session.json中的缺失、权限、截断及停止原因后再私下分享。', flush=True)
    stream = meta['streams'].get('logcat', {})
    normal = ('time_limit','operator_stop','operator_interrupt')
    return 0 if reason in normal and stream.get('reason') in normal and stream.get('bytes',0)>0 and not meta.get('error') else 2


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    capture = sub.add_parser('collect')
    capture.add_argument('--adb', default='adb')
    capture.add_argument('--serial', required=True)
    capture.add_argument('--user', required=True, type=int)
    capture.add_argument('--package', choices=PACKAGES, default=PACKAGES[0])
    capture.add_argument('--stock-package', action='append', default=[])
    capture.add_argument('--watch-package', action='append', default=[], help='最多3个明确应用包名，补充前后进程、前台与系统退出摘要')
    capture.add_argument('--seconds', type=int, default=180)
    capture.add_argument('--keys', action='store_true', help='只读观察系统可见的导航键，不记录文本、数字键或修改权限')
    for action in ('collect','mark','stop','report'):
        command = capture if action=='collect' else sub.add_parser(action)
        command.add_argument('--output', required=True, type=Path)
        if action=='mark':
            command.add_argument('--step', required=True, choices=STEPS)
            command.add_argument('--phase', choices=('before','after','failure','recovered','note'), default='note')
            command.add_argument('--outcome', choices=('pass','fail','unknown'), default='unknown')
            command.add_argument('--observation', default='')
    args = parser.parse_args()
    if args.action=='collect':
        if not 1<=args.seconds<=600 or args.user<0 or args.serial.startswith('-') or re.search(r'\s|[\x00-\x1f]', args.serial):
            parser.error('需要明确设备、非负用户及1至600秒时限')
        if any(not re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+', value) for value in args.stock_package):
            parser.error('原桌面包名无效')
        if len(set(args.watch_package))>3 or any(not re.fullmatch(PACKAGE, value) for value in args.watch_package):
            parser.error('最多指定3个有效应用包名')
        return collect(args)
    meta = binding(args.output)
    if args.action=='mark':
        if meta.get('endedAt'):
            parser.error('采集已结束，不能把后续观察写成录制期间的结果')
        event(args.output, 'user.mark', source='user', step=args.step, phase=args.phase, outcome=args.outcome,
              observation=redact(args.observation[:1000], meta['salt']))
    elif args.action=='stop':
        with (args.output/'stop.request').open('a', encoding='utf-8'):
            pass
    else:
        report(args.output, meta)
    return 0


if __name__=='__main__':
    raise SystemExit(main())
