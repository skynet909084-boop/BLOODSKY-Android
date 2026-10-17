"""CI gate regression: faster feature PRs must never remove release-wide UI coverage."""
from pathlib import Path
import ast
import unittest

ROOT = Path(__file__).resolve().parents[1]


class FastFbdUiGateTests(unittest.TestCase):
    def test_quick_smoke_is_parseable_and_has_actual_graph_checks(self):
        script = (ROOT / 'tools/android_ui_fbd_quick.py').read_text(encoding='utf-8')
        ast.parse(script)
        for evidence in (
                "saved_connections()", "Время таймера, мс", "Соединить с…",
                "Инвертировать связь BOOL…", "Отменить связь FBD",
                "timerPresetMs", "'2345'", "am', 'force-stop'",
                "quick-fbd", "crash-log.txt", "result.json"):
            self.assertIn(evidence, script)

    def test_feature_pr_fast_but_main_manual_push_still_full(self):
        workflow = (ROOT / '.github/workflows/android-ui-smoke.yml').read_text(encoding='utf-8')
        self.assertIn("github.base_ref != 'main' && 'quick'", workflow)
        self.assertIn("inputs.ui_profile == 'quick'", workflow)
        self.assertIn("|| 'full'", workflow)
        self.assertIn("default: full", workflow)
        self.assertIn('BLOODSKY_UI_PROFILE', workflow)
        self.assertIn('python tools/android_ui_fbd_quick.py', workflow)
        self.assertIn('python tools/android_ui_smoke.py', workflow)
        self.assertIn("script: |", workflow)
        self.assertIn("branches: [main]", workflow)
        self.assertIn("cancel-in-progress: true", workflow)
        self.assertIn("Save UI screenshots and diagnostics", workflow)

    def test_full_ui_regressions_unmodified_and_legacy_media_retained(self):
        full = (ROOT / 'tools/android_ui_smoke.py').read_text(encoding='utf-8')
        for evidence in ("check_legacy_media_migration_on_device()",
                         "check_fbd_palette_and_properties(saved_widgets)",
                         "BLOODSKY", "result.json", "document-selection-ui.xml"):
            self.assertIn(evidence, full)


if __name__ == "__main__":
    unittest.main()
