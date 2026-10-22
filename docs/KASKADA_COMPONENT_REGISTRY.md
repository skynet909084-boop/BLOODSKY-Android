# Kaskada Craft — component registry and editor operations

This note extends the first-pass editor/widget map using verified code paths in `mbCanvas.java`. Component tags below are the integer values used by the deletion dispatcher; they are not a full registry. The separate factory-to-import map, including source keys read for every ID, is in [CRAFT_COMPONENT_ID_FACTORY_MATRIX_20261018.md](CRAFT_COMPONENT_ID_FACTORY_MATRIX_20261018.md).

## Component tag registry observed in `deleteView(View)`

| Tag | Runtime component class |
|---:|---|
| 1 | `CustomComponents.y` |
| 2 | `CustomLabel` |
| 4 | `CustomComponents.u` |
| 5 | `CustomComponents.q` |
| 6 | `CustomComponents.e` |
| 7 | `CustomDynText` |
| 8 | `CustomComponents.i` |
| 9 | `CustomComponents.c` |
| 10 | `CustomComponents.w` |
| 11 | `CustomComponents.r` |
| 12 | `CustomComponents.g` |
| 13 | `CustomComponents.v` |
| 14 | `CustomComponents.o` |
| 15 | `CustomComponents.d` |
| 18 | `CustomComponents.x` |
| 19 | `CustomComponents.k` |
| 20 | `CustomList` |
| 21 | `CustomSound` |
| 22 | `CustomComponents.l` |
| 23 | `CustomComponents.f` |
| 24 | `CustomComponents.m` |
| 25 | `CustomComponents.t` |
| 26 | `CustomComponents.b0` |
| 27 | `CustomComponents.a0` |
| 28 | `CustomComponents.j` |

Tags 3, 16 and 17 do not appear in this deletion switch. The factory `m(int, ...)` in the pinned decompilation does create ID 16 as `CustomComponents.n` (GIF) and ID 17 as `CustomComponents.h` (16-state image), so deletion-switch membership is not a reliable factory map. ID 3 is also absent from the factory method and remains unresolved; do not infer that it is invalid. The deletion routine removes the component from a corresponding application-level collection, removes the view from its parent, and invalidates the FBD drawing surface for logic blocks.

## Confirmed editor behaviors

- Screen navigation: `clickScreenLeft(View)` calls `s(false)`; `clickScreenRight(View)` calls `s(true)`.
- Resize operations have explicit `StartResize(View)` and `Resizing(View)` entry points.
- Alignment has `alignSelectView(View)` and `openDialogAlignComponents(View)`.
- Copy/cut/paste have separate toolbar handlers. Paste reconstructs a widget through the shared factory `m(...)`, passing its text, dimensions, position, text size, connection JSON, text color, background, and additional JSON.
- When pasted, the copied component is moved to the current screen and marked selected. The code also adjusts connection fields during paste, so BLOODSKY should not assume that a duplicate must blindly retain every original device binding; determine the original UX semantics before implementing this detail.
- The editor uses a shared interface and application-level collections keyed by component ID. BLOODSKY should use stable IDs and a central registry so delete/copy/selection cannot leave stale references.

## Porting caution

Craft component ID 6 mode 2 (`CustomComponents.e`) is implemented as `Константная кнопка Craft`: import preserves value type, constant, source register metadata and `visibleInWork`, while legacy device mapping remains disabled. The editor validates UINT16 or finite Float32 constants and requires an explicit TCP holding-register target. Operator presses write FC06 for UINT16 or FC16 for Float32; the original Craft register number is retained as source metadata and is not silently reused as a BLOODSKY network address. Side-by-side UI and device acceptance remain open.

Craft ID 13 (`CustomSeekBar`, `v.java`) is implemented as `Линейный задатчик Craft`: import preserves `max`, `isTurn` orientation and colors while leaving legacy device mapping disabled. The editor validates a 1–65535 integer maximum; operator mode presents a slider and writes the confirmed value through FC06 to an explicitly configured TCP holding register. Side-by-side visual and device acceptance remain open.

Craft ID 20 is implemented as `Список Craft`: sixteen independently selectable labels backed by a UINT16 mask, matching `CustomList` (`textBit0`–`textBit15`, `getValue`, `setValue`, and `l(bit)`). Imported labels and both `visibleInWork` / `checkNoChange` flags survive conversion. Imported device IDs remain unbound until an operator enters an explicit TCP holding-register endpoint; the runtime reads FC03 and writes the mask with FC06. Empty labels are unavailable, and `checkNoChange` makes the operator list read-only. Register/manual/hardware parity beyond this TCP mapping remains open.

JADX emits type-inference warnings around parts of the large factory and paste code. Use Smali and call-site analysis to resolve ambiguous branches before treating the factory's full tag mapping as definitive. Preserve unknown type IDs and unknown JSON fields during project import whenever possible.


Craft ID 6 mode 0 (`CustomComponents.e`) is imported as a momentary bit command. The source `addressBit` is retained as a validated 0–15 hint, but the installation-specific Craft device binding stays inactive. Operator mode requires an explicit TCP holding-register and opt-in, then uses FC22 to set the bit on touch-down and clear it on touch-up/cancel/activity pause. Native BLOODSKY buttons retain their previous toggle behavior unless explicitly configured as Craft buttons. Hardware/UI parity and release behavior during transport failure remain open.


Craft ID 6 mode 1 (`CustomComponents.e`) is imported with the original 1-based `changeScreen` mapped to a zero-based BLOODSKY screen index. Invalid target indices are flagged and do not silently redirect. The editor offers existing screens by index/name or an external URL, validates that a destination is selected, and preserves `visibleInWork` as an operator-mode visibility setting. Screen-navigation rendering/flow still needs side-by-side UI acceptance.


Craft ID 9 (`CustomComponents.c`) preserves `posSound` 1–5 and the source's loop-while-active behavior. Its property editor now exposes the five bundled Craft alarm sounds or a custom audio file/content URI; the imported device endpoint remains inactive until explicit TCP remapping. Playback now uses separate MediaPlayer/ToneGenerator instances keyed by screen and widget; one widget's stop/completion does not clear another. `tools/test_alarm_playback_contract.py` guards start/stop/completion isolation and lifecycle cleanup. Simultaneous audio, trigger-device and visual acceptance remain open.


Craft ID 23 (`CustomComponents.f`) is connected to the original camera palette slot, editable `rtsp_url`, and a foreground Android `VideoView`. Import preserves the original `craftOther` and opaque connection metadata while setting `craftRtspEnabled=false`; an operator must enable playback in properties and confirm each network launch. Playback is stopped on app pause, screen navigation, operator-mode exit, explicit stop, or dialog dismissal. `CraftRtspConfig` rejects non-RTSP(S) URLs, URI user-info, fragments, and malformed endpoints. Android codec/RTSPS compatibility and hardware playback have not been accepted on a device.


Craft ID 27 is `CustomComponents.a0`, titled with the original `custom_voice_recognition` resource and carrying sixteen `textVoice0`–`textVoice15` phrase fields. It is a voice-recognition control, not the TTS announcement widget. The palette and property editor expose all sixteen fields. A user can explicitly enable one-shot on-device recognition and select a local project register; tapping the component in operator mode and confirming starts Android's on-device recognizer, and a unique literal match sets the phrase-index bit. Imported instances remain disabled until configured. Craft's wake word, always-listening session, `#` variable substitution, and original external command transport are not ported; no microphone session starts automatically. Android-device validation remains outstanding.

Craft ID 25 (`CustomComponents.t`) now imports its title, body and topic, and can emit a local notification after an explicit opt-in on a 0→1 transition. The original embedded FCM server key is not reused; no remote FCM send is provided. Craft ID 26 (`CustomComponents.b0`) imports and edits its sixteen `link0`–`link15` values with per-link length limits, preserves unknown metadata, and remains a disabled WebView placeholder. The source's bitmask-driven URL loading is not run for imported projects.
