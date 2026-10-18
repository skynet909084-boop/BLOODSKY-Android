# BLOODSKY execution status — 2026-10-17

## Full manual/decompilation audit refresh — 2026-10-18

- `KASCADA_FULL_PARITY_AUDIT_20261018.md` now cross-checks all manual chapters 2–12, the decoded component registry, the pinned decompilation, and the three supplied `.pro`/ZIP samples. It separates implemented paths from tested compatibility and device/visual proof.
- `BLOODSKY_PARITY_MATRIX.json` was corrected: most broad feature rows are partial; bit list and voice-announcement widgets are missing. Earlier “implemented” labels had overstated KaSCADA parity.
- The immediate open SMS gap is material: manual §8.4 describes inbound SMS as a sender- and command-matched Modbus register/bit control; BLOODSKY only has an outbound one-value event widget. Manual §8.5 allows up to eight outgoing register values; BLOODSKY currently inserts one value.
- Stable `main` remains untouched. No new visual or physical-device parity is claimed by this audit refresh.

Target: original KaScada Craft 1.0.7 functionality and appearance with BLOODSKY branding and without licensing/component-count activation. A passing build does not prove a full clone.

## Craft project import update — 2026-10-18

- PR #142 imports Craft `.pro` JSON from project ZIPs into BLOODSKY screens and preserves the full source payload. The newer `gitH.zip` fixture confirmed 65 rows across three used screens, dynamic-text states/colors, five FBD blocks, and two unambiguous typed graph edges. It also exposed Craft's legacy absolute image paths; BLOODSKY resolves an attached image only when its basename matches one ZIP member.
- PR #142 maps Craft component IDs 1, 2, 4, 5, 6, 7 and 11. Recognized FBD blocks retain their original metadata; cycles, duplicate pin drivers, ambiguous keys, unknown IDs and inactive legacy Modbus bindings fail closed or remain preserved as source data.
- Exact head `01ae9d2299cc686a9fb37e4d9a8f59249d78f4e2`: Android unit tests + debug APK passed in [run #687](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39117140559); archive inspector passed in [run #22](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39117140629). UI smoke #349 was skipped by workflow rules; no emulator result is claimed. PR #142 remains open and unmerged.
- Follow-up PR #143 maps verified Craft button mode 1 to screen navigation. Other Craft button modes retain their original `craftOther` fields and inactive legacy device binding.
- The input `.pro` ZIP is Craft's JSON project export, not a raw `Rovki.db`. BLOODSKY's current named-project catalog is stored as JSON snapshots in SharedPreferences; it is not yet a SQLite catalog. Direct raw-database import and migration to SQLite are separate open tasks. No `.db` fixture was supplied, so raw database round-trip is not verified by these ZIP tests.

## Accepted software milestones

- PR47: portable ZIP image/audio import/export, bounded archives and isolated media generations; existing TCP/FBD fixes retained.
- PR48: screen snapshots preserve media, voice, sound, navigation and unknown metadata independently.
- PR49: fractional geometry survives save/load/copy; malformed/non-finite geometry is rejected.
- PR50: original home background/button resources, original six action order/weights, named project catalog with legacy-active migration and confirmation before deletion. Main CI run 38944633684 passed.
- PR51: original indicator bitmaps/default geometry/state metadata and portable custom ON/OFF images. PR CI run 38945048830 passed. Source ledgers record the exact files/hashes; visual status remains wired-unverified.
- PR52: reject stale polling sessions/bindings, pause foreground polling/audio and resume an open editor; original home/fullscreen/orientation wiring completed. Hardware and real-device lifecycle checks remain open.
- PR53: APK installation and project create/restart/reopen/delete checked on an Android 11 emulator, with screenshots and crash diagnostics.
- PR54–58: editor acceptance checklist, drawable/thumbnail audits and original indicator/button previews; original resource candidates are not treated as verified component semantics.
- PR59: original source/layout calculator transferred; arithmetic, clear/delete and reopening verified in the emulator.
- PR60: original five-category palette transferred with source hierarchy/artwork, existing component bindings and extended/FBD access; unsupported original types are explicitly disabled.
- PR61: property edits retain fractional geometry and reject invalid dimensions without partial mutation; emulator verifies save/restart/reopen.

## Accepted Work batch — 2026-10-17

- PR62: original ON/OFF image menus and five built-in lamps, custom image selection through the actual Android document provider, persistent URI permission, bounded decoding/cache and per-state unknown-field preservation. Accepted at `850e8332b12826a8e781a775254e3951f26b7328`; APK/unit run [361](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38993214536) and Android 11 UI run [33](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38993214537) passed.
- PR63: original cut/copy/paste/alignment icon strip with source layout/artwork; cut guards serialization before deleting and clipboard checks cover custom images/fractional dimensions. Accepted at `7cb8eea911fddb57c6090123657d02a8b8a552a4`; APK/unit run [362](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38993220012) and Android 11 UI run [34](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38993219703) passed. Both PRs are merged to main.

- PR65: source 40dp upper toolbar with clock/hide/reveal/palette, original run/stop controls and separate operator gestures. Accepted at `26b79714ff73525968d2c30fdb918ac96324e4b8`; [APK/unit run 366](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38994541962) and [UI run 37](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38994542013) passed. Merged to main. Editor taps edit properties; runtime taps operate controls. Runtime drag/long-press, state persistence after restart and stop/Back restoration are verified.

- PR67: original 40-preview FBD catalogue, nine existing-engine actions, explicit disabled source entries and strict atomic timer/counter/comparison property validation. Accepted at `bd9ec2725fefa598bf0038e98b48491619388721`; [APK/unit run 374](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39001572333) and [UI run 43](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39001572328) passed. 83 Python checks, 133 Android tests; AND/TON add, timer0 rejection, exact2345ms retention after process death plus the complete prior smoke verified. Merged to main. See [provenance, adaptations and limits](ORIGINAL_FBD_PALETTE.md).

## Remaining implementation work

- Complete original toolbar overlay/settings/menu behavior, FBD picker semantics, unsupported palette components, all property/context dialogs, fonts/drawables and every supported state; corresponding equal-density screenshots. Original home, calculator, supported palette and indicator wiring cover only part of this phase.
- Original SQLite `Rovki.db`/table and project-archive compatibility, including real-project fixture round trips. The named catalog/JSON ZIP format is BLOODSKY's own layer, not original database compatibility.
- Complete original component registry/property semantics, macros, pin-specific FBD layout and source-confirmed execution order/timing. Existing generic FBD/math tests do not prove original semantic parity.
- Source-confirmed system registers/device configuration. Do not accept PR45's unsupported register/timing claims as implementation specifications.
- Real Android USB/Bluetooth transport integration and lifecycle/permissions/reconnect checks; current RTU stream helpers and CRC tests are not hardware support.
- Original operator password/screen-lock/PDF behavior and intentional foreground-service/background behavior remain open after the basic guarded mode in PR65. Network devices/server UI, remote control and other original integrations must be backed by implementations and tests, not only menu items.
- Review remaining PR38/44 changes in focused main-based increments. ZIP corrections were ported separately; these old branches must not replace current Activity/TCP/FBD code wholesale.

## Device and release gates

- Install the produced debug APK; verify create/load/delete, media decoding/playback, screen navigation, save/reopen and simulated/real protocol workflows.
- Compare original and BLOODSKY at matching viewport/density/orientation, including pressed/disabled/error/runtime states.
- Prepare a signed public release only after signing-key/process and installation checks. No public release is claimed or issued by these debug builds.

## Known persistence limitation

The named catalog uses SharedPreferences snapshots; synchronous selections/imports restore prior in-memory preference values on a reported commit failure. Power-loss/disk-failure behavior still needs device testing. Old media generations are deliberately retained; garbage collection must prove no saved project references a generation before removal.

### Next persistence slice

Design an additive SQLite-backed catalog migration that imports existing SharedPreferences snapshots atomically, retains JSON project payloads and stable IDs, and leaves per-project media generations intact. Test the migration and restart behavior before changing the storage source of truth. Direct `Rovki.db` ingestion remains separate and requires a real database fixture to verify version-11 table contents and media ownership.

## Validation and next slice

- Latest batch: 78 Python checks; 129 Android unit tests, zero failures/errors/skips; debug APK built; complete API30 emulator flow passed at density560/1440x2560. Full Gradle/SDK/emulator runs were unavailable locally and executed in independent CI.
- Reviewed screenshots show custom yellow ON and builtin blue OFF images decoding after process restart, plus all four original selection icons with the canvas visible. Clipboard assertions compare every non-position widget JSON field, and empty-selection cut changes nothing.
- The shell-only FINGER event helper fixes the observed API30 DocumentsUI automation issue. It is not part of the installed app; no file-result or persisted-URI assertions were bypassed.
- Accepted PR65: source-backed upper toolbar and guarded operator-mode behavior. Next: original FBD palette/port semantics and remaining per-component properties, plus source settings/password/lock/PDF behavior. Each needs its own implementation and runtime evidence.
- Full original APK parity, original project database compatibility, physical-device USB/Bluetooth, and signed public release remain open. Passing this batch is not completion of the entire clone.

- PR65 screenshots reviewed: compact play/stop/navigation/clock/calculator row, runtime without grid/palette/selection strip, operational button blue-to-green, and restored editor tools. All pre-existing media/calculator/geometry/clipboard/catalog UI flows still pass. README documents the new toolbar and Back behavior.


### Uploaded project fixture review — 2026-10-18

- Reviewed all three supplied `.pro` projects and ZIP exports. `home.zip` contains `home/home.pro` plus its background image (24 components, screen 1). `gitH.zip` contains `gitH/gitH.pro` plus the screen-1 background image (65 components across screens 1–3; five FBD nodes). Neither example uses user-selected ON/OFF indicator images.
- `пож.pro` is a 124-component Craft JSON project. It declares 16 screens but has 103 components on screen 20 and 21 on screen 1. PR #143 now sizes the imported screen array to the highest declared or referenced Craft screen (capped at 128) while preserving the original `countScreen` in `craftSource`. Regressions cover screen 20 with stale count 16 and rejection of screen 129. Exact code head `ac059b6de181076f9f764fc970ddd4187fc07f19` passed 139 Python tests, Android JUnit, archive-inspector tests and debug APK in [Actions run #697](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39118946847); UI smoke was skipped by workflow rules.
- The paired 112-byte `пож.zip` contains only the empty `пож/` directory—no `.pro` JSON or media. `пож.pro` must be imported as standalone JSON or repackaged with its project assets before ZIP import can be verified.
- The fire project also contains 5 Craft component-ID 9 rows and 11 ID 19 rows. Neither has a verified BLOODSKY component mapping; they must remain visibly flagged/preserved until their source behavior is identified. Existing imports preserve unknown records, but should not imply those controls execute.
- Standalone Craft `.pro` JSON is now detected and converted by the shared import service; existing BLOODSKY JSON and ZIP paths remain supported. An integration test verifies conversion, screen-count reconciliation and project save. Next import batch: add sanitized fixtures from the supplied projects, verify unsupported-ID reporting and attached-background handling. The empty `пож.zip` still cannot validate media round-trip.
