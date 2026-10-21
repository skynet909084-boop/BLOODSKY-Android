import unittest
from pathlib import Path

from original_craft_probe_contract import ProbeBudget, ProbeDeadlineExceeded, quick_smoke_passes

ROOT = Path(__file__).resolve().parents[1]


class ProbeBudgetTests(unittest.TestCase):
    def test_budget_bounds_calls_and_reports_expiry(self):
        now = [10.0]
        budget = ProbeBudget(5.0, clock=lambda: now[0])
        self.assertEqual(5.0, budget.check())
        now[0] += 4.5
        self.assertAlmostEqual(0.5, budget.check())
        now[0] += 0.5
        with self.assertRaises(ProbeDeadlineExceeded):
            budget.check()


class QuickSmokeEvidenceTests(unittest.TestCase):
    def test_pass_requires_observed_screenshots_and_validated_actions(self):
        evidence = {
            "launcher_observed": True,
            "editor_observed": True,
            "screenshot_verified": True,
            "guided_goal_reached": True,
            "action_results": {
                "project_created": True,
                "palette_opened": True,
                "component_properties_opened": True,
            },
        }
        self.assertTrue(quick_smoke_passes(evidence))
        evidence["action_results"]["component_properties_opened"] = False
        self.assertFalse(quick_smoke_passes(evidence))

    def test_timeout_or_missing_visual_evidence_cannot_pass(self):
        evidence = {
            "launcher_observed": True,
            "editor_observed": True,
            "screenshot_verified": True,
            "guided_goal_reached": True,
            "action_results": {
                "project_created": True,
                "palette_opened": True,
                "component_properties_opened": True,
            },
        }
        evidence["runtime_limit_hit"] = True
        self.assertFalse(quick_smoke_passes(evidence))
        evidence["runtime_limit_hit"] = False
        evidence["screenshot_verified"] = False
        self.assertFalse(quick_smoke_passes(evidence))


class WorkflowSpeedContractTests(unittest.TestCase):
    def test_emulator_profile_is_manual_until_final_acceptance(self):
        workflow = (ROOT / ".github/workflows/original-craft-emulator.yml").read_text()
        trigger = workflow.split("on:", 1)[1].split("permissions:", 1)[0]
        self.assertIn("workflow_dispatch:", trigger)
        self.assertIn("options: [quick, full]", trigger)
        self.assertNotIn("pull_request:", trigger)
        self.assertIn("cancel-in-progress: false", workflow)

    def test_exact_apk_is_preferred_and_fallback_is_pinned(self):
        workflow = (ROOT / ".github/workflows/original-craft-emulator.yml").read_text()
        self.assertIn("11464db76f59393ccf698f57e60af1756756ffc78cb315d43052ae92adb50d4f", workflow)
        self.assertIn("steps.original-apk.outputs.ready != 'true'", workflow)
        self.assertIn("316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd", workflow)
        self.assertNotIn("actions/download-artifact@v4", workflow)
        self.assertNotIn("38600957172", workflow)

    def test_quick_profile_has_five_minute_budget_and_fails_closed(self):
        source = (ROOT / "tools/original_craft_emulator_probe.py").read_text()
        workflow = (ROOT / ".github/workflows/original-craft-emulator.yml").read_text()
        self.assertIn("MAX_RUNTIME_SECONDS = 5 * 60", source)
        self.assertIn('"emulator_startup_seconds": startup_seconds', source)
        self.assertIn('"interaction_seconds": round(time.time() - started, 1)', source)
        self.assertIn('"quick_smoke_passed": quick_smoke_passes', source)
        self.assertIn('if probe_error or (PROFILE == "quick" and not summary["quick_smoke_passed"]):', source)
        self.assertIn('timeout-minutes: ${{ github.event_name == \'workflow_dispatch\' && github.event.inputs.profile == \'full\' && 360 || 35 }}', workflow)
        self.assertIn("Mark emulator startup", workflow)


if __name__ == "__main__":
    unittest.main()
