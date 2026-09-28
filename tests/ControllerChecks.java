import dev.astriavr.player.GamepadState;
import dev.astriavr.player.GamepadState.Action;
import dev.astriavr.player.Optics;
import dev.astriavr.player.SeekAcceleration;
import dev.astriavr.player.PlaybackTuning;
import dev.astriavr.player.ViewOrientation;

public class ControllerChecks {
    private static int checks;
    private static void near(double got, double expected, double epsilon, String name) {
        checks++;
        if (!Double.isFinite(got) || Math.abs(got - expected) > epsilon)
            throw new AssertionError(name + ": expected " + expected + ", got " + got);
    }
    private static int bit(Action action) { return 1 << action.ordinal(); }
    private static float[] yaw(float degrees) {
        float c = (float)Math.cos(Math.toRadians(degrees)), s = (float)Math.sin(Math.toRadians(degrees));
        return new float[]{c,0,s, 0,1,0, -s,0,c}; // Android row-major input
    }
    private static float[] snapshot(ViewOrientation view) {
        float[] result = new float[9]; view.copyTo(result); return result;
    }
    private static void equalPose(float[] got, float[] expected, String name) {
        for (int i = 0; i < 9; i++) near(got[i], expected[i], .00002, name + " matrix[" + i + "]");
    }
    private static void forward(ViewOrientation view, double x, double y, double z, String name) {
        float[] matrix = snapshot(view);
        near(-matrix[6], x, .0001, name + " x");
        near(-matrix[7], y, .0001, name + " y");
        near(-matrix[8], z, .0001, name + " z");
    }

    public static void main(String[] args) {
        GamepadState pad = new GamepadState();
        for (Action action : Action.values()) {
            pad.clear();
            near(pad.key(action, true, false), bit(action), 0, "one press");
            near(pad.key(action, true, true), 0, 0, "OS repeat ignored");
            near(pad.key(action, true, false), 0, 0, "duplicate down ignored");
            near(pad.key(action, false, false), 0, 0, "release does not act");
            near(pad.key(action, true, false), bit(action), 0, "next press acts");
        }
        pad.clear();
        near(pad.key(Action.SEEK_BACK, true, false), bit(Action.SEEK_BACK), 0, "Dpad key first");
        near(pad.hat(-1), 0, 0, "same press as hat not counted twice");
        pad.key(Action.SEEK_BACK, false, false);
        near(pad.hat(-1), 0, 0, "hat still held");
        pad.hat(0);
        near(pad.hat(1), bit(Action.SEEK_FORWARD), 0, "hat-only right");
        near(pad.key(Action.SEEK_FORWARD, true, false), 0, 0, "same press as key not counted twice");
        pad.key(Action.SEEK_FORWARD, false, false);
        near(pad.hat(-1), bit(Action.SEEK_BACK), 0, "hat right-to-left");
        pad.clear();
        near(pad.key(Action.TOGGLE_GYRO, true, true), 0, 0, "repeat after focus change does not toggle");
        pad.clear();
        pad.stick(.05f, -.04f, 0);
        near(pad.isMoving() ? 1 : 0, 0, 0, "Hall stick center drift dead zone");
        pad.stick(1, 0, 0);
        near(pad.stickX, 1, 1e-6, "full right");
        pad.stick(0, -1, 0);
        near(-pad.stickY, 1, 1e-6, "up becomes positive pitch");
        pad.stick(1, 1, 0);
        near(Math.hypot(pad.stickX, pad.stickY), 1, 1e-6, "diagonal speed limited");
        pad.stick(.2f, 0, .25f);
        near(pad.stickX, 0, 0, "device flat honored");
        pad.stick(.56f, 0, 0);
        near(pad.stickX, .5, 1e-6, "smooth rescaled half deflection");
        pad.stick(Float.NaN, 1, 0);
        near(pad.isMoving() ? 1 : 0, 0, 0, "invalid axis stops movement");
        pad.stick(1, 1, 0); pad.clear();
        near(pad.isMoving() ? 1 : 0, 0, 0, "clear stops movement");

        for (int fps : new int[]{30, 60, 120}) {
            pad.clear();
            near(pad.key(Action.FOV_UP, true, false), bit(Action.FOV_UP), 0, "D-pad down immediate step");
            int count = 0;
            for (int i = 0; i < fps; i++) if (pad.repeatFov(1f/fps) != 0) count++;
            near(count, 4, 0, "D-pad exactly four repeats per second");
            pad.key(Action.FOV_UP, false, false);
            near(pad.repeatFov(.05f), 0, 0, "release stops FOV repeat");
        }
        pad.clear();
        pad.key(Action.FOV_DOWN, true, true);
        near(pad.hasFovRepeat() ? 1 : 0, 0, 0, "OS repeat after focus loss does not restart hold");
        pad.clear();
        pad.key(Action.FOV_DOWN, true, false);
        pad.key(Action.FOV_UP, true, false);
        near(pad.hasFovRepeat() ? 1 : 0, 0, 0, "both FOV directions stop repeating");
        pad.key(Action.FOV_UP, false, false);
        near(pad.hasFovRepeat() ? 1 : 0, 1, 0, "remaining FOV direction resumes");
        pad.clear();
        near(pad.repeatFov(.05f), 0, 0, "disconnect clears FOV repeat");

        for (int mode = 0; mode < 3; mode++) {
            for (int fps : new int[]{30,60,120}) {
                pad.clear();
                if (mode != 1) pad.key(Action.SEEK_FORWARD, true, false);
                if (mode != 0) pad.hat(1);
                int repeats = 0;
                for (int i = 0; i < fps; i++) {
                    if (mode != 1) pad.key(Action.SEEK_FORWARD, true, true);
                    if (mode != 0) pad.hat(1);
                    if (pad.repeatSeek(1f/fps) != 0) repeats++;
                }
                near(repeats, 4, 0, "key/hat/mixed hold seek four repeats per second");
                pad.key(Action.SEEK_FORWARD, false, false); pad.hat(0);
                near(pad.hasSeekRepeat() ? 1 : 0, 0, 0, "release both reports stops seeking");
            }
        }
        pad.clear(); pad.key(Action.SEEK_BACK, true, true);
        near(pad.hasSeekRepeat() ? 1 : 0, 0, 0, "stale OS repeat does not restart seeking");
        pad.clear(); pad.hat(-1); pad.key(Action.SEEK_BACK, true, false); pad.hat(0);
        near(pad.hasSeekRepeat() ? 1 : 0, 1, 0, "hat-to-key hold continuity");
        pad.key(Action.SEEK_FORWARD, true, false);
        near(pad.hasSeekRepeat() ? 1 : 0, 0, 0, "opposing seek buttons do not repeat");
        pad.clear();
        near(pad.repeatSeek(.05f), 0, 0, "focus loss clears held seek");

        SeekAcceleration burst = new SeekAcceleration();
        for (int i = 1; i <= 18; i++) near(burst.next(1, i*250), i <= 6 ? 10000 : i <= 12 ? 20000 : 30000, 0, "shared forward seek tiers");
        near(burst.next(-1, 4750), -10000, 0, "reversal starts fresh burst");
        for (int i = 2; i <= 15; i++) near(burst.next(-1, 4500+i*250), i <= 6 ? -10000 : i <= 12 ? -20000 : -30000, 0, "shared reverse seek tiers");
        near(burst.next(-1, 9250), -10000, 0, "one second pause resets burst");
        burst.reset();
        near(burst.next(1, 7000), 10000, 0, "focus reset starts at 10s");

        float ipd = 6.5f;
        pad.clear();
        near(pad.hat(0, -1), bit(Action.FOV_DOWN), 0, "hat up decreases FOV");
        near(pad.key(Action.FOV_DOWN, true, false), 0, 0, "FOV key/hat deduplicated");
        pad.clear();
        near(pad.hat(0, 1), bit(Action.FOV_UP), 0, "hat down increases FOV");
        pad.rightStick(.40f, 0);
        near(pad.rightX, 0, 0, "right dead zone inclusive 40 percent");
        for (int i = -40; i <= 40; i++) {
            pad.rightStick(i / 100f, 0);
            near(pad.rightX, 0, 0, "whole horizontal dead zone stops");
            pad.rightStick(0, i / 100f);
            near(pad.rightY, 0, 0, "whole vertical dead zone stops");
        }
        pad.rightStick(.401f, 0);
        near(pad.rightX, .001 / .6, 1e-6, "starts gently just outside dead zone");
        pad.rightStick(.55f, 0, .6f);
        near(pad.rightX, 0, 0, "larger device dead zone honored");
        pad.rightStick(.70f, .1f);
        near(pad.rightX, .5, 1e-6, "right half speed");
        near(pad.rightY, 0, 0, "horizontal leaves brightness alone");
        pad.rightStick(.1f, -1);
        near(pad.rightX, 0, 0, "vertical leaves IPD alone");
        near(pad.rightY, -1, 0, "up brightness axis");
        pad.rightStick(.8f, .8f);
        near(pad.isMoving() ? 1 : 0, 0, 0, "diagonal gap prevents mixed adjustment");
        pad.rightStick(Float.NaN, 1);
        near(pad.isMoving() ? 1 : 0, 0, 0, "invalid right axis stopped");
        pad.rightStick(1, 0); pad.clear();
        near(pad.isMoving() ? 1 : 0, 0, 0, "disconnect stops both sticks");
        float[] speeds = {.2f, .5f, .8f, 1, 1.2f, 1.5f, 2};
        near(PlaybackTuning.speed(PlaybackTuning.DEFAULT_SPEED), 1, 0, "default normal speed");
        for (int i = 0; i < speeds.length; i++) {
            near(PlaybackTuning.speed(i), speeds[i], 0, "speed tier");
            near(PlaybackTuning.stepSpeed(i, -1), Math.max(0, i-1), 0, "X slower clamped");
            near(PlaybackTuning.stepSpeed(i, 1), Math.min(6, i+1), 0, "Y faster clamped");
        }
        near(PlaybackTuning.adjustBrightness(.5f, 1, 1), .58, 1e-6, "slow brightness rate");
        near(PlaybackTuning.adjustBrightness(1, 1, 1), 1, 0, "brightness maximum");
        near(PlaybackTuning.adjustBrightness(.05f, -1, 1), .05, 1e-6, "brightness minimum");
        pad.clear(); pad.rightStick(0, -1);
        near(pad.volumeSteps(1f/60), 1, 0, "first deflection makes exactly one step");
        for (int i = 0; i < 32; i++) near(pad.volumeSteps(1f/60), 0, 0, "initial repeat delay");
        near(pad.volumeSteps(1f/60), 1, 0, "repeat after 550ms");
        for (int i = 0; i < 11; i++) near(pad.volumeSteps(1f/60), 0, 0, "full stick repeat interval");
        near(pad.volumeSteps(1f/60), 1, 0, "repeat every 200ms at full stick");
        pad.rightStick(0, 0);
        near(pad.volumeSteps(1), 0, 0, "neutral cancels repeats");
        pad.rightStick(0, 1);
        near(pad.volumeSteps(.01f), -1, 0, "opposite direction steps immediately");
        pad.rightStick(1, 0);
        near(pad.volumeSteps(1), 0, 0, "horizontal brightness cancels volume repeat");
        pad.rightStick(0, -1); pad.clear();
        near(pad.volumeSteps(1), 0, 0, "disconnect cancels pending first step");
        pad.rightStick(0, -1); pad.volumeSteps(.01f); pad.rightStick(Float.NaN, 0);
        near(pad.volumeSteps(1), 0, 0, "invalid input cancels repeat");
        int[] frameCounts = new int[2];
        int at = 0;
        for (int fps : new int[]{30, 120}) {
            pad.clear(); pad.rightStick(0, -1); frameCounts[at] = pad.volumeSteps(0);
            for (int i = 0; i < fps * 3; i++) frameCounts[at] += pad.volumeSteps(1f/fps);
            at++;
        }
        near(frameCounts[0], frameCounts[1], 0, "discrete steps independent of refresh rate");
        near(Optics.stepIpd(6.537f, 0), 6.5, 1e-6, "old continuous setting snaps to 0.1cm");
        near(Optics.tanHalfFov(88), Optics.TAN_HALF_FOV, 0, "default FOV 88 degrees");
        near(Optics.stepFov(88, -1), 80, 0, "D-pad up reduces FOV by 8");
        near(Optics.stepFov(88, 1), 96, 0, "D-pad down increases FOV by 8");
        near(Optics.stepFov(40, -1), 40, 0, "FOV minimum");
        near(Optics.stepFov(120, 1), 120, 0, "FOV maximum");
        for (int fov = 40; fov <= 120; fov += 8) {
            for (int correction : new int[]{0, 60, 100}) {
                near(Optics.rayAngle(0, fov, correction), 0, 0, "correction preserves optical center");
                near(Math.toDegrees(Optics.rayAngle(1, fov, correction)) * 2, fov, .00002, "correction preserves angular edge in every tier");
                double previous = -1;
                for (int r = 0; r <= 100; r++) {
                    double angle = Optics.rayAngle(r/100f, fov, correction);
                    near(angle > previous ? 1 : 0, 1, 0, "no radial foldover in any tier");
                    previous = angle;
                }
            }
            double centerSlope = Optics.rayAngle(.001f, fov, 60) / .001;
            double edgeSlope = (Optics.rayAngle(1, fov, 60) - Optics.rayAngle(.999f, fov, 60)) / .001;
            double oldCenter = Optics.rayAngle(.001f, fov, 0) / .001;
            double oldEdge = (Optics.rayAngle(1, fov, 0) - Optics.rayAngle(.999f, fov, 0)) / .001;
            near(centerSlope / edgeSlope < oldCenter / oldEdge ? 1 : 0, 1, 0, "less center-to-edge stretch disparity");
        }
        for (float distance : new float[]{5, 6.5f, 8}) {
            for (int width : new int[]{2800, 1400}) {
                Optics.Layout optical = new Optics.Layout(width, width * 9 / 20, distance);
                float left = optical.leftX + optical.eyeWidth / 2f;
                float right = optical.rightX + optical.eyeWidth / 2f;
                near(right - left, optical.centerDistance, 1, "HUD follows actual binocular center distance");
                near((left - optical.leftX) / optical.eyeWidth, (right - optical.rightX) / optical.eyeWidth, 0, "same eye-relative HUD position");
            }
        }
        for (int i = 0; i < 15; i++) ipd = Optics.stepIpd(ipd, -1);
        near(ipd, 5, 0, "X reaches 5cm exactly");
        near(Optics.stepIpd(ipd, -1), 5, 0, "X lower clamp");
        for (int i = 0; i < 30; i++) ipd = Optics.stepIpd(ipd, 1);
        near(ipd, 8, 0, "Y reaches 8cm exactly");
        near(Optics.stepIpd(ipd, 1), 8, 0, "Y upper clamp");
        near(Optics.stepIpd(6.5f, -1), 6.4, 1e-6, "X minus 0.1cm");
        near(Optics.stepIpd(6.5f, 1), 6.6, 1e-6, "Y plus 0.1cm");

        ViewOrientation view = new ViewOrientation();
        view.onSensor(yaw(0));
        view.onSensor(yaw(90));
        forward(view, -1,0,0, "existing head tracking");
        float[] before = snapshot(view);
        view.setGyroEnabled(false);
        equalPose(snapshot(view), before, "turning gyro off does not jump");
        view.onSensor(yaw(180));
        equalPose(snapshot(view), before, "phone rotation ignored while gyro off");
        view.move(30, 0);
        forward(view, -Math.sin(Math.toRadians(60)),0,-.5, "stick works with gyro off");
        before = snapshot(view);
        view.setGyroEnabled(true);
        equalPose(snapshot(view), before, "turning gyro on does not jump");
        view.onSensor(yaw(180));
        equalPose(snapshot(view), before, "first sample after enable keeps pose");
        view.onSensor(yaw(210));
        forward(view, -1,0,0, "head rotation resumes from enable position");
        before = snapshot(view);
        for (int i = 0; i < 20; i++) { view.setGyroEnabled(false); view.setGyroEnabled(true); }
        equalPose(snapshot(view), before, "repeated toggles stable");
        view.newSensorSession();
        view.onSensor(yaw(-45));
        equalPose(snapshot(view), before, "new sensor session preserves view");
        view.recenter();
        forward(view, 0,0,-1, "B recenters gyro mode");
        view.setGyroEnabled(false);
        view.move(90, 0);
        forward(view, 1,0,0, "right stick means look right");
        view.recenter();
        view.move(0, 30);
        forward(view, 0,.5,-Math.cos(Math.toRadians(30)), "up stick means look up");
        view.recenter();
        view.onSensor(yaw(75));
        forward(view, 0,0,-1, "B recenters stick-only mode without enabling gyro");
        near(view.isGyroEnabled() ? 1 : 0, 0, 0, "B preserves gyro switch");
        view.move(0, 100);
        forward(view, 0,Math.sin(Math.toRadians(85)),-Math.cos(Math.toRadians(85)), "pitch upper limit");

        ViewOrientation mixed = new ViewOrientation();
        mixed.onSensor(yaw(0)); mixed.onSensor(yaw(30)); mixed.move(10, 0); mixed.onSensor(yaw(40));
        forward(mixed, -.5,0,-Math.cos(Math.toRadians(30)), "head and stick compose without snapback");
        ViewOrientation slow = new ViewOrientation(), fast = new ViewOrientation();
        slow.setGyroEnabled(false); fast.setGyroEnabled(false);
        for (int i = 0; i < 30; i++) slow.move(90f/30, 0);
        for (int i = 0; i < 120; i++) fast.move(90f/120, 0);
        equalPose(snapshot(slow), snapshot(fast), "rate independent of refresh rate");
        forward(fast, 1,0,0, "90 degrees per second");
        System.out.println("PASS: " + checks + " controller state / dead zone / IPD / gyro-toggle checks");
    }
}
