# BLOODSKY unified prototype — progress checkpoint (2026-10-20)

## Unified target

- Working integration branch: `prototype/all-in-one-20261020`
- Stable `main` was not changed.
- Current prototype code head before the source-view audit checkpoint: `3dbe9cb6e4e9ef3c2e8706595aa49663bb81f1d6`
- The unified app already contains the current project importer, FBD work, component imports, HMI/runtime changes and media handling described in `KASCADA_FULL_PARITY_AUDIT_20261018.md`.

## Added in this checkpoint

PR [#158](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/158) was merged into the prototype as `d02bcaf`. It adds focused Modbus RTU stream-helper regressions for fragmented reads, request bytes, Unit ID/function/byte-count checks, CRC errors, exception frames, truncated responses and invalid address ranges. It changes tests only; it does not add a live USB, Bluetooth or serial RTU transport.

Exact-head validation passed:
- Android APK/JUnit run [#1150](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39377234697)
- Android UI smoke run [#780](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39377234782)

## Branch cleanup

Removed 22 unprotected branch refs after checking ancestry, duplicate SHAs and open PR dependencies:
- 19 refs whose commits were already contained in the prototype
- 2 exact duplicate refs
- the temporary head branch for merged PR #158

Unique branches that contain unmerged work and branches used by open PRs remain. The current inventory has 48 branches. Closed PRs and deleted refs do not imply that every historical task is complete; remaining open PRs still need their own acceptance gates.

## Craft 1.0.7 parity gates still open

The detailed source-backed status is in `KASCADA_FULL_PARITY_AUDIT_20261018.md`. Do not claim full parity until these are accepted:

1. Side-by-side, equal-density visual checks for each component and editor screen; Craft component ID 3 remains unresolved.
2. FBD source/runtime acceptance for CTU modes `+`, `-`, `R`; RS input order; timers and BLINK; triggers; INT/FLOAT/NaN/overflow behavior; and opaque original macro child records.
3. Import → edit → save → export → reopen acceptance on all supplied original archives, including media, screens/backgrounds, bindings, FBD, networks and device slots.
4. Real USB/Bluetooth/serial RTU acceptance. The RTU helper tests above are not transport or device proof.
5. Remaining device manager, component settings, local services and external integrations listed in the audit.

The original Craft emulator probe work remains in open PRs #149–#151. Keep those branches until the probe runs and artifacts are reviewed; their captured navigation coverage is not a substitute for visual, project round-trip or hardware acceptance.

## Continuation update — 20 Oct 2026

- Merged PR [#159](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/159) into the prototype at `ab9d11c03b0fbf6cab532317793f390438b0b68e`. It carries the pinned, read-only Craft FBD evidence scanner expansion, synthetic ZIP regression tests and math-source audit note.
- Exact-head source-evidence run [#79](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39379106977) passed. Android APK/JUnit run [#1151](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39379106927) passed. No UI smoke was needed for this PR because it changes no runtime or UI behavior.
- PRs #134 and #138 were closed as superseded after their source findings and tools were consolidated. Their old PR branches and temporary PR heads were removed.
- Branch inventory after cleanup: 46 branches; 11 open PRs. Unfinished branches and branches referenced by open PRs were retained.
- Stable `main` remains at `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.

The source findings and extraction tests do not implement INT/FLOAT arithmetic, comparison, CHANGE blocks or conversions in the app. Those remain disabled until the source-backed behavior is implemented and passes the prototype's exact-head runtime and UI acceptance gates.

## Craft emulator follow-up — 20 Oct 2026

- The quick long-press probe run [#52](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39237938241) passed as a workflow, but its FBD direction ended at `state_limit`: 25 captured states, 24 transitions and `coverage_complete=false`. Treat that artifact as partial evidence.
- Dispatched the exhaustive long-press matrix [#165](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39379411369) from `test/original-craft-long-press-20261019`.
- Dispatched the exhaustive traversal with stable loading-state detection [#166](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39379432124) from `fix/original-emulator-loading-detection-20261019`.
- Both full runs were in progress at this update. Review per-direction `ui-audit.json` and artifact coverage before treating any direction as complete.


## Source-view audit and picker follow-up — 20 Oct 2026

- Merged PR [#161](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/161) into the prototype as `3dbe9cb6e4e9ef3c2e8706595aa49663bb81f1d6`. It clarifies the source-backed unresolved Craft component ID 3 factory mapping in the existing matrix; no runtime code changed.
- Ported the MediaStore/document-picker hardening from #136 into PR [#160](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/160), preserving newer prototype smoke coverage. It scans and waits for the fixture, resolves exact grid/list filename nodes, requires app return, adds regressions, and selects the full UI profile for this branch family.
- PR #160 exact-head `309f159a7c45c7a79ebfa285ece23f32b58d8858`: APK/JUnit run [#1152](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39379640455) passed; full Android UI run [#782](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39379640479) is exercising Android 11. Keep #136 until these exact-head checks pass; then close it as superseded and remove its branch.
- Removed the merged #161 branch and the old source-view audit branch after verifying no open PR dependency and confirming all their changed file blobs match the prototype. Branch refs were rechecked absent. Inventory: 46 branches; 12 open PRs. Unique unfinished work remains.
- The duplicate full Craft run #166 was cancelled after confirming #165 is based on the same loading-detection branch and covers the same ten directions plus long-press. Run #165 remains active; review each direction artifact and `ui-audit.json` before claiming coverage.
- Stable `main` remains `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.

- Audited the leftover `codex/rtu-response-regressions-20261019` branch: its RTU test content already matches the prototype. PR [#162](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/162) adds a regression assertion for the observed timer dialog title. APK/JUnit [#1153](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39380654628) passed. UI smoke [#783](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39380654662) correctly exposed that the actual editor header is `Свойства: FBD Таймер`; the attempted `FBD CRAFT TIMER` label was wrong and has been reverted. Corrected UI run [#784](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39381333825) is running on head `e5ee239f71d4679118c1e29de90e6b7b6d176120`.
- Current inventory after this cleanup: 44 branches, 12 open PRs. Keep the leftover RTU branch until #162 passes, then remove it after verifying its content is fully represented in the prototype.


## Legacy media consolidation — 20 Oct 2026

- Verified that the prototype already contains the tested `LegacyProjectMediaMigrator`, its JUnit suite, the fail-closed open flow, the PNG pixel probe, and the API30 UI scenario that checks the migrated image before and after process restart. Core migrator and test files match the validated PR #93 tree; the prototype's surrounding MainActivity and media service are newer.
- Closed draft PR [#93](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/93) without merging it into `main`; its implementation was already consolidated in the prototype and its exact-head APK/JUnit + UI checks had passed.
- Removed the superseded refs `fix/project-legacy-media-migration`, `fix/project-migration-referenced-media-missing`, and `test/project-legacy-media-android-e2e` after confirming #94 was merged and #95/#96 closed, then verified all three refs absent.
- Current branch inventory: 44 branches; 12 open PRs. Stable `main` remains `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.
- Latest checks: #160 APK/JUnit passed and full UI is running; #162 APK/JUnit and UI are running; Craft #165's ten UI directions are still running. Redundant run #166 is cancelled.

## FBD integration follow-up

- Retargeted draft PR [#69](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/69) from `main` to `prototype/all-in-one-20261020` so its source drawing/editor transaction work is evaluated against the unified app. Its exact head `f8f167c78190c06bcb7b726ec613e6dce02e2402` previously passed APK/JUnit run [#396](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39016734417) and Android 11 smoke [#64](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39016734410) against its old `main` base.
- After retarget, GitHub reports `mergeable=false`; no PR #69 code was merged. Its MainActivity/editor changes need a deliberate integration pass against the newer prototype, followed by exact-head APK/JUnit and UI checks. Keep the branch and draft open; `main` stays unchanged.


## Continuation update — 20 Oct 2026 (later checkpoint)

- PR [#160](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/160) merged into the unified prototype as `b1801047402345af02667e1e7f6017b68de2fe67`. The current synchronized branch tree matches the code tested by full Android 11 UI smoke [#782](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39379640479); the later base sync adds documentation changes. APK/JUnit on synchronized head `dd350b96559874e338249742acac0557e9311bc7`, run [#1155](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39381758009), passed.
- Superseded PR [#136](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/136) was closed unmerged; its old branch was deleted after the validated prototype replacement landed.
- PR [#162](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/162) merged as `efc4a057114b414744391a2989d8b1c657dd4fd1`. It adds a one-line regression check for Craft's localized FBD timer palette/property labels; APK/JUnit run [#1156](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39381967912) passed, and corrected Android smoke [#784](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39381333825) passed before a base-only sync. Artifact [#783](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39380654662) established the observed property header is `Свойства: FBD Таймер`; the serialized type is `FBD CRAFT TIMER`.
- Deleted the merged #160 and #162 feature refs and the old RTU regression ref after checking that its `ModbusRtuTest.java` blob matched the prototype and its remaining differences were the corrected timer smoke label/test. Deleted refs were verified absent.
- Full Craft run [#165](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39379411369) completed successfully at workflow level across all ten directions. All ten artifacts were reviewed. Each ran offline, seeded its project successfully, captured 100 states / 99 transitions, and ended at `state_limit` with `coverage_limited=true` and `coverage_complete=false`; none hit the 3000-second runtime limit. Long-press actions executed: FBD 19, components 9, runtime 14, exit-policy 2, project-create 14, macros 9, import-paste 2, devices 12, network 56, import-qr 14. Only import-qr recorded one project-loading stall. Treat this as broad partial navigation evidence, not complete coverage.

| Craft direction | States / transitions | Long presses | Seconds | Stalls | Result |
|---|---:|---:|---:|---:|---|
| FBD | 100 / 99 | 19 | 1529.3 | 0 | state limit |
| Components | 100 / 99 | 9 | 1833.5 | 0 | state limit |
| Runtime | 100 / 99 | 14 | 1402.0 | 0 | state limit |
| Exit policy | 100 / 99 | 2 | 1685.5 | 0 | state limit |
| Project create | 100 / 99 | 14 | 1468.4 | 0 | state limit |
| Macros | 100 / 99 | 9 | 1352.6 | 0 | state limit |
| Import paste | 100 / 99 | 2 | 1539.7 | 0 | state limit |
| Devices | 100 / 99 | 12 | 1332.1 | 0 | state limit |
| Network | 100 / 99 | 56 | 1816.2 | 0 | state limit |
| Import QR | 100 / 99 | 14 | 1747.1 | 1 | state limit |
- Latest branch inventory after this cleanup: 31 branches and 9 open PRs. Stable `main` remains `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.

## Next integration queue

- PR [#69](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/69) remains intentionally unmerged. Its ordered/untyped graph drawing and transaction work must be decomposed against the richer prototype FBD implementation before selective porting; rerun APK/JUnit and Android UI smoke on each accepted port.
- Continue reviewing the remaining Craft 1.0.7 direction artifacts, then raise the FBD probe cap/termination strategy where evidence shows meaningful unexplored states. Keep the original emulator branch until the audit is complete.
- Stable `main` remains unchanged.


## Craft probe state de-duplication — 20 Oct 2026

- Reviewing all ten artifacts from full run [#165](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39379411369) exposed a probe defect: `capture_state` checked whether a signature was already in `seen`, but never inserted newly captured signatures. Repeated visible screens therefore consumed the 100-state budget. The #165 metrics above remain valid as partial evidence, but they are not a reliable count of unique UI states.
- PR [#151](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/151), head `d4b9af4d7821675b0d1d0afdb652f717dcae26f6`, now records unique signatures before accepting a state, removes duplicate XML captures, and adds a unit regression for first/duplicate/new signatures.
- Exact-head Android APK/JUnit run [#1157](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39384363621) passed. Ten-direction quick emulator run [#167](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39384363374) is running; manual full-profile retest [#168](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39384420617) is also running on the same head. Neither retest has been accepted yet; inspect each artifact's unique-state count and coverage flags after completion.
- Deleted temporary branch `fix/craft-probe-state-dedup-20261020` after its changes were ported to #151. Rechecked that the temporary ref, merged timer/RTU refs, and superseded RTU ref are absent. #151/#150/#149 remain open because their evidence and acceptance gates are unfinished.
- Stable `main` remains `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.


## Full branch inventory correction — 20 Oct 2026

- A fresh GitHub inventory returned 40 refs, so the earlier 31-branch count in this log was stale. After the verified deletion below, the current inventory is 39 refs, including `main` and the prototype; the open-PR count remains 9.
- Compared the full inventory against `prototype/all-in-one-20261020` and checked open PR heads/bases. Deleted `feat/fbd-75-pin-semantics`: its head was 621 commits behind the prototype with no branch-only commits and no open-PR dependency. Rechecked the ref after deletion; it is absent.
- Retained `feat/craft-screen-navigation-import-20261018` even though its head is 53 commits behind the prototype, because it remains the base of open PR #149. All other non-PR-head branches still have branch-only commits; spot checks of 13 changed-file sets found no file-blob-complete duplicate suitable for cleanup. Open PR heads and their base branches remain intact.
- Stable `main` remains `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.


### Quick profile check after de-duplication — run #167 (9/10 directions complete)

All nine available `ui-audit.json` files had `unique_state_signatures == states_captured`, no probe error, and terminated at the depth/control/cycle boundary rather than the state cap. Each reported `coverage_limited=true`, `coverage_complete=false`, and one project-loading-stalled state; this is a helper regression check, not a complete Craft coverage claim. The network artifact is still pending.

| Direction | States / unique | Transitions | Long presses | Seconds | Loading stalls |
|---|---:|---:|---:|---:|---:|
| FBD | 5 / 5 | 19 | 3 | 248.8 | 1 |
| Components | 10 / 10 | 32 | 15 | 484.1 | 1 |
| Runtime | 5 / 5 | 19 | 2 | 221.6 | 1 |
| Exit policy | 12 / 12 | 28 | 7 | 386.3 | 1 |
| Project create | 7 / 7 | 22 | 6 | 318.8 | 1 |
| Macros | 11 / 11 | 26 | 7 | 368.4 | 1 |
| Import paste | 10 / 10 | 29 | 3 | 320.2 | 1 |
| Devices | 11 / 11 | 26 | 7 | 357.8 | 1 |
| Import QR | 8 / 8 | 26 | 7 | 333.7 | 1 |


### Probe signature audit follow-up

- A further code review found the first de-dup implementation keyed visible text/geometry but omitted interaction attributes. PR #151 now includes package, hint, enabled/clickable/long-clickable, focus, checked/selected, scrollable, and related accessibility fields in each node signature; a unit regression verifies that changing `long-clickable` or `enabled` creates a distinct signature.
- Quick artifacts from superseded head `d4b9af4d7821675b0d1d0afdb652f717dcae26f6` (run #167) and the partial full run #168 are historical evidence only. They were cancelled after the signature was strengthened; the provisional table above must not be used as final acceptance evidence.
- Latest probe head: `0406c01c2440d65cd726967744a3d45a27eb04dc`. Exact-head APK/JUnit run [#1159](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39386327606) passed. Fresh quick probe [#170](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39386327522) passed all ten directions; artifact-level results and the full-profile follow-up are recorded below.


### Exact-head quick probe results and full-profile follow-up — 20 Oct 2026

- All 10 directions on head `0406c01c2440d65cd726967744a3d45a27eb04dc` completed successfully in quick run [#170](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39386327522); the exact-head APK/JUnit run [#1159](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39386327606) also passed.
- All ten `ui-audit.json` files report unique signatures equal to captured states, no probe error, and zero or one project-loading stall. Every quick run stopped at the depth/control/cycle boundary with `coverage_limited=true` and `coverage_complete=false`. This confirms the dedup/signature fix and probe execution; it does not establish complete Craft UI coverage.

| Direction | States / unique | Transitions | Long-press controls | Long presses | Seconds | Loading stalls |
|---|---:|---:|---:|---:|---:|---:|
| Import paste | 18 / 18 | 46 | 12 | 3 | 630.5 | 0 |
| Project create | 10 / 10 | 31 | 32 | 2 | 492.1 | 1 |
| Import QR | 11 / 11 | 34 | 33 | 2 | 534.6 | 1 |
| Exit policy | 13 / 13 | 36 | 33 | 2 | 537.7 | 1 |
| FBD | 9 / 9 | 32 | 6 | 3 | 449.3 | 1 |
| Runtime | 11 / 11 | 34 | 33 | 2 | 502.6 | 1 |
| Macros | 9 / 9 | 22 | 8 | 3 | 322.4 | 0 |
| Components | 11 / 11 | 28 | 143 | 8 | 383.5 | 0 |
| Network | 11 / 11 | 34 | 33 | 2 | 524.9 | 1 |
| Devices | 8 / 8 | 24 | 31 | 3 | 322.6 | 1 |

- Dispatched full-profile run [#171](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39388319775) against the same exact head; it is in progress. Its artifacts must be reviewed per direction before accepting any coverage claim.
- Stable `main` remains `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.


### Full-profile Craft run #171 — active checkpoint

- Run [#171](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39388319775) is running on probe head `0406c01c2440d65cd726967744a3d45a27eb04dc`. The latest check shows 9 of 10 direction jobs in progress and `macros` completed successfully; the other artifacts are not published yet.
- Reviewed the available `macros` artifact: 5 captured / 5 unique states, 32 transitions, 29 long-pressable controls, 3 long presses, no loading stall and no probe error. It ended at `depth_control_or_cycle_limit`, with `coverage_limited=true` and `coverage_complete=false`. This is partial navigation evidence only.
- PR [#69](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/69) remains a draft and unmerged. Its exact head `f8f167c78190c06bcb7b726ec613e6dce02e2402` passed APK/JUnit [#396](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39016734417) and UI smoke [#64](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39016734410) on its old base. After retargeting, current PR metadata reports `mergeable=false`; its five commits are based at `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`, while the current PR base is `63bffdd0c2a966c6dbc059b14fee508af3c44fa6`. Do not merge. Decompose and port selected functionality against the current model, then run exact-head APK/JUnit and UI smoke.
- Stable `main` remains `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.


#### #171 artifact update — exit-policy

| Direction | States / unique | Transitions | Long-press controls | Long presses | Seconds | Loading stalls | Coverage |
|---|---:|---:|---:|---:|---:|---:|---|
| Exit policy | 15 / 15 | 61 | 35 | 2 | 953.2 | 0 | limited; depth/control/cycle boundary |

The artifact completed without probe errors or runtime-cap hit. Its project seed was not attempted, appropriate to this exit-policy path. `coverage_complete=false`; this is not full Craft coverage. The other eight directions remain in progress.


## Portable project media review — 20 Oct 2026

- Reviewed open PRs [#38](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/38) and [#44](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/44). Their base chains are legacy branches; do not merge them wholesale into the unified prototype.
- The prototype already contains `ProjectMediaService` and wires it into the UI paths: `MainActivity` resolves media for runtime playback/read, calls `importProjectBundle` on project import, and `exportProjectZipBundle` for ZIP export. The service currently uses stable project identity, per-import media generations, bounded reads, media resolution and staged publication/rollback. Its tests include basename collisions and metadata/background preservation, rejection/rollback cases, sources-deleted image/audio resolution followed by re-export, traversal rejection and generation replacement.
- This supersedes the older service/test payload in PR #44; preserve the open legacy refs for now, but do not duplicate or merge them. The actual original-project acceptance remains open: import → edit → save → export → reopen for each supplied archive, including screens, background/media, bindings, FBD, network and device-slot records. Existing synthetic media tests are not that acceptance.


## Craft component ID 3 source follow-up — 20 Oct 2026

- Decompiled-source/Smali follow-up confirms the `mbCanvas.m(...)` factory explicitly handles IDs 1 and 2, then IDs 4–28; ID 3 falls through to its default branch, brings the existing canvases forward, and returns `null`. Reload, import and paste/copy call sites use this factory, so this path has no visible component mapping for ID 3.
- Enumerated concrete `CustomComponents` classes expose IDs 1, 2 and 4–28; the helper class with ID 14 is a chart helper, not evidence for component ID 3. This still does **not** prove ID 3 is absent app-wide.
- Keep unsupported ID 3 import fail-closed; do not invent a class or run behavior. Remaining source work: search all other component registries/factories, palette/resource adapters and original `.pro` samples for positive ID 3 evidence. Only a supported class+fields+behavior mapping should justify implementation.


### ID 3 search in supplied project samples — 20 Oct 2026

- Parsed the Library-provided projects `home2.pro`, `gitH.pro`, `пож.pro`, and `дом 2025.pro` embedded in its ZIP. Counts: 65, 65, 124 and 346 `TableComponent` records respectively; none contains a record with `componentId=3`.
- `home2.zip` and `gitH.zip` each contain the paired `.pro` plus one image; their `.pro` files differ. `пож.zip` contains only an empty directory entry, while its separate `.pro` is the 124-component source. The 346-component `дом 2025.pro` is embedded in `дом 2025.zip`.
- These examples provide no positive mapping for ID 3; absence in these projects does not resolve the app-wide mapping question. Keep it fail-closed and continue registry/factory/resource search.


## Original project acceptance gap — 20 Oct 2026

- Existing sanitized CI fixtures cover `home.pro` (24 components), `gitH.pro` (65 components) and standalone `пож.pro` (124 components). They check import/save/reopen, but do not establish ZIP media round-trip or full runtime bindings.
- Newly supplied `home2` and `дом 2025` archives are not yet represented in that CI acceptance; `дом 2025` contains 346 component records. `пож.zip` has no media payload.
- Next acceptance package: add sanitized immutable fixtures with checksums and expected record/screen/connection counts; perform import → deterministic single-field edit → save → ZIP export → re-import → reopen; assert component/connection metadata, unknown fields, inactive legacy bindings, media bytes/backgrounds, screen counts and device slots are preserved except for the explicit edit. Keep missing-media, size/path, rollback and CRC failure checks intact. Add a focused API30 UI restart pass for a representative fixture.
- Do not claim original-project parity until these fixture-based gates pass. No raw Library archive has been copied into the repository.


### FBD PR #69 selective-port review — 20 Oct 2026

- Current prototype FBD is substantially richer than PR #69's original ordered/untyped graph: it retains named typed pins and unknown edge metadata; validates pin compatibility, occupancy, duplicate endpoints and cycles; renders a broader Craft block set; and already has source-aware timer modes, units and BLINK behavior. The prototype also has its own `invertOut`/`craftTimerUnit` schema. Do not port PR #69's generic boolean inverter, `bloodskyFbdInvertOut`, ordinal pin layout, old timer schema or remove-all-outgoing-edge behavior; those conflict with current semantics or lose edge-selection precision.
- A narrow gap remains: current FBD connect and selected-edge disconnect mutate the graph and call `persistProject()`, which ignores the boolean returned by `persistProjectNow(false)`. Existing output inversion already performs a rollback. A future fix should snapshot only the typed edge graph and project metadata, use synchronous persistence result, restore safely on failure, and keep undo/redo history coherent. Add injected-save-failure tests for connect and disconnect, then run exact-head APK/JUnit and API30 UI smoke.
- PR #69 remains open, draft and `mergeable=false`; its old-base checks are not evidence for the current prototype. Do not merge wholesale.


#### Source archive manifest for future fixtures

| Source project | Components | Used screen IDs (component counts) | Declared screens | `TableConnections` records | Device slots |
|---|---:|---|---:|---:|---:|
| `home2.pro` and paired ZIP | 65 | 1 (53), 2 (7), 3 (5) | 16 | 3 | 16 |
| `gitH.pro` and paired ZIP | 65 | 1 (53), 2 (7), 3 (5) | 16 | 3 | 16 |
| `пож.pro` (ZIP has no project/media payload) | 124 | 1 (21), 20 (103) | 16 | 1 | 16 |
| `дом 2025.pro` embedded in ZIP | 346 | 1 (87), 3 (188), 4 (71) | 16 | 2 | 16 |

These are record counts only; they do not assert the supported runtime semantics of each device/component or prove archive round-trip acceptance.
