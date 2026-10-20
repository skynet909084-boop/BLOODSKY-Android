# BLOODSKY execution status — 2026-10-17

## Craft register gateway ID 19 — 2026-10-18

- Added an explicit settings form for source, destination, count, mode, trigger endpoint and trigger bit. Imported device-index bindings are preserved but do not run; gateway enable is opt-in and requires explicit endpoints (TCP destination, TCP or local-system source).
- Added direct Modbus TCP execution for continuous transfer, rising-edge trigger, and single-register source-change mode. Reads support FC03/FC04; writes use validated FC16 holding-register batches. All network requests run on the existing background Modbus executor and are guarded by project/operator lifecycle tokens.
- `CraftRegisterGateway` validates address/range/count/mode constraints and owns event state. Unit tests cover 1–99 counts, FC04 source normalization, holding-only target, low→high edge behavior, source-change behavior, disabled import and no destination writes after a failed read.
- The gateway can now read a bounded local Craft system-register range with `system://register/N` and copy it to an explicitly configured TCP holding-register destination. System-register destinations are still unsupported; imported Craft bindings remain disabled until remapped. RTU/Bluetooth transports and physical acceptance remain open.

## Craft bit gateway ID 22 — 2026-10-18

- Manual page 60 and Craft 1.0.7 source confirm a bit-gateway widget with 1–16 bits, FC01 Coils or FC02 Discrete Inputs source selection, three transfer modes, and a holding-register bit trigger for rising-edge mode.
- Added opt-in TCP settings/runtime: FC01/02 bit reads, FC05 one-coil write or FC15 packed writes, continuous/rising-edge/source-change modes, and a 1-bit limit for source-change. Imported endpoint indices stay disabled until manually remapped.
- Added loopback protocol tests for FC05/15 packing and echo, importer preservation tests, and pure gateway mode/validation tests. ID 22 remains TCP-only; system-register endpoints, RTU/Bluetooth and paired-device acceptance remain open for that gateway.

## Full manual/decompilation audit refresh — 2026-10-18

- `KASCADA_FULL_PARITY_AUDIT_20261018.md` now cross-checks all manual chapters 2–12, the decoded component registry, the pinned decompilation, and the three supplied `.pro`/ZIP samples. It separates implemented paths from tested compatibility and device/visual proof.
- `BLOODSKY_PARITY_MATRIX.json` was corrected: most broad feature rows are partial; bit list and voice-announcement widgets are missing. Earlier “implemented” labels had overstated KaSCADA parity.
- Incoming SMS §8.4 now has an opt-in foreground receiver, runtime permission, encrypted sender allowlist, exact command parser, up-to-16 signed 16-bit holding-register writes, atomic FC22 bit write, optional FC22 strobe set and Craft ID 18 import disabled-by-default. Outgoing SMS §8.5 reads 1–8 contiguous registers and supports `%1`–`%8`, while preserving `{value}`. Android multipart/PDU/device tests and exact vendor error UX remain open.
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
- Source-confirmed system registers/device configuration. ID7 dynamic text reads a global/system bit, and imported ID8 numeric input reads UINT16/INT16 plus source-ordered big-endian Float32 from the system bank. Opt-in writes are limited to user addresses 10–1999 (Float32 starts 10–1998); service writes 0–9, gateway destinations and the other typed UI bindings remain open. Do not accept PR45's unsupported register/timing claims as implementation specifications; see `docs/KASKADA_SYSTEM_REGISTERS_AUDIT_20261019.md`.
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
- Standalone Craft `.pro` JSON is detected and converted by the shared import service; existing BLOODSKY JSON and ZIP paths remain supported. Sanitized CI fixtures now preserve the three supplied projects’ component records while removing project/device credentials and labels. A new Android unit test imports all three, validates their screens/components (including fire-project screen 20 and 11 fail-closed ID 19 gateways), then atomically saves and reopens each project. The empty `пож.zip` still cannot validate media round-trip; background asset decoding remains covered separately.


### Craft ID 20 bit list — implementation slice

- Decompiled `CustomList.java` and `mbCanvas` confirm a 16-bit selection mask, labels `textBit0`–`textBit15`, multi-select popup, `visibleInWork`, and `checkNoChange` (“bits cannot be changed in work mode”).
- BLOODSKY imports the 16 labels and flags, preserves opaque legacy connection metadata, and requires an explicit operator TCP holding-register binding. FC03 reads the UINT16 mask; FC06 writes the updated mask. Blank labels cannot be toggled.
- New Craft list widgets can be added from the original component palette; property editor saves labels/flags and supports read-only mode and hidden-in-work mode. Tests cover mask boundary, editor normalization, serialized/object label forms, and importer fail-closed remapping.
- Validation: 141 Python checks passed locally; Android CI run #778 passed its unit tests and debug APK build on commit `e75b0e3e795340e84eab86574c02fef04fb299db`. The UI smoke job was skipped by workflow rules. Remaining: compare operator popup/rendering side-by-side with Craft and verify against a Modbus device.


### Craft ID 13 linear setter — implementation slice

- Decompiled `CustomSeekBar` (`v.java`) and its `mbCanvas` save/restore and touch callbacks: the slider stores `max`, `isTurn` orientation and text/progress/background colors, and commits its integer position on release.
- BLOODSKY now imports Craft component ID 13, preserves those visual settings and unknown source metadata, creates a property-editable horizontal/vertical slider, and allows a 1–65535 maximum. Imported device bindings remain inactive; an explicitly entered TCP holding-register endpoint is required before FC06 writes.
- Added range/model tests and importer coverage. Validation: 141 Python tests passed locally; Android unit tests and debug APK passed in CI run #788 on code/test commit `70355b49eb723438f9dcaace2cdfcd9161d084f5`. UI smoke was skipped by workflow rules. Side-by-side rendering and Modbus-device acceptance remain open.


### Craft ID 6 button mode 2 — constant write slice

- Decompiled `CustomComponents.e` and the `mbCanvas` touch callback: button mode 2 writes a configured constant register, with `typeRegister` selecting UINT16 or Float32; `visibleInWork` controls operator-mode visibility. The existing device association is installation-specific.
- Import now preserves the mode, constant, type, source register and visibility while leaving the old binding disabled. The button editor can switch between bit-toggle and constant-write modes; constant mode validates the value and requires an explicit TCP holding-register. Operator presses write UINT16 with FC06 or Float32 with FC16.
- Added integer/Float32 validation and importer regressions. Validation: 141 Python tests passed locally; Android unit tests and debug APK passed in CI run #795 on code commit `02fbeeeb013a0952b17a3205180f377bc6c419c6`. UI smoke was skipped by workflow rules. Mode 0 bit-toggle parity, screen-navigation editor parity, and hardware/UI acceptance remain open.



## Craft ID 6 button mode 0 — momentary bit write

- Decompiled `CustomComponents.e` and the `mbCanvas` touch listener: mode 0 sets the connection bit on touch-down and clears it on touch-up or touch-cancel.
- Import preserves Craft's `addressBit` as a 0–15 hint and keeps the source device binding disabled. The editor requires an explicit TCP holding-register mapping and an opt-in checkbox before the operator can write.
- Operator mode uses atomic Modbus FC22 masks for the selected bit, clears the output on release/cancel and when the activity pauses, and retains the existing toggle behavior for native BLOODSKY buttons.
- Validation: mode-mask boundaries and importer fail-closed behavior covered by Android tests. Unit tests and debug APK passed in [Actions run #807](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39138717863). UI smoke #469 was skipped by workflow rules. Modbus-device, visual comparison, and momentary release under connection loss remain open.


### Craft ID 6 mode 1 — screen navigation

- Import maps Craft `changeScreen` from its 1-based screen number to BLOODSKY's 0-based screen index and preserves `visibleInWork`; invalid references stay flagged instead of silently targeting another screen.
- The editor uses a picker populated from existing screen names and indices, validates that an internal screen or external URL is selected, and preserves the Craft visibility option in operator mode.
- Validation: importer tests cover target screen and hidden-in-work preservation; Android unit tests and debug APK passed in [Actions run #813](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39139138324). UI smoke #475 was skipped by workflow rules. Side-by-side visual acceptance remains open.

### Craft ID 9 — selectable alarm sounds

- Decompiled `CustomComponents.c`: `posSound` selects one of five bundled Craft alarms; an active trigger starts looping playback and a cleared trigger stops it.
- The importer already preserves Craft's sound index and keeps the old device mapping inactive. The property editor now exposes all five presets and a custom audio-file option; choosing the custom option lets the saved path or content URI be used instead of the bundled sound.
- Android unit tests and debug APK passed in [Actions run #815](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39139483169). UI smoke #477 was skipped by workflow rules.
- Open: trigger-device acceptance and side-by-side audio/UI review. Playback is now keyed per screen/widget so one alarm's start, stop, or completion does not replace another; `tools/test_alarm_playback_contract.py` guards this wiring. Simultaneous playback still needs device acceptance.

## Resume checkpoint — 2026-10-18

- Active work is PR #143 on branch `feat/craft-screen-navigation-import-20261018`; it remains open and unmerged. Stable `main` was not changed.
- Completed slices include Craft ID 6 modes 0/1/2, ID 9 five bundled alarm presets/custom audio selection, ID 13 linear setter, and ID 20 bit list. Imported Craft device endpoints remain inactive until explicit TCP remapping.
- Latest code validation: [Actions run #815](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39139483169) passed Android unit tests and debug APK build/upload. UI smoke was skipped by workflow rules.
- Independent per-widget alarm players are implemented and guarded by source-contract tests. Resume with paired audio/UI/device acceptance, then continue the remaining source-backed widget and transport audit.
- Product constraints: retain optional SMS; omit licensing and remote archive delivery; imported Craft bindings stay inactive until explicitly remapped.


## RTSP URL validation slice — 2026-10-18

- Added `CraftRtspConfig` for bounded RTSP/RTSPS endpoint parsing. It rejects malformed URLs, missing hosts, invalid ports, whitespace/control characters, fragments, and embedded URI user-info so a password is not silently placed in ordinary project JSON.
- JUnit tests cover accepted RTSP/RTSPS/IPv6 endpoints and invalid schemes, hosts, credentials, ports, whitespace, and length.
- Exact code/test head `29897268747cf246558e155a7c1a6b7d310664c1`: Android unit tests, archive inspector, and debug APK passed in Actions run [#827](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39140908453).
- Added fail-closed Craft ID 23 import mapping: an explicit disabled placeholder preserves the original opaque `craftOther` and connection fields; no imported stream starts. JUnit regressions verify metadata round-trip, blank-label behavior and disabled runtime flag.
- Scope remains a URL validator plus import placeholder only. No editor/player/lifecycle integration or decoder/device acceptance is claimed. Imported credentials remain in preserved Craft source data; define secure-storage/export handling before any runtime activation.
