package dev.astriavr.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View

/** Fixed-size status line: bitrate updates invalidate pixels, never the title's layout. */
class PlaybackInfoView(context: Context) : View(context) {
    private val ink = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * resources.displayMetrics.density
        color = 0xC9C2D4DE.toInt()
    }
    private val rateInk = TextPaint(ink).apply { typeface = Typeface.MONOSPACE }
    private val gap = 6f * resources.displayMetrics.density
    private val rateWidth = rateInk.measureText(AppText.BITRATE_MBPS.text())
    private var detail = ""
    private var rate = AppText.BITRATE.text()
    private var showRate = false
    private var drawnDetail = ""
    private var detailLeft = 0f
    private var rateLeft = 0f

    fun setDetail(value: String, showBitrate: Boolean) {
        if (detail == value && showRate == showBitrate) return
        detail = value; showRate = showBitrate
        arrange()
        describe()
        invalidate()
    }

    fun setBitrate(value: String) {
        if (rate == value) return
        rate = value
        describe()
        invalidate()
    }

    private fun describe() {
        val description = if (showRate) "${detail} · ${rate}" else detail
        contentDescription = description
        tooltipText = description
    }

    private fun arrange() {
        val reserved = if (showRate) rateWidth + gap else 0f
        val available = (width - reserved).coerceAtLeast(0f)
        drawnDetail = TextUtils.ellipsize(detail, ink, available, TextUtils.TruncateAt.MIDDLE).toString()
        val detailWidth = ink.measureText(drawnDetail)
        detailLeft = ((width - detailWidth - reserved) / 2f).coerceAtLeast(0f)
        rateLeft = detailLeft + detailWidth + if (showRate) gap else 0f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { arrange() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val baseline = (height - ink.ascent() - ink.descent()) / 2f
        canvas.drawText(drawnDetail, detailLeft, baseline, ink)
        if (showRate) canvas.drawText(rate, rateLeft, baseline, rateInk)
    }
}
