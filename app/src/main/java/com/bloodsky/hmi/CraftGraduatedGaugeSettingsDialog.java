package com.bloodsky.hmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import java.util.Locale;

/** Compact source-style Craft ID24 graduated needle-gauge settings. */
final class CraftGraduatedGaugeSettingsDialog {
    interface SaveAction {
        boolean save(JSONObject updated, double min, double max);
    }

    private CraftGraduatedGaugeSettingsDialog() {}

    private static EditText row(Activity activity, LinearLayout form,
                                String label, String value, int kind, int dp) {
        LinearLayout line = new LinearLayout(activity);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText(label);
        title.setTextColor(Color.rgb(210, 210, 210));
        title.setTextSize(16);
        line.addView(title, new LinearLayout.LayoutParams(126 * dp, 50 * dp));
        EditText edit = new EditText(activity);
        edit.setSingleLine(true);
        edit.setInputType(kind);
        edit.setText(value);
        edit.setTextSize(17);
        edit.setTextColor(Color.rgb(210, 210, 210));
        edit.setContentDescription(label);
        line.addView(edit, new LinearLayout.LayoutParams(0, 50 * dp, 1f));
        form.addView(line, new LinearLayout.LayoutParams(-1, 52 * dp));
        return edit;
    }

    private static String color(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    static void show(Activity activity, JSONObject fields,
                     double minValue, double maxValue, SaveAction save, Runnable advanced) {
        final int panel = Color.rgb(36, 36, 36), light = Color.rgb(210, 210, 210);
        final int dp = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
        final int integer = InputType.TYPE_CLASS_NUMBER;
        final int signed = integer | InputType.TYPE_NUMBER_FLAG_SIGNED;
        final int hex = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setPadding(18 * dp, 8 * dp, 18 * dp, 12 * dp);
        form.setBackgroundColor(panel);

        TextView title = new TextView(activity);
        title.setText("Настройки");
        title.setTextColor(light);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        form.addView(title, new LinearLayout.LayoutParams(-1, 48 * dp));

        TextView device = new TextView(activity);
        device.setText("Устройство: " + CraftGraduatedGaugeProperties.sourceDeviceLabel(fields));
        device.setTextColor(light);
        device.setTextSize(20);
        device.setGravity(Gravity.CENTER);
        form.addView(device, new LinearLayout.LayoutParams(-1, 46 * dp));

        EditText register = row(activity, form, "Рег:", Integer.toString(
                CraftGraduatedGaugeProperties.sourceRegister(fields)), integer, dp);
        EditText min = row(activity, form, "Минимум:", Integer.toString(
                fields == null ? (int) minValue : fields.optInt("minValue", (int) minValue)), signed, dp);
        EditText max = row(activity, form, "Максимум:", Integer.toString(
                fields == null ? (int) maxValue : fields.optInt("maxValue", (int) maxValue)), signed, dp);
        EditText divisions = row(activity, form, "Деления:", Integer.toString(
                fields == null ? 6 : fields.optInt("craftGaugeScaleCount", 6)), integer, dp);
        EditText size = row(activity, form, "Размер текста:", Integer.toString(
                fields == null ? 20 : fields.optInt("craftGaugeTextSize", 20)), integer, dp);
        EditText low = row(activity, form, "Зона низкая %:", Integer.toString(
                fields == null ? 75 : fields.optInt("craftGaugeLowPercent", 75)), integer, dp);
        EditText medium = row(activity, form, "Зона средняя %:", Integer.toString(
                fields == null ? 90 : fields.optInt("craftGaugeMediumPercent", 90)), integer, dp);
        EditText textValue = row(activity, form, "Цвет значения:", color(
                fields == null ? Color.BLACK : fields.optInt("craftGaugeTextValueColor", Color.BLACK)),
                hex, dp);
        EditText textScale = row(activity, form, "Цвет шкалы:", color(
                fields == null ? Color.BLACK : fields.optInt("craftGaugeTextScaleColor", Color.BLACK)),
                hex, dp);
        EditText center = row(activity, form, "Цвет центра:", color(
                fields == null ? Color.BLACK : fields.optInt("craftGaugeCenterColor", Color.BLACK)),
                hex, dp);
        EditText needle = row(activity, form, "Цвет стрелки:", color(
                fields == null ? Color.RED : fields.optInt("craftGaugeIndicatorColor", Color.RED)),
                hex, dp);
        EditText marks = row(activity, form, "Цвет делений:", color(
                fields == null ? Color.DKGRAY : fields.optInt("craftGaugeMarkColor", Color.DKGRAY)),
                hex, dp);

        TextView pointerLabel = new TextView(activity);
        pointerLabel.setText("Указатель Craft:");
        pointerLabel.setTextColor(light);
        pointerLabel.setTextSize(16);
        form.addView(pointerLabel);
        Spinner pointer = new Spinner(activity);
        ArrayAdapter<String> choices = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,
                CraftGraduatedGaugeProperties.INDICATORS);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pointer.setAdapter(choices);
        String selected = fields == null ? "NormalIndicator"
                : fields.optString("craftGaugeIndicatorName", "NormalIndicator");
        for (int i = 0; i < CraftGraduatedGaugeProperties.INDICATORS.length; i++) {
            if (CraftGraduatedGaugeProperties.INDICATORS[i].equals(selected)) {
                pointer.setSelection(i);
                break;
            }
        }
        form.addView(pointer, new LinearLayout.LayoutParams(-1, 46 * dp));

        CheckBox hideBackground = new CheckBox(activity);
        hideBackground.setText("Скрыть фон шкалы");
        hideBackground.setTextColor(light);
        hideBackground.setChecked(fields != null
                && fields.optBoolean("craftGaugeHideBackground", false));
        form.addView(hideBackground);

        TextView advancedLink = new TextView(activity);
        advancedLink.setText("Дополнительно: привязка Modbus");
        advancedLink.setGravity(Gravity.CENTER);
        advancedLink.setTextColor(Color.rgb(173, 173, 173));
        advancedLink.setTextSize(13);
        form.addView(advancedLink, new LinearLayout.LayoutParams(-1, 44 * dp));

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity,
                android.R.style.Theme_Material_Dialog_Alert)
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null)
                .create();
        advancedLink.setOnClickListener(v -> {
            dialog.dismiss();
            advanced.run();
        });
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int source = Integer.parseInt(register.getText().toString().trim());
                int minValueEdited = Integer.parseInt(min.getText().toString().trim());
                int maxValueEdited = Integer.parseInt(max.getText().toString().trim());
                int divisionsEdited = Integer.parseInt(divisions.getText().toString().trim());
                int sizeEdited = Integer.parseInt(size.getText().toString().trim());
                int lowEdited = Integer.parseInt(low.getText().toString().trim());
                int mediumEdited = Integer.parseInt(medium.getText().toString().trim());
                JSONObject updated = CraftGraduatedGaugeProperties.withSettings(
                        fields, source, minValueEdited, maxValueEdited,
                        divisionsEdited, sizeEdited,
                        Color.parseColor(textValue.getText().toString().trim()),
                        Color.parseColor(textScale.getText().toString().trim()),
                        Color.parseColor(center.getText().toString().trim()),
                        Color.parseColor(needle.getText().toString().trim()),
                        Color.parseColor(marks.getText().toString().trim()),
                        lowEdited, mediumEdited, hideBackground.isChecked(),
                        (String) pointer.getSelectedItem());
                if (save.save(updated, minValueEdited, maxValueEdited)) dialog.dismiss();
            } catch (Exception invalid) {
                String reason = invalid.getMessage();
                Toast.makeText(activity, reason == null ? "Проверь параметры шкалы"
                        : reason, Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(panel));
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f);
            int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.76f);
            window.setLayout(Math.min(620 * dp, width), height);
        }
    }
}
