package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CraftBitListTest {
    @Test public void readsCraftTextBitWhetherObjectOrJsonString() throws Exception {
        JSONObject values = new JSONObject().put("textBit0", "Start").put("textBit15", "Stop");
        assertArrayEquals(CraftBitList.labelsFromCraft(new JSONObject().put("textBit", values)),
                CraftBitList.labelsFromCraft(new JSONObject().put("textBit", values.toString())));
        assertEquals("Start", CraftBitList.labelsFromCraft(new JSONObject().put("textBit", values))[0]);
        assertEquals("Stop", CraftBitList.labelsFromCraft(new JSONObject().put("textBit", values))[15]);
    }

    @Test public void togglesOnlyNamedBitsAndPreservesSixteenBitUnsignedMask() {
        String[] labels = new String[16]; labels[0] = "On"; labels[15] = "Off";
        assertEquals(0x8001, CraftBitList.toggle(1, 15, labels));
        assertEquals(0x8000, CraftBitList.toggle(0x8001, 0, labels));
        assertEquals(0xffff, CraftBitList.fromRegister(65535));
        assertEquals(0x8001, CraftBitList.availableMask(labels));
        assertThrows(IllegalArgumentException.class, () -> CraftBitList.toggle(0, 1, labels));
        assertThrows(IllegalArgumentException.class, () -> CraftBitList.fromRegister(-1));
    }

    @Test public void editorAlwaysNormalizesToSixteenBoundedLines() {
        String[] labels = CraftBitList.parseEditorLines("On\nOff\n\nThird");
        assertEquals(16, labels.length);
        String serialized = CraftBitList.toEditorLines(labels);
        assertEquals(16, serialized.split("\\R", -1).length);
        assertArrayEquals(labels, CraftBitList.parseEditorLines(serialized));
        assertEquals(11, CraftBitList.availableMask(labels));
        StringBuilder overflow = new StringBuilder();
        for (int i = 0; i < 16; i++) overflow.append("x\n");
        overflow.append("overflow");
        assertThrows(IllegalArgumentException.class, () -> CraftBitList.parseEditorLines(overflow.toString()));
    }
    @Test public void compactPropertiesKeepAll16LabelsAndOriginalSourceKeys() throws Exception {
        String[] labels = new String[16];
        for (int i = 0; i < labels.length; i++) labels[i] = "Пункт " + i;
        JSONObject fields = new JSONObject()
                .put("connectionInfo", "")
                .put("craftListEnabled", false)
                .put("vendorRoot", 77)
                .put("craftOther", new JSONObject()
                        .put("textBit", new JSONObject().put("sourceOpaque", "keep"))
                        .put("vendorField", true))
                .put("craftConnectionInfo", new JSONObject()
                        .put("addressRegister", 17).put("originalUnit", 3));
        JSONObject edited = CraftBitList.withSettings(fields, 65535, labels, false, true);
        assertEquals(65535, CraftBitList.sourceRegister(edited));
        assertArrayEquals(labels, CraftBitList.fromJson(edited.getJSONArray("craftListLabels")));
        assertArrayEquals(labels, CraftBitList.labelsFromCraft(edited.getJSONObject("craftOther")));
        assertFalse(edited.getBoolean("craftListVisibleInWork"));
        assertTrue(edited.getBoolean("craftListCheckNoChange"));
        assertFalse(edited.getBoolean("craftListEnabled"));
        assertEquals("", edited.getString("connectionInfo"));
        assertEquals(77, edited.getInt("vendorRoot"));
        assertTrue(edited.getJSONObject("craftOther").getBoolean("vendorField"));
        assertEquals("keep", edited.getJSONObject("craftOther")
                .getJSONObject("textBit").getString("sourceOpaque"));
        assertEquals(3, edited.getJSONObject("craftConnectionInfo").getInt("originalUnit"));
        assertEquals(17, CraftBitList.sourceRegister(fields));
    }

    @Test public void stringEncodedOriginalListFieldsStayStringEncoded() throws Exception {
        JSONObject fields = new JSONObject()
                .put("craftConnectionInfo", "{\"addressRegister\":9,\"sourceFlag\":5}")
                .put("craftOther", "{\"textBit\":\"{\\\"textBit0\\\":\\\"Original\\\",\\\"vendor\\\":true}\",\"otherFlag\":4}");
        String[] labels = CraftBitList.parseEditorLines("First\nSecond");
        JSONObject edited = CraftBitList.withSettings(fields, 42, labels, true, false);
        assertTrue(edited.get("craftConnectionInfo") instanceof String);
        assertTrue(edited.get("craftOther") instanceof String);
        assertEquals(5, new JSONObject(edited.getString("craftConnectionInfo")).getInt("sourceFlag"));
        JSONObject other = new JSONObject(edited.getString("craftOther"));
        assertEquals(4, other.getInt("otherFlag"));
        assertTrue(other.get("textBit") instanceof String);
        assertEquals("First", new JSONObject(other.getString("textBit")).getString("textBit0"));
        assertTrue(new JSONObject(other.getString("textBit")).getBoolean("vendor"));
    }

    @Test public void changingListRegisterRequiresFreshExplicitTcpPermission() throws Exception {
        String[] labels = CraftBitList.parseEditorLines("Output 0\nOutput 1");
        JSONObject source = new JSONObject().put("craftListEnabled", true)
                .put("craftConnectionInfo", "{\"addressRegister\":40011,\"vendor\":99}")
                .put("craftOther", "{\"vendor\":{\"keep\":true}}")
                .put("connectionInfo", "tcp://192.0.2.1:502/1/40011");
        JSONObject cosmetic = CraftBitList.withSettings(source, 40011, labels, true, false);
        assertTrue(cosmetic.getBoolean("craftListEnabled"));
        JSONObject changed = CraftBitList.withSettings(source, 40012, labels, true, false);
        assertFalse(changed.getBoolean("craftListEnabled"));
        assertEquals("tcp://192.0.2.1:502/1/40011",
                changed.getString("connectionInfo"));
        assertTrue(changed.get("craftConnectionInfo") instanceof String);
        assertTrue(changed.get("craftOther") instanceof String);
        assertEquals(99, new JSONObject(changed.getString("craftConnectionInfo")).getInt("vendor"));
        assertTrue(new JSONObject(changed.getString("craftOther")).getJSONObject("vendor")
                .getBoolean("keep"));
        assertTrue(source.getBoolean("craftListEnabled"));
        assertEquals(40011, CraftBitList.sourceRegister(source));
    }

    @Test public void invalidBitListPropertiesDoNotMutateProjectData() throws Exception {
        JSONObject fields = new JSONObject().put("original", 99);
        String[] labels = new String[16];
        assertThrows(IllegalArgumentException.class,
                () -> CraftBitList.withSettings(fields, -1, labels, true, false));
        assertThrows(IllegalArgumentException.class,
                () -> CraftBitList.withSettings(fields, 65536, labels, true, false));
        assertThrows(IllegalArgumentException.class,
                () -> CraftBitList.withSettings(fields, 1, new String[15], true, false));
        labels[2] = new String(new char[81]).replace('\u0000', 'A');
        assertThrows(IllegalArgumentException.class,
                () -> CraftBitList.withSettings(fields, 1, labels, true, false));
        assertEquals(99, fields.getInt("original"));
        assertFalse(fields.has("craftConnectionInfo"));
    }
}
