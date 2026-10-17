# BLOODSKY cloud AI pilot

This branch is an isolated cloud coding pilot. It preserves the stable Android application. The first agent task may only add new JUnit regression tests; broader product edits remain outside this pilot.

## Architecture

GitHub Actions -> ephemeral FreeLLMAPI router -> bounded Python coding agent -> sanitized proposal artifact -> fresh validation runner -> optional isolated branch and draft pull request -> independent Android CI and emulator -> human approval.

FreeLLMAPI is the model router; `tools/cloud_coding_agent.py` is the coding agent. The agent uses the OpenAI-compatible `/v1/chat/completions` endpoint and accepts JSON file proposals, never model-supplied shell commands. No paid service, Premium license, hosted router subscription, automatic merge, or release signing is enabled.

## Why runs were previously unconfirmed

The first workflow had only `workflow_dispatch`. A new dispatch workflow must exist on the default branch before GitHub offers manual dispatch; merely adding it to a PR does not run it. Commit `7892939` added the `pull_request` trigger. Exact head `eedd963` has confirmed successful preflight run [39011103827](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39011103827) and Android build run [39011103848](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39011103848). These establish static validation/build only, not provider inference. Earlier emulator runs were superseded by successive PR updates.

Both cloud workflows now watch the cloud implementation, tests and documentation. Preflight concurrency is scoped per PR/ref so one branch cannot cancel another branch's check. Keep each final commit unchanged until its checks complete.

## Reproducible router contract

`.github/workflows/cloud-ai-agent.yml` automatically runs fixture mode on relevant PR changes. It builds [FreeLLMAPI v0.15.0](https://github.com/tashfeenahmed/freellmapi/tree/7ae0ef193d297c02479efed2a07946c44457512b) from immutable source commit `7ae0ef193d297c02479efed2a07946c44457512b` using its Dockerfile and dependency lockfile. The upstream license is MIT. Docker startup and declarative-config code were inspected; no piped installer or automatic updater is used. This is a source pin, not a claim that base images or every transitive dependency have been security audited.

The real router forwards a completion request to a deterministic local fixture on an internal Docker network without internet egress. Docker does not publish ports on an internal-only network, so a fixed-target Node relay on a separate bridge exposes only host loopback and forwards only to that router. The router and fixture retain their internal-only network. The response becomes two actual Android boundary tests. A separate runner revalidates the proposal, runs the full Python suite, Android JUnit tests and `assembleDebug`, and retains reports/APK. It also checks JUnit XML to ensure every proposed class actually executed passing tests without skips; successful compilation alone is insufficient. Fixture mode verifies routing, protocol, allowlist, patch generation and compilation; **it does not perform AI inference**. It never publishes a PR.

## Live free-provider setup

After this workflow is accepted onto the default branch, the repository owner can select **BLOODSKY FreeLLMAPI agent → Run workflow → mode: live**. Keep `publish: false` for the first inference proof.

Required repository configuration:

| Setting | Purpose |
| --- | --- |
| Actions secret `GROQ_FREE_API_KEY` | Key from a free-only Groq organization with billing disabled; never use a paid organization key. |
| Actions variable `BLOODSKY_FREE_TIER_CONFIRMED=true` | Owner confirmation that the account has no paid fallback/billing and that provider terms permit this personal experiment. |
| Optional Actions secret `BLOODSKY_AGENT_TOKEN` | Dedicated fine-grained token for this repository only, Contents and Pull requests read/write; required only for `publish: true`. No administration, workflow editing, signing or billing rights. |

Only `openai/gpt-oss-120b` on Groq is selected. Before the request, provisioning disables every other model, fallback row and provider key in the ephemeral database; a missing selected model stops the pilot. Its current free-plan availability was checked against [Groq rate limits](https://console.groq.com/docs/rate-limits) on 2026-10-17. Limits and availability can change; recheck the account before enabling. A FreeLLMAPI router cannot detect whether a supplied provider key belongs to a paid account. The confirmation setting is an operational gate, not a technical billing guarantee. Never enable billing to repair a quota error.

One run makes one agent completion request with a 4096-token output cap, 96 KiB source-context limit, 128 KiB response cap and 90-second client timeout. No agent retry/repair loop is enabled (zero attempts, below the contract maximum of two). Router-internal provider behavior is separate from this client budget. Quota, malformed, incomplete and oversized responses stop the run. Only the selected helper class and its existing tests plus repository instructions enter the prompt; environment secrets do not.

The proposal can add at most three `app/src/test/java/com/bloodsky/hmi/CloudAgent*Test.java` files. Existing files, production code, CI configuration, path traversal, symlinks, duplicate paths and ignored tests are rejected. This is a bounded pilot, not a general-purpose shell agent. Static checks do not prove generated tests are meaningful; review is still required.

Generation gets only the provider key. Validation runs in a fresh job without provider secrets or persisted checkout credentials. Publishing runs in another job, executes no proposed Java code, creates one `automation/cloud-agent-RUN_ID` branch and one draft PR, and never pushes to `main`. The dedicated token ensures the new PR triggers independent CI; GitHub's default `GITHUB_TOKEN` normally suppresses downstream workflow events. A rerun cannot force-overwrite an existing branch. CI or emulator failure keeps the draft blocked; human approval remains required for merge.

## Operational constraints

The router listens on host loopback only. Its generated encryption key, dashboard password, unified key, provider config and SQLite database are ephemeral. Containers/networks are removed in `finally`; the runner itself is disposable on timeout/cancellation. Router logs, raw provider bodies, databases and credentials are never uploaded. Artifacts contain only proposed source/patch, sanitized metadata and independent test/build reports, retained for seven days.

Private repositories may have GitHub Actions minute limits or charges. Stop when free usage is exhausted; do not silently enable paid usage.

## Current state

The static preflight is confirmed on `eedd963`. This implementation adds the real-router fixture and the manually gated live agent. Final-head run links and conclusions must be recorded after CI completes. Live inference, provider credentials, billing state and draft publication have not been verified in this session. Absence of a live run must never be reported as a successful inference. Android UI/physical-device and visual-parity claims remain governed by their existing checks.
