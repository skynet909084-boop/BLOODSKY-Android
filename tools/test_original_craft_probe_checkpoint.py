"""Deterministic coverage for resumable original Craft probe checkpoints."""
import tempfile
import unittest
from pathlib import Path

from original_craft_probe_checkpoint import (
    atomic_write_json, checkpoint_from_run, load_checkpoint, new_checkpoint,
)


class ProbeCheckpointTests(unittest.TestCase):
    def test_atomic_round_trip_preserves_frontier_paths_and_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "checkpoint.json"
            row = {"state": "state-000", "signature": "sig-a", "path": ["Open menu"]}
            data = checkpoint_from_run(
                [[{"kind": "tap", "label": "Next", "node": {"x": 1, "y": 2}}]],
                {"sig-a"}, [row], [{"from": "state-000", "action": "Open menu"}],
                [[]], "original-release", "a" * 64, False,
            )
            atomic_write_json(path, data)
            restored = load_checkpoint(path, "original-release", "a" * 64)
            self.assertEqual(data, restored)

    def test_interruption_resume_continues_pending_paths_without_losing_rows(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "checkpoint.json"
            initial = new_checkpoint("original-release", "b" * 64)
            initial.update({"frontier": [[{"label": "Next"}]],
                            "visited_signatures": ["sig-root"],
                            "states": [{"state": "state-000", "signature": "sig-root"}],
                            "transitions": [{"from": "state-000", "action": "Next"}]})
            atomic_write_json(path, initial)
            resumed = load_checkpoint(path, "original-release", "b" * 64)
            self.assertEqual([[{"label": "Next"}]], resumed["frontier"])
            self.assertEqual("sig-root", resumed["states"][0]["signature"])
            resumed["frontier"].clear()
            resumed["states"].append({"state": "state-001", "signature": "sig-next"})
            atomic_write_json(path, resumed)
            final = load_checkpoint(path, "original-release", "b" * 64)
            self.assertEqual(["sig-root", "sig-next"], final["visited_signatures"])
            self.assertEqual(2, len(final["states"]))

    def test_duplicate_states_keep_first_confirmed_evidence_and_dedupe_signatures(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "checkpoint.json"
            data = new_checkpoint("reconstructed-debug", "c" * 64)
            data.update({"visited_signatures": ["same", "same"],
                         "states": [{"state": "confirmed", "signature": "same", "screenshot": "state-000.png"},
                                    {"state": "duplicate", "signature": "same", "screenshot": "state-001.png"}]})
            atomic_write_json(path, data)
            restored = load_checkpoint(path, "reconstructed-debug", "c" * 64)
            self.assertEqual(["same"], restored["visited_signatures"])
            self.assertEqual("confirmed", restored["states"][0]["state"])
            self.assertEqual(1, len(restored["states"]))

    def test_resume_rejects_mismatched_apk_provenance(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "checkpoint.json"
            atomic_write_json(path, new_checkpoint("original-release", "d" * 64))
            with self.assertRaisesRegex(ValueError, "provenance"):
                load_checkpoint(path, "reconstructed-debug", "d" * 64)


if __name__ == "__main__":
    unittest.main()
