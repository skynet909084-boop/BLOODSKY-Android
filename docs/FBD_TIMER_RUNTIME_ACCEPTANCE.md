# FBD timer runtime — deterministic regression and safe clock epochs

Stacked FBD implementation batch, 17 Oct 2026. Works in new draft PR on top of #102 → #101 → #76. Original stable main is not modified.

## BLOODSKY semantics implemented

- TON: while IN true, Q rises exactly at PT elapsed milliseconds, and falls/reset immediately on IN false.
- TOF: while IN true, Q true; on falling IN, Q stays true for PT, then false; later IN true rearms.
- TP: on IN rising edge, output pulse remains active for PT regardless of falling IN; held high cannot retrigger until a new low→high transition.
- All timers use monotonic Android `SystemClock.elapsedRealtime()`; backwards/outdated timer epochs are discarded or restarted safely.
- PT is clamped to 1..3600000 ms at runtime, with exact integer property validation unchanged.
- Project reload / entering operator mode resets timer clock epochs, not presets/metadata.
- Editor render does not execute stateful timers, counters or triggers (PR #101).

## Checks and limits

`FbdTimerTransitionsTest` tests exact TON/TOF/TP deadline boundaries, repeated evaluations, fallback, hold/re-arm and invalid/backwards timestamps with a deterministic fake clock.

Require exact-head Python regression + Java JUnit/APK + Android API30 smoke before merging the PR into its parent. No production signing or auto-merge.

**Important:** These are documented BLOODSKY timer conventions, NOT confirmed bit-level equivalence to original KaScada Craft. Original class `FB_TIMER.java` has TON/TOF/TP labels, but source superclasses/port data still need proof of exact units, mode behavior and project migration. Original disabled source previews must remain disabled until verified. See `docs/ORIGINAL_FBD_SOURCE_PORT_EVIDENCE.md`.
