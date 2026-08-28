package com.stormpanda.megingiard.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.EditorSection
import com.stormpanda.megingiard.mirror.MirrorEditorTopOverlay
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppDimens
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalAppDimens
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TAG = "PrimaryOverlayActivity"

/**
 * Translucent, edge-to-edge Activity hosted on the primary display (Display 0).
 *
 * Hosts deep configuration dialogs, widescreen master-detail settings, button/layout
 * inspectors, setup wizards, and tutorials on the top screen while allowing the secondary
 * display to remain an unobstructed, live interactive action deck.
 */
class PrimaryOverlayActivity : ComponentActivity() {
    private var wasFrozenForModal = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val displayId = runCatching { display?.displayId }.getOrNull()
        AppLog.i(TAG, "onCreate: started on display=$displayId")

        wasFrozenForModal = savedInstanceState?.getBoolean("wasFrozenForModal") ?: false
        val initialModal = AppStateManager.activePrimaryModal.value != null
        if (savedInstanceState == null && initialModal && ScreenCaptureManager.isCapturing.value && !ScreenCaptureManager.isFrozen.value) {
            AppLog.i(TAG, "Freezing mirror capture for primary modal activity")
            wasFrozenForModal = true
            ScreenCaptureManager.setFrozen(true)
        }

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        lifecycleScope.launch {
            combine(
                AppStateManager.activePrimaryModal,
                AppStateManager.isViewportEditActive,
            ) { modal, isEdit ->
                Pair(modal != null, isEdit)
            }.collect { (hasModal, isEdit) ->
                if (!hasModal && !isEdit) {
                    AppLog.i(TAG, "All primary overlays inactive -> finishing activity")
                    finish()
                    return@collect
                }

                if (hasModal && !isEdit) {
                    if (!wasFrozenForModal && ScreenCaptureManager.isCapturing.value && !ScreenCaptureManager.isFrozen.value) {
                        AppLog.i(TAG, "Freezing mirror capture for primary modal activity")
                        wasFrozenForModal = true
                        ScreenCaptureManager.setFrozen(true)
                    }
                } else {
                    if (wasFrozenForModal) {
                        AppLog.i(TAG, "Resuming live mirror capture for viewport editor activity")
                        if (ScreenCaptureManager.isCapturing.value) {
                            ScreenCaptureManager.setFrozen(false)
                        }
                        wasFrozenForModal = false
                    }
                }
            }
        }

        setContent {
            val themeMode by SettingsManager.themeMode.collectAsState()
            val userAccentArgb by SettingsManager.accentColor.collectAsState()
            val appColors = paletteFor(themeMode, Color(userAccentArgb))
            val activeModal by AppStateManager.activePrimaryModal.collectAsState()
            val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()

            MaterialTheme(
                colorScheme = colorSchemeFor(appColors, themeMode),
                typography = megingiardTypography,
            ) {
                CompositionLocalProvider(
                    LocalAppColors provides appColors,
                    LocalAppDimens provides AppDimens(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                    ) {
                        when {
                            isViewportEditActive -> {
                                MirrorEditorTopOverlay(
                                    onDone = {
                                        AppStateManager.setViewportEditActive(false)
                                        AppStateManager.openPrimaryModal(
                                            PrimaryModalConfig(
                                                type = PrimaryModalType.MACROPAD_EDITOR,
                                                payload = PrimaryModalPayload.MacroPad(section = EditorSection.MIRROR),
                                            ),
                                        )
                                    },
                                    onCancel = {
                                        AppStateManager.setViewportEditActive(false)
                                        AppStateManager.openPrimaryModal(
                                            PrimaryModalConfig(
                                                type = PrimaryModalType.MACROPAD_EDITOR,
                                                payload = PrimaryModalPayload.MacroPad(section = EditorSection.MIRROR),
                                            ),
                                        )
                                    },
                                )
                            }

                            activeModal != null -> {
                                PrimaryModalHost(
                                    config = activeModal!!,
                                    onDismiss = {
                                        AppLog.d(TAG, "Dismissing primary modal: ${activeModal?.type}")
                                        AppStateManager.closePrimaryModal()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (PrimaryOverlayInputBridge.processGenericMotionEvent(event) { action, dpadKeyCode ->
                val keyEvent = KeyEvent(action, dpadKeyCode)
                val handled = dispatchKeyEvent(keyEvent)
                if (!handled && action == KeyEvent.ACTION_DOWN) {
                    PrimaryOverlayInputBridge.sendFocusRecovery(dpadKeyCode)
                }
            }
        ) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                AppLog.i(TAG, "onKeyDown: Back/B-Button pressed -> handling back")
                if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    AppStateManager.closePrimaryModal()
                    AppStateManager.setActiveCropCutoutId(null)
                    AppStateManager.setSelectedCutoutId(null)
                }
                return true
            }

            KeyEvent.KEYCODE_BUTTON_L1 -> {
                AppLog.d(TAG, "onKeyDown: L1 pressed -> Bumper PREV")
                PrimaryOverlayInputBridge.sendBumper(BumperDirection.PREV)
                return true
            }

            KeyEvent.KEYCODE_BUTTON_R1 -> {
                AppLog.d(TAG, "onKeyDown: R1 pressed -> Bumper NEXT")
                PrimaryOverlayInputBridge.sendBumper(BumperDirection.NEXT)
                return true
            }

            KeyEvent.KEYCODE_BUTTON_A -> {
                AppLog.d(TAG, "onKeyDown: Button A pressed -> Forwarding as DPAD_CENTER down")
                val dpadCenterDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
                dispatchKeyEvent(dpadCenterDown)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> {
                AppLog.d(TAG, "onKeyUp: Button A released -> Forwarding as DPAD_CENTER up")
                val dpadCenterUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
                val handled = dispatchKeyEvent(dpadCenterUp)
                if (!handled) {
                    PrimaryOverlayInputBridge.sendFocusRecovery(KeyEvent.KEYCODE_BUTTON_A)
                }
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("wasFrozenForModal", wasFrozenForModal)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy: isFinishing=$isFinishing")
        if (isFinishing) {
            AppStateManager.closePrimaryModal()
            AppStateManager.setActiveCropCutoutId(null)
            if (wasFrozenForModal && ScreenCaptureManager.isCapturing.value) {
                AppLog.i(TAG, "Resuming live mirror capture after closing primary overlay activity")
                ScreenCaptureManager.setFrozen(false)
            }
            wasFrozenForModal = false
        }
    }
}
