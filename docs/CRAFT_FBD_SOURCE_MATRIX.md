> Historical reference note recovered from PR27. Describes that branch's observations, not verified current main behavior. See LEGACY_PR_CONSOLIDATION.md and reference/README.md for current recovery status.

# KaScada Craft 1.0.7 FBD original-source matrix

Source: decompiled APK artifact `11912849123`, `jadx/sources/ru/himik/nightscream/kascada/Logic/`. Inspected Java `onDraw(Canvas)` methods; do not infer visual parity from PNG resources.

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
| FB_MX | Yes | Same with header/divider | BOOL enable, Integer index, Integer output | Runtime integrated; visual partial |
| FB_POW | Yes | Same with header/divider | Three-section layout | Existing runtime; visual partial |
| FB_ARRAY | No local onDraw | Inherited | EN, DT, ID inputs and Q output; shared Integer register store | Runtime integrated; inherited visual approximation |
| FB_CTU | No local onDraw | Inherited | Inspect base class | Not matched |
| FB_MACRO | No local onDraw | Inherited | Inspect base class | Not matched |
| FB_SEL | No local onDraw | Inherited | Integer select with typed inputs | Runtime integrated; inherited visual approximation |

## Geometry and semantics verified

- Original `FB_AND.onDraw`: `V.set(T, U, width-T, height-U)`; draw fill `-7829368` and stroke `-16777216`. Input port Y = `height/4` and `3*height/4`. Output Y = `(height/3)/2 + height/3` (integer arithmetic).
- Original `FB_XOR`, `FB_RS`, `FB_TIMER`: divider at `height/3`; vertical divider at `width/2` from header to bottom. Input port lines are **conditional on nonempty labels M and O**; output line is conditional on nonempty label Q. Text baseline is determined by `a0`, not arbitrary font centering.
- Original `FB_AND` and `FB_XOR`: output inversion bubble only if `f8973e && R == 1`. This behavior is **not yet faithfully implemented** in BLOODSKY.
- The existing BLOODSKY FBD types are not one-to-one with the original classes (e.g. CTD and NAND variants). **Do not claim they are original until class/mode mapping is verified.**

## Work required before full-clone claim

1. Trace constructor defaults and superclass state (`T`, `U`, `W`, `M`, `O`, `Q`, `a0`, `b0`, `R`, `f8973e`) and reproduce state-dependent drawing.
2. Implement class-specific widgets, not a single generalized FBD renderer.
3. Map original FBD menus, parameters, mode selectors and port behavior to BLOODSKY; compare screenshots on the same density.
4. Verify XML menus/dialogs, original drawable assets, project storage, Modbus semantics, and operator mode independently.
5. Keep custom BLOODSKY improvements out of the baseline clone.

This matrix remains an **audit**, not evidence of full pixel or lifecycle parity. `FB_MACRO` nested graph storage, remaining palette/action coverage, exact same-density drawings, and complete original project/storage behavior remain open. See per-batch runtime evidence before treating any row as accepted.
