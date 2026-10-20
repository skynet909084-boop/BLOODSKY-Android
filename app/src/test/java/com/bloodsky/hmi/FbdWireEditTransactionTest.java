package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class FbdWireEditTransactionTest {
    private static MainActivity.FbdConnection edge(int source, int target) throws Exception {
        JSONObject json = MainActivity.connectionToJson(
                MainActivity.pinnedFbdConnection(source, "Q", target, "IN1"));
        json.put("sourceSignalType", "BOOL");
        json.put("targetSignalType", "BOOL");
        json.put("customWireData", new JSONObject().put("keep", "metadata"));
        return MainActivity.connectionFromJson(json);
    }

    @Test public void failedSaveRestoresEveryEdgeAndDoesNotAddUndoStep() throws Exception {
        Object screen = new Object();
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(edge(0, 1));
        String before = FbdWireHistory.snapshot(edges);
        FbdWireHistory history = new FbdWireHistory();

        boolean saved = FbdWireEditTransaction.apply(screen, edges, history,
                () -> edges.add(edgeUnchecked(1, 2)), () -> false);

        assertFalse(saved);
        assertEquals(before, FbdWireHistory.snapshot(edges));
        assertFalse(history.canUndo(screen));
        assertFalse(history.canRedo(screen));
    }

    @Test public void successfulSaveRecordsOriginalTypedGraphForUndo() throws Exception {
        Object screen = new Object();
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(edge(0, 1));
        String before = FbdWireHistory.snapshot(edges);
        FbdWireHistory history = new FbdWireHistory();

        assertTrue(FbdWireEditTransaction.apply(screen, edges, history,
                () -> edges.add(edgeUnchecked(1, 2)), () -> true));
        assertEquals(2, edges.size());
        assertTrue(history.canUndo(screen));
        assertTrue(history.undo(screen, edges));
        assertEquals(before, FbdWireHistory.snapshot(edges));
        JSONObject restored = MainActivity.connectionToJson(edges.get(0));
        assertEquals("Q", restored.getString("sourcePin"));
        assertEquals("IN1", restored.getString("targetPin"));
        assertEquals("metadata", restored.getJSONObject("customWireData").getString("keep"));
    }

    @Test public void saveExceptionRestoresGraphBeforePropagatingFailure() throws Exception {
        Object screen = new Object();
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(edge(0, 1));
        String before = FbdWireHistory.snapshot(edges);
        try {
            FbdWireEditTransaction.apply(screen, edges, new FbdWireHistory(),
                    () -> edges.clear(), () -> { throw new IOException("injected save failure"); });
            fail("Expected save exception");
        } catch (IOException expected) {
            assertEquals("injected save failure", expected.getMessage());
        }
        assertEquals(before, FbdWireHistory.snapshot(edges));
    }

    private static MainActivity.FbdConnection edgeUnchecked(int source, int target) {
        try { return edge(source, target); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
