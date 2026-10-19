#!/usr/bin/env python3
"""Compare a local Quiet TV APK with the installed package; never install it."""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shlex
import subprocess


def verify_installed(apk, serial=None, adb='adb'):
    if serial:
        def shell(command):
            return subprocess.check_output([adb, '-s', serial, 'shell', command], text=True, timeout=30)
        device = serial
    else:
        from tv import CONFIG, shell
        device = CONFIG['ip'] + ':5555'
    apk = Path(apk).resolve()
    local_sha256 = hashlib.sha256(apk.read_bytes()).hexdigest()
    paths = shell('pm path home.quiet.tv').strip().splitlines()
    installed, = [line[len('package:'):] for line in paths if line.startswith('package:')]
    installed_sha256 = shell('sha256sum ' + shlex.quote(installed)).split()[0]
    now = datetime.now().astimezone()
    record = {
        'checked_at': now.isoformat(timespec='seconds'),
        'device': device,
        'model': shell('getprop ro.product.model').strip(),
        'application_id': 'home.quiet.tv',
        'local_apk': str(apk),
        'installed_apk': installed,
        'local_sha256': local_sha256,
        'installed_sha256': installed_sha256,
        'matches': local_sha256 == installed_sha256,
    }
    receipt = apk.with_suffix(f'.installed-{now.strftime("%Y%m%dT%H%M%S%f%z")}.json')
    with receipt.open('x', encoding='utf-8') as output:
        output.write(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    print(f'安装核对记录：{receipt}')
    if not record['matches']:
        raise RuntimeError(f'本地与电视 APK 不一致，见 {receipt}')
    print(f'本地与电视 APK SHA-256 一致：{local_sha256}')
    return receipt


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--serial', help='Explicit emulator/device; omit to retain device.json behavior')
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args()
    verify_installed(args.apk, args.serial, args.adb)
