# KaScada Smart Craft 1.0.7 (107) — карта интерфейса по APK

## Подтверждение сборки

- APK: `KaScada Smart Craft 1.0.7 (08.02.2026).apk`
- Package ID: `ru.kascada.modbus`
- Version: `1.0.7`, build code `107`
- SHA-256 APK: `11464db76f59393ccf698f57e60af1756756ffc78cb315d43052ae92adb50d4f`
- 4622 файловых записей внутри APK, 273 XML-макетов и 18 XML-файлов контекстных меню (включая вспомогательные/библиотечные макеты).
- APK и ранее предоставленная декомпиляция совпали побайтово по AndroidManifest.xml, цифровому сертификату CERT.RSA и подписному MANIFEST.MF.
- В APK встроен `assets/help.pdf` (6 страниц), описывающий основные графические компоненты, регистры и блоки логики.

**Статус проверки:** это статический аудит ресурсов и smali-кода. Android-эмулятор недоступен, приложение не запускалось, нажатия по живому интерфейсу не проводились. Отображение/доступность функций зависят от лицензии, сохраненных проектов, настроек и подключённых устройств.

## Главное меню — подтвержденные XML-обработчики

| Кнопка в оригинале | ID | Метод |
|---|---|---|
| Create a new project | `bGoToCanvas` | `onStartWork` |
| Upload a project | `button3` | `onLoadProject` |
| Delete a project | `button4` | `onDeleteProject` |
| Export a project | `btnExport` | `onExportProject` |
| Import a project | `btnImport` | `getImportFile` |
| Exit | `button2` | `onExit` |

Маршруты, проверенные в `MainActivity.smali`:
- `onLoadProject` — запрашивает разрешение на чтение хранилища при необходимости и запускает `ProjectLoadListActivity`.
- `onDeleteProject` — запускает `ProjectDeleteListActivity`.
- `onExportProject` — запрашивает разрешение на запись и запускает `ProjectExportActivity` после разрешения; на старой версии Android запускает сразу.
- `getImportFile` — запрашивает разрешение на чтение хранилища; переход определяется callback после выдачи разрешения.
- `onStartWork` — подготавливает диалог создания проекта; точный результат для всех вариантов ввода не проверен в работающем приложении.
- `onExit` — показывает диалог подтверждения.

## Холст/редактор — кнопки из XML-макета и обработчики

| ID | Обработчик | Подтвержденное назначение по именам и smali |
|---|---|---|
| `menu_app_bar_hide` | `onClickIcoCharging` | Отображение/действие с иконкой зарядки или скрытием меню (код требует дальнейшей проверки) |
| `ico_charging` | `onClickIcoCharging` | Отображение/действие с иконкой зарядки или скрытием меню (код требует дальнейшей проверки) |
| `(нет ID)` | `onClickGetCalculator` | Открывает DialogFragment калькулятора |
| `(нет ID)` | `onClickGetInfo` | Открывает сведения/документ или меню по режиму |
| `slidingScreenRight` | `clickScreenRight` | Переключение экрана вправо |
| `numScreen` | `onClickSetNameScreen` | Установка названия экрана |
| `slidingScreenLeft` | `clickScreenLeft` | Переключение экрана влево |
| `lockScreen` | `lockScreen` | Блокировка экрана/редактирования |
| `workToolbar` | `onClickWorkToolbar` | Переключает панель работы/редактирования |
| `(нет ID)` | `selectActionCut` | Вырезать выделенные элементы |
| `(нет ID)` | `selectActionCopy` | Копировать выделенные элементы |
| `(нет ID)` | `selectActionPaste` | Вставить |
| `dialogAlignComponents` | `openDialogAlignComponents` | Открывает окно выравнивания компонентов |

## Панель HMI-компонентов

В XML-панели найдено **27** элементов, по ID:

- `tab_switch`, `tab_button`, `tab_digital_setters`, `tab_list`, `tab_line_setter`, `tab_voice_recognition`, `tab_text`, `tab_dyn_text`
- `tab_digital_indicator`, `tab_lamp`, `tab_alarm`, `tab_line_level`, `tab_round_indicator`, `tab_sound`, `tab_graph`, `tab_gif`
- `tab_dyn_pictures`, `tab_archive`, `tab_background`, `tab_gateway`, `tab_gateway_bit`, `tab_sms_in`, `tab_sms_out`, `tab_camera`
- `tab_push_notif`, `tab_web_view`, `tab_email`

Группы панели по строковым ресурсам: **Setters, Text, Indicators, Graphic, Other**. В числе компонентов — переключатель, кнопка, цифровой задатчик, список, слайдер, голосовое управление, надписи, индикаторы, тревога, звук, график, анимация GIF, динамическая картинка, архив, фон, шлюзы, SMS, RTSP, push, веб-компонент и email.

## Панель Logic/Var/FB/EL

В XML-панели найдено **40** элементов.

- `tab_var`, `tab_varOut`, `tab_varInt`, `tab_varOutInt`, `tab_varFloat`, `tab_varOutFloat`, `tab_var_name_in`, `tab_var_name_out`
- `tab_fb_rs`, `tab_fb_t_trigger`, `tab_fb_timer`, `tab_fb_ctu`, `tab_fb_move`, `tab_fb_mx`, `tab_fb_macro`, `tab_fb_array`
- `tab_fb_word_to_float`, `tab_fb_int_to_float`, `tab_fb_float_to_int`, `tab_fb_sensor_orientation`, `tab_fb_dt`, `tab_fb_sensor_speed`, `tab_fb_mqtt_subscriber`, `tab_fb_mqtt_publisher`
- `tab_fb_sin`, `tab_fb_log`, `tab_fb_abs`, `tab_fb_pow`, `tab_fb_xor`, `tab_fb_sel`, `tab_fb_sunrise`, `tab_fb_sensor_light`
- `tab_el_and`, `tab_el_or`, `tab_el_compare_int`, `tab_el_arithmetic_int`, `tab_el_arithmetic_float`, `tab_el_compare_float`, `tab_fb_change_i`, `tab_fb_change_f`

Встроенная справка дополнительно описывает входные и выходные Var Boolean/Int/Float; триггеры RS/T/R/F; таймеры TON/TOF/TP; счётчики; MOVE, POW, XOR, SEL, MUX, преобразования типов, тригонометрические и математические блоки, датчики и операции AND/OR/сравнения/арифметики. Не все функции справки обязательно доступны без лицензии.

## Контекстные меню — ресурсы APK

Приведены фактические названия пунктов после подстановки ресурсов `strings.xml`. Это наличие пункта в APK, а не подтверждение его доступности в каждом режиме.

### `actionmenu.xml`
- Edition of the list of servers (`edit_list_servers`)
- Set a password (`action_set_password`)
- Activate the mobile mode (`change_mode`)
- Activate the timeout displaying (`change_show_timeout`)
- Creation of bridge WiFi-USB (`bridge_wifi_to_usb`)

### `popup_alarm.xml`
- Sound notification (`popup_alarm_setSound`)
- Network device (`popup_set_connections_device`)
- Address (`popup_set_address`)

### `popup_archive.xml`
- Configuration (`popup_archive_preference`)
- Network device (`popup_archive_connections_device`)
- Address (`popup_archive_address`)
- Graphic (`popup_archive_graph`)
- Export to csv (`popup_archive_export_csv`)

### `popup_background_screen.xml`
- Set an image (`popup_set_background_image`)
- Use solid color (`popup_set_background_color`)

### `popup_caller.xml`
- Resize (`popup_caller_resize`)
- Change the number (`popup_caller_phone_number`)

### `popup_dyn_pic.xml`
- Resize (`popup_resize`)
- Configuration (`popup_preference`)
- Network device (`popup_set_connections_device`)
- Address (`popup_set_address`)

### `popup_dyn_text.xml`
- Resize (`popup_resize`)
- Configuration (`popup_preference`)
- Network device (`popup_set_connections_device`)
- Address (`popup_set_address`)

### `popup_gateway.xml`
- Configuration (`popup_gateway_preference`)
- Source (`popup_gateway_choose_source_server`)
- Address (`popup_gateway_set_address_source`)
- Receiver (`popup_gateway_choose_receiver_server`)
- Address (`popup_gateway_set_address_receiver`)

### `popup_label.xml`
- Resize (`popup_label_resize`)
- Configuration (`popup_label_preference`)

### `popup_lamp.xml`
- Resize (`popup_label_resize`)
- Configuration (`popup_preference`)
- Network device (`popup_set_connections_device`)
- Address (`popup_set_address`)
- Replace image (`popup_change_background`)

### `popup_network_device_list.xml`
- Edit the network device (`network_device_edit_item`)
- Delete the network device (`network_device_delete_item`)

### `popup_pdf_info.xml`
- Select a PDF-file (`popup_set_pdf_path_file`)
- Open a file (`popup_open_pdf`)

### `popup_seek_bar.xml`
- Resize (`popup_resize`)
- Configuration (`popup_preference`)
- Network device (`popup_set_connections_device`)
- Address (`popup_set_address`)
- Rotate (`popup_turn`)

### `popup_server_list.xml`
- Delete (`delete_item`)
- Edit (`edit_item`)

### `popup_sms.xml`
- Configuration (`popup_sms_preference`)
- Network device (`popup_set_connections_device`)
- Address (`popup_set_address`)

### `popup_sound.xml`
- Configuration (`popup_sound_preference`)
- Network device (`popup_set_connections_device`)
- Address (`popup_set_address`)

### `popup_switch.xml`
- Resize (`popup_resize`)
- Configuration (`popup_preference`)
- Network device (`popup_set_connections_device`)
- Address (`popup_set_address`)
- Replace image (`popup_change_background`)

### `popupmenu.xml`
- Resize (`popup_resize`)
- Configuration (`popup_preference`)
- Network device (`popup_set_connections_device`)
- Address (`popup_set_address`)

## Диалоги настроек (из ресурсов)

Найдено **50** файлов `dialog_*.xml`, а также отдельные макеты всплывающих редакторов логических блоков.

- `dialog_alarm.xml` — Sound notification; Network device; Reg:; Bit:
- `dialog_archive.xml` — Settings; Network device; Reg:; Bit:; Graphic; Export to csv
- `dialog_background.xml` — Set an image; Use solid color
- `dialog_button.xml` — Settings; Network device; Address; Replace image
- `dialog_caller.xml` — Change the number
- `dialog_camera.xml`
- `dialog_choose_devices.xml` — System registries; 1; 2; 3; 4; 5; 6; 7; 8
- `dialog_choose_image_lamp.xml` — Choose from the library or specify the path for your picture.; Specify your picture
- `dialog_choose_import_source.xml` — "KaScada Cloud 
(link or QR)"; This device; Import .pro (deprecated way)
- `dialog_circle_gauge.xml` — Settings; Network device; Reg:
- `dialog_constant_bool.xml` — Укажите константу Bool; Ok
- `dialog_constant_float.xml` — Укажите константу Int; Ok
- `dialog_constant_int.xml` — Укажите константу Int; Ok
- `dialog_dyn_pictures.xml` — Settings; Network device; Reg:
- `dialog_dyn_text.xml` — Settings; Network device; Reg:
- `dialog_edit_number.xml` — Settings; Network device; Reg:
- `dialog_email.xml` — Settings; Network device; Reg:; Bit:
- `dialog_gateway.xml` — Settings; Network device; Reg:; Network device; Reg:
- `dialog_gateway_bit.xml` — Settings; Network device; Address; Network device; Address
- `dialog_gauge2.xml` — Settings; Network device; Reg:
- `dialog_gif.xml` — Settings; Network device; Reg:; Bit:
- `dialog_graph.xml` — Settings; Network device; Reg:
- `dialog_label.xml` — Settings
- `dialog_lamp.xml` — Settings; Network device; Reg:; Bit:; Replace image
- `dialog_line_level.xml` — Settings; Network device; Reg:
- `dialog_list.xml` — Settings; Network device; Reg:
- `dialog_load_voice_model.xml` — Wait, file is downloading
- `dialog_menu_align_components.xml` — Component alignment
- `dialog_menu_image_lamp.xml` — Click on the image to select an image; ON; OFF
- `dialog_modbus_slave.xml` — Modbus RTU Slave (Wi-Fi); Please, specify the port:; KaScadaCloud; (error key)
- `dialog_push_notif.xml` — Settings; Network device; Reg:; Bit:
- `dialog_quit.xml` — Exit. Save the project?; Save as...; Do not save; Yes
- `dialog_register.xml` — Settings; Network device; Reg:
- `dialog_seek_bar.xml` — Settings; Network device; Reg:; Rotate
- `dialog_set_address.xml` — Register:; 512; Bit:; 0
- `dialog_set_function_and_bit_gatewaybit.xml` — Reg:; Bit:
- `dialog_set_pause.xml` — Pause; Accept
- `dialog_set_reg_bool.xml` — Register; Bit; Ok
- `dialog_set_reg_bool_out.xml` — Register; Bit; Ok
- `dialog_set_reg_float.xml` — Register; Ok
- `dialog_set_reg_int.xml` — Register; Ok
- `dialog_set_reg_out_float.xml` — Register; Ok
- `dialog_set_reg_out_int.xml` — Register; Ok
- `dialog_sms.xml` — Settings; Network device; Reg:; Bit:
- `dialog_sms_receive.xml` — Settings; Network device; Reg:; Bit:
- `dialog_sound.xml` — Settings; Network device; Reg:
- `dialog_switch.xml` — Settings; Network device; Reg:; Bit:; Replace image
- `dialog_switch_backup.xml` — Resize; Settings; Network device; Address; Replace image
- `dialog_voice_recognition.xml` — Settings; Network device; Reg:
- `dialog_web_view.xml` — Settings; Network device; Reg:

## Важные экраны Activity (AndroidManifest)

- `ru.himik.nightscream.kascada.MainActivity`
- `ru.himik.nightscream.kascada.mbCanvas`
- `ru.himik.nightscream.kascada.Preference.PrefActivity`
- `ru.himik.nightscream.kascada.Utils.ShowQrCode`
- `ru.himik.nightscream.kascada.Bd.ProjectLoadListActivity`
- `ru.himik.nightscream.kascada.Bd.ProjectDeleteListActivity`
- `ru.himik.nightscream.kascada.Preference.ServerList.EditServerList`
- `ru.himik.nightscream.kascada.Connections.Bluetooth.DeviceBluetoothList`
- `ru.himik.nightscream.kascada.Preference.Address.PrefActivityAddressConnection`
- `ru.himik.nightscream.kascada.Bd.Export.ProjectExportActivity`
- `ru.himik.nightscream.kascada.Bd.Import.ProjectImportActivity`
- `ru.himik.nightscream.kascada.Preference.NetworkDevicePref.PrefActivityNetworkDevice`
- `ru.himik.nightscream.kascada.Preference.AlarmSelectSound.PrefActivitySelectAlarmSound`
- `ru.himik.nightscream.kascada.Preference.BridgeWiFiToUsb.BridgeWiFiToUsb`
- `com.karumi.dexter.DexterActivity`
- `com.journeyapps.barcodescanner.CaptureActivity`
- `com.google.android.gms.common.api.GoogleApiActivity`

## Разрешения приложения

Среди разрешений: Bluetooth, Wi-Fi, сеть, доступ к файлам, состояние телефона, местоположение, звук, камера, SMS, уведомления и пробуждение экрана. Само по себе наличие разрешения не означает, что приложение использует его при каждом запуске. Полный список в JSON.

## Что ещё нужно проверить именно запуском на Android

- Вид каждого экрана, размеры и порядок элементов, нажатия, жесты и контекстные меню.
- Создание/редактирование/сохранение и экспорт/импорт на настоящей установленной сборке.
- Наличие и поведение функций в бесплатном и лицензионном режимах.
- Проверку аппаратных сценариев USB/Bluetooth/Modbus RTU/TCP, MQTT, Wi-Fi, SMS, камеры и RTSP только с соответствующими тестовыми устройствами и разрешениями.
- Сопоставление каждого экрана и поведения с BLOODSKY в одинаковых тест-кейсах.

## Методика и ограничения

- Получен APK из сообщения пользователя; без проверки официального сертификата издателя нельзя удостоверять происхождение с внешнего магазина.
- Декомпиляция не равна запуску: часть элементов создаётся динамически, а поведение зависит от состояния и внешних сервисов.
- Все идентификаторы и маршруты взяты из предоставленных файлов APK и совпавшей с ним декомпиляции, без генерации вымышленных экранов.