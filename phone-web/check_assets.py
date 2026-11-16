#!/usr/bin/env python3
"""Verify bundled phone assets without requiring Node during an ordinary Android build."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT.parent / 'app/src/main/assets/phone-settings'


def main():
    manifest = json.loads((ASSETS / 'build-manifest.json').read_text(encoding='utf-8'))
    expected = {'package.json', 'package-lock.json', 'tsconfig.json', 'vite.config.ts', 'index.html', 'build.mjs', 'check_assets.py'}
    for folder in ('src', 'public'):
        expected.update(p.relative_to(ROOT).as_posix() for p in (ROOT / folder).rglob('*') if p.is_file())
    if expected != set(manifest['source']):
        raise SystemExit('Phone web inputs changed. Run npm ci and npm run build in phone-web.')
    for base, entries in ((ROOT, manifest['source']), (ASSETS, manifest['assets'])):
        for name, digest in entries.items():
            file = base / name
            if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest() != digest:
                raise SystemExit('Stale phone web resource: ' + name + '. Rebuild phone-web.')
    print('Phone settings source and bundled assets match.')


if __name__ == '__main__':
    main()
