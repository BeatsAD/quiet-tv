#!/usr/bin/env python3
from datetime import datetime
import json
from pathlib import Path
from tv import ROOT, shell, screenshot

folder = ROOT / 'backups' / datetime.now().strftime('%Y%m%d-%H%M%S')
folder.mkdir(parents=True)
commands = {
    'packages.txt': 'pm list packages -f -U -u',
    'packages-installed.txt': 'pm list packages',
    'packages-disabled.txt': 'pm list packages -d --user 0',
    'packages-enabled.txt': 'pm list packages -e --user 0',
    'packages-third-party.txt': 'pm list packages -3',
    'getprop.txt': 'getprop',
    'settings-global.txt': 'settings list global',
    'settings-secure.txt': 'settings --user 0 list secure',
    'settings-system.txt': 'settings --user 0 list system',
    'settings-dumpsys.txt': 'dumpsys settings',
    'package-dumpsys.txt': 'dumpsys package',
    'users.txt': 'pm list users',
    'home-activity.txt': 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME',
    'home-candidates.txt': 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME',
    'tv-apps.txt': 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER',
    'tv-inputs.txt': 'dumpsys tv_input',
    'memory.txt': 'cat /proc/meminfo',
    'display.txt': 'wm size',
    'display-density.txt': 'wm density',
    'wifi.txt': 'cmd wifi status',
}
for name, command in commands.items():
    output = shell(command, timeout=90)
    (folder / name).write_text(output)
    print(f'{name}: {len(output)} characters', flush=True)
screenshot(folder / '00-original.png')
if not (ROOT / 'changes.json').exists():
    (ROOT / 'backup-current.json').write_text(json.dumps({'path': str(folder)}, indent=2) + '\n')
    (ROOT / 'changes.json').write_text(json.dumps({'backup': str(folder), 'disabled': [], 'homeChanged': False}, indent=2) + '\n')
else:
    print('Original baseline and restore journal preserved; this folder is an additional snapshot.')
print('Backup complete:', folder)
