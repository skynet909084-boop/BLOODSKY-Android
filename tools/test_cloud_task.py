"""Task selection, context and invocation boundaries for free FBD coding tasks."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock

from cloud_coding_agent import read_context
from run_cloud_task import PROFILES, execute, plan

ROOT = Path(__file__).resolve().parents[1]


class CloudTaskTests(unittest.TestCase):
    def test_every_profile_has_available_bounded_source_context_and_unique_class(self):
        names = set()
        for profile in PROFILES:
            selected = plan(profile, "12345")
            self.assertLessEqual(len(selected["task"]), 4000)
            self.assertIn(selected["class_name"], selected["task"])
            self.assertNotIn(selected["class_name"], names)
            names.add(selected["class_name"])
            self.assertTrue(read_context(ROOT, selected["context"]))
            self.assertNotEqual(selected["class_name"], plan(profile, "12346")["class_name"])

    def test_invalid_selection_cannot_supply_paths_or_commands(self):
        for profile, run_id, task in [("../../secret", "1", ""), ("properties", "1; echo bad", ""),
                                      ("properties", "", ""), ("properties", "1", "x" * 3601)]:
            with self.assertRaises(ValueError):
                plan(profile, run_id, task)

    def test_custom_task_is_data_and_cannot_change_context_or_mode(self):
        task = "$(touch forbidden)\n--mode live\n`arbitrary-command`"
        selected = plan("fbd-duration", "123", task)
        selected["context"].append("unused.java")
        # A caller cannot mutate the reviewed profile definition by editing a plan.
        self.assertNotIn("unused.java", plan("fbd-duration", "124")["context"])
        selected = plan("fbd-duration", "123", task)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "result"
            observed = {}
            def run(command, **kwargs):
                observed["task"] = Path(command[command.index("--task-file") + 1]).read_text()
                observed["command"] = command
                self.assertEqual(ROOT, kwargs["cwd"])
                self.assertTrue(kwargs["check"])
                self.assertNotIn("shell", kwargs)
            execute(ROOT, selected, output, runner=run)
            self.assertIn(task, observed["task"])
            self.assertNotIn(task, observed["command"])
            self.assertEqual("anonymous", observed["command"][observed["command"].index("--mode") + 1])
            self.assertEqual(selected, json.loads((output / "task-plan.json").read_text()))
            self.assertFalse(Path(observed["command"][observed["command"].index("--task-file") + 1]).exists())

    def test_missing_context_prevents_any_router_invocation(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = Mock()
            with self.assertRaises(ValueError):
                execute(Path(directory), plan("fbd-duration", "123"), Path(directory) / "out", runner)
            runner.assert_not_called()


if __name__ == "__main__":
    unittest.main()
