# Craft component factory matrix — ID 1–28

Дата проверки: 2026-10-18. Это сверка исходной фабрики виджетов Craft с текущим BLOODSKY import path, а не заявление о полном функциональном или визуальном паритете.

## Источник и метод

- Источник: workflow artifact `BLOODSKY-Kaskada-decompiled`, run [38600957172](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38600957172), artifact [11912849123](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38600957172/artifacts/11912849123), SHA-256 `d78bd703d6096ac12d69e19f8b5b5dfa9a4c25e342ce9af0ef8b42326aaa3f8c`.
- Архив распакован и проверен локально в этой сессии. Фабрика: `jadx/sources/ru/himik/nightscream/kascada/mbCanvas.java`, метод `m(int, ...)`: ID 1 в ветке `if (i2 == 1)` (строка 4842), ID 2 в `if (i2 == 2)` (4897), ID 4–28 в `switch (i2)` (4941–6427).
- Для каждой ветки выписаны создаваемый класс и ключи JSON `other`, которые фабрика читает. Общие поля геометрии/текста/color и connection JSON находятся в отдельных колонках таблицы `pro_<id>_component`; `other` — JSON свойств конкретного виджета.
- JADX сообщает о 110 ошибках декомпиляции в архиве. Неоднозначные/повреждённые места требуют проверки Smali. Отсутствие ветки в этом методе не трактуется как доказательство отсутствия ID во всём приложении.

## Полная сверка factory → импорт BLOODSKY

Статус импорта описывает только то, что делает текущий `CraftProjectImporter.convertComponent()` и runtime configuration; слово «поддерживается» здесь не означает pixel parity, аппаратную приёмку или портирование оригинальной реализации.

| Craft ID | Класс, создаваемый фабрикой | Ключи `other`, читаемые фабрикой | Текущий импорт BLOODSKY |
|---:|---|---|---|
| 1 | `CustomComponents.y` | `visibleInWork, numScreen, imagePosition, timeZ` | Виджет «Индикатор»; объект `other` сохраняется. Сверить исходную семантику image-position и runtime. |
| 2 | `CustomLabel` | `numScreen, autoResizeText, enableFrameLabel, timeZ` | Текст с auto-resize/frame. Нужны точные шрифт, рамка, размеры и скриншотное сравнение. |
| 3 | Ветка не найдена в `m()`; класс/назначение не установлены | — | Общий fallback сохраняет исходный ID и данные как неизвестный/отключённый компонент. Исследовать call sites, registry и Smali; не назначать класс по догадке. |
| 4 | `CustomComponents.u` | `numScreen, digit, divisor, type, typeInt, enableFrameRegister, fontChange, timeZ` | Импортируется как «Текст»; точная числовая/регистровая семантика и оформление отдельно не подтверждены. |
| 5 | `CustomComponents.q` | `numScreen, imagePosition, pathOrNameOff, pathOrNameOn, isImageUserOff, isImageUserOn, timeZ` | «Индикатор»; `other` сохраняется. Проверить media path, состояния и цвета на исходном проекте. |
| 6 | `CustomComponents.e` | `numScreen, mode, changeScreen, visibleInWork, autoResizeText, imagePosition, typeRegister, constantRegister, constant, timeZ` | Все три режима разобраны: моментальная битовая кнопка, переход/URL, константная запись. Импортированные сетевые связи выключены до явного TCP remap. UI и аппаратное поведение ещё не приняты. |
| 7 | `CustomDynText` | `numScreen, text0…text15, colorBackground0…colorBackground15, colorText, sizeText, autoResizeText, enableFrameDynText, typeCommand, timeZ` | Динамический текст и 16 состояний импортируются. Осталось визуальное/регистровое side-by-side сравнение. |
| 8 | `CustomComponents.i` | `numScreen, min, max, type, typeInt, countNumAfterPointEditNumber, enableFrameEditNumber, password, fontChange, timeZ` | Не имеет отдельной ветки типа в importer; попадает в видимый отключённый fallback. Numeric-entry properties/editor/runtime не перенесены. |
| 9 | `CustomComponents.c` | `numScreen, posSound, timeZ` | Импорт звукового сигнала и 5 пресетов; отдельные alarm players. Нужна проверка на устройстве и точная проверка исходной trigger semantics/UI. |
| 10 | `CustomComponents.w` | `numScreen, phone, message, option, optionRegister, optionCountRegister, timeZ` | Не имеет собственного importer mapping; fallback видим и не исполняется. Исходящий SMS реализован как отдельное BLOODSKY событие, но полное соответствие именно ID 10 не доказано. |
| 11 | `CustomComponents.r` | `numScreen, showText, sizeText, colorText, showScale, colorProgress, colorBackground, max, min, timeZ` | Импорт как progress bar, диапазон min/max сохраняется. Точные шкала, цвет и поведение требуют визуальной/устройственной проверки. |
| 12 | `CustomComponents.g` | `numScreen, showText, sizeText, colorText, max, inColor, outColor, centerColor, firstColorPoint, secondColorPoint, timeZ` | Нет отдельного mapping; общий disabled fallback. Renderer, properties и runtime не перенесены. |
| 13 | `CustomComponents.v` | `numScreen, colorText, max, colorProgress, colorBackground, isTurn, timeZ` | Linear setter импортируется; horizontal/vertical свойства и max сохраняются, запись требует явной TCP-настройки. Side-by-side и hardware acceptance открыты. |
| 14 | `CustomComponents.o` | `numScreen, secondPeriod, countPoint, colorBackground, typeGraph, nameSeries1…nameSeries4, countGraphChannel, timeZ` | Нет отдельного mapping; disabled fallback. График Craft и его настройки не портированы. |
| 15 | `CustomComponents.d` | `numScreen, typeRecord, registerArchive, optionChannel, typeRecordOptionChannel, countArchiveOptionalChannelASCII, fileName, regArchiveOptionalChannel, countChannel, trigger/reset/email/cloud fields, timeZ` | Нет отдельного component mapping. Не считать реализованным общим экспортом/архивом; архивный runtime не портирован. |
| 16 | `CustomComponents.n` | `numScreen, pathGif, timeZ` | Фабрика подтверждает GIF-компонент; importer отдельного mapping не имеет, использует disabled fallback. GIF/media loading отсутствует. |
| 17 | `CustomComponents.h` | `numScreen, pathPic_0…pathPic_15, typeCommand, regTouchX, regTouchY, timeZ` | Фабрика подтверждает 16-состояний изображение; importer отдельного mapping не имеет, использует disabled fallback. Переходы/изображения не перенесены. |
| 18 | `CustomComponents.x` | `numScreen, phone, command, typeCommand, strob, regStrob, bitStrob, timeZ` | Видимый incoming-SMS placeholder, выключен при импорте; включение требует явной проверки и настройки оператора. Multipart/PDU и реальная SIM ещё не приняты. |
| 19 | `CustomComponents.k` | `numScreen, mode, regMode, bitMode, typeRequest_source, countRegister_source, addressRegister_source, addressRegister_receive, posDeviceNetworkInList_source/receive/trigger, timeZ` | Register gateway import/editor/TCP runtime; legacy endpoints выключены, требуются новые явные TCP bindings. Проверить source-side fidelity, exception/stop/reconnect и hardware. |
| 20 | `CustomList` | `numScreen, textBit0…textBit15, visibleInWork, checkNoChange, timeZ` | 16 пунктов/маска импортируются; legacy binding выключен до remap. Визуальное сравнение списка и hardware ещё открыто. |
| 21 | `CustomSound` | `numScreen, pathSound_0…pathSound_7, timeZ` | Отдельного mapping нет; disabled fallback. Проверить различие между ID 21 sound asset и ID 9 alarm прежде чем считать alarm UI покрытием. |
| 22 | `CustomComponents.l` | `numScreen, mode, regMode, bitMode, countBit, functionModbusSource, source/receive/trigger connection fields, timeZ` | Bit gateway import/editor/TCP runtime; legacy endpoints выключены до явных TCP bindings. Проверить hardware и stop/reconnect. |
| 23 | `CustomComponents.f` | `numScreen, rtsp_url, timeZ` | Видимый RTSP placeholder, поток отключён; config validator есть, но player/editor/lifecycle нет. |
| 24 | `CustomComponents.m` | `numScreen, minGauge, maxGauge, countTextScale, percentLowScale, indicatorName, colorCenterCircle, colorIndicator, colorMark, colorTextScale, colorTextValue, sizeText, hideBackground, timeZ` | Нет отдельного mapping; disabled fallback. Gauge UI/runtime не перенесены. |
| 25 | `CustomComponents.t` | `numScreen, title, body, to, timeZ` | Нет отдельного mapping; disabled fallback. Проверить, что это email/event component, и какие действия оригинал выполняет. |
| 26 | `CustomComponents.b0` | `numScreen, link0…link15, timeZ` | Нет отдельного mapping; disabled fallback. Screen-link picker/flow для ID 26 не подтверждён. |
| 27 | `CustomComponents.a0` | `numScreen, textVoice0…textVoice15, timeZ` | На source string/resource это распознавание речи; импорт сохраняет 16 фраз и показывает disabled placeholder. Не смешивать с TTS/объявлением; распознавание Craft не портировано. |
| 28 | `CustomComponents.j` | `numScreen`, title/body/address/bit/type, MQTT, email, sensor, timer, variable and macro fields, `timeZ` | Не обычный виджет; это смешанная группа/узел интеграций и данных, видимая с ID 28. Полный смысл каждого поля требует отдельного разбора Smali/call sites; сейчас общий disabled fallback, если не классифицирован отдельным FBD adapter. |

## Выводы и исправления к прежней таблице

1. Неполный `deleteView(View)` switch не является источником полной карты. Он не содержал 16/17, однако фабрика `m()` подтверждает ID 16 = GIF и ID 17 = multi-state image.
2. В фабрике `m()` не найден ID 3. Это зафиксировано как unresolved, а не как «ID 3 не существует».
3. Реестр класса `CustomComponents.a0` для ID 27 должен называться recognition/распознавание по исходным строкам `custom_voice_recognition` и `textVoice0…15`; это не доказательство TTS.
4. По текущему importer отдельные точные визуальные/поведенческие mapping есть только у части ID. Неизвестные ID сохраняются fail-closed, но это совместимость/сохранение данных, а не паритет.
5. Для полного аудита остаются source/Smali cross-reference, per-ID свойства редактора и runtime, визуальное сравнение на равной плотности и устройство/транспортные acceptance.

