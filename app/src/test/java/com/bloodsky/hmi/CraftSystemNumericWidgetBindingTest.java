package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CraftSystemNumericWidgetBindingTest {
    @Test public void integerSystemWidgetReadsAndWritesOnlyAfterOptIn() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        JSONObject widget = numericWidget(120, CraftNumericInput.INTEGER,
                CraftNumericInput.SIGNED_16, false);

        CraftSystemRegisterAccess.writeInt(120, 0xffff, bank);
        assertEquals("-1", CraftSystemNumericWidgetBinding.readIntegerState(widget, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWriteInteger(widget));
        widget.put("craftNumericEnabled", true);
        assertTrue(CraftSystemNumericWidgetBinding.writeInteger(widget, -23, bank));
        assertEquals("-23", CraftSystemNumericWidgetBinding.readIntegerState(widget, bank));
    }

    @Test public void serviceWritesRemainDisabledAndFloatReadsSourceOrder() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        JSONObject service = numericWidget(9, CraftNumericInput.INTEGER,
                CraftNumericInput.UNSIGNED_16, true);
        JSONObject floating = numericWidget(120, CraftNumericInput.FLOAT32,
                CraftNumericInput.UNSIGNED_16, false);
        JSONObject serviceFloat = numericWidget(9, CraftNumericInput.FLOAT32,
                CraftNumericInput.UNSIGNED_16, true);
        JSONObject invalidFloatRead = numericWidget(1999, CraftNumericInput.FLOAT32,
                CraftNumericInput.UNSIGNED_16, false);
        JSONObject overflowFloat = numericWidget(Integer.MAX_VALUE, CraftNumericInput.FLOAT32,
                CraftNumericInput.UNSIGNED_16, true);
        JSONObject badFloat = numericWidget(1999, CraftNumericInput.FLOAT32,
                CraftNumericInput.UNSIGNED_16, true);
        JSONObject outOfRange = numericWidget(2000, CraftNumericInput.INTEGER,
                CraftNumericInput.UNSIGNED_16, true);
        JSONObject firstUserRegister = numericWidget(10, CraftNumericInput.INTEGER,
                CraftNumericInput.UNSIGNED_16, true);
        JSONObject lastUserRegister = numericWidget(1999, CraftNumericInput.INTEGER,
                CraftNumericInput.UNSIGNED_16, true);
        JSONObject lastFloatPair = numericWidget(1998, CraftNumericInput.FLOAT32,
                CraftNumericInput.UNSIGNED_16, true);
        JSONObject partialFloatPair = numericWidget(1999, CraftNumericInput.FLOAT32,
                CraftNumericInput.UNSIGNED_16, true);

        assertNotNull(CraftSystemNumericWidgetBinding.readIntegerState(service, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWriteInteger(service));
        assertFalse(CraftSystemNumericWidgetBinding.writeInteger(service, 5, bank));
        assertNull(CraftSystemNumericWidgetBinding.readIntegerState(floating, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWrite(floating));
        assertFalse(CraftSystemNumericWidgetBinding.writeValue(floating, 5, bank));
        bank.writeWord(120, 0x4148);
        bank.writeWord(121, 0x0000);
        floating.put("craftNumericDecimals", 2);
        assertEquals("12.5", CraftSystemNumericWidgetBinding.readFloatState(floating, bank));
        assertEquals("12.5", CraftSystemNumericWidgetBinding.readState(floating, bank));
        floating.put("craftNumericEnabled", true);
        assertTrue(CraftSystemNumericWidgetBinding.canWrite(floating));
        assertTrue(CraftSystemNumericWidgetBinding.writeValue(floating, 23.125d, bank));
        assertEquals(23.125f, bank.readFloat32(120, false, false), 0.00001f);
        CraftServiceRegisters.global().clear();
        CraftServiceRegisters.global().writeArrayWord(0, 0x3f80);
        CraftServiceRegisters.global().writeArrayWord(1, 0x0000);
        bank.writeWord(10, 0x0000);
        JSONObject firstFloat = numericWidget(0, CraftNumericInput.FLOAT32,
                CraftNumericInput.UNSIGNED_16, false);
        assertEquals("1.0", CraftSystemNumericWidgetBinding.readFloatState(firstFloat, bank));
        assertNotNull(CraftSystemNumericWidgetBinding.readFloatState(serviceFloat, bank));
        assertNull(CraftSystemNumericWidgetBinding.readFloatState(invalidFloatRead, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWrite(serviceFloat));
        assertFalse(CraftSystemNumericWidgetBinding.writeValue(serviceFloat, 1.5d, bank));
        CraftServiceRegisters.global().clear();
        assertNull(CraftSystemNumericWidgetBinding.readFloatState(badFloat, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWrite(badFloat));
        assertNull(CraftSystemNumericWidgetBinding.readFloatState(overflowFloat, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWrite(overflowFloat));
        assertNull(CraftSystemNumericWidgetBinding.readIntegerState(outOfRange, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWriteInteger(outOfRange));
        assertFalse(CraftSystemNumericWidgetBinding.writeInteger(outOfRange, 5, bank));
        assertTrue(CraftSystemNumericWidgetBinding.canWriteInteger(firstUserRegister));
        assertTrue(CraftSystemNumericWidgetBinding.canWriteInteger(lastUserRegister));
        assertTrue(CraftSystemNumericWidgetBinding.canWrite(lastFloatPair));
        assertFalse(CraftSystemNumericWidgetBinding.canWrite(partialFloatPair));
    }

    @Test public void unsupportedSlotOrWidgetNeverActivates() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        JSONObject device = numericWidget(120, CraftNumericInput.INTEGER,
                CraftNumericInput.UNSIGNED_16, true);
        device.getJSONObject("craftBindingResolution").put("kind", "device");
        JSONObject wrongType = numericWidget(120, CraftNumericInput.INTEGER,
                CraftNumericInput.UNSIGNED_16, true);
        wrongType.put("craftComponentId", 7);

        assertNull(CraftSystemNumericWidgetBinding.readState(device, bank));
        assertFalse(CraftSystemNumericWidgetBinding.canWriteInteger(device));
        assertNull(CraftSystemNumericWidgetBinding.readState(wrongType, bank));
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
