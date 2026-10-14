package com.bloodsky.hmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

        TextView status = new TextView(this);
        status.setText("\nТекущий этап: базовый Android-каркас и редактор экранов.\nFBD, Modbus RTU/TCP и полный перенос оригинальных компонентов будут добавляться отдельными проверяемыми этапами.");
        status.setTextColor(Color.rgb(71, 85, 105));
        status.setTextSize(14);
        status.setPadding(dp(20), dp(12), dp(20), dp(20));
        root.addView(status);
        setContentView(root);
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
                Object parsed = new org.json.JSONTokener(json).nextValue();
                if (!(parsed instanceof JSONObject) && !(parsed instanceof JSONArray)) throw new IllegalArgumentException("Invalid project JSON");
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(ACTIVE_PROJECT, json).apply();
                loadProject();
                showEditor();
                Toast.makeText(this, "Проект JSON импортирован", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Импорт не выполнен: файл не является допустимым JSON-проектом", Toast.LENGTH_LONG).show();
            }
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
        copy.type = source.type; copy.text = source.text; copy.connectionInfo = source.connectionInfo; copy.inputValue = source.inputValue;
        copy.x = source.x; copy.y = source.y; copy.w = source.w; copy.h = source.h;
        return copy;
    }

    private void activateScreen(int index) {
        saveActiveScreen();
        if (screens.isEmpty()) screens.add(new Screen("Экран 1"));
        currentScreenIndex = Math.max(0, Math.min(index, screens.size() - 1));
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
        connectionField.setHint("Например: Modbus TCP, регистр 40001");
        form.addView(connectionField);

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
                    w.x = Math.max(0, readPositiveInt(xField, Math.round(w.x), true));
                    w.y = Math.max(0, readPositiveInt(yField, Math.round(w.y), true));
                    w.w = Math.max(dp(32), readPositiveInt(widthField, Math.round(w.w), false));
                    w.h = Math.max(dp(24), readPositiveInt(heightField, Math.round(w.h), false));
                    persistProject();
                    editor.invalidate();
                }).show();
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
        copy.x = Math.min(Math.max(0, editor == null ? clipboardWidget.x + dp(24) : editor.getWidth() - clipboardWidget.w),
                clipboardWidget.x + dp(24));
        copy.y = clipboardWidget.y + dp(24);
        copy.w = clipboardWidget.w;
        copy.h = clipboardWidget.h;
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
        persistProject();
        editor.invalidate();
    }

    private JSONObject widgetToJson(Widget w) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("type", w.type); obj.put("text", w.text);
        obj.put("connectionInfo", w.connectionInfo == null ? "" : w.connectionInfo);
        obj.put("inputValue", w.inputValue);
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
        if ("FBD Таймер".equals(widget.type)) return !inputs.isEmpty() && inputs.get(0);
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
                    canvas.drawText(w.text, w.x + dp(29), w.y + w.h / 2 + dp(5), paint);
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
                    canvas.drawText(w.text, tx, ty, paint);
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
