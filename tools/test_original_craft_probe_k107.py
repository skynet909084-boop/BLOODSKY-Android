"""Tests for cautious K107 evidence links."""
import json
import tempfile
import unittest
from pathlib import Path

from original_craft_probe_k107 import (
    EXPECTED_APK_SHA256, build_k107_report, load_case_matrix, map_action_to_case_ids,
)


def sample_matrix():
    return {
        "apk_sha256": EXPECTED_APK_SHA256,
        "test_cases": [
            {"id": "K107-0001", "feature": "Create a new project",
             "procedure": "Нажать bGoToCanvas, проверить диалог."},
            {"id": "K107-0002", "feature": "Upload a project",
             "procedure": "Нажать button3."},
        ],
    }


class K107MappingTests(unittest.TestCase):
    def test_maps_only_exact_resource_id_mentioned_in_case_steps(self):
        cases = sample_matrix()["test_cases"]
        action = {"kind": "tap", "node": {"resource_id": "ru.kascada.modbus:id/bGoToCanvas"}}
        self.assertEqual(["K107-0001"], map_action_to_case_ids(action, cases))
        action["node"]["resource_id"] = "ru.kascada.modbus:id/bGoToCanvass"
        self.assertEqual([], map_action_to_case_ids(action, cases))

    def test_matrix_validates_hash_count_and_sequential_case_ids(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "matrix.json"
            matrix = sample_matrix()
            matrix["test_cases"] = [
                {"id": f"K107-{i:04d}", "feature": "", "procedure": ""}
                for i in range(1, 551)
            ]
            path.write_text(json.dumps(matrix), encoding="utf-8")
            self.assertEqual(550, len(load_case_matrix(path)))
            matrix["test_cases"][0]["id"] = "K107-0002"
            path.write_text(json.dumps(matrix), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "missing, duplicated"):
                load_case_matrix(path)

    def test_observation_references_cases_and_evidence_without_marking_them_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            (out / "state-001.xml").write_text("<hierarchy />", encoding="utf-8")
            cases = sample_matrix()["test_cases"]
            states = [{"state": "state-001", "k107_case_ids": ["K107-0001"],
                       "path": ["Create project"]}]
            report = build_k107_report(cases, states, out)
            self.assertEqual("WAITING", report["case_statuses"][0]["status"])
            self.assertEqual(["obs-state-001"], report["case_statuses"][0]["evidence_refs"])
            self.assertEqual("state-001.xml", report["observations"][0]["evidence"]["accessibility_tree"])
            self.assertNotIn("screenshot", report["observations"][0]["evidence"])
            self.assertEqual(2, len(report["remaining_case_ids"]))


if __name__ == "__main__":
    unittest.main()
