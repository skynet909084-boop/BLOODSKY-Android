package com.bloodsky.hmi;

import android.content.Context;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Operator-side Modbus reads for widget bindings.
 * Must be called only from a background executor (never the UI thread).
 *
 * <p>Supported schemes:
 * <ul>
 *   <li>{@code tcp://host:port/unit/register}</li>
 *   <li>{@code usb://unit/register} — requires an explicitly selected USB device name</li>
 *   <li>{@code bt://AA:BB:CC:DD:EE:FF/unit/register} — paired SPP only</li>
 * </ul>
 * Physical device acceptance remains an open gate.
 */
public final class ModbusPollGateway implements Closeable {
    private final Context appContext;
    private final ConcurrentHashMap<String, BluetoothModbusConnection> bluetoothByMac = new ConcurrentHashMap<>();
    private volatile String selectedUsbDeviceName;
    private volatile UsbSerialSettings usbSettings =
            new UsbSerialSettings(9600, 8, 1, UsbSerialSettings.Parity.NONE);
    private volatile UsbSerialModbusConnection usbConnection;
    private final Object usbLock = new Object();

    public ModbusPollGateway(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required");
        this.appContext = context.getApplicationContext();
    }

    public void setSelectedUsbDevice(String deviceName) {
        synchronized (usbLock) {
            if (deviceName == null || deviceName.trim().isEmpty()) {
                selectedUsbDeviceName = null;
            } else {
                selectedUsbDeviceName = deviceName.trim();
            }
            closeUsbQuietly();
        }
    }

    public String getSelectedUsbDevice() {
        return selectedUsbDeviceName;
    }

    public void setUsbSettings(UsbSerialSettings settings) {
        if (settings == null) throw new IllegalArgumentException("Serial settings are required");
        synchronized (usbLock) {
            this.usbSettings = settings;
            closeUsbQuietly();
        }
    }

    public List<UsbSerialConnector.DeviceOption> listUsbDevices() {
        return new UsbSerialConnector(appContext).listDevices();
    }

    /** Returns the first register value as a decimal string, or throws. */
    public String readFirstValue(String binding) throws IOException {
        Endpoint endpoint = Endpoint.parse(binding);
        if (endpoint == null) throw new IOException("Неверный binding: " + binding);
        int[] values = read(endpoint);
        if (values == null || values.length < 1) throw new IOException("Пустой ответ регистра");
        return Integer.toString(values[0]);
    }

    public int[] read(Endpoint endpoint) throws IOException {
        if (endpoint == null) throw new IllegalArgumentException("Endpoint is required");
        switch (endpoint.scheme) {
            case TCP:
                return new ModbusTcpClient(endpoint.host, endpoint.port)
                        .read(endpoint.unit, endpoint.address, endpoint.quantity, endpoint.functionCode);
            case USB:
                return readUsb(endpoint);
            case BT:
                return readBluetooth(endpoint);
            default:
                throw new IOException("Unsupported scheme");
        }
    }

    private int[] readUsb(Endpoint endpoint) throws IOException {
        UsbSerialModbusConnection connection = ensureUsb();
        try {
            return connection.read(endpoint.unit, endpoint.functionCode, endpoint.address, endpoint.quantity);
        } catch (IOException failure) {
            synchronized (usbLock) { closeUsbQuietly(); }
            throw failure;
        }
    }

    private UsbSerialModbusConnection ensureUsb() throws IOException {
        synchronized (usbLock) {
            if (selectedUsbDeviceName == null || selectedUsbDeviceName.isEmpty()) {
                throw new IOException("Сначала выберите USB serial-адаптер");
            }
            if (usbConnection != null && usbConnection.isOpen()) return usbConnection;
            closeUsbQuietly();
            try {
                Future<UsbSerialModbusConnection> future = new UsbSerialConnector(appContext)
                        .connect(selectedUsbDeviceName, usbSettings, ModbusRtuClient.DEFAULT_TIMEOUT_MS);
                usbConnection = future.get(120, TimeUnit.SECONDS);
                return usbConnection;
            } catch (Exception failure) {
                closeUsbQuietly();
                if (failure instanceof IOException) throw (IOException) failure;
                throw new IOException("Не удалось открыть USB serial", failure);
            }
        }
    }

    private int[] readBluetooth(Endpoint endpoint) throws IOException {
        String mac = endpoint.host;
        BluetoothModbusConnection connection = bluetoothByMac.get(mac);
        if (connection == null || !connection.isOpen()) {
            if (connection != null) {
                try { connection.close(); } catch (IOException ignored) { }
                bluetoothByMac.remove(mac, connection);
            }
            connection = BluetoothModbusConnection.connect(appContext, mac, ModbusRtuClient.DEFAULT_TIMEOUT_MS);
            bluetoothByMac.put(mac, connection);
        }
        try {
            return connection.read(endpoint.unit, endpoint.functionCode, endpoint.address, endpoint.quantity);
        } catch (IOException failure) {
            bluetoothByMac.remove(mac, connection);
            try { connection.close(); } catch (IOException ignored) { }
            throw failure;
        }
    }

    private void closeUsbQuietly() {
        UsbSerialModbusConnection connection = usbConnection;
        usbConnection = null;
        if (connection != null) {
            try { connection.close(); } catch (IOException ignored) { }
        }
    }

    @Override
    public void close() {
        synchronized (usbLock) { closeUsbQuietly(); }
        for (BluetoothModbusConnection connection : bluetoothByMac.values()) {
            try { connection.close(); } catch (IOException ignored) { }
        }
        bluetoothByMac.clear();
    }

    /** Parsed widget binding for operator polling. */
    public static final class Endpoint {
        public enum Scheme { TCP, USB, BT }

        public final Scheme scheme;
        /** TCP host or Bluetooth MAC (uppercase). Null for USB. */
        public final String host;
        public final int port;
        public final int unit;
        public final int address;
        public final int functionCode;
        public final int quantity;

        private Endpoint(Scheme scheme, String host, int port, int unit, int address,
                         int functionCode, int quantity) {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
            this.unit = unit;
            this.address = address;
            this.functionCode = functionCode;
            this.quantity = quantity;
        }

        public String registerKey() {
            switch (scheme) {
                case TCP:
                    return "tcp/" + host.toLowerCase(Locale.ROOT) + ":" + port + "/" + unit
                            + "/" + address + "/FC" + functionCode;
                case USB:
                    return "usb/" + unit + "/" + address + "/FC" + functionCode;
                case BT:
                    return "bt/" + host + "/" + unit + "/" + address + "/FC" + functionCode;
                default:
                    return "unknown";
            }
        }

        public static Endpoint parse(String binding) {
            if (binding == null) return null;
            String value = binding.trim();
            if (value.startsWith("tcp://")) return parseTcp(value);
            if (value.startsWith("usb://")) {
                UsbSerialBinding usb = UsbSerialBinding.parse(value, 1);
                if (usb == null) return null;
                return new Endpoint(Scheme.USB, null, 0, usb.unit, usb.address, usb.functionCode, 1);
            }
            if (value.startsWith("bt://")) {
                BluetoothRtuBinding bt = BluetoothRtuBinding.parse(value, 1);
                if (bt == null) return null;
                return new Endpoint(Scheme.BT, bt.address, 0, bt.unit, bt.registerAddress,
                        bt.functionCode, 1);
            }
            return null;
        }

        private static Endpoint parseTcp(String value) {
            try {
                java.net.URI uri = java.net.URI.create(value.trim());
                if (!"tcp".equals(uri.getScheme())) return null;
                String[] path = uri.getPath() == null ? new String[0] : uri.getPath().split("/", -1);
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
                return new Endpoint(Scheme.TCP, uri.getHost(), uri.getPort(), unit, address, functionCode, 1);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
