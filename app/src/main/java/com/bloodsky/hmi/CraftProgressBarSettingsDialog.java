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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Locale;

/** Compact Craft ID11 appearance/range dialog with guarded project persistence. */
final class CraftProgressBarSettingsDialog {
    interface SaveAction {
        boolean save(JSONObject updated, double minimum, double maximum);
    }

    private CraftProgressBarSettingsDialog() {}

    private static EditText input(Activity activity, String value, int type, String description) {
        EditText edit = new EditText(activity);
        edit.setInputType(type);
        edit.setText(value);
        edit.setSingleLine(true);
        edit.setTextColor(Color.rgb(210, 210, 210));
        edit.setTextSize(17);
        edit.setContentDescription(description);
        return edit;
    }

    private static void addRow(Activity activity, LinearLayout form, String label,
                               EditText field, int px) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText(label);
        title.setTextSize(17);
        title.setTextColor(Color.rgb(210, 210, 210));
        row.addView(title, new LinearLayout.LayoutParams(105 * px, 52 * px));
        row.addView(field, new LinearLayout.LayoutParams(0, 52 * px, 1f));
        form.addView(row, new LinearLayout.LayoutParams(-1, 54 * px));
    }

    static void show(Activity activity, JSONObject fields, double minimum,
                     double maximum, SaveAction onSave, Runnable onAdvanced) {
        final int background = Color.rgb(36, 36, 36);
        final int foreground = Color.rgb(210, 210, 210);
        final int px = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setPadding(20 * px, 8 * px, 20 * px, 12 * px);
        form.setBackgroundColor(background);

        TextView heading = new TextView(activity);
        heading.setText("Настройки");
        heading.setTextSize(26);
        heading.setTextColor(foreground);
        heading.setGravity(Gravity.CENTER);
        form.addView(heading, new LinearLayout.LayoutParams(-1, 48 * px));

        TextView device = new TextView(activity);
        device.setText("Устройство: " + CraftProgressBarProperties.sourceDeviceLabel(fields));
        device.setTextColor(foreground);
        device.setTextSize(20);
        device.setGravity(Gravity.CENTER);
        form.addView(device, new LinearLayout.LayoutParams(-1, 44 * px));

        int numbers = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED;
        EditText register = input(activity, Integer.toString(
                CraftProgressBarProperties.sourceRegister(fields)),
                InputType.TYPE_CLASS_NUMBER, "Регистр шкалы Craft");
        addRow(activity, form, "Рег:", register, px);

        EditText minField = input(activity, Double.toString(minimum), numbers,
                "Минимальное значение шкалы");
        addRow(activity, form, "Мин:", minField, px);

        EditText maxField = input(activity, Double.toString(maximum), numbers,
                "Максимальное значение шкалы");
        addRow(activity, form, "Макс:", maxField, px);

        CheckBox showText = new CheckBox(activity);
        showText.setText("Показывать значение");
        showText.setTextColor(foreground);
        showText.setChecked(fields == null || fields.optBoolean("craftProgressShowText", true));
        form.addView(showText);

        CheckBox showScale = new CheckBox(activity);
        showScale.setText("Показывать деления шкалы");
        showScale.setTextColor(foreground);
        showScale.setChecked(fields != null && fields.optBoolean("craftProgressShowScale", false));
        form.addView(showScale);

        EditText textSize = input(activity, Integer.toString(fields == null ? 50
                        : fields.optInt("craftProgressTextSize", 50)),
                InputType.TYPE_CLASS_NUMBER, "Размер текста шкалы");
        addRow(activity, form, "Размер:", textSize, px);

        EditText fillColor = input(activity, String.format(Locale.ROOT, "#%08X", fields == null
                        ? 0xff2eec00 : fields.optInt("craftProgressColor", 0xff2eec00)),
                InputType.TYPE_CLASS_TEXT, "Цвет заполнения шкалы");
        addRow(activity, form, "Заполнение:", fillColor, px);

        EditText trackColor = input(activity, String.format(Locale.ROOT, "#%08X", fields == null
                        ? 0xff9c9c9b : fields.optInt("craftProgressBackgroundColor", 0xff9c9c9b)),
                InputType.TYPE_CLASS_TEXT, "Цвет дорожки шкалы");
        addRow(activity, form, "Дорожка:", trackColor, px);

        EditText textColor = input(activity, String.format(Locale.ROOT, "#%08X", fields == null
                        ? 0xff0000ff : fields.optInt("craftProgressTextColor", 0xff0000ff)),
                InputType.TYPE_CLASS_TEXT, "Цвет значения шкалы");
        addRow(activity, form, "Текст:", textColor, px);

        TextView advanced = new TextView(activity);
        advanced.setText("Дополнительно: привязка Modbus");
        advanced.setTextColor(Color.rgb(173, 173, 173));
        advanced.setTextSize(13);
        advanced.setGravity(Gravity.CENTER);
        advanced.setContentDescription("Расширенные свойства прогресс-бара Craft");
        form.addView(advanced, new LinearLayout.LayoutParams(-1, 42 * px));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity,
                android.R.style.Theme_Material_Dialog_Alert)
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null)
                .create();
        advanced.setOnClickListener(v -> {
            dialog.dismiss();
            onAdvanced.run();
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        int source = Integer.parseInt(register.getText().toString().trim());
                        double min = Double.parseDouble(minField.getText().toString().trim());
                        double max = Double.parseDouble(maxField.getText().toString().trim());
                        int size = Integer.parseInt(textSize.getText().toString().trim());
                        int fill = Color.parseColor(fillColor.getText().toString().trim());
                        int track = Color.parseColor(trackColor.getText().toString().trim());
                        int text = Color.parseColor(textColor.getText().toString().trim());
                        JSONObject edited = CraftProgressBarProperties.withSettings(
                                fields, source, min, max, showText.isChecked(), size,
                                text, showScale.isChecked(), fill, track);
                        if (onSave.save(edited, min, max)) dialog.dismiss();
                    } catch (Exception invalid) {
                        String message = invalid.getMessage();
                        Toast.makeText(activity, message == null ? "Проверь параметры шкалы"
                                : message, Toast.LENGTH_LONG).show();
                    }
                }));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(background));
            int maxWidth = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f);
            window.setLayout(Math.min(620 * px, maxWidth),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}
