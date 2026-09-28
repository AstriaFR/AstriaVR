package dev.astriavr.player;

/** Completed taps only: a second down suspends the single action, but never seeks by itself. */
public final class PlaybackTapSequence {
    public enum Action { NONE, SINGLE, TOGGLE, BACK, FORWARD }
    private final long doubleTimeout;
    private final float doubleSlopSquared;
    private int zone, count;
    private long lastUp, singleDeadline, startedAt;
    private float lastX, lastY, downX, downY;
    private int downZone;
    private boolean pendingSingle, touching, continuing;
    public static final long SEEK_CONTINUATION_MS = 600;
    public static final long DOUBLE_TAP_MS = 350;

    public PlaybackTapSequence(long doubleTimeout, float doubleSlop) {
        this.doubleTimeout = doubleTimeout;
        doubleSlopSquared = doubleSlop * doubleSlop;
    }
    public static int zone(float x, float width) {
        if (width <= 0) return 0;
        return x < width * .2f ? -1 : x >= width * .8f ? 1 : 0;
    }
    public Action down(long now, float x, float y, float width) {
        int nextZone = zone(x, width);
        long window = count >= 2 && zone != 0 ? SEEK_CONTINUATION_MS : doubleTimeout;
        float dx = x - lastX, dy = y - lastY;
        boolean withinWindow = count >= 2 && zone != 0 ? now - lastUp < window : now - lastUp <= window;
        continuing = count > 0 && nextZone == zone && now >= lastUp && withinWindow
            && (count >= 2 || dx * dx + dy * dy <= doubleSlopSquared);
        // An ambiguous second down replaces the pending tap. Only the timer may confirm SINGLE;
        // never flash the bars on a second down just because its position crossed a region boundary.
        pendingSingle = false;
        if (!continuing) { count = 0; startedAt = now; }
        touching = true; downZone = nextZone; downX = x; downY = y;
        return Action.NONE;
    }
    public Action up(long now) {
        if (!touching) return Action.NONE;
        touching = false; zone = downZone; lastX = downX; lastY = downY; lastUp = now;
        count = continuing ? Math.min(3, count + 1) : 1;
        if (count == 1) {
            pendingSingle = true; singleDeadline = now + doubleTimeout;
            return Action.NONE;
        }
        if (zone < 0) return Action.BACK;
        if (zone > 0) return Action.FORWARD;
        // A center triple tap must not toggle twice or leave a delayed toolbar click.
        return count == 2 ? Action.TOGGLE : Action.NONE;
    }
    public Action confirmSingle(long now) {
        if (!pendingSingle || touching || now < singleDeadline) return Action.NONE;
        cancel(); return Action.SINGLE;
    }
    public long singleDelay(long now) { return pendingSingle && !touching ? Math.max(0, singleDeadline - now) : -1; }
    /** Only a completed fresh edge double tap, never the third/fourth tap in a seek burst. */
    public long seekDoubleStartedAt() { return count == 2 && zone != 0 ? startedAt : -1; }
    public void cancel() { count = 0; pendingSingle = touching = continuing = false; }
}
