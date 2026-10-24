# Original Craft emulator and ID coverage checkpoint — 19 October 2026

## Direct answer

The original Craft interface has **not** been fully traversed, and all 1,110 public Android
resource IDs have **not** been individually mapped to BLOODSKY. The existing emulator run was a
bounded quick probe, not full application acceptance. Android-generated integer resource IDs are
package-local, so matching their numeric values across two APKs would be meaningless; compare
symbolic names, source component IDs, behavior, and screenshots instead.

## What the original emulator evidence contains

Workflow run [#28](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39205169194),
artifact `12165887121`, used the `quick` profile and captured 18 unique states, 40 transitions,
and 18 screenshots. It completed in 703.1 seconds and recorded zero project-loading stalls.

The screenshots and UI trees cover the launch menu; project-name/orientation dialog; a blank newly
created editor project; the upload menu; Cloud-link import; QR scanner error dialog; file picker;
and exit confirmation. Some captures show a white “KaScada Service — version 1.0.7” heads-up
notification over the editor; source and screenshot evidence identify it as the foreground-service
notification, not an About dialog.
They do **not** cover the component palette, editor context menus, FBD catalogue, macro dialog or
library, project settings, widget property forms, operator mode, or a real project import. This run
cannot support a claim of complete interface review or original-vs-BLOODSKY visual parity.

An earlier interrupted run [#39168081863](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39168081863),
artifact `12153170860`, contains 128 screenshots and UI trees. I reviewed the contact sheets and
key captures `state-072`, `state-075`, and `state-088`. They show the component palette categories
Setters/Text/Indicators/Graphic/Other, a project-options menu, and navigation to screen 2. The
archive stopped before a final traversal report and contains repeated dialog edits and blank-canvas
states. Across its 128 UI trees, 79 distinct app resource-ID names appear; all 79 resolve to the
original `public.xml`, and 26 visible component-palette `tab_*` IDs are present. No FBD palette ID
appears. It does not show the FBD catalogue or macro workflow.

## Current extended run

Run [#29](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39209744161) rebuilt and installed the original APK, then failed before the Python probe started. The runner executes each line of its `script` input independently, so a multiline shell `if` did not work. The failure was in the workflow wrapper; no UI states were captured in this run.

Run [#30](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39210019878) is now in the original-UI emulator step with the wrapper fixed to a single command. The probe keeps one app session, explores up to 300 unique visible states (depth 12), saves each unique XML/screenshot immediately, updates JSON checkpoints, and is capped at 5 h 45 min to leave artifact-upload time under the six-hour GitHub-hosted job limit. It records whether traversal stopped at the wall-clock, state, depth, or per-screen action cap. Final state and screenshot totals are not available yet.

The live GitHub job currently reports the original APK rebuilt, KVM enabled, and step 11/12 (install, launch, traverse) still in progress. The separate BLOODSKY APK build for this revision completed successfully. A full 24-hour GitHub-hosted job is not available; this run uses the maximum practical single-job window and preserves artifacts at the end.

## Source ID inventories and current mappings

| ID family | Decompiled Craft source | BLOODSKY cross-check | Remaining gap |
|---|---|---|---|
| Component factory IDs 1–28 | Factory method `mbCanvas.m(...)`; source/Smali-derived matrix in `CRAFT_COMPONENT_ID_FACTORY_MATRIX_20261018.md`; reproducible extractor `tools/audit_craft_component_factory.py` | Import mappings and safe fallbacks are documented per ID | ID 3 has no branch in this factory and remains unresolved; this does not prove it is absent elsewhere. Per-ID visual/runtime/device acceptance is incomplete. |
| FBD palette rows | 40 source tile IDs in `fragment_component_logic_bar.xml` (same wide/portrait catalogue family) | 39 row IDs are explicitly bound in `OriginalFbdPalette`; `test_original_fbd_palette` passes source drawing/layout checks | `tab_fb_macro` / `FB_MACRO` is the sole unbound row and stays visibly disabled/dimmed pending full original `.mcr` and nested-runtime acceptance. |
| Main component palette rows | 27 `tab_*` IDs in both original component palette layouts | 26 visible IDs have explicit `OriginalComponentPalette` actions; source `tab_email` is `visibility="gone"` in the original and also remains hidden | Four visible features are intentionally disabled/dimmed in BLOODSKY: voice recognition, GIF, camera, and web page. A hidden email palette action does not have a BLOODSKY action. |
| Android view/resource IDs | `public.xml` lists 5,230 public resources, including 1,110 `type="id"`; 273 original layout XML files contain 759 distinct `@id` names; decompiled Java references 526 distinct `R.id` names | BLOODSKY uses its own resource namespace and prefixes generated original-style palette IDs with `kc_` | No exhaustive per-view mapping yet. Many source IDs belong to AndroidX/third-party UI and are not product controls; identify app-owned controls by layout/source call sites before mapping behavior. |

The exhaustive symbol pass now emits one CSV row for each of the 1,110 public IDs at
`ORIGINAL_VIEW_ID_INVENTORY_20261019.csv`. It finds 498 distinct Java ID references in the
Craft application package (526 across all packages), 307 public-only IDs, 152 framework/library
candidates, 572 product/source IDs with no first-pass BLOODSKY name candidate, and 79 product
IDs with name candidates still requiring handler/behavior review. Runtime XML is not yet attached
to this CSV because run #30 has not reached artifact upload. The inventory checks every symbol's
references; it does **not** mean each ID has been semantically mapped or emulator-verified.
The generator test passes, the full Python tool suite passes (150 tests), and `git diff --check`
is clean.

## What has been verified about enabled/dimmed palette rows

The source-contract test `test_every_thumbnail_is_a_drawing_and_only_explicit_supported_entries_are_enabled`
passes. In the FBD catalogue, mapped rows are explicitly enabled at full alpha, while the original
macro row remains dimmed. In the component catalogue, only the four unsupported visible functions
listed above are deliberately dimmed. This is a code/resource check, **not** screenshot evidence
that every icon appears bright and correctly positioned on an emulator.

## Next acceptance work

1. Add a directed original-emulator path that opens the editor menu, component palette, FBD page,
   macro setup, saved macro library, and settings; capture screenshot plus accessibility XML at each
   meaningful state instead of spending time on repeated dialog text edits.
2. Add a source-ID checklist for product-owned controls from the original layouts and handlers.
   Cross-check the 28 widget factory IDs and 40 FBD rows against current action bindings and explicit
   disabled states. Keep AndroidX/framework IDs categorized separately.
3. Compare matching Craft and BLOODSKY screens at the same viewport/density. Record unverified
   behavior as open; do not infer a successful interaction from an ID being present.
4. Obtain a disposable `.mcr` exported by the original app before asserting macro runtime parity.
5. Project-network comparison for the phone's `home2` and `дом 2025` exports is recorded separately in `HOME_PROJECT_NETWORK_CORRELATION_20261019.md`; live endpoint reachability and exact device-selector behavior remain unverified.
6. Product-owned layout ID families and BLOODSKY mapping boundaries are tracked in `ORIGINAL_PRODUCT_VIEW_ID_MAP_20261019.md`.
