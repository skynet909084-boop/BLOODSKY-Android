# Atomic Modbus TCP batch semantics — 22 October 2026

BLOODSKY serves KaScada Craft-style 16-bit user registers 10–1999
and maps coil N to bit N%16 in register N/16. Service registers 0–9
are unavailable to the Modbus TCP slave.

Prior FC03/04/01/02 loops read each word/bit under an independent lock
and FC16/15 loops wrote each separately. Concurrent FBD scans, Float32
writes or independent TCP sessions could observe or publish an
intermediate half-updated register pair/multiword bit state.

The register bank now owns synchronized range methods for all these
functions: validate the full range and values, then snapshot or mutate
the entire register/bit batch while holding the project bank lock.
FC05/06/22 and FC23 retain their existing atomic per-request behavior.
No Modbus function code, MBAP layout or wire address is changed.

JUnit verifies legal FC15/16/FC02/FC04 responses, 18 bits crossing two
registers, rejected invalid batches without partial mutation, and
concurrent FC16 writes/FC03 reads of a Float32 pair without torn values.

These are protocol correctness improvements consistent with the
Craft register representation, not evidence of hardware or visual parity.
