Warning: truncated output (original token count: 235960)
Total output lines: 15007

package com.bloodsky.hmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
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
import android.widget.VideoView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private static final String PROJECT_TRASH = "project_trash_v1";
    private static final String PROJECT_MEDIA_CLEANUP_QUEUE = "project_media_cleanup_pending_v1";
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
    private final CraftWebViewWindow craftWebViewWindow = new CraftWebViewWindow();
    private Dialog craftRtspDialog;
    private VideoView craftRtspVideoView;
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
    private ModbusPollGateway modbusPollGateway;
    private final ExecutorService projectMediaCleanupExecutor = Executors.newSingleThreadExecutor();
    private final Object projectMediaCleanupLock = new Object();
    private volatile UsbSerialModbusConnection manualUsbConnection;
    private final java.util.concurrent.ConcurrentHashMap<String, BluetoothModbusConnection> bluetoothModbusConnections =
            new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long bluetoothSessionGeneration;
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
    private boolean externalRtuConsentPromptPending;
    private boolean externalRtuConsentResolved;
    private boolean externalRtuApprovedForSession;
    private boolean externalBluetoothPermissionDeniedForSession;
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
    private static final int REQUEST_OPERATOR_BLUETOOTH_PERMISSION = 6206;
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
    private Widget pendingCraftVoiceWidget;
    private Widget activeCraftVoiceWidget;
    private boolean pendingCraftVoicePermission;
    private final android.location.LocationListener fbdLocationListener = new android.location.LocationListener() {
        @Override public void onLocationChanged(android.location.Location location) {
            if (location == null || !operatorMode || editor == null) return;
            double kmh = Double.isNaN(location.getSpeed()) ? 0.0 : location.getSpeed() * 3.6;
            for (Widget widget : fbdRuntimeWidgetsIncludingMacros(widgets)) if ("FBD CRAFT SENSOR SPEED".equals(widget.type)) {
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
                for (Widget widget : fbdRuntimeWidgetsIncludingMacros(widgets)) if ("FBD CRAFT SENSOR LIGHT".equals(widget.type)) {
                    widget.craftSensorValue = Math.max(0, (int)event.values[0]); widget.craftSensorValid = true; changed = true;
                }
            } else if (event.sensor.getType() == android.hardware.Sensor.TYPE_ORIENTATION && event.values.length >= 3) {
                for (Widget widget : fbdRuntimeWidgetsIncludingMacros(widgets)) if ("FBD CRAFT SENSOR ORIENTATION".equals(widget.type)) {
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
        modbusPollGateway = new ModbusPollGateway(getApplicationContext());
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
        // Quarantine old media BEFORE any project can be reopened or imported.
        // The background executor deletes only the quarantined directories.
        stagePendingProjectMediaCleanup();
        if (!restoreMediaPickerState(state)) showHome();
        schedulePendingProjectMediaCleanup();
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
            Widget requestedCraftVoice = pendingCraftVoiceWidget;
            pendingCraftVoiceWidget = null;
            boolean craftRequest = pendingCraftVoicePermission;
            pendingCraftVoicePermission = false;
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (craftRequest) {
                    if (requestedCraftVoice != null) startCraftVoiceRecognition(requestedCraftVoice);
                } else startVoiceRecognition(pendingVoiceMode);
            } else Toast.makeText(this, "Для голосового управления нужен доступ к микрофону", Toast.LENGTH_LONG).show();
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
        if (requestCode == REQUEST_OPERATOR_BLUETOOTH_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
                startModbusPolling();
            else {
                externalBluetoothPermissionDeniedForSession = true;
                Toast.makeText(this, "Bluetooth RTU выключен без разрешения BLUETOOTH_CONNECT; TCP и выбранный USB остаются доступны", Toast.LENGTH_LONG).show();
                startModbusPolling();
            }
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
        craftWebViewWindow.close();
        closeCraftRtspPlayer();
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
        pendingCraftVoiceWidget = null;
        releaseVoiceRecognizer();
        resetCraftAlarmEdges();
        stopAllCraftAlarmPlayback();
        if (textToSpeech != null) try { textToSpeech.stop(); } catch (Exception ignored) {}
        super.onPause();
    }

    private void closeCraftRtspPlayer() {
        Dialog dialog = craftRtspDialog;
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            return;
        }
        VideoView video = craftRtspVideoView;
        craftRtspVideoView = null;
        craftRtspDialog = null;
        if (video != null) {
            video.setKeepScreenOn(false);
            try { video.stopPlayback(); } catch (RuntimeException ignored) { }
        }
    }

    @Override protected void onDestroy() {
        craftWebViewWindow.close();
        closeCraftRtspPlayer();
        manualUsbScreenGeneration++;
        stopModbusTcpSlave();
        closeManualUsbConnection();
        unregisterIncomingSmsReceiver();
        stopModbusPolling();
        stopFbdSensors();
        stopCraftMqtt();
        releaseVoiceRecognizer();
        editor = null;
        if (modbusPollGateway != null) {
            modbusPollGateway.close();
            modbusPollGateway = null;
        }
        modbusExecutor.shutdownNow();
        projectMediaCleanupExecutor.shutdownNow();
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
        stopScreenAlarms(currentScreenIndex, widgets);
    }

    private void stopScreenAlarms(int screenIndex, List<Widget> screenWidgets) {
        for (int index = 0; index < screenWidgets.size(); index++) {
            Widget widget = screenWidgets.get(index);
            if ("Звуковой сигнал".equals(widget.type)) {
                widget.lastSoundState = false;
                stopCraftAlarmAlert(screenIndex + ":" + index);
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
        addLegacyMenuItem("Восстановить проект", this::chooseTrashedProject, menuWidth);
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
            String oldTrash = prefs.getString(PROJECT_TRASH, "{}");
            String active = prefs.getString(ACTIVE_PROJECT, null);
            String oldName = prefs.getString("project_name", null);
            boolean deletingActive = active != null && key.equals(ProjectMediaService.getOrGenerateProjectId(active));
            JSONObject moved = ProjectCatalog.moveToTrash(oldCatalog, oldTrash, key);
            android.content.SharedPreferences.Editor edit = prefs.edit()
                    .putString(PROJECT_CATALOG, moved.getJSONObject("catalog").toString())
                    .putString(PROJECT_TRASH, moved.getJSONObject("trash").toString());
            if (deletingActive) edit.remove(ACTIVE_PROJECT).remove("project_name");
            if (!edit.commit()) {
                android.content.SharedPreferences.Editor restore = prefs.edit()
                        .putString(PROJECT_CATALOG, oldCatalog).putString(PROJECT_TRASH, oldTrash);
                if (active != null) restore.putString(ACTIVE_PROJECT, active);
                if (oldName != null) restore.putString("project_name", oldName);
                if (!restore.commit()) android.util.Log.e("BLOODSKY", "Could not persist restored catalog");
                throw new IOException("Не удалось удалить проект");
            }
            showHome();
            if (deletingActive) { widgets.clear(); screens.clear(); projectExtraFields = new JSONObject(); clipboardWidgets = null; imageCache.clear(); gifCache.clear(); }
        } catch (Exception error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void chooseTrashedProject() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            List<JSONObject> projects = ProjectCatalog.list(prefs.getString(PROJECT_TRASH, "{}"));
            if (projects.isEmpty()) {
                Toast.makeText(this, "Нет удалённых проектов", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] labels = new String[projects.size()];
            for (int i = 0; i < labels.length; i++)
                labels[i] = projects.get(i).optString("projectName", "Мой проект");
            new AlertDialog.Builder(this).setTitle("Восстановить проект")
                    .setItems(labels, (dialog, which) -> showTrashedProjectActions(projects.get(which)))
                    .setNegativeButton("Отмена", null).show();
        } catch (Exception error) {
            Toast.makeText(this, "Корзина проектов: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showTrashedProjectActions(JSONObject selected) {
        new AlertDialog.Builder(this).setTitle(selected.optString("projectName", "Мой проект"))
                .setItems(new String[]{"Восстановить проект", "Удалить навсегда"}, (dialog, which) -> {
                    if (which == 0) restoreTrashedProject(selected);
                    else new AlertDialog.Builder(this).setTitle("Удалить проект и его файлы навсегда?")
                            .setMessage("Медиа проекта и его резервные поколения будут удалены.")
                            .setNegativeButton("Отмена", null)
                            .setPositiveButton("Удалить", (confirm, choice) -> permanentlyDeleteTrashedProject(selected))
                            .show();
                }).setNegativeButton("Отмена", null).show();
    }

    private void permanentlyDeleteTrashedProject(JSONObject selected) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            synchronized (projectMediaCleanupLock) {
                String oldTrash = prefs.getString(PROJECT_TRASH, "{}");
                String key = ProjectMediaService.getOrGenerateProjectId(selected.toString());
                JSONObject result = ProjectCatalog.permanentlyDeleteFromTrash(oldTrash, key);
                // Commit the tombstone in the SAME transaction as removing the
                // trash entry. If storage cleanup fails or Android kills us,
                // the next launch can safely retry all private generations.
                String pending = ProjectMediaCleanupQueue.enqueue(
                        prefs.getString(PROJECT_MEDIA_CLEANUP_QUEUE, "[]"), key);
                if (!prefs.edit()
                        .putString(PROJECT_TRASH, result.getJSONObject("trash").toString())
                        .putString(PROJECT_MEDIA_CLEANUP_QUEUE, pending)
                        .commit()) {
                    throw new IOException("Не удалось сохранить удаление проекта и очередь очистки");
                }
            }
            stagePendingProjectMediaCleanup();
            schedulePendingProjectMediaCleanup();
            Toast.makeText(this, "Проект удалён; очистка его файлов запущена", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось удалить проект: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stagePendingProjectMediaCleanup() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            for (String id : ProjectMediaCleanupQueue.pending(
                    prefs.getString(PROJECT_MEDIA_CLEANUP_QUEUE, "[]"))) {
                synchronized (projectMediaCleanupLock) {
                    try {
                        String queue = prefs.getString(PROJECT_MEDIA_CLEANUP_QUEUE, "[]");
                        if (!ProjectMediaCleanupQueue.pending(queue).contains(id)) continue;
                        boolean referenced = ProjectMediaCleanupQueue.isReferenced(id,
                                prefs.getString(PROJECT_CATALOG, "{}"),
                                prefs.getString(PROJECT_TRASH, "{}"),
                                prefs.getString(ACTIVE_PROJECT, null));
                        if (referenced) {
                            // If the old media was already staged, it is safe
                            // to clean that isolated copy despite ID reuse.
                            if (!ProjectMediaService.hasQuarantinedMediaById(getFilesDir(), id)
                                    && !prefs.edit().putString(PROJECT_MEDIA_CLEANUP_QUEUE,
                                    ProjectMediaCleanupQueue.remove(queue, id)).commit())
                                android.util.Log.e("BLOODSKY", "Could not dismiss reused cleanup ID");
                            continue;
                        }
                        ProjectMediaService.quarantineProjectMediaById(getFilesDir(), id);
                    } catch (Exception failure) {
                        android.util.Log.e("BLOODSKY", "Project media quarantine deferred", failure);
                    }
                }
            }
        } catch (Exception invalidQueue) {
            android.util.Log.e("BLOODSKY", "Invalid project media cleanup queue", invalidQueue);
        }
    }

    private void schedulePendingProjectMediaCleanup() {
        try {
            projectMediaCleanupExecutor.execute(this::drainPendingProjectMediaCleanup);
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            // The durable queue remains available on the next launch.
        }
    }

    private void drainPendingProjectMediaCleanup() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            for (String id : ProjectMediaCleanupQueue.pending(
                    prefs.getString(PROJECT_MEDIA_CLEANUP_QUEUE, "[]"))) {
                synchronized (projectMediaCleanupLock) {
                    try {
                        String queue = prefs.getString(PROJECT_MEDIA_CLEANUP_QUEUE, "[]");
                        if (!ProjectMediaCleanupQueue.pending(queue).contains(id)) continue;
                        // Never delete the live project directory in a worker:
                        // a project with the same ID may be re-imported while
                        // this task is running. Only a quarantined directory
                        // is eligible for recursive deletion.
                        if (ProjectMediaService.hasQuarantinedMediaById(getFilesDir(), id)) {
                            ProjectMediaService.deleteQuarantinedMediaById(getFilesDir(), id);
                        } else if (ProjectMediaCleanupQueue.isReferenced(id,
                                prefs.getString(PROJECT_CATALOG, "{}"),
                                prefs.getString(PROJECT_TRASH, "{}"),
                                prefs.getString(ACTIVE_PROJECT, null))) {
                            // No quarantined old copy exists. A current project
                            // owns that normal directory; dismiss the tombstone.
                        } else if (ProjectMediaService.hasProjectMediaDirectoryById(getFilesDir(), id)) {
                            // Staging failed earlier. Retry at the next launch
                            // rather than silently losing the only tombstone.
                            continue;
                        }
                        if (!prefs.edit().putString(PROJECT_MEDIA_CLEANUP_QUEUE,
                                ProjectMediaCleanupQueue.remove(queue, id)).commit())
                            android.util.Log.e("BLOODSKY", "Could not acknowledge deleted media; will retry");
                    } catch (Exception failure) {
                        android.util.Log.e("BLOODSKY", "Project media cleanup deferred until next launch", failure);
                    }
                }
            }
        } catch (Exception invalidQueue) {
            android.util.Log.e("BLOODSKY", "Invalid project media cleanup queue, refusing deletion", invalidQueue);
        }
    }

    private void restoreTrashedProject(JSONObject selected) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String oldCatalog = prefs.getString(PROJECT_CATALOG, "{}");
            String oldTrash = prefs.getString(PROJECT_TRASH, "{}");
            String key = ProjectMediaService.getOrGenerateProjectId(selected.toString());
            JSONObject restored = ProjectCatalog.restoreFromTrash(oldCatalog, oldTrash, key);
            if (!prefs.edit().putString(PROJECT_CATALOG, restored.getJSONObject("catalog").toString())
                    .putString(PROJECT_TRASH, restored.getJSONObject("trash").toString()).commit())
                throw new IOException("Не удалось восстановить проект");
            Toast.makeText(this, "Проект восстановлен", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
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
                        if (modbusPollGateway != null) modbusPollGateway.setSelectedUsbConnection(opened);
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
        if (modbusPollGateway != null) modbusPollGateway.setSelectedUsbConnection(null);
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
        String funcName = functionCode == 1 ? "coils (FC01)\n" : (functionCode == 2 ? "discrete inputs (FC02)\n" : (functionCode == 3 ? "holding registers …195960 tokens truncated…              int imgIdx = CraftMultiStateImage.selectLiveValue(typeCommand, liveValues.get(i));
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
                    boolean voiceEnabled = w.extraFields.optBoolean("craftVoiceRecognitionEnabled", false);
                    paint.setColor(voiceEnabled ? Color.rgb(134, 239, 172) : Color.rgb(248, 113, 113));
                    paint.setTextSize(dp(10));
                    canvas.drawText(voiceEnabled ? "Локально • нажми для команды" : "Голосовое управление выключено",
                            w.x + dp(12), w.y + w.h / 2 + dp(4), paint);
                    paint.setColor(Color.rgb(148, 163, 184));
                    paint.setTextSize(dp(9));
                    canvas.drawText("16 фраз • локальный регистр "
                                    + w.extraFields.optInt("craftVoiceRegisterAddress", -1),
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
                    canvas.drawText(w.extraFields.optBoolean("craftRtspEnabled", false)
                                    ? "RTSP • нажмите для просмотра" : "RTSP • поток выключен",
                            w.x + dp(12), w.y + w.h / 2 + dp(4), paint);
                    paint.setColor(Color.rgb(148, 163, 184));
                    paint.setTextSize(dp(9));
                    canvas.drawText("Источник сохранён; запуск требует подтверждения",
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
                    canvas.drawText(w.extraFields.optBoolean("craftWebViewEnabled", false)
                            ? "WebView • нажмите для открытия" : "WebView выключен",
                            w.x + dp(12), w.y + w.h / 2 + dp(4), paint);
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
                    canvas.drawText(w.extraFields.optBoolean("craftEmailEnabled", false)
                                    ? "Email • нажмите для подтверждения" : "Email выключен",
                            w.x + dp(12), w.y + w.h / 2 + dp(4), paint);
                    paint.setColor(Color.rgb(148, 163, 184));
                    paint.setTextSize(dp(9));
                    canvas.drawText(w.extraFields.optString("craftEmailTo", ""), w.x + dp(12), w.y + w.h - dp(10), paint);
                } else if ("Голосовое сообщение".equals(w.type)) {
                    int voiceIdx = operatorMode
                            ? CraftHmiStateSignal.decode(liveValues.get(i)) : 0;
                    String voiceLabel = voiceIdx < 0
                            ? "🗣 " + w.text + ": Нет связи"
                            : "🗣 " + w.text + " [" + voiceIdx + "]: " + w.voiceMessages[voiceIdx];
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
            final int originalScreenIndex = currentScreenIndex;
            final Screen originalScreen = originalScreenIndex >= 0
                    && originalScreenIndex < screens.size()
                    ? screens.get(originalScreenIndex) : null;
            if (!isRuntimeWidgetCurrent(widget, index, originalScreen, originalScreenIndex))
                return;
            final boolean previous = widget.buttonState;
            final boolean next = !previous;
            if (!widget.extraFields.optBoolean("craftSwitchBitEnabled", false)) {
                JSONObject oldProject = projectExtraFields;
                widget.buttonState = next;
                if (!persistProjectNow(true)) {
                    widget.buttonState = previous;
                    projectExtraFields = oldProject;
                    saveActiveScreen();
                    Toast.makeText(MainActivity.this, "Не удалось сохранить переключатель",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Переключатель: " + (next ? "ВКЛ" : "ВЫКЛ"),
                            Toast.LENGTH_SHORT).show();
                }
                invalidate();
                return;
            }
            final BindingRequest binding = parseBinding(-1, widget.connectionInfo);
            if (binding == null || binding.functionCode != 3) {
                Toast.makeText(MainActivity.this,
                        "Для записи бита нужен TCP holding-регистр FC03/FC22",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (widget.craftSwitchWritePending) {
                Toast.makeText(MainActivity.this, "Запись переключателя ещё выполняется",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            final int bit = widget.extraFields.optInt("craftSwitchBit", -1);
            final int[] masks;
            try { masks = OriginalCraftSwitch.writeMasks(bit, next); }
            catch (IllegalArgumentException invalid) {
                Toast.makeText(MainActivity.this, invalid.getMessage(),
                        Toast.LENGTH_LONG).show();
                return;
            }
            final JSONObject oldFields = widget.extraFields;
            final int requestSequence =
                    widget.extraFields.optInt("craftSwitchWriteSequence", 0) + 1;
            try {
                widget.extraFields = new JSONObject(oldFields.toString());
                widget.extraFields.put("craftSwitchWriteSequence", requestSequence);
            } catch (Exception error) {
                widget.extraFields = oldFields;
                Toast.makeText(MainActivity.this, "Не удалось подготовить запись бита",
                        Toast.LENGTH_LONG).show();
                return;
            }
            widget.craftSwitchWritePending = true;
            modbusExecutor.execute(() -> {
                Exception failure = null;
                try {
                    if (!isRuntimeWidgetCurrent(widget, index, originalScreen,
                            originalScreenIndex))
                        throw new IOException("Рабочий режим остановлен");
                    new ModbusTcpClient(binding.host, binding.port).writeMask(
                            binding.unit, binding.address, masks[0], masks[1]);
                } catch (Exception error) { failure = error; }
                final Exception result = failure;
                runOnUiThread(() -> {
                    widget.craftSwitchWritePending = false;
                    if (!isRuntimeWidgetCurrent(widget, index, originalScreen,
                            originalScreenIndex)
                            || widget.extraFields.optInt("craftSwitchWriteSequence", 0)
                                    != requestSequence
                            || widget.buttonState != previous) return;
                    if (result != null) {
                        widget.extraFields = oldFields;
                        Toast.makeText(MainActivity.this,
                                "Ошибка записи бита переключателя: "
                                        + describeModbusError(result), Toast.LENGTH_LONG).show();
                        invalidate();
                        return;
                    }
                    JSONObject oldProject = projectExtraFields;
                    widget.buttonState = next;
                    if (!persistProjectNow(true)) {
                        widget.buttonState = previous;
                        widget.extraFields = oldFields;
                        projectExtraFields = oldProject;
                        saveActiveScreen();
                        Toast.makeText(MainActivity.this,
                                "Бит записан, но проект не сохранён",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this,
                                "Переключатель: " + (next ? "ВКЛ" : "ВЫКЛ"),
                                Toast.LENGTH_SHORT).show();
                    }
                    invalidate();
                });
            });
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

        private void restoreCraftBitListChecks(android.content.DialogInterface dialog,
                java.util.List<Integer> bits, int mask) {
            if (!(dialog instanceof AlertDialog)) return;
            android.widget.ListView list = ((AlertDialog) dialog).getListView();
            if (list == null) return;
            for (int i = 0; i < bits.size(); i++)
                list.setItemChecked(i, (mask & (1 << bits.get(i))) != 0);
        }

        private void openCraftBitList(Widget widget, int widgetIndex) {
            final int openingScreenIndex = currentScreenIndex;
            final Screen openingScreen = openingScreenIndex >= 0
                    && openingScreenIndex < screens.size()
                    ? screens.get(openingScreenIndex) : null;
            if (!isRuntimeWidgetCurrent(widget, widgetIndex, openingScreen, openingScreenIndex))
                return;
            String[] allLabels = CraftBitList.fromJson(
                    widget.extraFields.optJSONArray("craftListLabels"));
            java.util.ArrayList<String> choices = new java.util.ArrayList<>();
            java.util.ArrayList<Integer> bits = new java.util.ArrayList<>();
            for (int bit = 0; bit < 16; bit++)
                if (allLabels[bit] != null && !allLabels[bit].trim().isEmpty()) {
                    choices.add(allLabels[bit]);
                    bits.add(bit);
                }
            if (choices.isEmpty()) {
                Toast.makeText(MainActivity.this,
                        "У списка нет подписанных пунктов. Настрой его в редакторе.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (widget.extraFields.optBoolean("craftListCheckNoChange", false)) {
                int shownMask = widget.extraFields.optInt("craftListInitialMask", 0) & 0xffff;
                String liveValue = liveValues.get(widgetIndex);
                if (liveValue != null) try {
                    shownMask = CraftBitList.fromRegister(Integer.parseInt(liveValue));
                } catch (Exception ignored) {}
                String[] readOnlyChoices = new String[choices.size()];
                for (int k = 0; k < choices.size(); k++)
                    readOnlyChoices[k] = ((shownMask & (1 << bits.get(k))) != 0
                            ? "✓  " : "○  ") + choices.get(k);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(widget.text == null ? "Список" : widget.text)
                        .setItems(readOnlyChoices, null)
                        .setMessage("Значения доступны только для просмотра в рабочем режиме")
                        .setPositiveButton("Закрыть", null).show();
                return;
            }
            int initialMask = widget.extraFields.optInt("craftListInitialMask", 0) & 0xffff;
            String current = liveValues.get(widgetIndex);
            if (current != null) try {
                initialMask = CraftBitList.fromRegister(Integer.parseInt(current));
            } catch (Exception ignored) {}
            final int[] mask = {initialMask};
            final int[] confirmedMask = {initialMask};
            final int[] changeSequence = {0};
            boolean[] checked = new boolean[bits.size()];
            for (int k = 0; k < bits.size(); k++)
                checked[k] = (mask[0] & (1 << bits.get(k))) != 0;
            final String connection = widget.connectionInfo == null
                    ? "" : widget.connectionInfo.trim();
            final BindingRequest binding = connection.isEmpty()
                    ? null : parseBinding(-1, connection);
            if (!connection.isEmpty() && binding == null) {
                Toast.makeText(MainActivity.this, "Проверь TCP привязку битового списка",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (binding != null && binding.functionCode != 3) {
                Toast.makeText(MainActivity.this,
                        "Битовый список использует holding-регистр FC03/FC06",
                        Toast.LENGTH_LONG).show();
                return;
            }
            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(widget.text == null || widget.text.isEmpty()
                            ? "Выбери пункты" : widget.text)
                    .setMultiChoiceItems(choices.toArray(new String[0]), checked,
                            (d, position, isChecked) -> {
                        if (!isRuntimeWidgetCurrent(widget, widgetIndex,
                                openingScreen, openingScreenIndex)) {
                            d.dismiss();
                            return;
                        }
                        if (position < 0 || position >= bits.size()) return;
                        int bit = bits.get(position);
                        final int previousMask = mask[0];
                        final int next = (isChecked
                                ? mask[0] | (1 << bit) : mask[0] & ~(1 << bit)) & 0xffff;
                        if (next == previousMask) return;
                        if (binding == null) {
                            JSONObject previous = widget.extraFields;
                            JSONObject previousProject = projectExtraFields;
                            boolean committed = false;
                            try {
                                widget.extraFields = new JSONObject(previous.toString());
                                widget.extraFields.put("craftListInitialMask", next);
                                if (!persistProjectNow(true))
                                    throw new IOException("Проект не сохранён");
                                committed = true;
                                mask[0] = next;
                                confirmedMask[0] = next;
                                liveValues.put(widgetIndex, Integer.toString(next));
                            } catch (Exception failure) {
                                Toast.makeText(MainActivity.this,
                                        "Не удалось сохранить список: " + failure.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            } finally {
                                if (!committed) {
                                    widget.extraFields = previous;
                                    projectExtraFields = previousProject;
                                    mask[0] = previousMask;
                                    restoreCraftBitListChecks(d, bits, previousMask);
                                    saveActiveScreen();
                                }
                                invalidate();
                            }
                            return;
                        }
                        mask[0] = next;
                        final int requestedMask = next;
                        final int sequence = ++changeSequence[0];
                        modbusExecutor.execute(() -> {
                            Exception failure = null;
                            try {
                                if (!isRuntimeWidgetCurrent(widget, widgetIndex,
                                        openingScreen, openingScreenIndex))
                                    throw new IOException("Рабочий режим остановлен");
                                new ModbusTcpClient(binding.host, binding.port).writeSingle(
                                        binding.unit, binding.address, requestedMask);
                            } catch (Exception error) { failure = error; }
                            final Exception result = failure;
                            runOnUiThread(() -> {
                                if (!isRuntimeWidgetCurrent(widget, widgetIndex,
                                        openingScreen, openingScreenIndex)) return;
                                if (result == null) confirmedMask[0] = requestedMask;
                                if (sequence == changeSequence[0]) {
                                    if (result != null) {
                                        mask[0] = confirmedMask[0];
                                        restoreCraftBitListChecks(d, bits, mask[0]);
                                        Toast.makeText(MainActivity.this,
                                                "Не удалось записать маску списка: "
                                                        + describeModbusError(result),
                                                Toast.LENGTH_LONG).show();
                                    }
                                    liveValues.put(widgetIndex, Integer.toString(
                                            result == null ? requestedMask : confirmedMask[0]));
                                    invalidate();
                                }
                            });
                        });
                    })
                    .setPositiveButton("Готово", null)
                    .setNegativeButton("Закрыть", null).create();
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

        /** Legacy two-button setpoint: safe local step or FC06 holding-register write. */
        private void adjustSetpoint(Widget widget, int direction) {
            if (!operatorMode || !activityResumed || editor != this
                    || widget == null || direction == 0) return;
            final int index = widgets.indexOf(widget);
            if (index < 0 || !"Задатчик".equals(widget.type)) return;
            final double min = Math.min(widget.minValue, widget.maxValue);
            final double max = Math.max(widget.minValue, widget.maxValue);
            if (!Double.isFinite(min) || !Double.isFinite(max)
                    || min < Integer.MIN_VALUE || max > Integer.MAX_VALUE) {
                Toast.makeText(MainActivity.this, "Некорректный диапазон задатчика",
                        Toast.LENGTH_LONG).show();
                return;
            }
            final int lower = (int) Math.ceil(min), upper = (int) Math.floor(max);
            if (lower > upper) return;
            final int oldValue = widget.extraFields.optInt("bloodskySetpointValue", lower);
            final int step = Math.max(1, Math.min(65535,
                    widget.extraFields.optInt("bloodskySetpointStep", 1)));
            final long requested = (long) oldValue + (direction < 0 ? -(long) step : step);
            final int next = (int) Math.max(lower, Math.min(upper, requested));
            if (next == oldValue) return;
            final String connection = widget.connectionInfo == null ? "" : widget.connectionInfo.trim();
            final BindingRequest binding = connection.isEmpty() ? null : parseBinding(-1, connection);
            if (!connection.isEmpty() && (binding == null || binding.functionCode != 3
                    || next < 0 || next > 65535)) {
                Toast.makeText(MainActivity.this,
                        "Для задатчика нужен TCP holding-регистр FC03/FC06 (0–65535)",
                        Toast.LENGTH_LONG).show();
                return;
            }
            final EditorCanvas originalCanvas = this;
            final Screen originalScreen = currentScreenIndex >= 0
                    && currentScreenIndex < screens.size() ? screens.get(currentScreenIndex) : null;
            final int originalScreenIndex = currentScreenIndex;
            final Runnable apply = () -> {
                if (!operatorMode || !activityResumed || editor != originalCanvas
                        || originalScreen == null || currentScreenIndex != originalScreenIndex
                        || currentScreenIndex < 0 || currentScreenIndex >= screens.size()
                        || screens.get(currentScreenIndex) != originalScreen
                        || index >= widgets.size() || widgets.get(index) != widget) return;
                JSONObject previous = widget.extraFields;
                try {
                    widget.extraFields = new JSONObject(previous.toString());
                    widget.extraFields.put("bloodskySetpointValue", next);
                    if (!persistProjectNow(true)) {
                        widget.extraFields = previous;
                        saveActiveScreen();
                        Toast.makeText(MainActivity.this, "Не удалось сохранить уставку",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    liveValues.put(index, Integer.toString(next));
                    invalidate();
                    Toast.makeText(MainActivity.this, "Уставка: " + next, Toast.LENGTH_SHORT).show();
                } catch (Exception failure) {
                    widget.extraFields = previous;
                    saveActiveScreen();
                    Toast.makeText(MainActivity.this, "Не удалось изменить уставку",
                            Toast.LENGTH_LONG).show();
                }
            };
            if (binding == null) {
                apply.run();
                return;
            }
            modbusExecutor.execute(() -> {
                Exception failure = null;
                try {
                    if (!operatorMode || !activityResumed || editor != originalCanvas
                            || !widgets.contains(widget)) throw new IOException("Рабочий режим остановлен");
                    new ModbusTcpClient(binding.host, binding.port)
                            .writeSingle(binding.unit, binding.address, next);
                } catch (Exception error) { failure = error; }
                final Exception result = failure;
                runOnUiThread(() -> {
                    if (result == null) apply.run();
                    else if (editor == originalCanvas && widgets.contains(widget))
                        Toast.makeText(MainActivity.this,
                                "Ошибка записи уставки: " + describeModbusError(result),
                                Toast.LENGTH_LONG).show();
                });
            });
        }

        /** Delayed operator dialogs must never commit into a replaced canvas/page. */
        private boolean isRuntimeWidgetCurrent(Widget widget, int index,
                Screen originalScreen, int originalScreenIndex) {
            return activityResumed && operatorMode && editor == this
                    && originalScreen != null && originalScreenIndex >= 0
                    && currentScreenIndex == originalScreenIndex
                    && currentScreenIndex < screens.size()
                    && screens.get(currentScreenIndex) == originalScreen
                    && index >= 0 && index < widgets.size()
                    && widgets.get(index) == widget;
        }

        /** Apply an operator value only when its project snapshot persists. */
        private boolean saveRuntimeLinearSetpoint(Widget widget, int index, int next,
                Screen originalScreen, int originalScreenIndex) {
            if (!isRuntimeWidgetCurrent(widget, index, originalScreen, originalScreenIndex))
                return false;
            JSONObject oldFields = widget.extraFields;
            JSONObject oldProject = projectExtraFields;
            try {
                widget.extraFields = new JSONObject(oldFields.toString());
                widget.extraFields.put("craftLinearValue", next);
                if (!persistProjectNow(true)) {
                    widget.extraFields = oldFields;
                    projectExtraFields = oldProject;
                    saveActiveScreen();
                    Toast.makeText(MainActivity.this, "Не удалось сохранить уставку",
                            Toast.LENGTH_LONG).show();
                    return false;
                }
                liveValues.put(index, Integer.toString(next));
                invalidate();
                return true;
            } catch (Exception error) {
                widget.extraFields = oldFields;
                projectExtraFields = oldProject;
                saveActiveScreen();
                Toast.makeText(MainActivity.this, "Не удалось сохранить уставку",
                        Toast.LENGTH_LONG).show();
                return false;
            }
        }

        private void openCraftLinearSetter(Widget widget, int widgetIndex) {
            final int openingScreenIndex = currentScreenIndex;
            final Screen openingScreen = openingScreenIndex >= 0
                    && openingScreenIndex < screens.size()
                    ? screens.get(openingScreenIndex) : null;
            if (!isRuntimeWidgetCurrent(widget, widgetIndex, openingScreen, openingScreenIndex))
                return;
            final int maximum = CraftLinearSetter.maximum(widget.extraFields);
            final int initial = CraftLinearSetter.value(widget.extraFields, liveValues.get(widgetIndex));
            final String connection = widget.connectionInfo == null ? ""
                    : widget.connectionInfo.trim();
            final BindingRequest binding = connection.isEmpty() ? null
                    : parseBinding(-1, connection);
            if (!connection.isEmpty() && (binding == null || binding.functionCode != 3)) {
                Toast.makeText(MainActivity.this, "Проверь TCP holding-регистр FC03/FC06",
                        Toast.LENGTH_LONG).show();
                return;
            }
            android.widget.LinearLayout form = new android.widget.LinearLayout(MainActivity.this);
            form.setOrientation(android.widget.LinearLayout.VERTICAL);
            form.setPadding(dp(22), dp(14), dp(22), dp(4));
            TextView current = new TextView(MainActivity.this);
            current.setText(initial + " / " + maximum);
            form.addView(current);
            android.widget.SeekBar slider = new android.widget.SeekBar(MainActivity.this);
            slider.setMax(maximum);
            slider.setProgress(initial);
            if (widget.extraFields.optBoolean("craftLinearVertical", false))
                slider.setRotation(270f);
            form.addView(slider);
            slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar seekBar,
                        int progress, boolean fromUser) {
                    current.setText(progress + " / " + maximum);
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
            });
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(widget.text == null || widget.text.isEmpty()
                            ? "Линейный задатчик" : widget.text)
                    .setView(form).setNegativeButton("Отмена", null)
                    .setPositiveButton("Задать", (dialog, which) -> {
                        if (!isRuntimeWidgetCurrent(widget, widgetIndex,
                                openingScreen, openingScreenIndex)) {
                            Toast.makeText(MainActivity.this,
                                    "Экран изменился: повторно открой задатчик",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        final int next = slider.getProgress();
                        if (binding == null) {
                            saveRuntimeLinearSetpoint(widget, widgetIndex, next,
                                    openingScreen, openingScreenIndex);
                            return;
                        }
                        modbusExecutor.execute(() -> {
                            Exception failure = null;
                            try {
                                if (!isRuntimeWidgetCurrent(widget, widgetIndex,
                                        openingScreen, openingScreenIndex))
                                    throw new IOException("Рабочий режим остановлен");
                                new ModbusTcpClient(binding.host, binding.port)
                                        .writeSingle(binding.unit, binding.address, next);
                            } catch (Exception error) { failure = error; }
                            final Exception result = failure;
                            runOnUiThread(() -> {
                                if (!isRuntimeWidgetCurrent(widget, widgetIndex,
                                        openingScreen, openingScreenIndex)) return;
                                if (result == null) {
                                    if (!saveRuntimeLinearSetpoint(widget, widgetIndex, next,
                                            openingScreen, openingScreenIndex))
                                        Toast.makeText(MainActivity.this,
                                                "Регистр записан, но проект не сохранён",
                                                Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(MainActivity.this,
                                            "Не удалось записать значение: "
                                                    + describeModbusError(result),
                                            Toast.LENGTH_LONG).show();
                                }
                                invalidate();
                            });
                        });
                    }).show();
        }

        private void openCraftNumericInput(Widget widget, int widgetIndex) {
            final int openingScreenIndex = currentScreenIndex;
            final Screen openingScreen = openingScreenIndex >= 0
                    && openingScreenIndex < screens.size()
                    ? screens.get(openingScreenIndex) : null;
            if (!isRuntimeWidgetCurrent(widget, widgetIndex, openingScreen, openingScreenIndex))
                return;
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
                if (!isRuntimeWidgetCurrent(widget, widgetIndex,
                        openingScreen, openingScreenIndex)) {
                    Toast.makeText(MainActivity.this, "Экран изменился: открой ввод заново",
                            Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    return;
                }
                if (passwordRequired && (passwordField == null || !expectedPassword.equals(passwordField.getText().toString()))) {
                    Toast.makeText(MainActivity.this, "Неверный пароль", Toast.LENGTH_LONG).show(); return;
                }
                final double next;
                try { next = CraftNumericInput.parseCommit(value.getText().toString(), widget.extraFields); }
                catch (IllegalArgumentException invalid) { Toast.makeText(MainActivity.this, invalid.getMessage(), Toast.LENGTH_LONG).show(); return; }
                final String formatted = widget.extraFields.optInt("craftNumericMode", CraftNumericInput.INTEGER) == CraftNumericInput.FLOAT32
                        ? CraftNumericInput.truncateFloatDisplay((float) next, widget.extraFields) : Integer.toString((int) next);
                if (binding == null) {
                    final String oldText = widget.text;
                    final String oldLiveValue = liveValues.get(widgetIndex);
                    final JSONObject oldProject = projectExtraFields;
                    org.json.JSONArray oldSystemRegisters = null;
                    boolean committed = false;
                    try {
                        if (craftSystemBinding) {
                            oldSystemRegisters = systemRegisterBank.toProjectJson();
                            if (!CraftSystemNumericWidgetBinding.writeValue(
                                    widget.extraFields, next, systemRegisterBank))
                                throw new IllegalStateException("Системный регистр недоступен");
                        }
                        widget.text = formatted;
                        if (!persistProjectNow(true))
                            throw new IllegalStateException("Проект не сохранён");
                        committed = true;
                        liveValues.put(widgetIndex, formatted);
                        invalidate();
                        dialog.dismiss();
                    } catch (Exception failure) {
                        Toast.makeText(MainActivity.this,
                                "Не удалось записать значение: " + failure.getMessage(),
                                Toast.LENGTH_LONG).show();
                    } finally {
                        if (!committed) {
                            widget.text = oldText;
                            projectExtraFields = oldProject;
                            if (oldSystemRegisters != null)
                                systemRegisterBank.restoreProjectJson(oldSystemRegisters);
                            if (oldLiveValue == null) liveValues.remove(widgetIndex);
                            else liveValues.put(widgetIndex, oldLiveValue);
                            saveActiveScreen();
                            invalidate();
                        }
                    }
                    return;
                }
                final int registerCount = CraftNumericInput.registerCount(widget.extraFields);
                final int[] encoded;
                try {
                    encoded = registerCount == 2
                            ? FbdCraftRegisterCodec.floatRegisters((float) next)
                            : new int[]{CraftNumericInput.integerRegister(next, widget.extraFields)};
                } catch (Exception invalid) {
                    Toast.makeText(MainActivity.this, "Неверное значение регистра",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                modbusExecutor.execute(() -> {
                    Exception failure = null;
                    try {
                        if (!isRuntimeWidgetCurrent(widget, widgetIndex,
                                openingScreen, openingScreenIndex))
                            throw new IOException("Рабочий режим остановлен");
                        ModbusTcpClient client = new ModbusTcpClient(binding.host, binding.port);
                        if (registerCount == 2)
                            client.writeMultiple(binding.unit, binding.address, encoded);
                        else client.writeSingle(binding.unit, binding.address, encoded[0]);
                    } catch (Exception error) { failure = error; }
                    final Exception result = failure;
                    runOnUiThread(() -> {
                        if (!isRuntimeWidgetCurrent(widget, widgetIndex,
                                openingScreen, openingScreenIndex)) return;
                        if (result == null) {
                            String oldText = widget.text;
                            JSONObject oldProject = projectExtraFields;
                            widget.text = formatted;
                            if (persistProjectNow(true)) {
                                liveValues.put(widgetIndex, formatted);
                                dialog.dismiss();
                            } else {
                                widget.text = oldText;
                                projectExtraFields = oldProject;
                                saveActiveScreen();
                                Toast.makeText(MainActivity.this,
                                        "Регистр записан, но проект не сохранён",
                                        Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Не удалось записать значение: " + describeModbusError(result),
                                    Toast.LENGTH_LONG).show();
                        }
                        invalidate();
                    });
                });
            }));
            dialog.show(); value.requestFocus();
        }

        private void openCraftRtsp(Widget widget) {
            if (!operatorMode || !activityResumed
                    || !widget.extraFields.optBoolean("craftRtspEnabled", false)) {
                Toast.makeText(MainActivity.this, "Разрешите RTSP в свойствах и запустите рабочий режи��", Toast.LENGTH_LONG).show();
                return;
            }
            JSONObject sourceOther = widget.extraFields.optJSONObject("craftOther");
            String candidate = sourceOther == null ? "" : sourceOther.optString("rtsp_url", "");
            final CraftRtspConfig config;
            try { config = CraftRtspConfig.parse(candidate); }
            catch (IllegalArgumentException invalid) {
                Toast.makeText(MainActivity.this, invalid.getMessage(), Toast.LENGTH_LONG).show(); return;
            }
            URI uri = URI.create(config.getUrl());
            String endpoint = uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost()
                    + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
            new AlertDialog.Builder(MainActivity.this).setTitle("Запустить видеопоток?")
                    .setMessage(endpoint + "\nБудет открыто сетевое RTSP-соединение. Остановите поток, если устройство или сеть не доверенные.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Запустить", (dialog, which) -> {
                        if (!widgets.contains(widget)) return;
                        try {
                            CraftRtspConfig authorized = CraftRtspPlaybackPolicy.authorize(operatorMode,
                                    activityResumed, widget.extraFields.optBoolean("craftRtspEnabled", false),
                                    true, candidate);
                            showCraftRtspPlayer(widget.text, authorized);
                        } catch (IllegalArgumentException | IllegalStateException invalid) {
                            Toast.makeText(MainActivity.this, invalid.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }).show();
        }

        private void showCraftRtspPlayer(String title, CraftRtspConfig config) {
            closeCraftRtspPlayer();
            LinearLayout content = new LinearLayout(MainActivity.this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(12), dp(8), dp(12), dp(8));
            TextView status = new TextView(MainActivity.this);
            status.setText("Подключение к RTSP. Поддержка зависит от формата потока и Android-устройства.");
            content.addView(status, new LinearLayout.LayoutParams(-1, -2));
            VideoView video = new VideoView(MainActivity.this);
            video.setKeepScreenOn(true);
            LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(-1, dp(230));
            videoParams.topMargin = dp(8);
            content.addView(video, videoParams);
            LinearLayout controls = new LinearLayout(MainActivity.this);
            controls.setGravity(Gravity.CENTER);
            Button pause = new Button(MainActivity.this);
            pause.setText("Пауза");
            pause.setAllCaps(false);
            Button stop = new Button(MainActivity.this);
            stop.setText("Остановить");
            stop.setAllCaps(false);
            controls.addView(pause, new LinearLayout.LayoutParams(0, -2, 1f));
            controls.addView(stop, new LinearLayout.LayoutParams(0, -2, 1f));
            content.addView(controls, new LinearLayout.LayoutParams(-1, -2));
            Dialog dialog = new Dialog(MainActivity.this);
            dialog.setTitle(title == null || title.trim().isEmpty() ? "Видео RTSP Craft" : title);
            dialog.setContentView(content);
            dialog.setOnDismissListener(dismissed -> {
                video.setKeepScreenOn(false);
                try { video.stopPlayback(); } catch (RuntimeException ignored) { }
                if (craftRtspVideoView == video) craftRtspVideoView = null;
                if (craftRtspDialog == dialog) craftRtspDialog = null;
            });
            craftRtspDialog = dialog;
            craftRtspVideoView = video;
            video.setOnPreparedListener(player -> status.setText("Поток воспроизводится"));
            video.setOnErrorListener((player, what, extra) -> {
                status.setText("Не удалось воспроизвести поток на этом устройстве");
                return true;
            });
            pause.setOnClickListener(v -> {
                try {
                    if (video.isPlaying()) { video.pause(); pause.setText("Продолжить"); }
                    else { video.start(); pause.setText("Пауза"); }
                } catch (RuntimeException failure) {
                    status.setText("Поток недоступен");
                }
            });
            stop.setOnClickListener(v -> closeCraftRtspPlayer());
            dialog.show();
            video.setVideoURI(Uri.parse(config.getUrl()));
            video.start();
        }

        private void openCraftWebView(Widget widget, int index) {
            if (!operatorMode || !activityResumed || !widget.extraFields.optBoolean("craftWebViewEnabled", false)) {
                Toast.makeText(MainActivity.this, "Разрешите WebView в свойствах компонента", Toast.LENGTH_LONG).show();
                return;
            }
            String[] links = CraftWebViewLinks.read(widget.extraFields.optJSONObject("craftOther"));
            if (!widget.connectionInfo.trim().isEmpty()) {
                int selected = CraftWebViewPolicy.selectedIndex(liveValues.get(index));
                if (selected < 0) {
                    Toast.makeText(MainActivity.this, "Нет активного бита или достоверного значения регистра", Toast.LENGTH_LONG).show();
                    return;
                }
                confirmCraftWebView(widget, links[selected]);
            } else {
                String[] labels = new String[links.length];
                for (int i = 0; i < links.length; i++) labels[i] = i + ": " + links[i];
                new AlertDialog.Builder(MainActivity.this).setTitle("Ссылка WebView")
                        .setItems(labels, (d, which) -> confirmCraftWebView(widget, links[which]))
                        .setNegativeButton("Отмена", null).show();
            }
        }

        private void confirmCraftWebView(Widget widget, String candidate) {
            final String url;
            try { url = CraftWebViewPolicy.checkedUrl(candidate); }
            catch (IllegalArgumentException invalid) {
                Toast.makeText(MainActivity.this, invalid.getMessage(), Toast.LENGTH_LONG).show(); return;
            }
            new AlertDialog.Builder(MainActivity.this).setTitle("Открыть страницу?")
                    .setMessage(url + "\nБудет выполнен сетевой запрос к этому серверу.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Открыть", (d, which) -> {
                        if (!operatorMode || !activityResumed || !widgets.contains(widget)
                                || !widget.extraFields.optBoolean("craftWebViewEnabled", false)) return;
                        try { craftWebViewWindow.open(MainActivity.this, url); }
                        catch (RuntimeException failure) {
                            craftWebViewWindow.close();
                            Toast.makeText(MainActivity.this, "Не удалось открыть WebView", Toast.LENGTH_LONG).show();
                        }
                    }).show();
        }

        private void requestCraftEmail(Widget widget) {
            if (!operatorMode || !activityResumed || !widgets.contains(widget)
                    || !widget.extraFields.optBoolean("craftEmailEnabled", false)) {
                Toast.makeText(MainActivity.this, "Email Craft выключен в свойствах компонента", Toast.LENGTH_LONG).show();
                return;
            }
            String recipient = widget.extraFields.optString("craftEmailTo", "").trim();
            String subject = widget.extraFields.optString("craftEmailTitle", widget.text);
            String body = widget.extraFields.optString("craftEmailBody", "");
            String validationError = CraftEmailPolicy.validationError(recipient, subject, body);
            if (validationError != null) {
                Toast.makeText(MainActivity.this, validationError, Toast.LENGTH_LONG).show();
                return;
            }
            new AlertDialog.Builder(MainActivity.this).setTitle("Подготовить письмо?")
                    .setMessage("Получатель: " + recipient + "\nТема: " + subject
                            + "\n\nОткроется приложение почты с черновиком. Письмо отправится только после отдельного нажатия «Отправить» в нём.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Открыть почту", (dialog, which) -> {
                        if (!operatorMode || !activityResumed || !widgets.contains(widget)
                                || !widget.extraFields.optBoolean("craftEmailEnabled", false)) return;
                        Intent compose = new Intent(Intent.ACTION_SENDTO,
                                Uri.fromParts("mailto", recipient, null));
                        compose.putExtra(Intent.EXTRA_SUBJECT, subject);
                        compose.putExtra(Intent.EXTRA_TEXT, body);
                        try { startActivity(Intent.createChooser(compose, "Подготовить email")); }
                        catch (android.content.ActivityNotFoundException unavailable) {
                            Toast.makeText(MainActivity.this, "На устройстве нет приложения для email", Toast.LENGTH_LONG).show();
                        }
                    }).show();
        }

        private void activateRuntimeWidget(int index, float x, float y) {
            Widget picked = widgets.get(index);
            if ("Видео RTSP Craft".equals(picked.type)) {
                openCraftRtsp(picked);
            } else if ("WebView Craft".equals(picked.type)) {
                openCraftWebView(picked, index);
            } else if ("Распознавание речи Craft".equals(picked.type)) {
                requestCraftVoiceRecognition(picked);
            } else if ("Email Craft".equals(picked.type)) {
                requestCraftEmail(picked);
            } else if ("Архив Craft".equals(picked.type)) {
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

