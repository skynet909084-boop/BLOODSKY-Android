import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile

from prepare_craft_resources import unpack_reference


class CraftReferenceTests(unittest.TestCase):
    def check_archive(self, entries, valid=True):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / 'reference.zip'
            with zipfile.ZipFile(archive, 'w') as out:
                for name, content in entries:
                    out.writestr(name, content)
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            target = root / 'decoded'
            if valid:
                unpack_reference(archive, target, digest)
                self.assertEqual((target / entries[0][0]).read_bytes(), entries[0][1])
            else:
                with self.assertRaises(ValueError):
                    unpack_reference(archive, target, digest)
                self.assertFalse(target.exists())

    def test_exact_original_bytes_survive_extraction(self):
        self.check_archive([('apktool/assets/fonts/digital.ttf', b'font bytes')])

    def test_rejects_unsafe_archive_before_writing(self):
        for name in ('../escape', '/absolute', 'a/../b', 'a//b', 'C:/file', 'a\\b'):
            with self.subTest(name=name):
                self.check_archive([('good/file', b'good'), (name, b'bad')], valid=False)

    def test_rejects_duplicate_names(self):
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            self.check_archive([('file', b'first'), ('file', b'second')], valid=False)

    def test_rejects_changed_reference_checksum(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / 'reference.zip'
            archive.write_bytes(b'changed bytes')
            with self.assertRaisesRegex(ValueError, 'checksum'):
                unpack_reference(archive, Path(directory) / 'decoded')
