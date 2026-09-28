package dev.astriavr.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.text.format.DateFormat
import android.view.View

/** Fixed footprint: system broadcasts update only these pixels, never the title's layout. */
class DeviceStatusView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val clockInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiStyle.TEXT; textSize = 12f * density; typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val batteryInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * density; typeface = Typeface.MONOSPACE
    }
    private var active = false
    private var attached = false
    private var registered = false
    private var clock = "--:--"
    private var percent = -1
    private var charging = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh(intent) }
    }

    fun setActive(value: Boolean) { active = value; syncListening() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        syncListening()
    }

    override fun onDetachedFromWindow() {
        attached = false
        syncListening()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        syncListening()
    }

    @Suppress("DEPRECATION")
    private fun syncListening() {
        val needed = attached && active && isShown && windowVisibility == VISIBLE
        if (needed == registered) return
        if (!needed) {
            context.unregisterReceiver(receiver)
            registered = false
            return
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        val sticky = if (Build.VERSION.SDK_INT >= 33)
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        registered = true
        refresh(sticky)
    }

    private fun refresh(intent: Intent?) {
        val nextClock = DateFormat.format("HH:mm", System.currentTimeMillis()).toString()
        var nextPercent = percent
        var nextCharging = charging
        if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            nextPercent = if (level >= 0 && scale > 0) ((level.toLong() * 100 / scale).toInt()).coerceIn(0, 100) else -1
            nextCharging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        }
        if (clock == nextClock && percent == nextPercent && charging == nextCharging) return
        clock = nextClock; percent = nextPercent; charging = nextCharging
        contentDescription = AppText.TIME_BATTERY.text(clock, if (percent < 0) AppText.UNKNOWN.text() else "${percent}%", if (charging) AppText.CHARGING.text() else "")
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        fun baseline(ink: Paint, center: Float) = center - (ink.ascent() + ink.descent()) / 2f
        canvas.drawText(clock, width / 2f, baseline(clockInk, height * .30f), clockInk)
        val label = if (percent < 0) "--%" else "${percent}%"
        val iconWidth = 13f * density
        val gap = 4f * density
        val left = (width - iconWidth - gap - batteryInk.measureText(label)) / 2f
        val center = height * .72f
        val top = center - 3.5f * density
        batteryInk.color = if (charging) UiStyle.ACCENT else if (percent in 0..15) UiStyle.DANGER else UiStyle.MUTED
        batteryInk.style = Paint.Style.STROKE; batteryInk.strokeWidth = density
        canvas.drawRoundRect(left, top, left + 11f * density, top + 7f * density, density, density, batteryInk)
        batteryInk.style = Paint.Style.FILL
        canvas.drawRect(left + 12f * density, top + 2f * density, left + iconWidth, top + 5f * density, batteryInk)
        if (percent > 0) canvas.drawRect(left + 1.5f * density, top + 1.5f * density,
            left + (1.5f + 8f * percent / 100f) * density, top + 5.5f * density, batteryInk)
        canvas.drawText(label, left + iconWidth + gap, baseline(batteryInk, center), batteryInk)
    }
}
