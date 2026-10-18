# Smart home and local assistant integration plan

## Product boundary

BLOODSKY's project editor, Modbus/FBD runtime, and existing screens remain usable locally without Yandex, Xiaomi, or an AI service. External integrations are optional adapters. Yandex's personal device-control API and Telegram require internet; the local core must continue operating when those services are unavailable.

## Integration shape

Use a provider-neutral `SmartHomeAdapter` boundary. Each adapter exposes a normalized device list, current readable values, writable capabilities, and scenario actions. Project bindings refer to a provider plus stable device/capability IDs; credentials stay outside exported project files. Unknown capabilities remain read-only until their value type, units, and write range are verified. A provider connection failure must not stop Modbus polling or local FBD execution.

### Yandex Smart Home

The request is to control devices already connected to a user's Yandex Smart Home account. Yandex documents a user-application API for listing home/device state, changing device and group capabilities, and launching scenarios. Access uses a registered Yandex OAuth app and `iot:view` / `iot:control` scopes. This is an optional cloud connector, separate from BLOODSKY's own cloud management (which remains out of scope).

Implementation sequence:

1. Add an opt-in Yandex adapter and account-linking flow; do not ask users to paste a long-lived token.
2. Read device IDs, names, capabilities, groups, and scenarios with `iot:view`.
3. Add `iot:control` actions only after explicit account consent; show the target and action before voice-triggered writes.
4. Keep provider request IDs and bounded error text in diagnostics; never put tokens in project exports or logs.

Official protocol: <https://yandex.ru/dev/dialogs/smart-home/doc/en/concepts/platform-protocol>.

### Xiaomi Home

The official Xiaomi-supported `Xiaomi Home` integration is a Home Assistant component, not a drop-in Android API. It signs into a Xiaomi account. Its README says commands use Xiaomi Cloud unless a supported central gateway provides local mode; the documented local LAN option is limited to Wi-Fi/Ethernet IP devices and is not recommended by Xiaomi. The local central-gateway option is region-limited.

Do not embed undocumented Mi Home endpoints or request Xiaomi passwords in BLOODSKY. First support a documented local bridge boundary (for example a user-configured MQTT/Home Assistant endpoint). A direct Xiaomi adapter can follow only where Xiaomi exposes a supported consumer-account or local-device interface for the user's actual devices. This keeps core control local while making any account/cloud dependency visible and opt-in.

Official Xiaomi integration and its local/cloud boundaries: <https://github.com/XiaoMi/ha_xiaomi_home>.

### Home Assistant bridge: first implementation

BLOODSKY now has an optional read-only Home Assistant REST connection. The user enters the local or HTTPS base URL and a Home Assistant access token, then BLOODSKY discovers and displays entity names, IDs and current states. The token is encrypted with an Android Keystore AES-GCM key and is never added to project JSON or logs. Plain HTTP is accepted only for localhost, `.local`, or private LAN IPv4 hosts; public hosts require HTTPS. Local HTTP still exposes the token to anyone able to observe the home network, so only use it on a trusted LAN. Device actions are deliberately not enabled in this discovery step.

This is a documented bridge path for Xiaomi: configure Xiaomi Home in Home Assistant first, then connect BLOODSKY to Home Assistant. It avoids asking BLOODSKY users for Xiaomi account passwords and keeps device discovery local when Home Assistant is local. Xiaomi devices that the configured Home Assistant integration cannot expose remain unavailable. Home Assistant recommends its WebSocket API for subscriptions/live updates; the current first step uses REST discovery and does not yet claim continuous updates.

Home Assistant official REST API: <https://developers.home-assistant.io/docs/api/rest>.

## Voice assistant: offline and online modes

The first implementation is push-to-talk with an explicit mode choice on every activation:

- **Local/offline:** API 31+ on-device `SpeechRecognizer`, gated by Android's runtime availability check. It never falls back to the network recognizer. BLOODSKY supports API 23+, so on older devices offline speech is reported unavailable until a bundled offline engine is selected and shipped.
- **Online:** Android's default speech-recognition service. The user sees that audio may be processed by that service and explicitly chooses this mode. If it is unavailable, the command fails visibly; it does not block Modbus/FBD or change the mode behind the user's back.

Both modes feed the same deterministic Russian allowlist and execute locally. The initial supported commands navigate existing screens by name/number and move to project run/stop mode. Starting the project asks for confirmation because it begins runtime execution; stopping is immediate. Unknown phrases are rejected. The current project model has no standalone named-scenario registry, so “run scenario” is not exposed as an implemented command. Arbitrary device writes, smart-home commands, and LLM-generated actions are not enabled by speech.

Android exposes `createOnDeviceSpeechRecognizer()` from API 31 and requires checking `isOnDeviceRecognitionAvailable()` before creating it. Text-to-speech is local Android TTS. No cloud LLM is currently configured; a future online language model may only help interpret/clarify a request before the same local allowlist, confirmation, and capability checks. Online providers remain opt-in and no paid service is added without explicit approval.

Android API reference: <https://developer.android.com/reference/android/speech/SpeechRecognizer>.

## Delivery order

1. Keep inbound/outbound SMS visible as optional SIM-based channels alongside Telegram; implement send/receive and runtime permission prompts before enabling either action.
2. Define and test the provider-neutral device/capability model against existing Modbus and MQTT types.
3. Build the Yandex opt-in OAuth adapter, then test read-only discovery before control actions.
4. Select a Xiaomi path from the user's actual hub/device inventory; prefer documented local control and keep any Xiaomi Cloud bridge opt-in.
5. Extend push-to-talk to read-only telemetry, then guarded controls and a real named-scenario model.

## Acceptance checks

- Local projects, Modbus polling, and FBD continue to work with airplane mode enabled.
- Provider tokens are not serialized into project JSON/ZIP or diagnostic logs.
- Unavailable provider devices do not change local project state or execute writes.
- Voice input cannot run an unknown or ambiguous scenario and cannot bypass confirmation for a guarded write.
- Offline speech never silently switches to a network recognizer; online speech is separately selected by the user.
- SMS prompts for permission only when the user enables SMS; unsupported devices without telephony messaging show a clear unavailable state.
