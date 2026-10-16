# Legacy PR consolidation — 2026-10-16

Baseline main: `139fcd28b55c551af48501820e969c7c922bed5a` (PR34 squash merge); independent APK build #278 succeeded with APK and test-report artifacts. This inventory covers the 15 legacy open PRs observed before Batch 4 produces its own PR. Their unique work is NOT declared implemented or tested merely because a newer PR was merged.

Active implementation successor: issue #35 and Jules session 3155993989279481968. Original scope/acceptance tracking in #28/#31/#22 remains open. Consolidation closes obsolete PR presentations as superseded, not as completed; source branches and exact head SHAs below are retained for selective porting and can be reopened. Do not delete these branches until unique work is accounted for.

| PR | Preserved head SHA | Preserved branch | Disposition / remaining work |
|---|---|---|---|
| #27 | `b8c8a0059ce78c976867afa44fd53ffac1a521c9` | `test/android-lifecycle-persistence-issue22` | Resource tooling/reference docs recovered separately. Lifecycle/Robolectric/codec/UI changes need selective port against current main; do not merge the obsolete Activity wholesale. |
| #26 | `dca15963ce1b7cd2dac8e0ebb1201c75901d00e5` | `test/project-screen-edit-roundtrip` | Multi-screen edit/save tests and screen codec: candidate inputs for Batch 4 persistence work, not yet ported. |
| #25 | `eae92cd4e30ec399e0642685ed4450825f371d97` | `automation/instant-build-verification` | Extra build-completion verifier: optional redundant CI layer; current APK workflow already fails missing artifact uploads. No email delivery implementation here. |
| #23 | `e02e271a2444cfd6b4b25d87cef5d327b65c01e8` | `automation/free-agent-pilot` | Historical free-agent pilot documentation; Jules is already in use. Preserve as history, do not restart an extra agent. |
| #21 | `3c3a11a70437325d1597165501cbb51d468655d3` | `test/fbd-graph-validator` | FBD graph validation extraction and tests: compare with current inline production validation before porting. |
| #20 | `8c8838900a29f258c3c8d353c99a47f3f0121c8c` | `test/fbd-connection-codec` | Production FBD connection codec extraction/tests: evaluate with metadata preservation in Batch 4. |
| #19 | `860bd5630ee9d2630564987bc6b665cbde89687e` | `test/fbd-project-roundtrip` | FBD edited connection round-trip tests: preserve as candidate regression cases. |
| #18 | `b40e1faf16aea1890a8c3a8102203f4dc680eca7` | `feat/connection-metadata-preservation-6741818251318549274` | Unknown FBD connection metadata and JSON codecs: important unresolved persistence candidate, not evidence that main already preserves all edge fields. |
| #17 | `ef81a7d3364fe348515e5630c78b190cb97e6f3e` | `fix/fbd-connection-metadata-roundtrip` | Earlier FBD metadata fix; compare with PR18/20 and current main. Not the original-image PR. |
| #16 | `eb04377de3ac022ac43fec779369733ba038a761` | `test/project-json-metadata-copy` | JSON deep copy/codec/test extraction: evaluate against current serializer, do not blindly replace MainActivity. |
| #15 | `2fa06d424c189f2f101acf006c9c2b560a51923f` | `fix/modbus-tcp-validator-current-main` | Modbus TCP validator rework: evaluate current inline validation before retaining unique test cases. |
| #13 | `f186730fcef2f4c2d5400660a23f3fdebf31e60a` | `fix/modbus-register-address-normalization` | Address normalization helper/tests: evaluate boundary semantics against current main. |
| #7 | `5b810d25ee2106c060c3cd6495c4a181623d92d9` | `feat/modbus-tcp-validation-tests` | Earlier TCP validator work; compare with successor PR15 and current implementation. |
| #5 | `62aa8c1d92eafc1992a7d4cd030330e65c21e179` | `ui/kaskada-editor-toolbar-parity` | Earlier toolbar/vector icon attempt: use original Craft assets and screenshot evidence for Batch 4, not arbitrary replacement icons. |
| #4 | `18f1e3a5c87da617939e8973012ce0a97a073cfa` | `tests/project-document-validation` | Project validation/metadata helper/tests: preserve unique malformed-input cases when consolidating. |

## Resource recovery

The conflicting PR27 contains useful original resource tooling that was absent from main. The recovery PR brings only its independent catalogue/bundling/audit tools and historical reference notes forward, plus a pinned reference ZIP and preparation workflow. It intentionally does not replace Activity, codecs, dependency versions or current tests with an older branch version. See reference/README.md for provenance and limits.

## Batch 4 handoff

1. Fetch the current main after resource recovery. Run tools/prepare_craft_resources.py locally before comparing/rendering assets.
2. Inspect all old PRs relevant to the feature before writing a replacement. Retrieve files using preserved head refs above even if the PR is closed.
3. Prioritize original images/icons/states/thumbnails and real screenshot comparisons. Bundled assets alone do not establish parity.
4. Port unique metadata/lifecycle/FBD/Modbus regression cases with production code integration, preserving PR34 media pickers, all 16 state editors, validated settings, and compatibility.
5. Report per legacy PR: already covered / selectively ported with tests / rejected with reason / still pending. Do not mark all old tasks complete just because their PRs are closed.

## Validation of recovery

Local preparation: 1,377 original resource/asset files catalogued; 152 compiled image aliases and 14 sound aliases. XML map: 2,081 references, 140 unresolved; drawable literal-wiring audit: 151 PNGs, zero explicit references in baseline UI. Local tools unittest suite: 41 passed (the full repository suite in CI also includes tests not downloaded into this local checkout). Gradle and Android SDK are unavailable locally; JVM tests and APK build must pass independent CI before integration. No physical-device test or visual equivalence claim.
