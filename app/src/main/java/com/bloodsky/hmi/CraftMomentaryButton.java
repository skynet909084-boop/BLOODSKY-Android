package com.bloodsky.hmi;

import org.json.JSONObject;

/** Safe mapping for Craft button mode 0: hold a register bit while pressed. */
public final class CraftMomentaryButton {
    private CraftMomentaryButton() { }

    public static int bit(JSONObject fields) {
        int value = fields == null ? 0 : fields.optInt("craftButtonBit", 0);
        if (value < 0 || value > 15) throw new IllegalArgumentException("Бит должен быть от 0 до 15");
        return value;
    }

    public static int[] masks(int bit, boolean pressed) {
        if (bit < 0 || bit > 15) throw new IllegalArgumentException("Бит должен быть от 0 до 15");
        int mask = 1 << bit;
        return pressed ? new int[]{0xffff, mask} : new int[]{0xffff ^ mask, 0};
    }
}
