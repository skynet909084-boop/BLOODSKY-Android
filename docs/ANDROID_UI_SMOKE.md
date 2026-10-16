# Android UI smoke gate

A separate read-only GitHub Actions workflow builds and installs the debug APK on an Android 11/API30 x86_64 emulator. It runs on relevant PRs/main changes and can be dispatched manually. The emulator action is pinned to the commit resolved from its official v2 tag; no signing/deployment credentials are used.

`tools/android_ui_smoke.py` uses actual UIAutomator text/class/bounds to select controls; it does not assume fixed tap coordinates. It verifies six home actions, creates a named project, force-stops/restarts the app, reopens that project, confirms deletion and checks the active/catalog values from the debug sandbox. It captures six screenshot/UI XML pairs and crash diagnostics in the `BLOODSKY-Android-UI-smoke` artifact. Ambiguous or missing controls fail the job.

Scope: installation and this named-project UI flow on a disposable emulator. It does not verify original visual parity, image/audio decoding, hardware transports, API23/API35 compatibility, real Xiaomi lifecycle behavior or a signed release. Screenshots are evidence for further inspection, not an automatic pixel-parity assertion. Never run this script against a personal device: it clears the installed debug app's data. The CI workflow starts its own disposable emulator.
