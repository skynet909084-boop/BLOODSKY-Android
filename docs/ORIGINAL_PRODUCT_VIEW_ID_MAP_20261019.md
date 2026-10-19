# Craft 1.0.7 product-view ID map — working checkpoint

Date: 2026-10-19. This is a source/layout map for the high-value user-facing screens. It is not an assertion that all 1,110 public `id` resources have been implemented in BLOODSKY.

## Inventory and matching rule

- `public.xml`: 5,230 public resource symbols, of which 1,110 are type `id`.
- Original `res/layout`: 273 layouts, 759 distinct `@id`/`@+id` names.
- Decompiled Java: 526 distinct `R.id` references across all packages; 498 references remain after restricting to Craft's `ru.himik.nightscream.kascada` package for the machine-readable audit.
- The counts differ because some public IDs are not used in a layout, some layout references are generated elsewhere, and many belong to AndroidX/third-party widgets. Numeric resource integers are APK-local; compare symbolic names and handlers.
- BLOODSKY namespaced copied source-layout IDs (for example, `kc_picker_tab_button`) instead of reusing original numeric IDs. Compare names, event handlers, state and screenshots.

## Screen groups checked against original source

| Original layout | Unique IDs in layout | Source behavior checked | BLOODSKY correspondence | Status |
|---|---:|---|---|---|
| `main_menu.xml` | 12 | `p.java` dispatches `bGoToCanvas` and menu actions; `MainActivity.java` owns project list and create/import routes | Main screen, create/import, project list flows exist in `MainActivity` | Source/action map partial; all menu branches still need emulator confirmation |
| `activity_work_old.xml` | 21 | Editor host IDs and action wiring in `mbCanvas.java` | `kc_editor_toolbar.xml` and `OriginalEditorToolbar`; toolbar actions bind hide/run/previous/next/rename/calculator/menu | Core toolbar IDs mapped; canvas selection/resize/context flows not fully accepted |
| `activity_work_portrait.xml` | 24 | Same editor family with portrait-only `calc`, `layoutProgress`, and `pdf_doc` | Namespaced toolbar resources reuse the original hierarchy/art | `pdf_doc` intentionally dimmed; hidden/invisible controls need matching runtime states |
| `fragment_component_bar.xml` | 30 total (27 palette IDs + 3 shell IDs) | `t/b.java` binds category switching/long-press; `mbCanvas.java` dispatches tile IDs to widget factories | `OriginalComponentPalette` has explicit actions for 26 visible rows; original `tab_email` is `gone` | Source and code mapping checked; captured palette screenshots exist in interrupted 128-state run, not latest quick run |
| `fragment_component_logic_bar.xml` | 43 total (40 FBD tile IDs + 3 shell IDs) | `t/c.java` sets FBD long-press handlers; `mbCanvas.java` dispatches each `tab_fb_*` tile, including macro | `OriginalFbdPalette` binds 39 entries; `tab_fb_macro` remains dimmed pending nested graph/library acceptance | Source mapping checked; no original emulator screenshot of this catalogue yet |
| `create_macro.xml` | 8 | `r/b0.java` reads macro name, four typed input slots, one output, then Create | BLOODSKY has macro data/graph helpers, but this original screen workflow is not proven end-to-end | Layout/source mapped; original runtime screen not yet captured by the long probe |
| `fragment_tile_macros.xml` | 4 | `t/d/b.java` owns the saved macro library and empty state | No full original `.mcr` library parity claim | Layout/source mapped; no original runtime capture or disposable `.mcr` fixture |
| `layout_device_network.xml` | 74 | `PrefActivityNetworkDevice.java` binds 16 address/name/server rows, device count and +/- controls | Project importer preserves source network records; complete 16-slot editor/selector is not implemented | Source inventory checked; no visual/runtime parity claim |
| `server_list.xml` | 4 | Server/transport list and add action | BLOODSKY does not yet have a complete Craft-compatible multi-transport manager | Source inventory checked; implementation gap |
| `dialog_*.xml` (50 layouts) | 118 distinct IDs across the family | `r/h0.java` owns most widget property dialogs; `r/e0.java` handles typed FBD variable/register dialogs; `mbCanvas.java` owns exit confirmation | Several BLOODSKY property forms exist, but not all original fields and validation states are ported | Inventory/source ownership checked; field-by-field and runtime parity remain open |

## Confirmed row binding boundaries

- **Component palette:** explicit rows are `tab_alarm`, `tab_archive`, `tab_background`, `tab_button`, `tab_camera`, `tab_digital_indicator`, `tab_digital_setters`, `tab_dyn_pictures`, `tab_dyn_text`, `tab_email`, `tab_gateway`, `tab_gateway_bit`, `tab_gif`, `tab_graph`, `tab_lamp`, `tab_line_level`, `tab_line_setter`, `tab_list`, `tab_push_notif`, `tab_round_indicator`, `tab_sms_in`, `tab_sms_out`, `tab_sound`, `tab_switch`, `tab_text`, `tab_voice_recognition`, and `tab_web_view`. Of these, 26 visible rows have explicit BLOODSKY bindings or disabled-state handling; email is hidden by the original.
- **FBD palette:** 40 source tiles are visible in the portrait/wide layout family. 39 have explicit BLOODSKY actions. The macro tile is the outstanding row and is deliberately disabled until nested graph editing, save/reload, and instance refresh are accepted.
- **Editor toolbar:** `OriginalEditorToolbar` explicitly binds original-style namespaced IDs for hide, run/stop, previous/next screen, rename, calculator and project menu. The PDF/document button remains disabled. This is a code mapping, not proof that every toolbar action matches original screenshots at every density.
- **Network device rows:** source layout repeats `editAddressDeviceN`, `editNameDeviceN`, `textServerDeviceN`, and `tableRowN` for N=1…16. Preserve the source row index and endpoint fields when importing; `countDevice` alone is not a replacement for the 16 explicit row numbers.

## Still not mapped exhaustively

1. Property dialogs for every component, including per-field validation, defaults, enabled state and save/cancel behavior.
2. Every network/transport setting view, Bluetooth permission path, USB picker, QR/import error and operator-mode control.
3. AndroidX/framework IDs and decorative `@id` constraints that are not product controls.
4. One-to-one runtime evidence for each source control. ID presence in `public.xml` or a click case in decompiled code alone does not prove a visible or working control.

The original UI runner now uses a single-session, checkpointed traversal with a 5h45m budget and a 300-state ceiling. Its `ui-audit.json`, per-state XML and screenshots will determine which source rows were actually reached; the execution cap is not a promise that every possible screen is reachable.

## Full public-ID cross-reference checkpoint

`ORIGINAL_VIEW_ID_INVENTORY_20261019.csv` now contains one row for each of the 1,110 public type-`id` symbols. The generator checks original layout references, Craft-package Java references, BLOODSKY name candidates, and (when supplied) runtime hierarchy XML. The output currently has 307 public-only/unclassified IDs, 152 framework or library candidates, 572 product/source IDs without a BLOODSKY name candidate, and 79 IDs with name candidates that still need behavior review. The candidate count is deliberately not presented as parity: BLOODSKY prefixes IDs, so this first-pass rule only looks for exact names and known prefixes.

The emulator evidence was still running when this checkpoint was generated, so the CSV has no runtime IDs yet. “Checked all IDs” here means every public symbol was included and cross-referenced; it does not mean every symbol has been semantically matched, clicked, or proven in the emulator. The next pass should use the completed runtime trees/screenshots to mark observed controls, then map the 572 unmatched product/source IDs by feature and handler rather than expanding fuzzy name matching.
