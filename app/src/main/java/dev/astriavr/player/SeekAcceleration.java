package dev.astriavr.player;

/** Shared by touch/buttons/gamepad: 1–6 = 10s, 7–12 = 20s, 13+ = 30s; idle/reversal resets. */
public final class SeekAcceleration {
    private int direction, count;
    private long previousMs;
    public long next(int requestedDirection, long nowMs) {
        int sign = Integer.signum(requestedDirection);
        if (sign == 0) return 0;
        if (sign != direction || nowMs < previousMs || nowMs - previousMs >= 1000) count = 0;
        direction = sign;
        previousMs = nowMs;
        count = Math.min(13, count + 1);
        return sign * (count <= 6 ? 10_000L : count <= 12 ? 20_000L : 30_000L);
    }
    public void reset() { direction = count = 0; previousMs = 0; }
}
