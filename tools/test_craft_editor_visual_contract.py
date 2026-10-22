"""Fast source-contract regressions for the unified Craft editor.

These tests intentionally check stable high-level behavior, not pixel parity.
Visual parity still requires original-reference screenshots and emulator evidence.
"""
from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[1]
          / "app/src/main/java/com/bloodsky/hmi/MainActivity.java")


class CraftEditorVisualContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.canvas = cls.source.split("private class EditorCanvas extends View", 1)[1]

    def test_canvas_uses_reference_backdrop_instead_of_white_fallback(self):
        self.assertIn("R.drawable.kc_main_background_small", self.canvas)
        self.assertIn("CraftCanvasVisualStyle.useOriginalBackdrop(", self.canvas)
        self.assertIn("canvas.drawBitmap(craftCanvasBackdrop", self.canvas)
        self.assertNotIn("setBackgroundColor(Color.WHITE)", self.canvas)

    def test_custom_project_canvas_background_is_not_overwritten(self):
        self.assertIn("backgroundImagePath", self.canvas)
        self.assertIn("canvas.drawColor(screenColor)", self.canvas)
        self.assertIn("canvas.drawBitmap(selectedBackground", self.canvas)

    def test_default_canvas_grid_remains_hidden_in_editor(self):
        self.assertIn('optBoolean("bloodskyShowDesignGrid", false)', self.canvas)
        self.assertIn("CraftCanvasVisualStyle.showDesignGrid(", self.canvas)

    def test_fbd_context_actions_are_not_replaced_by_generic_widget_menu(self):
        dispatch = self.source.split("private void showWidgetContextMenu()", 1)[1].split(
            "private void showFbdBlockContextMenu(", 1)[0]
        self.assertIn("isFbd(widgets.get(editor.selected))", dispatch)
        self.assertIn("showFbdBlockContextMenu(editor.selected)", dispatch)
        self.assertIn("showStandardWidgetContextMenu()", dispatch)

    def test_standard_widget_menu_opens_properties(self):
        menu = self.source.split("private void showStandardWidgetContextMenu()", 1)[1].split(
            "private void ", 1)[0]
        self.assertIn('primaryLabel = "Настройки индикатора"', menu)
        self.assertIn('primaryLabel = "Настройки переключателя"', menu)
        self.assertIn('primaryLabel = "Настройки кнопки"', menu)
        self.assertIn('primaryLabel = "Настройки числа"', menu)
        self.assertIn('primaryLabel = "Настройки шкалы"', menu)
        self.assertIn('"Расширенные свойства"', menu)
        self.assertIn("editSelectedWidget()", menu)
        self.assertIn("editSelectedWidget(true)", menu)
        self.assertIn("showAlignDialog()", menu)
        self.assertIn("copySelectedWidget()", menu)
        self.assertIn("deleteSelectedWidget()", menu)
        self.assertIn('primaryLabel = "Настройки надписи"', menu)
        self.assertIn('primaryLabel = "Настройки динамического текста"', menu)
        self.assertIn('primaryLabel = "Настройки пороговой шкалы"', menu)
        self.assertIn('primaryLabel = "Настройки градуированной шкалы"', menu)
        self.assertIn("widgets.get(editor.selected) != selectedWidget", menu)
        self.assertIn("operatorMode || editor == null", menu)
        self.assertIn('"Элемент изменён: выбери его заново"', menu)
        self.assertIn("row.setFocusable(true)", menu)
        self.assertIn("row.setClickable(true)", menu)
        self.assertIn("row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES)", menu)
        self.assertIn('row.setContentDescription(selectedWidget.type + ": " + labels[i])', menu)

    def test_runtime_never_opens_editor_context_actions(self):
        menu = self.source.split("private void showStandardWidgetContextMenu()", 1)[1].split(
            "private void ", 1)[0]
        self.assertIn("if (operatorMode || editor == null", menu)

    def test_destructive_action_is_still_last_and_red(self):
        menu = self.source.split("private void showStandardWidgetContextMenu()", 1)[1].split(
            "private void ", 1)[0]
        self.assertIn('"Выравнивание", "Копировать", "Удалить"', menu)
        self.assertIn("i == 4 ? Color.rgb(235, 105, 90)", menu)

    def test_main_settings_reuse_existing_specialized_dialogs(self):
        editor = self.source.split("private void editSelectedWidget(boolean advanced)", 1)[1]
        for dialog in ("CraftListSettingsDialog", "CraftProgressBarSettingsDialog",
                       "CraftButtonSettingsDialog", "CraftNumericInputSettingsDialog",
                       "CraftLabelSettingsDialog", "CraftDynamicTextSettingsDialog",
                       "CraftThresholdGaugeSettingsDialog", "CraftGraduatedGaugeSettingsDialog"):
            self.assertIn(dialog + ".show(", editor)
        for handler in ("showOriginalLampSettings(w)", "showOriginalSwitchSettings(w)",
                        "showOriginalNumericIndicatorSettings(w)",
                        "showOriginalLinearSetterSettings(w)"):
            self.assertIn(handler, editor)


if __name__ == "__main__":
    unittest.main()
