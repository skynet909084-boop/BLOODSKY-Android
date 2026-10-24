# Unambiguous executable Craft macro input names — October 22, 2026

KaScada Craft .mcr interface includes nameIn1..nameIn4 and typeIn1..4.
BLOODSKY's runtime uses case-insensitive pin matching, but executable
bloodskyGraph validation previously allowed duplicate input names that
differed only by case. As a result, an input wire could be routed to
the wrong source interface signal when editing or executing the macro.

For **executable sidecars only**, reject duplicate canonical input
names before validating child wiring. For original opaque .mcr records
without bloodskyGraph, preserve all original fields and input labels;
unknown original input-name policy must not cause lossy imports.

The legacy coarse MainActivity.knownFbdPinType helper also used
JSONObject.optInt on craftMacroPortType, bypassing the exact enum
parser added to FbdCraftPinTypes in PR #362. Both oracles now reject
out-of-range/fractional type values and refuse untrusted wire joins.

JVM tests cover case-fold/trim aliases, valid distinct inputs,
preservation of opaque source macros and real wire validation for
invalid BOOL aliases. No changes to original resources, transport,
project format or permissions. Android visual and real-device gates
remain unresolved.
