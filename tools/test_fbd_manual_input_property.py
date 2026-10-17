"""Source guard: an FBD input must be actually editable in its properties UI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java"


class FbdInputPropertyWiringTests(unittest.TestCase):
    def test_source_input_checkbox_load_and_apply(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn('if ("FBD Вход".equals(w.type)) {', source)
        self.assertIn('fbdInputField.setContentDescription("Значение входа FBD")', source)
        self.assertIn('fbdInputField.setChecked(w.inputValue)', source)
        self.assertIn('if (fbdInputField != null) w.inputValue = fbdInputField.isChecked()', source)

    def test_editor_preview_is_not_stateful_runtime(self):
        source = SOURCE.read_text(encoding="utf-8")
        start = source.index("if (w.type != null && w.type.startsWith(\"FBD \")) {")
        end = source.index('} else if ("Динамический текст"', start)
        view = source[start:end]
        self.assertNotIn("evaluateFbd(i", view)
        self.assertIn('canvas.drawText("OUT = —"', view)


if __name__ == "__main__":
    unittest.main()
