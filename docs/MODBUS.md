# BLOODSKY Modbus status

## Summary

| Layer | Status | Evidence |
|---|---|---|
| Modbus TCP master | **Implemented** | `ModbusTcpClient`, unit fixtures |
| RTU framing helpers | **Implemented** | `ModbusRtu` |
| RTU stream client | **Implemented (code)** | `ModbusRtuClient` + unit tests |
| USB serial | **Implemented (code)** | `UsbSerialConnector` / Session / Connection + mik3y 3.11.0 |
| Bluetooth Classic SPP | **Implemented (code)** | `BluetoothModbusConnection` / `BluetoothRtuBinding` |
| Operator polling for usb:// and bt:// | **Not wired** | Explicit next step |
| Hardware / physical device | **Not verified** | Release gate |

Do **not** claim production RTU support until a physical adapter/device acceptance pass is recorded.

## Binding URIs

- `usb://<unit>/<register>` — after user selects adapter (`UsbSerialBinding`)
- `bt://AA:BB:CC:DD:EE:FF/<unit>/<register>` — paired SPP only (`BluetoothRtuBinding`)
- 3xxxx → FC04, 4xxxx → FC03

## Permissions

- Bluetooth Classic + `BLUETOOTH_CONNECT`
- USB host optional feature

## Remaining gates

1. Wire operator polling for `usb://` / `bt://` (off UI thread)
2. Physical USB + SPP acceptance (no emulator)
3. Inter-frame timing on real UARTs
4. Reconnect after detach/drop

Source: ported from `feat/bluetooth-spp-modbus-rtu-20261022` onto main line.
