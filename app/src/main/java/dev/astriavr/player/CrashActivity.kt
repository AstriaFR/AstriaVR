package dev.astriavr.player

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Only displayed after a recorded failure, or by explicitly opening the diagnostic button. */
@SuppressLint("SetTextI18n")
class CrashActivity : Activity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(newBase)
        applyOverrideConfiguration(AppLanguage.configuration())
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = CrashLog.read(this)
        val systemExits = if (Build.VERSION.SDK_INT >= 30) runCatching {
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                .getHistoricalProcessExitReasons(packageName, 0, 3).joinToString("\n") {
                    "${java.util.Date(it.timestamp)}：reason=${it.reason}, status=${it.status}, ${it.description}"
                }
        }.getOrDefault("") else ""
        val report = (saved.ifBlank { AppText.NO_JAVA_EXCEPTIONS_HAVE_BEEN_RECORDED.text() } + AppText.N_NSYSTEM_EXIT_HISTORY_N.text() + systemExits).trim()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyle.stars(this@CrashActivity)
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        box.addView(UiStyle.label(this, AppText.ASTRIAVR_ERROR_REPORT.text(), 20f, bold = true))
        box.addView(UiStyle.label(this, AppText.COPY_THE_ERROR_REPORT_TO_HELP.text(), 14f, UiStyle.MUTED))
        box.addView(Button(this).apply {
            text = AppText.COPY_ERROR_REPORT.text()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14f); setTextColor(UiStyle.ACCENT); background = UiStyle.ripple(this@CrashActivity)
            setOnClickListener {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("AstriaVR", report))
                Toast.makeText(this@CrashActivity, AppText.COPIED.text(), Toast.LENGTH_SHORT).show()
            }
        })
        box.addView(Button(this).apply {
            text = AppText.TRY_OPENING_THE_PLAYER_AGAIN.text()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14f); setTextColor(UiStyle.ACCENT); background = UiStyle.ripple(this@CrashActivity)
            setOnClickListener {
                CrashLog.clear(this@CrashActivity)
                startActivity(Intent(this@CrashActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
        })
        box.addView(UiStyle.label(this, report, 12f).apply { setTextIsSelectable(true) })
        val scroll = ScrollView(this).apply { addView(box) }
        if (Build.VERSION.SDK_INT >= 30) scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
    }
}

