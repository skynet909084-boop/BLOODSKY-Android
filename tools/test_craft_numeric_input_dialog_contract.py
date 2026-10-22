"""Craft ID8 has its own compact dialog, not the generic white inspector."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class NumericInputDialogContract(unittest.TestCase):
    def test_short_editor_tap_and_safe_save(self):
        main=(ROOT/"app/src/main/java/com/bloodsky/hmi/MainActivity.java").read_text(encoding="utf-8")
        dialog=(ROOT/"app/src/main/java/com/bloodsky/hmi/CraftNumericInputSettingsDialog.java").read_text(encoding="utf-8")
        editor=main.split("private void editSelectedWidget(boolean advanced)",1)[1]
        self.assertIn('if (!advanced && "Числовой ввод Craft".equals(w.type))',editor)
        self.assertIn("CraftNumericInputSettingsDialog.show(this, w.extraFields, updated ->",editor)
        self.assertIn("if (!persistProjectNow(true))",editor)
        self.assertIn("w.extraFields = previous;",editor)
        self.assertIn("dialogGuard::advanced",editor)
        self.assertIn("if (!dialogGuard.require()) return false;",editor)
        for phrase in ["Настройки", "Устройство: ", "Рег:", "Мин:", "Макс:",
                       "Float32", "UINT16", "INT16", "Дробная:",
                       "Системный шрифт", "Рамка",
                       "Дополнительно: пароль и привязка Modbus"]:
            self.assertIn(phrase,dialog)
        self.assertIn("CraftNumericInputCompactSettings.withSettings(",dialog)
        self.assertNotIn("writeMask(",dialog)
        self.assertNotIn("writeHolding",dialog)

if __name__=="__main__":
    unittest.main()
