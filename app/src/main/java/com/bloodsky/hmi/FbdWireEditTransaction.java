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

    private static void restore(List<MainActivity.FbdConnection> edges,
                                List<MainActivity.FbdConnection> previous) {
        edges.clear();
        edges.addAll(previous);
    }
}
