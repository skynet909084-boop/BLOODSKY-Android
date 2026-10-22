package com.bloodsky.hmi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.json.JSONObject;

/** Snapshot and placement rules for editor copy, cut and paste. */
final class WidgetClipboard {
    static final class Bundle {
        final ArrayList<MainActivity.Widget> widgets = new ArrayList<>();
        final ArrayList<MainActivity.FbdConnection> connections = new ArrayList<>();
        final Map<Integer, Integer> resultBindings = new HashMap<>();
        float minX, minY, maxX, maxY;
    }

    private WidgetClipboard() { }

    static Bundle capture(List<MainActivity.Widget> source, List<Integer> selection,
            List<MainActivity.FbdConnection> edges) throws Exception {
        if (source == null || selection == null) throw new IllegalArgumentException("Missing selection");
        ArrayList<Integer> indexes = new ArrayList<>();
        for (Integer index : selection) {
            if (index != null && index >= 0 && index < source.size() && !indexes.contains(index))
                indexes.add(index);
        }
        Collections.sort(indexes);
        if (indexes.isEmpty()) throw new IllegalArgumentException("Empty selection");

        Bundle bundle = new Bundle();
        bundle.minX = bundle.minY = Float.POSITIVE_INFINITY;
        bundle.maxX = bundle.maxY = Float.NEGATIVE_INFINITY;
        Map<Integer, Integer> remap = new HashMap<>();
        for (int index : indexes) {
            MainActivity.Widget widget = MainActivity.widgetFromJson(
                    new JSONObject(MainActivity.widgetToJson(source.get(index)).toString()));
            remap.put(index, bundle.widgets.size());
            bundle.widgets.add(widget);
            bundle.minX = Math.min(bundle.minX, widget.x);
            bundle.minY = Math.min(bundle.minY, widget.y);
            bundle.maxX = Math.max(bundle.maxX, widget.x + widget.w);
            bundle.maxY = Math.max(bundle.maxY, widget.y + widget.h);
        }
        for (int index : indexes) {
            int copyIndex = remap.get(index);
            int sourceIndex = FbdResultBinding.sourceIndex(source.get(index).connectionInfo);
            Integer copiedSource = remap.get(sourceIndex);
            if (copiedSource != null) bundle.resultBindings.put(copyIndex, copiedSource);
        }
        for (MainActivity.Widget widget : bundle.widgets) {
            widget.x -= bundle.minX;
            widget.y -= bundle.minY;
        }
        if (edges != null) for (MainActivity.FbdConnection edge : edges) {
            Integer from = remap.get(edge.source), to = remap.get(edge.target);
            if (from != null && to != null) {
                bundle.connections.add(MainActivity.connectionFromJson(
                        MainActivity.connectionToJson(edge)));
                MainActivity.FbdConnection copy = bundle.connections.get(bundle.connections.size() - 1);
                copy.source = from;
                copy.target = to;
            }
        }
        bundle.maxX -= bundle.minX;
        bundle.maxY -= bundle.minY;
        bundle.minX = bundle.minY = 0;
        return bundle;
    }

    static ArrayList<MainActivity.Widget> place(Bundle bundle, float canvasWidth,
            float canvasHeight, float offset, int firstWidgetIndex) throws Exception {
        if (bundle == null || bundle.widgets.isEmpty()) throw new IllegalArgumentException("Empty clipboard");
        float tx = offset, ty = offset;
        float maxTx = Math.max(0, canvasWidth - bundle.maxX);
        float maxTy = Math.max(0, canvasHeight - bundle.maxY);
        tx = Math.max(0, Math.min(maxTx, tx));
        ty = Math.max(0, Math.min(maxTy, ty));
        ArrayList<MainActivity.Widget> result = new ArrayList<>();
        for (MainActivity.Widget widget : bundle.widgets) {
            MainActivity.Widget copy = MainActivity.widgetFromJson(
                    new JSONObject(MainActivity.widgetToJson(widget).toString()));
            copy.x += tx;
            copy.y += ty;
            int sourceRow = copy.extraFields.optInt("craftSourceRow", -1);
            if (sourceRow >= 0) {
                copy.extraFields.put("craftCopySourceRow", sourceRow);
                copy.extraFields.remove("craftSourceRow");
            }
            Integer copiedSource = bundle.resultBindings.get(result.size());
            if (copiedSource != null) copy.connectionInfo = "fbd://" + (firstWidgetIndex + copiedSource + 1);
            if ("Архив Craft".equals(copy.type))
                copy.extraFields.put("craftArchiveKey", java.util.UUID.randomUUID().toString());
            result.add(copy);
        }
        return result;
    }

    static ArrayList<MainActivity.FbdConnection> placedConnections(Bundle bundle, int firstWidgetIndex) throws Exception {
        ArrayList<MainActivity.FbdConnection> result = new ArrayList<>();
        if (bundle == null) return result;
        for (MainActivity.FbdConnection edge : bundle.connections) {
            MainActivity.FbdConnection copy = MainActivity.connectionFromJson(
                    MainActivity.connectionToJson(edge));
            copy.source += firstWidgetIndex;
            copy.target += firstWidgetIndex;
            result.add(copy);
        }
        return result;
    }

    /** Remove a selection and reindex surviving graph and positional result references. */
    static int deleteSelection(List<MainActivity.Widget> widgets,
            List<MainActivity.FbdConnection> edges, List<Integer> selection) {
        if (widgets == null || selection == null) return 0;
        TreeSet<Integer> removed = new TreeSet<>(Collections.reverseOrder());
        for (Integer index : selection)
            if (index != null && index >= 0 && index < widgets.size()) removed.add(index);
        if (removed.isEmpty()) return 0;
        for (int index : removed) {
            widgets.remove(index);
            for (MainActivity.Widget survivor : widgets)
                survivor.connectionInfo = FbdResultBinding.afterWidgetRemoved(survivor.connectionInfo, index);
        }
        if (edges != null) {
            ArrayList<MainActivity.FbdConnection> kept = new ArrayList<>();
            for (MainActivity.FbdConnection edge : edges) {
                boolean removedEndpoint = false;
                for (int index : removed) if (edge.source == index || edge.target == index) {
                    removedEndpoint = true;
                    break;
                }
                if (removedEndpoint) continue;
                for (int index : removed) {
                    if (edge.source > index) edge.source--;
                    if (edge.target > index) edge.target--;
                }
                kept.add(edge);
            }
            edges.clear();
            edges.addAll(kept);
        }
        return removed.size();
    }
}
