package com.bloodsky.hmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/** Source menu rows and order; existing editor commands stay accessible as dialog actions. */
public final class OriginalFbdMenus {
    public interface Host {
        void connect();void invert();void disconnect();void mode();void time();void properties();void more();
    }
    private OriginalFbdMenus() {}
    public static void show(Activity activity,String title,boolean timer,boolean invertible,boolean inverted,Host host) {
        int layout=timer?R.layout.kc_popup_logic_fb_timer:invertible?R.layout.kc_popup_logic_fb_and_or:R.layout.kc_popup_logic_fb_pow;
        View root=LayoutInflater.from(activity).inflate(layout,null);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title).setView(root)
                .setPositiveButton("Свойства",(d,w)->host.properties())
                .setNeutralButton("Другие действия",(d,w)->host.more()).setNegativeButton("Отмена",null).create();
        bind(root,R.id.kc_fbd_logic_connect,"Соединить с …",dialog,host::connect);
        bind(root,R.id.kc_fbd_logic_disconnect_out,"Отсоединить выход",dialog,host::disconnect);
        if(invertible) {
            bind(root,R.id.kc_fbd_logic_invert,inverted?"Инвертировать: включено":"Инвертировать: выключено",dialog,host::invert);
            root.findViewById(R.id.kc_fbd_logic_invert).setSelected(inverted);
        }
        if(timer) {
            bind(root,R.id.kc_fbd_logic_edit,"Редактировать тип",dialog,host::mode);
            bind(root,R.id.kc_fbd_logic_edit_time,"Редактировать тип времени",dialog,host::time);
        }
        dialog.show();
    }
    public interface Choice { void choose(String value); }
    public static void modes(Activity activity,String selected,Choice choice) {
        View root=LayoutInflater.from(activity).inflate(R.layout.kc_popup_logic_fb_timer_edit_type,null);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Тип таймера: "+selected).setView(root).setNegativeButton("Отмена",null).create();
        bind(root,R.id.kc_fbd_timerActionTON,"TON",dialog,()->choice.choose("FBD Таймер"));
        bind(root,R.id.kc_fbd_timerActionTOF,"TOF",dialog,()->choice.choose("FBD TOF"));
        bind(root,R.id.kc_fbd_timerActionTP,"TP",dialog,()->choice.choose("FBD TP"));
        View blink=root.findViewById(R.id.kc_fbd_timerActionBLINK);
        blink.setEnabled(false);blink.setAlpha(.45f);blink.setContentDescription("BLINK: пока не перенесено");
        disableChildren(blink);dialog.show();
    }
    private static void disableChildren(View view) {
        view.setEnabled(false);
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++)disableChildren(((ViewGroup)view).getChildAt(i));
    }
    public static void units(Activity activity,Choice choice) {
        View root=LayoutInflater.from(activity).inflate(R.layout.kc_popup_logic_fb_timer_edit_type_time,null);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Единицы времени").setView(root).setNegativeButton("Отмена",null).create();
        bind(root,R.id.kc_fbd_timerMs,"мс",dialog,()->choice.choose("MS"));
        bind(root,R.id.kc_fbd_timerSek,"сек",dialog,()->choice.choose("SEK"));
        bind(root,R.id.kc_fbd_timerMin,"мин",dialog,()->choice.choose("MIN"));
        dialog.show();
    }
    private static void bind(View root,int id,String label,AlertDialog dialog,Runnable action) {
        View row=root.findViewById(id);row.setContentDescription(label);
        row.setOnClickListener(v->{dialog.dismiss();action.run();});
    }
}
