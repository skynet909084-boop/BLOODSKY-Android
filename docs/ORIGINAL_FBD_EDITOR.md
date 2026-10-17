# Source FBD editor menus, canvas and graph actions

Reference archive SHA256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`. Source `Logic/FB_AND`, `FB_OR`, `FB_XOR`, `FB_TIMER`, `FB_POW`, `SIN`, `ABS`, `IntToFloat`, `FloatToInt`, their drawing bases, and `popup_logic_fb_{and_or,timer,pow,timer_edit_type,timer_edit_type_time}.xml` were inspected. The source RS input order is R,S; the legacy BLOODSKY RS order S,R remains unchanged.

## Product behavior

- Default-style supported FBD canvas blocks now share source drawing methods with the catalogue: grey body, black outlines/pins, white type/port labels and Boolean inversion bubble. AND/OR quarter-height inputs and XOR/timer divided bodies are source-based. Fractional saved geometry is retained with a canvas scale to integer View dimensions. Eleven existing types are covered (the nine catalogue actions plus TOF/TP). Custom caption edits remain visible. Existing nondefault color/text/frame styles retain the previous renderer and its inversion marker.
- Long press on FBD opens original source row hierarchy/order. Connect, invert and disconnect are wired; numeric output blocks omit inversion. Properties and the former copy/align/delete menu remain reachable through dialog actions. The menu is a native dialog adaptation of source popup content, not a claim of exact popup placement.
- Connect lists the next available ordered input on each target. The existing self/duplicate/cycle guards apply. Native wires use the source input locations and follow block movement. Disconnect removes only outgoing edges; incoming/unrelated edges and their unknown metadata survive. Legacy manually wired graphs retain their original ordering, extra inputs and fanout.
- Boolean inversion is applied once in the cached evaluator and is reflected in downstream Boolean and numeric consumers. Boolean outputs now propagate numeric 0/1 instead of leaving the numeric cache at 0. Numeric math outputs ignore Boolean inversion metadata.
- Timer mode menu selects existing TON/TOF/TP behavior and resets transient timer state. BLINK is visibly disabled. Source MS/SEK/MIN rows open a constant-duration editor; finite durations from 1ms to 1h must resolve to integer milliseconds. Decimal comma is accepted. Leaving a rounded repeating minutes display unchanged preserves the original exact preset. Cancel/invalid input does not mutate the project.
- FBD menu and connection edits publish synchronously. Snapshot failures prevent mutation; failed publication/mutation restores independent widget/connection copies and refreshes the screen snapshot. The duration dialog resolves the current widget again after a failed save, so retry does not edit a detached snapshot.

## Persistence and compatibility

New owned optional widget keys: `bloodskyFbdInvertOut` (Boolean) and `bloodskyFbdTimeUnit` (`MS`, `SEK`, `MIN`). Defaults are false/MS. Legacy widgets without these keys do not acquire them during unrelated saves; imported unknown fields and `other` object/string forms remain preserved. Existing connection JSON is unchanged. Copy/screen snapshots retain the new state independently.

This is the existing ordered, untyped BLOODSKY execution graph. It does not implement original typed/null port semantics, original RS/CTU mappings, a timer numeric time input, BLINK, macros or original database compatibility. The unit choice edits a stored constant duration; it does not claim original source register/unit semantics. Canvas new-item sizes remain existing BLOODSKY sizes. Source AND/OR layout labels and drawing do not change legacy multi-input truth tables.

## Verification

Python source/layout/resource contracts, Android JUnit evaluator/metadata/transaction/duration/port tests, and full API30 UI smoke. New UI checks cover independent block placement, source canvas/context menu, inversion, connect and rejected reverse cycle, TOF mode, invalid then exact 2.5-second duration, graph/mode/unit/preset after process death, block drag with unchanged edges and output disconnect after restart. The workflow budget is 35 minutes for the expanded full scenario; all prior assertions remain. Capture waits for UI-idle before obtaining pixels to avoid the previously observed black transition frame.

Local Gradle unit/build are unavailable; independent CI results and final screenshot review are required before acceptance. No equal-viewport original APK comparison or physical hardware support is claimed.
