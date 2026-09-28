package dev.astriavr.player

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView

/** Shared visual language for playback, library, settings and controller help. */
object UiStyle {
    const val BACKGROUND = 0xFF071126.toInt()
    const val SURFACE = 0xD20C1935.toInt()
    const val RAISED = 0xDC1B2B56.toInt()
    const val MODAL = 0xEE0D1A36.toInt()
    const val LINE = 0xFF4A6394.toInt()
    const val TEXT = 0xFFF4F8FF.toInt()
    const val MUTED = 0xFFB5C7E2.toInt()
    const val ACCENT = 0xFF87D9FF.toInt()
    const val DANGER = 0xFFFFA7A7.toInt()
    val easing = PathInterpolator(.2f, 0f, 0f, 1f)
    fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density * value + .5f).toInt()
    /** Static translucent surfaces for text-heavy pages; no video sampling behind every card. */
    fun glass(context: Context, color: Int = SURFACE, radius: Int = 16, border: Int = 0x547499C9) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            Color.argb(Color.alpha(color), (Color.red(color) + 9).coerceAtMost(255),
                (Color.green(color) + 10).coerceAtMost(255), (Color.blue(color) + 14).coerceAtMost(255)), color)).apply {
            cornerRadius = dp(context, radius).toFloat()
            if (border != 0) setStroke(dp(context, 1), border)
        }
    fun shape(context: Context, color: Int = SURFACE, radius: Int = 16, border: Int = 0): GradientDrawable =
        if (color == SURFACE || color == RAISED) glass(context, color, radius, if (border != 0) border else 0x547499C9)
        else GradientDrawable().apply {
            setColor(color); cornerRadius = dp(context, radius).toFloat()
            if (border != 0) setStroke(dp(context, 1), border)
        }
    fun ripple(context: Context, color: Int = RAISED, radius: Int = 14) = RippleDrawable(
        ColorStateList.valueOf(0x3087D9FF), shape(context, color, radius), shape(context, -1, radius))
    fun stars(context: Context, kind: StarfieldDrawable.Kind = StarfieldDrawable.Kind.PAGE, radius: Int = 0) =
        StarfieldDrawable(context, kind, radius)
    fun label(context: Context, value: String, size: Float, color: Int = TEXT, bold: Boolean = false) = TextView(context).apply {
        text = value; setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, size); setTextColor(color); includeFontPadding = false
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }
    fun icon(context: Context, symbol: String, label: String, backgroundColor: Int = RAISED, click: () -> Unit) = ImageButton(context).apply {
        setImageDrawable(PlayerIcon(symbol)); contentDescription = label; tooltipText = label
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
        background = ripple(context, backgroundColor)
        setOnClickListener { click() }
    }
    fun duration(ms: Long) = if (ms >= 3600000) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", ms / 3600000, ms / 60000 % 60, ms / 1000 % 60)
        else String.format(java.util.Locale.ROOT, "%d:%02d", ms / 60000, ms / 1000 % 60)
    fun motionDuration(ms: Long) = if (ValueAnimator.areAnimatorsEnabled()) ms else 0L
    fun appear(view: View, distance: Float = 10f) {
        view.animate().cancel(); view.alpha = 0f; view.translationY = distance
        view.animate().alpha(1f).translationY(0f).setDuration(motionDuration(220)).setInterpolator(easing).start()
    }
}

