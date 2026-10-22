# Craft property-record integrity checkpoint — 2026-10-22

Integration baseline verified against GitHub: `bbdf1920f1ad0ab8a84ce0ed5c7678d6d511ceca`.
Work branch: `fix/craft-property-record-integrity-20261022`.

## Evidence and four corrected component paths

Pinned `reference/craft-reference.zip`, SHA-256
`316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`,
contains `jadx/sources/ru/himik/nightscream/kascada/mbCanvas.java`.
Factory cases 7, 11, 12 and 24 parse both connectionInfo (`str2`) and other
(`str3`) as JSON objects, with error handling for invalid records.

| ID | Original class | BLOODSKY corrected editor |
|---|---|---|
| 7 | CustomDynText | CraftDynamicTextProperties.withSettings |
| 11 | CustomComponents.r | CraftProgressBarProperties.withSettings/apply |
| 12 | CustomComponents.g | CraftThresholdGaugeProperties.withSettings/apply |
| 24 | CustomComponents.m | CraftGraduatedGaugeProperties.withSettings/apply |

At the baseline, these editors replaced explicit arrays, booleans, numbers
and JSON null with empty records. An ordinary property edit could therefore
silently delete opaque imported data. This is a preservation defect, not a
claim about original runtime behavior for malformed projects.

`CraftPropertyRecord.copy` now distinguishes absent records (new components)
from present unsupported records (edit rejected). Valid objects and encoded
object strings retain their representation and unknown nested keys. The three
in-place apply paths validate before changing any runtime appearance fields.
Active endpoints and write permissions are not changed by this patch.

## Verification

- Local Python suite: PASS, 271 tests.
- Original image/audio integrity: PASS, 166 byte-identical aliases.
- `git diff --check`: PASS.
- Local Gradle unit tests and assembleDebug: NOT RUN; both commands attempted,
  but `gradle` is not installed (exit 127).
- Added parameterized JVM regressions for all four editor paths: invalid source
  types, input immutability, nested unknown fields, encoded/object formats,
  persistence/reopen, absent records and direct-apply rejection.
- Independent GitHub Actions at PR HEAD: pending when this checkpoint was written.
- Same-density visual comparison, emulator, hardware: NOT RUN.

## Continuation

PR #343–352 were already merged before this work; do not repeat them.
Check this branch's exact-head APK/JUnit/Python run, then request human review
per `BLOODSKY_SAFE_AUTONOMOUS_AGENT.md`; do not auto-merge.
Next inspect the same unsupported-record fallback in remaining property helpers,
then tackle source-backed FBD and import/export gaps. Existing parity matrices
contain historical stale rows (e.g. ID14); use current code and per-change evidence.
All four component parity statuses remain partial. Stable main is untouched.
