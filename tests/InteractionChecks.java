import dev.astriavr.player.PlaybackTapSequence;
import dev.astriavr.player.PlaylistDragOrder;
import dev.astriavr.player.FlatVideoGeometry;
import dev.astriavr.player.SeekAcceleration;
import java.util.ArrayList;
import static dev.astriavr.player.PlaybackTapSequence.Action.*;

/** Offline behavioral checks. Does not connect to a device or read application data. */
public final class InteractionChecks {
    private static int assertions;
    private static void expect(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static PlaybackTapSequence taps() { return new PlaybackTapSequence(PlaybackTapSequence.DOUBLE_TAP_MS, 80); }
    private static PlaybackTapSequence.Action tap(PlaybackTapSequence s, long t, float x) {
        expect(s.down(t, x, 100, 1000) == NONE, "unexpected early single");
        return s.up(t + 40);
    }
    public static void main(String[] args) {
        PlaybackTapSequence s = taps();
        expect(tap(s, 1000, 500) == NONE, "single starts pending");
        expect(s.confirmSingle(1389) == NONE, "single cannot fire before full 350ms window");
        expect(s.confirmSingle(1390) == SINGLE, "single confirmed after window");
        expect(s.confirmSingle(1500) == NONE, "single fires exactly once");

        s = taps();
        tap(s, 1000, 500);
        expect(s.down(1250, 505, 101, 1000) == NONE, "second down must not toggle");
        expect(s.confirmSingle(1350) == NONE, "second finger suspends toolbar");
        expect(s.up(1400) == TOGGLE, "second up toggles exactly once");
        expect(tap(s, 1550, 500) == NONE, "center triple must not toggle again");
        expect(tap(s, 1650, 500) == NONE, "center burst cannot hide toolbar");
        expect(s.confirmSingle(2000) == NONE, "center burst leaves no delayed click");
        expect(tap(s, 2400, 500) == NONE, "new center sequence after pause");
        expect(tap(s, 2530, 500) == TOGGLE, "next distinct double toggles");

        for (int direction : new int[] {-1, 1}) {
            s = taps();
            float x = direction < 0 ? 100 : 900;
            var action = direction < 0 ? BACK : FORWARD;
            expect(tap(s, 1000, x) == NONE, "first edge tap pending");
            expect(tap(s, 1150, x) == action, "edge double seeks");
            expect(tap(s, 1500, x) == action, "third edge tap seeks once");
            expect(tap(s, 2050, x) == action, "fourth edge tap seeks once");
            expect(s.confirmSingle(2300) == NONE, "seek burst never toggles toolbar");
            expect(tap(s, 3000, x) == NONE, "pause ends seeking burst");
            expect(s.confirmSingle(3390) == SINGLE, "single returns after burst expiry");
        }

        s = taps();
        tap(s, 1000, 100); tap(s, 1150, 100);
        expect(tap(s, 1400, 900) == NONE, "changing side starts new sequence");
        expect(tap(s, 1550, 900) == FORWARD, "opposite direction needs double");
        s = taps();
        tap(s, 1000, 450);
        expect(s.down(1150, 700, 100, 1000) == NONE, "distant second down must never flash toolbar");
        expect(s.up(1190) == NONE, "distant second tap cannot toggle");
        expect(s.confirmSingle(1539) == NONE, "replacement single must wait its full window");
        expect(s.confirmSingle(1540) == SINGLE, "ambiguous pair resolves to only one delayed single");
        s.cancel();
        expect(s.confirmSingle(2000) == NONE, "drag/toolbar/navigation cancellation clears timers");
        expect(s.up(2010) == NONE, "cancelled up ignored");
        tap(s, 2500, 100);
        s.down(2650, 100, 100, 1000); s.cancel();
        expect(s.up(2800) == NONE, "second-finger drag cannot seek");
        expect(s.confirmSingle(3000) == NONE, "cancel cannot leak the first single");
        expect(PlaybackTapSequence.zone(199, 1000) == -1, "left fifth");
        expect(PlaybackTapSequence.zone(200, 1000) == 0, "left center boundary");
        expect(PlaybackTapSequence.zone(799, 1000) == 0, "right center boundary");
        expect(PlaybackTapSequence.zone(800, 1000) == 1, "right fifth");
        expect(PlaybackTapSequence.zone(0, 0) == 0, "zero-width layout safe");

        // Regressions: a second down at the boundary, even with a queued timer, cannot emit SINGLE.
        for (int offset = 0; offset <= 350; offset++) {
            s = taps(); tap(s, 10_000, 500);
            expect(s.down(10_040 + offset, 510, 100, 1000) == NONE, "second down cannot toggle bars");
            expect(s.singleDelay(10_500) == -1, "second finger removes pending single scheduling");
            expect(s.confirmSingle(10_500) == NONE, "stale timer while second finger held is harmless");
            expect(s.up(10_540 + offset) == TOGGLE, "qualified second up toggles playback");
            expect(s.confirmSingle(12_000) == NONE, "double tap leaves no toolbar action");
        }
        s = taps(); tap(s, 20_000, 199);
        expect(s.down(20_150, 201, 100, 1000) == NONE, "zone boundary cannot confirm an early single");
        expect(s.up(20_190) == NONE, "zone change restarts qualification");
        expect(s.confirmSingle(20_539) == NONE, "zone change waits from its own up");
        expect(s.confirmSingle(20_540) == SINGLE, "zone change has one eventual single");
        s = taps(); tap(s, 30_000, 500); s.down(30_100, 500, 100, 1000); s.cancel();
        expect(s.singleDelay(30_500) == -1 && s.confirmSingle(31_000) == NONE, "cancelled second touch never exposes a timer");

        for (int size = 1; size <= 60; size++) for (int from = 0; from < size; from++) for (int to = 0; to < size; to++) {
            var expected = new ArrayList<Integer>();
            for (int i = 0; i < size; i++) expected.add(i);
            Integer moved = expected.remove(from); expected.add(to, moved);
            for (int p = 0; p < size; p++)
                expect(PlaylistDragOrder.originalPosition(p, from, to) == expected.get(p), "preview must match remove/insert order");
            expect(PlaylistDragOrder.originalPosition(to, from, to) == from, "dragged item occupies its placeholder");
            expect(PlaylistDragOrder.originalPosition(to, -1, -1) == to, "cancel restores cursor positions");
        }
        for (int direction : new int[] {-1, 1}) {
            SeekAcceleration seek = new SeekAcceleration();
            for (int i = 1; i <= 100; i++) {
                long expected = direction * (i <= 6 ? 10_000L : i <= 12 ? 20_000L : 30_000L);
                expect(seek.next(direction, i * 250L) == expected, "touch and gamepad share every tier boundary");
            }
            expect(seek.next(-direction, 25_250) == -direction * 10_000L, "reversal resets tier");
            expect(seek.next(-direction, 26_250) == -direction * 10_000L, "one-second pause resets tier");
            expect(seek.next(0, 26_300) == 0, "zero direction cannot seek");
            expect(seek.next(direction, 100) == direction * 10_000L, "clock rollback resets safely");
            seek.reset();
            expect(seek.next(direction, 400) == direction * 10_000L, "stop/change-source clears burst");
        }
        for (int[] window : new int[][] {{3120,1440},{1920,1080},{1080,1920},{800,600},{1,1}}) {
            for (int[] video : new int[][] {{1920,1080},{1080,1920},{3840,1600},{1440,1080},{0,0}}) {
                for (float pixel : new float[] {1f, 4f/3f, Float.NaN, 0f}) {
                    int[] fit = FlatVideoGeometry.fit(window[0], window[1], video[0], video[1], pixel);
                    expect(fit[0] > 0 && fit[1] > 0 && fit[0] <= window[0] && fit[1] <= window[1], "native video fits inside window");
                    expect(fit[0] == window[0] || fit[1] == window[1], "native video uses available width or height");
                    double ratio = video[0] > 0 && video[1] > 0 ? (double)video[0]/video[1]*(Float.isFinite(pixel)&&pixel>0?pixel:1) : 16.0/9;
                    expect(Math.abs(fit[0]-fit[1]*ratio) <= Math.max(1,ratio), "original frame aspect is preserved to pixel rounding");
                }
            }
        }
        int[] cinema = FlatVideoGeometry.fit(3120, 1440, 1920, 1080, 1);
        expect(cinema[0] == 2560 && cinema[1] == 1440, "wide phone letterboxes regular 16:9 without VR distortion");
        FlatVideoGeometry.Transform zoom = new FlatVideoGeometry.Transform();
        zoom.gesture(960, 540, 960, 540, 100, 200, 1920, 1080, 1600, 900);
        expect(zoom.scale() == 2f && zoom.offsetX() == 0 && zoom.offsetY() == 0, "two-finger spread zooms about center");
        zoom.gesture(960, 540, 1080, 480, 100, 100, 1920, 1080, 1600, 900);
        expect(zoom.offsetX() == 120 && zoom.offsetY() == -60, "two fingers move the enlarged image");
        zoom.gesture(1080, 480, 10000, -10000, 100, 100, 1920, 1080, 1600, 900);
        expect(zoom.offsetX() == 640 && zoom.offsetY() == -360, "dragging clamps to video edges");
        zoom.reset();
        zoom.gesture(400, 400, 400, 400, 100, 200, 1920, 1080, 1600, 900);
        expect(zoom.offsetX() == 560 && zoom.offsetY() == 140, "pinch keeps off-center focus stationary");
        zoom.gesture(400, 400, 400, 400, 200, 100, 1920, 1080, 1600, 900);
        expect(zoom.scale() == 1f && zoom.offsetX() == 0 && zoom.offsetY() == 0, "zooming out restores centered fit");
        zoom.gesture(400, 400, 400, 400, 0, 100, 1920, 1080, 1600, 900);
        expect(zoom.scale() == 1f, "invalid finger spacing leaves transform unchanged");
        zoom.reset();
        zoom.gesture(960, 540, 2000, 540, 100, 200, 1920, 1080, 300, 900);
        expect(zoom.scale() == 2f && zoom.offsetX() == 660, "portrait video can move inside its horizontal letterbox");
        System.out.println("PASS: " + assertions + " interaction, seek-tier and native-video geometry checks");
    }
}
