package com.bloodsky.hmi;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** Static palette copied from Craft's fragment_component_bar.xml, not runtime widgets. */
public final class OriginalComponentPalette {
    public interface Selection { void add(String type, String text); }
    private OriginalComponentPalette() {}

    public static void show(Activity activity, Selection add,
                            Runnable extended, Runnable background) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.kc_fragment_component_bar);
        // In-scope source entries stay visible; unimplemented semantics stay disabled.
        unavailable(dialog, R.id.kc_picker_tab_switch, "Переключатель");
        unavailable(dialog, R.id.kc_picker_tab_list, "Список");
        unavailable(dialog, R.id.kc_picker_tab_line_setter, "Линейный задатчик");
        unavailable(dialog, R.id.kc_picker_tab_voice_recognition, "Распознавание речи");
        unavailable(dialog, R.id.kc_picker_tab_digital_indicator, "Цифровой индикатор");
        unavailable(dialog, R.id.kc_picker_tab_alarm, "Сигнализатор");
        unavailable(dialog, R.id.kc_picker_tab_gif, "GIF");
        unavailable(dialog, R.id.kc_picker_tab_archive, "Архив");
        unavailable(dialog, R.id.kc_picker_tab_gateway, "Шлюз");
        unavailable(dialog, R.id.kc_picker_tab_gateway_bit, "Битовый шлюз");
        bind(dialog, R.id.kc_picker_tab_sms_in, "Входящее SMS", "SMS: команда", add);
        bind(dialog, R.id.kc_picker_tab_sms_out, "SMS-уведомление", "SMS: {value}", add);
        dialog.findViewById(R.id.kc_picker_tab_sms_in).setVisibility(View.VISIBLE);
        dialog.findViewById(R.id.kc_picker_tab_sms_out).setVisibility(View.VISIBLE);
        unavailable(dialog, R.id.kc_picker_tab_camera, "Камера");
        bind(dialog, R.id.kc_picker_tab_push_notif, "Telegram-уведомление", "Событие", add);
        unavailable(dialog, R.id.kc_picker_tab_web_view, "Веб-страница");
        bind(dialog, R.id.kc_picker_tab_button, "Кнопка", "Кнопка", add);
        bind(dialog, R.id.kc_picker_tab_digital_setters, "Задатчик", "Задатчик: 0", add);
        bind(dialog, R.id.kc_picker_tab_text, "Текст", "Надпись", add);
        bind(dialog, R.id.kc_picker_tab_dyn_text, "Динамический текст", "Динамический текст", add);
        bind(dialog, R.id.kc_picker_tab_lamp, "Индикатор", "Индикатор", add);
        bind(dialog, R.id.kc_picker_tab_line_level, "Прогресс-бар", "Уровень", add);
        bind(dialog, R.id.kc_picker_tab_round_indicator, "Шкала / Стрелка", "Шкала", add);
        bind(dialog, R.id.kc_picker_tab_sound, "Звуковой сигнал", "Аларм", add);
        bind(dialog, R.id.kc_picker_tab_graph, "График / Тренд", "Тренд", add);
        bind(dialog, R.id.kc_picker_tab_dyn_pictures, "Многопозиционное фото", "Фото", add);
        action(dialog, R.id.kc_picker_tab_background, "Фон экрана", background);
        // The page corner opens the original FBD catalogue; its next page retains extended types.
        action(dialog, R.id.kc_picker_switch_layout, "Функциональные блоки и другие элементы", extended);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.rgb(35, 35, 35)));
            window.getDecorView().setPadding(0, 0, 0, 0);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private static void bind(Dialog dialog, int id, String type, String text,
                             Selection add) {
        action(dialog, id, "Добавить: " + type, () -> add.add(type, text));
    }

    private static void action(Dialog dialog, int id, String label, Runnable callback) {
        View view = dialog.findViewById(id);
        view.setContentDescription(label);
        view.setOnClickListener(v -> { dialog.dismiss(); callback.run(); });
    }

    private static void unavailable(Dialog dialog, int id, String label) {
        View view = dialog.findViewById(id);
        view.setContentDescription(label + ": пока не перенесено");
        view.setEnabled(false);
        view.setAlpha(0.45f);
    }
}
