# Fractional geometry in properties

Opening and applying properties previously rounded x/y/width/height to integers. It also clamped negative imported coordinates and small imported sizes, so changing text could silently alter valid geometry.

The fields now display round-trippable float strings and accept signed decimal pixels. All four fields validate before any widget mutation. Coordinates use -100000..100000 and sizes 1..100000, matching existing import validation. Commas are accepted as decimal separators. Non-finite, malformed and out-of-range values leave the dialog open and the widget unchanged; no fallback or silent clamp is applied. Unknown metadata is preserved by the existing serialization path.

Unit coverage checks repeated unchanged edits, exact boundary and fractional values, commas, non-finite input, blank fields and out-of-range input before float conversion. The emulator selects the original lamp, tries an invalid blank width and confirms no partial change, applies fractional geometry, restarts the process and verifies the saved coordinates/sizes. This fixes the existing properties form; the complete original preference/property UI remains a separate transfer.
