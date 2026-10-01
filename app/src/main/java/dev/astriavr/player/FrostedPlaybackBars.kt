package dev.astriavr.player

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

/** One tiny shared preview for both bars. Never captures the controls or alters the video surface. */
internal class FrostedPlaybackBars(
    private val root: View,
    bars: List<View>,
    private val eligible: () -> Boolean,
    private val source: () -> SurfaceView?,
    private val contentId: () -> Any?,
    private val position: () -> Long,
    private val moving: () -> Boolean,
) {
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("Playback glass", android.os.Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val worker = Handler(thread.looper)
    private val layers = bars.map { view -> GlassLayer(view).also { view.background = it } }
    private var frame: Bitmap? = null
    private val frameArea = RectF()
    private var previous: Snapshot? = null
    private var active = false
    private var closed = false
    private var busy = false
    private var generation = 0
    private var nextAttempt = 0L
    private val refreshTask = Runnable { refresh() }

    private data class Snapshot(val surface: SurfaceView?, val id: Any?, val position: Long,
        val width: Int, val height: Int, val area: RectF)

    fun start() { if (!closed) { active = true; refresh() } }

    fun invalidateContent() {
        generation++
        clearFrame()
        nextAttempt = 0
        refresh()
    }

    fun stop() {
        active = false
        generation++
        main.removeCallbacks(refreshTask)
        clearFrame()
    }

    fun close() {
        stop()
        closed = true
        // Finish any copy/blur already in flight; its generation can no longer be published.
        thread.quitSafely()
    }

    private fun clearFrame() {
        val changed = frame != null
        frame = null; previous = null
        if (changed) layers.forEach { it.invalidateSelf() }
        // Published bitmaps may still be referenced by RenderThread; let GC retire them safely.
    }

    fun refresh() {
        main.removeCallbacks(refreshTask)
        if (closed || !active || !eligible() || root.width <= 0 || root.height <= 0) {
            if (previous != null) { generation++; clearFrame() }
            return
        }
        if (busy) return
        val now = SystemClock.uptimeMillis()
        if (now < nextAttempt) { main.postDelayed(refreshTask, nextAttempt - now); return }
        val surface = source()
        val area = if (surface == null) RectF(0f, 0f, root.width.toFloat(), root.height.toFloat()) else {
            if (!surface.isShown || !surface.holder.surface.isValid || surface.width <= 0 || surface.height <= 0) {
                clearFrame(); nextAttempt = now + 750; main.postDelayed(refreshTask, 750); return
            }
            val origin = IntArray(2); val location = IntArray(2)
            root.getLocationOnScreen(origin); surface.getLocationOnScreen(location)
            RectF((location[0] - origin[0]).toFloat(), (location[1] - origin[1]).toFloat(),
                location[0] - origin[0] + surface.width * surface.scaleX,
                location[1] - origin[1] + surface.height * surface.scaleY)
        }
        val snapshot = Snapshot(surface, contentId(), if (surface == null) 0 else position(), root.width, root.height, area)
        if (previous == snapshot && (surface == null || !moving())) return
        if (previous?.surface !== surface || previous?.id != snapshot.id) clearFrame()
        val token = generation
        val w = surface?.width ?: root.width
        val h = surface?.height ?: root.height
        val ratio = 192f / max(w, h)
        val small = Bitmap.createBitmap(max(1, (w * ratio).roundToInt()), max(1, (h * ratio).roundToInt()), Bitmap.Config.ARGB_8888)
        busy = true
        nextAttempt = now + 200 // At most five shared samples/second, only while the bars are visible.
        if (surface == null) {
            // Idle background uses the identical full-screen crop, with no screenshot or UI feedback.
            val backdrop = StarfieldDrawable(root.context, StarfieldDrawable.Kind.PAGE)
            backdrop.setBounds(0, 0, w, h)
            worker.post {
                val ok = runCatching {
                    val canvas = Canvas(small)
                    canvas.scale(small.width.toFloat() / w, small.height.toFloat() / h)
                    backdrop.draw(canvas)
                    blur(small)
                }.isSuccess
                main.post { finish(token, snapshot, small, ok) }
            }
        } else {
            try {
                PixelCopy.request(surface, small, { result ->
                    val ok = result == PixelCopy.SUCCESS && runCatching { blur(small) }.isSuccess
                    main.post { finish(token, snapshot, small, ok) }
                }, worker)
            } catch (_: IllegalArgumentException) {
                finish(token, snapshot, small, false)
            }
        }
    }

    private fun blur(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        GlassBlur.apply(pixels, bitmap.width, bitmap.height, 3)
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun finish(token: Int, snapshot: Snapshot, bitmap: Bitmap, ok: Boolean) {
        busy = false
        if (closed || !active || token != generation || !eligible() || source() !== snapshot.surface ||
            contentId() != snapshot.id || root.width != snapshot.width || root.height != snapshot.height) {
            bitmap.recycle()
            if (!closed && active) main.post(refreshTask)
            return
        }
        if (ok) {
            frame = bitmap; frameArea.set(snapshot.area); previous = snapshot
            layers.forEach { it.invalidateSelf() }
        } else {
            bitmap.recycle(); clearFrame()
            nextAttempt = SystemClock.uptimeMillis() + 1500 // Unsupported/empty surfaces keep the translucent fallback.
        }
        if (!ok || snapshot.surface != null && moving())
            main.postDelayed(refreshTask, (nextAttempt - SystemClock.uptimeMillis()).coerceAtLeast(1))
    }

    private inner class GlassLayer(private val owner: View) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val origin = IntArray(2)
        private val location = IntArray(2)
        private val target = RectF()
        private var opacity = 255
        private var tint: Shader? = null

        override fun onBoundsChange(bounds: Rect) {
            if (bounds.isEmpty) return
            tint = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
                intArrayOf(0xAF19254A.toInt(), 0xAD0A1733.toInt()), null, Shader.TileMode.CLAMP)
        }

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val save = canvas.save()
            canvas.clipRect(bounds)
            val image = frame
            if (image != null) {
                root.getLocationOnScreen(origin); owner.getLocationOnScreen(location)
                val x = (origin[0] - location[0]).toFloat()
                val y = (origin[1] - location[1]).toFloat()
                target.set(frameArea); target.offset(x, y)
                paint.shader = null
                // Frost scatters most of the detail; a little of the real scene remains transmissive.
                paint.alpha = opacity * 235 / 255
                canvas.drawBitmap(image, null, target, paint)
            }
            paint.alpha = opacity
            paint.shader = tint
            canvas.drawRect(bounds, paint)
            paint.shader = null
            paint.color = 0x38C6DAFF
            paint.alpha = 56 * opacity / 255
            val edge = if (owner.top > root.height / 2) bounds.top.toFloat() else bounds.bottom - root.resources.displayMetrics.density
            canvas.drawRect(bounds.left.toFloat(), edge, bounds.right.toFloat(), edge + root.resources.displayMetrics.density, paint)
            paint.color = Color.WHITE
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
