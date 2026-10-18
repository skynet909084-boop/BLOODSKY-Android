# Kaskada Craft — component registry and editor operations

This note extends the first-pass editor/widget map using verified code paths in `mbCanvas.java`. Component tags below are the integer values used by the deletion dispatcher; they are stable clues for mapping saved widget types to runtime classes, but still need to be cross-checked against project serialization and the component factory.

## Component tag registry observed in `deleteView(View)`

| Tag | Runtime component class |
|---:|---|
| 1 | `CustomComponents.y` |
| 2 | `CustomLabel` |
| 4 | `CustomComponents.u` |
| 5 | `CustomComponents.q` |
| 6 | `CustomComponents.e` |
| 7 | `CustomDynText` |
| 8 | `CustomComponents.i` |
| 9 | `CustomComponents.c` |
| 10 | `CustomComponents.w` |
| 11 | `CustomComponents.r` |
| 12 | `CustomComponents.g` |
| 13 | `CustomComponents.v` |
| 14 | `CustomComponents.o` |
| 15 | `CustomComponents.d` |
| 18 | `CustomComponents.x` |
| 19 | `CustomComponents.k` |
| 20 | `CustomList` |
| 21 | `CustomSound` |
| 22 | `CustomComponents.l` |
| 23 | `CustomComponents.f` |
| 24 | `CustomComponents.m` |
| 25 | `CustomComponents.t` |
| 26 | `CustomComponents.b0` |
| 27 | `CustomComponents.a0` |
| 28 | `CustomComponents.j` |

Tags 3, 16 and 17 do not appear in this particular switch. Do not infer that these values are invalid; other component families are handled by separate checks. The deletion routine removes the component from a corresponding application-level collection, removes the view from its parent, and invalidates the FBD drawing surface for logic blocks.

## Confirmed editor behaviors

- Screen navigation: `clickScreenLeft(View)` calls `s(false)`; `clickScreenRight(View)` calls `s(true)`.
- Resize operations have explicit `StartResize(View)` and `Resizing(View)` entry points.
- Alignment has `alignSelectView(View)` and `openDialogAlignComponents(View)`.
- Copy/cut/paste have separate toolbar handlers. Paste reconstructs a widget through the shared factory `m(...)`, passing its text, dimensions, position, text size, connection JSON, text color, background, and additional JSON.
- When pasted, the copied component is moved to the current screen and marked selected. The code also adjusts connection fields during paste, so BLOODSKY should not assume that a duplicate must blindly retain every original device binding; determine the original UX semantics before implementing this detail.
- The editor uses a shared interface and application-level collections keyed by component ID. BLOODSKY should use stable IDs and a central registry so delete/copy/selection cannot leave stale references.

## Porting caution

Craft ID 20 is implemented as `Список Craft`: sixteen independently selectable labels backed by a UINT16 mask, matching `CustomList` (`textBit0`–`textBit15`, `getValue`, `setValue`, and `l(bit)`). Imported labels and both `visibleInWork` / `checkNoChange` flags survive conversion. Imported device IDs remain unbound until an operator enters an explicit TCP holding-register endpoint; the runtime reads FC03 and writes the mask with FC06. Empty labels are unavailable, and `checkNoChange` makes the operator list read-only. Register/manual/hardware parity beyond this TCP mapping remains open.

JADX emits type-inference warnings around parts of the large factory and paste code. Use Smali and call-site analysis to resolve ambiguous branches before treating the factory's full tag mapping as definitive. Preserve unknown type IDs and unknown JSON fields during project import whenever possible.
