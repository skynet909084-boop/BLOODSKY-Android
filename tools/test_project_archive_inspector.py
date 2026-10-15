import json
import os
import tempfile
import unittest
import warnings
import zipfile

from project_archive_inspector import inspect_archive, safe_entry_name


class SafeEntryNameTests(unittest.TestCase):
    def test_rejects_absolute_and_traversal_paths(self):
        for name in ("/absolute.json", "../escape.json", "folder/../escape.json",
                     r"folder\\escape.json", "C:/escape.json"):
            with self.subTest(name=name):
                self.assertFalse(safe_entry_name(name))

    def test_accepts_normal_relative_paths(self):
        self.assertTrue(safe_entry_name("project/data.json"))
        self.assertTrue(safe_entry_name("assets/layout.xml"))


class ArchiveInspectorTests(unittest.TestCase):
    def make_archive(self, entries):
        temp = tempfile.NamedTemporaryFile(suffix=".zip", delete=False)
        temp.close()
        with zipfile.ZipFile(temp.name, "w", zipfile.ZIP_DEFLATED) as archive:
            for name, content in entries:
                archive.writestr(name, content)
        self.addCleanup(lambda: os.path.exists(temp.name) and os.unlink(temp.name))
        return temp.name

    def test_recognizes_project_json(self):
        payload = json.dumps({
            "TableNameProject": {"name": "Demo"},
            "TableComponent": [],
            "TableConnections": []
        })
        path = self.make_archive([("project.json", payload)])
        report = inspect_archive(path)
        self.assertEqual(report.json_entries, 1)
        self.assertEqual(report.project_json_entries, ["project.json"])
        self.assertFalse(any(issue.severity == "error" for issue in report.issues))

    def test_reports_invalid_json(self):
        path = self.make_archive([("broken.json", "{not json")])
        report = inspect_archive(path)
        self.assertTrue(any(issue.severity == "error" and "Invalid JSON" in issue.message
                            for issue in report.issues))

    def test_reports_unsafe_zip_path(self):
        path = self.make_archive([("../outside.json", "{}")])
        report = inspect_archive(path)
        self.assertTrue(any(issue.severity == "error" and issue.entry == "../outside.json"
                            for issue in report.issues))

    def test_non_zip_file_returns_error_report(self):
        temp = tempfile.NamedTemporaryFile(delete=False)
        temp.write(b"not a zip")
        temp.close()
        self.addCleanup(lambda: os.path.exists(temp.name) and os.unlink(temp.name))
        report = inspect_archive(temp.name)
        self.assertTrue(any(issue.severity == "error" and "Cannot open ZIP" in issue.message
                            for issue in report.issues))


if __name__ == "__main__":
    unittest.main()
