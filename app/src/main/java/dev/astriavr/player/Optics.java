package dev.astriavr.player;

/** Physical display geometry, independent of Android's logical density (560 dpi). */
public final class Optics {
    public static final double DIAGONAL_CM = 6.8 * 2.54;
    public static final double WIDTH_CM = DIAGONAL_CM * 20.0 / Math.sqrt(481.0);
    public static final double HEIGHT_CM = WIDTH_CM * 9.0 / 20.0;
    public static final double EYE_DIAMETER_CM = 5.0;
    public static final float FOV_DEGREES = 88f;
    public static final float TAN_HALF_FOV = (float) Math.tan(Math.toRadians(FOV_DEGREES / 2.0));

    public static int clampFov(int degrees) {
        return Math.max(40, Math.min(120, 40 + Math.round((Math.max(40, Math.min(120, degrees)) - 40) / 8f) * 8));
    }
    public static int stepFov(int degrees, int steps) { return clampFov(clampFov(degrees) + steps * 8); }
    public static float tanHalfFov(int degrees) {
        return (float) Math.tan(Math.toRadians(clampFov(degrees) / 2.0));
    }

    public static int clampPhoneFov(int degrees) {
        return Math.max(60, Math.min(130, 60 + Math.round((degrees - 60) / 10f) * 10));
    }

    public static int stepPhoneFov(int degrees, int steps) {
        return clampPhoneFov(clampPhoneFov(degrees) + steps * 10);
    }

    /** Centered 16:9 by default; full screen keeps the actual window aspect. */
    public static final class PhoneViewport {
        public final int x, y, width, height;
        public PhoneViewport(int screenWidth, int screenHeight, boolean fill) {
            int w = Math.max(0, screenWidth), h = Math.max(0, screenHeight);
            if (fill) { width = w; height = h; }
            else if (w * 9L > h * 16L) { height = h; width = (int) (h * 16L / 9); }
            else { width = w; height = (int) (w * 9L / 16); }
            x = (w - width) / 2; y = (h - height) / 2;
        }
    }

    /** Rectilinear camera at z=-1; horizontal FOV is independent of headset optics. */
    public static float[] phonePlane(int width, int height, int horizontalFov) {
        float x = (float) Math.tan(Math.toRadians(clampPhoneFov(horizontalFov) / 2.0));
        float aspect = width > 0 && height > 0 ? width / (float) height : 1f;
        return new float[]{x, x / aspect};
    }

    /** Angular drag distance through the same perspective camera used by the shader. */
    public static float phoneDragAngle(float from, float to, int size, float plane) {
        if (size <= 0) return 0;
        return (float) Math.toDegrees(Math.atan((2 * from / size - 1) * plane)
            - Math.atan((2 * to / size - 1) * plane));
    }

    /** General Panini with hard vertical compression (w=1), inverse sampled from the panorama.
     * Formula: https://arxiv.org/html/1704.07528v2, Eq. 2.
     * Returns horizontal/vertical plane extent and d. The adaptive schedule is our geometric
     * heuristic, not the paper's content-aware optimization. All settings preserve horizontal FOV.
     */
    public static float[] phoneWarp(int width, int height, int horizontalFov, int percent) {
        return phoneWarp(width, height, horizontalFov, percent, false);
    }

    public static float[] phoneWarp(int width, int height, int horizontalFov, int percent, boolean ellipse) {
        return phoneWarp(width, height, (float) clampPhoneFov(horizontalFov), percent, ellipse);
    }

    public static float[] phoneWarp(int width, int height, float horizontalFov, int percent, boolean ellipse) {
        float fov = Float.isFinite(horizontalFov) ? Math.max(60, Math.min(130, horizontalFov)) : 90;
        double aspect = width > 0 && height > 0 ? width / (double) height : 1.0;
        double half = Math.toRadians(fov / 2.0);
        double diagonal = Math.toDegrees(2 * Math.atan(Math.tan(half) * Math.hypot(1, 1 / aspect)));
        // Fade in as the rectangular view's corner angle grows, rather than imposing a fixed warp.
        double t = Math.max(0, Math.min(1, (diagonal - 90) / 50));
        double c = Math.cos(half);
        // At full strength, balance horizontal/vertical local scale at the horizontal edge.
        // Solves (1+c)d^2 + c*d - c = 0 for the hard-compressed Panini Jacobian.
        double balancedD = (Math.sqrt(c * c + 4 * c * (1 + c)) - c) / (2 * (1 + c));
        // New 70% matches the previous 100%; extend the dynamic compression range above it.
        double d = balancedD * t * t * (3 - 2 * t) * Math.max(0, Math.min(100, percent)) / 70.0;
        // Ellipse is now a plain perspective display boundary, without any combined warp.
        if (ellipse) d = 0;
        double extent = (d + 1) * Math.sin(half) / (d + c);
        return new float[]{(float) extent, (float) (extent / aspect), (float) d,
            ellipse ? 1f : 0f};
    }

    /** Inverse camera ray for touch coordinates. Matches the phone branch of the fragment shader. */
    public static float[] phoneRayAngles(float x, float y, int width, int height, int fov, int percent) {
        return phoneRayAngles(x, y, width, height, fov, percent, false);
    }

    public static boolean insidePhoneEllipse(float x, float y, int width, int height) {
        if (width <= 0 || height <= 0) return false;
        double nx = 2.0 * x / width - 1, ny = 1 - 2.0 * y / height;
        // Fourth-power superellipse expands the corners compared with the old true ellipse.
        return nx * nx * nx * nx + ny * ny * ny * ny <= 1;
    }

    public static float[] phoneRayAngles(float x, float y, int width, int height, int fov, int percent, boolean ellipse) {
        return phoneRayAngles(x, y, width, height, (float) clampPhoneFov(fov), percent, ellipse);
    }

    public static float[] phoneRayAngles(float x, float y, int width, int height, float fov, int percent, boolean ellipse) {
        if (width <= 0 || height <= 0) return new float[]{0f, 0f};
        float[] warp = phoneWarp(width, height, fov, percent, ellipse);
        double nx = 2.0 * x / width - 1.0, ny = 1.0 - 2.0 * y / height;
        double px = nx * warp[0], py = ny * warp[1];
        double d = warp[2], q = px / (d + 1), q2 = q * q;
        double cosYaw = (Math.sqrt(1 + q2 * (1 - d * d)) - d * q2) / (1 + q2);
        double sinYaw = q * (d + cosYaw);
        double rx = sinYaw, ry = py * cosYaw, rz = -cosYaw;
        double length = Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= length; ry /= length; rz /= length;
        return new float[]{(float) Math.toDegrees(Math.atan2(rx, -rz)),
            (float) Math.toDegrees(Math.atan2(ry, Math.hypot(rx, rz)))};
    }

    /** Blend rectilinear and equidistant rays, preserving the center and full angular diameter.
     * This reduces wide-FOV projection stretch; it is not a measured headset lens profile. */
    public static float[] projection(int degrees, int correctionPercent) {
        return projection((float) clampFov(degrees), correctionPercent);
    }

    public static float[] projection(float degrees, int correctionPercent) {
        float angle = Float.isFinite(degrees) ? Math.max(40, Math.min(120, degrees)) : 88;
        return new float[]{(float) Math.tan(Math.toRadians(angle / 2.0)), (float) Math.toRadians(angle / 2.0),
            // 100% on the new scale equals 60% on the original scale.
            Math.max(0, Math.min(100, correctionPercent)) / 100f * .6f};
    }

    public static double rayAngle(float radius, int degrees, int correctionPercent) {
        float[] p = projection(degrees, correctionPercent);
        return Math.atan(radius * p[0]) * (1 - p[2]) + radius * p[1] * p[2];
    }

    public static float clampIpd(float centimeters) {
        return Float.isFinite(centimeters) ? Math.max(5f, Math.min(8f, centimeters)) : 6.5f;
    }

    public static float stepIpd(float centimeters, int steps) {
        return clampIpd((Math.round(clampIpd(centimeters) * 10f) + steps) / 10f);
    }

    public static float diameter(float value) {
        if (!Float.isFinite(value)) return 5f;
        return Math.round(Math.max(4f, Math.min(7f, value)) * 5f) / 5f;
    }
    /** Largest 0.2 cm step that fits both eyes, including the 5 cm minimum IPD. */
    public static float maxDiameter(double screenWidthCm, double screenHeightCm) {
        for (int step = 35; step >= 20; step--) {
            float d = step / 5f;
            if (fits(d, screenWidthCm, screenHeightCm)) return d;
        }
        return 0f;
    }
    public static float fitDiameter(float value, double screenWidthCm, double screenHeightCm) {
        float maximum = maxDiameter(screenWidthCm, screenHeightCm);
        if (maximum == 0f) throw new IllegalArgumentException("Screen cannot fit 4 cm eye circles");
        return Math.min(diameter(value), maximum);
    }
    public static float minIpd(float diameter) { return Math.max(5f, diameter); }
    public static float maxIpd(float diameter, double screenWidthCm) {
        return (float) (Math.floor(Math.min(8, screenWidthCm - diameter) * 10 + .00001) / 10);
    }
    public static boolean fits(float diameter, double screenWidthCm, double screenHeightCm) {
        return Double.isFinite(screenWidthCm) && Double.isFinite(screenHeightCm)
            && screenHeightCm + .000001 >= diameter && maxIpd(diameter, screenWidthCm) >= minIpd(diameter);
    }
    public static float stepIpd(float value, int steps, float diameter, double screenWidthCm) {
        float lower = minIpd(diameter), upper = maxIpd(diameter, screenWidthCm);
        float current = Float.isFinite(value) ? value : 6.5f;
        current = Math.max(lower, Math.min(upper, current));
        return Math.max(lower, Math.min(upper, (Math.round(current * 10) + steps) / 10f));
    }

    /** Physical density, never logical Android densityDpi. Null means unusable report. */
    public static double[] detectedScreen(int widthPx, int heightPx, float xdpi, float ydpi) {
        if (widthPx <= 0 || heightPx <= 0 || !Float.isFinite(xdpi) || !Float.isFinite(ydpi)
            || xdpi < 80 || ydpi < 80 || xdpi > 1500 || ydpi > 1500) return null;
        double x = widthPx / (double) xdpi * 2.54, y = heightPx / (double) ydpi * 2.54;
        double diagonal = Math.hypot(x, y) / 2.54;
        if (diagonal < 3 || diagonal > 20) return null;
        return new double[]{Math.max(x, y), Math.min(x, y)};
    }

    public static double parseScreenCm(String text) {
        if (text == null || !text.trim().matches("[0-9]{1,2}(\\.[0-9]{1,2})?")) return Double.NaN;
        double value = Double.parseDouble(text.trim());
        return value > 0 && value <= 50 ? value : Double.NaN;
    }

    public static final class Layout {
        public final int eyeWidth, eyeHeight, leftX, rightX, y;
        public final float centerDistance;
        public Layout(int width, int height, float ipdCm) {
            this(width, height, ipdCm, 5f, WIDTH_CM, HEIGHT_CM);
        }
        public Layout(int width, int height, float ipdCm, float diameter, double screenWidthCm, double screenHeightCm) {
            double pxPerCmX = width / screenWidthCm;
            double pxPerCmY = height / screenHeightCm;
            eyeWidth = (int) Math.min(width / 2, Math.round(diameter * pxPerCmX));
            eyeHeight = (int) Math.min(height, Math.round(diameter * pxPerCmY));
            centerDistance = (float) (stepIpd(ipdCm, 0, diameter, screenWidthCm) * pxPerCmX);
            // Select one centered pair of viewports; avoid independent rounding overlap at minimum IPD.
            int distancePx = Math.max(eyeWidth, Math.min(width - eyeWidth, Math.round(centerDistance)));
            leftX = Math.round((width - distancePx - eyeWidth) / 2f);
            rightX = leftX + distancePx;
            y = (height - eyeHeight) / 2;
        }
    }

    /** Logical UV is bottom-left origin. Layout: 0=SBS, 1=TB, 2=mono. */
    public static float[] eyeRect(int layout, int eye, boolean swap) {
        int sourceEye = swap ? 1 - eye : eye;
        if (layout == 0) return new float[]{sourceEye * .5f, 0f, .5f, 1f};
        if (layout == 1) return new float[]{0f, sourceEye == 0 ? .5f : 0f, 1f, .5f};
        return new float[]{0f, 0f, 1f, 1f};
    }
}
