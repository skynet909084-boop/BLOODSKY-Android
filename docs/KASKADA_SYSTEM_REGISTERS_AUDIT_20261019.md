# KaScada system registers and KaLogic variable model — 2026-10-19

## Sources checked

- HMI-KaSCADA v1.4 manual supplied by the user, PDF pages 69–74 (printed sections 9 and 10).
- BLOODSKY-Android source on `feat/craft-screen-navigation-import-20261018`: `CraftRegisterGateway`, `CraftBitGateway`, `CraftNetworkBindingResolver`, `CraftProjectImporter`, and `docs/BLOODSKY_EXECUTION_STATUS.md`.

This note separates documented behavior from the current implementation. Passing import/build checks alone does not establish full register or service-register parity.

## Documented model

KaLogic provides an internal nonvolatile system-register area for PLC-style processing and data conversion. The manual says the first ten addresses (0–9) are service registers and addresses 10–1000 are user registers. Values are connected to UI components and FBD through Boolean, Integer/Word and Float variables.

The count/range mismatch is now partly resolved against Craft 1.0.7 Smali. The manual says 200 registers and its table shows user addresses through 1000; the runtime allocates an `int[2000]` register bank (static size `E=2000`, highest valid index `F=1999`). Its project-save loop serializes nonzero values starting at address 10 through the end of that array. Thus the runtime capacity is 2000 words (addresses 0–1999), while the manual documents only through 1000. Whether the UI intentionally limits entry to 1000 still needs a separate check.

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
- The project-persisted **Система** user bank (addresses 10–1999), bounded word/bit/Float API, and `FB_MX`/`FB_ARRAY` routing for user words are implemented. Service-register providers and side effects for 0–9, typed variable component bindings, Float gateway endpoints, and internal gateway transfers remain open. RTU/Bluetooth transport parity also remains open; see `docs/BLOODSKY_EXECUTION_STATUS.md`.
- `CraftNetworkBindingResolver` recognizes imported `posDeviceNetworkInList=100` as `kind=global` and records `globalRegisterBucket=8`. This preserves original binding metadata; it does not activate the corresponding component binding.
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


## Original runtime findings (Craft 1.0.7 Smali)

Pinned decompilation inspected from `run 38600957172` (artifact SHA-256 `d78bd703d6096ac12d69e19f8b5b5dfa9a4c25e342ce9af0ef8b42326aaa3f8c`):

- `ru/himik/nightscream/kascada/q/d/b.smali` constructs `j` as an `int[2000]`; the static highest accepted index is 1999. The same class reads a 16-bit word (`I(address)`), reads a bit (`G(address, bit)`), writes a bit (`K(address, bit, value)`), and reads a Float from two adjacent words (`H(address)`). Its Float path assembles four bytes and calls `ByteBuffer.wrap(...).getFloat()`; exact register/byte arrangement still needs validation against the VarOutFloat writer and sample projects.
- Write paths differ: `U(address, word)` and `L(address, bit, value)` special-case addresses below 10 and route them through component/handler logic; user-register writes update the backing array. `T(address, word)` is a direct array assignment. Model service registers as side-effecting providers/commands rather than ordinary retained words.
- The bank is cleared by `C()`, which replaces it with a zeroed `int[2000]`.
- `mbCanvas.smali` restores project-retained user values from the `systemRegisters` JSON array. Each entry is an object with a key `reg_N`; the load path writes the value into `MyApplication.v0.j[N]`.
- `Bd/h$a.smali` saves only nonzero words, scanning from address 10 through the full array length. Service words 0–9 are therefore not retained by this project-save loop; user values are stored in the project's `systemRegisters` column.
- `Bd/e.smali` defines `TableNameProject.systemRegisters` as text, and project import/export copies that field. This is persisted project state, separate from an external Modbus device register map.
- The runtime's register-9 path uses the same bank bit accessors and dispatches handler events for service-bit effects. The manual remains the authority for user-facing labels; message-to-action mapping and permission/timing behavior should be traced before implementing those side effects.

This confirms the bank size and persistence shape from executable source. The manual's 200 count is inconsistent with the runtime; don't implement a 200-word bank. The 1000 endpoint remains the documented table endpoint, while the source bank continues through 1999.


## Progress — shared FBD user-register access

Pinned Craft FB_MX and FB_ARRAY use the same q.d.b register manager that project save serializes. BLOODSKY now routes their addresses 10–1999 through the project bank, while 0–9 remain transient and do not invoke unverified service actions. Tests cover per-project isolation, the service/user boundary, 16-bit range rejection and address limits. This does not yet wire Var/VarOut UI components or gateway endpoints to the bank.
