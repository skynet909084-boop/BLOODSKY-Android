# Modbus status

| Layer | Status | Evidence |
|---|---|---|
| Modbus TCP master | **Implemented** | `ModbusTcpClient` and unit tests |
| RTU stream client | **Implemented (code)** | `ModbusRtuClient` and validation tests |
| USB serial | **Implemented (code)** | `UsbSerialConnector` + mik3y; adapter selected manually |
| Bluetooth Classic SPP | **Implemented (code)** | `BluetoothModbusConnection`; paired device only |
| Operator binding router | **Integrated** | `ModbusPollGateway` routes `tcp://`, `usb://`, and `bt://` bindings |
| Operator polling in UI | **Integrated** | `MainActivity` sends project reads through `ModbusPollGateway` |
| Hardware / physical device | **Not verified** | Release gate; current tests use JVM/emulator paths |

## Binding URIs

- `tcp://host:port/unit/register`
- `usb://unit/register` — requires an explicitly selected and connected adapter.
- `bt://AA:BB:CC:DD:EE:FF/unit/register` — paired SPP device; Android Bluetooth permission is separate.

Imported USB/Bluetooth bindings remain idle until the operator approves RTU polling for that session. USB also requires manual adapter selection and connection. Reads support the function and value formats represented by the binding; writes remain TCP-only.

## Remaining gates

1. Verify USB serial and Bluetooth SPP against physical adapters and Modbus devices.
2. Confirm inter-frame timing, disconnect handling and reconnection behavior on real hardware.
3. Add an accepted physical write path before enabling USB/Bluetooth writes.
