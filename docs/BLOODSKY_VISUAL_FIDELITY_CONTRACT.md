# BLOODSKY visual-fidelity contract — Kaskada Craft 1.0.7

## Product decision

The original Kaskada Craft 1.0.7 interface is the visual reference. BLOODSKY must not introduce a new visual language merely because it is easier to implement. A user moving from the original should find the same screen composition, navigation, terminology, iconography, density, and interaction patterns.

This is a strict visual-compatibility target, not a request for a loosely inspired theme. New capabilities should be added behind existing familiar workflows wherever practical. If a genuinely new feature has no original counterpart, it should be visually quiet and consistent with the original rather than restyling the entire application.

## Rules

1. **Reference before redesign.** Inspect the original decoded layouts, drawable resources, styles, menu definitions, component registry and Smali where JADX is ambiguous. Capture/reference actual screens before changing dimensions or colors based on guesses.
2. **Preserve screen geometry.** Match orientation, toolbar/menu placement, panel widths, margins, row heights, control sizes, canvas coordinate behavior, and dialog sizing. Account for the original device density and screen orientation.
3. **Preserve visual assets.** Reuse authorized original project assets when available. Otherwise reproduce them deliberately from source evidence; do not replace original icons with generic emoji or arbitrary modern icons.
4. **Preserve language and sequence.** Use the original Russian labels, capitalization, ordering, menu grouping, shortcuts/gestures where verified, and expected navigation.
5. **Match states, not just screenshots.** Verify normal, selected, pressed, disabled, focused, error, modal, work/runtime, and empty states. Match context-menu behavior, selection markers, resize handles and canvas grid where present.
6. **Do not use theme-wide guesses.** A charcoal-and-amber palette or any other style is not proof of fidelity. Colors must be sampled from the original resources/screens or verified implementation.
7. **Keep enhancements non-disruptive.** Preserve original project format fields, component tags, settings and workflow. Put advanced/experimental options in existing relevant dialogs or a clearly secondary area.
8. **Avoid claiming exact parity prematurely.** A screen is complete only after side-by-side visual review and interaction checks against the same original state.

## Screenshot comparison protocol

For each original screen, record a reference image and compare BLOODSKY at the same orientation, viewport and density. Compare:
- outer frame, background and panel boundaries;
- toolbar height, button positions and icon shapes;
- typography, labels, line breaks and alignment;
- padding, spacing, border thickness, corner radius and shadows;
- component rendering on the canvas, selection/resize visuals and grid;
- context menus, property dialogs and confirmation dialogs;
- screen navigation and runtime/work-mode appearance.

Use a side-by-side comparison and, where possible, a 50% alpha overlay or pixel-difference image. Fix the largest structural differences first, then typography/assets, then minor spacing. Keep a regression screenshot set so later feature work cannot silently drift the interface.

## Release gate

- [ ] Original home/project menu matched.
- [ ] Original editor toolbar and canvas matched.
- [ ] Component picker and every supported component's appearance matched.
- [ ] Context menu and component-properties dialogs matched.
- [ ] Project/screen navigation and work/runtime states matched.
- [ ] Same operations tested in both apps: create/open project, add component, open properties, move/resize, copy/paste, change screen, save and reopen.
- [ ] All differences documented; only intentional additions are allowed.
- [ ] APK build and install smoke test pass.

## Known evidence limitation

The repository's decompilation inventory confirms `mbCanvas`, component configuration fields and original import/export structures, but decompiled Java alone is not sufficient to establish every exact visual dimension. JADX reported decompilation errors for some classes. Where layout or behavior is uncertain, verify resources and Smali rather than inventing a replacement. Do not call the interface pixel-identical until original screenshots/resources have been compared directly.

## Asset provenance gate (KaScada Craft 1.0.7 / 107)

The reference archive is pinned by SHA-256 in `tools/prepare_craft_resources.py`.
During the Android APK workflow, that archive is unpacked; complete original
`apktool/res` and `apktool/assets` trees and their per-file SHA-256 manifest
are retained under `build/craft-reference/bundled` for auditing. The complete
decoded trees are **not** packaged wholesale into the BLOODSKY APK.

Only supported original drawable and audio files are copied into Android
`drawable-nodpi/kc_*` and `raw/kc_*` aliases, preserving their contents.
The mandatory `tools/verify_craft_resource_aliases.py` build gate compares
each compiled alias to the pinned decoded original **byte for byte**.
It also validates the source-backed 40dp FIT_XY cut/copy/paste/align layout and
essential original wallpaper, toolbar and action icon names. It rejects absent
or modified assets and publishes
`build-reports/kascada-original-alias-integrity.md` as part of the CI diagnostics.

Evidence categories must remain distinct:

- **Byte-identical original:** `kc_*` image/audio aliases that passed the
  hash comparison with pinned decoded KaScada files.
- **Source-derived layout:** layouts such as `kc_selection_actions.xml` that
  reproduce verified original action mappings and dimensions but have Android
  resource IDs renamed for BLOODSKY. They are not claimed byte-identical.
- **Implemented approximation / unverified:** Java-built dialogs, canvas
  composition, native component rendering and interaction states until
  screenshot/gesture comparison with original KaScada on equivalent devices.

A successful resource-integrity gate establishes **asset origin**, not
pixel-level visual parity, functional completeness, device compatibility or
rights to distribute third-party artwork. Final release acceptance still
requires original-reference screen comparisons and applicable permissions.
