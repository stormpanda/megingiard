package com.stormpanda.megingiard.macropad

import android.os.VibrationEffect
import android.os.Vibrator

private const val TAG = "HapticFeedback"

// ─── Vibration parameters ────────────────────────────────────────────────────
// Duration / amplitude pairs tuned to feel like a very brief, subtle "tick".
// LIGHT  = smallest detectable pulse on most actuators.
// MEDIUM = slightly more present without being intrusive.
// STRONG = most prominent — still a short click, not a buzz.

private const val HF_LIGHT_DURATION_MS  = 15L
private const val HF_MEDIUM_DURATION_MS = 15L
private const val HF_STRONG_DURATION_MS = 15L

private const val HF_LIGHT_AMPLITUDE  = 64   // 25 % of 255
private const val HF_MEDIUM_AMPLITUDE = 128  // 50 % of 255
private const val HF_STRONG_AMPLITUDE = 255  // 100 % of 255

/** Minimum custom amplitude clamped at call-site to prevent silent zero. */
private const val HF_CUSTOM_AMPLITUDE_MIN = 5
/** Maximum custom amplitude on the user-facing scale (maps to Android's 255). */
private const val HF_CUSTOM_AMPLITUDE_MAX = 100
/** Android's maximum amplitude value. User-facing 100 maps to this. */
private const val HF_CUSTOM_AMPLITUDE_ANDROID_MAX = 255
/** Minimum custom duration clamped to avoid zero-length vibrations. */
private const val HF_CUSTOM_DURATION_MIN_MS = 1L
/** Maximum custom duration clamped to the user-facing ceiling. */
private const val HF_CUSTOM_DURATION_MAX_MS = 200L

/**
 * Triggers a short haptic tick on [vibrator] for the given [strength].
 * [HapticStrength.OFF] is a no-op.
 *
 * [customDurationMs] and [customAmplitude] are only used when
 * [strength] == [HapticStrength.CUSTOM]; ignored for all other strength levels.
 *
 * Caller is responsible for rate-limiting high-frequency calls
 * (e.g. TrackpointMove events).
 */
fun triggerHaptic(
    vibrator: Vibrator,
    strength: HapticStrength,
    customDurationMs: Int = 0,
    customAmplitude: Int = 0,
) {
    val (durationMs, amplitude) = when (strength) {
        HapticStrength.OFF    -> return
        HapticStrength.LIGHT  -> HF_LIGHT_DURATION_MS  to HF_LIGHT_AMPLITUDE
        HapticStrength.MEDIUM -> HF_MEDIUM_DURATION_MS to HF_MEDIUM_AMPLITUDE
        HapticStrength.STRONG -> HF_STRONG_DURATION_MS to HF_STRONG_AMPLITUDE
        HapticStrength.CUSTOM -> {
            val dur = customDurationMs.toLong().coerceIn(HF_CUSTOM_DURATION_MIN_MS, HF_CUSTOM_DURATION_MAX_MS)
            val userAmp = customAmplitude.coerceIn(HF_CUSTOM_AMPLITUDE_MIN, HF_CUSTOM_AMPLITUDE_MAX)
            // Map user-facing 5–100 linearly to Android's 1–255
            val amp = (userAmp * HF_CUSTOM_AMPLITUDE_ANDROID_MAX / HF_CUSTOM_AMPLITUDE_MAX)
                .coerceIn(1, HF_CUSTOM_AMPLITUDE_ANDROID_MAX)
            dur to amp
        }
    }
    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
}
