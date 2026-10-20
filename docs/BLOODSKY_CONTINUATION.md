# BLOODSKY continuation handoff — 2026-10-20

This file is the durable handoff for continuing the Craft 1.0.7 parity work without relying on chat history. Update it when the active branch, verification state, or next work changes.

## Working line and safety

- Repository: `skynet909084-boop/BLOODSKY-Android`.
- Active branch: `feat/craft-screen-navigation-import-20261018`, PR #143, stacked on PR #142 → #141 → #139 → #76.
- Stable `main` is untouched. Do not merge PRs, delete branches, or rebase stacked work as part of routine progress; preserve branch dependencies and review them first.
- Local checkout matched `origin/feat/craft-screen-navigation-import-20261018` at `c5b166c0a8779febc67f46505d160e4a239b8c5d` before the current read-only system-widget change.
- Persist each focused, reviewed change to GitHub on the active feature branch and update this handoff after meaningful gates. Do not rely on chat memory as the project record.

## Latest completed changes

- `6d05c18` — ID19 can read an explicitly configured local Craft system-register range through `system://register/N` (0–1999) and copy it to an explicitly configured TCP holding-register destination. System-register destinations/writes are unsupported; imported bindings remain disabled.
- `cf2b430` — corrected the system gateway audit to reflect the actual implementation boundary.
- `56ff229` — fixed the Android UI smoke selector to expect the Craft-localized FBD timer title (`Свойства: FBD Таймер`). This was a test selector bug, not an app defect.
- Current uncommitted slice: imported Craft component ID 7 (`CustomDynText`) can read a bit from the local system-register bank when `posDeviceNetworkInList=100` and `typeRequest=1`; network bindings and output widgets remain disabled. Evidence: pinned `CustomComponents/CustomDynText.java#getStringAddress` selects request 1 and label `S#<address>` for system slots; `q/d/b.java#G` reads the configured address bit. Regression coverage is in `CraftSystemWidgetBindingTest`.

## Verification snapshot for `56ff229`

- Local Python suite: 149/149 passed after `python tools/prepare_craft_resources.py` materialized pinned Craft resources; generated resources were removed afterward.
- Android APK/JUnit run #1125: passed on docs-only head `c5b166c` — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39360336164
- Quick original Craft probe #157: passed its focused route, 8 states / 7 transitions in 47.9 seconds; `coverage_complete=false` by design — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39360336281 (artifact 12237655437).
- Full Android 11 UI smoke #760: passed on `c5b166c` — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39360336162
- Full original Craft 1.0.7 emulator probe #155 remains in progress on exact product head `56ff22926c43e088cf59d4825ad5c13bef594b98` — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39359626632
- Exact new head `efdef6ac75a1bbd558808cacd53d5779f2634868`: quick Craft probe #157 passed — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39363667130; full Android UI smoke is still running — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39363667121; Android APK/JUnit run #39363667171 failed only in the new test `systemServiceBitsAreReadFromCraftServiceRegisterProvider` — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39363667171.
- The failing assertion expected bit 15 to be 1 after `setCharging(true)`. Craft's service provider intentionally stores `!charging` in that bit (the manual defines 0 = charging, 1 = not charging); the test now checks 1 after `setCharging(false)` and restores its prior value. Rerun the APK/JUnit gate on the follow-up head before calling this change verified.
- The local environment has no `gradle` executable or wrapper. Python suite passed 149/149 after `python tools/prepare_craft_resources.py` materialized pinned Craft resources; generated files were removed. Do not report Android verification until the exact-head rerun passes.

## Source and documentation baseline

- `docs/GITHUB_BRANCH_AUDIT_20261019.md` inventories the active PR stack, open branch work, and earlier gate results. Last recorded repository-wide inventory: 129 branches and 31 open PRs; counts are an audit snapshot, not a branch-pruning instruction.
- `docs/KASKADA_FULL_PARITY_AUDIT_20261018.md`, `docs/KASKADA_DECOMPILATION_AUDIT.md`, `docs/HMI_KASCADA_MANUAL_CROSSWALK.md`, and topic-specific audits are the source-backed parity record. The supplied HMI-KaSCADA v1.4 manual was rendered and scanned (109 pages); screenshots were compared with the register table and dynamic-picture settings. The pinned Craft 1.0.7 Apktool/JADX archive SHA-256 is `d78bd703d6096ac12d69e19f8b5b5dfa9a4c25e342ce9af0ef8b42326aaa3f8c`.
- Distinguish manual/decompilation evidence, automated tests, emulator exploration, and real-device/visual acceptance. A successful build or focused probe is not full parity.

## Open work queue

1. Finish and record full Craft probe #155. The full UI smoke passed at #760; rerun it on the current code head after commit.
2. Extend source-confirmed typed system bindings beyond dynamic-text bit reads (starting with numeric inputs), and implement the remaining internal/system gateway behavior while preserving explicit opt-in and fail-closed import semantics.
3. Complete legacy macro round-trip and execution behavior only where supported by Craft source/fixtures; unknown child blocks must remain preserved and fail closed.
4. Complete Modbus RTU Bluetooth/USB adapters, reconnect/lifecycle handling, and physical-device acceptance; the current RTU client core is stream-level only.
5. Continue the manual + screenshots + pinned-decompilation gap audit across component properties, editor/settings/device screens, FBD behavior and persistence; implement verified gaps in focused changes with regression coverage.
6. Re-audit all open PRs and all branch tips against the latest source and exact-head checks. Preserve stacked dependencies; do not assume an open PR, green historical run, or “mergeable” flag proves current acceptance.

## Resume procedure

1. Read this handoff plus `docs/GITHUB_BRANCH_AUDIT_20261019.md` and the relevant topic audits before changing code.
2. Check the current remote branch head and its workflow outcomes; do not reuse old run results for a newer commit.
3. Select one source-backed gap, implement it with focused tests, run the Python suite and applicable Android/Craft workflows, and commit it to the active branch.
4. Update this file and the detailed audit with commit SHA, evidence, remaining limits, and direct GitHub run/artifact links.
