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

## Local voice assistant

Start with push-to-talk, offline speech recognition, a constrained Russian command grammar, and local text-to-speech. Commands can select an existing screen, read an explicitly selected value, and run or stop a named BLOODSKY scenario. The parser must only resolve to known project actions; ambiguous commands get a spoken/text clarification. Device writes and scenario starts require a confirmation step until the user can review and disable that guard per action.

Android exposes an on-device `SpeechRecognizer` from API 31 and a runtime availability check. BLOODSKY supports API 23+, so older devices need an explicitly bundled offline recognizer/model; otherwise voice control reports unavailable and must not silently fall back to a network recognizer. The assistant must not transmit audio or commands to a cloud LLM. A local language model can be evaluated later against the target set-top box's RAM/CPU budget; deterministic intent parsing remains the safe execution layer.

Android API reference: <https://developer.android.com/reference/android/speech/SpeechRecognizer>.

## Delivery order

1. Keep inbound/outbound SMS visible as optional SIM-based channels alongside Telegram; implement send/receive and runtime permission prompts before enabling either action.
2. Define and test the provider-neutral device/capability model against existing Modbus and MQTT types.
3. Build the Yandex opt-in OAuth adapter, then test read-only discovery before control actions.
4. Select a Xiaomi path from the user's actual hub/device inventory; prefer documented local control and keep any Xiaomi Cloud bridge opt-in.
5. Add offline push-to-talk for named local scenarios, then extend to read-only telemetry and guarded control.

## Acceptance checks

- Local projects, Modbus polling, and FBD continue to work with airplane mode enabled.
- Provider tokens are not serialized into project JSON/ZIP or diagnostic logs.
- Unavailable provider devices do not change local project state or execute writes.
- Voice input cannot run an unknown or ambiguous scenario and cannot bypass confirmation for a guarded write.
- SMS prompts for permission only when the user enables SMS; unsupported devices without telephony messaging show a clear unavailable state.
