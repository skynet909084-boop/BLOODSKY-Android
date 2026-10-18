package com.bloodsky.hmi;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

/** Original catalogue appearance; actions use the existing BLOODSKY engine. */
public final class OriginalFbdPalette {
    private OriginalFbdPalette() {}
    public static void show(Activity activity, OriginalComponentPalette.Selection add,
                            Runnable components, Runnable extended) {
        Dialog dialog=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.kc_fragment_component_logic_bar);
        disablePreviews((ViewGroup)dialog.findViewById(android.R.id.content));
        bind(dialog,R.id.kc_logic_tab_el_and,"FBD И","И",add);
        bind(dialog,R.id.kc_logic_tab_el_or,"FBD ИЛИ","ИЛИ",add);
        bind(dialog,R.id.kc_logic_tab_fb_xor,"FBD XOR","XOR",add);
        // These original source previews now map to verified BLOODSKY
        // runtime contracts. Unsupported previews remain disabled.
        bindNative(dialog,R.id.kc_logic_tab_fb_rs,"FB_RS",add);
        bindNative(dialog,R.id.kc_logic_tab_fb_t_trigger,"FB_TRIGGER",add);
        bindNative(dialog,R.id.kc_logic_tab_fb_ctu,"FB_CTU",add);
        bindNative(dialog,R.id.kc_logic_tab_fb_change_i,"FB_CHANGE_I",add);
        bindNative(dialog,R.id.kc_logic_tab_fb_change_f,"FB_CHANGE_F",add);
        bindNative(dialog,R.id.kc_logic_tab_fb_move,"FB_MOVE",add);
        bindNative(dialog,R.id.kc_logic_tab_fb_mx,"FB_MX",add);
        bindNative(dialog,R.id.kc_logic_tab_fb_array,"FB_ARRAY",add);
        bindNative(dialog,R.id.kc_logic_tab_fb_word_to_float,"WordToFloat",add);
        bindNative(dialog,R.id.kc_logic_tab_fb_log,"LOG",add);
        bindNative(dialog,R.id.kc_logic_tab_fb_sel,"FB_SEL",add);
        bindNative(dialog,R.id.kc_logic_tab_el_compare_int,"FB_COMPARE",add);
        bindNative(dialog,R.id.kc_logic_tab_el_arithmetic_int,"FB_ARITHMETIC_INT",add);
        bindNative(dialog,R.id.kc_logic_tab_el_arithmetic_float,"FB_ARITHMETIC_FLOAT",add);
        bindNative(dialog,R.id.kc_logic_tab_el_compare_float,"FB_COMPARE_FLOAT",add);
        bind(dialog,R.id.kc_logic_tab_fb_timer,"FBD Таймер","TON",add);
        bind(dialog,R.id.kc_logic_tab_fb_pow,"FBD POW","POW",add);
        bind(dialog,R.id.kc_logic_tab_fb_sin,"FBD SIN","SIN",add);
        bind(dialog,R.id.kc_logic_tab_fb_abs,"FBD ABS","ABS",add);
        bind(dialog,R.id.kc_logic_tab_fb_int_to_float,"FBD IntToFloat","Int->Float",add);
        bind(dialog,R.id.kc_logic_tab_fb_float_to_int,"FBD FloatToInt","Float->Int",add);
        action(dialog,R.id.kc_logic_switch_layout,"Другие элементы BLOODSKY",extended);
        View back=dialog.findViewById(R.id.kc_logic_close);
        if(back!=null) action(dialog,R.id.kc_logic_close,"Палитра компонентов",components);
        dialog.show();
        Window window=dialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.rgb(35,35,35)));
            window.getDecorView().setPadding(0,0,0,0);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
        }
    }
    /**
     * Exact original FB preview tag -> a tested BLOODSKY FBD terminal contract.
     * Never fall back to similarly named but incompatible extended widgets:
     * native CTU uses +,-,R and numeric Q, not CU/R/PV and Boolean Q.
     */
    public static String nativeTypeForOriginalTag(String tag) {
        if ("FB_RS".equals(tag)) return "FBD RS";
        if ("FB_TRIGGER".equals(tag)) return "FBD CRAFT T";
        if ("FB_CTU".equals(tag)) return "FBD CRAFT CTU";
        if ("FB_CHANGE_I".equals(tag)) return "FBD CRAFT CHANGE I";
        if ("FB_CHANGE_F".equals(tag)) return "FBD CRAFT CHANGE F";
        if ("FB_MOVE".equals(tag)) return "FBD CRAFT MOVE";
        if ("FB_MX".equals(tag)) return "FBD CRAFT MX";
        if ("FB_ARRAY".equals(tag)) return "FBD CRAFT ARRAY";
        if ("WordToFloat".equals(tag)) return "FBD CRAFT WORD TO FLOAT";
        if ("LOG".equals(tag)) return "FBD CRAFT LOG";
        if ("FB_SEL".equals(tag)) return "FBD CRAFT SEL";
        if ("FB_COMPARE".equals(tag)) return "FBD CRAFT COMPARE";
        if ("FB_ARITHMETIC_INT".equals(tag)) return "FBD CRAFT ARITHMETIC INT";
        if ("FB_ARITHMETIC_FLOAT".equals(tag)) return "FBD CRAFT ARITHMETIC FLOAT";
        if ("FB_COMPARE_FLOAT".equals(tag)) return "FBD CRAFT COMPARE FLOAT";
        return null; // Unverified preview remains disabled, no false affordance.
    }

    public static String nativeLabelForOriginalTag(String tag) {
        if ("FB_RS".equals(tag)) return "RS";
        if ("FB_TRIGGER".equals(tag)) return "T";
        if ("FB_CTU".equals(tag)) return "CTU 16 bit";
        if ("FB_CHANGE_I".equals(tag)) return "CH_I";
        if ("FB_CHANGE_F".equals(tag)) return "CH_F";
        if ("FB_MOVE".equals(tag)) return "MOVE";
        if ("FB_MX".equals(tag)) return "MX";
        if ("FB_ARRAY".equals(tag)) return "ARRAY";
        if ("WordToFloat".equals(tag)) return "WORD->FL";
        if ("LOG".equals(tag)) return "LN";
        if ("FB_SEL".equals(tag)) return "SEL";
        if ("FB_COMPARE".equals(tag)) return ">";
        if ("FB_ARITHMETIC_INT".equals(tag) || "FB_ARITHMETIC_FLOAT".equals(tag)) return "+";
        if ("FB_COMPARE_FLOAT".equals(tag)) return ">";
        return null;
    }

    private static void bindNative(Dialog dialog, int id, String sourceTag,
                                   OriginalComponentPalette.Selection add) {
        String type = nativeTypeForOriginalTag(sourceTag);
        String text = nativeLabelForOriginalTag(sourceTag);
        if (type == null || text == null)
            throw new IllegalArgumentException("Unverified original FB preview: " + sourceTag);
        View view = dialog.findViewById(id);
        if (!(view instanceof OriginalFbdDrawing)
                || !sourceTag.equals(String.valueOf(view.getTag())))
            throw new IllegalStateException("Original FB source XML tag mismatch: " + sourceTag);
        bind(dialog, id, type, text, add);
    }

    private static void disablePreviews(ViewGroup group) {
        for(int i=0;i<group.getChildCount();i++) {
            View view=group.getChildAt(i);
            if(view instanceof OriginalFbdDrawing) {
                view.setContentDescription(String.valueOf(view.getTag())+": пока не перенесено");
                view.setEnabled(false); view.setAlpha(0.45f);
                view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            } else if(view instanceof ViewGroup) disablePreviews((ViewGroup)view);
        }
    }
    private static void bind(Dialog dialog,int id,String type,String text,OriginalComponentPalette.Selection add) {
        View view=dialog.findViewById(id); view.setEnabled(true); view.setAlpha(1f);
        action(dialog,id,"Добавить: "+type,()->add.add(type,text));
    }
    private static void action(Dialog dialog,int id,String label,Runnable callback) {
        View view=dialog.findViewById(id); view.setContentDescription(label);
        view.setOnClickListener(v->{dialog.dismiss();callback.run();});
    }
}
