#!/usr/bin/env python3
"""Single-command ADB client. TV_ADB_BACKEND=python bypasses macOS adb privacy failures."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / 'device.json').read_text())
ADB = os.environ.get('ADB', CONFIG['adb'])

def shell(command, binary=False, timeout=45):
    if os.environ.get('TV_ADB_BACKEND', 'native') == 'python':
        from adb_shell.adb_device import AdbDeviceTcp
        from adb_shell.auth.sign_pythonrsa import PythonRSASigner
        key = Path.home() / '.android/adbkey'
        signer = PythonRSASigner(key.with_suffix('.pub').read_text(), key.read_text())
        device = AdbDeviceTcp(CONFIG['ip'], 5555, default_transport_timeout_s=timeout)
        device.connect(rsa_keys=[signer], auth_timeout_s=30)
        try:
            return device.shell(command, decode=not binary, read_timeout_s=timeout)
        finally:
            device.close()
    args = [ADB, '-s', CONFIG['ip'] + ':5555', 'exec-out' if binary else 'shell', command]
    result = subprocess.run(args, capture_output=True, timeout=timeout, check=True)
    return result.stdout if binary else result.stdout.decode('utf-8', errors='replace')

def screenshot(path):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    data = shell('screencap -p', binary=True)
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
        raise RuntimeError('TV did not return a PNG screenshot')
    path.write_bytes(data)
    print(path.resolve())

def push_chunks(source, destination):
    """Separate shell calls, no sync push. Verify whole-file MD5 before use."""
    data = Path(source).read_bytes()
    encoded = base64.b64encode(data).decode('ascii')
    staging = destination + '.b64'
    shell(': > ' + shlex.quote(staging))
    for offset in range(0, len(encoded), 24000):
        chunk = encoded[offset:offset + 24000]
        shell("printf '%s' " + shlex.quote(chunk) + ' >> ' + shlex.quote(staging))
    shell('base64 -d ' + shlex.quote(staging) + ' > ' + shlex.quote(destination))
    remote = shell('md5sum ' + shlex.quote(destination)).split()[0]
    expected = hashlib.md5(data).hexdigest()
    if remote != expected:
        raise RuntimeError(f'MD5 mismatch: {remote} != {expected}')
    print(f'MD5 verified: {remote} ({len(data)} bytes)')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest='action', required=True)
    sub.add_parser('shell').add_argument('command')
    sub.add_parser('screenshot').add_argument('path')
    push = sub.add_parser('push-chunks')
    push.add_argument('source')
    push.add_argument('destination')
    args = parser.parse_args()
    if args.action == 'shell':
        print(shell(args.command), end='')
    elif args.action == 'screenshot':
        screenshot(args.path)
    else:
        push_chunks(args.source, args.destination)
