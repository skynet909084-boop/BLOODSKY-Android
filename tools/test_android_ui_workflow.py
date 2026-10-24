"""Regression tests: Android emulator is a release-acceptance tool, not a dev gate."""
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).resolve().parents[1] / '.github/workflows/android-ui-smoke.yml'


class AndroidUiWorkflowProfileTests(unittest.TestCase):
    def test_no_android_on_feature_or_integration_pull_requests(self):
        workflow = WORKFLOW.read_text(encoding='utf-8')
        pr_trigger = workflow.split('  pull_request:', 1)[1].split('\n\npermissions:', 1)[0]
        self.assertIn('branches: [main]', pr_trigger)
        self.assertIn('types: [opened, reopened, synchronize, ready_for_review, labeled]', pr_trigger)
        self.assertNotIn("github.event.action == 'ready_for_review'", workflow)
        self.assertIn("contains(github.event.pull_request.labels.*.name, 'release-candidate')", workflow)
        self.assertNotIn('craft-server-editor', workflow)
        job_header = workflow.split('jobs:', 1)[1].split('    steps:', 1)[0]
        self.assertIn('    if: github.event_name != \'pull_request\'', job_header)
        self.assertNotIn("default: quick", workflow)

    def test_manual_release_profiles_remain_available(self):
        workflow = WORKFLOW.read_text(encoding='utf-8')
        dispatch = workflow.split('  workflow_dispatch:', 1)[1].split('  pull_request:', 1)[0]
        profile = next(line for line in workflow.splitlines() if 'BLOODSKY_UI_PROFILE:' in line)
        self.assertIn('default: full', dispatch)
        self.assertIn('options: [full, quick]', dispatch)
        self.assertIn("github.event_name == 'workflow_dispatch'", profile)
        self.assertIn("inputs.ui_profile == 'quick'", profile)
        self.assertIn("|| 'full'", profile)
        self.assertIn('script: bash tools/run_android_ui_profile.sh', workflow)

    def test_release_main_gate_and_diagnostics_preserved(self):
        workflow = WORKFLOW.read_text(encoding='utf-8')
        self.assertIn('  pull_request:\n    branches: [main]', workflow)
        self.assertNotIn('\n  push:\n', workflow)
        self.assertIn('timeout-minutes: 35', workflow)
        self.assertIn('Save UI screenshots and diagnostics', workflow)
        self.assertIn('retention-days: 14', workflow)
        self.assertIn('cancel-in-progress: true', workflow)


if __name__ == '__main__':
    unittest.main()
