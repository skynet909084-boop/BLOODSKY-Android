# FBD Craft macro INT/FLOAT interface integrity (2026-10-22)

Source reference: original FB_MACRO exposes typed interface codes
1=Boolean, 2=Integer and 3=Float; BLOODSKY's FbdCraftMacroDefinition
validates optional executable bloodskyGraph sidecars.

Prior nested-graph validation checked only whether output was Boolean.
Consequently an INT value could masquerade as FLOAT (and vice versa)
at a macro interface. It also used a coarse pin-type oracle when
validating child edges, allowing a numeric INT/FLOAT cross-connection
to pass. The editor/runtime treats such graphs as executable.

Validation now resolves each known graph pin through
FbdCraftPinTypes.typeForWidget (original typed pin contracts) and uses
its compatibility method, retaining generic NUMBER compatibility
where native source type is genuinely unverified. Explicit INT and
FLOAT are never interchangeable. Invalid executable sidecars fail
closed without discarding the original opaque Craft child records.

JVM regression covers INT/FLOAT mismatched output ports and child
links as well as valid same-type graph edges and preserved native
macro interface declarations. No FBD runtime ABI, source archives,
Android visuals, or imported network permissions change. Original
device and screenshot tests remain outstanding.
