package com.stormpanda.megingiard

import com.stormpanda.megingiard.mirror.MirrorRuntimeAction
import com.stormpanda.megingiard.mirror.MirrorRuntimePolicyState
import com.stormpanda.megingiard.mirror.decideMirrorRuntimeAction
import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorRuntimePolicyTest {
    private companion object {
        const val LAYOUT_A = "layout-a"
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
            )
        )

        assertEquals(MirrorRuntimeAction.START, decision)
    }

    @Test
    fun `stops when active layout does not want mirror while capture is running`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = true,
                globalAutoStart = true,
                layoutId = LAYOUT_A,
                layoutWantsMirror = false,
            )
        )

        assertEquals(MirrorRuntimeAction.STOP, decision)
    }

    @Test
    fun `does nothing when active layout wants mirror while capture is already running`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = true,
                globalAutoStart = true,
                layoutId = LAYOUT_A,
                layoutWantsMirror = true,
            )
        )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `does nothing when active layout does not want mirror and capture is stopped`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = false,
                globalAutoStart = true,
                layoutId = LAYOUT_A,
                layoutWantsMirror = false,
            )
        )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `does not start when global auto-start is disabled`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = false,
                globalAutoStart = false,
                layoutId = LAYOUT_A,
                layoutWantsMirror = true,
            )
        )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `does not start while prompt is already in flight`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = true,
                isOnValidScreen = true,
                isCapturing = false,
                globalAutoStart = true,
                layoutId = LAYOUT_A,
                layoutWantsMirror = true,
            )
        )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `does nothing when not on valid screen even if layout wants mirror`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = false,
                isCapturing = false,
                globalAutoStart = true,
                layoutId = LAYOUT_A,
                layoutWantsMirror = true,
            )
        )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `does nothing when no layout is active even if capture is running`() {
        val decision = decideMirrorRuntimeAction(
            MirrorRuntimePolicyState(
                promptInFlight = false,
                isOnValidScreen = true,
                isCapturing = true,
                globalAutoStart = true,
                layoutId = null,
                layoutWantsMirror = false,
            )
        )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }
}
