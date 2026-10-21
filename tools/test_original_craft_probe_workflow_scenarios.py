"""Ensure quick scenarios remain selectable without making PR checks exhaustive."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/original-craft-emulator.yml"
PROBE = ROOT / "tools/original_craft_emulator_probe.py"


class ProbeWorkflowScenarioTests(unittest.TestCase):
    def test_workflow_exposes_short_scenarios_and_defaults_to_one(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("quick_scenario:", workflow)
        self.assertIn("options: [project-create, hmi-palette, hmi-id17-properties]", workflow)
        self.assertIn("default: hmi-id17-properties", workflow)
        self.assertIn("--scenario", workflow)
        self.assertIn("resume_run_id:", workflow)
        self.assertIn("actions/download-artifact@v4", workflow)
        self.assertIn("max_states:", workflow)
        self.assertIn("options: ['3', '10', '25', '300']", workflow)
        self.assertIn("KASCADA_PROBE_MAX_STATES", workflow)
        self.assertIn("--resume", workflow)
        self.assertIn("tools/test_original_craft_probe_workflow_scenarios.py", workflow)

    def test_scenario_pass_is_not_a_k107_or_parity_pass(self):
        probe = PROBE.read_text(encoding="utf-8")
        self.assertIn('"k107_case_status": "WAITING"', probe)
        self.assertIn('"scenario_status"', probe)
        self.assertIn("not a K107 acceptance PASS or BLOODSKY parity claim", probe)
        self.assertIn("state_signature", probe)
        self.assertIn('raise SystemExit("quick scenario did not reach its required observed endpoint")', probe)


if __name__ == "__main__":
    unittest.main()
