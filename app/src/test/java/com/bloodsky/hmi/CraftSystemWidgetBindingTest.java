package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CraftSystemWidgetBindingTest {
    @Test public void dynamicTextReadsBitFromImportedGlobalSystemBinding() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        CraftSystemRegisterAccess.writeBool(120, 7, true, 0L, bank);
        JSONObject widget = dynamicTextWidget(120, 7, 1, "global", 100);

        assertEquals("1", CraftSystemWidgetBinding.readDynamicTextState(widget, bank));
        CraftSystemRegisterAccess.writeBool(120, 7, false, 0L, bank);
        assertEquals("0", CraftSystemWidgetBinding.readDynamicTextState(widget, bank));
    }

    @Test public void systemServiceBitsAreReadFromCraftServiceRegisterProvider() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        CraftServiceRegisters service = FbdCraftRegisterStore.serviceRegisters();
        boolean wasCharging = service.readRawBit(0, 15) != 0;
        service.setCharging(true);
        JSONObject widget = dynamicTextWidget(0, 15, 1, "global", 100);

        assertEquals("1", CraftSystemWidgetBinding.readDynamicTextState(widget, bank));
        service.setCharging(wasCharging);
    }

    @Test public void externalOrUnsupportedBindingsAreNotActivated() throws Exception {
        CraftSystemRegisterBank bank = new CraftSystemRegisterBank();
        JSONObject network = dynamicTextWidget(120, 7, 1, "device", 2);
        JSONObject wrongRequest = dynamicTextWidget(120, 7, 3, "global", 100);
        JSONObject wrongType = dynamicTextWidget(120, 7, 1, "global", 100);
        wrongType.put("type", "Кнопка");

        assertNull(CraftSystemWidgetBinding.readDynamicTextState(network, bank));
        assertNull(CraftSystemWidgetBinding.readDynamicTextState(wrongRequest, bank));
        assertNull(CraftSystemWidgetBinding.readDynamicTextState(wrongType, bank));
    }

    private static JSONObject dynamicTextWidget(int address, int bit, int request,
                                                String sourceKind, int sourceSlot) throws Exception {
        JSONObject widget = new JSONObject();
        widget.put("type", "Динамический текст");
        widget.put("craftComponentId", 7);
        widget.put("craftBindingResolution", new JSONObject()
                .put("kind", sourceKind).put("sourceSlot", sourceSlot));
        widget.put("craftConnectionInfo", new JSONObject()
                .put("addressRegister", address).put("addressBit", bit).put("typeRequest", request));
        return widget;
    }
}
