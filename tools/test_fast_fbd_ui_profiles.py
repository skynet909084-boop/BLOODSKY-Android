"""CI gate regression: feature UI smoke stays focused and release coverage stays full."""
from pathlib import Path
import ast
import unittest

ROOT = Path(__file__).resolve().parents[1]


class FastFbdUiGateTests(unittest.TestCase):
    def test_quick_smoke_is_parseable_and_covers_core_editor_path(self):
        script = (ROOT / 'tools/android_ui_fbd_quick.py').read_text(encoding='utf-8')
        ast.parse(script)
        for evidence in (
                "Добавить: FBD И", "Добавить: FBD Таймер",
                "Свойства: FBD Таймер", "Время таймера, мс",
                "Инвертировать выход BOOL", "Связать FBD",
                "saved_connections()", "Инвертировать BOOL-связь FBD…",
                "Отменить связь FBD", "am', 'force-stop",
                "quick-fbd", "crash-log.txt", "result.json"):
            self.assertIn(evidence, script)
        self.assertIn("'2345'", script)
        self.assertNotIn("self.tap(description='Добавить: FBD CRAFT TIMER')", script)

    def test_feature_pr_emulator_is_not_restarted_for_each_push(self):
        workflow = (ROOT / '.github/workflows/android-ui-smoke.yml').read_text(encoding='utf-8')
        self.assertIn('types: [opened, reopened, synchronize, ready_for_review, labeled]', workflow)
        self.assertIn("github.event.action == 'ready_for_review'", workflow)
        self.assertIn("contains(github.event.pull_request.labels.*.name, 'android-smoke')", workflow)
        self.assertIn("github.event_name == 'pull_request' && github.base_ref != 'main' && 'quick'", workflow)
        self.assertIn("inputs.ui_profile == 'quick'", workflow)
        self.assertIn("|| 'full'", workflow)
        self.assertIn('default: quick', workflow)
        self.assertIn('BLOODSKY_UI_PROFILE', workflow)
        self.assertIn('script: bash tools/run_android_ui_profile.sh', workflow)
        runner = (ROOT / 'tools/run_android_ui_profile.sh').read_text()
        self.assertIn('exec python tools/android_ui_fbd_quick.py', runner)
        self.assertIn('exec python tools/android_ui_smoke.py', runner)
        self.assertIn('set -euo pipefail', runner)
        self.assertIn('branches: [main]', workflow)
        self.assertIn('cancel-in-progress: true', workflow)
        self.assertIn('Save UI screenshots and diagnostics', workflow)

    def test_full_ui_regressions_unmodified_and_legacy_media_retained(self):
        full = (ROOT / 'tools/android_ui_smoke.py').read_text(encoding='utf-8')
        for evidence in ("check_legacy_media_migration_on_device()",
                         "check_fbd_palette_and_properties(saved_widgets)",
                         "BLOODSKY", "result.json", "document-selection-ui.xml"):
            self.assertIn(evidence, full)


if __name__ == "__main__":
    unittest.main()
