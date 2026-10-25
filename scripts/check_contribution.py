#!/usr/bin/env python3
"""Check changed documentation and Python syntax without contacting a TV."""
import argparse
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='origin/main', help='Comparison commit or branch')
    args = parser.parse_args()
    # Include deletions in classification, and new local files in pre-commit checks.
    changed = set(git('diff', '--name-only', '-z', args.base).split('\0'))
    new_files = set(git('ls-files', '--others', '--exclude-standard', '-z').split('\0'))
    changed.update(new_files)
    changed.discard('')
    subprocess.run(['git', 'diff', '--check', args.base], cwd=ROOT, check=True)

    public_files = set(git('ls-files', '-z').split('\0')) | new_files
    public_files.discard('')
    local_only = git('ls-files', '--cached', '--ignored', '--exclude-from=.gitignore', '-z').strip('\0')
    if local_only:
        raise SystemExit('Local-only files are tracked; update the publication exceptions if explicitly approved: ' + local_only.replace('\0', ', '))
    python_files = {name for name in public_files if name.endswith('.py')}
    for name in sorted(python_files):
        path = ROOT / name
        if path.is_file():
            compile(path.read_bytes(), name, 'exec')

    documents = 0
    for name in sorted(changed):
        path = ROOT / name
        if name not in public_files or path.suffix != '.md' or not path.is_file():
            continue
        documents += 1
        source = re.sub(r'(?ms)^```[^\n]*\n.*?^```[^\n]*$', '', path.read_text())
        targets = [match[0] or match[1] for match in re.findall(
            r'!?\[[^\]\n]*\]\((?:<([^>\n]+)>|([^\s)]+))(?:\s+[^)]*)?\)', source)]
        targets += re.findall(r'<(?:img|a)\b[^>]*\b(?:src|href)=["\']([^"\']+)', source)
        for target in targets:
            url = urlsplit(target)
            if url.scheme or url.netloc or not url.path:
                continue
            destination = path.parent / unquote(url.path)
            if not destination.exists():
                raise SystemExit(f'{name}: missing local link: {target}')
            relative = os.path.relpath(destination.resolve(), ROOT)
            if relative not in public_files and not any(item.startswith(relative.rstrip('/') + '/') for item in public_files):
                raise SystemExit(f'{name}: link target is not included in the public repository: {target}')
    print(f'Python syntax checked; local links checked in {documents} changed Markdown files.')

    tooling = any(name.startswith('.github/workflows/') or name == 'scripts/check_contribution.py'
                  for name in changed)
    project_changes = {name for name in changed if not name.endswith('.md')}
    flags = {
        'install': any(name.startswith('prototypes/install-guide/') or name in (
            'docs/AGENT_INSTALL.zh-CN.md', 'scripts/build_install_guide.py', '.github/workflows/pages.yml')
                       for name in changed),
        'android': tooling or any(name.startswith(('app/', 'gradle/', 'coocaa-home-shim/', 'xiaomi-home-helper/', 'home-proxy/'))
                                 or name in ('build.gradle', 'settings.gradle', 'gradle.properties',
                                             'gradlew', 'gradlew.bat', 'scripts/bundle_about.py', 'LICENSE')
                                 or name.startswith('prototypes/about/') for name in project_changes)
                   or 'THIRD_PARTY_NOTICES.md' in changed,
        'python': tooling or any(name.startswith('scripts/') and name.endswith('.py') for name in changed),
    }
    print('Selected checks:', ', '.join(key for key, enabled in flags.items() if enabled) or 'documentation / Python syntax')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
            for key, enabled in flags.items():
                output.write(f'{key}={str(enabled).lower()}\n')


if __name__ == '__main__':
    main()
