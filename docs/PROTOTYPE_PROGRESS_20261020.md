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


### Interim checkpoint for full Craft run #171

This historical checkpoint was superseded when run #171 finished. The final per-direction audit table and coverage limits are recorded under “Обновление статуса полного Craft UI probe #171 — финальный разбор” below. No active probe jobs remain.

PR [#69](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/69) remains a draft and unmerged. Its old-base checks do not validate the current prototype, and GitHub reported `mergeable=false` against the then-current base. Its generic inverter/ordinal pin model conflicts with the richer typed graph; the narrower failed-save rollback was adapted to current code in draft PR #163. Do not merge PR #69 wholesale.

Stable `main` remains `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.


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

- Existing sanitized CI fixtures cover `home.pro` (24 components), `gitH.pro` (65 components) and standalone `пож.pro` (124 components). PR #166 now runs these through ZIP import → atomic save/reopen → export → re-import, checking component/screen counts, Craft source records and background bytes. It uses a fixture image payload and does not prove the original archives' exact media mapping or full runtime bindings.
- The separately supplied `home2` and `дом 2025` archives remain outside this CI acceptance; `дом 2025` contains 346 component records. `пож.zip` has no media payload.
- Remaining acceptance: add sanitized immutable versions of `home2` and `дом 2025` with checksums and expected record/screen/connection counts; verify unknown fields, inactive legacy bindings, original media/backgrounds, device slots and runtime bindings around a deterministic single-field edit. Keep missing-media, size/path, rollback and CRC failure checks intact. Add a focused API30 UI restart pass for a representative original-project fixture.
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


## Обновление: приложенная декомпиляция Craft 1.0.7 — 20 Oct 2026

- Проверен приложенный пользователем Apktool/JADX source archive; SHA-256: `92d12206bde746f5a76c89e0c3311119287b14bb26cdc8ca36d515e2fca01f7a`. Файл остался в пользовательском scratch и не копировался в репозиторий.
- Независимый Smali cross-check: `smali/ru/himik/nightscream/kascada/mbCanvas.smali`, числовая фабрика `mbCanvas.m(...)`. Условия отдельно обрабатывают IDs 1 и 2; packed-switch покрывает 4–28; default для ID 3 поднимает существующие canvas views и возвращает `null`. Обнаружен и второй маршрут создания `private l(String,int,int,int)`: он выбирает palette-компоненты по строковым именам (`FB_TRIGGER`, `FB_XOR`, `MQTT_PUBLISHER`, `CustomGateway` и др.), создаёт соответствующие классы и сохраняет views в `MyApplication.D` (`SparseArray`). Это отдельный palette adapter, но он не связывает числовой ID 3 с классом. В двух DEX Smali-деревьях literal search нашёл все прямые `CustomComponents/...-><init>` места: вне самих компонентов они относятся к `mbCanvas`/его palette пути и helper-классам; поиски `Class.forName/newInstance`, `ClassLoader.loadClass`, `Constructor.newInstance` и `Method.invoke` не выявили отражённого создания компонентов. Это ограниченный поиск точных Smali opcode/reference patterns, не доказательство отсутствия произвольного obfuscated/indirect пути. Числовая фабрика остаётся fail-closed для ID 3.
- Ни в приложенных проектных примерах, ни в ранее изученных пользовательских примерах не найдено положительного соответствия ID 3. Числовая фабрика и строковая palette adapter проверены; поиск точных reflective class-construction patterns также завершён. Оставить ID 3 видимым неподдерживаемым/отключённым placeholder и не назначать тип по догадке. Осталось найти реальный Craft проект/каталог с таким ID либо подтверждение из другой версии приложения.

## Обновление: FBD rollback PR #163 — 20 Oct 2026

- Создан узкий draft PR [#163](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/163) в `prototype/all-in-one-20261020`, head `8f7b71339fea7dc65d1afc339e6febbeaa75df10`. Он переносит только атомарность сохранения connect/disconnect рёбер в модель текущего прототипа: неуспешная запись или исключение восстанавливает typed edge JSON и метаданные; undo snapshot добавляется только после успешного сохранения.
- Точный head прошёл APK/JUnit [#1162](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39392446480). API 30 smoke [#786](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39392008943) прошёл на runtime-identical commit `6568d9a92a6246f31d2548340956a8420b879db4`; последующие коммиты PR меняли только тесты. PR #163 принят и squash-слит в prototype как `91e9996b6fb4904ae2429b1d217604b8a32aa0e5`.
- PR #69 по-прежнему не вливать целиком: его старая модель рёбер и инвертора несовместима с более богатой текущей FBD-моделью. #163 — адаптированный узкий фикс, а не перенос PR #69.

## Обновление статуса полного Craft UI probe #171 — 20 Oct 2026

- Проверка run [#171](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39388319775) подтверждает head `0406c01c2440d65cd726967744a3d45a27eb04dc`. На момент записи завершены 3 из 10 направлений: FBD, exit-policy и macros; остальные 7 ещё в работе.
- Для FBD: 16/16 уникальных состояний, 115 переходов, 230 доступных long-press controls / 82 выполненных long presses, 1766.4 с, 0 stalled states. Для exit-policy: 15/15, 61, 35/2, 953.2 с, 0 stalls. Для macros: 5/5, 32, 29/3, 484.6 с, 0 stalls. Во всех трёх отчётах нет probe errors; прогоны остановились на depth/control/cycle boundary с `coverage_complete=false`. Это навигационные наблюдения, не доказательство полного UI-паритета.
- После завершения оставшихся семи направлений перепроверить каждый `ui-audit.json`, добавить все 10 строк и сохранить фактические ограничения покрытия. Главная ветка остаётся на зафиксированном SHA `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.



### Дополнение к source audit ID 3 — 20 Oct 2026

- В двух DEX-деревьях найдено 27 классов, реализующих `CustomComponents/z` и `getIdComponent()`. Их constructor defaults для возвращаемого ID соответствуют 1, 2 и 4–28; ни один default не задаёт ID 3. В нескольких классах соответствующее поле может позднее получить значение из parsed/passed data, поэтому это не раскрывает смысл сериализованного ID 3 и не заменяет поиск реального проекта/каталога с таким ID.


## Обновление статуса полного Craft UI probe #171 — финальный разбор

Run [#171](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39388319775) завершился с успехом для всех 10 jobs. Каждый артефакт указывает на один и тот же probe SHA `0406c01c2440d65cd726967744a3d45a27eb04dc`. В каждой строке ниже `unique states = captured states`, `probe_error=""`; однако все отчёты отмечают `coverage_limited=true` и `coverage_complete=false`.

| Направление | Состояния / уникальные | Переходы | Long-press controls / actions | Секунды | Stalls | Seed | Остановка |
|---|---:|---:|---:|---:|---:|---|---|
| FBD | 16 / 16 | 115 | 230 / 82 | 1766.4 | 0 | success (21.2s) | depth/control/cycle limit |
| Exit policy | 15 / 15 | 61 | 35 / 2 | 953.2 | 0 | не запускался | depth/control/cycle limit |
| Macros | 5 / 5 | 32 | 29 / 3 | 484.6 | 0 | success (26.6s) | depth/control/cycle limit |
| Runtime | 22 / 22 | 134 | 479 / 109 | 2839.3 | 0 | success (26.4s) | depth/control/cycle limit |
| Network | 22 / 22 | 151 | 478 / 134 | 3003.8 | 0 | success (22.0s) | runtime limit |
| Components | 36 / 36 | 177 | 263 / 34 | 3004.1 | 0 | success (22.2s) | runtime limit |
| Import QR | 26 / 26 | 141 | 426 / 118 | 3092.0 | 1 | не запускался | runtime limit |
| Devices | 22 / 22 | 136 | 403 / 97 | 3077.0 | 0 | success (26.0s) | runtime limit |
| Project create | 20 / 20 | 133 | 452 / 117 | 3055.8 | 0 | success (24.0s) | runtime limit |
| Import paste | 30 / 30 | 161 | 427 / 111 | 3090.8 | 1 | не запускался | runtime limit |

В таблице шесть направлений завершились по 3000-секундному runtime cap; фактическое elapsed может превышать его из-за teardown и записи отчёта. Четыре завершились на границе глубины/контролов/цикла. Import QR и import paste дали по одному stalled state. Успешный статус job означает успешное выполнение probe и загрузку артефакта, не завершение покрытия и не UI-паритет.

Разбор stall-снимков: оба флага относятся к краткому loading overlay на побочном пути создания проекта, а не к подтверждённой длительной блокировке. В Import QR `state-004` показывает меню проекта на PNG, несмотря на XML с `Загрузка проекта, подождите...`; следующий `state-005` после альтернативного CANCEL пути также уже содержит меню. В Import paste `state-009` показывает редактор/тост `The project default Created`, а следующий `state-010` — меню проекта. Логи не содержат исключений. Probe ставит флаг regex-проверкой в момент снимка; 90-секундный persistence wait работает только внутри `seed_project()`, а seed для этих двух направлений не запускался. Это transient loading-overlay observations, не evidence о persistent hang.


## P1 sanitized Craft ZIP acceptance — 20 Oct 2026

- PR [#164](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/164) добавил in-memory sanitized Craft ZIP через production import bundle, редактирование, atomic save/reopen, export и повторный import. Тест сравнивает сохранённое `craftSource`, проверяет vendor-поля, fail-closed placeholder для неизвестного ID 3 и исходные байты фонового media. PR слит в prototype как `822f4fb1a60872bf85c4f7077655fa1745185c09`; exact-head APK/JUnit [#1165](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39394794516) прошёл на `744a199f91d2a728dfc778cfbe349a067fc1b190`. UI smoke #791 пропущен path filter, так как менялись только тесты.
- PR [#165](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/165) добавил явную проверку сохранения обеих исходных Craft component rows на интегрированной базе; APK/JUnit [#1166](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39395670323) и Android 11 UI smoke [#794](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39395670292) прошли. PR слит как `b8c0c84860ecc6a5ecbc290d2d3199861e6420ad`.
- PR [#166](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/166) расширил ZIP round-trip на три обезличенных каталожных fixture `home`, `gitH`, `пож`: проверяются 24/65/124 component rows, 16/16/20 screens, исходный `craftSource`, явное изменение проекта и байты background media. Exact-head APK/JUnit [#1167](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39396050898) passed; PR слит в prototype как `2a6fda2b864dfeed437dd11e390672fe308394ce`. UI smoke #797 запущен отдельно и проверяется.
- Эти sanitized fixtures закрывают ZIP round-trip для трёх проектных JSON-примеров, но не полную приёмку отсутствующих `home2` и `дом 2025` архивов, runtime bindings или аппаратного поведения. Оригинальные пользовательские архивы не добавлялись в репозиторий.


## Финальная интеграционная проверка текущего prototype — 20 Oct 2026

- Последний code/test integration commit: `2a6fda2b864dfeed437dd11e390672fe308394ce`; этот progress-log checkpoint записан отдельным documentation-only commit. PR #163 FBD rollback, PR #164 sanitized ZIP acceptance, PR #165 интеграционная проверка и PR #166 round-trip трёх sanitized fixtures слиты только в `prototype/all-in-one-20261020`.
- Интегрированный PR #165 прошёл APK/JUnit #1166 и Android 11 UI smoke #794 на merge ref; PR #166 прошёл exact-head APK/JUnit #1167. UI smoke #797 для #166 выполняется отдельно после merge и не заменяет JUnit.
- PR #69 остаётся открытым и не слит: переносить целиком несовместимую старую FBD-модель нельзя. Узкий rollback уже реализован и принят через #163.
- Full Craft UI probe #171 остаётся только частичным навигационным evidence: все 10 jobs успешны, но все направления имеют `coverage_limited=true` и `coverage_complete=false`; полной UI-паритетности не заявлять.
- `main` не менялась и остаётся `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.


## Branch convergence review — 20 Oct 2026

- Confirmed current integration head: `prototype/all-in-one-20261020` at `cd9188fe757fdcd917b515bd1dcc3e2b5db6ac96`. Stable `main` remains exactly `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.
- There are 9 open PRs: #151, #150, #149, #74, #73, #69, #46, #44 and #38. They are not nine ready-to-merge feature batches; several are probe-only, stacked on older heads, or superseded by newer prototype implementations.
- #149–#151 are original-Craft emulator probe/tooling branches. Keep them separate from app runtime until their artifacts are accepted; they provide evidence, not application features.
- #69 is not safe to merge wholesale. Its ordered/untyped graph and ordinal pin/inversion model conflicts with the prototype's typed, named-pin graph. The current prototype already has source-aware FBD menus, timer units/modes and selected-edge edits; the narrow save-failure rollback was adapted and merged through #163.
- #44's older ZIP media service is superseded by the prototype's generation-based media service and newer ZIP acceptance coverage (#164–#166). #46's backup/rename transaction is based on the older service model and must not overwrite generation isolation without a separate demonstrated bug.
- #73 adds timer-boundary assertions already present in `FbdPropertyEditsTest`; importing it would duplicate existing coverage. #74 is cloud-agent tooling stacked on the old #69 branch, not an app-runtime feature.
- #38 is a legacy batch branch. Do not merge its old MainActivity wholesale; map any remaining unique feature against current code before porting.
- User direction: prioritize consolidating app implementation now. Testing the user's original project archives is deferred to final-app acceptance; do not add those raw archives to the public repository.
- Next integration rule: port only a clearly missing, bounded feature from a branch, or mark it superseded after confirming equivalent current code/tests. Run exact-head APK/JUnit and UI smoke for runtime/UI changes; leave `main` untouched.


### USB/RTU branch check — 20 Oct 2026

- Inspected the USB portion of legacy PR #38 against the current prototype. It manually opens the first attached USB device, selects the first interface and bulk endpoints, and sends a fixed single-register request. The dialog stores baud/parity/stop-bit settings, but this code does not apply them through a serial driver. The current app has no USB serial library or USB transport integration; `ModbusRtuClient` is only a protocol client over already-open byte streams.
- Do not merge the PR #38 USB code as-is or report USB RTU as implemented. The supported next implementation must use a serial driver, request Android USB permission, apply serial parameters, and connect the existing `ModbusRtuClient` to the opened port; device hardware acceptance remains a separate gate.
- User direction: defer original project archive testing to final-app acceptance and prioritize app implementation/convergence meanwhile.


## Continuation checkpoint — 20 Oct 2026, 14:26 Yekaterinburg

- Integration cleanup is recorded at `a58997c` on `prototype/all-in-one-20261020`; stable `main` remains `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.
- Removed five unused cloud-agent/DeepSeek workflows from the integration branch and closed superseded PRs #38, #44, #46, #69, #73 and #74. Open PRs #149–151 are retained as original-app evidence probes.
- A fresh inventory still shows 43 branch refs. They have not been deleted; keep them available as history until a supported ref-deletion operation is available. The integration line remains the only working app prototype.
- Reviewed the newly supplied decoded source archive (SHA-256 `92d12206bde746f5a76c89e0c3311119287b14bb26cdc8ca36d515e2fca01f7a`). It exposes Craft's USB serial call path and supported CDC ACM, CH34x, CP21xx, FTDI and Prolific drivers. Craft requests USB permission, opens a serial port, applies baud/data/stop/parity settings, then exchanges RTU frames.
- BLOODSKY still has a stream-level `ModbusRtuClient`, without USB host permission/driver ownership or a device lifecycle. Implement that adapter as the next focused code slice; do not transplant old PR #38 wholesale. Require fake-port tests, exact-head APK/JUnit and UI evidence before calling the runtime path integrated. Hardware acceptance remains separate.
- This checkpoint changed documentation/workflows only; no Android code changed and no new APK run was started.


## USB serial RTU implementation checkpoint — 20 Oct 2026

- Starting point confirmed at `prototype/all-in-one-20261020` SHA `663ec0fdf62ec1be6ec224b45cfc7ea15f82e7ec`; `main` remains pinned to `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.
- Local feature branch `feature/usb-serial-rtu-20261020` contains commit `e855961d93ee5d61890725d9f355dd22fd999902`. It adds the pinned upstream `usb-serial-for-android` 3.11.0 dependency and MIT attribution, optional USB-host feature declaration, validated serial framing settings, an explicit-device USB permission/open/detach lifecycle, a serial-port adapter around `ModbusRtuClient`, and fake-port JVM tests for RTU request/response, UART mapping, timeout/error and close behavior.
- The connector is an API slice only: it exposes device enumeration and cancellable connection creation, but no editor UI or project-binding integration is enabled. Imported USB bindings remain inactive. RTU read support remains FC01–FC04; write functions and physical adapter behavior are not implemented/verified by this change.
- Validation attempted locally: `git diff --check` passed. Python discovery ran 164 tests and reported 4 failures / 26 errors tied to missing generated Craft reference files and removed workflow/resource expectations; the failing cases did not target the new Java code. Android unit tests/APK build could not run because this environment has no Gradle executable, Gradle wrapper, Java compiler, or configured Android SDK.
- Exact-head GitHub validation is pending publication of the feature branch. No change was made to the remote prototype or stable `main` in this checkpoint.

- Draft PR [#167](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/167) targets the prototype. Initial APK/JUnit run [#1168](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39403487628) exposed stale Python checks that still required intentionally removed cloud-agent workflows. Updated those checks to assert the automation stays disabled and labeled the old pilot document as paused/history. Exact-head run [#1170](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39404204508) passed the full Python suite, Android unit tests, and debug APK build. Its compiler caught an inner-class constructor reference error in `UsbSerialConnector`; moved connection opening to the outer connector and kept cancellation/resource cleanup explicit.
- Exact-head Android 11 UI smoke [#801](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39404400404) passed its quick FBD regression profile. The earlier smoke build failure [#798](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39403487707) is superseded by this passing run. No physical USB serial adapter was available, so adapter communication and unplug/reconnect remain unverified hardware gates.

### Final status

- PR [#167](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/167) was merged into `prototype/all-in-one-20261020` as merge commit `c34ab0811460c244d648f58c56ddbfd93d6c0379`. The prototype now includes the USB serial API slice; stable `main` remains pinned to `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.
- A documentation-only follow-up restored the complete progress log after the prior long-file transfer was truncated and recorded this final status. No application code changed after the successful exact-head build and smoke run.


## USB Modbus RTU writes — 20 Oct 2026

- PR [#168](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/168) adds FC05/FC06 single coil/register writes, FC0F/FC10 batch writes, and FC22 mask writes to `ModbusRtuClient`, then exposes them through the USB session/connection. RTU replies must pass CRC and exact acknowledgement checks; the transaction keeps the existing bounded deadline.
- Exact-head APK/JUnit [#1175](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39406473632) and Android 11 UI smoke [#808](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39406651996) passed. The smoke is the existing quick FBD regression, not a USB hardware test.
- PR #168 was merged into `prototype/all-in-one-20261020` as `e337bd86e9cb4af4480d34815abea1c500e55338`. Stable `main` remains `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`. Project bindings/UI and physical adapter acceptance remain open.


## Manual USB RTU screen — 20 Oct 2026

- Added a home-menu entry for a separate USB serial Modbus RTU screen. The operator refreshes supported devices, chooses one adapter, selects baud/parity/stop bits, and explicitly connects; Android USB permission is requested for that selection.
- Added manual FC03/FC04 register reads with unit/address/quantity validation and visible results, plus disconnect. No project widget binding is activated, and this screen does not issue writes.
- Back to home, explicit disconnect, and Activity destruction close the owned session. A generation guard closes a late successful connect if the operator has already left the screen.
- Python suite: 164 tests passed after preparing the pinned Craft reference resources. Local Android Gradle tests/build were not available (no wrapper or installed Gradle); exact-head GitHub Actions APK/JUnit and Android UI smoke are pending.
- No physical USB adapter or visual parity comparison against Craft was performed. Stable `main` remains pinned at `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.


## Manual USB RTU screen accepted — 20 Oct 2026

- PR [#169](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/169) merged to `prototype/all-in-one-20261020` as `d42f24aa30f90454bb912cb23c1e7005da593df8`. It adds an explicit manual USB serial Modbus RTU screen for device selection, line settings, connect/disconnect, and FC03/FC04 reads. Imported project bindings remain inactive; no writes are issued by this UI.
- Exact-head APK/JUnit [#1176](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39407984324) passed Python checks, Android unit tests, and debug APK build. Existing Android 11 quick UI smoke [#810](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39407984254) passed; that profile did not navigate to the USB screen.
- PR [#170](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/170) then added a hardware-independent USB screen navigation check to the quick UI profile. It merged as `2a63e73ce8d0306f12b604df0be8fbac3a9e183f`; exact-head APK/JUnit [#1177](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39408807345) and Android 11 smoke [#811](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39408807324) both passed. The smoke opened the screen, scrolled to Connect, captured it, and returned home.
- No USB permission grant, serial transaction, physical adapter, or original-app visual parity was tested. Stable `main` remains pinned at `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.


## Craft sensor block source drawings — 20 Oct 2026

- Added default source-backed canvas rendering for `FBD CRAFT SENSOR ORIENTATION` and `FBD CRAFT SUNRISE`, based on original `Sensor_Orientation.onDraw/onMeasure`, `Sunrise_GPS.onDraw/onMeasure`, and `OriginalFbdDrawing.draw11`.
- The caption follows imported axis (`ORIENTATION_X/Y/Z`) and sunrise mode (`SR/SS`). Source-shaped gray body, black outline, and centered Q output are retained; a user-edited caption still uses the editable BLOODSKY appearance.
- Added JUnit regressions for type coverage, dynamic labels, and custom-caption fallback. Updated the source matrix; the rows remain partial visual parity because there is no same-density side-by-side comparison.
- Local Python gate passed: 164 tests. `gradle testDebugUnitTest --stacktrace` and `gradle assembleDebug --stacktrace` were attempted but could not run because Gradle and a wrapper are absent. Exact-head Android CI is required for validation.
- Stable `main` remains pinned at `b11fbb9b9bdb35cacbcd466d57d414071d94aa5e`.


## Craft light and speed source drawings — 20 Oct 2026

- Extended the same original `draw11` default source skin to `FBD CRAFT SENSOR LIGHT` (`LM`) and `FBD CRAFT SENSOR SPEED` (`SM`). Both expose only the original centered `Q` output and no inputs.
- Added type/default-caption/pin regression assertions and source matrix rows. No project fields or runtime behavior changed.
- Validation and exact-head CI are pending; visual parity remains partial until same-density comparison.
