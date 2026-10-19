from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET
from transfer_craft_component_palette import ANDROID as A
from transfer_craft_fbd_palette import drawing, inventory, layout

ROOT=Path(__file__).resolve().parents[1]
class OriginalFbdPaletteTests(unittest.TestCase):
    def test_source_drawings_and_both_orientation_layouts_are_reproducible(self):
        decoded=ROOT/'build/craft-reference'
        self.assertEqual(drawing(decoded),(ROOT/'app/src/main/java/com/bloodsky/hmi/OriginalFbdDrawing.java').read_text())
        for folder,landscape in [('layout',False),('layout-land',True)]:
            self.assertEqual(layout(decoded,landscape),(ROOT/'app/src/main/res'/folder/'kc_fragment_component_logic_bar.xml').read_text())
        self.assertEqual(40,len(inventory(decoded)))
        self.assertNotIn('Path path = path;',drawing(decoded))
        self.assertIn('Path path = this.path;',drawing(decoded))

    def test_every_thumbnail_is_a_drawing_and_only_explicit_supported_entries_are_enabled(self):
        root=ET.parse(ROOT/'app/src/main/res/layout/kc_fragment_component_logic_bar.xml')
        code=(ROOT/'app/src/main/java/com/bloodsky/hmi/OriginalFbdPalette.java').read_text()
        previews=list(root.iter('com.bloodsky.hmi.OriginalFbdDrawing'))
        self.assertEqual(40,len(previews))
        self.assertIn('view.setEnabled(false)',code)
        ids={n.get(A+'id').split('/')[-1] for n in previews}
        bound=re.findall(r'bind\(dialog,R.id.(\w+),"([^"]+)"',code)
        self.assertEqual(3,len(bound))
        native=re.findall(r'bindNative\(dialog,R.id.(\w+),"([^"]+)"',code)
        self.assertGreaterEqual(len(native),20)
        main=(ROOT/'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        for identifier,kind in bound:
            self.assertIn(identifier,ids); self.assertIn('"'+kind+'"',main)
        # Source RS is R,S, while legacy BLOODSKY is S,R; no false equivalence.
        self.assertNotIn('bind(dialog,R.id.kc_logic_tab_fb_rs',code)
        self.assertNotIn('bind(dialog,R.id.kc_logic_tab_fb_ctu',code)
        self.assertIn('this::showFbdPalette',main)
        self.assertIn('this::showCraftMacroTools',main)
        self.assertIn('otherElements.setText("Другие элементы BLOODSKY")',main)
        self.assertIn('showExtendedWidgetMenu();',main)

    def test_macro_library_uses_source_grid_cards_and_page_switches(self):
        source = ET.parse(ROOT / 'build/craft-reference/apktool/res/layout/fragment_tile_macros.xml').getroot()
        source_card = ET.parse(ROOT / 'build/craft-reference/apktool/res/layout/item_tile_add_new_macros.xml').getroot()
        self.assertEqual('androidx.recyclerview.widget.RecyclerView',
                         next(n.tag for n in source if n.tag == 'androidx.recyclerview.widget.RecyclerView'))
        self.assertEqual('100.0dp', source_card.get(A + 'layout_width'))
        self.assertEqual('100.0dp', source_card.get(A + 'layout_height'))
        self.assertEqual('FB_MACRO', next(n for n in source_card.iter()
                                          if n.tag == 'com.bloodsky.hmi.OriginalFbdDrawing').get(A + 'tag'))

        card = ET.parse(ROOT / 'app/src/main/res/layout/kc_macro_library_card.xml').getroot()
        self.assertEqual('100dp', card.get(A + 'layout_width'))
        self.assertEqual('100dp', card.get(A + 'layout_height'))
        self.assertEqual('FB_MACRO', next(n for n in card.iter()
                                          if n.tag == 'com.bloodsky.hmi.OriginalFbdDrawing').get(A + 'tag'))

        main = (ROOT / 'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        page = main.split('private void showCraftMacroTools()', 1)[1].split(
                'private void addCraftMacroFromCatalog()', 1)[0]
        self.assertIn('android.widget.GridLayout', page)
        self.assertIn('/ 180f', page)
        self.assertIn('dp(100)', page)
        self.assertIn('R.layout.kc_macro_library_card', page)
        self.assertIn('FbdCraftMacroCatalog.names(projectExtraFields)', page)
        self.assertIn('R.drawable.kc_tab_page1', page)
        self.assertIn('showAddWidgetMenu();', page)
        self.assertIn('setOnLongClickListener', page)
        self.assertIn('showFbdPalette();', page)
        self.assertIn('Импорт .mcr', page)
        self.assertIn('Экспорт .mcr', page)

    def test_every_referenced_drawable_exists_and_xml_backgrounds_match_original(self):
        for folder in ['layout','layout-land']:
            for node in ET.parse(ROOT/'app/src/main/res'/folder/'kc_fragment_component_logic_bar.xml').iter():
                background=node.get(A+'background','')
                if background.startswith('@drawable/'):
                    name=background.split('/')[-1]
                    matches=list((ROOT/'app/src/main/res').glob('drawable*/'+name+'.*'))
                    self.assertEqual(1,len(matches),name)
                    source=ROOT/'build/craft-reference/apktool/res/drawable'/matches[0].name.removeprefix('kc_')
                    self.assertEqual(source.read_bytes(),matches[0].read_bytes())

    def test_all_fbd_fields_validate_before_any_widget_change(self):
        code=(ROOT/'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        handler=code.split('final FbdPropertyEdits fbd;',1)[1].split('properties.show();',1)[0]
        self.assertLess(handler.index('FbdPropertyEdits.parse('),handler.index('w.text ='))
        self.assertNotIn('readPositiveInt(timerPresetField',handler)
        self.assertNotIn('readDouble(compareLeftField',handler)
        self.assertIn('w.compareOperator = fbd.operator',handler)

if __name__=='__main__':unittest.main()
