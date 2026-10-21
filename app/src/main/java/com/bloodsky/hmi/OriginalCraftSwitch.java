package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;

/** Source-backed properties for Craft CustomComponents.y (component ID 1). */
public final class OriginalCraftSwitch {
    public static final String TYPE = "Переключатель Craft";
    private OriginalCraftSwitch() { }

    public static boolean supports(String type) { return TYPE.equals(type); }

    public static int imagePosition(JSONObject widgetFields) {
        JSONObject other = other(widgetFields);
        int value = other.optInt("imagePosition", 1);
        return Math.max(1, Math.min(8, value));
    }

    public static boolean visibleInWork(JSONObject widgetFields) {
        return other(widgetFields).optBoolean("visibleInWork", true);
    }

    public static String drawableName(JSONObject widgetFields, boolean on) {
        return "kc_switch" + imagePosition(widgetFields) + (on ? "_on" : "_off");
    }

    /** FC22 masks used after the operator explicitly remaps the imported device endpoint. */
    public static int[] writeMasks(int bit, boolean on) {
        return CraftMomentaryButton.masks(bit, on);
    }

    public static JSONObject withImagePosition(JSONObject widgetFields, int imagePosition)
            throws JSONException {
        if (imagePosition < 1 || imagePosition > 8)
            throw new IllegalArgumentException("Положение изображения должно быть от 1 до 8");
        JSONObject result = new JSONObject(widgetFields == null ? "{}" : widgetFields.toString());
        JSONObject other = copyOther(result);
        other.put("imagePosition", imagePosition);
        other.put("visibleInWork", visibleInWork(result));
        result.put("craftOther", other);
        return result;
    }

    public static JSONObject withVisibility(JSONObject widgetFields, boolean visible)
            throws JSONException {
        JSONObject result = new JSONObject(widgetFields == null ? "{}" : widgetFields.toString());
        JSONObject other = copyOther(result);
        other.put("imagePosition", imagePosition(result));
        other.put("visibleInWork", visible);
        result.put("craftOther", other);
        return result;
    }

    private static JSONObject other(JSONObject fields) {
        if (fields == null) return new JSONObject();
        Object value = fields.opt("craftOther");
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof String) {
            try { return new JSONObject((String) value); } catch (JSONException ignored) { }
        }
        return new JSONObject();
    }

    private static JSONObject copyOther(JSONObject fields) throws JSONException {
        Object value = fields.opt("craftOther");
        if (value instanceof JSONObject) return new JSONObject(value.toString());
        if (value instanceof String) {
            try { return new JSONObject((String) value); } catch (JSONException ignored) { }
        }
        return new JSONObject();
    }
}
