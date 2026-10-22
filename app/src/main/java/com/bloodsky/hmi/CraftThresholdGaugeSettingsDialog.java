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

/** Craft ID12 threshold gauge's own dark appearance and register settings. */
final class CraftThresholdGaugeSettingsDialog {
    interface SaveAction {
        boolean save(JSONObject fields, double maximum);
    }

    private CraftThresholdGaugeSettingsDialog() {}

    private static EditText field(Activity activity, String value, int type, String description) {
        EditText edit = new EditText(activity);
        edit.setInputType(type);
        edit.setSingleLine(true);
        edit.setText(value);
        edit.setTextSize(17);
        edit.setTextColor(Color.rgb(210, 210, 210));
        edit.setContentDescription(description);
        return edit;
    }

    private static EditText row(Activity activity, LinearLayout form, String caption,
                                String value, int inputType, int dp) {
        LinearLayout line = new LinearLayout(activity);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText(caption);
        title.setTextColor(Color.rgb(210, 210, 210));
        title.setTextSize(16);
        line.addView(title, new LinearLayout.LayoutParams(128 * dp, 50 * dp));
        EditText edit = field(activity, value, inputType, caption);
        line.addView(edit, new LinearLayout.LayoutParams(0, 50 * dp, 1f));
        form.addView(line, new LinearLayout.LayoutParams(-1, 52 * dp));
        return edit;
    }

    private static String color(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    static void show(Activity activity, JSONObject fields, double widgetMax,
                     SaveAction save, Runnable advanced) {
        final int dark = Color.rgb(36, 36, 36), light = Color.rgb(210, 210, 210);
        final int dp = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
        final int whole = InputType.TYPE_CLASS_NUMBER;
        final int colorInput = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setPadding(20 * dp, 8 * dp, 20 * dp, 12 * dp);
        form.setBackgroundColor(dark);

        TextView heading = new TextView(activity);
        heading.setText("Настройки");
        heading.setTextSize(26);
        heading.setTextColor(light);
        heading.setGravity(Gravity.CENTER);
        form.addView(heading, new LinearLayout.LayoutParams(-1, 48 * dp));
        TextView device = new TextView(activity);
        device.setText("Устройство: " + CraftThresholdGaugeProperties.sourceDeviceLabel(fields));
        device.setTextSize(20);
        device.setTextColor(light);
        device.setGravity(Gravity.CENTER);
        form.addView(device, new LinearLayout.LayoutParams(-1, 48 * dp));

        EditText source = row(activity, form, "Рег:",
                Integer.toString(CraftThresholdGaugeProperties.sourceRegister(fields)), whole, dp);
        int currentMax = fields == null ? (int) widgetMax
                : fields.optInt("maxValue", (int) widgetMax);
        EditText maximum = row(activity, form, "Максимум:",
                Integer.toString(Math.max(1, currentMax)), whole, dp);

        CheckBox showText = new CheckBox(activity);
        showText.setText("Показывать подписи делений");
        showText.setTextColor(light);
        showText.setChecked(fields == null || fields.optBoolean("craftGaugeShowText", true));
        form.addView(showText);

        EditText size = row(activity, form, "Размер:",
                Integer.toString(fields == null ? 12 : fields.optInt("craftGaugeTextSize", 12)),
                whole, dp);
        EditText textColor = row(activity, form, "Цвет подписей:",
                color(fields == null ? Color.BLACK : fields.optInt("craftGaugeTextColor", Color.BLACK)),
                colorInput, dp);
        EditText inside = row(activity, form, "Внутренняя:",
                color(fields == null ? 0xff888888 : fields.optInt("craftGaugeInColor", 0xff888888)),
                colorInput, dp);
        EditText outside = row(activity, form, "Внешняя:",
                color(fields == null ? 0xff277454 : fields.optInt("craftGaugeOutColor", 0xff277454)),
                colorInput, dp);
        EditText center = row(activity, form, "Центр:",
                color(fields == null ? Color.BLACK : fields.optInt("craftGaugeCenterColor", Color.BLACK)),
                colorInput, dp);
        EditText first = row(activity, form, "Первая зона:",
                Integer.toString(fields == null ? 0 : fields.optInt("craftGaugeFirstColorPoint", 0)),
                whole, dp);
        EditText second = row(activity, form, "Вторая зона:",
                Integer.toString(fields == null ? 0 : fields.optInt("craftGaugeSecondColorPoint", 0)),
                whole, dp);

        TextView advancedLink = new TextView(activity);
        advancedLink.setText("Дополнительно: привязка Modbus");
        advancedLink.setTextSize(13);
        advancedLink.setTextColor(Color.rgb(173, 173, 173));
        advancedLink.setGravity(Gravity.CENTER);
        advancedLink.setContentDescription("Расширенные настройки пороговой шкалы Craft");
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
                int address = Integer.parseInt(source.getText().toString().trim());
                int max = Integer.parseInt(maximum.getText().toString().trim());
                int textSize = Integer.parseInt(size.getText().toString().trim());
                int firstPoint = Integer.parseInt(first.getText().toString().trim());
                int secondPoint = Integer.parseInt(second.getText().toString().trim());
                JSONObject edited = CraftThresholdGaugeProperties.withSettings(
                        fields, address, max, showText.isChecked(), textSize,
                        Color.parseColor(textColor.getText().toString().trim()),
                        Color.parseColor(inside.getText().toString().trim()),
                        Color.parseColor(outside.getText().toString().trim()),
                        Color.parseColor(center.getText().toString().trim()),
                        firstPoint, secondPoint);
                if (save.save(edited, max)) dialog.dismiss();
            } catch (Exception invalid) {
                String message = invalid.getMessage();
                Toast.makeText(activity, message == null ? "Проверь параметры шкалы"
                        : message, Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(dark));
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f);
            int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.76f);
            window.setLayout(Math.min(620 * dp, width), height);
        }
    }
}
