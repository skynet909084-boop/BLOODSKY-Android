# Craft FBD MQTT subscriber/publisher native payload types — 2026-10-22

Original KaScada Smart Craft 1.0.7 mbCanvas factory:
- ID139 MQTT_SUBSCRIBER reads `typeQ` for output signal type.
- ID140 MQTT_PUBLISHER reads `typeIn` for payload input type.

The original records also retain unrelated opaque fields, which
BLOODSKY must not use to infer the native payload type. The .mcr
translator previously preferred `typeQ` for *both* components,
falling back to `typeIn`. For publishers this could turn a FLOAT
source into BOOL when `typeQ=1, typeIn=3`. Its JSONObject.optInt
could also wrap 64-bit or fractional source values into a valid enum.

The mapper now reads the correct source key for each original
component via CraftExactInteger, allows only codes 1/2/3, and
preserves type consistency between the widget class and stored
craftMqttType. Missing source type defaults to BOOL (existing
fallback). Invalid source records keep original macro metadata
opaque and do not create a falsely executable graph.

JVM tests cover both factory IDs, INT/FLOAT/BOOL, contradictory
foreign type fields, invalid numeric coercions, and supported
default behavior. No actual MQTT connection is automatically
enabled; network/device/visual acceptance remains a release gate.
