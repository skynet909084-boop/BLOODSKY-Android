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
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private JSONObject projectExtraFields = new JSONObject();
    private LinearLayout root;
    private EditorCanvas editor;
    private final List<Widget> widgets = new ArrayList<>();
    private final List<Screen> screens = new ArrayList<>();
    private int currentScreenIndex = 0;
    private Widget clipboardWidget;
    private boolean fbdWireMode = false;
    private int wireSource = -1;
    // Cache stateful FBD outputs once per canvas scan so fan-out connections do not consume edge pulses repeatedly.
    private boolean[] fbdCacheValid = new boolean[0];
    private boolean[] fbdCacheValues = new boolean[0];
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

    @Override protected void onDestroy() {
        stopModbusPolling();
        editor = null;
        modbusExecutor.shutdownNow();
        super.onDestroy();
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
        Spinner functionSpinner = new Spinner(this);
        ArrayAdapter<String> functionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Holding registers (FC03)", "Input registers (FC04)"});
        functionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        functionSpinner.setAdapter(functionAdapter);
        form.addView(functionSpinner);
        EditText addressField = propertyField("40001", InputType.TYPE_CLASS_NUMBER);
        addressField.setHint("Адрес регистра: FC03 обычно 40001, FC04 обычно 30001");
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
            final int functionCode = functionSpinner.getSelectedItemPosition() == 0 ? 3 : 4;
            final int port, unit, startAddress, count;
            try {
                port = Integer.parseInt(portField.getText().toString().trim());
                unit = Integer.parseInt(unitField.getText().toString().trim());
                int enteredAddress = Integer.parseInt(addressField.getText().toString().trim());
                count = Integer.parseInt(countField.getText().toString().trim());
                if (port < 1 || port > 65535 || unit < 0 || unit > 255 || count < 1 || count > 125
                        || enteredAddress < 0 || enteredAddress > 65535) throw new IllegalArgumentException();
                if (functionCode == 4 && enteredAddress >= 30001 && enteredAddress <= 39999) {
                    startAddress = enteredAddress - 30001;
                } else if (functionCode == 3 && enteredAddress >= 40001 && enteredAddress <= 49999) {
                    startAddress = enteredAddress - 40001;
                } else if ((functionCode == 4 && enteredAddress >= 40001 && enteredAddress <= 49999)
                        || (functionCode == 3 && enteredAddress >= 30001 && enteredAddress <= 39999)) {
                    throw new IllegalArgumentException("Адрес не соответствует выбранному типу регистра");
                } else {
                    startAddress = enteredAddress;
                }
                if (startAddress < 0 || startAddress > 65535 || startAddress + count > 65536) throw new IllegalArgumentException();
                if (host.isEmpty()) throw new IllegalArgumentException();
            } catch (Exception ex) {
                String message = ex.getMessage();
                result.setText(message == null || message.trim().isEmpty()
                        ? "Проверь IP, порт, Unit ID, адрес и количество регистров."
                        : "Параметры запроса: " + message);
                return;
            }
            result.setText("Подключаюсь к " + host + ":" + port + "…");
            new Thread(() -> {
                String report;
                try {
                    report = readModbusRegisters(host, port, unit, startAddress, count, functionCode);
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
        return readModbusRegisters(host, port, unit, startAddress, count, 3);
    }

    private String readModbusRegisters(String host, int port, int unit, int startAddress, int count, int functionCode) throws Exception {
        if (functionCode != 3 && functionCode != 4) throw new IllegalArgumentException("Unsupported Modbus function");
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
            out.writeByte(functionCode); // Read holding or input registers
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
            if (function == (functionCode | 0x80) && pdu.length >= 2) {
                throw new IOException("Код исключения Modbus: " + (pdu[1] & 0xff));
            }
            if (function != functionCode || pdu.length < 2) throw new IOException("Неожиданный код функции");
            int byteCount = pdu[1] & 0xff;
            if (byteCount != count * 2 || pdu.length < 2 + byteCount) throw new IOException("Размер ответа не совпадает с запросом");
            StringBuilder report = new StringBuilder();
            report.append("Успешно: ").append(count).append(functionCode == 3 ? " holding registers (FC03)\n" : " input registers (FC04)\n");
            for (int i = 0; i < count; i++) {
                int value = ((pdu[2 + i * 2] & 0xff) << 8) | (pdu[3 + i * 2] & 0xff);
                report.append((functionCode == 3 ? 40001 : 30001) + startAddress + i).append("  |  ")
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
        if (screenArray == null || screenArray.length() == 0 || screenArray.length() > 100) {
            throw new IllegalArgumentException("Invalid screen list");
        }
        int currentScreen = project.optInt("currentScreen", 0);
        if (currentScreen < 0 || currentScreen >= screenArray.length()) {
            throw new IllegalArgumentException("Current screen index out of range");
        }
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
            if (connections == null) continue;
            if (connections.length() > 4000) throw new IllegalArgumentException("Too many connections");
            int nodeCount = screenWidgets.length();
            List<List<Integer>> adjacency = new ArrayList<>();
            int[] indegree = new int[nodeCount];
            for (int node = 0; node < nodeCount; node++) adjacency.add(new ArrayList<>());
            java.util.HashSet<Long> uniqueEdges = new java.util.HashSet<>();
            for (int j = 0; j < connections.length(); j++) {
                JSONObject edge = connections.optJSONObject(j);
                if (edge == null) throw new IllegalArgumentException("Connection must be an object");
                int source = edge.optInt("source", -1);
                int target = edge.optInt("target", -1);
                if (source < 0 || target < 0 || source >= nodeCount || target >= nodeCount || source == target) {
                    throw new IllegalArgumentException("Connection index out of range");
                }
                JSONObject sourceWidget = screenWidgets.optJSONObject(source);
                JSONObject targetWidget = screenWidgets.optJSONObject(target);
                if (sourceWidget == null || targetWidget == null
                        || !sourceWidget.optString("type", "").startsWith("FBD ")
                        || !targetWidget.optString("type", "").startsWith("FBD ")) {
                    throw new IllegalArgumentException("Connections must link FBD blocks");
                }
                long edgeKey = (((long) source) << 32) | (target & 0xffffffffL);
                if (!uniqueEdges.add(edgeKey)) throw new IllegalArgumentException("Duplicate FBD connection");
                adjacency.get(source).add(target);
                indegree[target]++;
            }
            // Kahn's algorithm rejects imported graphs with cycles before any project data is saved.
            java.util.ArrayDeque<Integer> ready = new java.util.ArrayDeque<>();
            for (int node = 0; node < nodeCount; node++) if (indegree[node] == 0) ready.add(node);
            int visitedCount = 0;
            while (!ready.isEmpty()) {
                int node = ready.removeFirst();
                visitedCount++;
                for (int next : adjacency.get(node)) {
                    if (--indegree[next] == 0) ready.addLast(next);
                }
            }
            if (visitedCount != nodeCount) throw new IllegalArgumentException("Cyclic FBD connections");
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
        screenBar.addView(action("Фон", this::editScreenBackground), new LinearLayout.LayoutParams(-2, dp(42)));
        screenBar.addView(action("›", this::nextScreen), new LinearLayout.LayoutParams(dp(48), dp(42)));
        screenBar.addView(action("+ Экран", this::addScreen), new LinearLayout.LayoutParams(-2, dp(42)));
        screenBar.addView(action("−", this::removeCurrentScreen), new LinearLayout.LayoutParams(dp(48), dp(42)));
        root.addView(screenBar);

        // Keep the canvas tall on phones: component choices live in one compact list menu.
        root.addView(action("+ Добавить элемент", this::showAddWidgetMenu),
                new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout toolsBar = new LinearLayout(this);
        toolsBar.setOrientation(LinearLayout.HORIZONTAL);
        toolsBar.addView(action("Связать FBD", () -> {
            fbdWireMode = !fbdWireMode;
            wireSource = -1;
            Toast.makeText(this, fbdWireMode ? "Режим связи: выбери исходный FBD-блок, затем целевой" : "Режим связи выключен", Toast.LENGTH_LONG).show();
            if (editor != null) editor.invalidate();
        }), new LinearLayout.LayoutParams(0, dp(42), 1));
        toolsBar.addView(action("Диагностика Modbus", this::showModbusDiagnostics),
                new LinearLayout.LayoutParams(0, dp(42), 1));
        root.addView(toolsBar);

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
        // Multiple widgets can display the same Modbus register. Read that register
        // once per scan, then fan the value out to every widget using the binding.
        final Map<String, BindingRequest> groupedRequests = new LinkedHashMap<>();
        for (int i = 0; i < snapshotWidgets.size(); i++) {
            Widget widget = snapshotWidgets.get(i);
            BindingRequest request = parseBinding(i, widget.connectionInfo);
            if (request == null) continue;
            String key = request.registerKey();
            BindingRequest existing = groupedRequests.get(key);
            if (existing == null) groupedRequests.put(key, request);
            else existing.widgetIndices.add(i);
        }
        final List<BindingRequest> requests = new ArrayList<>(groupedRequests.values());
        if (modbusPollInFlight) return;
        // Surface malformed tcp bindings as a configuration error rather than leaving
        // an old live value on screen, which can look like valid but stale process data.
        final Map<Integer, String> invalidBindings = new HashMap<>();
        for (int i = 0; i < snapshotWidgets.size(); i++) {
            String binding = snapshotWidgets.get(i).connectionInfo;
            if (binding != null && binding.startsWith("tcp://")
                    && parseBinding(i, binding) == null) invalidBindings.put(i, "CFG");
        }
        if (requests.isEmpty() && invalidBindings.isEmpty()) return;
        modbusPollInFlight = true;
        modbusExecutor.execute(() -> {
            try {
                final Map<Integer, String> updates = new HashMap<>(invalidBindings);
                for (BindingRequest request : requests) {
                    String value;
                    try {
                        String report = readModbusRegisters(request.host, request.port, request.unit, request.address, 1, request.functionCode);
                        String[] lines = report.split("\n");
                        if (lines.length < 2) throw new IOException("Пустой ответ регистра");
                        String[] columns = lines[1].split("\\|");
                        if (columns.length < 2) throw new IOException("Не удалось разобрать регистр");
                        value = columns[1].trim();
                    } catch (Exception e) {
                        value = "ERR";
                    }
                    for (int widgetIndex : request.widgetIndices) updates.put(widgetIndex, value);
                }
                runOnUiThread(() -> {
                    if (editor == null) return;
                    for (Map.Entry<Integer, String> entry : updates.entrySet()) {
                        int index = entry.getKey();
                        if (index < 0 || index >= widgets.size() || index >= snapshotWidgets.size()
                                || widgets.get(index) != snapshotWidgets.get(index)) continue;
                        liveValues.put(index, entry.getValue());
                        if ("FBD Вход".equals(widgets.get(index).type)) {
                            widgets.get(index).inputValue = isActiveModbusValue(entry.getValue());
                        }
                    }
                    editor.invalidate();
                });
            } finally {
                modbusPollInFlight = false;
            }
        });
    }

    private boolean isActiveModbusValue(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty() || "err".equals(normalized) || "cfg".equals(normalized)
                || "false".equals(normalized) || "off".equals(normalized)
                || "no".equals(normalized)) return false;
        if ("true".equals(normalized) || "on".equals(normalized) || "yes".equals(normalized)) return true;
        try {
            return Double.parseDouble(normalized) != 0.0d;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private BindingRequest parseBinding(int widgetIndex, String binding) {
        if (binding == null || !binding.startsWith("tcp://")) return null;
        try {
            URI uri = URI.create(binding);
            String[] path = uri.getPath().split("/", -1);
            // A binding must have exactly /unit/address; reject extra path segments,
            // query strings and fragments instead of silently reading a different register.
            if (uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535
                    || path.length != 3 || !path[0].isEmpty()
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
            int unit = Integer.parseInt(path[1]);
            int enteredAddress = Integer.parseInt(path[2]);
            if (unit < 0 || unit > 255 || enteredAddress < 0 || enteredAddress > 65535) return null;
            int functionCode = 3;
            int address = enteredAddress;
            if (enteredAddress >= 30001 && enteredAddress <= 39999) {
                functionCode = 4;
                address = enteredAddress - 30001;
            } else if (enteredAddress >= 40001 && enteredAddress <= 49999) {
                functionCode = 3;
                address = enteredAddress - 40001;
            }
            return new BindingRequest(widgetIndex, uri.getHost(), uri.getPort(), unit, address, functionCode);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static class BindingRequest {
        final List<Integer> widgetIndices = new ArrayList<>();
        final int port, unit, address, functionCode;
        final String host;
        BindingRequest(int widgetIndex, String host, int port, int unit, int address, int functionCode) {
            this.widgetIndices.add(widgetIndex);
            this.host = host; this.port = port; this.unit = unit;
            this.address = address; this.functionCode = functionCode;
        }
        String registerKey() {
            return host.toLowerCase(java.util.Locale.ROOT) + ":" + port + "/" + unit + "/" + address + "/FC" + functionCode;
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
        copy.type = source.type; copy.text = source.text; copy.connectionInfo = source.connectionInfo; copy.inputValue = source.inputValue; copy.buttonState = source.buttonState; copy.rsState = source.rsState; copy.counterPreset = source.counterPreset; copy.counterValue = source.counterValue; copy.counterLastInput = source.counterLastInput; copy.timerPresetMs = source.timerPresetMs; copy.timerStartedAt = source.timerStartedAt; copy.compareA = source.compareA; copy.compareB = source.compareB; copy.compareOperator = source.compareOperator;
        copy.backgroundColor = source.backgroundColor; copy.textColor = source.textColor; copy.textSizeSp = source.textSizeSp;
        copy.visible = source.visible; copy.showFrame = source.showFrame; copy.autoResizeText = source.autoResizeText;
        try { copy.extraFields = new JSONObject(source.extraFields.toString()); } catch (Exception ignored) { copy.extraFields = new JSONObject(); }
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

    private void editScreenBackground() {
        if (screens.isEmpty()) return;
        Screen screen = screens.get(currentScreenIndex);
        EditText input = propertyField(String.format(java.util.Locale.ROOT, "#%06X", (0xFFFFFF & screen.backgroundColor)), InputType.TYPE_CLASS_TEXT);
        input.setHint("#RRGGBB");
        new AlertDialog.Builder(this).setTitle("Цвет фона экрана").setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Применить", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.startsWith("#")) value = "#" + value;
                    if (!value.matches("#[0-9a-fA-F]{6}")) {
                        Toast.makeText(this, "Укажи цвет в формате #RRGGBB", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try { screen.backgroundColor = Color.parseColor(value); }
                    catch (IllegalArgumentException ignored) { return; }
                    persistProject(); if (editor != null) editor.invalidate();
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

    private void showWidgetContextMenu() {
        if (editor == null || editor.selected < 0 || editor.selected >= widgets.size()) return;
        final String[] actions = {"Свойства", "Копировать", "Удалить"};
        new AlertDialog.Builder(this)
                .setTitle(widgets.get(editor.selected).text)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) editSelectedWidget();
                    else if (which == 1) copySelectedWidget();
                    else if (which == 2) deleteSelectedWidget();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showModbusDiagnostics() {
        StringBuilder report = new StringBuilder();
        int boundCount = 0;
        int errorCount = 0;
        int pendingCount = 0;
        int activeCount = 0;
        int inactiveCount = 0;
        for (int i = 0; i < widgets.size(); i++) {
            Widget widget = widgets.get(i);
            String binding = widget.connectionInfo == null ? "" : widget.connectionInfo.trim();
            if (binding.isEmpty()) continue;
            boundCount++;
            String state = liveValues.get(i);
            if (binding.startsWith("tcp://") && parseBinding(i, binding) == null) state = "CFG — неверный адрес";
            else if (state == null) state = "Ожидание ответа";
            if ("ERR".equals(state) || (state != null && state.startsWith("CFG"))) errorCount++;
            else if ("Ожидание ответа".equals(state)) pendingCount++;
            else if (isActiveModbusValue(state)) activeCount++;
            else inactiveCount++;
            report.append(i + 1).append(". ").append(widget.text == null || widget.text.trim().isEmpty() ? widget.type : widget.text)
                    .append("\n  Привязка: ").append(binding)
                    .append("\n  Текущее значение: ").append(state)
                    .append("\n\n");
        }
        if (boundCount == 0) {
            report.append("На текущем экране нет элементов с указанной привязкой.\n")
                    .append("Открой «Свойства» элемента и укажи адрес Modbus, например:\n")
                    .append("tcp://192.168.1.10:502/1/40001");
        } else {
            report.insert(0, "Привязок: " + boundCount + " · Ошибок: " + errorCount + " · Ожидание: " + pendingCount + " · Активных: " + activeCount + " · Неактивных: " + inactiveCount + "\n\n");
        }
        TextView content = new TextView(this);
        content.setText(report.toString());
        content.setTextSize(14);
        content.setTextColor(Color.rgb(30, 41, 59));
        content.setPadding(dp(16), dp(12), dp(16), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle("Диагностика Modbus")
                .setView(scroll)
                .setPositiveButton("Обновить", (dialog, which) -> showModbusDiagnostics())
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showAddWidgetMenu() {
        final String[] labels = {
                "Текст / надпись", "Кнопка", "Индикатор", "Задатчик",
                "FBD — Вход", "FBD — И", "FBD — ИЛИ", "FBD — НЕ",
                "FBD — NAND", "FBD — NOR", "FBD — XOR",
                "FBD — TON (задержка включения)", "FBD — TP (импульс)", "FBD — TOF (задержка выключения)",
                "FBD — Сравнение", "FBD — RS", "FBD — SR", "FBD — CTU (счёт вверх)",
                "FBD — CTD (счёт вниз)", "FBD — R_TRIG", "FBD — F_TRIG"
        };
        final String[] types = {
                "Текст", "Кнопка", "Индикатор", "Задатчик",
                "FBD Вход", "FBD И", "FBD ИЛИ", "FBD НЕ",
                "FBD NAND", "FBD NOR", "FBD XOR",
                "FBD Таймер", "FBD TP", "FBD TOF",
                "FBD Сравнение", "FBD RS", "FBD SR", "FBD CTU",
                "FBD CTD", "FBD R_TRIG", "FBD F_TRIG"
        };
        final String[] defaults = {
                "Надпись", "Кнопка", "Индикатор", "Задатчик: 0",
                "Вход", "И", "ИЛИ", "НЕ", "NAND", "NOR", "XOR",
                "TON", "TP", "TOF", "A > B", "RS", "SR", "CTU", "CTD", "R_TRIG", "F_TRIG"
        };
        new AlertDialog.Builder(this)
                .setTitle("Добавить элемент")
                .setItems(labels, (dialog, which) -> addWidget(types[which], defaults[which]))
                .setNegativeButton("Отмена", null)
                .show();
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
        if ("FBD CTD".equals(type)) w.counterValue = w.counterPreset;
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
        connectionField.setHint("tcp://192.168.1.10:502/1/40001 (FC03); для Input Registers: 30001 (FC04)");
        form.addView(connectionField);

        final EditText timerPresetField;
        if ("FBD Таймер".equals(w.type) || "FBD TP".equals(w.type) || "FBD TOF".equals(w.type)) {
            TextView timerLabel = new TextView(this);
            timerLabel.setText("Время " + ("FBD TP".equals(w.type) ? "импульса TP" : ("FBD TOF".equals(w.type) ? "выключения TOF" : "задержки TON")) + " (миллисекунды)");
            timerLabel.setPadding(0, dp(10), 0, 0);
            form.addView(timerLabel);
            timerPresetField = propertyField(Integer.toString(w.timerPresetMs), InputType.TYPE_CLASS_NUMBER);
            form.addView(timerPresetField);
        } else {
            timerPresetField = null;
        }
        final EditText counterPresetField;
        if ("FBD CTU".equals(w.type) || "FBD CTD".equals(w.type)) {
            TextView counterLabel = new TextView(this);
            counterLabel.setText("Уставка счётчика " + ("FBD CTD".equals(w.type) ? "CTD" : "CTU"));
            counterLabel.setPadding(0, dp(10), 0, 0);
            form.addView(counterLabel);
            counterPresetField = propertyField(Integer.toString(w.counterPreset), InputType.TYPE_CLASS_NUMBER);
            form.addView(counterPresetField);
        } else {
            counterPresetField = null;
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

        TextView styleLabel = new TextView(this);
        styleLabel.setText("Оформление");
        styleLabel.setPadding(0, dp(10), 0, 0);
        form.addView(styleLabel);
        EditText backgroundColorField = propertyField(w.backgroundColor == 0 ? "" : String.format(java.util.Locale.ROOT, "#%06X", (0xFFFFFF & w.backgroundColor)), InputType.TYPE_CLASS_TEXT);
        backgroundColorField.setHint("Фон: #RRGGBB; пусто — цвет по типу");
        form.addView(backgroundColorField);
        EditText textColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X", (0xFFFFFF & w.textColor)), InputType.TYPE_CLASS_TEXT);
        textColorField.setHint("Цвет текста: #RRGGBB");
        form.addView(textColorField);
        EditText textSizeField = propertyField(Integer.toString(w.textSizeSp), InputType.TYPE_CLASS_NUMBER);
        textSizeField.setHint("Размер текста, 8–48 sp");
        form.addView(textSizeField);
        android.widget.CheckBox visibleField = new android.widget.CheckBox(this);
        visibleField.setText("Элемент видим на экране"); visibleField.setChecked(w.visible); form.addView(visibleField);
        android.widget.CheckBox frameField = new android.widget.CheckBox(this);
        frameField.setText("Показывать рамку"); frameField.setChecked(w.showFrame); form.addView(frameField);
        android.widget.CheckBox autoResizeField = new android.widget.CheckBox(this);
        autoResizeField.setText("Автоподбор размера текста по ширине"); autoResizeField.setChecked(w.autoResizeText); form.addView(autoResizeField);

        // The full property form can exceed a phone's viewport; keep every field reachable.
        android.widget.ScrollView propertyScroll = new android.widget.ScrollView(this);
        propertyScroll.setFillViewport(true);
        propertyScroll.setClipToPadding(false);
        propertyScroll.addView(form);

        new AlertDialog.Builder(this)
                .setTitle("Свойства: " + w.type)
                .setView(propertyScroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Применить", (dialog, which) -> {
                    w.text = textField.getText().toString();
                    w.connectionInfo = connectionField.getText().toString().trim();
                    if (timerPresetField != null) {
                        int newPreset = readPositiveInt(timerPresetField, w.timerPresetMs, false);
                        w.timerPresetMs = Math.max(1, Math.min(3600000, newPreset));
                        w.timerStartedAt = 0L;
                    }
                    if (counterPresetField != null) {
                        w.counterPreset = Math.max(1, Math.min(1000000, readPositiveInt(counterPresetField, w.counterPreset, false)));
                        if ("FBD CTD".equals(w.type)) w.counterValue = w.counterPreset;
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
                    w.backgroundColor = parseOptionalColor(backgroundColorField.getText().toString(), 0);
                    w.textColor = parseOptionalColor(textColorField.getText().toString(), w.textColor);
                    w.textSizeSp = Math.max(8, Math.min(48, readPositiveInt(textSizeField, w.textSizeSp, false)));
                    w.visible = visibleField.isChecked(); w.showFrame = frameField.isChecked(); w.autoResizeText = autoResizeField.isChecked();
                    persistProject();
                    editor.invalidate();
                }).show();
    }

    private int parseOptionalColor(String text, int fallback) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return fallback == 0 ? 0 : fallback;
        if (!value.startsWith("#")) value = "#" + value;
        if (value.matches("#[0-9a-fA-F]{6}")) {
            try { return Color.parseColor(value); } catch (IllegalArgumentException ignored) { return fallback; }
        }
        Toast.makeText(this, "Цвет задаётся в формате #RRGGBB", Toast.LENGTH_SHORT).show();
        return fallback;
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
        clipboardWidget.backgroundColor = source.backgroundColor; clipboardWidget.textColor = source.textColor; clipboardWidget.textSizeSp = source.textSizeSp;
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
        copy.backgroundColor = clipboardWidget.backgroundColor; copy.textColor = clipboardWidget.textColor; copy.textSizeSp = clipboardWidget.textSizeSp;
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
        if (w.extraFields != null) {
            java.util.Iterator<String> keys = w.extraFields.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                obj.put(key, w.extraFields.get(key));
            }
        }
        obj.put("type", w.type); obj.put("text", w.text);
        obj.put("connectionInfo", w.connectionInfo == null ? "" : w.connectionInfo);
        obj.put("inputValue", w.inputValue); obj.put("buttonState", w.buttonState); obj.put("rsState", w.rsState); obj.put("counterPreset", w.counterPreset); obj.put("counterValue", w.counterValue); obj.put("counterLastInput", w.counterLastInput);
        obj.put("timerPresetMs", w.timerPresetMs);
        obj.put("compareA", w.compareA); obj.put("compareB", w.compareB); obj.put("compareOperator", w.compareOperator);
        obj.put("backgroundColor", w.backgroundColor); obj.put("textColor", w.textColor); obj.put("textSizeSp", w.textSizeSp);
        obj.put("visible", w.visible); obj.put("showFrame", w.showFrame); obj.put("autoResizeText", w.autoResizeText);
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
        try { w.extraFields = new JSONObject(obj.toString()); } catch (Exception ignored) { w.extraFields = new JSONObject(); }
        w.type = obj.optString("type", "Текст"); w.text = obj.optString("text", "Надпись");
        w.connectionInfo = obj.optString("connectionInfo", "");
        w.inputValue = obj.optBoolean("inputValue", false); w.buttonState = obj.optBoolean("buttonState", false); w.rsState = obj.optBoolean("rsState", false); w.counterPreset = Math.max(1, Math.min(1000000, obj.optInt("counterPreset", 1))); w.counterValue = Math.max(0, Math.min(1000000, obj.optInt("counterValue", 0))); w.counterLastInput = obj.optBoolean("counterLastInput", false);
        w.timerPresetMs = Math.max(1, Math.min(3600000, obj.optInt("timerPresetMs", 1000)));
        w.compareA = obj.optDouble("compareA", 0.0); w.compareB = obj.optDouble("compareB", 0.0);
        w.compareOperator = normalizeComparisonOperator(obj.optString("compareOperator", ">"));
        w.backgroundColor = obj.optInt("backgroundColor", 0); w.textColor = obj.optInt("textColor", Color.rgb(31, 41, 55));
        w.textSizeSp = Math.max(8, Math.min(48, obj.optInt("textSizeSp", 14)));
        w.visible = obj.optBoolean("visible", true); w.showFrame = obj.optBoolean("showFrame", true); w.autoResizeText = obj.optBoolean("autoResizeText", false);
        w.x = obj.optInt("x", dp(40)); w.y = obj.optInt("y", dp(50));
        w.w = obj.optInt("w", dp(160)); w.h = obj.optInt("h", dp(58));
        return w;
    }

    private void persistProject() {
        try {
            saveActiveScreen();
            JSONObject project = new JSONObject();
            if (projectExtraFields != null) {
                java.util.Iterator<String> projectKeys = projectExtraFields.keys();
                while (projectKeys.hasNext()) { String key = projectKeys.next(); project.put(key, projectExtraFields.get(key)); }
            }
            JSONArray screenArray = new JSONArray();
            for (Screen screen : screens) {
                JSONObject screenObj = new JSONObject();
                if (screen.extraFields != null) {
                    java.util.Iterator<String> keys = screen.extraFields.keys();
                    while (keys.hasNext()) { String key = keys.next(); screenObj.put(key, screen.extraFields.get(key)); }
                }
                screenObj.put("name", screen.name);
                screenObj.put("backgroundColor", screen.backgroundColor);
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
            project.put("projectName", projectName);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(ACTIVE_PROJECT, project.toString())
                    .putString("project_name", projectName).apply();
        } catch (Exception ignored) {
            Toast.makeText(this, "Не удалось сохранить проект", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadProject() {
        widgets.clear(); screens.clear(); currentScreenIndex = 0;
        projectExtraFields = new JSONObject();
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        projectName = prefs.getString("project_name", projectName);
        String json = prefs.getString(ACTIVE_PROJECT, "[]");
        try {
            Object parsed = new org.json.JSONTokener(json).nextValue();
            if (parsed instanceof JSONObject) {
                JSONObject project = (JSONObject) parsed;
                String importedProjectName = project.optString("projectName", projectName);
                if (importedProjectName != null && !importedProjectName.trim().isEmpty()) projectName = importedProjectName.trim();
                try { projectExtraFields = new JSONObject(project.toString()); } catch (Exception ignored) { projectExtraFields = new JSONObject(); }
                JSONArray screenArray = project.optJSONArray("screens");
                if (screenArray != null) {
                    for (int i = 0; i < screenArray.length(); i++) {
                        JSONObject screenObj = screenArray.getJSONObject(i);
                        Screen screen = new Screen(screenObj.optString("name", "Экран " + (i + 1)));
                        screen.backgroundColor = screenObj.optInt("backgroundColor", Color.rgb(250, 252, 255));
                        try { screen.extraFields = new JSONObject(screenObj.toString()); } catch (Exception ignored) { screen.extraFields = new JSONObject(); }
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
                projectExtraFields = new JSONObject();
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
        int backgroundColor = Color.rgb(250, 252, 255);
        JSONObject extraFields = new JSONObject();
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
        if (index < 0 || index >= widgets.size()) return false;
        if (fbdCacheValid.length != widgets.size() || fbdCacheValues.length != widgets.size()) {
            fbdCacheValid = new boolean[widgets.size()];
            fbdCacheValues = new boolean[widgets.size()];
        }
        if (fbdCacheValid[index]) return fbdCacheValues[index];
        boolean result = evaluateFbdUncached(index, visiting);
        fbdCacheValues[index] = result;
        fbdCacheValid[index] = true;
        return result;
    }

    private boolean evaluateFbdUncached(int index, boolean[] visiting) {
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
        if ("FBD NAND".equals(widget.type)) {
            if (inputs.isEmpty()) return false;
            for (Boolean value : inputs) if (!value) return true;
            return false;
        }
        if ("FBD NOR".equals(widget.type)) {
            if (inputs.isEmpty()) return false;
            for (Boolean value : inputs) if (value) return false;
            return true;
        }
        if ("FBD XOR".equals(widget.type)) {
            boolean result = false;
            for (Boolean value : inputs) result ^= value;
            return result;
        }
        if ("FBD RS".equals(widget.type)) {
            boolean set = !inputs.isEmpty() && inputs.get(0);
            boolean reset = inputs.size() > 1 && inputs.get(1);
            if (reset) widget.rsState = false;
            else if (set) widget.rsState = true;
            return widget.rsState;
        }
        if ("FBD SR".equals(widget.type)) {
            boolean set = !inputs.isEmpty() && inputs.get(0);
            boolean reset = inputs.size() > 1 && inputs.get(1);
            if (set) widget.rsState = true;
            else if (reset) widget.rsState = false;
            return widget.rsState;
        }
        if ("FBD R_TRIG".equals(widget.type)) {
            boolean input = !inputs.isEmpty() && inputs.get(0);
            boolean risingEdge = input && !widget.counterLastInput;
            widget.counterLastInput = input;
            return risingEdge;
        }
        if ("FBD F_TRIG".equals(widget.type)) {
            boolean input = !inputs.isEmpty() && inputs.get(0);
            boolean fallingEdge = !input && widget.counterLastInput;
            widget.counterLastInput = input;
            return fallingEdge;
        }
        if ("FBD CTU".equals(widget.type)) {
            boolean countInput = !inputs.isEmpty() && inputs.get(0);
            boolean resetInput = inputs.size() > 1 && inputs.get(1);
            if (resetInput) {
                widget.counterValue = 0;
            } else if (countInput && !widget.counterLastInput) {
                widget.counterValue = Math.min(1000000, widget.counterValue + 1);
            }
            widget.counterLastInput = countInput;
            return widget.counterValue >= widget.counterPreset;
        }
        if ("FBD CTD".equals(widget.type)) {
            boolean countInput = !inputs.isEmpty() && inputs.get(0);
            boolean loadInput = inputs.size() > 1 && inputs.get(1);
            if (loadInput) {
                widget.counterValue = widget.counterPreset;
            } else if (countInput && !widget.counterLastInput) {
                widget.counterValue = Math.max(0, widget.counterValue - 1);
            }
            widget.counterLastInput = countInput;
            return widget.counterValue <= 0;
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
        if ("FBD TP".equals(widget.type)) {
            boolean input = !inputs.isEmpty() && inputs.get(0);
            long now = SystemClock.elapsedRealtime();
            if (input && !widget.counterLastInput) widget.timerStartedAt = now;
            widget.counterLastInput = input;
            if (widget.timerStartedAt == 0L) return false;
            boolean pulseActive = now - widget.timerStartedAt < widget.timerPresetMs;
            if (!pulseActive) widget.timerStartedAt = 0L;
            return pulseActive;
        }
        if ("FBD TOF".equals(widget.type)) {
            boolean input = !inputs.isEmpty() && inputs.get(0);
            long now = SystemClock.elapsedRealtime();
            if (input) {
                widget.counterLastInput = true;
                widget.timerStartedAt = 0L;
                return true;
            }
            if (widget.counterLastInput) {
                widget.counterLastInput = false;
                widget.timerStartedAt = now;
            }
            if (widget.timerStartedAt == 0L) return false;
            if (now - widget.timerStartedAt >= widget.timerPresetMs) {
                widget.timerStartedAt = 0L;
                return false;
            }
            return true;
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
        boolean buttonState = false;
        boolean rsState = false;
        int counterPreset = 1;
        int counterValue = 0;
        boolean counterLastInput = false;
        int timerPresetMs = 1000;
        long timerStartedAt = 0L;
        double compareA = 0.0, compareB = 0.0;
        String compareOperator = ">";
        int backgroundColor = 0; // 0 keeps the type-specific default
        int textColor = Color.rgb(31, 41, 55);
        int textSizeSp = 14;
        boolean visible = true;
        boolean showFrame = true;
        boolean autoResizeText = false;
        JSONObject extraFields = new JSONObject(); // preserve unknown imported widget metadata for round-trip
        float x, y, w, h;
    }

    private class EditorCanvas extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int selected = -1;
        float downX, downY, startX, startY;
        long downAt;
        boolean dragging;

        EditorCanvas() {
            super(MainActivity.this);
            setBackgroundColor(Color.WHITE);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(screens.isEmpty() ? Color.rgb(250, 252, 255) : screens.get(currentScreenIndex).backgroundColor);
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
                    if (!from.visible || !to.visible || !isFbd(from) || !isFbd(to)) continue;
                    float x1 = from.x + from.w, y1 = from.y + from.h / 2;
                    float x2 = to.x, y2 = to.y + to.h / 2;
                    float mid = (x1 + x2) / 2f;
                    canvas.drawLine(x1, y1, mid, y1, paint);
                    canvas.drawLine(mid, y1, mid, y2, paint);
                    canvas.drawLine(mid, y2, x2, y2, paint);
                }
                paint.setStyle(Paint.Style.FILL);
            }
            // Start a fresh FBD scan for this frame; recursive evaluations share this cache.
            fbdCacheValid = new boolean[widgets.size()];
            fbdCacheValues = new boolean[widgets.size()];
            for (int i = 0; i < widgets.size(); i++) {
                Widget w = widgets.get(i);
                if (!w.visible) continue;
                RectF rect = new RectF(w.x, w.y, w.x + w.w, w.y + w.h);
                paint.setStyle(Paint.Style.FILL);
                if (w.backgroundColor != 0) {
                    paint.setColor(w.backgroundColor);
                } else if (w.type != null && w.type.startsWith("FBD ")) {
                    paint.setColor(Color.rgb(237, 233, 254));
                } else if ("Индикатор".equals(w.type)) {
                    paint.setColor(Color.rgb(220, 252, 231));
                } else if ("Кнопка".equals(w.type)) {
                    paint.setColor(w.buttonState ? Color.rgb(187, 247, 208) : Color.rgb(219, 234, 254));
                } else if ("Задатчик".equals(w.type)) {
                    paint.setColor(Color.rgb(254, 243, 199));
                } else {
                    paint.setColor(Color.WHITE);
                }
                canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                if (w.showFrame || i == selected) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(i == selected ? 2.5f : 1));
                    paint.setColor(i == selected ? Color.rgb(21, 101, 192) : Color.rgb(148, 163, 184));
                    canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(w.textColor);
                paint.setTextSize(dp(w.textSizeSp));
                if (w.type != null && w.type.startsWith("FBD ")) {
                    paint.setColor(w.textColor);
                    paint.setTextSize(dp(w.textSizeSp));
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(24)) / Math.max(4f, w.text.length() * 0.58f))));
                    String blockTitle = "FBD Вход".equals(w.type) ? "Вход " + (w.inputValue ? "1" : "0")
                            : (("FBD CTU".equals(w.type) || "FBD CTD".equals(w.type)) ? ("FBD CTD".equals(w.type) ? "CTD " : "CTU ") + w.counterValue + "/" + w.counterPreset : w.text);
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
                    String indicatorValue = liveValues.get(i);
                    int indicatorColor = Color.rgb(100, 116, 139);
                    if (indicatorValue != null) {
                        if ("ERR".equals(indicatorValue) || "CFG".equals(indicatorValue)) {
                            indicatorColor = Color.rgb(220, 38, 38);
                        } else {
                            try {
                                indicatorColor = Double.parseDouble(indicatorValue) == 0.0
                                        ? Color.rgb(220, 38, 38) : Color.rgb(22, 163, 74);
                            } catch (NumberFormatException ignored) {
                                indicatorColor = Color.rgb(100, 116, 139);
                            }
                        }
                    }
                    paint.setColor(indicatorColor);
                    canvas.drawCircle(w.x + dp(16), w.y + w.h / 2, dp(6), paint);
                    paint.setColor(w.textColor);
                    String indicatorText = w.text + (indicatorValue != null ? ": " + indicatorValue : "");
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(36)) / Math.max(4f, indicatorText.length() * 0.58f))));
                    canvas.drawText(indicatorText, w.x + dp(29), w.y + w.h / 2 + dp(5), paint);
                } else if ("Кнопка".equals(w.type)) {
                    String buttonText = w.text + (w.buttonState ? "  [ВКЛ]" : "  [ВЫКЛ]");
                    paint.setColor(w.textColor);
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(20)) / Math.max(4f, buttonText.length() * 0.58f))));
                    canvas.drawText(buttonText, w.x + dp(10), w.y + w.h / 2 + dp(5), paint);
                } else if ("Задатчик".equals(w.type)) {
                    paint.setColor(w.textColor);
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(18)) / Math.max(4f, w.text.length() * 0.58f))));
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
                    String displayText = w.text + (liveValues.containsKey(i) ? " = " + liveValues.get(i) : "");
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(20)) / Math.max(4f, displayText.length() * 0.58f))));
                    canvas.drawText(displayText, tx, ty, paint);
                }
            }
            for (Widget widget : widgets) {
                if (("FBD Таймер".equals(widget.type) || "FBD TP".equals(widget.type) || "FBD TOF".equals(widget.type)) && widget.timerStartedAt > 0L
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
                downX = x; downY = y; downAt = SystemClock.elapsedRealtime(); dragging = false; selected = -1;
                for (int i = widgets.size() - 1; i >= 0; i--) {
                    Widget w = widgets.get(i);
                    if (!w.visible) continue;
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
                } else if (selected >= 0 && SystemClock.elapsedRealtime() - downAt >= 550L) {
                    // A long press acts like a right-click context menu on desktop HMI editors.
                    showWidgetContextMenu();
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
                    } else if ("Кнопка".equals(picked.type)) {
                        picked.buttonState = !picked.buttonState;
                        persistProject();
                        Toast.makeText(MainActivity.this, picked.text + (picked.buttonState ? ": ВКЛ" : ": ВЫКЛ"), Toast.LENGTH_SHORT).show();
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
