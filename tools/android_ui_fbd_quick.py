#!/usr/bin/env python3
"""Focused FBD Android UI gate for feature PRs.

The full 800+ line Android smoke remains mandatory before integration to
main/release. This fast test is deterministic and tests FBD native UI, graph
persistence, inverter undo, geometry and mode/unit editor without unrelated
DocumentsUI/media/calculator/screen-copy scenarios.
"""
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

        # Keep one source-backed AND and TON as real editable Android widgets.
        self.tap(description='Добавить элемент')
        self.tap(description='Функциональные блоки и другие элементы')
        for attempt in range(12):
            xml = self.dump()
            if matching_nodes(xml, description='Добавить: FBD И'):
                break
            scrolls = [n for n in ET.fromstring(xml).iter('node')
                       if n.get('class') == 'android.widget.ScrollView']
            if len(scrolls) != 1:
                raise AssertionError('Original FBD AND palette entry not reachable')
            x, top, right, bottom = map(int, re.findall(r'\d+', scrolls[0].get('bounds')))
            adb('shell', 'input', 'swipe', str((x+right)//2),
                str(bottom-180), str((x+right)//2), str(top+80), '300')
        self.tap(description='Добавить: FBD И')
        self.wait_node(description='Редактор экрана')
        self.tap(description='Добавить элемент')
        self.tap(description='Функциональные блоки и другие элементы')
        self.tap(description='Добавить: FBD CRAFT TIMER')
        initial = current_widgets()
        if [x['type'] for x in initial] != ['FBD И', 'FBD CRAFT TIMER']:
            raise AssertionError('Wrong original palette FBD blocks: ' + repr(initial))
        source, target = initial
        canvas = self.wait_node(description='Редактор экрана')
        left, top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))

        # Source long-press: the new menu must not modify the graph by itself.
        sx, sy = round(left + source['x'] + 9), round(top + source['y'] + 9)
        adb('shell', 'input', 'swipe', str(sx), str(sy), str(sx), str(sy), '800')
        for label in ('Соединить с…', 'Инвертировать связь BOOL…',
                      'Инвертировать выход BOOL', 'Отсоединить выход',
                      'Свойства', 'Другие действия'):
            self.wait_node(label)
        self.capture('fast-fbd-longpress-menu')
        self.tap('Отмена')
        if current_widgets() != initial or self.saved_connections():
            raise AssertionError('Cancelled FBD context menu changed the graph')

        # Original FB_AND's invertOut is a block property, separate from wire inversion.
        adb('shell', 'input', 'swipe', str(sx), str(sy), str(sx), str(sy), '800')
        self.tap('Инвертировать выход BOOL')
        initial = current_widgets()
        if not initial[0].get('invertOut'):
            raise AssertionError('Craft Boolean block output inversion did not persist')
        source, target = initial
        self.capture('fast-fbd-craft-output-inverted')

        # Save an exact, non-round 2345ms timer preset.
        tx = round(left + target['x'] + target['w'] / 2)
        ty = round(top + target['y'] + target['h'] / 2)
        adb('shell', 'input', 'tap', str(tx), str(ty))
        self.wait_node('Свойства: FBD CRAFT TIMER')
        self.replace_property_input('Время таймера, мс', '2345')
        self.tap('Применить')
        self.wait_node(description='Редактор экрана')
        changed = current_widgets()
        if changed[1]['timerPresetMs'] != 2345 or changed[0] != source:
            raise AssertionError('Timer preset or unrelated FBD property changed')
        self.capture('fast-fbd-source-canvas')

        # Typed Q->IN edge, BOOL inversion and persisted undo.
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
        if not inverted[0].get('inverted'):
            raise AssertionError('BOOL inversion failed to save')
        self.capture('fast-fbd-inverted-wire')
        self.tap_editor_menu_item('Отменить связь FBD')
        if self.saved_connections() != edge:
            raise AssertionError('Undo did not restore exact pin metadata')

        # A force-stopped process must reload the same source, timer and edge.
        expected = current_widgets()
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        if current_widgets() != expected or self.saved_connections() != edge:
            raise AssertionError('FBD graph changed after cold restart')
        # Check the two new source-compatible FBD operators in the REAL
        # editor palette and ensure their distinct IDs survive process death.
        def add_native_from_extended(label):
            self.tap(description='Добавить элемент')
            self.tap(description='Функциональные блоки и другие элементы')
            self.tap(description='Другие элементы BLOODSKY')
            item_text = label + '    ›'
            direction = None
            for attempt in range(80):
                xml = self.dump()
                nodes = matching_nodes(xml, text=item_text)
                scrolls = [n for n in ET.fromstring(xml).iter('node')
                           if n.get('class') == 'android.widget.ScrollView'
                           and n.get('resource-id', '') != 'android:id/buttonPanel'
                           and any(child.get('class') == 'android.widget.TextView'
                                   and '    ›' in child.get('text', '')
                                   for child in n.iter('node'))]
                if len(scrolls) != 1:
                    raise AssertionError('Expected one scrolling extended FBD item list, got ' + str(len(scrolls)))
                rows = [n.get('text', '') for n in scrolls[0].iter('node')
                        if n.get('class') == 'android.widget.TextView' and '    ›' in n.get('text', '')]
                left_s, top_s, right_s, bottom_s = map(
                    int, re.findall(r'\d+', scrolls[0].get('bounds')))
                # The picker can reopen at either end. Turn around at the
                # actual first/last row so a fast fling cannot skip Craft T.
                if direction is None:
                    direction = -1 if rows and rows[-1].startswith('FBD — Craft MOVE') else 1
                if rows and rows[-1].startswith('FBD — Craft MOVE'):
                    direction = -1
                elif rows and rows[0].startswith('Текст / надпись'):
                    direction = 1
                if nodes:
                    l, t, r, b = map(int, re.findall(r'\d+', nodes[0].get('bounds')))
                    if t >= top_s and b <= bottom_s and b > t:
                        self.tap(text=item_text)
                        self.wait_node(description='Редактор экрана')
                        return
                x_s = (left_s + right_s)//2
                y1, y2 = ((bottom_s - 180, bottom_s - 480) if direction == 1
                          else (top_s + 180, top_s + 480))
                adb('shell', 'input', 'swipe', str(x_s), str(y1), str(x_s), str(y2), '260')
                time.sleep(0.04)
            raise AssertionError('New Craft FBD block not reachable: ' + label)

        add_native_from_extended('FBD — Craft CTU 16 бит (+, −, R)')
        add_native_from_extended('FBD — Craft T (переключатель по фронту)')
        native = current_widgets()
        if len(native) != 4 or [x['type'] for x in native[2:]] != [
                'FBD CRAFT CTU', 'FBD CRAFT T']:
            raise AssertionError('Original-native CTU/T palette block mismatch: ' + repr(native))
        if native[2].get('counterValue') != 0:
            raise AssertionError('Craft CTU did not start at original zero count')
        self.capture('fast-fbd-craft-ctu16-and-toggle')

        # Source native BLINK uses the same saved exact-ms preset but MUST
        # remain mode BLINK rather than being silently switched to TON.
        add_native_from_extended('FBD — Craft BLINK (мигание)')
        native = current_widgets()
        if len(native) != 5 or native[4]['type'] != 'FBD CRAFT BLINK':
            raise AssertionError('Native BLINK extended palette ID missing: ' + repr(native))
        if native[4]['timerPresetMs'] != 1000:
            raise AssertionError('New native BLINK did not start at 1000ms')
        canvas = self.wait_node(description='Редактор экрана')
        c_left, c_top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        bx = round(c_left + native[4]['x'] + native[4]['w'] / 2)
        by = round(c_top + native[4]['y'] + native[4]['h'] / 2)
        adb('shell', 'input', 'tap', str(bx), str(by))
        self.wait_node('Свойства: FBD CRAFT BLINK')
        self.replace_property_input('Время таймера, мс', '3456')
        self.tap('Применить')
        self.wait_node(description='Редактор экрана')
        native = current_widgets()
        if native[4]['type'] != 'FBD CRAFT BLINK' or native[4]['timerPresetMs'] != 3456:
            raise AssertionError('Craft BLINK type or exact 3456ms preset was lost')
        self.capture('fast-fbd-native-blink-3456ms')

        add_native_from_extended('FBD — Craft TIMER (TON / TOF / TP / BLINK)')
        native = current_widgets()
        if len(native) != 6 or native[5]['type'] != 'FBD CRAFT TIMER':
            raise AssertionError('Original Craft FB_TIMER must have its own versioned type: ' + repr(native))
        if native[5].get('text') != 'TON':
            raise AssertionError('Native Craft timer must default to source mode TON')
        canvas = self.wait_node(description='Редактор экрана')
        c_left, c_top, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        tx = round(c_left + native[5]['x'] + native[5]['w'] / 2)
        ty = round(c_top + native[5]['y'] + native[5]['h'] / 2)
        adb('shell', 'input', 'tap', str(tx), str(ty))
        self.wait_node('Свойства: FBD CRAFT TIMER')
        self.tap(description='Режим таймера FBD')
        self.tap('TOF')
        self.tap('Применить')
        self.wait_node(description='Редактор экрана')
        native = current_widgets()
        if (native[5]['type'] != 'FBD CRAFT TIMER'
                or native[5].get('craftTimerMode') != 'TOF'
                or native[5].get('text') != 'TOF'):
            raise AssertionError('Craft source mode did not persist independently of legacy TOF: ' + repr(native[5]))
        self.capture('fast-fbd-native-craft-timer-tof')

        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        if current_widgets() != native or self.saved_connections() != edge:
            raise AssertionError('Native CTU/T project changed after app restart')

        # Create a fresh test project for the actual source palette so that
        # the canvas has free space. A crowded viewport must never be used to
        # validate placement: overlap was previously possible on fallback.
        adb('shell', 'pm', 'clear', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Создать новый проект')
        self.tap(class_name='android.widget.EditText')
        adb('shell', 'input', 'text', PROJECT)
        self.tap('Создать')
        self.wait_node(description='Редактор экрана')

        # The original 1.0.7 Craft FBD palette already contains source previews
        # for RS, T, CTU, typed change detectors and gated Integer MOVE. These preview actions
        # are backed by verified runtimes rather than the generic extra menu.
        def add_from_original_preview(type_id):
            self.tap(description='Добавить элемент')
            self.tap(description='Функциональные блоки и другие элементы')
            desc = 'Добавить: ' + type_id
            for attempt in range(18):
                xml = self.dump()
                nodes = matching_nodes(xml, description=desc)
                scrolls = [n for n in ET.fromstring(xml).iter('node')
                           if n.get('class') == 'android.widget.ScrollView']
                # Original Craft has TWO genuine layouts. Portrait holds
                # a ScrollView; the wide/landscape palette is a flat grid.
                # The latter exposes every enabled original source tile
                # directly and must NOT be rejected for lacking scrolling.
                if not scrolls:
                    if len(nodes) != 1:
                        raise AssertionError('Original wide Craft tile not found: ' + desc)
                    self.tap(description=desc)
                    self.wait_node(description='Редактор экрана')
                    return
                if len(scrolls) != 1:
                    raise AssertionError('Ambiguous original Craft scrolling list')
                left, top, right, bottom = map(
                    int, re.findall(r'\d+', scrolls[0].get('bounds')))
                if len(nodes) == 1:
                    _, y1, _, y2 = map(int, re.findall(r'\d+', nodes[0].get('bounds')))
                    if top <= y1 and y2 <= bottom and y2 > y1:
                        self.tap(description=desc)
                        self.wait_node(description='Редактор экрана')
                        return
                adb('shell', 'input', 'swipe', str((left + right)//2),
                    str(bottom - 95), str((left + right)//2), str(top + 45), '250')
            raise AssertionError('Verified original Craft preview not reachable: ' + type_id)

        add_from_original_preview('FBD RS')
        add_from_original_preview('FBD CRAFT T')
        add_from_original_preview('FBD CRAFT CTU')
        add_from_original_preview('FBD CRAFT CHANGE I')
        add_from_original_preview('FBD CRAFT CHANGE F')
        add_from_original_preview('FBD CRAFT MOVE')
        add_from_original_preview('FBD CRAFT MX')
        add_from_original_preview('FBD CRAFT ARRAY')
        add_from_original_preview('FBD CRAFT LOG')
        add_from_original_preview('FBD CRAFT WORD TO FLOAT')
        original = current_widgets()
        if [w['type'] for w in original] != [
                'FBD RS', 'FBD CRAFT T', 'FBD CRAFT CTU',
                'FBD CRAFT CHANGE I', 'FBD CRAFT CHANGE F', 'FBD CRAFT MOVE',
                'FBD CRAFT MX', 'FBD CRAFT ARRAY',
                'FBD CRAFT LOG', 'FBD CRAFT WORD TO FLOAT']:
            raise AssertionError('Original Craft source previews mapped incorrectly: '
                                 + repr(original))
        if original[2].get('counterValue') != 0:
            raise AssertionError('Original CTU preview created incorrect starting count')
        # Every pair must have non-intersecting screen geometry after insertion.
        for i, first in enumerate(original):
            for second in original[i + 1:]:
                intersects = (first['x'] < second['x'] + second['w']
                              and first['x'] + first['w'] > second['x']
                              and first['y'] < second['y'] + second['h']
                              and first['y'] + first['h'] > second['y'])
                if intersects:
                    raise AssertionError('Native original FBD blocks overlapped: '
                                         + repr((first, second)))
        hierarchy = ET.fromstring(self.dump())
        scrollable = {node.get('class') for node in hierarchy.iter('node')
                      if node.get('scrollable') == 'true'}
        if not {'android.widget.ScrollView', 'android.widget.HorizontalScrollView'}.issubset(scrollable):
            raise AssertionError('FBD design surface is not scrollable in both axes: '
                                 + repr(scrollable))
        screen_size = re.search(r'(\\d+)x(\\d+)', adb('shell', 'wm', 'size'))
        if not screen_size:
            raise AssertionError('Could not determine emulator screen size for workspace swipe')
        screen_w, screen_h = map(int, screen_size.groups())
        pan_x = round(screen_w * 0.90)
        adb('shell', 'input', 'swipe', str(pan_x), str(round(screen_h * 0.68)),
            str(pan_x), str(round(screen_h * 0.35)), '350')
        time.sleep(0.2)
        if current_widgets() != original:
            raise AssertionError('Panning the FBD workspace changed saved widget data')
        self.capture('fast-fbd-workspace-scrolled')
        adb('shell', 'input', 'swipe', str(pan_x), str(round(screen_h * 0.35)),
            str(pan_x), str(round(screen_h * 0.68)), '350')
        if current_widgets() != original:
            raise AssertionError('Returning the FBD workspace to the top changed widget data')
        self.capture('fast-fbd-original-craft-source-palette')

        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        if current_widgets() != original or self.saved_connections():
            raise AssertionError('Original Craft palette native types were not persisted')

        # Pure Float32 operators and source casts have a separate clean canvas.
        adb('shell', 'pm', 'clear', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Создать новый проект')
        self.tap(class_name='android.widget.EditText')
        adb('shell', 'input', 'text', PROJECT)
        self.tap('Создать')
        self.wait_node(description='Редактор экрана')
        expected_pure = ['FBD CRAFT TIMER', 'FBD CRAFT POW', 'FBD CRAFT SIN', 'FBD CRAFT ABS',
                         'FBD CRAFT INT TO FLOAT', 'FBD CRAFT FLOAT TO INT']
        for native_type in expected_pure:
            add_from_original_preview(native_type)
        pure = current_widgets()
        if [widget['type'] for widget in pure] != expected_pure:
            raise AssertionError('Original Craft pure math/conversions mapped incorrectly: '
                                 + repr(pure))
        if pure[0].get('text') != 'TON':
            raise AssertionError('Original Craft timer preview must create source TON mode')
        for i, first in enumerate(pure):
            for second in pure[i + 1:]:
                intersects = (first['x'] < second['x'] + second['w']
                              and first['x'] + first['w'] > second['x']
                              and first['y'] < second['y'] + second['h']
                              and first['y'] + first['h'] > second['y'])
                if intersects:
                    raise AssertionError('Pure original FBD blocks overlapped: '
                                         + repr((first, second)))
        self.capture('fast-fbd-original-craft-pure-float-operators')
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        if current_widgets() != pure or self.saved_connections():
            raise AssertionError('Original Craft pure operator IDs changed after restart')

        # Versioned INTEGER SEL/COMPARE have different ports from BLOODSKY's
        # existing generic operators. Exercise the real extended picker.
        adb('shell', 'pm', 'clear', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Создать новый проект')
        self.tap(class_name='android.widget.EditText')
        adb('shell', 'input', 'text', PROJECT)
        self.tap('Создать')
        self.wait_node(description='Редактор экрана')
        add_native_from_extended('FBD — Craft SEL (целочисленный)')
        add_native_from_extended('FBD — Craft COMPARE (INT32)')
        operators = current_widgets()
        if [w['type'] for w in operators] != [
                'FBD CRAFT SEL', 'FBD CRAFT COMPARE']:
            raise AssertionError('Source Integer SEL/COMPARE picker mapping incorrect: ' +
                                 repr(operators))
        if operators[1].get('compareOperator') != '>':
            raise AssertionError('Original COMPARE should default to >')
        # Edit the native comparator rather than generic double comparator.
        canvas = self.wait_node(description='Редактор экрана')
        x0, y0, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
        cmp = operators[1]
        adb('shell', 'input', 'tap',
            str(round(x0 + cmp['x'] + cmp['w']/2)),
            str(round(y0 + cmp['y'] + cmp['h']/2)))
        self.wait_node('Свойства: FBD CRAFT COMPARE')
        self.replace_property_input('Оператор сравнения', '==')
        self.tap('Применить')
        self.wait_node(description='Редактор экрана')
        operators = current_widgets()
        if (operators[1]['type'] != 'FBD CRAFT COMPARE'
                or operators[1]['compareOperator'] != '=='):
            raise AssertionError('Original COMPARE mode or type lost after editing')
        self.capture('fast-fbd-source-native-sel-compare')
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        if current_widgets() != operators or self.saved_connections():
            raise AssertionError('Signed Integer native operator state lost after restart')

        # Original KaScada Craft preview icons (draw9 SEL, draw15 COMPARE)
        # must open the same tested signed-Integer runtime types.
        adb('shell', 'pm', 'clear', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Создать новый проект')
        self.tap(class_name='android.widget.EditText')
        adb('shell', 'input', 'text', PROJECT)
        self.tap('Создать')
        self.wait_node(description='Редактор экрана')
        add_from_original_preview('FBD CRAFT SEL')
        add_from_original_preview('FBD CRAFT COMPARE')
        palette_native = current_widgets()
        if [w['type'] for w in palette_native] != [
                'FBD CRAFT SEL', 'FBD CRAFT COMPARE']:
            raise AssertionError('Original FB_SEL/FB_COMPARE preview mapped to wrong ports')
        self.capture('fast-fbd-original-source-integer-sel-compare')
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        if current_widgets() != palette_native or self.saved_connections():
            raise AssertionError('Original source SEL/COMPARE preview widgets lost after restart')
        # Exercise both new source-backed change detectors through the real
        # extended picker; the original preview remains gated separately.
        add_native_from_extended('FBD — Craft CH_I (изменение Integer)')
        add_native_from_extended('FBD — Craft CH_F (изменение Float)')
        changes = current_widgets()
        if [w['type'] for w in changes[-2:]] != [
                'FBD CRAFT CHANGE I', 'FBD CRAFT CHANGE F']:
            raise AssertionError('Craft change detector picker types incorrect')
        if changes[:-2] != palette_native:
            raise AssertionError('Adding change detectors altered existing native widgets')
        # Opening and applying ordinary properties must preserve each variant.
        for widget in changes[-2:]:
            canvas = self.wait_node(description='Редактор экрана')
            x0, y0, _, _ = map(int, re.findall(r'\d+', canvas.get('bounds')))
            adb('shell', 'input', 'tap',
                str(round(x0 + widget['x'] + widget['w']/2)),
                str(round(y0 + widget['y'] + widget['h']/2)))
            self.wait_node('Свойства: ' + widget['type'])
            self.tap('Применить')
            self.wait_node(description='Редактор экрана')
        if current_widgets() != changes:
            raise AssertionError('Change detector properties altered saved fields')
        self.capture('fast-fbd-native-change-int-float')
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        if current_widgets() != changes or self.saved_connections():
            raise AssertionError('Native change detector data lost after cold restart')
        self.capture('fast-fbd-native-change-restarted')

        # MQTT source/sink blocks use the original palette. Verify both IDs
        # are actionable and persist before attempting any live broker test.
        adb('shell', 'pm', 'clear', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Создать новый проект')
        self.tap(class_name='android.widget.EditText')
        adb('shell', 'input', 'text', PROJECT)
        self.tap('Создать')
        self.wait_node(description='Редактор экрана')
        add_from_original_preview('FBD CRAFT MQTT SUB BOOL')
        add_from_original_preview('FBD CRAFT MQTT PUB BOOL')
        mqtt = current_widgets()
        if [w['type'] for w in mqtt] != [
                'FBD CRAFT MQTT SUB BOOL', 'FBD CRAFT MQTT PUB BOOL']:
            raise AssertionError('Original MQTT previews did not create typed FBD blocks: ' + repr(mqtt))
        self.capture('fast-fbd-original-mqtt-blocks')
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        self.tap('Загрузить проект')
        self.tap(PROJECT)
        self.wait_node(description='Редактор экрана')
        if current_widgets() != mqtt or self.saved_connections():
            raise AssertionError('Original MQTT block IDs changed after cold restart')
        crashes = adb('logcat', '-d', '-b', 'crash')
        (self.output / 'crash-log.txt').write_text(crashes)
        if PACKAGE in crashes:
            raise AssertionError('Android crash detected')
        elapsed = round(time.monotonic() - started, 2)
        (self.output / 'result.json').write_text(json.dumps({
            'result': 'passed', 'profile': 'quick-fbd',
            'package': PACKAGE, 'api_level': adb('shell', 'getprop', 'ro.build.version.sdk').strip(),
            'elapsed_seconds': elapsed, 'checks': [
                'source FBD palette', 'long-press context', 'cancel without mutation',
                'exact timer ms persistence', 'source canvas', 'typed BOOL Q-IN edge',
                'BOOL invert and undo', 'project cold restart',
                'native Craft CTU16 + T extended palette',
                'native BLINK palette and exact 3456ms mode preservation',
                'native block cold restart',
                'original Craft RS/T/CTU source preview selection',
                'original source preview block cold restart',
                'source Integer SEL and COMPARE extended picker',
                'COMPARE exact == mode and cold restart',
                'original source SEL/COMPARE previews and cold restart',
                'native CH_I/CH_F picker, property Apply, preserved widgets and cold restart',
                'original MQTT subscriber/publisher palette and cold restart',
                'no app crash']
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
