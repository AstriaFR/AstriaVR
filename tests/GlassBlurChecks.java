import dev.astriavr.player.GlassBlur;
import java.util.Arrays;

public final class GlassBlurChecks {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        for (int[] size : new int[][] {{1,1}, {1,9}, {9,1}, {192,108}, {108,192}}) {
            int[] solid = new int[size[0] * size[1]];
            Arrays.fill(solid, 0xff3657a2);
            GlassBlur.apply(solid, size[0], size[1], 3);
            for (int pixel : solid) check(pixel == 0xff3657a2, "Tint changed or edge darkened");
        }
        int[] impulse = new int[31 * 31];
        Arrays.fill(impulse, 0xff000000);
        impulse[15 * 31 + 15] = 0xffff0000;
        GlassBlur.apply(impulse, 31, 31, 3);
        check((impulse[15 * 31 + 15] & 0x00ff0000) > 0, "Lost highlight");
        check(impulse[15 * 31 + 15] != 0xffff0000, "Highlight did not scatter");
        check((impulse[15 * 31 + 16] & 0x00ff0000) > 0, "No diffusion");
        check(impulse[0] == 0xff000000, "Opposite edges contaminated");
        for (int pixel : impulse) check((pixel & 0xff00ffff) == 0xff000000, "Color channel leakage");
        System.out.println("PASS: glass blur solid fields, degenerate sizes, diffusion and color isolation");
    }
}
