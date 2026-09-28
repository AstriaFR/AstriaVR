import dev.astriavr.player.BoundarySeekSequence;
import dev.astriavr.player.PlaybackTapSequence;
import dev.astriavr.player.PlaybackPosition;
import static dev.astriavr.player.BoundarySeekSequence.*;

/** Edge timing, actual screen gestures and strict paused-zero selection. */
public final class BoundarySeekChecks {
    private static int checks;
    private static void expect(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static int forward(BoundarySeekSequence s, long now) {
        return s.seek(1, 60_000, 60_000, true, false, true, now, -1);
    }
    private static int screen(BoundarySeekSequence s, int direction, long position, boolean paused, long start, long now) {
        return s.seek(direction, position, 60_000, direction > 0 && position == 60_000, paused, true, now, start);
    }
    public static void main(String[] args) {
        BoundarySeekSequence s = new BoundarySeekSequence();
        s.reached(1, 1000);
        expect(forward(s, 1600) == WAIT, "button first press at 600ms");
        expect(forward(s, 1750) == SWITCH, "button double still switches");
        s.reset(); s.reached(1, 1000);
        expect(screen(s, 1, 60_000, false, 1600, 1780) == SWITCH, "screen double switches without third tap");
        s.reset(); s.reached(1, 1000);
        expect(screen(s, 1, 60_000, false, 1599, 1780) == WAIT, "idle gap is measured before first tap, not second");
        for (int t = 1900; t <= 2400; t += 100)
            expect(forward(s, t) == WAIT, "ongoing burst cannot skip");
        expect(screen(s, 1, 60_000, false, 3000, 3190) == SWITCH, "new double after quiet switches");

        s.reset(); s.reached(1, 0);
        expect(forward(s, 800) == WAIT, "first button");
        expect(forward(s, 1550) == WAIT, "button pair expires at 750ms");
        expect(forward(s, 1700) == SWITCH, "fresh button pair");
        s.reset(); s.reached(1, 0); forward(s, 800);
        expect(s.seek(1, 60_000, 60_000, true, false, false, 1050, -1) == WAIT, "held controller repeat cannot switch");
        expect(forward(s, 1200) == WAIT, "repeat cancelled first button");
        s.reset(); s.reached(1, 0); forward(s, 800);
        expect(s.seek(-1, 60_000, 60_000, true, false, true, 900, -1) == SEEK, "reverse away from end seeks");

        for (long position : new long[] {1, 500, 1000, 9999, 10_000}) {
            for (boolean paused : new boolean[] {false, true}) {
                s.reset(); s.reached(-1, 0);
                expect(screen(s, -1, position, paused, 1000, 1180) == SEEK, "nonzero 0-10s always rewinds, even with old start latch");
                expect(PlaybackPosition.seek(position, -10_000, 60_000) == 0, "rewind target is exactly zero");
            }
        }
        s.reset(); s.reached(-1, 0);
        expect(screen(s, -1, 0, false, 1000, 1180) == SEEK, "zero while playing must pause, cannot select previous");
        s.reached(-1, 1180);
        expect(screen(s, -1, 0, true, 1779, 1940) == WAIT, "a double that starts too soon after zero cannot select previous");
        expect(screen(s, -1, 0, true, 2540, 2710) == SWITCH, "paused zero fresh double selects previous");

        s.reset(); s.suppressUntilIdle(2000); s.reached(-1, 2000);
        expect(screen(s, -1, 0, true, 2500, 2700) == WAIT, "old transition gesture is suppressed by start time");
        expect(screen(s, -1, 0, true, 3300, 3480) == SWITCH, "next fresh gesture after transition works");
        s.reset(); s.reached(1, 0); forward(s, 800); s.cancel(900);
        expect(forward(s, 1000) == WAIT, "other interaction cancels button pair");

        for (int direction : new int[] {-1, 1}) {
            s.reset(); s.reached(direction, 0);
            PlaybackTapSequence taps = new PlaybackTapSequence(PlaybackTapSequence.DOUBLE_TAP_MS, 80);
            int switches = 0;
            float x = direction < 0 ? 100 : 900;
            for (long t : new long[] {800, 950}) {
                taps.down(t, x, 100, 1000);
                PlaybackTapSequence.Action action = taps.up(t + 30);
                if (action == PlaybackTapSequence.Action.BACK || action == PlaybackTapSequence.Action.FORWARD) {
                    int result = screen(s, direction, direction < 0 ? 0 : 60_000, direction < 0, taps.seekDoubleStartedAt(), t + 30);
                    if (result == SWITCH) switches++;
                }
            }
            expect(switches == 1, "exactly two screen taps select one adjacent video");
            taps.down(1100, x, 100, 1000); taps.up(1130);
            expect(taps.seekDoubleStartedAt() == -1, "third tap is not another completed double");
            taps.down(1730, x, 100, 1000);
            expect(taps.up(1760) == PlaybackTapSequence.Action.NONE, "600ms silence starts a new gesture");
            taps.down(1850, x, 100, 1000); taps.up(1880);
            expect(taps.seekDoubleStartedAt() == 1730, "new double retains its first down timestamp");
        }
        System.out.println("Boundary seek checks passed: " + checks);
    }
}