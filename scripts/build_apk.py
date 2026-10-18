#!/usr/bin/env python3
"""Build and archive a debug APK without connecting to a TV."""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DEBUG_OUTPUT = ROOT / 'app/build/outputs/apk/debug'


def archive_apk(summary, built_at=None):
    metadata = json.loads((DEBUG_OUTPUT / 'output-metadata.json').read_text())
    element, = metadata['elements']
    apk = DEBUG_OUTPUT / element['outputFile']
    data = apk.read_bytes()
    sha256 = hashlib.sha256(data).hexdigest()
    name = f"quiet-tv_{element['versionName']}_b{element['versionCode']}_{metadata['variantName']}.apk"
    destination = ROOT / 'dist/apk' / name
    receipt = destination.with_suffix('.json')
    if destination.exists():
        if (hashlib.sha256(destination.read_bytes()).hexdigest() == sha256
                and receipt.exists()
                and json.loads(receipt.read_text())['sha256'] == sha256):
            print(f'复用已归档安装包：{destination}')
            return destination
        raise FileExistsError(f'归档名称已占用，请递增 versionCode 后重新构建：{destination}')
    if receipt.exists():
        raise FileExistsError(f'归档记录已存在，请递增 versionCode：{receipt}')
    record = {
        'apk': name,
        'application_id': metadata['applicationId'],
        'version_name': element['versionName'],
        'version_code': element['versionCode'],
        'build_type': metadata['variantName'],
        'built_at': built_at,
        'apk_modified_at': datetime.fromtimestamp(apk.stat().st_mtime).astimezone().isoformat(timespec='seconds'),
        'archived_at': datetime.now().astimezone().isoformat(timespec='seconds'),
        'bytes': len(data),
        'sha256': sha256,
        'summary': summary,
        'workspace_commit_at_archive': subprocess.check_output(
            ['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'workspace_dirty_at_archive': bool(subprocess.check_output(
            ['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip()),
    }
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open('xb') as output:
        output.write(data)
    with receipt.open('x', encoding='utf-8') as output:
        output.write(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    print(f'安装包：{destination}\nSHA-256：{sha256}\n归档记录：{receipt}')
    return destination


def build_apk(summary):
    subprocess.run([str(ROOT / 'gradlew'), ':app:assembleDebug', '--console=plain'],
                   cwd=ROOT, check=True)
    return archive_apk(summary, datetime.now().astimezone().isoformat(timespec='seconds'))


def parse_summary(description=__doc__):
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument('--summary', required=True, help='本次交付的变更摘要')
    args = parser.parse_args()
    if not args.summary.strip():
        parser.error('--summary 不能为空')
    return args.summary.strip()


if __name__ == '__main__':
    build_apk(parse_summary())
