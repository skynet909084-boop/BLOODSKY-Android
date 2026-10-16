"""Regression tests for original Craft component-picker candidate audit."""
from pathlib import Path
import tempfile
import unittest

from audit_picker_resource_candidates import audit


class PickerResourceCandidateAuditTests(unittest.TestCase):
    def test_reports_candidate_names_without_claiming_verified_mappings(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            drawables = root / "drawable"
            drawables.mkdir()
            for i in range(105):
                (drawables / f"resource_{i}.png").write_bytes(b"png")
            for name in ("lamp_green", "button_pressed", "gauge_round", "chart_line"):
                (drawables / f"{name}.png").write_bytes(b"png")
            (drawables / "button_pressed.9.png").write_bytes(b"ninepatch")
            (drawables / "UPPER.png").write_bytes(b"invalid")
            output = root / "report.md"
            self.assertEqual(audit(drawables, output), 109)
            report = output.read_text(encoding="utf-8")
            self.assertIn("Candidates only", report)
            self.assertIn("`lamp_green`", report)
            self.assertIn("`button_pressed`", report)
            self.assertIn("`gauge_round`", report)
            self.assertIn("`chart_line`", report)
            self.assertNotIn("button_pressed.9", report)

    def test_rejects_missing_or_incomplete_catalogue(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaises(ValueError):
                audit(root / "missing", root / "report.md")
            drawable = root / "drawable"
            drawable.mkdir()
            (drawable / "lamp.png").write_bytes(b"png")
            with self.assertRaises(ValueError):
                audit(drawable, root / "report.md")


if __name__ == "__main__":
    unittest.main()
