package com.bloodsky.hmi;

/** Converts Craft ID14's source-confirmed byte formats from Modbus registers. */
public final class CraftTrendGraphCodec {
    private CraftTrendGraphCodec() {}

    public static int readQuantity(int graphType) {
        int type = Math.max(0, Math.min(2, graphType));
        return type == 2 ? 2 : 1;
    }

    /** Source ID14 reads one contiguous value per configured channel. */
    public static int readQuantity(int graphType, int channelCount) {
        int channels = Math.max(1, Math.min(4, channelCount));
        return readQuantity(graphType) * channels;
    }

    /** Decodes Craft's typeGraph: 0=Java short, 1=Java char, 2=big-endian Float32. */
    public static double decodeFirst(int[] registers, int graphType) {
        if (registers == null) throw new IllegalArgumentException("Нет регистров графика");
        int type = Math.max(0, Math.min(2, graphType));
        int quantity = readQuantity(type);
        if (registers.length < quantity) throw new IllegalArgumentException("Недостаточно регистров графика");
        int first = registers[0] & 0xffff;
        if (type == 0) return (short) first;
        if (type == 1) return first;
        int bits = (first << 16) | (registers[1] & 0xffff);
        float value = Float.intBitsToFloat(bits);
        return Float.isFinite(value) ? value : Double.NaN;
    }

    /** Decodes the source's contiguous byte/register layout, preserving NaN per float channel. */
    public static double[] decodeChannels(int[] registers, int graphType, int channelCount) {
        if (registers == null) throw new IllegalArgumentException("Нет регистров графика");
        int type = Math.max(0, Math.min(2, graphType));
        int channels = Math.max(1, Math.min(4, channelCount));
        int width = readQuantity(type);
        if (registers.length < width * channels)
            throw new IllegalArgumentException("Недостаточно регистров графика");
        double[] values = new double[channels];
        for (int channel = 0; channel < channels; channel++) {
            int offset = channel * width;
            if (type == 0) {
                values[channel] = (short) (registers[offset] & 0xffff);
            } else if (type == 1) {
                values[channel] = registers[offset] & 0xffff;
            } else {
                int bits = ((registers[offset] & 0xffff) << 16)
                        | (registers[offset + 1] & 0xffff);
                float value = Float.intBitsToFloat(bits);
                values[channel] = Float.isFinite(value) ? value : Double.NaN;
            }
        }
        return values;
    }
}
