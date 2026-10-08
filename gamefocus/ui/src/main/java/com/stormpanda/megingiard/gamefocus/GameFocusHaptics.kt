package com.stormpanda.megingiard.gamefocus

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.MainThread
import com.stormpanda.megingiard.AppLog

private const val TAG = "GameFocusHaptics"

// Pre-calibrated parameters for crisp, subtle mechanical impulses
// On modern handhelds (such as AYN Thor's Qualcomm LRA), PRIMITIVE_CLICK produces a crisp 6ms impulse.
// Scaling it to 0.45f creates a very light, subtle micro-tick for lateral and grid navigation,
// while 1.0f produces a firm, distinct mechanical click for boundary actions (categories, tabs, overlays).
private const val HAPTIC_TICK_SCALE = 0.45f
private const val HAPTIC_CLICK_SCALE = 1.0f

// Fallbacks when hardware composition primitives are unsupported
private const val FALLBACK_TICK_DURATION_MS = 8L
private const val FALLBACK_TICK_AMPLITUDE = 35 // ~14% power for a subtle micro-tick
private const val FALLBACK_CLICK_DURATION_MS = 12L
private const val FALLBACK_CLICK_AMPLITUDE = 75 // ~30% power for a distinct mechanical click

/**
 * Provides lean, zero-config tactile haptic feedback for gamepad navigation in Game Focus.
 *
 * Utilizes Android's hardware composition primitive ([VibrationEffect.Composition.PRIMITIVE_CLICK])
 * on modern actuators (e.g. AYN Thor LRA) with amplitude scaling to deliver crisp, mechanical tactile
 * clicks without buzzing. Note: PRIMITIVE_LOW_TICK and PRIMITIVE_TICK are intentionally avoided because
 * Qualcomm's HAL reports support but evaluates to a 0ms dummy wave on Snapdragon G3x Gen 2 hardware.
 * Provides graceful micro-pulse fallbacks when hardware primitives are unsupported.
 * Dispatches haptic effects with [VibrationAttributes.USAGE_TOUCH] to respect system touch-feedback settings.
 */
object GameFocusHaptics {
    private var cachedVibrator: Vibrator? = null
    private var supportsClick: Boolean = false
    private var isInitialized: Boolean = false

    private val touchVibrationAttributes: VibrationAttributes by lazy {
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
    }

    private val tickPrimitiveEffect: VibrationEffect by lazy {
        VibrationEffect
            .startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, HAPTIC_TICK_SCALE)
            .compose()
    }

    private val clickPrimitiveEffect: VibrationEffect by lazy {
        VibrationEffect
            .startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, HAPTIC_CLICK_SCALE)
            .compose()
    }

    private val fallbackTickEffect: VibrationEffect by lazy {
        VibrationEffect.createOneShot(FALLBACK_TICK_DURATION_MS, FALLBACK_TICK_AMPLITUDE)
    }

    private val fallbackClickEffect: VibrationEffect by lazy {
        VibrationEffect.createOneShot(FALLBACK_CLICK_DURATION_MS, FALLBACK_CLICK_AMPLITUDE)
    }

    private fun getVibrator(context: Context): Vibrator? {
        if (isInitialized) return cachedVibrator
        val appContext = context.applicationContext
        val vibratorManager = appContext.getSystemService(VibratorManager::class.java)
        val vibrator = vibratorManager?.defaultVibrator ?: appContext.getSystemService(Vibrator::class.java)
        if (vibrator != null && vibrator.hasVibrator()) {
            cachedVibrator = vibrator
            supportsClick = vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            AppLog.i(TAG, "Initialized GameFocusHaptics (supportsClick=$supportsClick)")
        } else {
            AppLog.w(TAG, "No functional hardware vibrator found on device")
        }
        isInitialized = true
        return cachedVibrator
    }

    /**
     * Triggers a subtle micro-tick for browsing individual items (gallery posters, library grid cards).
     */
    @MainThread
    fun tick(context: Context) {
        val vibrator = getVibrator(context) ?: return
        try {
            val effect = if (supportsClick) tickPrimitiveEffect else fallbackTickEffect
            vibrator.vibrate(effect, touchVibrationAttributes)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to perform haptic tick", e)
        }
    }

    /**
     * Triggers a firmer, distinct click for category rolls (Up/Down), library tab switches (L1/R1),
     * gallery letter jumps (L1/R1), and Library view toggling (R2).
     */
    @MainThread
    fun click(context: Context) {
        val vibrator = getVibrator(context) ?: return
        try {
            val effect = if (supportsClick) clickPrimitiveEffect else fallbackClickEffect
            vibrator.vibrate(effect, touchVibrationAttributes)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to perform haptic click", e)
        }
    }

    internal fun resetForTesting() {
        cachedVibrator = null
        supportsClick = false
        isInitialized = false
    }
}
