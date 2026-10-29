#!/usr/bin/env python3
"""Download verified public files through mirrors; fetch version indexes from origin."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import sys
import tempfile
import time
from urllib.parse import urlsplit, urlunsplit
from urllib.request import Request, urlopen

INDEX = 'https://beatsad.github.io/quiet-tv/latest.json'
MIRRORS = ('https://ghfast.top/', 'https://gh-proxy.com/')
UNAVAILABLE = set()  # Only this process; do not retry a timed-out host for every bundle file.


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


def public_url(url):
    parsed = urlsplit(url)
    return parsed.scheme == 'https' and not parsed.username and not parsed.password and not parsed.query and not parsed.fragment


def candidates(url, sha256, github_url=None):
    if not public_url(url):
        return [url]
    mirror_target = github_url or url
    if (re.fullmatch(r'[a-f0-9]{64}', sha256 or '') and public_url(mirror_target)
            and urlsplit(mirror_target).hostname in ('github.com', 'raw.githubusercontent.com')):
        return [root + mirror_target for root in MIRRORS if root not in UNAVAILABLE] + [url]
    return [url]


def announce(event, name, **values):
    if event:
        event(name, **values)
    else:
        print(json.dumps(dict(event=name, **values), ensure_ascii=False), file=sys.stderr, flush=True)


def download(url, destination, sha256, size=None, github_url=None, event=None):
    destination = Path(destination)
    parsed = urlsplit(url)
    if parsed.scheme != 'https' or parsed.username or parsed.password or not re.fullmatch(r'[a-f0-9]{64}', sha256 or ''):
        raise ValueError('公开资源须有 HTTPS 原地址和可信 SHA-256')
    if destination.exists():
        if digest(destination) == sha256 and (size is None or destination.stat().st_size == size):
            announce(event, 'resource_reused', file=str(destination))
            return destination
        raise ValueError('已有文件与本次校验不符，保留原件，请使用新目录：' + str(destination))
    for source in candidates(url, sha256, github_url):
        # Attempt files are retained on failure; never remove a user's partial download.
        fd, temporary = tempfile.mkstemp(prefix=destination.name + '.', suffix='.part', dir=destination.parent)
        path = Path(temporary)
        parsed = urlsplit(source)
        display = urlunsplit((parsed.scheme, parsed.netloc, parsed.path, '', ''))
        announce(event, 'resource_attempt', source=display)
        try:
            with os.fdopen(fd, 'wb') as stream:
                with urlopen(Request(source, headers={'Cache-Control': 'no-cache', 'User-Agent': 'QuietTV-Computer'}),
                             timeout=30 if source == url else 8) as response:
                    for chunk in iter(lambda: response.read(1024 * 1024), b''):
                        stream.write(chunk)
            if (size is not None and path.stat().st_size != size) or digest(path) != sha256:
                raise ValueError('文件大小或 SHA-256 不符')
            os.replace(path, destination)
            announce(event, 'resource_verified', source=display, file=str(destination), sha256=sha256)
            return destination
        except (OSError, ValueError) as error:
            if isinstance(error, OSError) and getattr(error, 'code', None) != 404:
                UNAVAILABLE.update(root for root in MIRRORS if source.startswith(root))
            announce(event, 'resource_failed', source=display, reason=str(getattr(error, 'reason', error)))
    raise ValueError('适用镜像及原站均未取得校验通过的文件；已保留本次下载记录')


def read_json(url):
    if urlsplit(url).scheme != 'https':
        raise ValueError('版本索引须使用 HTTPS 原站')
    with urlopen(Request(url, headers={'Cache-Control': 'no-cache', 'User-Agent': 'QuietTV-Computer'}), timeout=30) as response:
        return json.load(response)


def bundle(directory, index_url=INDEX):
    """Bootstrap without importing the other tools, which may not exist yet."""
    directory = Path(directory)
    directory.mkdir(mode=0o700, parents=False, exist_ok=False)
    index = read_json(index_url + ('&' if '?' in index_url else '?') + 'check=' + str(time.time_ns()))
    if (not isinstance(index, dict) or index.get('schemaVersion') != 1 or not isinstance(index.get('tools'), dict) or
            not isinstance(index.get('guides'), dict) or not index['guides'] or
            not {'agent_session.py', 'computer_resources.py', 'device_environment.py'}.issubset(index.get('tools', {}))):
        raise ValueError('工具索引不完整，未下载执行文件')
    with (directory / 'latest.json').open('x', encoding='utf-8') as stream:
        json.dump(index, stream, ensure_ascii=False, indent=2)
    with (directory / 'downloads.jsonl').open('x', encoding='utf-8') as log:
        def event(name, **values):
            value = dict(event=name, **values)
            log.write(json.dumps(value, ensure_ascii=False) + '\n'); log.flush()
            print(json.dumps(value, ensure_ascii=False), flush=True)
        for section in ('tools', 'guides'):
            for name, item in index[section].items():
                relative = PurePosixPath(name)
                if relative.is_absolute() or '..' in relative.parts or '\\' in name or (section == 'tools' and len(relative.parts) != 1):
                    raise ValueError('索引文件路径不合法：' + name)
                url = item['url']; parsed = urlsplit(url)
                if not public_url(url) or not ((parsed.hostname == 'beatsad.github.io' and parsed.path.startswith('/quiet-tv/'))
                        or parsed.hostname in ('github.com', 'raw.githubusercontent.com') and parsed.path.lower().startswith('/beatsad/quiet-tv/')):
                    raise ValueError('工具或说明原地址不属于官方项目：' + name)
                target = directory / name
                target.parent.mkdir(parents=True, exist_ok=True)
                alias = ('https://raw.githubusercontent.com/BeatsAD/quiet-tv/main/scripts/' + name
                         if section == 'tools' and parsed.hostname == 'beatsad.github.io' else None)
                download(url, target, item['sha256'], github_url=alias, event=event)
    return directory


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--bundle', required=True, type=Path, help='全新工具包目录；已有任务复用原目录')
    args = parser.parse_args()
    try:
        bundle(args.bundle)
        print('工具包已下载校验，后续使用本目录的 agent_session.py。')
        return 0
    except (OSError, ValueError, KeyError) as error:
        print('工具准备未完成：' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
