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
public class CraftControlRecordIntegrityTest {
    @Parameterized.Parameters(name = "Craft control {0}")
    public static Collection<Object[]> components() {
        return Arrays.asList(new Object[][] {{6}, {8}, {20}});
    }
    private final int id;
    public CraftControlRecordIntegrityTest(int id) { this.id = id; }

    private String enabledKey() {
        return id == 6 ? "craftButtonBitEnabled" : id == 8 ? "craftNumericEnabled" : "craftListEnabled";
    }
    private JSONObject edit(JSONObject fields, int register) throws Exception {
        if (id == 6) return CraftButtonCompactSettings.withSettings(fields, register, 0, "Run", true);
        if (id == 8) return CraftNumericInputCompactSettings.withSettings(fields, register,
                0, 100, 0, 0, 2, false, true);
        return CraftBitList.withSettings(fields, register, CraftBitList.parseEditorLines("Run\nStop"), true, false);
    }

    @Test public void invalidSourceDataCannotBeOverwrittenByControlEdit() throws Exception {
        for (String key : new String[] {"craftOther", "craftConnectionInfo"}) {
            for (Object raw : new Object[] {JSONObject.NULL, true, 123, 0.25,
                    new JSONArray().put("vendor"), "[]", "false", "null", "{broken", ""}) {
                JSONObject fields = new JSONObject().put(key, raw)
                        .put("connectionInfo", "tcp://localhost:502/1/5").put(enabledKey(), true);
                String before = fields.toString();
                assertThrows(IllegalArgumentException.class, () -> edit(fields, 6));
                assertEquals(before, fields.toString());
            }
        }
    }

    @Test public void validRecordsRetainSecretsUnknownKeysAndRemapConsentRules() throws Exception {
        for (boolean encoded : new boolean[] {true, false}) {
            JSONObject source = new JSONObject().put("addressRegister", 5)
                    .put("password", "test-only-placeholder").put("vendor", new JSONArray().put(123));
            JSONObject other = new JSONObject().put("vendor", "keep");
            JSONObject fields = new JSONObject().put("craftConnectionInfo", encoded ? source.toString() : source)
                    .put("craftOther", encoded ? other.toString() : other)
                    .put("connectionInfo", "tcp://localhost:502/1/5").put(enabledKey(), true);
            String before = fields.toString();
            assertTrue(edit(fields, 5).getBoolean(enabledKey()));
            JSONObject changed = new JSONObject(edit(fields, 6).toString());
            assertFalse(changed.getBoolean(enabledKey()));
            assertEquals("tcp://localhost:502/1/5", changed.getString("connectionInfo"));
            assertEquals(encoded, changed.get("craftConnectionInfo") instanceof String);
            assertEquals(encoded, changed.get("craftOther") instanceof String);
            JSONObject link = new JSONObject(changed.get("craftConnectionInfo").toString());
            assertEquals("test-only-placeholder", link.getString("password"));
            assertEquals(123, link.getJSONArray("vendor").getInt(0));
            assertEquals("keep", new JSONObject(changed.get("craftOther").toString()).getString("vendor"));
            assertEquals(before, fields.toString());
        }
    }

    @Test public void newControlsWithAbsentRecordsRemainEditable() throws Exception {
        JSONObject edited = edit(new JSONObject(), 6);
        assertEquals(6, edited.getJSONObject("craftConnectionInfo").getInt("addressRegister"));
        assertFalse(edited.optBoolean(enabledKey(), false));
    }
}
