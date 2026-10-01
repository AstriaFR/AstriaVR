package dev.astriavr.player

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** App language is independent of the phone language and survives upgrades and resets. */
object AppLanguage {
    private const val KEY = "app_language"

    fun load(context: Context) {
        AppText.setEnglish(context.getSharedPreferences("astriavr-settings", Context.MODE_PRIVATE)
            .getString(KEY, "zh") == "en")
    }

    fun select(context: Context, english: Boolean) {
        context.getSharedPreferences("astriavr-settings", Context.MODE_PRIVATE).edit()
            .putString(KEY, if (english) "en" else "zh").apply()
        AppText.setEnglish(english)
    }

    fun configuration() = Configuration().apply {
        fontScale = 1f
        setLocale(if (AppText.isEnglish()) Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE)
    }
}
