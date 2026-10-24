"""Source-contract checks for source-style Craft ID13 slider property dialog."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class LinearSetterDialogContract(unittest.TestCase):
    def test_short_tap_routes_into_dedicated_dark_dialog(self):
        main = (ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java").read_text(encoding="utf-8")
        route = main.split("private void editSelectedWidget(boolean advanced)", 1)[1]
        self.assertIn('if (!advanced && "Линейный задатчик Craft".equals(w.type))', route)
        self.assertIn("showOriginalLinearSetterSettings(w)", route)
        dialog = main.split("private void showOriginalLinearSetterSettings(Widget widget)", 1)[1].split(
            "private void showOriginalNumericIndicatorSettings(", 1
        )[0]
        for value in ["Настройки", "Устройство: ", "Рег:", "Макс:",
                      "Вертикальный", "Дополнительно: привязка Modbus"]:
            self.assertIn(value, dialog)
        self.assertIn("CraftLinearSetter.withSettings(", dialog)
        self.assertIn("dialogGuard.advanced();", dialog)
        self.assertIn("if (!dialogGuard.require()) return;", dialog)
        self.assertIn("if (!persistProjectNow(true))", dialog)
        self.assertIn("widget.extraFields = previous;", dialog)
        self.assertNotIn("writeMask(", dialog)
        self.assertNotIn("writeHolding", dialog)


if __name__ == "__main__":
    unittest.main()
