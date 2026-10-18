# Аудит подключений Craft и причин неактивных компонентов

Дата: 2026-10-19  
Сверено: руководство HMI KaSCADA 1.4, pinned Craft APK 1.0.7 (JADX source/resources), BLOODSKY.

## Что подтверждено в оригинале

| Канал | Поведение Craft | Лимит и нюансы | Состояние BLOODSKY |
|---|---|---|---|
| Wi-Fi / IP-сеть | Настройка хранит IP/имя и TCP-порт (по умолчанию Modbus 502). `WifiConnector` создаёт TCP socket, соединяется с timeout 3000 ms, socket read timeout 5000 ms и повторяет цикл опроса. Сетевой интерфейс выбирает Android; отдельной привязки только к Wi-Fi в коде не видно. | В руководстве заявлены Modbus TCP и RTU-over-TCP. В декомпилированном Java `WifiConnector` формат пакета зафиксирован как режим 2 (MBAP/TCP); конструктор принимает boolean с именем из исходного checkbox, но не сохраняет его. Поэтому RTU-over-TCP по JADX подтвердить нельзя. Smali/DEX в закреплённом extracted reference отсутствуют, спорный аргумент требует повторной распаковки APK. | Modbus TCP FC01/02/03/04 и FC05/06/15/16/22 реализованы через `ModbusTcpClient`; I/O идёт в фоновых executor'ах. Wi-Fi и Ethernet доступны как Android-сети при IP-маршруте к устройству. RTU-over-TCP пока не поддержан. |
| Bluetooth | `DeviceBluetoothList` показывает `BluetoothAdapter.getBondedDevices()`; метод discovery пуст. `Bluetooth.b` открывает insecure RFCOMM с UUID SPP `00001101-0000-1000-8000-00805F9B34FB` (есть fallback через reflection). Это Classic Bluetooth serial, не BLE. | Устройство сначала нужно сопрячь в Android. Несколько Bluetooth endpoint могут занимать отдельные project slots, в общем лимите проекта. | Нет Bluetooth SPP transport и экрана выбора/разрешений в BLOODSKY. |
| USB-OTG serial | `q/e/a` пробует CDC ACM, CP21xx, FTDI, Prolific и CH34x; выбирает первый найденный serial port, запрашивает USB permission, открывает порт и задаёт baud/data/stop/parity. | Редактор запрещает добавить больше одной записи USB (`error_usb_only_one`). Это лимит конфигурации оригинального приложения; не доказательство, что Android/адаптер физически не способен видеть другие USB устройства. | Нет USB host enumeration, permission flow или serial Modbus RTU runtime. Скопированные/импортированные USB bindings должны оставаться неактивными. |
| Число устройств | Manual §2.2 и модель проекта подтверждают максимум 16 device slots. `MyApplication.f9042j` изначально равен 16; picker и canvas читают сохранённый `countDevice`, так что конкретный проект может использовать меньше. | 16 — максимум каналов/слотов конфигурации Craft, а не гарантия 16 одновременных физических соединений или пропускной способности. USB — не более одной конфигурации. | Нынешний BLOODSKY Modbus runtime принимает TCP endpoint на уровне связанной операции, но полного Craft device manager с 16 выбираемыми транспортами пока нет. Нельзя объявлять 16 одновременных сессий проверенными. |

## Modbus RTU: запрос, ответ и незакрытый путь

В Craft RTU — это серийный кадр `[unit][function][data][CRC low][CRC high]`, а не TCP MBAP. `x/a.c(record, mode)` отправляет mode 1/3 через RTU serializers и mode 2 через MBAP serializers. Bluetooth `Connections/Bluetooth/b.java` использует mode 1; USB `q/e/a.java` использует mode 3. Общая CRC таблица в `x/b.java` соответствует Modbus CRC-16 (poly `0xA001`, init `0xFFFF`); кадры идут через RFCOMM/serial stream. Для TCP request/response действует другой framing.

BLOODSKY в `MainActivity` уже содержит RTU CRC/frame helpers и `ModbusRtuTransport`, но этот stream helper не подключён к редактору, device manager, Bluetooth или USB. До этой проверки он не сверял response Unit ID и точное число байт данных; это исправлено и покрыто новыми JVM regression cases. Он по-прежнему реализует только FC01–FC04 read request/response, не реализует RTU записи, не задаёт timeout на произвольном InputStream и не управляет открытием/закрытием транспорта. Следовательно, это тестируемый протокольный фрагмент, а не активный RTU transport. Проверить физический RTU можно только после адаптеров SPP/USB, cancellation/timeout policy и hardware tests.

## Причины «притушенных» действий

Диммирование палитры в BLOODSKY сейчас следует за отсутствием подтверждённой цепочки «редактор → сохранение → runtime». Это не косметический запрет: включение без обработчика создало бы виджет, который пользователь считает рабочим, но который не выполняет действие.

- Источник-переключатель подключён к существующей кнопке; источник цифрового индикатора — к текстовому виджету. Это близкие доступные представления, но не полная семантическая/визуальная parity.
- ID 9 звукового сигнализатора и ID 19/22 шлюзов уже подключены к существующим обработчикам; шлюзам требуется явное TCP переназначение endpoint. Неизвестные исходные привязки не запускаются автоматически.
- Распознавание речи сохраняет конфигурацию, но его Craft-команда не исполняется; GIF не имеет совместимого playback runtime; камера/RTSP остаётся валидируемым, но не запускаемым placeholder; WebView не реализован как Craft widget. Эти строки должны оставаться явно неактивными до полного UI/runtime-пути и проверки lifecycle.
- В оригинальной FBD-палитре неподключённым остаётся превью `FB_MACRO`: вложенный граф нельзя корректно редактировать и запускать. Превью сенсоров `Sensor_Light`, `Sensor_Orientation`, `Sensor_Speed` уже связаны с FBD runtime и не являются выключенными: свет/ориентация зависят от наличия Android-датчика, скорость — от GPS и разрешения location. На эмуляторе отсутствие датчика/локации даст невалидное значение, а не подтверждение аппаратной работы.
- Значок документа в верхней панели редактора также намеренно отключён: просмотрщик project PDF пока не подключён. Это действие панели, не компонент проекта.
- Источник проекта может хранить соединения для компонентов, которые BLOODSKY пока не реализует. Сохранение этих полей не означает, что к устройству уже подключились.

## Вывод и проверяемый путь закрытия

Корень большинства неактивных transport-bound компонентов — не отсутствие исходных ID, а незавершённый BLOODSKY transport/device manager. Текущие операторы используют TCP-only runtime; Craft Bluetooth SPP и USB serial RTU надо добавлять как отдельные транспорты с общим безопасным Modbus PDU слоем, управлением разрешениями, таймаутами, отменой и reconnect.

Для включения каждого элемента требуются: настройки устройства и его endpoint, явное переназначение импортированных bindings, корректные FC/формат/масштаб, фоновой runtime с остановкой на lifecycle, тесты на локальном Modbus simulator и Android UI smoke. USB и Bluetooth дополнительно требуют физической аппаратной проверки; эмулятор их не подтверждает. RTU-over-TCP сначала нужно установить по DEX/Smali, поскольку текущего JADX bundle недостаточно для вывода.

## Evidence paths

- Руководство: `HMI-KaSCADA-v1.4-ot-08.09.19 (1).pdf`, §§2.2, 3.1–3.2 (pages 12, 21–28).
- Wi-Fi: `build/craft-reference/jadx/sources/ru/himik/nightscream/kascada/Connections/Wifi/WifiConnector.java`.
- Bluetooth: `.../Connections/Bluetooth/DeviceBluetoothList.java`, `.../Connections/Bluetooth/b.java`.
- USB runtime/drivers: `.../q/e/a.java`, `.../Connections/Usb/driver/`.
- USB one-entry rule: `.../Preference/ServerList/EditServerList.java`.
- Project slot count: `.../MyApplication.java`, `.../mbCanvas.java`, `.../Preference/NetworkDevicePref/`.
- BLOODSKY TCP boundary and tests: `docs/MODBUS_TCP_TRANSPORT.md`, `app/src/main/java/com/bloodsky/hmi/ModbusTcpClient.java`.

JADX output has known decompilation errors. The pinned extracted reference contains no Smali tree, so ambiguous source findings are marked as such rather than treated as proven bytecode behavior. No physical Wi-Fi, Bluetooth or USB device has been tested by this audit.
