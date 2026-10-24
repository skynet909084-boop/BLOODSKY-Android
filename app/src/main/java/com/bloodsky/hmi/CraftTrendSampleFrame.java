package com.bloodsky.hmi;

/**
 * Validates one complete Craft ID14 trend-poll frame before touching channel histories.
 *
 * <p>The Modbus poller encodes exactly one numeric value per configured channel,
 * separated by '|'. A partial or extra-channel frame must not silently change the
 * channel count: doing so would discard the existing rolling history on the next
 * successful poll.</p>
 */
public final class CraftTrendSampleFrame {
    private CraftTrendSampleFrame() {}

    /**
     * @return one slot per configured channel (NaN marks an unavailable channel),
     *         or null if the whole frame is missing, malformed, or unavailable.
     */
    public static double[] decode(String raw, int channelCount) {
        if (channelCount < 1 || channelCount > 4)
            throw new IllegalArgumentException("Craft trend needs 1..4 channels");
        if (raw == null || raw.startsWith("ERR") || raw.startsWith("CFG")) return null;

        String[] fields = raw.split("\\|", -1);
        if (fields.length != channelCount) return null;

        double[] values = new double[channelCount];
        boolean hasValidSample = false;
        for (int i = 0; i < channelCount; i++) {
            double value;
            try {
                value = Double.parseDouble(fields[i].trim());
            } catch (NumberFormatException malformed) {
                value = Double.NaN;
            }
            if (Double.isFinite(value)) {
                values[i] = value;
                hasValidSample = true;
            } else {
                values[i] = Double.NaN;
            }
        }
        return hasValidSample ? values : null;
    }
}
