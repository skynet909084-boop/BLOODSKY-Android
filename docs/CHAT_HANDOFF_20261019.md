# BLOODSKY-Android session handoff — 2026-10-19

This is the durable entry point for resuming work after a ChatGPT/Work chat is closed. The repository, pull request, source audits, tests, and CI artifacts are the source of truth; chat history is not automatically copied between chats.

## Repository and current GitHub state

- Repository: https://github.com/skynet909084-boop/BLOODSKY-Android
- Active PR: https://github.com/skynet909084-boop/BLOODSKY-Android/pull/143
- PR title: Import Craft projects and map local integrations
- PR status when checked (2026-10-19 21:00 Asia/Yekaterinburg): open, mergeable, not merged.
- Head branch: `feat/craft-screen-navigation-import-20261018`
- Base branch: `feat/craft-project-json-import-20261018`
- Head SHA checked at initial handoff: `76c668fb70dfd697dea62a25e64777085031d4a3`; latest progress commit below.
- The default/stable branch is `main`. Do not merge this PR into stable without the requested review and verified checks.
- Local scratch workspace for this chat contained no project checkout; GitHub was the source used for status and documentation.

## CI status at handoff

At initial handoff, PR-triggered workflow runs for the then-current head were queued. Later runs must be checked against the latest head before relying on them:

| Workflow | Run | State |
|---|---:|---|
| BLOODSKY Android UI smoke | 691 | queued |
| BLOODSKY Android APK | 1058 | queued |
| Original Craft 1.0.7 emulator probe | 88 | queued |

Run links can be opened from the PR Actions tab: https://github.com/skynet909084-boop/BLOODSKY-Android/actions. Re-query the PR head and these checks before relying on this status; earlier passing runs apply only to their own SHAs.

## Current implementation summary recorded in PR #143

- Craft component factory IDs 1–28 and FBD IDs 101–140 have source-backed audit coverage. One widget ID and the macro/unknown paths remain unresolved or gated; do not claim full parity.
- 39 FBD blocks have adapters; block ID 135 remains gated pending nested graph/save behavior.
- Craft import/project settings and opt-in local TCP runtimes cover selected component IDs (6, 8, 13, 15, 19, 20, 22). SMS remains disabled pending explicit configuration.
- Original component palettes/resources are preserved and structurally checked. Several unsupported functions (voice recognition, GIF, camera, WebView, unknown IDs) remain preserved but inactive.
- Three-dot menu navigation was ported; a prior Android 11 UI smoke passed on its own commit. The current head still needs the queued checks above.
- Project import was statically checked against a supplied export: 346 components, 200 FBD blocks, 16 declared screens; all 11 encountered FBD IDs are represented. This was not an executable import test because Gradle was unavailable in that working environment.
- A regression test covers 16 `deviceNetwork` rows, `posDeviceNetworkInList=100`, and repeated `connectionsId=2`: imported value 100 remains metadata, is not treated as device 100, and does not create an active binding.
- Modbus/TCP, Bluetooth SPP/RTU, and USB-OTG/RTU source findings are in `docs/TRANSPORT_CONNECTIVITY_AUDIT_20261019.md`. Physical USB/Bluetooth and mock/real Modbus acceptance remain open.
- More parity details and the open checklist are in `docs/KASCADA_FULL_PARITY_AUDIT_20261018.md`, `docs/CRAFT_COMPONENT_ID_FACTORY_MATRIX_20261018.md`, and `docs/ORIGINAL_EMULATOR_AND_ID_COVERAGE_20261019.md`.

## System-register findings and gap

Source of truth: `docs/KASKADA_SYSTEM_REGISTERS_AUDIT_20261019.md`.

- The supplied KaScada manual describes the internal “Система” bank, service registers 0–9, and user registers through 1000; it also contains a conflicting “200 registers” statement.
- Craft 1.0.7 Smali confirms an `int[2000]` bank, valid indices 0–1999. Project save persists nonzero user values from 10 through 1999 as `systemRegisters` entries named `reg_N`. Service addresses 0–9 go through separate handler/side-effect paths.
- Boolean bits, 16-bit words, and Float values (two words) have separate read/write paths. Float byte/register order and exact service side effects still need source tracing or controlled original-app verification before coding.
- Current BLOODSKY gateway components can use explicitly configured TCP endpoints, but the internal “Система” bank, service-register providers, Float packing, and gateway endpoints into that bank are not yet implemented.
- Craft import slot 100 is a global/system binding marker (bucket index 8), not Modbus register 100 or network device 100. Current import preserves metadata; it does not execute the binding.
- Planned sequence: define/test a typed bank API; trace and isolate service-register side effects; route component bindings to the bank; then connect register/bit gateways to internal or external endpoints. Keep uncertain imported bindings inactive until correctly mapped.

## FBD macro status and next acceptance

Source of truth: `docs/FBD_MACRO_CODEC_20261018.md`.

- Craft macro definition is a named `.mcr` JSON file with up to four typed inputs and one typed output; the original macro tile opens interface setup, while the macro library is a separate file list.
- BLOODSKY has bounded import/export, project catalog persistence, a nested editor/runtime for explicitly embedded BLOODSKY graphs, and typed pin checks.
- Original opaque Craft child records are preserved but not executed until their component semantics are mapped. Supplied project exports have no `.mcr`, so they cannot validate original macro execution.
- Next focused acceptance: build a small macro with typed ports, wire a graph, save/export `.mcr`, reopen it, insert an instance, connect typed pins, and verify execution; add a targeted original-APK probe for the same workflow.

## Emulator/source-coverage limitations

- Original APK probes have been useful but remain partial. Prior probe #28 covered 18 states/40 transitions and did not reach component palette, FBD, macro, settings, or property editors. An earlier artifact had 128 screenshots/UI trees but no final traversal report.
- The recent PR description says a faster one-launch traversal strategy was added; do not assume its most recent run has finished. Verify run #88 and inspect its artifact/report before stating coverage.
- Source-ID coverage and emulator-screen coverage are different evidence; neither alone establishes full behavior or visual parity.

## Resume instructions for the next Work chat

1. Open the PR and this file; refresh the head SHA and workflow states first.
2. Read the linked audit relevant to the active task. Keep past checks attached to their original commit SHA.
3. Continue from the unfinished system-register bank work and/or the macro acceptance above; preserve the PR branch and keep stable `main` untouched.
4. Commit durable notes/results to the PR branch and update this handoff when the state changes.



## Progress after the initial handoff — 2026-10-19

- Continued on PR #143 branch `feat/craft-screen-navigation-import-20261018`; stable `main` remains untouched.
- Added `CraftSystemRegisterBank` as an isolated per-project bank for Craft user registers 10–1999 (the 2000-word capacity confirmed from pinned Smali). Service registers 0–9 remain outside this class because their original handlers have side effects.
- Added bounded 16-bit word and bit read/write, Float32 across two words with explicit word/byte swaps, and atomic import/export for Craft's `systemRegisters` JSON array (nonzero values only; malformed/duplicate/service rows reject the complete restore).
- Added `CraftSystemRegisterBankTest` for range checks, bit preservation, all four Float byte/register layouts, JSON persistence, and atomic rejection.
- Commits: `9d35beb2ca13d0023a42e7a6c29584f324435ce8` (bank), `2d72493fdd31e13531245ee2ddb51da8256c185d` (tests), `b666289175cdd4a2a455b3d03648d3455f991f10` (declare JSON exception in API/tests).
- This is a foundational implementation only: it is not yet connected to project load/save, component bindings, service-register providers, or gateway endpoints. Do not claim complete system-register parity.
- Current workspace has no checked-out project, so local Gradle/test execution was unavailable. Check exact-head APK/unit and UI workflows before relying on this change.


## Progress after register-bank persistence integration — 2026-10-19

- Added per-project Craft system-register bank restore/save in `MainActivity`. Valid Craft arrays restore atomically; only user words 10–1999 are saved; unknown/malformed bank fields are preserved instead of overwritten.
- Resolved the name collision: the legacy BLOODSKY text setting now lives in `systemRegisterSettings`; legacy string values are migrated from `systemRegisters`, while Craft's JSONArray remains dedicated to the register bank. Added a regression test for both cases.
- Commit `10d4623f331fe80c7bbad3cca435a1f955e6bc1d`: `fix(registers): separate Craft bank from legacy settings`.
- Exact-head checks were triggered: APK/unit run #1064 (run ID `39286221453`), UI smoke #697 (`39286221404`), Craft emulator probe #94 (`39286221413`). Last checked, all three were queued without an assigned runner. Check their status against the newest PR head before relying on them.
- Prior exact-head run #1063 on `911b5e3472149ca02e9a15ee15382093b9ae2379` passed APK/unit tests. Its Craft probe #93 completed but reached only 12 states / 11 transitions and stopped at its state limit; coverage was incomplete.
- This work implements project storage and compatibility only. It does not route component bindings to the bank or implement service-register behavior. The next safe step is to trace and isolate service providers/commands before activating any component binding. Keep imported/ambiguous bindings inactive.


## CI results for register-bank persistence — 2026-10-19

- APK/unit run #1064 (39286221453) completed its build job successfully: archive-inspector tests, Android unit tests, debug APK build, and artifact uploads all passed for the PR merge ref containing code commit 10d4623f331fe80c7bbad3cca435a1f955e6bc1d.
- Original Craft emulator probe #94 (39286221413) rebuilt and launched the pinned Craft 1.0.7 APK successfully. It captured 12 states and 11 transitions in 72.6 seconds, then stopped at the configured state limit; coverage_complete=false. It reached macro editor/create flow but did not cover full functionality.
- UI smoke #697 (39286221404) built the APK and emulator successfully, but the Install and exercise APK on Android 11 step was cancelled (The operation was canceled). GitHub still reported the workflow run as in progress although its only job was marked cancelled; immediate job rerun was rejected because GitHub considered the workflow run still running. Retry after its run status settles.
- These are results for the code commit/PR merge ref above, not proof for subsequent application-source changes. PR head is now documentation-only commit 35c7b3f1633f043ce2bd83b25dbb588af6e2164c.
- Next work: resolve/retry the UI smoke cancellation, then trace original service-register handlers and route supported FBD/global user-register access to the project bank. Service registers 0–9 and unclear imported bindings must remain inactive until their effects are source-backed and tested.


## CI and FBD register integration — 2026-10-19

- On documentation-only head b3a88aa4ab96da2b5cdebde3909d8240cd73c3cb, APK/unit run #1066 passed, Original Craft probe #96 completed successfully, and Android UI smoke #699 passed on Android 11.
- Probe #96 rebuilt and launched Craft successfully but remains partial: 12 states / 11 transitions, 68.6 seconds, termination at state_limit. It reached the macro editor route; it is not full feature coverage.
- Verified from docs/FBD_CRAFT_MX_ARRAY_20261018.md and docs/KASKADA_SYSTEM_REGISTERS_AUDIT_20261019.md that FB_MX/FB_ARRAY share q.d.b and the project saves user words 10–1999. BLOODSKY now routes these FBD user-word reads/writes to each project's persisted bank; transient service words 0–9 remain isolated pending side-effect tracing.
- New implementation changes are being prepared on PR #143; check the latest head and checks before relying on them. Keep main untouched and do not merge without review.


## FBD evaluator correction — 2026-10-19

- First compile exposed that the graph evaluator is static; it cannot capture MainActivity.systemRegisterBank directly. The previous code commit failed APK/unit and UI build for that compile error.
- Updated the evaluator API to accept the project bank at its top-level entry and carry it through recursive evaluation via a scoped ThreadLocal, restored in finally. Added an FBD graph integration test for ARRAY write → persisted bank → MX read.
- Fix is on the current PR branch; previous CI results do not include this correction. Recheck the latest head.


## Craft 1.0.7 service registers 0–9 — 2026-10-19

- User asked to compare HMI-KaSCADA v1.4 manual table 9.1.1 with actual Craft 1.0.7 decompiled behavior, then port that behavior to BLOODSKY. The pinned full decompilation is in the original-Craft artifact from run 38600957172; audit sources: q/d/b.java, mbCanvas.java, MyApplication.java, Utils/o.java and palette q/d/a.java.
- Implemented CraftServiceRegisters as a shared transient service bank. FbdCraftRegisterStore routes MX reads and ARRAY raw writes to that bank for addresses 0–9 and keeps user words 10–1999 project-scoped. ARRAY raw T / MX raw I deliberately bypass VarOut handlers. Separate word/bit handler APIs preserve source-specific effects.
- MainActivity supplies charging, battery, brightness and current-screen values, and handles navigation, palette, save, refresh, URL and exit actions. Service behavior and write-path differences are recorded in docs/KASKADA_SYSTEM_REGISTERS_AUDIT_20261019.md; the FBD and manual crosswalk notes were updated too.
- Source differences from manual: register 7 is a palette index 0–16 (bit handler stores the bit index itself); register 8 word writes submit a password candidate without storing it; register 9 whole-word writes trigger exit only, while bit writes implement save/refresh/URL/exit/log actions with source timing. Service words are shared, not per-project.
- Bounded gaps: project-password unlocking and Craft-style channel-fault sources have no BLOODSKY equivalent yet; Craft bit-10 opens an embedded WebView, while BLOODSKY uses external URL intent; typed UI VarIn/VarOut bindings are not implemented. Keep these limits visible and do not claim full component-binding parity.
- APK/unit run #1084 passed and built the debug APK for code head 1cd27d1fac3fd169b731ade8c223a777b1c2f475. Original Craft probe #114 passed for the same code head. Full BLOODSKY Android 11 smoke #717 was superseded by documentation-only commits; check the newest CI runs on this handoff commit.
- PR #143 remains open, base feat/craft-project-json-import-20261018, and must not be merged into stable main without review.
