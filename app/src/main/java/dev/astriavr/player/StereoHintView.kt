package dev.astriavr.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.View

/** Small head-locked status text near the bottom of the right optical viewport. */
class StereoHintView(context: Context) : View(context) {
    var settings = RenderSettings()
        set(value) { if (field != value) { field = value; updateGeometry(); invalidate() } }
    var debugMode = false
        set(value) { if (field != value) { field = value; invalidate() } }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private var layout = settings.opticalLayout(0, 0)
    private var phoneViewport = Optics.PhoneViewport(0, 0, false)
    private val phoneBoundary = Path()
    private var message = ""
    private var expiresAt = 0L
    private var centersUntil = 0L
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38EF70.toInt() }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF38EF70.toInt()
        style = Paint.Style.STROKE
    }

    fun showCenters() {
        if (settings.phoneMode) return
        centersUntil = SystemClock.uptimeMillis() + 1500
        invalidate()
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun showValue(value: String) {
        message = value
        expiresAt = SystemClock.uptimeMillis() + 1500
        invalidate()
    }

    fun clear() { message = ""; expiresAt = 0; centersUntil = 0; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { updateGeometry() }

    private fun updateGeometry() {
        layout = settings.opticalLayout(width, height)
        phoneViewport = Optics.PhoneViewport(width, height, settings.phoneFillScreen)
        // UI lettering uses dp; only optical calibration markers use physical screen dimensions.
        paint.textSize = (if (settings.phoneMode) 14f else 7f) * resources.displayMetrics.density
        edgePaint.strokeWidth = maxOf(1f, (height / settings.screenHeightCm * .015).toFloat())
        phoneBoundary.reset()
        if (settings.phoneMode && phoneViewport.width > 0 && phoneViewport.height > 0) {
            val inset = edgePaint.strokeWidth / 2
            val cx = phoneViewport.x + phoneViewport.width / 2f
            val cy = height - phoneViewport.y - phoneViewport.height / 2f
            val rx = (phoneViewport.width / 2f - inset).coerceAtLeast(0f)
            val ry = (phoneViewport.height / 2f - inset).coerceAtLeast(0f)
            if (settings.phoneElliptical) {
                for (i in 0..128) {
                    val angle = i * Math.PI / 64
                    val x = kotlin.math.cos(angle); val y = kotlin.math.sin(angle)
                    val px = cx + (rx * kotlin.math.sign(x) * kotlin.math.sqrt(kotlin.math.abs(x))).toFloat()
                    val py = cy + (ry * kotlin.math.sign(y) * kotlin.math.sqrt(kotlin.math.abs(y))).toFloat()
                    if (i == 0) phoneBoundary.moveTo(px, py) else phoneBoundary.lineTo(px, py)
                }
                phoneBoundary.close()
            } else phoneBoundary.addRect(cx - rx, cy - ry, cx + rx, cy + ry, Path.Direction.CW)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centersRemaining = centersUntil - SystemClock.uptimeMillis()
        if (debugMode && settings.phoneMode) {
            canvas.drawPath(phoneBoundary, edgePaint)
            canvas.drawCircle(phoneViewport.x + phoneViewport.width / 2f,
                height - phoneViewport.y - phoneViewport.height / 2f,
                minOf(phoneViewport.width, phoneViewport.height) * .006f, centerPaint)
        }
        if (!settings.phoneMode && (debugMode || centersRemaining > 0)) {
            val centerY = height - layout.y - layout.eyeHeight / 2f
            val radius = layout.eyeHeight * .006f
            canvas.drawCircle(layout.leftX + layout.eyeWidth / 2f, centerY, radius, centerPaint)
            canvas.drawCircle(layout.rightX + layout.eyeWidth / 2f, centerY, radius, centerPaint)
            val inset = edgePaint.strokeWidth / 2f
            val top = height - layout.y - layout.eyeHeight.toFloat()
            if (layout.eyeWidth > edgePaint.strokeWidth && layout.eyeHeight > edgePaint.strokeWidth) {
                for (left in intArrayOf(layout.leftX, layout.rightX)) {
                    canvas.drawOval(left + inset, top + inset,
                        left + layout.eyeWidth - inset, top + layout.eyeHeight - inset, edgePaint)
                }
            }
            if (centersRemaining > 0) postInvalidateDelayed(centersRemaining)
        }
        val remaining = expiresAt - SystemClock.uptimeMillis()
        if (remaining <= 0 || message.isEmpty()) return
        paint.alpha = (160 * (remaining / 300f).coerceIn(0f, 1f)).toInt()
        val y = if (settings.phoneMode) minOf(height - 100f * resources.displayMetrics.density,
            height - phoneViewport.y - 36f * resources.displayMetrics.density).coerceAtLeast(paint.textSize)
            else height - layout.y - layout.eyeHeight + layout.eyeHeight * .78f
        val x = if (settings.phoneMode) width / 2f else layout.rightX + layout.eyeWidth / 2f
        canvas.drawText(message, x, y, paint)
        if (remaining > 300) postInvalidateDelayed(remaining - 300) else postInvalidateOnAnimation()
    }
}
