# BLOODSKY FBD output → HMI widget runtime binding

Scope: 17 Oct 2026, dedicated draft PR on top of FBD timer/math/editor branches. Do not modify stable main.

## Wiring an FBD output to a visual element

Set an ordinary on-screen widget's **Привязка к данным** to `fbd://N`, where N is the **1-based element index on the same screen** (the numbering shown by FBD wire labels). In operator mode, the evaluated output of that FBD block is displayed by existing HMI indicator, gauge, dynamic text, bit list, photo or text logic:

- FBD BOOL Q → `1` or `0`.
- FBD NUMBER OUT → finite decimal string.
- Missing source / unsupported output / stale or invalid graph → `CFG` (do not present as a valid signal).
- In editor mode stale FBD live values are removed; ordinary Modbus TCP polling remains independent.

Input FBD widgets already accept the existing `tcp://host:port/unit/address` Modbus binding and update `inputValue` from poll results. This connects real Modbus inputs to a BLOODSKY FBD graph and its computed result to displayed HMI widgets **without** silently writing a PLC output.

## Safety and known limitations

- `fbd://N` is positional: deletion now reindexes surviving bindings to preserve source identity; deleting the referenced source marks it `fbd://missing-N` so runtime displays `CFG` instead of silently showing another block. Independent add-at-end does not change existing references. External import/reorder operations still require ID-backed source references before claiming original Craft compatibility.
- This binding updates **presentation values**, not Modbus output coils/registers and not audio event handling outside existing widget flows.
- The runtime evaluates FBD once per Android canvas update; larger timing/runtime performance remains hardware-dependent.
- Original Craft native tag/register/FBD interop, real hardware and pixel-level UI parity are separate acceptance tasks. This extension is BLOODSKY-specific.
- Test `FbdResultBindingTest` rejects malformed/unsafe indices, non-FBD sources, numeric overflow and stale caches. Require exact-head JUnit/debug APK and API30 smoke, no main merge until checked.

Root handoff: `docs/FBD_EXECUTION_HANDOFF_20261017.md`.
