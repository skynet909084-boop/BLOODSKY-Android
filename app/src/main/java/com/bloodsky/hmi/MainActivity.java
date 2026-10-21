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
    private int pendingCraftBluetoothSlot = -1;
    private boolean pendingCraftBluetoothReturnsToDevices;
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
        manualUsbScreenGeneration++;
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
            if (deletingActive) { widgets.clear(); screens.clear(); projectExtraFields = new JSONObject(); clipboardWidget = null; imageCache.clear(); gifCache.clear(); }
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
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private void closeManualUsbConnection() {
        UsbSerialModbusConnection connection = manualUsbConnection; manualUsbConnection = null;
        if (connection != null) try { connection.close(); } catch (IOException error) { android.util.Log.w("BLOODSKY", "USB serial close failed", error); }
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
            if (unit < 1 || unit > 247) throw new IllegalArgumentException("Unit ID must be 1..247 for a request/response RTU exchange");
            if (functionCode < 1 || functionCode > 4) throw new IllegalArgumentException("RTU stream helper currently supports read functions FC01..FC04 only");
            int maxCount = functionCode <= 2 ? 2000 : 125;
            if (count < 1 || count > maxCount) throw new IllegalArgumentException("Invalid Modbus RTU read quantity");
            if (startAddress < 0 || startAddress > 65535 || startAddress + count > 65536) {
                throw new IllegalArgumentException("Modbus RTU address range must fit 0..65535");
            }
            byte[] frame = buildModbusRtuFrame(unit, functionCode, startAddress, count);
            out.write(frame);
            out.flush();

            byte[] header = new byte[3];
            readFully(header, 0, 3);

            int respUnit = header[0] & 0xFF;
            int respFunc = header[1] & 0xFF;
            if (respUnit != unit) {
                throw new IOException("Unexpected unit ID in Modbus RTU response: " + respUnit);
            }

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
            int expectedByteCount = functionCode <= 2 ? (count + 7) / 8 : count * 2;
            if (byteCount != expectedByteCount) {
                throw new IOException("Invalid byte count in Modbus RTU response: expected "
                        + expectedByteCount + ", got " + byteCount);
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
        return parseBinding(widgetIndex, binding, 1);
    }

    private void startFbdSensors() {
        if (!activityResumed || !operatorMode || editor == null) return;
        boolean neededLight = false, neededOrientation = false, neededSpeed = false;
        for (Widget widget : widgets) {
            neededLight |= "FBD CRAFT SENSOR LIGHT".equals(widget.type);
            neededOrientation |= "FBD CRAFT SENSOR ORIENTATION".equals(widget.type);
            neededSpeed |= "FBD CRAFT SENSOR SPEED".equals(widget.type);
        }
        if (!fbdSensorsRegistered && (neededLight || neededOrientation)) {
            if (fbdSensorManager == null)
                fbdSensorManager = (android.hardware.SensorManager)getSystemService(SENSOR_SERVICE);
            boolean registered = false;
            if (fbdSensorManager != null && neededLight) {
                android.hardware.Sensor sensor = fbdSensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT);
                if (sensor != null) registered |= fbdSensorManager.registerListener(fbdSensorListener, sensor,
                        android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (fbdSensorManager != null && neededOrientation) {
                android.hardware.Sensor sensor = fbdSensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ORIENTATION);
                if (sensor != null) registered |= fbdSensorManager.registerListener(fbdSensorListener, sensor,
                        android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
            }
            fbdSensorsRegistered = registered;
        }
        if (neededSpeed && !fbdLocationRegistered) {
            if (android.os.Build.VERSION.SDK_INT >= 23
                    && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (!fbdLocationPermissionRequested) {
                    fbdLocationPermissionRequested = true;
                    requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FBD_SPEED_LOCATION);
                }
                return;
            }
            if (fbdLocationManager == null)
                fbdLocationManager = (android.location.LocationManager)getSystemService(LOCATION_SERVICE);
            try {
                if (fbdLocationManager != null && fbdLocationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                    fbdLocationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER,
                            1000L, 0f, fbdLocationListener, Looper.getMainLooper());
                    fbdLocationRegistered = true;
                }
            } catch (SecurityException | IllegalArgumentException ignored) {}
        }
    }

    private void stopFbdSensors() {
        if (fbdSensorManager != null && fbdSensorsRegistered) fbdSensorManager.unregisterListener(fbdSensorListener);
        fbdSensorsRegistered = false;
        if (fbdLocationManager != null && fbdLocationRegistered) {
            try { fbdLocationManager.removeUpdates(fbdLocationListener); } catch (SecurityException ignored) {}
        }
        fbdLocationRegistered = false;
        for (Widget widget : widgets) if (isCraftFbdSensorSource(widget.type)) widget.craftSensorValid = false;
    }

    private void stopCraftMqtt() {
        FbdCraftMqttRuntime active = craftMqttRuntime;
        craftMqttRuntime = null;
        if (active != null) active.close();
        for (Widget widget : widgets) if (isCraftMqttSubscriber(widget.type)) widget.craftMqttValue = null;
    }

    private void startCraftMqtt() {
        stopCraftMqtt();
        if (!activityResumed || !operatorMode || editor == null) return;
        FbdCraftMqttRuntime runtime = new FbdCraftMqttRuntime();
        craftMqttRuntime = runtime;
        final EditorCanvas sessionEditor = editor;
        for (int server = 1; server <= 2; server++) {
            JSONObject saved;
            try { saved = new JSONObject(getSharedPreferences("bloodsky_mqtt", MODE_PRIVATE)
                    .getString("server" + server, "{}")); }
            catch (Exception invalid) { saved = new JSONObject(); }
            FbdCraftMqttRuntime.Broker broker = new FbdCraftMqttRuntime.Broker(
                    saved.optString("uri", ""), saved.optString("username", ""), saved.optString("password", ""));
            Map<String, FbdCraftMqttRuntime.MessageListener> subscriptions = new LinkedHashMap<>();
            boolean used = false;
            for (int i = 0; i < widgets.size(); i++) {
                Widget target = widgets.get(i);
                if (isCraftMqttSubscriber(target.type) && mqttServer(target) == server) {
                    used = true;
                    String topic = target.extraFields.optString("craftMqttTopic", "").trim();
                    int type = mqttType(target);
                    final int targetIndex = i;
                    if (FbdCraftMqttRuntime.validTopicFilter(topic)) {
                        FbdCraftMqttRuntime.MessageListener nextListener = (receivedTopic, payload) -> {
                        Double value = FbdCraftMqttRuntime.parsePayload(payload, type);
                        runOnUiThread(() -> {
                            if (craftMqttRuntime != runtime || !activityResumed || !operatorMode
                                    || editor != sessionEditor || targetIndex >= widgets.size()
                                    || widgets.get(targetIndex) != target) return;
                            target.craftMqttValue = value;
                            liveValues.put(targetIndex, value == null ? "ERR: MQTT payload" : Double.toString(value));
                            if (editor != null) editor.invalidate();
                        });
                        };
                        FbdCraftMqttRuntime.MessageListener previous = subscriptions.get(topic);
                        if (previous == null) subscriptions.put(topic, nextListener);
                        else subscriptions.put(topic, (receivedTopic, payload) -> {
                            previous.onMessage(receivedTopic, payload);
                            nextListener.onMessage(receivedTopic, payload);
                        });
                    }
                } else if (isCraftMqttPublisher(target.type) && mqttServer(target) == server) used = true;
            }
            if (used && broker.isConfigured()) runtime.connect(server, broker, subscriptions);
        }
    }

    private BindingRequest parseBinding(int widgetIndex, String binding, int quantity) {
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
            if (quantity < 1 || quantity > 8 || address + quantity > 65536) return null;
            return new BindingRequest(widgetIndex, uri.getHost(), uri.getPort(), unit, address, functionCode, quantity);
        } catch (Exception ignored) {
            return null;
        }
    }

    private BindingRequest parseProjectBinding(int widgetIndex, String binding, int quantity) {
        UsbSerialBinding usb = UsbSerialBinding.parse(binding, quantity);
        if (usb != null)
            return new BindingRequest(widgetIndex, "", 0, usb.unit, usb.address,
                    usb.functionCode, usb.quantity, true);
        return parseBinding(widgetIndex, binding, quantity);
    }

    private int[] readBoundRegisters(BindingRequest request) throws IOException {
        if (!request.usb)
            return new ModbusTcpClient(request.host, request.port).read(
                    request.unit, request.address, request.quantity, request.functionCode);
        UsbSerialModbusConnection connection = manualUsbConnection;
        if (connection == null || !connection.isOpen()) throw new IOException("USB RTU не подключён");
        return connection.read(request.unit, request.functionCode, request.address, request.quantity);
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
        craftGatewayStates.clear();
        craftBitGatewayStates.clear();
        craftArchiveTriggerHigh.clear();
        craftArchiveTriggerEdges.clear();
        mainHandler.removeCallbacks(modbusPollRunnable);
    }

    private int archiveReadQuantity(Widget widget) {
        return CraftArchiveChannels.readQuantity(widget.extraFields.optInt("craftArchiveChannelCount", 1),
                widget.extraFields.optString("craftArchiveRecordType", "INT"));
    }

    private int trendReadQuantity(Widget widget) {
        return CraftTrendGraphCodec.readQuantity(widget.extraFields.optInt("craftTrendType", 1),
                widget.extraFields.optInt("craftTrendChannelCount", 1));
    }

    private String formatPolledValue(Widget widget, int[] words) {
        if ("График / Тренд".equals(widget.type)
                && widget.extraFields.optInt("craftComponentId", -1) == 14) {
            double[] channels = CraftTrendGraphCodec.decodeChannels(words,
                    widget.extraFields.optInt("craftTrendType", 1),
                    widget.extraFields.optInt("craftTrendChannelCount", 1));
            StringBuilder encoded = new StringBuilder();
            for (int i = 0; i < channels.length; i++) {
                if (i > 0) encoded.append('|');
                encoded.append(Double.toString(channels[i]));
            }
            return encoded.toString();
        }
        if ("Архив Craft".equals(widget.type)) return CraftArchiveChannels.decode(words,
                widget.extraFields.optInt("craftArchiveChannelCount", 1),
                widget.extraFields.optString("craftArchiveRecordType", "INT"));
        if (OriginalCraftNumericIndicator.TYPE.equals(widget.type)) {
            int sourceType = Math.max(0, Math.min(3, widget.extraFields.optInt("craftNumericIndicatorType", 0)));
            return OriginalCraftNumericIndicator.format(OriginalCraftNumericIndicator.decode(words, sourceType),
                    widget.extraFields.optInt("craftNumericIndicatorDigits", 5),
                    widget.extraFields.optInt("craftNumericIndicatorDecimals", 0),
                    sourceType == OriginalCraftNumericIndicator.FLOAT32);
        }
        return words.length > 1 ? words[0] + "," + words[1] : Integer.toString(words[0]);
    }

    private void pollCraftOdTempSources() {
        if (!activityResumed || !operatorMode || editor == null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        final EditorCanvas sessionEditor = editor;
        for (int i = 0; i < widgets.size(); i++) {
            Widget widget = widgets.get(i);
            if (!isCraftOdTempSource(widget.type) || widget.craftOdTempPending
                    || now < widget.craftOdTempNextPollAt) continue;
            String host = widget.extraFields.optString("ip", "10.10.0.1").trim();
            int sensorType = widget.extraFields.optInt("craftOdTempType", widget.extraFields.optInt("typeSensor", 1));
            int responseType = widget.extraFields.optInt("craftOdTempResponse", widget.extraFields.optInt("typeResponse", 1));
            if (!isAllowedOdTempHost(host) || (sensorType != 1 && sensorType != 2)
                    || responseType < 1 || responseType > 2) {
                widget.craftSensorValid = false; widget.craftOdTempNextPollAt = now + 5000L;
                liveValues.put(i, "CFG"); continue;
            }
            widget.craftOdTempPending = true; widget.craftOdTempNextPollAt = now + 5000L;
            final Widget target = widget; final int index = i;
            final int requestedType = sensorType, requestedResponse = responseType;
            try {
                craftSensorExecutor.execute(() -> {
                    Double reading = null; String error = null;
                    try { reading = readCraftOdTemp(host, requestedType, requestedResponse); }
                    catch (Exception failure) { error = "ERR: " + describeModbusError(failure); }
                    final Double result = reading; final String failureText = error;
                    runOnUiThread(() -> {
                        target.craftOdTempPending = false;
                        if (index >= widgets.size() || widgets.get(index) != target || !operatorMode
                                || !activityResumed || editor != sessionEditor) return;
                        target.craftOdTempNextPollAt = android.os.SystemClock.elapsedRealtime() + 5000L;
                        target.craftSensorValid = result != null && Double.isFinite(result);
                        if (target.craftSensorValid) {
                            target.craftSensorValue = result;
                            liveValues.put(index, Double.toString(result));
                        } else liveValues.put(index, failureText == null ? "ERR: ответ датчика" : failureText);
                        if (editor != null) editor.invalidate();
                    });
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) { widget.craftOdTempPending = false; }
        }
    }

    private static double readCraftOdTemp(String host, int sensorType, int responseType) throws Exception {
        if (!isAllowedOdTempHost(host)) throw new IOException("Недопустимый адрес ODTEMP");
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection)new java.net.URL("http://" + host + "/state.json").openConnection();
            connection.setRequestMethod("GET"); connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            if (connection.getResponseCode() != 200) throw new IOException("ODTEMP HTTP " + connection.getResponseCode());
            java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[2048]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (body.size() + count > 65536) throw new IOException("Ответ ODTEMP слишком большой");
                    body.write(buffer, 0, count);
                }
            }
            JSONObject json = new JSONObject(new String(body.toByteArray(), StandardCharsets.UTF_8));
            Object raw;
            if (sensorType == 1) raw = json.opt("value");
            else {
                JSONObject values = json.optJSONObject("value");
                raw = values == null ? null : values.opt(responseType == 1 ? "t" : "h");
            }
            if (raw == null) throw new IOException("В ответе ODTEMP нет значения");
            double value = raw instanceof Number ? ((Number)raw).doubleValue() : Double.parseDouble(String.valueOf(raw));
            if (!Double.isFinite(value)) throw new IOException("ODTEMP вернуло неконечное значение");
            return value;
        } finally { if (connection != null) connection.disconnect(); }
    }
    private void pollBoundWidgets() {
        final long sessionToken = pollSession.token();
        final EditorCanvas sessionEditor = editor;
        if (!pollSession.accepts(sessionToken) || sessionEditor == null) return;
        final List<Widget> snapshotWidgets = new ArrayList<>(widgets);
        final List<String> snapshotBindings = new ArrayList<>();
        final List<JSONObject> snapshotExtras = new ArrayList<>();
        for (Widget widget : snapshotWidgets) {
            snapshotBindings.add(widget.connectionInfo);
            try { snapshotExtras.add(new JSONObject(widget.extraFields.toString())); }
            catch (Exception ignored) { snapshotExtras.add(new JSONObject()); }
        }
        final Map<String, BindingRequest> groupedRequests = new LinkedHashMap<>();
        final List<Integer> gatewayIndices = new ArrayList<>();
        final List<Integer> bitGatewayIndices = new ArrayList<>();
        final List<Integer> archiveTriggerIndices = new ArrayList<>();
        final Map<Integer, String> craftSystemTextUpdates = new HashMap<>();
        final Map<Integer, String> craftSystemNumericUpdates = new HashMap<>();
        for (int i = 0; i < snapshotWidgets.size(); i++) {
            Widget widget = snapshotWidgets.get(i);
            if ("Шлюз битовый".equals(widget.type)) {
                if (snapshotExtras.get(i).optBoolean("craftBitGatewayEnabled", false)) bitGatewayIndices.add(i);
                continue;
            }
            if ("Шлюз Craft".equals(widget.type)) {
                if (snapshotExtras.get(i).optBoolean("craftGatewayEnabled", false)) gatewayIndices.add(i);
                continue;
            }
            if ("Архив Craft".equals(widget.type)
                    && !snapshotExtras.get(i).optString("craftArchiveTriggerBinding", "").trim().isEmpty())
                archiveTriggerIndices.add(i);
            if (isCraftRegisterSink(widget.type)) continue;
            String systemTextState = CraftSystemWidgetBinding.readDynamicTextState(
                    snapshotExtras.get(i), systemRegisterBank);
            if (systemTextState != null) {
                craftSystemTextUpdates.put(i, systemTextState);
                continue;
            }
            String systemNumericState = CraftSystemNumericWidgetBinding.readState(
                    snapshotExtras.get(i), systemRegisterBank);
            if (systemNumericState != null) {
                craftSystemNumericUpdates.put(i, systemNumericState);
                continue;
            }
            boolean craftSource = isCraftRegisterSource(widget.type);
            if (craftSource && widget.extraFields.optBoolean("craftVarConstantMode", false)) continue;
            int quantity = craftSource && isCraftRegisterFloat(widget.type) ? 2 : 1;
            if ("График / Тренд".equals(widget.type)
                    && widget.extraFields.optInt("craftComponentId", -1) == 14)
                quantity = trendReadQuantity(widget);
            if (OriginalCraftNumericIndicator.TYPE.equals(widget.type))
                quantity = OriginalCraftNumericIndicator.readQuantity(widget.extraFields.optInt("craftNumericIndicatorType", 0));
            if ("Архив Craft".equals(widget.type)) quantity = archiveReadQuantity(widget);
            boolean usbProjectBindingAllowed = craftSource || "FBD Вход".equals(widget.type);
            BindingRequest request = usbProjectBindingAllowed
                    ? parseProjectBinding(i, widget.connectionInfo, quantity)
                    : parseBinding(i, widget.connectionInfo, quantity);
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
            boolean usbProjectBindingAllowed = isCraftRegisterSource(snapshotWidgets.get(i).type)
                    || "FBD Вход".equals(snapshotWidgets.get(i).type);
            int numericIndicatorQuantity = OriginalCraftNumericIndicator.TYPE.equals(snapshotWidgets.get(i).type)
                    ? OriginalCraftNumericIndicator.readQuantity(snapshotWidgets.get(i).extraFields
                    .optInt("craftNumericIndicatorType", 0)) : 1;
            if ("График / Тренд".equals(snapshotWidgets.get(i).type)
                    && snapshotWidgets.get(i).extraFields.optInt("craftComponentId", -1) == 14)
                numericIndicatorQuantity = trendReadQuantity(snapshotWidgets.get(i));
            if (binding != null && binding.startsWith("tcp://")
                    && parseBinding(i, binding, numericIndicatorQuantity) == null)
                invalidBindings.put(i, "CFG");
            else if (usbProjectBindingAllowed && binding != null && binding.startsWith("usb://")
                    && parseProjectBinding(i, binding,
                    "График / Тренд".equals(snapshotWidgets.get(i).type)
                            && snapshotWidgets.get(i).extraFields.optInt("craftComponentId", -1) == 14
                            ? trendReadQuantity(snapshotWidgets.get(i))
                            : isCraftRegisterFloat(snapshotWidgets.get(i).type) ? 2 : 1) == null) invalidBindings.put(i, "CFG");
        }
        if (requests.isEmpty() && invalidBindings.isEmpty() && craftSystemTextUpdates.isEmpty()
                && craftSystemNumericUpdates.isEmpty()
                && gatewayIndices.isEmpty() && bitGatewayIndices.isEmpty()) return;
        modbusPollInFlight = true;
        try {
            modbusExecutor.execute(() -> {
            try {
                final Map<Integer, String> updates = new HashMap<>(invalidBindings);
                updates.putAll(craftSystemTextUpdates);
                updates.putAll(craftSystemNumericUpdates);
                for (BindingRequest request : requests) {
                    if (!pollSession.accepts(sessionToken)) return;
                    String value;
                    try {
                        int[] words = readBoundRegisters(request);
                        StringBuilder rawWords = new StringBuilder();
                        for (int i = 0; i < words.length; i++) {
                            if (i > 0) rawWords.append(',');
                            rawWords.append(words[i]);
                        }
                        value = rawWords.toString();
                    } catch (Exception e) {
                        value = "ERR: " + describeModbusError(e);
                    }
                    for (int widgetIndex : request.widgetIndices) {
                        if (value.startsWith("ERR:")) updates.put(widgetIndex, value);
                        else {
                            String[] fields = value.split(",");
                            int[] words = new int[fields.length];
                            for (int i = 0; i < fields.length; i++) words[i] = Integer.parseInt(fields[i]);
                            updates.put(widgetIndex, formatPolledValue(snapshotWidgets.get(widgetIndex), words));
                        }
                    }
                }
                for (int widgetIndex : gatewayIndices) {
                    if (!pollSession.accepts(sessionToken)) return;
                    Widget gatewayWidget = snapshotWidgets.get(widgetIndex);
                    try {
                        CraftRegisterGateway.Config config = CraftRegisterGateway.from(snapshotExtras.get(widgetIndex));
                        if (config == null) {
                            craftGatewayStates.remove(gatewayWidget);
                            continue;
                        }
                        CraftRegisterGateway.State state = craftGatewayStates.get(gatewayWidget);
                        if (state == null) {
                            CraftRegisterGateway.State created = new CraftRegisterGateway.State();
                            CraftRegisterGateway.State existing = craftGatewayStates.putIfAbsent(gatewayWidget, created);
                            state = existing == null ? created : existing;
                        }
                        CraftRegisterGateway.Outcome outcome = CraftRegisterGateway.transfer(config, state,
                                new CraftRegisterGateway.Transport() {
                                    @Override public int[] read(CraftRegisterGateway.Binding binding, int quantity) throws IOException {
                                        if (!pollSession.accepts(sessionToken)) throw new IOException("Сеанс проекта завершён");
                                        if (binding.systemRegister)
                                            return CraftRegisterGateway.readSystemSource(binding, quantity, systemRegisterBank);
                                        return new ModbusTcpClient(binding.host, binding.port).read(
                                                binding.unit, binding.address, quantity, binding.readFunction);
                                    }
                                    @Override public void write(CraftRegisterGateway.Binding binding, int[] values) throws IOException {
                                        if (!pollSession.accepts(sessionToken) || !operatorMode || !activityResumed)
                                            throw new IOException("Шлюз остановлен");
                                        new ModbusTcpClient(binding.host, binding.port).writeMultiple(
                                                binding.unit, binding.address, values);
                                    }
                                });
                        updates.put(widgetIndex, outcome.transferred
                                ? "OK: передано " + outcome.count : "Ожидание события");
                    } catch (Exception failure) {
                        updates.put(widgetIndex, "ERR: " + describeModbusError(failure));
                    }
                }
                for (int widgetIndex : bitGatewayIndices) {
                    if (!pollSession.accepts(sessionToken)) return;
                    Widget gatewayWidget = snapshotWidgets.get(widgetIndex);
                    try {
                        CraftBitGateway.Config config = CraftBitGateway.from(snapshotExtras.get(widgetIndex));
                        if (config == null) { craftBitGatewayStates.remove(gatewayWidget); continue; }
                        CraftBitGateway.State state = craftBitGatewayStates.get(gatewayWidget);
                        if (state == null) {
                            CraftBitGateway.State created = new CraftBitGateway.State();
                            CraftBitGateway.State existing = craftBitGatewayStates.putIfAbsent(gatewayWidget, created);
                            state = existing == null ? created : existing;
                        }
                        CraftBitGateway.Outcome outcome = CraftBitGateway.transfer(config, state,
                                new CraftBitGateway.Transport() {
                                    @Override public int[] read(CraftBitGateway.Binding binding, int quantity) throws IOException {
                                        if (!pollSession.accepts(sessionToken)) throw new IOException("Сеанс проекта завершён");
                                        return new ModbusTcpClient(binding.host, binding.port).read(
                                                binding.unit, binding.address, quantity, binding.function);
                                    }
                                    @Override public void write(CraftBitGateway.Binding binding, int[] values) throws IOException {
                                        if (!pollSession.accepts(sessionToken) || !operatorMode || !activityResumed)
                                            throw new IOException("Шлюз остановлен");
                                        ModbusTcpClient client = new ModbusTcpClient(binding.host, binding.port);
                                        if (values.length == 1) client.writeSingleCoil(binding.unit, binding.address, values[0] == 1);
                                        else client.writeMultipleCoils(binding.unit, binding.address, values);
                                    }
                                });
                        updates.put(widgetIndex, outcome.transferred
                                ? "OK: передано бит " + outcome.count : "Ожидание события");
                    } catch (Exception failure) {
                        updates.put(widgetIndex, "ERR: " + describeModbusError(failure));
                    }
                }
                final Map<Integer, Boolean> archiveTriggerUpdates = new HashMap<>();
                for (int widgetIndex : archiveTriggerIndices) {
                    if (!pollSession.accepts(sessionToken)) return;
                    try {
                        String binding = snapshotExtras.get(widgetIndex).optString("craftArchiveTriggerBinding", "").trim();
                        BindingRequest trigger = parseBinding(widgetIndex, binding);
                        if (trigger == null) throw new IOException("Некорректная привязка триггера архива");
                        int[] words = new ModbusTcpClient(trigger.host, trigger.port).read(
                                trigger.unit, trigger.address, 1, trigger.functionCode);
                        archiveTriggerUpdates.put(widgetIndex, words.length > 0 && words[0] != 0);
                    } catch (Exception failure) {
                        archiveTriggerUpdates.put(widgetIndex, null);
                    }
                }
                runOnUiThread(() -> {
                    if (!pollSession.accepts(sessionToken) || !activityResumed
                            || editor != sessionEditor || isFinishing() || isDestroyed()) return;
                    for (Map.Entry<Integer, Boolean> trigger : archiveTriggerUpdates.entrySet()) {
                        int index = trigger.getKey();
                        if (index < 0 || index >= widgets.size() || widgets.get(index) != snapshotWidgets.get(index)) continue;
                        Widget archive = widgets.get(index);
                        Boolean current = trigger.getValue();
                        if (current == null) {
                            craftArchiveTriggerEdges.remove(archive);
                        } else {
                            boolean previous = Boolean.TRUE.equals(craftArchiveTriggerHigh.put(archive, current));
                            if (current && !previous) craftArchiveTriggerEdges.add(archive);
                        }
                    }
                    for (Map.Entry<Integer, String> entry : updates.entrySet()) {
                        int index = entry.getKey();
                        if (index < 0 || index >= widgets.size() || index >= snapshotWidgets.size()
                                || widgets.get(index) != snapshotWidgets.get(index)
                                || !java.util.Objects.equals(widgets.get(index).connectionInfo, snapshotBindings.get(index))) continue;
                        Widget w = widgets.get(index);
                        liveValues.put(index, entry.getValue());
                        if (operatorMode && "График / Тренд".equals(w.type))
                            recordTrendSample(w, entry.getValue());
                        if ("Архив Craft".equals(w.type) && operatorMode && entry.getValue() != null
                                && !entry.getValue().startsWith("ERR") && !entry.getValue().startsWith("CFG")) {
                            try {
                                boolean hasTrigger = !w.extraFields.optString("craftArchiveTriggerBinding", "").trim().isEmpty();
                                if (hasTrigger && !craftArchiveTriggerEdges.remove(w)) continue;
                                String projectJson = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
                                String archiveProject = ProjectMediaService.getOrGenerateProjectId(projectJson);
                                String archiveKey = w.extraFields.optString("craftArchiveKey", "").trim();
                                if (archiveKey.isEmpty()) {
                                    archiveKey = java.util.UUID.randomUUID().toString();
                                    w.extraFields.put("craftArchiveKey", archiveKey);
                                    persistProject();
                                }
                                final String targetProject = archiveProject, targetWidget = archiveKey;
                                final String sample = entry.getValue();
                                final int interval = w.extraFields.optInt("craftArchiveIntervalSeconds", 1);
                                final int maxRows = w.extraFields.optInt("craftArchiveMaxRows", 10000);
                                modbusExecutor.execute(() -> {
                                    LocalArchiveStore store = new LocalArchiveStore(MainActivity.this);
                                    try { store.record(targetProject, targetWidget, System.currentTimeMillis(), sample, interval, maxRows); }
                                    catch (Exception failure) { android.util.Log.e("BLOODSKY", "Archive sample failed", failure); }
                                    finally { store.close(); }
                                });
                            } catch (Exception failure) {
                                android.util.Log.e("BLOODSKY", "Archive setup failed", failure);
                            }
                        }
                        if (isCraftRegisterSource(w.type)) {
                            try {
                                String[] words = entry.getValue().split(",");
                                w.craftRegisterWord0 = Integer.parseInt(words[0]);
                                w.craftRegisterWord1 = words.length > 1 ? Integer.parseInt(words[1]) : 0;
                                w.craftRegisterValid = true;
                            } catch (Exception invalid) { w.craftRegisterValid = false; }
                        }
                        if (isCraftRegisterSource(w.type) && entry.getValue() != null
                                && entry.getValue().startsWith("ERR:")) w.craftRegisterValid = false;
                        if ("FBD Вход".equals(w.type)) {
                            w.inputValue = isActiveModbusValue(entry.getValue());
                        } else if ("Звуковой сигнал".equals(w.type)) {
                            boolean active = w.soundActive;
                            if (entry.getValue() != null) {
                                try { active = Double.parseDouble(entry.getValue()) >= w.soundThreshold; } catch (Exception ignored) {}
                            }
                            String alarmKey = currentScreenIndex + ":" + index;
                            if (active && !w.lastSoundState) {
                                w.lastSoundState = true;
                                playCraftAlarmAlert(alarmKey, w.soundFilePath, w.craftAlarmSoundIndex, w.craftAlarmLoop);
                            } else if (!active) {
                                w.lastSoundState = false;
                                stopCraftAlarmAlert(alarmKey);
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
                        } else if ("Telegram-уведомление".equals(w.type)) {
                            String state = entry.getValue();
                            if (state != null && !state.startsWith("ERR:") && !state.startsWith("CFG")) {
                                boolean active = isActiveModbusValue(state);
                                if (active && !w.lastTelegramState) sendTelegramNotification(index, w, state);
                                w.lastTelegramState = active;
                            }
                        } else if ("SMS-уведомление".equals(w.type)) {
                            String state = entry.getValue();
                            if (state != null && !state.startsWith("ERR:") && !state.startsWith("CFG")) {
                                boolean active = isActiveModbusValue(state);
                                boolean craftActionEnabled = w.extraFields.optInt("craftComponentId", -1) != 10
                                        || w.extraFields.optBoolean("smsEnabled", false);
                                if (active && !w.lastSmsState && craftActionEnabled) sendSmsNotification(w, state);
                                w.lastSmsState = active;
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

    private void recordTrendSample(Widget widget, String rawValue) {
        if (rawValue == null || rawValue.startsWith("ERR") || rawValue.startsWith("CFG")) return;
        String[] encoded = rawValue.split("\\|", -1);
        int channels = Math.min(encoded.length, Math.max(1, Math.min(4,
                widget.extraFields.optInt("craftTrendChannelCount", 1))));
        CraftTrendHistory[] histories = trendHistories.get(widget);
        if (histories == null || histories.length != channels) {
            CraftTrendHistory[] created = new CraftTrendHistory[channels];
            for (int i = 0; i < channels; i++) created[i] = new CraftTrendHistory();
            trendHistories.put(widget, created);
            histories = created;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        for (int i = 0; i < channels; i++) {
            final double value;
            try { value = Double.parseDouble(encoded[i].trim()); }
            catch (NumberFormatException invalid) { continue; }
            if (!Double.isFinite(value)) continue;
            histories[i].append(value, now,
                    widget.extraFields.optLong("craftTrendIntervalMs", 500),
                    widget.extraFields.optInt("craftTrendMaxPoints", 300));
        }
    }

    private void drawCraftTrendGraph(Canvas canvas, Widget widget, String liveValue, Paint paint) {
        float left = widget.x + dp(8), top = widget.y + dp(20);
        float right = widget.x + widget.w - dp(8), bottom = widget.y + widget.h - dp(8);
        if (right <= left || bottom <= top) return;
        int oldColor = paint.getColor();
        float oldWidth = paint.getStrokeWidth(), oldText = paint.getTextSize();
        Paint.Style oldStyle = paint.getStyle();
        Paint.Align oldAlign = paint.getTextAlign();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(widget.extraFields.optInt("craftTrendBackgroundColor", Color.BLACK));
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(widget.extraFields.optInt("craftTrendLabelsColor", Color.WHITE));
        canvas.drawRect(left, top, right, bottom, paint);
        CraftTrendHistory[] histories = trendHistories.get(widget);
        int configuredChannels = Math.max(1, Math.min(4,
                widget.extraFields.optInt("craftTrendChannelCount", 1)));
        int channelCount = histories == null ? configuredChannels : Math.min(configuredChannels, histories.length);
        double[][] series = new double[channelCount][];
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int channel = 0; channel < channelCount; channel++) {
            series[channel] = histories == null || channel >= histories.length
                    ? new double[0] : histories[channel].snapshotValues();
            for (double sample : series[channel]) {
                min = Math.min(min, sample);
                max = Math.max(max, sample);
            }
        }
        double[] firstSeries = series.length == 0 ? new double[0] : series[0];
        paint.setTextSize(dp(10));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(widget.extraFields.optInt("craftTrendLabelsColor", Color.WHITE));
        JSONArray names = widget.extraFields.optJSONArray("craftTrendSeriesNames");
        String firstName = names == null ? "1" : names.optString(0, "1");
        String title = widget.text == null || widget.text.trim().isEmpty() ? firstName : widget.text;
        String latest = "ожидание привязки";
        if (liveValue != null && !liveValue.startsWith("ERR") && !liveValue.startsWith("CFG")) {
            String[] liveChannels = liveValue.split("\\|", -1);
            latest = liveChannels[0];
        } else if (firstSeries.length > 0) {
            latest = String.format(java.util.Locale.ROOT, "%.3f", firstSeries[firstSeries.length - 1]);
        }
        canvas.drawText(title + "  " + latest, left + dp(3), top - dp(5), paint);
        if (max > Double.NEGATIVE_INFINITY) {
            if (max - min < 1e-9) { min -= 1; max += 1; }
            float plotLeft = left + dp(3), plotRight = Math.max(plotLeft, right - dp(3));
            float plotTop = top + dp(25), plotBottom = Math.max(plotTop, bottom - dp(3));
            JSONArray colors = widget.extraFields.optJSONArray("craftTrendSeriesColors");
            for (int channel = 0; channel < series.length; channel++) {
                int fallback = new int[]{Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE}[channel];
                int seriesColor = colors == null ? fallback : colors.optInt(channel, fallback);
                String seriesName = names == null ? Integer.toString(channel + 1)
                        : names.optString(channel, Integer.toString(channel + 1));
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(dp(8));
                paint.setColor(seriesColor);
                float columnWidth = (right - left) / 2f;
                float legendX = left + dp(4) + (channel % 2) * columnWidth;
                float legendY = top + dp(10) + (channel / 2) * dp(10);
                canvas.drawText(seriesName, legendX, legendY, paint);
                double[] samples = series[channel];
                if (samples.length == 0) continue;
                android.graphics.Path line = new android.graphics.Path();
                for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
                    float x = samples.length == 1 ? (plotLeft + plotRight) / 2f
                            : plotLeft + (plotRight - plotLeft) * sampleIndex / (samples.length - 1f);
                    float y = plotBottom - (float) ((samples[sampleIndex] - min) / (max - min))
                            * (plotBottom - plotTop);
                    if (sampleIndex == 0) line.moveTo(x, y); else line.lineTo(x, y);
                }
                paint.setColor(seriesColor);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                canvas.drawPath(line, paint);
            }
        } else if (liveValue != null && (liveValue.startsWith("ERR") || liveValue.startsWith("CFG"))) {
            paint.setColor(Color.rgb(255, 120, 120));
            canvas.drawText(liveValue, left + dp(3), (top + bottom) / 2f, paint);
        }
        paint.setColor(oldColor);
        paint.setStrokeWidth(oldWidth);
        paint.setStyle(oldStyle);
        paint.setTextAlign(oldAlign);
        paint.setTextSize(oldText);
    }

    private static class BindingRequest {
        final List<Integer> widgetIndices = new ArrayList<>();
        final int port, unit, address, functionCode, quantity;
        final String host;
        final boolean usb;
        BindingRequest(int widgetIndex, String host, int port, int unit, int address, int functionCode, int quantity) {
            this(widgetIndex, host, port, unit, address, functionCode, quantity, false);
        }
        BindingRequest(int widgetIndex, String host, int port, int unit, int address, int functionCode,
                       int quantity, boolean usb) {
            this.widgetIndices.add(widgetIndex);
            this.host = host; this.port = port; this.unit = unit;
            this.address = address; this.functionCode = functionCode; this.quantity = quantity; this.usb = usb;
        }
        String registerKey() {
            if (usb) return "usb/" + unit + "/" + address + "/FC" + functionCode + "/" + quantity;
            return host.toLowerCase(java.util.Locale.ROOT) + ":" + port + "/" + unit + "/" + address + "/FC" + functionCode + "/" + quantity;
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

    private void showCraftMacroTools() {
        final android.app.Dialog dialog = new android.app.Dialog(this,
                android.R.style.Theme_Material_NoActionBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(35, 35, 35));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(4), dp(8), dp(4));
        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(24);
        back.setContentDescription("Вернуться к FBD");
        back.setAllCaps(false);
        back.setOnClickListener(v -> {
            dialog.dismiss();
            showFbdPalette();
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(56), dp(48)));

        TextView title = new TextView(this);
        title.setText("Макросы");
        title.setTextColor(Color.rgb(237, 176, 59));
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button more = new Button(this);
        more.setText("⋮");
        more.setTextSize(22);
        more.setContentDescription("Действия с макросами");
        more.setAllCaps(false);
        more.setOnClickListener(anchor -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
            menu.getMenu().add("Создать из выбранного FBD-блока")
                    .setOnMenuItemClickListener(item -> {
                        dialog.dismiss();
                        createCraftMacroFromSelection();
                        return true;
                    });
            menu.getMenu().add("Импорт .mcr").setOnMenuItemClickListener(item -> {
                dialog.dismiss();
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try { startActivityForResult(intent, REQUEST_IMPORT_CRAFT_MACRO); }
                catch (Exception failure) {
                    Toast.makeText(this, "Не удалось открыть выбор файла", Toast.LENGTH_LONG).show();
                }
                return true;
            });
            android.view.MenuItem export = menu.getMenu().add("Экспорт .mcr");
            try { export.setEnabled(FbdCraftMacroCatalog.names(projectExtraFields).length > 0); }
            catch (Exception invalidCatalog) { export.setEnabled(false); }
            export.setOnMenuItemClickListener(item -> {
                dialog.dismiss();
                exportCraftMacroFromCatalog();
                return true;
            });
            menu.getMenu().add("Другие элементы BLOODSKY").setOnMenuItemClickListener(item -> {
                dialog.dismiss();
                showExtendedWidgetMenu();
                return true;
            });
            menu.show();
        });
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        String[] names;
        try { names = FbdCraftMacroCatalog.names(projectExtraFields); }
        catch (Exception invalidCatalog) {
            names = new String[0];
            Toast.makeText(this, "Каталог макросов повреждён", Toast.LENGTH_LONG).show();
        }

        FrameLayout library = new FrameLayout(this);
        page.addView(library, new LinearLayout.LayoutParams(-1, 0, 1f));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        int columns = Math.max(1, (int) ((getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density) / 180f));
        grid.setColumnCount(columns);
        grid.setPadding(dp(16), dp(16), dp(16), dp(16));

        for (String macroName : names) {
            FbdCraftMacroDefinition definition;
            try { definition = FbdCraftMacroCatalog.find(projectExtraFields, macroName); }
            catch (Exception invalid) { definition = null; }
            if (definition == null) continue;

            View card = getLayoutInflater().inflate(R.layout.kc_macro_library_card, grid, false);
            android.graphics.drawable.GradientDrawable cardBackground =
                    new android.graphics.drawable.GradientDrawable();
            cardBackground.setColor(Color.WHITE);
            cardBackground.setCornerRadius(dp(8));
            card.setBackground(cardBackground);
            card.setElevation(0f);

            TextView cardTitle = card.findViewById(R.id.kc_macro_library_title);
            cardTitle.setText(definition.getName());
            card.setContentDescription("Добавить макрос: " + definition.getName());
            final FbdCraftMacroDefinition selectedDefinition = definition;
            card.setOnClickListener(v -> {
                try {
                    addCraftMacroWidget(selectedDefinition);
                    dialog.dismiss();
                } catch (Exception failure) {
                    Toast.makeText(this, "Не удалось добавить макрос", Toast.LENGTH_LONG).show();
                }
            });

            android.widget.GridLayout.LayoutParams cardParams =
                    new android.widget.GridLayout.LayoutParams(
                            android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED),
                            android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED));
            cardParams.width = dp(100);
            cardParams.height = dp(100);
            cardParams.setMargins(dp(16), dp(16), dp(16), dp(16));
            grid.addView(card, cardParams);
        }
        scroll.addView(grid, new android.widget.ScrollView.LayoutParams(-1, -2));
        library.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        if (names.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("Библиотека макросов пуста");
            empty.setTextColor(Color.WHITE);
            empty.setTextSize(20);
            empty.setGravity(Gravity.CENTER);
            empty.setContentDescription("Библиотека макросов пуста");
            library.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        }

        ImageView switchPage = new ImageView(this);
        switchPage.setBackgroundResource(R.drawable.kc_tab_page1);
        switchPage.setContentDescription("Палитра компонентов");
        switchPage.setOnClickListener(v -> {
            dialog.dismiss();
            showAddWidgetMenu();
        });
        switchPage.setOnLongClickListener(v -> {
            dialog.dismiss();
            showFbdPalette();
            return true;
        });
        FrameLayout.LayoutParams switchParams =
                new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM | Gravity.END);
        library.addView(switchPage, switchParams);

        dialog.setContentView(page);
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    Color.rgb(35, 35, 35)));
            window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT);
        }
    }
    private void addCraftMacroFromCatalog() {
        try {
            String[] names = FbdCraftMacroCatalog.names(projectExtraFields);
            if (names.length == 0) {
                Toast.makeText(this, "Сначала импортируй .mcr в проект", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this).setTitle("Добавить макрос")
                    .setItems(names, (dialog, which) -> {
                        try {
                            FbdCraftMacroDefinition definition = FbdCraftMacroCatalog.find(
                                    projectExtraFields, names[which]);
                            if (definition != null) addCraftMacroWidget(definition);
                        } catch (Exception invalid) {
                            Toast.makeText(this, "Не удалось добавить макрос", Toast.LENGTH_LONG).show();
                        }
                    }).setNegativeButton("Отмена", null).show();
        } catch (Exception invalidCatalog) {
            Toast.makeText(this, "Каталог макросов повреждён", Toast.LENGTH_LONG).show();
        }
    }

    private void addCraftMacroWidget(FbdCraftMacroDefinition definition) throws Exception {
        Widget widget = new Widget();
        widget.type = "FBD CRAFT MACRO";
        widget.text = definition.getName();
        widget.extraFields.put("craftMacroDefinition", definition.toJson());
        widget.w = dp(170);
        widget.h = Math.max(dp(76), dp(22) * Math.max(2, definition.getInputs().size()));
        if (editor != null && editor.getWidth() > 0 && editor.getHeight() > 0) {
            float[] empty = freeFbdPlacement(widgets, editor.getWidth(), editor.getHeight(),
                    widget.w, widget.h, dp(40), dp(50), dp(18));
            if (empty == null) {
                Toast.makeText(this, "Нет свободного места для FBD-макроса", Toast.LENGTH_LONG).show();
                return;
            }
            widget.x = empty[0];
            widget.y = empty[1];
        } else {
            widget.x = dp(40) + (widgets.size() % 4) * dp(24);
            widget.y = dp(50) + (widgets.size() % 6) * dp(22);
        }
        fbdWireHistory.clear();
        liveValues.clear();
        widgets.add(widget);
        persistProject();
        if (editor != null) editor.invalidate();
    }

    private void createCraftMacroFromSelection() {
        if (editor == null || editor.selected < 0 || editor.selected >= widgets.size()
                || !isFbd(widgets.get(editor.selected))) {
            Toast.makeText(this, "Сначала выбери выходной FBD-блок на схеме", Toast.LENGTH_LONG).show();
            return;
        }
        Widget output = widgets.get(editor.selected);
        String[] outputPins = knownFbdPins(output, true);
        if (outputPins.length == 0) {
            Toast.makeText(this, "У выбранного блока нет проверенного выхода", Toast.LENGTH_LONG).show();
            return;
        }
        String suggestedName = output.text == null || output.text.trim().isEmpty()
                ? "Macro" : output.text.trim();
        if (suggestedName.length() > 10) suggestedName = suggestedName.substring(0, 10);
        EditText name = propertyField(suggestedName, InputType.TYPE_CLASS_TEXT);
        name.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(10)});
        new AlertDialog.Builder(this).setTitle("Создать макрос из схемы")
                .setMessage("Вложенная схема будет взята от выбранного блока до его входных источников.")
                .setView(name).setNegativeButton("Отмена", null)
                .setPositiveButton("Далее", (dialog, which) -> {
                    String macroName = name.getText().toString().trim();
                    if (outputPins.length == 1) saveCraftMacroFromSelection(macroName,
                            editor.selected, outputPins[0]);
                    else new AlertDialog.Builder(this).setTitle("Выбери выход макроса")
                            .setItems(outputPins, (pinDialog, pinIndex) ->
                                    saveCraftMacroFromSelection(macroName, editor.selected,
                                            outputPins[pinIndex]))
                            .setNegativeButton("Отмена", null).show();
                }).show();
    }

    private void saveCraftMacroFromSelection(String name, int outputNode, String outputPin) {
        try {
            if (outputNode < 0 || outputNode >= widgets.size()) throw new IllegalArgumentException(
                    "Выбранный блок больше не существует");
            List<FbdConnection> edges = screens.get(currentScreenIndex).connections;
            FbdCraftMacroDefinition definition = createCraftMacroDefinitionFromGraph(
                    name, widgets, edges, outputNode, outputPin);
            FbdCraftMacroCatalog.upsert(projectExtraFields, definition);
            if (!persistProjectNow(true)) throw new IOException("Не удалось сохранить проект");
            int refreshed = replaceCraftMacroInstances(widgets, definition);
            persistProject();
            if (editor != null) editor.invalidate();
            addCraftMacroWidget(definition);
            Toast.makeText(this, "Макрос «" + definition.getName() + "» создан; обновлено блоков: "
                    + refreshed + ", добавлен новый экземпляр",
                    Toast.LENGTH_LONG).show();
        } catch (Exception failure) {
            Toast.makeText(this, "Макрос не создан: " + failure.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Capture one selected output and its upstream closure as a typed macro definition. */
    public static FbdCraftMacroDefinition createCraftMacroDefinitionFromGraph(String name,
            List<Widget> sourceWidgets, List<FbdConnection> sourceConnections,
            int outputNode, String outputPin) throws Exception {
        if (sourceWidgets == null || sourceConnections == null || outputNode < 0
                || outputNode >= sourceWidgets.size()) throw new IllegalArgumentException("Invalid macro source graph");
        Widget outputWidget = sourceWidgets.get(outputNode);
        int typeOut = craftMacroGraphPortType(sourceWidgets, sourceConnections, outputNode, outputPin, true, new boolean[sourceWidgets.size()]);
        if (typeOut < 0) throw new IllegalArgumentException("Unsupported macro output type");

        boolean[] included = new boolean[sourceWidgets.size()];
        List<Integer> pending = new ArrayList<>();
        pending.add(outputNode);
        while (!pending.isEmpty()) {
            int target = pending.remove(pending.size() - 1);
            if (target < 0 || target >= sourceWidgets.size() || included[target]) continue;
            Widget widget = sourceWidgets.get(target);
            if (!isFbdWidget(widget))
                throw new IllegalArgumentException("Нельзя включить в макрос неподдерживаемый блок");
            if ("FBD CRAFT MACRO".equals(canonicalFbdBlockType(widget.type))) {
                FbdCraftMacroDefinition nested = macroDefinition(widget);
                JSONObject nestedGraph = nested == null ? null
                        : nested.toJson().optJSONObject("bloodskyGraph");
                if (nestedGraph == null)
                    throw new IllegalArgumentException(
                            "Вложенный макрос должен иметь проверенный граф BLOODSKY");
            }
            included[target] = true;
            for (FbdConnection edge : sourceConnections)
                if (edge != null && edge.target == target) pending.add(edge.source);
        }

        List<Integer> nodeIds = new ArrayList<>();
        for (int i = 0; i < included.length; i++) if (included[i]) nodeIds.add(i);
        boolean[] isInput = new boolean[sourceWidgets.size()];
        int[] firstOutputType = new int[sourceWidgets.size()];
        int inputCount = 0;
        for (int node : nodeIds) {
            boolean hasIncoming = false;
            for (FbdConnection edge : sourceConnections)
                if (edge != null && edge.target == node && edge.source >= 0
                        && edge.source < included.length && included[edge.source]) { hasIncoming = true; break; }
            if (hasIncoming) continue;
            Widget leaf = sourceWidgets.get(node);
            String[] pins = knownFbdPins(leaf, true);
            int code = pins.length == 0 ? -1 : craftMacroGraphPortType(sourceWidgets, sourceConnections, node, pins[0], true, new boolean[sourceWidgets.size()]);
            if (code < 0) throw new IllegalArgumentException("Входной источник без поддерживаемого типа");
            if (++inputCount > 4) throw new IllegalArgumentException("В макросе допускается не больше четырёх входов");
            isInput[node] = true;
            firstOutputType[node] = code;
        }

        JSONArray nestedWidgets = new JSONArray();
        JSONArray nestedConnections = new JSONArray();
        int[] remap = new int[sourceWidgets.size()];
        StringBuilder root = new StringBuilder("{\"name\":").append(JSONObject.quote(name))
                .append(",\"uniqueId\":").append(System.currentTimeMillis())
                .append(",\"countIn\":").append(inputCount);
        int inputIndex = 0;
        for (int node : nodeIds) {
            remap[node] = nestedWidgets.length();
            if (isInput[node]) {
                String portName = "I" + (++inputIndex);
                int code = firstOutputType[node];
                JSONObject inputWidget = new JSONObject().put("type", "FBD CRAFT MACRO IN")
                        .put("craftMacroPortName", portName).put("craftMacroPortType", code);
                nestedWidgets.put(inputWidget);
                root.append(",\"nameIn").append(inputIndex).append("\":").append(JSONObject.quote(portName))
                        .append(",\"typeIn").append(inputIndex).append("\":").append(code);
            } else nestedWidgets.put(widgetToJson(sourceWidgets.get(node)));
        }
        for (FbdConnection edge : sourceConnections) {
            if (edge == null || edge.source < 0 || edge.target < 0
                    || edge.source >= included.length || edge.target >= included.length
                    || !included[edge.source] || !included[edge.target]) continue;
            JSONObject nestedEdge = connectionToJson(edge);
            nestedEdge.put("source", remap[edge.source]).put("target", remap[edge.target]);
            if (isInput[edge.source]) nestedEdge.put("sourcePin", "Q");
            nestedConnections.put(nestedEdge);
        }
        root.append(",\"nameOut\":").append(JSONObject.quote(outputPin))
                .append(",\"typeOut\":").append(typeOut)
                .append(",\"fbInMacros\":[],\"bloodskyGraph\":{")
                .append("\"widgets\":").append(nestedWidgets)
                .append(",\"connections\":").append(nestedConnections)
                .append(",\"outputNode\":").append(remap[outputNode])
                .append(",\"outputPin\":").append(JSONObject.quote(isInput[outputNode] ? "Q" : outputPin))
                .append(",\"outputPort\":").append(JSONObject.quote(outputPin)).append("}}");
        return FbdCraftMacroDefinition.fromJson(root.toString());
    }

    /** Refresh every placed instance when the project definition is rebuilt under the same name. */
    public static int replaceCraftMacroInstances(List<Widget> widgets,
                                                 FbdCraftMacroDefinition definition) throws Exception {
        if (widgets == null || definition == null) return 0;
        int updated = 0;
        for (Widget widget : widgets) {
            if (widget == null || !"FBD CRAFT MACRO".equals(canonicalFbdBlockType(widget.type))) continue;
            FbdCraftMacroDefinition existing = macroDefinition(widget);
            if (existing == null || !existing.getName().equalsIgnoreCase(definition.getName())) continue;
            widget.extraFields.put("craftMacroDefinition", definition.toJson());
            widget.craftMacroDefinitionSourceCache = null;
            widget.craftMacroDefinitionCache = null;
            widget.craftMacroRuntimeGraphSource = null;
            widget.craftMacroRuntimeWidgets = null;
            widget.craftMacroRuntimeConnections = null;
            widget.craftMacroRuntimeOutputNode = -1;
            updated++;
        }
        return updated;
    }

    private static int craftMacroPortType(Widget widget, String pin, boolean output) {
        String signal = knownFbdPinType(widget, pin, output);
        if ("BOOL".equals(signal)) return 1;
        if (!"NUMBER".equals(signal)) return -1;
        String kind = canonicalFbdBlockType(widget == null ? null : widget.type);
        if ((kind.contains("FLOAT") && !kind.endsWith("FLOAT TO INT"))
                || kind.equals("FBD CRAFT LOG") || kind.equals("FBD CRAFT POW")) return 3;
        return 2;
    }

    /** Infer polymorphic numeric outputs from their connected upstream signal types. */
    private static int craftMacroGraphPortType(List<Widget> graphWidgets,
            List<FbdConnection> graphConnections, int node, String pin, boolean output,
            boolean[] visiting) {
        if (graphWidgets == null || graphConnections == null || node < 0
                || node >= graphWidgets.size()) return -1;
        Widget widget = graphWidgets.get(node);
        int declared = craftMacroPortType(widget, pin, output);
        if (declared != 2 || !output
                || !"NUMBER".equals(knownFbdPinType(widget, pin, true))) return declared;
        String kind = canonicalFbdBlockType(widget == null ? null : widget.type);
        // Explicit integer/floating blocks keep their declared conversion direction.
        if (kind.contains("INT") || kind.contains("FLOAT")
                || "FBD CRAFT LOG".equals(kind) || "FBD CRAFT POW".equals(kind))
            return declared;
        if (visiting == null || visiting.length != graphWidgets.size() || visiting[node])
            return declared;
        visiting[node] = true;
        boolean hasNumericInput = false;
        boolean hasFloatInput = false;
        for (FbdConnection edge : graphConnections) {
            if (edge == null || edge.target != node || edge.source < 0
                    || edge.source >= graphWidgets.size()) continue;
            int inputType = craftMacroGraphPortType(graphWidgets, graphConnections,
                    edge.source, edge.extraFields == null ? "" : edge.extraFields.optString("sourcePin", ""), true, visiting);
            if (inputType == 2 || inputType == 3) {
                hasNumericInput = true;
                if (inputType == 3) hasFloatInput = true;
            }
        }
        visiting[node] = false;
        return hasNumericInput && hasFloatInput ? 3 : declared;
    }

    /** Resolve a nested macro output by widget identity after graph edits reindex the canvas. */
    static int findCraftMacroOutputNode(List<Widget> graphWidgets, Widget outputWidget) {
        if (graphWidgets == null || outputWidget == null) return -1;
        for (int i = 0; i < graphWidgets.size(); i++)
            if (graphWidgets.get(i) == outputWidget) return i;
        return -1;
    }

    /** Replace an editable nested graph, re-infer its output type, and validate the full definition. */
    public static FbdCraftMacroDefinition updateCraftMacroDefinitionGraph(
            FbdCraftMacroDefinition definition, List<Widget> graphWidgets,
            List<FbdConnection> graphConnections, int outputNode, String outputPin) throws Exception {
        if (definition == null || graphWidgets == null || graphConnections == null)
            throw new IllegalArgumentException("Missing macro graph");
        int outputType = craftMacroGraphPortType(graphWidgets, graphConnections, outputNode,
                outputPin, true, new boolean[graphWidgets.size()]);
        if (outputType < 1) throw new IllegalArgumentException("Unsupported macro output type");
        JSONObject root = definition.toJson();
        String outputPort = root.optString("nameOut", "Q");
        JSONArray nestedWidgets = new JSONArray();
        for (Widget widget : graphWidgets) nestedWidgets.put(widgetToJson(widget));
        JSONArray nestedConnections = new JSONArray();
        for (FbdConnection edge : graphConnections) nestedConnections.put(connectionToJson(edge));
        JSONObject graph = new JSONObject().put("widgets", nestedWidgets)
                .put("connections", nestedConnections).put("outputNode", outputNode)
                .put("outputPin", outputPin).put("outputPort", outputPort);
        root.put("typeOut", outputType).put("bloodskyGraph", graph);
        return FbdCraftMacroDefinition.fromJson(root.toString());
    }

    private void exportCraftMacroFromCatalog() {
        try {
            String[] names = FbdCraftMacroCatalog.names(projectExtraFields);
            if (names.length == 0) {
                Toast.makeText(this, "В проекте нет импортированных макросов", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this).setTitle("Выбери макрос")
                    .setItems(names, (dialog, which) -> {
                        try {
                            FbdCraftMacroDefinition definition = FbdCraftMacroCatalog.find(
                                    projectExtraFields, names[which]);
                            if (definition == null) return;
                            pendingCraftMacroExport = definition.toMcrBytes();
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("application/json");
                            intent.putExtra(Intent.EXTRA_TITLE,
                                    definition.getName().replaceAll("[^A-Za-zА-Яа-я0-9._-]", "_") + ".mcr");
                            startActivityForResult(intent, REQUEST_EXPORT_CRAFT_MACRO);
                        } catch (Exception failure) {
                            pendingCraftMacroExport = null;
                            Toast.makeText(this, "Не удалось подготовить макрос", Toast.LENGTH_LONG).show();
                        }
                    }).setNegativeButton("Отмена", null).show();
        } catch (Exception invalidCatalog) {
            Toast.makeText(this, "Каталог макросов повреждён", Toast.LENGTH_LONG).show();
        }
    }

    private void importCraftMacro(Uri uri) {
        modbusExecutor.execute(() -> {
            try {
                byte[] bytes;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("File unavailable");
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int total = 0, read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > FbdCraftMacroDefinition.MAX_FILE_BYTES)
                            throw new IOException("Macro file too large");
                        output.write(buffer, 0, read);
                    }
                    bytes = output.toByteArray();
                }
                FbdCraftMacroDefinition definition = FbdCraftMacroDefinition.fromMcrBytes(bytes);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    try {
                        FbdCraftMacroCatalog.upsert(projectExtraFields, definition);
                        if (!persistProjectNow(true)) throw new IOException("Project save failed");
                        Toast.makeText(this, "Макрос «" + definition.getName() + "» добавлен в проект",
                                Toast.LENGTH_LONG).show();
                    } catch (Exception failure) {
                        Toast.makeText(this, "Макрос не сохранён: " + failure.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed())
                        Toast.makeText(this, "Импорт макроса не выполнен: " + failure.getMessage(),
                                Toast.LENGTH_LONG).show();
                });
            }
        });
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
                    gifCache.clear();
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
        pendingMediaWidgetIndex = editor == null ? -1 : editor.selected;
        pendingMediaFieldPath = activePropertiesForm == null ? null : viewPath(activePropertiesForm, field, "");
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
            int readGrant = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (readGrant == 0) {
                Toast.makeText(this, "Нет доступа к выбранному файлу. Выберите другой файл.", Toast.LENGTH_LONG).show();
                return;
            }
            try { getContentResolver().takePersistableUriPermission(selected, readGrant); }
            catch (SecurityException | IllegalArgumentException ignored) {
                /* The selected bytes are copied into private project storage below. */
            }
            String projectJson = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
            AlertDialog waitingDialog = activePropertiesDialog;
            if (waitingDialog != null && waitingDialog.isShowing()) {
                pendingMediaCopyCount++;
                Button apply = waitingDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (apply != null) apply.setEnabled(false);
            }
            modbusExecutor.execute(() -> {
                try (InputStream in = getContentResolver().openInputStream(selected)) {
                    String displayName = selectedMediaDisplayName(selected);
                    ProjectMediaService.SelectedMediaCopy copy = ProjectMediaService.storeSelectedMediaCopy(
                            getFilesDir(), projectJson, displayName, in);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || waitingDialog == null
                                || waitingDialog != activePropertiesDialog || !waitingDialog.isShowing()) {
                            cleanupUnreferencedMediaCopy(projectJson, copy);
                            finishPendingMediaCopy(waitingDialog);
                            return;
                        }
                        target.setText(copy.projectPath);
                        imageCache.remove(selected.toString());
                        imageCache.remove(copy.projectPath);
                        gifCache.remove(copy.projectPath);
                        trackPendingMediaCopy(waitingDialog, projectJson, copy);
                        finishPendingMediaCopy(waitingDialog);
                        Toast.makeText(this, "Файл сохранён в проект", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception ex) {
                    android.util.Log.e("BLOODSKY", "Selected project media could not be stored", ex);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        finishPendingMediaCopy(waitingDialog);
                        Toast.makeText(this, "Не удалось сохранить файл в проект: "
                                + ex.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_LOCAL_ARCHIVE) {
            final String archiveProject = pendingArchiveProjectId;
            final String archiveWidget = pendingArchiveWidgetKey;
            pendingArchiveProjectId = pendingArchiveWidgetKey = pendingArchiveTitle = null;
            if (archiveProject == null || archiveWidget == null) return;
            modbusExecutor.execute(() -> {                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("CSV output unavailable");
                    LocalArchiveStore store = new LocalArchiveStore(this);
                    try { store.writeCsv(archiveProject, archiveWidget, out); }
                    finally { store.close(); }
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed())
                        Toast.makeText(this, "CSV архива сохранён", Toast.LENGTH_LONG).show(); });
                } catch (Exception failure) {
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed())
                        Toast.makeText(this, "Не удалось сохранить CSV архива", Toast.LENGTH_LONG).show(); });
                }
            });
            return;
        }
        if (requestCode == REQUEST_IMPORT_CRAFT_MACRO) {
            importCraftMacro(uri);
        } else if (requestCode == REQUEST_EXPORT_CRAFT_MACRO) {
            byte[] bytes = pendingCraftMacroExport;
            pendingCraftMacroExport = null;
            if (bytes == null) return;
            modbusExecutor.execute(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Output stream unavailable");
                    out.write(bytes);
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed())
                        Toast.makeText(this, "Макрос экспортирован", Toast.LENGTH_SHORT).show(); });
                } catch (Exception failure) {
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed())
                        Toast.makeText(this, "Экспорт макроса не выполнен", Toast.LENGTH_LONG).show(); });
                }
            });
        } else if (requestCode == REQUEST_EXPORT_PROJECT || requestCode == REQUEST_EXPORT_PROJECT_ZIP) {
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

    private void finishPendingMediaCopy(AlertDialog dialog) {
        if (dialog == null || dialog != activePropertiesDialog) return;
        pendingMediaCopyCount = Math.max(0, pendingMediaCopyCount - 1);
        Button apply = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (apply != null) apply.setEnabled(pendingMediaCopyCount == 0);
    }

    private static final class PendingMediaCopy {
        final String projectJson;
        final String projectPath;
        PendingMediaCopy(String projectJson, String projectPath) {
            this.projectJson = projectJson;
            this.projectPath = projectPath;
        }
    }

    private void trackPendingMediaCopy(AlertDialog dialog, String projectJson,
                                       ProjectMediaService.SelectedMediaCopy copy) {
        if (copy == null || !copy.created) return;
        List<PendingMediaCopy> copies = pendingMediaCopies.get(dialog);
        if (copies == null) {
            copies = new ArrayList<>();
            pendingMediaCopies.put(dialog, copies);
        }
        copies.add(new PendingMediaCopy(projectJson, copy.projectPath));
    }

    private void cleanupUnreferencedMediaCopy(String projectJson,
                                              ProjectMediaService.SelectedMediaCopy copy) {
        if (copy == null || !copy.created) return;
        String savedProjectJson = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
        try {
            ProjectMediaService.deleteUnreferencedSelectedMedia(
                    getFilesDir(), projectJson, copy.projectPath, savedProjectJson);
        } catch (IOException cleanupFailure) {
            android.util.Log.w("BLOODSKY", "Could not remove canceled project media", cleanupFailure);
        }
    }

    private void cleanupPendingMediaCopies(AlertDialog dialog) {
        List<PendingMediaCopy> copies = pendingMediaCopies.remove(dialog);
        if (copies == null || copies.isEmpty()) return;
        String savedProjectJson = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
        for (PendingMediaCopy copy : copies) {
            try {
                ProjectMediaService.deleteUnreferencedSelectedMedia(
                        getFilesDir(), copy.projectJson, copy.projectPath, savedProjectJson);
            } catch (IOException cleanupFailure) {
                android.util.Log.w("BLOODSKY", "Could not remove canceled project media", cleanupFailure);
            }
        }
    }

    /** Keep a picker copy once an image-state edit has synchronously saved its reference. */
    private void commitPendingMediaCopy(String projectPath) {
        if (projectPath == null || projectPath.isEmpty() || pendingMediaCopies.isEmpty()) return;
        String activeProject = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
        String activeProjectId;
        try { activeProjectId = ProjectMediaService.getOrGenerateProjectId(activeProject); }
        catch (RuntimeException invalidProject) { return; }
        java.util.Iterator<Map.Entry<AlertDialog, List<PendingMediaCopy>>> entries =
                pendingMediaCopies.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<AlertDialog, List<PendingMediaCopy>> entry = entries.next();
            List<PendingMediaCopy> copies = entry.getValue();
            java.util.Iterator<PendingMediaCopy> pending = copies.iterator();
            while (pending.hasNext()) {
                PendingMediaCopy copy = pending.next();
                if (!projectPath.equals(copy.projectPath)) continue;
                try {
                    if (activeProjectId.equals(ProjectMediaService.getOrGenerateProjectId(copy.projectJson)))
                        pending.remove();
                } catch (RuntimeException ignored) { }
            }
            if (copies.isEmpty()) entries.remove();
        }
    }

    private String selectedMediaDisplayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    String name = cursor.getString(column);
                    if (name != null && !name.trim().isEmpty()) return name;
                }
            }
        } catch (Exception ignored) { }
        return uri == null ? "media.bin" : uri.getLastPathSegment();
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

    private void alignSelectedWidgets(String alignMode) {
        if (editor == null || editor.selectedWidgets.size() < 2) return;
        ArrayList<Integer> indexes = new ArrayList<>();
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (int index : editor.selectedWidgets) {
            if (index >= 0 && index < widgets.size() && seen.add(index)) indexes.add(index);
        }
        if (indexes.size() < 2) {
            Toast.makeText(this, "Выбери несколько элементов", Toast.LENGTH_SHORT).show();
            return;
        }
        float[][] geometry = new float[indexes.size()][4];
        for (int i = 0; i < indexes.size(); i++) {
            Widget widget = widgets.get(indexes.get(i));
            geometry[i][0] = widget.x;
            geometry[i][1] = widget.y;
            geometry[i][2] = widget.w;
            geometry[i][3] = widget.h;
        }
        float[][] aligned = CanvasGroupAlignment.apply(geometry, alignMode);
        for (int i = 0; i < indexes.size(); i++) {
            Widget widget = widgets.get(indexes.get(i));
            widget.x = aligned[i][0];
            widget.y = aligned[i][1];
            widget.w = aligned[i][2];
            widget.h = aligned[i][3];
        }
        persistProject();
        editor.invalidate();
        Toast.makeText(this, "Элементы выровнены", Toast.LENGTH_SHORT).show();
    }

    private void showAlignDialog() {
        if (editor == null || editor.selected < 0 || editor.selected >= widgets.size()) {
            Toast.makeText(this, "Выбери элемент на экране", Toast.LENGTH_SHORT).show();
            return;
        }
        if (editor.selectedWidgets.size() > 1) {
            ArrayList<String> options = new ArrayList<>(java.util.Arrays.asList(
                    "По левому краю группы", "По правому краю группы",
                    "По верхнему краю группы", "По нижнему краю группы",
                    "По центру группы горизонтально", "По центру группы вертикально"));
            ArrayList<String> modes = new ArrayList<>(java.util.Arrays.asList(
                    "left", "right", "top", "bottom", "center_h", "center_v"));
            if (editor.selectedWidgets.size() >= 3) {
                options.add("Распределить по горизонтали");
                options.add("Распределить по вертикали");
                modes.add("space_h");
                modes.add("space_v");
            }
            options.add("Одинаковая максимальная ширина");
            options.add("Одинаковая максимальная высота");
            modes.add("max_width");
            modes.add("max_height");
            new AlertDialog.Builder(this)
                    .setTitle("Выравнивание выбранных элементов")
                    .setItems(options.toArray(new String[0]), (dialog, which) -> alignSelectedWidgets(modes.get(which)))
                    .setNegativeButton("Отмена", null)
                    .show();
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
        result.put("systemRegisterSettings", registers);
        if (result.opt("systemRegisters") instanceof String) result.remove("systemRegisters");
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
        Object savedSystemRegisters = projectExtraFields.opt("systemRegisters");
        String legacySystemRegisterSettings = savedSystemRegisters instanceof String
                ? (String) savedSystemRegisters : "SYS_STATUS=0, SYS_TIME=100";
        EditText sysRegField = propertyField(projectExtraFields.optString("systemRegisterSettings",
                legacySystemRegisterSettings), InputType.TYPE_CLASS_TEXT);
        sysRegField.setHint("Системные регистры");
        form.addView(sysRegField);

        EditText[][] mqttFields = {addMqttBrokerFields(form, 1), addMqttBrokerFields(form, 2)};

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
                for (int i = 0; i < mqttFields.length; i++) {
                    String endpoint = mqttFields[i][0].getText().toString().trim();
                    if (!endpoint.isEmpty() && !FbdCraftMqttRuntime.validUri(endpoint))
                        throw new IllegalArgumentException("MQTT сервер " + (i + 1) + ": tcp://адрес:порт или ssl://адрес:порт");
                    JSONObject broker = new JSONObject();
                    broker.put("uri", endpoint);
                    broker.put("username", mqttFields[i][1].getText().toString());
                    broker.put("password", mqttFields[i][2].getText().toString());
                    getSharedPreferences("bloodsky_mqtt", MODE_PRIVATE).edit()
                            .putString("server" + (i + 1), broker.toString()).apply();
                }
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

    private EditText[] addMqttBrokerFields(LinearLayout form, int number) {
        JSONObject saved;
        try { saved = new JSONObject(getSharedPreferences("bloodsky_mqtt", MODE_PRIVATE)
                .getString("server" + number, "{}")); }
        catch (Exception invalid) { saved = new JSONObject(); }
        TextView title = new TextView(this);
        title.setText("MQTT сервер " + number + " (Craft)");
        title.setPadding(0, dp(12), 0, 0);
        form.addView(title);
        EditText uri = propertyField(saved.optString("uri", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        uri.setHint("tcp://192.168.1.10:1883 или ssl://broker:8883"); form.addView(uri);
        EditText username = propertyField(saved.optString("username", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        username.setHint("Логин (необязательно)"); form.addView(username);
        EditText password = propertyField(saved.optString("password", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setHint("Пароль (необязательно)"); form.addView(password);
        return new EditText[]{uri, username, password};
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
        if (operatorMode) {
            area.addView(editor, new android.widget.FrameLayout.LayoutParams(-1, -1));
        } else {
            // Keep the visible Craft-style screen viewport, but give the editor a
            // larger scrollable design surface so dense FBD graphs are not capped
            // by the current device dimensions.
            android.widget.ScrollView verticalWorkspace = new android.widget.ScrollView(this);
            verticalWorkspace.setFillViewport(true);
            verticalWorkspace.setVerticalScrollBarEnabled(true);
            verticalWorkspace.setSmoothScrollingEnabled(false);
            int workspaceHeight = dp(2400);
            // ScrollView measures a custom View's vertical axis as unspecified;
            // keep the design surface taller than the viewport for real scroll range.
            editor.setMinimumHeight(workspaceHeight);
            verticalWorkspace.addView(editor,
                    new android.widget.ScrollView.LayoutParams(-1, workspaceHeight));
            area.addView(verticalWorkspace,
                    new android.widget.FrameLayout.LayoutParams(-1, -1));
        }
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
        configureCraftServiceRegisters();
        if (activityResumed) {
            mainHandler.removeCallbacks(craftServiceRegisterTicker);
            mainHandler.postDelayed(craftServiceRegisterTicker, 1000L);
        }
        setContentView(root);
        EditorCanvas attachedEditor = editor;
        // A landscape orientation change can attach the new editor tree before its first
        // measure/draw pass. Invalidate after attachment so the canvas is not left as the
        // transparent root background in emulator captures and on slower devices.
        attachedEditor.post(() -> {
            if (editor == attachedEditor) {
                attachedEditor.requestLayout();
                attachedEditor.invalidate();
            }
        });
        startModbusPolling(); startFbdSensors(); startCraftMqtt();
        refreshIncomingSmsReceiver();
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
        if (nestedMacroEdit != null) return;
        if (!persistProjectNow(true)) return;
        // Editing must not advance timers. Re-entering runtime starts a fresh
        // clock epoch rather than resuming an old saved elapsedRealtime value.
        if (!operatorMode) {
            FbdCraftRegisterStore.clear();
            FbdCraftNamedVariableStore.clear();
            for (Widget value : widgets) {
                resetFbdTimerRuntime(value);
                value.craftMqttLastEnable = false;
                value.craftMqttValue = null;
            }
        }
        operatorMode = !operatorMode; fbdWireMode = false; wireSource = -1;
        if (!operatorMode) for (Widget value : widgets) if (isCraftOdTempSource(value.type)) {
            value.craftSensorValid = false; value.craftOdTempNextPollAt = 0L;
        }
        showEditor();
    }

    private void showEditorCommands(View anchor) {
        if (nestedMacroEdit != null) {
            showEditorAdvancedCommands(anchor);
            return;
        }
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(4), 0, dp(4));
        rows.setBackgroundColor(Color.rgb(35, 35, 35));
        addSourceMenuRow(rows, "Сетевые устройства", "Слоты устройств и редактор серверов Craft", true,
                this::showCraftNetworkDevices);
        addSourceMenuRow(rows, "Пароль проекта", "Защищённая блокировка проекта пока не подключена", false, null);
        boolean station = projectExtraFields.optInt("stationMode", 0) == 1;
        addSourceMenuRow(rows, station ? "Режим станции: Слейв" : "Режим станции: Мастер",
                "Изменить сохранённый режим; сервер не запускается", true, () -> {
                    try {
                        projectExtraFields.put("stationMode", station ? 0 : 1);
                        persistProject();
                        showEditor();
                    } catch (Exception failure) {
                        Toast.makeText(this, "Не удалось сохранить режим станции", Toast.LENGTH_LONG).show();
                    }
                });
        addSourceMenuRow(rows, "Показывать тайм-ауты", "Отдельное отображение тайм-аутов пока не подключено", false, null);
        // Craft includes the Wi-Fi-to-USB row only when its hidden feature flag is enabled.
        addSourceMenuRow(rows, "Сетевое управление", "Modbus TCP Slave-сервер пока не реализован", false, null);
        addSourceMenuRow(rows, "Работа в фоне", "Фоновый сервис проекта пока не реализован", false, null);
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(75, 75, 75));
        rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        addSourceMenuRow(rows, "Дополнительные действия BLOODSKY…", "Проект, экраны, FBD, интеграции и диагностика", true,
                () -> showEditorAdvancedCommands(anchor));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(rows);
        android.widget.PopupWindow popup = new android.widget.PopupWindow(scroll, dp(292),
                Math.min(dp(560), getResources().getDisplayMetrics().heightPixels - dp(48)), true);
        sourceOverflowPopup = popup;
        popup.setOnDismissListener(() -> { if (sourceOverflowPopup == popup) sourceOverflowPopup = null; });
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.rgb(35, 35, 35)));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(8));
        popup.setTouchable(true);
        popup.showAsDropDown(anchor, -dp(292) + anchor.getWidth(), 0, Gravity.END);
    }

    private void addSourceMenuRow(LinearLayout parent, String title, String detail, boolean enabled, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(8), dp(12), dp(8));
        TextView heading = new TextView(this);
        heading.setText(title); heading.setTextSize(15); heading.setMaxLines(2);
        heading.setTextColor(enabled ? Color.rgb(238, 238, 238) : Color.rgb(142, 142, 142));
        row.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        if (detail != null && !detail.isEmpty()) {
            TextView caption = new TextView(this);            caption.setText(detail); caption.setTextSize(11); caption.setMaxLines(2);
            caption.setTextColor(enabled ? Color.rgb(174, 174, 174) : Color.rgb(112, 112, 112));
            row.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        }
        row.setMinimumHeight(dp(52));
        row.setEnabled(enabled);
        if (enabled && action != null) {
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setOnClickListener(v -> {
                if (sourceOverflowPopup != null) sourceOverflowPopup.dismiss();
                action.run();
            });
        }
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showBoundProjectDevices() {
        java.util.Map<String, Integer> endpoints = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Widget widget : widgets) {
            String binding = widget.connectionInfo == null ? "" : widget.connectionInfo.trim();
            if (binding.startsWith("tcp://")) {
                Integer count = endpoints.get(binding);
                endpoints.put(binding, count == null ? 1 : count + 1);
            }
        }
        StringBuilder text = new StringBuilder();
        if (endpoints.isEmpty()) text.append("В проекте пока нет локальных TCP-привязок.");
        else for (Map.Entry<String, Integer> endpoint : endpoints.entrySet())
            text.append(endpoint.getKey()).append("  ·  компонентов: ").append(endpoint.getValue()).append('\n');
        new AlertDialog.Builder(this).setTitle("Устройства / серверы проекта")
                .setMessage(text.toString()).setPositiveButton("Закрыть", null).show();
    }

    private void showCraftNetworkDevices() {
        craftNetworkScreen = "devices";
        View page = getLayoutInflater().inflate(R.layout.craft_layout_device_network, null);
        androidx.appcompat.widget.Toolbar toolbar = page.findViewById(R.id.craft_network_device_toolbar);
        toolbar.setTitle(R.string.craft_title); toolbar.setSubtitle(R.string.craft_network_devices);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationContentDescription("Назад");
        toolbar.setNavigationOnClickListener(v -> { saveCraftNetworkDevices(); craftNetworkScreen = ""; showEditor(); });
        JSONArray devices = CraftServerConnectionStore.devices(projectExtraFields);
        if (devices.length() == 0) {
            try {
                for (int i = 0; i < 16; i++)
                    devices.put(new JSONObject().put("name", "").put("address", 1).put("serverIndex", -1));
            }
            catch (Exception ignored) {}
        }
        TextView count = page.findViewById(R.id.craft_textCountDevice);
        ImageView remove = page.findViewById(R.id.craft_minus), add = page.findViewById(R.id.craft_plus);
        final int[] renderGeneration = {0};
        Runnable render = () -> {
            renderGeneration[0]++;
            JSONArray current = CraftServerConnectionStore.devices(projectExtraFields);
            count.setText(Integer.toString(current.length()));
            for (int i = 0; i < 16; i++) {
                int slot = i + 1;
                int rowId = getResources().getIdentifier("craft_tableRow" + slot, "id", getPackageName());
                View row = page.findViewById(rowId);
                if (row == null) continue;
                boolean visible = i < current.length(); row.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (!visible) continue;
                JSONObject item = current.optJSONObject(i); if (item == null) item = new JSONObject();
                EditText name = page.findViewById(getResources().getIdentifier("craft_editNameDevice" + slot, "id", getPackageName()));
                EditText address = page.findViewById(getResources().getIdentifier("craft_editAddressDevice" + slot, "id", getPackageName()));
                TextView server = page.findViewById(getResources().getIdentifier("craft_textServerDevice" + slot, "id", getPackageName()));
                name.setText(item.optString("name", "")); name.setContentDescription("Имя сетевого устройства " + slot);
                address.setText(Integer.toString(item.optInt("address", 1))); address.setContentDescription("Адрес устройства " + slot);
                int selected = item.optInt("serverIndex", -1);
                JSONArray servers = CraftServerConnectionStore.connections(projectExtraFields);
                JSONObject config = selected >= 0 && selected < servers.length() ? servers.optJSONObject(selected) : null;
                server.setText(config == null ? "-" : CraftServerConnectionStore.displayEndpoint(config));
                server.setContentDescription("Сервер устройства " + slot);
                server.setOnClickListener(v -> { saveCraftNetworkDevices(); showCraftServerList(true, slot - 1); });
                final int rowIndex = i, generation = renderGeneration[0];
                name.addTextChangedListener(simpleTextWatcher(value -> { if (generation == renderGeneration[0]) updateCraftDevice(rowIndex, "name", value); }));
                address.addTextChangedListener(simpleTextWatcher(value -> { if (generation == renderGeneration[0]) updateCraftDevice(rowIndex, "address", value); }));
            }
        };
        render.run();
        add.setOnClickListener(v -> {
            JSONArray current = CraftServerConnectionStore.devices(projectExtraFields);
            if (current.length() >= 16) return;
            try {
                current.put(new JSONObject().put("name", "").put("address", 1).put("serverIndex", -1));
                projectExtraFields.put(CraftServerConnectionStore.DEVICES_KEY, current); render.run();
            } catch (Exception ignored) { Toast.makeText(this, "Не удалось добавить устройство", Toast.LENGTH_SHORT).show(); }
        });
        remove.setOnClickListener(v -> {
            JSONArray current = CraftServerConnectionStore.devices(projectExtraFields);
            if (current.length() <= 1) return;
            JSONArray trimmed = new JSONArray();
            for (int i = 0; i < current.length() - 1; i++) trimmed.put(current.opt(i));
            try { projectExtraFields.put(CraftServerConnectionStore.DEVICES_KEY, trimmed); render.run(); }
            catch (Exception ignored) { Toast.makeText(this, "Не удалось изменить число устройств", Toast.LENGTH_SHORT).show(); }
        });
        setContentView(page);
    }

    private interface TextValueChanged { void changed(String value); }
    private TextView networkColumnHeading(String text) {
        TextView heading = new TextView(this); heading.setText(text); heading.setTextColor(Color.rgb(237, 176, 59)); heading.setGravity(Gravity.CENTER); heading.setTextSize(13); return heading;
    }
    private android.text.TextWatcher simpleTextWatcher(TextValueChanged changed) {
        return new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changed.changed(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        };
    }

    private void updateCraftDevice(int index, String key, String value) {
        JSONArray devices = CraftServerConnectionStore.devices(projectExtraFields);
        JSONObject device = devices.optJSONObject(index);
        if (device == null) return;
        try {
            if ("address".equals(key)) {
                if (value.trim().isEmpty()) return;
                int address = Integer.parseInt(value.trim());
                if (address < 0 || address > 254) return;
                device.put(key, address);
            } else device.put(key, value);
            projectExtraFields.put(CraftServerConnectionStore.DEVICES_KEY, devices);
        } catch (Exception ignored) {}
    }

    private void saveCraftNetworkDevices() {
        if (!persistProjectNow(true)) Toast.makeText(this, "Не удалось сохранить сетевые устройства", Toast.LENGTH_LONG).show();
    }

    private LinearLayout craftNetworkPage(String title, Runnable back) {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(Color.rgb(48, 48, 48));
        androidx.appcompat.widget.Toolbar toolbar = new androidx.appcompat.widget.Toolbar(
                new android.view.ContextThemeWrapper(this, R.style.CraftToolBarStyle));
        toolbar.setBackgroundColor(Color.rgb(35, 35, 35)); toolbar.setElevation(dp(8));
        toolbar.setTitle(R.string.craft_title); toolbar.setSubtitle(title); toolbar.setTitleTextColor(Color.rgb(237, 176, 59));
        toolbar.setSubtitleTextColor(Color.rgb(187, 187, 187));
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationContentDescription("Назад"); toolbar.setNavigationOnClickListener(v -> back.run());
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL); actions.setTag("craftNetworkActions");
        androidx.appcompat.widget.Toolbar.LayoutParams actionParams = new androidx.appcompat.widget.Toolbar.LayoutParams(-2, -1, Gravity.END | Gravity.CENTER_VERTICAL);
        toolbar.addView(actions, actionParams);
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, -2)); return page;
    }

    private ImageView craftSourceImage(String resource, String description) {
        ImageView image = new ImageView(this);
        int id = getResources().getIdentifier(resource, "drawable", getPackageName());
        if (id != 0) image.setImageResource(id);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(description);
        image.setClickable(true);
        return image;
    }

    private void showCraftServerList(boolean returnsToDevices, int deviceSlot) {
        craftNetworkScreen = "servers"; craftServerListReturnsToDevices = returnsToDevices;
        LinearLayout page = craftNetworkPage("Редактор серверов", () -> leaveCraftServerList());
        LinearLayout actions = (LinearLayout) page.findViewWithTag("craftNetworkActions");
        TextView none = new TextView(this); none.setText(R.string.craft_no_connections); none.setTextColor(Color.rgb(187, 187, 187)); none.setGravity(Gravity.CENTER); none.setTextSize(14);
        none.setOnClickListener(v -> { if (deviceSlot >= 0) assignCraftServer(deviceSlot, -1); leaveCraftServerList(); });
        ImageView add = craftSourceImage("kc_add3", "Новое подключение");
        LinearLayout.LayoutParams noneParams = new LinearLayout.LayoutParams(-2, -2); noneParams.rightMargin = dp(10);
        actions.addView(none, noneParams); actions.addView(add, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this); scroll.addView(rows); page.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        JSONArray connections = CraftServerConnectionStore.connections(projectExtraFields);
        for (int i = 0; i < connections.length(); i++) {
            JSONObject connection = connections.optJSONObject(i); if (connection == null) continue;
            LinearLayout row = (LinearLayout) getLayoutInflater().inflate(R.layout.craft_server_row, rows, false);
            ImageView logo = row.findViewById(R.id.craft_logo);
            TextView name = row.findViewById(R.id.craft_name), pause = row.findViewById(R.id.craft_pause), details = row.findViewById(R.id.craft_server);
            TextView port = row.findViewById(R.id.craft_port);
            int icon = getResources().getIdentifier(serverIconResource(connection), "drawable", getPackageName());
            if (icon != 0) logo.setImageResource(icon);
            logo.setContentDescription("Тип соединения");
            int connectionType = connection.optInt("type", 0);
            String sourceRowName = connectionType == 2 ? connection.optString("endpoint", "")
                    : connection.optString("name", CraftServerConnectionStore.typeName(connectionType));
            String sourceRowDetail = connectionType == 2 ? connection.optString("port", "502")
                    : CraftServerConnectionStore.displayEndpoint(connection);
            name.setText(sourceRowName); pause.setText(getString(R.string.craft_pause) + "(" + connection.optInt("pause", 0) + ")");
            details.setText(sourceRowDetail); port.setText("");
            final int index = i;
            row.setOnClickListener(v -> {
                if (deviceSlot < 0) showCraftConnectionEditor(index, deviceSlot);
                else { assignCraftServer(deviceSlot, index); leaveCraftServerList(); }
            });
            row.setOnLongClickListener(v -> {
                android.widget.PopupMenu menu = new android.widget.PopupMenu(this, row);
                menu.inflate(R.menu.craft_popup_server_list);
                menu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.craft_delete_item) deleteCraftConnection(index, deviceSlot);
                    else if (item.getItemId() == R.id.craft_edit_item) showCraftConnectionEditor(index, deviceSlot);
                    return true;
                }); menu.show(); return true;
            });
            rows.addView(row);
        }
        add.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(R.string.craft_new_connection)
                .setItems(getResources().getStringArray(R.array.craft_new_connection_types), (dialog, which) -> {
                    int type;
                    try { type = CraftServerConnectionStore.typeAtOriginalChooserPosition(which); }
                    catch (IllegalArgumentException invalid) { return; }
                    if (type == 1) { showCraftBluetoothPicker(returnsToDevices, deviceSlot); return; }
                    if (type == 3) for (int i = 0; i < connections.length(); i++) if (connections.optJSONObject(i) != null && connections.optJSONObject(i).optInt("type") == 3) {
                        Toast.makeText(this, "В Craft допускается одна USB-запись", Toast.LENGTH_LONG).show(); return;
                    }
                    try { connections.put(CraftServerConnectionStore.newConnection(type)); projectExtraFields.put(CraftServerConnectionStore.CONNECTIONS_KEY, connections); showCraftServerList(returnsToDevices, deviceSlot); showCraftConnectionEditor(connections.length() - 1, deviceSlot, true); }
                    catch (Exception failure) { Toast.makeText(this, "Не удалось создать подключение", Toast.LENGTH_SHORT).show(); }
                }).show());
        setContentView(page);
    }

    private String serverIconResource(JSONObject connection) {
        int type = connection.optInt("type", 0);
        if (type == 1) return "kc_bluetooth";
        if (type == 2) return connection.optBoolean("rtu", false) ? "kc_wifi_rtu" : "kc_wifi";
        if (type == 3) return "kc_usb";
        if (type == 4) return connection.optBoolean("rtu", false) ? "kc_cloud_rtu" : "kc_cloud";
        return "kc_disable";
    }

    private void showCraftBluetoothPicker(boolean returnsToDevices, int deviceSlot) {
        if (android.os.Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingCraftBluetoothReturnsToDevices = returnsToDevices; pendingCraftBluetoothSlot = deviceSlot;
            requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CRAFT_BLUETOOTH_PERMISSION);
            return;
        }
        pendingCraftBluetoothSlot = deviceSlot; pendingCraftBluetoothReturnsToDevices = returnsToDevices;
        craftNetworkScreen = "bluetooth"; craftServerListReturnsToDevices = returnsToDevices;
        LinearLayout page = craftNetworkPage("Bluetooth", () -> showCraftServerList(returnsToDevices, deviceSlot));
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this); scroll.addView(rows); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        try {
            android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) throw new IllegalStateException("Bluetooth не поддерживается этим устройством");
            java.util.Set<android.bluetooth.BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) throw new IllegalStateException("Нет сопряжённых Bluetooth-устройств");
            for (android.bluetooth.BluetoothDevice device : bonded) {
                String deviceName = device.getName(); String address = device.getAddress();
                LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(18), dp(12), dp(12), dp(12));
                TextView title = new TextView(this); title.setText(deviceName == null || deviceName.trim().isEmpty() ? "Bluetooth" : deviceName); title.setTextColor(Color.WHITE); title.setTextSize(16);
                TextView mac = new TextView(this); mac.setText(address); mac.setTextColor(Color.LTGRAY); row.addView(title); row.addView(mac);
                row.setBackgroundResource(android.R.drawable.list_selector_background);
                row.setOnClickListener(v -> {
                    JSONArray list = CraftServerConnectionStore.connections(projectExtraFields);
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject existing = list.optJSONObject(i);
                        if (existing != null && existing.optInt("type") == 1 && address.equalsIgnoreCase(existing.optString("endpoint", ""))) {
                            Toast.makeText(this, "Это Bluetooth-устройство уже добавлено", Toast.LENGTH_LONG).show(); return;
                        }
                    }
                    try {
                        JSONObject connection = CraftServerConnectionStore.newConnection(1)
                                .put("name", title.getText().toString()).put("endpoint", address);
                        list.put(connection); projectExtraFields.put(CraftServerConnectionStore.CONNECTIONS_KEY, list);
                        if (!persistProjectNow(true)) { Toast.makeText(this, "Не удалось сохранить устройство", Toast.LENGTH_LONG).show(); return; }
                        showCraftServerList(returnsToDevices, deviceSlot);
                    } catch (Exception failure) { Toast.makeText(this, "Не удалось сохранить устройство", Toast.LENGTH_LONG).show(); }
                });
                rows.addView(row); View divider = new View(this); divider.setBackgroundColor(Color.rgb(76, 76, 76)); rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        } catch (Exception unavailable) {
            TextView message = new TextView(this); message.setText(unavailable.getMessage() == null ? "Список Bluetooth-устройств недоступен" : unavailable.getMessage());
            message.setTextColor(Color.LTGRAY); message.setPadding(dp(18), dp(18), dp(18), dp(18)); rows.addView(message);
        }
        setContentView(page);
    }

    private void leaveCraftServerList() {
        saveCraftNetworkDevices();
        if (craftServerListReturnsToDevices) showCraftNetworkDevices();
        else { craftNetworkScreen = ""; showEditor(); }
    }

    private void assignCraftServer(int slot, int serverIndex) {
        JSONArray devices = CraftServerConnectionStore.devices(projectExtraFields); JSONObject device = devices.optJSONObject(slot);
        if (device != null) try { device.put("serverIndex", serverIndex).put("craftServerSelectionTouched", true); projectExtraFields.put(CraftServerConnectionStore.DEVICES_KEY, devices); } catch (Exception ignored) {}
    }

    private void deleteCraftConnection(int index, int deviceSlot) {
        JSONArray original = CraftServerConnectionStore.connections(projectExtraFields); JSONArray kept = new JSONArray();
        for (int i = 0; i < original.length(); i++) if (i != index) kept.put(original.opt(i));
        try { projectExtraFields.put(CraftServerConnectionStore.CONNECTIONS_KEY, kept); } catch (Exception ignored) {}
        JSONArray devices = CraftServerConnectionStore.devices(projectExtraFields);
        for (int i = 0; i < devices.length(); i++) { JSONObject d = devices.optJSONObject(i); if (d == null) continue; int n = d.optInt("serverIndex", -1); if (n == index) assignCraftServer(i, -1); else if (n > index) assignCraftServer(i, n - 1); }
        persistProjectNow(true); showCraftServerList(craftServerListReturnsToDevices, deviceSlot);
    }

    private void showCraftConnectionEditor(int index, int deviceSlot) {
        showCraftConnectionEditor(index, deviceSlot, false);
    }

    private void showCraftConnectionEditor(int index, int deviceSlot, boolean newConnection) {
        JSONArray connections = CraftServerConnectionStore.connections(projectExtraFields); JSONObject original = connections.optJSONObject(index);
        if (original == null) return;
        final int type = original.optInt("type", 0);
        View form;
        EditText endpoint = null, port = null, pause, name = null;
        final Spinner[] serialSettings = new Spinner[4];
        if (type == 2) {
            form = getLayoutInflater().inflate(R.layout.craft_dialog_add_wifi, null);
            endpoint = form.findViewById(R.id.craft_ip); port = form.findViewById(R.id.craft_port);
            pause = form.findViewById(R.id.craft_pause); CheckBox rtu = form.findViewById(R.id.craft_checkBoxRtu);
            endpoint.setContentDescription("IP адрес подключения"); endpoint.setText(original.optString("endpoint", ""));
            port.setContentDescription("Порт подключения"); port.setText(original.optString("port", "502"));
            pause.setContentDescription("Пауза"); pause.setText(Integer.toString(original.optInt("pause", 0)));
            rtu.setChecked(original.optBoolean("rtu", false));
        } else if (type == 1) {
            LinearLayout bluetoothForm = new LinearLayout(this); bluetoothForm.setOrientation(LinearLayout.VERTICAL); bluetoothForm.setPadding(dp(20), dp(8), dp(20), dp(4)); form = bluetoothForm;
            name = new EditText(this); name.setHint("Имя устройства"); name.setText(original.optString("name", "Bluetooth")); bluetoothForm.addView(name);
            endpoint = new EditText(this); endpoint.setHint("Bluetooth MAC-адрес"); endpoint.setInputType(InputType.TYPE_CLASS_TEXT); endpoint.setText(original.optString("endpoint", "")); bluetoothForm.addView(endpoint);
            pause = new EditText(this); pause.setHint("0"); pause.setInputType(InputType.TYPE_CLASS_NUMBER); pause.setText(Integer.toString(original.optInt("pause", 0))); bluetoothForm.addView(pause);
            Button paired = new Button(this); paired.setText("Выбрать сопряжённое устройство"); EditText addressField = endpoint, nameField = name;
            paired.setOnClickListener(v -> showCraftBondedBluetoothDevices(nameField, addressField)); bluetoothForm.addView(paired);
        } else if (type == 3) {
            form = getLayoutInflater().inflate(R.layout.craft_dialog_add_usb, null);
            serialSettings[0] = form.findViewById(R.id.craft_spinnerBaudRate);
            serialSettings[1] = form.findViewById(R.id.craft_spinnerDataBits);
            serialSettings[2] = form.findViewById(R.id.craft_spinnerStopBits);
            serialSettings[3] = form.findViewById(R.id.craft_spinnerParity);
            pause = form.findViewById(R.id.craft_pause); pause.setContentDescription("Пауза"); pause.setText(Integer.toString(original.optInt("pause", 0)));
            serialSettings[0].setSelection(selectionOf(serialSettings[0], Integer.toString(original.optInt("baud", 9600))));
            serialSettings[1].setSelection(selectionOf(serialSettings[1], Integer.toString(original.optInt("dataBits", 8))));
            int stop = original.optInt("stopBits", 2); serialSettings[2].setSelection(selectionOf(serialSettings[2], stop == 3 ? "1.5" : Integer.toString(stop)));
            int parity = original.optInt("parity", 0); serialSettings[3].setSelection(Math.max(0, Math.min(parity, serialSettings[3].getCount() - 1)));
        } else if (type == 4) {
            form = getLayoutInflater().inflate(R.layout.craft_dialog_add_cloud, null);
            endpoint = form.findViewById(R.id.craft_identifier); pause = form.findViewById(R.id.craft_pause);
            CheckBox rtu = form.findViewById(R.id.craft_checkBoxRtu); endpoint.setText(original.optString("endpoint", ""));
            pause.setContentDescription("Пауза"); pause.setText(Integer.toString(original.optInt("pause", 0))); rtu.setChecked(original.optBoolean("rtu", false));
        } else {
            return;
        }
        final EditText endpointInput = endpoint, portInput = port, pauseInput = pause, nameInput = name;
        final Spinner[] serialInputs = serialSettings;
        final CheckBox rtuInput = type == 2 ? form.findViewById(R.id.craft_checkBoxRtu)
                : type == 4 ? form.findViewById(R.id.craft_checkBoxRtu) : null;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(form).setNegativeButton("Отмена", null).setPositiveButton(newConnection ? "Создать" : "Ок", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String ep = endpointInput == null ? "" : endpointInput.getText().toString().trim();
                String portText = portInput == null ? "" : portInput.getText().toString().trim();
                int wait = Integer.parseInt(pauseInput.getText().toString().trim());
                wait = Math.min(1000, Math.max(0, wait));
                if (type == 2) { int p = Integer.parseInt(portText); if (ep.isEmpty() || p < 1 || p >= 65535) throw new IllegalArgumentException(); }
                if (type == 4 && ep.isEmpty()) throw new IllegalArgumentException();
                String displayName = type == 2 ? ep : type == 1
                        ? (nameInput.getText().toString().trim().isEmpty() ? "Bluetooth" : nameInput.getText().toString().trim())
                        : type == 3 ? "Usb" : "KaScada Cloud";
                original.put("name", displayName);
                if (type != 3) original.put("endpoint", ep);
                original.put("pause", wait);
                if (rtuInput != null) original.put("rtu", rtuInput.isChecked());
                if (type == 2) original.put("port", portText);
                if (type == 3) {
                    original.put("baud", Integer.parseInt(serialInputs[0].getSelectedItem().toString()));
                    original.put("dataBits", Integer.parseInt(serialInputs[1].getSelectedItem().toString()));
                    String stop = serialInputs[2].getSelectedItem().toString();
                    original.put("stopBits", "1.5".equals(stop) ? 3 : Integer.parseInt(stop));
                    original.put("parity", serialInputs[3].getSelectedItemPosition());
                }
                projectExtraFields.put(CraftServerConnectionStore.CONNECTIONS_KEY, connections);
                if (persistProjectNow(true)) { dialog.dismiss(); showCraftServerList(craftServerListReturnsToDevices, deviceSlot); }
                else Toast.makeText(this, "Не удалось сохранить подключение", Toast.LENGTH_LONG).show();
            } catch (Exception invalid) { Toast.makeText(this, type == 2 ? "Проверь IP, порт и паузу" : "Проверь параметры подключения", Toast.LENGTH_LONG).show(); }
        }));
        dialog.show();
    }

    private int selectionOf(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++)
            if (value.equals(spinner.getItemAtPosition(i).toString())) return i;
        return 0;
    }

    private void showCraftBondedBluetoothDevices(EditText name, EditText address) {
        try {
            android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) { Toast.makeText(this, "Bluetooth не поддерживается этим устройством", Toast.LENGTH_LONG).show(); return; }
            java.util.Set<android.bluetooth.BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) { Toast.makeText(this, "Нет сопряжённых Bluetooth-устройств", Toast.LENGTH_LONG).show(); return; }
            java.util.ArrayList<android.bluetooth.BluetoothDevice> devices = new java.util.ArrayList<>(bonded);
            String[] labels = new String[devices.size()];
            for (int i = 0; i < devices.size(); i++) {
                android.bluetooth.BluetoothDevice device = devices.get(i);
                String deviceName = device.getName();
                labels[i] = (deviceName == null || deviceName.trim().isEmpty() ? "Bluetooth" : deviceName)
                        + "\n" + device.getAddress();
            }
            new AlertDialog.Builder(this).setTitle("Сопряжённые устройства")
                    .setItems(labels, (list, which) -> {
                        android.bluetooth.BluetoothDevice device = devices.get(which);
                        try { name.setText(device.getName()); address.setText(device.getAddress()); }
                        catch (SecurityException denied) { Toast.makeText(this, "Нет разрешения на Bluetooth", Toast.LENGTH_LONG).show(); }
                    }).show();
        } catch (SecurityException denied) {
            Toast.makeText(this, "Нет разрешения на список Bluetooth-устройств", Toast.LENGTH_LONG).show();
        }
    }

    private void showEditorAdvancedCommands(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        if (nestedMacroEdit != null) {
            menu.getMenu().add("Сохранить вложенный граф").setOnMenuItemClickListener(item -> {
                if (persistProjectNow(true))
                    Toast.makeText(this, "Вложенный граф сохранён", Toast.LENGTH_SHORT).show();
                return true;
            });
            menu.getMenu().add("Вернуться на экран").setOnMenuItemClickListener(item -> {
                closeNestedMacroGraphEditor(); return true;
            });
            menu.show();
            return;
        }
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
        menu.getMenu().add("Telegram — настройки и тест").setOnMenuItemClickListener(item -> {
            showTelegramSettingsDialog(); return true;
        });
        menu.getMenu().add("Умный дом — Home Assistant / Xiaomi").setOnMenuItemClickListener(item -> {
            showHomeAssistantDialog(); return true;
        });
        menu.getMenu().add("Умный дом — Яндекс").setOnMenuItemClickListener(item -> { showYandexSmartHomeDialog(); return true; });
        menu.getMenu().add("Голосовой помощник").setOnMenuItemClickListener(item -> { showVoiceModeDialog(); return true; });
        menu.getMenu().add("Modbus").setOnMenuItemClickListener(item -> { showModbusDiagnostics(); return true; });
        menu.getMenu().add("Главное меню").setOnMenuItemClickListener(item -> {
            if (persistProjectNow(true)) showHome(); return true;
        });
        menu.show();
    }

    private void showYandexSmartHomeDialog() {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(8),dp(20),dp(4));
        TextView note=new TextView(this);
        note.setText("Создай приложение доступа к API в Яндекс OAuth (https://oauth.yandex.ru/client/new/) с правами iot:view и iot:control, затем вставь OAuth-токен. Список устройств доступен для просмотра; запуск сценария всегда отдельно подтверждается. Токен шифруется Android Keystore и не попадает в проект.");
        note.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);form.addView(note);
        EditText token=propertyField("",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        token.setSingleLine(true);token.setHint("OAuth-токен (пусто, если уже сохранён)");form.addView(token);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Яндекс Умный дом")
                .setView(form).setPositiveButton("Подключить",null).setNegativeButton("Отключить",null).setNeutralButton("Закрыть",null).create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                final String entered=token.getText().toString().trim();String access=entered;
                if(access.isEmpty())try{access=YandexTokenStore.read(this);}catch(Exception failure){Toast.makeText(this,"Не удалось прочитать токен; введи его снова",Toast.LENGTH_LONG).show();return;}
                if(access.isEmpty()){Toast.makeText(this,"Нужен OAuth-токен Яндекс Умного дома",Toast.LENGTH_LONG).show();return;}
                final String finalToken=access;dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                yandexExecutor.execute(()->{
                    List<YandexSmartHomeClient.Item> items=null;String error=null;
                    try{items=YandexSmartHomeClient.getHome(finalToken);}catch(Exception failure){error=failure.getMessage();}
                    final List<YandexSmartHomeClient.Item> result=items;final String problem=error;
                    runOnUiThread(()->{
                        if(isFinishing()||isDestroyed())return;
                        if(dialog.isShowing())dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        if(problem!=null){Toast.makeText(this,"Яндекс: "+(problem.contains("HTTP 401")?"токен отклонён (401)":"не удалось загрузить устройства; проверь токен и интернет"),Toast.LENGTH_LONG).show();return;}
                        try{if(!entered.isEmpty())YandexTokenStore.save(this,entered);}catch(Exception failure){Toast.makeText(this,"Токен не удалось сохранить безопасно",Toast.LENGTH_LONG).show();}
                        dialog.dismiss();showYandexSmartHomeItems(result,finalToken);
                    });
                });
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("Отключить Яндекс?").setMessage("Удалить сохранённый токен?")
                    .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{YandexTokenStore.clear(this);dialog.dismiss();Toast.makeText(this,"Яндекс отключён",Toast.LENGTH_SHORT).show();}).show());
        });
        dialog.show();
    }

    private void showYandexSmartHomeItems(List<YandexSmartHomeClient.Item> items,String token) {
        if(items==null||items.isEmpty()){new AlertDialog.Builder(this).setTitle("Яндекс Умный дом").setMessage("Устройства и сценарии не найдены.").setPositiveButton("ОК",null).show();return;}
        ArrayList<YandexSmartHomeClient.Item> shown=new ArrayList<>(items.subList(0,Math.min(300,items.size())));        android.widget.ListView list=new android.widget.ListView(this);list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,shown));
        AlertDialog homeDialog=new AlertDialog.Builder(this).setTitle("Яндекс: устройства и сценарии").setView(list).setPositiveButton("Готово",null).create();
        list.setOnItemClickListener((parent,view,position,id)->{
            YandexSmartHomeClient.Item selected=shown.get(position);
            if(!selected.scenario){Toast.makeText(this,"Устройство доступно через его capabilities; прямое управление пока не включено",Toast.LENGTH_SHORT).show();return;}
            new AlertDialog.Builder(this).setTitle("Запустить сценарий?").setMessage("Запустить «"+selected.name+"»?")
                    .setNegativeButton("Отмена",null).setPositiveButton("Запустить",(d,w)->{
                        yandexExecutor.execute(()->{String error=null;try{YandexSmartHomeClient.launchScenario(token,selected.id);}catch(Exception failure){error=failure.getMessage();}
                            final String problem=error;runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(problem!=null)Toast.makeText(this,"Яндекс: сценарий не запущен",Toast.LENGTH_LONG).show();else{homeDialog.dismiss();Toast.makeText(this,"Сценарий запущен",Toast.LENGTH_SHORT).show();}});});
                    }).show();
        });
        homeDialog.show();
    }

    private void sendSmsNotification(Widget widget,String value) {
        if (!operatorMode || !activityResumed) return;
        String phone = "";
        try { phone = SmsRecipientStore.read(this, widget.extraFields.optString("smsRecipientId", "")); }
        catch (Exception ignored) {}
        if (!SmsNotificationClient.isValidPhone(phone)) {
            Toast.makeText(this, "У SMS-элемента не настроен номер получателя", Toast.LENGTH_LONG).show(); return;
        }
        if(!getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY)) {
            Toast.makeText(this,"Это устройство не поддерживает SMS через SIM",Toast.LENGTH_LONG).show();return;
        }
        if(checkSelfPermission(android.Manifest.permission.SEND_SMS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.SEND_SMS},REQUEST_SMS_SEND_PERMISSION);return;
        }
        boolean craftSms = widget.extraFields.optInt("craftComponentId", -1) == 10;
        boolean optionMode = craftSms && widget.extraFields.optBoolean("craftSmsOption", false);
        int count = optionMode ? widget.extraFields.optInt("craftSmsOptionCount", 1)
                : widget.extraFields.optInt("smsRegisterCount", 1);
        if (count < 1 || count > 8) { Toast.makeText(this, "Количество регистров SMS должно быть 1–8", Toast.LENGTH_LONG).show(); return; }
        String registerBinding = widget.connectionInfo;
        if (optionMode) {
            BindingRequest trigger = parseBinding(-1, widget.connectionInfo);
            int optionRegister = widget.extraFields.optInt("craftSmsOptionRegister", -1);
            if (trigger == null || optionRegister < 0 || optionRegister > 65535) {
                Toast.makeText(this, "Проверь TCP-привязку и адрес SMS-регистра", Toast.LENGTH_LONG).show(); return;
            }
            registerBinding = "tcp://" + trigger.host + ":" + trigger.port + "/" + trigger.unit + "/" + optionRegister;
        }
        BindingRequest source = parseBinding(-1, registerBinding, optionMode ? count : 1);
        if (source == null || (source.functionCode != 3 && source.functionCode != 4)) {
            Toast.makeText(this, "Укажи корректный адрес регистра для SMS", Toast.LENGTH_LONG).show(); return;
        }
        final String destination = phone, template = widget.text, triggerValue = value;
        final String host = source.host;
        final int port = source.port, unit = source.unit, address = source.address;
        final int functionCode = source.functionCode;
        final long sessionToken = pollSession.token();
        final boolean readOptionRegisters = optionMode;
        smsExecutor.execute(() -> {
            String message;
            boolean readFailed = false;
            if (!readOptionRegisters) {
                message = SmsNotificationClient.message(template, triggerValue);
            } else {
                try {
                    int[] registers = new ModbusTcpClient(host, port).read(unit, address, count, functionCode);
                    String[] values = new String[registers.length];
                    for (int i = 0; i < registers.length; i++) values[i] = Integer.toString(registers[i]);
                    message = SmsOutgoingTemplate.render(template, values, triggerValue);
                } catch (Exception failure) {
                    message = SmsNotificationClient.message(template, triggerValue) + " !NO RESPONSE!";
                    readFailed = true;
                }
            }
            if (!pollSession.accepts(sessionToken) || !activityResumed || !operatorMode) return;
            boolean sent = false;
            try { SmsNotificationClient.send(this, destination, message); sent = true; }
            catch (Exception ignored) {}
            final boolean success = sent, noResponse = readFailed;
            runOnUiThread(() -> {
                if (!pollSession.accepts(sessionToken) || !activityResumed || !operatorMode) return;
                Toast.makeText(this, success ? (noResponse ? "SMS отправлено: устройство не ответило"
                        : "SMS отправлено через SIM") : "Не удалось отправить SMS через SIM", Toast.LENGTH_LONG).show();
            });
        });
    }

    private boolean hasConfiguredIncomingSms() {
        if (!operatorMode || editor == null) return false;
        for (Widget widget : widgets) {
            if ("Входящее SMS".equals(widget.type) && widget.extraFields.optBoolean("smsEnabled", false))
                return true;
        }
        return false;
    }

    private void refreshIncomingSmsReceiver() {
        if (!activityResumed || !hasConfiguredIncomingSms()
                || !getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY)) {
            unregisterIncomingSmsReceiver();
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            unregisterIncomingSmsReceiver();
            if (!smsReceivePermissionRequested) {
                smsReceivePermissionRequested = true;
                requestPermissions(new String[]{android.Manifest.permission.RECEIVE_SMS}, REQUEST_SMS_RECEIVE_PERMISSION);
            }
            return;
        }
        if (incomingSmsReceiver != null) return;
        incomingSmsReceiver = new SmsIncomingBroadcastReceiver(this::handleIncomingSms);
        android.content.IntentFilter filter = new android.content.IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(incomingSmsReceiver, filter, android.content.Context.RECEIVER_EXPORTED);
        else registerReceiver(incomingSmsReceiver, filter);
    }

    private void unregisterIncomingSmsReceiver() {
        if (incomingSmsReceiver == null) return;
        try { unregisterReceiver(incomingSmsReceiver); } catch (IllegalArgumentException ignored) {}
        incomingSmsReceiver = null;
    }

    private void handleIncomingSms(String sender, String body) {
        if (!activityResumed || !operatorMode || editor == null || !hasConfiguredIncomingSms()) return;
        final long sessionToken = pollSession.token();
        for (Widget widget : new ArrayList<>(widgets)) {
            if (!"Входящее SMS".equals(widget.type) || !widget.extraFields.optBoolean("smsEnabled", false)) continue;
            final String allowedSender;
            try { allowedSender = SmsRecipientStore.read(this, widget.extraFields.optString("smsSenderId", "")); }
            catch (Exception invalid) { continue; }
            String modeValue = widget.extraFields.optString("smsMode", "number");
            SmsIncomingCommand.Mode mode = "bit".equals(modeValue)
                    ? SmsIncomingCommand.Mode.SET_BIT : SmsIncomingCommand.Mode.REGISTER_NUMBER;
            int bit = widget.extraFields.optInt("smsBit", 0);
            SmsIncomingCommand.Result parsed = SmsIncomingCommand.parse(allowedSender, sender,
                    widget.extraFields.optString("smsCommand", ""), body, mode, bit);
            if (parsed == null) continue;
            BindingRequest binding = parseBinding(-1, widget.connectionInfo);
            if (binding == null || binding.functionCode != 3) continue;
            final String host = binding.host;
            final int port = binding.port, unit = binding.unit, address = binding.address;
            final int[] values = parsed.registerValues.clone();
            final boolean setStrobe = mode == SmsIncomingCommand.Mode.REGISTER_NUMBER
                    && widget.extraFields.optBoolean("smsStrobeEnabled", false);
            final BindingRequest strobeBinding = setStrobe
                    ? parseBinding(-1, "tcp://" + host + ":" + port + "/" + unit + "/"
                            + widget.extraFields.optInt("smsStrobeRegister", -1)) : null;
            final int strobeBit = widget.extraFields.optInt("smsStrobeBit", 0);
            if (setStrobe && (strobeBinding == null || strobeBinding.functionCode != 3
                    || strobeBinding.unit != unit || strobeBit < 0 || strobeBit > 15)) continue;
            modbusExecutor.execute(() -> {
                if (!pollSession.accepts(sessionToken) || !activityResumed || !operatorMode) return;
                Exception failure = null;
                try {
                    ModbusTcpClient client = new ModbusTcpClient(host, port);
                    if (parsed.mode == SmsIncomingCommand.Mode.SET_BIT) {
                        client.writeMask(unit, address, parsed.writeAndMask, parsed.writeOrMask);
                    } else {
                        if (values.length == 1) client.writeSingle(unit, address, values[0]);
                        else client.writeMultiple(unit, address, values);
                        if (setStrobe) {
                            int mask = 1 << strobeBit;
                            client.writeMask(unit, strobeBinding.address, 0xffff ^ mask, mask);
                        }
                    }
                } catch (Exception error) { failure = error; }
                final Exception result = failure;
                runOnUiThread(() -> {
                    if (!pollSession.accepts(sessionToken) || !activityResumed || !operatorMode) return;
                    Toast.makeText(this, result == null ? "SMS-команда выполнена: Modbus обновлён"
                            : "SMS-команда проверена, но запись Modbus не выполнена", Toast.LENGTH_LONG).show();
                });
            });
        }
    }

    private void showHomeAssistantDialog() {
        android.content.SharedPreferences prefs = getSharedPreferences("bloodsky_homeassistant", MODE_PRIVATE);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));
        TextView note = new TextView(this);
        note.setText("Локальный Home Assistant может дать доступ к Xiaomi Home и другим устройствам. В Home Assistant создай access token в профиле. Токен будет храниться зашифрованно в Android Keystore и не попадёт в проект. Сейчас подключение только читает список устройств и их состояние. HTTP используй только в доверенной домашней сети; для внешних адресов нужен HTTPS.");
        form.addView(note);
        EditText address = propertyField(prefs.getString("base_url", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        address.setSingleLine(true); address.setHint("http://homeassistant.local:8123"); form.addView(address);
        EditText token = propertyField("", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        token.setSingleLine(true); token.setHint("Access token (оставь пустым, если сохранён)"); form.addView(token);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Home Assistant / Xiaomi")
                .setView(form).setPositiveButton("Найти устройства", null)
                .setNegativeButton("Отключить", null).setNeutralButton("Закрыть", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                final String baseUrl = address.getText().toString().trim();
                final String enteredToken = token.getText().toString().trim();
                String accessToken = enteredToken;
                if (accessToken.isEmpty()) try { accessToken = HomeAssistantTokenStore.read(this); }
                catch (Exception failure) { Toast.makeText(this, "Не удалось прочитать сохранённый токен; введи его заново", Toast.LENGTH_LONG).show(); return; }
                if (accessToken.isEmpty()) { Toast.makeText(this, "Нужен access token Home Assistant", Toast.LENGTH_LONG).show(); return; }
                final String finalToken = accessToken;
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                homeAssistantExecutor.execute(() -> {
                    List<HomeAssistantClient.Entity> entities = null; String error = null;
                    try { entities = HomeAssistantClient.getEntities(baseUrl, finalToken); }
                    catch (Exception failure) { error = failure.getMessage(); }
                    final List<HomeAssistantClient.Entity> found = entities; final String failureText = error;
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        if (failureText != null) {
                            Toast.makeText(this, "Home Assistant: " + safeHomeAssistantError(failureText), Toast.LENGTH_LONG).show(); return;
                        }
                        try {
                            prefs.edit().putString("base_url", HomeAssistantClient.normalizeBaseUrl(baseUrl)).apply();
                            if (!enteredToken.isEmpty()) HomeAssistantTokenStore.save(this, enteredToken);
                        } catch (Exception storageFailure) {
                            Toast.makeText(this, "Устройства найдены, но токен не удалось сохранить безопасно", Toast.LENGTH_LONG).show();
                        }
                        dialog.dismiss(); showHomeAssistantEntities(found);
                    });
                });
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Отключить Home Assistant?")
                    .setMessage("Удалить сохранённый адрес и токен?")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить", (d, w) -> {
                        HomeAssistantTokenStore.clear(this); prefs.edit().clear().apply(); dialog.dismiss();
                        Toast.makeText(this, "Home Assistant отключён", Toast.LENGTH_SHORT).show();
                    }).show());
        });
        dialog.show();
    }

    private String safeHomeAssistantError(String error) {
        if (error == null || error.trim().isEmpty()) return "не удалось подключиться; проверь адрес и сеть";
        if (error.contains("HTTP 401")) return "токен отклонён (HTTP 401)";
        if (error.contains("HTTP 403")) return "нет доступа (HTTP 403)";
        if (error.contains("HTTP 404")) return "не найден API Home Assistant (HTTP 404)";
        if (error.contains("HTTPS")) return "для внешнего адреса используй HTTPS";
        return "не удалось подключиться; проверь адрес, сеть и токен";
    }

    private void showHomeAssistantEntities(List<HomeAssistantClient.Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Устройства Home Assistant")
                    .setMessage("Устройства не найдены или у токена нет доступа к состояниям.")
                    .setPositiveButton("ОК", null).show(); return;
        }
        ArrayList<HomeAssistantClient.Entity> shown = new ArrayList<>(entities.subList(0, Math.min(300, entities.size())));
        android.widget.ListView list = new android.widget.ListView(this);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, shown));
        AlertDialog entityDialog = new AlertDialog.Builder(this).setTitle("Home Assistant: " + entities.size() + " сущностей")
                .setView(list).setPositiveButton("Готово", null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            HomeAssistantClient.Entity selected = shown.get(position);
            if (!selected.canTogglePower()) {
                Toast.makeText(this, "Для этой сущности доступен только просмотр", Toast.LENGTH_SHORT).show(); return;
            }
            boolean turnOn = "off".equalsIgnoreCase(selected.state);
            String action = turnOn ? "включить" : "выключить";
            new AlertDialog.Builder(this).setTitle("Подтвердить действие")
                    .setMessage(action.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + action.substring(1)
                            + " «" + selected.name + "» (" + selected.entityId + ")?")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Подтвердить", (d, w) -> setHomeAssistantPower(selected, turnOn, entityDialog)).show();
        });
        entityDialog.show();
    }

    private void setHomeAssistantPower(HomeAssistantClient.Entity entity, boolean turnOn, AlertDialog entityDialog) {
        String baseUrl = getSharedPreferences("bloodsky_homeassistant", MODE_PRIVATE).getString("base_url", "");
        homeAssistantExecutor.execute(() -> {
            String error = null; List<HomeAssistantClient.Entity> updated = null;
            try {
                String token = HomeAssistantTokenStore.read(this);
                HomeAssistantClient.setPower(baseUrl, token, entity, turnOn);
                updated = HomeAssistantClient.getEntities(baseUrl, token);
            } catch (Exception failure) { error = failure.getMessage(); }
            final String failureText = error; final List<HomeAssistantClient.Entity> states = updated;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (failureText != null) {
                    Toast.makeText(this, "Home Assistant: " + safeHomeAssistantError(failureText), Toast.LENGTH_LONG).show(); return;
                }
                entityDialog.dismiss();
                Toast.makeText(this, "Команда отправлена", Toast.LENGTH_SHORT).show();
                showHomeAssistantEntities(states);
            });
        });
    }

    private void showVoiceModeDialog() {
        new AlertDialog.Builder(this).setTitle("Голосовой помощник")
                .setMessage("Локально: только встроенное распознавание на устройстве, без отправки аудио.\\n\\nЧерез интернет: аудио может обрабатываться выбранной в Android службой распознавания речи.")
                .setPositiveButton("Локально", (dialog, which) -> requestVoicePermission(VOICE_LOCAL))
                .setNegativeButton("Через интернет", (dialog, which) -> requestVoicePermission(VOICE_ONLINE))
                .setNeutralButton("Отмена", null).show();
    }

    private void requestVoicePermission(int mode) {
        pendingVoiceMode = mode;
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_VOICE_PERMISSION);
            return;
        }
        startVoiceRecognition(mode);
    }

    private void startVoiceRecognition(int mode) {
        if (mode == VOICE_LOCAL && (android.os.Build.VERSION.SDK_INT < 31
                || !android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(this))) {
            Toast.makeText(this, "Офлайн-распознавание речи на этом устройстве недоступно. Интернет-режим остаётся отдельным вариантом.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            releaseVoiceRecognizer();
            speechRecognizer = mode == VOICE_LOCAL
                    ? android.speech.SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                    : android.speech.SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(MainActivity.this, "Слушаю…", Toast.LENGTH_SHORT).show(); }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    Toast.makeText(MainActivity.this, "Не удалось распознать речь (код " + error + ")", Toast.LENGTH_LONG).show();
                    releaseVoiceRecognizer();
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> phrases = results == null ? null
                            : results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                    releaseVoiceRecognizer();
                    if (phrases == null || phrases.isEmpty()) { speakVoiceReply("Команда не распознана"); return; }
                    executeVoiceCommand(phrases.get(0));
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
            android.content.Intent intent = new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            if (mode == VOICE_LOCAL && android.os.Build.VERSION.SDK_INT < 33)
                intent.putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            speechRecognizer.startListening(intent);
        } catch (Exception failure) {
            releaseVoiceRecognizer();
            Toast.makeText(this, "Служба распознавания речи недоступна", Toast.LENGTH_LONG).show();
        }
    }

    private void executeVoiceCommand(String phrase) {
        ArrayList<String> names = new ArrayList<>();
        for (Screen screen : screens) names.add(screen.name);
        VoiceCommandParser.Command command = VoiceCommandParser.parse(phrase, names);
        switch (command.type) {
            case NEXT_SCREEN:
                nextScreen(); speakVoiceReply("Открываю следующий экран"); break;
            case PREVIOUS_SCREEN:
                previousScreen(); speakVoiceReply("Открываю предыдущий экран"); break;
            case OPEN_SCREEN:
                activateScreen(command.screenIndex); speakVoiceReply("Экран " + (command.screenIndex + 1)); break;
            case START_PROJECT:
                if (operatorMode) { speakVoiceReply("Проект уже запущен"); break; }
                new AlertDialog.Builder(this).setTitle("Запустить проект?")
                        .setMessage("Голосовая команда: «" + phrase + "». Запуск включает исполнение проекта и его связей.")
                        .setNegativeButton("Отмена", (d, w) -> speakVoiceReply("Запуск отменён"))
                        .setPositiveButton("Запустить", (d, w) -> { toggleOperatorMode(); speakVoiceReply("Проект запущен"); }).show();
                break;
            case STOP_PROJECT:
                if (operatorMode) { toggleOperatorMode(); speakVoiceReply("Проект остановлен"); }
                else speakVoiceReply("Проект уже остановлен");
                break;
            default:
                speakVoiceReply("Эта команда пока не поддерживается");
                Toast.makeText(this, "Распознано: «" + phrase + "». Поддерживаются экраны и запуск/остановка проекта.", Toast.LENGTH_LONG).show();
        }
    }

    private void speakVoiceReply(String text) {
        if (textToSpeech != null) textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bloodsky-voice-reply");
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void releaseVoiceRecognizer() {
        if (speechRecognizer != null) { try { speechRecognizer.cancel(); speechRecognizer.destroy(); } catch (Exception ignored) {} speechRecognizer = null; }
    }

    private void showTelegramSettingsDialog() {
        android.content.SharedPreferences prefs = getSharedPreferences(TELEGRAM_PREFS, MODE_PRIVATE);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));
        TextView note = new TextView(this);
        note.setText("Укажи токен своего Telegram-бота и chat_id. Данные хранятся отдельно от проектов и не попадают в экспорт.");
        form.addView(note);
        EditText tokenField = propertyField(prefs.getString("bot_token", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        tokenField.setSingleLine(true); tokenField.setHint("Токен бота"); form.addView(tokenField);
        EditText chatField = propertyField(prefs.getString("chat_id", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        chatField.setSingleLine(true); chatField.setHint("chat_id или @канал"); form.addView(chatField);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Telegram-уведомления")
                .setView(form)
                .setPositiveButton("Сохранить", null)
                .setNeutralButton("Отправить тест", null)
                .setNegativeButton("Закрыть", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String token = tokenField.getText().toString().trim();
                String chatId = chatField.getText().toString().trim();
                if (token.isEmpty() && chatId.isEmpty()) {
                    prefs.edit().remove("bot_token").remove("chat_id").apply();
                    Toast.makeText(this, "Настройки Telegram удалены", Toast.LENGTH_SHORT).show();
                    dialog.dismiss(); return;
                }
                if (!TelegramBotClient.isValidToken(token) || !TelegramBotClient.isValidChatId(chatId)) {
                    Toast.makeText(this, "Проверь токен бота и chat_id", Toast.LENGTH_LONG).show(); return;
                }
                prefs.edit().putString("bot_token", token).putString("chat_id", chatId).apply();
                rearmTelegramWidgets();
                Toast.makeText(this, "Настройки Telegram сохранены", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String token = tokenField.getText().toString().trim();
                String chatId = chatField.getText().toString().trim();
                if (!TelegramBotClient.isValidToken(token) || !TelegramBotClient.isValidChatId(chatId)) {
                    Toast.makeText(this, "Сначала укажи корректные токен и chat_id", Toast.LENGTH_LONG).show(); return;
                }
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                telegramExecutor.execute(() -> {
                    String error = null;
                    try { TelegramBotClient.sendMessage(token, chatId, "BLOODSKY: тестовое Telegram-уведомление."); }
                    catch (Exception failure) { error = failure.getMessage(); }
                    final String result = error;
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                        Toast.makeText(this, result == null ? "Тестовое уведомление отправлено" : "Telegram: " + result,
                                Toast.LENGTH_LONG).show();
                        if (result == null) {
                            prefs.edit().putString("bot_token", token).putString("chat_id", chatId).apply();
                            rearmTelegramWidgets();
                            if (dialog.isShowing()) dialog.dismiss();
                        }
                    });
                });
            });
        });
        dialog.show();
    }

    private void rearmTelegramWidgets() {
        for (Widget widget : widgets) {
            if ("Telegram-уведомление".equals(widget.type)) widget.lastTelegramState = false;
        }
    }

    private void sendTelegramNotification(int widgetIndex, Widget widget, String value) {
        android.content.SharedPreferences prefs = getSharedPreferences(TELEGRAM_PREFS, MODE_PRIVATE);
        String token = prefs.getString("bot_token", "");
        String chatId = prefs.getString("chat_id", "");
        if (!TelegramBotClient.isValidToken(token) || !TelegramBotClient.isValidChatId(chatId)) {
            liveValues.put(widgetIndex, "Telegram CFG");
            if (editor != null) editor.invalidate();
            return;
        }
        String message = (widget.text == null || widget.text.trim().isEmpty() ? "BLOODSKY: событие" : widget.text.trim())
                + "\nЗначение: " + value;
        final EditorCanvas sessionEditor = editor;
        telegramExecutor.execute(() -> {
            String status = "Telegram отправлено";
            try { TelegramBotClient.sendMessage(token, chatId, message); }
            catch (Exception failure) { status = "Telegram ERR"; }
            final String result = status;
            runOnUiThread(() -> {
                if (widgetIndex < 0 || widgetIndex >= widgets.size() || widgets.get(widgetIndex) != widget
                        || editor != sessionEditor || !activityResumed) return;
                liveValues.put(widgetIndex, result);
                if (editor != null) editor.invalidate();
            });
        });
    }

    @Override public void onBackPressed() {
        if (nestedMacroEdit != null) { closeNestedMacroGraphEditor(); return; }
        if (manualUsbScreenVisible) { showHome(); return; }
        if ("bluetooth".equals(craftNetworkScreen)) { showCraftServerList(craftServerListReturnsToDevices, pendingCraftBluetoothSlot); return; }
        if ("servers".equals(craftNetworkScreen)) { leaveCraftServerList(); return; }
        if ("devices".equals(craftNetworkScreen)) { saveCraftNetworkDevices(); craftNetworkScreen = ""; showEditor(); return; }
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
            copy.dynTextStates[i] = source.dynTextStates[i];            copy.dynTextColors[i] = source.dynTextColors[i];
            copy.bitListLabels[i] = source.bitListLabels[i];
            copy.voiceMessages[i] = source.voiceMessages[i];
            copy.imagePathStates[i] = source.imagePathStates[i];
        }
        copy.soundThreshold = source.soundThreshold;
        copy.soundActive = source.soundActive;
        copy.soundFilePath = source.soundFilePath;
        copy.craftAlarmSoundIndex = source.craftAlarmSoundIndex;
        copy.craftAlarmLoop = source.craftAlarmLoop;
        copy.targetScreenIndex = source.targetScreenIndex;
        copy.targetUrl = source.targetUrl;
        copy.lastVoiceState = source.lastVoiceState;
        copy.lastSoundState = source.lastSoundState;
        copy.lastTelegramState = source.lastTelegramState;
        copy.craftVariableNamespace = source.craftVariableNamespace;
        try { copy.extraFields = new JSONObject(source.extraFields.toString()); } catch (Exception ignored) { copy.extraFields = new JSONObject(); }
        copy.x = source.x; copy.y = source.y; copy.w = source.w; copy.h = source.h;
        return copy;
    }

    private void activateScreen(int index) {
        fbdWireHistory.clear();
        stopCurrentScreenAlarms();
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
        if (nestedMacroEdit != null) return;
        if (screens.size() < 2) { Toast.makeText(this, "Добавь ещё один экран", Toast.LENGTH_SHORT).show(); return; }
        activateScreen((currentScreenIndex - 1 + screens.size()) % screens.size());
    }

    private void nextScreen() {
        if (nestedMacroEdit != null) return;
        if (screens.size() < 2) { Toast.makeText(this, "Добавь ещё один экран", Toast.LENGTH_SHORT).show(); return; }
        activateScreen((currentScreenIndex + 1) % screens.size());
    }

    private void addScreen() {
        if (nestedMacroEdit != null) return;
        fbdWireHistory.clear();
        saveActiveScreen();
        screens.add(new Screen("Экран " + (screens.size() + 1)));
        currentScreenIndex = screens.size() - 1;
        widgets.clear();
        persistProject();
        showEditor();
    }

    private void removeCurrentScreen() {
        if (nestedMacroEdit != null) return;
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
        if (nestedMacroEdit != null) return;
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
                || "FBD TOF".equals(widget.type) || "FBD TP".equals(widget.type)
                || "FBD CRAFT TIMER".equals(widget.type);
        final boolean invertible = !isDynamicMacroWidget(widget)
                && knownFbdPins(widget, true).length == 1
                && "BOOL".equals(knownFbdPinType(widget,
                        knownFbdPins(widget, true)[0], true));
        final java.util.List<String> labels = new ArrayList<>();
        labels.add("Соединить с…");
        if (invertible) labels.add("Инвертировать связь BOOL…");
        if (supportsFbdBlockOutputInversion(widget.type))
            labels.add("Инвертировать выход BOOL");
        labels.add("Отсоединить выход");
        if (nestedMacroEdit != null && knownFbdPins(widget, true).length > 0)
            labels.add("Назначить выход макроса…");
        FbdCraftMacroDefinition nestedDefinition = macroDefinition(widget);
        if (nestedMacroEdit == null && nestedDefinition != null
                && hasCraftMacroGraph(nestedDefinition))
            labels.add("Редактировать вложенный граф");
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
                    if ("Назначить выход макроса…".equals(label)) {
                        chooseNestedMacroOutput(blockIndex, widget);
                    } else if ("Редактировать вложенный граф".equals(label)) {
                        openNestedMacroGraphEditor(blockIndex);
                    } else if ("Соединить с…".equals(label)) {
                        fbdWireMode = true;
                        wireSource = blockIndex;
                        Toast.makeText(this, "Выбери целевой блок FBD", Toast.LENGTH_LONG).show();
                    } else if ("Инвертировать связь BOOL…".equals(label)) {
                        showFbdInversionDialog(blockIndex);
                    } else if ("Инвертировать выход BOOL".equals(label)) {
                        try {
                            Object previous = widget.extraFields.opt("invertOut");
                            boolean existed = widget.extraFields.has("invertOut");
                            if (!toggleFbdBlockOutputInversion(widget)) return;
                            if (!persistProjectNow(true)) {
                                if (existed) widget.extraFields.put("invertOut", previous);
                                else widget.extraFields.remove("invertOut");
                                Toast.makeText(this, "Не удалось сохранить инверсию выхода", Toast.LENGTH_LONG).show();
                            }
                            if (editor != null) editor.invalidate();
                        } catch (Exception error) {
                            Toast.makeText(this, "Не удалось сохранить инверсию выхода", Toast.LENGTH_LONG).show();
                        }
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

    private String archiveProjectKey() {
        String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
        return ProjectMediaService.getOrGenerateProjectId(json);
    }

    private String archiveWidgetKey(Widget widget) throws org.json.JSONException {
        String key = widget.extraFields.optString("craftArchiveKey", "").trim();
        if (key.isEmpty()) {
            key = java.util.UUID.randomUUID().toString();
            widget.extraFields.put("craftArchiveKey", key);
            persistProject();
        }
        return key;
    }

    private void showLocalArchive(Widget widget) {
        final String projectKey, widgetKey;
        try { projectKey = archiveProjectKey(); widgetKey = archiveWidgetKey(widget); }
        catch (Exception failure) {
            Toast.makeText(this, "Сначала сохрани проект, затем открой архив", Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder report = new StringBuilder();
        try {
            LocalArchiveStore store = new LocalArchiveStore(this);
            try {
                List<LocalArchiveStore.Sample> rows = store.latest(projectKey, widgetKey, 40);
                if (rows.isEmpty()) report.append("Записей пока нет. Настрой локальный TCP-канал и запусти экран в рабочем режиме.");
                else {
                    java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT);
                    for (LocalArchiveStore.Sample row : rows)
                        report.append(format.format(new java.util.Date(row.timestamp))).append("  ").append(row.value).append("\n");
                }
            } finally { store.close(); }
        } catch (Exception failure) {
            report.append("Не удалось прочитать локальный архив.");
        }
        ScrollView scroll = new ScrollView(this);
        TextView content = new TextView(this);
        content.setText(report.toString()); content.setTextSize(14);
        content.setTextColor(Color.rgb(30, 41, 59)); content.setPadding(dp(16), dp(12), dp(16), dp(12));
        scroll.addView(content);
        String title = widget.text == null || widget.text.trim().isEmpty() ? "Архив" : widget.text;
        new AlertDialog.Builder(this).setTitle(title)
                .setView(scroll).setNeutralButton("Экспорт CSV", (dialog, which) -> exportLocalArchive(projectKey, widgetKey, widget.text))
                .setNegativeButton("График", (dialog, which) -> showArchiveGraph(projectKey, widgetKey, title))
                .setPositiveButton("Закрыть", null).show();
    }

    private void showArchiveGraph(String projectKey, String widgetKey, String title) {
        final List<LocalArchiveStore.Sample> rows;
        try {
            LocalArchiveStore store = new LocalArchiveStore(this);
            try { rows = store.latest(projectKey, widgetKey, 200); }
            finally { store.close(); }
        } catch (Exception failure) {
            Toast.makeText(this, "Не удалось прочитать локальный архив", Toast.LENGTH_LONG).show();
            return;
        }
        if (rows.isEmpty()) {
            Toast.makeText(this, "Для графика пока нет записей", Toast.LENGTH_SHORT).show();
            return;
        }
        View graph = new View(this) {
            final int[] colors = {Color.rgb(30, 136, 229), Color.rgb(67, 160, 71),
                    Color.rgb(251, 140, 0), Color.rgb(229, 57, 53)};
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float left = dp(48), right = getWidth() - dp(16), top = dp(24), bottom = getHeight() - dp(40);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Color.rgb(210, 216, 224)); paint.setStrokeWidth(dp(1));
                for (int grid = 0; grid <= 4; grid++) {
                    float y = top + (bottom - top) * grid / 4f;
                    canvas.drawLine(left, y, right, y, paint);
                }
                int maxChannels = 1;
                for (LocalArchiveStore.Sample row : rows)
                    maxChannels = Math.max(maxChannels, row.value.split(",", -1).length);
                List<Float> mins = new ArrayList<>(), maxs = new ArrayList<>();
                for (int channel = 0; channel < maxChannels; channel++) {
                    List<Float> values = new ArrayList<>();
                    float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
                    for (int i = rows.size() - 1; i >= 0; i--) {
                        String[] fields = rows.get(i).value.split(",", -1);
                        if (channel >= fields.length) { values.add(Float.NaN); continue; }
                        try {
                            float value = Float.parseFloat(fields[channel]);
                            if (!Float.isFinite(value)) value = Float.NaN;
                            values.add(value);
                            if (!Float.isNaN(value)) { min = Math.min(min, value); max = Math.max(max, value); }
                        } catch (NumberFormatException invalid) { values.add(Float.NaN); }
                    }
                    if (Float.isFinite(min) && Float.isFinite(max)) {
                        mins.add(min); maxs.add(max);
                    } else { mins.add(0f); maxs.add(1f); }
                }
                for (int channel = 0; channel < maxChannels; channel++) {
                    paint.setColor(colors[channel % colors.length]); paint.setStrokeWidth(dp(2));
                    paint.setStyle(Paint.Style.STROKE); paint.setStrokeJoin(Paint.Join.ROUND);
                    android.graphics.Path path = new android.graphics.Path(); boolean active = false;
                    List<LocalArchiveStore.Sample> ascending = new ArrayList<>(rows);
                    java.util.Collections.reverse(ascending);
                    for (int i = 0; i < ascending.size(); i++) {
                        String[] fields = ascending.get(i).value.split(",", -1);
                        if (channel >= fields.length) { active = false; continue; }
                        float value;
                        try { value = Float.parseFloat(fields[channel]); }
                        catch (NumberFormatException invalid) { active = false; continue; }
                        if (!Float.isFinite(value)) { active = false; continue; }
                        float min = mins.get(channel), max = maxs.get(channel);
                        float range = Math.max(0.0001f, max - min);
                        float x = left + (right - left) * (ascending.size() <= 1 ? 0.5f : i / (float)(ascending.size() - 1));
                        float y = bottom - (bottom - top) * (value - min) / range;
                        if (!active) { path.moveTo(x, y); active = true; } else path.lineTo(x, y);
                    }
                    canvas.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
                    paint.setTextSize(dp(12)); canvas.drawText("К" + (channel + 1), left + dp(6 + channel * 44), top - dp(7), paint);
                }
            }
        };
        graph.setLayoutParams(new ViewGroup.LayoutParams(-1, dp(280)));
        new AlertDialog.Builder(this).setTitle("График — " + title).setView(graph)
                .setPositiveButton("Закрыть", null).show();
    }

    private void exportLocalArchive(String projectKey, String widgetKey, String label) {
        pendingArchiveProjectId = projectKey;
        pendingArchiveWidgetKey = widgetKey;
        String safe = (label == null || label.trim().isEmpty() ? "archive" : label.trim())
                .replaceAll("[^A-Za-zА-Яа-я0-9._-]", "_");
        pendingArchiveTitle = safe + ".csv";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, pendingArchiveTitle);
        try { startActivityForResult(intent, REQUEST_EXPORT_LOCAL_ARCHIVE); }
        catch (Exception failure) {
            pendingArchiveProjectId = pendingArchiveWidgetKey = pendingArchiveTitle = null;
            Toast.makeText(this, "Не удалось открыть выбор файла", Toast.LENGTH_LONG).show();
        }
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
            else if (binding.startsWith("usb://")
                    && !(isCraftRegisterSource(widget.type) || isCraftRegisterSink(widget.type) || "FBD Вход".equals(widget.type)))
                state = "CFG — USB RTU доступен для FBD register variables и FBD Вход";
            else if (binding.startsWith("usb://")
                    && parseProjectBinding(i, binding, isCraftRegisterFloat(widget.type) ? 2 : 1) == null)
                state = "CFG — неверный USB RTU адрес";
            else if (binding.startsWith("usb://") && (manualUsbConnection == null || !manualUsbConnection.isOpen()))
                state = "ERR: USB RTU не подключён";
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
        OriginalFbdPalette.show(this, this::addWidget, this::showAddWidgetMenu,
                this::showCraftMacroTools, this::createCraftMacroFromSelection,
                this::showExtendedWidgetMenu);
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
                "FBD — Craft TIMER (TON / TOF / TP / BLINK)",                "FBD — Craft BLINK (мигание)",
                "FBD — Craft SEL (целочисленный)",
                "FBD — Craft COMPARE (INT32)",
                "FBD — Craft CH_I (изменение Integer)",
                "FBD — Craft CH_F (изменение Float)",
                "FBD — Craft Arithmetic INT (16 бит)",
                "FBD — Craft Arithmetic FLOAT32",
                "FBD — Craft COMPARE FLOAT32",
                "FBD — Craft MOVE (EN + Integer)",
                "FBD — Craft MX (EN + Integer ID)",
                "FBD — Craft ARRAY (EN + DT + ID)",
                "FBD — Craft LOG (Float32)",
                "FBD — Craft WordToFloat (Integer → Float32)",
                "FBD — Craft POW (Float32)", "FBD — Craft SIN (Float32)",
                "FBD — Craft ABS (Float32)", "FBD — Craft IntToFloat (signed 16-bit)",
                "FBD — Craft FloatToInt"
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
                "FBD CRAFT CTU", "FBD CRAFT T", "FBD CRAFT TIMER", "FBD CRAFT BLINK",
                "FBD CRAFT SEL", "FBD CRAFT COMPARE",
                "FBD CRAFT CHANGE I", "FBD CRAFT CHANGE F",
                "FBD CRAFT ARITHMETIC INT", "FBD CRAFT ARITHMETIC FLOAT",
                "FBD CRAFT COMPARE FLOAT", "FBD CRAFT MOVE",
                "FBD CRAFT MX", "FBD CRAFT ARRAY",
                "FBD CRAFT LOG", "FBD CRAFT WORD TO FLOAT",
                "FBD CRAFT POW", "FBD CRAFT SIN", "FBD CRAFT ABS",
                "FBD CRAFT INT TO FLOAT", "FBD CRAFT FLOAT TO INT"
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
                "CTU 16 bit", "T", "TON", "BLINK", "SEL", ">",
                "CH_I", "CH_F", "+", "+", ">", "MOVE", "MX", "ARRAY", "LN", "WORD->FL",
                "POW", "SIN", "ABS", "INT->FL", "FL->INT"
        };
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(4));
        Button macroTools = new Button(this);
        macroTools.setText("Редактор макросов  ›");
        macroTools.setAllCaps(false);
        macroTools.setOnClickListener(v -> showCraftMacroTools());
        list.addView(macroTools, new LinearLayout.LayoutParams(-1, -2));
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
        if (nestedMacroEdit != null && (!type.startsWith("FBD ")
                || "FBD CRAFT MACRO".equals(canonicalFbdBlockType(type)))) {
            Toast.makeText(this, "Во вложенный граф можно добавлять только обычные FBD-блоки", Toast.LENGTH_SHORT).show();
            return;
        }
        Widget w = new Widget();
        w.type = type;
        w.text = text;
        if ("FBD CRAFT ARITHMETIC INT".equals(type)
                || "FBD CRAFT ARITHMETIC FLOAT".equals(type)) w.compareOperator = "+";
        w.x = dp(40) + (widgets.size() % 4) * dp(24);
        w.y = dp(50) + (widgets.size() % 6) * dp(22);
        w.w = OriginalIndicator.supports(type) ? dp(40) : dp(160);
        w.h = OriginalIndicator.supports(type) ? dp(40) : dp(58);
        if (OriginalCraftNumericIndicator.TYPE.equals(type)) {
            w.w = dp(150); w.h = dp(40);
            try {
                w.extraFields.put("craftNumericIndicatorType", OriginalCraftNumericIndicator.SIGNED_INT16);
                w.extraFields.put("craftNumericIndicatorDigits", 5);
                w.extraFields.put("craftNumericIndicatorDecimals", 0);
                w.extraFields.put("craftNumericIndicatorFontChange", false);
                w.extraFields.put("craftNumericIndicatorFrame", false);
            } catch (org.json.JSONException ignored) { }
        }
        if (OriginalCraftSwitch.supports(type)) {
            w.w = dp(80); w.h = dp(80);
            try {
                w.extraFields.put("craftSwitchSkin", 1);
                w.extraFields.put("craftSwitchBit", 0);
                w.extraFields.put("craftSwitchBitEnabled", false);
                w.extraFields.put("craftSwitchVisibleInWork", true);
                w.extraFields.put("craftOther", new JSONObject().put("imagePosition", 1).put("visibleInWork", true));
            } catch (Exception ignored) { }
        }
        if ("Список Craft".equals(type)) {
            w.w = dp(190); w.h = dp(58);
            String[] labels = new String[16]; java.util.Arrays.fill(labels, "");
            labels[0] = "Вариант 1"; labels[1] = "Вариант 2";
            w.extraFields = new JSONObject();
            try {
                w.extraFields.put("craftListLabels", CraftBitList.toJson(labels));
                w.extraFields.put("craftListVisibleInWork", true);
                w.extraFields.put("craftListCheckNoChange", true);
            } catch (Exception ignored) { }
        }
        if ("GIF Craft".equals(type)) {
            w.w = dp(160); w.h = dp(100);
            try { w.extraFields.put("craftGifPath", ""); } catch (Exception ignored) { }
        }
        if ("Архив Craft".equals(type)) {
            w.w = dp(200); w.h = dp(72);
            try {
                w.extraFields.put("craftArchiveKey", java.util.UUID.randomUUID().toString());
                w.extraFields.put("craftArchiveIntervalSeconds", 1);
                w.extraFields.put("craftArchiveMaxRows", 10000);
                w.extraFields.put("craftArchiveChannelCount", 1);
                w.extraFields.put("craftArchiveRecordType", "INT");
            } catch (org.json.JSONException ignored) { }
        }
        if ("Числовой ввод Craft".equals(type)) { w.w = dp(120); w.h = dp(48); }
        if ("Линейный задатчик Craft".equals(type)) {
            w.w = dp(190); w.h = dp(62);
            try { w.extraFields.put("craftLinearMax", CraftLinearSetter.DEFAULT_MAX); w.extraFields.put("craftLinearValue", 0); } catch (Exception ignored) { }
        }
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
            commitPendingMediaCopy(value);
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
        final Spinner craftIndicatorTypeField;
        final EditText craftIndicatorDigitsField, craftIndicatorDecimalsField;
        final android.widget.CheckBox craftIndicatorFontChangeField, craftIndicatorFrameField;
        if (OriginalCraftNumericIndicator.TYPE.equals(w.type)) {
            craftIndicatorTypeField = new Spinner(this);
            craftIndicatorTypeField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"INT16", "UINT16", "FLOAT32", "INT32"}));
            craftIndicatorTypeField.setSelection(Math.max(0, Math.min(3,
                    w.extraFields.optInt("craftNumericIndicatorType", 0))));
            craftIndicatorTypeField.setContentDescription("Тип числового значения Craft");
            form.addView(craftIndicatorTypeField);
            craftIndicatorDigitsField = propertyField(Integer.toString(Math.max(1, Math.min(18,
                    w.extraFields.optInt("craftNumericIndicatorDigits", 5)))), InputType.TYPE_CLASS_NUMBER);
            craftIndicatorDigitsField.setHint("Разрядов целой части (1–18)"); form.addView(craftIndicatorDigitsField);
            craftIndicatorDecimalsField = propertyField(Integer.toString(Math.max(0, Math.min(9,
                    w.extraFields.optInt("craftNumericIndicatorDecimals", 0)))), InputType.TYPE_CLASS_NUMBER);
            craftIndicatorDecimalsField.setHint("Дробных разрядов (0–9)"); form.addView(craftIndicatorDecimalsField);
            craftIndicatorFontChangeField = new android.widget.CheckBox(this);
            craftIndicatorFontChangeField.setText("Системный шрифт");
            craftIndicatorFontChangeField.setChecked(w.extraFields.optBoolean("craftNumericIndicatorFontChange", false));
            form.addView(craftIndicatorFontChangeField);
            craftIndicatorFrameField = new android.widget.CheckBox(this);
            craftIndicatorFrameField.setText("Рамка по регистру");
            craftIndicatorFrameField.setChecked(w.extraFields.optBoolean("craftNumericIndicatorFrame", false));
            form.addView(craftIndicatorFrameField);
        } else {
            craftIndicatorTypeField = null; craftIndicatorDigitsField = null; craftIndicatorDecimalsField = null;
            craftIndicatorFontChangeField = null; craftIndicatorFrameField = null;
        }

        TextView connectionLabel = new TextView(this);
        connectionLabel.setText("Привязка к данным (адрес / регистр / устройство)");
        connectionLabel.setPadding(0, dp(10), 0, 0);
        form.addView(connectionLabel);
        EditText connectionField = propertyField(w.connectionInfo == null ? "" : w.connectionInfo, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        connectionField.setHint("tcp://192.168.1.10:502/1/40001 (FC03/FC04) или fbd://N (номер блока с 1)");
        if ("Прогресс-бар".equals(w.type)
                && w.extraFields.optInt("craftComponentId", -1) == 11)
            connectionField.setHint("Переназначь вручную: tcp://192.168.1.10:502/1/40001 (исходная связь Craft не активна)");
        if (isCraftRegisterSource(w.type) || isCraftRegisterSink(w.type))
            connectionField.setHint("TCP: tcp://192.168.1.10:502/1/40001 · USB RTU: usb://1/40001 (явно подключи адаптер)");
        else if ("FBD Вход".equals(w.type))
            connectionField.setHint("TCP: tcp://192.168.1.10:502/1/40001 · USB RTU: usb://1/40001");
        form.addView(connectionField);
        String sourceBindingText = CraftNetworkBindingResolver.display(
                w.extraFields.optJSONObject("craftBindingResolution"));
        if (!sourceBindingText.isEmpty()) {
            TextView sourceBindingLabel = new TextView(this);
            sourceBindingLabel.setText(sourceBindingText);
            sourceBindingLabel.setContentDescription("Исходная привязка Craft; неактивна");
            sourceBindingLabel.setPadding(0, dp(6), 0, dp(2));
            form.addView(sourceBindingLabel);
        }
        final EditText archiveIntervalField, archiveMaxRowsField, archiveChannelCountField, archiveTriggerField;
        final Spinner archiveRecordTypeField;
        if ("Архив Craft".equals(w.type)) {
            connectionField.setHint("Локальный канал: tcp://192.168.1.10:502/1/40001 (holding-регистр FC03)");
            TextView archiveHelp = new TextView(this);
        archiveHelp.setText("Импортированные связи Craft отключены. Укажи первый локальный TCP holding-регистр; каналы читаются подряд. INT занимает один регистр, FLOAT — два. Необязательный триггер записывает на фронте 0→1; без него действует интервал. Данные остаются на устройстве; удалённая отправка архива отключена.");
            archiveHelp.setPadding(0, dp(8), 0, dp(4)); form.addView(archiveHelp);
            archiveTriggerField = propertyField(w.extraFields.optString("craftArchiveTriggerBinding", ""),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            archiveTriggerField.setHint("Необязательный триггер: tcp://host:502/1/40002");
            archiveTriggerField.setContentDescription("Триггер архива"); form.addView(archiveTriggerField);
            archiveChannelCountField = propertyField(Integer.toString(Math.max(1, Math.min(4,
                    w.extraFields.optInt("craftArchiveChannelCount", 1)))), InputType.TYPE_CLASS_NUMBER);
            archiveChannelCountField.setHint("Каналы (1–4)");
            archiveChannelCountField.setContentDescription("Каналы архива"); form.addView(archiveChannelCountField);
            archiveRecordTypeField = new Spinner(this);
            archiveRecordTypeField.setContentDescription("Тип записи архива");
            archiveRecordTypeField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"INT — 16 бит", "FLOAT — 32 бит"}));
            archiveRecordTypeField.setSelection("FLOAT".equals(w.extraFields.optString("craftArchiveRecordType", "INT")) ? 1 : 0);
            form.addView(archiveRecordTypeField);
            archiveIntervalField = propertyField(Integer.toString(Math.max(1, w.extraFields.optInt("craftArchiveIntervalSeconds", 1))), InputType.TYPE_CLASS_NUMBER);
            archiveIntervalField.setHint("Интервал записи, секунды (1–86400)");
            archiveIntervalField.setContentDescription("Интервал архива"); form.addView(archiveIntervalField);
            archiveMaxRowsField = propertyField(Integer.toString(Math.max(100, w.extraFields.optInt("craftArchiveMaxRows", 10000))), InputType.TYPE_CLASS_NUMBER);
            archiveMaxRowsField.setHint("Максимум записей (100–100000)");
            archiveMaxRowsField.setContentDescription("Максимум строк архива"); form.addView(archiveMaxRowsField);
        } else {
            archiveIntervalField = null; archiveMaxRowsField = null;
            archiveChannelCountField = null; archiveRecordTypeField = null; archiveTriggerField = null;
        }
        final boolean craftBitList = "Список Craft".equals(w.type);
        final EditText craftBitListLabelsField;
        final android.widget.CheckBox craftBitListVisibleField, craftBitListNoChangeField;
        if (craftBitList) {
            connectionField.setHint("tcp://192.168.1.10:502/1/40001 (holding-регистр, FC03)");
            TextView labelsHelp = new TextView(this);
            labelsHelp.setText("Подписи битов 0–15, по одной на строку. Пустая строка скрывает пункт.");
            labelsHelp.setPadding(0, dp(10), 0, dp(4)); form.addView(labelsHelp);
            craftBitListLabelsField = new EditText(this);
            craftBitListLabelsField.setMinLines(5); craftBitListLabelsField.setMaxLines(10);
            craftBitListLabelsField.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
            craftBitListLabelsField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            craftBitListLabelsField.setText(CraftBitList.toEditorLines(CraftBitList.fromJson(w.extraFields.optJSONArray("craftListLabels"))));
            craftBitListLabelsField.setHint("0: Включить\n1: Выключить\n2: ..."); form.addView(craftBitListLabelsField);
            craftBitListVisibleField = new android.widget.CheckBox(this);
            craftBitListVisibleField.setText("Показывать список в рабочем режиме");
            craftBitListVisibleField.setChecked(w.extraFields.optBoolean("craftListVisibleInWork", true)); form.addView(craftBitListVisibleField);
            craftBitListNoChangeField = new android.widget.CheckBox(this);
            craftBitListNoChangeField.setText("Не изменять биты в рабочем режиме");
            craftBitListNoChangeField.setChecked(w.extraFields.optBoolean("craftListCheckNoChange", false)); form.addView(craftBitListNoChangeField);
            TextView remapHelp = new TextView(this);
            remapHelp.setText("Импортированная связь Craft выключена. Для обмена укажи свой TCP адрес holding-регистра выше."); form.addView(remapHelp);
        } else { craftBitListLabelsField = null; craftBitListVisibleField = null; craftBitListNoChangeField = null; }
        final boolean craftLinearSetter = "Линейный задатчик Craft".equals(w.type);
        final EditText craftLinearMaxField;
        final android.widget.CheckBox craftLinearVerticalField;
        if (craftLinearSetter) {
            connectionField.setHint("tcp://192.168.1.10:502/1/40001 (holding-регистр, FC03/FC06)");
            craftLinearMaxField = propertyField(Integer.toString(CraftLinearSetter.maximum(w.extraFields)), InputType.TYPE_CLASS_NUMBER);
            craftLinearMaxField.setHint("Верхний предел, 1–65535"); form.addView(craftLinearMaxField);
            craftLinearVerticalField = new android.widget.CheckBox(this);
            craftLinearVerticalField.setText("Вертикальная ориентация");
            craftLinearVerticalField.setChecked(w.extraFields.optBoolean("craftLinearVertical", false));
            form.addView(craftLinearVerticalField);
            TextView remapHelp = new TextView(this);
            remapHelp.setText("Связь из Craft отключена. Укажи TCP holding-регистр для чтения и записи."); form.addView(remapHelp);
        } else { craftLinearMaxField = null; craftLinearVerticalField = null; }
        final boolean craftGif = "GIF Craft".equals(w.type);
        final EditText craftGifPathField;
        if (craftGif) {
            TextView gifHelp = new TextView(this);
            gifHelp.setText("Анимированное изображение Craft (GIF)");
            form.addView(gifHelp);
            craftGifPathField = propertyField(w.extraFields.optString("craftGifPath", ""), InputType.TYPE_CLASS_TEXT);
            craftGifPathField.setHint("Файл GIF в проекте");
            form.addView(craftGifPathField);
            addMediaPicker(form, craftGifPathField, "image/gif");
        } else { craftGifPathField = null; }
        final boolean craftNumericInput = "Числовой ввод Craft".equals(w.type);
        final EditText craftNumericMinField, craftNumericMaxField, craftNumericDecimalsField, craftNumericPasswordField;
        final Spinner craftNumericModeField, craftNumericTypeIntField;
        final android.widget.CheckBox craftNumericFrameField, craftNumericSystemFontField, craftNumericPasswordRequiredField, craftNumericEnabledField;
        if (craftNumericInput) {
            connectionField.setHint("tcp://192.168.1.10:502/1/40001 (holding-регистр FC03/FC06)");
            craftNumericMinField = propertyField(Double.toString(w.extraFields.optDouble("craftNumericMin", 0)), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            craftNumericMinField.setHint("Минимум"); form.addView(craftNumericMinField);
            craftNumericMaxField = propertyField(Double.toString(w.extraFields.optDouble("craftNumericMax", 1000)), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            craftNumericMaxField.setHint("Максимум"); form.addView(craftNumericMaxField);
            craftNumericModeField = new Spinner(this);
            craftNumericModeField.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Целое число", "Float32"}));
            craftNumericModeField.setSelection(w.extraFields.optInt("craftNumericMode", 0) == CraftNumericInput.FLOAT32 ? 1 : 0); form.addView(craftNumericModeField);
            craftNumericTypeIntField = new Spinner(this);
            craftNumericTypeIntField.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"UINT16", "INT16"}));
            craftNumericTypeIntField.setSelection(w.extraFields.optInt("craftNumericTypeInt", 0) == CraftNumericInput.SIGNED_16 ? 1 : 0); form.addView(craftNumericTypeIntField);
            craftNumericDecimalsField = propertyField(Integer.toString(w.extraFields.optInt("craftNumericDecimals", 1)), InputType.TYPE_CLASS_NUMBER);
            craftNumericDecimalsField.setHint("Цифр после точки (0–8)"); form.addView(craftNumericDecimalsField);
            craftNumericFrameField = new android.widget.CheckBox(this); craftNumericFrameField.setText("Рамка");
            craftNumericFrameField.setChecked(w.extraFields.optBoolean("craftNumericFrame", false)); form.addView(craftNumericFrameField);
            craftNumericSystemFontField = new android.widget.CheckBox(this); craftNumericSystemFontField.setText("Системный шрифт");
            craftNumericSystemFontField.setChecked(w.extraFields.optBoolean("craftNumericSystemFont", false)); form.addView(craftNumericSystemFontField);
            craftNumericPasswordRequiredField = new android.widget.CheckBox(this); craftNumericPasswordRequiredField.setText("Запрашивать пароль оператора");
            craftNumericPasswordRequiredField.setChecked(w.extraFields.optBoolean("craftNumericPasswordRequired", false)); form.addView(craftNumericPasswordRequiredField);
            craftNumericPasswordField = propertyField("", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            craftNumericPasswordField.setHint(w.extraFields.optString("craftNumericPasswordKey", "").isEmpty()
                    ? "Новый пароль (1–10 символов; старый Craft надо задать заново)"
                    : "Новый пароль или пусто, чтобы сохранить текущий"); form.addView(craftNumericPasswordField);
            craftNumericEnabledField = new android.widget.CheckBox(this); craftNumericEnabledField.setText("Разрешить ввод после настройки привязки");
            craftNumericEnabledField.setChecked(w.extraFields.optBoolean("craftNumericEnabled", false)); form.addView(craftNumericEnabledField);
            TextView numericHelp = new TextView(this);
            numericHelp.setText("Связь Craft сохранена выключенной. Системный источник слота 100 читает локальные слова; запись целых в регистры 10–1999 и Float32 в пары 10–1998 доступна после явного разрешения ввода. Для TCP укажи свой holding-регистр. Пароль хранится в Android Keystore.");
            form.addView(numericHelp);
        } else {
            craftNumericMinField = craftNumericMaxField = craftNumericDecimalsField = craftNumericPasswordField = null;
            craftNumericModeField = craftNumericTypeIntField = null;
            craftNumericFrameField = craftNumericSystemFontField = craftNumericPasswordRequiredField = craftNumericEnabledField = null;
        }
        final boolean craftConstantButton = "Константная кнопка Craft".equals(w.type);
        final boolean craftButtonConfig = "Кнопка".equals(w.type) || craftConstantButton;
        final boolean craftSwitchConfig = OriginalCraftSwitch.supports(w.type);
        final android.widget.Spinner craftSwitchSkinField;
        final EditText craftSwitchBitField;
        final android.widget.CheckBox craftSwitchBitEnabledField, craftSwitchVisibleField;
        if (craftSwitchConfig) {
            craftSwitchSkinField = new android.widget.Spinner(this);
            String[] switchSkins = new String[8];
            for (int i = 0; i < switchSkins.length; i++) switchSkins[i] = "Оригинальный переключатель " + (i + 1);
            craftSwitchSkinField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, switchSkins));
            craftSwitchSkinField.setSelection(OriginalCraftSwitch.imagePosition(w.extraFields) - 1);
            form.addView(craftSwitchSkinField);
            craftSwitchBitField = propertyField(Integer.toString(w.extraFields.optInt("craftSwitchBit", 0)), InputType.TYPE_CLASS_NUMBER);
            craftSwitchBitField.setHint("Бит holding-регистра (0–15)"); form.addView(craftSwitchBitField);
            craftSwitchBitEnabledField = new android.widget.CheckBox(this);
            craftSwitchBitEnabledField.setText("Разрешить запись бита FC22 после ручной TCP-привязки");
            craftSwitchBitEnabledField.setChecked(w.extraFields.optBoolean("craftSwitchBitEnabled", false)); form.addView(craftSwitchBitEnabledField);
            TextView switchHelp = new TextView(this);
            switchHelp.setText("Импортированная привязка Craft не активируется. Для записи укажи TCP holding-регистр FC03/FC22 и включи флажок.");
            form.addView(switchHelp);
            craftSwitchVisibleField = new android.widget.CheckBox(this);
            craftSwitchVisibleField.setText("Показывать в рабочем режиме");
            craftSwitchVisibleField.setChecked(w.extraFields.optBoolean("craftSwitchVisibleInWork", OriginalCraftSwitch.visibleInWork(w.extraFields)));
            form.addView(craftSwitchVisibleField);
        } else { craftSwitchSkinField = null; craftSwitchBitField = null; craftSwitchBitEnabledField = null; craftSwitchVisibleField = null; }
        final android.widget.Spinner craftButtonModeField, craftButtonConstantTypeField;
        final EditText craftButtonConstantField, craftButtonBitField;
        final android.widget.CheckBox craftButtonVisibleField, craftButtonBitEnabledField;
        if (craftButtonConfig) {
            craftButtonModeField = new android.widget.Spinner(this);
            craftButtonModeField.setAdapter(new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"Моментальная команда по биту", "Запись константы"}));
            craftButtonModeField.setSelection(craftConstantButton || w.extraFields.optInt("craftButtonMode", 0) == 2 ? 1 : 0);
            form.addView(craftButtonModeField);
            craftButtonBitField = propertyField(Integer.toString(CraftMomentaryButton.bit(w.extraFields)),
                    InputType.TYPE_CLASS_NUMBER);
            craftButtonBitField.setHint("Бит регистра (0–15)"); form.addView(craftButtonBitField);
            craftButtonBitEnabledField = new android.widget.CheckBox(this);
            craftButtonBitEnabledField.setText("Разрешить запись бита FC22 после явной TCP-привязки");
            craftButtonBitEnabledField.setChecked(w.extraFields.optBoolean("craftButtonBitEnabled", false));
            form.addView(craftButtonBitEnabledField);
            TextView bitHelp = new TextView(this);
            bitHelp.setText("Режим Craft 0: при нажатии бит устанавливается, при отпускании сбрасывается. Нужен TCP holding-регистр FC03/FC22, например tcp://192.168.1.10:502/1/40001; импортированная привязка не используется.");
            form.addView(bitHelp);
            craftButtonConstantTypeField = new android.widget.Spinner(this);
            craftButtonConstantTypeField.setAdapter(new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"UINT16", "Float32"}));
            craftButtonConstantTypeField.setSelection(w.extraFields.optInt("craftButtonConstantType", 0)); form.addView(craftButtonConstantTypeField);
            craftButtonConstantField = propertyField(w.extraFields.optString("craftButtonConstant", "0"),
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            craftButtonConstantField.setHint("Значение константы"); form.addView(craftButtonConstantField);
            TextView constantHelp = new TextView(this);
            constantHelp.setText("Craft-регистр #" + w.extraFields.optInt("craftButtonConstantRegister", 0)
                    + " сохранён отдельно. Для записи константы настрой TCP holding-регистр FC03/FC06 выше."); form.addView(constantHelp);
            craftButtonVisibleField = new android.widget.CheckBox(this);
            craftButtonVisibleField.setText("Показывать кнопку в рабочем режиме");
            craftButtonVisibleField.setChecked(w.extraFields.optBoolean("craftButtonVisibleInWork", true)); form.addView(craftButtonVisibleField);
        } else { craftButtonModeField = null; craftButtonConstantTypeField = null; craftButtonConstantField = null; craftButtonBitField = null; craftButtonBitEnabledField = null; craftButtonVisibleField = null; }
        final boolean craftRegisterGateway = "Шлюз Craft".equals(w.type);
        final boolean craftBitGateway = "Шлюз битовый".equals(w.type);
        if (craftRegisterGateway || craftBitGateway) {
            connectionLabel.setVisibility(View.GONE);
            connectionField.setVisibility(View.GONE);
        }

        final EditText smsPhoneField;
        final android.widget.Spinner smsRegisterCountField;
        final android.widget.CheckBox craftSmsEnabledField, craftSmsOptionField;
        final EditText craftSmsOptionRegisterField;
        if ("SMS-уведомление".equals(w.type)) {
            String savedPhone="";
            try{savedPhone=SmsRecipientStore.read(this,w.extraFields.optString("smsRecipientId",""));}catch(Exception ignored){}
            if (savedPhone.isEmpty() && w.extraFields.optInt("craftComponentId", -1) == 10)
                savedPhone = w.extraFields.optJSONObject("craftOther") == null ? ""
                        : w.extraFields.optJSONObject("craftOther").optString("phone", "");
            smsPhoneField=propertyField(savedPhone,InputType.TYPE_CLASS_PHONE);
            smsPhoneField.setHint("Номер получателя, например +79991234567");form.addView(smsPhoneField);
            if (w.extraFields.optInt("craftComponentId", -1) == 10) {
                craftSmsEnabledField = new android.widget.CheckBox(this);
                craftSmsEnabledField.setText("Разрешить отправку SMS при срабатывании (явное подтверждение)");
                craftSmsEnabledField.setChecked(w.extraFields.optBoolean("smsEnabled", false));
                form.addView(craftSmsEnabledField);
                craftSmsOptionField = new android.widget.CheckBox(this);
                craftSmsOptionField.setText("Подставлять значения из регистров Craft");
                craftSmsOptionField.setChecked(w.extraFields.optBoolean("craftSmsOption", false));
                form.addView(craftSmsOptionField);
                craftSmsOptionRegisterField = propertyField(Integer.toString(
                        w.extraFields.optInt("craftSmsOptionRegister", 0)), InputType.TYPE_CLASS_NUMBER);
                craftSmsOptionRegisterField.setHint("Адрес первого регистра SMS (0–65535)");
                form.addView(craftSmsOptionRegisterField);
            } else {
                craftSmsEnabledField = null; craftSmsOptionField = null; craftSmsOptionRegisterField = null;
            }
            smsRegisterCountField = new android.widget.Spinner(this);
            String[] smsRegisterOptions = new String[8];
            for (int i = 0; i < smsRegisterOptions.length; i++) smsRegisterOptions[i] = (i + 1) + " рег.";
            smsRegisterCountField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, smsRegisterOptions));
            int savedCount = Math.max(1, Math.min(8, w.extraFields.optInt("smsRegisterCount", 1)));
            smsRegisterCountField.setSelection(savedCount - 1);
            smsRegisterCountField.setContentDescription("Количество регистров SMS для чтения");
            form.addView(smsRegisterCountField);
            TextView smsHelp=new TextView(this);smsHelp.setText("Сообщение отправляется один раз при переходе сигнала 0→1. В тексте {value} подставляется значение Modbus. Номер хранится зашифрованно на устройстве и не входит в экспорт проекта.");form.addView(smsHelp);
            TextView smsRegistersHelp = new TextView(this);
            smsRegistersHelp.setText("Читаются последовательные регистры от адреса привязки. Значения подставляются в %1…%8; если номера %n не указаны и нет {value}, значения добавляются в конец через пробел.");
            form.addView(smsRegistersHelp);
        } else { smsPhoneField=null; smsRegisterCountField=null; craftSmsEnabledField=null; craftSmsOptionField=null; craftSmsOptionRegisterField=null; }
        final android.widget.CheckBox incomingSmsEnabledField;
        final EditText incomingSmsSenderField, incomingSmsCommandField, incomingSmsBitField;
        final android.widget.Spinner incomingSmsModeField;
        final android.widget.CheckBox incomingSmsStrobeEnabledField;
        final EditText incomingSmsStrobeRegisterField, incomingSmsStrobeBitField;
        if ("Входящее SMS".equals(w.type)) {
            incomingSmsEnabledField = new android.widget.CheckBox(this);
            incomingSmsEnabledField.setText("Разрешить управление этим регистром через SMS");
            incomingSmsEnabledField.setChecked(w.extraFields.optBoolean("smsEnabled", false));
            form.addView(incomingSmsEnabledField);
            String savedSender = "";
            try { savedSender = SmsRecipientStore.read(this, w.extraFields.optString("smsSenderId", "")); }
            catch (Exception ignored) {}
            incomingSmsSenderField = propertyField(savedSender, InputType.TYPE_CLASS_PHONE);
            incomingSmsSenderField.setHint("Разрешённый номер, например +79991234567"); form.addView(incomingSmsSenderField);
            incomingSmsCommandField = propertyField(w.extraFields.optString("smsCommand", ""),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            incomingSmsCommandField.setHint("Точная команда: ВКЛ или Уставка"); form.addView(incomingSmsCommandField);
            incomingSmsModeField = new android.widget.Spinner(this);
            incomingSmsModeField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"Число в holding-регистр", "Установить бит"}));
            incomingSmsModeField.setSelection("bit".equals(w.extraFields.optString("smsMode", "number")) ? 1 : 0);
            form.addView(incomingSmsModeField);
            incomingSmsBitField = propertyField(Integer.toString(w.extraFields.optInt("smsBit", 0)), InputType.TYPE_CLASS_NUMBER);
            incomingSmsBitField.setHint("Номер бита 0–15 (для режима бит)"); form.addView(incomingSmsBitField);
            incomingSmsStrobeEnabledField = new android.widget.CheckBox(this);
            incomingSmsStrobeEnabledField.setText("Установить бит strobe после числовой команды");
            incomingSmsStrobeEnabledField.setChecked(w.extraFields.optBoolean("smsStrobeEnabled", false));
            form.addView(incomingSmsStrobeEnabledField);
            incomingSmsStrobeRegisterField = propertyField(w.extraFields.has("smsStrobeRegister")
                    ? Integer.toString(w.extraFields.optInt("smsStrobeRegister")) : "", InputType.TYPE_CLASS_NUMBER);
            incomingSmsStrobeRegisterField.setHint("Holding-регистр strobe (например 40002)"); form.addView(incomingSmsStrobeRegisterField);
            incomingSmsStrobeBitField = propertyField(Integer.toString(w.extraFields.optInt("smsStrobeBit", 0)), InputType.TYPE_CLASS_NUMBER);
            incomingSmsStrobeBitField.setHint("Strobe бит 0–15"); form.addView(incomingSmsStrobeBitField);
            TextView smsIncomingHelp = new TextView(this);
            smsIncomingHelp.setText("Адрес задаётся выше как tcp://хост:порт/unit/регистр; допустим только holding-регистр. Числовая команда: «команда пробел число…» (до 16 signed 16-bit значений) записывает последовательные регистры. Битовая команда должна полностью совпасть. Strobe устанавливается после успешной числовой записи и остаётся в 1 до сброса устройством/сценарием. SMS принимаются только пока открыт проект в операторском режиме.");
            form.addView(smsIncomingHelp);
        } else {
            incomingSmsEnabledField = null; incomingSmsSenderField = null;
            incomingSmsCommandField = null; incomingSmsBitField = null; incomingSmsModeField = null;
            incomingSmsStrobeEnabledField = null; incomingSmsStrobeRegisterField = null; incomingSmsStrobeBitField = null;
        }

        final android.widget.CheckBox gatewayEnabledField;
        final android.widget.Spinner gatewayModeField, gatewaySourceFunctionField;
        final EditText gatewayCountField, gatewaySourceField, gatewayDestinationField,
                gatewayTriggerField, gatewayTriggerBitField;
        if (craftRegisterGateway || craftBitGateway) {
            gatewayEnabledField = new android.widget.CheckBox(this);
            gatewayEnabledField.setText("Разрешить выполнение шлюза в операторском режиме");
            gatewayEnabledField.setChecked(w.extraFields.optBoolean(craftBitGateway ? "craftBitGatewayEnabled" : "craftGatewayEnabled", false));
            form.addView(gatewayEnabledField);
            gatewayModeField = new android.widget.Spinner(this);
            gatewayModeField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"Постоянно", "По фронту 0→1", "По изменению источника"}));
            int originalMode = craftBitGateway
                    ? w.extraFields.optInt("craftBitGatewayTransferMode", w.extraFields.optInt("craftBitGatewayMode", 0))
                    : w.extraFields.optInt("craftGatewayTransferMode", w.extraFields.optInt("craftGatewayMode", 0));
            gatewayModeField.setSelection(Math.max(0, Math.min(2, originalMode)));
            form.addView(gatewayModeField);
            if (craftBitGateway) {
                gatewaySourceFunctionField = new android.widget.Spinner(this);
                gatewaySourceFunctionField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                        android.R.layout.simple_spinner_dropdown_item, new String[]{"FC01 — Coils", "FC02 — Discrete Inputs"}));
                gatewaySourceFunctionField.setSelection(w.extraFields.optInt("craftBitGatewaySourceFunction", 1) == 2 ? 1 : 0);
                form.addView(gatewaySourceFunctionField);
            } else gatewaySourceFunctionField = null;
            int maxGatewayCount = craftBitGateway ? 16 : 99;
            gatewayCountField = propertyField(Integer.toString(Math.max(1,
                    Math.min(maxGatewayCount, w.extraFields.optInt(craftBitGateway ? "craftBitGatewayCount" : "craftGatewayCount", 1)))), InputType.TYPE_CLASS_NUMBER);
            gatewayCountField.setHint(craftBitGateway ? "Количество бит (1–16; по изменению — 1)" : "Количество регистров (1–99; по изменению — 1)"); form.addView(gatewayCountField);
            gatewaySourceField = propertyField(w.extraFields.optString(craftBitGateway ? "craftBitGatewaySource" : "craftGatewaySource", ""),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            gatewaySourceField.setHint(craftBitGateway ? "Источник: tcp://192.168.1.10:502/1/0 (адрес 0-based)" : "Источник: tcp://192.168.1.10:502/1/40001 (FC03/04) или system://register/10"); form.addView(gatewaySourceField);
            gatewayDestinationField = propertyField(w.extraFields.optString(craftBitGateway ? "craftBitGatewayDestination" : "craftGatewayDestination", ""),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            gatewayDestinationField.setHint(craftBitGateway ? "Coil-приёмник: tcp://192.168.1.11:502/1/0" : "Приёмник: tcp://192.168.1.11:502/1/40001 (holding)"); form.addView(gatewayDestinationField);
            gatewayTriggerField = propertyField(w.extraFields.optString(craftBitGateway ? "craftBitGatewayTrigger" : "craftGatewayTrigger", ""),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            gatewayTriggerField.setHint("Триггер для фронта: tcp://192.168.1.10:502/1/40002"); form.addView(gatewayTriggerField);
            gatewayTriggerBitField = propertyField(Integer.toString(Math.max(0,
                    Math.min(15, w.extraFields.optInt(craftBitGateway ? "craftBitGatewayTriggerBit" : "craftGatewayTriggerBit",
                            craftBitGateway ? 0 : w.extraFields.optInt("craftGatewayBitMode", 0))))),
                    InputType.TYPE_CLASS_NUMBER);
            gatewayTriggerBitField.setHint("Бит триггера (0–15)"); form.addView(gatewayTriggerBitField);
            TextView gatewayHelp = new TextView(this);
            gatewayHelp.setText(craftBitGateway
                    ? "У исходного проекта адреса устройств заданы внутренними индексами; укажи TCP-адреса вручную. Источник читает FC01/FC02, приёмник пишет coils через FC05/FC15. Постоянно и по фронту передаётся 1–16 бит, по изменению — один. Адреса TCP здесь нумеруются с нуля. Системные регистры и RTU/Bluetooth пока не поддерживаются. Импортированный шлюз выключен."
                    : "У исходного проекта адреса устройств заданы внутренними индексами; они не переносятся автоматически. Укажи TCP-адрес приёмника вручную. Источник можно задать как TCP/FC03/04 или как system://register/10 (локальный банк Система, только чтение); режимы 0/1 копируют 1–99 регистров, по изменению — один. Внутренние регистры 0–1999 доступны как источник; приёмник Система, RTU и Bluetooth пока не поддерживаются. Импортированный шлюз изначально выключен.");
            form.addView(gatewayHelp);
        } else {
            gatewayEnabledField = null; gatewayModeField = null; gatewaySourceFunctionField = null;
            gatewayCountField = null; gatewaySourceField = null; gatewayDestinationField = null;
            gatewayTriggerField = null; gatewayTriggerBitField = null;
        }

        final android.widget.CheckBox fbdInputField;
        if ("FBD Вход".equals(w.type)) {
            fbdInputField = new android.widget.CheckBox(this);
            fbdInputField.setText("Постоянное значение FBD-входа (1 / 0)");
            fbdInputField.setContentDescription("Значение входа FBD");
            fbdInputField.setChecked(w.inputValue);
            form.addView(fbdInputField);
        } else fbdInputField = null;

        final android.widget.CheckBox craftConstantModeField;
        final EditText craftConstantValueField;
        final EditText craftBitField;
        final android.widget.CheckBox craftSwapWordField;
        final android.widget.CheckBox craftSwapByteField;
        final EditText craftVariableNameField;
        final android.widget.Spinner craftVariableTypeField;
        final android.widget.CheckBox craftNamedConstantModeField;
        final EditText craftNamedConstantValueField;
        final android.widget.Spinner craftOrientationAxisField;
        if ("FBD CRAFT SENSOR ORIENTATION".equals(w.type)) {
            craftOrientationAxisField = new android.widget.Spinner(this);
            craftOrientationAxisField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"ORIENTATION_X", "ORIENTATION_Y", "ORIENTATION_Z"}));
            craftOrientationAxisField.setSelection(craftOrientationAxis(w) - 1);
            craftOrientationAxisField.setContentDescription("Ось датчика ориентации");
            form.addView(craftOrientationAxisField);
        } else craftOrientationAxisField = null;
        final EditText craftSunriseLatitudeField, craftSunriseLongitudeField;
        final android.widget.Spinner craftSunriseModeField;
        if ("FBD CRAFT SUNRISE".equals(w.type)) {
            TextView locationLabel = new TextView(this); locationLabel.setText("Координаты точки (широта / долгота)");
            form.addView(locationLabel);
            craftSunriseLatitudeField = propertyField(w.extraFields.has("latitude")
                    ? Double.toString(w.extraFields.optDouble("latitude")) : "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            craftSunriseLatitudeField.setHint("Широта: −90…90"); form.addView(craftSunriseLatitudeField);
            craftSunriseLongitudeField = propertyField(w.extraFields.has("longitude")
                    ? Double.toString(w.extraFields.optDouble("longitude")) : "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            craftSunriseLongitudeField.setHint("Долгота: −180…180"); form.addView(craftSunriseLongitudeField);
            craftSunriseModeField = new android.widget.Spinner(this);
            craftSunriseModeField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"SR — восход", "SS — закат"}));
            craftSunriseModeField.setSelection("SS".equals(craftSunriseMode(w)) ? 1 : 0);
            form.addView(craftSunriseModeField);
        } else {
            craftSunriseLatitudeField = null; craftSunriseLongitudeField = null; craftSunriseModeField = null;
        }
        final EditText craftOdTempHostField;
        final android.widget.Spinner craftOdTempTypeField, craftOdTempResponseField;
        if ("FBD CRAFT ODTEMP".equals(w.type)) {
            craftOdTempHostField = propertyField(w.extraFields.optString("ip", "10.10.0.1"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            craftOdTempHostField.setHint("IP устройства (частная сеть)"); form.addView(craftOdTempHostField);
            craftOdTempTypeField = new android.widget.Spinner(this);
            craftOdTempTypeField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"DT — одиночное значение", "T / влажность"}));
            craftOdTempTypeField.setSelection(w.extraFields.optInt("craftOdTempType", w.extraFields.optInt("typeSensor", 1)) == 2 ? 1 : 0);
            form.addView(craftOdTempTypeField);
            craftOdTempResponseField = new android.widget.Spinner(this);
            craftOdTempResponseField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"Температура (t)", "Влажность (h)"}));
            craftOdTempResponseField.setSelection(w.extraFields.optInt("craftOdTempResponse", w.extraFields.optInt("typeResponse", 1)) == 2 ? 1 : 0);
            form.addView(craftOdTempResponseField);
        } else {
            craftOdTempHostField = null; craftOdTempTypeField = null; craftOdTempResponseField = null;
        }
        final EditText craftMqttTopicField;
        final android.widget.Spinner craftMqttTypeField, craftMqttServerField;
        final android.widget.CheckBox craftMqttRetainField;
        if (isCraftMqttSubscriber(w.type) || isCraftMqttPublisher(w.type)) {
            boolean subscriber = isCraftMqttSubscriber(w.type);
            craftMqttTopicField = propertyField(w.extraFields.optString("craftMqttTopic", ""),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            craftMqttTopicField.setHint("MQTT topic"); form.addView(craftMqttTopicField);
            craftMqttTypeField = new android.widget.Spinner(this);
            craftMqttTypeField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"BOOL", "INT", "FLOAT"}));
            craftMqttTypeField.setSelection(mqttType(w) - 1); form.addView(craftMqttTypeField);
            craftMqttServerField = new android.widget.Spinner(this);
            craftMqttServerField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"MQTT сервер 1", "MQTT сервер 2"}));
            craftMqttServerField.setSelection(mqttServer(w) - 1); form.addView(craftMqttServerField);
            craftMqttRetainField = subscriber ? null : new android.widget.CheckBox(this);
            if (craftMqttRetainField != null) {
                craftMqttRetainField.setText("Retain");
                craftMqttRetainField.setChecked(w.extraFields.optBoolean("craftMqttRetain", false));
                form.addView(craftMqttRetainField);
            }
        } else {
            craftMqttTopicField = null; craftMqttTypeField = null;
            craftMqttServerField = null; craftMqttRetainField = null;
        }
        if (isCraftNamedVariableSource(w.type) || isCraftNamedVariableSink(w.type)) {
            boolean source = isCraftNamedVariableSource(w.type);
            craftVariableNameField = propertyField(w.extraFields.optString("craftVariableName",
                    w.extraFields.optString("name", "")), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            craftVariableNameField.setHint("Имя общей переменной");
            form.addView(craftVariableNameField);
            craftVariableTypeField = new android.widget.Spinner(this);
            craftVariableTypeField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"BOOL", "INT32", "FLOAT32"}));
            int oldKind = w.extraFields.optInt("typeVar", w.type.endsWith(" BOOL") ? 1 : w.type.endsWith(" FLOAT") ? 3 : 2);
            craftVariableTypeField.setSelection(oldKind >= 1 && oldKind <= 3 ? oldKind - 1 : 1);
            form.addView(craftVariableTypeField);
            craftNamedConstantModeField = source ? new android.widget.CheckBox(this) : null;
            craftNamedConstantValueField = source ? propertyField(w.extraFields.optString("craftNamedConstant", "0"),
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL) : null;
            if (source) {
                craftNamedConstantModeField.setText("Использовать константу вместо имени");
                craftNamedConstantModeField.setChecked(w.extraFields.optBoolean("craftNamedConstantMode",
                        w.extraFields.optBoolean("isConstant", false)));
                craftNamedConstantValueField.setHint("Значение константы");
                form.addView(craftNamedConstantModeField); form.addView(craftNamedConstantValueField);
            }
        } else {
            craftVariableNameField = null; craftVariableTypeField = null;
            craftNamedConstantModeField = null; craftNamedConstantValueField = null;
        }
        if (isCraftRegisterSource(w.type) || isCraftRegisterSink(w.type)) {
            boolean source = isCraftRegisterSource(w.type);
            craftConstantModeField = source ? new android.widget.CheckBox(this) : null;
            craftConstantValueField = source ? propertyField(w.extraFields.optString("craftVarConstant", "0"),
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL) : null;
            if (source) {
                craftConstantModeField.setText("Постоянное значение вместо регистра");
                craftConstantModeField.setChecked(w.extraFields.optBoolean("craftVarConstantMode", false));
                form.addView(craftConstantModeField);
                craftConstantValueField.setHint("Значение BOOL / INT / FLOAT");
                form.addView(craftConstantValueField);
            }
            craftBitField = isCraftRegisterBoolean(w.type)
                    ? propertyField(Integer.toString(w.extraFields.optInt("craftVarBit", 0)), InputType.TYPE_CLASS_NUMBER)
                    : null;
            if (craftBitField != null) {
                craftBitField.setHint("Бит регистра (0–15)");
                form.addView(craftBitField);
            }
            craftSwapWordField = isCraftRegisterFloat(w.type) && !source ? new android.widget.CheckBox(this) : null;
            craftSwapByteField = isCraftRegisterFloat(w.type) && !source ? new android.widget.CheckBox(this) : null;
            if (craftSwapWordField != null) {
                craftSwapWordField.setText("Поменять слова Float32");
                craftSwapWordField.setChecked(w.extraFields.optBoolean("craftSwapWord", false));
                craftSwapByteField.setText("Поменять байты внутри слов");
                craftSwapByteField.setChecked(w.extraFields.optBoolean("craftSwapByte", false));
                form.addView(craftSwapWordField); form.addView(craftSwapByteField);
            }
        } else {
            craftConstantModeField = null; craftConstantValueField = null; craftBitField = null;
            craftSwapWordField = null; craftSwapByteField = null;
        }

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
        final android.widget.Spinner soundPresetField;
        if ("Звуковой сигнал".equals(w.type)) {
            TextView soundHeader = new TextView(this);
            soundHeader.setText("Параметры звукового сигнала");

            soundHeader.setPadding(0, dp(10), 0, 0);
            form.addView(soundHeader);
            soundThresholdField = propertyField(Double.toString(w.soundThreshold), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            soundThresholdField.setHint("Порог включения (обычно 1)");
            form.addView(soundThresholdField);
            soundPresetField = new android.widget.Spinner(this);
            soundPresetField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"Пользовательский файл", "Сигнал Craft 1", "Сигнал Craft 2",
                            "Сигнал Craft 3", "Сигнал Craft 4", "Сигнал Craft 5"}));
            soundPresetField.setSelection(Math.max(0, Math.min(5, w.craftAlarmSoundIndex)));
            form.addView(soundPresetField);
            TextView soundPresetHelp = new TextView(this);
            soundPresetHelp.setText("Выбери один из пяти сигналов Craft или пользовательский аудиофайл.");
            form.addView(soundPresetHelp);
            soundFilePathField = propertyField(w.soundFilePath, InputType.TYPE_CLASS_TEXT);
            soundFilePathField.setHint("Файл / content URI или 'Аларм'");
            form.addView(soundFilePathField);
            addMediaPicker(form, soundFilePathField, "audio/*");
        } else { soundThresholdField = null; soundFilePathField = null; soundPresetField = null; }

        final android.widget.Spinner targetScreenIndexField;
        final EditText targetUrlField;
        final android.widget.CheckBox targetVisibleInWorkField;
        if ("Переход на экран".equals(w.type)) {
            TextView screenNavHeader = new TextView(this);
            screenNavHeader.setText("Куда перейти");
            screenNavHeader.setPadding(0, dp(10), 0, 0);
            form.addView(screenNavHeader);
            java.util.ArrayList<String> screenChoices = new java.util.ArrayList<>();
            screenChoices.add("— выбери экран —");
            for (int screen = 0; screen < screens.size(); screen++) {
                String screenName = screens.get(screen).name == null || screens.get(screen).name.trim().isEmpty()
                        ? "Экран " + (screen + 1) : screens.get(screen).name;
                screenChoices.add((screen + 1) + " — " + screenName);
            }
            targetScreenIndexField = new android.widget.Spinner(this);
            targetScreenIndexField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, screenChoices));
            int selectedTarget = w.targetScreenIndex >= 0 && w.targetScreenIndex < screens.size()
                    ? w.targetScreenIndex + 1 : 0;
            targetScreenIndexField.setSelection(selectedTarget);
            form.addView(targetScreenIndexField);
            targetUrlField = propertyField(w.targetUrl, InputType.TYPE_CLASS_TEXT);
            targetUrlField.setHint("Внешний URL вместо экрана (необязательно)");
            form.addView(targetUrlField);
            targetVisibleInWorkField = new android.widget.CheckBox(this);
            targetVisibleInWorkField.setText("Показывать в рабочем режиме");
            targetVisibleInWorkField.setChecked(w.extraFields.optBoolean("craftButtonVisibleInWork", true));
            form.addView(targetVisibleInWorkField);
        } else { targetScreenIndexField = null; targetUrlField = null; targetVisibleInWorkField = null; }

        final EditText[] imagePathFields = new EditText[w.imagePathStates.length];
        final Spinner imageSelectionModeField;
        if ("Многопозиционное фото".equals(w.type)) {
            TextView imgHeader = new TextView(this);
            imgHeader.setText("Пути к изображениям состояний (0–15)");
            imgHeader.setPadding(0, dp(10), 0, 0);
            form.addView(imgHeader);
            TextView imgModeHeader = new TextView(this);
            imgModeHeader.setText("Выбор состояния по значению регистра");
            imgModeHeader.setPadding(0, dp(10), 0, 0);
            form.addView(imgModeHeader);
            imageSelectionModeField = new Spinner(this);
            imageSelectionModeField.setContentDescription("Режим выбора картинки Craft");
            imageSelectionModeField.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"Номер картинки: 0–15", "Битовая маска: первый установленный бит"}));
            JSONObject imageCraftOther = w.extraFields.optJSONObject("craftOther");
            imageSelectionModeField.setSelection(imageCraftOther == null
                    || imageCraftOther.optInt("typeCommand", 0) == 0 ? 0 : 1);
            form.addView(imageSelectionModeField);
            TextView imgBindingHelp = new TextView(this);
            imgBindingHelp.setText("Чтобы включить выбор по устройству, задай явную TCP-привязку выше: "
                    + "tcp://192.168.1.10:502/1/40001. Без активного значения показывается картинка 0. "
                    + "Исходная привязка Craft при импорте не включается автоматически.");
            imgBindingHelp.setPadding(0, dp(4), 0, dp(4));
            form.addView(imgBindingHelp);
            for (int k = 0; k < imagePathFields.length; k++) {
                imagePathFields[k] = propertyField(w.imagePathStates[k], InputType.TYPE_CLASS_TEXT);
                imagePathFields[k].setHint("Изображение " + k + " (файл / URI)");
                form.addView(imagePathFields[k]);
                addMediaPicker(form, imagePathFields[k], "image/*");
            }
        } else imageSelectionModeField = null;

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

        final EditText[] craftVoicePhraseFields = new EditText[CraftVoiceRecognitionFields.PHRASE_COUNT];
        if ("Распознавание речи Craft".equals(w.type)) {
            JSONObject sourceOther = w.extraFields.optJSONObject("craftOther");
            String[] phrases = CraftVoiceRecognitionFields.read(sourceOther);
            TextView voiceHeader = new TextView(this);
            voiceHeader.setText("Исходные фразы Craft (0–15); распознавание пока выключено");
            voiceHeader.setPadding(0, dp(10), 0, 0);
            form.addView(voiceHeader);
            for (int k = 0; k < craftVoicePhraseFields.length; k++) {
                craftVoicePhraseFields[k] = propertyField(phrases[k], InputType.TYPE_CLASS_TEXT);
                craftVoicePhraseFields[k].setHint("Фраза Craft " + k);
                form.addView(craftVoicePhraseFields[k]);
            }
        }

        final EditText timerPresetField;
        final android.widget.Spinner timerUnitSelector;
        final android.widget.Spinner timerModeSelector;
        final android.widget.Spinner triggerModeSelector;
        if ("FBD CRAFT T".equals(w.type)) {
            TextView triggerLabel = new TextView(this);
            triggerLabel.setText("Режим исходного Craft trigger");
            form.addView(triggerLabel);
            triggerModeSelector = new android.widget.Spinner(this);
            triggerModeSelector.setContentDescription("Режим триггера Craft");
            triggerModeSelector.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"T", "R", "F"}));
            String mode = w.extraFields.optString("craftTriggerMode", "T");
            triggerModeSelector.setSelection("R".equals(mode) ? 1 : "F".equals(mode) ? 2 : 0);
            form.addView(triggerModeSelector);
        } else triggerModeSelector = null;
        if ("FBD Таймер".equals(w.type) || "FBD TP".equals(w.type)
                || "FBD TOF".equals(w.type) || "FBD CRAFT BLINK".equals(w.type)
                || "FBD CRAFT TIMER".equals(w.type)) {
            TextView timerModeLabel = new TextView(this);
            boolean originalBlink = "FBD CRAFT BLINK".equals(w.type);
            boolean originalCraftTimer = "FBD CRAFT TIMER".equals(w.type);
            timerModeLabel.setText(originalBlink ? "Режим Craft: BLINK"
                    : originalCraftTimer ? "Режим исходного Craft FB_TIMER"
                    : "Режим таймера (TON / TOF / TP)");
            form.addView(timerModeLabel);
            timerModeSelector = new android.widget.Spinner(this);
            timerModeSelector.setContentDescription("Режим таймера FBD");
            timerModeSelector.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item,
                    originalBlink ? new String[]{"BLINK"} : originalCraftTimer
                            ? new String[]{"TON", "TOF", "TP", "BLINK"} : FbdDurationUnits.modes()));
            String initialMode = originalCraftTimer ? w.extraFields.optString("craftTimerMode", "TON")
                    : FbdDurationUnits.modeForType(w.type);
            timerModeSelector.setSelection("TOF".equals(initialMode) ? 1 : "TP".equals(initialMode) ? 2 : "BLINK".equals(initialMode) && originalCraftTimer ? 3 : 0);
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
        if ("FBD Сравнение".equals(w.type)
                || "FBD CRAFT COMPARE".equals(w.type)
                || "FBD CRAFT COMPARE FLOAT".equals(w.type)
                || "FBD CRAFT ARITHMETIC INT".equals(w.type)
                || "FBD CRAFT ARITHMETIC FLOAT".equals(w.type)) {
            TextView compareLabel = new TextView(this);
            compareLabel.setText("FBD CRAFT COMPARE".equals(w.type)
                    ? "Craft INT32: оператор >, <, <=, >=, ==, !=; входы IN1/IN2"
                    : "FBD CRAFT COMPARE FLOAT".equals(w.type)
                    ? "Craft FLOAT32: оператор >, <, <=, >=, ==, !=; входы IN1/IN2"
                    : ("FBD CRAFT ARITHMETIC INT".equals(w.type)                            || "FBD CRAFT ARITHMETIC FLOAT".equals(w.type))
                    ? "Craft: оператор +, −, ×, ÷; входы IN1/IN2"
                    : "Числовое сравнение A / оператор / B");
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
            boolean sourceThresholdGauge = "Шкала / Стрелка".equals(w.type)
                    && w.extraFields.optInt("craftComponentId", -1) == 12;
            boolean sourceGraduatedGauge = "Шкала / Стрелка".equals(w.type)
                    && w.extraFields.optInt("craftComponentId", -1) == 24;
            TextView rangeLabel = new TextView(this);
            rangeLabel.setText(sourceThresholdGauge ? "Максимум исходной шкалы Craft"
                    : sourceGraduatedGauge ? "Диапазон Craft minGauge / maxGauge"
                    : "Диапазон шкалы (минимум / максимум)");
            rangeLabel.setPadding(0, dp(10), 0, 0);
            form.addView(rangeLabel);
            if (sourceThresholdGauge) {
                minValField = null;
            } else {
                int minInputType = sourceGraduatedGauge
                        ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                        : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL;
                minValField = propertyField(Double.toString(w.minValue), minInputType);
                minValField.setHint(sourceGraduatedGauge ? "Минимум шкалы (целое число)" : "Минимум (например 0)");
                form.addView(minValField);
            }
            maxValField = propertyField(Double.toString(w.maxValue), sourceThresholdGauge || sourceGraduatedGauge
                    ? InputType.TYPE_CLASS_NUMBER
                    : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            maxValField.setHint(sourceThresholdGauge ? "Максимум (целое число от 1)"
                    : sourceGraduatedGauge ? "Максимум шкалы (целое число)" : "Максимум (например 100)");
            form.addView(maxValField);
        } else {
            minValField = null;
            maxValField = null;
        }

        final EditText trendIntervalField;
        final EditText trendPointCountField;
        final EditText trendChannelCountField;
        final EditText trendTypeField;
        final EditText trendLabelsColorField;
        final EditText trendBackgroundColorField;
        final EditText[] trendSeriesNameFields;
        if ("График / Тренд".equals(w.type)
                && w.extraFields.optInt("craftComponentId", -1) == 14) {
            TextView trendLabel = new TextView(this);
            trendLabel.setText("Настройки графика Craft ID14");
            trendLabel.setPadding(0, dp(8), 0, 0);
            form.addView(trendLabel);
            trendIntervalField = propertyField(Integer.toString(
                    w.extraFields.optInt("craftTrendIntervalMs", 500)), InputType.TYPE_CLASS_NUMBER);
            trendIntervalField.setHint("Период выборки (100–60000 мс)");
            trendIntervalField.setContentDescription("Период выборки графика, миллисекунды");
            form.addView(trendIntervalField);
            trendPointCountField = propertyField(Integer.toString(
                    w.extraFields.optInt("craftTrendMaxPoints", 300)), InputType.TYPE_CLASS_NUMBER);
            trendPointCountField.setHint("Максимум точек истории (1–2000)");
            trendPointCountField.setContentDescription("Максимум точек графика");
            form.addView(trendPointCountField);
            trendChannelCountField = propertyField(Integer.toString(
                    w.extraFields.optInt("craftTrendChannelCount", 1)), InputType.TYPE_CLASS_NUMBER);
            trendChannelCountField.setHint("Число рядов (1–4)");
            trendChannelCountField.setContentDescription("Число рядов графика");
            form.addView(trendChannelCountField);
            trendTypeField = propertyField(Integer.toString(
                    w.extraFields.optInt("craftTrendType", 1)), InputType.TYPE_CLASS_NUMBER);
            trendTypeField.setHint("Тип: 0 UINT16, 1 INT16, 2 FLOAT32");
            trendTypeField.setContentDescription("Тип данных графика");
            form.addView(trendTypeField);
            trendLabelsColorField = propertyField(String.format(java.util.Locale.ROOT, "#%08X",
                    w.extraFields.optInt("craftTrendLabelsColor", Color.WHITE)), InputType.TYPE_CLASS_TEXT);
            trendLabelsColorField.setHint("Цвет подписей (ARGB)");
            trendLabelsColorField.setContentDescription("Цвет подписей графика");
            form.addView(trendLabelsColorField);
            trendBackgroundColorField = propertyField(String.format(java.util.Locale.ROOT, "#%08X",
                    w.extraFields.optInt("craftTrendBackgroundColor", Color.BLACK)), InputType.TYPE_CLASS_TEXT);
            trendBackgroundColorField.setHint("Цвет фона (ARGB)");
            trendBackgroundColorField.setContentDescription("Цвет фона графика");
            form.addView(trendBackgroundColorField);
            trendSeriesNameFields = new EditText[4];
            JSONArray existingNames = w.extraFields.optJSONArray("craftTrendSeriesNames");
            for (int channel = 0; channel < trendSeriesNameFields.length; channel++) {
                trendSeriesNameFields[channel] = propertyField(
                        existingNames == null ? Integer.toString(channel + 1)
                                : existingNames.optString(channel, Integer.toString(channel + 1)),
                        InputType.TYPE_CLASS_TEXT);
                trendSeriesNameFields[channel].setHint("Название ряда " + (channel + 1));
                trendSeriesNameFields[channel].setContentDescription("Название ряда " + (channel + 1));
                form.addView(trendSeriesNameFields[channel]);
            }
        } else {
            trendIntervalField = null;
            trendPointCountField = null;
            trendChannelCountField = null;
            trendTypeField = null;
            trendLabelsColorField = null;
            trendBackgroundColorField = null;
            trendSeriesNameFields = null;
        }

        final android.widget.CheckBox gaugeShowTextField;
        final EditText gaugeTextSizeField;
        final EditText gaugeTextColorField;
        final EditText gaugeInColorField;
        final EditText gaugeOutColorField;
        final EditText gaugeCenterColorField;
        final EditText gaugeFirstColorPointField;
        final EditText gaugeSecondColorPointField;
        if ("Шкала / Стрелка".equals(w.type)
                && w.extraFields.optInt("craftComponentId", -1) == 12) {
            TextView gaugePropertiesLabel = new TextView(this);
            gaugePropertiesLabel.setText("Настройки пороговой шкалы Craft");
            gaugePropertiesLabel.setPadding(0, dp(10), 0, 0);
            form.addView(gaugePropertiesLabel);
            gaugeShowTextField = new android.widget.CheckBox(this);
            gaugeShowTextField.setText("Показывать подписи делений");
            gaugeShowTextField.setChecked(w.extraFields.optBoolean("craftGaugeShowText", true));
            form.addView(gaugeShowTextField);
            gaugeTextSizeField = propertyField(Integer.toString(w.extraFields.optInt("craftGaugeTextSize", 12)), InputType.TYPE_CLASS_NUMBER);
            gaugeTextSizeField.setHint("Размер подписей (1–256 px)"); form.addView(gaugeTextSizeField);
            gaugeTextColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X",
                    0xFFFFFF & w.extraFields.optInt("craftGaugeTextColor", Color.BLACK)), InputType.TYPE_CLASS_TEXT);
            gaugeTextColorField.setHint("Цвет подписей: #RRGGBB"); form.addView(gaugeTextColorField);
            gaugeInColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X",
                    0xFFFFFF & w.extraFields.optInt("craftGaugeInColor", Color.rgb(136, 136, 136))), InputType.TYPE_CLASS_TEXT);
            gaugeInColorField.setHint("Цвет внутренней дуги: #RRGGBB"); form.addView(gaugeInColorField);
            gaugeOutColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X",
                    0xFFFFFF & w.extraFields.optInt("craftGaugeOutColor", Color.parseColor("#FF277454"))), InputType.TYPE_CLASS_TEXT);
            gaugeOutColorField.setHint("Цвет внешней дуги: #RRGGBB"); form.addView(gaugeOutColorField);
            gaugeCenterColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X",
                    0xFFFFFF & w.extraFields.optInt("craftGaugeCenterColor", Color.BLACK)), InputType.TYPE_CLASS_TEXT);
            gaugeCenterColorField.setHint("Цвет центра: #RRGGBB"); form.addView(gaugeCenterColorField);
            gaugeFirstColorPointField = propertyField(Integer.toString(
                    w.extraFields.optInt("craftGaugeFirstColorPoint", 0)), InputType.TYPE_CLASS_NUMBER);
            gaugeFirstColorPointField.setHint("Первая граница цветовой зоны"); form.addView(gaugeFirstColorPointField);
            gaugeSecondColorPointField = propertyField(Integer.toString(
                    w.extraFields.optInt("craftGaugeSecondColorPoint", 0)), InputType.TYPE_CLASS_NUMBER);
            gaugeSecondColorPointField.setHint("Вторая граница цветовой зоны"); form.addView(gaugeSecondColorPointField);
        } else {
            gaugeShowTextField = null;
            gaugeTextSizeField = null;
            gaugeTextColorField = null;
            gaugeInColorField = null;
            gaugeOutColorField = null;
            gaugeCenterColorField = null;
            gaugeFirstColorPointField = null;
            gaugeSecondColorPointField = null;
        }

        final EditText graduatedGaugeMinField;
        final EditText graduatedGaugeMaxField;
        final EditText graduatedGaugeCountField;
        final EditText graduatedGaugeTextSizeField;
        final EditText graduatedGaugeTextValueColorField;
        final EditText graduatedGaugeTextScaleColorField;
        final EditText graduatedGaugeCenterColorField;
        final EditText graduatedGaugeIndicatorColorField;
        final EditText graduatedGaugeMarkColorField;
        final EditText graduatedGaugeLowPercentField;
        final EditText graduatedGaugeMediumPercentField;
        final android.widget.CheckBox graduatedGaugeHideBackgroundField;
        final Spinner graduatedGaugeIndicatorField;
        if ("Шкала / Стрелка".equals(w.type)
                && w.extraFields.optInt("craftComponentId", -1) == 24) {
            TextView graduatedLabel = new TextView(this);
            graduatedLabel.setText("Свойства градуированной шкалы Craft");
            graduatedLabel.setPadding(0, dp(10), 0, 0);
            form.addView(graduatedLabel);
            graduatedGaugeMinField = minValField;
            graduatedGaugeMaxField = maxValField;
            graduatedGaugeCountField = propertyField(Integer.toString(
                    w.extraFields.optInt("craftGaugeScaleCount", 6)), InputType.TYPE_CLASS_NUMBER);
            graduatedGaugeCountField.setHint("Число делений (0–64)");
            form.addView(graduatedGaugeCountField);
            graduatedGaugeTextSizeField = propertyField(Integer.toString(
                    w.extraFields.optInt("craftGaugeTextSize", 20)), InputType.TYPE_CLASS_NUMBER);
            graduatedGaugeTextSizeField.setHint("Размер текста (1–256 px)");
            form.addView(graduatedGaugeTextSizeField);
            graduatedGaugeTextValueColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X",
                    0xFFFFFF & w.extraFields.optInt("craftGaugeTextValueColor", Color.BLACK)), InputType.TYPE_CLASS_TEXT);
            graduatedGaugeTextValueColorField.setHint("Цвет текущего значения");
            form.addView(graduatedGaugeTextValueColorField);
            graduatedGaugeTextScaleColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X",
                    0xFFFFFF & w.extraFields.optInt("craftGaugeTextScaleColor", Color.BLACK)), InputType.TYPE_CLASS_TEXT);
            graduatedGaugeTextScaleColorField.setHint("Цвет подписей шкалы");
            form.addView(graduatedGaugeTextScaleColorField);
            graduatedGaugeCenterColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X",
                    0xFFFFFF & w.extraFields.optInt("craftGaugeCenterColor", Color.parseColor("#BFBFBF"))), InputType.TYPE_CLASS_TEXT);
            graduatedGaugeCenterColorField.setHint("Цвет центральной окружности");
            form.addView(graduatedGaugeCenterColorField);
            graduatedGaugeIndicatorColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X",
                    0xFFFFFF & w.extraFields.optInt("craftGaugeIndicatorColor", Color.BLUE)), InputType.TYPE_CLASS_TEXT);
            graduatedGaugeIndicatorColorField.setHint("Цвет указателя");
            form.addView(graduatedGaugeIndicatorColorField);
            graduatedGaugeMarkColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X",
                    0xFFFFFF & w.extraFields.optInt("craftGaugeMarkColor", Color.BLACK)), InputType.TYPE_CLASS_TEXT);
            graduatedGaugeMarkColorField.setHint("Цвет меток");
            form.addView(graduatedGaugeMarkColorField);
            graduatedGaugeLowPercentField = propertyField(Integer.toString(
                    w.extraFields.optInt("craftGaugeLowPercent", 75)), InputType.TYPE_CLASS_NUMBER);
            graduatedGaugeLowPercentField.setHint("Нижний порог цветовой зоны, %");
            form.addView(graduatedGaugeLowPercentField);
            graduatedGaugeMediumPercentField = propertyField(Integer.toString(
                    w.extraFields.optInt("craftGaugeMediumPercent", 90)), InputType.TYPE_CLASS_NUMBER);
            graduatedGaugeMediumPercentField.setHint("Верхний порог цветовой зоны, %");
            form.addView(graduatedGaugeMediumPercentField);
            graduatedGaugeHideBackgroundField = new android.widget.CheckBox(this);
            graduatedGaugeHideBackgroundField.setText("Скрывать фон шкалы");
            graduatedGaugeHideBackgroundField.setChecked(
                    w.extraFields.optBoolean("craftGaugeHideBackground", false));
            form.addView(graduatedGaugeHideBackgroundField);
            graduatedGaugeIndicatorField = new Spinner(this);
            String[] indicatorNames = new String[]{"HalfLineIndicator", "KiteIndicator",
                    "LineIndicator", "NeedleIndicator", "NormalIndicator", "NormalSmallIndicator",
                    "QuarterLineIndicator", "SpindleIndicator", "TriangleIndicator"};
            graduatedGaugeIndicatorField.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, indicatorNames));
            ((ArrayAdapter<?>) graduatedGaugeIndicatorField.getAdapter()).setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item);
            String currentIndicator = w.extraFields.optString("craftGaugeIndicatorName", "NormalIndicator");
            for (int i = 0; i < indicatorNames.length; i++)
                if (indicatorNames[i].equals(currentIndicator)) graduatedGaugeIndicatorField.setSelection(i);
            form.addView(graduatedGaugeIndicatorField);
        } else {
            graduatedGaugeMinField = null; graduatedGaugeMaxField = null;
            graduatedGaugeCountField = null; graduatedGaugeTextSizeField = null;
            graduatedGaugeTextValueColorField = null; graduatedGaugeTextScaleColorField = null;
            graduatedGaugeCenterColorField = null; graduatedGaugeIndicatorColorField = null;
            graduatedGaugeMarkColorField = null; graduatedGaugeLowPercentField = null;
            graduatedGaugeMediumPercentField = null; graduatedGaugeHideBackgroundField = null;
            graduatedGaugeIndicatorField = null;
        }

        final android.widget.CheckBox progressShowTextField;
        final EditText progressTextSizeField;
        final EditText progressTextColorField;
        final android.widget.CheckBox progressShowScaleField;
        final EditText progressColorField;
        final EditText progressBackgroundColorField;
        if ("Прогресс-бар".equals(w.type)
                && w.extraFields.optInt("craftComponentId", -1) == 11) {
            TextView progressPropertiesLabel = new TextView(this);
            progressPropertiesLabel.setText("Оформление шкалы Craft");
            progressPropertiesLabel.setPadding(0, dp(10), 0, 0);
            form.addView(progressPropertiesLabel);
            progressShowTextField = new android.widget.CheckBox(this);
            progressShowTextField.setText("Показывать значение");
            progressShowTextField.setChecked(w.extraFields.optBoolean("craftProgressShowText", false));
            form.addView(progressShowTextField);
            progressTextSizeField = propertyField(Integer.toString(
                    w.extraFields.optInt("craftProgressTextSize", 50)), InputType.TYPE_CLASS_NUMBER);
            progressTextSizeField.setHint("Размер значения, 1–256 px");
            progressTextSizeField.setContentDescription("Размер текста индикатора прогресса");
            form.addView(progressTextSizeField);
            progressTextColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X",
                    0xFFFFFF & w.extraFields.optInt("craftProgressTextColor", Color.BLUE)), InputType.TYPE_CLASS_TEXT);
            progressTextColorField.setHint("Цвет значения: #RRGGBB");
            progressTextColorField.setContentDescription("Цвет текста индикатора прогресса");
            form.addView(progressTextColorField);
            progressShowScaleField = new android.widget.CheckBox(this);
            progressShowScaleField.setText("Показывать деления шкалы");
            progressShowScaleField.setChecked(w.extraFields.optBoolean("craftProgressShowScale", true));
            form.addView(progressShowScaleField);
            progressColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X",
                    0xFFFFFF & w.extraFields.optInt("craftProgressColor", Color.rgb(46, 236, 0))), InputType.TYPE_CLASS_TEXT);
            progressColorField.setHint("Цвет заполнения: #RRGGBB");
            progressColorField.setContentDescription("Цвет заполнения индикатора прогресса");
            form.addView(progressColorField);
            progressBackgroundColorField = propertyField(String.format(java.util.Locale.ROOT, "#%06X",
                    0xFFFFFF & w.extraFields.optInt("craftProgressBackgroundColor", Color.rgb(156, 156, 155))), InputType.TYPE_CLASS_TEXT);
            progressBackgroundColorField.setHint("Цвет дорожки: #RRGGBB");
            progressBackgroundColorField.setContentDescription("Цвет фона индикатора прогресса");
            form.addView(progressBackgroundColorField);
        } else {
            progressShowTextField = null;
            progressTextSizeField = null;
            progressTextColorField = null;
            progressShowScaleField = null;
            progressColorField = null;
            progressBackgroundColorField = null;
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
                .setTitle("Свойства: " + ("FBD CRAFT TIMER".equals(w.type) ? "FBD Таймер" : w.type))
                .setView(propertyScroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Применить", null).create();
        activePropertiesDialog = properties;
        activePropertiesForm = form;
        pendingMediaCopyCount = 0;
        properties.setOnDismissListener(dismissed -> {
            if (activePropertiesDialog == properties) {
                activePropertiesDialog = null;
                activePropertiesForm = null;
            }
            cleanupPendingMediaCopies(properties);
        });
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
                    if (gatewayEnabledField != null) {
                        boolean enabled = gatewayEnabledField.isChecked();
                        int mode = gatewayModeField.getSelectedItemPosition();
                        int count, triggerBit, sourceFunction = gatewaySourceFunctionField == null ? 3
                                : gatewaySourceFunctionField.getSelectedItemPosition() + 1;
                        try {
                            count = Integer.parseInt(gatewayCountField.getText().toString().trim());
                            triggerBit = Integer.parseInt(gatewayTriggerBitField.getText().toString().trim());
                        } catch (NumberFormatException invalid) {
                            Toast.makeText(this, "Укажи количество значений и бит триггера числами", Toast.LENGTH_LONG).show(); return;
                        }
                        String source = gatewaySourceField.getText().toString().trim();
                        String destination = gatewayDestinationField.getText().toString().trim();
                        String trigger = gatewayTriggerField.getText().toString().trim();
                        if (enabled) {
                            try {
                                if (craftBitGateway) CraftBitGateway.validate(true, mode, count, sourceFunction,
                                        source, destination, trigger, triggerBit);
                                else CraftRegisterGateway.validate(true, mode, count, source, destination, trigger, triggerBit);
                            }
                            catch (IllegalArgumentException invalid) {
                                Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show(); return;
                            }
                        } else if (count < 1 || count > (craftBitGateway ? 16 : 99) || triggerBit < 0 || triggerBit > 15) {
                            Toast.makeText(this, "Количество: " + (craftBitGateway ? "1–16 бит" : "1–99 регистров") + "; бит триггера: 0–15", Toast.LENGTH_LONG).show(); return;
                        }
                        try {
                            if (craftBitGateway) {
                                w.extraFields.put("craftBitGatewayEnabled", enabled);
                                w.extraFields.put("craftBitGatewayTransferMode", mode);
                                w.extraFields.put("craftBitGatewayCount", count);
                                w.extraFields.put("craftBitGatewaySourceFunction", sourceFunction);
                                w.extraFields.put("craftBitGatewaySource", source);
                                w.extraFields.put("craftBitGatewayDestination", destination);
                                w.extraFields.put("craftBitGatewayTrigger", trigger);
                                w.extraFields.put("craftBitGatewayTriggerBit", triggerBit);
                            } else {
                                w.extraFields.put("craftGatewayEnabled", enabled);
                                w.extraFields.put("craftGatewayTransferMode", mode);
                                w.extraFields.put("craftGatewayCount", count);
                                w.extraFields.put("craftGatewaySource", source);
                                w.extraFields.put("craftGatewayDestination", destination);
                                w.extraFields.put("craftGatewayTrigger", trigger);
                                w.extraFields.put("craftGatewayTriggerBit", triggerBit);
                            }
                            w.extraFields.put("craftUnsupportedComponent", !enabled);
                        } catch (Exception ignored) {}
                    }
                    if (craftBitField != null) {
                        try {
                            int bit = Integer.parseInt(craftBitField.getText().toString().trim());
                            if (bit < 0 || bit > 15) throw new NumberFormatException();
                            w.extraFields.put("craftVarBit", bit);
                        } catch (Exception invalid) {
                            Toast.makeText(this, "Бит регистра должен быть от 0 до 15", Toast.LENGTH_LONG).show(); return;
                        }
                    }
                    if (craftBitList) {
                        String[] labels;
                        try { labels = CraftBitList.parseEditorLines(craftBitListLabelsField.getText().toString()); }
                        catch (IllegalArgumentException invalid) { Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show(); return; }
                        BindingRequest listBinding = parseBinding(-1, connectionField.getText().toString().trim());
                        if (listBinding != null && listBinding.functionCode != 3) {
                            Toast.makeText(this, "Список записывает битовую маску в holding-регистр FC03/FC06", Toast.LENGTH_LONG).show(); return;
                        }
                        if (craftBitListVisibleField.isChecked() && CraftBitList.availableMask(labels) == 0) {
                            Toast.makeText(this, "Добавь подпись хотя бы одному пункту", Toast.LENGTH_LONG).show(); return;
                        }
                        try {
                            w.extraFields.put("craftListLabels", CraftBitList.toJson(labels));
                            w.extraFields.put("craftListVisibleInWork", craftBitListVisibleField.isChecked());
                            w.extraFields.put("craftListCheckNoChange", craftBitListNoChangeField.isChecked());
                            w.extraFields.put("craftListEnabled", listBinding != null);
                        } catch (Exception ignored) { }
                    }
                    if (craftLinearSetter) {
                        final int maximum;
                        try { maximum = CraftLinearSetter.requireMaximum(craftLinearMaxField.getText().toString()); }
                        catch (IllegalArgumentException invalid) { Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show(); return; }
                        BindingRequest linearBinding = parseBinding(-1, connectionField.getText().toString().trim());
                        if (!connectionField.getText().toString().trim().isEmpty()
                                && (linearBinding == null || linearBinding.functionCode != 3)) {
                            Toast.makeText(this, "Линейный задатчик требует корректный holding-регистр FC03/FC06", Toast.LENGTH_LONG).show(); return;
                        }
                        try {
                            w.extraFields.put("craftLinearMax", maximum);
                            w.extraFields.put("craftLinearValue", CraftLinearSetter.value(w.extraFields, null));
                            w.extraFields.put("craftLinearVertical", craftLinearVerticalField.isChecked());
                            w.extraFields.put("craftLinearEnabled", linearBinding != null);
                        } catch (Exception ignored) { }
                    }
                    if (craftGif) {
                        String gifPath = craftGifPathField.getText().toString().trim();
                        if (!gifPath.isEmpty() && !gifPath.startsWith("media/")) {
                            Toast.makeText(this, "Выбери GIF, чтобы сохранить его в проект", Toast.LENGTH_LONG).show(); return;
                        }
                        try {
                            w.extraFields.put("craftGifPath", gifPath);
                            JSONObject craftOther = w.extraFields.optJSONObject("craftOther");
                            if (craftOther != null) craftOther.put("pathGif", gifPath);
                        } catch (Exception ignored) { }
                    }
                    if (craftNumericInput) {
                        final double min, max;
                        final int decimals;
                        try {
                            min = Double.parseDouble(craftNumericMinField.getText().toString().trim().replace(',', '.'));
                            max = Double.parseDouble(craftNumericMaxField.getText().toString().trim().replace(',', '.'));
                            CraftNumericInput.validateRange(min, max);
                            decimals = Integer.parseInt(craftNumericDecimalsField.getText().toString().trim());
                            if (decimals < 0 || decimals > 8) throw new IllegalArgumentException("Точность должна быть 0–8");
                        } catch (Exception invalid) {
                            Toast.makeText(this, invalid instanceof IllegalArgumentException ? invalid.getMessage() : "Проверь минимум, максимум и точность", Toast.LENGTH_LONG).show(); return;
                        }
                        String targetText = connectionField.getText().toString().trim();
                        BindingRequest target = parseBinding(-1, targetText);
                        if (!targetText.isEmpty() && (target == null || target.functionCode != 3
                                || (craftNumericModeField.getSelectedItemPosition() == CraftNumericInput.FLOAT32 && target.address >= 65535))) {
                            Toast.makeText(this, "Нужен holding-регистр FC03/FC06; Float32 занимает два регистра", Toast.LENGTH_LONG).show(); return;
                        }
                        boolean passwordRequired = craftNumericPasswordRequiredField.isChecked();
                        String passwordKey = w.extraFields.optString("craftNumericPasswordKey", "");
                        String newPassword = craftNumericPasswordField.getText().toString();
                        if (passwordRequired) {
                            if (!newPassword.isEmpty()) {
                                if (newPassword.length() > 10) { Toast.makeText(this, "Пароль: максимум 10 символов", Toast.LENGTH_LONG).show(); return; }
                                if (passwordKey.isEmpty()) passwordKey = java.util.UUID.randomUUID().toString();
                                try { CraftNumericPasswordStore.save(this, passwordKey, newPassword); }
                                catch (Exception failure) { Toast.makeText(this, "Не удалось безопасно сохранить пароль", Toast.LENGTH_LONG).show(); return; }
                            } else if (passwordKey.isEmpty()) {
                                Toast.makeText(this, "Задай новый пароль или отключи проверку для импортированного компонента", Toast.LENGTH_LONG).show(); return;
                            }
                        } else if (!passwordKey.isEmpty()) {
                            CraftNumericPasswordStore.clear(this, passwordKey); passwordKey = "";
                        }
                        try {
                            w.extraFields.put("craftNumericMin", min); w.extraFields.put("craftNumericMax", max);
                            w.extraFields.put("craftNumericMode", craftNumericModeField.getSelectedItemPosition());
                            w.extraFields.put("craftNumericTypeInt", craftNumericTypeIntField.getSelectedItemPosition());
                            w.extraFields.put("craftNumericDecimals", decimals);
                            w.extraFields.put("craftNumericFrame", craftNumericFrameField.isChecked());
                            w.extraFields.put("craftNumericSystemFont", craftNumericSystemFontField.isChecked());
                            w.extraFields.put("craftNumericPasswordRequired", passwordRequired);
                            w.extraFields.put("craftNumericPasswordKey", passwordKey);
                            w.extraFields.put("craftNumericEnabled", craftNumericEnabledField.isChecked());
                            w.showFrame = craftNumericFrameField.isChecked();
                        } catch (Exception ignored) { }
                    }
                    if (craftButtonConfig) {
                        int mode = craftButtonModeField.getSelectedItemPosition() == 1 ? 2 : 0;
                        int valueType = craftButtonConstantTypeField.getSelectedItemPosition();
                        String constant = craftButtonConstantField.getText().toString().trim();
                        int buttonBit;
                        try {
                            buttonBit = Integer.parseInt(craftButtonBitField.getText().toString().trim());
                            CraftMomentaryButton.masks(buttonBit, true);
                        } catch (Exception invalid) {
                            Toast.makeText(this, "Бит кнопки должен быть целым числом от 0 до 15", Toast.LENGTH_LONG).show(); return;
                        }
                        BindingRequest buttonBinding = parseBinding(-1, connectionField.getText().toString().trim());
                        if (mode == 2) {
                            try { if (valueType == 0) CraftConstantButton.integer(constant); else CraftConstantButton.floating(constant); }
                            catch (IllegalArgumentException invalid) { Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show(); return; }
                            if (connectionField.getText().toString().trim().isEmpty() || buttonBinding == null || buttonBinding.functionCode != 3) {
                                Toast.makeText(this, "Для константы нужен корректный TCP holding-регистр FC03/FC06", Toast.LENGTH_LONG).show(); return;
                            }
                        } else if (craftButtonBitEnabledField.isChecked()
                                && (connectionField.getText().toString().trim().isEmpty()
                                || buttonBinding == null || buttonBinding.functionCode != 3)) {
                            Toast.makeText(this, "Для битовой команды нужен TCP holding-регистр FC03/FC22", Toast.LENGTH_LONG).show(); return;
                        }
                        try {
                            w.extraFields.put("craftButtonMode", mode);
                            w.extraFields.put("craftButtonBit", buttonBit);
                            w.extraFields.put("craftButtonBitEnabled", mode == 0 && craftButtonBitEnabledField.isChecked());
                            w.extraFields.put("craftButtonConstantType", valueType);
                            w.extraFields.put("craftButtonConstant", constant);
                            w.extraFields.put("craftButtonConstantEnabled", mode == 2 && buttonBinding != null);
                            w.extraFields.put("craftButtonVisibleInWork", craftButtonVisibleField.isChecked());
                            w.type = mode == 2 ? "Константная кнопка Craft" : "Кнопка";
                        } catch (Exception ignored) { }
                    }
                    if (craftSwitchConfig) {
                        int bit;
                        try {
                            bit = Integer.parseInt(craftSwitchBitField.getText().toString().trim());
                            CraftMomentaryButton.masks(bit, true);
                        } catch (Exception invalid) {
                            Toast.makeText(this, "Бит переключателя должен быть целым числом от 0 до 15", Toast.LENGTH_LONG).show(); return;
                        }
                        BindingRequest switchBinding = parseBinding(-1, connectionField.getText().toString().trim());
                        if (craftSwitchBitEnabledField.isChecked()
                                && (connectionField.getText().toString().trim().isEmpty()
                                || switchBinding == null || switchBinding.functionCode != 3)) {
                            Toast.makeText(this, "Для записи бита нужен TCP holding-регистр FC03/FC22", Toast.LENGTH_LONG).show(); return;
                        }
                        try {
                            int skin = craftSwitchSkinField.getSelectedItemPosition() + 1;
                            w.extraFields.put("craftSwitchSkin", skin);
                            w.extraFields.put("craftSwitchBit", bit);
                            w.extraFields.put("craftSwitchBitEnabled", craftSwitchBitEnabledField.isChecked());
                            w.extraFields.put("craftSwitchVisibleInWork", craftSwitchVisibleField.isChecked());
                            w.extraFields = OriginalCraftSwitch.withImagePosition(w.extraFields, skin);
                            w.extraFields = OriginalCraftSwitch.withVisibility(w.extraFields, craftSwitchVisibleField.isChecked());
                        } catch (Exception ignored) { }
                    }
                    if (craftConstantModeField != null) {
                        String constant = craftConstantValueField.getText().toString().trim();
                        try {
                            double numeric = Double.parseDouble(constant);
                            if (!Double.isFinite(numeric)) throw new NumberFormatException();
                            if (isCraftRegisterBoolean(w.type) && numeric != 0.0 && numeric != 1.0)
                                throw new NumberFormatException();
                            if ("FBD CRAFT VAR INT".equals(canonicalFbdBlockType(w.type))
                                    && (numeric < 0 || numeric > 65535 || numeric != Math.rint(numeric)))
                                throw new NumberFormatException();
                            w.extraFields.put("craftVarConstant", constant);
                            w.extraFields.put("craftVarConstantMode", craftConstantModeField.isChecked());
                        } catch (Exception invalid) {
                            Toast.makeText(this, "Укажи конечное значение BOOL (0/1), UINT16 или FLOAT32", Toast.LENGTH_LONG).show(); return;
                        }
                    }
                    if (craftSwapWordField != null) {
                        try {
                            w.extraFields.put("craftSwapWord", craftSwapWordField.isChecked());
                            w.extraFields.put("craftSwapByte", craftSwapByteField.isChecked());
                        } catch (Exception ignored) {}
                    }
                    if (craftVariableNameField != null) {
                        String variableName = craftVariableNameField.getText().toString().trim();
                        int variableKind = craftVariableTypeField.getSelectedItemPosition() + 1;
                        boolean useConstant = craftNamedConstantModeField != null && craftNamedConstantModeField.isChecked();
                        if (variableName.isEmpty() && !useConstant) {
                            Toast.makeText(this, "Укажи имя общей переменной", Toast.LENGTH_LONG).show(); return;
                        }
                        if (craftNamedConstantValueField != null) {
                            try {
                                double numeric = Double.parseDouble(craftNamedConstantValueField.getText().toString().trim());
                                if (!Double.isFinite(numeric)
                                        || (variableKind == 1 && numeric != 0.0 && numeric != 1.0)
                                        || (variableKind == 2 && (numeric < Integer.MIN_VALUE || numeric > Integer.MAX_VALUE || numeric != Math.rint(numeric)))
                                        || (variableKind == 3 && Math.abs(numeric) > Float.MAX_VALUE)) throw new NumberFormatException();
                                w.extraFields.put("craftNamedConstant", Double.toString(numeric));
                                w.extraFields.put("craftNamedConstantMode", useConstant);
                            } catch (Exception invalid) {
                                Toast.makeText(this, "Константа должна соответствовать BOOL, INT32 или FLOAT32", Toast.LENGTH_LONG).show(); return;
                            }
                        }
                        try { w.extraFields.put("craftVariableName", variableName); }
                        catch (Exception ignored) {}
                        boolean source = isCraftNamedVariableSource(w.type);
                        w.type = craftNamedVariableType(variableKind, !source);
                    }
                    if (craftOrientationAxisField != null) {
                        int axis = craftOrientationAxisField.getSelectedItemPosition() + 1;
                        int oldAxis = craftOrientationAxis(w);
                        if (textField.getText().toString().trim().equals(craftOrientationLabel(oldAxis)))
                            textField.setText(craftOrientationLabel(axis));
                        try { w.extraFields.put("craftSensorAxis", axis); }
                        catch (Exception ignored) {}
                    }
                    if (craftSunriseModeField != null) {
                        try {
                            double latitude = Double.parseDouble(craftSunriseLatitudeField.getText().toString().trim());
                            double longitude = Double.parseDouble(craftSunriseLongitudeField.getText().toString().trim());
                            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                                    || !Double.isFinite(longitude) || longitude < -180 || longitude > 180
                                    || (latitude == 0.0 && longitude == 0.0)) throw new NumberFormatException();
                            String oldMode = craftSunriseMode(w);
                            String newMode = craftSunriseModeField.getSelectedItemPosition() == 1 ? "SS" : "SR";
                            if (textField.getText().toString().trim().equals(oldMode)) textField.setText(newMode);
                            w.extraFields.put("latitude", latitude); w.extraFields.put("longitude", longitude);
                            w.extraFields.put("craftSunriseMode", newMode);
                        } catch (Exception invalid) {
                            Toast.makeText(this, "Укажи корректные координаты широты и долготы", Toast.LENGTH_LONG).show(); return;
                        }
                    }
                    if (craftOdTempHostField != null) {
                        String host = craftOdTempHostField.getText().toString().trim();
                        if (!isAllowedOdTempHost(host)) {
                            Toast.makeText(this, "Укажи частный IPv4-адрес устройства", Toast.LENGTH_LONG).show(); return;
                        }
                        try {
                            w.extraFields.put("ip", host);
                            w.extraFields.put("craftOdTempType", craftOdTempTypeField.getSelectedItemPosition() + 1);
                            w.extraFields.put("craftOdTempResponse", craftOdTempResponseField.getSelectedItemPosition() + 1);
                        } catch (Exception ignored) {}
                    }
                    if (craftMqttTopicField != null) {
                        String topic = craftMqttTopicField.getText().toString().trim();
                        boolean topicValid = isCraftMqttSubscriber(w.type)
                                ? FbdCraftMqttRuntime.validTopicFilter(topic)
                                : FbdCraftMqttRuntime.validTopic(topic);
                        if (!topicValid) {
                            Toast.makeText(this, "Укажи корректный MQTT topic/filter", Toast.LENGTH_LONG).show(); return;
                        }
                        int type = craftMqttTypeField.getSelectedItemPosition() + 1;
                        try {
                            w.extraFields.put("craftMqttTopic", topic);
                            w.extraFields.put("craftMqttType", type);
                            w.extraFields.put("craftMqttServer", craftMqttServerField.getSelectedItemPosition() + 1);
                            if (craftMqttRetainField != null) w.extraFields.put("craftMqttRetain", craftMqttRetainField.isChecked());
                        } catch (Exception ignored) {}
                        String suffix = type == 1 ? "BOOL" : type == 3 ? "FLOAT" : "INT";
                        w.type = isCraftMqttSubscriber(w.type) ? "FBD CRAFT MQTT SUB " + suffix
                                : "FBD CRAFT MQTT PUB " + suffix;
                    }
                    w.text = textField.getText().toString();
                    w.connectionInfo = connectionField.getText().toString().trim();
                    if (craftIndicatorTypeField != null) {
                        try {
                            int digits = Integer.parseInt(craftIndicatorDigitsField.getText().toString().trim());
                            int decimals = Integer.parseInt(craftIndicatorDecimalsField.getText().toString().trim());
                            if (digits < 1 || digits > 18 || decimals < 0 || decimals > 9)
                                throw new NumberFormatException();
                            int sourceType = craftIndicatorTypeField.getSelectedItemPosition();
                            w.extraFields.put("craftNumericIndicatorType", sourceType);
                            w.extraFields.put("craftNumericIndicatorDigits", digits);
                            w.extraFields.put("craftNumericIndicatorDecimals", decimals);
                            w.extraFields.put("craftNumericIndicatorFontChange", craftIndicatorFontChangeField.isChecked());
                            w.extraFields.put("craftNumericIndicatorFrame", craftIndicatorFrameField.isChecked());
                            JSONObject craftOther = w.extraFields.optJSONObject("craftOther");
                            if (craftOther == null) craftOther = new JSONObject();
                            craftOther.put("type", sourceType); craftOther.put("digit", digits);
                            craftOther.put("divisor", decimals);
                            craftOther.put("fontChange", craftIndicatorFontChangeField.isChecked());
                            craftOther.put("enableFrameRegister", craftIndicatorFrameField.isChecked());
                            w.extraFields.put("craftOther", craftOther);
                        } catch (Exception invalid) {
                            Toast.makeText(this, "Проверь разряды индикатора: целая часть 1–18, дробная 0–9", Toast.LENGTH_LONG).show(); return;
                        }
                    }
                    if (archiveIntervalField != null) {
                        final int interval, maxRows, channelCount;
                        String triggerBinding = archiveTriggerField.getText().toString().trim();
                        try {
                            interval = Integer.parseInt(archiveIntervalField.getText().toString().trim());
                            maxRows = Integer.parseInt(archiveMaxRowsField.getText().toString().trim());
                            channelCount = Integer.parseInt(archiveChannelCountField.getText().toString().trim());
                        } catch (NumberFormatException invalid) {
                            Toast.makeText(this, "Укажи число каналов, интервал и лимит архива", Toast.LENGTH_LONG).show(); return;
                        }
                        if (channelCount < 1 || channelCount > 4 || interval < 1 || interval > 86400
                                || maxRows < 100 || maxRows > 100000) {
                            Toast.makeText(this, "Каналы: 1–4; интервал: 1–86400 секунд; лимит: 100–100000 записей", Toast.LENGTH_LONG).show(); return;
                        }
                        if (!triggerBinding.isEmpty() && parseBinding(-1, triggerBinding) == null) {
                            Toast.makeText(this, "Триггер: tcp://host:port/unit/address", Toast.LENGTH_LONG).show(); return;
                        }
                        craftArchiveTriggerHigh.remove(w);
                        craftArchiveTriggerEdges.remove(w);
                        try {
                            w.extraFields.put("craftArchiveIntervalSeconds", interval);
                            w.extraFields.put("craftArchiveMaxRows", maxRows);
                            w.extraFields.put("craftArchiveChannelCount", channelCount);
                            w.extraFields.put("craftArchiveRecordType", archiveRecordTypeField.getSelectedItemPosition() == 1 ? "FLOAT" : "INT");
                            w.extraFields.put("craftArchiveTriggerBinding", triggerBinding);
                            if (w.extraFields.optString("craftArchiveKey", "").trim().isEmpty())
                                w.extraFields.put("craftArchiveKey", java.util.UUID.randomUUID().toString());
                        } catch (org.json.JSONException ignored) { }
                    }
                    if(smsPhoneField!=null) {
                        String phone=smsPhoneField.getText().toString().trim();
                        boolean craftSms = craftSmsEnabledField != null;
                        boolean enabled = !craftSms || craftSmsEnabledField.isChecked();
                        if((enabled || !phone.isEmpty()) && !SmsNotificationClient.isValidPhone(phone)) {
                            Toast.makeText(this,"Укажи номер получателя в международном формате",Toast.LENGTH_LONG).show();return;
                        }
                        int registerCount = smsRegisterCountField.getSelectedItemPosition() + 1;
                        boolean optionMode = craftSms && craftSmsOptionField.isChecked();
                        int optionRegister = craftSms ? w.extraFields.optInt("craftSmsOptionRegister", 0) : -1;
                        if (optionMode) {
                            try { optionRegister = Integer.parseInt(craftSmsOptionRegisterField.getText().toString().trim()); }
                            catch (NumberFormatException invalid) { optionRegister = -1; }
                            if (optionRegister < 0 || optionRegister > 65535) {
                                Toast.makeText(this, "Адрес регистра SMS должен быть 0–65535", Toast.LENGTH_LONG).show(); return;
                            }
                        }
                        if (!craftSms || optionMode) {
                            String[] previewValues = new String[registerCount];
                            java.util.Arrays.fill(previewValues, "0");
                            try { SmsOutgoingTemplate.render(textField.getText().toString(), previewValues, "0"); }
                            catch (IllegalArgumentException invalidTemplate) {
                                Toast.makeText(this, "Шаблон SMS использует номер регистра выше выбранного количества", Toast.LENGTH_LONG).show(); return;
                            }
                        }
                        try {
                            if (SmsNotificationClient.isValidPhone(phone)) {
                                String recipientKey=java.util.UUID.randomUUID().toString();
                                SmsRecipientStore.save(this,recipientKey,phone);w.extraFields.put("smsRecipientId",recipientKey);
                            }
                            w.extraFields.put("smsRegisterCount",registerCount);
                            if (craftSms) {
                                w.extraFields.put("smsEnabled", enabled);
                                w.extraFields.put("craftSmsOption", optionMode);
                                w.extraFields.put("craftSmsOptionRegister", Math.max(0, optionRegister));
                                w.extraFields.put("craftSmsOptionCount", registerCount);
                                org.json.JSONObject craftOther = w.extraFields.optJSONObject("craftOther");
                                if (craftOther != null) {
                                    craftOther.put("phone", phone);
                                    craftOther.put("message", textField.getText().toString());
                                    craftOther.put("option", optionMode);
                                    craftOther.put("optionRegister", Math.max(0, optionRegister));
                                    craftOther.put("optionCountRegister", registerCount);
                                }
                            }
                        } catch(Exception failure){Toast.makeText(this,"Не удалось безопасно сохранить настройки SMS",Toast.LENGTH_LONG).show();return;}
                    }
                    if (incomingSmsEnabledField != null) {
                        boolean enabled = incomingSmsEnabledField.isChecked();
                        String sender = incomingSmsSenderField.getText().toString().trim();
                        String command = incomingSmsCommandField.getText().toString().trim();
                        int bit;
                        try { bit = Integer.parseInt(incomingSmsBitField.getText().toString().trim()); }
                        catch (NumberFormatException invalid) { bit = -1; }
                        boolean strobeEnabled = incomingSmsStrobeEnabledField.isChecked();
                        int strobeRegister;
                        try { strobeRegister = Integer.parseInt(incomingSmsStrobeRegisterField.getText().toString().trim()); }
                        catch (NumberFormatException invalid) { strobeRegister = -1; }
                        int strobeBit;
                        try { strobeBit = Integer.parseInt(incomingSmsStrobeBitField.getText().toString().trim()); }
                        catch (NumberFormatException invalid) { strobeBit = -1; }
                        BindingRequest smsBinding = parseBinding(-1, w.connectionInfo);
                        BindingRequest smsStrobeBinding = smsBinding == null || strobeRegister < 0 ? null
                                : parseBinding(-1, "tcp://" + smsBinding.host + ":" + smsBinding.port + "/"
                                        + smsBinding.unit + "/" + strobeRegister);
                        if (enabled && (!SmsIncomingCommand.isValidSender(sender)
                                || command.isEmpty() || command.length() > 80 || command.indexOf('\n') >= 0
                                || command.indexOf('\r') >= 0 || bit < 0 || bit > 15
                                || smsBinding == null || smsBinding.functionCode != 3
                                || (strobeEnabled && (incomingSmsModeField.getSelectedItemPosition() != 0
                                    || smsStrobeBinding == null || smsStrobeBinding.functionCode != 3
                                    || smsStrobeBinding.unit != smsBinding.unit || strobeBit < 0 || strobeBit > 15)))) {
                            Toast.makeText(this, "Проверь номер, команду, Modbus holding-регистр и параметры strobe", Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (bit < 0 || bit > 15) bit = 0;
                        if (strobeBit < 0 || strobeBit > 15) strobeBit = 0;
                        try {
                            if (SmsIncomingCommand.isValidSender(sender)) {
                                String senderKey = java.util.UUID.randomUUID().toString();
                                SmsRecipientStore.save(this, senderKey, sender);
                                w.extraFields.put("smsSenderId", senderKey);
                            }
                            w.extraFields.put("smsCommand", command);
                            w.extraFields.put("smsMode", incomingSmsModeField.getSelectedItemPosition() == 1 ? "bit" : "number");
                            w.extraFields.put("smsBit", bit);
                            w.extraFields.put("smsEnabled", enabled);
                            w.extraFields.put("smsStrobeEnabled", strobeEnabled);
                            w.extraFields.put("smsStrobeRegister", strobeRegister);
                            w.extraFields.put("smsStrobeBit", strobeBit);
                        } catch (Exception failure) {
                            Toast.makeText(this, "Не удалось безопасно сохранить номер отправителя SMS", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
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
                            if (bitLabelFields[k] != null) {                                w.bitListLabels[k] = bitLabelFields[k].getText().toString();
                            }
                        }
                    }
                    if (soundThresholdField != null) {
                        w.soundThreshold = readDouble(soundThresholdField, w.soundThreshold);
                        w.soundFilePath = soundFilePathField.getText().toString();
                        w.craftAlarmSoundIndex = soundPresetField.getSelectedItemPosition();
                    }
                    if (targetScreenIndexField != null) {
                        int selectedTarget = targetScreenIndexField.getSelectedItemPosition();
                        String targetUrl = targetUrlField.getText().toString().trim();
                        if (selectedTarget <= 0 && targetUrl.isEmpty()) {
                            Toast.makeText(this, "Выбери существующий экран или укажи URL", Toast.LENGTH_LONG).show();
                            return;
                        }
                        w.targetScreenIndex = selectedTarget > 0 ? selectedTarget - 1 : -1;
                        w.targetUrl = targetUrl;
                        try { w.extraFields.put("craftButtonVisibleInWork", targetVisibleInWorkField.isChecked()); }
                        catch (Exception ignored) { }
                    }
                    if ("Многопозиционное фото".equals(w.type)) {
                        for (int k = 0; k < imagePathFields.length; k++) {
                            if (imagePathFields[k] != null) {
                                w.imagePathStates[k] = imagePathFields[k].getText().toString();
                            }
                        }
                        if (imageSelectionModeField != null) {
                            try {
                                JSONObject sourceOther = w.extraFields.optJSONObject("craftOther");
                                if (sourceOther == null) sourceOther = new JSONObject();
                                sourceOther.put("typeCommand", imageSelectionModeField.getSelectedItemPosition() == 0 ? 0 : 1);
                                w.extraFields.put("craftOther", sourceOther);
                            } catch (Exception ignored) { }
                        }
                    }
                    if ("Голосовое сообщение".equals(w.type)) {
                        for (int k = 0; k < voiceMsgFields.length; k++) {
                            if (voiceMsgFields[k] != null) {
                                w.voiceMessages[k] = voiceMsgFields[k].getText().toString();
                            }
                        }
                    }
                    if ("Распознавание речи Craft".equals(w.type)) {
                        String[] phrases = new String[craftVoicePhraseFields.length];
                        for (int k = 0; k < craftVoicePhraseFields.length; k++)
                            phrases[k] = craftVoicePhraseFields[k].getText().toString();
                        try {
                            JSONObject sourceOther = CraftVoiceRecognitionFields.write(
                                    w.extraFields.optJSONObject("craftOther"), phrases);
                            w.extraFields.put("craftOther", sourceOther);
                        } catch (Exception invalid) {
                            Toast.makeText(MainActivity.this, invalid.getMessage(), Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    if (timerPresetField != null) {
                        String oldMode = FbdDurationUnits.modeForType(w.type);
                        String newMode = String.valueOf(timerModeSelector.getSelectedItem());
                        w.timerPresetMs = fbd.timer;
                        boolean originalCraftTimer = "FBD CRAFT TIMER".equals(w.type);
                        w.type = "FBD CRAFT BLINK".equals(w.type) || originalCraftTimer ? w.type
                                : FbdDurationUnits.typeForMode(newMode);
                        // Keep custom labels intact, refresh only an untouched mode label.
                        if (w.text != null && w.text.equals(oldMode)) w.text = newMode;
                        w.timerStartedAt = 0L;
                        w.counterLastInput = false;
                        if ("FBD CRAFT BLINK".equals(w.type))
                            w.craftBlinkState = FbdCraftNativeTimer.State.initial();
                        try {
                            String unit = new String[]{"MS", "SEK", "MIN"}[timerUnitSelector.getSelectedItemPosition()];
                            if (originalCraftTimer) {
                                w.extraFields.put("craftTimerMode", newMode);
                                w.extraFields.put("craftTimerUnit", unit);
                                w.craftTimerState = FbdCraftNativeTimer.State.initial();
                            }
                            // An untouched old MS project must not acquire an extra field.
                            if (!"MS".equals(unit) || w.extraFields.has("bloodskyFbdTimeUnit"))
                                w.extraFields.put("bloodskyFbdTimeUnit", unit);
                        } catch (org.json.JSONException ignored) {}
                    }
                    if (triggerModeSelector != null) {
                        String mode = String.valueOf(triggerModeSelector.getSelectedItem());
                        try { w.extraFields.put("craftTriggerMode", mode); }
                        catch (org.json.JSONException ignored) {}
                        w.text = mode;
                    }
                    if (counterPresetField != null) {
                        w.counterPreset = fbd.counter;
                        if ("FBD CTD".equals(w.type)) w.counterValue = w.counterPreset;
                    }
                    if (compareLeftField != null) {
                        w.compareA = fbd.left;
                        w.compareB = fbd.right;
                        w.compareOperator = fbd.operator;
                        w.text = ("FBD CRAFT COMPARE".equals(w.type)
                                || "FBD CRAFT COMPARE FLOAT".equals(w.type)
                                || "FBD CRAFT ARITHMETIC INT".equals(w.type)
                                || "FBD CRAFT ARITHMETIC FLOAT".equals(w.type))
                                ? w.compareOperator
                                : Double.toString(w.compareA) + " " + w.compareOperator
                                        + " " + Double.toString(w.compareB);
                    }
                    if (minValField != null) {
                        w.minValue = readDouble(minValField, w.minValue);
                    } else if ("Шкала / Стрелка".equals(w.type)
                            && w.extraFields.optInt("craftComponentId", -1) == 12) {
                        w.minValue = 0.0;
                    }
                    if (maxValField != null) {
                        w.maxValue = readDouble(maxValField, w.maxValue);
                    }
                    if (trendIntervalField != null) {
                        try {
                            String[] seriesNames = new String[trendSeriesNameFields.length];
                            for (int i = 0; i < seriesNames.length; i++)
                                seriesNames[i] = trendSeriesNameFields[i].getText().toString();
                            CraftTrendGraphProperties.apply(w.extraFields,
                                    readPositiveInt(trendIntervalField,
                                            w.extraFields.optInt("craftTrendIntervalMs", 500), false),
                                    readPositiveInt(trendPointCountField,
                                            w.extraFields.optInt("craftTrendMaxPoints", 300), false),
                                    readPositiveInt(trendChannelCountField,
                                            w.extraFields.optInt("craftTrendChannelCount", 1), false),
                                    readPositiveInt(trendTypeField,
                                            w.extraFields.optInt("craftTrendType", 1), true),
                                    seriesNames,
                                    Color.parseColor(trendLabelsColorField.getText().toString().trim()),
                                    Color.parseColor(trendBackgroundColorField.getText().toString().trim()));
                        } catch (IllegalArgumentException | org.json.JSONException invalid) {
                            Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    if (gaugeShowTextField != null) {
                        try {
                            CraftThresholdGaugeProperties.apply(w.extraFields,
                                    readPositiveInt(maxValField, (int) w.maxValue, false),
                                    gaugeShowTextField.isChecked(),
                                    readPositiveInt(gaugeTextSizeField,
                                            w.extraFields.optInt("craftGaugeTextSize", 12), false),
                                    parseOptionalColor(gaugeTextColorField.getText().toString(),
                                            w.extraFields.optInt("craftGaugeTextColor", Color.BLACK)),
                                    parseOptionalColor(gaugeInColorField.getText().toString(),
                                            w.extraFields.optInt("craftGaugeInColor", Color.rgb(136, 136, 136))),
                                    parseOptionalColor(gaugeOutColorField.getText().toString(),
                                            w.extraFields.optInt("craftGaugeOutColor", Color.parseColor("#FF277454"))),
                                    parseOptionalColor(gaugeCenterColorField.getText().toString(),
                                            w.extraFields.optInt("craftGaugeCenterColor", Color.BLACK)),
                                    readPositiveInt(gaugeFirstColorPointField,
                                            w.extraFields.optInt("craftGaugeFirstColorPoint", 0), true),
                                    readPositiveInt(gaugeSecondColorPointField,
                                            w.extraFields.optInt("craftGaugeSecondColorPoint", 0), true));
                        } catch (IllegalArgumentException | org.json.JSONException invalid) {
                            Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    if (graduatedGaugeCountField != null) {
                        try {
                            CraftGraduatedGaugeProperties.apply(w.extraFields,
                                    (int) Math.round(readDouble(graduatedGaugeMinField, w.minValue)),
                                    (int) Math.round(readDouble(graduatedGaugeMaxField, w.maxValue)),
                                    readPositiveInt(graduatedGaugeCountField,
                                            w.extraFields.optInt("craftGaugeScaleCount", 6), true),
                                    readPositiveInt(graduatedGaugeTextSizeField,
                                            w.extraFields.optInt("craftGaugeTextSize", 20), false),
                                    parseOptionalColor(graduatedGaugeTextValueColorField.getText().toString(),
                                            w.extraFields.optInt("craftGaugeTextValueColor", Color.BLACK)),
                                    parseOptionalColor(graduatedGaugeTextScaleColorField.getText().toString(),
                                            w.extraFields.optInt("craftGaugeTextScaleColor", Color.BLACK)),
                                    parseOptionalColor(graduatedGaugeCenterColorField.getText().toString(),
                                            w.extraFields.optInt("craftGaugeCenterColor", Color.parseColor("#BFBFBF"))),
                                    parseOptionalColor(graduatedGaugeIndicatorColorField.getText().toString(),
                                            w.extraFields.optInt("craftGaugeIndicatorColor", Color.BLUE)),
                                    parseOptionalColor(graduatedGaugeMarkColorField.getText().toString(),
                                            w.extraFields.optInt("craftGaugeMarkColor", Color.BLACK)),
                                    readPositiveInt(graduatedGaugeLowPercentField,
                                            w.extraFields.optInt("craftGaugeLowPercent", 75), true),
                                    readPositiveInt(graduatedGaugeMediumPercentField,
                                            w.extraFields.optInt("craftGaugeMediumPercent", 90), true),
                                    graduatedGaugeHideBackgroundField.isChecked(),
                                    String.valueOf(graduatedGaugeIndicatorField.getSelectedItem()));
                        } catch (IllegalArgumentException | org.json.JSONException invalid) {
                            Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    if (progressShowTextField != null) {
                        try {
                            CraftProgressBarProperties.apply(w.extraFields, w.minValue, w.maxValue,
                                    progressShowTextField.isChecked(),
                                    readPositiveInt(progressTextSizeField,
                                            w.extraFields.optInt("craftProgressTextSize", 50), false),
                                    parseOptionalColor(progressTextColorField.getText().toString(),
                                            w.extraFields.optInt("craftProgressTextColor", Color.BLUE)),
                                    progressShowScaleField.isChecked(),
                                    parseOptionalColor(progressColorField.getText().toString(),
                                            w.extraFields.optInt("craftProgressColor", Color.rgb(46, 236, 0))),
                                    parseOptionalColor(progressBackgroundColorField.getText().toString(),
                                            w.extraFields.optInt("craftProgressBackgroundColor", Color.rgb(156, 156, 155))));
                        } catch (IllegalArgumentException | org.json.JSONException invalid) {
                            Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    w.x = geometry.x; w.y = geometry.y;
                    w.w = geometry.width; w.h = geometry.height;
                    w.backgroundColor = parseOptionalColor(backgroundColorField.getText().toString(), 0);
                    w.textColor = parseOptionalColor(textColorField.getText().toString(), w.textColor);
                    w.textSizeSp = Math.max(8, Math.min(48, readPositiveInt(textSizeField, w.textSizeSp, false)));
                    w.visible = visibleField.isChecked(); w.showFrame = frameField.isChecked(); w.autoResizeText = autoResizeField.isChecked();
                    persistProject();
                    editor.invalidate();
                    refreshIncomingSmsReceiver();
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

    private void drawCraftThresholdGauge(android.graphics.Canvas canvas, Widget widget,
                                        double rawValue, android.graphics.Paint paint) {
        float cx = widget.x + widget.w / 2f;
        float cy = widget.y + widget.h - dp(5);
        float radius = Math.max(dp(12), Math.min(widget.w / 2f - dp(6), widget.h - dp(12)));
        android.graphics.RectF outer = new android.graphics.RectF(
                cx - radius, cy - radius, cx + radius, cy + radius);
        int outColor = widget.extraFields.optInt("craftGaugeOutColor", Color.parseColor("#FF277454"));
        int inColor = widget.extraFields.optInt("craftGaugeInColor", Color.rgb(136, 136, 136));
        int centerColor = widget.extraFields.optInt("craftGaugeCenterColor", Color.BLACK);
        double max = widget.maxValue;
        double value = CraftThresholdGauge.ratio(rawValue, max) * max;

        paint.setShader(null);
        paint.setStyle(android.graphics.Paint.Style.FILL);
        paint.setColor(outColor);
        canvas.drawArc(outer, 180f, 180f, true, paint);
        float innerRadius = radius * 0.76f;
        android.graphics.RectF inner = new android.graphics.RectF(
                cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius);
        paint.setColor(inColor);
        canvas.drawArc(inner, 180f, 180f, true, paint);

        float scaleRadius = radius * 0.70f;
        android.graphics.RectF scale = new android.graphics.RectF(
                cx - scaleRadius, cy - scaleRadius, cx + scaleRadius, cy + scaleRadius);
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(dp(5));
        paint.setColor(Color.rgb(180, 180, 180));
        canvas.drawArc(scale, 185f, 170f, false, paint);

        int first = widget.extraFields.optInt("craftGaugeFirstColorPoint", 0);
        int second = widget.extraFields.optInt("craftGaugeSecondColorPoint", 0);
        if (first == 0 && second == 0) {
            drawCraftGaugeBand(canvas, paint, scale, 0, max, max, Color.GREEN);
        } else if (first == 0) {
            drawCraftGaugeBand(canvas, paint, scale, 0, second, max, Color.GREEN);
            drawCraftGaugeBand(canvas, paint, scale, second, max, max, Color.RED);
        } else {
            drawCraftGaugeBand(canvas, paint, scale, 0, first, max, Color.GREEN);
            drawCraftGaugeBand(canvas, paint, scale, first, second == 0 ? max : second, max, Color.YELLOW);
            if (second > 0) drawCraftGaugeBand(canvas, paint, scale, second, max, max, Color.RED);
        }

        int[] tickValues = CraftThresholdGauge.majorTickValues(max);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(Color.DKGRAY);
        for (int i = 0; i < tickValues.length; i++) {
            double degree = 10.0 + (i * 160.0 / 5.0);
            double radians = Math.toRadians(degree);
            float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
            float x1 = cx - cos * radius * 0.83f;
            float y1 = cy - sin * radius * 0.83f;
            float x2 = cx - cos * radius * 0.98f;
            float y2 = cy - sin * radius * 0.98f;
            canvas.drawLine(x1, y1, x2, y2, paint);
            if (widget.extraFields.optBoolean("craftGaugeShowText", true)) {
                paint.setStyle(android.graphics.Paint.Style.FILL);
                paint.setColor(widget.extraFields.optInt("craftGaugeTextColor", widget.textColor));
                paint.setTextSize(dp(widget.extraFields.optInt("craftGaugeTextSize", 12)));
                paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                canvas.drawText(Integer.toString(tickValues[i]), cx - cos * radius * 1.10f,
                        cy - sin * radius * 1.10f + dp(4), paint);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setColor(Color.DKGRAY);
            }
        }

        double needleRadians = Math.toRadians(CraftThresholdGauge.needleDegrees(value, max));
        float cos = (float) Math.cos(needleRadians), sin = (float) Math.sin(needleRadians);
        paint.setColor(Color.argb(128, 255, 0, 0));
        paint.setStrokeWidth(dp(3));
        canvas.drawLine(cx + cos * radius * 0.14f, cy - sin * radius * 0.14f,
                cx + cos * radius * 0.72f, cy - sin * radius * 0.72f, paint);
        paint.setStyle(android.graphics.Paint.Style.FILL);
        paint.setColor(centerColor);
        canvas.drawCircle(cx, cy, Math.max(dp(4), radius * 0.12f), paint);
        paint.setTextAlign(android.graphics.Paint.Align.LEFT);
        paint.setStrokeWidth(dp(1));
    }

    private void drawCraftGaugeBand(android.graphics.Canvas canvas, android.graphics.Paint paint,
                                    android.graphics.RectF bounds, double from, double to,
                                    double max, int color) {
        if (max <= 0 || from >= to) return;
        double low = Math.max(-max * 0.03125, Math.min(max * 1.03125, from));
        double high = Math.max(-max * 0.03125, Math.min(max * 1.03125, to));
        if (low >= high) return;
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(dp(5));
        paint.setColor(color);
        float start = (float) (190.0 + (low / max) * 160.0);
        float sweep = (float) (((high - low) / max) * 160.0);
        canvas.drawArc(bounds, start, sweep, false, paint);
    }

    private void drawCraftGraduatedGauge(android.graphics.Canvas canvas, Widget widget,
                                         double rawValue, android.graphics.Paint paint) {
        int min = (int) Math.round(widget.minValue), max = (int) Math.round(widget.maxValue);
        if (max <= min) max = min == Integer.MAX_VALUE ? min : min + 1;
        float ratio = (float) CraftGraduatedGauge.ratio(rawValue, min, max);
        float cx = widget.x + widget.w / 2f, cy = widget.y + widget.h - dp(14);
        float radius = Math.max(dp(10), Math.min(widget.w / 2f - dp(8), widget.h - dp(22)));
        android.graphics.RectF dial = new android.graphics.RectF(cx-radius, cy-radius, cx+radius, cy+radius);
        paint.setShader(null); paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeCap(android.graphics.Paint.Cap.BUTT); paint.setStrokeWidth(dp(6));
        if (!widget.extraFields.optBoolean("craftGaugeHideBackground", false)) {
            paint.setColor(Color.rgb(205,205,205)); canvas.drawArc(dial,180f,180f,false,paint);
        }
        float low=Math.max(0,Math.min(100,widget.extraFields.optInt("craftGaugeLowPercent",75)))/100f;
        float med=Math.max(low,Math.min(100,widget.extraFields.optInt("craftGaugeMediumPercent",90)))/100f;
        if(low>0){paint.setColor(Color.GREEN);canvas.drawArc(dial,180f,low*180f,false,paint);}
        if(med>low){paint.setColor(Color.YELLOW);canvas.drawArc(dial,180f+low*180f,(med-low)*180f,false,paint);}
        if(med<1){paint.setColor(Color.RED);canvas.drawArc(dial,180f+med*180f,(1-med)*180f,false,paint);}
        int count=Math.max(0,Math.min(64,widget.extraFields.optInt("craftGaugeScaleCount",6)));
        int[] ticks=CraftGraduatedGauge.tickValues(min,max,count);
        paint.setStrokeWidth(dp(1.5f)); paint.setColor(widget.extraFields.optInt("craftGaugeMarkColor",Color.BLACK));
        for(int tick:ticks){
            double tr=CraftGraduatedGauge.ratio(tick,min,max), a=Math.toRadians(180+tr*180);
            float cos=(float)Math.cos(a),sin=(float)Math.sin(a);
            canvas.drawLine(cx+cos*radius*.82f,cy+sin*radius*.82f,cx+cos*radius*.98f,cy+sin*radius*.98f,paint);
            paint.setStyle(android.graphics.Paint.Style.FILL); paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            paint.setTextSize(dp(Math.max(1,widget.extraFields.optInt("craftGaugeTextSize",20))));
            paint.setColor(widget.extraFields.optInt("craftGaugeTextScaleColor",Color.BLACK));
            canvas.drawText(Integer.toString(tick),cx+cos*radius*1.16f,cy+sin*radius*1.16f+dp(4),paint);
            paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setColor(widget.extraFields.optInt("craftGaugeMarkColor",Color.BLACK));
        }
        float angle=(float)Math.toRadians(CraftGraduatedGauge.needleDegrees(rawValue,min,max));
        drawCraftGraduatedIndicator(canvas,paint,cx,cy,radius,angle,
                widget.extraFields.optInt("craftGaugeIndicatorColor",Color.BLUE),
                widget.extraFields.optString("craftGaugeIndicatorName","NormalIndicator"));
        paint.setStyle(android.graphics.Paint.Style.FILL);
        paint.setColor(widget.extraFields.optInt("craftGaugeCenterColor",Color.parseColor("#BFBFBF")));
        canvas.drawCircle(cx,cy,Math.max(dp(4),radius*.11f),paint);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        paint.setTextSize(dp(Math.max(1,widget.extraFields.optInt("craftGaugeTextSize",20))));
        paint.setColor(widget.extraFields.optInt("craftGaugeTextValueColor",Color.BLACK));
        canvas.drawText(String.format(java.util.Locale.ROOT,"%.1f",rawValue),cx,cy+radius*.42f,paint);
        paint.setTextAlign(android.graphics.Paint.Align.LEFT);paint.setStrokeWidth(dp(1));
    }

    private void drawCraftGraduatedIndicator(android.graphics.Canvas canvas,android.graphics.Paint paint,
            float cx,float cy,float radius,float angle,int color,String type) {
        float ux=(float)Math.cos(angle),uy=(float)Math.sin(angle),px=-uy,py=ux;
        float length=radius*.76f; paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));paint.setColor(color);
        if("LineIndicator".equals(type)||"HalfLineIndicator".equals(type)
                ||"QuarterLineIndicator".equals(type)||"SpindleIndicator".equals(type)){
            float f="HalfLineIndicator".equals(type)?.5f:"QuarterLineIndicator".equals(type)?.25f:1f;
            canvas.drawLine(cx,cy,cx+ux*length*f,cy+uy*length*f,paint);
            if("SpindleIndicator".equals(type))canvas.drawLine(cx-ux*length*.2f,cy-uy*length*.2f,cx,cy,paint);
            return;
        }
        float tip=length*("NormalSmallIndicator".equals(type)?.65f:1f),half=Math.max(dp(2),radius*.045f);
        if("NeedleIndicator".equals(type)){
            paint.setStrokeWidth(dp(2));
            canvas.drawLine(cx-ux*radius*.18f,cy-uy*radius*.18f,
                    cx+ux*length,cy+uy*length,paint);
            return;
        }
        android.graphics.Path p=new android.graphics.Path();
        p.moveTo(cx+ux*tip,cy+uy*tip);
        if("KiteIndicator".equals(type)){
            p.lineTo(cx+px*half,cy+py*half);p.lineTo(cx-ux*radius*.1f,cy-uy*radius*.1f);p.lineTo(cx-px*half,cy-py*half);
        }else if("TriangleIndicator".equals(type)){
            p.lineTo(cx+px*half*1.7f,cy+py*half*1.7f);p.lineTo(cx-px*half*1.7f,cy-py*half*1.7f);
        }else{
            p.lineTo(cx+px*half,cy+py*half);p.lineTo(cx-ux*radius*.1f,cy-uy*radius*.1f);p.lineTo(cx-px*half,cy-py*half);
        }
        p.close();paint.setStyle(android.graphics.Paint.Style.FILL);canvas.drawPath(p,paint);
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
            if ("Архив Craft".equals(copy.type))
                copy.extraFields.put("craftArchiveKey", java.util.UUID.randomUUID().toString());
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
        editor.selectedWidgets.clear();
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
        if ("FBD CRAFT CTU".equals(w.type)) {
            obj.put("craftLastPlus", w.craftLastPlus);
            obj.put("craftLastMinus", w.craftLastMinus);
        }
        obj.put("timerPresetMs", w.timerPresetMs); obj.put("timerStartedAt", w.timerStartedAt);
        obj.put("compareA", w.compareA); obj.put("compareB", w.compareB); obj.put("compareOperator", w.compareOperator);
        obj.put("minValue", w.minValue); obj.put("maxValue", w.maxValue);
        obj.put("backgroundColor", w.backgroundColor); obj.put("textColor", w.textColor); obj.put("textSizeSp", w.textSizeSp);
        obj.put("visible", w.visible); obj.put("showFrame", w.showFrame); obj.put("autoResizeText", w.autoResizeText);
        obj.put("x", w.x); obj.put("y", w.y); obj.put("w", w.w); obj.put("h", w.h);

        obj.put("soundThreshold", w.soundThreshold);
        obj.put("soundActive", w.soundActive);
        obj.put("soundFilePath", w.soundFilePath == null ? "Аларм" : w.soundFilePath);
        obj.put("craftAlarmSoundIndex", w.craftAlarmSoundIndex);
        obj.put("craftAlarmLoop", w.craftAlarmLoop);
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
        if ("FBD CRAFT CTU".equals(w.type)) {
            w.craftLastPlus = obj.optBoolean("craftLastPlus", false);
            w.craftLastMinus = obj.optBoolean("craftLastMinus", false);
        }
        w.timerPresetMs = Math.max(1, Math.min(3600000, obj.optInt("timerPresetMs", 1000)));
        w.timerStartedAt = obj.optLong("timerStartedAt", 0L);
        w.compareA = obj.optDouble("compareA", 0.0); w.compareB = obj.optDouble("compareB", 0.0);
        boolean craftArithmetic = "FBD CRAFT ARITHMETIC INT".equals(w.type)
                || "FBD CRAFT ARITHMETIC FLOAT".equals(w.type);
        w.compareOperator = normalizeWidgetOperator(w.type,
                obj.optString("compareOperator", craftArithmetic ? "+" : ">"));
        w.minValue = obj.optDouble("minValue", 0.0); w.maxValue = obj.optDouble("maxValue", 100.0);
        w.backgroundColor = obj.optInt("backgroundColor", 0); w.textColor = obj.optInt("textColor", Color.rgb(31, 41, 55));
        w.textSizeSp = Math.max(8, Math.min(48, obj.optInt("textSizeSp", 14)));
        w.visible = obj.optBoolean("visible", true); w.showFrame = obj.optBoolean("showFrame", true); w.autoResizeText = obj.optBoolean("autoResizeText", false);
        w.soundThreshold = obj.optDouble("soundThreshold", 1.0); w.soundActive = obj.optBoolean("soundActive", false); w.soundFilePath = obj.optString("soundFilePath", "Аларм");
        w.craftAlarmSoundIndex = Math.max(0, Math.min(5, obj.optInt("craftAlarmSoundIndex", 0)));
        w.craftAlarmLoop = obj.optBoolean("craftAlarmLoop", false);
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

    private static String normalizeWidgetOperator(String type, String value) {
        String operator = value == null ? "" : value.trim();
        if (("FBD CRAFT ARITHMETIC INT".equals(type)
                || "FBD CRAFT ARITHMETIC FLOAT".equals(type))
                && ("+".equals(operator) || "-".equals(operator)
                || "*".equals(operator) || "/".equals(operator))) return operator;
        if ("FBD CRAFT ARITHMETIC INT".equals(type)
                || "FBD CRAFT ARITHMETIC FLOAT".equals(type)) return "+";
        return normalizeComparisonOperatorStatic(operator);
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
        String projectEntryPath = "";
        java.util.Set<String> entryNames = new java.util.HashSet<>();
        Map<String, byte[]> pendingMedia = new java.util.LinkedHashMap<>();
        long totalUncompressedBytes = 0;
        int entryCount = 0;

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(inputStream)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entryCount > MAX_PROJECT_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("ZIP entry count exceeds limit");
                }
                String name = entry.getName();
                if (!isSafeProjectArchiveEntryName(name)) {
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
                String lowerName = name.toLowerCase(java.util.Locale.ROOT);
                boolean projectCandidate = "tablenameproject.json".equals(lowerName)
                        || lowerName.endsWith(".json") || lowerName.endsWith(".pro");
                if (!entry.isDirectory() && projectCandidate && !lowerName.startsWith("media/")) {
                    if (projectJsonBytes != null) {
                        throw new IllegalArgumentException("Multiple project files in archive");
                    }
                    projectJsonBytes = entryBuffer.toByteArray();
                    projectEntryPath = name;
                } else if (!entry.isDirectory()) {
                    String mediaPath = lowerName.startsWith("media/")
                            ? name.substring("media/".length()) : name;
                    pendingMedia.put(mediaPath, entryBuffer.toByteArray());
                }
            }
        }
        if (projectJsonBytes == null) {
            throw new IllegalArgumentException("No project JSON found in ZIP archive");
        }
        String json = new String(projectJsonBytes, StandardCharsets.UTF_8);
        JSONObject parsed = new JSONObject(json);
        if (CraftProjectImporter.isCraftProject(parsed)) {
            json = CraftProjectImporter.convert(json, projectEntryPath, pendingMedia);        }
        validateImportedProjectStatic(json);
        if (extractedMedia != null) {
            for (String path : pendingMedia.keySet()) {
                for (String existing : extractedMedia.keySet()) {
                    if (existing.equalsIgnoreCase(path)) {
                        throw new IllegalArgumentException("Duplicate extracted media path: " + path);
                    }
                }
            }
            extractedMedia.putAll(pendingMedia);
        }
        return json;
    }

    /** Reject ambiguous and platform-specific paths before interpreting a ZIP member. */
    static boolean isSafeProjectArchiveEntryName(String name) {
        if (name == null || name.isEmpty() || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0 || name.startsWith("/")) return false;
        boolean directory = name.endsWith("/");
        String body = directory ? name.substring(0, name.length() - 1) : name;
        if (body.isEmpty()) return false;
        String[] parts = body.split("/", -1);
        if (parts.length == 0 || parts[0].indexOf(':') >= 0) return false;
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return false;
        }
        return true;
    }

    private boolean persistNestedMacroEdit(boolean synchronous) {
        NestedMacroEditSession session = nestedMacroEdit;
        if (session == null || currentScreenIndex != session.screenIndex) return false;
        try {
            saveActiveScreen();
            int outputNode = findCraftMacroOutputNode(widgets, session.outputWidget);
            if (outputNode < 0)
                throw new IllegalArgumentException("Выходной блок удалён. Назначьте новый выход перед сохранением.");
            session.outputNode = outputNode;
            FbdCraftMacroDefinition updated = updateCraftMacroDefinitionGraph(
                    session.definition, widgets, screens.get(currentScreenIndex).connections,
                    outputNode, session.outputPin);
            FbdCraftMacroCatalog.upsert(projectExtraFields, updated);
            replaceCraftMacroInstances(session.hostScreen.widgets, updated);
            for (int i = 0; i < screens.size(); i++) {
                if (i != session.screenIndex) replaceCraftMacroInstances(screens.get(i).widgets, updated);
            }
            List<Widget> nestedWidgets = new ArrayList<>(widgets);
            session.definition = updated;
            screens.set(session.screenIndex, session.hostScreen);
            widgets.clear();
            for (Widget widget : session.hostScreen.widgets) widgets.add(copyWidget(widget));
            nestedMacroEdit = null;
            boolean saved;
            try {
                saved = persistProjectNow(synchronous);
            } finally {
                nestedMacroEdit = session;
                screens.set(session.screenIndex, session.nestedScreen);
                widgets.clear();
                widgets.addAll(nestedWidgets);
                if (editor != null) editor.invalidate();
            }
            return saved;
        } catch (Exception failure) {
            Toast.makeText(this, "Вложенный граф не сохранён: " + failure.getMessage(),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void chooseNestedMacroOutput(int blockIndex, Widget block) {
        if (nestedMacroEdit == null || blockIndex < 0 || blockIndex >= widgets.size()) return;
        String[] pins = knownFbdPins(block, true);
        if (pins.length == 0) {
            Toast.makeText(this, "У блока нет проверенного выхода", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Выход макроса")
                .setItems(pins, (dialog, which) -> {
                    NestedMacroEditSession session = nestedMacroEdit;
                    int previousNode = session.outputNode;
                    Widget previousWidget = session.outputWidget;
                    String previousPin = session.outputPin;
                    session.outputNode = blockIndex;
                    session.outputWidget = widgets.get(blockIndex);
                    session.outputPin = pins[which];
                    if (persistProjectNow(true)) {
                        Toast.makeText(this, "Выход макроса обновлён", Toast.LENGTH_SHORT).show();
                    } else {
                        session.outputNode = previousNode;
                        session.outputWidget = previousWidget;
                        session.outputPin = previousPin;
                    }
                }).setNegativeButton("Отмена", null).show();
    }

    private void openNestedMacroGraphEditor(int blockIndex) {
        if (operatorMode || nestedMacroEdit != null || blockIndex < 0 || blockIndex >= widgets.size()) return;
        if (!persistProjectNow(true)) return;
        try {
            Screen host = screens.get(currentScreenIndex);
            if (blockIndex >= host.widgets.size()) throw new IllegalArgumentException("Макрос не найден");
            Widget macro = host.widgets.get(blockIndex);
            FbdCraftMacroDefinition definition = macroDefinition(macro);
            if (definition == null) throw new IllegalArgumentException("Определение макроса недействительно");
            JSONObject graph = definition.toJson().optJSONObject("bloodskyGraph");
            if (graph == null) throw new IllegalArgumentException("В этом макросе нет редактируемого BLOODSKY-графа");
            JSONArray widgetArray = graph.optJSONArray("widgets");
            JSONArray connectionArray = graph.optJSONArray("connections");
            if (widgetArray == null || connectionArray == null || widgetArray.length() == 0)
                throw new IllegalArgumentException("Вложенный граф пуст или повреждён");
            Screen nested = new Screen("Макрос: " + definition.getName());
            nested.backgroundColor = host.backgroundColor;
            for (int i = 0; i < widgetArray.length(); i++)
                nested.widgets.add(widgetFromJson(widgetArray.getJSONObject(i)));
            for (int i = 0; i < connectionArray.length(); i++)
                nested.connections.add(connectionFromJson(connectionArray.getJSONObject(i)));
            int outputNode = graph.optInt("outputNode", -1);
            String outputPin = graph.optString("outputPin", "");
            if (outputNode < 0 || outputNode >= nested.widgets.size()
                    || !contains(knownFbdPins(nested.widgets.get(outputNode), true), outputPin))
                throw new IllegalArgumentException("Вложенный выход некорректен");
            NestedMacroEditSession session = new NestedMacroEditSession(
                    currentScreenIndex, host, nested, definition, outputNode, outputPin);
            nestedMacroEdit = session;
            screens.set(currentScreenIndex, nested);
            widgets.clear();
            for (Widget widget : nested.widgets) widgets.add(copyWidget(widget));
            session.outputWidget = widgets.get(outputNode);
            fbdWireHistory.clear();
            fbdWireMode = false;
            wireSource = -1;
            editor = null;
            showEditor();
        } catch (Exception failure) {
            Toast.makeText(this, "Не удалось открыть вложенный граф: " + failure.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static boolean contains(String[] values, String target) {
        if (values == null || target == null) return false;
        for (String value : values) if (target.equals(value)) return true;
        return false;
    }

    private void closeNestedMacroGraphEditor() {
        NestedMacroEditSession session = nestedMacroEdit;
        if (session == null) return;
        if (persistProjectNow(true)) {
            restoreNestedMacroHost(session);
            persistProjectNow(true);
            showEditor();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Закрыть без сохранения?")
                .setMessage("Текущие изменения вложенного графа не прошли проверку.")
                .setNegativeButton("Остаться", null)
                .setPositiveButton("Закрыть", (dialog, which) -> {
                    restoreNestedMacroHost(session);
                    persistProjectNow(true);
                    showEditor();
                }).show();
    }

    private void restoreNestedMacroHost(NestedMacroEditSession session) {
        nestedMacroEdit = null;
        currentScreenIndex = session.screenIndex;
        screens.set(session.screenIndex, session.hostScreen);
        widgets.clear();
        for (Widget widget : session.hostScreen.widgets) widgets.add(copyWidget(widget));
        fbdWireHistory.clear();
        fbdWireMode = false;
        wireSource = -1;
        editor = null;
    }

    private void configureCraftServiceRegisters() {
        CraftServiceRegisters service = CraftServiceRegisters.global();
        service.setScreenCount(screens.size());
        service.setCurrentScreen(currentScreenIndex + 1);
        service.setActions(new CraftServiceRegisters.Actions() {
            @Override public void selectScreen(int oneBasedScreen) {
                mainHandler.post(() -> {
                    if (oneBasedScreen >= 1 && oneBasedScreen <= screens.size()
                            && currentScreenIndex != oneBasedScreen - 1)
                        activateScreen(oneBasedScreen - 1);
                });
            }
            @Override public void applyBackground(int paletteIndex, int color) {
                mainHandler.post(() -> {
                    if (screens.isEmpty()) return;
                    Screen active = screens.get(Math.max(0, Math.min(currentScreenIndex, screens.size() - 1)));
                    if (paletteIndex == 0) {
                        if (active.extraFields.has("craftServicePaletteBaseColor"))
                            active.backgroundColor = active.extraFields.optInt("craftServicePaletteBaseColor", active.backgroundColor);
                        active.extraFields.remove("craftServicePaletteBaseColor");
                    } else {
                        if (!active.extraFields.has("craftServicePaletteBaseColor"))
                            try { active.extraFields.put("craftServicePaletteBaseColor", active.backgroundColor); } catch (Exception ignored) {}
                        active.backgroundColor = color;
                    }
                    if (editor != null) editor.invalidate();
                    persistProject();
                });
            }
            @Override public void enterPassword(int candidate) {
                // Craft 1.0.7 submits this candidate to its project-password
                // gate. BLOODSKY project locking is not implemented yet.
                android.util.Log.d("BLOODSKY", "Craft service password candidate received");
            }
            @Override public void saveProject() {
                mainHandler.post(() -> persistProjectNow(true));
            }
            @Override public void refreshComponents() {
                mainHandler.post(() -> { if (editor != null) editor.invalidate(); });
            }
            @Override public void openProjectUrl() {
                mainHandler.post(() -> {
                    String url = projectExtraFields.optString("projectUrl",
                            projectExtraFields.optString("url", ""));
                    if (url.isEmpty()) {
                        Toast.makeText(MainActivity.this, "URL проекта не задан", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception failure) {
                        Toast.makeText(MainActivity.this, "Не удалось открыть URL проекта", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void exitApplication() {
                mainHandler.post(() -> finishAffinity());
            }
            @Override public void logCascade() {
                android.util.Log.d("BLOODSKY", "каскада");
            }
        });
        updateCraftDeviceRegisters();
    }

    private void updateCraftDeviceRegisters() {
        CraftServiceRegisters service = CraftServiceRegisters.global();
        if (editor != null && !screens.isEmpty()) {
            service.setScreenCount(screens.size());
            service.setCurrentScreen(currentScreenIndex + 1);
        }
        Intent battery = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int status = battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                    || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
            service.setCharging(charging);
            service.setBatteryLevel(level);
        }
        try {
            service.setBrightness(android.provider.Settings.System.getInt(
                    getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS));
        } catch (android.provider.Settings.SettingNotFoundException ignored) {}
    }

    private void persistProject() { persistProjectNow(false); }

    private boolean persistProjectNow(boolean synchronous) {
        try {
            if (nestedMacroEdit != null) return persistNestedMacroEdit(synchronous);
            saveActiveScreen();
            JSONObject project = new JSONObject();
            if (projectExtraFields != null) {
                java.util.Iterator<String> projectKeys = projectExtraFields.keys();
                while (projectKeys.hasNext()) { String key = projectKeys.next(); project.put(key, projectExtraFields.get(key)); }
            }
            CraftProjectNetworkBridge.sync(project);
            if (systemRegisterBankPersistable) project.put("systemRegisters", systemRegisterBank.toProjectJson());
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
        FbdCraftRegisterStore.clear();
        FbdCraftNamedVariableStore.clear();
        imageCache.clear();
        gifCache.clear();
        widgets.clear(); screens.clear(); currentScreenIndex = 0;
        projectExtraFields = new JSONObject();
        systemRegisterBank.clear();
        systemRegisterBankPersistable = true;
        projectName = prefs.getString("project_name", projectName);
        try {
            Object parsed = new org.json.JSONTokener(json).nextValue();
            if (parsed instanceof JSONObject) {
                JSONObject project = (JSONObject) parsed;
                String importedProjectName = project.optString("projectName", projectName);
                if (importedProjectName != null && !importedProjectName.trim().isEmpty()) projectName = importedProjectName.trim();
                try { projectExtraFields = new JSONObject(project.toString()); } catch (Exception ignored) { projectExtraFields = new JSONObject(); }
                Object savedSystemRegisterBank = project.opt("systemRegisters");
                if (savedSystemRegisterBank instanceof JSONArray) {
                    try { systemRegisterBank.restoreProjectJson((JSONArray) savedSystemRegisterBank); }
                    catch (Exception invalidBank) {
                        systemRegisterBankPersistable = false;
                        android.util.Log.w("BLOODSKY", "Preserving invalid imported systemRegisters field", invalidBank);
                    }
                } else if (savedSystemRegisterBank instanceof String) {
                    projectExtraFields.put("systemRegisterSettings", savedSystemRegisterBank);
                    projectExtraFields.remove("systemRegisters");
                } else if (savedSystemRegisterBank != null && savedSystemRegisterBank != JSONObject.NULL) {
                    systemRegisterBankPersistable = false;
                }
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
        try { FbdCraftMacroCatalog.read(project); }
        catch (Exception invalidCatalog) { throw new IllegalArgumentException("Invalid Craft macro catalog"); }
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
                Widget sourceForPins = widgetFromJson(sourceWidget);
                Widget targetForPins = widgetFromJson(targetWidget);
                if (!hasCompatibleKnownFbdPins(edge, sourceForPins, targetForPins)) {
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
            if ("FBD CRAFT MACRO".equals(canonicalFbdBlockType(type))) {
                Widget parsed = widgetFromJson(widget);                if (macroDefinition(parsed) == null)
                    throw new IllegalArgumentException("FBD macro widget has no valid definition");
            }
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

    private static final class NestedMacroEditSession {
        final int screenIndex;
        final Screen hostScreen;
        final Screen nestedScreen;
        FbdCraftMacroDefinition definition;
        int outputNode;
        Widget outputWidget;
        String outputPin;
        NestedMacroEditSession(int screenIndex, Screen hostScreen, Screen nestedScreen,
                FbdCraftMacroDefinition definition, int outputNode, String outputPin) {
            this.screenIndex = screenIndex;
            this.hostScreen = hostScreen;
            this.nestedScreen = nestedScreen;
            this.definition = definition;
            this.outputNode = outputNode;
            this.outputPin = outputPin;
        }
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

    public static boolean isCraftFbdSensorSource(String type) {
        return "FBD CRAFT SENSOR LIGHT".equals(canonicalFbdBlockType(type))
                || "FBD CRAFT SENSOR ORIENTATION".equals(canonicalFbdBlockType(type))
                || "FBD CRAFT SENSOR SPEED".equals(canonicalFbdBlockType(type));
    }

    private static boolean isCraftSunriseSource(String type) {
        return "FBD CRAFT SUNRISE".equals(canonicalFbdBlockType(type));
    }

    private static boolean isCraftOdTempSource(String type) {
        return "FBD CRAFT ODTEMP".equals(canonicalFbdBlockType(type));
    }

    public static boolean isCraftMqttSubscriber(String type) {
        return type != null && type.startsWith("FBD CRAFT MQTT SUB ");
    }
    public static boolean isCraftMqttPublisher(String type) {
        return type != null && type.startsWith("FBD CRAFT MQTT PUB ");
    }
    private static int mqttType(Widget widget) {
        int fallback = widget.type.endsWith("BOOL") ? 1 : widget.type.endsWith("FLOAT") ? 3 : 2;
        return Math.max(1, Math.min(3, widget.extraFields.optInt("craftMqttType", fallback)));
    }
    private static int mqttServer(Widget widget) {
        return Math.max(1, Math.min(2, widget.extraFields.optInt("craftMqttServer", 1)));
    }

    public static boolean isAllowedOdTempHost(String host) {
        if (host == null) return false;
        String[] parts = host.trim().split("\\.", -1);
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                if (parts[i].isEmpty() || (parts[i].length() > 1 && parts[i].charAt(0) == '0')) return false;
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) return false;
            }
        } catch (NumberFormatException invalid) { return false; }
        return octets[0] == 10 || octets[0] == 192 && octets[1] == 168
                || octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31
                || octets[0] == 169 && octets[1] == 254;
    }

    private static String craftOrientationLabel(int axis) {
        return axis == 2 ? "ORIENTATION_Y" : axis == 3 ? "ORIENTATION_Z" : "ORIENTATION_X";
    }

    private static int craftOrientationAxis(Widget widget) {
        return Math.max(1, Math.min(3, widget.extraFields.optInt("craftSensorAxis",
                widget.extraFields.optInt("type", 1))));
    }

    private static String craftSunriseMode(Widget widget) {
        return widget.extraFields.optString("craftSunriseMode",
                widget.extraFields.optInt("type", 1) == 2 ? "SS" : "SR");
    }

    public static boolean isCraftRegisterSource(String type) {
        String kind = canonicalFbdBlockType(type);
        return "FBD CRAFT VAR BOOL".equals(kind) || "FBD CRAFT VAR INT".equals(kind)
                || "FBD CRAFT VAR FLOAT".equals(kind);
    }

    public static boolean isCraftNamedVariableSource(String type) {
        return type != null && type.startsWith("FBD CRAFT VAR NAME IN ");
    }

    public static boolean isCraftNamedVariableSink(String type) {
        return type != null && type.startsWith("FBD CRAFT VAR NAME OUT ");
    }

    private static int craftNamedVariableKind(String type) {
        if (type == null) return 0;
        if (type.endsWith(" BOOL")) return 1;
        if (type.endsWith(" INT")) return 2;
        if (type.endsWith(" FLOAT")) return 3;
        return 0;
    }

    private static String craftNamedVariableType(int kind, boolean output) {
        String suffix = kind == 1 ? "BOOL" : kind == 3 ? "FLOAT" : "INT";
        return "FBD CRAFT VAR NAME " + (output ? "OUT " : "IN ") + suffix;
    }

    private static String craftNamedVariableName(Widget widget) {
        return widget.extraFields.optString("craftVariableName",
                widget.extraFields.optString("name", ""));
    }

    public static boolean isCraftRegisterSink(String type) {
        String kind = canonicalFbdBlockType(type);
        return "FBD CRAFT VAR OUT BOOL".equals(kind) || "FBD CRAFT VAR OUT INT".equals(kind)
                || "FBD CRAFT VAR OUT FLOAT".equals(kind);
    }

    private static boolean isCraftRegisterFloat(String type) {
        String kind = canonicalFbdBlockType(type);
        return "FBD CRAFT VAR FLOAT".equals(kind) || "FBD CRAFT VAR OUT FLOAT".equals(kind);
    }

    private static boolean isCraftRegisterBoolean(String type) {
        String kind = canonicalFbdBlockType(type);
        return "FBD CRAFT VAR BOOL".equals(kind) || "FBD CRAFT VAR OUT BOOL".equals(kind);
    }

    public static String knownFbdPinType(String blockType, String pin, boolean output) {
        if (blockType == null || pin == null) return null;
        String kind = canonicalFbdBlockType(blockType);
        String name = canonicalFbdPinName(pin);
        if (kind.equals("FBD ВХОД")) return output && "Q".equals(name) ? "BOOL" : null;
        if (isCraftMqttSubscriber(kind) && output && "Q".equals(name))
            return kind.endsWith("BOOL") ? "BOOL" : "NUMBER";
        if (isCraftMqttPublisher(kind) && !output) {
            if ("EN".equals(name)) return "BOOL";
            if ("IN".equals(name)) return kind.endsWith("BOOL") ? "BOOL" : "NUMBER";
        }
        if (isCraftRegisterSource(kind) && output && "Q".equals(name))
            return isCraftRegisterBoolean(kind) ? "BOOL" : "NUMBER";
        if (isCraftRegisterSink(kind) && !output && "IN".equals(name))
            return isCraftRegisterBoolean(kind) ? "BOOL" : "NUMBER";
        if ((isCraftFbdSensorSource(kind) || isCraftSunriseSource(kind) || isCraftOdTempSource(kind))
                && output && "Q".equals(name)) return "NUMBER";
        if (isCraftNamedVariableSource(kind) && output && "Q".equals(name))
            return craftNamedVariableKind(kind) == 1 ? "BOOL" : "NUMBER";
        if (isCraftNamedVariableSink(kind) && !output && "IN".equals(name))
            return craftNamedVariableKind(kind) == 1 ? "BOOL" : "NUMBER";
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
        if (kind.equals("FBD CRAFT TIMER")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && "IN".equals(name)) return "BOOL";
            if (!output && ("MS".equals(name) || "PT".equals(name))) return "NUMBER";
        }
        if (kind.equals("FBD CRAFT ARITHMETIC INT")
                || kind.equals("FBD CRAFT ARITHMETIC FLOAT")) {
            if (output && "Q".equals(name)) return "NUMBER";
            if (!output && ("IN1".equals(name) || "IN2".equals(name))) return "NUMBER";
        }
        if (kind.equals("FBD CRAFT COMPARE FLOAT")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && ("IN1".equals(name) || "IN2".equals(name))) return "NUMBER";
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
        if (kind.equals("FBD CRAFT COMPARE")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && ("IN1".equals(name) || "IN2".equals(name))) return "NUMBER";
        }
        if (kind.equals("FBD CRAFT CHANGE I") || kind.equals("FBD CRAFT CHANGE F")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && ("FBD CRAFT CHANGE I".equals(kind) ? "I" : "F").equals(name))
                return "NUMBER";
        }
        if (kind.equals("FBD CRAFT MOVE")) {
            if (output && "Q".equals(name)) return "NUMBER";
            if (!output && "EN".equals(name)) return "BOOL";
            if (!output && "I".equals(name)) return "NUMBER";
        }
        if (kind.equals("FBD CRAFT MX")) {
            if (output && "Q".equals(name)) return "NUMBER";
            if (!output && "EN".equals(name)) return "BOOL";
            if (!output && "I".equals(name)) return "NUMBER";
        }
        if (kind.equals("FBD CRAFT ARRAY")) {
            if (output && "Q".equals(name)) return "NUMBER";
            if (!output && "EN".equals(name)) return "BOOL";
            if (!output && ("DT".equals(name) || "ID".equals(name))) return "NUMBER";
        }
        if (kind.equals("FBD CRAFT LOG") || kind.equals("FBD CRAFT WORD TO FLOAT")
                || kind.equals("FBD CRAFT SIN") || kind.equals("FBD CRAFT ABS")
                || kind.equals("FBD CRAFT INT TO FLOAT") || kind.equals("FBD CRAFT FLOAT TO INT")) {
            if (output && "OUT".equals(name)) return "NUMBER";
            if (!output && "IN".equals(name)) return "NUMBER";
        }
        if (kind.equals("FBD CRAFT POW")) {
            if (output && "Q".equals(name)) return "NUMBER";
            if (!output && ("X".equals(name) || "Y".equals(name))) return "NUMBER";
        }
        if (kind.equals("FBD CRAFT SEL")) {
            if (output && "Q".equals(name)) return "NUMBER";
            if (!output && "IN".equals(name)) return "BOOL";
            if (!output && ("I1".equals(name) || "I2".equals(name))) return "NUMBER";
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
                || kind.equals("FBD TP") || kind.equals("FBD CRAFT BLINK")
                || kind.equals("FBD CRAFT TIMER")) {
            if (output && "Q".equals(name)) return "BOOL";
            if (!output && "IN".equals(name)) return "BOOL";
            if (!output && "PT".equals(name)) return "NUMBER";
        }
        return null;
    }

    public static String[] knownFbdPins(Widget widget, boolean output) {
        if (widget == null) return new String[0];
        if ("FBD CRAFT MACRO".equals(canonicalFbdBlockType(widget.type))) {
            FbdCraftMacroDefinition definition = macroDefinition(widget);
            if (definition == null) return new String[0];
            if (output) return new String[]{definition.getOutput().name};
            String[] pins = new String[definition.getInputs().size()];
            for (int i = 0; i < pins.length; i++) pins[i] = definition.getInputs().get(i).name;
            return pins;
        }
        if ("FBD CRAFT MACRO IN".equals(canonicalFbdBlockType(widget.type)))
            return output ? new String[]{"Q"} : new String[0];
        return knownFbdPins(widget.type, output);
    }

    public static String knownFbdPinType(Widget widget, String pin, boolean output) {
        if (widget == null || pin == null) return null;
        String kind = canonicalFbdBlockType(widget.type);
        String canonicalPin = canonicalFbdPinName(pin);
        if ("FBD CRAFT MACRO".equals(kind)) {
            FbdCraftMacroDefinition definition = macroDefinition(widget);
            if (definition == null) return null;
            FbdCraftMacroDefinition.Port port = output ? definition.getOutput() : null;
            if (output && canonicalFbdPinName(port.name).equals(canonicalPin))
                return port.type == 1 ? "BOOL" : "NUMBER";
            if (!output) for (FbdCraftMacroDefinition.Port input : definition.getInputs())
                if (canonicalFbdPinName(input.name).equals(canonicalPin))
                    return input.type == 1 ? "BOOL" : "NUMBER";
            return null;
        }
        if ("FBD CRAFT MACRO IN".equals(kind) && output && "Q".equals(canonicalPin)) {
            int type = widget.extraFields.optInt("craftMacroPortType", -1);
            return type == 1 ? "BOOL" : (type == 2 || type == 3 ? "NUMBER" : null);
        }
        return knownFbdPinType(widget.type, pin, output);
    }

    private static FbdCraftMacroDefinition macroDefinition(Widget widget) {
        if (widget == null || widget.extraFields == null) return null;
        JSONObject json = widget.extraFields.optJSONObject("craftMacroDefinition");
        if (json == null) return null;
        if (json == widget.craftMacroDefinitionSourceCache)
            return widget.craftMacroDefinitionCache;
        try {
            widget.craftMacroDefinitionCache = FbdCraftMacroDefinition.fromJson(json.toString());
            try {
                widget.craftMacroDefinitionCache = FbdCraftMacroGraphAdapter.expandIfSupported(
                        widget.craftMacroDefinitionCache);
            } catch (Exception unsupportedLegacyGraph) {
                // Keep the original macro inspectable and lossless; execution stays fail-closed.
            }
            widget.craftMacroDefinitionSourceCache = json;
            return widget.craftMacroDefinitionCache;
        } catch (Exception invalid) {
            widget.craftMacroDefinitionCache = null;
            widget.craftMacroDefinitionSourceCache = json;
            return null;
        }
    }

    private static boolean hasCraftMacroGraph(FbdCraftMacroDefinition definition) {
        if (definition == null) return false;
        try { return definition.toJson().optJSONObject("bloodskyGraph") != null; }
        catch (Exception invalid) { return false; }
    }

    private static boolean isDynamicMacroWidget(Widget widget) {
        String kind = widget == null ? "" : canonicalFbdBlockType(widget.type);
        return "FBD CRAFT MACRO".equals(kind) || "FBD CRAFT MACRO IN".equals(kind);
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

    public static boolean hasCompatibleKnownFbdPins(JSONObject edge, Widget sourceWidget,
                                                     Widget targetWidget) throws Exception {
        if (sourceWidget == null || targetWidget == null || !canLoadFbdConnection(edge)) return false;
        if (!edge.has("sourcePin") && !edge.has("targetPin")) return true;
        String sourcePin = edge.optString("sourcePin", "");
        String targetPin = edge.optString("targetPin", "");
        String sourceKnown = knownFbdPinType(sourceWidget, sourcePin, true);
        String targetKnown = knownFbdPinType(targetWidget, targetPin, false);
        boolean sourceBlockKnown = knownFbdPins(sourceWidget, true).length > 0
                || knownFbdPins(sourceWidget, false).length > 0;
        boolean targetBlockKnown = knownFbdPins(targetWidget, true).length > 0
                || knownFbdPins(targetWidget, false).length > 0;
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
        Object sourcePin = json.opt("sourcePin");        Object targetPin = json.opt("targetPin");
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

    public static boolean evaluateFbdBlock(List<Widget> widgetsList, List<FbdConnection> connectionsList,
            int index, boolean[] visiting, boolean[] cacheValid, boolean[] cacheValues, double[] cacheNumeric,
            CraftSystemRegisterBank userRegisters) {
        return evaluateFbdBlock(widgetsList, connectionsList, index, visiting, cacheValid, cacheValues,
                cacheNumeric, userRegisters, false);
    }

    private static boolean evaluateFbdBlock(List<Widget> widgetsList, List<FbdConnection> connectionsList,
            int index, boolean[] visiting, boolean[] cacheValid, boolean[] cacheValues, double[] cacheNumeric,
            CraftSystemRegisterBank userRegisters, boolean allowSystemRegisterWrites) {
        CraftSystemRegisterBank previous = setActiveFbdSystemRegisterBank(userRegisters);
        Boolean previousWriteState = ACTIVE_FBD_SYSTEM_REGISTER_WRITES.get();
        ACTIVE_FBD_SYSTEM_REGISTER_WRITES.set(allowSystemRegisterWrites);
        try {
            return evaluateFbdBlock(widgetsList, connectionsList, index, visiting,
                    cacheValid, cacheValues, cacheNumeric);
        } finally {
            restoreActiveFbdSystemRegisterBank(previous);
            if (previousWriteState == null) ACTIVE_FBD_SYSTEM_REGISTER_WRITES.remove();
            else ACTIVE_FBD_SYSTEM_REGISTER_WRITES.set(previousWriteState);
        }
    }

    private static CraftSystemRegisterBank setActiveFbdSystemRegisterBank(
            CraftSystemRegisterBank userRegisters) {
        CraftSystemRegisterBank previous = ACTIVE_FBD_SYSTEM_REGISTER_BANK.get();
        if (userRegisters == null) ACTIVE_FBD_SYSTEM_REGISTER_BANK.remove();
        else ACTIVE_FBD_SYSTEM_REGISTER_BANK.set(userRegisters);
        return previous;
    }

    private static void restoreActiveFbdSystemRegisterBank(CraftSystemRegisterBank previous) {
        if (previous == null) ACTIVE_FBD_SYSTEM_REGISTER_BANK.remove();
        else ACTIVE_FBD_SYSTEM_REGISTER_BANK.set(previous);
    }

    public static boolean evaluateFbdBlock(List<Widget> widgetsList, List<FbdConnection> connectionsList, int index, boolean[] visiting, boolean[] cacheValid, boolean[] cacheValues, double[] cacheNumeric) {
        if (index < 0 || index >= widgetsList.size()) return false;
        if (cacheValid[index]) return cacheValues[index];
        boolean result = evaluateFbdBlockUncached(widgetsList, connectionsList, index, visiting, cacheValid, cacheValues, cacheNumeric);
        if (isFbdBlockOutputInverted(widgetsList.get(index))
                && hasCompleteInvertibleBooleanInputs(widgetsList, connectionsList, index)) result = !result;
        cacheValues[index] = result;
        cacheValid[index] = true;
        return result;
    }

    /** Craft's inherited `invertOut` is supported only for source-backed Boolean Q outputs. */
    public static boolean supportsFbdBlockOutputInversion(String type) {
        if (type == null) return false;
        String canonical = canonicalFbdBlockType(type);
        return "FBD AND".equals(canonical) || "FBD OR".equals(canonical)
                || "FBD XOR".equals(canonical) || "FBD CRAFT VAR BOOL".equals(canonical)
                || (isCraftNamedVariableSource(canonical) && craftNamedVariableKind(canonical) == 1)
                || "FBD RS".equals(canonical)
                || "FBD CRAFT T".equals(canonical)
                || "FBD CRAFT TIMER".equals(canonical)
                || "FBD CRAFT BLINK".equals(canonical)
                || "FBD CRAFT COMPARE".equals(canonical)
                || "FBD CRAFT COMPARE FLOAT".equals(canonical)
                || "FBD CRAFT CHANGE I".equals(canonical)
                || "FBD CRAFT CHANGE F".equals(canonical);
    }

    private static boolean hasCompleteInvertibleBooleanInputs(List<Widget> widgets,
                                                               List<FbdConnection> connections,
                                                               int targetIndex) {
        if (widgets == null || targetIndex < 0 || targetIndex >= widgets.size()) return false;
        Widget target = widgets.get(targetIndex);
        if (isCraftRegisterBoolean(target.type)
                || (isCraftNamedVariableSource(target.type) && craftNamedVariableKind(target.type) == 1))
            return true;
        if ("FBD AND".equals(canonicalFbdBlockType(target.type))
                || "FBD OR".equals(canonicalFbdBlockType(target.type))
                || "FBD XOR".equals(canonicalFbdBlockType(target.type)))
            return hasCompleteCraftGateInputs(widgets, connections, targetIndex);
        if (!supportsFbdBlockOutputInversion(target.type) || connections == null) return false;

        String[] required = knownFbdPins(target.type, false);
        if (required.length == 0) return false;
        java.util.Set<String> requiredPins = new java.util.HashSet<>();
        java.util.Set<String> connectedPins = new java.util.HashSet<>();
        for (String pin : required) requiredPins.add(canonicalFbdPinName(pin));
        int legacyInputs = 0;
        boolean sawNamed = false, sawLegacy = false;
        for (FbdConnection edge : connections) {
            if (edge == null || edge.target != targetIndex) continue;
            if (edge.source < 0 || edge.source >= widgets.size() || edge.extraFields == null) return false;
            String targetPin = canonicalFbdPinName(edge.extraFields.optString("targetPin", ""));
            if (targetPin.isEmpty()) { sawLegacy = true; legacyInputs++; }
            else {
                sawNamed = true;
                if (!requiredPins.contains(targetPin) || !connectedPins.add(targetPin)) return false;
            }
        }
        if (sawNamed && sawLegacy) return false;
        if (sawNamed ? connectedPins.size() != requiredPins.size()
                : legacyInputs != requiredPins.size()) return false;

        int legacyIndex = 0;
        for (FbdConnection edge : connections) {
            if (edge == null || edge.target != targetIndex) continue;
            Widget source = widgets.get(edge.source);
            if (source == null) return false;
            String sourcePin = canonicalFbdPinName(edge.extraFields.optString("sourcePin", ""));
            String targetPin = canonicalFbdPinName(edge.extraFields.optString("targetPin", ""));
            if (targetPin.isEmpty()) targetPin = canonicalFbdPinName(required[legacyIndex++]);
            String[] sourcePins = knownFbdPins(source.type, true);
            if (sourcePin.isEmpty()) {
                if (sourcePins.length != 1) return false;
                sourcePin = canonicalFbdPinName(sourcePins[0]);
            }
            String sourceSignal = knownFbdPinType(source.type, sourcePin, true);
            String targetSignal = knownFbdPinType(target.type, targetPin, false);
            if (sourceSignal == null || targetSignal == null || !sourceSignal.equals(targetSignal))
                return false;
        }
        return true;
    }

    private static boolean hasCompleteCraftGateInputs(List<Widget> widgets,
                                                       List<FbdConnection> connections,
                                                       int targetIndex) {
        if (connections == null) return false;
        java.util.Set<String> named = new java.util.HashSet<>();
        int legacyCount = 0;
        boolean sawNamed = false, sawLegacy = false;
        for (FbdConnection edge : connections) {
            if (edge == null || edge.target != targetIndex) continue;
            if (edge.source < 0 || edge.source >= widgets.size() || edge.extraFields == null)
                return false;
            String sourcePin = canonicalFbdPinName(edge.extraFields.optString("sourcePin", ""));
            String[] sourcePins = knownFbdPins(widgets.get(edge.source).type, true);
            if (sourcePin.isEmpty()) {
                if (sourcePins.length != 1 || !"BOOL".equals(knownFbdPinType(
                        widgets.get(edge.source).type, sourcePins[0], true))) return false;
            } else if (!"BOOL".equals(knownFbdPinType(
                    widgets.get(edge.source).type, sourcePin, true))) return false;
            String targetPin = canonicalFbdPinName(edge.extraFields.optString("targetPin", ""));
            if (targetPin.isEmpty()) { sawLegacy = true; legacyCount++; }
            else { sawNamed = true; named.add(targetPin); }
        }
        if (sawNamed && sawLegacy) return false;
        return sawNamed ? named.size() == 2 && named.contains("IN1") && named.contains("IN2")
                : legacyCount == 2;
    }

    public static boolean isFbdBlockOutputInverted(Widget widget) {
        return widget != null && widget.extraFields != null
                && supportsFbdBlockOutputInversion(widget.type)
                && widget.extraFields.optBoolean("invertOut", false);
    }

    /** Toggle only the known Craft-compatible field, retaining every other JSON field. */
    public static boolean toggleFbdBlockOutputInversion(Widget widget) throws Exception {
        if (widget == null || !supportsFbdBlockOutputInversion(widget.type)) return false;
        if (widget.extraFields == null) widget.extraFields = new JSONObject();
        widget.extraFields.put("invertOut", !isFbdBlockOutputInverted(widget));
        return true;
    }

    public static double evaluateFbdBlockNumeric(List<Widget> widgetsList, List<FbdConnection> connectionsList,
            int index, boolean[] visiting, boolean[] cacheValid, boolean[] cacheValues, double[] cacheNumeric,
            CraftSystemRegisterBank userRegisters) {
        CraftSystemRegisterBank previous = setActiveFbdSystemRegisterBank(userRegisters);
        try {
            return evaluateFbdBlockNumeric(widgetsList, connectionsList, index, visiting,
                    cacheValid, cacheValues, cacheNumeric);
        } finally {
            restoreActiveFbdSystemRegisterBank(previous);
        }
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

    /** Original FB_TIMER.f() returns without changing state until IN and MS resolve. */
    public static boolean fbdCraftTimerInputsReady(List<Double> values, List<String> pins) {
        double input = fbdNumericPinValue(values, pins, "IN", 0, Double.NaN);
        double millis = fbdNumericPinValue(values, pins, "MS", 1, Double.NaN);
        if (Double.isNaN(millis)) millis = fbdNumericPinValue(values, pins, "PT", 1, Double.NaN);
        return Double.isFinite(input) && FbdCraftIntegerSelector.exactInteger(millis) != null;
    }

    /** Original FB_TIMER takes an Integer MS input; PT remains a legacy BLOODSKY alias. */
    public static long fbdCraftTimerPresetMs(List<Double> values, List<String> pins,
                                             int configuredMs, String unit) {
        double raw = fbdNumericPinValue(values, pins, "MS", -1, Double.NaN);
        if (Double.isNaN(raw)) raw = fbdNumericPinValue(values, pins, "PT", -1, Double.NaN);
        if (Double.isNaN(raw)) return Math.max(1, Math.min(3600000, configuredMs));
        Integer exact = FbdCraftIntegerSelector.exactInteger(raw);
        if (exact == null) return Math.max(1, Math.min(3600000, configuredMs));
        String normalized = FbdDurationUnits.normalize(unit);
        int code = "SEK".equals(normalized) ? 2 : "MIN".equals(normalized) ? 3 : 1;
        try {
            return FbdCraftNativeTimer.durationMillis(exact, code);
        } catch (IllegalArgumentException invalid) {
            return Math.max(1, Math.min(3600000, configuredMs));
        }
    }

    /** Source LOG consumes and returns Float, so preserve its Float32 rounding. */
    public static double fbdCraftLogFloat32(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return Double.NaN;
        return (float) Math.log((float) value);
    }

    /** Source WordToFloat accepts a Java Integer and returns its Float value. */
    public static double fbdCraftWordToFloat(double value) {
        Integer exact = FbdCraftIntegerSelector.exactInteger(value);
        return exact == null ? Double.NaN : (float) exact.intValue();
    }

    /** Original IntToFloat narrows the signed Integer to Java short first. */
    public static double fbdCraftIntToFloat(double value) {
        Integer exact = FbdCraftIntegerSelector.exactInteger(value);
        return exact == null ? Double.NaN : (float) (short) exact.intValue();
    }

    /** Original FloatToInt casts a Float to int, truncating toward zero. */
    public static double fbdCraftFloatToInt(double value) {
        if (!Double.isFinite(value)) return Double.NaN;
        return (int) (float) value;
    }

    public static double fbdCraftUnaryFloat32(double value, String operation) {
        if (!Double.isFinite(value)) return Double.NaN;
        float input = (float) value;
        float result;
        if ("ABS".equals(operation)) result = Math.abs(input);
        else if ("SIN".equals(operation)) result = (float) Math.sin(input);
        else return Double.NaN;
        return Float.isFinite(result) ? result : Double.NaN;
    }

    public static double fbdCraftPowFloat32(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return Double.NaN;
        float result = (float) Math.pow((float) x, (float) y);
        return Float.isFinite(result) ? result : Double.NaN;
    }

    public static boolean evaluateFbdBlockUncached(List<Widget> widgetsList, List<FbdConnection> connectionsList,
            int index, boolean[] visiting, boolean[] cacheValid, boolean[] cacheValues, double[] cacheNumeric,
            CraftSystemRegisterBank userRegisters) {
        CraftSystemRegisterBank previous = setActiveFbdSystemRegisterBank(userRegisters);
        try {
            return evaluateFbdBlockUncached(widgetsList, connectionsList, index, visiting,
                    cacheValid, cacheValues, cacheNumeric);
        } finally {
            restoreActiveFbdSystemRegisterBank(previous);
        }
    }

    public static boolean evaluateFbdBlockUncached(List<Widget> widgetsList, List<FbdConnection> connectionsList, int index, boolean[] visiting, boolean[] cacheValid, boolean[] cacheValues, double[] cacheNumeric) {
        if (index < 0 || index >= widgetsList.size() || visiting[index]) return false;
        Widget widget = widgetsList.get(index);
        if (!isFbdWidget(widget)) return false;
        if ("FBD CRAFT MACRO".equals(canonicalFbdBlockType(widget.type)))
            return evaluateCraftMacroBlock(widgetsList, connectionsList, index, visiting,
                    cacheValid, cacheValues, cacheNumeric);
        if ("FBD CRAFT MACRO IN".equals(canonicalFbdBlockType(widget.type))) {
            double value = widget.craftMacroInputValid ? widget.craftMacroInputValue : Double.NaN;
            cacheNumeric[index] = value;
            return Double.isFinite(value) && value != 0.0;
        }
        if (isCraftMqttSubscriber(widget.type)) {
            double value = widget.craftMqttValue == null ? Double.NaN : widget.craftMqttValue;
            cacheNumeric[index] = value;
            return Double.isFinite(value) && value != 0.0;
        }
        if (isCraftOdTempSource(widget.type)) {
            double value = widget.craftSensorValid ? widget.craftSensorValue : Double.NaN;
            cacheNumeric[index] = value;
            return Double.isFinite(value) && value != 0.0;
        }
        if (isCraftSunriseSource(widget.type)) {
            int value = FbdCraftSunrise.currentLocalHhmm(
                    widget.extraFields.optDouble("latitude", Double.NaN),
                    widget.extraFields.optDouble("longitude", Double.NaN),
                    "SS".equals(craftSunriseMode(widget)),
                    System.currentTimeMillis(), java.util.TimeZone.getDefault());
            cacheNumeric[index] = value;
            return value != 0;
        }
        if (isCraftFbdSensorSource(widget.type)) {
            double value = Double.NaN;
            if (widget.craftSensorValid) {
                if ("FBD CRAFT SENSOR LIGHT".equals(widget.type)
                        || "FBD CRAFT SENSOR SPEED".equals(widget.type)) value = widget.craftSensorValue;
                else {
                    int axis = craftOrientationAxis(widget);
                    if (axis >= 1 && axis <= 3) value = widget.craftOrientationValues[axis - 1];
                }
            }
            cacheNumeric[index] = value;
            return Double.isFinite(value) && value != 0.0;
        }
        if (isCraftNamedVariableSource(widget.type)) {
            int kind = craftNamedVariableKind(widget.type);
            double value = FbdCraftNamedVariableStore.read(widget.craftVariableNamespace,
                    craftNamedVariableName(widget), kind);
            if (widget.extraFields.optBoolean("craftNamedConstantMode",
                    widget.extraFields.optBoolean("isConstant", false))) {
                try { value = Double.parseDouble(widget.extraFields.optString("craftNamedConstant",
                        widget.extraFields.optString("constant", "0"))); }
                catch (Exception ignored) { value = Double.NaN; }
            }
            cacheNumeric[index] = value;
            return Double.isFinite(value) && value != 0.0;
        }
        if (isCraftRegisterSource(widget.type)) {
            double value = Double.NaN;
            if (widget.extraFields.optBoolean("craftVarConstantMode", false)) {
                try { value = Double.parseDouble(widget.extraFields.optString("craftVarConstant", "0")); }
                catch (Exception ignored) { value = Double.NaN; }
            } else {
                String connection = widget.connectionInfo == null ? "" : widget.connectionInfo.trim();
                if (widget.craftRegisterValid) {
                    try {
                        if (isCraftRegisterBoolean(widget.type))
                            value = FbdCraftRegisterCodec.booleanValue(widget.craftRegisterWord0,
                                    widget.extraFields.optInt("craftVarBit", 0), false) ? 1.0 : 0.0;
                        else if ("FBD CRAFT VAR INT".equals(canonicalFbdBlockType(widget.type)))
                            value = widget.craftRegisterWord0;
                        else value = FbdCraftRegisterCodec.floatValue(widget.craftRegisterWord0,
                                    widget.craftRegisterWord1);
                    } catch (Exception ignored) { value = Double.NaN; }
                } else if (connection.isEmpty()) {
                    try {
                        CraftSystemRegisterBank bank = ACTIVE_FBD_SYSTEM_REGISTER_BANK.get();
                        int address = widget.extraFields.optInt("reg", -1);
                        if (isCraftRegisterBoolean(widget.type))
                            value = CraftSystemRegisterAccess.readBool(address,
                                    widget.extraFields.optInt("bit", widget.extraFields.optInt("craftVarBit", 0)),
                                    false, bank) ? 1.0 : 0.0;
                        else if ("FBD CRAFT VAR INT".equals(canonicalFbdBlockType(widget.type)))
                            value = CraftSystemRegisterAccess.readInt(address, bank);
                        else value = CraftSystemRegisterAccess.readFloat(address,
                                    widget.extraFields.optBoolean("craftSwapWord", false),
                                    widget.extraFields.optBoolean("craftSwapByte", false), bank);
                    } catch (Exception ignored) { value = Double.NaN; }
                }
            }
            cacheNumeric[index] = value;
            return Double.isFinite(value) && value != 0.0;
        }
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

        if (isCraftRegisterSink(widget.type)) {
            if (inputPins.isEmpty()) cacheNumeric[index] = Double.NaN;
            else if (isCraftRegisterBoolean(widget.type)) cacheNumeric[index] = inputs.get(0) ? 1.0 : 0.0;
            else cacheNumeric[index] = numInputs.get(0);
            String connection = widget.connectionInfo == null ? "" : widget.connectionInfo.trim();
            if (Boolean.TRUE.equals(ACTIVE_FBD_SYSTEM_REGISTER_WRITES.get()) && connection.isEmpty()
                    && Double.isFinite(cacheNumeric[index])) {                try {
                    int address = widget.extraFields.optInt("reg", -1);
                    CraftSystemRegisterBank bank = ACTIVE_FBD_SYSTEM_REGISTER_BANK.get();
                    if (isCraftRegisterBoolean(widget.type)) {
                        CraftSystemRegisterAccess.writeBool(address,
                                widget.extraFields.optInt("bit", widget.extraFields.optInt("craftVarBit", 0)),
                                cacheNumeric[index] != 0.0, android.os.SystemClock.elapsedRealtime(), bank);
                    } else if ("FBD CRAFT VAR OUT INT".equals(canonicalFbdBlockType(widget.type))) {
                        double numeric = cacheNumeric[index];
                        if (numeric == Math.rint(numeric) && numeric >= Integer.MIN_VALUE && numeric <= Integer.MAX_VALUE)
                            CraftSystemRegisterAccess.writeInt(address, (int) numeric, bank);
                    } else if (Math.abs(cacheNumeric[index]) <= Float.MAX_VALUE) {
                        CraftSystemRegisterAccess.writeFloat(address, (float) cacheNumeric[index],
                                widget.extraFields.optBoolean("craftSwapWord", false),
                                widget.extraFields.optBoolean("craftSwapByte", false), bank,
                                android.os.SystemClock.elapsedRealtime());
                    }
                } catch (Exception ignored) { }
            }
            return !Double.isNaN(cacheNumeric[index]) && cacheNumeric[index] != 0.0;
        }
        if (isCraftNamedVariableSink(widget.type)) {
            if (inputPins.isEmpty()) cacheNumeric[index] = Double.NaN;
            else cacheNumeric[index] = isCraftNamedVariableSink(widget.type) && craftNamedVariableKind(widget.type) == 1
                    ? (inputs.get(0) ? 1.0 : 0.0) : numInputs.get(0);
            if (Double.isFinite(cacheNumeric[index])) FbdCraftNamedVariableStore.write(
                    widget.craftVariableNamespace, craftNamedVariableName(widget),
                    craftNamedVariableKind(widget.type), cacheNumeric[index]);
            return Double.isFinite(cacheNumeric[index]) && cacheNumeric[index] != 0.0;
        }

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
        if ("FBD CRAFT LOG".equals(widget.type) || "FBD CRAFT WORD TO FLOAT".equals(widget.type)
                || "FBD CRAFT INT TO FLOAT".equals(widget.type)
                || "FBD CRAFT FLOAT TO INT".equals(widget.type)
                || "FBD CRAFT SIN".equals(widget.type) || "FBD CRAFT ABS".equals(widget.type)) {
            if (!inputPins.contains("IN")) { cacheNumeric[index] = Double.NaN; return false; }
            double val = fbdNumericPinValue(numInputs, inputPins, "IN", -1, Double.NaN);
            double result;
            if ("FBD CRAFT LOG".equals(widget.type)) result = fbdCraftLogFloat32(val);
            else if ("FBD CRAFT WORD TO FLOAT".equals(widget.type)) result = fbdCraftWordToFloat(val);
            else if ("FBD CRAFT INT TO FLOAT".equals(widget.type)) result = fbdCraftIntToFloat(val);
            else if ("FBD CRAFT FLOAT TO INT".equals(widget.type)) result = fbdCraftFloatToInt(val);
            else result = fbdCraftUnaryFloat32(val,
                    "FBD CRAFT ABS".equals(widget.type) ? "ABS" : "SIN");
            cacheNumeric[index] = result;
            return !Double.isNaN(result) && !Double.isInfinite(result) && result != 0.0;
        }
        if ("FBD CRAFT POW".equals(widget.type)) {
            if (!inputPins.contains("X") || !inputPins.contains("Y")) {
                cacheNumeric[index] = Double.NaN;
                return false;
            }
            double result = fbdCraftPowFloat32(
                    fbdNumericPinValue(numInputs, inputPins, "X", -1, Double.NaN),
                    fbdNumericPinValue(numInputs, inputPins, "Y", -1, Double.NaN));
            cacheNumeric[index] = result;
            return !Double.isNaN(result) && !Double.isInfinite(result) && result != 0.0;
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
        if ("FBD CRAFT CHANGE I".equals(widget.type)) {
            boolean connected = false;
            for (String p : inputPins) if ("I".equals(canonicalFbdPinName(p))) connected = true;
            if (!connected) return false;
            Integer value = FbdCraftIntegerSelector.exactInteger(
                    fbdNumericPinValue(numInputs, inputPins, "I", -1, Double.NaN));
            if (value == null) return false;
            FbdCraftChangeTransitions.IntState next = FbdCraftChangeTransitions.nextInt(
                    widget.craftChangeIntState, value);
            widget.craftChangeIntState = next;
            return next.changed;
        }
        if ("FBD CRAFT CHANGE F".equals(widget.type)) {
            boolean connected = false;
            for (String p : inputPins) if ("F".equals(canonicalFbdPinName(p))) connected = true;
            if (!connected) return false;
            double value = fbdNumericPinValue(numInputs, inputPins, "F", -1, Double.NaN);
            if (!Double.isFinite(value) || !Float.isFinite((float)value)) return false;
            FbdCraftChangeTransitions.FloatState next = FbdCraftChangeTransitions.nextFloat(
                    widget.craftChangeFloatState, (float)value);
            widget.craftChangeFloatState = next;
            return next.changed;
        }
        if ("FBD CRAFT MX".equals(canonicalType)) {
            boolean enabled = fbdBooleanPinValue(inputs, inputPins, "EN", -1);
            Integer id = FbdCraftIntegerSelector.exactInteger(
                    fbdNumericPinValue(numInputs, inputPins, "I", -1, Double.NaN));
            Integer value = enabled && id != null ? FbdCraftRegisterStore.read(id, ACTIVE_FBD_SYSTEM_REGISTER_BANK.get()) : null;
            if (value == null) {
                cacheNumeric[index] = Double.NaN;
                return false;
            }
            cacheNumeric[index] = value;
            return value != 0;
        }
        if ("FBD CRAFT ARRAY".equals(canonicalType)) {
            boolean hasEnable = false, hasData = false, hasId = false;
            for (String pin : inputPins) {
                String name = canonicalFbdPinName(pin);
                if ("EN".equals(name)) hasEnable = true;
                if ("DT".equals(name)) hasData = true;
                if ("ID".equals(name)) hasId = true;
            }
            if (hasEnable && hasData && hasId) {
                boolean enabled = fbdBooleanPinValue(inputs, inputPins, "EN", -1);
                if (!enabled) widget.craftArrayLastEnable = false;
                else if (FbdCraftRegisterStore.risingEnable(widget.craftArrayLastEnable, true)) {
                    widget.craftArrayLastEnable = true;
                    Integer id = FbdCraftIntegerSelector.exactInteger(
                            fbdNumericPinValue(numInputs, inputPins, "ID", -1, Double.NaN));
                    Integer data = FbdCraftIntegerSelector.exactInteger(
                            fbdNumericPinValue(numInputs, inputPins, "DT", -1, Double.NaN));
                    if (id != null && data != null) FbdCraftRegisterStore.write(id, data, ACTIVE_FBD_SYSTEM_REGISTER_BANK.get());
                }
            }
            Integer id = hasId ? FbdCraftIntegerSelector.exactInteger(
                    fbdNumericPinValue(numInputs, inputPins, "ID", -1, Double.NaN)) : null;
            Integer value = id == null ? null : FbdCraftRegisterStore.read(id, ACTIVE_FBD_SYSTEM_REGISTER_BANK.get());
            if (value == null) {
                cacheNumeric[index] = Double.NaN;
                return false;
            }
            cacheNumeric[index] = value;
            return value != 0;
        }
        if ("FBD CRAFT MOVE".equals(widget.type)) {
            boolean hasEnable = false, hasInput = false;
            for (String pin : inputPins) {
                String name = canonicalFbdPinName(pin);
                if ("EN".equals(name)) hasEnable = true;
                if ("I".equals(name)) hasInput = true;
            }
            Integer output = FbdCraftMove.output(hasEnable,
                    fbdBooleanPinValue(inputs, inputPins, "EN", -1), hasInput,
                    fbdNumericPinValue(numInputs, inputPins, "I", -1, Double.NaN));
            if (output == null) {
                cacheNumeric[index] = Double.NaN;
                return false;
            }
            cacheNumeric[index] = output;
            return output != 0;
        }
        if ("FBD CRAFT SEL".equals(widget.type)) {
            // The source returns an Integer only when all three inputs exist.
            // Carry original null-like invalidity as NaN in the transient
            // numeric cache. Downstream native compare rejects it and HMI
            // displays CFG. A fake zero would incorrectly satisfy comparisons.
            boolean hasIn = false, hasI1 = false, hasI2 = false;
            for (String pin : inputPins) {
                String name = canonicalFbdPinName(pin);
                if ("IN".equals(name)) hasIn = true;
                if ("I1".equals(name)) hasI1 = true;
                if ("I2".equals(name)) hasI2 = true;
            }
            if (!hasIn || !hasI1 || !hasI2) {
                cacheNumeric[index] = Double.NaN;
                return false;
            }
            boolean selector = fbdBooleanPinValue(inputs, inputPins, "IN", 0);
            Integer i1 = FbdCraftIntegerSelector.exactInteger(
                    fbdNumericPinValue(numInputs, inputPins, "I1", 1, Double.NaN));
            Integer i2 = FbdCraftIntegerSelector.exactInteger(
                    fbdNumericPinValue(numInputs, inputPins, "I2", 2, Double.NaN));
            if (i1 == null || i2 == null) {
                cacheNumeric[index] = Double.NaN;
                return false;
            }
            int value = FbdCraftIntegerSelector.select(selector, i1, i2);
            cacheNumeric[index] = value;
            return value != 0;
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
            String mode = widget.extraFields == null ? "T"
                    : widget.extraFields.optString("craftTriggerMode", "T");
            boolean input = fbdBooleanPinValue(inputs, inputPins, "IN", 0);
            FbdCraftNativeTransitions.Trigger prior = new FbdCraftNativeTransitions.Trigger(
                    widget.craftToggleOutput, widget.craftToggleLastInput);
            FbdCraftNativeTransitions.Trigger next = "R".equals(mode)
                    ? FbdCraftNativeTransitions.rTrig(prior, input)
                    : "F".equals(mode) ? FbdCraftNativeTransitions.fTrig(prior, input)
                    : FbdCraftNativeTransitions.toggleRise(prior, input);
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
        if ("FBD CRAFT ARITHMETIC INT".equals(widget.type)
                || "FBD CRAFT ARITHMETIC FLOAT".equals(widget.type)
                || "FBD CRAFT COMPARE FLOAT".equals(widget.type)) {
            boolean leftConnected = false, rightConnected = false;
            for (String pin : inputPins) {
                String normalized = canonicalFbdPinName(pin);
                if ("IN1".equals(normalized)) leftConnected = true;
                if ("IN2".equals(normalized)) rightConnected = true;
            }
            if (!leftConnected || !rightConnected) return false;
            double left = fbdNumericPinValue(numInputs, inputPins, "IN1", 0, Double.NaN);
            double right = fbdNumericPinValue(numInputs, inputPins, "IN2", 1, Double.NaN);
            int action = FbdCraftMath.actionForOperator(widget.compareOperator,
                    "FBD CRAFT COMPARE FLOAT".equals(widget.type));
            if ("FBD CRAFT ARITHMETIC INT".equals(widget.type)) {
                Integer l = FbdCraftIntegerSelector.exactInteger(left);
                Integer r = FbdCraftIntegerSelector.exactInteger(right);
                if (l == null || r == null) return false;
                int result = FbdCraftMath.intArithmetic(l, r, action);
                cacheNumeric[index] = result;
                return result != 0;
            }
            float l = (float) left, r = (float) right;
            if ("FBD CRAFT ARITHMETIC FLOAT".equals(widget.type)) {
                float result = FbdCraftMath.floatArithmetic(l, r, action);
                cacheNumeric[index] = result;
                return result != 0f;
            }
            return FbdCraftMath.compare(l, r, action);
        }
        if ("FBD CRAFT COMPARE".equals(widget.type)) {
            // Source FB_COMPARE uses signed Integer operands, never float.
            // Invalid, disconnected or fractional operands safely return false.
            boolean i1Connected = false, i2Connected = false;
            for (String name : inputPins) {
                if ("IN1".equals(canonicalFbdPinName(name))) i1Connected = true;
                if ("IN2".equals(canonicalFbdPinName(name))) i2Connected = true;
            }
            if (!i1Connected || !i2Connected) return false;
            Integer left = FbdCraftIntegerSelector.exactInteger(
                    fbdNumericPinValue(numInputs, inputPins, "IN1", 0, Double.NaN));
            Integer right = FbdCraftIntegerSelector.exactInteger(
                    fbdNumericPinValue(numInputs, inputPins, "IN2", 1, Double.NaN));
            return left != null && right != null
                    && FbdCraftIntegerCompare.compare(
                            left, right, normalizeComparisonOperatorStatic(widget.compareOperator));
        }
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
        if ("FBD CRAFT TIMER".equals(canonicalType)) {
            if (!fbdCraftTimerInputsReady(numInputs, inputPins)) {
                cacheNumeric[index] = Double.NaN;
                return false;
            }
            boolean input = fbdBooleanPinValue(inputs, inputPins, "IN", 0);
            long presetMs = fbdCraftTimerPresetMs(numInputs, inputPins, widget.timerPresetMs,
                    widget.extraFields.optString("craftTimerUnit", "MS"));
            FbdCraftNativeTimer.State prior = widget.craftTimerState == null
                    ? FbdCraftNativeTimer.State.initial() : widget.craftTimerState;
            String mode = widget.extraFields.optString("craftTimerMode", "TON");
            int modeCode = "TOF".equals(mode) ? FbdCraftNativeTimer.TOF
                    : "TP".equals(mode) ? FbdCraftNativeTimer.TP
                    : "BLINK".equals(mode) ? FbdCraftNativeTimer.BLINK : FbdCraftNativeTimer.TON;
            FbdCraftNativeTimer.State next = FbdCraftNativeTimer.step(modeCode, prior, input,
                    System.currentTimeMillis(), presetMs);
            widget.craftTimerState = next;
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
                step = FbdTimerTransitions.ton(input, now, preset, widget.timerStartedAt);            else if ("FBD TOF".equals(canonicalType))
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

    static void isolateCraftMacroVariables(Widget macro, List<Widget> nested) {
        if (macro == null || nested == null) return;
        if (macro.craftVariableNamespace == null || macro.craftVariableNamespace.isEmpty())
            macro.craftVariableNamespace = "craft-macro-" + java.util.UUID.randomUUID();
        for (Widget child : nested) {
            if (isCraftNamedVariableSource(child.type) || isCraftNamedVariableSink(child.type))
                child.craftVariableNamespace = macro.craftVariableNamespace;
        }
    }

    /** Execute a validated, acyclic BLOODSKY macro graph in its own runtime namespace. */
    private static boolean evaluateCraftMacroBlock(List<Widget> widgetsList,
            List<FbdConnection> connectionsList, int index, boolean[] visiting,
            boolean[] cacheValid, boolean[] cacheValues, double[] cacheNumeric) {
        Widget macro = widgetsList.get(index);
        FbdCraftMacroDefinition definition = macroDefinition(macro);
        if (definition == null) { cacheNumeric[index] = Double.NaN; return false; }
        final JSONObject graph;
        try { graph = definition.toJson().optJSONObject("bloodskyGraph"); }
        catch (Exception invalid) { cacheNumeric[index] = Double.NaN; return false; }
        if (graph == null) { cacheNumeric[index] = Double.NaN; return false; }
        if (macro.craftMacroRuntimeGraphSource != graph
                && (macro.craftMacroRuntimeGraphSource == null
                    || !macro.craftMacroRuntimeGraphSource.toString().equals(graph.toString()))) {
            try {
                JSONArray rawWidgets = graph.getJSONArray("widgets");
                JSONArray rawConnections = graph.getJSONArray("connections");
                List<Widget> nestedWidgets = new ArrayList<>();
                for (int i = 0; i < rawWidgets.length(); i++)
                    nestedWidgets.add(widgetFromJson(rawWidgets.getJSONObject(i)));
                List<FbdConnection> nestedConnections = new ArrayList<>();
                for (int i = 0; i < rawConnections.length(); i++)
                    nestedConnections.add(connectionFromJson(rawConnections.getJSONObject(i)));
                macro.craftMacroRuntimeGraphSource = new JSONObject(graph.toString());
                macro.craftMacroRuntimeWidgets = nestedWidgets;
                macro.craftMacroRuntimeConnections = nestedConnections;
                macro.craftMacroRuntimeOutputNode = graph.getInt("outputNode");
                macro.craftMacroRuntimeOutputPin = graph.getString("outputPin");
            } catch (Exception malformed) {
                macro.craftMacroRuntimeGraphSource = null;
                macro.craftMacroRuntimeWidgets = null;
                macro.craftMacroRuntimeConnections = null;
                cacheNumeric[index] = Double.NaN;
                return false;
            }
        }
        List<Widget> nested = macro.craftMacroRuntimeWidgets;
        List<FbdConnection> nestedEdges = macro.craftMacroRuntimeConnections;
        if (nested == null || nested.isEmpty()) { cacheNumeric[index] = Double.NaN; return false; }
        isolateCraftMacroVariables(macro, nested);
        visiting[index] = true;
        try {
            for (Widget child : nested) if ("FBD CRAFT MACRO IN".equals(
                    canonicalFbdBlockType(child.type))) child.craftMacroInputValid = false;
            if (connectionsList != null) for (FbdConnection edge : connectionsList) {
                if (edge == null || edge.target != index || edge.source < 0
                        || edge.source >= widgetsList.size()) continue;
                String inputName = canonicalFbdPinName(edge.extraFields.optString("targetPin", ""));
                FbdCraftMacroDefinition.Port input = null;
                for (FbdCraftMacroDefinition.Port port : definition.getInputs())
                    if (canonicalFbdPinName(port.name).equals(inputName)) { input = port; break; }
                if (input == null) continue;
                Widget inputNode = null;
                for (Widget child : nested) if ("FBD CRAFT MACRO IN".equals(
                        canonicalFbdBlockType(child.type))
                        && input.name.equalsIgnoreCase(child.extraFields.optString(
                                "craftMacroPortName", ""))) { inputNode = child; break; }
                if (inputNode == null) continue;
                boolean sourceBool = evaluateFbdBlock(widgetsList, connectionsList, edge.source,
                        visiting, cacheValid, cacheValues, cacheNumeric);
                double sourceNumber = evaluateFbdBlockNumeric(widgetsList, connectionsList,
                        edge.source, visiting, cacheValid, cacheValues, cacheNumeric);
                inputNode.craftMacroInputValue = input.type == 1
                        ? (sourceBool ? 1.0 : 0.0) : sourceNumber;
                inputNode.craftMacroInputValid = true;
            }
            boolean[] nestedVisiting = new boolean[nested.size()];
            boolean[] nestedCacheValid = new boolean[nested.size()];
            boolean[] nestedCacheValues = new boolean[nested.size()];
            double[] nestedCacheNumeric = new double[nested.size()];
            int outputNode = macro.craftMacroRuntimeOutputNode;
            boolean outputBool = evaluateFbdBlock(nested, nestedEdges, outputNode,
                    nestedVisiting, nestedCacheValid, nestedCacheValues, nestedCacheNumeric);
            double value = nestedCacheNumeric[outputNode];
            FbdCraftMacroDefinition.Port output = definition.getOutput();
            if (output.type == 1) value = outputBool ? 1.0 : 0.0;
            cacheNumeric[index] = value;
            return output.type == 1 ? outputBool : Double.isFinite(value) && value != 0.0;
        } finally {
            visiting[index] = false;
        }
    }

    private void scheduleCraftRegisterWrite(Widget widget, int widgetIndex, double value) {
        if (!operatorMode || widget == null || widget.craftRegisterWritePending
                || !Double.isFinite(value) || widget.connectionInfo == null) return;
        BindingRequest request = parseProjectBinding(widgetIndex, widget.connectionInfo,
                isCraftRegisterFloat(widget.type) ? 2 : 1);
        if (request == null || request.functionCode != 3) return;
        final UsbSerialModbusConnection selectedUsbConnection = request.usb ? manualUsbConnection : null;
        String key = widget.connectionInfo + "|" + value + "|" + widget.extraFields.optInt("craftVarBit", 0)
                + "|" + widget.extraFields.optBoolean("craftSwapWord", false)
                + "|" + widget.extraFields.optBoolean("craftSwapByte", false)
                + (request.usb ? "|usbSession=" + System.identityHashCode(selectedUsbConnection) : "");
        if (key.equals(widget.craftRegisterLastWriteKey)
                || android.os.SystemClock.elapsedRealtime() < widget.craftRegisterRetryAt) return;
        boolean bool = isCraftRegisterBoolean(widget.type), floating = isCraftRegisterFloat(widget.type);
        int bit = widget.extraFields.optInt("craftVarBit", 0), integer = 0;
        int[] words = null;
        try {
            if (bool) FbdCraftRegisterCodec.bitWriteAndMask(bit);
            else if (floating) {
                if (Math.abs(value) > Float.MAX_VALUE) return;
                words = FbdCraftRegisterCodec.floatRegisters((float)value,
                        widget.extraFields.optBoolean("craftSwapWord", false),
                        widget.extraFields.optBoolean("craftSwapByte", false));
            } else {
                if (value < 0 || value > 65535 || value != Math.rint(value)) return;
                integer = (int)value;
            }
        } catch (IllegalArgumentException invalid) { return; }
        final int[] writeWords = words;
        final int writeInt = integer, unit = request.unit, address = request.address;
        final boolean writeBool = bool, writeFloat = floating;
        final String writeKey = key;
        final Widget targetWidget = widget;
        targetWidget.craftRegisterWritePending = true;
        try {
            modbusExecutor.execute(() -> {
                String error = null;
                try {
                    if (request.usb) {
                        UsbSerialModbusConnection connection = selectedUsbConnection;
                        if (connection == null || !connection.isOpen()) throw new IOException("USB RTU не подключён");
                        if (writeBool) connection.writeMask(unit, address,
                                FbdCraftRegisterCodec.bitWriteAndMask(bit),
                                FbdCraftRegisterCodec.bitWriteOrMask(bit, value != 0.0));
                        else if (writeFloat) connection.writeMultiple(unit, address, writeWords);
                        else connection.writeSingle(unit, address, writeInt);
                    } else {
                        ModbusTcpClient client = new ModbusTcpClient(request.host, request.port);
                        if (writeBool) client.writeMask(unit, address,
                                FbdCraftRegisterCodec.bitWriteAndMask(bit),
                                FbdCraftRegisterCodec.bitWriteOrMask(bit, value != 0.0));
                        else if (writeFloat) client.writeMultiple(unit, address, writeWords);
                        else client.writeSingle(unit, address, writeInt);
                    }
                } catch (Exception failure) { error = "ERR: " + describeModbusError(failure); }
                final String writeError = error;
                runOnUiThread(() -> {
                    targetWidget.craftRegisterWritePending = false;
                    targetWidget.craftRegisterRetryAt = android.os.SystemClock.elapsedRealtime() + 2000L;
                    if (!operatorMode || isFinishing() || isDestroyed() || widgetIndex >= widgets.size()
                            || widgets.get(widgetIndex) != targetWidget) return;
                    if (writeError == null) {
                        targetWidget.craftRegisterLastWriteKey = writeKey;
                        liveValues.put(widgetIndex, "OK");
                    } else liveValues.put(widgetIndex, writeError);
                    if (editor != null) editor.invalidate();
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            targetWidget.craftRegisterWritePending = false;
        }
    }

    private boolean evaluateFbd(int index, boolean[] visiting) {
        if (screens.isEmpty()) return false;
        if (fbdCacheValid.length != widgets.size() || fbdCacheValues.length != widgets.size()) {
            fbdCacheValid = new boolean[widgets.size()];
            fbdCacheValues = new boolean[widgets.size()];
            fbdCacheNumeric = new double[widgets.size()];
        }
        return evaluateFbdBlock(widgets, screens.get(currentScreenIndex).connections, index, visiting,
                fbdCacheValid, fbdCacheValues, fbdCacheNumeric, systemRegisterBank, operatorMode);
    }

    private boolean evaluateFbdUncached(int index, boolean[] visiting) {
        if (screens.isEmpty()) return false;
        return evaluateFbdBlockUncached(widgets, screens.get(currentScreenIndex).connections, index, visiting, fbdCacheValid, fbdCacheValues, fbdCacheNumeric, systemRegisterBank);
    }

    private double evaluateFbdNumeric(int index, boolean[] visiting) {
        if (screens.isEmpty()) return 0.0;
        return evaluateFbdBlockNumeric(widgets, screens.get(currentScreenIndex).connections, index, visiting, fbdCacheValid, fbdCacheValues, fbdCacheNumeric, systemRegisterBank);
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
                    if (screen.connections.contains(edge)) {
                        commitFbdWireEdit(screen, () -> disconnectFbdEdge(screen.connections, edge),
                                "Связь FBD удалена");
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
        String[] sourcePins = knownFbdPins(sourceAtSelection, true);
        String[] targetPins = knownFbdPins(targetAtSelection, false);
        if (sourcePins.length == 0 || targetPins.length == 0) {
            // A known input-only or output-only block must not fall back to an untyped wire.
            if (!canUseUnpinnedFbdFallback(sourceAtSelection, targetAtSelection)) {
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
                            sourceAtSelection, chosenSourcePin, targetAtSelection);
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
                                    String sourceSignal = knownFbdPinType(sourceAtSelection, chosenSourcePin, true);
                                    String targetSignal = knownFbdPinType(targetAtSelection, chosenTargetPin, false);
                                    if (sourceSignal != null && targetSignal != null) {
                                        edge.extraFields.put("sourceSignalType", sourceSignal);
                                        edge.extraFields.put("targetSignalType", targetSignal);
                                    }
                                    JSONObject json = connectionToJson(edge);
                                    boolean dynamicMacroPins = isDynamicMacroWidget(sourceAtSelection)
                                            || isDynamicMacroWidget(targetAtSelection);
                                    boolean compatible = dynamicMacroPins
                                            ? hasCompatibleKnownFbdPins(json, sourceAtSelection, targetAtSelection)
                                            : hasCompatibleKnownFbdPins(json, sourceType, targetType);
                                    if (!compatible) {
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
            if ("FBD CRAFT POW".equals(kind))
                return output ? 0.5f : (i == 0 ? 0.5f : 5f / 6f);
            if (isCraftMqttPublisher(kind))
                return i == 0 ? 1f / 6f : 2f / 3f;
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

    public static float fbdTerminalFraction(Widget widget, String pin, boolean output) {
        if (widget == null) return 0.5f;
        if (!"FBD CRAFT MACRO".equals(canonicalFbdBlockType(widget.type)))
            return fbdTerminalFraction(widget.type, pin, output);
        String[] pins = knownFbdPins(widget, output);
        String name = canonicalFbdPinName(pin);
        for (int i = 0; i < pins.length; i++)
            if (canonicalFbdPinName(pins[i]).equals(name))
                return output || pins.length <= 1 ? 0.5f : (i + 1f) / (pins.length + 1f);
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
        }    }

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

    public static boolean canUseUnpinnedFbdFallback(Widget source, Widget target) {
        if (source == null || target == null || source.type == null || target.type == null
                || !source.type.trim().toUpperCase(java.util.Locale.ROOT).startsWith("FBD ")
                || !target.type.trim().toUpperCase(java.util.Locale.ROOT).startsWith("FBD ")) return false;
        return knownFbdPins(source, true).length == 0 && knownFbdPins(source, false).length == 0
                && knownFbdPins(target, true).length == 0 && knownFbdPins(target, false).length == 0;
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

    public static String[] compatibleFbdTargetPins(Widget source, String sourcePin, Widget target) {
        String[] targetPins = knownFbdPins(target, false);
        String sourceSignal = knownFbdPinType(source, sourcePin, true);
        if (knownFbdPins(source, true).length > 0 && sourceSignal == null) return new String[0];
        List<String> compatible = new ArrayList<>();
        for (String targetPin : targetPins) {
            String targetSignal = knownFbdPinType(target, targetPin, false);
            if (sourceSignal == null || targetSignal == null || sourceSignal.equals(targetSignal))
                compatible.add(targetPin);
        }
        return compatible.toArray(new String[0]);
    }

    public static String[] knownFbdPins(String blockType, boolean output) {
        if (blockType == null) return new String[0];
        String kind = canonicalFbdBlockType(blockType);
        if ("FBD ВХОД".equals(kind)) return output ? new String[]{"Q"} : new String[0];
        if (isCraftMqttSubscriber(kind)) return output ? new String[]{"Q"} : new String[0];
        if (isCraftMqttPublisher(kind)) return output ? new String[0] : new String[]{"EN", "IN"};
        if (isCraftRegisterSource(kind)) return output ? new String[]{"Q"} : new String[0];
        if (isCraftRegisterSink(kind)) return output ? new String[0] : new String[]{"IN"};
        if (isCraftNamedVariableSource(kind)) return output ? new String[]{"Q"} : new String[0];
        if (isCraftNamedVariableSink(kind)) return output ? new String[0] : new String[]{"IN"};
        if (isCraftSunriseSource(kind)) return output ? new String[]{"Q"} : new String[0];
        if (isCraftOdTempSource(kind)) return output ? new String[]{"Q"} : new String[0];
        if (isCraftFbdSensorSource(kind)) return output ? new String[]{"Q"} : new String[0];
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
        if ("FBD CRAFT LOG".equals(kind) || "FBD CRAFT WORD TO FLOAT".equals(kind)
                || "FBD CRAFT SIN".equals(kind) || "FBD CRAFT ABS".equals(kind)
                || "FBD CRAFT INT TO FLOAT".equals(kind) || "FBD CRAFT FLOAT TO INT".equals(kind))
            return output ? new String[]{"OUT"} : new String[]{"IN"};
        if ("FBD CRAFT POW".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"X", "Y"};
        if ("FBD CRAFT TIMER".equals(kind))
            return output ? new String[]{"Q"} : new String[]{"IN", "MS"};
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
        commitFbdWireEdit(screen, () -> screen.connections.add(edge), "Связь FBD добавлена");
    }

    /** Save a typed wire mutation before publishing its undo step to the editor history. */
    private boolean commitFbdWireEdit(Screen screen, Runnable mutation, String successMessage) {
        if (screen == null || currentScreenIndex < 0 || currentScreenIndex >= screens.size()
                || screens.get(currentScreenIndex) != screen) return false;
        JSONObject originalProjectFields = null;
        try {
            originalProjectFields = projectExtraFields == null
                    ? null : new JSONObject(projectExtraFields.toString());
            boolean saved = FbdWireEditTransaction.apply(screen, screen.connections, fbdWireHistory,
                    mutation, () -> persistProjectNow(true));
            if (!saved) {
                projectExtraFields = originalProjectFields;
                saveActiveScreen();
                if (editor != null) editor.invalidate();
                Toast.makeText(this, "Изменение FBD не сохранено", Toast.LENGTH_LONG).show();
                return false;
            }
            if (editor != null) editor.invalidate();
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception failure) {
            projectExtraFields = originalProjectFields;
            saveActiveScreen();
            if (editor != null) editor.invalidate();
            Toast.makeText(this, "Не удалось изменить FBD: " + failure.getMessage(),
                    Toast.LENGTH_LONG).show();
            return false;
        }
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
        // Native Craft edge flags persist only on native CTU records so a saved
        // held input cannot be counted again after reopening the project.
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
        transient double craftSensorValue = Double.NaN;
        transient Double craftMqttValue;
        transient boolean craftMqttLastEnable;
        transient double[] craftOrientationValues = new double[]{Double.NaN, Double.NaN, Double.NaN};
        transient boolean craftSensorValid;
        transient boolean craftOdTempPending;
        transient long craftOdTempNextPollAt;
        transient int craftRegisterWord0, craftRegisterWord1;
        transient boolean craftRegisterValid;
        transient boolean craftRegisterWritePending;
        transient String craftRegisterLastWriteKey = "";
        transient long craftRegisterRetryAt;
        transient boolean craftMacroInputValid;
        transient double craftMacroInputValue = Double.NaN;
        transient JSONObject craftMacroRuntimeGraphSource;
        transient List<Widget> craftMacroRuntimeWidgets;
        transient List<FbdConnection> craftMacroRuntimeConnections;
        transient int craftMacroRuntimeOutputNode = -1;
        transient String craftMacroRuntimeOutputPin = "";
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
        public int craftAlarmSoundIndex = 0;
        public boolean craftAlarmLoop = false;
        public int targetScreenIndex = 0;
        public String targetUrl = "";
        public int lastVoiceState = -1;
        public boolean lastSoundState = false;
        public boolean lastTelegramState = false;
        transient boolean lastSmsState = false;
        public JSONObject extraFields = new JSONObject();
        transient JSONObject craftMacroDefinitionSourceCache;
        transient FbdCraftMacroDefinition craftMacroDefinitionCache;
        // Runtime-only scope for nodes inside an FB_MACRO instance.
        public String craftVariableNamespace = "";
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

    private void publishCraftMqttEdges() {
        FbdCraftMqttRuntime runtime = craftMqttRuntime;
        if (runtime == null || !operatorMode || screens.isEmpty()
                || currentScreenIndex < 0 || currentScreenIndex >= screens.size()) return;
        for (int target = 0; target < widgets.size(); target++) {
            Widget publisher = widgets.get(target);
            if (!isCraftMqttPublisher(publisher.type)) continue;
            List<Boolean> booleanValues = new ArrayList<>();
            List<Double> numericValues = new ArrayList<>();
            List<String> pins = new ArrayList<>();
            for (FbdConnection edge : screens.get(currentScreenIndex).connections) {
                if (edge.target != target || edge.source < 0 || edge.source >= widgets.size()) continue;
                pins.add(edge.extraFields.optString("targetPin", ""));
                booleanValues.add(evaluateFbd(edge.source, new boolean[widgets.size()]));
                numericValues.add(evaluateFbdNumeric(edge.source, new boolean[widgets.size()]));
            }
            boolean enabled = fbdBooleanPinValue(booleanValues, pins, "EN", 0);
            if (!enabled) { publisher.craftMqttLastEnable = false; continue; }
            if (publisher.craftMqttLastEnable) continue;
            publisher.craftMqttLastEnable = true;
            int type = mqttType(publisher);
            String payload;
            if (type == 1) {
                payload = Boolean.toString(fbdBooleanPinValue(booleanValues, pins, "IN", 1));
            } else {
                double value = fbdNumericPinValue(numericValues, pins, "IN", 1, Double.NaN);
                if (!Double.isFinite(value)) { liveValues.put(target, "MQTT CFG"); continue; }
                if (type == 2) {
                    Integer exact = FbdCraftIntegerSelector.exactInteger(value);
                    if (exact == null) { liveValues.put(target, "MQTT CFG"); continue; }
                    payload = Integer.toString(exact);
                } else {
                    float narrowed = (float) value;
                    if (!Float.isFinite(narrowed)) { liveValues.put(target, "MQTT CFG"); continue; }
                    payload = Float.toString(narrowed);
                }
            }
            String topic = publisher.extraFields.optString("craftMqttTopic", "").trim();
            boolean sent = runtime.publish(mqttServer(publisher), topic, payload,
                    publisher.extraFields.optBoolean("craftMqttRetain", false));
            liveValues.put(target, sent ? "MQTT → " + topic : "MQTT offline");
        }
    }

    private class EditorCanvas extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int selected = -1;
        final ArrayList<Integer> selectedWidgets = new ArrayList<>();
        final LinkedHashMap<Integer, float[]> dragOrigins = new LinkedHashMap<>();
        float downX, downY, startX, startY, startW, startH;
        float marqueeX, marqueeY;
        float groupMinX, groupMinY, groupMaxX, groupMaxY;
        long downAt;
        boolean dragging;
        boolean resizing;
        boolean marqueeActive;

        EditorCanvas() {
            super(MainActivity.this);
            setBackgroundColor(Color.WHITE);
            setWillNotDraw(false);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Screen currentScreen = screens.isEmpty() ? null : screens.get(currentScreenIndex);
            canvas.drawColor(currentScreen == null ? Color.rgb(250, 252, 255) : currentScreen.backgroundColor);
            if (currentScreen != null) {
                String backgroundImage = currentScreen.extraFields.optString("backgroundImagePath", "");
                Bitmap bitmap = backgroundImage.isEmpty() ? null : loadCachedBitmap(backgroundImage);
                if (bitmap != null) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setFilterBitmap(true);
                    canvas.drawBitmap(bitmap, null, new Rect(0, 0, getWidth(), getHeight()), paint);
                    paint.setFilterBitmap(false);
                }
            }
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
                    float y1 = from.y + from.h * fbdTerminalFraction(from,
                            connection.extraFields.optString("sourcePin", ""), true);
                    float x2 = to.x;
                    float y2 = to.y + to.h * fbdTerminalFraction(to,
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
                for (int i = 0; i < widgets.size(); i++) {
                    Widget sink = widgets.get(i);
                    if (isCraftRegisterSink(sink.type)) scheduleCraftRegisterWrite(sink, i, fbdCacheNumeric[i]);
                }
                publishCraftMqttEdges();
            }
            // fbd:// widget bindings remain read-only; explicit VarOut blocks write only in operator mode.
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
                if (!w.visible || (operatorMode && ("Кнопка".equals(w.type) || "Константная кнопка Craft".equals(w.type)
                        || "Переход на экран".equals(w.type))
                        && !w.extraFields.optBoolean("craftButtonVisibleInWork", true))
                        || (operatorMode && OriginalCraftSwitch.supports(w.type)
                        && !w.extraFields.optBoolean("craftSwitchVisibleInWork", OriginalCraftSwitch.visibleInWork(w.extraFields)))
                        || (operatorMode && isFbd(w))
                        || (operatorMode && "Список Craft".equals(w.type)
                        && !w.extraFields.optBoolean("craftListVisibleInWork", true))) continue;
                RectF rect = new RectF(w.x, w.y, w.x + w.w, w.y + w.h);
                paint.setStyle(Paint.Style.FILL);
                if (w.backgroundColor != 0) {
                    paint.setColor(w.backgroundColor);
                } else if (w.type != null && w.type.startsWith("FBD ")) {
                    paint.setColor(Color.rgb(237, 233, 254));
                } else if ("Индикатор".equals(w.type) || "Двухпозиционный индикатор".equals(w.type)) {
                    paint.setColor(Color.rgb(220, 252, 231));
                } else if (OriginalCraftSwitch.supports(w.type)) {
                    paint.setColor(Color.TRANSPARENT);
                } else if ("Кнопка".equals(w.type) || "Константная кнопка Craft".equals(w.type)) {
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
                boolean sourceCraftSwitch = OriginalCraftSwitch.supports(w.type);
                if (sourceCraftSwitch) {
                    OriginalCraftSwitchRenderer.draw(canvas, getResources(), rect, w.extraFields, w.buttonState, paint);
                }
                if (!OriginalIndicator.supports(w.type) && !sourceFbdSkin && !sourceCraftSwitch)
                    canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                if ((!OriginalIndicator.supports(w.type) && w.showFrame
                        && !sourceFbdSkin && !sourceCraftSwitch) || i == selected || selectedWidgets.contains(i)) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(i == selected ? 2.5f : 1));
                    paint.setColor(i == selected || selectedWidgets.contains(i) ? Color.rgb(21, 101, 192) : Color.rgb(148, 163, 184));
                    canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                }
                if (i == selected && selectedWidgets.size() <= 1) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.rgb(21, 101, 192));
                    float grip = dp(9);
                    canvas.drawRect(w.x + w.w - grip, w.y + w.h - grip, w.x + w.w, w.y + w.h, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(w.textColor);
                paint.setTextSize(dp(w.textSizeSp));
                if (sourceCraftSwitch) {
                    // The source switch is a pair of full-size original bitmaps; it has no text overlay.
                } else if (w.type != null && w.type.startsWith("FBD ")) {
                    if (FbdSourceCanvasSkin.shouldUse(w)) {
                        FbdSourceCanvasSkin.draw(canvas, paint, w, getResources().getDisplayMetrics().density);
                        if (i == selected || selectedWidgets.contains(i)) {
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setStrokeWidth(dp(2.5f));
                            paint.setColor(Color.rgb(21, 101, 192));
                            canvas.drawRect(rect, paint);
                            paint.setStyle(Paint.Style.FILL);
                            float grip = dp(9);
                            if (i == selected && selectedWidgets.size() <= 1)
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
                    paint.setTextSize(dp(11));                    paint.setColor(Color.rgb(100, 116, 139));
                    canvas.drawText("OUT = —", w.x + dp(12), w.y + w.h - dp(8), paint);
                    paint.setTextSize(dp(10));
                    paint.setColor(Color.rgb(109, 40, 217));
                    String[] blockInputs = knownFbdPins(w, false);
                    if (blockInputs.length == 0) blockInputs = new String[]{"", ""};
                    for (int pinIndex = 0; pinIndex < blockInputs.length; pinIndex++) {
                        float py = w.y + w.h * (blockInputs[pinIndex].isEmpty()
                                ? (pinIndex + 1f) / (blockInputs.length + 1f)
                                : fbdTerminalFraction(w, blockInputs[pinIndex], false));
                        canvas.drawLine(w.x, py, w.x + dp(14), py, paint);
                        canvas.drawCircle(w.x + dp(4), py, dp(4), paint);
                        if (!blockInputs[pinIndex].isEmpty())
                            canvas.drawText(blockInputs[pinIndex], w.x + dp(17), py - dp(3), paint);
                    }
                    String[] blockOutputs = knownFbdPins(w, true);
                    if (blockOutputs.length == 0) blockOutputs = new String[]{""};
                    for (int pinIndex = 0; pinIndex < blockOutputs.length; pinIndex++) {
                        float py = w.y + w.h * (blockOutputs[pinIndex].isEmpty()
                                ? (pinIndex + 1f) / (blockOutputs.length + 1f)
                                : fbdTerminalFraction(w, blockOutputs[pinIndex], true));
                        canvas.drawLine(w.x + w.w - dp(14), py, w.x + w.w, py, paint);
                        canvas.drawCircle(w.x + w.w - dp(4), py, dp(4), paint);
                    }
                    paint.setStyle(Paint.Style.FILL);
                    }
                } else if (OriginalCraftNumericIndicator.TYPE.equals(w.type)) {
                    String value = liveValues.get(i);
                    if (value == null) value = "0";
                    boolean failed = value.startsWith("ERR") || value.startsWith("CFG");
                    android.graphics.Typeface previousTypeface = paint.getTypeface();
                    android.graphics.Paint.Align previousAlign = paint.getTextAlign();
                    if (!w.extraFields.optBoolean("craftNumericIndicatorFontChange", false)) {
                        try { paint.setTypeface(android.graphics.Typeface.createFromAsset(
                                MainActivity.this.getAssets(), "fonts/sf_digital_readout_heavy.ttf")); }
                        catch (Exception missingFont) { /* Use the platform font if the source font is unavailable. */ }
                    }
                    if (w.extraFields.optBoolean("craftNumericIndicatorFrame", false)) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dp(1)); paint.setColor(w.textColor);
                        canvas.drawRect(rect, paint); paint.setStyle(Paint.Style.FILL);
                    }
                    paint.setColor(failed ? Color.rgb(185, 28, 28) : w.textColor);
                    paint.setTextSize(dp(w.textSizeSp)); paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(value, w.x + w.w / 2f, w.y + w.h / 2f + dp(5), paint);
                    paint.setTextAlign(previousAlign); paint.setTypeface(previousTypeface);
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
                } else if ("Числовой ввод Craft".equals(w.type)) {
                    String value = liveValues.get(i);
                    if (value == null || value.startsWith("ERR") || value.startsWith("CFG")) value = w.text;
                    if (value == null) value = "0";
                    android.graphics.Typeface previousTypeface = paint.getTypeface();
                    if (!w.extraFields.optBoolean("craftNumericSystemFont", false)) {
                        try { paint.setTypeface(android.graphics.Typeface.createFromAsset(
                                MainActivity.this.getAssets(), "fonts/sf_digital_readout_heavy.ttf")); }
                        catch (Exception missingFont) { /* Keep the platform fallback if the asset is unavailable. */ }
                    }
                    paint.setColor(w.textColor); paint.setTextSize(dp(w.textSizeSp));
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(value, w.x + w.w / 2f, w.y + w.h / 2f + dp(5), paint);
                    paint.setTextAlign(Paint.Align.LEFT);
                    paint.setTypeface(previousTypeface);
                } else if ("Список Craft".equals(w.type)) {
                    int mask = w.extraFields.optInt("craftListInitialMask", 0) & 0xffff;
                    String liveMask = liveValues.get(i);
                    if (liveMask != null) try { mask = CraftBitList.fromRegister(Integer.parseInt(liveMask)); } catch (Exception ignored) { }
                    String[] labels = CraftBitList.fromJson(w.extraFields.optJSONArray("craftListLabels"));
                    paint.setColor(w.textColor); paint.setTextSize(dp(12));
                    canvas.drawText(w.text + "  " + String.format(java.util.Locale.ROOT, "0x%04X", mask), w.x + dp(8), w.y + dp(17), paint);
                    paint.setTextSize(dp(10));
                    StringBuilder chosen = new StringBuilder();
                    for (int bit = 0; bit < 16; bit++) if ((mask & (1 << bit)) != 0 && labels[bit] != null && !labels[bit].isEmpty()) {
                        if (chosen.length() > 0) chosen.append(", ");
                        chosen.append(labels[bit]);
                    }
                    String summary = chosen.length() == 0 ? "Выбрать пункты" : chosen.toString();
                    if (summary.length() > 64) summary = summary.substring(0, 61) + "…";
                    canvas.drawText(summary, w.x + dp(8), w.y + dp(34), paint);
                    if (liveMask != null && liveMask.startsWith("ERR:")) {
                        paint.setColor(Color.rgb(185, 28, 28)); paint.setTextSize(dp(9));
                        canvas.drawText("Нет связи", w.x + dp(8), w.y + w.h - dp(5), paint);
                    }
                } else if ("Линейный задатчик Craft".equals(w.type)) {
                    int maximum = CraftLinearSetter.maximum(w.extraFields);
                    int value = CraftLinearSetter.value(w.extraFields, liveValues.get(i));
                    float ratio = value / (float) maximum;
                    boolean vertical = w.extraFields.optBoolean("craftLinearVertical", false);
                    paint.setColor(w.textColor); paint.setTextSize(dp(11));
                    canvas.drawText(w.text + ": " + value + " / " + maximum, w.x + dp(7), w.y + dp(14), paint);
                    paint.setColor(w.extraFields.optInt("craftLinearBackgroundColor", Color.rgb(156, 156, 155)));
                    if (vertical) {
                        float center = w.x + w.w / 2f;
                        canvas.drawRoundRect(center - dp(4), w.y + dp(20), center + dp(4), w.y + w.h - dp(5), dp(4), dp(4), paint);
                        paint.setColor(w.extraFields.optInt("craftLinearProgressColor", Color.rgb(46, 236, 0)));
                        float bottom = w.y + w.h - dp(5), top = bottom - ((w.h - dp(25)) * ratio);
                        canvas.drawRoundRect(center - dp(4), top, center + dp(4), bottom, dp(4), dp(4), paint);
                    } else {
                        RectF track = new RectF(w.x + dp(7), w.y + dp(25), w.x + w.w - dp(7), w.y + w.h - dp(8));
                        canvas.drawRoundRect(track, dp(5), dp(5), paint);
                        paint.setColor(w.extraFields.optInt("craftLinearProgressColor", Color.rgb(46, 236, 0)));
                        canvas.drawRoundRect(track.left, track.top, track.left + track.width() * ratio, track.bottom, dp(5), dp(5), paint);
                    }
                    String linearStatus = liveValues.get(i);
                    if (linearStatus != null && linearStatus.startsWith("ERR:")) {
                        paint.setColor(Color.rgb(185, 28, 28)); paint.setTextSize(dp(9));
                        canvas.drawText("Нет связи", w.x + dp(7), w.y + w.h - dp(3), paint);
                    }
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
                    paint.setShader(null);
                    paint.setStyle(Paint.Style.FILL);
                    float ratio = CraftProgressBar.ratio(val, w.minValue, w.maxValue);
                    float inset = dp(4);
                    RectF track = new RectF(w.x + inset, w.y + inset,
                            w.x + w.w - inset, w.y + w.h - inset);
                    int background = w.extraFields.optInt("craftProgressBackgroundColor", Color.rgb(156, 156, 155));
                    int progress = w.extraFields.optInt("craftProgressColor", Color.rgb(46, 236, 0));
                    if (Color.alpha(background) != 0) {
                        paint.setShader(new android.graphics.LinearGradient(track.left, track.top,
                                track.left, track.bottom,
                                new int[]{Color.rgb(40, 40, 40), Color.WHITE, Color.rgb(119, 119, 119)},
                                null, android.graphics.Shader.TileMode.CLAMP));
                        canvas.drawRoundRect(track, dp(4), dp(4), paint);
                        paint.setShader(null);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dp(1));
                        paint.setColor(Color.rgb(68, 68, 68));
                        canvas.drawRoundRect(track, dp(4), dp(4), paint);
                        paint.setStyle(Paint.Style.FILL);
                    }
                    RectF fill = new RectF(track.left, track.top,
                            track.left + track.width() * ratio, track.bottom);
                    if (fill.width() > 0) {
                        paint.setShader(new android.graphics.LinearGradient(fill.left, fill.top,
                                fill.left, fill.bottom,
                                new int[]{CraftProgressBar.shade(progress, 0.7f), progress,
                                        CraftProgressBar.shade(progress, 0.7f)},
                                null, android.graphics.Shader.TileMode.CLAMP));
                        canvas.drawRoundRect(fill, dp(4), dp(4), paint);
                        paint.setShader(null);
                    }
                    if (w.extraFields.optBoolean("craftProgressShowScale", false)) {
                        paint.setColor(Color.BLACK);
                        paint.setStrokeWidth(2f);
                        float spacing = Math.max(0f, track.height() / 20f);
                        float center = w.x + w.w / 2f;
                        for (int tick = 1; tick < 20; tick++) {
                            float length = tick == 10 ? w.w / 6f : (tick % 2 == 0 ? w.w / 8f : w.w / 16f);
                            float y = track.top + tick * spacing;
                            canvas.drawLine(center - length, y, center + length, y, paint);
                        }
                    }
                    if (w.extraFields.optBoolean("craftProgressShowText", true)) {
                        paint.setColor(w.extraFields.optInt("craftProgressTextColor", w.textColor));
                        paint.setTextSize(w.extraFields.optInt("craftProgressTextSize", dp(12)));
                        String valueText = Long.toString(Math.round(val));
                        canvas.drawText(valueText, w.x + (w.w - paint.measureText(valueText)) / 2f,
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

