# Craft button ID6 and bit list ID20: explicit remap consent — 22 October 2026

Both original-style compact dialogs edit the *source* Craft register while
the connected BLOODSKY operator may have separately approved a Modbus TCP
endpoint. A source-register edit previously retained that write consent,
so an operator could keep sending FC22/FC06 commands on an unrelated old
mapped endpoint while seeing a changed register in the Craft dialog.

- For ID6, changing the register disables both momentary-bit and
  constant-write consent. Changing only the bit disables momentary-bit
  consent. Cosmetic caption/visibility changes leave approvals unchanged.
- For ID20, changing the original register disables its explicit bit-list
  binding. Changing labels, visibility or read-only display without changing
  the register preserves the existing approval.
- Endpoint `connectionInfo`, `craftConnectionInfo`, `craftOther`,
  imported unrecognized metadata and source representation are preserved.
  Nothing automatically remaps a PLC register or activates a legacy endpoint.
- JUnit covers safe no-op/cosmetic edits, changed registers, nested vendor
  metadata, byte-identical endpoint text and immutability of source objects.

This follows the source-backed original compact editors while preserving
BLOODSKY's deliberate operator authorization boundary. Real-device
visual/protocol acceptance remains outstanding.
