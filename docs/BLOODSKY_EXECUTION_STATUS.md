# BLOODSKY execution status — 2026-10-16

Target: original KaScada Craft 1.0.7 functionality and appearance with BLOODSKY branding and without licensing/component-count activation. A passing build does not prove a full clone.

## Accepted software milestones

- PR47: portable ZIP image/audio import/export, bounded archives and isolated media generations; existing TCP/FBD fixes retained.
- PR48: screen snapshots preserve media, voice, sound, navigation and unknown metadata independently.
- PR49: fractional geometry survives save/load/copy; malformed/non-finite geometry is rejected.
- PR50: original home background/button resources, original six action order/weights, named project catalog with legacy-active migration and confirmation before deletion. Main CI run 38944633684 passed.
- PR51: original indicator bitmaps/default geometry/state metadata and portable custom ON/OFF images. PR CI run 38945048830 passed. Source ledgers record the exact files/hashes; visual status remains wired-unverified.
- Current runtime change: reject stale polling sessions/bindings, pause foreground polling/audio, resume an open editor. Current-head CI is the acceptance gate; device behavior is not yet verified.

## Remaining implementation work

- Original editor toolbar, component/FBD pickers, all property and context dialogs, original fonts/drawables and every supported state; corresponding equal-density screenshots. Home/indicator resource wiring is only part of this phase.
- Original SQLite `Rovki.db`/table and project-archive compatibility, including real-project fixture round trips. The named catalog/JSON ZIP format is BLOODSKY's own layer, not original database compatibility.
- Complete original component registry/property semantics, macros, pin-specific FBD layout and source-confirmed execution order/timing. Existing generic FBD/math tests do not prove original semantic parity.
- Source-confirmed system registers/device configuration. Do not accept PR45's unsupported register/timing claims as implementation specifications.
- Real Android USB/Bluetooth transport integration and lifecycle/permissions/reconnect checks; current RTU stream helpers and CRC tests are not hardware support.
- Complete work/operator mode and intentional foreground-service/background behavior. Network devices/server UI, remote control and other original integrations must be backed by implementations and tests, not only menu items.
- Review remaining PR38/44 changes in focused main-based increments. ZIP corrections were ported separately; these old branches must not replace current Activity/TCP/FBD code wholesale.

## Device and release gates

- Install the produced debug APK; verify create/load/delete, media decoding/playback, screen navigation, save/reopen and simulated/real protocol workflows.
- Compare original and BLOODSKY at matching viewport/density/orientation, including pressed/disabled/error/runtime states.
- Prepare a signed public release only after signing-key/process and installation checks. No public release is claimed or issued by these debug builds.

## Known persistence limitation

The named catalog uses SharedPreferences snapshots; synchronous selections/imports restore prior in-memory preference values on a reported commit failure. Power-loss/disk-failure behavior still needs device testing. Old media generations are deliberately retained; garbage collection must prove no saved project references a generation before removal.
