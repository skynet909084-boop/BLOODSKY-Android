package com.bloodsky.hmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

/** Original lamp dialogs from r.h0.o/k and the pinned dialog_*_image_lamp layouts. */
public final class OriginalIndicatorImages {
    public interface Host {
        Bitmap preview(boolean active);
        boolean select(boolean active, boolean userImage, String value);
        void chooseCustom(boolean active, Runnable changed);
    }
    private OriginalIndicatorImages() {}
    public static void show(Activity activity, Host host) {
        Context context = new ContextThemeWrapper(activity, android.R.style.Theme_Material_Dialog_Alert);
        View states = LayoutInflater.from(context).inflate(R.layout.kc_dialog_menu_image_lamp, null);
        ImageView on = states.findViewById(R.id.kc_lamp_imageOn);
        ImageView off = states.findViewById(R.id.kc_lamp_imageOff);
        Runnable refresh = () -> { on.setImageBitmap(host.preview(true)); off.setImageBitmap(host.preview(false)); };
        on.setContentDescription("Изображение ON"); off.setContentDescription("Изображение OFF");
        on.setOnClickListener(v -> choose(context, host, true, refresh));
        off.setOnClickListener(v -> choose(context, host, false, refresh));
        refresh.run();
        new AlertDialog.Builder(context).setView(states).show();
    }
    private static void choose(Context context, Host host, boolean active, Runnable refresh) {
        View library = LayoutInflater.from(context).inflate(R.layout.kc_dialog_choose_image_lamp, null);
        AlertDialog dialog = new AlertDialog.Builder(context).setView(library).create();
        int[] ids = {R.id.kc_lamp_imageGreen, R.id.kc_lamp_imageYellow, R.id.kc_lamp_imageRed,
                R.id.kc_lamp_imageBlue, R.id.kc_lamp_imageOff};
        String[] values = {"green", "yellow", "red", "blue", "off"};
        String[] names = {"Зелёная лампа", "Жёлтая лампа", "Красная лампа", "Синяя лампа", "Выключенная лампа"};
        for (int i=0;i<ids.length;i++) {
            final String value = values[i];
            View image = library.findViewById(ids[i]); image.setContentDescription(names[i]);
            image.setOnClickListener(v -> { if(host.select(active,false,value)) {refresh.run();dialog.dismiss();} });
        }
        library.findViewById(R.id.kc_lamp_buttonChoosePath).setContentDescription("Выбрать своё изображение");
        library.findViewById(R.id.kc_lamp_buttonChoosePath).setOnClickListener(v ->
                host.chooseCustom(active, () -> {refresh.run();dialog.dismiss();}));
        dialog.show();
    }
}
