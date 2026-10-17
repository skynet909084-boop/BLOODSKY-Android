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

## Non-negotiable gates

The runtime feature branch is not automatically promoted into `main`. Keep existing BLOODSKY CTU `CU,R,PV,Q` graph semantics intact. Original source CTU labels `+,-,R,Q`, RS priority and timer units are *open* until verified from original actual superclass implementation. Add separate native-compatible blocks, UI and source test only if the evidence supports actual operation; old project fields/anonymous wires must round-trip unchanged.

This note plus `docs/FBD_EXECUTION_HANDOFF_20261017.md` and [issue #75](https://github.com/skynet909084-boop/BLOODSKY-Android/issues/75) serve as durable continuation context. Once the evidence artifact is available, document findings here with exact SHA and CI run IDs instead of restarting the study.
