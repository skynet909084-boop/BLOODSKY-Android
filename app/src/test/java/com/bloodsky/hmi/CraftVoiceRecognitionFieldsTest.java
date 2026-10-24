package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CraftVoiceRecognitionFieldsTest {
    @Test public void readsAndWritesSixteenPhrasesWithoutDroppingUnknownSourceFields() throws Exception {
        JSONObject source = new JSONObject()
                .put("textVoice0", "start pump")
                .put("textVoice15", "stop pump")
                .put("futureField", 42);
        String[] phrases = CraftVoiceRecognitionFields.read(source);
        assertEquals("start pump", phrases[0]);
        assertEquals("", phrases[1]);
        assertEquals("stop pump", phrases[15]);

        phrases[1] = "open valve";
        JSONObject saved = CraftVoiceRecognitionFields.write(source, phrases);
        assertEquals("start pump", saved.getString("textVoice0"));
        assertEquals("open valve", saved.getString("textVoice1"));
        assertEquals("stop pump", saved.getString("textVoice15"));
        assertEquals(42, saved.getInt("futureField"));
        assertEquals(16, CraftVoiceRecognitionFields.read(saved).length);
    }

    @Test public void rejectsInvalidPhraseArraysAndOversizedPhrase() {
        assertThrows(IllegalArgumentException.class,
                () -> CraftVoiceRecognitionFields.write(null, new String[15]));
        String[] phrases = CraftVoiceRecognitionFields.read(null);
        phrases[4] = new String(new char[CraftVoiceRecognitionFields.MAX_PHRASE_LENGTH + 1]).replace('\0', 'x');
        assertThrows(IllegalArgumentException.class,
                () -> CraftVoiceRecognitionFields.write(null, phrases));
    }
}
