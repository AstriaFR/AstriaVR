package dev.astriavr.player

import android.app.Activity
import android.app.Dialog
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.*

/** All settings dialogs share a scrollable body and an always-accessible action footer. */
open class SettingsDialog(
    private val activity: Activity,
    private val heading: String,
    private val content: View,
    private val actions: List<Action>,
    private val cancellable: Boolean = true,
    private val scrollContent: Boolean = true
) : Dialog(activity, R.style.ConfirmationTheme) {
    data class Action(val label: String, val primary: Boolean = false,
        val validate: () -> Boolean = { true }, val accepted: () -> Unit = {})
    private var consumed = false
    private var maxBodyHeight = dp(220)
    private lateinit var card: LinearLayout
    private lateinit var title: TextView
    private lateinit var body: ViewGroup
    private lateinit var footer: LinearLayout
    private val visibleFrame = Rect()
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastTitleHeight = -1
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { fitWindow() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(cancellable); setCanceledOnTouchOutside(cancellable)
        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = UiStyle.glass(context, UiStyle.MODAL, 22)
        }
        title = UiStyle.label(context, heading, if (scrollContent) 20f else 18f, bold = true).apply {
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            maxLines = 2
        }
        card.addView(title, LinearLayout.LayoutParams(-1, -2))
        body = if (scrollContent) object : ScrollView(context) {
            override fun onMeasure(w: Int, h: Int) {
                val cap = if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) maxBodyHeight
                    else minOf(maxBodyHeight, MeasureSpec.getSize(h))
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport = false; addView(content) } else object : FrameLayout(context) {
            override fun onMeasure(w: Int, h: Int) {
                val cap = if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) maxBodyHeight
                    else minOf(maxBodyHeight, MeasureSpec.getSize(h))
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
            }
        }.apply { addView(content) }
        // Weight lets native dialog/IME measurement shrink the body before touching the footer.
        card.addView(body, LinearLayout.LayoutParams(-1, -2, 1f).apply { topMargin = dp(14) })
        footer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun button(action: Action) = Button(context).apply {
            text = action.label; isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            setTextColor(if (action.primary) UiStyle.BACKGROUND else UiStyle.TEXT)
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            includeFontPadding = false; setPadding(dp(8), 0, dp(8), 0)
            stateListAnimator = null; elevation = 0f; backgroundTintList = null
            background = UiStyle.ripple(context, if (action.primary) UiStyle.ACCENT else UiStyle.RAISED, 12)
            setOnClickListener {
                if (!consumed && action.validate()) { consumed = true; dismiss(); action.accepted() }
            }
        }
        fun row(items: List<Action>) = LinearLayout(context).apply {
            items.forEachIndexed { i, action ->
                addView(button(action), LinearLayout.LayoutParams(0, dp(if (scrollContent) 48 else 40), 1f).apply { if (i > 0) marginStart = dp(10) })
            }
        }
        if (actions.size > 2) {
            footer.addView(row(actions.take(2)))
            footer.addView(row(actions.drop(2)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        } else footer.addView(row(actions))
        card.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        setContentView(card)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    override fun onStart() {
        super.onStart()
        window?.decorView?.viewTreeObserver?.addOnGlobalLayoutListener(layoutListener)
        fitWindow()
    }
    override fun onStop() {
        window?.decorView?.viewTreeObserver?.removeOnGlobalLayoutListener(layoutListener)
        super.onStop()
    }
    @Suppress("DEPRECATION")
    fun fitWindow() {
        if (!::body.isInitialized) return
        val decor = activity.window.decorView
        decor.getWindowVisibleDisplayFrame(visibleFrame)
        val metrics = context.resources.displayMetrics
        val width = minOf(decor.width.takeIf { it > 0 } ?: metrics.widthPixels,
            visibleFrame.width().takeIf { it > 0 } ?: metrics.widthPixels)
        val height = minOf(decor.height.takeIf { it > 0 } ?: metrics.heightPixels,
            visibleFrame.height().takeIf { it > 0 } ?: metrics.heightPixels)
        if (width == lastWidth && height == lastHeight && title.measuredHeight == lastTitleHeight) return
        lastWidth = width; lastHeight = height; lastTitleHeight = title.measuredHeight
        val compact = height < dp(if (scrollContent) 240 else 300)
        val padding = dp(if (compact) 12 else if (scrollContent) 20 else 16)
        card.setPadding(padding, padding, padding, padding)
        title.visibility = if (height < dp(180)) View.GONE else View.VISIBLE
        val bodyGap = dp(if (!scrollContent) 6 else if (compact) 8 else 14)
        val footerGap = dp(if (!scrollContent) 6 else if (compact) 8 else 16)
        (body.layoutParams as LinearLayout.LayoutParams).topMargin = bodyGap
        (footer.layoutParams as LinearLayout.LayoutParams).topMargin = footerGap
        val footerHeight = dp(if (actions.size > 2) 104 else if (scrollContent) 48 else 40)
        val titleHeight = if (title.visibility == View.GONE) 0 else maxOf(dp(if (scrollContent) 28 else 24), title.measuredHeight)
        maxBodyHeight = (height - dp(if (compact) 16 else if (scrollContent) 32 else 24) - padding * 2 - titleHeight - bodyGap - footerGap - footerHeight)
            .coerceAtLeast(dp(12))
        body.requestLayout(); footer.requestLayout()
        window?.apply {
            setLayout(minOf(dp(480), (width - dp(24)).coerceAtLeast(1)), WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER); setDimAmount(.64f)
            if (Build.VERSION.SDK_INT >= 30) decorView.windowInsetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else decorView.systemUiVisibility = activity.window.decorView.systemUiVisibility
        }
    }
    private fun dp(value: Int) = UiStyle.dp(activity, value)
    companion object {
        fun message(activity: Activity, heading: String, text: String, actions: List<Action>, cancellable: Boolean = true) =
            SettingsDialog(activity, heading, UiStyle.label(activity, text, 14f, UiStyle.MUTED).apply {
                setLineSpacing(UiStyle.dp(activity, 5).toFloat(), 1f)
            }, actions, cancellable)
    }
}
