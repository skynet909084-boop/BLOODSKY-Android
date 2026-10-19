package com.bloodsky.hmi;

/** Source-backed Craft ID17 register-to-image selection. */
public final class CraftMultiStateImage {
    private static final int STATE_COUNT = 16;

    private CraftMultiStateImage() {}

    /**
     * Craft typeCommand 0 uses the register as a zero-based image number. A nonzero value
     * treats it as a bit mask and selects the first set bit (lowest image index). Craft hides
     * the image when the bit mask has no set bits or a numeric value is outside 0..15.
     *
     * @return image index 0..15, or -1 when the source component is hidden
     */
    public static int selectIndex(int typeCommand, int registerValue) {
        if (typeCommand == 0) {
            return registerValue >= 0 && registerValue < STATE_COUNT ? registerValue : -1;
        }
        for (int index = 0; index < STATE_COUNT; index++) {
            if (((registerValue >> index) & 1) == 1) return index;
        }
        return -1;
    }
}
