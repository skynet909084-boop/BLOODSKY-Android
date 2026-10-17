package com.bloodsky.hmi;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Editor operations over the existing ordered, untyped BLOODSKY graph. */
public final class FbdEditorModel {
    private FbdEditorModel() {}
    public static boolean timer(String type) {
        return "FBD Таймер".equals(type)||"FBD TOF".equals(type)||"FBD TP".equals(type);
    }
    public static boolean booleanOutput(String type) {
        if(type==null) return false;
        return timer(type)||type.equals("FBD И")||type.equals("FBD ИЛИ")||type.equals("FBD НЕ")
                ||type.equals("FBD NAND")||type.equals("FBD NOR")||type.equals("FBD XOR")
                ||type.equals("FBD RS")||type.equals("FBD SR")||type.equals("FBD CTU")
                ||type.equals("FBD CTD")||type.equals("FBD R_TRIG")||type.equals("FBD F_TRIG")
                ||type.equals("FBD Сравнение");
    }
    public static String drawing(String type) {
        if(type==null)return null;
        switch(type) {
            case "FBD И":return "FB_AND";
            case "FBD ИЛИ":return "FB_OR";
            case "FBD XOR":return "FB_XOR";
            case "FBD Таймер":case "FBD TOF":case "FBD TP":return "FB_TIMER";
            case "FBD POW":return "FB_POW";
            case "FBD SIN":return "SIN";
            case "FBD ABS":return "ABS";
            case "FBD IntToFloat":return "IntToFloat";
            case "FBD FloatToInt":return "FloatToInt";
            default:return null;
        }
    }
    public static String title(String type) {
        switch(type) {
            case "FBD И":return "AND";case "FBD ИЛИ":return "OR";
            case "FBD Таймер":return "TON";case "FBD TOF":return "TOF";case "FBD TP":return "TP";
            case "FBD IntToFloat":return "INT->FL";case "FBD FloatToInt":return "FL->INT";
            default:return type.substring(4);
        }
    }
    public static boolean originalShape(MainActivity.Widget w) {
        return drawing(w.type)!=null && w.backgroundColor==0 && w.textColor==0xff1f2937
                && w.textSizeSp==14 && w.showFrame && !w.autoResizeText;
    }
    public static float wireInputY(MainActivity.Widget w,int ordinal) {
        return inputY(originalShape(w)?w.type:null,ordinal);
    }
    public static String caption(String type,String text) {
        String standard="FBD И".equals(type)?"И":"FBD ИЛИ".equals(type)?"ИЛИ":
                "FBD IntToFloat".equals(type)?"Int->Float":"FBD FloatToInt".equals(type)?"Float->Int":title(type);
        return text==null||standard.equals(text)?title(type):text;
    }
    public static int durationEdit(String value,String unit,int previousMs) {
        if(!"MS".equals(unit)&&!"SEK".equals(unit)&&!"MIN".equals(unit))throw new IllegalArgumentException("Неизвестная единица времени");
        return value.trim().replace(',','.').equals(durationText(previousMs,unit))?previousMs:duration(value,unit);
    }
    public static int inputCount(String type) {
        if("FBD Вход".equals(type)) return 0;
        if(timer(type)||"FBD НЕ".equals(type)||"FBD R_TRIG".equals(type)||"FBD F_TRIG".equals(type)
                ||"FBD ABS".equals(type)||"FBD SIN".equals(type)||"FBD COS".equals(type)
                ||"FBD TAN".equals(type)||"FBD SQRT".equals(type)||"FBD FloatToInt".equals(type)
                ||"FBD IntToFloat".equals(type)||"FBD MOVE".equals(type)) return 1;
        if("FBD SEL".equals(type))return 3;
        // This existing comparison block uses its A/B constants, not graph inputs.
        if("FBD Сравнение".equals(type))return 0;
        if(booleanOutput(type)||"FBD Сложение".equals(type)||"FBD Вычитание".equals(type)
                ||"FBD Умножение".equals(type)||"FBD Деление".equals(type)
                ||"FBD POW".equals(type)||"FBD MIN_MAX".equals(type))return 2;
        return 0;
    }
    public static int incoming(int target,List<MainActivity.FbdConnection> edges) {
        int count=0;for(MainActivity.FbdConnection edge:edges)if(edge.target==target)count++;return count;
    }
    public static int ordinal(MainActivity.FbdConnection edge,List<MainActivity.FbdConnection> edges) {
        int count=0;
        for(MainActivity.FbdConnection candidate:edges) {
            if(candidate==edge)return count;
            if(candidate.target==edge.target)count++;
        }
        return count;
    }
    public static float inputY(String type,int ordinal) {
        if("FBD И".equals(type)||"FBD ИЛИ".equals(type))return ordinal==0?.25f:ordinal==1?.75f:.5f;
        if("FBD XOR".equals(type)||timer(type)||"FBD POW".equals(type))return ordinal==0?.5f:ordinal==1?5f/6f:.5f;
        if(drawing(type)!=null)return .5f;
        return ordinal==0?.38f:ordinal==1?.68f:.5f;
    }
    public static float inputOffsetY(MainActivity.Widget w,int ordinal) {
        if(!originalShape(w))return w.h*inputY(null,ordinal);
        int height=Math.max(1,Math.round(w.h));
        int y;
        if("FBD И".equals(w.type)||"FBD ИЛИ".equals(w.type))
            y=ordinal==0?height/4:ordinal==1?(height/4)*3:height/2;
        else if("FBD XOR".equals(w.type)||timer(w.type)||"FBD POW".equals(w.type))
            y=ordinal==0?(height/3)/2+height/3:ordinal==1?(height/3)/2+(height/3)*2:height/2;
        else y=height/2;
        return y*(w.h/height);
    }
    public static float outputOffsetY(MainActivity.Widget w) {
        if(!originalShape(w))return w.h/2;
        int height=Math.max(1,Math.round(w.h));
        boolean unary="SIN".equals(drawing(w.type))||"ABS".equals(drawing(w.type))
                ||"IntToFloat".equals(drawing(w.type))||"FloatToInt".equals(drawing(w.type));
        int y=unary?height/2:(height/3)/2+height/3;
        return y*(w.h/height);
    }
    public static String inputName(String type,int index) {
        if(timer(type))return "IN";
        if("FBD POW".equals(type))return index==0?"X":"Y";
        if("FBD RS".equals(type)||"FBD SR".equals(type))return index==0?"S":"R"; // Legacy order retained.
        return "IN"+(index+1);
    }
    public static int duration(String value,String unit) {
        try {
            int factor="MS".equals(unit)?1:"SEK".equals(unit)?1000:"MIN".equals(unit)?60000:0;
            if(factor==0)throw new NumberFormatException();
            int ms=new BigDecimal(value.trim().replace(',','.')).multiply(BigDecimal.valueOf(factor)).intValueExact();
            if(ms<1||ms>3600000)throw new NumberFormatException();
            return ms;
        }catch(RuntimeException invalid){throw new IllegalArgumentException("Время: от 1 мс до 60 мин, с точностью до миллисекунды");}
    }
    public static String durationText(int ms,String unit) {
        int factor="MIN".equals(unit)?60000:"SEK".equals(unit)?1000:1;
        return BigDecimal.valueOf(ms).divide(BigDecimal.valueOf(factor),12,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    public static List<MainActivity.FbdConnection> withoutOutput(int source,List<MainActivity.FbdConnection> edges) {
        List<MainActivity.FbdConnection> kept=new ArrayList<>();
        for(MainActivity.FbdConnection edge:edges)if(edge.source!=source)kept.add(edge);
        return kept;
    }
}
