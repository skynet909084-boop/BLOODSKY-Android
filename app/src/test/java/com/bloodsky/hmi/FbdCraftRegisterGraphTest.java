package com.bloodsky.hmi;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class FbdCraftRegisterGraphTest {
    private static MainActivity.Widget widget(String type) {
        MainActivity.Widget w = new MainActivity.Widget();
        w.type = type;
        w.text = type;
        return w;
    }

    private static double number(List<MainActivity.Widget> nodes,
                                 List<MainActivity.FbdConnection> edges, int index) {
        FbdCraftTestGraphs.Graph graph = FbdCraftTestGraphs.withReadyCraftCounters(nodes, edges);
        int n = graph.widgets.size();
        return MainActivity.evaluateFbdBlockNumeric(graph.widgets, graph.connections, index,
                new boolean[n], new boolean[n], new boolean[n], new double[n]);
    }

    @Test public void arrayWritesSharedCraftIntegerOnRisingEnableAndMxReadsIt() throws Exception {
        FbdCraftRegisterStore.clear();
        MainActivity.Widget enable = widget("FBD Вход");
        MainActivity.Widget data = widget("FBD CRAFT CTU"); data.counterValue = 42;
        MainActivity.Widget id = widget("FBD CRAFT CTU"); id.counterValue = 7;
        MainActivity.Widget array = widget("FBD CRAFT ARRAY");
        MainActivity.Widget mxEnable = widget("FBD Вход");
        MainActivity.Widget mxId = widget("FBD CRAFT CTU"); mxId.counterValue = 7;
        MainActivity.Widget mx = widget("FBD CRAFT MX");
        List<MainActivity.Widget> nodes = Arrays.asList(enable, data, id, array, mxEnable, mxId, mx);
        List<MainActivity.FbdConnection> edges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 3, "EN"),
                MainActivity.pinnedFbdConnection(1, "Q", 3, "DT"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "ID"),
                MainActivity.pinnedFbdConnection(4, "Q", 6, "EN"),
                MainActivity.pinnedFbdConnection(5, "Q", 6, "I"));

        enable.inputValue = true;
        mxEnable.inputValue = true;
        assertEquals(42.0, number(nodes, edges, 3), 0.00001);
        assertEquals(42.0, number(nodes, edges, 6), 0.00001);
        assertTrue("ARRAY records its rising enable", array.craftArrayLastEnable);
        data.counterValue = 99;
        assertEquals(42.0, number(nodes, edges, 3), 0.00001);
        enable.inputValue = false;
        number(nodes, edges, 3);
        assertFalse(array.craftArrayLastEnable);
        enable.inputValue = true;
        assertEquals(99.0, number(nodes, edges, 3), 0.00001);
    }

    private static double number(List<MainActivity.Widget> nodes,
                                 List<MainActivity.FbdConnection> edges, int index,
                                 CraftSystemRegisterBank userRegisters) {
        FbdCraftTestGraphs.Graph graph = FbdCraftTestGraphs.withReadyCraftCounters(nodes, edges);
        int n = graph.widgets.size();
        return MainActivity.evaluateFbdBlockNumeric(graph.widgets, graph.connections, index,
                new boolean[n], new boolean[n], new boolean[n], new double[n], userRegisters);
    }

    @Test public void userArrayWordsPersistInProjectBankAndMxReadsThem() throws Exception {
        FbdCraftRegisterStore.clear();
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        MainActivity.Widget enable = widget("FBD Вход"); enable.inputValue = true;
        MainActivity.Widget data = widget("FBD CRAFT CTU"); data.counterValue = 321;
        MainActivity.Widget id = widget("FBD CRAFT CTU"); id.counterValue = 10;
        MainActivity.Widget array = widget("FBD CRAFT ARRAY");
        MainActivity.Widget mxEnable = widget("FBD Вход"); mxEnable.inputValue = true;
        MainActivity.Widget mxId = widget("FBD CRAFT CTU"); mxId.counterValue = 10;
        MainActivity.Widget mx = widget("FBD CRAFT MX");
        List<MainActivity.Widget> nodes = Arrays.asList(enable, data, id, array, mxEnable, mxId, mx);
        List<MainActivity.FbdConnection> edges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 3, "EN"),
                MainActivity.pinnedFbdConnection(1, "Q", 3, "DT"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "ID"),
                MainActivity.pinnedFbdConnection(4, "Q", 6, "EN"),
                MainActivity.pinnedFbdConnection(5, "Q", 6, "I"));

        assertEquals(321.0, number(nodes, edges, 3, bank), 0.00001);
        assertEquals(321.0, number(nodes, edges, 6, bank), 0.00001);
        assertEquals(321, bank.readWord(10));
    }

    @Test public void importedCraftVarBlocksReadProjectSystemRegistersBySerializedAddress() throws Exception {
        FbdCraftRegisterStore.clear();
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        bank.writeWord(1000, -17);
        bank.writeBit(10, 4, true);
        bank.writeFloat32(1001, 12.5f, false, false);

        MainActivity.Widget integer = widget("FBD CRAFT VAR INT");
        integer.extraFields.put("reg", 1000);
        MainActivity.Widget bool = widget("FBD CRAFT VAR BOOL");
        bool.extraFields.put("reg", 10);
        bool.extraFields.put("bit", 4);
        MainActivity.Widget floating = widget("FBD CRAFT VAR FLOAT");
        floating.extraFields.put("reg", 1001);
        List<MainActivity.Widget> nodes = Arrays.asList(integer, bool, floating);
        List<MainActivity.FbdConnection> edges = java.util.Collections.emptyList();

        assertEquals(-17.0, number(nodes, edges, 0, bank), 0.0);
        assertEquals(1.0, number(nodes, edges, 1, bank), 0.0);
        assertEquals(12.5, number(nodes, edges, 2, bank), 0.0);
    }

    @Test public void invalidArrayIndexCannotReadOrWriteOutsideSourceBank() throws Exception {
        FbdCraftRegisterStore.clear();
        MainActivity.Widget enable = widget("FBD Вход"); enable.inputValue = true;
        MainActivity.Widget data = widget("FBD CRAFT CTU"); data.counterValue = 55;
        MainActivity.Widget id = widget("FBD CRAFT CTU"); id.counterValue = 2000;
        MainActivity.Widget array = widget("FBD CRAFT ARRAY");
        List<MainActivity.Widget> nodes = Arrays.asList(enable, data, id, array);
        List<MainActivity.FbdConnection> edges = Arrays.asList(
                MainActivity.pinnedFbdConnection(0, "Q", 3, "EN"),
                MainActivity.pinnedFbdConnection(1, "Q", 3, "DT"),
                MainActivity.pinnedFbdConnection(2, "Q", 3, "ID"));
        assertTrue(Double.isNaN(number(nodes, edges, 3)));
        assertNull(FbdCraftRegisterStore.read(-1));
        assertNull(FbdCraftRegisterStore.read(2000));
    }
}
