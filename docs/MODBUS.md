# BLOODSKY Modbus status

## Summary

| Layer | Status | Evidence |
|---|---|---|
| Modbus TCP master | **Implemented** | `ModbusTcpClient`, unit fixtures, production regression tests |
| RTU framing (CRC16, frames) | **Implemented (framing only)** | `ModbusRtu`, expanded unit tests |
| RTU serial / USB / Bluetooth transport | **Not implemented** | No Android serial stack, permissions or device tests |
| Hardware / physical device | **Not verified** | Explicit release gate |

Do not describe BLOODSKY as having full Modbus RTU or hardware support until a real transport exists and is tested on device.

## TCP (production path)

See also the historical note in `MODBUS_TCP_TRANSPORT.md`.

Supported functions:

| Op | Function | Quantity | Address |
|---|---|---|---|
| Read coils / discrete | 01 / 02 | 1..2000 bits | 0..65535 |
| Read holding / input | 03 / 04 | 1..125 regs | 0..65535 |
| Write single holding | 06 | 1 | 0..65535 |
| Write multiple holding | 16 | 1..123 | 0..65535 |

- PDU addresses are zero-based; UI 3xxxx/4xxxx conversion stays in scanner/binding code.
- Unit ID 0..255 for TCP gateways.
- Per-request socket (no pooling); failed exchange cannot poison the next poll.
- Validation of host/port/timeout/range/value happens before connect.
- `ModbusTcpClient` has no Android dependency.

## RTU framing (this PR)

`ModbusRtu` provides:

- Standard Modbus RTU CRC-16 (poly 0xA001, init 0xFFFF, low byte first on the wire)
- Read request frames (FC01–FC04)
- Write single (FC06) and write multiple (FC16) frames
- CRC validation
- Stream `exchange` helper for **future** serial transports (takes `InputStream`/`OutputStream` only)

`MainActivity.calculateModbusRtuCrc` / `buildModbusRtuFrame` remain for compatibility; tests assert they match `ModbusRtu`.

## Transport interface

`ModbusTransport` defines `read` / `writeSingle` / `writeMultiple`.
`ModbusTcpClient` is the concrete TCP implementation path used today.
A future `ModbusRtuTransport` would implement the same interface over a serial stream after USB/Bluetooth wiring exists.

## Explicit remaining gates

1. Android USB host / Bluetooth SPP (or equivalent) open/close, permissions and lifecycle.
2. Inter-frame quiet time / 3.5 character timing on real UARTs.
3. Reconnect policy after cable unplug or Bluetooth drop.
4. Integration with original Craft device-picker / system-register semantics.
5. Physical-device acceptance tests (not emulator-only).
6. Background/foreground service behaviour under Doze and process death.

Until those gates pass, UI may show serial options as **unavailable** rather than claiming support.

## Tests

- `ModbusTcpClientTest` — invalid inputs, max FC01/FC03, signed bits, exceptions, timeouts, reconnect after malformation.
- `ModbusTcpProductionRegressionTest` / `ModbusTcpWriteConfirmationTest` — Activity entry points.
- `ModbusRtuTest` — CRC vector 0x0A84, frame layout, write frames, CRC tamper detection, stream exchange round-trip.
- `Batch3ModbusAndSettingsTest` — FC01/FC02 mock server through MainActivity static helpers.

Run:

```sh
gradle testDebugUnitTest --tests 'com.bloodsky.hmi.Modbus*'
python -m unittest discover -s tools -p 'test_*.py' -v
```

Independent GitHub Actions APK/JUnit remains the merge gate.
