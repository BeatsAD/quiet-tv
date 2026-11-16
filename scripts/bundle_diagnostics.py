#!/usr/bin/env python3
"""Embed source identity for private diagnostics, without machine paths or build time."""
import argparse
import json
from pathlib import Path
import subprocess
from build_apk import ROOT, source_digest


def metadata():
    commit, dirty = 'unknown', None
    try:
        top = subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], cwd=ROOT, text=True).strip()
        if Path(top).resolve() != ROOT.resolve():
            raise ValueError('Source archive is outside its owning checkout')
        commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
        areas = ['app/src/main', 'app/src/standard', 'app/src/legacy19', 'app/src/home', 'app/src/relay',
                 'app/build.gradle', 'app/proguard-rules.pro', 'home-proxy/src/contract', 'build.gradle',
                 'settings.gradle', 'gradle.properties', 'gradle/security-constraints.gradle', 'gradle/diagnostics.gradle',
                 'scripts/bundle_about.py', 'scripts/bundle_diagnostics.py', 'scripts/build_apk.py',
                 'scripts/install_tcl.py', 'prototypes/about', 'LICENSE', 'THIRD_PARTY_NOTICES.md']
        changed = subprocess.run(['git', 'diff-index', '--quiet', 'HEAD', '--', *areas], cwd=ROOT, check=False)
        untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '--', *areas], cwd=ROOT, text=True)
        dirty = changed.returncode != 0 or bool(untracked.strip())
    except (OSError, ValueError, subprocess.CalledProcessError):
        pass
    return dict(schemaVersion=1, sourceCommit=commit, sourceDirty=dirty, sourceSha256=source_digest(),
                mappingLookup='package, version_code, compatibility, ABI and production_sources_sha256 in the immutable build record')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output/'diagnostic-build.json').write_text(json.dumps(metadata(), sort_keys=True)+'\n', encoding='utf-8')
