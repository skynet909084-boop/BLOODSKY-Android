"""Focused source-contract checks for source-style Craft ID1 switch editor flow."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java"


class OriginalCraftSwitchDialogContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_short_tap_uses_switch_dialog_without_replacing_advanced_editor(self):
        entry = self.source.split("private void editSelectedWidget(boolean advanced)", 1)[1]
        self.assertIn("if (!advanced && OriginalCraftSwitch.supports(w.type))", entry)
        self.assertIn("showOriginalSwitchSettings(w);", entry)
        compact = self.source.split("private void showOriginalSwitchSettings(Widget widget)", 1)[1].split(
            "private void showOriginalLampSettings(", 1
        )[0]
        for label in ["Настройки", "Устройство: ", "Рег:", "Бит:",
                      "Сменить изображение", "Дополнительно: привязка Modbus"]:
            self.assertIn(label, compact)
        self.assertIn("editSelectedWidget(true)", compact)
        self.assertIn("android.R.style.Theme_Material_Dialog_Alert", compact)

    def test_palette_skin_selector_and_safe_persistence(self):
        compact = self.source.split("private void showOriginalSwitchSettings(Widget widget)", 1)[1].split(
            "private void showOriginalLampSettings(", 1
        )[0]
        self.assertIn("String[] choices = new String[8]", compact)
        self.assertIn("OriginalCraftSwitch.withSettings(", compact)
        self.assertIn("if (!persistProjectNow(true))", compact)
        self.assertIn("widget.extraFields = old;", compact)
        self.assertIn("saveActiveScreen();", compact)
        self.assertNotIn("writeMask(", compact)
        self.assertNotIn("craftSwitchBitEnabled\", true", compact)


if __name__ == "__main__":
    unittest.main()
