# Original Craft FBD gap review — status 24 Oct 2026

## Scope and evidence

Source review uses the pinned KaScada Smart Craft 1.0.7 archive, SHA-256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`, decoded Java under `ru.himik.nightscream.kascada/Logic`, and the original palette XML. Arithmetic source hashes are recorded below. Source and unit-test evidence do not establish visual or hardware parity.

The all-in-one branch `prototype/all-in-one-20261020` is synchronized with `main` through PR #419 and follow-up Craft PRs #421/#422. The prior checklist in this file named old PRs and described unfinished code that is now present; this status replaces it.

## Source-backed runtime work completed

1. **Typed arithmetic:** `FB_ARITHMETIC_INT`, `FB_ARITHMETIC_FLOAT`, and `FB_COMPARE_FLOAT` use separate Craft block types and typed pins. Tests cover unsigned-char wrapping, Float32/IEEE division, comparison modes including NaN, named graph wiring, and persistence (`FbdCraftMathTest`, `FbdDisconnectTest`, `CraftSourceProjectFbdRuntimeTest`). Source SHA-256: INT `2914b05cbda9f9b0a845b9d65682bc93f77c20fd4965eeb459f76b471953ae8f`; FLOAT `3385ba4c8a1df430d3ffe4ac64e3cc284ab10a7c7130c49171d18a2e95946192`; comparator `6658837671a8c6471da23d8b996ea0620948b0702ea44b0729ee14d3083b75ad`.

2. **Native transitions:** `FB_TRIGGER` modes T/F/R, reset-dominant `FB_RS`, unsigned-16-bit `FB_CTU`, and Craft timer modes have separate transition models and tests (`FbdCraftNativeTransitionsTest`, `FbdCraftNativeTimerTest`, `FbdCraftToggleGraphTest`). Craft's source TP behavior remains distinct from BLOODSKY's generic IEC timer behavior.

3. **Pull-evaluated blocks and registers:** `FB_MOVE`, `FB_POW`, `FB_SEL`, `FB_MX`, and `FB_ARRAY` resolve native named pins. ARRAY writes on its rising enable; MX reads the project-local Craft register store. Bounds, missing values, and read/write edges have tests (`FbdCraftMoveTest`, `FbdCraftFloatConversionTest`, `FbdCraftRegisterGraphTest`). External transport and hardware behavior are separate acceptance items.

4. **Macro blocks:** Craft `.mcr` sidecars are preserved and validated; only child graphs whose block and wire semantics are supported execute. Missing or unsupported macro graphs remain visible and non-executable. Synthetic graph tests exist, but none of the supplied source projects contains an `.mcr` sample, so acceptance with an original nested macro remains open.

## Remaining gaps

1. **Visual parity:** source-specific block layout, text baselines, pin positions, modes, and selection/disabled states still need same-density comparison against the original APK. Reserve this for the release-candidate stage.

2. **Original macro evidence:** use a source-authentic Craft project with a nested `.mcr` sidecar to verify import, edit, save/reopen, pin typing, and supported execution. Do not broaden executable coverage based on synthetic fixtures alone.

3. **Hardware and transport acceptance:** validate sensor availability, register mappings, retries, and lifecycle against a mock peer and supported Android hardware. Routine development checks remain Python tests, Android unit tests, and APK build; emulator UI, device, and visual checks are deferred to RC.

4. **Unknown factory ID 3:** pinned `mbCanvas.m()` has no ID 3 branch. Keep it unresolved and fail-closed unless a source call-site or Smali path identifies its exact class and behavior; do not guess an implementation.

Do not claim full FBD or Craft parity while these acceptance gaps remain open.
