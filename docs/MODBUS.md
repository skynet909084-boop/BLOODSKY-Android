# BLOODSKY Modbus status

## Summary

| Layer | Status | Evidence |
|---|---|---|
| Modbus TCP master | **Implemented** | `ModbusTcpClient`, unit fixtures |
| RTU framing helpers | **Implemented** | `ModbusRtu` on main |
| RTU stream client | **Porting** | `ModbusRtuClient` from `feat/bluetooth-spp-modbus-rtu-20261022` |
| USB serial (FTDI/CP210x/…) | **Porting** | `UsbSerialConnector` + mik3y usb-serial-for-android 3.11.0 |
| Bluetooth Classic SPP | **Porting** | `BluetoothModbusConnection` / `BluetoothRtuBinding` |
| Hardware / physical device | **Not verified** | Explicit release gate |

Do **not** claim production RTU support until a physical adapter/device acceptance pass is recorded.

## Binding URIs (explicit, project-stored)

- `tcp://host:port/...` — existing TCP path
- `usb://<unit>/<register>` — USB RTU after user selects adapter (`UsbSerialBinding`)
- `bt://AA:BB:CC:DD:EE:FF/<unit>/<register>` — paired SPP only (`BluetoothRtuBinding`)

3xxxx/4xxxx register prefixes map to FC04/FC03 as in Craft-style addressing.

## Permissions / features

- `BLUETOOTH` / `BLUETOOTH_ADMIN` (≤ API 30), `BLUETOOTH_CONNECT`
- `android.hardware.usb.host` optional
- `android.hardware.bluetooth` optional

## Remaining gates

1. Finish landing `ModbusRtuClient`, `UsbSerialSession`, `UsbSerialConnector`, `UsbSerialModbusConnection`, `BluetoothModbusConnection` on this branch
2. Wire operator polling for `usb://` and `bt://` (off UI thread)
3. Physical USB + SPP acceptance (no emulator)
4. Inter-frame timing on real UARTs
5. Reconnect after detach/drop

Source of the serial stack: branch `feat/bluetooth-spp-modbus-rtu-20261022` (already implemented there; not yet fully on main).
