package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class FbdCraftTypedWireContractsTest {
    @Test public void typedArithmeticAndComparisonsRejectWrongNumericKind() throws Exception {
        assertTrue(wire("FBD CRAFT VAR INT", "Q", "FBD CRAFT ARITHMETIC INT", "IN1"));
        assertFalse(wire("FBD CRAFT VAR INT", "Q", "FBD CRAFT ARITHMETIC FLOAT", "IN1"));
        assertTrue(wire("FBD CRAFT VAR FLOAT", "Q", "FBD CRAFT ARITHMETIC FLOAT", "IN2"));
        assertFalse(wire("FBD CRAFT VAR FLOAT", "Q", "FBD CRAFT COMPARE", "IN1"));
        assertTrue(wire("FBD CRAFT VAR FLOAT", "Q", "FBD CRAFT COMPARE FLOAT", "IN2"));
    }

    @Test public void explicitConvertersAreRequiredBetweenCraftIntegerAndFloat() throws Exception {
        assertFalse(wire("FBD CRAFT VAR INT", "Q", "FBD CRAFT FLOAT TO INT", "IN"));
        assertTrue(wire("FBD CRAFT VAR FLOAT", "Q", "FBD CRAFT FLOAT TO INT", "IN"));
        assertTrue(wire("FBD CRAFT FLOAT TO INT", "OUT", "FBD CRAFT VAR OUT INT", "IN"));
        assertTrue(wire("FBD CRAFT VAR INT", "Q", "FBD CRAFT INT TO FLOAT", "IN"));
        assertTrue(wire("FBD CRAFT INT TO FLOAT", "OUT", "FBD CRAFT VAR OUT FLOAT", "IN"));
        assertTrue(wire("FBD CRAFT VAR INT", "Q", "FBD CRAFT WORD TO FLOAT", "IN"));
    }

    @Test public void genericNumericBlocksRemainCompatibleUntilTheirTypeIsConfirmed() throws Exception {
        assertTrue(wire("FBD POW", "OUT", "FBD CRAFT ARITHMETIC FLOAT", "IN1"));
        assertTrue(wire("FBD CRAFT VAR INT", "Q", "FBD MOVE", "IN"));
        assertEquals("INT", FbdCraftPinTypes.typeForBlock("FBD CRAFT VAR INT", "Q", true));
        assertEquals("FLOAT", FbdCraftPinTypes.typeForBlock("FBD CRAFT VAR FLOAT", "Q", true));
    }

    @Test public void craftTimerPresetRequiresIntegerWhileAcceptingLegacyPinAlias() throws Exception {
        assertEquals("INT", FbdCraftPinTypes.typeForBlock("FBD CRAFT TIMER", "MS", false));
        assertEquals("INT", FbdCraftPinTypes.typeForBlock("FBD CRAFT TIMER", "PT", false));
        assertTrue(wire("FBD CRAFT VAR INT", "Q", "FBD CRAFT TIMER", "MS"));
        assertTrue(wire("FBD CRAFT VAR INT", "Q", "FBD CRAFT TIMER", "PT"));
        assertTrue(wire("FBD CRAFT CTU", "Q", "FBD CRAFT TIMER", "MS",
                "NUMBER", "NUMBER"));
        assertFalse(wire("FBD CRAFT VAR FLOAT", "Q", "FBD CRAFT TIMER", "MS"));
        assertFalse(wire("FBD CRAFT VAR FLOAT", "Q", "FBD CRAFT TIMER", "PT"));
    }

    @Test public void craftCounterOutputUsesOriginalIntegerType() throws Exception {
        assertEquals("INT", FbdCraftPinTypes.typeForBlock("FBD CRAFT CTU", "Q", true));
        assertTrue(wire("FBD CRAFT CTU", "Q", "FBD CRAFT VAR OUT INT", "IN"));
        assertFalse(wire("FBD CRAFT CTU", "Q", "FBD CRAFT VAR OUT FLOAT", "IN"));
    }

    @Test public void originalCraftFloatMathPinsRejectIntegerWires() throws Exception {
        assertEquals("FLOAT", FbdCraftPinTypes.typeForBlock("FBD CRAFT POW", "X", false));
        assertEquals("FLOAT", FbdCraftPinTypes.typeForBlock("FBD CRAFT POW", "Q", true));
        assertTrue(wire("FBD CRAFT VAR FLOAT", "Q", "FBD CRAFT POW", "X"));
        assertFalse(wire("FBD CRAFT VAR INT", "Q", "FBD CRAFT POW", "X"));
        assertArrayEquals(new String[]{"X", "Y"}, MainActivity.compatibleFbdTargetPins(
                "FBD CRAFT VAR FLOAT", "Q", "FBD CRAFT POW"));
        assertArrayEquals(new String[0], MainActivity.compatibleFbdTargetPins(
                "FBD CRAFT VAR INT", "Q", "FBD CRAFT POW"));

        for (String kind : new String[]{"FBD CRAFT SIN", "FBD CRAFT LOG", "FBD CRAFT ABS"}) {
            assertEquals("FLOAT", FbdCraftPinTypes.typeForBlock(kind, "IN", false));
            assertEquals("FLOAT", FbdCraftPinTypes.typeForBlock(kind, "OUT", true));
            assertTrue(wire("FBD CRAFT VAR FLOAT", "Q", kind, "IN"));
            assertFalse(wire("FBD CRAFT VAR INT", "Q", kind, "IN"));
        }
    }

    private static boolean wire(String sourceType, String sourcePin, String targetType,
                                String targetPin) throws Exception {
        return wire(sourceType, sourcePin, targetType, targetPin, null, null);
    }

    private static boolean wire(String sourceType, String sourcePin, String targetType,
                                String targetPin, String storedSourceSignal,
                                String storedTargetSignal) throws Exception {
        JSONObject edge = new JSONObject().put("source", 0).put("target", 1)
                .put("sourcePin", sourcePin).put("targetPin", targetPin);
        if (storedSourceSignal != null) edge.put("sourceSignalType", storedSourceSignal);
        if (storedTargetSignal != null) edge.put("targetSignalType", storedTargetSignal);
        return MainActivity.hasCompatibleKnownFbdPins(edge, sourceType, targetType);
    }
}
