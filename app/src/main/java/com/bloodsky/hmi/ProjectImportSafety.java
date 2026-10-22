package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

/** Disarms imported external endpoints and send-capable actions while preserving their settings. */
public final class ProjectImportSafety {
    private static final String[] OPT_IN_FLAGS = {
            "craftGatewayEnabled", "craftBitGatewayEnabled", "smsEnabled",
            "craftWebViewEnabled", "craftEmailEnabled", "craftVoiceRecognitionEnabled",
            "craftButtonBitEnabled", "craftButtonConstantEnabled", "craftSwitchBitEnabled",
            "craftLinearEnabled", "craftListEnabled", "craftRtspEnabled",
            // Project/server-wide listener and other operator opt-ins must not
            // survive import as active capabilities on a different device.
            "modbusSlaveEnabled", "craftNumericEnabled", "craftPushEnabled",
            "craftSoundEnabled", "smsStrobeEnabled"
    };

    private ProjectImportSafety() { }

    public static String deactivateExternalActions(String json) throws JSONException {
        Object root = new org.json.JSONTokener(json).nextValue();
        deactivate(root);
        return root.toString();
    }

    private static void deactivate(Object value) throws JSONException {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            preserveAndClearEndpoint(object, "connectionInfo", "bloodskyImportedInactiveConnectionInfo",
                    "tcp://", "usb://", "bt://");
            preserveAndClearEndpoint(object, "craftArchiveTriggerBinding",
                    "bloodskyImportedInactiveArchiveTriggerBinding", "tcp://");
            for (String flag : OPT_IN_FLAGS) {
                if (object.optBoolean(flag, false)) object.put(flag, false);
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) deactivate(object.opt(keys.next()));
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) deactivate(array.opt(i));
        }
    }

    private static void preserveAndClearEndpoint(JSONObject object, String key, String preservedKey,
                                                  String... externalSchemes) throws JSONException {
        Object raw = object.opt(key);
        if (!(raw instanceof String)) return;
        String endpoint = ((String) raw).trim();
        if (endpoint.isEmpty()) return;
        boolean external = false;
        // RFC 3986 URI schemes are case-insensitive. Preserve the original
        // endpoint text in the inactive field, but compare normalized schemes
        // so imported TCP:// or Bt:// links cannot slip past disarming.
        String normalized = endpoint.toLowerCase(Locale.ROOT);
        for (String scheme : externalSchemes) external |= normalized.startsWith(scheme);
        if (!external) return;
        if (!object.has(preservedKey)) object.put(preservedKey, endpoint);
        object.put(key, "");
    }
}
