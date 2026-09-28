package dev.astriavr.player

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

/** Shared nebula artwork, decoded once at native resolution and center-cropped without stretching. */
class StarfieldDrawable(context: Context, private val kind: Kind, radiusDp: Int = 0) : Drawable() {
    enum class Kind { PAGE, PANEL, BAR }
    private val density = context.resources.displayMetrics.density
    private val radius = radiusDp * density
    private val rect = RectF()
    private val edge = RectF()
    private val clip = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val bitmap = if (kind == Kind.BAR) null else artwork(context)
    private val nebula = bitmap?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    private var barBase: Shader? = null
    private var barViolet: Shader? = null
    private var barBlue: Shader? = null
    private var border: Shader? = null
    private var opacity = 255
    private var filter: ColorFilter? = null

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        clip.reset()
        if (rect.isEmpty) return
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val inset = .5f * density
        edge.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        if (bitmap != null) {
            // Wide phones crop top/bottom; squarer screens crop left/right. Never stretch the clouds.
            val scale = max(rect.width() / bitmap.width, rect.height() / bitmap.height)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(rect.centerX() - bitmap.width * scale / 2f, rect.centerY() - bitmap.height * scale / 2f)
            }
            nebula?.setLocalMatrix(matrix)
        } else {
            barBase = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(0xB80B1735.toInt(), 0xA51C2450.toInt(), 0xB80B2045.toInt()), null, Shader.TileMode.CLAMP)
            barViolet = light(.22f, .85f, .50f, 1.8f, 0x405943A3)
            barBlue = light(.78f, .12f, .48f, 1.8f, 0x384276AF)
        }
        border = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(0x5A89C4EE, 0x247E74C7, 0x487BC5ED), null, Shader.TileMode.CLAMP)
    }

    private fun light(x: Float, y: Float, rx: Float, ry: Float, color: Int): Shader =
        RadialGradient(0f, 0f, 1f, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(rect.width() * rx, rect.height() * ry)
                postTranslate(rect.left + rect.width() * x, rect.top + rect.height() * y)
            })
        }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty || opacity == 0) return
        val save = canvas.save()
        canvas.clipPath(clip)
        paint.colorFilter = filter
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = opacity
        if (kind == Kind.BAR) {
            paint.shader = barBase; canvas.drawRect(rect, paint)
            paint.shader = barViolet; canvas.drawRect(rect, paint)
            paint.shader = barBlue; canvas.drawRect(rect, paint)
        } else {
            paint.shader = nebula
            canvas.drawRect(rect, paint)
            // The art remains visible while labels and dialog content sit on a quiet dark surface.
            paint.shader = null
            paint.color = UiStyle.BACKGROUND
            paint.alpha = (if (kind == Kind.PANEL) 150 else 35) * opacity / 255
            canvas.drawRect(rect, paint)
        }
        if (kind == Kind.PANEL) {
            paint.color = Color.WHITE
            paint.alpha = opacity
            paint.shader = border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = .7f * density
            val r = (radius - .5f * density).coerceAtLeast(0f)
            canvas.drawRoundRect(edge, r, r, paint)
        }
        canvas.restoreToCount(save)
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, min(radius, min(rect.width(), rect.height()).coerceAtLeast(0f) / 2f))
    }
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        @Volatile private var cachedArtwork: Bitmap? = null
        fun artwork(context: Context): Bitmap = cachedArtwork ?: synchronized(this) {
            cachedArtwork ?: requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.cosmic_nebula,
                BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }))
                .also { cachedArtwork = it }
        }
    }
}
