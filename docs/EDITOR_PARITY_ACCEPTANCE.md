# Editor parity acceptance checklist (2026-10-17)

Target: KaScada Craft 1.0.7 editor familiarity with BLOODSKY branding. This checklist is a verification gate, not a claim of completed implementation.

## Baseline
- Capture original APK and BLOODSKY screenshots at identical viewport, density and orientation.
- Record original screen entry sequence, toolbar rows, menu order, background, grid spacing, text, icons and dimensions.
- Store provenance for every transferred original resource; never infer source behavior from a screenshot alone.

## Editor checks
- Toolbar arrangement, action labels, icons, popup/menu hierarchy and touch targets match the original.
- Component picker displays actual image thumbnails (not names alone), supports scrolling and distinguishes ON/OFF states.
- Selecting a component inserts it at the original default size; move/resize/copy/save/reopen preserve fractional geometry.
- Property dialog includes device/system register, address, bit, state images, state colors and original context actions.
- FBD block palette, port placement, wires and drag behavior are validated separately against original behavior.
- Work/operator mode hides editing affordances and preserves intended runtime interactions.

## Regression gates
- Existing project catalog create/load/delete and portable ZIP import/export continue to pass.
- No loss of media, sound, navigation, unknown metadata or polling-session safety after editor changes.
- Run Python source checks, Android unit tests, APK build and emulator UI smoke test on exact PR head.
- Review matching screenshots before merging; test real-device hardware separately.
- Do not publish a public release or claim full visual parity until the relevant gates pass.

## Next implementation slice
First compare the current editor toolbar and component picker against source-backed original assets. Implement one narrowly scoped UI change with tests and screenshots, rather than merging older PR38 wholesale.
