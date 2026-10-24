# Craft macro input: exact port type codes (22 October 2026)

The original FB_MACRO interface uses 1=Boolean, 2=Integer, 3=Float.
BLOODSKY carries that value as craftMacroPortType for executable
FBD CRAFT MACRO IN widgets. Runtime pin introspection previously used
JSONObject.optInt, which silently wraps huge JSON integers (e.g.
4294967297 -> 1) and truncates fractions (2.9 -> 2). This can incorrectly
admit a graph connection across original signal types even when the
stored macro definition is invalid.

The runtime now reads with CraftExactInteger.read, retaining valid integer
and numeric-string types, and returns an unavailable pin for unsupported
or malformed type metadata. It never mutates imported values or guesses
a replacement. Regression covers valid 1/2/3, signed extremes, large
numeric aliases, noninteger source formats and missing type codes.

This is a source-backed correctness guard rather than a claim of
visual, hardware or full macro behavior parity.
