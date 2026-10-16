"""Tests for decoded Android resource XML cross-reference audit."""
from pathlib import Path
import tempfile
import unittest
from map_craft_xml_references import scan, report


class OriginalResourceReferenceTests(unittest.TestCase):
    def test_qualified_drawable_and_menu_references(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            res = root / "res"
            (res / "drawable-hdpi").mkdir(parents=True)
            (res / "drawable-hdpi" / "lamp.png").write_bytes(b"PNG")
            (res / "layout").mkdir()
            (res / "layout" / "screen.xml").write_text(
                '<ImageView android:src="@drawable/lamp" android:id="@+id/indicator" />',
                encoding="utf-8",
            )
            (res / "menu").mkdir()
            (res / "menu" / "main.xml").write_text(
                '<item android:icon="@drawable/lamp" />', encoding="utf-8"
            )
            index, references = scan(res)
            self.assertIn(("drawable", "lamp"), index)
            self.assertEqual(len(references[("drawable", "lamp")]), 2)
            self.assertIn(("id", "indicator"), references)
            output = root / "audit.md"
            total, missing = report(res, output)
            self.assertGreaterEqual(total, 2)
            self.assertEqual(missing, 0)
            self.assertIn("layout/screen.xml", output.read_text(encoding="utf-8"))

    def test_reports_missing_file_resource(self):
        with tempfile.TemporaryDirectory() as temp:
            res = Path(temp) / "res"
            (res / "layout").mkdir(parents=True)
            (res / "layout" / "screen.xml").write_text(
                '<ImageView src="@drawable/unknown" />', encoding="utf-8"
            )
            total, missing = report(res, Path(temp) / "out.md")
            self.assertEqual(total, 1)
            self.assertEqual(missing, 1)


if __name__ == "__main__":
    unittest.main()
