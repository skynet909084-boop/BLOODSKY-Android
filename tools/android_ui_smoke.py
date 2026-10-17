#!/usr/bin/env python3
"""Install and exercise the debug APK on an already booted, disposable emulator."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'com.bloodsky.hmi'
PROJECT = 'Smoke_Project'


def adb(*args, binary=False):
    result = subprocess.run(['adb', *args], capture_output=True, timeout=45, check=True)
    return result.stdout if binary else result.stdout.decode('utf-8', errors='replace')


def matching_nodes(xml, text=None, class_name=None, description=None):
    nodes = ET.fromstring(xml).iter('node')
    return [n for n in nodes if (text is None or n.get('text', '').casefold() == text.casefold())
            and (class_name is None or n.get('class') == class_name)
            and (description is None or n.get('content-desc') == description)
            and n.get('enabled') == 'true' and n.get('bounds') != '[0,0][0,0]']


def center(bounds):
    found = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if not found:
        raise ValueError('Invalid UI bounds: ' + bounds)
    left, top, right, bottom = map(int, found.groups())
    if right <= left or bottom <= top:
        raise ValueError('Empty UI bounds: ' + bounds)
    return (left + right) // 2, (top + bottom) // 2


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


def property_scroll_up(target, previous=None):
    order = ['Положение X', 'Положение Y', 'Ширина элемента', 'Высота элемента']
    return previous is None or order.index(target) >= order.index(previous)


def keyboard_is_shown(diagnostics):
    return re.search(r'\bmInputShown=true\b', diagnostics) is not None


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
        output.mkdir(parents=True, exist_ok=True)

    def dump(self):
        adb('shell', 'uiautomator', 'dump', '/sdcard/bloodsky-ui.xml')
        return adb('shell', 'cat', '/sdcard/bloodsky-ui.xml')

    def wait_node(self, text=None, class_name=None, timeout=30, description=None):
        deadline = time.monotonic() + timeout
        last = ''
        while time.monotonic() < deadline:
            try:
                last = self.dump()
                nodes = matching_nodes(last, text, class_name, description)
                if len(nodes) == 1:
                    return nodes[0]
                if len(nodes) > 1:
                    raise RuntimeError('Ambiguous UI target: ' + str(text or class_name))
            except (subprocess.SubprocessError, ET.ParseError):
                pass
            time.sleep(0.3)
        (self.output / 'last-ui.xml').write_text(last)
        raise AssertionError('UI target not found: ' + str(text or class_name))

    def tap(self, text=None, class_name=None, description=None):
        node = self.wait_node(text, class_name, description=description)
        x, y = center(node.get('bounds'))
        adb('shell', 'input', 'tap', str(x), str(y))

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
            nodes = [n for n in matching_nodes(xml, text=filename)
                     if n.get('package') == 'com.android.documentsui']
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
                    if not any(n.get('package') == 'com.android.documentsui'
                               for n in ET.fromstring(xml).iter('node')):
                        return
                if attempts >= 3:
                    break
            previous = bounds
        (self.output / 'document-selection-ui.xml').write_text(xml)
        raise AssertionError('Document did not open through system picker: ' + filename)

    def scroll_to_description(self, description):
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
            upward = property_scroll_up(description, getattr(self, "last_property_description", None))
            if nodes:
                _, node_top, _, _ = map(int, re.findall(r'\d+', nodes[0].get('bounds')))
                upward = node_top > top
            start, end = (bottom - 25, top + 25) if upward else (top + 25, bottom - 25)
            adb('shell', 'input', 'swipe', str(x), str(start), str(x), str(end), '300')
        raise AssertionError('Property field not reachable: ' + description)

    def replace_property_input(self, description, value):
        node = self.scroll_to_description(description)
        x, y = center(node.get('bounds'))
        adb('shell', 'input', 'tap', str(x), str(y))
        focused = self.wait_node(description=description, class_name='android.widget.EditText')
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

    def capture(self, name):
        (self.output / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        (self.output / (name + '.xml')).write_text(self.dump())

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
        self.wait_node('Настройки проекта')
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
        self.wait_node('Добавить элемент')
        self.capture('02d-extended-components')
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
        self.wait_node('Свойства: Индикатор')
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
        adb('shell', 'input', 'keyevent', '4')
        self.wait_node('Создать новый проект')
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        persisted = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
        after = json.loads({n.get('name'): n.text for n in ET.fromstring(persisted)}['active_project'])['screens'][0]['widgets'][0]
        expected = dict(x=40.125, y=50.875, w=160.375, h=140.625)
        if any(after[key] != value for key, value in expected.items()):
            raise AssertionError('Fractional property edit did not survive process restart')
        state_images = after.get('other', after)
        if isinstance(state_images, str):
            state_images = json.loads(state_images)
        if not state_images.get('pathOrNameOn', '').startswith('content://') or state_images.get('pathOrNameOff') != 'blue':
            raise AssertionError('Selected ON/OFF images did not survive process restart')
        if not state_images.get('isImageUserOn') or state_images.get('isImageUserOff'):
            raise AssertionError('Builtin/custom image flags were not retained')
        self.capture('04b-fractional-reopened')
        canvas = self.wait_node(description='Редактор экрана')
        left, top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        adb('shell', 'input', 'tap', str(round(left + after['x'] + after['w'] / 2)),
            str(round(top + after['y'] + after['h'] / 2)))
        self.wait_node('Свойства: Индикатор')
        self.tap('Сменить изображение')
        self.wait_node(description='Изображение ON')
        self.capture('04g-custom-image-after-restart')
        adb('shell', 'input', 'keyevent', '4')
        self.wait_node('Свойства: Индикатор')
        self.tap('Отмена')
        self.capture('04h-original-selection-actions')
        # Copy/cut/paste through the actual original action strip. All non-position
        # fields, including fractional dimensions and custom ON/OFF URI metadata,
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
        crashes = adb('logcat', '-d', '-b', 'crash')
        (self.output / 'crash-log.txt').write_text(crashes)
        if PACKAGE in crashes:
            raise AssertionError('Application crash recorded')
        (self.output / 'result.json').write_text(json.dumps({'result': 'passed', 'package': PACKAGE,
                'checks': ['home actions', 'create', 'original calculator add/divide/multiply/subtract/decimal/clear/delete/reopen', 'original five-category palette', 'palette add indicator/button/text/progress', 'extended component/FBD access', 'process restart', 'reload four palette-created components', 'invalid geometry leaves dialog/project unchanged', 'fractional properties survive second process restart', 'original ON/OFF dialogs and builtin state selection', 'custom document-provider image and persisted URI grant after process restart', 'original compact toolbar and hide/reveal', 'editor button opens properties without activation', 'operator drag and long-press cannot edit or activate', 'runtime button on/off and persistence after restart', 'stop and Back restore editor tools', 'confirmed deletion'],
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
    except Exception:
        try:
            smoke.capture('failure')
            (args.output / 'logcat.txt').write_text(adb('logcat', '-d', '-t', '1500'))
        except Exception:
            pass
        raise

if __name__ == '__main__':
    main()
