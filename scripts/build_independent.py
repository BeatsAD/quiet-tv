#!/usr/bin/env python3
"""Build immutable local 2.0 test packages; never install, merge or publish."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

import build_apk as build


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-number', required=True, type=int)
    parser.add_argument('--compatibility', choices=build.COMPATIBILITY, default='standard')
    parser.add_argument('--local-test', action='store_true', help='Record local TV testing; independent versions remain three-part')
    parser.add_argument('--variant', choices=('debug', 'benchmark'), default='debug')
    args = parser.parse_args()
    if args.build_number < 1:
        parser.error('--build-number must be positive')
    configuration = f'{args.compatibility}-{args.variant}-default-local{int(args.local_test)}'
    with build.build_workspace(build.ROOT, 'independent', configuration) as (output_root, lock_fd):
        archive = build.ROOT / 'dist/apk/desktop-v2-independent'
        archive.mkdir(parents=True, exist_ok=True)
        used = [int(path.stem[6:]) for path in archive.glob('build-*.json')]
        if used and args.build_number <= max(used):
            parser.error(f'--build-number must be higher than reserved {max(used)}')
        reservation = archive / f'build-{args.build_number}.json'
        # Reserve once, including failed builds. The independent package has its own sequence.
        with reservation.open('x') as output:
            json.dump({'application_id': 'home.quiet.tv.scopeqa',
                       'version_code': args.build_number, 'compatibility': args.compatibility,
                       'local_test': args.local_test, 'build_type': args.variant}, output)
        before = build.source_digest()
        command = ['./gradlew', ':app:assemble' + args.variant.capitalize(), '-PquietIndependent=true',
                   f'-PquietBuildNumber={args.build_number}',
                   f'-PquietLocalTest={str(args.local_test).lower()}',
                   f'-PquietCompatibility={args.compatibility}', f'-PquietBuildRoot={output_root}']
        with (archive / f'build-{args.build_number}.log').open('x') as log:
            subprocess.run(command, cwd=build.ROOT, stdout=log, stderr=subprocess.STDOUT, check=True, pass_fds=(lock_fd,))
        if before != build.source_digest():
            raise RuntimeError('Production source changed during build; package not archived')
        apk = output_root / f'app/outputs/apk/{args.variant}/app-{args.variant}.apk'
        metadata = build.manifest_info(apk)
        version = '2.0.0'
        sdk = {'standard': 21, 'legacy14': 14, 'legacy19': 19}[args.compatibility]
        if (metadata['application_id'] != 'home.quiet.tv.scopeqa'
                or metadata['version_name'] != version
                or metadata['version_code'] != args.build_number or metadata['min_sdk'] != sdk):
            raise RuntimeError('Independent APK identity/profile does not match the request')
        with zipfile.ZipFile(apk) as package:
            abis = sorted({name.split('/')[1] for name in package.namelist()
                           if name.startswith('lib/') and name.endswith('.so')})
        signers = build.signer_digest(apk, sdk)
        name, _ = build.apk_names(version, sdk, abis, build_number=args.build_number)
        destination = archive / name
        with destination.open('xb') as target, apk.open('rb') as source:
            shutil.copyfileobj(source, target)
        digest = hashlib.sha256(destination.read_bytes()).hexdigest()
        with Path(str(destination) + '.sha256').open('x') as output:
            output.write(f'{digest}  {destination.name}\n')
        commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=build.ROOT, text=True).strip()
        metadata.update(compatibility=args.compatibility, build_type=args.variant, abis=abis, packaged_abis=abis, signer_sha256=signers, local_test=args.local_test,
                        bytes=destination.stat().st_size, home_mode='standard', workspace_commit_at_archive=commit,
                        sha256=digest, source_digest=before,
                        source_commit=commit,
                        tracked_source_dirty=bool(subprocess.check_output(
                            ['git', 'diff', 'HEAD', '--name-only'], cwd=build.ROOT, text=True).strip()),
                        apk=str(destination), purpose='independent local test; no production upgrade or publication')
        with destination.with_suffix('.json').open('x') as output:
            json.dump(metadata, output, ensure_ascii=False, indent=2)
        print(json.dumps(metadata, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
