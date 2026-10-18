# Craft Var / VarOut blocks in BLOODSKY

Source: `reference/craft-reference.zip` pinned by SHA256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`; classes `Logic/c/f.java`, `Logic/c/g.java`, `Logic/VarName/VarNameIn.java`, `Logic/VarName/VarNameOut.java`, `Logic/Sensor_Light.java`, `Logic/Sensor_Orientation.java`, `Logic/Sensor_Speed.java`, `Logic/Sunrise_GPS.java` and `Logic/ODTEMP.java`.

## Original register blocks

The six Craft palette rows map to typed BLOODSKY FBD sources and sinks:

| Craft row | BLOODSKY block | Value contract |
|---|---|---|
| `Var` / `VarOut` | `FBD CRAFT VAR BOOL` / `...VAR OUT BOOL` | One selected bit in a 16-bit register |
| `VarInt` / `VarOutInt` | `FBD CRAFT VAR INT` / `...VAR OUT INT` | Unsigned register value, 0–65535 |
| `VarFloat` / `VarOutFloat` | `FBD CRAFT VAR FLOAT` / `...VAR OUT FLOAT` | IEEE-754 Float32 across two big-endian registers |

Sources use the existing `tcp://host:port/unit/address` Modbus TCP binding or a configured constant. Float reads request two consecutive registers. Float outputs expose independent word and byte swap options. BOOL output uses FC22 mask write to preserve all other bits in the register; Integer uses FC06 and Float32 uses FC16. Writes run asynchronously only in operator mode, require a connected graph input, and suppress duplicate values with a bounded retry interval. FC22 support remains device-dependent and has not been validated against a physical PLC.

## Named VarName blocks

`VarNameIn` and `VarNameOut` map to three typed source/sink pairs: BOOL, INT32 and FLOAT32. A shared transient store follows Craft's top-level global name behavior; named variables are cleared when a project is loaded or operator mode starts. Constant mode is supported on VarNameIn. Names are case-sensitive, as in Craft's `HashMap` store.

Macro-instance namespaces are intentionally not emulated by this flat screen graph. `FB_MACRO` remains disabled until nested graph editing, dynamic outer pins, persistence and isolated per-instance namespaces can be implemented together. ODTEMP and MQTT remain disabled pending device-network and broker integration.

`Sensor_Light` and `Sensor_Orientation` use Android's built-in light and orientation sensors. `Sensor_Speed` requests foreground fine-location permission only when used in operator mode, then reports GPS speed in km/h. Sensor/location listeners are removed when the activity pauses and cached readings are invalidated. `Sunrise_GPS` is implemented locally from its configured latitude/longitude and current local date/time zone; it does not acquire location or make a network request.

`ODTEMP` polls its source-compatible `/state.json` endpoint asynchronously every five seconds. Its editor accepts only private IPv4 addresses, bounds the response at 64 KiB, applies connection/read timeouts, and rejects redirects and malformed/non-finite values.

Existing unrecognized JSON metadata remains in `Widget.extraFields` through load/edit/save. Native sensor readings, real PLC timing, and exact same-density drawing parity still need device-level verification.
