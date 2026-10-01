package dev.astriavr.player;

/** Fixed 90-degree front camera, right eye, top-left image coordinates. Android independent. */
public final class CoverProjection {
    private CoverProjection() {}
    public static long sampleTimeUs(long durationMs) {
        // Never ask the retriever for frame zero or an arbitrary representative frame.
        // Short clips use 20%; at exactly ten minutes both rules meet at 2:00.
        return durationMs > 0 && durationMs < 600_000
            ? Math.max(1, durationMs * 200) : 120_000_000;
    }
    public static double[] uv(double x, double y, int width, int height, int layout, int degrees) {
        double rx = (2 * x / width - 1);
        double ry = (1 - 2 * y / height) * height / width;
        double[] result = new double[2];
        project(rx, ry, layout, degrees, 1536, 1536, result);
        return result;
    }
    private static void project(double rx, double ry, int layout, int projection,
            int sourceWidth, int sourceHeight, double[] result) {
        double ew = sourceWidth * (layout == 0 ? .5 : 1);
        double eh = sourceHeight * (layout == 1 ? .5 : 1);
        double u, v;
        if (VideoProjection.isFisheye(projection)) {
            double radius = Math.hypot(rx, ry);
            double scale = Math.atan(radius) / (Math.PI * Math.max(radius, 1e-9));
            u = .5 + rx * scale * Math.min(ew, eh) / ew;
            v = .5 - ry * scale * Math.min(ew, eh) / eh;
        } else if (projection == VideoProjection.CUBEMAP) {
            u = (1 + (rx + 1) * .5) / 3;
            v = .5 + (1 - ry) * .25;
        } else if (projection == VideoProjection.EAC) {
            double px = 2 / ew, py = 2 / eh;
            u = px + (1.5 + Math.atan(rx) * 2 / Math.PI) * (1 - 2 * px) / 3;
            v = py + (.5 - Math.atan(ry) * 2 / Math.PI) * (.5 - 2 * py);
        } else {
            u = .5 + Math.atan2(rx, 1) / Math.toRadians(VideoProjection.degrees(projection));
            v = .5 - Math.atan2(ry, Math.hypot(rx, 1)) / Math.PI;
        }
        if (layout == 0) u = .5 + u * .5;
        else if (layout == 1) v = .5 + v * .5;
        result[0] = u; result[1] = v;
    }
    public static int[] render(int[] source, int sourceWidth, int sourceHeight, int layout, int degrees, int width, int height) {
        int[] pixels = new int[width * height];
        double[] uv = new double[2];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            double rx = 2 * (x + .5) / width - 1;
            double ry = (1 - 2 * (y + .5) / height) * height / width;
            project(rx, ry, layout, degrees, sourceWidth, sourceHeight, uv);
            double u = uv[0], v = uv[1];
            double sx = Math.max(0, Math.min(sourceWidth - 1, u * sourceWidth - .5));
            double sy = Math.max(0, Math.min(sourceHeight - 1, v * sourceHeight - .5));
            int ix = (int)sx, iy = (int)sy, jx = Math.min(ix + 1, sourceWidth - 1), jy = Math.min(iy + 1, sourceHeight - 1);
            int a = source[iy * sourceWidth + ix], b = source[iy * sourceWidth + jx];
            int c = source[jy * sourceWidth + ix], d = source[jy * sourceWidth + jx];
            double fx = sx - ix, fy = sy - iy;
            int rgb = 0xff000000;
            for (int shift = 0; shift <= 16; shift += 8) {
                double top = ((a >> shift) & 255) * (1-fx) + ((b >> shift) & 255) * fx;
                double bottom = ((c >> shift) & 255) * (1-fx) + ((d >> shift) & 255) * fx;
                rgb |= ((int)Math.round(top * (1-fy) + bottom * fy)) << shift;
            }
            pixels[y * width + x] = rgb;
        }
        return pixels;
    }
}
