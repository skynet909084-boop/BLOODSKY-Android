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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
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
    private static final int REQUEST_EXPORT_PROJECT = 7101;
    private static final int REQUEST_IMPORT_PROJECT = 7102;
    private static final int REQUEST_PICK_MEDIA = 7103;
    private EditText pendingMediaField;
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
    private boolean isOperatorMode = false;
    private boolean[] fbdCacheValid = new boolean[0];
    private boolean[] fbdCacheValues = new boolean[0];
    private double[] fbdCacheNumeric = new double[0];
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

    private MediaPlayer mediaPlayer;
    private TextToSpeech textToSpeech;
    private final java.util.Map<String, Bitmap> imageCache = new java.util.HashMap<>();

    private Bitmap loadCachedBitmap(String pathOrUri) {
        if (pathOrUri == null || pathOrUri.trim().isEmpty()) return null;
        if (imageCache.containsKey(pathOrUri)) return imageCache.get(pathOrUri);
        Bitmap bitmap = null;
        try {
            if (pathOrUri.startsWith("content://")) {
                try (InputStream is = getContentResolver().openInputStream(Uri.parse(pathOrUri))) {
                    bitmap = BitmapFactory.decodeStream(is);
                }
            } else if (new java.io.File(pathOrUri).exists()) {
                bitmap = BitmapFactory.decodeFile(pathOrUri);
            } else {
                java.io.File localMedia = new java.io.File(getFilesDir(), pathOrUri);
                if (localMedia.exists()) {
                    bitmap = BitmapFactory.decodeFile(localMedia.getAbsolutePath());
                }
            }
        } catch (Exception ignored) {}
        imageCache.put(pathOrUri, bitmap);
        return bitmap;
    }

    public enum UsbHardwareStatus {
        NO_USB_HOST_FEATURE("Устройство Android не поддерживает USB Host (OTG)"),
        NO_USB_DEVICE_ATTACHED("USB-RS485 адаптер не подключён"),
        USB_PERMISSION_DENIED("Разрешение на доступ к USB-адаптеру не предоставлено"),
        USB_READY("USB-RS485 адаптер подключён и готов к работе");

        private final String description;
        UsbHardwareStatus(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    private BroadcastReceiver usbReceiver;

    public UsbHardwareStatus checkUsbHardwareStatus() {
        return checkUsbHardwareStatusStatic(this);
    }

    public static UsbHardwareStatus checkUsbHardwareStatusStatic(Context context) {
        if (context == null) return UsbHardwareStatus.NO_USB_HOST_FEATURE;
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            return UsbHardwareStatus.NO_USB_HOST_FEATURE;
        }
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) return UsbHardwareStatus.NO_USB_HOST_FEATURE;
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList == null || deviceList.isEmpty()) {
            return UsbHardwareStatus.NO_USB_DEVICE_ATTACHED;
        }
        for (UsbDevice device : deviceList.values()) {
            if (!usbManager.hasPermission(device)) {
                return UsbHardwareStatus.USB_PERMISSION_DENIED;
            }
        }
        return UsbHardwareStatus.USB_READY;
    }

    private void registerUsbReceiver() {
        if (usbReceiver != null) return;
        usbReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    Toast.makeText(MainActivity.this, "USB-устройство отключено", Toast.LENGTH_SHORT).show();
                } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    Toast.makeText(MainActivity.this, "USB-устройство подключено", Toast.LENGTH_SHORT).show();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(usbReceiver, filter);
    }

    private void unregisterUsbReceiver() {
        if (usbReceiver != null) {
            try { unregisterReceiver(usbReceiver); } catch (Exception ignored) {}
            usbReceiver = null;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            projectName = state.getString("projectName", projectName);
            currentScreenIndex = state.getInt("currentScreenIndex", currentScreenIndex);
            isOperatorMode = state.getBoolean("isOperatorMode", isOperatorMode);
            fbdWireMode = state.getBoolean("fbdWireMode", fbdWireMode);
        }
        try {
            textToSpeech = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                    try { textToSpeech.setLanguage(new java.util.Locale("ru", "RU")); } catch (Exception ignored) {}
                }
            });
        } catch (Exception ex) {
            android.util.Log.e("BLOODSKY", "TTS init error", ex);
        }
        registerUsbReceiver();
        showHome();
    }

    @Override protected void onPause() {
        stopModbusPolling();
        if (mediaPlayer != null) {
            try { mediaPlayer.pause(); } catch (Exception ignored) {}
        }
        if (textToSpeech != null) {
            try { textToSpeech.stop(); } catch (Exception ignored) {}
        }
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (editor != null) {
            startModbusPolling();
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("projectName", projectName);
        outState.putInt("currentScreenIndex", currentScreenIndex);
        outState.putBoolean("isOperatorMode", isOperatorMode);
        outState.putBoolean("fbdWireMode", fbdWireMode);
    }

    @Override protected void onDestroy() {
        unregisterUsbReceiver();
        stopModbusPolling();
        editor = null;
        modbusExecutor.shutdownNow();
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.release(); } catch (Exception ignored) {}
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
                try { mediaPlayer.stop(); mediaPlayer.release(); } catch (Exception ignored) {}
                mediaPlayer = null;
            }
            if (pathOrUri != null && pathOrUri.startsWith("content://")) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(this, Uri.parse(pathOrUri));
                mediaPlayer.prepare();
                mediaPlayer.start();
            } else if (pathOrUri != null && new java.io.File(pathOrUri).exists()) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(pathOrUri);
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
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        GradientDrawable face = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.rgb(190, 190, 190), Color.rgb(132, 132, 132), Color.rgb(190, 190, 190) });
        face.setCornerRadius(dp(2));
        face.setStroke(dp(1), Color.rgb(76, 76, 76));
        button.setBackground(face);
        button.setOnClickListener(v -> task.run());
        return button;
    }

    private void showHome() {
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
        addLegacyMenuItem("Экспорт проекта (ZIP / JSON)", this::exportProject, menuWidth);
        addLegacyMenuItem("Импорт проекта (ZIP / JSON)", this::importProject, menuWidth);
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
        if (functionCode != 1 && functionCode != 2 && functionCode != 3 && functionCode != 4) {
            throw new IllegalArgumentException("Unsupported Modbus function");
        }
        int transaction = (int) (SystemClock.elapsedRealtime() & 0xffff);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            out.writeShort(transaction);
            out.writeShort(0);
            out.writeShort(6);
            out.writeByte(unit);
            out.writeByte(functionCode);
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
                throw new IOException("Modbus: " + describeModbusExceptionCodeStatic(exceptionCode)
                        + " (код " + exceptionCode + ")");
            }
            if (function != functionCode || pdu.length < 2) throw new IOException("Неожиданный код функции");
            int byteCount = pdu[1] & 0xff;
            int expectedByteCount = (functionCode == 1 || functionCode == 2) ? (count + 7) / 8 : count * 2;
            if (byteCount != expectedByteCount || pdu.length != 2 + byteCount) {
                throw new IOException("Размер ответа не совпадает с запросом");
            }
            StringBuilder report = new StringBuilder();
            String funcName = functionCode == 1 ? "coils (FC01)\n" : (functionCode == 2 ? "discrete inputs (FC02)\n" : (functionCode == 3 ? "holding registers (FC03)\n" : "input registers (FC04)\n"));
            report.append("Успешно: ").append(count).append(" ").append(funcName);
            int baseAddr = functionCode == 1 ? 1 : (functionCode == 2 ? 10001 : (functionCode == 3 ? 40001 : 30001));
            for (int i = 0; i < count; i++) {
                int value;
                if (functionCode == 1 || functionCode == 2) {
                    value = ((pdu[2 + (i / 8)] & 0xff) >> (i % 8)) & 1;
                } else {
                    value = ((pdu[2 + i * 2] & 0xff) << 8) | (pdu[3 + i * 2] & 0xff);
                }
                report.append(baseAddr + startAddress + i).append("  |  ")
                        .append(value).append("  |  0x")
                        .append(String.format(java.util.Locale.ROOT, "%04X", value)).append('\n');
            }
            return report.toString();
        }
    }

    public static boolean writeModbusRegisterStatic(String host, int port, int unit, int address, int value) throws Exception {
        int transaction = (int) (SystemClock.elapsedRealtime() & 0xffff);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            out.writeShort(transaction);
            out.writeShort(0);
            out.writeShort(6);
            out.writeByte(unit);
            out.writeByte(6);
            out.writeShort(address);
            out.writeShort(value);
            out.flush();

            byte[] header = new byte[7];
            in.readFully(header);
            int responseTransaction = ((header[0] & 0xff) << 8) | (header[1] & 0xff);
            int protocol = ((header[2] & 0xff) << 8) | (header[3] & 0xff);
            int length = ((header[4] & 0xff) << 8) | (header[5] & 0xff);
            int responseUnit = header[6] & 0xff;
            if (responseTransaction != transaction || protocol != 0 || responseUnit != unit || length < 2 || length > 254) {
                throw new IOException("Некорректный заголовок ответа при записи");
            }
            byte[] pdu = new byte[length - 1];
            in.readFully(pdu);
            int function = pdu[0] & 0xff;
            if (function == 0x86) {
                int exceptionCode = pdu.length > 1 ? pdu[1] & 0xff : 0;
                throw new IOException("Modbus: " + describeModbusExceptionCodeStatic(exceptionCode) + " (код " + exceptionCode + ")");
            }
            return function == 6;
        }
    }

    public static boolean writeModbusMultipleRegistersStatic(String host, int port, int unit, int startAddress, int[] values) throws Exception {
        int transaction = (int) (SystemClock.elapsedRealtime() & 0xffff);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            int byteCount = values.length * 2;
            out.writeShort(transaction);
            out.writeShort(0);
            out.writeShort(7 + byteCount);
            out.writeByte(unit);
            out.writeByte(16);
            out.writeShort(startAddress);
            out.writeShort(values.length);
            out.writeByte(byteCount);
            for (int val : values) {
                out.writeShort(val);
            }
            out.flush();

            byte[] header = new byte[7];
            in.readFully(header);
            int responseTransaction = ((header[0] & 0xff) << 8) | (header[1] & 0xff);
            int protocol = ((header[2] & 0xff) << 8) | (header[3] & 0xff);
            int length = ((header[4] & 0xff) << 8) | (header[5] & 0xff);
            int responseUnit = header[6] & 0xff;
            if (responseTransaction != transaction || protocol != 0 || responseUnit != unit || length < 2 || length > 254) {
                throw new IOException("Некорректный заголовок ответа при групповой записи");
            }
            byte[] pdu = new byte[length - 1];
            in.readFully(pdu);
            int function = pdu[0] & 0xff;
            if (function == 0x90) {
                int exceptionCode = pdu.length > 1 ? pdu[1] & 0xff : 0;
                throw new IOException("Modbus: " + describeModbusExceptionCodeStatic(exceptionCode) + " (код " + exceptionCode + ")");
            }
            return function == 16;
        }
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
        modbusPollingActive = true;
        mainHandler.postDelayed(modbusPollRunnable, 1000L);
    }

    private void stopModbusPolling() {
        modbusPollingActive = false;
        mainHandler.removeCallbacks(modbusPollRunnable);
    }

    private void pollBoundWidgets() {
        final List<Widget> snapshotWidgets = new ArrayList<>(widgets);
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
                    if (isFinishing() || isDestroyed() || editor == null) return;
                    for (Map.Entry<Integer, String> entry : updates.entrySet()) {
                        int index = entry.getKey();
                        if (index < 0 || index >= widgets.size() || index >= snapshotWidgets.size()
                                || widgets.get(index) != snapshotWidgets.get(index)) continue;
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
                if (!getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(ACTIVE_PROJECT, json).commit()) throw new IllegalStateException("Could not save imported project");
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

    private void toggleOperatorMode() {
        isOperatorMode = !isOperatorMode;
        fbdWireMode = false;
        wireSource = -1;
        Toast.makeText(this, isOperatorMode ? "Режим Оператора (Работа)" : "Режим Редактора", Toast.LENGTH_SHORT).show();
        showEditor();
    }

    private void showEditor() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(35, 35, 35));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(3), dp(2), dp(3), dp(2));
        top.setBackgroundColor(Color.rgb(35, 35, 35));
        top.addView(legacyToolbarButton("☰", this::showHome),
                new LinearLayout.LayoutParams(dp(42), dp(38)));
        Button rename = legacyToolbarButton(projectName, this::editProjectName);
        top.addView(rename, new LinearLayout.LayoutParams(0, dp(38), 1));
        top.addView(legacyToolbarButton(isOperatorMode ? "▶ Работа" : "✏️ Редакт.", this::toggleOperatorMode),
                new LinearLayout.LayoutParams(dp(80), dp(38)));
        top.addView(legacyToolbarButton("💾", () -> {
            persistProject();
            Toast.makeText(this, "Проект сохранён на устройстве", Toast.LENGTH_SHORT).show();
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
        if (!isOperatorMode) {
            screenBar.addView(legacyToolbarButton("Фон", this::editScreenBackground),
                    new LinearLayout.LayoutParams(dp(54), dp(34)));
        }
        screenBar.addView(legacyToolbarButton("▶", this::nextScreen),
                new LinearLayout.LayoutParams(dp(38), dp(34)));
        if (!isOperatorMode) {
            screenBar.addView(legacyToolbarButton("+", this::addScreen),
                    new LinearLayout.LayoutParams(dp(38), dp(34)));
            screenBar.addView(legacyToolbarButton("−", this::removeCurrentScreen),
                    new LinearLayout.LayoutParams(dp(38), dp(34)));
        }
        root.addView(screenBar, new LinearLayout.LayoutParams(-1, dp(36)));

        if (!isOperatorMode) {
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
            toolsBar.addView(legacyToolbarButton("Выравн.", this::showAlignDialog),
                    new LinearLayout.LayoutParams(dp(60), dp(36)));
            toolsBar.addView(legacyToolbarButton("Modbus", this::showModbusDiagnostics),
                    new LinearLayout.LayoutParams(dp(64), dp(36)));
            toolsBar.addView(legacyToolbarButton("Настр.", this::showProjectSettingsDialog),
                    new LinearLayout.LayoutParams(dp(60), dp(36)));
            root.addView(toolsBar, new LinearLayout.LayoutParams(-1, dp(40)));
        }

        editor = new EditorCanvas();
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));

        if (!isOperatorMode) {
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
        }
        setContentView(root);
        editor.invalidate();
        startModbusPolling();
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
        copy.minValue = source.minValue; copy.maxValue = source.maxValue;
        copy.backgroundColor = source.backgroundColor; copy.textColor = source.textColor; copy.textSizeSp = source.textSizeSp;
        copy.visible = source.visible; copy.showFrame = source.showFrame; copy.autoResizeText = source.autoResizeText;
        for (int i = 0; i < 16; i++) {
            copy.dynTextStates[i] = source.dynTextStates[i];
            copy.dynTextColors[i] = source.dynTextColors[i];
            copy.bitListLabels[i] = source.bitListLabels[i];
        }
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
                "FBD — CTD (счёт вниз)", "FBD — R_TRIG", "FBD — F_TRIG"
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
                "FBD CTD", "FBD R_TRIG", "FBD F_TRIG"
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
                "RS", "SR", "CTU", "CTD", "R_TRIG", "F_TRIG"
        };
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(4));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(list);
        final AlertDialog[] picker = new AlertDialog[1];

        for (int i = 0; i < labels.length; i++) {
            if (i == 0) {
                TextView section = new TextView(this);
                section.setText("КОМПОНЕНТЫ И ИНДИКАТОРЫ");
                section.setTextColor(Color.rgb(37, 99, 235));
                section.setTextSize(12);
                section.setTypeface(null, android.graphics.Typeface.BOLD);
                section.setPadding(dp(12), dp(8), dp(12), dp(6));
                list.addView(section, new LinearLayout.LayoutParams(-1, -2));
            } else if (i == 14) {
                TextView section = new TextView(this);
                section.setText("ФУНКЦИОНАЛЬНЫЕ БЛОКИ (FBD)");
                section.setTextColor(Color.rgb(37, 99, 235));
                section.setTextSize(12);
                section.setTypeface(null, android.graphics.Typeface.BOLD);
                section.setPadding(dp(12), dp(14), dp(12), dp(6));
                list.addView(section, new LinearLayout.LayoutParams(-1, -2));
            }
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setBackgroundColor(i % 2 == 0 ? Color.rgb(248, 250, 252) : Color.WHITE);
            row.setMinimumHeight(dp(52));

            ImageView thumbView = new ImageView(this);
            Bitmap thumbBmp = createWidgetThumbnail(types[i], defaults[i]);
            thumbView.setImageBitmap(thumbBmp);
            row.addView(thumbView, new LinearLayout.LayoutParams(dp(44), dp(44)));

            TextView item = new TextView(this);
            item.setText("  " + labels[i]);
            item.setTextColor(Color.rgb(30, 41, 59));
            item.setTextSize(15);
            item.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));

            row.setOnClickListener(v -> {
                if (picker[0] != null) picker[0].dismiss();
                addWidget(types[index], defaults[index]);
            });
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
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

    private Bitmap createWidgetThumbnail(String type, String defaultText) {
        int w = dp(44), h = dp(44);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setColor(Color.rgb(241, 245, 249));
        canvas.drawRoundRect(new RectF(2, 2, w - 2, h - 2), dp(4), dp(4), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.rgb(203, 213, 225));
        canvas.drawRoundRect(new RectF(2, 2, w - 2, h - 2), dp(4), dp(4), paint);

        paint.setStyle(Paint.Style.FILL);
        if (type.startsWith("FBD ")) {
            paint.setColor(Color.rgb(136, 136, 136));
            canvas.drawRoundRect(new RectF(4, 4, w - 4, h - 4), dp(3), dp(3), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            paint.setColor(Color.BLACK);
            canvas.drawRoundRect(new RectF(4, 4, w - 4, h - 4), dp(3), dp(3), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.BLACK);
            paint.setTextSize(dp(10));
            paint.setTextAlign(Paint.Align.CENTER);
            String label = type.replace("FBD ", "").replace(" (селектор)", "");
            if (label.length() > 6) label = label.substring(0, 5);
            canvas.drawText(label, w / 2f, h / 2f + dp(3.5f), paint);
        } else if ("Индикатор".equals(type) || "Двухпозиционный индикатор".equals(type)) {
            paint.setColor(Color.rgb(22, 163, 74));
            canvas.drawCircle(w / 2f, h / 2f, dp(10), paint);
            paint.setColor(Color.rgb(187, 247, 208));
            canvas.drawCircle(w / 2f - dp(3), h / 2f - dp(3), dp(3), paint);
        } else if ("Кнопка".equals(type)) {
            paint.setColor(Color.rgb(37, 99, 235));
            canvas.drawRoundRect(new RectF(6, 12, w - 6, h - 12), dp(4), dp(4), paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(dp(10));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("BTN", w / 2f, h / 2f + dp(3.5f), paint);
        } else if ("Задатчик".equals(type)) {
            paint.setColor(Color.rgb(217, 119, 6));
            canvas.drawRoundRect(new RectF(6, 14, w - 6, h - 14), dp(3), dp(3), paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(dp(10));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("−  +", w / 2f, h / 2f + dp(3.5f), paint);
        } else if ("Шкала / Стрелка".equals(type)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(71, 85, 105));
            canvas.drawArc(new RectF(8, 10, w - 8, h - 6), 180, 180, false, paint);
            paint.setColor(Color.rgb(220, 38, 38));
            canvas.drawLine(w / 2f, h - 10, w / 2f + dp(8), 14, paint);
        } else if ("Прогресс-бар".equals(type)) {
            paint.setColor(Color.rgb(226, 232, 240));
            canvas.drawRect(8, 16, w - 8, h - 16, paint);
            paint.setColor(Color.rgb(34, 197, 94));
            canvas.drawRect(8, 16, w - 16, h - 16, paint);
        } else if ("График / Тренд".equals(type)) {
            paint.setColor(Color.rgb(34, 197, 94));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawLine(8, h - 12, 18, 14, paint);
            canvas.drawLine(18, 14, 28, 26, paint);
            canvas.drawLine(28, 26, w - 8, 12, paint);
        } else if ("Звуковой сигнал".equals(type)) {
            paint.setColor(Color.rgb(239, 68, 68));
            canvas.drawCircle(w / 2f, h / 2f, dp(11), paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(dp(12));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("🔔", w / 2f, h / 2f + dp(4), paint);
        } else if ("Многопозиционное фото".equals(type)) {
            paint.setColor(Color.rgb(147, 51, 234));
            canvas.drawRoundRect(new RectF(8, 10, w - 8, h - 10), dp(3), dp(3), paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(dp(12));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("🖼", w / 2f, h / 2f + dp(4), paint);
        } else {
            paint.setColor(Color.rgb(71, 85, 105));
            paint.setTextSize(dp(11));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(defaultText.length() > 5 ? defaultText.substring(0, 5) : defaultText, w / 2f, h / 2f + dp(4), paint);
        }
        return bitmap;
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
        if (type.equals("Шкала / Стрелка") || type.equals("График / Тренд")) { w.w = dp(200); w.h = dp(110); }
        if (type.equals("Прогресс-бар") || type.equals("Битовый список")) { w.w = dp(180); w.h = dp(64); }
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
                    if (minValField != null) {
                        w.minValue = readDouble(minValField, w.minValue);
                        w.maxValue = readDouble(maxValField, w.maxValue);
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
        w.x = obj.optInt("x", 40); w.y = obj.optInt("y", 50);
        w.w = obj.optInt("w", 160); w.h = obj.optInt("h", 58);

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
        exportProjectToZip(outputStream, projectJson, null);
    }

    public static void exportProjectToZip(OutputStream outputStream, String projectJson, Map<String, byte[]> mediaFiles) throws java.io.IOException {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(outputStream)) {
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("TableNameProject.json");
            zos.putNextEntry(entry);
            byte[] bytes = projectJson.getBytes(StandardCharsets.UTF_8);
            zos.write(bytes, 0, bytes.length);
            zos.closeEntry();

            if (mediaFiles != null && !mediaFiles.isEmpty()) {
                for (Map.Entry<String, byte[]> media : mediaFiles.entrySet()) {
                    String fileName = media.getKey();
                    if (fileName == null || fileName.trim().isEmpty()) continue;
                    fileName = fileName.replace('\\', '/');
                    if (fileName.contains("..") || fileName.startsWith("/")) {
                        throw new IllegalArgumentException("Unsafe media entry path: " + fileName);
                    }
                    String entryPath = fileName.startsWith("media/") ? fileName : "media/" + fileName;
                    java.util.zip.ZipEntry mediaEntry = new java.util.zip.ZipEntry(entryPath);
                    zos.putNextEntry(mediaEntry);
                    byte[] mediaBytes = media.getValue();
                    if (mediaBytes != null && mediaBytes.length > 0) {
                        zos.write(mediaBytes, 0, mediaBytes.length);
                    }
                    zos.closeEntry();
                }
            }
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
                if ("TableNameProject.json".equalsIgnoreCase(name) || name.endsWith(".json")) {
                    if (projectJsonBytes != null) {
                        throw new IllegalArgumentException("Multiple JSON project files in archive");
                    }
                    projectJsonBytes = entryBuffer.toByteArray();
                } else if (name.toLowerCase(java.util.Locale.ROOT).startsWith("media/") && extractedMedia != null) {
                    String relativeMediaName = name.substring("media/".length());
                    extractedMedia.put(relativeMediaName, entryBuffer.toByteArray());
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

    private void persistProject() {
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
            project.put("projectName", projectName);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(ACTIVE_PROJECT, project.toString())
                    .putString("project_name", projectName).apply();
        } catch (Exception ignored) {
            Toast.makeText(this, "Не удалось сохранить проект", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadProject() {
        widgets.clear(); screens.clear(); currentScreenIndex = 0;
        projectExtraFields = new JSONObject();
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        projectName = prefs.getString("project_name", projectName);
        String json = prefs.getString(ACTIVE_PROJECT, "[]");
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
        for (Widget w : screens.get(currentScreenIndex).widgets) widgets.add(copyWidget(w));
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
            java.util.HashSet<Long> uniqueEdges = new java.util.HashSet<>();
            for (int j = 0; j < connections.length(); j++) {
                JSONObject edge = connections.optJSONObject(j);
                if (edge == null) throw new IllegalArgumentException("Connection must be an object");
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
                long edgeKey = (((long) source) << 32) | (target & 0xffffffffL);
                if (!uniqueEdges.add(edgeKey)) throw new IllegalArgumentException("Duplicate FBD connection");
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
            int x = widget.optInt("x", 0), y = widget.optInt("y", 0), w = widget.optInt("w", 160), h = widget.optInt("h", 58);
            if (x < -100000 || x > 100000 || y < -100000 || y > 100000 || w < 1 || w > 100000 || h < 1 || h > 100000) throw new IllegalArgumentException("Widget geometry out of range");
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
        public FbdConnection(int source, int target) { this.source = source; this.target = target; }
    }

    public static boolean isFbdWidget(Widget widget) {
        return widget != null && widget.type != null && widget.type.startsWith("FBD ");
    }

    private boolean isFbd(Widget widget) {
        return isFbdWidget(widget);
    }

    public static void runFbdScanCycle(List<Widget> widgetsList, List<FbdConnection> connectionsList, boolean[] cacheValid, boolean[] cacheValues, double[] cacheNumeric) {
        if (widgetsList == null || cacheValid == null || cacheValues == null || cacheNumeric == null) return;
        java.util.Arrays.fill(cacheValid, false);
        for (int i = 0; i < widgetsList.size(); i++) {
            if (isFbdWidget(widgetsList.get(i))) {
                evaluateFbdBlock(widgetsList, connectionsList, i, new boolean[widgetsList.size()], cacheValid, cacheValues, cacheNumeric);
            }
        }
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
        if (connectionsList != null) {
            for (FbdConnection connection : connectionsList) {
                if (connection.target == index) {
                    inputs.add(evaluateFbdBlock(widgetsList, connectionsList, connection.source, visiting, cacheValid, cacheValues, cacheNumeric));
                    numInputs.add(evaluateFbdBlockNumeric(widgetsList, connectionsList, connection.source, visiting, cacheValid, cacheValues, cacheNumeric));
                }
            }
        }
        visiting[index] = false;

        if ("FBD Сложение".equals(widget.type)) {
            double sum = 0.0;
            for (Double v : numInputs) sum += v;
            if (Double.isNaN(sum) || Double.isInfinite(sum)) sum = 0.0;
            cacheNumeric[index] = sum;
            return sum != 0.0;
        }
        if ("FBD Вычитание".equals(widget.type)) {
            double diff = numInputs.isEmpty() ? 0.0 : numInputs.get(0);
            for (int i = 1; i < numInputs.size(); i++) diff -= numInputs.get(i);
            if (Double.isNaN(diff) || Double.isInfinite(diff)) diff = 0.0;
            cacheNumeric[index] = diff;
            return diff != 0.0;
        }
        if ("FBD Умножение".equals(widget.type)) {
            double prod = numInputs.isEmpty() ? 0.0 : 1.0;
            for (Double v : numInputs) prod *= v;
            if (Double.isNaN(prod) || Double.isInfinite(prod)) prod = 0.0;
            cacheNumeric[index] = prod;
            return prod != 0.0;
        }
        if ("FBD Деление".equals(widget.type)) {
            double div = numInputs.isEmpty() ? 0.0 : numInputs.get(0);
            for (int i = 1; i < numInputs.size(); i++) {
                double denom = numInputs.get(i);
                div = (denom != 0.0) ? div / denom : 0.0;
            }
            if (Double.isNaN(div) || Double.isInfinite(div)) div = 0.0;
            cacheNumeric[index] = div;
            return div != 0.0;
        }
        if ("FBD ABS".equals(widget.type)) {
            double val = numInputs.isEmpty() ? 0.0 : Math.abs(numInputs.get(0));
            if (Double.isNaN(val) || Double.isInfinite(val)) val = 0.0;
            cacheNumeric[index] = val;
            return val != 0.0;
        }
        if ("FBD POW".equals(widget.type)) {
            double base = numInputs.isEmpty() ? widget.compareA : numInputs.get(0);
            double exp = numInputs.size() > 1 ? numInputs.get(1) : 1.0;
            double res = Math.pow(base, exp);
            if (Double.isNaN(res) || Double.isInfinite(res)) res = 0.0;
            cacheNumeric[index] = res;
            return res != 0.0;
        }
        if ("FBD SQRT".equals(widget.type)) {
            double val = numInputs.isEmpty() ? widget.compareA : Math.max(0.0, numInputs.get(0));
            double res = Math.sqrt(Math.max(0.0, val));
            if (Double.isNaN(res) || Double.isInfinite(res)) res = 0.0;
            cacheNumeric[index] = res;
            return res != 0.0;
        }
        if ("FBD SIN".equals(widget.type)) {
            double val = numInputs.isEmpty() ? widget.compareA : numInputs.get(0);
            double res = Math.sin(val);
            if (Double.isNaN(res) || Double.isInfinite(res)) res = 0.0;
            cacheNumeric[index] = res;
            return res != 0.0;
        }
        if ("FBD COS".equals(widget.type)) {
            double val = numInputs.isEmpty() ? widget.compareA : numInputs.get(0);
            double res = Math.cos(val);
            if (Double.isNaN(res) || Double.isInfinite(res)) res = 0.0;
            cacheNumeric[index] = res;
            return res != 0.0;
        }
        if ("FBD TAN".equals(widget.type)) {
            double val = numInputs.isEmpty() ? widget.compareA : numInputs.get(0);
            double res = Math.tan(val);
            if (Double.isNaN(res) || Double.isInfinite(res)) res = 0.0;
            cacheNumeric[index] = res;
            return res != 0.0;
        }
        if ("FBD FloatToInt".equals(widget.type)) {
            double val = numInputs.isEmpty() ? widget.compareA : numInputs.get(0);
            double res = (double) ((long) val);
            cacheNumeric[index] = res;
            return res != 0.0;
        }
        if ("FBD IntToFloat".equals(widget.type)) {
            double val = numInputs.isEmpty() ? widget.compareA : numInputs.get(0);
            cacheNumeric[index] = val;
            return val != 0.0;
        }
        if ("FBD MIN_MAX".equals(widget.type)) {
            double minV = numInputs.isEmpty() ? 0.0 : numInputs.get(0);
            for (Double v : numInputs) minV = Math.min(minV, v);
            if (Double.isNaN(minV) || Double.isInfinite(minV)) minV = 0.0;
            cacheNumeric[index] = minV;
            return minV != 0.0;
        }
        if ("FBD MOVE".equals(widget.type) || "FBD SEL".equals(widget.type)) {
            double val = numInputs.isEmpty() ? widget.compareA : numInputs.get(0);
            if (Double.isNaN(val) || Double.isInfinite(val)) val = 0.0;
            cacheNumeric[index] = val;
            return val != 0.0;
        }

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
            switch (normalizeComparisonOperatorStatic(widget.compareOperator)) {
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
            if (!isOperatorMode) {
                paint.setColor(Color.rgb(228, 234, 241));
                paint.setStrokeWidth(1);
                int grid = dp(20);
                for (int x = 0; x < getWidth(); x += grid) canvas.drawLine(x, 0, x, getHeight(), paint);
                for (int y = 0; y < getHeight(); y += grid) canvas.drawLine(0, y, getWidth(), y, paint);
            }

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
            fbdCacheValid = new boolean[widgets.size()];
            fbdCacheValues = new boolean[widgets.size()];
            fbdCacheNumeric = new double[widgets.size()];
            for (int i = 0; i < widgets.size(); i++) {
                Widget w = widgets.get(i);
                if (!w.visible) continue;
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
                canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                boolean isSel = !isOperatorMode && i == selected;
                if (w.showFrame || isSel) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(isSel ? 2.5f : 1));
                    paint.setColor(isSel ? Color.rgb(21, 101, 192) : Color.rgb(148, 163, 184));
                    canvas.drawRoundRect(rect, dp(6), dp(6), paint);
                }
                if (isSel) {
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
                    paint.setColor(indicatorColor);
                    canvas.drawCircle(w.x + dp(16), w.y + w.h / 2, dp(6), paint);
                    paint.setColor(w.textColor);
                    String indicatorText = w.text + (indicatorValue != null ? ": " + indicatorValue : "");
                    if (w.autoResizeText) paint.setTextSize(Math.min(paint.getTextSize(), Math.max(dp(8), (w.w - dp(36)) / Math.max(4f, indicatorText.length() * 0.58f))));
                    canvas.drawText(indicatorText, w.x + dp(29), w.y + w.h / 2 + dp(5), paint);
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
                        if (!isOperatorMode) {
                            resizing = x >= w.x + w.w - grip && y >= w.y + w.h - grip;
                        }
                        break;
                    }
                }
                invalidate();
                return true;
            }
            if (!isOperatorMode && event.getAction() == MotionEvent.ACTION_MOVE && selected >= 0) {
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
                if (!isOperatorMode && dragging) {
                    persistProject();
                } else if (!isOperatorMode && selected >= 0 && SystemClock.elapsedRealtime() - downAt >= 550L) {
                    showWidgetContextMenu();
                } else if (!isOperatorMode && selected >= 0 && fbdWireMode) {
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
                            editSelectedWidget();
                        }
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

