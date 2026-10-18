# Component interaction audit (2026-10-18)

Reference: `reference/craft-reference.zip` (SHA-256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`). Decompiled source is used as a navigation aid; ambiguous behavior should be checked in Smali before parity claims. BLOODSKY observations below refer to PR #143 HEAD `a75f3d55ccfe6a0a87e01ec7ff55e79a0fb12e63`.

## Shared tap and hold routes

The Craft canvas attaches a shared click listener to regular widgets. In editor mode, `mbCanvas.onClickView(View)` selects the view, refreshes selection chrome, and calls `r.h0.l(...)` to open the component-specific property editor. A long press in editor mode routes to the shared drag listener: the view is hidden during the drag, the trash target is shown, and the drop path either moves it or deletes it. FBD items use a separate click listener for their block properties; they share the editor drag path.

BLOODSKY's canvas routes a short editor tap to `editSelectedWidget()`; the property form contains the common caption/data-binding fields plus type-specific controls. Moving or resizing commits on release. A stationary hold (550 ms) opens a contextual action menu; FBD blocks get the FBD-specific menu. This is an additional BLOODSKY shortcut, while the original hold gesture is used to begin dragging. The existing toolbar and selection-action controls remain available for commands.

## Operator-mode component taps

Only four component IDs have a dedicated branch in Craft's `onClickView` operator-mode switch. The rest do not open the generic editor from this switch.

| Craft ID | Decompiled route and purpose | BLOODSKY route at audited HEAD | Status |
|---:|---|---|---|
| 1 | `CustomComponents.y`: toggle button state; use local application register for special device 100, otherwise send the configured device command. Missing device binding shows a connection toast. | `Кнопка` routes through `activateRuntimeWidget`; local state and configured Modbus writes are handled, including rollback on write failure. Craft momentary buttons have a separate press/release path. | Implemented for configured local behavior; no claim of device-by-device visual parity. |
| 8 | `CustomComponents.i`: open numeric-value dialog with min/max, integer/decimal input, optional password, text filter and commit callback. It refuses an unbound non-local input. | `Числовой ввод Craft` routes to `openCraftNumericInput`, with explicit enablement, encrypted password storage, range/type checks and optional local Modbus write. | Implemented; visual and mocked-peer acceptance remain separately tracked. |
| 15 | CustomComponents.d: operator tap opens archive settings; actions route to CSV export, graph and preferences. It stores timestamped channels in SQLite. | Partial local implementation: import preserves ID 15 as Архив Craft; user supplies a local TCP register; polling samples at a configurable interval into bounded SQLite history; operator tap opens history and Android document picker exports CSV. Imported device links remain disabled. Multi-channel/trigger/reset semantics and the original graph are not yet ported; SMTP/cloud are intentionally excluded. |
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
- Top-right three-dot menu: original mbCanvas binds the app-bar button to r.f0, which inflates the dark menu_ dialog. Ordered rows route to: device/server list (PrefActivityNetworkDevice); project password (mbCanvas.f0, legacy plaintext project pass); station/mobile mode (Z0 and stationMode); timeout visibility (g1 and showTimeOut); Wi-Fi-to-USB bridge (hidden by default, no click branch); network control (r.g0 / bottomsheet_remote_control: Modbus slave port and proprietary cloud flags); background work (MyApplication.f9046n gate).
- BLOODSKY's icon opens a platform PopupMenu in showEditorCommands(), with project, screen, FBD, integrations, Modbus diagnostics and home actions. Project settings can store station/Modbus values but do not start a Modbus slave server; diagnostics are not remote control. There is no secure project lock, timeout display or background service. These rows cannot safely map to look-alike actions. The legacy password is plaintext and cloud control is outside local-first scope. Menu structure audit is complete; visual/action parity for unavailable backends remains open.
- Per-widget property form: common caption and data-binding fields; type-specific settings include indicator images, Craft numeric/bit-list/linear-setter options, media, and FBD controls.

## Remaining archive and component acceptance

The ID 15 baseline is implemented as one explicitly mapped local register, bounded per-widget history, interval sampling, a history dialog and CSV export. Remaining parity work: verify the source sampling trigger and channel decode against Smali/call sites, then add multi-channel and date/channel graph behavior where supported. Keep legacy SMTP/cloud disabled. ID 26 WebView remains absent pending a local-content navigation policy; ID 14 trend hit testing and pan/zoom parity remain unverified.

## Validation boundary

The existing PR checks and Android 11 smoke establish build/startup/editor/media stability. They do not exercise every widget ID or every three-dot/context-menu row. The new archive code needs CI compilation, emulator interaction and mock-peer sampling/export acceptance before it can be called visually or functionally at parity.
