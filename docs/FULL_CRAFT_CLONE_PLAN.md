> Historical reference note recovered from PR27. Describes that branch's observations, not verified current main behavior. See LEGACY_PR_CONSOLIDATION.md and reference/README.md for current recovery status.

# Full KaScada Craft 1.0.7 clone — source-driven implementation plan

**Milestone:** a faithful Android clone of the user-supplied KaScada Craft 1.0.7 APK. No BLOODSKY-specific redesign until parity is verified.

## Phase A — Original resource inventory and preservation (implemented in CI; APK verification pending)

- Source of truth: Actions artifact `11912849123`, `apktool/res` and `apktool/assets`.
- `tools/bundle_craft_resources.py` preserves both trees under `build/craft-reference/bundled/`, outside the shipped APK. It writes SHA-256 hashes and byte sizes for every file to `MANIFEST.json`.
- Build additionally compiles 151 original non-nine-patch `drawable/*.png` files as `kc_*.png` resources for existing widgets.
- The asset library includes drawable variants, layouts, menus, values XML, animations, raw files, fonts, sounds and web files that exist in the decoded source. **Assets are not automatically active UI resources.** XML styles and layouts need deliberate Android integration; images need actual widget bindings.
- Decompiled `jadx` Java and `smali` are **reference source**, not silently added to the Android runtime. Copying all source verbatim would create conflicts and fail to compile.

## Phase B — Complete resource-to-screen map (pending)

- Index each original `res/layout`, `res/menu`, `res/drawable*`, `res/anim*`, `res/raw` and `assets` entry.
- Trace references in original `mbCanvas`, editor activities, dialogs, adapters and FBD classes.
- Record where every resource appears, state variants, dimensions, scaling and click behavior.
- Wire actual imported assets into corresponding BLOODSKY views.

## Phase C — Screen and workflow parity (pending)

- Recreate original main screen, toolbar, drop-down menus, element library, project editor, property dialogs, FBD editor and operator mode.
- Match text, icons, positions, themes, dimensions, backgrounds, dialogs and interaction semantics.
- Test screenshots at identical screen dimensions/density; document differences.

## Phase D — Functional parity (pending)

- Reproduce project file loading/saving and compatibility, canvas geometry, object types and dynamic properties.
- Port FBD classes individually (see `CRAFT_FBD_SOURCE_MATRIX.md`) and test execution against source.
- Reproduce system registers, Modbus transports and scanner, events, alarms, animations and operator controls.
- Verify behavior without real hardware using controlled Modbus test fixtures, then on real hardware.

## Phase E — Parity acceptance (pending)

- Automated tests for each original element and menu.
- Build an installable APK and test on Android.
- Complete original-vs-BLOODSKY screenshots and regression checklists.
- Only after this gate begin BLOODSKY-specific enhancements.

## Operational risks

- The source GitHub Actions artifact can expire. Keep a user-owned long-term copy of the original APK/decompiled archive; do not claim the CI import is permanent.
- Importing resource files into `assets` preserves them, but does **not** constitute porting all app code or ensuring identical behavior.
- Any third-party dependencies, native libraries, permissions and licensing must be audited separately before bundling them.
- **Current status:** resource preservation implemented in workflow; the complete clone is not yet finished.
