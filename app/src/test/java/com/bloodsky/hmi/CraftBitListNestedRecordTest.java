package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CraftBitListNestedRecordTest {
    @Test public void invalidNestedLabelsAbortWithoutReplacingSourceOrApproval() throws Exception {
        for (boolean encoded : new boolean[] {false, true}) {
            for (Object raw : new Object[] {JSONObject.NULL, true, 17, new JSONArray().put("keep"), "[]", "null", "{"}) {
                JSONObject other = new JSONObject().put("textBit", raw).put("vendor", "keep");
                JSONObject fields = new JSONObject().put("craftOther", encoded ? other.toString() : other)
                        .put("craftListEnabled", true);
                String before = fields.toString();
                assertThrows(IllegalArgumentException.class, () -> CraftBitList.withSettings(fields,
                        3, CraftBitList.parseEditorLines("Changed"), true, false));
                assertEquals(before, fields.toString());
            }
        }
    }

    @Test public void nestedLabelFormatsPreserveAllSixteenCaptionsAndVendorDataAfterReopen() throws Exception {
        for (boolean outerEncoded : new boolean[] {false, true}) {
            for (boolean innerEncoded : new boolean[] {false, true}) {
                JSONObject labels = new JSONObject().put("vendor", new JSONArray().put("opaque"));
                JSONObject other = new JSONObject().put("textBit", innerEncoded ? labels.toString() : labels);
                JSONObject fields = new JSONObject().put("craftOther", outerEncoded ? other.toString() : other);
                String[] captions = new String[16];
                for (int i = 0; i < captions.length; i++) captions[i] = "Caption " + i;
                JSONObject edited = new JSONObject(CraftBitList.withSettings(fields, 3, captions, true, false).toString());
                assertEquals(outerEncoded, edited.get("craftOther") instanceof String);
                JSONObject out = new JSONObject(edited.get("craftOther").toString());
                assertEquals(innerEncoded, out.get("textBit") instanceof String);
                JSONObject text = new JSONObject(out.get("textBit").toString());
                assertEquals("opaque", text.getJSONArray("vendor").getString(0));
                assertArrayEquals(captions, CraftBitList.labelsFromCraft(out));
                assertArrayEquals(captions, CraftBitList.fromJson(edited.getJSONArray("craftListLabels")));
            }
        }
    }
}
