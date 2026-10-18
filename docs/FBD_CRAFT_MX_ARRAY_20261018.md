# Craft FB_MX and FB_ARRAY register blocks — 2026-10-18

## Source contract

Pinned input archive: `reference/craft-reference.zip`, SHA-256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`.

| Craft block | Source pins | Behavior | BLOODSKY block |
| --- | --- | --- | --- |
| `FB_MX` (source SHA-256 `318188d6afccf493a7f3f8d4ee16513c3e3511d44b49e0fd1a28a88184592b55`) | `EN: BOOL`, `I: Integer` → `Q: Integer` | When EN is true, read bank[I]; otherwise Q is null. | `FBD CRAFT MX` |
| `FB_ARRAY` (source SHA-256 `f1cd4a9d78ae529aa8949f9f463bc9068cc36daf0fcb835f29a10df3c32b1b40`) | `EN: BOOL`, `DT: Integer`, `ID: Integer` → `Q: Integer` | Write DT to bank[ID] once on EN's rising edge; read bank[ID] for Q. A low EN resets the edge latch. | `FBD CRAFT ARRAY` |

The shared source manager `ru.himik.nightscream.kascada.q.d.b` has `int[2000]`, IDs 0–1999, `I(id)` for read and `T(id,value)` for write. The original methods check IDs above 1999 but omit lower-bound checks. `FbdCraftRegisterStore` uses the same range and Integer values, rejects negative/out-of-range IDs without throwing, shares data between both block types in the running graph, and clears on project load and run entry. Register contents and edge latches are runtime state, not project JSON.

The editor exposes source previews from the actual Craft palette and the separate extended menu. Named graph contracts, source layout and wire anchors are covered by `OriginalFbdNativePaletteMappingTest`; register boundaries and EN rising-edge writes are covered by `FbdCraftRegisterStoreTest` and `FbdCraftRegisterGraphTest`. API30 quick smoke creates both through their original preview actions, checks non-overlap, persistence and cold reload.

The engine represents invalid/null numeric source outputs as non-finite graph values and fails downstream closed. `FB_MACRO` remains unavailable until nested executable graph and namespace storage is implemented. This batch does not claim full FBD completion or pixel-identical parity.
