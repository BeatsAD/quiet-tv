#!/usr/bin/env python3
"""Exactly one package mutation per invocation, with a pre-written recovery journal."""
import json
from pathlib import Path
import re
import sys
import time
from tv import ROOT, shell, screenshot

package = sys.argv[1]
if not re.fullmatch(r'[A-Za-z0-9_.]+', package):
    raise ValueError('Invalid package name')
state_path = ROOT / 'changes.json'
state = json.loads(state_path.read_text())
baseline = (Path(state['backup']) / 'packages-disabled.txt').read_text().splitlines()
if 'package:' + package in baseline:
    raise RuntimeError('Package was already disabled before this task')
if package not in state['disabled']:
    state['disabled'].append(package)
    state_path.write_text(json.dumps(state, indent=2) + '\n')
output = shell('pm disable-user --user 0 ' + package)
print(output, flush=True)
if 'new state: disabled-user' not in output:
    raise RuntimeError('Disable was not confirmed; journal retained so restore is safe')
shell('input keyevent KEYCODE_DPAD_RIGHT')
time.sleep(1)
screenshot(ROOT / 'evidence' / ('disabled-' + package + '.png'))
