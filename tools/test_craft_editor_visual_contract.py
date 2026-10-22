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
        self.assertIn('"Свойства"', menu)
        self.assertIn("editSelectedWidget()", menu)


if __name__ == "__main__":
    unittest.main()
