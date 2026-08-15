package com.stormpanda.megingiard.ui

import android.os.Bundle
import android.view.KeyEvent
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
import com.stormpanda.megingiard.mirror.CropSelectorOverlay
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.flow.filter
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
    private var wasFrozenInitially = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "onCreate: started on display=${display?.displayId}")

        wasFrozenInitially = savedInstanceState?.getBoolean("wasFrozenInitially") ?: ScreenCaptureManager.isFrozen.value
        if (savedInstanceState == null && !wasFrozenInitially) {
            ScreenCaptureManager.setFrozen(true)
        }

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        lifecycleScope.launch {
            AppStateManager.activePrimaryModal
                .filter { it == null && AppStateManager.activeCropCutoutId.value == null }
                .collect {
                    AppLog.i(TAG, "activePrimaryModal is null -> finishing activity")
                    finish()
                }
        }

        setContent {
            val themeMode by SettingsManager.themeMode.collectAsState()
            val userAccentArgb by SettingsManager.accentColor.collectAsState()
            val appColors = paletteFor(themeMode, Color(userAccentArgb))
            val activeModal by AppStateManager.activePrimaryModal.collectAsState()
            val activeCropCutoutId by AppStateManager.activeCropCutoutId.collectAsState()

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
                            activeCropCutoutId != null -> {
                                CropSelectorOverlay(
                                    cutoutId = activeCropCutoutId!!,
                                    onDismiss = {
                                        AppLog.d(TAG, "Dismissing crop selector")
                                        AppStateManager.setActiveCropCutoutId(null)
                                        AppStateManager.closePrimaryModal()
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

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK) {
            AppLog.i(TAG, "onKeyDown: Back/B-Button pressed -> closing primary modal")
            AppStateManager.closePrimaryModal()
            AppStateManager.setActiveCropCutoutId(null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("wasFrozenInitially", wasFrozenInitially)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy: isFinishing=$isFinishing")
        if (isFinishing) {
            AppStateManager.closePrimaryModal()
            AppStateManager.setActiveCropCutoutId(null)
            if (!wasFrozenInitially) {
                ScreenCaptureManager.setFrozen(false)
            }
        }
    }
}
