package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class FbdWireHistoryTest {
    private static MainActivity.FbdConnection edge(int source, String sourcePin,
                                                    int target, String targetPin) throws Exception {
        MainActivity.FbdConnection e = MainActivity.pinnedFbdConnection(source, sourcePin, target, targetPin);
        e = MainActivity.connectionFromJson(MainActivity.connectionToJson(e)
                .put("customOriginalMetadata", new JSONObject().put("nested", "сохранить"))
                .put("sourceSignalType", "BOOL").put("targetSignalType", "BOOL"));
        return e;
    }

    @Test public void undoRedoPreservesUnknownMetadataAndOriginalPinSpelling() throws Exception {
        Object screen = new Object();
        FbdWireHistory history = new FbdWireHistory();
        List<MainActivity.FbdConnection> wires = new ArrayList<>();
        MainActivity.FbdConnection first = edge(0, "Q", 1, " in1 ");
        history.recordBefore(screen, wires);
        wires.add(first);

        assertTrue(history.canUndo(screen));
        assertFalse(history.canRedo(screen));
        assertTrue(history.undo(screen, wires));
        assertTrue(wires.isEmpty());
        assertTrue(history.canRedo(screen));
        assertTrue(history.redo(screen, wires));
        assertEquals(1, wires.size());
        JSONObject restored = MainActivity.connectionToJson(wires.get(0));
        assertEquals(" in1 ", restored.getString("targetPin"));
        assertEquals("сохранить", restored.getJSONObject("customOriginalMetadata").getString("nested"));
        assertEquals("BOOL", restored.getString("sourceSignalType"));
        assertEquals(0, restored.getInt("source"));
    }

    @Test public void deletingOneWireCanBeUndoneWithoutChangingOthers() throws Exception {
        Object screen = new Object();
        FbdWireHistory history = new FbdWireHistory();
        List<MainActivity.FbdConnection> wires = new ArrayList<>();
        wires.add(edge(0, "Q", 2, "IN1"));
        wires.add(edge(1, "Q", 2, "IN2"));
        history.recordBefore(screen, wires);
        assertTrue(MainActivity.disconnectFbdEdge(wires, wires.get(0)));
        assertEquals(1, wires.size());
        assertTrue(history.undo(screen, wires));
        assertEquals(2, wires.size());
        assertEquals("IN1", MainActivity.connectionToJson(wires.get(0)).getString("targetPin"));
        assertEquals("IN2", MainActivity.connectionToJson(wires.get(1)).getString("targetPin"));
        assertTrue(history.redo(screen, wires));
        assertEquals(1, wires.size());
        assertEquals("IN2", MainActivity.connectionToJson(wires.get(0)).getString("targetPin"));
    }

    @Test public void newEditAfterUndoDiscardsRedoChain() throws Exception {
        Object screen = new Object();
        FbdWireHistory history = new FbdWireHistory();
        List<MainActivity.FbdConnection> wires = new ArrayList<>();
        history.recordBefore(screen, wires);
        wires.add(edge(0, "Q", 2, "IN1"));
        assertTrue(history.undo(screen, wires));
        assertTrue(history.canRedo(screen));
        history.recordBefore(screen, wires);
        wires.add(edge(1, "Q", 2, "IN2"));
        assertFalse(history.canRedo(screen));
        assertTrue(history.undo(screen, wires));
        assertEquals(0, wires.size());
    }

    @Test public void screenAndProjectChangesCannotReplayAnotherGraphsHistory() throws Exception {
        Object firstScreen = new Object();
        Object secondScreen = new Object();
        FbdWireHistory history = new FbdWireHistory();
        List<MainActivity.FbdConnection> first = new ArrayList<>();
        List<MainActivity.FbdConnection> second = new ArrayList<>();
        history.recordBefore(firstScreen, first);
        first.add(edge(0, "Q", 1, "IN1"));
        assertFalse(history.undo(secondScreen, second));
        assertTrue(history.canUndo(firstScreen));
        history.clear();
        assertFalse(history.canUndo(firstScreen));
        history.recordBefore(secondScreen, second);
        second.add(edge(2, "Q", 3, "IN2"));
        assertFalse(history.canUndo(firstScreen));
        assertTrue(history.undo(secondScreen, second));
        assertEquals(0, second.size());
        assertEquals(1, first.size());
    }

    @Test public void historyIsBoundedAndOnlyRestoresMostRecent32Steps() throws Exception {
        Object screen = new Object();
        FbdWireHistory history = new FbdWireHistory();
        List<MainActivity.FbdConnection> wires = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            history.recordBefore(screen, wires);
            wires.add(edge(i, "Q", i + 100, "IN1"));
        }
        int undone = 0;
        while (history.undo(screen, wires)) undone++;
        assertEquals(32, undone);
        assertEquals(8, wires.size());
        int redone = 0;
        while (history.redo(screen, wires)) redone++;
        assertEquals(32, redone);
        assertEquals(40, wires.size());
    }

    @Test public void invalidSnapshotDoesNotMutateLiveGraph() throws Exception {
        List<MainActivity.FbdConnection> wires = new ArrayList<>();
        wires.add(edge(0, "Q", 1, "IN1"));
        String snapshot = FbdWireHistory.snapshot(wires);
        assertEquals(1, FbdWireHistory.deserialize(snapshot).size());
        try {
            FbdWireHistory.deserialize("[{\"source\":1,\"target\":1}]");
            fail("A self-edge must not restore");
        } catch (IllegalArgumentException expected) { }
        assertEquals(1, wires.size());
    }
}
