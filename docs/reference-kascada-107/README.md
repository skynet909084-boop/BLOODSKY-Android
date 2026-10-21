# KaScada Smart Craft 1.0.7 (107) — reference and execution tracker

This folder imports a **static inspection of the user-supplied APK**, intended to guide BLOODSKY development. The source is the APK with SHA-256 `11464db76f59393ccf698f57e60af1756756ffc78cb315d43052ae92adb50d4f`, package `ru.kascada.modbus`, version `1.0.7 (107)`. The decompiled resources used for navigation have matching AndroidManifest/signature entries.

## Contents

- [STATIC_FUNCTION_AUDIT.md](STATIC_FUNCTION_AUDIT.md) — source-level menu, widget, logic, network, settings and project-function inventory.
- [INTERFACE_AUDIT.md](INTERFACE_AUDIT.md) — button IDs, callback and navigation map.
- [ui_inventory.json](ui_inventory.json) — machine-readable source-level IDs and resource inventory.
- [full_function_test_matrix.json](full_function_test_matrix.json) — 550 **proposed** individual verification cases, with original source references, steps, expected outcome and evidence status.
- [ANDROID_RUNTIME_TEST_PROTOCOL.md](ANDROID_RUNTIME_TEST_PROTOCOL.md) — runbook for a controlled Android test environment.

**These materials are not a claim that all 550 scenarios were executed**: each imported case starts at `WAITING_FOR_ANDROID_RUNTIME` and `STATIC_APK_RESOURCE_ONLY`. Do not convert it to `PASS` without a tested build/device, reproducible actions, an observed result and an artifact path. Blocked licensing, absent physical hardware, and source-only findings must be represented explicitly.

## Current 550-case execution count (2026-10-21)

The count is derived from `full_function_test_matrix.json`; it measures original-app cases with acceptable evidence, not BLOODSKY feature readiness.

| Status | Count |
| --- | ---: |
| PASS | 0 |
| FAIL | 0 |
| BLOCKED | 0 |
| NOT RUN (`WAITING_FOR_ANDROID_RUNTIME`) | 550 |
| Requires recheck | 0 |

The short original-app probes in PRs #201 and #202 have recorded bounded scenario results, but no individual case has yet met the matrix's original-plus-BLOODSKY equivalence evidence rule. Those scenario runs therefore do not change these K107 counts. The resumable full-profile implementation in PR #203 still requires exact-head CI and a deliberately interrupted/resumed artifact; no multi-hour traversal was started.

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
| HMI switch | `OriginalComponentPalette.java` binds `kc_picker_tab_switch` to a generic Button, so selecting a switch does not establish Craft switch semantics | P1: [Issue #193](https://github.com/skynet909084-boop/BLOODSKY-Android/issues/193) — add a real switch model, source-backed property UI and tests | Original switch states and register writes agree, including save/import/reopen |
| Numeric indicator | `kc_picker_tab_digital_indicator` currently adds a Text widget; visual placeholder is not proof of numeric indicator parity | P1: [Issue #194](https://github.com/skynet909084-boop/BLOODSKY-Android/issues/194) — source-backed widget type/format and bound-value rendering | Check signed/unsigned/float formats, range, geometry and device read |
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

## Existing executable original-app emulator route

The repository has [Original Craft 1.0.7 emulator probe](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/workflows/original-craft-emulator.yml), with a manual `workflow_dispatch` input `profile: full` and an Android 11/API 30 emulator. On the integration branch represented by this PR, the workflow rebuilds the pinned original decompilation, debug-signs it, runs `tools/original_craft_emulator_probe.py --full`, and uploads a short-retention artifact with screenshots, UI XML and logs; that build is **reconstructed/debug-signed**, not byte-identical to the published APK. Separate probe changes are under review in PR [#198](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/198): that branch verifies the published Release APK SHA-256, tries installing that exact APK first, and falls back to the reconstructed APK only if installation fails. Run #174 on PR #198 recorded `apk_source=original-release` and SHA-256 `11464db76f59393ccf698f57e60af1756756ffc78cb315d43052ae92adb50d4f`; its traversal was partial and must not be treated as full coverage. Existing [emulator audit](../CRAFT_1_0_7_EMULATOR_AUDIT_20261018.md) also records earlier runs that terminated before final JSON.

Run the workflow explicitly from the GitHub Actions page (`Run workflow`, choose the working branch and `full` profile). Then inspect completion, artifact evidence, traversal coverage and crash logs; map specific observed actions to K107 IDs. An unfinished run is not 550 passed tests. Network stays disabled and real external hardware is not simulated.

## Original APK and full source archives (verified GitHub Release, 2026-10-21)

The user confirmed permission to publish both files. They are stored as **release assets**, not copied into the normal Git history. GitHub reports the same SHA-256 digests as the independently examined user uploads.

| Artifact | Download | Size (bytes) | GitHub SHA-256 |
| --- | --- | ---: | --- |
| Original KaScada Smart Craft 1.0.7 APK | [KaScada.Smart.Craft.1.0.7.08.02.2026.apk](https://github.com/skynet909084-boop/BLOODSKY-Android/releases/download/kascada-analysis-1/KaScada.Smart.Craft.1.0.7.08.02.2026.apk) | 63,296,727 | `11464db76f59393ccf698f57e60af1756756ffc78cb315d43052ae92adb50d4f` |
| User-supplied full decoded ZIP | [KaScada_v1.0.7.107._base_src.zip](https://github.com/skynet909084-boop/BLOODSKY-Android/releases/download/kascada-analysis-1/KaScada_v1.0.7.107._base_src.zip) | 70,442,686 | `92d12206bde746f5a76c89e0c3311119287b14bb26cdc8ca36d515e2fca01f7a` |
| Curated build-time reference archive | [reference/craft-reference.zip](../../reference/craft-reference.zip) | 6,871,095 | `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd` |

[Open the release containing both original files](https://github.com/skynet909084-boop/BLOODSKY-Android/releases/tag/kascada-analysis-1).

**File naming:** the user's ZIP upload was named `KaScada_v1.0.7(107)_base_src.zip`. GitHub Release uses `KaScada_v1.0.7.107._base_src.zip`; matching SHA-256 verifies the binary content despite the renamed asset.

The earlier GitHub Actions artifact `BLOODSKY-Kaskada-decompiled` (run [38600957172](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38600957172)) has time-limited retention; it is no longer the sole source of the full reference code. The pinned `reference/craft-reference.zip` remains the lightweight subset used by Android CI.

**Provenance/verification rule:** keep the published ZIP byte-for-byte immutable as a reference, check SHA-256 after each retrieval, and continue verifying ambiguous JADX against Smali. Treat APK/runtime and decoded-source observations as separate evidence. The presence of the original files does **not** mark any of the 550 interactive tests as passed. For complete behavioral testing, run the [original-app Android probe](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/workflows/original-craft-emulator.yml), retain screenshot/UI-tree evidence, and reconcile specific cases with BLOODSKY.

