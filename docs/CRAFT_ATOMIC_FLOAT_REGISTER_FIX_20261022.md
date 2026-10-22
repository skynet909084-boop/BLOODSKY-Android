# Craft internal FLOAT32 register consistency — 22 Oct 2026

## Why

BLOODSKY's single HMI/FBD app shares project-local Craft system registers
10..1999. A user register can contain a signed or full-width Java int from
Craft FBD ARRAY, JSON restoration, or internal Int/Bit writes. The FLOAT32
representation uses **only the low 16 bits of each adjacent register**.

Before this fix, the direct `CraftSystemRegisterBank.readFloat32` path rejected
such raw ints although the typed access path masked them. The typed
`CraftSystemRegisterAccess.readFloat/writeFloat` path also operated on two
user words separately, allowing a reader or Modbus handler to observe a torn
FLOAT32 value while an FBD scan was writing a pair.

## Behavior now

- Typed FLOAT32 **reads and writes for addresses 10..1998** use the same
  synchronized project-bank operation as direct FLOAT32 access.
- Direct bank reads mask the low 16 bits of full-width/negative raw user words.
- Word/byte swap flags, endian order, bounds, and rejection of nonfinite
  FLOAT32 writes remain unchanged.
- Source-defined service VarOut semantics at 0..9 remain separate. A
  cross-boundary 9->10 write checks for a project bank **before** issuing
  any service write, preventing a missing-bank partial command.
- Nothing about these changes converts the 0..9 service commands into
  ordinary persisted register storage, or changes the imported project JSON
  schema. FBD, HMI and internal Modbus all continue using one active
  project-local system register bank.

## Verification and limitations

Java tests cover all swap combinations, oversized and negative raw words,
invalid writes preserving prior values, end-of-bank bounds, the 9->10
boundary, and concurrent reader/writer non-tearing. The concurrency test
asserts integrity, not throughput or scheduling latency. This change
does **not** establish physical Modbus interoperability, timing parity with
KaScada Smart Craft 1.0.7, or matching original screenshots. Existing
original image/sound aliases are untouched.
