package com.bloodsky.hmi;

import org.json.JSONObject;

/** Compact Craft ID8 numeric setter settings, independent of authorization to write. */
final class CraftNumericInputCompactSettings {
    private CraftNumericInputCompactSettings() {}

    static int sourceRegister(JSONObject fields) {
        return OriginalCraftLampConnection.register(fields);
    }

    static String sourceDeviceLabel(JSONObject fields) {
        return OriginalCraftLampConnection.deviceLabel(fields);
    }

    /** Save all native source keys while leaving the password and all permissions untouched. */
    static JSONObject withSettings(JSONObject fields, int register, double min, double max,
                                   int mode, int integerType, int decimals,
                                   boolean systemFont, boolean frame) throws Exception {
        if (register < 0 || register > 65535)
            throw new IllegalArgumentException("Регистр должен быть от 0 до 65535");
        CraftNumericInput.validateRange(min, max);
        if (mode != CraftNumericInput.INTEGER && mode != CraftNumericInput.FLOAT32)
            throw new IllegalArgumentException("Недопустимый тип числового ввода");
        // Original Craft ID8 uses a two-register value in Float32 mode.
        // Never save an impossible terminal Modbus register pair.
        if (mode == CraftNumericInput.FLOAT32 && register == 65535)
            throw new IllegalArgumentException("Для FLOAT32 нужен регистр от 0 до 65534");
        if (integerType != CraftNumericInput.UNSIGNED_16 && integerType != CraftNumericInput.SIGNED_16)
            throw new IllegalArgumentException("Недопустимый тип целого числа");
        if (decimals < 0 || decimals > 8)
            throw new IllegalArgumentException("Дробных разрядов должно быть от 0 до 8");

        JSONObject edited = fields == null ? new JSONObject() : new JSONObject(fields.toString());
        Object oldConnection = edited.opt("craftConnectionInfo");
        JSONObject connection = CraftPropertyRecord.copy(edited, "craftConnectionInfo");
        connection.put("addressRegister", register);
        connection.put("min", min);
        connection.put("max", max);
        edited.put("craftConnectionInfo", oldConnection instanceof String
                ? connection.toString() : connection);

        Object oldOther = edited.opt("craftOther");
        JSONObject other = CraftPropertyRecord.copy(edited, "craftOther");
        other.put("type", mode);
        other.put("typeInt", integerType);
        other.put("countNumAfterPointEditNumber", decimals);
        other.put("fontChange", systemFont);
        other.put("enableFrameEditNumber", frame);
        edited.put("craftOther", oldOther instanceof String ? other.toString() : other);

        boolean targetChanged = register != sourceRegister(fields)
                || mode != (fields == null ? CraftNumericInput.INTEGER
                        : fields.optInt("craftNumericMode", CraftNumericInput.INTEGER))
                || integerType != (fields == null ? CraftNumericInput.UNSIGNED_16
                        : fields.optInt("craftNumericTypeInt", CraftNumericInput.UNSIGNED_16));
        edited.put("craftNumericMin", min);
        edited.put("craftNumericMax", max);
        edited.put("craftNumericMode", mode);
        edited.put("craftNumericTypeInt", integerType);
        edited.put("craftNumericDecimals", decimals);
        edited.put("craftNumericSystemFont", systemFont);
        edited.put("craftNumericFrame", frame);
        // Reconfigure endpoint and explicitly approve write again after changing
        // source register or numeric representation. Never enable a legacy endpoint.
        if (targetChanged) edited.put("craftNumericEnabled", false);
        return edited;
    }
}
