#!/usr/bin/env python3
"""Check executable profiles and unverified references without contacting a device."""
import json
from pathlib import Path

source = Path(__file__).resolve().parents[1] / 'app/src/main/assets/setup-presets.json'
data = json.loads(source.read_text())
assert data['schema'] == 1
seen = set()
for device in data['devices']:
    assert device.get('fingerprint'), 'Executable profiles require an exact firmware fingerprint'
    key = (device['fingerprint'], device.get('brand'), device.get('model'))
    assert key not in seen, 'Duplicate executable device rule'
    seen.add(key)
    packages = set()
    for app in device.get('apps', []):
        assert app['package'] not in packages, 'Duplicate package rule'
        packages.add(app['package'])
        assert set(app['actions']) <= {'ENABLE', 'DISABLE', 'UNINSTALL', 'RESTORE'}
        assert not app.get('recommendDisable') or 'DISABLE' in app['actions']
    home = device.get('home', {})
    assert not home.get('uninstallVerified') or home.get('restoreInstallVerified'), 'HOME uninstall requires verified recovery'
for reference in data.get('references', []):
    assert all(reference.get(k) for k in ['model', 'os', 'source', 'limitations'])
    assert not {'fingerprint', 'apps', 'home'} & reference.keys(), 'References cannot contain executable rules'
    assert set(reference['validation'].values()) == {'unverified'}, 'Promote verified cases through the explicit device rules'
print(f"PASS: {len(data['devices'])} executable firmware profile(s), {len(data.get('references', []))} unverified references; no inherited permissions")
