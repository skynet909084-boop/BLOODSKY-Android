package com.bloodsky.hmi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class FbdGraphValidatorTest {
    private JSONArray widgets(int count) throws Exception {
        JSONArray result = new JSONArray();
        for (int i = 0; i < count; i++) {
            result.put(new JSONObject().put("type", "FBD " + i));
        }
        return result;
    }

    private JSONObject edge(int source, int target) throws Exception {
        return new JSONObject().put("source", source).put("target", target);
    }

    private void assertInvalid(JSONArray widgets, JSONArray connections, String message) {
        try {
            FbdGraphValidator.validate(widgets, connections);
            fail("Expected invalid graph: " + message);
        } catch (IllegalArgumentException expected) {
            assertEquals(message, expected.getMessage());
        }
    }

    @Test
    public void acceptsAcyclicFanOutAndFanInGraph() throws Exception {
        JSONArray widgets = widgets(4);
        JSONArray edges = new JSONArray()
                .put(edge(0, 1))
                .put(edge(0, 2))
                .put(edge(1, 3))
                .put(edge(2, 3));
        FbdGraphValidator.validate(widgets, edges);
    }

    @Test
    public void rejectsDuplicateEdges() throws Exception {
        assertInvalid(widgets(2), new JSONArray().put(edge(0, 1)).put(edge(0, 1)),
                "Duplicate FBD connection");
    }

    @Test
    public void rejectsCycles() throws Exception {
        assertInvalid(widgets(3), new JSONArray().put(edge(0, 1)).put(edge(1, 2)).put(edge(2, 0)),
                "Cyclic FBD connections");
    }

    @Test
    public void rejectsOutOfRangeAndSelfConnections() throws Exception {
        assertInvalid(widgets(2), new JSONArray().put(edge(0, 2)), "Connection index out of range");
        assertInvalid(widgets(2), new JSONArray().put(edge(1, 1)), "Connection index out of range");
    }

    @Test
    public void rejectsStringAndFractionalConnectionIndices() throws Exception {
        JSONArray stringIndex = new JSONArray().put(new JSONObject()
                .put("source", "0").put("target", 1));
        assertInvalid(widgets(2), stringIndex, "Connection index out of range");

        JSONArray fractionalIndex = new JSONArray().put(new JSONObject()
                .put("source", 0).put("target", 1.5));
        assertInvalid(widgets(2), fractionalIndex, "Connection index out of range");
    }

    @Test
    public void rejectsConnectionsToNonFbdWidgets() throws Exception {
        JSONArray widgets = new JSONArray()
                .put(new JSONObject().put("type", "FBD Вход"))
                .put(new JSONObject().put("type", "Индикатор"));
        assertInvalid(widgets, new JSONArray().put(edge(0, 1)), "Connections must link FBD blocks");
    }

    @Test
    public void acceptsEmptyConnectionList() throws Exception {
        FbdGraphValidator.validate(widgets(2), new JSONArray());
    }
}
