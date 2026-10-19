import unittest
import xml.etree.ElementTree as ET

from original_craft_probe_state import is_project_loading


class OriginalCraftProbeStateTest(unittest.TestCase):
    def test_detects_project_loading_text(self):
        root = ET.fromstring('<hierarchy><node text="Загрузка проекта, подождите... 03:05" /></hierarchy>')
        self.assertTrue(is_project_loading(root))

    def test_detects_loading_description_and_spelling_variant(self):
        root = ET.fromstring('<hierarchy><node content-desc="Загруска проекта" /></hierarchy>')
        self.assertTrue(is_project_loading(root))

    def test_does_not_mark_normal_editor_as_loading(self):
        root = ET.fromstring('<hierarchy><node text="Редактор проекта" /><node text="Создать" /></hierarchy>')
        self.assertFalse(is_project_loading(root))


if __name__ == "__main__":
    unittest.main()
