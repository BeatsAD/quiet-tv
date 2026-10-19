#!/usr/bin/env python3
"""Run existing TV checks serially with explicit stock-HOME setup and exact restoration."""
import argparse
from pathlib import Path
import re
import subprocess

SCENES = ('home-recovery', 'setup', 'setup-focus', 'setup-prototype', 'connection-ui', 'pairing-cancel', 'dock', 'about', 'optimized', 'memory')
FIRST_SCREEN = {'setup', 'setup-prototype', 'connection-ui'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--scenarios', nargs='+', choices=SCENES, default=list(SCENES[:8]))
    args = parser.parse_args()
    def shell(*command):
        return subprocess.check_output([args.adb, '-s', args.serial, 'shell', *command], text=True, stderr=subprocess.STDOUT, timeout=180)
    if shell('getprop', 'ro.kernel.qemu').strip() != '1' or shell('am', 'get-current-user').strip() != '0':
        parser.error('Only a dedicated emulator, user 0, may run these fixture mutations')
    if args.output.exists():
        parser.error('Use a new output directory; prior evidence is never overwritten')
    previous = shell('cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME').strip().splitlines()[-1]
    if not re.fullmatch(r'[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+', previous) or previous.startswith('android/'):
        parser.error('Set a known emulator HOME before running fixture checks')
    stock = 'com.google.android.tvlauncher'
    state = re.search(r'User 0:.*?enabled=(\d+)', shell('dumpsys', 'package', stock))
    if not state or state[1] not in ('0', '1', '3'):
        parser.error('Expected the dedicated Android TV image and a supported stock enabled-state')
    setting = int(state[1])
    disabled = shell('pm', 'list', 'packages', '-d')
    args.output.mkdir(parents=True)
    def restore():
        shell('pm', {0:'default-state', 1:'enable', 3:'disable-user'}[setting], '--user', '0', stock)
        shell('cmd', 'package', 'set-home-activity', '--user', '0', previous)
    try:
        for scene in args.scenarios:
            restore()
            if scene in FIRST_SCREEN:
                shell('pm', 'enable', '--user', '0', stock)
                shell('cmd', 'package', 'set-home-activity', '--user', '0', stock+'/.MainActivity')
            result = shell('am', 'instrument', '-w', '-e', 'scenario', scene, 'home.quiet.tv.test/home.quiet.tv.ManagementInstrumentation')
            (args.output / (scene+'.txt')).write_text(result)
            if 'PASS:' not in result or 'FAIL:' in result:
                raise RuntimeError(scene+': '+result[-1600:])
            print('PASS '+scene, flush=True)
    finally:
        restore()
        if disabled != shell('pm', 'list', 'packages', '-d'):
            raise RuntimeError('Emulator package baseline differs; inspect before further mutations')
        after = shell('cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME').strip().splitlines()[-1]
        if after != previous:
            raise RuntimeError('Emulator HOME was not restored')


if __name__ == '__main__':
    main()
