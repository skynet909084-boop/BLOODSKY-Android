# Project import safety: iterative inactive endpoint pass — 22 Oct 2026

A Craft project may contain nested macro definitions, FBD records and vendor
metadata. The import safety pass previously used recursive Java calls for
every nested JSON array/object, so deeply nested but parseable project data
could exhaust the app call stack while clearing external endpoints.

The importer now uses a bounded stack of JSON containers. It still:
- deactivates the same send-capable feature flags;
- copies the original TCP/USB/Bluetooth connection into inactive metadata
  before clearing the active connection;
- disarms TCP archive trigger bindings;
- preserves arbitrary unknown JSON metadata, nesting and scalar values;
- rejects an extreme number of containers instead of running unboundedly.

JVM regressions cover 96 nested array/object levels and 1200 sibling
widgets, plus idempotent re-import, matching the existing security policy.
This does not activate legacy Craft network connections, change project
format, or claim identical device behavior. Independent Android/JUnit CI
and full release-device tests remain required.
