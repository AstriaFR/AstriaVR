import dev.astriavr.player.CoverProjection;
import dev.astriavr.player.Optics;
import dev.astriavr.player.VideoProjection;

/** Synthetic lens images catch eye mixing and accidental equirectangular cover sampling. */
public final class FisheyeChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static int[] lens(int size, int eye) {
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            double dx = (x + .5 - size / 2.0) / (size / 2.0);
            double dy = (y + .5 - size / 2.0) / (size / 2.0);
            // Separate eye color, asymmetric landmarks, black outside the physical lens circle.
            pixels[y * size + x] = dx * dx + dy * dy > 1 ? 0xff000000
                : 0xff000000 | (eye == 0 ? 0xb00000 : 0x0000b0) | ((x * 127 / size) << 8)
                  | (y < size / 3 ? 0x404040 : 0);
        }
        return pixels;
    }
    public static void main(String[] args) {
        int size = 192;
        int[] left = lens(size, 0), right = lens(size, 1);
        int[] expected = CoverProjection.render(right, size, size, 2, VideoProjection.FISHEYE_180, 96, 54);
        for (int layout = 0; layout < 2; layout++) {
            int w = layout == 0 ? size * 2 : size, h = layout == 0 ? size : size * 2;
            int[] stereo = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int eye = layout == 0 ? x / size : y / size;
                stereo[y * w + x] = (eye == 0 ? left : right)[(y % size) * size + x % size];
            }
            int[] actual = CoverProjection.render(stereo, w, h, layout, VideoProjection.STEREO_FISHEYE_180, 96, 54);
            for (int i = 0; i < actual.length; i++) {
                check(actual[i] == expected[i], "stereo cover must match isolated right lens, layout " + layout + ", pixel " + i);
                check((actual[i] & 255) >= 0xb0, "front cover must stay inside right-eye circle");
            }
            for (boolean swap : new boolean[]{false, true}) for (int eye = 0; eye < 2; eye++) {
                float[] rect = Optics.eyeRect(layout, eye, swap);
                // Sample the production eye rectangle in the top-down synthetic source.
                int x = (int)((rect[0] + rect[2] * .5) * w);
                int y = (int)((1 - rect[1] - rect[3] * .5) * h);
                int sourceEye = swap ? 1 - eye : eye;
                int color = stereo[y * w + x];
                check(sourceEye == 0 ? (color >> 16 & 255) >= 0xb0 : (color & 255) >= 0xb0,
                    "headset and phone eye selection must sample requested lens");
            }
        }
        // At 45 degrees the equidistant lens radius is exactly halfway from center to rim.
        // Draw a narrow vertical marker at that radius; the right edge of a 90-degree cover sees it.
        int[] marked = new int[size * size];
        java.util.Arrays.fill(marked, 0xff000000);
        for (int y = 0; y < size; y++) for (int x = 140; x < 148; x++) marked[y * size + x] = 0xffffffff;
        int[] view = CoverProjection.render(marked, size, size, 2, VideoProjection.FISHEYE_180, 96, 54);
        check((view[27 * 96 + 95] & 255) > 240, "45-degree landmark is at the 90-degree viewport edge");
        check(view[27 * 96 + 48] == 0xff000000, "off-axis landmark must not move to center");
        System.out.println("PASS: " + checks + " fisheye separation / cover / landmark checks");
    }
}
