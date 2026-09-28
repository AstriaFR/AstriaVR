package dev.astriavr.player;

public final class PoseMath {
    private PoseMath() {}
    public static void identity(float[] matrix) {
        java.util.Arrays.fill(matrix, 0f);
        matrix[0] = matrix[4] = matrix[8] = 1f;
    }
    /** Inputs: sensor row-major screen-to-world. Output: GL column-major camera-to-recentered-world. */
    public static void relative(float[] reference, float[] current, float[] result) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                result[col * 3 + row] = reference[row] * current[col]
                    + reference[3 + row] * current[3 + col]
                    + reference[6 + row] * current[6 + col];
            }
        }
    }
}
