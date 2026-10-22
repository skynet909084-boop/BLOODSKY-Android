# Modbus status

| Layer | Status | Evidence |
|---|---|---|
| Modbus TCP master | **Implemented** | `ModbusTcpClient` |
| RTU stream client | **Implemented (code)** | `ModbusRtuClient` |
| USB serial | **Implemented (code)** | `UsbSerialConnector` + mik3y |
| Bluetooth Classic SPP | **Implemented (code)** | `BluetoothModbusConnection` |
| Operator binding router | **Implemented (code)** | `ModbusPollGateway` (`tcp://`, `usb://`, `bt://`) |
| Operator polling in UI | **Implemented (code)** | MainActivity → gateway `readFirstValue` |
| Hardware / physical device | **Not verified** | Release gate |

## Binding URIs

- `tcp://host:port/unit/register`
- `usb://unit/register` — requires explicit USB device selection (`ModbusPollGateway.setSelectedUsbDevice`)
- `bt://AA:BB:CC:DD:EE:FF/unit/register` — paired SPP only

Writes remain **TCP-only** until a physical write path is accepted.

## Remaining gates

1. USB device picker in settings UI
2. Physical USB + SPP acceptance (no emulator)
3. Write path for usb/bt (read path first)
4. Inter-frame timing / reconnect on real UARTs
