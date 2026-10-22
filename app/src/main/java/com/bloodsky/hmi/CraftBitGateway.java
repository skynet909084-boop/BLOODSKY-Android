package com.bloodsky.hmi;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;

/** Fail-closed TCP and explicitly mapped Bluetooth RTU implementation of Craft component ID 22. */
public final class CraftBitGateway {
    public static final int MODE_CONTINUOUS = 0;
    public static final int MODE_RISING_EDGE = 1;
    public static final int MODE_ON_CHANGE = 2;

    public interface Transport {
        int[] read(Binding binding, int quantity) throws IOException;
        void write(Binding binding, int[] values) throws IOException;
    }

    public static final class Binding {
        public final String host;
        public final String bluetoothAddress;
        public final int port, unit, address, function;
        private Binding(String host, int port, int unit, int address, int function, String bluetoothAddress) {
            this.host = host; this.port = port; this.unit = unit; this.address = address;
            this.function = function; this.bluetoothAddress = bluetoothAddress;
        }
    }

    public static final class Config {
        public final int mode, count, triggerBit;
        public final Binding source, destination, trigger;
        private Config(int mode, int count, int triggerBit, Binding source, Binding destination, Binding trigger) {
            this.mode = mode; this.count = count; this.triggerBit = triggerBit;
            this.source = source; this.destination = destination; this.trigger = trigger;
        }
    }

    public static final class State {
        private String configKey;
        private boolean triggerBaseline, previousTrigger, sourceBaseline;
        private int[] previousSource;
        private void resetIfChanged(Config c) {
            String key = c.mode + "|" + c.count + "|" + c.triggerBit + "|"
                    + id(c.source) + "|" + id(c.destination) + "|" + id(c.trigger);
            if (!key.equals(configKey)) {
                configKey = key; triggerBaseline = false; previousTrigger = false;
                sourceBaseline = false; previousSource = null;
            }
        }
        private static String id(Binding b) {
            return b == null ? "" : (b.bluetoothAddress == null ? b.host + ":" + b.port
                    : "bt/" + b.bluetoothAddress) + "/" + b.unit + "/" + b.address + "/" + b.function;
        }
    }

    public static final class Outcome {
        public final boolean transferred;
        public final int count;
        private Outcome(boolean transferred, int count) { this.transferred = transferred; this.count = count; }
    }

    private CraftBitGateway() { }

    public static Config validate(boolean enabled, int mode, int count, int sourceFunction,
                                  String sourceText, String destinationText, String triggerText, int triggerBit) {
        if (!enabled) throw new IllegalArgumentException("Битовый шлюз выключен");
        if (mode < MODE_CONTINUOUS || mode > MODE_ON_CHANGE) throw new IllegalArgumentException("Неизвестный режим шлюза");
        int maximum = mode == MODE_ON_CHANGE ? 1 : 16;
        if (count < 1 || count > maximum) throw new IllegalArgumentException(
                mode == MODE_ON_CHANGE ? "Режим «по изменению» передаёт ровно один бит" : "Количество бит должно быть 1–16");
        if (sourceFunction != 1 && sourceFunction != 2) throw new IllegalArgumentException("Источник должен быть FC01 Coils или FC02 Discrete Inputs");
        Binding source = parse(sourceText, sourceFunction, count);
        Binding destination = parse(destinationText, 1, count);
        if (source == null || destination == null) throw new IllegalArgumentException("Проверь TCP/Bluetooth адрес источника и приёмника");
        if ((long) source.address + count > 65536L || (long) destination.address + count > 65536L)
            throw new IllegalArgumentException("Диапазон битов выходит за адресное пространство Modbus");
        Binding trigger = null;
        if (mode == MODE_RISING_EDGE) {
            CraftRegisterGateway.Binding register = CraftRegisterGateway.parseBinding(triggerText, 1, false);
            if (register == null || register.systemRegister || register.readFunction != 3
                    || triggerBit < 0 || triggerBit > 15)
                throw new IllegalArgumentException("Для фронта нужен holding-регистр и бит 0–15");
            trigger = new Binding(register.host, register.port, register.unit, register.address, 3,
                    register.bluetoothAddress);
        }
        return new Config(mode, count, triggerBit, source, destination, trigger);
    }

    public static Config from(JSONObject fields) {
        if (fields == null || !fields.optBoolean("craftBitGatewayEnabled", false)) return null;
        return validate(true, fields.optInt("craftBitGatewayTransferMode", MODE_CONTINUOUS),
                fields.optInt("craftBitGatewayCount", 1), fields.optInt("craftBitGatewaySourceFunction", 1),
                fields.optString("craftBitGatewaySource", ""), fields.optString("craftBitGatewayDestination", ""),
                fields.optString("craftBitGatewayTrigger", ""), fields.optInt("craftBitGatewayTriggerBit", 0));
    }

    /** Endpoints use TCP or bt://MAC/unit/zero-based-address; source function is configured separately. */
    public static Binding parse(String value, int function) {
        return parse(value, function, 1);
    }

    public static Binding parse(String value, int function, int quantity) {
        if (value == null || (function != 1 && function != 2)) return null;
        try {
            URI uri = URI.create(value.trim());
            if ("bt".equals(uri.getScheme())) {
                BluetoothRtuBinding bluetooth = BluetoothRtuBinding.parseBits(value.trim(), function, quantity);
                return bluetooth == null ? null : new Binding("", 0, bluetooth.unit,
                        bluetooth.registerAddress, function, bluetooth.address);
            }
            String[] path = uri.getPath().split("/", -1);
            if (!"tcp".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535
                    || path.length != 3 || !path[0].isEmpty() || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getRawUserInfo() != null) return null;
            int unit = Integer.parseInt(path[1]), address = Integer.parseInt(path[2]);
            if (unit < 0 || unit > 255 || address < 0 || address > 65535) return null;
            return new Binding(uri.getHost(), uri.getPort(), unit, address, function, null);
        } catch (Exception ignored) { return null; }
    }

    public static Outcome transfer(Config c, State state, Transport transport) throws IOException {
        if (c == null || state == null || transport == null) throw new IllegalArgumentException("Gateway config/state/transport required");
        state.resetIfChanged(c);
        if (c.mode == MODE_RISING_EDGE) {
            int[] trigger = transport.read(c.trigger, 1);
            if (trigger == null || trigger.length != 1 || trigger[0] < 0 || trigger[0] > 65535)
                throw new IOException("Некорректный ответ триггера битового шлюза");
            boolean active = ((trigger[0] >>> c.triggerBit) & 1) != 0;
            boolean rising = state.triggerBaseline && !state.previousTrigger && active;
            state.triggerBaseline = true; state.previousTrigger = active;
            if (!rising) return new Outcome(false, 0);
        }
        int[] bits = transport.read(c.source, c.count);
        if (bits == null || bits.length != c.count) throw new IOException("Число битов в ответе шлюза не совпадает с настройкой");
        for (int bit : bits) if (bit != 0 && bit != 1) throw new IOException("Источник вернул значение бита вне 0/1");
        if (c.mode == MODE_ON_CHANGE) {
            boolean changed = state.sourceBaseline && !Arrays.equals(state.previousSource, bits);
            state.sourceBaseline = true; state.previousSource = bits.clone();
            if (!changed) return new Outcome(false, 0);
        }
        transport.write(c.destination, bits.clone());
        return new Outcome(true, bits.length);
    }
}
