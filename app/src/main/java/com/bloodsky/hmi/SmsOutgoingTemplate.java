package com.bloodsky.hmi;

/** Bounded renderer for the KaSCADA %1..%8 outgoing-SMS value placeholders. */
public final class SmsOutgoingTemplate {
    private SmsOutgoingTemplate() {}

    public static String render(String template, String[] values, String triggerValue) {
        if (values == null || values.length < 1 || values.length > 8)
            throw new IllegalArgumentException("SMS register count must be 1..8");
        String text = template == null || template.trim().isEmpty() ? "Событие BLOODSKY" : template.trim();
        text = text.replace("{value}", triggerValue == null ? "" : triggerValue);
        StringBuilder result = new StringBuilder(Math.min(1200, text.length() + values.length * 8));
        boolean hasNumberedPlaceholder = false;
        for (int i = 0; i < text.length();) {
            char ch = text.charAt(i);
            if (ch == '%' && i + 1 < text.length() && text.charAt(i + 1) >= '1' && text.charAt(i + 1) <= '9') {
                int digit = text.charAt(i + 1) - '0';
                if (digit > 8 || digit > values.length)
                    throw new IllegalArgumentException("SMS placeholder exceeds configured register count");
                result.append(values[digit - 1]);
                hasNumberedPlaceholder = true;
                i += 2;
            } else {
                result.append(ch);
                i++;
            }
            if (result.length() > 1200) return result.substring(0, 1200);
        }
        if (!hasNumberedPlaceholder && (template == null || !template.contains("{value}"))) {
            for (String value : values) {
                if (result.length() >= 1200) break;
                result.append(' ').append(value == null ? "" : value);
            }
        }
        return result.length() > 1200 ? result.substring(0, 1200) : result.toString();
    }
}
