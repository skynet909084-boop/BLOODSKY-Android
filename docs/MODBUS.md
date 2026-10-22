# Modbus status

| Layer | Status | Evidence |
|---|---|---|
| Modbus TCP master | **Implemented** | `ModbusTcpClient` |
| RTU stream client | **Implemented (code)** | `ModbusRtuClient` |
| USB serial | **Implemented (code)** | `UsbSerialConnector` + mik3y |
| Bluetooth Classic SPP | **Implemented (code)** | `BluetoothModbusConnection` |
| Operator binding router | **Implemented (code)** | `ModbusPollGateway` (`tcp://`, `usb://`, `bt://`) |
| Operator polling in UI | **Patch ready** | `patches/MainActivity-modbus-operator-poll.patch` |
| Hardware / physical device | **Not verified** | Release gate |

## Binding URIs

- `tcp://host:port/unit/register`
- `usb://unit/register` — requires explicit USB device selection (`ModbusPollGateway.setSelectedUsbDevice`)
- `bt://AA:BB:CC:DD:EE:FF/unit/register` — paired SPP only

Writes remain **TCP-only** until a physical write path is accepted.

## Remaining gates

1. Apply `patches/MainActivity-modbus-operator-poll.patch` (or equivalent) to wire UI
2. USB device picker in settings UI
3. Physical USB + SPP acceptance (no emulator)
4. Write path for usb/bt (read path first)
5. Inter-frame timing / reconnect on real UARTs
