"""Craft ID24 gauge gets its own source-style dark settings and atomic save."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class GraduatedGaugeContract(unittest.TestCase):
    def test_short_tap_uses_dedicated_source_settings_not_generic_form(self):
        main = (ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java").read_text(encoding="utf-8")
        dialog = (ROOT / "app/src/main/java/com/bloodsky/hmi/CraftGraduatedGaugeSettingsDialog.java").read_text(encoding="utf-8")
        editor = main.split("private void editSelectedWidget(boolean advanced)", 1)[1]
        self.assertIn('"Шкала / Стрелка".equals(w.type)', editor)
        self.assertIn('w.extraFields.optInt("craftComponentId", -1) == 24', editor)
        self.assertIn("CraftGraduatedGaugeSettingsDialog.show(this, w.extraFields,", editor)
        self.assertIn("if (!persistProjectNow(true))", editor)
        self.assertIn("w.maxValue = previousMax;", editor)
        self.assertIn("w.minValue = previousMin;", editor)
        self.assertIn("dialogGuard::advanced", editor)
        self.assertIn("if (!dialogGuard.require()) return false;", editor)
        for field in ["Настройки", "Устройство: ", "Рег:", "Минимум:",
                      "Максимум:", "Деления:", "Цвет стрелки:", "Зона низкая %:",
                      "Зона средняя %:", "Скрыть фон шкалы",
                      "Дополнительно: привязка Modbus"]:
            self.assertIn(field, dialog)
        self.assertIn("CraftGraduatedGaugeProperties.withSettings(", dialog)
        self.assertIn("CraftGraduatedGaugeProperties.INDICATORS", dialog)
        self.assertNotIn("writeMask(", dialog)
        self.assertNotIn("writeHolding", dialog)

if __name__ == "__main__":
    unittest.main()
