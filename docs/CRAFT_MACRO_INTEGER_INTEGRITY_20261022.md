# FBD macro integer identity checkpoint — 2026-10-22

Baseline: bbdf1920f1ad0ab8a84ce0ed5c7678d6d511ceca.
Branch: fix/craft-fbd-parity-batch-20261022. PR #353/#354 remain independent.

## Evidence and changes

Original pinned Logic/FB_MACRO.java serializes countIn, typed ports, saveKey,
keyOut and numOnIn as integer topology fields; mbCanvas's component factory
uses discrete component IDs. FbdCraftMacroDefinition and GraphAdapter used
JSONObject.optInt, which truncates fractions and wraps oversized Numbers.
A value 4294967399 could become supported component 103; index 4294967296
could become node 0. This is invalid-data aliasing, not original macro behavior.

Five corrected boundaries:
- Interface counts and input/output type codes are exact signed 32-bit integers.
- Original child component IDs cannot wrap/truncate into a supported factory ID.
- Original saveKey/keyOut/interface targets/pin numbers cannot alias other nodes.
- Native nested graph output/edge indices and input port types are exact.
- Reverse property synchronization rejects malformed source/widget saveKey and
  widget component IDs before returning a changed definition.

CraftExactInteger accepts mathematically exact integral Numbers/numeric strings,
retains absent-key defaults, and rejects explicit null, booleans, nonintegral or
out-of-range values. It does not normalize or delete the original metadata.
Untranslatable original topology stays opaque/non-executable; malformed native
graphs fail validation. Valid integer formats still serialize and reopen.

## Verification

PASS: 271 local Python tests; 166 original resource aliases; git diff --check.
Local Gradle unit/APK attempted, NOT RUN (gradle absent, exit 127).
Added JVM regressions for each boundary, valid string/numeric roundtrip and
opaque preservation. Independent exact-head CI pending at commit creation.
Visual/device/hardware testing NOT RUN; full macro parity is not claimed.

## Continuation

Confirm exact-head CI and obtain human review under SAFE_AUTONOMOUS_AGENT before
merge. Main is unchanged. Next: type-aware FBD operator editor validation;
comparison/arithmetic currently share an overly permissive parser. Keep this
separate from original source-mode defaults and valid imported arithmetic.
