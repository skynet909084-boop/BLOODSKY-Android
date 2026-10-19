# BLOODSKY ↔ KaSCADA Craft 1.0.7 — полный аудит паритета

Дата сверки: 2026-10-18  
Целевой APK: KaScada Smart Craft 1.0.7  
Спецификация: `HMI-KaSCADA-v1.4-ot-08.09.19 (1).pdf` (109 страниц)  
Исходники: `reference/craft-reference.zip`, SHA-256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`.

## Правило аудита

Руководство описывает KaSCADA 1.4, а APK — Craft 1.0.7. Одинаковое название или наличие картинки не доказывает одинаковую функцию. Для поведения сверяем таблицы руководства с Java-кодом/Smali, реестром компонентов и реальными проектами. Decompiled Java используется как навигация: в архиве зафиксированы ошибки JADX, поэтому спорные методы требуют проверки Smali. Портируем поведение в BLOODSKY, а не подключаем исходное приложение как рантайм.

Статус `есть частично` означает, что путь есть, но не все настройки/сценарии и acceptance-проверки закрыты. `Не сделано` означает, что функции пока нет в приложении. `Не проверено` означает, что код есть, но нет аппаратного/визуального подтверждения. «Готово» без привязки к конкретному тесту не ставится.

## Сверка по руководству

| Руководство | Функция оригинала | BLOODSKY сейчас | Что ещё сделать для закрытия |
|---|---|---|---|
| 2.1–2.4, стр. 6–12 | Проекты, экраны, редактор и рабочий режим | Создание/загрузка/удаление, JSON-каталог, несколько экранов, базовый редактор и отдельный режим оператора работают | Оригинальный каталог SQLite `Rovki.db`, режимы/настройки проекта, пароль, PDF-документ, таймауты и полное восстановление процесса |
| 2.5–2.9, стр. 12–20 | Настройки дисплея, сеть, устройства, мост, Modbus Slave | Локальный Modbus TCP runtime существует; часть настроек проекта сохраняется | Полный менеджер устройств и до 16 устройств, локальный Slave, Wi-Fi→USB мост, запуск/переподключение и проверка поворота/планшета |
| 3.1–3.2, стр. 21–28 | TCP, RTU-over-TCP, Bluetooth SPP, USB-OTG serial; до 16 device slots | BLOODSKY поддерживает Modbus TCP по IP-сети. RTU CRC/stream helpers не являются USB/Bluetooth транспортом. | Реализовать device manager, Bluetooth SPP и USB serial RTU, Android permissions, reconnect/timeouts и hardware acceptance. Manual указывает RTU-over-TCP, но зафиксированный JADX исходник задаёт MBAP framing; Smali/DEX-проверка ещё нужна. Оригинальный редактор Craft ограничивает USB одной записью. Подробно: `docs/TRANSPORT_CONNECTIVITY_AUDIT_20261019.md` |
| 3.3, стр. 28–30 | Локальный web-сервер для просмотра/управления и скачивания архивов | Полного локального web-сервера нет | Реализовать локальный read/control API, авторизацию и экспорт архива; проверить LAN-only режим. Удалённое управление не входит в продукт |
| 3.4, стр. 30–34 | Групповое редактирование и 13 операций выравнивания/распределения/размера | Есть выбор, clipboard и базовое выравнивание | Multi-select и оставшиеся distribute/normalize операции, перенос связей, точная геометрия и restart acceptance |
| 4, стр. 35–49 | Переключатель, кнопки разных режимов, числовой задатчик, список, линейный задатчик | Craft ID 13: horizontal/vertical integer slider, max limit, persisted value and FC06 write on commit; imported legacy mapping stays disabled. Craft ID 6 mode 0: press/release bit command via FC22 after explicit TCP remap; imported device link remains disabled and native BLOODSKY toggle behavior is preserved. Mode 2: UINT16/Float32 constant write via FC06/FC16 after remap. Mode 1: validated existing-screen picker or external URL, preserving operator visibility. Craft ID 20: 16 UINT16 choices, TCP FC03/FC06, visibility/read-only options. Other controls remain partial | Verify exact colors/geometry and vertical direction; side-by-side colors/geometry for buttons and slider, screen-navigation flow, hardware acceptance |
| 5, стр. 50–53 | Статический и 16-состояний динамический текст | Основные модели/импортированы | Сверить шрифты, рамку, авторазмер, все состояния, границы регистров и поведение на устройстве |
| 6, стр. 54–69 | Цифровые/битовые индикаторы, звонок, шкалы, звук | Часть визуальных моделей; Craft ID 9 импортирует posSound и циклический alarm, а редактор теперь позволяет выбрать один из пяти звуков Craft или пользовательский файл. Активные тревоги имеют отдельные screen/widget keyed players; source-contract tests protect independent start/stop/completion | Завершить каждую настройку/масштаб/битовую семантику и проверить одновременные сигналы, stop/release, звуки и изображения на устройстве |
| 7.1–7.5, печатные стр. 70–75 (PDF стр. 48–53) | Графики, анимация, динамические изображения, архив, фон | Craft ID17 импортирует 16 картинок `pathPic_0..15` из ZIP при однозначном совпадении медиа; существующий BLOODSKY редактор/рендерер поддерживает 16 image states. Исходная привязка остаётся выключенной | Сверить исходный выбор состояния по bit/number и безопасно настроенную привязку; проверить все 16 состояний и рендер на равной плотности. GIF, полноценный архив (запись по фронту, 1–4 канала, ASCII-канал, таблица, CSV и очистка) остаются открыты; удалённая выгрузка не реализуется |
| 8.1, стр. 76–77 | Телефон | Нет функционального phone-компонента | Проверить предназначение по layout/source; реализовать только подтверждённое действие и permission flow |
| 8.2–8.3, стр. 78–83 | Шлюз регистров и битов | Craft ID 19: manual settings and TCP runtime for continuous, rising edge and source-change; 1–99 contiguous words (change exactly 1), FC03/04 read and FC16 holding write. Craft ID 22: TCP FC01/02 source bit reads and FC05/15 coil writes; 1–16 bits (change exactly 1), with the same three modes. Both imported legacy bindings stay off until the operator remaps endpoints. Unit tests cover limits, mode state, packing and failed reads. | Android CI/APK passed for both slices; simulator/hardware acceptance is open. System-register/RTU/Bluetooth transports remain unsupported. Verify runtime stop/reconnect and exception behavior on paired devices |
| 8.4, стр. 84–86 | Входящее SMS: до 16 integer registers, exact text match for bit mode, optional success strobe | Opt-in foreground receiver, runtime permission, encrypted sender allowlist, one-to-sixteen signed 16-bit sequential writes, atomic FC22 bit set, optional FC22 strobe set after successful numeric write, Craft ID 18 import disabled-by-default implemented | Android multipart/PDU device tests, screen diagnostics parity and real SIM/Modbus hardware acceptance remain open |
| 8.5, стр. 87–89 | Исходящее SMS: текст и до 8 регистров, `%1`…`%8` | Event widget reads 1–8 contiguous registers from its selected TCP binding, renders `%n` placeholders or appends values, retains `{value}`, encrypts recipient, and sends multipart SMS | Android SMS-manager/device tests, multi-SIM choice, and exact original error-message parity remain open |
| 8.6, стр. 90 | RTSP видео | `CraftRtspConfig` validates RTSP/RTSPS endpoints and rejects URI user-info. Imported Craft ID 23 now becomes a visible disabled placeholder; legacy fields are preserved and never start a stream | Confirm the original URL/auth fields and implement editor/player lifecycle; verify decoder/codec, network errors and safe credential storage/export on Android |
| 8.7, стр. 91 | Push-уведомления | Исходящий Telegram Bot API есть как опциональная замена/дополнение | Перенести конфигурацию уровня компонента и условия срабатывания; Telegram требует сеть, локальное ядро не должно от него зависеть |
| 8.8, стр. 92 | WebView | Не сделано как Craft-компонент | Реализовать ограниченный widget без file/content access по умолчанию, URL allowlist для управления и отдельный сетевой permission UX |
| 9, стр. 93–101 | Системные регистры и типы/масштабы | Часть переменных и runtime значений есть | Полный перечень имён, типов, единиц, endian, update cadence и persistence по исходнику/Smali; acceptance для каждого адреса |
| 10–11, стр. 93–101 | FBD: RS, triggers, timers, CTU, MOVE, math, sensors, MX/ARRAY, macro | Значительный источник-блок рантайм и редактор есть; MX/ARRAY и часть macro уже подтверждены отдельными тестами | Свести каждый класс из `CRAFT_FBD_SOURCE_MATRIX.md` с точными pin/type/mode/order/state; CTU `+,-,R`, RS `R,S`, BLINK, orientation units, named variables, sensor lifecycle и оригинальные macro child records остаются незакрыты или требуют runtime proof |
| 12, стр. 102–109 | EL-логика, сравнения и арифметика с таблицами истинности | Boolean и типизированные compare/arithmetic пути есть | Сверить каждую таблицу, тип/переполнение/деление на ноль, pin identity, визуальные имена и сканирование; исключить BLOODSKY-only операторы из заявления об оригинальном паритете |

## Сверка декомпиляции и примеров

| Источник | Установлено | Состояние/пробел |
|---|---|---|
| Фабрика `mbCanvas.m(...)` + `deleteView(View)` | Source-backed карта ID 1–28 и прочитанных JSON `other` ключей: [полная матрица factory → import](CRAFT_COMPONENT_ID_FACTORY_MATRIX_20261018.md). Фабрика подтверждает ID 16 = GIF и ID 17 = multi-state image; в `m()` не найден ID 3. | Factory-карта заполнена для этого среза. ID 3 остаётся unresolved; свойства editor/renderer/runtime и side-by-side acceptance требуют отдельной проверки по каждому ID. `deleteView` — отдельный неполный switch. |
| `home.zip` / `home.pro` | 24 компонента, один используемый экран и background asset | Обезличенная CI-фикстура проверяет импорт, структуру экранов, все 24 записи и save/reopen. ZIP-медиа отдельно не включено в эту round-trip проверку; визуальная сверка остаётся открытой |
| `gitH.zip` / `gitH.pro` | 65 компонентов на трёх используемых экранах, 5 FBD узлов, 2 однозначных типизированных ребра и background asset | Обезличенная CI-фикстура проверяет импорт/save/reopen всех 65 записей и 16 объявленных экранов; полный runtime bindings, media ZIP round-trip и визуальная проверка ещё открыты |
| `пож.pro` / `пож.zip` | JSON имеет 124 компонента, объявляет 16 экранов, ссылается на экран 20; ZIP содержит только пустую директорию | Обезличенная CI-фикстура проверяет standalone import/save/reopen на 20 экранах; 5 ID 9 переводятся в alarm, 11 ID 19 остаются выключенными шлюзами. ZIP-перенос картинок/медиа проверить нельзя — их в ZIP нет |
| Decompiled Smali/JADX archive | 11,080 JADX files, 15,193 Apktool files, 110 JADX decompile errors | Java подтверждать Smali, особенно SMS, FBD, device transport и obfuscated factory branches |

## Отдельные пользовательские интеграции

| Функция | Реализовано | Осталось |
|---|---|---|
| Telegram | Bot/chat settings, encrypted secret, Modbus edge event | Компонентные настройки/ответные статусы и установка, сквозной UI test |
| SMS | Исходящее SMS-уведомление reads/templates 1–8 registers; входящий opt-in component has foreground receiver, encrypted sender allowlist, 1–16 signed numeric values, atomic bit and optional strobe writes | Android multipart/PDU and real SIM/Modbus hardware tests; exact vendor event/error UX still partial |
| Home Assistant / Xiaomi | HA discovery + подтверждённые power actions; Xiaomi доступен через установленный пользователем HA integration | WebSocket subscriptions, сущности сверх allowlist, reconnect, сквозная работа с устройством |
| Yandex Smart Home | Device/scenario discovery, запуск выбранного сценария после подтверждения; token encrypted | Native OAuth, прямые capability writes, discovery ошибок и сквозной UI test |
| Голос | Push-to-talk, явный offline on-device/online Android recognition, allowlist навигации и run/stop, подтверждение запуска | Локальный ассистент пока распознаёт команды, но не является LLM; нет локальных именованных сценариев и голосовых вызовов HA/Yandex; offline recognition отсутствует на Android <31/без engine |

Интеграции опциональны. Редактор, локальная логика, сценарии/проект и локальный Modbus доступны без внешних сервисов. Не добавлять платные API без отдельного решения пользователя. Не переносить лицензирование, привязку к устройству и обязательный сервер.

## План закрытия пробелов

| Приоритет | Пакет работ | Критерий закрытия |
|---|---|---|
| P0 | Точная матрица: каждому Craft ID и FBD классу указать source evidence, свойства, runtime и BLOODSKY статус; исправить старые завышенные `implemented` отметки | Матрица factory ID 1–28 и нынешний importer status опубликованы; ID 3 остаётся unresolved. Полный P0 не закрыт: нужны Smali/call-site cross-check, editor/renderer/runtime evidence и тест/снимок/протокол для каждого завершённого ID |
| P1 | Импорт примеров: sanitised fixtures, component-level report, asset basename/path matching и экспорт-импорт без потери unknown fields | Все три переданных проекта проходят import→save→export→reopen; неподдерживаемые элементы видимы и безопасны |
| P2 | Остальные редакторские виджеты и свойства (анимация, архив, RTSP, WebView, телефон, sensors) | Craft ID 8 now maps the original digital-setters palette tab to import/editor/runtime numeric entry, source-mode validation, encrypted operator password storage and explicit TCP remapping. IDs 13/20 retain their own import/editor/operator paths. Register ID 19 and bit ID 22 gateways have explicit TCP-only settings/runtime and unit coverage. Paired UI, mocked-peer boundary and hardware acceptance remain open |
| P3 | Источник-точный FBD/EL: CTU/RS/TIMER modes, pins/types/inversion, macro, named vars, sensors and system registers | Unit + imported-project tests против Smali/source vectors; UI smoke и equal-density screenshot на исходных блоках |
| P4 | Device/protocol stack: device manager, Modbus RTU Bluetooth/USB, local slave, Wi-Fi-to-USB bridge | Mock simulator suite, permissions/reconnect tests и физическое устройство для каждого транспортного family |
| P5 | Local runtime services: local browser control/archive, background lifecycle, alarm/event service | LAN-only test, bounded auth/session, airplane-mode runtime test и lifecycle stress |
| P6 | Внешние сервисы и голосовые сценарии | Отдельные opt-in, отсутствие зависимости core, подтверждённые control actions, airplane-mode и отсутствие credential leakage |
| P7 | Финальный приёмочный прогон | Python + Android JUnit + APK + UI emulator + import fixtures + paired original screenshots; main не менять и не сливать без принятого review |

## Текущее решение по следующему пакету

P0 source audit is in progress: the exact factory-to-class/property key slice for IDs 1–28 is now recorded in `CRAFT_COMPONENT_ID_FACTORY_MATRIX_20261018.md`; ID 3 is explicitly unresolved. Incoming and outgoing SMS now cover the manual's 1–16 incoming values, optional strobe, and 1–8 outgoing templates. Imported ID 18 remains disabled pending explicit operator configuration. Craft ID 27 is confirmed as a voice-recognition control with sixteen `textVoice0`–`textVoice15` fields, not a TTS announcement. Import preserves metadata, renders a disabled placeholder, and lets the operator edit all sixteen source phrase fields without enabling execution; the original recognition interaction remains unported. Exact original phrase-field mapping, local transports and FBD parity remain open. No imported command executes automatically.

## Проверки этого аудита

- Руководство: `pdftotext -layout` + визуальные листы страниц; ключевые разделы 2–12 сверены с текстом и иллюстрациями.
- APK: код/ресурсы из pinned `craft-reference.zip`; архивный checksum проверяется `tools/prepare_craft_resources.py`.
- Примеры: локально предоставленные `.pro` и ZIP; состав отмечен выше.
- Full Android 11 UI smoke: Actions run [#531](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39153192414) passed on audit commit `59c8868aded8e79328c678e1998e336ea6fade5b`; APK/unit/archive checks passed in [#870](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39153192458). The preceding full smoke [#530](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/39152105436) passed on UI fix `c1f46d8f83e643e2a9659712fb853d7e7964966a`. The smoke captured the migrated Craft PNG and alarm widget, then verified the PNG remained visible after process restart. This is an editor/media regression check, not a side-by-side visual acceptance of every Craft ID.
- Этот файл не утверждает pixel parity, аппаратную поддержку или завершение всей KaSCADA-функциональности.

## Обновление импортёра изображения Craft ID17 — 2026-10-19

- На ветку PR #143 перенесён проверенный commit `54830d6` из дочерней ветки PR #157. Он сохраняет исходные поля, сопоставляет 16 image paths с ZIP-медиа только при однозначном совпадении и оставляет неизвестную связь устройства выключенной.
- Руководство §7.3 (PDF стр. 49–50) показывает 16 image-path полей и управление по биту либо числу. По `CustomComponents/h.java` Craft `typeCommand=0` выбирает номер изображения 0–15; ненулевое значение выбирает картинку по первой установленной битовой позиции, а нулевая маска или неверный числовой индекс скрывает изображение. Этот выбор перенесён в `CraftMultiStateImage` и покрыт тестами; текущий экранный рендерер применяет выбранный путь.
- Craft ID17 unit tests cover present, missing, and ambiguous ZIP media plus numeric/bit selection. On PR #143 head `f222d6c`, APK/JUnit #1102 passes; UI smoke #735 and Craft probe #132 are running. Explicitly configured device binding and same-density visual/device acceptance remain open; imported legacy bindings remain disabled.
