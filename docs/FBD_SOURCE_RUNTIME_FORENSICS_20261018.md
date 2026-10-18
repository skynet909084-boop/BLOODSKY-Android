# BLOODSKY: original FBD runtime contracts — verification checkpoint

The user's repository changed from **private to public** on 18 October 2026. Existing original Craft reference `reference/craft-reference.zip` is part of the repository; do not pretend the copyright/source distribution implications disappear just because the repo is public.

## Accepted baseline

- FBD feature PR [#76](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/76) still targets stable, untouched `main`.
- SHA-pinned original label evidence `docs/ORIGINAL_FBD_SOURCE_PORT_EVIDENCE.md` proves visible labels, NOT runtime behavior.
- Latest accepted merged original-style editor package #107 `a3733fed0baeec3c335f4beefba1f1e2b333712c` passed exact-head APK/JUnit and full Android API30 smoke before merge.
- Following public visibility change, failed hosted-runner jobs were retried. Original source audit [39060397585](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39060397585) **PASS** on attempt 2; Android APK/JUnit [39060397635](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39060397635) **PASS** on attempt 2. Android API30 [39060397547](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39060397547) remained in progress at this note. The fast CI PR #108 also had APK [39059963201](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39059963201) PASS on attempt 2, quick emulator [39059963180](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39059963180) in progress.

## What this independent source-audit PR does

- `tools/original_fbd_runtime_evidence.py` checks SHA-256 of the pinned Craft ZIP, caps file count/sizes, rejects unsafe member names and does not execute embedded Java.
- It indexes **CTU, RS, TIMER and TRIGGER**, optionally CTD/SR/BLINK if present. It follows direct `extends` superclass relationships within the original FBD Logic directory and records per-class source hash, source line number, method/branch/call evidence (bounded to 96 short lines/class).
- `tools/test_original_fbd_runtime_evidence.py` tests fake zipped classes, direct superclass relationships, rejection of wrong hashes and unsafe ZIP paths.
- `.github/workflows/original-fbd-runtime-evidence.yml` runs the source extractor and publishes a small JSON evidence artifact without uploading the decompiled archive again.
- We must **inspect the generated evidence first**, then implement native-compatible counter/RS/timer/BLINK semantics as separate versioned BLOODSKY blocks with pinned regression tests. The scanner alone does not establish semantic parity and must never enable 40 disabled original palette previews by assumption.

## Exact original algorithm evidence — SHA-pinned extraction completed

Automated [source evidence workflow 39062491015](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39062491015) **PASS** after a regression fix: source `reference/craft-reference.zip` SHA256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`, artifact `12111628647`. The JSON was downloaded and execution sections inspected.

| Original class / SHA256 prefix | Evidence (1-based source lines) | Native portable rule |
| --- | --- | --- |
| `FB_CTU.java` `76baf47e` | `f()` 46–69, `getOut()` 88–93, `getCountIn()` 72–73 | Three BOOL source terminals `+`, `-`, `R`; independent rising-edge flags; `h0 = (char)(h0 + 1)` then `h0 = (char)(h0 - 1)`, then level-reset `h0=(char)0`; **output is Integer**, not Boolean, with Java unsigned 16-bit wrap |
| `FB_RS.java` `543a1707` | `f()` 41–55, `getOut()` 69–74 | Set S branch evaluated before reset R branch, so reset dominates when both high; retained state otherwise; own output may invert via internal `f8973e` flag |
| `FB_TIMER.java` `e9207266` | `f()` 62–154, `getOut()` 175–180, `setActionExecute()` 250–268 and `setTypeTime()` 270–282 | Separate input BOOL `IN` and integer time input; mode codes 1 (TON), 2 (TOF), 3 (TP), 4 (blink), time-unit codes 1 (*1), 2 (*1000), 3 (*`UsbId.SILABS_CP2102` symbolic source constant). The last constant's numerical definition and TP initial-output state still need verification |
| `FB_TRIGGER.java` `f44bdc2b` | `f()` 46–95 and `setActionExecute()` 183–194 | Mode 1 toggles latched Q on rising edge, mode 2 pulses on rising edge, mode 3 pulses on falling edge; optional output inversion |
| `Logic/c/c.java` and `Logic/c/d.java` | Read-only inherited source captured by superclass resolver | Native parent classes are **not the BLOODSKY runtime**. Confirm save/loading and wire/type behaviors before UI parity claims |

A separate implementation `FbdCraftNativeTransitions` and Java regression `FbdCraftNativeTransitionsTest` now codify the **demonstrated deterministic subset** (CTU uint16 wrap + edge/reset order, RS reset priority and output inversion, trigger toggle/R/F pulse). This is a *standalone portable source contract*, not an auto-replacement of BLOODSKY's existing `FBD CTU` CU/R/PV/Q extension or a newly enabled original palette action. Follow-up integration must add distinct versioned block IDs, numeric Q/OUT wiring and project migration tests before operator use.

**Do not assume the original timer's symbolic unit constant is exactly 60 seconds without reading its definition, and do not mark the entire FBD palette complete from this static trace.** Preserve user projects, legacy graph metadata and disabled preview rows.

## Native Craft timer state model, isolated batch — 18 October 2026

Portable `FbdCraftNativeTimer` and JUnit `FbdCraftNativeTimerTest` now encode the deterministic observable transitions in pinned original `FB_TIMER.java` SHA256 `e92072664732a19373a21afb21bb33837c412f8b7b711f3814d80bddca53c9f7`, lines 62–154.

- TON (original action code 1) resets output and timer latch on low, starts clock on first high, sets Q after elapsed preset.
- TOF (code 2) sets Q on high, delays false output after input falls, resets timer on high.
- BLINK (code 4) starts true when enabled, toggles Q on each full preset interval, resets when disabled. Because the original method resets `i0` at each toggle, the next evaluation restarts the clock: this subtle one-scan interval is preserved rather than "fixed" arbitrarily.
- TP (code 3) starts an epoch on new high, and visibly only clears Q after elapsed time. The observed `f()` body has **no explicit Q=true assignment** for TP. Accordingly, the pure model does not invent a positive pulse from a default false state. A seeded true output can be held until PT. Constructor / mode changes must be source-audited before we enable a native TP UI.
- Time unit codes source-confirmed: 1 (milliseconds) and 2 (*1000); code 3 multiplies by symbolic `UsbId.SILABS_CP2102`. A caller must pass an **independently verified** multiplier; no hardcoded unsupported number. The portable implementation fails on invalid unit or signed-overflow, explicitly safer than source int overflow.
- Source uses wall-clock `System.currentTimeMillis`, so the portable test supplies clock values deterministically. Existing BLOODSKY FBD runtime uses `SystemClock.elapsedRealtime` and is not changed, avoiding regressions in older saved projects.
- Test coverage: exact deadlines, restart/rearm, BLINK enable/disable and alternation, TP ambiguity, inversion, invalid mode/unit/duration, wall-clock rollback and overflow.

This standalone code is **not yet an original Craft runtime or a palette entry**. No existing BLOODSKY CTU/RS/TON/TOF/TP or project JSON is altered. Required to activate native Craft blocks: inspect constructor and `setActionExecute` TP initialization, resolve the third unit constant from pinned source, then create separate native type IDs and numeric/BOOL terminal tests plus source parity UI screenshots.

## Versioned native CTU now connected to BLOODSKY graph (separate from old CTU)

The source-backed `FBD CRAFT CTU` widget now exists as a **new** optional item in BLOODSKY's extended widget menu. This change must not be mistaken for renaming or upgrading the existing `FBD CTU`: both IDs are distinct and old project files keep their semantics.

- Inputs (all BOOL) are the original `+`, `-`, `R`; output `Q` has **NUMBER** type. The three visible contacts use ¼, ½, ¾ of block height. NUMBER Q can connect to typed numeric consumers `FBD MOVE.IN`, but **cannot** connect to Boolean AND, ensuring signal types are not confused.
- Each operator-mode scan uses the source-backed `FbdCraftNativeTransitions.ctu` pure function: independent rising edges, wrap at 65535 (Java `char`) and reset **after** increment/decrement. The result is stored in the existing `counterValue` property and numeric FBD output cache; a Boolean proxy is returned for internal engine traversal only (nonzero), not exposed on Q as a BOOL.
- Two additional fields `craftLastPlus` and `craftLastMinus` are intentionally **transient** (not serialized); cold project start begins with fresh native input edge detectors, while saved counterValue and unknown JSON remain preserved. No new mandatory project format fields were introduced. This avoids changing legacy projects.
- `FbdCraftVersionedCounterTest` covers named-pin routing independent of edge insertion order, numeric Q compatibility, wraparound, reset-last, held-input suppression, JSON copies, unknown metadata, HMI display and unchanged old `FBD CTU` pin contract.
- This native CTU is a supported BLOODSKY **source-compatible variant**, not evidence that the rest of the original FBD system (TP timer bug, output inversion, all 40 disabled palettes, real PLC conditions) is complete.

**Acceptance:** exact-head Android JUnit/debug APK and emulator UI smoke before merging. The current #76 FBD parent remains separate, draft, and stable `main` untouched.

## Original Craft T toggle mode — versioned operator block

A second standalone source-compatible block `FBD CRAFT T` is now available in BLOODSKY's **extended** FBD picker without altering the existing `FBD R_TRIG` / `FBD F_TRIG` variants. Original pinned `FB_TRIGGER.java` constructor at lines 18–35 initializes output `false` and mode 1; execution lines 53–63 toggles Q only on rising `IN` edges.

- Typed source-backed contacts: BOOL `IN`, BOOL `Q`. Numerical signals cannot connect to the Boolean input.
- Operator graph invokes `FbdCraftNativeTransitions.toggleRise` once per scan and keeps separate, **transient** `craftToggleOutput` / `craftToggleLastInput` state. Cold project loading resets both to false, as with a fresh source constructor, while saved unknown widget fields remain unchanged.
- `FbdCraftToggleGraphTest` checks rising→held→falling→rising toggles, typed pin compatibility, persistence isolation and legacy rising/falling blocks.
- This is an independent and clearly labelled extension, **not** a claim to have enabled the source's 40 disabled preview items.

## Source-matched Craft BLINK — standalone operator integration

- Distinct optional extended-palette block `FBD CRAFT BLINK`: source-like BOOL `IN`, NUMERIC `PT` and BOOL `Q`, preserving old timer IDs `FBD TON`, `FBD TOF` and `FBD TP`. It uses existing exact-int-millisecond saved `timerPresetMs` and MS/SEK/MIN property conversion; the spinner fixes the mode to BLINK rather than silently converting it to TON or TP.
- The operator evaluates the independently source-backed `FbdCraftNativeTimer.BLINK` once per scan with source `System.currentTimeMillis()` and a source-style 1-scan phase restart. IN=false clears phase, arms=false and Q=false; IN=true starts ON, toggles after PT, then repeats.
- Immutable transient `FbdCraftNativeTimer.State` is package-private (not copied into persisted JSON); switching screens/project loading resets its runtime epoch while the preset, opaque metadata and legacy graph remain intact. `resetFbdTimerRuntime` resets the new variant only in addition to existing supported timer semantics.
- `FbdCraftBlinkGraphTest` checks actual MainActivity graph execution with pin names, numerical PT compatibility from native CTU, no accidental BOOL input from numeric output, timed ON/OFF/ON, reset/rearm, legacy timer contracts and exact 2345ms load/reset.
- **Scope:** only BLINK, not original Craft TP whose constructor defaults Q=false and mode body has no Q=true assignment. No claim of pixel-perfect original timer or real-hardware test. Source unit-code 3 multiplier 60000 is verified from archived original. Keep original FBD preview catalogue disabled unless source behavior and visuals are documented individually.
- **Gate:** standalone PR JUnit/APK and Android API30 test must pass, then merge to #76 feature branch only; never publish `main` without a fully reviewed combined head.

## Original Craft 1.0.7 FBD preview activation — source-backed only

The original fullscreen logic palette already contains imported preview widgets tagged `FB_RS`, `FB_TRIGGER` and `FB_CTU` in both `res/layout` and `res/layout-land`, but those had been greyed out with the unsupported catalogue. They now have proven BLOODSKY actions **without changing the visual positions or replacing the original preview drawings**:

| Original source preview | New action | Verified runtime contract |
| --- | --- | --- |
| `FB_RS` | `FBD RS` | BOOL R/S → BOOL Q; reset dominant, matching original source and existing BLOODSKY RS |
| `FB_TRIGGER` | `FBD CRAFT T` | BOOL IN → BOOL Q, original retained toggle per rising edge, not one-shot R_TRIG |
| `FB_CTU` | `FBD CRAFT CTU` | BOOL +/−/R → NUMBER Q, independent edges and unsigned 16-bit wrap, NOT the existing threshold BOOL counter |

`OriginalFbdPalette.nativeTypeForOriginalTag` is an explicit allowlist. It now also enables `FB_CHANGE_I` and `FB_CHANGE_F` as their versioned, typed `FBD CRAFT CHANGE I/F` contracts, and `FB_MOVE` as a distinct typed `FBD CRAFT MOVE` (BOOL EN, signed-Integer I/Q). `FB_SEL`, `FB_ARRAY`, `FB_MX`, `FB_MACRO`, alternate native timer modes and other unverified previews remain disabled; similar names do not establish semantic equivalence. Existing independently verified AND/OR/TON/POW/etc. bindings remain unchanged. The `OriginalFbdDrawing` class and original XML palette geometry are untouched; only click actions for source-backed rows have been enabled.

`OriginalFbdNativePaletteMappingTest` asserts source mappings, numeric versus Boolean pin contracts, RS priority and unsupported tags. `tools/android_ui_fbd_quick.py` exercises the actual original palette, adds each enabled source-native node, checks non-overlap and verifies project cold-reload IDs. CH_I/CH_F and MOVE contracts are further documented in `docs/FBD_CRAFT_CHANGE_I_F_SOURCE_20261018.md` and `docs/FBD_CRAFT_MOVE_20261018.md`. No source preview is treated as complete merely from a visual label; MX/ARRAY register behavior and MACRO graph lifecycle remain open.

## Original FBD palette crowded-screen placement defect — resolved separately

Reviewing real Android screenshot from the accepted [#121](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/121) original RS/T/CTU palette run **39070131140** (artifact `12114798193`) exposed a real editor defect: when `freeFbdPlacement` returned null due lack of space, `addWidget` showed a toast but **still inserted a new 150dp FBD at the stale default offset**, overlapping existing native blocks. The test previously checked types and save/reload but failed to assert non-overlapping geometry.

The corrected `addWidget` now returns immediately without altering widgets, caches, history, or saved project on a failed placement. The toast tells the operator to make room first; this is intentionally a conservative safety behavior, **not** an infinite Canvas or auto-zoom feature. An expanded workspace/pan-and-zoom is a separate future UX task.

The real Android smoke now starts a second **fresh test project** for original RS/T/CTU picker selections, so all three can be placed and the test asserts that their rectangles do not overlap before cold-restart verification. Existing extended CTU/T/BLINK and 2345/3456 ms tests still run in the first project. RS now also uses the established source-gray default FBD canvas style, R/S/Q labels and no custom-color override, replacing the visibly inconsistent purple skin seen in the original screenshot. Dedicated Java skin assertions cover original R/S terminal fractions and default appearance.

No native FBD algorithms changed; no existing project files changed. Requires exact-head JUnit/APK + Android API30 before merging into draft feature #76. Original source palette fidelity remains incremental, not pixel-perfect.

## Non-negotiable gates

The runtime feature branch is not automatically promoted into `main`. Keep existing BLOODSKY CTU `CU,R,PV,Q` graph semantics intact. Original source CTU labels `+,-,R,Q`, RS priority and timer units are *open* until verified from original actual superclass implementation. Add separate native-compatible blocks, UI and source test only if the evidence supports actual operation; old project fields/anonymous wires must round-trip unchanged.

This note plus `docs/FBD_EXECUTION_HANDOFF_20261017.md` and [issue #75](https://github.com/skynet909084-boop/BLOODSKY-Android/issues/75) serve as durable continuation context. Once the evidence artifact is available, document findings here with exact SHA and CI run IDs instead of restarting the study.
