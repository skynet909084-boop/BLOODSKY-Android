"""Regression tests for the feature-first Android UI workflow gate."""
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).resolve().parents[1] / '.github/workflows/android-ui-smoke.yml'


class AndroidUiWorkflowProfileTests(unittest.TestCase):
    def test_feature_pr_smoke_only_on_review_or_opt_in(self):
        workflow = WORKFLOW.read_text()
        gate = next(line for line in workflow.splitlines() if line.strip().startswith('if:'))
        self.assertIn("github.base_ref == 'main'", gate)
        self.assertIn("github.event.action == 'ready_for_review'", gate)
        self.assertIn("contains(github.event.pull_request.labels.*.name, 'android-smoke')", gate)
        self.assertNotIn('craft-server-editor', gate)
        self.assertNotIn("github.event.action != 'synchronize'", gate)
        self.assertIn('types: [opened, reopened, synchronize, ready_for_review, labeled]', workflow)

    def test_feature_profile_is_quick_main_and_manual_full_remain(self):
        workflow = WORKFLOW.read_text()
        profile = next(line for line in workflow.splitlines() if 'BLOODSKY_UI_PROFILE:' in line)
        self.assertIn("github.event_name == 'pull_request' && github.base_ref != 'main' && 'quick'", profile)
        self.assertIn("inputs.ui_profile == 'quick'", profile)
        self.assertIn("|| 'full'", profile)
        self.assertIn('default: quick', workflow)
        self.assertIn('timeout-minutes:', workflow)
        self.assertIn("github.base_ref != 'main' && 18 || 35", workflow)
        self.assertIn('script: bash tools/run_android_ui_profile.sh', workflow)

    def test_evidence_is_still_saved_and_main_release_is_checked(self):
        workflow = WORKFLOW.read_text()
        self.assertIn('branches: [main]', workflow)
        self.assertIn('Save UI screenshots and diagnostics', workflow)
        self.assertIn('retention-days: 14', workflow)
        self.assertIn('cancel-in-progress: true', workflow)


if __name__ == '__main__':
    unittest.main()
