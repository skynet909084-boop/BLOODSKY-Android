# BLOODSKY FBD — working handoff, 17 Oct 2026

## Continuation 18 Oct 2026 — full-suite document-picker blocker

- Verified feature head `29b9bcaae2ca7c2ed6bce2d6d9bb1d4e5cde463d`: APK/JUnit 39088568857 and all three source audits PASS; full Android UI 39088568858 FAILED in `tap_document("bloodsky-custom.png")` (job 117560302627). This is not an RS/FBD assertion failure. Failure artifacts exist as 12121514401; artifact UI XML has not been inspected in this session.
- Found a concrete selector gap: `tap_document` accepted only filename text despite the existing tested `document_nodes` helper supporting Android grid accessibility descriptions. Use that helper while retaining two-snapshot bounds stability, ambiguity rejection and bounded retries. Require BLOODSKY's package to appear after selection; an empty accessibility tree no longer counts as success. This is a candidate fix, not a confirmed diagnosis of the unseen device XML.
- Added executable mocked regressions for description-only grid rows, transient empty hierarchy, and ambiguous filenames. After pinned resource preparation, all 137 Python tests PASS. Local Android JUnit/APK NOT RUN: `gradle` is absent. Independent CI and full API30 acceptance still required.
- PR134 head `d36a957d63a3a115ce5923dcd7a91c237650a958`: source evidence 39088329098, APK 39088329099, quick UI 39088329116 all SUCCESS. PR135 head `8f1860480ad1cbd01b8b7d4df397a6deb0f5de21`: APK 39088794373 and quick UI 39088794269 SUCCESS. Neither result proves full-suite acceptance or direct CH_I/CH_F click/restart coverage. Both remain draft, not integrated by this continuation.
- Next: confirm this picker correction in FULL emulator profile; inspect saved picker XML on failure. Then integrate reviewed source/CHANGE work and add direct native CHANGE UI persistence coverage. Stable main unchanged; full original visual parity and hardware acceptance remain open.

## Final accepted FBD milestones on 18 October 2026 (after the earlier checkpoint)

- Feature PR [#76](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/76) remains **DRAFT**, targeting unmodified stable `main`. Its latest combined functional HEAD before this documentation-only commit was `f21742da6ec7b015a3e7419b53b57e1b254f0e3e`; the full API30 and APK/JUnit of this final combined HEAD must pass before considering any stable branch integration. Do not claim they passed from older exact commits.
- #116 source forensic runtime and original Craft CTU16 + toggle integration merged to FBD at `779f91cb78b161e2b8f61c93f7db8e5c57d3c904`. Original signed/unsigned Java char rules preserved in versioned CTU16, reset-dominant RS, native trigger toggle. Tested APK/JUnit 39064462701 PASS and API30 39064462788 PASS, quick result artifact 12111979076, no crash.
- #118 original native 4-mode BLINK variant merged at `40b976a68741d0e1b14ef4b2357cfc54f139bce3`. Typed BOOL IN, NUMBER PT, BOOL Q; exact-int millis and MS/SEK/MIN property editing, transient source-matched phase state. Independent JUnit/APK 39065102000 PASS and API30 39065101999 PASS. Existing BLOODSKY TON/TOF/TP/CTU behavior preserved; original source TP bug/ambiguity intentionally not mass-ported.
- #117 extended picker/end-to-end native CTU16 and `T` UI regression merged at `5ed2efd0c85b935bfc4fc769f9d3ffbb83e55073`; run [39065308546](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39065308546) **PASS**, artifact `12112334233` inspected: API30, 188.60s within test, blocks inserted through real menu, project survives cold restart, zero crash logs. The failed first implementation of the UI checker accidentally included the dialog's independent buttonPanel ScrollView; fixed to target only scrollable item list.
- #119 gray source-style canvas for native CTU16, T, BLINK merged at `bdbcdc51c338df7c145be4c03e62ea2e39a51de6`; JUnit/APK [39065480555](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39065480555) **PASS** and quick emulator [39065480559](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39065480559) **PASS on retry 2**. First emulator attempt ADB TCP5554 connection refusal before app launch, unrelated to program code. Original source-like terminals and custom styling fallback tested; no unsupported claim of pixel-perfect parity.
- #120 **BLINK actual UI properties and restart acceptance** merged at `f21742da6ec7b015a3e7419b53b57e1b254f0e3e`. Exact-head JUnit/APK [39065849293](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39065849293) PASS and API30 [39065849327](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39065849327) **PASS**. Downloaded artifact `12113890146`, JSON result passed, 261.56s in test, validates native BLINK widget addition, exact `3456 ms` timer properties, unchanged BLINK mode across Apply, saved project cold restart, empty crash log. That screenshot came from PR120's **pre-#119 visual branch**, so expect purple legacy blocks there; #119's gray new skin is now included in the final combined feature branch. No same-screen integrated screenshot/pixel proof yet.
- New active branch has source-backed CTU16 (NUMBER-Q), T toggle, BLINK, old Craft-style AND/TON, tested typed Q/IN BOOL inversion and undo, 2345ms precision, original source pin facts, project persistence. Retained unsupported original palette preview disables, unproven TP source initialization, real physical PLC/Modbus testing, full original Craft styling acceptance. Merge only following final **exact latest head** main-targeting full UI run success; do not automatically publish release or stable `main`.
- All steps documented on [issue #75](https://github.com/skynet909084-boop/BLOODSKY-Android/issues/75) and these two handoffs. No paid agent or private CI minute spending; repo public by the user's explicit instruction.

## Authoritative October 18 checkpoint: public CI restored and native FBD evidence

- Repository is now PUBLIC at user request. Standard GitHub-hosted runner jobs restarted without paying for private Actions minutes. Stable `main` unchanged.
- Formerly blocked working PR #76 Actions have PASS after retry: pinned original audit [39060397585](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39060397585), Android APK/JUnit [39060397635](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39060397635), **full API30 emulator [39060397547](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39060397547)** (attempt 2).
- Fast FBD smoke passed actual Android API30 [39059963180](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39059963180), attempt 3, artifact 12112091131. Downloaded `result.json`: result passed, profile quick-fbd, 108.99 seconds *in-app test only*, FBD palette/AND/TON, long-press, 2345ms, BOOL inversion+undo, restart, no crash. Fast CI APK/JUnit [39059963201](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39059963201) PASS. PR #108 merged into FBD feature branch only at `58a3f4433b78e4f3b727ecca560c3bcc0d8882e2`. Main-targeting PRs and default manual dispatch still run full emulator smoke.
- Private fallback Windows self-hosted PR #109 closed after public Actions restored; branch/scripts and documentation retained but no local runner was registered or falsely marked green.
- Source-verified [PR #110](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/110): static CTU/RS/TIMER/TRIGGER and imported superclass inspection, pinned Craft zip hash 316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd. Source audit [39062491015](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39062491015) PASS; source evidence artifact 12111628647 inspected. CTU is separate rising-edge +/-/R unsigned (char) 16bit integer-Q, NOT BLOODSKY's existing threshold BOOL CTU. RS is reset-dominant, original trigger modes toggle/rise/fall. Timer mode/unit subtleties still require native source interpretation.
- Separate [PR #111](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/111) implements pure original-source CTU/RS/trigger transitions plus JUnit, intentionally NOT wired into the original palette or substituted for saved BLOODSKY CTU. The PR is stacked on #110 and is awaiting final exact-head Android tests. Exact hashes and line evidence in `docs/FBD_SOURCE_RUNTIME_FORENSICS_20261018.md`.
- After merging CI #108, wait for new exact merged-head full UI/APK. Then check #110/#111 final checks before merge to #76. Keep #76 draft and stable `main` untouched until actual source parity and hardware/visual gates are supported. Do not restart completed work.

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
