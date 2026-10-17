# Anonymous inference proof (separate from the deterministic fixture)

This follow-up tests actual AI generation through FreeLLMAPI using the documented anonymous OVHcloud endpoint. No account, provider API key, card, paid project or billing setting is supplied. The selected provider is fixed to `ovh`, model `gpt-oss-120b`; all other models/providers/fallback rows are disabled before requesting a completion. Authenticated OVHcloud accounts are outside this workflow.

Official references checked 2026-10-17:

- [Getting started and anonymous limits](https://docs.ovhcloud.com/en/guides/public-cloud/ai-machine-learning/ai-endpoints-getting-started): anonymous access is limited to two requests per minute per IP/model; authenticated keys require a project/payment method. This proof does not create such a project/key.
- [Free API and data handling](https://www.ovhcloud.com/en/public-cloud/free-ai-api/): anonymous requests have limits, account trials are distinct, and OVHcloud states that inference data is not retained or used for training.
- The pinned FreeLLMAPI provider registers OVH as keyless and omits provider authorization for its sentinel key.

The owner-triggered PR/manual workflow makes one client request with a 1024-token output budget and low reasoning effort. It sends only the selected BLOODSKY helper and repository coding rules, never an archive, key or complete source tree. No retries, account creation or paid fallback is configured. Provider availability and anonymous quotas can change: an error blocks the proof instead of purchasing capacity or switching services.

The pinned router bundles the OVH adapter but not its model rows. Startup therefore declares the officially documented model through FreeLLMAPI's supported `models` configuration, with a two-request-per-minute local limit. It does not require a Premium catalog feed or enable automatic catalog changes.

The response must pass the same test-only allowlist. A fresh runner then executes the full Python suite, Android JUnit tests, verifies each proposed class actually ran passing tests, and builds an APK. Generation and validation have no branch/PR write token. Existing deterministic router CI remains independent. The per-run class name prevents a later task from colliding with an already accepted generated test.

## Automatic draft publication without a PAT

After the publisher workflow is accepted on `main`, a successful **push/manual run on main** can trigger `.github/workflows/cloud-ai-publish.yml`. Pull-request runs are validation-only artifacts and cannot enter the privileged publisher. The publisher always checks out trusted `main`, never executes proposed Java, and uses only its short-lived repository `GITHUB_TOKEN`. It accepts source commits already in main's history and refuses publication if application, tools, Gradle or agent rules changed since validation.

The publisher downloads only the specific successful run's proposal, checks its mode/pinned-router evidence/patch digest, and reapplies the test-only allowlist. It creates at most one `automation/cloud-agent-RUN_ID` branch and one draft PR. No force push, approval, merge or signing is performed. APK/unit, API30 emulator and preflight workflows are explicitly dispatched on that branch, since token-created PR events may otherwise wait for approval. Independent CI and human review remain required.

GitHub must permit Actions to create PRs: **Settings → Actions → General → Allow GitHub Actions to create and approve pull requests**. This is a repository policy setting, not a provider account or payment method; the workflow never approves PRs. If GitHub blocks creation, the job fails and retains the concrete validated branch. No personal access token, provider secret, paid account or billing change is needed for this anonymous path.

Owner operation: run **BLOODSKY anonymous free AI proof** on `main` with one bounded task. Accepted changes to the agent/router/publisher workflows or selected helper also trigger the default bounded task automatically. After inference and independent validation succeed, the publisher creates the draft and starts its checks. Generated test-only changes do not match the push filters, preventing recursive task creation. No scheduled self-selected product work or automatic merge is enabled by this pilot.

Until a successful `BLOODSKY anonymous free AI proof` run and its artifacts have been inspected, real inference is **not verified**. A successful fixture run alone is insufficient. This workflow is experimental development tooling and is not an inference service bundled in the Android application.


## Focused FBD task profiles

The anonymous workflow accepts a reviewed `profile` choice: `fbd-duration` (default), `fbd-rollback`, or `properties`. The profile selects fixed, bounded Java helper/test context; a task override cannot select files, providers, shell commands or production edits. The generated class includes the profile and workflow run ID. Exactly one router/model request remains the limit, with no automatic repair. `task-plan.json` records the chosen profile and context alongside the proposal; independent validation still requires the generated test to execute successfully and the debug APK to build.

The default FBD task covers exact units and unchanged rounded-minute edits, replacing repetitive timer-maximum-only proposals. Changes to either FBD helper trigger the main loop, while generated tests do not. The `fbd-rollback` task targets independent metadata snapshots after a false save or throwing mutation. These contexts use executable existing tests as API examples without uploading the oversized Activity source.

First main-origin proof: [39016400413](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39016400413) passed 108 Python checks, 135 Android JUnit tests (including two newly generated tests), and APK assembly. The publisher pushed branch `automation/cloud-agent-39016400413`, but repository policy rejected bot PR creation. [Draft PR #73](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/73) was opened through the authorized GitHub connector. Unattended publication remains blocked until the repository owner enables the documented Actions PR permission; no admin setting or account credential was changed.
