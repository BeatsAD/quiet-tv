#!/usr/bin/env python3
"""Reusable build directories and a read-only worktree/storage report (macOS/Linux)."""
import argparse
from contextlib import contextmanager
import fcntl
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys


def warn_space(root):
    free = shutil.disk_usage(root).free / 1e9
    if free < 25:
        print(f'存储提醒：可用 {free:.1f} GB，建议保留 40–50 GB；仅核查并移交清理候选，不自动删除。', file=sys.stderr)
    return free


@contextmanager
def build_workspace(root, namespace, configuration):
    """Lock from compilation through archival; children must inherit the yielded fd."""
    if any(not re.fullmatch(r'[a-zA-Z0-9_-]+', part) for part in (namespace, configuration)):
        raise ValueError('构建目录标识只能含英文字母、数字、下划线和短横线')
    root = Path(root).resolve()
    warn_space(root)
    parent = root / 'output/build-cache' / namespace
    parent.mkdir(parents=True, exist_ok=True)
    # Keep the lock file: unlinking it would let another process lock a different inode.
    with (parent / (configuration + '.lock')).open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('同一配置已有构建运行，请等其完成再重试：' + configuration) from None
        destination = parent / configuration
        destination.mkdir(exist_ok=True)
        # Closing releases our reference. An inherited child fd keeps its lock if
        # the parent exits early, until the compiler process also exits.
        yield destination, lock.fileno()


def git(root, *args):
    return subprocess.check_output(['git', '--no-optional-locks', '-C', str(root), *args], text=True).strip()


def report(root, target, sizes=False):
    root = Path(git(root, 'rev-parse', '--show-toplevel'))
    target_commit = git(root, 'rev-parse', '--verify', target + '^{commit}')
    trees = []
    raw = subprocess.check_output(['git', '-C', str(root), 'worktree', 'list', '--porcelain', '-z'])
    for block in raw.split(b'\0\0'):
        fields = {}
        for field in block.split(b'\0'):
            if field:
                key, _, value = field.decode().partition(' ')
                fields[key] = value
        if 'worktree' not in fields:
            continue
        path = Path(fields['worktree'])
        state = 'missing' if not path.is_dir() else 'pending-manual-deletion' if '待删除文件' in path.parts else 'active'
        item = dict(path=str(path), state=state, head=fields.get('HEAD'), branch=fields.get('branch'), locked='locked' in fields)
        if state == 'active':
            item['tracked_or_untracked_changes'] = bool(git(path, 'status', '--porcelain', '--untracked-files=normal'))
            result = subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', fields['HEAD'], target_commit])
            if result.returncode not in (0, 1):
                raise RuntimeError('无法核对工作树提交关系：' + str(path))
            item['commits_in_target'] = result.returncode == 0
            item['retention_refs'] = git(root, 'for-each-ref', '--contains=' + fields['HEAD'], '--format=%(refname)', 'refs/heads', 'refs/tags').splitlines()
            if sizes:
                item['allocated_bytes'] = int(subprocess.check_output(['du', '-sk', str(path)]).split()[0]) * 1024
        trees.append(item)
    return dict(repo=str(root), target=target, free_gb=warn_space(root), worktrees=trees,
                notice='合入、干净或闲置均不等于可删除；退役前仍须核对进程占用、忽略文件、外部引用及保留记录。未执行删除或移动。')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--target', default='main', help='2.0 开发核对 codex/desktop-v2-native，不强制合入稳定 main')
    parser.add_argument('--sizes', action='store_true', help='额外统计活动工作树实际分配空间，可能较慢')
    args = parser.parse_args()
    print(json.dumps(report(args.repo, args.target, args.sizes), ensure_ascii=False, indent=2))
