package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
