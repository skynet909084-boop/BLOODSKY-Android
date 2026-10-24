# Original KaScada .pro MQTT FBD native type mapping — 23 October 2026

Factory mbCanvas IDs 139 (MQTT_SUBSCRIBER) and 140 (MQTT_PUBLISHER)
have distinct Craft source keys: ID139 output typeQ and ID140
input typeIn. Their native typed signal codes are 1 BOOL, 2 INT,
3 FLOAT. Prior BLOODSKY ordinary .pro importer classified both as
a BOOL placeholder and used optInt(typeQ,typeIn) for the common
craftMqttType value, even when the real original publisher was FLOAT.

Source import now classifies each typed block using its correct
original property and validates exact integer type code 1..3.
Malformed values cannot become a different native signal or
silently activate an incorrect FBD/MQTT link. Missing values keep
the existing BOOL default and unrelated/unknown source fields
survive export/re-import. Import never activates an external
network endpoint automatically.

JVM regressions cover direct .pro → editor → .pro round trips,
contradictory typeQ/typeIn fields, complete native signal types,
invalid fractional/64-bit aliases and empty defaults.

This complements the .mcr translator fix in PR #366; neither
change establishes original UI or network device acceptance.
