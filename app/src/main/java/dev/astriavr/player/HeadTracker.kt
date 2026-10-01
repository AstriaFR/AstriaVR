package dev.astriavr.player

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface

/** Rotation-vector fusion plus controller and touch offsets. */
class HeadTracker(context: Context, private val displayRotation: () -> Int) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val raw = FloatArray(9)
    private val screen = FloatArray(9)
    private val orientation = ViewOrientation()
    private val lock = Any()
    private var lastRotation = -1
    private var active = false
    var onPoseChanged: () -> Unit = {}
    val gyroEnabled: Boolean get() = synchronized(lock) { orientation.isGyroEnabled }
    var sensorName: String = AppText.NOT_STARTED.text()
        private set
    var lastEventMs: Long = 0L
        private set

    fun start() {
        if (active) return
        active = true
        restartSensor()
    }

    private fun restartSensor() {
        manager.unregisterListener(this)
        synchronized(lock) { orientation.newSensorSession() }
        lastEventMs = 0
        if (!active || !gyroEnabled) { sensorName = AppText.GYROSCOPE_OFF.text(); return }
        for (type in intArrayOf(Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR)) {
            val sensor = manager.getDefaultSensor(type) ?: continue
            if (manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)) {
                sensorName = if (type == Sensor.TYPE_GAME_ROTATION_VECTOR) AppText.GYROSCOPE_FUSION.text() else AppText.ROTATION_VECTOR.text()
                return
            }
        }
        sensorName = AppText.ORIENTATION_SENSOR_UNAVAILABLE.text()
    }

    fun stop() { active = false; manager.unregisterListener(this) }

    fun recenter() {
        synchronized(lock) { orientation.recenter() }
        onPoseChanged()
    }

    fun setGyroEnabled(enabled: Boolean) {
        if (enabled == gyroEnabled) return
        synchronized(lock) { orientation.setGyroEnabled(enabled) }
        restartSensor()
        onPoseChanged()
    }
    fun moveView(yawDegrees: Float, pitchDegrees: Float) {
        synchronized(lock) { orientation.move(yawDegrees, pitchDegrees) }
        onPoseChanged()
    }
    fun copyPose(destination: FloatArray) = synchronized(lock) { orientation.copyTo(destination) }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active || !gyroEnabled) return
        SensorManager.getRotationMatrixFromVector(raw, event.values)
        val rotation = displayRotation()
        val axes = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(raw, axes.first, axes.second, screen)
        synchronized(lock) {
            if (rotation != lastRotation) orientation.newSensorSession()
            orientation.onSensor(screen)
        }
        lastRotation = rotation
        lastEventMs = SystemClock.elapsedRealtime()
        onPoseChanged()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
