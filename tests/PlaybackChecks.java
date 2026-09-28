import dev.astriavr.player.PlaybackPosition;
import dev.astriavr.player.FovTransition;
import dev.astriavr.player.ViewOrientation;
import java.util.Random;

public final class PlaybackChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(!PlaybackPosition.shouldResume(false, true, true), "changing mode after end must not restart playback");
        check(!PlaybackPosition.shouldResume(false, false, false), "paused video stays paused across output changes");
        check(PlaybackPosition.shouldResume(false, true, false), "playing video resumes across output changes");
        check(PlaybackPosition.shouldResume(true, false, false), "pending open request still autoplays");
        check(PlaybackPosition.shouldResume(true, true, true), "explicit pending restart overrides completed state");
        check(PlaybackPosition.open(null, 5000, 10000) == 5000, "picker reopens at saved position");
        check(PlaybackPosition.open(0L, 5000, 10000) == 0, "explicit restart overrides history");
        check(PlaybackPosition.open(2000L, 5000, 10000) == 2000, "recreated session wins over history");
        check(PlaybackPosition.open(null, 10000, 10000) == 0, "finished history restarts");
        check(PlaybackPosition.open(null, 5000, 0) == 5000, "unknown duration retains resume");
        check(PlaybackPosition.resume(-1, 0) == 0, "negative checkpoint");
        check(PlaybackPosition.resume(90, 100) == 90, "resume mid-video");
        check(PlaybackPosition.resume(100, 100) == 0, "completed video restarts");
        check(PlaybackPosition.resume(110, 100) == 0, "changed duration restarts");
        check(PlaybackPosition.resume(90, -9223372036854775807L) == 90, "unknown duration preserves checkpoint");
        check(PlaybackPosition.seek(Long.MAX_VALUE - 5, 10, 0) == Long.MAX_VALUE, "positive overflow");
        check(PlaybackPosition.seek(100, Long.MIN_VALUE, 1000) == 0, "negative overflow");
        Random random = new Random(31);
        for (int i = 0; i < 10000; i++) {
            long duration = 1 + random.nextInt(10000000);
            long position = random.nextInt(12000000);
            long delta = random.nextInt(120000) - 60000;
            long result = PlaybackPosition.seek(position, delta, duration);
            check(result >= 0 && result <= duration, "seek stays within video");
            check(result == Math.min(duration, Math.max(0, position + delta)), "seek matches bounded arithmetic");
        }
        for (int fps : new int[]{30, 60, 90, 120, 144}) {
            FovTransition fov = new FovTransition();
            check(!fov.isRunning(0), "new renderer sleeps");
            fov.update(88, 0, true);
            check(!fov.isRunning(0), "initial target requires one frame");
            fov.update(120, 0, false);
            check(fov.isRunning(0), "change wakes animation");
            long step = 1000000000L / fps, now = 0;
            int frames = 0;
            float visible = 88;
            while (fov.isRunning(now)) {
                now += step;
                visible = fov.update(120, now, false);
                check(++frames < fps, "on-demand animation terminates");
            }
            check(visible == 120, "last requested frame reaches target");
            check(!fov.isRunning(now), "no redundant steady-state redraws");
            fov.update(40, now, false);
            check(fov.isRunning(now), "reverse change wakes again");
            fov.update(90, now, true);
            check(!fov.isRunning(now), "mode switch snaps and stops");
        }
        ViewOrientation view = new ViewOrientation();
        view.setGyroEnabled(false);
        view.move(42, 15);
        float[] before = new float[9], after = new float[9];
        view.copyTo(before);
        view.setGyroEnabled(true);
        view.newSensorSession();
        view.onSensor(new float[]{1,0,0,0,1,0,0,0,1});
        view.copyTo(after);
        for (int i = 0; i < 9; i++) check(Math.abs(before[i] - after[i]) < .00001f, "restarted sensor preserves manual view");
        System.out.println("PASS: " + checks + " playback / on-demand rendering / sensor restart checks");
    }
}
