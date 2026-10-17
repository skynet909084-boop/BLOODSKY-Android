# BLOODSKY Android UI CI — quick FBD vs full acceptance

Updated 2026-10-17. Stable `main` is **not changed** by this CI experiment.

## Verified implementation checkpoint, 17 October 2026

- Full [#107](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/107) at source-style head `3b3d4241f23f29aa25b061a921f898bcef6628df` **PASS**: Android APK/JUnit [39058889524](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39058889524) plus 800+-line complete Android API30 smoke [39058889521](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39058889521). UI artifact `12110094620` reviewed, including `result.json: passed`, gray blocks, selected wire and dark long-press menu. Source-style #107 merged into #76 at `a3733fed0baeec3c335f4beefba1f1e2b333712c`. The previous comparable full smoke [39057577820](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39057577820) ran for approximately **16 minutes** wall time (18:19:21Z–18:35:22Z).
- The first quick-gate attempt [39059678989](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39059678989) failed **before Android test script execution** because the ReactiveCircus runner evaluated a multiline `if ... fi` as a one-line shell command, yielding `Syntax error: end of file unexpected (expecting "fi")`. Corrected to a one-line script `bash tools/run_android_ui_profile.sh ...`. Also corrected Markdown backticks that Bash had treated as command substitution in the GITHUB_STEP_SUMMARY section. Python/Android build at first head [39059679077](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39059679077) passed.
- Follow-up exact #108 head `39523cd0767c119628b15797d5f74bf1095ea020` had both APK [39059963201](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39059963201) and UI [39059963180](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39059963180) end in **failure without executable job steps or downloadable logs**. PR #76 new merged-head audit/APK/UI runs `39060144730`, `39060144738`, `39060144805` also failed immediately without steps. A single retry of #108 UI run `39059963180` (attempt 2) failed the same way. **Reason is not confirmed**: GitHub-hosted runner/account/quota/service restriction is possible, but it is not evidence of broken FBD code. Do not mark quick CI PASS or spend money to troubleshoot. Main remains untouched.
- Therefore **quick suite elapsed time has not been measured yet**. Its smaller UI scope should reduce time spent navigating the app, but Android SDK preparation and emulator startup remain expensive. An actual green quick Android result is required before adopting the gate, and a full smoke remains required before stable merge.

## Why Android UI verification is slow

The prior `.github/workflows/android-ui-smoke.yml` ran `tools/android_ui_smoke.py` for every new pull-request revision, even if a change affected only an FBD label or canvas color. Every CI runner starts clean, provisions Android SDK/build-tools and Gradle, builds the debug APK and a test input-driver JAR, boots an API30/x86_64 emulator with KVM, performs accessibility `uiautomator dump` repeatedly, drives the 800+-line UI suite with save/reopen/image-provider/canvas actions, then uploads captures. GitHub's `cancel-in-progress: true` automatically terminates obsolete intermediate head runs. Such cancellations are **not** successful verification and rerunning all UI scenarios after every small commit consumes runner time.

## Two explicit verification tiers

| Where | Profile | Coverage |
| --- | --- | --- |
| Feature PR targeting another feature branch, e.g. #107 or CI experiments | `quick` (automatic) | Real API30 emulator: new original FBD palette AND+TON, block-specific long-press menu and Cancel without mutation, exact 2345 ms, typed Q→IN, BOOL inverter, undo, cold project restart, crash log |
| PR targeting stable `main`, push to `main` | `full` (automatic) | Unmodified `tools/android_ui_smoke.py`: original palette/editor, image library, DocumentsUI files, all FBD geometry/wire editing, copying, restart, Modbus-related UI, operator workflow, deleted/legacy media migration and crash checks |
| `workflow_dispatch` on any branch | `full` by default; `quick` selectable | Explicit final verification before integrating feature branches |
| Android JUnit and debug APK workflow | Unchanged | All Python source tests and Java JUnit plus compile + debug artifact for each meaningful head |

**Exact-source CI gate policy:** feature→feature merges require green exact-head Android APK/JUnit AND quick UI, plus a manually dispatched **full** run at the *same head* for FBD integration into #76. PR #76 targeting `main` runs full automatically. For release or stable main merge, do not rely on only the quick profile. If the full test is red or missing, no merge, sign or release.

The quick test does not exercise the legacy-media/DocsUI/copy-clipboard flows, and should never be reported as equivalent to full. Both profiles preserve crash captures and `result.json` indicating `profile: quick-fbd` for the focused variant. The workflow step summary records profile and commit.

## What this change does NOT do

- No disabling of unit tests, Android debug builds, or full UI acceptance.
- No risky Android emulator snapshot cache: cached AVD states can be outdated or mask cold-boot bugs, so only test scope is shortened.
- No agent API, paid subscription, release signing, or production PLC writes.
- No change to FBD runtime behavior or current saved projects.

## Workflow steps when continuing later

1. Read [FBD issue #75](https://github.com/skynet909084-boop/BLOODSKY-Android/issues/75), working [PR #76](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/76), recent source-style PR #107 and this doc. The accepted #76 baseline `87f1aa1ada09580a1bfd7568bf0f060dd09aec58` passed all independent full gates; do not reimplement #98–106.
2. Check latest FBD source-style PR #107 and merge only after its *exact-head* full UI and JUnit/APK passed. If it has a newer commit after checks, re-run.
3. Validate this fast-gate branch `ci/fbd-fast-emulator-gate-20261017` with exact-head Python/JUnit/APK and its **quick** UI; inspect `result.json`, screenshots, and time. Fix any reliability errors without weakening assertions.
4. Only then merge this CI change into the approved FBD feature branch; if necessary run the **full** profile via Actions → BLOODSKY Android UI smoke → Run workflow → `ui_profile=full`. Then normal PR #76 to `main` will automatically invoke full.
5. Record exact SHA, run links, passed/failed/blocked status and measurements in issue #75 and `docs/FBD_EXECUTION_HANDOFF_20261017.md`. Move to editor, tags, Modbus and alarms after FBD working branch accepted, not from scratch.

This policy follows the user's preference for a few **large, verified batches**, direct development without idle agent waiting, and durable repository-based continuity instead of chat-only progress.
