#!/usr/bin/env python3
"""Install and exercise the debug APK on an already booted, disposable emulator."""
import argparse
import io
import wave
from png_pixel_probe import solid_rgba_png, count_matching_png_pixels
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import traceback
import xml.etree.ElementTree as ET

PACKAGE = 'com.bloodsky.hmi'
WIFI_TRANSPORT_LABEL = r'Wi-Fi\3G\4G'
PROJECT = 'Smoke_Project'


def adb(*args, binary=False):
    result = subprocess.run(['adb', *args], capture_output=True, timeout=45, check=True)
    return result.stdout if binary else result.stdout.decode('utf-8', errors='replace')


def matching_nodes(xml, text=None, class_name=None, description=None, resource_id=None):
    nodes = ET.fromstring(xml).iter('node')
    return [n for n in nodes if (text is None or n.get('text', '').casefold() == text.casefold())
            and (class_name is None or n.get('class') == class_name)
            and (description is None or n.get('content-desc') == description)
            and (resource_id is None or n.get('resource-id') == resource_id)
            and n.get('enabled') == 'true' and n.get('bounds') != '[0,0][0,0]']


def network_device_slot_controls(xml):
    nodes = list(ET.fromstring(xml).iter('node'))
    counts = [node for node in nodes
              if node.get('resource-id', '').endswith('craft_textCountDevice')]
    plus = [node for node in nodes
            if node.get('resource-id', '').endswith('craft_plus')]
    if len(counts) != 1 or len(plus) != 1:
        raise AssertionError('Network device screen must expose one slot count and one add-slot control')
    return counts[0], plus[0]


def center(bounds):
    found = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if not found:
        raise ValueError('Invalid UI bounds: ' + bounds)
    left, top, right, bottom = map(int, found.groups())
    if right <= left or bottom <= top:
        raise ValueError('Empty UI bounds: ' + bounds)
    return (left + right) // 2, (top + bottom) // 2


def project_media_asset_sha256(path):
    match = re.fullmatch(r'media/asset_([0-9a-f]{64})\.[a-z0-9]{1,10}', path or '')
    return match.group(1) if match else None


def project_catalog_storage_id(xml, project_name):
    values = {node.get('name'): node.text for node in ET.fromstring(xml)}
    catalog = json.loads(values.get('project_catalog_v1', '{}'))
    matches = [project for project in catalog.values()
               if project.get('projectName') == project_name]
    if len(matches) != 1:
        return None
    storage_id = matches[0].get('storageId', '')
    return storage_id if storage_id else None


def preferences_are_empty(xml):
    root = ET.fromstring(xml)
    values = {node.get('name'): node.text for node in root}
    return 'active_project' not in values and json.loads(values.get('project_catalog_v1', '{}')) == {}


def document_nodes(xml, filename):
    # Android DocumentsUI grid exposes display name in accessibility description, not text.
    text_nodes = [n for n in matching_nodes(xml, text=filename)
                  if n.get('package') == 'com.android.documentsui']
    if text_nodes:
        return text_nodes
    return [n for n in matching_nodes(xml)
            if n.get('package') == 'com.android.documentsui'
            and n.get('content-desc', '').startswith(filename + ', ')]


def property_scroll_up(target, previous=None, visible_descriptions=()):
    order = ['Время таймера, мс', 'Уставка счётчика', 'Сравнение A', 'Оператор сравнения', 'Сравнение B', 'Положение X', 'Положение Y', 'Ширина элемента', 'Высота элемента']
    visible = [order.index(value) for value in visible_descriptions if value in order]
    # Craft exposes additional component-specific fields that are intentionally not
    # listed in this navigation hint table. Keep searching those fields instead of
    # raising ValueError; a repeated scroll signature reverses direction below.
    if target not in order:
        return True
    if visible:
        if order.index(target) > max(visible): return True
        if order.index(target) < min(visible): return False
    return previous is None or (previous in order and order.index(target) >= order.index(previous))


def keyboard_is_shown(diagnostics):
    return re.search(r'\bmInputShown=true\b', diagnostics) is not None


def kill_background_process_as_app(package, pids):
    if not pids:
        raise ValueError('At least one app process ID is required')
    # The PID can exit between pidof and run-as (observed on API 30). Confirm
    # whether the process still exists before treating an "unknown pid" result
    # as a failure. The selected DocumentsUI task is checked after this helper.
    deadline = time.monotonic() + 5
    last_error = ''
    while time.monotonic() < deadline:
        probe = subprocess.run(['adb', 'shell', 'pidof', package],
                               capture_output=True, text=True, timeout=45)
        if probe.returncode != 0 or not probe.stdout.strip():
            return
        live_pids = set(probe.stdout.split())
        targets = [pid for pid in pids if pid in live_pids]
        if not targets:
            return
        killed = subprocess.run(['adb', 'shell', 'run-as', package,
                                 'kill', '-9', *targets],
                                capture_output=True, text=True, timeout=45)
        if killed.returncode != 0:
            last_error = (killed.stderr or killed.stdout).strip()
        time.sleep(0.25)
    raise RuntimeError('Application process survived kill request: '
                       + (last_error or package))


def linux_process_state(stat_record):
    """Read the state field from /proc/<pid>/stat despite spaces in comm."""
    close = stat_record.rfind(')')
    if close < 0:
        raise ValueError('Malformed /proc process stat record')
    fields = stat_record[close + 1:].strip().split()
    if not fields:
        raise ValueError('Missing process state in /proc stat record')
    return fields[0]


def wait_for_app_process_exit(package, pids, timeout=5):
    """Confirm original app PIDs have exited or entered zombie state."""
    if not pids:
        raise ValueError('At least one app process ID is required')
    deadline = time.monotonic() + timeout
    last_states = {}
    while True:
        for pid in pids:
            probe = subprocess.run(
                ['adb', 'shell', 'run-as', package, 'cat', '/proc/' + pid + '/stat'],
                capture_output=True, text=True, timeout=45, check=False)
            if probe.returncode != 0:
                error = (probe.stderr or probe.stdout).strip()
                if 'No such file or directory' in error or 'No such process' in error:
                    last_states[pid] = None
                    continue
                raise RuntimeError('Could not inspect app process ' + pid + ': ' + error)
            last_states[pid] = linux_process_state(probe.stdout)
        active = {pid: state for pid, state in last_states.items()
                  if state is not None and state != 'Z'}
        if not active:
            return last_states
        if time.monotonic() >= deadline:
            raise RuntimeError('Application process survived kill request: '
                               + repr(active))
        time.sleep(0.25)


def kill_background_app_process(package):
    """Ask ActivityManager to kill a stopped package process, not force-stop it."""
    return subprocess.run(['adb', 'shell', 'am', 'kill', package],
                          capture_output=True, text=True, timeout=45)


def property_input_matches(node, value):
    # API 30 accessibility exposes the hint as text for an empty EditText.
    hints = {'Положение X': 'X', 'Положение Y': 'Y',
             'Ширина элемента': 'Ширина', 'Высота элемента': 'Высота'}
    observed = node.get('text', '')
    return observed == value or (value == '' and observed == hints.get(node.get('content-desc')))


def center_height(bounds):
    center(bounds)  # Validate before calculating the visible height.
    _, top, _, bottom = map(int, re.findall(r'\d+', bounds))
    return bottom - top


def properties_scroll_node(xml):
    nodes = [n for n in ET.fromstring(xml).iter('node')
             if n.get('class') == 'android.widget.ScrollView'
             and any(c.get('class') == 'android.widget.EditText' for c in n.iter('node'))]
    if len(nodes) != 1:
        raise AssertionError('Expected one properties form scroll view')
    return nodes[0]


class Smoke:
    def __init__(self, output):
        self.output = output
        self.source_overflow_checked = False
        output.mkdir(parents=True, exist_ok=True)

    def dump(self):
        adb('shell', 'uiautomator', 'dump', '/sdcard/bloodsky-ui.xml')
        return adb('shell', 'cat', '/sdcard/bloodsky-ui.xml')

    def wait_node(self, text=None, class_name=None, timeout=30, description=None, focused=None, resource_id=None):
        deadline = time.monotonic() + timeout
        last = ''
        while time.monotonic() < deadline:
            try:
                last = self.dump()
                nodes = matching_nodes(last, text, class_name, description, resource_id)
                if len(nodes) == 1 and (focused is None or nodes[0].get('focused') == str(focused).lower()):
                    return nodes[0]
                if len(nodes) > 1:
                    raise RuntimeError('Ambiguous UI target: ' + str(text or class_name))
            except (subprocess.SubprocessError, ET.ParseError):
                pass
            time.sleep(0.3)
        (self.output / 'last-ui.xml').write_text(last)
        raise AssertionError('UI target not found: ' + str(text or description or class_name))

    def wait_text_fragment(self, fragment, timeout=15):
        deadline = time.monotonic() + timeout
        last = ''
        while time.monotonic() < deadline:
            last = self.dump()
            hits = [node for node in ET.fromstring(last).iter('node')
                    if fragment.casefold() in (node.get('text') or '').casefold()
                    and node.get('enabled') == 'true' and node.get('bounds') != '[0,0][0,0]']
            if hits:
                if len(hits) > 1:
                    raise AssertionError('Ambiguous UI text fragment: ' + fragment)
                return hits[0]
            time.sleep(0.25)
        (self.output / 'last-ui.xml').write_text(last)
        raise AssertionError('UI text fragment not found: ' + fragment)

    def wait_for_saved_main_activity(self, timeout=15):
        """Wait until ActivityTaskManager has saved MainActivity behind DocumentsUI."""
        deadline = time.monotonic() + timeout
        last_dump = ''
        while time.monotonic() < deadline:
            last_dump = adb('shell', 'dumpsys', 'activity', 'activities')
            lines = last_dump.splitlines()
            for index, line in enumerate(lines):
                if 'ActivityRecord{' not in line or 'com.bloodsky.hmi/.MainActivity' not in line:
                    continue
                end = next((i for i in range(index + 1, len(lines))
                            if 'ActivityRecord{' in lines[i]), len(lines))
                record = '\n'.join(lines[index:end])
                if re.search(r'\bmHaveState=true\b', record) and re.search(r'\bstate=STOPPED\b', record):
                    return
            time.sleep(0.25)
        diagnostic = self.output / 'media-picker-activity-state.txt'
        diagnostic.write_text(last_dump)
        raise AssertionError(
            'MainActivity did not reach STOPPED with saved state before process termination; '
            'see ' + str(diagnostic)
        )

    def tap(self, text=None, class_name=None, description=None, resource_id=None):
        node = self.wait_node(text, class_name, description=description, resource_id=resource_id)
        x, y = center(node.get('bounds'))
        adb('shell', 'input', 'tap', str(x), str(y))

    def tap_text_fragment(self, fragment):
        node = self.wait_text_fragment(fragment)
        x, y = center(node.get('bounds'))
        adb('shell', 'input', 'tap', str(x), str(y))

    def long_press_fragment(self, fragment, duration_ms=1000):
        node = self.wait_text_fragment(fragment)
        x, y = center(node.get('bounds'))
        adb('shell', 'input', 'swipe', str(x), str(y), str(x), str(y), str(duration_ms))

    def prepare_custom_image(self):
        # ADB push alone does not index the image in API30 MediaStore. The
        # Downloads image-filter view can remain empty until a media scan.
        path = '/sdcard/Download/bloodsky-custom.png'
        adb('push', 'build/craft-reference/apktool/res/drawable/lamp_yellow.png', path)
        adb('shell', 'am', 'broadcast', '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE',
            '-d', 'file://' + path)
        deadline = time.monotonic() + 30
        observed = ''
        while time.monotonic() < deadline:
            observed = adb('shell', 'content', 'query', '--uri',
                           'content://media/external/images/media', '--projection', '_display_name')
            if re.search(r'_display_name=bloodsky-custom\.png(?:,|$)', observed, re.MULTILINE):
                return
            time.sleep(0.3)
        (self.output / 'media-index.txt').write_text(observed)
        raise AssertionError('Custom image was not indexed by MediaStore')

    def tap_document(self, filename):
        xml = self.dump()
        list_buttons = [n for n in matching_nodes(xml, description='List view')
                        if n.get('package') == 'com.android.documentsui']
        if len(list_buttons) == 1:
            x, y = center(list_buttons[0].get('bounds'))
            adb('shell', 'input', 'tap', str(x), str(y))
        # Wait for filename text in the settled list. A grid accessibility row may
        # survive a layout switch briefly; its old center can miss the shorter row.
        deadline = time.monotonic() + 30
        previous = None
        attempts = 0
        while time.monotonic() < deadline:
            xml = self.dump()
            nodes = document_nodes(xml, filename)
            if len(nodes) > 1:
                raise AssertionError('Ambiguous document: ' + filename)
            bounds = nodes[0].get('bounds') if len(nodes) == 1 else None
            if bounds and bounds == previous:
                x, y = center(bounds)
                print('Opening document', filename, 'at', x, y, flush=True)
                adb('shell', 'CLASSPATH=/data/local/tmp/bloodsky-ui-input.jar',
                    'app_process', '/system/bin', 'BloodskyFingerTap', str(x), str(y))
                attempts += 1
                for _ in range(3):
                    xml = self.dump()
                    visible = list(ET.fromstring(xml).iter('node'))
                    if (any(n.get('package') == PACKAGE for n in visible)
                            and not any(n.get('package') == 'com.android.documentsui'
                                        for n in visible)):
                        return
                if attempts >= 3:
                    break
            previous = bounds
        (self.output / 'document-selection-ui.xml').write_text(xml)
        raise AssertionError('Document did not open through system picker: ' + filename)

    def scroll_to_description(self, description):
        previous_signature = None
        previous_direction = None
        for attempt in range(16):
            xml = self.dump()
            nodes = matching_nodes(xml, description=description)
            scroll = properties_scroll_node(xml)
            all_fields = [n for n in scroll.iter('node') if n.get('class') == 'android.widget.EditText']
            full_height = max((center_height(n.get('bounds')) for n in all_fields), default=1)
            if len(nodes) == 1 and center_height(nodes[0].get('bounds')) >= full_height:
                self.last_property_description = description
                return nodes[0]
            left, top, right, bottom = map(int, re.findall(r'\d+', scroll.get('bounds')))
            x = (left + right) // 2
            visible = [n.get("content-desc") for n in all_fields]
            upward = property_scroll_up(description, getattr(self, "last_property_description", None), visible)
            if nodes:
                _, node_top, _, _ = map(int, re.findall(r'\d+', nodes[0].get('bounds')))
                upward = node_top > top
            signature = [(n.get('content-desc'), n.get('bounds'), n.get('text')) for n in all_fields]
            if signature == previous_signature and previous_direction is not None:
                upward = not previous_direction
            previous_signature, previous_direction = signature, upward
            start, end = (bottom - 25, top + (bottom-top)//3) if upward else (top + 25, bottom - (bottom-top)//3)
            adb('shell', 'input', 'swipe', str(x), str(start), str(x), str(end), '300')
        raise AssertionError('Property field not reachable: ' + description)

    def replace_property_input(self, description, value):
        node = self.scroll_to_description(description)
        x, y = center(node.get('bounds'))
        adb('shell', 'input', 'tap', str(x), str(y))
        focused = self.wait_node(description=description, class_name='android.widget.EditText', focused=True, timeout=10)
        if focused.get('focused') != 'true':
            raise AssertionError('Property field did not receive focus: ' + description)
        adb('shell', 'input', 'keyevent', '123', *(['67'] * (len(focused.get('text', '')) + 1)))
        if value:
            adb('shell', 'input', 'text', value)
        xml = self.dump()
        changed = matching_nodes(xml, description=description, class_name='android.widget.EditText')
        if len(changed) != 1 or not property_input_matches(changed[0], value):
            raise AssertionError('Property input did not match requested value: ' + description)
        if keyboard_is_shown(adb('shell', 'dumpsys', 'input_method')):
            adb('shell', 'input', 'keyevent', '4')

    def replace_focused_dialog_input(self, description, value):
        """Replace a focused dialog field and verify what Android actually typed."""
        focused = self.wait_node(description=description, class_name='android.widget.EditText',
                                 focused=True, timeout=10)
        current = focused.get('text', '')
        adb('shell', 'input', 'keyevent', '123', *(['67'] * (len(current) + 1)))
        if value:
            adb('shell', 'input', 'text', value)
        changed = self.wait_node(description=description, class_name='android.widget.EditText',
                                 focused=True, timeout=10)
        if changed.get('text', '') != value:
            raise AssertionError('Dialog input did not match requested value: ' + description)

    def capture(self, name):
        (self.output / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        (self.output / (name + '.xml')).write_text(self.dump())

    def capture_until_pixels(self, name, color, tolerance, minimum, timeout_seconds=8):
        deadline = time.monotonic() + timeout_seconds
        screenshot = b''
        count = 0
        while True:
            screenshot = adb('exec-out', 'screencap', '-p', binary=True)
            count = count_matching_png_pixels(screenshot, color, tolerance=tolerance)
            if count >= minimum or time.monotonic() >= deadline:
                break
            time.sleep(0.25)
        (self.output / (name + '.png')).write_bytes(screenshot)
        (self.output / (name + '.xml')).write_text(self.dump())
        return count

    def tap_editor_menu_item(self, item):
        self.tap(description='Меню проекта')
        xml = self.dump()
        if matching_nodes(xml, text='Дополнительные действия BLOODSKY…'):
            if not self.source_overflow_checked:
                required_rows = (
                    'Сетевые устройства', 'Пароль проекта', 'Режим станции: Мастер',
                    'Показывать тайм-ауты', 'Modbus TCP Slave: выключен', 'Работа в фоне')
                visible_text = {node.get('text', '') for node in ET.fromstring(xml).iter('node')
                                if node.get('bounds') != '[0,0][0,0]'}
                missing = [label for label in required_rows if label not in visible_text]
                if missing:
                    raise AssertionError('Source overflow menu is missing rows: ' + ', '.join(missing))
                self.tap('Сетевые устройства')
                self.wait_node('Количество:')
                count_node, plus_node = network_device_slot_controls(self.dump())
                if count_node.get('text') != '0':
                    raise AssertionError('A new smoke project must start with zero network-device slots')
                for expected_count in range(1, 17):
                    xml = self.dump()
                    count_node, plus_node = network_device_slot_controls(xml)
                    if count_node.get('text') != str(expected_count - 1):
                        raise AssertionError('Network-device count changed unexpectedly before plus: '
                                             + repr(count_node.get('text')))
                    x, y = center(plus_node.get('bounds'))
                    adb('shell', 'input', 'tap', str(x), str(y))
                    deadline = time.monotonic() + 5
                    while time.monotonic() < deadline:
                        count_node, _ = network_device_slot_controls(self.dump())
                        if count_node.get('text') == str(expected_count):
                            break
                        time.sleep(0.2)
                    else:
                        raise AssertionError('Adding network-device slot did not reach '
                                             + str(expected_count))
                xml = self.dump()
                count_node, plus_node = network_device_slot_controls(xml)
                x, y = center(plus_node.get('bounds'))
                adb('shell', 'input', 'tap', str(x), str(y))
                time.sleep(0.5)
                count_node, _ = network_device_slot_controls(self.dump())
                if count_node.get('text') != '16':
                    raise AssertionError('Network-device slot count must be capped at 16')
                self.capture('01b-craft-network-devices')
                self.tap(description='Сервер устройства 1')
                self.wait_node('Редактор серверов')
                self.capture('01c-craft-server-list')
                self.tap(description='Новое подключение')
                self.tap(WIFI_TRANSPORT_LABEL)
                self.capture('01d-craft-wifi-form')
                ip = self.wait_node(class_name='android.widget.EditText', description='IP адрес подключения')
                x, y = center(ip.get('bounds'))
                adb('shell', 'input', 'tap', str(x), str(y))
                self.wait_node(description='IP адрес подключения', class_name='android.widget.EditText',
                               focused=True, timeout=10)
                self.replace_focused_dialog_input('IP адрес подключения', '192.168.0.155')
                # In landscape Android 11 promotes EditText to a fullscreen IME.
                # The IME action labelled NEXT reliably moves focus to the port;
                # tapping the hidden dialog's stale bounds typed into the IP again.
                adb('shell', 'input', 'keyevent', '66')
                self.wait_node(description='Порт подключения', class_name='android.widget.EditText',
                               focused=True, timeout=10)
                self.replace_focused_dialog_input('Порт подключения', '4001')
                if keyboard_is_shown(adb('shell', 'dumpsys', 'input_method')):
                    adb('shell', 'input', 'keyevent', '4')
                self.wait_node(text='192.168.0.155', class_name='android.widget.EditText',
                               description='IP адрес подключения')
                self.wait_node(text='4001', class_name='android.widget.EditText',
                               description='Порт подключения')
                self.tap('RTU', class_name='android.widget.CheckBox')
                self.tap('Создать')
                self.wait_text_fragment('192.168.0.155')
                self.wait_text_fragment('4001')
                prefs = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
                active = json.loads({n.get('name'): n.text for n in ET.fromstring(prefs)}['active_project'])
                saved = active.get('craftServerConnections', [])
                if (len(saved) != 1 or saved[0].get('endpoint') != '192.168.0.155'
                        or saved[0].get('port') != '4001' or saved[0].get('rtu') is not True):
                    raise AssertionError('Craft server connection fields were not persisted')
                self.long_press_fragment('192.168.0.155')
                self.tap('Редактировать')
                self.wait_node(text='192.168.0.155', class_name='android.widget.EditText', description='IP адрес подключения')
                self.wait_node(text='4001', class_name='android.widget.EditText', description='Порт подключения')
                self.tap('Ок')
                self.wait_text_fragment('192.168.0.155')
                self.tap_text_fragment('192.168.0.155')
                self.wait_node(description='Сервер устройства 1')
                prefs = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
                active = json.loads({n.get('name'): n.text for n in ET.fromstring(prefs)}['active_project'])
                if active.get('craftNetworkDevices', [])[0].get('serverIndex') != 0:
                    raise AssertionError('Craft server row tap did not assign the selected server to the device slot')
                self.capture('01e-craft-device-server-assigned')
                adb('shell', 'input', 'keyevent', '4')
                self.wait_node(description='Редактор экрана')
                self.source_overflow_checked = True
                self.tap(description='Меню проекта')
            self.tap('Дополнительные действия BLOODSKY…')
        for attempt in range(7):
            xml = self.dump()
            available = matching_nodes(xml, text=item)
            if len(available) == 1:
                x, y = center(available[0].get('bounds'))
                adb('shell', 'input', 'tap', str(x), str(y))
                return
            if len(available) > 1:
                raise AssertionError('Ambiguous project command: ' + item)
            scrollable = [n for n in ET.fromstring(xml).iter('node')
                          if n.get('scrollable') == 'true'
                          and n.get('bounds') != '[0,0][0,0]']
            if not scrollable:
                raise AssertionError('Project command not reachable: ' + item)
            left, top, right, bottom = map(int, re.findall(r'\d+', scrollable[-1].get('bounds')))
            x = (left + right) // 2
            adb('shell', 'input', 'swipe', str(x), str(bottom-20), str(x), str(top+20), '300')
        raise AssertionError('Project menu did not reveal: ' + item)

    def saved_connections(self):
        xml = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
        active = json.loads({n.get('name'): n.text for n in ET.fromstring(xml)}['active_project'])
        return active['screens'][0].get('connections', [])

    def check_fbd_palette_and_properties(self, saved_widgets):
        baseline = saved_widgets()
        self.tap(description='Добавить элемент')
        self.tap(description='Функциональные блоки и другие элементы')
        self.wait_node(description='Добавить: FBD Таймер')
        # Source RS is now versioned and mapped to a reset-dominant
        # R/S/Q contract; its original Craft preview must be clickable.
        enabled_rs = [n for n in ET.fromstring(self.dump()).iter('node')
                      if n.get('content-desc') == 'Добавить: FBD RS']
        if len(enabled_rs) != 1 or enabled_rs[0].get('enabled') != 'true':
            raise AssertionError('Original source RS preview must expose tested FBD RS')
        if matching_nodes(self.dump(), description='Палитра компонентов'):
            self.tap(description='Палитра компонентов')
        else:
            # The original wide layout has no left arrow. Back dismisses the page.
            adb('shell', 'input', 'keyevent', '4')
            self.wait_node(description='Редактор экрана')
            self.tap(description='Добавить элемент')
        self.wait_node(description='Добавить: Индикатор')
        self.tap(description='Функциональные блоки и другие элементы')
        # The native scroll wrapper keeps lower source rows reachable on shorter screens.
        for attempt in range(12):
            xml = self.dump()
            if matching_nodes(xml, description='Добавить: FBD И'):
                break
            scrolls = [n for n in ET.fromstring(xml).iter('node') if n.get('class') == 'android.widget.ScrollView']
            if len(scrolls) != 1: raise AssertionError('Expected original FBD palette scroll view')
            left, top, right, bottom = map(int, re.findall(r'\d+', scrolls[0].get('bounds')))
            x = (left + right) // 2
            adb('shell', 'input', 'swipe', str(x), str(bottom-180), str(x), str(top+80), '350')
        self.wait_node(description='Добавить: FBD И')
        self.capture('04m-original-fbd-lower-rows')
        self.tap(description='Добавить: FBD И')
        self.wait_node(description='Редактор экрана')
        self.tap(description='Добавить элемент')
        self.tap(description='Функциональные блоки и другие элементы')
        self.tap(description='Добавить: FBD Таймер')
        self.wait_node(description='Редактор экрана')
        widgets = saved_widgets()
        actual_types = [w.get('type') for w in widgets[-2:]]
        if widgets[:-2] != baseline or actual_types != ['FBD И', 'FBD CRAFT TIMER']:
            raise AssertionError(
                'Original palette did not add the requested FBD types independently: '
                + repr(actual_types))
        # Every newly placed FBD block must be visually distinct from all other
        # visible widgets; a previously passing smoke screenshot hid its Q->IN wire
        # underneath the overlapping AND and TON rectangles.
        def overlaps(first, second):
            return (first['x'] < second['x'] + second['w']
                    and first['x'] + first['w'] > second['x']
                    and first['y'] < second['y'] + second['h']
                    and first['y'] + first['h'] > second['y'])
        for idx in (len(widgets)-2, len(widgets)-1):
            placed = widgets[idx]
            for other_idx, other in enumerate(widgets):
                if other_idx == idx or other.get('visible', True) is False:
                    continue
                if overlaps(placed, other):
                    raise AssertionError('New FBD block overlaps visible widget '
                                         + str(other_idx) + ': ' + repr(placed))
        timer = widgets[-1]
        canvas = self.wait_node(description='Редактор экрана')
        left, top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        adb('shell', 'input', 'tap', str(int(left+timer['x']+timer['w']/2)), str(int(top+timer['y']+timer['h']/2)))
        self.wait_node('Свойства: FBD Таймер')
        self.replace_property_input('Время таймера, мс', '0')
        self.tap('Применить')
        self.wait_node('Свойства: FBD Таймер')
        if saved_widgets() != widgets:
            raise AssertionError('Invalid FBD preset partially changed the saved project')
        self.capture('04n-fbd-invalid-preset')
        self.replace_property_input('Время таймера, мс', '2345')
        self.tap('Применить')
        self.wait_node(description='Редактор экрана')
        self.capture('04o-fbd-preset-saved')
        expected = json.loads(json.dumps(widgets))
        expected[-1]['timerPresetMs'] = 2345
        # Applying properties to the original Craft FB_TIMER persists its
        # selected mode and unit alongside the duration, even when they remain
        # at their imported TON/MS defaults.
        expected[-1]['craftTimerMode'] = 'TON'
        expected[-1]['craftTimerUnit'] = 'MS'
        if saved_widgets() != expected:
            raise AssertionError('FBD preset edit changed an unexpected project field')
        # Exercise the real editor's named-pin wiring, drag persistence and removal.
        # Select a point well inside the source, away from its resize grip.
        if self.saved_connections():
            raise AssertionError('New FBD blocks unexpectedly have existing wires')
        source_idx, target_idx = len(expected)-2, len(expected)-1
        source, target = expected[source_idx], expected[target_idx]
        canvas = self.wait_node(description='Редактор экрана')
        left, top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        self.tap_editor_menu_item('Связать FBD')
        adb('shell', 'input', 'tap', str(round(left+source['x']+9)), str(round(top+source['y']+9)))
        adb('shell', 'input', 'tap', str(round(left+target['x']+target['w']/2)),
            str(round(top+target['y']+target['h']/2)))
        self.wait_node('Выход FBD')
        self.tap('Q')
        self.wait_node('Вход FBD')
        self.tap('IN')
        links = self.saved_connections()
        if len(links) != 1 or any(links[0].get(k) != value for k, value in {
                'source': source_idx, 'target': target_idx,
                'sourcePin': 'Q', 'targetPin': 'IN',
                'sourceSignalType': 'BOOL', 'targetSignalType': 'BOOL'}.items()):
            raise AssertionError('Named Q -> IN wire was not saved correctly: ' + repr(links))
        self.capture('04q-fbd-named-pin-wire')
        # Original-style FBD long-press commands must be present, and
        # opening/canceling the menu cannot mutate the saved FBD graph.
        sx, sy = round(left+source['x']+9), round(top+source['y']+9)
        adb('shell', 'input', 'swipe', str(sx), str(sy), str(sx), str(sy), '800')
        self.wait_node('Соединить с…')
        self.wait_node('Инвертировать связь BOOL…')
        self.wait_node('Отсоединить выход')
        self.capture('04w-fbd-block-source-actions')
        self.tap('Отмена')
        if self.saved_connections() != links:
            raise AssertionError('Canceling FBD context actions changed saved wires')
        if saved_widgets() != expected:
            raise AssertionError('Adding a wire changed component properties')
        adb('shell', 'input', 'swipe',
            str(round(left+source['x']+9)), str(round(top+source['y']+9)),
            str(round(left+source['x']+39)), str(round(top+source['y']+19)), '310')
        dragged_widgets = saved_widgets()
        if dragged_widgets == expected:
            raise AssertionError('FBD drag did not persist new geometry')
        for idx, widget in enumerate(dragged_widgets):
            old = expected[idx]
            if idx == source_idx:
                if {k: v for k, v in widget.items() if k not in ('x', 'y')} != {
                        k: v for k, v in old.items() if k not in ('x', 'y')}:
                    raise AssertionError('Dragging FBD source mutated non-geometry fields')
            elif widget != old:
                raise AssertionError('Dragging FBD source moved or modified another block')
        if self.saved_connections() != links:
            raise AssertionError('Dragging an FBD block changed its named wire')
        self.capture('04r-fbd-drag-wire-preserved')
        expected = dragged_widgets
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        if saved_widgets() != expected:
            raise AssertionError('FBD palette blocks/preset did not survive process restart')
        if self.saved_connections() != links:
            raise AssertionError('Named FBD pin wiring was lost after process restart')
        self.capture('04p-fbd-reopened')
        # A typed BOOL wire can be inverted without changing the saved pin
        # identities. Undo/redo must restore the exact connection metadata.
        self.tap_editor_menu_item('Инвертировать BOOL-связь FBD…')
        self.wait_node('Инверсия связи FBD (BOOL)')
        self.tap(str(source_idx+1) + ':Q → ' + str(target_idx+1) + ':IN  обычная')
        inverted = self.saved_connections()
        if len(inverted) != 1 or not inverted[0].get('inverted'):
            raise AssertionError('Named BOOL wire inversion was not persisted')
        if {k: v for k, v in inverted[0].items() if k != 'inverted'} != links[0]:
            raise AssertionError('Wire inversion changed other saved metadata')
        self.capture('04v-fbd-inverted-bool-wire')
        self.tap_editor_menu_item('Отменить связь FBD')
        if self.saved_connections() != links:
            raise AssertionError('Undo did not restore normal BOOL wire metadata')
        self.tap_editor_menu_item('Повторить связь FBD')
        if self.saved_connections() != inverted:
            raise AssertionError('Redo did not restore inverted BOOL wire metadata')
        self.tap_editor_menu_item('Отменить связь FBD')
        if self.saved_connections() != links:
            raise AssertionError('Second undo failed to restore normal BOOL wire')
        self.tap_editor_menu_item('Удалить связь FBD…')
        label = (str(source_idx+1) + ': ' + source['text'] + ' → '
                 + str(target_idx+1) + ': ' + target['text'] + ' [Q → IN]')
        self.wait_node('Удалить связь FBD')
        self.tap(label)
        if self.saved_connections():
            raise AssertionError('FBD disconnect did not persist removal')
        self.capture('04s-fbd-disconnected')
        # Undo must restore the same fully typed connection, not a generic wire.
        self.tap_editor_menu_item('Отменить связь FBD')
        if self.saved_connections() != links:
            raise AssertionError('FBD undo failed to restore Q -> IN with all metadata')
        self.capture('04t-fbd-undo-restored')
        self.tap_editor_menu_item('Повторить связь FBD')
        if self.saved_connections():
            raise AssertionError('FBD redo failed to remove the restored wire')
        self.capture('04u-fbd-redo-disconnected')
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        if self.saved_connections() or saved_widgets() != expected:
            raise AssertionError('Removed FBD wire or dragged blocks failed to persist after restart')

    def check_legacy_media_migration_on_device(self):
        """Reproduce an id-less Android project with pre-ID photo/audio files."""
        adb('shell', 'am', 'force-stop', PACKAGE)
        legacy = {
            'projectName': 'Legacy_Media_Smoke',
            'currentScreen': 0,
            'screens': [{
                'name': 'Экран 1',
                'connections': [],
                'widgets': [
                    {'type': 'Многопозиционное фото', 'text': 'Legacy photo',
                     'x': 160, 'y': 135, 'w': 210, 'h': 110,
                     'imagePathStates': ['media/photo.png'] +
                        ['Картинка ' + str(i) for i in range(1, 16)]},
                    {'type': 'Звуковой сигнал', 'text': 'Legacy audio',
                     'x': 500, 'y': 135, 'w': 140, 'h': 80,
                     'soundFilePath': 'media/alarm.wav'}
                ]
            }]
        }
        # Draw a real, CRC-valid magenta PNG. The previous one-pixel fixture
        # had an invalid IDAT CRC: copying its bytes passed but it did not render.
        image = solid_rgba_png(8, 8, (235, 21, 207, 255))
        audio_buffer = io.BytesIO()
        with wave.open(audio_buffer, 'wb') as sound:
            sound.setnchannels(1)
            sound.setsampwidth(2)
            sound.setframerate(8000)
            sound.writeframes(b'\x00\x00' * 800)
        audio = audio_buffer.getvalue()
        image_file = self.output / 'legacy-photo.png'
        audio_file = self.output / 'legacy-alarm.wav'
        image_file.write_bytes(image)
        audio_file.write_bytes(audio)
        adb('push', str(image_file), '/data/local/tmp/bloodsky-legacy-photo.png')
        adb('push', str(audio_file), '/data/local/tmp/bloodsky-legacy-alarm.wav')
        adb('shell', 'run-as', PACKAGE, 'mkdir', '-p', 'files/media')
        adb('shell', 'run-as', PACKAGE, 'cp',
            '/data/local/tmp/bloodsky-legacy-photo.png', 'files/media/photo.png')
        adb('shell', 'run-as', PACKAGE, 'cp',
            '/data/local/tmp/bloodsky-legacy-alarm.wav', 'files/media/alarm.wav')

        root = ET.Element('map')
        for name, value in (
                ('active_project', json.dumps(legacy, ensure_ascii=False, separators=(',', ':'))),
                ('project_catalog_v1', '{}'),
                ('project_name', legacy['projectName'])):
            ET.SubElement(root, 'string', {'name': name}).text = value
        old_prefs = self.output / 'legacy-prefs.xml'
        old_prefs.write_bytes(ET.tostring(root, encoding='utf-8', xml_declaration=True))
        adb('push', str(old_prefs), '/data/local/tmp/bloodsky-legacy-prefs.xml')
        adb('shell', 'run-as', PACKAGE, 'cp',
            '/data/local/tmp/bloodsky-legacy-prefs.xml', 'shared_prefs/bloodsky_projects.xml')
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.wait_node('Загрузить проект')
        current_xml = adb('shell', 'run-as', PACKAGE, 'cat',
                          'shared_prefs/bloodsky_projects.xml')
        prefs = {node.get('name'): node.text for node in ET.fromstring(current_xml)}
        active = json.loads(prefs['active_project'])
        storage = active.get('storageId', '')
        if not storage or active.get('projectName') != legacy['projectName']:
            raise AssertionError('Id-less project failed to retain name and stable storage ID')
        stored = json.loads(prefs.get('project_catalog_v1', '{}'))
        project_dir = 'proj_' + hashlib.sha256(('sid_' + storage).encode()).hexdigest()[:16]
        if project_dir not in stored:
            raise AssertionError('Migrated identity not persisted into project catalog')
        media = 'files/projects/' + project_dir + '/media/'
        for filename, data in (('photo.png', image), ('alarm.wav', audio)):
            actual = adb('shell', 'run-as', PACKAGE, 'cat', media + filename, binary=True)
            original = adb('shell', 'run-as', PACKAGE, 'cat',
                           'files/media/' + filename, binary=True)
            if actual != data or original != data:
                raise AssertionError('Legacy media migration lost or altered ' + filename)
        self.tap('Загрузить проект')
        self.tap(legacy['projectName'])
        self.wait_node(description='Редактор экрана')
        visible_pixels = self.capture_until_pixels(
            '07-migrated-legacy-media', (235, 21, 207), tolerance=12, minimum=800)
        if visible_pixels < 800:
            current_after_open = adb('shell', 'run-as', PACKAGE, 'cat',
                                     'shared_prefs/bloodsky_projects.xml')
            loaded_prefs = {node.get('name'): node.text
                            for node in ET.fromstring(current_after_open)}
            loaded_project = json.loads(loaded_prefs.get('active_project', '{}'))
            loaded_screens = loaded_project.get('screens', [])
            loaded_widgets = (loaded_screens[0].get('widgets', [])
                             if loaded_screens else [])
            render_state = {
                'projectName': loaded_project.get('projectName'),
                'storageId': loaded_project.get('storageId'),
                'screenCount': len(loaded_screens),
                'screenName': loaded_screens[0].get('name') if loaded_screens else None,
                'widgets': [{
                    'type': item.get('type'), 'text': item.get('text'),
                    'x': item.get('x'), 'y': item.get('y'),
                    'w': item.get('w'), 'h': item.get('h'),
                    'visible': item.get('visible'),
                    'imagePath0': (item.get('imagePathStates') or [None])[0]
                } for item in loaded_widgets]
            }
            raise AssertionError(
                'Migrated image bytes exist, but editor did not render the actual PNG: '
                + str(visible_pixels) + ' magenta pixels; active project state='
                + json.dumps(render_state, ensure_ascii=False))
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(legacy['projectName'])
        self.wait_node(description='Редактор экрана')
        next_xml = adb('shell', 'run-as', PACKAGE, 'cat',
                       'shared_prefs/bloodsky_projects.xml')
        next_active = json.loads({node.get('name'): node.text
                                 for node in ET.fromstring(next_xml)}['active_project'])
        if next_active.get('storageId') != storage:
            raise AssertionError('Media storage identity changed across process restart')
        reopened_pixels = self.capture_until_pixels(
            '07b-migrated-legacy-media-reopened',
            (235, 21, 207), tolerance=12, minimum=800)
        if reopened_pixels < 800:
            raise AssertionError('Migrated image stopped rendering after restart: '
                                 + str(reopened_pixels) + ' magenta pixels')
        for filename, data in (('photo.png', image), ('alarm.wav', audio)):
            if adb('shell', 'run-as', PACKAGE, 'cat', media + filename, binary=True) != data:
                raise AssertionError('Legacy media disappeared after restart: ' + filename)


    def check_craft_archive_widget_on_device(self):
        # Exercise Craft ID 15 through the real source palette, property dialog,
        # operator canvas and local-history dialog on the Android emulator.
        adb('shell', 'input', 'keyevent', '4')
        self.wait_node('Создать новый проект')
        archive_project = 'Archive_Smoke'
        self.tap('Создать новый проект')
        self.tap(class_name='android.widget.EditText')
        adb('shell', 'input', 'text', archive_project)
        self.tap('Создать')
        self.wait_node(description='Редактор экрана')
        self.tap(description='Добавить элемент')
        self.tap(description='Добавить: Архив Craft')
        self.wait_node(description='Редактор экрана')
        prefs = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
        widgets = json.loads({n.get('name'): n.text for n in ET.fromstring(prefs)}['active_project'])['screens'][0]['widgets']
        if len(widgets) != 1 or widgets[0].get('type') != 'Архив Craft':
            raise AssertionError('The original palette did not create Craft ID 15')
        self.capture('08-craft-id15-editor')
        canvas = self.wait_node(description='Редактор экрана')
        left, top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        archive = widgets[0]
        adb('shell', 'input', 'tap', str(round(left + archive['x'] + archive['w'] / 2)),
            str(round(top + archive['y'] + archive['h'] / 2)))
        self.wait_node('Свойства: Архив Craft')
        properties = self.dump()
        labels = [n.get('text', '') for n in ET.fromstring(properties).iter('node')]
        if not any('Данные остаются на устройстве' in label for label in labels):
            raise AssertionError('Archive properties do not explain local-only storage')
        self.capture('08a-craft-id15-properties')
        self.replace_property_input('Каналы архива', '2')
        self.tap(description='Тип записи архива')
        self.tap('FLOAT — 32 бит')
        self.capture('08a2-craft-id15-channel-settings')
        self.tap('Применить')
        self.wait_node(description='Редактор экрана')
        prefs = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
        widgets = json.loads({n.get('name'): n.text for n in ET.fromstring(prefs)}['active_project'])['screens'][0]['widgets']
        extras = widgets[0]
        if (extras.get('craftArchiveChannelCount') != 2
                or extras.get('craftArchiveRecordType') != 'FLOAT'
                or extras.get('craftArchiveIntervalSeconds') != 1
                or extras.get('craftArchiveMaxRows') != 10000):
            raise AssertionError('Craft ID 15 channel settings did not persist independently: ' + repr(extras))
        archive = widgets[0]
        self.tap(description='Запустить проект')
        self.wait_node(description='Рабочий экран')
        canvas = self.wait_node(description='Рабочий экран')
        left, top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        adb('shell', 'input', 'tap', str(round(left + archive['x'] + archive['w'] / 2)),
            str(round(top + archive['y'] + archive['h'] / 2)))
        self.wait_node('Записей пока нет. Настрой локальный TCP-канал и запусти экран в рабочем режиме.')
        self.wait_node('График')
        self.wait_node('Экспорт CSV')
        self.wait_node('Закрыть')
        self.capture('08b-craft-id15-local-history')
        self.tap('Закрыть')
        self.tap(description='Остановить проект')
        self.wait_node(description='Редактор экрана')
        prefs = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
        saved = json.loads({n.get('name'): n.text for n in ET.fromstring(prefs)}['active_project'])['screens'][0]['widgets']
        if saved != widgets:
            raise AssertionError('Craft ID 15 settings changed during operator history view')
        adb('shell', 'input', 'keyevent', '4')
        self.wait_node('Создать новый проект')


    def check_media_picker_activity_recreation(self):
        project = 'Media_Restore_Project'
        self.tap('Создать новый проект')
        self.tap(class_name='android.widget.EditText')
        adb('shell', 'input', 'text', project)
        self.tap('Создать')
        self.wait_node(description='Редактор экрана')
        self.tap(description='Добавить элемент')
        self.tap(description='Добавить: Многопозиционное фото')
        self.wait_node(description='Редактор экрана')

        prefs = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
        active = json.loads({n.get('name'): n.text for n in ET.fromstring(prefs)}['active_project'])
        widget = active['screens'][0]['widgets'][0]
        canvas = self.wait_node(description='Редактор экрана')
        left, top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        adb('shell', 'input', 'tap', str(int(left + widget['x'] + widget['w'] / 2)),
            str(int(top + widget['y'] + widget['h'] / 2)))
        self.wait_node('Свойства: Многопозиционное фото')

        # Change the unsaved widget caption and then recreate the Activity while
        # DocumentsUI is open. The picker result must return to this form and field.
        xml = self.dump()
        edit_fields = matching_nodes(xml, class_name='android.widget.EditText')
        if not edit_fields:
            raise AssertionError('Media widget properties form has no editable caption')
        x, y = center(edit_fields[0].get('bounds'))
        adb('shell', 'input', 'tap', str(x), str(y))
        adb('shell', 'input', 'keycombination', '113', '29')
        adb('shell', 'input', 'text', 'Restored_label')

        self.prepare_custom_image()
        if keyboard_is_shown(adb('shell', 'dumpsys', 'input_method')):
            adb('shell', 'input', 'keyevent', '4')
        pickers = []
        for _ in range(12):
            xml = self.dump()
            pickers = matching_nodes(xml, text='Выбрать файл…')
            if pickers:
                break
            scroll = properties_scroll_node(xml)
            left, top, right, bottom = map(int, re.findall(r'\d+', scroll.get('bounds')))
            x = (left + right) // 2
            adb('shell', 'input', 'swipe', str(x), str(bottom - 100), str(x), str(top + 100), '300')
        if not pickers:
            raise AssertionError('Image media picker button is not reachable in the properties form')
        x, y = center(pickers[0].get('bounds'))
        adb('shell', 'input', 'tap', str(x), str(y))
        self.tap(description='Show roots')
        # The breadcrumb and roots drawer share this label on API 30.
        self.tap('Downloads', resource_id='android:id/title')
        self.wait_for_saved_main_activity()
        app_pids = adb('shell', 'pidof', PACKAGE).strip().split()
        if not app_pids:
            raise AssertionError('Application process is missing while DocumentsUI is open')
        # run-as kill races with Android reaping its target PID. ActivityManager
        # kills the stopped process while preserving the task's saved state.
        kill_result = kill_background_app_process(PACKAGE)
        if kill_result.returncode != 0:
            raise RuntimeError('ActivityManager could not kill the stopped app: '
                               + (kill_result.stderr or kill_result.stdout).strip())
        try:
            wait_for_app_process_exit(PACKAGE, app_pids)
        except RuntimeError as failure:
            (self.output / 'process-termination.txt').write_text(
                'kill stdout: ' + kill_result.stdout + '\n'
                + 'kill stderr: ' + kill_result.stderr + '\n'
                + 'kill returncode: ' + str(kill_result.returncode) + '\n'
                + str(failure) + '\n'
                + adb('shell', 'ps', '-A', '-o', 'PID,STAT,NAME'))
            raise
        self.tap_document('bloodsky-custom.png')
        self.wait_node('Свойства: Многопозиционное фото', timeout=30)
        self.wait_node('Restored_label', timeout=10)
        self.capture('07-media-picker-activity-restored')
        self.tap('Применить')
        self.wait_node(description='Редактор экрана')

        prefs = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
        active = json.loads({n.get('name'): n.text for n in ET.fromstring(prefs)}['active_project'])
        restored = active['screens'][0]['widgets'][0]
        image_paths = restored.get('imagePathStates', [])
        if restored.get('text') != 'Restored_label':
            raise AssertionError('Unsaved widget caption did not survive Activity recreation')
        if not image_paths or not image_paths[0].startswith('media/asset_'):
            raise AssertionError('Selected image did not return to its original media field')

    def run(self, apk):
        serial = adb('get-serialno').strip()
        if not serial.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
            raise RuntimeError('UI smoke requires a disposable emulator; refusing to clear device data')
        adb('install', '-r', str(apk))
        adb('push', 'build/ui-input/bloodsky-ui-input.jar', '/data/local/tmp/bloodsky-ui-input.jar')
        adb('shell', 'pm', 'clear', PACKAGE)
        adb('logcat', '-c')
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        for label in ['Создать новый проект', 'Загрузить проект', 'Удалить проект', 'Экспорт проекта', 'Импорт проекта', 'Выход']:
            self.wait_node(label)
        self.capture('01-home')
        self.tap('Создать новый проект')
        self.tap(class_name='android.widget.EditText')
        adb('shell', 'input', 'text', PROJECT)
        self.tap('Создать')
        self.wait_node(description='Редактор экрана')
        self.wait_node(description='Запустить проект')
        self.wait_node(description='Текущий экран')
        self.capture('01a-original-toolbar')
        self.tap(description='Скрыть панель')
        self.wait_node(description='Показать панель')
        self.tap(description='Показать панель')
        self.wait_node(description='Запустить проект')
        self.tap(description='Меню проекта')
        # This is the original editor overflow menu: its first row opens the
        # project TCP bindings. Project settings live elsewhere in this build.
        self.wait_node('Сетевые устройства')
        self.wait_node('Пароль проекта')
        self.wait_node('Режим станции: Мастер')
        self.wait_node('Показывать тайм-ауты')
        self.wait_node('Modbus TCP Slave: выключен')
        self.wait_node('Работа в фоне')
        # The last action is below the popup's viewport; tap_editor_menu_item
        # verifies it after scrolling when the smoke flow opens it.
        self.capture('01b-editor-commands')
        adb('shell', 'input', 'keyevent', '4')
        self.tap(description='Калькулятор')
        for key in ['1', '2', '+', '3', '=']:
            self.tap(key, class_name='android.widget.Button')
        self.wait_node('15.0', class_name='android.widget.TextView')
        self.capture('02a-calculator-addition')
        for key in ['С', '9', '/', '3', '=']:
            self.tap(key, class_name='android.widget.Button')
        self.wait_node('3.0', class_name='android.widget.TextView')
        for key in ['С', '2', '.', '5', '*', '4', '=']:
            self.tap(key, class_name='android.widget.Button')
        self.wait_node('10.0', class_name='android.widget.TextView')
        for key in ['С', '8', '-', '5', '=']:
            self.tap(key, class_name='android.widget.Button')
        self.wait_node('3.0', class_name='android.widget.TextView')
        self.tap('Выход', class_name='android.widget.Button')
        self.wait_node(description='Редактор экрана')
        self.tap(description='Калькулятор')
        for key in ['4', '2', '<-']:
            self.tap(key, class_name='android.widget.Button')
        self.wait_node('4', class_name='android.widget.TextView')
        self.capture('02b-calculator-reopened')
        self.tap('Выход', class_name='android.widget.Button')
        self.wait_node(description='Редактор экрана')
        self.tap(description='Добавить элемент')
        self.wait_node(description='Добавить: Индикатор')
        self.capture('02c-original-palette')
        self.tap(description='Добавить: Индикатор')
        for kind in ['Кнопка', 'Текст', 'Прогресс-бар']:
            self.wait_node(description='Редактор экрана')
            self.tap(description='Добавить элемент')
            self.tap(description='Добавить: ' + kind)
        self.wait_node(description='Редактор экрана')
        self.tap(description='Добавить элемент')
        self.tap(description='Функциональные блоки и другие элементы')
        self.wait_node(description='Добавить: FBD Таймер')
        self.capture('02d-original-fbd-palette')
        self.tap(description='Другие элементы BLOODSKY')
        self.wait_node('Добавить элемент')
        self.capture('02e-extended-components')
        self.tap('Отмена')
        self.wait_node(description='Редактор экрана')
        self.capture('02-created-editor')
        # Force a real activity/process restart instead of only reusing in-memory data.
        adb('shell', 'input', 'keyevent', '4')
        self.wait_node('Создать новый проект')
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.wait_node(PROJECT)
        self.capture('03-saved-project-list')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        self.capture('04-reopened-editor')
        prefs = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
        values = {node.get('name'): node.text for node in ET.fromstring(prefs)}
        active = json.loads(values['active_project'])
        widgets = active['screens'][0]['widgets']
        if active['projectName'] != PROJECT or [w['type'] for w in widgets] != ['Индикатор', 'Кнопка', 'Текст', 'Прогресс-бар']:
            raise AssertionError('Palette-created components/project did not survive process restart')
        if widgets[0]['w'] != widgets[0]['h'] or widgets[0]['w'] <= 0:
            raise AssertionError('Original indicator default geometry was lost')
        canvas = self.wait_node(description='Редактор экрана')
        left, top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        first = widgets[0]
        adb('shell', 'input', 'tap', str(round(left + first['x'] + first['w'] / 2)),
            str(round(top + first['y'] + first['h'] / 2)))
        self.wait_node('Настройки')
        self.tap('Сменить изображение')
        self.wait_node(description='Изображение ON')
        self.capture('04c-original-on-off')
        self.tap(description='Изображение ON')
        self.wait_node(description='Красная лампа')
        self.capture('04d-original-image-library')
        self.tap(description='Красная лампа')
        self.tap(description='Изображение OFF')
        self.tap(description='Синяя лампа')
        self.capture('04e-selected-state-images')
        # Exercise the actual Android document provider and a persistent content URI grant.
        adb('push', 'build/craft-reference/apktool/res/drawable/lamp_yellow.png', '/sdcard/Download/bloodsky-custom.png')
        self.tap(description='Изображение ON')
        self.tap(description='Выбрать своё изображение')
        self.wait_node('Своё изображение')
        self.prepare_custom_image()
        self.tap('Выбрать файл…')
        self.tap(description='Show roots')
        self.tap('Downloads')
        self.capture('04f-document-picker')
        self.tap_document('bloodsky-custom.png')
        self.wait_node('Своё изображение')
        self.tap('Применить')
        self.wait_node(description='Изображение ON')
        self.capture('04f-custom-state-image')
        adb('shell', 'input', 'keyevent', '4')
        self.wait_node('Настройки')
        self.tap('ОТМЕНА')
        canvas = self.wait_node(description='Редактор экрана')
        left, top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        adb('shell', 'input', 'swipe',
            str(round(left + first['x'] + first['w'] / 2)),
            str(round(top + first['y'] + first['h'] / 2)),
            str(round(left + first['x'] + first['w'] / 2)),
            str(round(top + first['y'] + first['h'] / 2)), '700')
        self.tap(description='Индикатор: Расширенные свойства')
        self.wait_node('Свойства: Индикатор')
        self.replace_property_input('Положение X', '40.125')
        self.replace_property_input('Положение Y', '50.875')
        self.replace_property_input('Ширина элемента', '')
        self.replace_property_input('Высота элемента', '140.625')
        self.tap('Применить')
        self.wait_node('Свойства: Индикатор')
        persisted = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
        before = json.loads({n.get('name'): n.text for n in ET.fromstring(persisted)}['active_project'])['screens'][0]['widgets'][0]
        if any(before[key] != first[key] for key in ['x', 'y', 'w', 'h']):
            raise AssertionError('Invalid properties edit partially mutated geometry')
        self.replace_property_input('Ширина элемента', '160.375')
        self.capture('04a-fractional-properties')
        self.tap('Применить')
        self.wait_node(description='Редактор экрана')
        persisted = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
        storage_id_before_restart = project_catalog_storage_id(persisted, PROJECT)
        if not storage_id_before_restart:
            raise AssertionError('Project catalog has no stable storage ID before restart')
        adb('shell', 'input', 'keyevent', '4')
        self.wait_node('Создать новый проект')
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        persisted = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
        prefs = {n.get('name'): n.text for n in ET.fromstring(persisted)}
        after = json.loads(prefs['active_project'])['screens'][0]['widgets'][0]
        expected = dict(x=40.125, y=50.875, w=160.375, h=140.625)
        if any(after[key] != value for key, value in expected.items()):
            raise AssertionError('Fractional property edit did not survive process restart')
        state_images = after.get('other', after)
        if isinstance(state_images, str):
            state_images = json.loads(state_images)
        selected_path = state_images.get('pathOrNameOn', '')
        image_digest = project_media_asset_sha256(selected_path)
        if image_digest is None or state_images.get('pathOrNameOff') != 'blue':
            raise AssertionError('Selected ON/OFF project media paths did not survive process restart')
        storage_id = project_catalog_storage_id(persisted, PROJECT)
        if not storage_id or storage_id != storage_id_before_restart:
            raise AssertionError('Reloaded project catalog has no stable storage ID for its selected image')
        project_id = 'proj_' + hashlib.sha256(('sid_' + storage_id).encode()).hexdigest()[:16]
        stored_project_files = adb('shell', 'run-as', PACKAGE, 'find', 'files/projects', '-type', 'f')
        asset_path = 'files/projects/' + project_id + '/' + selected_path
        if asset_path not in stored_project_files.splitlines():
            raise AssertionError('Selected media asset is missing from its project directory; stored files: '
                                 + stored_project_files)
        asset_bytes = adb('shell', 'run-as', PACKAGE, 'cat',
                          asset_path, binary=True)
        if hashlib.sha256(asset_bytes).hexdigest() != image_digest:
            raise AssertionError('Selected project image asset is missing or its contents do not match its path hash')
        if not state_images.get('isImageUserOn') or state_images.get('isImageUserOff'):
            raise AssertionError('Builtin/custom image flags were not retained')
        self.capture('04b-fractional-reopened')
        canvas = self.wait_node(description='Редактор экрана')
        left, top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        adb('shell', 'input', 'tap', str(round(left + after['x'] + after['w'] / 2)),
            str(round(top + after['y'] + after['h'] / 2)))
        self.wait_node('Настройки')
        self.tap('Сменить изображение')
        self.wait_node(description='Изображение ON')
        self.capture('04g-custom-image-after-restart')
        adb('shell', 'input', 'keyevent', '4')
        self.wait_node('Настройки')
        self.tap('ОТМЕНА')
        self.capture('04h-original-selection-actions')
        # Copy/cut/paste through the actual original action strip. All non-position
        # fields, including fractional dimensions and project-owned custom ON/OFF media metadata,
        # must survive the clipboard round trip.
        def saved_widgets():
            xml = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
            active = json.loads({n.get('name'): n.text for n in ET.fromstring(xml)}['active_project'])
            return active['screens'][0]['widgets']
        self.tap(description='Копировать')
        self.tap(description='Вставить')
        copied = saved_widgets()
        if len(copied) != 5:
            raise AssertionError('Copy/paste did not add exactly one component')
        expected_copy = {k: v for k, v in copied[0].items() if k not in ('x', 'y')}
        if {k: v for k, v in copied[-1].items() if k not in ('x', 'y')} != expected_copy:
            raise AssertionError('Clipboard lost indicator properties or media')
        self.tap(description='Вырезать')
        if len(saved_widgets()) != 4:
            raise AssertionError('Cut did not remove selected copy')
        self.tap(description='Вставить')
        pasted = saved_widgets()
        if len(pasted) != 5 or {k: v for k, v in pasted[-1].items() if k not in ('x', 'y')} != expected_copy:
            raise AssertionError('Cut/paste lost copied indicator properties')
        self.capture('04i-pasted-indicator')
        self.tap(description='Вырезать')
        self.tap(description='Вырезать')  # No selection: must leave the clipboard/project intact.
        if saved_widgets() != copied[:4]:
            raise AssertionError('Cut changed unrelated components or accepted an empty selection')
        # Editor clicks edit a button; only operator taps may activate it.
        baseline = saved_widgets()
        def button_point(description):
            canvas = self.wait_node(description=description)
            left, top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
            button = baseline[1]
            return round(left + button['x'] + 10), round(top + button['y'] + 10)
        x, y = button_point('Редактор экрана')
        adb('shell', 'input', 'tap', str(x), str(y))
        self.wait_node('Свойства: Кнопка')
        self.tap('Отмена')
        if saved_widgets() != baseline:
            raise AssertionError('Editing a button activated or mutated it')
        self.tap(description='Запустить проект')
        self.wait_node(description='Рабочий экран')
        self.wait_node(description='Остановить проект')
        ui = self.dump()
        if matching_nodes(ui, description='Добавить элемент') or matching_nodes(ui, description='Вырезать'):
            raise AssertionError('Editor tools remain visible in operator mode')
        self.capture('04j-operator-mode')
        x, y = button_point('Рабочий экран')
        adb('shell', 'input', 'swipe', str(x), str(y), str(x+70), str(y+30), '300')
        adb('shell', 'input', 'swipe', str(x), str(y), str(x), str(y), '800')
        ui = self.dump()
        if saved_widgets() != baseline or any(n.get('text','').startswith('Свойства:') for n in ET.fromstring(ui).iter('node')):
            raise AssertionError('Runtime drag/long-press edited or activated a component')
        adb('shell', 'input', 'tap', str(x), str(y))
        self.wait_node(description='Рабочий экран')
        activated = saved_widgets()
        if not activated[1]['buttonState']:
            raise AssertionError('Operator tap did not activate the button')
        expected = [dict(widget) for widget in baseline]
        expected[1]['buttonState'] = True
        if activated != expected:
            raise AssertionError('Operator tap changed unrelated fields')
        self.capture('04k-operator-button-on')
        adb('shell', 'input', 'keyevent', '4')
        self.wait_node(description='Редактор экрана')
        self.wait_node(description='Запустить проект')
        adb('shell', 'input', 'keyevent', '4')
        self.wait_node('Создать новый проект')
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        self.wait_node(description='Запустить проект')
        if saved_widgets() != expected:
            raise AssertionError('Runtime button state did not survive restart in safe editor mode')
        self.tap(description='Запустить проект')
        x, y = button_point('Рабочий экран')
        adb('shell', 'input', 'tap', str(x), str(y))
        self.wait_node(description='Рабочий экран')
        self.tap(description='Остановить проект')
        self.wait_node(description='Редактор экрана')
        if saved_widgets() != baseline:
            raise AssertionError('Stop/restart/operator off changed widget geometry or metadata')
        self.capture('04l-editor-mode-restored')
        self.check_fbd_palette_and_properties(saved_widgets)
        adb('shell', 'input', 'keyevent', '4')
        self.wait_node('Создать новый проект')
        self.tap('Удалить проект')
        self.tap(PROJECT)
        self.wait_node('Удалить проект?')
        self.capture('05-delete-confirmation')
        self.tap('Удалить')
        self.wait_node('Создать новый проект')
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            prefs = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
            if preferences_are_empty(prefs):
                break
            time.sleep(0.2)
        else:
            raise AssertionError('Deleted project remains in active project/catalog')
        self.capture('06-after-deletion')
        self.check_legacy_media_migration_on_device()
        self.check_craft_archive_widget_on_device()
        self.check_media_picker_activity_recreation()
        crashes = adb('logcat', '-d', '-b', 'crash')
        (self.output / 'crash-log.txt').write_text(crashes)
        if PACKAGE in crashes:
            raise AssertionError('Application crash recorded')
        (self.output / 'result.json').write_text(json.dumps({'result': 'passed', 'package': PACKAGE,
                'checks': ['home actions', 'create', 'original calculator add/divide/multiply/subtract/decimal/clear/delete/reopen', 'original five-category palette', 'palette add indicator/button/text/progress', 'original 40-preview FBD palette and return/extended navigation', 'FBD AND/TON add and restart', 'FBD non-overlapping initial block positions', 'FBD typed Q-IN wiring, drag, restart, disconnect, undo and redo', 'invalid timer preset leaves project and form unchanged', 'exact valid timer preset preserves other fields', 'Craft network device slots and server list navigation', 'Wi-Fi endpoint, port and RTU persistence round trip', 'process restart', 'reload four palette-created components', 'invalid geometry leaves dialog/project unchanged', 'fractional properties survive second process restart', 'original ON/OFF dialogs and builtin state selection', 'custom document-provider image copied into project media and retained after process restart', 'original compact toolbar and hide/reveal', 'editor button opens properties without activation', 'operator drag and long-press cannot edit or activate', 'runtime button on/off and persistence after restart', 'stop and Back restore editor tools', 'confirmed deletion', 'legacy media bytes preserved and visible PNG rendered across restart', 'Craft ID 15 palette, property dialog and local history on Android 11', 'media picker Activity recreation restores the open properties form and selected image field'],
                'api_level': adb('shell', 'getprop', 'ro.build.version.sdk').strip(),
                'display_size': adb('shell', 'wm', 'size').strip(),
                'display_density': adb('shell', 'wm', 'density').strip()}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=Path('build-reports/android-ui-smoke'))
    args = parser.parse_args()
    smoke = Smoke(args.output)
    try:
        smoke.run(args.apk)
    except Exception as exc:
        try:
            (args.output / 'failure.txt').write_text(
                type(exc).__name__ + ': ' + str(exc) + '\n\n' + traceback.format_exc())
            try:
                (args.output / 'failure-activity.txt').write_text(
                    adb('shell', 'dumpsys', 'activity', 'activities'))
            except Exception as diagnostic_error:
                (args.output / 'failure-activity.txt').write_text(
                    'diagnostic failed: ' + repr(diagnostic_error))
            try:
                (args.output / 'failure-project.xml').write_text(
                    adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml'))
            except Exception as diagnostic_error:
                (args.output / 'failure-project.xml').write_text(
                    'diagnostic failed: ' + repr(diagnostic_error))
            smoke.capture('failure')
            (args.output / 'logcat.txt').write_text(adb('logcat', '-d', '-t', '1500'))
        except Exception:
            pass
        raise

if __name__ == '__main__':
    main()
