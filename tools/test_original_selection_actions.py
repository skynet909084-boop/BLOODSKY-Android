from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
from transfer_craft_component_palette import ANDROID, selection_actions

ROOT = Path(__file__).resolve().parents[1]

class OriginalSelectionActionsTests(unittest.TestCase):
    def test_original_selection_hierarchy_and_attributes_are_retained(self):
        def shape(node): return node.tag, node.attrib, [shape(c) for c in node]
        self.assertEqual(shape(ET.fromstring(selection_actions(ROOT / 'build/craft-reference'))),
                         shape(ET.parse(ROOT / 'app/src/main/res/layout/kc_selection_actions.xml').getroot()))

    def test_original_artwork_order_and_accessible_handlers(self):
        root = ET.parse(ROOT / 'app/src/main/res/layout/kc_selection_actions.xml')
        buttons = [node for node in root.iter() if node.tag == 'ImageButton']
        self.assertEqual(['action_cut', 'action_copy', 'action_paste', 'align_open_menu'],
                         [node.get(ANDROID + 'background').removeprefix('@drawable/kc_') for node in buttons])
        code = (ROOT / 'app/src/main/java/com/bloodsky/hmi/OriginalSelectionActions.java').read_text()
        for node in buttons:
            self.assertNotIn(ANDROID + 'onClick', node.attrib)
            self.assertIn('R.id.' + node.get(ANDROID + 'id').split('/')[-1], code)
            name = node.get(ANDROID + 'background').split('/')[-1]
            matches = list((ROOT / 'app/src/main/res/drawable-nodpi').glob(name + '.*'))
            self.assertEqual(1, len(matches))
            original = ROOT / 'build/craft-reference/apktool/res/drawable' / matches[0].name.removeprefix('kc_')
            self.assertEqual(original.read_bytes(), matches[0].read_bytes())
        self.assertIn('button.setContentDescription(label)', code)
        main = (ROOT / 'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        cut = main.split('private void cutSelectedWidget()', 1)[1].split('private void pasteWidget()', 1)[0]
        self.assertIn('if (copySelectedWidgetToClipboard()) deleteSelectedWidget()', cut)
        self.assertIn('OriginalSelectionActions.create(root, this::cutSelectedWidget', main)

if __name__ == '__main__': unittest.main()
