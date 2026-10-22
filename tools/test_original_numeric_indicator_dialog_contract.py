"""Guard the compact Craft ID4 numeric property path."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java"


class NumericIndicatorDialogContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = MAIN.read_text(encoding="utf-8")

    def test_numeric_editor_routes_to_its_own_dialog(self):
        editor = self.main.split("private void editSelectedWidget(boolean advanced)", 1)[1]
        self.assertIn('!advanced && OriginalCraftNumericIndicator.TYPE.equals(w.type)', editor)
        self.assertIn("showOriginalNumericIndicatorSettings(w)", editor)
        form = self.main.split("private void showOriginalNumericIndicatorSettings(Widget widget)", 1)[1]
        form = form.split("private void showOriginalSwitchSettings(", 1)[0]
        for part in ["Настройки", "Устройство: ", "Рег:", "Разряд:", "Дробная:",
                     "Рамка по регистру", "Системный шрифт",
                     "Дополнительно: привязка Modbus", "FLOAT32", "INT32"]:
            self.assertIn(part, form)
        self.assertIn("editSelectedWidget(true)", form)
        self.assertIn("OriginalCraftNumericIndicator.withSettings(", form)
        self.assertIn("if (!persistProjectNow(true))", form)
        self.assertIn("widget.extraFields = old;", form)
        self.assertNotIn("writeMask(", form)
        self.assertNotIn("writeHolding", form)


if __name__ == "__main__":
    unittest.main()
