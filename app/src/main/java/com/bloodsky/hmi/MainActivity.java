Y™Áäx-ÆÈ‹j◊ù¢Îi∫⁄+äßj[hëÈ‹¢ÈÌﬂ^v€Ωµ€}ˆo+^≤â¢∂◊ùpackage com.bloodsky.hmi;

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
    private String projectName = "–ú–æ–π –ø—Ä–æ–µ–∫—Ç";
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
            } else Toast.makeText(this, "–î–ª—è –≥–æ–ª–æ—Å–æ–≤–æ–≥–æ —É–ø—Ä–∞–≤–ª–µ–Ω–∏—è –Ω—É–∂–µ–Ω –¥–æ—Å—Ç—É–ø –∫ –º–∏–∫—Ä–æ—Ñ–æ–Ω—É", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_SMS_SEND_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "–†–∞–∑—Ä–µ—à–µ–Ω–∏–µ SMS –≤–∫–ª—é—á–µ–Ω–æ; —É–≤–µ–¥–æ–º–ª–µ–Ω–∏–µ –ø—Ä–∏–¥—ë—Ç –ø—Ä–∏ —Å–ª–µ–¥—É—é—â–µ–º –Ω–æ–≤–æ–º —Å–æ–±—ã—Ç–∏–∏", Toast.LENGTH_LONG).show();
            else Toast.makeText(this, "–û—Ç–ø—Ä–∞–≤–∫–∞ SMS –æ—Ç–∫–ª—é—á–µ–Ω–∞ –±–µ–∑ —Ä–∞–∑—Ä–µ—à–µ–Ω–∏—è SEND_SMS", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_SMS_RECEIVE_PERMISSION) {
            smsReceivePermissionRequested = false;
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                refreshIncomingSmsReceiver();
            } else {
                Toast.makeText(this, "–í—Ö–æ–¥—è—â–µ–µ SMS –æ—Ç–∫–ª—é—á–µ–Ω–æ –±–µ–∑ —Ä–∞–∑—Ä–µ—à–µ–Ω–∏—è RECEIVE_SMS", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_CRAFT_BLUETOOTH_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
                showCraftBluetoothPicker(pendingCraftBluetoothReturnsToDevices, pendingCraftBluetoothSlot);
            else Toast.makeText(this, "–°–ø–∏—Å–æ–∫ Bluetooth-—É—Å—Ç—Ä–æ–π—Å—Ç–≤ –Ω–µ–¥–æ—Å—Ç—É–ø–µ–Ω –±–µ–∑ —Ä–∞–∑—Ä–µ—à–µ–Ω–∏—è", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_OPERATOR_BLUETOOTH_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
                startModbusPolling();
            else {
                externalBluetoothPermissionDeniedForSession = true;
                Toast.makeText(this, "Bluetooth RTU –≤—ã–∫–ª—é—á–µ–Ω –±–µ–∑ —Ä–∞–∑—Ä–µ—à–µ–Ω–∏—è BLUETOOTH_CONNECT; TCP –∏ –≤—ã–±—Ä–∞–Ω–Ω—ã–π USB –æ—Å—Ç–∞—é—Ç—Å—è –¥–æ—Å—Ç—É–ø–Ω—ã", Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, "–£–≤–µ–¥–æ–º–ª–µ–Ω–∏–µ –Ω–µ –ø–æ–∫–∞–∑–∞–Ω–æ –±–µ–∑ —Ä–∞–∑—Ä–µ—à–µ–Ω–∏—è POST_NOTIFICATIONS", Toast.LENGTH_LONG).show();
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
            } else if (pathOrUri != null && !pathOrUri.trim().isEmpty() && !"–ê–ª–∞—Ä–º".equals(pathOrUri)) {
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
            if ("–ó–≤—É–∫–æ–≤–æ–π —Å–∏–≥–Ω–∞–ª".equals(widget.type)) {
                widget.lastSoundState = false;
                stopCraftAlarmAlert(screenIndex + ":" + index);
            }
        }
    }

    private void resetCraftAlarmEdges() {
        for (Screen screen : screens) {
            for (Widget widget : screen.widgets) {
                if ("–ó–≤—É–∫–æ–≤–æ–π —Å–∏–≥–Ω–∞–ª".equals(widget.type)) widget.lastSoundState = false;
            }
        }
        for (Widget widget : widgets) {
            if ("–ó–≤—É–∫–æ–≤–æ–π —Å–∏–≥–Ω–∞–ª".equals(widget.type)) widget.lastSoundState = false;
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
        catch (Exception error) { Toast.makeText(this, "–°–æ—Ö—Ä–∞–Ω–µ–Ω–∏–µ –ø—Ä–æ–µ–∫—Ç–∞: " + error.getMessage(), Toast.LENGTH_LONG).show(); }
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
        addLegacyMenuItem("–°–æ–∑–¥–∞—Ç—å –Ω–æ–≤—ã–π –ø—Ä–æ–µ–∫—Ç", this::createNamedProject, menuWidth);
        addLegacyMenuItem("–ó–∞–≥—Ä—É–∑–∏—Ç—å –ø—Ä–æ–µ–∫—Ç", () -> chooseSavedProject(false), menuWidth);
        addLegacyMenuItem("–£–¥–∞–ª–∏—Ç—å –ø—Ä–æ–µ–∫—Ç", () -> chooseSavedProject(true), menuWidth);
        addLegacyMenuItem("–í–æ—Å—Å—Ç–∞–Ω–æ–≤–∏—Ç—å –ø—Ä–æ–µ–∫—Ç", this::chooseTrashedProject, menuWidth);
        addLegacyMenuItem("–≠–∫—Å–ø–æ—Ä—Ç –ø—Ä–æ–µ–∫—Ç–∞", () -> {
            if (getSharedPreferences(PREFS, MODE_PRIVATE).contains(ACTIVE_PROJECT)) {
                if (loadProject()) exportProject();
            } else Toast.makeText(this, "–°–Ω–∞—á–∞–ª–∞ —Å–æ–∑–¥–∞–π –∏–ª–∏ –∑–∞–≥—Ä—É–∑–∏ –ø—Ä–æ–µ–∫—Ç", Toast.LENGTH_SHORT).show();
        }, menuWidth);
        addLegacyMenuItem("–ò–º–ø–æ—Ä—Ç –ø—Ä–æ–µ–∫—Ç–∞", this::importProject, menuWidth);
        addLegacyMenuItem("USB Serial ‚Ä¢ Modbus RTU", this::showUsbSerialScreen, menuWidth);
        addLegacyMenuItem("–í—ã—Ö–æ–¥", this::finish, menuWidth);
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
                .putString("project_name", new JSONObject(normalized).optString("projectName", "–ú–æ–π –ø—Ä–æ–µ–∫—Ç"));
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
                throw new IOException("–ù–µ —É–¥–∞–ª–æ—Å—å —Å–æ—Ö—Ä–∞–Ω–∏—Ç—å —Ç–µ–∫—É—â–∏–π –ø—Ä–æ–µ–∫—Ç");
            }
        }
        return prefs.getString(PROJECT_CATALOG, "{}");
    }

    private void createNamedProject() {
        EditText name = propertyField("", InputType.TYPE_CLASS_TEXT);
        name.setHint("–ò–º—è –ø—Ä–æ–µ–∫—Ç–∞");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("–°–æ–∑–¥–∞—Ç—å –Ω–æ–≤—ã–π –ø—Ä–æ–µ–∫—Ç")
                .setView(name).setNegativeButton("–û—Ç–º–µ–Ω–∞", null).setPositiveButton("–°–æ–∑–¥–∞—Ç—å", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                readProjectCatalog(); // Migrate/preserve the previous active project first.
                String json = ProjectCatalog.create(name.getText().toString());
                if (!storeActiveProject(json, true)) throw new IOException("–ù–µ —É–¥–∞–ª–æ—Å—å —Å–æ—Ö—Ä–∞–Ω–∏—Ç—å –ø—Ä–æ–µ–∫—Ç");
                if (!loadProject()) throw new IOException("–ù–µ —É–¥–∞–ª–æ—Å—å –æ—Ç–∫—Ä—ã—Ç—å –Ω–æ–≤—ã–π –ø—Ä–æ–µ–∫—Ç");
                dialog.dismiss(); showEditor();
            } catch (Exception error) { name.setError(error.getMessage()); }
        }));
        dialog.show();
    }

    private void chooseSavedProject(boolean deleting) {
        try {
            List<JSONObject> projects = ProjectCatalog.list(readProjectCatalog());
            if (projects.isEmpty()) {
                Toast.makeText(this, "–ù–µ—Ç —Å–æ—Ö—Ä–∞–Ω—ë–Ω–Ω—ã—Ö –ø—Ä–æ–µ–∫—Ç–æ–≤", Toast.LENGTH_SHORT).show(); return;
            }
            String[] labels = new String[projects.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = projects.get(i).optString("projectName", "–ú–æ–π –ø—Ä–æ–µ–∫—Ç");
            new AlertDialog.Builder(this).setTitle(deleting ? "–£–¥–∞–ª–∏—Ç—å –ø—Ä–æ–µ–∫—Ç" : "–ó–∞–≥—Ä—É–∑–∏—Ç—å –ø—Ä–æ–µ–∫—Ç")
                    .setItems(labels, (dialog, which) -> {
                        JSONObject selected = projects.get(which);
                        if (deleting) {
                            new AlertDialog.Builder(this).setTitle("–£–¥–∞–ª–∏—Ç—å –ø—Ä–æ–µ–∫—Ç?")
                                    .setMessage(selected.optString("projectName", "–ú–æ–π –ø—Ä–æ–µ–∫—Ç"))
                                    .setNegativeButton("–û—Ç–º–µ–Ω–∞", null).setPositiveButton("–£–¥–∞–ª–∏—Ç—å", (confirm, choice) -> deleteSavedProject(selected)).show();
                        } else try {
                            if (!storeActiveProject(selected.toString(), true)) throw new IOException("–ù–µ —É–¥–∞–ª–æ—Å—å –æ—Ç–∫—Ä—ã—Ç—å –ø—Ä–æ–µ–∫—Ç");
                            if (!loadProject()) throw new IOException("–ù–µ —É–¥–∞–ª–æ—Å—å –æ—Ç–∫—Ä—ã—Ç—å –ø—Ä–æ–µ–∫—Ç");
                            showEditor();
                        } catch (Exception error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
                    }).setNegativeButton("–û—Ç–º–µ–Ω–∞", null).show();
        } catch (Exception error) { Toast.makeText(this, "–°–ø–∏—Å–æ–∫ –ø—Ä–æ–µ–∫—Ç–æ–≤: " + error.getMessage(), Toast.LENGTH_LONG).show(); }
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
                throw new IOException("–ù–µ —É–¥–∞–ª–æ—Å—å —É–¥–∞–ª–∏—Ç—å –ø—Ä–æ–µ–∫—Ç");
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
                Toast.makeText(this, "–ù–µ—Ç —É–¥–∞–ª—ë–Ω–Ω—ã—Ö –ø—Ä–æ–µ–∫—Ç–æ–≤", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] labels = new String[projects.size()];
            for (int i = 0; i < labels.length; i++)
                labels[i] = projects.get(i).optString("projectName", "–ú–æ–π –ø—Ä–æ–µ–∫—Ç");
            new AlertDialog.Builder(this).setTitle("–í–æ—Å—Å—Ç–∞–Ω–æ–≤–∏—Ç—å –ø—Ä–æ–µ–∫—Ç")
                    .setItems(labels, (dialog, which) -> showTrashedProjectActions(projects.get(which)))
                    .setNegativeButton("–û—Ç–º–µ–Ω–∞", null).show();
        } catch (Exception error) {
            Toast.makeText(this, "–ö–æ—Ä–∑–∏–Ω–∞ –ø—Ä–æ–µ–∫—Ç–æ–≤: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showTrashedProjectActions(JSONObject selected) {
        new AlertDialog.Builder(this).setTitle(selected.optString("projectName", "–ú–æ–π –ø—Ä–æ–µ–∫—Ç"))
                .setItems(new String[]{"–í–æ—Å—Å—Ç–∞–Ω–æ–≤–∏—Ç—å –ø—Ä–æ–µ–∫—Ç", "–£–¥–∞–ª–∏—Ç—å –Ω–∞–≤—Å–µ–≥–¥–∞"}, (dialog, which) -> {
                    if (which == 0) restoreTrashedProject(selected);
                    else new AlertDialog.Builder(this).setTitle("–£–¥–∞–ª–∏—Ç—å –ø—Ä–æ–µ–∫—Ç –∏ –µ–≥–æ —Ñ–∞–π–ª—ã –Ω–∞–≤—Å–µ–≥–¥–∞?")
                            .setMessage("–ú–µ–¥–∏–∞ –ø—Ä–æ–µ–∫—Ç–∞ –∏ –µ–≥–æ —Ä–µ–∑–µ—Ä–≤–Ω—ã–µ –ø–æ–∫–æ–ª–µ–Ω–∏—è –±—É–¥—É—Ç —É–¥–∞–ª–µ–Ω—ã.")
                            .setNegativeButton("–û—Ç–º–µ–Ω–∞", null)
                            .setPositiveButton("–£–¥–∞–ª–∏—Ç—å", (confirm, choice) -> permanentlyDeleteTrashedProject(selected))
                            .show();
                }).setNegativeButton("–û—Ç–º–µ–Ω–∞", null).show();
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
                    throw new IOException("–ù–µ —É–¥–∞–ª–æ—Å—å —Å–æ—Ö—Ä–∞–Ω–∏—Ç—å —É–¥–∞–ª–µ–Ω–∏–µ –ø—Ä–æ–µ–∫—Ç–∞ –∏ –æ—á–µ—Ä–µ–¥—å –æ—á–∏—Å—Ç–∫–∏");
                }
            }
            stagePendingProjectMediaCleanup();
            schedulePendingProjectMediaCleanup();
            Toast.makeText(this, "–ü—Ä–æ–µ–∫—Ç —É–¥–∞–ª—ë–Ω; –æ—á–∏—Å—Ç–∫–∞ –µ–≥–æ —Ñ–∞–π–ª–æ–≤ –∑–∞–ø—É—â–µ–Ω–∞", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "–ù–µ —É–¥–∞–ª–æ—Å—å —É–¥–∞–ª–∏—Ç—å –ø—Ä–æ–µ–∫—Ç: " + error.getMessage(), Toast.LENGTH_LONG).show();
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
                throw new IOException("–ù–µ —É–¥–∞–ª–æ—Å—å –≤–æ—Å—Å—Ç–∞–Ω–æ–≤–∏—Ç—å –ø—Ä–æ–µ–∫—Ç");
            Toast.makeText(this, "–ü—Ä–æ–µ–∫—Ç –≤–æ—Å—Å—Ç–∞–Ω–æ–≤–ª–µ–Ω", Toast.LENGTH_SHORT).show();
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
        root.addView(heading("BLOODSKY ‚Ä¢ Modbus TCP Scanner"), new LinearLayout.LayoutParams(-1, dp(60)));
        root.addView(action("‚Äπ –ù–∞–∑–∞–¥", this::showHome), new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText hostField = propertyField("192.168.1.10", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        hostField.setHint("IP-–∞–¥—Ä–µ—Å —É—Å—Ç—Ä–æ–π—Å—Ç–≤–∞");
        form.addView(hostField);
        EditText portField = propertyField("502", InputType.TYPE_CLASS_NUMBER);
        portField.setHint("TCP-–ø–æ—Ä—Ç (–æ–±—ã—á–Ω–æ 502)");
        form.addView(portField);
        EditText unitField = propertyField("1", InputType.TYPE_CLASS_NUMBER);
        unitField.setHint("Unit ID (0‚Äì255)");
        form.addView(unitField);
        Spinner functionSpinner = new Spinner(this);
        ArrayAdapter<String> functionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Holding registers (FC03)", "Input registers (FC04)"});
        functionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        functionSpinner.setAdapter(functionAdapter);
        form.addView(functionSpinner);
        EditText addressField = propertyField("40001", InputType.TYPE_CLASS_NUMBER);
        addressField.setHint("–ê–¥—Ä–µ—Å —Ä–µ–≥–∏—Å—Ç—Ä–∞: FC03 –æ–±—ã—á–Ω–æ 40001, FC04 –æ–±—ã—á–Ω–æ 30001");
        form.addView(addressField);
        EditText countField = propertyField("10", InputType.TYPE_CLASS_NUMBER);
        countField.setHint("–ö–æ–ª–∏—á–µ—Å—Ç–≤–æ —Ä–µ–≥–∏—Å—Ç—Ä–æ–≤ (1‚Äì125)");
        form.addView(countField);

        TextView result = new TextView(this);
        result.setTextColor(Color.rgb(31, 41, 55));
        result.setTextSize(14);
        result.setPadding(dp(16), dp(10), dp(16), dp(10));
        result.setText("–ì–æ—Ç–æ–≤ –∫ —á—Ç–µ–Ω–∏—é holding registers (FC03). –°–∫–∞–Ω–∏—Ä–æ–≤–∞–Ω–∏–µ –≤—ã–ø–æ–ª–Ω—è–µ—Ç—Å—è –ø–æ —Å–µ—Ç–∏; —É—Å—Ç—Ä–æ–π—Å—Ç–≤–æ –¥–æ–ª–∂–Ω–æ –±—ã—Ç—å –¥–æ—Å—Ç—É–ø–Ω–æ.");
        root.addView(form, new LinearLayout.LayoutParams(-1, -2));
        root.addView(action("–°—á–∏—Ç–∞—Ç—å —Ä–µ–≥–∏—Å—Ç—Ä—ã", () -> {
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
                    throw new IllegalArgumentException("–ê–¥—Ä–µ—Å –Ω–µ —Å–æ–æ—Ç–≤–µ—Ç—Å—Ç–≤—É–µ—Ç –≤—ã–±—Ä–∞–Ω–Ω–æ–º—É —Ç–∏–ø—É —Ä–µ–≥–∏—Å—Ç—Ä–∞");
                } else {
                    startAddress = enteredAddress;
                }
                if (startAddress < 0 || startAddress > 65535 || startAddress + count > 65536) throw new IllegalArgumentException();
                if (host.isEmpty()) throw new IllegalArgumentException();
            } catch (Exception ex) {
                String message = ex.getMessage();
                result.setText(message == null || message.trim().isEmpty()
                        ? "–ü—Ä–æ–≤–µ—Ä—å IP, –ø–æ—Ä—Ç, Unit ID, –∞–¥—Ä–µ—Å –∏ –∫–æ–ª–∏—á–µ—Å—Ç–≤–æ —Ä–µ–≥–∏—Å—Ç—Ä–æ–≤."
                        : "–ü–∞—Ä–∞–º–µ—Ç—Ä—ã –∑–∞–ø—Ä–æ—Å–∞: " + message);
                return;
            }
            result.setText("–ü–æ–¥–∫–ª—é—á–∞—é—Å—å –∫ " + host + ":" + port + "‚Ä¶");
            new Thread(() -> {
                String report;
                try {
                    report = readModbusRegisters(host, port, unit, startAddress, count, functionCode);
                } catch (Exception ex) {
                    report = "–û—à–∏–±–∫–∞ Modbus TCP: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
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
        root.addView(heading("BLOODSKY ‚Ä¢ USB Serial RTU"), new LinearLayout.LayoutParams(-1, dp(60)));
        root.addView(action("‚Äπ –ù–∞–∑–∞–¥", this::showHome), new LinearLayout.LayoutParams(-1, dp(48)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(12));
        scroll.addView(form);
        TextView intro = new TextView(this);
        intro.setText("–í—ã–±–µ—Ä–∏ USB-–∞–¥–∞–ø—Ç–µ—Ä –∏ –Ω–∞–∂–º–∏ ¬´–ü–æ–¥–∫–ª—é—á–∏—Ç—å¬ª. –†–∞–∑—Ä–µ—à–µ–Ω–∏–µ Android –∑–∞–ø—Ä–∞—à–∏–≤–∞–µ—Ç—Å—è –¥–ª—è –≤—ã–±—Ä–∞–Ω–Ω–æ–≥–æ —É—Å—Ç—Ä–æ–π—Å—Ç–≤–∞.");
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
        EditText unitField = propertyField("1", InputType.TYPE_CLASS_NUMBER); unitField.setHint("Unit ID (0‚Äì247)"); form.addView(unitField);
        EditText addressField = propertyField("0", InputType.TYPE_CLASS_NUMBER); addressField.setHint("–ù–∞—á–∞–ª—å–Ω—ã–π –∞–¥—Ä–µ—Å —Ä–µ–≥–∏—Å—Ç—Ä–∞ (0‚Äì65535)"); form.addView(addressField);
        EditText countField = propertyField("10", InputType.TYPE_CLASS_NUMBER); countField.setHint("–ö–æ–ª–∏—á–µ—Å—Ç–≤–æ —Ä–µ–≥–∏—Å—Ç—Ä–æ–≤ (1‚Äì125)"); form.addView(countField);
        Spinner functionSpinner = new Spinner(this);
        functionSpinner.setAdapter(simpleSpinnerAdapter(new String[]{"Holding registers (FC03)", "Input registers (FC04)"})); form.addView(functionSpinner);
        TextView result = new TextView(this); result.setTextColor(Color.rgb(31, 41, 55)); result.setTextSize(14);
        result.setPadding(0, dp(10), 0, dp(10)); result.setText("USB-–∞–¥–∞–ø—Ç–µ—Ä—ã –Ω–µ –≤—ã–±—Ä–∞–Ω—ã.");
        Runnable refreshDevices = () -> {
            try {
                if (manualUsbConnector == null) manualUsbConnector = new UsbSerialConnector(this);
                devices.clear(); devices.addAll(manualUsbConnector.listDevices()); deviceAdapter.clear();
                for (UsbSerialConnector.DeviceOption option : devices)
                    deviceAdapter.add(option.id + " ‚Ä¢ " + String.format(java.util.Locale.US, "%04X:%04X", option.vendorId, option.productId));
                deviceAdapter.notifyDataSetChanged();
                deviceInfo.setText(devices.isEmpty() ? "–ü–æ–¥–¥–µ—Ä–∂–∏–≤–∞–µ–º—ã–µ USB serial-–∞–¥–∞–ø—Ç–µ—Ä—ã –Ω–µ –Ω–∞–π–¥–µ–Ω—ã." : "–ù–∞–π–¥–µ–Ω–æ –∞–¥–∞–ø—Ç–µ—Ä–æ–≤: " + devices.size());
                result.setText(devices.isEmpty() ? "–ü–æ–¥–∫–ª—é—á–∏ USB serial-–∞–¥–∞–ø—Ç–µ—Ä –∏ –æ–±–Ω–æ–≤–∏ —Å–ø–∏—Å–æ–∫." : "–í—ã–±–µ—Ä–∏ —É—Å—Ç—Ä–æ–π—Å—Ç–≤–æ, –ø–∞—Ä–∞–º–µ—Ç—Ä—ã –ª–∏–Ω–∏–∏ –∏ –Ω–∞–∂–º–∏ ¬´–ü–æ–¥–∫–ª—é—á–∏—Ç—å¬ª.");
            } catch (Exception error) { result.setText("–û—à–∏–±–∫–∞ –ø–æ–∏—Å–∫–∞ USB-–∞–¥–∞–ø—Ç–µ—Ä–æ–≤: " + errorMessage(error)); }
        };
        form.addView(action("–û–±–Ω–æ–≤–∏—Ç—å —Å–ø–∏—Å–æ–∫ USB", refreshDevices), new LinearLayout.LayoutParams(-1, dp(48)));
        form.addView(action("–ü–æ–¥–∫–ª—é—á–∏—Ç—å –≤—ã–±—Ä–∞–Ω–Ω—ã–π –∞–¥–∞–ø—Ç–µ—Ä", () -> {
            if (devices.isEmpty() || deviceSpinner.getSelectedItemPosition() < 0) { result.setText("–°–Ω–∞—á–∞–ª–∞ –æ–±–Ω–æ–≤–∏ —Å–ø–∏—Å–æ–∫ –∏ –≤—ã–±–µ—Ä–∏ –∞–¥–∞–ø—Ç–µ—Ä."); return; }
            final UsbSerialConnector.DeviceOption selected = devices.get(deviceSpinner.getSelectedItemPosition());
            final int baud = Integer.parseInt(baudChoices[baudSpinner.getSelectedItemPosition()]);
            final String framing = parityChoices[paritySpinner.getSelectedItemPosition()];
            UsbSerialSettings.Parity parity = framing.charAt(1) == 'E' ? UsbSerialSettings.Parity.EVEN : framing.charAt(1) == 'O' ? UsbSerialSettings.Parity.ODD : UsbSerialSettings.Parity.NONE;
            final int stopBits = framing.endsWith("2") ? 2 : 1;
            result.setText("–û—Ç–∫—Ä—ã–≤–∞—é " + selected.id + ". –ü–æ–¥—Ç–≤–µ—Ä–¥–∏ –¥–æ—Å—Ç—É–ø –≤ –¥–∏–∞–ª–æ–≥–µ Android, –µ—Å–ª–∏ –æ–Ω –ø–æ—è–≤–∏—Ç—Å—è‚Ä¶");
            modbusExecutor.execute(() -> {
                try {
                    UsbSerialModbusConnection opened = manualUsbConnector.connect(selected.id, new UsbSerialSettings(baud, 8, stopBits, parity), 1500).get();
                    runOnUiThread(() -> {
                        if (screenGeneration != manualUsbScreenGeneration) { try { opened.close(); } catch (IOException ignored) { } return; }
                        closeManualUsbConnection(); manualUsbConnection = opened;
                        if (modbusPollGateway != null) modbusPollGateway.setSelectedUsbConnection(opened);
                        result.setText("–ü–æ–¥–∫–ª—é—á–µ–Ω–æ: " + selected.id + " ‚Ä¢ " + baud + " " + framing + ". –ó–∞–ø—Ä–æ—Å—ã –≤—ã–ø–æ–ª–Ω—è—é—Ç—Å—è —Ç–æ–ª—å–∫–æ –ø–æ –∫–Ω–æ–ø–∫–µ.");
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> { if (screenGeneration == manualUsbScreenGeneration) result.setText("–ù–µ —É–¥–∞–ª–æ—Å—å –ø–æ–¥–∫–ª—é—á–∏—Ç—å USB-–∞–¥–∞–ø—Ç–µ—Ä: " + errorMessage(error)); });
                }
            });
        }), new LinearLayout.LayoutParams(-1, dp(48)));
        form.addView(action("–û—Ç–∫–ª—é—á–∏—Ç—å", () -> { closeManualUsbConnection(); result.setText("USB-–∞–¥–∞–ø—Ç–µ—Ä –æ—Ç–∫–ª—é—á—ë–Ω."); }), new LinearLayout.LayoutParams(-1, dp(48)));
        form.addView(action("–°—á–∏—Ç–∞—Ç—å —Ä–µ–≥–∏—Å—Ç—Ä—ã", () -> {
            final UsbSerialModbusConnection connection = manualUsbConnection;
            if (connection == null) { result.setText("–°–Ω–∞—á–∞–ª–∞ –ø–æ–¥–∫–ª—é—á–∏ USB-–∞–¥–∞–ø—Ç–µ—Ä."); return; }
            final int unit, address, count;
            try {
                unit = Integer.parseInt(unitField.getText().toString().trim()); address = Integer.parseInt(addressField.getText().toString().trim());
                count = Integer.parseInt(countField.getText().toString().trim());
                if (unit < 0 || unit > 247 || address < 0 || address > 65535 || count < 1 || count > 125 || address + count > 65536) throw new IllegalArgumentException();
            } catch (Exception invalid) { result.setText("–ü—Ä–æ–≤–µ—Ä—å Unit ID, –∞–¥—Ä–µ—Å –∏ –∫–æ–ª–∏—á–µ—Å—Ç–≤–æ —Ä–µ–≥–∏—Å—Ç—Ä–æ–≤."); return; }
            final int function = functionSpinner.getSelectedItemPosition() == 0 ? 3 : 4;
            result.setText("–ß–∏—Ç–∞—é —Ä–µ–≥–∏—Å—Ç—Ä—ã –ø–æ Modbus RTU‚Ä¶");
            modbusExecutor.execute(() -> {                try {
                    int[] values = connection.read(unit, function, address, count);
                    StringBuilder report = new StringBuilder("FC").append(function).append(" ‚Ä¢ –∞–¥—Ä–µ—Å ").append(address).append("\n");
                    for (int i = 0; i < values.length; i++) report.append(address + i).append(": ").append(values[i]).append(i + 1 == values.length ? "" : "\n");
                    runOnUiThread(() -> result.setText(report.toString()));
                } catch (Exception error) {
                    runOnUiThread(() -> { if (manualUsbConnection == connection && !connection.isOpen()) manualUsbConnection = null; result.setText("–û—à–∏–±–∫–∞ Modbus RTU: " + errorMessage(error)); });
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
                throw new IOException("Modbus RTU: " + describeModbusExceptionCodeStatic(exceptionCode) + " (–∫–æ–¥ " + exceptionCode + ")");
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
        report.append("–£—Å–ø–µ—à–Ω–æ: ").append(count).append(" ").append(funcName);
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
                + " (–∫–æ–¥ " + ex.exceptionCode + ")", ex);
    }

    private String readModbusRegisters(String host, int port, int unit, int startAddress, int count, int functionCode) throws Exception {
        return readModbusRegistersStatic(host, port, unit, startAddress, count, functionCode);
    }

    public static String describeModbusExceptionCodeStatic(int code) {
        switch (code) {
            case 1: return "—É—Å—Ç—Ä–æ–π—Å—Ç–≤–æ –Ω–µ –ø–æ–¥–¥–µ—Ä–∂–∏–≤–∞–µ—Ç —ç—Ç—É —Ñ—É–Ω–∫—Ü–∏—é";
            case 2: return "–Ω–µ–¥–æ–ø—É—Å—Ç–∏–º—ã–π –∞–¥—Ä–µ—Å —Ä–µ–≥–∏—Å—Ç—Ä–∞";
            case 3: return "–Ω–µ–¥–æ–ø—É—Å—Ç–∏–º–æ–µ –∑–Ω–∞—á–µ–Ω–∏–µ –≤ –∑–∞–ø—Ä–æ—Å–µ";
            case 4: return "–≤–Ω—É—Ç—Ä–µ–Ω–Ω—è—è –æ—à–∏–±–∫–∞ —É—Å—Ç—Ä–æ–π—Å—Ç–≤–∞";
            case 5: return "–∑–∞–ø—Ä–æ—Å –ø—Ä–∏–Ω—è—Ç, –æ–±—Ä–∞–±–æ—Ç–∫–∞ –≤—ã–ø–æ–ª–Ω—è–µ—Ç—Å—è";
            case 6: return "—É—Å—Ç—Ä–æ–π—Å—Ç–≤–æ –∑–∞–Ω—è—Ç–æ, –ø–æ–≤—Ç–æ—Ä–∏—Ç–µ –ø–æ–∑–∂–µ";
            case 8: return "–æ—à–∏–±–∫–∞ —á—ë—Ç–Ω–æ—Å—Ç–∏ –ø–∞–º—è—Ç–∏";
            case 10: return "—à–ª—é–∑ –Ω–µ —Å–º–æ–≥ —É—Å—Ç–∞–Ω–æ–≤–∏—Ç—å —Å–æ–µ–¥–∏–Ω–µ–Ω–∏–µ";
            case 11: return "—à–ª—é–∑ –Ω–µ –ø–æ–ª—É—á–∏–ª –æ—Ç–≤–µ—Ç –æ—Ç —É—Å—Ç—Ä–æ–π—Å—Ç–≤–∞";
            default: return "–Ω–µ–∏–∑–≤–µ—Å—Ç–Ω–æ–µ –∏—Å–∫–ª—é—á–µ–Ω–∏–µ –ø—Ä–æ—Ç–æ–∫–æ–ª–∞";
        }
    }

    private String describeModbusExceptionCode(int code) {
        return describeModbusExceptionCodeStatic(code);
    }

    private String describeModbusError(Exception error) {
        if (error instanceof java.net.SocketTimeoutException) return "—Ç–∞–π–º-–∞—É—Ç –æ—Ç–≤–µ—Ç–∞ (3 —Å)";
        if (error instanceof java.net.ConnectException) return "–Ω–µ —É–¥–∞–ª–æ—Å—å –ø–æ–¥–∫–ª—é—á–∏—Ç—å—Å—è –∫ —É—Å—Ç—Ä–æ–π—Å—Ç–≤—É";
        if (error instanceof java.net.UnknownHostException) return "–Ω–µ —É–¥–∞–ª–æ—Å—å –æ–ø—Ä–µ–¥–µ–ª–∏—Ç—å –∞–¥—Ä–µ—Å —Ö–æ—Å—Ç–∞";
        if (error instanceof java.net.NoRouteToHostException) return "–Ω–µ—Ç –º–∞—Ä—à—Ä—É—Ç–∞ –¥–æ —É—Å—Ç—Ä–æ–π—Å—Ç–≤–∞";
        if (error instanceof java.net.SocketException) return "–æ—à–∏–±–∫–∞ —Å–æ–∫–µ—Ç–∞/—Å–æ–µ–¥–∏–Ω–µ–Ω–∏–µ –∑–∞–∫—Ä—ã—Ç–æ";
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
        return message.length() > 90 ? message.substring(0, 90) : message;
    }

    private boolean isActiveModbusValue(String value) {
        return CraftModbusActiveValue.isActive(value);
    }

    private BindingRequest parseBinding(int widgetIndex, String binding) {
        return parseBinding(widgetIndex, binding, 1);
    }

    private void startFbdSensors() {
        if (!activityResumed || !operatorMode || editor == null) return;
        boolean neededLight = false, neededOrientation = false, neededSpeed = false;
        for (Widget widget : fbdRuntimeWidgetsIncludingMacros(widgets)) {
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
        for (Widget widget : fbdRuntimeWidgetsIncludingMacros(widgets)) {
            if (isCraftMqttSubscriber(widget.type)) widget.craftMqttValue = null;
            if (isCraftMqttPublisher(widget.type)) widget.craftMqttLastEnable = false;
        }
    }

    private void startCraftMqtt() {
        stopCraftMqtt();
        if (!activityResumed || !operatorMode || editor == null) return;
        FbdCraftMqttRuntime runtime = new FbdCraftMqttRuntime();
        craftMqttRuntime = runtime;
        final EditorCanvas sessionEditor = editor;
        final List<Widget> runtimeWidgets = fbdRuntimeWidgetsIncludingMacros(widgets);
        for (int server = 1; server <= 2; server++) {
            JSONObject saved;
            try { saved = new JSONObject(getSharedPreferences("bloodsky_mqtt", MODE_PRIVATE)
                    .getString("server" + server, "{}")); }
            catch (Exception invalid) { saved = new JSONObject(); }
            FbdCraftMqttRuntime.Broker broker = new FbdCraftMqttRuntime.Broker(
                    saved.optString("uri", ""), saved.optString("username", ""), saved.optString("password", ""));
            Map<String, FbdCraftMqttRuntime.MessageListener> subscriptions = new LinkedHashMap<>();
            boolean used = false;
            for (Widget target : runtimeWidgets) {
                if (isCraftMqttSubscriber(target.type) && mqttServer(target) == server) {
                    used = true;
                    String topic = target.extraFields.optString("craftMqttTopic", "").trim();
                    int type = mqttType(target);
                    final int targetIndex = widgets.indexOf(target);
                    if (FbdCraftMqttRuntime.validTopicFilter(topic)) {
                        FbdCraftMqttRuntime.MessageListener nextListener = (receivedTopic, payload) -> {
                        Double value = FbdCraftMqttRuntime.parsePayload(payload, type);
                        runOnUiThread(() -> {
                            if (craftMqttRuntime != runtime || !activityResumed || !operatorMode
                                    || editor != sessionEditor
                                    || !fbdRuntimeWidgetsIncludingMacros(widgets).contains(target)) return;
                            target.craftMqttValue = value;
                            if (targetIndex >= 0) liveValues.put(targetIndex,
                                    value == null ? "ERR: MQTT payload" : Double.toString(value));
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
        BluetoothRtuBinding bluetooth = BluetoothRtuBinding.parse(binding, quantity);
        if (bluetooth != null)
            return new BindingRequest(widgetIndex, "", 0, bluetooth.unit, bluetooth.registerAddress,
                    bluetooth.functionCode, bluetooth.quantity, false, bluetooth.address);
        UsbSerialBinding usb = UsbSerialBinding.parse(binding, quantity);
        if (usb != null)
            return new BindingRequest(widgetIndex, "", 0, usb.unit, usb.address,
                    usb.functionCode, usb.quantity, true);
        return parseBinding(widgetIndex, binding, quantity);
    }

    private int[] readBoundRegisters(BindingRequest request) throws IOException {
        ModbusPollGateway gateway = modbusPollGateway;
        if (gateway == null) throw new IOException("Modbus gateway –Ω–µ –∑–∞–ø—É—â–µ–Ω");
        return gateway.read(request.toGatewayEndpoint());
    }

    private void startModbusPolling() {
        stopModbusPolling();
        if (!activityResumed || editor == null) return;
        if (ModbusRtuOperatorPolicy.shouldPrompt(operatorMode, hasExternalRtuBinding(),
                externalRtuConsentResolved)) {
            if (externalRtuConsentPromptPending) return;
            externalRtuConsentPromptPending = true;
            new AlertDialog.Builder(this)
                    .setTitle("–í–Ω–µ—à–Ω–∏–µ –ø–æ–¥–∫–ª—é—á–µ–Ω–∏—è Modbus RTU")
                    .setMessage("–ü—Ä–æ–µ–∫—Ç —Å–æ–¥–µ—Ä–∂–∏—Ç USB –∏–ª–∏ Bluetooth RTU-–ø—Ä–∏–≤—è–∑–∫–∏. –†–∞–∑—Ä–µ—à–∏—Ç—å –∏—Ö –æ–ø—Ä–æ—Å –≤ —ç—Ç–æ–º —Å–µ–∞–Ω—Å–µ –æ–ø–µ—Ä–∞—Ç–æ—Ä–∞? USB-–∞–¥–∞–ø—Ç–µ—Ä –¥–æ–ª–∂–µ–Ω –±—ã—Ç—å –≤—ã–±—Ä–∞–Ω –∏ –ø–æ–¥–∫–ª—é—á—ë–Ω –≤—Ä—É—á–Ω—É—é. Bluetooth –ø–æ–¥–∫–ª—é—á–∞–µ—Ç—Å—è —Ç–æ–ª—å–∫–æ –∫ —É–∫–∞–∑–∞–Ω–Ω–æ–º—É –≤ –ø—Ä–∏–≤—è–∑–∫–µ —Å–æ–ø—Ä—è–∂—ë–Ω–Ω–æ–º—É —É—Å—Ç—Ä–æ–π—Å—Ç–≤—É. –ò–º–ø–æ—Ä—Ç–∏—Ä–æ–≤–∞–Ω–Ω—ã–µ –ø—Ä–∏–≤—è–∑–∫–∏ –±–µ–∑ —ç—Ç–æ–≥–æ —Ä–∞–∑—Ä–µ—à–µ–Ω–∏—è –Ω–µ –æ–ø—Ä–∞—à–∏–≤–∞—é—Ç—Å—è.")
                    .setNegativeButton("–û—Å—Ç–∞–≤–∏—Ç—å –≤—ã–∫–ª—é—á–µ–Ω–Ω—ã–º–∏", (dialog, which) -> resolveExternalRtuConsent(false))
                    .setPositiveButton("–†–∞–∑—Ä–µ—à–∏—Ç—å –Ω–∞ —Å–µ–∞–Ω—Å", (dialog, which) -> resolveExternalRtuConsent(true))
                    .setOnCancelListener(dialog -> resolveExternalRtuConsent(false))
                    .show();
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                == android.content.pm.PackageManager.PERMISSION_GRANTED)
            externalBluetoothPermissionDeniedForSession = false;
        if (operatorMode && hasBluetoothRegisterBinding() && android.os.Build.VERSION.SDK_INT >= 31
                && !externalBluetoothPermissionDeniedForSession
                && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_OPERATOR_BLUETOOTH_PERMISSION);
            return;
        }
        pollSession.start();
        modbusPollingActive = true;
        mainHandler.postDelayed(modbusPollRunnable, 1000L);
    }

    private void resolveExternalRtuConsent(boolean approved) {
        externalRtuConsentPromptPending = false;
        externalRtuConsentResolved = true;
        externalRtuApprovedForSession = approved;
        if (!approved) Toast.makeText(this,
                "USB/Bluetooth Modbus RTU –æ—Å—Ç–∞–≤–ª–µ–Ω –≤—ã–∫–ª—é—á–µ–Ω–Ω—ã–º –¥–ª—è —ç—Ç–æ–≥–æ —Å–µ–∞–Ω—Å–∞",
                Toast.LENGTH_LONG).show();
        startModbusPolling();
    }

    private boolean hasExternalRtuBinding() {
        for (Widget widget : widgets) {
            if (widget == null) continue;
            if (ModbusRtuOperatorPolicy.isRtuBinding(widget.connectionInfo)) return true;
            JSONObject fields = widget.extraFields;
            if (ModbusRtuOperatorPolicy.hasGatewayRtuBinding(
                    fields.optBoolean("craftGatewayEnabled", false),
                    fields.optString("craftGatewaySource", ""),
                    fields.optString("craftGatewayDestination", ""),
                    fields.optString("craftGatewayTrigger", ""))) return true;
            if (ModbusRtuOperatorPolicy.hasGatewayRtuBinding(
                    fields.optBoolean("craftBitGatewayEnabled", false),
                    fields.optString("craftBitGatewaySource", ""),
                    fields.optString("craftBitGatewayDestination", ""),
                    fields.optString("craftBitGatewayTrigger", ""))) return true;
        }
        return false;
    }

    private void requireGatewayBluetoothSessionAccess() throws IOException {
        boolean permissionGranted = android.os.Build.VERSION.SDK_INT < 31
                || checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!ModbusRtuOperatorPolicy.mayAccessBluetoothGateway(operatorMode,
                externalRtuApprovedForSession, permissionGranted
                        && !externalBluetoothPermissionDeniedForSession))
            throw new IOException("Bluetooth-—à–ª—é–∑ —Ç—Ä–µ–±—É–µ—Ç —Ä–∞–∑—Ä–µ—à–µ–Ω–∏—è RTU –Ω–∞ —Ç–µ–∫—É—â–∏–π —Å–µ–∞–Ω—Å");
    }

    private UsbSerialModbusConnection requireGatewayUsbSessionAccess() throws IOException {
        if (!ModbusRtuOperatorPolicy.mayPollRtu("usb://", operatorMode,
                externalRtuApprovedForSession))
            throw new IOException("USB-—à–ª—é–∑ —Ç—Ä–µ–±—É–µ—Ç —Ä–∞–∑—Ä–µ—à–µ–Ω–∏—è RTU –Ω–∞ —Ç–µ–∫—É—â–∏–π —Å–µ–∞–Ω—Å");
        UsbSerialModbusConnection connection = manualUsbConnection;
        if (connection == null || !connection.isOpen())
            throw new IOException("–í—ã–±–µ—Ä–∏—Ç–µ –∏ –ø–æ–¥–∫–ª—é—á–∏—Ç–µ USB-–∞–¥–∞–ø—Ç–µ—Ä –≤ —Ä—É—á–Ω–æ–º —Ä–µ–∂–∏–º–µ RTU");
        return connection;
    }

    private void stopModbusPolling() {
        pollSession.stop();
        modbusPollingActive = false;
        craftGatewayStates.clear();
        craftBitGatewayStates.clear();
        craftArchiveTriggerHigh.clear();
        craftArchiveTriggerEdges.clear();
        closeBluetoothModbusConnections();
        if (modbusPollGateway != null) modbusPollGateway.closeBluetoothConnections();
        mainHandler.removeCallbacks(modbusPollRunnable);
    }

    private boolean hasBluetoothRegisterBinding() {
        for (Widget widget : widgets) {
            if (widget == null) continue;
            if (isBluetoothEndpoint(widget.connectionInfo)) return true;
            if (widget.extraFields.optBoolean("craftGatewayEnabled", false)
                    && (isBluetoothEndpoint(widget.extraFields.optString("craftGatewaySource", ""))
                    || isBluetoothEndpoint(widget.extraFields.optString("craftGatewayDestination", ""))
                    || isBluetoothEndpoint(widget.extraFields.optString("craftGatewayTrigger", "")))) return true;
            if (widget.extraFields.optBoolean("craftBitGatewayEnabled", false)
                    && (isBluetoothEndpoint(widget.extraFields.optString("craftBitGatewaySource", ""))
                    || isBluetoothEndpoint(widget.extraFields.optString("craftBitGatewayDestination", ""))
                    || isBluetoothEndpoint(widget.extraFields.optString("craftBitGatewayTrigger", "")))) return true;
        }
        return false;
    }

    private static boolean isBluetoothEndpoint(String value) {
        return value != null && value.trim().startsWith("bt://");
    }

    private BluetoothModbusConnection bluetoothConnection(String address) throws IOException {
        final long session = bluetoothSessionGeneration;
        BluetoothModbusConnection existing = bluetoothModbusConnections.get(address);
        if (existing != null && existing.isOpen()) return existing;
        if (existing != null) bluetoothModbusConnections.remove(address, existing);
        BluetoothModbusConnection opened = BluetoothModbusConnection.connect(this, address, 3000);
        if (session != bluetoothSessionGeneration || !modbusPollingActive) {
            try { opened.close(); } catch (IOException ignored) { }
            throw new IOException("Bluetooth RTU operator session ended while connecting");
        }
        BluetoothModbusConnection prior = bluetoothModbusConnections.putIfAbsent(address, opened);
        if (prior != null) {
            try { opened.close(); } catch (IOException ignored) { }
            if (prior.isOpen()) return prior;
            bluetoothModbusConnections.replace(address, prior, opened);
        }
        return opened;
    }

    private void closeBluetoothModbusConnections() {
        bluetoothSessionGeneration++;
        for (java.util.Map.Entry<String, BluetoothModbusConnection> entry
                : bluetoothModbusConnections.entrySet()) {
            BluetoothModbusConnection connection = entry.getValue();
            if (bluetoothModbusConnections.remove(entry.getKey(), connection)) {
                try { connection.close(); }
                catch (IOException failure) { android.util.Log.w("BLOODSKY", "Bluetooth RTU close failed", failure); }
            }
        }
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
        if ("–ì—Ä–∞—Ñ–∏–∫ / –¢—Ä–µ–Ω–¥".equals(widget.type)
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
        if ("–ê—Ä—Ö–∏–≤ Craft".equals(widget.type)) return CraftArchiveChannels.decode(words,
                widget.extraFields.optInt("craftArchiveChannelCount", 1),
                widget.extraFields.optString("craftArchiveRecordType", "INT"));
        if (OriginalCraftNumericIndicator.TYPE.equals(widget.type)) {
            int sourceType = widget.extraFields.optInt("craftNumericIndicatorType", 0);
            return OriginalCraftNumericIndicator.format(OriginalCraftNumericIndicator.decode(words, sourceType),
                    widget.extraFields.optInt("craftNumericIndicatorDigits", 5),
                    widget.extraFields.optInt("craftNumericIndicatorDecimals", 0),
                    sourceType);
        }
        return words.length > 1 ? words[0] + "," + words[1] : Integer.toString(words[0]);
    }

    private void pollCraftOdTempSources() {
        if (!activityResumed || !operatorMode || editor == null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        final EditorCanvas sessionEditor = editor;
        for (Widget widget : fbdRuntimeWidgetsIncludingMacros(widgets)) {
            int index = widgets.indexOf(widget);
            if (!isCraftOdTempSource(widget.type) || widget.craftOdTempPending
                    || now < widget.craftOdTempNextPollAt) continue;
            String host = widget.extraFields.optString("ip", "10.10.0.1").trim();
            int sensorType = widget.extraFields.optInt("craftOdTempType", widget.extraFields.optInt("typeSensor", 1));
            int responseType = widget.extraFields.optInt("craftOdTempResponse", widget.extraFields.optInt("typeResponse", 1));
            if (!isAllowedOdTempHost(host) || (sensorType != 1 && sensorType != 2)
                    || responseType < 1 || responseType > 2) {
                widget.craftSensorValid = false; widget.craftOdTempNextPollAt = now + 5000L;
                if (index >= 0) liveValues.put(index, "CFG");
                continue;
            }
            widget.craftOdTempPending = true; widget.craftOdTempNextPollAt = now + 5000L;
            final Widget target = widget; final int topLevelIndex = index;
            final int requestedType = sensorType, requestedResponse = responseType;
            try {
                craftSensorExecutor.execute(() -> {
                    Double reading = null; String error = null;
                    try { reading = readCraftOdTemp(host, requestedType, requestedResponse); }
                    catch (Exception failure) { error = "ERR: " + describeModbusError(failure); }
                    final Double result = reading; final String failureText = error;
                    runOnUiThread(() -> {
                        target.craftOdTempPending = false;
                        if (!fbdRuntimeWidgetsIncludingMacros(widgets).contains(target) || !operatorMode
                                || !activityResumed || editor != sessionEditor) return;
                        target.craftOdTempNextPollAt = android.os.SystemClock.elapsedRealtime() + 5000L;
                        target.craftSensorValid = result != null && Double.isFinite(result);
                        if (target.craftSensorValid) {
                            target.craftSensorValue = result;
                            if (topLevelIndex >= 0) liveValues.put(topLevelIndex, Double.toString(result));
                        } else if (topLevelIndex >= 0) liveValues.put(topLevelIndex,
                                failureText == null ? "ERR: –æ—Ç–≤–µ—Ç –¥–∞—Ç—á–∏–∫–∞" : failureText);
                        if (editor != null) editor.invalidate();
                    });
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) { widget.craftOdTempPending = false; }
        }
    }

    private static double readCraftOdTemp(String host, int sensorType, int responseType) throws Exception {
        if (!isAllowedOdTempHost(host)) throw new IOException("–ù–µ–¥–æ–ø—É—Å—Ç–∏–º—ã–π –∞–¥—Ä–µ—Å ODTEMP");
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
                    if (body.size() + count > 65536) throw new IOException("–û—Ç–≤–µ—Ç ODTEMP —Å–ª–∏—à–∫–æ–º –±–æ–ª—å—à–æ–π");
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
            if (raw == null) throw new IOException("–í –æ—Ç–≤–µ—Ç–µ ODTEMP –Ω–µ—Ç –∑–Ω–∞—á–µ–Ω–∏—è");
            double value = raw instanceof Number ? ((Number)raw).doubleValue() : Double.parseDouble(String.valueOf(raw));
            if (!Double.isFinite(value)) throw new IOException("ODTEMP –≤–µ—Ä–Ω—É–ª–æ –Ω–µ–∫–æ–Ω–µ—á–Ω–æ–µ –∑–Ω–∞—á–µ–Ω–∏–µ");
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
            if ("–®–ª—é–∑ –±–∏—Ç–æ–≤—ã–π".equals(widget.type)) {
                if (ModbusRtuOperatorPolicy.mayRunGateway(
                        snapshotExtras.get(i).optBoolean("craftBitGatewayEnabled", false), operatorMode))
                    bitGatewayIndices.add(i);
                continue;
            }
            if ("–®–ª—é–∑ Craft".equals(widget.type)) {
                if (ModbusRtuOperatorPolicy.mayRunGateway(
                        snapshotExtras.get(i).optBoolean("craftGatewayEnabled", false), operatorMode))
                    gatewayIndices.add(i);
                continue;
            }
            if ("–ê—Ä—Ö–∏–≤ Craft".equals(widget.type)
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
            if ("–ì—Ä–∞—Ñ–∏–∫ / –¢—Ä–µ–Ω–¥".equals(widget.type)
                    && widget.extraFields.optInt("craftComponentId", -1) == 14)
                quantity = trendReadQuantity(widget);
            if (OriginalCraftNumericIndicator.TYPE.equals(widget.type)) {
                int sourceType = widget.extraFields.optInt("craftNumericIndicatorType", 0);
                if (!OriginalCraftNumericIndicator.isSupportedSourceType(sourceType)) continue;
                quantity = OriginalCraftNumericIndicator.readQuantity(sourceType);
            }
            if ("–ê—Ä—Ö–∏–≤ Craft".equals(widget.type)) quantity = archiveReadQuantity(widget);
            boolean rtuBinding = ModbusRtuOperatorPolicy.isRtuBinding(widget.connectionInfo);
            if (rtuBinding && !ModbusRtuOperatorPolicy.mayPollRtu(widget.connectionInfo,
                    operatorMode, externalRtuApprovedForSession)) continue;
            if (isBluetoothEndpoint(widget.connectionInfo) && externalBluetoothPermissionDeniedForSession) continue;
            boolean projectRtuBindingAllowed = rtuBinding || craftSource || "FBD –í—Ö–æ–¥".equals(widget.type);
            BindingRequest request = projectRtuBindingAllowed
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
            boolean rtuBinding = ModbusRtuOperatorPolicy.isRtuBinding(binding);
            boolean numericIndicator = OriginalCraftNumericIndicator.TYPE.equals(snapshotWidgets.get(i).type);
            int numericIndicatorType = numericIndicator
                    ? snapshotWidgets.get(i).extraFields.optInt("craftNumericIndicatorType", 0) : 0;
            int numericIndicatorQuantity = numericIndicator
                    && OriginalCraftNumericIndicator.isSupportedSourceType(numericIndicatorType)
                    ? OriginalCraftNumericIndicator.readQuantity(numericIndicatorType) : 1;
            if ("–ì—Ä–∞—Ñ–∏–∫ / –¢—Ä–µ–Ω–¥".equals(snapshotWidgets.get(i).type)
                    && snapshotWidgets.get(i).extraFields.optInt("craftComponentId", -1) == 14)
                numericIndicatorQuantity = trendReadQuantity(snapshotWidgets.get(i));
            if (numericIndicator && !OriginalCraftNumericIndicator.isSupportedSourceType(numericIndicatorType))
                invalidBindings.put(i, "CFG ‚Äî –Ω–µ–∏–∑–≤–µ—Å—Ç–Ω—ã–π —Ñ–æ—Ä–º–∞—Ç Craft");
            else if (binding != null && binding.startsWith("tcp://")
                    && parseBinding(i, binding, numericIndicatorQuantity) == null)
                invalidBindings.put(i, "CFG");
            else if (rtuBinding && !ModbusRtuOperatorPolicy.mayPollRtu(binding,
                    operatorMode, externalRtuApprovedForSession))
                invalidBindings.put(i, "ERR: –í–Ω–µ—à–Ω–∏–π Modbus RTU –Ω–µ —Ä–∞–∑—Ä–µ—à—ë–Ω");
            else if (isBluetoothEndpoint(binding) && externalBluetoothPermissionDeniedForSession)
                invalidBindings.put(i, "ERR: –Ω–µ—Ç —Ä–∞–∑—Ä–µ—à–µ–Ω–∏—è BLUETOOTH_CONNECT");
            else if (rtuBinding && parseProjectBinding(i, binding, numericIndicatorQuantity) == null)
                invalidBindings.put(i, "CFG ‚Äî –Ω–µ–≤–µ—Ä–Ω–∞—è USB/Bluetooth RTU-–ø—Ä–∏–≤—è–∑–∫–∞");
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
                                        if (!pollSession.accepts(sessionToken)) throw new IOException("–°–µ–∞–Ω—Å –ø—Ä–æ–µ–∫—Ç–∞ –∑–∞–≤–µ—Ä—à—ë–Ω");
                                        if (binding.systemRegister)
                                            return CraftRegisterGateway.readSystemSource(binding, quantity, systemRegisterBank);
                                        if (binding.usb) return requireGatewayUsbSessionAccess().read(
                                                binding.unit, binding.readFunction, binding.address, quantity);
                                        if (binding.bluetoothAddress != null) {
                                            requireGatewayBluetoothSessionAccess();
                                            return bluetoothConnection(binding.bluetoothAddress).read(binding.unit,
                                                    binding.readFunction, binding.address, quantity);
                                        }
                                        return new ModbusTcpClient(binding.host, binding.port).read(
                                                binding.unit, binding.address, quantity, binding.readFunction);
                                    }
                                    @Override public void write(CraftRegisterGateway.Binding binding, int[] values) throws IOException {
                                        if (!pollSession.accepts(sessionToken) || !operatorMode || !activityResumed)
                                            throw new IOException("–®–ª—é–∑ –æ—Å—Ç–∞–Ω–æ–≤–ª–µ–Ω");
                                        if (binding.systemRegister) {
                                            CraftRegisterGateway.writeSystemDestination(
                                                    binding, values, systemRegisterBank);
                                            return;
                                        }
                                        if (binding.usb) {
                                            requireGatewayUsbSessionAccess().writeMultiple(
                                                    binding.unit, binding.address, values);
                                            return;
                                        }
                                        if (binding.bluetoothAddress != null) {
                                            requireGatewayBluetoothSessionAccess();
                                            bluetoothConnection(binding.bluetoothAddress).writeMultiple(
                                                    binding.unit, binding.address, values);
                                            return;
                                        }
                                        new ModbusTcpClient(binding.host, binding.port).writeMultiple(
                                                binding.unit, binding.address, values);
                                    }
                                });
                        updates.put(widgetIndex, outcome.transferred
                                ? "OK: –ø–µ—Ä–µ–¥–∞–Ω–æ " + outcome.count : "–û–∂–∏–¥–∞–Ω–∏–µ —Å–æ–±—ã—Ç–∏—è");
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
                                        if (!pollSession.accepts(sessionToken)) throw new IOException("–°–µ–∞–Ω—Å –ø—Ä–æ–µ–∫—Ç–∞ –∑–∞–≤–µ—Ä—à—ë–Ω");
                                        if (binding.systemRegister) return CraftBitGateway.readSystemSource(
                                                binding, quantity, systemRegisterBank);
                                        if (binding.usb) return requireGatewayUsbSessionAccess().read(
                                                binding.unit, binding.function, binding.address, quantity);
                                        if (binding.bluetoothAddress != null) {
                                            requireGatewayBluetoothSessionAccess();
                                            return bluetoothConnection(binding.bluetoothAddress).read(binding.unit,
                                                    binding.function, binding.address, quantity);
                                        }
                                        return new ModbusTcpClient(binding.host, binding.port).read(
                                                binding.unit, binding.address, quantity, binding.function);
                                    }
                                    @Override public void write(CraftBitGateway.Binding binding, int[] values) throws IOException {
                                        if (!pollSession.accepts(sessionToken) || !operatorMode || !activityResumed)
                                            throw new IOException("–®–ª—é–∑ –æ—Å—Ç–∞–Ω–æ–≤–ª–µ–Ω");
                                        if (binding.systemRegister) {
                                            CraftBitGateway.writeSystemDestination(
                                                    binding, values, systemRegisterBank);
                                            return;
                                        }
                                        if (binding.usb) {
                                            UsbSerialModbusConnection connection = requireGatewayUsbSessionAccess();
                                            if (values.length == 1) connection.writeSingleCoil(
                                                    binding.unit, binding.address, values[0] == 1);
                                            else connection.writeMultipleCoils(binding.unit, binding.address, values);
                                            return;
                                        }
                                        if (binding.bluetoothAddress != null) {
                                            requireGatewayBluetoothSessionAccess();
                                            BluetoothModbusConnection connection = bluetoothConnection(binding.bluetoothAddress);
                                            if (values.length == 1) connection.writeSingleCoil(
                                                    binding.unit, binding.address, values[0] == 1);
                                            else connection.writeMultipleCoils(binding.unit, binding.address, values);
                                            return;
                                        }
                                        ModbusTcpClient client = new ModbusTcpClient(binding.host, binding.port);
                                        if (values.length == 1) client.writeSingleCoil(binding.unit, binding.address, values[0] == 1);
                                        else client.writeMultipleCoils(binding.unit, binding.address, values);
                                    }
                                });
                        updates.put(widgetIndex, outcome.transferred
                                ? "OK: –ø–µ—Ä–µ–¥–∞–Ω–æ –±–∏—Ç " + outcome.count : "–û–∂–∏–¥–∞–Ω–∏–µ —Å–æ–±—ã—Ç–∏—è");
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
                        if (trigger == null) throw new IOException("–ù–µ–∫–æ—Ä—Ä–µ–∫—Ç–Ω–∞—è –ø—Ä–∏–≤—è–∑–∫–∞ —Ç—Ä–∏–≥–≥–µ—Ä–∞ –∞—Ä—Ö–∏–≤–∞");
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
                        if (operatorMode && "–ì—Ä–∞—Ñ–∏–∫ / –¢—Ä–µ–Ω–¥".equals(w.type))
                            recordTrendSample(w, entry.getValue());
                        if ("–ê—Ä—Ö–∏–≤ Craft".equals(w.type) && operatorMode && entry.getValue() != null
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
                        if ("FBD –í—Ö–æ–¥".equals(w.type)) {
                            w.inputValue = isActiveModbusValue(entry.getValue());
                        } else if ("–ó–≤—É–∫–æ–≤–æ–π —Å–∏–≥–Ω–∞–ª".equals(w.type)) {
                            // A malformed or disconnected source must not trigger a remembered
                            // locally configured alarm or latch the previous remote state.
                            Boolean measuredActive = CraftAlarmSignal.active(entry.getValue(), w.soundThreshold);
                            boolean active = Boolean.TRUE.equals(measuredActive);
                            String alarmKey = currentScreenIndex + ":" + index;
                            if (active && !w.lastSoundState) {
                                w.lastSoundState = true;
                                playCraftAlarmAlert(alarmKey, w.soundFilePath, w.craftAlarmSoundIndex, w.craftAlarmLoop);
                            } else if (!active) {
                                w.lastSoundState = false;
                                stopCraftAlarmAlert(alarmKey);
                            }
                        } else if ("–ó–≤—É–∫–æ–≤–æ–π —Å–∏–≥–Ω–∞–ª Craft".equals(w.type)) {
                            boolean soundEnabled = w.extraFields.optBoolean("craftSoundEnabled", false);
                            int activeMask = soundEnabled ? CraftSoundBank.activeMask(entry.getValue()) : 0;
                            String soundKey = currentScreenIndex + ":" + index + ":craftSound";
                            w.craftSoundActiveMask = activeMask;
                            if (!soundEnabled) {
                                w.craftSoundPlayedMask = 0;
                                stopCraftAlarmAlert(soundKey);
                            } else if (!craftAlarmPlayers.containsKey(soundKey)) {
                                // The original widget only inspects bit edges while its player is idle.
                                w.craftSoundPlayedMask = CraftSoundBank.clearInactive(w.craftSoundPlayedMask, activeMask);
                                if (activeMask != 0) {
                                    int channel = CraftSoundBank.nextPendingChannel(activeMask, w.craftSoundPlayedMask);
                                    if (channel >= 0) {
                                        w.craftSoundPlayedMask |= 1 << channel;
                                        String path = CraftSoundBank.pathAt(w.extraFields.optJSONArray("craftSoundPaths"), channel);
                                        if (!path.isEmpty()) playCraftAlarmAlert(soundKey, path, -1, false);
                                    }
                                }
                            }
                        } else if ("–ì–æ–ª–æ—Å–æ–≤–æ–µ —Å–æ–æ–±—â–µ–Ω–∏–µ".equals(w.type)) {
                            int voiceIdx = CraftHmiStateSignal.decode(entry.getValue());
                            // An invalid or lost signal is not state zero, and
                            // must never enqueue speech. Keep the last valid
                            // state so a reconnect does not replay the same message.
                            if (voiceIdx >= 0 && voiceIdx != w.lastVoiceState) {
                                w.lastVoiceState = voiceIdx;
                                speakVoiceAnnouncement(w.voiceMessages[voiceIdx]);
                            }
                        } else if ("Telegram-—É–≤–µ–¥–æ–º–ª–µ–Ω–∏–µ".equals(w.type)) {
                            String state = entry.getValue();
                            if (state != null && !state.startsWith("ERR:") && !state.startsWith("CFG")) {
                                boolean active = isActiveModbusValue(state);
                                if (active && !w.lastTelegramState) sendTelegramNotification(index, w, state);
                                w.lastTelegramState = active;
                            }
                        } else if ("–£–≤–µ–¥–æ–º–ª–µ–Ω–∏–µ Craft".equals(w.type)) {
                            String state = entry.getValue();
                            boolean active = state != null && !state.startsWith("ERR:") && !state.startsWith("CFG")
                                    && isActiveModbusValue(state);
                            boolean enabled = w.extraFields.optBoolean("craftPushEnabled", false);
                            if (enabled && active && !w.lastCraftPushState)
                                sendCraftLocalNotification(currentScreenIndex, index, w);
                            w.lastCraftPushState = active;
                        } else if ("SMS-—É–≤–µ–¥–æ–º–ª–µ–Ω–∏–µ".equals(w.type)) {
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
        int channels = Math.max(1, Math.min(4,
                widget.extraFields.optInt("craftTrendChannelCount", 1)));
        // A truncated poll must never reconfigure the number of channels and
        // discard previously collected trends. Per-channel unavailable values
        // are skipped, while valid sibling channels can still be sampled.
        double[] values = CraftTrendSampleFrame.decode(rawValue, channels);
        if (values == null) return;
        CraftTrendHistory[] histories = trendHistories.get(widget);
        if (histories == null || histories.length != channels) {
            CraftTrendHistory[] created = new CraftTrendHistory[channels];
            for (int i = 0; i < channels; i++) created[i] = new CraftTrendHistory();
            trendHistories.put(widget, created);
            histories = created;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        for (int i = 0; i < channels; i++) {
            if (!Double.isFinite(values[i])) continue;
            histories[i].append(values[i], now,
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
        String latest = "–æ–∂–∏–¥–∞–Ω–∏–µ –ø—Ä–∏–≤—è–∑–∫–∏";
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
        final String bluetoothAddress;
        BindingRequest(int widgetIndex, String host, int port, int unit, int address, int functionCode, int quantity) {
            this(widgetIndex, host, port, unit, address, functionCode, quantity, false, null);
        }
        BindingRequest(int widgetIndex, String host, int port, int unit, int address, int functionCode,
                       int quantity, boolean usb) {
            this(widgetIndex, host, port, unit, address, functionCode, quantity, usb, null);
        }
        BindingRequest(int widgetIndex, String host, int port, int unit, int address, int functionCode,
                       int quantity, boolean usb, String bluetoothAddress) {
            this.widgetIndices.add(widgetIndex);
            this.host = host; this.port = port; this.unit = unit;
            this.address = address; this.functionCode = functionCode; this.quantity = quantity;
            this.usb = usb; this.bluetoothAddress = bluetoothAddress;
        }
        String registerKey() {
            if (usb) return "usb/" + unit + "/" + address + "/FC" + functionCode + "/" + quantity;
            if (bluetoothAddress != null) return "bt/" + bluetoothAddress + "/" + unit + "/"
                    + address + "/FC" + functionCode + "/" + quantity;
            return host.toLowerCase(java.util.Locale.ROOT) + ":" + port + "/" + unit + "/" + address + "/FC" + functionCode + "/" + quantity;
        }
        ModbusPollGateway.Endpoint toGatewayEndpoint() {
            ModbusPollGateway.Endpoint.Scheme scheme = usb
                    ? ModbusPollGateway.Endpoint.Scheme.USB
                    : bluetoothAddress != null ? ModbusPollGateway.Endpoint.Scheme.BT
                    : ModbusPollGateway.Endpoint.Scheme.TCP;
            String endpointHost = bluetoothAddress != null ? bluetoothAddress : host;
            return ModbusPollGateway.Endpoint.of(scheme, endpointHost, port, unit, address,
                    functionCode, quantity);
        }
    }

    private void exportProject() {
        persistProject();
        String active = getSharedPreferences(PREFS, MODE_PRIVATE).getString(ACTIVE_PROJECT, "{}");
        final boolean hasCraftSource;
        boolean detectedCraftSource;
        try { detectedCraftSource = new JSONObject(active).has("craftSource"); }
        catch (Exception ignored) { detectedCraftSource = false; }
        hasCraftSource = detectedCraftSource;
        String[] options = hasCraftSource
                ? new String[]{"KaScada .pro ZIP ‚Äî –ø—Ä–æ–µ–∫—Ç –∏ –º–µ–¥–∏–∞", "BLOODSKY ZIP ‚Äî –ø—Ä–æ–µ–∫—Ç –∏ –º–µ–¥–∏–∞", "JSON ‚Äî —Ç–æ–ª—å–∫–æ –Ω–∞—Å—Ç—Ä–æ–π–∫–∏"}
                : new String[]{"ZIP ‚Äî –ø—Ä–æ–µ–∫—Ç –∏ –º–µ–¥–∏–∞", "JSON ‚Äî —Ç–æ–ª—å–∫–æ –Ω–∞—Å—Ç—Ä–æ–π–∫–∏"};
        new AlertDialog.Builder(this).setTitle("–≠–∫—Å–ø–æ—Ä—Ç –ø—Ä–æ–µ–∫—Ç–∞")
                .setItems(options, (dialog, which) -> {
                    boolean craftZip = hasCraftSource && which == 0;
                    boolean portableZip = hasCraftSource ? which == 1 : which == 0;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(portableZip || craftZip ? "application/zip" : "application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, projectName.replaceAll("[^A-Za-z–ê-–Ø–∞-—è0-9._-]", "_")
                            + (craftZip ? ".craft.zip" : portableZip ? ".bloodsky.zip" : ".bloodsky.json"));
                    int request = craftZip ? REQUEST_EXPORT_CRAFT_PROJECT
                            : portableZip ? REQUEST_EXPORT_PROJECT_ZIP : REQUEST_EXPORT_PROJECT;
                    try { startActivityForResult(intent, request); }
                    catch (Exception e) { Toast.makeText(this, "–ù–µ —É–¥–∞–ª–æ—Å—å –æ—Ç–∫—Ä—ã—Ç—å –≤—ã–±–æ—Ä —Ñ–∞–π–ª–∞", Toast.LENGTH_LONG).show(); }
                }).show();
    }

    private void showCraftMacroTools() {
        final android.app.Dialog dialog = new android.app.Dialog(this,
                android.R.style.Theme_Material_NoActionBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_◊≠µÁkhëÈÏ∂ªßq´^u’…∏ÅΩ’—¡’–Ä¸Ä¿∏’òÄËÄ°§ÄÙÙÄ¿Ä¸Ä¿∏»’òÄËÄ¿∏‹’ò§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅ=5AIÅ1=Pàπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÅIPÅI%Q!5Q%Å%9Pàπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÅIPÅI%Q!5Q%Å1=Pàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Ä¿∏’òÄËÄ°§ÄÙÙÄ¿Ä¸Ä¿∏»’òÄËÄ¿∏‹’ò§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅ5=Yàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Ä¿∏’òÄËÄ°§ÄÙÙÄ¿Ä¸Ä¿∏’òÄËÄ’òÄºÄŸò§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅA=\àπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Ä¿∏’òÄËÄ°§ÄÙÙÄ¿Ä¸Ä¿∏’òÄËÄ’òÄºÄŸò§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°•Õ…Öô—5≈——A’â±•Õ°ï»°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å§ÄÙÙÄ¿Ä¸Ä≈òÄºÄŸòÄËÄ…òÄºÄÕòÏ(ÄÄÄÄÄÄÄÄÄÄÄÄººÅ=…•ù•πÖ∞Å	}ILÅë…Ö‹‡†§ËÅ°ïÖëï»ÄƒºÃ∞ÅHΩLÅÖ–Äƒº»ÅÖπêÄ‘ºÿ∞(ÄÄÄÄÄÄÄÄÄÄÄÄººÅΩ’—¡’–ÅDÅÖ–Äƒº»∏Å]•…îÅÖπç°Ω…ÃÅÖπêÅë…Ö›∏ÅçΩπ—Öç—ÃÅµ’Õ–ÅµÖ—ç†∏(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†â	ÅILàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Ä¿∏’òÄËÄ°§ÄÙÙÄ¿Ä¸Ä¿∏’òÄËÄ’òÄºÄŸò§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅIIdàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸ÄÕòÄºÄ·òÄËÄ†…òÄ®Å§Ä¨ÄÕò§ÄºÄ·òÏ(ÄÄÄÄÄÄÄÄÄÄÄÄººÅQ°îÅΩ…•ù•πÖ∞Å…Öô–Å9Ω=HΩa=HÅÖπêÅçΩµ¡Ö…îÅë…Ö›•πúÅ’ÕïÃ(ÄÄÄÄÄÄÄÄÄÄÄÄººÅ≈’Ö…—ï»µ°ï•ù°–ÅÖπêÅ—°…ïîµ≈’Ö…—ï»µ°ï•ù°–Å•π¡’–Å¡Ω…—Ã∏(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ’—¡’–ÄòòÅ¡•πÃπ±ïπù—†ÄÙÙÄ»§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†â	Å9àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	Å=Hàπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÉBáBÉBCBKBwBWBwBcBTàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å§ÄÙÙÄ¿Ä¸Ä¿∏»’òÄËÄ¿∏‹’òÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅQ°îÅΩ…•ù•πÖ∞ÅÕ¡±•–µâΩë‰ÅÕΩ’…çîÅ	}a=HΩ	}Q%5HÅ°ÖÃÅÑ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅΩπîµ—°•…êÅ°ïÖëï»∏Å%—ÃÅπÖµïêÅçΩπ—Öç—ÃÅ±•îÅÖ–Äƒº»ÅÖπêÄ‘ºÿ∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†â	Åa=Hàπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅQ=8àπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÅQ@àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅQ=àπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÅIPÅ	1%9,àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅIPÅQ%5Hàπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÅIPÅ5`àπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å§ÄÙÙÄ¿Ä¸Ä¿∏’òÄËÄ’òÄºÄŸòÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ä°§Ä¨Ä≈ò§ÄºÄ°¡•πÃπ±ïπù—†Ä¨Ä≈ò§Ï(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄººÅ1ïùÖç‰Å’π¡•ππïêÅù…Ö¡†ÅïëùïÃÅ°ÖŸîÅπºÅÕΩ’…çîµçΩπô•…µïêÅ¡•∏ÅùïΩµï—…‰∏(ÄÄÄÄÄÄÄÅ…ï—’…∏Ä¿∏’òÏ(ÄÄÄÅÙ((ÄÄÄÅ¡’â±•åÅÕ—Ö—•åÅô±ΩÖ–ÅôâëQï…µ•πÖ±…Öç—•Ω∏°]•ëùï–Å›•ëùï–∞ÅM—…•πúÅ¡•∏∞ÅâΩΩ±ïÖ∏ÅΩ’—¡’–§ÅÏ(ÄÄÄÄÄÄÄÅ•òÄ°›•ëùï–ÄÙÙÅπ’±∞§Å…ï—’…∏Ä¿∏’òÏ(ÄÄÄÄÄÄÄÅ•òÄ†Ñâ	ÅIPÅ5I<àπï≈’Ö±Ã°çÖπΩπ•çÖ±âë	±Ωç≠QÂ¡î°›•ëùï–π—Â¡î§§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅôâëQï…µ•πÖ±…Öç—•Ω∏°›•ëùï–π—Â¡î∞Å¡•∏∞ÅΩ’—¡’–§Ï(ÄÄÄÄÄÄÄÅM—…•πùmtÅ¡•πÃÄÙÅ≠πΩ›πâëA•πÃ°›•ëùï–∞ÅΩ’—¡’–§Ï(ÄÄÄÄÄÄÄÅM—…•πúÅπÖµîÄÙÅçÖπΩπ•çÖ±âëA•π9Öµî°¡•∏§Ï(ÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å§ÄÙÄ¿ÏÅ§ÄÅ¡•πÃπ±ïπù—†ÏÅ§¨¨§(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°çÖπΩπ•çÖ±âëA•π9Öµî°¡•πÕm•t§πï≈’Ö±Ã°πÖµî§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–ÅÒÅ¡•πÃπ±ïπù—†ÄÙÄƒÄ¸Ä¿∏’òÄËÄ°§Ä¨Ä≈ò§ÄºÄ°¡•πÃπ±ïπù—†Ä¨Ä≈ò§Ï(ÄÄÄÄÄÄÄÅ…ï—’…∏Ä¿∏’òÏ(ÄÄÄÅÙ((ÄÄÄÅ¡’â±•åÅÕ—Ö—•åÅâΩΩ±ïÖ∏Å•Õ%πŸï…—•â±ïâë	ΩΩ±]•…î°âëΩππïç—•Ω∏ÅçΩππïç—•Ω∏∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅÕΩ’…çïQÂ¡î∞ÅM—…•πúÅ—Ö…ùï—QÂ¡î§ÅÏ(ÄÄÄÄÄÄÄÅ•òÄ°çΩππïç—•Ω∏ÄÙÙÅπ’±∞ÅÒÅçΩππïç—•Ω∏πï·—…Ö•ï±ëÃÄÙÙÅπ’±∞§Å…ï—’…∏ÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ)M=9=â©ïç–Åµï—ÑÄÙÅçΩππïç—•Ω∏πï·—…Ö•ï±ëÃÏ(ÄÄÄÄÄÄÄÅ…ï—’…∏Äâ	==0àπï≈’Ö±Ã°≠πΩ›πâëA•πQÂ¡î°ÕΩ’…çïQÂ¡î∞Åµï—ÑπΩ¡—M—…•πú†âÕΩ’…çïA•∏à∞Äàà§∞Å—…’î§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÄâ	==0àπï≈’Ö±Ã°≠πΩ›πâëA•πQÂ¡î°—Ö…ùï—QÂ¡î∞Åµï—ÑπΩ¡—M—…•πú†â—Ö…ùï—A•∏à∞Äàà§∞ÅôÖ±Õî§§Ï(ÄÄÄÅÙ((ÄÄÄÅ¡’â±•åÅÕ—Ö—•åÅâΩΩ±ïÖ∏Å•Õâë	ΩΩ±ïÖπ]•…ï%πŸï…—ïê°âëΩππïç—•Ω∏Åïëùî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅÕΩ’…çïQÂ¡î∞ÅM—…•πúÅ—Ö…ùï—QÂ¡î§ÅÏ(ÄÄÄÄÄÄÄÅ…ï—’…∏Å•Õ%πŸï…—•â±ïâë	ΩΩ±]•…î°ïëùî∞ÅÕΩ’…çïQÂ¡î∞Å—Ö…ùï—QÂ¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ	ΩΩ±ïÖ∏πQIUπï≈’Ö±Ã°ïëùîπï·—…Ö•ï±ëÃπΩ¡–†â•πŸï…—ïêà§§Ï(ÄÄÄÅÙ((ÄÄÄÅ¡’â±•åÅÕ—Ö—•åÅâΩΩ±ïÖ∏Åïôôïç—•Ÿïâë	ΩΩ±ïÖπ%π¡’–°âëΩππïç—•Ω∏ÅçΩππïç—•Ω∏∞ÅM—…•πúÅÕΩ’…çïQÂ¡î∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ—Ö…ùï—QÂ¡î∞ÅâΩΩ±ïÖ∏ÅÕΩ’…çïYÖ±’î§ÅÏ(ÄÄÄÄÄÄÄÅ…ï—’…∏Å•Õâë	ΩΩ±ïÖπ]•…ï%πŸï…—ïê°çΩππïç—•Ω∏∞ÅÕΩ’…çïQÂ¡î∞Å—Ö…ùï—QÂ¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÄÖÕΩ’…çïYÖ±’îÄËÅÕΩ’…çïYÖ±’îÏ(ÄÄÄÅÙ((ÄÄÄÄº®®Å5’—Ö—îÅΩπ±‰ÅÑÅ≠πΩ›∏Å	ΩΩ±ïÖ∏ÅïëùîÏÅΩ—°ï»Å•µ¡Ω…—ïêÅµï—ÖëÖ—ÑÅ•ÃÅ’π—Ω’ç°ïê∏Ä®º(ÄÄÄÅ¡’â±•åÅÕ—Ö—•åÅâΩΩ±ïÖ∏Å—Ωùù±ïâë	ΩΩ±ïÖπ]•…ï%πŸï…Õ•Ω∏°âëΩππïç—•Ω∏ÅçΩππïç—•Ω∏∞ÅM—…•πúÅÕΩ’…çïQÂ¡î∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ—Ö…ùï—QÂ¡î§Å—°…Ω›ÃÅ·çï¡—•Ω∏ÅÏ(ÄÄÄÄÄÄÄÅ•òÄ†Ö•Õ%πŸï…—•â±ïâë	ΩΩ±]•…î°çΩππïç—•Ω∏∞ÅÕΩ’…çïQÂ¡î∞Å—Ö…ùï—QÂ¡î§§Å…ï—’…∏ÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅçΩππïç—•Ω∏πï·—…Ö•ï±ëÃπ¡’–†â•πŸï…—ïêà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÖ•Õâë	ΩΩ±ïÖπ]•…ï%πŸï…—ïê°çΩππïç—•Ω∏∞ÅÕΩ’…çïQÂ¡î∞Å—Ö…ùï—QÂ¡î§§Ï(ÄÄÄÄÄÄÄÅ…ï—’…∏Å—…’îÏ(ÄÄÄÅÙ((ÄÄÄÄº®®Å±Ωç¨Åï¡Ωç°ÃÅµ’Õ–ÅπïŸï»Å±ïÖ¨Åô…Ω¥ÅÕÖŸïêÅô•±ïÃÅΩ»Åïë•—•πúÅ•π—ºÅ±•ŸîÅï·ïç’—•Ω∏∏Ä®º(ÄÄÄÅ¡’â±•åÅÕ—Ö—•åÅŸΩ•êÅ…ïÕï—âëQ•µï…I’π—•µî°]•ëùï–Å›•ëùï–§ÅÏ(ÄÄÄÄÄÄÄÅ•òÄ°›•ëùï–ÄÙÙÅπ’±∞§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÅM—…•πúÅ≠•πêÄÙÅçÖπΩπ•çÖ±âë	±Ωç≠QÂ¡î°›•ëùï–π—Â¡î§Ï(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅ	1%9,àπï≈’Ö±Ã°≠•πê§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πç…Öô—	±•π≠M—Ö—îÄÙÅâë…Öô—9Ö—•ŸïQ•µï»πM—Ö—îπ•π•—•Ö∞†§Ï(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅQ%5Hàπï≈’Ö±Ã°≠•πê§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πç…Öô—Q•µï…M—Ö—îÄÙÅâë…Öô—9Ö—•ŸïQ•µï»πM—Ö—îπ•π•—•Ö∞†§Ï(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅIIdàπï≈’Ö±Ã°≠•πê§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πç…Öô—……ÖÂ1ÖÕ—πÖâ±îÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅQ=8àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅQ@àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅQ=àπï≈’Ö±Ã°≠•πê§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–π—•µï…M—Ö…—ïë–ÄÙÄ¡0Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†â	ÅQ@àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅQ=àπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πçΩ’π—ï…1ÖÕ—%π¡’–ÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅÙÄÄÄÅÙ((ÄÄÄÄº®®Å=π±‰Å’πÕ’¡¡Ω…—ïêÅ±ïùÖç‰Åâ±Ωç¨Å¡Ö•…ÃÅµÖ‰Å’ÕîÅÖπΩπÂµΩ’ÃÅ¡ΩÕ•—•ΩπÖ∞ÅçΩππïç—•ΩπÃ∏Ä®º(ÄÄÄÅ¡’â±•åÅÕ—Ö—•åÅâΩΩ±ïÖ∏ÅçÖπUÕïUπ¡•ππïëâëÖ±±âÖç¨°M—…•πúÅÕΩ’…çïQÂ¡î∞ÅM—…•πúÅ—Ö…ùï—QÂ¡î§ÅÏ(ÄÄÄÄÄÄÄÅ•òÄ°ÕΩ’…çïQÂ¡îÄÙÙÅπ’±∞ÅÒÅ—Ö…ùï—QÂ¡îÄÙÙÅπ’±∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄÖÕΩ’…çïQÂ¡îπ—…•¥†§π—ΩU¡¡ï…ÖÕî°©ÖŸÑπ’—•∞π1ΩçÖ±îπI==P§πÕ—Ö…—Õ]•—††â	Äà§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄÖ—Ö…ùï—QÂ¡îπ—…•¥†§π—ΩU¡¡ï…ÖÕî°©ÖŸÑπ’—•∞π1ΩçÖ±îπI==P§πÕ—Ö…—Õ]•—††â	Äà§§Å…ï—’…∏ÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ…ï—’…∏Å≠πΩ›πâëA•πÃ°ÕΩ’…çïQÂ¡î∞Å—…’î§π±ïπù—†ÄÙÙÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ≠πΩ›πâëA•πÃ°ÕΩ’…çïQÂ¡î∞ÅôÖ±Õî§π±ïπù—†ÄÙÙÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ≠πΩ›πâëA•πÃ°—Ö…ùï—QÂ¡î∞Å—…’î§π±ïπù—†ÄÙÙÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ≠πΩ›πâëA•πÃ°—Ö…ùï—QÂ¡î∞ÅôÖ±Õî§π±ïπù—†ÄÙÙÄ¿Ï(ÄÄÄÅÙ((ÄÄÄÅ¡’â±•åÅÕ—Ö—•åÅâΩΩ±ïÖ∏ÅçÖπUÕïUπ¡•ππïëâëÖ±±âÖç¨°]•ëùï–ÅÕΩ’…çî∞Å]•ëùï–Å—Ö…ùï–§ÅÏ(ÄÄÄÄÄÄÄÅ•òÄ°ÕΩ’…çîÄÙÙÅπ’±∞ÅÒÅ—Ö…ùï–ÄÙÙÅπ’±∞ÅÒÅÕΩ’…çîπ—Â¡îÄÙÙÅπ’±∞ÅÒÅ—Ö…ùï–π—Â¡îÄÙÙÅπ’±∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄÖÕΩ’…çîπ—Â¡îπ—…•¥†§π—ΩU¡¡ï…ÖÕî°©ÖŸÑπ’—•∞π1ΩçÖ±îπI==P§πÕ—Ö…—Õ]•—††â	Äà§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄÖ—Ö…ùï–π—Â¡îπ—…•¥†§π—ΩU¡¡ï…ÖÕî°©ÖŸÑπ’—•∞π1ΩçÖ±îπI==P§πÕ—Ö…—Õ]•—††â	Äà§§Å…ï—’…∏ÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ…ï—’…∏Å≠πΩ›πâëA•πÃ°ÕΩ’…çî∞Å—…’î§π±ïπù—†ÄÙÙÄ¿ÄòòÅ≠πΩ›πâëA•πÃ°ÕΩ’…çî∞ÅôÖ±Õî§π±ïπù—†ÄÙÙÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ≠πΩ›πâëA•πÃ°—Ö…ùï–∞Å—…’î§π±ïπù—†ÄÙÙÄ¿ÄòòÅ≠πΩ›πâëA•πÃ°—Ö…ùï–∞ÅôÖ±Õî§π±ïπù—†ÄÙÙÄ¿Ï(ÄÄÄÅÙ((ÄÄÄÄº®®ÅM°Ω‹ÅΩπ±‰Å—Â¡îµçΩµ¡Ö—•â±îÅ—Ö…ùï–Å—ï…µ•πÖ±ÃÅÖô—ï»Åç°ΩΩÕ•πúÅÑÅÕΩ’…çîÅ¡•∏∏Ä®º(ÄÄÄÅ¡’â±•åÅÕ—Ö—•åÅM—…•πùmtÅçΩµ¡Ö—•â±ïâëQÖ…ùï—A•πÃ°M—…•πúÅÕΩ’…çïQÂ¡î∞ÅM—…•πúÅÕΩ’…çïA•∏∞ÅM—…•πúÅ—Ö…ùï—QÂ¡î§ÅÏ(ÄÄÄÄÄÄÄÅM—…•πùmtÅ—Ö…ùï—A•πÃÄÙÅ≠πΩ›πâëA•πÃ°—Ö…ùï—QÂ¡î∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÅM—…•πúÅÕΩ’…çïM•ùπÖ∞ÄÙÅ≠πΩ›πâëA•πQÂ¡î°ÕΩ’…çïQÂ¡î∞ÅÕΩ’…çïA•∏∞Å—…’î§Ï(ÄÄÄÄÄÄÄÅ•òÄ°≠πΩ›πâëA•πÃ°ÕΩ’…çïQÂ¡î∞Å—…’î§π±ïπù—†Ä¯Ä¿ÄòòÅÕΩ’…çïM•ùπÖ∞ÄÙÙÅπ’±∞§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Åπï‹ÅM—…•πùl¡tÏ(ÄÄÄÄÄÄÄÅ1•Õ–ÒM—…•πú¯ÅçΩµ¡Ö—•â±îÄÙÅπï‹Å……ÖÂ1•Õ–¯†§Ï(ÄÄÄÄÄÄÄÅôΩ»Ä°M—…•πúÅ—Ö…ùï—A•∏ÄËÅ—Ö…ùï—A•πÃ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ—Ö…ùï—M•ùπÖ∞ÄÙÅ≠πΩ›πâëA•πQÂ¡î°—Ö…ùï—QÂ¡î∞Å—Ö…ùï—A•∏∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅÕΩ’…çï·Öç–ÄÙÅâë…Öô—A•πQÂ¡ïÃπ—Â¡ïΩ…	±Ωç¨°ÕΩ’…çïQÂ¡î∞ÅÕΩ’…çïA•∏∞Å—…’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ—Ö…ùï—·Öç–ÄÙÅâë…Öô—A•πQÂ¡ïÃπ—Â¡ïΩ…	±Ωç¨°—Ö…ùï—QÂ¡î∞Å—Ö…ùï—A•∏∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†°ÕΩ’…çïM•ùπÖ∞ÄÙÙÅπ’±∞ÅÒÅ—Ö…ùï—M•ùπÖ∞ÄÙÙÅπ’±∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅâë…Öô—A•πQÂ¡ïÃπçΩµ¡Ö—•â±î°ÕΩ’…çïM•ùπÖ∞∞Å—Ö…ùï—M•ùπÖ∞§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅâë…Öô—A•πQÂ¡ïÃπçΩµ¡Ö—•â±î°ÕΩ’…çï·Öç–∞Å—Ö…ùï—·Öç–§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩµ¡Ö—•â±îπÖëê°—Ö…ùï—A•∏§Ï(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅ…ï—’…∏ÅçΩµ¡Ö—•â±îπ—Ω……Ö‰°πï‹ÅM—…•πùl¡t§Ï(ÄÄÄÅÙ((ÄÄÄÅ¡’â±•åÅÕ—Ö—•åÅM—…•πùmtÅçΩµ¡Ö—•â±ïâëQÖ…ùï—A•πÃ°]•ëùï–ÅÕΩ’…çî∞ÅM—…•πúÅÕΩ’…çïA•∏∞Å]•ëùï–Å—Ö…ùï–§ÅÏ(ÄÄÄÄÄÄÄÅM—…•πùmtÅ—Ö…ùï—A•πÃÄÙÅ≠πΩ›πâëA•πÃ°—Ö…ùï–∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÅM—…•πúÅÕΩ’…çïM•ùπÖ∞ÄÙÅ≠πΩ›πâëA•πQÂ¡î°ÕΩ’…çî∞ÅÕΩ’…çïA•∏∞Å—…’î§Ï(ÄÄÄÄÄÄÄÅ•òÄ°≠πΩ›πâëA•πÃ°ÕΩ’…çî∞Å—…’î§π±ïπù—†Ä¯Ä¿ÄòòÅÕΩ’…çïM•ùπÖ∞ÄÙÙÅπ’±∞§Å…ï—’…∏Åπï‹ÅM—…•πùl¡tÏ(ÄÄÄÄÄÄÄÅ1•Õ–ÒM—…•πú¯ÅçΩµ¡Ö—•â±îÄÙÅπï‹Å……ÖÂ1•Õ–¯†§Ï(ÄÄÄÄÄÄÄÅôΩ»Ä°M—…•πúÅ—Ö…ùï—A•∏ÄËÅ—Ö…ùï—A•πÃ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ—Ö…ùï—M•ùπÖ∞ÄÙÅ≠πΩ›πâëA•πQÂ¡î°—Ö…ùï–∞Å—Ö…ùï—A•∏∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅÕΩ’…çï·Öç–ÄÙÅâë…Öô—A•πQÂ¡ïÃπ—Â¡ïΩ…]•ëùï–°ÕΩ’…çî∞ÅÕΩ’…çïA•∏∞Å—…’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ—Ö…ùï—·Öç–ÄÙÅâë…Öô—A•πQÂ¡ïÃπ—Â¡ïΩ…]•ëùï–°—Ö…ùï–∞Å—Ö…ùï—A•∏∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†°ÕΩ’…çïM•ùπÖ∞ÄÙÙÅπ’±∞ÅÒÅ—Ö…ùï—M•ùπÖ∞ÄÙÙÅπ’±∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅâë…Öô—A•πQÂ¡ïÃπçΩµ¡Ö—•â±î°ÕΩ’…çïM•ùπÖ∞∞Å—Ö…ùï—M•ùπÖ∞§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅâë…Öô—A•πQÂ¡ïÃπçΩµ¡Ö—•â±î°ÕΩ’…çï·Öç–∞Å—Ö…ùï—·Öç–§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩµ¡Ö—•â±îπÖëê°—Ö…ùï—A•∏§Ï(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅ…ï—’…∏ÅçΩµ¡Ö—•â±îπ—Ω……Ö‰°πï‹ÅM—…•πùl¡t§Ï(ÄÄÄÅÙ((ÄÄÄÅ¡’â±•åÅÕ—Ö—•åÅM—…•πùmtÅ≠πΩ›πâëA•πÃ°M—…•πúÅâ±Ωç≠QÂ¡î∞ÅâΩΩ±ïÖ∏ÅΩ’—¡’–§ÅÏ(ÄÄÄÄÄÄÄÅ•òÄ°â±Ωç≠QÂ¡îÄÙÙÅπ’±∞§Å…ï—’…∏Åπï‹ÅM—…•πùl¡tÏ(ÄÄÄÄÄÄÄÅM—…•πúÅ≠•πêÄÙÅçÖπΩπ•çÖ±âë	±Ωç≠QÂ¡î°â±Ωç≠QÂ¡î§Ï(ÄÄÄÄÄÄÄÅ•òÄ†â	ÉBKBóB{BPàπï≈’Ö±Ã°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùl¡tÏ(ÄÄÄÄÄÄÄÅ•òÄ°•Õ…Öô—5≈——M’âÕç…•âï»°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùl¡tÏ(ÄÄÄÄÄÄÄÅ•òÄ°•Õ…Öô—5≈——A’â±•Õ°ï»°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùl¡tÄËÅπï‹ÅM—…•πùmuÏâ8à∞Äâ%8âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ°•Õ…Öô—Iïù•Õ—ï…MΩ’…çî°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùl¡tÏ(ÄÄÄÄÄÄÄÅ•òÄ°•Õ…Öô—Iïù•Õ—ï…M•π¨°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùl¡tÄËÅπï‹ÅM—…•πùmuÏâ%8âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ°•Õ…Öô—9ÖµïëYÖ…•Öâ±ïMΩ’…çî°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùl¡tÏ(ÄÄÄÄÄÄÄÅ•òÄ°•Õ…Öô—9ÖµïëYÖ…•Öâ±ïM•π¨°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùl¡tÄËÅπï‹ÅM—…•πùmuÏâ%8âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ°•Õ…Öô—M’π…•ÕïMΩ’…çî°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùl¡tÏ(ÄÄÄÄÄÄÄÅ•òÄ°•Õ…Öô—=ëQïµ¡MΩ’…çî°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùl¡tÏ(ÄÄÄÄÄÄÄÅ•òÄ°•Õ…Öô—âëMïπÕΩ…MΩ’…çî°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùl¡tÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	Å9àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	Å=Hàπï≈’Ö±Ã°≠•πê§ÅÒÄâ	Åa=Hàπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	Å99àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	Å9=Hàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ%8ƒà∞Äâ%8»âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÉBwBTàπï≈’Ö±Ã°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ%8âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅILàπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅMHàπï≈’Ö±Ã°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâHà∞ÄâLâÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅI}QI%àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	Å}QI%àπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÅIPÅPàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ%8âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅQTàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏà¨à∞Äà¥à∞ÄâHâÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅQTàπï≈’Ö±Ã°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâTà∞ÄâHà∞ÄâAXâÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅQàπï≈’Ö±Ã°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâà∞Äâ1à∞ÄâAXâÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÉBáBoB{B[BWBwBcBTàπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÉBKBØBüBcBãBCBwBcBTàπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÉBèBsBwB{B[BWBwBcBTàπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÉBSBWBoBWBwBcBTàπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	Å5%9}5`àπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâ=UPâÙÄËÅπï‹ÅM—…•πùmuÏâ%8ƒà∞Äâ%8»âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅ=5AIàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ%8ƒà∞Äâ%8»âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅ=5AIÅ1=Pàπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÅIPÅI%Q!5Q%Å%9Pàπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÅIPÅI%Q!5Q%Å1=Pàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ%8ƒà∞Äâ%8»âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÉBáBÉBCBKBwBWBwBcBTàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâà∞ÄââÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	Å5=Yàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâ=UPâÙÄËÅπï‹ÅM—…•πùmuÏâ%8âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅ!9Å$àπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ$âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅ!9Åàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏââÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅ5=Yàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ8à∞Äâ$âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅ5`àπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ8à∞Äâ$âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅIIdàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ8à∞ÄâPà∞Äâ%âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅM0àπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ%8à∞Äâ$ƒà∞Äâ$»âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅM0àπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâ=UPâÙÄËÅπï‹ÅM—…•πùmuÏâà∞Äâ%8¿à∞Äâ%8ƒâÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅA=\àπï≈’Ö±Ã°≠•πê§§Å…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâ=UPâÙÄËÅπï‹ÅM—…•πùmuÏâ	Mà∞Äâa@âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅM%8àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	Å	Làπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	Å=Làπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅQ8àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅMEIPàπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	Å1=QQ=%9Pàπï≈’Ö±Ã°≠•πê§ÅÒÄâ	Å%9QQ=1=Pàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâ=UPâÙÄËÅπï‹ÅM—…•πùmuÏâ%8âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅ1=àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅIPÅ]=IÅQ<Å1=Pàπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÅIPÅM%8àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅIPÅ	Làπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÅIPÅ%9PÅQ<Å1=Pàπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅIPÅ1=PÅQ<Å%9Pàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâ=UPâÙÄËÅπï‹ÅM—…•πùmuÏâ%8âÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅA=\àπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ`à∞ÄâdâÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅQ%5Hàπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ%8à∞Äâ5LâÙÏ(ÄÄÄÄÄÄÄÅ•òÄ†â	ÅQ=8àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅQ=àπï≈’Ö±Ã°≠•πê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄâ	ÅQ@àπï≈’Ö±Ã°≠•πê§ÅÒÄâ	ÅIPÅ	1%9,àπï≈’Ö±Ã°≠•πê§§(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅΩ’—¡’–Ä¸Åπï‹ÅM—…•πùmuÏâDâÙÄËÅπï‹ÅM—…•πùmuÏâ%8à∞ÄâAPâÙÏ(ÄÄÄÄÄÄÄÅ…ï—’…∏Åπï‹ÅM—…•πùl¡tÏ(ÄÄÄÅÙ((ÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅÖëëâëΩππïç—•Ω∏°•π–ÅÕΩ’…çî∞Å•π–Å—Ö…ùï–∞ÅâëΩππïç—•Ω∏Åïëùî§ÅÏ(ÄÄÄÄÄÄÄÅ•òÄ°ïëùîÄÙÙÅπ’±∞ÅÒÅïëùîπÕΩ’…çîÄÑÙÅÕΩ’…çîÅÒÅïëùîπ—Ö…ùï–ÄÑÙÅ—Ö…ùï–(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅç’……ïπ—Mç…ïïπ%πëï‡ÄÄ¿ÅÒÅç’……ïπ—Mç…ïïπ%πëï‡Ä¯ÙÅÕç…ïïπÃπÕ•Èî†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅÕΩ’…çîÄÄ¿ÅÒÅ—Ö…ùï–ÄÄ¿ÅÒÅÕΩ’…çîÄ¯ÙÅ›•ëùï—ÃπÕ•Èî†§ÅÒÅ—Ö…ùï–Ä¯ÙÅ›•ëùï—ÃπÕ•Èî†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅÕΩ’…çîÄÙÙÅ—Ö…ùï–ÅÒÄÖ•Õâê°›•ëùï—Ãπùï–°ÕΩ’…çî§§ÅÒÄÖ•Õâê°›•ëùï—Ãπùï–°—Ö…ùï–§§§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÅMç…ïï∏ÅÕç…ïï∏ÄÙÅÕç…ïïπÃπùï–°ç’……ïπ—Mç…ïïπ%πëï‡§Ï(ÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö°ÖÕΩµ¡Ö—•â±ï-πΩ›πâëA•πÃ°çΩππïç—•ΩπQΩ)ÕΩ∏°ïëùî§∞Å›•ëùï—Ãπùï–°ÕΩ’…çî§π—Â¡î∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï—Ãπùï–°—Ö…ùï–§π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°—°•Ã∞ÄãBwB◊FB˚BÀBÛB◊FFB„BÛF/B‘ÉBÎB˚B˜FB√BÎFF,Å	à∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Å•πŸÖ±•ê§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°—°•Ã∞ÄãBwB◊BÎB˚FFB◊BÎFB˜B√F<ÉFBÀF?BﬂF0Å	à∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅ•òÄ°…ïÖç°ïÕâê°—Ö…ùï–∞ÅÕΩ’…çî∞Åπï‹ÅâΩΩ±ïÖπm›•ëùï—ÃπÕ•Èî†•t∞ÅÕç…ïï∏πçΩππïç—•ΩπÃ§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°—°•Ã∞ÄãBáBÀF?BﬂF0ÉB˚FBÛB◊B˜B◊B˜B¿ËÉB˚B˜B¿ÉFB˚BﬂB”B√FGFÉFB„BÎBÏÉB»Å	à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅôΩ»Ä°âëΩππïç—•Ω∏Åï·•Õ—•πúÄËÅÕç…ïï∏πçΩππïç—•ΩπÃ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅπï›QÖ…ùï—A•∏ÄÙÅçÖπΩπ•çÖ±âëA•π9Öµî°ïëùîπï·—…Ö•ï±ëÃπΩ¡—M—…•πú†â—Ö…ùï—A•∏à∞Äàà§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Öπï›QÖ…ùï—A•∏π•Õµ¡—‰†§ÄòòÅï·•Õ—•πúπ—Ö…ùï–ÄÙÙÅ—Ö…ùï–(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅπï›QÖ…ùï—A•∏πï≈’Ö±Ã°çÖπΩπ•çÖ±âëA•π9Öµî°ï·•Õ—•πúπï·—…Ö•ï±ëÃπΩ¡—M—…•πú†â—Ö…ùï—A•∏à∞Äàà§§§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°—°•Ã∞ÄãBKFB˚B–Å	ÉFB€B‘ÉBﬂB√B˜F?FÉB”FFBœB„BÉBˇFB˚BÀB˚B”B˚Bà∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ë’¡±•çÖ—ïâëπë¡Ω•π–°ï·•Õ—•πú∞Åïëùî§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°—°•Ã∞ÄãBãB√BÎB√F<ÉFBÀF?BﬂF0ÉFB€B‘ÉFFF'B◊FFBÀFB◊Fà∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅçΩµµ•—âë]•…ïë•–°Õç…ïï∏∞Ä†§Ä¥¯ÅÕç…ïï∏πçΩππïç—•ΩπÃπÖëê°ïëùî§∞ÄãBáBÀF?BﬂF0Å	ÉB”B˚B«B√BÀBÔB◊B˜B¿à§Ï(ÄÄÄÅÙ((ÄÄÄÄº®®ÅMÖŸîÅÑÅ—Â¡ïêÅ›•…îÅµ’—Ö—•Ω∏ÅâïôΩ…îÅ¡’â±•Õ°•πúÅ•—ÃÅ’πëºÅÕ—ï¿Å—ºÅ—°îÅïë•—Ω»Å°•Õ—Ω…‰∏Ä®º(ÄÄÄÅ¡…•ŸÖ—îÅâΩΩ±ïÖ∏ÅçΩµµ•—âë]•…ïë•–°Mç…ïï∏ÅÕç…ïï∏∞ÅI’ππÖâ±îÅµ’—Ö—•Ω∏∞ÅM—…•πúÅÕ’ççïÕÕ5ïÕÕÖùî§ÅÏ(ÄÄÄÄÄÄÄÅ•òÄ°Õç…ïï∏ÄÙÙÅπ’±∞ÅÒÅç’……ïπ—Mç…ïïπ%πëï‡ÄÄ¿ÅÒÅç’……ïπ—Mç…ïïπ%πëï‡Ä¯ÙÅÕç…ïïπÃπÕ•Èî†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅÕç…ïïπÃπùï–°ç’……ïπ—Mç…ïïπ%πëï‡§ÄÑÙÅÕç…ïï∏§Å…ï—’…∏ÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ)M=9=â©ïç–ÅΩ…•ù•πÖ±A…Ω©ïç—•ï±ëÃÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅΩ…•ù•πÖ±A…Ω©ïç—•ï±ëÃÄÙÅ¡…Ω©ïç—·—…Ö•ï±ëÃÄÙÙÅπ’±∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸Åπ’±∞ÄËÅπï‹Å)M=9=â©ïç–°¡…Ω©ïç—·—…Ö•ï±ëÃπ—ΩM—…•πú†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕÖŸïêÄÙÅâë]•…ïë•—Q…ÖπÕÖç—•Ω∏πÖ¡¡±‰°Õç…ïï∏∞ÅÕç…ïï∏πçΩππïç—•ΩπÃ∞Åôâë]•…ï!•Õ—Ω…‰∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµ’—Ö—•Ω∏∞Ä†§Ä¥¯Å¡ï…Õ•Õ—A…Ω©ïç—9Ω‹°—…’î§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖÕÖŸïê§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡…Ω©ïç—·—…Ö•ï±ëÃÄÙÅΩ…•ù•πÖ±A…Ω©ïç—•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÖŸïç—•ŸïMç…ïï∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ïë•—Ω»ÄÑÙÅπ’±∞§Åïë•—Ω»π•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°—°•Ã∞ÄãBcBﬂBÛB◊B˜B◊B˜B„B‘Å	ÉB˜B‘ÉFB˚FFB√B˜B◊B˜B¯à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ïë•—Ω»ÄÑÙÅπ’±∞§Åïë•—Ω»π•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°—°•Ã∞ÅÕ’ççïÕÕ5ïÕÕÖùî∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å—…’îÏ(ÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏ÅôÖ•±’…î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ¡…Ω©ïç—·—…Ö•ï±ëÃÄÙÅΩ…•ù•πÖ±A…Ω©ïç—•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÕÖŸïç—•ŸïMç…ïï∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ïë•—Ω»ÄÑÙÅπ’±∞§Åïë•—Ω»π•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°—°•Ã∞ÄãBwB‘ÉFB”B√BÔB˚FF0ÉB„BﬂBÛB◊B˜B„FF0Å	ËÄàÄ¨ÅôÖ•±’…îπùï—5ïÕÕÖùî†§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÅÙ((ÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅçΩππïç—âë]•ëùï—Ã°•π–ÅÕΩ’…çî∞Å•π–Å—Ö…ùï–§ÅÏ(ÄÄÄÄÄÄÄÅ•òÄ°ÕΩ’…çîÄÄ¿ÅÒÅ—Ö…ùï–ÄÄ¿ÅÒÅÕΩ’…çîÄ¯ÙÅ›•ëùï—ÃπÕ•Èî†§ÅÒÅ—Ö…ùï–Ä¯ÙÅ›•ëùï—ÃπÕ•Èî†§ÅÒÅÕΩ’…çîÄÙÙÅ—Ö…ùï–§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°—°•Ã∞ÄãBKF/B«B◊FB‡ÉB”BÀB¿ÉFB√BﬂB˜F/FÅ	∑B«BÔB˚BÎB¿à∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅ•òÄ†Ö•Õâê°›•ëùï—Ãπùï–°ÕΩ’…çî§§ÅÒÄÖ•Õâê°›•ëùï—Ãπùï–°—Ö…ùï–§§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°—°•Ã∞ÄãBáBÀF?BﬂB‡ÉBÛB˚B€B˜B¯ÉFB˚BﬂB”B√BÀB√FF0ÉFB˚BÔF3BÎB¯ÉBÛB◊B€B”FÅ	∑B«BÔB˚BÎB√BÛB‡à∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅMç…ïï∏ÅÕç…ïï∏ÄÙÅÕç…ïïπÃπùï–°ç’……ïπ—Mç…ïïπ%πëï‡§Ï(ÄÄÄÄÄÄÄÅ•òÄ°…ïÖç°ïÕâê°—Ö…ùï–∞ÅÕΩ’…çî∞Åπï‹ÅâΩΩ±ïÖπm›•ëùï—ÃπÕ•Èî†•t∞ÅÕç…ïï∏πçΩππïç—•ΩπÃ§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°—°•Ã∞ÄãBáBÀF?BﬂF0ÉB˚FBÛB◊B˜B◊B˜B¿ËÉB˚B˜B¿ÉFB˚BﬂB”B√FGFÉFB„BÎBÏÉB»Å	à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅç°ΩΩÕïâëA•πÃ°ÕΩ’…çî∞Å—Ö…ùï–§Ï(ÄÄÄÅÙ((ÄÄÄÅ¡’â±•åÅÕ—Ö—•åÅç±ÖÕÃÅ]•ëùï–ÅÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅM—…•πúÅ—Â¡îÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅM—…•πúÅ—ï·–Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅM—…•πúÅçΩππïç—•Ωπ%πôºÄÙÄààÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅâΩΩ±ïÖ∏Å•π¡’—YÖ±’îÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅâΩΩ±ïÖ∏Åâ’——ΩπM—Ö—îÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅâΩΩ±ïÖ∏Åç…Öô—M›•—ç°]…•—ïAïπë•πúÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅâΩΩ±ïÖ∏Å…ÕM—Ö—îÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄººÅI’π—•µîµΩπ±‰Åë•Õ—•πç—•Ω∏Åâï—›ïï∏ÅÑÅµ•ÕÕ•πúÅπÖ—•ŸîÅΩ’—¡’–ÅÖπêÅÑÅŸÖ±•ê(ÄÄÄÄÄÄÄÄººÅ±ΩÖ–Å9Ö8Ω%πô•π•—‰Å¡…Ωë’çïêÅâ‰Å…Öô–Å%¥‹‘–ÅÖ…•—°µï—•å∏(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅâΩΩ±ïÖ∏Åôâë=’—¡’—ŸÖ•±Öâ±îÄÙÅ—…’îÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅâΩΩ±ïÖ∏Åôâë=’—¡’—ŸÖ•±Öâ•±•—Â·¡±•ç•–Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅ•π–ÅçΩ’π—ï…A…ïÕï–ÄÙÄƒÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅ•π–ÅçΩ’π—ï…YÖ±’îÄÙÄ¿Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅâΩΩ±ïÖ∏ÅçΩ’π—ï…1ÖÕ—%π¡’–ÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄººÅ9Ö—•ŸîÅ…Öô–ÅïëùîÅô±ÖùÃÅ¡ï…Õ•Õ–ÅΩπ±‰ÅΩ∏ÅπÖ—•ŸîÅQTÅ…ïçΩ…ëÃÅÕºÅÑÅÕÖŸïê(ÄÄÄÄÄÄÄÄººÅ°ï±êÅ•π¡’–ÅçÖππΩ–ÅâîÅçΩ’π—ïêÅÖùÖ•∏ÅÖô—ï»Å…ïΩ¡ïπ•πúÅ—°îÅ¡…Ω©ïç–∏(ÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Åç…Öô—1ÖÕ—A±’ÃÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Åç…Öô—1ÖÕ—5•π’ÃÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄººÅ=…•ù•πÖ∞Å	}QI%HÅµΩëîÅPÅ…ïÕï—ÃÅ—ºÅDıôÖ±ÕîÅΩ∏ÅÑÅô…ïÕ†Å•πÕ—Öπçî∏(ÄÄÄÄÄÄÄÄººÅºÅπΩ–ÅÕï…•Ö±•ÈîÅ…’π—•µîÅ±Ö—ç°ïÃÅ•π—ºÅ±ïùÖç‰Å¡…Ω©ïç–Åµï—ÖëÖ—Ñ∏(ÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Åç…Öô—QΩùù±ï=’—¡’–ÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Åç…Öô—QΩùù±ï1ÖÕ—%π¡’–ÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄººÅ°ÖπùîÅëï—ïç—•Ω∏Å…’π—•µîÅ•ÃÅ—…ÖπÕ•ïπ–ÏÅÕΩ’…çîÅÕ—Ö…—ÃÅÖ–Ä¿º¿∏¡ò∏(ÄÄÄÄÄÄÄÄººÅMÖŸïêÅΩ±êÅ¡…Ω©ïç–ÅÕ—Ö—îÅÖπêÅŸïπëΩ»Åµï—ÖëÖ—ÑÅ…ïµÖ•∏Å’π—Ω’ç°ïê∏(ÄÄÄÄÄÄÄÅâë…Öô—°ÖπùïQ…ÖπÕ•—•ΩπÃπ%π—M—Ö—îÅç…Öô—°Öπùï%π—M—Ö—îÄÙÅâë…Öô—°ÖπùïQ…ÖπÕ•—•ΩπÃπ%π—M—Ö—îπ•π•—•Ö∞†§Ï(ÄÄÄÄÄÄÄÅâë…Öô—°ÖπùïQ…ÖπÕ•—•ΩπÃπ±ΩÖ—M—Ö—îÅç…Öô—°Öπùï±ΩÖ—M—Ö—îÄÙÅâë…Öô—°ÖπùïQ…ÖπÕ•—•ΩπÃπ±ΩÖ—M—Ö—îπ•π•—•Ö∞†§Ï(ÄÄÄÄÄÄÄÄººÅ	±•π¨Å…’π—•µîÅï¡Ωç†Å•ÃÅπΩ–ÅÕÖŸïêÏÅÑÅπï›±‰ÅΩ¡ïπïêÅ¡…Ω©ïç–ÅÕ—Ö…—ÃÅ•ë±î∏(ÄÄÄÄÄÄÄÅâë…Öô—9Ö—•ŸïQ•µï»πM—Ö—îÅç…Öô—	±•π≠M—Ö—îÄÙÅâë…Öô—9Ö—•ŸïQ•µï»πM—Ö—îπ•π•—•Ö∞†§Ï(ÄÄÄÄÄÄÄÅâë…Öô—9Ö—•ŸïQ•µï»πM—Ö—îÅç…Öô—Q•µï…M—Ö—îÄÙÅâë…Öô—9Ö—•ŸïQ•µï»πM—Ö—îπ•π•—•Ö∞†§Ï(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–Å±ΩπúÅç…Öô—Q•µï…I’π—•µïA…ïÕï—5ÃÏ(ÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Åç…Öô—……ÖÂ1ÖÕ—πÖâ±îÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅëΩ’â±îÅç…Öô—MïπÕΩ…YÖ±’îÄÙÅΩ’â±îπ9Ö8Ï(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅΩ’â±îÅç…Öô—5≈——YÖ±’îÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅâΩΩ±ïÖ∏Åç…Öô—5≈——1ÖÕ—πÖâ±îÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅëΩ’â±ïmtÅç…Öô—=…•ïπ—Ö—•ΩπYÖ±’ïÃÄÙÅπï‹ÅëΩ’â±ïmuÌΩ’â±îπ9Ö8∞ÅΩ’â±îπ9Ö8∞ÅΩ’â±îπ9Ö9ÙÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅâΩΩ±ïÖ∏Åç…Öô—MïπÕΩ…YÖ±•êÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅâΩΩ±ïÖ∏Åç…Öô—=ëQïµ¡Aïπë•πúÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–Å±ΩπúÅç…Öô—=ëQïµ¡9ï·—AΩ±±–Ï(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–Å•π–Åç…Öô—Iïù•Õ—ï…]Ω…ê¿∞Åç…Öô—Iïù•Õ—ï…]Ω…êƒÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅâΩΩ±ïÖ∏Åç…Öô—Iïù•Õ—ï…YÖ±•êÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅâΩΩ±ïÖ∏Åç…Öô—Iïù•Õ—ï…]…•—ïAïπë•πúÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅM—…•πúÅç…Öô—Iïù•Õ—ï…1ÖÕ—]…•—ï-ï‰ÄÙÄààÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–Å±ΩπúÅç…Öô—Iïù•Õ—ï…Iï—…Â–Ï(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅâΩΩ±ïÖ∏Åç…Öô—5Öç…Ω%π¡’—YÖ±•êÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅëΩ’â±îÅç…Öô—5Öç…Ω%π¡’—YÖ±’îÄÙÅΩ’â±îπ9Ö8Ï(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–Å)M=9=â©ïç–Åç…Öô—5Öç…ΩI’π—•µï…Ö¡°MΩ’…çîÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–Å1•Õ–Ò]•ëùï–¯Åç…Öô—5Öç…ΩI’π—•µï]•ëùï—ÃÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–Å1•Õ–ÒâëΩππïç—•Ω∏¯Åç…Öô—5Öç…ΩI’π—•µïΩππïç—•ΩπÃÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–Å•π–Åç…Öô—5Öç…ΩI’π—•µï=’—¡’—9ΩëîÄÙÄ¥ƒÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅM—…•πúÅç…Öô—5Öç…ΩI’π—•µï=’—¡’—A•∏ÄÙÄààÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅ•π–Å—•µï…A…ïÕï—5ÃÄÙÄƒ¿¿¿Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅ±ΩπúÅ—•µï…M—Ö…—ïë–ÄÙÄ¡0Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅëΩ’â±îÅçΩµ¡Ö…ïÄÙÄ¿∏¿∞ÅçΩµ¡Ö…ïÄÙÄ¿∏¿Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅM—…•πúÅçΩµ¡Ö…ï=¡ï…Ö—Ω»ÄÙÄà¯àÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅëΩ’â±îÅµ•πYÖ±’îÄÙÄ¿∏¿∞ÅµÖ·YÖ±’îÄÙÄƒ¿¿∏¿Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅ•π–ÅâÖç≠ù…Ω’πëΩ±Ω»ÄÙÄ¿Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅ•π–Å—ï·—Ω±Ω»ÄÙÅΩ±Ω»π…ùà†Ãƒ∞Ä–ƒ∞Ä‘‘§Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅ•π–Å—ï·—M•ÈïM¿ÄÙÄƒ–Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅâΩΩ±ïÖ∏ÅŸ•Õ•â±îÄÙÅ—…’îÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅâΩΩ±ïÖ∏ÅÕ°Ω›…ÖµîÄÙÅ—…’îÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅâΩΩ±ïÖ∏ÅÖ’—ΩIïÕ•ÈïQï·–ÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅM—…•πùmtÅëÂπQï·—M—Ö—ïÃÄÙÅπï‹ÅM—…•πùlƒŸtÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅ•π—mtÅëÂπQï·—Ω±Ω…ÃÄÙÅπï‹Å•π—lƒŸtÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅM—…•πùmtÅâ•—1•Õ—1Öâï±ÃÄÙÅπï‹ÅM—…•πùlƒŸtÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅM—…•πùmtÅŸΩ•çï5ïÕÕÖùïÃÄÙÅπï‹ÅM—…•πùlƒŸtÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅM—…•πùmtÅ•µÖùïAÖ—°M—Ö—ïÃÄÙÅπï‹ÅM—…•πùlƒŸtÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅëΩ’â±îÅÕΩ’πëQ°…ïÕ°Ω±êÄÙÄƒ∏¿Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅâΩΩ±ïÖ∏ÅÕΩ’πëç—•ŸîÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅM—…•πúÅÕΩ’πë•±ïAÖ—†ÄÙÄãBCBÔB√FBàÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅ•π–Åç…Öô—±Ö…µMΩ’πë%πëï‡ÄÙÄ¿Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅâΩΩ±ïÖ∏Åç…Öô—±Ö…µ1ΩΩ¿ÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄººÅ%»ƒÅÕΩ’πêµâÖπ¨Å±Ö—ç°ïÃÅÖ…îÅ…’π—•µîµΩπ±‰Å±•≠îÅ…Öô–ùÃÅ5ïë•ÖA±ÖÂï»ÅÕ—Ö—î∏(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–Å•π–Åç…Öô—MΩ’πëç—•Ÿï5ÖÕ¨Ï(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–Å•π–Åç…Öô—MΩ’πëA±ÖÂïë5ÖÕ¨Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅ•π–Å—Ö…ùï—Mç…ïïπ%πëï‡ÄÙÄ¿Ï(ÄÄÄÄÄÄÄÅ¡’â±•åÅM—…•πúÅ—Ö…ùï—U…∞ÄÙÄààÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅ•π–Å±ÖÕ—YΩ•çïM—Ö—îÄÙÄ¥ƒÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅâΩΩ±ïÖ∏Å±ÖÕ—MΩ’πëM—Ö—îÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅâΩΩ±ïÖ∏Å±ÖÕ—Qï±ïù…ÖµM—Ö—îÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅâΩΩ±ïÖ∏Å±ÖÕ—…Öô—A’Õ°M—Ö—îÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–ÅâΩΩ±ïÖ∏Å±ÖÕ—MµÕM—Ö—îÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅ)M=9=â©ïç–Åï·—…Ö•ï±ëÃÄÙÅπï‹Å)M=9=â©ïç–†§Ï(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–Å)M=9=â©ïç–Åç…Öô—5Öç…Ωïô•π•—•ΩπMΩ’…çïÖç°îÏ(ÄÄÄÄÄÄÄÅ—…ÖπÕ•ïπ–Åâë…Öô—5Öç…Ωïô•π•—•Ω∏Åç…Öô—5Öç…Ωïô•π•—•ΩπÖç°îÏ(ÄÄÄÄÄÄÄÄººÅI’π—•µîµΩπ±‰ÅÕçΩ¡îÅôΩ»ÅπΩëïÃÅ•πÕ•ëîÅÖ∏Å	}5I<Å•πÕ—Öπçî∏(ÄÄÄÄÄÄÄÅ¡’â±•åÅM—…•πúÅç…Öô—YÖ…•Öâ±ï9ÖµïÕ¡ÖçîÄÙÄààÏ(ÄÄÄÄÄÄÄÅ¡’â±•åÅô±ΩÖ–Å‡∞Å‰∞Å‹∞Å†Ï((ÄÄÄÄÄÄÄÅ¡’â±•åÅ]•ëùï–†§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å§ÄÙÄ¿ÏÅ§ÄÄƒÿÏÅ§¨¨§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅëÂπQï·—M—Ö—ïÕm•tÄÙÄãBáB˚FFB˚F?B˜B„B‘ÄàÄ¨Å§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅëÂπQï·—Ω±Ω…Õm•tÄÙÄ¿Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâ•—1•Õ—1Öâï±Õm•tÄÙÄãBGB„FÄàÄ¨Å§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅŸΩ•çï5ïÕÕÖùïÕm•tÄÙÄãBáB˚B˚B«F'B◊B˜B„B‘ÄàÄ¨Å§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•µÖùïAÖ—°M—Ö—ïÕm•tÄÙÄãBkB√FFB„B˜BÎB¿ÄàÄ¨Å§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÅÙ((ÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅ¡’â±•Õ°…Öô—5≈——ëùïÃ†§ÅÏ(ÄÄÄÄÄÄÄÅâë…Öô—5≈——I’π—•µîÅ…’π—•µîÄÙÅç…Öô—5≈——I’π—•µîÏ(ÄÄÄÄÄÄÄÅ•òÄ°…’π—•µîÄÙÙÅπ’±∞ÅÒÄÖΩ¡ï…Ö—Ω…5ΩëîÅÒÅÕç…ïïπÃπ•Õµ¡—‰†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅç’……ïπ—Mç…ïïπ%πëï‡ÄÄ¿ÅÒÅç’……ïπ—Mç…ïïπ%πëï‡Ä¯ÙÅÕç…ïïπÃπÕ•Èî†§§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÅ©ÖŸÑπ’—•∞πMï–Ò]•ëùï–¯ÅÕïï∏ÄÙÅ©ÖŸÑπ’—•∞πΩ±±ïç—•ΩπÃππï›Mï—…Ωµ5Ö¿†(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å©ÖŸÑπ’—•∞π%ëïπ—•—Â!ÖÕ°5Ö¿Ò]•ëùï–∞Å	ΩΩ±ïÖ∏¯†§§Ï(ÄÄÄÄÄÄÄÅ¡’â±•Õ°…Öô—5≈——ëùïÕ%π…Ö¡†°…’π—•µî∞Å›•ëùï—Ã∞ÅÕç…ïïπÃπùï–°ç’……ïπ—Mç…ïïπ%πëï‡§πçΩππïç—•ΩπÃ∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕïï∏∞Ä¿∞Å—…’î§Ï(ÄÄÄÅÙ((ÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅ¡’â±•Õ°…Öô—5≈——ëùïÕ%π…Ö¡†°âë…Öô—5≈——I’π—•µîÅ…’π—•µî∞(ÄÄÄÄÄÄÄÄÄÄÄÅ1•Õ–Ò]•ëùï–¯Åù…Ö¡°]•ëùï—Ã∞Å1•Õ–ÒâëΩππïç—•Ω∏¯Åù…Ö¡°Ωππïç—•ΩπÃ∞(ÄÄÄÄÄÄÄÄÄÄÄÅ©ÖŸÑπ’—•∞πMï–Ò]•ëùï–¯ÅÕïï∏∞Å•π–Åëï¡—†∞ÅâΩΩ±ïÖ∏ÅÕç…ïïπ…Ö¡†§ÅÏ(ÄÄÄÄÄÄÄÅ•òÄ°ù…Ö¡°]•ëùï—ÃÄÙÙÅπ’±∞ÅÒÅù…Ö¡°Ωππïç—•ΩπÃÄÙÙÅπ’±∞ÅÒÅëï¡—†Ä¯Ä‡§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÅâΩΩ±ïÖπmtÅçÖç°ïYÖ±•êÄÙÅπï‹ÅâΩΩ±ïÖπmù…Ö¡°]•ëùï—ÃπÕ•Èî†•tÏ(ÄÄÄÄÄÄÄÅâΩΩ±ïÖπmtÅçÖç°ïYÖ±’ïÃÄÙÅπï‹ÅâΩΩ±ïÖπmù…Ö¡°]•ëùï—ÃπÕ•Èî†•tÏ(ÄÄÄÄÄÄÄÅëΩ’â±ïmtÅçÖç°ï9’µï…•åÄÙÅπï‹ÅëΩ’â±ïmù…Ö¡°]•ëùï—ÃπÕ•Èî†•tÏ(ÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å—Ö…ùï–ÄÙÄ¿ÏÅ—Ö…ùï–ÄÅù…Ö¡°]•ëùï—ÃπÕ•Èî†§ÏÅ—Ö…ùï–¨¨§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Å¡’â±•Õ°ï»ÄÙÅù…Ö¡°]•ëùï—Ãπùï–°—Ö…ùï–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•Õ…Öô—5≈——A’â±•Õ°ï»°¡’â±•Õ°ï»π—Â¡î§§ÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ1•Õ–Ò	ΩΩ±ïÖ∏¯ÅâΩΩ±ïÖπYÖ±’ïÃÄÙÅπï‹Å……ÖÂ1•Õ–¯†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ1•Õ–ÒΩ’â±î¯Åπ’µï…•çYÖ±’ïÃÄÙÅπï‹Å……ÖÂ1•Õ–¯†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ1•Õ–ÒM—…•πú¯Å¡•πÃÄÙÅπï‹Å……ÖÂ1•Õ–¯†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°âëΩππïç—•Ω∏ÅïëùîÄËÅù…Ö¡°Ωππïç—•ΩπÃ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ïëùîÄÙÙÅπ’±∞ÅÒÅïëùîπ—Ö…ùï–ÄÑÙÅ—Ö…ùï–ÅÒÅïëùîπÕΩ’…çîÄÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅïëùîπÕΩ’…çîÄ¯ÙÅù…Ö¡°]•ëùï—ÃπÕ•Èî†§§ÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡•πÃπÖëê°ïëùîπï·—…Ö•ï±ëÃπΩ¡—M—…•πú†â—Ö…ùï—A•∏à∞Äàà§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖπYÖ±’ïÃπÖëê°ïŸÖ±’Ö—ïâë	±Ωç¨°ù…Ö¡°]•ëùï—Ã∞Åù…Ö¡°Ωππïç—•ΩπÃ∞ÅïëùîπÕΩ’…çî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹ÅâΩΩ±ïÖπmù…Ö¡°]•ëùï—ÃπÕ•Èî†•t∞ÅçÖç°ïYÖ±•ê∞ÅçÖç°ïYÖ±’ïÃ∞ÅçÖç°ï9’µï…•å∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÂÕ—ïµIïù•Õ—ï…	Öπ¨∞Å—…’î§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπ’µï…•çYÖ±’ïÃπÖëê°ïŸÖ±’Ö—ïâë	±Ωç≠9’µï…•å°ù…Ö¡°]•ëùï—Ã∞Åù…Ö¡°Ωππïç—•ΩπÃ∞ÅïëùîπÕΩ’…çî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹ÅâΩΩ±ïÖπmù…Ö¡°]•ëùï—ÃπÕ•Èî†•t∞ÅçÖç°ïYÖ±•ê∞ÅçÖç°ïYÖ±’ïÃ∞ÅçÖç°ï9’µï…•å∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÂÕ—ïµIïù•Õ—ï…	Öπ¨§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅïπÖâ±ïêÄÙÅôâë	ΩΩ±ïÖπA•πYÖ±’î°âΩΩ±ïÖπYÖ±’ïÃ∞Å¡•πÃ∞Äâ8à∞Ä¿§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖïπÖâ±ïê§ÅÏÅ¡’â±•Õ°ï»πç…Öô—5≈——1ÖÕ—πÖâ±îÄÙÅôÖ±ÕîÏÅçΩπ—•π’îÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°¡’â±•Õ°ï»πç…Öô—5≈——1ÖÕ—πÖâ±î§ÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ¡’â±•Õ°ï»πç…Öô—5≈——1ÖÕ—πÖâ±îÄÙÅ—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•π–Å—Â¡îÄÙÅµ≈——QÂ¡î°¡’â±•Õ°ï»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ¡ÖÂ±ΩÖêÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°—Â¡îÄÙÙÄƒ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ÖÂ±ΩÖêÄÙÅ	ΩΩ±ïÖ∏π—ΩM—…•πú°ôâë	ΩΩ±ïÖπA•πYÖ±’î°âΩΩ±ïÖπYÖ±’ïÃ∞Å¡•πÃ∞Äâ%8à∞Äƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅëΩ’â±îÅŸÖ±’îÄÙÅôâë9’µï…•çA•πYÖ±’î°π’µï…•çYÖ±’ïÃ∞Å¡•πÃ∞Äâ%8à∞Äƒ∞ÅΩ’â±îπ9Ö8§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ’â±îπ•Õ•π•—î°ŸÖ±’î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õç…ïïπ…Ö¡†§Å±•ŸïYÖ±’ïÃπ¡’–°—Ö…ùï–∞Äâ5EQPÅà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°—Â¡îÄÙÙÄ»§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ%π—ïùï»Åï·Öç–ÄÙÅâë…Öô—%π—ïùï…Mï±ïç—Ω»πï·Öç—%π—ïùï»°ŸÖ±’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ï·Öç–ÄÙÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õç…ïïπ…Ö¡†§Å±•ŸïYÖ±’ïÃπ¡’–°—Ö…ùï–∞Äâ5EQPÅà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ÖÂ±ΩÖêÄÙÅ%π—ïùï»π—ΩM—…•πú°ï·Öç–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–ÅπÖ……Ω›ïêÄÙÄ°ô±ΩÖ–§ÅŸÖ±’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö±ΩÖ–π•Õ•π•—î°πÖ……Ω›ïê§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õç…ïïπ…Ö¡†§Å±•ŸïYÖ±’ïÃπ¡’–°—Ö…ùï–∞Äâ5EQPÅà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ÖÂ±ΩÖêÄÙÅ±ΩÖ–π—ΩM—…•πú°πÖ……Ω›ïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ—Ω¡•åÄÙÅ¡’â±•Õ°ï»πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âç…Öô—5≈——QΩ¡•åà∞Äàà§π—…•¥†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕïπ–ÄÙÅ…’π—•µîπ¡’â±•Õ†°µ≈——Mï…Ÿï»°¡’â±•Õ°ï»§∞Å—Ω¡•å∞Å¡ÖÂ±ΩÖê∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡’â±•Õ°ï»πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—5≈——Iï—Ö•∏à∞ÅôÖ±Õî§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õç…ïïπ…Ö¡†§Å±•ŸïYÖ±’ïÃπ¡’–°—Ö…ùï–∞ÅÕïπ–Ä¸Äâ5EQPÉäHÄàÄ¨Å—Ω¡•åÄËÄâ5EQPÅΩôô±•πîà§Ï(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅôΩ»Ä°]•ëùï–ÅµÖç…ºÄËÅù…Ö¡°]•ëùï—Ã§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖÕïï∏πÖëê°µÖç…º§ÅÒÄÑâ	ÅIPÅ5I<àπï≈’Ö±Ã°çÖπΩπ•çÖ±âë	±Ωç≠QÂ¡î°µÖç…ºπ—Â¡î§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄÖïπÕ’…ï…Öô—5Öç…ΩI’π—•µï…Ö¡†°µÖç…º§§ÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•ÕΩ±Ö—ï…Öô—5Öç…ΩYÖ…•Öâ±ïÃ°µÖç…º∞ÅµÖç…ºπç…Öô—5Öç…ΩI’π—•µï]•ëùï—Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ¡’â±•Õ°…Öô—5≈——ëùïÕ%π…Ö¡†°…’π—•µî∞ÅµÖç…ºπç…Öô—5Öç…ΩI’π—•µï]•ëùï—Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµÖç…ºπç…Öô—5Öç…ΩI’π—•µïΩππïç—•ΩπÃ∞ÅÕïï∏∞Åëï¡—†Ä¨Äƒ∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÅÙ(ÄÄÄÅ¡…•ŸÖ—îÅç±ÖÕÃÅë•—Ω…ÖπŸÖÃÅï·—ïπëÃÅY•ï‹ÅÏ(ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅô•πÖ∞ÅAÖ•π–Å¡Ö•π–ÄÙÅπï‹ÅAÖ•π–°AÖ•π–π9Q%}1%M}1§Ï(ÄÄÄÄÄÄÄÅ•π–ÅÕï±ïç—ïêÄÙÄ¥ƒÏ(ÄÄÄÄÄÄÄÅô•πÖ∞Å……ÖÂ1•Õ–Ò%π—ïùï»¯ÅÕï±ïç—ïë]•ëùï—ÃÄÙÅπï‹Å……ÖÂ1•Õ–¯†§Ï(ÄÄÄÄÄÄÄÅô•πÖ∞Å1•π≠ïë!ÖÕ°5Ö¿Ò%π—ïùï»∞Åô±ΩÖ—mt¯Åë…Öù=…•ù•πÃÄÙÅπï‹Å1•π≠ïë!ÖÕ°5Ö¿¯†§Ï(ÄÄÄÄÄÄÄÅô±ΩÖ–ÅëΩ›π`∞ÅëΩ›πd∞ÅÕ—Ö…—`∞ÅÕ—Ö…—d∞ÅÕ—Ö…—\∞ÅÕ—Ö…— Ï(ÄÄÄÄÄÄÄÅô±ΩÖ–ÅµÖ…≈’ïï`∞ÅµÖ…≈’ïïdÏ(ÄÄÄÄÄÄÄÅô±ΩÖ–Åù…Ω’¡5•π`∞Åù…Ω’¡5•πd∞Åù…Ω’¡5Ö·`∞Åù…Ω’¡5Ö·dÏ(ÄÄÄÄÄÄÄÅ±ΩπúÅëΩ›π–Ï(ÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Åë…Öùù•πúÏ(ÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Å…ïÕ•È•πúÏ(ÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅµÖ…≈’ïïç—•ŸîÏ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅô•πÖ∞Å	•—µÖ¿Åç…Öô—ÖπŸÖÕ	Öç≠ë…Ω¿ÄÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ	•—µÖ¡Öç—Ω…‰πëïçΩëïIïÕΩ’…çî°ùï—IïÕΩ’…çïÃ†§∞ÅHπë…Ö›Öâ±îπ≠ç}µÖ•π}âÖç≠ù…Ω’πë}ÕµÖ±∞§Ï((ÄÄÄÄÄÄÄÅë•—Ω…ÖπŸÖÃ†§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÕ’¡ï»°5Ö•πç—•Ÿ•—‰π—°•Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÕï—	Öç≠ù…Ω’πëΩ±Ω»°Ω±Ω»π…ùà†‰»∞Ä‰»∞Ä‰»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÕï—]•±±9Ω—…Ö‹°ôÖ±Õî§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ=Ÿï……•ëîÅ¡…Ω—ïç—ïêÅŸΩ•êÅΩπ…Ö‹°ÖπŸÖÃÅçÖπŸÖÃ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÕ’¡ï»πΩπ…Ö‹°çÖπŸÖÃ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅMç…ïï∏Åç’……ïπ—Mç…ïï∏ÄÙÅÕç…ïïπÃπ•Õµ¡—‰†§Ä¸Åπ’±∞ÄËÅÕç…ïïπÃπùï–°ç’……ïπ—Mç…ïïπ%πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅÕç…ïïπΩ±Ω»ÄÙÅç’……ïπ—Mç…ïï∏ÄÙÙÅπ’±∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸Å…Öô—ÖπŸÖÕY•Õ’Ö±M—Â±îπ1e}1%!Q}	-I=U9ÄËÅç’……ïπ—Mç…ïï∏πâÖç≠ù…Ω’πëΩ±Ω»Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅâÖç≠ù…Ω’πë%µÖùîÄÙÅç’……ïπ—Mç…ïï∏ÄÙÙÅπ’±∞Ä¸Äàà(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÅç’……ïπ—Mç…ïï∏πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†ââÖç≠ù…Ω’πë%µÖùïAÖ—†à∞Äàà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ	•—µÖ¿ÅÕï±ïç—ïë	Öç≠ù…Ω’πêÄÙÅâÖç≠ù…Ω’πë%µÖùîπ•Õµ¡—‰†§Ä¸Åπ’±∞ÄËÅ±ΩÖëÖç°ïë	•—µÖ¿°âÖç≠ù…Ω’πë%µÖùî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅΩ…•ù•πÖ±ïôÖ’±–ÄÙÅ…Öô—ÖπŸÖÕY•Õ’Ö±M—Â±îπ’Õï=…•ù•πÖ±	Öç≠ë…Ω¿°Õç…ïïπΩ±Ω»∞ÅâÖç≠ù…Ω’πë%µÖùî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Ω…•ù•πÖ±ïôÖ’±–§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅç—’Ö∞Åâ’πë±ïêÅ…Öô–Å›Ö±±¡Ö¡ï»∞ÅπΩ–Å	1==M-dùÃÅôΩ…µï»Å›°•—îÅïë•—Ω»ÅÕ’…ôÖçî∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Ω±Ω»°Ω±Ω»π…ùà†‰»∞Ä‰»∞Ä‰»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ç…Öô—ÖπŸÖÕ	Öç≠ë…Ω¿ÄÑÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—•±—ï…	•—µÖ¿°—…’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›	•—µÖ¿°ç…Öô—ÖπŸÖÕ	Öç≠ë…Ω¿∞Åπ’±∞∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹ÅIïç–†¿∞Ä¿∞Åùï—]•ë—††§∞Åùï—!ï•ù°–†§§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—•±—ï…	•—µÖ¿°ôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅ·¡±•ç•—±‰ÅÕï±ïç—ïêÅÕç…ïï∏ÅçΩ±Ω»ÄºÅ¡…•ŸÖ—îµ¡…Ω©ïç–Åâ•—µÖ¿Å•ÃÅÖ’—°Ω…•—Ö—•Ÿî∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Ω±Ω»°Õç…ïïπΩ±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õï±ïç—ïë	Öç≠ù…Ω’πêÄÑÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—•±—ï…	•—µÖ¿°—…’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›	•—µÖ¿°Õï±ïç—ïë	Öç≠ù…Ω’πê∞Åπ’±∞∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹ÅIïç–†¿∞Ä¿∞Åùï—]•ë—††§∞Åùï—!ï•ù°–†§§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—•±—ï…	•—µÖ¿°ôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄººÅQ°îÅ…Öô–ÅÕç…ïï∏ÅÕ°Ω›∏Åâ‰Å—°îÅ’Õï»Å°ÖÃÅπºÅçΩπÕ¡•ç’Ω’ÃÅâ±’îΩ›°•—î(ÄÄÄÄÄÄÄÄÄÄÄÄººÅëïÕ•ù∏Åù…•ê∏Å-ïï¿ÅÕπÖ¿µ—ºµù…•êÅâ’–Å…ïπëï»Åù’•ëïÃÅΩπ±‰Å›°ï∏Å…ï≈’ïÕ—ïê∏(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…Öô—ÖπŸÖÕY•Õ’Ö±M—Â±îπÕ°Ω›ïÕ•ùπ…•ê°Ω¡ï…Ö—Ω…5Ωëî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡…Ω©ïç—·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†ââ±ΩΩëÕ≠ÂM°Ω›ïÕ•ùπ…•êà∞ÅôÖ±Õî§§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»πÖ…ùà†–‘∞Ä»ƒ¿∞Ä»ƒ¿∞Ä»ƒ¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—…Ω≠ï]•ë—††ƒ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–Åù…•êÄÙÅë¿†»¿§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å‡ÄÙÄ¿ÏÅ‡ÄÅùï—]•ë—††§ÏÅ‡Ä¨ÙÅù…•ê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›1•πî°‡∞Ä¿∞Å‡∞Åùï—!ï•ù°–†§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å‰ÄÙÄ¿ÏÅ‰ÄÅùï—!ï•ù°–†§ÏÅ‰Ä¨ÙÅù…•ê§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›1•πî†¿∞Å‰∞Åùï—]•ë—††§∞Å‰∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ¡ï…Ö—Ω…5ΩëîÄòòÄÖÕç…ïïπÃπ•Õµ¡—‰†§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅMç…ïï∏ÅÕç…ïï∏ÄÙÅÕç…ïïπÃπùï–°ç’……ïπ—Mç…ïïπ%πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ»–∞Ä‘‡∞Ä»Ã‹§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—…Ω≠ï]•ë—†°ë¿†»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπMQI=-§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°âëΩππïç—•Ω∏ÅçΩππïç—•Ω∏ÄËÅÕç…ïï∏πçΩππïç—•ΩπÃ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°çΩππïç—•Ω∏πÕΩ’…çîÄÄ¿ÅÒÅçΩππïç—•Ω∏π—Ö…ùï–ÄÄ¿ÅÒÅçΩππïç—•Ω∏πÕΩ’…çîÄ¯ÙÅ›•ëùï—ÃπÕ•Èî†§ÅÒÅçΩππïç—•Ω∏π—Ö…ùï–Ä¯ÙÅ›•ëùï—ÃπÕ•Èî†§§ÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Åô…Ω¥ÄÙÅ›•ëùï—Ãπùï–°çΩππïç—•Ω∏πÕΩ’…çî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Å—ºÄÙÅ›•ëùï—Ãπùï–°çΩππïç—•Ω∏π—Ö…ùï–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Öô…Ω¥πŸ•Õ•â±îÅÒÄÖ—ºπŸ•Õ•â±îÅÒÄÖ•Õâê°ô…Ω¥§ÅÒÄÖ•Õâê°—º§§ÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å‡ƒÄÙÅô…Ω¥π‡Ä¨Åô…Ω¥π‹Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å‰ƒÄÙÅô…Ω¥π‰Ä¨Åô…Ω¥π†Ä®ÅôâëQï…µ•πÖ±…Öç—•Ω∏°ô…Ω¥∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩππïç—•Ω∏πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âÕΩ’…çïA•∏à∞Äàà§∞Å—…’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å‡»ÄÙÅ—ºπ‡Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å‰»ÄÙÅ—ºπ‰Ä¨Å—ºπ†Ä®ÅôâëQï…µ•πÖ±…Öç—•Ω∏°—º∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩππïç—•Ω∏πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†â—Ö…ùï—A•∏à∞Äàà§∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°•Õâë	ΩΩ±ïÖπ]•…ï%πŸï…—ïê°çΩππïç—•Ω∏∞Åô…Ω¥π—Â¡î∞Å—ºπ—Â¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÅΩ±Ω»π…ùà†»Ã–∞Ä‡‡∞Äƒ»§ÄËÅΩ±Ω»π…ùà†ƒ»–∞Ä‘‡∞Ä»Ã‹§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Åµ•êÄÙÄ°‡ƒÄ¨Å‡»§ÄºÄ…òÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›1•πî°‡ƒ∞Å‰ƒ∞Åµ•ê∞Å‰ƒ∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›1•πî°µ•ê∞Å‰ƒ∞Åµ•ê∞Å‰»∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›1•πî°µ•ê∞Å‰»∞Å‡»∞Å‰»∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅôâëÖç°ïYÖ±•êÄÙÅπï‹ÅâΩΩ±ïÖπm›•ëùï—ÃπÕ•Èî†•tÏ(ÄÄÄÄÄÄÄÄÄÄÄÅôâëÖç°ïYÖ±’ïÃÄÙÅπï‹ÅâΩΩ±ïÖπm›•ëùï—ÃπÕ•Èî†•tÏ(ÄÄÄÄÄÄÄÄÄÄÄÅôâëÖç°ï9’µï…•åÄÙÅπï‹ÅëΩ’â±ïm›•ëùï—ÃπÕ•Èî†•tÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Ω¡ï…Ö—Ω…5Ωëî§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å§ÄÙÄ¿ÏÅ§ÄÅ›•ëùï—ÃπÕ•Èî†§ÏÅ§¨¨§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°•Õâê°›•ëùï—Ãπùï–°§§§§ÅïŸÖ±’Ö—ïâê°§∞Åπï‹ÅâΩΩ±ïÖπm›•ëùï—ÃπÕ•Èî†•t§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å§ÄÙÄ¿ÏÅ§ÄÅ›•ëùï—ÃπÕ•Èî†§ÏÅ§¨¨§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–ÅÕ•π¨ÄÙÅ›•ëùï—Ãπùï–°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°•Õ…Öô—Iïù•Õ—ï…M•π¨°Õ•π¨π—Â¡î§§ÅÕç°ïë’±ï…Öô—Iïù•Õ—ï…]…•—î°Õ•π¨∞Å§∞ÅôâëÖç°ï9’µï…•çm•t§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡’â±•Õ°…Öô—5≈——ëùïÃ†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄººÅôâêËººÅ›•ëùï–Åâ•πë•πùÃÅ…ïµÖ•∏Å…ïÖêµΩπ±‰ÏÅï·¡±•ç•–ÅYÖ…=’–Åâ±Ωç≠ÃÅ›…•—îÅΩπ±‰Å•∏ÅΩ¡ï…Ö—Ω»ÅµΩëî∏(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å§ÄÙÄ¿ÏÅ§ÄÅ›•ëùï—ÃπÕ•Èî†§ÏÅ§¨¨§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Å—Ö…ùï–ÄÙÅ›•ëùï—Ãπùï–°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°•Õâê°—Ö…ùï–§ÅÒÅ—Ö…ùï–πçΩππïç—•Ωπ%πôºÄÙÙÅπ’±∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄÖ—Ö…ùï–πçΩππïç—•Ωπ%πôºπÕ—Ö…—Õ]•—††âôâêËººà§§ÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Ω¡ï…Ö—Ω…5Ωëî§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅŸÖ±’îÄÙÅâëIïÕ’±—	•πë•πúπ…ïÕΩ±Ÿî°—Ö…ùï–πçΩππïç—•Ωπ%πôº∞Å›•ëùï—Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôâëÖç°ïYÖ±•ê∞ÅôâëÖç°ïYÖ±’ïÃ∞ÅôâëÖç°ï9’µï…•å§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ±•ŸïYÖ±’ïÃπ¡’–°§∞ÅŸÖ±’îÄÙÙÅπ’±∞Ä¸ÄâàÄËÅŸÖ±’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ±•ŸïYÖ±’ïÃπ…ïµΩŸî°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å§ÄÙÄ¿ÏÅ§ÄÅ›•ëùï—ÃπÕ•Èî†§ÏÅ§¨¨§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Å‹ÄÙÅ›•ëùï—Ãπùï–°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö‹πŸ•Õ•â±îÅÒÄ°Ω¡ï…Ö—Ω…5ΩëîÄòòÄ†ãBkB˜B˚BˇBÎB¿àπï≈’Ö±Ã°‹π—Â¡î§ÅÒÄãBkB˚B˜FFB√B˜FB˜B√F<ÉBÎB˜B˚BˇBÎB¿Å…Öô–àπï≈’Ö±Ã°‹π—Â¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄãBB◊FB◊FB˚B–ÉB˜B¿ÉF7BÎFB√BÙàπï≈’Ö±Ã°‹π—Â¡î§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÄÖ‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—	’——ΩπY•Õ•â±ï%π]Ω…¨à∞Å—…’î§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄ°Ω¡ï…Ö—Ω…5ΩëîÄòòÅ=…•ù•πÖ±…Öô—M›•—ç†πÕ’¡¡Ω…—Ã°‹π—Â¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÄÖ‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—M›•—ç°Y•Õ•â±ï%π]Ω…¨à∞Å=…•ù•πÖ±…Öô—M›•—ç†πŸ•Õ•â±ï%π]Ω…¨°‹πï·—…Ö•ï±ëÃ§§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄ°Ω¡ï…Ö—Ω…5ΩëîÄòòÅ•Õâê°‹§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄ°Ω¡ï…Ö—Ω…5ΩëîÄòòÄãBáBˇB„FB˚BËÅ…Öô–àπï≈’Ö±Ã°‹π—Â¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÄÖ‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—1•Õ—Y•Õ•â±ï%π]Ω…¨à∞Å—…’î§§§ÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅIïç—Å…ïç–ÄÙÅπï‹ÅIïç—°‹π‡∞Å‹π‰∞Å‹π‡Ä¨Å‹π‹∞Å‹π‰Ä¨Å‹π†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πâÖç≠ù…Ω’πëΩ±Ω»ÄÑÙÄ¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹πâÖç≠ù…Ω’πëΩ±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°‹π—Â¡îÄÑÙÅπ’±∞ÄòòÅ‹π—Â¡îπÕ—Ö…—Õ]•—††â	Äà§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»Ã‹∞Ä»ÃÃ∞Ä»‘–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBcB˜B”B„BÎB√FB˚F àπï≈’Ö±Ã°‹π—Â¡î§ÅÒÄãBSBÀFFBˇB˚BﬂB„FB„B˚B˜B˜F/B‰ÉB„B˜B”B„BÎB√FB˚F àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»»¿∞Ä»‘»∞Ä»Ãƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°=…•ù•πÖ±…Öô—M›•—ç†πÕ’¡¡Ω…—Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»πQI9MAI9P§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBkB˜B˚BˇBÎB¿àπï≈’Ö±Ã°‹π—Â¡î§ÅÒÄãBkB˚B˜FFB√B˜FB˜B√F<ÉBÎB˜B˚BˇBÎB¿Å…Öô–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹πâ’——ΩπM—Ö—îÄ¸ÅΩ±Ω»π…ùà†ƒ‡‹∞Ä»–‹∞Ä»¿‡§ÄËÅΩ±Ω»π…ùà†»ƒ‰∞Ä»Ã–∞Ä»‘–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãB_B√B”B√FFB„BËàπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»‘–∞Ä»–Ã∞Äƒ‰‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãB£BÎB√BÔB¿ÄºÉBáFFB◊BÔBÎB¿àπï≈’Ö±Ã°‹π—Â¡î§ÅÒÄãBFB˚BœFB◊FF∑B«B√F àπï≈’Ö±Ã°‹π—Â¡î§ÅÒÄãBGB„FB˚BÀF/B‰ÉFBˇB„FB˚BËàπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»–ƒ∞Ä»–‘∞Ä»–‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBOFB√FB„BËÄºÉBãFB◊B˜B–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ‘∞Ä»Ã∞Ä–»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π]!%Q§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅÅÕΩ’…çîµâÖç≠ïêÅ	ÅâΩë‰ÅÖ±…ïÖë‰Å¡Ö•π—ÃÅ•—ÃÅΩ›∏ÅÕ≈’Ö…î(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅΩ’—±•πîÏÅπïŸï»Å±ïÖŸîÅ—°îÅôΩ…µï»Å¡’…¡±îÅ…Ω’πëïêÅ…ïç—Öπù±îÅ°Ö±º∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕΩ’…çïâëM≠•∏ÄÙÅâëMΩ’…çïÖπŸÖÕM≠•∏πÕ°Ω’±ëUÕî°‹§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕΩ’…çï…Öô—M›•—ç†ÄÙÅ=…•ù•πÖ±…Öô—M›•—ç†πÕ’¡¡Ω…—Ã°‹π—Â¡î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ÕΩ’…çï…Öô—M›•—ç†§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ=…•ù•πÖ±…Öô—M›•—ç°Iïπëï…ï»πë…Ö‹°çÖπŸÖÃ∞Åùï—IïÕΩ’…çïÃ†§∞Å…ïç–∞Å‹πï·—…Ö•ï±ëÃ∞Å‹πâ’——ΩπM—Ö—î∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö=…•ù•πÖ±%πë•çÖ—Ω»πÕ’¡¡Ω…—Ã°‹π—Â¡î§ÄòòÄÖÕΩ’…çïâëM≠•∏ÄòòÄÖÕΩ’…çï…Öô—M›•—ç†(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ…Öô—1Öâï±¡¡ïÖ…Öπçîπë…Ö›	Öç≠ù…Ω’πê°‹π—Â¡î∞Å‹πâÖç≠ù…Ω’πëΩ±Ω»§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ…Öô—ÂπÖµ•çQï·—A…Ω¡ï…—•ïÃπë…Ö›	Öç≠ù…Ω’πê°‹π—Â¡î∞Å‹πâÖç≠ù…Ω’πëΩ±Ω»§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°…ïç–∞Åë¿†ÿ§∞Åë¿†ÿ§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ††Ö=…•ù•πÖ±%πë•çÖ—Ω»πÕ’¡¡Ω…—Ã°‹π—Â¡î§ÄòòÅ‹πÕ°Ω›…Öµî(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÄÖÕΩ’…çïâëM≠•∏ÄòòÄÖÕΩ’…çï…Öô—M›•—ç†§ÅÒÅ§ÄÙÙÅÕï±ïç—ïêÅÒÅÕï±ïç—ïë]•ëùï—ÃπçΩπ—Ö•πÃ°§§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπMQI=-§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—…Ω≠ï]•ë—†°ë¿°§ÄÙÙÅÕï±ïç—ïêÄ¸Ä»∏’òÄËÄƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°§ÄÙÙÅÕï±ïç—ïêÅÒÅÕï±ïç—ïë]•ëùï—ÃπçΩπ—Ö•πÃ°§§Ä¸ÅΩ±Ω»π…ùà†»ƒ∞Äƒ¿ƒ∞Äƒ‰»§ÄËÅΩ±Ω»π…ùà†ƒ–‡∞ÄƒÿÃ∞Äƒ‡–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°…ïç–∞Åë¿†ÿ§∞Åë¿†ÿ§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°§ÄÙÙÅÕï±ïç—ïêÄòòÅÕï±ïç—ïë]•ëùï—ÃπÕ•Èî†§ÄÙÄƒ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»ƒ∞Äƒ¿ƒ∞Äƒ‰»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Åù…•¿ÄÙÅë¿†‰§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Iïç–°‹π‡Ä¨Å‹π‹Ä¥Åù…•¿∞Å‹π‰Ä¨Å‹π†Ä¥Åù…•¿∞Å‹π‡Ä¨Å‹π‹∞Å‹π‰Ä¨Å‹π†∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿°‹π—ï·—M•ÈïM¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ÕΩ’…çï…Öô—M›•—ç†§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅQ°îÅÕΩ’…çîÅÕ›•—ç†Å•ÃÅÑÅ¡Ö•»ÅΩòÅô’±∞µÕ•ÈîÅΩ…•ù•πÖ∞Åâ•—µÖ¡ÃÏÅ•–Å°ÖÃÅπºÅ—ï·–ÅΩŸï…±Ö‰∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°‹π—Â¡îÄÑÙÅπ’±∞ÄòòÅ‹π—Â¡îπÕ—Ö…—Õ]•—††â	Äà§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°âëMΩ’…çïÖπŸÖÕM≠•∏πÕ°Ω’±ëUÕî°‹§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâëMΩ’…çïÖπŸÖÕM≠•∏πë…Ö‹°çÖπŸÖÃ∞Å¡Ö•π–∞Å‹∞Åùï—IïÕΩ’…çïÃ†§πùï—•Õ¡±ÖÂ5ï—…•çÃ†§πëïπÕ•—‰§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°§ÄÙÙÅÕï±ïç—ïêÅÒÅÕï±ïç—ïë]•ëùï—ÃπçΩπ—Ö•πÃ°§§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπMQI=-§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—…Ω≠ï]•ë—†°ë¿†»∏’ò§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»ƒ∞Äƒ¿ƒ∞Äƒ‰»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Iïç–°…ïç–∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Åù…•¿ÄÙÅë¿†‰§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°§ÄÙÙÅÕï±ïç—ïêÄòòÅÕï±ïç—ïë]•ëùï—ÃπÕ•Èî†§ÄÙÄƒ§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Iïç–°‹π‡Ä¨Å‹π‹Ä¥Åù…•¿∞Å‹π‰Ä¨Å‹π†Ä¥Åù…•¿∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Å‹π‹∞Å‹π‰Ä¨Å‹π†∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿°‹π—ï·—M•ÈïM¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πÖ’—ΩIïÕ•ÈïQï·–§Å¡Ö•π–πÕï—Qï·—M•Èî°5Ö—†πµ•∏°¡Ö•π–πùï—Qï·—M•Èî†§∞Å5Ö—†πµÖ‡°ë¿†‡§∞Ä°‹π‹Ä¥Åë¿†»–§§ÄºÅ5Ö—†πµÖ‡†—ò∞Å‹π—ï·–π±ïπù—††§Ä®Ä¿∏‘·ò§§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅâ±Ωç≠Q•—±îÄÙÄâ	ÉBKFB˚B–àπï≈’Ö±Ã°‹π—Â¡î§Ä¸ÄãBKFB˚B–ÄàÄ¨Ä°‹π•π¡’—YÖ±’îÄ¸ÄàƒàÄËÄà¿à§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÄ†â	ÅIPÅQTàπï≈’Ö±Ã°‹π—Â¡î§Ä¸ÄâQTƒÿÄàÄ¨Å‹πçΩ’π—ï…YÖ±’î(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÄ††â	ÅQTàπï≈’Ö±Ã°‹π—Â¡î§ÅÒÄâ	ÅQàπï≈’Ö±Ã°‹π—Â¡î§§Ä¸Ä†â	ÅQàπï≈’Ö±Ã°‹π—Â¡î§Ä¸ÄâQÄàÄËÄâQTÄà§Ä¨Å‹πçΩ’π—ï…YÖ±’îÄ¨ÄàºàÄ¨Å‹πçΩ’π—ï…A…ïÕï–ÄËÅ‹π—ï·–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°±•ŸïYÖ±’ïÃπçΩπ—Ö•πÕ-ï‰°§§ÄòòÄÑâ	ÉBKFB˚B–àπï≈’Ö±Ã°‹π—Â¡î§§Åâ±Ωç≠Q•—±îÄ¨ÙÄàÅlàÄ¨Å±•ŸïYÖ±’ïÃπùï–°§§Ä¨ÄâtàÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°â±Ωç≠Q•—±î∞Å‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Åë¿†»Ã§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅ9ïŸï»ÅïŸÖ±’Ö—îÅÕ—Ö—ïô’∞Å	Å±Ωù•åÅô…Ω¥Å—°îÅïë•—Ω»ùÃÅ¡Ö•π–Å¡ÖÕÃË(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅë…Ö›•πúÅÖ±ΩπîÅµ’Õ–ÅπΩ–ÅçΩ’π–ÅïëùïÃÅΩ»ÅÖëŸÖπçîÅQ=8ΩQ=ΩQ@∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§ÏÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ¿¿∞Äƒƒÿ∞ÄƒÃ‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–†â=UPÄÙÉäPà∞Å‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†‡§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒ¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ¿‰∞Ä–¿∞Ä»ƒ‹§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πùmtÅâ±Ωç≠%π¡’—ÃÄÙÅ≠πΩ›πâëA•πÃ°‹∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°â±Ωç≠%π¡’—Ãπ±ïπù—†ÄÙÙÄ¿§Åâ±Ωç≠%π¡’—ÃÄÙÅπï‹ÅM—…•πùmuÏàà∞ÄàâÙÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å¡•π%πëï‡ÄÙÄ¿ÏÅ¡•π%πëï‡ÄÅâ±Ωç≠%π¡’—Ãπ±ïπù—†ÏÅ¡•π%πëï‡¨¨§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å¡‰ÄÙÅ‹π‰Ä¨Å‹π†Ä®Ä°â±Ωç≠%π¡’—Õm¡•π%πëï·tπ•Õµ¡—‰†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸Ä°¡•π%πëï‡Ä¨Ä≈ò§ÄºÄ°â±Ωç≠%π¡’—Ãπ±ïπù—†Ä¨Ä≈ò§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÅôâëQï…µ•πÖ±…Öç—•Ω∏°‹∞Åâ±Ωç≠%π¡’—Õm¡•π%πëï·t∞ÅôÖ±Õî§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›1•πî°‹π‡∞Å¡‰∞Å‹π‡Ä¨Åë¿†ƒ–§∞Å¡‰∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›•…ç±î°‹π‡Ä¨Åë¿†–§∞Å¡‰∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Öâ±Ωç≠%π¡’—Õm¡•π%πëï·tπ•Õµ¡—‰†§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°â±Ωç≠%π¡’—Õm¡•π%πëï·t∞Å‹π‡Ä¨Åë¿†ƒ‹§∞Å¡‰Ä¥Åë¿†Ã§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πùmtÅâ±Ωç≠=’—¡’—ÃÄÙÅ≠πΩ›πâëA•πÃ°‹∞Å—…’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°â±Ωç≠=’—¡’—Ãπ±ïπù—†ÄÙÙÄ¿§Åâ±Ωç≠=’—¡’—ÃÄÙÅπï‹ÅM—…•πùmuÏàâÙÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å¡•π%πëï‡ÄÙÄ¿ÏÅ¡•π%πëï‡ÄÅâ±Ωç≠=’—¡’—Ãπ±ïπù—†ÏÅ¡•π%πëï‡¨¨§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å¡‰ÄÙÅ‹π‰Ä¨Å‹π†Ä®Ä°â±Ωç≠=’—¡’—Õm¡•π%πëï·tπ•Õµ¡—‰†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸Ä°¡•π%πëï‡Ä¨Ä≈ò§ÄºÄ°â±Ωç≠=’—¡’—Ãπ±ïπù—†Ä¨Ä≈ò§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÅôâëQï…µ•πÖ±…Öç—•Ω∏°‹∞Åâ±Ωç≠=’—¡’—Õm¡•π%πëï·t∞Å—…’î§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›1•πî°‹π‡Ä¨Å‹π‹Ä¥Åë¿†ƒ–§∞Å¡‰∞Å‹π‡Ä¨Å‹π‹∞Å¡‰∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›•…ç±î°‹π‡Ä¨Å‹π‹Ä¥Åë¿†–§∞Å¡‰∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°=…•ù•πÖ±…Öô—9’µï…•ç%πë•çÖ—Ω»πQeAπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅŸÖ±’îÄÙÅ…Öô—9’µï…•ç%πë•çÖ—Ω…•Õ¡±Ö‰πŸÖ±’î°±•ŸïYÖ±’ïÃπùï–°§§∞ÅΩ¡ï…Ö—Ω…5Ωëî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹πçΩππïç—•Ωπ%πôºÄÑÙÅπ’±∞ÄòòÄÖ‹πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅôÖ•±ïêÄÙÅ…Öô—9’µï…•ç%πë•çÖ—Ω…•Õ¡±Ö‰π’πÖŸÖ•±Öâ±î°ŸÖ±’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖπë…Ω•êπù…Ö¡°•çÃπQÂ¡ïôÖçîÅ¡…ïŸ•Ω’ÕQÂ¡ïôÖçîÄÙÅ¡Ö•π–πùï—QÂ¡ïôÖçî†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖπë…Ω•êπù…Ö¡°•çÃπAÖ•π–π±•ù∏Å¡…ïŸ•Ω’Õ±•ù∏ÄÙÅ¡Ö•π–πùï—Qï·—±•ù∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—9’µï…•ç%πë•çÖ—Ω…Ωπ—°Öπùîà∞ÅôÖ±Õî§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏÅ¡Ö•π–πÕï—QÂ¡ïôÖçî°Öπë…Ω•êπù…Ö¡°•çÃπQÂ¡ïôÖçîπç…ïÖ—ï…ΩµÕÕï–†(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ5Ö•πç—•Ÿ•—‰π—°•Ãπùï—ÕÕï—Ã†§∞ÄâôΩπ—ÃΩÕô}ë•ù•—Ö±}…ïÖëΩ’—}°ïÖŸ‰π——òà§§ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖ—ç†Ä°·çï¡—•Ω∏Åµ•ÕÕ•πùΩπ–§ÅÏÄº®ÅUÕîÅ—°îÅ¡±Ö—ôΩ…¥ÅôΩπ–Å•òÅ—°îÅÕΩ’…çîÅôΩπ–Å•ÃÅ’πÖŸÖ•±Öâ±î∏Ä®ºÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—9’µï…•ç%πë•çÖ—Ω……Öµîà∞ÅôÖ±Õî§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπMQI=-§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—…Ω≠ï]•ë—†°ë¿†ƒ§§ÏÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Iïç–°…ïç–∞Å¡Ö•π–§ÏÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°ôÖ•±ïêÄ¸ÅΩ±Ω»π…ùà†ƒ‡‘∞Ä»‡∞Ä»‡§ÄËÅ‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿°‹π—ï·—M•ÈïM¿§§ÏÅ¡Ö•π–πÕï—Qï·—±•ù∏°AÖ•π–π±•ù∏π9QH§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°ŸÖ±’î∞Å‹π‡Ä¨Å‹π‹ÄºÄ…ò∞Å‹π‰Ä¨Å‹π†ÄºÄ…òÄ¨Åë¿†‘§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—±•ù∏°¡…ïŸ•Ω’Õ±•ù∏§ÏÅ¡Ö•π–πÕï—QÂ¡ïôÖçî°¡…ïŸ•Ω’ÕQÂ¡ïôÖçî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBSB„B˜B√BÛB„FB◊FBÎB„B‰ÉFB◊BÎFFàπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅÕ—Ö—ï%ë‡ÄÙÅΩ¡ï…Ö—Ω…5Ωëî(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸Å…Öô—!µ•M—Ö—ïM•ùπÖ∞πëïçΩëî°±•ŸïYÖ±’ïÃπùï–°§§§ÄËÄ¿Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅë•—Ω»Å¡…ïŸ•ï‹ÅëïôÖ’±—ÃÅ—ºÅÕ—Ö—îÅÈï…ºÏÅΩ¡ï…Ö—Ω»ÅµΩëî(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅÕ°Ω›ÃÅÖ∏Å’πÖŸÖ•±Öâ±îÅÕ—Ö—’ÃÅ…Ö—°ï»Å—°Ö∏ÅÑÅôÖ±ÕîÅÕ—Ö—î∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅÕ—Ö—ïQï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õ—Ö—ï%ë‡ÄÄ¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕ—Ö—ïQï·–ÄÙÄãBwB◊FÉFBÀF?BﬂB‡àÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕ—Ö—ïQï·–ÄÙÅ‹πëÂπQï·—M—Ö—ïÕmÕ—Ö—ï%ë·tÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õ—Ö—ïQï·–ÄÙÙÅπ’±∞ÅÒÅÕ—Ö—ïQï·–π•Õµ¡—‰†§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕ—Ö—ïQï·–ÄÙÅ‹π—ï·–Ä¨ÄàÅlàÄ¨ÅÕ—Ö—ï%ë‡Ä¨ÄâtàÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅâùΩ±Ω»ÄÙÅ‹πëÂπQï·—Ω±Ω…ÕmÕ—Ö—ï%ë·tÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°âùΩ±Ω»ÄÑÙÄ¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°âùΩ±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°…ïç–∞Åë¿†ÿ§∞Åë¿†ÿ§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πÖ’—ΩIïÕ•ÈïQï·–§Å¡Ö•π–πÕï—Qï·—M•Èî°5Ö—†πµ•∏°¡Ö•π–πùï—Qï·—M•Èî†§∞Å5Ö—†πµÖ‡°ë¿†‡§∞Ä°‹π‹Ä¥Åë¿†»¿§§ÄºÅ5Ö—†πµÖ‡†—ò∞ÅÕ—Ö—ïQï·–π±ïπù—††§Ä®Ä¿∏‘·ò§§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°Õ—Ö—ïQï·–∞Å‹π‡Ä¨Åë¿†ƒ¿§∞Å‹π‰Ä¨Å‹π†ÄºÄ»Ä¨Åë¿†‘§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBüB„FBÔB˚BÀB˚B‰ÉBÀBÀB˚B–Å…Öô–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅŸÖ±’îÄÙÅ…Öô—9’µï…•ç%π¡’—•Õ¡±Ö‰πŸÖ±’î°±•ŸïYÖ±’ïÃπùï–°§§∞Å‹π—ï·–∞ÅΩ¡ï…Ö—Ω…5Ωëî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹πçΩππïç—•Ωπ%πôºÄÑÙÅπ’±∞ÄòòÄÖ‹πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅŸÖ±’ïUπÖŸÖ•±Öâ±îÄÙÅ…Öô—9’µï…•ç%π¡’—•Õ¡±Ö‰π’πÖŸÖ•±Öâ±î°ŸÖ±’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖπë…Ω•êπù…Ö¡°•çÃπQÂ¡ïôÖçîÅ¡…ïŸ•Ω’ÕQÂ¡ïôÖçîÄÙÅ¡Ö•π–πùï—QÂ¡ïôÖçî†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—9’µï…•çMÂÕ—ïµΩπ–à∞ÅôÖ±Õî§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏÅ¡Ö•π–πÕï—QÂ¡ïôÖçî°Öπë…Ω•êπù…Ö¡°•çÃπQÂ¡ïôÖçîπç…ïÖ—ï…ΩµÕÕï–†(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ5Ö•πç—•Ÿ•—‰π—°•Ãπùï—ÕÕï—Ã†§∞ÄâôΩπ—ÃΩÕô}ë•ù•—Ö±}…ïÖëΩ’—}°ïÖŸ‰π——òà§§ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖ—ç†Ä°·çï¡—•Ω∏Åµ•ÕÕ•πùΩπ–§ÅÏÄº®Å-ïï¿Å—°îÅ¡±Ö—ôΩ…¥ÅôÖ±±âÖç¨Å•òÅ—°îÅÖÕÕï–Å•ÃÅ’πÖŸÖ•±Öâ±î∏Ä®ºÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°ŸÖ±’ïUπÖŸÖ•±Öâ±îÄ¸ÅΩ±Ω»π…ùà†ƒ‡‘∞Ä»‡∞Ä»‡§ÄËÅ‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿°‹π—ï·—M•ÈïM¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—±•ù∏°AÖ•π–π±•ù∏π9QH§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°ŸÖ±’î∞Å‹π‡Ä¨Å‹π‹ÄºÄ…ò∞Å‹π‰Ä¨Å‹π†ÄºÄ…òÄ¨Åë¿†‘§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—±•ù∏°AÖ•π–π±•ù∏π1P§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—QÂ¡ïôÖçî°¡…ïŸ•Ω’ÕQÂ¡ïôÖçî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBáBˇB„FB˚BËÅ…Öô–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅµÖÕ¨ÄÙÅ‹πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—1•Õ—%π•—•Ö±5ÖÕ¨à∞Ä¿§ÄòÄ¡·ôôôòÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ±•Ÿï5ÖÕ¨ÄÙÅ±•ŸïYÖ±’ïÃπùï–°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅëïçΩëïë5ÖÕ¨ÄÙÅ…Öô—!µ•M—Ö—ïM•ùπÖ∞πëïçΩëï]Ω…ê°±•Ÿï5ÖÕ¨§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄÙÅ…Öô—!µ•M—Ö—ïM•ùπÖ∞π’πÖŸÖ•±Öâ±ï]Ω…ê°±•Ÿï5ÖÕ¨∞ÅΩ¡ï…Ö—Ω…5Ωëî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹πçΩππïç—•Ωπ%πôºÄÑÙÅπ’±∞ÄòòÄÖ‹πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ëïçΩëïë5ÖÕ¨Ä¯ÙÄ¿§ÅµÖÕ¨ÄÙÅëïçΩëïë5ÖÕ¨Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πùmtÅ±Öâï±ÃÄÙÅ…Öô—	•—1•Õ–πô…Ωµ)ÕΩ∏°‹πï·—…Ö•ï±ëÃπΩ¡—)M=9……Ö‰†âç…Öô—1•Õ—1Öâï±Ãà§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§ÏÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒ»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹π—ï·–Ä¨ÄàÄÄàÄ¨Ä°Õ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸ÄãBwB◊FÉFBÀF?BﬂB‡àÄËÅM—…•πúπôΩ…µÖ–°©ÖŸÑπ’—•∞π1ΩçÖ±îπI==P∞Äà¡‡î¿—`à∞ÅµÖÕ¨§§∞Å‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Åë¿†ƒ‹§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒ¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πù	’•±ëï»Åç°ΩÕï∏ÄÙÅπï‹ÅM—…•πù	’•±ëï»†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Åâ•–ÄÙÄ¿ÏÅâ•–ÄÄƒÿÏÅâ•–¨¨§Å•òÄ†°µÖÕ¨ÄòÄ†ƒÄÅâ•–§§ÄÑÙÄ¿ÄòòÅ±Öâï±Õmâ•—tÄÑÙÅπ’±∞ÄòòÄÖ±Öâï±Õmâ•—tπ•Õµ¡—‰†§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ç°ΩÕï∏π±ïπù—††§Ä¯Ä¿§Åç°ΩÕï∏πÖ¡¡ïπê†à∞Äà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅç°ΩÕï∏πÖ¡¡ïπê°±Öâï±Õmâ•—t§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅÕ’µµÖ…‰ÄÙÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸ÄãBwB◊FÉFBÀF?BﬂB‡àÄËÅç°ΩÕï∏π±ïπù—††§ÄÙÙÄ¿Ä¸ÄãBKF/B«FB√FF0ÉBˇFB˜BÎFF,àÄËÅç°ΩÕï∏π—ΩM—…•πú†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õ’µµÖ…‰π±ïπù—††§Ä¯Äÿ–§ÅÕ’µµÖ…‰ÄÙÅÕ’µµÖ…‰πÕ’âÕ—…•πú†¿∞Äÿƒ§Ä¨ÄãäòàÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°Õ’µµÖ…‰∞Å‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Åë¿†Ã–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õ•ùπÖ±UπÖŸÖ•±Öâ±î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ‡‘∞Ä»‡∞Ä»‡§§ÏÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–†ãBwB◊FÉFBÀF?BﬂB‡à∞Å‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†‘§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBoB„B˜B◊BÁB˜F/B‰ÉBﬂB√B”B√FFB„BËÅ…Öô–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅµÖ·•µ’¥ÄÙÅ…Öô—1•πïÖ…Mï——ï»πµÖ·•µ’¥°‹πï·—…Ö•ï±ëÃ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ±•πïÖ…M—Ö—’ÃÄÙÅ±•ŸïYÖ±’ïÃπùï–°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄÙÅ…Öô—1•πïÖ…Mï——ï»π’πÖŸÖ•±Öâ±î°±•πïÖ…M—Ö—’Ã∞ÅΩ¡ï…Ö—Ω…5Ωëî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹πçΩππïç—•Ωπ%πôºÄÑÙÅπ’±∞ÄòòÄÖ‹πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅŸÖ±’îÄÙÅ…Öô—1•πïÖ…Mï——ï»πŸÖ±’î°‹πï·—…Ö•ï±ëÃ∞Å±•πïÖ…M—Ö—’Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å…Ö—•ºÄÙÅŸÖ±’îÄºÄ°ô±ΩÖ–§ÅµÖ·•µ’¥Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅŸï…—•çÖ∞ÄÙÅ‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—1•πïÖ…Yï…—•çÖ∞à∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Õ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸ÅΩ±Ω»π…ùà†ƒ‡‘∞Ä»‡∞Ä»‡§ÄËÅ‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°Õ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸Å‹π—ï·–Ä¨ÄàËÉBwB◊FÉFBÀF?BﬂB‡à(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÅ‹π—ï·–Ä¨ÄàËÄàÄ¨ÅŸÖ±’îÄ¨ÄàÄºÄàÄ¨ÅµÖ·•µ’¥∞Å‹π‡Ä¨Åë¿†‹§∞Å‹π‰Ä¨Åë¿†ƒ–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—1•πïÖ…	Öç≠ù…Ω’πëΩ±Ω»à∞ÅΩ±Ω»π…ùà†ƒ‘ÿ∞Äƒ‘ÿ∞Äƒ‘‘§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Ÿï…—•çÖ∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Åçïπ—ï»ÄÙÅ‹π‡Ä¨Å‹π‹ÄºÄ…òÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°çïπ—ï»Ä¥Åë¿†–§∞Å‹π‰Ä¨Åë¿†»¿§∞Åçïπ—ï»Ä¨Åë¿†–§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†‘§∞Åë¿†–§∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—1•πïÖ…A…Ωù…ïÕÕΩ±Ω»à∞ÅΩ±Ω»π…ùà†–ÿ∞Ä»Ãÿ∞Ä¿§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–ÅâΩ——Ω¥ÄÙÅ‹π‰Ä¨Å‹π†Ä¥Åë¿†‘§∞Å—Ω¿ÄÙÅâΩ——Ω¥Ä¥Ä†°‹π†Ä¥Åë¿†»‘§§Ä®Å…Ö—•º§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖÕ•ùπÖ±UπÖŸÖ•±Öâ±î§ÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°çïπ—ï»Ä¥Åë¿†–§∞Å—Ω¿∞Åçïπ—ï»Ä¨Åë¿†–§∞ÅâΩ——Ω¥∞Åë¿†–§∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅIïç—Å—…Öç¨ÄÙÅπï‹ÅIïç—°‹π‡Ä¨Åë¿†‹§∞Å‹π‰Ä¨Åë¿†»‘§∞Å‹π‡Ä¨Å‹π‹Ä¥Åë¿†‹§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†‡§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°—…Öç¨∞Åë¿†‘§∞Åë¿†‘§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—1•πïÖ…A…Ωù…ïÕÕΩ±Ω»à∞ÅΩ±Ω»π…ùà†–ÿ∞Ä»Ãÿ∞Ä¿§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖÕ•ùπÖ±UπÖŸÖ•±Öâ±î§ÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°—…Öç¨π±ïô–∞Å—…Öç¨π—Ω¿∞Å—…Öç¨π±ïô–Ä¨Å—…Öç¨π›•ë—††§Ä®Å…Ö—•º∞Å—…Öç¨πâΩ——Ω¥∞Åë¿†‘§∞Åë¿†‘§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õ•ùπÖ±UπÖŸÖ•±Öâ±î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ‡‘∞Ä»‡∞Ä»‡§§ÏÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–†ãBwB◊FÉFBÀF?BﬂB‡à∞Å‹π‡Ä¨Åë¿†‹§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†Ã§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBGB„FB˚BÀF/B‰ÉFBˇB„FB˚BËàπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅëïçΩëïë5ÖÕ¨ÄÙÅ…Öô—!µ•M—Ö—ïM•ùπÖ∞πëïçΩëï]Ω…ê°±•ŸïYÖ±’ïÃπùï–°§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄÙÅ…Öô—!µ•M—Ö—ïM•ùπÖ∞π’πÖŸÖ•±Öâ±ï]Ω…ê°±•ŸïYÖ±’ïÃπùï–°§§∞ÅΩ¡ï…Ö—Ω…5Ωëî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹πçΩππïç—•Ωπ%πôºÄÑÙÅπ’±∞ÄòòÄÖ‹πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–Åâ•—µÖÕ¨ÄÙÅëïçΩëïë5ÖÕ¨Ä¯ÙÄ¿Ä¸ÅëïçΩëïë5ÖÕ¨ÄËÄ¿Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å±•πïdÄÙÅ‹π‰Ä¨Åë¿†ƒÿ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹π—ï·–Ä¨ÄàËÄàÄ¨Ä°Õ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸ÄãBwB◊FÉFBÀF?BﬂB‡à(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÅM—…•πúπôΩ…µÖ–°©ÖŸÑπ’—•∞π1ΩçÖ±îπI==P∞Äà¡‡î¿—`à∞Åâ•—µÖÕ¨§§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Åë¿†‡§∞Å±•πïd∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ±•πïdÄ¨ÙÅë¿†ƒ–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–ÅàÄÙÄ¿ÏÅàÄÄÃÄòòÅ±•πïdÄÅ‹π‰Ä¨Å‹π†Ä¥Åë¿†–§ÏÅà¨¨§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Åâ•—=∏ÄÙÄ°â•—µÖÕ¨ÄòÄ†ƒÄÅà§§ÄÑÙÄ¿Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»†ÖÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄòòÅâ•—=∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÅΩ±Ω»π…ùà†»»∞ÄƒÿÃ∞Ä‹–§ÄËÅΩ±Ω»π…ùà†ƒ–‡∞ÄƒÿÃ∞Äƒ‡–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›•…ç±î°‹π‡Ä¨Åë¿†ƒ»§∞Å±•πïdÄ¥Åë¿†–§∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹πâ•—1•Õ—1Öâï±ÕmâtÄ¨ÄàËÄà(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¨Ä°Õ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸Äà¸àÄËÅâ•—=∏Ä¸ÄàƒàÄËÄà¿à§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Åë¿†»»§∞Å±•πïd∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ±•πïdÄ¨ÙÅë¿†ƒ–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBFB˚BœFB◊FF∑B«B√F àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ’â±îÅ¡Ω±±ïêÄÙÅ…Öô—!µ•9’µï…•çM•ùπÖ∞πëïçΩëî°±•ŸïYÖ±’ïÃπùï–°§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄÙÅ…Öô—!µ•9’µï…•çM•ùπÖ∞π’πÖŸÖ•±Öâ±î°±•ŸïYÖ±’ïÃπùï–°§§∞ÅΩ¡ï…Ö—Ω…5Ωëî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹πçΩππïç—•Ωπ%πôºÄÑÙÅπ’±∞ÄòòÄÖ‹πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅëΩ’â±îÅŸÖ∞ÄÙÅ¡Ω±±ïêÄÙÙÅπ’±∞Ä¸Ä¿∏¿ÄËÅ¡Ω±±ïêÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M°Öëï»°π’±∞§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å…Ö—•ºÄÙÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸Ä¡òÄËÅ…Öô—A…Ωù…ïÕÕ	Ö»π…Ö—•º°ŸÖ∞∞Å‹πµ•πYÖ±’î∞Å‹πµÖ·YÖ±’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å•πÕï–ÄÙÅë¿†–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅIïç—Å—…Öç¨ÄÙÅπï‹ÅIïç—°‹π‡Ä¨Å•πÕï–∞Å‹π‰Ä¨Å•πÕï–∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Å‹π‹Ä¥Å•πÕï–∞Å‹π‰Ä¨Å‹π†Ä¥Å•πÕï–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅâÖç≠ù…Ω’πêÄÙÅ‹πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—A…Ωù…ïÕÕ	Öç≠ù…Ω’πëΩ±Ω»à∞ÅΩ±Ω»π…ùà†ƒ‘ÿ∞Äƒ‘ÿ∞Äƒ‘‘§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–Å¡…Ωù…ïÕÃÄÙÅ‹πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—A…Ωù…ïÕÕΩ±Ω»à∞ÅΩ±Ω»π…ùà†–ÿ∞Ä»Ãÿ∞Ä¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Ω±Ω»πÖ±¡°Ñ°âÖç≠ù…Ω’πê§ÄÑÙÄ¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M°Öëï»°πï‹ÅÖπë…Ω•êπù…Ö¡°•çÃπ1•πïÖ……Öë•ïπ–°—…Öç¨π±ïô–∞Å—…Öç¨π—Ω¿∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…Öç¨π±ïô–∞Å—…Öç¨πâΩ——Ω¥∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å•π—muÌΩ±Ω»π…ùà†–¿∞Ä–¿∞Ä–¿§∞ÅΩ±Ω»π]!%Q∞ÅΩ±Ω»π…ùà†ƒƒ‰∞Äƒƒ‰∞Äƒƒ‰•Ù∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπ’±∞∞ÅÖπë…Ω•êπù…Ö¡°•çÃπM°Öëï»πQ•±ï5Ωëîπ15@§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°—…Öç¨∞Åë¿†–§∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M°Öëï»°π’±∞§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπMQI=-§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—…Ω≠ï]•ë—†°ë¿†ƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ÿ‡∞Äÿ‡∞Äÿ‡§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°—…Öç¨∞Åë¿†–§∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅIïç—Åô•±∞ÄÙÅπï‹ÅIïç—°—…Öç¨π±ïô–∞Å—…Öç¨π—Ω¿∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…Öç¨π±ïô–Ä¨Å—…Öç¨π›•ë—††§Ä®Å…Ö—•º∞Å—…Öç¨πâΩ——Ω¥§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄòòÅô•±∞π›•ë—††§Ä¯Ä¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M°Öëï»°πï‹ÅÖπë…Ω•êπù…Ö¡°•çÃπ1•πïÖ……Öë•ïπ–°ô•±∞π±ïô–∞Åô•±∞π—Ω¿∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•±∞π±ïô–∞Åô•±∞πâΩ——Ω¥∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å•π—muÌ…Öô—A…Ωù…ïÕÕ	Ö»πÕ°Öëî°¡…Ωù…ïÕÃ∞Ä¿∏›ò§∞Å¡…Ωù…ïÕÃ∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…Öô—A…Ωù…ïÕÕ	Ö»πÕ°Öëî°¡…Ωù…ïÕÃ∞Ä¿∏›ò•Ù∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπ’±∞∞ÅÖπë…Ω•êπù…Ö¡°•çÃπM°Öëï»πQ•±ï5Ωëîπ15@§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°ô•±∞∞Åë¿†–§∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M°Öëï»°π’±∞§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—A…Ωù…ïÕÕM°Ω›MçÖ±îà∞ÅôÖ±Õî§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π	1,§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—…Ω≠ï]•ë—††…ò§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–ÅÕ¡Öç•πúÄÙÅ5Ö—†πµÖ‡†¡ò∞Å—…Öç¨π°ï•ù°–†§ÄºÄ»¡ò§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Åçïπ—ï»ÄÙÅ‹π‡Ä¨Å‹π‹ÄºÄ…òÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å—•ç¨ÄÙÄƒÏÅ—•ç¨ÄÄ»¿ÏÅ—•ç¨¨¨§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å±ïπù—†ÄÙÅ—•ç¨ÄÙÙÄƒ¿Ä¸Å‹π‹ÄºÄŸòÄËÄ°—•ç¨ÄîÄ»ÄÙÙÄ¿Ä¸Å‹π‹ÄºÄ·òÄËÅ‹π‹ÄºÄƒŸò§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å‰ÄÙÅ—…Öç¨π—Ω¿Ä¨Å—•ç¨Ä®ÅÕ¡Öç•πúÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›1•πî°çïπ—ï»Ä¥Å±ïπù—†∞Å‰∞Åçïπ—ï»Ä¨Å±ïπù—†∞Å‰∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õ•ùπÖ±UπÖŸÖ•±Öâ±îÅÒÅ‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—A…Ωù…ïÕÕM°Ω›Qï·–à∞Å—…’î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Õ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸ÅΩ±Ω»π…ùà†ƒ‡‘∞Ä»‡∞Ä»‡§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÅ‹πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—A…Ωù…ïÕÕQï·—Ω±Ω»à∞Å‹π—ï·—Ω±Ω»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°‹πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—A…Ωù…ïÕÕQï·—M•Èîà∞Åë¿†ƒ»§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅŸÖ±’ïQï·–ÄÙÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸ÄãBwB◊FÉFBÀF?BﬂB‡àÄËÅ1Ωπúπ—ΩM—…•πú°5Ö—†π…Ω’πê°ŸÖ∞§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°ŸÖ±’ïQï·–∞Å‹π‡Ä¨Ä°‹π‹Ä¥Å¡Ö•π–πµïÖÕ’…ïQï·–°ŸÖ±’ïQï·–§§ÄºÄ…ò∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‰Ä¨Å‹π†Ä¥Å•πÕï–∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—…Ω≠ï]•ë—†°ë¿†ƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãB£BÎB√BÔB¿ÄºÉBáFFB◊BÔBÎB¿àπï≈’Ö±Ã°‹π—Â¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ‹πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—Ωµ¡Ωπïπ—%êà∞Ä¥ƒ§ÄÙÙÄƒ»§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ’â±îÅ¡Ω±±ïêÄÙÅ…Öô—!µ•9’µï…•çM•ùπÖ∞πëïçΩëî°±•ŸïYÖ±’ïÃπùï–°§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄÙÅ…Öô—!µ•9’µï…•çM•ùπÖ∞π’πÖŸÖ•±Öâ±î°±•ŸïYÖ±’ïÃπùï–°§§∞ÅΩ¡ï…Ö—Ω…5Ωëî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹πçΩππïç—•Ωπ%πôºÄÑÙÅπ’±∞ÄòòÄÖ‹πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õ•ùπÖ±UπÖŸÖ•±Öâ±î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ‡‘∞Ä»‡∞Ä»‡§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–†ãBwB◊FÉFBÀF?BﬂB‡à∞Å‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Åë¿†ƒÿ§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅë…Ö›…Öô—Q°…ïÕ°Ω±ëÖ’ùî°çÖπŸÖÃ∞Å‹∞Å¡Ω±±ïêÄÙÙÅπ’±∞Ä¸Ä¿∏¿ÄËÅ¡Ω±±ïê∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãB£BÎB√BÔB¿ÄºÉBáFFB◊BÔBÎB¿àπï≈’Ö±Ã°‹π—Â¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ‹πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—Ωµ¡Ωπïπ—%êà∞Ä¥ƒ§ÄÙÙÄ»–§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ’â±îÅ¡Ω±±ïêÄÙÅ…Öô—!µ•9’µï…•çM•ùπÖ∞πëïçΩëî°±•ŸïYÖ±’ïÃπùï–°§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄÙÅ…Öô—!µ•9’µï…•çM•ùπÖ∞π’πÖŸÖ•±Öâ±î°±•ŸïYÖ±’ïÃπùï–°§§∞ÅΩ¡ï…Ö—Ω…5Ωëî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹πçΩππïç—•Ωπ%πôºÄÑÙÅπ’±∞ÄòòÄÖ‹πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õ•ùπÖ±UπÖŸÖ•±Öâ±î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ‡‘∞Ä»‡∞Ä»‡§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–†ãBwB◊FÉFBÀF?BﬂB‡à∞Å‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Åë¿†ƒÿ§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅë…Ö›…Öô—…Öë’Ö—ïëÖ’ùî°çÖπŸÖÃ∞Å‹∞Å¡Ω±±ïêÄÙÙÅπ’±∞Ä¸Ä¿∏¿ÄËÅ¡Ω±±ïê∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãB£BÎB√BÔB¿ÄºÉBáFFB◊BÔBÎB¿àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ’â±îÅ¡Ω±±ïêÄÙÅ…Öô—!µ•9’µï…•çM•ùπÖ∞πëïçΩëî°±•ŸïYÖ±’ïÃπùï–°§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄÙÅ…Öô—!µ•9’µï…•çM•ùπÖ∞π’πÖŸÖ•±Öâ±î°±•ŸïYÖ±’ïÃπùï–°§§∞ÅΩ¡ï…Ö—Ω…5Ωëî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹πçΩππïç—•Ωπ%πôºÄÑÙÅπ’±∞ÄòòÄÖ‹πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õ•ùπÖ±UπÖŸÖ•±Öâ±î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ‡‘∞Ä»‡∞Ä»‡§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–†ãBwB◊FÉFBÀF?BﬂB‡à∞Å‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Åë¿†ƒÿ§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅëΩ’â±îÅŸÖ∞ÄÙÅ¡Ω±±ïêÄÙÙÅπ’±∞Ä¸Ä¿∏¿ÄËÅ¡Ω±±ïêÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅëΩ’â±îÅ…Ö—•ºÄÙÅ5Ö—†πµÖ‡†¿∏¿∞Å5Ö—†πµ•∏†ƒ∏¿∞Ä°ŸÖ∞Ä¥Å‹πµ•πYÖ±’î§ÄºÅ5Ö—†πµÖ‡†¿∏¿¿ƒ∞Å‹πµÖ·YÖ±’îÄ¥Å‹πµ•πYÖ±’î§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Åç‡ÄÙÅ‹π‡Ä¨Å‹π‹ÄºÄ…ò∞Åç‰ÄÙÅ‹π‰Ä¨Å‹π†Ä¥Åë¿†ƒ‘§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å…Öë•’ÃÄÙÅ5Ö—†πµ•∏°‹π‹∞Å‹π†Ä®Äƒ∏Ÿò§Ä®Ä¿∏–…òÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπMQI=-§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—…Ω≠ï]•ë—†°ë¿†Ã§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ¿¿∞Äƒƒÿ∞ÄƒÃ‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›…å°ç‡Ä¥Å…Öë•’Ã∞Åç‰Ä¥Å…Öë•’Ã∞Åç‡Ä¨Å…Öë•’Ã∞Åç‰Ä¨Å…Öë•’Ã∞Äƒ‡¿∞Äƒ‡¿∞ÅôÖ±Õî∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–ÅÖπù±ïIÖêÄÙÄ°ô±ΩÖ–§Å5Ö—†π—ΩIÖë•ÖπÃ†ƒ‡¿Ä¨Å…Ö—•ºÄ®Äƒ‡¿§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Åπ‡ÄÙÅç‡Ä¨Ä°ô±ΩÖ–§Å5Ö—†πçΩÃ°Öπù±ïIÖê§Ä®Ä°…Öë•’ÃÄ¥Åë¿†ÿ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Åπ‰ÄÙÅç‰Ä¨Ä°ô±ΩÖ–§Å5Ö—†πÕ•∏°Öπù±ïIÖê§Ä®Ä°…Öë•’ÃÄ¥Åë¿†ÿ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—…Ω≠ï]•ë—†°ë¿†»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»»¿∞ÄÃ‡∞ÄÃ‡§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›1•πî°ç‡∞Åç‰∞Åπ‡∞Åπ‰∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›•…ç±î°ç‡∞Åç‰∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹π—ï·–Ä¨ÄàÄàÄ¨ÅM—…•πúπôΩ…µÖ–°©ÖŸÑπ’—•∞π1ΩçÖ±îπI==P∞Äàî∏≈òà∞ÅŸÖ∞§∞Å‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Åë¿†ƒÿ§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBOFB√FB„BËÄºÉBãFB◊B˜B–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅë…Ö›…Öô—Q…ïπë…Ö¡†°çÖπŸÖÃ∞Å‹∞Å±•ŸïYÖ±’ïÃπùï–°§§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBcB˜B”B„BÎB√FB˚F àπï≈’Ö±Ã°‹π—Â¡î§ÅÒÄãBSBÀFFBˇB˚BﬂB„FB„B˚B˜B˜F/B‰ÉB„B˜B”B„BÎB√FB˚F àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ•πë•çÖ—Ω…YÖ±’îÄÙÅ±•ŸïYÖ±’ïÃπùï–°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Å°ÖÕ	•πë•πúÄÙÅ‹πçΩππïç—•Ωπ%πôºÄÑÙÅπ’±∞ÄòòÄÖ‹πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ	ΩΩ±ïÖ∏ÅÕ—Ö—îÄÙÅ=…•ù•πÖ±…Öô—1Öµ¡Ωππïç—•Ω∏π•Õ	•—Iïù•Õ—ï»°‹πï·—…Ö•ï±ëÃ§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸Å=…•ù•πÖ±…Öô—1Öµ¡Ωππïç—•Ω∏πëïçΩëï	•–°•πë•çÖ—Ω…YÖ±’î∞Å‹πï·—…Ö•ï±ëÃ§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÅ…Öô—%πë•çÖ—Ω…M•ùπÖ∞πëïçΩëî°•πë•çÖ—Ω…YÖ±’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄÙÅ…Öô—%πë•çÖ—Ω…M•ùπÖ∞π’πÖŸÖ•±Öâ±î°•πë•çÖ—Ω…YÖ±’î∞ÅΩ¡ï…Ö—Ω…5Ωëî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ°ÖÕ	•πë•πú§ÅÒÄ°Ω¡ï…Ö—Ω…5ΩëîÄòòÄ°°ÖÕ	•πë•πúÅÒÅ•πë•çÖ—Ω…YÖ±’îÄÑÙÅπ’±∞§ÄòòÅÕ—Ö—îÄÙÙÅπ’±∞§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ	•—µÖ¿Å±Öµ¿ÄÙÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸Åπ’±∞ÄËÅΩ…•ù•πÖ±%πë•çÖ—Ω…	•—µÖ¿°‹∞Å	ΩΩ±ïÖ∏πQIUπï≈’Ö±Ã°Õ—Ö—î§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—±¡°Ñ†»‘‘§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅ=…•ù•πÖ∞Å%µÖùïY•ï‹Å’ÕïÃÅ%Q}adÅÖπêÅ—ï∏Å…Ö‹Å¡•·ï±ÃÅΩòÅ¡Öëë•πú∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅ9ïŸï»ÅÕ°Ω‹ÅÑÅπΩ…µÖ∞Å=Å•µÖùîÅ›°ï∏Å—°îÅâΩ’πêÅÕΩ’…çîÅ°ÖÃÅôÖ•±ïê∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°±Öµ¿ÄÑÙÅπ’±∞ÄòòÅ…ïç–π›•ë—††§Ä¯Ä»¿ÄòòÅ…ïç–π°ï•ù°–†§Ä¯Ä»¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›	•—µÖ¿°±Öµ¿∞Åπ’±∞∞Åπï‹ÅIïç—°…ïç–π±ïô–Ä¨Äƒ¿∞Å…ïç–π—Ω¿Ä¨Äƒ¿∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ïç–π…•ù°–Ä¥Äƒ¿∞Å…ïç–πâΩ——Ω¥Ä¥Äƒ¿§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õ•ùπÖ±UπÖŸÖ•±Öâ±î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ‡‘∞Ä»‡∞Ä»‡§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—±•ù∏°AÖ•π–π±•ù∏π9QH§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–†ãBwB◊FÉFBÀF?BﬂB‡à∞Å‹π‡Ä¨Å‹π‹ÄºÄ…ò∞Å‹π‰Ä¨Å‹π†ÄºÄ…ò∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—±•ù∏°AÖ•π–π±•ù∏π1P§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°±Öµ¿ÄÙÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ	•—µÖ¿Å›Ö…π•πúÄÙÅΩ…•ù•πÖ±	•—µÖ¿°Hπë…Ö›Öâ±îπ≠ç}›Ö…π•πù|Ã»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°›Ö…π•πúÄÑÙÅπ’±∞§ÅçÖπŸÖÃπë…Ö›	•—µÖ¿°›Ö…π•πú∞Å…ïç–π…•ù°–Ä¥Å›Ö…π•πúπùï—]•ë—††§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ïç–πâΩ——Ω¥Ä¥Å›Ö…π•πúπùï—!ï•ù°–†§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBkB˜B˚BˇBÎB¿àπï≈’Ö±Ã°‹π—Â¡î§ÅÒÄãBkB˚B˜FFB√B˜FB˜B√F<ÉBÎB˜B˚BˇBÎB¿Å…Öô–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅçΩπÕ—Öπ—	’——Ω∏ÄÙÄãBkB˚B˜FFB√B˜FB˜B√F<ÉBÎB˜B˚BˇBÎB¿Å…Öô–àπï≈’Ö±Ã°‹π—Â¡î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅâ’——ΩπQï·–ÄÙÅ‹π—ï·–Ä¨Ä°çΩπÕ—Öπ—	’——Ω∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÄàÄÉäHÄàÄ¨Å‹πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âç…Öô—	’——ΩπΩπÕ—Öπ–à∞Äà¿à§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÅ‹πâ’——ΩπM—Ö—îÄ¸ÄàÄÅoBKBkBmtàÄËÄàÄÅoBKBØBkBmtà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πÖ’—ΩIïÕ•ÈïQï·–§Å¡Ö•π–πÕï—Qï·—M•Èî°5Ö—†πµ•∏°¡Ö•π–πùï—Qï·—M•Èî†§∞Å5Ö—†πµÖ‡°ë¿†‡§∞Ä°‹π‹Ä¥Åë¿†»¿§§ÄºÅ5Ö—†πµÖ‡†—ò∞Åâ’——ΩπQï·–π±ïπù—††§Ä®Ä¿∏‘·ò§§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°â’——ΩπQï·–∞Å‹π‡Ä¨Åë¿†ƒ¿§∞Å‹π‰Ä¨Å‹π†ÄºÄ»Ä¨Åë¿†‘§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãB_B√B”B√FFB„BËàπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πÖ’—ΩIïÕ•ÈïQï·–§Å¡Ö•π–πÕï—Qï·—M•Èî°5Ö—†πµ•∏°¡Ö•π–πùï—Qï·—M•Èî†§∞Å5Ö—†πµÖ‡°ë¿†‡§∞Ä°‹π‹Ä¥Åë¿†ƒ‡§§ÄºÅ5Ö—†πµÖ‡†—ò∞Å‹π—ï·–π±ïπù—††§Ä®Ä¿∏‘·ò§§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹π—ï·–∞Å‹π‡Ä¨Åë¿†‰§∞Å‹π‰Ä¨Åë¿†»ƒ§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»‘‘∞Ä»‘‘∞Ä»‘‘§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°πï‹ÅIïç—°‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Åë¿†»‰§∞Å‹π‡Ä¨Å‹π‹Ä¥Åë¿†‡§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†ÿ§§∞Åë¿†–§∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπMQI=-§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»ƒ‹∞Äƒƒ‰∞Äÿ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°πï‹ÅIïç—°‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Åë¿†»‰§∞Å‹π‡Ä¨Å‹π‹Ä¥Åë¿†‡§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†ÿ§§∞Åë¿†–§∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ»¿∞Ä‡¿∞Äƒ¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–†ãä"HÄÄÄÄÄÄÄÄÄÄÄÄÄ¨à∞Å‹π‡Ä¨Åë¿†ƒ‡§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†ƒÿ§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãB_BÀFBÎB˚BÀB˚B‰ÉFB„BœB˜B√BÏÅ…Öô–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πù	’•±ëï»ÅÖç—•Ÿï°Öππï±ÃÄÙÅπï‹ÅM—…•πù	’•±ëï»†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Åç°Öππï∞ÄÙÄ¿ÏÅç°Öππï∞ÄÅ…Öô—MΩ’πë	Öπ¨π!991LÏÅç°Öππï∞¨¨§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†°‹πç…Öô—MΩ’πëç—•Ÿï5ÖÕ¨ÄòÄ†ƒÄÅç°Öππï∞§§ÄÑÙÄ¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Öç—•Ÿï°Öππï±Ãπ±ïπù—††§Ä¯Ä¿§ÅÖç—•Ÿï°Öππï±ÃπÖ¡¡ïπê†à∞à§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖç—•Ÿï°Öππï±ÃπÖ¡¡ïπê°ç°Öππï∞§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÖç—•ŸîÄÙÅÖç—•Ÿï°Öππï±Ãπ±ïπù—††§Ä¯Ä¿Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Öç—•ŸîÄ¸ÅΩ±Ω»π…ùà†»Ã‰∞Äÿ‡∞Äÿ‡§ÄËÅΩ±Ω»π…ùà†ƒ¿¿∞Äƒƒÿ∞ÄƒÃ‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›•…ç±î°‹π‡Ä¨Åë¿†ƒÿ§∞Å‹π‰Ä¨Å‹π†ÄºÄ»∞Åë¿†ÿ§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅÕΩ’πë1Öâï∞ÄÙÄ°Öç—•ŸîÄ¸Äã¬~R(ÄàÄËÄã¬~RÄà§Ä¨Å‹π—ï·–(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¨Ä°Öç—•ŸîÄ¸ÄàÅlàÄ¨ÅÖç—•Ÿï°Öππï±ÃÄ¨ÄâtàÄËÄàÅl√äL›tà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πÖ’—ΩIïÕ•ÈïQï·–§Å¡Ö•π–πÕï—Qï·—M•Èî°5Ö—†πµ•∏°¡Ö•π–πùï—Qï·—M•Èî†§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ5Ö—†πµÖ‡°ë¿†‡§∞Ä°‹π‹Ä¥Åë¿†Ã»§§ÄºÅ5Ö—†πµÖ‡†—ò∞ÅÕΩ’πë1Öâï∞π±ïπù—††§Ä®Ä¿∏‘·ò§§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°ÕΩ’πë1Öâï∞∞Å‹π‡Ä¨Åë¿†»‡§∞Å‹π‰Ä¨Å‹π†ÄºÄ»Ä¨Åë¿†‘§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãB_BÀFBÎB˚BÀB˚B‰ÉFB„BœB˜B√BÏàπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅÕΩ’πëIïÖë•πúÄÙÅ±•ŸïYÖ±’ïÃπùï–°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄÙÅ…Öô—±Ö…µM•ùπÖ∞π’πÖŸÖ•±Öâ±î°ÕΩ’πëIïÖë•πú∞Å‹πÕΩ’πëQ°…ïÕ°Ω±ê∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ï…Ö—Ω…5Ωëî∞Å‹πçΩππïç—•Ωπ%πôºÄÑÙÅπ’±∞ÄòòÄÖ‹πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ	ΩΩ±ïÖ∏ÅµïÖÕ’…ïëç—•ŸîÄÙÅ…Öô—±Ö…µM•ùπÖ∞πÖç—•Ÿî°ÕΩ’πëIïÖë•πú∞Å‹πÕΩ’πëQ°…ïÕ°Ω±ê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅÖç—•ŸîÄÙÄÖÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄòòÄ°µïÖÕ’…ïëç—•ŸîÄÑÙÅπ’±∞Ä¸ÅµïÖÕ’…ïëç—•ŸîÄËÅ‹πÕΩ’πëç—•Ÿî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Õ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸ÅΩ±Ω»π…ùà†ƒ‡‘∞Ä»‡∞Ä»‡§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÅÖç—•ŸîÄ¸ÅΩ±Ω»π…ùà†»Ã‰∞Äÿ‡∞Äÿ‡§ÄËÅΩ±Ω»π…ùà†ƒ¿¿∞Äƒƒÿ∞ÄƒÃ‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›•…ç±î°‹π‡Ä¨Åë¿†ƒÿ§∞Å‹π‰Ä¨Å‹π†ÄºÄ»∞Åë¿†ÿ§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Õ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸ÅΩ±Ω»π…ùà†ƒ‡‘∞Ä»‡∞Ä»‡§ÄËÅ‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅÕΩ’πë1Öâï∞ÄÙÅÕ•ùπÖ±UπÖŸÖ•±Öâ±îÄ¸ÄãäjÄÄàÄ¨Å‹π—ï·–Ä¨ÄàÅoBwB◊FÉFBÀF?BﬂB·tà(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÄ°Öç—•ŸîÄ¸Äã¬~R(ÄàÄËÄã¬~RÄà§Ä¨Å‹π—ï·–Ä¨Ä°Öç—•ŸîÄ¸ÄàÅoBCBoBCBÉBqtàÄËÄàÅoB{Bità§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πÖ’—ΩIïÕ•ÈïQï·–§Å¡Ö•π–πÕï—Qï·—M•Èî°5Ö—†πµ•∏°¡Ö•π–πùï—Qï·—M•Èî†§∞Å5Ö—†πµÖ‡°ë¿†‡§∞Ä°‹π‹Ä¥Åë¿†Ã»§§ÄºÅ5Ö—†πµÖ‡†—ò∞ÅÕΩ’πë1Öâï∞π±ïπù—††§Ä®Ä¿∏‘·ò§§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°ÕΩ’πë1Öâï∞∞Å‹π‡Ä¨Åë¿†»‡§∞Å‹π‰Ä¨Å‹π†ÄºÄ»Ä¨Åë¿†‘§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBCFFB„B»Å…Öô–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒÃ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–†ãBCFFB„B»ËÄàÄ¨Å‹π—ï·–∞Å‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Åë¿†»Ã§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°±•ŸïYÖ±’ïÃπçΩπ—Ö•πÕ-ï‰°§§Ä¸ÄãBkB√B˜B√BÔF,É
‹ÄàÄ¨Å±•ŸïYÖ±’ïÃπùï–°§§ÄËÄãBoB˚BÎB√BÔF3B˜B√F<ÉB„FFB˚FB„F<à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†‰§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBB◊FB◊FB˚B–ÉB˜B¿ÉF7BÎFB√BÙàπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅπÖŸ1Öâï∞ÄÙÄã¬~R\ÄàÄ¨Å‹π—ï·–Ä¨ÄàÄ£æ˛˜æ˛˜BÎFB√BÙÄàÄ¨Ä°‹π—Ö…ùï—Mç…ïïπ%πëï‡Ä¨Äƒ§Ä¨Äà§àÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πÖ’—ΩIïÕ•ÈïQï·–§Å¡Ö•π–πÕï—Qï·—M•Èî°5Ö—†πµ•∏°¡Ö•π–πùï—Qï·—M•Èî†§∞Å5Ö—†πµÖ‡°ë¿†‡§∞Ä°‹π‹Ä¥Åë¿†ƒÿ§§ÄºÅ5Ö—†πµÖ‡†—ò∞ÅπÖŸ1Öâï∞π±ïπù—††§Ä®Ä¿∏‘·ò§§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°πÖŸ1Öâï∞∞Å‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Å‹π†ÄºÄ»Ä¨Åë¿†‘§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†â%Å…Öô–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖπë…Ω•êπù…Ö¡°•çÃπ5ΩŸ•îÅµΩŸ•îÄÙÅ±ΩÖëÖç°ïë5ΩŸ•î°‹πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âç…Öô—•ôAÖ—†à∞Äàà§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°µΩŸ•îÄÑÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–Åë’…Ö—•Ω∏ÄÙÅ5Ö—†πµÖ‡†ƒ∞ÅµΩŸ•îπë’…Ö—•Ω∏†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµΩŸ•îπÕï—Q•µî†°•π–§Ä°MÂÕ—ïµ±Ωç¨π’¡—•µï5•±±•Ã†§ÄîÅë’…Ö—•Ω∏§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅÕÖŸîÄÙÅçÖπŸÖÃπÕÖŸî†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπç±•¡Iïç–°…ïç–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπ—…ÖπÕ±Ö—î°…ïç–π±ïô–∞Å…ïç–π—Ω¿§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπÕçÖ±î°…ïç–π›•ë—††§ÄºÅµΩŸ•îπ›•ë—††§∞Å…ïç–π°ï•ù°–†§ÄºÅµΩŸ•îπ°ï•ù°–†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµΩŸ•îπë…Ö‹°çÖπŸÖÃ∞Ä¿∞Ä¿§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπ…ïÕ—Ω…ïQΩΩ’π–°ÕÖŸî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ΩÕ—%πŸÖ±•ëÖ—ïï±ÖÂïê†ÃÕ0§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»–ƒ∞Ä»–‘∞Ä»–‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Iïç–°…ïç–∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†‹ƒ∞Ä‡‘∞Äƒ¿‘§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒ»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ±Öâï∞ÄÙÅ‹πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âç…Öô—•ôAÖ—†à∞Äàà§π•Õµ¡—‰†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÄãBKF/B«B◊FB‡Å%àÄËÄâ%ÉB˜B◊B”B˚FFFBˇB◊BÙàÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°±Öâï∞∞Å‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Å‹π†ÄºÄ»Ä¨Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBsB˜B˚BœB˚BˇB˚BﬂB„FB„B˚B˜B˜B˚B‘ÉFB˚FB¯àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ)M=9=â©ïç–Åç…Öô—=—°ï»ÄÙÅ‹πï·—…Ö•ï±ëÃπΩ¡—)M=9=â©ïç–†âç…Öô—=—°ï»à§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–Å—Â¡ïΩµµÖπêÄÙÅç…Öô—=—°ï»ÄÙÙÅπ’±∞Ä¸Ä¿ÄËÅç…Öô—=—°ï»πΩ¡—%π–†â—Â¡ïΩµµÖπêà∞Ä¿§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–Å•µù%ë‡ÄÙÅ…Öô—5’±—•M—Ö—ï%µÖùîπÕï±ïç—1•ŸïYÖ±’î°—Â¡ïΩµµÖπê∞Å±•ŸïYÖ±’ïÃπùï–°§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°•µù%ë‡Ä¯ÙÄ¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ•µùAÖ—†ÄÙÅ‹π•µÖùïAÖ—°M—Ö—ïÕm•µù%ë·tÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ	•—µÖ¿Åâ•—µÖ¿ÄÙÅ±ΩÖëÖç°ïë	•—µÖ¿°•µùAÖ—†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅIïç—Å•µÖùï	Ω’πëÃÄÙÅπï‹ÅIïç—°…ïç–π±ïô–Ä¨Äƒ¿∞Å…ïç–π—Ω¿Ä¨Äƒ¿∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ïç–π…•ù°–Ä¥Äƒ¿∞Å…ïç–πâΩ——Ω¥Ä¥Äƒ¿§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°â•—µÖ¿ÄÑÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›	•—µÖ¿°â•—µÖ¿∞Åπ’±∞∞Å•µÖùï	Ω’πëÃ∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°…Öô—5’±—•M—Ö—ï%µÖùîπÕ°Ω’±ëM°Ω›9ΩA•ç—’…î°•µù%ë‡∞ÅôÖ±Õî§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ•µÖùï	Ω’πëÃπ›•ë—††§Ä¯Ä¿ÄòòÅ•µÖùï	Ω’πëÃπ°ï•ù°–†§Ä¯Ä¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ	•—µÖ¿ÅπΩA•ç—’…îÄÙÅΩ…•ù•πÖ±	•—µÖ¿°Hπë…Ö›Öâ±îπ≠ç}πΩ}¡•ç—’…î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°πΩA•ç—’…îÄÑÙÅπ’±∞§ÅçÖπŸÖÃπë…Ö›	•—µÖ¿°πΩA•ç—’…î∞Åπ’±∞∞Å•µÖùï	Ω’πëÃ∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBÉB√FBˇB˚BﬂB˜B√BÀB√B˜B„B‘ÉFB◊FB‡Å…Öô–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†‘ƒ∞Äÿ‘∞Ä‡‘§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°πï‹ÅIïç—°‹π‡Ä¨Åë¿†ÿ§∞Å‹π‰Ä¨Åë¿†ÿ§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Å‹π‹Ä¥Åë¿†ÿ§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†ÿ§§∞Åë¿†–§∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»¿Ã∞Ä»ƒÃ∞Ä»»‘§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹π—ï·–∞Å‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Åë¿†ƒ‰§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅŸΩ•çïπÖâ±ïêÄÙÅ‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—YΩ•çïIïçΩùπ•—•ΩππÖâ±ïêà∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°ŸΩ•çïπÖâ±ïêÄ¸ÅΩ±Ω»π…ùà†ƒÃ–∞Ä»Ã‰∞Äƒ‹»§ÄËÅΩ±Ω»π…ùà†»–‡∞ÄƒƒÃ∞ÄƒƒÃ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒ¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°ŸΩ•çïπÖâ±ïêÄ¸ÄãBoB˚BÎB√BÔF3B˜B¯ÉäàÉB˜B√B€BÛB‡ÉB”BÔF<ÉBÎB˚BÛB√B˜B”F,àÄËÄãBOB˚BÔB˚FB˚BÀB˚B‘ÉFBˇFB√BÀBÔB◊B˜B„B‘ÉBÀF/BÎBÔF;FB◊B˜B¯à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Å‹π†ÄºÄ»Ä¨Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ–‡∞ÄƒÿÃ∞Äƒ‡–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–†àƒÿÉFFB√B‹ÉäàÉBÔB˚BÎB√BÔF3B˜F/B‰ÉFB◊BœB„FFF Äà(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¨Å‹πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—YΩ•çïIïù•Õ—ï…ëë…ïÕÃà∞Ä¥ƒ§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†ƒ¿§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBKB„B”B◊B¯ÅIQM@Å…Öô–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†Ã¿∞Ä–ƒ∞Ä‘‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°πï‹ÅIïç—°‹π‡Ä¨Åë¿†ÿ§∞Å‹π‰Ä¨Åë¿†ÿ§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Å‹π‹Ä¥Åë¿†ÿ§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†ÿ§§∞Åë¿†–§∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»¿Ã∞Ä»ƒÃ∞Ä»»‘§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹π—ï·–∞Å‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Åë¿†ƒ‰§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»–‡∞ÄƒƒÃ∞ÄƒƒÃ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒ¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—I—Õ¡πÖâ±ïêà∞ÅôÖ±Õî§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÄâIQM@ÉäàÉB˜B√B€BÛB„FB‘ÉB”BÔF<ÉBˇFB˚FBÛB˚FFB¿àÄËÄâIQM@ÉäàÉBˇB˚FB˚BËÉBÀF/BÎBÔF;FB◊BÙà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Å‹π†ÄºÄ»Ä¨Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ–‡∞ÄƒÿÃ∞Äƒ‡–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–†ãBcFFB˚FB˜B„BËÉFB˚FFB√B˜FGBÙÏÉBﬂB√BˇFFBËÉFFB◊B«FB◊FÉBˇB˚B”FBÀB◊FB€B”B◊B˜B„F<à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†ƒ¿§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†â]ïâY•ï‹Å…Öô–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†Ã¿∞Ä–ƒ∞Ä‘‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°πï‹ÅIïç—°‹π‡Ä¨Åë¿†ÿ§∞Å‹π‰Ä¨Åë¿†ÿ§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Å‹π‹Ä¥Åë¿†ÿ§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†ÿ§§∞Åë¿†–§∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»¿Ã∞Ä»ƒÃ∞Ä»»‘§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹π—ï·–∞Å‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Åë¿†ƒ‰§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»–‡∞ÄƒƒÃ∞ÄƒƒÃ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒ¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—]ïâY•ï›πÖâ±ïêà∞ÅôÖ±Õî§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸Äâ]ïâY•ï‹ÉäàÉB˜B√B€BÛB„FB‘ÉB”BÔF<ÉB˚FBÎFF/FB„F<àÄËÄâ]ïâY•ï‹ÉBÀF/BÎBÔF;FB◊BÙà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Å‹π†ÄºÄ»Ä¨Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ–‡∞ÄƒÿÃ∞Äƒ‡–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–†àƒÿÉB„FFB˚B”B˜F/FÉFFF/BÔB˚BËÉFB˚FFB√B˜B◊B˜F,à∞Å‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†ƒ¿§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†âµÖ•∞Å…Öô–àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†Ã¿∞Ä–ƒ∞Ä‘‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›IΩ’πëIïç–°πï‹ÅIïç—°‹π‡Ä¨Åë¿†ÿ§∞Å‹π‰Ä¨Åë¿†ÿ§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Å‹π‹Ä¥Åë¿†ÿ§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†ÿ§§∞Åë¿†–§∞Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»¿Ã∞Ä»ƒÃ∞Ä»»‘§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒƒ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹π—ï·–∞Å‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Åë¿†ƒ‰§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»–‡∞ÄƒƒÃ∞ÄƒƒÃ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒ¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—µÖ•±πÖâ±ïêà∞ÅôÖ±Õî§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÄâµÖ•∞ÉäàÉB˜B√B€BÛB„FB‘ÉB”BÔF<ÉBˇB˚B”FBÀB◊FB€B”B◊B˜B„F<àÄËÄâµÖ•∞ÉBÀF/BÎBÔF;FB◊BÙà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Å‹π†ÄºÄ»Ä¨Åë¿†–§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ–‡∞ÄƒÿÃ∞Äƒ‡–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†‰§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°‹πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âç…Öô—µÖ•±Qºà∞Äàà§∞Å‹π‡Ä¨Åë¿†ƒ»§∞Å‹π‰Ä¨Å‹π†Ä¥Åë¿†ƒ¿§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBOB˚BÔB˚FB˚BÀB˚B‘ÉFB˚B˚B«F'B◊B˜B„B‘àπï≈’Ö±Ã°‹π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅŸΩ•çï%ë‡ÄÙÅΩ¡ï…Ö—Ω…5Ωëî(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸Å…Öô—!µ•M—Ö—ïM•ùπÖ∞πëïçΩëî°±•ŸïYÖ±’ïÃπùï–°§§§ÄËÄ¿Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅŸΩ•çï1Öâï∞ÄÙÅŸΩ•çï%ë‡ÄÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸Äã¬~^åÄàÄ¨Å‹π—ï·–Ä¨ÄàËÉBwB◊FÉFBÀF?BﬂB‡à(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÄã¬~^åÄàÄ¨Å‹π—ï·–Ä¨ÄàÅlàÄ¨ÅŸΩ•çï%ë‡Ä¨ÄâtËÄàÄ¨Å‹πŸΩ•çï5ïÕÕÖùïÕmŸΩ•çï%ë·tÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°‹π—ï·—Ω±Ω»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πÖ’—ΩIïÕ•ÈïQï·–§Å¡Ö•π–πÕï—Qï·—M•Èî°5Ö—†πµ•∏°¡Ö•π–πùï—Qï·—M•Èî†§∞Å5Ö—†πµÖ‡°ë¿†‡§∞Ä°‹π‹Ä¥Åë¿†ƒÿ§§ÄºÅ5Ö—†πµÖ‡†—ò∞ÅŸΩ•çï1Öâï∞π±ïπù—††§Ä®Ä¿∏‘·ò§§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°ŸΩ•çï1Öâï∞∞Å‹π‡Ä¨Åë¿†‡§∞Å‹π‰Ä¨Å‹π†ÄºÄ»Ä¨Åë¿†‘§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å—‡ÄÙÅ‹π‡Ä¨Åë¿†ƒ¿§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å—‰ÄÙÅ‹π‰Ä¨Å‹π†ÄºÄ»Ä¨Åë¿†‘§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅë•Õ¡±ÖÂQï·–ÄÙÅ‹π—ï·–Ä¨Ä°±•ŸïYÖ±’ïÃπçΩπ—Ö•πÕ-ï‰°§§Ä¸ÄàÄÙÄàÄ¨Å±•ŸïYÖ±’ïÃπùï–°§§ÄËÄàà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πÖ’—ΩIïÕ•ÈïQï·–§Å¡Ö•π–πÕï—Qï·—M•Èî°5Ö—†πµ•∏°¡Ö•π–πùï—Qï·—M•Èî†§∞Å5Ö—†πµÖ‡°ë¿†‡§∞Ä°‹π‹Ä¥Åë¿†»¿§§ÄºÅ5Ö—†πµÖ‡†—ò∞Åë•Õ¡±ÖÂQï·–π±ïπù—††§Ä®Ä¿∏‘·ò§§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°ë•Õ¡±ÖÂQï·–∞Å—‡∞Å—‰∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°]•ëùï–Å›•ëùï–ÄËÅ›•ëùï—Ã§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ††â	ÉBãB√BÁBÛB◊F àπï≈’Ö±Ã°›•ëùï–π—Â¡î§ÅÒÄâ	ÅQ@àπï≈’Ö±Ã°›•ëùï–π—Â¡î§ÅÒÄâ	ÅQ=àπï≈’Ö±Ã°›•ëùï–π—Â¡î§§ÄòòÅ›•ëùï–π—•µï…M—Ö…—ïë–Ä¯Ä¡0(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅMÂÕ—ïµ±Ωç¨πï±Ö¡ÕïëIïÖ±—•µî†§Ä¥Å›•ëùï–π—•µï…M—Ö…—ïë–ÄÅ›•ëùï–π—•µï…A…ïÕï—5Ã§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ΩÕ—%πŸÖ±•ëÖ—ïï±ÖÂïê†ƒ¿¡0§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâ…ïÖ¨Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅ	1%9,àπï≈’Ö±Ã°›•ëùï–π—Â¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅâë…Öô—9Ö—•ŸïQ•µï»ππïïëÕIïë…Ö‹°âë…Öô—9Ö—•ŸïQ•µï»π	1%9,∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πç…Öô—	±•π≠M—Ö—î∞ÅMÂÕ—ï¥πç’……ïπ—Q•µï5•±±•Ã†§∞Å›•ëùï–π—•µï…A…ïÕï—5Ã§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ΩÕ—%πŸÖ±•ëÖ—ïï±ÖÂïê†ƒ¿¡0§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâ…ïÖ¨Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†â	ÅIPÅQ%5Hàπï≈’Ö±Ã°›•ëùï–π—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅµΩëîÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âç…Öô—Q•µï…5Ωëîà∞ÄâQ=8à§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅµΩëïΩëîÄÙÄâQ=àπï≈’Ö±Ã°µΩëî§Ä¸Åâë…Öô—9Ö—•ŸïQ•µï»πQ=(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÄâQ@àπï≈’Ö±Ã°µΩëî§Ä¸Åâë…Öô—9Ö—•ŸïQ•µï»πQ@(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÄâ	1%9,àπï≈’Ö±Ã°µΩëî§Ä¸Åâë…Öô—9Ö—•ŸïQ•µï»π	1%9,ÄËÅâë…Öô—9Ö—•ŸïQ•µï»πQ=8Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°âë…Öô—9Ö—•ŸïQ•µï»ππïïëÕIïë…Ö‹°µΩëïΩëî∞Å›•ëùï–πç…Öô—Q•µï…M—Ö—î∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅMÂÕ—ï¥πç’……ïπ—Q•µï5•±±•Ã†§∞Å›•ëùï–πç…Öô—Q•µï…I’π—•µïA…ïÕï—5Ã§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ΩÕ—%πŸÖ±•ëÖ—ïï±ÖÂïê†ƒ¿¡0§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâ…ïÖ¨Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°µÖ…≈’ïïç—•ŸîÄòòÅë…Öùù•πú§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å±ïô–ÄÙÅ5Ö—†πµ•∏°ëΩ›π`∞ÅµÖ…≈’ïï`§∞Å—Ω¿ÄÙÅ5Ö—†πµ•∏°ëΩ›πd∞ÅµÖ…≈’ïïd§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å…•ù°–ÄÙÅ5Ö—†πµÖ‡°ëΩ›π`∞ÅµÖ…≈’ïï`§∞ÅâΩ——Ω¥ÄÙÅ5Ö—†πµÖ‡°ëΩ›πd∞ÅµÖ…≈’ïïd§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»πÖ…ùà†–»∞Ä»ƒ∞Äƒ¿ƒ∞Äƒ‰»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Iïç–°±ïô–∞Å—Ω¿∞Å…•ù°–∞ÅâΩ——Ω¥∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπMQI=-§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—…Ω≠ï]•ë—†°ë¿†ƒ∏’ò§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†»ƒ∞Äƒ¿ƒ∞Äƒ‰»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Iïç–°±ïô–∞Å—Ω¿∞Å…•ù°–∞ÅâΩ——Ω¥∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ôâë]•…ï5Ωëî§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—M—Â±î°AÖ•π–πM—Â±îπ%10§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Ω±Ω»°Ω±Ω»π…ùà†ƒ¿‰∞Ä–¿∞Ä»ƒ‹§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö•π–πÕï—Qï·—M•Èî°ë¿†ƒ»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖπŸÖÃπë…Ö›Qï·–°›•…ïMΩ’…çîÄÄ¿Ä¸ÄãBÉBWB[BcBpÉBáBKBøB_B`ËÉBÀF/B«B◊FB‡ÉB„FFB˚FB˜B„BËàÄËÄãBÉBWB[BcBpÉBáBKBøB_B`ËÉBÀF/B«B◊FB‡ÉFB◊BÔF0à∞Åë¿†ƒ¿§∞Åë¿†ƒ‡§∞Å¡Ö•π–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅô•πÖ∞Å=¡ï…Ö—Ω…ïÕ—’…îÅΩ¡ï…Ö—Ω…ïÕ—’…îÄÙÅπï‹Å=¡ï…Ö—Ω…ïÕ—’…î†§Ï(ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅ]•ëùï–Å¡…ïÕÕïë…Öô—	’——Ω∏Ï(ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅ	•πë•πùIï≈’ïÕ–Å¡…ïÕÕïë…Öô—	’——Ωπ	•πë•πúÏ(ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅ•π–Å¡…ïÕÕïë…Öô—	’——Ωπ%πëï‡ÄÙÄ¥ƒÏ(ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅ•π–Å¡…ïÕÕïë…Öô—	’——Ωπ	•–Ï(ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅ•π–ÅΩ¡ï…Ö—Ω…5Ωµïπ—Ö…ÂÖπë•ëÖ—îÄÙÄ¥ƒÏ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅâΩΩ±ïÖ∏Å•Õ…Öô—5Ωµïπ—Ö…Â	’——Ω∏°]•ëùï–Å›•ëùï–§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å›•ëùï–ÄÑÙÅπ’±∞ÄòòÄãBkB˜B˚BˇBÎB¿àπï≈’Ö±Ã°›•ëùï–π—Â¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ›•ëùï–πï·—…Ö•ï±ëÃπ°ÖÃ†âç…Öô—	’——Ωπ5Ωëîà§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅâïù•π…Öô—	•—	’——Ω∏°]•ëùï–Å›•ëùï–∞Å•π–Å•πëï‡§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ï…Ö—Ω…5Ωµïπ—Ö…ÂÖπë•ëÖ—îÄÙÅ•πëï‡Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—	’——Ωπ	•—πÖâ±ïêà∞ÅôÖ±Õî§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB√FFFB˚B‰ÅQ@Å°Ω±ë•πú∑FB◊BœB„FFF Å¿ÃΩ»»ÉB‡ÉBÀBÎBÔF;FB‡ÉB«B„FB˚BÀFF8ÉBÎB˚BÛB√B˜B”FÉB»ÉFBÀB˚BÁFFBÀB√Fà∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ	•πë•πùIï≈’ïÕ–Åâ•πë•πúÄÙÅ¡Ö…Õï	•πë•πú†¥ƒ∞Å›•ëùï–πçΩππïç—•Ωπ%πôº§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°â•πë•πúÄÙÙÅπ’±∞ÅÒÅâ•πë•πúπô’πç—•ΩπΩëîÄÑÙÄÃ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBFB˚BÀB◊FF0ÅQ@Å°Ω±ë•πú∑FB◊BœB„FFF Å¿ÃΩ»»ÉB»ÉFBÀB˚BÁFFBÀB√FÉBÎB˜B˚BˇBÎB‡à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏÅ¡…ïÕÕïë…Öô—	’——Ωπ	•–ÄÙÅ…Öô—5Ωµïπ—Ö…Â	’——Ω∏πâ•–°›•ëùï–πï·—…Ö•ï±ëÃ§ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅçÖ—ç†Ä°%±±ïùÖ±…ù’µïπ—·çï¡—•Ω∏Å•πŸÖ±•ê§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞Å•πŸÖ±•êπùï—5ïÕÕÖùî†§∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§ÏÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ¡…ïÕÕïë…Öô—	’——Ω∏ÄÙÅ›•ëùï–Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ¡…ïÕÕïë…Öô—	’——Ωπ	•πë•πúÄÙÅâ•πë•πúÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ¡…ïÕÕïë…Öô—	’——Ωπ%πëï‡ÄÙÅ•πëï‡Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πâ’——ΩπM—Ö—îÄÙÅ—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ≈’ï’ï…Öô—	•—	’——Ωπ]…•—î°›•ëùï–∞Åâ•πë•πú∞Å•πëï‡∞Å¡…ïÕÕïë…Öô—	’——Ωπ	•–∞Å—…’î§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅïπë…Öô—	•—	’——Ω∏†§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Å›•ëùï–ÄÙÅ¡…ïÕÕïë…Öô—	’——Ω∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ	•πë•πùIï≈’ïÕ–Åâ•πë•πúÄÙÅ¡…ïÕÕïë…Öô—	’——Ωπ	•πë•πúÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•π–Å•πëï‡ÄÙÅ¡…ïÕÕïë…Öô—	’——Ωπ%πëï‡Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•π–Åâ•–ÄÙÅ¡…ïÕÕïë…Öô—	’——Ωπ	•–Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ¡…ïÕÕïë…Öô—	’——Ω∏ÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ¡…ïÕÕïë…Öô—	’——Ωπ	•πë•πúÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ¡…ïÕÕïë…Öô—	’——Ωπ%πëï‡ÄÙÄ¥ƒÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°›•ëùï–ÄÙÙÅπ’±∞ÅÒÅâ•πë•πúÄÙÙÅπ’±∞§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πâ’——ΩπM—Ö—îÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ≈’ï’ï…Öô—	•—	’——Ωπ]…•—î°›•ëùï–∞Åâ•πë•πú∞Å•πëï‡∞Åâ•–∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅÖç—•ŸÖ—ï…Öô—M›•—ç†°]•ëùï–Å›•ëùï–∞Å•π–Å•πëï‡§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–ÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡ÄÙÅç’……ïπ—Mç…ïïπ%πëï‡Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅMç…ïï∏ÅΩ…•ù•πÖ±Mç…ïï∏ÄÙÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡Ä¯ÙÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡ÄÅÕç…ïïπÃπÕ•Èî†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÅÕç…ïïπÃπùï–°Ω…•ù•πÖ±Mç…ïïπ%πëï‡§ÄËÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å•πëï‡∞ÅΩ…•ù•πÖ±Mç…ïï∏∞ÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅâΩΩ±ïÖ∏Å¡…ïŸ•Ω’ÃÄÙÅ›•ëùï–πâ’——ΩπM—Ö—îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅâΩΩ±ïÖ∏Åπï·–ÄÙÄÖ¡…ïŸ•Ω’ÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—M›•—ç°	•—πÖâ±ïêà∞ÅôÖ±Õî§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ)M=9=â©ïç–ÅΩ±ëA…Ω©ïç–ÄÙÅ¡…Ω©ïç—·—…Ö•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πâ’——ΩπM—Ö—îÄÙÅπï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö¡ï…Õ•Õ—A…Ω©ïç—9Ω‹°—…’î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πâ’——ΩπM—Ö—îÄÙÅ¡…ïŸ•Ω’ÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡…Ω©ïç—·—…Ö•ï±ëÃÄÙÅΩ±ëA…Ω©ïç–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÖŸïç—•ŸïMç…ïï∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB‘ÉFB”B√BÔB˚FF0ÉFB˚FFB√B˜B„FF0ÉBˇB◊FB◊BÎBÔF;FB√FB◊BÔF0à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBB◊FB◊BÎBÔF;FB√FB◊BÔF0ËÄàÄ¨Ä°πï·–Ä¸ÄãBKBkBlàÄËÄãBKBØBkBlà§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å	•πë•πùIï≈’ïÕ–Åâ•πë•πúÄÙÅ¡Ö…Õï	•πë•πú†¥ƒ∞Å›•ëùï–πçΩππïç—•Ωπ%πôº§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°â•πë•πúÄÙÙÅπ’±∞ÅÒÅâ•πë•πúπô’πç—•ΩπΩëîÄÑÙÄÃ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBSBÔF<ÉBﬂB√BˇB„FB‡ÉB«B„FB¿ÉB˜FB€B◊BÙÅQ@Å°Ω±ë•πú∑FB◊BœB„FFF Å¿ÃΩ»»à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°›•ëùï–πç…Öô—M›•—ç°]…•—ïAïπë•πú§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãB_B√BˇB„FF0ÉBˇB◊FB◊BÎBÔF;FB√FB◊BÔF<ÉB◊F'FDÉBÀF/BˇB˚BÔB˜F?B◊FFF<à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–Åâ•–ÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—M›•—ç°	•–à∞Ä¥ƒ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π—mtÅµÖÕ≠ÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏÅµÖÕ≠ÃÄÙÅ=…•ù•πÖ±…Öô—M›•—ç†π›…•—ï5ÖÕ≠Ã°â•–∞Åπï·–§ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅçÖ—ç†Ä°%±±ïùÖ±…ù’µïπ—·çï¡—•Ω∏Å•πŸÖ±•ê§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞Å•πŸÖ±•êπùï—5ïÕÕÖùî†§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å)M=9=â©ïç–ÅΩ±ë•ï±ëÃÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–Å…ï≈’ïÕ—Mï≈’ïπçîÄÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—M›•—ç°]…•—ïMï≈’ïπçîà∞Ä¿§Ä¨ÄƒÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃÄÙÅπï‹Å)M=9=â©ïç–°Ω±ë•ï±ëÃπ—ΩM—…•πú†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃπ¡’–†âç…Öô—M›•—ç°]…•—ïMï≈’ïπçîà∞Å…ï≈’ïÕ—Mï≈’ïπçî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Åï……Ω»§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃÄÙÅΩ±ë•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB‘ÉFB”B√BÔB˚FF0ÉBˇB˚B”BœB˚FB˚BÀB„FF0ÉBﬂB√BˇB„FF0ÉB«B„FB¿à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πç…Öô—M›•—ç°]…•—ïAïπë•πúÄÙÅ—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅµΩëâ’Õ·ïç’—Ω»πï·ïç’—î††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ·çï¡—•Ω∏ÅôÖ•±’…îÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å•πëï‡∞ÅΩ…•ù•πÖ±Mç…ïï∏∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—°…Ω‹Åπï‹Å%=·çï¡—•Ω∏†ãBÉB√B«B˚FB„B‰ÉFB◊B€B„BÉB˚FFB√B˜B˚BÀBÔB◊BÙà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å5Ωëâ’ÕQç¡±•ïπ–°â•πë•πúπ°ΩÕ–∞Åâ•πë•πúπ¡Ω…–§π›…•—ï5ÖÕ¨†(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâ•πë•πúπ’π•–∞Åâ•πë•πúπÖëë…ïÕÃ∞ÅµÖÕ≠Õl¡t∞ÅµÖÕ≠Õl≈t§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Åï……Ω»§ÅÏÅôÖ•±’…îÄÙÅï……Ω»ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å·çï¡—•Ω∏Å…ïÕ’±–ÄÙÅôÖ•±’…îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…’π=πU•Q°…ïÖê††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πç…Öô—M›•—ç°]…•—ïAïπë•πúÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å•πëï‡∞ÅΩ…•ù•πÖ±Mç…ïï∏∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—M›•—ç°]…•—ïMï≈’ïπçîà∞Ä¿§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÑÙÅ…ï≈’ïÕ—Mï≈’ïπçî(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅ›•ëùï–πâ’——ΩπM—Ö—îÄÑÙÅ¡…ïŸ•Ω’Ã§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…ïÕ’±–ÄÑÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃÄÙÅΩ±ë•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãB{F#B„B«BÎB¿ÉBﬂB√BˇB„FB‡ÉB«B„FB¿ÉBˇB◊FB◊BÎBÔF;FB√FB◊BÔF<ËÄà(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¨ÅëïÕç…•âï5Ωëâ’Õ……Ω»°…ïÕ’±–§∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ)M=9=â©ïç–ÅΩ±ëA…Ω©ïç–ÄÙÅ¡…Ω©ïç—·—…Ö•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πâ’——ΩπM—Ö—îÄÙÅπï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö¡ï…Õ•Õ—A…Ω©ïç—9Ω‹°—…’î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πâ’——ΩπM—Ö—îÄÙÅ¡…ïŸ•Ω’ÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃÄÙÅΩ±ë•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡…Ω©ïç—·—…Ö•ï±ëÃÄÙÅΩ±ëA…Ω©ïç–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÖŸïç—•ŸïMç…ïï∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBGB„FÉBﬂB√BˇB„FB√BÙ∞ÉB˜B¯ÉBˇFB˚B◊BÎFÉB˜B‘ÉFB˚FFB√B˜FGBÙà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBB◊FB◊BÎBÔF;FB√FB◊BÔF0ËÄàÄ¨Ä°πï·–Ä¸ÄãBKBkBlàÄËÄãBKBØBkBlà§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅ≈’ï’ï…Öô—	•—	’——Ωπ]…•—î°]•ëùï–Å›•ëùï–∞Å	•πë•πùIï≈’ïÕ–Åâ•πë•πú∞Å•π–Å•πëï‡∞Å•π–Åâ•–∞ÅâΩΩ±ïÖ∏Å¡…ïÕÕïê§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π—mtÅµÖÕ≠ÃÄÙÅ…Öô—5Ωµïπ—Ö…Â	’——Ω∏πµÖÕ≠Ã°â•–∞Å¡…ïÕÕïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅµΩëâ’Õ·ïç’—Ω»πï·ïç’—î††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ·çï¡—•Ω∏ÅôÖ•±’…îÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°¡…ïÕÕïêÄòòÄ†ÖΩ¡ï…Ö—Ω…5ΩëîÅÒÄÖÖç—•Ÿ•—ÂIïÕ’µïêÅÒÄÖ›•ëùï—ÃπçΩπ—Ö•πÃ°›•ëùï–§§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—°…Ω‹Åπï‹Å%=·çï¡—•Ω∏†ãBÉB√B«B˚FB„B‰ÉFB◊B€B„BÉB˚FFB√B˜B˚BÀBÔB◊BÙà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å5Ωëâ’ÕQç¡±•ïπ–°â•πë•πúπ°ΩÕ–∞Åâ•πë•πúπ¡Ω…–§π›…•—ï5ÖÕ¨°â•πë•πúπ’π•–∞Åâ•πë•πúπÖëë…ïÕÃ∞ÅµÖÕ≠Õl¡t∞ÅµÖÕ≠Õl≈t§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Åï……Ω»§ÅÏÅôÖ•±’…îÄÙÅï……Ω»ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å·çï¡—•Ω∏Å…ïÕ’±–ÄÙÅôÖ•±’…îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…ïÕ’±–ÄÑÙÅπ’±∞§Å…’π=πU•Q°…ïÖê††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖÖç—•Ÿ•—ÂIïÕ’µïê§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞Ä°¡…ïÕÕïêÄ¸ÄãBwB‘ÉFB”B√BÔB˚FF0ÉFFFB√B˜B˚BÀB„FF0ÉB«B„FËÄàÄËÄãBwB‘ÉFB”B√BÔB˚FF0ÉFB«FB˚FB„FF0ÉB«B„FËÄà§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¨ÅëïÕç…•âï5Ωëâ’Õ……Ω»°…ïÕ’±–§∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅ•π–Å°•—I’π—•µï]•ëùï–°ô±ΩÖ–Å‡∞Åô±ΩÖ–Å‰§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å§ÄÙÅ›•ëùï—ÃπÕ•Èî†§Ä¥ÄƒÏÅ§Ä¯ÙÄ¿ÏÅ§¥¥§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Å‹ÄÙÅ›•ëùï—Ãπùï–°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πŸ•Õ•â±îÄòòÄÖ•Õâê°‹§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÄÑ†ãBáBˇB„FB˚BËÅ…Öô–àπï≈’Ö±Ã°‹π—Â¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÄÖ‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—1•Õ—Y•Õ•â±ï%π]Ω…¨à∞Å—…’î§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÄÑ††ãBkB˜B˚BˇBÎB¿àπï≈’Ö±Ã°‹π—Â¡î§ÅÒÄãBkB˚B˜FFB√B˜FB˜B√F<ÉBÎB˜B˚BˇBÎB¿Å…Öô–àπï≈’Ö±Ã°‹π—Â¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄãBB◊FB◊FB˚B–ÉB˜B¿ÉF7BÎFB√BÙàπï≈’Ö±Ã°‹π—Â¡î§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÄÖ‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—	’——ΩπY•Õ•â±ï%π]Ω…¨à∞Å—…’î§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÄÑ°=…•ù•πÖ±…Öô—M›•—ç†πÕ’¡¡Ω…—Ã°‹π—Â¡î§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÄÖ‹πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—M›•—ç°Y•Õ•â±ï%π]Ω…¨à∞Å=…•ù•πÖ±…Öô—M›•—ç†πŸ•Õ•â±ï%π]Ω…¨°‹πï·—…Ö•ï±ëÃ§§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ‡Ä¯ÙÅ‹π‡ÄòòÅ‡ÄÙÅ‹π‡Ä¨Å‹π‹ÄòòÅ‰Ä¯ÙÅ‹π‰ÄòòÅ‰ÄÙÅ‹π‰Ä¨Å‹π†§Å…ï—’…∏Å§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ä¥ƒÏ(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅâΩΩ±ïÖ∏ÅΩπ=¡ï…Ö—Ω…QΩ’ç†°5Ω—•ΩπŸïπ–ÅïŸïπ–§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å‡ÄÙÅïŸïπ–πùï—`†§∞Å‰ÄÙÅïŸïπ–πùï—d†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅÖç—•Ω∏ÄÙÅïŸïπ–πùï—ç—•Ωπ5ÖÕ≠ïê†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Öç—•Ω∏ÄÙÙÅ5Ω—•ΩπŸïπ–πQ%=9}=]8§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–Å°•–ÄÙÅ°•—I’π—•µï]•ëùï–°‡∞Å‰§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ï…Ö—Ω…5Ωµïπ—Ö…ÂÖπë•ëÖ—îÄÙÄ¥ƒÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ï…Ö—Ω…ïÕ—’…îπâïù•∏°°•–∞Å‡∞Å‰∞ÅMÂÕ—ïµ±Ωç¨πï±Ö¡ÕïëIïÖ±—•µî†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°°•–Ä¯ÙÄ¿ÄòòÅ•Õ…Öô—5Ωµïπ—Ö…Â	’——Ω∏°›•ëùï—Ãπùï–°°•–§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ›•ëùï—Ãπùï–°°•–§πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—	’——Ωπ5Ωëîà∞Ä¿§ÄÙÙÄ¿§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâïù•π…Öô—	•—	’——Ω∏°›•ëùï—Ãπùï–°°•–§∞Å°•–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°Öç—•Ω∏ÄÙÙÅ5Ω—•ΩπŸïπ–πQ%=9}5=Y§ÅΩ¡ï…Ö—Ω…ïÕ—’…îπµΩŸî°‡∞Å‰∞Åë¿†‘§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅï±ÕîÅ•òÄ°Öç—•Ω∏ÄÙÙÅ5Ω—•ΩπŸïπ–πQ%=9}U@§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅçÖπë•ëÖ—îÄÙÅΩ¡ï…Ö—Ω…5Ωµïπ—Ö…ÂÖπë•ëÖ—îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–Å—Ö…ùï–ÄÙÅΩ¡ï…Ö—Ω…ïÕ—’…îπïπê°°•—I’π—•µï]•ëùï–°‡∞Å‰§∞Å‡∞Å‰∞ÅMÂÕ—ïµ±Ωç¨πï±Ö¡ÕïëIïÖ±—•µî†§∞Åë¿†‘§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°çÖπë•ëÖ—îÄ¯ÙÄ¿§Åïπë…Öô—	•—	’——Ω∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ï…Ö—Ω…5Ωµïπ—Ö…ÂÖπë•ëÖ—îÄÙÄ¥ƒÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°çÖπë•ëÖ—îÄÄ¿ÄòòÅ—Ö…ùï–Ä¯ÙÄ¿ÄòòÅ—Ö…ùï–ÄÅ›•ëùï—ÃπÕ•Èî†§§ÅÖç—•ŸÖ—ïI’π—•µï]•ëùï–°—Ö…ùï–∞Å‡∞Å‰§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°Öç—•Ω∏ÄÙÙÅ5Ω—•ΩπŸïπ–πQ%=9}90ÅÒÅÖç—•Ω∏ÄÙÙÅ5Ω—•ΩπŸïπ–πQ%=9}A=%9QI}=]8§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ï…Ö—Ω…ïÕ—’…îπçÖπçï∞†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅïπë…Öô—	•—	’——Ω∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ï…Ö—Ω…5Ωµïπ—Ö…ÂÖπë•ëÖ—îÄÙÄ¥ƒÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å—…’îÏ(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅ…ïÕ—Ω…ï…Öô—	•—1•Õ—°ïç≠Ã°Öπë…Ω•êπçΩπ—ïπ–π•Ö±Ωù%π—ï…ôÖçîÅë•Ö±Ωú∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ©ÖŸÑπ’—•∞π1•Õ–Ò%π—ïùï»¯Åâ•—Ã∞Å•π–ÅµÖÕ¨§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ñ°ë•Ö±ΩúÅ•πÕ—ÖπçïΩòÅ±ï…—•Ö±Ωú§§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÖπë…Ω•êπ›•ëùï–π1•Õ—Y•ï‹Å±•Õ–ÄÙÄ†°±ï…—•Ö±Ωú§Åë•Ö±Ωú§πùï—1•Õ—Y•ï‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°±•Õ–ÄÙÙÅπ’±∞§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å§ÄÙÄ¿ÏÅ§ÄÅâ•—ÃπÕ•Èî†§ÏÅ§¨¨§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ±•Õ–πÕï—%—ïµ°ïç≠ïê°§∞Ä°µÖÕ¨ÄòÄ†ƒÄÅâ•—Ãπùï–°§§§§ÄÑÙÄ¿§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅΩ¡ïπ…Öô—	•—1•Õ–°]•ëùï–Å›•ëùï–∞Å•π–Å›•ëùï—%πëï‡§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–ÅΩ¡ïπ•πùMç…ïïπ%πëï‡ÄÙÅç’……ïπ—Mç…ïïπ%πëï‡Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅMç…ïï∏ÅΩ¡ïπ•πùMç…ïï∏ÄÙÅΩ¡ïπ•πùMç…ïïπ%πëï‡Ä¯ÙÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅΩ¡ïπ•πùMç…ïïπ%πëï‡ÄÅÕç…ïïπÃπÕ•Èî†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÅÕç…ïïπÃπùï–°Ω¡ïπ•πùMç…ïïπ%πëï‡§ÄËÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å›•ëùï—%πëï‡∞ÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πùmtÅÖ±±1Öâï±ÃÄÙÅ…Öô—	•—1•Õ–πô…Ωµ)ÕΩ∏†(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—)M=9……Ö‰†âç…Öô—1•Õ—1Öâï±Ãà§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ©ÖŸÑπ’—•∞π……ÖÂ1•Õ–ÒM—…•πú¯Åç°Ω•çïÃÄÙÅπï‹Å©ÖŸÑπ’—•∞π……ÖÂ1•Õ–¯†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ©ÖŸÑπ’—•∞π……ÖÂ1•Õ–Ò%π—ïùï»¯Åâ•—ÃÄÙÅπï‹Å©ÖŸÑπ’—•∞π……ÖÂ1•Õ–¯†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Åâ•–ÄÙÄ¿ÏÅâ•–ÄÄƒÿÏÅâ•–¨¨§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Ö±±1Öâï±Õmâ•—tÄÑÙÅπ’±∞ÄòòÄÖÖ±±1Öâï±Õmâ•—tπ—…•¥†§π•Õµ¡—‰†§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅç°Ω•çïÃπÖëê°Ö±±1Öâï±Õmâ•—t§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâ•—ÃπÖëê°â•–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ç°Ω•çïÃπ•Õµ¡—‰†§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBåÉFBˇB„FBÎB¿ÉB˜B◊FÉBˇB˚B”BˇB„FB√B˜B˜F/FÉBˇFB˜BÎFB˚B»∏ÉBwB√FFFB˚B‰ÉB◊BœB¯ÉB»ÉFB◊B”B√BÎFB˚FB‘∏à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—1•Õ—°ïç≠9Ω°Öπùîà∞ÅôÖ±Õî§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅÕ°Ω›π5ÖÕ¨ÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—1•Õ—%π•—•Ö±5ÖÕ¨à∞Ä¿§ÄòÄ¡·ôôôòÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ±•ŸïYÖ±’îÄÙÅ±•ŸïYÖ±’ïÃπùï–°›•ëùï—%πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°±•ŸïYÖ±’îÄÑÙÅπ’±∞§Å—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕ°Ω›π5ÖÕ¨ÄÙÅ…Öô—	•—1•Õ–πô…ΩµIïù•Õ—ï»°%π—ïùï»π¡Ö…Õï%π–°±•ŸïYÖ±’î§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Å•ùπΩ…ïê§ÅÌÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πùmtÅ…ïÖë=π±Â°Ω•çïÃÄÙÅπï‹ÅM—…•πùmç°Ω•çïÃπÕ•Èî†•tÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å¨ÄÙÄ¿ÏÅ¨ÄÅç°Ω•çïÃπÕ•Èî†§ÏÅ¨¨¨§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ïÖë=π±Â°Ω•çïÕm≠tÄÙÄ†°Õ°Ω›π5ÖÕ¨ÄòÄ†ƒÄÅâ•—Ãπùï–°¨§§§ÄÑÙÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÄãärLÄÄàÄËÄãä^,ÄÄà§Ä¨Åç°Ω•çïÃπùï–°¨§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å±ï…—•Ö±Ωúπ	’•±ëï»°5Ö•πç—•Ÿ•—‰π—°•Ã§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—Q•—±î°›•ëùï–π—ï·–ÄÙÙÅπ’±∞Ä¸ÄãBáBˇB„FB˚BËàÄËÅ›•ëùï–π—ï·–§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—%—ïµÃ°…ïÖë=π±Â°Ω•çïÃ∞Åπ’±∞§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—5ïÕÕÖùî†ãB_B˜B√FB◊B˜B„F<ÉB”B˚FFFBˇB˜F,ÉFB˚BÔF3BÎB¯ÉB”BÔF<ÉBˇFB˚FBÛB˚FFB¿ÉB»ÉFB√B«B˚FB◊BÉFB◊B€B„BÛB‘à§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—AΩÕ•—•Ÿï	’——Ω∏†ãB_B√BÎFF/FF0à∞Åπ’±∞§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•π–Å•π•—•Ö±5ÖÕ¨ÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—1•Õ—%π•—•Ö±5ÖÕ¨à∞Ä¿§ÄòÄ¡·ôôôòÏ(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅç’……ïπ–ÄÙÅ±•ŸïYÖ±’ïÃπùï–°›•ëùï—%πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ç’……ïπ–ÄÑÙÅπ’±∞§Å—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π•—•Ö±5ÖÕ¨ÄÙÅ…Öô—	•—1•Õ–πô…ΩµIïù•Õ—ï»°%π—ïùï»π¡Ö…Õï%π–°ç’……ïπ–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Å•ùπΩ…ïê§ÅÌÙ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π—mtÅµÖÕ¨ÄÙÅÌ•π•—•Ö±5ÖÕ≠ÙÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π—mtÅçΩπô•…µïë5ÖÕ¨ÄÙÅÌ•π•—•Ö±5ÖÕ≠ÙÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π—mtÅç°ÖπùïMï≈’ïπçîÄÙÅÏ¡ÙÏ(ÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖπmtÅç°ïç≠ïêÄÙÅπï‹ÅâΩΩ±ïÖπmâ•—ÃπÕ•Èî†•tÏ(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å¨ÄÙÄ¿ÏÅ¨ÄÅâ•—ÃπÕ•Èî†§ÏÅ¨¨¨§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅç°ïç≠ïëm≠tÄÙÄ°µÖÕ≠l¡tÄòÄ†ƒÄÅâ•—Ãπùï–°¨§§§ÄÑÙÄ¿Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅM—…•πúÅçΩππïç—•Ω∏ÄÙÅ›•ëùï–πçΩππïç—•Ωπ%πôºÄÙÙÅπ’±∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÄààÄËÅ›•ëùï–πçΩππïç—•Ωπ%πôºπ—…•¥†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å	•πë•πùIï≈’ïÕ–Åâ•πë•πúÄÙÅçΩππïç—•Ω∏π•Õµ¡—‰†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸Åπ’±∞ÄËÅ¡Ö…Õï	•πë•πú†¥ƒ∞ÅçΩππïç—•Ω∏§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖçΩππïç—•Ω∏π•Õµ¡—‰†§ÄòòÅâ•πë•πúÄÙÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBFB˚BÀB◊FF0ÅQ@ÉBˇFB„BÀF?BﬂBÎFÉB«B„FB˚BÀB˚BœB¯ÉFBˇB„FBÎB¿à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°â•πë•πúÄÑÙÅπ’±∞ÄòòÅâ•πë•πúπô’πç—•ΩπΩëîÄÑÙÄÃ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBGB„FB˚BÀF/B‰ÉFBˇB„FB˚BËÉB„FBˇB˚BÔF3BﬂFB◊FÅ°Ω±ë•πú∑FB◊BœB„FFF Å¿ÃΩ¿ÿà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ±ï…—•Ö±ΩúÅë•Ö±ΩúÄÙÅπï‹Å±ï…—•Ö±Ωúπ	’•±ëï»°5Ö•πç—•Ÿ•—‰π—°•Ã§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—Q•—±î°›•ëùï–π—ï·–ÄÙÙÅπ’±∞ÅÒÅ›•ëùï–π—ï·–π•Õµ¡—‰†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÄãBKF/B«B◊FB‡ÉBˇFB˜BÎFF,àÄËÅ›•ëùï–π—ï·–§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—5’±—•°Ω•çï%—ïµÃ°ç°Ω•çïÃπ—Ω……Ö‰°πï‹ÅM—…•πùl¡t§∞Åç°ïç≠ïê∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ°ê∞Å¡ΩÕ•—•Ω∏∞Å•Õ°ïç≠ïê§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å›•ëùï—%πëï‡∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅêπë•Õµ•ÕÃ†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°¡ΩÕ•—•Ω∏ÄÄ¿ÅÒÅ¡ΩÕ•—•Ω∏Ä¯ÙÅâ•—ÃπÕ•Èî†§§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–Åâ•–ÄÙÅâ•—Ãπùï–°¡ΩÕ•—•Ω∏§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–Å¡…ïŸ•Ω’Õ5ÖÕ¨ÄÙÅµÖÕ≠l¡tÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–Åπï·–ÄÙÄ°•Õ°ïç≠ïê(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÅµÖÕ≠l¡tÅÄ†ƒÄÅâ•–§ÄËÅµÖÕ≠l¡tÄòÅ¯†ƒÄÅâ•–§§ÄòÄ¡·ôôôòÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°πï·–ÄÙÙÅ¡…ïŸ•Ω’Õ5ÖÕ¨§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°â•πë•πúÄÙÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ)M=9=â©ïç–Å¡…ïŸ•Ω’ÃÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ)M=9=â©ïç–Å¡…ïŸ•Ω’ÕA…Ω©ïç–ÄÙÅ¡…Ω©ïç—·—…Ö•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅçΩµµ•——ïêÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃÄÙÅπï‹Å)M=9=â©ïç–°¡…ïŸ•Ω’Ãπ—ΩM—…•πú†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃπ¡’–†âç…Öô—1•Õ—%π•—•Ö±5ÖÕ¨à∞Åπï·–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö¡ï…Õ•Õ—A…Ω©ïç—9Ω‹°—…’î§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—°…Ω‹Åπï‹Å%=·çï¡—•Ω∏†ãBFB˚B◊BÎFÉB˜B‘ÉFB˚FFB√B˜FGBÙà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩµµ•——ïêÄÙÅ—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµÖÕ≠l¡tÄÙÅπï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩπô•…µïë5ÖÕ≠l¡tÄÙÅπï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ±•ŸïYÖ±’ïÃπ¡’–°›•ëùï—%πëï‡∞Å%π—ïùï»π—ΩM—…•πú°πï·–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏ÅôÖ•±’…î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBwB‘ÉFB”B√BÔB˚FF0ÉFB˚FFB√B˜B„FF0ÉFBˇB„FB˚BËËÄàÄ¨ÅôÖ•±’…îπùï—5ïÕÕÖùî†§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅô•πÖ±±‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖçΩµµ•——ïê§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃÄÙÅ¡…ïŸ•Ω’ÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡…Ω©ïç—·—…Ö•ï±ëÃÄÙÅ¡…ïŸ•Ω’ÕA…Ω©ïç–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµÖÕ≠l¡tÄÙÅ¡…ïŸ•Ω’Õ5ÖÕ¨Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ïÕ—Ω…ï…Öô—	•—1•Õ—°ïç≠Ã°ê∞Åâ•—Ã∞Å¡…ïŸ•Ω’Õ5ÖÕ¨§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÖŸïç—•ŸïMç…ïï∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµÖÕ≠l¡tÄÙÅπï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–Å…ï≈’ïÕ—ïë5ÖÕ¨ÄÙÅπï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–ÅÕï≈’ïπçîÄÙÄ¨≠ç°ÖπùïMï≈’ïπçïl¡tÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµΩëâ’Õ·ïç’—Ω»πï·ïç’—î††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ·çï¡—•Ω∏ÅôÖ•±’…îÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å›•ëùï—%πëï‡∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—°…Ω‹Åπï‹Å%=·çï¡—•Ω∏†ãBÉB√B«B˚FB„B‰ÉFB◊B€B„BÉB˚FFB√B˜B˚BÀBÔB◊BÙà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å5Ωëâ’ÕQç¡±•ïπ–°â•πë•πúπ°ΩÕ–∞Åâ•πë•πúπ¡Ω…–§π›…•—ïM•πù±î†(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâ•πë•πúπ’π•–∞Åâ•πë•πúπÖëë…ïÕÃ∞Å…ï≈’ïÕ—ïë5ÖÕ¨§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Åï……Ω»§ÅÏÅôÖ•±’…îÄÙÅï……Ω»ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å·çï¡—•Ω∏Å…ïÕ’±–ÄÙÅôÖ•±’…îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…’π=πU•Q°…ïÖê††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å›•ëùï—%πëï‡∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…ïÕ’±–ÄÙÙÅπ’±∞§ÅçΩπô•…µïë5ÖÕ≠l¡tÄÙÅ…ï≈’ïÕ—ïë5ÖÕ¨Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õï≈’ïπçîÄÙÙÅç°ÖπùïMï≈’ïπçïl¡t§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…ïÕ’±–ÄÑÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµÖÕ≠l¡tÄÙÅçΩπô•…µïë5ÖÕ≠l¡tÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ïÕ—Ω…ï…Öô—	•—1•Õ—°ïç≠Ã°ê∞Åâ•—Ã∞ÅµÖÕ≠l¡t§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBwB‘ÉFB”B√BÔB˚FF0ÉBﬂB√BˇB„FB√FF0ÉBÛB√FBÎFÉFBˇB„FBÎB¿ËÄà(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¨ÅëïÕç…•âï5Ωëâ’Õ……Ω»°…ïÕ’±–§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ±•ŸïYÖ±’ïÃπ¡’–°›•ëùï—%πëï‡∞Å%π—ïùï»π—ΩM—…•πú†(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ïÕ’±–ÄÙÙÅπ’±∞Ä¸Å…ï≈’ïÕ—ïë5ÖÕ¨ÄËÅçΩπô•…µïë5ÖÕ≠l¡t§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—AΩÕ•—•Ÿï	’——Ω∏†ãBOB˚FB˚BÀB¯à∞Åπ’±∞§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—9ïùÖ—•Ÿï	’——Ω∏†ãB_B√BÎFF/FF0à∞Åπ’±∞§πç…ïÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅë•Ö±ΩúπÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÅÙ(((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅÖç—•ŸÖ—ï…Öô—ΩπÕ—Öπ—	’——Ω∏°]•ëùï–Å›•ëùï–∞Å•π–Å•πëï‡§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ	•πë•πùIï≈’ïÕ–Åâ•πë•πúÄÙÅ¡Ö…Õï	•πë•πú†¥ƒ∞Å›•ëùï–πçΩππïç—•Ωπ%πôº§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—	’——ΩπΩπÕ—Öπ—πÖâ±ïêà∞ÅôÖ±Õî§ÅÒÅâ•πë•πúÄÙÙÅπ’±∞ÅÒÅâ•πë•πúπô’πç—•ΩπΩëîÄÑÙÄÃ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãB_B√B”B√B‰ÅQ@Å°Ω±ë•πú∑FB◊BœB„FFF ÉB”BÔF<ÉF7FB˚B‰ÉBÎB˚B˜FFB√B˜FB˜B˚B‰ÉBÎB˜B˚BˇBÎB‡à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§ÏÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Åô±ΩÖ—•πúÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—	’——ΩπΩπÕ—Öπ—QÂ¡îà∞Ä¿§ÄÙÙÄƒÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–Å•π—ïùï»Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π—mtÅ›Ω…ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ô±ΩÖ—•πú§ÅÏÅ›Ω…ëÃÄÙÅâë…Öô—Iïù•Õ—ï…Ωëïåπô±ΩÖ—Iïù•Õ—ï…Ã°…Öô—ΩπÕ—Öπ—	’——Ω∏πô±ΩÖ—•πú°›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âç…Öô—	’——ΩπΩπÕ—Öπ–à∞Äà¿à§§∞ÅôÖ±Õî∞ÅôÖ±Õî§ÏÅ•π—ïùï»ÄÙÄ¿ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅï±ÕîÅÏÅ•π—ïùï»ÄÙÅ…Öô—ΩπÕ—Öπ—	’——Ω∏π•π—ïùï»°›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âç…Öô—	’——ΩπΩπÕ—Öπ–à∞Äà¿à§§ÏÅ›Ω…ëÃÄÙÅπ’±∞ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°%±±ïùÖ±…ù’µïπ—·çï¡—•Ω∏Å•πŸÖ±•ê§ÅÏÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞Å•πŸÖ±•êπùï—5ïÕÕÖùî†§∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§ÏÅ…ï—’…∏ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅâΩΩ±ïÖ∏Å›…•—ï±ΩÖ–ÄÙÅô±ΩÖ—•πúÏ(ÄÄÄÄÄÄÄÄÄÄÄÅµΩëâ’Õ·ïç’—Ω»πï·ïç’—î††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ·çï¡—•Ω∏ÅôÖ•±’…îÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ¡ï…Ö—Ω…5ΩëîÅÒÄÖÖç—•Ÿ•—ÂIïÕ’µïêÅÒÄÖ›•ëùï—ÃπçΩπ—Ö•πÃ°›•ëùï–§§Å—°…Ω‹Åπï‹Å%=·çï¡—•Ω∏†ãBÉB√B«B˚FB„B‰ÉFB◊B€B„BÉB˚FFB√B˜B˚BÀBÔB◊BÙà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ5Ωëâ’ÕQç¡±•ïπ–Åç±•ïπ–ÄÙÅπï‹Å5Ωëâ’ÕQç¡±•ïπ–°â•πë•πúπ°ΩÕ–∞Åâ•πë•πúπ¡Ω…–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°›…•—ï±ΩÖ–§Åç±•ïπ–π›…•—ï5’±—•¡±î°â•πë•πúπ’π•–∞Åâ•πë•πúπÖëë…ïÕÃ∞Å›Ω…ëÃ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅï±ÕîÅç±•ïπ–π›…•—ïM•πù±î°â•πë•πúπ’π•–∞Åâ•πë•πúπÖëë…ïÕÃ∞Å•π—ïùï»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Åï……Ω»§ÅÏÅôÖ•±’…îÄÙÅï……Ω»ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å·çï¡—•Ω∏Å…ïÕ’±–ÄÙÅôÖ•±’…îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…’π=πU•Q°…ïÖê††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö›•ëùï—ÃπçΩπ—Ö•πÃ°›•ëùï–§ÅÒÄÖΩ¡ï…Ö—Ω…5ΩëîÅÒÅ›•ëùï—Ãπ•πëï·=ò°›•ëùï–§ÄÑÙÅ•πëï‡§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…ïÕ’±–ÄÙÙÅπ’±∞§Å±•ŸïYÖ±’ïÃπ¡’–°•πëï‡∞Äâ=,à§ÏÅï±ÕîÅ±•ŸïYÖ±’ïÃπ¡’–°•πëï‡∞ÄâIHËÄàÄ¨ÅëïÕç…•âï5Ωëâ’Õ……Ω»°…ïÕ’±–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞Å…ïÕ’±–ÄÙÙÅπ’±∞Ä¸ÄãBkB˚B˜FFB√B˜FB¿ÉBﬂB√BˇB„FB√B˜B¿àÄËÄãBwB‘ÉFB”B√BÔB˚FF0ÉBﬂB√BˇB„FB√FF0ÉBÎB˚B˜FFB√B˜FFËÄàÄ¨ÅëïÕç…•âï5Ωëâ’Õ……Ω»°…ïÕ’±–§∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÄº®®Å1ïùÖç‰Å—›ºµâ’——Ω∏ÅÕï—¡Ω•π–ËÅÕÖôîÅ±ΩçÖ∞ÅÕ—ï¿ÅΩ»Å¿ÿÅ°Ω±ë•πúµ…ïù•Õ—ï»Å›…•—î∏Ä®º(ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅÖë©’Õ—Mï—¡Ω•π–°]•ëùï–Å›•ëùï–∞Å•π–Åë•…ïç—•Ω∏§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ¡ï…Ö—Ω…5ΩëîÅÒÄÖÖç—•Ÿ•—ÂIïÕ’µïêÅÒÅïë•—Ω»ÄÑÙÅ—°•Ã(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅ›•ëùï–ÄÙÙÅπ’±∞ÅÒÅë•…ïç—•Ω∏ÄÙÙÄ¿§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–Å•πëï‡ÄÙÅ›•ëùï—Ãπ•πëï·=ò°›•ëùï–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°•πëï‡ÄÄ¿ÅÒÄÑãB_B√B”B√FFB„BËàπï≈’Ö±Ã°›•ëùï–π—Â¡î§§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅëΩ’â±îÅµ•∏ÄÙÅ5Ö—†πµ•∏°›•ëùï–πµ•πYÖ±’î∞Å›•ëùï–πµÖ·YÖ±’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅëΩ’â±îÅµÖ‡ÄÙÅ5Ö—†πµÖ‡°›•ëùï–πµ•πYÖ±’î∞Å›•ëùï–πµÖ·YÖ±’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ’â±îπ•Õ•π•—î°µ•∏§ÅÒÄÖΩ’â±îπ•Õ•π•—î°µÖ‡§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅµ•∏ÄÅ%π—ïùï»π5%9}Y1UÅÒÅµÖ‡Ä¯Å%π—ïùï»π5a}Y1U§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB◊BÎB˚FFB◊BÎFB˜F/B‰ÉB”B„B√BˇB√BﬂB˚BÙÉBﬂB√B”B√FFB„BÎB¿à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–Å±Ω›ï»ÄÙÄ°•π–§Å5Ö—†πçï•∞°µ•∏§∞Å’¡¡ï»ÄÙÄ°•π–§Å5Ö—†πô±ΩΩ»°µÖ‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°±Ω›ï»Ä¯Å’¡¡ï»§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–ÅΩ±ëYÖ±’îÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—%π–†ââ±ΩΩëÕ≠ÂMï—¡Ω•π—YÖ±’îà∞Å±Ω›ï»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–ÅÕ—ï¿ÄÙÅ5Ö—†πµÖ‡†ƒ∞Å5Ö—†πµ•∏†ÿ‘‘Ã‘∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—%π–†ââ±ΩΩëÕ≠ÂMï—¡Ω•π—M—ï¿à∞Äƒ§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å±ΩπúÅ…ï≈’ïÕ—ïêÄÙÄ°±Ωπú§ÅΩ±ëYÖ±’îÄ¨Ä°ë•…ïç—•Ω∏ÄÄ¿Ä¸Ä¥°±Ωπú§ÅÕ—ï¿ÄËÅÕ—ï¿§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–Åπï·–ÄÙÄ°•π–§Å5Ö—†πµÖ‡°±Ω›ï»∞Å5Ö—†πµ•∏°’¡¡ï»∞Å…ï≈’ïÕ—ïê§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°πï·–ÄÙÙÅΩ±ëYÖ±’î§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅM—…•πúÅçΩππïç—•Ω∏ÄÙÅ›•ëùï–πçΩππïç—•Ωπ%πôºÄÙÙÅπ’±∞Ä¸ÄààÄËÅ›•ëùï–πçΩππïç—•Ωπ%πôºπ—…•¥†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å	•πë•πùIï≈’ïÕ–Åâ•πë•πúÄÙÅçΩππïç—•Ω∏π•Õµ¡—‰†§Ä¸Åπ’±∞ÄËÅ¡Ö…Õï	•πë•πú†¥ƒ∞ÅçΩππïç—•Ω∏§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖçΩππïç—•Ω∏π•Õµ¡—‰†§ÄòòÄ°â•πë•πúÄÙÙÅπ’±∞ÅÒÅâ•πë•πúπô’πç—•ΩπΩëîÄÑÙÄÃ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅπï·–ÄÄ¿ÅÒÅπï·–Ä¯Äÿ‘‘Ã‘§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBSBÔF<ÉBﬂB√B”B√FFB„BÎB¿ÉB˜FB€B◊BÙÅQ@Å°Ω±ë•πú∑FB◊BœB„FFF Å¿ÃΩ¿ÿÄ†√äLÿ‘‘Ã‘§à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Åë•—Ω…ÖπŸÖÃÅΩ…•ù•πÖ±ÖπŸÖÃÄÙÅ—°•ÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅMç…ïï∏ÅΩ…•ù•πÖ±Mç…ïï∏ÄÙÅç’……ïπ—Mç…ïïπ%πëï‡Ä¯ÙÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅç’……ïπ—Mç…ïïπ%πëï‡ÄÅÕç…ïïπÃπÕ•Èî†§Ä¸ÅÕç…ïïπÃπùï–°ç’……ïπ—Mç…ïïπ%πëï‡§ÄËÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–ÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡ÄÙÅç’……ïπ—Mç…ïïπ%πëï‡Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅI’ππÖâ±îÅÖ¡¡±‰ÄÙÄ†§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ¡ï…Ö—Ω…5ΩëîÅÒÄÖÖç—•Ÿ•—ÂIïÕ’µïêÅÒÅïë•—Ω»ÄÑÙÅΩ…•ù•πÖ±ÖπŸÖÃ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅΩ…•ù•πÖ±Mç…ïï∏ÄÙÙÅπ’±∞ÅÒÅç’……ïπ—Mç…ïïπ%πëï‡ÄÑÙÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅç’……ïπ—Mç…ïïπ%πëï‡ÄÄ¿ÅÒÅç’……ïπ—Mç…ïïπ%πëï‡Ä¯ÙÅÕç…ïïπÃπÕ•Èî†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅÕç…ïïπÃπùï–°ç’……ïπ—Mç…ïïπ%πëï‡§ÄÑÙÅΩ…•ù•πÖ±Mç…ïï∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÅ•πëï‡Ä¯ÙÅ›•ëùï—ÃπÕ•Èî†§ÅÒÅ›•ëùï—Ãπùï–°•πëï‡§ÄÑÙÅ›•ëùï–§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ)M=9=â©ïç–Å¡…ïŸ•Ω’ÃÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃÄÙÅπï‹Å)M=9=â©ïç–°¡…ïŸ•Ω’Ãπ—ΩM—…•πú†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃπ¡’–†ââ±ΩΩëÕ≠ÂMï—¡Ω•π—YÖ±’îà∞Åπï·–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö¡ï…Õ•Õ—A…Ω©ïç—9Ω‹°—…’î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃÄÙÅ¡…ïŸ•Ω’ÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÖŸïç—•ŸïMç…ïï∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB‘ÉFB”B√BÔB˚FF0ÉFB˚FFB√B˜B„FF0ÉFFFB√BÀBÎFà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ±•ŸïYÖ±’ïÃπ¡’–°•πëï‡∞Å%π—ïùï»π—ΩM—…•πú°πï·–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBèFFB√BÀBÎB¿ËÄàÄ¨Åπï·–∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏ÅôÖ•±’…î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃÄÙÅ¡…ïŸ•Ω’ÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÖŸïç—•ŸïMç…ïï∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB‘ÉFB”B√BÔB˚FF0ÉB„BﬂBÛB◊B˜B„FF0ÉFFFB√BÀBÎFà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅÙÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°â•πë•πúÄÙÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖ¡¡±‰π…’∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅµΩëâ’Õ·ïç’—Ω»πï·ïç’—î††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ·çï¡—•Ω∏ÅôÖ•±’…îÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ¡ï…Ö—Ω…5ΩëîÅÒÄÖÖç—•Ÿ•—ÂIïÕ’µïêÅÒÅïë•—Ω»ÄÑÙÅΩ…•ù•πÖ±ÖπŸÖÃ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄÖ›•ëùï—ÃπçΩπ—Ö•πÃ°›•ëùï–§§Å—°…Ω‹Åπï‹Å%=·çï¡—•Ω∏†ãBÉB√B«B˚FB„B‰ÉFB◊B€B„BÉB˚FFB√B˜B˚BÀBÔB◊BÙà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å5Ωëâ’ÕQç¡±•ïπ–°â•πë•πúπ°ΩÕ–∞Åâ•πë•πúπ¡Ω…–§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπ›…•—ïM•πù±î°â•πë•πúπ’π•–∞Åâ•πë•πúπÖëë…ïÕÃ∞Åπï·–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Åï……Ω»§ÅÏÅôÖ•±’…îÄÙÅï……Ω»ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å·çï¡—•Ω∏Å…ïÕ’±–ÄÙÅôÖ•±’…îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…’π=πU•Q°…ïÖê††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…ïÕ’±–ÄÙÙÅπ’±∞§ÅÖ¡¡±‰π…’∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅï±ÕîÅ•òÄ°ïë•—Ω»ÄÙÙÅΩ…•ù•πÖ±ÖπŸÖÃÄòòÅ›•ëùï—ÃπçΩπ—Ö•πÃ°›•ëùï–§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãB{F#B„B«BÎB¿ÉBﬂB√BˇB„FB‡ÉFFFB√BÀBÎB‡ËÄàÄ¨ÅëïÕç…•âï5Ωëâ’Õ……Ω»°…ïÕ’±–§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÄº®®Åï±ÖÂïêÅΩ¡ï…Ö—Ω»Åë•Ö±ΩùÃÅµ’Õ–ÅπïŸï»ÅçΩµµ•–Å•π—ºÅÑÅ…ï¡±ÖçïêÅçÖπŸÖÃΩ¡Öùî∏Ä®º(ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅâΩΩ±ïÖ∏Å•ÕI’π—•µï]•ëùï—’……ïπ–°]•ëùï–Å›•ëùï–∞Å•π–Å•πëï‡∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅMç…ïï∏ÅΩ…•ù•πÖ±Mç…ïï∏∞Å•π–ÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅÖç—•Ÿ•—ÂIïÕ’µïêÄòòÅΩ¡ï…Ö—Ω…5ΩëîÄòòÅïë•—Ω»ÄÙÙÅ—°•Ã(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅΩ…•ù•πÖ±Mç…ïï∏ÄÑÙÅπ’±∞ÄòòÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡Ä¯ÙÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅç’……ïπ—Mç…ïïπ%πëï‡ÄÙÙÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅç’……ïπ—Mç…ïïπ%πëï‡ÄÅÕç…ïïπÃπÕ•Èî†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅÕç…ïïπÃπùï–°ç’……ïπ—Mç…ïïπ%πëï‡§ÄÙÙÅΩ…•ù•πÖ±Mç…ïï∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ•πëï‡Ä¯ÙÄ¿ÄòòÅ•πëï‡ÄÅ›•ëùï—ÃπÕ•Èî†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ›•ëùï—Ãπùï–°•πëï‡§ÄÙÙÅ›•ëùï–Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÄº®®Å¡¡±‰ÅÖ∏ÅΩ¡ï…Ö—Ω»ÅŸÖ±’îÅΩπ±‰Å›°ï∏Å•—ÃÅ¡…Ω©ïç–ÅÕπÖ¡Õ°Ω–Å¡ï…Õ•Õ—Ã∏Ä®º(ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅâΩΩ±ïÖ∏ÅÕÖŸïI’π—•µï1•πïÖ…Mï—¡Ω•π–°]•ëùï–Å›•ëùï–∞Å•π–Å•πëï‡∞Å•π–Åπï·–∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅMç…ïï∏ÅΩ…•ù•πÖ±Mç…ïï∏∞Å•π–ÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å•πëï‡∞ÅΩ…•ù•πÖ±Mç…ïï∏∞ÅΩ…•ù•πÖ±Mç…ïïπ%πëï‡§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ)M=9=â©ïç–ÅΩ±ë•ï±ëÃÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ)M=9=â©ïç–ÅΩ±ëA…Ω©ïç–ÄÙÅ¡…Ω©ïç—·—…Ö•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃÄÙÅπï‹Å)M=9=â©ïç–°Ω±ë•ï±ëÃπ—ΩM—…•πú†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃπ¡’–†âç…Öô—1•πïÖ…YÖ±’îà∞Åπï·–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö¡ï…Õ•Õ—A…Ω©ïç—9Ω‹°—…’î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃÄÙÅΩ±ë•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡…Ω©ïç—·—…Ö•ï±ëÃÄÙÅΩ±ëA…Ω©ïç–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÖŸïç—•ŸïMç…ïï∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB‘ÉFB”B√BÔB˚FF0ÉFB˚FFB√B˜B„FF0ÉFFFB√BÀBÎFà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ±•ŸïYÖ±’ïÃπ¡’–°•πëï‡∞Å%π—ïùï»π—ΩM—…•πú°πï·–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Åï……Ω»§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃÄÙÅΩ±ë•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡…Ω©ïç—·—…Ö•ï±ëÃÄÙÅΩ±ëA…Ω©ïç–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÖŸïç—•ŸïMç…ïï∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB‘ÉFB”B√BÔB˚FF0ÉFB˚FFB√B˜B„FF0ÉFFFB√BÀBÎFà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏ÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅΩ¡ïπ…Öô—1•πïÖ…Mï——ï»°]•ëùï–Å›•ëùï–∞Å•π–Å›•ëùï—%πëï‡§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–ÅΩ¡ïπ•πùMç…ïïπ%πëï‡ÄÙÅç’……ïπ—Mç…ïïπ%πëï‡Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅMç…ïï∏ÅΩ¡ïπ•πùMç…ïï∏ÄÙÅΩ¡ïπ•πùMç…ïïπ%πëï‡Ä¯ÙÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅΩ¡ïπ•πùMç…ïïπ%πëï‡ÄÅÕç…ïïπÃπÕ•Èî†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÅÕç…ïïπÃπùï–°Ω¡ïπ•πùMç…ïïπ%πëï‡§ÄËÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å›•ëùï—%πëï‡∞ÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–ÅµÖ·•µ’¥ÄÙÅ…Öô—1•πïÖ…Mï——ï»πµÖ·•µ’¥°›•ëùï–πï·—…Ö•ï±ëÃ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–Å•π•—•Ö∞ÄÙÅ…Öô—1•πïÖ…Mï——ï»πŸÖ±’î°›•ëùï–πï·—…Ö•ï±ëÃ∞Å±•ŸïYÖ±’ïÃπùï–°›•ëùï—%πëï‡§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅM—…•πúÅçΩππïç—•Ω∏ÄÙÅ›•ëùï–πçΩππïç—•Ωπ%πôºÄÙÙÅπ’±∞Ä¸Äàà(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÅ›•ëùï–πçΩππïç—•Ωπ%πôºπ—…•¥†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å	•πë•πùIï≈’ïÕ–Åâ•πë•πúÄÙÅçΩππïç—•Ω∏π•Õµ¡—‰†§Ä¸Åπ’±∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÅ¡Ö…Õï	•πë•πú†¥ƒ∞ÅçΩππïç—•Ω∏§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖçΩππïç—•Ω∏π•Õµ¡—‰†§ÄòòÄ°â•πë•πúÄÙÙÅπ’±∞ÅÒÅâ•πë•πúπô’πç—•ΩπΩëîÄÑÙÄÃ§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBFB˚BÀB◊FF0ÅQ@Å°Ω±ë•πú∑FB◊BœB„FFF Å¿ÃΩ¿ÿà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅÖπë…Ω•êπ›•ëùï–π1•πïÖ…1ÖÂΩ’–ÅôΩ…¥ÄÙÅπï‹ÅÖπë…Ω•êπ›•ëùï–π1•πïÖ…1ÖÂΩ’–°5Ö•πç—•Ÿ•—‰π—°•Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ…¥πÕï—=…•ïπ—Ö—•Ω∏°Öπë…Ω•êπ›•ëùï–π1•πïÖ…1ÖÂΩ’–πYIQ%0§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ…¥πÕï—AÖëë•πú°ë¿†»»§∞Åë¿†ƒ–§∞Åë¿†»»§∞Åë¿†–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅQï·—Y•ï‹Åç’……ïπ–ÄÙÅπï‹ÅQï·—Y•ï‹°5Ö•πç—•Ÿ•—‰π—°•Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅç’……ïπ–πÕï—Qï·–°•π•—•Ö∞Ä¨ÄàÄºÄàÄ¨ÅµÖ·•µ’¥§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ…¥πÖëëY•ï‹°ç’……ïπ–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÖπë…Ω•êπ›•ëùï–πMïï≠	Ö»ÅÕ±•ëï»ÄÙÅπï‹ÅÖπë…Ω•êπ›•ëùï–πMïï≠	Ö»°5Ö•πç—•Ÿ•—‰π—°•Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÕ±•ëï»πÕï—5Ö‡°µÖ·•µ’¥§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÕ±•ëï»πÕï—A…Ωù…ïÕÃ°•π•—•Ö∞§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—1•πïÖ…Yï…—•çÖ∞à∞ÅôÖ±Õî§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕ±•ëï»πÕï—IΩ—Ö—•Ω∏†»‹¡ò§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ…¥πÖëëY•ï‹°Õ±•ëï»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÕ±•ëï»πÕï—=πMïï≠	Ö…°Öπùï1•Õ—ïπï»°πï‹ÅÖπë…Ω•êπ›•ëùï–πMïï≠	Ö»π=πMïï≠	Ö…°Öπùï1•Õ—ïπï»†§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ=Ÿï……•ëîÅ¡’â±•åÅŸΩ•êÅΩπA…Ωù…ïÕÕ°Öπùïê°Öπë…Ω•êπ›•ëùï–πMïï≠	Ö»ÅÕïï≠	Ö»∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–Å¡…Ωù…ïÕÃ∞ÅâΩΩ±ïÖ∏Åô…ΩµUÕï»§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅç’……ïπ–πÕï—Qï·–°¡…Ωù…ïÕÃÄ¨ÄàÄºÄàÄ¨ÅµÖ·•µ’¥§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ=Ÿï……•ëîÅ¡’â±•åÅŸΩ•êÅΩπM—Ö…—Q…Öç≠•πùQΩ’ç†°Öπë…Ω•êπ›•ëùï–πMïï≠	Ö»ÅÕïï≠	Ö»§ÅÌÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ=Ÿï……•ëîÅ¡’â±•åÅŸΩ•êÅΩπM—Ω¡Q…Öç≠•πùQΩ’ç†°Öπë…Ω•êπ›•ëùï–πMïï≠	Ö»ÅÕïï≠	Ö»§ÅÌÙ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å±ï…—•Ö±Ωúπ	’•±ëï»°5Ö•πç—•Ÿ•—‰π—°•Ã§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—Q•—±î°›•ëùï–π—ï·–ÄÙÙÅπ’±∞ÅÒÅ›•ëùï–π—ï·–π•Õµ¡—‰†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÄãBoB„B˜B◊BÁB˜F/B‰ÉBﬂB√B”B√FFB„BËàÄËÅ›•ëùï–π—ï·–§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—Y•ï‹°ôΩ…¥§πÕï—9ïùÖ—•Ÿï	’——Ω∏†ãB{FBÛB◊B˜B¿à∞Åπ’±∞§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—AΩÕ•—•Ÿï	’——Ω∏†ãB_B√B”B√FF0à∞Ä°ë•Ö±Ωú∞Å›°•ç†§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å›•ëùï—%πëï‡∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãB∑BÎFB√BÙÉB„BﬂBÛB◊B˜B„BÔFF<ËÉBˇB˚BÀFB˚FB˜B¯ÉB˚FBÎFB˚B‰ÉBﬂB√B”B√FFB„BËà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–Åπï·–ÄÙÅÕ±•ëï»πùï—A…Ωù…ïÕÃ†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°â•πë•πúÄÙÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÖŸïI’π—•µï1•πïÖ…Mï—¡Ω•π–°›•ëùï–∞Å›•ëùï—%πëï‡∞Åπï·–∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµΩëâ’Õ·ïç’—Ω»πï·ïç’—î††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ·çï¡—•Ω∏ÅôÖ•±’…îÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å›•ëùï—%πëï‡∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—°…Ω‹Åπï‹Å%=·çï¡—•Ω∏†ãBÉB√B«B˚FB„B‰ÉFB◊B€B„BÉB˚FFB√B˜B˚BÀBÔB◊BÙà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å5Ωëâ’ÕQç¡±•ïπ–°â•πë•πúπ°ΩÕ–∞Åâ•πë•πúπ¡Ω…–§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπ›…•—ïM•πù±î°â•πë•πúπ’π•–∞Åâ•πë•πúπÖëë…ïÕÃ∞Åπï·–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Åï……Ω»§ÅÏÅôÖ•±’…îÄÙÅï……Ω»ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å·çï¡—•Ω∏Å…ïÕ’±–ÄÙÅôÖ•±’…îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…’π=πU•Q°…ïÖê††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å›•ëùï—%πëï‡∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…ïÕ’±–ÄÙÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖÕÖŸïI’π—•µï1•πïÖ…Mï—¡Ω•π–°›•ëùï–∞Å›•ëùï—%πëï‡∞Åπï·–∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBÉB◊BœB„FFF ÉBﬂB√BˇB„FB√BÙ∞ÉB˜B¯ÉBˇFB˚B◊BÎFÉB˜B‘ÉFB˚FFB√B˜FGBÙà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBwB‘ÉFB”B√BÔB˚FF0ÉBﬂB√BˇB„FB√FF0ÉBﬂB˜B√FB◊B˜B„B‘ËÄà(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¨ÅëïÕç…•âï5Ωëâ’Õ……Ω»°…ïÕ’±–§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅΩ¡ïπ…Öô—9’µï…•ç%π¡’–°]•ëùï–Å›•ëùï–∞Å•π–Å›•ëùï—%πëï‡§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–ÅΩ¡ïπ•πùMç…ïïπ%πëï‡ÄÙÅç’……ïπ—Mç…ïïπ%πëï‡Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅMç…ïï∏ÅΩ¡ïπ•πùMç…ïï∏ÄÙÅΩ¡ïπ•πùMç…ïïπ%πëï‡Ä¯ÙÄ¿(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅΩ¡ïπ•πùMç…ïïπ%πëï‡ÄÅÕç…ïïπÃπÕ•Èî†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸ÅÕç…ïïπÃπùï–°Ω¡ïπ•πùMç…ïïπ%πëï‡§ÄËÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å›•ëùï—%πëï‡∞ÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—9’µï…•çπÖâ±ïêà∞ÅôÖ±Õî§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãB{FBÎFB˚B‰ÉFBÀB˚BÁFFBÀB¿Å%Ä‡ÉB‡ÉF?BÀB˜B¯ÉFB√BﬂFB◊F#B‡ÉBÀBÀB˚B–à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§ÏÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Åç…Öô—MÂÕ—ïµ	•πë•πúÄÙÅ…Öô—MÂÕ—ïµ9’µï…•ç]•ëùï—	•πë•πúπ•Õ±ΩâÖ±MÂÕ—ïµ	•πë•πú°›•ëùï–πï·—…Ö•ï±ëÃ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ç…Öô—MÂÕ—ïµ	•πë•πúÄòòÄÖ…Öô—MÂÕ—ïµ9’µï…•ç]•ëùï—	•πë•πúπçÖπ]…•—î°›•ëùï–πï·—…Ö•ï±ëÃ§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBSBÔF<ÉFB„FFB◊BÛB˜B˚B‰ÉFBÀF?BﬂB‡Å…Öô–ÉBÀBÀB˚B–ÉB”B˚FFFBˇB◊BÙÉB»ÉBˇB˚BÔF3BﬂB˚BÀB√FB◊BÔF3FBÎB„B‘ÉFB◊BœB„FFFF,Äƒ√äLƒ‰‰‰Ä°±ΩÖ–Ã»ÉäPÉB”B¯Äƒ‰‰‡§à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§ÏÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Å¡ÖÕÕ›Ω…ëIï≈’•…ïêÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—9’µï…•çAÖÕÕ›Ω…ëIï≈’•…ïêà∞ÅôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ¡ÖÕÕ›Ω…ë-ï‰ÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âç…Öô—9’µï…•çAÖÕÕ›Ω…ë-ï‰à∞Äàà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅM—…•πúÅï·¡ïç—ïëAÖÕÕ›Ω…êÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏÅï·¡ïç—ïëAÖÕÕ›Ω…êÄÙÅ¡ÖÕÕ›Ω…ëIï≈’•…ïêÄ¸Å…Öô—9’µï…•çAÖÕÕ›Ω…ëM—Ω…îπ…ïÖê°5Ö•πç—•Ÿ•—‰π—°•Ã∞Å¡ÖÕÕ›Ω…ë-ï‰§ÄËÄààÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅçÖ—ç†Ä°·çï¡—•Ω∏ÅôÖ•±’…î§ÅÏÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB‘ÉFB”B√BÔB˚FF0ÉB˚FBÎFF/FF0ÉFB˚FFB√B˜FGB˜B˜F/B‰ÉBˇB√FB˚BÔF0à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§ÏÅ…ï—’…∏ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°¡ÖÕÕ›Ω…ëIï≈’•…ïêÄòòÅï·¡ïç—ïëAÖÕÕ›Ω…êπ•Õµ¡—‰†§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBB˚BÀFB˚FB˜B¯ÉBﬂB√B”B√B‰ÉBˇB√FB˚BÔF0Å…Öô–ÉB»ÉFBÀB˚BÁFFBÀB√FÉF7BÔB◊BÛB◊B˜FB¿à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§ÏÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å	•πë•πùIï≈’ïÕ–Åâ•πë•πúÄÙÅ¡Ö…Õï	•πë•πú†¥ƒ∞Å›•ëùï–πçΩππïç—•Ωπ%πôº§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°›•ëùï–πçΩππïç—•Ωπ%πôºÄÑÙÅπ’±∞ÄòòÄÖ›•ëùï–πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÄ°â•πë•πúÄÙÙÅπ’±∞ÅÒÅâ•πë•πúπô’πç—•ΩπΩëîÄÑÙÄÃ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄ°…Öô—9’µï…•ç%π¡’–π…ïù•Õ—ï…Ω’π–°›•ëùï–πï·—…Ö•ï±ëÃ§ÄÙÙÄ»ÄòòÅâ•πë•πúπÖëë…ïÕÃÄ¯ÙÄÿ‘‘Ã‘§§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBFB˚BÀB◊FF0ÅQ@Å°Ω±ë•πú∑FB◊BœB„FFF Å¿ÃΩ¿ÿà∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§ÏÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ1•πïÖ…1ÖÂΩ’–ÅôΩ…¥ÄÙÅπï‹Å1•πïÖ…1ÖÂΩ’–°5Ö•πç—•Ÿ•—‰π—°•Ã§ÏÅôΩ…¥πÕï—=…•ïπ—Ö—•Ω∏°1•πïÖ…1ÖÂΩ’–πYIQ%0§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅôΩ…¥πÕï—AÖëë•πú°ë¿†»»§∞Åë¿†ƒ–§∞Åë¿†»»§∞Åë¿†–§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅQï·—Y•ï‹Å…ÖπùîÄÙÅπï‹ÅQï·—Y•ï‹°5Ö•πç—•Ÿ•—‰π—°•Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ…ÖπùîπÕï—Qï·–†ãBSB„B√BˇB√BﬂB˚BÙËÄàÄ¨Å›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—Ω’â±î†âç…Öô—9’µï…•ç5•∏à∞Ä¿§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¨ÄàÉäLÄàÄ¨Å›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—Ω’â±î†âç…Öô—9’µï…•ç5Ö‡à∞Äƒ¿¿¿§§ÏÅôΩ…¥πÖëëY•ï‹°…Öπùî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ•π•—•Ö∞ÄÙÅ±•ŸïYÖ±’ïÃπùï–°›•ëùï—%πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ç…Öô—MÂÕ—ïµ	•πë•πú§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅÕÂÕ—ïµYÖ±’îÄÙÅ…Öô—MÂÕ—ïµ9’µï…•ç]•ëùï—	•πë•πúπ…ïÖëM—Ö—î°›•ëùï–πï·—…Ö•ï±ëÃ∞ÅÕÂÕ—ïµIïù•Õ—ï…	Öπ¨§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ÕÂÕ—ïµYÖ±’îÄÑÙÅπ’±∞§Å•π•—•Ö∞ÄÙÅÕÂÕ—ïµYÖ±’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°•π•—•Ö∞ÄÙÙÅπ’±∞ÅÒÅ•π•—•Ö∞πÕ—Ö…—Õ]•—††âIHà§ÅÒÅ•π•—•Ö∞πÕ—Ö…—Õ]•—††âà§§Å•π•—•Ö∞ÄÙÅ›•ëùï–π—ï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°•π•—•Ö∞ÄÙÙÅπ’±∞ÅÒÅ•π•—•Ö∞π•Õµ¡—‰†§§Å•π•—•Ö∞ÄÙÄà¿àÏ(ÄÄÄÄÄÄÄÄÄÄÄÅë•—Qï·–ÅŸÖ±’îÄÙÅ¡…Ω¡ï…—Â•ï±ê°•π•—•Ö∞∞Å%π¡’—QÂ¡îπQeA}1MM}9U5	HÅÅ%π¡’—QÂ¡îπQeA}9U5	I}1}M%9ÅÅ%π¡’—QÂ¡îπQeA}9U5	I}1}%50§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅŸÖ±’îπÕï—•±—ï…Ã°πï‹ÅÖπë…Ω•êπ—ï·–π%π¡’—•±—ï…muÌπï‹ÅÖπë…Ω•êπ—ï·–π%π¡’—•±—ï»π1ïπù—°•±—ï»†ƒ‘•Ù§ÏÅôΩ…¥πÖëëY•ï‹°ŸÖ±’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅë•—Qï·–Å¡ÖÕÕ›Ω…êÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°¡ÖÕÕ›Ω…ëIï≈’•…ïê§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ÖÕÕ›Ω…êÄÙÅ¡…Ω¡ï…—Â•ï±ê†àà∞Å%π¡’—QÂ¡îπQeA}1MM}QaPÅÅ%π¡’—QÂ¡îπQeA}QaQ}YI%Q%=9}AMM]=I§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ÖÕÕ›Ω…êπÕï—•±—ï…Ã°πï‹ÅÖπë…Ω•êπ—ï·–π%π¡’—•±—ï…muÌπï‹ÅÖπë…Ω•êπ—ï·–π%π¡’—•±—ï»π1ïπù—°•±—ï»†ƒ¿•Ù§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ÖÕÕ›Ω…êπÕï—!•π–†ãBB√FB˚BÔF0ÉB˚BˇB◊FB√FB˚FB¿à§ÏÅôΩ…¥πÖëëY•ï‹°¡ÖÕÕ›Ω…ê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Åë•—Qï·–Å¡ÖÕÕ›Ω…ë•ï±êÄÙÅ¡ÖÕÕ›Ω…êÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ±ï…—•Ö±ΩúÅë•Ö±ΩúÄÙÅπï‹Å±ï…—•Ö±Ωúπ	’•±ëï»°5Ö•πç—•Ÿ•—‰π—°•Ã§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—Q•—±î°›•ëùï–π—ï·–ÄÙÙÅπ’±∞ÅÒÅ›•ëùï–π—ï·–π•Õµ¡—‰†§Ä¸ÄãBüB„FBÔB˚BÀB˚B‰ÉBÀBÀB˚B–àÄËÅ›•ëùï–π—ï·–§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—Y•ï‹°ôΩ…¥§πÕï—9ïùÖ—•Ÿï	’——Ω∏†ãB{FBÛB◊B˜B¿à∞Åπ’±∞§πÕï—AΩÕ•—•Ÿï	’——Ω∏†ãB_B√B”B√FF0à∞Åπ’±∞§πç…ïÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅë•Ö±ΩúπÕï—=πM°Ω›1•Õ—ïπï»°Õ°Ω›∏Ä¥¯Åë•Ö±Ωúπùï—	’——Ω∏°±ï…—•Ö±Ωúπ	UQQ=9}A=M%Q%Y§πÕï—=π±•ç≠1•Õ—ïπï»°ÿÄ¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å›•ëùï—%πëï‡∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãB∑BÎFB√BÙÉB„BﬂBÛB◊B˜B„BÔFF<ËÉB˚FBÎFB˚B‰ÉBÀBÀB˚B–ÉBﬂB√B˜B˚BÀB¯à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅë•Ö±Ωúπë•Õµ•ÕÃ†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°¡ÖÕÕ›Ω…ëIï≈’•…ïêÄòòÄ°¡ÖÕÕ›Ω…ë•ï±êÄÙÙÅπ’±∞ÅÒÄÖï·¡ïç—ïëAÖÕÕ›Ω…êπï≈’Ö±Ã°¡ÖÕÕ›Ω…ë•ï±êπùï—Qï·–†§π—ΩM—…•πú†§§§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB◊BÀB◊FB˜F/B‰ÉBˇB√FB˚BÔF0à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§ÏÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅëΩ’â±îÅπï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏÅπï·–ÄÙÅ…Öô—9’µï…•ç%π¡’–π¡Ö…ÕïΩµµ•–°ŸÖ±’îπùï—Qï·–†§π—ΩM—…•πú†§∞Å›•ëùï–πï·—…Ö•ï±ëÃ§ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖ—ç†Ä°%±±ïùÖ±…ù’µïπ—·çï¡—•Ω∏Å•πŸÖ±•ê§ÅÏÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞Å•πŸÖ±•êπùï—5ïÕÕÖùî†§∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§ÏÅ…ï—’…∏ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅM—…•πúÅôΩ…µÖ——ïêÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—%π–†âç…Öô—9’µï…•ç5Ωëîà∞Å…Öô—9’µï…•ç%π¡’–π%9QH§ÄÙÙÅ…Öô—9’µï…•ç%π¡’–π1=PÃ»(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸Å…Öô—9’µï…•ç%π¡’–π—…’πçÖ—ï±ΩÖ—•Õ¡±Ö‰†°ô±ΩÖ–§Åπï·–∞Å›•ëùï–πï·—…Ö•ï±ëÃ§ÄËÅ%π—ïùï»π—ΩM—…•πú†°•π–§Åπï·–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°â•πë•πúÄÙÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅM—…•πúÅΩ±ëQï·–ÄÙÅ›•ëùï–π—ï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅM—…•πúÅΩ±ë1•ŸïYÖ±’îÄÙÅ±•ŸïYÖ±’ïÃπùï–°›•ëùï—%πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å)M=9=â©ïç–ÅΩ±ëA…Ω©ïç–ÄÙÅ¡…Ω©ïç—·—…Ö•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ…úπ©ÕΩ∏π)M=9……Ö‰ÅΩ±ëMÂÕ—ïµIïù•Õ—ï…ÃÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏ÅçΩµµ•——ïêÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ç…Öô—MÂÕ—ïµ	•πë•πú§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ±ëMÂÕ—ïµIïù•Õ—ï…ÃÄÙÅÕÂÕ—ïµIïù•Õ—ï…	Öπ¨π—ΩA…Ω©ïç—)ÕΩ∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö…Öô—MÂÕ—ïµ9’µï…•ç]•ëùï—	•πë•πúπ›…•—ïYÖ±’î†(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–πï·—…Ö•ï±ëÃ∞Åπï·–∞ÅÕÂÕ—ïµIïù•Õ—ï…	Öπ¨§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—°…Ω‹Åπï‹Å%±±ïùÖ±M—Ö—ï·çï¡—•Ω∏†ãBáB„FFB◊BÛB˜F/B‰ÉFB◊BœB„FFF ÉB˜B◊B”B˚FFFBˇB◊BÙà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–π—ï·–ÄÙÅôΩ…µÖ——ïêÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö¡ï…Õ•Õ—A…Ω©ïç—9Ω‹°—…’î§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—°…Ω‹Åπï‹Å%±±ïùÖ±M—Ö—ï·çï¡—•Ω∏†ãBFB˚B◊BÎFÉB˜B‘ÉFB˚FFB√B˜FGBÙà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩµµ•——ïêÄÙÅ—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ±•ŸïYÖ±’ïÃπ¡’–°›•ëùï—%πëï‡∞ÅôΩ…µÖ——ïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅë•Ö±Ωúπë•Õµ•ÕÃ†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏ÅôÖ•±’…î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBwB‘ÉFB”B√BÔB˚FF0ÉBﬂB√BˇB„FB√FF0ÉBﬂB˜B√FB◊B˜B„B‘ËÄàÄ¨ÅôÖ•±’…îπùï—5ïÕÕÖùî†§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅô•πÖ±±‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖçΩµµ•——ïê§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–π—ï·–ÄÙÅΩ±ëQï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡…Ω©ïç—·—…Ö•ï±ëÃÄÙÅΩ±ëA…Ω©ïç–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Ω±ëMÂÕ—ïµIïù•Õ—ï…ÃÄÑÙÅπ’±∞§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÂÕ—ïµIïù•Õ—ï…	Öπ¨π…ïÕ—Ω…ïA…Ω©ïç—)ÕΩ∏°Ω±ëMÂÕ—ïµIïù•Õ—ï…Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Ω±ë1•ŸïYÖ±’îÄÙÙÅπ’±∞§Å±•ŸïYÖ±’ïÃπ…ïµΩŸî°›•ëùï—%πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅï±ÕîÅ±•ŸïYÖ±’ïÃπ¡’–°›•ëùï—%πëï‡∞ÅΩ±ë1•ŸïYÖ±’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÖŸïç—•ŸïMç…ïï∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π–Å…ïù•Õ—ï…Ω’π–ÄÙÅ…Öô—9’µï…•ç%π¡’–π…ïù•Õ—ï…Ω’π–°›•ëùï–πï·—…Ö•ï±ëÃ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å•π—mtÅïπçΩëïêÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅïπçΩëïêÄÙÅ…ïù•Õ—ï…Ω’π–ÄÙÙÄ»(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¸Åâë…Öô—Iïù•Õ—ï…Ωëïåπô±ΩÖ—Iïù•Õ—ï…Ã†°ô±ΩÖ–§Åπï·–§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄËÅπï‹Å•π—muÌ…Öô—9’µï…•ç%π¡’–π•π—ïùï…Iïù•Õ—ï»°πï·–∞Å›•ëùï–πï·—…Ö•ï±ëÃ•ÙÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Å•πŸÖ±•ê§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB◊BÀB◊FB˜B˚B‘ÉBﬂB˜B√FB◊B˜B„B‘ÉFB◊BœB„FFFB¿à∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµΩëâ’Õ·ïç’—Ω»πï·ïç’—î††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ·çï¡—•Ω∏ÅôÖ•±’…îÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å›•ëùï—%πëï‡∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—°…Ω‹Åπï‹Å%=·çï¡—•Ω∏†ãBÉB√B«B˚FB„B‰ÉFB◊B€B„BÉB˚FFB√B˜B˚BÀBÔB◊BÙà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ5Ωëâ’ÕQç¡±•ïπ–Åç±•ïπ–ÄÙÅπï‹Å5Ωëâ’ÕQç¡±•ïπ–°â•πë•πúπ°ΩÕ–∞Åâ•πë•πúπ¡Ω…–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…ïù•Õ—ï…Ω’π–ÄÙÙÄ»§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅç±•ïπ–π›…•—ï5’±—•¡±î°â•πë•πúπ’π•–∞Åâ•πë•πúπÖëë…ïÕÃ∞ÅïπçΩëïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅï±ÕîÅç±•ïπ–π›…•—ïM•πù±î°â•πë•πúπ’π•–∞Åâ•πë•πúπÖëë…ïÕÃ∞ÅïπçΩëïël¡t§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Åï……Ω»§ÅÏÅôÖ•±’…îÄÙÅï……Ω»ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å·çï¡—•Ω∏Å…ïÕ’±–ÄÙÅôÖ•±’…îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…’π=πU•Q°…ïÖê††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•ÕI’π—•µï]•ëùï—’……ïπ–°›•ëùï–∞Å›•ëùï—%πëï‡∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ•πùMç…ïï∏∞ÅΩ¡ïπ•πùMç…ïïπ%πëï‡§§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…ïÕ’±–ÄÙÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅΩ±ëQï·–ÄÙÅ›•ëùï–π—ï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ)M=9=â©ïç–ÅΩ±ëA…Ω©ïç–ÄÙÅ¡…Ω©ïç—·—…Ö•ï±ëÃÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–π—ï·–ÄÙÅôΩ…µÖ——ïêÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°¡ï…Õ•Õ—A…Ω©ïç—9Ω‹°—…’î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ±•ŸïYÖ±’ïÃπ¡’–°›•ëùï—%πëï‡∞ÅôΩ…µÖ——ïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅë•Ö±Ωúπë•Õµ•ÕÃ†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•ëùï–π—ï·–ÄÙÅΩ±ëQï·–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡…Ω©ïç—·—…Ö•ï±ëÃÄÙÅΩ±ëA…Ω©ïç–Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕÖŸïç—•ŸïMç…ïï∏†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBÉB◊BœB„FFF ÉBﬂB√BˇB„FB√BÙ∞ÉB˜B¯ÉBˇFB˚B◊BÎFÉB˜B‘ÉFB˚FFB√B˜FGBÙà∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄãBwB‘ÉFB”B√BÔB˚FF0ÉBﬂB√BˇB„FB√FF0ÉBﬂB˜B√FB◊B˜B„B‘ËÄàÄ¨ÅëïÕç…•âï5Ωëâ’Õ……Ω»°…ïÕ’±–§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅë•Ö±ΩúπÕ°Ω‹†§ÏÅŸÖ±’îπ…ï≈’ïÕ—Ωç’Ã†§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅΩ¡ïπ…Öô—I—Õ¿°]•ëùï–Å›•ëùï–§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ¡ï…Ö—Ω…5ΩëîÅÒÄÖÖç—•Ÿ•—ÂIïÕ’µïê(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄÖ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—I—Õ¡πÖâ±ïêà∞ÅôÖ±Õî§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBÉB√BﬂFB◊F#B„FB‘ÅIQM@ÉB»ÉFBÀB˚BÁFFBÀB√FÉB‡ÉBﬂB√BˇFFFB„FB‘ÉFB√B«B˚FB„B‰ÉFB◊B€B„æ˛˜æ˛Ùà∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ)M=9=â©ïç–ÅÕΩ’…çï=—°ï»ÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—)M=9=â©ïç–†âç…Öô—=—°ï»à§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅçÖπë•ëÖ—îÄÙÅÕΩ’…çï=—°ï»ÄÙÙÅπ’±∞Ä¸ÄààÄËÅÕΩ’…çï=—°ï»πΩ¡—M—…•πú†â…—Õ¡}’…∞à∞Äàà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å…Öô—I—Õ¡Ωπô•úÅçΩπô•úÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏÅçΩπô•úÄÙÅ…Öô—I—Õ¡Ωπô•úπ¡Ö…Õî°çÖπë•ëÖ—î§ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅçÖ—ç†Ä°%±±ïùÖ±…ù’µïπ—·çï¡—•Ω∏Å•πŸÖ±•ê§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞Å•πŸÖ±•êπùï—5ïÕÕÖùî†§∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§ÏÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅUI$Å’…§ÄÙÅUI$πç…ïÖ—î°çΩπô•úπùï—U…∞†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅïπë¡Ω•π–ÄÙÅ’…§πùï—Mç°ïµî†§π—Ω1Ω›ï…ÖÕî°1ΩçÖ±îπI==P§Ä¨ÄàËººàÄ¨Å’…§πùï—!ΩÕ–†§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¨Ä°’…§πùï—AΩ…–†§ÄÄ¿Ä¸ÄààÄËÄàËàÄ¨Å’…§πùï—AΩ…–†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å±ï…—•Ö±Ωúπ	’•±ëï»°5Ö•πç—•Ÿ•—‰π—°•Ã§πÕï—Q•—±î†ãB_B√BˇFFFB„FF0ÉBÀB„B”B◊B˚BˇB˚FB˚BË¸à§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—5ïÕÕÖùî°ïπë¡Ω•π–Ä¨ÄâqªBGFB”B◊FÉB˚FBÎFF/FB¯ÉFB◊FB◊BÀB˚B‘ÅIQM@∑FB˚B◊B”B„B˜B◊B˜B„B‘∏ÉB{FFB√B˜B˚BÀB„FB‘ÉBˇB˚FB˚BË∞ÉB◊FBÔB‡ÉFFFFB˚BÁFFBÀB¯ÉB„BÔB‡ÉFB◊FF0ÉB˜B‘ÉB”B˚BÀB◊FB◊B˜B˜F/B‘∏à§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—9ïùÖ—•Ÿï	’——Ω∏†ãB{FBÛB◊B˜B¿à∞Åπ’±∞§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—AΩÕ•—•Ÿï	’——Ω∏†ãB_B√BˇFFFB„FF0à∞Ä°ë•Ö±Ωú∞Å›°•ç†§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö›•ëùï—ÃπçΩπ—Ö•πÃ°›•ëùï–§§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…Öô—I—Õ¡Ωπô•úÅÖ’—°Ω…•ÈïêÄÙÅ…Öô—I—Õ¡A±ÖÂâÖç≠AΩ±•ç‰πÖ’—°Ω…•Èî°Ω¡ï…Ö—Ω…5Ωëî∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖç—•Ÿ•—ÂIïÕ’µïê∞Å›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—I—Õ¡πÖâ±ïêà∞ÅôÖ±Õî§∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…’î∞ÅçÖπë•ëÖ—î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕ°Ω›…Öô—I—Õ¡A±ÖÂï»°›•ëùï–π—ï·–∞ÅÖ’—°Ω…•Èïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°%±±ïùÖ±…ù’µïπ—·çï¡—•Ω∏ÅÅ%±±ïùÖ±M—Ö—ï·çï¡—•Ω∏Å•πŸÖ±•ê§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞Å•πŸÖ±•êπùï—5ïÕÕÖùî†§∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅÕ°Ω›…Öô—I—Õ¡A±ÖÂï»°M—…•πúÅ—•—±î∞Å…Öô—I—Õ¡Ωπô•úÅçΩπô•ú§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅç±ΩÕï…Öô—I—Õ¡A±ÖÂï»†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ1•πïÖ…1ÖÂΩ’–ÅçΩπ—ïπ–ÄÙÅπï‹Å1•πïÖ…1ÖÂΩ’–°5Ö•πç—•Ÿ•—‰π—°•Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅçΩπ—ïπ–πÕï—=…•ïπ—Ö—•Ω∏°1•πïÖ…1ÖÂΩ’–πYIQ%0§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅçΩπ—ïπ–πÕï—AÖëë•πú°ë¿†ƒ»§∞Åë¿†‡§∞Åë¿†ƒ»§∞Åë¿†‡§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅQï·—Y•ï‹ÅÕ—Ö—’ÃÄÙÅπï‹ÅQï·—Y•ï‹°5Ö•πç—•Ÿ•—‰π—°•Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÕ—Ö—’ÃπÕï—Qï·–†ãBB˚B”BÎBÔF;FB◊B˜B„B‘ÉBËÅIQM@∏ÉBB˚B”B”B◊FB€BÎB¿ÉBﬂB√BÀB„FB„FÉB˚FÉFB˚FBÛB√FB¿ÉBˇB˚FB˚BÎB¿ÉB‡Åπë…Ω•ê∑FFFFB˚BÁFFBÀB¿∏à§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅçΩπ—ïπ–πÖëëY•ï‹°Õ—Ö—’Ã∞Åπï‹Å1•πïÖ…1ÖÂΩ’–π1ÖÂΩ’—AÖ…ÖµÃ†¥ƒ∞Ä¥»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅY•ëïΩY•ï‹ÅŸ•ëïºÄÙÅπï‹ÅY•ëïΩY•ï‹°5Ö•πç—•Ÿ•—‰π—°•Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅŸ•ëïºπÕï—-ïï¡Mç…ïïπ=∏°—…’î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ1•πïÖ…1ÖÂΩ’–π1ÖÂΩ’—AÖ…ÖµÃÅŸ•ëïΩAÖ…ÖµÃÄÙÅπï‹Å1•πïÖ…1ÖÂΩ’–π1ÖÂΩ’—AÖ…ÖµÃ†¥ƒ∞Åë¿†»Ã¿§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅŸ•ëïΩAÖ…ÖµÃπ—Ω¡5Ö…ù•∏ÄÙÅë¿†‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅçΩπ—ïπ–πÖëëY•ï‹°Ÿ•ëïº∞ÅŸ•ëïΩAÖ…ÖµÃ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ1•πïÖ…1ÖÂΩ’–ÅçΩπ—…Ω±ÃÄÙÅπï‹Å1•πïÖ…1ÖÂΩ’–°5Ö•πç—•Ÿ•—‰π—°•Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅçΩπ—…Ω±ÃπÕï—…ÖŸ•—‰°…ÖŸ•—‰π9QH§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ	’——Ω∏Å¡Ö’ÕîÄÙÅπï‹Å	’——Ω∏°5Ö•πç—•Ÿ•—‰π—°•Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö’ÕîπÕï—Qï·–†ãBB√FBﬂB¿à§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö’ÕîπÕï—±±Ö¡Ã°ôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ	’——Ω∏ÅÕ—Ω¿ÄÙÅπï‹Å	’——Ω∏°5Ö•πç—•Ÿ•—‰π—°•Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÕ—Ω¿πÕï—Qï·–†ãB{FFB√B˜B˚BÀB„FF0à§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÕ—Ω¿πÕï—±±Ö¡Ã°ôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅçΩπ—…Ω±ÃπÖëëY•ï‹°¡Ö’Õî∞Åπï‹Å1•πïÖ…1ÖÂΩ’–π1ÖÂΩ’—AÖ…ÖµÃ†¿∞Ä¥»∞Ä≈ò§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅçΩπ—…Ω±ÃπÖëëY•ï‹°Õ—Ω¿∞Åπï‹Å1•πïÖ…1ÖÂΩ’–π1ÖÂΩ’—AÖ…ÖµÃ†¿∞Ä¥»∞Ä≈ò§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅçΩπ—ïπ–πÖëëY•ï‹°çΩπ—…Ω±Ã∞Åπï‹Å1•πïÖ…1ÖÂΩ’–π1ÖÂΩ’—AÖ…ÖµÃ†¥ƒ∞Ä¥»§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•Ö±ΩúÅë•Ö±ΩúÄÙÅπï‹Å•Ö±Ωú°5Ö•πç—•Ÿ•—‰π—°•Ã§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅë•Ö±ΩúπÕï—Q•—±î°—•—±îÄÙÙÅπ’±∞ÅÒÅ—•—±îπ—…•¥†§π•Õµ¡—‰†§Ä¸ÄãBKB„B”B◊B¯ÅIQM@Å…Öô–àÄËÅ—•—±î§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅë•Ö±ΩúπÕï—Ωπ—ïπ—Y•ï‹°çΩπ—ïπ–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅë•Ö±ΩúπÕï—=π•Õµ•ÕÕ1•Õ—ïπï»°ë•Õµ•ÕÕïêÄ¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅŸ•ëïºπÕï—-ïï¡Mç…ïïπ=∏°ôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏÅŸ•ëïºπÕ—Ω¡A±ÖÂâÖç¨†§ÏÅÙÅçÖ—ç†Ä°I’π—•µï·çï¡—•Ω∏Å•ùπΩ…ïê§ÅÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ç…Öô—I—Õ¡Y•ëïΩY•ï‹ÄÙÙÅŸ•ëïº§Åç…Öô—I—Õ¡Y•ëïΩY•ï‹ÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ç…Öô—I—Õ¡•Ö±ΩúÄÙÙÅë•Ö±Ωú§Åç…Öô—I—Õ¡•Ö±ΩúÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅç…Öô—I—Õ¡•Ö±ΩúÄÙÅë•Ö±ΩúÏ(ÄÄÄÄÄÄÄÄÄÄÄÅç…Öô—I—Õ¡Y•ëïΩY•ï‹ÄÙÅŸ•ëïºÏ(ÄÄÄÄÄÄÄÄÄÄÄÅŸ•ëïºπÕï—=πA…ï¡Ö…ïë1•Õ—ïπï»°¡±ÖÂï»Ä¥¯ÅÕ—Ö—’ÃπÕï—Qï·–†ãBB˚FB˚BËÉBÀB˚FBˇFB˚B„BﬂBÀB˚B”B„FFF<à§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅŸ•ëïºπÕï—=π……Ω…1•Õ—ïπï»†°¡±ÖÂï»∞Å›°Ö–∞Åï·—…Ñ§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕ—Ö—’ÃπÕï—Qï·–†ãBwB‘ÉFB”B√BÔB˚FF0ÉBÀB˚FBˇFB˚B„BﬂBÀB◊FFB‡ÉBˇB˚FB˚BËÉB˜B¿ÉF7FB˚BÉFFFFB˚BÁFFBÀB‘à§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ¡Ö’ÕîπÕï—=π±•ç≠1•Õ—ïπï»°ÿÄ¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Ÿ•ëïºπ•ÕA±ÖÂ•πú†§§ÅÏÅŸ•ëïºπ¡Ö’Õî†§ÏÅ¡Ö’ÕîπÕï—Qï·–†ãBFB˚B”B˚BÔB€B„FF0à§ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅï±ÕîÅÏÅŸ•ëïºπÕ—Ö…–†§ÏÅ¡Ö’ÕîπÕï—Qï·–†ãBB√FBﬂB¿à§ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°I’π—•µï·çï¡—•Ω∏ÅôÖ•±’…î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕ—Ö—’ÃπÕï—Qï·–†ãBB˚FB˚BËÉB˜B◊B”B˚FFFBˇB◊BÙà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÕ—Ω¿πÕï—=π±•ç≠1•Õ—ïπï»°ÿÄ¥¯Åç±ΩÕï…Öô—I—Õ¡A±ÖÂï»†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅë•Ö±ΩúπÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅŸ•ëïºπÕï—Y•ëïΩUI$°U…§π¡Ö…Õî°çΩπô•úπùï—U…∞†§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅŸ•ëïºπÕ—Ö…–†§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅΩ¡ïπ…Öô—]ïâY•ï‹°]•ëùï–Å›•ëùï–∞Å•π–Å•πëï‡§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ¡ï…Ö—Ω…5ΩëîÅÒÄÖÖç—•Ÿ•—ÂIïÕ’µïêÅÒÄÖ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—]ïâY•ï›πÖâ±ïêà∞ÅôÖ±Õî§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBÉB√BﬂFB◊F#B„FB‘Å]ïâY•ï‹ÉB»ÉFBÀB˚BÁFFBÀB√FÉBÎB˚BÛBˇB˚B˜B◊B˜FB¿à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πùmtÅ±•π≠ÃÄÙÅ…Öô—]ïâY•ï›1•π≠Ãπ…ïÖê°›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—)M=9=â©ïç–†âç…Öô—=—°ï»à§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö›•ëùï–πçΩππïç—•Ωπ%πôºπ—…•¥†§π•Õµ¡—‰†§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅÕï±ïç—ïêÄÙÅ…Öô—]ïâY•ï›AΩ±•ç‰πÕï±ïç—ïë%πëï‡°±•ŸïYÖ±’ïÃπùï–°•πëï‡§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õï±ïç—ïêÄÄ¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB◊FÉB√BÎFB„BÀB˜B˚BœB¯ÉB«B„FB¿ÉB„BÔB‡ÉB”B˚FFB˚BÀB◊FB˜B˚BœB¯ÉBﬂB˜B√FB◊B˜B„F<ÉFB◊BœB„FFFB¿à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩπô•…µ…Öô—]ïâY•ï‹°›•ëùï–∞Å±•π≠ÕmÕï±ïç—ïët§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅM—…•πùmtÅ±Öâï±ÃÄÙÅπï‹ÅM—…•πùm±•π≠Ãπ±ïπù—°tÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å§ÄÙÄ¿ÏÅ§ÄÅ±•π≠Ãπ±ïπù—†ÏÅ§¨¨§Å±Öâï±Õm•tÄÙÅ§Ä¨ÄàËÄàÄ¨Å±•π≠Õm•tÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å±ï…—•Ö±Ωúπ	’•±ëï»°5Ö•πç—•Ÿ•—‰π—°•Ã§πÕï—Q•—±î†ãBáFF/BÔBÎB¿Å]ïâY•ï‹à§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—%—ïµÃ°±Öâï±Ã∞Ä°ê∞Å›°•ç†§Ä¥¯ÅçΩπô•…µ…Öô—]ïâY•ï‹°›•ëùï–∞Å±•π≠Õm›°•ç°t§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—9ïùÖ—•Ÿï	’——Ω∏†ãB{FBÛB◊B˜B¿à∞Åπ’±∞§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅçΩπô•…µ…Öô—]ïâY•ï‹°]•ëùï–Å›•ëùï–∞ÅM—…•πúÅçÖπë•ëÖ—î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅM—…•πúÅ’…∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏÅ’…∞ÄÙÅ…Öô—]ïâY•ï›AΩ±•ç‰πç°ïç≠ïëU…∞°çÖπë•ëÖ—î§ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅçÖ—ç†Ä°%±±ïùÖ±…ù’µïπ—·çï¡—•Ω∏Å•πŸÖ±•ê§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞Å•πŸÖ±•êπùï—5ïÕÕÖùî†§∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§ÏÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å±ï…—•Ö±Ωúπ	’•±ëï»°5Ö•πç—•Ÿ•—‰π—°•Ã§πÕï—Q•—±î†ãB{FBÎFF/FF0ÉFFFB√B˜B„FF¸à§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—5ïÕÕÖùî°’…∞Ä¨ÄâqªBGFB”B◊FÉBÀF/BˇB˚BÔB˜B◊BÙÉFB◊FB◊BÀB˚B‰ÉBﬂB√BˇFB˚FÉBËÉF7FB˚BÛFÉFB◊FBÀB◊FF∏à§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—9ïùÖ—•Ÿï	’——Ω∏†ãB{FBÛB◊B˜B¿à∞Åπ’±∞§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—AΩÕ•—•Ÿï	’——Ω∏†ãB{FBÎFF/FF0à∞Ä°ê∞Å›°•ç†§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ¡ï…Ö—Ω…5ΩëîÅÒÄÖÖç—•Ÿ•—ÂIïÕ’µïêÅÒÄÖ›•ëùï—ÃπçΩπ—Ö•πÃ°›•ëùï–§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄÖ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—]ïâY•ï›πÖâ±ïêà∞ÅôÖ±Õî§§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏÅç…Öô—]ïâY•ï›]•πëΩ‹πΩ¡ï∏°5Ö•πç—•Ÿ•—‰π—°•Ã∞Å’…∞§ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖ—ç†Ä°I’π—•µï·çï¡—•Ω∏ÅôÖ•±’…î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅç…Öô—]ïâY•ï›]•πëΩ‹πç±ΩÕî†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB‘ÉFB”B√BÔB˚FF0ÉB˚FBÎFF/FF0Å]ïâY•ï‹à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅ…ï≈’ïÕ—…Öô—µÖ•∞°]•ëùï–Å›•ëùï–§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ¡ï…Ö—Ω…5ΩëîÅÒÄÖÖç—•Ÿ•—ÂIïÕ’µïêÅÒÄÖ›•ëùï—ÃπçΩπ—Ö•πÃ°›•ëùï–§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄÖ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—µÖ•±πÖâ±ïêà∞ÅôÖ±Õî§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄâµÖ•∞Å…Öô–ÉBÀF/BÎBÔF;FB◊BÙÉB»ÉFBÀB˚BÁFFBÀB√FÉBÎB˚BÛBˇB˚B˜B◊B˜FB¿à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅ…ïç•¡•ïπ–ÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âç…Öô—µÖ•±Qºà∞Äàà§π—…•¥†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅÕ’â©ïç–ÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âç…Öô—µÖ•±Q•—±îà∞Å›•ëùï–π—ï·–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅâΩë‰ÄÙÅ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—M—…•πú†âç…Öô—µÖ•±	Ωë‰à∞Äàà§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅM—…•πúÅŸÖ±•ëÖ—•Ωπ……Ω»ÄÙÅ…Öô—µÖ•±AΩ±•ç‰πŸÖ±•ëÖ—•Ωπ……Ω»°…ïç•¡•ïπ–∞ÅÕ’â©ïç–∞ÅâΩë‰§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ŸÖ±•ëÖ—•Ωπ……Ω»ÄÑÙÅπ’±∞§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÅŸÖ±•ëÖ—•Ωπ……Ω»∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅπï‹Å±ï…—•Ö±Ωúπ	’•±ëï»°5Ö•πç—•Ÿ•—‰π—°•Ã§πÕï—Q•—±î†ãBB˚B”BœB˚FB˚BÀB„FF0ÉBˇB„FF3BÛB¯¸à§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—5ïÕÕÖùî†ãBB˚BÔFFB√FB◊BÔF0ËÄàÄ¨Å…ïç•¡•ïπ–Ä¨ÄâqªBãB◊BÛB¿ËÄàÄ¨ÅÕ’â©ïç–(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄ¨ÄâqπqªB{FBÎFB˚B◊FFF<ÉBˇFB„BÔB˚B€B◊B˜B„B‘ÉBˇB˚FFF,ÉFÉFB◊FB˜B˚BÀB„BÎB˚B∏ÉBB„FF3BÛB¯ÉB˚FBˇFB√BÀB„FFF<ÉFB˚BÔF3BÎB¯ÉBˇB˚FBÔB‘ÉB˚FB”B◊BÔF3B˜B˚BœB¯ÉB˜B√B€B√FB„F<É
ØB{FBˇFB√BÀB„FF3
ÏÉB»ÉB˜FGB∏à§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—9ïùÖ—•Ÿï	’——Ω∏†ãB{FBÛB◊B˜B¿à∞Åπ’±∞§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄπÕï—AΩÕ•—•Ÿï	’——Ω∏†ãB{FBÎFF/FF0ÉBˇB˚FFFà∞Ä°ë•Ö±Ωú∞Å›°•ç†§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖΩ¡ï…Ö—Ω…5ΩëîÅÒÄÖÖç—•Ÿ•—ÂIïÕ’µïêÅÒÄÖ›•ëùï—ÃπçΩπ—Ö•πÃ°›•ëùï–§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÒÄÖ›•ëùï–πï·—…Ö•ï±ëÃπΩ¡—	ΩΩ±ïÖ∏†âç…Öô—µÖ•±πÖâ±ïêà∞ÅôÖ±Õî§§Å…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ%π—ïπ–ÅçΩµ¡ΩÕîÄÙÅπï‹Å%π—ïπ–°%π—ïπ–πQ%=9}M9Q<∞(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅU…§πô…ΩµAÖ…—Ã†âµÖ•±—ºà∞Å…ïç•¡•ïπ–∞Åπ’±∞§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩµ¡ΩÕîπ¡’—·—…Ñ°%π—ïπ–πaQI}MU	)P∞ÅÕ’â©ïç–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩµ¡ΩÕîπ¡’—·—…Ñ°%π—ïπ–πaQI}QaP∞ÅâΩë‰§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏÅÕ—Ö…—ç—•Ÿ•—‰°%π—ïπ–πç…ïÖ—ï°ΩΩÕï»°çΩµ¡ΩÕî∞ÄãBB˚B”BœB˚FB˚BÀB„FF0ÅïµÖ•∞à§§ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçÖ—ç†Ä°Öπë…Ω•êπçΩπ—ïπ–πç—•Ÿ•—Â9Ω—Ω’πë·çï¡—•Ω∏Å’πÖŸÖ•±Öâ±î§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB¿ÉFFFFB˚BÁFFBÀB‘ÉB˜B◊FÉBˇFB„BÔB˚B€B◊B˜B„F<ÉB”BÔF<ÅïµÖ•∞à∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ¡…•ŸÖ—îÅŸΩ•êÅÖç—•ŸÖ—ïI’π—•µï]•ëùï–°•π–Å•πëï‡∞Åô±ΩÖ–Å‡∞Åô±ΩÖ–Å‰§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Å¡•ç≠ïêÄÙÅ›•ëùï—Ãπùï–°•πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ãBKB„B”B◊B¯ÅIQM@Å…Öô–àπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ…Öô—I—Õ¿°¡•ç≠ïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†â]ïâY•ï‹Å…Öô–àπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ…Öô—]ïâY•ï‹°¡•ç≠ïê∞Å•πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBÉB√FBˇB˚BﬂB˜B√BÀB√B˜B„B‘ÉFB◊FB‡Å…Öô–àπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï≈’ïÕ—…Öô—YΩ•çïIïçΩùπ•—•Ω∏°¡•ç≠ïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†âµÖ•∞Å…Öô–àπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï≈’ïÕ—…Öô—µÖ•∞°¡•ç≠ïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBCFFB„B»Å…Öô–àπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕ°Ω›1ΩçÖ±…ç°•Ÿî°¡•ç≠ïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBüB„FBÔB˚BÀB˚B‰ÉBÀBÀB˚B–Å…Öô–àπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ…Öô—9’µï…•ç%π¡’–°¡•ç≠ïê∞Å•πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãB_B√B”B√FFB„BËàπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§ÄòòÅ‰Ä¯ÙÅ¡•ç≠ïêπ‰Ä¨Åë¿†»‰§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖë©’Õ—Mï—¡Ω•π–°¡•ç≠ïê∞Å‡ÄÅ¡•ç≠ïêπ‡Ä¨Å¡•ç≠ïêπ‹ÄºÄ»Ä¸Ä¥ƒÄËÄƒ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBoB„B˜B◊BÁB˜F/B‰ÉBﬂB√B”B√FFB„BËÅ…Öô–àπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ…Öô—1•πïÖ…Mï——ï»°¡•ç≠ïê∞Å•πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBáBˇB„FB˚BËÅ…Öô–àπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅΩ¡ïπ…Öô—	•—1•Õ–°¡•ç≠ïê∞Å•πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°=…•ù•πÖ±…Öô—M›•—ç†πÕ’¡¡Ω…—Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖç—•ŸÖ—ï…Öô—M›•—ç†°¡•ç≠ïê∞Å•πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBkB˚B˜FFB√B˜FB˜B√F<ÉBÎB˜B˚BˇBÎB¿Å…Öô–àπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖç—•ŸÖ—ï…Öô—ΩπÕ—Öπ—	’——Ω∏°¡•ç≠ïê∞Å•πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBkB˜B˚BˇBÎB¿àπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°•Õ…Öô—5Ωµïπ—Ö…Â	’——Ω∏°¡•ç≠ïê§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBGB„FB˚BÀB√F<ÉBÎB˚BÛB√B˜B”B¿ÉFB√B«B˚FB√B◊F∞ÉBˇB˚BÎB¿ÉBÎB˜B˚BˇBÎFÉFB”B◊FB€B„BÀB√F;Fà∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡•ç≠ïêπâ’——ΩπM—Ö—îÄÙÄÖ¡•ç≠ïêπâ’——ΩπM—Ö—îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ï…Õ•Õ—A…Ω©ïç–†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ	•πë•πùIï≈’ïÕ–Å…ï≈’ïÕ–ÄÙÅ¡Ö…Õï	•πë•πú†¥ƒ∞Å¡•ç≠ïêπçΩππïç—•Ωπ%πôº§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…ï≈’ïÕ–ÄÑÙÅπ’±∞ÄòòÅ…ï≈’ïÕ–πô’πç—•ΩπΩëîÄÙÙÄÃ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞ÅâΩΩ±ïÖ∏Åâ—π=∏ÄÙÅ¡•ç≠ïêπâ’——ΩπM—Ö—îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµΩëâ’Õ·ïç’—Ω»πï·ïç’—î††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ·çï¡—•Ω∏ÅôÖ•±’…îÄÙÅπ’±∞Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö›…•—ï5Ωëâ’ÕIïù•Õ—ï…M—Ö—•å°…ï≈’ïÕ–π°ΩÕ–∞Å…ï≈’ïÕ–π¡Ω…–∞Å…ï≈’ïÕ–π’π•–∞Å…ï≈’ïÕ–πÖëë…ïÕÃ∞Åâ—π=∏Ä¸ÄƒÄËÄ¿§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—°…Ω‹Åπï‹Å%=·çï¡—•Ω∏†ãBèFFFB˚BÁFFBÀB¯ÉB˚FBÎBÔB˚B˜B„BÔB¯ÉBﬂB√BˇB„FF0ÉFB◊BœB„FFFB¿à§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Åï……Ω»§ÅÏÅôÖ•±’…îÄÙÅï……Ω»ÏÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô•πÖ∞Å·çï¡—•Ω∏Å…ïÕ’±–ÄÙÅôÖ•±’…îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…ïÕ’±–ÄÑÙÅπ’±∞§Å…’π=πU•Q°…ïÖê††§Ä¥¯ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡•ç≠ïêπâ’——ΩπM—Ö—îÄÙÄÖâ—π=∏Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ï…Õ•Õ—A…Ω©ïç–†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ïë•—Ω»ÄÑÙÅπ’±∞§Åïë•—Ω»π•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãB{F#B„B«BÎB¿ÉBﬂB√BˇB„FB‡Å5Ωëâ’ÃËÄàÄ¨ÅëïÕç…•âï5Ωëâ’Õ……Ω»°…ïÕ’±–§∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞Å¡•ç≠ïêπ—ï·–Ä¨Ä°¡•ç≠ïêπâ’——ΩπM—Ö—îÄ¸ÄàËÉBKBkBlàÄËÄàËÉBKBØBkBlà§∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†â	ÉBKFB˚B–àπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡•ç≠ïêπ•π¡’—YÖ±’îÄÙÄÖ¡•ç≠ïêπ•π¡’—YÖ±’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ï…Õ•Õ—A…Ω©ïç–†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBKFB˚B–Å	ÄÙÄàÄ¨Ä°¡•ç≠ïêπ•π¡’—YÖ±’îÄ¸ÄàƒàÄËÄà¿à§∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ†ãBB◊FB◊FB˚B–ÉB˜B¿ÉF7BÎFB√BÙàπï≈’Ö±Ã°¡•ç≠ïêπ—Â¡î§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°¡•ç≠ïêπ—Ö…ùï—Mç…ïïπ%πëï‡Ä¯ÙÄ¿ÄòòÅ¡•ç≠ïêπ—Ö…ùï—Mç…ïïπ%πëï‡ÄÅÕç…ïïπÃπÕ•Èî†§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖç—•ŸÖ—ïMç…ïï∏°¡•ç≠ïêπ—Ö…ùï—Mç…ïïπ%πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBB◊FB◊FB˚B–ÉB˜B¿ÉB∑BÎFB√BÙÄàÄ¨Ä°¡•ç≠ïêπ—Ö…ùï—Mç…ïïπ%πëï‡Ä¨Äƒ§∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°¡•ç≠ïêπ—Ö…ùï—U…∞ÄÑÙÅπ’±∞ÄòòÄÖ¡•ç≠ïêπ—Ö…ùï—U…∞π—…•¥†§π•Õµ¡—‰†§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ—…‰ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ%π—ïπ–Åâ…Ω›Õï…%π—ïπ–ÄÙÅπï‹Å%π—ïπ–°%π—ïπ–πQ%=9}Y%\∞ÅU…§π¡Ö…Õî°¡•ç≠ïêπ—Ö…ùï—U…∞§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕ—Ö…—ç—•Ÿ•—‰°â…Ω›Õï…%π—ïπ–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅçÖ—ç†Ä°·çï¡—•Ω∏Åï‡§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBwB‘ÉFB”B√BÔB˚FF0ÉB˚FBÎFF/FF0ÅUI0ËÄàÄ¨Å¡•ç≠ïêπ—Ö…ùï—U…∞∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅ9ºÅ¡…Ω¡ï…—•ïÃÅë•Ö±ΩùÃÅ•∏ÅΩ¡ï…Ö—Ω»ÅµΩëî∏(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄººÅ9ºÅ¡…Ω¡ï…—•ïÃÅë•Ö±ΩùÃÅ•∏ÅΩ¡ï…Ö—Ω»ÅµΩëî∏(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÅÙ((ÄÄÄÄÄÄÄÅ=Ÿï……•ëîÅ¡’â±•åÅâΩΩ±ïÖ∏ÅΩπQΩ’ç°Ÿïπ–°5Ω—•ΩπŸïπ–ÅïŸïπ–§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Ω¡ï…Ö—Ω…5Ωëî§Å…ï—’…∏ÅΩπ=¡ï…Ö—Ω…QΩ’ç†°ïŸïπ–§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å‡ÄÙÅïŸïπ–πùï—`†§∞Å‰ÄÙÅïŸïπ–πùï—d†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅÖç—•Ω∏ÄÙÅïŸïπ–πùï—ç—•Ωπ5ÖÕ≠ïê†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Öç—•Ω∏ÄÙÙÅ5Ω—•ΩπŸïπ–πQ%=9}=]8§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅëΩ›π`ÄÙÅ‡ÏÅëΩ›πdÄÙÅ‰ÏÅµÖ…≈’ïï`ÄÙÅ‡ÏÅµÖ…≈’ïïdÄÙÅ‰Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅëΩ›π–ÄÙÅMÂÕ—ïµ±Ωç¨πï±Ö¡ÕïëIïÖ±—•µî†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅë…Öùù•πúÄÙÅôÖ±ÕîÏÅ…ïÕ•È•πúÄÙÅôÖ±ÕîÏÅµÖ…≈’ïïç—•ŸîÄÙÅôÖ±ÕîÏÅÕï±ïç—ïêÄÙÄ¥ƒÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅë…Öù=…•ù•πÃπç±ïÖ»†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâΩΩ±ïÖ∏Å°•–ÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å§ÄÙÅ›•ëùï—ÃπÕ•Èî†§Ä¥ÄƒÏÅ§Ä¯ÙÄ¿ÏÅ§¥¥§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Å‹ÄÙÅ›•ëùï—Ãπùï–°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö‹πŸ•Õ•â±î§ÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‡Ä¯ÙÅ‹π‡ÄòòÅ‡ÄÙÅ‹π‡Ä¨Å‹π‹ÄòòÅ‰Ä¯ÙÅ‹π‰ÄòòÅ‰ÄÙÅ‹π‰Ä¨Å‹π†§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖÕï±ïç—ïë]•ëùï—ÃπçΩπ—Ö•πÃ°§§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕï±ïç—ïë]•ëùï—Ãπç±ïÖ»†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕï±ïç—ïë]•ëùï—ÃπÖëê°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕï±ïç—ïêÄÙÅ§ÏÅÕ—Ö…—`ÄÙÅ‹π‡ÏÅÕ—Ö…—dÄÙÅ‹π‰ÏÅÕ—Ö…—\ÄÙÅ‹π‹ÏÅÕ—Ö…— ÄÙÅ‹π†Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Åù…•¿ÄÙÅë¿†»»§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ïÕ•È•πúÄÙÅÕï±ïç—ïë]•ëùï—ÃπÕ•Èî†§ÄÙÙÄƒ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄòòÅ‡Ä¯ÙÅ‹π‡Ä¨Å‹π‹Ä¥Åù…•¿ÄòòÅ‰Ä¯ÙÅ‹π‰Ä¨Å‹π†Ä¥Åù…•¿Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ°•–ÄÙÅ—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅâ…ïÖ¨Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°°•–§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅù…Ω’¡5•π`ÄÙÅù…Ω’¡5•πdÄÙÅ±ΩÖ–πA=M%Q%Y}%9%9%QdÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅù…Ω’¡5Ö·`ÄÙÅù…Ω’¡5Ö·dÄÙÅ±ΩÖ–π9Q%Y}%9%9%QdÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å•πëï‡ÄËÅÕï±ïç—ïë]•ëùï—Ã§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°•πëï‡ÄÄ¿ÅÒÅ•πëï‡Ä¯ÙÅ›•ëùï—ÃπÕ•Èî†§§ÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Å›•ëùï–ÄÙÅ›•ëùï—Ãπùï–°•πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅë…Öù=…•ù•πÃπ¡’–°•πëï‡∞Åπï‹Åô±ΩÖ—muÌ›•ëùï–π‡∞Å›•ëùï–πÂÙ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅù…Ω’¡5•π`ÄÙÅ5Ö—†πµ•∏°ù…Ω’¡5•π`∞Å›•ëùï–π‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅù…Ω’¡5•πdÄÙÅ5Ö—†πµ•∏°ù…Ω’¡5•πd∞Å›•ëùï–π‰§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅù…Ω’¡5Ö·`ÄÙÅ5Ö—†πµÖ‡°ù…Ω’¡5Ö·`∞Å›•ëùï–π‡Ä¨Å›•ëùï–π‹§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅù…Ω’¡5Ö·dÄÙÅ5Ö—†πµÖ‡°ù…Ω’¡5Ö·d∞Å›•ëùï–π‰Ä¨Å›•ëùï–π†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕï±ïç—ïë]•ëùï—Ãπç±ïÖ»†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµÖ…≈’ïïç—•ŸîÄÙÅ—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖπë…Ω•êπŸ•ï‹πY•ï›AÖ…ïπ–Å¡Ö…ïπ–ÄÙÅùï—AÖ…ïπ–†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°¡Ö…ïπ–ÄÑÙÅπ’±∞§Å¡Ö…ïπ–π…ï≈’ïÕ—•ÕÖ±±Ω›%π—ï…çï¡—QΩ’ç°Ÿïπ–°°•–ÅÒÅµÖ…≈’ïïç—•Ÿî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Öç—•Ω∏ÄÙÙÅ5Ω—•ΩπŸïπ–πQ%=9}5=Y§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Åë‡ÄÙÅ‡Ä¥ÅëΩ›π`∞Åë‰ÄÙÅ‰Ä¥ÅëΩ›πdÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°µÖ…≈’ïïç—•Ÿî§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°5Ö—†πÖâÃ°ë‡§Ä¨Å5Ö—†πÖâÃ°ë‰§Ä¯Åë¿†‘§§Åë…Öùù•πúÄÙÅ—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµÖ…≈’ïï`ÄÙÅ‡ÏÅµÖ…≈’ïïdÄÙÅ‰Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Õï±ïç—ïêÄ¯ÙÄ¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°5Ö—†πÖâÃ°ë‡§Ä¨Å5Ö—†πÖâÃ°ë‰§Ä¯Åë¿†‘§§Åë…Öùù•πúÄÙÅ—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ë…Öùù•πú§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Å‹ÄÙÅ›•ëùï—Ãπùï–°Õï±ïç—ïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°…ïÕ•È•πú§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–Åµ•π\ÄÙÅë¿†ÿ¿§∞Åµ•π ÄÙÅë¿†–¿§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–ÅµÖ·\ÄÙÅ5Ö—†πµÖ‡°µ•π\∞Åùï—]•ë—††§Ä¥Å‹π‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–ÅµÖ· ÄÙÅ5Ö—†πµÖ‡°µ•π ∞Åùï—!ï•ù°–†§Ä¥Å‹π‰§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‹ÄÙÅ5Ö—†πµÖ‡°µ•π\∞Å5Ö—†πµ•∏°µÖ·\∞ÅÕπÖ¡QΩ…•ê°Õ—Ö…—\Ä¨Åë‡§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π†ÄÙÅ5Ö—†πµÖ‡°µ•π ∞Å5Ö—†πµ•∏°µÖ· ∞ÅÕπÖ¡QΩ…•ê°Õ—Ö…— Ä¨Åë‰§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°Õï±ïç—ïë]•ëùï—ÃπÕ•Èî†§Ä¯Äƒ§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–ÅµΩŸï`ÄÙÅÕπÖ¡QΩ…•ê°ë‡§∞ÅµΩŸïdÄÙÅÕπÖ¡QΩ…•ê°ë‰§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµΩŸï`ÄÙÅÖπŸÖÕMï±ïç—•ΩπïΩµï—…‰πç±Öµ¡Q…ÖπÕ±Ö—•Ω∏†(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµΩŸï`∞Åù…Ω’¡5•π`∞Åù…Ω’¡5Ö·`∞Åùï—]•ë—††§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµΩŸïdÄÙÅÖπŸÖÕMï±ïç—•ΩπïΩµï—…‰πç±Öµ¡Q…ÖπÕ±Ö—•Ω∏†(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµΩŸïd∞Åù…Ω’¡5•πd∞Åù…Ω’¡5Ö·d∞Åùï—!ï•ù°–†§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°5Ö¿ππ—…‰Ò%π—ïùï»∞Åô±ΩÖ—mt¯ÅΩ…•ù•∏ÄËÅë…Öù=…•ù•πÃπïπ—…ÂMï–†§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–Å•πëï‡ÄÙÅΩ…•ù•∏πùï—-ï‰†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°•πëï‡ÄÄ¿ÅÒÅ•πëï‡Ä¯ÙÅ›•ëùï—ÃπÕ•Èî†§§ÅçΩπ—•π’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Åµïµâï»ÄÙÅ›•ëùï—Ãπùï–°•πëï‡§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµïµâï»π‡ÄÙÅΩ…•ù•∏πùï—YÖ±’î†•l¡tÄ¨ÅµΩŸï`Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµïµâï»π‰ÄÙÅΩ…•ù•∏πùï—YÖ±’î†•l≈tÄ¨ÅµΩŸïdÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–ÅµÖ·`ÄÙÅ5Ö—†πµÖ‡†¿∞Åùï—]•ë—††§Ä¥Å‹π‹§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–ÅµÖ·dÄÙÅ5Ö—†πµÖ‡†¿∞Åùï—!ï•ù°–†§Ä¥Å‹π†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡ÄÙÅ5Ö—†πµÖ‡†¿∞Å5Ö—†πµ•∏°µÖ·`∞ÅÕπÖ¡QΩ…•ê°Õ—Ö…—`Ä¨Åë‡§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‰ÄÙÅ5Ö—†πµÖ‡†¿∞Å5Ö—†πµ•∏°µÖ·d∞ÅÕπÖ¡QΩ…•ê°Õ—Ö…—dÄ¨Åë‰§§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Öç—•Ω∏ÄÙÙÅ5Ω—•ΩπŸïπ–πQ%=9}U@§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖπë…Ω•êπŸ•ï‹πY•ï›AÖ…ïπ–Å¡Ö…ïπ–ÄÙÅùï—AÖ…ïπ–†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°¡Ö…ïπ–ÄÑÙÅπ’±∞§Å¡Ö…ïπ–π…ï≈’ïÕ—•ÕÖ±±Ω›%π—ï…çï¡—QΩ’ç°Ÿïπ–°ôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°µÖ…≈’ïïç—•Ÿî§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅµÖ…≈’ïïç—•ŸîÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°ë…Öùù•πú§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å±ïô–ÄÙÅ5Ö—†πµ•∏°ëΩ›π`∞ÅµÖ…≈’ïï`§∞Å—Ω¿ÄÙÅ5Ö—†πµ•∏°ëΩ›πd∞ÅµÖ…≈’ïïd§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅô±ΩÖ–Å…•ù°–ÄÙÅ5Ö—†πµÖ‡°ëΩ›π`∞ÅµÖ…≈’ïï`§∞ÅâΩ——Ω¥ÄÙÅ5Ö—†πµÖ‡°ëΩ›πd∞ÅµÖ…≈’ïïd§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôΩ»Ä°•π–Å§ÄÙÄ¿ÏÅ§ÄÅ›•ëùï—ÃπÕ•Èî†§ÏÅ§¨¨§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Å‹ÄÙÅ›•ëùï—Ãπùï–°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°‹πŸ•Õ•â±îÄòòÅÖπŸÖÕMï±ïç—•ΩπïΩµï—…‰πçïπ—ï…%πÕ•ëî†(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ‹π‡∞Å‹π‰∞Å‹π‹∞Å‹π†∞Å±ïô–∞Å—Ω¿∞Å…•ù°–∞ÅâΩ——Ω¥§§(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕï±ïç—ïë]•ëùï—ÃπÖëê°§§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†ÖÕï±ïç—ïë]•ëùï—Ãπ•Õµ¡—‰†§§ÅÕï±ïç—ïêÄÙÅÕï±ïç—ïë]•ëùï—Ãπùï–°Õï±ïç—ïë]•ëùï—ÃπÕ•Èî†§Ä¥Äƒ§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°ë…Öùù•πú§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ¡ï…Õ•Õ—A…Ω©ïç–†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°Õï±ïç—ïêÄ¯ÙÄ¿ÄòòÅMÂÕ—ïµ±Ωç¨πï±Ö¡ÕïëIïÖ±—•µî†§Ä¥ÅëΩ›π–Ä¯ÙÄ‘‘¡0§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕ°Ω›]•ëùï—Ωπ—ï·—5ïπ‘†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°Õï±ïç—ïêÄ¯ÙÄ¿ÄòòÅôâë]•…ï5Ωëî§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ]•ëùï–Å¡•ç≠ïêÄÙÅ›•ëùï—Ãπùï–°Õï±ïç—ïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ†Ö•Õâê°¡•ç≠ïê§§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBHÉFB◊B€B„BÛB‘ÉFBÀF?BﬂB‡ÉBÀF/B«B◊FB‡Å	∑B«BÔB˚BËà∞ÅQΩÖÕ–π19Q!}M!=IP§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°›•…ïMΩ’…çîÄÄ¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•…ïMΩ’…çîÄÙÅÕï±ïç—ïêÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅQΩÖÕ–πµÖ≠ïQï·–°5Ö•πç—•Ÿ•—‰π—°•Ã∞ÄãBcFFB˚FB˜B„BËÉBÀF/B«FB√BÙËÄàÄ¨Å¡•ç≠ïêπ—ï·–Ä¨Äà∏ÉBãB◊BˇB◊FF0ÉBÀF/B«B◊FB‡ÉFB◊BÔB◊BÀB˚B‰ÉB«BÔB˚BËà∞ÅQΩÖÕ–π19Q!}1=9§πÕ°Ω‹†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•π–ÅÕΩ’…çîÄÙÅ›•…ïMΩ’…çîÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ›•…ïMΩ’…çîÄÙÄ¥ƒÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅçΩππïç—âë]•ëùï—Ã°ÕΩ’…çî∞ÅÕï±ïç—ïê§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅôâë]•…ï5ΩëîÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙÅï±ÕîÅ•òÄ°Õï±ïç—ïêÄ¯ÙÄ¿§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅïë•—Mï±ïç—ïë]•ëùï–†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅë…Öù=…•ù•πÃπç±ïÖ»†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°Öç—•Ω∏ÄÙÙÅ5Ω—•ΩπŸïπ–πQ%=9}90§ÅÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÖπë…Ω•êπŸ•ï‹πY•ï›AÖ…ïπ–Å¡Ö…ïπ–ÄÙÅùï—AÖ…ïπ–†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•òÄ°¡Ö…ïπ–ÄÑÙÅπ’±∞§Å¡Ö…ïπ–π…ï≈’ïÕ—•ÕÖ±±Ω›%π—ï…çï¡—QΩ’ç°Ÿïπ–°ôÖ±Õî§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅÕï±ïç—ïêÄÙÄ¥ƒÏÅë…Öùù•πúÄÙÅôÖ±ÕîÏÅ…ïÕ•È•πúÄÙÅôÖ±ÕîÏÅµÖ…≈’ïïç—•ŸîÄÙÅôÖ±ÕîÏ(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅë…Öù=…•ù•πÃπç±ïÖ»†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ•πŸÖ±•ëÖ—î†§Ï(ÄÄÄÄÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å—…’îÏ(ÄÄÄÄÄÄÄÄÄÄÄÅÙ(ÄÄÄÄÄÄÄÄÄÄÄÅ…ï—’…∏Å—…’îÏ(ÄÄÄÄÄÄÄÅÙ(ÄÄÄÅÙ)Ù(