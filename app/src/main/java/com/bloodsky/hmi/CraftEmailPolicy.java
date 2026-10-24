package com.bloodsky.hmi;

/** Input and activation rules for the user-confirmed Craft email composer. */
public final class CraftEmailPolicy {
    private CraftEmailPolicy() { }

    /** Returns an operator-facing validation error, or null when the draft is safe to open. */
    public static String validationError(String recipient, String subject, String body) {
        String to = recipient == null ? "" : recipient.trim();
        String title = subject == null ? "" : subject;
        String message = body == null ? "" : body;
        if (to.isEmpty() || to.length() > 320 || containsLineBreak(to) || to.indexOf(',') >= 0 || to.indexOf(';') >= 0
                || !to.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+"))
            return "Укажи один корректный адрес получателя";
        if (title.length() > 120 || containsLineBreak(title)) return "Тема письма должна быть до 120 символов в одной строке";
        if (message.length() > 2000) return "Текст письма не должен превышать 2000 символов";
        return null;
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
