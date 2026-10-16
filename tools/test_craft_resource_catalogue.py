"""Verify deterministic source resource catalogue and checksum traceability."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from catalogue_craft_resources import catalogue


class ResourceCatalogueTests(unittest.TestCase):
    def test_catalogue_includes_every_file_and_hash(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "original"
            source.mkdir()
            digest = hashlib.sha256(b"abc").hexdigest()
            (source / "MANIFEST.json").write_text(json.dumps({
                "files": [
                    {"path": "res/layout/dialog.xml", "size": 3, "sha256": digest},
                    {"path": "assets/sound.wav", "size": 3, "sha256": digest},
                ]
            }), encoding="utf-8")
            output = root / "report.md"
            self.assertEqual(catalogue(source, output), 2)
            text = output.read_text(encoding="utf-8")
            self.assertIn("res/layout/dialog.xml", text)
            self.assertIn("assets/sound.wav", text)
            self.assertIn(digest, text)
            self.assertIn("Total original files: **2**", text)


if __name__ == "__main__":
    unittest.main()
