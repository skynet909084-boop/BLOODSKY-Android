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
}
