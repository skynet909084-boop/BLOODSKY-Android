# ORIGINAL APK SOURCE CONTRACTS & PARITY LEDGER — KaScada Craft 1.0.7

## Executive Summary

This document and its paired machine-readable ledger (`docs/ORIGINAL_APK_SOURCE_CONTRACTS.json`) establish the exact decompiled source contracts, resource mappings, system register specifications, FBD block rules, project schema definitions, and implementation backlog for BLOODSKY Batch 5 preparation.

All details are extracted directly from the decompiled KaScada Craft 1.0.7 reference archive (`reference/craft-reference.zip`, SHA-256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`).

---

## 1. Original Image Aliases Audit (152 Assets)

The original APK contains **152 image assets** (151 PNGs + 1 JPG) in `res/drawable`:

- **Total packaged original images in APK archive**: 152
- **Directly referenced in BLOODSKY Java source code**: 12 (dynamically verified against current `app/` source tree by `tools/verify_original_source_contracts.py`)
- **Packaged-but-unused in current BLOODSKY runtime**: 140

### Asset Inventory Summary
- **Buttons / Switches**: `kc_button.png`, `kc_button2.png`, `kc_button3.png`, `kc_switch1_off.png`..`kc_switch8_on.png`
- **Lamps / Indicators**: `kc_lamp_off.png`, `kc_lamp_green.png`, `kc_lamp_red.png`, `kc_lamp_yellow.png`, `kc_lamp_blue.png`, `kc_green_light.png`, `kc_red_light.png`
- **Gauges & Meters**: `kc_needle_indicator.png`, `kc_spindle_indicator.png`, `kc_half_line_indicator.png`, `kc_quarter_line_indicator.png`, `kc_progress.png`
- **Toolbars & Navigation**: `kc_menu_app_bar.png`, `kc_menu_appbar_right.png`, `kc_menu_down.png`, `kc_menu_middle.png`, `kc_menu_up.png`, `kc_arrow_left.png`, `kc_arrow_right.png`
- **Picker Tabs**: `kc_tab_alarm.png`, `kc_tab_archive.png`, `kc_tab_button.png`, `kc_tab_digital_setters.png`, `kc_tab_dyn_pictures.png`, `kc_tab_edit_number.png`, `kc_tab_gateway.png`, `kc_tab_graph.png`, `kc_tab_line_indicator.png`, `kc_tab_text.png`, `kc_tab_round_indicator.png`, `kc_tab_web_view.png`

---

## 2. System Registers Map (Device 0)

System Registers reside in **Device 0** (System Device / "Системные регистры"). Array capacity is 2000 registers (indices 0..1999).

| Address | Name | Type | Access | Default | Semantics & Triggers |
|---|---|---|---|---|---|
| **0** | `SYS_FLAGS` | BITFIELD | RO | 0 | System state (Bit 15: Disconnect/Offline flag) |
| **1** | `SYS_PROJECT_ID` | UINT16 | RO | 1 | Active project identifier (`MyApplication.b().c`) |
| **2** | `SYS_TIME` | UINT16 | RO | 0 | Current time formatted as `(hour * 100) + minute` |
| **3** | `SYS_DATE` | UINT16 | RO | 0 | Current date formatted as `(day * 100) + month` |
| **4** | `SYS_SCREEN_INDEX` | UINT16 | RW | 1 | Active screen index (`1..countScreen`). Writing triggers active screen jump |
| **5** | `SYS_BRIGHTNESS` | UINT16 | RW | 255 | Screen brightness level (`0..255`), bound to Android `Settings.System` |
| **6** | `SYS_BUZZER_SOUND` | UINT16 | RW | 0 | Sound playback trigger index |
| **7** | `SYS_ORIENTATION` | UINT16 | RW | 0 | Screen orientation / lock mode (`0..16`) |
| **8** | `SYS_TTS_PHRASE` | UINT16 | RW | 0 | Text-To-Speech phrase playback trigger index |
| **9** | `SYS_CONTROL_FLAGS` | BITFIELD | RW | 0 | System control bits (Bits 0-2: modes, Bit 8: sync, Bit 9: cloud, Bit 15: trigger) |
| **10..1999** | `SYS_USER_MEM` | UINT16 | RW | 0 | General internal system memory for local FBD variables and scripts |

### Ambiguity Blockers
1. System registers belong exclusively to **Device 0**.
2. System registers MUST NOT generate external Modbus TCP/RTU slave network requests.
3. Writing to `SYS_SCREEN_INDEX` (Reg 4) must immediately request screen navigation in the HMI runtime loop.

---

## 3. Component & Property Source Contract Ledger

| Component | Source Class | Resources / Layout | BLOODSKY Production Call Site | Status | Evidence |
|---|---|---|---|---|---|
| **CustomLabel** | `CustomComponents.CustomLabel` | `res/layout/design_text.xml` | `MainActivity.addWidget` | Partial | Uses Android `TextView` with sp font size instead of px auto-scaling and original preference popup. |
| **CustomButton** | `CustomComponents.e` | `kc_button.png`, `kc_button2.png`, `kc_button3.png` | `MainActivity.addWidget` | Partial | Procedural canvas drawing; missing 3 original button PNG skins and original popup dialog. |
| **CustomIndicator** | `CustomComponents.q` | `kc_lamp_off.png`, `kc_lamp_green.png`.. | `MainActivity.addWidget` | Partial | Basic circle canvas draw; missing original lamp PNG drawables and multi-color indicator states. |
| **CustomSetpoint** | `CustomComponents.i` | `kc_plus.png`, `kc_minus.png` | `MainActivity.adjustSetpoint` | Partial | Procedural +/- buttons; missing float min/max clamping and original numeric dialog. |
| **CustomDynText** | `CustomComponents.CustomDynText` | `res/layout/dialog_dyn_text.xml` | `MainActivity.addWidget` | Partial | Hardcoded 2-state toggle; missing multi-range dynamic text dialog and table mapping. |
| **CustomGauge** | `CustomComponents.m` | `kc_needle_indicator.png`, `kc_spindle_indicator.png` | `MainActivity.addWidget` | Partial | Canvas arc draw; missing needle PNG rotation renderer. |
| **CustomProgressBar** | `CustomComponents.v` | `kc_progress.png` | `MainActivity.addWidget` | Partial | Simple fill Rect; missing original progress bar texture/frame asset. |
| **CustomTrend** | `CustomComponents.o` | `kc_graph.png` | `MainActivity.addWidget` | Partial | Basic Canvas polyline; missing multi-point historical trace array. |
| **CustomList** | `CustomComponents.CustomList` | `res/layout/dialog_list.xml` | `MainActivity.addWidget` | Partial | Basic ListView; missing original dialog list configuration. |
| **CustomSound** | `CustomComponents.CustomSound` | `res/raw/kc_alarm.wav` | `MainActivity.playSoundAlert` | Partial | ToneGenerator fallback; missing embedded sound asset trigger. |
| **CustomScreenLink** | `CustomComponents.b0` | `kc_arrow_right.png` | `MainActivity.activateScreen` | Implemented | Navigates active screen index on tap. |
| **CustomMultiStatePhoto** | `CustomComponents.h` | `kc_dyn_picture.png`, `kc_no_picture.png` | `MainActivity.loadCachedBitmap` | Partial | Decodes image URI; missing state index fallback image frame. |
| **CustomVoice** | `CustomComponents.a0` | `res/layout/dialog_voice.xml` | `MainActivity.speakVoiceAnnouncement` | Partial | TTS initialized; missing rising-edge trigger check. |

---

## 4. FBD Logic Blocks Contract

All FBD blocks execute in the decoupled 100ms FBD scan cycle loop:

- **Boolean (`FB_AND`, `FB_OR`, `FB_XOR`, `FB_RS`, `FB_TRIGGER`)**: Evaluated in `MainActivity.evaluateFbdBlock`.
- **Timers (`FB_TIMER`)**: `FB_TIMER` evaluated in `MainActivity.evaluateFbdBlock`. Status is **partial**: provisional TON/TOF/TP logic exists in 100ms cycle using milliseconds, but requires exact state-machine verification against `ru.himik.nightscream.kascada.Logic.FB_TIMER`.
- **Counters (`FB_CTU`)**: Evaluated in `MainActivity.evaluateFbdBlockNumeric`. Status is **partial**: BLOODSKY implements provisional `CU/R/PV` counter, whereas original Craft `FB_CTU` uses `C/R/PV` pins with numeric output.
- **Math & Conversions (`FB_ARITHMETIC_FLOAT`, `ABS`, `POW`, `SIN`, `WordToFloat`, `FloatToInt`, `IntToFloat`)**: Evaluated in `MainActivity.evaluateFbdBlockNumeric`. `WordToFloat` is **partial** (provisional 2-register float assembly; requires endianness validation against KaScada Craft `WordToFloat`).

---

## 5. Backlog Implementation Stages

### Stage 1: UI & Component Visual Parity
- Wire all 152 `kc_*` drawables to widget renderers and pickers.
- Implement `autoTextSize` px font scaling and native popup preference rows.
- Port original metallic button skins and multi-lamp indicator states.

### Stage 2: Project Schema & Compatibility
- Full `TableNameProject` SQLite schema compatibility.
- Ensure strict `JsonObjectCopy.deepCopy` retention of unknown metadata (`extraFields`).

### Stage 3: Devices, System Registers & Modbus Protocol
- Implement Device 0 System Registers (`SYS_FLAGS` through `SYS_CONTROL_FLAGS` and memory registers 10..1999).
- Implement structured Device-Register-Bit binding in project serialization.

### Stage 4: FBD Engine & Block Parity
- Full timer millisecond precision evaluation and 32-bit Float math blocks.
- Verify FBD cycle prevention and edge trigger detection.

### Stage 5: Audio, Events & System Integrations
- Edge-triggered sound playback and TTS voice synthesis.
- Background alarm notification processing.
