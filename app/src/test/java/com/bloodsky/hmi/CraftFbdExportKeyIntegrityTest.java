package com.bloodsky.hmi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native saveKey and componentId select FBD wire endpoints during .pro export. */
public final class CraftFbdExportKeyIntegrityTest {
    private static JSONObject row(int id, Object saveKey, Object screen) throws Exception {
        JSONObject other = new JSONObject().put("saveKey", saveKey)
                .put("numScreen", screen).put("vendor", "preserve");
        return new JSONObject().put("componentId", id)
                .put("text", "Gate").put("x", 3).put("y", 4)
                .put("width", 60).put("height", 40)
                .put("jsTableComponent_other", other);
    }

    private static int allocate(JSONArray rows, int screen) throws Exception {
        Method method = CraftProjectExporter.class.getDeclaredMethod(
                "nextCraftSaveKey", JSONArray.class, int.class);
        method.setAccessible(true);
        return ((Integer) method.invoke(null, rows, screen)).intValue();
    }

    private static InvocationTargetException allocationFails(JSONArray rows)
            throws Exception {
        return assertThrows(InvocationTargetException.class,
                () -> allocate(rows, 1));
    }

    @Test public void normalAndCrossScreenKeysAllocateDeterministically() throws Exception {
        JSONArray rows = new JSONArray()
                .put(row(103, 12, 1)).put(row(104, 40, 2))
                .put(row(114, 16, 1)).put(row(2, 999, 1));
        assertEquals(17, allocate(rows, 1));
        assertEquals(41, allocate(rows, 2));
        assertEquals(1, allocate(new JSONArray(), 1));
        assertEquals(1, allocate(new JSONArray().put(row(103, -1, 1)), 1));
    }

    @Test public void oversizedOrFractionalSourceKeysNeverAliasRealEndpoint()
            throws Exception {
        for (Object invalid : new Object[]{4294967298L, "4294967298",
                7.5, JSONObject.NULL, new JSONArray().put(2),
                -2}) {
            JSONArray rows = new JSONArray().put(row(103, invalid, 1));
            assertNotNull(allocationFails(rows).getCause());
        }
        assertNotNull(allocationFails(new JSONArray()
                .put(row(103, Integer.MAX_VALUE, 1))).getCause());
    }

    @Test public void ambiguousOriginalKeysCannotCreateAnotherWireDestination()
            throws Exception {
        JSONArray rows = new JSONArray().put(row(103, 3, 1))
                .put(row(104, 3, 1));
        assertNotNull(allocationFails(rows).getCause());
        // The same key in different screens is not ambiguous.
        rows.getJSONObject(1).getJSONObject("jsTableComponent_other")
                .put("numScreen", 2);
        assertEquals(4, allocate(rows, 1));
    }

    private static void copy(JSONObject widget, JSONArray rows, JSONArray output)
            throws Exception {
        Method method = CraftProjectExporter.class.getDeclaredMethod(
                "appendCopiedComponentRow", JSONObject.class, int.class, int.class,
                JSONArray.class, JSONArray.class);
        method.setAccessible(true);
        method.invoke(null, widget, 0, 0, rows, output);
    }

    @Test public void copiedCraftFactoryIdentityMustMatchOriginalSource()
            throws Exception {
        JSONArray rows = new JSONArray().put(row(103, 10, 1));
        JSONArray output = new JSONArray().put(
                new JSONObject(rows.getJSONObject(0).toString()));
        JSONObject widget = new JSONObject().put("craftComponentId", 104);
        assertNotNull(assertThrows(InvocationTargetException.class,
                () -> copy(widget, rows, output)).getCause());
        assertEquals(1, rows.length());
        assertEquals(1, output.length());

        widget.put("craftComponentId", 103);
        copy(widget, rows, output);
        assertEquals(2, rows.length());
        assertEquals(2, output.length());
        assertEquals(103, widget.getInt("craftComponentId"));
        assertEquals(11, output.getJSONObject(1)
                .getJSONObject("jsTableComponent_other").getInt("saveKey"));
        assertEquals("preserve", output.getJSONObject(1)
                .getJSONObject("jsTableComponent_other").getString("vendor"));
    }

    @Test public void copiedSourceScreenCannotWrapToWrongDestination()
            throws Exception {
        JSONArray rows = new JSONArray().put(row(103, 10, 4294967297L));
        JSONArray output = new JSONArray().put(
                new JSONObject(rows.getJSONObject(0).toString()));
        assertNotNull(assertThrows(InvocationTargetException.class,
                () -> copy(new JSONObject().put("craftComponentId", 103),
                        rows, output)).getCause());
        assertEquals(1, rows.length());
    }
}
