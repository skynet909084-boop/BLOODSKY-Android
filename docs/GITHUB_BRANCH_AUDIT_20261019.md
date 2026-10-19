# BLOODSKY GitHub branch and unfinished-work audit — 2026-10-19

## Repository snapshot

- Repository: `skynet909084-boop/BLOODSKY-Android`; default branch: `main`.
- All remote refs were fetched locally and enumerated: 129 branches. Eleven branch tips (including `main`) are already reachable from `main`; 118 have commits outside `main`.
- GitHub search returned 31 open pull requests. This includes dependency stacks and drafts, so an open PR or a mergeable flag alone is not evidence that it is ready for `main`.
- No branch was deleted and `main` was not changed. Old branches often form the base of an active PR; rebasing or deleting them without checking descendants could discard work.

## Active integration chain

| PR | Head → base | State / remaining work |
|---:|---|---|
| #76 | `feat/fbd-75-pin-semantics` → `main` | Draft; typed FBD pins and runtime still need exact-head CI, original comparison, and acceptance. Parent for later FBD work. |
| #139 | `feat/fbd-complete-source-batch-20261018` → #76 branch | Open; native Craft operators/timer/MX/ARRAY. Keep stacked until the complete source and UI gates pass. |
| #141 | `feat/fbd-legacy-macro-runtime-20261018` → #139 branch | Open; verified legacy macro execution. Original opaque macro child records remain fail-closed. |
| #142 | `feat/craft-project-json-import-20261018` → #141 branch | Open; `.pro` ZIP importer. Import/runtime and media acceptance remain partial. |
| #143 | `feat/craft-screen-navigation-import-20261018` → #142 branch | Open; current integration branch. Exact old head `8d589bf` passed APK/unit run #1096, Craft probe #126, and Android UI smoke #729. Probe #126 only captured 12 states/11 transitions and ended at `state_limit` (`coverage_complete=false`). |

PR #143 is the active working line. Its head commit already includes source-backed service registers 0–9 and typed user-register 10–1000 bindings from Craft 1.0.7. The manual says 200 registers and lists through 1000; decompiled runtime allocates 2000 integer slots (0–1999) and persists user words from 10 onward. That discrepancy and the implemented side-effect split are documented in `KASKADA_SYSTEM_REGISTERS_AUDIT_20261019.md`.

## Current branch change

- Craft ID17 is the manual's dynamic picture (§7.3, PDF pages 49–50): it has sixteen paths and chooses an image by bit or numeric register value.
- The source-confirmed importer change from PR #157 was cherry-picked into PR #143 as commit `54830d6` (`Import Craft multi-state image media safely`). It maps only unique, present ZIP media paths to `imagePathStates`; absent/ambiguous paths stay unresolved, all Craft metadata is retained, and the old device/register binding stays inactive.
- Three importer tests cover present, missing, and ambiguous media. Source inspection found the exact runtime choice in `CustomComponents/h.java`: `typeCommand=0` selects numeric image 0–15; nonzero uses the first set bit and hides the component for mask zero. `CraftMultiStateImage` implements this selection and MainActivity uses it; the property editor now exposes both modes and documents explicit TCP binding. Same-density visual/device proof remains open.
- The first two runtime-transfer commits were caught by exact merge checks: `b9c8f58` truncated `MainActivity.java`, `0ba4383` still had a truncated tail, and `125ae8a` compiled the restored file but missed a renderer closing brace. Commit `f222d6c` has the complete file (verified against its Git blob SHA) and the corrected brace. Local Python tests pass 149/149. On exact head `e0de694`, APK/JUnit #1103 and full Android 11 UI smoke #736 passed. Craft probe #133 passed as a run but reached only 12 states / 11 transitions and ended at `state_limit` (`coverage_complete=false`).
- PR #157's earlier APK/unit run #1029 and Craft probe #52 succeeded on its own head. Those checks do not cover the cherry-picked PR #143 head; new exact-head CI is required before closing the duplicate PR.

## Other open PR work that is not complete

| PRs | Workstream | Current disposition |
|---|---|---|
| #134–#138 | FBD source audit, CHANGE I/F, editor UI, picker and gap notes | Separate stacked feature/test work. Preserve; check each source SHA and head-specific tests before folding into #143. |
| #140 | ZIP import path/staging hardening | Separate open PR; do not assume integration until its archive regression gate is checked against the current importer. |
| #144 | RTU response regressions | Draft with merge conflict; rebase/resolve and rerun tests before integrating. |
| #145 | Component factory ID3 audit | Draft; source says factory falls back for ID3, so identity remains unresolved rather than guessed. |
| #146–#147 | Nested macro capture and macro editor page | Draft stack. Capture/editor work does not prove original `.mcr` child execution or full create→wire→save→reopen behavior. |
| #148 | Component/source/emulator ID audit | Draft documentation. Useful source evidence, but not an acceptance run. |
| #149–#151 | Craft emulator loading and supplemental/long-press probes | Draft stack; older supplemental run #8 was cancelled. Latest PR #143 probe is successful but incomplete, so full original UI coverage is still open. |
| #152 | Network binding versus FBD graph-edge preservation | Draft, currently targets `main`; test verifies metadata separation, not working global/system component bindings. |
| #154 | Modbus RTU client core | Open and tests passed on its own head; it is a stream-level FC01–FC04 helper only. Bluetooth SPP/USB adapter, write functions, reconnect/lifecycle, and physical acceptance remain open. |
| #155 | Craft macro codec contract fixtures | Open and test-only. It captures serialization contracts, not execution support for unknown child blocks. |
| #93 | Legacy media migration | Draft based on `main`; requires exact-head APK/JUnit, Android UI, persistence and media acceptance. |
| #69, #73–#74 | FBD menus/canvas and cloud-agent experiments | Draft work based on older branches. Review dependency/CI state before reuse; do not treat experiment success as product parity. |
| #38, #44–#46 | Jules/portable media and recovery stack | Older stack with conflicts and broad changes. Portable-media fixes have been ported in later work; do not replace current Activity/FBD/runtime wholesale. Re-audit only focused residual fixes. |
| #45 | Batch5 source-contract backlog | Open, broad documentation/tooling proposal; reconcile with newer audits before merging. |

Other unmerged refs without an open PR directly attached include branch bases, test/audit checkpoints, and older experiments. They were left intact; a full descendant/dependency review is needed before pruning them.

## Manual, screenshots, and source evidence checked

- Rendered and scanned all 109 pages of the supplied HMI-KaSCADA v1.4 PDF. Reviewed its chapter/index pages and visual screenshots, the system register table 9.1.1 (PDF page 70), and the dynamic picture settings (PDF pages 49–50).
- Inspected the supplied screenshot of table 9.1.1 and the recent GitHub screenshots. They support the documented color/bit layout and user-visible source fields; they are evidence, not replacements for runtime acceptance.
- Downloaded the pinned Craft 1.0.7 Apktool/JADX archive used by existing audits and verified its SHA-256 against the repository record: `d78bd703d6096ac12d69e19f8b5b5dfa9a4c25e342ce9af0ef8b42326aaa3f8c`.
- The original-app probe artifact rebuilt and installed Craft successfully with no AndroidRuntime errors, but captured only the home screen, editor/FBD picker and macro create/edit path. It did not traverse the full component properties, settings, devices, transport setup, or all FBD functions.

## Next gates

1. On `4cd07e3`, APK/JUnit #1106 passed. UI smoke #739 is running after the official Paho repository fix. Craft probe #136 passed as a run but remains partial (12 states, 11 transitions, `state_limit`).
2. Continue source-backed gaps on the same integration line in focused commits: internal/system gateway endpoint and typed component binding; macro round-trip/execution; RTU hardware adapter; remaining documented component/runtime gaps.
4. Keep PR #143 open until its checks and acceptance notes are current. Close PR #157 only after its change is present in #143 and the new head passes.
5. Do not merge into `main` or delete the unmerged dependency branches while their stacked PRs remain active.

## Branch audit continuation — 2026-10-19

- PR #143 head `7d72ac8` adds the ID17 number/bit selection property control. Local Python suite passed 149/149; parity JSON and `git diff --check` passed.
- Clean-runner UI smoke exposed a dependency-resolution gap hidden by the APK workflow cache. Eclipse's official Paho instructions list `https://repo.eclipse.org/content/repositories/paho-releases/` as the release repository; it is now included in `settings.gradle` so CI and fresh checkouts can resolve the MQTT client without relying on a warm cache.
- Current follow-up commit must be checked by fresh APK/JUnit, Android 11 UI smoke and original Craft probe. No merge, branch deletion or change to `main` was made.

## Focused Craft probe route correction — in progress

- Probe #136 screenshots and accessibility tree show the HMI component palette at state 4, then the old quick route tapped `switch_layout` and spent the rest of its 12-state budget creating a macro.
- Decompiled `mbCanvas.java` confirms `tab_dyn_pictures` is a drag source; dropping it on the canvas creates `CustomDynPictures`. The #136 accessibility tree exposes that exact image control (`long-clickable=true`) at `[611,938][751,1078]`.
- Probe #137 reached the palette, dragged the ID17 source, and tapped the drop point, but the captured screen remained the palette and exposed no property fields. The prior quick-route success flag was too broad, so commit `df7ac34` now requires visible property fields and a closed palette before reporting the waypoint.
- Exact-head probe #138 rebuilt and launched the original APK successfully, captured 7 states / 6 transitions in 38.2 seconds, and correctly reported `guided_goal_reached=false` because the palette remained visible with no property fields. Placement/property interaction remains unverified. APK/JUnit #1108 passed; Android 11 UI smoke #741 was still running when this note was prepared. Quick runs remain explicitly incomplete UI coverage.

## Focused Craft ID17 property route — 2026-10-19 continuation

- On PR #143, commit `59250da` taught the probe to use the placed view's accessibility bounds and infer the uncovered canvas area from the screenshot. The next artifact showed why a direct tap still failed: Craft's component palette covers the full-screen touch layer.
- JADX confirms `t/b.java` installs a touch listener on `layout_tab_component`; a left fling calls `mbCanvas.x0(true)`, which shifts the palette layer and reveals the editor canvas. The probe now performs that Craft-native gesture before tapping ID17.
- Exact-head probe run `39318191856` captured the property dialog, including the `reg` field, but still reported the guided goal as false. The artifact showed a reporting bug: populated fields are intentionally excluded from the actionable-control list. Commit `ac73f9b` now evaluates all visible controls in the state report.
- Exact-head original Craft probe run `39318963974` passed and reports `guided_goal_reached=true`, 8 states, 7 transitions, and 47.8 seconds. Its final state has the ID17 property dialog open with `reg=0`; coverage remains incomplete by design because this is the focused quick route, not the full UI crawl.
- APK/JUnit run `39318963991` passed on `ac73f9b`. PR-triggered Android 11 smoke `39318964026` was cancelled before producing a result. Full profile was manually dispatched as run `39320189795` on documentation-only head `7ced551`; it is in progress and must finish before the Android 11 gate can be marked passed. No product-code behavior was changed in this probe-fix slice.
