package com.bloodsky.hmi;

/** Session gate for project-provided USB and Bluetooth RTU endpoints. */
public final class ModbusRtuOperatorPolicy {
    private ModbusRtuOperatorPolicy() { }

    public static boolean isRtuBinding(String binding) {
        if (binding == null) return false;
        String value = binding.trim();
        return value.startsWith("usb://") || value.startsWith("bt://");
    }

    public static boolean shouldPrompt(boolean operatorMode, boolean hasRtuBinding,
                                       boolean consentResolved) {
        return operatorMode && hasRtuBinding && !consentResolved;
    }

    public static boolean mayPollRtu(String binding, boolean operatorMode,
                                     boolean consentGranted) {
        return isRtuBinding(binding) && operatorMode && consentGranted;
    }

    /**
     * Craft register gateways carry their endpoints outside Widget.connectionInfo.
     * Include those configured addresses in the same per-operator-session RTU prompt.
     */
    public static boolean hasGatewayRtuBinding(boolean enabled, String source,
            String destination, String trigger) {
        return enabled && (isRtuBinding(source) || isRtuBinding(destination)
                || isRtuBinding(trigger));
    }

    /** Bluetooth gateway reads and writes need both session consent and Android permission. */
    public static boolean mayAccessBluetoothGateway(boolean operatorMode,
            boolean consentGranted, boolean bluetoothConnectGranted) {
        return operatorMode && consentGranted && bluetoothConnectGranted;
    }
}
