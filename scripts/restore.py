#!/usr/bin/env python3
"""Re-enable only packages changed by this project, preserving original disabled apps."""
import json
import re
from datetime import datetime
from tv import ROOT, shell, screenshot

state = json.loads((ROOT / 'changes.json').read_text())
backup = __import__('pathlib').Path(state['backup'])
original_disabled = set((backup / 'packages-disabled.txt').read_text().splitlines())
failures = []
for package in state['disabled']:
    if 'package:' + package in original_disabled:
        raise RuntimeError(f'Refusing to change originally disabled package {package}')
    result = shell('pm enable --user 0 ' + package)
    print(result, flush=True)
    if 'new state: enabled' not in result:
        failures.append(package)
if failures:
    raise RuntimeError('Could not re-enable: ' + ', '.join(failures) + '. Journal preserved for retry.')
if state.get('homeChanged'):
    original_home = next(line.strip() for line in (backup / 'home-activity.txt').read_text().splitlines() if '/' in line)
    result = shell('cmd package set-home-activity --user 0 ' + original_home)
    print(result, flush=True)
    if 'Success' not in result:
        raise RuntimeError('Original HOME was not confirmed; restore journal preserved.')
    print(shell('input keyevent KEYCODE_HOME'), flush=True)
import time
time.sleep(2)
screenshot(ROOT / 'evidence' / ('restored-' + datetime.now().strftime('%Y%m%d-%H%M%S') + '.png'))
print('Restored enabled packages and original HOME. Quiet TV remains installed for optional use.')
