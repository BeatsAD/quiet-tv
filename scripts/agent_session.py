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
         'device-presets.json', 'remote_calibration.py', 'agent_session.py')
GUIDES = {'agent.md': 'docs/AGENT.md', 'maintenance.md': 'docs/AGENT_ADAPT.zh-CN.md',
          'guides/rules.md': 'docs/DEVICE_ADAPTATION.zh-CN.md',
          **{'guides/' + name + '.md': 'docs/agent/' + name + '.md' for name in
             ('connect', 'install', 'update', 'home', 'tcl', 'restore', 'acceptance', 'inputs', 'session', 'remote')}}
RUNNERS = ('install_quiet_tv', 'install_tcl', 'configure_home_guard', 'configure_boot_home',
           'device_maintenance', 'device_selftest', 'device_adaptation', 'vendor_adaptation', 'remote_calibration')
PACKAGES = ('home.quiet.tv', 'com.ts.launcher', 'com.coocaa.quiettv')


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
             'next': '有未确认步骤时先读原日志和现场，再 review；不重放历史命令。否则按目标读取对应指南。',
             'steps': [{key: step.get(key) for key in ('id', 'tool', 'status', 'exit', 'record', 'log', 'review')}
                       for step in state['steps']], 'current': public_snapshot, 'snapshotStatus': 'checked' if state.get('latestSnapshot') else 'before-command-only',
             'note': 'returned 只表示工具退出成功；实体 HOME、重启、网络 ADB、冷启动和待机仍须分别验收。'}
    save(directory / 'summary.json', value)
    print(json.dumps(value, ensure_ascii=False, indent=2))


def run_step(directory, state, tool, arguments):
    if unresolved(state):
        raise ValueError('上一步未确认；先 resume、核对原记录并 review，不自动重试')
    if state['intent'] == 'inspect' and not (tool == 'device_selftest' and not arguments
            or tool == 'device_maintenance' and arguments == ['inspect']
            or tool == 'remote_calibration' and arguments == ['status']):
        raise ValueError('只读任务只允许默认自检或 inspect；改变目标须明确建立新任务')
    if tool == 'install_quiet_tv' and state['intent'] in ('update', 'trial', 'restore'):
        if state['intent'] == 'restore':
            raise ValueError('恢复任务不能重新安装；按原记录选择恢复工具')
        goals = [arguments[i + 1] for i, value in enumerate(arguments[:-1]) if value == '--goal']
        goals += [value.split('=', 1)[1] for value in arguments if value.startswith('--goal=')]
        if goals != [state['intent']]:
            raise ValueError('安装目标必须保持任务原有的仅更新或仅试用意图')
    reserved = ('--serial', '--user', '--adb', '--record', '--package')
    # Reject argparse abbreviations too; scope comes only from the confirmed session.
    if any(arg.startswith('--') and any(flag.startswith(arg.split('=')[0]) for flag in reserved)
           for arg in arguments):
        raise ValueError('设备、用户、工具路径、包名和记录路径由任务固定，不在子命令重复指定')
    if state['package'] != tcl.PACKAGE and tool not in ('device_maintenance', 'device_selftest', 'configure_home_guard', 'remote_calibration'):
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
    if tool in ('device_maintenance', 'device_selftest', 'configure_home_guard', 'remote_calibration'):
        command += ['--package', state['package']]
    step = {'id': number, 'tool': tool, 'arguments': arguments, 'status': 'running',
            'startedAt': stamp(), 'before': current, 'record': str(record), 'log': str(log)}
    state['steps'].append(step)
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
    # The next resume/run takes a fresh snapshot; do not relabel the old baseline as post-change.
    state.pop('latestSnapshot', None)
    save(directory / 'session.json', state)
    summary(directory, state)
    return code


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('action', choices=('start', 'resume', 'run', 'review'))
    parser.add_argument('--session', type=Path, required=True)
    parser.add_argument('--serial')
    parser.add_argument('--user', type=int)
    parser.add_argument('--package', choices=PACKAGES, default=tcl.PACKAGE)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--intent', choices=('setup', 'update', 'trial', 'repair', 'restore', 'inspect'))
    parser.add_argument('--bundle', type=Path)
    parser.add_argument('--step', type=int)
    parser.add_argument('--note', help='核对的原始记录、现场结果和下一步；不是验收通过标记')
    parser.add_argument('--tool', choices=RUNNERS)
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
            save(directory / 'session.json', state)
            summary(directory, state)
            return 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.TimeoutExpired, KeyboardInterrupt) as error:
        print('任务未继续：' + (str(error) or '用户中断') + '；保留原始记录。', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
