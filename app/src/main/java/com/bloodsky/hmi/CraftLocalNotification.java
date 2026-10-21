package com.bloodsky.hmi;

/** Bounded content helpers for source-imported Craft ID25 local notifications. */
public final class CraftLocalNotification {
    private CraftLocalNotification() {}

    public static String title(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) value = "BLOODSKY";
        return value.length() > 120 ? value.substring(0, 120) : value;
    }

    public static String body(String body) {
        String value = body == null ? "" : body;
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    /** Stable per-screen/widget ID lets a later event update only its own notification. */
    public static int notificationId(int screenIndex, int widgetIndex) {
        return (screenIndex + ":" + widgetIndex + ":craft-id25").hashCode() & 0x7fffffff;
    }
}
