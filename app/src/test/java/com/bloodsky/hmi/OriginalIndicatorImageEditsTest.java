package com.bloodsky.hmi;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class OriginalIndicatorImageEditsTest {
    @Test public void editsBothStatesWithoutChangingRepresentationOrUnknownFields() throws Exception {
        for(int representation=0;representation<3;representation++) {
            JSONObject state=new JSONObject().put("vendor",new JSONObject().put("x",42))
                    .put("pathOrNameOff","blue").put("isImageUserOff",false);
            JSONObject fields=representation==0?state:new JSONObject().put("outer","keep")
                    .put("other",representation==1?state:state.toString());
            String original=fields.toString();
            JSONObject changed=OriginalIndicator.withImage(fields,true,false,"red");
            assertEquals(original,fields.toString());
            assertEquals("blue",OriginalIndicator.imageValue(OriginalIndicator.settings(changed),false));
            changed=OriginalIndicator.withImage(changed,false,true,"content://test/off.png");
            JSONObject edited=OriginalIndicator.settings(changed);
            assertEquals("red",OriginalIndicator.imageValue(edited,true));
            assertFalse(OriginalIndicator.isUserImage(edited,true));
            assertTrue(OriginalIndicator.isUserImage(edited,false));
            assertEquals(42,edited.getJSONObject("vendor").getInt("x"));
            if(representation>0) {
                assertEquals("keep",changed.getString("outer"));
                assertEquals(representation==2,changed.get("other") instanceof String);
            }
        }
    }
    @Test public void invalidSelectionDoesNotMutateInput() throws Exception {
        JSONObject original=new JSONObject().put("vendor",42);
        for(String value:new String[]{"","unknown","lamp_green"}) {
            try {OriginalIndicator.withImage(original,true,false,value);fail();}
            catch(IllegalArgumentException expected) {assertEquals("{\"vendor\":42}",original.toString());}
        }
        try {OriginalIndicator.withImage(original,false,true," ");fail();}
        catch(IllegalArgumentException expected) {assertEquals(42,original.getInt("vendor"));}
    }
    @Test public void codecAndScreenSnapshotsRetainSelectedStates() throws Exception {
        MainActivity.Widget w=new MainActivity.Widget();w.type="Индикатор";w.text="Лампа";
        w.extraFields=OriginalIndicator.withImage(w.extraFields,true,false,"yellow");
        w.extraFields=OriginalIndicator.withImage(w.extraFields,false,false,"red");
        MainActivity.Widget restored=MainActivity.widgetFromJson(MainActivity.widgetToJson(MainActivity.copyWidget(w)));
        assertEquals("yellow",OriginalIndicator.imageValue(OriginalIndicator.settings(restored.extraFields),true));
        assertEquals("red",OriginalIndicator.imageValue(OriginalIndicator.settings(restored.extraFields),false));
    }
    @Test public void samplingBoundsAllocationBeforeDecode() {
        assertEquals(1,ProjectImageSizing.sampleSize(2048,2048));
        assertEquals(4,ProjectImageSizing.sampleSize(4097,4097));
        assertEquals(4,ProjectImageSizing.sampleSize(8000,6000));
        for(int width:new int[]{1,4097,Integer.MAX_VALUE}) {
            int sample=ProjectImageSizing.sampleSize(width,Integer.MAX_VALUE);
            assertTrue(((long)width+sample-1)/sample<=2048);
            assertTrue(((long)Integer.MAX_VALUE+sample-1)/sample<=2048);
        }
        try {ProjectImageSizing.sampleSize(0,1);fail();} catch(IllegalArgumentException expected) {}
    }
}
