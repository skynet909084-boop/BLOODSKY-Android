# BLOODSKY-Android session handoff — 2026-10-19

This is the durable entry point for resuming work after a ChatGPT/Work chat is closed. The repository, pull request, source audits, tests, and CI artifacts are the source of truth; chat history is not automatically copied between chats.

## Repository and current GitHub state

- Repository: https://github.com/skynet909084-boop/BLOODSKY-Android
- Active PR: https://github.com/skynet909084-boop/BLOODSKY-Android/pull/143
- PR title: Import Craft projects and map local integrations
- PR status when checked (2026-10-19 21:00 Asia/Yekaterinburg): open, mergeable, not merged.
- Head branch: `feat/craft-screen-navigation-import-20261018`
- Base branch: `feat/craft-project-json-import-20261018`
- Head SHA checked: `76c668fb70dfd697dea62a25e64777085031d4a3`
- The default/stable branch is `main`. Do not merge this PR into stable without the requested review and verified checks.
- Local scratch workspace for this chat contained no project checkout; GitHub was the source used for status and documentation.

## CI status at handoff

For the PR head SHA above, the latest queried PR-triggered workflow runs were queued; no outcome was confirmed yet:

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

