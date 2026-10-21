"""Source-backed layout, resource and behavior coverage for the original palette."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET
from transfer_craft_component_palette import transfer, ANDROID

ROOT = Path(__file__).resolve().parents[1]

class OriginalComponentPaletteTests(unittest.TestCase):
    def test_every_source_attribute_and_child_is_transferred(self):
        expected = ET.fromstring(transfer(ROOT / 'build/craft-reference'))
        actual = ET.parse(ROOT / 'app/src/main/res/layout/kc_fragment_component_bar.xml').getroot()
        def shape(node):
            return node.tag, node.attrib, [shape(child) for child in node]
        self.assertEqual(shape(expected), shape(actual))

    def test_every_visible_component_is_explicitly_bound_or_unavailable(self):
        layout = ET.parse(ROOT / 'app/src/main/res/layout/kc_fragment_component_bar.xml')
        code = (ROOT / 'app/src/main/java/com/bloodsky/hmi/OriginalComponentPalette.java').read_text()
        for node in layout.iter():
            identifier = node.get(ANDROID + 'id', '').split('/')[-1]
            if identifier.startswith('kc_picker_tab_') and node.get(ANDROID + 'visibility') != 'gone':
                self.assertRegex(code, r'(?:bind|action|unavailable)\(dialog, R.id.' + identifier + r'[, ]')
            drawable = node.get(ANDROID + 'background', '')
            if drawable.startswith('@drawable/kc_'):
                name = drawable.split('/')[-1]
                matches = list((ROOT / 'app/src/main/res/drawable-nodpi').glob(name + '.*'))
                self.assertEqual(1, len(matches), name)
                source = ROOT / 'build/craft-reference/apktool/res/drawable' / matches[0].name.removeprefix('kc_')
                self.assertEqual(source.read_bytes(), matches[0].read_bytes())
        self.assertIn('view.setEnabled(false)', code)
        self.assertIn('this::showFbdPalette', (ROOT / 'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text())

    def test_bound_components_are_existing_types_and_background_has_own_action(self):
        code = (ROOT / 'app/src/main/java/com/bloodsky/hmi/OriginalComponentPalette.java').read_text()
        main = (ROOT / 'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        for component in re.findall(r'bind\(dialog, R.id.\w+, "([^"]+)"', code):
            self.assertIn('"' + component + '"', main)
        self.assertIn('"Фон экрана", background', code)
        self.assertNotIn('java.util.function', code) # API 23 compatibility.

    def test_supported_original_controls_are_not_dimmed(self):
        code = (ROOT / 'app/src/main/java/com/bloodsky/hmi/OriginalComponentPalette.java').read_text()
        for item, widget_type in [
            ('switch', 'Переключатель Craft'),
            ('alarm', 'Звуковой сигнал'),
            ('digital_indicator', 'Цифровой индикатор Craft'),
            ('gateway', 'Шлюз Craft'),
            ('gateway_bit', 'Шлюз битовый'),
        ]:
            unique_actions = {
                'switch': ('Добавить: Переключатель Craft', '() -> add.add(OriginalCraftSwitch.TYPE, "Переключатель")'),
                'alarm': ('Добавить: Сигнализатор Craft', '() -> add.add("Звуковой сигнал", "Аларм")'),
                'digital_indicator': ('Добавить: Цифровой индикатор', '() -> add.add(OriginalCraftNumericIndicator.TYPE, "0")'),
            }
            if item in unique_actions:
                label, callback = unique_actions[item]
                self.assertIn(label, code)
                self.assertIn(callback, code)
                self.assertNotRegex(code, r'unavailable\(dialog, R.id.kc_picker_tab_' + item + r',')
                continue
            self.assertRegex(code, r'bind\(dialog, R.id.kc_picker_tab_' + item
                             + r'\s*,\s*"' + re.escape(widget_type) + r'"')
            self.assertNotRegex(code, r'unavailable\(dialog, R.id.kc_picker_tab_' + item + r',')

    def test_sms_directions_are_opt_in_and_push_uses_telegram(self):
        palette = (ROOT / 'app/src/main/java/com/bloodsky/hmi/OriginalComponentPalette.java').read_text()
        main = (ROOT / 'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('bind(dialog, R.id.kc_picker_tab_sms_in, "Входящее SMS", "SMS: команда", add)', palette)
        self.assertIn('bind(dialog, R.id.kc_picker_tab_sms_out, "SMS-уведомление", "SMS: {value}", add)', palette)
        self.assertIn('R.id.kc_picker_tab_sms_in).setVisibility(View.VISIBLE)', palette)
        self.assertIn('R.id.kc_picker_tab_sms_out).setVisibility(View.VISIBLE)', palette)
        self.assertIn('"Telegram-уведомление", "Событие", add', palette)
        self.assertIn('"Telegram-уведомление".equals(w.type)', main)
        self.assertIn('"SMS-уведомление".equals(w.type)', main)
        self.assertIn('android.permission.SEND_SMS', manifest)
        self.assertIn('android.permission.RECEIVE_SMS', manifest)
        self.assertIn('smsEnabled", false', main)
        self.assertIn('refreshIncomingSmsReceiver()', main)
        self.assertIn('new SmsIncomingBroadcastReceiver(this::handleIncomingSms)', main)

if __name__ == '__main__':
    unittest.main()
