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

    @Test public void failedDisconnectSaveRestoresSelectedTypedEdge() throws Exception {
        Object screen = new Object();
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        edges.add(edge(0, 1));
        String before = FbdWireHistory.snapshot(edges);
        FbdWireHistory history = new FbdWireHistory();

        boolean saved = FbdWireEditTransaction.apply(screen, edges, history,
                () -> edges.remove(0), () -> false);

        assertFalse(saved);
        assertEquals(before, FbdWireHistory.snapshot(edges));
        JSONObject restored = MainActivity.connectionToJson(edges.get(0));
        assertEquals("Q", restored.getString("sourcePin"));
        assertEquals("IN1", restored.getString("targetPin"));
        assertEquals("metadata", restored.getJSONObject("customWireData").getString("keep"));
        assertFalse(history.canUndo(screen));
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

    @Test public void failedUndoSaveRestoresOriginalEdgesAndUndoTimeline() throws Exception {
        Object screen = new Object();
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        FbdWireHistory history = new FbdWireHistory();
        history.recordBefore(screen, edges);
        edges.add(edge(0, 1));
        String before = FbdWireHistory.snapshot(edges);

        assertFalse(FbdWireEditTransaction.replay(screen, edges, history, false, () -> false));
        assertEquals(before, FbdWireHistory.snapshot(edges));
        assertTrue(history.canUndo(screen));
        assertFalse(history.canRedo(screen));

        assertTrue(FbdWireEditTransaction.replay(screen, edges, history, false, () -> true));
        assertTrue(edges.isEmpty());
        assertFalse(history.canUndo(screen));
        assertTrue(history.canRedo(screen));
    }

    @Test public void failedRedoSaveLeavesRedoAndTypedGraphUnchanged() throws Exception {
        Object screen = new Object();
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        FbdWireHistory history = new FbdWireHistory();
        history.recordBefore(screen, edges);
        edges.add(edge(2, 3));
        assertTrue(history.undo(screen, edges));
        assertTrue(edges.isEmpty());

        assertFalse(FbdWireEditTransaction.replay(screen, edges, history, true, () -> false));
        assertTrue(edges.isEmpty());
        assertTrue(history.canRedo(screen));
        assertFalse(history.canUndo(screen));

        assertTrue(FbdWireEditTransaction.replay(screen, edges, history, true, () -> true));
        assertEquals(1, edges.size());
        assertEquals("BOOL", MainActivity.connectionToJson(edges.get(0))
                .getString("targetSignalType"));
        assertTrue(history.canUndo(screen));
        assertFalse(history.canRedo(screen));
    }

    @Test public void saveExceptionRollsBackReplayWithoutLosingOtherScreensHistory()
            throws Exception {
        Object first = new Object(), second = new Object();
        List<MainActivity.FbdConnection> firstEdges = new ArrayList<>();
        List<MainActivity.FbdConnection> secondEdges = new ArrayList<>();
        FbdWireHistory history = new FbdWireHistory();
        history.recordBefore(first, firstEdges);
        firstEdges.add(edge(0, 1));
        history.recordBefore(second, secondEdges);
        secondEdges.add(edge(3, 4));
        String before = FbdWireHistory.snapshot(firstEdges);

        try {
            FbdWireEditTransaction.replay(first, firstEdges, history, false,
                    () -> { throw new IOException("disk full"); });
            fail("Expected storage failure");
        } catch (IOException expected) {
            assertEquals("disk full", expected.getMessage());
        }
        assertEquals(before, FbdWireHistory.snapshot(firstEdges));
        assertTrue(history.canUndo(first));
        assertTrue(history.canUndo(second));
        assertEquals(1, secondEdges.size());
    }

    @Test public void noReplayHistoryNeverCallsPersistence() throws Exception {
        Object screen = new Object();
        FbdWireHistory history = new FbdWireHistory();
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        final int[] writes = {0};
        assertFalse(FbdWireEditTransaction.replay(screen, edges, history, false,
                () -> { writes[0]++; return true; }));
        assertEquals(0, writes[0]);
    }

    private static MainActivity.FbdConnection edgeUnchecked(int source, int target) {
        try { return edge(source, target); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
