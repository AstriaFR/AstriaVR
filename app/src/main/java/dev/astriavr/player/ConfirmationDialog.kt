package dev.astriavr.player

import android.app.Activity

/** Confirmation copy and settings forms use the same adaptive dialog implementation. */
class ConfirmationDialog(activity: Activity, heading: String, message: String,
    confirmLabel: String, confirmed: () -> Unit) : SettingsDialog(activity, heading,
    UiStyle.label(activity, message, 14f, UiStyle.MUTED).apply {
        setLineSpacing(UiStyle.dp(activity, 5).toFloat(), 1f)
    }, listOf(Action(AppText.CANCEL.text()), Action(confirmLabel, true, accepted = confirmed)))
