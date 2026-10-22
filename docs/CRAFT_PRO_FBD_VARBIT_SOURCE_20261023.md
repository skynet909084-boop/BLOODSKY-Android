# Exact original Craft .pro FBD variable bit parsing

KaScada 1.0.7 Var and VarOut BOOL blocks ID101/102 use
a 0..15 bit position in one 16-bit source register. BLOODSKY
imports original Var/VarOut and INT/FLOAT variants through the
same craftVarBit metadata field. The prior .pro importer used
JSONObject.optInt, silently wrapping huge integers and
truncating fractions into unrelated native bit positions.

Import now parses the source `bit` with CraftExactInteger.
For source BOOL Var/VarOut the native bit must be 0..15.
For INT/FLOAT Var variants the exact int32 metadata is kept,
without inventing a range for unverified original meaning.
Malformed source records fail import instead of accidentally
reading/writing a different bit. Missing `bit` keeps the
existing zero fallback.

JVM regression covers positive native BOOL/INT/FLOAT values,
BOOL boundaries, malformed null/arrays, overlarge numeric JSON,
fractional source fields and .pro export/re-import preserving
unknown original vendor metadata.

This fix does not assert external Modbus hardware parity or
pixel-matched original FBD appearance.
