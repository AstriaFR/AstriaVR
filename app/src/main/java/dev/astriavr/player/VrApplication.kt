package dev.astriavr.player

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

/** Keep the actual exception locally so a phone without ADB can report it on the next launch. */
class VrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLanguage.load(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            CrashLog.record(this, thread.name, error)
            // Do not resume a failed main/GL thread or hide the fatal error from Android.
            if (previous != null) previous.uncaughtException(thread, error)
            else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(1) }
        }
    }
}

object CrashLog {
    @Volatile var phase = AppText.APPLICATION_STARTUP.text()
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")
    private fun reportHeader(context: Context): String {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        return "AstriaVR ${version}"
    }
    // Keep older logs available from the diagnostic button, but do not redirect a fixed,
    // freshly upgraded build into an old version's error screen on every launch.
    fun hasReport(context: Context): Boolean {
        val report = file(context)
        if (!report.exists()) return false
        // Patch releases can deliberately retain versionName (4.0). Installation time distinguishes them.
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        if (report.lastModified() < installedAt) return false
        return read(context).lineSequence().firstOrNull()?.trim() == reportHeader(context)
    }
    fun read(context: Context): String = runCatching { file(context).readText() }.getOrDefault("")
    fun clear(context: Context) { file(context).delete() }
    fun record(context: Context, thread: String, error: Throwable) {
        runCatching {
            file(context).writeText(AppText.N_N_ANDROID_API_N_PHASE.text(reportHeader(context), Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, phase, thread, java.util.Date(), error.stackTraceToString()).trimIndent())
        }
    }
}
