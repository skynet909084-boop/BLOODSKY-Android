import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
import xml.etree.ElementTree as ET
from android_ui_smoke import Smoke
from android_ui_smoke import center, matching_nodes, preferences_are_empty, properties_scroll_node, property_input_matches, property_scroll_up, keyboard_is_shown, document_nodes, kill_background_process_as_app, kill_background_app_process, linux_process_state, wait_for_app_process_exit, project_media_asset_sha256, project_catalog_storage_id, network_device_slot_controls, WIFI_TRANSPORT_LABEL

class AndroidSmokeHelpersTests(unittest.TestCase):
    def test_saved_activity_gate_accepts_stopped_main_activity_with_saved_state(self):
        dump = ('ActivityRecord{1 u0 com.bloodsky.hmi/.MainActivity t1}\n'
                '  mHaveState=true\n  state=STOPPED stopped=true\n'
                'ActivityRecord{2 u0 com.android.documentsui/.picker.PickActivity t1}')
        with TemporaryDirectory() as folder:
            with patch('android_ui_smoke.adb', return_value=dump) as command:
                Smoke(Path(folder)).wait_for_saved_main_activity()
            command.assert_called_once_with('shell', 'dumpsys', 'activity', 'activities')

    def test_saved_activity_gate_writes_diagnostics_when_state_is_missing(self):
        dump = 'ActivityRecord{1 u0 com.bloodsky.hmi/.MainActivity t1}\n  state=PAUSED'
        with TemporaryDirectory() as folder:
            with patch('android_ui_smoke.adb', return_value=dump), \
                 patch('android_ui_smoke.time.monotonic', side_effect=[0, 0, 2]), \
                 patch('android_ui_smoke.time.sleep'):
                with self.assertRaisesRegex(AssertionError, 'did not reach STOPPED with saved state'):
                    Smoke(Path(folder)).wait_for_saved_main_activity(timeout=1)
                self.assertEqual(dump, (Path(folder) / 'media-picker-activity-state.txt').read_text())

    def test_process_state_parses_spaces_in_proc_comm(self):
        self.assertEqual('Z', linux_process_state('12502 (com.bloodsky hmi) Z 1 2 3'))

    def test_wait_for_original_app_pids_accepts_reaped_pid_and_zombie(self):
        from types import SimpleNamespace
        with patch('android_ui_smoke.subprocess.run', side_effect=[
                SimpleNamespace(returncode=0, stdout='12502 (com.bloodsky.hmi) Z 1 2 3', stderr=''),
                SimpleNamespace(returncode=1, stdout='', stderr='cat: /proc/12503/stat: No such file or directory')]):
            states = wait_for_app_process_exit('com.bloodsky.hmi', ['12502', '12503'])
        self.assertEqual({'12502': 'Z', '12503': None}, states)

    def test_wait_for_original_app_pids_rejects_live_process(self):
        from types import SimpleNamespace
        with patch('android_ui_smoke.subprocess.run', return_value=SimpleNamespace(
                returncode=0, stdout='12502 (com.bloodsky.hmi) S 1 2 3', stderr='')), \
             patch('android_ui_smoke.time.monotonic', side_effect=[0, 1]), \
             patch('android_ui_smoke.time.sleep'):
            with self.assertRaisesRegex(RuntimeError, "'12502': 'S'"):
                wait_for_app_process_exit('com.bloodsky.hmi', ['12502'], timeout=0.5)

    def test_background_activity_kill_uses_activity_manager(self):
        from types import SimpleNamespace
        with patch('android_ui_smoke.subprocess.run', return_value=SimpleNamespace(
                returncode=0, stdout='', stderr='')) as command:
            result = kill_background_app_process('com.bloodsky.hmi')
        self.assertEqual(0, result.returncode)
        command.assert_called_once_with(['adb', 'shell', 'am', 'kill', 'com.bloodsky.hmi'],
                                        capture_output=True, text=True, timeout=45)

    def test_wifi_transport_selector_matches_observed_backslash_label(self):
        xml = ('<hierarchy><node text="Wi-Fi\\3G\\4G" enabled="true" '
               'bounds="[10,20][110,80]"/></hierarchy>')
        self.assertEqual(r'Wi-Fi\3G\4G', WIFI_TRANSPORT_LABEL)
        self.assertEqual(1, len(matching_nodes(xml, text=WIFI_TRANSPORT_LABEL)))

    def test_network_device_screen_exposes_one_slot_count_and_add_button(self):
        xml = ('<hierarchy><node resource-id="com.bloodsky.hmi:id/craft_textCountDevice" '
               'text="0" enabled="true" bounds="[100,10][140,50]"/>'
               '<node resource-id="com.bloodsky.hmi:id/craft_plus" class="android.widget.ImageView" '
               'enabled="true" bounds="[200,10][240,50]"/></hierarchy>')
        count, plus = network_device_slot_controls(xml)
        self.assertEqual('0', count.get('text'))
        self.assertEqual('[200,10][240,50]', plus.get('bounds'))

    def test_network_device_slot_controls_fail_closed_on_missing_or_ambiguous_nodes(self):
        for xml in (
                '<hierarchy/>',
                '<hierarchy><node resource-id="com.bloodsky.hmi:id/craft_textCountDevice" text="0"/>'
                '<node resource-id="com.bloodsky.hmi:id/craft_textCountDevice" text="1"/>'
                '<node resource-id="com.bloodsky.hmi:id/craft_plus"/></hierarchy>'):
            with self.subTest(xml=xml), self.assertRaisesRegex(AssertionError, 'one slot count'):
                network_device_slot_controls(xml)

    def test_project_media_asset_path_returns_digest_only_for_owned_asset_names(self):
        digest = 'a' * 64
        self.assertEqual(digest, project_media_asset_sha256('media/asset_' + digest + '.png'))
        for path in ('content://provider/image/1', '../media/asset_' + digest + '.png',
                     'media/asset_' + digest + '.PNG', 'media/asset_' + digest):
            with self.subTest(path=path):
                self.assertIsNone(project_media_asset_sha256(path))

    def test_project_storage_id_is_read_from_the_persisted_catalog(self):
        import json
        import xml.etree.ElementTree as ET
        storage_id = 'stable-project-id'
        prefs = ET.Element('map')
        ET.SubElement(prefs, 'string', {'name': 'active_project'}).text = '{"projectName":"Smoke_Project"}'
        ET.SubElement(prefs, 'string', {'name': 'project_catalog_v1'}).text = json.dumps({
            'proj_123': {'projectName': 'Smoke_Project', 'storageId': storage_id}
        })
        xml = ET.tostring(prefs, encoding='unicode')
        self.assertEqual(storage_id, project_catalog_storage_id(xml, 'Smoke_Project'))
        self.assertIsNone(project_catalog_storage_id(xml, 'Missing_Project'))

    def test_background_process_kill_uses_the_debuggable_app_uid(self):
        from types import SimpleNamespace
        with patch('android_ui_smoke.subprocess.run', side_effect=[
                SimpleNamespace(returncode=0, stdout='1234', stderr=''),
                SimpleNamespace(returncode=0, stdout='', stderr=''),
                SimpleNamespace(returncode=0, stdout='', stderr='')]) as command, \
                patch('android_ui_smoke.time.sleep'):
            kill_background_process_as_app('com.bloodsky.hmi', ['1234'])
        self.assertEqual(['adb', 'shell', 'run-as', 'com.bloodsky.hmi', 'kill', '-9', '1234'], command.call_args_list[1].args[0])
        with self.assertRaisesRegex(ValueError, 'process ID'):
            kill_background_process_as_app('com.bloodsky.hmi', [])

    def test_background_process_kill_accepts_pid_exit_race_after_confirmation(self):
        from types import SimpleNamespace
        with patch('android_ui_smoke.subprocess.run', side_effect=[
                SimpleNamespace(returncode=0, stdout='1234', stderr=''),
                SimpleNamespace(returncode=1, stdout='', stderr="kill: unknown pid '1234'"),
                SimpleNamespace(returncode=1, stdout='', stderr='')]) as command, \
                patch('android_ui_smoke.time.sleep'):
            kill_background_process_as_app('com.bloodsky.hmi', ['1234'])
        self.assertEqual(3, command.call_count)

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

    def test_documentsui_downloads_selects_root_row_not_breadcrumb(self):
        xml = ('<hierarchy>'
               '<node text="Downloads" resource-id="com.android.documentsui:id/breadcrumb_text" '
               'class="android.widget.TextView" enabled="true" bounds="[0,20][120,60]"/>'
               '<node text="Downloads" resource-id="android:id/title" '
               'class="android.widget.TextView" enabled="true" bounds="[10,100][130,140]"/>'
               '</hierarchy>')
        nodes = matching_nodes(xml, text='Downloads', resource_id='android:id/title')
        self.assertEqual(1, len(nodes))
        self.assertEqual((70, 120), center(nodes[0].get('bounds')))

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
            with patch.object(smoke, 'dump', return_value='<hierarchy>' + node + node + '</hierarchy>'):
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

    def test_dialog_input_replaces_existing_text_and_verifies_focused_value(self):
        old = ET.fromstring(self.xml('true').replace('text="1000"', 'text="192.168.0.1"'))[0]
        new = ET.fromstring(self.xml('true').replace('text="1000"', 'text="192.168.0.155"'))[0]
        with TemporaryDirectory() as temp, patch('android_ui_smoke.adb') as command:
            smoke = Smoke(Path(temp))
            with patch.object(smoke, 'wait_node', side_effect=[old, new]):
                smoke.replace_focused_dialog_input('IP адрес подключения', '192.168.0.155')
            calls = [item.args for item in command.call_args_list]
            self.assertEqual(('shell', 'input', 'keyevent', '123', *(['67'] * 12)), calls[0])
            self.assertEqual(('shell', 'input', 'text', '192.168.0.155'), calls[1])

    def test_dialog_input_rejects_value_not_observed_after_typing(self):
        old = ET.fromstring(self.xml('true').replace('text="1000"', 'text="502"'))[0]
        wrong = ET.fromstring(self.xml('true').replace('text="1000"', 'text="5024001"'))[0]
        with TemporaryDirectory() as temp, patch('android_ui_smoke.adb') as command:
            smoke = Smoke(Path(temp))
            with patch.object(smoke, 'wait_node', side_effect=[old, wrong]):
                with self.assertRaisesRegex(AssertionError, 'did not match requested value'):
                    smoke.replace_focused_dialog_input('Порт подключения', '4001')
            self.assertEqual(2, command.call_count)


if __name__ == '__main__':
    unittest.main()
