package com.bloodsky.hmi;

import android.graphics.Movie;

/** Bounded decoder for imported Craft ID 16 GIF media. */
public final class CraftGifAsset {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_DIMENSION = 2048;

    private CraftGifAsset() { }

    public static boolean hasGifHeader(byte[] bytes) {
        if (bytes == null || bytes.length < 6) return false;
        return bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
    }

    public static Movie decode(byte[] bytes) {
        if (!hasGifHeader(bytes) || bytes.length > MAX_BYTES) return null;
        try {
            Movie movie = Movie.decodeByteArray(bytes, 0, bytes.length);
            if (movie == null || movie.width() < 1 || movie.height() < 1
                    || movie.width() > MAX_DIMENSION || movie.height() > MAX_DIMENSION) return null;
            return movie;
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
