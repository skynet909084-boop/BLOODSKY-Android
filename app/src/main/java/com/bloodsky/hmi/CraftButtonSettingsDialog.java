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

/** Craft ID6 button-specific appearance and metadata; runtime writes require advanced opt-in. */
final class CraftButtonSettingsDialog {
    interface SaveAction { boolean save(JSONObject updated, String caption); }
    private CraftButtonSettingsDialog() {}

    private static EditText field(Activity activity, String value, int inputType, String name) {
        EditText input = new EditText(activity);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setText(value);
        input.setTextColor(Color.rgb(210, 210, 210));
        input.setTextSize(20);
        input.setContentDescription(name);
        return input;
    }

    static void show(Activity activity, JSONObject fields, String caption,
                     boolean constantWidget, SaveAction save, Runnable advanced) {
        final int panel = Color.rgb(36, 36, 36), label = Color.rgb(210, 210, 210);
        final int dp = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setPadding(dp * 22, dp * 8, dp * 22, dp * 12);
        form.setBackgroundColor(panel);

        TextView title = new TextView(activity);
        title.setText("Настройки");
        title.setTextSize(26);
        title.setTextColor(label);
        title.setGravity(Gravity.CENTER);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp * 50));

        TextView device = new TextView(activity);
        device.setText("Устройство: " + CraftButtonCompactSettings.sourceDeviceLabel(fields));
        device.setTextSize(21);
        device.setTextColor(label);
        device.setGravity(Gravity.CENTER);
        form.addView(device, new LinearLayout.LayoutParams(-1, dp * 50));

        int mode = CraftButtonCompactSettings.mode(fields, constantWidget);
        TextView modeText = new TextView(activity);
        modeText.setText("Режим: " + (mode == 1 ? "Переход по экрану"
                : mode == 2 ? "Запись константы" : "Моментальная команда"));
        modeText.setTextSize(15);
        modeText.setTextColor(label);
        modeText.setGravity(Gravity.CENTER);
        form.addView(modeText, new LinearLayout.LayoutParams(-1, dp * 42));

        EditText captionField = field(activity, caption == null ? "" : caption,
                InputType.TYPE_CLASS_TEXT, "Подпись кнопки Craft");
        captionField.setHint("Текст кнопки");
        form.addView(captionField, new LinearLayout.LayoutParams(-1, dp * 58));

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER);
        TextView registerLabel = new TextView(activity);
        registerLabel.setText("Рег:");
        registerLabel.setTextColor(label);
        registerLabel.setTextSize(20);
        row.addView(registerLabel);
        EditText registerField = field(activity, Integer.toString(
                CraftButtonCompactSettings.sourceRegister(fields)), InputType.TYPE_CLASS_NUMBER,
                "Регистр кнопки Craft");
        registerField.setGravity(Gravity.CENTER);
        row.addView(registerField, new LinearLayout.LayoutParams(dp * 78, dp * 52));

        TextView bitLabel = new TextView(activity);
        bitLabel.setText("  Бит:");
        bitLabel.setTextColor(label);
        bitLabel.setTextSize(20);
        row.addView(bitLabel);
        EditText bitField = field(activity, Integer.toString(CraftButtonCompactSettings.bit(fields)),
                InputType.TYPE_CLASS_NUMBER, "Бит кнопки Craft");
        bitField.setGravity(Gravity.CENTER);
        row.addView(bitField, new LinearLayout.LayoutParams(dp * 60, dp * 52));
        form.addView(row, new LinearLayout.LayoutParams(-1, dp * 60));

        CheckBox visible = new CheckBox(activity);
        visible.setText("Показывать в рабочем режиме");
        visible.setTextColor(label);
        visible.setChecked(fields == null || fields.optBoolean("craftButtonVisibleInWork", true));
        form.addView(visible, new LinearLayout.LayoutParams(-1, dp * 48));

        TextView advancedLink = new TextView(activity);
        advancedLink.setText("Дополнительно: режим, константа и привязка Modbus");
        advancedLink.setTextColor(Color.rgb(173, 173, 173));
        advancedLink.setGravity(Gravity.CENTER);
        advancedLink.setTextSize(13);
        advancedLink.setContentDescription("Расширенные настройки кнопки Craft");
        form.addView(advancedLink, new LinearLayout.LayoutParams(-1, dp * 54));

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
                        int register = Integer.parseInt(registerField.getText().toString().trim());
                        int bit = Integer.parseInt(bitField.getText().toString().trim());
                        String text = captionField.getText().toString();
                        JSONObject updated = CraftButtonCompactSettings.withSettings(
                                fields, register, bit, text, visible.isChecked());
                        if (save.save(updated, text)) dialog.dismiss();
                    } catch (Exception invalid) {
                        String message = invalid instanceof NumberFormatException
                                ? "Укажи целые регистр (0–65535) и бит (0–15)"
                                : invalid.getMessage();
                        Toast.makeText(activity, message == null ? "Проверь настройки кнопки"
                                : message, Toast.LENGTH_LONG).show();
                    }
                }));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(panel));
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f);
            window.setLayout(Math.min(dp * 620, width), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}
