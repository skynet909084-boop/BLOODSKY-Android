import json
import os
import tempfile
import unittest
import zipfile
from project_archive_inspector import inspect_archive, safe_entry_name

class ArchiveInspectorTests(unittest.TestCase):
    def test_safe_paths(self):
        self.assertTrue(safe_entry_name("project/main.json"))
        self.assertFalse(safe_entry_name("../escape.txt"))
        self.assertFalse(safe_entry_name("C:/escape.txt"))
        self.assertFalse(safe_entry_name("folder\\escape.txt"))
        self.assertFalse(safe_entry_name("/absolute.txt"))

    def test_valid_project_archive(self):
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "project.zip")
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("project.json", json.dumps({"TableNameProject": {}, "TableComponent": [], "TableConnections": []}))
                archive.writestr("media/panel.png", b"image")
            report = inspect_archive(path)
            self.assertEqual(report.json_entries, 1)
            self.assertEqual(report.project_json_entries, ["project.json"])
            self.assertFalse(any(issue.severity == "error" for issue in report.issues))

    def test_invalid_json_is_error(self):
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "bad.zip")
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("project.json", "{broken")
            report = inspect_archive(path)
            self.assertTrue(any(issue.severity == "error" and "Invalid JSON" in issue.message for issue in report.issues))

    def test_unsafe_path_is_error(self):
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "unsafe.zip")
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("../outside.txt", "bad")
            report = inspect_archive(path)
            self.assertTrue(any(issue.severity == "error" and issue.message == "Unsafe ZIP entry path" for issue in report.issues))

if __name__ == "__main__":
    unittest.main()
