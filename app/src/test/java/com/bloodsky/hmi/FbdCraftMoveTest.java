package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class FbdCraftMoveTest {
    @Test public void exactSourceOutputRequiresEnabledNamedInputsAndIntegerSignal() {
        assertNull(FbdCraftMove.output(false, true, true, 7));
        assertNull(FbdCraftMove.output(true, false, true, 7));
        assertNull(FbdCraftMove.output(true, true, false, 7));
        assertNull(FbdCraftMove.output(true, true, true, 7.5));
        assertNull(FbdCraftMove.output(true, true, true, Double.NaN));
        assertNull(FbdCraftMove.output(true, true, true, 2147483648d));
        assertEquals(Integer.valueOf(Integer.MIN_VALUE),
                FbdCraftMove.output(true, true, true, Integer.MIN_VALUE));
        assertEquals(Integer.valueOf(7), FbdCraftMove.output(true, true, true, 7));
    }

    private static MainActivity.Widget widget(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        w.text = type;
        return w;
    }

    private static boolean scan(List<MainActivity.Widget> nodes,
                                List<MainActivity.FbdConnection> edges) {
        int n = nodes.size();
        return MainActivity.evaluateFbdBlock(nodes, edges, 2, new boolean[n],
                new boolean[n], new boolean[n], new double[n]);
    }

    @Test public void namedEnableAndIntegerPinsDriveNumericSourceOutput() throws Exception {
        MainActivity.Widget sourceInteger = widget("FBD CRAFT CTU");
        sourceInteger.counterValue = 32767;
        MainActivity.Widget enable = widget("FBD Вход");
        enable.inputValue = true;
        MainActivity.Widget move = widget("FBD CRAFT MOVE");
        List<MainActivity.Widget> nodes = Arrays.asList(sourceInteger, enable, move);
        List<MainActivity.FbdConnection> edges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 2, "I"),
                MainActivity.pinnedFbdConnection(1, "Q", 2, "EN"));
        assertTrue(scan(nodes, edges));
        assertEquals(32767d, MainActivity.evaluateFbdBlockNumeric(nodes, edges, 2,
                new boolean[3], new boolean[3], new boolean[3], new double[3]), 0d);

        enable.inputValue = false;
        assertFalse(scan(nodes, edges));
        assertTrue(Double.isNaN(MainActivity.evaluateFbdBlockNumeric(nodes, edges, 2,
                new boolean[3], new boolean[3], new boolean[3], new double[3])));

        JSONObject saved = MainActivity.widgetToJson(move);
        assertEquals("FBD CRAFT MOVE", MainActivity.widgetFromJson(saved).type);
    }
}
