# Component interaction audit (2026-10-18)

Reference: `reference/craft-reference.zip` (SHA-256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`). Decompiled source is used as a navigation aid; ambiguous behavior should be checked in Smali before parity claims. The follow-up below compares against PR #143 HEAD `6759340d8ad1f6000c394fbef4b5ecee8607ae28` and records new source findings.

## Shared tap and hold routes

The Craft canvas attaches a shared click listener to regular widgets. In editor mode, `mbCanvas.onClickView(View)` selects the view, refreshes selection chrome, and calls `r.h0.l(...)` to open the component-specific property editor. A long press in editor mode routes to the shared drag listener: the view is hidden during the drag, the trash target is shown, and the drop path either moves it or deletes it. FBD items use a separate click listener for their block properties; they share the editor drag path.

BLOODSKY's canvas routes a short editor tap to `editSelectedWidget()`; the property form contains the common caption/data-binding fields plus type-specific controls. Moving or resizing commits on release. A stationary hold (550 ms) opens a contextual action menu; FBD blocks get the FBD-specific menu. This is an additional BLOODSKY shortcut, while the original hold gesture is used to begin dragging. The existing toolbar and selection-action controls remain available for commands.


## Marquee selection and group movement

Source: `mbCanvas$k.smali` (canvas selection listener) and `mbCanvas$z.smali` (canvas drag listener). Dragging on the canvas selection surface draws a rectangular marquee. On release, the source checks each component's center point against that rectangle, skips connected `VarNameIn`/`VarNameOut` interface nodes, marks matching components selected, computes the group's union bounds, and stores each selected component's offset from the union's top-left corner. Dragging the resulting `RelativeLayout` selection box repositions every selected component from those offsets, so mixed widgets and FBD components preserve their relative layout.

At the audited BLOODSKY head, the canvas only hit-tests a single widget and updates that widget's geometry during drag; a drag starting on empty canvas does not create a selection. This is a concrete editor parity gap. The incremental implementation adds a center-based marquee, visible selection borders, and group translation while retaining single-widget resize and tap/property routes. Group movement is clamped to the canvas and keeps the existing grid snap. The existing copy/cut/align toolbar actions still operate on the primary selected widget; bulk actions remain separate work. Unit tests cover marquee geometry and group-bound clamping. Full visual acceptance remains open.

## Operator-mode component taps

Only four component IDs have a dedicated branch in Craft's `onClickView` operator-mode switch. The rest do not open the generic editor from this switch.

| Craft ID | Decompiled route and purpose | BLOODSKY route at audited HEAD | Status |
|---:|---|---|---|
| 1 | `CustomComponents.y`: toggle button state; use local application register for special device 100, otherwise send the configured device command. Missing device binding shows a connection toast. | `Кнопка` routes through `activateRuntimeWidget`; local state and configured Modbus writes are handled, including rollback on write failure. Craft momentary buttons have a separate press/release path. | Implemented for configured local behavior; no claim of device-by-device visual parity. |
| 8 | `CustomComponents.i`: open numeric-value dialog with min/max, integer/decimal input, optional password, text filter and commit callback. It refuses an unbound non-local input. | `Числовой ввод Craft` routes to `openCraftNumericInput`, with explicit enablement, encrypted password storage, range/type checks and optional local Modbus write. | Implemented; visual and mocked-peer acceptance remain separately tracked. |
| 15 | `CustomComponents.d`: operator tap opens archive settings; actions route to CSV export, graph and preferences. SQLite rows contain `var1`–`var5`, `_time`, `_date`. `countChannel` selects 1–4 numeric channels; `INT` consumes one register per channel and `FLOAT` two. `setValue(1)` rearms a one-shot trigger read; the trigger response dispatches the archive sample read. Optional ASCII/float/integer text channel and clear trigger have separate settings/handlers. The source bounds growth by deleting old rows after 100,000. | Local implementation reads 1–4 contiguous INT/FLOAT channels, accepts an explicitly remapped local rising-edge trigger or an interval fallback, stores bounded SQLite rows, shows recent history and a simple multichannel graph, and exports CSV via Android's document picker. Imported device bindings stay inactive. Optional string channel, reset trigger, source-compatible date/channel graph controls and exact original database interchange remain open; remote archive delivery is excluded. |
| 20 | `CustomList`: show the 16-bit list dialog; selecting bits changes the mask and sends it through the selected local/device register path, subject to the “no change” setting. | `Список Craft` routes to `openCraftBitList`; configured local masks persist, and explicit Modbus bindings write FC06. Read-only mode is supported. | Implemented for configured local behavior. |

Other component tags have no dedicated branch in the operator click switch, but several still process touch inside the widget. Their touch path must be audited separately before calling them display-only.

## Component-specific touch paths outside the click switch

| Craft ID | Source path | BLOODSKY comparison / consequence |
|---:|---|---|
| 13 | `CustomComponents.v` is a `SeekBar`; it enables touch, tracks ACTION_DOWN/MOVE/UP, and uses a delayed long-press callback for editor drag. Its `onProgressChanged` is empty, so this source alone does **not** establish a device write when the thumb moves. | BLOODSKY has a Craft linear setter with explicit local Modbus behavior. Treat that as a local feature; do not claim the original performs the same write until the enclosing call path is verified. |
| 14 | `CustomComponents.o` is the trend/chart view. It records the touch coordinate on ACTION_DOWN and uses a delayed long-press callback for editor drag. The component's graph action is separately reachable from the archive dialog. | BLOODSKY's trend is rendered on canvas; hit testing, point inspection and pan/zoom parity are not established. |
| 26 | `CustomComponents.b0` is a WebView. It enables JavaScript and zoom, installs a WebViewClient/WebChromeClient, and its touch listener returns false so WebView gestures/navigation continue while selection state is updated. Its data update can load a URL from a selected bit. | No matching WebView component is present in the audited BLOODSKY palette. This is an open component gap; do not copy the original unrestricted JavaScript/URL behavior without an explicit local-content and navigation policy. |

The remaining custom widget classes in the archive only capture the initial touch point in `dispatchTouchEvent` and return to the superclass; their component action still routes through the shared canvas listeners. The source scan covered every Java file under `CustomComponents/`, excluding the base interface and shared data model.

## Context-menu and properties routes in BLOODSKY

- Standard widget hold menu: **Свойства**, **Выравнивание**, **Копировать**, **Удалить**. Each row has a direct callback in `showStandardWidgetContextMenu()`.
- FBD hold menu: connection, BOOL inversion, disconnect, macro output/graph actions when applicable, timer settings, properties, and the standard “other actions” route. Type/pin guards filter unavailable actions.
- Top-right three-dot menu: original mbCanvas binds the app-bar button to r.f0, which inflates the dark `menu_` dialog. Ordered rows route to: device/server list (`PrefActivityNetworkDevice`); project password (`mbCanvas.f0`, legacy plaintext project pass); station/mobile mode (`Z0` and `stationMode`); timeout visibility (`g1` and `showTimeOut`); Wi-Fi-to-USB bridge (hidden by default, no click branch); network control (`r.g0` / `bottomsheet_remote_control`: Modbus slave port); background work (`MyApplication.f9046n` gate).
- BLOODSKY now opens a dark anchored popup with the same visible row order (the hidden Wi-Fi-to-USB row stays hidden). The endpoint row lists TCP bindings read-only; the station row changes only saved project metadata; project lock, timeout overlay, Modbus slave server and background service stay disabled and explain why. “Дополнительные действия BLOODSKY…” opens the existing project/screen/FBD/integration/diagnostic actions. This restores the menu hierarchy and visible style, not the missing backends. The original password is plaintext; only explicitly configured local integrations are supported.
- Per-widget property form: common caption and data-binding fields; type-specific settings include indicator images, Craft numeric/bit-list/linear-setter options, media, and FBD controls.

## Remaining archive and component acceptance

The archive source call sites in `mbCanvas` dispatch type 15 for the main archive value and types 150–154 for archive history, reset, channel reads and optional text-channel reads. The local decoder covers contiguous INT/FLOAT words and the optional rising-edge register trigger; this is a local remap, not the original device-index transport. Android 11 smoke #561 exercised the palette item, local-only properties, channel history dialog and saved widget; screenshots are attached to the run. Remaining ID 15 work: optional ASCII/string channel, reset input, source-compatible date/channel graph controls, exact SQLite interchange and mock Modbus peer acceptance. Keep imported legacy links and remote delivery disabled. ID 26 WebView remains absent pending a local-content navigation policy; ID 14 trend hit testing and pan/zoom parity remain unverified.

## Validation boundary

Android APK run #900 passed the unit suite and debug build. Android 11 smoke #561 passed the three-dot menu path and ID 15 palette/property/history path; its screenshots are in the run artifact. Follow-up #562 checks wrapped menu captions and accessible ID 15 channel controls. This still does not establish every widget ID, source interaction parity, mock-peer behavior or full side-by-side visual parity.
