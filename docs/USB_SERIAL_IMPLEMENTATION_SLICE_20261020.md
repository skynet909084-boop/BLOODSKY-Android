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

The audited USB serial library is only a candidate. Before adding it, check its official release/API against this repository's Android/Gradle configuration, then pin a version and test the build. Until that is done, neither the library nor physical USB support is part of BLOODSKY.