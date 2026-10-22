# Added Craft FBD component factory identity — 23 October 2026

Original KaScada 1.0.7 .pro TableComponent rows store native
componentId (101–140). New BLOODSKY FBD blocks are mapped
back to those factories when exporting an edited .pro.

Previously an explicit craftComponentId (e.g. 104) was accepted
ahead of the actual editor block type (e.g. FBD AND, ID103).
This could emit an OR source row while the BLOODSKY editor
still displayed AND. The factory mapping also used startsWith
for named variable and MQTT block types, treating unknown
numeric suffixes (such as DOUBLE) as executable originals.

Native IDs on newly authored widgets must now agree exactly
with the inferred original factory from the canonical block
type. Named variable and MQTT types require BOOL/INT/FLOAT
rather than accepting arbitrary suffixes. Valid unannotated
widgets still infer their native IDs; macros ID135 remain
unavailable for unsupported new .pro output. Original
imported source rows are not remapped by this check.

JVM regression covers new AND/OR round trips, mismatched
factory IDs, MQTT publisher/subscriber mix-ups, unsupported
numeric types, source saveKey allocation and implicit ID
inference. Source references and unknown original fields
continue to be preserved. Device/pixel parity not claimed.
