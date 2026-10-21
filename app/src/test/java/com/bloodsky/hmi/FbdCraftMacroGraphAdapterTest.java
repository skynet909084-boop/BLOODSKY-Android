package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class FbdCraftMacroGraphAdapterTest {
    private static JSONObject validMacro() throws Exception {
        JSONObject child = new JSONObject().put("componentId", 103).put("x", 10).put("y", 20)
                .put("saveKey", 50).put("keyOut", 900).put("numOnIn", 1)
                .put("nMcr", "Gate").put("unIdMcr", 7);
        return new JSONObject().put("name", "Gate").put("uniqueId", 7).put("countIn", 2)
                .put("nameIn1", "A").put("typeIn1", 1).put("numOnIn1", 1).put("keyOutIn1", 50)
                .put("nameIn2", "B").put("typeIn2", 1).put("numOnIn2", 2).put("keyOutIn2", 50)
                .put("nameOut", "Q").put("typeOut", 1).put("saveKeyOut", 900)
                .put("fbInMacros", new JSONArray().put(child));
    }

    @Test public void mapsOriginalCraftTimerContactAndRetainsLegacyPtAlias() {
        assertArrayEquals(new String[]{"IN", "MS"},
                MainActivity.knownFbdPins("FBD CRAFT TIMER", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CRAFT TIMER", "MS", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CRAFT TIMER", "PT", false));
        assertEquals(2000L, MainActivity.fbdCraftTimerPresetMs(
                java.util.Collections.singletonList(2.0), java.util.Collections.singletonList("MS"),
                1000, "SEK"));
        assertEquals(3000L, MainActivity.fbdCraftTimerPresetMs(
                java.util.Collections.singletonList(3.0), java.util.Collections.singletonList("PT"),
                1000, "SEK"));
    }

    @Test public void mapsVerifiedCraftComponentIds() throws Exception {
        assertEquals("FBD AND", FbdCraftMacroGraphAdapter.typeForComponentId(103, null));
        assertEquals("FBD CRAFT TIMER", FbdCraftMacroGraphAdapter.typeForComponentId(115, null));
        assertEquals("FBD CRAFT VAR NAME IN FLOAT", FbdCraftMacroGraphAdapter.typeForComponentId(133,
                new JSONObject().put("typeVar", 3)));
        assertEquals("FBD CRAFT MX", FbdCraftMacroGraphAdapter.typeForComponentId(128, null));
        assertEquals("FBD CRAFT MACRO", FbdCraftMacroGraphAdapter.typeForComponentId(135, null));
        assertNull(FbdCraftMacroGraphAdapter.typeForComponentId(999, null));
    }

    @Test public void reconstructsInputsAndOutputFromCraftKeys() throws Exception {
        JSONObject macro = validMacro();
        JSONObject graph = FbdCraftMacroGraphAdapter.translate(macro, macro.getJSONArray("fbInMacros"));
        assertNotNull(graph);
        assertEquals(3, graph.getJSONArray("widgets").length());
        assertEquals(2, graph.getJSONArray("connections").length());
        assertEquals(2, graph.getJSONArray("connections").getJSONObject(1).getInt("target"));
        assertEquals("IN2", graph.getJSONArray("connections").getJSONObject(1).getString("targetPin"));
        assertEquals(2, graph.getInt("outputNode"));
        assertEquals("Q", graph.getString("outputPin"));
    }

    @Test public void expandsOnlyFullyMappedLegacyMacroAndPreservesRawChildren() throws Exception {
        FbdCraftMacroDefinition expanded = FbdCraftMacroGraphAdapter.expandIfSupported(
                FbdCraftMacroDefinition.fromJson(validMacro().toString()));
        assertNotNull(expanded.toJson().optJSONObject("bloodskyGraph"));
        assertEquals(103, expanded.toJson().getJSONArray("fbInMacros").getJSONObject(0).getInt("componentId"));
    }

    @Test public void expandsAndExecutesNestedMacroWhenItsDefinitionIsResolved() throws Exception {
        JSONObject nestedChild = new JSONObject().put("componentId", 105).put("x", 0).put("y", 0)
                .put("saveKey", 10).put("keyOut", 900).put("numOnIn", 1)
                .put("reg", 10).put("isConstant", true).put("constant", 31)
                .put("nMcr", "Inner").put("unIdMcr", 8);
        JSONObject nestedRoot = new JSONObject().put("name", "Inner").put("uniqueId", 8)
                .put("countIn", 0).put("typeOut", 2).put("nameOut", "Q").put("saveKeyOut", 900)
                .put("fbInMacros", new JSONArray().put(nestedChild));
        final FbdCraftMacroDefinition nestedDefinition = FbdCraftMacroGraphAdapter.expandIfSupported(
                FbdCraftMacroDefinition.fromJson(nestedRoot.toString()));
        assertNotNull(nestedDefinition.toJson().optJSONObject("bloodskyGraph"));

        JSONObject nestedInstance = new JSONObject().put("componentId", 135).put("x", 0).put("y", 0)
                .put("saveKey", 20).put("keyOut", 900).put("numOnIn", 1)
                .put("name", "Inner").put("nMcr", "Outer").put("unIdMcr", 9);
        JSONObject outerRoot = new JSONObject().put("name", "Outer").put("uniqueId", 9)
                .put("countIn", 0).put("typeOut", 2).put("nameOut", "Q").put("saveKeyOut", 900)
                .put("fbInMacros", new JSONArray().put(nestedInstance));
        FbdCraftMacroDefinition outerDefinition = FbdCraftMacroGraphAdapter.expandIfSupported(
                FbdCraftMacroDefinition.fromJson(outerRoot.toString()),
                child -> "Inner".equals(child.optString("name")) ? nestedDefinition : null);
        JSONObject graph = outerDefinition.toJson().getJSONObject("bloodskyGraph");
        assertEquals("FBD CRAFT MACRO", graph.getJSONArray("widgets").getJSONObject(0).getString("type"));
        assertEquals("Inner", graph.getJSONArray("widgets").getJSONObject(0)
                .getJSONObject("craftMacroDefinition").getString("name"));

        MainActivity.Widget instance = MainActivity.widgetFromJson(new JSONObject()
                .put("type", "FBD CRAFT MACRO").put("craftMacroDefinition", outerDefinition.toJson()));
        double[] numeric = new double[1];
        MainActivity.evaluateFbdBlock(Collections.singletonList(instance), Collections.emptyList(), 0,
                new boolean[1], new boolean[1], new boolean[1], numeric,
                new CraftSystemRegisterBank(), false);
        assertEquals(31.0, numeric[0], 0.0);
    }

    @Test public void executesImportedRegisterWritesOnlyDuringOperatorScan() throws Exception {
        JSONObject literal = new JSONObject().put("componentId", 105).put("x", 0).put("y", 0)
                .put("saveKey", 10).put("keyOut", 20).put("numOnIn", 1)
                .put("reg", 10).put("isConstant", true).put("constant", 42)
                .put("nMcr", "WriteUserRegister").put("unIdMcr", 7);
        JSONObject registerOutput = new JSONObject().put("componentId", 106).put("x", 10).put("y", 0)
                .put("saveKey", 20).put("keyOut", -1).put("numOnIn", 1)
                .put("reg", 11).put("nMcr", "WriteUserRegister").put("unIdMcr", 7);
        JSONObject macroOutput = new JSONObject().put("componentId", 105).put("x", 20).put("y", 0)
                .put("saveKey", 30).put("keyOut", 900).put("numOnIn", 1)
                .put("reg", 10).put("isConstant", true).put("constant", 7)
                .put("nMcr", "WriteUserRegister").put("unIdMcr", 7);
        JSONObject source = new JSONObject().put("name", "WriteUserRegister").put("uniqueId", 7)
                .put("countIn", 0).put("typeOut", 2).put("nameOut", "Q").put("saveKeyOut", 900)
                .put("fbInMacros", new JSONArray().put(literal).put(registerOutput).put(macroOutput));
        FbdCraftMacroDefinition expanded = FbdCraftMacroGraphAdapter.expandIfSupported(
                FbdCraftMacroDefinition.fromJson(source.toString()));
        assertNotNull("register output blocks have executable pins", expanded.toJson().optJSONObject("bloodskyGraph"));

        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        MainActivity.Widget macro = MainActivity.widgetFromJson(new JSONObject()
                .put("type", "FBD CRAFT MACRO").put("craftMacroDefinition", expanded.toJson()));
        double[] numeric = new double[1];
        MainActivity.evaluateFbdBlock(Collections.singletonList(macro), Collections.emptyList(), 0,
                new boolean[1], new boolean[1], new boolean[1], numeric, bank, false);
        assertEquals("editor evaluation must not write the system register", 0, bank.readWord(11));

        macro = MainActivity.widgetFromJson(new JSONObject()
                .put("type", "FBD CRAFT MACRO").put("craftMacroDefinition", expanded.toJson()));
        numeric = new double[1];
        MainActivity.evaluateFbdBlock(Collections.singletonList(macro), Collections.emptyList(), 0,
                new boolean[1], new boolean[1], new boolean[1], numeric, bank, true);
        assertEquals(42, bank.readWord(11));
        assertEquals("macro output still follows its declared output key", 7.0, numeric[0], 0.0);
    }

    @Test public void usesVerifiedOutputPinNamesForCraftConverterEdges() throws Exception {
        JSONObject first = new JSONObject().put("componentId", 118).put("x", 0).put("y", 0)
                .put("saveKey", 50).put("keyOut", 51).put("numOnIn", 1)
                .put("nMcr", "Gate").put("unIdMcr", 7);
        JSONObject second = new JSONObject().put("componentId", 121).put("x", 10).put("y", 0)
                .put("saveKey", 51).put("keyOut", 900).put("numOnIn", 1)
                .put("nMcr", "Gate").put("unIdMcr", 7);
        JSONObject macro = new JSONObject().put("countIn", 1).put("nameIn1", "A").put("typeIn1", 2)
                .put("keyOutIn1", 50).put("numOnIn1", 1).put("saveKeyOut", 900)
                .put("fbInMacros", new JSONArray().put(first).put(second));
        JSONObject graph = FbdCraftMacroGraphAdapter.translate(macro, macro.getJSONArray("fbInMacros"));
        assertNotNull(graph);
        assertEquals("OUT", graph.getJSONArray("connections").getJSONObject(0).getString("sourcePin"));
        assertEquals("OUT", graph.getString("outputPin"));
    }

    @Test public void translatesCraftSensorSunriseAndMqttMetadata() throws Exception {
        JSONArray children = new JSONArray()
                .put(new JSONObject().put("componentId", 120).put("x", 0).put("y", 0)
                        .put("type", 3).put("saveKey", 10).put("keyOut", -1))
                .put(new JSONObject().put("componentId", 127).put("x", 0).put("y", 0)
                        .put("type", 2).put("saveKey", 20).put("keyOut", -1)
                        .put("latitude", 51.5).put("longitude", -0.1))
                .put(new JSONObject().put("componentId", 139).put("x", 0).put("y", 0)
                        .put("saveKey", 30).put("keyOut", -1).put("topic", "site/value")
                        .put("typeQ", 3).put("numMqttServer", 2))
                .put(new JSONObject().put("componentId", 140).put("x", 0).put("y", 0)
                        .put("saveKey", 40).put("keyOut", -1).put("topic", "site/out")
                        .put("typeIn", 1).put("numMqttServer", 2).put("mqttRetain", true))
                .put(new JSONObject().put("componentId", 121).put("x", 0).put("y", 0)
                        .put("saveKey", 50).put("keyOut", 900));
        JSONObject root = new JSONObject().put("countIn", 0).put("saveKeyOut", 900)
                .put("fbInMacros", children);
        JSONObject graph = FbdCraftMacroGraphAdapter.translate(root, children);
        assertNotNull(graph);
        JSONArray widgets = graph.getJSONArray("widgets");
        assertEquals(3, widgets.getJSONObject(0).getInt("craftSensorAxis"));
        assertEquals("SS", widgets.getJSONObject(1).getString("craftSunriseMode"));
        assertEquals("site/value", widgets.getJSONObject(2).getString("craftMqttTopic"));
        assertEquals(3, widgets.getJSONObject(2).getInt("craftMqttType"));
        assertEquals(2, widgets.getJSONObject(2).getInt("craftMqttServer"));
        assertTrue(widgets.getJSONObject(3).getBoolean("craftMqttRetain"));
    }

    @Test public void preservesMacrosThatNeedScreenLevelIo() throws Exception {
        int[] unsupported = {101, 108, 120, 130, 131, 132, 139, 140};
        for (int id : unsupported) {
            JSONObject root = validMacro();
            root.getJSONArray("fbInMacros").getJSONObject(0).put("componentId", id);
            FbdCraftMacroDefinition expanded = FbdCraftMacroGraphAdapter.expandIfSupported(
                    FbdCraftMacroDefinition.fromJson(root.toString()));
            assertNull("ID " + id + " must stay opaque", expanded.toJson().optJSONObject("bloodskyGraph"));
        }
    }

    @Test public void failsClosedForUnknownIdsOrBrokenEdges() throws Exception {
        JSONObject unknown = validMacro();
        unknown.getJSONArray("fbInMacros").getJSONObject(0).put("componentId", 999);
        assertNull(FbdCraftMacroGraphAdapter.translate(unknown, unknown.getJSONArray("fbInMacros")));
        JSONObject broken = validMacro();
        broken.getJSONArray("fbInMacros").getJSONObject(0).put("keyOut", 123456);
        assertNull(FbdCraftMacroGraphAdapter.translate(broken, broken.getJSONArray("fbInMacros")));
    }
}
