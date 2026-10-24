package com.bloodsky.hmi;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
                || "FBD CRAFT MX".equals(type) || "FBD CRAFT ARRAY".equals(type)
                || "FBD CRAFT LOG".equals(type) || "FBD CRAFT WORD TO FLOAT".equals(type)
                || "FBD CRAFT POW".equals(type) || "FBD CRAFT SIN".equals(type)
                || "FBD CRAFT ABS".equals(type) || "FBD CRAFT INT TO FLOAT".equals(type)
                || "FBD CRAFT FLOAT TO INT".equals(type)
                || "FBD CRAFT SENSOR ORIENTATION".equals(type)
                || "FBD CRAFT SENSOR LIGHT".equals(type)
                || "FBD CRAFT SENSOR SPEED".equals(type)
                || "FBD CRAFT SUNRISE".equals(type)
                || "FBD CRAFT ODTEMP".equals(type)
                || isCraftVariable(type)
                || MainActivity.isCraftMqttSubscriber(type) || MainActivity.isCraftMqttPublisher(type);
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
                : "FBD CRAFT LOG".equals(w.type) ? "LN"
                : "FBD CRAFT WORD TO FLOAT".equals(w.type) ? "WORD->FL"
                : "FBD CRAFT POW".equals(w.type) ? "POW"
                : "FBD CRAFT SIN".equals(w.type) ? "SIN"
                : "FBD CRAFT ABS".equals(w.type) ? "ABS"
                : "FBD CRAFT INT TO FLOAT".equals(w.type) ? "INT->FL"
                : "FBD CRAFT FLOAT TO INT".equals(w.type) ? "FL->INT"
                : "FBD CRAFT SENSOR ORIENTATION".equals(w.type) ? orientationCaption(w)
                : "FBD CRAFT SENSOR LIGHT".equals(w.type) ? "LM"
                : "FBD CRAFT SENSOR SPEED".equals(w.type) ? "SM"
                : "FBD CRAFT SUNRISE".equals(w.type) ? sunriseCaption(w)
                : "FBD CRAFT ODTEMP".equals(w.type) ? "DT"
                : isCraftVariable(w.type) ? variableCaption(w)
                : "FBD CRAFT COMPARE".equals(w.type) ? w.compareOperator
                : MainActivity.isCraftMqttSubscriber(w.type) ? "MQTT_S"
                : MainActivity.isCraftMqttPublisher(w.type) ? "MQTT_P"
                : "FBD CRAFT COMPARE FLOAT".equals(w.type)
                        || "FBD CRAFT ARITHMETIC INT".equals(w.type)
                        || "FBD CRAFT ARITHMETIC FLOAT".equals(w.type) ? w.compareOperator
                : isTimer(w.type) ? FbdDurationUnits.modeForType(w.type) : "XOR";
        // Preserve manually edited labels by falling back to the editable skin.
        return w.text == null || w.text.isEmpty() || defaultCaption.equals(w.text)
                || isDefaultVariableCaption(w)
                || ("FBD CRAFT T".equals(w.type)
                    && ("T".equals(w.text) || "R".equals(w.text) || "F".equals(w.text)));
    }

    public static float inputY(String type, String name) {
        if (MainActivity.isCraftMqttPublisher(type))
            return "EN".equalsIgnoreCase(name) ? 1f / 6f : 2f / 3f;
        if ("FBD CRAFT MOVE".equals(type))
            return "EN".equalsIgnoreCase(name) ? 0.5f : 5f / 6f;
        if ("FBD CRAFT POW".equals(type))
            return "X".equalsIgnoreCase(name) ? 0.5f : 5f / 6f;
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

    private static boolean isCraftVariable(String type) {
        return MainActivity.isCraftRegisterSource(type) || MainActivity.isCraftRegisterSink(type)
                || MainActivity.isCraftNamedVariableSource(type)
                || MainActivity.isCraftNamedVariableSink(type);
    }

    static String variableCaption(MainActivity.Widget widget) {
        String type = widget.type;
        if (MainActivity.isCraftNamedVariableSource(type) || MainActivity.isCraftNamedVariableSink(type)) {
            String name = widget.extraFields.optString("craftVariableName",
                    widget.extraFields.optString("name", ""));
            return name.isEmpty() ? "var name" : name;
        }
        if (MainActivity.isCraftRegisterSource(type)
                && widget.extraFields.optBoolean("craftVarConstantMode", false))
            return widget.extraFields.optString("craftVarConstant", "0");
        String reg = widget.extraFields.optString("reg", "0");
        return type.endsWith("BOOL") ? "S#" + reg + "."
                + widget.extraFields.optInt("craftVarBit", 0) : "S#" + reg;
    }

    private static boolean isBooleanCraftVariable(String type) { return type.endsWith("BOOL"); }
    private static boolean isCraftVariableSource(String type) {
        return MainActivity.isCraftRegisterSource(type) || MainActivity.isCraftNamedVariableSource(type);
    }
    private static int craftVariableShape(String type) {
        if (MainActivity.isCraftNamedVariableSource(type)) return 4;
        if (MainActivity.isCraftNamedVariableSink(type)) return 5;
        if (type.endsWith("FLOAT")) return 3;
        if (type.endsWith("INT")) return 2;
        return 1;
    }
    private static boolean isDefaultVariableCaption(MainActivity.Widget w) {
        if (!isCraftVariable(w.type)) return false;
        String text = w.text == null ? "" : w.text;
        return text.isEmpty() || text.equals(variableCaption(w)) || "VAR".equals(text)
                || "VAR_INT".equals(text) || "VAR_FLOAT".equals(text) || "S#".equals(text)
                || "S#_INT".equals(text) || "S#_FLOAT".equals(text) || "var name".equals(text);
    }

    private static void drawCraftVariable(Canvas canvas, Paint brush, MainActivity.Widget w, float density) {
        float stroke = Math.max(1f, density), pin = Math.max(5f, 10f * density);
        boolean source = isCraftVariableSource(w.type);
        int shape = craftVariableShape(w.type);
        float x0 = w.x, x1 = w.x + w.w, y0 = w.y, y1 = w.y + w.h, cy = (y0 + y1) / 2f;
        float left = source ? x0 + stroke : x0 + pin, right = source ? x1 - pin : x1 - stroke;
        RectF body = new RectF(left, y0 + stroke, right, y1 - stroke);
        Paint.Style oldStyle = brush.getStyle(); Paint.Align oldAlign = brush.getTextAlign();
        int oldColor = brush.getColor(); float oldSize = brush.getTextSize(), oldWidth = brush.getStrokeWidth();
        boolean oldAA = brush.isAntiAlias(); Path shapePath = new Path();
        try {
            brush.setAntiAlias(true); brush.setStrokeWidth(stroke);
            if (shape == 4) {
                shapePath.moveTo(left, body.top); shapePath.lineTo(right - pin, body.top);
                shapePath.lineTo(right, cy); shapePath.lineTo(right - pin, body.bottom);
                shapePath.lineTo(left, body.bottom); shapePath.close();
            } else if (shape == 5) {
                shapePath.moveTo(left, cy); shapePath.lineTo(left + pin, body.top);
                shapePath.lineTo(right, body.top); shapePath.lineTo(right, body.bottom);
                shapePath.lineTo(left + pin, body.bottom); shapePath.close();
            } else if (shape == 3) {
                float bevel = Math.min(body.height() / 2f, 12f * density);
                shapePath.moveTo(body.left + bevel, body.top); shapePath.lineTo(body.right - bevel, body.top);
                shapePath.lineTo(body.right, cy); shapePath.lineTo(body.right - bevel, body.bottom);
                shapePath.lineTo(body.left + bevel, body.bottom); shapePath.lineTo(body.left, cy); shapePath.close();
            }
            brush.setStyle(Paint.Style.FILL); brush.setColor(Color.rgb(136, 136, 136));
            if (shape == 2) canvas.drawRoundRect(body, pin, pin, brush);
            else if (shape >= 3) canvas.drawPath(shapePath, brush);
            else canvas.drawRect(body, brush);
            brush.setStyle(Paint.Style.STROKE); brush.setColor(Color.BLACK);
            if (shape == 2) canvas.drawRoundRect(body, pin, pin, brush);
            else if (shape >= 3) canvas.drawPath(shapePath, brush);
            else canvas.drawRect(body, brush);
            if (!source) canvas.drawLine(x0, cy, left, cy, brush);
            else if (isBooleanCraftVariable(w.type) && MainActivity.isFbdBlockOutputInverted(w)) {
                float radius = 3.5f * density, cx = right + radius;
                canvas.drawLine(cx + radius, cy, x1, cy, brush); canvas.drawCircle(cx, cy, radius, brush);
            } else canvas.drawLine(right, cy, x1, cy, brush);
            String caption = variableCaption(w);
            brush.setStyle(Paint.Style.FILL); brush.setColor(Color.WHITE); brush.setTextAlign(Paint.Align.CENTER);
            float size = Math.min(14f * density, Math.max(7f * density, w.h * .45f));
            brush.setTextSize(size);
            float available = Math.max(1, body.width() - 8 * density), measured = brush.measureText(caption);
            if (measured > available && measured > 0) brush.setTextSize(Math.max(6f * density, size * available / measured));
            canvas.drawText(caption, (left + right) / 2f, cy - (brush.ascent() + brush.descent()) / 2f, brush);
        } finally {
            brush.setStyle(oldStyle); brush.setTextAlign(oldAlign); brush.setColor(oldColor);
            brush.setTextSize(oldSize); brush.setStrokeWidth(oldWidth); brush.setAntiAlias(oldAA);
        }
    }

    static String orientationCaption(MainActivity.Widget widget) {
        int axis = Math.max(1, Math.min(3, widget.extraFields.optInt("craftSensorAxis",
                widget.extraFields.optInt("type", 1))));
        return axis == 2 ? "ORIENTATION_Y" : axis == 3 ? "ORIENTATION_Z" : "ORIENTATION_X";
    }

    static String sunriseCaption(MainActivity.Widget widget) {
        return widget.extraFields.optString("craftSunriseMode",
                widget.extraFields.optInt("type", 1) == 2 ? "SS" : "SR");
    }

    /** Decompiled original FB_CTU=draw9 and FB_RS/FB_TRIGGER=draw8. */
    private static boolean hasNativeHeader(String type) {
        return "FBD CRAFT CTU".equals(type) || "FBD CRAFT SEL".equals(type)
                || "FBD CRAFT T".equals(type) || "FBD RS".equals(type)
                || "FBD CRAFT CHANGE I".equals(type)
                || "FBD CRAFT CHANGE F".equals(type)
                || "FBD CRAFT MOVE".equals(type) || "FBD CRAFT ARRAY".equals(type)
                || "FBD CRAFT POW".equals(type) || MainActivity.isCraftMqttPublisher(type);
    }

    public static void draw(Canvas canvas, Paint brush, MainActivity.Widget w, float density) {
        if (!shouldUse(w)) return;
        if (isCraftVariable(w.type)) { drawCraftVariable(canvas, brush, w, density); return; }
        float pin = Math.max(5, 10 * density);
        boolean simpleSource = "FBD CRAFT SENSOR ORIENTATION".equals(w.type)
                || "FBD CRAFT SENSOR LIGHT".equals(w.type)
                || "FBD CRAFT SENSOR SPEED".equals(w.type)
                || "FBD CRAFT SUNRISE".equals(w.type)
                || "FBD CRAFT ODTEMP".equals(w.type);
        float x0 = w.x, x1 = w.x + w.w, y0 = w.y, y1 = w.y + w.h;
        float left = x0 + (simpleSource ? Math.max(1, density) : pin), right = x1 - pin;
        RectF body = simpleSource
                ? new RectF(left, y0 + Math.max(1, density), right, y1 - Math.max(1, density))
                : new RectF(left, y0, right, y1);
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
                if ("Q".equals(port) && !simpleSource && MainActivity.isFbdBlockOutputInverted(w)) {
                    float radius = 3.5f * density;
                    float cx = right + radius;
                    canvas.drawLine(cx + radius, yy, x1, yy, brush);
                    canvas.drawCircle(cx, yy, radius, brush);
                } else canvas.drawLine(right, yy, x1, yy, brush);
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
                    : "FBD CRAFT VAR BOOL".equals(w.type) ? "VAR"
                    : "FBD CRAFT VAR INT".equals(w.type) ? "VAR_INT"
                    : "FBD CRAFT VAR FLOAT".equals(w.type) ? "VAR_FLOAT"
                    : "FBD CRAFT VAR OUT BOOL".equals(w.type) ? "S#"
                    : "FBD CRAFT VAR OUT INT".equals(w.type) ? "S#_INT"
                    : "FBD CRAFT VAR OUT FLOAT".equals(w.type) ? "S#_FLOAT"
                    : MainActivity.isCraftNamedVariableSource(w.type) ? "var name"
                    : MainActivity.isCraftNamedVariableSink(w.type) ? "var name"
                    : MainActivity.isCraftMqttSubscriber(w.type) ? "MQTT_S"
                    : MainActivity.isCraftMqttPublisher(w.type) ? "MQTT_P"
                    : "FBD CRAFT MOVE".equals(w.type) ? "MOVE"
                    : "FBD CRAFT MX".equals(w.type) ? "MX"
                    : "FBD CRAFT ARRAY".equals(w.type) ? "ARRAY"
                    : "FBD CRAFT LOG".equals(w.type) ? "LN"
                    : "FBD CRAFT WORD TO FLOAT".equals(w.type) ? "WORD->FL"
                    : "FBD CRAFT POW".equals(w.type) ? "POW"
                    : "FBD CRAFT SIN".equals(w.type) ? "SIN"
                    : "FBD CRAFT ABS".equals(w.type) ? "ABS"
                    : "FBD CRAFT INT TO FLOAT".equals(w.type) ? "INT->FL"
                    : "FBD CRAFT FLOAT TO INT".equals(w.type) ? "FL->INT"
                    : "FBD CRAFT SENSOR ORIENTATION".equals(w.type) ? orientationCaption(w)
                    : "FBD CRAFT SENSOR LIGHT".equals(w.type) ? "LM"
                    : "FBD CRAFT SENSOR SPEED".equals(w.type) ? "SM"
                    : "FBD CRAFT SUNRISE".equals(w.type) ? sunriseCaption(w)
                    : "FBD CRAFT ODTEMP".equals(w.type) ? "DT"
                    : "FBD CRAFT COMPARE".equals(w.type) ? w.compareOperator
                    : FbdDurationUnits.modeForType(w.type);
            float center = simpleSource ? (x0 + w.w / 2f - pin / 2f) : (left + right) / 2f;
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
                        || MainActivity.isCraftRegisterSource(w.type)
                        || MainActivity.isCraftRegisterSink(w.type)
                        || MainActivity.isCraftNamedVariableSource(w.type)
                        || MainActivity.isCraftNamedVariableSink(w.type)
                        || "FBD CRAFT MOVE".equals(w.type)
                        || "FBD CRAFT LOG".equals(w.type)
                        || "FBD CRAFT WORD TO FLOAT".equals(w.type)
                        || "FBD CRAFT POW".equals(w.type)
                        || "FBD CRAFT SIN".equals(w.type)
                        || "FBD CRAFT ABS".equals(w.type)
                        || "FBD CRAFT INT TO FLOAT".equals(w.type)
                        || "FBD CRAFT FLOAT TO INT".equals(w.type)) {
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
