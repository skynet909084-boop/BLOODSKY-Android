package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class FbdPropertyEditsTest {
    @Test public void presetsKeepExactBoundsRatherThanClamping() {
        assertEquals(Integer.valueOf(1),FbdPropertyEdits.parse("1",null,null,null,null).timer);
        assertEquals(Integer.valueOf(3600000),FbdPropertyEdits.parse("3600000",null,null,null,null).timer);
        assertEquals(Integer.valueOf(1000000),FbdPropertyEdits.parse(null,"1000000",null,null,null).counter);
        for(String bad:new String[]{"","0","-1","1.5","NaN","Infinity","2147483648","text"}) {
            reject(bad,null,null,null,null); reject(null,bad,null,null,null);
        }
        reject("3600001",null,null,null,null); reject(null,"1000001",null,null,null);
    }
    @Test public void comparisonAcceptsOriginalAliasesAndFiniteSignedCommaValues() {
        FbdPropertyEdits edit=FbdPropertyEdits.parse(null,null," -12,75 ","<>","0.125");
        assertEquals(-12.75,edit.left,0); assertEquals(0.125,edit.right,0); assertEquals("!=",edit.operator);
        assertEquals("==",FbdPropertyEdits.parse(null,null,"1","=","1").operator);
        for(String op:new String[]{">","<",">=","<=","==","!="})
            assertEquals(op,FbdPropertyEdits.parse(null,null,"1",op,"2").operator);
        for(String bad:new String[]{"","NaN","Infinity","-Infinity","1e999","x"}) {
            reject(null,null,bad,">","1"); reject(null,null,"1",">",bad);
        }
        for(String op:new String[]{"","=>","=<","equals","bad"}) reject(null,null,"1",op,"2");
    }
    @Test public void unrelatedWidgetsHaveNoFbdEdits() {
        FbdPropertyEdits edit=FbdPropertyEdits.parse(null,null,null,null,null);
        assertNull(edit.timer);assertNull(edit.counter);assertNull(edit.left);assertNull(edit.right);assertNull(edit.operator);
    }
    @Test public void fractionalGeometryAndUnknownMetadataSurviveValidatedApply() throws Exception {
        org.json.JSONObject raw=new org.json.JSONObject("{\"type\":\"FBD Таймер\",\"text\":\"TON\",\"x\":12.375,\"y\":34.125,\"w\":150.5,\"h\":76.25,\"other\":{\"vendor\":42},\"futureFbd\":{\"pin\":7}}");
        MainActivity.Widget widget=MainActivity.widgetFromJson(raw);
        FbdPropertyEdits edit=FbdPropertyEdits.parse("2345",null,null,null,null);
        widget.timerPresetMs=edit.timer;
        org.json.JSONObject result=MainActivity.widgetToJson(widget);
        assertEquals(2345,result.getInt("timerPresetMs"));
        for(String field:new String[]{"x","y","w","h"}) assertEquals(raw.getDouble(field),result.getDouble(field),0);
        assertEquals(42,result.getJSONObject("other").getInt("vendor"));
        assertEquals(7,result.getJSONObject("futureFbd").getInt("pin"));
    }
    private void reject(String timer,String counter,String left,String op,String right) {
        try {FbdPropertyEdits.parse(timer,counter,left,op,right);fail("Invalid property accepted");}
        catch(IllegalArgumentException expected) {assertFalse(expected.getMessage().isEmpty());}
    }
}
