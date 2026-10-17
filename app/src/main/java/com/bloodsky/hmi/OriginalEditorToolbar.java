package com.bloodsky.hmi;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Source toolbar hierarchy/artwork from activity_work_portrait, with explicit native bindings. */
public final class OriginalEditorToolbar {
    public interface Host {
        void hide(); void toggleMode(); void previous(); void next();
        void renameScreen(); void calculator(); void menu(View anchor);
    }
    private OriginalEditorToolbar() {}
    public static View create(ViewGroup parent, String screen, boolean running, Host host) {
        View bar = LayoutInflater.from(parent.getContext()).inflate(R.layout.kc_editor_toolbar, parent, false);
        bind(bar, R.id.kc_toolbar_menu_app_bar_hide, "Скрыть панель", host::hide);
        View mode = bar.findViewById(R.id.kc_toolbar_workToolbar);
        mode.setBackgroundResource(running ? R.drawable.kc_stop : R.drawable.kc_start);
        bind(bar, R.id.kc_toolbar_workToolbar, running ? "Остановить проект" : "Запустить проект", host::toggleMode);
        bind(bar, R.id.kc_toolbar_slidingScreenLeft, "Предыдущий экран", host::previous);
        bind(bar, R.id.kc_toolbar_slidingScreenRight, "Следующий экран", host::next);
        TextView name = bar.findViewById(R.id.kc_toolbar_numScreen);
        name.setText(screen); name.setContentDescription("Текущий экран");
        name.setOnClickListener(running ? null : v -> host.renameScreen());
        name.setClickable(!running);
        bind(bar, R.id.kc_toolbar_calc, "Калькулятор", host::calculator);
        View menu = bar.findViewById(R.id.kc_toolbar_menu_appbar_right);
        menu.setContentDescription("Меню проекта"); menu.setOnClickListener(v -> host.menu(v));
        View info = bar.findViewById(R.id.kc_toolbar_pdf_doc);
        info.setContentDescription("Документ проекта: пока недоступно");
        info.setEnabled(false); info.setAlpha(0.45f);
        return bar;
    }
    private static void bind(View bar, int id, String label, Runnable action) {
        View view = bar.findViewById(id); view.setContentDescription(label);
        view.setOnClickListener(v -> action.run());
    }
}
