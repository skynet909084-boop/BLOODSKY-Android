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

    def capture(self, name):
        (self.output / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        (self.output / (name + '.xml')).write_text(self.dump())

    def run(self, apk):
        serial = adb('get-serialno').strip()
        if not serial.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
            raise RuntimeError('UI smoke requires a disposable emulator; refusing to clear device data')
        adb('install', '-r', str(apk))
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
        self.wait_node(PROJECT)
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
        self.wait_node(PROJECT)
        self.tap(description='Калькулятор')
        for key in ['4', '2', '<-']:
            self.tap(key, class_name='android.widget.Button')
        self.wait_node('4', class_name='android.widget.TextView')
        self.capture('02b-calculator-reopened')
        self.tap('Выход', class_name='android.widget.Button')
        self.wait_node(PROJECT)
        self.tap('+ Элемент')
        self.wait_node(description='Добавить: Индикатор')
        self.capture('02c-original-palette')
        self.tap(description='Добавить: Индикатор')
        for kind in ['Кнопка', 'Текст', 'Прогресс-бар']:
            self.wait_node(PROJECT)
            self.tap('+ Элемент')
            self.tap(description='Добавить: ' + kind)
        self.wait_node(PROJECT)
        self.tap('+ Элемент')
        self.tap(description='Функциональные блоки и другие элементы')
        self.wait_node('Добавить элемент')
        self.capture('02d-extended-components')
        self.tap('Отмена')
        self.wait_node(PROJECT)
        self.capture('02-created-editor')
        # Force a real activity/process restart instead of only reusing in-memory data.
        self.tap('☰')
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.wait_node(PROJECT)
        self.capture('03-saved-project-list')
        self.tap(PROJECT)
        self.wait_node(PROJECT)
        self.capture('04-reopened-editor')
        prefs = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/bloodsky_projects.xml')
        values = {node.get('name'): node.text for node in ET.fromstring(prefs)}
        active = json.loads(values['active_project'])
        widgets = active['screens'][0]['widgets']
        if active['projectName'] != PROJECT or [w['type'] for w in widgets] != ['Индикатор', 'Кнопка', 'Текст', 'Прогресс-бар']:
            raise AssertionError('Palette-created components/project did not survive process restart')
        if widgets[0]['w'] != widgets[0]['h'] or widgets[0]['w'] <= 0:
            raise AssertionError('Original indicator default geometry was lost')
        self.tap('☰')
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
                'checks': ['home actions', 'create', 'original calculator add/divide/multiply/subtract/decimal/clear/delete/reopen', 'original five-category palette', 'palette add indicator/button/text/progress', 'extended component/FBD access', 'process restart', 'reload four palette-created components', 'confirmed deletion'],
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
