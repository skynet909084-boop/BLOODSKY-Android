"""Tests for original drawable usage audit."""
from pathlib import Path
import tempfile
import unittest
from audit_craft_drawable_wiring import audit


class DrawableWiringTests(unittest.TestCase):
    def test_explicit_and_missing_resources_are_distinguished(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            original = root / "drawables"
            original.mkdir()
            for name in ["lamp_green", "lamp_red", "button", "arrow", "meter",
                         "switch", "led", "valve", "gauge", "pump"]:
                (original / (name + ".png")).write_bytes(b"png")
            src = root / "src"
            src.mkdir()
            (src / "Screen.java").write_text(
                'class Screen { int icon = R.drawable.kc_lamp_green; '
                'String name = "kc_button"; }', encoding="utf-8")
            output = root / "audit.md"
            total, wired = audit(original, src, output)
            self.assertEqual((total, wired), (10, 2))
            report = output.read_text(encoding="utf-8")
            self.assertIn("No explicit literal reference: **8**", report)
            self.assertIn("`kc_lamp_red`", report)
            self.assertIn("`kc_button`", report)

    def test_missing_original_source_is_error(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            with self.assertRaises(ValueError):
                audit(root, root, root / "report.md")


if __name__ == "__main__":
    unittest.main()
