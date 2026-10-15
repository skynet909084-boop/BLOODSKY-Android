"""Verify that the original Craft asset library is copied without renaming."""
import json
from pathlib import Path
import tempfile
import unittest

from bundle_craft_resources import bundle


class CraftResourceBundleTests(unittest.TestCase):
    def test_preserves_names_paths_and_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            decoded = root / "decoded/apktool"
            for i in range(501):
                item = decoded / "res" / ("layout" if i % 2 else "drawable-hdpi") / f"item_{i}.xml"
                item.parent.mkdir(parents=True, exist_ok=True)
                item.write_bytes(f"<item id='{i}'/>".encode())
            for name in ("fonts/digital.ttf", "web/index.html",
                         "audio/alert.wav", "help.pdf"):
                item = decoded / "assets" / name
                item.parent.mkdir(parents=True, exist_ok=True)
                item.write_bytes(b"original-binary-contents")
            app = root / "app"
            groups = bundle(root / "decoded", app)
            self.assertEqual(groups, {"res": 501, "assets": 4})
            out = app / "src/main/assets/kascada_original"
            self.assertEqual((out / "assets/fonts/digital.ttf").read_bytes(),
                             b"original-binary-contents")
            self.assertEqual((out / "res/layout/item_1.xml").read_bytes(),
                             b"<item id='1'/>")
            manifest = json.loads((out / "MANIFEST.json").read_text())
            self.assertEqual(len(manifest["files"]), 505)
            self.assertEqual(len(manifest["files"][0]["sha256"]), 64)

    def test_rejects_missing_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaises(ValueError):
                bundle(root / "not-extracted", root / "app")


if __name__ == "__main__":
    unittest.main()
