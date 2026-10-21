package com.bloodsky.hmi;

import java.util.ArrayList;
import java.util.List;

/** Test graph helpers for Craft CTU fixtures that act as initialized numeric sources. */
final class FbdCraftTestGraphs {
    static final class Graph {
        final List<MainActivity.Widget> widgets;
        final List<MainActivity.FbdConnection> connections;

        Graph(List<MainActivity.Widget> widgets, List<MainActivity.FbdConnection> connections) {
            this.widgets = widgets;
            this.connections = connections;
        }
    }

    private FbdCraftTestGraphs() { }

    static Graph withReadyCraftCounters(List<MainActivity.Widget> originalWidgets,
                                        List<MainActivity.FbdConnection> originalConnections) {
        List<MainActivity.Widget> widgets = new ArrayList<>(originalWidgets);
        List<MainActivity.FbdConnection> connections = new ArrayList<>(originalConnections);
        for (int target = 0; target < originalWidgets.size(); target++) {
            MainActivity.Widget widget = originalWidgets.get(target);
            if (widget == null || !"FBD CRAFT CTU".equals(widget.type)) continue;
            for (String pin : new String[]{"+", "-", "R"}) {
                MainActivity.Widget input = new MainActivity.Widget();
                input.type = "FBD Вход";
                input.text = input.type;
                input.inputValue = false;
                int source = widgets.size();
                widgets.add(input);
                try {
                    connections.add(MainActivity.pinnedFbdConnection(source, "Q", target, pin));
                } catch (Exception invalidFixture) {
                    throw new AssertionError("Test CTU input fixture must be valid", invalidFixture);
                }
            }
        }
        return new Graph(widgets, connections);
    }
}
