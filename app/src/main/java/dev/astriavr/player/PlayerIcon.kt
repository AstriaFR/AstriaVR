package dev.astriavr.player

import android.graphics.*
import android.graphics.drawable.Drawable

/** Duotone 24-unit icons. Geometry is built once, never allocated per frame. */
class PlayerIcon(symbol: String) : Drawable() {
    private data class Layer(val path: Path, val color: Int, val filled: Boolean)
    private val layers = ArrayList<Layer>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private var opacity = 255
    private var filter: ColorFilter? = null
    private val ink = UiStyle.TEXT
    private val accent = UiStyle.ACCENT
    private val soft = 0x3687D9FF
    private fun path(fill: Boolean = false, color: Int = ink, draw: Path.() -> Unit) { layers += Layer(Path().apply(draw), color, fill) }
    private fun line(vararg p: Float, color: Int = ink) = path(color = color) { moveTo(p[0], p[1]); for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1]) }
    private fun circle(x: Float, y: Float, r: Float, fill: Boolean = false, color: Int = ink) = path(fill, color) { addCircle(x, y, r, Path.Direction.CW) }
    private fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float, fill: Boolean = false, color: Int = ink) = path(fill, color) { addRoundRect(RectF(l,t,r,b),radius,radius,Path.Direction.CW) }
    init {
        when (symbol) {
            "grid" -> {
                rect(3f,3f,10f,10f,1.2f,false,accent); rect(14f,3f,21f,10f,1.2f)
                rect(3f,14f,10f,21f,1.2f); rect(14f,14f,21f,21f,1.2f,false,accent)
            }
            "list" -> {
                for(y in floatArrayOf(5f,12f,19f)) { rect(3f,y-2,7f,y+2,1f,true,accent); line(11f,y,21f,y) }
            }
            "play" -> path(true, accent) { moveTo(7f,4.5f); cubicTo(7f,3.8f,7.8f,3.4f,8.4f,3.9f); lineTo(20f,11.1f); quadTo(21.1f,12f,20f,12.9f); lineTo(8.4f,20.1f); quadTo(7f,21f,7f,19.5f); close() }
            "pause" -> { rect(5f,4f,9f,20f,1.3f,true,accent); rect(15f,4f,19f,20f,1.3f,true,accent) }
            "back", "forward" -> {
                val forward = symbol == "forward"
                path(true, accent) { if (forward) { moveTo(3f,5f); lineTo(12f,12f); lineTo(3f,19f) } else { moveTo(21f,5f); lineTo(12f,12f); lineTo(21f,19f) }; close() }
                path(true) { if (forward) { moveTo(12f,5f); lineTo(21f,12f); lineTo(12f,19f) } else { moveTo(12f,5f); lineTo(3f,12f); lineTo(12f,19f) }; close() }
            }
            "open" -> {
                path(true, soft) { moveTo(3f,8f); lineTo(21f,8f); lineTo(18.8f,19f); lineTo(3f,19f); close() }
                path { moveTo(3f,10f); lineTo(3f,6f); quadTo(3f,4f,5f,4f); lineTo(9f,4f); lineTo(12f,7f); lineTo(19f,7f); quadTo(21f,7f,21f,9f); lineTo(18.5f,19f); lineTo(3f,19f); lineTo(5.5f,10f); lineTo(21f,10f) }
            }
            "playlist" -> {
                rect(2.5f,7f,21.5f,21f,2.6f,true,soft); rect(2.5f,7f,21.5f,21f,2.6f)
                line(6f,3f,18f,3f,color=accent)
                path(true,accent) { moveTo(9f,10.5f); lineTo(15.5f,14f); lineTo(9f,17.5f); close() }
            }
            "settings" -> {
                path(true,soft) { for (i in 0..47) { val angle = (i * Math.PI / 24) - Math.PI / 2; val radius = if (i % 6 in 1..4) 10.0 else 8.1; val x=(12+radius*kotlin.math.cos(angle)).toFloat(); val y=(12+radius*kotlin.math.sin(angle)).toFloat(); if(i==0) moveTo(x,y) else lineTo(x,y) }; close() }
                path { for (i in 0..47) { val angle = (i * Math.PI / 24) - Math.PI / 2; val radius = if (i % 6 in 1..4) 10.0 else 8.1; val x=(12+radius*kotlin.math.cos(angle)).toFloat(); val y=(12+radius*kotlin.math.sin(angle)).toFloat(); if(i==0) moveTo(x,y) else lineTo(x,y) }; close() }
                circle(12f,12f,3.5f,color=accent)
            }
            "gyro" -> {
                path(color=accent) { addOval(RectF(2.5f,7f,21.5f,17f),Path.Direction.CW) }
                path { addOval(RectF(7f,2.5f,17f,21.5f),Path.Direction.CW) }
                circle(12f,12f,2.1f,true,accent)
            }
            "center" -> {
                line(3f,8f,3f,3f,8f,3f); line(16f,3f,21f,3f,21f,8f)
                line(3f,16f,3f,21f,8f,21f); line(16f,21f,21f,21f,21f,16f)
                circle(12f,12f,4f,false,accent); circle(12f,12f,1f,true,accent)
            }
            "add" -> { line(12f,4f,12f,20f,color=accent); line(4f,12f,20f,12f,color=accent) }
            "close" -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
            "return" -> { line(10f,4f,2.5f,12f,10f,20f); line(3f,12f,21f,12f) }
            "search" -> { circle(10.5f,10.5f,7f); line(16f,16f,22f,22f,color=accent) }
            "more" -> { circle(5f,12f,1.7f,true); circle(12f,12f,1.7f,true); circle(19f,12f,1.7f,true) }
            "restart" -> { path { arcTo(RectF(4f,4f,20f,20f),-135f,310f,false) }; line(3f,2f,3f,8f,9f,8f,color=accent) }
            "refresh" -> { path { arcTo(RectF(4f,4f,20f,20f),-145f,140f,false) }; path { arcTo(RectF(4f,4f,20f,20f),35f,140f,false) }; line(20f,3f,20f,9f,14f,9f,color=accent); line(4f,21f,4f,15f,10f,15f,color=accent) }
            "remove" -> { circle(12f,12f,9f,false,UiStyle.DANGER); line(7f,12f,17f,12f,color=UiStyle.DANGER) }
            "check" -> line(4f,12f,9f,17f,20f,6f,color=accent)
        }
    }
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat()); canvas.scale(bounds.width()/24f,bounds.height()/24f)
        for (layer in layers) { paint.color=layer.color; paint.alpha=Color.alpha(layer.color)*opacity/255; paint.style=if(layer.filled) Paint.Style.FILL else Paint.Style.STROKE; paint.colorFilter=filter; canvas.drawPath(layer.path,paint) }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { opacity=alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { filter=colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
