"""Validate imported Craft 1.0.7 source inventory without claiming Android execution."""

import collections
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REFERENCE = ROOT / "docs" / "reference-kascada-107"
ORIGINAL_APK_SHA256 = "11464db76f59393ccf698f57e60af1756756ffc78cb315d43052ae92adb50d4f"


class Kascada107ReferenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with (REFERENCE / "ui_inventory.json").open(encoding="utf-8") as stream:
            cls.inventory = json.load(stream)
        with (REFERENCE / "full_function_test_matrix.json").open(encoding="utf-8") as stream:
            cls.matrix = json.load(stream)

    def test_source_identity_is_consistent(self):
        self.assertEqual(ORIGINAL_APK_SHA256, self.inventory["apk_sha256"])
        self.assertEqual(ORIGINAL_APK_SHA256, self.matrix["apk_sha256"])
        self.assertEqual("ru.kascada.modbus", self.inventory["package"])
        self.assertEqual(self.inventory["package"], self.matrix["android_package"])
        self.assertEqual("1.0.7", self.inventory["version_name"])
        self.assertEqual("107", str(self.inventory["version_code"]))

    def test_source_inventory_counts(self):
        self.assertEqual(6, len(self.inventory["main_menu"]))
        self.assertEqual(27, len(self.inventory["hmi_component_toolbar"]))
        self.assertEqual(40, len(self.inventory["logic_component_toolbar"]))
        self.assertEqual(18, len(self.inventory["popup_menus"]))
        self.assertGreaterEqual(len(self.inventory["dialog_layouts"]), 50)

    def test_case_ids_and_category_coverage(self):
        matrix = self.matrix
        cases = matrix["test_cases"]
        self.assertEqual(550, matrix["test_count"])
        self.assertEqual(matrix["test_count"], len(cases))
        self.assertEqual(len(cases), len({case["id"] for case in cases}))
        self.assertEqual(
            [f"K107-{number:04d}" for number in range(1, 551)],
            [case["id"] for case in cases],
        )
        counts = collections.Counter(
            "Сквозные сценарии" if case["category"].startswith("Сквозные сценарии")
            else case["category"]
            for case in cases
        )
        self.assertEqual(matrix["categories"], dict(counts))
        self.assertEqual(27, counts["HMI-компоненты"])
        self.assertEqual(40, counts["Logic/FB/VAR/EL"])

    def test_every_case_requires_real_runtime_evidence(self):
        statuses = {"WAITING_FOR_ANDROID_RUNTIME", "PASS", "FAIL", "BLOCKED"}
        for case in self.matrix["test_cases"]:
            with self.subTest(case_id=case["id"]):
                for required in ("category", "feature", "source", "procedure", "expected", "priority"):
                    self.assertTrue(case.get(required), required)
                self.assertIn(case["priority"], {"P0", "P1", "P2"})
                self.assertIn(case["stage"], statuses)
                if case["stage"] == "PASS":
                    self.assertNotEqual("STATIC_APK_RESOURCE_ONLY", case.get("evidence"))
                    self.assertTrue(case.get("runtime_artifact"))
                    self.assertTrue(case.get("observed_result"))
                if case["stage"] == "FAIL":
                    self.assertTrue(case.get("observed_result"))
                if case["stage"] == "BLOCKED":
                    self.assertTrue(case.get("blocked_reason"))


if __name__ == "__main__":
    unittest.main()
