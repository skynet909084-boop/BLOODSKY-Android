from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
from transfer_craft_component_palette import transfer

ROOT=Path(__file__).resolve().parents[1]
class OriginalIndicatorImagesTests(unittest.TestCase):
    def test_complete_original_layouts_are_transferred(self):
        def shape(node): return node.tag,node.attrib,[shape(c) for c in node]
        for name in ['dialog_menu_image_lamp','dialog_choose_image_lamp']:
            with self.subTest(name=name):
                original=ET.fromstring(transfer(ROOT/'build/craft-reference',name,'kc_lamp_'))
                actual=ET.parse(ROOT/'app/src/main/res/layout'/('kc_'+name+'.xml')).getroot()
                self.assertEqual(shape(original),shape(actual))

    def test_custom_file_selection_and_rollback_are_connected_to_real_renderer(self):
        main=(ROOT/'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        edit=main.split('private boolean selectIndicatorImage(',1)[1].split('private void editSelectedWidget()',1)[0]
        self.assertIn('widgets.contains(widget)',edit)
        self.assertIn('OriginalIndicator.withImage(old, active, user, value)',edit)
        self.assertIn('persistProjectNow(true)',edit)
        self.assertIn('widget.extraFields = old; projectExtraFields = oldProject; saveActiveScreen()',edit)
        self.assertIn('return originalIndicatorBitmap(widget, active)',edit)
        self.assertIn('addMediaPicker(form, path, "image/*")',edit)
        self.assertIn('loadCachedBitmap(selected) == null',edit)
        decode=main.split('private Bitmap loadCachedBitmap(',1)[1].split('private Bitmap originalIndicatorBitmap',1)[0]
        self.assertIn('readProjectMediaData(pathOrUri, json)',decode)
        self.assertIn('inJustDecodeBounds = true',decode)
        self.assertIn('ProjectImageSizing.sampleSize',decode)
        self.assertIn('32L * 1024 * 1024',decode)

    def test_all_original_builtin_images_have_real_selection_handlers(self):
        ui=(ROOT/'app/src/main/java/com/bloodsky/hmi/OriginalIndicatorImages.java').read_text()
        for name in ['imageGreen','imageYellow','imageRed','imageBlue','imageOff']:
            self.assertIn('R.id.kc_lamp_'+name,ui)
        self.assertIn('host.select(active,false,value)',ui)
        self.assertIn('host.chooseCustom(active',ui)

if __name__=='__main__':unittest.main()
