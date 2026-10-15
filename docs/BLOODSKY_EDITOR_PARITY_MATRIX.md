# BLOODSKY editor parity matrix

This matrix separates source-confirmed Kaskada Craft behavior from BLOODSKY implementation status. A feature is not considered complete because a toolbar item exists; it must work, persist, and survive reopening.

## Editor workflow

| Capability | Kaskada reference | BLOODSKY checkpoint / acceptance test |
|---|---|---|
| Add component | Component factory in `mbCanvas` and custom component registry | Picker offers only implemented types; adding one creates a stable internal ID and correct default geometry |
| Select and move | Canvas selection and drag behavior | Selected item is visibly distinguished; drag changes coordinates and persists |
| Resize | `StartResize(View)`, `Resizing(View)` | Handles respect minimum size; grid snapping does not cause jumps; geometry survives reopen |
| Context properties | Component-specific settings and `connectionInfo` / `other` fields | Dialog is type-aware; changing a property updates the canvas and is saved |
| Copy / cut / paste | `selectActionCopy`, `selectActionCut`, `selectActionPaste` | All widget properties and unknown metadata survive; pasted item gets a new identity and offset position |
| Align | `alignSelectView`, `openDialogAlignComponents` | Alignment is deterministic and persists for all selected widgets |
| Multiple screens | Screen navigation and per-screen component tables | Names, background colors, and widgets remain attached to the right screen through navigation and restart |
| Project persistence | SQLite tables plus JSON/ZIP import-export | Save/reopen and export/import round trips preserve project name, geometry, colors, bindings, extra fields and assets |
| Runtime / work mode | `onClickWorkToolbar`, runtime lifecycle | Runtime interactions do not accidentally move widgets; leaving runtime stops polling and releases resources |

## Data and compatibility rules

- Keep original component type tags separate from the database row ID and BLOODSKY's internal stable identity.
- Preserve unknown JSON keys, `connectionInfo`, `other`, and media references when loading and saving projects.
- Validate imported archives before changing the active project. Reject absolute paths, `..` traversal, malformed JSON and unreasonable archive sizes.
- Treat register bindings as explicit protocol/function/unit/address configuration. Do not silently reinterpret an invalid binding.
- Keep network polling off the UI thread, limit concurrent requests, show readable error states, and stop polling when the editor closes.

## Modbus acceptance checklist

- [ ] Holding-register FC03 and input-register FC04 use correct zero-based protocol addresses.
- [ ] Unit ID, port, register address and quantity are validated before network I/O.
- [ ] Invalid bindings show a configuration error rather than a misleading zero.
- [ ] Timeout, connection failure, and Modbus exception responses are distinguishable.
- [ ] Widgets sharing the same register can reuse a poll result within one scan.
- [ ] A polling task cannot update a destroyed screen or activity.
- [ ] Automated tests use mocks/simulators; no hardware access is required for CI.

## Verified automated coverage (current checkpoint)

- [x] Import validator accepts a multi-screen project and the legacy single-screen widget-array format.
- [x] Import validator rejects malformed JSON, trailing content, empty/missing screen lists, invalid current-screen indices, non-integer indices/geometry, malformed widget/screen structures, malformed `connections` fields, invalid/self-referential/duplicate FBD edges and cyclic FBD graphs.
- [x] Backward compatibility is tested for older projects that omit the optional `connections` array.
- [x] The metadata-copy helper is exercised with nested unknown fields and arrays; a caller-written current value is verified to override an older value after the metadata copy.
- [x] The CI workflow completed successfully for commit `f8ebe8b81cc83674001d30a48a32524eee360294` (unit tests and debug APK build passed; run [38681284597](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38681284597)).
- [ ] End-to-end load/edit/save/reopen round-trip tests for project-, screen- and widget-level unknown metadata are still outstanding.
- [ ] Android device/emulator smoke tests and pixel-by-pixel comparison with Kaskada Craft are still outstanding.
- [ ] Modbus RTU and full original protocol/component parity remain outstanding.

The metadata helper test validates the copy operation, not the entire Android persistence lifecycle. Keep the release gate below in force until the outstanding checks are implemented and exercised.

## Release gate

Do not label a build release-ready until the GitHub Actions APK artifact exists, the APK installs, the editor launches, and smoke tests cover project save/reopen, screen navigation, component property editing and a simulated Modbus read. The current workflow produces a debug APK for development; it is not a signed public release.
