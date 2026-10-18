# Original Craft FB_MOVE source transfer

Pinned archive: `reference/craft-reference.zip`, SHA-256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`.

`FB_MOVE` exposes BOOL `EN`, Java Integer `I`, and Java Integer `Q`. Its source `getOut()` is null unless both inputs are connected, both outputs are present, and EN is true; otherwise it returns the exact Integer input. BLOODSKY has a new, distinct `FBD CRAFT MOVE` type so the existing generic `FBD MOVE` remains compatible with older projects.

The native block exposes typed named pins, accepts only finite exact signed-INT32 numeric values, and represents source null/invalid output with a transient NaN cache so downstream numeric source-aware blocks fail closed. Its original preview is enabled in both orientation layouts. The Canvas uses the original draw8 three-section split with EN at 1/2, I at 5/6 and Q at 1/2. No existing saved FBD block is migrated.

`FbdCraftMoveTest` covers enable/missing-input behavior and Integer limits. `OriginalFbdNativePaletteMappingTest` checks preview mapping, typed terminals, and wire geometry. The real Android smoke adds the block from the original source palette and verifies all source-backed palette type IDs and non-overlapping placement after restart. This remains a source-compatible local graph block; remote PLC/runtime transport parity is outside this change.
