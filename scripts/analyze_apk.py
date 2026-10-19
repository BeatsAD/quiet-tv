#!/usr/bin/env python3
"""Read APK contents and report packaged bytes; does not contact a device."""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import zipfile


def analyze(apk):
    apk = Path(apk).resolve()
    groups = defaultdict(lambda: {'packaged_bytes': 0, 'decoded_bytes': 0, 'files': 0})
    with zipfile.ZipFile(apk) as archive:
        entries = archive.infolist()
        for entry in entries:
            name = entry.filename
            if entry.is_dir():
                continue
            if name.startswith('lib/'):
                group = '/'.join(name.split('/')[:2])
            elif name.endswith('.dex'):
                group = 'dex'
            elif name.startswith('assets/wallpapers/'):
                group = 'wallpapers'
            elif name.startswith('assets/about/'):
                group = 'about'
            else:
                group = name.split('/')[0] if '/' in name else 'other'
            item = groups[group]
            item['packaged_bytes'] += entry.compress_size
            item['decoded_bytes'] += entry.file_size
            item['files'] += 1
    size = apk.stat().st_size
    return {'apk': str(apk), 'bytes': size, 'mib': round(size / 1024**2, 3),
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
            'groups': dict(sorted(groups.items())),
            'zip_and_signing_overhead_bytes': size - sum(g['packaged_bytes'] for g in groups.values()),
            'largest_entries': [{'path': e.filename, 'packaged_bytes': e.compress_size}
                                for e in sorted(entries, key=lambda e: e.compress_size, reverse=True)[:12]]}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--output', type=Path, help='Create a new JSON report, never overwrite')
    args = parser.parse_args()
    report = analyze(args.apk)
    data = json.dumps(report, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open('x') as out:
            out.write(data)
    print(data)
