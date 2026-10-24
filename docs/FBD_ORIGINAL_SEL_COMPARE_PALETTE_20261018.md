# Original Craft SEL / COMPARE preview activation

These two original static source-drawn tiles are enabled only on the versioned, source-researched BLOODSKY runtimes:

| Original palette tag | New editable widget | Typed pins | Source drawing |
| --- | --- | --- | --- |
| `FB_SEL` | `FBD CRAFT SEL` | BOOL IN; signed-int numeric I1,I2; numeric Q | `draw9`, 1/4 header, 3/8–5/8–7/8 inputs, Q at 3/8 |
| `FB_COMPARE` | `FBD CRAFT COMPARE` | signed-int numeric IN1,IN2; BOOL Q | `draw15`, IN1=1/4, IN2=3/4, Q=1/2 |

Original FB_SEL source returns Integer/null if any input is missing; BLOODSKY safely supplies 0 on missing/invalid operands. Original FB_COMPARE also has a null state and optional internal Boolean Q inversion flag; BLOODSKY uses false for missing inputs and supports per-wire Boolean Q inversion, not an intrinsic stored pin invert. Therefore these are **source-compatible improvements, not literal one-to-one original behavior**. Existing generic SEL and generic floating-point comparison are unchanged.

`OriginalFbdPalette.bindNative` performs a pinned source-view tag check before connecting a preview; unknown/unsupported original tiles remain disabled. Java `OriginalFbdNativePaletteMappingTest` verifies signed and Boolean port contracts. Actual Android quick smoke now scrolls the native original Craft portrait source palette, activates both original icons on an empty test project, screenshots the editor, force-stops the app and verifies exact saved IDs. A failure must block merging, never be declared visually accepted from Java unit tests alone.

This branch stays stacked on the source-backed SEL/COMPARE native integration candidate until exact-head Android APK/JUnit and API30 UI PASS; stable `main` remains untouched.
