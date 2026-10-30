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
from install_tcl import manifest_home_mode

ROOT = Path(__file__).resolve().parents[1]
ABIS = ('universal', 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')
COMPATIBILITY = ('standard', 'legacy14', 'legacy19')
SYSTEM_LABELS = {
    14: ('安卓4.0-4.4', 'Android4.0-4.4'),
    19: ('安卓4.4', 'Android4.4'),
    21: ('安卓5.0及以上', 'Android5.0AndAbove'),
    23: ('安卓6.0及以上', 'Android6.0AndAbove'),
    26: ('安卓8.0及以上', 'Android8.0AndAbove'),
    31: ('安卓12.0及以上', 'Android12.0AndAbove'),
}


def apk_names(version, min_sdk, packaged_abis, home_mode='standard'):
    abi_names = {'armeabi-v7a': 'ARM32', 'arm64-v8a': 'ARM64', 'x86': 'x86', 'x86_64': 'x86_64'}
    architecture = abi_names[packaged_abis[0]] if len(packaged_abis) == 1 else ''
    suffix = '_' + architecture if architecture else ''
    statuses = ('实验版', 'Experimental') if home_mode == 'relay' else ('测试版', 'Test')
    return tuple(f'QuietTV_{version}_{system}{suffix}_{status}.apk'
                 for system, status in zip(SYSTEM_LABELS[min_sdk], statuses))


def source_digest():
    paths = [p for source in ('main', 'standard', 'legacy19', 'home', 'relay')
             for p in (ROOT / 'app/src' / source).rglob('*') if p.is_file()]
    paths += [p for p in (ROOT / 'home-proxy/src/contract/java').rglob('*') if p.is_file()]
    paths += [ROOT / name for name in ['app/build.gradle', 'app/proguard-rules.pro', 'build.gradle', 'settings.gradle',
              'gradle.properties', 'gradle/security-constraints.gradle', 'scripts/bundle_about.py', 'scripts/build_apk.py', 'scripts/install_tcl.py',
              'prototypes/about/index.html', 'prototypes/about/styles.css', 'prototypes/about/app.js',
              'prototypes/about/assets/wechat.png', 'prototypes/about/assets/qq-group.png',
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


def archive_folders(archive_dir=None):
    if archive_dir is not None:
        return [Path(archive_dir)]
    worktrees = subprocess.check_output(['git', 'worktree', 'list', '--porcelain'], cwd=ROOT, text=True)
    return [Path(line[9:]) / 'dist/apk' for line in worktrees.splitlines() if line.startswith('worktree ')]


def used_build_numbers(archive_dir=None):
    numbers = []
    for folder in archive_folders(archive_dir):
        if not folder.exists():
            continue
        for path in folder.rglob('*.apk'):
            match = re.match(r'(?:quiet-tv_[^_]+_b(\d+)_|QuietTV_\d+\.\d+\.\d+-(\d+)_)', path.name)
            if match:
                numbers.append(int(next(value for value in match.groups() if value is not None)))
            elif re.match(r'QuietTV_\d+\.\d+\.\d+_', path.name):
                receipt = path.with_suffix('.json')
                record = json.loads(receipt.read_text()) if receipt.is_file() else {}
                if record.get('application_id') != 'home.quiet.tv' or not isinstance(record.get('version_code'), int) or record['version_code'] < 1:
                    raise ValueError(f'无构建号文件名的归档缺少有效构建号元数据：{path}')
        for path in folder.rglob('*.json'):
            record = json.loads(path.read_text())
            if record.get('application_id') == 'home.quiet.tv' and 'version_code' in record:
                numbers.append(int(record['version_code']))
    return numbers


def check_delivery_version(actual, packaged_abis, status, sha256, archive_dir=None):
    # Historical names also occupy the same version/system/ABI/status slot.
    identity = {key: actual[key] for key in ('application_id', 'version_name', 'min_sdk')}
    for folder in archive_folders(archive_dir):
        for path in folder.rglob('*.json'):
            record = json.loads(path.read_text())
            previous_status = record.get('status', '实验版' if record.get('home_mode') == 'relay' else '测试版')
            if (all(record.get(key) == value for key, value in identity.items())
                    and record.get('packaged_abis') == packaged_abis and previous_status == status
                    and record.get('sha256') != sha256):
                raise FileExistsError(f'同版本、系统、架构和状态已有不同内容，请增加软件版本号：{path}')


def archive_apk(summary, built_at=None, output_dir=None, abi='universal', archive_dir=None, min_sdk=21, production_digest=None, compatibility='standard', home_mode='standard'):
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
    if compatibility not in COMPATIBILITY or (compatibility == 'legacy14' and min_sdk != 14) or (compatibility == 'legacy19' and min_sdk != 19) or (compatibility == 'standard' and min_sdk < 21):
        raise ValueError('兼容档位与 APK 最低系统不符')
    system = SYSTEM_LABELS[min_sdk][0]
    status = '实验版' if home_mode == 'relay' else '测试版'
    name, public_name = apk_names(actual['version_name'], min_sdk, packaged_abis, home_mode)
    destination = Path(archive_dir or ROOT / 'dist/apk') / name
    receipt = destination.with_suffix('.json')
    checksum = destination.with_name(destination.name + '.sha256')
    checksum_text = f'{sha256}  {name}\n'
    if destination.exists():
        previous = json.loads(receipt.read_text()) if receipt.is_file() else {}
        if (hashlib.sha256(destination.read_bytes()).hexdigest() == sha256
                and previous.get('sha256') == sha256 and previous.get('public_apk') == public_name
                and all(previous.get(key) == value for key, value in actual.items())
                and checksum.exists() and checksum.read_text() == checksum_text):
            print(f'复用已归档安装包：{destination}')
            return destination
        raise FileExistsError(f'归档名称已占用，请增加软件版本号：{destination}')
    if receipt.exists() or checksum.exists():
        raise FileExistsError(f'归档记录已存在，请核对原件；不同内容须增加软件版本号：{receipt}')
    check_delivery_version(actual, packaged_abis, status, sha256, archive_dir)
    used = used_build_numbers(archive_dir)
    if used and actual['version_code'] <= max(used):
        raise ValueError(f'新包构建号必须高于已归档的 {max(used)}')
    sdk = next(line[8:] for line in (ROOT / 'local.properties').read_text().splitlines() if line.startswith('sdk.dir='))
    aapt = sorted((Path(sdk) / 'build-tools').glob('*/aapt2'))[-1]
    manifest_tree = subprocess.check_output([str(aapt), 'dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml'], text=True)
    if manifest_home_mode(manifest_tree) != home_mode:
        raise ValueError('实际 APK 的 HOME 形态与构建参数不符，停止归档')
    record = {
        'apk': name, 'public_apk': public_name, 'application_id': metadata['applicationId'],
        'version_name': element['versionName'], 'version_code': element['versionCode'],
        'build_type': metadata['variantName'], 'status': status, 'system': system,
        'compatibility': compatibility, 'home_mode': home_mode, 'abi': abi, 'packaged_abis': packaged_abis, 'min_sdk': min_sdk, 'target_sdk': actual['target_sdk'],
        'signer_sha256': certs, 'built_at': built_at,
        'apk_modified_at': datetime.fromtimestamp(apk.stat().st_mtime).astimezone().isoformat(timespec='seconds'),
        'archived_at': datetime.now().astimezone().isoformat(timespec='seconds'),
        'bytes': len(data), 'sha256': sha256, 'summary': summary,
        'workspace_commit_at_archive': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'workspace_dirty_at_archive': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip()),
        'workspace_diff_sha256': hashlib.sha256(subprocess.check_output(['git', 'diff', 'HEAD', '--binary'], cwd=ROOT)).hexdigest(),
        'production_sources_sha256': production_digest,
        'build_parameters': {'quietBuildNumber': actual['version_code'], 'quietCompatibility': compatibility,
                             'quietMinSdk': min_sdk, 'quietAbi': abi, 'quietHomeMode': home_mode},
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
    print(f'安装包：{destination}\n公开名称：{public_name}\n字节数：{len(data)}\nSHA-256：{sha256}')
    return destination


def build_apk(summary, variant='debug', abi='universal', min_sdk=None, compatibility='standard', build_number=None, home_mode='standard'):
    if compatibility not in COMPATIBILITY:
        raise ValueError('不支持的兼容档位')
    if home_mode not in ('standard', 'relay') or (home_mode == 'relay' and compatibility != 'standard'):
        raise ValueError('relay 只适用于安卓5及以上标准代码档位')
    if min_sdk is None:
        min_sdk = 14 if compatibility == 'legacy14' else 19 if compatibility == 'legacy19' else 21
    allowed = (14,) if compatibility == 'legacy14' else (19,) if compatibility == 'legacy19' else (21,23,26,31)
    if variant not in ('debug', 'benchmark', 'release') or abi not in ABIS or min_sdk not in allowed:
        raise ValueError('不支持的构建类型或 ABI')
    if build_number is not None and (not isinstance(build_number, int) or build_number < 1):
        raise ValueError('构建号必须为正整数')
    before = source_digest()
    identity = f'b{build_number}' if build_number is not None else before[:12]
    build_root = ROOT / 'output/candidate-builds' / f'{compatibility}-{home_mode}-{variant}-{abi}-api{min_sdk}-{identity}'
    subprocess.run([str(ROOT / 'gradlew'), ':app:assemble' + variant.capitalize(), '--console=plain',
                    '-PquietBuildRoot=' + str(build_root), '-PquietAbi=' + abi, '-PquietMinSdk='+str(min_sdk),
                    '-PquietCompatibility=' + compatibility, '-PquietHomeMode=' + home_mode]
                   + (['-PquietBuildNumber=' + str(build_number)] if build_number is not None else []), cwd=ROOT, check=True)
    if source_digest() != before:
        raise RuntimeError('生产源码在构建期间改变，产物未归档，请重新构建')
    return archive_apk(summary, datetime.now().astimezone().isoformat(timespec='seconds'),
                       build_root / 'app/outputs/apk' / variant, abi, min_sdk=min_sdk, production_digest=before, compatibility=compatibility, home_mode=home_mode)


def verify_batch(apks):
    records = [json.loads(path.with_suffix('.json').read_text()) for path in apks]
    for field in ('application_id', 'version_name', 'signer_sha256',
                  'workspace_commit_at_archive', 'production_sources_sha256'):
        if any(record[field] != records[0][field] for record in records):
            raise ValueError(f'批次 {field} 不一致')
    expected = [('standard', 'armeabi-v7a'), ('standard', 'arm64-v8a'),
                ('standard', 'universal'), ('legacy14', 'universal')]
    if len(records) == 2:
        expected = [('standard', 'universal'), ('legacy14', 'universal')]
    if [(record['compatibility'], record['abi']) for record in records] != expected:
        raise ValueError('批次须为标准三架构加安卓4.x，或无原生库的标准通用加安卓4.x')
    numbers = [int(record['version_code']) for record in records]
    if numbers != list(range(numbers[0], numbers[0] + len(records))):
        raise ValueError('批次构建号必须依次递增')
    libraries = []
    for path in apks:
        with zipfile.ZipFile(path) as archive:
            libraries.append({name: hashlib.sha256(archive.read(name)).hexdigest()
                              for name in archive.namelist()
                              if name.startswith('lib/') and name.endswith('.so')})
    if len(records) == 2 and any(libraries):
        raise ValueError('两包模式仅适用于标准版和安卓4.x均无原生库')
    universal = libraries[2] if len(records) == 4 else {}
    # Pure-Java standard packages have no architecture-specific payload.
    if universal and {name.split('/')[1] for name in universal} != set(ABIS[1:]):
        raise ValueError('通用包必须包含全部四种原生库架构')
    names_by_abi = [{name.split('/', 2)[2] for name in universal if name.split('/')[1] == abi}
                    for abi in ABIS[1:]]
    if universal and (not names_by_abi[0] or any(names != names_by_abi[0] for names in names_by_abi)):
        raise ValueError('通用包各架构的原生库不完整')
    for index, abi in enumerate(('armeabi-v7a', 'arm64-v8a') if len(records) == 4 else ()):
        matching = {name: digest for name, digest in universal.items() if name.split('/')[1] == abi}
        if libraries[index] != matching:
            raise ValueError(f'{abi} 单架构包与通用包的原生库不一致')
    if libraries[-1]:
        raise ValueError('安卓4.x包不应包含原生库')
    return {'version_name': records[0]['version_name'],
            'source_commit': records[0]['workspace_commit_at_archive'],
            'production_sources_sha256': records[0]['production_sources_sha256'],
            'signer_sha256': records[0]['signer_sha256'],
            'artifacts': records, 'native_libraries_sha256': libraries,
            'validation_scope': '整批构建、版本、签名、架构及原生库一致性；功能、升级数据及真机验收另行记录'}


def build_batch(summary, first_build_number, variant='benchmark', universal_only=False):
    if not isinstance(first_build_number, int) or first_build_number < 1:
        raise ValueError('整批构建必须指定起始正整数构建号')
    used = used_build_numbers()
    if used and first_build_number <= max(used):
        raise ValueError(f'起始构建号必须高于已归档的 {max(used)}')
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip():
        raise ValueError('整批构建前须提交工作区改动，固定源码基线')
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    before = source_digest()
    apks = []
    plan = [('standard', 'universal'), ('legacy14', 'universal')] if universal_only else [
        ('standard', 'armeabi-v7a'), ('standard', 'arm64-v8a'),
        ('standard', 'universal'), ('legacy14', 'universal')]
    for offset, (profile, abi) in enumerate(plan):
        if (source_digest() != before or
                subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip() != commit):
            raise RuntimeError('批次源码基线改变，停止后续构建与批次归档')
        apks.append(build_apk(summary, variant, abi, compatibility=profile,
                              build_number=first_build_number + offset))
    if (source_digest() != before or
            subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip() != commit):
        raise RuntimeError('批次源码基线改变，不生成批次清单')
    record = verify_batch(apks)
    manifest = ROOT / 'dist/apk' / f"QuietTV_{record['version_name']}-{first_build_number}-{first_build_number + len(apks) - 1}_batch.json"
    with manifest.open('x', encoding='utf-8') as output:
        output.write(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    print(f'批次清单：{manifest}')
    return manifest


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
    parser.add_argument('--compatibility', choices=COMPATIBILITY, default='standard', help='standard: Android 5+; legacy14: Android 4.0-4.4; legacy19: historical API19 builds')
    parser.add_argument('--home-mode', choices=['standard', 'relay'], default='standard', help='relay: 不声明 HOME，复用现有桌面守护')
    parser.add_argument('--min-sdk', choices=[14,19,21,23,26,31], type=int, help='defaults to 14 for legacy14, 19 for legacy19, 21 for standard')
    parser.add_argument('--build-number', type=int, help='Positive delivery number; first number for --batch')
    parser.add_argument('--batch', action='store_true', help='Build standard ARM32, ARM64, universal and one legacy14 APK')
    parser.add_argument('--universal-only', action='store_true', help='With --batch, build standard universal + legacy14; both must have no native libraries')
    args = parser.parse_args()
    if not args.summary.strip():
        parser.error('--summary 不能为空')
    if args.universal_only and not args.batch:
        parser.error('--universal-only 必须配合 --batch')
    if args.batch:
        if args.abi != 'universal' or args.min_sdk is not None or args.compatibility != 'standard' or args.home_mode != 'standard':
            parser.error('--batch 固定各档位配置，不能同时指定 ABI、最低 API 或兼容档位')
        build_batch(args.summary.strip(), args.build_number, args.variant, args.universal_only)
    else:
        build_apk(args.summary.strip(), args.variant, args.abi, args.min_sdk, args.compatibility, args.build_number, args.home_mode)
