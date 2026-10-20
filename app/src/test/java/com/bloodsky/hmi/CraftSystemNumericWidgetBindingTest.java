package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CraftSystemNumericWidgetBindingTest {
    @Test public void integerSystemWidgetReadsAndWritesOnlyAfterOptIn() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        JSONObject widget = numericWidget(120, CraftNumericInput.INTEGER,
                CraftNumericInput.SIGNED_16, true);

        CraftSystemRegisterAccess.writeInt(120, 0xffff, bank);
        assertEquals("-1", CraftSystemNumericWidgetBinding.readIntegerState(widget, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWriteInteger(widget));
        assertTrue(CraftSystemNumericWidgetBinding.writeInteger(widget, -23, bank));
        assertEquals("-23", CraftSystemNumericWidgetBinding.readIntegerState(widget, bank));
    }

    @Test public void serviceAndFloatBindingsDoNotWriteThroughThisPath() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        JSONObject service = numericWidget(9, CraftNumericInput.INTEGER,
                CraftNumericInput.UNSIGNED_16, true);
        JSONObject floating = numericWidget(120, CraftNumericInput.FLOAT32,
                CraftNumericInput.UNSIGNED_16, true);
        JSONObject outOfRange = numericWidget(2000, CraftNumericInput.INTEGER,
                CraftNumericInput.UNSIGNED_16, true);

        assertEquals("0", CraftSystemNumericWidgetBinding.readIntegerState(service, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWriteInteger(service));
        assertNull(CraftSystemNumericWidgetBinding.readIntegerState(floating, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWriteInteger(floating));
        assertNull(CraftSystemNumericWidgetBinding.readIntegerState(outOfRange, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWriteInteger(outOfRange));
    }

    @Test public void unsupportedSlotOrWidgetNeverActivates() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        JSONObject device = numericWidget(120, CraftNumericInput.INTEGER,
                CraftNumericInput.UNSIGNED_16, true);
        device.getJSONObject("craftBindingResolution").put("kind", "device");
        JSONObject wrongType = numericWidget(120, CraftNumericInput.INTEGER,
                CraftNumericInput.UNSIGNED_16, true);
        wrongType.put("craftComponentId", 7);

        assertNull(CraftSystemNumericWidgetBinding.readIntegerState(device, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWriteInteger(device));
        assertNull(CraftSystemNumericWidgetBinding.readIntegerState(wrongType, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWriteInteger(wrongType));
    }

    private static JSONObject numericWidget(int address, int mode, int intType,
                                            boolean enabled) throws Exception {
        JSONObject widget = new JSONObject();
        widget.put("type", "Числовой ввод Craft");
        widget.put("craftComponentId", 8);
        widget.put("craftNumericMode", mode);
        widget.put("craftNumericTypeInt", intType);
        widget.put("craftNumericMin", intType == CraftNumericInput.SIGNED_16 ? -32768 : 0);
        widget.put("craftNumericMax", intType == CraftNumericInput.SIGNED_16 ? 32767 : 65535);
        widget.put("craftNumericEnabled", enabled);
        widget.put("craftBindingResolution", new JSONObject()
                .put("kind", "global").put("sourceSlot", 100));
        widget.put("craftConnectionInfo", new JSONObject().put("addressRegister", address));
        return widget;
    }
}
