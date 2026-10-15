# BLOODSKY Android

BLOODSKY is an Android APK project for a faithful, improved successor to Kaskada Craft 1.0.7. The current deliverable is an installable Android APK; a Windows EXE is not the target of this project.

## Current implementation stage

The current prototype includes:
- a multi-screen editor with screen naming and background color controls;
- canvas widgets, selection, dragging, resize grip, copy/paste and delete;
- widget property editing and preservation of unknown JSON metadata;
- local project persistence plus JSON import/export;
- a Modbus TCP register scanner for FC03/FC04;
- periodic polling of widgets with `tcp://host:port/unit/address` bindings;
- an initial set of FBD logic blocks and connections, including cycle checks.

This is still a working prototype, not yet a complete Kaskada Craft replacement. The exact original UI has not been verified pixel-for-pixel against the decompiled APK and reference screenshots. Full component/property parity, Modbus RTU, broader protocol compatibility, robust runtime testing and a signed release build remain open work.

## Build and tests

GitHub Actions workflow **BLOODSKY Android APK** runs the Python archive-inspector tests, Android unit tests, builds the debug APK and uploads the artifact as `BLOODSKY-Android-debug-APK` for 14 days. It runs on relevant code pushes/PRs, can be started manually, and performs a scheduled daily validation/build at 03:17 UTC.

Debug APKs are for development/testing, not public release. A release build must use a properly managed signing key and pass installation and runtime checks.

To run the archive-inspector tests locally:

```sh
python -m unittest discover -s tools -p 'test_*.py' -v
```

## Reverse-engineering notes

- `docs/BLOODSKY_AUTONOMY_PLAN.md` — autonomous CI behaviour, product milestones, and clear boundaries of automation.
- `docs/BLOODSKY_IMPLEMENTATION_CONTRACT.md` — persistence and compatibility contract.
- `docs/BLOODSKY_ANDROID_COMPONENT_INVENTORY.md` — decompiled component and FBD inventory.
- `docs/BLOODSKY_EDITOR_PARITY_MATRIX.md` — editor parity checklist, Modbus acceptance tests and release gates.
- `docs/BLOODSKY_VISUAL_FIDELITY_CONTRACT.md` — strict visual-fidelity target and side-by-side comparison/release checklist.
- `tools/project_archive_inspector.py` and `tools/test_project_archive_inspector.py` — defensive, read-only project archive validation and tests.
