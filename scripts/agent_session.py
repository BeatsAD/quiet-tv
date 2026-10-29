#!/usr/bin/env python3
"""Keep a private, resumable task ledger around existing Quiet TV tools. Never replay a command."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys

import configure_boot_home as boot
import configure_home_guard as guard
import install_tcl as tcl
from verify_install import component, resolver

ROOT = Path(__file__).resolve().parent
TOOLS = ('install_quiet_tv.py', 'verify_install.py', 'install_tcl.py', 'tcl_cast.py',
         'configure_home_guard.py', 'configure_boot_home.py', 'device_maintenance.py',
         'device_selftest.py', 'device_adaptation.py', 'vendor_adaptation.py',
         'device-presets.json', 'agent_session.py', 'computer_resources.py', 'device_environment.py', 'remote_calibration.py')
GUIDES = {'agent.md': 'docs/AGENT.md', 'maintenance.md': 'docs/AGENT_ADAPT.zh-CN.md',
          'guides/rules.md': 'docs/DEVICE_ADAPTATION.zh-CN.md',
          **{'guides/' + name + '.md': 'docs/agent/' + name + '.md' for name in
             ('connect', 'install', 'update', 'home', 'tcl', 'restore', 'acceptance', 'inputs', 'session', 'remote')}}
RUNNERS = ('install_quiet_tv', 'install_tcl', 'configure_home_guard', 'configure_boot_home',
           'device_maintenance', 'device_selftest', 'device_adaptation', 'vendor_adaptation', 'device_environment', 'remote_calibration')
PACKAGES = ('home.quiet.tv', 'com.ts.launcher', 'com.coocaa.quiettv')
CHECKS = {'install': ('apk', 'basic_ui', 'desktop_configured', 'physical_home', 'recovery'),
          'update': ('apk', 'preservation'),
          'repair': ('repair',), 'restore': ('physical_home', 'recovery'),
          'inspect': ('inspection',), 'noop': ('inspection',)}
ROUTES = {'setup': tuple(CHECKS),
          'update': ('update', 'noop', 'inspect'), 'repair': ('repair', 'inspect'),
          'restore': ('restore', 'inspect'), 'inspect': ('inspect', 'noop')}
# Restoring the original desktop always needs a restore intent.
ROUTES['setup'] = tuple(route for route in CHECKS if route != 'restore')


def option(arguments, name, default=None):
    values = [arguments[i + 1] for i, arg in enumerate(arguments[:-1]) if arg == name]
    values += [arg.split('=', 1)[1] for arg in arguments if arg.startswith(name + '=')]
    if len(values) > 1 or arguments and arguments[-1] == name:
        raise ValueError('参数须完整且只能指定一次：' + name)
    return values[0] if values else default


def operation(tool, arguments):
    # The child parsers accept abbreviations; do not let an abbreviated flag evade routing.
    flags = ('--apply', '--goal', '--action', '--pause', '--restore-record', '--verify-only', '--adb-only')
    if any(arg.startswith('--') and any(flag.startswith(arg.split('=')[0])
            and flag != arg.split('=')[0] for flag in flags) for arg in arguments):
        raise ValueError('任务调用须使用完整参数名，不使用缩写')
    if tool == 'device_selftest':
        return 'test' if '--apply' in arguments else 'read'
    if tool == 'remote_calibration':
        if not arguments or arguments[0] not in ('status', 'begin', 'capture', 'preview', 'confirm', 'cancel'):
            raise ValueError('遥控器工具须先指定完整校准操作')
        return 'read' if arguments[0] == 'status' else 'remote'
    if tool == 'device_environment':
        if not arguments or arguments[0] not in ('adb', 'peripherals'):
            raise ValueError('现场工具须先指定 adb 或 peripherals')
        return 'read'
    if tool == 'install_quiet_tv':
        return 'read' if '--verify-only' in arguments else option(arguments, '--goal', 'trial')
    if tool == 'install_tcl':
        return 'restore' if option(arguments, '--restore-record') else 'trial'
    if tool == 'configure_boot_home':
        return 'restore' if option(arguments, '--restore-record') else 'takeover'
    if tool == 'configure_home_guard':
        return 'restore' if '--pause' in arguments else 'adb' if '--adb-only' in arguments else 'takeover'
    if tool == 'device_maintenance':
        if not arguments or arguments[0].startswith('-'):
            raise ValueError('维护工具须先指定完整操作名')
        return {'inspect': 'read', 'input-list': 'read', 'profile-get': 'read',
                'input-open': 'test', 'profile-set': 'input', 'profile-reset': 'input',
                'repair': 'home', 'management-config': 'adb', 'disable': 'manage',
                'restore': 'restore', 'restore-app': 'restore', 'pause': 'restore'}.get(arguments[0], 'unknown')
    action = option(arguments, '--action', 'inspect')
    if action == 'inspect':
        return 'read'
    if tool == 'device_adaptation':
        return 'rules' if action == 'rules-set' else 'takeover'
    return 'restore' if action in ('tcl-restore', 'legacy-app-restore', 'hisense-restore') else 'takeover'


def results(state):
    latest = {}
    for item in state.get('results', []):
        valid = (item.get('trial') == state.get('trial', {}).get('record') if item['check'] == 'basic_ui'
                 else item['step'] >= state.get('apkEvidenceAfter', 0) if item['check'] == 'apk'
                 else item['step'] >= state.get('evidenceAfter', 0))
        if valid:
            latest[item['check']] = item
    return latest


def completion(state):
    required = state.get('requiredChecks', [])
    latest = results(state)
    missing = [name for name in required if latest.get(name, {}).get('status') not in ('PASS', 'FAIL', 'LIMITED')]
    failed = [name for name in required if latest.get(name, {}).get('status') == 'FAIL']
    limited = [name for name in required if latest.get(name, {}).get('status') == 'LIMITED']
    pending = unresolved(state)
    status = ('failed' if failed or any(step['status'] == 'failed' for step in pending) else
              'pending' if pending else 'partial' if limited else
              'pending' if missing or not state.get('route') else 'complete')
    return dict(status=status, required=required, missing=missing, failed=failed, limited=limited)


def record_result(state, check, status, source, note, evidence=None):
    item = dict(check=check, status=status, source=source, note=note, at=stamp(), step=len(state['steps']))
    if check == 'basic_ui':
        item['trial'] = state.get('trial', {}).get('record')
    if evidence:
        item['evidence'] = evidence
    state.setdefault('results', []).append(item)


def plan(state, route, checks, issue, note):
    if route not in ROUTES[state['intent']]:
        raise ValueError('路线不符合原任务意图；目标改变须建立新任务并保留原恢复记录')
    if state.get('route') and state['steps'] and state['route'] != route:
        if completion(state)['status'] != 'complete':
            raise ValueError('当前路线尚未完成，不能改路线跳过缺项；目标改变须建立关联任务')
        state.setdefault('completedRoutes', []).append(dict(route=state['route'], at=stamp(), step=len(state['steps'])))
        state['evidenceAfter'] = len(state['steps']) + 1
    if route == 'repair' and not issue:
        raise ValueError('修复路线须说明 --issue，不能把所有问题交给 HOME／ADB repair')
    current = state.get('latestSnapshot', state.get('baseline', {}))
    installed = tcl.user_state(current.get('packageDump', ''), state['scope']['user'])
    if route in ('update', 'repair') or route == 'noop' and state['intent'] == 'setup':
        if not installed or not tcl.usable(installed):
            raise ValueError('尚未确认当前用户的可用安装；先只读核对，不当作升级、修复或正常无需操作')
    previous = state.get('requiredChecks', []) if state.get('route') == route else []
    state.update(route=route, issue=issue, planNote=note,
                 requiredChecks=list(dict.fromkeys((*CHECKS[route], *previous, *checks))))


def allowed(state, tool, arguments):
    action = operation(tool, arguments)
    route = state.get('route')
    if action == 'read':
        return action
    choices = {'install': ('trial', 'takeover', 'adb', 'home', 'input', 'rules', 'manage', 'test', 'remote'),
               'update': ('update', 'test'),
               'restore': ('restore',), 'inspect': (), 'noop': ()}
    if route == 'repair':
        issue = state.get('issue')
        actions = ('remote', 'test') if issue == 'remote' else ('home', 'takeover', 'adb', 'test') if issue == 'home' else ('adb', 'test') if issue == 'adb' else (
                  ('input', 'test') if issue == 'inputs' else ('manage', 'rules', 'test') if issue == 'apps' else ('test',))
    else:
        actions = choices.get(route, ())
    if action not in actions:
        raise ValueError('当前路线不允许此动作；先 plan 选择符合原意图的路线，不重新接管或扩大修复范围')
    if route == 'install' and action not in ('trial', 'test') and '--apply' in arguments:
        if results(state).get('basic_ui', {}).get('status') != 'PASS' or not state.get('trial'):
            raise ValueError('首次配置前先安装试用，并 accept basic_ui 记录用户实际结果')
        if action == 'remote' and results(state).get('desktop_configured', {}).get('status') != 'PASS':
            raise ValueError('先完成桌面路线配置，再进行遥控器逐键校准')
    return action


def collect_step(state, step, action, reconcile=False):
    record = Path(step['record'])
    events = record / 'record.jsonl'
    if events.is_file():
        entries = [json.loads(line) for line in events.read_text(encoding='utf-8').splitlines() if line.strip()]
        verified = next((item for item in reversed(entries) if item['event'] in
                         ('installed_apk_verified', 'unchanged_apk_verified', 'boot_apk')), None)
        if verified and verified['event'] == 'boot_apk':
            verified = {**verified, 'actual': verified['metadata']}
        if verified and reconcile:
            dump = state['latestSnapshot'].get('packageDump', '')
            match = re.search(r'\bversionCode=(\d+)', dump)
            if not match or int(match[1]) != verified['actual']['version_code']:
                verified = None
        if verified:
            record_result(state, 'apk', 'PASS', 'script', '实际 APK 已核验', str(events))
            if action == 'trial' and any(item['event'] == 'launch' for item in entries):
                state['trial'] = dict(record=step['record'], apk=str(record / 'installed-after.apk'),
                                      sha256=verified['actual']['sha256'])
            if verified['event'] == 'unchanged_apk_verified':
                record_result(state, 'preservation', 'PASS', 'script', '相同 APK 未安装、启动或重新配置', str(events))
        if (not reconcile or verified) and any(item['event'] in ('desktop_configured', 'boot_configured') for item in entries):
            record_result(state, 'desktop_configured', 'PASS', 'script', '桌面配置已执行并读回，实体场景另验', str(events))
    if (step['tool'] in ('configure_home_guard', 'device_adaptation', 'vendor_adaptation')
            and action == 'takeover' and step.get('exit') == 0 and '--apply' in step['arguments'] and record.exists()):
        record_result(state, 'desktop_configured', 'PASS', 'script', '配置工具已读回，实体场景另验', str(record))
    report = record / 'report.json'
    if step['tool'] == 'device_environment' and report.is_file():
        value = json.loads(report.read_text(encoding='utf-8'))
        if 'devices' in value and value.get('coverage'):
            if (value.get('serial') != state['scope']['serial'] or value.get('package') != state['package']
                    or value.get('identity', {}).get('fingerprint') != state['scope']['fingerprint']
                    or value.get('identity', {}).get('user') != state['scope']['user']):
                raise ValueError('外设现场不属于本任务设备／固件／用户')
            state['peripherals'] = dict(report=str(report), devices=value['devices'],
                                        coverage=value['coverage'], note=value['note'], recordedAt=value['createdAt'])
    if step['tool'] == 'device_selftest' and report.is_file():
        value = json.loads(report.read_text(encoding='utf-8'))
        if (value.get('serial') != state['scope']['serial'] or value.get('package') != state['package']
                or value.get('identity', {}).get('fingerprint') != state['scope']['fingerprint']
                or value.get('identity', {}).get('user') != state['scope']['user']):
            raise ValueError('自检报告不属于本任务设备／固件／用户')
        for item in value['checks']:
            if state.get('route') == 'restore' and item.get('source') == 'user':
                continue  # APK scenes describe Quiet TV, not the restored stock desktop.
            if item['status'] != 'SKIP':
                previous = results(state).get(item['name'], {})
                if item.get('source') == previous.get('source') == 'user':
                    recorded = item.get('evidence', {}).get('recordedAt', 0)
                    if recorded <= datetime.fromisoformat(previous['at']).timestamp() * 1000:
                        continue
                record_result(state, item['name'], item['status'], item.get('source', 'script'), item['detail'], str(report))


def stamp():
    return datetime.now(timezone.utc).isoformat()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, value):
    """Replace only our own small state file; the previous record survives interrupted writes."""
    temporary = path.with_suffix('.tmp')
    with open(temporary, 'w', encoding='utf-8') as stream:
        os.chmod(temporary, 0o600)
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


@contextmanager
def locked(directory):
    with open(directory / 'session.lock', 'a+b') as stream:
        stream.seek(0)
        if not stream.read(1):
            stream.write(b'0'); stream.flush()
        stream.seek(0)
        try:
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise ValueError('任务仍在运行，请等待原命令结束') from error
        os.set_inheritable(stream.fileno(), True)
        yield


def bundle_files(bundle=None):
    files = {str(ROOT / name): digest(ROOT / name) for name in TOOLS}
    if bundle:
        bundle = bundle.resolve()
        index = json.loads(bundle.read_text(encoding='utf-8'))
        if set(index.get('tools', {})) != set(TOOLS) or set(index.get('guides', {})) != set(GUIDES):
            raise ValueError('工具或说明清单不完整，请下载同一次 latest.json 的全部文件')
        for section in ('tools', 'guides'):
            for name, item in index[section].items():
                path = ROOT / name if section == 'tools' else bundle.parent / name
                if digest(path) != item['sha256']:
                    raise ValueError('工具或说明哈希不符：' + name)
                files[str(path.resolve())] = item['sha256']
        files[str(bundle)] = digest(bundle)
    else:
        for source in GUIDES.values():
            path = ROOT.parent / source
            if not path.is_file():
                raise ValueError('独立工具目录须提供 --bundle <同次下载的 latest.json>')
            files[str(path.resolve())] = digest(path)
    return files


def verify_files(state):
    for name, expected in state['files'].items():
        if digest(Path(name)) != expected:
            raise ValueError('本次任务的工具或说明已经变化，保留原目录和记录，不能混用版本：' + name)


def snapshot(adb, user, package):
    scope = boot.identity(adb, user)
    scope['hardwareSerial'] = adb.shell('getprop', 'ro.serialno') or adb.shell('getprop', 'ro.boot.serialno')
    values = {'scope': scope, 'package': package, 'checkedAt': stamp(), 'unknown': {}}
    queries = {'packageDump': ('dumpsys', 'package', package),
               'adbEnabled': ('settings', 'get', 'global', 'adb_enabled'),
               'accessibility': ('settings', '--user', str(user), 'get', 'secure', 'enabled_accessibility_services')}
    for key, command in queries.items():
        try:
            values[key] = adb.shell(*command)
        except (OSError, ValueError, subprocess.TimeoutExpired) as error:
            values['unknown'][key] = str(error)
    try:
        values['home'] = component(adb.shell(*resolver(adb, scope['sdk'], user)))
    except (OSError, ValueError, subprocess.TimeoutExpired) as error:
        values['unknown']['home'] = str(error)
    # Do not call application broadcasts here: they could start the app and mask boot failures.
    return values


def checked_snapshot(state):
    verify_files(state)
    adb = guard.ComputerAdb(state['adb'], state['scope']['serial'])
    current = snapshot(adb, state['scope']['user'], state['package'])
    if current['scope'] != state['scope']:
        raise ValueError('设备、固件、用户或 ADB 身份已变化；保留任务记录，未继续执行')
    return current


def alive(pid):
    if not pid:
        return False
    if os.name == 'nt':
        result = subprocess.check_output(['tasklist', '/FI', 'PID eq ' + str(pid), '/FO', 'CSV'], text=True)
        return '"' + str(pid) + '"' in result
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False


def unresolved(state):
    return [step for step in state['steps'] if step['status'] in ('running', 'failed')]


def summary(directory, state):
    current = state.get('latestSnapshot', state['baseline'])
    package_dump = current.get('packageDump', '')
    version = re.search(r'\bversionCode=(\d+)', package_dump)
    package_state = tcl.user_state(package_dump, state['scope']['user'])
    public_snapshot = {'home': current.get('home'), 'adbEnabled': current.get('adbEnabled'),
                       'packageState': package_state, 'versionCode': int(version[1]) if version else None,
                       'unknown': current['unknown']}
    value = {'intent': state['intent'], 'package': state['package'], 'session': str(directory),
             'route': state.get('route'), 'issue': state.get('issue'), 'completion': completion(state),
             'results': results(state), 'trial': state.get('trial'), 'completedRoutes': state.get('completedRoutes', []),
             'peripherals': state.get('peripherals'),
             'next': ('先核对未确认步骤并 review，不重放命令' if unresolved(state) else
                      '先 plan 选择路线和必要验收' if not state.get('route') else
                      '先安装试用，再 accept basic_ui 记录用户结果' if state['route'] == 'install'
                      and results(state).get('basic_ui', {}).get('status') != 'PASS' else
                      '补齐 completion.missing；受限项记录 LIMITED，最后 finish 汇总'),
             'steps': [{key: step.get(key) for key in ('id', 'tool', 'status', 'exit', 'record', 'log', 'review')}
                       for step in state['steps']], 'current': public_snapshot, 'snapshotStatus': 'checked' if state.get('latestSnapshot') else 'before-command-only',
             'note': 'returned 只表示工具退出成功；实体 HOME、重启、网络 ADB、冷启动和待机仍须分别验收。'}
    save(directory / 'summary.json', value)
    print(json.dumps(value, ensure_ascii=False, indent=2))


def run_step(directory, state, tool, arguments):
    if unresolved(state):
        raise ValueError('上一步未确认；先 resume、核对原记录并 review，不自动重试')
    action = allowed(state, tool, arguments)
    if state.get('route') == 'install' and tool == 'install_quiet_tv' and action == 'takeover':
        trial = state.get('trial')
        if not trial:
            raise ValueError('先安装试用，再配置接管')
        if option(arguments, '--apk') or option(arguments, '--sha256'):
            raise ValueError('首次接管自动沿用本任务已核验的试用 APK，不另选文件')
        arguments = [*arguments, '--apk', trial['apk'], '--sha256', trial['sha256']]
    reserved = ('--serial', '--user', '--adb', '--record', '--package')
    # Reject argparse abbreviations too; scope comes only from the confirmed session.
    if any(arg.startswith('--') and any(flag.startswith(arg.split('=')[0]) for flag in reserved)
           for arg in arguments):
        raise ValueError('设备、用户、工具路径、包名和记录路径由任务固定，不在子命令重复指定')
    if state['package'] != tcl.PACKAGE and tool not in ('device_maintenance', 'device_selftest', 'configure_home_guard', 'device_environment', 'remote_calibration'):
        raise ValueError('此工具只支持普通包名；专用包沿用已验证的同包名流程')
    current = checked_snapshot(state)
    number = len(state['steps']) + 1
    record = directory / ('step-%04d' % number)
    if tool == 'configure_home_guard':
        record = record.with_suffix('.json')
    log = directory / ('step-%04d.log' % number)
    command = [sys.executable, str(ROOT / (tool + '.py')), *arguments,
               '--serial', state['scope']['serial'], '--user', str(state['scope']['user']),
               '--adb', state['adb'], '--record', str(record)]
    if tool in ('device_maintenance', 'device_selftest', 'configure_home_guard', 'device_environment', 'remote_calibration'):
        command += ['--package', state['package']]
    step = {'id': number, 'tool': tool, 'arguments': arguments, 'status': 'running',
            'startedAt': stamp(), 'before': current, 'record': str(record), 'log': str(log)}
    state['steps'].append(step)
    if '--apply' in arguments and action == 'remote':
        for name in ('physical_home', 'remote'):
            if name in state.get('requiredChecks', []):
                record_result(state, name, 'SKIP', 'script', '校准阶段改变，需按最终映射重新验收')
    elif '--apply' in arguments and action not in ('read', 'test'):
        state['evidenceAfter'] = number
        if action in ('trial', 'update'):
            state.pop('trial', None)
            state['apkEvidenceAfter'] = number
    save(directory / 'session.json', state)
    summary(directory, state)
    # Stream output for TV prompts while keeping the original output in a private file.
    with open(log, 'x', encoding='utf-8') as output:
        os.chmod(log, 0o600)
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding='utf-8', errors='replace', close_fds=False)
        step['pid'] = process.pid
        save(directory / 'session.json', state)
        try:
            for line in process.stdout:
                output.write(line); output.flush()
                print(line, end='', flush=True)
            code = process.wait()
        except KeyboardInterrupt:
            # No automatic rollback or replay. Keep the durable running marker for reconciliation.
            print('任务中断；若原工具仍运行，请等待结束后再 resume。', file=sys.stderr)
            raise
        finally:
            process.stdout.close()
    step.update(status='returned' if code == 0 else 'failed', exit=code, endedAt=stamp())
    if code == 0:
        collect_step(state, step, action)
    # The next resume/run takes a fresh snapshot; do not relabel the old baseline as post-change.
    state.pop('latestSnapshot', None)
    save(directory / 'session.json', state)
    summary(directory, state)
    return code


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('action', choices=('start', 'plan', 'resume', 'run', 'review', 'accept', 'finish'))
    parser.add_argument('--session', type=Path, required=True)
    parser.add_argument('--serial')
    parser.add_argument('--user', type=int)
    parser.add_argument('--package', choices=PACKAGES, default=tcl.PACKAGE)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--intent', choices=('setup', 'update', 'repair', 'restore', 'inspect'))
    parser.add_argument('--bundle', type=Path)
    parser.add_argument('--step', type=int)
    parser.add_argument('--note', help='核对的原始记录、现场结果和下一步；不是验收通过标记')
    parser.add_argument('--tool', choices=RUNNERS)
    parser.add_argument('--route', choices=tuple(CHECKS))
    parser.add_argument('--issue', choices=('home', 'adb', 'remote', 'settings', 'network', 'inputs', 'peripheral', 'apps'))
    parser.add_argument('--checks', default='', help='额外必要验收，逗号分隔；外设可用 peripheral:实际设备')
    parser.add_argument('--check', help='用户实际确认的验收项')
    parser.add_argument('--status', choices=('PASS', 'FAIL', 'SKIP', 'LIMITED'))
    parser.add_argument('--source', choices=('user', 'agent'), default='user')
    raw = list(sys.argv[1:] if argv is None else argv)
    split = raw.index('--') if '--' in raw else len(raw)
    args = parser.parse_args(raw[:split])
    arguments = raw[split + 1:]
    directory = args.session.resolve()
    try:
        if args.action == 'start':
            if not args.serial or args.user is None or args.user < 0 or not args.intent:
                parser.error('start 需要已确认的 --serial、--user 和 --intent')
            files = bundle_files(args.bundle)
            current = snapshot(guard.ComputerAdb(args.adb, args.serial), args.user, args.package)
            directory.mkdir(mode=0o700, parents=False, exist_ok=False)
            state = {'schemaVersion': 1, 'createdAt': stamp(), 'intent': args.intent,
                     'package': args.package, 'adb': args.adb, 'scope': current['scope'],
                     'files': files, 'baseline': current, 'latestSnapshot': current, 'steps': []}
            save(directory / 'session.json', state)
            summary(directory, state)
            return 0
        with locked(directory):
            state = json.loads((directory / 'session.json').read_text(encoding='utf-8'))
            if state.get('schemaVersion') != 1:
                raise ValueError('任务记录版本不支持')
            if any(step['status'] == 'running' and alive(step.get('pid')) for step in state['steps']):
                raise ValueError('原工具进程仍在运行；不能开始另一项操作')
            if args.action == 'run':
                if not args.tool:
                    parser.error('run 需要 --tool，并在 -- 后提供工具自身参数')
                return run_step(directory, state, args.tool, arguments)
            current = checked_snapshot(state)
            state['latestSnapshot'] = current
            if args.action == 'review':
                if not args.note or not args.note.strip() or args.step is None:
                    parser.error('review 需要 --step 和明确的现场核对 --note')
                matches = [step for step in unresolved(state) if step['id'] == args.step]
                if len(matches) != 1:
                    raise ValueError('只能核对一条未确认步骤')
                matches[0].update(status='reviewed', review={'note': args.note, 'snapshot': current, 'at': stamp()})
                collect_step(state, matches[0], operation(matches[0]['tool'], matches[0]['arguments']), reconcile=True)
            elif args.action == 'plan':
                if not args.route or not args.note or not args.note.strip():
                    parser.error('plan 需要 --route 和说明判断依据的 --note')
                checks = [name.strip() for name in args.checks.split(',') if name.strip()]
                plan(state, args.route, checks, args.issue, args.note)
            elif args.action == 'accept':
                if not args.check or not args.status or not args.note or not args.note.strip():
                    parser.error('accept 需要 --check、--status 和用户实际反馈的 --note')
                if unresolved(state):
                    raise ValueError('先核对未确认步骤，不用验收结果跳过失败')
                if not state.get('route') or args.check not in state['requiredChecks']:
                    raise ValueError('先 plan 把本次需要验证的项目加入清单')
                if args.check == 'basic_ui' and args.status == 'PASS' and state['route'] == 'install' and not state.get('trial'):
                    raise ValueError('尚无本任务安装试用及实际 APK 核验记录')
                if args.check in ('apk', 'desktop_configured') and args.status == 'PASS':
                    raise ValueError('APK／配置核验由实际工具记录，不以人工反馈代替')
                if args.check in ('basic_ui', 'physical_home') and args.status == 'PASS' and args.source != 'user':
                    raise ValueError('基础试用与实体 HOME 需要用户实际反馈')
                record_result(state, args.check, args.status, args.source, args.note)
            save(directory / 'session.json', state)
            summary(directory, state)
            if args.action == 'finish':
                status = completion(state)['status']
                return 0 if status == 'complete' else 1 if status == 'failed' else 2
            return 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.TimeoutExpired, KeyboardInterrupt) as error:
        print('任务未继续：' + (str(error) or '用户中断') + '；保留原始记录。', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
