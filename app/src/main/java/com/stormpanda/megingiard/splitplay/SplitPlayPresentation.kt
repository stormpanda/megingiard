package com.stormpanda.megingiard.splitplay

import android.app.Application
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.BackgroundMacroPadOverlay
import com.stormpanda.megingiard.mirror.MirrorPresentationLifecycleOwner
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppDimens
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalAppDimens
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "SplitPlayPresentation"

class SplitPlayPresentation(
    outerContext: Context,
    display: Display
) : Presentation(outerContext, display) {

    private lateinit var renderView: SplitPlayRenderView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "onCreate display=${display.displayId}")

        // Force full screen, hide system bars
        window?.let { win ->
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Block window from taking input focus globally, so physical buttons/input passes to game
            win.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            win.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        // Setup the lifecycle owners for Jetpack Compose support inside Presentation window
        val lifecycleOwner = MirrorPresentationLifecycleOwner(context.applicationContext as Application)
        window?.decorView?.apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
        }

        setOnDismissListener {
            AppLog.i(TAG, "dismissed → scope cancelled, lifecycle destroyed")
            scope.cancel()
            lifecycleOwner.destroy()
            if (SplitPlayManager.onFrameAvailable != null) {
                SplitPlayManager.onFrameAvailable = null
            }
        }

        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        setContentView(container)

        // Renders the bottom half of the game
        renderView = SplitPlayRenderView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setHalf(false) // Bottom screen shows the bottom half
        }
        container.addView(renderView)

        // Observe frame buffer updates from the virtual display sandbox
        SplitPlayManager.onFrameAvailable = { bitmap ->
            renderView.updateBitmap(bitmap)
        }

        // Compose View overlay for the MacroPad buttons
        val themeMode = SettingsManager.themeMode.value
        val userAccent = androidx.compose.ui.graphics.Color(SettingsManager.accentColor.value)
        val appColors = paletteFor(themeMode, userAccent)

        val composeViewContext = context.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION, null)
        val composeView = ComposeView(composeViewContext).apply {
            setContent {
                androidx.compose.material3.MaterialTheme(
                    colorScheme = colorSchemeFor(appColors, themeMode),
                    typography = megingiardTypography
                ) {
                    CompositionLocalProvider(
                        LocalAppColors provides appColors,
                        LocalAppDimens provides AppDimens()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Render Ambient MacroPad overlay (contains buttons + quick menu swipe indicator)
                            BackgroundMacroPadOverlay(showQuickMenuBar = true)
                        }
                    }
                }
            }
        }

        container.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Observe SplitPlayState lifecycle — auto-dismiss when inactive
        scope.launch {
            SplitPlayManager.state.collectLatest { state ->
                if (state is SplitPlayState.Inactive || state is SplitPlayState.Error) {
                    AppLog.i(TAG, "SplitPlay state is $state, dismissing presentation")
                    dismiss()
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y
        val displayId = SplitPlayManager.currentDisplayId

        // The game area is centered horizontally: 960x1080 inside 1920x1080 screen.
        // Touch bounds for the game box are [480, 1440].
        val gameLeft = 480f
        val gameRight = 1440f

        if (displayId >= 0 && x in gameLeft..gameRight) {
            // Touch is within the game area -> intercept and inject!
            val actionMasked = ev.actionMasked
            val pointerCount = ev.pointerCount

            when (actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val actionIndex = ev.actionIndex
                    val pointerId = ev.getPointerId(actionIndex)
                    val px = ev.getX(actionIndex)
                    val py = ev.getY(actionIndex)
                    val mapped = SplitPlayTouchMapper.mapTouch(4, px, py)
                    if (mapped != null) {
                        SplitPlayManager.injectTouch(displayId, pointerId, 0, mapped.first, mapped.second)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until pointerCount) {
                        val pointerId = ev.getPointerId(i)
                        val px = ev.getX(i)
                        val py = ev.getY(i)
                        val mapped = SplitPlayTouchMapper.mapTouch(4, px, py)
                        if (mapped != null) {
                            SplitPlayManager.injectTouch(displayId, pointerId, 1, mapped.first, mapped.second)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    val actionIndex = ev.actionIndex
                    val pointerId = ev.getPointerId(actionIndex)
                    val px = ev.getX(actionIndex)
                    val py = ev.getY(actionIndex)
                    val mapped = SplitPlayTouchMapper.mapTouch(4, px, py)
                    if (mapped != null) {
                        SplitPlayManager.injectTouch(displayId, pointerId, 2, mapped.first, mapped.second)
                    }
                }
            }
            return true // Absorb the touch event so it doesn't trigger MacroPad buttons underneath
        }

        // Touch is in the margins/gutters -> let it go to the Compose View for MacroPad button clicks
        return super.dispatchTouchEvent(ev)
    }
}
