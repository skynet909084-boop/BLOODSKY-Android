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
