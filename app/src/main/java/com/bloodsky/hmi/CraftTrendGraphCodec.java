package com.bloodsky.hmi;

/** Converts Craft ID14's source-confirmed byte formats from Modbus registers. */
public final class CraftTrendGraphCodec {
    private CraftTrendGraphCodec() {}

    public static int readQuantity(int graphType) {
        int type = Math.max(0, Math.min(2, graphType));
        return type == 2 ? 2 : 1;
    }

    /** Decodes the first configured series. Craft's ID14 uses unsigned, signed, or big-endian float. */
    public static double decodeFirst(int[] registers, int graphType) {
        if (registers == null) throw new IllegalArgumentException("Нет регистров графика");
        int type = Math.max(0, Math.min(2, graphType));
        int quantity = readQuantity(type);
        if (registers.length < quantity) throw new IllegalArgumentException("Недостаточно регистров графика");
        int first = registers[0] & 0xffff;
        if (type == 0) return first;
        if (type == 1) return (short) first;
        int bits = (first << 16) | (registers[1] & 0xffff);
        float value = Float.intBitsToFloat(bits);
        return Float.isFinite(value) ? value : Double.NaN;
    }
}
