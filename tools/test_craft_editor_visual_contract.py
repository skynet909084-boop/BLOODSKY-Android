"""Fast source-contract regressions for the unified Craft editor.

These tests intentionally check stable high-level behavior, not pixel parity.
Visual parity still requires original-reference screenshots and emulator evidence.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT
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

    def test_fbd_menu_rejects_stale_screen_mode_and_selection(self):
        fbd = self.source.split("private void showFbdBlockContextMenu(", 1)[1].split(
            "private void showStandardWidgetContextMenu()", 1)[0]
        for required in (
            "operatorMode || editor == null",
            "currentScreenIndex < 0",
            "final EditorCanvas menuCanvas = editor",
            "final int menuScreenIndex = currentScreenIndex",
            "final Screen menuScreen = screens.get(currentScreenIndex)",
            "new ArrayList<>(widgets)",
            "new ArrayList<>(editor.selectedWidgets)",
            "operatorMode || editor != menuCanvas",
            "currentScreenIndex != menuScreenIndex",
            "screens.get(currentScreenIndex) != menuScreen",
            "!sameWidgetOrder(menuWidgetOrder)",
            "editor.selected != menuFocusedIndex",
            "!editor.selectedWidgets.equals(menuSelection)",
            "widgets.get(blockIndex) != widget",
            "FBD изменён: выбери блок заново",
        ):
            with self.subTest(required=required):
                self.assertIn(required, fbd)

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
        self.assertIn("pasteWidget()", menu)
        self.assertIn('primaryLabel = "Настройки надписи"', menu)
        self.assertIn('primaryLabel = "Настройки динамического текста"', menu)
        self.assertIn('primaryLabel = "Настройки пороговой шкалы"', menu)
        self.assertIn('primaryLabel = "Настройки градуированной шкалы"', menu)
        self.assertIn("widgets.get(editor.selected) != selectedWidget", menu)
        self.assertIn("final int menuScreenIndex = currentScreenIndex", menu)
        self.assertIn("final EditorCanvas menuCanvas = editor", menu)
        self.assertIn("editor != menuCanvas", menu)
        self.assertIn("currentScreenIndex != menuScreenIndex", menu)
        self.assertIn("final Screen menuScreen = screens.get(currentScreenIndex)", menu)
        self.assertIn("screens.get(currentScreenIndex) != menuScreen", menu)
        self.assertIn("operatorMode || editor == null", menu)
        self.assertIn('"Элемент изменён: выбери его заново"', menu)
        self.assertIn("row.setFocusable(true)", menu)
        self.assertIn("row.setClickable(true)", menu)
        self.assertIn("row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES)", menu)
        self.assertIn('row.setContentDescription(selectedWidget.type + ": " + labels[i])', menu)

    def test_original_copy_and_align_assets_in_context_menu(self):
        menu = self.source.split("private void showStandardWidgetContextMenu()", 1)[1].split(
            "private void ", 1)[0]
        self.assertIn("R.drawable.kc_align_open_menu", menu)
        self.assertIn("R.drawable.kc_action_copy", menu)
        self.assertIn("R.drawable.kc_action_paste", menu)
        self.assertIn("sourceIcon.setContentDescription(null)", menu)
        self.assertIn("sourceIcon.setScaleType(android.widget.ImageView.ScaleType.FIT_XY)", menu)
        self.assertIn("new LinearLayout.LayoutParams(dp(40), dp(40))", menu)
        layout = (ROOT / "app/src/main/res/layout/kc_selection_actions.xml").read_text(
            encoding="utf-8")
        self.assertIn("@drawable/kc_action_copy", layout)
        self.assertIn("@drawable/kc_action_paste", layout)
        self.assertIn("@drawable/kc_align_open_menu", layout)

    def test_indicator_artwork_is_accessible_without_advanced_inspector(self):
        menu = self.source.split("private void showStandardWidgetContextMenu()", 1)[1].split(
            "private void ", 1)[0]
        self.assertIn("OriginalIndicator.supports(selectedWidget.type)", menu)
        self.assertIn('"Изображения ВКЛ / ВЫКЛ"', menu)
        self.assertIn("showIndicatorImages(selectedWidget)", menu)
        self.assertIn("currentScreenIndex < 0", menu)
        self.assertIn("editor != menuCanvas", menu)
        self.assertIn("screens.get(currentScreenIndex) != menuScreen", menu)
        self.assertIn("widgets.get(editor.selected) != selectedWidget", menu)

    def test_context_actions_reject_changed_group_selection(self):
        menu = self.source.split("private void showStandardWidgetContextMenu()", 1)[1].split(
            "private void ", 1)[0]
        self.assertIn("new ArrayList<>(editor.selectedWidgets)", menu)
        self.assertIn("final int menuFocusedIndex = editor.selected", menu)
        self.assertIn("final int menuWidgetCount = widgets.size()", menu)
        self.assertEqual(menu.count("widgets.size() != menuWidgetCount"), 2)
        self.assertIn("new ArrayList<>(widgets)", menu)
        self.assertEqual(menu.count("!sameWidgetOrder(menuWidgetOrder)"), 2)
        self.assertIn("private boolean sameWidgetOrder(", self.source)
        self.assertIn("widgets.get(i) != originals.get(i)", self.source)
        self.assertEqual(menu.count("editor.selected != menuFocusedIndex"), 2)
        self.assertEqual(menu.count("!editor.selectedWidgets.equals(menuSelection)"), 2)
        self.assertIn("menuSelectionWidgets.add(widgets.get(selectionIndex))", menu)
        self.assertEqual(menu.count("sameSelectedWidgetObjects(menuSelection, menuSelectionWidgets)"), 2)
        self.assertIn("private boolean sameSelectedWidgetObjects(", self.source)
        self.assertIn("widgets.get(index) != originals.get(i)", self.source)
        self.assertIn("copySelectedWidget()", menu)
        self.assertIn("deleteSelectedWidget()", menu)
        self.assertIn("pasteWidget()", menu)

    def test_paste_rejects_operator_mode_and_missing_screen(self):
        source = self.source.split("private void pasteWidget()", 1)[1].split(
            "private void ", 1)[0]
        self.assertIn("if (operatorMode || editor == null", source)
        self.assertIn("currentScreenIndex >= screens.size()", source)
        self.assertIn("float width = editor.getWidth()", source)
        self.assertIn("float height = editor.getHeight()", source)
        self.assertIn("WidgetClipboard.place(", source)

    def test_operator_mode_blocks_copy_and_delete(self):
        copy = self.source.split("private boolean copySelectedWidgetToClipboard()", 1)[1].split(
            "private ", 1)[0]
        delete = self.source.split("private void deleteSelectedWidget()", 1)[1].split(
            "public static JSONObject widgetToJson(", 1)[0]
        self.assertIn("if (operatorMode) return false;", copy)
        self.assertIn("if (operatorMode) return;", delete)

    def test_runtime_never_opens_editor_context_actions(self):
        menu = self.source.split("private void showStandardWidgetContextMenu()", 1)[1].split(
            "private void ", 1)[0]
        self.assertIn("if (operatorMode || editor == null", menu)

    def test_destructive_action_is_still_last_and_red(self):
        menu = self.source.split("private void showStandardWidgetContextMenu()", 1)[1].split(
            "private void ", 1)[0]
        self.assertIn('"Выравнивание", "Копировать", "Вставить", "Удалить"', menu)
        self.assertIn("i == 5 ? Color.rgb(235, 105, 90)", menu)

    def test_delayed_editor_actions_require_original_screen_canvas_and_order(self):
        helper = self.source.split("private boolean isEditorDialogContextCurrent(", 1)[1].split(
            "private boolean sameSelectedWidgetObjects(", 1)[0]
        for required in (
            "!operatorMode", "editor != null", "editor == originalCanvas",
            "originalScreenIndex >= 0", "originalScreenIndex == currentScreenIndex",
            "currentScreenIndex < screens.size()",
            "screens.get(currentScreenIndex) == originalScreen",
            "sameWidgetOrder(originalWidgetOrder)",
        ):
            with self.subTest(required=required):
                self.assertIn(required, helper)

    def test_fbd_wire_inversion_and_disconnection_are_screen_scoped(self):
        inversion = self.source.split(
            "private void showFbdInversionDialog(int sourceFilter)", 1)[1].split(
            "private void showFbdDisconnectDialog()", 1)[0]
        disconnect = self.source.split(
            "private void showFbdDisconnectDialog(int sourceFilter)", 1)[1].split(
            "/** Offer pin-specific wiring", 1)[0]
        for body in (inversion, disconnect):
            self.assertIn("operatorMode || editor == null", body)
            self.assertIn("final Screen screen = screens.get(currentScreenIndex)", body)
            self.assertIn("final EditorCanvas dialogCanvas = editor", body)
            self.assertIn("new ArrayList<>(widgets)", body)
            self.assertIn("isEditorDialogContextCurrent(dialogCanvas, screen, dialogScreenIndex,", body)
            self.assertIn("screen.connections.contains(edge)", body)
            self.assertIn("edge.source >= widgets.size()", body)
            self.assertIn("edge.target >= widgets.size()", body)
            self.assertIn("Экран FBD изменён: открой меню заново", body)
        self.assertIn("!isInvertibleFbdBoolWire(edge, from.type, to.type)", inversion)
        self.assertIn("commitFbdWireEdit(screen,", disconnect)

    def test_two_stage_fbd_pin_picker_checks_context_before_each_stage(self):
        picker = self.source.split("private void chooseFbdPins(int source, int target)", 1)[1].split(
            "/** A named terminal's physical Y position", 1)[0]
        self.assertIn("operatorMode || editor == null", picker)
        self.assertIn("final Screen screenAtSelectionObject", picker)
        self.assertIn("final EditorCanvas canvasAtSelection", picker)
        self.assertIn("final ArrayList<Widget> widgetOrderAtSelection", picker)
        self.assertEqual(picker.count("isEditorDialogContextCurrent(canvasAtSelection,"), 2)
        self.assertIn("sourceChoice < 0 || sourceChoice >= sourcePins.length", picker)
        self.assertIn("targetChoice < 0 || targetChoice >= compatiblePins.length", picker)
        self.assertIn("widgets.get(source) != sourceAtSelection", picker)
        self.assertIn("widgets.get(target) != targetAtSelection", picker)
        self.assertIn("canUseUnpinnedFbdFallback(sourceAtSelection, targetAtSelection)", picker)
        self.assertIn("hasCompatibleKnownFbdPins", picker)

    def test_alignment_dialog_guards_selection_and_rolls_back_on_save_failure(self):
        dialog = self.source.split("private void showAlignDialog()", 1)[1].split(
            "private Button legacyToolbarButton(", 1)[0]
        self.assertIn("operatorMode || editor == null", dialog)
        self.assertEqual(dialog.count("isEditorDialogContextCurrent(dialogCanvas,"), 2)
        self.assertEqual(dialog.count("editor.selected != dialogFocusedIndex"), 2)
        self.assertEqual(dialog.count("!editor.selectedWidgets.equals(dialogSelection)"), 2)
        single = self.source.split("private void alignSelectedWidget(String alignMode)", 1)[1].split(
            "private void alignSelectedWidgets(String alignMode)", 1)[0]
        group = self.source.split("private void alignSelectedWidgets(String alignMode)", 1)[1].split(
            "private void showAlignDialog()", 1)[0]
        self.assertIn("operatorMode || editor == null", single)
        self.assertIn("operatorMode || editor == null", group)
        self.assertIn("final float beforeX = w.x, beforeY = w.y", single)
        self.assertIn("if (!persistProjectNow(true))", single)
        self.assertIn("w.x = beforeX;", single)
        self.assertIn("w.y = beforeY;", single)
        self.assertIn("if (!persistProjectNow(true))", group)
        for axis in ("x", "y", "w", "h"):
            self.assertIn("widget." + axis + " = geometry[i][", group)
        self.assertIn("CanvasGroupAlignment.apply(geometry, alignMode)", group)

    def test_nested_macro_output_dialog_cannot_update_replaced_graph(self):
        dialog = self.source.split("private void chooseNestedMacroOutput(int blockIndex, Widget block)", 1)[1].split(
            "private void openNestedMacroGraphEditor(", 1)[0]
        self.assertIn("operatorMode || editor == null", dialog)
        self.assertIn("final NestedMacroEditSession sessionAtSelection = nestedMacroEdit", dialog)
        self.assertIn("isEditorDialogContextCurrent(canvasAtSelection, screenAtSelection,", dialog)
        self.assertIn("nestedMacroEdit != sessionAtSelection", dialog)
        self.assertIn("widgets.get(blockIndex) != block", dialog)
        self.assertIn("!contains(knownFbdPins(block, true), pins[which])", dialog)
        self.assertIn("sessionAtSelection.outputNode = previousNode", dialog)
        self.assertIn("sessionAtSelection.outputWidget = previousWidget", dialog)
        self.assertIn("sessionAtSelection.outputPin = previousPin", dialog)

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
