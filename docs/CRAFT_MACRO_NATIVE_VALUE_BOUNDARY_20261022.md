# Craft macro native typed value boundary — October 22, 2026

FB_MACRO interface codes in the pinned original KaScada Craft 1.0.7
source are 1=Boolean, 2=Java Integer, 3=Java Float. BLOODSKY's generic
FBD engine caches numeric signals as Java double. Prior macro input
and output paths forwarded these doubles unchanged across INT/FLOAT
macro ports. This admitted fractional/out-of-range values as Integer
and preserved excess precision not representable as original Float32.

The new FbdCraftMacroValueCodec normalizes every numeric macro input
and output to its native source type:
- INT32: only finite, exactly integral values inside signed Java int.
- Float32: single Java float cast (including signed zero and underflow),
  with non-finite and overflowing values unavailable.
- BOOL: remains on the existing separate truth/availability path.

Malformed or unavailable numeric input does not propagate as an
apparently valid value. No externally connected device or FBD block
changes its representation: conversion occurs only at the Craft macro
interface boundary, preserving the source .mcr format and all metadata.

JVM tests cover precision, signed zero, integer limits, invalid
values and a complete Float32 pass-through macro runtime. Physical
hardware and visual acceptance remain unverified.
