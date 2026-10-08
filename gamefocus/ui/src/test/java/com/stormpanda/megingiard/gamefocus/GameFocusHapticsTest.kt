package com.stormpanda.megingiard.gamefocus

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.megingiard.AppLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator

private const val TAG = "GameFocusHapticsTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameFocusHapticsTest {
    private lateinit var context: Context
    private lateinit var vibrator: Vibrator
    private lateinit var shadowVibrator: ShadowVibrator

    @Before
    fun setUp() {
        AppLog.d(TAG, "Setting up GameFocusHapticsTest")
        context = ApplicationProvider.getApplicationContext()
        val vibratorManager = context.getSystemService(VibratorManager::class.java)
        vibrator = vibratorManager?.defaultVibrator ?: context.getSystemService(Vibrator::class.java)!!
        shadowVibrator = shadowOf(vibrator)
        shadowVibrator.setHasVibrator(true)
        ShadowVibrator.reset()
        GameFocusHaptics.resetForTesting()
    }

    @After
    fun tearDown() {
        ShadowVibrator.reset()
        GameFocusHaptics.resetForTesting()
    }

    @Test
    fun testTickWithPrimitivesSupported() {
        shadowVibrator.setSupportedPrimitives(listOf(VibrationEffect.Composition.PRIMITIVE_CLICK))
        GameFocusHaptics.tick(context)

        assertTrue(shadowVibrator.isVibrating)
        val primitives = shadowVibrator.primitiveSegmentsInPrimitiveEffects ?: emptyList()
        assertEquals(1, primitives.size)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK, primitives[0].id)
        assertEquals(0.45f, primitives[0].scale, 0.01f)

        val attrs = shadowVibrator.vibrationAttributesFromLastVibration as? VibrationAttributes
        assertNotNull(attrs)
        assertEquals(VibrationAttributes.USAGE_TOUCH, attrs?.usage)
    }

    @Test
    fun testClickWithPrimitivesSupported() {
        shadowVibrator.setSupportedPrimitives(listOf(VibrationEffect.Composition.PRIMITIVE_CLICK))
        GameFocusHaptics.click(context)

        assertTrue(shadowVibrator.isVibrating)
        val primitives = shadowVibrator.primitiveSegmentsInPrimitiveEffects ?: emptyList()
        assertEquals(1, primitives.size)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK, primitives[0].id)
        assertEquals(1.0f, primitives[0].scale, 0.01f)

        val attrs = shadowVibrator.vibrationAttributesFromLastVibration as? VibrationAttributes
        assertNotNull(attrs)
        assertEquals(VibrationAttributes.USAGE_TOUCH, attrs?.usage)
    }

    @Test
    fun testTickFallbackWhenPrimitivesUnsupported() {
        shadowVibrator.setSupportedPrimitives(emptyList())
        GameFocusHaptics.tick(context)

        assertTrue(shadowVibrator.isVibrating)
        assertTrue(shadowVibrator.primitiveSegmentsInPrimitiveEffects.isNullOrEmpty())
        assertEquals(8L, shadowVibrator.milliseconds)

        val attrs = shadowVibrator.vibrationAttributesFromLastVibration as? VibrationAttributes
        assertNotNull(attrs)
        assertEquals(VibrationAttributes.USAGE_TOUCH, attrs?.usage)
    }

    @Test
    fun testClickFallbackWhenPrimitivesUnsupported() {
        shadowVibrator.setSupportedPrimitives(emptyList())
        GameFocusHaptics.click(context)

        assertTrue(shadowVibrator.isVibrating)
        assertTrue(shadowVibrator.primitiveSegmentsInPrimitiveEffects.isNullOrEmpty())
        assertEquals(12L, shadowVibrator.milliseconds)

        val attrs = shadowVibrator.vibrationAttributesFromLastVibration as? VibrationAttributes
        assertNotNull(attrs)
        assertEquals(VibrationAttributes.USAGE_TOUCH, attrs?.usage)
    }

    @Test
    fun testNoVibratorDoesNotVibrate() {
        shadowVibrator.setHasVibrator(false)
        GameFocusHaptics.tick(context)
        assertFalse(shadowVibrator.isVibrating)

        GameFocusHaptics.click(context)
        assertFalse(shadowVibrator.isVibrating)
    }

    @Test
    fun testResetForTesting() {
        shadowVibrator.setSupportedPrimitives(listOf(VibrationEffect.Composition.PRIMITIVE_CLICK))
        GameFocusHaptics.tick(context)
        assertEquals(1, shadowVibrator.primitiveSegmentsInPrimitiveEffects?.size)

        GameFocusHaptics.resetForTesting()
        ShadowVibrator.reset()

        // Set to unsupported and verify re-initialization takes the fallback path
        shadowVibrator.setSupportedPrimitives(emptyList())
        GameFocusHaptics.click(context)
        assertTrue(shadowVibrator.primitiveSegmentsInPrimitiveEffects.isNullOrEmpty())
        assertEquals(12L, shadowVibrator.milliseconds)
    }
}
