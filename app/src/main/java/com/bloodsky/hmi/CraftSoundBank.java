package com.bloodsky.hmi;

/** Bit-to-track selection rules used by KaScada Craft's eight-channel sound component. */
public final class CraftSoundBank {
    public static final int CHANNELS = 8;
    private static final int MASK = (1 << CHANNELS) - 1;

    private CraftSoundBank() {}

    /** Craft exposes the eight sound triggers as bits of one unsigned register value. */
    public static int activeMask(String rawValue) {
        if (rawValue == null) return 0;
        String value = rawValue.trim();
        try {
            long parsed = value.startsWith("0x") || value.startsWith("0X")
                    ? Long.parseLong(value.substring(2), 16) : Long.parseLong(value);
            return (int) parsed & MASK;
        } catch (NumberFormatException invalid) {
            try {
                double parsed = Double.parseDouble(value);
                if (!Double.isFinite(parsed) || parsed < 0 || parsed > 65535) return 0;
                return ((int) Math.round(parsed)) & MASK;
            } catch (NumberFormatException ignored) { return 0; }
        }
    }

    /** Clear a track's played latch after its source bit falls. */
    public static int clearInactive(int playedMask, int activeMask) {
        return playedMask & activeMask & MASK;
    }

    /** Pick the first active track that has not played in this high interval. */
    public static int nextPendingChannel(int activeMask, int playedMask) {
        int pending = activeMask & ~playedMask & MASK;
        for (int channel = 0; channel < CHANNELS; channel++)
            if ((pending & (1 << channel)) != 0) return channel;
        return -1;
    }

    public static String pathAt(org.json.JSONArray paths, int channel) {
        if (paths == null || channel < 0 || channel >= CHANNELS) return "";
        return paths.optString(channel, "").trim();
    }

    /** Store explicit operator settings while retaining the original Craft fields for round trips. */
    public static void storeProperties(org.json.JSONObject fields, boolean enabled, String[] paths)
            throws org.json.JSONException {
        if (fields == null) return;
        org.json.JSONArray savedPaths = new org.json.JSONArray();
        org.json.JSONObject craftOther = fields.optJSONObject("craftOther");
        if (craftOther == null) craftOther = new org.json.JSONObject();
        for (int channel = 0; channel < CHANNELS; channel++) {
            String path = paths != null && channel < paths.length && paths[channel] != null
                    ? paths[channel].trim() : "";
            savedPaths.put(path);
            craftOther.put("pathSound_" + channel, path);
        }
        fields.put("craftSoundPaths", savedPaths);
        fields.put("craftSoundEnabled", enabled);
        fields.put("craftOther", craftOther);
    }
}
