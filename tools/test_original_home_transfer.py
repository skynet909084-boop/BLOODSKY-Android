"""Source/resource wiring checks; these do not claim screenshot parity."""
import hashlib
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class OriginalHomeTransferTests(unittest.TestCase):
    def test_original_files_match_transfer_provenance(self):
        provenance = json.loads((ROOT / 'docs/original-home-provenance.json').read_text())
        original = ROOT / 'build/craft-reference'
        for path, expected in provenance.items():
            with self.subTest(path=path):
                self.assertEqual(expected, hashlib.sha256((original/path).read_bytes()).hexdigest())

    def test_home_uses_original_images_and_exposes_manual_usb_rtu(self):
        source = (ROOT / 'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        home = source.split('private void showHome()', 1)[1].split('private void addLegacyMenuItem', 1)[0]
        labels = ['Создать новый проект', 'Загрузить проект', 'Удалить проект', 'Экспорт проекта', 'Импорт проекта', 'Выход']
        positions = [home.index('addLegacyMenuItem("'+label+'"') for label in labels]
        self.assertEqual(sorted(positions), positions)
        self.assertEqual(8, home.count('addLegacyMenuItem('))
        self.assertIn('addLegacyMenuItem("Восстановить проект", this::chooseTrashedProject', home)
        self.assertIn('addLegacyMenuItem("USB Serial • Modbus RTU", this::showUsbSerialScreen', home)
        usb = source.split('private void showUsbSerialScreen()', 1)[1].split('private ArrayAdapter<String> simpleSpinnerAdapter', 1)[0]
        self.assertIn('manualUsbConnector.listDevices()', usb)
        self.assertIn('.connect(selected.id', usb)
        self.assertIn('connection.read(unit, function, address, count)', usb)
        self.assertIn('action("Отключить"', usb)
        self.assertIn('screenGeneration != manualUsbScreenGeneration', usb)
        # Keep the explicitly selected RTU session while the operator loads a project from home.
        self.assertNotIn('closeManualUsbConnection()', home)
        self.assertIn('closeManualUsbConnection()', usb)
        self.assertIn('closeManualUsbConnection()', source.split('protected void onDestroy()', 1)[1].split('@Override', 1)[0])
        back = source.split('public void onBackPressed()', 1)[1].split('private String currentScreenName()', 1)[0]
        self.assertIn('if (manualUsbScreenVisible) { showHome(); return; }', back)
        self.assertIn('R.drawable.kc_main_background_small', home)
        self.assertIn('root.setWeightSum(1.0f)', home)
        self.assertIn('SCREEN_ORIENTATION_UNSPECIFIED', home)
        button = source.split('private Button legacyMenuButton(', 1)[1].split('private void showHome()', 1)[0]
        self.assertIn('R.drawable.kc_background_button', button)
        self.assertNotIn('new GradientDrawable', button)
        self.assertIn('button.setAllCaps(true)', button)
        editor = source.split('private void showEditor()', 1)[1].split('private String currentScreenName()', 1)[0]
        self.assertIn('SCREEN_ORIENTATION_LANDSCAPE', editor)
        self.assertIn('WindowManager.LayoutParams.FLAG_FULLSCREEN', source)
        for name in ['background_button.png', 'main_background_small.png']:
            original = ROOT / 'build/craft-reference/apktool/res/drawable' / name
            compiled = ROOT / 'app/src/main/res/drawable-nodpi' / ('kc_'+name)
            self.assertEqual(original.read_bytes(), compiled.read_bytes())

if __name__ == '__main__':
    unittest.main()
