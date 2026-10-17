# BLOODSKY FBD — working handoff, 17 Oct 2026

## Continuation anchor

Repository: `skynet909084-boop/BLOODSKY-Android`. Do **not** restart from earlier PR #70 or reassign vague tasks to agents. All work is being performed directly and verified in GitHub Actions. Main branch remains stable.

- Active integration parent: draft PR #76, `feat/fbd-75-pin-semantics`, exact integrated commit `28199b37dd07d27806945a9197165e9795b814ee`.
- PR #98 merged into #76: original Craft SHA-pinned source labels and method evidence.
- PR #99 merged into #76: typed FBD+history and lossless legacy PNG/WAV migration; its original isolated APK/UI checks passed at `d65ff20c951f1171ec1d7a3af5994856d92a88b6`.
- Draft PR #100, branch `audit/fbd-java-lexical-evidence-20261017`: ignore comment/string false positives in original source scanner. Exact head `477420678c692a2c4511f610fe3924eabe2c243c`, source audit and APK pass, API30 outcome still to be confirmed.
- This FBD engineering branch `feat/fbd-runtime-wiring-final-batch-20261017` is based on the merged #76 head. It is **separate from PR #100**, so source-audit checks can continue independently.

## This batch: implementation and expected behavior

1. Typed per-pin wire geometry: lines follow source/target named ports as blocks move, with two or three separate input rows where known; unlabeled legacy wires retain center fallback.
2. BOOL-only wire inversion with a visible editor menu, explicit `inverted` JSON metadata, preserved unknown fields, undo/redo, and synchronous-save failure rollback. NUMBER and unnamed legacy wires cannot be inverted.
3. Editing and rendering do not execute the FBD runtime or change timer/counter/latch values; runtime is evaluated separately in operator mode. Editor renders `OUT = —` as a deliberate non-live placeholder rather than a misleading result.
4. Cold project load and entering runtime clear transient TON/TOF/TP elapsedRealtime timestamps. Existing presets, counter data, widget data, unknown fields and old media are retained.
5. Regression JUnit and Android API30 emulator checks include pin geometry, typed inversion, JSON round-trip, timer epoch protection and inversion undo/redo.

## Required checks before integrating this draft

- Android unit tests, debug APK and Python tests on **exact head**.
- API30 UI smoke, screenshot `04v-fbd-inverted-bool-wire.png`, project JSON containing BOOL `inverted:true`, then undo -> redo -> undo.
- Original source audit remains required on integration parent. PR #100 must be tested and then merged into the feature branch separately.
- Merge only to feature branch after green exact-head checks; do not merge to stable `main` or issue a release until overall FBD parity acceptance.

## Open source-parity blockers (do not claim completed)

Original user-supplied `reference/craft-reference.zip` SHA256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd` proves short labels CTU `+,-,R,Q`, RS `R,S,Q`, timer `TON,IN,Q` with TOF/TP modes. It does **not** establish full executable semantics. BLOODSKY CTU `CU,R,PV,Q` is a provisional extension. Original timer units, RS priority, original output-inversion convention, all 40 source FBD preview actions and pixel-perfect UI comparison require further direct source/hardware analysis. Unsupported original palette actions remain disabled. FBD numeric inputs and outputs should not be misrepresented as source-equivalent.

## Next follow-up sequence

1. Inspect exact head CI and emulator artifact, repair isolated failures (never weaken tests).
2. Integrate accepted #100 and this PR into #76 in dependency-safe order.
3. Independent read-only source superclass analysis of original CTU/RS/TIMER; only then implement versioned source-compatible counterparts while keeping old BLOODSKY projects working.
4. Add visual equal-density original/clone side-by-side and physical Android test; only then evaluate closing #76 / merging main.
5. Move to editor widgets, Modbus, tags, alarms and runtime workstreams #79–86; do not repeatedly re-audit old accepted FBD changes.

Log each exact SHA, CI run, merge and blockers in this file before declaring the phase done. Passing APK is not equivalent to functional parity.
