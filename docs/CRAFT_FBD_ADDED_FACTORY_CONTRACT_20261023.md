# Exact factory identity for newly added Craft FBD blocks

Original Craft .pro rows use componentId 101–140. The BLOODSKY
exporter previously trusted an explicit craftComponentId ahead
of the actual editor block type, allowing FBD AND (ID103) to be
serialized as OR (ID104). It also used prefix matches for
named-variable and MQTT types, treating unsupported DOUBLE/NUMBER
variants as known Craft blocks.

New blocks must now agree exactly with their source factory ID,
or infer a verified ID from a recognized block type. Typed
named variables and MQTT sources are limited to BOOL/INT/FLOAT.
This covers omitted Craft ID metadata on newly drawn blocks
without changing imported original source rows.

New publisher/subscriber source rows also derive their original
typeIn/typeQ default from the typed block's name: a newly
created MQTT PUB FLOAT cannot silently export BOOL. If explicit
craftMqttType disagrees with the block type or is an invalid
numeric JSON value, export fails rather than rewriting it.
The MQTT server ID is read without lossy integer coercion.

JVM regressions cover source .pro roundtrip of new gates,
native factory spoofing, invalid typed names, source ID
inference and MQTT INT/FLOAT field consistency. Existing
original artwork/metadata remain unchanged; hardware/device
parity is still outstanding.
