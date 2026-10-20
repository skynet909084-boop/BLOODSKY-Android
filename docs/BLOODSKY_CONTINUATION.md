# BLOODSKY continuation handoff — 2026-10-20

This file is the durable handoff for continuing the Craft 1.0.7 parity work without relying on chat history. Update it when the active branch, verification state, or next work changes.

## Working line and safety

- Repository: `skynet909084-boop/BLOODSKY-Android`.
- Active branch: `feat/craft-screen-navigation-import-20261018`, PR #143, stacked on PR #142 → #141 → #139 → #76.
- Stable `main` is untouched. Do not merge PRs, delete branches, or rebase stacked work as part of routine progress; preserve branch dependencies and review them first.
- The ID8 test/documentation follow-up is committed as `4f3a6c605f9fb03d9da032ce3ae453dbab24e301` on the same PR branch. APK/JUnit #1130 passed on this exact head; full UI #764 and Craft probe #161 are still running.
- Persist each focused, reviewed change to GitHub on the active feature branch and update this handoff after meaningful gates. Do not rely on chat memory as the project record.

## Latest completed changes

- `6d05c18` — ID19 can read an explicitly configured local Craft system-register range through `system://register/N` (0–1999) and copy it to an explicitly configured TCP holding-register destination. System-register destinations/writes are unsupported; imported bindings remain disabled.
- `cf2b430` — corrected the system gateway audit to reflect the actual implementation boundary.
- `56ff229` — fixed the Android UI smoke selector to expect the Craft-localized FBD timer title (`Свойства: FBD Таймер`). This was a test selector bug, not an app defect.
- `efdef6a`/`b5e62e8` — imported Craft component ID 7 (`CustomDynText`) reads a bit from the local system-register bank when `posDeviceNetworkInList=100` and `typeRequest=1`; network bindings and output widgets remain disabled. Evidence: pinned `CustomComponents/CustomDynText.java#getStringAddress` selects request 1 and label `S#<address>` for system slots; `q/d/b.java#G` reads the configured address bit. Regression coverage is in `CraftSystemWidgetBindingTest`.
- `7bb8bb3` — imported Craft ID8 reads the exact system word for source slot 100, with UINT16/INT16 display; writes require the existing explicit input opt-in and are limited to integer mode and project registers 10–1999. Float32 and writes to service registers 0–9 stay disabled. Source evidence: pinned `CustomComponents/i.java#getInitConnectionInfo`, `setValue`, and `mbCanvas.t`; tests are in `CraftSystemNumericWidgetBindingTest`.

## Verification snapshot for `56ff229`

- Local Python suite: 149/149 passed after `python tools/prepare_craft_resources.py` materialized pinned Craft resources; generated resources were removed afterward.
- Android APK/JUnit run #1125: passed on docs-only head `c5b166c` — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39360336164
- Quick original Craft probe #157: passed its focused route, 8 states / 7 transitions in 47.9 seconds; `coverage_complete=false` by design — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39360336281 (artifact 12237655437).
- Full Android 11 UI smoke #760: passed on `c5b166c` — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39360336162
- Full original Craft 1.0.7 emulator probe #155 remains in progress on exact product head `56ff22926c43e088cf59d4825ad5c13bef594b98` — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39359626632
- On intermediate head `efdef6ac75a1bbd558808cacd53d5779f2634868`, the APK/JUnit run failed only because the new test expected service bit 15 to be 1 after `setCharging(true)`. Craft stores `!charging` in that bit; the manual defines 0 = charging and 1 = not charging. The test was corrected in `b5e62e8e6284de5265185b07b2a5cb9d61cb8721` to assert 1 after `setCharging(false)` and restore its prior value.
- Exact head `b5e62e8e6284de5265185b07b2a5cb9d61cb8721`: Android APK/JUnit/build passed — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39364083355; focused Craft probe passed — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39364083373; Android 11 UI smoke #762 passed — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39364083422.
- Initial exact-head run on `7bb8bb356f1a8e87f98ed681ad036075250232a1`: APK/JUnit #1129 found two fixture assertions to correct — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39365707344. Android 11 full UI smoke #763 passed on that same product head — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39365707406; its 9.4 MB diagnostic artifact is 12239996717, SHA-256 `3ade30cec0ad08c3612c7e6eae63dc0cc480ab25d42223433bb42ce2f9409904`. Commit `4f3a6c6` corrects both test fixtures and extends the parity/handoff docs. APK/JUnit #1130 passed on the exact head — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39367551305; test/build diagnostics artifact 12239902934 (SHA-256 `71aba7f17b94d82423582f25a6884c123e0f3414dd58f766806bc45539f05892`) and debug APK artifact 12239882942 (SHA-256 `64d18862e177a4b483f446e951e42cf5b7fe522c108895e28e8f4c79f05444c2`). Full UI #764 and Craft probe #161 are active on this head.
- Quick original Craft probe #160 completed on `7bb8bb3`: 8 states / 7 transitions in 47.4 seconds; `execution_finished=true`, `coverage_complete=false`; artifact 12239129480 — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39365707332/artifacts/12239129480.
- Full manually dispatched original Craft traversal #155 remains active on older product head `56ff22926c43e088cf59d4825ad5c13bef594b98` — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39359626632. Do not treat either quick route as complete coverage.
- The local environment has no `gradle` executable or wrapper. Python suite passed 149/149 after `python tools/prepare_craft_resources.py` materialized pinned Craft resources; generated files were removed. The corrected Android fixture assertions passed in APK/JUnit #1130 on `4f3a6c6`. Full Android 11 smoke #763 passed on parent `7bb8bb3`; #764 is the same full profile on the current head.

## GitHub Actions / Gradle review (2026-10-20)

- The suggested “Build with Gradle” repository, `gradle/gradle-build-action`, says its v3 action was superseded by `gradle/actions/setup-gradle`; current workflows already use `gradle/actions/setup-gradle@v4` with Gradle 8.9. The pinned v4 action provides Gradle User Home caching, so adding the legacy action would duplicate setup rather than speed up the build. References: https://github.com/gradle/gradle-build-action and https://github.com/gradle/actions/tree/v4/setup-gradle.
- The suggested wrapper updater creates PRs for checked-in Gradle Wrapper files. This repository currently has no `gradlew`, `gradlew.bat`, `gradle-wrapper.properties`, or `gradle-wrapper.jar`, so there is nothing for it to update. Defer that scheduled PR workflow until the project adopts a checked-in wrapper; CI currently pins Gradle 8.9 centrally in `gradle/actions/setup-gradle`.
- No action change was made from these recommendations: the cache/setup action is already present, and introducing a wrapper automation without a wrapper would add noise instead of removing build friction.

## GitHub Actions / Gradle review (2026-10-20)

- The suggested “Build with Gradle” repository, `gradle/gradle-build-action`, says its v3 action was superseded by `gradle/actions/setup-gradle`; current workflows already use `gradle/actions/setup-gradle@v4` with Gradle 8.9. The pinned v4 action configures Gradle User Home caching, so adding the legacy action would duplicate setup rather than speed up the build. References: https://github.com/gradle/gradle-build-action and https://github.com/gradle/actions/tree/v4/setup-gradle.
- The suggested wrapper updater creates PRs for checked-in Gradle Wrapper files. This repository currently has no `gradlew`, `gradlew.bat`, `gradle-wrapper.properties`, or `gradle-wrapper.jar`, so there is nothing for it to update. Defer that scheduled PR workflow until the project adopts a checked-in wrapper; CI currently pins Gradle 8.9 centrally in `gradle/actions/setup-gradle`.
- No action change was made from these recommendations: the cache/setup action is already present, and introducing wrapper automation without a wrapper would add noise instead of removing build friction.

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
