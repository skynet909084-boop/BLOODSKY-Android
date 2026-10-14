package com.bloodsky.hmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.DataOutputStream;

public class MainActivity extends Activity {
    private static final String PREFS = "bloodsky_projects";
    private static final String ACTIVE_PROJECT = "active_project";
    private static final int REQUEST_EXPORT_PROJECT = 7101;
    private static final int REQUEST_IMPORT_PROJECT = 7102;
    private String projectName = "Мой проект";
    private LinearLayout root;
    private EditorCanvas editor;
    private final List<Widget> widgets = new ArrayList<>();
    private final List<Screen> screens = new ArrayList<>();
    private int currentScreenIndex = 0;
    private Widget clipboardWidget;
    private boolean fbdWireMode = false;
    private int wireSource = -1;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService modbusExecutor = Executors.newSingleThreadExecutor();
    private final Map<Integer, String> liveValues = new HashMap<>();
    private boolean modbusPollingActive = false;
    private volatile boolean modbusPollInFlight = false;
    private final Runnable modbusPollRunnable = new Runnable() {
        @Override public void run() {
            if (!modbusPollingActive || editor == null) return;
            pollBoundWidgets();
            mainHandler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        showHome();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView heading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(22);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(18), dp(12), dp(12), dp(12));
        view.setBackgroundColor(Color.rgb(16, 36, 58));
        return view;
    }

    private Button action(String text, Runnable task) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(v -> task.run());
        return button;
    }

    private void showHome() {
        stopModbusPolling();
        editor = null;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(241, 245, 249));
        root.addView(heading("BLOODSKY  •  HMI / SCADA"),
                new LinearLayout.LayoutParams(-1, dp(64)));

        TextView intro = new TextView(this);
        intro.setText("Android-редактор экранов\nОснова собственного проекта на базе анализа Kaskada Craft 1.0.7");
        intro.setTextSize(16);
        intro.setTextColor(Color.rgb(31, 41, 55));
        intro.setPadding(dp(20), dp(22), dp(20), dp(18));
        root.addView(intro);

        root.addView(action("Создать / открыть проект", () -> {
            loadProject();
            showEditor();
        }), new LinearLayout.LayoutParams(-1, dp(58)));

        root.addView(action("Имя проекта", () -> editProjectName()),
                new LinearLayout.LayoutParams(-1, dp(54)));
        root.addView(action("Экспорт проекта (JSON)", this::exportProject),
                new LinearLayout.LayoutParams(-1, dp(54)));
        root.addView(action("Импорт проекта (JSON)", this::importProject),
                new LinearLayout.LayoutParams(-1, dp(54)));
        root.addView(action("Modbus Scanner (TCP)", this::showModbusScanner),
                new LinearLayout.LayoutParams(-1, dp(54)));

        TextView status = new TextView(this);
        status.setText("\nТекущий этап: базовый Android-каркас и редактор экранов.\nFBD, Modbus RTU/TCP и полный перенос оригинальных компонентов будут добавляться отдельными проверяемыми этапами.");
        status.setTextColor(Color.rgb(71, 85, 105));
        status.setTextSize(14);
        status.setPadding(dp(20), dp(12), dp(20), dp(20));
        root.addView(status);
        setContentView(root);
    }

    private void showModbusScanner() {
        stopModbusPolling();
        editor = null;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(241, 245, 249));
        root.addView(heading("BLOODSKY • Modbus TCP Scanner"), new LinearLayout.LayoutParams(-1, dp(60)));
        root.addView(action("‹ Назад", this::showHome), new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText hostField = propertyField("192.168.1.10", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        hostField.setHint("IP-адрес устройства");
        form.addView(hostField);
        EditText portField = propertyField("502", InputType.TYPE_CLASS_NUMBER);
        portField.setHint("TCP-порт (обычно 502)");
        form.addView(portField);
        EditText unitField = propertyField("1", InputType.TYPE_CLASS_NUMBER);
        unitField.setHint("Unit ID (0–255)");
        form.addView(unitField);
        EditText addressField = propertyField("40001", InputType.TYPE_CLASS_NUMBER);
        addressField.setHint("Первый holding register (например 40001)");
        form.addView(addressField);
        EditText countField = propertyField("10", InputType.TYPE_CLASS_NUMBER);
        countField.setHint("Количество регистров (1–125)");
        form.addView(countField);

        TextView result = new TextView(this);
        result.setTextColor(Color.rgb(31, 41, 55));
        result.setTextSize(14);
        result.setPadding(dp(16), dp(10), dp(16), dp(10));
        result.setText("Готов к чтению holding registers (FC03). Сканирование выполняется по сети; устройство должно быть доступно.");
        root.addView(form, new LinearLayout.LayoutParams(-1, -2));
        root.addView(action("Считать регистры", () -> {
            final String host = hostField.getText().toString().trim();
            final int port, unit, startAddress, count;
            try {
                port = Integer.parseInt(portField.getText().toString().trim());
                unit = Integer.parseInt(unitField.getText().toString().trim());
                int enteredAddress = Integer.parseInt(addressField.getText().toString().trim());
                count = Integer.parseInt(countField.getText().toString().trim());
                if (port < 1 || port > 65535 || unit < 0 || unit > 255 || count < 1 || count > 125
                        || enteredAddress < 1 || enteredAddress > 49999) throw new IllegalArgumentException();
                startAddress = enteredAddress >= 40001 ? enteredAddress - 40001 : enteredAddress;
                if (startAddress < 0 || startAddress > 65535 || startAddress + count > 65536) throw new IllegalArgumentException();
                if (host.isEmpty()) throw new IllegalArgumentException();
            } catch (Exception ex) {
                result.setText("Проверь IP, порт, Unit ID, адрес и количество регистров.");
                return;
            }
            result.setText("Подключаюсь к " + host + ":" + port + "…");
            new Thread(() -> {
                String report;
                try {
                    report = readModbusHoldingRegisters(host, port, unit, startAddress, count);
                } catch (Exception ex) {
                    report = "Ошибка Modbus TCP: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                }
                final String finalReport = report;
                runOnUiThread(() -> result.setText(finalReport));
            }, "bloodsky-modbus-scan").start();
        }), new LinearLayout.LayoutParams(-1, dp(52)));
        root.addView(result, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private String readModbusHoldingRegisters(String host, int port, int unit, int startAddress, int count) throws Exception {
        int transaction = (int) (SystemClock.elapsedRealtime() & 0xffff);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            out.writeShort(transaction);
            out.writeShort(0); // Modbus protocol identifier
            out.writeShort(6); // Unit ID + function + address + quantity
            out.writeByte(unit);
            out.writeByte(3); // Read holding registers
            out.writeShort(startAddress);
            out.writeShort(count);
            out.flush();

            byte[] header = new byte[7];
            in.readFully(header);
            int responseTransaction = ((header[0] & 0xff) << 8) | (header[1] & 0xff);
            int protocol = ((header[2] & 0xff) << 8) | (header[3] & 0xff);
            int length = ((header[4] & 0xff) << 8) | (header[5] & 0xff);
            int responseUnit = header[6] & 0xff;
            if (responseTransaction != transaction || protocol != 0 || responseUnit != unit || length < 2 || length > 254) {
                throw new IOException("Некорректный заголовок ответа");
            }
            byte[] pdu = new byte[length - 1];
            in.readFully(pdu);
            int function = pdu[0] & 0xff;
            if (function == 0x83 && pdu.length >= 2) {
                throw new IOException("Код исключения Modbus: " + (pdu[1] & 0xff));
            }
            if (function != 3 || pdu.length < 2) throw new IOException("Неожиданный код функции");
            int byteCount = pdu[1] & 0xff;
            if (byteCount != count * 2 || pdu.length < 2 + byteCount) throw new IOException("Размер ответа не совпадает с запросом");
            StringBuilder report = new StringBuilder();
            report.append("Успешно: ").append(count).append(" holding registers\n");
            for (int i = 0; i < count; i++) {
                int value = ((pdu[2 + i * 2] & 0xff) << 8) | (pdu[3 + i * 2] & 0xff);
                report.append(40001 + startAddress + i).append("  |  ")
                        .append(value).append("  |  0x")
                        .append(String.format(java.util.Locale.ROOT, "%04X", value)).append('\n');
            }
            return report.toString();
        }
    }

    private void exportProject() {
        persistProject();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, projectName.replaceAll("[^A-Za-zА-Яа-я0-9._-]", "_") + ".bloodsky.json");
        try {
            startActivityForResult(intent, REQUEST_EXPORT_PROJECT);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть выбор файла", Toast.LENGTH_LONG).show();
        }
    }

    private void importProject() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_PROJECT);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть выбор файла", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_PROJECT) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Output stream unavailable");
                String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
                out.write(json.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "JSON проекта экспортирован", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Ошибка экспорта проекта", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_IMPORT_PROJECT) {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                if (in == null) throw new IllegalStateException("Input stream unavailable");
                byte[] chunk = new byte[8192];
                int count;
                while ((count = in.read(chunk)) != -1) {
                    if (buffer.size() + count > 8 * 1024 * 1024) throw new IllegalArgumentException("Project file too large");
                    buffer.write(chunk, 0, count);
                }
                String json = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                validateImportedProject(json);
                // Commit only after the complete document has passed validation.
                if (!getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(ACTIVE_PROJECT, json).commit()) throw new IllegalStateException("Could not save imported project");
                loadProject();
                showEditor();
                Toast.makeText(this, "Проект JSON импортирован", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Импорт не выполнен: файл не является допустимым JSON-проектом", Toast.LENGTH_LONG).show();
            }
        }
    }


    private void validateImportedProject(String json) throws Exception {
        org.json.JSONTokener tokener = new org.json.JSONTokener(json);
        Object parsed = tokener.nextValue();
        if (tokener.nextClean() != 0) throw new IllegalArgumentException("Trailing content after JSON");
        if (parsed instanceof JSONArray) {
            validateWidgetArray((JSONArray) parsed);
            return;
        }
        if (!(parsed instanceof JSONObject)) throw new IllegalArgumentException("Project must be a JSON object or array");
        JSONObject project = (JSONObject) parsed;
        JSONArray screenArray = project.optJSONArray("screens");
        if (screenArray == null || screenArray.length() == 0 || screenArray.length() > 100) throw new IllegalArgumentException("Invalid screen list");
        int totalWidgets = 0;
        for (int i = 0; i < screenArray.length(); i++) {
            JSONObject screen = screenArray.optJSONObject(i);
            if (screen == null) throw new IllegalArgumentException("Screen must be an object");
            JSONArray screenWidgets = screen.optJSONArray("widgets");
            if (screenWidgets == null) throw new IllegalArgumentException("Screen widgets must be an array");
            validateWidgetArray(screenWidgets);
            totalWidgets += screenWidgets.length();
            if (totalWidgets > 2000) throw new IllegalArgumentException("Too many widgets");
            JSONArray connections = screen.optJSONArray("connections");
            if (connections != null) {
                if (connections.length() > 4000) throw new IllegalArgumentException("Too many connections");
                for (int j = 0; j < connections.length(); j++) {
                    JSONObject edge = connections.optJSONObject(j);
                    if (edge == null) throw new IllegalArgumentException("Connection must be an object");
                    int source = edge.optInt("source", -1), target = edge.optInt("target", -1);
                    if (source < 0 || target < 0 || source >= screenWidgets.length() || target >= screenWidgets.length() || source == target) throw new IllegalArgumentException("Connection index out of range");
                    JSONObject a = screenWidgets.optJSONObject(source), b = screenWidgets.optJSONObject(target);
                    if (a == null || b == null || !a.optString("type", "").startsWith("FBD ") || !b.optString("type", "").startsWith("FBD ")) throw new IllegalArgumentException("Connections must link FBD blocks");
                }
            }
        }
    }

    private void validateWidgetArray(JSONArray array) {
        if (array.length() > 2000) throw new IllegalArgumentException("Too many widgets");
        for (int i = 0; i < array.length(); i++) {
            JSONObject widget = array.optJSONObject(i);
            if (widget == null) throw new IllegalArgumentException("Widget must be an object");
            String type = widget.optString("type", "Текст"), label = widget.optString("text", "Надпись");
            if (type.length() > 100 || label.length() > 2000 || widget.optString("connectionInfo", "").length() > 2000) throw new IllegalArgumentException("Widget field too long");
            int x = widget.optInt("x", 0), y = widget.optInt("y", 0), w = widget.optInt("w", 160), h = widget.optInt("h", 58);
            if (x < -100000 || x > 100000 || y < -100000 || y > 100000 || w < 1 || w > 100000 || h < 1 || h > 100000) throw new IllegalArgumentException("Widget geometry out of range");
        }
    }

    private void editProjectName() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(projectName);
        new AlertDialog.Builder(this)
                .setTitle("Имя проекта")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) projectName = value;
                    persistProject();
                }).show();
    }

    private void showEditor() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(Color.rgb(16, 36, 58));
        Button back = action("‹ Проекты", this::showHome);
        Button rename = action(projectName, this::editProjectName);
        Button save = action("Сохранить", () -> {
            persistProject();
            Toast.makeText(this, "Проект сохранён на устройстве", Toast.LENGTH_SHORT).show();
        });
        top.addView(back);
        top.addView(rename, new LinearLayout.LayoutParams(0, dp(48), 1));
        top.addView(save);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout screenBar = new LinearLayout(this);
        screenBar.setGravity(Gravity.CENTER_VERTICAL);
        screenBar.setBackgroundColor(Color.rgb(235, 241, 247));
        screenBar.addView(action("‹", this::previousScreen), new LinearLayout.LayoutParams(dp(48), dp(42)));
        screenBar.addView(action(currentScreenName(), this::renameCurrentScreen), new LinearLayout.LayoutParams(0, dp(42), 1));
        screenBar.addView(action("›", this::nextScreen), new LinearLayout.LayoutParams(dp(48), dp(42)));
        screenBar.addView(action("+ Экран", this::addScreen), new LinearLayout.LayoutParams(-2, dp(42)));
        screenBar.addView(action("−", this::removeCurrentScreen), new LinearLayout.LayoutParams(dp(48), dp(42)));
        root.addView(screenBar);

        LinearLayout palette = new LinearLayout(this);
        palette.setOrientation(LinearLayout.HORIZONTAL);
        palette.setBackgroundColor(Color.rgb(226, 232, 240));
        palette.addView(action("+ Текст", () -> addWidget("Текст", "Надпись")),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        palette.addView(action("+ Кнопка", () -> addWidget("Кнопка", "Кнопка")),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        palette.addView(action("+ Индикатор", () -> addWidget("Индикатор", "Индикатор")),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        palette.addView(action("+ Задатчик", () -> addWidget("Задатчик", "Задатчик: 0")),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(palette);

        LinearLayout fbdPalette = new LinearLayout(this);
        fbdPalette.setOrientation(LinearLayout.HORIZONTAL);
        fbdPalette.setBackgroundColor(Color.rgb(237, 233, 254));
        fbdPalette.addView(action("+ Вход", () -> addWidget("FBD Вход", "Вход")), new LinearLayout.LayoutParams(0, dp(44), 1));
        fbdPalette.addView(action("+ И", () -> addWidget("FBD И", "И")), new LinearLayout.LayoutParams(0, dp(44), 1));
        fbdPalette.addView(action("+ ИЛИ", () -> addWidget("FBD ИЛИ", "ИЛИ")), new LinearLayout.LayoutParams(0, dp(44), 1));
        fbdPalette.addView(action("+ НЕ", () -> addWidget("FBD НЕ", "НЕ")), new LinearLayout.LayoutParams(0, dp(44), 1));
        fbdPalette.addView(action("+ Таймер", () -> addWidget("FBD Таймер", "TON")), new LinearLayout.LayoutParams(0, dp(44), 1));
        fbdPalette.addView(action("+ Сравнение", () -> addWidget("FBD Сравнение", "A > B")), new LinearLayout.LayoutParams(0, dp(44), 1));
        root.addView(fbdPalette);
        root.addView(action("Связать FBD", () -> {
            fbdWireMode = !fbdWireMode;
            wireSource = -1;
            Toast.makeText(this, fbdWireMode ? "Режим связи: выбери исходный FBD-блок, затем целевой" : "Режим связи выключен", Toast.LENGTH_LONG).show();
            if (editor != null) editor.invalidate();
        }), new LinearLayout.LayoutParams(-1, dp(42)));

        editor = new EditorCanvas();
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setBackgroundColor(Color.rgb(241, 245, 249));
        bottom.addView(action("Свойства", this::editSelectedWidget),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        bottom.addView(action("Копировать", this::copySelectedWidget),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        bottom.addView(action("Вставить", this::pasteWidget),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        bottom.addView(action("Удалить", this::deleteSelectedWidget),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(bottom);
        setContentView(root);
        editor.invalidate();
        startModbusPolling();
    }

    private void startModbusPolling() {
        stopModbusPolling();
        modbusPollingActive = true;
        mainHandler.postDelayed(modbusPollRunnable, 1000L);
    }

    private void stopModbusPolling() {
        modbusPollingActive = false;
        mainHandler.removeCallbacks(modbusPollRunnable);
    }

    private void pollBoundWidgets() {
        final List<Widget> snapshotWidgets = new ArrayList<>(widgets);
        final List<BindingRequest> requests = new ArrayList<>();
        for (int i = 0; i < snapshotWidgets.size(); i++) {
            Widget widget = snapshotWidgets.get(i);
            BindingRequest request = parseBinding(i, widget.connectionInfo);
            if (request != null) requests.add(request);
        }
        if (requests.isEmpty() || modbusPollInFlight) return;
        modbusPollInFlight = true;
        modbusExecutor.execute(() -> {
            try {
                final Map<Integer, String> updates = new HashMap<>();
                for (BindingRequest request : requests) {
                    try {
                        String report = readModbusHoldingRegisters(request.host, request.port, request.unit, request.address, 1);
                        String[] lines = report.split("\\n");
                        if (lines.length < 2) throw new IOException("Пустой ответ регистра");
                        String[] columns = lines[1].split("\\|");
                        if (columns.length < 2) throw new IOException("Не удалось разобрать регистр");
                        updates.put(request.widgetIndex, columns[1].trim());
                    } catch (Exception e) {
                        updates.put(request.widgetIndex, "ERR");
                    }
                }
                runOnUiThread(() -> {
                    if (editor == null) return;
                    for (Map.Entry<Integer, String> entry : updates.entrySet()) {
                        int index = entry.getKey();
                        if (index < 0 || index >= widgets.size() || index >= snapshotWidgets.size()
                                || widgets.get(index) != snapshotWidgets.get(index)) continue;
                        liveValues.put(index, entry.getValue());
                        if ("FBD Вход".equals(widgets.get(index).type)) {
                            widgets.get(index).inputValue = !"0".equals(entry.getValue()) && !"ERR".equals(entry.getValue());
                        }
                    }
                    editor.invalidate();
                });
            } finally {
                modbusPollInFlight = false;
            }
        });
    }

    private BindingRequest parseBinding(int widgetIndex, String binding) {
        if (binding == null || !binding.startsWith("tcp://")) return null;
        try {
            URI uri = URI.create(binding);
            String[] path = uri.getPath().split("/");
            if (uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535 || path.length < 3) return null;
            int unit = Integer.parseInt(path[1]);
            int enteredAddress = Integer.parseInt(path[2]);
            if (unit < 0 || unit > 255 || enteredAddress < 0 || enteredAddress > 65535) return null;
            int address = enteredAddress >= 40001 && enteredAddress <= 49999 ? enteredAddress - 40001 : enteredAddress;
            return new BindingRequest(widgetIndex, uri.getHost(), uri.getPort(), unit, address);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static class BindingRequest {
        final int widgetIndex, port, unit, address;
        final String host;
        BindingRequest(int widgetIndex, String host, int port, int unit, int address) {
            this.widgetIndex = widgetIndex; this.host = host; this.port = port; this.unit = unit; this.address = address;
        }
    }

    private String currentScreenName() {
        if (screens.isEmpty()) return "Экран 1";
        currentScreenIndex = Math.max(0, Math.min(currentScreenIndex, screens.size() - 1));
        return screens.get(currentScreenIndex).name;
    }

    private void saveActiveScreen() {
        if (screens.isEmpty()) screens.add(new Screen("Экран 1"));
        Screen screen = screens.get(Math.max(0, Math.min(currentScreenIndex, screens.size() - 1)));
        screen.widgets.clear();
        for (Widget source : widgets) screen.widgets.add(copyWidget(source));
    }

    private Widget copyWidget(Widget source) {
        Widget copy = new Widget();
        copy.type = source.type; copy.text = source.text; copy.connectionInfo = source.connectionInfo; copy.inputValue = source.inputValue; copy.timerPresetMs = source.timerPresetMs; copy.timerStartedAt = source.timerStartedAt; copy.compareA = source.compareA; copy.compareB = source.compareB; copy.compareOperator = source.compareOperator;
        copy.x = source.x; copy.y = source.y; copy.w = source.w; copy.h = source.h;
        return copy;
    }

    private void activateScreen(int index) {
        saveActiveScreen();
        if (screens.isEmpty()) screens.add(new Screen("Экран 1"));
        currentScreenIndex = Math.max(0, Math.min(index, screens.size() - 1));
        liveValues.clear();
        widgets.clear();
        for (Widget source : screens.get(currentScreenIndex).widgets) widgets.add(copyWidget(source));
        persistProject();
        showEditor();
    }

    private void previousScreen() {
        if (screens.size() < 2) { Toast.makeText(this, "Добавь ещё один экран", Toast.LENGTH_SHORT).show(); return; }
        activateScreen((currentScreenIndex - 1 + screens.size()) % screens.size());
    }

    private void nextScreen() {
        if (screens.size() < 2) { Toast.makeText(this, "Добавь ещё один экран", Toast.LENGTH_SHORT).show(); return; }
        activateScreen((currentScreenIndex + 1) % screens.size());
    }

    private void addScreen() {
        saveActiveScreen();
        screens.add(new Screen("Экран " + (screens.size() + 1)));
        currentScreenIndex = screens.size() - 1;
        widgets.clear();
        persistProject();
        showEditor();
    }

    private void removeCurrentScreen() {
        if (screens.size() <= 1) {
            Toast.makeText(this, "В проекте должен остаться хотя бы один экран", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Удалить экран?")
                .setMessage("Экран «" + currentScreenName() + "» и все его элементы будут удалены.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    saveActiveScreen();
                    screens.remove(currentScreenIndex);
                    currentScreenIndex = Math.max(0, Math.min(currentScreenIndex, screens.size() - 1));
                    widgets.clear();
                    for (Widget widget : screens.get(currentScreenIndex).widgets) widgets.add(copyWidget(widget));
                    editor = null;
                    persistProject();
                    showEditor();
                }).show();
    }

    private void renameCurrentScreen() {
        EditText input = propertyField(currentScreenName(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AlertDialog.Builder(this).setTitle("Имя экрана").setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) screens.get(currentScreenIndex).name = name;
                    persistProject(); showEditor();
                }).show();
    }

    private void addWidget(String type, String text) {
        Widget w = new Widget();
        w.type = type;
        w.text = text;
        w.x = dp(40) + (widgets.size() % 4) * dp(24);
        w.y = dp(50) + (widgets.size() % 6) * dp(22);
        w.w = type.equals("Индикатор") ? dp(140) : dp(160);
        w.h = type.equals("Индикатор") ? dp(54) : dp(58);
        liveValues.clear();
        if (type.equals("Задатчик")) { w.w = dp(190); w.h = dp(62); }
        if (type.startsWith("FBD ")) { w.w = dp(150); w.h = dp(76); }
        widgets.add(w);
        persistProject();
        if (editor != null) editor.invalidate();
    }

    private EditText propertyField(String value, int inputType) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setText(value);
        field.setSelectAllOnFocus(true);
        return field;
    }

    private void editSelectedWidget() {
        if (editor == null || editor.selected < 0 || editor.selected >= widgets.size()) {
            Toast.makeText(this, "Сначала выбери элемент на экране", Toast.LENGTH_SHORT).show();
            return;
        }
        Widget w = widgets.get(editor.selected);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        form.setPadding(pad, dp(4), pad, 0);

        TextView textLabel = new TextView(this);
        textLabel.setText("Подпись");
        form.addView(textLabel);
        EditText textField = propertyField(w.text, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(textField);

        TextView connectionLabel = new TextView(this);
        connectionLabel.setText("Привязка к данным (адрес / регистр / устройство)");
        connectionLabel.setPadding(0, dp(10), 0, 0);
        form.addView(connectionLabel);
        EditText connectionField = propertyField(w.connectionInfo == null ? "" : w.connectionInfo, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        connectionField.setHint("tcp://192.168.1.10:502/1/40001");
        form.addView(connectionField);

        final EditText timerPresetField;
        if ("FBD Таймер".equals(w.type)) {
            TextView timerLabel = new TextView(this);
            timerLabel.setText("Задержка TON (миллисекунды)");
            timerLabel.setPadding(0, dp(10), 0, 0);
            form.addView(timerLabel);
            timerPresetField = propertyField(Integer.toString(w.timerPresetMs), InputType.TYPE_CLASS_NUMBER);
            form.addView(timerPresetField);
        } else {
            timerPresetField = null;
        }

        final EditText compareLeftField;
        final EditText compareOperatorField;
        final EditText compareRightField;
        if ("FBD Сравнение".equals(w.type)) {
            TextView compareLabel = new TextView(this);
            compareLabel.setText("Числовое сравнение A / оператор / B");
            compareLabel.setPadding(0, dp(10), 0, 0);
            form.addView(compareLabel);
            compareLeftField = propertyField(Double.toString(w.compareA), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            compareOperatorField = propertyField(w.compareOperator, InputType.TYPE_CLASS_TEXT);
            compareRightField = propertyField(Double.toString(w.compareB), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            form.addView(compareLeftField);
            form.addView(compareOperatorField);
            form.addView(compareRightField);
        } else {
            compareLeftField = null;
            compareOperatorField = null;
            compareRightField = null;
        }

        TextView geometryLabel = new TextView(this);
        geometryLabel.setText("Положение и размер (пиксели)");
        geometryLabel.setPadding(0, dp(10), 0, 0);
        form.addView(geometryLabel);

        EditText xField = propertyField(Integer.toString(Math.round(w.x)), InputType.TYPE_CLASS_NUMBER);
        xField.setHint("X");
        form.addView(xField);
        EditText yField = propertyField(Integer.toString(Math.round(w.y)), InputType.TYPE_CLASS_NUMBER);
        yField.setHint("Y");
        form.addView(yField);
        EditText widthField = propertyField(Integer.toString(Math.round(w.w)), InputType.TYPE_CLASS_NUMBER);
        widthField.setHint("Ширина");
        form.addView(widthField);
        EditText heightField = propertyField(Integer.toString(Math.round(w.h)), InputType.TYPE_CLASS_NUMBER);
        heightField.setHint("Высота");
        form.addView(heightField);

        new AlertDialog.Builder(this)
                .setTitle("Свойства: " + w.type)
                .setView(form)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Применить", (dialog, which) -> {
                    w.text = textField.getText().toString();
                    w.connectionInfo = connectionField.getText().toString().trim();
                    if (timerPresetField != null) {
                        int newPreset = readPositiveInt(timerPresetField, w.timerPresetMs, false);
                        w.timerPresetMs = Math.max(1, Math.min(3600000, newPreset));
                        w.timerStartedAt = 0L;
                    }
                    if (compareLeftField != null) {
                        w.compareA = readDouble(compareLeftField, w.compareA);
                        w.compareB = readDouble(compareRightField, w.compareB);
                        w.compareOperator = normalizeComparisonOperator(compareOperatorField.getText().toString());
                        w.text = Double.toString(w.compareA) + " " + w.compareOperator + " " + Double.toString(w.compareB);
                    }
                    w.x = Math.max(0, readPositiveInt(xField, Math.round(w.x), true));
                    w.y = Math.max(0, readPositiveInt(yField, Math.round(w.y), true));
                    w.w = Math.max(dp(32), readPositiveInt(widthField, Math.round(w.w), false));
                    w.h = Math.max(dp(24), readPositiveInt(heightField, Math.round(w.h), false));
                    persistProject();
                    editor.invalidate();
                }).show();
    }

    private double readDouble(EditText field, double fallback) {
        try {
            double value = Double.parseDouble(field.getText().toString().trim().replace(',', '.'));
            return Double.isNaN(value) || Double.isInfinite(value) ? fallback : value;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String normalizeComparisonOperator(String value) {
        String operator = value == null ? ">" : value.trim();
        return operator.equals(">") || operator.equals("<") || operator.equals(">=")
                || operator.equals("<=") || operator.equals("==") || operator.equals("!=") ? operator : ">";
    }

    private int readPositiveInt(EditText field, int fallback, boolean allowZero) {
        try {
            int value = Integer.parseInt(field.getText().toString().trim());
            return allowZero ? Math.max(0, value) : Math.max(1, value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void copySelectedWidget() {
        if (editor == null || editor.selected < 0 || editor.selected >= widgets.size()) {
            Toast.makeText(this, "Сначала выбери элемент на экране", Toast.LENGTH_SHORT).show();
            return;
        }
        Widget source = widgets.get(editor.selected);
        clipboardWidget = new Widget();
        clipboardWidget.type = source.type;
        clipboardWidget.text = source.text;
        clipboardWidget.connectionInfo = source.connectionInfo;
        clipboardWidget.inputValue = source.inputValue;
        clipboardWidget.timerPresetMs = source.timerPresetMs;
        clipboardWidget.compareA = source.compareA;
        clipboardWidget.compareB = source.compareB;
        clipboardWidget.compareOperator = source.compareOperator;
        clipboardWidget.x = source.x;
        clipboardWidget.y = source.y;
        clipboardWidget.w = source.w;
        clipboardWidget.h = source.h;
        Toast.makeText(this, "Элемент скопирован", Toast.LENGTH_SHORT).show();
    }

    private void pasteWidget() {
        if (clipboardWidget == null) {
            Toast.makeText(this, "Сначала скопируй элемент", Toast.LENGTH_SHORT).show();
            return;
        }
        Widget copy = new Widget();
        copy.type = clipboardWidget.type;
        copy.text = clipboardWidget.text;
        copy.connectionInfo = clipboardWidget.connectionInfo;
        copy.inputValue = clipboardWidget.inputValue;
        copy.timerPresetMs = clipboardWidget.timerPresetMs;
        copy.compareA = clipboardWidget.compareA;
        copy.compareB = clipboardWidget.compareB;
        copy.compareOperator = clipboardWidget.compareOperator;
        copy.x = Math.min(Math.max(0, editor == null ? clipboardWidget.x + dp(24) : editor.getWidth() - clipboardWidget.w),
                clipboardWidget.x + dp(24));
        copy.y = clipboardWidget.y + dp(24);
        copy.w = clipboardWidget.w;
        copy.h = clipboardWidget.h;
        liveValues.clear();
        widgets.add(copy);
        if (editor != null) {
            editor.selected = widgets.size() - 1;
            editor.invalidate();
        }
        persistProject();
        Toast.makeText(this, "Копия добавлена на экран", Toast.LENGTH_SHORT).show();
    }

    private void adjustSetpoint(Widget widget, int delta) {
        String current = widget.text == null ? "Задатчик: 0" : widget.text;
        int split = current.lastIndexOf(':');
        String prefix = split >= 0 ? current.substring(0, split + 1) + " " : "Задатчик: ";
        String valueText = split >= 0 ? current.substring(split + 1).trim() : current.trim();
        try {
            double value = Double.parseDouble(valueText.replace(',', '.'));
            widget.text = prefix + (value + delta);
        } catch (NumberFormatException ignored) {
            widget.text = prefix + delta;
        }
        persistProject();
        Toast.makeText(MainActivity.this, "Значение: " + widget.text, Toast.LENGTH_SHORT).show();
    }

    private void deleteSelectedWidget() {
        if (editor == null || editor.selected < 0 || editor.selected >= widgets.size()) {
            Toast.makeText(this, "Сначала выбери элемент на экране", Toast.LENGTH_SHORT).show();
            return;
        }
        int removed = editor.selected;
        widgets.remove(removed);
        if (!screens.isEmpty()) {
            Screen screen = screens.get(currentScreenIndex);
            List<FbdConnection> kept = new ArrayList<>();
            for (FbdConnection connection : screen.connections) {
                if (connection.source == removed || connection.target == removed) continue;
                if (connection.source > removed) connection.source--;
                if (connection.target > removed) connection.target--;
                kept.add(connection);
            }
            screen.connections.clear();
            screen.connections.addAll(kept);
        }
        editor.selected = -1;
        liveValues.clear();
        persistProject();
        editor.invalidate();
    }

    private JSONObject widgetToJson(Widget w) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("type", w.type); obj.put("text", w.text);
        obj.put("connectionInfo", w.connectionInfo == null ? "" : w.connectionInfo);
        obj.put("inputValue", w.inputValue);
        obj.put("timerPresetMs", w.timerPresetMs);
        obj.put("compareA", w.compareA); obj.put("compareB", w.compareB); obj.put("compareOperator", w.compareOperator);
        obj.put("x", w.x); obj.put("y", w.y); obj.put("w", w.w); obj.put("h", w.h);
        return obj;
    }

    private JSONArray widgetsToJson(List<Widget> list) throws Exception {
        JSONArray array = new JSONArray();
        for (Widget w : list) array.put(widgetToJson(w));
        return array;
    }

    private Widget widgetFromJson(JSONObject obj) {
        Widget w = new Widget();
        w.type = obj.optString("type", "Текст"); w.text = obj.optString("text", "Надпись");
        w.connectionInfo = obj.optString("connectionInfo", "");
        w.inputValue = obj.optBoolean("inputValue", false);
        w.timerPresetMs = Math.max(1, Math.min(3600000, obj.optInt("timerPresetMs", 1000)));
        w.compareA = obj.optDouble("compareA", 0.0); w.compareB = obj.optDouble("compareB", 0.0);
        w.compareOperator = normalizeComparisonOperator(obj.optString("compareOperator", ">"));
        w.x = obj.optInt("x", dp(40)); w.y = obj.optInt("y", dp(50));
        w.w = obj.optInt("w", dp(160)); w.h = obj.optInt("h", dp(58));
        return w;
    }

    private void persistProject() {
        try {
            saveActiveScreen();
            JSONObject project = new JSONObject();
            JSONArray screenArray = new JSONArray();
            for (Screen screen : screens) {
                JSONObject screenObj = new JSONObject();
                screenObj.put("name", screen.name);
                screenObj.put("widgets", widgetsToJson(screen.widgets));
                JSONArray connectionArray = new JSONArray();
                for (FbdConnection connection : screen.connections) {
                    JSONObject connectionObj = new JSONObject();
                    connectionObj.put("source", connection.source);
                    connectionObj.put("target", connection.target);
                    connectionArray.put(connectionObj);
                }
                screenObj.put("connections", connectionArray);
                screenArray.put(screenObj);
            }
            project.put("screens", screenArray);
            project.put("currentScreen", currentScreenIndex);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(ACTIVE_PROJECT, project.toString())
                    .putString("project_name", projectName).apply();
        } catch (Exception ignored) {
            Toast.makeText(this, "Не удалось сохранить проект", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadProject() {
        widgets.clear(); screens.clear(); currentScreenIndex = 0;
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        projectName = prefs.getString("project_name", projectName);
        String json = prefs.getString(ACTIVE_PROJECT, "[]");
        try {
            Object parsed = new org.json.JSONTokener(json).nextValue();
            if (parsed instanceof JSONObject) {
                JSONObject project = (JSONObject) parsed;
                JSONArray screenArray = project.optJSONArray("screens");
                if (screenArray != null) {
                    for (int i = 0; i < screenArray.length(); i++) {
                        JSONObject screenObj = screenArray.getJSONObject(i);
                        Screen screen = new Screen(screenObj.optString("name", "Экран " + (i + 1)));
                        JSONArray widgetArray = screenObj.optJSONArray("widgets");
                        if (widgetArray != null) for (int j = 0; j < widgetArray.length(); j++) screen.widgets.add(widgetFromJson(widgetArray.getJSONObject(j)));
                        JSONArray connectionArray = screenObj.optJSONArray("connections");
                        if (connectionArray != null) for (int j = 0; j < connectionArray.length(); j++) {
                            JSONObject connectionObj = connectionArray.optJSONObject(j);
                            if (connectionObj == null) continue;
                            int source = connectionObj.optInt("source", -1);
                            int target = connectionObj.optInt("target", -1);
                            if (source >= 0 && target >= 0 && source < screen.widgets.size() && target < screen.widgets.size()
                                    && source != target && isFbd(screen.widgets.get(source)) && isFbd(screen.widgets.get(target))) {
                                screen.connections.add(new FbdConnection(source, target));
                            }
                        }
                        screens.add(screen);
                    }
                    currentScreenIndex = Math.max(0, Math.min(project.optInt("currentScreen", 0), Math.max(0, screens.size() - 1)));
                }
            } else if (parsed instanceof JSONArray) {
                // Backward compatibility with the earlier single-screen project format.
                Screen legacy = new Screen("Экран 1");
                JSONArray legacyWidgets = (JSONArray) parsed;
                for (int i = 0; i < legacyWidgets.length(); i++) legacy.widgets.add(widgetFromJson(legacyWidgets.getJSONObject(i)));
                screens.add(legacy);
            }
        } catch (Exception ignored) {
            screens.clear();
        }
        if (screens.isEmpty()) screens.add(new Screen("Экран 1"));
        currentScreenIndex = Math.max(0, Math.min(currentScreenIndex, screens.size() - 1));
        for (Widget w : screens.get(currentScreenIndex).widgets) widgets.add(copyWidget(w));
    }

    private static class Screen {
        String name;
        final List<Widget> widgets = new ArrayList<>();
        final List<FbdConnection> connections = new ArrayList<>();
        Screen(String name) { this.name = name; }
    }

    private static class FbdConnection {
        int source;
        int target;
        FbdConnection(int source, int target) { this.source = source; this.target = target; }
    }

    private boolean isFbd(Widget widget) {
        return widget != null && widget.type != null && widget.type.startsWith("FBD ");
    }

    private boolean evaluateFbd(int index, boolean[] visiting) {
        if (index < 0 || index >= widgets.size() || visiting[index]) return false;
        Widget widget = widgets.get(index);
        if (!isFbd(widget)) return false;
        if ("FBD Вход".equals(widget.type)) return widget.inputValue;
        visiting[index] = true;
        List<Boolean> inputs = new ArrayList<>();
        if (!screens.isEmpty()) {
            for (FbdConnection connection : screens.get(currentScreenIndex).connections) {
                if (connection.target == index) inputs.add(evaluateFbd(connection.source, visiting));
            }
        }
        visiting[index] = false;
        if ("FBD И".equals(widget.type)) {
            if (inputs.isEmpty()) return false;
            for (Boolean value : inputs) if (!value) return false;
            return true;
        }
        if ("FBD ИЛИ".equals(widget.type)) {
            for (Boolean value : inputs) if (value) return true;
            return false;
        }
        if ("FBD НЕ".equals(widget.type)) return inputs.isEmpty() || !inputs.get(0);
        if ("FBD Сравнение".equals(widget.type)) {
            switch (normalizeComparisonOperator(widget.compareOperator)) {
                case "<": return widget.compareA < widget.compareB;
                case ">=": return widget.compareA >= widget.compareB;
                case "<=": return widget.compareA <= widget.compareB;
                case "==": return Double.compare(widget.compareA, widget.compareB) == 0;
                case "!=": return Double.compare(widget.compareA, widget.compareB) != 0;
                default: return widget.compareA > widget.compareB;
            }
        }
        if ("FBD Таймер".equals(widget.type)) {
            boolean input = !inputs.isEmpty() && inputs.get(0);
            if (!input) {
                widget.timerStartedAt = 0L;
                return false;
            }
            long now = SystemClock.elapsedRealtime();
            if (widget.timerStartedAt == 0L) widget.timerStartedAt = now;
            return now - widget.timerStartedAt >= widget.timerPresetMs;
        }
        return false;
    }

    private boolean reachesFbd(int current, int goal, boolean[] visited, List<FbdConnection> connections) {
        if (current == goal) return true;
        if (current < 0 || current >= visited.length || visited[current]) return false;
        visited[current] = true;
        for (FbdConnection connection : connections) {
            if (connection.source == current && reachesFbd(connection.target, goal, visited, connections)) return true;
        }
        return false;
    }

    private void connectFbdWidgets(int source, int target) {
        if (source < 0 || target < 0 || source >= widgets.size() || target >= widgets.size() || source == target) {
            Toast.makeText(this, "Выбери два разных FBD-блока", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isFbd(widgets.get(source)) || !isFbd(widgets.get(target))) {
            Toast.makeText(this, "Связи можно создавать только между FBD-блоками", Toast.LENGTH_SHORT).show();
            return;
        }
        Screen screen = screens.get(currentScreenIndex);
        if (reachesFbd(target, source, new boolean[widgets.size()], screen.connections)) {
            Toast.makeText(this, "Связь отменена: она создаёт цикл в FBD", Toast.LENGTH_LONG).show();
            return;
        }
        for (FbdConnection connection : screen.connections) {
            if (connection.source == source && connection.target == target) {
                Toast.makeText(this, "Такая связь уже существует", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        screen.connections.add(new FbdConnection(source, target));
        persistProject();
        Toast.makeText(this, "Связь FBD добавлена", Toast.LENGTH_SHORT).show();
    }

    private static class Widget {
        String type;
        String text;
        String connectionInfo = "";
        boolean inputValue = false;
        int timerPresetMs = 1000;
        long timerStartedAt = 0L;
        double compareA = 0.0, compareB = 0.0;
        String compareOperator = ">";
        float x, y, w, h;
    }

    private class EditorCanvas extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int selected = -1;
        float downX, downY, startX, startY;
        boolean dragging;

        EditorCanvas() {
            super(MainActivity.this);
            setBackgroundColor(Color.WHITE);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.rgb(250, 252, 255));
            paint.setColor(Color.rgb(228, 234, 241));
            paint.setStrokeWidth(1);
            int grid = dp(20);
            for (int x = 0; x < getWidth(); x += grid) canvas.drawLine(x, 0, x, getHeight(), paint);
            for (int y = 0; y < getHeight(); y += grid) canvas.drawLine(0, y, getWidth(), y, paint);

            if (!screens.isEmpty()) {
                Screen screen = screens.get(currentScreenIndex);
                paint.setColor(Color.rgb(124, 58, 237));
                paint.setStrokeWidth(dp(2));
                paint.setStyle(Paint.Style.STROKE);
                for (FbdConnection connection : screen.connections) {
                    if (connection.source < 0 || connection.target < 0 || connection.source >= widgets.size() || connection.target >= widgets.size()) continue;
                    Widget from = widgets.get(connection.source);
                    Widget to = widgets.get(connection.target);
                    if (!isFbd(from) || !isFbd(to)) continue;
                    float x1 = from.x + from.w, y1 = from.y + from.h / 2;
                    float x2 = to.x, y2 = to.y + to.h / 2;
                    float mid = (x1 + x2) / 2f;
                    canvas.drawLine(x1, y1, mid, y1, paint);
                    canvas.drawLine(mid, y1, mid, y2, paint);
                    canvas.drawLine(mid, y2, x2, y2, paint);
                }
                paint.setStyle(Paint.Style.FILL);
            }
            for (int i = 0; i < widgets.size(); i++) {
                Widget w = widgets.get(i);
                RectF rect = new RectF(w.x, w.y, w.x + w.w, w.y + w.h);
                paint.setStyle(Paint.Style.FILL);
                if (w.type != null && w.type.startsWith("FBD ")) {
                    paint.setColor(Color.rgb(237, 233, 254));
                } else if ("Индикатор".equals(w.type)) {
                    paint.setColor(Color.rgb(220, 252, 231));
                } else if ("Кнопка".equals(w.type)) {
                    paint.setColor(Color.rgb(219, 234, 254));
                } else if ("Задатчик".equals(w.type)) {
                    paint.setColor(Color.rgb(254, 243, 199));
                } else {
                    paint.setColor(Color.WHITE);
                }
                canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(i == selected ? 2.5f : 1));
                paint.setColor(i == selected ? Color.rgb(21, 101, 192) : Color.rgb(148, 163, 184));
                canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(31, 41, 55));
                paint.setTextSize(dp(14));
                if (w.type != null && w.type.startsWith("FBD ")) {
                    paint.setColor(Color.rgb(91, 33, 182));
                    paint.setTextSize(dp(13));
                    String blockTitle = "FBD Вход".equals(w.type) ? "Вход " + (w.inputValue ? "1" : "0") : w.text;
                    if (liveValues.containsKey(i) && !"FBD Вход".equals(w.type)) blockTitle += " [" + liveValues.get(i) + "]";
                    canvas.drawText(blockTitle, w.x + dp(12), w.y + dp(23), paint);
                    boolean output = evaluateFbd(i, new boolean[widgets.size()]);
                    paint.setTextSize(dp(11));
                    paint.setColor(output ? Color.rgb(22, 163, 74) : Color.rgb(100, 116, 139));
                    canvas.drawText("OUT = " + (output ? "1" : "0"), w.x + dp(12), w.y + w.h - dp(8), paint);
                    paint.setTextSize(dp(13));
                    paint.setColor(Color.rgb(109, 40, 217));
                    canvas.drawLine(w.x, w.y + w.h * 0.38f, w.x + dp(14), w.y + w.h * 0.38f, paint);
                    canvas.drawLine(w.x, w.y + w.h * 0.68f, w.x + dp(14), w.y + w.h * 0.68f, paint);
                    canvas.drawLine(w.x + w.w - dp(14), w.y + w.h / 2, w.x + w.w, w.y + w.h / 2, paint);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(w.x + dp(4), w.y + w.h * 0.38f, dp(4), paint);
                    canvas.drawCircle(w.x + dp(4), w.y + w.h * 0.68f, dp(4), paint);
                    canvas.drawCircle(w.x + w.w - dp(4), w.y + w.h / 2, dp(4), paint);
                } else if ("Индикатор".equals(w.type)) {
                    paint.setColor(Color.rgb(22, 163, 74));
                    canvas.drawCircle(w.x + dp(16), w.y + w.h / 2, dp(6), paint);
                    paint.setColor(Color.rgb(31, 41, 55));
                    canvas.drawText(w.text + (liveValues.containsKey(i) ? ": " + liveValues.get(i) : ""), w.x + dp(29), w.y + w.h / 2 + dp(5), paint);
                } else if ("Задатчик".equals(w.type)) {
                    paint.setColor(Color.rgb(120, 80, 10));
                    canvas.drawText(w.text, w.x + dp(9), w.y + dp(21), paint);
                    paint.setColor(Color.rgb(255, 255, 255));
                    canvas.drawRoundRect(new RectF(w.x + dp(8), w.y + dp(29), w.x + w.w - dp(8), w.y + w.h - dp(6)), dp(4), dp(4), paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(Color.rgb(217, 119, 6));
                    canvas.drawRoundRect(new RectF(w.x + dp(8), w.y + dp(29), w.x + w.w - dp(8), w.y + w.h - dp(6)), dp(4), dp(4), paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.rgb(120, 80, 10));
                    canvas.drawText("−             +", w.x + dp(18), w.y + w.h - dp(16), paint);
                } else {
                    float tx = w.x + dp(10);
                    float ty = w.y + w.h / 2 + dp(5);
                    canvas.drawText(w.text + (liveValues.containsKey(i) ? " = " + liveValues.get(i) : ""), tx, ty, paint);
                }
            }
            for (Widget widget : widgets) {
                if ("FBD Таймер".equals(widget.type) && widget.timerStartedAt > 0L
                        && SystemClock.elapsedRealtime() - widget.timerStartedAt < widget.timerPresetMs) {
                    postInvalidateDelayed(100L);
                    break;
                }
            }
            if (fbdWireMode) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(109, 40, 217));
                paint.setTextSize(dp(12));
                canvas.drawText(wireSource < 0 ? "РЕЖИМ СВЯЗИ: выбери источник" : "РЕЖИМ СВЯЗИ: выбери цель", dp(10), dp(18), paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(11));
            paint.setColor(Color.rgb(100, 116, 139));
            canvas.drawText("Редактор BLOODSKY • перетаскивай элементы, касайся для выбора", dp(10), getHeight() - dp(10), paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = x; downY = y; dragging = false; selected = -1;
                for (int i = widgets.size() - 1; i >= 0; i--) {
                    Widget w = widgets.get(i);
                    if (x >= w.x && x <= w.x + w.w && y >= w.y && y <= w.y + w.h) {
                        selected = i; startX = w.x; startY = w.y; break;
                    }
                }
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE && selected >= 0) {
                float dx = x - downX, dy = y - downY;
                if (Math.abs(dx) + Math.abs(dy) > dp(5)) dragging = true;
                if (dragging) {
                    Widget w = widgets.get(selected);
                    w.x = Math.max(0, Math.min(getWidth() - w.w, startX + dx));
                    w.y = Math.max(0, Math.min(getHeight() - w.h, startY + dy));
                    invalidate();
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (dragging) {
                    persistProject();
                } else if (selected >= 0 && fbdWireMode) {
                    Widget picked = widgets.get(selected);
                    if (!isFbd(picked)) {
                        Toast.makeText(MainActivity.this, "В режиме связи выбери FBD-блок", Toast.LENGTH_SHORT).show();
                    } else if (wireSource < 0) {
                        wireSource = selected;
                        Toast.makeText(MainActivity.this, "Источник выбран: " + picked.text + ". Теперь выбери целевой блок", Toast.LENGTH_LONG).show();
                    } else {
                        int source = wireSource;
                        wireSource = -1;
                        connectFbdWidgets(source, selected);
                        fbdWireMode = false;
                    }
                } else if (selected >= 0) {
                    Widget picked = widgets.get(selected);
                    if ("Задатчик".equals(picked.type) && y >= picked.y + dp(29)) {
                        adjustSetpoint(picked, x < picked.x + picked.w / 2 ? -1 : 1);
                    } else if ("FBD Вход".equals(picked.type)) {
                        picked.inputValue = !picked.inputValue;
                        persistProject();
                        Toast.makeText(MainActivity.this, "Вход FBD = " + (picked.inputValue ? "1" : "0"), Toast.LENGTH_SHORT).show();
                    } else {
                        editSelectedWidget();
                    }
                }
                invalidate();
                return true;
            }
            return true;
        }
    }
}
