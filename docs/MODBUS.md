# BLOODSKY Modbus status

## Summary

| Layer | Status | Evidence |
|---|---|---|
| Modbus TCP master | **Implemented** | `ModbusTcpClient` |
| RTU stream client | **Implemented (code)** | `ModbusRtuClient` |
| USB serial | **Implemented (code)** | `UsbSerialConnector` + mik3y |
| Bluetooth Classic SPP | **Implemented (code)** | `BluetoothModbusConnection` |
| Operator binding router | **Implemented (code)** | `ModbusPollGateway` (`tcp://`, `usb://`, `bt://`) |
| Operator polling in UI | **In progress** | MainActivity hooks via gateway |
| Hardware / physical device | **Not verified** | Release gate |

## Binding URIs

- `tcp://host:port/unit/register`
- `usb://unit/register` — requires explicit USB device selection (`ModbusPollGateway.setSelectedUsbDevice`)
- `bt://AA:BB:CC:DD:EE:FF/unit/register` — paired SPP only

## Remaining gates

1. Finish MainActivity poll path fully on this branch if not already applied
2. USB device picker in settings UI
3. Physical USB + SPP acceptance (no emulator)
4. Write path for usb/bt (read path first)
