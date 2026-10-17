package com.bloodsky.hmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.speech.tts.TextToSpeech;
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
    private static final String PROJECT_CATALOG = "project_catalog_v1";
    private static final int REQUEST_EXPORT_PROJECT = 7101;
    private static final int REQUEST_IMPORT_PROJECT = 7102;
    private static final int REQUEST_PICK_MEDIA = 7103;
    private static final int REQUEST_EXPORT_PROJECT_ZIP = 7104;
    private EditText pendingMediaField;
    private String projectName = "Мой проект";
    private JSONObject projectExtraFields = new JSONObject();
    private LinearLayout root;
    private EditorCanvas editor;
    private final List<Widget> widgets = new ArrayList<>();
    private final List<Screen> screens = new ArrayList<>();
    private int currentScreenIndex = 0;
    private Widget clipboardWidget;
    private boolean operatorMode = false;
    private boolean fbdWireMode = false;
    private final FbdWireHistory fbdWireHistory = new FbdWireHistory();
    private int wireSource = -1;
    private boolean[] fbdCacheValid = new boolean[0];
    private boolean[] fbdCacheValues = new boolean[0];
    private double[] fbdCacheNumeric = new double[0];
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService modbusExecutor = Executors.newSingleThreadExecutor();
    private final Map<Integer, String> liveValues = new HashMap<>();
    private boolean modbusPollingActive = false;
    private boolean activityResumed = false;
    private final PollSessionGuard pollSession = new PollSessionGuard();
    private volatile boolean modbusPollInFlight = false;
    private final Runnable modbusPollRunnable = new Runnable() {
        @Override public void run() {
            if (!modbusPollingActive || editor == null) return;
            pollBoundWidgets();
            mainHandler.postDelayed(this, 1000L);
        }
    };

    private MediaPlayer mediaPlayer;
    private TextToSpeech textToSpeech;
    private final java.util.Map<String, Bitmap> imageCache = new java.util.HashMap<>();

    private Bitmap loadCachedBitmap(String pathOrUri) {
        if (pathOrUri == null || pathOrUri.trim().isEmpty()) return null;
        if (imageCache.containsKey(pathOrUri)) return imageCache.get(pathOrUri);
        Bitmap bitmap = null;
        try {
            String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
            byte[] bytes = readProjectMediaData(pathOrUri, json);
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = ProjectImageSizing.sampleSize(bounds.outWidth, bounds.outHeight);
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Exception ignored) {}
        long bytesCached = 0;
        for (Bitmap cached : imageCache.values()) if (cached != null) bytesCached += cached.getByteCount();
        if (imageCache.size() >= 128 || (bitmap != null && bytesCached + bitmap.getByteCount() > 32L * 1024 * 1024))
            imageCache.clear();
        imageCache.put(pathOrUri, bitmap);
        return bitmap;
    }

    private Bitmap originalIndicatorBitmap(Widget widget, boolean active) {
        try {
            JSONObject settings = OriginalIndicator.settings(widget.extraFields);
            String value = OriginalIndicator.imageValue(settings, active);
            if (OriginalIndicator.isUserImage(settings, active)) return loadCachedBitmap(value);
            int resource;
            switch (OriginalIndicator.builtinName(value)) {
                case "lamp_green": resource = R.drawable.kc_lamp_green; break;
                case "lamp_red": resource = R.drawable.kc_lamp_red; break;
                case "lamp_yellow": resource = R.drawable.kc_lamp_yellow; break;
                case "lamp_blue": resource = R.drawable.kc_lamp_blue; break;
                case "lamp_off": resource = R.drawable.kc_lamp_off; break;
                default: resource = R.drawable.kc_warning_32;
            }
            return originalBitmap(resource);
        } catch (Exception invalid) { return null; }
    }

    private Bitmap originalBitmap(int resource) {
        String key = "original-resource:" + resource;
        Bitmap cached = imageCache.get(key);
        if (cached == null) {
            cached = BitmapFactory.decodeResource(getResources(), resource);
            imageCache.put(key, cached);
        }
        return cached;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        try {
            textToSpeech = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                    try { textToSpeech.setLanguage(new java.util.Locale("ru", "RU")); } catch (Exception ignored) {}
                }
            });
        } catch (Exception ex) {
            android.util.Log.e("BLOODSKY", "TTS init error", ex);
        }
        showHome();
    }

    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (editor != null) startModbusPolling();
    }

    @Override protected void onPause() {
        activityResumed = false;
        stopModbusPolling();
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (textToSpeech != null) try { textToSpeech.stop(); } catch (Exception ignored) {}
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopModbusPolling();
        editor = null;
        modbusExecutor.shutdownNow();
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (textToSpeech != null) {
            try { textToSpeech.stop(); textToSpeech.shutdown(); } catch (Exception ignored) {}
            textToSpeech = null;
        }
        super.onDestroy();
    }

    private void playSoundAlert(String pathOrUri) {
        try {
            if (mediaPlayer != null) {
                try { mediaPlayer.stop(); } catch (Exception ignored) {}
            try { mediaPlayer.release(); } catch (Exception ignored) {}
                mediaPlayer = null;
            }
            if (pathOrUri != null && pathOrUri.startsWith("content://")) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(this, Uri.parse(pathOrUri));
                mediaPlayer.prepare();
                mediaPlayer.start();
            } else if (pathOrUri != null && !pathOrUri.trim().isEmpty() && !"Аларм".equals(pathOrUri)) {
                String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
                java.io.File file = ProjectMediaService.resolveMediaFile(getFilesDir(), json, pathOrUri);
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(file.getAbsolutePath());
                mediaPlayer.prepare();
                mediaPlayer.start();
            } else {
                ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 80);
                toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350);
            }
        } catch (Exception ex) {
            android.util.Log.e("BLOODSKY", "Sound alert error", ex);
            try {
                ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 80);
                toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 250);
            } catch (Exception ignored) {}
        }
    }

    private void speakVoiceAnnouncement(String text) {
        if (textToSpeech != null && text != null && !text.trim().isEmpty()) {
            try {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bloodsky_tts_" + SystemClock.elapsedRealtime());
            } catch (Exception ex) {
                android.util.Log.e("BLOODSKY", "TTS speak error", ex);
            }
        }
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
        button.setAllCaps(true);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.kc_background_button);
        button.setOnClickListener(v -> task.run());
        return button;
    }

    private void showHome() {
        operatorMode = false;
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        stopModbusPolling();
        editor = null;
        try { readProjectCatalog(); }
        catch (Exception error) { Toast.makeText(this, "Сохранение проекта: " + error.getMessage(), Toast.LENGTH_LONG).show(); }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundResource(R.drawable.kc_main_background_small);
        root.setWeightSum(1.0f);
        TextView title = new TextView(this);
        title.setText("BLOODSKY");
        title.setTextColor(Color.rgb(237, 176, 59));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.topMargin = dp(8);
        root.addView(title, titleParams);
        // Original main_menu.xml order, 300dp width and 0.14 weights.
        int menuWidth = Math.min(dp(300), getResources().getDisplayMetrics().widthPixels);
        addLegacyMenuItem("Создать новый проект", this::createNamedProject, menuWidth);
        addLegacyMenuItem("Загрузить проект", () -> chooseSavedProject(false), menuWidth);
        addLegacyMenuItem("Удалить проект", () -> chooseSavedProject(true), menuWidth);
        addLegacyMenuItem("Экспорт проекта", () -> {
            if (getSharedPreferences(PREFS, MODE_PRIVATE).contains(ACTIVE_PROJECT)) {
                if (loadProject()) exportProject();
            } else Toast.makeText(this, "Сначала создай или загрузи проект", Toast.LENGTH_SHORT).show();
        }, menuWidth);
        addLegacyMenuItem("Импорт проекта", this::importProject, menuWidth);
        addLegacyMenuItem("Выход", this::finish, menuWidth);
        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 0.1f));
        TextView footer = new TextView(this);
        footer.setText("BLOODSKY 0.1.0");
        footer.setTextColor(Color.rgb(255, 248, 243));
        footer.setTextSize(18);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-2, -2));
        setContentView(root);
    }

    private void addLegacyMenuItem(String label, Runnable task, int width) {
        Button button = legacyMenuButton(label, task);
        // The source specifies dp for text rather than sp.
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, 0, 0.14f);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(10);
        root.addView(button, params);
    }

    private boolean storeActiveProject(String json, boolean synchronous) throws Exception {
        String normalized = ProjectCatalog.normalize(json);
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String oldActive = prefs.getString(ACTIVE_PROJECT, null);
        String oldCatalog = prefs.getString(PROJECT_CATALOG, null);
        String oldName = prefs.getString("project_name", null);
        String catalog = ProjectCatalog.upsert(oldCatalog, normalized);
        android.content.SharedPreferences.Editor edit = prefs.edit()
                .putString(ACTIVE_PROJECT, normalized).putString(PROJECT_CATALOG, catalog)
                .putString("project_name", new JSONObject(normalized).optString("projectName", "Мой проект"));
        if (!synchronous) { edit.apply(); return true; }
        if (edit.commit()) return true;
        android.content.SharedPreferences.Editor restore = prefs.edit();
        if (oldActive == null) restore.remove(ACTIVE_PROJECT); else restore.putString(ACTIVE_PROJECT, oldActive);
        if (oldCatalog == null) restore.remove(PROJECT_CATALOG); else restore.putString(PROJECT_CATALOG, oldCatalog);
        if (oldName == null) restore.remove("project_name"); else restore.putString("project_name", oldName);
        if (!restore.commit()) android.util.Log.e("BLOODSKY", "Could not persist restored project preferences");
        return false;
    }

    /**
     * Upgrade old project media before changing its saved identity. The save callback
     * commits ACTIVE_PROJECT and PROJECT_CATALOG together; failed copies leave both
     * the original saved JSON and the old files untouched.
     */
    private String migrateLegacyActiveProject(String stored) throws Exception {
        return LegacyProjectMediaMigrator.migrate(getFilesDir(), stored, newJson -> {
            try { return storeActiveProject(newJson, true); }
            catch (Exception failure) {
                android.util.Log.e("BLOODSKY", "Legacy project ID commit failed", failure);
                return false;
            }
        });
    }

    private String readProjectCatalog() throws Exception {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String active = prefs.getString(ACTIVE_PROJECT, null);
        if (active != null) {
            if (LegacyProjectMediaMigrator.needsMigration(active)) {
                migrateLegacyActiveProject(active);
            } else if (!storeActiveProject(active, true)) {
                throw new IOException("Не удалось сохранить текущий проект");
            }
        }
        return prefs.getString(PROJECT_CATALOG, "{}");
    }

    private void createNamedProject() {
        EditText name = propertyField("", InputType.TYPE_CLASS_TEXT);
        name.setHint("Имя проекта");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Создать новый проект")
                .setView(name).setNegativeButton("Отмена", null).setPositiveButton("Создать", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                readProjectCatalog(); // Migrate/preserve the previous active project first.
                String json = ProjectCatalog.create(name.getText().toString());
                if (!storeActiveProject(json, true)) throw new IOException("Не удалось сохранить проект");
                if (!loadProject()) throw new IOException("Не удалось открыть новый проект");
                dialog.dismiss(); showEditor();
            } catch (Exception error) { name.setError(error.getMessage()); }
        }));
        dialog.show();
    }

    private void chooseSavedProject(boolean deleting) {
        try {
            List<JSONObject> projects = ProjectCatalog.list(readProjectCatalog());
            if (projects.isEmpty()) {
                Toast.makeText(this, "Нет сохранённых проектов", Toast.LENGTH_SHORT).show(); return;
            }
            String[] labels = new String[projects.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = projects.get(i).optString("projectName", "Мой проект");
            new AlertDialog.Builder(this).setTitle(deleting ? "Удалить проект" : "Загрузить проект")
                    .setItems(labels, (dialog, which) -> {
                        JSONObject selected = projects.get(which);
                        if (deleting) {
                            new AlertDialog.Builder(this).setTitle("Удалить проект?")
                                    .setMessage(selected.optString("projectName", "Мой проект"))
                                    .setNegativeButton("Отмена", null).setPositiveButton("Удалить", (confirm, choice) -> deleteSavedProject(selected)).show();
                        } else try {
                            if (!storeActiveProject(selected.toString(), true)) throw new IOException("Не удалось открыть проект");
                            if (!loadProject()) throw new IOException("Не удалось открыть проект");
                            showEditor();
                        } catch (Exception error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
                    }).setNegativeButton("Отмена", null).show();
        } catch (Exception error) { Toast.makeText(this, "Список проектов: " + error.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void deleteSavedProject(JSONObject selected) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String key = ProjectMediaService.getOrGenerateProjectId(selected.toString());
            String oldCatalog = prefs.getString(PROJECT_CATALOG, "{}");
            String active = prefs.getString(ACTIVE_PROJECT, null);
            String oldName = prefs.getString("project_name", null);
            boolean deletingActive = active != null && key.equals(ProjectMediaService.getOrGenerateProjectId(active));
            android.content.SharedPreferences.Editor edit = prefs.edit()
                    .putString(PROJECT_CATALOG, ProjectCatalog.remove(oldCatalog, key));
            if (deletingActive) edit.remove(ACTIVE_PROJECT).remove("project_name");
            if (!edit.commit()) {
                android.content.SharedPreferences.Editor restore = prefs.edit().putString(PROJECT_CATALOG, oldCatalog);
                if (active != null) restore.putString(ACTIVE_PROJECT, active);
                if (oldName != null) restore.putString("project_name", oldName);
                if (!restore.commit()) android.util.Log.e("BLOODSKY", "Could not persist restored catalog");
                throw new IOException("Не удалось удалить проект");
            }
            if (deletingActive) { widgets.clear(); screens.clear(); projectExtraFields = new JSONObject(); clipboardWidget = null; imageCache.clear(); }
            showHome();
        } catch (Exception error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
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

    public static int calculateModbusRtuCrc(byte[] data, int length) {
        int crc = 0xFFFF;
        for (int pos = 0; pos < length; pos++) {
            crc ^= (data[pos] & 0xFF);
            for (int i = 8; i != 0; i--) {
                if ((crc & 0x0001) != 0) {
                    crc >>= 1;
                    crc ^= 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    public static byte[] buildModbusRtuFrame(int unit, int functionCode, int startAddress, int count) {
        byte[] frame = new byte[8];
        frame[0] = (byte) (unit & 0xFF);
        frame[1] = (byte) (functionCode & 0xFF);
        frame[2] = (byte) ((startAddress >> 8) & 0xFF);
        frame[3] = (byte) (startAddress & 0xFF);
        frame[4] = (byte) ((count >> 8) & 0xFF);
        frame[5] = (byte) (count & 0xFF);
        int crc = calculateModbusRtuCrc(frame, 6);
        frame[6] = (byte) (crc & 0xFF);
        frame[7] = (byte) ((crc >> 8) & 0xFF);
        return frame;
    }

    public static class ModbusRtuTransport {
        private final InputStream in;
        private final OutputStream out;

        public ModbusRtuTransport(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        private void readFully(byte[] buffer, int offset, int length) throws IOException {
            int totalRead = 0;
            while (totalRead < length) {
                int count = in.read(buffer, offset + totalRead, length - totalRead);
                if (count < 0) {
                    throw new IOException("Stream closed before reading expected " + length + " bytes (read " + totalRead + ")");
                }
                totalRead += count;
            }
        }

        public byte[] sendAndReceive(int unit, int functionCode, int startAddress, int count) throws IOException {
            byte[] frame = buildModbusRtuFrame(unit, functionCode, startAddress, count);
            out.write(frame);
            out.flush();

            byte[] header = new byte[3];
            readFully(header, 0, 3);

            int respUnit = header[0] & 0xFF;
            int respFunc = header[1] & 0xFF;

            if (respFunc == (functionCode | 0x80)) {
                int exceptionCode = header[2] & 0xFF;
                byte[] crcBuf = new byte[2];
                readFully(crcBuf, 0, 2);
                int expectedCrc = ((crcBuf[1] & 0xFF) << 8) | (crcBuf[0] & 0xFF);
                int calcCrc = calculateModbusRtuCrc(header, 3);
                if (calcCrc != expectedCrc) {
                    throw new IOException("CRC mismatch in Modbus RTU exception response (expected 0x"
                            + String.format(java.util.Locale.ROOT, "%04X", calcCrc) + ", got 0x"
                            + String.format(java.util.Locale.ROOT, "%04X", expectedCrc) + ")");
                }
                throw new IOException("Modbus RTU: " + describeModbusExceptionCodeStatic(exceptionCode) + " (код " + exceptionCode + ")");
            }

            if (respFunc != functionCode) {
                throw new IOException("Unexpected function code in Modbus RTU response: " + respFunc);
            }

            int byteCount = header[2] & 0xFF;
            if (byteCount < 0 || byteCount > 252) {
                throw new IOException("Invalid byte count in Modbus RTU response: " + byteCount);
            }

            byte[] payload = new byte[byteCount];
            readFully(payload, 0, byteCount);

            byte[] crcBuf = new byte[2];
            readFully(crcBuf, 0, 2);
            int expectedCrc = ((crcBuf[1] & 0xFF) << 8) | (crcBuf[0] & 0xFF);

            byte[] fullPdu = new byte[3 + byteCount];
            System.arraycopy(header, 0, fullPdu, 0, 3);
            System.arraycopy(payload, 0, fullPdu, 3, byteCount);

            int calcCrc = calculateModbusRtuCrc(fullPdu, fullPdu.length);
            if (calcCrc != expectedCrc) {
                throw new IOException("CRC mismatch in Modbus RTU response (expected 0x"
                        + String.format(java.util.Locale.ROOT, "%04X", calcCrc) + ", got 0x"
                        + String.format(java.util.Locale.ROOT, "%04X", expectedCrc) + ")");
            }
            return payload;
        }
    }

    private String readModbusHoldingRegisters(String host, int port, int unit, int startAddress, int count) throws Exception {
        return readModbusRegisters(host, port, unit, startAddress, count, 3);
    }

    public static String readModbusRegistersStatic(String host, int port, int unit, int startAddress, int count, int functionCode) throws Exception {
        final int[] values;
        try {
            values = new ModbusTcpClient(host, port).read(unit, startAddress, count, functionCode);
        } catch (ModbusTcpClient.ModbusException ex) {
            throw describeTcpException(ex);
        }
        StringBuilder report = new StringBuilder();
        String funcName = functionCode == 1 ? "coils (FC01)\n" : (functionCode == 2 ? "discrete inputs (FC02)\n" : (functionCode == 3 ? "holding registers (FC03)\n" : "input registers (FC04)\n"));
        report.append("Успешно: ").append(count).append(" ").append(funcName);
        int baseAddr = functionCode == 1 ? 1 : (functionCode == 2 ? 10001 : (functionCode == 3 ? 40001 : 30001));
        for (int i = 0; i < count; i++) {
            report.append(baseAddr + startAddress + i).append("  |  ")
                    .append(values[i]).append("  |  0x")
                    .append(String.format(java.util.Locale.ROOT, "%04X", values[i])).append('\n');
        }
        return report.toString();
    }

    public static boolean writeModbusRegisterStatic(String host, int port, int unit, int address, int value) throws Exception {
        try {
            new ModbusTcpClient(host, port).writeSingle(unit, address, value);
            return true;
        } catch (ModbusTcpClient.ModbusException ex) {
            throw describeTcpException(ex);
        }
    }

    public static boolean writeModbusMultipleRegistersStatic(String host, int port, int unit, int startAddress, int[] values) throws Exception {
        try {
            new ModbusTcpClient(host, port).writeMultiple(unit, startAddress, values);
            return true;
        } catch (ModbusTcpClient.ModbusException ex) {
            throw describeTcpException(ex);
        }
    }

    private static IOException describeTcpException(ModbusTcpClient.ModbusException ex) {
        return new IOException("Modbus: " + describeModbusExceptionCodeStatic(ex.exceptionCode)
                + " (код " + ex.exceptionCode + ")", ex);
    }

    private String readModbusRegisters(String host, int port, int unit, int startAddress, int count, int functionCode) throws Exception {
        return readModbusRegistersStatic(host, port, unit, startAddress, count, functionCode);
    }

    public static String describeModbusExceptionCodeStatic(int code) {
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

    private String describeModbusExceptionCode(int code) {
        return describeModbusExceptionCodeStatic(code);
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

    private void startModbusPolling() {
        stopModbusPolling();
        if (!activityResumed || editor == null) return;
        pollSession.start();
        modbusPollingActive = true;
        mainHandler.postDelayed(modbusPollRunnable, 1000L);
    }

    private void stopModbusPolling() {
        pollSession.stop();
        modbusPollingActive = false;
        mainHandler.removeCallbacks(modbusPollRunnable);
    }

    private void pollBoundWidgets() {
        final long sessionToken = pollSession.token();
        final EditorCanvas sessionEditor = editor;
        if (!pollSession.accepts(sessionToken) || sessionEditor == null) return;
        final List<Widget> snapshotWidgets = new ArrayList<>(widgets);
        final List<String> snapshotBindings = new ArrayList<>();
        for (Widget widget : snapshotWidgets) snapshotBindings.add(widget.connectionInfo);
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
                    if (!pollSession.accepts(sessionToken)) return;
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
                    if (!pollSession.accepts(sessionToken) || !activityResumed
                            || editor != sessionEditor || isFinishing() || isDestroyed()) return;
                    for (Map.Entry<Integer, String> entry : updates.entrySet()) {
                        int index = entry.getKey();
                        if (index < 0 || index >= widgets.size() || index >= snapshotWidgets.size()
                                || widgets.get(index) != snapshotWidgets.get(index)
                                || !java.util.Objects.equals(widgets.get(index).connectionInfo, snapshotBindings.get(index))) continue;
                        Widget w = widgets.get(index);
                        liveValues.put(index, entry.getValue());
                        if ("FBD Вход".equals(w.type)) {
                            w.inputValue = isActiveModbusValue(entry.getValue());
                        } else if ("Звуковой сигнал".equals(w.type)) {
                            boolean active = w.soundActive;
                            if (entry.getValue() != null) {
                                try { active = Double.parseDouble(entry.getValue()) >= w.soundThreshold; } catch (Exception ignored) {}
                            }
                            if (active && !w.lastSoundState) {
                                w.lastSoundState = true;
                                playSoundAlert(w.soundFilePath);
                            } else if (!active) {
                                w.lastSoundState = false;
                            }
                        } else if ("Голосовое сообщение".equals(w.type)) {
                            int voiceIdx = 0;
                            if (entry.getValue() != null) {
                                try { voiceIdx = (int) Math.round(Double.parseDouble(entry.getValue())); } catch (Exception ignored) {}
                            }
                            if (voiceIdx < 0 || voiceIdx >= 16) voiceIdx = 0;
                            if (voiceIdx != w.lastVoiceState) {
                                w.lastVoiceState = voiceIdx;
                                speakVoiceAnnouncement(w.voiceMessages[voiceIdx]);
                            }
                        }
                    }
                    editor.invalidate();
                });
            } finally {
                modbusPollInFlight = false;
            }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            modbusPollInFlight = false;
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

    private void exportProject() {
        persistProject();
        new AlertDialog.Builder(this).setTitle("Экспорт проекта")
                .setItems(new String[]{"ZIP — проект и медиа", "JSON — только настройки"}, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(which == 0 ? "application/zip" : "application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, projectName.replaceAll("[^A-Za-zА-Яа-я0-9._-]", "_")
                            + (which == 0 ? ".bloodsky.zip" : ".bloodsky.json"));
                    try { startActivityForResult(intent, which == 0 ? REQUEST_EXPORT_PROJECT_ZIP : REQUEST_EXPORT_PROJECT); }
                    catch (Exception e) { Toast.makeText(this, "Не удалось открыть выбор файла", Toast.LENGTH_LONG).show(); }
                }).show();
    }

    public byte[] readProjectMediaData(String pathOrUri) throws IOException {
        return readProjectMediaData(pathOrUri, getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}"));
    }

    private byte[] readProjectMediaData(String pathOrUri, String json) throws IOException {
        if (pathOrUri != null && pathOrUri.trim().startsWith("content://")) {
            try (InputStream in = getContentResolver().openInputStream(Uri.parse(pathOrUri.trim()))) {
                return ProjectMediaService.readBoundedMedia(in);
            }
        }
        try (InputStream in = new java.io.FileInputStream(ProjectMediaService.resolveMediaFile(getFilesDir(), json, pathOrUri))) {
            return ProjectMediaService.readBoundedMedia(in);
        }
    }

    private void importProjectBundle(Uri uri) {
        modbusExecutor.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Input stream unavailable");
                ProjectMediaService.importProjectBundle(getFilesDir(), in, json -> {
                    try { validateImportedProject(json); }
                    catch (Exception invalid) { throw new IllegalArgumentException("Invalid imported project", invalid); }
                    try { return storeActiveProject(json, true); }
                    catch (Exception error) { throw new IllegalArgumentException("Could not save project catalog", error); }
                });
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    imageCache.clear();
                    if (loadProject()) {
                        showEditor();
                        Toast.makeText(this, "Проект импортирован", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Импорт выполнен, но открытие проекта не удалось", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception error) {
                android.util.Log.e("BLOODSKY", "Project import failed", error);
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed())
                    Toast.makeText(this, "Импорт не выполнен: " + error.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        });
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

    private void chooseMediaFile(EditText field, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        pendingMediaField = field;
        try {
            startActivityForResult(intent, REQUEST_PICK_MEDIA);
        } catch (Exception ex) {
            pendingMediaField = null;
            Toast.makeText(this, "Не удалось открыть выбор файла", Toast.LENGTH_LONG).show();
        }
    }

    private void addMediaPicker(LinearLayout form, EditText field, String mimeType) {
        Button choose = new Button(this);
        choose.setText("Выбрать файл…");
        choose.setAllCaps(false);
        choose.setOnClickListener(v -> chooseMediaFile(field, mimeType));
        form.addView(choose);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_MEDIA) {
            EditText target = pendingMediaField;
            pendingMediaField = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null || target == null) return;
            Uri selected = data.getData();
            try {
                int readGrant = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if (readGrant == 0) throw new SecurityException("Read access not granted");
                getContentResolver().takePersistableUriPermission(selected, readGrant);
                try (InputStream in = getContentResolver().openInputStream(selected)) {
                    if (in == null) throw new IOException("File unavailable");
                }
                target.setText(selected.toString());
                imageCache.remove(selected.toString());
            } catch (Exception ex) {
                Toast.makeText(this, "Нет постоянного доступа к файлу. Выберите другой файл.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_PROJECT || requestCode == REQUEST_EXPORT_PROJECT_ZIP) {
            final String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
            modbusExecutor.execute(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Output stream unavailable");
                    if (requestCode == REQUEST_EXPORT_PROJECT_ZIP) {
                        ProjectMediaService.exportProjectZipBundle(out, json, source -> readProjectMediaData(source, json));
                    } else out.write(json.getBytes(StandardCharsets.UTF_8));
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed())
                        Toast.makeText(this, "Проект экспортирован", Toast.LENGTH_LONG).show(); });
                } catch (Exception error) {
                    android.util.Log.e("BLOODSKY", "Project export failed", error);
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed())
                        Toast.makeText(this, "Ошибка экспорта: " + error.getMessage(), Toast.LENGTH_LONG).show(); });
                }
            });
        } else if (requestCode == REQUEST_IMPORT_PROJECT) {
            importProjectBundle(uri);
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

    private void alignSelectedWidget(String alignMode) {
        if (editor == null || editor.selected < 0 || editor.selected >= widgets.size()) {
            Toast.makeText(this, "Выбери элемент для выравнивания", Toast.LENGTH_SHORT).show();
            return;
        }
        Widget w = widgets.get(editor.selected);
        float canvasWidth = editor.getWidth() > 0 ? editor.getWidth() : dp(360);
        float canvasHeight = editor.getHeight() > 0 ? editor.getHeight() : dp(640);
        switch (alignMode) {
            case "left": w.x = 0; break;
            case "right": w.x = Math.max(0, canvasWidth - w.w); break;
            case "top": w.y = 0; break;
            case "bottom": w.y = Math.max(0, canvasHeight - w.h); break;
            case "center_h": w.x = Math.max(0, snapToGrid((canvasWidth - w.w) / 2f)); break;
            case "center_v": w.y = Math.max(0, snapToGrid((canvasHeight - w.h) / 2f)); break;
        }
        persistProject();
        if (editor != null) editor.invalidate();
        Toast.makeText(this, "Элемент выровнен", Toast.LENGTH_SHORT).show();
    }

    private void showAlignDialog() {
        if (editor == null || editor.selected < 0 || editor.selected >= widgets.size()) {
            Toast.makeText(this, "Выбери элемент на экране", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] options = {"По левому краю", "По правому краю", "По верхнему краю", "По нижнему краю", "По центру (горизонт.)", "По центру (вертик.)"};
        String[] modes = {"left", "right", "top", "bottom", "center_h", "center_v"};
        new AlertDialog.Builder(this)
                .setTitle("Выравнивание элемента")
                .setItems(options, (dialog, which) -> alignSelectedWidget(modes[which]))
                .setNegativeButton("Отмена", null)
                .show();
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

    public static int requireSettingInt(String text, int min, int max, String label) {
        try {
            if (text == null || !text.trim().matches("[0-9]+")) throw new NumberFormatException();
            int value = Integer.parseInt(text.trim());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + ": допустимо " + min + "–" + max);
        }
    }

    private static String legacySlaveValue(String legacy, String label, String fallback) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?i)" + label + "\\s*:\\s*([0-9]+)").matcher(legacy);
        return m.find() ? m.group(1) : fallback;
    }

    public static JSONObject validatedProjectSettings(JSONObject current, String modeText,
            String idText, String portText, String registers) throws Exception {
        int mode = requireSettingInt(modeText, 0, 1, "Режим");
        int id = requireSettingInt(idText, 1, 247, "Slave ID");
        int port = requireSettingInt(portText, 1, 65535, "Порт");
        JSONObject result = new JSONObject(current.toString());
        String legacy = result.optString("modbusSlave", "");
        if (legacy.matches("(?is).*slave id\\s*:\\s*[0-9]+.*")
                && legacy.matches("(?is).*port\\s*:\\s*[0-9]+.*")) {
            legacy = legacy.replaceFirst("(?i)(slave id\\s*:\\s*)[0-9]+", "Slave ID: " + id)
                    .replaceFirst("(?i)(port\\s*:\\s*)[0-9]+", "Port: " + port);
        } else {
            legacy = "Slave ID: " + id + ", Port: " + port;
        }
        result.put("stationMode", mode);
        result.put("modbusSlaveId", id);
        result.put("modbusSlavePort", port);
        result.put("modbusSlave", legacy);
        result.put("systemRegisters", registers);
        return result;
    }

    private void showProjectSettingsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        form.setPadding(pad, dp(4), pad, 0);

        TextView note = new TextView(this);
        note.setText("Примечание: Настройки станции и Modbus Slave сохраняются в метаданных проекта. Они задают параметры конфигурации, но не запускают фоновый TCP-сервер Modbus Slave.");
        note.setTextColor(Color.rgb(100, 116, 139));
        note.setTextSize(12);
        note.setPadding(0, 0, 0, dp(8));
        form.addView(note);

        TextView stationLabel = new TextView(this);
        stationLabel.setText("Режим станции (0 = Мастер, 1 = Слейв)");
        form.addView(stationLabel);
        EditText stationField = propertyField(Integer.toString(projectExtraFields.optInt("stationMode", 0)), InputType.TYPE_CLASS_NUMBER);
        form.addView(stationField);

        TextView slaveLabel = new TextView(this);
        slaveLabel.setText("Настройки Modbus Slave (ID / Порт)");
        slaveLabel.setPadding(0, dp(10), 0, 0);
        form.addView(slaveLabel);
        String legacySlave = projectExtraFields.optString("modbusSlave", "");
        EditText slaveIdField = propertyField(projectExtraFields.optString("modbusSlaveId",
                legacySlaveValue(legacySlave, "Slave ID", "1")), InputType.TYPE_CLASS_NUMBER);
        slaveIdField.setHint("Slave ID: 1–247");
        form.addView(slaveIdField);
        EditText slavePortField = propertyField(projectExtraFields.optString("modbusSlavePort",
                legacySlaveValue(legacySlave, "Port", "502")), InputType.TYPE_CLASS_NUMBER);
        slavePortField.setHint("Порт: 1–65535");
        form.addView(slavePortField);

        TextView sysRegLabel = new TextView(this);
        sysRegLabel.setText("Системные регистры");
        sysRegLabel.setPadding(0, dp(10), 0, 0);
        form.addView(sysRegLabel);
        EditText sysRegField = propertyField(projectExtraFields.optString("systemRegisters", "SYS_STATUS=0, SYS_TIME=100"), InputType.TYPE_CLASS_TEXT);
        sysRegField.setHint("Системные регистры");
        form.addView(sysRegField);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);

        AlertDialog settings = new AlertDialog.Builder(this)
                .setTitle("Настройки проекта")
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null).create();
        settings.setOnShowListener(ignored -> settings.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                JSONObject updated = validatedProjectSettings(projectExtraFields,
                        stationField.getText().toString(), slaveIdField.getText().toString(),
                        slavePortField.getText().toString(), sysRegField.getText().toString().trim());
                projectExtraFields = updated;
                persistProject();
                settings.dismiss();
                Toast.makeText(this, "Настройки проекта сохранены", Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException ex) {
                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                Toast.makeText(this, "Ошибка сохранения настроек", Toast.LENGTH_LONG).show();
            }
        }));
        settings.show();
    }

    private void showEditor() {
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(35, 35, 35));
        android.widget.FrameLayout area = new android.widget.FrameLayout(this);
        android.widget.ImageButton reveal = editorIcon(R.drawable.kc_menu_app_bar, "Показать панель");
        final View[] toolbar = new View[1];
        toolbar[0] = OriginalEditorToolbar.create(root, currentScreenName(), operatorMode, new OriginalEditorToolbar.Host() {
            public void hide() { toolbar[0].setVisibility(View.GONE); reveal.setVisibility(View.VISIBLE); }
            public void toggleMode() { toggleOperatorMode(); }
            public void previous() { previousScreen(); }
            public void next() { nextScreen(); }
            public void renameScreen() { renameCurrentScreen(); }
            public void calculator() { showOriginalCalculator(); }
            public void menu(View anchor) { showEditorCommands(anchor); }
        });
        root.addView(toolbar[0], new LinearLayout.LayoutParams(-1, -2));
        editor = new EditorCanvas();
        editor.setContentDescription(operatorMode ? "Рабочий экран" : "Редактор экрана");
        area.addView(editor, new android.widget.FrameLayout.LayoutParams(-1, -1));
        reveal.setVisibility(View.GONE);
        reveal.setOnClickListener(v -> { reveal.setVisibility(View.GONE); toolbar[0].setVisibility(View.VISIBLE); });
        android.widget.FrameLayout.LayoutParams revealParams = new android.widget.FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP | Gravity.LEFT);
        revealParams.leftMargin = dp(10); revealParams.topMargin = dp(5);
        area.addView(reveal, revealParams);
        if (!operatorMode) {
            android.widget.ImageButton palette = editorIcon(R.drawable.kc_menu_bar_component, "Добавить элемент");
            palette.setOnClickListener(v -> showAddWidgetMenu());
            android.widget.FrameLayout.LayoutParams paletteParams = new android.widget.FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM | Gravity.LEFT);
            paletteParams.setMargins(dp(15), dp(15), dp(15), dp(15));
            area.addView(palette, paletteParams);
        }
        root.addView(area, new LinearLayout.LayoutParams(-1, 0, 1));
        if (!operatorMode) {
            View actions = OriginalSelectionActions.create(root, this::cutSelectedWidget,
                    this::copySelectedWidget, this::pasteWidget, this::showAlignDialog);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-2, -2);
            actionParams.gravity = Gravity.CENTER_HORIZONTAL;
            root.addView(actions, actionParams);
        }
        setContentView(root); editor.invalidate(); startModbusPolling();
    }

    private android.widget.ImageButton editorIcon(int drawable, String description) {
        android.widget.ImageButton button = new android.widget.ImageButton(this);
        button.setBackgroundColor(Color.TRANSPARENT); button.setImageResource(drawable);
        button.setScaleType(android.widget.ImageView.ScaleType.FIT_XY); button.setPadding(0, 0, 0, 0);
        button.setContentDescription(description); return button;
    }

    private void showOriginalCalculator() {
        if (getFragmentManager().findFragmentByTag("original-calculator") == null)
            new OriginalCalculatorDialog().show(getFragmentManager(), "original-calculator");
    }

    private void toggleOperatorMode() {
        if (!persistProjectNow(true)) return;
        // Editing must not advance timers. Re-entering runtime starts a fresh
        // clock epoch rather than resuming an old saved elapsedRealtime value.
        if (!operatorMode) for (Widget value : widgets) resetFbdTimerRuntime(value);
        operatorMode = !operatorMode; fbdWireMode = false; wireSource = -1;
        showEditor();
    }

    private void showEditorCommands(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        if (!operatorMode) {
            menu.getMenu().add("Имя проекта").setOnMenuItemClickListener(item -> { editProjectName(); return true; });
            menu.getMenu().add("Настройки проекта").setOnMenuItemClickListener(item -> { showProjectSettingsDialog(); return true; });
            menu.getMenu().add("Сохранить проект").setOnMenuItemClickListener(item -> { persistProject(); return true; });
            menu.getMenu().add("Добавить экран").setOnMenuItemClickListener(item -> { addScreen(); return true; });
            menu.getMenu().add("Удалить экран").setOnMenuItemClickListener(item -> { removeCurrentScreen(); return true; });
            menu.getMenu().add("Фон экрана").setOnMenuItemClickListener(item -> { editScreenBackground(); return true; });
            menu.getMenu().add(fbdWireMode ? "Выключить связь FBD" : "Связать FBD").setOnMenuItemClickListener(item -> {
                fbdWireMode = !fbdWireMode; wireSource = -1; showEditor(); return true;
            });
            menu.getMenu().add("Удалить связь FBD…").setOnMenuItemClickListener(item -> {
                showFbdDisconnectDialog(); return true;
            });
            menu.getMenu().add("Инвертировать BOOL-связь FBD…").setOnMenuItemClickListener(item -> {
                showFbdInversionDialog(); return true;
            });
            if (currentScreenIndex >= 0 && currentScreenIndex < screens.size()) {
                Screen current = screens.get(currentScreenIndex);
                if (fbdWireHistory.canUndo(current))
                    menu.getMenu().add("Отменить связь FBD").setOnMenuItemClickListener(item -> {
                        replayFbdWireEdit(false); return true;
                    });
                if (fbdWireHistory.canRedo(current))
                    menu.getMenu().add("Повторить связь FBD").setOnMenuItemClickListener(item -> {
                        replayFbdWireEdit(true); return true;
                    });
            }
        }
        menu.getMenu().add("Modbus").setOnMenuItemClickListener(item -> { showModbusDiagnostics(); return true; });
        menu.getMenu().add("Главное меню").setOnMenuItemClickListener(item -> {
            if (persistProjectNow(true)) showHome(); return true;
        });
        menu.show();
    }

    @Override public void onBackPressed() {
        if (editor != null) {
            if (operatorMode) toggleOperatorMode();
            else if (persistProjectNow(true)) showHome();
        } else super.onBackPressed();
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

    public static Widget copyWidget(Widget source) {
        Widget copy = new Widget();
        copy.type = source.type; copy.text = source.text; copy.connectionInfo = source.connectionInfo; copy.inputValue = source.inputValue; copy.buttonState = source.buttonState; copy.rsState = source.rsState; copy.counterPreset = source.counterPreset; copy.counterValue = source.counterValue; copy.counterLastInput = source.counterLastInput; copy.timerPresetMs = source.timerPresetMs; copy.timerStartedAt = source.timerStartedAt; copy.compareA = source.compareA; copy.compareB = source.compareB; copy.compareOperator = source.compareOperator;
        copy.minValue = source.minValue; copy.maxValue = source.maxValue;
        copy.backgroundColor = source.backgroundColor; copy.textColor = source.textColor; copy.textSizeSp = source.textSizeSp;
        copy.visible = source.visible; copy.showFrame = source.showFrame; copy.autoResizeText = source.autoResizeText;
        for (int i = 0; i < 16; i++) {
            copy.dynTextStates[i] = source.dynTextStates[i];
            copy.dynTextColors[i] = source.dynTextColors[i];
            copy.bitListLabels[i] = source.bitListLabels[i];
            copy.voiceMessages[i] = source.voiceMessages[i];
            copy.imagePathStates[i] = source.imagePathStates[i];
        }
        copy.soundThreshold = source.soundThreshold;
        copy.soundActive = source.soundActive;
        copy.soundFilePath = source.soundFilePath;
        copy.targetScreenIndex = source.targetScreenIndex;
        copy.targetUrl = source.targetUrl;
        copy.lastVoiceState = source.lastVoiceState;
        copy.lastSoundState = source.lastSoundState;
        try { copy.extraFields = new JSONObject(source.extraFields.toString()); } catch (Exception ignored) { copy.extraFields = new JSONObject(); }
        copy.x = source.x; copy.y = source.y; copy.w = source.w; copy.h = source.h;
        return copy;
    }

    private void activateScreen(int index) {
        fbdWireHistory.clear();
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
        fbdWireHistory.clear();
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
                    fbdWireHistory.clear();
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
        if (isFbd(widgets.get(editor.selected))) {
            showFbdBlockContextMenu(editor.selected);
            return;
        }
        showStandardWidgetContextMenu();
    }

    /** Source-ordered FBD actions with existing typed graph guards. */
    private void showFbdBlockContextMenu(final int blockIndex) {
        if (operatorMode || blockIndex < 0 || blockIndex >= widgets.size()) return;
        final Widget widget = widgets.get(blockIndex);
        final boolean timer = "FBD Таймер".equals(widget.type)
                || "FBD TOF".equals(widget.type) || "FBD TP".equals(widget.type);
        final boolean invertible = knownFbdPins(widget.type, true).length == 1
                && "BOOL".equals(knownFbdPinType(widget.type,
                        knownFbdPins(widget.type, true)[0], true));
        final java.util.List<String> labels = new ArrayList<>();
        labels.add("Соединить с…");
        if (invertible) labels.add("Инвертировать связь BOOL…");
        labels.add("Отсоединить выход");
        if (timer) labels.add("Режим / единицы времени");
        labels.add("Свойства");
        labels.add("Другие действия");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(widget.text == null ? widget.type : widget.text)
                .setItems(labels.toArray(new String[0]), (dialog, choice) -> {
                    if (blockIndex >= widgets.size() || widgets.get(blockIndex) != widget) {
                        Toast.makeText(this, "FBD изменён: выбери блок заново", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String label = labels.get(choice);
                    if ("Соединить с…".equals(label)) {
                        fbdWireMode = true;
                        wireSource = blockIndex;
                        Toast.makeText(this, "Выбери целевой блок FBD", Toast.LENGTH_LONG).show();
                    } else if ("Инвертировать связь BOOL…".equals(label)) {
                        showFbdInversionDialog(blockIndex);
                    } else if ("Отсоединить выход".equals(label)) {
                        showFbdDisconnectDialog(blockIndex);
                    } else if ("Режим / единицы времени".equals(label)
                            || "Свойства".equals(label)) {
                        editor.selected = blockIndex;
                        editSelectedWidget();
                    } else {
                        editor.selected = blockIndex;
                        showStandardWidgetContextMenu();
                    }
                    if (editor != null) editor.invalidate();
                }).setNegativeButton("Отмена", null).show();
    }

    private void showStandardWidgetContextMenu() {
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
        final String[] labels = {"Свойства", "Выравнивание", "Копировать", "Удалить"};
        final String[] details = {
                "Настроить элемент и привязку",
                "Выровнять на холсте",
                "Создать копию элемента",
                "Удалить элемент с экрана"
        };
        final int[] symbols = {0x2699, 0x21D4, 0x29C9, 0x2715};

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
            symbol.setTextColor(i == 3 ? Color.rgb(235, 105, 90) : Color.rgb(237, 176, 59));
            row.addView(symbol, new LinearLayout.LayoutParams(dp(38), -2));

            LinearLayout textColumn = new LinearLayout(this);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            textColumn.setPadding(dp(8), 0, 0, 0);
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(16);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            label.setTextColor(i == 3 ? Color.rgb(235, 105, 90) : Color.rgb(237, 176, 59));
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
                else if (index == 1) showAlignDialog();
                else if (index == 2) copySelectedWidget();
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
        OriginalComponentPalette.show(this, this::addWidget,
                this::showFbdPalette, this::editScreenBackground);
    }

    private void showFbdPalette() {
        OriginalFbdPalette.show(this, this::addWidget, this::showAddWidgetMenu, this::showExtendedWidgetMenu);
    }

    private void showExtendedWidgetMenu() {
        final String[] labels = {
                "Текст / надпись", "Кнопка", "Индикатор", "Задатчик",
                "Динамический текст", "Битовый список", "Шкала / Стрелка", "Прогресс-бар", "График / Тренд", "Двухпозиционный индикатор",
                "Звуковой сигнал / Аларм", "Переход на экран / Ссылка", "Многопозиционное фото", "Голосовое сообщение",
                "FBD — Вход", "FBD — И", "FBD — ИЛИ", "FBD — НЕ",
                "FBD — NAND", "FBD — NOR", "FBD — XOR",
                "FBD — TON (задержка включения)", "FBD — TP (импульс)", "FBD — TOF (задержка выключения)",
                "FBD — Сравнение", "FBD — Сложение (+)", "FBD — Вычитание (-)", "FBD — Умножение (*)", "FBD — Деление (/)",
                "FBD — POW (степень)", "FBD — SQRT (корень)", "FBD — SIN", "FBD — COS", "FBD — TAN",
                "FBD — FloatToInt", "FBD — IntToFloat",
                "FBD — MOVE", "FBD — SEL (селектор)", "FBD — ABS", "FBD — MIN / MAX",
                "FBD — RS", "FBD — SR", "FBD — CTU (счёт вверх)",
                "FBD — CTD (счёт вниз)", "FBD — R_TRIG", "FBD — F_TRIG",
                "FBD — Craft CTU 16 бит (+, −, R)",
                "FBD — Craft T (переключатель по фронту)",
                "FBD — Craft BLINK (мигание)"
        };
        final String[] types = {
                "Текст", "Кнопка", "Индикатор", "Задатчик",
                "Динамический текст", "Битовый список", "Шкала / Стрелка", "Прогресс-бар", "График / Тренд", "Двухпозиционный индикатор",
                "Звуковой сигнал", "Переход на экран", "Многопозиционное фото", "Голосовое сообщение",
                "FBD Вход", "FBD И", "FBD ИЛИ", "FBD НЕ",
                "FBD NAND", "FBD NOR", "FBD XOR",
                "FBD Таймер", "FBD TP", "FBD TOF",
                "FBD Сравнение", "FBD Сложение", "FBD Вычитание", "FBD Умножение", "FBD Деление",
                "FBD POW", "FBD SQRT", "FBD SIN", "FBD COS", "FBD TAN",
                "FBD FloatToInt", "FBD IntToFloat",
                "FBD MOVE", "FBD SEL", "FBD ABS", "FBD MIN_MAX",
                "FBD RS", "FBD SR", "FBD CTU",
                "FBD CTD", "FBD R_TRIG", "FBD F_TRIG",
                "FBD CRAFT CTU", "FBD CRAFT T", "FBD CRAFT BLINK"
        };
        final String[] defaults = {
                "Надпись", "Кнопка", "Индикатор", "Задатчик: 0",
                "Динамический текст", "Битовый список", "Шкала", "Уровень", "Тренд", "Состояние",
                "Аларм", "Переход -> Экран 1", "Фото", "Голос",
                "Вход", "И", "ИЛИ", "НЕ", "NAND", "NOR", "XOR",
                "TON", "TP", "TOF", "A > B", "A + B", "A - B", "A * B", "A / B",
                "POW", "SQRT", "SIN", "COS", "TAN",
                "Float->Int", "Int->Float",
                "MOVE", "SEL", "ABS", "MIN/MAX",
                "RS", "SR", "CTU", "CTD", "R_TRIG", "F_TRIG",
                "CTU 16 bit", "T", "BLINK"
        };
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(4));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(list);
        final AlertDialog[] picker = new AlertDialog[1];

        for (int i = 0; i < labels.length; i++) {
            if (i == 14) {
                TextView section = new TextView(this);
                section.setText("ФУНКЦИОНАЛЬНЫЕ БЛОКИ (FBD)");
                section.setTextColor(Color.rgb(37, 99, 235));
                section.setTextSize(12);
                section.setTypeface(null, android.graphics.Typeface.BOLD);
                section.setPadding(dp(12), dp(14), dp(12), dp(8));
                list.addView(section, new LinearLayout.LayoutParams(-1, -2));
            }
            final int index = i;
            TextView item = new TextView(this);
            item.setText(labels[i] + "    ›");
            item.setTextColor(Color.rgb(30, 41, 59));
            item.setTextSize(16);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), dp(12), dp(12), dp(12));
            item.setBackgroundColor(i % 2 == 0
                    ? Color.rgb(248, 250, 252) : Color.WHITE);
            item.setMinHeight(dp(48));
            item.setOnClickListener(v -> {
                if (picker[0] != null) picker[0].dismiss();
                addWidget(types[index], defaults[index]);
            });
            if (OriginalIndicator.supports(types[i]) || "Кнопка".equals(types[i])) {
                // Preview with verified original APK drawable resources.
                // Button artwork is the same original background used by legacyMenuButton.
                LinearLayout previewRow = new LinearLayout(this);
                previewRow.setOrientation(LinearLayout.HORIZONTAL);
                previewRow.setGravity(Gravity.CENTER_VERTICAL);
                android.widget.ImageView preview = new android.widget.ImageView(this);
                boolean buttonPreview = "Кнопка".equals(types[i]);
                preview.setImageResource(buttonPreview
                        ? R.drawable.kc_background_button : R.drawable.kc_lamp_off);
                preview.setContentDescription(buttonPreview
                        ? "Кнопка: оригинальный фон" : "Индикатор: выключен");
                preview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams previewSize = new LinearLayout.LayoutParams(dp(40), dp(40));
                previewSize.setMargins(dp(12), dp(4), dp(8), dp(4));
                previewRow.addView(preview, previewSize);
                previewRow.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
                previewRow.setOnClickListener(v -> item.performClick());
                previewRow.setMinimumHeight(dp(48));
                list.addView(previewRow, new LinearLayout.LayoutParams(-1, -2));
            } else {
                list.addView(item, new LinearLayout.LayoutParams(-1, -2));
            }
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

    /**
     * Find space for a newly created FBD block without changing existing layout.
     * Deterministic row-major search; work in Canvas pixels (same units as Widget geometry).
     * This is a BLOODSKY editor usability adaptation, not a copied Craft placement rule.
     */
    public static float[] freeFbdPlacement(List<Widget> existing, float canvasWidth,
                                           float canvasHeight, float width, float height,
                                           float originX, float originY, float gap) {
        if (existing == null || !Float.isFinite(canvasWidth) || !Float.isFinite(canvasHeight)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || !Float.isFinite(originX) || !Float.isFinite(originY)
                || !Float.isFinite(gap) || width <= 0 || height <= 0
                || canvasWidth <= width || canvasHeight <= height) return null;
        float separation = Math.max(1f, gap);
        float step = Math.max(8f, separation);
        float startX = Math.max(0f, originX), startY = Math.max(0f, originY);
        for (int row = 0; row < 200; row++) {
            float y = startY + row * step;
            if (y + height + separation > canvasHeight) break;
            for (int col = 0; col < 200; col++) {
                float x = startX + col * step;
                if (x + width + separation > canvasWidth) break;
                boolean occupied = false;
                for (Widget old : existing) {
                    if (old == null || !old.visible) continue;
                    if (x < old.x + old.w + separation && x + width + separation > old.x
                            && y < old.y + old.h + separation && y + height + separation > old.y) {
                        occupied = true;
                        break;
                    }
                }
                if (!occupied) return new float[]{x, y};
            }
        }
        return null;
    }

    private void addWidget(String type, String text) {
        Widget w = new Widget();
        w.type = type;
        w.text = text;
        w.x = dp(40) + (widgets.size() % 4) * dp(24);
        w.y = dp(50) + (widgets.size() % 6) * dp(22);
        w.w = OriginalIndicator.supports(type) ? dp(40) : dp(160);
        w.h = OriginalIndicator.supports(type) ? dp(40) : dp(58);
        if (type.equals("Задатчик")) { w.w = dp(190); w.h = dp(62); }
        if (type.equals("Шкала / Стрелка") || type.equals("График / Тренд")) { w.w = dp(200); w.h = dp(110); }
        if (type.equals("Прогресс-бар") || type.equals("Битовый список")) { w.w = dp(180); w.h = dp(64); }
        if (type.startsWith("FBD ")) {
            w.w = dp(150); w.h = dp(76);
            if (editor != null && editor.getWidth() > 0 && editor.getHeight() > 0) {
                float[] empty = freeFbdPlacement(widgets, editor.getWidth(), editor.getHeight(),
                        w.w, w.h, dp(40), dp(50), dp(18));
                if (empty != null) { w.x = empty[0]; w.y = empty[1]; }
                else {
                    // The old fallback inserted the block at a hard-coded offset,
                    // overlapping other blocks while claiming that no space was
                    // available. Fail without changing project/history instead.
                    Toast.makeText(this,
                            "Нет свободного места для FBD-блока: освободи место на экране",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
        if ("FBD CTD".equals(type)) w.counterValue = w.counterPreset;
        // Only a successfully placed element may invalidate undo history and
        // graph output caches. A rejected insert leaves the project unchanged.
        fbdWireHistory.clear();
        liveValues.clear();
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
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        return field;
    }

    private boolean selectIndicatorImage(Widget widget, boolean active, boolean user, String value) {
        if (!widgets.contains(widget) || !OriginalIndicator.supports(widget.type)) return false;
        JSONObject old = widget.extraFields;
        JSONObject oldProject = projectExtraFields;
        try {
            widget.extraFields = OriginalIndicator.withImage(old, active, user, value);
            if (!persistProjectNow(true)) {
                widget.extraFields = old; projectExtraFields = oldProject; saveActiveScreen();
                Toast.makeText(this, "Не удалось сохранить изображение", Toast.LENGTH_LONG).show();
                return false;
            }
            if (editor != null) editor.invalidate();
            return true;
        } catch (Exception invalid) {
            widget.extraFields = old; projectExtraFields = oldProject;
            Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show(); return false;
        }
    }

    private void showIndicatorImages(Widget widget) {
        OriginalIndicatorImages.show(this, new OriginalIndicatorImages.Host() {
            public Bitmap preview(boolean active) { return originalIndicatorBitmap(widget, active); }
            public boolean select(boolean active, boolean user, String value) {
                return selectIndicatorImage(widget, active, user, value);
            }
            public void chooseCustom(boolean active, Runnable changed) {
                LinearLayout form = new LinearLayout(MainActivity.this); form.setOrientation(LinearLayout.VERTICAL);
                EditText path = propertyField("", InputType.TYPE_CLASS_TEXT);
                path.setHint("Файл / content URI"); path.setContentDescription("Своё изображение индикатора");
                form.addView(path); addMediaPicker(form, path, "image/*");
                AlertDialog custom = new AlertDialog.Builder(MainActivity.this).setTitle("Своё изображение")
                        .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Применить", null).create();
                custom.setOnShowListener(shown -> custom.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String selected = path.getText().toString().trim();
                    imageCache.remove(selected);
                    if (selected.isEmpty() || loadCachedBitmap(selected) == null) {
                        Toast.makeText(MainActivity.this, "Выберите доступное изображение", Toast.LENGTH_LONG).show(); return;
                    }
                    if (selectIndicatorImage(widget, active, true, selected)) {custom.dismiss();changed.run();}
                }));
                custom.show();
            }
        });
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

        if (OriginalIndicator.supports(w.type)) {
            Button images = new Button(this); images.setText("Сменить изображение"); images.setAllCaps(false);
            images.setOnClickListener(v -> showIndicatorImages(w)); form.addView(images);
        }

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
        connectionField.setHint("tcp://192.168.1.10:502/1/40001 (FC03/FC04) или fbd://N (номер блока с 1)");
        form.addView(connectionField);

        final android.widget.CheckBox fbdInputField;
        if ("FBD Вход".equals(w.type)) {
            fbdInputField = new android.widget.CheckBox(this);
            fbdInputField.setText("Постоянное значение FBD-входа (1 / 0)");
            fbdInputField.setContentDescription("Значение входа FBD");
            fbdInputField.setChecked(w.inputValue);
            form.addView(fbdInputField);
        } else fbdInputField = null;

        final EditText[] dynStateFields = new EditText[4];
        if ("Динамический текст".equals(w.type)) {
            TextView dynLabel = new TextView(this);
            dynLabel.setText("Тексты состояний (0–3)");
            dynLabel.setPadding(0, dp(10), 0, 0);
            form.addView(dynLabel);
            for (int k = 0; k < 4; k++) {
                dynStateFields[k] = propertyField(w.dynTextStates[k], InputType.TYPE_CLASS_TEXT);
                dynStateFields[k].setHint("Текст состояния " + k);
                form.addView(dynStateFields[k]);
            }
        }

        final EditText[] bitLabelFields = new EditText[4];
        if ("Битовый список".equals(w.type)) {
            TextView bitLabelHeader = new TextView(this);
            bitLabelHeader.setText("Названия битов (0–3)");
            bitLabelHeader.setPadding(0, dp(10), 0, 0);
            form.addView(bitLabelHeader);
            for (int k = 0; k < 4; k++) {
                bitLabelFields[k] = propertyField(w.bitListLabels[k], InputType.TYPE_CLASS_TEXT);
                bitLabelFields[k].setHint("Подпись бита " + k);
                form.addView(bitLabelFields[k]);
            }
        }

        final EditText soundThresholdField;
        final EditText soundFilePathField;
        if ("Звуковой сигнал".equals(w.type)) {
            TextView soundHeader = new TextView(this);
            soundHeader.setText("Параметры звука (порог срабатывания и имя файла)");
            soundHeader.setPadding(0, dp(10), 0, 0);
            form.addView(soundHeader);
            soundThresholdField = propertyField(Double.toString(w.soundThreshold), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            soundThresholdField.setHint("Порог (например 1.0)");
            form.addView(soundThresholdField);
            soundFilePathField = propertyField(w.soundFilePath, InputType.TYPE_CLASS_TEXT);
            soundFilePathField.setHint("Файл / content URI или 'Аларм'");
            form.addView(soundFilePathField);
            addMediaPicker(form, soundFilePathField, "audio/*");
        } else { soundThresholdField = null; soundFilePathField = null; }

        final EditText targetScreenIndexField;
        final EditText targetUrlField;
        if ("Переход на экран".equals(w.type)) {
            TextView screenNavHeader = new TextView(this);
            screenNavHeader.setText("Целевой экран (индекс 1..N) и URL");
            screenNavHeader.setPadding(0, dp(10), 0, 0);
            form.addView(screenNavHeader);
            targetScreenIndexField = propertyField(Integer.toString(w.targetScreenIndex + 1), InputType.TYPE_CLASS_NUMBER);
            targetScreenIndexField.setHint("Номер экрана (начиная с 1)");
            form.addView(targetScreenIndexField);
            targetUrlField = propertyField(w.targetUrl, InputType.TYPE_CLASS_TEXT);
            targetUrlField.setHint("URL для перехода (необязательно)");
            form.addView(targetUrlField);
        } else { targetScreenIndexField = null; targetUrlField = null; }

        final EditText[] imagePathFields = new EditText[w.imagePathStates.length];
        if ("Многопозиционное фото".equals(w.type)) {
            TextView imgHeader = new TextView(this);
            imgHeader.setText("Пути к изображениям состояний (0–15)");
            imgHeader.setPadding(0, dp(10), 0, 0);
            form.addView(imgHeader);
            for (int k = 0; k < imagePathFields.length; k++) {
                imagePathFields[k] = propertyField(w.imagePathStates[k], InputType.TYPE_CLASS_TEXT);
                imagePathFields[k].setHint("Изображение " + k + " (файл / URI)");
                form.addView(imagePathFields[k]);
                addMediaPicker(form, imagePathFields[k], "image/*");
            }
        }

        final EditText[] voiceMsgFields = new EditText[w.voiceMessages.length];
        if ("Голосовое сообщение".equals(w.type)) {
            TextView voiceHeader = new TextView(this);
            voiceHeader.setText("Тексты голосовых оповещений (0–15)");
            voiceHeader.setPadding(0, dp(10), 0, 0);
            form.addView(voiceHeader);
            for (int k = 0; k < voiceMsgFields.length; k++) {
                voiceMsgFields[k] = propertyField(w.voiceMessages[k], InputType.TYPE_CLASS_TEXT);
                voiceMsgFields[k].setHint("Голос " + k);
                form.addView(voiceMsgFields[k]);
            }
        }

        final EditText timerPresetField;
        final android.widget.Spinner timerUnitSelector;
        final android.widget.Spinner timerModeSelector;
        if ("FBD Таймер".equals(w.type) || "FBD TP".equals(w.type)
                || "FBD TOF".equals(w.type) || "FBD CRAFT BLINK".equals(w.type)) {
            TextView timerModeLabel = new TextView(this);
            boolean originalBlink = "FBD CRAFT BLINK".equals(w.type);
            timerModeLabel.setText(originalBlink ? "Режим Craft: BLINK"
                    : "Режим таймера (TON / TOF / TP)");
            form.addView(timerModeLabel);
            timerModeSelector = new android.widget.Spinner(this);
            timerModeSelector.setContentDescription("Режим таймера FBD");
            timerModeSelector.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item,
                    originalBlink ? new String[]{"BLINK"} : FbdDurationUnits.modes()));
            String initialMode = FbdDurationUnits.modeForType(w.type);
            timerModeSelector.setSelection("TOF".equals(initialMode) ? 1 : "TP".equals(initialMode) ? 2 : 0);
            form.addView(timerModeSelector);

            TextView timerLabel = new TextView(this);
            timerLabel.setText("Время таймера (исходное значение хранится точно в мс)");
            timerLabel.setPadding(0, dp(10), 0, 0);
            form.addView(timerLabel);
            String[] unitValues = {"MS", "SEK", "MIN"};
            String initialUnit = FbdDurationUnits.normalize(w.extraFields.optString("bloodskyFbdTimeUnit", "MS"));
            int initialUnitIndex = "SEK".equals(initialUnit) ? 1 : "MIN".equals(initialUnit) ? 2 : 0;
            timerPresetField = propertyField(FbdDurationUnits.display(w.timerPresetMs, initialUnit),
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                            | InputType.TYPE_NUMBER_FLAG_SIGNED);
            timerPresetField.setContentDescription(initialUnitIndex == 0 ? "Время таймера, мс"
                    : "Время таймера, " + (initialUnitIndex == 1 ? "сек" : "мин"));
            form.addView(timerPresetField);
            timerUnitSelector = new android.widget.Spinner(this);
            timerUnitSelector.setContentDescription("Единица времени FBD");
            timerUnitSelector.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"мс", "сек", "мин"}));
            timerUnitSelector.setSelection(initialUnitIndex);
            final int[] lastUnit = {initialUnitIndex};
            timerUnitSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position == lastUnit[0]) return;
                    try {
                        int oldMilliseconds = FbdDurationUnits.parse(
                                timerPresetField.getText().toString(), unitValues[lastUnit[0]], w.timerPresetMs);
                        timerPresetField.setText(FbdDurationUnits.display(oldMilliseconds, unitValues[position]));
                        timerPresetField.setContentDescription(position == 0 ? "Время таймера, мс"
                                : "Время таймера, " + (position == 1 ? "сек" : "мин"));
                    } catch (IllegalArgumentException ignored) {
                        // Keep invalid text visible so Apply reports an error.
                    }
                    lastUnit[0] = position;
                }
            });
            form.addView(timerUnitSelector);
        } else {
            timerPresetField = null;
            timerUnitSelector = null;
            timerModeSelector = null;
        }
        final EditText counterPresetField;
        if ("FBD CTU".equals(w.type) || "FBD CTD".equals(w.type)) {
            TextView counterLabel = new TextView(this);
            counterLabel.setText("Уставка счётчика " + ("FBD CTD".equals(w.type) ? "CTD" : "CTU"));
            counterLabel.setPadding(0, dp(10), 0, 0);
            form.addView(counterLabel);
            counterPresetField = propertyField(Integer.toString(w.counterPreset), InputType.TYPE_CLASS_NUMBER);
            counterPresetField.setContentDescription("Уставка счётчика");
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
            compareLeftField.setContentDescription("Сравнение A");
            compareOperatorField.setContentDescription("Оператор сравнения");
            compareRightField.setContentDescription("Сравнение B");
            form.addView(compareLeftField);
            form.addView(compareOperatorField);
            form.addView(compareRightField);
        } else {
            compareLeftField = null;
            compareOperatorField = null;
            compareRightField = null;
        }

        final EditText minValField;
        final EditText maxValField;
        if ("Шкала / Стрелка".equals(w.type) || "Прогресс-бар".equals(w.type) || "График / Тренд".equals(w.type)) {
            TextView rangeLabel = new TextView(this);
            rangeLabel.setText("Диапазон шкалы (минимум / максимум)");
            rangeLabel.setPadding(0, dp(10), 0, 0);
            form.addView(rangeLabel);
            minValField = propertyField(Double.toString(w.minValue), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            minValField.setHint("Минимум (например 0)");
            form.addView(minValField);
            maxValField = propertyField(Double.toString(w.maxValue), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            maxValField.setHint("Максимум (например 100)");
            form.addView(maxValField);
        } else {
            minValField = null;
            maxValField = null;
        }

        TextView geometryLabel = new TextView(this);
        geometryLabel.setText("Положение и размер (пиксели)");
        geometryLabel.setPadding(0, dp(10), 0, 0);
        form.addView(geometryLabel);

        EditText xField = propertyField(Float.toString(w.x), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        xField.setHint("X");
        xField.setContentDescription("Положение X");
        form.addView(xField);
        EditText yField = propertyField(Float.toString(w.y), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        yField.setHint("Y");
        yField.setContentDescription("Положение Y");
        form.addView(yField);
        EditText widthField = propertyField(Float.toString(w.w), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        widthField.setHint("Ширина");
        widthField.setContentDescription("Ширина элемента");
        form.addView(widthField);
        EditText heightField = propertyField(Float.toString(w.h), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        heightField.setHint("Высота");
        heightField.setContentDescription("Высота элемента");
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

        android.widget.ScrollView propertyScroll = new android.widget.ScrollView(this);
        propertyScroll.setFillViewport(true);
        propertyScroll.setClipToPadding(false);
        propertyScroll.addView(form);

        AlertDialog properties = new AlertDialog.Builder(this)
                .setTitle("Свойства: " + w.type)
                .setView(propertyScroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Применить", null).create();
        properties.setOnShowListener(shown -> properties.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(clicked -> {
                    final WidgetGeometryEdits geometry;
                    final FbdPropertyEdits fbd;
                    try {
                        geometry = WidgetGeometryEdits.parse(xField.getText().toString(), yField.getText().toString(),
                                widthField.getText().toString(), heightField.getText().toString());
                        String timerDurationMs = null;
                        if (timerPresetField != null) {
                            String unit = new String[]{"MS", "SEK", "MIN"}[timerUnitSelector.getSelectedItemPosition()];
                            timerDurationMs = Integer.toString(FbdDurationUnits.parse(
                                    timerPresetField.getText().toString(), unit, w.timerPresetMs));
                        }
                        fbd = FbdPropertyEdits.parse(timerDurationMs,
                                counterPresetField == null ? null : counterPresetField.getText().toString(),
                                compareLeftField == null ? null : compareLeftField.getText().toString(),
                                compareOperatorField == null ? null : compareOperatorField.getText().toString(),
                                compareRightField == null ? null : compareRightField.getText().toString());
                    } catch (IllegalArgumentException invalid) {
                        Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    w.text = textField.getText().toString();
                    w.connectionInfo = connectionField.getText().toString().trim();
                    if (fbdInputField != null) w.inputValue = fbdInputField.isChecked();
                    if ("Динамический текст".equals(w.type)) {
                        for (int k = 0; k < 4; k++) {
                            if (dynStateFields[k] != null) {
                                w.dynTextStates[k] = dynStateFields[k].getText().toString();
                            }
                        }
                    }
                    if ("Битовый список".equals(w.type)) {
                        for (int k = 0; k < 4; k++) {
                            if (bitLabelFields[k] != null) {
                                w.bitListLabels[k] = bitLabelFields[k].getText().toString();
                            }
                        }
                    }
                    if (soundThresholdField != null) {
                        w.soundThreshold = readDouble(soundThresholdField, w.soundThreshold);
                        w.soundFilePath = soundFilePathField.getText().toString();
                    }
                    if (targetScreenIndexField != null) {
                        int idx = readPositiveInt(targetScreenIndexField, w.targetScreenIndex + 1, false) - 1;
                        w.targetScreenIndex = Math.max(0, idx);
                        w.targetUrl = targetUrlField.getText().toString();
                    }
                    if ("Многопозиционное фото".equals(w.type)) {
                        for (int k = 0; k < imagePathFields.length; k++) {
                            if (imagePathFields[k] != null) {
                                w.imagePathStates[k] = imagePathFields[k].getText().toString();
                            }
                        }
                    }
                    if ("Голосовое сообщение".equals(w.type)) {
                        for (int k = 0; k < voiceMsgFields.length; k++) {
                            if (voiceMsgFields[k] != null) {
                                w.voiceMessages[k] = voiceMsgFields[k].getText().toString();
                            }
                        }
                    }
                    if (timerPresetField != null) {
                        String oldMode = FbdDurationUnits.modeForType(w.type);
                        String newMode = String.valueOf(timerModeSelector.getSelectedItem());
                        w.timerPresetMs = fbd.timer;
                        w.type = "FBD CRAFT BLINK".equals(w.type) ? w.type
                                : FbdDurationUnits.typeForMode(newMode);
                        // Keep custom labels intact, refresh only an untouched mode label.
                        if (w.text != null && w.text.equals(oldMode)) w.text = newMode;
                        w.timerStartedAt = 0L;
                        w.counterLastInput = false;
                        if ("FBD CRAFT BLINK".equals(w.type))
                            w.craftBlinkState = FbdCraftNativeTimer.State.initial();
                        try {
                            String unit = new String[]{"MS", "SEK", "MIN"}[timerUnitSelector.getSelectedItemPosition()];
                            // An untouched old MS project must not acquire an extra field.
                            if (!"MS".equals(unit) || w.extraFields.has("bloodskyFbdTimeUnit"))
                                w.extraFields.put("bloodskyFbdTimeUnit", unit);
                        } catch (org.json.JSONException ignored) {}
                    }
                    if (counterPresetField != null) {
                        w.counterPreset = fbd.counter;
                        if ("FBD CTD".equals(w.type)) w.counterValue = w.counterPreset;
                    }
                    if (compareLeftField != null) {
                        w.compareA = fbd.left;
                        w.compareB = fbd.right;
                        w.compareOperator = fbd.operator;
                        w.text = Double.toString(w.compareA) + " " + w.compareOperator + " " + Double.toString(w.compareB);
                    }
                    if (minValField != null) {
                        w.minValue = readDouble(minValField, w.minValue);
                        w.maxValue = readDouble(maxValField, w.maxValue);
                    }
                    w.x = geometry.x; w.y = geometry.y;
                    w.w = geometry.width; w.h = geometry.height;
                    w.backgroundColor = parseOptionalColor(backgroundColorField.getText().toString(), 0);
                    w.textColor = parseOptionalColor(textColorField.getText().toString(), w.textColor);
                    w.textSizeSp = Math.max(8, Math.min(48, readPositiveInt(textSizeField, w.textSizeSp, false)));
                    w.visible = visibleField.isChecked(); w.showFrame = frameField.isChecked(); w.autoResizeText = autoResizeField.isChecked();
                    persistProject();
                    editor.invalidate();
                    properties.dismiss();
                }));
        properties.show();
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
        if (copySelectedWidgetToClipboard())
            Toast.makeText(this, "Элемент скопирован со всеми настройками", Toast.LENGTH_SHORT).show();
    }

    private boolean copySelectedWidgetToClipboard() {
        if (editor == null || editor.selected < 0 || editor.selected >= widgets.size()) {
            Toast.makeText(this, "Сначала выбери элемент на экране", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            clipboardWidget = widgetFromJson(widgetToJson(widgets.get(editor.selected)));
            return true;
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось скопировать элемент", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void cutSelectedWidget() {
        // Never remove the source if serializing its complete clipboard copy failed.
        if (copySelectedWidgetToClipboard()) deleteSelectedWidget();
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
            fbdWireHistory.clear();
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
        double newValue = delta;
        try {
            double value = Double.parseDouble(valueText.replace(',', '.'));
            newValue = value + delta;
            widget.text = prefix + newValue;
        } catch (NumberFormatException ignored) {
            widget.text = prefix + delta;
        }
        persistProject();
        BindingRequest request = parseBinding(-1, widget.connectionInfo);
        if (request != null && request.functionCode == 3) {
            final double valToWrite = newValue;
            final String oldText = current;
            modbusExecutor.execute(() -> {
                try {
                    boolean ok = writeModbusRegisterStatic(request.host, request.port, request.unit, request.address, (int) valToWrite);
                    if (!ok) throw new IOException("Устройство отклонило запись регистра");
                } catch (Exception ex) {
                    android.util.Log.e("BLOODSKY", "Modbus write failed", ex);
                    runOnUiThread(() -> {
                        widget.text = oldText;
                        persistProject();
                        if (editor != null) editor.invalidate();
                        Toast.makeText(MainActivity.this, "Ошибка записи Modbus: " + (ex.getMessage() != null ? ex.getMessage() : "ошибка связи"), Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
        Toast.makeText(MainActivity.this, "Значение: " + widget.text, Toast.LENGTH_SHORT).show();
    }

    private void deleteSelectedWidget() {
        if (editor == null || editor.selected < 0 || editor.selected >= widgets.size()) {
            Toast.makeText(this, "Сначала выбери элемент на экране", Toast.LENGTH_SHORT).show();
            return;
        }
        int removed = editor.selected;
        fbdWireHistory.clear();
        widgets.remove(removed);
        // A positional fbd://N reference must never silently switch to a
        // different source after deletion. Missing sources render CFG.
        for (Widget survivor : widgets) {
            survivor.connectionInfo = FbdResultBinding.afterWidgetRemoved(
                    survivor.connectionInfo, removed);
        }
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

    public static JSONObject widgetToJson(Widget w) throws Exception {
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
        obj.put("timerPresetMs", w.timerPresetMs); obj.put("timerStartedAt", w.timerStartedAt);
        obj.put("compareA", w.compareA); obj.put("compareB", w.compareB); obj.put("compareOperator", w.compareOperator);
        obj.put("minValue", w.minValue); obj.put("maxValue", w.maxValue);
        obj.put("backgroundColor", w.backgroundColor); obj.put("textColor", w.textColor); obj.put("textSizeSp", w.textSizeSp);
        obj.put("visible", w.visible); obj.put("showFrame", w.showFrame); obj.put("autoResizeText", w.autoResizeText);
        obj.put("x", w.x); obj.put("y", w.y); obj.put("w", w.w); obj.put("h", w.h);

        obj.put("soundThreshold", w.soundThreshold);
        obj.put("soundActive", w.soundActive);
        obj.put("soundFilePath", w.soundFilePath == null ? "Аларм" : w.soundFilePath);
        obj.put("targetScreenIndex", w.targetScreenIndex);
        obj.put("targetUrl", w.targetUrl == null ? "" : w.targetUrl);

        JSONArray dynStatesArr = new JSONArray();
        JSONArray dynColorsArr = new JSONArray();
        JSONArray bitLabelsArr = new JSONArray();
        JSONArray voiceMessagesArr = new JSONArray();
        JSONArray imagePathStatesArr = new JSONArray();
        for (int i = 0; i < 16; i++) {
            dynStatesArr.put(w.dynTextStates[i] == null ? "Состояние " + i : w.dynTextStates[i]);
            dynColorsArr.put(w.dynTextColors[i]);
            bitLabelsArr.put(w.bitListLabels[i] == null ? "Бит " + i : w.bitListLabels[i]);
            voiceMessagesArr.put(w.voiceMessages[i] == null ? "Сообщение " + i : w.voiceMessages[i]);
            imagePathStatesArr.put(w.imagePathStates[i] == null ? "Картинка " + i : w.imagePathStates[i]);
        }
        obj.put("dynTextStates", dynStatesArr);
        obj.put("dynTextColors", dynColorsArr);
        obj.put("bitListLabels", bitLabelsArr);
        obj.put("voiceMessages", voiceMessagesArr);
        obj.put("imagePathStates", imagePathStatesArr);
        return obj;
    }

    private JSONArray widgetsToJson(List<Widget> list) throws Exception {
        JSONArray array = new JSONArray();
        for (Widget w : list) array.put(widgetToJson(w));
        return array;
    }

    public static Widget widgetFromJson(JSONObject obj) {
        Widget w = new Widget();
        try { w.extraFields = new JSONObject(obj.toString()); } catch (Exception ignored) { w.extraFields = new JSONObject(); }
        w.type = obj.optString("type", "Текст"); w.text = obj.optString("text", "Надпись");
        w.connectionInfo = obj.optString("connectionInfo", "");
        w.inputValue = obj.optBoolean("inputValue", false); w.buttonState = obj.optBoolean("buttonState", false); w.rsState = obj.optBoolean("rsState", false); w.counterPreset = Math.max(1, Math.min(1000000, obj.optInt("counterPreset", 1))); w.counterValue = Math.max(0, Math.min(1000000, obj.optInt("counterValue", 0))); w.counterLastInput = obj.optBoolean("counterLastInput", false);
        w.timerPresetMs = Math.max(1, Math.min(3600000, obj.optInt("timerPresetMs", 1000)));
        w.timerStartedAt = obj.optLong("timerStartedAt", 0L);
        w.compareA = obj.optDouble("compareA", 0.0); w.compareB = obj.optDouble("compareB", 0.0);
        w.compareOperator = normalizeComparisonOperatorStatic(obj.optString("compareOperator", ">"));
        w.minValue = obj.optDouble("minValue", 0.0); w.maxValue = obj.optDouble("maxValue", 100.0);
        w.backgroundColor = obj.optInt("backgroundColor", 0); w.textColor = obj.optInt("textColor", Color.rgb(31, 41, 55));
        w.textSizeSp = Math.max(8, Math.min(48, obj.optInt("textSizeSp", 14)));
        w.visible = obj.optBoolean("visible", true); w.showFrame = obj.optBoolean("showFrame", true); w.autoResizeText = obj.optBoolean("autoResizeText", false);
        w.soundThreshold = obj.optDouble("soundThreshold", 1.0); w.soundActive = obj.optBoolean("soundActive", false); w.soundFilePath = obj.optString("soundFilePath", "Аларм");
        w.targetScreenIndex = obj.optInt("targetScreenIndex", 0); w.targetUrl = obj.optString("targetUrl", "");
        w.x = (float) obj.optDouble("x", 40); w.y = (float) obj.optDouble("y", 50);
        w.w = (float) obj.optDouble("w", 160); w.h = (float) obj.optDouble("h", 58);

        JSONArray dynStatesArr = obj.optJSONArray("dynTextStates");
        JSONArray dynColorsArr = obj.optJSONArray("dynTextColors");
        JSONArray bitLabelsArr = obj.optJSONArray("bitListLabels");
        JSONArray voiceMessagesArr = obj.optJSONArray("voiceMessages");
        JSONArray imagePathStatesArr = obj.optJSONArray("imagePathStates");
        for (int i = 0; i < 16; i++) {
            w.dynTextStates[i] = (dynStatesArr != null && i < dynStatesArr.length()) ? dynStatesArr.optString(i, "Состояние " + i) : "Состояние " + i;
            w.dynTextColors[i] = (dynColorsArr != null && i < dynColorsArr.length()) ? dynColorsArr.optInt(i, 0) : 0;
            w.bitListLabels[i] = (bitLabelsArr != null && i < bitLabelsArr.length()) ? bitLabelsArr.optString(i, "Бит " + i) : "Бит " + i;
            w.voiceMessages[i] = (voiceMessagesArr != null && i < voiceMessagesArr.length()) ? voiceMessagesArr.optString(i, "Сообщение " + i) : "Сообщение " + i;
            w.imagePathStates[i] = (imagePathStatesArr != null && i < imagePathStatesArr.length()) ? imagePathStatesArr.optString(i, "Картинка " + i) : "Картинка " + i;
        }
        return w;
    }

    private static String normalizeComparisonOperatorStatic(String value) {
        String operator = value == null ? ">" : value.trim();
        return operator.equals(">") || operator.equals("<") || operator.equals(">=")
                || operator.equals("<=") || operator.equals("==") || operator.equals("!=") ? operator : ">";
    }

    public static void saveProjectAtomic(java.io.File targetFile, String json) throws java.io.IOException {
        java.io.File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        java.io.File tempFile = new java.io.File(targetFile.getAbsolutePath() + ".tmp");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        }
        if (!tempFile.renameTo(targetFile)) {
            if (targetFile.exists() && !targetFile.delete()) {
                throw new java.io.IOException("Could not delete target file for atomic overwrite");
            }
            if (!tempFile.renameTo(targetFile)) {
                throw new java.io.IOException("Could not rename temp project file to target");
            }
        }
    }

    public static String loadProjectAtomic(java.io.File targetFile) throws java.io.IOException {
        if (!targetFile.exists()) {
            java.io.File tempFile = new java.io.File(targetFile.getAbsolutePath() + ".tmp");
            if (tempFile.exists()) targetFile = tempFile;
            else throw new java.io.FileNotFoundException("Project file missing: " + targetFile);
        }
        try (java.io.FileInputStream in = new java.io.FileInputStream(targetFile);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = in.read(chunk)) != -1) {
                if (buffer.size() + count > 8 * 1024 * 1024) throw new IllegalArgumentException("Project file too large");
                buffer.write(chunk, 0, count);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static void exportProjectToZip(OutputStream outputStream, String projectJson) throws java.io.IOException {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(outputStream)) {
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("TableNameProject.json");
            zos.putNextEntry(entry);
            byte[] bytes = projectJson.getBytes(StandardCharsets.UTF_8);
            zos.write(bytes, 0, bytes.length);
            zos.closeEntry();
            zos.finish();
        }
    }

    public static String importProjectFromZip(InputStream inputStream) throws Exception {
        return importProjectFromZip(inputStream, null);
    }

    public static String importProjectFromZip(InputStream inputStream, Map<String, byte[]> extractedMedia) throws Exception {
        byte[] projectJsonBytes = null;
        java.util.Set<String> entryNames = new java.util.HashSet<>();
        long totalUncompressedBytes = 0;

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(inputStream)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new IllegalArgumentException("Unsafe entry path in ZIP archive: " + name);
                }
                if (!entryNames.add(name.toLowerCase(java.util.Locale.ROOT))) {
                    throw new IllegalArgumentException("Duplicate entry name in ZIP archive: " + name);
                }
                ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int count;
                while ((count = zis.read(chunk)) != -1) {
                    totalUncompressedBytes += count;
                    if (totalUncompressedBytes > 10 * 1024 * 1024) {
                        throw new IllegalArgumentException("ZIP uncompressed size exceeds limit");
                    }
                    entryBuffer.write(chunk, 0, count);
                }
                zis.closeEntry();
                if ("TableNameProject.json".equalsIgnoreCase(name) || (!name.startsWith("media/") && name.endsWith(".json"))) {
                    if (projectJsonBytes != null) {
                        throw new IllegalArgumentException("Multiple JSON project files in archive");
                    }
                    projectJsonBytes = entryBuffer.toByteArray();
                } else if (name.startsWith("media/") && extractedMedia != null && !entry.isDirectory()) {
                    extractedMedia.put(name.substring("media/".length()), entryBuffer.toByteArray());
                }
            }
        }
        if (projectJsonBytes == null) {
            throw new IllegalArgumentException("No project JSON found in ZIP archive");
        }
        String json = new String(projectJsonBytes, StandardCharsets.UTF_8);
        validateImportedProjectStatic(json);
        return json;
    }

    private void persistProject() { persistProjectNow(false); }

    private boolean persistProjectNow(boolean synchronous) {
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
                    connectionArray.put(connectionToJson(connection));
                }
                screenObj.put("connections", connectionArray);
                screenArray.put(screenObj);
            }
            project.put("screens", screenArray);
            project.put("currentScreen", currentScreenIndex);
            project.put("projectName", projectName);
            project = new JSONObject(ProjectMediaService.ensureProjectHasId(project.toString()));
            projectExtraFields = new JSONObject(project.toString());
            return storeActiveProject(project.toString(), synchronous);
        } catch (Exception ignored) {
            Toast.makeText(this, "Не удалось сохранить проект", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean loadProject() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String json = prefs.getString(ACTIVE_PROJECT, "[]");
        try {
            if (LegacyProjectMediaMigrator.needsMigration(json))
                json = migrateLegacyActiveProject(json);
        } catch (Exception failure) {
            android.util.Log.e("BLOODSKY", "Legacy project migration failed", failure);
            Toast.makeText(this, "Не удалось перенести старые файлы проекта. Проект не изменён.",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        fbdWireHistory.clear();
        imageCache.clear();
        widgets.clear(); screens.clear(); currentScreenIndex = 0;
        projectExtraFields = new JSONObject();
        projectName = prefs.getString("project_name", projectName);
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
                            if (canLoadFbdConnection(connectionObj)
                                    && source < screen.widgets.size() && target < screen.widgets.size()
                                    && isFbd(screen.widgets.get(source)) && isFbd(screen.widgets.get(target))) {
                                screen.connections.add(connectionFromJson(connectionObj));
                            }
                        }
                        screens.add(screen);
                    }
                    currentScreenIndex = Math.max(0, Math.min(project.optInt("currentScreen", 0), Math.max(0, screens.size() - 1)));
                }
            } else if (parsed instanceof JSONArray) {
                projectExtraFields = new JSONObject();
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
        // elapsedRealtime belongs to the running device, not a saved project.
        // Retain presets and unknown fields, discard only timer runtime epoch.
        for (Screen loaded : screens) for (Widget saved : loaded.widgets)
            resetFbdTimerRuntime(saved);
        for (Widget w : screens.get(currentScreenIndex).widgets) widgets.add(copyWidget(w));
        return true;
    }

    private void validateImportedProject(String json) throws Exception {
        validateImportedProjectStatic(json);
    }

    private void validateWidgetArray(JSONArray array) {
        validateWidgetArrayStatic(array);
    }

    public static void validateImportedProjectStatic(String json) throws Exception {
        org.json.JSONTokener tokener = new org.json.JSONTokener(json);
        Object parsed = tokener.nextValue();
        if (tokener.nextClean() != 0) throw new IllegalArgumentException("Trailing content after JSON");
        if (parsed instanceof JSONArray) {
            validateWidgetArrayStatic((JSONArray) parsed);
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
            validateWidgetArrayStatic(screenWidgets);
            totalWidgets += screenWidgets.length();
            if (totalWidgets > 2000) throw new IllegalArgumentException("Too many widgets");

            JSONArray connections = screen.optJSONArray("connections");
            if (connections == null) continue;
            if (connections.length() > 4000) throw new IllegalArgumentException("Too many connections");
            int nodeCount = screenWidgets.length();
            List<List<Integer>> adjacency = new ArrayList<>();
            int[] indegree = new int[nodeCount];
            for (int node = 0; node < nodeCount; node++) adjacency.add(new ArrayList<>());
            java.util.HashMap<Long, java.util.HashSet<String>> pinsByPair = new java.util.HashMap<>();
            java.util.HashSet<String> occupiedTargetPins = new java.util.HashSet<>();
            for (int j = 0; j < connections.length(); j++) {
                JSONObject edge = connections.optJSONObject(j);
                if (edge == null) throw new IllegalArgumentException("Connection must be an object");
                if (!canLoadFbdConnection(edge)) throw new IllegalArgumentException("Invalid FBD pin metadata or endpoints");
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
                if (!hasCompatibleKnownFbdPins(edge, sourceWidget.optString("type", ""),
                        targetWidget.optString("type", ""))) {
                    throw new IllegalArgumentException("Invalid FBD pin direction or type");
                }
                String targetPin = canonicalFbdPinName(edge.optString("targetPin", ""));
                if (!targetPin.isEmpty() && !occupiedTargetPins.add(target + ":"
                        + targetPin)) {
                    throw new IllegalArgumentException("Multiple FBD wires drive the same target pin");
                }
                long edgeKey = (((long) source) << 32) | (target & 0xffffffffL);
                java.util.HashSet<String> pairPins = pinsByPair.get(edgeKey);
                if (pairPins == null) {
                    pairPins = new java.util.HashSet<>();
                    pinsByPair.put(edgeKey, pairPins);
                }
                String normalizedPin = targetPin;
                if (pairPins.contains("") || (normalizedPin.isEmpty() && !pairPins.isEmpty())
                        || !pairPins.add(normalizedPin)) {
                    throw new IllegalArgumentException("Duplicate FBD connection");
                }
                adjacency.get(source).add(target);
                indegree[target]++;
            }
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

    public static void validateWidgetArrayStatic(JSONArray array) {
        if (array.length() > 2000) throw new IllegalArgumentException("Too many widgets");
        for (int i = 0; i < array.length(); i++) {
            JSONObject widget = array.optJSONObject(i);
            if (widget == null) throw new IllegalArgumentException("Widget must be an object");
            String type = widget.optString("type", "Текст"), label = widget.optString("text", "Надпись");
            if (type.length() > 100 || label.length() > 2000 || widget.optString("connectionInfo", "").length() > 2000) throw new IllegalArgumentException("Widget field too long");
            double x = widget.optDouble("x", widget.has("x") ? Double.NaN : 0), y = widget.optDouble("y", widget.has("y") ? Double.NaN : 0), w = widget.optDouble("w", widget.has("w") ? Double.NaN : 160), h = widget.optDouble("h", widget.has("h") ? Double.NaN : 58);
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(w) || Double.isNaN(h)
                    || x < -100000 || x > 100000 || y < -100000 || y > 100000 || w < 1 || w > 100000 || h < 1 || h > 100000) throw new IllegalArgumentException("Widget geometry out of range");
        }
    }

    private static class Screen {
        String name;
        int backgroundColor = Color.rgb(250, 252, 255);
        JSONObject extraFields = new JSONObject();
        final List<Widget> widgets = new ArrayList<>();
        final List<FbdConnection> connections = new ArrayList<>();
        Screen(String name) { this.name = name; }
    }

    public static class FbdConnection {
        public int source;
        public int target;
        private JSONObject extraFields = new JSONObject();
        public FbdConnection(int source, int target) { this.source = source; this.target = target; }
    }

    /** Shared by the actual project loader and the round-trip regression tests. */
    public static FbdConnection connectionFromJson(JSONObject json) throws Exception {
        FbdConnection connection = new FbdConnection(json.optInt("source", -1), json.optInt("target", -1));
        connection.extraFields = new JSONObject(json.toString());
        return connection;
    }

    /** Keep unknown edge fields, but always serialize the current edited/reindexed endpoints. */
    public static JSONObject connectionToJson(FbdConnection connection) throws Exception {
        JSONObject json = connection.extraFields == null
                ? new JSONObject() : new JSONObject(connection.extraFields.toString());
        json.put("source", connection.source);
        json.put("target", connection.target);
        return json;
    }

    /** Optional signal types: explicit pins must agree unless either is ANY. */
    public static boolean hasCompatibleFbdSignalTypes(FbdConnection edge) {
        if (!hasValidFbdPinMetadata(edge)) return false;
        JSONObject fields = edge.extraFields;
        boolean sourcePresent = fields.has("sourceSignalType");
        boolean targetPresent = fields.has("targetSignalType");
        if (!sourcePresent && !targetPresent) return true;
        if (!sourcePresent || !targetPresent) return false;
        Object source = fields.opt("sourceSignalType");
        Object target = fields.opt("targetSignalType");
        if (!(source instanceof String) || !(target instanceof String)) return false;
        String a = ((String) source).trim().toUpperCase(java.util.Locale.ROOT);
        String b = ((String) target).trim().toUpperCase(java.util.Locale.ROOT);
        if (!("BOOL".equals(a) || "NUMBER".equals(a) || "ANY".equals(a))) return false;
        if (!("BOOL".equals(b) || "NUMBER".equals(b) || "ANY".equals(b))) return false;
        return a.equals(b) || "ANY".equals(a) || "ANY".equals(b);
    }

    /** Minimal built-in FBD pin contract; unknown blocks stay untyped until sourced. */
    /** Normalize internal Russian widget identifiers to their public FBD terminal contracts. */
    public static String canonicalFbdBlockType(String blockType) {
        if (blockType == null) return "";
        String kind = blockType.trim().toUpperCase(java.util.Locale.ROOT);
        if ("FBD И".equals(kind)) return "FBD AND";
        if ("FBD ИЛИ".equals(kind)) return "FBD OR";
        if ("FBD ТАЙМЕР".equals(kind)) return "FBD TON";
        return kind;
    }

    /** Compare pin identifiers consistently without rewriting imported project metadata. */
    public static String canonicalFbdPinName(String pin) {
        return pin == null ? "" : pin.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public static String knownFbdPinType(String blockType, String pin, boolean output) {
        if (blockType == null || pin == null) return null;
        String kind = canonicalFbdBlockType(blockType);
        String name = canonicalFbdPinName(pin);
        if (kind.equals("FBD ВХОД")) return output && "Q".equals(name) ? "BOOL" : null;
        if (kind.equals("FBD AND") || kind.equals("FBD OR") || kind.equals("FBD XOR")
                || kind.equals("FBD NAND") || kind.equals("FBD NOR")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && ("IN1".equals(name) || "IN2".equals(name))) return "BOOL";
        }
        if (kind.equals("FBD НЕ")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && "IN".equals(name)) return "BOOL";
        }
        if (kind.equals("FBD RS") || kind.equals("FBD SR")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && ("S".equals(name) || "R".equals(name))) return "BOOL";
        }
        if (kind.equals("FBD CRAFT T")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && "IN".equals(name)) return "BOOL";
        }
        if (kind.equals("FBD R_TRIG") || kind.equals("FBD F_TRIG")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && "IN".equals(name)) return "BOOL";
        }
        if (kind.equals("FBD CRAFT CTU")) {
            if (output && "Q".equals(name)) return "NUMBER";
            if (!output && ("+".equals(name) || "-".equals(name) || "R".equals(name)))
                return "BOOL";
        }
        if (kind.equals("FBD CTU") || kind.equals("FBD CTD")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && ("CU".equals(name) || "CD".equals(name)
                    || "R".equals(name) || "LD".equals(name))) {
                if (kind.equals("FBD CTU")) return ("CU".equals(name) || "R".equals(name)) ? "BOOL" : null;
                return ("CD".equals(name) || "LD".equals(name)) ? "BOOL" : null;
            }
            if (!output && "PV".equals(name)) return "NUMBER";
        }
        if (kind.equals("FBD СЛОЖЕНИЕ") || kind.equals("FBD ВЫЧИТАНИЕ")
                || kind.equals("FBD УМНОЖЕНИЕ") || kind.equals("FBD ДЕЛЕНИЕ")
                || kind.equals("FBD MIN_MAX")) {
            if (output && "OUT".equals(name)) return "NUMBER";
            if (!output && ("IN1".equals(name) || "IN2".equals(name))) return "NUMBER";
        }
        if (kind.equals("FBD СРАВНЕНИЕ")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && ("A".equals(name) || "B".equals(name))) return "NUMBER";
        }
        if (kind.equals("FBD MOVE")) {
            if (output && "OUT".equals(name)) return "NUMBER";
            if (!output && "IN".equals(name)) return "NUMBER";
        }
        if (kind.equals("FBD SEL")) {
            if (output && "OUT".equals(name)) return "NUMBER";
            if (!output && "G".equals(name)) return "BOOL";
            if (!output && ("IN0".equals(name) || "IN1".equals(name))) return "NUMBER";
        }
        if (kind.equals("FBD POW")) {
            if (output && "OUT".equals(name)) return "NUMBER";
            if (!output && ("BASE".equals(name) || "EXP".equals(name))) return "NUMBER";
        }
        if (kind.equals("FBD SIN") || kind.equals("FBD ABS")
                || kind.equals("FBD COS") || kind.equals("FBD TAN") || kind.equals("FBD SQRT")
                || kind.equals("FBD FLOATTOINT") || kind.equals("FBD INTTOFLOAT")) {
            if (output && "OUT".equals(name)) return "NUMBER";
            if (!output && "IN".equals(name)) return "NUMBER";
        }
        if (kind.equals("FBD TON") || kind.equals("FBD TOF")
                || kind.equals("FBD TP") || kind.equals("FBD CRAFT BLINK")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && "IN".equals(name)) return "BOOL";
            if (!output && "PT".equals(name)) return "NUMBER";
        }
        return null;
    }

    /** Enforce known pin directions and types; preserve legacy unpinned connections. */
    public static boolean hasCompatibleKnownFbdPins(JSONObject edge, String sourceType, String targetType) throws Exception {
        if (!canLoadFbdConnection(edge)) return false;
        if (!edge.has("sourcePin") && !edge.has("targetPin")) return true;
        String sourcePin = edge.optString("sourcePin", "");
        String targetPin = edge.optString("targetPin", "");
        String sourceKnown = knownFbdPinType(sourceType, sourcePin, true);
        String targetKnown = knownFbdPinType(targetType, targetPin, false);
        boolean sourceBlockKnown = knownFbdPins(sourceType, true).length > 0
                || knownFbdPins(sourceType, false).length > 0;
        boolean targetBlockKnown = knownFbdPins(targetType, true).length > 0
                || knownFbdPins(targetType, false).length > 0;
        if ((sourceBlockKnown && sourceKnown == null) || (targetBlockKnown && targetKnown == null)) return false;
        if (sourceKnown != null && targetKnown != null && !sourceKnown.equals(targetKnown)) return false;
        if (sourceKnown != null && edge.has("sourceSignalType")
                && !("ANY".equalsIgnoreCase(edge.optString("sourceSignalType"))
                || sourceKnown.equalsIgnoreCase(edge.optString("sourceSignalType")))) return false;
        if (targetKnown != null && edge.has("targetSignalType")
                && !("ANY".equalsIgnoreCase(edge.optString("targetSignalType"))
                || targetKnown.equalsIgnoreCase(edge.optString("targetSignalType")))) return false;
        return true;
    }

    /** Validate pin metadata on import while leaving legacy unpinned connections intact. */
    public static boolean canLoadFbdConnection(JSONObject json) throws Exception {
        if (json == null) return false;
        FbdConnection edge = connectionFromJson(json);
        return edge.source >= 0 && edge.target >= 0 && edge.source != edge.target
                && hasCompatibleFbdSignalTypes(edge);
    }

    /** Create an explicitly pinned connection; reject malformed metadata before graph mutation. */
    public static FbdConnection pinnedFbdConnection(int source, String sourcePin,
                                                     int target, String targetPin) throws Exception {
        if (source < 0 || target < 0 || source == target) {
            throw new IllegalArgumentException("Invalid FBD endpoints");
        }
        JSONObject metadata = new JSONObject();
        metadata.put("source", source);
        metadata.put("target", target);
        metadata.put("sourcePin", sourcePin);
        metadata.put("targetPin", targetPin);
        FbdConnection edge = connectionFromJson(metadata);
        if (!hasValidFbdPinMetadata(edge)) {
            throw new IllegalArgumentException("Invalid FBD pin metadata");
        }
        return edge;
    }

    /** Check whether an edge's explicit pin metadata is well-formed; legacy edges have no pins. */
    public static boolean hasValidFbdPinMetadata(FbdConnection edge) {
        if (edge == null || edge.extraFields == null) return false;
        JSONObject json = edge.extraFields;
        if (!json.has("sourcePin") && !json.has("targetPin")) return true;
        if (!json.has("sourcePin") || !json.has("targetPin")) return false;
        Object sourcePin = json.opt("sourcePin");
        Object targetPin = json.opt("targetPin");
        return sourcePin instanceof String && targetPin instanceof String
                && !((String) sourcePin).trim().isEmpty()
                && !((String) targetPin).trim().isEmpty();
    }

    /** A single source may drive distinct named pins on one target, but never twice the same pin. */
    public static boolean duplicateFbdEndpoint(FbdConnection oldEdge, FbdConnection newEdge) {
        if (oldEdge == null || newEdge == null || oldEdge.source != newEdge.source
                || oldEdge.target != newEdge.target) return false;
        String oldPin = canonicalFbdPinName(oldEdge.extraFields.optString("targetPin", ""));
        String newPin = canonicalFbdPinName(newEdge.extraFields.optString("targetPin", ""));
        return oldPin.isEmpty() || newPin.isEmpty() || oldPin.equals(newPin);
    }

    /** Remove a selected edge by identity, even if a legacy project contains duplicates. */
    public static boolean disconnectFbdEdge(List<FbdConnection> connections, FbdConnection selected) {
        return connections != null && selected != null && connections.remove(selected);
    }

    /** Remove only the selected directed edge; keep other edges and their metadata intact. */
    public static boolean disconnectFbdEdge(List<FbdConnection> connections, int source, int target) {
        if (connections == null || source < 0 || target < 0) return false;
        for (int i = 0; i < connections.size(); i++) {
            FbdConnection edge = connections.get(i);
            if (edge != null && edge.source == source && edge.target == target) {
                connections.remove(i);
                return true;
            }
        }
        return false;
    }

    public static boolean isFbdWidget(Widget widget) {
        return widget != null && widget.type != null && widget.type.startsWith("FBD ");
    }

    private boolean isFbd(Widget widget) {
        return isFbdWidget(widget);
    }

    public static boolean evaluateFbdBlock(List<Widget> widgetsList, List<FbdConnection> connectionsList, int index, boolean[] visiting, boolean[] cacheValid, boolean[] cacheValues, double[] cacheNumeric) {
        if (index < 0 || index >= widgetsList.size()) return false;
        if (cacheValid[index]) return cacheValues[index];
        boolean result = evaluateFbdBlockUncached(widgetsList, connectionsList, index, visiting, cacheValid, cacheValues, cacheNumeric);
        cacheValues[index] = result;
        cacheValid[index] = true;
        return result;
    }

    public static double evaluateFbdBlockNumeric(List<Widget> widgetsList, List<FbdConnection> connectionsList, int index, boolean[] visiting, boolean[] cacheValid, boolean[] cacheValues, double[] cacheNumeric) {
        if (index < 0 || index >= widgetsList.size()) return 0.0;
        evaluateFbdBlock(widgetsList, connectionsList, index, visiting, cacheValid, cacheValues, cacheNumeric);
        return cacheNumeric[index];
    }

    /** Pinned wires are addressed by their target terminal, never by insertion order. */
    public static boolean fbdBooleanPinValue(List<Boolean> values, List<String> pins,
                                             String targetPin, int legacyIndex) {
        if (values == null || pins == null) return false;
        String requested = canonicalFbdPinName(targetPin);
        if (requested.isEmpty()) return false;
        for (int i = 0; i < values.size() && i < pins.size(); i++) {
            if (requested.equals(canonicalFbdPinName(pins.get(i)))) return Boolean.TRUE.equals(values.get(i));
        }
        // Old projects have no pin names; keep their original positional behavior.
        boolean hasNamedPin = false;
        for (String pin : pins) if (!canonicalFbdPinName(pin).isEmpty()) hasNamedPin = true;
        return !hasNamedPin && legacyIndex >= 0 && legacyIndex < values.size()
                && Boolean.TRUE.equals(values.get(legacyIndex));
    }

    /** Imported unnamed graphs retain positional math; new named ones use terminals. */
    public static boolean hasNamedFbdInput(List<String> pins) {
        if (pins == null) return false;
        for (String pin : pins) if (!canonicalFbdPinName(pin).isEmpty()) return true;
        return false;
    }

    /** Numeric terminal lookup; unnamed legacy connections retain their positional index. */
    public static double fbdNumericPinValue(List<Double> values, List<String> pins,
                                            String targetPin, int legacyIndex, double defaultValue) {
        if (values == null || pins == null) return defaultValue;
        String requested = canonicalFbdPinName(targetPin);
        if (requested.isEmpty()) return defaultValue;
        for (int i = 0; i < values.size() && i < pins.size(); i++) {
            if (requested.equals(canonicalFbdPinName(pins.get(i)))) {
                Double value = values.get(i);
                return value != null && !Double.isNaN(value) && !Double.isInfinite(value)
                        ? value : defaultValue;
            }
        }
        for (String pin : pins) {
            if (!canonicalFbdPinName(pin).isEmpty()) return defaultValue;
        }
        if (legacyIndex < 0 || legacyIndex >= values.size()) return defaultValue;
        Double value = values.get(legacyIndex);
        return value != null && !Double.isNaN(value) && !Double.isInfinite(value)
                ? value : defaultValue;
    }

    /** Counter transition functions are deterministic and independent of the Android clock. */
    public static int fbdCountUpStep(int current, boolean count, boolean previousCount,
                                      boolean reset) {
        if (reset) return 0;
        int bounded = Math.max(0, Math.min(1000000, current));
        return count && !previousCount && bounded < 1000000 ? bounded + 1 : bounded;
    }

    public static int fbdCountDownStep(int current, boolean count, boolean previousCount,
                                        boolean load, int preset) {
        if (load) return Math.max(0, Math.min(1000000, preset));
        int bounded = Math.max(0, Math.min(1000000, current));
        return count && !previousCount && bounded > 0 ? bounded - 1 : bounded;
    }

    /** Counter preset is a nonnegative integer; preserve saved preset for invalid PV. */
    public static int fbdCounterPreset(List<Double> values, List<String> pins, int configured) {
        int fallback = Math.max(0, Math.min(1000000, configured));
        double value = fbdNumericPinValue(values, pins, "PV", -1, fallback);
        if (value < 0 || value > 1000000 || Double.isNaN(value) || Double.isInfinite(value))
            return fallback;
        return (int) Math.round(value);
    }

    /** PT uses milliseconds in the current BLOODSKY runtime; original Craft parity remains under audit. */
    public static long fbdTonPresetMs(List<Double> values, List<String> pins, int configuredMs) {
        long fallback = Math.max(1L, Math.min(3600000L, (long) configuredMs));
        double raw = fbdNumericPinValue(values, pins, "PT", -1, fallback);
        if (raw < 1.0 || raw > 3600000.0 || Double.isNaN(raw) || Double.isInfinite(raw))
            return fallback;
        return Math.round(raw);
    }

    public static boolean evaluateFbdBlockUncached(List<Widget> widgetsList, List<FbdConnection> connectionsList, int index, boolean[] visiting, boolean[] cacheValid, boolean[] cacheValues, double[] cacheNumeric) {
        if (index < 0 || index >= widgetsList.size() || visiting[index]) return false;
        Widget widget = widgetsList.get(index);
        if (!isFbdWidget(widget)) return false;
        if ("FBD Вход".equals(widget.type)) {
            cacheNumeric[index] = widget.inputValue ? 1.0 : 0.0;
            return widget.inputValue;
        }
        visiting[index] = true;
        List<Boolean> inputs = new ArrayList<>();
        List<Double> numInputs = new ArrayList<>();
        List<String> inputPins = new ArrayList<>();
        if (connectionsList != null) {
            for (FbdConnection connection : connectionsList) {
                if (connection.target == index) {
                    inputPins.add(connection.extraFields.optString("targetPin", ""));
                    boolean sourceValue = evaluateFbdBlock(widgetsList, connectionsList, connection.source,
                            visiting, cacheValid, cacheValues, cacheNumeric);
                    String sourceType = connection.source >= 0 && connection.source < widgetsList.size()
                            ? widgetsList.get(connection.source).type : "";
                    inputs.add(effectiveFbdBooleanInput(connection, sourceType, widget.type, sourceValue));
                    numInputs.add(evaluateFbdBlockNumeric(widgetsList, connectionsList, connection.source,
                            visiting, cacheValid, cacheValues, cacheNumeric));
                }
            }
        }
        visiting[index] = false;
        final String canonicalType = canonicalFbdBlockType(widget.type);

        if ("FBD Сложение".equals(widget.type)) {
            double sum = 0.0;
            if (hasNamedFbdInput(inputPins)) {
                sum = fbdNumericPinValue(numInputs, inputPins, "IN1", 0, 0.0)
                        + fbdNumericPinValue(numInputs, inputPins, "IN2", 1, 0.0);
            } else for (Double v : numInputs) sum += v;
            if (Double.isNaN(sum) || Double.isInfinite(sum)) sum = 0.0;
            cacheNumeric[index] = sum;
            return sum != 0.0;
        }
        if ("FBD Вычитание".equals(widget.type)) {
            double diff;
            if (hasNamedFbdInput(inputPins)) {
                diff = fbdNumericPinValue(numInputs, inputPins, "IN1", 0, 0.0)
                        - fbdNumericPinValue(numInputs, inputPins, "IN2", 1, 0.0);
            } else {
                diff = numInputs.isEmpty() ? 0.0 : numInputs.get(0);
                for (int i = 1; i < numInputs.size(); i++) diff -= numInputs.get(i);
            }
            if (Double.isNaN(diff) || Double.isInfinite(diff)) diff = 0.0;
            cacheNumeric[index] = diff;
            return diff != 0.0;
        }
        if ("FBD Умножение".equals(widget.type)) {
            double prod;
            if (hasNamedFbdInput(inputPins)) {
                prod = fbdNumericPinValue(numInputs, inputPins, "IN1", 0, 0.0)
                        * fbdNumericPinValue(numInputs, inputPins, "IN2", 1, 0.0);
            } else {
                prod = numInputs.isEmpty() ? 0.0 : 1.0;
                for (Double v : numInputs) prod *= v;
            }
            if (Double.isNaN(prod) || Double.isInfinite(prod)) prod = 0.0;
            cacheNumeric[index] = prod;
            return prod != 0.0;
        }
        if ("FBD Деление".equals(widget.type)) {
            double div;
            if (hasNamedFbdInput(inputPins)) {
                double numerator = fbdNumericPinValue(numInputs, inputPins, "IN1", 0, 0.0);
                double denominator = fbdNumericPinValue(numInputs, inputPins, "IN2", 1, 0.0);
                div = denominator != 0.0 ? numerator / denominator : 0.0;
            } else {
                div = numInputs.isEmpty() ? 0.0 : numInputs.get(0);
                for (int i = 1; i < numInputs.size(); i++) {
                    double denom = numInputs.get(i);
                    div = denom != 0.0 ? div / denom : 0.0;
                }
            }
            if (Double.isNaN(div) || Double.isInfinite(div)) div = 0.0;
            cacheNumeric[index] = div;
            return div != 0.0;
        }
        if ("FBD ABS".equals(widget.type)) {
            double val = Math.abs(fbdNumericPinValue(numInputs, inputPins, "IN", 0, 0.0));
            if (Double.isNaN(val) || Double.isInfinite(val)) val = 0.0;
            cacheNumeric[index] = val;
            return val != 0.0;
        }
        if ("FBD POW".equals(widget.type)) {
            double base = fbdNumericPinValue(numInputs, inputPins, "BASE", 0, widget.compareA);
            double exp = fbdNumericPinValue(numInputs, inputPins, "EXP", 1, 1.0);
            double res = Math.pow(base, exp);
            if (Double.isNaN(res) || Double.isInfinite(res)) res = 0.0;
            cacheNumeric[index] = res;
            return res != 0.0;
        }
        if ("FBD SQRT".equals(widget.type)) {
            double val = fbdNumericPinValue(numInputs, inputPins, "IN", 0, widget.compareA);
            double res = Math.sqrt(Math.max(0.0, val));
            if (Double.isNaN(res) || Double.isInfinite(res)) res = 0.0;
            cacheNumeric[index] = res;
            return res != 0.0;
        }
        if ("FBD SIN".equals(widget.type)) {
            double val = fbdNumericPinValue(numInputs, inputPins, "IN", 0, widget.compareA);
            double res = Math.sin(val);
            if (Double.isNaN(res) || Double.isInfinite(res)) res = 0.0;
            cacheNumeric[index] = res;
            return res != 0.0;
        }
        if ("FBD COS".equals(widget.type)) {
            double val = fbdNumericPinValue(numInputs, inputPins, "IN", 0, widget.compareA);
            double res = Math.cos(val);
            if (Double.isNaN(res) || Double.isInfinite(res)) res = 0.0;
            cacheNumeric[index] = res;
            return res != 0.0;
        }
        if ("FBD TAN".equals(widget.type)) {
            double val = fbdNumericPinValue(numInputs, inputPins, "IN", 0, widget.compareA);
            double res = Math.tan(val);
            if (Double.isNaN(res) || Double.isInfinite(res)) res = 0.0;
            cacheNumeric[index] = res;
            return res != 0.0;
        }
        if ("FBD FloatToInt".equals(widget.type)) {
            double val = fbdNumericPinValue(numInputs, inputPins, "IN", 0, widget.compareA);
            double res = (double) ((long) val);
            cacheNumeric[index] = res;
            return res != 0.0;
        }
        if ("FBD IntToFloat".equals(widget.type)) {
            double val = fbdNumericPinValue(numInputs, inputPins, "IN", 0, widget.compareA);
            cacheNumeric[index] = val;
            return val != 0.0;
        }
        if ("FBD MIN_MAX".equals(widget.type)) {
            double minV;
            if (hasNamedFbdInput(inputPins)) {
                minV = Math.min(fbdNumericPinValue(numInputs, inputPins, "IN1", 0, 0.0),
                        fbdNumericPinValue(numInputs, inputPins, "IN2", 1, 0.0));
            } else {
                minV = numInputs.isEmpty() ? 0.0 : numInputs.get(0);
                for (Double v : numInputs) minV = Math.min(minV, v);
            }
            if (Double.isNaN(minV) || Double.isInfinite(minV)) minV = 0.0;
            cacheNumeric[index] = minV;
            return minV != 0.0;
        }
        if ("FBD MOVE".equals(widget.type) || "FBD SEL".equals(widget.type)) {
            double val;
            if (hasNamedFbdInput(inputPins)) {
                if ("FBD SEL".equals(widget.type)) {
                    boolean selector = fbdBooleanPinValue(inputs, inputPins, "G", -1);
                    val = selector ? fbdNumericPinValue(numInputs, inputPins, "IN1", -1, widget.compareA)
                            : fbdNumericPinValue(numInputs, inputPins, "IN0", -1, widget.compareA);
                } else val = fbdNumericPinValue(numInputs, inputPins, "IN", 0, widget.compareA);
            } else val = numInputs.isEmpty() ? widget.compareA : numInputs.get(0);
            if (Double.isNaN(val) || Double.isInfinite(val)) val = 0.0;
            cacheNumeric[index] = val;
            return val != 0.0;
        }

        if ("FBD AND".equals(canonicalType)) {
            if (inputs.isEmpty()) return false;
            for (Boolean value : inputs) if (!value) return false;
            return true;
        }
        if ("FBD OR".equals(canonicalType)) {
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
            boolean set = fbdBooleanPinValue(inputs, inputPins, "S", 0);
            boolean reset = fbdBooleanPinValue(inputs, inputPins, "R", 1);
            if (reset) widget.rsState = false;
            else if (set) widget.rsState = true;
            return widget.rsState;
        }
        if ("FBD SR".equals(widget.type)) {
            boolean set = fbdBooleanPinValue(inputs, inputPins, "S", 0);
            boolean reset = fbdBooleanPinValue(inputs, inputPins, "R", 1);
            if (set) widget.rsState = true;
            else if (reset) widget.rsState = false;
            return widget.rsState;
        }
        if ("FBD CRAFT T".equals(widget.type)) {
            boolean input = fbdBooleanPinValue(inputs, inputPins, "IN", 0);
            FbdCraftNativeTransitions.Trigger next = FbdCraftNativeTransitions.toggleRise(
                    new FbdCraftNativeTransitions.Trigger(
                            widget.craftToggleOutput, widget.craftToggleLastInput), input);
            widget.craftToggleOutput = next.output;
            widget.craftToggleLastInput = next.lastInput;
            return next.output;
        }
        if ("FBD R_TRIG".equals(widget.type)) {
            boolean input = fbdBooleanPinValue(inputs, inputPins, "IN", 0);
            boolean risingEdge = input && !widget.counterLastInput;
            widget.counterLastInput = input;
            return risingEdge;
        }
        if ("FBD F_TRIG".equals(widget.type)) {
            boolean input = fbdBooleanPinValue(inputs, inputPins, "IN", 0);
            boolean fallingEdge = !input && widget.counterLastInput;
            widget.counterLastInput = input;
            return fallingEdge;
        }
        if ("FBD CRAFT CTU".equals(widget.type)) {
            // Source-backed Craft +,-,R: independent rising edges, reset last,
            // unsigned (char) 16-bit count and a NUMBER Q output. This is a
            // separate versioned block; legacy BLOODSKY CTU stays unchanged.
            boolean plus = fbdBooleanPinValue(inputs, inputPins, "+", 0);
            boolean minus = fbdBooleanPinValue(inputs, inputPins, "-", 1);
            boolean reset = fbdBooleanPinValue(inputs, inputPins, "R", 2);
            FbdCraftNativeTransitions.Counter count = FbdCraftNativeTransitions.ctu(
                    new FbdCraftNativeTransitions.Counter(widget.counterValue,
                            widget.craftLastPlus, widget.craftLastMinus),
                    plus, minus, reset);
            widget.counterValue = count.value;
            widget.craftLastPlus = count.lastPlus;
            widget.craftLastMinus = count.lastMinus;
            cacheNumeric[index] = count.value;
            return count.value != 0;
        }
        if ("FBD CTU".equals(widget.type)) {
            boolean countInput = fbdBooleanPinValue(inputs, inputPins, "CU", 0);
            boolean resetInput = fbdBooleanPinValue(inputs, inputPins, "R", 1);
            widget.counterValue = fbdCountUpStep(widget.counterValue, countInput,
                    widget.counterLastInput, resetInput);
            widget.counterLastInput = countInput;
            return widget.counterValue >= fbdCounterPreset(numInputs, inputPins, widget.counterPreset);
        }
        if ("FBD CTD".equals(widget.type)) {
            boolean countInput = fbdBooleanPinValue(inputs, inputPins, "CD", 0);
            boolean loadInput = fbdBooleanPinValue(inputs, inputPins, "LD", 1);
            widget.counterValue = fbdCountDownStep(widget.counterValue, countInput,
                    widget.counterLastInput, loadInput,
                    fbdCounterPreset(numInputs, inputPins, widget.counterPreset));
            widget.counterLastInput = countInput;
            return widget.counterValue <= 0;
        }
        if ("FBD НЕ".equals(canonicalType))
            return !fbdBooleanPinValue(inputs, inputPins, "IN", 0);
        if ("FBD Сравнение".equals(widget.type)) {
            double a = fbdNumericPinValue(numInputs, inputPins, "A", 0, widget.compareA);
            double b = fbdNumericPinValue(numInputs, inputPins, "B", 1, widget.compareB);
            switch (normalizeComparisonOperatorStatic(widget.compareOperator)) {
                case "<": return a < b;
                case ">=": return a >= b;
                case "<=": return a <= b;
                case "==": return Double.compare(a, b) == 0;
                case "!=": return Double.compare(a, b) != 0;
                default: return a > b;
            }
        }
        if ("FBD CRAFT BLINK".equals(canonicalType)) {
            boolean input = fbdBooleanPinValue(inputs, inputPins, "IN", 0);
            long presetMs = fbdTonPresetMs(numInputs, inputPins, widget.timerPresetMs);
            FbdCraftNativeTimer.State prior = widget.craftBlinkState == null
                    ? FbdCraftNativeTimer.State.initial() : widget.craftBlinkState;
            FbdCraftNativeTimer.State next = FbdCraftNativeTimer.step(
                    FbdCraftNativeTimer.BLINK, prior, input,
                    System.currentTimeMillis(), presetMs);
            widget.craftBlinkState = next;
            return next.output;
        }
        if ("FBD TON".equals(canonicalType)
                || "FBD TOF".equals(canonicalType)
                || "FBD TP".equals(canonicalType)) {
            boolean input = fbdBooleanPinValue(inputs, inputPins, "IN", 0);
            long preset = fbdTonPresetMs(numInputs, inputPins, widget.timerPresetMs);
            long now = SystemClock.elapsedRealtime();
            FbdTimerTransitions.Step step;
            if ("FBD TON".equals(canonicalType))
                step = FbdTimerTransitions.ton(input, now, preset, widget.timerStartedAt);
            else if ("FBD TOF".equals(canonicalType))
                step = FbdTimerTransitions.tof(input, now, preset,
                        widget.timerStartedAt, widget.counterLastInput);
            else step = FbdTimerTransitions.tp(input, now, preset,
                        widget.timerStartedAt, widget.counterLastInput);
            widget.timerStartedAt = step.startedAt;
            if (!"FBD TON".equals(canonicalType))
                widget.counterLastInput = step.lastInput;
            return step.output;
        }
        return false;
    }

    private boolean evaluateFbd(int index, boolean[] visiting) {
        if (screens.isEmpty()) return false;
        if (fbdCacheValid.length != widgets.size() || fbdCacheValues.length != widgets.size()) {
            fbdCacheValid = new boolean[widgets.size()];
            fbdCacheValues = new boolean[widgets.size()];
            fbdCacheNumeric = new double[widgets.size()];
        }
        return evaluateFbdBlock(widgets, screens.get(currentScreenIndex).connections, index, visiting, fbdCacheValid, fbdCacheValues, fbdCacheNumeric);
    }

    private boolean evaluateFbdUncached(int index, boolean[] visiting) {
        if (screens.isEmpty()) return false;
        return evaluateFbdBlockUncached(widgets, screens.get(currentScreenIndex).connections, index, visiting, fbdCacheValid, fbdCacheValues, fbdCacheNumeric);
    }

    private double evaluateFbdNumeric(int index, boolean[] visiting) {
        if (screens.isEmpty()) return 0.0;
        return evaluateFbdBlockNumeric(widgets, screens.get(currentScreenIndex).connections, index, visiting, fbdCacheValid, fbdCacheValues, fbdCacheNumeric);
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

    /** Explicit edge selection avoids deleting unrelated FBD connections. */
    /** Wire history is session-local and never changes persisted legacy edge metadata. */
    private void replayFbdWireEdit(boolean redo) {
        if (operatorMode || currentScreenIndex < 0 || currentScreenIndex >= screens.size()) return;
        Screen current = screens.get(currentScreenIndex);
        try {
            boolean changed = redo
                    ? fbdWireHistory.redo(current, current.connections)
                    : fbdWireHistory.undo(current, current.connections);
            if (!changed) return;
            persistProject();
            if (editor != null) editor.invalidate();
            Toast.makeText(this, redo ? "Связь FBD повторена" : "Связь FBD отменена",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            fbdWireHistory.clear();
            Toast.makeText(this, "Не удалось восстановить связь FBD", Toast.LENGTH_LONG).show();
        }
    }

    /** Named BOOL-only wire inversion, with undo/redo and immediate persistence. */
    private void showFbdInversionDialog() { showFbdInversionDialog(-1); }

    private void showFbdInversionDialog(int sourceFilter) {
        if (operatorMode || screens.isEmpty() || currentScreenIndex < 0
                || currentScreenIndex >= screens.size()) return;
        Screen screen = screens.get(currentScreenIndex);
        final List<FbdConnection> choices = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (FbdConnection edge : screen.connections) {
            if (edge == null || (sourceFilter >= 0 && edge.source != sourceFilter)) continue;
            if (edge.source < 0 || edge.target < 0
                    || edge.source >= widgets.size() || edge.target >= widgets.size()) continue;
            Widget from = widgets.get(edge.source);
            Widget to = widgets.get(edge.target);
            if (!isInvertibleFbdBoolWire(edge, from.type, to.type)) continue;
            choices.add(edge);
            labels.add((edge.source + 1) + ":" + edge.extraFields.optString("sourcePin", "")
                    + " → " + (edge.target + 1) + ":" + edge.extraFields.optString("targetPin", "")
                    + (isFbdBooleanWireInverted(edge, from.type, to.type) ? "  ✓ ИНВ" : "  обычная"));
        }
        if (choices.isEmpty()) {
            Toast.makeText(this, "Нет типизированных BOOL-связей для инверсии", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Инверсия связи FBD (BOOL)")
                .setItems(labels.toArray(new String[0]), (dialog, index) -> {
                    FbdConnection edge = choices.get(index);
                    if (!screen.connections.contains(edge)) return;
                    Widget from = widgets.get(edge.source), to = widgets.get(edge.target);
                    if (!isInvertibleFbdBoolWire(edge, from.type, to.type)) return;
                    Object previous = edge.extraFields.opt("inverted");
                    boolean existed = edge.extraFields.has("inverted");
                    try {
                        fbdWireHistory.recordBefore(screen, screen.connections);
                        if (!toggleFbdBooleanWireInversion(edge, from.type, to.type)) return;
                        if (!persistProjectNow(true)) {
                            if (existed) edge.extraFields.put("inverted", previous);
                            else edge.extraFields.remove("inverted");
                            fbdWireHistory.clear();
                            Toast.makeText(this, "Инверсия не сохранена", Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (editor != null) editor.invalidate();
                    } catch (Exception failure) {
                        try {
                            if (existed) edge.extraFields.put("inverted", previous);
                            else edge.extraFields.remove("inverted");
                        } catch (Exception ignored) {}
                        fbdWireHistory.clear();
                        Toast.makeText(this, "Ошибка изменения BOOL-связи", Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("Отмена", null).show();
    }

    private void showFbdDisconnectDialog() { showFbdDisconnectDialog(-1); }

    private void showFbdDisconnectDialog(int sourceFilter) {
        if (screens.isEmpty() || currentScreenIndex < 0 || currentScreenIndex >= screens.size()) return;
        Screen screen = screens.get(currentScreenIndex);
        if (screen.connections.isEmpty()) {
            Toast.makeText(this, "Нет связей FBD для удаления", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<FbdConnection> candidates = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (FbdConnection edge : screen.connections) {
            if (edge == null || (sourceFilter >= 0 && edge.source != sourceFilter)
                    || edge.source < 0 || edge.target < 0
                    || edge.source >= widgets.size() || edge.target >= widgets.size()) continue;
            candidates.add(edge);
            JSONObject metadata = edge.extraFields;
            String pinLabel = metadata != null && metadata.has("sourcePin") && metadata.has("targetPin")
                    ? " [" + metadata.optString("sourcePin") + " → " + metadata.optString("targetPin") + "]" : "";
            labels.add((edge.source + 1) + ": " + widgets.get(edge.source).text
                    + " → " + (edge.target + 1) + ": " + widgets.get(edge.target).text + pinLabel);
        }
        if (candidates.isEmpty()) {
            Toast.makeText(this, "Нет доступных связей FBD", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Удалить связь FBD")
                .setItems(labels.toArray(new String[0]), (dialog, selected) -> {
                    FbdConnection edge = candidates.get(selected);
                    if (!screen.connections.contains(edge)) return;
                    try {
                        fbdWireHistory.recordBefore(screen, screen.connections);
                    } catch (Exception invalid) {
                        Toast.makeText(this, "Не удалось сохранить историю FBD", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (disconnectFbdEdge(screen.connections, edge)) {
                        persistProject();
                        if (editor != null) editor.invalidate();
                        Toast.makeText(this, "Связь FBD удалена", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("Отмена", null).show();
    }

    /** Offer pin-specific wiring for blocks whose pin names are confirmed in this build. */
    private void chooseFbdPins(int source, int target) {
        final int screenAtSelection = currentScreenIndex;
        final Widget sourceAtSelection = widgets.get(source);
        final Widget targetAtSelection = widgets.get(target);
        String sourceType = widgets.get(source).type;
        String targetType = widgets.get(target).type;
        String[] sourcePins = knownFbdPins(sourceType, true);
        String[] targetPins = knownFbdPins(targetType, false);
        if (sourcePins.length == 0 || targetPins.length == 0) {
            // A known input-only or output-only block must not fall back to an untyped wire.
            if (!canUseUnpinnedFbdFallback(sourceType, targetType)) {
                Toast.makeText(this, "Для этого FBD-блока не определены контакты", Toast.LENGTH_LONG).show();
                return;
            }
            addFbdConnection(source, target, new FbdConnection(source, target));
            return;
        }
        new AlertDialog.Builder(this).setTitle("Выход FBD")
                .setItems(sourcePins, (dialog, sourceChoice) -> {
                    String chosenSourcePin = sourcePins[sourceChoice];
                    String[] compatiblePins = compatibleFbdTargetPins(
                            sourceType, chosenSourcePin, targetType);
                    if (compatiblePins.length == 0) {
                        Toast.makeText(this, "Нет совместимых входов FBD", Toast.LENGTH_LONG).show();
                        return;
                    }
                    new AlertDialog.Builder(this).setTitle("Вход FBD")
                            .setItems(compatiblePins, (targetDialog, targetChoice) -> {
                                try {
                                    if (screenAtSelection != currentScreenIndex || source >= widgets.size()
                                            || target >= widgets.size()
                                            || widgets.get(source) != sourceAtSelection
                                            || widgets.get(target) != targetAtSelection
                                            || !sourceType.equals(widgets.get(source).type)
                                            || !targetType.equals(widgets.get(target).type)) {
                                        Toast.makeText(this, "FBD-блоки изменились: выбери их заново", Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    String chosenTargetPin = compatiblePins[targetChoice];
                                    FbdConnection edge = pinnedFbdConnection(source, chosenSourcePin,
                                            target, chosenTargetPin);
                                    String sourceSignal = knownFbdPinType(sourceType, chosenSourcePin, true);
                                    String targetSignal = knownFbdPinType(targetType, chosenTargetPin, false);
                                    if (sourceSignal != null && targetSignal != null) {
                                        edge.extraFields.put("sourceSignalType", sourceSignal);
                                        edge.extraFields.put("targetSignalType", targetSignal);
                                    }
                                    JSONObject json = connectionToJson(edge);
                                    if (!hasCompatibleKnownFbdPins(json, sourceType, targetType)) {
                                        Toast.makeText(this, "Несовместимые контакты FBD", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    addFbdConnection(source, target, edge);
                                } catch (Exception error) {
                                    Toast.makeText(this, "Ошибка контактов FBD", Toast.LENGTH_SHORT).show();
                                }
                            }).setNegativeButton("Отмена", null).show();
                })
                .setNegativeButton("Отмена", null).show();
    }

    /** A named terminal's physical Y position is stable across edits and drags. */
    public static float fbdTerminalFraction(String type, String pin, boolean output) {
        String[] pins = knownFbdPins(type, output);
        String name = canonicalFbdPinName(pin);
        String kind = canonicalFbdBlockType(type);
        for (int i = 0; i < pins.length; i++) {
            if (!canonicalFbdPinName(pins[i]).equals(name)) continue;
            // The original Craft AND/OR/XOR and compare drawing uses
            // quarter-height and three-quarter-height input ports.
            if (!output && pins.length == 2) {
                if ("FBD AND".equals(kind) || "FBD OR".equals(kind)
                        || "FBD СРАВНЕНИЕ".equals(kind))
                    return i == 0 ? 0.25f : 0.75f;
                // The original split-body source FB_XOR/FB_TIMER has a
                // one-third header. Its named contacts lie at 1/2 and 5/6.
                if ("FBD XOR".equals(kind) || "FBD TON".equals(kind)
                        || "FBD TP".equals(kind) || "FBD TOF".equals(kind)
                        || "FBD CRAFT BLINK".equals(kind))
                    return i == 0 ? 0.5f : 5f / 6f;
            }
            return (i + 1f) / (pins.length + 1f);
        }
        // Legacy unpinned graph edges have no source-confirmed pin geometry.
        return 0.5f;
    }

    public static boolean isInvertibleFbdBoolWire(FbdConnection connection,
                                                   String sourceType, String targetType) {
        if (connection == null || connection.extraFields == null) return false;
        JSONObject meta = connection.extraFields;
        return "BOOL".equals(knownFbdPinType(sourceType, meta.optString("sourcePin", ""), true))
                && "BOOL".equals(knownFbdPinType(targetType, meta.optString("targetPin", ""), false));
    }

    public static boolean isFbdBooleanWireInverted(FbdConnection edge,
                                                    String sourceType, String targetType) {
        return isInvertibleFbdBoolWire(edge, sourceType, targetType)
                && Boolean.TRUE.equals(edge.extraFields.opt("inverted"));
    }

    public static boolean effectiveFbdBooleanInput(FbdConnection connection, String sourceType,
                                                    String targetType, boolean sourceValue) {
        return isFbdBooleanWireInverted(connection, sourceType, targetType)
                ? !sourceValue : sourceValue;
    }

    /** Mutate only a known Boolean edge; other imported metadata is untouched. */
    public static boolean toggleFbdBooleanWireInversion(FbdConnection connection, String sourceType,
                                                         String targetType) throws Exception {
        if (!isInvertibleFbdBoolWire(connection, sourceType, targetType)) return false;
        connection.extraFields.put("inverted",
                !isFbdBooleanWireInverted(connection, sourceType, targetType));
        return true;
    }

    /** Clock epochs must never leak from saved files or editing into live execution. */
    public static void resetFbdTimerRuntime(Widget widget) {
        if (widget == null) return;
        String kind = canonicalFbdBlockType(widget.type);
        if ("FBD CRAFT BLINK".equals(kind)) {
            widget.craftBlinkState = FbdCraftNativeTimer.State.initial();
        }
        if ("FBD TON".equals(kind) || "FBD TP".equals(kind) || "FBD TOF".equals(kind)) {
            widget.timerStartedAt = 0L;
            if ("FBD TP".equals(kind) || "FBD TOF".equals(kind))
                widget.counterLastInput = false;
        }
    }

    /** Only unsupported legacy block pairs may use anonymous positional connections. */
    public static boolean canUseUnpinnedFbdFallback(String sourceType, String targetType) {
        if (sourceType == null || targetType == null
                || !sourceType.trim().toUpperCase(java.util.Locale.ROOT).startsWith("FBD ")
                || !targetType.trim().toUpperCase(java.util.Locale.ROOT).startsWith("FBD ")) return false;
        return knownFbdPins(sourceType, true).length == 0
                && knownFbdPins(sourceType, false).length == 0
                && knownFbdPins(targetType, true).length == 0
                && knownFbdPins(targetType, false).length == 0;
    }

    /** Show only type-compatible target terminals after choosing a source pin. */
    public static String[] compatibleFbdTargetPins(String sourceType, String sourcePin, String targetType) {
        String[] targetPins = knownFbdPins(targetType, false);
        String sourceSignal = knownFbdPinType(sourceType, sourcePin, true);
        if (knownFbdPins(sourceType, true).length > 0 && sourceSignal == null)
            return new String[0];
        List<String> compatible = new ArrayList<>();
        for (String targetPin : targetPins) {
            String targetSignal = knownFbdPinType(targetType, targetPin, false);
            if (sourceSignal == null || targetSignal == null || sourceSignal.equals(targetSignal))
                compatible.add(targetPin);
        }
        return compatible.toArray(new String[0]);
    }

    public static String[] knownFbdPins(String blockType, boolean output) {
        if (blockType == null) return new String[0];
        String kind = canonicalFbdBlockType(blockType);
        if ("FBD ВХОД".equals(kind)) return output ? new String[]{"Q"} : new String[0];
        if ("FBD AND".equals(kind) || "FBD OR".equals(kind) || "FBD XOR".equals(kind)
                || "FBD NAND".equals(kind) || "FBD NOR".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"IN1", "IN2"};
        if ("FBD НЕ".equals(kind)) return output ? new String[]{"Q"} : new String[]{"IN"};
        if ("FBD RS".equals(kind) || "FBD SR".equals(kind)) return output ? new String[]{"Q"} : new String[]{"R", "S"};
        if ("FBD R_TRIG".equals(kind) || "FBD F_TRIG".equals(kind)
                || "FBD CRAFT T".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"IN"};
        if ("FBD CRAFT CTU".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"+", "-", "R"};
        if ("FBD CTU".equals(kind)) return output ? new String[]{"Q"} : new String[]{"CU", "R", "PV"};
        if ("FBD CTD".equals(kind)) return output ? new String[]{"Q"} : new String[]{"CD", "LD", "PV"};
        if ("FBD СЛОЖЕНИЕ".equals(kind) || "FBD ВЫЧИТАНИЕ".equals(kind)
                || "FBD УМНОЖЕНИЕ".equals(kind) || "FBD ДЕЛЕНИЕ".equals(kind)
                || "FBD MIN_MAX".equals(kind))
            return output ? new String[]{"OUT"} : new String[]{"IN1", "IN2"};
        if ("FBD СРАВНЕНИЕ".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"A", "B"};
        if ("FBD MOVE".equals(kind))
            return output ? new String[]{"OUT"} : new String[]{"IN"};
        if ("FBD SEL".equals(kind))
            return output ? new String[]{"OUT"} : new String[]{"G", "IN0", "IN1"};
        if ("FBD POW".equals(kind)) return output ? new String[]{"OUT"} : new String[]{"BASE", "EXP"};
        if ("FBD SIN".equals(kind) || "FBD ABS".equals(kind)
                || "FBD COS".equals(kind) || "FBD TAN".equals(kind) || "FBD SQRT".equals(kind)
                || "FBD FLOATTOINT".equals(kind) || "FBD INTTOFLOAT".equals(kind))
            return output ? new String[]{"OUT"} : new String[]{"IN"};
        if ("FBD TON".equals(kind) || "FBD TOF".equals(kind)
                || "FBD TP".equals(kind) || "FBD CRAFT BLINK".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"IN", "PT"};
        return new String[0];
    }

    private void addFbdConnection(int source, int target, FbdConnection edge) {
        if (edge == null || edge.source != source || edge.target != target
                || currentScreenIndex < 0 || currentScreenIndex >= screens.size()
                || source < 0 || target < 0 || source >= widgets.size() || target >= widgets.size()
                || source == target || !isFbd(widgets.get(source)) || !isFbd(widgets.get(target))) return;
        Screen screen = screens.get(currentScreenIndex);
        try {
            if (!hasCompatibleKnownFbdPins(connectionToJson(edge), widgets.get(source).type,
                    widgets.get(target).type)) {
                Toast.makeText(this, "Несовместимые контакты FBD", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception invalid) {
            Toast.makeText(this, "Некорректная связь FBD", Toast.LENGTH_SHORT).show();
            return;
        }
        if (reachesFbd(target, source, new boolean[widgets.size()], screen.connections)) {
            Toast.makeText(this, "Связь отменена: она создаёт цикл в FBD", Toast.LENGTH_LONG).show();
            return;
        }
        for (FbdConnection existing : screen.connections) {
            String newTargetPin = canonicalFbdPinName(edge.extraFields.optString("targetPin", ""));
            if (!newTargetPin.isEmpty() && existing.target == target
                    && newTargetPin.equals(canonicalFbdPinName(existing.extraFields.optString("targetPin", "")))) {
                Toast.makeText(this, "Вход FBD уже занят другим проводом", Toast.LENGTH_SHORT).show();
                return;
            }
            if (duplicateFbdEndpoint(existing, edge)) {
                Toast.makeText(this, "Такая связь уже существует", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        try {
            fbdWireHistory.recordBefore(screen, screen.connections);
        } catch (Exception invalid) {
            Toast.makeText(this, "Не удалось сохранить историю FBD", Toast.LENGTH_LONG).show();
            return;
        }
        screen.connections.add(edge);
        persistProject();
        if (editor != null) editor.invalidate();
        Toast.makeText(this, "Связь FBD добавлена", Toast.LENGTH_SHORT).show();
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
        chooseFbdPins(source, target);
    }

    public static class Widget {
        public String type;
        public String text;
        public String connectionInfo = "";
        public boolean inputValue = false;
        public boolean buttonState = false;
        public boolean rsState = false;
        public int counterPreset = 1;
        public int counterValue = 0;
        public boolean counterLastInput = false;
        // Native Craft edge flags are transient operator runtime only;
        // their absence from JSON protects old project schemas on save/copy.
        boolean craftLastPlus = false;
        boolean craftLastMinus = false;
        // Original FB_TRIGGER mode T resets to Q=false on a fresh instance.
        // Do not serialize runtime latches into legacy project metadata.
        boolean craftToggleOutput = false;
        boolean craftToggleLastInput = false;
        // Blink runtime epoch is not saved; a newly opened project starts idle.
        FbdCraftNativeTimer.State craftBlinkState = FbdCraftNativeTimer.State.initial();
        public int timerPresetMs = 1000;
        public long timerStartedAt = 0L;
        public double compareA = 0.0, compareB = 0.0;
        public String compareOperator = ">";
        public double minValue = 0.0, maxValue = 100.0;
        public int backgroundColor = 0;
        public int textColor = Color.rgb(31, 41, 55);
        public int textSizeSp = 14;
        public boolean visible = true;
        public boolean showFrame = true;
        public boolean autoResizeText = false;
        public String[] dynTextStates = new String[16];
        public int[] dynTextColors = new int[16];
        public String[] bitListLabels = new String[16];
        public String[] voiceMessages = new String[16];
        public String[] imagePathStates = new String[16];
        public double soundThreshold = 1.0;
        public boolean soundActive = false;
        public String soundFilePath = "Аларм";
        public int targetScreenIndex = 0;
        public String targetUrl = "";
        public int lastVoiceState = -1;
        public boolean lastSoundState = false;
        public JSONObject extraFields = new JSONObject();
        public float x, y, w, h;

        public Widget() {
            for (int i = 0; i < 16; i++) {
                dynTextStates[i] = "Состояние " + i;
                dynTextColors[i] = 0;
                bitListLabels[i] = "Бит " + i;
                voiceMessages[i] = "Сообщение " + i;
                imagePathStates[i] = "Картинка " + i;
            }
        }
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
            if (!operatorMode) {
                for (int x = 0; x < getWidth(); x += grid) canvas.drawLine(x, 0, x, getHeight(), paint);
                for (int y = 0; y < getHeight(); y += grid) canvas.drawLine(0, y, getWidth(), y, paint);
            }

            if (!operatorMode && !screens.isEmpty()) {
                Screen screen = screens.get(currentScreenIndex);
                paint.setColor(Color.rgb(124, 58, 237));
                paint.setStrokeWidth(dp(2));
                paint.setStyle(Paint.Style.STROKE);
                for (FbdConnection connection : screen.connections) {
                    if (connection.source < 0 || connection.target < 0 || connection.source >= widgets.size() || connection.target >= widgets.size()) continue;
                    Widget from = widgets.get(connection.source);
                    Widget to = widgets.get(connection.target);
                    if (!from.visible || !to.visible || !isFbd(from) || !isFbd(to)) continue;
                    float x1 = from.x + from.w;
                    float y1 = from.y + from.h * fbdTerminalFraction(from.type,
                            connection.extraFields.optString("sourcePin", ""), true);
                    float x2 = to.x;
                    float y2 = to.y + to.h * fbdTerminalFraction(to.type,
                            connection.extraFields.optString("targetPin", ""), false);
                    paint.setColor(isFbdBooleanWireInverted(connection, from.type, to.type)
                            ? Color.rgb(234, 88, 12) : Color.rgb(124, 58, 237));
                    float mid = (x1 + x2) / 2f;
                    canvas.drawLine(x1, y1, mid, y1, paint);
                    canvas.drawLine(mid, y1, mid, y2, paint);
                    canvas.drawLine(mid, y2, x2, y2, paint);
                }
                paint.setStyle(Paint.Style.FILL);
            }
            fbdCacheValid = new boolean[widgets.size()];
            fbdCacheValues = new boolean[widgets.size()];
            fbdCacheNumeric = new double[widgets.size()];
            if (operatorMode) {
                for (int i = 0; i < widgets.size(); i++)
                    if (isFbd(widgets.get(i))) evaluateFbd(i, new boolean[widgets.size()]);
            }
            // FBD output bindings are read-only and local to this screen.
            // They do not interfere with Modbus polling or write PLC registers.
            for (int i = 0; i < widgets.size(); i++) {
                Widget target = widgets.get(i);
                if (isFbd(target) || target.connectionInfo == null
                        || !target.connectionInfo.startsWith("fbd://")) continue;
                if (operatorMode) {
                    String value = FbdResultBinding.resolve(target.connectionInfo, widgets,
                            fbdCacheValid, fbdCacheValues, fbdCacheNumeric);
                    liveValues.put(i, value == null ? "CFG" : value);
                } else liveValues.remove(i);
            }
            for (int i = 0; i < widgets.size(); i++) {
                Widget w = widgets.get(i);
                if (!w.visible || (operatorMode && isFbd(w))) continue;
                RectF rect = new RectF(w.x, w.y, w.x + w.w, w.y + w.h);
                paint.setStyle(Paint.Style.FILL);
                if (w.backgroundColor != 0) {
                    paint.setColor(w.backgroundColor);
                } else if (w.type != null && w.type.startsWith("FBD ")) {
                    paint.setColor(Color.rgb(237, 233, 254));
                } else if ("Индикатор".equals(w.type) || "Двухпозиционный индикатор".equals(w.type)) {
                    paint.setColor(Color.rgb(220, 252, 231));
                } else if ("Кнопка".equals(w.type)) {
                    paint.setColor(w.buttonState ? Color.rgb(187, 247, 208) : Color.rgb(219, 234, 254));
                } else if ("Задатчик".equals(w.type)) {
                    paint.setColor(Color.rgb(254, 243, 199));
                } else if ("Шкала / Стрелка".equals(w.type) || "Прогресс-бар".equals(w.type) || "Битовый список".equals(w.type)) {
                    paint.setColor(Color.rgb(241, 245, 249));
                } else if ("График / Тренд".equals(w.type)) {
                    paint.setColor(Color.rgb(15, 23, 42));
                } else {
                    paint.setColor(Color.WHITE);
                }
                // A source-backed FBD body already paints its own square
                // outline; never leave the former purple rounded rectangle halo.
                boolean sourceFbdSkin = FbdSourceCanvasSkin.shouldUse(w);
                if (!OriginalIndicator.supports(w.type) && !sourceFbdSkin)
                    canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                if ((!OriginalIndicator.supports(w.type) && w.showFrame
                        && !sourceFbdSkin) || i == selected) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(i == selected ? 2.5f : 1));
                    paint.setColor(i == selected ? Color.rgb(21, 101, 192) : Color.rgb(148, 163, 184));
                    canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                }
                if (i == selected) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.rgb(21, 101, 192));
                    float grip = dp(9);
                    canvas.drawRect(w.x + w.w - grip, w.y + w.h - grip, w.x + w.w, w.y + w.h, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(w.textColor);
                paint.setTextSize(dp(w.textSizeSp));
                if (w.type != null && w.type.startsWith("FBD ")) {
                    if (FbdSourceCanvasSkin.shouldUse(w)) {
                        FbdSourceCanvasSkin.draw(canvas, paint, w, getResources().getDisplayMetrics().density);
                        if (i == selected) {
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setStrokeWidth(dp(2.5f));
                            paint.setColor(Color.rgb(21, 101, 192));
                            canvas.drawRect(rect, paint);
                            paint.setStyle(Paint.Style.FILL);
                            float grip = dp(9);
                            canvas.drawRect(w.x + w.w - grip, w.y + w.h - grip,
                                    w.x + w.w, w.y + w.h, paint);
                        }
                    } else {
                    paint.setColor(w.textColor);
                    paint.setTextSize(dp(w.textSizeSp));
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(24)) / Math.max(4f, w.text.length() * 0.58f))));
                    String blockTitle = "FBD Вход".equals(w.type) ? "Вход " + (w.inputValue ? "1" : "0")
                            : ("FBD CRAFT CTU".equals(w.type) ? "CTU16 " + w.counterValue
                            : (("FBD CTU".equals(w.type) || "FBD CTD".equals(w.type)) ? ("FBD CTD".equals(w.type) ? "CTD " : "CTU ") + w.counterValue + "/" + w.counterPreset : w.text));
                    if (liveValues.containsKey(i) && !"FBD Вход".equals(w.type)) blockTitle += " [" + liveValues.get(i) + "]";
                    canvas.drawText(blockTitle, w.x + dp(12), w.y + dp(23), paint);
                    // Never evaluate stateful FBD logic from the editor's paint pass:
                    // drawing alone must not count edges or advance TON/TOF/TP.
                    paint.setTextSize(dp(11));
                    paint.setColor(Color.rgb(100, 116, 139));
                    canvas.drawText("OUT = —", w.x + dp(12), w.y + w.h - dp(8), paint);
                    paint.setTextSize(dp(10));
                    paint.setColor(Color.rgb(109, 40, 217));
                    String[] blockInputs = knownFbdPins(w.type, false);
                    if (blockInputs.length == 0) blockInputs = new String[]{"", ""};
                    for (int pinIndex = 0; pinIndex < blockInputs.length; pinIndex++) {
                        float py = w.y + w.h * (blockInputs[pinIndex].isEmpty()
                                ? (pinIndex + 1f) / (blockInputs.length + 1f)
                                : fbdTerminalFraction(w.type, blockInputs[pinIndex], false));
                        canvas.drawLine(w.x, py, w.x + dp(14), py, paint);
                        canvas.drawCircle(w.x + dp(4), py, dp(4), paint);
                        if (!blockInputs[pinIndex].isEmpty())
                            canvas.drawText(blockInputs[pinIndex], w.x + dp(17), py - dp(3), paint);
                    }
                    String[] blockOutputs = knownFbdPins(w.type, true);
                    if (blockOutputs.length == 0) blockOutputs = new String[]{""};
                    for (int pinIndex = 0; pinIndex < blockOutputs.length; pinIndex++) {
                        float py = w.y + w.h * (blockOutputs[pinIndex].isEmpty()
                                ? (pinIndex + 1f) / (blockOutputs.length + 1f)
                                : fbdTerminalFraction(w.type, blockOutputs[pinIndex], true));
                        canvas.drawLine(w.x + w.w - dp(14), py, w.x + w.w, py, paint);
                        canvas.drawCircle(w.x + w.w - dp(4), py, dp(4), paint);
                    }
                    paint.setStyle(Paint.Style.FILL);
                    }
                } else if ("Динамический текст".equals(w.type)) {
                    int stateIdx = 0;
                    if (liveValues.containsKey(i)) {
                        try { stateIdx = (int) Math.round(Double.parseDouble(liveValues.get(i))); } catch (Exception ignored) {}
                    }
                    if (stateIdx < 0 || stateIdx >= 16) stateIdx = 0;
                    String stateText = w.dynTextStates[stateIdx];
                    if (stateText == null || stateText.isEmpty()) stateText = w.text + " [" + stateIdx + "]";
                    int bgColor = w.dynTextColors[stateIdx];
                    if (bgColor != 0) {
                        paint.setColor(bgColor);
                        canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                    }
                    paint.setColor(w.textColor);
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(20)) / Math.max(4f, stateText.length() * 0.58f))));
                    canvas.drawText(stateText, w.x + dp(10), w.y + w.h / 2 + dp(5), paint);
                } else if ("Битовый список".equals(w.type)) {
                    int bitmask = 0;
                    if (liveValues.containsKey(i)) {
                        try { bitmask = Integer.parseInt(liveValues.get(i)); } catch (Exception ignored) {}
                    }
                    paint.setColor(w.textColor);
                    paint.setTextSize(dp(11));
                    float lineY = w.y + dp(16);
                    canvas.drawText(w.text + ": 0x" + String.format(java.util.Locale.ROOT, "%04X", bitmask), w.x + dp(8), lineY, paint);
                    lineY += dp(14);
                    for (int b = 0; b < 3 && lineY < w.y + w.h - dp(4); b++) {
                        boolean bitOn = (bitmask & (1 << b)) != 0;
                        paint.setColor(bitOn ? Color.rgb(22, 163, 74) : Color.rgb(148, 163, 184));
                        canvas.drawCircle(w.x + dp(12), lineY - dp(4), dp(4), paint);
                        paint.setColor(w.textColor);
                        canvas.drawText(w.bitListLabels[b] + ": " + (bitOn ? "1" : "0"), w.x + dp(22), lineY, paint);
                        lineY += dp(14);
                    }
                } else if ("Прогресс-бар".equals(w.type)) {
                    double val = 0.0;
                    if (liveValues.containsKey(i)) {
                        try { val = Double.parseDouble(liveValues.get(i)); } catch (Exception ignored) {}
                    }
                    double ratio = Math.max(0.0, Math.min(1.0, (val - w.minValue) / Math.max(0.001, w.maxValue - w.minValue)));
                    paint.setColor(Color.rgb(203, 213, 225));
                    canvas.drawRoundRect(w.x + dp(8), w.y + dp(24), w.x + w.w - dp(8), w.y + w.h - dp(8), dp(4), dp(4), paint);
                    paint.setColor(Color.rgb(37, 99, 235));
                    canvas.drawRoundRect(w.x + dp(8), w.y + dp(24), w.x + dp(8) + (float) ((w.w - dp(16)) * ratio), w.y + w.h - dp(8), dp(4), dp(4), paint);
                    paint.setColor(w.textColor);
                    paint.setTextSize(dp(12));
                    canvas.drawText(w.text + ": " + String.format(java.util.Locale.ROOT, "%.1f", val), w.x + dp(10), w.y + dp(18), paint);
                } else if ("Шкала / Стрелка".equals(w.type)) {
                    double val = 0.0;
                    if (liveValues.containsKey(i)) {
                        try { val = Double.parseDouble(liveValues.get(i)); } catch (Exception ignored) {}
                    }
                    double ratio = Math.max(0.0, Math.min(1.0, (val - w.minValue) / Math.max(0.001, w.maxValue - w.minValue)));
                    float cx = w.x + w.w / 2f, cy = w.y + w.h - dp(15);
                    float radius = Math.min(w.w, w.h * 1.6f) * 0.42f;
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(3));
                    paint.setColor(Color.rgb(100, 116, 139));
                    canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, 180, 180, false, paint);
                    float angleRad = (float) Math.toRadians(180 + ratio * 180);
                    float nx = cx + (float) Math.cos(angleRad) * (radius - dp(6));
                    float ny = cy + (float) Math.sin(angleRad) * (radius - dp(6));
                    paint.setStrokeWidth(dp(2));
                    paint.setColor(Color.rgb(220, 38, 38));
                    canvas.drawLine(cx, cy, nx, ny, paint);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx, cy, dp(4), paint);
                    paint.setColor(w.textColor);
                    paint.setTextSize(dp(11));
                    canvas.drawText(w.text + " " + String.format(java.util.Locale.ROOT, "%.1f", val), w.x + dp(8), w.y + dp(16), paint);
                } else if ("График / Тренд".equals(w.type)) {
                    paint.setColor(Color.rgb(51, 65, 85));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(1));
                    canvas.drawRect(w.x + dp(8), w.y + dp(20), w.x + w.w - dp(8), w.y + w.h - dp(8), paint);
                    paint.setColor(Color.rgb(34, 197, 94));
                    canvas.drawLine(w.x + dp(10), w.y + w.h - dp(12), w.x + w.w / 2f, w.y + dp(28), paint);
                    canvas.drawLine(w.x + w.w / 2f, w.y + dp(28), w.x + w.w - dp(10), w.y + w.h - dp(22), paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.rgb(248, 250, 252));
                    paint.setTextSize(dp(10));
                    canvas.drawText(w.text, w.x + dp(10), w.y + dp(15), paint);
                } else if ("Индикатор".equals(w.type) || "Двухпозиционный индикатор".equals(w.type)) {
                    String indicatorValue = liveValues.get(i);
                    Bitmap lamp = originalIndicatorBitmap(w, isActiveModbusValue(indicatorValue));
                    paint.setAlpha(255);
                    // Original ImageView uses FIT_XY and ten raw pixels of padding.
                    if (lamp != null && rect.width() > 20 && rect.height() > 20) {
                        canvas.drawBitmap(lamp, null, new RectF(rect.left + 10, rect.top + 10,
                                rect.right - 10, rect.bottom - 10), paint);
                    }
                    boolean failed = lamp == null || (indicatorValue != null
                            && (indicatorValue.startsWith("ERR") || indicatorValue.startsWith("CFG")));
                    if (failed) {
                        Bitmap warning = originalBitmap(R.drawable.kc_warning_32);
                        if (warning != null) canvas.drawBitmap(warning, rect.right - warning.getWidth(),
                                rect.bottom - warning.getHeight(), paint);
                    }
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
                } else if ("Звуковой сигнал".equals(w.type)) {
                    boolean active = w.soundActive;
                    if (liveValues.containsKey(i)) {
                        try { active = Double.parseDouble(liveValues.get(i)) >= w.soundThreshold; } catch (Exception ignored) {}
                    }
                    paint.setColor(active ? Color.rgb(239, 68, 68) : Color.rgb(100, 116, 139));
                    canvas.drawCircle(w.x + dp(16), w.y + w.h / 2, dp(6), paint);
                    paint.setColor(w.textColor);
                    String soundLabel = (active ? "🔊 " : "🔇 ") + w.text + (active ? " [АЛАРМ]" : " [ОК]");
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(32)) / Math.max(4f, soundLabel.length() * 0.58f))));
                    canvas.drawText(soundLabel, w.x + dp(28), w.y + w.h / 2 + dp(5), paint);
                } else if ("Переход на экран".equals(w.type)) {
                    paint.setColor(w.textColor);
                    String navLabel = "🔗 " + w.text + " (Экран " + (w.targetScreenIndex + 1) + ")";
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(16)) / Math.max(4f, navLabel.length() * 0.58f))));
                    canvas.drawText(navLabel, w.x + dp(8), w.y + w.h / 2 + dp(5), paint);
                } else if ("Многопозиционное фото".equals(w.type)) {
                    int imgIdx = 0;
                    if (liveValues.containsKey(i)) {
                        try { imgIdx = (int) Math.round(Double.parseDouble(liveValues.get(i))); } catch (Exception ignored) {}
                    }
                    if (imgIdx < 0 || imgIdx >= 16) imgIdx = 0;
                    String imgPath = w.imagePathStates[imgIdx];
                    Bitmap bitmap = loadCachedBitmap(imgPath);
                    if (bitmap != null) {
                        canvas.drawBitmap(bitmap, null, rect, paint);
                    } else {
                        String imgLabel = "🖼 " + w.text + " [" + imgIdx + "]: " + (imgPath == null ? "" : imgPath);
                        paint.setColor(w.textColor);
                        if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(16)) / Math.max(4f, imgLabel.length() * 0.58f))));
                        canvas.drawText(imgLabel, w.x + dp(8), w.y + w.h / 2 + dp(5), paint);
                    }
                } else if ("Голосовое сообщение".equals(w.type)) {
                    int voiceIdx = 0;
                    if (liveValues.containsKey(i)) {
                        try { voiceIdx = (int) Math.round(Double.parseDouble(liveValues.get(i))); } catch (Exception ignored) {}
                    }
                    if (voiceIdx < 0 || voiceIdx >= 16) voiceIdx = 0;
                    String voiceLabel = "🗣 " + w.text + " [" + voiceIdx + "]: " + w.voiceMessages[voiceIdx];
                    paint.setColor(w.textColor);
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(16)) / Math.max(4f, voiceLabel.length() * 0.58f))));
                    canvas.drawText(voiceLabel, w.x + dp(8), w.y + w.h / 2 + dp(5), paint);
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
        }

        private final OperatorGesture operatorGesture = new OperatorGesture();

        private int hitRuntimeWidget(float x, float y) {
            for (int i = widgets.size() - 1; i >= 0; i--) {
                Widget w = widgets.get(i);
                if (w.visible && !isFbd(w) && x >= w.x && x <= w.x + w.w && y >= w.y && y <= w.y + w.h) return i;
            }
            return -1;
        }

        private boolean onOperatorTouch(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN)
                operatorGesture.begin(hitRuntimeWidget(x, y), x, y, SystemClock.elapsedRealtime());
            else if (action == MotionEvent.ACTION_MOVE) operatorGesture.move(x, y, dp(5));
            else if (action == MotionEvent.ACTION_UP) {
                int target = operatorGesture.end(hitRuntimeWidget(x, y), x, y, SystemClock.elapsedRealtime(), dp(5));
                if (target >= 0 && target < widgets.size()) activateRuntimeWidget(target, x, y);
                invalidate();
            } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN)
                operatorGesture.cancel();
            return true;
        }

        private void activateRuntimeWidget(int index, float x, float y) {
            Widget picked = widgets.get(index);
            if ("Задатчик".equals(picked.type) && y >= picked.y + dp(29)) {
                adjustSetpoint(picked, x < picked.x + picked.w / 2 ? -1 : 1);
            } else if ("Кнопка".equals(picked.type)) {
                picked.buttonState = !picked.buttonState;
                persistProject();
                BindingRequest request = parseBinding(-1, picked.connectionInfo);
                if (request != null && request.functionCode == 3) {
                    final boolean btnOn = picked.buttonState;
                    modbusExecutor.execute(() -> {
                        try {
                            boolean ok = writeModbusRegisterStatic(request.host, request.port, request.unit, request.address, btnOn ? 1 : 0);
                            if (!ok) throw new IOException("Устройство отклонило запись регистра");
                        } catch (Exception ex) {
                            android.util.Log.e("BLOODSKY", "Modbus write failed", ex);
                            runOnUiThread(() -> {
                                picked.buttonState = !btnOn;
                                persistProject();
                                if (editor != null) editor.invalidate();
                                Toast.makeText(MainActivity.this, "Ошибка записи Modbus: " + (ex.getMessage() != null ? ex.getMessage() : "ошибка связи"), Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                }
                Toast.makeText(MainActivity.this, picked.text + (picked.buttonState ? ": ВКЛ" : ": ВЫКЛ"), Toast.LENGTH_SHORT).show();
            } else if ("FBD Вход".equals(picked.type)) {
                picked.inputValue = !picked.inputValue;
                persistProject();
                Toast.makeText(MainActivity.this, "Вход FBD = " + (picked.inputValue ? "1" : "0"), Toast.LENGTH_SHORT).show();
            } else if ("Переход на экран".equals(picked.type)) {
                if (picked.targetScreenIndex >= 0 && picked.targetScreenIndex < screens.size()) {
                    activateScreen(picked.targetScreenIndex);
                    Toast.makeText(MainActivity.this, "Переход на Экран " + (picked.targetScreenIndex + 1), Toast.LENGTH_SHORT).show();
                } else if (picked.targetUrl != null && !picked.targetUrl.trim().isEmpty()) {
                    try {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(picked.targetUrl));
                        startActivity(browserIntent);
                    } catch (Exception ex) {
                        Toast.makeText(MainActivity.this, "Не удалось открыть URL: " + picked.targetUrl, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // No properties dialogs in operator mode.
                }
            } else {
                // No properties dialogs in operator mode.
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (operatorMode) return onOperatorTouch(event);
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
                    editSelectedWidget();
                }
                invalidate();
                return true;
            }
            return true;
        }
    }
}


