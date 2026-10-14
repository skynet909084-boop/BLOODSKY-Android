# Kaskada Craft 1.0.7 — decompilation audit for BLOODSKY

Date: 2026-10-14  
Source APK: `KaScada.Smart.Craft.1.0.7.08.02.2026.apk`  
Workflow run: [38600957172](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38600957172)  
Artifact: [BLOODSKY-Kaskada-decompiled](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38600957172/artifacts/11912849123)

## Status

The decompilation pipeline now completes successfully. The artifact was downloaded and inspected:
- JADX tree: 11,080 files, including 6,228 Java source files.
- Apktool tree: 15,193 files, including 10,355 Smali files.
- The archive contains 26,276 files and is approximately 136 MB.
- JADX reported 110 decompilation errors. Its output is therefore useful but not a perfect source reconstruction; inspect Smali for any missing or malformed Java classes.
- Apktool decoded the manifest, resources, assets, and Smali successfully. Its log contains many case-insensitive path collision warnings from the artifact upload environment; the decoded files themselves are present in the artifact.

## Confirmed application structure

Package root: `ru.himik.nightscream.kascada`

- `MainActivity.java` — app entry point, project list and app-level flows.
- `mbCanvas.java` — central screen/editor/operator activity. JADX reconstructed it as a very large class (~938 KB, 15,614 lines), so it needs to be studied in focused sections rather than ported wholesale.
- `CustomComponents/` — reusable on-screen controls/widgets; many class names are obfuscated, so correlate class code with layouts, drawable resources and Smali.
- `Logic/` — FBD/logic blocks found in reconstructed source: AND, OR, XOR, RS, CTU, TIMER, TRIGGER, integer/float arithmetic and compare, MOVE, SEL, MX, ARRAY, MACRO, conversions, ABS, LOG, SIN, POW, MQTT publisher/subscriber and device/sensor-related blocks.
- `Bd/Export/ProjectExportActivity.java`, `Bd/Import/ProjectImportActivity.java`, `Bd/ProjectLoadListActivity.java`, `Bd/ProjectDeleteListActivity.java` — project import/export, load and delete flows.
- `Connections/Wifi/WifiConnector.java` and `Connections/Cloud/CloudConnector.java` — socket-based communication connectors; both load native library `modbus`.
- `Connections/Usb/driver/` — serial USB driver implementations including CDC ACM, CH34x, Prolific, CP21xx and FTDI.
- Manifest registers `MainActivity`, landscape `mbCanvas`, preferences, project import/export/load/delete, Bluetooth device list, network device preferences, USB attach handling, foreground service and MQTT service.
- Decoded assets include `assets/web/KascadaWebControl.html`, bundled web assets and `assets/help.pdf`.

## Important technical conclusions

1. **Do not treat a green workflow alone as proof of perfect decompilation.** JADX reports 110 errors; the Smali output is the fallback reference for affected methods.
2. **The editor is concentrated in `mbCanvas`.** This is likely where canvas gestures, screen elements, property dialogs, operator mode and register bindings converge. Build a method map first; avoid blindly copying this huge class.
3. **Keep the original app's project/data format compatible where practical.** Inspect the database helper/models and ZIP import/export implementation before defining BLOODSKY's persistence schema.
4. **Native Modbus dependency matters.** The Wi-Fi/cloud connectors call `System.loadLibrary("modbus")`. A BLOODSKY port must explicitly account for protocol implementation and native library/ABI availability, or replace it with a tested implementation.
5. **Obfuscated names require cross-referencing.** Use class references, resource IDs, layout XML, strings and Smali together to identify widget roles reliably.

## Next reverse-engineering steps

1. Map the key `mbCanvas` methods into: editor/operator mode, canvas gestures, widget creation, property dialogs, project persistence, register bindings, communication polling and FBD execution.
2. Inspect `CustomComponents` classes against layout/drawable resources to build a widget catalogue (buttons, indicators, setpoints, text, images, dynamic text and sounds).
3. Trace the database helper and models plus ZIP import/export to document the exact project schema and preserve compatibility.
4. Trace Modbus RTU/TCP requests and register address/data conversion through connectors and the native `modbus` library.
5. Convert these findings into a BLOODSKY implementation checklist, then implement/test feature groups incrementally. Do not claim a feature is ported until it works in the BLOODSKY build.
