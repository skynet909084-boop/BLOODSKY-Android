import unittest
import tempfile
from pathlib import Path

from original_craft_probe_state import load_checkpoint, remember_state_signature, write_checkpoint, configured_state_limit


class OriginalCraftProbeStateTests(unittest.TestCase):
    def test_full_state_limit_can_be_capped_without_expanding_quick_profile(self):
        self.assertEqual(12, configured_state_limit("quick"))
        self.assertEqual(12, configured_state_limit("quick", "300"))
        self.assertEqual(300, configured_state_limit("full"))
        for cap in ("3", "10", "25", "300"):
            with self.subTest(cap=cap):
                self.assertEqual(int(cap), configured_state_limit("full", cap))
        for cap in ("0", "301", "many"):
            with self.subTest(cap=cap), self.assertRaisesRegex(ValueError, "max_states"):
                configured_state_limit("full", cap)

    def test_signature_is_registered_once_and_duplicate_is_rejected(self):
        seen = set()
        self.assertTrue(remember_state_signature(seen, "screen-a"))
        self.assertFalse(remember_state_signature(seen, "screen-a"))
        self.assertTrue(remember_state_signature(seen, "screen-b"))
        self.assertEqual({"screen-a", "screen-b"}, seen)

    def test_atomic_checkpoint_round_trip_preserves_frontier_and_provenance(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "checkpoint.json"
            source = {
                "profile": "full",
                "apk_sha256": "abc123",
                "visited_signatures": ["screen-a"],
                "state_rows": [{"state": "state-000", "path": []}],
                "transition_rows": [{"action": "open editor"}],
                "pending_paths": [[{"kind": "tap", "label": "open editor"}]],
            }
            write_checkpoint(path, source)
            self.assertEqual(source["pending_paths"], load_checkpoint(path, apk_sha256="abc123")["pending_paths"])
            self.assertTrue(path.with_name("checkpoint.json.tmp").exists() is False)

    def test_resume_rejects_different_apk_profile_or_incomplete_data(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "checkpoint.json"
            write_checkpoint(path, {
                "profile": "full", "apk_sha256": "abc123", "visited_signatures": [],
                "state_rows": [], "transition_rows": [], "pending_paths": [],
            })
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                load_checkpoint(path, apk_sha256="different")
            with self.assertRaisesRegex(ValueError, "profile mismatch"):
                load_checkpoint(path, apk_sha256="abc123", profile="quick")

    def test_interrupted_frontier_is_restored_then_can_be_committed(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "checkpoint.json"
            pending = [[{"kind": "tap", "label": "open project"}],
                       [{"kind": "tap", "label": "open settings"}]]
            write_checkpoint(path, {
                "profile": "full", "apk_sha256": "abc123", "visited_signatures": ["root"],
                "state_rows": [{"state_signature": "root"}], "transition_rows": [],
                "pending_paths": pending,
            })
            recovered = load_checkpoint(path, apk_sha256="abc123")
            self.assertEqual(pending, recovered["pending_paths"])
            self.assertEqual(["root"], recovered["visited_signatures"])
            recovered["pending_paths"] = pending[1:]
            recovered["visited_signatures"].append("project")
            write_checkpoint(path, recovered)
            resumed = load_checkpoint(path, apk_sha256="abc123")
            self.assertEqual(pending[1:], resumed["pending_paths"])
            self.assertEqual(["root", "project"], resumed["visited_signatures"])


if __name__ == "__main__":
    unittest.main()
