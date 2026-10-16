from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
class WidgetGeometryEditorTests(unittest.TestCase):
    def test_geometry_is_validated_before_any_widget_edit_and_dialog_stays_open_on_error(self):
        source=(ROOT/'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        form=source.split('private void editSelectedWidget()',1)[1].split('private int parseOptionalColor',1)[0]
        self.assertLess(form.index('WidgetGeometryEdits.parse('),form.index('w.text = textField'))
        self.assertIn('.setPositiveButton("Применить", null).create()',form)
        self.assertIn('catch (IllegalArgumentException invalid)',form)
        self.assertIn('properties.dismiss()',form)
        for member in ['x','y','w','h']:
            self.assertIn('Float.toString(w.'+member+')',form)
            self.assertNotIn('Math.round(w.'+member+')',form)
        for field in ['x','y','width','height']:
            self.assertNotIn('readPositiveInt('+field+'Field',form)
if __name__=='__main__':unittest.main()
