package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class FbdCraftBlinkGraphTest {
    private static MainActivity.Widget widget(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        w.text = type;
        return w;
    }

    private static boolean scan(List<MainActivity.Widget> nodes,
                                List<MainActivity.FbdConnection> edges) {
        int n = nodes.size();
        return MainActivity.evaluateFbdBlock(nodes, edges, 1, new boolean[n],
                new boolean[n], new boolean[n], new double[n]);
    }

    @Test public void sourceCompatibleBlinkPinsAreTypedAndWired() {
        assertArrayEquals(new String[]{"IN", "PT"},
                MainActivity.knownFbdPins("FBD CRAFT BLINK", false));
        assertArrayEquals(new String[]{"Q"},
                MainActivity.knownFbdPins("FBD CRAFT BLINK", true));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CRAFT BLINK", "Q", true));
        assertEquals("BOOL", MainActivity.knownFbdPinType("FBD CRAFT BLINK", "IN", false));
        assertEquals("NUMBER", MainActivity.knownFbdPinType("FBD CRAFT BLINK", "PT", false));
        assertArrayEquals(new String[]{"IN"},
                MainActivity.compatibleFbdTargetPins("FBD Вход", "Q", "FBD CRAFT BLINK"));
        assertArrayEquals(new String[]{"PT"},
                MainActivity.compatibleFbdTargetPins("FBD CRAFT CTU", "Q", "FBD CRAFT BLINK"));
        assertEquals(0.5f, MainActivity.fbdTerminalFraction("FBD CRAFT BLINK", "IN", false), 0.00001f);
        assertEquals(5f/6f, MainActivity.fbdTerminalFraction("FBD CRAFT BLINK", "PT", false), 0.00001f);
    }

    @Test public void operatorClockAlternatesAndLowResetsNativeBlink() throws Exception {
        MainActivity.Widget input = widget("FBD Вход");
        MainActivity.Widget blink = widget("FBD CRAFT BLINK");
        blink.timerPresetMs = 1000;
        List<MainActivity.Widget> nodes = Arrays.asList(input, blink);
        List<MainActivity.FbdConnection> edges = Collections.singletonList(
                MainActivity.pinnedFbdConnection(0, "Q", 1, "IN"));

        assertFalse(scan(nodes, edges));
        input.inputValue = true;
        assertTrue("BLINK starts in its ON phase", scan(nodes, edges));
        assertTrue("Immediate next scan stays ON", scan(nodes, edges));

        blink.craftBlinkState = new FbdCraftNativeTimer.State(
                true, true, true, System.currentTimeMillis() - 2000);
        assertFalse("Passed preset forces ON->OFF transition", scan(nodes, edges));
        assertFalse("Intermediate restart tick stays OFF", scan(nodes, edges));
        blink.craftBlinkState = new FbdCraftNativeTimer.State(
                false, true, true, System.currentTimeMillis() - 2000);
        assertTrue("Next expired OFF phase becomes ON", scan(nodes, edges));

        input.inputValue = false;
        assertFalse(scan(nodes, edges));
        assertFalse(blink.craftBlinkState.armed);
        assertFalse(blink.craftBlinkState.started);
        input.inputValue = true;
        assertTrue(scan(nodes, edges));
    }

    @Test public void editingOrLoadingResetsTransientClockWithoutErasingPresetOrVendorData() throws Exception {
        MainActivity.Widget blink = widget("FBD CRAFT BLINK");
        blink.timerPresetMs = 2345;
        blink.craftBlinkState = new FbdCraftNativeTimer.State(true, true, true, 123456);
        blink.extraFields.put("bloodskyFbdTimeUnit", "MS");
        blink.extraFields.put("fromCraftProject", new JSONObject().put("opaque", 9));

        JSONObject saved = MainActivity.widgetToJson(blink);
        assertFalse(saved.has("craftBlinkState"));
        MainActivity.Widget loaded = MainActivity.widgetFromJson(saved);
        assertEquals(2345, loaded.timerPresetMs);
        assertFalse(loaded.craftBlinkState.output);
        assertFalse(loaded.craftBlinkState.armed);
        assertEquals(9, loaded.extraFields.getJSONObject("fromCraftProject").getInt("opaque"));
        loaded.craftBlinkState = new FbdCraftNativeTimer.State(true, true, true, 123456);
        MainActivity.resetFbdTimerRuntime(loaded);
        assertFalse(loaded.craftBlinkState.output);
        assertFalse(loaded.craftBlinkState.started);
        assertFalse(loaded.craftBlinkState.armed);
        assertEquals(2345, loaded.timerPresetMs);
    }

    @Test public void existingBloodskyTimerContractUntouched() {
        for (String type : new String[]{"FBD TON", "FBD TOF", "FBD TP"}) {
            assertArrayEquals(new String[]{"IN", "PT"}, MainActivity.knownFbdPins(type, false));
            assertArrayEquals(new String[]{"Q"}, MainActivity.knownFbdPins(type, true));
        }
        assertEquals(60000L, FbdCraftNativeTimer.VERIFIED_MINUTE_MS);
    }
}
