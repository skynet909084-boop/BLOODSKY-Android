# USB serial integration slice — 2026-10-20

This document began as the focused implementation slice for the existing umbrella task [#35](https://github.com/skynet909084-boop/BLOODSKY-Android/issues/35). The first transport/UI/binding slices have since been implemented; current code and verification status are recorded below. Physical hardware acceptance is still open.

## Craft source evidence and initial BLOODSKY gap (historical)

When this slice was proposed, the supplied Craft 1.0.7 decompilation confirmed USB serial probing for CDC ACM, CH34x, CP21xx, FTDI and Prolific, a permission request, port open, serial parameter configuration, then Modbus RTU I/O. At that earlier checkpoint BLOODSKY only had the stream RTU client. The old PR #38 raw USB-channel approach is not a safe base because it omits serial-driver configuration.

## First implementation slice

1. Add a small Android USB serial owner that enumerates supported serial drivers, chooses only an explicitly selected device, requests USB permission, opens one port, and applies the project's baud rate, data bits, stop bits and parity.
2. Adapt the opened serial port to the existing RTU client behind an injectable port/transport seam. Keep Android USB manager and vendor driver objects out of the protocol client.
3. Handle permission denial, device detach, cancellation, Activity/service shutdown and failed open/configure/read/write. Every terminal path closes the port and connection once; no imported Craft binding may silently start talking to a device.
4. Preserve the existing fail-closed rule: a copied/imported USB binding stays inactive until the user explicitly selects and configures the local device.

## Tests and acceptance

- Fake-port tests: successful open/configuration and RTU request/response; exact baud/data/stop/parity mapping; permission denied; unsupported/no device; open or configuration failure; read timeout/error; detach during exchange; cancellation and repeated close.
- Android tests: permission grant/denial through the receiver or activity-result path, attach/detach lifecycle and no leaked port after recreation/finish.
- Gate on the exact product commit: Python suite, Android unit tests, debug APK build and UI smoke. Record skipped/unavailable gates rather than inferring success.
- Hardware acceptance remains a separate gate: test at least one supported USB-RS485 adapter and verify a known RTU read plus unplug/reconnect. Do not report a physical adapter as tested until that run is recorded.

## Dependency decision

The upstream `mik3y/usb-serial-for-android` 3.11.0 release was checked against the app's `minSdk 23` and Java 17 configuration. Its documented API supplies device probing, permission handoff, `open`, `read`, `write`, and `setParameters`; its license is MIT. The pinned artifact is `com.github.mik3y:usb-serial-for-android:3.11.0` from JitPack. Keep the upstream MIT notice in `docs/THIRD_PARTY_NOTICES.md`.

## Implementation checkpoint

The prototype branch contains `UsbSerialSettings`, `UsbSerialConnector`, `UsbSerialSession`, and `UsbSerialModbusConnection`. Callers enumerate supported serial devices and pass the selected Android device name explicitly; the connector requests permission, configures the selected port, adapts it to `ModbusRtuClient`, and closes resources on cancellation, detach, I/O failure, or explicit close. The async API uses `Future` to retain Android API 23 compatibility. The later RTU write follow-up added FC05, FC06, FC0F, FC10 and FC22 with CRC and acknowledgement validation.

The prototype now also has a separate manual USB RTU screen in the home menu. The operator refreshes the device list, chooses one adapter, configures baud/parity/stop bits, explicitly connects, and starts FC03/FC04 reads with a button. Back, disconnect, screen exit and Activity destruction close the owned session. This screen is independent of project bindings and does not issue writes. Imported bindings remain inactive, and no physical adapter has been tested. The read/write transport slices passed their exact-head CI checks; this UI addition still needs its own exact-head APK/JUnit and UI validation. Hardware acceptance still requires a supported USB-RS485 adapter, a known RTU read, a write acknowledgement, and unplug/reconnect verification.

## Explicit project binding follow-up

`usb://<unit>/<register>` is now supported for Craft register variable sources, Craft register variable outputs, and `FBD Вход` only. Standard 3xxxx/4xxxx prefixes select FC04/FC03; other addresses are zero-based. The operator must enter the endpoint and explicitly select/connect the local adapter. The Activity keeps that selected session while returning home to load a project, then closes it on USB settings re-entry, explicit disconnect, or Activity destruction. Missing sessions fail visibly. Imported Craft connection metadata remains untouched and inactive. Parser coverage and exact-head CI are required before merging; physical adapter communication remains an open hardware gate.


## Current implementation and validation — 20 Oct 2026

USB-serial support now exists in the unified prototype. The implementation was integrated in these independently checked slices:

- PR [#167](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/167): selected-device USB connector, Android permission, UART configuration, RTU session and fake-port tests. APK/JUnit [#1170](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39404204508) and Android UI smoke [#801](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39404400404) passed.
- PR [#168](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/168): RTU write APIs FC05/06/0F/10/22, CRC and acknowledgement checks. APK/JUnit [#1175](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39406473632) and UI smoke [#808](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39406651996) passed.
- PRs [#169](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/169) and [#170](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/170): manual USB screen for explicit adapter selection/configuration/connection/read/disconnect and Android 11 navigation smoke. APK/JUnit [#1176](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39407984324), [#1177](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39408807345), UI smoke [#810](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39407984254) and [#811](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39408807324) passed.
- PR [#175](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/175): explicit `usb://<unit>/<register>` bindings for selected USB RTU session, including supported Craft variable source/output and FBD input paths. APK/JUnit [#1186](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39415056759), [#1187](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39415113998) and Android 11 UI smoke [#825](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39415114000) passed.
- PR [#179](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/179): fake-port test for a detach during an active read; APK/JUnit [#1195](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39419308463) and UI smoke [#833](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39419309643) passed.

`UsbSerialSessionTest` exercises serial framing, RTU read/write, timeout, I/O failure, detach unblocking and idempotent close with a fake port. `UsbSerialBindingTest` exercises explicit endpoint parsing. The manual diagnostics screen exposes FC03/FC04 reads. Project register bindings can route supported writes through the explicitly selected session. Imported Craft connection metadata is not silently activated: the operator must configure an explicit local endpoint and select the device.

## Remaining USB acceptance

- Android UsbManager integration instrumentation: enumerate/choose, permission grant/denial, cancellation, attach/detach broadcasts and Activity recreation/finish cleanup. Existing fake-port tests do not cover the Android broadcast/permission stack.
- The user currently has no USB-serial adapter. Physical acceptance with a supported USB-RS485 adapter and known PLC/device must cover read, write acknowledgement, unplug and reconnect when hardware is available.
- A full persistent Craft device manager with up to 16 transport slots is still a separate requirement. The manual USB screen and explicit binding route do not complete that manager.
- Equal-density visual comparison with Craft remains open.

The USB implementation is therefore **code implemented, automatic fake-port/binding tests passed, Android permission and physical hardware acceptance incomplete**. Do not report real USB hardware as tested.
