# KaScada system registers and KaLogic variable model — 2026-10-19

## Sources checked

- HMI-KaSCADA v1.4 manual supplied by the user, PDF pages 69–74 (printed sections 9 and 10).
- BLOODSKY-Android source on `feat/craft-screen-navigation-import-20261018`: `CraftRegisterGateway`, `CraftBitGateway`, `CraftNetworkBindingResolver`, `CraftProjectImporter`, and `docs/BLOODSKY_EXECUTION_STATUS.md`.

This note records documented behavior separately from the current implementation. A successful import/build is not evidence that the internal register bank is implemented.

## Documented model

KaLogic provides an internal nonvolatile system-register area for PLC-style processing and data conversion. The manual says the first ten addresses (0–9) are service registers and addresses 10–1000 are user registers. Values are connected to UI components and FBD through Boolean, Integer/Word and Float variables.

The manual itself has an unresolved count/range inconsistency: it says the area contains 200 registers, but lists user-register addresses through 1000. Preserve this discrepancy as an open question; do not silently choose a capacity.

Components can bind directly to a system register by choosing the device named **Система**. The manual describes register and bit gateways as the bridge between this internal bank and external device registers. They are distinct from direct Modbus device bindings.

### Service-register table (manual §9, PDF pages 69–70)

| Address | Name | Documented meaning | Access |
|---:|---|---|---|
| 0 | RGST | Status. Bits 0–7 report communication-channel faults. Bit 15 reports charging state: 0 = charging, 1 = not charging. | Read |
| 1 | RGID | Identification, lower 16 bits of the MAC address. | Read |
| 2 | RGDT | Date. | Read |
| 3 | RGTM | Time. | Read |
| 4 | RGCN | Screen control/status; active screen is indicated by 1 in its corresponding bit. | Read/write |
| 5 | RGIL | Screen brightness. | Write |
| 6 | RFCH | Battery charge level. | Read |
| 7 | RGRGB | Background color, 16-bit value. | Write |
| 8 | RGPR | Password, range 0–65535. | Read |
| 9 | — | Bit field: bits 0–2 weekday; bit 8 seconds; bit 13 refresh all on-screen components (setters read values, at most once per second); bit 14 save project; bit 15 exit KaScada. | Read/write |
| 10–1000 | RGUS | User registers. | Read/write |

Treat the entries as the manual's contract, not as proven runtime semantics. The bit numbering/encoding and exact date/time representation should be verified against original source or controlled app behavior before implementation.

## Variable and FBD data model (manual §10)

- Input and output variable elements support Boolean, Integer/Word, and Float values.
- Boolean variables select a register and bit; integer and float variables select a register.
- Boolean inputs can be inverted. Inputs may select a system value, a constant, or a register value before connecting to an FB input.
- Internal named variables connect FB outputs and inputs and can be Boolean, Integer or Float.
- A Float output may span two registers. The manual exposes byte-order choices: high register first and high byte first. Preserve this as explicit conversion metadata; do not assume host-endian float layout.
- The register gateway (§8.2) transfers register values between a device and system registers. The bit gateway (§8.3) transfers a register bit between device/system endpoints using a register.bit address.

## BLOODSKY state

- Craft component ID 19 register gateway has opt-in TCP read/transfer/write runtime; ID 22 bit gateway has opt-in TCP bit-transfer runtime. These operate on explicitly configured TCP endpoints.
- Internal **Система** register storage, service-register providers, Float register packing, and system-register endpoints for the gateways are not implemented yet. RTU/Bluetooth transport parity also remains open; see `docs/BLOODSKY_EXECUTION_STATUS.md`.
- `CraftNetworkBindingResolver` recognizes imported `posDeviceNetworkInList=100` as `kind=global` and records `globalRegisterBucket=8`. This preserves the original binding metadata; it does not create a live system-register bank or execute the element.
- The special Craft source slot 100 is a global/system binding marker, not Modbus register address 100 and not network device slot 100. Do not conflate it with system-register addresses 0–1000 or an external device's Modbus address.

## Recommended implementation order

1. Resolve the bank capacity discrepancy from Craft 1.0.7 source/runtime; keep address validation configurable until confirmed.
2. Define a typed register-bank API for Boolean bits, 16-bit words, and two-word Floats with explicit byte/register order.
3. Implement service-register providers with read/write permissions and lifecycle hooks (screen, battery, time, refresh, save, exit); isolate side effects behind tested commands.
4. Route component bindings with device **Система** to that bank; route gateway source/destination endpoints to internal or external transport without collapsing the two namespaces.
5. Preserve imported project fields but keep unsupported or ambiguous system bindings visible and inactive until a faithful mapping exists.
6. Add tests for read-only/write-only addresses, bit masks, endian variants, refresh/save/exit side effects, import/save/reopen, and gateway transfers to/from the system bank; then verify the UI on emulator/device.

## Acceptance boundary

This audit establishes the manual's documented model and the current BLOODSKY gap. It does not claim that system-register runtime behavior is implemented or that every manual detail matches Craft 1.0.7. Validate against the pinned decompilation and original app before marking parity complete.
