package com.bloodsky.hmi;

import org.junit.Test;
import org.json.JSONObject;
import java.util.*;
import static org.junit.Assert.*;

public class FbdEditorModelTest {
    private MainActivity.Widget block(String type) {MainActivity.Widget w=new MainActivity.Widget();w.type=type;w.text=type;w.w=150.375f;w.h=76.125f;return w;}
    private double numeric(List<MainActivity.Widget> widgets,List<MainActivity.FbdConnection> edges,int index) {
        int n=widgets.size();return MainActivity.evaluateFbdBlockNumeric(widgets,edges,index,new boolean[n],new boolean[n],new boolean[n],new double[n]);
    }
    @Test public void invertedBooleanOutputFeedsDownstreamNumericInputs() {
        MainActivity.Widget and=block("FBD И");and.fbdInvertOutput=true;
        MainActivity.Widget sin=block("FBD SIN");
        List<MainActivity.Widget> widgets=Arrays.asList(and,sin);
        List<MainActivity.FbdConnection> edges=Arrays.asList(new MainActivity.FbdConnection(0,1));
        assertEquals(1,numeric(widgets,edges,0),0);assertEquals(Math.sin(1),numeric(widgets,edges,1),0);
        and.fbdInvertOutput=false;assertEquals(0,numeric(widgets,edges,1),0);
    }
    @Test public void normalBooleanOutputsAlsoPropagateOneRatherThanStaleZero() {
        MainActivity.Widget input=block("FBD Вход");input.inputValue=true;
        MainActivity.Widget or=block("FBD ИЛИ");MainActivity.Widget math=block("FBD Сложение");
        assertEquals(1,numeric(Arrays.asList(input,or,math),Arrays.asList(new MainActivity.FbdConnection(0,1),new MainActivity.FbdConnection(1,2)),2),0);
    }
    @Test public void numericOutputIsNeverInvertedByBooleanMetadata() {
        MainActivity.Widget pow=block("FBD POW");pow.compareA=2;pow.fbdInvertOutput=true;
        assertEquals(2,numeric(Arrays.asList(pow),Collections.emptyList(),0),0);
        assertFalse(FbdEditorModel.booleanOutput("FBD POW"));assertTrue(FbdEditorModel.booleanOutput("FBD TOF"));
    }
    @Test public void inversionAndUnitCopyAndRoundTripKeepUnknownFieldsAndGeometry() throws Exception {
        MainActivity.Widget w=MainActivity.widgetFromJson(new JSONObject("{\"type\":\"FBD Таймер\",\"text\":\"TON\",\"x\":12.375,\"y\":13.125,\"w\":150.5,\"h\":76.25,\"other\":\"{\\\"vendor\\\":7}\",\"future\":{\"port\":9}}"));
        w.fbdInvertOutput=true;w.fbdTimeUnit="SEK";w.timerPresetMs=2500;
        MainActivity.Widget copy=MainActivity.copyWidget(w);
        JSONObject saved=MainActivity.widgetToJson(copy);MainActivity.Widget reopened=MainActivity.widgetFromJson(saved);
        assertTrue(reopened.fbdInvertOutput);assertEquals("SEK",reopened.fbdTimeUnit);assertEquals(2500,reopened.timerPresetMs);
        assertEquals(12.375f,reopened.x,0);assertEquals(150.5f,reopened.w,0);
        assertEquals(9,saved.getJSONObject("future").getInt("port"));assertEquals("{\"vendor\":7}",saved.getString("other"));
        reopened.fbdInvertOutput=false;
        assertFalse(MainActivity.widgetFromJson(MainActivity.widgetToJson(reopened)).fbdInvertOutput);
    }
    @Test public void absentEditorMetadataDoesNotAddNewKeysToLegacyWidgets() throws Exception {
        JSONObject saved=MainActivity.widgetToJson(block("FBD И"));
        assertFalse(saved.has("bloodskyFbdInvertOut"));assertFalse(saved.has("bloodskyFbdTimeUnit"));
    }
    @Test public void exactDurationConversionsRejectSubMillisecondOverflowAndInvalidUnits() {
        assertEquals(2345,FbdEditorModel.duration("2,345","SEK"));assertEquals(90000,FbdEditorModel.duration("1.5","MIN"));
        assertEquals(3600000,FbdEditorModel.duration("60","MIN"));assertEquals(1,FbdEditorModel.duration("1","MS"));
        for(String[] pair:new String[][]{{"0","MS"},{"-1","MS"},{"0.0001","SEK"},{"60.1","MIN"},{"Infinity","MS"},{"1e999","MS"},{"1","bad"}}) {
            try{FbdEditorModel.duration(pair[0],pair[1]);fail("Invalid duration accepted");}catch(IllegalArgumentException expected){}
        }
        assertEquals("2.345",FbdEditorModel.durationText(2345,"SEK"));assertEquals("1.5",FbdEditorModel.durationText(90000,"MIN"));
    }
    @Test public void unchangedRepeatingMinuteDisplayKeepsTheExactOriginalMilliseconds() {
        String displayed=FbdEditorModel.durationText(2345,"MIN");
        assertEquals(2345,FbdEditorModel.durationEdit(displayed,"MIN",2345));
        assertEquals(2345,FbdEditorModel.durationEdit(displayed.replace('.',','),"MIN",2345));
        assertEquals(90000,FbdEditorModel.durationEdit("1.5","MIN",2345));
    }
    @Test public void customCaptionsAreNeverConfusedWithAnotherTypesLocalizedDefault() {
        assertEquals("AND",FbdEditorModel.caption("FBD И","И"));
        assertEquals("ИЛИ",FbdEditorModel.caption("FBD И","ИЛИ"));
        assertEquals("И",FbdEditorModel.caption("FBD ИЛИ","И"));
        assertEquals("My timer",FbdEditorModel.caption("FBD Таймер","My timer"));
    }
    @Test public void disconnectOnlyRemovesOutgoingEdgesAndPreservesOtherMetadata() throws Exception {
        MainActivity.FbdConnection input=MainActivity.connectionFromJson(new JSONObject("{\"source\":2,\"target\":0,\"future\":{\"bend\":7}}"));
        MainActivity.FbdConnection output=new MainActivity.FbdConnection(0,1);
        List<MainActivity.FbdConnection> kept=FbdEditorModel.withoutOutput(0,Arrays.asList(input,output,new MainActivity.FbdConnection(1,2)));
        assertEquals(2,kept.size());assertEquals(7,MainActivity.connectionToJson(kept.get(0)).getJSONObject("future").getInt("bend"));
        assertSame(input,kept.get(0));assertEquals(2,input.source);
    }
    @Test public void incomingOrdinalsAndSourcePinCoordinatesStayStableWhenUnrelatedEdgesMove() {
        MainActivity.FbdConnection first=new MainActivity.FbdConnection(0,2),second=new MainActivity.FbdConnection(1,2);
        List<MainActivity.FbdConnection> edges=Arrays.asList(first,new MainActivity.FbdConnection(2,3),second);
        assertEquals(2,FbdEditorModel.incoming(2,edges));assertEquals(1,FbdEditorModel.ordinal(second,edges));
        assertEquals(.25f,FbdEditorModel.inputY("FBD И",0),0);assertEquals(.75f,FbdEditorModel.inputY("FBD И",1),0);
        assertEquals(.5f,FbdEditorModel.inputY("FBD Таймер",0),0);assertEquals("S",FbdEditorModel.inputName("FBD RS",0));
    }
    @Test public void customStylesKeepWiresAttachedToTheirLegacyRendererPins() {
        MainActivity.Widget w=block("FBD И");w.textColor=0xff1f2937;
        assertTrue(FbdEditorModel.originalShape(w));assertEquals(.25f,FbdEditorModel.wireInputY(w,0),0);
        w.backgroundColor=0xff123456;
        assertFalse(FbdEditorModel.originalShape(w));assertEquals(.38f,FbdEditorModel.wireInputY(w,0),0);
        w.backgroundColor=0;w.textSizeSp=20;
        assertEquals(.68f,FbdEditorModel.wireInputY(w,1),0);
    }
    @Test public void failedPublicationRestoresFieldsGeometryAndIndependentUnknownEdgeMetadata() throws Exception {
        List<MainActivity.Widget> widgets=new ArrayList<>(Arrays.asList(block("FBD И")));
        List<MainActivity.FbdConnection> edges=new ArrayList<>(Arrays.asList(MainActivity.connectionFromJson(new JSONObject("{\"source\":0,\"target\":1,\"future\":{\"bend\":7}}"))));
        boolean saved=FbdEditorTransaction.apply(widgets,edges,()->{widgets.get(0).fbdInvertOutput=true;widgets.get(0).w=999;edges.clear();},()->false);
        assertFalse(saved);assertFalse(widgets.get(0).fbdInvertOutput);assertEquals(150.375f,widgets.get(0).w,0);assertEquals(1,edges.size());
        assertEquals(7,MainActivity.connectionToJson(edges.get(0)).getJSONObject("future").getInt("bend"));
    }
    @Test public void failedMutationRestoresAndSuccessfulPublicationKeepsChanges() throws Exception {
        List<MainActivity.Widget> widgets=new ArrayList<>(Arrays.asList(block("FBD Таймер")));List<MainActivity.FbdConnection> edges=new ArrayList<>();
        assertFalse(FbdEditorTransaction.apply(widgets,edges,()->{widgets.get(0).timerPresetMs=99;throw new IllegalStateException();},()->true));
        assertEquals(1000,widgets.get(0).timerPresetMs);
        assertTrue(FbdEditorTransaction.apply(widgets,edges,()->widgets.get(0).timerPresetMs=2500,()->true));assertEquals(2500,widgets.get(0).timerPresetMs);
    }
}
