package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Original mbCanvas cases 139/140 use distinct MQTT typeQ/typeIn keys.
 * A malformed type must not be reinterpreted as a valid native signal.
 */
public final class FbdCraftMacroMqttNativeTypeTest {
    private static JSONObject root(String name, int outputType) throws Exception {
        return new JSONObject().put("name", name).put("countIn", 0)
                .put("typeOut", outputType).put("nameOut", "Q")
                .put("saveKeyOut", 900).put("fbInMacros", new JSONArray());
    }

    private static JSONObject source(int componentId, Object typeQ, Object typeIn)
            throws Exception {
        JSONObject child = new JSONObject().put("componentId", componentId)
                .put("x", 0).put("y", 0).put("saveKey", 50)
                .put("keyOut", componentId == 139 ? 900 : -1)
                .put("topic", "sensor/site");
        if (typeQ != null) child.put("typeQ", typeQ);
        if (typeIn != null) child.put("typeIn", typeIn);
        return child;
    }

    private static JSONObject publisherGraph(Object typeQ, Object typeIn)
            throws Exception {
        JSONObject published = source(140, typeQ, typeIn);
        JSONObject output = new JSONObject().put("componentId", 108)
                .put("x", 60).put("y", 0).put("saveKey", 60)
                .put("keyOut", 900).put("isConstant", true)
                .put("constant", "1.5");
        return FbdCraftMacroGraphAdapter.translate(root("Publisher", 3),
                new JSONArray().put(published).put(output));
    }

    @Test public void id139SubscriberReadsTypeQEvenWhenTypeInDiffers()
            throws Exception {
        JSONObject graph = FbdCraftMacroGraphAdapter.translate(
                root("Subscriber", 3), new JSONArray().put(source(139, 3, 1)));
        assertNotNull(graph);
        JSONObject widget = graph.getJSONArray("widgets").getJSONObject(0);
        assertEquals("FBD CRAFT MQTT SUB FLOAT", widget.getString("type"));
        assertEquals(3, widget.getInt("craftMqttType"));
        JSONObject valid = FbdCraftMacroGraphAdapter.translate(
                root("Subscriber", 2), new JSONArray().put(source(139, 2, 3)));
        assertEquals("FBD CRAFT MQTT SUB INT",
                valid.getJSONArray("widgets").getJSONObject(0).getString("type"));
    }

    @Test public void id140PublisherReadsTypeInNotForeignTypeQ()
            throws Exception {
        JSONObject graph = publisherGraph(1, 3);
        assertNotNull(graph);
        JSONObject publisher = graph.getJSONArray("widgets").getJSONObject(0);
        assertEquals("FBD CRAFT MQTT PUB FLOAT", publisher.getString("type"));
        assertEquals(3, publisher.getInt("craftMqttType"));
        assertEquals("sensor/site", publisher.getString("craftMqttTopic"));
        assertEquals("FBD CRAFT VAR FLOAT",
                graph.getJSONArray("widgets").getJSONObject(1).getString("type"));
    }

    @Test public void oversizeOrFractionalSourceTypeNeverAliasesBool()
            throws Exception {
        for (Object bad : new Object[]{4294967297L, "4294967297",
                1.5, -1, JSONObject.NULL, new JSONArray().put(1)}) {
            assertNull(FbdCraftMacroGraphAdapter.translate(
                    root("Subscriber", 1), new JSONArray().put(source(139, bad, 1))));
            assertNull(publisherGraph(1, bad));
        }
    }

    @Test public void absentCorrectTypeDefaultsToBoolNotOtherSourceKey()
            throws Exception {
        JSONObject subscriber = FbdCraftMacroGraphAdapter.translate(
                root("Subscriber", 1), new JSONArray().put(source(139, null, 3)));
        assertNotNull(subscriber);
        assertEquals("FBD CRAFT MQTT SUB BOOL",
                subscriber.getJSONArray("widgets").getJSONObject(0).getString("type"));
        JSONObject publisher = publisherGraph(3, null);
        assertNotNull(publisher);
        assertEquals("FBD CRAFT MQTT PUB BOOL",
                publisher.getJSONArray("widgets").getJSONObject(0).getString("type"));
    }
}
