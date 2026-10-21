# KaScada Smart Craft 1.0.7 (107) — полный аудит функций

**Источники:** предоставленный APK и совпадающая декомпиляция, AndroidManifest, XML-ресурсы и smali-код.

**Честный статус:** статическая инвентаризация выполнена; физический/облачный Android-эмулятор в этой сессии не подключён. Ни один сценарий кликами по запущенному приложению пока не проверен. Этот документ — полный реестр проверок, а не заявление о 100%-ной работоспособности.

## Проверяемая база

- APK SHA-256: `11464db76f59393ccf698f57e60af1756756ffc78cb315d43052ae92adb50d4f`
- Package: `ru.kascada.modbus`; версия `1.0.7`, code `107`.
- Основных пунктов меню: **6**.
- HMI-элементов на панели: **27**; Logic/VAR/FB/EL-элементов: **40**.
- Файлов меню: **18**; XML-макетов `dialog_*.xml`: **50**; Android Activities в манифесте: **17**, включая сторонние.
- Оригинальная встроенная справка: `assets/help.pdf`.
- Автоматизированный реестр проверки: **550** отдельных тестов с ID, файлом-источником и текущим статусом.

## Масштаб функционального исследования

1. **Все проекты:** создание, имя, ориентация, загрузка, удаление, экспорт, импорт, сохранение, сохранение как, восстановление, конфликт названий.
2. **Полный HMI-редактор:** все 27 компонентов, панели, разные экраны, выделение, перетаскивание, изменение размеров, копирование/вырезание/вставка, слои, фон, ориентация, режим исполнения.
3. **Логика:** все 40 элементов панели Logic/VAR/FB/EL, связи/значения, таймеры/триггеры/счётчики, функции, датчики, макросы и MQTT.
4. **Каждый диалог и контекстное меню:** все найденные 18 XML-меню, 50 `dialog_*.xml`, панели и дополнительные окна, динамические действия.
5. **Каждая настройка каждого компонента:** значения, диапазоны, типы, цвета, ограничения, восстановление после перезапуска.
6. **Связь и оборудование:** USB, Bluetooth, Wi-Fi, Modbus TCP/RTU, серверы, шлюзы, мост Wi-Fi–USB, разрывы и переподключения.
7. **KaScadaCloud:** import/share, QR, Modbus Slave по облаку, веб-управление, доступ и лицензия.
8. **Медиа и внешние каналы:** камера, RTSP, GIF, звук, голосовой ввод, SMS, email, push, WebView и HTTP.
9. **Данные:** графики, динамические индикаторы, архивы, CSV, отправка архивов в облако.
10. **Негативные и лицензионные сценарии:** права Android, offline, ошибки файлов и устройств, free/full, сворачивание/возврат, смена ориентации, падение/перезапуск.

## Дополнительные подтверждённые окна и разделы (по ресурсам)

В `res/layout/menu_.xml`: редактирование списка сетевых устройств, пароль, мобильный режим, отображение тайм-аута, мост Wi-Fi–USB (по умолчанию скрыт в XML), сетевое управление и фоновая работа.
В `res/layout/bottomsheet_remote_control.xml`: локальный Modbus RTU Slave (Wi-Fi), подключение через KaScadaCloud и веб-управление через KaScadaCloud (помечено как требующее полной версии).
В `res/layout/dialog_choose_import_source.xml`: импорт из KaScadaCloud по ссылке/QR, с устройства, через устаревший .pro.
В настройках графика — подписи о минимальном периоде 100 мс и 2–1000 точках, в архиве — 1–4 канала, в битовом шлюзе — до 16 бит. Это параметры интерфейса, не результаты runtime-тестов.

## Инвентарь по источникам: главные меню, HMI, Logic

### Главное меню

- `bGoToCanvas` — **Create a new project**, обработчик `onStartWork`
- `button3` — **Upload a project**, обработчик `onLoadProject`
- `button4` — **Delete a project**, обработчик `onDeleteProject`
- `btnExport` — **Export a project**, обработчик `onExportProject`
- `btnImport` — **Import a project**, обработчик `getImportFile`
- `button2` — **Exit**, обработчик `onExit`

### HMI-компоненты (27)

- `tab_switch`
- `tab_button` — Button
- `tab_digital_setters`
- `tab_list` — List
- `tab_line_setter`
- `tab_voice_recognition`
- `tab_text` — Text
- `tab_dyn_text` — Dyn. text
- `tab_digital_indicator`
- `tab_lamp`
- `tab_alarm`
- `tab_line_level`
- `tab_round_indicator`
- `tab_sound`
- `tab_graph`
- `tab_gif` — GIF
- `tab_dyn_pictures`
- `tab_archive`
- `tab_background` — Background
- `tab_gateway`
- `tab_gateway_bit`
- `tab_sms_in`
- `tab_sms_out`
- `tab_camera`
- `tab_push_notif`
- `tab_web_view`
- `tab_email`

### Логические компоненты (40)

- `tab_var`
- `tab_varOut`
- `tab_varInt`
- `tab_varOutInt`
- `tab_varFloat`
- `tab_varOutFloat`
- `tab_var_name_in`
- `tab_var_name_out`
- `tab_fb_rs`
- `tab_fb_t_trigger`
- `tab_fb_timer`
- `tab_fb_ctu`
- `tab_fb_move`
- `tab_fb_mx`
- `tab_fb_macro`
- `tab_fb_array`
- `tab_fb_word_to_float`
- `tab_fb_int_to_float`
- `tab_fb_float_to_int`
- `tab_fb_sensor_orientation`
- `tab_fb_dt`
- `tab_fb_sensor_speed`
- `tab_fb_mqtt_subscriber`
- `tab_fb_mqtt_publisher`
- `tab_fb_sin`
- `tab_fb_log`
- `tab_fb_abs`
- `tab_fb_pow`
- `tab_fb_xor`
- `tab_fb_sel`
- `tab_fb_sunrise`
- `tab_fb_sensor_light`
- `tab_el_and`
- `tab_el_or`
- `tab_el_compare_int`
- `tab_el_arithmetic_int`
- `tab_el_arithmetic_float`
- `tab_el_compare_float`
- `tab_fb_change_i`
- `tab_fb_change_f`

### Все контекстные меню (18 файлов)

- `actionmenu.xml` — Edition of the list of servers, Set a password, Activate the mobile mode, Activate the timeout displaying, Creation of bridge WiFi-USB
- `popup_alarm.xml` — Sound notification, Network device, Address
- `popup_archive.xml` — Configuration, Network device, Address, Graphic, Export to csv
- `popup_background_screen.xml` — Set an image, Use solid color
- `popup_caller.xml` — Resize, Change the number
- `popup_dyn_pic.xml` — Resize, Configuration, Network device, Address
- `popup_dyn_text.xml` — Resize, Configuration, Network device, Address
- `popup_gateway.xml` — Configuration, Source, Address, Receiver, Address
- `popup_label.xml` — Resize, Configuration
- `popup_lamp.xml` — Resize, Configuration, Network device, Address, Replace image
- `popup_network_device_list.xml` — Edit the network device, Delete the network device
- `popup_pdf_info.xml` — Select a PDF-file, Open a file
- `popup_seek_bar.xml` — Resize, Configuration, Network device, Address, Rotate
- `popup_server_list.xml` — Delete, Edit
- `popup_sms.xml` — Configuration, Network device, Address
- `popup_sound.xml` — Configuration, Network device, Address
- `popup_switch.xml` — Resize, Configuration, Network device, Address, Replace image
- `popupmenu.xml` — Resize, Configuration, Network device, Address

## Настройки компонентов и экранов

Ниже перечислены конкретные файлы XML-настроек. Все изменяемые `*Preference` представлены отдельными тестами в JSON-матрице.

- `base_address_button.xml` — 2 настроек: `address_register`, `address_bit`
- `base_address_connection.xml` — 2 настроек: `address_register`, `address_bit`
- `base_address_connection_sms_receive.xml` — 3 настроек: `SmsReceiveTypeCommand`, `address_register`, `address_bit`
- `base_preference.xml` — 6 настроек: `base_pref_edit_text`, `AutoResizeText`, `base_pref_edit_height_text`, `colorText`, `enableFrameLabel`, `colorBackground`
- `base_preference_button.xml` — 6 настроек: `base_pref_edit_text`, `AutoResizeText`, `base_pref_edit_height_text`, `colorText`, `list_button_mode`, `checkVisible`
- `base_preference_dyn_text.xml` — 37 настроек: `colorText`, `listTypeCommandDynText`, `AutoResizeText`, `base_pref_edit_height_text`, `enableFrameDynText`, `base_pref_edit_text0`, `colorBackground0`, `base_pref_edit_text1`, `colorBackground1`, `base_pref_edit_text2`…
- `base_preference_edit_number.xml` — 10 настроек: `listTypeEditNumber`, `listTypeIntEditNumber`, `colorText`, `checkFontChangeEditNumber`, `enableFrameEditNumber`, `colorBackground`, `min`, `max`, `listCountNumAfterPointEditNumber`, `edit_number_pass`
- `base_preference_lamp.xml` — 1 настроек: `listTypeLamp`
- `base_preference_register.xml` — 7 настроек: `listTypeRegister`, `colorText`, `enableFrameRegister`, `colorBackground`, `listDigit`, `listDivisor`, `checkFontChangeRegister`
- `base_preference_switch.xml` — 1 настроек: `checkVisible`
- `base_preference_voice_recognition.xml` — 16 настроек: `voice_rec_text_bit_0`, `voice_rec_text_bit_1`, `voice_rec_text_bit_2`, `voice_rec_text_bit_3`, `voice_rec_text_bit_4`, `voice_rec_text_bit_5`, `voice_rec_text_bit_6`, `voice_rec_text_bit_7`, `voice_rec_text_bit_8`, `voice_rec_text_bit_9`…
- `base_preference_web_view.xml` — 16 настроек: `web_view_link_bit_0`, `web_view_link_bit_1`, `web_view_link_bit_2`, `web_view_link_bit_3`, `web_view_link_bit_4`, `web_view_link_bit_5`, `web_view_link_bit_6`, `web_view_link_bit_7`, `web_view_link_bit_8`, `web_view_link_bit_9`…
- `preference_archive.xml` — 15 настроек: `ArchiveFileName`, `listArchiveConnections`, `ArchiveType`, `ArchiveCountChannel`, `ArchiveRegister`, `ArchiveOptionChannel`, `ArchiveTypeOptionChannel`, `ArchiveRegisterOptionChannel`, `ArchiveLengthOptionChannel`, `ArchiveIsReset`…
- `preference_circle_gauge.xml` — 8 настроек: `gauge_showText`, `gauge_sizeText`, `gauge_textColor`, `gauge_outColor`, `gauge_inColor`, `gauge_centerColor`, `max_gauge`, `range_gauge`
- `preference_dyn_pictures.xml` — 19 настроек: `listTypeCommandDynPicture`, `prefEditTouchX`, `prefEditTouchY`, `EditPathPicture_0`, `EditPathPicture_1`, `EditPathPicture_2`, `EditPathPicture_3`, `EditPathPicture_4`, `EditPathPicture_5`, `EditPathPicture_6`…
- `preference_email.xml` — 6 настроек: `email_login`, `email_password`, `email_to`, `email_title`, `email_body`, `email_info`
- `preference_gateway.xml` — 5 настроек: `editGatewayCountRegister`, `listModeGateway`, `listGatewayConnections`, `gatewayReg`, `gatewayBit`
- `preference_gateway_bit.xml` — 5 настроек: `editGatewayBitCountBit`, `listModeGateway`, `listGatewayConnections`, `gatewayReg`, `gatewayBit`
- `preference_gauge2.xml` — 12 настроек: `gauge_colorTextValue`, `gauge_colorTextScale`, `gauge_countTextScale`, `gauge_sizeText`, `gauge_colorCenterCircle`, `gaugeIndicator`, `gauge_colorIndicator`, `gauge_colorMark`, `hideBackground`, `gauge_min`…
- `preference_gif.xml` — 1 настроек: `EditGifPath`
- `preference_graph.xml` — 9 настроек: `listTypeGraph`, `graphCountChannel`, `EditGraphPeriodPref`, `EditGraphCountPointPref`, `graphColorBackground`, `graphNameSeries1`, `graphNameSeries2`, `graphNameSeries3`, `graphNameSeries4`
- `preference_line_level.xml` — 8 настроек: `chShowText`, `editSizeText`, `colorText`, `lineLevelEditMin`, `lineLevelEditMax`, `colorProgress`, `colorBackground`, `chShowScale`
- `preference_list.xml` — 21 настроек: `base_pref_edit_text`, `base_pref_edit_height_text`, `colorText`, `checkVisible`, `checkNoChange`, `base_pref_list_text_0`, `base_pref_list_text_1`, `base_pref_list_text_2`, `base_pref_list_text_3`, `base_pref_list_text_4`…
- `preference_push_notif.xml` — 3 настроек: `pref_edit_push_to`, `pref_edit_push_title`, `pref_edit_push_body`
- `preference_seek_bar.xml` — 4 настроек: `colorText`, `seekBarEditMax`, `colorProgress`, `colorBackground`
- `preference_sms.xml` — 5 настроек: `pref_edit_sms_phone`, `pref_edit_sms_message`, `chOption`, `optionRegister`, `optionCountRegister`
- `preference_sms_receive.xml` — 6 настроек: `listTypeCommandSmsReceive`, `SmsReceivePhone`, `SmsReceiveTextCommand`, `smsRecCheckStrob`, `smsRecReg`, `smsRecBit`
- `preference_sound.xml` — 8 настроек: `EditPathSound_0`, `EditPathSound_1`, `EditPathSound_2`, `EditPathSound_3`, `EditPathSound_4`, `EditPathSound_5`, `EditPathSound_6`, `EditPathSound_7`
- `register_address_connection.xml` — 1 настроек: `address_register`

## Сквозные тесты функциональности


### Создание и организация проектов

1. пустое имя и длинное имя
2. одинаковые имена / дубликаты
3. кириллица, Unicode и спецсимволы
4. горизонтальная и вертикальная ориентация
5. создание первого экрана и нескольких экранов
6. переименование экранов и их переключение
7. загрузка существующего проекта
8. удаление с подтверждением/отменой
9. выход с сохранением/без сохранения и Save as
10. закрытие приложения и восстановление проекта

### Импорт, экспорт, обмен

1. экспорт во встроенное хранилище
2. импорт с устройства
3. импорт .pro (legacy)
4. импорт KaScadaCloud по ссылке
5. импорт по QR-коду
6. проверка коллизий имён при импорте
7. повреждённый/пустой/неподдерживаемый файл
8. перенос проекта между двумя устройствами

### Редактор HMI

1. добавление/выбор компонента
2. перетаскивание и координаты
3. ресайз во всех направлениях
4. множественное выделение
5. выравнивание элементов
6. вырезать/копировать/вставить
7. удалить и отменить действие
8. слои и перекрытие объектов
9. подложка из файла или сплошного цвета
10. автоматический/ручной размер текста
11. блокировка редактирования и рабочий режим
12. панель инструментов: открыть/скрыть

### Исполнение и регистры

1. переход редактор → исполнение → редактор
2. чтение Bool/Int/Float
3. запись Bool/Int/Float
4. битовая адресация и граничные адреса
5. обрыв связи / восстановление и повторная передача
6. одновременные компоненты на одном регистре
7. масштабирование, диапазоны, типы и ограничения ввода
8. поведение реального времени при фоновой работе

### Подключения и серверы

1. добавление TCP Wi-Fi: IP, порт, пауза, RTU toggle
2. USB последовательные: baud/data/stop/parity/pause
3. Bluetooth: поиск/пара/отключение
4. редактирование и удаление сетевых устройств
5. редактирование/удаление списка серверов
6. связь нескольких сетевых устройств в одном проекте
7. тайм-ауты, повторные подключения, неверные адреса
8. WiFi–USB мост и проверки внешнего контроллера

### Логические блоки

1. цепочки AND/OR/сравнение/арифметика
2. RS/T-триггеры и таймеры TON/TOF/TP
3. счётчик и фронты сигналов
4. MOVE/MUX/SEL/XOR/POW/ABS/LOG/SIN
5. преобразование INT/FLOAT/WORD
6. массивы и макросы: создание/редактирование/переиспользование
7. датчики ориентации, скорости, освещённости
8. время/дата и астрономические блоки
9. повторное открытие логического проекта с соединениями

### Сеть, облако и внешняя интеграция

1. локальный Modbus RTU Slave по Wi-Fi
2. Modbus RTU Slave через KaScadaCloud
3. web-управление через KaScadaCloud
4. MQTT publish
5. MQTT subscribe
6. контроль сети извне и пароль доступа
7. HTTP/WebView и внешние URL
8. лицензионные ограничения облака

### События, уведомления и медиа

1. аварийная сигнализация и звук
2. SMS исходящие по событию
3. SMS входящие и разбор команд
4. email отправка по событию
5. push по ключу устройства
6. микрофон: распознавание команд по битам
7. загрузка голосовой модели
8. RTSP/camera, сетевой поток
9. анимированные GIF и динамические изображения
10. воспроизведение звука по переменной

### Данные и визуализация

1. графики: от 2 до 1000 точек (значения из подписей)
2. график: период от 100 мс (из подписи)
3. несколько линий графика, названия и цвета
4. архивирование 1–4 каналов (из подписи)
5. архив: очищение, выгрузка CSV
6. архив: отправка в облако по регистру/биту
7. шкалы, стрелочные и круглые индикаторы
8. задатчики, слайдеры и списки
9. экранные датчики и визуальная индикация

### Эксплуатация и ограничения

1. без лицензии / активная лицензия
2. покупка/активация лицензии только в разрешённой тестовой среде
3. разрешения Android и сценарий отказа
4. фон/сворачивание/возврат приложения
5. изменение ориентации и разрешения
6. недостаточно места на диске
7. нет интернета / слабая сеть
8. падение/перезапуск приложения без потери проекта
9. конфиденциальность SMS, микрофона, камеры и логов

## Что необходимо сделать на Android

Запустить **именно этот** APK и пройти каждый тест из JSON-матрицы. Для каждого теста сохранять шаги, видеозапись или скриншоты, значения настроек, Android API, состояние лицензии, версию приложения, ожидаемый и реальный результат. Ставить PASS/FAIL/BLOCKED с причиной.
**Нельзя помечать PASS по названию XML-ресурса или по наличию smali-класса.** Наличие кнопки и параметра в коде не гарантирует их работоспособность на текущем Android или без лицензии.
Для проверки устройств использовать разрешённый тестовый Modbus-сервер/контроллер, тестовые учётные записи и адреса. Не отправлять реальные SMS/email, не активировать платные подписки и не менять промышленное оборудование без отдельного подтверждения.
## Результаты для BLOODSKY

После тестирования каждый экран и компонент сопоставить с BLOODSKY: IDENTICAL / FUNCTIONALLY_EQUIVALENT / MISSING / BROKEN / UNVERIFIED; приложить скриншоты, шаги, различия и GitHub issue. Порядок приоритета — сначала проекты/холст/HMI/Logic/регистры, затем сетевые функции, затем фон/облако/медиа.