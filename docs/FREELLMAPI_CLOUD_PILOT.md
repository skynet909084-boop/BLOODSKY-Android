# BLOODSKY cloud AI pilot

This branch is an isolated pilot for cloud-based AI development. It does **not** activate autonomous code changes or alter the stable Android build.

## Architecture

GitHub Actions (ephemeral runner) -> optional FreeLLMAPI router (container on same runner) -> coding agent -> reviewable pull request -> existing Android tests and APK build.

FreeLLMAPI is a model router, not a coding agent. A provider API key and agent configuration are required. Provider free-tier limits and terms apply. No paid account should be enabled by default.

## Rollout gates

1. Inspect the upstream FreeLLMAPI release and license, pin its source or container by immutable digest, and audit setup scripts before execution.
2. Configure provider credentials only as GitHub Actions secrets; never write keys to tracked files or logs. Keep the router bound to loopback.
3. Test connectivity in a manual, read-only workflow with no write token, limited permissions, timeout, and concurrency controls.
4. Introduce a coding agent on a disposable branch with scoped token permissions, a strict run budget, and a bounded retry count.
5. Require the existing Python source-contract tests, Android unit tests, APK build, and emulator smoke tests. Preserve logs/artifacts for inspection.
6. Create a PR rather than pushing directly to main; require human approval for merging and release signing.

## Operational constraints

GitHub-hosted runners are temporary. Their router database and keys disappear after each run unless explicitly persisted. Do not cache encrypted credential databases or export secrets as artifacts. A permanently hosted router would require a separate service, but a per-job router is enough for this pilot.

Private repositories may have GitHub Actions minute limits or charges. Stop when free usage is exhausted; do not silently enable paid usage.

## Current state

Architecture documented; no credentials configured, no agent installed, and no workflow activated. Next step is a read-only, manual connectivity proof after credentials are available.
