"""Regression tests for the isolated cloud AI pilot safety contract."""
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "tools" / "check_cloud_ai_pilot.py"


class CloudAiPilotTests(unittest.TestCase):
    def test_preflight_safety_contract(self):
        result = subprocess.run(
            [sys.executable, str(CHECKER)],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=15,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_preflight_remains_read_only(self):
        workflow = (ROOT / ".github/workflows/cloud-ai-preflight.yml").read_text()
        self.assertIn("contents: read", workflow)
        self.assertIn("pull_request:", workflow)
        self.assertNotIn("pull_request_target:", workflow)
        self.assertNotIn("contents: write", workflow)
        self.assertNotIn("secrets.", workflow)


if __name__ == "__main__":
    unittest.main()
