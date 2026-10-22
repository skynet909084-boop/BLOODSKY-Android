package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class FbdTypedOperatorEditsTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> types() {
        return Arrays.asList(new Object[][] {
                {"FBD CRAFT COMPARE", 107, false},
                {"FBD CRAFT COMPARE FLOAT", 112, false},
                {"FBD CRAFT ARITHMETIC INT", 110, true},
                {"FBD CRAFT ARITHMETIC FLOAT", 111, true},
                {"FBD Сравнение", -1, false}});
    }
    private final String type;
    private final int componentId;
    private final boolean arithmetic;
    public FbdTypedOperatorEditsTest(String type, int componentId, boolean arithmetic) {
        this.type = type; this.componentId = componentId; this.arithmetic = arithmetic;
    }
    private FbdPropertyEdits parse(String operator) {
        return FbdPropertyEdits.parse(type, null, null, " -12,5 ", operator, "7");
    }

    @Test public void acceptsOnlySourceOperatorFamily() {
        String[] valid = arithmetic ? new String[] {"+", "-", "*", "/"}
                : new String[] {">", "<", ">=", "<=", "==", "!="};
        for (String operator : valid) {
            FbdPropertyEdits edit = parse(" " + operator + " ");
            assertEquals(operator, edit.operator);
            assertEquals(-12.5, edit.left, 0);
            assertEquals(7, edit.right, 0);
        }
        String[] wrong = arithmetic ? new String[] {">", "<", ">=", "<=", "==", "!=", "=", "<>"}
                : new String[] {"+", "-", "*", "/"};
        for (String operator : wrong)
            assertThrows(operator, IllegalArgumentException.class, () -> parse(operator));
    }

    @Test public void validEditsKeepOperatorThroughSaveReopenAndCraftActionMapping() throws Exception {
        String[] valid = arithmetic ? new String[] {"+", "-", "*", "/"}
                : new String[] {">", "<", "<=", ">=", "=", "<>"};
        for (int i = 0; i < valid.length; i++) {
            MainActivity.Widget widget = MainActivity.widgetFromJson(new JSONObject()
                    .put("type", type).put("vendor", new JSONObject().put("opaque", true)));
            FbdPropertyEdits edit = parse(valid[i]);
            widget.compareOperator = edit.operator;
            MainActivity.Widget reloaded = MainActivity.widgetFromJson(MainActivity.widgetToJson(widget));
            assertEquals(edit.operator, reloaded.compareOperator);
            assertTrue(reloaded.extraFields.getJSONObject("vendor").getBoolean("opaque"));
            if (componentId > 0)
                assertEquals(i + 1, CraftProjectExporter.craftOperatorAction(componentId, edit.operator));
        }
    }

    @Test public void wrongFamilyIsRejectedBeforeChangingSavedWidget() throws Exception {
        MainActivity.Widget widget = MainActivity.widgetFromJson(new JSONObject()
                .put("type", type).put("compareOperator", arithmetic ? "*" : "<=")
                .put("vendor", "retain"));
        String before = MainActivity.widgetToJson(widget).toString();
        try {
            FbdPropertyEdits edit = parse(arithmetic ? ">" : "+");
            widget.compareOperator = edit.operator;
            fail("wrong operator family accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals(before, MainActivity.widgetToJson(widget).toString());
        }
    }
}
