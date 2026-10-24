# Craft ID2/ID4/ID5 source-record integrity — 22 October 2026

The pinned KaScada 1.0.7 factory creates ID2 CustomLabel, ID4
CustomComponents.u numeric indicator, and ID5 CustomComponents.q lamp.
Their native widget editors carry object-valued source records. Source
may be represented by BLOODSKY as a JSONObject or JSON-encoded string.

Prior behavior silently replaced explicit JSON null, arrays and scalars
in the native records with an empty JSONObject. A seemingly harmless
edit could therefore erase opaque vendor metadata and make a damaged
project appear successfully round-tripped.

The source-backed compact editors now use the shared
`CraftPropertyRecord.copy` fail-closed parser (introduced in #353/#354):
- label ID2: `craftOther`;
- numeric indicator ID4: `craftConnectionInfo` and `craftOther`;
- two-state lamp ID5: `craftConnectionInfo`.

Missing records still create valid empty source objects for newly authored
components. Valid JSON object/string records retain their original shape,
unknown keys and inactive `connectionInfo`. Invalid records are rejected
before applying any settings. Tests cover malformed cases and repeated
edits without mutating input.

This closes a metadata-preservation defect, not pixel, network or actual
Android device acceptance.
