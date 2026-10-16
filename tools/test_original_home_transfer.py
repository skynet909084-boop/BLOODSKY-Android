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

    def test_home_uses_original_images_and_six_actions(self):
        source = (ROOT / 'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
        home = source.split('private void showHome()', 1)[1].split('private void addLegacyMenuItem', 1)[0]
        labels = ['Создать новый проект', 'Загрузить проект', 'Удалить проект', 'Экспорт проекта', 'Импорт проекта', 'Выход']
        positions = [home.index('addLegacyMenuItem("'+label+'"') for label in labels]
        self.assertEqual(sorted(positions), positions)
        self.assertEqual(6, home.count('addLegacyMenuItem('))
        self.assertIn('R.drawable.kc_main_background_small', home)
        button = source.split('private Button legacyMenuButton(', 1)[1].split('private void showHome()', 1)[0]
        self.assertIn('R.drawable.kc_background_button', button)
        self.assertNotIn('new GradientDrawable', button)
        for name in ['background_button.png', 'main_background_small.png']:
            original = ROOT / 'build/craft-reference/apktool/res/drawable' / name
            compiled = ROOT / 'app/src/main/res/drawable-nodpi' / ('kc_'+name)
            self.assertEqual(original.read_bytes(), compiled.read_bytes())

if __name__ == '__main__':
    unittest.main()
