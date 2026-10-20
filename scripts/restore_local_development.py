#!/usr/bin/env python3
"""Restore missing local-only development files from the last complete public tree."""
import argparse
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PREVIOUS_SOURCE = '82763d8b01fc269e4ac4c4b9b916cc9b482734be'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def restore(ref):
    available = subprocess.run(['git', 'cat-file', '-e', ref + '^{commit}'],
                               cwd=ROOT, capture_output=True)
    if available.returncode:
        raise SystemExit(f'History unavailable. Run git fetch origin {ref}, then retry.')
    tracked = set(git('ls-files', '-z').decode().split('\0'))
    former = {}
    for record in git('ls-tree', '-r', '-z', ref).decode().split('\0'):
        if not record:
            continue
        metadata, name = record.split('\t', 1)
        mode, kind, oid = metadata.split()
        if kind == 'blob' and mode in ('100644', '100755') and name not in tracked:
            former[name] = (mode, oid)
    if not former:
        print('No local development files need restoring.')
        return
    ignored = subprocess.run(['git', 'check-ignore', '--no-index', '--stdin', '-z'],
                             input='\0'.join(former) + '\0', text=True,
                             cwd=ROOT, capture_output=True)
    if ignored.returncode not in (0, 1):
        ignored.check_returncode()
    restored = kept = 0
    for name in filter(None, ignored.stdout.split('\0')):
        path = ROOT / name
        if path.exists() or path.is_symlink():
            kept += 1
            continue
        path.resolve().relative_to(ROOT)
        mode, oid = former[name]
        contents = git('cat-file', 'blob', oid)
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open('xb') as output:
            output.write(contents)
        if mode == '100755':
            path.chmod(path.stat().st_mode | 0o111)
        restored += 1
    print(f'Restored {restored} missing local files; kept {kept} existing paths unchanged.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ref', default=PREVIOUS_SOURCE,
                        help='Historical commit containing the local development files')
    restore(parser.parse_args().ref)
