package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class FbdDisconnectTest {
    @Test public void removesOnlySelectedDirectedEdgeAndKeepsMetadata() throws Exception {
        MainActivity.FbdConnection first = MainActivity.connectionFromJson(
                new JSONObject("{\"source\":1,\"target\":2,\"vendor\":{\"pin\":\"A\"}}"));
        MainActivity.FbdConnection second = MainActivity.connectionFromJson(
                new JSONObject("{\"source\":2,\"target\":1,\"vendor\":{\"pin\":\"B\"}}"));
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(first);
        edges.add(second);
        assertTrue(MainActivity.disconnectFbdEdge(edges, 1, 2));
        assertEquals(1, edges.size());
        assertSame(second, edges.get(0));
        assertEquals("B", MainActivity.connectionToJson(edges.get(0))
                .getJSONObject("vendor").getString("pin"));
        assertFalse(MainActivity.disconnectFbdEdge(edges, 1, 2));
        assertEquals(1, edges.size());
    }

    @Test public void identitySelectionRemovesOnlyOneDuplicate() throws Exception {
        MainActivity.FbdConnection first = MainActivity.connectionFromJson(
                new JSONObject("{\\"source\\":0,\\"target\\":1,\\"note\\":\\"first\\"}"));
        MainActivity.FbdConnection second = MainActivity.connectionFromJson(
                new JSONObject("{\\"source\\":0,\\"target\\":1,\\"note\\":\\"second\\"}"));
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(first);
        edges.add(second);
        assertTrue(MainActivity.disconnectFbdEdge(edges, second));
        assertEquals(1, edges.size());
        assertSame(first, edges.get(0));
        assertEquals("first", MainActivity.connectionToJson(first).getString("note"));
        assertFalse(MainActivity.disconnectFbdEdge(edges, second));
    }

    @Test public void invalidEndpointsDoNotMutateGraph() {
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(new MainActivity.FbdConnection(0, 1));
        assertFalse(MainActivity.disconnectFbdEdge(edges, -1, 1));
        assertFalse(MainActivity.disconnectFbdEdge(edges, 0, -1));
        assertFalse(MainActivity.disconnectFbdEdge(null, 0, 1));
        assertEquals(1, edges.size());
    }
}
