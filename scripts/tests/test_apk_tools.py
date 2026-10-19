import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import analyze_apk
import build_apk


class ArchiveChecks(unittest.TestCase):
    def setUp(self):
        # Preserve fixtures; project policy prohibits recursive temporary cleanup.
        self.root = Path(tempfile.mkdtemp(prefix='quiet-apk-check-'))
        self.output = self.root / 'output'
        self.output.mkdir()
        self.archive = self.root / 'archive'
        self.apk = self.output / 'app.apk'
        self.write_apk(b'code')
        (self.output / 'output-metadata.json').write_text(json.dumps({
            'applicationId': 'home.quiet.tv', 'variantName': 'benchmark',
            'elements': [{'outputFile': 'app.apk', 'versionName': '1.1.0', 'versionCode': 16}]}))

    def write_apk(self, code):
        with zipfile.ZipFile(self.apk, 'w') as z:
            z.writestr('classes.dex', code)
            z.writestr('lib/arm64-v8a/libfixture.so', b'native')

    def save(self, abi='arm64-v8a'):
        with patch.object(build_apk, 'signer_digest', return_value=['certificate']), patch.object(build_apk, 'manifest_min_sdk', return_value=31):
            return build_apk.archive_apk('fixture', output_dir=self.output, abi=abi, archive_dir=self.archive)

    def test_same_bytes_reuse_and_different_bytes_refused(self):
        first = self.save()
        receipt = first.with_suffix('.json').read_bytes()
        self.assertEqual(first, self.save())
        self.assertEqual(receipt, first.with_suffix('.json').read_bytes())
        self.write_apk(b'changed')
        with self.assertRaises(FileExistsError):
            self.save()
        self.assertEqual(receipt, first.with_suffix('.json').read_bytes())

    def test_wrong_architecture_cannot_be_mislabeled(self):
        with self.assertRaises(ValueError):
            self.save('armeabi-v7a')
        self.assertFalse(self.archive.exists())

    def test_unsigned_apk_not_archived(self):
        with patch.object(build_apk, 'signer_digest', return_value=[]):
            with self.assertRaises(ValueError):
                build_apk.archive_apk('unsigned', output_dir=self.output, archive_dir=self.archive)
        self.assertFalse(self.archive.exists())

    def test_breakdown_accounts_for_every_packaged_byte(self):
        report = analyze_apk.analyze(self.apk)
        self.assertEqual(report['groups']['lib/arm64-v8a']['packaged_bytes'], 6)
        self.assertEqual(report['bytes'], sum(g['packaged_bytes'] for g in report['groups'].values())
                         + report['zip_and_signing_overhead_bytes'])

    def test_manifest_sdk_cannot_be_mislabeled(self):
        with patch.object(build_apk, 'signer_digest', return_value=['certificate']), patch.object(build_apk, 'manifest_min_sdk', return_value=26):
            with self.assertRaises(ValueError):
                build_apk.archive_apk('wrong sdk', output_dir=self.output, archive_dir=self.archive)
        self.assertFalse(self.archive.exists())

    def test_api26_candidate_has_explicit_suffix(self):
        with patch.object(build_apk, 'signer_digest', return_value=['certificate']), patch.object(build_apk, 'manifest_min_sdk', return_value=26):
            apk = build_apk.archive_apk('compat', output_dir=self.output, archive_dir=self.archive, min_sdk=26)
        self.assertTrue(apk.name.endswith('_api26.apk'))
        self.assertEqual(json.loads(apk.with_suffix('.json').read_text())['min_sdk'], 26)

    def test_build_does_not_archive_changing_sources(self):
        with patch.object(build_apk, 'source_digest', side_effect=['before', 'after']), patch.object(build_apk.subprocess, 'run'), patch.object(build_apk, 'archive_apk') as archive:
            with self.assertRaises(RuntimeError):
                build_apk.build_apk('source race')
        archive.assert_not_called()


if __name__ == '__main__':
    unittest.main()
