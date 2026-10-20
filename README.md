# BLOODSKY Android

BLOODSKY is an Android APK project for a faithful, improved successor to Kaskada Craft 1.0.7. The current deliverable is an installable Android APK; a Windows EXE is not the target of this project.

## Current implementation stage

The current prototype includes:
- named projects with separate create/load/delete actions and migration of the previous active project;
- a multi-screen editor with screen naming and background color controls;
- canvas widgets, selection, dragging, resize grip, cut/copy/paste and delete;
- widget property editing and preservation of unknown JSON metadata;
- compact original toolbar and a separate foreground operator mode with guarded runtime gestures;
- local project persistence plus portable ZIP image/audio import/export and JSON export;
- original APK home background/buttons, calculator, supported five-category palette and selection-action icons, with recorded source provenance;
- the original 40-preview FBD catalogue with source-backed native logic, math/conversion, timer, trigger, register, MX/ARRAY and selected sensor/MQTT integrations; unsupported previews remain disabled;
- original indicator ON/OFF image menus, built-in lamps and custom document-provider images retained after process restart;
- a Modbus TCP register scanner for FC03/FC04;
- periodic polling of widgets with `tcp://host:port/unit/address` bindings;
- typed FBD pins and graph validation, Craft `.mcr` import/export, graph capture/editing for BLOODSKY-authored macros, including bounded nesting of validated graph-backed macros, and per-instance nested graph execution. Opaque original Craft macro children are preserved but not executed.

This is still a working prototype, not yet a complete Kaskada Craft replacement. The exact original UI has not been verified pixel-for-pixel against the decompiled APK and reference screenshots. Full component/property parity, execution of opaque original Craft macro children, Modbus RTU binding into project widgets, broader protocol compatibility, physical-device testing and a signed release build remain open work. The prototype now has an explicit USB serial connection API over the tested RTU client; it does not automatically activate imported bindings and still needs exact-head CI and hardware acceptance.

Current accepted milestones, implementation gaps and device/release gates: [execution status](docs/BLOODSKY_EXECUTION_STATUS.md).
The current source-backed, page-by-page functional audit and remaining implementation plan are in [Kaskada full parity audit](docs/KASCADA_FULL_PARITY_AUDIT_20261018.md); the machine-readable matrix is intentionally conservative and marks source/runtime or device parity as partial until its acceptance gate passes.

## Editor and operator controls

- Use the bottom-left component icon to open the palette. Tap a component to edit properties; long-press it for the context menu. The bottom strip provides cut/copy/paste/alignment.
- The component palette page corner opens the FBD catalogue; the FBD page corner opens the third page, the macro editor. It creates macros from a selected FBD output and its upstream graph, lists project macros, and adds or imports/exports them. Other BLOODSKY elements remain available from that editor. Supported source-backed entries add native FBD blocks; dimmed entries remain unavailable. Back dismisses the wide catalogue.
- Long-press an FBD block for wiring, inversion, timer/property actions, and other commands. Long-press a macro with an embedded BLOODSKY graph and choose **Редактировать вложенный граф** to edit it; assign its output pin from a verified block, then save through validation. Opaque original Craft child records are not executed.
- Timer/counter presets and comparison properties reject invalid input while keeping the form and saved project unchanged. Comparison accepts decimal comma and `=`/`<>` aliases.
- The original play icon starts operator mode; the stop icon returns to editing. Runtime hides editing tools, grid/selection handles and FBD editor visuals. Short taps operate existing controls; dragging and long-pressing do not edit or activate them.
- The left toolbar icon hides the toolbar; the floating icon reveals it. The right icon opens project/screen commands. Back stops operator mode first, then returns from editing to the home menu with a save.
- A project reopened after process restart starts in the editor and retains its saved component state. Foreground preview polling retains its existing behavior; USB/Bluetooth and background service operation remain unverified.

## Regression guardrails

`tools/test_android_source_contract.py` adds source-contract checks for project persistence, multi-screen and legacy loading, import validation limits, Modbus TCP response checks, unknown metadata preservation, FBD cycle prevention, and the CI build steps. These checks are fast regression guards; they do not replace device-level or instrumentation tests.

## Build and tests

GitHub Actions workflow **BLOODSKY Android APK** runs the Python archive-inspector tests, Android unit tests, builds the debug APK and uploads the artifact as `BLOODSKY-Android-debug-APK` for 14 days. It runs on relevant code pushes/PRs, can be started manually, and performs a scheduled daily validation/build at 03:17 UTC.

A separate [Android UI smoke workflow](docs/ANDROID_UI_SMOKE.md) installs the debug APK on a disposable API30 emulator, tests create/restart/reopen/delete and saves screenshots/UI XML. Passing this gate does not establish original visual parity or physical-device compatibility.

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
