# BLOODSKY original Craft FB_COMPARE — six signed int32 modes

**Status:** source-backed implementation and regression test in draft PR; not merged into stable `main`.

Original SHA-pinned class `FB_COMPARE.java` sha256 `07e789fe1da43fe7d07330bd33defe3cc5f3994b622c99c512005f3fcf9d5f8d`, source method `f()` lines 54–103: BOOL result from signed Java `Integer` operands and mode `e0` values 1 (`>`), 2 (`<`), 3 (`<=`), 4 (`>=`), 5 (`==`), 6 (`!=`). Missing source connections: original `getOut()` can return null; existing BLOODSKY graph has only Boolean false on absence. Output may invert with `f8973e` source option, while BLOODSKY already supports per-wire BOOL inversion, not a native Q-property toggle.

New `FBD CRAFT COMPARE` extended FBD block has distinct ID; typed signed-integral numeric `IN1`, `IN2`, Boolean `Q`. Source default comparator `>` and all six canonical operator strings persist in saved JSON `compareOperator`. Editing the operator preserves native block ID and gray original-like source skin. Numeric values must be exact finite signed int32, verified with `FbdCraftIntegerSelector.exactInteger`; fractional/infinite/out-of-range/missing inputs fail closed as BOOL false. The existing generalized `FBD Сравнение` A/B double comparison remains untouched (no silent behavior change).

`FbdCraftIntegerCompareTest` exercises all six modes, signed extremes, invalid mode rejection, two named numerical wires from original uint16 CTU, absent operand, source terminals and project round-trip. Source canvas skin test covers gray appearance and original input labels. This does not enable the original `FB_COMPARE` palette icon until its real Android click path and visual/semantic review pass.

This PR is stacked on source-backed Craft Integer SEL port; both need their own exact-head Android JUnit/APK and emulator SUCCESS before integration into FBD feature [#76](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/76). The last green FBD stable `main` is deliberately unchanged; field Modbus/PLC source-parity remains unverified.
