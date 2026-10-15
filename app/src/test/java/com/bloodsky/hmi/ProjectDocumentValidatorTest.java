package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProjectDocumentValidatorTest {
    @Test public void acceptsMultiScreenProjectAndUnknownMetadata() throws Exception {
        ProjectDocumentValidator.validateImportedProject(
            "{\"vendorField\":{\"keep\":true},\"currentScreen\":1,\"screens\":["
            + "{\"name\":\"Main\",\"customScreen\":7,\"widgets\":[{\"type\":\"Текст\",\"vendorWidget\":\"x\"}],\"connections\":[]},"
            + "{\"name\":\"Second\",\"widgets\":[],\"connections\":[]}]}" );
    }
    @Test public void acceptsLegacyWidgetArray() throws Exception {
        ProjectDocumentValidator.validateImportedProject("[{\"type\":\"Текст\",\"x\":1,\"y\":2}]");
    }
    @Test public void rejectsMalformedJson() { assertRejected("{not-json}"); }
    @Test public void rejectsTrailingContent() {
        assertRejected("{\"screens\":[{\"widgets\":[],\"connections\":[]}]} trailing");
    }
    @Test public void rejectsEmptyOrMissingScreens() {
        assertRejected("{\"screens\":[]}"); assertRejected("{}");
    }
    @Test public void rejectsOutOfRangeCurrentScreen() {
        assertRejected("{\"currentScreen\":1,\"screens\":[{\"widgets\":[],\"connections\":[]}]}");
        assertRejected("{\"currentScreen\":-1,\"screens\":[{\"widgets\":[],\"connections\":[]}]}");
    }
    @Test public void rejectsMalformedScreenAndWidgetStructures() {
        assertRejected("{\"screens\":[null]}");
        assertRejected("{\"screens\":[{\"widgets\":null}]}");
        assertRejected("{\"screens\":[{\"widgets\":[null]}]}");
    }
    @Test public void rejectsInvalidAndCyclicFbdConnections() {
        String twoBlocks = "{\"screens\":[{\"widgets\":[{\"type\":\"FBD AND\"},{\"type\":\"FBD OR\"}],\"connections\":[%s]}]}";
        assertRejected(String.format(twoBlocks, "{\"source\":0,\"target\":2}"));
        assertRejected(String.format(twoBlocks, "{\"source\":0,\"target\":1},{\"source\":1,\"target\":0}"));
    }
    @Test public void metadataCopyPreservesUnknownNestedValuesAndKnownFieldsWin() throws Exception {
        org.json.JSONObject extras = new org.json.JSONObject(
            "{\"vendor\":{\"mode\":\"retained\",\"levels\":[1,2]},\"name\":\"old\",\"legacyFlag\":true}");
        org.json.JSONObject target = new org.json.JSONObject();
        ProjectMetadata.copyFields(extras, target);
        target.put("name", "edited");
        target.put("widgets", new org.json.JSONArray());
        assertEquals("edited", target.getString("name"));
        assertTrue(target.getBoolean("legacyFlag"));
        assertEquals("retained", target.getJSONObject("vendor").getString("mode"));
        assertEquals(2, target.getJSONObject("vendor").getJSONArray("levels").length());
        assertTrue(target.has("widgets"));
    }
    private static void assertRejected(String json) {
        try { ProjectDocumentValidator.validateImportedProject(json); fail("Expected rejection: " + json); }
        catch (Exception expected) { /* Invalid documents must not be accepted. */ }
    }
}
