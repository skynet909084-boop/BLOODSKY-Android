# BLOODSKY FBD — working handoff, 17 Oct 2026

## Current authoritative status — 17 October 2026

- **SINGLE working FBD integration:** [PR #76](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/76), branch `feat/fbd-75-pin-semantics`, latest merged feature commit **`a3733fed0baeec3c335f4beefba1f1e2b333712c`**. Still a draft targeting untouched stable `main`. #100 and large #106 were previously merged, including all #101–105 code.
- **Source-style and timer UI #107 ACCEPTED & MERGED into #76.** Exact tested PR107 head `3b3d4241f23f29aa25b061a921f898bcef6628df` passed 124 Python regressions, Android JUnit and debug APK [39058889524](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39058889524), plus the complete API30 UI smoke [39058889521](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39058889521). Emulator artifact `12110094620` result.json `passed`; screenshots `04q` and `04w` visually inspected: source-gray AND/TON bodies, correct Q→IN wire, dark block context commands, absent old purple background. Integration merge SHA `a3733fed0baeec3c335f4beefba1f1e2b333712c`.
- Features now in working tree: typed source/target FBD wires with inversion/undo, arithmetic, timers TON/TOF/TP, optional exact MS/SEK/MIN display preserving integer milliseconds, source-styled editor AND/OR/XOR/timers/compare, block long-press actions, Modbus→FBD→HMI, deletion-safe `fbd://N` references, manual FBD input, migrated legacy PNG/WAV content.
- **CI speed work in progress:** [PR #108](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/108), branch `ci/fbd-fast-emulator-gate-20261017`, targets #76. Quick actual emulator checks on feature-to-feature PRs; original full emulator suite stays automatic for PRs targeting main, push main, and manually dispatched by default. See `docs/FBD_FAST_CI_WORKFLOW_20261017.md` in #108. Initial quick test workflow `39059678989` failed **before running Android assertions** because ReactiveCircus received a multiline shell `if` string; repaired with one-line `bash tools/run_android_ui_profile.sh` wrapper and safe summary output. **The latest #108 exact head `39523cd0767c119628b15797d5f74bf1095ea020` is NOT accepted**: its jobs `39059963201` and `39059963180` failed without job step/log details. At the same time the new #76 merged-head jobs `39060144805`, `39060144738`, `39060144730` failed within seconds without executed steps. This points to a GitHub Actions runner/account/infrastructure blocker, **not demonstrated Android test failures**; exact cause not confirmed. Do not use these jobs as PASS, do not merge #108 until a real emulator smoke and APK/JUnit run complete successfully.
- Historical successful full FBD CI predecessor #76 `87f1aa1ada09580a1bfd7568bf0f060dd09aec58`: source audit `39055132308`, Android APK/JUnit `39055132283`, API30 smoke `39055132344` all PASS. This does not substitute for merged-head reruns.
- **Original Craft parity still open:** native CTU `+,-,R` numeric output vs provisional BLOODSKY `CU,R,PV`; RS latch precedence; BLINK, 40 disabled original FBD palette execution actions; original same-viewport screen comparison and physical Android/PLC test. Therefore close the *engineering phase* only after explicit acceptance; never claim 100% Craft equivalent.
- Continue directly in GitHub, with grouped code commits + single final CI runs. No paid agents/services, no signing or changes to `main`. Existing completed PR #98–107 must not be needlessly redone.

Historical notes below are retained for traceability, but this section supersedes their older pending statuses.

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

## Consolidated source geometry / FBD manual input — prepared branch

Combined head branch: `feat/fbd-original-pin-geometry-constant-input-20261017`, starting from #105. All previous FBD patches #101–105 are inherited in order. This branch is intentionally not yet a PR while previous emulator checks are running; opening a new PR earlier would spend additional free GitHub Actions minutes.

- AND, OR, XOR, and numeric compare terminals use source-documented input fractions `1/4`, `3/4` from `docs/CRAFT_FBD_SOURCE_MATRIX.md`. Both drawn pin dots and wire endpoints use the same pure `fbdTerminalFraction` calculation. Three-input BLOODSKY counters keep explicit `1/4`, `1/2`, `3/4` positions; other extensions retain their previously tested geometry.
- FBD Вход now exposes a Boolean constant toggle in the normal properties dialog. It reads `inputValue`, applies to the saved widget, and remains compatible with optional Modbus TCP input bindings. State/extraFields round-trip tested by `FbdManualInputPersistenceTest`.
- New Python regression `tools/test_fbd_manual_input_property.py` checks actual editor checkbox wiring and ensures Canvas drawing is not executing stateful FBD evaluation.
- #100 source scanner lexical fix fully passed source audit, APK and API30 UI, and was merged into the #76 feature branch at `51aa8c8199cea6a28a75ec1f3a580066facb417f`.
- #101–#105 have green exact-head APK/JUnit builds at this checkpoint, but UI smoke must finish before acceptance. Use GitHub workflows, not agent task comments, as proof.

**Efficient integration:** After #105 emulator passes and this preparatory branch is reviewed, open ONE consolidated draft PR from this branch into `feat/fbd-75-pin-semantics`. This carries all app work #101–#105 + the two new improvements. Run full exact-head APK/JUnit + API30 smoke + source audit against #76's updated #100 baseline. Merge into the FBD feature branch if all pass; close stacked superseded drafts with explicit references. Do not merge to stable main or declare full original Craft parity until the unresolved source semantics/hardware/screenshot gates are satisfied.
