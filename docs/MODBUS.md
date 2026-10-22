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

See also `MODBUS_TCP_TRANSPORT.md`.

Supported: FC01/02 (1..2000 bits), FC03/04 (1..125 regs), FC06, FC16 (1..123). Zero-based PDU addresses. Unit 0..255. Per-request sockets. Validation before connect.

## RTU framing

`ModbusRtu`: CRC-16, read/write frames, CRC validation, stream exchange helper for a future serial transport. No Android USB/Bluetooth.

`MainActivity.calculateModbusRtuCrc` / `buildModbusRtuFrame` remain; tests check CRC/frame compatibility.

## Transport interface

`ModbusTransport` + `ModbusTcpClient implements ModbusTransport`.

## Remaining gates

1. Android USB / Bluetooth open-close, permissions, lifecycle
2. Inter-frame timing on real UARTs
3. Reconnect after unplug/drop
4. Original Craft device-picker / system registers
5. Physical-device acceptance tests
6. Background service under Doze

## Tests

`ModbusTcpClientTest`, production regression tests, expanded `ModbusRtuTest`, `Batch3ModbusAndSettingsTest`.

```sh
gradle testDebugUnitTest --tests 'com.bloodsky.hmi.Modbus*'
```
