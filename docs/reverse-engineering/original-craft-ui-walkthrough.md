# Original Craft 1.0.7 UI walkthrough (partial)

Evidence: emulator run [#61](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39243881294), branch `feat/craft-screen-navigation-import-20261018`, commit `5dc53835e403b7e2d57b0be6d9f55ca206a29c88`. The run rebuilt and launched the original APK offline. It completed successfully in 593.1 seconds and uploaded screenshots plus UIAutomator XML. Coverage is partial: 25 states, 24 transitions, state limit reached, zero project-loading stalls.

## Verified route

1. Main screen button `CREATE A NEW PROJECT` opens the project dialog.
2. The project-name field accepts text; `CREATE` opens the editor.
3. Editor toolbar exposes `menu_app_bar_hide`, `workToolbar`, `slidingScreenLeft`, `numScreen`, `slidingScreenRight`, `calc`, `pdf_doc`, and `menu_appbar_right`.
4. Floating `menu_bar_component` opens the component palette.
5. The palette is grouped into Setters, Text, Indicators, Graphic, and Other. Captured IDs include:
   - Setters: `tab_switch`, `tab_button`, `tab_digital_setters`, `tab_list`, `tab_line_setter`, `tab_voice_recognition`.
   - Text: `tab_text`, `tab_dyn_text`.
   - Indicators: `tab_digital_indicator`, `tab_lamp`, `tab_alarm`, `tab_line_level`, `tab_round_indicator`, `tab_sound`.
   - Graphic: `tab_graph`, `tab_gif`, `tab_dyn_pictures`, `tab_archive`, `tab_background`.
   - Other: `tab_gateway`, `tab_gateway_bit`, `tab_sms_in`, `tab_sms_out`, `tab_camera`, `tab_push_notif`, `tab_web_view`.
6. `tab_background` opens the background chooser with “Set an image” and “Use solid color”. The image option opens the Android Gallery/Files chooser; the color option opens a color picker with `CANCEL` and `INSTALL`.
7. `switch_layout` advances the editor library to FBD blocks, then to the macro library. The macro page displays `tvEmptyLibrary` / “Macros library is empty”.

## FBD palette IDs observed

Variable groups: `tab_var`, `tab_varOut`, `tab_varInt`, `tab_varOutInt`, `tab_varFloat`, `tab_varOutFloat`.

Blocks: `tab_fb_rs`, `tab_fb_t_trigger`, `tab_fb_timer`, `tab_fb_ctu`, `tab_fb_move`, `tab_fb_mx`, `tab_fb_macro`, `tab_fb_array`, conversions/sensors `tab_fb_word_to_float`, `tab_fb_int_to_float`, `tab_fb_float_to_int`, `tab_fb_sensor_orientation`, `tab_fb_dt`, `tab_fb_sensor_speed`, MQTT `tab_fb_mqtt_subscriber`, `tab_fb_mqtt_publisher`, math `tab_fb_sin`, `tab_fb_log`, `tab_fb_abs`, `tab_fb_pow`, `tab_fb_xor`, `tab_fb_sel`, `tab_fb_sunrise`, `tab_fb_sensor_light`; logic/electrical `tab_el_and`, `tab_el_or`, `tab_el_compare_int`, `tab_el_arithmetic_int`, `tab_el_arithmetic_float`, `tab_el_compare_float`, `tab_fb_change_i`, `tab_fb_change_f`.

## Automation gap and next probe

Most palette tiles are exposed as non-clickable `View`/`ImageView` nodes, so the generic quick traversal cannot discover their touch semantics from accessibility flags. It repeatedly tapped `tab_sound` without leaving the palette. Run #62 adds a zero-distance ADB swipe (1.1 s hold) when a normal tap produces no visible state transition. The next investigation must also test dragging/tapping a selected palette tile onto the canvas, inspect `menu_appbar_right`, and capture edit dialogs for placed components. Do not treat the empty macro-library screenshot as proof that macro creation is unavailable.

## Guided ADB verification (run #65)

Evidence: [run #65](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39257628353), commit `d00308ec1fab7c363f1ac3366979b10556a74155`, artifact `Original-Craft-1.0.7-emulator-probe` (artifact ID `12189260618`). A deterministic ADB route replaced the broad button walk for the quick profile. It completed in 39.5 seconds and captured 7 states / 6 actions with no project-loading stalls.

The screenshots and UIAutomator trees verify this exact route:

1. `CREATE A NEW PROJECT` → name dialog → entered `CraftAudit` → `CREATE`.
2. In the editor, `menu_bar_component` opens the component palette.
3. First `switch_layout` opens the FBD palette. Its visible tiles include `tab_fb_t_trigger`, `tab_fb_timer`, and `tab_fb_macro`.
4. Second `switch_layout` opens the macro library. The screenshot says “Macros library is empty”; the accessibility tree exposes only `switch_layout` there.

This confirms that the macro library is a distinct page after FBD. It does not yet verify creating a macro from a block: FBD tiles are custom non-clickable views, and the macro tile must be tested with the original long-press/drag gesture into the canvas. The run #65 artifact includes `state-000` through `state-006` PNG screenshots and matching XML trees.
