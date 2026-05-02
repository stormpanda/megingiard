package com.stormpanda.megingiard.macropad

import android.os.VibrationEffect
import android.os.Vibrator

private const val TAG = "HapticFeedback"

// ─── Vibration parameters ────────────────────────────────────────────────────
// Duration / amplitude pairs tuned to feel like a very brief, subtle "tick".
// LIGHT  = smallest detectable pulse on most actuators.
// MEDIUM = slightly more present without being intrusive.
// STRONG = most prominent — still a short click, not a buzz.

private const val HF_LIGHT_DURATION_MS  = 5L
private const val HF_MEDIUM_DURATION_MS = 7L
private const val HF_STRONG_DURATION_MS = 9L

private const val HF_LIGHT_AMPLITUDE  = 1
private const val HF_MEDIUM_AMPLITUDE = 10
private const val HF_STRONG_AMPLITUDE = 25

/**
 * Triggers a short haptic tick on [vibrator] for the given [strength].
 * [HapticStrength.OFF] is a no-op.
 *
 * Caller is responsible for rate-limiting high-frequency calls
 * (e.g. TrackpointMove events).
 */
fun triggerHaptic(vibrator: Vibrator, strength: HapticStrength) {
    val (durationMs, amplitude) = when (strength) {
        HapticStrength.OFF    -> return
        HapticStrength.LIGHT  -> HF_LIGHT_DURATION_MS  to HF_LIGHT_AMPLITUDE
        HapticStrength.MEDIUM -> HF_MEDIUM_DURATION_MS to HF_MEDIUM_AMPLITUDE
        HapticStrength.STRONG -> HF_STRONG_DURATION_MS to HF_STRONG_AMPLITUDE
    }
    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
}
