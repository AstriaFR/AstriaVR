package dev.astriavr.player;

/** Position rules shared by saved sessions, history and seeking. No Android dependency. */
public final class PlaybackPosition {
    private PlaybackPosition() {}

    /** playWhenReady can remain true after ENDED; rebuilding output must not replay the file. */
    public static boolean shouldResume(boolean pending, boolean playWhenReady, boolean ended) {
        return pending || (playWhenReady && !ended);
    }

    /** null means resume a known file; an explicit zero is the user's "from start" action. */
    public static long open(Long requested, long saved, long duration) {
        return resume(requested == null ? saved : requested, duration);
    }

    public static long resume(long position, long duration) {
        if (position < 0 || (duration > 0 && position >= duration)) return 0;
        return position;
    }

    public static long seek(long position, long delta, long duration) {
        long start = Math.max(0, position);
        long limit = duration > 0 ? duration : Long.MAX_VALUE;
        if (delta >= 0) return Math.min(limit, start > Long.MAX_VALUE - delta ? Long.MAX_VALUE : start + delta);
        return Math.min(limit, delta < -start ? 0 : start + delta);
    }
}
