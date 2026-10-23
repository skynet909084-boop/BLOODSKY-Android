> Historical reference note recovered from PR27. Describes that branch's observations, not verified current main behavior. See LEGACY_PR_CONSOLIDATION.md and reference/README.md for current recovery status.

# KaScada Craft 1.0.7 FBD original-source matrix

Source: decompiled APK artifact `11912849123`, `jadx/sources/ru/himik/nightscream/kascada/Logic/`. Inspected Java `onDraw(Canvas)` methods; do not infer visual parity from PNG resources.

Source-driven project evidence is tracked separately in [the project fixture audit](PROJECT_SOURCE_FIXTURE_AUDIT_20261023.md): the five supplied sanitized `.pro` samples contain no ID 135 macro instance, and the inspected project ZIPs provide no `.mcr` sidecar. Existing macro runtime tests therefore remain contract/synthetic evidence until an original nested-macro project is available.

| Original Java class | Custom onDraw | Body | Ports / text | BLOODSKY parity |
|---|---|---|---|---|
| FB_AND | Yes | #888888 rectangular fill, black stroke | AND centered, two inputs at 1/4 and 3/4, one output centered, conditional inversion circle | Partial |
| FB_OR | Yes | Same | OR centered, same port layout | Partial |
| FB_XOR | Yes | Same with header and vertical divider | XOR header, conditional M/O/Q text and port lines, conditional inversion circle | Partial |
| FB_RS | Yes | Same with header and vertical divider | RS header, conditional M/O/Q text and port lines, conditional inversion circle | Partial |
| FB_TIMER | Yes | Same with header and vertical divider | TON/TOF/TP/BLINK mode; configurable time unit MS/SEK/MIN; conditional labels | Partial |
| FB_TRIGGER | Yes | Same with header and vertical divider | T/F/R mode, conditional labels and pins | Runtime integrated; visual partial |
| FB_COMPARE | Yes | Same | Comparison operator >, <, <=, >=, =, <>; quarter-height input pins | Runtime integrated; visual partial |
| FB_COMPARE_FLOAT | Yes | Same | Compare layout | Runtime integrated; visual partial |
| FB_ARITHMETIC_INT | Yes | Same | Arithmetic layout | Runtime integrated; visual partial |
| FB_ARITHMETIC_FLOAT | Yes | Same | Arithmetic layout | Runtime integrated; visual partial |
| FB_CHANGE_I | Yes | Same with header/divider | Three-section layout | Runtime integrated; visual partial |
| FB_CHANGE_F | Yes | Same with header/divider | Three-section layout | Runtime integrated; visual partial |
| FB_MOVE | Yes | Same with header/divider | Three-section layout | Runtime integrated; visual partial |
| Sensor_Orientation | Yes | #888888 rectangle, black stroke | ORIENTATION_X/Y/Z label selected by `type`; one centered Q output | Runtime integrated; default visual source skin added, parity partial |
| Sensor_Light | Yes | #888888 rectangle, black stroke | LM label; one centered Q output | Runtime integrated; default visual source skin added, parity partial |
| Sensor_Speed | Yes | #888888 rectangle, black stroke | SM label; one centered Q output | Runtime integrated; default visual source skin added, parity partial |
| Sunrise_GPS | Yes | #888888 rectangle, black stroke | SR/SS label selected by `type`; one centered Q output | Runtime integrated; default visual source skin added, parity partial |
| ODTEMP | Yes | #888888 rectangle, black stroke | DT label; one centered Q output; source `draw12` | Runtime integrated; default visual source skin added, parity partial |
| FB_MX | Yes | Same with header/divider | BOOL enable, Integer index, Integer output | Runtime integrated; visual partial |
| FB_POW | Yes | Same with header/divider | Three-section layout | Existing runtime; visual partial |
| FB_ARRAY | No local onDraw | Inherited | EN, DT, ID inputs and Q output; shared Integer register store | Runtime integrated; inherited visual approximation |
| FB_CTU | No local onDraw | Inherited | Inspect base class | Not matched |
| FB_MACRO | No local onDraw | Inherited | `.mcr` sidecar stores child graph and typed input/output interface | Imports a matching `<name>.mcr`, executes when every child block and edge maps to verified BLOODSKY semantics; unsupported graphs remain visible and non-executable |
| FB_SEL | No local onDraw | Inherited | Integer select with typed inputs | Runtime integrated; inherited visual approximation |
| Var | Yes, draw0 | Gray rectangle, black stroke | Register/bit or constant label; Q output with optional invert bubble | Runtime integrated; default source drawing added, parity partial |
| VarOut | Yes, draw1 | Gray rectangle, black stroke | S# address label; IN contact | Runtime integrated; default source drawing added, parity partial |
| VarInt | Yes, draw2 | Gray rounded rectangle, black stroke | Register or constant label; Q output | Runtime integrated; default source drawing added, parity partial |
| VarOutInt | Yes, draw3 | Gray rounded rectangle, black stroke | S# address label; IN contact | Runtime integrated; default source drawing added, parity partial |
| VarFloat | Yes, draw4 | Gray beveled polygon, black stroke | Register or constant label; Q output | Runtime integrated; default source drawing added, parity partial |
| VarOutFloat | Yes, draw5 | Gray beveled polygon, black stroke | S# address label; IN contact | Runtime integrated; default source drawing added, parity partial |
| VarNameIn | Yes, draw6 | Right-facing gray arrow shape, black stroke | Variable name; Q output | Runtime integrated; default source drawing added, parity partial |
| VarNameOut | Yes, draw7 | Left-facing gray arrow shape, black stroke | Variable name; IN contact | Runtime integrated; default source drawing added, parity partial |

## Geometry and semantics verified

- Original `FB_AND.onDraw`: `V.set(T, U, width-T, height-U)`; draw fill `-7829368` and stroke `-16777216`. Input port Y = `height/4` and `3*height/4`. Output Y = `(height/3)/2 + height/3` (integer arithmetic).
- Original `FB_XOR`, `FB_RS`, `FB_TIMER`: divider at `height/3`; vertical divider at `width/2` from header to bottom. Input port lines are **conditional on nonempty labels M and O**; output line is conditional on nonempty label Q. Text baseline is determined by `a0`, not arbitrary font centering.
- Original `FB_AND` and `FB_XOR`: output inversion bubble only if `f8973e && R == 1`. BLOODSKY evaluates persisted `invertOut` for supported BOOL Q blocks when their required, type-compatible inputs are connected. The class-specific bubble visibility and geometry still need screenshot parity verification.
- The existing BLOODSKY FBD types are not one-to-one with the original classes (e.g. CTD and NAND variants). **Do not claim they are original until class/mode mapping is verified.**

## Work required before full-clone claim

1. Trace constructor defaults and superclass state (`T`, `U`, `W`, `M`, `O`, `Q`, `a0`, `b0`, `R`, `f8973e`) and reproduce state-dependent drawing.
2. Implement class-specific widgets, not a single generalized FBD renderer.
3. Map original FBD menus, parameters, mode selectors and port behavior to BLOODSKY; compare screenshots on the same density.
4. Verify XML menus/dialogs, original drawable assets, project storage, Modbus semantics, and operator mode independently.
5. Keep custom BLOODSKY improvements out of the baseline clone.

This matrix remains an **audit**, not evidence of full pixel or lifecycle parity. `FB_MACRO` nested graph storage, remaining palette/action coverage, exact same-density drawings, and complete original project/storage behavior remain open. See per-batch runtime evidence before treating any row as accepted.


## Complete factory → importer ID matrix (101–140)

This table records the original factory class and the exact `other` keys read by `mbCanvas.m(...)`, then the current BLOODSKY `FbdCraftMacroGraphAdapter.typeForComponentId()` result. It is a mapping audit, not a claim that all mapped fields, execution behavior, or rendering are at parity. Dynamic VarName IDs 133/134 depend on a valid `typeVar`; Craft ID 135 is kept visible as a typed block while execution remains gated on a validated matching `.mcr` definition.

Source: pinned decompilation artifact [run 38600957172](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38600957172), SHA-256 `d78bd703d6096ac12d69e19f8b5b5dfa9a4c25e342ce9af0ef8b42326aaa3f8c`. JADX factory `mbCanvas.java` nested switch covers 101–140; Smali `mbCanvas.smali` packed-switch begins at `0x65` (101). The decompiled ID 128 case is emitted as a resource constant comment, but the Smali key is numeric 128 and the constructed class is `FB_MX`.

All 40 IDs have an original factory branch and a BLOODSKY adapter type mapping. The adapter deliberately exposes ID 135 (`FB_MACRO`) even when its sidecar is missing; the importer leaves the block non-executable until a matching, validated definition supplies a supported child graph. ID 133/134 can still fail closed for an invalid variable type. Mapped type names do not establish exact properties, FBD pin compatibility, runtime semantics, save/reopen of macro child records, or visual parity. Existing source-by-source renderer/runtime findings remain in the rows above and require per-class acceptance.

| Craft ID | Factory class | Keys read from `other` | BLOODSKY adapter mapping |
|---:|---|---|---|
| 101 | `Var` | `bit, constant, invertOut, isConstant, keyOut, numOnIn, numScreen, reg, saveKey` | FBD CRAFT VAR BOOL |
| 102 | `VarOut` | `bit, invertOut, keyOut, numOnIn, numScreen, reg, saveKey` | FBD CRAFT VAR OUT BOOL |
| 103 | `FB_AND` | `invertOut, keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD AND |
| 104 | `FB_OR` | `invertOut, keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD OR |
| 105 | `VarInt` | `constant, isConstant, keyOut, numOnIn, numScreen, reg, saveKey` | FBD CRAFT VAR INT |
| 106 | `VarOutInt` | `invertOut, keyOut, numOnIn, numScreen, reg, saveKey` | FBD CRAFT VAR OUT INT |
| 107 | `FB_COMPARE` | `compareAction, invertOut, keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT COMPARE |
| 108 | `VarFloat` | `constant, invertOut, isConstant, keyOut, numOnIn, numScreen, reg, saveKey` | FBD CRAFT VAR FLOAT |
| 109 | `VarOutFloat` | `invertOut, isHighByte, isHighRegister, keyOut, numOnIn, numScreen, reg, saveKey` | FBD CRAFT VAR OUT FLOAT |
| 110 | `FB_ARITHMETIC_INT` | `compareAction, keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT ARITHMETIC INT |
| 111 | `FB_ARITHMETIC_FLOAT` | `compareAction, keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT ARITHMETIC FLOAT |
| 112 | `FB_COMPARE_FLOAT` | `compareAction, invertOut, keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT COMPARE FLOAT |
| 113 | `FB_RS` | `invertOut, keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD RS |
| 114 | `FB_TRIGGER` | `invertOut, keyOut, nMcr, numOnIn, numScreen, saveKey, type, unIdMcr` | FBD CRAFT T |
| 115 | `FB_TIMER` | `invertOut, keyOut, nMcr, numOnIn, numScreen, saveKey, type, typeTime, unIdMcr` | FBD CRAFT TIMER |
| 116 | `FB_CTU` | `invertOut, keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT CTU |
| 117 | `FB_MOVE` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT MOVE |
| 118 | `IntToFloat` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT INT TO FLOAT |
| 119 | `FloatToInt` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT FLOAT TO INT |
| 120 | `Sensor_Orientation` | `keyOut, nMcr, numOnIn, numScreen, saveKey, type, unIdMcr` | FBD CRAFT SENSOR ORIENTATION |
| 121 | `SIN` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT SIN |
| 122 | `LOG` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT LOG |
| 123 | `ABS` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT ABS |
| 124 | `FB_POW` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT POW |
| 125 | `FB_XOR` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD XOR |
| 126 | `FB_SEL` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT SEL |
| 127 | `Sunrise_GPS` | `keyOut, latitude, longitude, nMcr, numOnIn, numScreen, saveKey, type, unIdMcr` | FBD CRAFT SUNRISE |
| 128 | `FB_MX` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT MX |
| 129 | `WordToFloat` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT WORD TO FLOAT |
| 130 | `ODTEMP` | `ip, keyOut, nMcr, numOnIn, numScreen, saveKey, typeResponse, typeSensor, unIdMcr` | FBD CRAFT ODTEMP |
| 131 | `Sensor_Light` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT SENSOR LIGHT |
| 132 | `Sensor_Speed` | `keyOut, nMcr, numOnIn, numScreen, saveKey, unIdMcr` | FBD CRAFT SENSOR SPEED |
| 133 | `VarNameIn` | `constant, invertOut, isConstant, keyOut, nMcr, numOnIn, numScreen, saveKey, typeVar, typeVarValue, unIdMcr` | namedVariableType(false, typeVar) |
| 134 | `VarNameOut` | `keyOut, nMcr, numOnIn, numScreen, saveKey, typeVar, unIdMcr` | namedVariableType(true, typeVar) |
| 135 | `FB_MACRO` | `countIn, keyOut, keyOutIn1, keyOutIn2, keyOutIn3, keyOutIn4, nameIn1, nameIn2, nameIn3, nameIn4, nameOut, numOnIn, numOnIn1, numOnIn2, numOnIn3, numOnIn4, numScreen, saveKey, saveKeyOut, typeIn1, typeIn2, typeIn3, typeIn4, typeOut, uniqueId` | `FBD CRAFT MACRO`; visible placeholder, executable only for a supported validated `.mcr` graph |
| 136 | `FB_ARRAY` | `keyOut, numOnIn, numScreen, saveKey` | FBD CRAFT ARRAY |
| 137 | `FB_CHANGE_I` | `keyOut, numOnIn, numScreen, saveKey` | FBD CRAFT CHANGE I |
| 138 | `FB_CHANGE_F` | `keyOut, numOnIn, numScreen, saveKey` | FBD CRAFT CHANGE F |
| 139 | `MQTT_SUBSCRIBER` | `keyOut, numMqttServer, numOnIn, numScreen, saveKey, topic, typeQ` | FBD CRAFT MQTT SUB BOOL |
| 140 | `MQTT_PUBLISHER` | `keyOut, mqttRetain, numMqttServer, numOnIn, numScreen, saveKey, topic, typeIn` | FBD CRAFT MQTT PUB BOOL |

