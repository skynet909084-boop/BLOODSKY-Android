package com.bloodsky.hmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/** Source-style ID20 list editor; no automatic device mapping or network writes. */
final class CraftListSettingsDialog {
    interface SaveAction {
        /** Return false when atomic project persistence failed. */
        boolean save(JSONObject updated);
    }

    private CraftListSettingsDialog() {}

    static void show(Activity activity, JSONObject fields, SaveAction onSave, Runnable advanced) {
        final int background = Color.rgb(36, 36, 36);
        final int foreground = Color.rgb(210, 210, 210);
        int px = Math.round(activity.getResources().getDisplayMetrics().density);
        int space = Math.max(1, px);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setBackgroundColor(background);
        form.setPadding(22 * space, 8 * space, 22 * space, 12 * space);

        TextView title = new TextView(activity);
        title.setText("Настройки");
        title.setTextSize(26);
        title.setTextColor(foreground);
        title.setGravity(Gravity.CENTER);
        form.addView(title, new LinearLayout.LayoutParams(-1, 48 * space));

        TextView device = new TextView(activity);
        device.setText("Устройство: " + CraftBitList.sourceDeviceLabel(fields));
        device.setTextSize(21);
        device.setTextColor(foreground);
        device.setGravity(Gravity.CENTER);
        form.addView(device, new LinearLayout.LayoutParams(-1, 50 * space));

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER);
        TextView label = new TextView(activity);
        label.setText("Рег:");
        label.setTextSize(20);
        label.setTextColor(foreground);
        row.addView(label);
        EditText register = new EditText(activity);
        register.setInputType(InputType.TYPE_CLASS_NUMBER);
        register.setSingleLine(true);
        register.setText(Integer.toString(CraftBitList.sourceRegister(fields)));
        register.setTextSize(21);
        register.setTextColor(foreground);
        register.setGravity(Gravity.CENTER);
        register.setContentDescription("Регистр списка Craft");
        row.addView(register, new LinearLayout.LayoutParams(95 * space, 52 * space));
        form.addView(row, new LinearLayout.LayoutParams(-1, 58 * space));

        TextView labelTitle = new TextView(activity);
        labelTitle.setText("Подписи битов 0–15");
        labelTitle.setTextSize(17);
        labelTitle.setTextColor(foreground);
        form.addView(labelTitle);

        EditText labels = new EditText(activity);
        labels.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        labels.setSingleLine(false);
        labels.setMinLines(3);
        labels.setMaxLines(6);
        labels.setGravity(Gravity.TOP);
        labels.setTextSize(15);
        labels.setTextColor(foreground);
        labels.setHint("16 строк — по одному пункту на бит");
        labels.setContentDescription("Подписи шестнадцати битов Craft");
        labels.setText(CraftBitList.toEditorLines(
                CraftBitList.fromJson(fields == null ? null : fields.optJSONArray("craftListLabels"))));
        form.addView(labels, new LinearLayout.LayoutParams(-1, 145 * space));

        CheckBox visible = new CheckBox(activity);
        visible.setText("Показывать в рабочем режиме");
        visible.setTextColor(foreground);
        visible.setChecked(fields == null || fields.optBoolean("craftListVisibleInWork", true));
        form.addView(visible, new LinearLayout.LayoutParams(-1, 44 * space));

        CheckBox readOnly = new CheckBox(activity);
        readOnly.setText("Не изменять биты в рабочем режиме");
        readOnly.setTextColor(foreground);
        readOnly.setChecked(fields != null && fields.optBoolean("craftListCheckNoChange", false));
        form.addView(readOnly, new LinearLayout.LayoutParams(-1, 44 * space));

        TextView advancedLink = new TextView(activity);
        advancedLink.setText("Дополнительно: привязка Modbus");
        advancedLink.setTextColor(Color.rgb(173, 173, 173));
        advancedLink.setTextSize(13);
        advancedLink.setGravity(Gravity.CENTER);
        advancedLink.setContentDescription("Расширенные настройки списка и привязка Modbus");
        form.addView(advancedLink, new LinearLayout.LayoutParams(-1, 42 * space));

        AlertDialog dialog = new AlertDialog.Builder(activity,
                android.R.style.Theme_Material_Dialog_Alert)
                .setView(form)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null)
                .create();
        advancedLink.setOnClickListener(v -> {
            dialog.dismiss();
            advanced.run();
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        int address = Integer.parseInt(register.getText().toString().trim());
                        String[] chosenLabels = CraftBitList.parseEditorLines(labels.getText().toString());
                        JSONObject updated = CraftBitList.withSettings(
                                fields, address, chosenLabels, visible.isChecked(), readOnly.isChecked());
                        if (onSave.save(updated)) dialog.dismiss();
                    } catch (Exception invalid) {
                        String reason = invalid instanceof NumberFormatException
                                ? "Укажи целый регистр от 0 до 65535" : invalid.getMessage();
                        Toast.makeText(activity, reason == null ? "Проверь настройки списка" : reason,
                                Toast.LENGTH_LONG).show();
                    }
                }));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(background));
            int maxWidth = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f);
            window.setLayout(Math.min(620 * space, maxWidth),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}
