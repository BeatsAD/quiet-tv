#!/usr/bin/env python3
"""Check executable profiles and unverified references without contacting a device."""
import json
import re
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

# Opening a known system panel does not grant any package-management permissions.
panels = json.loads((source.parent / 'tv-input-profiles.json').read_text())
assert panels['schema'] == 1
components = set()
for panel in panels['panels']:
    component = panel['component']
    assert re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+', component), 'Invalid panel component'
    package, activity = component.split('/')
    canonical = package + '/' + (package + activity if activity.startswith('.') else activity)
    assert canonical not in components, 'Duplicate system input panel'
    components.add(canonical)
    assert panel['type'] in {'activity', 'service'}
    assert panel['type'] != 'service' or panel.get('action'), 'Services need an explicit action'
    assert all(panel.get(key) for key in ['labelZh', 'labelEn', 'source', 'validation'])
    assert panel['source'].startswith('https://github.com/Gathin23/cleanyourtv/'), 'Panel provenance is required'
    assert isinstance(panel.get('preferPanel', False), bool)
print(f'PASS: {len(components)} system input panel profile(s); runtime component and permission checks required')
