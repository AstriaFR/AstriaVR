package dev.astriavr.player

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.ViewGroup
import kotlin.math.hypot

/** A native decoder surface. VR shaders and sensors are not part of this output path. */
class FlatVideoView(context: Context) : ViewGroup(context) {
    val surfaceView = SurfaceView(context)
    var onVideoTouch: (MotionEvent) -> Boolean = { false }
    var onTap: () -> Unit = {}
    var onMultiTouchStart: () -> Unit = {}
    var onMultiTouchActive: (Boolean) -> Unit = {}
    var zoomEnabled = true
        set(value) {
            if (field == value) return
            field = value
            if (!value) resetTransform()
        }
    private var videoWidth = 0
    private var videoHeight = 0
    private var pixelRatio = 1f
    private val transform = FlatVideoGeometry.Transform()
    private val fingers = FloatArray(3) // midpoint X/Y and distance, reused during MOVE
    private var multiTouch = false
    private var previousX = 0f
    private var previousY = 0f
    private var previousSpan = 0f
    init {
        setBackgroundColor(Color.BLACK)
        addView(surfaceView, LayoutParams(-1, -1))
        surfaceView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        contentDescription = AppText.STANDARD_VIDEO_VIEW.text()
    }
    fun setVideoSize(width: Int, height: Int, pixelWidthHeightRatio: Float) {
        if (width == videoWidth && height == videoHeight && pixelWidthHeightRatio == pixelRatio) return
        videoWidth = width; videoHeight = height; pixelRatio = pixelWidthHeightRatio; requestLayout()
    }
    fun resetTransform() {
        transform.reset()
        applyTransform()
    }
    private fun applyTransform() {
        surfaceView.pivotX = surfaceView.measuredWidth / 2f
        surfaceView.pivotY = surfaceView.measuredHeight / 2f
        surfaceView.scaleX = transform.scale()
        surfaceView.scaleY = transform.scale()
        surfaceView.translationX = transform.offsetX()
        surfaceView.translationY = transform.offsetY()
    }
    private fun sampleFingers(event: MotionEvent, excludedIndex: Int = -1): Boolean {
        var first = -1
        var second = -1
        for (i in 0 until event.pointerCount) {
            if (i == excludedIndex) continue
            if (first < 0) first = i else { second = i; break }
        }
        if (second < 0) return false
        val dx = event.getX(second) - event.getX(first)
        val dy = event.getY(second) - event.getY(first)
        fingers[0] = (event.getX(first) + event.getX(second)) / 2f
        fingers[1] = (event.getY(first) + event.getY(second)) / 2f
        fingers[2] = hypot(dx, dy)
        return true
    }
    private fun rememberFingers() {
        previousX = fingers[0]; previousY = fingers[1]; previousSpan = fingers[2]
    }
    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w); val height = MeasureSpec.getSize(h)
        setMeasuredDimension(width, height)
        val fit = FlatVideoGeometry.fit(width, height, videoWidth, videoHeight, pixelRatio)
        surfaceView.measure(MeasureSpec.makeMeasureSpec(fit[0], MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(fit[1], MeasureSpec.EXACTLY))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val x = (width - surfaceView.measuredWidth) / 2
        val y = (height - surfaceView.measuredHeight) / 2
        surfaceView.layout(x, y, x + surfaceView.measuredWidth, y + surfaceView.measuredHeight)
        transform.constrain(width, height, surfaceView.measuredWidth, surfaceView.measuredHeight)
        applyTransform()
    }
    override fun onInterceptTouchEvent(event: MotionEvent) = true
    // Single-finger tap timing is shared with VrView; two fingers transform the native surface.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multiTouch = false; previousSpan = 0f
                return onVideoTouch(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!multiTouch) { multiTouch = true; onMultiTouchStart(); onMultiTouchActive(true) }
                if (sampleFingers(event)) rememberFingers()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!multiTouch) return onVideoTouch(event)
                if (sampleFingers(event)) {
                    if (zoomEnabled) transform.gesture(previousX, previousY, fingers[0], fingers[1], previousSpan, fingers[2],
                        width, height, surfaceView.measuredWidth, surfaceView.measuredHeight)
                    rememberFingers()
                    applyTransform()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (!multiTouch) return onVideoTouch(event)
                if (sampleFingers(event, event.actionIndex)) rememberFingers() else previousSpan = 0f
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (multiTouch) { multiTouch = false; previousSpan = 0f; onMultiTouchActive(false); return true }
                return onVideoTouch(event)
            }
            MotionEvent.ACTION_CANCEL -> {
                if (multiTouch) { multiTouch = false; previousSpan = 0f; onMultiTouchStart(); onMultiTouchActive(false); return true }
                return onVideoTouch(event)
            }
        }
        return onVideoTouch(event)
    }
    override fun performClick(): Boolean { super.performClick(); onTap(); return true }
}
