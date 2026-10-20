# USB serial integration slice — 2026-10-20

This is the next focused implementation slice for the existing umbrella task [#35](https://github.com/skynet909084-boop/BLOODSKY-Android/issues/35). It narrows the first code change; it does not mark USB support implemented.

## Evidence and current boundary

The supplied Craft 1.0.7 decompilation confirms USB serial probing for CDC ACM, CH34x, CP21xx, FTDI and Prolific, a permission request, port open, serial parameter configuration, then Modbus RTU I/O. BLOODSKY currently has a stream-level RTU client, but no Android USB host enumeration, permission flow, serial-port lifecycle, or runtime binding from an imported project. The old PR #38 raw USB-channel approach is not a safe base because it omits serial-driver configuration.

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
