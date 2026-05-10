package com.stormpanda.megingiard

import com.stormpanda.megingiard.mirror.MirrorRuntimeAction
import com.stormpanda.megingiard.mirror.MirrorRuntimePolicyState
import com.stormpanda.megingiard.mirror.decideMirrorRuntimeAction
import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorRuntimePolicyTest {
    private companion object {
        const val LAYOUT_A = "layout-a"
        const val LAYOUT_B = "layout-b"
    }

    @Test
    fun `starts when active layout wants mirror and no capture is running`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = false,
                globalAutoStart = true,
                layoutId = LAYOUT_A,
                layoutWantsMirror = true,
                confirmedMirrorLayoutId = null,
            )
        )

        assertEquals(MirrorRuntimeAction.START, decision.action)
        assertEquals(null, decision.confirmedMirrorLayoutId)
    }

    @Test
    fun `does not stop fresh capture on stale false layout snapshot`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = true,
                globalAutoStart = true,
                layoutId = LAYOUT_A,
                layoutWantsMirror = false,
                confirmedMirrorLayoutId = null,
            )
        )

        assertEquals(MirrorRuntimeAction.NONE, decision.action)
        assertEquals(null, decision.confirmedMirrorLayoutId)
    }

    @Test
    fun `latches confirmed mirror-on once capture and layout agree`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = true,
                globalAutoStart = true,
                layoutId = LAYOUT_A,
                layoutWantsMirror = true,
                confirmedMirrorLayoutId = null,
            )
        )

        assertEquals(MirrorRuntimeAction.NONE, decision.action)
        assertEquals(LAYOUT_A, decision.confirmedMirrorLayoutId)
    }

    @Test
    fun `stops after confirmed running session switches same layout to off`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = true,
                globalAutoStart = true,
                layoutId = LAYOUT_A,
                layoutWantsMirror = false,
                confirmedMirrorLayoutId = LAYOUT_A,
            )
        )

        assertEquals(MirrorRuntimeAction.STOP, decision.action)
        assertEquals(null, decision.confirmedMirrorLayoutId)
    }

    @Test
    fun `stops after confirmed running session switches to different off layout`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = true,
                globalAutoStart = true,
                layoutId = LAYOUT_B,
                layoutWantsMirror = false,
                confirmedMirrorLayoutId = LAYOUT_A,
            )
        )

        assertEquals(MirrorRuntimeAction.STOP, decision.action)
        assertEquals(null, decision.confirmedMirrorLayoutId)
    }

    @Test
    fun `clears confirmation when capture stops`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = false,
                globalAutoStart = true,
                layoutId = LAYOUT_A,
                layoutWantsMirror = false,
                confirmedMirrorLayoutId = LAYOUT_A,
            )
        )

        assertEquals(MirrorRuntimeAction.NONE, decision.action)
        assertEquals(null, decision.confirmedMirrorLayoutId)
    }
}
