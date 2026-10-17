from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
from transfer_craft_component_palette import ANDROID, editor_toolbar

ROOT = Path(__file__).resolve().parents[1]
class OriginalEditorToolbarTests(unittest.TestCase):
    def test_source_hierarchy_and_resolved_attributes_are_preserved(self):
        def shape(n): return n.tag, n.attrib, [shape(c) for c in n]
        self.assertEqual(shape(ET.fromstring(editor_toolbar(ROOT / 'build/craft-reference'))),
                         shape(ET.parse(ROOT / 'app/src/main/res/layout/kc_editor_toolbar.xml').getroot()))

    def test_visible_icons_are_bound_or_explicitly_disabled_and_use_original_bytes(self):
        root = ET.parse(ROOT / 'app/src/main/res/layout/kc_editor_toolbar.xml')
        code = (ROOT / 'app/src/main/java/com/bloodsky/hmi/OriginalEditorToolbar.java').read_text()
        for node in root.iter():
            self.assertNotIn(ANDROID + 'onClick', node.attrib)
            asset = node.get(ANDROID + 'background', '')
            if asset.startswith('@drawable/kc_'):
                name = asset.split('/')[-1]
                matches = list((ROOT / 'app/src/main/res/drawable-nodpi').glob(name + '.*'))
                self.assertEqual(1, len(matches), name)
                original = ROOT / 'build/craft-reference/apktool/res/drawable' / matches[0].name.removeprefix('kc_')
                self.assertEqual(original.read_bytes(), matches[0].read_bytes())
            if node.tag == 'ImageView' and node.get(ANDROID + 'visibility', 'visible') == 'visible':
                self.assertIn('R.id.' + node.get(ANDROID + 'id').split('/')[-1], code)
        self.assertIn('info.setEnabled(false)', code)
        self.assertIn('R.drawable.kc_stop : R.drawable.kc_start', code)
        self.assertEqual(1, len(list(root.iter('TextClock'))))

    def test_runtime_input_is_routed_before_any_editor_mutation(self):
        code = (ROOT / 'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        touch = code.split('@Override public boolean onTouchEvent(MotionEvent event)', 1)[1]
        self.assertLess(touch.index('if (operatorMode) return onOperatorTouch(event)'), touch.index('w.x ='))
        self.assertNotIn('activateRuntimeWidget(', touch)
        runtime = code.split('private void activateRuntimeWidget(', 1)[1].split('@Override public boolean onTouchEvent', 1)[0]
        self.assertNotIn('editSelectedWidget()', runtime)
        self.assertIn('if (!persistProjectNow(true)) return;', code)
        self.assertIn('operatorMode = !operatorMode; fbdWireMode = false; wireSource = -1;', code)
        self.assertIn('operatorMode ? "Рабочий экран" : "Редактор экрана"', code)

if __name__ == '__main__': unittest.main()
