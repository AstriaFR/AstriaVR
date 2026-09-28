package dev.astriavr.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.opengles.GL10

data class RenderSettings(val ipdCm: Float = 6.5f, val layout: Int = 0, val swapEyes: Boolean = false,
    val fovDegrees: Int = 88, val edgeCorrection: Int = 50, val eyeDiameterCm: Float = 5f,
    val screenWidthCm: Double = Optics.WIDTH_CM, val screenHeightCm: Double = Optics.HEIGHT_CM,
    val projectionDegrees: Int = 180, val phoneMode: Boolean = false, val phoneFovDegrees: Int = 90,
    val phoneFillScreen: Boolean = false, val phoneWideCorrection: Int = 60,
    val phoneElliptical: Boolean = false, val sourceProjection: Int = 180,
    val cubePolesFlipped: Boolean = false) {
    fun opticalLayout(width: Int, height: Int) = Optics.Layout(width, height, ipdCm,
        eyeDiameterCm, screenWidthCm, screenHeightCm)
    fun stepIpd(steps: Int) = copy(ipdCm = Optics.stepIpd(ipdCm, steps, eyeDiameterCm, screenWidthCm))
    fun fitScreen() = copy(eyeDiameterCm = Optics.fitDiameter(eyeDiameterCm, screenWidthCm, screenHeightCm)).stepIpd(0)
}

class VideoOutput(val texture: SurfaceTexture, val surface: Surface, val textureId: Int) {
    fun release() {
        texture.setOnFrameAvailableListener(null)
        surface.release()
        texture.release()
    }
}

class VrView(context: Context, tracker: HeadTracker) : GLSurfaceView(context) {
    var onTap: () -> Unit = {}
    var onDoubleTap: () -> Unit = {}
    var onSeekTap: (Int, Long) -> Unit = { _, _ -> }
    var onTouchActive: (Boolean) -> Unit = {}
    var tapTarget: View = this
    var onOutput: (VideoOutput) -> Unit = {}
    var onOutputRetired: (VideoOutput) -> Unit = { it.release() }
    var onError: (String) -> Unit = {}
    var dragEnabled = true
    var onDrag: (Float, Float) -> Unit = { _, _ -> }
    val vrRenderer = VrRenderer(tracker,
        { output -> post { onOutput(output) } },
        { error -> post { onError(error) } },
        { requestRender() },
        { retired -> post { onOutputRetired(retired) } })
    private val touchConfig = ViewConfiguration.get(context)
    private val taps = PlaybackTapSequence(PlaybackTapSequence.DOUBLE_TAP_MS, touchConfig.scaledDoubleTapSlop.toFloat())
    private val touchSlop = maxOf(touchConfig.scaledTouchSlop.toFloat(), context.resources.displayMetrics.density * 12f)
    private var tapCandidate = false
    private var multiplePointers = false
    private var trackingTouch = false
    private var draggingView = false
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private val confirmTap = object : Runnable {
        override fun run() {
            dispatchTap(taps.confirmSingle(android.os.SystemClock.uptimeMillis()))
            val delay = taps.singleDelay(android.os.SystemClock.uptimeMillis())
            if (delay >= 0) postDelayed(this, delay.coerceAtLeast(1))
        }
    }
    private fun moveView(current: MotionEvent, distanceX: Float, distanceY: Float) {
        if (!dragEnabled || multiplePointers || current.pointerCount != 1) return
        val currentSettings = vrRenderer.settings
        if (currentSettings.phoneMode) {
            val viewport = Optics.PhoneViewport(width, height, currentSettings.phoneFillScreen)
            val top = height - viewport.y - viewport.height
            if (downX < viewport.x || downX >= viewport.x + viewport.width ||
                downY < top || downY >= top + viewport.height) return
            if (currentSettings.phoneElliptical &&
                !Optics.insidePhoneEllipse(downX - viewport.x, downY - top, viewport.width, viewport.height)) return
            val from = Optics.phoneRayAngles(current.x + distanceX - viewport.x, current.y + distanceY - top,
                viewport.width, viewport.height, vrRenderer.displayedFovDegrees, currentSettings.phoneWideCorrection,
                currentSettings.phoneElliptical)
            val to = Optics.phoneRayAngles(current.x - viewport.x, current.y - top,
                viewport.width, viewport.height, vrRenderer.displayedFovDegrees, currentSettings.phoneWideCorrection,
                currentSettings.phoneElliptical)
            onDrag(from[0] - to[0], from[1] - to[1])
            return
        }
        val area = currentSettings.opticalLayout(width, height)
        val angle = vrRenderer.displayedFovDegrees
        if (area.eyeWidth > 0 && area.eyeHeight > 0)
            onDrag(distanceX * angle / area.eyeWidth, -distanceY * angle / area.eyeHeight)
    }

    init {
        setEGLContextClientVersion(2)
        // The six-integer chooser requires an EXACT alpha-channel match. Accept both RGBX
        // and RGBA device configs instead of failing on drivers that only expose RGBA8888.
        setEGLConfigChooser { egl, display ->
            CrashLog.phase = AppText.SELECTING_EGL_CONFIGURATION.text()
            val spec = intArrayOf(
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                0x3040, 4, // EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT
                EGL10.EGL_NONE)
            val count = IntArray(1)
            check(egl.eglChooseConfig(display, spec, null, 0, count) && count[0] > 0) {
                AppText.NO_RGB_WINDOW_CONFIGURATION_SUPPORTS_OPENGL.text(egl.eglGetError().toString(16))
            }
            val configs = arrayOfNulls<EGLConfig>(count[0])
            check(egl.eglChooseConfig(display, spec, configs, configs.size, count)) { AppText.COULD_NOT_READ_EGL_CONFIGURATION.text() }
            fun attribute(config: EGLConfig, name: Int): Int {
                val value = IntArray(1)
                return if (egl.eglGetConfigAttrib(display, config, name, value)) value[0] else 0
            }
            // This full-screen pass needs no depth/stencil or multisampling buffers.
            configs.filterNotNull().minBy { config ->
                attribute(config, EGL10.EGL_SAMPLES) * 1000 +
                    attribute(config, EGL10.EGL_DEPTH_SIZE) * 10 +
                    attribute(config, EGL10.EGL_STENCIL_SIZE) * 10 + attribute(config, EGL10.EGL_ALPHA_SIZE)
            }
        }
        setRenderer(vrRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        tracker.onPoseChanged = { requestRender() }
    }

    /** Main thread must detach/release the player before rebuilding its output. */
    fun retryOutput() { queueEvent { vrRenderer.releaseOnGlThread(); vrRenderer.onSurfaceCreated(null, null); requestRender() } }

    fun cancelPendingGestures() {
        cancelTapSequence()
        trackingTouch = false; draggingView = false
        onTouchActive(false)
    }

    private fun cancelTapSequence() { tapCandidate = false; taps.cancel(); removeCallbacks(confirmTap) }
    private fun dispatchTap(action: PlaybackTapSequence.Action) {
        when (action) {
            PlaybackTapSequence.Action.SINGLE -> tapTarget.performClick()
            PlaybackTapSequence.Action.TOGGLE -> onDoubleTap()
            PlaybackTapSequence.Action.BACK -> onSeekTap(-1, taps.seekDoubleStartedAt())
            PlaybackTapSequence.Action.FORWARD -> onSeekTap(1, taps.seekDoubleStartedAt())
            else -> Unit
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean = handleVideoTouch(event, width)

    fun handleVideoTouch(event: MotionEvent, areaWidth: Int): Boolean {
        // Use delivery uptime consistently: queued MotionEvent timestamps must not consume the
        // visible single-tap waiting interval before the application receives the first up.
        val now = android.os.SystemClock.uptimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onTouchActive(true)
                removeCallbacks(confirmTap)
                multiplePointers = false; tapCandidate = true; trackingTouch = true; draggingView = false
                downX = event.x; downY = event.y; downTime = now
                lastX = downX; lastY = downY
                taps.down(now, event.x, event.y, areaWidth.toFloat())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX; val dy = event.y - downY
                if (trackingTouch && !multiplePointers) {
                    if (!draggingView && dx * dx + dy * dy > touchSlop * touchSlop) {
                        draggingView = true; cancelTapSequence()
                    }
                    if (draggingView) {
                        moveView(event, lastX - event.x, lastY - event.y)
                        lastX = event.x; lastY = event.y
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> { multiplePointers = true; draggingView = false; cancelTapSequence() }
            MotionEvent.ACTION_CANCEL -> cancelPendingGestures()
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val dx = event.x - downX; val dy = event.y - downY
            if (trackingTouch && tapCandidate && !multiplePointers && dx * dx + dy * dy <= touchSlop * touchSlop &&
                now - downTime <= ViewConfiguration.getLongPressTimeout()) {
                dispatchTap(taps.up(now))
                val delay = taps.singleDelay(now)
                if (delay >= 0) postDelayed(confirmTap, delay)
            } else cancelTapSequence()
            tapCandidate = false; trackingTouch = false; draggingView = false
            onTouchActive(false)
        }
        return true
    }
    override fun onDetachedFromWindow() { cancelPendingGestures(); super.onDetachedFromWindow() }
    override fun performClick(): Boolean { super.performClick(); onTap(); return true }
}

class VrRenderer(
    private val tracker: HeadTracker,
    private val outputReady: (VideoOutput) -> Unit,
    private val reportError: (String) -> Unit,
    private val requestFrame: () -> Unit,
    private val retireOutput: (VideoOutput) -> Unit,
) : GLSurfaceView.Renderer {
    @Volatile var settings = RenderSettings()
        set(value) { if (field != value) { field = value; requestFrame() } }
    @Volatile var debugMode = false
        set(value) { if (field != value) { field = value; requestFrame() } }
    @Volatile var displayedFovDegrees = 88f
        private set
    private val fovTransition = FovTransition()
    private var cachedFov = Float.NaN
    @Volatile var output: VideoOutput? = null
        private set
    @Volatile var videoWidth = 3840
    @Volatile var videoHeight = 2160
    private val frameAvailable = AtomicBoolean(false)
    private val clearFrame = AtomicBoolean(false)
    private var hasFrame = false
    private var program = 0
    private var width = 0
    private var height = 0
    private val textureMatrix = FloatArray(16)
    private val pose = FloatArray(9)
    private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }
    private var cachedSettings: RenderSettings? = null
    private var cachedWidth = -1
    private var cachedHeight = -1
    private var opticalLayout = Optics.Layout(2800, 1260, 6.5f)
    private var eyeRects = arrayOf(Optics.eyeRect(0, 0, false), Optics.eyeRect(0, 1, false))
    private var aPosition = 0
    private var uPose = 0
    private var uTextureMatrix = 0
    private var uEyeRect = 0
    private var uProjection = 0
    private var projection = Optics.projection(88, 50)
    private var uVideoSize = 0
    private var uHasFrame = 0
    private var uLongitudeSpan = 0
    private var uSourceProjection = 0
    private var uCubePolesFlipped = 0
    private var uPhoneMode = 0
    private var uPhoneWarp = 0
    private var uPhoneEllipse = 0
    private var phoneWarp = Optics.phoneWarp(16, 9, 90, 100)
    private var phoneViewport = Optics.PhoneViewport(0, 0, false)

    fun resetFrame() { clearFrame.set(true); requestFrame() }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        CrashLog.phase = AppText.CREATING_GL_VIDEO_TEXTURES_AND_SHADERS.text()
        // On unplanned EGL loss the decoder may still reference the previous Surface.
        // Queue detachment on its application thread before releasing the Java handles.
        output?.let(retireOutput)
        output = null
        program = 0
        hasFrame = false
        frameAvailable.set(false)
        Matrix.setIdentityM(textureMatrix, 0)
        try {
            val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty()
            check(extensions.contains("GL_OES_EGL_image_external")) { AppText.THE_GPU_DOES_NOT_SUPPORT_EXTERNAL.text() }
            program = makeProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            aPosition = GLES20.glGetAttribLocation(program, "aPosition")
            uPose = GLES20.glGetUniformLocation(program, "uPose")
            uTextureMatrix = GLES20.glGetUniformLocation(program, "uTextureMatrix")
            uEyeRect = GLES20.glGetUniformLocation(program, "uEyeRect")
            uProjection = GLES20.glGetUniformLocation(program, "uProjection")
            uVideoSize = GLES20.glGetUniformLocation(program, "uVideoSize")
            uHasFrame = GLES20.glGetUniformLocation(program, "uHasFrame")
            uLongitudeSpan = GLES20.glGetUniformLocation(program, "uLongitudeSpan")
            uSourceProjection = GLES20.glGetUniformLocation(program, "uSourceProjection")
            uCubePolesFlipped = GLES20.glGetUniformLocation(program, "uCubePolesFlipped")
            uPhoneMode = GLES20.glGetUniformLocation(program, "uPhoneMode")
            uPhoneWarp = GLES20.glGetUniformLocation(program, "uPhoneWarp")
            uPhoneEllipse = GLES20.glGetUniformLocation(program, "uPhoneEllipse")
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val texture = SurfaceTexture(ids[0])
            val created = VideoOutput(texture, Surface(texture), ids[0])
            texture.setOnFrameAvailableListener {
                if (output === created) { frameAvailable.set(true); requestFrame() }
            }
            output = created
            GLES20.glUseProgram(program)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uVideo"), 0)
            outputReady(created)
        } catch (error: Exception) {
            reportError(AppText.VR_RENDERER_INITIALIZATION_FAILED.text(error.message))
            releaseOnGlThread()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    override fun onDrawFrame(gl: GL10?) {
        CrashLog.phase = AppText.DRAWING_VR_FRAME.text()
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val currentOutput = output ?: return
        if (program == 0 || width <= 0 || height <= 0) return
        if (clearFrame.getAndSet(false)) hasFrame = false
        if (frameAvailable.getAndSet(false)) {
            try {
                currentOutput.texture.updateTexImage()
                currentOutput.texture.getTransformMatrix(textureMatrix)
                hasFrame = true
            } catch (error: RuntimeException) {
                reportError(AppText.COULD_NOT_UPDATE_VIDEO_TEXTURE.text(error.message))
                // The application thread detaches the decoder before releasing this output.
                // Stop drawing until the user retries; do not repeatedly report each frame.
                GLES20.glDeleteProgram(program)
                program = 0
                return
            }
        }
        val currentSettings = settings
        val frameTime = System.nanoTime()
        displayedFovDegrees = fovTransition.update(
            (if (currentSettings.phoneMode) currentSettings.phoneFovDegrees else currentSettings.fovDegrees).toFloat(),
            frameTime, cachedSettings == null || cachedSettings?.phoneMode != currentSettings.phoneMode)
        if (cachedSettings != currentSettings || cachedWidth != width || cachedHeight != height) {
            opticalLayout = currentSettings.opticalLayout(width, height)
            phoneViewport = Optics.PhoneViewport(width, height, currentSettings.phoneFillScreen)
            cachedFov = Float.NaN
            eyeRects = arrayOf(
                Optics.eyeRect(currentSettings.layout, 0, currentSettings.swapEyes),
                Optics.eyeRect(currentSettings.layout, 1, currentSettings.swapEyes))
            cachedSettings = currentSettings
            cachedWidth = width
            cachedHeight = height
        }
        if (cachedFov != displayedFovDegrees) {
            if (currentSettings.phoneMode) phoneWarp = Optics.phoneWarp(phoneViewport.width, phoneViewport.height,
                displayedFovDegrees, currentSettings.phoneWideCorrection, currentSettings.phoneElliptical)
            else projection = Optics.projection(displayedFovDegrees, currentSettings.edgeCorrection)
            cachedFov = displayedFovDegrees
        }
        tracker.copyPose(pose)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, currentOutput.textureId)
        GLES20.glUniformMatrix3fv(uPose, 1, false, pose, 0)
        GLES20.glUniformMatrix4fv(uTextureMatrix, 1, false, textureMatrix, 0)
        GLES20.glUniform3fv(uProjection, 1, projection, 0)
        GLES20.glUniform2f(uVideoSize, videoWidth.coerceAtLeast(1).toFloat(), videoHeight.coerceAtLeast(1).toFloat())
        GLES20.glUniform1f(uHasFrame, if (hasFrame && !debugMode) 1f else 0f)
        GLES20.glUniform1f(uLongitudeSpan, Math.toRadians(currentSettings.projectionDegrees.toDouble()).toFloat())
        GLES20.glUniform1f(uCubePolesFlipped, if (currentSettings.cubePolesFlipped) 1f else 0f)
        GLES20.glUniform1i(uSourceProjection, when (currentSettings.sourceProjection) {
            VideoProjection.FISHEYE_180, VideoProjection.STEREO_FISHEYE_180 -> 1
            VideoProjection.CUBEMAP -> 2
            VideoProjection.EAC -> 3
            else -> 0
        })
        GLES20.glUniform1f(uPhoneMode, if (currentSettings.phoneMode) 1f else 0f)
        GLES20.glUniform3fv(uPhoneWarp, 1, phoneWarp, 0)
        GLES20.glUniform1f(uPhoneEllipse, phoneWarp[3])
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quad)
        for (eye in 0 until (if (currentSettings.phoneMode) 1 else 2)) {
            if (currentSettings.phoneMode) GLES20.glViewport(phoneViewport.x, phoneViewport.y, phoneViewport.width, phoneViewport.height)
            else GLES20.glViewport(if (eye == 0) opticalLayout.leftX else opticalLayout.rightX,
                opticalLayout.y, opticalLayout.eyeWidth, opticalLayout.eyeHeight)
            GLES20.glUniform4fv(uEyeRect, 1, eyeRects[eye], 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glDisableVertexAttribArray(aPosition)
        if (fovTransition.isRunning(frameTime)) requestFrame()
    }

    /** Caller must first detach the player from this Surface on the application thread. */
    fun releaseOnGlThread() {
        output?.let {
            it.release()
            GLES20.glDeleteTextures(1, intArrayOf(it.textureId), 0)
        }
        output = null
        if (program != 0) GLES20.glDeleteProgram(program)
        program = 0
        hasFrame = false
        frameAvailable.set(false)
    }

    private fun makeProgram(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val message = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                error(message)
            }
            return shader
        }
        val vertexId = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fragmentId = try { compile(GLES20.GL_FRAGMENT_SHADER, fragment) }
            catch (error: Exception) { GLES20.glDeleteShader(vertexId); throw error }
        val linked = GLES20.glCreateProgram()
        GLES20.glAttachShader(linked, vertexId)
        GLES20.glAttachShader(linked, fragmentId)
        GLES20.glLinkProgram(linked)
        GLES20.glDeleteShader(vertexId)
        GLES20.glDeleteShader(fragmentId)
        val status = IntArray(1)
        GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val message = GLES20.glGetProgramInfoLog(linked)
            GLES20.glDeleteProgram(linked)
            error(message)
        }
        return linked
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            varying vec2 vPosition;
            void main() {
                vPosition = aPosition;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform samplerExternalOES uVideo;
            uniform mat3 uPose;
            uniform mat4 uTextureMatrix;
            uniform vec4 uEyeRect;
            uniform vec3 uProjection; // tan(half FOV), half FOV radians, edge correction
            uniform vec2 uVideoSize;
            uniform float uHasFrame;
            uniform float uLongitudeSpan;
            uniform int uSourceProjection;
            uniform float uCubePolesFlipped;
            uniform float uPhoneMode;
            uniform vec3 uPhoneWarp; // rectangle plane extents, dynamic Panini d (hard compression)
            uniform float uPhoneEllipse; // relaxed superellipse boundary, no combined projection
            varying vec2 vPosition;
            const float PI = 3.141592653589793;
            // Source atlas coordinates are calculated top-down, then converted to texture UV.
            // Cubemap: R L U / D F B. EAC: L F R / D B U, with rotated lower faces.
            vec2 cubeUv(vec3 d) {
                vec3 a = abs(d);
                vec2 p;
                vec2 tile;
                bool eac = uSourceProjection == 3;
                if (a.x >= a.y && a.x >= a.z) {
                    p = vec2(d.x > 0.0 ? d.z : -d.z, -d.y) / a.x;
                    tile = eac ? vec2(d.x > 0.0 ? 2.0 : 0.0, 0.0)
                               : vec2(d.x > 0.0 ? 0.0 : 1.0, 0.0);
                } else if (a.y >= a.z) {
                    p = vec2(d.x, d.y > 0.0 ? -d.z : d.z) / a.y;
                    tile = d.y > 0.0 ? vec2(2.0, 0.0) : vec2(0.0, 1.0);
                    if (eac) {
                        p = vec2(p.y, -p.x);
                        tile = vec2(d.y > 0.0 ? 2.0 : 0.0, 1.0);
                    } else if (uCubePolesFlipped > 0.5) p = -p;
                } else {
                    p = vec2(d.z < 0.0 ? d.x : -d.x, -d.y) / a.z;
                    tile = vec2(d.z < 0.0 ? 1.0 : 2.0, 1.0);
                    if (eac) {
                        if (d.z > 0.0) p = vec2(-p.y, p.x);
                        tile = vec2(1.0, d.z < 0.0 ? 0.0 : 1.0);
                    }
                }
                vec2 local = eac ? atan(p) * (2.0 / PI) + 0.5 : p * 0.5 + 0.5;
                vec2 size = max(uVideoSize * uEyeRect.zw, vec2(1.0));
                vec2 origin;
                vec2 extent;
                if (eac) {
                    vec2 pad = min(vec2(2.0) / size, vec2(0.05));
                    extent = vec2((1.0 - 2.0 * pad.x) / 3.0, 0.5 - 2.0 * pad.y);
                    origin = vec2(pad.x + tile.x * extent.x, pad.y + tile.y * 0.5);
                } else {
                    extent = vec2(1.0 / 3.0, 0.5);
                    origin = tile * extent;
                }
                // Keep bilinear taps inside each face instead of leaking into unrelated tiles.
                vec2 inset = min(vec2(0.5) / size, extent * 0.25);
                vec2 uv = clamp(origin + local * extent, origin + inset, origin + extent - inset);
                return vec2(uv.x, 1.0 - uv.y);
            }
            void main() {
                float radius = length(vPosition);
                vec2 squared = vPosition * vPosition;
                float phoneBoundary = dot(squared, squared);
                vec3 ray;
                if (uPhoneMode > 0.5) {
                    if (uPhoneEllipse > 0.5 && phoneBoundary > 1.0) {
                        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0); return;
                    }
                    vec2 p = vPosition * uPhoneWarp.xy;
                    float d = uPhoneWarp.z;
                    float q = p.x / (d + 1.0);
                    float q2 = q * q;
                    float cosYaw = (sqrt(max(0.0, 1.0 + q2 * (1.0 - d * d))) - d * q2) / (1.0 + q2);
                    float sinYaw = q * (d + cosYaw);
                    // Hard vertical compression avoids the old radial warp's top/bottom bulge.
                    ray = normalize(vec3(sinYaw, p.y * cosYaw, -cosYaw));
                } else {
                    if (radius > 1.0) { gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0); return; }
                    float theta = mix(atan(radius * uProjection.x), radius * uProjection.y, uProjection.z);
                    ray = vec3(vPosition * (sin(theta) / max(radius, 0.000001)), -cos(theta));
                }
                vec3 direction = normalize(uPose * ray);
                vec2 uv;
                if (uSourceProjection == 1) {
                    if (direction.z > 0.0) { gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0); return; }
                    // Each eye's equidistant circle covers 180 degrees; SBS/TB is split by uEyeRect.
                    float angle = acos(clamp(-direction.z, 0.0, 1.0));
                    vec2 size = max(uVideoSize * uEyeRect.zw, vec2(1.0));
                    uv = vec2(0.5) + direction.xy / max(length(direction.xy), 0.000001)
                        * (angle / PI) * min(size.x, size.y) / size;
                } else if (uSourceProjection >= 2) {
                    uv = cubeUv(direction);
                } else {
                    float yaw = atan(direction.x, -direction.z);
                    if (abs(yaw) > uLongitudeSpan * 0.5) { gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0); return; }
                    uv = vec2(yaw / uLongitudeSpan + 0.5, asin(clamp(direction.y, -1.0, 1.0)) / PI + 0.5);
                }
                vec3 color;
                if (uHasFrame < 0.5) {
                    // A quiet alignment grid until the first video frame arrives.
                    vec2 grid = abs(fract(uv * 18.0 + 0.5) - 0.5);
                    float line = 1.0 - smoothstep(0.015, 0.04, min(grid.x, grid.y));
                    color = mix(vec3(0.025, 0.055, 0.065), vec3(0.12, 0.29, 0.31), line);
                    if (abs(uv.x - 0.5) < 0.0015 || abs(uv.y - 0.5) < 0.0015) color = vec3(0.47, 0.89, 0.79);
                } else {
                    vec2 halfTexel = 0.5 / uVideoSize;
                    vec2 eyeUv = clamp(uEyeRect.xy + uv * uEyeRect.zw,
                        uEyeRect.xy + halfTexel, uEyeRect.xy + uEyeRect.zw - halfTexel);
                    vec2 sampleUv = (uTextureMatrix * vec4(eyeUv, 0.0, 1.0)).xy;
                    color = texture2D(uVideo, sampleUv).rgb;
                }
                float mask = uPhoneMode > 0.5
                    ? (uPhoneEllipse > 0.5 ? 1.0 - smoothstep(0.984, 1.0, phoneBoundary) : 1.0)
                    : 1.0 - smoothstep(0.996, 1.0, radius);
                gl_FragColor = vec4(color * mask, 1.0);
            }
        """
    }
}
