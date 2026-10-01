package dev.astriavr.player;

/** Camera orientation shared by head tracking and a rate-controlled stick. No Android dependency. */
public final class ViewOrientation {
    private final float[] anchor = new float[9];
    private final float[] sensor = new float[9];
    private final float[] reference = new float[9];
    private final float[] latest = new float[9];
    private final float[] output = new float[9];
    private final float[] scratch = new float[9];
    private final float[] rotation = new float[9];
    private boolean gyroEnabled = true;
    private boolean hasSensor;
    private float manualPitch;

    public ViewOrientation() {
        PoseMath.identity(anchor);
        PoseMath.identity(sensor);
        PoseMath.identity(output);
    }

    public boolean isGyroEnabled() { return gyroEnabled; }

    /** Incoming matrices are Android row-major screen-to-world, as in 1.0.2. */
    public void onSensor(float[] screen) {
        System.arraycopy(screen, 0, latest, 0, 9);
        if (!hasSensor || !gyroEnabled) {
            System.arraycopy(screen, 0, reference, 0, 9);
            PoseMath.identity(sensor);
        } else {
            PoseMath.relative(reference, screen, sensor);
        }
        hasSensor = true;
        compose();
    }

    public void setGyroEnabled(boolean enabled) {
        if (enabled == gyroEnabled) return;
        freeze();
        gyroEnabled = enabled;
    }

    /** A resumed sensor stream gets a fresh reference without discarding the visible orientation. */
    public void newSensorSession() { freeze(); hasSensor = false; }

    public void recenter() {
        PoseMath.identity(anchor);
        PoseMath.identity(sensor);
        PoseMath.identity(output);
        if (hasSensor) System.arraycopy(latest, 0, reference, 0, 9);
        manualPitch = 0f;
    }

    /** Positive yaw looks right, positive pitch looks up. Stick pitch is limited to +/-85 degrees. */
    public void move(float yawDegrees, float pitchDegrees) {
        if (!Float.isFinite(yawDegrees) || !Float.isFinite(pitchDegrees)) return;
        float nextPitch = Math.max(-85f, Math.min(85f, manualPitch + pitchDegrees));
        float pitchStep = nextPitch - manualPitch;
        manualPitch = nextPitch;
        compose();
        double yaw = Math.toRadians(-yawDegrees);
        float c = (float)Math.cos(yaw), s = (float)Math.sin(yaw);
        // World-up yaw, followed by pitch around the current camera's right axis.
        PoseMath.identity(rotation);
        rotation[0] = c; rotation[2] = -s; rotation[6] = s; rotation[8] = c;
        multiply(rotation, output, scratch);
        double pitch = Math.toRadians(pitchStep);
        c = (float)Math.cos(pitch); s = (float)Math.sin(pitch);
        PoseMath.identity(rotation);
        rotation[4] = c; rotation[5] = s; rotation[7] = -s; rotation[8] = c;
        multiply(scratch, rotation, anchor);
        // Keep the current head pose in the anchor, then measure future head motion from here.
        // This also lets the stick work while gyro control is disabled.
        if (hasSensor) System.arraycopy(latest, 0, reference, 0, 9);
        PoseMath.identity(sensor);
        compose();
    }

    public void copyTo(float[] destination) { System.arraycopy(output, 0, destination, 0, 9); }

    private void freeze() {
        compose();
        System.arraycopy(output, 0, anchor, 0, 9);
        PoseMath.identity(sensor);
        if (hasSensor) System.arraycopy(latest, 0, reference, 0, 9);
    }

    private void compose() { multiply(anchor, sensor, output); }

    /** Column-major product. Destination must not alias an input. */
    private static void multiply(float[] left, float[] right, float[] destination) {
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                destination[col * 3 + row] = left[row] * right[col * 3]
                    + left[3 + row] * right[col * 3 + 1]
                    + left[6 + row] * right[col * 3 + 2];
            }
        }
    }
}
