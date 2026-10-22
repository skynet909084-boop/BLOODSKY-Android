"""Craft ID7's own dark settings and transparent background contracts."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class DynamicTextSourceContract(unittest.TestCase):
    def test_component_specific_dialog_and_atomic_save(self):
        main = (ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java").read_text(encoding="utf-8")
        form = (ROOT / "app/src/main/java/com/bloodsky/hmi/CraftDynamicTextSettingsDialog.java").read_text(encoding="utf-8")
        editor = main.split("private void editSelectedWidget(boolean advanced)", 1)[1]
        self.assertIn('if (!advanced && "Динамический текст".equals(w.type))', editor)
        self.assertIn("CraftDynamicTextSettingsDialog.show(this, w.extraFields,", editor)
        self.assertIn("CraftDynamicTextProperties.drawBackground(w.type, w.backgroundColor)", main)
        self.assertIn("System.arraycopy(previousLabels", editor)
        self.assertIn("System.arraycopy(previousColors", editor)
        self.assertIn("if (!persistProjectNow(true))", editor)
        self.assertIn("editSelectedWidget(true)", editor)
        self.assertIn("CraftDynamicTextProperties.STATE_COUNT", form)
        for label in ["Настройки", "Устройство: ", "Рег:", "Состояния 0–15",
                      "Показывать рамку", "Подбирать размер текста",
                      "Дополнительно: размеры и привязка Modbus"]:
            self.assertIn(label, form)
        self.assertIn("CraftDynamicTextProperties.withSettings(", form)
        self.assertIn("CraftDynamicTextProperties.parseColor(", form)
        self.assertNotIn("writeMask(", form)
        self.assertNotIn("writeHolding", form)

if __name__ == "__main__":
    unittest.main()
