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
| 15 | `CustomComponents.d`: open its archive property flow. The editor branch inflates `dialog_archive`; its controls include archive register/device settings, reset and mail options, and CSV export. Runtime class `d` owns an SQLite `Archive` store and CSV generation. | No matching archive widget or archive property flow is present in the audited component palette/model. | **Open parity gap.** Keep cloud/mail behavior out of scope. A local archive needs its sampling, retention, CSV destination and lifecycle specified before adding a partial clone. |
| 20 | `CustomList`: show the 16-bit list dialog; selecting bits changes the mask and sends it through the selected local/device register path, subject to the “no change” setting. | `Список Craft` routes to `openCraftBitList`; configured local masks persist, and explicit Modbus bindings write FC06. Read-only mode is supported. | Implemented for configured local behavior. |

All other component tags in this click switch have no dedicated operator-mode action. They render or update from their bound values, with editor-only properties configured through the shared property route. Do not infer that they have click actions from their presence in the canvas.

## Context-menu and properties routes in BLOODSKY

- Standard widget hold menu: **Свойства**, **Выравнивание**, **Копировать**, **Удалить**. Each row has a direct callback in `showStandardWidgetContextMenu()`.
- FBD hold menu: connection, BOOL inversion, disconnect, macro output/graph actions when applicable, timer settings, properties, and the standard “other actions” route. Type/pin guards filter unavailable actions.
- Top-right editor menu: separately audited in PR #143 description. It is not a widget context menu.
- Per-widget property form: common caption and data-binding fields; type-specific settings include indicator images, Craft numeric/bit-list/linear-setter options, media, and FBD controls.

## Next concrete slice

Implement the missing local archive component only after its source routes are covered end to end: sampling trigger and register decoding, bounded retention, a visible history view, CSV export via Android's document picker, and project save/reopen. Exclude legacy SMTP/cloud upload and avoid treating the source app's project password or archive credentials as safe storage patterns. This is a larger component feature, not a missing tap callback.

## Validation boundary

The existing PR checks and Android 11 smoke establish build/startup/editor/media stability. They do not exercise every widget ID, every context-menu row, the archive lifecycle, or the numeric/bit-list Modbus interaction on a mock device. Keep those acceptance items open until exercised on the emulator or a test peer.
