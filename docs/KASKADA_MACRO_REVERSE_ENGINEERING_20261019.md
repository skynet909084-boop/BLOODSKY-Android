# Kaskada Craft 1.0.7 macro editor findings

Updated: 2026-10-19

## Runtime-confirmed route

CI emulator probe run [#80](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39274597156) follows a fresh project through the original app:

1. Open the component palette, switch to FBD, then long-press and drag the `tab_fb_macro` tile onto the canvas.
2. The original opens a `Create a new macro` dialog.
3. Entering `MacroTest` and tapping `Create` closes the dialog and opens a dedicated macro graph canvas.
4. That canvas visibly renders four input pins (`in 1` through `in 4`) on the left and one output pin (`out`) on the right. Its top-left control returns; its bottom-left `menu_bar_component` plus button opens the FBD/component palette.
5. The palette opened from the macro canvas shows variable input/output entries and the original FBD, logic and element catalog. The probe captured it as states 010–011.

Screenshots and UI XML are in the run artifact (state-006: creation form; state-009: macro canvas; state-010: palette). The artifact is retained by GitHub Actions for seven days. The runtime probe did not yet test dragging a child block from this palette, returning/saving the graph, or reopening the saved project.

The user also reports that a drag over empty canvas draws a selection rectangle for selecting several mixed objects, and a long-press can drag an individual block. This is user-provided behavior and remains to be independently checked against the original.

## Creation form and IDs

The runtime hierarchy exposes these original IDs:

| Purpose | ID |
| --- | --- |
| Dialog title | `title` |
| Macro name field | `nameMacro` |
| Input rows | `item_in1` … `item_in4` |
| Input names | `eName` within each input row |
| Type choices | `ch_B`, `ch_I`, `ch_F` (Bool, Int, Float) |
| Output row | `item_out` |
| Create action | `bCreateMacro` |
| Canvas palette button | `menu_bar_component` |
| Canvas return button | `menu_app_bar_show` |

The dialog defaults each input name to `in 1` … `in 4` and defaults Bool on. Source listeners make Bool/Int/Float mutually exclusive within each port row. The output defaults to `out`. The name field is limited to ten characters in the original layout.

Source paths in the pinned decompilation:

- `ru/himik/nightscream/kascada/r/b0.smali` — macro dialog wiring, type-choice listeners and create handler.
- `ru/himik/nightscream/kascada/Logic/FB_MACRO.smali` — macro block type and serialized properties.
- `ru/himik/nightscream/kascada/Logic/c/e.smali` — base FBD component and macro-port metadata.

## Saved macro model

The original FBD type ID is **135** (`FB_MACRO`). Its JSON includes a macro name and unique ID, input count, four named/type-tagged inputs, the named/type-tagged output, and per-port connection keys (`keyOutIn1` … `keyOutIn4`, `saveKeyOut`). The base macro object also carries `countIn`, `numOnIn*`, `typeIn*`, `nameIn*`, `typeOut` and `nameOut`.

The nested graph is serialized under `fbInMacros`. The original collects non-macro components whose root macro unique ID matches the owning macro, then stores each child component's normal properties plus `componentId`, canvas `x/y`, root macro name `nMcr` and root unique ID `unIdMcr`. It filters macro-port variable nodes from that child list; their names, types and links are represented by the owner's input/output fields. Register-oriented children may add `reg` and `bit`.

This means a faithful importer must preserve both the macro's public typed ports and the separately serialized child graph. Treating ID 135 as an ordinary visible block loses behavior and graph structure.

## BLOODSKY gap and next port steps

The current parity matrix marks ID 135 unsupported. BLOODSKY can edit and run BLOODSKY-authored nested graphs, and preserves opaque original macro child records, but those original children are not executed.

Next implementation work:

1. Model the original macro ports and `fbInMacros` children without dropping unknown properties or IDs.
2. Map only child component IDs whose factory, properties, pin types and runtime behavior are already source-verified; keep unknown child records visible and fail-closed.
3. Match original pin names, types, input count and connection keys when rendering imported macros.
4. Verify import/save/reopen and nested execution using a small source-derived macro fixture before enabling execution for imported macros.
5. Add the macro editor palette and the original canvas selection/drag gestures to runtime parity checks.

Do not infer that the macro canvas saves on return until the save/reopen path has been captured.
