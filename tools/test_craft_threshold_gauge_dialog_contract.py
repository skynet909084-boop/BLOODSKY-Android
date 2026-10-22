"""Craft ID12 threshold gauge own dark dialog and safe range persistence."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class ThresholdGaugeDialogContract(unittest.TestCase):
    def test_short_tap_properties_and_no_implicit_modbus_writes(self):
        main = (ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java").read_text(encoding="utf-8")
        dialog = (ROOT / "app/src/main/java/com/bloodsky/hmi/CraftThresholdGaugeSettingsDialog.java").read_text(encoding="utf-8")
        editor = main.split("private void editSelectedWidget(boolean advanced)", 1)[1]
        self.assertIn('"Шкала / Стрелка".equals(w.type)', editor)
        self.assertIn('w.extraFields.optInt("craftComponentId", -1) == 12', editor)
        self.assertIn("CraftThresholdGaugeSettingsDialog.show(this, w.extraFields, w.maxValue,", editor)
        self.assertIn("if (!persistProjectNow(true))", editor)
        self.assertIn("w.maxValue = previousMax;", editor)
        self.assertIn("editSelectedWidget(true)", editor)
        for label in ["Настройки", "Устройство: ", "Рег:", "Максимум:",
                      "Показывать подписи делений", "Внутренняя:", "Внешняя:",
                      "Первая зона:", "Вторая зона:", "Дополнительно: привязка Modbus"]:
            self.assertIn(label, dialog)
        self.assertIn("CraftThresholdGaugeProperties.withSettings(", dialog)
        self.assertNotIn("writeMask(", dialog)
        self.assertNotIn("writeHolding", dialog)

if __name__ == "__main__":
    unittest.main()
