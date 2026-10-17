package com.bloodsky.hmi;

/** Keep sampled project images within 2048px on either edge before allocating pixels. */
public final class ProjectImageSizing {
    private ProjectImageSizing() {}
    public static int sampleSize(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Недопустимое изображение");
        int sample=1;
        while (((long)width+sample-1)/sample>2048 || ((long)height+sample-1)/sample>2048) sample*=2;
        return sample;
    }
}
