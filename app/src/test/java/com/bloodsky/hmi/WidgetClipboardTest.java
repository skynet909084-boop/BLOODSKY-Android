package com.bloodsky.hmi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WidgetClipboardTest {
    private MainActivity.Widget widget(String type, float x, float y) {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = type;
        widget.text = type;
        widget.x = x;
        widget.y = y;
        widget.w = 40;
        widget.h = 20;
        return widget;
    }

    @Test public void groupCopyPreservesPropertiesRelativeGeometryAndInternalLinks() throws Exception {
        MainActivity.Widget a = widget("FBD Вход", 120.5f, 80.25f);
        MainActivity.Widget b = widget("FBD AND", 240.5f, 100.25f);
        MainActivity.Widget outside = widget("Текст", 400, 100);
        a.extraFields.put("vendorFlag", "keep");
        b.connectionInfo = "fbd://1";
        ArrayList<MainActivity.FbdConnection> edges = new ArrayList<>();
        MainActivity.FbdConnection internal = MainActivity.connectionFromJson(
                new JSONObject("{\"source\":0,\"target\":1,\"sourcePin\":\"OUT\",\"opaque\":17}"));
        edges.add(internal);
        edges.add(new MainActivity.FbdConnection(1, 2));

        WidgetClipboard.Bundle bundle = WidgetClipboard.capture(
                Arrays.asList(a, b, outside), Arrays.asList(1, 0, 1), edges);
        ArrayList<MainActivity.Widget> pasted = WidgetClipboard.place(bundle, 500, 300, 24, 3);
        ArrayList<MainActivity.FbdConnection> pastedEdges = WidgetClipboard.placedConnections(bundle, 3);

        assertEquals(2, pasted.size());
        assertEquals(24f, pasted.get(0).x, 0f);
        assertEquals(24f, pasted.get(0).y, 0f);
        assertEquals(144f, pasted.get(1).x, 0f);
        assertEquals(44f, pasted.get(1).y, 0f);
        assertEquals("keep", pasted.get(0).extraFields.getString("vendorFlag"));
        assertEquals("fbd://4", pasted.get(1).connectionInfo);
        assertEquals(1, pastedEdges.size());
        assertEquals(3, pastedEdges.get(0).source);
        assertEquals(4, pastedEdges.get(0).target);
        assertEquals(17, MainActivity.connectionToJson(pastedEdges.get(0)).getInt("opaque"));
        assertEquals("OUT", MainActivity.connectionToJson(pastedEdges.get(0)).getString("sourcePin"));
        assertEquals("fbd://1", b.connectionInfo);
        assertEquals(120.5f, a.x, 0f);
    }

    @Test public void singleItemAndCanvasClampingRemainSupported() throws Exception {
        MainActivity.Widget source = widget("Текст", 90, 70);
        WidgetClipboard.Bundle bundle = WidgetClipboard.capture(
                Collections.singletonList(source), Collections.singletonList(0), null);
        ArrayList<MainActivity.Widget> pasted = WidgetClipboard.place(bundle, 100, 80, 100, 0);
        assertEquals(60f, pasted.get(0).x, 0f);
        assertEquals(60f, pasted.get(0).y, 0f);
        assertEquals(90f, source.x, 0f);
        assertEquals(70f, source.y, 0f);
    }

    @Test public void selectionIsDeduplicatedAndInvalidIndexesAreIgnored() throws Exception {
        WidgetClipboard.Bundle bundle = WidgetClipboard.capture(
                Arrays.asList(widget("A", 0, 0), widget("B", 50, 0)),
                Arrays.asList(1, -1, 1, 9), null);
        assertEquals(1, bundle.widgets.size());
        assertEquals("B", bundle.widgets.get(0).type);
        try {
            WidgetClipboard.capture(Collections.emptyList(), Collections.emptyList(), null);
            fail("Empty selection must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void groupCutDropsTouchingEdgesAndReindexesSurvivorsAndBindings() throws Exception {
        ArrayList<MainActivity.Widget> widgets = new ArrayList<>(Arrays.asList(
                widget("keep0", 0, 0), widget("remove", 40, 0), widget("keep1", 80, 0),
                widget("remove", 120, 0), widget("keep2", 160, 0)));
        widgets.get(2).connectionInfo = "fbd://5";
        widgets.get(4).connectionInfo = "fbd://3";
        ArrayList<MainActivity.FbdConnection> edges = new ArrayList<>(Arrays.asList(
                new MainActivity.FbdConnection(0, 2), new MainActivity.FbdConnection(1, 2),
                new MainActivity.FbdConnection(2, 4), new MainActivity.FbdConnection(0, 4)));

        assertEquals(2, WidgetClipboard.deleteSelection(widgets, edges, Arrays.asList(3, 1, 3)));
        assertEquals(3, widgets.size());
        assertEquals("fbd://3", widgets.get(1).connectionInfo);
        assertEquals("fbd://2", widgets.get(2).connectionInfo);
        assertEquals(3, edges.size());
        assertEquals(0, edges.get(0).source);
        assertEquals(1, edges.get(0).target);
        assertEquals(1, edges.get(1).source);
        assertEquals(2, edges.get(1).target);
        assertEquals(0, edges.get(2).source);
        assertEquals(2, edges.get(2).target);
    }
}
