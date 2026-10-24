# Craft FBD operator editor checkpoint — 2026-10-22

Baseline bbdf1920f1ad0ab8a84ce0ed5c7678d6d511ceca.
Branch fix/fbd-typed-operator-edits-20261022; independent of PR #353–355.

## Source and defect

Pinned Logic/FB_ARITHMETIC_INT.java and FB_ARITHMETIC_FLOAT.java implement
compareAction 1–4 as +, -, *, /. FB_COMPARE.java and FB_COMPARE_FLOAT.java
implement 1–6 as >, <, <=, >=, =, <>. These are separate operator families.

BLOODSKY's property form used one permissive parser for both families. It could
save a comparison in an arithmetic block, or arithmetic in a comparator. Later
runtime/default or reload normalization could replace it with + or >, changing
the operator's selected operation silently.

FbdPropertyEdits adds a widget-type-aware overload, called by the Activity before
mutation. Four original components ID107/112/110/111 now reject the opposite
family; the generic comparator also rejects arithmetic. Existing = and <>
aliases normalize to == and !=. Timer/counter/number parsing and original runtime
arithmetic semantics are unchanged. The arithmetic prompt now shows the accepted
ASCII symbols rather than different Unicode glyphs.

## Tests

New parameterized JVM tests cover all five widget types: full valid/invalid
operator sets, alias normalization, save/reopen, original export action numbers,
unknown metadata retention and rejection before widget changes.
Local Python: PASS 271; resource integrity: PASS 166 aliases; diff check: PASS.
Gradle testDebugUnitTest and assembleDebug attempted locally: NOT RUN (missing
gradle, exit 127). Independent exact-head CI pending at commit creation.
No device UI, screenshot or hardware acceptance performed.

## Next

Check exact-head CI and human review before merge, per SAFE_AUTONOMOUS_AGENT.
Main remains untouched. Do not duplicate #353–355. Further FBD source-mode/type
validation and imported graph runtime acceptance remain open; no full-parity claim.
