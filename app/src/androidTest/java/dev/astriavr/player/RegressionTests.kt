package dev.astriavr.player

import android.content.Intent
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.View
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Platform runner only; no downloaded test dependencies. Runs in the isolated .validation app. */
@Suppress("DEPRECATION")
class RegressionTests : InstrumentationTestCase() {
    private val context get() = instrumentation.targetContext
    private var activity: MainActivity? = null

    override fun setUp() {
        super.setUp()
        check(context.packageName.endsWith(".validation")) { "Never run against the distribution app" }
        context.getSharedPreferences("astriavr-settings", 0).edit().clear()
            .putBoolean("ordinary_video", true).putBoolean("screen_setup_done", true).commit()
        AppLanguage.load(context)
        context.deleteDatabase("playlist.db")
        CrashLog.clear(context)
    }

    private fun await(latch: CountDownLatch) { assertTrue("background operation timed out", latch.await(20, TimeUnit.SECONDS)) }
    private fun main(work: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { work() } catch(error: Throwable) { failure=error } }
        instrumentation.waitForIdleSync()
        failure?.let { throw it }
    }
    private fun eventually(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < end) {
            var success = false
            main { success = condition() }
            if (success) return
            SystemClock.sleep(50)
        }
        fail(message)
    }
    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(target)
    private fun invoke(target: Any, name: String, vararg args: Any?) {
        target.javaClass.declaredMethods.single { it.name==name && it.parameterCount==args.size }
            .apply { isAccessible=true }.invoke(target,*args)
    }
    private fun sample(): Uri {
        val file = File(context.filesDir, "test-pattern.mp4")
        instrumentation.context.assets.open("test-pattern.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(file)
    }
    private fun launch(): MainActivity {
        assertFalse("Unlock the connected phone before UI regression checks",
            (context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked)
        return (instrumentation.startActivitySync(Intent(context,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity).also {
                activity=it; instrumentation.waitForIdleSync()
                main { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
    }

    private fun captureView(view: View, name: String) {
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(context.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }

    override fun tearDown() {
        activity?.let { main { it.finish() } }
        instrumentation.waitForIdleSync()
        super.tearDown()
    }

    fun testDefaultLanguageAndSavedChoice() {
        assertFalse("Fresh settings default to Chinese", AppText.isEnglish())
        AppLanguage.select(context, true)
        AppText.setEnglish(false)
        AppLanguage.load(context)
        assertTrue("Explicit English selection survives reload", AppText.isEnglish())
        AppLanguage.select(context, false)
        AppText.setEnglish(true)
        AppLanguage.load(context)
        assertFalse("Explicit Chinese selection survives reload", AppText.isEnglish())
    }

    fun testLauncherIconIsAdaptiveOnSupportedAndroidVersions() {
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue("Android 8-12 must also receive an adaptive icon, not a legacy square bitmap",
            icon is android.graphics.drawable.AdaptiveIconDrawable)
    }

    fun testLanguageRecreationPreservesVideoAndSettings() {
        var screen = launch()
        val uri = sample()
        main { invoke(screen, "openSource", uri, 4500L, "test-pattern.mp4", false, true) }
        eventually("paused sample not ready") { (field(screen, "player") as? ExoPlayer)?.playbackState == Player.STATE_READY }
        main { invoke(screen, "openSettingsPage") }
        for (english in listOf(true, false)) {
            val shouldPlay = !english
            main { (field(screen, "player") as ExoPlayer).playWhenReady = shouldPlay }
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            try {
                main { ((field(screen, "languageSettingsCard") as android.view.ViewGroup).getChildAt(1) as android.widget.Button).performClick() }
                screen = instrumentation.waitForMonitorWithTimeout(monitor, 20000) as? MainActivity
                    ?: throw AssertionError("language switch did not recreate the screen")
                activity = screen
            } finally { instrumentation.removeMonitor(monitor) }
            eventually("translated player not ready") { (field(screen, "player") as? ExoPlayer)?.playbackState == Player.STATE_READY }
            main {
                val player = field(screen, "player") as ExoPlayer
                assertEquals(shouldPlay, player.playWhenReady)
                if (!shouldPlay) assertTrue("paused position was lost", player.currentPosition in 4400..4600)
                assertEquals(uri, field(screen, "source"))
                assertEquals(true, field(screen, "settingsOpen"))
                assertEquals(if (english) "en" else "zh", screen.resources.configuration.locales[0].language)
                AppLanguage.load(context)
                assertEquals(english, AppText.isEnglish())
                assertEquals(if (english) "Standard video" else "普通视频", (field(screen, "viewingModeButton") as TextView).text.toString())
            }
        }
    }

    fun testBilingualSettingsFitNarrowAndWideLayouts() {
        fun verifyButtons(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (view is android.widget.Button) {
                val layout = view.layout ?: throw AssertionError("Unmeasured button")
                val available = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
                for (line in 0 until layout.lineCount)
                    assertTrue("Button overflow: ${view.text}", layout.getLineWidth(line) <= available + 1f)
                assertTrue("Button clipped vertically: ${view.text}", layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom + 1)
                assertEquals("Button truncated: ${view.text}", view.text.length, layout.getLineEnd(layout.lineCount - 1))
            }
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) verifyButtons(view.getChildAt(i))
        }
        for (english in listOf(false, true)) {
            main { AppLanguage.select(context, english) }
            val screen = launch()
            main {
                invoke(screen, "openSettingsPage")
                val page = field(screen, "settingsPage") as View
                for (mode in listOf(0, 1, 2, 0, 2)) {
                    invoke(screen, "setViewingMode", mode)
                    for (widthDp in listOf(600, 740, 840)) {
                        val width = UiStyle.dp(screen, widthDp)
                        val height = UiStyle.dp(screen, 360)
                        repeat(3) {
                            page.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                            page.layout(0, 0, width, height)
                        }
                        verifyButtons(page)
                        val modeButton = field(screen, "viewingModeButton") as View
                        assertEquals("Wider settings button", UiStyle.dp(screen, 156), modeButton.width)
                        assertEquals("Settings button height", UiStyle.dp(screen, 40), modeButton.height)
                        val modeCard = field(screen, "viewingModeCard") as android.view.ViewGroup
                        assertEquals("Title row height stays unchanged", UiStyle.dp(screen, 44), modeCard.getChildAt(0).minimumHeight)
                        if (mode != 2) {
                            val tuning = field(screen, "debugButton") as View
                            assertEquals("Header width stays unchanged", UiStyle.dp(screen, 128), tuning.width)
                            assertEquals("Compact header button height", UiStyle.dp(screen, 40), tuning.height)
                        }
                        val language = field(screen, "languageSettingsCard") as View
                        assertEquals("Bilingual language title", "语言/Language",
                            ((language as android.view.ViewGroup).getChildAt(0) as TextView).text.toString())
                        assertTrue("Language follows viewing mode", language.top >= modeCard.bottom)
                        val body = field(screen, "settingsBody") as View
                        assertTrue("Language must occupy half a row", kotlin.math.abs(language.width * 2 + UiStyle.dp(screen, 12) - body.width) <= 2)
                        val columns = field(screen, "settingsColumns") as android.widget.LinearLayout
                        assertEquals("Settings must stay side by side", android.widget.LinearLayout.HORIZONTAL, columns.orientation)
                        assertTrue("Left and right cards overlap", columns.getChildAt(0).right < columns.getChildAt(1).left)
                        if (mode != 2) {
                            assertTrue("Language precedes Picture", language.bottom <= (field(screen, "visualCard") as View).top)
                        } else {
                            val picture = field(screen, "ordinaryPictureCard") as View
                            val controls = field(screen, "ordinaryControlsCard") as View
                            assertSame("2D picture on the left", columns.getChildAt(0), picture.parent)
                            assertSame("2D controls on the right", columns.getChildAt(1), controls.parent)
                            assertTrue("Language precedes 2D Picture", language.bottom <= picture.top)
                            val right = columns.getChildAt(1) as android.view.ViewGroup
                            assertEquals("Only controls occupy the right column", 1, (0 until right.childCount).count { right.getChildAt(it).visibility == View.VISIBLE })
                        }
                        captureView(page, "settings-${if (english) "en" else "zh"}-$mode-$widthDp.png")
                    }
                }
                screen.finish()
            }
            instrumentation.waitForIdleSync()
            activity = null
        }
    }

    fun testTwoChoiceSettingsToggleWithoutDialogs() {
        val screen = launch()
        main {
            invoke(screen, "openSettingsPage")
            fun verifyToggle(name: String, value: (RenderSettings) -> Boolean) {
                val button = field(screen, name) as android.widget.Button
                val initial = value(field(screen, "settings") as RenderSettings)
                button.performClick()
                assertEquals("First click toggles $name", !initial, value(field(screen, "settings") as RenderSettings))
                assertNull("No selector for $name", field(screen, "confirmationDialog"))
                button.performClick()
                assertEquals("Second click restores $name", initial, value(field(screen, "settings") as RenderSettings))
                assertNull("No selector on return for $name", field(screen, "confirmationDialog"))
            }
            invoke(screen, "setViewingMode", 1)
            verifyToggle("phoneAspectButton") { it.phoneFillScreen }
            verifyToggle("phoneShapeButton") { it.phoneElliptical }
            verifyToggle("phoneEyeButton") { it.swapEyes }
            invoke(screen, "setViewingMode", 0)
            verifyToggle("swapButton") { it.swapEyes }
            for (name in listOf("projectionButton", "layoutButton", "viewingModeButton")) {
                (field(screen, name) as android.widget.Button).performClick()
                val dialog = field(screen, "confirmationDialog") as? android.app.Dialog
                assertTrue("Multi-choice selector still opens for $name", dialog?.isShowing == true)
                dialog?.dismiss()
            }
        }
    }

    fun testReinstalledSameVersionDoesNotReopenOldCrash() {
        CrashLog.record(context, "regression", IllegalStateException("isolated diagnostic fixture"))
        assertTrue("a crash from this installation is reported", CrashLog.hasReport(context))
        val report = File(context.filesDir, "last-crash.txt")
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        assertTrue(report.setLastModified((installedAt - 1000).coerceAtLeast(1)))
        assertFalse("an earlier 4.0 installation must not block the updated player", CrashLog.hasReport(context))
        assertTrue("old diagnostics remain available", CrashLog.read(context).contains("isolated diagnostic fixture"))
        CrashLog.clear(context)
    }

    fun testCompletedVideoDoesNotReplayWhenOutputChanges() {
        val screen = launch()
        main { invoke(screen, "openSource", sample(), 0L, "ordinary.mp4", true, true) }
        eventually("sample starts") { (field(screen, "player") as? ExoPlayer)?.isPlaying == true }
        main {
            val player = field(screen, "player") as ExoPlayer
            player.seekTo(player.duration)
        }
        eventually("sample ends") { (field(screen, "player") as? ExoPlayer)?.playbackState == Player.STATE_ENDED }
        main { invoke(screen, "setViewingMode", 1) }
        eventually("VR player prepared") { (field(screen, "player") as? ExoPlayer)?.playbackState == Player.STATE_READY }
        main { assertFalse("switching after end stays paused", (field(screen,"player") as ExoPlayer).playWhenReady) }
    }

    fun testDatabaseQueueCancellationAndLiteralSearch() {
        val store=PlaylistStore(context)
        val writes=CountDownLatch(1)
        store.submit({
            assertFalse("DB write must be off main", Looper.myLooper()==Looper.getMainLooper())
            store.add("content://test/literal","100%_movie.mp4")
            store.add("content://test/other","100xyzmovie.mp4")
        }) { assertTrue(it.isSuccess); writes.countDown() }
        await(writes)
        val searched=CountDownLatch(1)
        store.queryAsync("%_") { result ->
            assertEquals(Looper.getMainLooper(),Looper.myLooper())
            result.getOrThrow().use { assertEquals(1,it.count) }
            searched.countDown()
        }
        await(searched)
        val entered=CountDownLatch(1); val gate=CountDownLatch(1); val drained=CountDownLatch(1)
        store.submit({ entered.countDown(); await(gate) })
        await(entered)
        val delivered=AtomicInteger()
        val cancellation=store.queryAsync { result -> result.getOrNull()?.close(); delivered.incrementAndGet() }
        cancellation.cancel(); gate.countDown()
        store.submit({}) { drained.countDown() }
        await(drained); instrumentation.waitForIdleSync()
        assertEquals("cancelled cursor must not reach UI",0,delivered.get())
        store.closeWhenIdle()
    }

    fun testCoverOwnershipCancellationAndDrain() {
        val uri=sample(); val store=PlaylistStore(context)
        val entry=store.add(uri.toString(),"ordinary.mp4")
        store.setProjection(uri.toString(),2,0)
        val ordinary=store.find(uri.toString())!!
        val covers=CoverRepository(context,store)
        val first=Any(); val second=Any(); val obsolete=AtomicInteger(); val done=CountDownLatch(1)
        main {
            covers.load(ordinary,first) { _,_ -> obsolete.incrementAndGet() }
            covers.load(ordinary,second) { _,_ -> obsolete.incrementAndGet() }
            // Rebinding the same owner replaces its callback; cancellation leaves other owners intact.
            covers.load(ordinary,second) { bitmap,info ->
                assertNotNull(bitmap); assertEquals(320,bitmap!!.width); assertEquals(180,bitmap.height)
                assertTrue(info.metadataLoaded); assertTrue(info.duration>0); done.countDown()
            }
            covers.cancel(first)
        }
        await(done); assertEquals(0,obsolete.get())
        val closed=CountDownLatch(1)
        main { covers.close { store.closeWhenIdle(); closed.countDown() } }
        await(closed)
        assertTrue(entry.id>0)
    }

    fun testControllerPlaylistKeepsBarsHiddenAndDrawsBothEyes() {
        val uri = sample()
        val secondFile = File(context.filesDir, "second_VR180.mp4")
        File(uri.path!!).copyTo(secondFile, overwrite = true)
        val secondUri = Uri.fromFile(secondFile)
        val screen = launch()
        main { invoke(screen, "openSource", uri, 0L, "test-pattern.mp4", false, true) }
        eventually("first video ready") { (field(screen, "player") as? ExoPlayer)?.playbackState == Player.STATE_READY }
        main { invoke(screen, "setViewingMode", 0) }
        eventually("VR mode ready") {
            screen.hasWindowFocus() && (field(screen, "player") as? ExoPlayer)?.playbackState == Player.STATE_READY
        }
        val added = CountDownLatch(1)
        main {
            val store = field(screen, "playlist") as PlaylistStore
            store.submit({ store.add(secondUri.toString(), secondFile.name) }) {
                assertTrue(it.isSuccess); added.countDown()
            }
            invoke(screen, "showControls", true)
            invoke(screen, "onControllerAction", GamepadState.Action.RECENTER, true)
            assertFalse("Controller action hides controls", field(screen, "controlsVisible") as Boolean)
            (field(screen, "vrView") as VrView).onTap()
            assertTrue("Touch can reveal controls again", field(screen, "controlsVisible") as Boolean)
        }
        await(added)
        main { invoke(screen, "toggleVrPlaylist") }
        val view = field(screen, "vrPlaylist") as VrPlaylistView
        eventually("playlist loaded and bars hidden") {
            field(view, "displayCount") == 2 && view.alpha == 1f &&
                (field(screen, "topBar") as View).visibility == View.GONE &&
                (field(screen, "bottomBar") as View).visibility == View.GONE
        }
        main {
            val saved = view.settings
            for (ipd in listOf(5f, 6.5f, 8f)) {
                view.settings = saved.copy(ipdCm = ipd).fitScreen()
                val layout = view.settings.opticalLayout(view.width, view.height)
                val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
                val y = view.height - layout.y - layout.eyeHeight
                val left = Bitmap.createBitmap(image, layout.leftX, y, layout.eyeWidth, layout.eyeHeight)
                val right = Bitmap.createBitmap(image, layout.rightX, y, layout.eyeWidth, layout.eyeHeight)
                assertTrue("Both eyes draw the same playlist at IPD $ipd", left.sameAs(right))
                val pixels = IntArray(left.width * left.height)
                left.getPixels(pixels, 0, left.width, 0, 0, left.width, left.height)
                assertTrue("Eye image contains visible playlist content", pixels.any { it ushr 24 != 0 })
                left.recycle(); right.recycle(); image.recycle()
            }
            view.settings = saved
            view.move(if ((field(view, "selected") as Int) == 0) 1 else -1)
            assertEquals(secondUri.toString(), view.selection()!!.uri)
            val input = field(screen, "playlistInput") as PlaylistInput
            input.handleKey(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_UP), true)
            assertEquals(secondUri, field(screen, "source"))
            assertFalse("Selecting another video must never reveal controls", field(screen, "controlsVisible") as Boolean)
            input.handleKey(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_UP), true)
        }
        eventually("selected video starts") { (field(screen, "player") as? ExoPlayer)?.isPlaying == true }
        main {
            assertFalse("Asynchronous preparation keeps controls hidden", field(screen, "controlsVisible") as Boolean)
            assertEquals(View.GONE, (field(screen, "topBar") as View).visibility)
            assertEquals(View.GONE, (field(screen, "bottomBar") as View).visibility)
        }
    }

    fun testPlaybackResumeModesAndListLifecycle() {
        val uri=sample(); val screen=launch()
        fun current()=field(screen,"player") as? ExoPlayer
        main { invoke(screen,"openSource",uri,null,"test-pattern.mp4",false,true) }
        eventually("ordinary video never became ready") { current()?.playbackState==Player.STATE_READY }
        main {
            assertEquals(12000L,current()!!.duration)
            current()!!.seekTo(4500)
            invoke(screen,"savePosition")
            invoke(screen,"stopCurrentVideo")
            invoke(screen,"openSource",uri,null,"test-pattern.mp4",false,true)
        }
        eventually("reopened video never became ready") { current()?.playbackState==Player.STATE_READY }
        main { assertTrue("picker resume was lost",current()!!.currentPosition in 4400..4600) }
        for (mode in listOf(0,1,2)) {
            main { invoke(screen,"setViewingMode",mode) }
            eventually("mode $mode failed to prepare") { current()?.playbackState==Player.STATE_READY }
            main {
                assertFalse("switching mode started paused playback",current()!!.playWhenReady)
                assertTrue("switching mode lost position",current()!!.currentPosition in 4400..4600)
            }
        }
        main { invoke(screen,"openPlaylistPage") }
        val page=field(screen,"playlistPage") as PlaylistPage
        eventually("playlist query never completed") { (field(page,"count") as TextView).text.toString()=="1" }
        main {
            assertEquals(View.VISIBLE,page.visibility)
            captureView(page,"playlist-check.png")
            invoke(screen,"closePlaylistPage",false)
            invoke(screen,"openPlaylistPage")
        }
        eventually("rapid reopen hid the playlist") { page.visibility==View.VISIBLE && field(page,"query")==null }
        main {
            invoke(screen,"closePlaylistPage",false)
            invoke(screen,"openSource",uri,0L,"test-pattern.mp4",false,true)
        }
        eventually("restart never became ready") { current()?.playbackState==Player.STATE_READY }
        main { assertEquals("explicit restart must ignore history",0L,current()!!.currentPosition) }
        main { invoke(screen,"openSettingsPage") }
        eventually("ordinary controls card is hidden") { (field(screen,"ordinaryControlsCard") as View).isShown }
        main { captureView(field(screen,"settingsPage") as View,"settings-check.png"); invoke(screen,"closeSettingsPage") }
        assertFalse("unexpected crash report",CrashLog.hasReport(context))
    }
}
