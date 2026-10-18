# HMI KaSCADA 1.4 manual → BLOODSKY crosswalk

Source: `HMI-KaSCADA-v1.4-ot-08.09.19 (1).pdf` (109 pages), checked as extracted text and rendered page sheets. The manual describes KaSCADA 1.4; the pinned APK is Kaskada Craft 1.0.7. Similar labels are useful evidence, but do not prove identical behavior or file formats. “Present” below means a code path exists, not that full device or pixel parity has passed.

## Manual map and implementation status

| Manual pages / sections | Capability described | BLOODSKY checkpoint | Remaining work |
|---|---|---|---|
| 6–20, 2.1–2.9 | Project list/create/open/delete/import/export; portrait/landscape; editor/work modes; up to 20 screens; device/time status; calculator/info; run/stop password; stationary display and timeouts; Wi-Fi/USB bridge, Modbus Slave and cloud settings | Project archive import/export, multiple screens, editor/work-mode lifecycle, screen navigation, calculator, and selected project settings exist. Recent importer handles stale screen counts and references later screens. | Match project settings except licensing and cloud/remote control, which are out of scope for this independent app. Local Modbus Slave/bridge behavior and orientation-specific parity still need implementation and device checks. |
| 2.2, 3.1–3.2 | Wi-Fi Modbus TCP and RTU-over-TCP; Bluetooth SPP Modbus RTU; USB-OTG serial (RS232/RS485/UART), CP2102/CH340/FTDI; up to 16 devices | BLOODSKY has a TCP Modbus client and keeps imported Craft device bindings inactive. | Bluetooth and serial transport, device manager parity, reconnect/timeout behavior, address/function/unit conversion, and real-hardware acceptance. Do not map Craft connection blobs into BLOODSKY protocol strings without verification. |
| 3.3 | Browser monitoring/control and archive download; Cloud remote control/archive view | No complete local web-server parity claim. | Cloud remote control is out of scope. Local browser monitoring/control and archive download remain in scope for later implementation; test local-only network boundaries and session lifecycle. |
| 3.4 | Group editing: select, cut/copy/paste, align edges/centers, distribute spacing, normalize width/height (13 operations) | Single-widget selection actions and geometry editing exist. | Multi-selection and all 13 group operations, multi-widget clipboard, incident-edge transfer, and restart persistence. |
| 4.1–4.5 | Switch, multifunction button (write bit, screen change, constant write, visibility), numeric setpoint, list, slider | Button, setpoint and screen-navigation representations exist; imported property metadata is retained. | True switch/list/slider controls, button action modes and register writes; bindings need protocol-level verification and simulated I/O tests. |
| 5.1–5.2 | Static text and 16-state dynamic text | Static and dynamic text (including 16 labels/colors) are imported. | Compare font/frame/resize rules and exact state selection on Android. |
| 6.1–6.6 | Digital indicator, bit indicator, bell/alarm, linear and circular indicators, audio-file playback | Indicator/progress/gauge/sound types exist with partial behavior. This turn maps source component 9 to a five-sound looping Craft alarm and preserves its imported sound choice. | Digital/bit states and linear/circular scaling need parity tests. Imported register bindings remain inactive. Craft ID 9 alarm playback is keyed per screen/widget, with source-contract coverage for independent stop/completion and lifecycle cleanup; simultaneous audio, trigger-device behavior, and exact APK waveform matching still need Android-device acceptance. |
| 7.2–7.5 | Animation, dynamic image, archive/trend, background image/color | Multi-state image, trend and screen backgrounds are represented; archive media is retained. | Animated GIF timing, image-state selection and archive recording/export need runtime parity and sample-project comparison. |
| 8.1–8.8 | Phone, register gateway, bit gateway, inbound/outbound SMS, RTSP video, push notifications, WebView | Craft component 19 imports as a visibly unsupported placeholder while retaining the three connections and modes. BLOODSKY has an outbound Telegram event widget and app-wide bot/chat settings. SMS palette entries remain visible but inactive until SIM-based send/receive and original component settings are ported. | Implement register/bit gateways, SMS send/receive, RTSP and WebView. Telegram is an additional notification path; configure a bot token and chat ID in the app, then bind a Telegram widget to a Modbus value. |
| 9, system registers | Built-in system values and registers | Selected internal/runtime values and BLOODSKY variables exist. | Map every manual register name, type, unit, update cadence and persistence rule against the source. |
| 10–11, FBD | RS; T/R/F triggers; TON/TOF/TP/BLINK; CTU; MOVE; POW; conversions; SIN; orientation; LN; XOR; ABS; sunrise/sunset; SEL; MX; temperature/humidity, light and speed sensors; MCR; ARRAY | Several source-compatible FBD blocks and authored macro graphs exist with tests. Opaque imported Craft macro records are preserved, not executed. | Finish per-block pin/type/scan/reset parity. Sensors need hardware lifecycle; macro import execution and full array semantics need verification. |
| 12, EL | AND/OR, integer comparisons and arithmetic, float comparisons and arithmetic; truth tables | BLOODSKY has Boolean and typed arithmetic/compare paths; not all visual layouts and type boundaries are identical. | Compare each truth table and edge cases to the manual; verify imported wire indexes/types and editor visuals. |

## Visual pages checked

- Pages 6–20 show the project list, editor/work-mode menus, connection and settings dialogs, and the screen navigation surface. These pages define the interaction flow as well as the labels.
- Pages 21–75 pair widget examples with property tables. The settings tables are useful for field-by-field editor coverage; screenshots alone do not establish runtime behavior.
- Page 91 documents the orientation sensor axes and angles: X heading uses north/east/south/west at 0/90/180/270 degrees; Y describes tilt; Z describes side roll. These are explicit acceptance values for the FBD sensor, not just an illustration to copy.
- Pages 102–109 show the logic element UI and truth tables. These should be used as truth-value acceptance cases when comparing the original EL block behavior.
- The original APK's bundled drawable resources are already available through the pinned-resource preparation step. Reusing a matching bitmap helps visual parity, but does not mark the associated feature as implemented when its interaction or data path is missing.

## Product scope from the user

- BLOODSKY remains an independent local application. Licensing, cloud administration and remote cloud control are explicitly out of scope. Optional Yandex/Xiaomi adapters do not make the app's local Modbus/FBD runtime depend on the internet.
- Local device transports, project editing, widgets, FBD/EL, archiving, and local web monitoring remain in scope.
- SMS remains an optional SIM-based channel alongside Telegram. Outbound SMS now sends on a configured Modbus rising edge and requests SEND_SMS permission at runtime; inbound SMS remains visible but disabled. Telegram notifications use the Bot API when the user configures a bot and chat.
- A push-to-talk assistant now offers explicit local on-device and online Android speech-recognition modes. Both use the same tested Russian command allowlist for screen navigation and confirmed project start/stop; this is not yet a general LLM or named-scenario executor.
- A first optional Home Assistant bridge discovers entity states and confirms on/off actions for supported light, switch, fan, and input_boolean entities; it supports a local Xiaomi Home-to-Home Assistant path. The direct Yandex adapter reads device/scenario inventory and can start named Yandex scenarios after confirmation; device capability writes and native OAuth sign-in remain follow-up work.

## Decoded component IDs confirmed this pass

| Craft ID | Decompiled class | Meaning | Import treatment |
|---:|---|---|---|
| 9 | `CustomComponents.c` | Bell/alarm. `posSound` selects one of five bundled sounds; one Modbus bit connection; sound loops while active. | Maps to BLOODSKY sound alarm; selected sound index and loop flag persist. The Craft binding stays in `craftConnectionInfo`, not the active BLOODSKY binding. |
| 19 | `CustomComponents.k` | Register gateway. Has source register, receive register and trigger connection records; modes select transfer behavior. Activation can write registers. | Maps to a labeled unsupported placeholder and preserves all original JSON/mode/connection fields. No gateway action is executed. |

## Working interpretation and immediate gaps

1. The PDF is a functional specification; imported APK source determines the exact serialized fields and runtime details. Keep those evidence sources distinct.
2. Preserve original project JSON and bindings before attempting conversion. An unknown or unmapped binding must not become an active device write.
3. Prioritize user-visible editor parity and safe archive/project round trips, then implement transport-backed widgets against a Modbus simulator, then optional network/media integrations.
4. Treat “code path present” as partial until the Android runtime is tested for interaction, save/reopen and failure states.

## Verification for this checkpoint

- Importer unit coverage asserts ID 9 classification, sound index and binding preservation, and ID 19's three-register connection metadata plus inactive status.
- Python resource preparation supplies the exact five original alarm audio assets under `R.raw.kc_alarm1` … `kc_alarm5`.
- Full Android unit/APK and visual runtime checks remain required on the exact pull-request head; source tests alone do not establish full parity.
