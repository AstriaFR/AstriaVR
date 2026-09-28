package dev.astriavr.player;

/** Fits the original video frame inside a window without crop, stretch, or optical projection. */
public final class FlatVideoGeometry {
    private FlatVideoGeometry() {}
    public static int[] fit(int windowWidth, int windowHeight, int videoWidth, int videoHeight, float pixelRatio) {
        int w = Math.max(1, windowWidth), h = Math.max(1, windowHeight);
        double ratio = videoWidth > 0 && videoHeight > 0
            ? (double) videoWidth / videoHeight * (Float.isFinite(pixelRatio) && pixelRatio > 0 ? pixelRatio : 1)
            : 16.0 / 9;
        if ((double) w / h > ratio) w = Math.max(1, Math.min(w, (int) Math.round(h * ratio)));
        else h = Math.max(1, Math.min(h, (int) Math.round(w / ratio)));
        return new int[] {w, h};
    }

    /** SurfaceView property transform: zoom and pan without resizing the decoder output. */
    public static final class Transform {
        private float scale = 1f;
        private float offsetX, offsetY;

        public float scale() { return scale; }
        public float offsetX() { return offsetX; }
        public float offsetY() { return offsetY; }

        public void reset() { scale = 1f; offsetX = offsetY = 0f; }

        public void gesture(float oldFocusX, float oldFocusY, float focusX, float focusY,
                float oldSpan, float span, int viewportWidth, int viewportHeight,
                int contentWidth, int contentHeight) {
            if (!Float.isFinite(oldFocusX) || !Float.isFinite(oldFocusY) ||
                    !Float.isFinite(focusX) || !Float.isFinite(focusY) ||
                    !Float.isFinite(oldSpan) || !Float.isFinite(span) || oldSpan <= 0 || span <= 0) return;
            float nextScale = Math.max(1f, Math.min(4f, scale * span / oldSpan));
            float factor = nextScale / scale;
            float centerX = viewportWidth / 2f, centerY = viewportHeight / 2f;
            offsetX = focusX - centerX - factor * (oldFocusX - centerX - offsetX);
            offsetY = focusY - centerY - factor * (oldFocusY - centerY - offsetY);
            scale = nextScale;
            constrain(viewportWidth, viewportHeight, contentWidth, contentHeight);
        }

        public void constrain(int viewportWidth, int viewportHeight, int contentWidth, int contentHeight) {
            if (scale <= 1f) { reset(); return; }
            // When the enlarged frame still fits, allow it to slide within the letterbox.
            // Once it fills an axis, keep the image edge against that viewport edge.
            float limitX = Math.abs(contentWidth * scale - viewportWidth) / 2f;
            float limitY = Math.abs(contentHeight * scale - viewportHeight) / 2f;
            offsetX = Math.max(-limitX, Math.min(limitX, offsetX));
            offsetY = Math.max(-limitY, Math.min(limitY, offsetY));
        }
    }
}
