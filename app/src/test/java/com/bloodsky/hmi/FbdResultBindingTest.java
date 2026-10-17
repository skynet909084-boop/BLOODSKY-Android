package com.bloodsky.hmi;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class FbdResultBindingTest {
    private static MainActivity.Widget widget(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        return w;
    }

    @Test public void onlyExplicitOneBasedSameScreenIndicesAreAccepted() {
        assertEquals(0, FbdResultBinding.sourceIndex("fbd://1"));
        assertEquals(14, FbdResultBinding.sourceIndex("fbd://15"));
        assertEquals(1999, FbdResultBinding.sourceIndex("fbd://2000"));
        assertEquals(0, FbdResultBinding.sourceIndex("fbd://0001"));
        for (String invalid : Arrays.asList("fbd://0", "fbd://2001", "fbd://-1",
                "fbd://+1", "fbd://1.0", "fbd://1/", "fbd://1?x=2",
                "fbd://", "fbd:// 1", "fbd://1 ", "fbd://00123", "FBD://1",
                "tcp://host:502/1/1")) {
            assertEquals("Invalid source binding must be refused: " + invalid, -1,
                    FbdResultBinding.sourceIndex(invalid));
        }
    }

    @Test public void knownBoolAndNumberOutputsHaveCorrectDisplayValues() {
        List<MainActivity.Widget> nodes = Arrays.asList(
                widget("FBD Вход"), widget("FBD POW"), widget("Индикатор"));
        boolean[] valid = {true, true, false};
        boolean[] booleans = {true, true, false};
        double[] numbers = {1, -12.5, 0};
        assertEquals("1", FbdResultBinding.resolve("fbd://1", nodes, valid, booleans, numbers));
        booleans[0] = false;
        assertEquals("0", FbdResultBinding.resolve("fbd://1", nodes, valid, booleans, numbers));
        assertEquals("-12.5", FbdResultBinding.resolve("fbd://2", nodes, valid, booleans, numbers));
        numbers[1] = Double.POSITIVE_INFINITY;
        assertEquals("CFG", FbdResultBinding.resolve("fbd://2", nodes, valid, booleans, numbers));
    }

    @Test public void invalidOrNonFbdSourcesNeverBecomeControlSignals() {
        List<MainActivity.Widget> nodes = Arrays.asList(widget("Кнопка"), widget("FBD UNKNOWN"));
        boolean[] valid = {true, true};
        boolean[] boolOutputs = {true, true};
        double[] numericOutputs = {4, 5};
        assertEquals("CFG", FbdResultBinding.resolve("fbd://1", nodes, valid, boolOutputs, numericOutputs));
        assertEquals("CFG", FbdResultBinding.resolve("fbd://2", nodes, valid, boolOutputs, numericOutputs));
        assertEquals("CFG", FbdResultBinding.resolve("fbd://3", nodes, valid, boolOutputs, numericOutputs));
        assertEquals("CFG", FbdResultBinding.resolve("fbd://bogus", nodes, valid, boolOutputs, numericOutputs));
        assertNull(FbdResultBinding.resolve("tcp://host", nodes, valid, boolOutputs, numericOutputs));
    }

    @Test public void incompleteRuntimeCachesCannotLeakOldFbdValues() {
        List<MainActivity.Widget> nodes = Arrays.asList(widget("FBD AND"), widget("Индикатор"));
        boolean[] incomplete = {false, false};
        boolean[] outputs = {true, false};
        double[] numbers = {1, 0};
        assertEquals("CFG", FbdResultBinding.resolve("fbd://1", nodes, incomplete, outputs, numbers));
        assertEquals("CFG", FbdResultBinding.resolve("fbd://1", nodes, new boolean[0], outputs, numbers));
        assertEquals("CFG", FbdResultBinding.resolve("fbd://1", nodes, incomplete, outputs, new double[0]));
        assertEquals("CFG", FbdResultBinding.resolve("fbd://1", nodes, null, outputs, numbers));
    }
}
