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
                    autoStartSuppressed = false,
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
                    autoStartSuppressed = false,
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
                    autoStartSuppressed = false,
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
                    autoStartSuppressed = false,
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
                    autoStartSuppressed = false,
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
                    autoStartSuppressed = false,
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
                    autoStartSuppressed = false,
                ),
            )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `does not start when active layout auto-start is suppressed`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = false,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = true,
                    autoStartSuppressed = true,
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
                    autoStartSuppressed = false,
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
                    autoStartSuppressed = false,
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
                    autoStartSuppressed = false,
                    tutorialsActive = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }

    @Test
    fun `isPrivdMirrorConnecting blocks during race window and allows once dismissed`() {
        // 1. Race window: daemon failed, prompt is not yet active (async delay), but user wants it
        assertTrue(
            isPrivdMirrorConnecting(
                privdState = PrivdState.FAILED,
                promptActive = false,
                hasCreds = true,
                showPromptPref = true,
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
                showPromptPref = true,
                dismissed = true,
                isManuallyDisconnected = false,
            ),
        )

        // 3. User disabled the show prompt preference -> should not block on FAILED state
        assertFalse(
            isPrivdMirrorConnecting(
                privdState = PrivdState.FAILED,
                promptActive = false,
                hasCreds = true,
                showPromptPref = false,
                dismissed = false,
                isManuallyDisconnected = false,
            ),
        )

        // 4. Normal active connecting states should block
        assertTrue(
            isPrivdMirrorConnecting(
                privdState = PrivdState.CONNECTING,
                promptActive = false,
                hasCreds = true,
                showPromptPref = true,
                dismissed = false,
                isManuallyDisconnected = false,
            ),
        )
        assertTrue(
            isPrivdMirrorConnecting(
                privdState = PrivdState.BOOTSTRAPPING,
                promptActive = false,
                hasCreds = true,
                showPromptPref = true,
                dismissed = false,
                isManuallyDisconnected = false,
            ),
        )

        // 5. Active connection pending on OFF state should block
        assertTrue(
            isPrivdMirrorConnecting(
                privdState = PrivdState.OFF,
                promptActive = false,
                hasCreds = true,
                showPromptPref = true,
                dismissed = false,
                isManuallyDisconnected = false,
            ),
        )

        // 6. OFF state after manual disconnect should not block
        assertFalse(
            isPrivdMirrorConnecting(
                privdState = PrivdState.OFF,
                promptActive = false,
                hasCreds = true,
                showPromptPref = true,
                dismissed = false,
                isManuallyDisconnected = true,
            ),
        )
    }

    @Test
    fun `stops mirroring when showIntegrationHome is true while capturing`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = true,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = true,
                    autoStartSuppressed = false,
                    showIntegrationHome = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.STOP, decision)
    }

    @Test
    fun `does not start mirroring when showIntegrationHome is true even if layout wants mirror`() {
        val decision =
            decideMirrorRuntimeAction(
                MirrorRuntimePolicyState(
                    promptInFlight = false,
                    isOnValidScreen = true,
                    isCapturing = false,
                    layoutId = LAYOUT_A,
                    layoutWantsMirror = true,
                    autoStartSuppressed = false,
                    showIntegrationHome = true,
                ),
            )

        assertEquals(MirrorRuntimeAction.NONE, decision)
    }
}
