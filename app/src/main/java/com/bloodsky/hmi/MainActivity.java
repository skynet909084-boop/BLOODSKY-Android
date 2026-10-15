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
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
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
    private static final String BACKUP_PROJECT = "backup_project";
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

    @Override protected void onPause() {
        // Preserve unsaved canvas edits when Android backgrounds or recreates us.
        // Never persist from the home screen: no project has been loaded there yet.
        if (editor != null) persistProject();
        super.onPause();
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

    private int snapToGrid(float value) {
        int step = Math.max(1, dp(20));
        return Math.round(value / step) * step;
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

    private Button legacyMenuButton(String text, Runnable task) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(20);
        button.setTextColor(Color.BLACK);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        GradientDrawable face = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.rgb(190, 190, 190), Color.rgb(132, 132, 132), Color.rgb(190, 190, 190) });
        face.setCornerRadius(dp(2));
        face.setStroke(dp(1), Color.rgb(76, 76, 76));
        button.setBackground(face);
        // Reuse the original Craft toolbar artwork instead of emoji icons.
        String iconName = "💾".equals(text) ? "archive"
                : "Копировать".equals(text) ? "action_copy"
                : "Вставить".equals(text) ? "action_paste"
                : "Удалить".equals(text) ? "basket"
                : "◀".equals(text) ? "arrow_left"
                : "▶".equals(text) ? "arrow_right"
                : "+ Элемент".equals(text) ? "menu_bar_component"
                : "Modbus".equals(text) ? "wifi_rtu"
                : null;
        if (iconName != null) {
            int drawableId = getResources().getIdentifier("kc_" + iconName,
                    "drawable", getPackageName());
            if (drawableId != 0) {
                Drawable icon = getResources().getDrawable(drawableId);
                icon.setBounds(0, 0, dp(19), dp(19));
                button.setCompoundDrawables(icon, null, null, null);
                button.setCompoundDrawablePadding(dp(3));
                if ("💾".equals(text)) button.setText("");
            }
        }
        button.setOnClickListener(v -> task.run());
        return button;
    }

    private void showHome() {
        // Do not discard unsaved editor state if storage fails.
        if (editor != null && !persistProject()) return;
        stopModbusPolling();
        editor = null;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(12), dp(4), dp(12), dp(8));
        root.setBackgroundColor(Color.rgb(35, 35, 35));

        TextView title = new TextView(this);
        title.setText("BLOODSKY");
        title.setTextColor(Color.rgb(237, 176, 59));
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(8), dp(5), dp(8), dp(7));
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView subtitle = new TextView(this);
        subtitle.setText("HMI / SCADA");
        subtitle.setTextColor(Color.rgb(187, 187, 187));
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(24)));

        int menuWidth = Math.max(dp(220), Math.min(dp(300),
                getResources().getDisplayMetrics().widthPixels - dp(36)));
        addLegacyMenuItem("Открыть / создать проект", () -> {
            loadProject();
            showEditor();
        }, menuWidth);
        addLegacyMenuItem("Имя проекта", this::editProjectName, menuWidth);
        addLegacyMenuItem("Экспорт проекта", this::exportProject, menuWidth);
        addLegacyMenuItem("Импорт проекта", this::importProject, menuWidth);
        addLegacyMenuItem("Устройства и Modbus", this::showModbusScanner, menuWidth);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        TextView footer = new TextView(this);
        footer.setText("BLOODSKY  •  интерфейс в стиле Kaskada Craft 1.0.7");
        footer.setTextColor(Color.rgb(237, 176, 59));
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(26)));
        setContentView(root);
    }

    private void addLegacyMenuItem(String label, Runnable task, int width) {
        Button button = legacyMenuButton(label, task);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(48));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(10);
        root.addView(button, params);
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
            if (function == (functionCode | 0x80)) {
                if (pdu.length != 2) throw new IOException("Некорректный ответ с исключением Modbus");
                int exceptionCode = pdu[1] & 0xff;
                throw new IOException("Modbus: " + describeModbusExceptionCode(exceptionCode)
                        + " (код " + exceptionCode + ")");
            }
            if (function != functionCode || pdu.length < 2) throw new IOException("Неожиданный код функции");
            int byteCount = pdu[1] & 0xff;
            if (byteCount != count * 2 || pdu.length != 2 + byteCount) {
                throw new IOException("Размер ответа не совпадает с запросом");
            }
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
        if (editor != null && !persistProject()) return;
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
                // Preserve the previous valid project before replacing it on import.
                android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                android.content.SharedPreferences.Editor changes = prefs.edit();
                String previous = prefs.getString(ACTIVE_PROJECT, null);
                if (previous != null) {
                    try {
                        validateImportedProject(previous);
                        changes.putString(BACKUP_PROJECT, previous);
                    } catch (Exception invalidPrevious) {
                        // Do not replace a valid backup with a damaged project.
                    }
                }
                // Commit the imported document and its backup atomically.
                if (!changes.putString(ACTIVE_PROJECT, json).commit()) throw new IllegalStateException("Could not save imported project");
                loadProject();
                showEditor();
                Toast.makeText(this, "Проект JSON импортирован", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Импорт не выполнен: файл не является допустимым JSON-проектом", Toast.LENGTH_LONG).show();
            }
        }
    }


    private void validateImportedProject(String json) throws Exception {
        Object parsed = ProjectJsonCodec.parse(json);
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
        int currentScreen = ProjectJsonCodec.readIntField(project, "currentScreen", 0);
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

            FbdGraphValidator.validateScreenConnections(screen, screenWidgets);
        }
    }

    private void validateWidgetArray(JSONArray array) throws org.json.JSONException {
        if (array.length() > 2000) throw new IllegalArgumentException("Too many widgets");
        for (int i = 0; i < array.length(); i++) {
            JSONObject widget = array.optJSONObject(i);
            if (widget == null) throw new IllegalArgumentException("Widget must be an object");
            String type = widget.optString("type", "Текст"), label = widget.optString("text", "Надпись");
            if (type.length() > 100 || label.length() > 2000 || widget.optString("connectionInfo", "").length() > 2000) throw new IllegalArgumentException("Widget field too long");
            int x = ProjectJsonCodec.readIntField(widget, "x", 0), y = ProjectJsonCodec.readIntField(widget, "y", 0), w = ProjectJsonCodec.readIntField(widget, "w", 160), h = ProjectJsonCodec.readIntField(widget, "h", 58);
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

    private Button legacyToolbarButton(String text, Runnable task) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(Color.rgb(20, 20, 20));
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setGravity(Gravity.CENTER);
        GradientDrawable face = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.rgb(190, 190, 190), Color.rgb(145, 145, 145), Color.rgb(190, 190, 190) });
        face.setCornerRadius(dp(2));
        face.setStroke(dp(1), Color.rgb(70, 70, 70));
        button.setBackground(face);
        button.setOnClickListener(v -> task.run());
        return button;
    }

    private void showEditor() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(35, 35, 35));

        // Compact charcoal toolbar inspired by Kaskada Craft's icon strip.
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(3), dp(2), dp(3), dp(2));
        top.setBackgroundColor(Color.rgb(35, 35, 35));
        top.addView(legacyToolbarButton("☰", this::showHome),
                new LinearLayout.LayoutParams(dp(42), dp(38)));
        Button rename = legacyToolbarButton(projectName, this::editProjectName);
        top.addView(rename, new LinearLayout.LayoutParams(0, dp(38), 1));
        top.addView(legacyToolbarButton("💾", () -> {
            if (persistProject()) Toast.makeText(this, "Проект сохранён на устройстве", Toast.LENGTH_SHORT).show();
        }), new LinearLayout.LayoutParams(dp(48), dp(38)));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout screenBar = new LinearLayout(this);
        screenBar.setGravity(Gravity.CENTER_VERTICAL);
        screenBar.setPadding(dp(3), dp(1), dp(3), dp(1));
        screenBar.setBackgroundColor(Color.rgb(48, 48, 48));
        screenBar.addView(legacyToolbarButton("◀", this::previousScreen),
                new LinearLayout.LayoutParams(dp(38), dp(34)));
        screenBar.addView(legacyToolbarButton(currentScreenName(), this::renameCurrentScreen),
                new LinearLayout.LayoutParams(0, dp(34), 1));
        screenBar.addView(legacyToolbarButton("Фон", this::editScreenBackground),
                new LinearLayout.LayoutParams(dp(54), dp(34)));
        screenBar.addView(legacyToolbarButton("▶", this::nextScreen),
                new LinearLayout.LayoutParams(dp(38), dp(34)));
        screenBar.addView(legacyToolbarButton("+", this::addScreen),
                new LinearLayout.LayoutParams(dp(38), dp(34)));
        screenBar.addView(legacyToolbarButton("−", this::removeCurrentScreen),
                new LinearLayout.LayoutParams(dp(38), dp(34)));
        root.addView(screenBar, new LinearLayout.LayoutParams(-1, dp(36)));

        LinearLayout toolsBar = new LinearLayout(this);
        toolsBar.setGravity(Gravity.CENTER_VERTICAL);
        toolsBar.setPadding(dp(3), dp(2), dp(3), dp(2));
        toolsBar.setBackgroundColor(Color.rgb(35, 35, 35));
        toolsBar.addView(legacyToolbarButton("+ Элемент", this::showAddWidgetMenu),
                new LinearLayout.LayoutParams(0, dp(36), 1));
        toolsBar.addView(legacyToolbarButton(fbdWireMode ? "Связь: ВКЛ" : "Связать FBD", () -> {
            fbdWireMode = !fbdWireMode;
            wireSource = -1;
            Toast.makeText(this, fbdWireMode ? "Режим связи: выбери исходный FBD-блок, затем целевой" : "Режим связи выключен", Toast.LENGTH_LONG).show();
            showEditor();
        }), new LinearLayout.LayoutParams(0, dp(36), 1));
        toolsBar.addView(legacyToolbarButton("Modbus", this::showModbusDiagnostics),
                new LinearLayout.LayoutParams(dp(78), dp(36)));
        root.addView(toolsBar, new LinearLayout.LayoutParams(-1, dp(40)));

        editor = new EditorCanvas();
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(3), dp(2), dp(3), dp(2));
        bottom.setBackgroundColor(Color.rgb(35, 35, 35));
        bottom.addView(legacyToolbarButton("Свойства", this::editSelectedWidget),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        bottom.addView(legacyToolbarButton("Копировать", this::copySelectedWidget),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        bottom.addView(legacyToolbarButton("Вставить", this::pasteWidget),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        bottom.addView(legacyToolbarButton("Удалить", this::deleteSelectedWidget),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(44)));
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
        try {
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
                        value = "ERR: " + describeModbusError(e);
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
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // The Activity may be shutting down; never leave the poll lock stuck.
            modbusPollInFlight = false;
        }
    }

    private String describeModbusExceptionCode(int code) {
        switch (code) {
            case 1: return "устройство не поддерживает эту функцию";
            case 2: return "недопустимый адрес регистра";
            case 3: return "недопустимое значение в запросе";
            case 4: return "внутренняя ошибка устройства";
            case 5: return "запрос принят, обработка выполняется";
            case 6: return "устройство занято, повторите позже";
            case 8: return "ошибка чётности памяти";
            case 10: return "шлюз не смог установить соединение";
            case 11: return "шлюз не получил ответ от устройства";
            default: return "неизвестное исключение протокола";
        }
    }

    private String describeModbusError(Exception error) {
        if (error instanceof java.net.SocketTimeoutException) return "тайм-аут ответа (3 с)";
        if (error instanceof java.net.ConnectException) return "не удалось подключиться к устройству";
        if (error instanceof java.net.UnknownHostException) return "не удалось определить адрес хоста";
        if (error instanceof java.net.NoRouteToHostException) return "нет маршрута до устройства";
        if (error instanceof java.net.SocketException) return "ошибка сокета/соединение закрыто";
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
        return message.length() > 90 ? message.substring(0, 90) : message;
    }

    private boolean isActiveModbusValue(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty() || normalized.startsWith("err") || normalized.startsWith("cfg")
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

        Widget selectedWidget = widgets.get(editor.selected);
        String titleText = selectedWidget.text == null || selectedWidget.text.trim().isEmpty()
                ? selectedWidget.type : selectedWidget.text;

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(4), dp(4), dp(4), dp(4));
        list.setBackgroundColor(Color.rgb(35, 35, 35));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(list);

        final AlertDialog[] menu = new AlertDialog[1];
        final String[] labels = {"Свойства", "Копировать", "Удалить"};
        final String[] details = {
                "Настроить элемент и привязку",
                "Создать копию элемента",
                "Удалить элемент с экрана"
        };
        final int[] symbols = {0x2699, 0x29C9, 0x2715};

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setMinimumHeight(dp(54));
            row.setBackgroundColor(i % 2 == 0
                    ? Color.rgb(48, 48, 48) : Color.rgb(40, 40, 40));

            TextView symbol = new TextView(this);
            symbol.setText(new String(Character.toChars(symbols[i])));
            symbol.setTextSize(21);
            symbol.setGravity(Gravity.CENTER);
            symbol.setTextColor(i == 2 ? Color.rgb(235, 105, 90) : Color.rgb(237, 176, 59));
            row.addView(symbol, new LinearLayout.LayoutParams(dp(38), -2));

            LinearLayout textColumn = new LinearLayout(this);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            textColumn.setPadding(dp(8), 0, 0, 0);
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(16);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            label.setTextColor(i == 2 ? Color.rgb(235, 105, 90) : Color.rgb(237, 176, 59));
            textColumn.addView(label, new LinearLayout.LayoutParams(-1, -2));

            TextView detail = new TextView(this);
            detail.setText(details[i]);
            detail.setTextSize(12);
            detail.setTextColor(Color.rgb(187, 187, 187));
            textColumn.addView(detail, new LinearLayout.LayoutParams(-1, -2));
            row.addView(textColumn, new LinearLayout.LayoutParams(0, -2, 1));

            row.setOnClickListener(v -> {
                if (menu[0] != null) menu[0].dismiss();
                if (index == 0) editSelectedWidget();
                else if (index == 1) copySelectedWidget();
                else deleteSelectedWidget();
            });
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));

            View separator = new View(this);
            separator.setBackgroundColor(Color.rgb(75, 75, 75));
            list.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
        }

        menu[0] = new AlertDialog.Builder(this)
                .setTitle(titleText)
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .create();
        menu[0].show();
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView dialogTitle = menu[0].findViewById(titleId);
        if (dialogTitle != null) dialogTitle.setTextColor(Color.rgb(237, 176, 59));
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
            if ((state != null && state.startsWith("ERR")) || (state != null && state.startsWith("CFG"))) errorCount++;
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
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(4));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(list);
        final AlertDialog[] picker = new AlertDialog[1];

        for (int i = 0; i < labels.length; i++) {
            if (i == 4) {
                TextView section = new TextView(this);
                section.setText("ФУНКЦИОНАЛЬНЫЕ БЛОКИ (FBD)");
                section.setTextColor(Color.rgb(37, 99, 235));
                section.setTextSize(12);
                section.setTypeface(null, android.graphics.Typeface.BOLD);
                section.setPadding(dp(12), dp(14), dp(12), dp(8));
                list.addView(section, new LinearLayout.LayoutParams(-1, -2));
            }
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(8), dp(4), dp(12), dp(4));
            item.setBackgroundColor(i % 2 == 0
                    ? Color.rgb(248, 250, 252) : Color.WHITE);
            item.addView(new WidgetPreview(types[i], defaults[i]),
                    new LinearLayout.LayoutParams(dp(68), dp(46)));
            TextView itemLabel = new TextView(this);
            itemLabel.setText(labels[i] + "    ›");
            itemLabel.setTextColor(Color.rgb(30, 41, 59));
            itemLabel.setTextSize(15);
            itemLabel.setGravity(Gravity.CENTER_VERTICAL);
            itemLabel.setPadding(dp(10), 0, 0, 0);
            item.addView(itemLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
            item.setOnClickListener(v -> {
                if (picker[0] != null) picker[0].dismiss();
                addWidget(types[index], defaults[index]);
            });
            list.addView(item, new LinearLayout.LayoutParams(-1, -2));
            View separator = new View(this);
            separator.setBackgroundColor(Color.rgb(226, 232, 240));
            list.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        picker[0] = new AlertDialog.Builder(this)
                .setTitle("Добавить элемент")
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .create();
        picker[0].show();
    }

    // Draw actual lightweight element thumbnails without external image dependencies.
    private class WidgetPreview extends View {
        private final String type;
        private final String label;
        private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        WidgetPreview(String type, String label) {
            super(MainActivity.this);
            this.type = type;
            this.label = label;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            String resourceName = "kc_" + ("Индикатор".equals(type) ? "lamp_green"
                    : "Кнопка".equals(type) ? "button"
                    : "Задатчик".equals(type) ? "tab_digital_setters"
                    : "Текст".equals(type) ? "tab_text"
                    : type.startsWith("FBD ") ? "logic_ico" : "no_picture");
            int originalId = getResources().getIdentifier(resourceName, "drawable", getPackageName());
            if (originalId != 0) {
                Drawable original = getResources().getDrawable(originalId);
                int inset = dp(3);
                original.setBounds(inset, inset, getWidth() - inset, getHeight() - inset);
                original.draw(canvas);
                return;
            }
            float width = getWidth(), height = getHeight();
            float left = dp(3), top = dp(5), right = width - dp(3), bottom = height - dp(5);
            previewPaint.setStrokeWidth(dp(1.5f));
            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setColor(Color.rgb(241, 245, 249));
            canvas.drawRoundRect(left, top, right, bottom, dp(5), dp(5), previewPaint);
            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setColor(Color.rgb(71, 85, 105));
            canvas.drawRoundRect(left, top, right, bottom, dp(5), dp(5), previewPaint);
            previewPaint.setStyle(Paint.Style.FILL);
            if ("Индикатор".equals(type)) {
                previewPaint.setColor(Color.rgb(34, 197, 94));
                canvas.drawCircle(width / 2, height / 2, dp(10), previewPaint);
                previewPaint.setStyle(Paint.Style.STROKE);
                previewPaint.setColor(Color.rgb(21, 128, 61));
                canvas.drawCircle(width / 2, height / 2, dp(10), previewPaint);
            } else {
                String symbol = type.startsWith("FBD ") ? label
                        : "Кнопка".equals(type) ? "ПУСК"
                        : "Задатчик".equals(type) ? "−  0  +"
                        : "Текст".equals(type) ? "Текст" : label;
                previewPaint.setStyle(Paint.Style.FILL);
                previewPaint.setColor(Color.rgb(30, 41, 59));
                previewPaint.setTextAlign(Paint.Align.CENTER);
                previewPaint.setTextSize(dp(symbol.length() > 7 ? 9 : 12));
                canvas.drawText(symbol, width / 2, height / 2
                        - (previewPaint.ascent() + previewPaint.descent()) / 2, previewPaint);
            }
        }
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
        try {
            // Clone through the project serializer so all current and future properties are preserved.
            clipboardWidget = widgetFromJson(widgetToJson(widgets.get(editor.selected)));
            Toast.makeText(this, "Элемент скопирован со всеми настройками", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось скопировать элемент", Toast.LENGTH_SHORT).show();
        }
    }

    private void pasteWidget() {
        if (clipboardWidget == null) {
            Toast.makeText(this, "Сначала скопируй элемент", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Widget copy = widgetFromJson(widgetToJson(clipboardWidget));
            int offset = dp(24);
            float maxX = editor == null ? Math.max(0, copy.x + offset) : Math.max(0, editor.getWidth() - copy.w);
            float maxY = editor == null ? Math.max(0, copy.y + offset) : Math.max(0, editor.getHeight() - copy.h);
            copy.x = Math.max(0, Math.min(maxX, copy.x + offset));
            copy.y = Math.max(0, Math.min(maxY, copy.y + offset));
            widgets.add(copy);
            if (editor != null) {
                editor.selected = widgets.size() - 1;
                editor.invalidate();
            }
            persistProject();
            Toast.makeText(this, "Копия добавлена на экран", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось вставить элемент", Toast.LENGTH_SHORT).show();
        }
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
        obj.put("inputValue", w.inputValue); obj.put("buttonState", w.buttonState); obj.put("rsState", w.rsState); obj.put("counterPreset", w.counterPreset); obj.put("counterValue", w.counterValue); obj.put("counterLastInput", w.counterLastInput);
        obj.put("timerPresetMs", w.timerPresetMs);
        obj.put("compareA", w.compareA); obj.put("compareB", w.compareB); obj.put("compareOperator", w.compareOperator);
        obj.put("backgroundColor", w.backgroundColor); obj.put("textColor", w.textColor); obj.put("textSizeSp", w.textSizeSp);
        obj.put("visible", w.visible); obj.put("showFrame", w.showFrame); obj.put("autoResizeText", w.autoResizeText);
        obj.put("x", w.x); obj.put("y", w.y); obj.put("w", w.w); obj.put("h", w.h);
        return ProjectJsonCodec.mergePreservedFields(w.extraFields, obj);
    }

    private JSONArray widgetsToJson(List<Widget> list) throws Exception {
        JSONArray array = new JSONArray();
        for (Widget w : list) array.put(widgetToJson(w));
        return array;
    }

    private Widget widgetFromJson(JSONObject obj) {
        Widget w = new Widget();
        w.extraFields = JsonObjectCopy.deepCopy(obj);
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

    private boolean persistProject() {
        try {
            saveActiveScreen();
            JSONObject project = new JSONObject();
            JSONArray screenArray = new JSONArray();
            for (Screen screen : screens) {
                JSONArray connectionArray = new JSONArray();
                for (FbdConnection connection : screen.connections) {
                    connectionArray.put(FbdConnectionJsonCodec.toJson(
                            connection.source, connection.target, connection.extraFields));
                }
                screenArray.put(ProjectScreenJsonCodec.toJson(
                        screen.name, screen.backgroundColor, widgetsToJson(screen.widgets),
                        connectionArray, screen.extraFields));
            }
            project.put("screens", screenArray);
            project.put("currentScreen", currentScreenIndex);
            project.put("projectName", projectName);
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            android.content.SharedPreferences.Editor changes = prefs.edit();
            String previous = prefs.getString(ACTIVE_PROJECT, null);
            String next = ProjectJsonCodec.serialize(
                    ProjectJsonCodec.mergePreservedFields(projectExtraFields, project));
            // A repeated autosave of identical data must not erase the useful
            // previous revision kept as backup.
            if (previous != null && !previous.equals(next)) {
                try {
                    validateImportedProject(previous);
                    changes.putString(BACKUP_PROJECT, previous);
                } catch (Exception invalidPrevious) {
                    // Preserve the existing last-known-good backup.
                }
            }
            if (!changes.putString(ACTIVE_PROJECT, next)
                    .putString("project_name", projectName).commit()) {
                Toast.makeText(this, "Не удалось сохранить проект", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        } catch (Exception ignored) {
            Toast.makeText(this, "Не удалось сохранить проект", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void loadProject() {
        widgets.clear(); screens.clear(); currentScreenIndex = 0;
        projectExtraFields = new JSONObject();
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        projectName = prefs.getString("project_name", projectName);
        String json = prefs.getString(ACTIVE_PROJECT, "[]");
        // A damaged primary must not silently discard a recoverable project.
        try {
            validateImportedProject(json);
        } catch (Exception invalidPrimary) {
            String backup = prefs.getString(BACKUP_PROJECT, null);
            if (backup != null) {
                try {
                    validateImportedProject(backup);
                    // Repair the primary copy immediately, before opening the editor.
                    // Keep the backup intact in case the next session needs it.
                    if (!prefs.edit().putString(ACTIVE_PROJECT, backup).commit()) {
                        Toast.makeText(this, "Не удалось записать восстановленный проект", Toast.LENGTH_LONG).show();
                        return;
                    }
                    json = backup;
                    Toast.makeText(this, "Проект восстановлен из резервной копии", Toast.LENGTH_LONG).show();
                } catch (Exception invalidBackup) {
                    Toast.makeText(this, "Проект повреждён, резервная копия недоступна", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Проект повреждён, резервная копия недоступна", Toast.LENGTH_LONG).show();
            }
        }
        try {
            Object parsed = ProjectJsonCodec.parse(json);
            if (parsed instanceof JSONObject) {
                JSONObject project = (JSONObject) parsed;
                String importedProjectName = project.optString("projectName", projectName);
                if (importedProjectName != null && !importedProjectName.trim().isEmpty()) projectName = importedProjectName.trim();
                projectExtraFields = JsonObjectCopy.deepCopy(project);
                JSONArray screenArray = project.optJSONArray("screens");
                if (screenArray != null) {
                    for (int i = 0; i < screenArray.length(); i++) {
                        JSONObject screenObj = screenArray.getJSONObject(i);
                        Screen screen = new Screen(screenObj.optString("name", "Экран " + (i + 1)));
                        screen.backgroundColor = screenObj.optInt("backgroundColor", Color.rgb(250, 252, 255));
                        screen.extraFields = JsonObjectCopy.deepCopy(screenObj);
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
                                FbdConnection connection = new FbdConnection(source, target);
                                connection.extraFields = FbdConnectionJsonCodec.preserveLoadedFields(connectionObj);
                                screen.connections.add(connection);
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
        JSONObject extraFields = new JSONObject();
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
        float downX, downY, startX, startY, startW, startH;
        long downAt;
        boolean dragging;
        boolean resizing;

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
                if (i == selected) {
                    // Desktop-style resize grip: drag the lower-right corner to change size.
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.rgb(21, 101, 192));
                    float grip = dp(9);
                    canvas.drawRect(w.x + w.w - grip, w.y + w.h - grip, w.x + w.w, w.y + w.h, paint);
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
                    // Show the original Craft logic icon in each FBD block.
                    int logicIconId = getResources().getIdentifier(
                            "kc_logic_ico", "drawable", getPackageName());
                    if (logicIconId != 0 && w.w >= dp(72)) {
                        Drawable logicIcon = getResources().getDrawable(logicIconId);
                        int iconX = (int) (w.x + w.w - dp(26));
                        int iconY = (int) (w.y + dp(5));
                        logicIcon.setBounds(iconX, iconY, iconX + dp(19), iconY + dp(19));
                        logicIcon.draw(canvas);
                    }
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
                        if (indicatorValue.startsWith("ERR") || indicatorValue.startsWith("CFG")) {
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
                    // Use the actual KaScada lamp artwork on the canvas, not
                    // only in the add-element catalogue. Preserve a vector
                    // fallback if the original resource is unavailable.
                    String lampName = indicatorColor == Color.rgb(22, 163, 74)
                            ? "kc_lamp_green" : indicatorColor == Color.rgb(220, 38, 38)
                            ? "kc_lamp_red" : "kc_lamp_off";
                    int lampId = getResources().getIdentifier(lampName, "drawable", getPackageName());
                    if (lampId != 0) {
                        Drawable lamp = getResources().getDrawable(lampId);
                        int radius = dp(13);
                        int cx = (int) w.x + dp(16);
                        int cy = (int) (w.y + w.h / 2);
                        lamp.setBounds(cx - radius, cy - radius, cx + radius, cy + radius);
                        lamp.draw(canvas);
                    } else {
                        paint.setColor(indicatorColor);
                        canvas.drawCircle(w.x + dp(16), w.y + w.h / 2, dp(6), paint);
                    }
                    paint.setColor(w.textColor);
                    String indicatorText = w.text + (indicatorValue != null ? ": " + indicatorValue : "");
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(36)) / Math.max(4f, indicatorText.length() * 0.58f))));
                    canvas.drawText(indicatorText, w.x + dp(29), w.y + w.h / 2 + dp(5), paint);
                } else if ("Кнопка".equals(w.type)) {
                    // Draw the original Craft button image behind its live label.
                    int buttonImageId = getResources().getIdentifier(
                            w.buttonState ? "kc_button2" : "kc_button",
                            "drawable", getPackageName());
                    if (buttonImageId != 0) {
                        Drawable buttonImage = getResources().getDrawable(buttonImageId);
                        buttonImage.setBounds((int) w.x, (int) w.y,
                                (int) (w.x + w.w), (int) (w.y + w.h));
                        buttonImage.draw(canvas);
                    }
                    String buttonText = w.text + (w.buttonState ? "  [ВКЛ]" : "  [ВЫКЛ]");
                    paint.setColor(w.textColor);
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(20)) / Math.max(4f, buttonText.length() * 0.58f))));
                    canvas.drawText(buttonText, w.x + dp(10), w.y + w.h / 2 + dp(5), paint);
                } else if ("Задатчик".equals(w.type)) {
                    // Original Craft setter artwork behind the adjustable value.
                    int setterId = getResources().getIdentifier(
                            "kc_tab_digital_setters", "drawable", getPackageName());
                    if (setterId != 0) {
                        Drawable setterImage = getResources().getDrawable(setterId);
                        int imageLeft = (int) (w.x + w.w - dp(35));
                        int imageTop = (int) (w.y + dp(3));
                        setterImage.setBounds(imageLeft, imageTop,
                                imageLeft + dp(28), imageTop + dp(23));
                        setterImage.draw(canvas);
                    }
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
            // Keep the canvas free of BLOODSKY-only branding overlays. Visual parity
            // must be driven by the original Kaskada editor chrome, not added captions.
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = x; downY = y; downAt = SystemClock.elapsedRealtime(); dragging = false; resizing = false; selected = -1;
                for (int i = widgets.size() - 1; i >= 0; i--) {
                    Widget w = widgets.get(i);
                    if (!w.visible) continue;
                    if (x >= w.x && x <= w.x + w.w && y >= w.y && y <= w.y + w.h) {
                        selected = i; startX = w.x; startY = w.y; startW = w.w; startH = w.h;
                        float grip = dp(22);
                        resizing = x >= w.x + w.w - grip && y >= w.y + w.h - grip;
                        break;
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
                    if (resizing) {
                        int minW = dp(60), minH = dp(40);
                        float maxW = Math.max(minW, getWidth() - w.x);
                        float maxH = Math.max(minH, getHeight() - w.y);
                        w.w = Math.max(minW, Math.min(maxW, snapToGrid(startW + dx)));
                        w.h = Math.max(minH, Math.min(maxH, snapToGrid(startH + dy)));
                    } else {
                        float maxX = Math.max(0, getWidth() - w.w);
                        float maxY = Math.max(0, getHeight() - w.h);
                        w.x = Math.max(0, Math.min(maxX, snapToGrid(startX + dx)));
                        w.y = Math.max(0, Math.min(maxY, snapToGrid(startY + dy)));
                    }
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
