# Craft arithmetic and Float32 comparison batch — 18 Oct 2026

This batch stacks on the active FBD integration line from #76. Stable `main` remains unchanged.

## Source contracts transferred

The contracts are pinned to `reference/craft-reference.zip` SHA256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`:

| Craft class | Contract | BLOODSKY block |
| --- | --- | --- |
| `FB_ARITHMETIC_INT` (SHA256 `2914b05cbda9f9b0a845b9d65682bc93f77c20fd4965eeb459f76b471953ae8f`) | Signed Integer inputs; actions `+`, `−`, `×`, `÷`; result stored in Java `char` (unsigned 16-bit wrap); divide-by-zero is caught and yields zero. | `FBD CRAFT ARITHMETIC INT` |
| `FB_ARITHMETIC_FLOAT` (SHA256 `3385ba4c8a1df430d3ffe4ac64e3cc284ab10a7c7130c49171d18a2e95946192`) | Float32 inputs/results; IEEE division behavior, including infinities and NaN. | `FBD CRAFT ARITHMETIC FLOAT` |
| `FB_COMPARE_FLOAT` (SHA256 `6658837671a8c6471da23d8b996ea0620948b0702ea44b0729ee14d3083b75ad`) | Float32 inputs; six source modes `>`, `<`, `<=`, `>=`, `==`, `!=`; source branch behavior makes NaN true for the first four and `!=`, false for `==`. | `FBD CRAFT COMPARE FLOAT` |

The three blocks are available from the original Craft palette and extended FBD picker, expose typed named `IN1`/`IN2` and `Q` terminals, use original quarter/three-quarter input geometry and center output geometry, and preserve mode/type through project JSON. The existing integer `COMPARE` remains a separate signed-INT32 block. The native `FB_TRIGGER` palette action now exposes its source modes T/R/F: T toggles on rising edges, R is a rising-edge pulse, and F is a falling-edge pulse. The selected mode persists in the block's extension metadata.

## Verification

`FbdCraftMathTest` covers integer wrap and divide-by-zero, Float32/IEEE behavior, all NaN comparison modes, source tag mappings, typed pins, source canvas geometry, named graph evaluation, and mode persistence. The final local Python suite passed 135 tests; `git diff --check` and smoke-script byte compilation passed. Local Gradle is unavailable. Exact-head APK/JUnit and quick Android UI smoke are running for combined PR #139 head `f8f1409e1614005ed2feba18a19d2f1d4c6af4ba`.

The source register blocks `FB_MX` and `FB_ARRAY` are now integrated with a bounded transient Craft register bank; see `docs/FBD_CRAFT_MX_ARRAY_20261018.md`. `FB_MACRO` still needs persisted executable nested-graph storage and remains disabled. This does not claim full FBD completion or pixel-perfect comparison against Craft.
