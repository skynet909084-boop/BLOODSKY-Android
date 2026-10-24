"""Contract for Craft ID6 button's own dark short-tap property editor."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ButtonDialogContract(unittest.TestCase):
    def test_button_short_tap_uses_component_dialog(self):
        source = (ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java").read_text(encoding="utf-8")
        dialog = (ROOT / "app/src/main/java/com/bloodsky/hmi/CraftButtonSettingsDialog.java").read_text(encoding="utf-8")
        editor = source.split("private void editSelectedWidget(boolean advanced)", 1)[1]
        self.assertIn('"Кнопка".equals(w.type)', editor)
        self.assertIn('"Константная кнопка Craft".equals(w.type)', editor)
        self.assertIn("CraftButtonSettingsDialog.show(this, w.extraFields, w.text,", editor)
        self.assertIn("w.extraFields = previousFields;", editor)
        self.assertIn("w.text = previousCaption;", editor)
        self.assertIn("if (!persistProjectNow(true))", editor)
        for marker in ["Настройки", "Устройство: ", "Режим: ", "Рег:", "Бит:",
                       "Подпись кнопки Craft", "Показывать в рабочем режиме",
                       "Дополнительно: режим, константа и привязка Modbus"]:
            self.assertIn(marker, dialog)
        self.assertIn("CraftButtonCompactSettings.withSettings(", dialog)
        self.assertNotIn("writeMask(", dialog)
        self.assertNotIn("writeMultiple(", dialog)


if __name__ == "__main__":
    unittest.main()
