# Exact native Craft FBD wire import from .pro (2026-10-23)

Original KaScada Smart Craft 1.0.7 stores FBD connections as
screen-local component saveKey, outbound keyOut and one-based
target input numOnIn. BLOODSKY converts those fields to editor
graph connections on import.

Previously the importer used Number.intValue() for all three
source fields. A 64-bit key (for example 4294967307) or a
fractional JSON source value could be narrowed to 11, silently
attaching a wire to an unrelated source FBD block. A huge
numOnIn could similarly select the wrong input pin.

Import now reads all original FBD topology IDs through the
exact signed-32-bit CraftExactInteger helper. Invalid
source fields reject the project instead of silently creating
a different executable graph. Valid original keys and pins
are mapped unchanged, and all other original .pro fields
are kept for round-trip. Missing native values preserve
the existing unconnected sentinel behavior.

JVM regressions cover normal AND->OR import and export,
no mutation of original source, 64-bit/fraction/null/array
attacks on keyOut, saveKey and numOnIn, and valid
unwired blocks. This complements the edited wire exporter
fix in PR #382 without claiming original Android visual
or actual PLC parity.
