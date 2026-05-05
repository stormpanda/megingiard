package com.stormpanda.megingiard.gyro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.macropad.GamepadInjector
import com.stormpanda.megingiard.macropad.GamepadKeycodes
import com.stormpanda.megingiard.settings.GyroSettings
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "GyroProcessor"

/**
 * Scale factor from rad/s to int16 gamepad stick units.
 *
 * At sensitivity=1.0, 1 rad/s → 3 000 stick units (≈9 % of full −32768…32767 travel).
 */
private const val GYRO_GAMEPAD_SCALE = 3000f

/**
 * Scale factor from rad/s to relative mouse-pixel delta.
 *
 * At sensitivity=1.0, 1 rad/s → 2 px per sensor update (~200 Hz = ~400 px/s).
 */
private const val GYRO_MOUSE_SCALE = 2f

/**
 * Processes gyroscope sensor events and dispatches them to the configured virtual
 * input device (gamepad left/right stick, or mouse).
 *
 * ### Lifecycle
 * Call [start] once when MacroPad injectors are activated; call [stop] when they
 * are deactivated.  [start] is a no-op when the gyro feature is disabled in
 * [GyroSettings] or when the sensor is unavailable on the device.
 *
 * ### Coordinate mapping
 * The gyroscope reports angular velocity in rad/s on three axes (device body frame):
 * - `values[0]` — X axis: positive = device top tilts away from user (pitch)
 * - `values[1]` — Y axis: positive = device left edge moves down (roll)
 * - `values[2]` — Z axis: positive = device rotates counter-clockwise (yaw, mostly unused)
 *
 * For aiming: Y-axis rotation → horizontal input; X-axis rotation → vertical input
 * (with Y negated so "tilt right" = "aim right").
 */
object GyroProcessor : SensorEventListener {

    private var sensorManager: SensorManager? = null

    // Handler is created lazily on first use and reused; the main looper lives for
    // the process lifetime so no explicit cleanup is needed.
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    val isRunning: Boolean get() = sensorManager != null

    /**
     * Starts the gyroscope listener.
     *
     * Ensures the required injector (gamepad or mouse) is running — starting it if
     * necessary regardless of the active MacroPad profile's device flags, because
     * gyro aiming is a global feature independent of per-layout button configuration.
     *
     * Safe to call if already running (no-op).
     */
    fun start(context: Context) {
        if (!GyroSettings.enabled.value) {
            AppLog.d(TAG, "start() skipped — gyro disabled in settings")
            return
        }
        if (GyroSettings.gyroOutput.value == GyroOutput.OFF) {
            AppLog.d(TAG, "start() skipped — gyro output is OFF")
            return
        }
        if (isRunning) {
            AppLog.d(TAG, "start() — already running")
            return
        }

        val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE)
            as? SensorManager
        if (sm == null) {
            AppLog.e(TAG, "start() — SensorManager unavailable")
            return
        }
        val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyro == null) {
            AppLog.w(TAG, "start() — TYPE_GYROSCOPE sensor not found on this device")
            return
        }

        // Ensure the required injector is running before the first event arrives.
        ensureInjector(context)

        val registered = sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME, mainHandler)
        if (!registered) {
            AppLog.e(TAG, "start() — registerListener failed")
            return
        }
        sensorManager = sm
        AppLog.i(TAG, "start() — gyro listener registered, output=${GyroSettings.gyroOutput.value}")
    }

    /** Unregisters the gyroscope listener. Safe to call when not running (no-op). */
    fun stop() {
        val sm = sensorManager ?: return
        sm.unregisterListener(this)
        sensorManager = null
        AppLog.i(TAG, "stop() — gyro listener unregistered")
    }

    // ── SensorEventListener ──────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        val output      = GyroSettings.gyroOutput.value
        val sensitivity = GyroSettings.sensitivity.value
        val deadZone    = GyroSettings.deadZone.value

        // Raw angular velocity (rad/s): X = pitch, Y = roll (used as horizontal aim)
        val rawX = event.values[0]  // vertical axis: positive = aim down
        val rawY = event.values[1]  // horizontal axis: positive = aim right (negated below)

        // Apply dead zone independently per axis
        val filteredX = applyDeadZone(rawX, deadZone)
        val filteredY = applyDeadZone(rawY, deadZone)

        // Scale and negate Y so tilting right → aim right
        val scaledX =  filteredX * sensitivity
        val scaledY = -filteredY * sensitivity

        when (output) {
            GyroOutput.OFF -> return

            GyroOutput.GAMEPAD_LEFT_STICK -> {
                if (!GamepadInjector.isRunning) return
                GamepadInjector.joystick(GamepadKeycodes.ABS_X, toStickValue(scaledY))
                GamepadInjector.joystick(GamepadKeycodes.ABS_Y, toStickValue(scaledX))
            }

            GyroOutput.GAMEPAD_RIGHT_STICK -> {
                if (!GamepadInjector.isRunning) return
                GamepadInjector.joystick(GamepadKeycodes.ABS_Z,  toStickValue(scaledY))
                GamepadInjector.joystick(GamepadKeycodes.ABS_RZ, toStickValue(scaledX))
            }

            GyroOutput.MOUSE -> {
                if (!MouseInjector.isRunning) return
                val dx = (scaledY * GYRO_MOUSE_SCALE).roundToInt()
                val dy = (scaledX * GYRO_MOUSE_SCALE).roundToInt()
                if (dx != 0 || dy != 0) MouseInjector.moveMouse(dx, dy)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        AppLog.d(TAG, "onAccuracyChanged sensor=${sensor.name} accuracy=$accuracy")
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun ensureInjector(context: Context) {
        when (GyroSettings.gyroOutput.value) {
            GyroOutput.GAMEPAD_LEFT_STICK,
            GyroOutput.GAMEPAD_RIGHT_STICK -> {
                if (!GamepadInjector.isRunning) {
                    AppLog.i(TAG, "ensureInjector — starting GamepadInjector for gyro")
                    GamepadInjector.start(context)
                }
            }
            GyroOutput.MOUSE -> {
                if (!MouseInjector.isRunning) {
                    AppLog.i(TAG, "ensureInjector — starting MouseInjector for gyro")
                    MouseInjector.start(context)
                }
            }
            GyroOutput.OFF -> Unit
        }
    }

    /**
     * Returns [value] unchanged when its absolute value exceeds [threshold]; returns 0
     * otherwise (dead zone).
     */
    private fun applyDeadZone(value: Float, threshold: Float): Float =
        if (abs(value) < threshold) 0f else value

    /**
     * Converts a scaled gyro axis value (rad/s × sensitivity) to a clamped int16
     * gamepad stick unit.
     */
    private fun toStickValue(scaled: Float): Int =
        (scaled * GYRO_GAMEPAD_SCALE).coerceIn(-32768f, 32767f).toInt()
}
