# BLOODSKY Android — component and logic inventory

Status: static inventory from the successful Kaskada Craft 1.0.7 decompilation artifact. This is a reverse-engineering aid, not a claim that these components have already been reimplemented.

## Target

BLOODSKY is an Android APK project intended to reproduce and improve Kaskada Craft 1.0.7. The immediate output target is an installable Android APK. This repository is the Android project; a Windows executable is not the deliverable for this workstream.

## Evidence base

- Decompiled archive: `BLOODSKY-Kaskada-decompiled.zip`
- Java-like sources under `jadx/sources/ru/himik/nightscream/kascada/`
- Decoded manifest at `apktool/AndroidManifest.xml`
- JADX reports decompilation errors for some classes. Decompiled Java is therefore a navigation aid; verify ambiguous or damaged logic against Apktool Smali before reimplementing it.

## Confirmed application surfaces

Manifest names include:
- `ru.himik.nightscream.kascada.MainActivity`
- `ru.himik.nightscream.kascada.mbCanvas` (landscape screen/editor activity)
- `ru.himik.nightscream.kascada.Preference.PrefActivity`
- project load/delete/import/export activities
- network-device, address-connection, server-list, Bluetooth-device, alarm-sound and Wi-Fi-to-USB bridge settings
- foreground service and Eclipse Paho MQTT service

The decompiled source manifest declares networking, Wi-Fi state, Bluetooth, storage, location, audio, camera, SMS, wake-lock and notification permissions. BLOODSKY's manifest currently requests only INTERNET and ACCESS_FINE_LOCATION; it does not request SMS permissions. Keep permissions limited to enabled features, with runtime permission flows and clear explanations rather than copying the legacy manifest wholesale. Telegram notifications use the existing INTERNET permission.

## Screen/widget inventory (class-name mapping is partly obfuscated)

The original uses many custom view classes in `CustomComponents`. The decompiler has renamed a number of them to single letters; the strings below are strong clues to their configuration, but do not treat a class-to-visible-label mapping as proven until checked in the original screen registry and layouts.

| Source class | Evidence in its configuration strings | Likely function to verify |
|---|---|---|
| `CustomLabel` | `autoResizeText`, `enableFrameLabel`, `timeZ` | Static label/text |
| `CustomDynText` | text variants `T0`…`T14` | Dynamic text/status text |
| `CustomList` | register, bit, request type, visible-in-work flags | List/table-like data view |
| `CustomSound` | sound paths and Modbus register/bit fields | Sound/alarm trigger |
| `a0` | `textVoice0`… | Voice/text announcement-related component |
| `b0` | `link0`… and URL handling | Link/navigation component |
| `e` | `imagePosition`, `changeScreen`, `constantRegister` | Image or screen-action component; verify exact role |
| `g` | `showText`, `sizeText`, `max`, `inColor`, `outColor`, color points | Color/value visualization, likely gauge/scale |
| `h` | `pathPic_0`… | Multi-state picture/image |
| `i` | `min`, `max`, `password`, decimal places, edit frame | Numeric entry/setpoint |
| `m` | `minGauge`, `maxGauge`, scale count, indicator/mark colors | Gauge |
| `n` | `pathGif` | Animated GIF |
| `o` | graph series names, period, points, channel count, graph type | Trend/chart |
| `q` | on/off image paths and states | Two-state indicator/image |
| `r` | `showScale`, `max`, `percentLowScale` | Progress/bar indicator |
| `u` | `digit`, `divisor`, `typeInt`, register frame | Numeric/register display |
| `v` | `colorProgress`, `colorBackground`, `max` | Progress/value bar |
| `w`, `x` | phone, message, command/strobe register settings | Legacy SMS/command actions; verify exact split. BLOODSKY removes SMS and uses Telegram for user notifications. |
| `y` | `imagePosition`, visibility in work mode | Image-positioned component |
| `f` | `rtsp_url` | RTSP/video component |
| `d` | Archive table and CSV export strings | Archive/history service logic, not necessarily a visual widget |
| `k`, `l` | source/receive/trigger registers and transfer modes | Register-copy/bit/word operation; verify behavior |
| `j`, `t` | title/body/email or topic notification strings | Notification/email/news integration; verify exact role |

This list intentionally marks uncertain mappings for further verification. Do not use obfuscated one-letter class names as public BLOODSKY API names.

## Confirmed FBD/logic class inventory

Sources found under `Logic/` include:
- Boolean: `FB_AND`, `FB_OR`, `FB_XOR`, `FB_RS`
- Timer/counter/trigger: `FB_TIMER`, `FB_CTU`, `FB_TRIGGER`, `FB_CHANGE_I`, `FB_CHANGE_F`
- Arithmetic and selection: `FB_ARITHMETIC_INT`, `FB_ARITHMETIC_FLOAT`, `FB_MOVE`, `FB_SEL`, `FB_MX`, `FB_ARRAY`, `FB_MACRO`
- Comparisons: `FB_COMPARE`, `FB_COMPARE_FLOAT`
- Conversion: `FloatToInt`, `IntToFloat`, `WordToFloat`
- Math: `ABS`, `LOG`, `SIN`, `FB_POW`
- Variables: `Var`, `VarInt`, `VarFloat`, `VarOut`, `VarOutInt`, `VarOutFloat`, `VarNameIn`, `VarNameOut`
- MQTT: `MQTT_PUBLISHER`, `MQTT_SUBSCRIBER`
- Device/sensor-related classes: `ODTEMP`, `Sensor_Light`, `Sensor_Orientation`, `Sensor_Speed`, `Sunrise_GPS`

Presence in the APK is confirmed; exact pin types, scan order, persistence, execution timing and edge cases still need source/Smali review before implementation.

## Confirmed project data shape

Database `Rovki.db`, schema version 11, includes a project table with fields such as project name/password, station mode, screen/device counts, screen background, network-device configuration, created time/date, work mode, PDF document, timeout, system registers, Modbus slave and settings.

Per-screen component table:
`pro_<screen>_component(_id, componentId, text, width, height, x, y, textSize, connectionInfo, colorText, colorBackground, other)`

Per-screen connection table:
`pro_<screen>_connections(_id, connectionsId, name, server)`

Import/export code references project metadata, component tables, connection tables and screen assets. Preserve unknown fields and original identifiers during round trips wherever possible; lossy conversion is a compatibility bug.

## Implementation order

1. Establish the app module/build toolchain and produce a minimal installable BLOODSKY APK.
2. Build a component registry from original creation/serialization code, then verify uncertain entries against Smali and resource layouts.
3. Implement the editor canvas: add/select/move/resize/delete/copy/paste/align, context properties, labels and selection state.
4. Implement project database and archive import/export with round-trip tests and preservation of unknown fields.
5. Implement widgets in small verified groups, starting with label, dynamic text, numeric display/input, button/state image, gauge/bar and chart.
6. Implement FBD data model and deterministic execution semantics, with unit tests for every block and pin.
7. Audit Modbus transport, device configuration, reconnect/timeouts, register types/endianness and polling. Keep real-device I/O disabled in tests.
8. Implement MQTT only after checking original connection/security semantics.
9. Add regression fixtures, APK build/signing workflow and install smoke test.

## Required compatibility checks

- A project exported by BLOODSKY can be reopened without changing component IDs, positions, colors, text, connections or unknown metadata.
- Import rejects unsafe/malformed archives without partial project creation.
- Editing a widget changes its persisted properties and survives app restart.
- FBD tests cover normal, boundary and reset conditions.
- Modbus tests use a simulator/mock before any hardware is involved.
- APK builds reproducibly in CI; do not report an APK as ready until the workflow has uploaded a signed artifact and an install smoke test has passed.
