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

import java.util.Locale;

/** Compact source-style Craft ID2 label properties, no network side effects. */
final class CraftLabelSettingsDialog {
    interface SaveAction {
        boolean save(JSONObject fields, String caption, int color, int size, int background,
                     boolean frame, boolean autoResize);
    }

    private CraftLabelSettingsDialog() {}

    private static EditText input(Activity activity, String value, int inputType, String name) {
        EditText view = new EditText(activity);
        view.setInputType(inputType);
        view.setSingleLine(true);
        view.setText(value);
        view.setTextColor(Color.rgb(210, 210, 210));
        view.setTextSize(18);
        view.setContentDescription(name);
        return view;
    }

    private static void row(Activity activity, LinearLayout form, String title, EditText value, int dp) {
        LinearLayout line = new LinearLayout(activity);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(activity);
        label.setText(title);
        label.setTextColor(Color.rgb(210, 210, 210));
        label.setTextSize(18);
        line.addView(label, new LinearLayout.LayoutParams(105 * dp, 50 * dp));
        line.addView(value, new LinearLayout.LayoutParams(0, 50 * dp, 1f));
        form.addView(line, new LinearLayout.LayoutParams(-1, 52 * dp));
    }

    static void show(Activity activity, JSONObject fields, String caption, int textColor,
                     int textSize, int background, boolean frame, boolean autoResize,
                     SaveAction save, Runnable advanced) {
        final int panel = Color.rgb(36, 36, 36), foreground = Color.rgb(210, 210, 210);
        final int dp = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setPadding(20 * dp, 8 * dp, 20 * dp, 12 * dp);
        form.setBackgroundColor(panel);

        TextView heading = new TextView(activity);
        heading.setText("Настройки");
        heading.setTextColor(foreground);
        heading.setTextSize(26);
        heading.setGravity(Gravity.CENTER);
        form.addView(heading, new LinearLayout.LayoutParams(-1, 48 * dp));

        EditText text = input(activity, caption == null ? "" : caption,
                InputType.TYPE_CLASS_TEXT, "Надпись компонента Craft");
        text.setHint("Текст надписи");
        form.addView(text, new LinearLayout.LayoutParams(-1, 58 * dp));

        EditText size = input(activity, Integer.toString(textSize),
                InputType.TYPE_CLASS_NUMBER, "Размер шрифта надписи Craft");
        row(activity, form, "Размер:", size, dp);

        EditText color = input(activity, String.format(Locale.ROOT, "#%08X", textColor),
                InputType.TYPE_CLASS_TEXT, "Цвет текста надписи Craft");
        row(activity, form, "Цвет:", color, dp);

        EditText backgroundField = input(activity, background == 0 ? "Прозрачный"
                : String.format(Locale.ROOT, "#%08X", background),
                InputType.TYPE_CLASS_TEXT, "Фон надписи Craft, прозрачный или цвет");
        row(activity, form, "Фон:", backgroundField, dp);

        CheckBox showFrame = new CheckBox(activity);
        showFrame.setText("Рамка");
        showFrame.setChecked(frame);
        showFrame.setTextColor(foreground);
        form.addView(showFrame, new LinearLayout.LayoutParams(-1, 44 * dp));

        CheckBox fitText = new CheckBox(activity);
        fitText.setText("Подбирать размер текста");
        fitText.setChecked(autoResize);
        fitText.setTextColor(foreground);
        form.addView(fitText, new LinearLayout.LayoutParams(-1, 44 * dp));

        TextView advancedLink = new TextView(activity);
        advancedLink.setText("Дополнительно: размеры и свойства");
        advancedLink.setTextSize(13);
        advancedLink.setTextColor(Color.rgb(173, 173, 173));
        advancedLink.setGravity(Gravity.CENTER);
        advancedLink.setContentDescription("Расширенные настройки надписи Craft");
        form.addView(advancedLink, new LinearLayout.LayoutParams(-1, 44 * dp));

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
                        String captionValue = text.getText().toString();
                        if (captionValue.length() > 2000)
                            throw new IllegalArgumentException("Надпись не длиннее 2000 символов");
                        int sizeValue = CraftLabelAppearance.requireTextSize(size.getText().toString());
                        int colorValue = Color.parseColor(color.getText().toString().trim());
                        String requestedBackground = backgroundField.getText().toString().trim();
                        int backgroundValue = requestedBackground.isEmpty()
                                || "Прозрачный".equalsIgnoreCase(requestedBackground)
                                || "transparent".equalsIgnoreCase(requestedBackground)
                                ? 0 : Color.parseColor(requestedBackground);
                        JSONObject updated = CraftLabelAppearance.withSettings(
                                fields, showFrame.isChecked(), fitText.isChecked());
                        if (save.save(updated, captionValue, colorValue, sizeValue,
                                backgroundValue, showFrame.isChecked(), fitText.isChecked()))
                            dialog.dismiss();
                    } catch (Exception invalid) {
                        String reason = invalid.getMessage();
                        Toast.makeText(activity, reason == null ? "Проверь настройки надписи"
                                : reason, Toast.LENGTH_LONG).show();
                    }
                }));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(panel));
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f);
            window.setLayout(Math.min(620 * dp, width), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}
