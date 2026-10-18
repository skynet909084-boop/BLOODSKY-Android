package com.bloodsky.hmi;

import java.util.ArrayList;
import java.util.List;

/** Decodes Craft archive channel registers in register order. */
public final class CraftArchiveChannels {
    private CraftArchiveChannels() {}

    public static int readQuantity(int channelCount, String recordType) {
        int channels = Math.max(1, Math.min(4, channelCount));
        return "FLOAT".equals(recordType) ? channels * 2 : channels;
    }

    public static String decode(int[] words, int channelCount, String recordType) {
        if (words == null) throw new IllegalArgumentException("Archive words are required");
        int channels = Math.max(1, Math.min(4, channelCount));
        boolean floating = "FLOAT".equals(recordType);
        if (words.length < readQuantity(channels, recordType))
            throw new IllegalArgumentException("Not enough register words for archive channels");
        List<String> values = new ArrayList<>();
        for (int channel = 0; channel < channels; channel++) {
            if (floating) {
                int bits = ((words[channel * 2] & 0xffff) << 16) | (words[channel * 2 + 1] & 0xffff);
                values.add(Float.toString(Float.intBitsToFloat(bits)));
            } else values.add(Integer.toString(words[channel] & 0xffff));
        }
        return android.text.TextUtils.join(",", values);
    }
}
