package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Bounded source-field mapping for Craft ID26. URLs are stored, never loaded here. */
public final class CraftWebViewLinks {
    public static final int LINK_COUNT = 16;
    public static final int MAX_LINK_LENGTH = 2048;

    private CraftWebViewLinks() {}

    public static String[] read(JSONObject sourceOther) {
        String[] links = new String[LINK_COUNT];
        for (int i = 0; i < links.length; i++) {
            String value = sourceOther == null ? "" : sourceOther.optString("link" + i, "");
            links[i] = bounded(value);
        }
        return links;
    }

    public static JSONObject write(JSONObject sourceOther, String[] links) throws JSONException {
        if (links == null || links.length != LINK_COUNT)
            throw new IllegalArgumentException("Craft ID26 requires exactly 16 link fields");
        JSONObject result = sourceOther == null ? new JSONObject() : new JSONObject(sourceOther.toString());
        for (int i = 0; i < links.length; i++) {
            String value = links[i] == null ? "" : links[i];
            if (value.length() > MAX_LINK_LENGTH)
                throw new IllegalArgumentException("Craft link " + i + " exceeds " + MAX_LINK_LENGTH + " characters");
            result.put("link" + i, value);
        }
        return result;
    }

    public static JSONArray toJsonArray(String[] links) {
        JSONArray result = new JSONArray();
        if (links != null) for (int i = 0; i < Math.min(links.length, LINK_COUNT); i++)
            result.put(bounded(links[i]));
        while (result.length() < LINK_COUNT) result.put("");
        return result;
    }

    private static String bounded(String value) {
        if (value == null) return "";
        return value.length() > MAX_LINK_LENGTH ? value.substring(0, MAX_LINK_LENGTH) : value;
    }
}
