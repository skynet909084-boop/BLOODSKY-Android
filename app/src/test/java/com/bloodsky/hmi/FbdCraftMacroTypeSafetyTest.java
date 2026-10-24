package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/** Reject loss of original INT(2)/FLOAT(3) identity in executable macro graphs. */
public final class FbdCraftMacroTypeSafetyTest {
    private static JSONObject directMacro(int childPortType, int outputPortType)
            throws JSONException {
        JSONObject port = new JSONObject().put("type", "FBD CRAFT MACRO IN")
                .put("craftMacroPortName", "Value")
                .put("craftMacroPortType", childPortType);
        JSONObject graph = new JSONObject().put("widgets", new JSONArray().put(port))
                .put("connections", new JSONArray()).put("outputNode", 0)
                .put("outputPin", "Q").put("outputPort", "Q");
        return new JSONObject().put("name", "TypedPort").put("countIn", 1)
                .put("nameIn1", "Value").put("typeIn1", childPortType)
                .put("nameOut", "Q").put("typeOut", outputPortType)
                .put("fbInMacros", new JSONArray()).put("bloodskyGraph", graph);
    }

    @Test public void directIntInputCannotMasqueradeAsFloatOutput() throws Exception {
        assertThrows(JSONException.class, () -> FbdCraftMacroDefinition.fromJson(
                directMacro(2, 3).toString()));
        assertThrows(JSONException.class, () -> FbdCraftMacroDefinition.fromJson(
                directMacro(3, 2).toString()));
        assertEquals("INT", FbdCraftMacroDefinition.fromJson(
                directMacro(2, 2).toString()).getOutput().signalType());
        assertEquals("FLOAT", FbdCraftMacroDefinition.fromJson(
                directMacro(3, 3).toString()).getOutput().signalType());
    }

    private static JSONObject wiredMacro(String sourceType, String destinationType)
            throws Exception {
        JSONObject from = new JSONObject().put("type", sourceType);
        JSONObject to = new JSONObject().put("type", destinationType);
        JSONObject graph = new JSONObject()
                .put("widgets", new JSONArray().put(from).put(to))
                .put("connections", new JSONArray().put(new JSONObject()
                        .put("source", 0).put("sourcePin", "Q")
                        .put("target", 1).put("targetPin", "IN")))
                .put("outputNode", 0).put("outputPin", "Q").put("outputPort", "Q");
        int type = "FBD CRAFT VAR INT".equals(sourceType) ? 2 : 3;
        return new JSONObject().put("name", "TypedWires")
                .put("countIn", 0).put("typeOut", type).put("nameOut", "Q")
                .put("fbInMacros", new JSONArray()).put("bloodskyGraph", graph);
    }

    @Test public void macroWiringCannotConnectCraftIntToFloatOrViceVersa()
            throws Exception {
        assertThrows(JSONException.class, () -> FbdCraftMacroDefinition.fromJson(
                wiredMacro("FBD CRAFT VAR INT", "FBD CRAFT VAR OUT FLOAT").toString()));
        assertThrows(JSONException.class, () -> FbdCraftMacroDefinition.fromJson(
                wiredMacro("FBD CRAFT VAR FLOAT", "FBD CRAFT VAR OUT INT").toString()));
    }

    @Test public void identicalCraftTypedConnectionsRemainExecutable() throws Exception {
        FbdCraftMacroDefinition ints = FbdCraftMacroDefinition.fromJson(
                wiredMacro("FBD CRAFT VAR INT", "FBD CRAFT VAR OUT INT").toString());
        assertEquals("INT", ints.getOutput().signalType());
        FbdCraftMacroDefinition floats = FbdCraftMacroDefinition.fromJson(
                wiredMacro("FBD CRAFT VAR FLOAT", "FBD CRAFT VAR OUT FLOAT").toString());
        assertEquals("FLOAT", floats.getOutput().signalType());
        assertEquals(1, floats.toJson().getJSONObject("bloodskyGraph")
                .getJSONArray("connections").length());
    }
}
