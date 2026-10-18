import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
from android_ui_smoke import Smoke
from android_ui_smoke import center, matching_nodes, preferences_are_empty, properties_scroll_node, property_input_matches, property_scroll_up, keyboard_is_shown, document_nodes

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

    def test_observed_fields_override_stale_scroll_history_after_invalid_apply(self):
        # Invalid Apply auto-scrolls the form to X. Width is now below the viewport,
        # even though the last edited field was Height (the real API30 failure).
        self.assertTrue(property_scroll_up('Ширина элемента', 'Высота элемента', ['Положение X']))
        self.assertFalse(property_scroll_up('Положение X', 'Положение Y', ['Ширина элемента','Высота элемента']))
        self.assertTrue(property_scroll_up('Время таймера, мс', visible_descriptions=[]))

    def test_keyboard_visibility_uses_observed_input_method_state(self):
        self.assertTrue(keyboard_is_shown('mInputShown=true mShowRequested=true'))
        self.assertFalse(keyboard_is_shown('mInputShown=false'))
        self.assertFalse(keyboard_is_shown('unknown'))

    def test_document_grid_uses_filename_description_without_matching_preview_action(self):
        xml = '<hierarchy><node package="com.android.documentsui" content-desc="bloodsky-custom.png, 15.53 kB, 1:17 AM" enabled="true" bounds="[10,20][110,120]"/><node package="com.android.documentsui" content-desc="Preview the file bloodsky-custom.png" enabled="true" bounds="[80,20][110,50]"/></hierarchy>'
        nodes = document_nodes(xml, 'bloodsky-custom.png')
        self.assertEqual(1, len(nodes))
        self.assertEqual((60,70), center(nodes[0].get('bounds')))
        self.assertEqual([], document_nodes(xml, 'unknown.png'))
        text_xml = '<hierarchy><node package="com.android.documentsui" text="bloodsky-custom.png" enabled="true" bounds="[10,20][110,120]"/></hierarchy>'
        self.assertEqual(1, len(document_nodes(text_xml, 'bloodsky-custom.png')))

    def test_invalid_bounds_are_rejected(self):
        for bounds in ['bad', '[1,2][1,2]', '[100,20][10,30]']:
            with self.subTest(bounds=bounds), self.assertRaises(ValueError):
                center(bounds)

    def test_document_selection_switches_grid_to_list_and_opens_exact_filename(self):
        grid = '<hierarchy><node package="com.android.documentsui" content-desc="List view" enabled="true" bounds="[200,20][300,80]"/></hierarchy>'
        listing = '<hierarchy><node package="com.android.documentsui" text="bloodsky-custom.png" enabled="true" bounds="[50,100][350,200]"/><node package="com.android.documentsui" text="other.png" enabled="true" bounds="[50,200][350,300]"/></hierarchy>'
        with TemporaryDirectory() as folder, patch('android_ui_smoke.adb') as command:
            smoke = Smoke(Path(folder))
            with patch.object(smoke, 'dump', side_effect=[grid, listing, listing, '<hierarchy><node package="com.bloodsky.hmi"/></hierarchy>']):
                smoke.tap_document('bloodsky-custom.png')
            self.assertEqual([('shell', 'input', 'tap', '250', '50'),
                              ('shell', 'CLASSPATH=/data/local/tmp/bloodsky-ui-input.jar',
                               'app_process', '/system/bin', 'BloodskyFingerTap', '200', '150')],
                             [c.args for c in command.call_args_list])

    def test_custom_image_waits_for_media_index_before_picker(self):
        with TemporaryDirectory() as folder, patch('android_ui_smoke.adb', side_effect=['', '', 'No result found.', 'Row: 0 _display_name=bloodsky-custom.png']) as command, patch('android_ui_smoke.time.sleep'):
            Smoke(Path(folder)).prepare_custom_image()
            self.assertEqual(4, command.call_count)
            self.assertIn('android.intent.action.MEDIA_SCANNER_SCAN_FILE', command.call_args_list[1].args)

    def test_unindexed_image_fails_with_diagnostics(self):
        with TemporaryDirectory() as folder, patch('android_ui_smoke.adb', return_value='No result found.'), patch('android_ui_smoke.time.monotonic', side_effect=[0, 0, 31]), patch('android_ui_smoke.time.sleep'):
            with self.assertRaisesRegex(AssertionError, 'not indexed'):
                Smoke(Path(folder)).prepare_custom_image()
            self.assertEqual('No result found.', (Path(folder) / 'media-index.txt').read_text())

    def test_grid_document_selection_waits_for_app_return(self):
        grid = '<hierarchy><node package="com.android.documentsui" content-desc="bloodsky-custom.png, 15 kB" enabled="true" bounds="[50,100][350,300]"/></hierarchy>'
        app = '<hierarchy><node package="com.bloodsky.hmi"/></hierarchy>'
        with TemporaryDirectory() as folder, patch('android_ui_smoke.adb') as command:
            smoke = Smoke(Path(folder))
            with patch.object(smoke, 'dump', side_effect=[grid, grid, grid, '<hierarchy/>', app]) as dump:
                smoke.tap_document('bloodsky-custom.png')
            self.assertEqual(5, dump.call_count)
            self.assertEqual('200', command.call_args.args[-1])

    def test_ambiguous_grid_documents_fail_without_tapping(self):
        node = '<node package="com.android.documentsui" content-desc="bloodsky-custom.png, 15 kB" enabled="true" bounds="[50,100][350,300]"/>'
        with TemporaryDirectory() as folder, patch('android_ui_smoke.adb') as command:
            smoke = Smoke(Path(folder))
            with patch.object(smoke, 'dump', return_value='<hierarchy>' + node * 2 + '</hierarchy>'):
                with self.assertRaisesRegex(AssertionError, 'Ambiguous document'):
                    smoke.tap_document('bloodsky-custom.png')
            command.assert_not_called()

    def test_deletion_requires_both_active_and_catalog_to_be_empty(self):
        self.assertTrue(preferences_are_empty('<map><string name="project_catalog_v1">{}</string></map>'))
        self.assertFalse(preferences_are_empty('<map><string name="active_project">{}</string></map>'))
        self.assertFalse(preferences_are_empty('<map><string name="project_catalog_v1">{"project":{}}</string></map>'))

class PropertyFocusWaitTests(unittest.TestCase):
    def xml(self, focused):
        return '<hierarchy><node class="android.widget.EditText" content-desc="timer" text="1000" enabled="true" focused="' + focused + '" bounds="[1,1][100,40]"/></hierarchy>'

    def test_existing_field_waits_for_focus_transition_before_typing(self):
        with TemporaryDirectory() as temp:
            smoke = Smoke(Path(temp))
            with patch.object(smoke, 'dump', side_effect=[self.xml('false'), self.xml('true')]) as dump, patch('android_ui_smoke.time.sleep'):
                node = smoke.wait_node(description='timer', class_name='android.widget.EditText', focused=True)
            self.assertEqual(node.get('focused'), 'true')
            self.assertEqual(dump.call_count, 2)

    def test_existing_unfocused_field_cannot_pass_focus_requirement(self):
        with TemporaryDirectory() as temp:
            smoke = Smoke(Path(temp))
            with patch.object(smoke, 'dump', return_value=self.xml('false')), patch('android_ui_smoke.time.monotonic', side_effect=[0, 0, 2]), patch('android_ui_smoke.time.sleep'):
                with self.assertRaises(AssertionError):
                    smoke.wait_node(description='timer', focused=True, timeout=1)
            self.assertIn('focused="false"', (Path(temp) / 'last-ui.xml').read_text())


if __name__ == '__main__':
    unittest.main()
