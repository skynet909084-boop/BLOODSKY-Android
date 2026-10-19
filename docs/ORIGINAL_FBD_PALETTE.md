# Original FBD catalogue and validated properties

Source: `reference/craft-reference.zip` SHA256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`, decoded `fragment_component_logic_bar{,_portrait}.xml`, `Logic/*.java`, `Logic/VarName/*.java`, `Logic/c/{b,c,d,e,f,g}.java` and `t/c.java`.

`tools/transfer_craft_fbd_palette.py` resolves the original two layouts and generates a readonly native `OriginalFbdDrawing`: source Canvas instructions, constructor/default port labels and measurement fonts. Equivalent drawing bodies are shared. All 40 previews remain graphics, including rectangular/rounded variables, directional names, triangle float arithmetic/comparison, pins and header dividers. Empty macro drawing is explicitly reduced to its default unnamed four-input shape. No executable source code, MQTT, sensor access or database implementation is imported.

Adaptations: fullscreen dialog rather than fragment/drag palette; tap adds at existing editor coordinates; a portrait ScrollView keeps original rows reachable on short screens, with original page/return icons pinned outside scrolling. Native `layout-land` selects the original wide weighted arrangement without the portrait scroll wrapper. The current editor requests landscape, so the full emulator flow exercises the wide page; portrait layout/scroll is generated and source-checked but is not reached by that editor flow. BLOODSKY's disabled alpha marks unimplemented semantics. No same-viewport original APK screenshot comparison has been performed; these source-backed drawings do not establish pixel-perfect parity.

## Enabled actions and boundaries

Original AND, OR and XOR continue to add the existing BLOODSKY Boolean blocks. Original Craft timer, POW, SIN, ABS, IntToFloat and FloatToInt previews now add separate native source-compatible variants with named contacts and source Float32/cast behavior. Native blocks with typed terminals and focused runtime/persistence tests also back RS, T/R/F triggers, CTU, SEL, integer/float compare and arithmetic, CH_I/CH_F, MOVE, MX, ARRAY, LOG and WordToFloat. Numeric wire metadata still uses the shared `NUMBER` category, so it does not distinguish Craft Integer from Float across every connection. MX/ARRAY use a bounded transient 2000-slot Integer bank; invalid IDs fail closed. BLOODSKY-authored macros now support nested graph editing, validated `.mcr` persistence, dynamic typed interfaces and isolated per-instance runtime state. The original `FB_MACRO` palette row remains disabled because opaque Craft child records are preserved but not executed. Hardware-bound sensor, GPS and MQTT previews stay disabled without their device lifecycle. Other unsupported previews remain unavailable rather than silently adding a different block. The next page retains existing extended actions and opens a third macro-editor page for project macros, FBD graph capture, placement and `.mcr` import/export; the portrait left arrow returns to components. Back dismisses the catalogue in either orientation. See `docs/FBD_CRAFT_CONVERSIONS_20261018.md` for conversion source hashes.

## Property validation

Timer 1..3600000 ms and counter 1..1000000 now require valid integers rather than silently clamping/falling back. Both comparison operands must be finite; decimal comma and signed values are accepted. Allowed operators are `> < >= <= == !=` plus original aliases `=` and `<>`. The complete edit validates before any widget field changes. Invalid input keeps the dialog and saved project unchanged. Existing JSON fields, unknown metadata and fractional geometry remain untouched by this change. Existing load/import normalization remains compatible.

## Verification

Local Python source/resource and behavior guards; Android JUnit covers ranges, invalid/non-finite values, aliases and unknown-field/fractional round trip. Full API30 smoke additionally opens the wide catalogue (scrolls if a portrait page is presented), verifies disabled RS and return/extended navigation, adds AND and TON, rejects timer 0, saves exactly 2345 ms and reopens after process death. Accepted results are recorded below.

Initial CI APK run 370 (`39000650547`) and UI run 39 (`39000650530`) stopped before emulator startup because the two original float-variable XML backgrounds were missing. Both source XML files are now compiled unchanged, the generator reproduces them, and regression checks resolve every drawable referenced by both layouts. These failed runs are not acceptance evidence.

The next compile runs (APK 371 `39000754810`, UI 40 `39000754769`) exposed a generator alias collision (`Path path = path`). Field references now retain `this.` through normalization, preserving Java scope; source regression covers the specific collision. Neither run reached the emulator or acceptance.

APK/unit run 372 passed all 133 Android tests. UI run 41 (`39000837006`) reached the legacy fractional-geometry check, then its scroll driver used stale Height history while X was visible and Width was below the viewport after invalid Apply. The saved screenshot/UI XML confirmed the field was below the viewport. Scroll direction now uses observed visible property anchors, bounded shorter gestures and reverses at an observed unchanged boundary; a helper regression reproduces that exact state. All geometry and persistence assertions remain. Wide catalogue return uses Android Back, because the source wide layout has no portrait arrow. This failed run is not acceptance evidence.

## Accepted validation — 2026-10-17

PR67 merged after exact-head `bd9ec2725fefa598bf0038e98b48491619388721` passed both gates. Merge commit `eef08fa93bad1aab8ce9a257a185ab4daf32ddd0` has the same tested tree `9eb9eb47c8eb9c0e68bcc52ac78ba1eb5552090b`.

- Python: 83 passed locally and in CI. Complete branch diff whitespace check passed.
- Android: 133 JUnit tests, failures/errors/skipped 0; XML reports inspected. [APK/unit run 374](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39001572333) passed. APK artifact `12089017579`, reports `12089002631`.
- [UI run 43](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39001572328) passed on API30 Nexus6, density560. Artifact `12089063497` contains `result.json` confirming the full old/new checks. Disabled source RS, wide Back/extended navigation, AND/TON add, rejection of timer0 with unchanged saved widgets, exact2345ms apply and restart retention passed.
- Final captures `02d-original-fbd-palette`, `04n-fbd-invalid-preset`, `04o-fbd-preset-saved`, `04p-fbd-reopened` were reviewed. `04m` PNG is a black transition frame and is excluded from visual evidence; `02d` correctly shows the same entire wide page. Screenshots verify presentation/state, while the smoke compares saved JSON for exact2345ms and all other widget fields.
- Local Gradle unit/build were not run because Gradle/SDK is absent; successful CI supplied independent Android verification. No physical-device or original equal-viewport comparison is claimed.

Long-press FBD actions now cover connect, typed BOOL-wire inversion, disconnect, timer mode/units, properties and existing actions. Remaining parity work includes opaque original Craft macro-child execution, Craft-specific inversion and drawing state, distinct Integer versus Float wire types, exact same-density artwork, and hardware lifecycle.
