Warning: truncated output (original token count: 193807)
Total output lines: 12175

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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    private static final int MAX_PROJECT_ARCHIVE_ENTRIES = 20_000;
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
    private static final int REQUEST_EXPORT_CRAFT_PROJECT = 7108;
    private static final int REQUEST_IMPORT_CRAFT_MACRO = 7105;
    private static final int REQUEST_EXPORT_CRAFT_MACRO = 7106;
    private static final int REQUEST_EXPORT_LOCAL_ARCHIVE = 7107;
    private byte[] pendingCraftMacroExport;
    private String pendingArchiveProjectId, pendingArchiveWidgetKey, pendingArchiveTitle;
    private EditText pendingMediaField;
    private AlertDialog activePropertiesDialog;
    private LinearLayout activePropertiesForm;
    private int pendingMediaWidgetIndex = -1;
    private String pendingMediaFieldPath;
    private int pendingMediaCopyCount;
    private final Map<AlertDialog, List<PendingMediaCopy>> pendingMediaCopies = new java.util.IdentityHashMap<>();
    private String projectName = "Мой проект";
    private JSONObject projectExtraFields = new JSONObject();
    private final CraftSystemRegisterBank systemRegisterBank = new CraftSystemRegisterBank();
    private CraftModbusTcpSlave modbusTcpSlave;
    private boolean systemRegisterBankPersistable = true;
    private LinearLayout root;
    private android.widget.PopupWindow sourceOverflowPopup;
    private EditorCanvas editor;
    private final List<Widget> widgets = new ArrayList<>();
    private final List<Screen> screens = new ArrayList<>();
    private int currentScreenIndex = 0;
    private NestedMacroEditSession nestedMacroEdit;
    private WidgetClipboard.Bundle clipboardWidgets;
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
    private volatile UsbSerialModbusConnection manualUsbConnection;
    private UsbSerialConnector manualUsbConnector;
    private int manualUsbScreenGeneration;
    private boolean manualUsbScreenVisible;
    private String craftNetworkScreen = "";
    private boolean craftServerListReturnsToDevices;
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
    private final java.util.Map<Widget, CraftTrendHistory[]> trendHistories =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
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
    private static final int REQUEST_CRAFT_BLUETOOTH_PERMISSION = 6204;
    private static final int REQUEST_CRAFT_PUSH_NOTIFICATION_PERMISSION = 6205;
    private int pendingCraftBluetoothSlot = -1;
    private boolean pendingCraftBluetoothReturnsToDevices;
    private String pendingCraftNotificationTitle, pendingCraftNotificationBody;
    private int pendingCraftNotificationId;
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
    private final java.util.Map<String, android.graphics.Movie> gifCache = new java.util.HashMap<>();

    private android.graphics.Movie loadCachedMovie(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        if (gifCache.containsKey(path)) return gifCache.get(path);
        android.graphics.Movie movie = null;
        try {
            String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
            movie = CraftGifAsset.decode(readProjectMediaData(path, json));
        } catch (Exception ignored) { }
        if (gifCache.size() >= 4) gifCache.clear();
        gifCache.put(path, movie);
        return movie;
    }

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
        if (!restoreMediaPickerState(state)) showHome();
    }

    private static final String STATE_RESTORE_MEDIA_PICKER = "restore_media_picker";
    private static final String STATE_MEDIA_WIDGET_INDEX = "media_widget_index";
    private static final String STATE_MEDIA_FIELD_PATH = "media_field_path";
    private static final String STATE_MEDIA_FORM_PATHS = "media_form_paths";
    private static final String STATE_MEDIA_FORM_TYPES = "media_form_types";
    private static final String STATE_MEDIA_FORM_VALUES = "media_form_values";

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingMediaField == null || activePropertiesDialog == null
                || activePropertiesForm == null || editor == null) return;
        String fieldPath = viewPath(activePropertiesForm, pendingMediaField, "");
        if (fieldPath == null) return;
        ArrayList<String> paths = new ArrayList<>();
        ArrayList<String> types = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        collectPropertyFormState(activePropertiesForm, "", paths, types, values);
        outState.putBoolean(STATE_RESTORE_MEDIA_PICKER, true);
        outState.putInt(STATE_MEDIA_WIDGET_INDEX, editor.selected);
        outState.putString(STATE_MEDIA_FIELD_PATH, fieldPath);
        outState.putStringArrayList(STATE_MEDIA_FORM_PATHS, paths);
        outState.putStringArrayList(STATE_MEDIA_FORM_TYPES, types);
        outState.putStringArrayList(STATE_MEDIA_FORM_VALUES, values);
    }

    private boolean restoreMediaPickerState(Bundle state) {
        if (state == null || !state.getBoolean(STATE_RESTORE_MEDIA_PICKER, false) || !loadProject()) return false;
        showEditor();
        int widgetIndex = state.getInt(STATE_MEDIA_WIDGET_INDEX, -1);
        if (editor == null || widgetIndex < 0 || widgetIndex >= widgets.size()) return false;
        editor.selected = widgetIndex;
        editor.selectedWidgets.clear();
        editor.selectedWidgets.add(widgetIndex);
        editSelectedWidget();
        if (activePropertiesDialog == null || activePropertiesForm == null) return false;
        restorePropertyFormState(activePropertiesForm,
                state.getStringArrayList(STATE_MEDIA_FORM_PATHS),
                state.getStringArrayList(STATE_MEDIA_FORM_TYPES),
                state.getStringArrayList(STATE_MEDIA_FORM_VALUES));
        View target = findViewByPath(activePropertiesForm, state.getString(STATE_MEDIA_FIELD_PATH, ""));
        if (!(target instanceof EditText)) return false;
        pendingMediaField = (EditText) target;
        pendingMediaWidgetIndex = widgetIndex;
        pendingMediaFieldPath = state.getString(STATE_MEDIA_FIELD_PATH);
        return true;
    }

    private static void collectPropertyFormState(View view, String path, ArrayList<String> paths,
            ArrayList<String> types, ArrayList<String> values) {
        String type = null, value = null;
        if (view instanceof EditText) {
            type = "text";
            value = ((EditText) view).getText().toString();
        } else if (view instanceof android.widget.CheckBox) {
            type = "check";
            value = Boolean.toString(((android.widget.CheckBox) view).isChecked());
        } else if (view instanceof Spinner) {
            type = "spinner";
            value = Integer.toString(((Spinner) view).getSelectedItemPosition());
        }
        if (type != null) {
            paths.add(path);
            types.add(type);
            values.add(value);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String childPath = path.isEmpty() ? Integer.toString(i) : path + "." + i;
                collectPropertyFormState(group.getChildAt(i), childPath, paths, types, values);
            }
        }
    }

    private static void restorePropertyFormState(View root, ArrayList<String> paths,
            ArrayList<String> types, ArrayList<String> values) {
        if (paths == null || types == null || values == null
                || paths.size() != types.size() || paths.size() != values.size()) return;
        for (int i = 0; i < paths.size(); i++) {
            View view = findViewByPath(root, paths.get(i));
            String type = types.get(i), value = values.get(i);
            if ("text".equals(type) && view instanceof EditText) ((EditText) view).setText(value);
            else if ("check".equals(type) && view instanceof android.widget.CheckBox)
                ((android.widget.CheckBox) view).setChecked(Boolean.parseBoolean(value));
            else if ("spinner".equals(type) && view instanceof Spinner) {
                Spinner spinner = (Spinner) view;
                try {
                    int position = Integer.parseInt(value);
                    if (position >= 0 && position < spinner.getCount()) spinner.setSelection(position);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private static View findViewByPath(View root, String path) {
        if (root == null) return null;
        if (path == null || path.isEmpty()) return root;
        View current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof ViewGroup)) return null;
            int index;
            try { index = Integer.parseInt(part); }
            catch (NumberFormatException invalid) { return null; }
            ViewGroup group = (ViewGroup) current;
            if (index < 0 || index >= group.getChildCount()) return null;
            current = group.getChildAt(index);
        }
        return current;
    }

    private static String viewPath(View root, View target, String path) {
        if (root == target) return path;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            String childPath = path.isEmpty() ? Integer.toString(i) : path + "." + i;
            String found = viewPath(group.getChildAt(i), target, childPath);
            if (found != null) return found;
        }
        return null;
    }

    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        updateModbusTcpSlaveRuntime();
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
        if (requestCode == REQUEST_CRAFT_BLUETOOTH_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
                showCraftBluetoothPicker(pendingCraftBluetoothReturnsToDevices, pendingCraftBluetoothSlot);
            else Toast.makeText(this, "Список Bluetooth-устройств недоступен без разрешения", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_CRAFT_PUSH_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                String title = pendingCraftNotificationTitle, body = pendingCraftNotificationBody;
                int id = pendingCraftNotificationId;
                pendingCraftNotificationTitle = pendingCraftNotificationBody = null;
                if (title != null && body != null) showCraftLocalNotification(title, body, id);
            } else {
                pendingCraftNotificationTitle = pendingCraftNotificationBody = null;
                Toast.makeText(this, "Уведомление не показано без разрешения POST_NOTIFICATIONS", Toast.LENGTH_LONG).show();
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
        stopModbusTcpSlave();
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
        manualUsbScreenGeneration++;
        stopModbusTcpSlave();
        closeManualUsbConnection();
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
            player = new MediaPlayer();
            int alarmResource = craftAlarmResource(craftAlarmSoundIndex);
            if (alarmResource != 0) {
                player.setDataSource(this, Uri.parse("android.resource://" + getPackageName() + "/" + alarmResource));
            } else if (pathOrUri != null && pathOrUri.startsWith("content://")) {
                player.setDataSource(this, Uri.parse(pathOrUri));
            } else if (pathOrUri != null && !pathOrUri.trim().isEmpty() && !"Аларм".equals(pathOrUri)) {
                String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
                java.io.File file = ProjectMediaService.resolveMediaFile(getFilesDir(), json, pathOrUri);
                player.setDataSource(file.getAbsolutePath());
            } else {
                player.release();
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_ALARM, 80);
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350);
                craftAlarmTones.put(alarmKey, tone);
                return;
            }
            player.setLooping(loop);
            MediaPlayer activePlayer = player;
            player.setOnPreparedListener(prepared -> {
                if (craftAlarmPlayers.get(alarmKey) != prepared) return;
                if (!activityResumed || isFinishing() || isDestroyed()) {                    stopCraftAlarmAlert(alarmKey);
                    return;
                }
                try {
                    prepared.start();
                } catch (Exception ex) {
                    if (craftAlarmPlayers.get(alarmKey) == prepared) {
                        craftAlarmPlayers.remove(alarmKey);
                        releaseCraftAlarmPlayer(prepared);
                        android.util.Log.e("BLOODSKY", "Craft alarm start error", ex);
                        playCraftAlarmFallbackTone(alarmKey, ToneGenerator.TONE_PROP_BEEP, 250);
                    }
                }
            });
            player.setOnCompletionListener(completed -> {
                if (craftAlarmPlayers.get(alarmKey) == completed) {
                    craftAlarmPlayers.remove(alarmKey);
                    releaseCraftAlarmPlayer(completed);
                }
            });
            player.setOnErrorListener((failed, what, extra) -> {
                if (craftAlarmPlayers.get(alarmKey) == failed) {
                    craftAlarmPlayers.remove(alarmKey);
                    releaseCraftAlarmPlayer(failed);
                    android.util.Log.e("BLOODSKY", "Craft alarm error: " + what + "/" + extra);
                    playCraftAlarmFallbackTone(alarmKey, ToneGenerator.TONE_PROP_BEEP, 250);
                }
                return true;
            });
            craftAlarmPlayers.put(alarmKey, activePlayer);
            player.prepareAsync();
        } catch (Exception ex) {
            if (craftAlarmPlayers.get(alarmKey) == player) craftAlarmPlayers.remove(alarmKey);
            if (player != null) releaseCraftAlarmPlayer(player);
            android.util.Log.e("BLOODSKY", "Craft alarm error", ex);
            playCraftAlarmFallbackTone(alarmKey, ToneGenerator.TONE_PROP_BEEP, 250);
        }
    }

    private void playCraftAlarmFallbackTone(String alarmKey, int toneType, int durationMs) {
        if (!activityResumed || isFinishing() || isDestroyed()) return;
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_ALARM, 80);
            if (!tone.startTone(toneType, durationMs)) {
                tone.release();
                return;
            }
            craftAlarmTones.put(alarmKey, tone);
        } catch (Exception ignored) { }
    }

    private static void releaseCraftAlarmPlayer(MediaPlayer player) {
        try { player.stop(); } catch (Exception ignored) { }
        try { player.release(); } catch (Exception ignored) { }
    }

    private void stopCraftAlarmAlert(String alarmKey) {
        MediaPlayer player = craftAlarmPlayers.remove(alarmKey);
        if (player != null) {
            releaseCraftAlarmPlayer(player);
        }
        ToneGenerator tone = craftAlarmTones.remove(alarmKey);
        if (tone != null) {
            try { tone.stopTone(); } catch (Exception ignored) { }
            try { tone.release(); } catch (Exception ignored) { }
        }
    }

    private void stopAllCraftAlarmPlayback() {
        for (MediaPlayer player : new java.util.ArrayList<>(craftAlarmPlayers.values())) {
            releaseCraftAlarmPlayer(player);
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
        manualUsbScreenGeneration++;
        manualUsbScreenVisible = false;
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
        addLegacyMenuItem("USB Serial • Modbus RTU", this::showUsbSerialScreen, menuWidth);
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, 0, 0.11f);
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
            try { ProjectMediaService.deleteProjectMedia(getFilesDir(), selected.toString()); }
            catch (IOException cleanupFailure) {
                android.util.Log.e("BLOODSKY", "Deleted project media could not be cleaned", cleanupFailure);
                Toast.makeText(this, "Проект удалён, но его медиа не удалось очистить", Toast.LENGTH_LONG).show();
            }
            showHome();
            if (deletingActive) { widgets.clear(); screens.clear(); projectExtraFields = new JSONObject(); clipboardWidgets = null; imageCache.clear(); gifCache.clear(); }
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

    private void showUsbSerialScreen() {
        final int screenGeneration = ++manualUsbScreenGeneration;
        manualUsbScreenVisible = true;
        stopModbusPolling();
        editor = null;
        closeManualUsbConnection();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(241, 245, 249));
        root.addView(heading("BLOODSKY • USB Serial RTU"), new LinearLayout.LayoutParams(-1, dp(60)));
        root.addView(action("‹ Назад", this::showHome), new LinearLayout.LayoutParams(-1, dp(48)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(12));
        scroll.addView(form);
        TextView intro = new TextView(this);
        intro.setText("Выбери USB-адаптер и нажми «Подключить». Разрешение Android запрашивается для выбранного устройства.");
        intro.setTextColor(Color.rgb(55, 65, 81)); intro.setTextSize(14); form.addView(intro);
        Spinner deviceSpinner = new Spinner(this);
        List<UsbSerialConnector.DeviceOption> devices = new ArrayList<>();
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(deviceAdapter); form.addView(deviceSpinner);
        TextView deviceInfo = new TextView(this); deviceInfo.setTextColor(Color.rgb(75, 85, 99)); deviceInfo.setTextSize(12); form.addView(deviceInfo);
        Spinner baudSpinner = new Spinner(this);
        String[] baudChoices = {"9600", "19200", "38400", "57600", "115200"};
        baudSpinner.setAdapter(simpleSpinnerAdapter(baudChoices)); form.addView(baudSpinner);
        Spinner paritySpinner = new Spinner(this);
        String[] parityChoices = {"8N1", "8E1", "8O1", "8N2"};
        paritySpinner.setAdapter(simpleSpinnerAdapter(parityChoices)); form.addView(paritySpinner);
        EditText unitField = propertyField("1", InputType.TYPE_CLASS_NUMBER); unitField.setHint("Unit ID (0–247)"); form.addView(unitField);
        EditText addressField = propertyField("0", InputType.TYPE_CLASS_NUMBER); addressField.setHint("Начальный адрес регистра (0–65535)"); form.addView(addressField);
        EditText countField = propertyField("10", InputType.TYPE_CLASS_NUMBER); countField.setHint("Количество регистров (1–125)"); form.addView(countField);
        Spinner functionSpinner = new Spinner(this);
        functionSpinner.setAdapter(simpleSpinnerAdapter(new String[]{"Holding registers (FC03)", "Input registers (FC04)"})); form.addView(functionSpinner);
        TextView result = new TextView(this); result.setTextColor(Color.rgb(31, 41, 55)); result.setTextSize(14);
        result.setPadding(0, dp(10), 0, dp(10)); result.setText("USB-адаптеры не выбраны.");
        Runnable refreshDevices = () -> {
            try {
                if (manualUsbConnector == null) manualUsbConnector = new UsbSerialConnector(this);
                devices.clear(); devices.addAll(manualUsbConnector.listDevices()); deviceAdapter.clear();
                for (UsbSerialConnector.DeviceOption option : devices)
                    deviceAdapter.add(option.id + " • " + String.format(java.util.Locale.US, "%04X:%04X", option.vendorId, option.productId));
                deviceAdapter.notifyDataSetChanged();
                deviceInfo.setText(devices.isEmpty() ? "Поддерживаемые USB serial-адаптеры не найдены." : "Найдено адаптеров: " + devices.size());
                result.setText(devices.isEmpty() ? "Подключи USB serial-адаптер и обнови список." : "Выбери устройство, параметры линии и нажми «Подключить».");
            } catch (Exception error) { result.setText("Ошибка поиска USB-адаптеров: " + errorMessage(error)); }
        };
        form.addView(action("Обновить список USB", refreshDevices), new LinearLayout.LayoutParams(-1, dp(48)));
        form.addView(action("Подключить выбранный адаптер", () -> {
            if (devices.isEmpty() || deviceSpinner.getSelectedItemPosition() < 0) { result.setText("Сначала обнови список и выбери адаптер."); return; }
            final UsbSerialConnector.DeviceOption selected = devices.get(deviceSpinner.getSelectedItemPosition());
            final int baud = Integer.parseInt(baudChoices[baudSpinner.getSelectedItemPosition()]);
            final String framing = parityChoices[paritySpinner.getSelectedItemPosition()];
            UsbSerialSettings.Parity parity = framing.charAt(1) == 'E' ? UsbSerialSettings.Parity.EVEN : framing.charAt(1) == 'O' ? UsbSerialSettings.Parity.ODD : UsbSerialSettings.Parity.NONE;
            final int stopBits = framing.endsWith("2") ? 2 : 1;
            result.setText("Открываю " + selected.id + ". Подтверди доступ в диалоге Android, если он появится…");
            modbusExecutor.execute(() -> {
                try {
                    UsbSerialModbusConnection opened = manualUsbConnector.connect(selected.id, new UsbSerialSettings(baud, 8, stopBits, parity), 1500).get();
                    runOnUiThread(() -> {
                        if (screenGeneration != manualUsbScreenGeneration) { try { opened.close(); } catch (IOException ignored) { } return; }
                        closeManualUsbConnection(); manualUsbConnection = opened;
                        result.setText("Подключено: " + selected.id + " • " + baud + " " + framing + ". Запросы выполняются только по кнопке.");
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> { if (screenGeneration == manualUsbScreenGeneration) result.setText("Не удалось подключить USB-адаптер: " + errorMessage(error)); });
                }
            });
        }), new LinearLayout.LayoutParams(-1, dp(48)));
        form.addView(action("Отключить", () -> { closeManualUsbConnection(); result.setText("USB-адаптер отключён."); }), new LinearLayout.LayoutParams(-1, dp(48)));
        form.addView(action("Считать регистры", () -> {
            final UsbSerialModbusConnection connection = manualUsbConnection;
            if (connection == null) { result.setText("Сначала подключи USB-адаптер."); return; }
            final int unit, address, count;
            try {
                unit = Integer.parseInt(unitField.getText().toString().trim()); address = Integer.parseInt(addressField.getText().toString().trim());
                count = Integer.parseInt(countField.getText().toString().trim());
                if (unit < 0 || unit > 247 || address < 0 || address > 65535 || count < 1 || count > 125 || address + count > 65536) throw new IllegalArgumentException();
            } catch (Exception invalid) { result.setText("Проверь Unit ID, адрес и количество регистров."); return; }
            final int function = functionSpinner.getSelectedItemPosition() == 0 ? 3 : 4;
            result.setText("Читаю регистры по Modbus RTU…");
            modbusExecutor.execute(() -> {                try {
                    int[] values = connection.read(unit, function, address, count);
                    StringBuilder report = new StringBuilder("FC").append(function).append(" • адрес ").append(address).append("\n");
                    for (int i = 0; i < values.length; i++) report.append(address + i).append(": ").append(values[i]).append(i + 1 == values.length ? "" : "\n");
                    runOnUiThread(() -> result.setText(report.toString()));
                } catch (Exception error) {
                    runOnUiThread(() -> { if (manualUsbConnection == connection && !connection.isOpen()) manualUsbConnection = null; result.setText("Ошибка Modbus RTU: " + errorMessage(error)); });
                }
            });
        }), new LinearLayout.LayoutParams(-1, dp(48)));
        form.addView(result, new LinearLayout.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root); refreshDevices.run();
    }

    private ArrayAdapter<String> simpleSpinnerAdapter(String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); return adapter;
    }

    private String errorMessage(Throwable error) {
        Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
        r…163807 tokens truncated….drawText(valueText, w.x + (w.w - paint.measureText(valueText)) / 2f,
                                w.y + w.h - inset, paint);
                    }
                    paint.setStrokeWidth(dp(1));
                } else if ("Шкала / Стрелка".equals(w.type)
                        && w.extraFields.optInt("craftComponentId", -1) == 12) {
                    double val = 0.0;
                    if (liveValues.containsKey(i)) {
                        try { val = Double.parseDouble(liveValues.get(i)); } catch (Exception ignored) {}
                    }
                    drawCraftThresholdGauge(canvas, w, val, paint);
                } else if ("Шкала / Стрелка".equals(w.type)
                        && w.extraFields.optInt("craftComponentId", -1) == 24) {
                    double val = 0.0;
                    if (liveValues.containsKey(i)) {
                        try { val = Double.parseDouble(liveValues.get(i)); } catch (Exception ignored) {}
                    }
                    drawCraftGraduatedGauge(canvas, w, val, paint);
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
                    drawCraftTrendGraph(canvas, w, liveValues.get(i), paint);
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
                } else if ("Кнопка".equals(w.type) || "Константная кнопка Craft".equals(w.type)) {
                    boolean constantButton = "Константная кнопка Craft".equals(w.type);
                    String buttonText = w.text + (constantButton
                            ? "  → " + w.extraFields.optString("craftButtonConstant", "0")
                            : w.buttonState ? "  [ВКЛ]" : "  [ВЫКЛ]");
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
                } else if ("Звуковой сигнал Craft".equals(w.type)) {
                    StringBuilder activeChannels = new StringBuilder();
                    for (int channel = 0; channel < CraftSoundBank.CHANNELS; channel++) {
                        if ((w.craftSoundActiveMask & (1 << channel)) != 0) {
                            if (activeChannels.length() > 0) activeChannels.append(",");
                            activeChannels.append(channel);
                        }
                    }
                    boolean active = activeChannels.length() > 0;
                    paint.setColor(active ? Color.rgb(239, 68, 68) : Color.rgb(100, 116, 139));
                    canvas.drawCircle(w.x + dp(16), w.y + w.h / 2, dp(6), paint);
                    paint.setColor(w.textColor);
                    String soundLabel = (active ? "🔊 " : "🔇 ") + w.text
                            + (active ? " [" + activeChannels + "]" : " [0–7]");
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(),
                            Math.max(dp(8), (w.w - dp(32)) / Math.max(4f, soundLabel.length() * 0.58f))));
                    canvas.drawText(soundLabel, w.x + dp(28), w.y + w.h / 2 + dp(5), paint);
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
                } else if ("Архив Craft".equals(w.type)) {
                    paint.setColor(w.textColor);
                    paint.setTextSize(dp(13));
                    canvas.drawText("Архив: " + w.text, w.x + dp(8), w.y + dp(23), paint);
                    paint.setTextSize(dp(11));
                    canvas.drawText(liveValues.containsKey(i) ? "Каналы · " + liveValues.get(i) : "Локальная история",
                            w.x + dp(8), w.y + w.h - dp(9), paint);
                } else if ("Переход на экран".equals(w.type)) {
                    paint.setColor(w.textColor);
                    String navLabel = "🔗 " + w.text + " (Экран " + (w.targetScreenIndex + 1) + ")";
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(16)) / Math.max(4f, navLabel.length() * 0.58f))));
                    canvas.drawText(navLabel, w.x + dp(8), w.y + w.h / 2 + dp(5), paint);
                } else if ("GIF Craft".equals(w.type)) {
                    android.graphics.Movie movie = loadCachedMovie(w.extraFields.optString("craftGifPath", ""));
                    if (movie != null) {
                        int duration = Math.max(1, movie.duration());
                        movie.setTime((int) (SystemClock.uptimeMillis() % duration));
                        int save = canvas.save();
                        canvas.clipRect(rect);
                        canvas.translate(rect.left, rect.top);
                        canvas.scale(rect.width() / movie.width(), rect.height() / movie.height());
                        movie.draw(canvas, 0, 0);
                        canvas.restoreToCount(save);
                        postInvalidateDelayed(33L);
                    } else {
                        paint.setColor(Color.rgb(241, 245, 249));
                        canvas.drawRect(rect, paint);
                        paint.setColor(Color.rgb(71, 85, 105));
                        paint.setTextSize(dp(12));
                        String label = w.extraFields.optString("craftGifPath", "").isEmpty()
                                ? "Выбери GIF" : "GIF недоступен";
                        canvas.drawText(label, w.x + dp(8), w.y + w.h / 2 + dp(4), paint);
                    }
                } else if ("Многопозиционное фото".equals(w.type)) {
                    int imgIdx = 0;
                    if (liveValues.containsKey(i)) {
                        try {
                            int registerValue = (int) Math.round(Double.parseDouble(liveValues.get(i)));
                            JSONObject craftOther = w.extraFields.optJSONObject("craftOther");
                            int typeCommand = craftOther == null ? 0 : craftOther.optInt("typeCommand", 0);
                            imgIdx = CraftMultiStateImage.selectIndex(typeCommand, registerValue);
                        } catch (Exception ignored) { imgIdx = -1; }
                    }
                    if (imgIdx >= 0) {
                        String imgPath = w.imagePathStates[imgIdx];
                        Bitmap bitmap = loadCachedBitmap(imgPath);
                        RectF imageBounds = new RectF(rect.left + 10, rect.top + 10,
                                rect.right - 10, rect.bottom - 10);
                        if (bitmap != null) {
                            canvas.drawBitmap(bitmap, null, imageBounds, paint);
                        } else if (CraftMultiStateImage.shouldShowNoPicture(imgIdx, false)
                                && imageBounds.width() > 0 && imageBounds.height() > 0) {
                            Bitmap noPicture = originalBitmap(R.drawable.kc_no_picture);
                            if (noPicture != null) canvas.drawBitmap(noPicture, null, imageBounds, paint);
                        }
                    }
                } else if ("Распознавание речи Craft".equals(w.type)) {
                    paint.setColor(Color.rgb(51, 65, 85));
                    canvas.drawRoundRect(new RectF(w.x + dp(6), w.y + dp(6),
                            w.x + w.w - dp(6), w.y + w.h - dp(6)), dp(4), dp(4), paint);
                    paint.setColor(Color.rgb(203, 213, 225));
                    paint.setTextSize(dp(11));
                    canvas.drawText(w.text, w.x + dp(12), w.y + dp(19), paint);
                    paint.setColor(Color.rgb(248, 113, 113));
                    paint.setTextSize(dp(10));
                    canvas.drawText("Распознавание речи не подключено",
                            w.x + dp(12), w.y + w.h / 2 + dp(4), paint);
                    paint.setColor(Color.rgb(148, 163, 184));
                    paint.setTextSize(dp(9));
                    canvas.drawText("16 исходных фраз сохранены",
                            w.x + dp(12), w.y + w.h - dp(10), paint);
                } else if ("Видео RTSP Craft".equals(w.type)) {
                    paint.setColor(Color.rgb(30, 41, 59));
                    canvas.drawRoundRect(new RectF(w.x + dp(6), w.y + dp(6),
                            w.x + w.w - dp(6), w.y + w.h - dp(6)), dp(4), dp(4), paint);
                    paint.setColor(Color.rgb(203, 213, 225));
                    paint.setTextSize(dp(11));
                    canvas.drawText(w.text, w.x + dp(12), w.y + dp(19), paint);
                    paint.setColor(Color.rgb(248, 113, 113));
                    paint.setTextSize(dp(10));
                    canvas.drawText("RTSP • воспроизведение не подключено",
                            w.x + dp(12), w.y + w.h / 2 + dp(4), paint);
                    paint.setColor(Color.rgb(148, 163, 184));
                    paint.setTextSize(dp(9));
                    canvas.drawText("Источник сохранён; поток выключен",
                            w.x + dp(12), w.y + w.h - dp(10), paint);
                } else if ("WebView Craft".equals(w.type)) {
                    paint.setColor(Color.rgb(30, 41, 59));
                    canvas.drawRoundRect(new RectF(w.x + dp(6), w.y + dp(6),
                            w.x + w.w - dp(6), w.y + w.h - dp(6)), dp(4), dp(4), paint);
                    paint.setColor(Color.rgb(203, 213, 225));
                    paint.setTextSize(dp(11));
                    canvas.drawText(w.text, w.x + dp(12), w.y + dp(19), paint);
                    paint.setColor(Color.rgb(248, 113, 113));
                    paint.setTextSize(dp(10));
                    canvas.drawText("WebView выключен", w.x + dp(12), w.y + w.h / 2 + dp(4), paint);
                    paint.setColor(Color.rgb(148, 163, 184));
                    paint.setTextSize(dp(9));
                    canvas.drawText("16 исходных ссылок сохранены", w.x + dp(12), w.y + w.h - dp(10), paint);
                } else if ("Email Craft".equals(w.type)) {
                    paint.setColor(Color.rgb(30, 41, 59));
                    canvas.drawRoundRect(new RectF(w.x + dp(6), w.y + dp(6),
                            w.x + w.w - dp(6), w.y + w.h - dp(6)), dp(4), dp(4), paint);
                    paint.setColor(Color.rgb(203, 213, 225));
                    paint.setTextSize(dp(11));
                    canvas.drawText(w.text, w.x + dp(12), w.y + dp(19), paint);
                    paint.setColor(Color.rgb(248, 113, 113));
                    paint.setTextSize(dp(10));
                    canvas.drawText("Отправка email не подключена",
                            w.x + dp(12), w.y + w.h / 2 + dp(4), paint);
                    paint.setColor(Color.rgb(148, 163, 184));
                    paint.setTextSize(dp(9));
                    canvas.drawText("Учётные данные защищены", w.x + dp(12), w.y + w.h - dp(10), paint);
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
                if ("FBD CRAFT BLINK".equals(widget.type)
                        && FbdCraftNativeTimer.needsRedraw(FbdCraftNativeTimer.BLINK,
                        widget.craftBlinkState, System.currentTimeMillis(), widget.timerPresetMs)) {
                    postInvalidateDelayed(100L);
                    break;
                }
                if ("FBD CRAFT TIMER".equals(widget.type)) {
                    String mode = widget.extraFields.optString("craftTimerMode", "TON");
                    int modeCode = "TOF".equals(mode) ? FbdCraftNativeTimer.TOF
                            : "TP".equals(mode) ? FbdCraftNativeTimer.TP
                            : "BLINK".equals(mode) ? FbdCraftNativeTimer.BLINK : FbdCraftNativeTimer.TON;
                    if (FbdCraftNativeTimer.needsRedraw(modeCode, widget.craftTimerState,
                            System.currentTimeMillis(), widget.craftTimerRuntimePresetMs)) {
                        postInvalidateDelayed(100L);
                        break;
                    }
                }
            }
            if (marqueeActive && dragging) {
                float left = Math.min(downX, marqueeX), top = Math.min(downY, marqueeY);
                float right = Math.max(downX, marqueeX), bottom = Math.max(downY, marqueeY);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(42, 21, 101, 192));
                canvas.drawRect(left, top, right, bottom, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.5f));
                paint.setColor(Color.rgb(21, 101, 192));
                canvas.drawRect(left, top, right, bottom, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (fbdWireMode) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(109, 40, 217));
                paint.setTextSize(dp(12));
                canvas.drawText(wireSource < 0 ? "РЕЖИМ СВЯЗИ: выбери источник" : "РЕЖИМ СВЯЗИ: выбери цель", dp(10), dp(18), paint);
            }
        }

        private final OperatorGesture operatorGesture = new OperatorGesture();
        private Widget pressedCraftButton;
        private BindingRequest pressedCraftButtonBinding;
        private int pressedCraftButtonIndex = -1;
        private int pressedCraftButtonBit;
        private int operatorMomentaryCandidate = -1;

        private boolean isCraftMomentaryButton(Widget widget) {
            return widget != null && "Кнопка".equals(widget.type)
                    && widget.extraFields.has("craftButtonMode");
        }

        private void beginCraftBitButton(Widget widget, int index) {
            operatorMomentaryCandidate = index;
            if (!widget.extraFields.optBoolean("craftButtonBitEnabled", false)) {
                Toast.makeText(MainActivity.this, "Настрой TCP holding-регистр FC03/FC22 и включи битовую команду в свойствах", Toast.LENGTH_LONG).show();
                return;
            }
            BindingRequest binding = parseBinding(-1, widget.connectionInfo);
            if (binding == null || binding.functionCode != 3) {
                Toast.makeText(MainActivity.this, "Проверь TCP holding-регистр FC03/FC22 в свойствах кнопки", Toast.LENGTH_LONG).show();
                return;
            }
            try { pressedCraftButtonBit = CraftMomentaryButton.bit(widget.extraFields); }
            catch (IllegalArgumentException invalid) {
                Toast.makeText(MainActivity.this, invalid.getMessage(), Toast.LENGTH_LONG).show(); return;
            }
            pressedCraftButton = widget;
            pressedCraftButtonBinding = binding;
            pressedCraftButtonIndex = index;
            widget.buttonState = true;
            invalidate();
            queueCraftBitButtonWrite(widget, binding, index, pressedCraftButtonBit, true);
        }

        private void endCraftBitButton() {
            Widget widget = pressedCraftButton;
            BindingRequest binding = pressedCraftButtonBinding;
            int index = pressedCraftButtonIndex;
            int bit = pressedCraftButtonBit;
            pressedCraftButton = null;
            pressedCraftButtonBinding = null;
            pressedCraftButtonIndex = -1;
            if (widget == null || binding == null) return;
            widget.buttonState = false;
            invalidate();
            queueCraftBitButtonWrite(widget, binding, index, bit, false);
        }

        private void activateCraftSwitch(Widget widget, int index) {
            boolean previous = widget.buttonState;
            boolean next = !previous;
            widget.buttonState = next;
            persistProject();
            invalidate();
            if (!widget.extraFields.optBoolean("craftSwitchBitEnabled", false)) {
                Toast.makeText(MainActivity.this, "Переключатель: " + (next ? "ВКЛ" : "ВЫКЛ"), Toast.LENGTH_SHORT).show();
                return;
            }
            BindingRequest binding = parseBinding(-1, widget.connectionInfo);
            if (binding == null || binding.functionCode != 3) {
                widget.buttonState = previous;
                persistProject();
                invalidate();
                Toast.makeText(MainActivity.this, "Для записи бита нужен TCP holding-регистр FC03/FC22", Toast.LENGTH_LONG).show();
                return;
            }
            final int bit = widget.extraFields.optInt("craftSwitchBit", -1);
            final int[] masks;
            try { masks = OriginalCraftSwitch.writeMasks(bit, next); }
            catch (IllegalArgumentException invalid) {
                widget.buttonState = previous;
                persistProject();
                invalidate();
                Toast.makeText(MainActivity.this, invalid.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            final String requestedState = next ? "ВКЛ" : "ВЫКЛ";
            final int requestSequence = widget.extraFields.optInt("craftSwitchWriteSequence", 0) + 1;
            try { widget.extraFields.put("craftSwitchWriteSequence", requestSequence); } catch (Exception ignored) { }
            persistProject();
            modbusExecutor.execute(() -> {
                Exception failure = null;
                try {
                    if (!activityResumed || !operatorMode || !widgets.contains(widget))
                        throw new IOException("Рабочий режим остановлен");
                    new ModbusTcpClient(binding.host, binding.port).writeMask(binding.unit, binding.address, masks[0], masks[1]);
                } catch (Exception error) { failure = error; }
                final Exception result = failure;
                if (result != null) runOnUiThread(() -> {
                    if (!widgets.contains(widget) || widgets.indexOf(widget) != index
                            || widget.extraFields.optInt("craftSwitchWriteSequence", 0) != requestSequence
                            || widget.buttonState != next) return;
                    widget.buttonState = previous;
                    persistProject();
                    if (editor != null) editor.invalidate();
                    Toast.makeText(MainActivity.this, "Ошибка записи бита переключателя: " + describeModbusError(result), Toast.LENGTH_LONG).show();
                });
            });
            Toast.makeText(MainActivity.this, "Переключатель: " + requestedState, Toast.LENGTH_SHORT).show();
        }

        private void queueCraftBitButtonWrite(Widget widget, BindingRequest binding, int index, int bit, boolean pressed) {
            final int[] masks = CraftMomentaryButton.masks(bit, pressed);
            modbusExecutor.execute(() -> {
                Exception failure = null;
                try {
                    if (pressed && (!operatorMode || !activityResumed || !widgets.contains(widget)))
                        throw new IOException("Рабочий режим остановлен");
                    new ModbusTcpClient(binding.host, binding.port).writeMask(binding.unit, binding.address, masks[0], masks[1]);
                } catch (Exception error) { failure = error; }
                final Exception result = failure;
                if (result != null) runOnUiThread(() -> {
                    if (!activityResumed) return;
                    Toast.makeText(MainActivity.this, (pressed ? "Не удалось установить бит: " : "Не удалось сбросить бит: ")
                            + describeModbusError(result), Toast.LENGTH_LONG).show();
                });
            });
        }

        private int hitRuntimeWidget(float x, float y) {
            for (int i = widgets.size() - 1; i >= 0; i--) {
                Widget w = widgets.get(i);
                if (w.visible && !isFbd(w)
                        && !("Список Craft".equals(w.type)
                        && !w.extraFields.optBoolean("craftListVisibleInWork", true))
                        && !(("Кнопка".equals(w.type) || "Константная кнопка Craft".equals(w.type)
                        || "Переход на экран".equals(w.type))
                        && !w.extraFields.optBoolean("craftButtonVisibleInWork", true))
                        && !(OriginalCraftSwitch.supports(w.type)
                        && !w.extraFields.optBoolean("craftSwitchVisibleInWork", OriginalCraftSwitch.visibleInWork(w.extraFields)))
                        && x >= w.x && x <= w.x + w.w && y >= w.y && y <= w.y + w.h) return i;
            }
            return -1;
        }

        private boolean onOperatorTouch(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                int hit = hitRuntimeWidget(x, y);
                operatorMomentaryCandidate = -1;
                operatorGesture.begin(hit, x, y, SystemClock.elapsedRealtime());
                if (hit >= 0 && isCraftMomentaryButton(widgets.get(hit))
                        && widgets.get(hit).extraFields.optInt("craftButtonMode", 0) == 0)
                    beginCraftBitButton(widgets.get(hit), hit);
            } else if (action == MotionEvent.ACTION_MOVE) operatorGesture.move(x, y, dp(5));
            else if (action == MotionEvent.ACTION_UP) {
                int candidate = operatorMomentaryCandidate;
                int target = operatorGesture.end(hitRuntimeWidget(x, y), x, y, SystemClock.elapsedRealtime(), dp(5));
                if (candidate >= 0) endCraftBitButton();
                operatorMomentaryCandidate = -1;
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
                        invalidate();                    })
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
            boolean craftSystemBinding = CraftSystemNumericWidgetBinding.isGlobalSystemBinding(widget.extraFields);
            if (craftSystemBinding && !CraftSystemNumericWidgetBinding.canWrite(widget.extraFields)) {
                Toast.makeText(MainActivity.this,
                        "Для системной связи Craft ввод доступен в пользовательские регистры 10–1999 (Float32 — до 1998)",
                        Toast.LENGTH_LONG).show(); return;
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
            if (craftSystemBinding) {
                String systemValue = CraftSystemNumericWidgetBinding.readState(widget.extraFields, systemRegisterBank);
                if (systemValue != null) initial = systemValue;
            }
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
                    if (craftSystemBinding) {
                        if (!operatorMode || !activityResumed || !widgets.contains(widget)
                                || !CraftSystemNumericWidgetBinding.writeValue(widget.extraFields, next, systemRegisterBank)) {
                            Toast.makeText(MainActivity.this, "Не удалось записать системный регистр", Toast.LENGTH_LONG).show(); return;
                        }
                    }
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
            } else if (OriginalCraftSwitch.supports(picked.type)) {
                activateCraftSwitch(picked, index);
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

