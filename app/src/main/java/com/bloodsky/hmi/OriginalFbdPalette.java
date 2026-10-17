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
