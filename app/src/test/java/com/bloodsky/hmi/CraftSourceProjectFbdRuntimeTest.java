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
}
