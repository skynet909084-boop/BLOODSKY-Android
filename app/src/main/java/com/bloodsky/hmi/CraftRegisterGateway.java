package com.bloodsky.hmi;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;

/** Source-backed Craft register gateway plan, validation, and transfer state. */
public final class CraftRegisterGateway {
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
        public final int port, unit, address, readFunction;
        public final boolean systemRegister, systemWritable, usb;
        private Binding(String host, int port, int unit, int address, int readFunction,
                        boolean systemRegister, boolean systemWritable,
                        String bluetoothAddress, boolean usb) {
            this.host = host; this.port = port; this.unit = unit; this.address = address;
            this.readFunction = readFunction; this.systemRegister = systemRegister;
            this.systemWritable = systemWritable;
            this.bluetoothAddress = bluetoothAddress; this.usb = usb;
        }
        private static Binding system(int address, boolean writable) {
            return new Binding("", 0, 0, address, 3, true, writable, null, false);
        }
        public boolean sameDevice(Binding other) {
            if (other == null || systemRegister != other.systemRegister) return false;
            if (systemRegister) return true;
            if (usb || other.usb) return usb && other.usb && unit == other.unit;
            if (bluetoothAddress != null || other.bluetoothAddress != null)
                return bluetoothAddress != null && bluetoothAddress.equalsIgnoreCase(other.bluetoothAddress)
                        && unit == other.unit;
            return host.equalsIgnoreCase(other.host) && port == other.port && unit == other.unit;
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
        private boolean baselineSet, previousTrigger;
        private int[] previousSource;
        private String key(Config config) {
            return config.mode + "|" + config.count + "|" + config.triggerBit + "|"
                    + id(config.source) + "|" + id(config.destination) + "|" + id(config.trigger);
        }
        private String id(Binding binding) {
            if (binding == null) return "";
            if (binding.systemRegister) return "system/" + binding.address;
            if (binding.usb) return "usb/" + binding.unit + "/" + binding.address
                    + "/" + binding.readFunction;
            if (binding.bluetoothAddress != null) return "bt/" + binding.bluetoothAddress + "/"
                    + binding.unit + "/" + binding.address + "/" + binding.readFunction;
            return binding.host + ":" + binding.port + "/" + binding.unit + "/"
                    + binding.address + "/" + binding.readFunction;
        }
        private void resetIfChanged(Config config) {
            String nextKey = key(config);
            if (!nextKey.equals(configKey)) {
                configKey = nextKey; baselineSet = false; previousTrigger = false; previousSource = null;
            }
        }
    }

    public static final class Outcome {
        public final boolean transferred;
        public final int count;
        private Outcome(boolean transferred, int count) { this.transferred = transferred; this.count = count; }
    }

    private CraftRegisterGateway() { }

    public static Config validate(boolean enabled, int mode, int count, String sourceText,
                                  String destinationText, String triggerText, int triggerBit) {
        if (!enabled) throw new IllegalArgumentException("Шлюз выключен");
        if (mode < MODE_CONTINUOUS || mode > MODE_ON_CHANGE)
            throw new IllegalArgumentException("Неизвестный режим шлюза");
        int maximum = mode == MODE_ON_CHANGE ? 1 : 99;
        if (count < 1 || count > maximum) throw new IllegalArgumentException(
                mode == MODE_ON_CHANGE ? "Режим «по изменению» передаёт ровно один регистр" : "Количество регистров должно быть 1–99");
        Binding source = parseBinding(sourceText, count, false);
        Binding destination = parseBinding(destinationText, count, true);
        if (source == null || destination == null) throw new IllegalArgumentException("Проверь адреса источника и приёмника");
        if (mode == MODE_RISING_EDGE) {
            Binding trigger = parseBinding(triggerText, 1, false);
            if (trigger == null || trigger.readFunction != 3 || triggerBit < 0 || triggerBit > 15)
                throw new IllegalArgumentException("Для фронта нужен holding-регистр и бит 0–15");
            return new Config(mode, count, triggerBit, source, destination, trigger);
        }
        return new Config(mode, count, 0, source, destination, null);
    }

    /** Imported Craft bindings are device indices, so they cannot be executed until explicitly replaced with TCP endpoints. */
    public static Config from(JSONObject fields) {
        if (fields == null || !fields.optBoolean("craftGatewayEnabled", false)) return null;
        return validate(true, fields.optInt("craftGatewayTransferMode", MODE_CONTINUOUS),
                fields.optInt("craftGatewayCount", 1), fields.optString("craftGatewaySource", ""),
                fields.optString("craftGatewayDestination", ""), fields.optString("craftGatewayTrigger", ""),
                fields.optInt("craftGatewayTriggerBit", 0));
    }

    public static Binding parseBinding(String value, int quantity, boolean destination) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value.trim());
            if ("system".equals(uri.getScheme())) {
                String[] systemPath = uri.getPath().split("/", -1);
                if (!"register".equals(uri.getHost()) || systemPath.length != 2
                        || !systemPath[0].isEmpty() || uri.getRawQuery() != null
                        || uri.getRawFragment() != null || uri.getRawUserInfo() != null) return null;
                int address = Integer.parseInt(systemPath[1]);
                if (address < 0 || (destination
                        ? address < CraftSystemRegisterBank.FIRST_USER_REGISTER
                        : address >= CraftSystemRegisterBank.SIZE)
                        || address >= CraftSystemRegisterBank.SIZE
                        || quantity < 1 || quantity > 99
                        || (long) address + quantity > CraftSystemRegisterBank.SIZE) return null;
                return Binding.system(address, destination);
            }
            if ("bt".equals(uri.getScheme())) {
                BluetoothRtuBinding bluetooth = BluetoothRtuBinding.parse(value.trim(), quantity);
                if (bluetooth == null || (destination && bluetooth.functionCode != 3)) return null;
                return new Binding("", 0, bluetooth.unit, bluetooth.registerAddress,
                        bluetooth.functionCode, false, false, bluetooth.address, false);
            }
            if ("usb".equals(uri.getScheme())) {
                String[] path = uri.getPath().split("/", -1);
                if (uri.getHost() == null || uri.getPort() != -1 || path.length != 2
                        || !path[0].isEmpty() || uri.getRawUserInfo() != null
                        || uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
                int unit = Integer.parseInt(uri.getHost());
                int enteredAddress = Integer.parseInt(path[1]);
                if (unit < 0 || unit > 247 || enteredAddress < 0 || enteredAddress > 65535)
                    return null;
                int function = 3, address = enteredAddress;
                if (enteredAddress >= 30001 && enteredAddress <= 39999) {
                    function = 4; address = enteredAddress - 30001;
                } else if (enteredAddress >= 40001 && enteredAddress <= 49999) {
                    function = 3; address = enteredAddress - 40001;
                }
                if ((destination && function != 3) || quantity < 1 || quantity > 99
                        || (long) address + quantity > 65536L) return null;
                return new Binding("", 0, unit, address, function, false, false, null, true);
            }
            if (!"tcp".equals(uri.getScheme())) return null;
            String[] path = uri.getPath().split("/", -1);
            if (uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535
                    || path.length != 3 || !path[0].isEmpty() || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getRawUserInfo() != null) return null;
            int unit = Integer.parseInt(path[1]);
            int enteredAddress = Integer.parseInt(path[2]);
            if (unit < 0 || unit > 255 || enteredAddress < 0 || enteredAddress > 65535) return null;
            int function = 3, address = enteredAddress;
            if (enteredAddress >= 30001 && enteredAddress <= 39999) {
                function = 4; address = enteredAddress - 30001;
            } else if (enteredAddress >= 40001 && enteredAddress <= 49999) {
                function = 3; address = enteredAddress - 40001;
            }
            if ((destination && function != 3) || quantity < 1 || quantity > (destination ? 99 : 99)
                    || (long)address + quantity > 65536L) return null;
            return new Binding(uri.getHost(), uri.getPort(), unit, address, function,
                    false, false, null, false);
        } catch (Exception ignored) { return null; }
    }

    /** Reads Craft's local system bank for a source/trigger endpoint; never writes it. */
    public static int[] readSystemSource(Binding binding, int quantity,
                                         CraftSystemRegisterBank projectBank) {
        if (binding == null || !binding.systemRegister || quantity < 1 || quantity > 99
                || (long) binding.address + quantity > CraftSystemRegisterBank.SIZE)
            throw new IllegalArgumentException("A bounded Craft system-register source is required");
        int[] values = new int[quantity];
        int serviceCount = Math.min(quantity,
                Math.max(0, CraftSystemRegisterBank.FIRST_USER_REGISTER - binding.address));
        for (int i = 0; i < serviceCount; i++)
            values[i] = CraftSystemRegisterAccess.readInt(binding.address + i, projectBank) & 0xffff;
        if (serviceCount < quantity) {
            int userAddress = binding.address + serviceCount;
            if (projectBank == null) throw new IllegalArgumentException("Active project register bank required");
            int[] userWords = projectBank.readWords(userAddress, quantity - serviceCount);
            System.arraycopy(userWords, 0, values, serviceCount, userWords.length);
        }
        return values;
    }

    /** Writes only the project's user-register bank; Craft service registers 0..9 stay protected. */
    public static void writeSystemDestination(Binding binding, int[] values,
                                               CraftSystemRegisterBank projectBank) {
        if (binding == null || !binding.systemRegister || !binding.systemWritable
                || values == null || values.length < 1 || values.length > 99
                || (long) binding.address + values.length > CraftSystemRegisterBank.SIZE)
            throw new IllegalArgumentException("A bounded Craft user-register destination is required");
        if (projectBank == null) throw new IllegalArgumentException("Active project register bank required");
        projectBank.writeWords(binding.address, values.clone());
    }

    public static Outcome transfer(Config config, State state, Transport transport) throws IOException {
        if (config == null || state == null || transport == null) throw new IllegalArgumentException("Gateway config/state/transport required");
        state.resetIfChanged(config);
        boolean risingEdge = false;
        if (config.mode == MODE_RISING_EDGE) {
            int[] trigger = transport.read(config.trigger, 1);
            if (trigger == null || trigger.length != 1 || trigger[0] < 0 || trigger[0] > 65535)
                throw new IOException("Некорректный ответ триггера шлюза");
            boolean active = ((trigger[0] >>> config.triggerBit) & 1) != 0;
            risingEdge = state.baselineSet && !state.previousTrigger && active;
            state.baselineSet = true;
            if (!risingEdge) {
                state.previousTrigger = active;
                return new Outcome(false, 0);
            }
        }
        int[] values = transport.read(config.source, config.count);
        if (values == null || values.length != config.count)
            throw new IOException("Количество прочитанных регистров шлюза не совпало с настройкой");
        for (int value : values) if (value < 0 || value > 65535) throw new IOException("Значение шлюза выходит за 16 бит");
        if (config.mode == MODE_ON_CHANGE) {
            boolean changed = state.baselineSet && !Arrays.equals(state.previousSource, values);
            if (!state.baselineSet) {
                state.baselineSet = true;
                state.previousSource = values.clone();
                return new Outcome(false, 0);
            }
            if (!changed) return new Outcome(false, 0);
        }
        transport.write(config.destination, values.clone());
        // Keep the last successfully transferred state. If the read or write
        // fails, the same changed value or still-high trigger must be retried.
        if (config.mode == MODE_ON_CHANGE) state.previousSource = values.clone();
        if (risingEdge) state.previousTrigger = true;
        return new Outcome(true, values.length);
    }
}
