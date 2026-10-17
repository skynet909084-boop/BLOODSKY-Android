# Craft original FBD contact geometry — draw8/draw9 parity checkpoint

This change is stacked on the crowded-screen placement and gray RS fix (PR #124) and is **not** a claim of complete original pixel parity. It follows the imported original source drawing methods in `app/src/main/java/com/bloodsky/hmi/OriginalFbdDrawing.java`.

## Source `draw8`: FB_RS and FB_TRIGGER

The original `draw8(Canvas)` renders a rectangular gray/black body with a header divider at ⅓ height and a vertical split below that header. Its two input terminal centers are at `(⅓ + ⅙) = ½` and `(⅔ + ⅙) = ⅚` of block height. The single output `Q` is at ½. The live BLOODSKY source-gray native RS uses R/S and Q at those exact **fractional** anchors; the native Craft T uses IN/Q at ½. All edit/drag connector endpoints use the shared `MainActivity.fbdTerminalFraction` so the wire and drawn pin are aligned.

## Source `draw9`: FB_CTU

The original `draw9(Canvas)` has a header divider at ¼ height, vertical lower-body split and terminal centers at `(¼ + ⅛) = ⅜` (+), `(½ + ⅛) = ⅝` (−), `(¾ + ⅛) = ⅞` (R). The source Q output is at ⅜. In the native **`FBD CRAFT CTU`** variant, the Canvas and the typed FBD wire terminal positions use these fractions and the default block title reads `CTU` (rather than displaying the diagnostic internal type label `CTU16`). **Legacy `FBD CTU`** CU/R/PV and Boolean threshold Q keep their previous layout and logic.

The source uses integer divisions of actual pixel height, so fractional positioning on custom-resized BLOODSKY blocks may differ by under one source pixel. Original preview dimensions (60dp×50dp) and the larger operator FBD widgets are not expected to be pixel-identical as a whole.

`FbdSourceCanvasSkin` now renders a genuine header and lower split for RS, T, CTU; visible pin labels and output label align with source wire endpoints. JUnit `FbdCraftVersionedCounterTest` and `FbdSourceNativeCanvasSkinTest` assert expected input and numeric-Q output fractions. Requires exact PR-head Android JUnit/debug APK and real Android API30 smoke before merging only into feature FBD #76. `main` stays unchanged.
