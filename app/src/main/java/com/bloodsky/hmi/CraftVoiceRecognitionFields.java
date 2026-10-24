package com.bloodsky.hmi;

import org.json.JSONException;
import org.json.JSONObject;

/** Lossless editor access to the sixteen source phrase fields on Craft ID 27. */
public final class CraftVoiceRecognitionFields {
    public static final int PHRASE_COUNT = 16;
    public static final int MAX_PHRASE_LENGTH = 2000;

    private CraftVoiceRecognitionFields() { }

    public static String[] read(JSONObject sourceOther) {
        String[] phrases = new String[PHRASE_COUNT];
        for (int i = 0; i < PHRASE_COUNT; i++)
            phrases[i] = sourceOther == null ? "" : sourceOther.optString("textVoice" + i, "");
        return phrases;
    }

    public static JSONObject write(JSONObject sourceOther, String[] phrases) throws JSONException {
        if (phrases == null || phrases.length != PHRASE_COUNT)
            throw new IllegalArgumentException("Нужно 16 фраз Craft");
        JSONObject updated = sourceOther == null ? new JSONObject() : new JSONObject(sourceOther.toString());
        for (int i = 0; i < PHRASE_COUNT; i++) {
            String phrase = phrases[i] == null ? "" : phrases[i];
            if (phrase.length() > MAX_PHRASE_LENGTH)
                throw new IllegalArgumentException("Фраза Craft " + i + " длиннее " + MAX_PHRASE_LENGTH + " символов");
            updated.put("textVoice" + i, phrase);
        }
        return updated;
    }
}
