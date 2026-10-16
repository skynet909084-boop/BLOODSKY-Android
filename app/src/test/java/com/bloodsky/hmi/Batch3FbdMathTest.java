package com.bloodsky.hmi;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Batch3FbdMathTest {

    private MainActivity.Widget createFbdWidget(String type, double compareA) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        w.text = type;
        w.compareA = compareA;
        return w;
    }

    @Test
    public void testFbdPowEvaluation() {
        List<MainActivity.Widget> widgets = new ArrayList<>();
        List<MainActivity.FbdConnection> connections = new ArrayList<>();

        MainActivity.Widget inBase = createFbdWidget("FBD MOVE", 2.0);
        MainActivity.Widget inExp = createFbdWidget("FBD MOVE", 3.0);
        MainActivity.Widget powBlock = createFbdWidget("FBD POW", 0.0);

        widgets.add(inBase);
        widgets.add(inExp);
        widgets.add(powBlock);

        connections.add(new MainActivity.FbdConnection(0, 2));
        connections.add(new MainActivity.FbdConnection(1, 2));

        boolean[] visiting = new boolean[3];
        boolean[] cacheValid = new boolean[3];
        boolean[] cacheValues = new boolean[3];
        double[] cacheNumeric = new double[3];

        boolean result = MainActivity.evaluateFbdBlock(widgets, connections, 2, visiting, cacheValid, cacheValues, cacheNumeric);
        assertTrue(result);
        assertEquals(8.0, cacheNumeric[2], 0.001);
    }

    @Test
    public void testFbdSqrtEvaluation() {
        List<MainActivity.Widget> widgets = new ArrayList<>();
        List<MainActivity.FbdConnection> connections = new ArrayList<>();

        MainActivity.Widget inVal = createFbdWidget("FBD MOVE", 16.0);
        MainActivity.Widget sqrtBlock = createFbdWidget("FBD SQRT", 0.0);

        widgets.add(inVal);
        widgets.add(sqrtBlock);

        connections.add(new MainActivity.FbdConnection(0, 1));

        boolean[] visiting = new boolean[2];
        boolean[] cacheValid = new boolean[2];
        boolean[] cacheValues = new boolean[2];
        double[] cacheNumeric = new double[2];

        boolean result = MainActivity.evaluateFbdBlock(widgets, connections, 1, visiting, cacheValid, cacheValues, cacheNumeric);
        assertTrue(result);
        assertEquals(4.0, cacheNumeric[1], 0.001);

        // Test clamped negative input
        widgets.set(0, createFbdWidget("FBD MOVE", -9.0));
        visiting = new boolean[2];
        cacheValid = new boolean[2];
        cacheValues = new boolean[2];
        cacheNumeric = new double[2];

        result = MainActivity.evaluateFbdBlock(widgets, connections, 1, visiting, cacheValid, cacheValues, cacheNumeric);
        assertFalse(result);
        assertEquals(0.0, cacheNumeric[1], 0.001);
    }

    @Test
    public void testFbdTrigEvaluation() {
        List<MainActivity.Widget> widgets = new ArrayList<>();
        List<MainActivity.FbdConnection> connections = new ArrayList<>();

        MainActivity.Widget inVal = createFbdWidget("FBD MOVE", 0.0);
        MainActivity.Widget sinBlock = createFbdWidget("FBD SIN", 0.0);

        widgets.add(inVal);
        widgets.add(sinBlock);
        connections.add(new MainActivity.FbdConnection(0, 1));

        boolean[] visiting = new boolean[2];
        boolean[] cacheValid = new boolean[2];
        boolean[] cacheValues = new boolean[2];
        double[] cacheNumeric = new double[2];

        MainActivity.evaluateFbdBlock(widgets, connections, 1, visiting, cacheValid, cacheValues, cacheNumeric);
        assertEquals(0.0, cacheNumeric[1], 0.001);
    }

    @Test
    public void testFbdConversionEvaluation() {
        List<MainActivity.Widget> widgets = new ArrayList<>();
        List<MainActivity.FbdConnection> connections = new ArrayList<>();

        MainActivity.Widget inVal = createFbdWidget("FBD MOVE", 7.89);
        MainActivity.Widget floatToInt = createFbdWidget("FBD FloatToInt", 0.0);

        widgets.add(inVal);
        widgets.add(floatToInt);
        connections.add(new MainActivity.FbdConnection(0, 1));

        boolean[] visiting = new boolean[2];
        boolean[] cacheValid = new boolean[2];
        boolean[] cacheValues = new boolean[2];
        double[] cacheNumeric = new double[2];

        MainActivity.evaluateFbdBlock(widgets, connections, 1, visiting, cacheValid, cacheValues, cacheNumeric);
        assertEquals(7.0, cacheNumeric[1], 0.001);
    }
}
