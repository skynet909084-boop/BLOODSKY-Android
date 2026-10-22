# Exact native FBD source modes for .mcr graph translation — 23 October 2026

Original factory and source matrix:
- 107/112 FB_COMPARE/FB_COMPARE_FLOAT: compareAction 1..6
- 110/111 FB_ARITHMETIC_INT/FLOAT: compareAction 1..4
- 114 FB_TRIGGER: type 1..3 (T/R/F)
- 115 FB_TIMER: type 1..4 (TON/TOF/TP/BLINK), typeTime 1..3 (MS/SEK/MIN)
- 120 Sensor_Orientation: type 1..3 (X/Y/Z)
- 127 Sunrise_GPS: type 1..2 (sunrise/sunset)

Previous translator used optInt, which truncated fractional values
and wrapped overlarge IDs into valid mode codes; out-of-range native
enums defaulted to a different valid operation (+, >, TON, T,
orientation X or sunrise). This could falsely certify execution of
an unsupported original source graph.

The translator now reads exact signed int source modes and rejects
invalid mode combinations without altering the original .mcr records.
Missing mode values preserve the previous native defaults. This
affects only source-to-executable-graph translation, not arbitrary
Craft metadata import or visual presentation.

JVM cases cover all mode families, valid choices, legacy defaults,
64-bit overflow, fractions, wrong JSON types and out-of-range enums.
Full on-device FBD scans and original UI visuals remain release gates.
