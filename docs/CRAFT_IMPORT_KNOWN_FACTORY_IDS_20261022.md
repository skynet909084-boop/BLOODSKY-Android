# Correct Craft factory-support classification — 22 October 2026

Pinned KaScada 1.0.7 factory evidence maps:
- ID8 `CustomComponents.i`: integer/Float32 numeric input;
- ID14 `CustomComponents.o`: trend chart with four source-defined series;
- ID15: Craft archive/history component.

BLOODSKY already imported each ID to an editable concrete type and
decoded source settings. However, `isSupportedComponent()` had not been
updated when those types were introduced. It then also marked each
imported widget as `craftUnsupportedComponent=true`, incorrectly
treating working components as placeholders.

This change aligns the known-type predicate with the implemented importer.
Original metadata, inactive network endpoints, opt-in defaults and all
screen/component row indices remain preserved.

**Intentional exclusions:** ID3 is still not identified in the original
factory method and must stay unknown. ID19 is a network write gateway and
has its own explicit `craftUnsupportedComponent` handling; it remains
disabled on legacy import pending deliberate endpoint remapping.

JUnit covers a multi-widget project, native properties, inactive bindings,
unknown vendor fields and export/re-import with the same three supported
factory IDs, plus fail-closed ID3/19. Real-device behavior and visual parity
remain unverified; the stable main branch is unchanged.
