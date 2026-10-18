package com.bloodsky.hmi;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.*;

public class FbdCraftMacroDefinitionTest {
    private static String macro(String extra) {
        return "{\"name\":\"Scale\",\"countIn\":3,\"uniqueId\":42,"
                + "\"nameIn1\":\"Enable\",\"typeIn1\":1,"
                + "\"nameIn2\":\"Raw\",\"typeIn2\":2,"
                + "\"nameIn3\":\"Gain\",\"typeIn3\":3,"
                + "\"nameOut\":\"Result\",\"typeOut\":3,"
                + "\"fbInMacros\":[{\"componentId\":17,\"x\":12,\"y\":24,"
                + "\"nMcr\":\"Scale\",\"unIdMcr\":42,\"vendorTag\":\"keep\"}],"
                + extra + "}";
    }

    @Test public void readsDynamicTypedPortsAndRoundTripsUnknownMetadata() throws Exception {
        FbdCraftMacroDefinition definition = FbdCraftMacroDefinition.fromJson(
                macro("\"authorNote\":\"opaque\""));
        assertEquals("Scale", definition.getName());
        assertEquals(3, definition.getInputs().size());
        assertEquals("Enable", definition.getInputs().get(0).name);
        assertEquals("BOOL", definition.getInputs().get(0).signalType());
        assertEquals("INT", definition.getInputs().get(1).signalType());
        assertEquals("FLOAT", definition.getInputs().get(2).signalType());
        assertEquals("Result", definition.getOutput().name);
        assertEquals("FLOAT", definition.getOutput().signalType());

        FbdCraftMacroDefinition reloaded = FbdCraftMacroDefinition.fromMcrBytes(
                definition.toMcrBytes());
        assertEquals("opaque", reloaded.toJson().getString("authorNote"));
        assertEquals("keep", reloaded.toJson().getJSONArray("fbInMacros")
                .getJSONObject(0).getString("vendorTag"));
    }

    @Test public void rejectsMalformedUtf8OversizeAndTrailingJson() throws Exception {
        assertInvalid(() -> FbdCraftMacroDefinition.fromMcrBytes(new byte[]{(byte) 0xc3, 0x28}));
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson("{} {}"));
        byte[] tooLarge = new byte[FbdCraftMacroDefinition.MAX_FILE_BYTES + 1];
        assertInvalid(() -> FbdCraftMacroDefinition.fromMcrBytes(tooLarge));
    }

    @Test public void rejectsUnsafeNamesInvalidTypesAndForeignChildRecords() throws Exception {
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro("\"name\":\"../x\"")));
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro("\"typeOut\":4")));
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro(
                "\"fbInMacros\":[{\"componentId\":17,\"x\":12,\"y\":24,"
                        + "\"nMcr\":\"Other\",\"unIdMcr\":42}]")));
    }

    @Test public void enforcesChildLimitAndPortCount() throws Exception {
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro("\"countIn\":5")));
        StringBuilder children = new StringBuilder("[" );
        for (int i = 0; i <= FbdCraftMacroDefinition.MAX_CHILD_COMPONENTS; i++) {
            if (i > 0) children.append(',');
            children.append("{\"componentId\":1,\"x\":0,\"y\":0}");
        }
        children.append(']');
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(
                "{\"name\":\"Big\",\"countIn\":0,\"typeOut\":1,\"fbInMacros\":"
                        + children + "}"));
    }

    @Test public void validatesOptionalBloodskyNestedGraphAndRejectsCycles() throws Exception {
        String graph = "\"bloodskyGraph\":{" +
                "\"widgets\":[{\"type\":\"FBD CRAFT MACRO IN\",\"craftMacroPortName\":\"Raw\","
                + "\"craftMacroPortType\":2},{\"type\":\"FBD POW\"}],"
                + "\"connections\":[{\"source\":0,\"sourcePin\":\"Q\",\"target\":1,\"targetPin\":\"BASE\"}],"
                + "\"outputNode\":1,\"outputPin\":\"OUT\",\"outputPort\":\"Result\"}";
        FbdCraftMacroDefinition valid = FbdCraftMacroDefinition.fromJson(macro(graph));
        assertTrue(valid.toJson().has("bloodskyGraph"));

        String cycle = "\"bloodskyGraph\":{" +
                "\"widgets\":[{\"type\":\"FBD НЕ\"},{\"type\":\"FBD НЕ\"}],"
                + "\"connections\":[{\"source\":0,\"sourcePin\":\"Q\",\"target\":1,\"targetPin\":\"IN\"},"
                + "{\"source\":1,\"sourcePin\":\"Q\",\"target\":0,\"targetPin\":\"IN\"}],"
                + "\"outputNode\":0,\"outputPin\":\"Q\",\"outputPort\":\"Result\"}";
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro(cycle)));

        String foreignPort = "\"bloodskyGraph\":{" +
                "\"widgets\":[{\"type\":\"FBD CRAFT MACRO IN\",\"craftMacroPortName\":\"Missing\","
                + "\"craftMacroPortType\":1}],\"connections\":[],"
                + "\"outputNode\":0,\"outputPin\":\"Q\",\"outputPort\":\"Result\"}";
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro(foreignPort)));

        String duplicateTarget = "\"bloodskyGraph\":{" +
                "\"widgets\":[{\"type\":\"FBD CRAFT MACRO IN\",\"craftMacroPortName\":\"Raw\","
                + "\"craftMacroPortType\":2},{\"type\":\"FBD POW\"}],"
                + "\"connections\":[{\"source\":0,\"sourcePin\":\"Q\",\"target\":1,\"targetPin\":\"BASE\"},"
                + "{\"source\":0,\"sourcePin\":\"Q\",\"target\":1,\"targetPin\":\"BASE\"}],"
                + "\"outputNode\":1,\"outputPin\":\"OUT\",\"outputPort\":\"Result\"}";
        assertInvalid(() -> FbdCraftMacroDefinition.fromJson(macro(duplicateTarget)));
    }

    private static void assertInvalid(ThrowingRunnable action) throws Exception {
        try { action.run(); fail("Expected invalid macro to be rejected"); }
        catch (JSONException expected) { /* expected */ }
    }

    private interface ThrowingRunnable { void run() throws Exception; }
}
