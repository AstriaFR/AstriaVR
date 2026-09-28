package dev.astriavr.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.media.AudioManager
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.util.Locale
import java.util.concurrent.Executors

/** Local video player with a shared Chinese / English UI catalog. */
@SuppressLint("SetTextI18n")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // Keep framework dialog/list text at its default size as well. This only
        // overrides font scaling for this screen; Android display density is retained.
        applyOverrideConfiguration(AppLanguage.configuration())
    }

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("astriavr-settings", MODE_PRIVATE) }
    private lateinit var playlist: PlaylistStore
    private lateinit var covers: CoverRepository
    private lateinit var playlistPage: PlaylistPage
    private lateinit var vrPlaylist: VrPlaylistView
    private lateinit var playlistInput: PlaylistInput
    private var playlistOpen = false
    private var resumeAfterPlaylist = false
    private var resumeAfterVrPlaylist = false
    @Volatile private var closing = false
    private val compensationPrefs by lazy { getSharedPreferences("lens-compensation-by-band", MODE_PRIVATE) }
    private lateinit var tracker: HeadTracker
    private lateinit var gamepad: GamepadInput
    private lateinit var vrView: VrView
    private lateinit var flatVideo: FlatVideoView
    private var ordinaryVideo = false
    private var hdrEnabled = true
    private lateinit var hdrSwitch: Switch
    private lateinit var hdrInfo: TextView
    private lateinit var ordinaryPictureCard: View
    private var manualViewingChoice = false
    private var vrResumed = true
    private lateinit var logoView: StopLogoView
    private lateinit var topActions: LinearLayout
    private lateinit var recenterButton: ImageButton
    private lateinit var opticsGroup: View
    private lateinit var visualCard: View
    private val panoramaOptions = mutableListOf<View>()
    private lateinit var stereoHint: StereoHintView
    private lateinit var opticsText: TextView
    private lateinit var speedText: TextView
    private lateinit var settingsPage: LinearLayout
    private var settingsOpen = false
    private var languageChanging = false
    private var resumeAfterLanguageChange = false
    private var languagePositionMs = 0L
    private var restoredLanguagePosition: Long? = null
    private lateinit var screenInfo: TextView
    private lateinit var diameterText: TextView
    private lateinit var diameterSeek: SeekBar
    private lateinit var dragSwitch: Switch
    private lateinit var rotationSwitch: Switch
    private lateinit var ordinaryRotationSwitch: Switch
    private lateinit var pinchSwitch: Switch
    private var syncingRotationSwitches = false
    private var detectedScreenApplied = false
    private var customScreenApplied = false
    private var firstSetupShown = false
    private val seekAcceleration = SeekAcceleration()
    private val boundarySeek = BoundarySeekSequence()
    private var adjacentRequest = 0
    private var adjacentPending = false
    private var navigationBack: BackNavigation? = null
    private lateinit var correctionText: TextView
    private lateinit var correctionSeek: SeekBar
    private lateinit var phoneCorrectionText: TextView
    private lateinit var phoneCorrectionSeek: SeekBar
    private lateinit var root: FrameLayout
    private lateinit var idleBackdrop: View
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private var frostedBars: FrostedPlaybackBars? = null
    private lateinit var tuningRow: LinearLayout
    private lateinit var tuningScroll: android.widget.HorizontalScrollView
    private lateinit var detailText: PlaybackInfoView
    private val bitrateMeter = VideoBitrateMeter()
    private var bitrateSampling = false
    private val bitrateTicker = object : Runnable {
        override fun run() {
            syncBitrateSampling()
            if (!bitrateSampling) return
            val rate = bitrateMeter.takeBitsPerSecond()
            detailText.setBitrate(if (rate.isFinite()) String.format(Locale.ROOT, AppText.BITRATE_F_MBPS.text(), rate / 1_000_000.0) else AppText.BITRATE.text())
            handler.postDelayed(this, 1000)
        }
    }
    private lateinit var videoTitle: TextView
    private lateinit var deviceStatus: DeviceStatusView
    private lateinit var debugButton: Button
    private var debugMode = false
    private var confirmationDialog: android.app.Dialog? = null
    private val playbackButtons = mutableListOf<View>()
    private lateinit var playButton: ImageButton
    private lateinit var layoutButton: Button
    private lateinit var projectionButton: Button
    private lateinit var viewingModeButton: Button
    private lateinit var viewingModeCard: LinearLayout
    private lateinit var languageSettingsCard: LinearLayout
    private lateinit var settingsBody: LinearLayout
    private lateinit var settingsLeft: LinearLayout
    private lateinit var settingsColumns: LinearLayout
    private lateinit var controlsCard: View
    private lateinit var ordinaryControlsCard: View
    private lateinit var controllerGuideCard: View
    private lateinit var phoneAspectButton: Button
    private lateinit var phoneShapeButton: Button
    private lateinit var phoneCorrectionPanel: LinearLayout
    private lateinit var ipdGroup: View
    private val doubleLensOptions = mutableListOf<View>()
    private val singleLensOptions = mutableListOf<View>()
    private lateinit var phoneEyeButton: Button
    private lateinit var phoneEyeRow: View
    private lateinit var controllerGuide: ControllerGuideView
    private var projectionMode = 0 // VideoProjection source ID, 0 auto
    private var layoutMode = -1 // -1 auto, otherwise the existing SBS/TB/mono indices
    private var videoFormat: Format? = null
    private var metadataProjection = 0
    private var savedCoverProjection = ""
    private val videoPrefs by lazy { getSharedPreferences("video-projections", MODE_PRIVATE) }
    private lateinit var swapButton: Button
    private lateinit var timeText: TextView
    private lateinit var ipdText: TextView
    private lateinit var gyroButton: ImageButton
    private lateinit var flatResetButton: ImageButton
    private lateinit var seek: SeekBar
    private var player: ExoPlayer? = null
    private var source: Uri? = null
    private var sourceLoading = false
    private var sourceName = AppText.CHOOSE_A_LOCAL_VR_VIDEO.text()
    private var positionMs = 0L
    private var pendingPlay = false
    private var active = false
    private var startupReady = false
    private var draggingSeek = false
    private var controlsVisible = true
    private var noticeView: TextView? = null
    private val hideNotice = Runnable { noticeView?.visibility = View.GONE }
    private var speedIndex = PlaybackTuning.DEFAULT_SPEED
    private var brightness = -1f
    private val persistSettings = Runnable { saveSettings() }
    private var settings = RenderSettings()
    private var decoder = ""
    private var failure: String? = null
    private var renderFailure = false
    private var playIcon = ""
    private var projectionWarning = ""
    private var lastSavedMs = 0L
    private var nameRequest = 0
    private var nameQuery: android.os.CancellationSignal? = null
    private val importCancellation = android.os.CancellationSignal()

    private val ticker = object : Runnable {
        override fun run() {
            if (!active) return
            if (!settingsOpen && confirmationDialog?.isShowing != true && !playlistOpen && !playlistInput.open && !settings.phoneMode && !ordinaryVideo && !firstSetupShown && !prefs.getBoolean("screen_setup_done", false) && root.width > 0 && root.height > 0) {
                firstSetupShown = true
                detectScreenParameters(true)
            }
            updateStatus()
            if (player?.isPlaying == true && SystemClock.elapsedRealtime() - lastSavedMs > 5000) savePosition()
            handler.postDelayed(this, 500)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (CrashLog.hasReport(this)) {
            startActivity(Intent(this, CrashActivity::class.java))
            finish()
            return
        }
        try {
            initializePlayerScreen()
            startupReady = true
            // Restore only a recreated Activity, never autoplay on a fresh launch.
            savedInstanceState?.getString("session_uri")?.let {
                speedIndex = savedInstanceState.getInt("session_speed", PlaybackTuning.DEFAULT_SPEED)
                    .coerceIn(0, 6)
                if (savedInstanceState.getBoolean("language_changed") && !savedInstanceState.getBoolean("session_loading"))
                    restoredLanguagePosition = savedInstanceState.getLong("session_position")
                openSource(Uri.parse(it), if (savedInstanceState.getBoolean("session_loading")) null else savedInstanceState.getLong("session_position"),
                    savedInstanceState.getString("session_name"), autoplay = savedInstanceState.getBoolean("language_resume"))
                if (savedInstanceState.getBoolean("language_changed"))
                    manualViewingChoice = savedInstanceState.getBoolean("session_manual_viewing")
            }
            if (savedInstanceState?.getBoolean("playlist_open") == true) openPlaylistPage()
            if (savedInstanceState?.getBoolean("settings_open") == true) openSettingsPage()
            syncBackCallback()
        } catch (error: Exception) {
            CrashLog.record(this, Thread.currentThread().name, error)
            startActivity(Intent(this, CrashActivity::class.java))
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun initializePlayerScreen() {
        CrashLog.phase = AppText.LOADING_SETTINGS_AND_INITIALIZING_THE_INTERFACE.text()
        debugMode = prefs.getBoolean("debug_mode", false)
        ordinaryVideo = prefs.getBoolean("ordinary_video", false)
        hdrEnabled = Build.VERSION.SDK_INT < 31 || prefs.getBoolean("ordinary_hdr", true)
        if (ordinaryVideo && debugMode) {
            debugMode = false
            prefs.edit().putBoolean("debug_mode", false).apply()
        }
        applyRotationPreference()
        loadProjectionChoice()
        val phoneCorrection = prefs.getInt("phone_rect_compensation_v2", 60)
        settings = RenderSettings(
            Optics.stepIpd(prefs.getFloat("ipd", 6.5f), 0),
            prefs.getInt("layout", 0).coerceIn(0, 2), prefs.getBoolean("swap", false),
            Optics.clampFov(prefs.getInt("fov_8deg", 88)), eyeDiameterCm = Optics.diameter(prefs.getFloat("eye_diameter", 5f)),
            phoneMode = prefs.getBoolean("phone_mode", false),
            phoneFovDegrees = Optics.clampPhoneFov(prefs.getInt("phone_fov", 90)),
            phoneFillScreen = prefs.getBoolean("phone_fill_screen", false),
            phoneElliptical = prefs.getBoolean("phone_elliptical", false),
            phoneWideCorrection = ((phoneCorrection.coerceIn(0, 100) + 2) / 5) * 5)
        detectedScreenApplied = prefs.getBoolean("screen_detected", false)
        customScreenApplied = prefs.getBoolean("screen_custom", false)
        if (detectedScreenApplied || customScreenApplied) {
            val w = prefs.getFloat("screen_width_cm", 0f).toDouble()
            val h = prefs.getFloat("screen_height_cm", 0f).toDouble()
            if (Optics.fits(4f, w, h) && w >= h && w <= 50 && h <= 35) {
                settings = settings.copy(screenWidthCm = w, screenHeightCm = h)
            } else { detectedScreenApplied = false; customScreenApplied = false }
        }
        // Preserve parameters already explicitly chosen in an earlier version.
        if (detectedScreenApplied || customScreenApplied) prefs.edit().putBoolean("screen_setup_done", true).apply()
        settings = settings.fitScreen()
        settings = settings.copy(edgeCorrection = readCompensation(settings.eyeDiameterCm))
        brightness = prefs.getFloat("brightness", -1f).let { if (it.isFinite() && it >= 0f) it.coerceIn(.05f, 1f) else -1f }
        playlist = PlaylistStore(this)
        covers = CoverRepository(this, playlist)
        playlistInput = PlaylistInput(::toggleVrPlaylist) { action ->
            when (action) {
                PlaylistNavigation.LEFT -> vrPlaylist.move(-1)
                PlaylistNavigation.RIGHT -> vrPlaylist.move(1)
                PlaylistNavigation.CONFIRM -> vrPlaylist.selection()?.let { entry ->
                    closeVrPlaylist(false)
                    openSource(Uri.parse(entry.uri), entry.position, entry.name)
                    beginControllerUse()
                }
            }
        }

        tracker = HeadTracker(this) { windowManager.defaultDisplay.rotation }
        tracker.setGyroEnabled(prefs.getBoolean("gyro_enabled", false))
        gamepad = GamepadInput(this, ::onControllerAction, { yaw, pitch ->
            if (!ordinaryVideo) { beginControllerUse(); tracker.moveView(yaw, pitch) }
        }, ::adjustFromController, { speedIndex }, { playlistInput.clear(); syncBackCallback() })
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        vrView = VrView(this, tracker).apply {
            vrRenderer.settings = settings
            dragEnabled = prefs.getBoolean("drag_enabled", true)
            onDrag = { yaw, pitch -> tracker.moveView(yaw, pitch) }
            onTap = { resetSeekSequence(); showControls(!controlsVisible) }
            onDoubleTap = {
                if (source != null) { togglePlayback() }
            }
            onSeekTap = { direction, startedAt ->
                if (source != null && !debugMode) {
                    val elapsedStart = if (startedAt < 0) -1L else
                        SystemClock.elapsedRealtime() - (SystemClock.uptimeMillis() - startedAt)
                    seekStep(direction, screenDoubleStartedMs = elapsedStart)
                }
            }
            onOutput = { output ->
                if (active && !ordinaryVideo && vrRenderer.output === output) {
                    player?.setVideoSurface(output.surface) ?: ensurePlayer()
                }
            }
            onOutputRetired = { retired ->
                player?.clearVideoSurface(retired.surface)
                retired.release()
            }
            onError = error@ { message ->
                if (ordinaryVideo && !debugMode) return@error
                if (active) {
                    renderFailure = true
                    failure = message
                    releasePlayer()
                    showControls(true)
                    updateStatus()
                }
            }
        }
        root.addView(vrView, FrameLayout.LayoutParams(-1, -1))
        flatVideo = FlatVideoView(this).apply {
            visibility = View.GONE
            zoomEnabled = prefs.getBoolean("flat_pinch_enabled", true)
            onVideoTouch = { event -> vrView.handleVideoTouch(event, width) }
            onTap = { vrView.onTap() }
            onMultiTouchStart = { vrView.cancelPendingGestures() }
            onMultiTouchActive = { vrView.onTouchActive(it) }
        }
        root.addView(flatVideo, FrameLayout.LayoutParams(-1, -1))
        // The theme fills the idle page; opening a video removes this overlay completely.
        idleBackdrop = View(this).apply {
            background = UiStyle.stars(this@MainActivity)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(idleBackdrop, FrameLayout.LayoutParams(-1, -1))
        stereoHint = StereoHintView(this)
        root.addView(stereoHint, FrameLayout.LayoutParams(-1, -1))
        vrPlaylist = VrPlaylistView(this, playlist, covers) { closeVrPlaylist(true) }
        root.addView(vrPlaylist, FrameLayout.LayoutParams(-1, -1))
        buildControls()
        buildSettingsPage()
        applyDebugAppearance()
        root.setOnApplyWindowInsetsListener { _, insets ->
            applyPageInsets(insets)
            insets
        }
        CrashLog.phase = AppText.SHOWING_THE_PLAYER.text()
        setContentView(root)
        // PhoneWindow on the target device dereferences a null DecorView if its
        // InsetsController is requested before setContentView installs the decor.
        configureWindow()
        updateSettings()
        updateStatus()
    }

    private fun applyPageInsets(insets: WindowInsets?) {
        val cutout = insets?.displayCutout
        val left = cutout?.safeInsetLeft ?: 0
        val right = cutout?.safeInsetRight ?: 0
        val top = cutout?.safeInsetTop ?: 0
        val bottom = cutout?.safeInsetBottom ?: 0
        // Apply cached window insets before showing a lazy page, not one frame after its entrance.
        if (::settingsPage.isInitialized) settingsPage.setPadding(dp(22) + left, dp(6) + top, dp(22) + right, bottom)
        if (::playlistPage.isInitialized) playlistPage.setContentPadding(dp(18) + left, dp(8) + top, dp(18) + right, dp(8) + bottom)
    }

    @Suppress("DEPRECATION")
    private fun configureWindow() {
        val decor = window.decorView
        window.attributes = window.attributes.apply {
            screenBrightness = brightness
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            // View's controller can be null before attachment; focus/resume applies it again.
            // Do not use Window.getInsetsController(): its implementation can throw before
            // returning, so a Kotlin ?. on that getter cannot protect against this crash.
            decor.windowInsetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            decor.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun buildControls() {
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            isClickable = true
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = UiStyle.stars(this@MainActivity, StarfieldDrawable.Kind.BAR)
        }
        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        logoView = StopLogoView(this).apply { onStop = ::stopCurrentVideo }
        videoTitle = label("", 16f).apply {
            setTypeface(typeface, Typeface.NORMAL)
            setSingleLine(); gravity = Gravity.CENTER
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isHorizontalFadingEdgeEnabled = true; setFadingEdgeLength(dp(16))
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }
        detailText = PlaybackInfoView(this)
        headings.addView(videoTitle, LinearLayout.LayoutParams(-1, -2))
        headings.addView(detailText, LinearLayout.LayoutParams(-1, dp(16)).apply { topMargin = dp(5) })
        val actions = row().apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        topActions = actions
        fun topAction(icon: String, description: String, action: () -> Unit): ImageButton {
            val control = iconButton(icon, description, action).apply { tooltipText = description; setPadding(dp(8), dp(8), dp(8), dp(8)) }
            actions.addView(control, LinearLayout.LayoutParams(dp(48), dp(48)).apply { if (actions.childCount > 0) marginStart = dp(6) })
            return control
        }
        gyroButton = topAction("gyro", AppText.TOGGLE_GYROSCOPE_B.text(), ::toggleGyro)
        recenterButton = topAction("center", AppText.RECENTER_A.text(), ::recenterNow)
        flatResetButton = topAction("restart", AppText.RESET_THE_STANDARD_VIDEO_S_SIZE.text()) {
            flatVideo.resetTransform()
            stereoHint.showValue(AppText.VIEW_RESET.text())
        }
        topAction("settings", AppText.SETTINGS.text(), ::openSettingsPage)
        topAction("playlist", AppText.PLAYLIST.text(), ::openPlaylistPage)
        topBar.addView(logoView, LinearLayout.LayoutParams(dp(156), dp(48)))
        topBar.addView(headings, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(12); marginEnd = dp(12) })
        deviceStatus = DeviceStatusView(this)
        topBar.addView(deviceStatus, LinearLayout.LayoutParams(dp(54), dp(40)).apply { marginEnd = dp(8) })
        topBar.addView(actions, LinearLayout.LayoutParams(dp(264), dp(48)))
        root.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            isClickable = true
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = UiStyle.stars(this@MainActivity, StarfieldDrawable.Kind.BAR)
        }
        val progressRow = row()
        playButton = iconButton("play", AppText.PLAY.text()) { togglePlayback() }
        playbackButtons.add(playButton)
        progressRow.addView(playButton, LinearLayout.LayoutParams(dp(46), dp(44)))
        progressRow.addView(iconButton("back", AppText.REWIND.text()) { seekStep(-1) }.also { playbackButtons.add(it) }, LinearLayout.LayoutParams(dp(42), dp(44)))
        seek = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(UiStyle.ACCENT)
            thumbTintList = android.content.res.ColorStateList.valueOf(UiStyle.ACCENT)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(UiStyle.LINE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) { resetSeekSequence(); draggingSeek = true }
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val duration = player?.duration ?: C.TIME_UNSET
                        if (duration > 0) {
                            val message = "${time(duration * progress / 1000)} / ${time(duration)}"
                            timeText.text = message
                            stereoHint.showValue(message)
                        }
                    }
                }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    val current = player
                    val duration = current?.duration ?: C.TIME_UNSET
                    if (current != null && duration > 0 && current.isCurrentMediaItemSeekable) {
                        val target = duration * bar.progress / 1000
                        boundarySeek.reset()
                        if (target == 0L) {
                            pendingPlay = false
                            current.pause()
                            boundarySeek.reached(-1, SystemClock.elapsedRealtime())
                        }
                        else if (target >= duration) boundarySeek.reached(1, SystemClock.elapsedRealtime())
                        current.seekTo(target)
                    }
                    draggingSeek = false
                }
            })
        }
        val timeline = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        timeline.addView(seek, LinearLayout.LayoutParams(-1, dp(28)))
        timeText = label("00:00 / --:--", 9f).apply { gravity = Gravity.CENTER; setTextColor(UiStyle.MUTED) }
        timeline.addView(timeText, LinearLayout.LayoutParams(-1, dp(16)))
        progressRow.addView(timeline, LinearLayout.LayoutParams(0, dp(44), 1f))
        progressRow.addView(iconButton("forward", AppText.FORWARD.text()) { seekStep(1) }.also { playbackButtons.add(it) }, LinearLayout.LayoutParams(dp(42), dp(44)))
        tuningRow = row().apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        fun tuningGroup(title: String, decrease: () -> Unit, increase: () -> Unit, onGroup: (View) -> Unit = {}): TextView {
            val group = row().apply { background = UiStyle.shape(this@MainActivity, 0x58234574, 12) }
            group.addView(button("−", decrease).apply { contentDescription = AppText.DECREASE.text(title); setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f); background = UiStyle.ripple(this@MainActivity, Color.TRANSPARENT, 10) }, LinearLayout.LayoutParams(dp(24), dp(44)))
            val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            words.addView(label(title, 8f).apply { gravity = Gravity.CENTER; setTextColor(UiStyle.MUTED) })
            val value = label("", 12f).apply { gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD) }
            words.addView(value)
            group.addView(words, LinearLayout.LayoutParams(0, dp(44), 1f))
            group.addView(button("+", increase).apply { contentDescription = AppText.INCREASE.text(title); setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f); background = UiStyle.ripple(this@MainActivity, Color.TRANSPARENT, 10) }, LinearLayout.LayoutParams(dp(24), dp(44)))
            tuningRow.addView(group, LinearLayout.LayoutParams(dp(84), dp(44)).apply { if (tuningRow.childCount > 0) marginStart = dp(6) })
            onGroup(group)
            return value
        }
        ipdText = tuningGroup(AppText.IPD_CM.text(), { changeIpd(-1) }, { changeIpd(1) }, { ipdGroup = it })
        speedText = tuningGroup(AppText.PLAYBACK_SPEED.text(), { changeSpeed(-1) }, { changeSpeed(1) })
        opticsText = tuningGroup(AppText.FIELD_OF_VIEW.text(), { changeFov(-1) }, { changeFov(1) }, { opticsGroup = it })
        tuningScroll = android.widget.HorizontalScrollView(this).apply {
            isFillViewport = false
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, 0)
            addView(tuningRow, FrameLayout.LayoutParams(-2, dp(44), Gravity.CENTER_VERTICAL))
        }
        val playbackRow = row()
        playbackRow.addView(progressRow, LinearLayout.LayoutParams(0, dp(44), 1f))
        playbackRow.addView(tuningScroll, LinearLayout.LayoutParams(dp(264), dp(44)).apply { marginStart = dp(8) })
        bottomBar.addView(playbackRow, LinearLayout.LayoutParams(-1, dp(44)))
        root.addView(bottomBar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        fun glassButtons(view: View) {
            if (view is ImageButton) view.background = UiStyle.ripple(this, 0x48234574, 14)
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) glassButtons(view.getChildAt(i))
        }
        glassButtons(topBar); glassButtons(bottomBar)
        frostedBars = FrostedPlaybackBars(root, listOf(topBar, bottomBar),
            eligible = { active && hasWindowFocus() && controlsVisible && !settingsOpen && !playlistOpen &&
                !playlistInput.open && topBar.isShown && bottomBar.isShown },
            source = { if (source == null && !debugMode) null
                else if (ordinaryVideo && !debugMode) flatVideo.surfaceView else vrView },
            contentId = { if (debugMode) "debug" else source },
            position = { player?.currentPosition ?: positionMs },
            moving = { debugMode || !ordinaryVideo || player?.isPlaying == true })
    }

    private fun updateTuningLayout() {
        ipdGroup.visibility = if (settings.phoneMode || ordinaryVideo) View.GONE else View.VISIBLE
        opticsGroup.visibility = if (ordinaryVideo) View.GONE else View.VISIBLE
        val width = dp(if (settings.phoneMode || ordinaryVideo) 104 else 84)
        val buttonWidth = dp(if (settings.phoneMode || ordinaryVideo) 28 else 24)
        var visibleCount = 0
        for (i in 0 until tuningRow.childCount) {
            val group = tuningRow.getChildAt(i) as LinearLayout
            val margin = if (group.visibility == View.VISIBLE && visibleCount++ > 0) dp(6) else 0
            val params = group.layoutParams as LinearLayout.LayoutParams
            if (params.width != width || params.marginStart != margin) {
                params.width = width; params.marginStart = margin; group.layoutParams = params
            }
            for (index in intArrayOf(0, 2)) group.getChildAt(index).let { button ->
                if (button.layoutParams.width != buttonWidth) button.layoutParams = button.layoutParams.apply { this.width = buttonWidth }
            }
        }
        val targetWidth = visibleCount * width + (visibleCount - 1).coerceAtLeast(0) * dp(6)
        if (tuningScroll.layoutParams.width != targetWidth) {
            tuningScroll.layoutParams = tuningScroll.layoutParams.apply { this.width = targetWidth }
            tuningScroll.scrollTo(0, 0)
        }
    }

    private fun syncVideoMode() {
        val useVr = !ordinaryVideo || debugMode
        vrView.visibility = if (useVr) View.VISIBLE else View.GONE
        flatVideo.visibility = if (!useVr && source != null) View.VISIBLE else View.GONE
        vrView.tapTarget = if (useVr) vrView else flatVideo
        vrView.dragEnabled = !ordinaryVideo && prefs.getBoolean("drag_enabled", true)
        val runVr = active && useVr
        if (runVr != vrResumed) {
            if (runVr) vrView.onResume() else {
                vrView.queueEvent { vrView.vrRenderer.releaseOnGlThread() }
                vrView.onPause()
            }
            vrResumed = runVr
        }
        if (runVr && !playlistOpen && (source != null || debugMode)) tracker.start() else tracker.stop()
    }

    private fun setViewingMode(mode: Int) = changeViewingMode(mode, automatic = false)

    private fun shouldResumePlayback() = PlaybackPosition.shouldResume(pendingPlay,
        player?.playWhenReady == true, player?.playbackState == Player.STATE_ENDED)

    private fun setHdrEnabled(enabled: Boolean) {
        if (hdrEnabled == enabled) return
        hdrEnabled = enabled
        prefs.edit().putBoolean("ordinary_hdr", enabled).apply()
        hdrSwitch.isChecked = enabled
        if (ordinaryVideo) {
            val resume = shouldResumePlayback()
            // Android 15+ can change HDR headroom without rebuilding or interrupting playback.
            if (Build.VERSION.SDK_INT >= 35) {
                flatVideo.surfaceView.setDesiredHdrHeadroom(if (enabled) 0f else 1f)
            } else {
                releasePlayer()
                pendingPlay = resume
                if (active) ensurePlayer()
            }
        }
        updateHdrInfo()
    }

    private fun hdrUnavailable() {
        if (!ordinaryVideo || hdrEnabled) return
        setHdrEnabled(true)
        notifyUser(AppText.THIS_DECODER_CANNOT_CONVERT_HDR_TO.text())
    }

    @Suppress("DEPRECATION")
    private fun updateHdrInfo() {
        if (!::hdrInfo.isInitialized) return
        val types = windowManager.defaultDisplay.hdrCapabilities.supportedHdrTypes
        val displayNames = types.asSequence().mapNotNull { when (it) {
            android.view.Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
            android.view.Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
            android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
            android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> AppText.DOLBY_VISION.text()
            else -> null
        } }.distinct().joinToString(" / ")
        val format = videoFormat
        val content = when {
            source == null || format == null -> ""
            format.sampleMimeType == "video/dolby-vision" -> AppText.CURRENT_VIDEO_DOLBY_VISION_N.text()
            format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> AppText.CURRENT_VIDEO_HDR_PQ_N.text()
            format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> AppText.CURRENT_VIDEO_HDR_HLG_N.text()
            else -> AppText.CURRENT_VIDEO_SDR_OR_NO_HDR.text()
        }
        hdrInfo.text = content + (if (displayNames.isEmpty()) AppText.THE_DISPLAY_REPORTS_NO_HDR_SUPPORT.text() else AppText.DISPLAY_SUPPORTS.text(displayNames)) +
            (if (Build.VERSION.SDK_INT < 31) AppText.NTHIS_ANDROID_VERSION_HANDLES_HDR_AUTOMATICALLY.text() else "")
    }

    private fun changeViewingMode(mode: Int, automatic: Boolean) {
        val nextOrdinary = mode == 2
        if (!automatic) {
            manualViewingChoice = true
            source?.let { videoPrefs.edit().putBoolean("${projectionKey(it)}_ordinary_override",nextOrdinary).apply() }
        }
        if (ordinaryVideo == nextOrdinary && (nextOrdinary || settings.phoneMode == (mode == 1))) return
        val resume = shouldResumePlayback()
        if (nextOrdinary && debugMode && !automatic) setDebugMode(false)
        releasePlayer()
        ordinaryVideo = nextOrdinary
        flatVideo.resetTransform()
        // Ordinary playback does not overwrite the last single-/double-lens VR preference.
        if (!nextOrdinary) settings = settings.copy(phoneMode = mode == 1)
        pendingPlay = resume
        renderFailure = false
        vrView.cancelPendingGestures(); logoView.cancelPending()
        if (active) { if (ordinaryVideo) gamepad.stop() else gamepad.start() }
        gamepad.clearInput(); resetSeekSequence(); stereoHint.clear()
        vrView.vrRenderer.resetFrame()
        updateSettings(); updateStatus()
        syncBackCallback()
        if (active) ensurePlayer()
    }

    private fun stopCurrentVideo() {
        if (debugMode) setDebugMode(false)
        if (source == null) { showControls(true); return }
        pendingPlay = false; resumeAfterPlaylist = false; resumeAfterVrPlaylist = false
        closeVrPlaylist(false)
        vrView.cancelPendingGestures(); logoView.cancelPending()
        gamepad.clearInput(); resetSeekSequence(); stereoHint.clear()
        releasePlayer() // Save the resume position before clearing the active source.
        nameRequest++; nameQuery?.cancel(); nameQuery = null
        source = null; sourceLoading = false; sourceName = ""; positionMs = 0; videoFormat = null
        metadataProjection = 0; projectionWarning = ""; savedCoverProjection = ""
        failure = null; renderFailure = false; decoder = ""
        vrView.vrRenderer.resetFrame(); flatVideo.resetTransform(); flatVideo.setVideoSize(0, 0, 1f)
        updateSettings(false); updateStatus(); showControls(true)
    }

    private fun showConfirmation(title: String, message: String, actionLabel: String, action: () -> Unit) {
        showSettingsDialog(ConfirmationDialog(this, title, message, actionLabel, action))
    }

    private fun showSettingsDialog(dialog: SettingsDialog) {
        if (confirmationDialog?.isShowing == true || isFinishing || isDestroyed) return
        confirmationDialog = dialog
        dialog.setOnDismissListener {
            if (confirmationDialog === dialog) confirmationDialog = null
            if (!closing && startupReady) configureWindow()
        }
        dialog.show()
    }

    private fun chooseSetting(title: String, choices: List<String>, selected: Int, chosen: (Int) -> Unit) {
        if (choices.size == 2) {
            chosen(1 - selected.coerceIn(0, 1))
            return
        }
        // Compact one-column choices; row heights fit the actual window with no scrolling.
        val group = object : LinearLayout(this) {
            override fun onMeasure(w: Int, h: Int) {
                val available = if (View.MeasureSpec.getMode(h) == View.MeasureSpec.UNSPECIFIED) dp(40) * childCount
                    else View.MeasureSpec.getSize(h)
                val rowHeight = minOf(dp(40), available / childCount.coerceAtLeast(1)).coerceAtLeast(1)
                for (i in 0 until childCount) getChildAt(i).layoutParams.height = rowHeight
                super.onMeasure(w, h)
            }
        }.apply { orientation = LinearLayout.VERTICAL }
        val dialog = SettingsDialog(this, title, group, listOf(SettingsDialog.Action(AppText.CANCEL.text())), scrollContent = false)
        choices.forEachIndexed { index, text ->
            group.addView(android.widget.RadioButton(this).apply {
                id = View.generateViewId()
                val display = text.removeSuffix(AppText.DEFAULT.text())
                this.text = if (title == AppText.VIEWING_MODE.text() && display.endsWith(" · VR")) {
                    android.text.SpannableString(display).apply {
                        val start = display.length - 2
                        setSpan(android.text.style.ForegroundColorSpan(UiStyle.ACCENT), start, display.length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(android.text.style.StyleSpan(Typeface.BOLD), start, display.length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else display
                contentDescription = text
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f); setTextColor(UiStyle.TEXT)
                buttonTintList = android.content.res.ColorStateList.valueOf(UiStyle.ACCENT)
                setPadding(dp(4), 0, dp(4), 0); minHeight = 0; minimumHeight = 0
                includeFontPadding = false; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                isChecked = index == selected
                setOnClickListener { dialog.dismiss(); chosen(index) }
            }, LinearLayout.LayoutParams(-1, dp(40)))
        }
        showSettingsDialog(dialog)
    }

    private fun setDebugMode(enabled: Boolean) {
        if (enabled && ordinaryVideo) return
        debugMode = enabled
        prefs.edit().putBoolean("debug_mode", enabled).apply()
        if (enabled) {
            pendingPlay = false
            resumeAfterPlaylist = false; resumeAfterVrPlaylist = false
            player?.pause(); savePosition()
            if (ordinaryVideo) releasePlayer()
        }
        gamepad.clearInput(); resetSeekSequence(); stereoHint.clear()
        applyDebugAppearance()
        syncVideoMode()
        updateStatus()
    }

    private fun applyDebugAppearance() {
        vrView.vrRenderer.debugMode = debugMode
        stereoHint.settings = if (ordinaryVideo && !debugMode) settings.copy(phoneMode = true, phoneFillScreen = true) else settings
        stereoHint.debugMode = debugMode
        idleBackdrop.visibility = if (source == null && !debugMode) View.VISIBLE else View.GONE
        frostedBars?.refresh()
        // Debug only changes the viewing area; both playback bars retain their starfield backgrounds.
        debugButton.text = if (debugMode) AppText.EXIT_DEBUG.text() else AppText.DEBUG_MODE.text()
        debugButton.setTextColor(if (debugMode) UiStyle.ACCENT else UiStyle.TEXT)
        debugButton.isSelected = debugMode
        for (button in playbackButtons) {
            val enabled = !debugMode || button === playButton
            button.isEnabled = enabled; button.alpha = if (enabled) 1f else .35f
        }
    }

    private fun buildSettingsPage() {
        // Settings typography is independent of the compact playback controls.
        fun label(value: String, size: Float) = this@MainActivity.label(value, size + 2f).apply {
            includeFontPadding = false
            setLineSpacing(0f, 1f)
        }
        fun button(value: String, action: () -> Unit) = this@MainActivity.button(value, action).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            includeFontPadding = false
            setLineSpacing(0f, 1f)
            maxLines = 2
            setAutoSizeTextTypeUniformWithConfiguration(12, 14, 1, TypedValue.COMPLEX_UNIT_DIP)
        }
        settingsPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyle.stars(this@MainActivity)
            setPadding(dp(22), dp(6), dp(22), 0)
            isClickable = true
            visibility = View.GONE
        }
        val header = row()
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label(AppText.SETTINGS.text(), 23f).apply { setTypeface(typeface, Typeface.BOLD) })
        header.addView(heading, LinearLayout.LayoutParams(0, dp(46), 1f))
        debugButton = button(AppText.DEBUG_MODE.text()) {
            if (debugMode) {
                setDebugMode(false)
                closeSettingsPage()
            } else showConfirmation(AppText.ENTER_DEBUG_MODE.text(),
                AppText.PAUSE_THE_VIDEO_AND_SHOW_A.text(), AppText.ENTER_DEBUG.text()) {
                setDebugMode(true)
                closeSettingsPage()
            }
        }
        header.addView(debugButton, LinearLayout.LayoutParams(dp(128), dp(40)).apply { marginEnd = dp(12) })
        header.addView(button(AppText.RESET_DEFAULTS.text()) {
            showConfirmation(AppText.RESET_ALL_SETTINGS.text(),
                AppText.RESET_VIEWING_BRIGHTNESS_SPEED_LENS_CORRECTION.text(), AppText.RESET_DEFAULTS.text(), ::restoreDefaults)
        }, LinearLayout.LayoutParams(dp(128), dp(40)).apply { rightMargin = dp(12) })
        header.addView(iconButton("return", AppText.BACK_TO_PLAYER.text()) { closeSettingsPage() }, LinearLayout.LayoutParams(dp(48), dp(40)))
        settingsPage.addView(header)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(16)) }
        settingsBody = body
        val columns = row().apply { gravity = Gravity.TOP }
        settingsColumns = columns
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        settingsLeft = left
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        columns.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(12) })
        columns.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        // All viewing modes share two columns; ordinary controls occupy the right column alone.
        fun card(parent: LinearLayout, title: String, subtitle: String? = null): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(18))
                background = UiStyle.shape(this@MainActivity, UiStyle.SURFACE, 18)
            }
            card.addView(label(title, 16f).apply { setTypeface(typeface, Typeface.BOLD) })
            if (subtitle != null) card.addView(label(subtitle, 11f).apply { setTextColor(UiStyle.MUTED); setPadding(0, dp(3), 0, dp(8)) })
            parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            return card
        }
        fun actionRow(parent: LinearLayout, title: String, control: View): LinearLayout {
            val line = row()
            line.addView(label(title, 13f).apply { minimumHeight = dp(44) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
            line.addView(control, LinearLayout.LayoutParams(dp(156), dp(40)))
            parent.addView(line)
            return line
        }
        fun toggle(parent: LinearLayout, title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit): Switch {
            val line = row()
            val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), dp(8), dp(4)) }
            words.addView(label(title, 13f))
            words.addView(label(subtitle, 10f).apply { setTextColor(UiStyle.MUTED); if (!AppText.isEnglish()) maxLines = 2 })
            line.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            val control = Switch(this).apply {
                contentDescription = title
                isChecked = checked
                thumbTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(UiStyle.ACCENT, UiStyle.MUTED))
                setOnCheckedChangeListener { _, enabled -> change(enabled) }
            }
            line.addView(control, LinearLayout.LayoutParams(dp(52), dp(48)))
            parent.addView(line)
            return control
        }
        viewingModeCard = row().apply {
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = UiStyle.shape(this@MainActivity, UiStyle.SURFACE, 18)
        }
        viewingModeCard.addView(label(AppText.VIEWING_MODE.text(), 14f).apply {
            setTypeface(typeface, Typeface.BOLD)
            minimumHeight = dp(44)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        viewingModeButton = button("") {
            chooseSetting(AppText.VIEWING_MODE.text(), listOf(AppText.D_DUAL_LENS_VR.text(), AppText.PHONE_SINGLE_LENS_VR.text(), AppText.SETTINGS_STANDARD_VIDEO.text()),
                if (ordinaryVideo) 2 else if (settings.phoneMode) 1 else 0, ::setViewingMode)
        }
        viewingModeCard.addView(viewingModeButton, LinearLayout.LayoutParams(dp(156), dp(40)))
        left.addView(viewingModeCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        body.addView(columns)
        val visual = card(left, AppText.PICTURE.text())
        visualCard = visual
        phoneAspectButton = button("") {
            chooseSetting(AppText.PHONE_ASPECT_RATIO.text(), listOf(AppText.LETTERBOX.text(), AppText.FULL_SCREEN.text()), if (settings.phoneFillScreen) 1 else 0) {
                settings = settings.copy(phoneFillScreen = it == 1)
                vrView.cancelPendingGestures(); updateSettings(); updateStatus()
            }
        }
        singleLensOptions.add(actionRow(visual, AppText.PHONE_ASPECT_RATIO.text(), phoneAspectButton))
        phoneShapeButton = button("") {
            chooseSetting(AppText.PHONE_PROJECTION.text(), listOf(AppText.DYNAMIC_RECTANGLE.text(), AppText.ELLIPSE.text()), if (settings.phoneElliptical) 1 else 0) {
                settings = settings.copy(phoneElliptical = it == 1)
                vrView.cancelPendingGestures(); stereoHint.clear(); updateSettings(); updateStatus()
            }
        }
        singleLensOptions.add(actionRow(visual, AppText.PHONE_PROJECTION.text(), phoneShapeButton))
        val phoneCorrection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        phoneCorrectionPanel = phoneCorrection
        phoneCorrectionText = label("", 12f).apply { setPadding(0, dp(8), 0, dp(3)) }
        phoneCorrection.addView(phoneCorrectionText)
        phoneCorrectionSeek = SeekBar(this).apply {
            max = 20; keyProgressIncrement = 1
            contentDescription = AppText.ULTRAWIDE_CORRECTION_IN_STEPS_EQUALS_THE.text()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        settings = settings.copy(phoneWideCorrection = progress * 5)
                        updateSettings()
                    }
                }
            })
        }
        phoneCorrection.addView(phoneCorrectionSeek, LinearLayout.LayoutParams(-1, dp(38)))
        visual.addView(phoneCorrection)
        singleLensOptions.add(phoneCorrection)
        val sizes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        diameterText = label("", 12f).apply { setPadding(0, dp(6), 0, 0) }
        sizes.addView(diameterText)
        diameterSeek = SeekBar(this).apply {
            max = 15
            keyProgressIncrement = 1
            contentDescription = AppText.CIRCULAR_FIELD_DIAMETER_PER_EYE_TO.text()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) selectDiameter((20 + progress) / 5f)
                }
            })
        }
        sizes.addView(diameterSeek, LinearLayout.LayoutParams(-1, dp(48)))
        val limits = row()
        limits.addView(label("4 cm", 10f), LinearLayout.LayoutParams(0, -2, 1f))
        limits.addView(label("7 cm", 10f))
        sizes.addView(limits)
        visual.addView(sizes)
        doubleLensOptions.add(sizes)
        projectionButton = button("") {
            val projectionChoices = VideoProjection.choices(settings.phoneMode)
            val selected = VideoProjection.forViewingMode(projectionMode, settings.phoneMode)
            chooseSetting(AppText.SOURCE_PROJECTION.text(), projectionChoices.map { settingsProjectionLabel(it) },
                projectionChoices.indexOf(selected).coerceAtLeast(0)) { choice ->
                projectionMode = projectionChoices[choice]
                if (projectionMode == VideoProjection.STEREO_FISHEYE_180 && layoutMode == 2) layoutMode = -1
                saveProjectionChoice(); updateSettings(); updateStatus()
            }
        }
        panoramaOptions.add(actionRow(visual, AppText.SOURCE_PROJECTION.text(), projectionButton))
        layoutButton = button("") {
            val stereoFisheye = settings.sourceProjection == VideoProjection.STEREO_FISHEYE_180
            val choices = if (stereoFisheye) listOf(AppText.AUTO_DETECT_DEFAULT.text(), AppText.SIDE_BY_SIDE_SBS.text(), AppText.TOP_BOTTOM_TB.text())
                else listOf(AppText.AUTO_DETECT_DEFAULT.text(), AppText.SIDE_BY_SIDE_SBS.text(), AppText.TOP_BOTTOM_TB.text(), AppText.MONO.text())
            val selected = if (stereoFisheye && layoutMode == 2) 0 else layoutMode + 1
            chooseSetting(AppText.VIDEO_LAYOUT.text(), choices, selected) { choice ->
                layoutMode = choice - 1
                saveProjectionChoice(); updateSettings(); updateStatus()
            }
        }
        panoramaOptions.add(actionRow(visual, AppText.VIDEO_LAYOUT.text(), layoutButton))
        swapButton = button("") {
            chooseSetting(AppText.EYE_ORDER.text(), listOf(AppText.NORMAL.text(), AppText.SWAP_EYES.text()), if (settings.swapEyes) 1 else 0) {
                settings = settings.copy(swapEyes = it == 1); updateSettings()
            }
        }
        doubleLensOptions.add(actionRow(visual, AppText.SWAP_EYES.text(), swapButton))
        phoneEyeButton = button("") {
            chooseSetting(AppText.VIEWING_LENS.text(), listOf(AppText.LEFT_LENS.text(), AppText.RIGHT_LENS.text()), if (settings.swapEyes) 1 else 0) {
                settings = settings.copy(swapEyes = it == 1); updateSettings()
            }
        }
        phoneEyeRow = actionRow(visual, AppText.VIEWING_LENS.text(), phoneEyeButton)
        correctionText = label("", 12f).apply { setPadding(0, dp(8), 0, dp(3)) }
        visual.addView(correctionText)
        doubleLensOptions.add(correctionText)
        correctionSeek = SeekBar(this).apply {
            max = 20; progress = settings.edgeCorrection / 5
            keyProgressIncrement = 1
            contentDescription = AppText.LENS_DISTORTION_CORRECTION_TO_IN_STEPS.text()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        compensationPrefs.edit().putInt(compensationKey(settings.eyeDiameterCm), progress * 5).apply()
                        updateSettings()
                    }
                }
            })
        }
        visual.addView(correctionSeek, LinearLayout.LayoutParams(-1, dp(38)))
        doubleLensOptions.add(correctionSeek)
        val correctionInfo = label(AppText.FOR_VR_HEADSETS_USE_FOR_DIRECT.text(), 10f).apply { setTextColor(UiStyle.MUTED) }
        visual.addView(correctionInfo)
        doubleLensOptions.add(correctionInfo)
        languageSettingsCard = row().apply {
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = UiStyle.shape(this@MainActivity, UiStyle.SURFACE, 18)
        }
        languageSettingsCard.addView(label(AppText.LANGUAGE.text(), 14f).apply {
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        languageSettingsCard.addView(button(AppText.LANGUAGE_NAME.text()) {
            chooseSetting(AppText.LANGUAGE.text(), listOf("简体中文", "English"), if (AppText.isEnglish()) 1 else 0) {
                changeLanguage(it == 1)
            }
        }, LinearLayout.LayoutParams(dp(156), dp(40)))
        left.addView(languageSettingsCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val display = card(right, AppText.SCREEN_CALIBRATION.text(), AppText.CENTER_THE_CIRCULAR_VIEWS_USING_THE.text())
        doubleLensOptions.add(display)
        screenInfo = label("", 11f).apply { setTextColor(UiStyle.MUTED); setLineSpacing(dp(1).toFloat(), 1f) }
        display.addView(screenInfo, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val screenActions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        screenActions.addView(button(AppText.DETECT_AGAIN.text()) { detectScreenParameters() }, LinearLayout.LayoutParams(-1, dp(40)).apply { bottomMargin = dp(8) })
        screenActions.addView(button(AppText.ENTER_MANUALLY.text()) { editScreenParameters(false) }, LinearLayout.LayoutParams(-1, dp(40)))
        display.addView(screenActions)
        val controls = card(right, AppText.CONTROLS.text(), AppText.CHOOSE_HOW_YOU_PREFER_TO_WATCH.text())
        controlsCard = controls
        dragSwitch = toggle(controls, AppText.DRAG_TO_LOOK.text(), AppText.DRAG_WITH_ONE_FINGER_TO_LOOK.text(), vrView.dragEnabled) {
            vrView.dragEnabled = it; prefs.edit().putBoolean("drag_enabled", it).apply()
        }
        rotationSwitch = toggle(controls, AppText.AUTO_ROTATE.text(), AppText.SWITCH_BETWEEN_LANDSCAPE_ORIENTATIONS_WITH_THE.text(), prefs.getBoolean("rotate_enabled", false)) {
            if (!syncingRotationSwitches) setRotationEnabled(it)
        }
        val ordinaryPicture = card(left, AppText.PICTURE.text())
        ordinaryPictureCard = ordinaryPicture
        hdrSwitch = toggle(ordinaryPicture, "HDR", AppText.ON_AUTOMATIC_HDR_OUTPUT_OFF_DISPLAY.text(), hdrEnabled, ::setHdrEnabled)
        hdrSwitch.isEnabled = Build.VERSION.SDK_INT >= 31
        hdrSwitch.alpha = if (hdrSwitch.isEnabled) 1f else .5f
        hdrInfo = label("", 11f).apply { setTextColor(UiStyle.MUTED); setPadding(0, dp(6), 0, 0) }
        ordinaryPicture.addView(hdrInfo)
        left.removeView(languageSettingsCard)
        left.addView(languageSettingsCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val ordinaryControls = card(right, AppText.CONTROLS.text(), AppText.TOUCH_AND_ROTATION_SETTINGS_FOR_STANDARD.text())
        ordinaryControlsCard = ordinaryControls
        pinchSwitch = toggle(ordinaryControls, AppText.PINCH_TO_ZOOM.text(), AppText.PINCH_TO_ZOOM_AND_DRAG_TO.text(), prefs.getBoolean("flat_pinch_enabled", true)) {
            prefs.edit().putBoolean("flat_pinch_enabled", it).apply()
            flatVideo.zoomEnabled = it
        }
        ordinaryRotationSwitch = toggle(ordinaryControls, AppText.AUTO_ROTATE.text(), AppText.SWITCH_BETWEEN_LANDSCAPE_ORIENTATIONS_WITH_THE.text(), prefs.getBoolean("rotate_enabled", false)) {
            if (!syncingRotationSwitches) setRotationEnabled(it)
        }
        ordinaryControls.addView(label(AppText.AFTER_ZOOMING_TAP_RESET_IN_THE.text(), 11f).apply {
            setTextColor(UiStyle.MUTED); setPadding(0, dp(8), 0, 0)
        })
        val touchHelp = AppText.TAP_TO_SHOW_OR_HIDE_THE.text()
        val boundaryHelp = AppText.AT_THE_END_OR_WHEN_PAUSED.text()
        for (section in listOf(controls, ordinaryControls)) {
            section.addView(label("${touchHelp}\n${boundaryHelp}", 11f).apply {
                setTextColor(UiStyle.MUTED); setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(0, dp(8), 0, dp(4))
            })
        }
        val guide = card(body, AppText.CONTROLLER_GUIDE.text(), AppText.BUTTONS_AND_ACTIONS.text())
        controllerGuideCard = guide
        controllerGuide = ControllerGuideView(this)
        guide.addView(controllerGuide, LinearLayout.LayoutParams(-1, -2))
        val about = card(body, AppText.ABOUT.text(), AppText.ASTRIAVR_A_NEW_VIEW_OF_YOUR.text())
        val info = packageManager.getPackageInfo(packageName, 0)
        about.addView(label(AppText.VERSION_LOCAL_VR_PLAYER_FOR_ANDROID.text(info.versionName), 12f))
        fun aboutSection(title: String, text: String) {
            about.addView(label(title, 12f).apply {
                setTextColor(UiStyle.TEXT); setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(5))
            })
            about.addView(label(text, 11f).apply {
                setTextColor(UiStyle.MUTED); setLineSpacing(dp(4).toFloat(), 1f)
            })
        }
        aboutSection(AppText.WHAT_IT_CAN_DO.text(), AppText.ASTRIAVR_PLAYS_LOCAL_PANORAMIC_AND_STANDARD.text())
        aboutSection(AppText.HOW_VIDEO_BECOMES_YOUR_VIEW.text(), AppText.MEDIA_EXOPLAYER_HANDLES_PLAYBACK_AND_SYSTEM.text())
        aboutSection(AppText.ADJUST_FOR_A_COMFORTABLE_VIEW.text(), AppText.IPD_SETS_THE_SPACING_BETWEEN_THE.text())
        aboutSection(AppText.USEFUL_DETAILS.text(), AppText.IN_DUAL_LENS_MODE_PRESS_MENU.text())
        about.addView(label("bilibili @雨星衡  ·  github @AstriaFR", 12f).apply {
            setTextColor(UiStyle.ACCENT); setPadding(0, dp(12), 0, 0)
        })
        settingsPage.addView(ScrollView(this).apply { isFillViewport = false; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(settingsPage, FrameLayout.LayoutParams(-1, -1))
    }

    private fun applyRotationPreference() {
        requestedOrientation = if (prefs.getBoolean("rotate_enabled", false))
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    private fun setRotationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("rotate_enabled", enabled).apply()
        syncingRotationSwitches = true
        try {
            rotationSwitch.isChecked = enabled
            ordinaryRotationSwitch.isChecked = enabled
        } finally { syncingRotationSwitches = false }
        applyRotationPreference()
    }

    private fun openSettingsPage() {
        if (settingsOpen) return
        closeVrPlaylist(false)
        settingsOpen = true
        syncBitrateSampling()
        gamepad.clearInput()
        resetSeekSequence()
        stereoHint.clear()
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        settingsPage.visibility = View.VISIBLE
        videoTitle.isSelected = false
        syncBackCallback()
    }

    private fun changeLanguage(english: Boolean) {
        if (languageChanging || english == AppText.isEnglish()) return
        languageChanging = true
        val current = player
        resumeAfterLanguageChange = PlaybackPosition.shouldResume(pendingPlay,
            current?.playWhenReady == true, current?.playbackState == Player.STATE_ENDED)
        languagePositionMs = current?.currentPosition ?: positionMs
        saveSettings()
        AppLanguage.select(this, english)
        // Rebuild every native and Canvas label; the saved session keeps the video and position.
        recreate()
    }

    private fun selectDiameter(diameter: Float) {
        settings = settings.copy(eyeDiameterCm = diameter).fitScreen()
        updateSettings()
        if (settings.eyeDiameterCm < diameter) notifyUser(AppText.THIS_SCREEN_SUPPORTS_CIRCULAR_VIEWS_UP.text(settings.eyeDiameterCm))
    }

    private fun restoreDefaults() {
        if (ordinaryVideo) setViewingMode(0)
        handler.removeCallbacks(persistSettings)
        gamepad.clearInput()
        resetSeekSequence()
        stereoHint.clear()
        vrView.cancelPendingGestures()
        setDebugMode(false)
        settings = RenderSettings()
        projectionMode = 0
        layoutMode = -1
        videoPrefs.edit().clear().apply()
        compensationPrefs.edit().clear().apply()
        prefs.edit().putInt("projection_mode", 0).putInt("layout_mode", -1)
            .putBoolean("gyro_enabled", false).putBoolean("drag_enabled", true)
            .putBoolean("rotate_enabled", false).putBoolean("flat_pinch_enabled", true)
            .putBoolean("screen_setup_done", false).apply()
        detectedScreenApplied = false
        customScreenApplied = false
        tracker.setGyroEnabled(false)
        tracker.recenter()
        vrView.dragEnabled = true
        dragSwitch.isChecked = true
        rotationSwitch.isChecked = false
        ordinaryRotationSwitch.isChecked = false
        pinchSwitch.isChecked = true
        setHdrEnabled(true)
        flatVideo.zoomEnabled = true
        applyRotationPreference()
        speedIndex = PlaybackTuning.DEFAULT_SPEED
        player?.setPlaybackSpeed(PlaybackTuning.speed(speedIndex))
        brightness = -1f
        configureWindow()
        updateSettings()
        updateStatus()
        // Re-run first-launch physical-screen setup after rotation/layout settles.
        firstSetupShown = false
        notifyUser(AppText.DEFAULT_SETTINGS_RESTORED.text())
    }

    private fun applyScreen(w: Double, h: Double, custom: Boolean) {
        detectedScreenApplied = !custom
        customScreenApplied = custom
        val previousDiameter = settings.eyeDiameterCm
        settings = settings.copy(screenWidthCm = w, screenHeightCm = h).fitScreen()
        prefs.edit().putBoolean("screen_setup_done", true).apply()
        updateSettings()
        if (settings.eyeDiameterCm < previousDiameter) notifyUser(AppText.FIELD_DIAMETER_ADJUSTED_TO_CM.text(settings.eyeDiameterCm))
    }

    private fun useDefaultScreen() {
        detectedScreenApplied = false
        customScreenApplied = false
        settings = settings.copy(screenWidthCm = Optics.WIDTH_CM, screenHeightCm = Optics.HEIGHT_CM).stepIpd(0)
        prefs.edit().putBoolean("screen_setup_done", true).apply()
        updateSettings()
    }

    private fun screenFallback(reason: String, firstLaunch: Boolean) {
        showSettingsDialog(SettingsDialog.message(this, AppText.SCREEN_DETECTION_INCOMPLETE.text(),
            AppText.N_NENTER_THE_SCREEN_S_PHYSICAL.text(reason),
            listOf(SettingsDialog.Action(AppText.USE_DEFAULTS.text(), accepted = ::useDefaultScreen),
                SettingsDialog.Action(AppText.ENTER_MANUALLY.text(), true, accepted = { editScreenParameters(firstLaunch) })), !firstLaunch))
    }

    private fun editScreenParameters(firstLaunch: Boolean) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        fun field(title: String, current: Double): EditText {
            panel.addView(label(title, 13f))
            return EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setSingleLine()
                setTextColor(UiStyle.TEXT)
                setText(String.format(Locale.ROOT, "%.2f", current))
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
                imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = UiStyle.glass(this@MainActivity, UiStyle.RAISED, 16)
                panel.addView(this, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6); bottomMargin = dp(12) })
            }
        }
        panel.addView(label(AppText.ENTER_THE_ILLUMINATED_SCREEN_SIZE_EXCLUDING.text(), 12f))
        val longEdge = field(AppText.LONG_EDGE_CM.text(), settings.screenWidthCm)
        val shortEdge = field(AppText.SHORT_EDGE_CM.text(), settings.screenHeightCm)
        val validation = label("", 11f).apply { setTextColor(0xFFFFA9AA.toInt()); visibility = View.GONE }
        panel.addView(validation)
        shortEdge.imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        showSettingsDialog(SettingsDialog(this, AppText.CUSTOM_SCREEN_SIZE.text(), panel,
            listOf(SettingsDialog.Action(if (firstLaunch) AppText.USE_DEFAULTS.text() else AppText.CANCEL.text(), accepted = { if (firstLaunch) useDefaultScreen() }),
                SettingsDialog.Action(AppText.SAVE_APPLY.text(), true, validate = {
                val w = Optics.parseScreenCm(longEdge.text.toString())
                val h = Optics.parseScreenCm(shortEdge.text.toString())
                val problem = when {
                    !w.isFinite() -> AppText.ENTER_A_VALUE_ABOVE_AND_UP.text()
                    !h.isFinite() || h > 35 -> AppText.ENTER_A_VALUE_ABOVE_AND_UP_151.text()
                    w < h -> AppText.THE_LONG_EDGE_MUST_BE_AT.text()
                    !Optics.fits(4f, w, h) -> AppText.THIS_SIZE_CANNOT_FIT_THE_MINIMUM.text()
                    else -> null
                }
                if (problem == null) { applyScreen(w, h, true); true }
                else {
                    validation.text = problem; validation.visibility = View.VISIBLE
                    validation.post { validation.requestRectangleOnScreen(android.graphics.Rect(0, 0, validation.width, validation.height), false) }
                    false
                }
            })), !firstLaunch))
    }

    @Suppress("DEPRECATION")
    private fun detectScreenParameters(firstLaunch: Boolean = false) {
        val metrics = android.util.DisplayMetrics()
        if (runCatching { windowManager.defaultDisplay.getRealMetrics(metrics) }.isFailure) {
            screenFallback(AppText.COULD_NOT_READ_THE_SYSTEM_S.text(), firstLaunch)
            return
        }
        val dimensions = Optics.detectedScreen(metrics.widthPixels, metrics.heightPixels, metrics.xdpi, metrics.ydpi)
        if (dimensions == null) { screenFallback(AppText.THE_SYSTEM_DID_NOT_PROVIDE_VALID.text(), firstLaunch); return }
        val w = Math.round(dimensions[0] * 100) / 100.0
        val h = Math.round(dimensions[1] * 100) / 100.0
        val fullScreen = kotlin.math.abs(root.width - metrics.widthPixels) <= 4 &&
            kotlin.math.abs(root.height - metrics.heightPixels) <= 4
        if (!fullScreen || !Optics.fits(4f, w, h)) {
            screenFallback(if (!fullScreen) AppText.THE_APP_IS_NOT_USING_THE.text() else
                AppText.THE_DETECTED_SIZE_CM_CANNOT_FIT.text(w, h), firstLaunch)
            return
        }
        if (firstLaunch) { applyScreen(w, h, false); return }
        val result = String.format(Locale.ROOT,
            AppText.SYSTEM_RESOLUTION_D_D_PX_NPHYSICAL.text(),
            metrics.widthPixels, metrics.heightPixels, metrics.xdpi, metrics.ydpi, w, h, Math.hypot(w, h) / 2.54)
        showSettingsDialog(SettingsDialog.message(this, AppText.DETECTED_SCREEN_SIZE.text(), result,
            listOf(SettingsDialog.Action(AppText.CANCEL.text()), SettingsDialog.Action(AppText.ENTER_MANUALLY.text(), accepted = { editScreenParameters(false) }),
                SettingsDialog.Action(AppText.APPLY.text(), true, accepted = { applyScreen(w, h, false) }))))
    }

    private fun closeSettingsPage() {
        if (!settingsOpen) return
        settingsOpen = false
        gamepad.clearInput()
        resetSeekSequence()
        settingsPage.visibility = View.GONE
        syncBackCallback()
        showControls(controlsVisible)
    }

    @Deprecated("Legacy Android back button")
    // API 33+ uses the registered OnBackInvokedCallback above; this is only the older OS fallback.
    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        if (::playlistInput.isInitialized && playlistInput.open) closeVrPlaylist(true)
        else if (playlistOpen) { if (!playlistPage.handleBack()) closePlaylistPage(true) }
        else if (settingsOpen) closeSettingsPage()
        else if (!startupReady || ordinaryVideo || !gamepad.hasConnectedController()) finish()
        else stereoHint.showValue(AppText.CONTROLLER_CONNECTED_BACK_WILL_NOT_EXIT.text())
    }

    private fun syncBackCallback() {
        if (!startupReady || Build.VERSION.SDK_INT < 33) return
        val needed = settingsOpen || playlistOpen || playlistInput.open || (!ordinaryVideo && gamepad.hasConnectedController())
        val navigation = navigationBack ?: BackNavigation.create(this, ::handleBack).also { navigationBack = it }
        navigation.setEnabled(needed)
    }

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value; setTextSize(TypedValue.COMPLEX_UNIT_DIP, size); setTextColor(UiStyle.TEXT)
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f); isAllCaps = false
        setTextColor(UiStyle.ACCENT); setTypeface(typeface, Typeface.BOLD)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(3), 0, dp(3), 0)
        backgroundTintList = null; stateListAnimator = null; elevation = 0f
        background = UiStyle.ripple(this@MainActivity, UiStyle.RAISED, 12)
        setOnClickListener { action() }
    }
    private fun iconButton(icon: String, description: String, action: () -> Unit) = ImageButton(this).apply {
        contentDescription = description
        setImageDrawable(PlayerIcon(icon))
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        setPadding(dp(7), dp(7), dp(7), dp(7))
        background = UiStyle.ripple(this@MainActivity, UiStyle.RAISED, 14)
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    private fun compensationKey(diameter: Float) = when {
        diameter <= 5f -> "band_4_to_5"
        diameter <= 6f -> "band_5_2_to_6"
        else -> "band_6_2_to_7"
    }

    private fun readCompensation(diameter: Float): Int {
        val value = compensationPrefs.getInt(compensationKey(diameter), if (diameter <= 6f) 50 else 0)
        return ((value.coerceIn(0, 100) + 2) / 5) * 5
    }

    private fun updateSettings(persist: Boolean = true) {
        settings = settings.fitScreen()
        settings = settings.copy(edgeCorrection = readCompensation(settings.eyeDiameterCm))
        resolveProjection()
        vrView.vrRenderer.settings = settings
        stereoHint.settings = if (ordinaryVideo && !debugMode) settings.copy(phoneMode = true, phoneFillScreen = true) else settings
        vrPlaylist.settings = settings
        if ((settings.phoneMode || ordinaryVideo) && playlistInput.open) closeVrPlaylist(false)
        source?.takeIf { !sourceLoading }?.let {
            val layout = if (ordinaryVideo) 2 else settings.layout
            val degrees = if (ordinaryVideo) 0 else settings.sourceProjection
            val key = "${it}|${layout}|${degrees}"
            if (savedCoverProjection != key) {
                val uri = it.toString()
                playlist.submit({
                    val previous = playlist.find(uri)
                    playlist.setProjection(uri, layout, degrees)
                    previous?.takeIf { it.layout != layout || it.projection != degrees }
                }) { result ->
                    if (!closing) result.getOrNull()?.let { covers.invalidate(it) }
                }
                savedCoverProjection = key
            }
        }
        viewingModeButton.text = if (ordinaryVideo) AppText.SETTINGS_STANDARD_VIDEO.text() else if (settings.phoneMode) AppText.SINGLE_LENS_VR.text() else AppText.DUAL_LENS_VR.text()
        viewingModeButton.contentDescription = AppText.VIEWING_MODE_166.text(if (ordinaryVideo) AppText.SETTINGS_STANDARD_VIDEO.text() else if (settings.phoneMode) AppText.PHONE_SINGLE_LENS_VR_164.text() else AppText.D_DUAL_LENS_VR_165.text())
        controlsCard.visibility = if (ordinaryVideo) View.GONE else View.VISIBLE
        ordinaryControlsCard.visibility = if (ordinaryVideo) View.VISIBLE else View.GONE
        ordinaryPictureCard.visibility = if (ordinaryVideo) View.VISIBLE else View.GONE
        if (ordinaryVideo) updateHdrInfo()
        controllerGuideCard.visibility = if (ordinaryVideo) View.GONE else View.VISIBLE
        debugButton.visibility = if (ordinaryVideo) View.GONE else View.VISIBLE
        phoneAspectButton.text = if (settings.phoneFillScreen) AppText.FULL_SCREEN.text() else AppText.LETTERBOX.text()
        phoneShapeButton.text = if (settings.phoneElliptical) AppText.ELLIPSE.text() else AppText.DYNAMIC_RECTANGLE.text()
        phoneCorrectionText.text = AppText.ULTRAWIDE_CORRECTION.text(settings.phoneWideCorrection)
        phoneCorrectionSeek.progress = settings.phoneWideCorrection / 5
        if (Build.VERSION.SDK_INT >= 30) phoneCorrectionSeek.stateDescription = "${settings.phoneWideCorrection}%"
        for (option in singleLensOptions) option.visibility = if (settings.phoneMode && !ordinaryVideo) View.VISIBLE else View.GONE
        for (option in doubleLensOptions) option.visibility = if (settings.phoneMode || ordinaryVideo) View.GONE else View.VISIBLE
        for (option in panoramaOptions) option.visibility = if (ordinaryVideo) View.GONE else View.VISIBLE
        visualCard.visibility = if (ordinaryVideo) View.GONE else View.VISIBLE
        phoneCorrectionPanel.visibility = if (!ordinaryVideo && settings.phoneMode && !settings.phoneElliptical) View.VISIBLE else View.GONE
        phoneEyeRow.visibility = if (!ordinaryVideo && settings.phoneMode && settings.layout != 2) View.VISIBLE else View.GONE
        phoneEyeButton.text = if (settings.swapEyes) AppText.RIGHT_LENS.text() else AppText.LEFT_LENS.text()
        if (!ordinaryVideo) controllerGuide.setMode(settings.phoneMode)
        gyroButton.visibility = if (ordinaryVideo) View.GONE else View.VISIBLE
        recenterButton.visibility = if (ordinaryVideo) View.GONE else View.VISIBLE
        flatResetButton.visibility = if (ordinaryVideo && source != null) View.VISIBLE else View.GONE
        var topVisible = 0
        for (i in 0 until topActions.childCount) topActions.getChildAt(i).let { button ->
            val params = button.layoutParams as LinearLayout.LayoutParams
            val margin = if (button.visibility == View.VISIBLE && topVisible++ > 0) dp(6) else 0
            if (params.marginStart != margin) { params.marginStart = margin; button.layoutParams = params }
        }
        val actionsWidth = topVisible * dp(48) + (topVisible - 1).coerceAtLeast(0) * dp(6)
        if (topActions.layoutParams.width != actionsWidth) topActions.layoutParams = topActions.layoutParams.apply { width = actionsWidth }
        dragSwitch.isEnabled = !ordinaryVideo; dragSwitch.alpha = if (ordinaryVideo) .4f else 1f
        updateTuningLayout()
        syncVideoMode()
        val minIpd = Optics.minIpd(settings.eyeDiameterCm)
        val maxIpd = Optics.maxIpd(settings.eyeDiameterCm, settings.screenWidthCm)
        diameterText.text = AppText.CIRCULAR_FIELD_PER_EYE_CM.text(settings.eyeDiameterCm)
        diameterSeek.progress = Math.round(settings.eyeDiameterCm * 5) - 20
        if (Build.VERSION.SDK_INT >= 30) diameterSeek.stateDescription = AppText.CM.text(settings.eyeDiameterCm)
        screenInfo.text = String.format(Locale.ROOT, AppText.S_SCREEN_F_F_CM_NDIAMETER.text(),
            if (customScreenApplied) AppText.CUSTOM_SCREEN_SIZE.text() else if (detectedScreenApplied) AppText.DETECTED_SCREEN_SIZE_APPLIED.text() else AppText.DEFAULT_INCHES.text(),
            settings.screenWidthCm, settings.screenHeightCm, settings.eyeDiameterCm, minIpd, maxIpd)
        opticsText.text = "${if (settings.phoneMode) settings.phoneFovDegrees else settings.fovDegrees}°"
        opticsText.contentDescription = if (settings.phoneMode) AppText.HORIZONTAL_FIELD_OF_VIEW_DEGREES.text(settings.phoneFovDegrees) else AppText.FIELD_OF_VIEW_DEGREES.text(settings.fovDegrees)
        correctionText.text = AppText.LENS_DISTORTION_CORRECTION.text(settings.edgeCorrection)
        correctionSeek.progress = settings.edgeCorrection / 5
        if (Build.VERSION.SDK_INT >= 30) correctionSeek.stateDescription = "${settings.edgeCorrection}%"
        ipdText.text = String.format(Locale.ROOT, "%.1f", settings.ipdCm)
        projectionButton.text = (if (projectionMode == 0) AppText.AUTO.text() else "") + settingsProjectionLabel(settings.sourceProjection)
        val layoutName = arrayOf(AppText.SIDE_BY_SIDE_SBS.text(), AppText.TOP_BOTTOM_TB.text(), AppText.MONO.text())[settings.layout]
        val singleFisheye = settings.sourceProjection == VideoProjection.FISHEYE_180
        layoutButton.isEnabled = !singleFisheye
        layoutButton.alpha = if (singleFisheye) .5f else 1f
        val autoLayout = layoutMode == -1 || (settings.sourceProjection == VideoProjection.STEREO_FISHEYE_180 && layoutMode == 2)
        layoutButton.text = if (singleFisheye) AppText.MONO.text() else if (autoLayout) AppText.AUTO_177.text(layoutName) else layoutName
        swapButton.text = if (settings.swapEyes) AppText.ON.text() else AppText.OFF.text()
        if (persist) {
            handler.removeCallbacks(persistSettings)
            handler.postDelayed(persistSettings, 300)
        }
    }

    private fun settingsProjectionLabel(projection: Int) = when (projection) {
        0 -> AppText.SETTINGS_AUTO.text()
        180 -> AppText.SETTINGS_EQ_180.text()
        360 -> AppText.SETTINGS_EQ_360.text()
        VideoProjection.FISHEYE_180 -> AppText.SETTINGS_MONO_FISHEYE.text()
        VideoProjection.STEREO_FISHEYE_180 -> AppText.SETTINGS_STEREO_FISHEYE.text()
        VideoProjection.CUBEMAP -> AppText.SETTINGS_CUBEMAP.text()
        VideoProjection.EAC -> AppText.SETTINGS_EAC.text()
        else -> VideoProjection.label(projection)
    }

    private fun saveSettings() {
        handler.removeCallbacks(persistSettings)
        prefs.edit().putFloat("ipd", settings.ipdCm).putInt("layout", settings.layout)
            .putBoolean("swap", settings.swapEyes).putFloat("brightness", brightness)
            .putInt("fov_8deg", settings.fovDegrees).putFloat("eye_diameter", settings.eyeDiameterCm)
            .putBoolean("phone_mode", settings.phoneMode).putInt("phone_fov", settings.phoneFovDegrees)
            .putBoolean("ordinary_video", ordinaryVideo)
            .putBoolean("phone_fill_screen", settings.phoneFillScreen)
            .putBoolean("phone_elliptical", settings.phoneElliptical)
            .putInt("phone_rect_compensation_v2", settings.phoneWideCorrection)
            .putBoolean("screen_detected", detectedScreenApplied).putBoolean("screen_custom", customScreenApplied)
            .putFloat("screen_width_cm", settings.screenWidthCm.toFloat())
            .putFloat("screen_height_cm", settings.screenHeightCm.toFloat()).apply()
    }

    private fun projectionKey(uri: Uri): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(uri.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun loadProjectionChoice() {
        val key = source?.let(::projectionKey)
        projectionMode = (if (key == null) prefs.getInt("projection_mode", 0) else
            videoPrefs.getInt("${key}_projection", prefs.getInt("projection_mode", 0)))
            .let { if (VideoProjection.valid(it)) it else 0 }
        layoutMode = (if (key == null) prefs.getInt("layout_mode", -1) else
            videoPrefs.getInt("${key}_layout", prefs.getInt("layout_mode", -1))).coerceIn(-1, 2)
    }

    private fun saveProjectionChoice() {
        val key = source?.let(::projectionKey)
        if (key == null) prefs.edit().putInt("projection_mode", projectionMode).putInt("layout_mode", layoutMode).apply()
        else videoPrefs.edit().putInt("${key}_projection", projectionMode).putInt("${key}_layout", layoutMode).apply()
    }

    private fun resolveProjection() {
        val format = videoFormat
        val stereo = when (format?.stereoMode) {
            C.STEREO_MODE_LEFT_RIGHT -> 0
            C.STEREO_MODE_TOP_BOTTOM -> 1
            C.STEREO_MODE_MONO -> 2
            else -> -1
        }
        val requested = VideoProjection.forViewingMode(projectionMode, settings.phoneMode)
        val selection = VideoProjection.resolve(requested, layoutMode, metadataProjection, stereo,
            if (source == null) "" else sourceName, format?.width ?: 0, format?.height ?: 0,
            format?.pixelWidthHeightRatio ?: 1f)
        settings = settings.copy(projectionDegrees = selection.degrees, layout = selection.layout, sourceProjection = selection.projection,
            cubePolesFlipped = selection.projection == VideoProjection.CUBEMAP && metadataProjection == VideoProjection.CUBEMAP)
        projectionButton.contentDescription = AppText.SOURCE_PROJECTION_180.text(VideoProjection.label(selection.projection), selection.reason)
        projectionWarning = if (metadataProjection < 0 && projectionMode == 0 && selection.projection in intArrayOf(180, 360))
            AppText.UNKNOWN_PROJECTION_TAG_CHOOSE_THE_SOURCE.text() else ""
    }

    private fun updateVideoFormat(format: Format?) {
        videoFormat = format
        metadataProjection = VideoProjection.metadataDegrees(format?.projectionData)
        applyAutomaticViewingMode()
        updateSettings(false)
        updateStatus()
    }

    private fun applyAutomaticViewingMode() {
        if (source == null || manualViewingChoice) return
        val format = videoFormat ?: return
        val key = projectionKey(source!!)
        val stereo = when (format.stereoMode) {
            C.STEREO_MODE_LEFT_RIGHT -> 0
            C.STEREO_MODE_TOP_BOTTOM -> 1
            else -> -1
        }
        // These already include defaults selected before opening a file, not just per-file choices.
        val explicitVrProjection = projectionMode > 0 || layoutMode in 0..1
        val vr = explicitVrProjection || VideoProjection.isVrVideo(sourceName,
            format.projectionData?.isNotEmpty() == true,stereo,format.width,format.height,format.pixelWidthHeightRatio)
        val useOrdinary = if (videoPrefs.contains("${key}_ordinary_override"))
            videoPrefs.getBoolean("${key}_ordinary_override",false) else !vr
        if (ordinaryVideo != useOrdinary)
            changeViewingMode(if (useOrdinary) 2 else if (settings.phoneMode) 1 else 0, automatic = true)
    }

    @Suppress("DEPRECATION")
    private fun openVideo(addToPlaylist: Boolean = false) {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, addToPlaylist)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, if (addToPlaylist) ADD_VIDEOS else OPEN_VIDEO)
        } catch (error: Exception) {
            notifyUser(AppText.COULD_NOT_OPEN_THE_FILE_PICKER.text(error.message), Toast.LENGTH_LONG)
        }
    }

    @Deprecated("Uses the platform picker without another Activity dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_VIDEOS && resultCode == RESULT_OK && data != null) {
            val uris = linkedSetOf<Uri>()
            data.data?.let { uris.add(it) }
            data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
            importVideos(uris.toList(), data.flags)
            return
        }
        if (requestCode != OPEN_VIDEO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 &&
            data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* Temporary access may still be sufficient for this session. */ }
        }
        openSource(uri)
    }

    private fun openPlaylistPage() {
        if (playlistOpen) return
        resetSeekSequence()
        closeVrPlaylist(false)
        resumeAfterPlaylist = shouldResumePlayback()
        pendingPlay = false
        player?.pause()
        tracker.stop()
        savePosition()
        gamepad.clearInput(); playlistInput.clear(); vrView.cancelPendingGestures(); stereoHint.clear()
        playlistOpen = true
        syncBitrateSampling()
        topBar.visibility = View.GONE; bottomBar.visibility = View.GONE
        videoTitle.isSelected = false
        if (!::playlistPage.isInitialized) {
            playlistPage = PlaylistPage(this, playlist, covers, { entry, fromStart ->
                closePlaylistPage(false)
                openSource(Uri.parse(entry.uri), if (fromStart) 0 else entry.position, entry.name)
            }, { openVideo(true) }, { closePlaylistPage(true) }, { id, target, after, ready ->
                playlist.submit({ playlist.moveRelative(id, target, after) }) { result ->
                    if (!closing) ready(result.getOrDefault(false))
                }
            }) { entry, ready ->
                val keepPermission = source?.toString() == entry.uri
                playlist.submit({
                    playlist.remove(entry.id)
                    if (!keepPermission) runCatching {
                        contentResolver.releasePersistableUriPermission(Uri.parse(entry.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }) { result ->
                    if (!closing) {
                        if (result.isSuccess) covers.invalidate(entry, removed = true)
                        ready(result.isSuccess)
                    }
                }
            }
            root.addView(playlistPage, FrameLayout.LayoutParams(-1, -1))
        }
        applyPageInsets(root.rootWindowInsets)
        playlistPage.present()
        syncBackCallback()
    }

    private fun closePlaylistPage(resume: Boolean) {
        if (!playlistOpen) return
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(playlistPage.windowToken, 0)
        playlistPage.hidePage()
        playlistOpen = false
        if (active && !ordinaryVideo && (source != null || debugMode)) tracker.start()
        gamepad.clearInput()
        if (resume && resumeAfterPlaylist && active && !debugMode) {
            if (player == null) { pendingPlay = true; ensurePlayer() } else player?.play()
        }
        resumeAfterPlaylist = false
        showControls(true)
        syncBackCallback()
    }

    private fun importVideos(uris: List<Uri>, flags: Int) {
        if (uris.isEmpty()) return
        notifyUser(AppText.ADDING_VIDEOS.text(uris.size))
        io.execute {
            var added = 0
            var temporary = 0
            for (uri in uris) {
                if (closing) break
                runCatching {
                    var persisted = false
                    if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 && flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                        persisted = runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
                    }
                    if (!persisted) temporary++
                    val name = runCatching {
                        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, importCancellation)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: AppText.LOCAL_VIDEO.text()
                    playlist.add(uri.toString(), name)
                    added++
                }
            }
            handler.post {
                if (!closing) {
                    if (playlistOpen) playlistPage.refresh()
                    notifyUser(AppText.PROCESSED_VIDEOS_DUPLICATE_FILES_ARE_SKIPPED.text(added, uris.size) +
                        if (temporary > 0) AppText.NPERSISTENT_ACCESS_WAS_NOT_GRANTED_FOR.text(temporary) else "", Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun toggleVrPlaylist() {
        if (ordinaryVideo || settings.phoneMode || settingsOpen || playlistOpen) return
        if (playlistInput.open) { closeVrPlaylist(true); return }
        beginControllerUse()
        gamepad.clearInput(); resetSeekSequence(); vrView.cancelPendingGestures(); stereoHint.clear()
        resumeAfterVrPlaylist = shouldResumePlayback()
        pendingPlay = false
        player?.pause()
        savePosition()
        vrPlaylist.settings = settings
        vrPlaylist.show(source?.toString())
        playlistInput.setOpen(true)
        syncBitrateSampling()
        syncBackCallback()
    }

    private fun closeVrPlaylist(resume: Boolean) {
        if (!::playlistInput.isInitialized || !playlistInput.open) return
        playlistInput.setOpen(false)
        syncBitrateSampling()
        vrPlaylist.close()
        gamepad.clearInput(); resetSeekSequence()
        if (resume && resumeAfterVrPlaylist && active && !debugMode) {
            if (player == null) { pendingPlay = true; ensurePlayer() } else player?.play()
        }
        resumeAfterVrPlaylist = false
        if (failure != null) { showControls(true) }
        syncBackCallback()
    }

    private fun openSource(uri: Uri, resumePosition: Long? = null, name: String? = null, autoplay: Boolean = true) {
        val exactSessionPosition = restoredLanguagePosition
        restoredLanguagePosition = null
        releasePlayer()
        source = uri
        manualViewingChoice = false
        sourceLoading = true
        videoFormat = null
        metadataProjection = 0
        loadProjectionChoice()
        positionMs = resumePosition?.coerceAtLeast(0) ?: 0
        pendingPlay = autoplay
        failure = null
        decoder = ""
        sourceName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: AppText.LOCAL_VIDEO.text()
        savedCoverProjection = ""
        updateSettings(false)
        vrView.vrRenderer.resetFrame()
        flatVideo.resetTransform(); flatVideo.setVideoSize(0, 0, 1f)
        tracker.recenter()
        val request = ++nameRequest
        val initialName = sourceName
        playlist.submit({ playlist.add(uri.toString(), initialName) }) { result ->
            if (closing || request != nameRequest || source != uri) return@submit
            val entry = result.getOrNull()
            positionMs = exactSessionPosition?.coerceIn(0, entry?.duration?.takeIf { it > 0 } ?: Long.MAX_VALUE)
                ?: PlaybackPosition.open(resumePosition, entry?.position ?: 0, entry?.duration ?: 0)
            if (name == null && sourceName == initialName && entry != null) sourceName = entry.name
            sourceLoading = false
            updateSettings(false)
            savePosition()
            if (result.isFailure) notifyUser(AppText.COULD_NOT_SAVE_PLAYBACK_HISTORY_CHECK.text(), Toast.LENGTH_LONG)
            if (active) ensurePlayer()
            updateStatus()
        }
        nameQuery?.cancel()
        val cancellation = android.os.CancellationSignal()
        nameQuery = cancellation
        io.execute {
            val displayName = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, cancellation)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull()
            handler.post {
                if (!isDestroyed && request == nameRequest && source == uri && displayName != null) {
                    sourceName = displayName
                    applyAutomaticViewingMode()
                    updateSettings(false)
                    savePosition()
                    updateStatus()
                }
            }
        }
        if (active) {
            if (!ordinaryVideo && (renderFailure || vrView.vrRenderer.output == null)) {
                renderFailure = false
                vrView.retryOutput()
            } else ensurePlayer()
        }
        showControls(true)
        updateStatus()
    }

    private fun ensurePlayer() {
        if (!active || player != null || (debugMode && !pendingPlay) || sourceLoading) return
        val uri = source ?: return
        val output = vrView.vrRenderer.output
        if (!ordinaryVideo && output == null) return
        failure = null
        try {
            val requestSdr = ordinaryVideo && !hdrEnabled && Build.VERSION.SDK_INT in 31..34
            var expectedPlayer: ExoPlayer? = null
            val created = ExoPlayer.Builder(this,
                BitrateRenderersFactory(this, bitrateMeter, requestSdr) {
                    if (expectedPlayer != null && player === expectedPlayer) hdrUnavailable()
                }.setEnableDecoderFallback(true)).build()
            expectedPlayer = created
            player = created
            // The circular display area must not make the track selector discard 4K source tracks.
            created.trackSelectionParameters = created.trackSelectionParameters.buildUpon()
                .clearVideoSizeConstraints().clearViewportSizeConstraints()
                .setForceHighestSupportedBitrate(true).build()
            created.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            created.setHandleAudioBecomingNoisy(true)
            created.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (player === created) frostedBars?.invalidateContent()
                }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    if (player !== created) return
                    // The renderer resets its sample window on seeks; clear the displayed old rate too.
                    detailText.setBitrate(AppText.BITRATE.text())
                }
                override fun onTracksChanged(tracks: Tracks) {
                    if (player !== created) return
                    val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                    val index = group?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) } }
                    updateVideoFormat(if (group != null && index != null) group.getTrackFormat(index) else null)
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (player !== created) return
                    if (playbackState == Player.STATE_READY && positionMs > 0 && created.duration > 0 && positionMs >= created.duration) {
                        positionMs = 0
                        created.seekTo(0)
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        boundarySeek.reached(1, SystemClock.elapsedRealtime())
                        seekAcceleration.reset()
                        positionMs = 0; savePosition(); showControls(true)
                    } else if (playbackState == Player.STATE_READY && created.currentPosition == 0L) {
                        boundarySeek.reached(-1, SystemClock.elapsedRealtime())
                    }
                    updateStatus()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (player !== created) return
                    if (isPlaying && debugMode) setDebugMode(false)
                    syncBitrateSampling()
                    // Pausing/buffering must preserve the user's toolbar visibility, including double taps.
                    if (source == null && !debugMode && active) { showControls(true) }
                    updateStatus()
                }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (player !== created) return
                    vrView.vrRenderer.videoWidth = videoSize.width
                    vrView.vrRenderer.videoHeight = videoSize.height
                    flatVideo.setVideoSize(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio)
                    updateVideoFormat(created.videoFormat ?: videoFormat)
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (player !== created) return
                    val failedFormat = (error as? ExoPlaybackException)?.rendererFormat
                    val failedHdrVideo = failedFormat?.sampleMimeType?.startsWith("video/") == true &&
                        (failedFormat.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 ||
                            failedFormat.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG ||
                            failedFormat.sampleMimeType == "video/dolby-vision")
                    if (requestSdr && !hdrEnabled && failedHdrVideo && error.errorCode in 4001..4006) {
                        hdrUnavailable()
                        return
                    }
                    val tip = when (error.errorCode) {
                        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                            AppText.THE_FILE_HAS_MOVED_OR_ACCESS.text()
                        PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                            AppText.THIS_DEVICE_CANNOT_DECODE_THE_VIDEO.text()
                        else -> AppText.TAP_PLAY_TO_RETRY_OR_SELECT.text()
                    }
                    failure = "${tip}\n${error.errorCodeName}"
                    showControls(true)
                    updateStatus()
                }
            })
            created.addAnalyticsListener(object : AnalyticsListener {
                override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String,
                    initializedTimestampMs: Long, initializationDurationMs: Long) {
                    if (player !== created) return
                    decoder = decoderName
                    updateStatus()
                }
                override fun onVideoDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
                    if (player !== created) return
                    if (decoder == decoderName) { decoder = ""; updateStatus() }
                }
            })
            if (ordinaryVideo) {
                if (Build.VERSION.SDK_INT >= 35) flatVideo.surfaceView.setDesiredHdrHeadroom(if (hdrEnabled) 0f else 1f)
                created.setVideoSurfaceView(flatVideo.surfaceView)
            } else created.setVideoSurface(output!!.surface)
            created.setPlaybackSpeed(PlaybackTuning.speed(speedIndex))
            created.setMediaItem(MediaItem.fromUri(uri), positionMs)
            created.prepare()
            created.playWhenReady = pendingPlay
            pendingPlay = false
        } catch (error: Exception) {
            failure = AppText.CANNOT_PLAY.text(error.message)
            player?.release()
            player = null
            showControls(true)
            updateStatus()
        }
    }

    private fun togglePlayback() {
        resetSeekSequence()
        if (source == null) { openVideo(); return }
        if (sourceLoading) { pendingPlay = !pendingPlay; updateStatus(); return }
        if (!ordinaryVideo && renderFailure) {
            releasePlayer()
            renderFailure = false
            failure = null
            pendingPlay = true
            vrView.retryOutput()
            return
        }
        if (player?.playerError != null) {
            releasePlayer()
            pendingPlay = true
            ensurePlayer()
            return
        }
        if (player == null) { pendingPlay = true; ensurePlayer(); return }
        player?.let {
            if (it.playbackState == Player.STATE_ENDED) { it.seekTo(0); it.play() }
            else if (it.playWhenReady) it.pause() else it.play()
        }
    }

    private fun resetSeekSequence() {
        seekAcceleration.reset()
        boundarySeek.cancel(SystemClock.elapsedRealtime())
        adjacentRequest++
        adjacentPending = false
    }

    private fun seekStep(direction: Int, freshPress: Boolean = true, screenDoubleStartedMs: Long = -1) {
        if (debugMode || sourceLoading || adjacentPending || !active || settingsOpen || playlistOpen || playlistInput.open) return
        val current = player ?: return
        if (current.playerError != null || !current.isCurrentMediaItemSeekable) return
        val now = SystemClock.elapsedRealtime()
        when (boundarySeek.seek(direction, current.currentPosition, current.duration,
            current.playbackState == Player.STATE_ENDED, !current.playWhenReady, freshPress, now, screenDoubleStartedMs)) {
            BoundarySeekSequence.SWITCH -> { playAdjacent(direction); return }
            BoundarySeekSequence.WAIT -> { seekAcceleration.reset(); return }
        }
        val deltaMs = seekAcceleration.next(direction, now)
        val target = PlaybackPosition.seek(current.currentPosition, deltaMs, current.duration)
        if (target == 0L) {
            pendingPlay = false
            current.pause()
            boundarySeek.reached(-1, now)
        }
        else if (current.duration > 0 && target >= current.duration) boundarySeek.reached(1, now)
        current.seekTo(target)
        val prefix = AppText.S.text(if (direction < 0) AppText.REWIND.text() else AppText.FORWARD.text(), kotlin.math.abs(deltaMs) / 1000)
        stereoHint.showValue("${prefix}${time(target)} / ${if (current.duration > 0) time(current.duration) else "--:--"}")
        updateStatus()
    }

    private fun playAdjacent(direction: Int) {
        val uri = source ?: return
        val expectedPlayer = player ?: return
        val request = ++adjacentRequest
        adjacentPending = true
        playlist.submit({ playlist.adjacent(uri.toString(), direction) }) { result ->
            if (request != adjacentRequest) return@submit
            adjacentPending = false
            if (closing || !active || source != uri || player !== expectedPlayer ||
                settingsOpen || playlistOpen || playlistInput.open) return@submit
            val entry = result.getOrNull()
            if (entry == null) {
                boundarySeek.cancel(SystemClock.elapsedRealtime())
                notifyUser(if (result.isFailure) AppText.COULD_NOT_READ_THE_PLAYLIST_TRY.text() else AppText.THE_PLAYLIST_IS_EMPTY.text())
                return@submit
            }
            vrView.cancelPendingGestures()
            gamepad.clearInput()
            openSource(Uri.parse(entry.uri), 0L, entry.name)
            boundarySeek.suppressUntilIdle(SystemClock.elapsedRealtime())
            stereoHint.showValue("${if (direction > 0) AppText.NEXT.text() else AppText.PREVIOUS.text()} · ${entry.name}")
        }
    }

    private fun changeIpd(steps: Int) {
        if (ordinaryVideo || settings.phoneMode) return
        settings = settings.stepIpd(steps)
        updateSettings()
        stereoHint.showCenters()
        stereoHint.showValue(String.format(Locale.ROOT, AppText.IPD_F_CM.text(), settings.ipdCm))
    }

    private fun recenterNow() {
        if (ordinaryVideo) return
        tracker.recenter()
        notifyUser(AppText.RECENTERED.text())
    }

    private fun toggleGyro() {
        if (ordinaryVideo) return
        tracker.setGyroEnabled(!tracker.gyroEnabled)
        prefs.edit().putBoolean("gyro_enabled", tracker.gyroEnabled).apply()
        notifyUser(if (tracker.gyroEnabled) AppText.GYROSCOPE_CONTROL_ON.text() else AppText.GYROSCOPE_CONTROL_OFF_USE_THE_LEFT.text())
        updateStatus()
    }

    private fun onControllerAction(action: GamepadState.Action, freshPress: Boolean = true) {
        if (!active || !hasWindowFocus() || ordinaryVideo) return
        beginControllerUse()
        if (action != GamepadState.Action.SEEK_BACK && action != GamepadState.Action.SEEK_FORWARD) resetSeekSequence()
        when (action) {
            GamepadState.Action.SEEK_BACK -> seekStep(-1, freshPress)
            GamepadState.Action.SEEK_FORWARD -> seekStep(1, freshPress)
            GamepadState.Action.PLAY_PAUSE -> togglePlayback()
            GamepadState.Action.RECENTER -> recenterNow()
            GamepadState.Action.TOGGLE_GYRO -> toggleGyro()
            GamepadState.Action.IPD_DOWN, GamepadState.Action.IPD_UP -> {
                changeIpd(if (action == GamepadState.Action.IPD_DOWN) -1 else 1)
            }
            GamepadState.Action.SPEED_DOWN, GamepadState.Action.SPEED_UP -> {
                changeSpeed(if (action == GamepadState.Action.SPEED_DOWN) -1 else 1)
            }
            GamepadState.Action.SPEED_RESET -> {
                speedIndex = PlaybackTuning.DEFAULT_SPEED
                changeSpeed(0)
            }
            GamepadState.Action.VOLUME_DOWN, GamepadState.Action.VOLUME_UP -> {
                val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    if (action == GamepadState.Action.VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
                // Read back the applied stream step after the audio service processes the change.
                handler.removeCallbacks(showVolume)
                handler.postDelayed(showVolume, 100)
            }
            GamepadState.Action.FOV_DOWN, GamepadState.Action.FOV_UP -> {
                changeFov(if (action == GamepadState.Action.FOV_DOWN) -1 else 1)
            }
        }
    }

    private fun beginControllerUse() {
        handler.removeCallbacks(hideNotice)
        noticeView?.visibility = View.GONE
    }

    private fun changeSpeed(direction: Int) {
        speedIndex = PlaybackTuning.stepSpeed(speedIndex, direction)
        player?.setPlaybackSpeed(PlaybackTuning.speed(speedIndex))
        stereoHint.showValue(AppText.SPEED_X.text(PlaybackTuning.speed(speedIndex)))
        updateStatus()
    }

    private fun changeFov(direction: Int) {
        if (ordinaryVideo) return
        settings = if (settings.phoneMode) settings.copy(phoneFovDegrees = Optics.stepPhoneFov(settings.phoneFovDegrees, direction))
            else settings.copy(fovDegrees = Optics.stepFov(settings.fovDegrees, direction))
        updateSettings()
        stereoHint.showValue(if (settings.phoneMode) AppText.HORIZONTAL_FOV.text(settings.phoneFovDegrees) else AppText.FOV.text(settings.fovDegrees))
    }

    private val showVolume = Runnable {
        if (active) {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val value = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            stereoHint.showValue(AppText.VOLUME.text(value, maximum))
        }
    }

    private fun notifyUser(message: String, duration: Int = Toast.LENGTH_SHORT) {
        handler.removeCallbacks(hideNotice)
        noticeView?.visibility = View.GONE
        val notice = noticeView ?: label("", 12f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = UiStyle.glass(this@MainActivity, UiStyle.MODAL, 16)
            elevation = dp(8).toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            root.addView(this, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(76); leftMargin = dp(24); rightMargin = dp(24)
            })
            noticeView = this
        }
        notice.text = message
        notice.visibility = View.VISIBLE
        notice.bringToFront()
        handler.postDelayed(hideNotice, if (duration == Toast.LENGTH_LONG) 3500L else 2200L)
    }

    private fun adjustFromController(volumeSteps: Int, horizontal: Float, seconds: Float) {
        if (!active || !hasWindowFocus() || ordinaryVideo) return
        beginControllerUse()
        if (volumeSteps != 0) onControllerAction(if (volumeSteps > 0) GamepadState.Action.VOLUME_UP else GamepadState.Action.VOLUME_DOWN)
        if (horizontal != 0f) {
            val current = if (brightness >= 0f) brightness else
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
            brightness = PlaybackTuning.adjustBrightness(current, horizontal, seconds)
            window.attributes = window.attributes.apply { screenBrightness = brightness }
            stereoHint.showValue(AppText.BRIGHTNESS.text(Math.round(brightness * 100)))
        }
        handler.removeCallbacks(persistSettings)
        handler.postDelayed(persistSettings, 300)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (startupReady && event.actionMasked == MotionEvent.ACTION_DOWN) {
            val bounds = android.graphics.Rect()
            if (!logoView.getGlobalVisibleRect(bounds) || !bounds.contains(event.rawX.toInt(), event.rawY.toInt())) logoView.cancelPending()
            // A toolbar press, including its empty padding, cannot complete a video double tap.
            if (settingsOpen || listOf(topBar, bottomBar).any {
                it.visibility == View.VISIBLE && it.getGlobalVisibleRect(bounds) &&
                    bounds.contains(event.rawX.toInt(), event.rawY.toInt())
            }) vrView.cancelPendingGestures()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (startupReady && active && hasWindowFocus()) {
            if (ordinaryVideo && (event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
                    event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
                    event.device?.let { it.supportsSource(InputDevice.SOURCE_GAMEPAD) || it.supportsSource(InputDevice.SOURCE_JOYSTICK) } == true)) return true
            if (playlistInput.handleKey(event, !ordinaryVideo && !settingsOpen && !playlistOpen && !settings.phoneMode)) return true
            if (!settingsOpen && !playlistOpen && !playlistInput.open && gamepad.handleKey(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (startupReady && active && hasWindowFocus()) {
            if (ordinaryVideo && (event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
                    event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
                    event.device?.let { it.supportsSource(InputDevice.SOURCE_GAMEPAD) || it.supportsSource(InputDevice.SOURCE_JOYSTICK) } == true)) return true
            if (playlistInput.handleMotion(event)) return true
            if (!settingsOpen && !playlistOpen && !playlistInput.open && gamepad.handleMotion(event)) return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun updateStatus() {
        if (!::detailText.isInitialized) return
        syncBitrateSampling()
        idleBackdrop.visibility = if (source == null && !debugMode) View.VISIBLE else View.GONE
        val current = player
        root.keepScreenOn = active && (debugMode || (failure == null && current?.playWhenReady == true && current.playbackState != Player.STATE_ENDED))
        if (!controlsVisible || settingsOpen || playlistOpen || playlistInput.open) return
        val title = if (source == null) AppText.CHOOSE_A_VIDEO_TO_PLAY.text() else if (debugMode) AppText.FIELD_OF_VIEW_DEBUG.text() else sourceName
        // Reassigning text on the 500ms ticker restarts Android's marquee; only update on a real change.
        if (videoTitle.text.toString() != title) { videoTitle.text = title; videoTitle.tooltipText = title }
        val titleScrolling = active && source != null && !debugMode
        if (videoTitle.isSelected != titleScrolling) videoTitle.isSelected = titleScrolling
        val speedLabel = "${PlaybackTuning.speed(speedIndex)}x"
        if (speedText.text.toString() != speedLabel) speedText.text = speedLabel
        val decoderInfo = if (decoder.isNotEmpty()) AppText.DECODER.text(decoder) else if (source != null) AppText.DECODER_INACTIVE.text() else ""
        val issue = failure ?: if (sourceLoading) AppText.LOADING_PLAYBACK_HISTORY.text()
            else if (current?.playbackState == Player.STATE_BUFFERING) AppText.BUFFERING.text()
            else if (ordinaryVideo) "" else projectionWarning
        val detail = if (debugMode) AppText.PLAYBACK_EXITS_DEBUG_MODE_OR_DOUBLE.text() else listOf(decoderInfo, issue.replace('\n', ' ')).filter { it.isNotEmpty() }.joinToString(" · ")
        detailText.setDetail(detail, source != null && !debugMode)
        detailText.visibility = if (source == null) View.GONE else View.VISIBLE
        val pausable = failure == null && (pendingPlay || (current?.playWhenReady == true && current.playbackState != Player.STATE_ENDED))
        playButton.contentDescription = if (failure != null) AppText.RETRY.text() else if (pausable) AppText.PAUSE.text() else AppText.PLAY.text()
        val nextIcon = if (pausable) "pause" else "play"
        if (playIcon != nextIcon) { playIcon = nextIcon; playButton.setImageDrawable(PlayerIcon(nextIcon)) }
        gyroButton.imageAlpha = if (tracker.gyroEnabled) 255 else 110
        gyroButton.contentDescription = if (tracker.gyroEnabled) AppText.TURN_GYROSCOPE_OFF_B.text() else AppText.TURN_GYROSCOPE_ON_B.text()
        gyroButton.tooltipText = gyroButton.contentDescription
        if (Build.VERSION.SDK_INT >= 30) gyroButton.stateDescription = if (tracker.gyroEnabled) AppText.ON_217.text() else AppText.OFF_218.text()
        if (!draggingSeek) {
            val duration = current?.duration ?: C.TIME_UNSET
            val position = current?.currentPosition ?: positionMs
            seek.isEnabled = !debugMode && duration > 0 && current?.isCurrentMediaItemSeekable == true
            seek.progress = if (duration > 0) (position * 1000 / duration).toInt().coerceIn(0, 1000) else 0
            val timeLabel = "${time(position)} / ${if (duration > 0) time(duration) else "--:--"}"
            if (timeText.text.toString() != timeLabel) timeText.text = timeLabel
        }
    }

    private fun savePosition() {
        if (sourceLoading) return
        player?.let { positionMs = if (it.playbackState == Player.STATE_ENDED) 0 else it.currentPosition.coerceAtLeast(0) }
        source?.let { uri ->
            val duration = player?.duration?.takeIf { it > 0 } ?: 0
            val name = sourceName; val position = positionMs
            playlist.submit({ playlist.save(uri.toString(), name, position, duration) }) { result ->
                if (!closing && result.isFailure) notifyUser(AppText.COULD_NOT_SAVE_PLAYBACK_POSITION_CHECK.text())
            }
        }
        lastSavedMs = SystemClock.elapsedRealtime()
    }

    private fun releasePlayer() {
        resetSeekSequence()
        boundarySeek.reset()
        savePosition()
        val previous = player
        player = null
        syncBitrateSampling()
        if (::detailText.isInitialized) detailText.setBitrate(AppText.BITRATE.text())
        decoder = ""
        previous?.clearVideoSurface()
        previous?.release()
    }

    private fun syncBitrateSampling() {
        val enabled = active && controlsVisible && !settingsOpen && !playlistOpen &&
            (!::playlistInput.isInitialized || !playlistInput.open) && !debugMode &&
            failure == null && player?.isPlaying == true
        if (enabled == bitrateSampling) return
        bitrateSampling = enabled
        bitrateMeter.setEnabled(enabled)
        handler.removeCallbacks(bitrateTicker)
        // Pause/hide stops collection while preserving the last displayed sample.
        if (enabled) handler.postDelayed(bitrateTicker, 1000)
    }

    private fun showControls(requested: Boolean) {
        val canHide = debugMode || source != null
        if (settingsOpen || playlistOpen || playlistInput.open) return
        val visible = requested || !canHide
        controlsVisible = visible
        if (!visible) frostedBars?.refresh()
        if (!visible) videoTitle.isSelected = false
        for (bar in arrayOf(topBar, bottomBar)) {
            bar.animate().cancel()
            if (visible) {
                if (bar.visibility != View.VISIBLE) bar.alpha = 0f
                bar.visibility = View.VISIBLE
                bar.animate().alpha(1f).setDuration(UiStyle.motionDuration(160)).setInterpolator(UiStyle.easing).start()
            } else {
                bar.animate().alpha(0f).setDuration(UiStyle.motionDuration(140)).withEndAction {
                    if (!controlsVisible) bar.visibility = View.GONE
                }.start()
            }
        }
        syncBitrateSampling()
        if (visible) updateStatus()
    }
    override fun onResume() {
        super.onResume()
        if (!startupReady) return
        active = true
        deviceStatus.setActive(true)
        configureWindow()
        if (!playlistOpen && !ordinaryVideo && (source != null || debugMode)) tracker.start()
        if (!ordinaryVideo) gamepad.start()
        syncBackCallback()
        renderFailure = false
        syncVideoMode()
        // The GL output may already exist if its ready callback arrived before Activity.onResume.
        ensurePlayer()
        if (!debugMode && source == null) { showControls(true) }
        frostedBars?.start()
        handler.post(ticker)
    }

    override fun onPause() {
        if (!startupReady) { super.onPause(); return }
        active = false
        deviceStatus.setActive(false)
        frostedBars?.stop()
        syncBitrateSampling()
        videoTitle.isSelected = false
        closeVrPlaylist(false)
        resumeAfterPlaylist = false
        playlistInput.clear()
        root.keepScreenOn = false
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(hideNotice)
        noticeView?.visibility = View.GONE
        pendingPlay = false
        tracker.stop()
        gamepad.stop()
        vrView.cancelPendingGestures()
        logoView.cancelPending()
        draggingSeek = false
        resetSeekSequence()
        stereoHint.clear()
        handler.removeCallbacks(showVolume)
        saveSettings()
        releasePlayer()
        vrView.queueEvent { vrView.vrRenderer.releaseOnGlThread() }
        if (vrResumed) { vrView.onPause(); vrResumed = false }
        super.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW &&
            level != android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && ::covers.isInitialized) covers.trimMemory()
    }

    override fun onDestroy() {
        closing = true
        if (::deviceStatus.isInitialized) deviceStatus.setActive(false)
        frostedBars?.close(); frostedBars = null
        confirmationDialog?.dismiss(); confirmationDialog = null
        navigationBack?.setEnabled(false)
        nameQuery?.cancel()
        importCancellation.cancel()
        if (::playlistInput.isInitialized) playlistInput.clear()
        if (::vrPlaylist.isInitialized) vrPlaylist.close(immediate = true)
        if (::playlistPage.isInitialized) playlistPage.release()
        val finishStorage = {
            io.execute { if (::playlist.isInitialized) playlist.closeWhenIdle() }
            io.shutdown()
        }
        if (::covers.isInitialized) covers.close(finishStorage) else finishStorage()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (startupReady) {
            savePosition()
            outState.putString("session_uri", source?.toString())
            outState.putString("session_name", sourceName)
            outState.putLong("session_position", if (languageChanging) languagePositionMs else positionMs)
            outState.putBoolean("session_loading", sourceLoading)
            outState.putInt("session_speed", speedIndex)
            outState.putBoolean("playlist_open", playlistOpen)
            outState.putBoolean("settings_open", settingsOpen)
            outState.putBoolean("language_resume", languageChanging && resumeAfterLanguageChange)
            outState.putBoolean("language_changed", languageChanging)
            outState.putBoolean("session_manual_viewing", manualViewingChoice)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val orientationChanged = resources.configuration.orientation != newConfig.orientation
        super.onConfigurationChanged(newConfig)
        if (startupReady) {
            configureWindow()
            (confirmationDialog as? SettingsDialog)?.fitWindow()
            if (orientationChanged) tracker.recenter()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && startupReady) configureWindow()
        if (!hasFocus && startupReady) {
            vrView.cancelPendingGestures()
            logoView.cancelPending()
            gamepad.clearInput(); resetSeekSequence(); closeVrPlaylist(false); playlistInput.clear()
        }
    }

    private fun time(milliseconds: Long): String {
        val seconds = milliseconds.coerceAtLeast(0) / 1000
        return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }

    companion object { private const val OPEN_VIDEO = 10; private const val ADD_VIDEOS = 11 }
}


