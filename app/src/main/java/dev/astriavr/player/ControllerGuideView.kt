package dev.astriavr.player

import android.content.Context
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout

/** A single inline guide, with native dp labels beside a vector line drawing. */
class ControllerGuideView(context: Context) : LinearLayout(context) {
    private data class Action(val key: String, val title: String, val detail: String)
    private var phoneMode = false
    private var compact: Boolean? = null

    init { orientation = VERTICAL }

    fun setMode(phone: Boolean) {
        if (phoneMode == phone) return
        phoneMode = phone
        compact?.let { rebuild(it) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val narrow = MeasureSpec.getSize(widthMeasureSpec) < dp(620)
        if (compact != narrow) { compact = narrow; rebuild(narrow) }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun rebuild(narrow: Boolean) {
        removeAllViews()
        val left = listOf(
            Action("LT", AppText.PLAY_PAUSE.text(), AppText.PRESS_ONCE_TO_TOGGLE.text()),
            Action("LB / RB", AppText.SPEED.text(), AppText.AT_THE_LIMIT_DOUBLE_PRESS_THE.text()),
            Action(AppText.LEFT_STICK.text(), AppText.LOOK_AROUND.text(), AppText.PUSH_GENTLY_FOR_FINE_CONTROL_FULLY.text()),
            Action(AppText.D_PAD.text(), AppText.SEEK_FIELD_OF_VIEW.text(), AppText.REWIND_FORWARD_N_FOV_FOV.text())
        )
        val right = listOf(
            Action("A", AppText.RECENTER.text(), AppText.SET_THE_CURRENT_DIRECTION_AS_FORWARD.text()),
            Action("B", AppText.GYROSCOPE.text(), AppText.TOGGLE_HEAD_TRACKING.text()),
            Action("X / Y", if (phoneMode) AppText.IPD_NOT_USED.text() else AppText.IPD.text(), if (phoneMode) AppText.NO_IPD_ADJUSTMENT_IN_SINGLE_VIEW.text() else AppText.CM_PER_STEP_HOLD_TO_REPEAT.text()),
            Action(AppText.RIGHT_STICK.text(), AppText.BRIGHTNESS_VOLUME.text(), AppText.BRIGHTNESS_N_VOLUME.text())
        )
        val drawing = ImageView(context).apply {
            setImageResource(R.drawable.controller_sketch)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = AppText.CONTROLLER_DIAGRAM_LEFT_STICK_AT_UPPER.text()
        }
        fun column(items: List<Action>) = LinearLayout(context).apply {
            orientation = VERTICAL
            for (item in items) {
                val card = LinearLayout(context).apply {
                    orientation = VERTICAL
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    background = UiStyle.shape(context, 0x68203A68, 10)
                }
                card.addView(UiStyle.label(context, "${item.key}  ·  ${item.title}", 12f, UiStyle.TEXT, true))
                card.addView(UiStyle.label(context, item.detail, 11f, UiStyle.MUTED).apply {
                    setLineSpacing(dp(2).toFloat(), 1f)
                }, LayoutParams(-1, -2).apply { topMargin = dp(5) })
                addView(card, LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            }
        }
        val body = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        if (narrow) addView(drawing, LayoutParams(-1, dp(180)))
        body.addView(column(left), LayoutParams(0, -2, 1f).apply { marginEnd = dp(if (narrow) 6 else 8) })
        if (!narrow) body.addView(drawing, LayoutParams(0, dp(238), 1.55f))
        body.addView(column(right), LayoutParams(0, -2, 1f).apply { marginStart = dp(if (narrow) 6 else 8) })
        addView(body, LayoutParams(-1, -2))
        val selection = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = UiStyle.shape(context, 0x8C203B70.toInt(), 12, UiStyle.LINE)
        }
        selection.addView(UiStyle.label(context, if (phoneMode) AppText.PLAYLIST_CHOOSE_A_VIDEO.text() else AppText.MENU_START_VR_PLAYLIST.text(), 13f, UiStyle.ACCENT, true))
        selection.addView(UiStyle.label(context,
            if (phoneMode) AppText.TAP_THE_PLAYLIST_ICON_IN_THE.text()
            else AppText.MENU_OPEN_CLOSE_SELECT_RESUME.text(), 11f, UiStyle.TEXT),
            LayoutParams(-1, -2).apply { topMargin = dp(6) })
        addView(selection, LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
    private fun dp(value: Int) = UiStyle.dp(context, value)
}
