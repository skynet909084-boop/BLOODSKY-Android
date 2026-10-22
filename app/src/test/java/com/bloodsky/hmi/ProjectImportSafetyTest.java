package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ProjectImportSafetyTest {
    @Test public void deactivatesExternalRuntimeAndPreservesItsEndpointForManualRemap() throws Exception {
        JSONObject project = new JSONObject()
                .put("screens", new JSONArray().put(new JSONObject().put("widgets", new JSONArray().put(
                        new JSONObject().put("type", "FBD Источник регистра")
                                .put("connectionInfo", "bt://AA:BB:CC:DD:EE:FF/1/40001")
                                .put("craftGatewayEnabled", true)
                                .put("craftArchiveTriggerBinding", "tcp://192.0.2.4:502/1/40001")
                                .put("craftEmailEnabled", true)
                                .put("craftWebViewEnabled", true)))));

        JSONObject imported = new JSONObject(ProjectImportSafety.deactivateExternalActions(project.toString()));
        JSONObject widget = imported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("", widget.getString("connectionInfo"));
        assertEquals("bt://AA:BB:CC:DD:EE:FF/1/40001",
                widget.getString("bloodskyImportedInactiveConnectionInfo"));
        assertFalse(widget.getBoolean("craftGatewayEnabled"));
        assertEquals("", widget.getString("craftArchiveTriggerBinding"));
        assertEquals("tcp://192.0.2.4:502/1/40001",
                widget.getString("bloodskyImportedInactiveArchiveTriggerBinding"));
        assertFalse(widget.getBoolean("craftEmailEnabled"));
        assertFalse(widget.getBoolean("craftWebViewEnabled"));
    }

    @Test public void importedRuntimeOptInsRequireFreshOperatorApproval() throws Exception {
        JSONObject original = new JSONObject()
                .put("projectName", "Recoverable Craft project")
                .put("modbusSlaveEnabled", true)
                .put("modbusSlavePort", 1502)
                .put("modbusSlaveId", 9)
                .put("unknownProjectField", new JSONObject().put("retain", 17))
                .put("screens", new JSONArray().put(new JSONObject()
                        .put("widgets", new JSONArray()
                                .put(new JSONObject().put("type", "Числовой ввод Craft")
                                        .put("craftNumericEnabled", true)
                                        .put("craftNumericPasswordRequired", true)
                                        .put("craftNumericMin", -100)
                                        .put("connectionInfo", " TCP://192.0.2.2:502/1/40001 "))
                                .put(new JSONObject().put("type", "Уведомление Craft")
                                        .put("craftPushEnabled", true)
                                        .put("craftPushBody", "preserve this notice"))
                                .put(new JSONObject().put("type", "Звуковой сигнал Craft")
                                        .put("craftSoundEnabled", true)
                                        .put("craftSoundIndex", 3))
                                .put(new JSONObject().put("type", "SMS-уведомление")
                                        .put("smsEnabled", true)
                                        .put("smsStrobeEnabled", true)
                                        .put("smsStrobeRegister", 40002)))));

        JSONObject imported = new JSONObject(ProjectImportSafety.deactivateExternalActions(
                original.toString()));
        assertFalse(imported.getBoolean("modbusSlaveEnabled"));
        assertEquals(1502, imported.getInt("modbusSlavePort"));
        assertEquals(9, imported.getInt("modbusSlaveId"));
        assertEquals(17, imported.getJSONObject("unknownProjectField").getInt("retain"));
        JSONArray widgets = imported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("widgets");
        JSONObject numeric = widgets.getJSONObject(0);
        assertFalse(numeric.getBoolean("craftNumericEnabled"));
        assertTrue(numeric.getBoolean("craftNumericPasswordRequired"));
        assertEquals(-100, numeric.getInt("craftNumericMin"));
        assertEquals("", numeric.getString("connectionInfo"));
        assertEquals("TCP://192.0.2.2:502/1/40001",
                numeric.getString("bloodskyImportedInactiveConnectionInfo"));
        assertFalse(widgets.getJSONObject(1).getBoolean("craftPushEnabled"));
        assertEquals("preserve this notice", widgets.getJSONObject(1).getString("craftPushBody"));
        assertFalse(widgets.getJSONObject(2).getBoolean("craftSoundEnabled"));
        assertEquals(3, widgets.getJSONObject(2).getInt("craftSoundIndex"));
        assertFalse(widgets.getJSONObject(3).getBoolean("smsEnabled"));
        assertFalse(widgets.getJSONObject(3).getBoolean("smsStrobeEnabled"));
        assertEquals(40002, widgets.getJSONObject(3).getInt("smsStrobeRegister"));
    }

    @Test public void nestedMacrosAndRepeatedImportsCannotReactivateExternalEndpoints() throws Exception {
        JSONObject original = new JSONObject().put("craftMacroDefinitions",
                new JSONArray().put(new JSONObject().put("body",
                        new JSONObject().put("children", new JSONArray().put(
                                new JSONObject().put("craftGatewayEnabled", true)
                                        .put("craftPushEnabled", true)
                                        .put("connectionInfo", "Bt://AA:BB:CC:DD:EE:FF/1/40001")
                                        .put("craftArchiveTriggerBinding", "TCP://192.0.2.3:502/1/40002")
                                        .put("opaque", new JSONObject().put("keep", "metadata")))))));
        String once = ProjectImportSafety.deactivateExternalActions(original.toString());
        String twice = ProjectImportSafety.deactivateExternalActions(once);
        JSONObject first = new JSONObject(once).getJSONArray("craftMacroDefinitions")
                .getJSONObject(0).getJSONObject("body").getJSONArray("children").getJSONObject(0);
        JSONObject second = new JSONObject(twice).getJSONArray("craftMacroDefinitions")
                .getJSONObject(0).getJSONObject("body").getJSONArray("children").getJSONObject(0);
        assertFalse(first.getBoolean("craftGatewayEnabled"));
        assertFalse(first.getBoolean("craftPushEnabled"));
        assertEquals("", first.getString("connectionInfo"));
        assertEquals("Bt://AA:BB:CC:DD:EE:FF/1/40001",
                first.getString("bloodskyImportedInactiveConnectionInfo"));
        assertEquals("", first.getString("craftArchiveTriggerBinding"));
        assertEquals("TCP://192.0.2.3:502/1/40002",
                first.getString("bloodskyImportedInactiveArchiveTriggerBinding"));
        assertEquals("metadata", second.getJSONObject("opaque").getString("keep"));
        assertFalse(second.getBoolean("craftGatewayEnabled"));
        assertEquals("", second.getString("connectionInfo"));
        assertEquals(first.getString("bloodskyImportedInactiveConnectionInfo"),
                second.getString("bloodskyImportedInactiveConnectionInfo"));
        assertEquals(first.getString("bloodskyImportedInactiveArchiveTriggerBinding"),
                second.getString("bloodskyImportedInactiveArchiveTriggerBinding"));
    }

    @Test public void leavesInternalSystemBindingsAndAlreadyDisabledValuesAlone() throws Exception {
        JSONObject imported = new JSONObject(ProjectImportSafety.deactivateExternalActions(
                new JSONObject().put("connectionInfo", "system://register/10")
                        .put("craftGatewayEnabled", false).toString()));
        assertEquals("system://register/10", imported.getString("connectionInfo"));
        assertFalse(imported.getBoolean("craftGatewayEnabled"));
        assertFalse(imported.has("bloodskyImportedInactiveConnectionInfo"));

        JSONObject fbd = new JSONObject(ProjectImportSafety.deactivateExternalActions(
                new JSONObject().put("connectionInfo", "fbd://12").toString()));
        assertEquals("fbd://12", fbd.getString("connectionInfo"));
    }
}
