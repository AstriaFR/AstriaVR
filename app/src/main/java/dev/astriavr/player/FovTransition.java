package dev.astriavr.player;

/** Render-only, time-based interpolation. Persisted FOV is always the discrete target. */
public final class FovTransition {
    public static final long DURATION_NS = 220_000_000L;
    private boolean initialized;
    private float from, target, tangent;
    private long start;

    public boolean isRunning(long now) {
        return initialized && from != target && now - start < DURATION_NS;
    }

    public float update(float next, long now, boolean snap) {
        if (!Float.isFinite(next)) next = initialized ? target : 88f;
        if (!initialized || snap) {
            initialized = true; from = target = next; tangent = 0; start = now;
            return next;
        }
        double t = Math.max(0, Math.min(1, (now - start) / (double) DURATION_NS));
        float current = value(t);
        if (next != target) {
            float velocity = t >= 1 ? 0 : (float) ((6*t*t-6*t)*from
                + (3*t*t-4*t+1)*tangent + (-6*t*t+6*t)*target);
            float delta = next - current;
            // Keep velocity on same-direction presses, but never overshoot a new target.
            tangent = delta == 0 || velocity * delta <= 0 ? 0
                : Math.copySign(Math.min(Math.abs(velocity), 3 * Math.abs(delta)), delta);
            from = current; target = next; start = now;
            return current;
        }
        return current;
    }

    private float value(double t) {
        if (t >= 1) return target;
        return (float) ((2*t*t*t-3*t*t+1)*from + (t*t*t-2*t*t+t)*tangent
            + (-2*t*t*t+3*t*t)*target);
    }
}
