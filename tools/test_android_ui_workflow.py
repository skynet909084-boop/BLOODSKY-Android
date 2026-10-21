import unittest
from pathlib import Path


class AndroidUiWorkflowProfileTests(unittest.TestCase):
    def test_craft_server_editor_pull_requests_run_full_ui_suite(self):
        workflow = (Path(__file__).resolve().parents[1] / '.github/workflows/android-ui-smoke.yml').read_text()
        profile = next(line for line in workflow.splitlines() if 'BLOODSKY_UI_PROFILE:' in line)
        job_gate = next(line for line in workflow.splitlines() if line.strip().startswith('if:'))
        self.assertIn("contains(github.head_ref, 'craft-server-editor')", job_gate)
        self.assertIn("contains(github.head_ref, 'craft-server-editor')", profile)
        self.assertIn("!contains(github.head_ref, 'craft-server-editor')", profile)
        self.assertIn("'quick' ||", profile)
        self.assertIn("'full')", profile)


if __name__ == '__main__':
    unittest.main()
