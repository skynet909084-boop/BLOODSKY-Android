# KaScada Smart Craft 1.0.7 (107) — reference and execution tracker

This folder imports a **static inspection of the user-supplied APK**, intended to guide BLOODSKY development. The source is the APK with SHA-256 `11464db76f59393ccf698f57e60af1756756ffc78cb315d43052ae92adb50d4f`, package `ru.kascada.modbus`, version `1.0.7 (107)`. The decompiled resources used for navigation have matching AndroidManifest/signature entries.

## Contents

- [STATIC_FUNCTION_AUDIT.md](STATIC_FUNCTION_AUDIT.md) — source-level menu, widget, logic, network, settings and project-function inventory.
- [INTERFACE_AUDIT.md](INTERFACE_AUDIT.md) — button IDs, callback and navigation map.
- [ui_inventory.json](ui_inventory.json) — machine-readable source-level IDs and resource inventory.
- [full_function_test_matrix.json](full_function_test_matrix.json) — 550 **proposed** individual verification cases, with original source references, steps, expected outcome and evidence status.
- [ANDROID_RUNTIME_TEST_PROTOCOL.md](ANDROID_RUNTIME_TEST_PROTOCOL.md) — runbook for a controlled Android test environment.

**These materials are not a claim that all 550 scenarios were executed**: each imported case starts at `WAITING_FOR_ANDROID_RUNTIME` and `STATIC_APK_RESOURCE_ONLY`. Do not convert it to `PASS` without a tested build/device, reproducible actions, an observed result and an artifact path. Blocked licensing, absent physical hardware, and source-only findings must be represented explicitly.

## Reconcile with evidence already in BLOODSKY

Existing project evidence predates this imported test matrix:

- [Original Craft emulator audit](../CRAFT_1_0_7_EMULATOR_AUDIT_20261018.md) — **partial real Android 11/API 30 original-app smoke**, screen images/UI trees, interrupted exhaustive traversal; do not say the original was never run anywhere.
- [Full parity audit](../KASCADA_FULL_PARITY_AUDIT_20261018.md) — source/manual-to-implementation crosswalk and known gaps.
- [Unified acceptance matrix](../BLOODSKY_UNIFIED_ACCEPTANCE_MATRIX_20261020.md) — the project's **single** readiness score; do **not** invent a second percentage from 550 planned test cases.
- [Original asset provenance](../../reference/README.md), [visual fidelity contract](../BLOODSKY_VISUAL_FIDELITY_CONTRACT.md) — provenance and same-density screenshot acceptance.

Old emulator recordings can substantiate individual cases only after an auditor maps a *specific* screenshot, UI tree, run and action path to that case. No bulk-pass based on simply finding an Activity, icon, handler or old artifact.

## Concrete BLOODSKY gaps to triage (observed in current source)

| Area | Observation | Action | Acceptance |
| --- | --- | --- | --- |
| HMI switch | `OriginalComponentPalette.java` binds `kc_picker_tab_switch` to a generic Button, so selecting a switch does not establish Craft switch semantics | P1: add a real switch model, source-backed property UI and tests | Original switch states and register writes agree, including save/import/reopen |
| Numeric indicator | `kc_picker_tab_digital_indicator` currently adds a Text widget; visual placeholder is not proof of numeric indicator parity | P1: source-backed widget type/format and bound-value rendering | Check signed/unsigned/float formats, range, geometry and device read |
| Missing palette entries | GIF, Camera, WebView and voice-recognition entries are explicitly disabled in `OriginalComponentPalette.java` | P1/P2: implement independently with permissions, safe runtime behaviour and tests | Available only once genuine implementation works on device |
| Project network settings | PR [#191](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/191) introduces source-backed network device/server UI but is not yet merged into the working branch at this import | Avoid duplicate concurrent patches; review that PR and its exact-head CI first | Original import round-trip, 16 slots, endpoint handling, no unverified transport activation |
| Operator/device parity | Existing audits show partial transport/runtime support and no completed hardware or same-density original-vs-BLOODSKY parity | Keep device/hardware gates open; use API 30 original + current BLOODSKY smoke fixtures | Screenshot/gesture comparisons, real serial/Modbus tests, no misleading PASS |

These are *development targets*, not claims that a code change is already implemented. Original APK, proprietary binaries, tokens, and signing keys are deliberately **not** copied into this public repository.

## Safe follow-through

1. For each `K107-xxxx`, capture original run/build information, input actions, observed result, screenshot/UI XML/video and status.
2. Attach a matching BLOODSKY reproduction plus exact version/commit; mark `PASS` only with actual evidence; record `FAIL` and `BLOCKED` explicitly.
3. Open one focused implementation task/PR per confirmed gap, with regression test, Android unit gate and debug APK build.
4. Keep all test fixtures and the external original APK isolated; no Modbus operations on production controllers, SMS to real recipients or cloud purchases during automated audits.
5. Only update the unified acceptance matrix after **its** independent acceptance requirements are satisfied.

The source-inventory validator `tools/test_kascada_107_reference.py` checks IDs, coverage and provenance; it **does not** substitute for functional tests.
