# Craft control-record integrity — 2026-10-22

Verified integration baseline: `bbdf1920f1ad0ab8a84ce0ed5c7678d6d511ceca`.
Branch: `fix/craft-control-record-integrity-20261022`.
Independent sibling of PR #353; both contain the identical shared parser file.
Neither depends on unmerged changes to the other's component editors.

## Source → discrepancy → correction

Pinned original `mbCanvas.java` cases 6 (button), 8 (numeric setter), and
20 (CustomList) read connectionInfo and other with JSONObject constructors.
Case 20 additionally parses `textBit` as an encoded object containing sixteen
`textBit0`…`textBit15` fields. Existing BLOODSKY supports both encoded and
object-valued records, preserving opaque fields.

Four corrected paths:
1. ID6 compact button edit: reject unsupported source/other records.
2. ID8 numeric settings: reject unsupported records instead of dropping fields
   such as source password/vendor metadata.
3. ID20 list settings: reject unsupported source/other records.
4. ID20 nested textBit record: reject unsupported values before replacing labels.

All use the same absent-versus-invalid record distinction as PR #353. Existing
register-change approval revocation from PR #350 is retained, not reimplemented.
No new endpoint is activated. Valid unknown fields and nested encoded/object
representations remain intact across serialization and reopen.

## Verification

- PASS: full local Python suite, 271 tests; original asset integrity, 166 aliases;
  git diff --check.
- Local Gradle unit and APK commands attempted: NOT RUN (gradle missing, exit 127).
- Added JVM tests: three controls × invalid types, valid record roundtrip/consent,
  new controls; nested list invalid input and all four encoding combinations.
- Independent GitHub Actions: pending when this checkpoint was written.
- Device, visual, transport acceptance: NOT RUN. Component statuses remain partial.

## Next handoff

Check exact-head independent CI for this branch and PR #353, obtain human review
as required by BLOODSKY_SAFE_AUTONOMOUS_AGENT.md, then integrate via PR only.
Main is untouched. No production publication or autonomous runner enabled.
Remaining priorities: source-backed FBD/import-export gaps and further property
record auditing. Do not repeat already merged #343–352 or these record fixes.
