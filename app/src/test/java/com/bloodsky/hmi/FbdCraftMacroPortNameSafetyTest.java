package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;

/** Executable Craft macro pins must resolve to one source input per name. */
public final class FbdCraftMacroPortNameSafetyTest {
    private static JSONObject macro(String first, String second, boolean executable)
            throws Exception {
        JSONObject result = new JSONObject().put("name", "Paired")
                .put("countIn", 2).put("nameIn1", first).put("typeIn1", 1)
                .put("nameIn2", second).put("typeIn2", 2)
                .put("nameOut", "Q").put("typeOut", 1)
                .put("fbInMacros", new JSONArray());
        if (executable) result.put("bloodskyGraph", new JSONObject()
                .put("widgets", new JSONArray().put(new JSONObject()
                        .put("type", "FBD CRAFT MACRO IN")
                        .put("craftMacroPortName", first)
                        .put("craftMacroPortType", 1)))
                .put("connections", new JSONArray()).put("outputNode", 0)
                .put("outputPin", "Q").put("outputPort", "Q"));
        return result;
    }

    @Test public void rejectsAmbiguousNamesOnlyWhenThereIsAnExecutableGraph()
            throws Exception {
        for (String alias : new String[]{"VALVE", "valve", " Valve "}) {
            JSONObject invalid = macro("Valve", alias, true);
            assertThrows(JSONException.class,
                    () -> FbdCraftMacroDefinition.fromJson(invalid.toString()));
        }
        FbdCraftMacroDefinition valid = FbdCraftMacroDefinition.fromJson(
                macro("Valve", "Setpoint", true).toString());
        assertEquals(2, valid.getInputs().size());
        assertNotNull(valid.toJson().getJSONObject("bloodskyGraph"));

        // Historical original macros have opaque source metadata: we may
        // import/export them, but must not falsely certify their execution.
        JSONObject original = macro("Valve", "valve", false);
        FbdCraftMacroDefinition opaque = FbdCraftMacroDefinition.fromJson(original.toString());
        assertNull(opaque.toJson().optJSONObject("bloodskyGraph"));
        assertEquals("valve", opaque.getInputs().get(1).name);
    }

    @Test public void legacyWireValidatorCannotAliasInvalidPortTypeToBool()
            throws Exception {
        JSONObject edge = new JSONObject().put("source", 0).put("target", 1)
                .put("sourcePin", "Q").put("targetPin", "IN");
        MainActivity.Widget input = new MainActivity.Widget();
        input.type = "FBD CRAFT MACRO IN";
        MainActivity.Widget sink = new MainActivity.Widget();
        sink.type = "FBD CRAFT VAR OUT BOOL";
        input.extraFields.put("craftMacroPortType", 1);
        assertEquals("BOOL", MainActivity.knownFbdPinType(input, "Q", true));
        assertTrue(MainActivity.hasCompatibleKnownFbdPins(edge, input, sink));
        for (Object invalid : new Object[]{4294967297L, 1.5, "4294967297",
                JSONObject.NULL, new JSONArray().put(1)}) {
            input.extraFields.put("craftMacroPortType", invalid);
            assertNull(MainActivity.knownFbdPinType(input, "Q", true));
            assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, input, sink));
        }
        input.extraFields.put("craftMacroPortType", 2);
        assertEquals("NUMBER", MainActivity.knownFbdPinType(input, "Q", true));
        assertFalse(MainActivity.hasCompatibleKnownFbdPins(edge, input, sink));
    }
}
