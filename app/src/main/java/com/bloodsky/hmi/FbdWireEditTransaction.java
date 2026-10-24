package com.bloodsky.hmi;

import java.util.List;

/** Save a validated FBD wire edit atomically with respect to the in-memory graph. */
public final class FbdWireEditTransaction {
    @FunctionalInterface
    public interface Save {
        boolean persist() throws Exception;
    }

    private FbdWireEditTransaction() {}

    /**
     * Commits only after a successful save. Failed saves or exceptions restore the
     * complete serialized edge graph; undo history records only accepted edits.
     */
    public static boolean apply(Object screenKey,
                                List<MainActivity.FbdConnection> edges,
                                FbdWireHistory history,
                                Runnable mutation,
                                Save save) throws Exception {
        if (screenKey == null || edges == null || history == null || mutation == null || save == null) {
            throw new IllegalArgumentException("Missing FBD transaction input");
        }
        String before = FbdWireHistory.snapshot(edges);
        List<MainActivity.FbdConnection> previous = FbdWireHistory.deserialize(before);
        try {
            mutation.run();
            if (!save.persist()) {
                restore(edges, previous);
                return false;
            }
        } catch (Exception failed) {
            restore(edges, previous);
            throw failed;
        }
        history.recordSnapshotBefore(screenKey, before);
        return true;
    }

    /**
     * Replay one Undo/Redo step and commit it only if the project is saved.
     * On persistence failure the inverse history operation restores both the
     * in-memory typed edge graph and the original Undo/Redo stacks.
     */
    public static boolean replay(Object screenKey,
                                 List<MainActivity.FbdConnection> edges,
                                 FbdWireHistory history, boolean redo,
                                 Save save) throws Exception {
        if (screenKey == null || edges == null || history == null || save == null)
            throw new IllegalArgumentException("Missing FBD replay transaction input");

        String before = FbdWireHistory.snapshot(edges);
        boolean changed = redo ? history.redo(screenKey, edges) : history.undo(screenKey, edges);
        if (!changed) return false;
        try {
            if (save.persist()) return true;
        } catch (Exception failure) {
            rollbackReplay(screenKey, edges, history, redo, before);
            throw failure;
        }
        rollbackReplay(screenKey, edges, history, redo, before);
        return false;
    }

    private static void rollbackReplay(Object screenKey,
                                       List<MainActivity.FbdConnection> edges,
                                       FbdWireHistory history, boolean redo,
                                       String before) throws Exception {
        boolean reversed = redo ? history.undo(screenKey, edges) : history.redo(screenKey, edges);
        if (!reversed || !before.equals(FbdWireHistory.snapshot(edges))) {
            restore(edges, FbdWireHistory.deserialize(before));
            throw new IllegalStateException("Unable to restore the original FBD replay history");
        }
    }

    private static void restore(List<MainActivity.FbdConnection> edges,
                                List<MainActivity.FbdConnection> previous) {
        edges.clear();
        edges.addAll(previous);
    }
}
