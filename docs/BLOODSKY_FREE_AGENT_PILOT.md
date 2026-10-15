# BLOODSKY: no-paid-subscription coding-agent pilot

## What is already automated
The existing Android CI validates changes and builds debug APKs. The hourly ChatGPT check is a separate status check; it is not a continuously running coding agent.

## Zero-spend agent choice
First try **Google Jules** through its GitHub repository integration if a free allowance is available for this account. Free allowances and eligibility may change; do not assume unlimited runs. Jules must be connected and authorized by the repository owner in its own UI. Never enter a credit card or enable a paid plan for this pilot.

Fallback: continue with GitHub Actions for tests/builds and manual or ChatGPT-assisted GitHub code edits. GitHub Actions alone is not an AI coding agent. Do not enable paid API models or install agents that silently require billed tokens.

## Pilot issue
Use #22 (Android lifecycle save/reopen regression tests) as the first narrowly scoped task. A successful pilot must:
1. Produce a dedicated branch and a draft PR targeting main, with links to the task and test evidence.
2. Keep source compatibility and original project files safe.
3. Execute Python tests, Android JVM tests and assembleDebug; add an instrumentation/Robolectric test where feasible.
4. Stop after at most two attempted fixes if CI fails. Never auto-merge.
5. Report any limits or unavailable credentials instead of substituting paid services.

## Owner activation (one-time)
1. Sign in to the official Jules service and connect GitHub repository `skynet909084-boop/BLOODSKY-Android` with minimum permissions.
2. Confirm that the account currently offers free runs and that no payment method is required.
3. Start one task with issue #22 as the scope, requesting a draft PR and test results.
4. Inspect the first run and its permissions before allowing any further automation.

## Boundaries
No provider has been authenticated or started by adding this file. No cloud coding-agent workflow is enabled until the account owner completes the external authorization. Keep main protected, signing secrets unavailable, and production release manual.
