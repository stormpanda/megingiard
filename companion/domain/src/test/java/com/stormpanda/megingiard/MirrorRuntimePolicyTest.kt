package com.stormpanda.megingiard

import com.stormpanda.megingiard.mirror.MirrorRuntimeAction
import com.stormpanda.megingiard.mirror.MirrorRuntimePolicyState
import com.stormpanda.megingiard.mirror.decideMirrorRuntimeAction
import com.stormpanda.megingiard.mirror.isPrivdMirrorConnecting
import com.stormpanda.megingiard.privd.PrivdState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorRuntimePolicyTest {
    private companion object {
        const val LAYOUT_A = "layout-a"
    }

    private fun policyState(
        promptInFlight: Boolean = false,
        isOnValidScreen: Boolean = true,
        isCapturing: Boolean = false,
        layoutId: String? = LAYOUT_A,
        layoutWantsMirror: Boolean = true,
        privdMirrorConnecting: Boolean = false,
        tutorialsActive: Boolean = false,
    ) = MirrorRuntimePolicyState(
        promptInFlight = promptInFlight,
        isOnValidScreen = isOnValidScreen,
        isCapturing = isCapturing,
        layoutId = layoutId,
        layoutWantsMirror = layoutWantsMirror,
        privdMirrorConnecting = privdMirrorConnecting,
        tutorialsActive = tutorialsActive,
    )

    private fun checkConnecting(
        privdState: PrivdState,
        promptActive: Boolean = false,
        hasCreds: Boolean = true,
        dismissed: Boolean = false,
        isManuallyDisconnected: Boolean = false,
    ) = isPrivdMirrorConnecting(
        privdState = privdState,
        promptActive = promptActive,
        hasCreds = hasCreds,
        dismissed = dismissed,
        isManuallyDisconnected = isManuallyDisconnected,
    )

    @Test
    fun `starts when active layout wants mirror and no capture is running`() {
        assertEquals(MirrorRuntimeAction.START, decideMirrorRuntimeAction(policyState()))
    }

    @Test
    fun `stops when active layout does not want mirror while capture is running`() {
        assertEquals(MirrorRuntimeAction.STOP, decideMirrorRuntimeAction(policyState(isCapturing = true, layoutWantsMirror = false)))
    }

    @Test
    fun `does nothing when active layout wants mirror while capture is already running`() {
        assertEquals(MirrorRuntimeAction.NONE, decideMirrorRuntimeAction(policyState(isCapturing = true)))
    }

    @Test
    fun `does nothing when active layout does not want mirror and capture is stopped`() {
        assertEquals(MirrorRuntimeAction.NONE, decideMirrorRuntimeAction(policyState(layoutWantsMirror = false)))
    }

    @Test
    fun `does not start while prompt is already in flight`() {
        assertEquals(MirrorRuntimeAction.NONE, decideMirrorRuntimeAction(policyState(promptInFlight = true)))
    }

    @Test
    fun `does nothing when not on valid screen even if layout wants mirror`() {
        assertEquals(MirrorRuntimeAction.NONE, decideMirrorRuntimeAction(policyState(isOnValidScreen = false)))
    }

    @Test
    fun `does nothing when no layout is active even if capture is running`() {
        assertEquals(
            MirrorRuntimeAction.NONE,
            decideMirrorRuntimeAction(policyState(layoutId = null, isCapturing = true, layoutWantsMirror = false)),
        )
    }

    @Test
    fun `does not start while privd mirror daemon is connecting`() {
        assertEquals(MirrorRuntimeAction.NONE, decideMirrorRuntimeAction(policyState(privdMirrorConnecting = true)))
    }

    @Test
    fun `starts when privd mirror daemon is settled (not connecting)`() {
        assertEquals(MirrorRuntimeAction.START, decideMirrorRuntimeAction(policyState(privdMirrorConnecting = false)))
    }

    @Test
    fun `does not start when onboarding tutorials are active`() {
        assertEquals(MirrorRuntimeAction.NONE, decideMirrorRuntimeAction(policyState(tutorialsActive = true)))
    }

    @Test
    fun `isPrivdMirrorConnecting blocks during race window and allows once dismissed`() {
        assertTrue(checkConnecting(PrivdState.FAILED, hasCreds = true, dismissed = false))
        assertFalse(checkConnecting(PrivdState.FAILED, hasCreds = true, dismissed = true))

        assertTrue(checkConnecting(PrivdState.CONNECTING))
        assertTrue(checkConnecting(PrivdState.BOOTSTRAPPING))

        assertTrue(checkConnecting(PrivdState.OFF, hasCreds = true))
        assertTrue(checkConnecting(PrivdState.OFF, hasCreds = false))

        assertFalse(checkConnecting(PrivdState.OFF, hasCreds = true, isManuallyDisconnected = true))
        assertFalse(checkConnecting(PrivdState.OFF, hasCreds = false, isManuallyDisconnected = true))
    }
}
