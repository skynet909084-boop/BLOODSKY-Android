import unittest
from android_ui_smoke import center, matching_nodes, preferences_are_empty

class AndroidSmokeHelpersTests(unittest.TestCase):
    def test_observed_enabled_ui_targets_and_uppercase_russian(self):
        xml = '<hierarchy><node text="СОЗДАТЬ НОВЫЙ ПРОЕКТ" class="android.widget.Button" enabled="true" bounds="[10,20][110,80]"/><node text="Создать новый проект" enabled="false" bounds="[10,20][110,80]"/></hierarchy>'
        nodes = matching_nodes(xml, 'Создать новый проект')
        self.assertEqual(1, len(nodes))
        self.assertEqual((60, 50), center(nodes[0].get('bounds')))
        self.assertEqual([], matching_nodes(xml, class_name='android.widget.EditText'))

    def test_invalid_bounds_are_rejected(self):
        for bounds in ['bad', '[1,2][1,2]', '[100,20][10,30]']:
            with self.subTest(bounds=bounds), self.assertRaises(ValueError):
                center(bounds)

    def test_deletion_requires_both_active_and_catalog_to_be_empty(self):
        self.assertTrue(preferences_are_empty('<map><string name="project_catalog_v1">{}</string></map>'))
        self.assertFalse(preferences_are_empty('<map><string name="active_project">{}</string></map>'))
        self.assertFalse(preferences_are_empty('<map><string name="project_catalog_v1">{"project":{}}</string></map>'))

if __name__ == '__main__':
    unittest.main()
