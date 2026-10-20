# BLOODSKY continuation handoff — 2026-10-20

This file is the durable handoff for continuing the Craft 1.0.7 parity work without relying on chat history. Update it when the active branch, verification state, or next work changes.

## Working line and safety

- Repository: `skynet909084-boop/BLOODSKY-Android`.
- Active branch: `feat/craft-screen-navigation-import-20261018`, PR #143, stacked on PR #142 → #141 → #139 → #76.
- Stable `main` is untouched. Do not merge PRs, delete branches, or rebase stacked work as part of routine progress; preserve branch dependencies and review them first.
- Local checkout was clean and matched `origin/feat/craft-screen-navigation-import-20261018` at `56ff22926c43e088cf59d4825ad5c13bef594b98` when this handoff was written.
- Persist each focused, reviewed change to GitHub on the active feature branch and update this handoff after meaningful gates. Do not rely on chat memory as the project record.

## Latest completed changes

- `6d05c18` — ID19 can read an explicitly configured local Craft system-register range through `system://register/N` (0–1999) and copy it to an explicitly configured TCP holding-register destination. System-register destinations/writes are unsupported; imported bindings remain disabled.
- `cf2b430` — corrected the system gateway audit to reflect the actual implementation boundary.
- `56ff229` — fixed the Android UI smoke selector to expect the Craft-localized FBD timer title (`Свойства: FBD Таймер`). This was a test selector bug, not an app defect.

## Verification snapshot for `56ff229`

- Local Python suite: 149/149 passed after `python tools/prepare_craft_resources.py` materialized pinned Craft resources; generated resources were cleaned afterward.
- Android APK/JUnit run #1123: passed — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39358988336
- Quick original Craft probe #154: passed its focused route, 8 states / 7 transitions; `coverage_complete=false` by design — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39358988371 (artifact 12236104710).
- Full Android 11 UI smoke #758 attempt 1 was cancelled during the emulator script after opening `bloodsky-custom.png`; logs show no test verdict or assertion failure, and an artifact was uploaded (ID 12236389349). The job limit is 35 minutes, so the cancellation was not the configured timeout. Attempt 2 has been requested and is pending — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39358988319
- Full original Craft 1.0.7 emulator probe #155 remains in progress at offline UI traversal — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39359626632
- The APK/JUnit and quick probe checks run against exact commit `56ff22926c43e088cf59d4825ad5c13bef594b98`. The UI and Craft full-profile runs also target that product-code head; the UI rerun is a new attempt against the same snapshot. Record final outcomes here; diagnose and fix real failures before treating the gate as complete.

## Source and documentation baseline

- `docs/GITHUB_BRANCH_AUDIT_20261019.md` inventories the active PR stack, open branch work, and earlier gate results. Last recorded repository-wide inventory: 129 branches and 31 open PRs; counts are an audit snapshot, not a branch-pruning instruction.
- `docs/KASKADA_FULL_PARITY_AUDIT_20261018.md`, `docs/KASKADA_DECOMPILATION_AUDIT.md`, `docs/HMI_KASCADA_MANUAL_CROSSWALK.md`, and topic-specific audits are the source-backed parity record. The supplied HMI-KaSCADA v1.4 manual was rendered and scanned (109 pages); screenshots were compared with the register table and dynamic-picture settings. The pinned Craft 1.0.7 Apktool/JADX archive SHA-256 is `d78bd703d6096ac12d69e19f8b5b5dfa9a4c25e342ce9af0ef8b42326aaa3f8c`.
- Distinguish manual/decompilation evidence, automated tests, emulator exploration, and real-device/visual acceptance. A successful build or focused probe is not full parity.

## Open work queue

1. Finish and record full UI smoke #758 and Craft probe #155. Continue from any uncovered/failing state, then rerun the affected gate on the new exact head.
2. Implement source-confirmed typed bindings from non-FBD components to Craft system registers and the remaining internal/system gateway behavior, preserving explicit opt-in and fail-closed import semantics.
3. Complete legacy macro round-trip and execution behavior only where supported by Craft source/fixtures; unknown child blocks must remain preserved and fail closed.
4. Complete Modbus RTU Bluetooth/USB adapters, reconnect/lifecycle handling, and physical-device acceptance; the current RTU client core is stream-level only.
5. Continue the manual + screenshots + pinned-decompilation gap audit across component properties, editor/settings/device screens, FBD behavior and persistence; implement verified gaps in focused changes with regression coverage.
6. Re-audit all open PRs and all branch tips against the latest source and exact-head checks. Preserve stacked dependencies; do not assume an open PR, green historical run, or “mergeable” flag proves current acceptance.

## Resume procedure

1. Read this handoff plus `docs/GITHUB_BRANCH_AUDIT_20261019.md` and the relevant topic audits before changing code.
2. Check the current remote branch head and its workflow outcomes; do not reuse old run results for a newer commit.
3. Select one source-backed gap, implement it with focused tests, run the Python suite and applicable Android/Craft workflows, and commit it to the active branch.
4. Update this file and the detailed audit with commit SHA, evidence, remaining limits, and direct GitHub run/artifact links.
