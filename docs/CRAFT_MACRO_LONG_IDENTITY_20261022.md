# Exact 64-bit identity preservation for original Craft .mcr macros

Original Craft macro definitions use uniqueId and child unIdMcr to
identify which nested child belongs to which macro. This identity
is a Java long-style numeric field; values commonly exceed 32-bit
integer limits. The original .mcr data and these fields must be
round-tripped without narrowing.

BLOODSKY previously used JSONObject.optLong for both, which can
silently truncate fractions or wrap oversized values. A malformed
identity could therefore compare equal to an unrelated original
macro, authorizing a different child graph as if it belonged to
the current definition.

CraftExactLong.read requires a precise signed 64-bit integer.
Real JSON Long/BigDecimal and numeric strings remain supported;
Float/Double values beyond IEEE-754's exact integer range are
rejected rather than pretending their rounded values are exact.
Absent IDs retain their existing optional fallback. No project
record is edited during parsing.

JUnit covers original long identity round trips through MCR,
oversize and fractional aliases on both root and child, mismatched
child identity, source encoding, and missing fields. Original
resources and hardware/UI behavior are unchanged. Full macro
semantics and device parity remain separate acceptance gates.
