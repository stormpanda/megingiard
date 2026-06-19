package com.stormpanda.megingiard.mirror

import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppDimens
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalAppDimens
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private const val TAG = "CropSelectorActivity"

class CropSelectorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "onCreate: crop selector activity started on display=${display?.displayId}")
        enableEdgeToEdge()

        // Make window translucent/immersive
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        lifecycleScope.launch {
            AppStateManager.activeCropCutoutId
                .filter { it == null }
                .collect {
                    AppLog.i(TAG, "activeCropCutoutId is null -> finishing activity")
                    finish()
                }
        }

        setContent {
            val themeMode by SettingsManager.themeMode.collectAsState()
            val userAccentArgb by SettingsManager.accentColor.collectAsState()
            val appColors = paletteFor(themeMode, Color(userAccentArgb))
            val activeCropCutoutId by AppStateManager.activeCropCutoutId.collectAsState()

            MaterialTheme(
                colorScheme = colorSchemeFor(appColors, themeMode),
                typography = megingiardTypography
            ) {
                CompositionLocalProvider(
                    LocalAppColors provides appColors,
                    LocalAppDimens provides AppDimens()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        activeCropCutoutId?.let { cutoutId ->
                            CropSelectorOverlay(
                                cutoutId = cutoutId,
                                onDismiss = {
                                    AppLog.d(TAG, "Dismissing crop selector")
                                    AppStateManager.setActiveCropCutoutId(null)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLog.i(TAG, "onDestroy")
    }
}
