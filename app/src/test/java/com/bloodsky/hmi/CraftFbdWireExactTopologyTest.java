package com.bloodsky.hmi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Graph endpoint/source indexes in edited Craft .pro must never wrap or truncate. */
public final class CraftFbdWireExactTopologyTest {
    private static JSONObject row(int id, Object key, Object outgoing) throws Exception {
        JSONObject other = new JSONObject().put("numScreen", 1)
                .put("saveKey", key).put("keyOut", outgoing)
                .put("numOnIn", -1).put("vendor", "keep");
        return new JSONObject().put("componentId", id)
                .put("text", "Block").put("x", 1).put("y", 2)
                .put("width", 80).put("height", 50)
                .put("jsTableComponent_other", other);
    }

    private static JSONObject widget(int id, int sourceRow, String type) throws Exception {
        return new JSONObject().put("type", type).put("craftComponentId", id)
                .put("craftSourceRow", sourceRow).put("text", type)
                .put("x", 1).put("y", 2).put("w", 80).put("h", 50);
    }

    private static JSONObject wire(Object source, Object target) throws Exception {
        return new JSONObject().put("source", source).put("target", target)
                .put("sourcePin", "Q").put("targetPin", "IN1");
    }

    private static void write(JSONArray edited, JSONArray baseline,
                              JSONArray source, JSONArray result) throws Exception {
        Method m = CraftProjectExporter.class.getDeclaredMethod("writeFbdConnections",
                JSONArray.class, JSONArray.class, java.util.List.class,
                JSONArray.class, JSONArray.class);
        m.setAccessible(true);
        m.invoke(null, edited, baseline, Arrays.asList(new int[]{0, 1}),
                source, result);
    }

    private static JSONArray[] situation(Object sourceIndex, Object targetIndex)
            throws Exception {
        JSONArray widgets = new JSONArray().put(widget(103, 0, "FBD AND"))
                .put(widget(104, 1, "FBD OR"));
        JSONArray baseline = new JSONArray().put(new JSONObject()
                .put("widgets", new JSONArray(widgets.toString()))
                .put("connections", new JSONArray()));
        JSONArray edited = new JSONArray().put(new JSONObject()
                .put("widgets", widgets)
                .put("connections", new JSONArray().put(wire(sourceIndex, targetIndex))));
        JSONArray rows = new JSONArray().put(row(103, 10, -1))
                .put(row(104, 11, -1));
        return new JSONArray[]{edited, baseline, rows,
                new JSONArray(rows.toString())};
    }

    @Test public void validCraftBoolWireMapsToUnmodifiedOriginalSourceRows()
            throws Exception {
        JSONArray[] context = situation(0, 1);
        write(context[0], context[1], context[2], context[3]);
        JSONObject output = context[3].getJSONObject(0)
                .getJSONObject("jsTableComponent_other");
        assertEquals(11, output.getInt("keyOut"));
        assertEquals(1, output.getInt("numOnIn"));
        assertEquals("keep", output.getString("vendor"));
        assertEquals(-1, context[2].getJSONObject(0)
                .getJSONObject("jsTableComponent_other").getInt("keyOut"));
    }

    @Test public void hugeOrFractionalEditedWireIndexIsNeverAliased()
            throws Exception {
        for (Object bad : new Object[]{4294967296L, 4294967297L, 0.5,
                "4294967296", new JSONArray().put(0), JSONObject.NULL}) {
            JSONArray[] source = situation(bad, 1);
            String original = source[3].toString();
            assertNotNull(assertThrows(InvocationTargetException.class,
                    () -> write(source[0], source[1], source[2], source[3])).getCause());
            assertEquals(original, source[3].toString());
            JSONArray[] destination = situation(0, bad);
            assertNotNull(assertThrows(InvocationTargetException.class,
                    () -> write(destination[0], destination[1],
                            destination[2], destination[3])).getCause());
        }
    }

    @Test public void forgedFbdFactoryAndSourceKeyNeverSelectDifferentBlock()
            throws Exception {
        Method check = CraftProjectExporter.class.getDeclaredMethod(
                "isCraftFbd", JSONObject.class);
        check.setAccessible(true);
        JSONObject forged = widget(103, 0, "FBD AND");
        forged.put("craftComponentId", 4294967399L);
        assertNotNull(assertThrows(InvocationTargetException.class,
                () -> check.invoke(null, forged)).getCause());

        Method unique = CraftProjectExporter.class.getDeclaredMethod(
                "isUniqueSaveKey", JSONObject.class, int[].class, JSONArray.class);
        unique.setAccessible(true);
        JSONArray rows = new JSONArray().put(row(103, 4294967307L, -1))
                .put(row(104, 11, -1));
        JSONObject target = rows.getJSONObject(1)
                .getJSONObject("jsTableComponent_other");
        assertNotNull(assertThrows(InvocationTargetException.class,
                () -> unique.invoke(null, target, new int[]{0, 1}, rows)).getCause());
    }
}
