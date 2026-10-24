#!/usr/bin/env python3
"""Build and immutably archive signed candidates without contacting a TV."""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ABIS = ('universal', 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')
COMPATIBILITY = ('standard', 'legacy19')


def source_digest():
    paths = [p for source in ('main', 'standard', 'legacy19')
             for p in (ROOT / 'app/src' / source).rglob('*') if p.is_file()]
    paths += [ROOT / name for name in ['app/build.gradle', 'app/proguard-rules.pro', 'build.gradle', 'settings.gradle',
              'gradle.properties', 'gradle/security-constraints.gradle', 'scripts/bundle_about.py', 'scripts/build_apk.py',
              'prototypes/about/index.html', 'prototypes/about/styles.css', 'prototypes/about/app.js',
              'prototypes/about/assets/source.svg', 'prototypes/about/assets/wechat.webp', 'prototypes/about/assets/qq-group.webp',
              'LICENSE', 'THIRD_PARTY_NOTICES.md']]
    digest = hashlib.sha256()
    for path in sorted(set(paths)):
        digest.update(str(path.relative_to(ROOT)).encode() + b'\0' + path.read_bytes() + b'\0')
    return digest.hexdigest()


def signer_digest(apk, min_sdk=21):
    sdk = next(line[8:] for line in (ROOT / 'local.properties').read_text().splitlines() if line.startswith('sdk.dir='))
    signer = sorted((Path(sdk) / 'build-tools').glob('*/apksigner'))[-1]
    result = subprocess.check_output([str(signer), 'verify', '--verbose', '--min-sdk-version', str(min_sdk), '--print-certs', str(apk)], text=True)
    if min_sdk < 24 and 'Verified using v1 scheme (JAR signing): true' not in result:
        raise ValueError('旧系统安装包必须通过 v1 签名验证')
    return re.findall(r'Signer #\d+ certificate SHA-256 digest: (\w+)', result)


def manifest_min_sdk(apk):
    return manifest_info(apk)['min_sdk']


def manifest_info(apk):
    sdk = next(line[8:] for line in (ROOT / 'local.properties').read_text().splitlines() if line.startswith('sdk.dir='))
    aapt = sorted((Path(sdk) / 'build-tools').glob('*/aapt2'))[-1]
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    found = re.search(r"^(?:minSdkVersion|sdkVersion):'(\d+)'", badging, re.M)
    if not found:
        raise ValueError('aapt2 未返回 APK 最低系统版本')
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    target = re.search(r"^targetSdkVersion:'(\d+)'", badging, re.M)
    if not package or not target:
        raise ValueError('aapt2 未返回完整 APK 版本与目标系统')
    return {'application_id': package[1], 'version_code': int(package[2]), 'version_name': package[3],
            'min_sdk': int(found[1]), 'target_sdk': int(target[1])}


def used_build_numbers(archive_dir=None):
    if archive_dir is not None:
        folders = [Path(archive_dir)]
    else:
        worktrees = subprocess.check_output(['git', 'worktree', 'list', '--porcelain'], cwd=ROOT, text=True)
        folders = [Path(line[9:]) / 'dist/apk' for line in worktrees.splitlines() if line.startswith('worktree ')]
    numbers = []
    for folder in folders:
        if not folder.exists():
            continue
        for path in folder.rglob('*.apk'):
            match = re.match(r'(?:quiet-tv_[^_]+_b(\d+)_|QuietTV_\d+\.\d+\.\d+-(\d+)_)', path.name)
            if match:
                numbers.append(int(next(value for value in match.groups() if value is not None)))
        for path in folder.rglob('*.json'):
            record = json.loads(path.read_text())
            if record.get('application_id') == 'home.quiet.tv' and 'version_code' in record:
                numbers.append(int(record['version_code']))
    return numbers


def archive_apk(summary, built_at=None, output_dir=None, abi='universal', archive_dir=None, min_sdk=21, production_digest=None, compatibility='standard'):
    output_dir = Path(output_dir or ROOT / 'app/build/outputs/apk/debug')
    metadata = json.loads((output_dir / 'output-metadata.json').read_text())
    # Each ABI candidate is a standalone APK; install/verify still use one package path.
    element, = metadata['elements']
    apk = output_dir / element['outputFile']
    data = apk.read_bytes()
    sha256 = hashlib.sha256(data).hexdigest()
    with zipfile.ZipFile(apk) as archive:
        packaged_abis = sorted({p.split('/')[1] for p in archive.namelist() if p.startswith('lib/') and p.endswith('.so')})
    if abi != 'universal' and packaged_abis and packaged_abis != [abi]:
        raise ValueError(f'APK ABI 与请求不符：{packaged_abis} != {abi}')
    certs = signer_digest(apk, min_sdk)
    if len(certs) != 1:
        raise ValueError('必须使用单一可验证签名，未签名 release 不得归档')
    actual = manifest_info(apk)
    if actual['min_sdk'] != min_sdk:
        raise ValueError('APK 最低系统版本与请求不符')
    if (actual['application_id'] != metadata['applicationId'] or actual['version_code'] != int(element['versionCode'])
            or actual['version_name'] != element['versionName']):
        raise ValueError('APK 实际版本或包名与构建元数据不符')
    if actual['application_id'] != 'home.quiet.tv' or not re.fullmatch(r'\d+\.\d+\.\d+', actual['version_name']) or actual['version_code'] < 1:
        raise ValueError('归档仅支持三段版本号及正整数构建号的 Quiet TV 主程序')
    if compatibility not in COMPATIBILITY or (compatibility == 'legacy19' and min_sdk != 19) or (compatibility == 'standard' and min_sdk < 21):
        raise ValueError('兼容档位与 APK 最低系统不符')
    system = {19: '安卓4.4', 21: '安卓5及以上', 23: '安卓6及以上', 26: '安卓8及以上', 31: '安卓12及以上'}[min_sdk]
    abi_names = {'armeabi-v7a': 'ARM32', 'arm64-v8a': 'ARM64', 'x86': 'x86', 'x86_64': 'x86_64'}
    suffix = '_' + abi_names[packaged_abis[0]] if len(packaged_abis) == 1 else ''
    name = f"QuietTV_{actual['version_name']}-{actual['version_code']}_{system}{suffix}_测试版.apk"
    destination = Path(archive_dir or ROOT / 'dist/apk') / name
    receipt = destination.with_suffix('.json')
    checksum = destination.with_name(destination.name + '.sha256')
    checksum_text = f'{sha256}  {name}\n'
    if destination.exists():
        if (hashlib.sha256(destination.read_bytes()).hexdigest() == sha256 and receipt.exists()
                and json.loads(receipt.read_text())['sha256'] == sha256
                and checksum.exists() and checksum.read_text() == checksum_text):
            print(f'复用已归档安装包：{destination}')
            return destination
        raise FileExistsError(f'归档名称已占用，请使用新的递增构建号：{destination}')
    if receipt.exists() or checksum.exists():
        raise FileExistsError(f'归档记录已存在，请使用新的递增构建号：{receipt}')
    used = used_build_numbers(archive_dir)
    if used and actual['version_code'] <= max(used):
        raise ValueError(f'新包构建号必须高于已归档的 {max(used)}')
    record = {
        'apk': name, 'application_id': metadata['applicationId'],
        'version_name': element['versionName'], 'version_code': element['versionCode'],
        'build_type': metadata['variantName'], 'status': '测试版', 'system': system,
        'compatibility': compatibility, 'abi': abi, 'packaged_abis': packaged_abis, 'min_sdk': min_sdk, 'target_sdk': actual['target_sdk'],
        'signer_sha256': certs, 'built_at': built_at,
        'apk_modified_at': datetime.fromtimestamp(apk.stat().st_mtime).astimezone().isoformat(timespec='seconds'),
        'archived_at': datetime.now().astimezone().isoformat(timespec='seconds'),
        'bytes': len(data), 'sha256': sha256, 'summary': summary,
        'workspace_commit_at_archive': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'workspace_dirty_at_archive': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip()),
        'workspace_diff_sha256': hashlib.sha256(subprocess.check_output(['git', 'diff', 'HEAD', '--binary'], cwd=ROOT)).hexdigest(),
        'production_sources_sha256': production_digest,
        'build_parameters': {'quietBuildNumber': actual['version_code'], 'quietCompatibility': compatibility,
                             'quietMinSdk': min_sdk, 'quietAbi': abi},
        'validation_scope': '构建、APK 元数据与签名校验；安装、功能、升级数据及真机验收另行记录',
    }
    mapping = output_dir.parents[1] / 'mapping' / metadata['variantName'] / 'mapping.txt'
    destination.parent.mkdir(parents=True, exist_ok=True)
    if mapping.is_file():
        record['mapping_sha256'] = hashlib.sha256(mapping.read_bytes()).hexdigest()
        with destination.with_suffix('.mapping.txt').open('xb') as out:
            out.write(mapping.read_bytes())
    with destination.open('xb') as out:
        out.write(data)
    with receipt.open('x', encoding='utf-8') as out:
        out.write(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    with checksum.open('x', encoding='utf-8') as out:
        out.write(checksum_text)
    print(f'安装包：{destination}\n字节数：{len(data)}\nSHA-256：{sha256}')
    return destination


def build_apk(summary, variant='debug', abi='universal', min_sdk=None, compatibility='standard', build_number=None):
    if compatibility not in COMPATIBILITY:
        raise ValueError('不支持的兼容档位')
    if min_sdk is None:
        min_sdk = 19 if compatibility == 'legacy19' else 21
    allowed = (19,) if compatibility == 'legacy19' else (21,23,26,31)
    if variant not in ('debug', 'benchmark', 'release') or abi not in ABIS or min_sdk not in allowed:
        raise ValueError('不支持的构建类型或 ABI')
    if build_number is not None and (not isinstance(build_number, int) or build_number < 1):
        raise ValueError('构建号必须为正整数')
    build_root = ROOT / 'output/candidate-builds' / f'{compatibility}-{variant}-{abi}-api{min_sdk}'
    before = source_digest()
    subprocess.run([str(ROOT / 'gradlew'), ':app:assemble' + variant.capitalize(), '--console=plain',
                    '-PquietBuildRoot=' + str(build_root), '-PquietAbi=' + abi, '-PquietMinSdk='+str(min_sdk),
                    '-PquietCompatibility=' + compatibility]
                   + (['-PquietBuildNumber=' + str(build_number)] if build_number is not None else []), cwd=ROOT, check=True)
    if source_digest() != before:
        raise RuntimeError('生产源码在构建期间改变，产物未归档，请重新构建')
    return archive_apk(summary, datetime.now().astimezone().isoformat(timespec='seconds'),
                       build_root / 'app/outputs/apk' / variant, abi, min_sdk=min_sdk, production_digest=before, compatibility=compatibility)


def parse_summary(description=__doc__):
    # The existing build-and-install entry retains its debug-only one-APK contract.
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument('--summary', required=True)
    args = parser.parse_args()
    if not args.summary.strip():
        parser.error('--summary 不能为空')
    return args.summary.strip()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--summary', required=True)
    parser.add_argument('--variant', choices=['debug', 'benchmark', 'release'], default='debug')
    parser.add_argument('--abi', choices=ABIS, default='universal')
    parser.add_argument('--compatibility', choices=COMPATIBILITY, default='standard', help='standard: Android 5+; legacy19: Android 4.4 TCP-only')
    parser.add_argument('--min-sdk', choices=[19,21,23,26,31], type=int, help='defaults to 19 for legacy19 and 21 for standard')
    parser.add_argument('--build-number', type=int, help='Positive, increasing main-app delivery build number')
    args = parser.parse_args()
    if not args.summary.strip():
        parser.error('--summary 不能为空')
    build_apk(args.summary.strip(), args.variant, args.abi, args.min_sdk, args.compatibility, args.build_number)
