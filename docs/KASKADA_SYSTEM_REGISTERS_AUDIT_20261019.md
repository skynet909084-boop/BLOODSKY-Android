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

- Current integration status (23 Oct 2026): opt-in Craft ID19 register gateway supports explicitly mapped TCP, paired Bluetooth, manual USB RTU, local system-register source and local project user-register destination. ID22 supports TCP, paired Bluetooth, manual USB RTU and local user-register bit endpoints as `system://register/N/bit/B`. External RTU endpoints require operator mode and session consent; imported gateway bindings remain inactive. PRs #397 and #399 passed exact-head APK/JUnit CI; system destinations cannot reach service addresses 0–9.
- The project-persisted **Система** user bank (addresses 10–1999), bounded word/bit/Float API, and `FB_MX`/`FB_ARRAY` routing for user words are implemented. Registers 0–9 have a shared transient Craft service bank, dynamic status/time/battery/screen providers, and source-backed VarOut side-effect handlers. Non-FBD coverage is now partial: imported Craft ID 7 reads a bit for source slot 100/request 1; ID 8 reads UINT16/INT16 and Float32 from slot 100 and can write only after explicit opt-in to user addresses 10–1999 (Float32 pairs 10–1998). Decompiled `q/d/b.H` assembles Float32 bytes as `[word0 high, word0 low, word1 high, word1 low]`, matching big-endian `ByteBuffer.getFloat`; valid read starts are 0–1998 and may cross 9/10. Writes to service addresses 0–9 remain disabled. Other direct widget bindings remain open. RTU/Bluetooth/USB hardware acceptance remains open; see `docs/BLOODSKY_EXECUTION_STATUS.md`.
- `CraftNetworkBindingResolver` recognizes imported `posDeviceNetworkInList=100` as `kind=global` and records `globalRegisterBucket=8`. `CraftSystemWidgetBinding` uses the preserved raw `addressRegister`, `addressBit`, and `typeRequest` only for the verified ID 7 read path; all other imported connections stay inactive.
- The special Craft source slot 100 is a global/system binding marker, not Modbus register address 100 and not network device slot 100. Do not conflate it with system-register addresses 0–1000 or an external device's Modbus address.

### Remaining ID8 parity gaps

Pinned `CustomComponents/i.java` and `mbCanvas.java` show that ID8 routes system slot 100 through the internal register manager. BLOODSKY now reads UINT16/INT16 state and supports opt-in integer writes to user registers 10–1999. Imported Craft passwords remain in the Android Keystore flow and are never copied from the archive into the password store. Decompiled `i.getInitConnectionInfo`, `q/d/b.H`, `i.setValue`, and `mbCanvas.t` confirm Float32 read/write order and a typeRequest 16 write followed by refresh. BLOODSKY reads valid Float32 starts 0–1998 and explicitly opted-in writes to user pairs 10–1998. Still open: writes to service registers 0–9; direct bindings for the other widget IDs; mock-peer/real-device and full visual interaction acceptance. The exact-head APK/JUnit run #1129 on `7bb8bb3` exposed two assertion mistakes in the new tests; both fixtures were corrected and APK/JUnit #1130 passed on `4f3a6c6`. On PR head `927172c`, APK/JUnit #1131 and focused Craft probe #162 passed; the probe is incomplete (8 states / 7 transitions). Android 11 full smoke #765 passed on `927172c` — https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39367727663. Regression coverage includes signed words, source-ordered Float32 reads (including the 9/10 crossing), opt-in Float32 writes, disabled service writes, external slots, and pair bounds.

Source pin: `reference/craft-reference.zip`, SHA-256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`; extracted `jadx/sources/ru/himik/nightscream/kascada/CustomComponents/i.java` (`getStringAddress`, `getInitConnectionInfo`, `setValue`) and `jadx/sources/ru/himik/nightscream/kascada/mbCanvas.java` (`t(iVar, value, password)`, screen activation). The imported source slot is exactly 100; the register address comes separately from `addressRegister`.

## Recommended implementation order

1. Resolve the bank capacity discrepancy from Craft 1.0.7 source/runtime; keep address validation configurable until confirmed.
2. Define a typed register-bank API for Boolean bits, 16-bit words, and two-word Floats with explicit byte/register order.
3. Completed for registers 0–9: add service providers and source-backed word/bit command handlers with tested timing. Remaining service parity is limited to unavailable channel-fault mapping, project-password unlock, embedded WebView, and typed UI VarIn/VarOut bindings.
4. Route remaining direct component bindings with device **Система** to that bank. ID19 local user-register word routes and ID22 local user-register bit routes now exist; verify project round-trip and operator UI behavior.
5. Preserve imported project fields but keep unsupported or ambiguous system bindings visible and inactive until a faithful mapping exists.
6. Retain tests for read-only/write-only addresses, bit masks, endian variants and refresh/save/exit side effects; add full import/save/reopen coverage for gateway endpoints and verify the UI on emulator/device.

## Acceptance boundary

This audit separates the manual contract from Craft 1.0.7's actual call-path behavior. Register 0–9 providers and VarOut handlers are implemented in BLOODSKY from the pinned decompilation. Runtime cases without a BLOODSKY equivalent (project password lock, device-channel fault source, and Craft's internal WebView action) remain explicitly bounded.


## Original runtime findings (Craft 1.0.7 Smali)

Pinned decompilation inspected from `run 38600957172` (artifact SHA-256 `d78bd703d6096ac12d69e19f8b5b5dfa9a4c25e342ce9af0ef8b42326aaa3f8c`):

- `ru/himik/nightscream/kascada/q/d/b.smali` constructs `j` as an `int[2000]`; the static highest accepted index is 1999. The same class reads a 16-bit word (`I(address)`), reads a bit (`G(address, bit)`), writes a bit (`K(address, bit, value)`), and reads a Float from two adjacent words (`H(address)`). Its Float path assembles four bytes and calls `ByteBuffer.wrap(...).getFloat()`; exact register/byte arrangement still needs validation against the VarOutFloat writer and sample projects.
- Write paths differ: `U(address, word)` and `L(address, bit, value)` special-case addresses below 10 and route them through component/handler logic; user-register writes update the backing array. `T(address, word)` is a direct array assignment. Model service registers as side-effecting providers/commands rather than ordinary retained words.
- The bank is cleared by `C()`, which replaces it with a zeroed `int[2000]`.
- `mbCanvas.smali` restores project-retained user values from the `systemRegisters` JSON array. Each entry is an object with a key `reg_N`; the load path writes the value into `MyApplication.v0.j[N]`.
- `Bd/h$a.smali` saves only nonzero words, scanning from address 10 through the full array length. Service words 0–9 are therefore not retained by this project-save loop; user values are stored in the project's `systemRegisters` column.
- `Bd/e.smali` defines `TableNameProject.systemRegisters` as text, and project import/export copies that field. This is persisted project state, separate from an external Modbus device register map.
- The runtime's service paths are now traced through JADX source as well as Smali. Table 9.1.1 labels are kept distinct from implementation details below.

This confirms the bank size and persistence shape from executable source. The manual's 200 count is inconsistent with the runtime; don't implement a 200-word bank. The 1000 endpoint remains the documented table endpoint, while the source bank continues through 1999.


## Progress — shared FBD user-register access

Pinned Craft FB_MX reads q.d.b.I and FB_ARRAY writes q.d.b.T directly. BLOODSKY routes addresses 10–1999 through the project's persisted user bank; addresses 0–9 use one shared transient service bank, as in the original runtime manager. The FBD raw array path deliberately bypasses VarOut handlers. The distinct VarOut word/bit handlers now implement Craft 1.0.7 validation, navigation, palette, password submission, and register-9 commands. Unit tests cover shared raw service words, project-isolated user words, typed BOOL/INT/FLOAT access at documented addresses, side-effect dispatch, save/refresh timing, and register 0 status layout.

## Verified Craft 1.0.7 service-register behavior

The source audit covered JADX q/d/b.java, mbCanvas.java, MyApplication.java, Utils/o.java, and the Craft palette class, with Smali cross-checking. Service addresses remain transient and shared by the single runtime manager; project JSON persistence starts at address 10.

| Address | Dynamic/raw source behavior | VarOut word handler | VarOut bit handler |
|---:|---|---|---|
| 0 RGST | Bits 0–7 are channel error flags; bit 15 is inverted charging (0 while charging). | Ignored. | Ignored. |
| 1 RGID | Set from MyApplication.c when the manager starts; the pinned source initializes it to zero and has no assignment to the field. | Ignored. | Ignored. |
| 2 RGDT | dayOfMonth * 100 + month, with Java's month converted to 1–12. | Ignored. | Ignored. |
| 3 RGTM | hourOfDay * 100 + minute. | Ignored. | Ignored. |
| 4 RGCN | Current screen is stored as a one-based number. | Accepts 1..screen-count and navigates only on change. | A true bit write treats the bit index itself as a one-based screen number and navigates; false is ignored. |
| 5 RGIL | Refreshed from Android Settings.System.screen_brightness by the original worker. | Stores values ≤255 (negative values pass the original check); >255 is ignored. It does not call Settings.System.putInt. | Bits 0–7 mutate the transient word; other bit indices are ignored. |
| 6 RFCH | Battery level. | Ignored. | Ignored. |
| 7 RGRGB | A palette index in practice, despite the manual's 16-bit color label. The palette is indices 0–16. | Accepts 0..16, stores the index and applies/reset the background if changed. | On a true write, stores the bit index itself (not 1 << bit); accepts 0..16 and dispatches the palette index. False is ignored. |
| 8 RGPR | Starts at zero; the original word VarOut path submits a candidate to the project-password check without storing it. | Sends a changed candidate to the password gate; does not store it. | Mutates the selected bit and submits the resulting word only when it changed. |
| 9 | Bits 0–2 weekday (Monday=1..Sunday=7); bit 8 is set when seconds are odd. Clock/date values are periodically refreshed by the worker. | Stores the whole word and immediately dispatches exit only when bit 15 is set; it does not dispatch save/refresh for bits 14/13. | Evaluates command bits on each handler write: bit 15 exits, bit 14 saves on a rising latch with a 10 s limit, bit 13 refreshes at most once per second, bit 10 opens the project URL, and bit 11 logs “каскада”. |

Call-path distinction is material: FB_ARRAY calls raw T(address,value); FB_MX calls raw I(address). Those operations bypass all VarOut handler effects. Craft's ordinary user-variable output paths call U/L and invoke the service handler for addresses 0–9.

BLOODSKY now implements this split in CraftServiceRegisters and FbdCraftRegisterStore. MainActivity supplies battery, charge, brightness and current-screen values, handles screen navigation, palette updates, save/refresh/URL/exit commands, and clears callbacks when the editor closes. Register 1 remains zero unless a source ID provider is added. Channel faults can be written through the provider API but the current BLOODSKY connection model does not yet provide Craft-style channel indices. Project-password submission has no unlock effect while the equivalent project lock remains unimplemented. Craft's bit-10 action opens an external URL intent instead of its embedded WebView.


## Craft 1.0.7 user-register range 10–1000 and typed variables

The documented range 10–1000 contains ordinary user words; the original source does not assign a different per-address function to each register. The shared q.d.b manager stores raw Java `int` values at 10–1999. Its input/output components supply the type and operation:

| Craft component | Source behavior | Address / storage details |
|---|---|---|
| `Var` / `VarInt` | Read one bit with `G(reg,bit)` or one raw word with `I(reg)`. BOOL output inversion and constant mode are component properties. | `Var` and `VarInt` accept addresses 0–1999; bit 0–15. |
| `VarFloat` | Read Float32 across `reg` and `reg+1` using the manager's configured byte/register order. | The component's setter accepts 1–1998; two adjacent words are required. |
| `VarOut` / `VarOutInt` | Write a BOOL bit with `L(reg,bit,value)` or a whole raw integer with `U(reg,value)`. Addresses below 10 invoke service handlers; 10+ stores directly. | BOOL/INT output setters accept 1–1999; BOOL bit 0–15. |
| `VarOutFloat` | Convert Float32 to four bytes, arrange by its two order flags, and send the two resulting words through `U`. | Setter accepts 0–1998; each word is written separately, so a pair may straddle the service/user boundary. |

Craft serializes nonzero user values as `systemRegisters: [{"reg_10": value}, ...]`; service registers 0–9 are transient. Runtime capacity is 2000 entries (0–1999), even though manual table 9.1.1 documents ordinary user registers through 1000. The Java source does not clamp `I/T/U` to unsigned 16-bit values; Modbus transport words remain 16-bit.

BLOODSKY now resolves imported Craft FBD `VAR BOOL/INT/FLOAT` sources from their `reg`/`bit` fields into the active project's bank when no external TCP binding is configured. `VAR OUT` writes to that bank while operator mode runs. BOOL, raw INT, Float32 with the two byte/word order flags, and the 0–9 VarOut side-effect path use the typed adapter. MX/ARRAY continue to use their raw, side-effect-free path. Project save/import retains 10–1999, including the documented boundary register 1000.

Coverage added: typed read/write at 10 and 1000, signed raw integer compatibility, bit inversion, Float32 order combinations, service handler routing, and address/pair bounds. APK/unit, Android 11 UI smoke, and original Craft probe must be checked at the final commit head before treating this revision as verified.

### Float input/output address boundary cross-check

Craft `Logic/VarFloat.j()` rejects register 0 and accepts 1–1998; `Logic/VarOutFloat.j()` accepts 0–1998. BLOODSKY now keeps separate input and output Float bounds rather than applying one shared predicate. A VarOutFloat write beginning at service register 0 is accepted by the source setter but its two underlying service-register writes have no storage effect, so the adapter reports no retained write. Regression coverage locks these distinct boundaries.
