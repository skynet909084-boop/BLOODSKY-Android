package com.bloodsky.hmi;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Source-backed default FBD visual skin on the actual editor canvas.
 * Derived from documented FB_AND/OR (two quarter-height contacts) and
 * FB_XOR/FB_TIMER (header + split lower body) layout geometry, not from
 * palette screenshots. Custom-styled BLOODSKY widgets retain prior drawing.
 */
public final class FbdSourceCanvasSkin {
    private FbdSourceCanvasSkin() {}

    public static boolean supports(String type) {
        return "FBD И".equals(type) || "FBD ИЛИ".equals(type)
                || "FBD XOR".equals(type) || "FBD Таймер".equals(type)
                || "FBD TOF".equals(type) || "FBD TP".equals(type)
                || "FBD Сравнение".equals(type)
                || "FBD CRAFT CTU".equals(type) || "FBD CRAFT T".equals(type)
                || "FBD CRAFT TIMER".equals(type) || "FBD CRAFT BLINK".equals(type)
                || "FBD RS".equals(type) || "FBD CRAFT SEL".equals(type)
                || "FBD CRAFT COMPARE".equals(type)
                || "FBD CRAFT COMPARE FLOAT".equals(type)
                || "FBD CRAFT ARITHMETIC INT".equals(type)
                || "FBD CRAFT ARITHMETIC FLOAT".equals(type)
                || "FBD CRAFT CHANGE I".equals(type)
                || "FBD CRAFT CHANGE F".equals(type)
                || "FBD CRAFT MOVE".equals(type)
                || "FBD CRAFT MX".equals(type) || "FBD CRAFT ARRAY".equals(type);
    }

    public static boolean shouldUse(MainActivity.Widget w) {
        if (w == null || !supports(w.type) || w.backgroundColor != 0
                || w.textColor != Color.rgb(31, 41, 55)
                || w.textSizeSp != 14 || !w.showFrame || w.autoResizeText
                || w.w < 70 || w.h < 36) return false;
        String defaultCaption = "FBD И".equals(w.type) ? "И" : "FBD ИЛИ".equals(w.type) ? "ИЛИ"
                : "FBD Сравнение".equals(w.type) ? "Сравнение"
                : "FBD CRAFT CTU".equals(w.type) ? "CTU 16 bit"
                : "FBD CRAFT T".equals(w.type) ? "T"
                : "FBD CRAFT TIMER".equals(w.type) ? w.extraFields.optString("craftTimerMode", "TON")
                : "FBD RS".equals(w.type) ? "RS"
                : "FBD CRAFT BLINK".equals(w.type) ? "BLINK"
                : "FBD CRAFT SEL".equals(w.type) ? "SEL"
                : "FBD CRAFT CHANGE I".equals(w.type) ? "CH_I"
                : "FBD CRAFT CHANGE F".equals(w.type) ? "CH_F"
                : "FBD CRAFT MOVE".equals(w.type) ? "MOVE"
                : "FBD CRAFT MX".equals(w.type) ? "MX"
                : "FBD CRAFT ARRAY".equals(w.type) ? "ARRAY"
                : "FBD CRAFT COMPARE".equals(w.type) ? w.compareOperator
                : "FBD CRAFT COMPARE FLOAT".equals(w.type)
                        || "FBD CRAFT ARITHMETIC INT".equals(w.type)
                        || "FBD CRAFT ARITHMETIC FLOAT".equals(w.type) ? w.compareOperator
                : isTimer(w.type) ? FbdDurationUnits.modeForType(w.type) : "XOR";
        // Preserve manually edited labels by falling back to the editable skin.
        return w.text == null || w.text.isEmpty() || defaultCaption.equals(w.text)
                || ("FBD CRAFT T".equals(w.type)
                    && ("T".equals(w.text) || "R".equals(w.text) || "F".equals(w.text)));
    }

    public static float inputY(String type, String name) {
        if ("FBD CRAFT MOVE".equals(type))
            return "EN".equalsIgnoreCase(name) ? 0.5f : 5f / 6f;
        if ("FBD CRAFT ARRAY".equals(type))
            return "EN".equalsIgnoreCase(name) ? 3f/8f : "DT".equalsIgnoreCase(name) ? 5f/8f : 7f/8f;
        if ("FBD XOR".equals(type) || isTimer(type)) {
            String[] pins = MainActivity.knownFbdPins(type, false);
            for (int i = 0; i < pins.length; i++) {
                if (pins[i].equalsIgnoreCase(name)) return i == 0 ? 0.5f : (5f / 6f);
            }
        }
        return MainActivity.fbdTerminalFraction(type, name, false);
    }

    private static boolean isTimer(String type) {
        return "FBD Таймер".equals(type) || "FBD TOF".equals(type)
                || "FBD TP".equals(type) || "FBD CRAFT BLINK".equals(type)
                || "FBD CRAFT TIMER".equals(type) || "FBD CRAFT MX".equals(type);
    }

    /** Decompiled original FB_CTU=draw9 and FB_RS/FB_TRIGGER=draw8. */
    private static boolean hasNativeHeader(String type) {
        return "FBD CRAFT CTU".equals(type) || "FBD CRAFT SEL".equals(type)
                || "FBD CRAFT T".equals(type) || "FBD RS".equals(type)
                || "FBD CRAFT CHANGE I".equals(type)
                || "FBD CRAFT CHANGE F".equals(type)
                || "FBD CRAFT MOVE".equals(type) || "FBD CRAFT ARRAY".equals(type);
    }

    public static void draw(Canvas canvas, Paint brush, MainActivity.Widget w, float density) {
        if (!shouldUse(w)) return;
        float pin = Math.max(5, 10 * density);
        float x0 = w.x, x1 = w.x + w.w, y0 = w.y, y1 = w.y + w.h;
        float left = x0 + pin, right = x1 - pin;
        RectF body = new RectF(left, y0, right, y1);
        Paint.Style priorStyle = brush.getStyle();
        float priorSize = brush.getTextSize();
        float priorWidth = brush.getStrokeWidth();
        Paint.Align priorAlign = brush.getTextAlign();
        int priorColor = brush.getColor();
        try {
            brush.setStyle(Paint.Style.FILL);
            brush.setColor(Color.rgb(136, 136, 136));
            canvas.drawRect(body, brush);
            brush.setStyle(Paint.Style.STROKE);
            brush.setColor(Color.BLACK);
            brush.setStrokeWidth(Math.max(1, density));
            canvas.drawRect(body, brush);

            boolean divided = "FBD XOR".equals(w.type) || isTimer(w.type)
                    || hasNativeHeader(w.type);
            float headerFraction = ("FBD CRAFT CTU".equals(w.type)
                    || "FBD CRAFT SEL".equals(w.type)
                    || "FBD CRAFT ARRAY".equals(w.type)) ? 0.25f : 1f / 3f;
            if (divided) {
                float header = y0 + w.h * headerFraction;
                brush.setColor(Color.BLACK);
                canvas.drawLine(left, header, right, header, brush);
                canvas.drawLine((left + right) / 2f, header, (left + right) / 2f, y1, brush);
            }
            String[] inputPins = MainActivity.knownFbdPins(w.type, false);
            brush.setColor(Color.BLACK);
            brush.setStyle(Paint.Style.STROKE);
            for (String port : inputPins) {
                float yy = y0 + w.h * inputY(w.type, port);
                canvas.drawLine(x0, yy, left, yy, brush);
            }
            String[] outputPins = MainActivity.knownFbdPins(w.type, true);
            for (String port : outputPins) {
                float yy = y0 + w.h * MainActivity.fbdTerminalFraction(w.type, port, true);
                canvas.drawLine(right, yy, x1, yy, brush);
            }

            brush.setStyle(Paint.Style.FILL);
            brush.setColor(Color.WHITE);
            brush.setTextAlign(Paint.Align.CENTER);
            brush.setTextSize(Math.min(12f * density, Math.max(7f * density, w.h / 5f)));
            String name = "FBD И".equals(w.type) ? "AND" : "FBD ИЛИ".equals(w.type) ? "OR"
                    : "FBD XOR".equals(w.type) ? "XOR"
                    : "FBD Сравнение".equals(w.type) ? w.compareOperator
                    : "FBD CRAFT CTU".equals(w.type) ? "CTU"
                : "FBD CRAFT T".equals(w.type) ? "T"
                : "FBD CRAFT TIMER".equals(w.type) ? w.extraFields.optString("craftTimerMode", "TON")
                    : "FBD RS".equals(w.type) ? "RS"
                    : "FBD CRAFT BLINK".equals(w.type) ? "BLINK"
                    : "FBD CRAFT SEL".equals(w.type) ? "SEL"
                    : "FBD CRAFT CHANGE I".equals(w.type) ? "CH_I"
                    : "FBD CRAFT CHANGE F".equals(w.type) ? "CH_F"
                    : "FBD CRAFT MOVE".equals(w.type) ? "MOVE"
                    : "FBD CRAFT COMPARE".equals(w.type) ? w.compareOperator
                    : FbdDurationUnits.modeForType(w.type);
            float center = (left + right) / 2f;
            if (divided) {
                canvas.drawText(name, center, y0 + w.h * headerFraction / 2f
                        - (brush.ascent() + brush.descent()) / 2f, brush);
                float sectionStart = y0 + w.h * headerFraction;
                float remaining = w.h * (1f - headerFraction);
                brush.setTextSize(Math.min(10 * density, Math.max(6 * density, w.h / 7f)));
                for (int i = 0; i < inputPins.length; i++) {
                    float yy = y0 + w.h * inputY(w.type, inputPins[i]);
                    canvas.drawText(inputPins[i], (left + center) / 2f,
                            yy - (brush.ascent() + brush.descent()) / 2f, brush);
                }
                if (outputPins.length > 0)
                    canvas.drawText(outputPins[0], (center + right) / 2f,
                            y0 + w.h * MainActivity.fbdTerminalFraction(w.type, outputPins[0], true)
                                    - (brush.ascent() + brush.descent()) / 2f, brush);
            } else {
                canvas.drawText(name, center, y0 + w.h / 2f
                        - (brush.ascent() + brush.descent()) / 2f, brush);
                // New Craft-compatible source blocks retain visible port labels,
                // including the counter's unusual original +,-,R terminals.
                if ("FBD CRAFT CTU".equals(w.type) || "FBD CRAFT T".equals(w.type)
                        || "FBD RS".equals(w.type)
                        || "FBD CRAFT SEL".equals(w.type)
                        || "FBD CRAFT COMPARE".equals(w.type)
                        || "FBD CRAFT MOVE".equals(w.type)) {
                    brush.setTextAlign(Paint.Align.LEFT);
                    brush.setTextSize(Math.min(10f * density, Math.max(7f * density, w.h / 8f)));
                    for (String port : inputPins) {
                        float py = y0 + w.h * inputY(w.type, port);
                        canvas.drawText(port, left + 4 * density,
                                py - (brush.ascent() + brush.descent()) / 2f, brush);
                    }
                    if (outputPins.length > 0) {
                        brush.setTextAlign(Paint.Align.RIGHT);
                        canvas.drawText(outputPins[0], right - 4 * density,
                                y0 + w.h / 2f - (brush.ascent() + brush.descent()) / 2f,
                                brush);
                    }
                }
            }
        } finally {
            brush.setColor(priorColor);
            brush.setStyle(priorStyle);
            brush.setTextSize(priorSize);
            brush.setTextAlign(priorAlign);
            brush.setStrokeWidth(priorWidth);
        }
    }
}
