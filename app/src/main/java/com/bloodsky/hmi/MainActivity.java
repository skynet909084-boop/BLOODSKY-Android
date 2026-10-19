Warning: truncated output (original token count: 143238)
Total output lines: 9183

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
import android.graphics.Rect;
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
    private static final ThreadLocal<CraftSystemRegisterBank> ACTIVE_FBD_SYSTEM_REGISTER_BANK = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ACTIVE_FBD_SYSTEM_REGISTER_WRITES = new ThreadLocal<>();
    private static final String ACTIVE_PROJECT = "active_project";
    private static final String PROJECT_CATALOG = "project_catalog_v1";
    private static final String TELEGRAM_PREFS = "bloodsky_telegram";
    private static final int REQUEST_EXPORT_PROJECT = 7101;
    private static final int REQUEST_IMPORT_PROJECT = 7102;
    private static final int REQUEST_PICK_MEDIA = 7103;
    private static final int REQUEST_EXPORT_PROJECT_ZIP = 7104;
    private static final int REQUEST_IMPORT_CRAFT_MACRO = 7105;
    private static final int REQUEST_EXPORT_CRAFT_MACRO = 7106;
    private static final int REQUEST_EXPORT_LOCAL_ARCHIVE = 7107;
    private byte[] pendingCraftMacroExport;
    private String pendingArchiveProjectId, pendingArchiveWidgetKey, pendingArchiveTitle;
    private EditText pendingMediaField;
    private String projectName = "Мой проект";
    private JSONObject projectExtraFields = new JSONObject();
    private final CraftSystemRegisterBank systemRegisterBank = new CraftSystemRegisterBank();
    private boolean systemRegisterBankPersistable = true;
    private LinearLayout root;
    private android.widget.PopupWindow sourceOverflowPopup;
    private EditorCanvas editor;
    private final List<Widget> widgets = new ArrayList<>();
    private final List<Screen> screens = new ArrayList<>();
    private int currentScreenIndex = 0;
    private NestedMacroEditSession nestedMacroEdit;
    private Widget clipboardWidget;
    private boolean operatorMode = false;
    private boolean fbdWireMode = false;
    private final FbdWireHistory fbdWireHistory = new FbdWireHistory();
    private int wireSource = -1;
    private boolean[] fbdCacheValid = new boolean[0];
    private boolean[] fbdCacheValues = new boolean[0];
    private double[] fbdCacheNumeric = new double[0];
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable craftServiceRegisterTicker = new Runnable() {
        @Override public void run() {
            if (!activityResumed || editor == null) return;
            updateCraftDeviceRegisters();
            mainHandler.postDelayed(this, 1000L);
        }
    };
    private final ExecutorService modbusExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService craftSensorExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService telegramExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService homeAssistantExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService yandexExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService smsExecutor = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ConcurrentHashMap<Widget, CraftRegisterGateway.State> craftGatewayStates = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Widget, CraftBitGateway.State> craftBitGatewayStates = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Widget, Boolean> craftArchiveTriggerHigh = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<Widget> craftArchiveTriggerEdges = java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<Widget, Boolean>());
    private FbdCraftMqttRuntime craftMqttRuntime;
    private final Map<Integer, String> liveValues = new HashMap<>();
    private boolean modbusPollingActive = false;
    private boolean activityResumed = false;
    private android.hardware.SensorManager fbdSensorManager;
    private boolean fbdSensorsRegistered;
    private android.location.LocationManager fbdLocationManager;
    private boolean fbdLocationRegistered, fbdLocationPermissionRequested;
    private static final int REQUEST_FBD_SPEED_LOCATION = 6107;
    private static final int REQUEST_VOICE_PERMISSION = 6201;
    private static final int REQUEST_SMS_SEND_PERMISSION = 6202;
    private static final int REQUEST_SMS_RECEIVE_PERMISSION = 6203;
    private android.content.BroadcastReceiver incomingSmsReceiver;
    private boolean smsReceivePermissionRequested;
    private static final int VOICE_LOCAL = 1;
    private static final int VOICE_ONLINE = 2;
    private int pendingVoiceMode;
    private android.speech.SpeechRecognizer speechRecognizer;
    private final android.location.LocationListener fbdLocationListener = new android.location.LocationListener() {
        @Override public void onLocationChanged(android.location.Location location) {
            if (location == null || !operatorMode || editor == null) return;
            double kmh = Double.isNaN(location.getSpeed()) ? 0.0 : location.getSpeed() * 3.6;
            for (Widget widget : widgets) if ("FBD CRAFT SENSOR SPEED".equals(widget.type)) {
                widget.craftSensorValue = kmh; widget.craftSensorValid = true;
            }
            editor.invalidate();
        }
        @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    };
    private final android.hardware.SensorEventListener fbdSensorListener = new android.hardware.SensorEventListener() {
        @Override public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {}
        @Override public void onSensorChanged(android.hardware.SensorEvent event) {
            if (!operatorMode || editor == null || event.values == null) return;
            boolean changed = false;
            if (event.sensor.getType() == android.hardware.Sensor.TYPE_LIGHT && event.values.length > 0) {
                for (Widget widget : widgets) if ("FBD CRAFT SENSOR LIGHT".equals(widget.type)) {
                    widget.craftSensorValue = Math.max(0, (int)event.values[0]); widget.craftSensorValid = true; changed = true;
                }
            } else if (event.sensor.getType() == android.hardware.Sensor.TYPE_ORIENTATION && event.values.length >= 3) {
                for (Widget widget : widgets) if ("FBD CRAFT SENSOR ORIENTATION".equals(widget.type)) {
                    widget.craftOrientationValues[0] = event.values[0];
                    widget.craftOrientationValues[1] = event.values[1];
                    widget.craftOrientationValues[2] = event.values[2];
                    widget.craftSensorValid = true; changed = true;
                }
            }
            if (changed) editor.invalidate();
        }
    };
    private final PollSessionGuard pollSession = new PollSessionGuard();
    private volatile boolean modbusPollInFlight = false;
    private final Runnable modbusPollRunnable = new Runnable() {
        @Override public void run() {
            if (!modbusPollingActive || editor == null) return;
            pollBoundWidgets();
            pollCraftOdTempSources();
            mainHandler.postDelayed(this, 1000L);
        }
    };

    private final java.util.Map<String, MediaPlayer> craftAlarmPlayers = new java.util.HashMap<>();
    private final java.util.Map<String, ToneGenerator> craftAlarmTones = new java.util.HashMap<>();
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
        updateCraftDeviceRegisters();
        if (editor != null) {
            mainHandler.removeCallbacks(craftServiceRegisterTicker);
            mainHandler.postDelayed(craftServiceRegisterTicker, 1000L);
        }
        if (editor != null) { startModbusPolling(); startFbdSensors(); startCraftMqtt(); }
        refreshIncomingSmsReceiver();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_VOICE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
                startVoiceRecognition(pendingVoiceMode);
            else Toast.makeText(this, "Для голосового управления нужен доступ к микрофону", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_SMS_SEND_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Разрешение SMS включено; уведомление придёт при следующем новом событии", Toast.LENGTH_LONG).show();
            else Toast.makeText(this, "Отправка SMS отключена без разрешения SEND_SMS", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_SMS_RECEIVE_PERMISSION) {
            smsReceivePermissionRequested = false;
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                refreshIncomingSmsReceiver();
            } else {
                Toast.makeText(this, "Входящее SMS отключено без разрешения RECEIVE_SMS", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode != REQUEST_FBD_SPEED_LOCATION) return;
        fbdLocationPermissionRequested = false;
        if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
            startFbdSensors();
    }

    @Override protected void onPause() {
        if (editor != null) {
            editor.endCraftBitButton();
            editor.operatorMomentaryCandidate = -1;
        }
        activityResumed = false;
        mainHandler.removeCallbacks(craftServiceRegisterTicker);
        unregisterIncomingSmsReceiver();
        stopModbusPolling();
        stopFbdSensors();
        stopCraftMqtt();
        releaseVoiceRecognizer();
        resetCraftAlarmEdges();
        stopAllCraftAlarmPlayback();
        if (textToSpeech != null) try { textToSpeech.stop(); } catch (Exception ignored) {}
        super.onPause();
    }

    @Override protected void onDestroy() {
        unregisterIncomingSmsReceiver();
        stopModbusPolling();
        stopFbdSensors();
        stopCraftMqtt();
        releaseVoiceRecognizer();
        editor = null;
        modbusExecutor.shutdownNow();
        craftSensorExecutor.shutdownNow();
        telegramExecutor.shutdownNow();
        homeAssistantExecutor.shutdownNow();
        yandexExecutor.shutdownNow();
        smsExecutor.shutdownNow();
        resetCraftAlarmEdges();
        stopAllCraftAlarmPlayback();
        mainHandler.removeCallbacks(craftServiceRegisterTicker);
        CraftServiceRegisters.global().setActions(null);
        if (textToSpeech != null) {
            try { textToSpeech.stop(); textToSpeech.shutdown(); } catch (Exception ignored) {}
            textToSpeech = null;
        }
        super.onDestroy();
    }

    private void playCraftAlarmAlert(String alarmKey, String pathOrUri, int craftAlarmSoundIndex, boolean loop) {
        stopCraftAlarmAlert(alarmKey);
        MediaPlayer player = null;
        try {
            int alarmResource = craftAlarmResource(craftAlarmSoundIndex);
            if (alarmResource != 0) {
                player = MediaPlayer.create(this, alarmResource);
                if (player == null) return;
            } else if (pathOrUri != null && pathOrUri.startsWith("content://")) {
                player = new MediaPlayer();
                player.setDataSource(this, Uri.parse(pathOrUri));
                player.prepare();
            } else if (pathOrUri != null && !pathOrUri.trim().isEmpty() && !"Аларм".equals(pathOrUri)) {
                String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
                java.io.File file = ProjectMediaService.resolveMediaFile(getFilesDir(), json, pathOrUri);
                player = new MediaPlayer();
                player.setDataSource(file.getAbsolutePath());
                player.prepare();
            } else {
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_ALARM, 80);
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350);
                craftAlarmTones.put(alarmKey, tone);
                return;
            }
            player.setLooping(loop);
            MediaPlayer activePlayer = player;
            player.setOnCompletionListener(completed -> {
                if (craftAlarmPlayers.get(alarmKey) == completed) {
                    craftAlarmPlayers.remove(alarmKey);
                    try { completed.release(); } catch (Exception ignored) { }
                }
            });
            craftAlarmPlayers.put(alarmKey, activePlayer);
            player.start();
        } catch (Exception ex) {
            if (player != null) {
                try { player.release(); } catch (Exception ignored) { }
            }
            android.util.Log.e("BLOODSKY", "Craft alarm error", ex);
            try {
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_ALARM, 80);
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 250);
                craftAlarmTones.put(alarmKey, tone);
            } catch (Exception ignored) { }
        }
    }

    private void stopCraftAlarmAlert(String alarmKey) {
        MediaPlayer player = craftAlarmPlayers.remove(alarmKey);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
        }
        ToneGenerator tone = craftAlarmTones.remove(alarmKey);
        if (tone != null) {
            try { tone.stopTone(); } catch (Exception ignored) { }
            try { tone.release(); } catch (Exception ignored) { }
        }
    }

    private void stopAllCraftAlarmPlayback() {
        for (MediaPlayer player : new java.util.ArrayList<>(craftAlarmPlayers.values())) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
        }
        craftAlarmPlayers.clear();
        for (ToneGenerator tone : new java.util.ArrayList<>(craftAlarmTones.values())) {
            try { tone.stopTone(); } catch (Exception ignored) { }
            try { tone.release(); } catch (Exception ignored) { }
        }
        craftAlarmTones.clear();
    }

    private void stopCurrentScreenAlarms() {
        for (int index = 0; index < widgets.size(); index++) {
            Widget widget = widgets.get(index);
            if ("Звуковой сигнал".equals(widget.type)) {
                widget.lastSoundState = false;
                stopCraftAlarmAlert(currentScreenIndex + ":" + index);
            }
        }
    }

    private void resetCraftAlarmEdges() {
        for (Screen screen : screens) {
            for (Widget widget : screen.widgets) {
                if ("Звуковой сигнал".equals(widget.type)) widget.lastSoundState = false;
            }
        }
        for (Widget widget : widgets) {
            if ("Звуковой сигнал".equals(widget.type)) widget.lastSoundState = false;
        }
    }

    private static int craftAlarmResource(int index) {
        switch (index) {
            case 1: return R.raw.kc_alarm1;
            case 2: return R.raw.kc_alarm2;
            case 3: return R.raw.kc_alarm3;
            case 4: return R.raw.kc_alarm4;
            case 5: return R.raw.kc_alarm5;
            default: return 0;
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
        CraftServiceRegisters.global().setActions(null);
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
       …128238 tokens truncated…-1;
                if (candidate < 0 && target >= 0 && target < widgets.size()) activateRuntimeWidget(target, x, y);
                invalidate();
            } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
                operatorGesture.cancel();
                endCraftBitButton();
                operatorMomentaryCandidate = -1;
            }
            return true;
        }

        private void openCraftBitList(Widget widget, int widgetIndex) {
            String[] allLabels = CraftBitList.fromJson(widget.extraFields.optJSONArray("craftListLabels"));
            java.util.ArrayList<String> choices = new java.util.ArrayList<>();
            java.util.ArrayList<Integer> bits = new java.util.ArrayList<>();
            for (int bit = 0; bit < 16; bit++) if (allLabels[bit] != null && !allLabels[bit].trim().isEmpty()) {
                choices.add(allLabels[bit]); bits.add(bit);
            }
            if (choices.isEmpty()) {
                Toast.makeText(MainActivity.this, "У списка нет подписанных пунктов. Настрой его в редакторе.", Toast.LENGTH_LONG).show();
                return;
            }
            if (widget.extraFields.optBoolean("craftListCheckNoChange", false)) {
                int shownMask = widget.extraFields.optInt("craftListInitialMask", 0) & 0xffff;
                String liveValue = liveValues.get(widgetIndex);
                if (liveValue != null) try { shownMask = CraftBitList.fromRegister(Integer.parseInt(liveValue)); } catch (Exception ignored) { }
                String[] readOnlyChoices = new String[choices.size()];
                for (int i = 0; i < choices.size(); i++)
                    readOnlyChoices[i] = ((shownMask & (1 << bits.get(i))) != 0 ? "✓  " : "○  ") + choices.get(i);
                new AlertDialog.Builder(MainActivity.this).setTitle(widget.text == null ? "Список" : widget.text)
                        .setItems(readOnlyChoices, null)
                        .setMessage("Значения доступны только для просмотра в рабочем режиме")
                        .setPositiveButton("Закрыть", null).show();
                return;
            }
            int initialMask = widget.extraFields.optInt("craftListInitialMask", 0) & 0xffff;
            String current = liveValues.get(widgetIndex);
            if (current != null) try { initialMask = CraftBitList.fromRegister(Integer.parseInt(current)); } catch (Exception ignored) { }
            final int[] mask = {initialMask};
            boolean[] checked = new boolean[bits.size()];
            for (int i = 0; i < bits.size(); i++) checked[i] = (mask[0] & (1 << bits.get(i))) != 0;
            final BindingRequest binding = parseBinding(-1, widget.connectionInfo);
            if (widget.connectionInfo != null && !widget.connectionInfo.trim().isEmpty() && binding == null) {
                Toast.makeText(MainActivity.this, "Проверь TCP привязку битового списка", Toast.LENGTH_LONG).show(); return;
            }
            if (binding != null && binding.functionCode != 3) {
                Toast.makeText(MainActivity.this, "Битовый список использует holding-регистр FC03/FC06", Toast.LENGTH_LONG).show(); return;
            }
            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(widget.text == null || widget.text.isEmpty() ? "Выбери пункты" : widget.text)
                    .setMultiChoiceItems(choices.toArray(new String[0]), checked, (d, position, isChecked) -> {
                        int bit = bits.get(position);
                        int next = isChecked ? mask[0] | (1 << bit) : mask[0] & ~(1 << bit);
                        next &= 0xffff;
                        mask[0] = next;
                        liveValues.put(widgetIndex, Integer.toString(next));
                        if (binding == null) {
                            try { widget.extraFields.put("craftListInitialMask", next); } catch (Exception ignored) { }
                            persistProject();
                            invalidate();
                            return;
                        }
                        final int committedMask = next;
                        modbusExecutor.execute(() -> {
                            try {
                                if (!operatorMode || !activityResumed || !widgets.contains(widget)) throw new IOException("Рабочий режим остановлен");
                                new ModbusTcpClient(binding.host, binding.port).writeSingle(binding.unit, binding.address, committedMask);
                            } catch (Exception failure) {
                                runOnUiThread(() -> {
                                    if (!widgets.contains(widget) || !operatorMode || widgets.indexOf(widget) != widgetIndex) return;
                                    if (mask[0] == committedMask) {
                                        mask[0] = committedMask ^ (1 << bit);
                                        liveValues.put(widgetIndex, Integer.toString(mask[0]));
                                    }
                                    invalidate();
                                    Toast.makeText(MainActivity.this, "Не удалось записать маску списка: " + describeModbusError(failure), Toast.LENGTH_LONG).show();
                                });
                            }
                        });
                        invalidate();
                    })
                    .setPositiveButton("Готово", null).setNegativeButton("Закрыть", null).create();
            dialog.show();
        }

        private void activateCraftConstantButton(Widget widget, int index) {
            BindingRequest binding = parseBinding(-1, widget.connectionInfo);
            if (!widget.extraFields.optBoolean("craftButtonConstantEnabled", false) || binding == null || binding.functionCode != 3) {
                Toast.makeText(MainActivity.this, "Задай TCP holding-регистр для этой константной кнопки", Toast.LENGTH_LONG).show(); return;
            }
            boolean floating = widget.extraFields.optInt("craftButtonConstantType", 0) == 1;
            final int integer;
            final int[] words;
            try {
                if (floating) { words = FbdCraftRegisterCodec.floatRegisters(CraftConstantButton.floating(widget.extraFields.optString("craftButtonConstant", "0")), false, false); integer = 0; }
                else { integer = CraftConstantButton.integer(widget.extraFields.optString("craftButtonConstant", "0")); words = null; }
            } catch (IllegalArgumentException invalid) { Toast.makeText(MainActivity.this, invalid.getMessage(), Toast.LENGTH_LONG).show(); return; }
            final boolean writeFloat = floating;
            modbusExecutor.execute(() -> {
                Exception failure = null;
                try {
                    if (!operatorMode || !activityResumed || !widgets.contains(widget)) throw new IOException("Рабочий режим остановлен");
                    ModbusTcpClient client = new ModbusTcpClient(binding.host, binding.port);
                    if (writeFloat) client.writeMultiple(binding.unit, binding.address, words);
                    else client.writeSingle(binding.unit, binding.address, integer);
                } catch (Exception error) { failure = error; }
                final Exception result = failure;
                runOnUiThread(() -> {
                    if (!widgets.contains(widget) || !operatorMode || widgets.indexOf(widget) != index) return;
                    if (result == null) liveValues.put(index, "OK"); else liveValues.put(index, "ERR: " + describeModbusError(result));
                    Toast.makeText(MainActivity.this, result == null ? "Константа записана" : "Не удалось записать константу: " + describeModbusError(result), Toast.LENGTH_LONG).show();
                    invalidate();
                });
            });
        }

        private void openCraftLinearSetter(Widget widget, int widgetIndex) {
            final int maximum = CraftLinearSetter.maximum(widget.extraFields);
            final int initial = CraftLinearSetter.value(widget.extraFields, liveValues.get(widgetIndex));
            final BindingRequest binding = parseBinding(-1, widget.connectionInfo);
            if (widget.connectionInfo != null && !widget.connectionInfo.trim().isEmpty() && (binding == null || binding.functionCode != 3)) {
                Toast.makeText(MainActivity.this, "Проверь TCP holding-регистр FC03/FC06", Toast.LENGTH_LONG).show(); return;
            }
            android.widget.LinearLayout form = new android.widget.LinearLayout(MainActivity.this);
            form.setOrientation(android.widget.LinearLayout.VERTICAL); form.setPadding(dp(22), dp(14), dp(22), dp(4));
            TextView current = new TextView(MainActivity.this); current.setText(initial + " / " + maximum); form.addView(current);
            android.widget.SeekBar slider = new android.widget.SeekBar(MainActivity.this); slider.setMax(maximum); slider.setProgress(initial);
            if (widget.extraFields.optBoolean("craftLinearVertical", false)) slider.setRotation(270f);
            form.addView(slider);
            slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) { current.setText(progress + " / " + maximum); }
                @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) { }
            });
            new AlertDialog.Builder(MainActivity.this).setTitle(widget.text == null || widget.text.isEmpty() ? "Линейный задатчик" : widget.text)
                    .setView(form).setNegativeButton("Отмена", null)
                    .setPositiveButton("Задать", (d, which) -> {
                        int next = slider.getProgress();
                        if (binding == null) {
                            try { widget.extraFields.put("craftLinearValue", next); } catch (Exception ignored) { }
                            liveValues.put(widgetIndex, Integer.toString(next)); persistProject(); invalidate(); return;
                        }
                        modbusExecutor.execute(() -> {
                            Exception failure = null;
                            try {
                                if (!operatorMode || !activityResumed || !widgets.contains(widget)) throw new IOException("Рабочий режим остановлен");
                                new ModbusTcpClient(binding.host, binding.port).writeSingle(binding.unit, binding.address, next);
                            } catch (Exception error) { failure = error; }
                            final Exception result = failure;
                            runOnUiThread(() -> {
                                if (!widgets.contains(widget) || !operatorMode || widgets.indexOf(widget) != widgetIndex) return;
                                if (result == null) {
                                    try { widget.extraFields.put("craftLinearValue", next); } catch (Exception ignored) { }
                                    liveValues.put(widgetIndex, Integer.toString(next)); persistProject();
                                } else Toast.makeText(MainActivity.this, "Не удалось записать значение: " + describeModbusError(result), Toast.LENGTH_LONG).show();
                                invalidate();
                            });
                        });
                    }).show();
        }

        private void openCraftNumericInput(Widget widget, int widgetIndex) {
            if (!widget.extraFields.optBoolean("craftNumericEnabled", false)) {
                Toast.makeText(MainActivity.this, "Открой свойства ID 8 и явно разреши ввод", Toast.LENGTH_LONG).show(); return;
            }
            boolean passwordRequired = widget.extraFields.optBoolean("craftNumericPasswordRequired", false);
            String passwordKey = widget.extraFields.optString("craftNumericPasswordKey", "");
            final String expectedPassword;
            try { expectedPassword = passwordRequired ? CraftNumericPasswordStore.read(MainActivity.this, passwordKey) : ""; }
            catch (Exception failure) { Toast.makeText(MainActivity.this, "Не удалось открыть сохранённый пароль", Toast.LENGTH_LONG).show(); return; }
            if (passwordRequired && expectedPassword.isEmpty()) {
                Toast.makeText(MainActivity.this, "Повторно задай пароль Craft в свойствах элемента", Toast.LENGTH_LONG).show(); return;
            }
            final BindingRequest binding = parseBinding(-1, widget.connectionInfo);
            if (widget.connectionInfo != null && !widget.connectionInfo.trim().isEmpty()
                    && (binding == null || binding.functionCode != 3
                    || (CraftNumericInput.registerCount(widget.extraFields) == 2 && binding.address >= 65535))) {
                Toast.makeText(MainActivity.this, "Проверь TCP holding-регистр FC03/FC06", Toast.LENGTH_LONG).show(); return;
            }
            LinearLayout form = new LinearLayout(MainActivity.this); form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(dp(22), dp(14), dp(22), dp(4));
            TextView range = new TextView(MainActivity.this);
            range.setText("Диапазон: " + widget.extraFields.optDouble("craftNumericMin", 0)
                    + " – " + widget.extraFields.optDouble("craftNumericMax", 1000)); form.addView(range);
            String initial = liveValues.get(widgetIndex);
            if (initial == null || initial.startsWith("ERR") || initial.startsWith("CFG")) initial = widget.text;
            if (initial == null || initial.isEmpty()) initial = "0";
            EditText value = propertyField(initial, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            value.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(15)}); form.addView(value);
            EditText password = null;
            if (passwordRequired) {
                password = propertyField("", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                password.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});
                password.setHint("Пароль оператора"); form.addView(password);
            }
            final EditText passwordField = password;
            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(widget.text == null || widget.text.isEmpty() ? "Числовой ввод" : widget.text)
                    .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Задать", null).create();
            dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (passwordRequired && (passwordField == null || !expectedPassword.equals(passwordField.getText().toString()))) {
                    Toast.makeText(MainActivity.this, "Неверный пароль", Toast.LENGTH_LONG).show(); return;
                }
                final double next;
                try { next = CraftNumericInput.parseCommit(value.getText().toString(), widget.extraFields); }
                catch (IllegalArgumentException invalid) { Toast.makeText(MainActivity.this, invalid.getMessage(), Toast.LENGTH_LONG).show(); return; }
                final String formatted = widget.extraFields.optInt("craftNumericMode", CraftNumericInput.INTEGER) == CraftNumericInput.FLOAT32
                        ? CraftNumericInput.truncateFloatDisplay((float) next, widget.extraFields) : Integer.toString((int) next);
                if (binding == null) {
                    widget.text = formatted; liveValues.put(widgetIndex, formatted);
                    persistProject(); invalidate(); dialog.dismiss(); return;
                }
                modbusExecutor.execute(() -> {
                    Exception failure = null;
                    try {
                        if (!operatorMode || !activityResumed || !widgets.contains(widget)) throw new IOException("Рабочий режим остановлен");
                        ModbusTcpClient client = new ModbusTcpClient(binding.host, binding.port);
                        if (CraftNumericInput.registerCount(widget.extraFields) == 2)
                            client.writeMultiple(binding.unit, binding.address, FbdCraftRegisterCodec.floatRegisters((float) next));
                        else client.writeSingle(binding.unit, binding.address, CraftNumericInput.integerRegister(next, widget.extraFields));
                    } catch (Exception error) { failure = error; }
                    final Exception result = failure;
                    runOnUiThread(() -> {
                        if (!widgets.contains(widget) || !operatorMode || widgets.indexOf(widget) != widgetIndex) return;
                        if (result == null) {
                            widget.text = formatted; liveValues.put(widgetIndex, formatted); persistProject(); dialog.dismiss();
                        } else Toast.makeText(MainActivity.this, "Не удалось записать значение: " + describeModbusError(result), Toast.LENGTH_LONG).show();
                        invalidate();
                    });
                });
            }));
            dialog.show(); value.requestFocus();
        }

        private void activateRuntimeWidget(int index, float x, float y) {
            Widget picked = widgets.get(index);
            if ("Архив Craft".equals(picked.type)) {
                showLocalArchive(picked);
            } else if ("Числовой ввод Craft".equals(picked.type)) {
                openCraftNumericInput(picked, index);
            } else if ("Задатчик".equals(picked.type) && y >= picked.y + dp(29)) {
                adjustSetpoint(picked, x < picked.x + picked.w / 2 ? -1 : 1);
            } else if ("Линейный задатчик Craft".equals(picked.type)) {
                openCraftLinearSetter(picked, index);
            } else if ("Список Craft".equals(picked.type)) {
                openCraftBitList(picked, index);
            } else if ("Константная кнопка Craft".equals(picked.type)) {
                activateCraftConstantButton(picked, index);
            } else if ("Кнопка".equals(picked.type)) {
                if (isCraftMomentaryButton(picked)) {
                    Toast.makeText(MainActivity.this, "Битовая команда работает, пока кнопку удерживают", Toast.LENGTH_SHORT).show();
                    return;
                }
                picked.buttonState = !picked.buttonState;
                persistProject();
                BindingRequest request = parseBinding(-1, picked.connectionInfo);
                if (request != null && request.functionCode == 3) {
                    final boolean btnOn = picked.buttonState;
                    modbusExecutor.execute(() -> {
                        Exception failure = null;
                        try {
                            if (!writeModbusRegisterStatic(request.host, request.port, request.unit, request.address, btnOn ? 1 : 0))
                                throw new IOException("Устройство отклонило запись регистра");
                        } catch (Exception error) { failure = error; }
                        final Exception result = failure;
                        if (result != null) runOnUiThread(() -> {
                            picked.buttonState = !btnOn;
                            persistProject();
                            if (editor != null) editor.invalidate();
                            Toast.makeText(MainActivity.this, "Ошибка записи Modbus: " + describeModbusError(result), Toast.LENGTH_LONG).show();
                        });
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
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = x; downY = y; marqueeX = x; marqueeY = y;
                downAt = SystemClock.elapsedRealtime();
                dragging = false; resizing = false; marqueeActive = false; selected = -1;
                dragOrigins.clear();
                boolean hit = false;
                for (int i = widgets.size() - 1; i >= 0; i--) {
                    Widget w = widgets.get(i);
                    if (!w.visible) continue;
                    if (x >= w.x && x <= w.x + w.w && y >= w.y && y <= w.y + w.h) {
                        if (!selectedWidgets.contains(i)) {
                            selectedWidgets.clear();
                            selectedWidgets.add(i);
                        }
                        selected = i; startX = w.x; startY = w.y; startW = w.w; startH = w.h;
                        float grip = dp(22);
                        resizing = selectedWidgets.size() == 1
                                && x >= w.x + w.w - grip && y >= w.y + w.h - grip;
                        hit = true;
                        break;
                    }
                }
                if (hit) {
                    groupMinX = groupMinY = Float.POSITIVE_INFINITY;
                    groupMaxX = groupMaxY = Float.NEGATIVE_INFINITY;
                    for (int index : selectedWidgets) {
                        if (index < 0 || index >= widgets.size()) continue;
                        Widget widget = widgets.get(index);
                        dragOrigins.put(index, new float[]{widget.x, widget.y});
                        groupMinX = Math.min(groupMinX, widget.x);
                        groupMinY = Math.min(groupMinY, widget.y);
                        groupMaxX = Math.max(groupMaxX, widget.x + widget.w);
                        groupMaxY = Math.max(groupMaxY, widget.y + widget.h);
                    }
                } else {
                    selectedWidgets.clear();
                    marqueeActive = true;
                }
                android.view.ViewParent parent = getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(hit || marqueeActive);
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = x - downX, dy = y - downY;
                if (marqueeActive) {
                    if (Math.abs(dx) + Math.abs(dy) > dp(5)) dragging = true;
                    marqueeX = x; marqueeY = y;
                    invalidate();
                    return true;
                }
                if (selected >= 0) {
                    if (Math.abs(dx) + Math.abs(dy) > dp(5)) dragging = true;
                    if (dragging) {
                        Widget w = widgets.get(selected);
                        if (resizing) {
                            int minW = dp(60), minH = dp(40);
                            float maxW = Math.max(minW, getWidth() - w.x);
                            float maxH = Math.max(minH, getHeight() - w.y);
                            w.w = Math.max(minW, Math.min(maxW, snapToGrid(startW + dx)));
                            w.h = Math.max(minH, Math.min(maxH, snapToGrid(startH + dy)));
                        } else if (selectedWidgets.size() > 1) {
                            float moveX = snapToGrid(dx), moveY = snapToGrid(dy);
                            moveX = CanvasSelectionGeometry.clampTranslation(
                                    moveX, groupMinX, groupMaxX, getWidth());
                            moveY = CanvasSelectionGeometry.clampTranslation(
                                    moveY, groupMinY, groupMaxY, getHeight());
                            for (Map.Entry<Integer, float[]> origin : dragOrigins.entrySet()) {
                                int index = origin.getKey();
                                if (index < 0 || index >= widgets.size()) continue;
                                Widget member = widgets.get(index);
                                member.x = origin.getValue()[0] + moveX;
                                member.y = origin.getValue()[1] + moveY;
                            }
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
            }
            if (action == MotionEvent.ACTION_UP) {
                android.view.ViewParent parent = getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
                if (marqueeActive) {
                    marqueeActive = false;
                    if (dragging) {
                        float left = Math.min(downX, marqueeX), top = Math.min(downY, marqueeY);
                        float right = Math.max(downX, marqueeX), bottom = Math.max(downY, marqueeY);
                        for (int i = 0; i < widgets.size(); i++) {
                            Widget w = widgets.get(i);
                            if (w.visible && CanvasSelectionGeometry.centerInside(
                                    w.x, w.y, w.w, w.h, left, top, right, bottom))
                                selectedWidgets.add(i);
                        }
                        if (!selectedWidgets.isEmpty()) selected = selectedWidgets.get(selectedWidgets.size() - 1);
                    }
                } else if (dragging) {
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
                dragOrigins.clear();
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                android.view.ViewParent parent = getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
                selected = -1; dragging = false; resizing = false; marqueeActive = false;
                dragOrigins.clear();
                invalidate();
                return true;
            }
            return true;
        }
    }
}


