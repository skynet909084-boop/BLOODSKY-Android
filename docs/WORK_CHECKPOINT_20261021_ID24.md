# Work checkpoint — 21 Oct 2026

- PR #214 already merged into integration: exact head ac905f78 passed APK/JUnit run 39551468656. No development Android quick/full smoke; preserve main release gates.
- Reviewed and merged PR #224, head 081a784d, merge aede7db4. ID24 now has a dedicated canvas renderer driven by imported/editor scale and indicator settings. Exact-head run 39557848233 passed Python, Android unit tests, APK build/upload. No unresolved review threads returned.
- Follow-up fixes ID24 integer overflow for signed ranges wider than INT_MAX, retaining integer tick rounding. JVM regressions cover INT32 endpoints, every supported division count, midpoint, clamp and invalid values. Project JSON is unchanged.
- Local prepared-resource Python suite: 189 passed. Initial unprepared run failed due to missing generated references; preparing the pinned resource archive resolved it. Gradle unit/APK NOT RUN locally: Gradle unavailable. Require new exact-head GitHub CI before merging the fix.
- Android smoke, same-density visual comparison, real imported-project and hardware acceptance NOT RUN. Unified matrix remains 5/139 = 3.6%; no acceptance weights/statuses changed.
- Research tooling stack #198/#200/#201/#202/#203/#206 is deferred. Avoid restoring its older automatic original-probe policy. Open #188/#192/#197 require ancestry/diff reconciliation before integration; do not duplicate already integrated changes.
- Next product task: inspect original Craft ID14 CustomComponents.o graph sampling, four channel names, period, point count and graph type in the kascada-analysis-1 source release. Compare current importer/runtime first, then implement missing source-confirmed behavior with JVM tests and APK build. No Android emulator or original traversal during active development.
- Stable main is unchanged. Source and checks remain traceable through the PRs above. Git CLI push lacks credentials in this session; publish through the authorized GitHub plugin.
