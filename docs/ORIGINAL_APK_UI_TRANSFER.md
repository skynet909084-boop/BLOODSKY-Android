# Original APK transfer contract — user screenshots, 2026-10-16

## Mandatory implementation method

The user clarified: these screenshots show the original appearance; do not redraw or invent it. Transfer original resources and rendering/interaction code from the decompiled KaScada Craft 1.0.7 APK into BLOODSKY. Screenshots are verification evidence only, not assets to cut into replacement widgets.

Source: pinned reference/craft-reference.zip, reference/README.md and tools/prepare_craft_resources.py. Prepared originals are available under build/craft-reference/apktool/res, assets and build/craft-reference/jadx/sources/ru/himik/nightscream/kascada. Where Java decompilation is incomplete, inspect the matching Smali/resources; do not infer semantics from appearance alone.

The existing 152 compiled image aliases and bundled original files must be connected to actual runtime controls/states. Merely packaging resources or making a similar Canvas thumbnail does not meet the requirement.

## Source-to-runtime acceptance ledger

For each control, supply:
1. Original exact file/resource ID or renderer class/method, with provenance.
2. State selection and original geometry/text/font behavior.
3. BLOODSKY production call site that uses that original asset/ported renderer.
4. Tests for state and property persistence.
5. Original/BLOODSKY screenshots at matching viewport/density/orientation, with residual differences.
6. Status: wired-and-compared / wired-unverified / missing / blocked. Do not use Matched without evidence.

Retain original image/theme/font resources, state lists, menu order and labels, dialog structures and rendering logic. Do not use emoji, generic icons, new gradients, generated images or newly designed vectors as substitutes for available APK originals. Programmatic drawing is appropriate when it ports the original renderer rather than replacing it.

## Supplied comparison references

Six original screenshots are committed byte-for-byte in reference/user-ui-20261016/. These contain reusable UI reference views; other supplied views with user-specific identifiers/notification overlays are described only at UI level below. No screenshot is a new runtime asset or proof of implementation.

| Reference | Original filename | Confirmed visible evidence |
|---|---|---|
| [Component picker](../reference/user-ui-20261016/component-picker.jpg) | 1000006850.jpg | Categories Задатчики, Текст, Индикаторы, Графика, Прочее; real metallic previews and stateful green controls; page corner marker. |
| [FBD picker](../reference/user-ui-20261016/fbd-picker.jpg) | 1000006851.jpg | Var/FB/EL groups, grey blocks with thin black contours, pin-specific layouts, round/arrow variables and triangular operator forms. |
| [Device picker](../reference/user-ui-20261016/device-picker.jpg) | 1000006860.jpg | Выберите устройство dialog with Системные регистры and a configured device entry. |
| [Text properties](../reference/user-ui-20261016/text-properties.jpg) | 1000006861.jpg | Native preference-style rows: Изменить текст, Авто размер текста, Размер шрифта, Цвет текста; disabled font row with auto-size enabled. |
| [Register/bit](../reference/user-ui-20261016/register-bit-properties.jpg) | 1000006862.jpg | Component context/settings popup shows chosen device, separate register and bit editors, then Сменить изображение. |
| [Visibility](../reference/user-ui-20261016/visibility-properties.jpg) | 1000006863.jpg | Сделать невидимым with description Компонент будет прозрачным в режиме работа. |

### Additional observed references from the supplied set

- Landscape editor: dark top toolbar, orange menu/play-or-stop/screen arrows, screen title, lock, clock, calculator, information icon, overflow; orange plus at lower left; textured canvas.
- Alternative toolbar state: menu and plus remain over the canvas. Exact toggle behavior needs source/interaction verification.
- Portrait home: KaScada Smart Craft heading, six large metallic project-action buttons in order create/load/delete/export/import/exit; use original APK backgrounds/button resources. BLOODSKY branding changes must not copy personal license/identifiers.
- Runtime project: custom background, repeated metallic text controls, round indicator images, seven-segment numeric appearance and configurable numeric widths. Background content and current values are user project data, not defaults for BLOODSKY.
- Global settings popup: device list editing, password, stationary mode, timeout display, network control, background mode.
- Server editor/new connection chooser: Wi-Fi/3G/4G, Bluetooth, Usb, KaScada Cloud entries. A visible connection choice does not prove its transport is implemented in BLOODSKY.
- Context menu sequence includes Настройки, Устройство, Адрес and Сменить изображение; another view explicitly separates register and bit.
- Empty macro library has centered empty-state text and a corner page marker.
- Android navigation bars, notification overlays, changing clock values and connection toasts must not be built into the app UI.

## Important semantic checks

- Do not equate system registers with arbitrary Modbus addresses. Identify original address map and resolver from source.
- Separate device selection, register and bit in project serialization/binding; do not discard original fields.
- The screenshot says font height in pixels. Determine original conversion/scaling from source instead of assuming our current sp field has equivalent behavior.
- Авто размер текста affects the manual font-size row. Verify both appearance and persistence.
- The invisibility label refers to runtime transparency. Inspect original behavior for editability, hit testing and interaction before replacing it with global View.GONE or simply skipping the widget.
- Preserve actual OFF/ON/pressed/error/disabled selection from originals. A single sample screenshot does not establish every state.
- FBD block colors alone are insufficient: preserve shape, header, labels, left/right pin counts and spacing according to each original class.
- Existing screenshot shows an empty macro list, not proof that a macro engine is absent.
- Server/device screens must retain original menu paths; no real network connection or write is authorized by screenshots.

## Scheduling

Jules recovery session 14565554863478191515 currently owns ZIP/media. Keep this reference package and source-first transfer contract for the visual/component phase after its acceptance; do not launch a competing Activity rewrite. Remaining original-asset binding work from PR38 stays incomplete until transferred and compared.

This change only records original reference evidence. It does not claim a UI port, image-state parity, transport support or Android device verification.
