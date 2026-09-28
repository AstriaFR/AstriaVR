import dev.astriavr.player.VideoBitrateMeter;

/** Focused arithmetic checks only; does not claim decoder/device compatibility coverage. */
public final class VideoBitrateChecks {
    private static void close(double expected, double actual) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .01)
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void missing(double actual) {
        if (!Double.isNaN(actual)) throw new AssertionError("Expected no sample, got " + actual);
    }
    public static void main(String[] args) {
        VideoBitrateMeter meter = new VideoBitrateMeter();
        meter.addSample(0, 12_500); meter.addSample(40_000, 12_500);
        missing(meter.takeBitsPerSecond()); // Hidden bars do not collect.
        meter.setEnabled(true);
        for (int i = 0; i < 25; i++) meter.addSample(i * 40_000L, 12_500);
        close(2_500_000, meter.takeBitsPerSecond());
        missing(meter.takeBitsPerSecond()); // Each refresh consumes its window.
        for (int i = 49; i >= 0; i--) meter.addSample(i * 40_000L, 12_500);
        close(2_500_000, meter.takeBitsPerSecond()); // Reordered PTS / 2x window.
        for (int i = 0; i < 25; i++) meter.addSample(i * 40_000L, i % 2 == 0 ? 25_000 : 12_500);
        close(3_800_000, meter.takeBitsPerSecond()); // Variable packet sizes.
        meter.addSample(0, 100); meter.reset();
        missing(meter.takeBitsPerSecond()); // Seek reset.
        meter.addSample(0, 100); meter.setEnabled(false); meter.setEnabled(true);
        meter.addSample(5_000_000, 100);
        missing(meter.takeBitsPerSecond()); // Re-show never mixes hidden samples.
        System.out.println("Video bitrate arithmetic checks passed.");
    }
}
