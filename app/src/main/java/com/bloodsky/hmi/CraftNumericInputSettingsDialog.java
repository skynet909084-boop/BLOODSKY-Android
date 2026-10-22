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

/** Craft ID8's dedicated compact settings. Password and network write consent stay advanced. */
final class CraftNumericInputSettingsDialog {
    interface SaveAction { boolean save(JSONObject updated); }

    private CraftNumericInputSettingsDialog() {}

    private static EditText input(Activity activity, String value, int kind, String description) {
        EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setInputType(kind);
        field.setText(value);
        field.setTextColor(Color.rgb(210, 210, 210));
        field.setTextSize(18);
        field.setContentDescription(description);
        return field;
    }

    private static void row(Activity activity, LinearLayout form, String name, EditText field, int dp) {
        LinearLayout line = new LinearLayout(activity);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(activity);
        label.setText(name);
        label.setTextSize(18);
        label.setTextColor(Color.rgb(210, 210, 210));
        line.addView(label, new LinearLayout.LayoutParams(120 * dp, 49 * dp));
        line.addView(field, new LinearLayout.LayoutParams(0, 49 * dp, 1f));
        form.addView(line, new LinearLayout.LayoutParams(-1, 51 * dp));
    }

    static void show(Activity activity, JSONObject fields, SaveAction save, Runnable advanced) {
        final int panel = Color.rgb(36, 36, 36), foreground = Color.rgb(210, 210, 210);
        final int dp = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setPadding(dp * 20, dp * 8, dp * 20, dp * 12);
        form.setBackgroundColor(panel);
        TextView title = new TextView(activity);
        title.setText("Настройки");
        title.setTextSize(26);
        title.setTextColor(foreground);
        title.setGravity(Gravity.CENTER);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp * 48));

        TextView device = new TextView(activity);
        device.setText("Устройство: " + CraftNumericInputCompactSettings.sourceDeviceLabel(fields));
        device.setTextColor(foreground);
        device.setTextSize(20);
        device.setGravity(Gravity.CENTER);
        form.addView(device, new LinearLayout.LayoutParams(-1, dp * 44));

        EditText register = input(activity, Integer.toString(
                CraftNumericInputCompactSettings.sourceRegister(fields)),
                InputType.TYPE_CLASS_NUMBER, "Регистр числового задатчика Craft");
        row(activity, form, "Рег:", register, dp);

        int numeric = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED;
        EditText min = input(activity, Double.toString(fields == null
                ? 0 : fields.optDouble("craftNumericMin", 0)), numeric, "Минимум числового ввода Craft");
        row(activity, form, "Мин:", min, dp);
        EditText max = input(activity, Double.toString(fields == null
                ? 1000 : fields.optDouble("craftNumericMax", 1000)), numeric, "Максимум числового ввода Craft");
        row(activity, form, "Макс:", max, dp);

        TextView modeLabel = new TextView(activity);
        modeLabel.setText("Тип значения:");
        modeLabel.setTextColor(foreground);
        modeLabel.setTextSize(17);
        form.addView(modeLabel);
        Spinner mode = new Spinner(activity);
        ArrayAdapter<String> modeOptions = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, new String[]{"Целое число", "Float32"});
        modeOptions.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mode.setAdapter(modeOptions);
        mode.setSelection(fields != null && fields.optInt("craftNumericMode", 0) == 1 ? 1 : 0);
        mode.setContentDescription("Тип числового ввода Craft");
        form.addView(mode, new LinearLayout.LayoutParams(-1, dp * 47));

        Spinner integerType = new Spinner(activity);
        ArrayAdapter<String> intTypes = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, new String[]{"UINT16", "INT16"});
        intTypes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        integerType.setAdapter(intTypes);
        integerType.setSelection(fields != null && fields.optInt("craftNumericTypeInt", 0) == 1 ? 1 : 0);
        integerType.setContentDescription("Целочисленный формат Craft");
        form.addView(integerType, new LinearLayout.LayoutParams(-1, dp * 47));

        EditText decimals = input(activity, Integer.toString(fields == null
                ? 1 : fields.optInt("craftNumericDecimals", 1)),
                InputType.TYPE_CLASS_NUMBER, "Дробные разряды числового задатчика Craft");
        row(activity, form, "Дробная:", decimals, dp);

        CheckBox systemFont = new CheckBox(activity);
        systemFont.setText("Системный шрифт");
        systemFont.setTextColor(foreground);
        systemFont.setChecked(fields != null && fields.optBoolean("craftNumericSystemFont", false));
        form.addView(systemFont);

        CheckBox frame = new CheckBox(activity);
        frame.setText("Рамка");
        frame.setTextColor(foreground);
        frame.setChecked(fields != null && fields.optBoolean("craftNumericFrame", false));
        form.addView(frame);

        TextView security = new TextView(activity);
        security.setText("Пароль, разрешение записи и адрес TCP — в расширенных настройках.");
        security.setTextColor(Color.rgb(173, 173, 173));
        security.setTextSize(13);
        form.addView(security);

        TextView advancedLink = new TextView(activity);
        advancedLink.setText("Дополнительно: пароль и привязка Modbus");
        advancedLink.setGravity(Gravity.CENTER);
        advancedLink.setTextColor(Color.rgb(173, 173, 173));
        advancedLink.setTextSize(14);
        advancedLink.setContentDescription("Расширенные настройки числового ввода Craft");
        form.addView(advancedLink, new LinearLayout.LayoutParams(-1, dp * 44));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
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
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        int source = Integer.parseInt(register.getText().toString().trim());
                        double minimum = Double.parseDouble(min.getText().toString().trim());
                        double maximum = Double.parseDouble(max.getText().toString().trim());
                        int fraction = Integer.parseInt(decimals.getText().toString().trim());
                        JSONObject updated = CraftNumericInputCompactSettings.withSettings(
                                fields, source, minimum, maximum, mode.getSelectedItemPosition(),
                                integerType.getSelectedItemPosition(), fraction,
                                systemFont.isChecked(), frame.isChecked());
                        if (save.save(updated)) dialog.dismiss();
                    } catch (Exception invalid) {
                        String reason = invalid.getMessage();
                        Toast.makeText(activity, reason == null ? "Проверь настройки числового ввода"
                                : reason, Toast.LENGTH_LONG).show();
                    }
                }));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(panel));
            int maxWidth = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f);
            window.setLayout(Math.min(dp * 620, maxWidth), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}
