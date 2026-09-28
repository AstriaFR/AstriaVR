package dev.astriavr.player;

/** Small, bounded accumulator. No file reads, frame copies or per-frame allocations. */
public final class VideoBitrateMeter {
    private volatile boolean enabled;
    private long bytes, firstUs, lastUs;
    private int samples;

    public synchronized void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        reset();
    }

    public void addSample(long timeUs, int size) {
        if (!enabled || size <= 0) return;
        synchronized (this) {
            if (!enabled) return;
            if (samples == 0) firstUs = lastUs = timeUs;
            else { firstUs = Math.min(firstUs, timeUs); lastUs = Math.max(lastUs, timeUs); }
            bytes += size;
            samples++;
        }
    }

    /** Mean compressed video bits per media second over the latest UI sampling interval.
     * Presentation timestamps make this independent of playback speed and tolerate B-frame order.
     * The final frame duration is estimated from the other frames in the interval.
     */
    public synchronized double takeBitsPerSecond() {
        double spanUs = (double) lastUs - firstUs;
        double rate = enabled && samples >= 2 && spanUs > 0
                ? bytes * 8_000_000.0 / (spanUs * samples / (samples - 1.0)) : Double.NaN;
        reset();
        return rate;
    }

    public synchronized void reset() { bytes = 0; samples = 0; firstUs = lastUs = 0; }
}
