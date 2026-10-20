"""Regression tests for the isolated cloud AI pilot safety contract."""
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "tools" / "check_cloud_ai_pilot.py"


class CloudAiPilotTests(unittest.TestCase):
    def test_retired_pilot_disabled_state_contract(self):
        result = subprocess.run(
            [sys.executable, str(CHECKER)],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=15,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_retired_agent_workflows_are_absent_and_status_is_explicit(self):
        for name in ("cloud-ai-preflight.yml", "cloud-ai-agent.yml"):
            self.assertFalse((ROOT / ".github/workflows" / name).exists(), name)
        status = (ROOT / "docs/FREELLMAPI_CLOUD_PILOT.md").read_text().lower()
        self.assertIn("pilot is paused", status)
        self.assertIn("workflows were removed", status)
        self.assertIn("no code-writing agent runs automatically", status)


if __name__ == "__main__":
    unittest.main()
