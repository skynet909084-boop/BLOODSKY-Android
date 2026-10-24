"""Keep the Craft progress bar on its own dark, bounded property dialog."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class CraftProgressDialogContract(unittest.TestCase):
    def test_editor_route_and_operator_ranges_share_saved_properties(self):
        main = (ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java").read_text(encoding="utf-8")
        dialog = (ROOT / "app/src/main/java/com/bloodsky/hmi/CraftProgressBarSettingsDialog.java").read_text(
            encoding="utf-8"
        )
        editor = main.split("private void editSelectedWidget(boolean advanced)", 1)[1]
        self.assertIn('if (!advanced && "Прогресс-бар".equals(w.type))', editor)
        self.assertIn("CraftProgressBarSettingsDialog.show(this, w.extraFields,", editor)
        self.assertIn("w.minValue = min;", editor)
        self.assertIn("w.maxValue = max;", editor)
        self.assertIn("previousMin", editor)
        self.assertIn("if (!persistProjectNow(true))", editor)
        for key in ["Настройки", "Устройство: ", "Рег:", "Мин:", "Макс:",
                    "Показывать значение", "Показывать деления шкалы",
                    "Заполнение:", "Дорожка:", "Дополнительно: привязка Modbus"]:
            self.assertIn(key, dialog)
        self.assertIn("CraftProgressBarProperties.withSettings(", dialog)
        self.assertIn("Color.parseColor(", dialog)
        self.assertNotIn("writeMask(", dialog)
        self.assertNotIn("writeMultiple(", dialog)


if __name__ == "__main__":
    unittest.main()
