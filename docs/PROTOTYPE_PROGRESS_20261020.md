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

- Audited the leftover `codex/rtu-response-regressions-20261019` branch: its RTU test content already matches the prototype, but the quick FBD smoke retained the wrong timer property header. Ported the exact Craft title correction and a regression assertion in PR [#162](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/162). Exact-head APK/JUnit [#1153](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39380654628) and quick UI smoke [#783](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39380654662) are queued.
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
