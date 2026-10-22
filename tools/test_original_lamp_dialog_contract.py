"""Visible Craft lamp editor interaction and inactive-import regression checks."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java"
SOURCE = ROOT / "app/src/main/java/com/bloodsky/hmi/OriginalCraftLampConnection.java"


class CraftLampEditorContract(unittest.TestCase):
    def test_tap_opens_compact_original_properties(self):
        java = MAIN.read_text()
        settings = java.split("private void showOriginalLampSettings(Widget lamp)", 1)[1].split(
            "private void editSelectedWidget()", 1
        )[0]
        for label in ('title.setText("Настройки")',
                      '"Устройство: " + OriginalCraftLampConnection.deviceLabel',
                      'registerLabel.setText("Рег:")',
                      'bitLabel.setText("  Бит:")',
                      'images.setText("Сменить изображение")'):
            self.assertIn(label, settings)
        self.assertIn('android.R.style.Theme_Material_Dialog_Alert', settings)
        self.assertIn('persistProjectNow(true)', settings)

    def test_extended_inspector_is_still_reachable_and_import_does_not_auto_connect(self):
        java = MAIN.read_text()
        self.assertIn('if (!advanced && OriginalIndicator.supports(w.type))', java)
        self.assertIn('showOriginalLampSettings(w);', java)
        self.assertIn('editSelectedWidget(true);', java)
        lamp = SOURCE.read_text()
        self.assertIn('result.put("craftConnectionInfo"', lamp)
        self.assertNotIn('result.put("connectionInfo"', lamp)
        self.assertIn('decodeWord(raw)', lamp)


if __name__ == "__main__":
    unittest.main()
