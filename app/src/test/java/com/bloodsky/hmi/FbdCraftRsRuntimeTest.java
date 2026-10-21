package com.bloodsky.hmi;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Exercises the source RS transition through the same graph evaluator used by FBD scans. */
public class FbdCraftRsRuntimeTest {
    private static MainActivity.Widget block(String type) {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = type;
        widget.text = type;
        return widget;
    }

    private static boolean scan(List<MainActivity.Widget> widgets,
                                List<MainActivity.FbdConnection> connections) {
        int size = widgets.size();
        return MainActivity.evaluateFbdBlock(widgets, connections, 2,
                new boolean[size], new boolean[size], new boolean[size], new double[size]);
    }

    @Test public void craftRsIsResetDominantInGraphAndRetainsStateAfterReopen() throws Exception {
        MainActivity.Widget reset = block("FBD Вход");
        MainActivity.Widget set = block("FBD Вход");
        MainActivity.Widget latch = block("FBD RS");
        reset.inputValue = true;
        set.inputValue = true;
        latch.rsState = true;
        List<MainActivity.Widget> widgets = Arrays.asList(reset, set, latch);
        // Reverse insertion order to ensure named pins, not list order, select R and S.
        List<MainActivity.FbdConnection> wires = Arrays.asList(
                MainActivity.pinnedFbdConnection(1, "Q", 2, "S"),
                MainActivity.pinnedFbdConnection(0, "Q", 2, "R"));

        assertFalse("Craft executes S then R, so reset wins when both are true",
                scan(widgets, wires));
        reset.inputValue = false;
        assertTrue("Set establishes the latch after reset is released", scan(widgets, wires));

        MainActivity.Widget reopened = MainActivity.widgetFromJson(MainActivity.widgetToJson(latch));
        widgets.set(2, reopened);
        assertTrue("Saved RS output remains set after project reopen", scan(widgets, wires));
        reset.inputValue = true;
        assertFalse("Reset still wins after reopen", scan(widgets, wires));
    }
}
