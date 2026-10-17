import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile

from original_fbd_source_probe import report_from_zip, SOURCE_PREFIX


class OriginalFbdSourceProbeTests(unittest.TestCase):
    def archive(self, files):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "fixture.zip"
        with zipfile.ZipFile(path, "w") as archive:
            for name, body in files.items():
                archive.writestr(name, body)
        return path, hashlib.sha256(path.read_bytes()).hexdigest()

    def test_source_marker_lines_have_real_file_paths_and_hashes(self):
        source = "class FB_CTU {\n  int getValue() { return 1; }\n  // CTU output\n}\n"
        path, digest = self.archive({
            SOURCE_PREFIX + "FB_CTU.java": source,
            "apktool/res/layout/fragment_component_logic_bar.xml":
                '<FrameLayout android:id="@+id/FB_CTU"/>',
        })
        result = report_from_zip(path, digest)
        self.assertEqual(1, result["totals"]["logic_java"])
        self.assertEqual(1, result["totals"]["layout_xml"])
        self.assertIn("FB_CTU", result["markers_present"])
        self.assertIn("getValue", result["markers_present"])
        java = next(row for row in result["evidence"] if row["archive_path"].endswith(".java"))
        self.assertEqual(hashlib.sha256(source.encode()).hexdigest(), java["source_sha256"])
        self.assertEqual(1, java["hits"][0]["line"])
        self.assertIn("FB_CTU", java["hits"][0]["markers"])

    def test_archive_sha_guard_rejects_wrong_or_unreviewed_zip(self):
        path, digest = self.archive({SOURCE_PREFIX + "CTU.java": "class CTU {}"})
        with self.assertRaisesRegex(ValueError, "SHA256"):
            report_from_zip(path, "0" * 64)
        self.assertEqual(digest, hashlib.sha256(path.read_bytes()).hexdigest())

    def test_zip_traversal_rejected_before_any_source_is_used(self):
        path, digest = self.archive({"../unsafe.java": "class CTU {}"})
        with self.assertRaisesRegex(ValueError, "Unsafe ZIP"):
            report_from_zip(path, digest)

    def test_no_marker_does_not_invent_timer_semantics(self):
        path, digest = self.archive({SOURCE_PREFIX + "Unknown.java": "class Unknown { int a; }"})
        result = report_from_zip(path, digest)
        self.assertEqual([], result["markers_present"])
        self.assertEqual([], result["evidence"])
        self.assertIn("TON", result["markers_absent"])


if __name__ == "__main__":
    unittest.main()
