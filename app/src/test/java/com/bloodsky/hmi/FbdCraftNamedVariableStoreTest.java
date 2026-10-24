package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

/** Regression tests for Craft named variables shared by project and macro instances. */
public class FbdCraftNamedVariableStoreTest {
    @Test public void globalNamesCannotAliasScopedMacroVariables() {
        FbdCraftNamedVariableStore.clear();
        // Before this fix both variables had exactly the same map key: 2:3:abc:state.
        assertNotEquals(FbdCraftNamedVariableStore.key("3:abc:state", 2),
                FbdCraftNamedVariableStore.key("abc", "state", 2));
        assertTrue(FbdCraftNamedVariableStore.write("3:abc:state", 2, 37));
        assertTrue(FbdCraftNamedVariableStore.write("abc", "state", 2, 91));
        assertEquals(37.0, FbdCraftNamedVariableStore.read("3:abc:state", 2), 0);
        assertEquals(91.0, FbdCraftNamedVariableStore.read("abc", "state", 2), 0);
        FbdCraftNamedVariableStore.clearNamespace("abc");
        assertEquals(37.0, FbdCraftNamedVariableStore.read("3:abc:state", 2), 0);
        assertEquals(0.0, FbdCraftNamedVariableStore.read("abc", "state", 2), 0);
    }

    @Test public void scopesTypesAndDelimiterNamesRemainIndependent() {
        FbdCraftNamedVariableStore.clear();
        assertTrue(FbdCraftNamedVariableStore.write("", "3:a:b:c", 2, 17));
        assertTrue(FbdCraftNamedVariableStore.write("a:b", "c", 2, 24));
        assertTrue(FbdCraftNamedVariableStore.write("a", "b:c", 2, 35));
        assertTrue(FbdCraftNamedVariableStore.write("a:b", "c", 3, 12.5));
        assertTrue(FbdCraftNamedVariableStore.write("a:b", "c", 1, 1));
        assertEquals(17.0, FbdCraftNamedVariableStore.read("3:a:b:c", 2), 0);
        assertEquals(24.0, FbdCraftNamedVariableStore.read("a:b", "c", 2), 0);
        assertEquals(35.0, FbdCraftNamedVariableStore.read("a", "b:c", 2), 0);
        assertEquals(12.5, FbdCraftNamedVariableStore.read("a:b", "c", 3), 0);
        assertEquals(1.0, FbdCraftNamedVariableStore.read("a:b", "c", 1), 0);
        FbdCraftNamedVariableStore.clearNamespace("a:b");
        assertEquals(0.0, FbdCraftNamedVariableStore.read("a:b", "c", 2), 0);
        assertEquals(0.0, FbdCraftNamedVariableStore.read("a:b", "c", 3), 0);
        assertEquals(0.0, FbdCraftNamedVariableStore.read("a:b", "c", 1), 0);
        assertEquals(35.0, FbdCraftNamedVariableStore.read("a", "b:c", 2), 0);
        assertEquals(17.0, FbdCraftNamedVariableStore.read("3:a:b:c", 2), 0);
    }

    @Test public void emptyNamespaceAndDefaultApiShareOneGlobalValue() {
        FbdCraftNamedVariableStore.clear();
        assertEquals(FbdCraftNamedVariableStore.key("stage", 2),
                FbdCraftNamedVariableStore.key(null, "stage", 2));
        assertEquals(FbdCraftNamedVariableStore.key("stage", 2),
                FbdCraftNamedVariableStore.key("   ", "stage", 2));
        assertTrue(FbdCraftNamedVariableStore.write("stage", 2, 18));
        assertEquals(18.0, FbdCraftNamedVariableStore.read("", "stage", 2), 0);
        FbdCraftNamedVariableStore.clearNamespace("");
        assertEquals(18.0, FbdCraftNamedVariableStore.read("stage", 2), 0);
    }

    @Test public void float32VariableRoundsOnceOnWriteAndKeepsSignedZero() {
        FbdCraftNamedVariableStore.clear();
        assertTrue(FbdCraftNamedVariableStore.write("temperature", 3, 0.1d));
        assertEquals((double) 0.1f, FbdCraftNamedVariableStore.read("temperature", 3), 0.0d);
        assertNotEquals(0.1d, FbdCraftNamedVariableStore.read("temperature", 3), 0.0d);
        assertTrue(FbdCraftNamedVariableStore.write("temperature", 3, -0.0d));
        assertEquals(Double.doubleToRawLongBits(-0.0d),
                Double.doubleToRawLongBits(FbdCraftNamedVariableStore.read("temperature", 3)));
        assertTrue(FbdCraftNamedVariableStore.write("temperature", 3, Double.MIN_VALUE));
        assertEquals(0.0d, FbdCraftNamedVariableStore.read("temperature", 3), 0.0d);
        assertTrue(FbdCraftNamedVariableStore.write("temperature", 3, -Double.MIN_VALUE));
        assertEquals(Double.doubleToRawLongBits(-0.0d),
                Double.doubleToRawLongBits(FbdCraftNamedVariableStore.read("temperature", 3)));
    }

    @Test public void float32AcceptsRepresentableExtremesButRejectsOverflowWithoutClobbering() {
        FbdCraftNamedVariableStore.clear();
        assertTrue(FbdCraftNamedVariableStore.write("macro:float", 3, Float.MIN_VALUE));
        assertEquals((double) Float.MIN_VALUE,
                FbdCraftNamedVariableStore.read("macro:float", 3), 0.0d);
        assertTrue(FbdCraftNamedVariableStore.write("macro:float", 3, Float.MAX_VALUE));
        assertEquals((double) Float.MAX_VALUE,
                FbdCraftNamedVariableStore.read("macro:float", 3), 0.0d);
        assertFalse(FbdCraftNamedVariableStore.write("macro:float", 3, Double.MAX_VALUE));
        assertFalse(FbdCraftNamedVariableStore.write("macro:float", 3, Double.POSITIVE_INFINITY));
        assertFalse(FbdCraftNamedVariableStore.write("macro:float", 3, Double.NaN));
        assertEquals((double) Float.MAX_VALUE,
                FbdCraftNamedVariableStore.read("macro:float", 3), 0.0d);
    }

    @Test public void macroScopeFloat32DoesNotLeakOrChangeInt32AndBoolPins() {
        FbdCraftNamedVariableStore.clear();
        assertTrue(FbdCraftNamedVariableStore.write("shared", 3, 0.1d));
        assertTrue(FbdCraftNamedVariableStore.write("one", "shared", 3, 0.2d));
        assertTrue(FbdCraftNamedVariableStore.write("two", "shared", 3, 0.3d));
        assertTrue(FbdCraftNamedVariableStore.write("one", "shared", 2, Integer.MAX_VALUE));
        assertTrue(FbdCraftNamedVariableStore.write("one", "shared", 1, 1.0d));
        assertEquals((double) 0.1f, FbdCraftNamedVariableStore.read("shared", 3), 0.0d);
        assertEquals((double) 0.2f, FbdCraftNamedVariableStore.read("one", "shared", 3), 0.0d);
        assertEquals((double) 0.3f, FbdCraftNamedVariableStore.read("two", "shared", 3), 0.0d);
        assertEquals((double) Integer.MAX_VALUE,
                FbdCraftNamedVariableStore.read("one", "shared", 2), 0.0d);
        assertEquals(1.0d, FbdCraftNamedVariableStore.read("one", "shared", 1), 0.0d);
        FbdCraftNamedVariableStore.clearNamespace("one");
        assertEquals(0.0d, FbdCraftNamedVariableStore.read("one", "shared", 3), 0.0d);
        assertEquals(0.0d, FbdCraftNamedVariableStore.read("one", "shared", 2), 0.0d);
        assertEquals((double) 0.3f, FbdCraftNamedVariableStore.read("two", "shared", 3), 0.0d);
        assertEquals((double) 0.1f, FbdCraftNamedVariableStore.read("shared", 3), 0.0d);
    }

    @Test public void invalidNamesOrTypesRemainRejected() {
        FbdCraftNamedVariableStore.clear();
        assertNull(FbdCraftNamedVariableStore.key("", 2));
        assertNull(FbdCraftNamedVariableStore.key("ns", " ", 2));
        assertNull(FbdCraftNamedVariableStore.key("ns", "ok", 0));
        assertFalse(FbdCraftNamedVariableStore.write("ns", "bad", 1, 2.0));
        assertFalse(FbdCraftNamedVariableStore.write("ns", "bad", 2, 1.5));
        assertFalse(FbdCraftNamedVariableStore.write("ns", "bad", 3, Double.NaN));
    }
}
