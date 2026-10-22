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
