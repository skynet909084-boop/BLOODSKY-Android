"""Source and asset guards for the Craft component ID 1 switch gap."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class OriginalCraftSwitchTests(unittest.TestCase):
    def test_all_source_switch_state_assets_are_packaged(self):
        assets = ROOT / "app/src/main/res/drawable-nodpi"
        for skin in range(1, 9):
            for state in ("on", "off"):
                with self.subTest(skin=skin, state=state):
                    path = assets / f"kc_switch{skin}_{state}.png"
                    self.assertTrue(path.is_file(), path)
                    self.assertGreater(path.stat().st_size, 100)

    def test_palette_and_import_use_distinct_craft_switch_type(self):
        palette = (ROOT / "app/src/main/java/com/bloodsky/hmi/OriginalComponentPalette.java").read_text()
        importer = (ROOT / "app/src/main/java/com/bloodsky/hmi/CraftProjectImporter.java").read_text()
        self.assertIn("add.add(OriginalCraftSwitch.TYPE, \"Переключатель\")", palette)
        self.assertIn('if (componentId == 1) type = OriginalCraftSwitch.TYPE', importer)
        self.assertIn('widget.put("craftSwitchBitEnabled", false)', importer)
        self.assertIn('widget.put("craftOther", other)', importer)

    def test_renderer_uses_original_switch_state_image_pair(self):
        renderer = (ROOT / "app/src/main/java/com/bloodsky/hmi/OriginalCraftSwitchRenderer.java").read_text()
        model = (ROOT / "app/src/main/java/com/bloodsky/hmi/OriginalCraftSwitch.java").read_text()
        main = (ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java").read_text()
        self.assertIn('"kc_switch" + imagePosition', model)
        self.assertIn("OriginalCraftSwitchRenderer.draw", main)
        self.assertIn("writeMask(binding.unit, binding.address, masks[0], masks[1])", main)


if __name__ == "__main__":
    unittest.main()
