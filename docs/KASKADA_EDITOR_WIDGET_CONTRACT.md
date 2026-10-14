# Kaskada Craft editor and widget contract — first source pass

Source APK: Kaskada Craft 1.0.7  
Decompiled artifact: [workflow run 38600957172](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38600957172/artifacts/11912849123)

This is a first-pass source map to guide implementation in BLOODSKY. It records verified class/method names and serialized keys; it is not a claim that these behaviors are already implemented in BLOODSKY.

## 1. Editor control points in `mbCanvas.java`

The decompiled class has 15,614 lines. Useful named entry points include:

| Method | Verified source location | Likely responsibility from method name/context |
|---|---:|---|
| `m(...)` | 4704 | Creates/returns a custom component from type and configuration arguments |
| `l(String, int, int, int)` | 3513 | Component/editor operation; inspect call sites before assigning exact meaning |
| `Resizing(View)` | 9794 | Resize selected view |
| `StartResize(View)` | 9831 | Begin resize gesture |
| `alignSelectView(View)` | 9855 | Align selected view(s) |
| `deleteView(View)` | 10161 | Delete view from editor |
| `clickScreenLeft(View)` / `clickScreenRight(View)` | 10148–10152 | Change screen |
| `selectActionCopy(View)` | 14213 | Copy selection |
| `selectActionCut(View)` | 14236 | Cut selection |
| `selectActionPaste(View)` | 14308 | Paste selection |
| `openDialogAlignComponents(View)` | 13986 | Open alignment dialog |
| `onClickWorkToolbar(View)` | 13709 | Toolbar work/operator-mode action |
| `lockScreen(View)` | 12650 | Screen lock action |
| `onClickSetNameScreen(View)` | 13461 | Rename current screen |
| `onCreate(Bundle)` | 13719 | Activity setup |

The table intentionally distinguishes verified names from inferred responsibilities. Next, trace callers/callees and the dialogs/resources around each entry point, especially the component factory and persistence path.

## 2. Shared widget contract

Text controls and the list widget implement the common interface `CustomComponents.z`. It exposes shared concepts including:
- component ID and screen number;
- x/y position and width/height;
- display text and custom visibility;
- connection metadata;
- additional JSON metadata via `getJsonOther()`;
- connection JSON via `getJsonConnectionInfo()`;
- `getJsonOtherForCopy()` for copying widget configuration.

This suggests BLOODSKY should use a common component model and per-widget property schema, rather than hard-coding unrelated UI objects.

## 3. Text label: `CustomLabel`

Source: `CustomComponents/CustomLabel.java`

Confirmed metadata serialized by `getJsonOther()`:
- `numScreen`
- `autoResizeText`
- `enableFrameLabel`
- `timeZ`

The class exposes custom text, text size, font, text/background color, line spacing, maximum lines, auto-resize, selection frame and screen-number controls. Its connection JSON is empty, consistent with a static label rather than a directly register-bound display element.

Implementation implication: text editing must update the component's actual text property, persist it, and restore it on project reload; a dialog that only changes a temporary input will not be sufficient.

## 4. Dynamic text: `CustomDynText`

Confirmed connection metadata:
- `posDeviceNetworkInList`
- `addressRegister`
- `addressBit`
- `typeRequest`

Confirmed other metadata:
- `sizeText`, `colorText`
- `text0` through `text15`
- `colorBackground0` through `colorBackground15`
- `typeCommand`
- `numScreen`
- `autoResizeText`
- `enableFrameDynText`
- `timeZ`

This widget supports multiple text/background states and is linked to a device/register request. A compatible BLOODSKY implementation should preserve every state label/color, the command type, and the register/bit binding. Do not reduce it to a plain text label.

## 5. List/bit-oriented widget: `CustomList`

Confirmed connection metadata:
- `posDeviceNetworkInList`
- `addressRegister`
- `typeRequest`

Confirmed other metadata:
- `numScreen`
- `textBit`
- `visibleInWork`
- `checkNoChange`
- `timeZ`

The source also exposes bit-state helpers and methods for assigning bit arrays and register values. This points to a bit/word-oriented widget whose behavior depends on both communication data and UI state. Verify exact bit order, signedness, and register decoding from the full call chain before implementing protocol conversion.

## 6. Recommended implementation order

1. Build a canonical BLOODSKY component model: type, stable ID, screen, bounds, z-order, text/style, connection binding, widget-specific JSON/properties.
2. Implement load/save round trips before adding more widgets.
3. Add static label and dynamic text with the metadata above.
4. Add the bit/list widget after documenting bit numbering and request types.
5. Trace and port editor operations (select, resize, align, copy/cut/paste, delete, screen navigation).
6. Validate every feature using original-app project fixtures and regression tests.

Compatibility note: preserve unknown JSON fields during import/export when possible. This allows BLOODSKY to retain settings it does not yet understand instead of silently destroying them.
