# Exact Craft export identity validation — 22 October 2026

Craft source rows and screen indices map editable BLOODSKY components
back into the original KaScada `TableComponent` / `numScreen` records.

Before this change, `JSONObject.optInt` could wrap or truncate
out-of-range numeric identities in `craftSourceRow`,
`craftCopySourceRow`, `craftScreenNumber`, `craftComponentId`,
and original source `componentId` / `numScreen`. A large value such as
4294967296 therefore aliased index 0, risking a write into an
unrelated component during Craft .pro export.

The exporter now reuses the proven `CraftExactInteger.read` utility
from the original FBD macro integrity work (#355). All listed
identity fields must be exact signed 32-bit integers; missing fields
continue to use the previous distinct defaults for new screens/
components. Invalid explicit identities abort export rather than
guessing a destination row. Source metadata and unknown keys stay
unchanged.

New JVM regressions cover normal lossless export, 64-bit row/screen
aliases, fractions, oversized factory IDs, malformed identity arrays
and copied-component indices. This does not enable exporting arbitrary
new component types or unsupported screen moves. Original APK/device
and visual acceptance remain separate gates.
