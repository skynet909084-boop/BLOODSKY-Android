# Legacy branch audit and Batch 5 handoff — 2026-10-16

## Scope and evidence

Read all 15 legacy PR patches listed in LEGACY_PR_CONSOLIDATION.md, checked the live branch inventory, and compared candidate behavior with main MainActivity.java, app/build.gradle and the APK workflow. All 15 named source branches are present in the returned inventory. Preserved source SHAs remain in the consolidation document; this audit does not certify unchanged branch heads.

Baseline: main after PR36 (d85b7c49a2e57f987d95b049a7ddf853b3a5f472). PR38 head reviewed separately: 804190858ad05a544cd95de3ccf25f956eb5df1a; further Jules work may supersede findings. This is a source review, not a new execution of historical tests. No legacy Activity, dependency or workflow was merged. Closed PRs are not completed features.

## Per-PR disposition

| PR | Disposition | Evidence and next action |
|---|---|---|
| #27 | Partial resource recovery; persistence tests pending | Resource recovery is documented in PR36. MainActivityPersistenceRobolectricTest has editedTwoScreenProjectSurvivesActivityRecreation and corruptedStoredProjectFallsBackWithoutOverwritingPreferences. Current build.gradle has JUnit/org.json only, no Robolectric. Adapt these tests after PR38 acceptance; do not copy its obsolete Activity. |
| #26 | Selective test candidate | ProjectScreenJsonCodecTest.saveReopenKeepsUnknownWidgetFieldsAndConnectionsAfterEditing covers two-screen edits and edge metadata. Port the scenario to the actual current persistence path, not only a disconnected codec. |
| #25 | Do not port now | Adds a second workflow_run verifier for APK existence. Current android-apk.yml already uses if-no-files-found:error for APK upload. Additional workflow adds no application coverage or Gmail delivery. Reconsider only for a specific missing check. |
| #23 | Historical only | Free-agent pilot documentation; do not start another competing agent. Jules is the active implementation owner. |
| #21 | Basic graph checks covered; stricter cases pending | Main inline validator checks cycles, bounds, duplicates and FBD endpoints. Old tests also reject string/fractional/overflow indices and non-array connections. Current optInt/optJSONArray coercion does not establish strict rejection. Adapt fixtures and decide compatibility policy explicitly. |
| #20 | Metadata codec candidate | FbdConnectionJsonCodec and deep-copy tests preserve nested unknown connection fields. Main FbdConnection holds only source/target; serializer rebuilds a fresh object. Port preservation plus production-path tests after PR38. |
| #19 | Regression scenario candidate | editedFbdConnectionRoundTripsWithPreservedMetadata edits endpoints while retaining vendorConnectionOption. Keep this scenario once, together with #18/#20 rather than three duplicate suites. |
| #18 | Confirmed unresolved preservation gap | Main reads edge source/target and discards other fields; persistProject writes only endpoints. Preserve unknown edge JSON through import, copy, edit, delete/reindex and save/reopen. Never claim full unknown-field round-trip today. |
| #17 | Superseded candidate, not completed | Earlier orthogonal connection metadata case. Deduplicate into #18/#19/#20 work. This PR is not the original-image resource PR. |
| #16 | Deep-copy tests candidate | JsonObjectCopyTest verifies nested values are not shared after copying; ProjectJsonCodecTest covers unknown project/screen/widget data. Main preserves these extraFields via JSON copies, but helper tests cannot prove current UI/persistence behavior. Adapt rather than copy unused classes. |
| #15 | Selective TCP tests candidate | Tests mismatched transaction/unit, protocol, declared length, wrong function, byte count, truncated and malformed exception responses. Main has inline validation, now also FC01/02. Port independent simulator cases against readModbusRegistersStatic; do not replace it with old FC03/04-only validator. |
| #13 | Address-boundary candidate | Tests 30001/40001 conventions, wrong-function notation and 65535+quantity overflow. Reconcile scanner, binding and RTU behavior first. Do not silently replace today's FC01/02 semantics with the older two-function helper. |
| #7 | Superseded by #15 for candidate tests | Earlier TCP validator with overlapping tests. Keep one current production-path suite. |
| #5 | Reject replacement art; retain workflow references | Custom bloodsky_icon_* vector toolbar attempt is not evidence of original Craft identity. Use pinned Craft resources, rendering source and paired screenshots; do not port arbitrary vectors as parity. |
| #4 | Strict validation cases candidate | ProjectDocumentValidatorTest includes noninteger geometry/indices, malformed structures, non-array connections and trailing content. Main already handles several limits and trailing JSON, but uses optInt for geometry/endpoints and optJSONArray for connections. Adapt unique invalid-input cases with explicit compatibility decisions. |

## Confirmed source findings

1. **Unknown FBD connection data is lost.** Main FbdConnection contains only two ints; loadProject constructs it from endpoints, saveActiveScreen/persistProject must carry any future metadata too. Example regression fixture: an edge with source/target plus vendorConnectionOption containing routing/bends/labels. Change endpoints, save and reopen; assert current endpoints and unchanged nested data, with detached JSON objects.
2. **Validation is permissive in places.** Main uses optInt for currentScreen, geometry and endpoints, and optJSONArray for connections. Old strict tests are valuable candidates, not proof of compatibility. Establish which numeric JSON forms Craft actually exports before rejecting previously accepted documents; preserve unknown fields.
3. **Lifecycle evidence is missing from the current JVM configuration.** returnDefaultValues=true with JUnit/org.json is not Activity recreation coverage. #27 provides scenarios to adapt using Robolectric or instrumentation, including corrupt input without overwriting stored data.
4. **Current Modbus supports more functions than legacy helpers.** readModbusRegistersStatic has FC01/02 bit decoding and FC03/04 register decoding. Keep this when consolidating tests; use local fake servers, not live hardware.
5. **No extra APK verifier is required for this audit.** Existing APK upload fails if output is missing; extra workflow success cannot establish UI, device, media or protocol correctness.

## Deduplicated implementation queue

### Owned by active Batch 4 / PR38
USB serial routing and configuration; portable ZIP/media rewriting and isolation; original picker/state resource wiring; deterministic FBD scheduling; Activity recreation; meaningful runtime-path regression coverage. These remain PR38 acceptance work, not newly completed Batch 5 features. The follow-up review 5543728786 is the source of current blockers. Do not change the same Activity concurrently.

### First coherent follow-up after PR38 acceptance
- Bring forward connection metadata preservation from #18/#20 with the edited-edge case #19 and multi-screen case #26.
- Adapt strict document/graph fixtures from #4/#21, documenting accepted numeric formats instead of relying on coercion.
- Consolidate current transport tests from #15/#13, retaining FC01/02 and independently checking boundary/error behavior.
- Reuse #27 recreation/corrupt-storage scenarios as acceptance tests, without duplicating already accepted PR38 coverage.

### Remaining Batch 5 scope
Real Craft export compatibility; complete component/property catalog; verified system register map; missing FBD block semantics; confirmed events/archives/integrations; actual paired visual and interaction evidence. Issue #37 retains the detailed scope. None is marked implemented by this audit.

## Verification and limitations

- All 15 legacy patches retrieved successfully; their changed-file lists and candidate assertions inspected.
- All 15 preserved branch names observed in GitHub inventory.
- Current main serializer, validator, TCP response path, Gradle test dependencies and APK workflow inspected.
- No Android/JVM/hardware/UI tests run for this documentation-only audit.
- No runtime files, workflows, dependencies, old branches, secrets, releases or PR38 code modified.
- Rebase implementation decisions on the accepted PR38 head before porting code; do not merge entire historical branches.
