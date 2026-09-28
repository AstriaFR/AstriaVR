package dev.astriavr.player

import android.app.Activity
import android.os.Build

/** Keep API 33 classes out of the Activity's signatures on Android 9–12. */
internal interface BackNavigation {
    fun setEnabled(enabled: Boolean)

    companion object {
        fun create(activity: Activity, action: () -> Unit): BackNavigation =
            if (Build.VERSION.SDK_INT >= 33) Api33BackNavigation(activity, action)
            else object : BackNavigation { override fun setEnabled(enabled: Boolean) = Unit }
    }
}

@android.annotation.TargetApi(33)
private class Api33BackNavigation(activity: Activity, action: () -> Unit) : BackNavigation {
    private val dispatcher = activity.onBackInvokedDispatcher
    private val callback = android.window.OnBackInvokedCallback { action() }
    private var registered = false
    override fun setEnabled(enabled: Boolean) {
        if (registered == enabled) return
        if (enabled) dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        else dispatcher.unregisterOnBackInvokedCallback(callback)
        registered = enabled
    }
}
