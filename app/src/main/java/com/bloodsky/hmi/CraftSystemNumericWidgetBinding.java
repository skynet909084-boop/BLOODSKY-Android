package com.bloodsky.hmi;

import org.json.JSONObject;

/** Source-backed local system-register path for imported Craft numeric widgets. */
public final class CraftSystemNumericWidgetBinding {
    private static final int COMPONENT_ID = 8;
    private static final int GLOBAL_SOURCE_SLOT = 100;

    private CraftSystemNumericWidgetBinding() { }

    /** True for the imported Craft system slot, including modes we deliberately keep inactive. */
    public static boolean isGlobalSystemBinding(JSONObject fields) {
        if (fields == null || fields.optInt("craftComponentId", -1) != COMPONENT_ID
                || !"Числовой ввод Craft".equals(fields.optString("type", ""))) return false;
        JSONObject resolution = fields.optJSONObject("craftBindingResolution");
        return resolution != null && "global".equals(resolution.optString("kind", ""))
                && resolution.optInt("sourceSlot", -1) == GLOBAL_SOURCE_SLOT;
    }

    /** Craft ID8 integer mode reads one 16-bit system word. */
    public static String readIntegerState(JSONObject fields, CraftSystemRegisterBank bank) {
        if (!isSupportedInteger(fields) || bank == null) return null;
        int address = address(fields);
        if (!CraftSystemRegisterAccess.isValidWordAddress(address)) return null;
        int value = CraftSystemRegisterAccess.readInt(address, bank) & 0xffff;
        if (fields.optInt("craftNumericTypeInt", CraftNumericInput.UNSIGNED_16)
                == CraftNumericInput.SIGNED_16 && value >= 0x8000) value -= 0x10000;
        return Integer.toString(value);
    }

    /** Craft ID8 Float32 mode reads two consecutive words in source-confirmed big-endian order. */
    public static String readFloatState(JSONObject fields, CraftSystemRegisterBank bank) {
        if (!isSupportedFloat(fields) || bank == null) return null;
        int address = address(fields);
        if (address < 0 || address >= CraftSystemRegisterBank.SIZE - 1) return null;
        int first = CraftSystemRegisterAccess.readInt(address, bank);
        int second = CraftSystemRegisterAccess.readInt(address + 1, bank);
        float value = FbdCraftRegisterCodec.floatValue(first & 0xffff, second & 0xffff);
        return CraftNumericInput.truncateFloatDisplay(value, fields);
    }

    /** State display for either source-confirmed local ID8 mode. */
    public static String readState(JSONObject fields, CraftSystemRegisterBank bank) {
        return fields != null && fields.optInt("craftNumericMode", CraftNumericInput.INTEGER)
                == CraftNumericInput.FLOAT32 ? readFloatState(fields, bank) : readIntegerState(fields, bank);
    }

    /** Only opt-in integer writes to project-owned registers are enabled. */
    public static boolean canWriteInteger(JSONObject fields) {
        if (!isSupportedInteger(fields) || !fields.optBoolean("craftNumericEnabled", false)) return false;
        int address = address(fields);
        return address >= CraftSystemRegisterBank.FIRST_USER_REGISTER
                && address < CraftSystemRegisterBank.SIZE;
    }

    /** Only explicitly enabled integer or Float32 writes to project user words are enabled. */
    public static boolean canWrite(JSONObject fields) {
        if (fields == null || !fields.optBoolean("craftNumericEnabled", false)) return false;
        if (isSupportedInteger(fields)) return canWriteInteger(fields);
        if (!isSupportedFloat(fields)) return false;
        int address = address(fields);
        return address >= CraftSystemRegisterBank.FIRST_USER_REGISTER
                && address < CraftSystemRegisterBank.SIZE - 1;
    }

    public static boolean writeInteger(JSONObject fields, double value,
                                       CraftSystemRegisterBank bank) {
        if (!canWriteInteger(fields) || bank == null) return false;
        int word = CraftNumericInput.integerRegister(value, fields);
        return CraftSystemRegisterAccess.writeInt(address(fields), word, bank);
    }

    public static boolean writeValue(JSONObject fields, double value,
                                     CraftSystemRegisterBank bank) {
        if (!canWrite(fields) || bank == null) return false;
        int address = address(fields);
        if (isSupportedInteger(fields)) {
            return writeInteger(fields, value, bank);
        }
        float checked = (float) CraftNumericInput.parseCommit(Double.toString(value), fields);
        bank.writeFloat32(address, checked, false, false);
        return true;
    }

    private static boolean isSupportedInteger(JSONObject fields) {
        return isGlobalSystemBinding(fields)
                && fields.optInt("craftNumericMode", CraftNumericInput.INTEGER) == CraftNumericInput.INTEGER;
    }

    private static boolean isSupportedFloat(JSONObject fields) {
        return isGlobalSystemBinding(fields)
                && fields.optInt("craftNumericMode", CraftNumericInput.INTEGER) == CraftNumericInput.FLOAT32;
    }

    private static int address(JSONObject fields) {
        JSONObject connection = objectValue(fields.opt("craftConnectionInfo"));
        if (connection == null) return -1;
        Object raw = connection.opt("addressRegister");
        if (raw instanceof Number) return ((Number) raw).intValue();
        if (raw instanceof String) {
            try { return Integer.parseInt(((String) raw).trim()); }
            catch (NumberFormatException ignored) { return -1; }
        }
        return -1;
    }

    private static JSONObject objectValue(Object raw) {
        if (raw instanceof JSONObject) return (JSONObject) raw;
        if (!(raw instanceof String)) return null;
        try { return new JSONObject(((String) raw).trim()); }
        catch (Exception ignored) { return null; }
    }
}
