import unittest
from android_ui_smoke import center, matching_nodes, preferences_are_empty, properties_scroll_node, property_input_matches, property_scroll_up, keyboard_is_shown

class AndroidSmokeHelpersTests(unittest.TestCase):
    def test_observed_enabled_ui_targets_and_uppercase_russian(self):
        xml = '<hierarchy><node text="СОЗДАТЬ НОВЫЙ ПРОЕКТ" class="android.widget.Button" enabled="true" bounds="[10,20][110,80]"/><node text="Создать новый проект" enabled="false" bounds="[10,20][110,80]"/></hierarchy>'
        nodes = matching_nodes(xml, 'Создать новый проект')
        self.assertEqual(1, len(nodes))
        self.assertEqual((60, 50), center(nodes[0].get('bounds')))
        self.assertEqual([], matching_nodes(xml, class_name='android.widget.EditText'))

    def test_icon_is_targeted_by_accessibility_description(self):
        xml = '<hierarchy><node text="" content-desc="Калькулятор" class="android.widget.ImageButton" enabled="true" bounds="[10,20][110,80]"/></hierarchy>'
        self.assertEqual(1, len(matching_nodes(xml, description='Калькулятор')))
        self.assertEqual([], matching_nodes(xml, description='Unknown'))

    def test_properties_scroll_ignores_scrollable_button_bar(self):
        xml = '<hierarchy><node class="android.widget.ScrollView" resource-id="form"><node class="android.widget.EditText"/></node><node class="android.widget.ScrollView" resource-id="buttons"><node class="android.widget.Button"/></node></hierarchy>'
        self.assertEqual('form', properties_scroll_node(xml).get('resource-id'))
        with self.assertRaises(AssertionError):
            properties_scroll_node('<hierarchy/>')

    def test_empty_numeric_field_accessibility_hint_is_not_treated_as_entered_value(self):
        import xml.etree.ElementTree as ET
        node = ET.fromstring('<node text="Ширина" content-desc="Ширина элемента"/>')
        self.assertTrue(property_input_matches(node, ''))
        self.assertFalse(property_input_matches(node, '160.375'))
        node.set('text', '160.375')
        self.assertTrue(property_input_matches(node, '160.375'))
        self.assertFalse(property_input_matches(node, ''))
        node.set('text', 'unexpected')
        self.assertFalse(property_input_matches(node, ''))

    def test_property_scroll_direction_can_return_to_previous_field(self):
        self.assertTrue(property_scroll_up('Положение X'))
        self.assertTrue(property_scroll_up('Высота элемента', 'Ширина элемента'))
        self.assertFalse(property_scroll_up('Ширина элемента', 'Высота элемента'))

    def test_keyboard_visibility_uses_observed_input_method_state(self):
        self.assertTrue(keyboard_is_shown('mInputShown=true mShowRequested=true'))
        self.assertFalse(keyboard_is_shown('mInputShown=false'))
        self.assertFalse(keyboard_is_shown('unknown'))

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
