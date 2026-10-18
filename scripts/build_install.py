#!/usr/bin/env python3
"""Build, archive, install and verify an update. Does not change HOME or disable packages."""
import os
import subprocess
import time
from build_apk import build_apk, parse_summary
from verify_apk import verify_installed
from tv import ROOT, CONFIG, ADB, shell, screenshot, push_chunks

os.chdir(ROOT)
apk = build_apk(parse_summary(__doc__))
if os.environ.get('TV_ADB_BACKEND') == 'python':
    push_chunks(apk,'/data/local/tmp/quiet-tv.apk')
    result=shell('pm install -r /data/local/tmp/quiet-tv.apk',timeout=90)
    if 'Success' not in result: raise RuntimeError(result)
    print(result)
else:
    try:
        install=subprocess.run([ADB,'-s',CONFIG['ip']+':5555','install','-r',str(apk)],capture_output=True,text=True,timeout=90)
        message=install.stdout+install.stderr
        if 'No route to host' in message:
            os.environ['TV_ADB_BACKEND']='python'
            push_chunks(apk,'/data/local/tmp/quiet-tv.apk')
            message=shell('pm install -r /data/local/tmp/quiet-tv.apk',timeout=90)
        if 'Success' not in message: raise RuntimeError(message)
        print(message)
    except subprocess.TimeoutExpired:
        print('ADB install stalled; using shell/base64 transfer with MD5 verification.')
        push_chunks(apk,'/data/local/tmp/quiet-tv.apk')
        result=shell('pm install -r /data/local/tmp/quiet-tv.apk',timeout=90)
        if 'Success' not in result: raise RuntimeError(result)
        print(result)
verify_installed(apk)
print(shell('am start -n home.quiet.tv/.HomeActivity'))
time.sleep(2)
shell('input keyevent KEYCODE_DPAD_RIGHT')
time.sleep(.6)
screenshot(ROOT/'evidence/build-installed.png')
