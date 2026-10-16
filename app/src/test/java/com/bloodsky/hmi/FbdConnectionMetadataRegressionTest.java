package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Tests the connection codec used by MainActivity.loadProject/persistProject. */
public class FbdConnectionMetadataRegressionTest {
    private static JSONObject edge() throws Exception {
        return new JSONObject()
                .put("source", 0).put("target", 1)
                .put("vendorConnectionOption", new JSONObject()
                        .put("route", "orthogonal")
                        .put("bends", new JSONArray().put(12).put(34)))
                .put("futureFlag", true)
                .put("optionalField", JSONObject.NULL);
    }

    @Test public void unknownNestedMetadataSurvivesEditedEndpointsAndReopen() throws Exception {
        MainActivity.FbdConnection connection = MainActivity.connectionFromJson(edge());
        connection.source = 2;
        connection.target = 3;
        JSONObject saved = MainActivity.connectionToJson(connection);
        MainActivity.FbdConnection reopened = MainActivity.connectionFromJson(new JSONObject(saved.toString()));
        JSONObject secondSave = MainActivity.connectionToJson(reopened);
        assertEquals(2, reopened.source);
        assertEquals(3, reopened.target);
        assertEquals("orthogonal", secondSave.getJSONObject("vendorConnectionOption").getString("route"));
        assertEquals(34, secondSave.getJSONObject("vendorConnectionOption").getJSONArray("bends").getInt(1));
        assertTrue(secondSave.getBoolean("futureFlag"));
        assertTrue(secondSave.has("optionalField"));
        assertTrue(secondSave.isNull("optionalField"));
    }

    @Test public void importedMetadataDoesNotShareMutableObjects() throws Exception {
        JSONObject original = edge();
        MainActivity.FbdConnection decoded = MainActivity.connectionFromJson(original);
        original.getJSONObject("vendorConnectionOption").put("route", "changed");
        original.getJSONObject("vendorConnectionOption").getJSONArray("bends").put(0, 999);
        JSONObject saved = MainActivity.connectionToJson(decoded);
        assertEquals("orthogonal", saved.getJSONObject("vendorConnectionOption").getString("route"));
        assertEquals(12, saved.getJSONObject("vendorConnectionOption").getJSONArray("bends").getInt(0));
    }

    @Test public void serializedMetadataDoesNotMutateConnectionOrNextSave() throws Exception {
        MainActivity.FbdConnection decoded = MainActivity.connectionFromJson(edge());
        JSONObject first = MainActivity.connectionToJson(decoded);
        first.getJSONObject("vendorConnectionOption").getJSONArray("bends").put(0, 999);
        first.put("futureFlag", false);
        JSONObject next = MainActivity.connectionToJson(decoded);
        assertEquals(12, next.getJSONObject("vendorConnectionOption").getJSONArray("bends").getInt(0));
        assertTrue(next.getBoolean("futureFlag"));
    }

    @Test public void reindexedEndpointsOverrideOldImportedValues() throws Exception {
        MainActivity.FbdConnection decoded = MainActivity.connectionFromJson(edge().put("source", 3).put("target", 5));
        // Same endpoint adjustment used when deleting an earlier unrelated widget.
        decoded.source--;
        decoded.target--;
        JSONObject saved = MainActivity.connectionToJson(decoded);
        assertEquals(2, saved.getInt("source"));
        assertEquals(4, saved.getInt("target"));
        assertTrue(saved.has("vendorConnectionOption"));
    }

    @Test public void separateScreenConnectionsDoNotShareMetadata() throws Exception {
        JSONObject input = edge();
        MainActivity.FbdConnection screenOne = MainActivity.connectionFromJson(input);
        input.getJSONObject("vendorConnectionOption").put("route", "direct");
        MainActivity.FbdConnection screenTwo = MainActivity.connectionFromJson(input);
        assertEquals("orthogonal", MainActivity.connectionToJson(screenOne)
                .getJSONObject("vendorConnectionOption").getString("route"));
        assertEquals("direct", MainActivity.connectionToJson(screenTwo)
                .getJSONObject("vendorConnectionOption").getString("route"));
    }

    @Test public void newlyCreatedConnectionHasOnlyCurrentEndpoints() throws Exception {
        JSONObject saved = MainActivity.connectionToJson(new MainActivity.FbdConnection(4, 8));
        assertEquals(2, saved.length());
        assertEquals(4, saved.getInt("source"));
        assertEquals(8, saved.getInt("target"));
    }

    @Test public void preservesMetadataAcrossMultipleProjectSerializations() throws Exception {
        JSONObject project = new JSONObject().put("screens", new JSONArray().put(
                new JSONObject().put("connections", new JSONArray().put(edge()))));
        JSONObject serialized = new JSONObject(project.toString());
        JSONObject loadedEdge = serialized.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("connections").getJSONObject(0);
        MainActivity.FbdConnection edited = MainActivity.connectionFromJson(loadedEdge);
        edited.target = 7;
        serialized.getJSONArray("screens").getJSONObject(0)
                .put("connections", new JSONArray().put(MainActivity.connectionToJson(edited)));
        JSONObject reopened = new JSONObject(serialized.toString()).getJSONArray("screens")
                .getJSONObject(0).getJSONArray("connections").getJSONObject(0);
        assertEquals(7, reopened.getInt("target"));
        assertEquals("orthogonal", reopened.getJSONObject("vendorConnectionOption").getString("route"));
    }
}
