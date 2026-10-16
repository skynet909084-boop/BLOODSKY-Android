"""Confirm original image bytes and production wiring, not visual equivalence."""
import hashlib
import json
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]

class OriginalIndicatorTransferTests(unittest.TestCase):
    def test_source_and_image_provenance(self):
        provenance = json.loads((ROOT/'docs/original-indicator-provenance.json').read_text())
        for path, expected in provenance.items():
            with self.subTest(path=path):
                self.assertEqual(expected, hashlib.sha256((ROOT/'build/craft-reference'/path).read_bytes()).hexdigest())

    def test_original_image_aliases_and_production_renderer(self):
        source = (ROOT/'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        for name in ['lamp_green', 'lamp_off', 'lamp_red', 'lamp_yellow', 'lamp_blue', 'warning_32']:
            self.assertIn('R.drawable.kc_'+name, source)
            self.assertEqual((ROOT/'build/craft-reference/apktool/res/drawable'/ (name+'.png')).read_bytes(),
                             (ROOT/'app/src/main/res/drawable-nodpi'/('kc_'+name+'.png')).read_bytes())
        renderer = source.split('String indicatorValue = liveValues.get(i);', 1)[1].split('} else if ("Кнопка"', 1)[0]
        self.assertIn('originalIndicatorBitmap(w, isActiveModbusValue(indicatorValue))', renderer)
        self.assertNotIn('drawCircle', renderer)
        self.assertNotIn('drawText', renderer)
        self.assertIn('rect.left + 10', renderer)
        self.assertIn('rect.right - 10', renderer)

if __name__ == '__main__':
    unittest.main()
