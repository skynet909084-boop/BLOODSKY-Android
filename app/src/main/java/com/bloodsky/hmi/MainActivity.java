Warning: truncated output (original token count: 68292)
Total output lines: 4797

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
        sysRegLabel.setText("Системны…38292 tokens truncated…cit edge selection avoids deleting unrelated FBD connections. */
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
            // Original FB_CTU draw9(): header 1/4, input +,-,R at
            // 3/8, 5/8, 7/8 and numeric Q at 3/8. Legacy CTU is unaffected.
            if ("FBD CRAFT CTU".equals(kind)
                    || "FBD CRAFT SEL".equals(kind))
                return output ? 3f / 8f : (2f * i + 3f) / 8f;
            // Original FB_COMPARE draw15(): IN1=1/4, IN2=3/4,
            // BOOL Q=1/2; unlike the generic source-white graph fallback.
            if ("FBD CRAFT COMPARE".equals(kind))
                return output ? 0.5f : (i == 0 ? 0.25f : 0.75f);
            if ("FBD CRAFT COMPARE FLOAT".equals(kind)
                    || "FBD CRAFT ARITHMETIC INT".equals(kind)
                    || "FBD CRAFT ARITHMETIC FLOAT".equals(kind))
                return output ? 0.5f : (i == 0 ? 0.25f : 0.75f);
            if ("FBD CRAFT MOVE".equals(kind))
                return output ? 0.5f : (i == 0 ? 0.5f : 5f / 6f);
            // Original FB_RS draw8(): header 1/3, R/S at 1/2 and 5/6,
            // output Q at 1/2. Wire anchors and drawn contacts must match.
            if ("FBD RS".equals(kind))
                return output ? 0.5f : (i == 0 ? 0.5f : 5f / 6f);
            if ("FBD CRAFT ARRAY".equals(kind))
                return output ? 3f / 8f : (2f * i + 3f) / 8f;
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
                        || "FBD CRAFT BLINK".equals(kind) || "FBD CRAFT TIMER".equals(kind)
                        || "FBD CRAFT MX".equals(kind))
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
        if ("FBD CRAFT TIMER".equals(kind)) {
            widget.craftTimerState = FbdCraftNativeTimer.State.initial();
        }
        if ("FBD CRAFT ARRAY".equals(kind)) {
            widget.craftArrayLastEnable = false;
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
        if ("FBD CRAFT COMPARE".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"IN1", "IN2"};
        if ("FBD CRAFT COMPARE FLOAT".equals(kind)
                || "FBD CRAFT ARITHMETIC INT".equals(kind)
                || "FBD CRAFT ARITHMETIC FLOAT".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"IN1", "IN2"};
        if ("FBD СРАВНЕНИЕ".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"A", "B"};
        if ("FBD MOVE".equals(kind))
            return output ? new String[]{"OUT"} : new String[]{"IN"};
        if ("FBD CRAFT CHANGE I".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"I"};
        if ("FBD CRAFT CHANGE F".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"F"};
        if ("FBD CRAFT MOVE".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"EN", "I"};
        if ("FBD CRAFT MX".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"EN", "I"};
        if ("FBD CRAFT ARRAY".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"EN", "DT", "ID"};
        if ("FBD CRAFT SEL".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"IN", "I1", "I2"};
        if ("FBD SEL".equals(kind))
            return output ? new String[]{"OUT"} : new String[]{"G", "IN0", "IN1"};
        if ("FBD POW".equals(kind)) return output ? new String[]{"OUT"} : new String[]{"BASE", "EXP"};
        if ("FBD SIN".equals(kind) || "FBD ABS".equals(kind)
                || "FBD COS".equals(kind) || "FBD TAN".equals(kind) || "FBD SQRT".equals(kind)
                || "FBD FLOATTOINT".equals(kind) || "FBD INTTOFLOAT".equals(kind))
            return output ? new String[]{"OUT"} : new String[]{"IN"};
        if ("FBD CRAFT LOG".equals(kind) || "FBD CRAFT WORD TO FLOAT".equals(kind))
            return output ? new String[]{"OUT"} : new String[]{"IN"};
        if ("FBD TON".equals(kind) || "FBD TOF".equals(kind)
                || "FBD TP".equals(kind) || "FBD CRAFT BLINK".equals(kind)
                || "FBD CRAFT TIMER".equals(kind))
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
        // Change detection runtime is transient; source starts at 0/0.0f.
        // Saved old project state and vendor metadata remain untouched.
        FbdCraftChangeTransitions.IntState craftChangeIntState = FbdCraftChangeTransitions.IntState.initial();
        FbdCraftChangeTransitions.FloatState craftChangeFloatState = FbdCraftChangeTransitions.FloatState.initial();
        // Blink runtime epoch is not saved; a newly opened project starts idle.
        FbdCraftNativeTimer.State craftBlinkState = FbdCraftNativeTimer.State.initial();
        FbdCraftNativeTimer.State craftTimerState = FbdCraftNativeTimer.State.initial();
        boolean craftArrayLastEnable = false;
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


