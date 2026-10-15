# BLOODSKY Android

BLOODSKY is the Android APK project for a faithful, improved successor to Kaskada Craft 1.0.7. The current goal is an installable Android APK; a Windows EXE is not the target of this project.

## Current implementation stage

The Android application module and CI build are being established. The initial screen editor currently supports:
- creating text, button and indicator placeholders;
- selecting, dragging and deleting components;
- editing a component's displayed text;
- saving the active draft locally on the device.

This is a foundation, not yet a complete Kaskada Craft replacement. The original component registry, full properties dialogs, FBD execution, Modbus RTU/TCP, original project import/export, archive handling and full UI parity remain to be implemented and tested.

## Build

GitHub Actions workflow **BLOODSKY Android APK** builds a debug APK from the Android project and uploads it as a workflow artifact. Debug APKs are for development/testing, not public release. A release build must use a properly managed signing key and pass install/runtime checks.

## Reverse-engineering notes

- `docs/BLOODSKY_IMPLEMENTATION_CONTRACT.md` — persistence and compatibility contract.
- `docs/BLOODSKY_ANDROID_COMPONENT_INVENTORY.md` — decompiled component and FBD inventory.
- `docs/BLOODSKY_EDITOR_PARITY_MATRIX.md` — editor parity checklist, Modbus acceptance tests and release gates.
- `docs/BLOODSKY_VISUAL_FIDELITY_CONTRACT.md` — strict visual-fidelity target and side-by-side comparison/release checklist.
- `tools/project_archive_inspector.py` and its tests — defensive, read-only project archive validation.
