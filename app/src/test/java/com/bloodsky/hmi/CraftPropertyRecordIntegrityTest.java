package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class CraftPropertyRecordIntegrityTest {
    @Parameterized.Parameters(name = "Craft ID {0}")
    public static Collection<Object[]> components() {
        return Arrays.asList(new Object[][] {{7}, {11}, {12}, {24}});
    }

    private final int id;
    public CraftPropertyRecordIntegrityTest(int id) { this.id = id; }

    private JSONObject edit(JSONObject fields) throws Exception {
        switch (id) {
            case 7:
                String[] captions = new String[16];
                Arrays.fill(captions, "State");
                return CraftDynamicTextProperties.withSettings(fields, 42, captions,
                        new int[16], true, false);
            case 11:
                return CraftProgressBarProperties.withSettings(fields, 42, 0, 100,
                        true, 20, 1, true, 2, 3);
            case 12:
                return CraftThresholdGaugeProperties.withSettings(fields, 42, 100,
                        true, 20, 1, 2, 3, 4, 25, 75);
            case 24:
                return CraftGraduatedGaugeProperties.withSettings(fields, 42, 0, 100,
                        10, 20, 1, 2, 3, 4, 5, 25, 75, false, "NormalIndicator");
            default: throw new AssertionError(id);
        }
    }

    @Test public void unsupportedRecordsRejectEditWithoutLosingData() throws Exception {
        Object[] invalid = {JSONObject.NULL, true, false, 42, 1.5,
                new JSONArray().put(new JSONObject().put("vendor", "keep")),
                "[]", "null", "true", "42", "{broken", ""};
        for (String key : new String[] {"craftOther", "craftConnectionInfo"}) {
            for (Object value : invalid) {
                JSONObject fields = new JSONObject().put("craftOther", new JSONObject())
                        .put("craftConnectionInfo", new JSONObject().put("addressRegister", 5))
                        .put("connectionInfo", "tcp://localhost:502/1/5")
                        .put("vendor", new JSONArray().put("opaque")).put(key, value);
                String before = fields.toString();
                assertThrows(key + ": " + value, IllegalArgumentException.class, () -> edit(fields));
                assertEquals(before, fields.toString());
            }
        }
    }

    @Test public void validObjectAndEncodedRecordsRemainLosslessAndDetached() throws Exception {
        for (boolean encoded : new boolean[] {false, true}) {
            JSONObject other = new JSONObject().put("numScreen", 3)
                    .put("vendor", new JSONObject().put("future", new JSONArray().put(9)));
            JSONObject connection = new JSONObject().put("addressRegister", 5)
                    .put("posDeviceNetworkInList", 100).put("vendor", "retain");
            JSONObject fields = new JSONObject().put("craftOther", encoded ? other.toString() : other)
                    .put("craftConnectionInfo", encoded ? connection.toString() : connection)
                    .put("connectionInfo", "");
            String before = fields.toString();
            JSONObject edited = edit(fields);
            // Simulate persistence/reopen, including encoded-object representation.
            edited = new JSONObject(edited.toString());
            assertEquals(encoded, edited.get("craftOther") instanceof String);
            assertEquals(encoded, edited.get("craftConnectionInfo") instanceof String);
            JSONObject out = new JSONObject(edited.get("craftOther").toString());
            JSONObject link = new JSONObject(edited.get("craftConnectionInfo").toString());
            assertEquals(3, out.getInt("numScreen"));
            assertEquals(9, out.getJSONObject("vendor").getJSONArray("future").getInt(0));
            assertEquals(42, link.getInt("addressRegister"));
            assertEquals(100, link.getInt("posDeviceNetworkInList"));
            assertEquals("retain", link.getString("vendor"));
            assertEquals("", edited.getString("connectionInfo"));
            out.getJSONObject("vendor").put("future", "changed");
            assertEquals(before, fields.toString());
        }
    }

    @Test public void missingRecordsStillAllowNewComponents() throws Exception {
        JSONObject result = edit(new JSONObject());
        assertTrue(result.get("craftOther") instanceof JSONObject);
        assertEquals(42, result.getJSONObject("craftConnectionInfo").getInt("addressRegister"));
    }

    @Test public void directApplyRejectsUnsupportedOtherBeforeRuntimeMutation() throws Exception {
        if (id == 7) return; // ID7 exposes only the detached withSettings entry point.
        JSONObject fields = new JSONObject().put("craftOther", new JSONArray().put("opaque"))
                .put("minValue", -99).put("maxValue", 999);
        String before = fields.toString();
        assertThrows(IllegalArgumentException.class, () -> {
            if (id == 11) CraftProgressBarProperties.apply(fields, 0, 100, true, 20, 1, true, 2, 3);
            if (id == 12) CraftThresholdGaugeProperties.apply(fields, 100, true, 20, 1, 2, 3, 4, 25, 75);
            if (id == 24) CraftGraduatedGaugeProperties.apply(fields, 0, 100, 10, 20,
                    1, 2, 3, 4, 5, 25, 75, false, "NormalIndicator");
        });
        assertEquals(before, fields.toString());
    }
}
