# Exact original Craft FBD wire topology when saving .pro

The original KaScada 1.0.7 FBD editor serializes per-screen
wires using component saveKey/keyOut/numOnIn and componentId.
BLOODSKY supports editing and exporting these original links.

In the writeFbdConnections path, JSONObject.optInt/getInt
could wrap or truncate edited graph indexes, native factory IDs
and original saveKey/keyOut references. An oversized source
index such as 4294967296 could therefore alias widget 0,
redirecting a newly edited wire to the wrong Craft block.

All affected topology indexes and source identifiers now use
CraftExactInteger.read. The original one-outgoing-wire and
valid source/target pin compatibility checks remain in place.
Only projects with *edited* graph wiring are revalidated;
unchanged original opaque graphs are not rewritten or
implicitly executed. Wrong numeric field types fail closed.

JVM regression tests prove a valid native BOOL wire
serializes keyOut/numOnIn without source mutation,
oversized/fractional input and output indexes are rejected,
and a bad original saveKey or widget factory ID cannot alias
another original endpoint.

Original media, active external transport bindings and
production main are unaffected. Android/device/visual
acceptance remains an independent gate.
