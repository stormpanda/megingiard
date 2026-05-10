package com.stormpanda.megingiard

import com.stormpanda.megingiard.mirror.MirrorRuntimeAction
import com.stormpanda.megingiard.mirror.MirrorRuntimePolicyState
import com.stormpanda.megingiard.mirror.decideMirrorRuntimeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorRuntimePolicyTest {

    @Test
    fun `starts when active layout wants mirror and no capture is running`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = false,
                globalAutoStart = true,
                layoutWantsMirror = true,
                confirmedCapturingWithMirrorOn = false,
            )
        )

        assertEquals(MirrorRuntimeAction.START, decision.action)
        assertFalse(decision.confirmedCapturingWithMirrorOn)
    }

    @Test
    fun `does not stop fresh capture on stale false layout snapshot`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = true,
                globalAutoStart = true,
                layoutWantsMirror = false,
                confirmedCapturingWithMirrorOn = false,
            )
        )

        assertEquals(MirrorRuntimeAction.NONE, decision.action)
        assertFalse(decision.confirmedCapturingWithMirrorOn)
    }

    @Test
    fun `latches confirmed mirror-on once capture and layout agree`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = true,
                globalAutoStart = true,
                layoutWantsMirror = true,
                confirmedCapturingWithMirrorOn = false,
            )
        )

        assertEquals(MirrorRuntimeAction.NONE, decision.action)
        assertTrue(decision.confirmedCapturingWithMirrorOn)
    }

    @Test
    fun `stops after confirmed running session switches to off layout`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = true,
                globalAutoStart = true,
                layoutWantsMirror = false,
                confirmedCapturingWithMirrorOn = true,
            )
        )

        assertEquals(MirrorRuntimeAction.STOP, decision.action)
        assertFalse(decision.confirmedCapturingWithMirrorOn)
    }

    @Test
    fun `clears confirmation when capture stops`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = false,
                globalAutoStart = true,
                layoutWantsMirror = false,
                confirmedCapturingWithMirrorOn = true,
            )
        )

        assertEquals(MirrorRuntimeAction.NONE, decision.action)
        assertFalse(decision.confirmedCapturingWithMirrorOn)
    }
}
