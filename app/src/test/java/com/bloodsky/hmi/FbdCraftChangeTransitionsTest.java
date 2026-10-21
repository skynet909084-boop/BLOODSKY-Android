package com.bloodsky.hmi;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class FbdCraftChangeTransitionsTest {
    private static MainActivity.Widget widget(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        w.text = type;
        return w;
    }

    private static boolean scan(List<MainActivity.Widget> nodes,
                                List<MainActivity.FbdConnection> connections) {
        FbdCraftTestGraphs.Graph graph = FbdCraftTestGraphs.withReadyCraftCounters(nodes, connections);
        int n = graph.widgets.size();
        return MainActivity.evaluateFbdBlock(graph.widgets, graph.connections, 1,
                new boolean[n], new boolean[n], new boolean[n], new double[n]);
    }

    @Test public void originalIntegerChangeIsOneEvaluationBooleanPulse() {
        FbdCraftChangeTransitions.IntState s = FbdCraftChangeTransitions.IntState.initial();
        s = FbdCraftChangeTransitions.nextInt(s, 0);
        assertFalse(s.changed);
        s = FbdCraftChangeTransitions.nextInt(s, 3);
        assertTrue(s.changed);
        assertEquals(3, s.last);
        s = FbdCraftChangeTransitions.nextInt(s, 3);
        assertFalse(s.changed);
        s = FbdCraftChangeTransitions.nextInt(s, -5);
        assertTrue(s.changed);
        s = FbdCraftChangeTransitions.nextInt(s, -5);
        assertFalse(s.changed);
        s = FbdCraftChangeTransitions.nextInt(s, Integer.MAX_VALUE);
        assertTrue(s.changed);
    }

    @Test public void originalFloatUsesJavaFloat32ChangeNotDoubleJitter() {
        FbdCraftChangeTransitions.FloatState s = FbdCraftChangeTransitions.FloatState.initial();
        s = FbdCraftChangeTransitions.nextFloat(s, 1.0f);
        assertTrue(s.changed);
        s = FbdCraftChangeTransitions.nextFloat(s, (float)1.000000001d);
        assertFalse("Sub-float precision jitter must not pulse", s.changed);
        s = FbdCraftChangeTransitions.nextFloat(s, -2.5f);
        assertTrue(s.changed);
        s = FbdCraftChangeTransitions.nextFloat(s, -2.5f);
        assertFalse(s.changed);
        try {
            FbdCraftChangeTransitions.nextFloat(s, Float.NaN);
            fail("Nonfinite signal accepted");
        } catch (IllegalArgumentException expected) {}
        try {
            FbdCraftChangeTransitions.nextFloat(s, Float.POSITIVE_INFINITY);
            fail("Nonfinite signal accepted");
        } catch (IllegalArgumentException expected) {}
    }

    @Test public void nativeIntegerGraphEmitsPulseOnlyWhenInputValueChanges() throws Exception {
        MainActivity.Widget source = widget("FBD CRAFT CTU");
        MainActivity.Widget change = widget("FBD CRAFT CHANGE I");
        List<MainActivity.Widget> nodes = Arrays.asList(source, change);
        List<MainActivity.FbdConnection> wires = Collections.singletonList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "I"));
        assertFalse(scan(nodes, wires));
        source.counterValue = 123;
        assertTrue(scan(nodes, wires));
        assertFalse(scan(nodes, wires));
        source.counterValue = 0;
        assertTrue(scan(nodes, wires));
        assertFalse(scan(nodes, wires));
        // Disconnected source cannot produce an apparent transition.
        source.counterValue = 56;
        assertFalse(scan(nodes, Collections.emptyList()));
        assertTrue("Previous value remains cached for genuine reconnection",
                scan(nodes, wires));
    }

    @Test public void floatGraphUsesSameSourcePinWithFloat32Casting() throws Exception {
        MainActivity.Widget source = widget("FBD CRAFT CTU");
        MainActivity.Widget change = widget("FBD CRAFT CHANGE F");
        List<MainActivity.Widget> nodes = Arrays.asList(source, change);
        List<MainActivity.FbdConnection> wires = Collections.singletonList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "F"));
        source.counterValue = 65535;
        assertTrue(scan(nodes, wires));
        assertFalse(scan(nodes, wires));
        source.counterValue = 0;
        assertTrue(scan(nodes, wires));
    }

    @Test public void sourcePinsTypesAndTransientStateSurviveProjectLoad() throws Exception {
        assertArrayEquals(new String[]{"I"},
                MainActivity.knownFbdPins("FBD CRAFT CHANGE I", false));
        assertArrayEquals(new String[]{"F"},
                MainActivity.knownFbdPins("FBD CRAFT CHANGE F", false));
        for (String t : new String[]{"FBD CRAFT CHANGE I", "FBD CRAFT CHANGE F"}) {
            assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins(t, true));
            assertEquals("BOOL", MainActivity.knownFbdPinType(t, "Q", true));
            String input = t.endsWith(" I") ? "I" : "F";
            assertEquals("NUMBER", MainActivity.knownFbdPinType(t, input, false));
            assertArrayEquals(new String[]{input},
                    MainActivity.compatibleFbdTargetPins("FBD CRAFT CTU", "Q", t));
            MainActivity.Widget w = widget(t);
            w.craftChangeIntState = new FbdCraftChangeTransitions.IntState(7, true);
            w.craftChangeFloatState = new FbdCraftChangeTransitions.FloatState(2.5f, true);
            w.extraFields.put("vendor", "retain");
            MainActivity.Widget imported = MainActivity.widgetFromJson(MainActivity.widgetToJson(w));
            assertEquals(t, imported.type);
            assertEquals("retain", imported.extraFields.getString("vendor"));
            assertEquals(0, imported.craftChangeIntState.last);
            assertFalse(imported.craftChangeIntState.changed);
            assertEquals(0f, imported.craftChangeFloatState.last, 0f);
            assertFalse(imported.craftChangeFloatState.changed);
        }
    }

    @Test public void existingGenericFbdBlocksHaveNoBehaviorChange() {
        assertArrayEquals(new String[]{"CU", "R", "PV"},
                MainActivity.knownFbdPins("FBD CTU", false));
        assertArrayEquals(new String[]{"G", "IN0", "IN1"},
                MainActivity.knownFbdPins("FBD SEL", false));
    }
}
