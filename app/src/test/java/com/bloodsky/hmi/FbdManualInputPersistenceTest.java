package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class FbdManualInputPersistenceTest {
    @Test public void manualBoolInputSurvivesCopyAndSaveWithoutLosingUnknownFields() throws Exception {
        MainActivity.Widget input = new MainActivity.Widget();
        input.type = "FBD Вход";
        input.text = "Ручной вход";
        input.inputValue = true;
        JSONObject state = MainActivity.widgetToJson(input);
        state.put("legacyCraftTag", new JSONObject().put("source", "external"));
        MainActivity.Widget restored = MainActivity.widgetFromJson(state);
        assertTrue(restored.inputValue);
        assertEquals("Ручной вход", restored.text);
        JSONObject saved = MainActivity.widgetToJson(restored);
        assertTrue(saved.getBoolean("inputValue"));
        assertEquals("external", saved.getJSONObject("legacyCraftTag").getString("source"));
        MainActivity.Widget copied = MainActivity.copyWidget(restored);
        assertTrue(copied.inputValue);
        assertEquals("external", MainActivity.widgetToJson(copied)
                .getJSONObject("legacyCraftTag").getString("source"));
        copied.inputValue = false;
        JSONObject off = MainActivity.widgetToJson(copied);
        assertFalse(MainActivity.widgetFromJson(off).inputValue);
        assertTrue(restored.inputValue);
    }
}
