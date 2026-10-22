# Exact factory IDs and screen indices on Craft import — 22 Oct 2026

Original KaScada 1.0.7 factory IDs and component `numScreen` are
positive signed integers with 1-based screen numbering. The importer
previously used `Number.intValue()` for JSON numerics, silently wrapping
out-of-range values and truncating fractions.

A malformed source ID `4294967304` (8 + 2^32) could therefore become
supported numeric setter ID8. Similarly `4294967311` could be interpreted
as archive ID15, causing unrelated source fields containing `cloud` to
be scrubbed. An explicit `numScreen=4294967297` could be silently
remapped to screen 1 instead of being rejected.

The integer parser now rejects overflow, fractions and nonfinite numbers.
Explicit invalid `numScreen` is rejected in both import passes, before
committing any project. An omitted screen index continues to default to
screen 1, matching previous compatible imports. Genuine IDs 8/14/15
remain supported and unchanged.

Tests protect unknown factory data from reinterpretation, validate
screen-number bounds and verify normal 1-based source projects still
import. No output endpoints are auto-enabled. Physical Android and original
pixel-parity acceptance remain outstanding.
