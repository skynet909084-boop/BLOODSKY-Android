# Original FBD catalogue and validated properties

Source: `reference/craft-reference.zip` SHA256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`, decoded `fragment_component_logic_bar{,_portrait}.xml`, `Logic/*.java`, `Logic/VarName/*.java`, `Logic/c/{b,c,d,e,f,g}.java` and `t/c.java`.

`tools/transfer_craft_fbd_palette.py` resolves the original two layouts and generates a readonly native `OriginalFbdDrawing`: source Canvas instructions, constructor/default port labels and measurement fonts. Equivalent drawing bodies are shared. All 40 previews remain graphics, including rectangular/rounded variables, directional names, triangle float arithmetic/comparison, pins and header dividers. Empty macro drawing is explicitly reduced to its default unnamed four-input shape. No executable source code, MQTT, sensor access or database implementation is imported.

Adaptations: fullscreen dialog rather than fragment/drag palette; tap adds at existing editor coordinates; a portrait ScrollView keeps original rows reachable on short screens, with original page/return icons pinned outside scrolling. Native `layout-land` selects the original wide weighted arrangement without the portrait scroll wrapper. Landscape touch/layout has not been emulator-tested in this batch. BLOODSKY's disabled alpha marks unimplemented semantics. No same-viewport original APK screenshot comparison has been performed; these source-backed drawings do not establish pixel-perfect parity.

## Enabled actions and boundaries

Original AND, OR, XOR, TON, POW, SIN, ABS, IntToFloat and FloatToInt thumbnails add the nine corresponding **existing BLOODSKY** types. This maps operations, not original typed/pin-specific execution semantics: the existing graph uses ordered generic connections and double-valued math. It does not implement original missing-input/null behavior, float32/uint16 semantics or timer value pins. The original CTU has +, -, R inputs and a numeric output; source RS uses R,S ordering while the existing engine uses S,R. Those thumbnails and all other unsupported entries stay disabled, rather than silently adding a different block. The next page retains every existing extended widget/FBD action; the portrait left arrow returns to components. Back dismisses the catalogue in either orientation.

## Property validation

Timer 1..3600000 ms and counter 1..1000000 now require valid integers rather than silently clamping/falling back. Both comparison operands must be finite; decimal comma and signed values are accepted. Allowed operators are `> < >= <= == !=` plus original aliases `=` and `<>`. The complete edit validates before any widget field changes. Invalid input keeps the dialog and saved project unchanged. Existing JSON fields, unknown metadata and fractional geometry remain untouched by this change. Existing load/import normalization remains compatible.

## Verification

Local Python source/resource and behavior guards; Android JUnit covers ranges, invalid/non-finite values, aliases and unknown-field/fractional round trip. Full API30 smoke additionally opens and scrolls the catalogue, verifies disabled RS and return/extended navigation, adds AND and TON, rejects timer 0, saves exactly 2345 ms and reopens after process death. CI acceptance results are recorded after the exact-head build and emulator complete.

Initial CI APK run 370 (`39000650547`) and UI run 39 (`39000650530`) stopped before emulator startup because the two original float-variable XML backgrounds were missing. Both source XML files are now compiled unchanged, the generator reproduces them, and regression checks resolve every drawable referenced by both layouts. These failed runs are not acceptance evidence.

The next compile runs (APK 371 `39000754810`, UI 40 `39000754769`) exposed a generator alias collision (`Path path = path`). Field references now retain `this.` through normalization, preserving Java scope; source regression covers the specific collision. Neither run reached the emulator or acceptance.
