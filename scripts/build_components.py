#!/usr/bin/env python3
"""Create immutable local component archives and signed catalogs. Never upload or install."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    pack = sub.add_parser('pack')
    pack.add_argument('--kind', choices=('weather', 'tailscale'), required=True)
    pack.add_argument('--target', choices=('tv', 'phone'), required=True)
    pack.add_argument('--version', default='1.0.0')
    pack.add_argument('--release', default='2.0.0')
    pack.add_argument('--release-tag', help='Independent GitHub Release tag; overrides --release')
    pack.add_argument('--core', type=Path)
    pack.add_argument('--output', type=Path, required=True)
    index = sub.add_parser('index')
    index.add_argument('--artifact', type=Path, action='append', required=True)
    index.add_argument('--key', type=Path, required=True)
    index.add_argument('--revision', type=int, required=True)
    index.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.action == 'pack':
        if not all(re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', v) for v in (args.version, args.release)):
            parser.error('Invalid version')
        tag = args.release_tag or f'v{args.release}'
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}', tag) or '..' in tag or tag.endswith('.'):
            parser.error('Invalid release tag')
        if args.kind == 'weather':
            if args.target != 'tv':
                parser.error('Weather resources belong to the TV')
            directory = ROOT / 'app/src/main/assets/weather-gpu'
            paths = sorted(p for p in directory.iterdir() if p.is_file() and not p.name.endswith('.glsl') and p.name != 'weather.json')
        else:
            if args.core is None:
                parser.error('--core is required')
            data = args.core.read_bytes()
            if data[:6] != b'\x7fELF\x02\x01' or int.from_bytes(data[18:20], 'little') != 183:
                parser.error('The remote core must be ARM64 ELF')
            paths = [args.core]
        if not paths:
            parser.error('No component inputs')
        files = [(('libquietremote.so' if args.kind == 'tailscale' else p.name), p.read_bytes()) for p in paths]
        args.output.mkdir(parents=True, exist_ok=True)
        name = f'QuietTV_{args.target}_{args.kind}_{args.version}' + ('_arm64-v8a' if args.kind == 'tailscale' else '') + '.zip'
        archive = args.output / name
        with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as package:
            for filename, data in files:
                entry = zipfile.ZipInfo(filename, (1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = 0o100400 << 16
                package.writestr(entry, data)
        for path, (_, data) in zip(paths, files):
            if path.read_bytes() != data:
                raise RuntimeError('Component inputs changed during packaging; archive is not deliverable')
        repository = 'BeatsAD/quiet-tv-components' if args.kind == 'weather' else 'BeatsAD/quiet-tv'
        artifact = dict(id=args.kind, target=args.target, hostApi=1, version=args.version,
                        minSdk=21 if args.kind == 'weather' else 26,
                        abi='universal' if args.kind == 'weather' else 'arm64-v8a',
                        bytes=archive.stat().st_size, expandedBytes=sum(len(b) for _, b in files),
                        sha256=sha(archive.read_bytes()),
                        url=f'https://github.com/{repository}/releases/download/{tag}/{name}',
                        files=[dict(name=n, bytes=len(b), sha256=sha(b)) for n, b in files])
        with archive.with_suffix('.json').open('x') as f:
            json.dump(artifact, f, indent=2, ensure_ascii=False)
        with Path(str(archive) + '.sha256').open('x') as f:
            f.write(artifact['sha256'] + '  ' + name + '\n')
        print(json.dumps(dict(archive=str(archive), bytes=artifact['bytes'], expandedBytes=artifact['expandedBytes'], sha256=artifact['sha256'])))
    else:
        if args.revision < 1:
            parser.error('--revision must be positive')
        artifacts = [json.loads(p.read_text()) for p in args.artifact]
        if len({(a['target'], a['id']) for a in artifacts}) != len(artifacts):
            parser.error('Duplicate component target')
        payload = json.dumps(dict(schema=1, revision=args.revision, artifacts=artifacts), separators=(',', ':'), ensure_ascii=False).encode()
        signature = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(args.key)], input=payload, capture_output=True, check=True).stdout
        envelope = dict(keyId='quiettv-components-1', payload=base64.b64encode(payload).decode(), signature=base64.b64encode(signature).decode())
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open('x') as f:
            json.dump(envelope, f, separators=(',', ':'))
        print(json.dumps(dict(manifest=str(args.output), revision=args.revision, artifacts=len(artifacts))))


if __name__ == '__main__':
    main()
