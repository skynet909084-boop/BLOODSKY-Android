# BLOODSKY interface source-fidelity rule — 20 Oct 2026

The decompiled KaSCADA Craft 1.0.7 package is the reference for BLOODSKY's Craft-compatible interface. Use the checked-in `reference/craft-reference.zip` as the immutable source snapshot (SHA-256 is pinned in `tools/prepare_craft_resources.py`). This applies to every Craft-derived screen and interaction: view hierarchy, ordering, exact text, dimensions, colors, icons, menus, dialogs, gestures, navigation and persistence behavior.

## Evidence order

1. Decompiled APK resources: `apktool/res/layout`, `menu`, `drawable`, `values`, `values-ru`, `values-ru-rRU`, dimensions and styles.
2. JADX and Smali for which views are shown, their event handlers, state transitions, limits, defaults and serialization.
3. Original exported user projects and documentation for project compatibility and intended use.
4. Emulator observations and screen recordings to confirm runtime layout and behavior.

A recording or an existing BLOODSKY approximation cannot replace source evidence. When the APK material does not establish behavior, keep the behavior unresolved and record the evidence needed; do not invent a Craft behavior.

## Acceptance for each screen

Record the source layout/style/menu IDs, strings and source handler. Then verify:

- Same screen structure, labels, ordering, colors, dimensions and source artwork at the same resolution and density as Craft.
- Every enabled tap, long press, selection, back action, toolbar action and dialog button follows the source transition.
- Persisted values and defaults match source behavior after save, process recreation and reopen.
- Any unimplemented transport or operation is visibly unavailable without claiming it works.

Use the original resource artwork and resource values where possible. Keep BLOODSKY additions outside Craft-derived screens unless the original interface provides a corresponding location or a documented extension is required.

## Current screen slice

`CRAFT_SERVER_EDITOR_IMPLEMENTATION_20261020.md` maps the network-device table, server list, chooser, row actions, Wi-Fi and USB settings to the pinned XML and decompiled handlers. The source artwork, toolbar title/subtitle treatment, 16-slot count controls, transport chooser order, server-row assets and USB spinner defaults are now reflected in code. This is an implementation checkpoint, not visual acceptance: same-density screenshot comparison, native project record round-trip, and the full UI smoke are still required before accepting this slice.

## Whole-app scope

Apply this rule to the main screen, editor, every menu and property dialog, every component, FBD, operator view, device/transport screens and lifecycle restoration. Track screens and evidence in the unified acceptance matrix. A passing build or a small UI smoke path does not establish complete Craft UI parity.
