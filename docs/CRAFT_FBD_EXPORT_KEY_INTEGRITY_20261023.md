# Craft FBD .pro export endpoint identity and copy integrity

KaScada 1.0.7 FBD blocks (factory IDs 101–140) use saveKey
to identify source/target wiring inside each one-based screen.
BLOODSKY exporter allocates new saveKeys when users create
or duplicate FBD blocks. Previously that allocator used
JSONObject.optInt for componentId, numScreen and saveKey:
huge/fractional JSON values could be truncated or wrapped,
making a malformed source key alias an existing original endpoint.
Duplicate original keys on the same screen also had no early
protection when allocating new endpoints.

New allocation requires exact native int32 identities, checks
duplicate real keys per screen (preserving -1 sentinel) and
refuses key exhaustion. Cross-screen saveKeys remain independent.

Copying an imported component now also requires its explicitly
declared craftComponentId to match the original source factory ID
and verifies the one-based original screen without lossy coercion.
It never silently upgrades an altered FBD gate to another source
type. No change to source .pro fields, network activation or
original drawing resources.

JVM tests cover allocation across multiple screens, original
sentinel, overflow/fraction/invalid JSON, duplicate endpoints,
identity mismatches, and preservation of copied vendor metadata.
These checks do not prove complete KaScada on-device fidelity.
