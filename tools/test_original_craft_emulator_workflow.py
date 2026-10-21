"""Guard the original Craft probe against unnecessary reconstruction work."""
from pathlib import Path
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/original-craft-emulator.yml"
RUNNER = ROOT / "tools/run_original_craft_emulator_probe.sh"
RELEASE_SHA256 = "11464db76f59393ccf698f57e60af1756756ffc78cb315d43052ae92adb50d4f"


class OriginalCraftWorkflowTests(unittest.TestCase):
    def test_probe_downloads_and_verifies_release_without_rebuilding(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("KaScada.Smart.Craft.1.0.7.08.02.2026.apk", workflow)
        self.assertIn(RELEASE_SHA256, workflow)
        self.assertNotIn("BLOODSKY-Kaskada-decompiled", workflow)
        self.assertNotIn("apktool", workflow.lower())
        self.assertNotIn("Rebuild original APK", workflow)
        self.assertNotIn("KaScada-Craft-1.0.7.apk", workflow)
        self.assertIn("path: build-reports/original-craft-1.0.7/", workflow)

    def test_runner_uses_exact_release_and_fails_closed(self):
        runner = RUNNER.read_text(encoding="utf-8")
        self.assertIn("adb install -r build/KaScada-Smart-Craft-1.0.7-release.apk", runner)
        self.assertIn("KASCADA_PROBE_APK_SOURCE=original-release", runner)
        self.assertIn(RELEASE_SHA256, runner)
        self.assertIn("exit 1", runner)
        self.assertNotIn("falling back", runner.lower())
        self.assertNotIn("rebuilt-debug-signed", runner)
        result = subprocess.run(
            ["bash", "-n", str(RUNNER)],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_runner_and_regression_test_trigger_probe_workflow(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("'tools/run_original_craft_emulator_probe.sh'", workflow)
        self.assertIn("'tools/test_original_craft_emulator_workflow.py'", workflow)

    def test_prs_are_quick_and_only_manual_dispatch_can_request_full(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("profile: full", workflow)
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.profile == 'full'", workflow)
        self.assertNotIn("startsWith(github.head_ref", workflow)
        self.assertIn("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", workflow)


if __name__ == "__main__":
    unittest.main()
