# Craft MQTT blocks in BLOODSKY

Craft source inspected: `MQTT_SUBSCRIBER.java`, `MQTT_PUBLISHER.java`, and `q/b/b.java` in the pinned `reference/craft-reference.zip` (SHA-256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`).

The original palette rows now map to executable `FBD CRAFT MQTT SUB BOOL` and `FBD CRAFT MQTT PUB BOOL` blocks. Their properties select BOOL/INT/FLOAT, one of two server profiles, and a topic. Subscribers accept valid MQTT topic filters; publishers require a concrete topic. The publisher uses the original EN rising-edge behavior and sends UTF-8 payloads with QoS 2 and the configured Retain flag. Incoming payloads are parsed strictly by type; malformed, out-of-range and non-finite values invalidate the source value.

Broker profiles are configured through the project-settings dialog, matching Craft's two numbered server choices; as in Craft, profiles are app-wide. BLOODSKY keeps broker credentials in Android app-private preferences instead of exporting them into project JSON. Endpoints accept `tcp://host:port` for compatibility or `ssl://host:port` for TLS. Paho MQTT 3.1.1 clients retry failed initial connections with bounded backoff, reconnect automatically, re-subscribe after reconnect, and are isolated per server number. Connections start only in operator mode and close asynchronously on pause/exit.

Automated coverage validates endpoint/topic policy, typed payload parsing, original palette mapping, input/output pin contracts, and quick Android palette/persistence smoke. No live broker/device test has been run in this environment yet. Plain `tcp://` preserves Craft compatibility but does not encrypt credentials or payloads; use `ssl://` when the broker supports TLS.
