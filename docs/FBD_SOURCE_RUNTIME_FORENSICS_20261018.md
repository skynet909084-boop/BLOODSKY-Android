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

## Non-negotiable gates

The runtime feature branch is not automatically promoted into `main`. Keep existing BLOODSKY CTU `CU,R,PV,Q` graph semantics intact. Original source CTU labels `+,-,R,Q`, RS priority and timer units are *open* until verified from original actual superclass implementation. Add separate native-compatible blocks, UI and source test only if the evidence supports actual operation; old project fields/anonymous wires must round-trip unchanged.

This note plus `docs/FBD_EXECUTION_HANDOFF_20261017.md` and [issue #75](https://github.com/skynet909084-boop/BLOODSKY-Android/issues/75) serve as durable continuation context. Once the evidence artifact is available, document findings here with exact SHA and CI run IDs instead of restarting the study.
