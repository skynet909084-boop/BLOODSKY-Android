# Original Craft native SEL and COMPARE port positions (draw9 and draw15)

The pinned original Craft `OriginalFbdDrawing.java` class contains two different drawing methods, independently corroborated with source class SHA evidence. This PR only changes native, **versioned** block geometry—not legacy BLOODSKY pin locations or project files.

- `FB_SEL` maps to source `draw9`: 1/4 header height; BOOL `IN` at **3/8**, signed numeric `I1` at **5/8**, signed numeric `I2` at **7/8**, numeric Q at **3/8**. A vertical split below the header and text labels are drawn by the native gray Canvas skin.
- `FB_COMPARE` maps to source `draw15`: no header divider; numeric `IN1` at **1/4**, `IN2` at **3/4**, BOOL Q at **1/2**. Retains gray native body and six-mode title (`>`, `<`, `<=`, `>=`, `==`, `!=`).
- Use the same `MainActivity.fbdTerminalFraction` for wire endpoints and `FbdSourceCanvasSkin.inputY` for the Canvas contacts; a line should meet its pin even after manual resizing or moving.
- Unit checks are in `FbdSourceNativeCanvasSkinTest`. Existing Craft CTU16 (3/8,5/8,7/8), RS (1/2,5/6), T (1/2) and old generic FBD blocks are unchanged.

This visual parity slice is not pixel-perfect: original preview tiles are smaller than editable HMI widgets and original integer-rounded Canvas geometry may differ by under a pixel. Merge only after exact Android APK/JUnit and API30 UI passes; preserve draft #76 and stable main.
