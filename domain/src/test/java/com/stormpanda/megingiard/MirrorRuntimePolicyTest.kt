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

    @Test
    fun `starts when active layout wants mirror and no capture is running`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = false,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.START, decision)
    }

    @Test
    fun `stops when active layout does not want mirror while capture is running`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = true,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = false,
                ),
            )

        assertEquals(MirrorRuntimeAction.STOP, decision)
    }

    @Test
    fun `does nothing when active layout wants mirror while capture is already running`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = true,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `does nothing when active layout does not want mirror and capture is stopped`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = false,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = false,
                ),
            )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `does not start while prompt is already in flight`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = true,
                    isOnValidScreen = true,
                    isCapturing = false,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `does nothing when not on valid screen even if layout wants mirror`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = false,
                    isCapturing = false,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `does nothing when no layout is active even if capture is running`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = true,
                    layoutId = null,
                    layoutWantsMirror = false,
                ),
            )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `does not start while privd mirror daemon is connecting`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = false,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = true,
                    privdMirrorConnecting = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `starts when privd mirror daemon is settled (not connecting)`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = false,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = true,
                    privdMirrorConnecting = false,
                ),
            )

        assertEquals(MirrorRuntimeAction.START, decision)
    }

    @Test
    fun `does not start when onboarding tutorials are active`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = false,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = true,
                    tutorialsActive = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `isPrivdMirrorConnecting blocks during race window and allows once dismissed`() {
        // 1. Race window: daemon failed, prompt is not yet active (async delay), but user has creds
        assertTrue(
            isPrivdMirrorConnecting(
                privdState = PrivdState.FAILED,
                promptActive = false,
                hasCreds = true,
                dismissed = false,
                isManuallyDisconnected = false,
            ),
        )

        // 2. User clicked Done/Skip (dismissed = true) -> should not block anymore
        assertFalse(
            isPrivdMirrorConnecting(
                privdState = PrivdState.FAILED,
                promptActive = false,
                hasCreds = true,
                dismissed = true,
                isManuallyDisconnected = false,
            ),
        )

        // 3. Normal active connecting states should block
        assertTrue(
            isPrivdMirrorConnecting(
                privdState = PrivdState.CONNECTING,
                promptActive = false,
                hasCreds = true,
                dismissed = false,
                isManuallyDisconnected = false,
            ),
        )
        assertTrue(
            isPrivdMirrorConnecting(
                privdState = PrivdState.BOOTSTRAPPING,
                promptActive = false,
                hasCreds = true,
                dismissed = false,
                isManuallyDisconnected = false,
            ),
        )

        // 4. Active connection pending on OFF state should block even if hasCreds is false
        assertTrue(
            isPrivdMirrorConnecting(
                privdState = PrivdState.OFF,
                promptActive = false,
                hasCreds = true,
                dismissed = false,
                isManuallyDisconnected = false,
            ),
        )
        assertTrue(
            isPrivdMirrorConnecting(
                privdState = PrivdState.OFF,
                promptActive = false,
                hasCreds = false,
                dismissed = false,
                isManuallyDisconnected = false,
            ),
        )

        // 5. OFF state after manual disconnect should not block
        assertFalse(
            isPrivdMirrorConnecting(
                privdState = PrivdState.OFF,
                promptActive = false,
                hasCreds = true,
                dismissed = false,
                isManuallyDisconnected = true,
            ),
        )
        assertFalse(
            isPrivdMirrorConnecting(
                privdState = PrivdState.OFF,
                promptActive = false,
                hasCreds = false,
                dismissed = false,
                isManuallyDisconnected = true,
            ),
        )
    }

    @Test
    fun `stops mirroring when showIntegrationHome is true while capturing without fullscreen overlays`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = true,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = true,
                    showIntegrationHome = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.STOP, decision)
    }

    @Test
    fun `does not stop mirroring when showIntegrationHome is true if isFullscreenMouseActive is true`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = true,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = true,
                    showIntegrationHome = true,
                    isFullscreenMouseActive = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `does not stop mirroring when showIntegrationHome is true if wasMirroringStartedByTouchpad is true`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = true,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = false,
                    showIntegrationHome = true,
                    wasMirroringStartedByTouchpad = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `starts mirroring on showIntegrationHome when touchpad overlay requests mirroring`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = false,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = false,
                    showIntegrationHome = true,
                    isFullscreenMouseActive = true,
                    wasMirroringStartedByTouchpad = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.START, decision)
    }
}
