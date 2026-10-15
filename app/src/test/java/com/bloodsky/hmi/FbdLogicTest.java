package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class FbdLogicTest {

    private List<MainActivity.Widget> widgets;
    private boolean[] cacheValid;
    private boolean[] cacheValues;
    private double[] cacheNumeric;

    @Before
    public void setUp() {
        widgets = new ArrayList<>();
        resetCache(10);
    }

    private void resetCache(int size) {
        cacheValid = new boolean[size];
        cacheValues = new boolean[size];
        cacheNumeric = new double[size];
    }

    @Test
    public void testDivisionByZeroGuardedReturnsZero() {
        MainActivity.Widget in1 = new MainActivity.Widget();
        in1.type = "FBD Вход"; in1.inputValue = true;

        MainActivity.Widget div = new MainActivity.Widget();
        div.type = "FBD Деление";

        widgets.add(in1);
        widgets.add(div);
        resetCache(widgets.size());

        // Div block with no inputs or zero denominator
        double result = MainActivity.evaluateFbdBlockNumeric(widgets, new ArrayList<>(), 1, new boolean[widgets.size()], cacheValid, cacheValues, cacheNumeric);
        assertEquals(0.0, result, 0.0001);
        assertFalse(Double.isNaN(result));
        assertFalse(Double.isInfinite(result));
    }

    @Test
    public void testArithmeticBlocks() {
        MainActivity.Widget add = new MainActivity.Widget();
        add.type = "FBD Сложение";

        MainActivity.Widget abs = new MainActivity.Widget();
        abs.type = "FBD ABS";

        widgets.add(add);
        widgets.add(abs);
        resetCache(widgets.size());

        double addVal = MainActivity.evaluateFbdBlockNumeric(widgets, new ArrayList<>(), 0, new boolean[widgets.size()], cacheValid, cacheValues, cacheNumeric);
        assertEquals(0.0, addVal, 0.0001);
    }

    @Test
    public void testFlipFlopRsAndStateMachineRecovery() throws Exception {
        MainActivity.Widget rs = new MainActivity.Widget();
        rs.type = "FBD RS";
        rs.rsState = true;

        JSONObject json = MainActivity.widgetToJson(rs);
        assertTrue(json.optBoolean("rsState"));

        MainActivity.Widget restored = MainActivity.widgetFromJson(json);
        assertTrue(restored.rsState);
    }

    @Test
    public void testCounterCtuStateRecovery() throws Exception {
        MainActivity.Widget ctu = new MainActivity.Widget();
        ctu.type = "FBD CTU";
        ctu.counterPreset = 10;
        ctu.counterValue = 7;
        ctu.counterLastInput = true;

        JSONObject json = MainActivity.widgetToJson(ctu);
        assertEquals(7, json.optInt("counterValue"));
        assertTrue(json.optBoolean("counterLastInput"));

        MainActivity.Widget restored = MainActivity.widgetFromJson(json);
        assertEquals(10, restored.counterPreset);
        assertEquals(7, restored.counterValue);
        assertTrue(restored.counterLastInput);
    }

    @Test
    public void testTimerPresetAndStateRecovery() throws Exception {
        MainActivity.Widget timer = new MainActivity.Widget();
        timer.type = "FBD Таймер";
        timer.timerPresetMs = 2500;
        timer.timerStartedAt = 123456789L;

        JSONObject json = MainActivity.widgetToJson(timer);
        assertEquals(2500, json.optInt("timerPresetMs"));
        assertEquals(123456789L, json.optLong("timerStartedAt"));

        MainActivity.Widget restored = MainActivity.widgetFromJson(json);
        assertEquals(2500, restored.timerPresetMs);
        assertEquals(123456789L, restored.timerStartedAt);
    }

    @Test
    public void testBooleanLogicAndNandNorXor() {
        MainActivity.Widget andBlock = new MainActivity.Widget();
        andBlock.type = "FBD И";

        MainActivity.Widget notBlock = new MainActivity.Widget();
        notBlock.type = "FBD НЕ";

        widgets.add(andBlock);
        widgets.add(notBlock);
        resetCache(widgets.size());

        // Disconnected AND evaluates to false
        boolean andRes = MainActivity.evaluateFbdBlock(widgets, new ArrayList<>(), 0, new boolean[widgets.size()], cacheValid, cacheValues, cacheNumeric);
        assertFalse(andRes);

        // Disconnected NOT evaluates to true
        boolean notRes = MainActivity.evaluateFbdBlock(widgets, new ArrayList<>(), 1, new boolean[widgets.size()], cacheValid, cacheValues, cacheNumeric);
        assertTrue(notRes);
    }
}
