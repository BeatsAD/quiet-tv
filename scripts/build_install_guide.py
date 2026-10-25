#!/usr/bin/env python3
"""Build the installation site and its release index from public GitHub releases."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import time
from urllib.parse import urljoin
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = 'https://github.com/BeatsAD/quiet-tv'
API = 'https://api.github.com/repos/BeatsAD/quiet-tv/releases'
SITE = 'https://beatsad.github.io/quiet-tv/'


def fetch_releases():
    releases = []
    page = 1
    while True:
        headers = {'Accept': 'application/vnd.github+json', 'Cache-Control': 'no-cache',
                   'User-Agent': 'QuietTV-Installation-Index'}
        if os.environ.get('GITHUB_TOKEN'):
            headers['Authorization'] = 'Bearer ' + os.environ['GITHUB_TOKEN']
        request = Request(f'{API}?per_page=100&page={page}&check={int(time.time())}', headers=headers)
        with urlopen(request, timeout=30) as response:
            batch = json.load(response)
        if not isinstance(batch, list):
            raise ValueError('GitHub 发布列表不是数组')
        releases.extend(batch)
        if len(batch) < 100:
            return releases
        page += 1


def release_index(releases):
    records = []
    for release in releases:
        # Older published tags carry a build suffix; keep their real tag and URLs.
        match = re.fullmatch(r'v?((?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))(?:-b[1-9][0-9]*)?', release['tag_name'])
        if release['draft'] or not match:
            continue
        assets = []
        for asset in release['assets']:
            if not asset['name'].lower().startswith(('quiettv_', 'quiet-tv_')) or not asset['name'].lower().endswith('.apk'):
                continue
            if asset['state'] != 'uploaded' or asset['size'] <= 0:
                raise ValueError('公开 APK 附件尚未上传完成：' + asset['name'])
            if not asset['browser_download_url'].startswith(REPOSITORY + '/releases/download/'):
                raise ValueError('APK 下载地址不属于官方仓库')
            digest = asset.get('digest') or ''
            if digest and not re.fullmatch(r'sha256:[a-f0-9]{64}', digest):
                raise ValueError('APK 附件 SHA-256 格式无效')
            assets.append({'name': asset['name'], 'label': asset.get('label'),
                           'bytes': asset['size'], 'sha256': digest[7:] if digest else None,
                           'url': asset['browser_download_url']})
        if not assets:
            continue
        records.append({'versionName': match[1], 'tag': release['tag_name'],
                        'prerelease': release['prerelease'], 'publishedAt': release['published_at'],
                        'url': release['html_url'], 'assets': assets})
    records.sort(key=lambda item: (tuple(map(int, item['versionName'].split('.'))), item['publishedAt']), reverse=True)
    return {'schemaVersion': 1, 'checkedAt': datetime.now(timezone.utc).isoformat(),
            'source': API, 'latestVersion': records[0]['versionName'] if records else None, 'releases': records}


def build_site(output, releases):
    index = release_index(releases)
    source = ROOT / 'prototypes/install-guide'
    output.mkdir(parents=True, exist_ok=True)
    for name in ('styles.css', 'app.js', 'assets/icon.svg', 'assets/home.jpg'):
        (output / name).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source / name, output / name)
    html = (source / 'index.html').read_text()
    digest = hashlib.sha256((source / 'app.js').read_bytes()).hexdigest()[:12]
    html = re.sub(r'app\.js(?:\?v=[a-z0-9]+)?', 'app.js?v=' + digest, html)
    (output / 'index.html').write_text(html)
    shutil.copyfile(ROOT / 'updates/updates.json', output / 'updates.json')
    (output / 'latest.json').write_text(json.dumps(index, ensure_ascii=False, indent=2) + '\n')
    guide = (ROOT / 'docs/AGENT_INSTALL.zh-CN.md').read_text()
    # The same complete operating rules serve Raw and Pages; resolve relative links.
    guide = re.sub(r'\]\(([^\s)]+)\)', lambda match: '](' + urljoin(REPOSITORY + '/blob/main/docs/', match[1]) + ')', guide)
    summary = ['# Quiet TV 最新发布', '', '核对时间：' + index['checkedAt'], '']
    if index['releases']:
        latest = index['releases'][0]
        summary.extend([f"最新主程序版本：{latest['versionName']}  标签：{latest['tag']}",
                        '', '发布页：' + latest['url'], '', '完整适配候选与 SHA-256：' + SITE + 'latest.json',
                        '', '以下附件名称和地址由已公开 Release 自动生成，按电视系统、架构与覆盖升级条件选一个包：', ''])
        for asset in latest['assets']:
            summary.extend([asset['name'], asset['url'], 'SHA-256：' + (asset['sha256'] or '未提供，须另行核对'), ''])
    else:
        summary.extend(['当前没有已公开且含主程序 APK 的发布，请停止安装，不使用历史缓存。', ''])
    (output / 'agent.md').write_text('\n'.join(summary) + '\n---\n\n' + guide)
    return index


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--releases', type=Path, help='本地 GitHub 发布 JSON，用于离线检查；省略时实时读取官方 API')
    args = parser.parse_args()
    releases = json.loads(args.releases.read_text()) if args.releases else fetch_releases()
    index = build_site(args.output, releases)
    print(f"已生成安装站点：{args.output}  最新主程序：{index['latestVersion']}  适配发布：{len(index['releases'])}")


if __name__ == '__main__':
    main()
