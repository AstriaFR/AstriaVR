package dev.astriavr.player;

public final class PlaybackTuning {
    private static final float[] SPEEDS = {.2f, .5f, .8f, 1f, 1.2f, 1.5f, 2f};
    public static final int DEFAULT_SPEED = 3;
    public static int stepSpeed(int index, int direction) {
        return Math.max(0, Math.min(SPEEDS.length - 1, index + direction));
    }
    public static float speed(int index) { return SPEEDS[stepSpeed(index, 0)]; }
    public static float adjustBrightness(float value, float input, float seconds) {
        return Math.max(.05f, Math.min(1f, value + input * .08f * seconds));
    }
}
