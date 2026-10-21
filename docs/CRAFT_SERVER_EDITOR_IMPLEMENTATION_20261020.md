# Craft server editor screen slice — 20 Oct 2026

## Source-backed flow

The pinned `reference/craft-reference.zip` identifies the original UI in:

- `apktool/res/layout/server_list.xml` — full-screen server list, `Редактор серверов` subtitle, add button and no-connection action.
- `apktool/res/layout/alert_dialog_add_wifi.xml` — IP address, port, RTU and pause fields.
- `apktool/res/layout/layout_device_network.xml` — the network-device table has name, unit address and server columns, with up to 16 rows.
- `jadx/sources/ru/himik/nightscream/kascada/Preference/ServerList/EditServerList.java` — the add chooser order is Wi‑Fi/3G/4G, Bluetooth, USB, KaScada Cloud; a normal row tap selects the server; long-press opens edit/delete actions.
- `jadx/sources/ru/himik/nightscream/kascada/Preference/NetworkDevicePref/PrefActivityNetworkDevice.java` — selecting a slot's server opens the server-list editor.
- `apktool/res/layout/item_server.xml`, `popup_server_list.xml`, `alert_dialog_add_usb.xml`, `values-ru/arrays.xml`, `values/colors.xml` and `values/styles.xml` — original row typography, popup order, serial-control arrays, colors and toolbar treatment.
- `jadx/sources/ru/himik/nightscream/kascada/Preference/ServerList/a.java` and `q/e/b/a.java` — USB row formatting and the integer encodings for stop bits and parity.

The supplied screen recording corroborates the server-list rows, Wi‑Fi form, RTU marker and paired-Bluetooth picker. Its visible rows and one popup do not establish complete Craft parity.

## Implemented in the prototype worktree

- Replaced the overflow-menu endpoint summary action with a `Сетевые устройства` screen and a 1–16 slot table.
- Aligned the network table and server list with source colors, toolbar title/subtitle, count controls, column labels, row dimensions, original transport artwork, row text sizing, chooser order and delete/edit popup order.
- The chooser now maps its original order (Wi-Fi, Bluetooth, USB, Cloud) to the actual Craft server type IDs (2, 1, 3, 4). Normal server-row taps select and assign; long press exposes source-ordered delete/edit actions.
- Added editable Wi‑Fi, Bluetooth, USB and cloud parameter forms. Bluetooth can enumerate already-paired devices where Android permissions allow it. The USB list is limited to one record as in the source.
- USB uses the Craft baud/data/stop/parity value sets, defaults (9600/8/2/None), and source stop/parity integer encodings. Its list detail is formatted as in the original server model.
- Added resource values copied from the reference APK in `app/src/main/res/values/craft_reference.xml`; compiled original bitmap assets are referenced by their generated `kc_` names.
- Stored screen configuration under project extension fields `craftNetworkDevices` and `craftServerConnections`; the normal project serializer carries these fields through project saves and exports.
- Kept configuration separate from runtime activation. This screen does not prove TCP/RTU/Bluetooth/USB transport acceptance; Bluetooth/USB permission and physical-device tests remain open.
- Added `CraftServerConnectionStoreTest` for source type IDs and chooser mapping, USB defaults/formatting, Wi‑Fi fields, JSON round-trip and unknown project-field retention.
- Extended the Android 11 smoke route to add a Wi‑Fi endpoint, verify persisted values, edit it through the long-press menu, select it for a slot and check the assigned row.

## Verification and open work

Local checks on 20 Oct 2026:

- `python3 tools/prepare_craft_resources.py` succeeded.
- Python suite: 169 tests passed after updating image-reference accounting for the Craft assets now used by the screen.
- Added a PID-exit race check to the Android smoke process-recreation helper; it treats a vanished PID as success only after `pidof` confirms the process has exited. The smoke then verifies that the DocumentsUI result returns to the saved properties form.
- Python compilation and `git diff --check` passed.
- This container has no Gradle executable or project wrapper. Java compilation/JUnit and emulator UI smoke remain pending exact-head GitHub Actions. Earlier UI run #882 failed in the picker route; its artifact lacks a failure traceback. The next run will save `failure.txt` and Activity/project diagnostics.

Acceptance remains partial. Still required: consume/round-trip original `deviceNetwork` and server records without shadowing them, verify all 16 slots and validation, preserve edits through import/export/reopen and Activity recreation, compare same-density screenshots with Craft, verify Android Bluetooth permissions, and run transport/hardware acceptance separately. The whole-app source-fidelity rule is recorded in `CRAFT_UI_SOURCE_FIDELITY_20261020.md`.


## Native Craft project round-trip update — 21 Oct 2026

PR [#211](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/211) now hydrates `craftNetworkDevices` and `craftServerConnections` from native `TableNameProject.deviceNetwork` and `TableConnections` during import. Save writes edited known fields back into the nested Craft source while retaining vendor fields, repeated type IDs, sparse slots, original array/string shape and the independent `countDevice` value. Connector assignment resolves only when the type and native server value identify one row; ambiguity stays inactive until an explicit editor selection.

The exact PR head `77344773dfbe9ab1ad61cc88afab6eedcb4a4d83` passed APK/JUnit [#1299](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39548787279), with debug APK artifact `12322693578`, and Android 11 UI smoke [#925](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39548787333). Tests prove import hydration and native JSON save round-trip; they do not establish physical transport behavior or complete same-density visual parity.


PR [#212](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/212) adds regression coverage for deleting a connector used by multiple network slots. Slots assigned to the removed connector clear; later list indices shift while remaining slots retain the same endpoint in native Craft serialization. Exact head `70fb81f136004e6a0e5efdd9a6c4155ca976e9ab` passed APK/JUnit #1300 and Android 11 smoke #926, and was merged as `bd8f8fdb90e1492e346b8a5d6c151e42def68a55`. Complete add/remove lifecycle, imported parameter editing, physical transports and visual comparison remain open.
