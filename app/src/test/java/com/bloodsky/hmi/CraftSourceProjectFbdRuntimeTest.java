package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CraftSourceProjectFbdRuntimeTest {
    private static String sourceFixture(String name) throws Exception {
        try (InputStream input = CraftSourceProjectFbdRuntimeTest.class
                .getResourceAsStream("/craft_samples.json")) {
            assertNotNull("Missing sanitized source project fixtures", input);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
            return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8))
                    .getString(name);
        }
    }

    private static boolean evaluate(List<MainActivity.Widget> widgets,
                                    List<MainActivity.FbdConnection> edges,
                                    int index, CraftSystemRegisterBank registers,
                                    boolean allowSystemRegisterWrites) {
        int count = widgets.size();
        return MainActivity.evaluateFbdBlock(widgets, edges, index,
                new boolean[count], new boolean[count], new boolean[count],
                new double[count], registers, allowSystemRegisterWrites);
    }

    @Test public void importedHouseProjectExecutesSourceCompareAtAndBelowThreshold() throws Exception {
        JSONObject craftSource = new JSONObject(sourceFixture("дом 2025"));
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(craftSource.toString()));
        MainActivity.validateImportedProjectStatic(imported.toString());

        // Craft's source rows 146–151 on screen 3 contain an INT compare (action 4, >=)
        // fed by register 10 and constant 2600. Select it by its typed graph contract.
        JSONObject screen = imported.getJSONArray("screens").getJSONObject(2);
        JSONArray widgetJson = screen.getJSONArray("widgets");
        List<MainActivity.Widget> widgets = new ArrayList<>();
        for (int i = 0; i < widgetJson.length(); i++)
            widgets.add(MainActivity.widgetFromJson(widgetJson.getJSONObject(i)));

        JSONArray edgeJson = screen.getJSONArray("connections");
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        for (int i = 0; i < edgeJson.length(); i++)
            edges.add(MainActivity.connectionFromJson(edgeJson.getJSONObject(i)));

        int compareIndex = -1;
        int outputIndex = -1;
        boolean registerFeedsIn1 = false;
        boolean constantFeedsIn2 = false;
        for (int target = 0; target < widgets.size(); target++) {
            MainActivity.Widget candidate = widgets.get(target);
            if (candidate.extraFields.optInt("craftComponentId", -1) != 107
                    || !">=".equals(candidate.compareOperator)) continue;
            boolean registerOnIn1 = false;
            boolean thresholdOnIn2 = false;
            for (MainActivity.FbdConnection edge : edges) {
                if (edge.target != target) continue;
                MainActivity.Widget source = widgets.get(edge.source);
                int sourceId = source.extraFields.optInt("craftComponentId", -1);
                String targetPin = MainActivity.connectionToJson(edge).optString("targetPin", "");
                if (sourceId != 105) continue;
                if ("IN1".equals(targetPin)
                        && !source.extraFields.optBoolean("craftVarConstantMode", false)
                        && source.extraFields.optInt("reg", -1) == 10) {
                    registerOnIn1 = true;
                }
                if ("IN2".equals(targetPin)
                        && source.extraFields.optBoolean("craftVarConstantMode", false)
                        && "2600".equals(source.extraFields.optString("craftVarConstant", ""))) {
                    thresholdOnIn2 = true;
                }
            }
            if (registerOnIn1 && thresholdOnIn2) {
                compareIndex = target;
                registerFeedsIn1 = true;
                constantFeedsIn2 = true;
                break;
            }
        }
        for (MainActivity.FbdConnection edge : edges) {
            if (edge.source != compareIndex
                    || !"Q".equals(MainActivity.connectionToJson(edge).optString("sourcePin", ""))) continue;
            MainActivity.Widget sink = widgets.get(edge.target);
            if (sink.extraFields.optInt("craftComponentId", -1) == 102
                    && sink.extraFields.optInt("reg", -1) == 11
                    && sink.extraFields.optInt("bit", -1) == 2) {
                outputIndex = edge.target;
                break;
            }
        }

        assertTrue("Source FBD compare should be imported with IN1/IN2 wiring", compareIndex >= 0);
        assertTrue(registerFeedsIn1);
        assertTrue(constantFeedsIn2);
        assertTrue("Source compare should feed its imported BOOL register output", outputIndex >= 0);

        CraftSystemRegisterBank registers = new CraftSystemRegisterBank();
        registers.writeWord(10, 2600);
        assertTrue("Craft compare action 4 is >=, including equality",
                evaluate(widgets, edges, compareIndex, registers, false));
        assertTrue(evaluate(widgets, edges, outputIndex, registers, true));
        assertTrue("The imported VarOut must write the comparison to register 11 bit 2",
                registers.readBit(11, 2));

        registers.writeWord(10, 2599);
        assertFalse("A register value below the source threshold must compare false",
                evaluate(widgets, edges, compareIndex, registers, false));
        assertFalse(evaluate(widgets, edges, outputIndex, registers, true));
        assertFalse(registers.readBit(11, 2));
    }

    @Test public void editedSourceCompareModeExportsToOriginalCodeAndReimports() throws Exception {
        JSONObject craftSource = new JSONObject(sourceFixture("дом 2025"));
        Map<String, byte[]> media = Collections.singletonMap("fixture.png", new byte[]{1, 2, 3, 4});
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(
                craftSource.toString(), "", media));
        JSONObject sourceScreen = imported.getJSONArray("screens").getJSONObject(2);
        JSONArray widgets = sourceScreen.getJSONArray("widgets");
        int compareIndex = -1;
        for (int i = 0; i < widgets.length(); i++) {
            JSONObject widget = widgets.getJSONObject(i);
            if (widget.optInt("craftComponentId", -1) == 107
                    && ">=".equals(widget.optString("compareOperator", ""))) {
                compareIndex = i;
                break;
            }
        }
        assertTrue("Source project should contain a >= Craft comparator", compareIndex >= 0);
        JSONObject selected = widgets.getJSONObject(compareIndex);
        int sourceRow = selected.getInt("craftSourceRow");
        int connectionCount = sourceScreen.getJSONArray("connections").length();
        selected.put("compareOperator", ">");

        JSONObject exported = new JSONObject(CraftProjectExporter.export(imported.toString()));
        JSONObject editedRow = exported.getJSONArray("TableComponent").getJSONObject(sourceRow);
        JSONObject editedOther = editedRow.optJSONObject("jsTableComponent_other");
        if (editedOther == null)
            editedOther = new JSONObject(editedRow.getString("jsTableComponent_other"));
        assertEquals(1, editedOther.getInt("compareAction"));
        JSONObject originalComponent = craftSource.getJSONArray("TableComponent").getJSONObject(sourceRow);
        JSONObject originalOther = originalComponent.optJSONObject("jsTableComponent_other");
        if (originalOther == null)
            originalOther = new JSONObject(originalComponent.getString("jsTableComponent_other"));
        assertNotNull(originalOther);
        assertEquals(originalOther.getInt("saveKey"), editedOther.getInt("saveKey"));

        JSONObject reimported = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONObject restoredScreen = reimported.getJSONArray("screens").getJSONObject(2);
        assertEquals(connectionCount, restoredScreen.getJSONArray("connections").length());
        JSONArray restoredWidgets = restoredScreen.getJSONArray("widgets");
        boolean restoredEditedCompare = false;
        for (int i = 0; i < restoredWidgets.length(); i++) {
            JSONObject widget = restoredWidgets.getJSONObject(i);
            if (widget.optInt("craftSourceRow", -1) == sourceRow) {
                assertEquals(">", widget.getString("compareOperator"));
                restoredEditedCompare = true;
                break;
            }
        }
        assertTrue("Edited source comparator should survive export and re-import", restoredEditedCompare);
    }

    @Test public void importedHouseProjectExecutesCraftIntegerDivisionIntoVarOut() throws Exception {
        JSONObject craftSource = new JSONObject(sourceFixture("дом 2025"));
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(craftSource.toString()));
        JSONObject screen = imported.getJSONArray("screens").getJSONObject(3);
        JSONArray widgetJson = screen.getJSONArray("widgets");
        List<MainActivity.Widget> widgets = new ArrayList<>();
        for (int i = 0; i < widgetJson.length(); i++)
            widgets.add(MainActivity.widgetFromJson(widgetJson.getJSONObject(i)));
        JSONArray edgeJson = screen.getJSONArray("connections");
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        for (int i = 0; i < edgeJson.length(); i++)
            edges.add(MainActivity.connectionFromJson(edgeJson.getJSONObject(i)));

        int divisionIndex = -1;
        boolean registerOnIn1 = false;
        boolean constantOnIn2 = false;
        for (int target = 0; target < widgets.size(); target++) {
            MainActivity.Widget candidate = widgets.get(target);
            if (candidate.extraFields.optInt("craftComponentId", -1) != 110
                    || !"/".equals(candidate.compareOperator)) continue;
            boolean registerInput = false;
            boolean constantInput = false;
            for (MainActivity.FbdConnection edge : edges) {
                if (edge.target != target) continue;
                MainActivity.Widget source = widgets.get(edge.source);
                int sourceId = source.extraFields.optInt("craftComponentId", -1);
                String targetPin = MainActivity.connectionToJson(edge).optString("targetPin", "");
                if (sourceId != 105) continue;
                if ("IN1".equals(targetPin)
                        && !source.extraFields.optBoolean("craftVarConstantMode", false)
                        && source.extraFields.optInt("reg", -1) == 110) registerInput = true;
                if ("IN2".equals(targetPin)
                        && source.extraFields.optBoolean("craftVarConstantMode", false)
                        && "256".equals(source.extraFields.optString("craftVarConstant", ""))) constantInput = true;
            }
            if (registerInput && constantInput) {
                divisionIndex = target;
                registerOnIn1 = true;
                constantOnIn2 = true;
                break;
            }
        }
        assertTrue("Source integer division with register 110 / 256 should import", divisionIndex >= 0);
        assertTrue(registerOnIn1);
        assertTrue(constantOnIn2);

        int outputIndex = -1;
        for (MainActivity.FbdConnection edge : edges) {
            if (edge.source != divisionIndex) continue;
            MainActivity.Widget sink = widgets.get(edge.target);
            if (sink.extraFields.optInt("craftComponentId", -1) == 106
                    && sink.extraFields.optInt("reg", -1) == 114) {
                outputIndex = edge.target;
                break;
            }
        }
        assertTrue("Source integer division should feed VarOut register 114", outputIndex >= 0);

        CraftSystemRegisterBank registers = new CraftSystemRegisterBank();
        int count = widgets.size();
        registers.writeWord(110, 512);
        assertEquals(2.0, MainActivity.evaluateFbdBlockNumeric(widgets, edges, divisionIndex,
                new boolean[count], new boolean[count], new boolean[count], new double[count], registers), 0.0);
        assertTrue(evaluate(widgets, edges, outputIndex, registers, true));
        assertEquals(2, registers.readWord(114));

        registers.writeWord(110, 768);
        assertEquals(3.0, MainActivity.evaluateFbdBlockNumeric(widgets, edges, divisionIndex,
                new boolean[count], new boolean[count], new boolean[count], new double[count], registers), 0.0);
        assertTrue(evaluate(widgets, edges, outputIndex, registers, true));
        assertEquals(3, registers.readWord(114));
    }

    @Test public void importedHouseProjectExecutesSourceEqualityIntoBoolVarOut() throws Exception {
        JSONObject craftSource = new JSONObject(sourceFixture("дом 2025"));
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(craftSource.toString()));
        JSONObject screen = imported.getJSONArray("screens").getJSONObject(3);
        JSONArray widgetJson = screen.getJSONArray("widgets");
        List<MainActivity.Widget> widgets = new ArrayList<>();
        for (int i = 0; i < widgetJson.length(); i++)
            widgets.add(MainActivity.widgetFromJson(widgetJson.getJSONObject(i)));
        JSONArray edgeJson = screen.getJSONArray("connections");
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        for (int i = 0; i < edgeJson.length(); i++)
            edges.add(MainActivity.connectionFromJson(edgeJson.getJSONObject(i)));

        int compareIndex = -1;
        int outputIndex = -1;
        boolean registerOnIn1 = false;
        boolean constantOnIn2 = false;
        for (int candidate = 0; candidate < widgets.size(); candidate++) {
            MainActivity.Widget compare = widgets.get(candidate);
            if (compare.extraFields.optInt("craftComponentId", -1) != 107
                    || !"==".equals(compare.compareOperator)) continue;
            boolean hasRegister = false;
            boolean hasThreshold = false;
            for (MainActivity.FbdConnection edge : edges) {
                if (edge.target != candidate) continue;
                MainActivity.Widget source = widgets.get(edge.source);
                if (source.extraFields.optInt("craftComponentId", -1) != 105) continue;
                String pin = MainActivity.connectionToJson(edge).optString("targetPin", "");
                if ("IN1".equals(pin)
                        && !source.extraFields.optBoolean("craftVarConstantMode", false)
                        && source.extraFields.optInt("reg", -1) == 92) hasRegister = true;
                if ("IN2".equals(pin)
                        && source.extraFields.optBoolean("craftVarConstantMode", false)
                        && "6395".equals(source.extraFields.optString("craftVarConstant", "")))
                    hasThreshold = true;
            }
            if (!hasRegister || !hasThreshold) continue;
            for (MainActivity.FbdConnection edge : edges) {
                if (edge.source != candidate) continue;
                MainActivity.Widget sink = widgets.get(edge.target);
                if (sink.extraFields.optInt("craftComponentId", -1) == 102
                        && sink.extraFields.optInt("reg", -1) == 92
                        && sink.extraFields.optInt("bit", -1) == 1) {
                    compareIndex = candidate;
                    outputIndex = edge.target;
                    registerOnIn1 = true;
                    constantOnIn2 = true;
                    break;
                }
            }
            if (outputIndex >= 0) break;
        }
        assertTrue("Source screen 4 should contain register 92 == 6395 → VarOut register 92 bit 1",
                compareIndex >= 0 && outputIndex >= 0);
        assertTrue(registerOnIn1);
        assertTrue(constantOnIn2);

        CraftSystemRegisterBank registers = new CraftSystemRegisterBank();
        registers.writeWord(92, 6395);
        assertTrue("Craft source equality includes the exact threshold",
                evaluate(widgets, edges, compareIndex, registers, false));
        assertTrue(evaluate(widgets, edges, outputIndex, registers, true));
        assertTrue(registers.readBit(92, 1));

        registers.writeWord(92, 6392);
        assertFalse("A distinct source register value must not satisfy == 6395",
                evaluate(widgets, edges, compareIndex, registers, false));
        assertFalse(evaluate(widgets, edges, outputIndex, registers, true));
        assertFalse(registers.readBit(92, 1));
    }

    @Test public void importedHouseProjectExecutesSourceGreaterAndLessComparatorsAtBoundaries() throws Exception {
        JSONObject craftSource = new JSONObject(sourceFixture("дом 2025"));
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(craftSource.toString()));
        JSONObject screen = imported.getJSONArray("screens").getJSONObject(2);
        JSONArray widgetJson = screen.getJSONArray("widgets");
        List<MainActivity.Widget> widgets = new ArrayList<>();
        for (int i = 0; i < widgetJson.length(); i++)
            widgets.add(MainActivity.widgetFromJson(widgetJson.getJSONObject(i)));
        JSONArray edgeJson = screen.getJSONArray("connections");
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        for (int i = 0; i < edgeJson.length(); i++)
            edges.add(MainActivity.connectionFromJson(edgeJson.getJSONObject(i)));

        int greaterIndex = findSourceComparator(widgets, edges, ">", 29, "230");
        int lessIndex = findSourceComparator(widgets, edges, "<", 29, "210");
        assertTrue("Source action 1 comparator should import register 29 > 230", greaterIndex >= 0);
        assertTrue("Source action 2 comparator should import register 29 < 210", lessIndex >= 0);

        CraftSystemRegisterBank registers = new CraftSystemRegisterBank();
        registers.writeWord(29, 230);
        assertFalse("Strict greater-than is false at equality",
                evaluate(widgets, edges, greaterIndex, registers, false));
        registers.writeWord(29, 231);
        assertTrue("Strict greater-than is true above the threshold",
                evaluate(widgets, edges, greaterIndex, registers, false));

        registers.writeWord(29, 210);
        assertFalse("Strict less-than is false at equality",
                evaluate(widgets, edges, lessIndex, registers, false));
        registers.writeWord(29, 209);
        assertTrue("Strict less-than is true below the threshold",
                evaluate(widgets, edges, lessIndex, registers, false));
    }

    private static int findSourceComparator(List<MainActivity.Widget> widgets,
                                            List<MainActivity.FbdConnection> edges,
                                            String operator, int register, String threshold) throws Exception {
        for (int candidate = 0; candidate < widgets.size(); candidate++) {
            MainActivity.Widget compare = widgets.get(candidate);
            if (compare.extraFields.optInt("craftComponentId", -1) != 107
                    || !operator.equals(compare.compareOperator)) continue;
            boolean registerOnIn1 = false;
            boolean constantOnIn2 = false;
            for (MainActivity.FbdConnection edge : edges) {
                if (edge.target != candidate) continue;
                MainActivity.Widget source = widgets.get(edge.source);
                if (source.extraFields.optInt("craftComponentId", -1) != 105) continue;
                String pin = MainActivity.connectionToJson(edge).optString("targetPin", "");
                if ("IN1".equals(pin)
                        && !source.extraFields.optBoolean("craftVarConstantMode", false)
                        && source.extraFields.optInt("reg", -1) == register) registerOnIn1 = true;
                if ("IN2".equals(pin)
                        && source.extraFields.optBoolean("craftVarConstantMode", false)
                        && threshold.equals(source.extraFields.optString("craftVarConstant", "")))
                    constantOnIn2 = true;
            }
            if (registerOnIn1 && constantOnIn2) return candidate;
        }
        return -1;
    }

    @Test public void importedHouseProjectRunsSourceToggleIntoIntegerSelectorAndVarOut() throws Exception {
        JSONObject craftSource = new JSONObject(sourceFixture("дом 2025"));
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(craftSource.toString()));
        JSONObject screen = imported.getJSONArray("screens").getJSONObject(3);
        JSONArray widgetJson = screen.getJSONArray("widgets");
        List<MainActivity.Widget> widgets = new ArrayList<>();
        for (int i = 0; i < widgetJson.length(); i++)
            widgets.add(MainActivity.widgetFromJson(widgetJson.getJSONObject(i)));
        JSONArray edgeJson = screen.getJSONArray("connections");
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        for (int i = 0; i < edgeJson.length(); i++)
            edges.add(MainActivity.connectionFromJson(edgeJson.getJSONObject(i)));

        int triggerIndex = -1;
        int selectorIndex = -1;
        int outputIndex = -1;
        boolean register94OnTrigger = false;
        boolean triggerOnSelector = false;
        boolean selectorInputsFrom24And109 = false;
        for (int candidate = 0; candidate < widgets.size(); candidate++) {
            MainActivity.Widget trigger = widgets.get(candidate);
            if (trigger.extraFields.optInt("craftComponentId", -1) != 114
                    || !"T".equals(trigger.extraFields.optString("craftTriggerMode", ""))) continue;
            int input = -1;
            for (MainActivity.FbdConnection edge : edges) {
                if (edge.target != candidate
                        || !"IN".equals(MainActivity.connectionToJson(edge).optString("targetPin", ""))) continue;
                MainActivity.Widget source = widgets.get(edge.source);
                if (source.extraFields.optInt("craftComponentId", -1) == 101
                        && source.extraFields.optInt("reg", -1) == 94
                        && source.extraFields.optInt("bit", -1) == 0
                        && !source.extraFields.optBoolean("craftVarConstantMode", false)) {
                    input = edge.source;
                    register94OnTrigger = true;
                    break;
                }
            }
            if (input < 0) continue;
            for (MainActivity.FbdConnection triggerEdge : edges) {
                if (triggerEdge.source != candidate) continue;
                MainActivity.Widget sink = widgets.get(triggerEdge.target);
                if (sink.extraFields.optInt("craftComponentId", -1) != 126
                        || !"IN".equals(MainActivity.connectionToJson(triggerEdge).optString("targetPin", ""))) continue;
                int selected = triggerEdge.target;
                boolean has24 = false;
                boolean has109 = false;
                for (MainActivity.FbdConnection inputEdge : edges) {
                    if (inputEdge.target != selected) continue;
                    MainActivity.Widget value = widgets.get(inputEdge.source);
                    if (value.extraFields.optInt("craftComponentId", -1) != 105
                            || !value.extraFields.optBoolean("craftVarConstantMode", false)) continue;
                    String pin = MainActivity.connectionToJson(inputEdge).optString("targetPin", "");
                    String constant = value.extraFields.optString("craftVarConstant", "");
                    if ("I1".equals(pin) && "109".equals(constant)) has109 = true;
                    if ("I2".equals(pin) && "24".equals(constant)) has24 = true;
                }
                if (!has24 || !has109) continue;
                for (MainActivity.FbdConnection outputEdge : edges) {
                    if (outputEdge.source != selected) continue;
                    MainActivity.Widget output = widgets.get(outputEdge.target);
                    if (output.extraFields.optInt("craftComponentId", -1) == 106
                            && output.extraFields.optInt("reg", -1) == 90) {
                        triggerIndex = candidate;
                        selectorIndex = selected;
                        outputIndex = outputEdge.target;
                        triggerOnSelector = true;
                        selectorInputsFrom24And109 = true;
                        break;
                    }
                }
                if (outputIndex >= 0) break;
            }
            if (outputIndex >= 0) break;
        }

        assertTrue("Source screen 4 should contain the register 94 → Craft T → SEL → register 90 graph",
                triggerIndex >= 0 && selectorIndex >= 0 && outputIndex >= 0);
        assertTrue(register94OnTrigger);
        assertTrue(triggerOnSelector);
        assertTrue(selectorInputsFrom24And109);

        CraftSystemRegisterBank registers = new CraftSystemRegisterBank();
        assertEquals("Initial false selects source I1=109", 109, scanSourceSelector(
                widgets, edges, triggerIndex, outputIndex, 90, registers, false));
        registers.writeWord(94, 1);
        assertEquals("Rising input toggles Craft T high; true selects I2=24", 24,
                scanSourceSelector(widgets, edges, triggerIndex, outputIndex, 90, registers, true));
        assertEquals("Held input does not toggle a second time", 24,
                scanSourceSelector(widgets, edges, triggerIndex, outputIndex, 90, registers, true));
        registers.writeWord(94, 0);
        assertEquals("Falling input leaves the toggle output set", 24,
                scanSourceSelector(widgets, edges, triggerIndex, outputIndex, 90, registers, true));
        registers.writeWord(94, 1);
        assertEquals("Next rising edge toggles back to I1=109", 109,
                scanSourceSelector(widgets, edges, triggerIndex, outputIndex, 90, registers, false));
    }

    @Test public void importedHouseProjectKeepsTwoSourceToggleSelectorChainsIndependent() throws Exception {
        JSONObject craftSource = new JSONObject(sourceFixture("дом 2025"));
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(craftSource.toString()));
        JSONObject screen = imported.getJSONArray("screens").getJSONObject(3);
        JSONArray widgetJson = screen.getJSONArray("widgets");
        List<MainActivity.Widget> widgets = new ArrayList<>();
        for (int i = 0; i < widgetJson.length(); i++)
            widgets.add(MainActivity.widgetFromJson(widgetJson.getJSONObject(i)));
        JSONArray edgeJson = screen.getJSONArray("connections");
        List<MainActivity.FbdConnection> edges = new ArrayList<>();
        for (int i = 0; i < edgeJson.length(); i++)
            edges.add(MainActivity.connectionFromJson(edgeJson.getJSONObject(i)));

        int[] first = findSourceTogglePath(widgets, edges, 94, 90);
        int[] second = findSourceTogglePath(widgets, edges, 95, 91);
        assertNotNull("Source register 94 must drive its own selector and VarOut", first);
        assertNotNull("Source register 95 must drive its own selector and VarOut", second);
        assertTrue("The source chains must have distinct trigger state", first[0] != second[0]);

        CraftSystemRegisterBank registers = new CraftSystemRegisterBank();
        assertEquals(109, scanSourceSelector(widgets, edges, first[0], first[2], 90, registers, false));
        assertEquals(109, scanSourceSelector(widgets, edges, second[0], second[2], 91, registers, false));

        registers.writeWord(94, 1);
        assertEquals("Register 94's rising edge selects value 24 on register 90", 24,
                scanSourceSelector(widgets, edges, first[0], first[2], 90, registers, true));
        assertEquals("Register 95's chain stays at value 109", 109,
                scanSourceSelector(widgets, edges, second[0], second[2], 91, registers, false));

        registers.writeWord(95, 1);
        assertEquals("Register 95 toggles its own selector to value 24", 24,
                scanSourceSelector(widgets, edges, second[0], second[2], 91, registers, true));
        assertEquals("Register 94's trigger state remains set", 24,
                scanSourceSelector(widgets, edges, first[0], first[2], 90, registers, true));

        registers.writeWord(94, 0);
        assertEquals("A falling edge does not clear register 94's toggle", 24,
                scanSourceSelector(widgets, edges, first[0], first[2], 90, registers, true));
        assertEquals("Register 95 remains independently set", 24,
                scanSourceSelector(widgets, edges, second[0], second[2], 91, registers, true));

        registers.writeWord(94, 1);
        assertEquals("The next rising edge toggles only register 94's chain back", 109,
                scanSourceSelector(widgets, edges, first[0], first[2], 90, registers, false));
        assertEquals("Register 95 keeps its selected value", 24,
                scanSourceSelector(widgets, edges, second[0], second[2], 91, registers, true));

        registers.writeWord(95, 0);
        scanSourceSelector(widgets, edges, second[0], second[2], 91, registers, true);
        registers.writeWord(95, 1);
        assertEquals("Register 95's next rising edge toggles only its chain back", 109,
                scanSourceSelector(widgets, edges, second[0], second[2], 91, registers, false));
        assertEquals("Register 94 remains independently reset", 109,
                scanSourceSelector(widgets, edges, first[0], first[2], 90, registers, false));
    }

    private static int[] findSourceTogglePath(List<MainActivity.Widget> widgets,
                                             List<MainActivity.FbdConnection> edges,
                                             int inputRegister, int outputRegister) throws Exception {
        for (int trigger = 0; trigger < widgets.size(); trigger++) {
            MainActivity.Widget triggerWidget = widgets.get(trigger);
            if (triggerWidget.extraFields.optInt("craftComponentId", -1) != 114
                    || !"T".equals(triggerWidget.extraFields.optString("craftTriggerMode", ""))) continue;
            boolean sourceInput = false;
            for (MainActivity.FbdConnection edge : edges) {
                if (edge.target != trigger
                        || !"IN".equals(MainActivity.connectionToJson(edge).optString("targetPin", ""))) continue;
                MainActivity.Widget source = widgets.get(edge.source);
                sourceInput = source.extraFields.optInt("craftComponentId", -1) == 101
                        && source.extraFields.optInt("reg", -1) == inputRegister
                        && source.extraFields.optInt("bit", -1) == 0;
                if (sourceInput) break;
            }
            if (!sourceInput) continue;

            for (MainActivity.FbdConnection triggerEdge : edges) {
                if (triggerEdge.source != trigger
                        || !"IN".equals(MainActivity.connectionToJson(triggerEdge).optString("targetPin", ""))) continue;
                int selector = triggerEdge.target;
                if (widgets.get(selector).extraFields.optInt("craftComponentId", -1) != 126) continue;
                boolean firstValue = false;
                boolean secondValue = false;
                for (MainActivity.FbdConnection inputEdge : edges) {
                    if (inputEdge.target != selector) continue;
                    MainActivity.Widget value = widgets.get(inputEdge.source);
                    if (value.extraFields.optInt("craftComponentId", -1) != 105
                            || !value.extraFields.optBoolean("craftVarConstantMode", false)) continue;
                    String pin = MainActivity.connectionToJson(inputEdge).optString("targetPin", "");
                    String constant = value.extraFields.optString("craftVarConstant", "");
                    if ("I1".equals(pin) && "109".equals(constant)) firstValue = true;
                    if ("I2".equals(pin) && "24".equals(constant)) secondValue = true;
                }
                if (!firstValue || !secondValue) continue;
                for (MainActivity.FbdConnection outputEdge : edges) {
                    if (outputEdge.source != selector) continue;
                    MainActivity.Widget output = widgets.get(outputEdge.target);
                    if (output.extraFields.optInt("craftComponentId", -1) == 106
                            && output.extraFields.optInt("reg", -1) == outputRegister)
                        return new int[]{trigger, selector, outputEdge.target};
                }
            }
        }
        return null;
    }

    private static int scanSourceSelector(List<MainActivity.Widget> widgets,
                                          List<MainActivity.FbdConnection> edges,
                                          int triggerIndex, int outputIndex, int outputRegister,
                                          CraftSystemRegisterBank registers,
                                          boolean expectedTriggerOutput) {
        evaluate(widgets, edges, outputIndex, registers, true);
        assertEquals(expectedTriggerOutput, widgets.get(triggerIndex).craftToggleOutput);
        return registers.readWord(outputRegister);
    }
}
