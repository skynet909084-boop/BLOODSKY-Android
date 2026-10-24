"""Keep Craft ID20's own dark property editor and guarded source save."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class CraftListDialogContract(unittest.TestCase):
    def test_short_tap_opens_list_specific_properties(self):
        main = (ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java").read_text(encoding="utf-8")
        dialog = (ROOT / "app/src/main/java/com/bloodsky/hmi/CraftListSettingsDialog.java").read_text(encoding="utf-8")
        editor = main.split("private void editSelectedWidget(boolean advanced)", 1)[1]
        self.assertIn('if (!advanced && "Список Craft".equals(w.type))', editor)
        self.assertIn("CraftListSettingsDialog.show(this, w.extraFields, updated ->", editor)
        for name in ["Настройки", "Устройство: ", "Рег:", "Подписи битов 0–15",
                     "Показывать в рабочем режиме", "Не изменять биты в рабочем режиме",
                     "Дополнительно: привязка Modbus"]:
            self.assertIn(name, dialog)
        self.assertIn("CraftBitList.parseEditorLines(", dialog)
        self.assertIn("CraftBitList.withSettings(", dialog)
        self.assertIn("dialogGuard::advanced", editor)
        self.assertIn("if (!dialogGuard.require()) return false;", editor)
        self.assertIn("if (!persistProjectNow(true))", editor)
        self.assertIn("w.extraFields = previous;", editor)
        self.assertNotIn("writeMask(", dialog)
        self.assertNotIn("writeMultipleCoils(", dialog)


if __name__ == "__main__":
    unittest.main()
