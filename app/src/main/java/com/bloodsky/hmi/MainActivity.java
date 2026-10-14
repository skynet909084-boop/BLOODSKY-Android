package com.bloodsky.hmi;

import android.app.Activity;
import android.app.AlertDialog;
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
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS = "bloodsky_projects";
    private static final String ACTIVE_PROJECT = "active_project";
    private String projectName = "Мой проект";
    private LinearLayout root;
    private EditorCanvas editor;
    private final List<Widget> widgets = new ArrayList<>();
    private final List<Screen> screens = new ArrayList<>();
    private int currentScreenIndex = 0;
    private Widget clipboardWidget;

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

        TextView status = new TextView(this);
        status.setText("\nТекущий этап: базовый Android-каркас и редактор экранов.\nFBD, Modbus RTU/TCP и полный перенос оригинальных компонентов будут добавляться отдельными проверяемыми этапами.");
        status.setTextColor(Color.rgb(71, 85, 105));
        status.setTextSize(14);
        status.setPadding(dp(20), dp(12), dp(20), dp(20));
        root.addView(status);
        setContentView(root);
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
        root.addView(palette);

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
        copy.type = source.type; copy.text = source.text;
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

    private void deleteSelectedWidget() {
        if (editor == null || editor.selected < 0 || editor.selected >= widgets.size()) {
            Toast.makeText(this, "Сначала выбери элемент на экране", Toast.LENGTH_SHORT).show();
            return;
        }
        widgets.remove(editor.selected);
        editor.selected = -1;
        persistProject();
        editor.invalidate();
    }

    private JSONObject widgetToJson(Widget w) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("type", w.type); obj.put("text", w.text);
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
        Screen(String name) { this.name = name; }
    }

    private static class Widget {
        String type;
        String text;
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

            for (int i = 0; i < widgets.size(); i++) {
                Widget w = widgets.get(i);
                RectF rect = new RectF(w.x, w.y, w.x + w.w, w.y + w.h);
                paint.setStyle(Paint.Style.FILL);
                if ("Индикатор".equals(w.type)) {
                    paint.setColor(Color.rgb(220, 252, 231));
                } else if ("Кнопка".equals(w.type)) {
                    paint.setColor(Color.rgb(219, 234, 254));
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
                if ("Индикатор".equals(w.type)) {
                    paint.setColor(Color.rgb(22, 163, 74));
                    canvas.drawCircle(w.x + dp(16), w.y + w.h / 2, dp(6), paint);
                    paint.setColor(Color.rgb(31, 41, 55));
                    canvas.drawText(w.text, w.x + dp(29), w.y + w.h / 2 + dp(5), paint);
                } else {
                    float tx = w.x + dp(10);
                    float ty = w.y + w.h / 2 + dp(5);
                    canvas.drawText(w.text, tx, ty, paint);
                }
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
                if (dragging) persistProject();
                else if (selected >= 0) editSelectedWidget();
                invalidate();
                return true;
            }
            return true;
        }
    }
}
