# KaScada Craft 1.0.7 — visual parity ledger

**Source of truth:** decompiled `KaScada.Smart.Craft.1.0.7.08.02.2026.apk`, archived as GitHub Actions artifact `11912849123` (`BLOODSKY-Kaskada-decompiled`), workflow run `38600957172`.

## Confirmed from original Java source

- `jadx/sources/ru/himik/nightscream/kascada/Logic/FB_AND.java`: `onDraw` paints a solid color `-7829368` (ARGB `#FF888888`) rectangle inset by connector margins, draws a black outline, centers the block label, and places two input lines at 1/4 and 3/4 of the block height and one output line at the center.
- `Logic/FB_OR.java`: the same rectangular body and two-input layout.
- `Logic/FB_XOR.java`, `Logic/FB_RS.java`, `Logic/FB_TIMER.java`: same base fill/outline, but more elaborate internal subdivisions and text. **Not yet faithfully reproduced.**
- The block renderer is implemented in Java `onDraw`, not solely in PNG assets. Merely copying drawable files does not reproduce it.

## BLOODSKY implementation status

- Basic gray body, black outline, connector lines and centered label ported from the original FBD rendering. **Partial parity only**: additional input labels, special output inversion bubbles, FB-specific internal lines, pin sizes and text layout still require class-by-class porting.
- The original `apktool/res/drawable/*.png` resource images are imported at build time and used for several widget previews. An icon imported from the original does **not** establish parity of the complete widget.
- Menu structure, dialogs, canvas scaling, typography, FBD block variations, animation and operator mode have **not** been certified visually identical.

## Acceptance gate before custom design work

1. Compare every component against its original `jadx` rendering class and `apktool` XML/drawables.
2. Record expected geometry, colors, states, interactions and image assets.
3. Verify matching screenshots on equivalent Android density/resolution and automated behavior tests.
4. Keep additions or speculative redesigns out of the original-parity milestone.

Do not describe the app as a full clone until this gate is passed.
