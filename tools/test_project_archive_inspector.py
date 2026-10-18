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

    def test_rejects_ambiguous_normalized_paths(self):
        for name in (".", "./project.json", "folder/./project.json",
                     "folder//project.json", "folder///project.json",
                     "folder/../project.json", "folder//"):
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

    def test_validates_craft_component_other_and_screen_index(self):
        payload = json.dumps({
            "TableNameProject": {"name": "Demo", "countScreen": 1},
            "TableComponent": [
                {"componentId": "2", "text": "Label", "other": {"numScreen": 1}}
            ],
            "TableConnections": []
        })
        path = self.make_archive([("project.json", payload)])
        report = inspect_archive(path)
        self.assertFalse(any(issue.severity == "error" for issue in report.issues))

    def test_rejects_malformed_craft_tables_and_component_metadata(self):
        payload = json.dumps({
            "TableNameProject": {"name": "Demo"},
            "TableComponent": [
                {"componentId": "not-an-id", "other": "{broken"},
                {"componentId": "4", "other": {"numScreen": 0}},
                {"componentId": 5, "other": {"numScreen": 2}},
                None
            ],
            "TableConnections": {}
        })
        path = self.make_archive([("project.json", payload)])
        report = inspect_archive(path)
        messages = [issue.message for issue in report.issues if issue.severity == "error"]
        self.assertTrue(any("TableConnections must be an array" in message for message in messages))
        self.assertTrue(any("componentId must be an integer or decimal string" in message for message in messages))
        self.assertTrue(any("Invalid component other JSON" in message for message in messages))
        self.assertTrue(any("component row must be an object" in message for message in messages))
        self.assertTrue(any("numScreen must be a positive 1-based integer" in message for message in messages))
        self.assertTrue(any("numScreen exceeds countScreen" in message for message in messages))

    def test_requires_all_three_craft_tables(self):
        path = self.make_archive([("project.json", json.dumps({"TableComponent": []}))])
        report = inspect_archive(path)
        self.assertTrue(any(issue.severity == "error" and "Missing Craft project table TableNameProject" in issue.message
                            for issue in report.issues))
        self.assertTrue(any(issue.severity == "error" and "Missing Craft project table TableConnections" in issue.message
                            for issue in report.issues))

    def test_recognizes_pro_json_member(self):
        payload = json.dumps({
            "TableNameProject": {"name": "Demo", "countScreen": 1},
            "TableComponent": [
                {"componentId": "4", "other": {"numScreen": 1}}
            ],
            "TableConnections": []
        })
        path = self.make_archive([("home/home.pro", payload), ("home/image.png", b"png")])
        report = inspect_archive(path)
        self.assertEqual(report.json_entries, 1)
        self.assertEqual(report.project_json_entries, ["home/home.pro"])
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

    def test_duplicate_json_names_are_rejected_without_parsing(self):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            path = self.make_archive([("project.json", '{"TableComponent": []}'),
                                     ("project.json", '{"TableNameProject": {}}')])
        report = inspect_archive(path)
        self.assertEqual(report.json_entries, 0)
        self.assertTrue(any(issue.severity == "error" and issue.message == "Duplicate ZIP entry name"
                            for issue in report.issues))

    def test_rejects_unsafe_directory_entries(self):
        path = self.make_archive([("folder/../", ""), ("project.json", '{"TableComponent":[]}')])
        report = inspect_archive(path)
        self.assertTrue(any(issue.severity == "error" and issue.entry == "folder/../"
                            for issue in report.issues))

    def test_corrupted_json_member_returns_error_instead_of_crashing(self):
        temp = tempfile.NamedTemporaryFile(suffix=".zip", delete=False)
        temp.close()
        self.addCleanup(lambda: os.path.exists(temp.name) and os.unlink(temp.name))
        payload = b'{"TableComponent":[]}'
        with zipfile.ZipFile(temp.name, "w", compression=zipfile.ZIP_STORED) as archive:
            archive.writestr("project.json", payload)
        with open(temp.name, "rb") as handle:
            data = handle.read()
        offset = data.index(payload)
        corrupted = bytearray(data)
        corrupted[offset] ^= 1
        with open(temp.name, "wb") as handle:
            handle.write(corrupted)
        report = inspect_archive(temp.name)
        self.assertTrue(any(issue.severity == "error" and "Invalid JSON" in issue.message
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
