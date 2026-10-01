package dev.astriavr.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/** Runs before ordinary playback input. A held selection key cannot leak into video controls. */
class PlaylistInput(private val onMenu: () -> Unit, private val onNavigate: (Int) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val states = mutableMapOf<Int, PlaylistNavigation>()
    private val menuHeld = mutableSetOf<Pair<Int, Int>>()
    private var suppressUntil = 0L
    private var repeatScheduled = false
    var open = false
        private set
    private val repeat = object : Runnable {
        override fun run() {
            repeatScheduled = false
            if (!open) return
            val now = SystemClock.uptimeMillis()
            for (state in states.values.toList()) fire(state.repeat(now))
            scheduleRepeat()
        }
    }
    fun setOpen(value: Boolean) {
        open = value
        if (!value) suppressUntil = SystemClock.uptimeMillis() + 250
        handler.removeCallbacks(repeat)
        repeatScheduled = false
        // Keep held directions until their releases arrive, even after selecting a video.
        states.entries.removeAll { it.value.isNeutral }
    }
    fun clear() { handler.removeCallbacks(repeat); repeatScheduled = false; states.clear(); menuHeld.clear(); suppressUntil = 0 }
    fun handleKey(event: KeyEvent, allowMenu: Boolean): Boolean {
        val menu = event.keyCode == KeyEvent.KEYCODE_BUTTON_START || event.keyCode == KeyEvent.KEYCODE_MENU
        if (menu && allowMenu) {
            val key = event.deviceId to event.keyCode
            if (event.action == KeyEvent.ACTION_UP || event.isCanceled) menuHeld.remove(key)
            else if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && menuHeld.add(key)) onMenu()
            return true
        }
        val blocked = states[event.deviceId]?.isNeutral == false
        if (!open && !blocked && SystemClock.uptimeMillis() >= suppressUntil) return false
        val action = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> PlaylistNavigation.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> PlaylistNavigation.RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> PlaylistNavigation.CONFIRM
            else -> 0
        }
        if (action != 0) {
            if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
                val state = states.getOrPut(event.deviceId) { PlaylistNavigation() }
                fire(state.key(action, event.action == KeyEvent.ACTION_DOWN && !event.isCanceled, event.repeatCount > 0, event.eventTime))
                if (!open && state.isNeutral) states.remove(event.deviceId)
                scheduleRepeat()
            }
            return true
        }
        return open && (event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN || event.device?.supportsSource(InputDevice.SOURCE_GAMEPAD) == true)
    }
    fun handleMotion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        if (!open && states[event.deviceId]?.isNeutral != false && SystemClock.uptimeMillis() >= suppressUntil) return false
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) { states.remove(event.deviceId); return true }
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return true
        val state = states.getOrPut(event.deviceId) { PlaylistNavigation() }
        // After confirmation, keep consuming the rest of this batched motion event.
        for (i in 0 until event.historySize) fire(state.hat(event.getHistoricalAxisValue(MotionEvent.AXIS_HAT_X, i),
            event.getHistoricalAxisValue(MotionEvent.AXIS_HAT_Y, i), event.getHistoricalEventTime(i)))
        fire(state.hat(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y), event.eventTime))
        if (!open && state.isNeutral) states.remove(event.deviceId)
        scheduleRepeat()
        return true
    }
    private fun fire(action: Int) { if (open && action != 0) onNavigate(action) }
    private fun scheduleRepeat() {
        if (!open || states.values.none { it.hasRepeat() }) {
            handler.removeCallbacks(repeat); repeatScheduled = false
        } else if (!repeatScheduled) {
            repeatScheduled = true
            handler.postDelayed(repeat, 40)
        }
    }
}
