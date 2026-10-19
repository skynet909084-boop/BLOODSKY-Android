#!/usr/bin/env python3
"""Focused Android FBD smoke: add, edit, wire, invert and reopen a real project."""
import argparse
import json
from pathlib import Path
import re
import time
import xml.etree.ElementTree as ET

from android_ui_smoke import PACKAGE, PROJECT, Smoke, adb, center, matching_nodes


def current_widgets():
    payload = adb('shell', 'run-as', PACKAGE, 'cat',
                  'shared_prefs/bloodsky_projects.xml')
    preferences = {node.get('name'): node.text for node in ET.fromstring(payload)}
    return json.loads(preferences['active_project'])['screens'][0]['widgets']


class FastFbdSmoke(Smoke):
    def run_fast(self, apk):
        started = time.monotonic()
        serial = adb('get-serialno').strip()
        if not serial.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
            raise RuntimeError('Refusing destructive UI smoke on a real Android device')
        adb('install', '-r', str(apk))
        adb('shell', 'pm', 'clear', PACKAGE)
        adb('logcat', '-c')
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Создать новый проект')
        self.tap(class_name='android.widget.EditText')
        adb('shell', 'input', 'text', PROJECT)
        self.tap('Создать')
        self.wait_node(description='Редактор экрана')

        # Add one original Boolean operator and one source-native Craft timer.
        self.tap(description='Добавить элемент')
        self.tap(description='Функциональные блоки и другие элементы')
        self.wait_node(description='Добавить: FBD И')
        self.tap(description='Добавить: FBD И')
        self.wait_node(description='Редактор экрана')
        self.tap(description='Добавить элемент')
        self.tap(description='Функциональные блоки и другие элементы')
        self.tap(description='Добавить: FBD Таймер')
        initial = current_widgets()
        if [w['type'] for w in initial] != ['FBD И', 'FBD CRAFT TIMER']:
            raise AssertionError('Original FBD palette entries mapped incorrectly: ' + repr(initial))
        source, target = initial
        canvas = self.wait_node(description='Редактор экрана')
        left, top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        sx, sy = round(left + source['x'] + 9), round(top + source['y'] + 9)
        tx = round(left + target['x'] + target['w'] / 2)
        ty = round(top + target['y'] + target['h'] / 2)

        # Verify a canceled long-press is side-effect free, then persist output inversion.
        adb('shell', 'input', 'swipe', str(sx), str(sy), str(sx), str(sy), '800')
        for label in ('Соединить с…', 'Инвертировать связь BOOL…',
                      'Инвертировать выход BOOL', 'Отсоединить выход', 'Свойства'):
            self.wait_node(label)
        self.capture('fast-fbd-context-menu')
        self.tap('Отмена')
        if current_widgets() != initial or self.saved_connections():
            raise AssertionError('Canceling FBD context menu changed project state')
        adb('shell', 'input', 'swipe', str(sx), str(sy), str(sx), str(sy), '800')
        self.tap('Инвертировать выход BOOL')
        source = current_widgets()[0]
        if not source.get('invertOut'):
            raise AssertionError('Craft Boolean output inversion did not persist')
        self.capture('fast-fbd-output-inverted')

        # Edit and retain a non-round exact millisecond timer preset.
        adb('shell', 'input', 'tap', str(tx), str(ty))
        self.wait_node('Свойства: FBD CRAFT TIMER')
        self.replace_property_input('Время таймера, мс', '2345')
        self.tap('Применить')
        self.wait_node(description='Редактор экрана')
        edited = current_widgets()
        if edited[0] != source or edited[1].get('timerPresetMs') != 2345:
            raise AssertionError('Timer edit changed another block or lost exact ms value')
        self.capture('fast-fbd-timer-edited')

        # Persist a typed Q -> IN wire, invert it, and undo to the same metadata.
        self.tap_editor_menu_item('Связать FBD')
        adb('shell', 'input', 'tap', str(sx), str(sy))
        adb('shell', 'input', 'tap', str(tx), str(ty))
        self.wait_node('Выход FBD')
        self.tap('Q')
        self.wait_node('Вход FBD')
        self.tap('IN')
        edge = self.saved_connections()
        if len(edge) != 1 or any(edge[0].get(k) != v for k, v in {
                'source': 0, 'target': 1, 'sourcePin': 'Q', 'targetPin': 'IN',
                'sourceSignalType': 'BOOL', 'targetSignalType': 'BOOL'}.items()):
            raise AssertionError('Typed FBD edge incorrectly persisted: ' + repr(edge))
        self.tap_editor_menu_item('Инвертировать BOOL-связь FBD…')
        self.wait_node('Инверсия связи FBD (BOOL)')
        self.tap('1:Q → 2:IN  обычная')
        inverted = self.saved_connections()
        if len(inverted) != 1 or not inverted[0].get('inverted'):
            raise AssertionError('BOOL edge inversion failed to persist')
        self.tap_editor_menu_item('Отменить связь FBD')
        if self.saved_connections() != edge:
            raise AssertionError('Undo did not restore the exact typed FBD edge')
        self.capture('fast-fbd-wire-restored')

        # Cold restart verifies that widget settings and pin-level wiring survive.
        expected = current_widgets()
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        if current_widgets() != expected or self.saved_connections() != edge:
            raise AssertionError('FBD project changed after cold restart')
        crashes = adb('logcat', '-d', '-b', 'crash')
        (self.output / 'crash-log.txt').write_text(crashes)
        if PACKAGE in crashes:
            raise AssertionError('Android crash detected')
        elapsed = round(time.monotonic() - started, 2)
        checks = [
            'real Android 11 app launch and project creation',
            'original FBD AND and Craft timer palette entries',
            'cancel context menu without mutation',
            'Craft Boolean output inversion',
            'exact 2345ms timer edit',
            'typed BOOL Q-IN wire creation and persistence',
            'BOOL wire inversion and undo',
            'cold restart and project reload',
            'no app crash']
        (self.output / 'result.json').write_text(json.dumps({
            'result': 'passed', 'profile': 'quick-fbd',
            'package': PACKAGE, 'api_level': adb('shell', 'getprop', 'ro.build.version.sdk').strip(),
            'elapsed_seconds': elapsed, 'checks': checks
        }, indent=2))
        print(f'FAST_FBD_SMOKE_OK elapsed_seconds={elapsed}', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--output', type=Path,
                        default=Path('build-reports/android-ui-smoke'))
    args = parser.parse_args()
    smoke = FastFbdSmoke(args.output)
    args.output.mkdir(parents=True, exist_ok=True)
    try:
        smoke.run_fast(args.apk)
    except Exception:
        try:
            smoke.capture('fast-fbd-failure')
            (args.output / 'logcat.txt').write_text(adb('logcat', '-d', '-t', '1000'))
        except Exception:
            pass
        raise


if __name__ == '__main__':
    main()
