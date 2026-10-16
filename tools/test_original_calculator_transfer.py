"""Verify the transferred calculator against the pinned original, not a guessed UI."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'

class OriginalCalculatorTransferTests(unittest.TestCase):
    def test_layout_retains_every_original_attribute_and_child(self):
        original = ET.parse(ROOT / 'build/craft-reference/apktool/res/layout/calculator.xml').getroot()
        transferred = ET.parse(ROOT / 'app/src/main/res/layout/kc_calculator.xml').getroot()
        def shape(node, source=False):
            attrs = dict(node.attrib)
            if source:
                for key, value in attrs.items():
                    if value.startswith('@id/'):
                        attrs[key] = '@+id/kc_calc_' + value.split('/')[1]
                    elif value == '@string/Exit':
                        attrs[key] = 'Выход'
            return node.tag, attrs, [shape(child, source) for child in node]
        self.assertEqual(shape(original, True), shape(transferred))

    def test_original_arithmetic_is_retained(self):
        original = (ROOT / 'build/craft-reference/jadx/sources/ru/himik/nightscream/kascada/p.java').read_text()
        transferred = (ROOT / 'app/src/main/java/com/bloodsky/hmi/OriginalCalculatorDialog.java').read_text()
        # Includes input, operator chaining, float/error behavior and clearing. Only toast resources adapt.
        def arithmetic(source):
            return source[source.index('    void a(String str)'):source.index('    @Override', source.index('    void d()'))]
        expected = arithmetic(original).replace('getResources().getString(R.string.infinity)', '"Бесконечность"').replace('getResources().getString(R.string.error)', '"Ошибка"')
        self.assertEqual(expected, arithmetic(transferred))

    def test_every_transferred_control_has_a_handler(self):
        xml = ET.parse(ROOT / 'app/src/main/res/layout/kc_calculator.xml')
        code = (ROOT / 'app/src/main/java/com/bloodsky/hmi/OriginalCalculatorDialog.java').read_text()
        for button in xml.iter('Button'):
            ident = button.get(ANDROID + 'id').split('/')[1]
            self.assertIn('R.id.' + ident, code)
        main = (ROOT / 'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        self.assertIn('new OriginalCalculatorDialog().show(getFragmentManager(), "original-calculator")', main)

if __name__ == '__main__':
    unittest.main()
