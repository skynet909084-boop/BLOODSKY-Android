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
        assertEquals("On\nOff\n\nThird", CraftBitList.toEditorLines(labels));
        assertEquals(3, CraftBitList.availableMask(labels));
        StringBuilder overflow = new StringBuilder();
        for (int i = 0; i < 16; i++) overflow.append("x\n");
        overflow.append("overflow");
        assertThrows(IllegalArgumentException.class, () -> CraftBitList.parseEditorLines(overflow.toString()));
    }
}
