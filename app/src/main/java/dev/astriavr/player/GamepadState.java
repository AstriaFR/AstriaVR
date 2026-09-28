package dev.astriavr.player;

/** One controller's input state. D-pad key/hat duplicates and OS key repeats fire only once. */
public final class GamepadState {
    public enum Action { SEEK_BACK, SEEK_FORWARD, PLAY_PAUSE, RECENTER, TOGGLE_GYRO, SPEED_DOWN, SPEED_UP, VOLUME_DOWN, VOLUME_UP, FOV_DOWN, FOV_UP, IPD_DOWN, IPD_UP, SPEED_RESET }
    public static final float RIGHT_DEAD_ZONE = .40f;
    private int heldKeys;
    private int heldHat;
    private boolean leftTriggerKey, leftTriggerAxis, leftTriggerLatched;

    /** Merge digital L2 and analog LT reports into one physical press. */
    public int leftTriggerKey(boolean down, boolean repeated) {
        leftTriggerKey = down;
        return updateLeftTrigger(!repeated);
    }

    public int leftTriggerAxis(float value) {
        if (!Float.isFinite(value)) return 0;
        if (value >= .55f) leftTriggerAxis = true;
        else if (value <= .25f) leftTriggerAxis = false;
        return updateLeftTrigger(true);
    }

    private int updateLeftTrigger(boolean allowPress) {
        boolean down = leftTriggerKey || leftTriggerAxis;
        int action = down && !leftTriggerLatched && allowPress ? 1 << Action.PLAY_PAUSE.ordinal() : 0;
        leftTriggerLatched = down;
        return action;
    }
    public float stickX, stickY;
    public float rightX, rightY;
    private int volumeDirection;
    private boolean volumeFirstStep;
    private float volumeWait;
    private int repeatingFov;
    private float fovWait;
    private int repeatingSeekKeys;
    private float seekWait;
    private static final int SEEK_MASK = (1 << Action.SEEK_BACK.ordinal()) | (1 << Action.SEEK_FORWARD.ordinal());
    private static final int FOV_MASK = (1 << Action.FOV_DOWN.ordinal()) | (1 << Action.FOV_UP.ordinal());
    private static final int TUNING_MASK = (1 << Action.IPD_DOWN.ordinal()) | (1 << Action.IPD_UP.ordinal())
        | (1 << Action.SPEED_DOWN.ordinal()) | (1 << Action.SPEED_UP.ordinal());
    private int repeatingTuning;
    private float tuningWait;
    private int boundaryTap;
    private long boundaryTapTime;

    /** Only two fresh presses already at the same speed limit reset playback to 1x. */
    public int speedKey(Action action, boolean down, boolean repeated, int speedIndex, long timeMillis) {
        int result = key(action, down, repeated);
        if (repeated) boundaryTap = 0;
        if (result == 0) return 0;
        int direction = action == Action.SPEED_DOWN ? -1 : 1;
        boolean atLimit = PlaybackTuning.stepSpeed(speedIndex, direction) == speedIndex;
        if (atLimit && boundaryTap == result && timeMillis >= boundaryTapTime && timeMillis - boundaryTapTime <= 350) {
            boundaryTap = 0;
            repeatingTuning &= ~result; // Holding the second tap must keep the restored 1x speed.
            return 1 << Action.SPEED_RESET.ordinal();
        }
        boundaryTap = atLimit ? result : 0;
        boundaryTapTime = timeMillis;
        return result;
    }

    public int key(Action action, boolean down, boolean repeated) {
        int seekBefore = seekHeld();
        int fovBefore = fovHeld();
        int bit = 1 << action.ordinal();
        int before = heldKeys | heldHat;
        if (down) heldKeys |= bit; else heldKeys &= ~bit;
        if (action == Action.FOV_DOWN || action == Action.FOV_UP) {
            if (!down) repeatingFov &= ~bit;
            else if (!repeated && (before & bit) == 0) repeatingFov |= bit;
            else if ((heldHat & bit) != 0) repeatingFov |= bit;
            if (fovBefore != fovHeld()) fovWait = .25f;
        }
        if ((bit & TUNING_MASK) != 0) {
            int previous = repeatingTuning;
            if (!down) repeatingTuning &= ~bit;
            else if (!repeated && (before & bit) == 0) repeatingTuning |= bit;
            if (previous != repeatingTuning) tuningWait = .4f;
        }
        if ((bit & SEEK_MASK) != 0) {
            if (!down) repeatingSeekKeys &= ~bit;
            else if (!repeated && (before & bit) == 0) repeatingSeekKeys |= bit;
            // Key and HAT can report the same hold; don't reset its timer on duplicates.
            else if ((heldHat & bit) != 0) repeatingSeekKeys |= bit;
            if (seekBefore != seekHeld()) seekWait = .25f;
        }
        return down && !repeated ? bit & ~before : 0;
    }

    private int fovHeld() { return (repeatingFov | heldHat) & FOV_MASK; }
    public boolean hasFovRepeat() { return Integer.bitCount(fovHeld()) == 1; }
    public boolean hasTuningRepeat() { return Integer.bitCount(repeatingTuning) == 1; }
    public int repeatTuning(float seconds) {
        if (!hasTuningRepeat() || !Float.isFinite(seconds) || seconds <= 0) return 0;
        tuningWait -= Math.min(.05f, seconds);
        if (tuningWait > .00001f) return 0;
        tuningWait += .4f;
        boundaryTap = 0;
        return repeatingTuning;
    }
    private int seekHeld() { return (repeatingSeekKeys | heldHat) & SEEK_MASK; }
    public boolean hasSeekRepeat() { return Integer.bitCount(seekHeld()) == 1; }
    public int repeatSeek(float seconds) {
        if (!hasSeekRepeat() || !Float.isFinite(seconds) || seconds <= 0) return 0;
        seekWait -= Math.min(.05f, seconds);
        if (seekWait > .00001f) return 0;
        seekWait += .25f;
        return seekHeld();
    }
    public int repeatFov(float seconds) {
        if (!hasFovRepeat() || !Float.isFinite(seconds) || seconds <= 0) return 0;
        fovWait -= Math.min(.05f, seconds);
        if (fovWait > .00001f) return 0;
        fovWait += .25f;
        return fovHeld();
    }

    public int hat(float x) {
        return hat(x, 0);
    }

    public int hat(float x, float y) {
        int seekBefore = seekHeld();
        int fovBefore = fovHeld();
        int before = heldKeys | heldHat;
        heldHat = x < -.5f ? 1 << Action.SEEK_BACK.ordinal()
            : x > .5f ? 1 << Action.SEEK_FORWARD.ordinal() : 0;
        heldHat |= y < -.5f ? 1 << Action.FOV_DOWN.ordinal()
            : y > .5f ? 1 << Action.FOV_UP.ordinal() : 0;
        if (seekBefore != seekHeld()) seekWait = .25f;
        if (fovBefore != fovHeld()) fovWait = .25f;
        return (heldKeys | heldHat) & ~before;
    }

    public void stick(float x, float y, float deviceFlat) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) { stickX = stickY = 0; return; }
        double radius = Math.hypot(x, y);
        float deadZone = Float.isFinite(deviceFlat) ? Math.max(.12f, Math.min(.95f, deviceFlat)) : .12f;
        if (radius <= deadZone) { stickX = stickY = 0; return; }
        double amount = (Math.min(1, radius) - deadZone) / (1 - deadZone);
        stickX = (float)(x / radius * amount);
        stickY = (float)(y / radius * amount);
    }

    public void rightStick(float x, float y) {
        rightStick(x, y, 0);
    }

    public void rightStick(float x, float y, float deviceFlat) {
        rightX = rightY = 0;
        if (!Float.isFinite(x) || !Float.isFinite(y)) { resetVolumeRepeat(); return; }
        float deadZone = Float.isFinite(deviceFlat) ? Math.max(RIGHT_DEAD_ZONE, Math.min(.95f, deviceFlat)) : RIGHT_DEAD_ZONE;
        // A small diagonal gap prevents accidental changes to the other setting.
        if (Math.abs(x) > Math.abs(y) * 1.2f) rightX = rightAmount(x, deadZone);
        else if (Math.abs(y) > Math.abs(x) * 1.2f) rightY = rightAmount(y, deadZone);
        int direction = (int) -Math.signum(rightY);
        if (direction != volumeDirection) {
            resetVolumeRepeat();
            volumeDirection = direction;
            volumeFirstStep = direction != 0;
        }
    }

    /** One system volume step on vertical deflection, then deliberate, rate-limited repeats. */
    public int volumeSteps(float seconds) {
        if (volumeDirection == 0) return 0;
        if (volumeFirstStep) { volumeFirstStep = false; volumeWait = .55f; return volumeDirection; }
        if (!Float.isFinite(seconds) || seconds <= 0) return 0;
        volumeWait -= Math.min(.05f, seconds);
        if (volumeWait > .00001f) return 0;
        volumeWait += .45f - .25f * Math.abs(rightY);
        return volumeDirection;
    }

    private void resetVolumeRepeat() { volumeDirection = 0; volumeFirstStep = false; volumeWait = 0; }

    private float rightAmount(float value, float deadZone) {
        return Math.copySign(Math.max(0, Math.min(1, Math.abs(value)) - deadZone) / (1 - deadZone), value);
    }

    public boolean isMoving() { return stickX != 0 || stickY != 0 || rightX != 0 || rightY != 0; }
    public void clear() { heldKeys = heldHat = repeatingFov = repeatingSeekKeys = repeatingTuning = boundaryTap = 0; leftTriggerKey = leftTriggerAxis = leftTriggerLatched = false; fovWait = seekWait = tuningWait = 0; stickX = stickY = rightX = rightY = 0; resetVolumeRepeat(); }
}
