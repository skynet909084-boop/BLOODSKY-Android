# Аудит подключений Craft и причин неактивных компонентов

Дата: 2026-10-23
Сверено: руководство HMI KaSCADA 1.4, Craft APK 1.0.7 (JADX Java и полный Apktool/Smali артефакт), BLOODSKY.

## Что подтверждено в оригинале

| Канал | Поведение Craft | Лимит и нюансы | Состояние BLOODSKY |
|---|---|---|---|
| Wi-Fi / IP-сеть | Настройка хранит IP/имя и TCP-порт (по умолчанию Modbus 502). `WifiConnector` создаёт TCP socket, connect timeout 3000 ms, read timeout 5000 ms и цикл опроса. Сетевой интерфейс выбирает Android; отдельной привязки только к Wi-Fi в коде не видно. | Руководство заявляет Modbus TCP и RTU-over-TCP. Java и Smali подтверждают, что `WifiConnector` всегда задаёт mode 2 (MBAP/TCP); boolean-флаг RTU из конструктора перезаписывается значениями по умолчанию и нигде не сохраняется/не читается. Для этой версии APK RTU-over-TCP настройкой не включается. | Modbus TCP FC01/02/03/04 и FC05/06/15/16/22 реализованы через `ModbusTcpClient`; I/O идёт в фоновых executor'ах. Wi-Fi/Ethernet идут через доступный Android IP-маршрут. BLOODSKY RTU-over-TCP пока не поддерживает. |
| Bluetooth | `DeviceBluetoothList` показывает `BluetoothAdapter.getBondedDevices()`; метод discovery пуст. `Bluetooth.b` открывает insecure RFCOMM с UUID SPP `00001101-0000-1000-8000-00805F9B34FB` (есть fallback через reflection). Это Classic Bluetooth serial, не BLE. | Устройство сначала нужно сопрячь в Android. Несколько Bluetooth endpoint могут занимать отдельные project slots, в общем лимите проекта. | `BluetoothModbusConnection`/`BluetoothRtuBinding` обслуживают явно заданные `bt://MAC/unit/address` widget и ID19/22 gateway endpoints; текущий операторский сеанс требует RTU consent и Android `BLUETOOTH_CONNECT`. Fake/client tests passed; реальные paired-device connect, denial/disconnect recovery remain open. |
| USB-OTG serial | `q/e/a` пробует CDC ACM, CP21xx, FTDI, Prolific и CH34x; выбирает первый найденный serial port, запрашивает USB permission, открывает порт и задаёт baud/data/stop/parity. | Редактор запрещает добавить больше одной записи USB (`error_usb_only_one`). Это лимит конфигурации оригинального приложения; не доказательство, что Android/адаптер физически не способен видеть другие USB устройства. | `UsbSerialConnector` открывает только выбранный вручную адаптер из RTU screen, `UsbSerialModbusConnection` обслуживает RTU reads/writes и подключённые widget/ID19/22 gateway mappings. Requires operator-mode/current-session consent; imported endpoint data does not auto-select/open USB. Fake-port JUnit passed; UsbManager instrumentation and physical adapter/PLC acceptance remain open. |
| Число устройств | Manual §2.2 и модель проекта подтверждают максимум 16 device slots. `MyApplication.f9042j` изначально равен 16; picker и canvas читают сохранённый `countDevice`, так что конкретный проект может использовать меньше. | 16 — максимум каналов/слотов конфигурации Craft, а не гарантия 16 одновременных физических соединений или пропускной способности. USB — не более одной конфигурации. | BLOODSKY supports explicit TCP operations, configured SPP endpoints and one manually selected USB RTU session. A full Craft device manager with 16 selectable transports is still open; no 16-session acceptance claim. |

## Modbus RTU: запрос, ответ и незакрытый путь

В Craft RTU — это serial frame `[unit][function][data][CRC low][CRC high]`, а не TCP MBAP. Smali `x/a.c(record, mode)` подтверждает: mode 1/3 строят RTU, mode 2 строит MBAP. Bluetooth `Connections/Bluetooth/b.java` использует mode 1; USB `q/e/a.java` — mode 3. Общая CRC таблица в `x/b.java` соответствует Modbus CRC-16 (poly `0xA001`, init `0xFFFF`); кадры идут через RFCOMM/serial stream. Таким образом RTU-over-TCP из руководства не следует смешивать с RTU по Bluetooth/USB: наблюдаемый Wi-Fi коннектор mode 2 не вызывает RTU-ветку.

BLOODSKY routes RTU frames through `ModbusRtuClient`, `BluetoothModbusConnection` and the manually selected USB `UsbSerialModbusConnection`. Widgets and ID19/22 gateways use explicit bindings, FC01/02/03/04 reads and supported write functions; USB detach and I/O failure close the session, while gateway calls require current operator consent. JVM tests cover frame parsing and fake serial boundaries. Real SPP/USB adapters, Android permission/detach instrumentation, reconnect behavior and PLC interoperability still require device acceptance.

## Причины «притушенных» действий

Диммирование палитры в BLOODSKY сейчас следует за отсутствием подтверждённой цепочки «редактор → сохранение → runtime». Это не косметический запрет: включение без обработчика создало бы виджет, который пользователь считает рабочим, но который не выполняет действие.

- Источник-переключатель подключён к существующей кнопке; источник цифрового индикатора — к текстовому виджету. Это близкие доступные представления, но не полная семантическая/визуальная parity.
- ID 9 звукового сигнализатора и ID 19/22 шлюзов подключены к существующим обработчикам; внешние endpoints требуют явного remap и operator-session consent. Неизвестные исходные привязки не запускаются автоматически.
- ID 19 supports local `system://register/N` read/write endpoints limited to project user registers 10–1999. ID 22 supports local `system://register/N/bit/B` ranges on the same bank. Both gateways can use explicit TCP/BT/USB endpoints; USB still uses the manually selected adapter and external hardware is unverified.
- Распознавание речи сохраняет конфигурацию, но его Craft-команда не исполняется; GIF не имеет совместимого playback runtime; камера/RTSP остаётся валидируемым, но не запускаемым placeholder; WebView не реализован как Craft widget. Эти строки должны оставаться явно неактивными до полного UI/runtime-пути и проверки lifecycle.
- В оригинальной FBD-палитре неподключённым остаётся превью `FB_MACRO`: вложенный граф нельзя корректно редактировать и запускать. Превью сенсоров `Sensor_Light`, `Sensor_Orientation`, `Sensor_Speed` уже связаны с FBD runtime и не являются выключенными: свет/ориентация зависят от наличия Android-датчика, скорость — от GPS и разрешения location. На эмуляторе отсутствие датчика/локации даст невалидное значение, а не подтверждение аппаратной работы.
- Значок документа в верхней панели редактора также намеренно отключён: просмотрщик project PDF пока не подключён. Это действие панели, не компонент проекта.
- Источник проекта может хранить соединения для компонентов, которые BLOODSKY пока не реализует. Сохранение этих полей не означает, что к устройству уже подключились.

## Вывод и проверяемый путь закрытия

Корень оставшихся неактивных transport-bound компонентов — не отсутствие исходных ID, а незавершённый BLOODSKY device manager и незакрытые Android/hardware gates. Текущие операторы используют Modbus TCP, explicit Bluetooth SPP and a manually opened USB serial RTU session; полный Craft device manager для 16 слотов остаётся незавершённым.

Для включения каждого элемента требуются: настройки устройства и его endpoint, явное переназначение импортированных bindings, корректные FC/формат/масштаб, фоновый runtime с остановкой на lifecycle, тесты на локальном Modbus simulator и Android UI smoke. USB и Bluetooth дополнительно требуют физической аппаратной проверки; эмулятор их не подтверждает. Сверка Smali показывает, что в Craft 1.0.7 Wi-Fi connector использует только MBAP; настройка RTU-over-TCP из руководства в этой версии не включается.

## Evidence paths

- Руководство: `HMI-KaSCADA-v1.4-ot-08.09.19 (1).pdf`, §§2.2, 3.1–3.2 (pages 12, 21–28).
- Wi-Fi: `build/craft-reference/jadx/sources/ru/himik/nightscream/kascada/Connections/Wifi/WifiConnector.java`.
- Bluetooth: `.../Connections/Bluetooth/DeviceBluetoothList.java`, `.../Connections/Bluetooth/b.java`.
- USB runtime/drivers: `.../q/e/a.java`, `.../Connections/Usb/driver/`.
- USB one-entry rule: `.../Preference/ServerList/EditServerList.java`.
- Project slot count: `.../MyApplication.java`, `.../mbCanvas.java`, `.../Preference/NetworkDevicePref/`.
- BLOODSKY TCP boundary and tests: `docs/MODBUS_TCP_TRANSPORT.md`, `app/src/main/java/com/bloodsky/hmi/ModbusTcpClient.java`.

JADX output has known decompilation errors. The committed reduced `craft-reference.zip` omits Smali, поэтому Smali сверялся в полном workflow artifact `BLOODSKY-Kaskada-decompiled` (run `38600957172`, artifact `11912849123`). Проверены `WifiConnector.smali`, `x/a.smali`, `Bluetooth/b.smali`, `DeviceBluetoothList.smali` и `q/e/a.smali`; точные DEX-выводы для RTU mode зафиксированы выше. Физические Wi-Fi, Bluetooth и USB устройства этим аудитом не тестировались.


## USB serial implementation evidence from supplied source — 2026-10-20

- The newly supplied decoded tree (`KaScada_v1.0.7(107)_base_src.zip`, SHA-256 `92d12206bde746f5a76c89e0c3311119287b14bb26cdc8ca36d515e2fca01f7a`) contains the original serial-driver family and app call sites, including `q/e/a.smali` and `Preference/BridgeWiFiToUsb/b.smali`.
- The app enumerates devices through `UsbSerialProber.findAllDrivers(UsbManager)`, requests permission, opens the selected device with `UsbManager.openDevice`, opens its first serial port, then applies `setParameters(baud, dataBits, stopBits, parity)`. The RTU worker reads and writes frames through that serial-port API; the Wi-Fi-to-USB bridge does the same for its configured port.
- The included driver family covers CDC ACM, CH34x, CP21xx, FTDI and Prolific serial devices. The port contract exposes 5–8 data bits, 1/1.5/2 stop bits and none/odd/even/mark/space parity. The Wi-Fi-to-USB bridge constructor starts with 9600 baud, 8 data bits, 2 stop bits and no parity; treat that as that bridge's default, not a universal RTU requirement.
- This confirms the missing BLOODSKY slice is an Android USB host/permission/driver owner around the stream-level protocol client. The integration must preserve exact settings, handle permission denial/detach/cancellation, close the port on lifecycle stop and connect the byte stream to `ModbusRtuClient`. Test the adapter with a fake serial-port boundary before emulator/device acceptance; hardware checks remain required.
- The current upstream `mik3y/usb-serial-for-android` release page lists v3.11.0, and its documented port API uses the same `setParameters` shape. It is a candidate dependency, not yet added; verify its license, repository resolution and timeout/close behavior in BLOODSKY before selecting it. [Upstream release](https://github.com/mik3y/usb-serial-for-android/releases) · [Port API](https://github.com/mik3y/usb-serial-for-android/blob/master/usbSerialForAndroid/src/main/java/com/hoho/android/usbserial/driver/UsbSerialPort.java).


## Current BLOODSKY USB code status — re-audited 20 Oct 2026

The statements above that BLOODSKY has no USB host/serial runtime describe the earlier checkpoint and are superseded by the current prototype source tree. At prototype HEAD `88759f28f39d04e7bacbe7c7155a808453ba8cc0`:

- `UsbSerialConnector` enumerates supported devices using `UsbSerialProber`, opens only the explicitly selected device, requests Android USB permission, configures the chosen UART settings off the UI thread, and owns cancellation/cleanup.
- `UsbSerialSession` bridges the selected port into `ModbusRtuClient`; `UsbSerialModbusConnection` exposes RTU read and write methods and closes on detach/I/O failure.
- `MainActivity` has a manual USB diagnostics screen for discover/connect/disconnect/read. The operator register-write path also dispatches USB-bound mappings to this connection.
- `UsbSerialSessionTest` exercises settings, RTU read/write, timeout, I/O failure, detach-unblock and idempotent close with a fake port; `UsbSerialBindingTest` checks endpoint parsing and limits.
- No physical USB adapter is available. Android UsbManager permission broadcasts, device enumeration, Activity/UI lifecycle, real detach and PLC interoperability have not been accepted on hardware. A full persistent 16-device manager is a separate open requirement.

Accordingly the current status is **code implemented, verification incomplete**; mock-port tests are not hardware acceptance. The source audit above remains valid for Craft behavior and for the earlier BLOODSKY checkpoint, but its old “missing USB” status must not be used for the current prototype.
