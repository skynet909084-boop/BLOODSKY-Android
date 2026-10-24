# BLOODSKY FBD — source canvas + time unit acceptance, 17 Oct 2026

This direct implementation is stacked on working [PR #76](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/76), tested merged baseline `87f1aa1ada09580a1bfd7568bf0f060dd09aec58`. Stable `main` remains untouched. **No automatic claim of full original Craft parity.**

## Implemented

1. `FbdSourceCanvasSkin` draws original source-derived default-state **gray #888888 bodies, black outlines and pins, white type/port labels** for enabled source-backed AND, OR, XOR and TON/TOF/TP, plus numerical comparison. AND/OR/compare keep quarter-height inputs; XOR and timer split lower cells at 1/2 and 5/6 of height per the source `Logic/FB_XOR.java` / `Logic/FB_TIMER.java` default drawing documented in `docs/CRAFT_FBD_SOURCE_MATRIX.md` and `OriginalFbdDrawing.java`. Wire endpoints and drawn pins share that geometry. Default border/color/text styles use the gray skin; custom styles and captions retain the earlier editable BLOODSKY renderer. No logic executes inside drawing.
2. `FbdDurationUnits` exact BigDecimal MS/SEK/MIN conversion in the existing FBD timer properties. The canonical stored preset remains an integer 1..3600000ms; the display may be a rounded decimal, but unchanged text retains the **original exact integer milliseconds** even for repeating minute fractions. The edit rejects fractional milliseconds, NaN, infinity, zero, negative and overflow. Decimal comma is accepted.
3. TON/TOF/TP modes can be selected in the same properties screen; changing the mode clears transient monotonic timer epochs/previous edge latch without touching wires, user captions, unknown JSON or the saved duration. `bloodskyFbdTimeUnit` is a BLOODSKY-specific optional editor view preference; legacy files default to MS, with no global project schema rewrite.
4. New `FbdDurationUnitsTest` and updated `FbdWireRuntimeAndGeometryTest` cover round trips, invalid conversions, type changes, custom canvas fallback and source-header port positions. Prior smoke script must still find `Время таймера, мс` by accessibility label for default TON and verify exact 2345ms after restart.

## Verification gates

- Exact-head Python regressions, Android JUnit/debug APK and Android 11/API30 UI smoke including prior FBD connect, drag, invert, undo/redo, legacy PNG/WAV migration.
- Review final screenshot for editor gray/source-backed blocks and source wire endpoints, and persisted original old project fields.
- Merge into **feature** #76 only after all checks green, then re-run combined audit/JUnit/APK/API30.
- `main` stays unchanged. Original full CTU numeric +/−/R, RS priority, BLINK semantics, native timer mode metadata, 40 disabled palette previews and original pixel-parity/hardware validation remain separate unresolved gates. Do not enable unsupported source previews.

## Continuity

This is an *implementation* batch, not a new agent assignment. The canonical state is #76 merge SHA, active PR head/actions results, and `docs/FBD_EXECUTION_HANDOFF_20261017.md`. On completion, update the central issue #75 and the execution handoff with the exact tested SHA/results to avoid repeating accepted work.
