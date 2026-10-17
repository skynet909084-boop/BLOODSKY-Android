import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile

from original_fbd_source_probe import (report_from_zip, contract_fingerprint,
                                       assert_pinned_original_labels, SOURCE_PREFIX)


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

    def test_class_fingerprint_reports_quoted_port_labels_and_real_method_calls(self):
        source = ('class FB_TIMER {\n'
                  '  FB_TIMER(){setName("TON");}\n'
                  '  void ports(){drawText("Q");drawText("IN");drawText("PT");}\n'
                  '  int getValue(){return 0;}\n'
                  '}\n')
        row = contract_fingerprint(SOURCE_PREFIX + "FB_TIMER.java", source.encode())
        self.assertEqual(hashlib.sha256(source.encode()).hexdigest(), row["sha256"])
        self.assertEqual(["TON", "Q", "IN", "PT"], [x["value"] for x in row["literal_labels"]])
        self.assertEqual([2, 3, 3, 3], [x["line"] for x in row["literal_labels"]])
        calls = [x["name"] for x in row["method_calls"]]
        self.assertIn("setName", calls)
        self.assertIn("drawText", calls)
        self.assertIn("getValue", calls)
        self.assertIn("not validated direction", row["interpretation"])

    def test_unknown_class_produces_no_contract_and_no_false_labels(self):
        self.assertIsNone(contract_fingerprint(SOURCE_PREFIX + "Other.java",
                           b'class Other { String name = "Q"; }'))
        ctu = contract_fingerprint(SOURCE_PREFIX + "FB_CTU.java",
                    b'class FB_CTU { String name = "CTU"; String other = "NOT_A_PIN"; }')
        self.assertEqual(["CTU"], [x["value"] for x in ctu["literal_labels"]])
        self.assertEqual([], ctu["method_calls"])

    def test_index_includes_class_evidence_without_inferring_semantics(self):
        path, digest = self.archive({
            SOURCE_PREFIX + "FB_CTU.java": 'class FB_CTU { void f(){setName("CTU");drawText("+");drawText("-");drawText("R");} }',
            SOURCE_PREFIX + "FB_TIMER.java": 'class FB_TIMER { void f(){setName("TON");} }',
        })
        report = report_from_zip(path, digest)
        self.assertEqual(2, len(report["contracts"]))
        by_name = {x["archive_path"].split("/")[-1]: x for x in report["contracts"]}
        self.assertEqual(["CTU", "+", "-", "R"],
                         [x["value"] for x in by_name["FB_CTU.java"]["literal_labels"]])
        self.assertIn("not validated direction", by_name["FB_TIMER.java"]["interpretation"])

    def test_pin_claims_require_exact_class_and_original_label_sequence(self):
        def row(filename, labels):
            return {"archive_path": SOURCE_PREFIX + filename,
                    "sha256": "a" * 64,
                    "literal_labels": [{"line": n+1, "value": v}
                                       for n, v in enumerate(labels)]}
        correct = [
            row("FB_CTU.java", ["CTU", "+", "-", "R", "Q", "CTU"]),
            row("FB_RS.java", ["RS", "R", "S", "Q"]),
            row("FB_TIMER.java", ["TON", "IN", "Q", "TOF", "TP"]),
        ]
        claims = assert_pinned_original_labels(correct)
        self.assertEqual(3, len(claims))
        self.assertEqual(["CTU", "+", "-", "R", "Q"], claims[0]["labels"])
        self.assertIn("unverified", claims[0]["claim"])
        with self.assertRaisesRegex(ValueError, "missing"):
            assert_pinned_original_labels(correct[:2])
        wrong = list(correct)
        wrong[0] = row("FB_CTU.java", ["CTU", "CU", "R", "PV", "Q"])
        with self.assertRaisesRegex(ValueError, "mismatch"):
            assert_pinned_original_labels(wrong)
        wrong[0] = correct[0]
        wrong[2] = row("FB_TIMER.java", ["TON", "IN", "PT", "Q", "TOF", "TP"])
        with self.assertRaisesRegex(ValueError, "mismatch"):
            assert_pinned_original_labels(wrong)

    def test_comments_and_quoted_method_names_are_not_original_pin_evidence(self):
        source = (
            'class FB_CTU {\n'
            '  // drawText("PV"); getValue();\n'
            '  /* drawText("CU"); setValue();\n'
            '     drawText("PT"); getInput(); */\n'
            '  String note = "getValue("; // setName("TOF");\n'
            '  String url = "http://host"; // drawText("TP");\n'
            '  void body() { drawText("CTU"); drawText("+"); drawText("-");'
            ' drawText("R"); drawText("Q"); setName("CTU"); }\n'
            '}\n'
        )
        row = contract_fingerprint(SOURCE_PREFIX + "FB_CTU.java", source.encode())
        self.assertEqual(["CTU", "+", "-", "R", "Q", "CTU"],
                         [label["value"] for label in row["literal_labels"]])
        self.assertEqual([7] * 6, [label["line"] for label in row["literal_labels"]])
        self.assertEqual(["drawText"] * 5 + ["setName"],
                         [call["name"] for call in row["method_calls"]])
        self.assertEqual([7] * 6, [call["line"] for call in row["method_calls"]])

    def test_block_comments_do_not_hide_following_real_code(self):
        source = ('/* "CTU" and getValue() */ drawText("R");\n'
                  '/* ignored drawText("PV")\n'
                  '   ignored setValue() */ drawText("S");\n'
                  'String quoted = "getInput("; char c = \'/\';\n'
                  'setValue(42);\n')
        row = contract_fingerprint(SOURCE_PREFIX + "FB_RS.java", source.encode())
        self.assertEqual(["R", "S"], [label["value"] for label in row["literal_labels"]])
        self.assertEqual([1, 3], [label["line"] for label in row["literal_labels"]])
        self.assertEqual(["drawText", "drawText", "setValue"],
                         [call["name"] for call in row["method_calls"]])
        self.assertEqual([1, 3, 5], [call["line"] for call in row["method_calls"]])

    def test_no_marker_does_not_invent_timer_semantics(self):
        path, digest = self.archive({SOURCE_PREFIX + "Unknown.java": "class Unknown { int a; }"})
        result = report_from_zip(path, digest)
        self.assertEqual([], result["markers_present"])
        self.assertEqual([], result["evidence"])
        self.assertIn("TON", result["markers_absent"])


if __name__ == "__main__":
    unittest.main()
