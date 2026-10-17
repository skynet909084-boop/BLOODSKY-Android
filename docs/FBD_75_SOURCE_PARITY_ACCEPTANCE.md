# FBD #75 — source-parity implementation and acceptance checklist

Issue: #75. This document defines testable gates, not a claim that the features are implemented.

## Baseline and source of truth

- `docs/ORIGINAL_FBD_PALETTE.md` documents nine existing enabled BLOODSKY actions: AND, OR, XOR, TON, POW, SIN, ABS, IntToFloat, FloatToInt. Do not enable other source previews until their behavior is implemented.
- Use `reference/craft-reference.zip` and decoded original menus `popup_logic_fb_and_or`, `popup_logic_fb_timer`, `popup_logic_fb_rs` to verify pin names, order, value types, inversion behavior and units before coding. Keep a citation to each source class/layout in the PR.
- The current graph's ordered generic connections and double-valued evaluation are **not** equivalent to the original typed, pin-specific execution. Preserve old projects and avoid silently changing their behavior.
- Original CTU has `+`, `-`, `R` inputs; original RS uses `R`, `S`, while the existing engine uses `S`, `R`. Keep both disabled until exact semantics and tests exist.

## Implementation gates

1. **Pin identity**: represent source block ID, source output pin, destination block ID and destination input pin without conflating pin order with wire creation order. Define types only where confirmed by original source. Reject invalid and mismatched pins without mutating the graph.
2. **Wiring**: connecting/disconnecting affects only the selected edge; duplicate edge handling and cycle detection are deterministic. Dragging a block recomputes wire endpoints from block/pin geometry rather than storing stale screen coordinates.
3. **Inversion**: show inversion only for original-supported outputs. Persist inversion state and apply it at the correct stage of evaluation; never interpret a numeric output as Boolean without source evidence.
4. **Timers**: derive timer modes and units from source. Existing TON presets in milliseconds (including 2345 ms) must survive load, edit, save and process restart without implicit conversion or truncation. Invalid inputs must leave the project unchanged.
5. **Persistence**: preserve unknown connection metadata, fractional geometry and existing legacy edge records; a project round-trip must not erase unknown JSON fields.
6. **UX**: match the original popup's ordering and enabled/disabled affordances as evidenced by source; do not claim pixel parity without same-viewport screenshot comparison.

## Required tests and evidence

- Unit tests: typed pin validation, duplicate edge, disconnect, inversion (where supported), graph cycles, timer units/modes, invalid properties, legacy JSON and unknown metadata round-trip.
- UI API30 smoke: add AND and TON; connect and disconnect a wire; move a block; save; kill and reopen process; verify wiring and 2345 ms preset are unchanged.
- GitHub Actions: Python suite, Android JUnit XML results, `assembleDebug` artifact and emulator smoke must pass for the **exact PR head SHA**. Attach workflow URLs, APK artifact ID and smoke result JSON to the PR.
- Stop if original source does not establish a behavior, or if any check fails. Maximum two automated repair attempts. Do not merge automatically, sign a release, enable paid APIs or write to `main`.

## Status

Documentation gate prepared. No new FBD behavior or Android test is claimed by this commit.
