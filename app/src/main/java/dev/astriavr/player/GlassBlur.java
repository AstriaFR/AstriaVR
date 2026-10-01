package dev.astriavr.player;

/** Small opaque preview blur. Linear work per pixel, independent of the display resolution. */
public final class GlassBlur {
    private GlassBlur() {}

    public static void apply(int[] pixels, int width, int height, int radius) {
        if (width <= 0 || height <= 0 || (long) width * height != pixels.length)
            throw new IllegalArgumentException("Invalid preview dimensions");
        if (radius <= 0) return;
        radius = Math.min(radius, Math.max(width, height));
        int[] temp = new int[pixels.length];
        // Two separable box passes give a soft falloff without RenderScript or new Android APIs.
        for (int pass = 0; pass < 2; pass++) {
            sweep(pixels, temp, width, height, radius, false);
            sweep(temp, pixels, width, height, radius, true);
        }
    }

    private static void sweep(int[] src, int[] dst, int width, int height, int radius, boolean vertical) {
        int lines = vertical ? width : height;
        int length = vertical ? height : width;
        int step = vertical ? width : 1;
        int count = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int start = vertical ? line : line * width;
            int red = 0, green = 0, blue = 0;
            for (int k = -radius; k <= radius; k++) {
                int p = src[start + Math.max(0, Math.min(length - 1, k)) * step];
                red += (p >>> 16) & 255; green += (p >>> 8) & 255; blue += p & 255;
            }
            for (int at = 0; at < length; at++) {
                dst[start + at * step] = 0xff000000 | (red / count << 16) | (green / count << 8) | blue / count;
                int remove = src[start + Math.max(0, at - radius) * step];
                int add = src[start + Math.min(length - 1, at + radius + 1) * step];
                red += ((add >>> 16) & 255) - ((remove >>> 16) & 255);
                green += ((add >>> 8) & 255) - ((remove >>> 8) & 255);
                blue += (add & 255) - (remove & 255);
            }
        }
    }
}
