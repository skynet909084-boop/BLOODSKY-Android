# Craft FB_MX and FB_ARRAY register blocks — 2026-10-18

## Source contract

Pinned input archive: `reference/craft-reference.zip`, SHA-256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`.

| Craft block | Source pins | Behavior | BLOODSKY block |
| --- | --- | --- | --- |
| `FB_MX` (source SHA-256 `318188d6afccf493a7f3f8d4ee16513c3e3511d44b49e0fd1a28a88184592b55`) | `EN: BOOL`, `I: Integer` → `Q: Integer` | When EN is true, read bank[I]; otherwise Q is null. | `FBD CRAFT MX` |
| `FB_ARRAY` (source SHA-256 `f1cd4a9d78ae529aa8949f9f463bc9068cc36daf0fcb835f29a10df3c32b1b40`) | `EN: BOOL`, `DT: Integer`, `ID: Integer` → `Q: Integer` | Write DT to bank[ID] once on EN's rising edge; read bank[ID] for Q. A low EN resets the edge latch. | `FBD CRAFT ARRAY` |

The shared source manager `ru.himik.nightscream.kascada.q.d.b` has `int[2000]`, IDs 0–1999, `I(id)` for read and `T(id,value)` for write. The original methods check IDs above 1999 but omit lower-bound checks. BLOODSKY routes addresses 10–1999 through the project-persisted `CraftSystemRegisterBank` (raw Java int user words); addresses 0–9 use a shared transient `CraftServiceRegisters` bank. Raw `FB_ARRAY` writes and `FB_MX` reads bypass service VarOut handlers, matching the original `T`/`I` call path. Separate word/bit handler APIs model the original `U`/`L` actions; the manual/source crosswalk and remaining gaps are in `docs/KASKADA_SYSTEM_REGISTERS_AUDIT_20261019.md`. The `FB_ARRAY` rising-edge latch is runtime state; user register values persist in the project's `systemRegisters` JSON field. Invalid IDs fail closed.

The editor exposes source previews from the actual Craft palette and the separate extended menu. Named graph contracts, source layout and wire anchors are covered by `OriginalFbdNativePaletteMappingTest`; register boundaries and EN rising-edge writes are covered by `FbdCraftRegisterStoreTest` and `FbdCraftRegisterGraphTest`. API30 quick smoke creates both through their original preview actions, checks non-overlap, persistence and cold reload.

The engine represents invalid/null numeric source outputs as non-finite graph values and fails downstream closed. `FB_MACRO` remains unavailable until nested executable graph and namespace storage is implemented. This batch does not claim full FBD completion or pixel-identical parity.


## Typed local Craft variables

Imported `FBD CRAFT VAR BOOL/INT/FLOAT` and `VAR OUT BOOL/INT/FLOAT` now use their serialized `reg` and `bit` fields against the active project's system-register bank when no external TCP endpoint is configured. INT preserves the raw Craft Java integer value. BOOL accesses one bit. FLOAT spans two adjacent words and supports the same word/byte permutations as Craft VarOutFloat. Output blocks write only during operator mode, and addresses 0–9 continue through the source-backed VarOut handlers; MX/ARRAY retain raw access. Registers 10–1000 are covered in typed-access tests, while the runtime and project format retain the full source-backed 10–1999 range.
