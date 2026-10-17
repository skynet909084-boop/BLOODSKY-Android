package com.bloodsky.hmi;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/** Original mbCanvas layoutSelectAction, with explicit bindings replacing XML reflection. */
public final class OriginalSelectionActions {
    private OriginalSelectionActions() {}
    public static View create(ViewGroup parent, Runnable cut, Runnable copy, Runnable paste, Runnable align) {
        View bar = LayoutInflater.from(parent.getContext()).inflate(R.layout.kc_selection_actions, parent, false);
        bind(bar, R.id.kc_action_selectActionCut, "Вырезать", cut);
        bind(bar, R.id.kc_action_selectActionCopy, "Копировать", copy);
        bind(bar, R.id.kc_action_selectActionPaste, "Вставить", paste);
        bind(bar, R.id.kc_action_openDialogAlignComponents, "Выравнивание", align);
        return bar;
    }
    private static void bind(View bar, int id, String label, Runnable action) {
        View button = bar.findViewById(id);
        button.setContentDescription(label);
        button.setOnClickListener(view -> action.run());
    }
}
