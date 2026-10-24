package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Runtime pin matching must not wrap malformed original macro type codes. */
public final class FbdCraftMacroPortExactTypeTest {
    private static MainActivity.Widget input(Object raw) throws Exception {
        MainActivity.Widget widget = new MainActivity.Widget();
        widget.type = "FBD CRAFT MACRO IN";
        widget.extraFields.put("craftMacroPortName", "Input");
        if (raw != null) widget.extraFields.put("craftMacroPortType", raw);
        return widget;
    }

    @Test public void legitimateOriginalMacroPinTypesRemainDistinct() throws Exception {
        assertEquals("BOOL", FbdCraftPinTypes.typeForWidget(input(1), "Q", true));
        assertEquals("INT", FbdCraftPinTypes.typeForWidget(input(2), "Q", true));
        assertEquals("FLOAT", FbdCraftPinTypes.typeForWidget(input(3), "Q", true));
        assertEquals("INT", FbdCraftPinTypes.typeForWidget(input("2"), "q", true));
        assertNull(FbdCraftPinTypes.typeForWidget(input(1), "IN", false));
        assertFalse(FbdCraftPinTypes.compatible("FLOAT", "INT"));
    }

    @Test public void largeOrFractionalMacroPortTypesStayUnavailable() throws Exception {
        Object[] invalid = new Object[]{4294967297L, 4294967298L,
                4294967299L, 1.5, 2.9, "4294967297", "2.9",
                JSONObject.NULL, new JSONArray().put(1), true, -1, 0, 4};
        for (Object value : invalid) {
            MainActivity.Widget widget = input(value);
            String before = widget.extraFields.toString();
            assertNull("Invalid macro source port was aliased: " + value,
                    FbdCraftPinTypes.typeForWidget(widget, "Q", true));
            assertEquals(before, widget.extraFields.toString());
        }
        assertNull(FbdCraftPinTypes.typeForWidget(input(null), "Q", true));
    }
}
