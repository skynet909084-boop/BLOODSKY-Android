# Modbus TCP transport module

## Production path

MainActivity's existing static read/write entry points now delegate wire I/O to ModbusTcpClient. Scanner and bound-widget callers keep using those entry points and the same formatted read report. RTU, ZIP/media, project JSON, FBD and visual rendering are outside this change.

ModbusTcpClient contains no Android APIs. It returns decoded bit/register arrays and exposes ModbusException(functionCode, exceptionCode). MainActivity translates device errors through its existing Russian error descriptions.

## Supported operations and boundaries

| Operation | Function | Quantity | Address range |
|---|---|---|---|
| Read coils/discrete inputs | 01 / 02 | 1..2000 bits | zero-based 0..65535, range must fit |
| Read holding/input registers | 03 / 04 | 1..125 registers | zero-based 0..65535, range must fit |
| Write one holding register | 06 | 1 | zero-based 0..65535 |
| Write holding registers | 16 (0x10) | 1..123 | zero-based 0..65535, range must fit |

- The transport takes PDU addresses. UI 30001/40001 conversion remains the responsibility of existing scanner/binding paths; do not apply the conversion a second time.
- TCP Unit ID 0..255 is retained for gateway compatibility.
- Register values accept -32768..65535, preserving 16-bit signed/unsigned bit patterns; larger integers are rejected before network I/O instead of being silently truncated.
- Endpoint/timeout/range/quantity/value validation precedes connection.
- Caller arrays are copied before FC16 serialization.
- A process-wide atomic sequence allocates 16-bit transaction IDs.

## Failure behavior

Every operation owns and closes its socket, including error paths. A later poll creates a fresh connection after a failure; no automatic write retry is performed because an unanswered write may already have been executed by the device.

Connection timeout and response timeout default to 3000 ms. The response has one monotonic deadline shared by header and PDU reads. Receiving a new byte does not restart the deadline. DNS resolution and the small blocking request write are not covered by that response deadline.

Checks include transaction/protocol/unit IDs, function-specific MBAP length before payload allocation, full header/PDU reads, response function and byte count, exact two-byte exception PDUs, and exact address/value-or-quantity write acknowledgements. FC01/02 decode only the requested bits and ignore padding.

This retains per-request connections; it does not introduce socket pooling, parallel scanning UI, serial/USB support or a Modbus slave server. Existing UI executor ownership is unchanged; callers must continue performing I/O off the UI thread.

## Evidence

ModbusTcpClientTest adds 14 JVM cases, with grouped invalid-input cases and local socket fixtures covering maximum read/write requests, last address, signed register bits, split writes, typed device exceptions, partial header EOF, bad declared length, silent peer timeout, an overall deadline against trickled bytes, and a subsequent successful request after a malformed response.

Existing ModbusTcpProductionRegressionTest and ModbusTcpWriteConfirmationTest still call MainActivity's real static entry points and therefore also verify delegation. Source contracts now inspect checks in the transport and require all three Activity delegates; they remain supplementary to executable tests.

Local Android/Gradle execution is unavailable in this environment. Independent GitHub CI must pass before readiness. No physical equipment, Android UI/runtime, visual parity, or USB behavior is verified by these TCP tests.

Reference: MODBUS Application Protocol V1.1b3, function sections 6.1–6.4, 6.6, 6.12 and exception responses:
https://www.modbus.org/file/secure/modbusprotocolspecification.pdf
