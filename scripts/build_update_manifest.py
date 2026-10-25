#!/usr/bin/env python3
"""Generate an offline-signed update feed from verified local APK archives; never upload."""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile
import build_apk

ROOT = Path(__file__).resolve().parents[1]
PUBLIC_KEY = ROOT / 'app/src/main/assets/updates/public-key.der'
REPOSITORY = 'https://github.com/BeatsAD/quiet-tv/'


def artifact(receipt, url, channel, version):
    record = json.loads(receipt.read_text())
    apk = receipt.with_name(record['apk'])
    if apk.stat().st_size != record['bytes'] or hashlib.sha256(apk.read_bytes()).hexdigest() != record['sha256']:
        raise ValueError('归档 APK 字节与记录不一致')
    actual = build_apk.manifest_info(apk)
    for key, field in [('application_id', 'application_id'), ('version_name', 'version_name'),
                       ('version_code', 'version_code'), ('min_sdk', 'min_sdk')]:
        if str(actual[key]) != str(record[field]):
            raise ValueError('归档元数据与 APK 不一致：' + key)
    if actual['version_name'] != version:
        raise ValueError('一个版本记录中的 APK 必须属于同一软件版本')
    certs = build_apk.signer_digest(apk, actual['min_sdk'])
    if certs != record['signer_sha256']:
        raise ValueError('归档签名不一致')
    if channel == 'stable' and record['build_type'] != 'release':
        raise ValueError('正式渠道只接受 release 归档，正式验收仍需另行确认')
    if channel == 'test' and record['build_type'] not in ('debug', 'benchmark'):
        raise ValueError('测试渠道构建类型不一致')
    if not url.startswith(REPOSITORY + 'releases/download/') or not url.endswith('.apk') or '?' in url or '#' in url or '/../' in url:
        raise ValueError('下载地址必须是本仓库已审核的固定 Release APK 地址')
    with zipfile.ZipFile(apk) as archive:
        abis = sorted({name.split('/')[1] for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')})
    if abis != record['packaged_abis']:
        raise ValueError('归档架构不一致')
    sdk = next(line[8:] for line in (ROOT / 'local.properties').read_text().splitlines() if line.startswith('sdk.dir='))
    aapt = sorted((Path(sdk) / 'build-tools').glob('*/aapt2'))[-1]
    tree = subprocess.check_output([str(aapt), 'dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml'], text=True)
    home_mode = 'standard' if 'android.intent.category.HOME' in tree else 'relay'
    if record.get('home_mode', home_mode) != home_mode:
        raise ValueError('归档 HOME 形态不一致')
    return {
        'artifactId': f"{actual['application_id']}:{version}:{actual['version_code']}",
        'batchId': record['workspace_commit_at_archive'],
        'packageName': actual['application_id'], 'versionCode': actual['version_code'],
        'compatibility': record['compatibility'], 'homeMode': home_mode,
        'minSdk': actual['min_sdk'], 'abis': abis, 'signerSha256': certs,
        'bytes': record['bytes'], 'sha256': record['sha256'], 'url': url,
    }


def generate(releases, revision, private_key, public_key=PUBLIC_KEY):
    if revision < 1:
        raise ValueError('revision 必须为递增正整数')
    actual_key = subprocess.check_output(['openssl', 'pkey', '-in', str(private_key), '-pubout', '-outform', 'DER'])
    if actual_key != public_key.read_bytes():
        raise ValueError('签名私钥与 APK 中公开密钥不匹配')
    records = []
    for release_path in releases:
        spec = json.loads(release_path.read_text())
        version, channel = spec['versionName'], spec['channel']
        if not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', version):
            raise ValueError('版本必须为三段数字')
        if channel not in ('test', 'stable', 'experimental'):
            raise ValueError('未知发布渠道')
        for field in ('features', 'fixes'):
            if not isinstance(spec[field], list) or any(not isinstance(v, str) or not v.strip() or len(v) > 2000 for v in spec[field]):
                raise ValueError('更新内容必须为非空纯文本条目列表')
        entries = [artifact((release_path.parent / value['receipt']).resolve(), value['url'], channel, version)
                   for value in spec['artifacts']]
        if not entries or len({value['batchId'] for value in entries}) != 1:
            raise ValueError('一个版本必须包含同一源码提交的真实归档')
        records.append({'versionName': version, 'channel': channel, 'features': spec['features'], 'fixes': spec['fixes'], 'artifacts': entries})
    payload = json.dumps({'schemaVersion': 1, 'revision': revision,
                          'publishedAt': datetime.now(timezone.utc).isoformat(), 'releases': records},
                         ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    signature = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(private_key)], input=payload, stdout=subprocess.PIPE, check=True).stdout
    result = json.dumps({'keyId': 'quiettv-update-1', 'payload': base64.b64encode(payload).decode(),
                         'signature': base64.b64encode(signature).decode()}, indent=2) + '\n'
    if len(result.encode()) > 256 * 1024:
        raise ValueError('更新清单超过应用大小上限')
    return result


def verify_feed(path, public_key=PUBLIC_KEY):
    data = path.read_bytes()
    if len(data) > 256 * 1024:
        raise ValueError('更新清单超过应用大小上限')
    envelope = json.loads(data)
    if envelope['keyId'] != 'quiettv-update-1':
        raise ValueError('未知签名密钥')
    payload = base64.b64decode(envelope['payload'], validate=True)
    signature = base64.b64decode(envelope['signature'], validate=True)
    # One explicitly scoped temporary signature file; payload goes through stdin.
    with tempfile.NamedTemporaryFile(prefix='quiet-update-signature-') as temporary:
        temporary.write(signature)
        temporary.flush()
        subprocess.run(['openssl', 'dgst', '-sha256', '-verify', str(public_key),
                        '-keyform', 'DER', '-signature', temporary.name], input=payload,
                       stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=True)
    manifest = json.loads(payload)
    if manifest['schemaVersion'] != 1 or not isinstance(manifest['revision'], int) or manifest['revision'] < 1 or not isinstance(manifest['releases'], list):
        raise ValueError('更新清单结构无效')
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify', type=Path, help='只用公开密钥核对清单，不需要私钥或 SDK')
    parser.add_argument('--release', type=Path, action='append', default=[], help='本地版本说明及归档映射，可多次指定')
    parser.add_argument('--empty', action='store_true', help='生成无版本的撤回/初始化清单')
    parser.add_argument('--revision', type=int)
    parser.add_argument('--key', type=Path, help='仓库外的持久私钥')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.verify:
        if args.release or args.empty or args.revision or args.key or args.output:
            parser.error('--verify 不能与生成参数一起使用')
        manifest = verify_feed(args.verify)
        print('清单签名验证通过，revision=' + str(manifest['revision']))
        return
    if not args.key or not args.output or args.revision is None:
        parser.error('生成清单需要 --key、--revision 和 --output')
    if bool(args.release) == args.empty:
        parser.error('指定 --release 或 --empty，不能同时指定')
    if args.output.exists():
        previous = json.loads(base64.b64decode(json.loads(args.output.read_text())['payload']))
        if args.revision <= previous['revision']:
            parser.error('新清单 revision 必须高于目标文件现有 revision')
    text = generate(args.release, args.revision, args.key)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(text)
    print('已生成本地签名清单，未执行上传或发布：' + str(args.output))


if __name__ == '__main__':
    main()
