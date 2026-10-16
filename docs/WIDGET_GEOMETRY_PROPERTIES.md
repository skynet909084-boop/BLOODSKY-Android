# Fractional geometry in properties

Opening and applying properties previously rounded x/y/width/height to integers. It also clamped negative imported coordinates and small imported sizes, so changing text could silently alter valid geometry.

The fields now display round-trippable float strings and accept signed decimal pixels. All four fields validate before any widget mutation. Coordinates use -100000..100000 and sizes 1..100000, matching existing import validation. Commas are accepted as decimal separators. Non-finite, malformed and out-of-range values leave the dialog open and the widget unchanged; no fallback or silent clamp is applied. Unknown metadata is preserved by the existing serialization path.

Unit coverage checks repeated unchanged edits, exact boundary and fractional values, commas, non-finite input, blank fields and out-of-range input before float conversion. The emulator selects the original lamp, tries an invalid blank width and confirms no partial change, applies fractional geometry, restarts the process and verifies the saved coordinates/sizes. This fixes the existing properties form; the complete original preference/property UI remains a separate transfer.

The first emulator attempt exposed fullscreen landscape IME extraction and ambiguous dialog scroll containers. Property fields now request non-extracting input. The smoke helper identifies the form by its editable descendants, requires fully visible fields, checks actual focus and entered text, and dismisses the observed emulator keyboard before moving to another field. No geometry assertions were removed.

A second emulator attempt confirmed non-extracting input but exposed API 30 accessibility reporting a cleared numeric field as its hint ("Ширина"). The input helper recognizes only the known hint for an empty field; nonempty values still require exact text. The subsequent invalid-apply/no-partial-mutation assertion remains mandatory.

The failure XML contains only the application window, so keyboard visibility uses observed `dumpsys input_method` state instead of assuming IME nodes appear in UIAutomator. Scroll direction follows the requested field order, including returning from Height to Width after validation. Helpers have dedicated regression tests.
