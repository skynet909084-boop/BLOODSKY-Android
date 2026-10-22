# Craft VarName FLOAT32 precision — 22 October 2026

## The mismatch

KaScada's named `VarNameIn` and `VarNameOut` palette uses three distinct
pin types: BOOL (1), INT32 (2), and FLOAT32 (3), as recorded in
`docs/FBD_CRAFT_REGISTER_BLOCKS_20261018.md`. BLOODSKY evaluates numeric
FBD graph values as Java doubles. Previously, its transient named-variable
bank kept those doubles unchanged, so writing 0.1 through a FLOAT32 pin and
reading it from another block could give double-precision 0.1 instead of
the value representable in IEEE-754 Float32.

## Change

At the typed named-variable store boundary, FLOAT32 writes are now rounded
to Java `float` **once**, then represented as a double for the graph.
This preserves source-type precision, signed zero, subnormal values, and
underflow. Non-finite and out-of-range source values still fail closed
without overwriting a previous valid value. BOOL and INT32 rules are
unchanged. Global names and macro-instance namespaces remain distinct.

This updates the same unified Android application and does not change
project files, stored widget settings, original assets or FBD names.

## Tests and scope

JUnit regressions cover Float32 0.1, signed negative zero and underflow,
min/max finite values, rejected overflow/NaN/infinity, and nested macro
scope isolation alongside BOOL/INT32. Device scheduling/visual parity and
physical PLC scenarios are **not** established by these pure-store tests.
