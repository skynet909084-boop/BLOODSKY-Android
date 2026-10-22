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
        self.assertIn("if (operatorMode || editor == null", delete)

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
        self.assertIn("commitFbdWireEdit(screen,", inversion)
        self.assertNotIn("fbdWireHistory.clear()", inversion)
        self.assertNotIn("fbdWireHistory.recordBefore(", inversion)
        self.assertIn("toggleFbdBooleanWireInversion(edge, from.type, to.type)", inversion)
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

    def test_clipboard_paste_is_atomic_before_history_invalidation(self):
        paste = self.source.split("private void pasteWidget()", 1)[1].split(
            "private void deleteSelectedWidget()", 1)[0]
        self.assertIn("operatorMode || editor == null", paste)
        self.assertIn("final Screen screen = screens.get(currentScreenIndex)", paste)
        self.assertIn("new ArrayList<>(widgets)", paste)
        self.assertIn("new ArrayList<>(screen.connections)", paste)
        self.assertIn("WidgetClipboard.place(", paste)
        self.assertIn("WidgetClipboard.placedConnections(", paste)
        self.assertIn("screen.connections.addAll(copiedEdges)", paste)
        self.assertIn("if (!persistProjectNow(true))", paste)
        self.assertLess(paste.index("if (!persistProjectNow(true))"),
                        paste.index("committed = true"))
        self.assertLess(paste.index("committed = true"),
                        paste.index("fbdWireHistory.clear()"))
        self.assertIn("finally {", paste)
        self.assertIn("if (!committed)", paste)
        self.assertIn("widgets.addAll(originalWidgets)", paste)
        self.assertIn("screen.connections.addAll(originalEdges)", paste)
        self.assertIn("projectExtraFields = originalProjectFields", paste)
        self.assertIn("saveActiveScreen()", paste)

    def test_clipboard_delete_rolls_back_renumbered_graph_and_selection(self):
        deletion = self.source.split("private void deleteSelectedWidget()", 1)[1].split(
            "public static JSONObject widgetToJson(", 1)[0]
        for required in (
            "operatorMode || editor == null",
            "final Screen screen = screens.get(currentScreenIndex)",
            "final ArrayList<Integer> originalSelection",
            "final ArrayList<Widget> originalWidgets",
            "final ArrayList<String> originalBindings",
            "final ArrayList<FbdConnection> originalEdges",
            "final ArrayList<Integer> originalSources",
            "final ArrayList<Integer> originalTargets",
            "WidgetClipboard.deleteSelection(widgets, screen.connections, selection)",
            "if (!persistProjectNow(true))",
            "if (!committed)",
            "originalWidgets.get(i).connectionInfo = originalBindings.get(i)",
            "originalEdges.get(i).source = originalSources.get(i)",
            "originalEdges.get(i).target = originalTargets.get(i)",
            "editor.selectedWidgets.addAll(originalSelection)",
            "projectExtraFields = originalProjectFields",
            "saveActiveScreen()",
        ):
            with self.subTest(required=required):
                self.assertIn(required, deletion)
        self.assertLess(deletion.index("if (!persistProjectNow(true))"),
                        deletion.index("fbdWireHistory.clear()"))
        self.assertLess(deletion.index("if (!persistProjectNow(true))"),
                        deletion.index("releaseVoiceRecognizer()"))
        self.assertLess(deletion.index("if (!persistProjectNow(true))"),
                        deletion.index("liveValues.clear()"))
        self.assertIn("fbdWireMode = false", deletion)
        self.assertIn("wireSource = -1", deletion)

    def test_screen_creation_waits_for_durable_save_before_replacing_editor(self):
        creation = self.source.split("private void addScreen()", 1)[1].split(
            "private void removeCurrentScreen()", 1)[0]
        self.assertIn("operatorMode || nestedMacroEdit != null || editor == null", creation)
        self.assertIn("new ArrayList<>(screens)", creation)
        self.assertIn("new ArrayList<>(widgets)", creation)
        self.assertIn("screens.add(new Screen(", creation)
        self.assertIn("if (!persistProjectNow(true))", creation)
        self.assertIn("if (!committed)", creation)
        self.assertIn("screens.addAll(originalScreens)", creation)
        self.assertIn("widgets.addAll(originalWidgets)", creation)
        self.assertIn("projectExtraFields = originalProjectFields", creation)
        self.assertIn("if (committed) showEditor()", creation)
        self.assertLess(creation.index("if (!persistProjectNow(true))"),
                        creation.index("fbdWireMode = false"))

    def test_screen_deletion_is_guarded_and_rolls_back_before_navigation(self):
        deletion = self.source.split("private void removeCurrentScreen()", 1)[1].split(
            "private void editScreenBackground()", 1)[0]
        for required in (
            "screens.size() <= 1",
            "final EditorCanvas dialogCanvas",
            "final Screen dialogScreen",
            "editor != dialogCanvas",
            "currentScreenIndex != dialogScreenIndex",
            "screens.get(currentScreenIndex) != dialogScreen",
            "new ArrayList<>(screens)",
            "new ArrayList<>(widgets)",
            "screens.remove(dialogScreenIndex)",
            "if (!persistProjectNow(true))",
            "if (!committed)",
            "screens.addAll(originalScreens)",
            "widgets.addAll(originalWidgets)",
            "projectExtraFields = originalProjectFields",
            "fbdWireHistory.clear()",
            "if (committed)",
        ):
            with self.subTest(required=required):
                self.assertIn(required, deletion)
        self.assertLess(deletion.index("if (!persistProjectNow(true))"),
                        deletion.index("fbdWireHistory.clear()"))
        self.assertLess(deletion.index("if (!persistProjectNow(true))"),
                        deletion.index("editor = null"))

    def test_screen_rename_and_background_ignore_stale_dialogs(self):
        background = self.source.split("private void editScreenBackground()", 1)[1].split(
            "private void renameCurrentScreen()", 1)[0]
        rename = self.source.split("private void renameCurrentScreen()", 1)[1].split(
            "private void showWidgetContextMenu()", 1)[0]
        for method in (background, rename):
            self.assertIn("operatorMode || nestedMacroEdit != null || editor == null", method)
            self.assertIn("final EditorCanvas dialogCanvas", method)
            self.assertIn("currentScreenIndex != dialogScreenIndex", method)
            self.assertIn("screens.get(currentScreenIndex) != screen", method)
            self.assertIn("if (!persistProjectNow(true))", method)
        self.assertIn("screen.backgroundColor = oldColor", background)
        self.assertIn("screen.name = previousName", rename)
        self.assertIn("Имя экрана не может быть пустым", rename)

    def test_fbd_block_output_inversion_restores_metadata_after_save_or_exception(self):
        menu = self.source.split("private void showFbdBlockContextMenu(", 1)[1].split(
            "private void showStandardWidgetContextMenu()", 1)[0]
        branch = menu.split('} else if ("Инвертировать выход BOOL".equals(label)) {', 1)[1].split(
            '} else if ("Отсоединить выход".equals(label)) {', 1)[0]
        for required in (
            "new JSONObject(widget.extraFields.toString())",
            "toggleFbdBlockOutputInversion(widget)",
            "if (persistProjectNow(true))",
            "committed = true",
            "} catch (Exception error)",
            "} finally {",
            "if (!committed)",
            "widget.extraFields = before",
            "projectExtraFields = beforeProjectFields",
            "saveActiveScreen()",
        ):
            with self.subTest(required=required):
                self.assertIn(required, branch)

    def test_nested_macro_commit_is_atomic_across_catalog_and_screens(self):
        method = self.source.split("private boolean persistNestedMacroEdit(boolean synchronous)", 1)[1].split(
            "private void ", 1)[0]
        for required in (
            "final JSONObject projectBefore = projectExtraFields",
            "final FbdCraftMacroDefinition definitionBefore",
            "final int outputNodeBefore",
            "final ArrayList<Widget> nestedWidgets",
            "final ArrayList<Widget> hostWidgetsBefore",
            "IdentityHashMap<Screen, ArrayList<Widget>> otherScreensBefore",
            "hostStaged.add(copyWidget(widget))",
            "staged.add(copyWidget(widget))",
            "otherScreensBefore.put(other, original)",
            "new JSONObject(projectBefore.toString())",
            "FbdCraftMacroCatalog.upsert(projectExtraFields, updated)",
            "replaceCraftMacroInstances(session.hostScreen.widgets, updated)",
            "screens.set(session.screenIndex, session.hostScreen)",
            "committed = persistProjectNow(synchronous)",
            "finally {",
            "nestedMacroEdit = session",
            "screens.set(session.screenIndex, session.nestedScreen)",
            "widgets.addAll(nestedWidgets)",
            "if (!committed)",
            "projectExtraFields = projectBefore",
            "session.definition = definitionBefore",
            "session.outputNode = outputNodeBefore",
            "session.hostScreen.widgets.addAll(hostWidgetsBefore)",
            "entry.getKey().widgets.addAll(entry.getValue())",
        ):
            with self.subTest(required=required):
                self.assertIn(required, method)
        self.assertLess(method.index("staged.add(copyWidget(widget))"),
                        method.index("FbdCraftMacroCatalog.upsert"))
        self.assertLess(method.index("FbdCraftMacroCatalog.upsert"),
                        method.index("committed = persistProjectNow(synchronous)"))

    def test_legacy_setpoint_has_safe_runtime_handler_and_fc06_write(self):
        canvas = self.source.split("private class EditorCanvas extends View", 1)[1]
        self.assertIn('adjustSetpoint(picked, x < picked.x + picked.w / 2 ? -1 : 1)', canvas)
        method = canvas.split("private void adjustSetpoint(Widget widget, int direction)", 1)[1].split(
            "private void openCraftLinearSetter(", 1)[0]
        for required in (
            "!operatorMode || !activityResumed || editor != this",
            '"Задатчик".equals(widget.type)',
            "Math.min(widget.minValue, widget.maxValue)",
            "Math.max(widget.minValue, widget.maxValue)",
            "bloodskySetpointStep",
            "bloodskySetpointValue",
            "next == oldValue",
            "binding.functionCode != 3",
            "next < 0 || next > 65535",
            "originalScreen == null",
            "editor != originalCanvas",
            "widgets.get(index) != widget",
            "widget.extraFields = new JSONObject(previous.toString())",
            "if (!persistProjectNow(true))",
            "widget.extraFields = previous",
            "new ModbusTcpClient(binding.host, binding.port)",
            "writeSingle(binding.unit, binding.address, next)",
            "modbusExecutor.execute(",
        ):
            with self.subTest(required=required):
                self.assertIn(required, method)

    def test_craft_dialog_guard_keeps_widget_screen_and_canvas_identity(self):
        guard = self.source.split("private final class CraftWidgetDialogGuard", 1)[1].split(
            "private boolean sameSelectedWidgetObjects(", 1)[0]
        for phrase in (
            "private final EditorCanvas canvas", "private final Screen screen",
            "private final int screenIndex", "private final int widgetIndex",
            "private final Widget widget", "private final ArrayList<Widget> order",
            "isEditorDialogContextCurrent(canvas, screen, screenIndex, order)",
            "widgets.get(widgetIndex) == widget", "editor.selected == widgetIndex",
            "Элемент изменён: открой настройки заново",
            "if (require()) editSelectedWidget(true)",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, guard)

    def test_all_specialized_widget_property_saves_reject_stale_dialog(self):
        editor = self.source.split("private void editSelectedWidget(boolean advanced)", 1)[1].split(
            "LinearLayout form = new LinearLayout(this)", 1)[0]
        self.assertIn("if (operatorMode || currentScreenIndex < 0", editor)
        self.assertIn("new CraftWidgetDialogGuard(w)", editor)
        self.assertEqual(8, editor.count("if (!dialogGuard.require()) return false;"))
        self.assertEqual(8, editor.count("dialogGuard::advanced"))
        for dialog in (
            "CraftListSettingsDialog", "CraftProgressBarSettingsDialog",
            "CraftButtonSettingsDialog", "CraftNumericInputSettingsDialog",
            "CraftLabelSettingsDialog", "CraftDynamicTextSettingsDialog",
            "CraftThresholdGaugeSettingsDialog", "CraftGraduatedGaugeSettingsDialog",
        ):
            start = editor.index(dialog + ".show(")
            end = editor.index("            return;\n        }", start)
            segment = editor[start:end]
            with self.subTest(dialog=dialog):
                self.assertLess(segment.index("if (!dialogGuard.require()) return false;"),
                                segment.index("persistProjectNow(true)"))
                self.assertIn("dialogGuard::advanced", segment)

    def test_four_source_based_craft_settings_guard_saves_and_advanced_actions(self):
        source = self.source
        for name in (
            "showOriginalLampSettings(Widget lamp)",
            "showOriginalSwitchSettings(Widget widget)",
            "showOriginalNumericIndicatorSettings(Widget widget)",
            "showOriginalLinearSetterSettings(Widget widget)",
        ):
            start = source.index("private void " + name)
            end = source.index("\n    private ", start + 20)
            body = source[start:end]
            with self.subTest(name=name):
                self.assertIn("new CraftWidgetDialogGuard(", body)
                self.assertIn("if (!dialogGuard.require()) return;", body)
                self.assertIn("dialogGuard.advanced();", body)
                self.assertLess(body.index("if (!dialogGuard.require()) return;"),
                                body.index("persistProjectNow(true)"))
                self.assertIn("R.style.Theme_Material_Dialog_Alert", body)

    def test_advanced_inspector_rejects_stale_apply_and_image_action(self):
        editor = self.source.split("private void editSelectedWidget(boolean advanced)", 1)[1]
        callback = editor.split("properties.setOnShowListener(", 1)[1]
        self.assertIn("if (!dialogGuard.require())", callback)
        self.assertIn("properties.dismiss();", callback)
        self.assertLess(callback.index("if (!dialogGuard.require())"),
                        callback.index("WidgetGeometryEdits.parse("))
        self.assertIn("if (dialogGuard.require()) showIndicatorImages(w);", editor)
        self.assertIn("activePropertiesDialog = properties", editor)

    def test_original_image_selectors_reject_stale_widget_and_screen(self):
        selector = self.source.split("private boolean selectIndicatorImage(", 1)[1].split(
            "private void showIndicatorImages(", 1)[0]
        images = self.source.split("private void showIndicatorImages(", 1)[1].split(
            "private void ", 1)[0]
        self.assertIn("new CraftWidgetDialogGuard(widget).require()", selector)
        self.assertIn("final CraftWidgetDialogGuard imageGuard", images)
        self.assertIn("if (!imageGuard.require()", images)
        self.assertIn("return imageGuard.require() && selectIndicatorImage(", images)
        self.assertIn("if (!imageGuard.require()) return;", images)
        self.assertIn("R.drawable.kc_align_open_menu", self.source)

    def test_screen_navigation_is_durable_and_rolls_back_before_runtime_cleanup(self):
        navigate = self.source.split("private void activateScreen(int index)", 1)[1].split(
            "private void previousScreen()", 1)[0]
        for required in (
            "nestedMacroEdit != null || screens.isEmpty()",
            "index < 0 || index >= screens.size()",
            "final int previousScreenIndex = currentScreenIndex",
            "final ArrayList<Widget> previousScreenWidgets",
            "final ArrayList<Widget> previousCanvasWidgets",
            "final JSONObject previousProjectFields",
            "saveActiveScreen()",
            "currentScreenIndex = index",
            "if (!persistProjectNow(true))",
            "if (!committed)",
            "currentScreenIndex = previousScreenIndex",
            "previousScreen.widgets.addAll(previousScreenWidgets)",
            "widgets.addAll(previousCanvasWidgets)",
            "projectExtraFields = previousProjectFields",
            "if (!committed) return;",
            "stopScreenAlarms(previousScreenIndex, previousCanvasWidgets)",
            "releaseVoiceRecognizer()",
            "craftWebViewWindow.close()",
            "closeCraftRtspPlayer()",
            "fbdWireMode = false",
            "wireSource = -1",
        ):
            with self.subTest(required=required):
                self.assertIn(required, navigate)
        self.assertLess(navigate.index("if (!persistProjectNow(true))"),
                        navigate.index("releaseVoiceRecognizer()"))
        self.assertLess(navigate.index("if (!committed) return;"),
                        navigate.index("showEditor()"))
        alarm = self.source.split("private void stopScreenAlarms(", 1)[1].split(
            "private void ", 1)[0]
        self.assertIn("stopCraftAlarmAlert(screenIndex + \":\" + index)", alarm)

    def test_advanced_inspector_rolls_back_all_properties_on_validation_or_save_error(self):
        inspector = self.source.split("private void editSelectedWidget(boolean advanced)", 1)[1]
        commit = inspector.split("properties.setOnShowListener(", 1)[1].split(
            "properties.show();", 1)[0]
        for required in (
            "if (!dialogGuard.require())",
            "final Widget original = copyWidget(w)",
            "final JSONObject originalProjectFields",
            "new JSONObject(projectExtraFields.toString())",
            "boolean committed = false",
            "if (!persistProjectNow(true))",
            "committed = true",
            "} finally {",
            "if (!committed)",
            "restoreWidgetFromSnapshot(w, original)",
            "projectExtraFields = originalProjectFields",
            "saveActiveScreen()",
            "properties.dismiss()",
        ):
            with self.subTest(required=required):
                self.assertIn(required, commit)
        self.assertNotIn("persistProject();", commit)
        self.assertLess(commit.index("if (!persistProjectNow(true))"),
                        commit.index("committed = true"))
        self.assertLess(commit.index("committed = true"),
                        commit.index("properties.dismiss()"))
        restore = self.source.split("private static void restoreWidgetFromSnapshot(", 1)[1].split(
            "private void ", 1)[0]
        for field in (
            "target.extraFields = new JSONObject(source.extraFields.toString())",
            "target.x = source.x", "target.y = source.y", "target.w = source.w",
            "target.h = source.h", "target.type = source.type",
            "target.dynTextStates[i] = source.dynTextStates[i]",
            "target.connectionInfo = source.connectionInfo",
        ):
            self.assertIn(field, restore)

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
