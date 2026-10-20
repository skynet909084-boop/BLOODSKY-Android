# BLOODSKY unified prototype — progress checkpoint (2026-10-20)

## Unified target

- Working integration branch: `prototype/all-in-one-20261020`
- Stable `main` was not changed.
- Current prototype head before this checkpoint: `d02bcaf86eafd6435af26d896dfa6939ff5c0714`
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
