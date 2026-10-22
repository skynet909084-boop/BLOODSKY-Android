"""Visual source contract for transparent Craft label and its own dark inspector."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class CraftLabelVisualContract(unittest.TestCase):
    def test_transparent_text_label_does_not_get_default_white_card(self):
        c=(ROOT/"app/src/main/java/com/bloodsky/hmi/MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("CraftLabelAppearance.drawBackground(w.type, w.backgroundColor)",c)
        editor=c.split("private void editSelectedWidget(boolean advanced)",1)[1]
        self.assertIn("if (!advanced && CraftLabelAppearance.isLabel(w.type))",editor)
        self.assertIn("CraftLabelSettingsDialog.show(this, w.extraFields",editor)
        self.assertIn("if (!persistProjectNow(true))",editor)
        self.assertIn("w.backgroundColor = previousBackground;",editor)
        dialog=(ROOT/"app/src/main/java/com/bloodsky/hmi/CraftLabelSettingsDialog.java").read_text(encoding="utf-8")
        for label in ["Настройки", "Надпись компонента Craft", "Размер:", "Цвет:",
                      "Фон:", "Прозрачный", "Рамка", "Подбирать размер текста"]:
            self.assertIn(label,dialog)
        self.assertIn("CraftLabelAppearance.withSettings(",dialog)
        self.assertNotIn("writeMask(",dialog)

if __name__=="__main__":
    unittest.main()
