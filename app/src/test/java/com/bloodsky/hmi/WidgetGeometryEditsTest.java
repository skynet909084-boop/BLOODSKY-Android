package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class WidgetGeometryEditsTest {
    @Test public void unchangedFractionalFieldsDoNotDrift() {
        float x=40.125f,y=50.875f,w=160.375f,h=58.625f;
        for (int i=0;i<100;i++) {
            WidgetGeometryEdits result=WidgetGeometryEdits.parse(Float.toString(x),Float.toString(y),Float.toString(w),Float.toString(h));
            assertEquals(x,result.x,0f);assertEquals(y,result.y,0f);
            assertEquals(w,result.width,0f);assertEquals(h,result.height,0f);
        }
    }
    @Test public void boundsMatchImportedGeometryAndCommaIsAccepted() {
        WidgetGeometryEdits result=WidgetGeometryEdits.parse(" -100000 ","100000","1,125","99999.875");
        assertEquals(-100000f,result.x,0f);assertEquals(100000f,result.y,0f);
        assertEquals(1.125f,result.width,0f);assertEquals(99999.875f,result.height,0f);
    }
    @Test public void invalidFieldNeverReturnsAPartiallyValidEdit() {
        String[] valid={"40.125","50.875","160.375","58.625"};
        for(int field=0;field<4;field++) for(String bad:new String[]{"", "bad", "NaN", "Infinity", "1e200", "100000.0001", "-100000.0001"}) {
            String[] values=valid.clone();values[field]=bad;
            try {WidgetGeometryEdits.parse(values[0],values[1],values[2],values[3]);fail("Invalid geometry accepted");}
            catch(IllegalArgumentException expected) {assertTrue(expected.getMessage().contains("пикселей"));}
        }
        for(String bad:new String[]{"0","0.999","-1"}) {
            try {WidgetGeometryEdits.parse("0","0",bad,"10");fail("Invalid width accepted");}
            catch(IllegalArgumentException expected) {assertTrue(expected.getMessage().startsWith("Ширина"));}
            try {WidgetGeometryEdits.parse("0","0","10",bad);fail("Invalid height accepted");}
            catch(IllegalArgumentException expected) {assertTrue(expected.getMessage().startsWith("Высота"));}
        }
    }
}
