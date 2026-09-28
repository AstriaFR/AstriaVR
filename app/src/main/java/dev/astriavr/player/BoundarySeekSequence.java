package dev.astriavr.player;

/** A fresh screen double tap or pair of button presses changes files after the edge idle interval. */
public final class BoundarySeekSequence {
    public static final long IDLE_MS = 600;
    public static final long PAIR_MS = 750;
    public static final int SEEK = 0, WAIT = 1, SWITCH = 2;
    private int edge;
    private long reachedMs = -1, lastSeekMs = -1;
    private boolean first, suppressing;

    public void reset() {
        edge = 0;
        reachedMs = lastSeekMs = -1;
        first = suppressing = false;
    }

    public void cancel(long now) {
        first = false;
        lastSeekMs = now;
    }

    public void reached(int direction, long now) {
        if (edge == direction) return;
        edge = direction;
        reachedMs = now;
        first = false;
    }

    /** Ignore the tail of the old gesture after opening the adjacent video. */
    public void suppressUntilIdle(long now) {
        cancel(now);
        suppressing = true;
    }

    public int seek(int direction, long position, long duration, boolean ended, boolean paused,
                    boolean freshPress, long now, long screenDoubleStartedMs) {
        int sign = Integer.signum(direction);
        if (sign == 0) return WAIT;
        long gap = lastSeekMs < 0 ? Long.MAX_VALUE : now - lastSeekMs;
        if (suppressing) {
            long startGap = screenDoubleStartedMs >= 0 && lastSeekMs >= 0 ? screenDoubleStartedMs - lastSeekMs : gap;
            if (startGap < IDLE_MS || !freshPress) {
                lastSeekMs = now;
                return WAIT;
            }
            suppressing = false;
        }
        boolean atEdge = sign > 0 ? ended || duration > 0 && position >= duration
            : position == 0 && paused;
        if (!atEdge) {
            edge = 0;
            reachedMs = -1;
            first = false;
            lastSeekMs = now;
            return SEEK;
        }
        reached(sign, now);
        if (screenDoubleStartedMs >= 0) {
            boolean quietStart = screenDoubleStartedMs <= now && screenDoubleStartedMs - reachedMs >= IDLE_MS
                && (lastSeekMs < 0 || screenDoubleStartedMs - lastSeekMs >= IDLE_MS);
            first = false;
            lastSeekMs = now;
            return freshPress && quietStart ? SWITCH : WAIT;
        }
        boolean quiet = now - reachedMs >= IDLE_MS && gap >= IDLE_MS;
        boolean change = freshPress && first && gap >= 0 && gap < PAIR_MS;
        first = !change && freshPress && quiet;
        lastSeekMs = now;
        return change ? SWITCH : WAIT;
    }
}
