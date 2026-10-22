# Two-word numeric register span validation — 22 October 2026

KaScada Craft component ID4 numeric indicator source types 2 (FLOAT32)
and 3 (INT32) read two adjacent 16-bit Modbus registers. ID8 numeric
setter source mode 1 (FLOAT32) writes two adjacent registers. The valid
Modbus PDU address range ends at 65535 inclusive, so a two-register
request must start no later than 65534.

The original-style compact editors now reject start 65535 for these
two-word data types, while allowing single-word INT16/UINT16 settings
at the last register. Imported source records remain untouched;
unknown source type widths remain opaque. This is a runtime-address
correctness guard, not a claim the original KaScada dialog rejected
the same input or that pixel parity has been proven.

JVM tests cover each numeric type, 65534/65535 boundary behavior,
unchanged JSON and original connection metadata, and revocation of
numeric write approval when changing the target. Device/hardware
acceptance remains a release gate.
