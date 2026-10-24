package com.bloodsky.hmi;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;

/** Explicitly mapped TCP, RTU and local-register implementation of Craft component ID 22. */
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
        public final boolean usb, systemRegister, systemWritable;
        private Binding(String host, int port, int unit, int address, int function,
                        String bluetoothAddress, boolean usb, boolean systemRegister,
                        boolean systemWritable) {
            this.host = host; this.port = port; this.unit = unit; this.address = address;
            this.function = function; this.bluetoothAddress = bluetoothAddress;
            this.usb = usb; this.systemRegister = systemRegister; this.systemWritable = systemWritable;
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
            if (b == null) return "";
            if (b.systemRegister) return "system/" + b.address + "/" + b.systemWritable;
            if (b.usb) return "usb/" + b.unit + "/" + b.address + "/" + b.function;
            return (b.bluetoothAddress == null ? b.host + ":" + b.port
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
        Binding source = parse(sourceText, sourceFunction, count, false);
        Binding destination = parse(destinationText, 1, count, true);
        if (source == null || destination == null) throw new IllegalArgumentException("Проверь endpoint источника и приёмника");
        if ((long) source.address + count > 65536L || (long) destination.address + count > 65536L)
            throw new IllegalArgumentException("Диапазон битов выходит за адресное пространство Modbus");
        Binding trigger = null;
        if (mode == MODE_RISING_EDGE) {
            CraftRegisterGateway.Binding register = CraftRegisterGateway.parseBinding(triggerText, 1, false);
            if (register == null || register.systemRegister || register.readFunction != 3
                    || triggerBit < 0 || triggerBit > 15)
                throw new IllegalArgumentException("Для фронта нужен holding-регистр и бит 0–15");
            trigger = new Binding(register.host, register.port, register.unit, register.address, 3,
                    register.bluetoothAddress, register.usb, false, false);
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

    /** Endpoints use TCP, bt://MAC/unit/address, usb://unit/address or a local register.bit URI. */
    public static Binding parse(String value, int function) {
        return parse(value, function, 1);
    }

    public static Binding parse(String value, int function, int quantity) {
        return parse(value, function, quantity, false);
    }

    private static Binding parse(String value, int function, int quantity, boolean destination) {
        if (value == null || (function != 1 && function != 2)) return null;
        try {
            URI uri = URI.create(value.trim());
            if ("system".equals(uri.getScheme())) {
                String[] path = uri.getPath().split("/", -1);
                if (!"register".equals(uri.getHost()) || path.length != 4
                        || !path[0].isEmpty() || !"bit".equals(path[2])
                        || uri.getPort() != -1 || uri.getRawUserInfo() != null
                        || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || quantity < 1 || quantity > 16) return null;
                int register = Integer.parseInt(path[1]);
                int bit = Integer.parseInt(path[3]);
                if (!CraftSystemRegisterBank.isUserRegister(register) || bit < 0 || bit > 15)
                    return null;
                int startBit = register * 16 + bit;
                if ((long) startBit + quantity > (long) CraftSystemRegisterBank.SIZE * 16)
                    return null;
                return new Binding("", 0, 0, startBit, function, null,
                        false, true, destination);
            }
            if ("bt".equals(uri.getScheme())) {
                BluetoothRtuBinding bluetooth = BluetoothRtuBinding.parseBits(value.trim(), function, quantity);
                return bluetooth == null ? null : new Binding("", 0, bluetooth.unit,
                        bluetooth.registerAddress, function, bluetooth.address, false, false, false);
            }
            if ("usb".equals(uri.getScheme())) {
                String[] path = uri.getPath().split("/", -1);
                if (uri.getHost() == null || uri.getPort() != -1 || path.length != 2
                        || !path[0].isEmpty() || uri.getRawUserInfo() != null
                        || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || quantity < 1 || quantity > 16) return null;
                int unit = Integer.parseInt(uri.getHost());
                int address = Integer.parseInt(path[1]);
                if (unit < 1 || unit > 247 || address < 0 || address > 65535
                        || (long) address + quantity > 65536L) return null;
                return new Binding("", 0, unit, address, function, null, true, false, false);
            }
            String[] path = uri.getPath().split("/", -1);
            if (!"tcp".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535
                    || path.length != 3 || !path[0].isEmpty() || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getRawUserInfo() != null) return null;
            int unit = Integer.parseInt(path[1]), address = Integer.parseInt(path[2]);
            if (unit < 0 || unit > 255 || address < 0 || address > 65535
                    || quantity < 1 || quantity > 16
                    || (long) address + quantity > 65536L) return null;
            return new Binding(uri.getHost(), uri.getPort(), unit, address, function,
                    null, false, false, false);
        } catch (Exception ignored) { return null; }
    }

    /** Reads a contiguous Craft user-register bit range in the same order as Modbus FC01/02. */
    public static int[] readSystemSource(Binding binding, int quantity,
                                         CraftSystemRegisterBank projectBank) {
        if (binding == null || !binding.systemRegister || binding.systemWritable
                || quantity < 1 || quantity > 16
                || (long) binding.address + quantity > (long) CraftSystemRegisterBank.SIZE * 16)
            throw new IllegalArgumentException("A bounded Craft system-bit source is required");
        if (projectBank == null) throw new IllegalArgumentException("Active project register bank required");
        boolean[] values = projectBank.readCoils(binding.address, quantity);
        int[] result = new int[quantity];
        for (int i = 0; i < quantity; i++) result[i] = values[i] ? 1 : 0;
        return result;
    }

    /** Writes one validated range atomically; Craft service registers 0..9 cannot be addressed. */
    public static void writeSystemDestination(Binding binding, int[] values,
                                               CraftSystemRegisterBank projectBank) {
        if (binding == null || !binding.systemRegister || !binding.systemWritable
                || values == null || values.length < 1 || values.length > 16
                || (long) binding.address + values.length > (long) CraftSystemRegisterBank.SIZE * 16)
            throw new IllegalArgumentException("A bounded Craft system-bit destination is required");
        if (projectBank == null) throw new IllegalArgumentException("Active project register bank required");
        boolean[] bits = new boolean[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] != 0 && values[i] != 1)
                throw new IllegalArgumentException("Gateway bit must be 0 or 1");
            bits[i] = values[i] == 1;
        }
        projectBank.writeCoils(binding.address, bits);
    }

    public static Outcome transfer(Config c, State state, Transport transport) throws IOException {
        if (c == null || state == null || transport == null) throw new IllegalArgumentException("Gateway config/state/transport required");
        state.resetIfChanged(c);
        boolean risingEdge = false;
        boolean activeTrigger = false;
        if (c.mode == MODE_RISING_EDGE) {
            int[] trigger = transport.read(c.trigger, 1);
            if (trigger == null || trigger.length != 1 || trigger[0] < 0 || trigger[0] > 65535)
                throw new IOException("Некорректный ответ триггера битового шлюза");
            activeTrigger = ((trigger[0] >>> c.triggerBit) & 1) != 0;
            risingEdge = state.triggerBaseline && !state.previousTrigger && activeTrigger;
            if (!risingEdge) {
                state.triggerBaseline = true;
                state.previousTrigger = activeTrigger;
                return new Outcome(false, 0);
            }
        }
        int[] bits = transport.read(c.source, c.count);
        if (bits == null || bits.length != c.count) throw new IOException("Число битов в ответе шлюза не совпадает с настройкой");
        for (int bit : bits) if (bit != 0 && bit != 1) throw new IOException("Источник вернул значение бита вне 0/1");
        if (c.mode == MODE_ON_CHANGE) {
            boolean changed = state.sourceBaseline && !Arrays.equals(state.previousSource, bits);
            if (!state.sourceBaseline) {
                state.sourceBaseline = true;
                state.previousSource = bits.clone();
                return new Outcome(false, 0);
            }
            if (!changed) return new Outcome(false, 0);
        }
        transport.write(c.destination, bits.clone());
        if (c.mode == MODE_RISING_EDGE) {
            state.triggerBaseline = true;
            state.previousTrigger = activeTrigger;
        }
        if (c.mode == MODE_ON_CHANGE) {
            state.sourceBaseline = true;
            state.previousSource = bits.clone();
        }
        return new Outcome(true, bits.length);
    }
}
