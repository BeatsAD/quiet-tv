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
        if app.get('category') == 'bootAd':
            assert device.get('brand') and device.get('model'), 'Boot ads require an exact brand and model'
            assert isinstance(app.get('versionCode'), int) and not isinstance(app['versionCode'], bool) and app['versionCode'] >= 0, 'Boot ads require an exact numeric versionCode'
            assert set(app['actions']) == {'ENABLE', 'DISABLE'}, 'Boot ads permit only disable and enable-state recovery'
            assert not app.get('recommendDisable'), 'Boot ads must not be preselected by first setup'
            assert app.get('purpose') and app.get('impact') and app.get('source', '').startswith('https://'), 'Boot ads require purpose, impact and source'
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
    component = panel.get('component', '')
    package = panel.get('package', '')
    if component:
        assert not package, 'Use either component or package/action'
        assert re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+', component), 'Invalid panel component'
        package_name, activity = component.split('/')
        canonical = package_name + '/' + (package_name + activity if activity.startswith('.') else activity)
    else:
        assert re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+', package), 'Invalid service package'
        assert panel['type'] == 'service' and panel.get('action'), 'Package resolution needs a service action'
        canonical = package + ':' + panel['action']
    assert canonical not in components, 'Duplicate system input panel'
    components.add(canonical)
    assert isinstance(panel.get('requiredFeature', ''), str)
    assert isinstance(panel.get('extras', {}), dict)
    for value in panel.get('extras', {}).values():
        assert type(value) in (str, int, bool), 'Only primitive input extras are supported'
        assert type(value) is not int or -2147483648 <= value <= 2147483647, 'Integer extra out of range'
    assert panel['type'] in {'activity', 'service'}
    assert panel['type'] != 'service' or panel.get('action'), 'Services need an explicit action'
    assert all(panel.get(key) for key in ['labelZh', 'labelEn', 'source', 'validation'])
    assert panel['source'].startswith(('https://github.com/Gathin23/cleanyourtv/', 'https://github.com/pengchujin/aerial-tv/')), 'Panel provenance is required'
    assert isinstance(panel.get('preferPanel', False), bool)
    assert isinstance(panel.get('foregroundService', False), bool)
    assert not panel.get('foregroundService') or panel['type'] == 'service'
print(f'PASS: {len(components)} system input panel profile(s); runtime component and permission checks required')
