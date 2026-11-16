#!/usr/bin/env python3
"""Check the packaged icon artwork contract without third-party dependencies."""
import argparse
import json
from pathlib import Path
import re
import struct
import zlib
from build_icon_library import validate as validate_online

MAX_SOURCE_BYTES = 512 * 1024


def check_png(path, foreground):
    if not path.is_file() or not 33 <= path.stat().st_size <= MAX_SOURCE_BYTES:
        raise ValueError(f'{path}: missing PNG or source exceeds 512 KiB')
    data = path.read_bytes()
    if data[:8] != b'\x89PNG\r\n\x1a\n' or data[8:16] != b'\0\0\0\rIHDR':
        raise ValueError(f'{path}: PNG signature or IHDR is invalid')
    width, height, depth, color, compression, filtering, interlace = struct.unpack('>IIBBBBB', data[16:29])
    expected = 512 if foreground else 720
    if (width, height) != (expected, expected):
        raise ValueError(f'{path}: expected {expected}x{expected} px, got {width}x{height}')
    if depth != 8 or color not in ((6,) if foreground else (2, 6)) or (compression, filtering, interlace) != (0, 0, 0):
        raise ValueError(f'{path}: expected non-interlaced 8-bit {"RGBA" if foreground else "RGB/RGBA"} PNG')
    position, pixels = 8, []
    while position + 12 <= len(data):
        length, kind = struct.unpack('>I4s', data[position:position + 8])
        end = position + 12 + length
        if end > len(data):
            break
        payload = data[position + 8:end - 4]
        if zlib.crc32(kind + payload) & 0xffffffff != struct.unpack('>I', data[end - 4:end])[0]:
            raise ValueError(f'{path}: PNG checksum is invalid')
        if kind == b'acTL':
            raise ValueError(f'{path}: animated PNG is not supported')
        if kind == b'IDAT':
            pixels.append(payload)
        if kind == b'IEND':
            if length == 0 and pixels and end == len(data):
                stride = 1 + width * (4 if color == 6 else 3)
                decoder = zlib.decompressobj()
                decoded = decoder.decompress(b''.join(pixels), stride * height + 1)
                if len(decoded) != stride * height or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail or any(decoded[row] > 4 for row in range(0, len(decoded), stride)):
                    raise ValueError(f'{path}: PNG pixel data is invalid')
                return
            break
        position = end
    raise ValueError(f'{path}: PNG chunks are incomplete')


def validate(assets):
    catalog_path = assets / 'icon-library/catalog.json'
    if catalog_path.stat().st_size > 256 * 1024:
        raise ValueError('Icon catalog exceeds 256 KiB')
    catalog = json.loads(catalog_path.read_text(encoding='utf-8'))
    checked = set()
    for item in catalog['items']:
        for field in ('foreground', 'background'):
            name = item.get(field, '')
            if field == 'background' and not name:
                continue
            if not isinstance(name, str) or not re.fullmatch(r'icon-library/[a-z0-9-]+(?:/[a-z0-9-]+)*\.png', name):
                raise ValueError(f'Invalid {field} path: {name!r}')
            key = (name, field)
            if key not in checked:
                check_png(assets / name, field == 'foreground')
                checked.add(key)
    if (assets / 'icon-library/online.json').exists():
        online_count, descriptors = validate_online(assets / 'icon-library', 'online.json', artwork=False)
        print(f'Signed online icon metadata matches: {online_count} icons, {descriptors} artwork descriptors; images remain online.')

    rules_path = assets / 'icon-library/matches.json'
    if rules_path.stat().st_size > 256 * 1024:
        raise ValueError('Icon matching rules exceed 256 KiB')
    rules = json.loads(rules_path.read_text(encoding='utf-8'))
    if rules.get('version') != 1 or type(rules.get('revision')) is not int or rules['revision'] < 1:
        raise ValueError('Invalid icon matching rules revision')
    seen = set()
    for row in rules['items']:
        identity = row['id']
        if not re.fullmatch(r'[a-z][a-z0-9-]{0,47}', identity) or identity in seen or type(row['autoMatch']) is not bool:
            raise ValueError(f'Invalid matching identity: {identity!r}')
        seen.add(identity)
        if not isinstance(row['aliases'], list) or not row['aliases'] or any(not isinstance(name, str) or not name.strip() or len(name) > 100 for name in row['aliases']):
            raise ValueError(f'Invalid aliases: {identity}')
        if not isinstance(row['packages'], list) or any(not isinstance(pkg, str) or not re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+', pkg) for pkg in row['packages']):
            raise ValueError(f'Invalid package mapping: {identity}')
    return len(checked)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets', type=Path, default=Path(__file__).resolve().parents[1] / 'app/src/main/assets')
    args = parser.parse_args()
    try:
        count = validate(args.assets)
    except (OSError, ValueError, KeyError, TypeError, zlib.error) as error:
        raise SystemExit(f'Icon library validation failed: {error}') from error
    print(f'Icon library source dimensions, format and size match: {count} referenced files.')


if __name__ == '__main__':
    main()
