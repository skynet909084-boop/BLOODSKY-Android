package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CraftProjectExporterFbdTest {
    private static String project() throws Exception {
        JSONObject first = new JSONObject().put("componentId", 103).put("text", "AND")
                .put("x", 10).put("y", 20).put("width", 80).put("height", 40)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 10).put("keyOut", 20).put("numOnIn", 1)
                        .put("vendorBlockData", "retained"));
        JSONObject second = new JSONObject().put("componentId", 104).put("text", "OR")
                .put("x", 100).put("y", 20).put("width", 80).put("height", 40)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 20).put("keyOut", -1).put("numOnIn", -1));
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "FBD export")
                        .put("countScreen", 1).put("jsTableNameProject_screenBackground",
                                new JSONObject().put("screen1IsColor", true).put("screen1Color", 0)))
                .put("TableComponent", new JSONArray().put(first).put(second))
                .put("TableConnections", new JSONArray());
        return CraftProjectImporter.convert(source.toString());
    }

    @Test public void writesChangedFbdInputNumberBackToCraftAndReimports() throws Exception {
        JSONObject project = new JSONObject(project());
        JSONArray edges = project.getJSONArray("screens").getJSONObject(0).getJSONArray("connections");
        assertEquals(1, edges.length());
        edges.getJSONObject(0).put("targetPin", "IN2");

        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONObject source = exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other");
        assertEquals(20, source.getInt("keyOut"));
        assertEquals(2, source.getInt("numOnIn"));
        assertEquals("retained", source.getString("vendorBlockData"));

        JSONObject reimported = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONObject edge = reimported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("connections").getJSONObject(0);
        assertEquals("IN2", edge.getString("targetPin"));
    }

    @Test public void disconnectsCraftWireAndPreservesOneToOneRows() throws Exception {
        JSONObject project = new JSONObject(project());
        project.getJSONArray("screens").getJSONObject(0).getJSONArray("connections").remove(0);
        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONObject source = exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other");
        assertEquals(-1, source.getInt("keyOut"));
        assertEquals(-1, source.getInt("numOnIn"));
        JSONObject reimported = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        assertNotNull(reimported.getJSONObject("craftSource"));
        assertEquals(0, reimported.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("connections").length());
    }

    @Test public void deletingFbdTargetRemovesItsRowAndClearsRemainingSourceWire() throws Exception {
        JSONObject project = new JSONObject(project());
        JSONObject screen = project.getJSONArray("screens").getJSONObject(0);
        screen.getJSONArray("widgets").remove(1);
        screen.getJSONArray("connections").remove(0);

        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONArray rows = exported.getJSONArray("TableComponent");
        assertEquals(1, rows.length());
        JSONObject sourceOther = rows.getJSONObject(0).getJSONObject("jsTableComponent_other");
        assertEquals(-1, sourceOther.getInt("keyOut"));
        assertEquals(-1, sourceOther.getInt("numOnIn"));
        assertEquals("retained", sourceOther.getString("vendorBlockData"));

        JSONObject reimported = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONObject restored = reimported.getJSONArray("screens").getJSONObject(0);
        assertEquals(1, restored.getJSONArray("widgets").length());
        assertEquals(0, restored.getJSONArray("connections").length());
    }

    @Test public void newlyAddedCraftNativeFbdPaletteBlocksRoundTripIdsSettingsAndTypedWires() throws Exception {
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "Craft native FBD palette")
                        .put("countScreen", 1))
                .put("TableComponent", new JSONArray())
                .put("TableConnections", new JSONArray());
        JSONObject imported = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject screen = imported.getJSONArray("screens").getJSONObject(0);
        JSONArray widgets = screen.getJSONArray("widgets");
        // Exercise a typed source -> MOVE -> sink chain alongside the added palette blocks.
        widgets.put(new JSONObject().put("type", "FBD CRAFT VAR BOOL").put("text", "Enable")
                .put("x", 0).put("y", 60).put("w", 90).put("h", 50));
        widgets.put(new JSONObject().put("type", "FBD CRAFT VAR INT").put("text", "Source")
                .put("x", 0).put("y", 0).put("w", 90).put("h", 50)
                .put("craftVarConstantMode", true).put("craftVarConstant", "23"));
        widgets.put(new JSONObject().put("type", "FBD CRAFT MOVE").put("text", "MOVE")
                .put("x", 100).put("y", 10).put("w", 90).put("h", 50));
        widgets.put(new JSONObject().put("type", "FBD CRAFT VAR OUT INT").put("text", "Sink")
                .put("x", 200).put("y", 10).put("w", 90).put("h", 50));
        screen.getJSONArray("connections")
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(0, "Q", 2, "EN")))
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(1, "Q", 2, "I")))
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(2, "Q", 3, "IN")));

        String[] types = {
                "FBD CRAFT INT TO FLOAT", "FBD CRAFT FLOAT TO INT", "FBD CRAFT SENSOR ORIENTATION",
                "FBD CRAFT SIN", "FBD CRAFT LOG", "FBD CRAFT ABS", "FBD CRAFT POW", "FBD CRAFT SEL",
                "FBD CRAFT SUNRISE", "FBD CRAFT MX", "FBD CRAFT WORD TO FLOAT", "FBD CRAFT ODTEMP",
                "FBD CRAFT SENSOR LIGHT", "FBD CRAFT SENSOR SPEED", "FBD CRAFT ARRAY",
                "FBD CRAFT CHANGE I", "FBD CRAFT CHANGE F", "FBD CRAFT MQTT SUB INT",
                "FBD CRAFT MQTT PUB FLOAT"
        };
        for (int i = 0; i < types.length; i++) {
            JSONObject widget = new JSONObject().put("type", types[i]).put("text", types[i])
                    .put("x", 20 + i * 4).put("y", 100 + i * 4).put("w", 90).put("h", 50);
            if ("FBD CRAFT SENSOR ORIENTATION".equals(types[i])) widget.put("craftSensorAxis", 3);
            if ("FBD CRAFT SUNRISE".equals(types[i]))
                widget.put("craftSunriseMode", "SS").put("latitude", 47.6).put("longitude", -122.3);
            if ("FBD CRAFT ODTEMP".equals(types[i])) widget.put("craftOdTempHost", "192.0.2.44")
                    .put("craftOdTempType", 2).put("craftOdTempResponse", 2);
            if (types[i].startsWith("FBD CRAFT MQTT ")) widget.put("craftMqttTopic", "plant/temperature")
                    .put("craftMqttType", 3).put("craftMqttServer", 2)
                    .put("craftMqttRetain", "FBD CRAFT MQTT PUB FLOAT".equals(types[i]));
            widgets.put(widget);
        }

        JSONObject exported = new JSONObject(CraftProjectExporter.export(imported.toString()));
        JSONArray rows = exported.getJSONArray("TableComponent");
        int[] expectedIds = {101, 105, 117, 106, 118, 119, 120, 121, 122, 123, 124, 126,
                127, 128, 129, 130, 131, 132, 136, 137, 138, 139, 140};
        assertEquals(expectedIds.length, rows.length());
        for (int i = 0; i < expectedIds.length; i++)
            assertEquals("Craft ID at row " + i, expectedIds[i], rows.getJSONObject(i).getInt("componentId"));
        assertEquals(3, rows.getJSONObject(6).getJSONObject("jsTableComponent_other").getInt("type"));
        JSONObject sunrise = rows.getJSONObject(12).getJSONObject("jsTableComponent_other");
        assertEquals(2, sunrise.getInt("type"));
        assertEquals(47.6, sunrise.getDouble("latitude"), 0.0);
        JSONObject odtemp = rows.getJSONObject(15).getJSONObject("jsTableComponent_other");
        assertEquals("192.0.2.44", odtemp.getString("ip"));
        assertEquals(2, odtemp.getInt("typeSensor"));
        JSONObject mqttSub = rows.getJSONObject(21).getJSONObject("jsTableComponent_other");
        assertEquals("plant/temperature", mqttSub.getString("topic"));
        assertEquals(2, mqttSub.getInt("numMqttServer"));
        assertEquals(3, mqttSub.getInt("typeQ"));
        JSONObject mqttPub = rows.getJSONObject(22).getJSONObject("jsTableComponent_other");
        assertEquals(3, mqttPub.getInt("typeIn"));
        assertEquals(true, mqttPub.getBoolean("mqttRetain"));
        assertEquals(3, exported.getJSONArray("TableComponent").getJSONObject(0)
                .getJSONObject("jsTableComponent_other").getInt("keyOut"));
        assertEquals(3, exported.getJSONArray("TableComponent").getJSONObject(1)
                .getJSONObject("jsTableComponent_other").getInt("keyOut"));
        assertEquals(4, exported.getJSONArray("TableComponent").getJSONObject(2)
                .getJSONObject("jsTableComponent_other").getInt("keyOut"));

        JSONObject restored = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONObject restoredScreen = restored.getJSONArray("screens").getJSONObject(0);
        assertEquals(expectedIds.length, restoredScreen.getJSONArray("widgets").length());
        assertEquals(3, restoredScreen.getJSONArray("connections").length());
        assertEquals("FBD CRAFT MOVE", restoredScreen.getJSONArray("widgets").getJSONObject(2).getString("type"));
        assertEquals("FBD CRAFT SENSOR ORIENTATION", restoredScreen.getJSONArray("widgets").getJSONObject(6).getString("type"));
        assertEquals(3, restoredScreen.getJSONArray("widgets").getJSONObject(6).getInt("craftSensorAxis"));
        assertEquals("plant/temperature", restoredScreen.getJSONArray("widgets").getJSONObject(21).getString("craftMqttTopic"));
    }
    @Test public void multiScreenCraftProjectRoundTripsFbdWiresTimerEditsAndOpaqueTables() throws Exception {
        JSONObject and = new JSONObject().put("componentId", 103).put("text", "AND")
                .put("x", 10).put("y", 20).put("width", 80).put("height", 40)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 10).put("keyOut", 20).put("numOnIn", 1)
                        .put("vendorBlock", "and-data"));
        JSONObject or = new JSONObject().put("componentId", 104).put("text", "OR")
                .put("x", 100).put("y", 20).put("width", 80).put("height", 40)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 20).put("keyOut", -1).put("numOnIn", -1)
                        .put("vendorBlock", "or-data"));
        JSONObject timer = new JSONObject().put("componentId", 115).put("text", "Timer")
                .put("x", 10).put("y", 20).put("width", 100).put("height", 60)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 2)
                        .put("saveKey", 30).put("keyOut", -1).put("numOnIn", -1)
                        .put("type", 1).put("typeTime", 3).put("vendorTimer", "keep"));
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "FBD round trip")
                        .put("countScreen", 2).put("vendorProjectField", 17))
                .put("TableComponent", new JSONArray().put(and).put(or).put(timer))
                .put("TableConnections", new JSONArray().put(new JSONObject()
                        .put("vendorConnection", "keep")));

        JSONObject imported = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONArray screens = imported.getJSONArray("screens");
        assertEquals(2, screens.length());
        JSONArray firstEdges = screens.getJSONObject(0).getJSONArray("connections");
        assertEquals(1, firstEdges.length());
        firstEdges.getJSONObject(0).put("targetPin", "IN2");
        JSONObject timerWidget = screens.getJSONObject(1).getJSONArray("widgets").getJSONObject(0);
        timerWidget.put("craftTimerMode", "TOF").put("craftTimerUnit", "SEK");

        JSONObject exported = new JSONObject(CraftProjectExporter.export(imported.toString()));
        JSONArray rows = exported.getJSONArray("TableComponent");
        assertEquals(3, rows.length());
        JSONObject andOther = rows.getJSONObject(0).getJSONObject("jsTableComponent_other");
        JSONObject timerOther = rows.getJSONObject(2).getJSONObject("jsTableComponent_other");
        assertEquals(20, andOther.getInt("keyOut"));
        assertEquals(2, andOther.getInt("numOnIn"));
        assertEquals("and-data", andOther.getString("vendorBlock"));
        assertEquals(2, timerOther.getInt("numScreen"));
        assertEquals(2, timerOther.getInt("type"));
        assertEquals(2, timerOther.getInt("typeTime"));
        assertEquals("keep", timerOther.getString("vendorTimer"));
        assertEquals("keep", exported.getJSONArray("TableConnections").getJSONObject(0)
                .getString("vendorConnection"));

        JSONObject restored = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        assertEquals(2, restored.getJSONArray("screens").length());
        assertEquals("IN2", restored.getJSONArray("screens").getJSONObject(0)
                .getJSONArray("connections").getJSONObject(0).getString("targetPin"));
        JSONObject restoredTimer = restored.getJSONArray("screens").getJSONObject(1)
                .getJSONArray("widgets").getJSONObject(0);
        assertEquals("TOF", restoredTimer.getString("craftTimerMode"));
        assertEquals("SEK", restoredTimer.getString("craftTimerUnit"));
    }
    @Test public void exportsNewOriginalLogicGatesWithUniqueKeysAndTheirNewWire() throws Exception {
        JSONObject existing = new JSONObject().put("componentId", 104).put("text", "Existing OR")
                .put("x", 10).put("y", 10).put("width", 80).put("height", 50)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 7).put("keyOut", -1).put("numOnIn", -1));
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "Added gates").put("countScreen", 1))
                .put("TableComponent", new JSONArray().put(existing))
                .put("TableConnections", new JSONArray());
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject screen = project.getJSONArray("screens").getJSONObject(0);
        JSONArray widgets = screen.getJSONArray("widgets");
        widgets.put(new JSONObject().put("type", "FBD И").put("text", "New AND")
                .put("x", 120).put("y", 10).put("w", 80).put("h", 50));
        widgets.put(new JSONObject().put("type", "FBD ИЛИ").put("text", "New OR")
                .put("x", 230).put("y", 10).put("w", 80).put("h", 50));
        screen.getJSONArray("connections").put(MainActivity.connectionToJson(
                MainActivity.pinnedFbdConnection(1, "Q", 2, "IN2")));

        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONArray rows = exported.getJSONArray("TableComponent");
        assertEquals(3, rows.length());
        JSONObject andOther = rows.getJSONObject(1).getJSONObject("jsTableComponent_other");
        JSONObject orOther = rows.getJSONObject(2).getJSONObject("jsTableComponent_other");
        assertEquals(103, rows.getJSONObject(1).getInt("componentId"));
        assertEquals(104, rows.getJSONObject(2).getInt("componentId"));
        assertEquals(8, andOther.getInt("saveKey"));
        assertEquals(9, orOther.getInt("saveKey"));
        assertEquals(9, andOther.getInt("keyOut"));
        assertEquals(2, andOther.getInt("numOnIn"));
        assertEquals(-1, orOther.getInt("keyOut"));

        JSONObject restored = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONObject restoredScreen = restored.getJSONArray("screens").getJSONObject(0);
        assertEquals(3, restoredScreen.getJSONArray("widgets").length());
        JSONArray restoredEdges = restoredScreen.getJSONArray("connections");
        assertEquals(1, restoredEdges.length());
        assertEquals(1, restoredEdges.getJSONObject(0).getInt("source"));
        assertEquals(2, restoredEdges.getJSONObject(0).getInt("target"));
        assertEquals("IN2", restoredEdges.getJSONObject(0).getString("targetPin"));
    }

    @Test public void exportsNewCraftCounterTriggerAndTimerWithTypedWiresAndModes() throws Exception {
        JSONObject and = new JSONObject().put("componentId", 103).put("text", "AND")
                .put("x", 10).put("y", 10).put("width", 80).put("height", 50)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 7).put("keyOut", -1).put("numOnIn", -1));
        JSONObject or = new JSONObject().put("componentId", 104).put("text", "OR")
                .put("x", 100).put("y", 10).put("width", 80).put("height", 50)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 12).put("keyOut", -1).put("numOnIn", -1));
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "New Craft FBD blocks")
                        .put("countScreen", 1))
                .put("TableComponent", new JSONArray().put(and).put(or))
                .put("TableConnections", new JSONArray());
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject screen = project.getJSONArray("screens").getJSONObject(0);
        JSONArray widgets = screen.getJSONArray("widgets");
        widgets.put(new JSONObject().put("type", "FBD CRAFT CTU").put("text", "Counter")
                .put("x", 200).put("y", 10).put("w", 90).put("h", 50)
                .put("craftOther", new JSONObject().put("vendorCounter", "preserve")));
        widgets.put(new JSONObject().put("type", "FBD CRAFT T").put("text", "Trigger")
                .put("x", 300).put("y", 10).put("w", 90).put("h", 50)
                .put("craftTriggerMode", "F"));
        widgets.put(new JSONObject().put("type", "FBD CRAFT TIMER").put("text", "Timer")
                .put("x", 400).put("y", 10).put("w", 90).put("h", 50)
                .put("craftTimerMode", "TP").put("craftTimerUnit", "MIN"));

        screen.getJSONArray("connections")
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(0, "Q", 2, "+")))
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(1, "Q", 3, "IN")))
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(3, "Q", 4, "IN")));

        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONArray rows = exported.getJSONArray("TableComponent");
        assertEquals(5, rows.length());
        assertEquals(116, rows.getJSONObject(2).getInt("componentId"));
        assertEquals(114, rows.getJSONObject(3).getInt("componentId"));
        assertEquals(115, rows.getJSONObject(4).getInt("componentId"));
        JSONObject counterOther = rows.getJSONObject(2).getJSONObject("jsTableComponent_other");
        JSONObject triggerOther = rows.getJSONObject(3).getJSONObject("jsTableComponent_other");
        JSONObject timerOther = rows.getJSONObject(4).getJSONObject("jsTableComponent_other");
        assertEquals("preserve", counterOther.getString("vendorCounter"));
        assertEquals(3, triggerOther.getInt("type"));
        assertEquals(3, timerOther.getInt("type"));
        assertEquals(3, timerOther.getInt("typeTime"));
        assertEquals(counterOther.getInt("saveKey"), rows.getJSONObject(0)
                .getJSONObject("jsTableComponent_other").getInt("keyOut"));
        assertEquals(triggerOther.getInt("saveKey"), rows.getJSONObject(1)
                .getJSONObject("jsTableComponent_other").getInt("keyOut"));
        assertEquals(timerOther.getInt("saveKey"), triggerOther.getInt("keyOut"));
        assertEquals(1, triggerOther.getInt("numOnIn"));

        JSONObject restored = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONObject restoredScreen = restored.getJSONArray("screens").getJSONObject(0);
        JSONArray restoredEdges = restoredScreen.getJSONArray("connections");
        assertEquals(3, restoredEdges.length());
        assertEquals("+", restoredEdges.getJSONObject(0).getString("targetPin"));
        assertEquals("IN", restoredEdges.getJSONObject(1).getString("targetPin"));
        assertEquals("IN", restoredEdges.getJSONObject(2).getString("targetPin"));
        assertEquals(116, restoredScreen.getJSONArray("widgets").getJSONObject(2)
                .getInt("craftComponentId"));
        assertEquals("F", restoredScreen.getJSONArray("widgets").getJSONObject(3)
                .getString("craftTriggerMode"));
        JSONObject restoredTimer = restoredScreen.getJSONArray("widgets").getJSONObject(4);
        assertEquals("TP", restoredTimer.getString("craftTimerMode"));
        assertEquals("MIN", restoredTimer.getString("craftTimerUnit"));
    }


    @Test public void exportsNewCraftArithmeticAndCompareBlocksWithTypedWiresAndOperators() throws Exception {
        JSONObject intInput = new JSONObject().put("componentId", 105).put("text", "Integer input")
                .put("x", 10).put("y", 10).put("width", 80).put("height", 50)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 4).put("keyOut", -1).put("numOnIn", -1));
        JSONObject floatInput = new JSONObject().put("componentId", 108).put("text", "Float input")
                .put("x", 10).put("y", 90).put("width", 80).put("height", 50)
                .put("jsTableComponent_other", new JSONObject().put("numScreen", 1)
                        .put("saveKey", 9).put("keyOut", -1).put("numOnIn", -1));
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "New Craft math blocks")
                        .put("countScreen", 1))
                .put("TableComponent", new JSONArray().put(intInput).put(floatInput))
                .put("TableConnections", new JSONArray());
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject screen = project.getJSONArray("screens").getJSONObject(0);
        JSONArray widgets = screen.getJSONArray("widgets");
        widgets.put(new JSONObject().put("type", "FBD CRAFT ARITHMETIC INT").put("text", "INT *")
                .put("x", 120).put("y", 10).put("w", 90).put("h", 50)
                .put("compareOperator", "*"));
        widgets.put(new JSONObject().put("type", "FBD CRAFT COMPARE").put("text", "INT !=")
                .put("x", 230).put("y", 10).put("w", 90).put("h", 50)
                .put("compareOperator", "!="));
        widgets.put(new JSONObject().put("type", "FBD CRAFT ARITHMETIC FLOAT").put("text", "FLOAT /")
                .put("x", 120).put("y", 90).put("w", 90).put("h", 50)
                .put("compareOperator", "/"));
        widgets.put(new JSONObject().put("type", "FBD CRAFT COMPARE FLOAT").put("text", "FLOAT <=")
                .put("x", 230).put("y", 90).put("w", 90).put("h", 50)
                .put("compareOperator", "<="));
        screen.getJSONArray("connections")
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(0, "Q", 2, "IN1")))
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(2, "Q", 3, "IN1")))
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(1, "Q", 4, "IN2")))
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(4, "Q", 5, "IN2")));

        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONArray rows = exported.getJSONArray("TableComponent");
        assertEquals(6, rows.length());
        assertEquals(110, rows.getJSONObject(2).getInt("componentId"));
        assertEquals(107, rows.getJSONObject(3).getInt("componentId"));
        assertEquals(111, rows.getJSONObject(4).getInt("componentId"));
        assertEquals(112, rows.getJSONObject(5).getInt("componentId"));
        JSONObject intMathOther = rows.getJSONObject(2).getJSONObject("jsTableComponent_other");
        JSONObject intCompareOther = rows.getJSONObject(3).getJSONObject("jsTableComponent_other");
        JSONObject floatMathOther = rows.getJSONObject(4).getJSONObject("jsTableComponent_other");
        JSONObject floatCompareOther = rows.getJSONObject(5).getJSONObject("jsTableComponent_other");
        assertEquals(3, intMathOther.getInt("compareAction"));
        assertEquals(6, intCompareOther.getInt("compareAction"));
        assertEquals(4, floatMathOther.getInt("compareAction"));
        assertEquals(3, floatCompareOther.getInt("compareAction"));
        assertEquals(intMathOther.getInt("saveKey"), rows.getJSONObject(0)
                .getJSONObject("jsTableComponent_other").getInt("keyOut"));
        assertEquals(intCompareOther.getInt("saveKey"), intMathOther.getInt("keyOut"));
        assertEquals(floatMathOther.getInt("saveKey"), rows.getJSONObject(1)
                .getJSONObject("jsTableComponent_other").getInt("keyOut"));
        assertEquals(floatCompareOther.getInt("saveKey"), floatMathOther.getInt("keyOut"));
        assertEquals(1, intMathOther.getInt("numOnIn"));
        assertEquals(2, floatMathOther.getInt("numOnIn"));

        JSONObject restored = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONObject restoredScreen = restored.getJSONArray("screens").getJSONObject(0);
        assertEquals(4, restoredScreen.getJSONArray("connections").length());
        assertEquals("IN1", restoredScreen.getJSONArray("connections").getJSONObject(0)
                .getString("targetPin"));
        assertEquals("IN2", restoredScreen.getJSONArray("connections").getJSONObject(1)
                .getString("targetPin"));
        assertEquals("IN1", restoredScreen.getJSONArray("connections").getJSONObject(2)
                .getString("targetPin"));
        assertEquals("IN2", restoredScreen.getJSONArray("connections").getJSONObject(3)
                .getString("targetPin"));
        JSONArray restoredWidgets = restoredScreen.getJSONArray("widgets");
        assertEquals(110, restoredWidgets.getJSONObject(2).getInt("craftComponentId"));
        assertEquals("*", restoredWidgets.getJSONObject(2).getString("compareOperator"));
        assertEquals("!=", restoredWidgets.getJSONObject(3).getString("compareOperator"));
        assertEquals("/", restoredWidgets.getJSONObject(4).getString("compareOperator"));
        assertEquals("<=", restoredWidgets.getJSONObject(5).getString("compareOperator"));
    }


    @Test public void exportsNewCraftRegisterAndNamedVariablesWithSettingsAndWires() throws Exception {
        JSONObject source = new JSONObject()
                .put("TableNameProject", new JSONObject().put("name", "New Craft variables")
                        .put("countScreen", 1))
                .put("TableComponent", new JSONArray())
                .put("TableConnections", new JSONArray());
        JSONObject project = new JSONObject(CraftProjectImporter.convert(source.toString()));
        JSONObject screen = project.getJSONArray("screens").getJSONObject(0);
        JSONArray widgets = screen.getJSONArray("widgets");
        widgets.put(new JSONObject().put("type", "FBD CRAFT VAR BOOL").put("text", "Bool source")
                .put("x", 10).put("y", 10).put("w", 90).put("h", 50)
                .put("craftVarBit", 5));
        widgets.put(new JSONObject().put("type", "FBD CRAFT VAR OUT BOOL").put("text", "Bool sink")
                .put("x", 120).put("y", 10).put("w", 90).put("h", 50)
                .put("craftVarBit", 5));
        widgets.put(new JSONObject().put("type", "FBD CRAFT VAR INT").put("text", "Int source")
                .put("x", 10).put("y", 80).put("w", 90).put("h", 50)
                .put("craftVarConstantMode", true).put("craftVarConstant", "37"));
        widgets.put(new JSONObject().put("type", "FBD CRAFT VAR OUT INT").put("text", "Int sink")
                .put("x", 120).put("y", 80).put("w", 90).put("h", 50));
        widgets.put(new JSONObject().put("type", "FBD CRAFT VAR FLOAT").put("text", "Float source")
                .put("x", 10).put("y", 150).put("w", 90).put("h", 50));
        widgets.put(new JSONObject().put("type", "FBD CRAFT VAR OUT FLOAT").put("text", "Float sink")
                .put("x", 120).put("y", 150).put("w", 90).put("h", 50)
                .put("craftSwapWord", true).put("craftSwapByte", true));
        widgets.put(new JSONObject().put("type", "FBD CRAFT VAR NAME IN BOOL").put("text", "Named input")
                .put("x", 10).put("y", 220).put("w", 90).put("h", 50)
                .put("craftVariableName", "enabled")
                .put("craftNamedConstantMode", true).put("craftNamedConstant", "1"));
        widgets.put(new JSONObject().put("type", "FBD CRAFT VAR NAME OUT BOOL").put("text", "Named output")
                .put("x", 120).put("y", 220).put("w", 90).put("h", 50)
                .put("craftVariableName", "enabled"));
        screen.getJSONArray("connections")
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(0, "Q", 1, "IN")))
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(2, "Q", 3, "IN")))
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(4, "Q", 5, "IN")))
                .put(MainActivity.connectionToJson(MainActivity.pinnedFbdConnection(6, "Q", 7, "IN")));

        JSONObject exported = new JSONObject(CraftProjectExporter.export(project.toString()));
        JSONArray rows = exported.getJSONArray("TableComponent");
        assertEquals(8, rows.length());
        int[] expectedIds = {101, 102, 105, 106, 108, 109, 133, 134};
        for (int i = 0; i < expectedIds.length; i++)
            assertEquals(expectedIds[i], rows.getJSONObject(i).getInt("componentId"));
        JSONObject boolOther = rows.getJSONObject(0).getJSONObject("jsTableComponent_other");
        JSONObject intOther = rows.getJSONObject(2).getJSONObject("jsTableComponent_other");
        JSONObject floatSinkOther = rows.getJSONObject(5).getJSONObject("jsTableComponent_other");
        JSONObject namedInputOther = rows.getJSONObject(6).getJSONObject("jsTableComponent_other");
        JSONObject namedOutputOther = rows.getJSONObject(7).getJSONObject("jsTableComponent_other");
        assertEquals(5, boolOther.getInt("bit"));
        assertEquals(true, intOther.getBoolean("isConstant"));
        assertEquals("37", intOther.getString("constant"));
        assertEquals(true, floatSinkOther.getBoolean("isHighRegister"));
        assertEquals(true, floatSinkOther.getBoolean("isHighByte"));
        assertEquals(1, namedInputOther.getInt("typeVar"));
        assertEquals("enabled", namedInputOther.getString("typeVarValue"));
        assertEquals(1, namedOutputOther.getInt("typeVar"));
        assertEquals("enabled", namedOutputOther.getString("typeVarValue"));
        assertEquals(namedOutputOther.getInt("saveKey"), rows.getJSONObject(6)
                .getJSONObject("jsTableComponent_other").getInt("keyOut"));
        assertEquals(1, rows.getJSONObject(6).getJSONObject("jsTableComponent_other").getInt("numOnIn"));

        JSONObject restored = new JSONObject(CraftProjectImporter.convert(exported.toString()));
        JSONObject restoredScreen = restored.getJSONArray("screens").getJSONObject(0);
        assertEquals(8, restoredScreen.getJSONArray("widgets").length());
        assertEquals(4, restoredScreen.getJSONArray("connections").length());
        JSONObject restoredInt = restoredScreen.getJSONArray("widgets").getJSONObject(2);
        assertEquals(true, restoredInt.getBoolean("craftVarConstantMode"));
        assertEquals("37", restoredInt.getString("craftVarConstant"));
        JSONObject restoredFloatSink = restoredScreen.getJSONArray("widgets").getJSONObject(5);
        assertEquals(true, restoredFloatSink.getBoolean("craftSwapWord"));
        assertEquals(true, restoredFloatSink.getBoolean("craftSwapByte"));
        assertEquals("enabled", restoredScreen.getJSONArray("widgets").getJSONObject(6)
                .getString("craftVariableName"));
    }

}
