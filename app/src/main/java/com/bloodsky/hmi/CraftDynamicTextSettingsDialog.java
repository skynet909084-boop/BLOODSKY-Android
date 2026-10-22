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

/** Source-style Craft ID7 editor; no permission to write data registers here. */
final class CraftDynamicTextSettingsDialog {
    interface SaveAction {
        boolean save(JSONObject source, String[] texts, int[] colors,
                     boolean frame, boolean autoResize);
    }

    private CraftDynamicTextSettingsDialog() {}

    private static EditText field(Activity activity, String value, String hint) {
        EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setTextSize(15);
        field.setTextColor(Color.rgb(210, 210, 210));
        field.setText(value);
        field.setHint(hint);
        field.setContentDescription(hint);
        return field;
    }

    static void show(Activity activity, JSONObject fields,
                     String[] savedTexts, int[] savedColors,
                     boolean frame, boolean autoResize,
                     SaveAction save, Runnable advanced) {
        final int dark = Color.rgb(36, 36, 36), textColor = Color.rgb(210, 210, 210);
        final int dp = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setPadding(18 * dp, 8 * dp, 18 * dp, 12 * dp);
        form.setBackgroundColor(dark);

        TextView title = new TextView(activity);
        title.setText("Настройки");
        title.setTextColor(textColor);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        form.addView(title, new LinearLayout.LayoutParams(-1, 46 * dp));

        TextView device = new TextView(activity);
        device.setText("Устройство: " + CraftDynamicTextProperties.sourceDeviceLabel(fields));
        device.setTextColor(textColor);
        device.setTextSize(20);
        device.setGravity(Gravity.CENTER);
        form.addView(device, new LinearLayout.LayoutParams(-1, 44 * dp));

        LinearLayout addressRow = new LinearLayout(activity);
        addressRow.setGravity(Gravity.CENTER);
        TextView registerLabel = new TextView(activity);
        registerLabel.setText("Рег:");
        registerLabel.setTextColor(textColor);
        registerLabel.setTextSize(19);
        addressRow.addView(registerLabel);
        EditText register = field(activity,
                Integer.toString(CraftDynamicTextProperties.sourceRegister(fields)),
                "Регистр динамического текста Craft");
        register.setInputType(InputType.TYPE_CLASS_NUMBER);
        register.setGravity(Gravity.CENTER);
        addressRow.addView(register, new LinearLayout.LayoutParams(90 * dp, 50 * dp));
        form.addView(addressRow, new LinearLayout.LayoutParams(-1, 52 * dp));

        TextView stateHeader = new TextView(activity);
        stateHeader.setText("Состояния 0–15: надпись и цвет фона");
        stateHeader.setTextColor(textColor);
        stateHeader.setTextSize(16);
        form.addView(stateHeader, new LinearLayout.LayoutParams(-1, 32 * dp));

        EditText[] textFields = new EditText[CraftDynamicTextProperties.STATE_COUNT];
        EditText[] colorFields = new EditText[CraftDynamicTextProperties.STATE_COUNT];
        for (int state = 0; state < CraftDynamicTextProperties.STATE_COUNT; state++) {
            TextView label = new TextView(activity);
            label.setText("Состояние " + state);
            label.setTextColor(textColor);
            label.setTextSize(14);
            form.addView(label);
            EditText name = field(activity, savedTexts[state] == null ? "" : savedTexts[state],
                    "Текст состояния " + state);
            textFields[state] = name;
            form.addView(name, new LinearLayout.LayoutParams(-1, 48 * dp));
            EditText color = field(activity,
                    CraftDynamicTextProperties.formatColor(savedColors[state]),
                    "Цвет состояния " + state + " (#AARRGGBB)");
            colorFields[state] = color;
            form.addView(color, new LinearLayout.LayoutParams(-1, 48 * dp));
        }

        CheckBox showFrame = new CheckBox(activity);
        showFrame.setText("Показывать рамку");
        showFrame.setChecked(frame);
        showFrame.setTextColor(textColor);
        form.addView(showFrame);

        CheckBox fitText = new CheckBox(activity);
        fitText.setText("Подбирать размер текста");
        fitText.setChecked(autoResize);
        fitText.setTextColor(textColor);
        form.addView(fitText);

        TextView advancedLink = new TextView(activity);
        advancedLink.setText("Дополнительно: размеры и привязка Modbus");
        advancedLink.setTextColor(Color.rgb(173, 173, 173));
        advancedLink.setGravity(Gravity.CENTER);
        advancedLink.setTextSize(13);
        advancedLink.setContentDescription("Расширенные свойства динамического текста Craft");
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
                int address = Integer.parseInt(register.getText().toString().trim());
                String[] labels = new String[CraftDynamicTextProperties.STATE_COUNT];
                int[] colors = new int[CraftDynamicTextProperties.STATE_COUNT];
                for (int i = 0; i < CraftDynamicTextProperties.STATE_COUNT; i++) {
                    labels[i] = textFields[i].getText().toString();
                    colors[i] = CraftDynamicTextProperties.parseColor(
                            colorFields[i].getText().toString());
                }
                JSONObject updated = CraftDynamicTextProperties.withSettings(fields,
                        address, labels, colors, showFrame.isChecked(), fitText.isChecked());
                if (save.save(updated, labels, colors,
                        showFrame.isChecked(), fitText.isChecked())) dialog.dismiss();
            } catch (Exception invalid) {
                String reason = invalid.getMessage();
                Toast.makeText(activity, reason == null ? "Проверь подписи и цвета"
                        : reason, Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(dark));
            int maxWidth = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f);
            int maxHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.75f);
            window.setLayout(Math.min(620 * dp, maxWidth), maxHeight);
        }
    }
}
