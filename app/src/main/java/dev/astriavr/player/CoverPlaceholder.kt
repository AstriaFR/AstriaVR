package dev.astriavr.player

import android.graphics.*
import android.graphics.drawable.Drawable

/** Code-native placeholder; a failed decoder never falls back to a misleading first frame. */
class CoverPlaceholder : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var gradient: LinearGradient? = null
    override fun onBoundsChange(bounds: Rect) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        gradient = LinearGradient(0f, 0f, w, h, 0xFF274571.toInt(), 0xFF101E3C.toInt(), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(w * .5f - h * .055f, h * .39f)
        path.lineTo(w * .5f + h * .11f, h * .5f)
        path.lineTo(w * .5f - h * .055f, h * .61f)
        path.close()
    }
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        paint.shader = gradient
        canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null
        paint.color = 0xFF6280B4.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = h * .012f
        canvas.drawCircle(w * .5f, h * .5f, h * .25f, paint)
        paint.style = Paint.Style.FILL; paint.color = UiStyle.ACCENT
        canvas.drawPath(path, paint)
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.OPAQUE
}
