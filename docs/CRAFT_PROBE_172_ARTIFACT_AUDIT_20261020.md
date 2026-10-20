# Craft 1.0.7 UI probe #172 — verified artifact audit

Date: 2026-10-20. Source run: [Original Craft emulator probe #172](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39429941170), head `627b29aa5a8905e98480e76a71cd5e47ecee8e04`.

## Evidence method

Downloaded and opened **all ten** workflow artifact ZIPs. Examined each `ui-audit.json` plus captured state/control labels, with screenshots/XML present in the archives. This is a navigation evidence inventory, **not** proof that the corresponding editor/FBD/device workflow was exercised correctly. A successful GitHub Actions run means the probe process succeeded, not that the user interface has complete coverage.

| Direction | Artifact ID | Profile | Seeded project | Unique states | Transitions | Long presses | Stop reason | Coverage complete |
|---|---:|---|---|---:|---:|---:|---|---|
| FBD | 12271497650 | full | yes | 22 | 142 | 125 | runtime_limit (3096.1 s) | no |
| Components | 12267837961 | quick | not attempted | 10 | 22 | 3 | depth_control_or_cycle_limit | no |
| Runtime | 12267648245 | quick | not attempted | 11 | 34 | 2 | depth_control_or_cycle_limit | no |
| Exit policy | 12268546067 | quick | not attempted | 13 | 36 | 2 | depth_control_or_cycle_limit | no |
| Project create | 12267334519 | quick | not attempted | 7 | 29 | 2 | depth_control_or_cycle_limit | no |
| Macros | 12267064060 | quick | not attempted | 8 | 24 | 3 | depth_control_or_cycle_limit | no |
| Import paste | 12267494907 | quick | not attempted | 17 | 46 | 3 | depth_control_or_cycle_limit | no |
| Devices | 12268320993 | quick | not attempted | 8 | 24 | 3 | depth_control_or_cycle_limit | no |
| Network | 12267733252 | quick | not attempted | 12 | 32 | 2 | depth_control_or_cycle_limit | no |
| Import QR | 12268267059 | quick | not attempted | 11 | 34 | 2 | depth_control_or_cycle_limit | no |

All ten report `execution_finished=true`, `coverage_limited=true`, `coverage_complete=false`, and an empty `probe_error`. Only FBD reports `runtime_limit_hit=true`. Loading-stall counters: devices, exit-policy, import-QR, macros, runtime each 1; other directions 0. **No direction should be accepted as exhaustive from this run.**

## What the UI evidence actually shows

- **FBD (full):** seeded a project, reached the editor, the component chooser and a license/purchase dialog. Many captured states revisit the same top-level chooser with slightly different enabled/control sets. The captured screen labels do **not** establish that all factory block properties, pins, timer modes, CTU/RS semantics, macro editing, save/reopen or runtime execution were explored. License dialog reports `UNKNOWN_ERROR` offline and must not be bypassed.
- **Components (quick):** reached project creation, editor and element chooser (Button/List/Text/Dyn. text/GIF/Background), plus screen selection. This is useful initial navigation proof, not all ID 1–28 settings.
- **Import paste (quick):** reached the Paste link / Scan QR / Import dialog, but not a successful full native ZIP/PRO round trip.
- **Other quick directions:** predominantly explore project creation, home, screen switching, the element chooser, or the rename dialog; recorded states do not prove targeted macros/device transports/network settings or runtime transitions. The quick profiles intentionally limit depth/actions.
- **Project seeding:** only the FBD direction reports successful explicit seed; nine other directions report no seed attempt.

## Required next probe work (avoid duplicate broad runs)

1. Use the ten `ui-audit.json` files to select *specific unobserved screens/actions*; do not repeat a ten-emulator full traversal without an improved strategy.
2. Improve focus/action ranking and depth, prioritize direct FBD block insertion/properties/wires and the 16-slot device manager over cycling through home/chooser states. Collect matched screenshot, XML, state transition and persisted data for each discovered property.
3. Treat any original license/purchase dialog as an explicit coverage blocker. Never assert that restricted functionality was exercised or try to circumvent licensing.
4. Run bounded targeted probes for individual missing contracts, with clear termination/evidence assertions, not unconstrained repeated 3,000-second jobs.
5. Reconcile findings with decompiled Java/Smali, original APK resources, factory ID and FBD source matrices and sample-project round-trips. UI traversal cannot replace code-backed semantic evidence.
6. Keep research PRs #149–151 and their distinct evidence until a verified consolidation/merge decision. No acceptance weight is added from run #172 alone.

Acceptance matrix remains **5 / 139 weighted points = 3.6%**, unchanged: this report adds source-audit evidence, not accepted behavior. Stable `main` was not changed.
