#!/usr/bin/env python3
"""Explicit-device HOME measurements. --exercise requires a dedicated emulator."""
import argparse
from datetime import datetime
import json
from pathlib import Path
import re
import statistics
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--exercise', action='store_true', help='Force-stop/relaunch only on emulator')
    parser.add_argument('--cold', type=int, default=5)
    parser.add_argument('--warm', type=int, default=20)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Report already exists')
    def shell(*command):
        return subprocess.check_output([args.adb, '-s', args.serial, 'shell', *command], text=True, timeout=45)
    if args.exercise and shell('getprop', 'ro.kernel.qemu').strip() != '1':
        parser.error('--exercise is restricted to a dedicated emulator; physical device is read-only')
    report = {'label': args.label, 'measured_at': datetime.now().astimezone().isoformat(),
              'device': args.serial, 'sdk': shell('getprop', 'ro.build.version.sdk').strip(),
              'abis': shell('getprop', 'ro.product.cpu.abilist').strip(),
              'installed': shell('dumpsys', 'package', 'home.quiet.tv'),
              'exercise': args.exercise, 'timing_metric': 'am start -W TotalTime, not first usable frame'}
    if args.exercise:
        for mode, count in [('cold', args.cold), ('warm', args.warm)]:
            samples = []
            for _ in range(count):
                if mode == 'cold':
                    shell('am', 'force-stop', 'home.quiet.tv')
                else:
                    shell('am', 'start', '-W', '-a', 'android.settings.SETTINGS')
                result = shell('am', 'start', '-W', '-n', 'home.quiet.tv/.HomeActivity')
                found = re.search(r'^TotalTime:\s*(\d+)', result, re.M)
                if not found:
                    raise RuntimeError('Launch timing unavailable: ' + result)
                samples.append(int(found[1]))
                time.sleep(1)
            report[mode] = {'samples_ms': samples, 'median_ms': statistics.median(samples),
                            'p95_ms': sorted(samples)[max(0, int(len(samples)*.95+.999)-1)]}
    report['memory'] = shell('dumpsys', 'meminfo', 'home.quiet.tv')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x') as out:
        json.dump(report, out, ensure_ascii=False, indent=2)
    print(json.dumps({k: v for k, v in report.items() if k not in ['installed', 'memory']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
