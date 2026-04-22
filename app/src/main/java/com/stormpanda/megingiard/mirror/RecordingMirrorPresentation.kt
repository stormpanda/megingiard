package com.stormpanda.megingiard.mirror

import android.app.Application
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.TouchRecordingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val TAG = "RecordingMirrorPresent"

private const val RMP_VIRTUAL_DISPLAY_NAME = "RecordingCapture"

/**
 * A minimal [Presentation] shown on the secondary display while the user records a
 * [com.stormpanda.megingiard.macropad.MacroStep.TouchTap] position.
 *
 * It creates its own [VirtualDisplay] from the same [MediaProjection] token used by
 * [ScreenCaptureService], so the user sees a live mirror of the primary screen and can
 * tap the desired position. The normalised tap coordinates are delivered to
 * [TouchRecordingManager.onTapRecorded] which resets [TouchRecordingManager.recordingRequested]
 * — causing [ScreenCaptureService] to dismiss this Presentation.
 *
 * The Presentation does NOT share or touch [ScreenCaptureManager]'s viewport state.
 */
class RecordingMirrorPresentation(
    context: Context,
    display: Display,
    private val srcWidth: Int,
    private val srcHeight: Int,
    private val mediaProjection: MediaProjection,
) : Presentation(context, display, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen) {

    private var virtualDisplay: VirtualDisplay? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Prevent the system from dismissing this Presentation on back press. */
    override fun cancel() {
        AppLog.d(TAG, "cancel() ignored")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "onCreate display=${display?.displayId} src=${srcWidth}x${srcHeight}")

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
        }

        // ── Letterbox layout (same algorithm as MirrorPresentation) ───────────────
        val windowContext = context.createWindowContext(
            display,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            null,
        )
        val windowMetrics = windowContext.getSystemService(WindowManager::class.java).maximumWindowMetrics
        val targetBounds = windowMetrics.bounds
        val targetWidth = targetBounds.width()
        val targetHeight = targetBounds.height()

        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()

        var finalWidth = targetWidth
        var finalHeight = targetHeight
        if (srcRatio > targetRatio) {
            finalHeight = (targetWidth / srcRatio).toInt()
        } else {
            finalWidth = (targetHeight * srcRatio).toInt()
        }

        val dpi = context.resources.displayMetrics.densityDpi

        // ── Views ──────────────────────────────────────────────────────────────────
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
        }

        val sv = SurfaceView(context).apply {
            layoutParams = FrameLayout.LayoutParams(finalWidth, finalHeight, Gravity.CENTER)
            setZOrderMediaOverlay(true)
        }
        sv.holder.setFixedSize(srcWidth, srcHeight)

        sv.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                virtualDisplay?.release()
                try {
                    virtualDisplay = mediaProjection.createVirtualDisplay(
                        RMP_VIRTUAL_DISPLAY_NAME,
                        srcWidth, srcHeight, dpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        holder.surface, null, null,
                    )
                    AppLog.i(TAG, "VirtualDisplay created ${srcWidth}x${srcHeight} dpi=$dpi")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to create VirtualDisplay", e)
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                virtualDisplay?.release()
                virtualDisplay = null
                AppLog.d(TAG, "surfaceDestroyed → VirtualDisplay released")
            }
        })

        container.addView(sv)

        // ── ComposeView: transparent tap-capture overlay ──────────────────────────
        // Use a TYPE_APPLICATION window context so that any Compose Dialogs launched
        // from within the ComposeView do not inherit the Presentation window type.
        val composeViewContext = context.createWindowContext(
            display,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            null,
        )
        val composeView = ComposeView(composeViewContext).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setContent {
                TapCaptureOverlay()
            }
        }
        container.addView(composeView)
        setContentView(container)
    }
}

/**
 * A transparent full-screen Box that captures the first touch-down event and delivers
 * its normalised position to [TouchRecordingManager]. After the tap is recorded the
 * block exits, leaving the surface unresponsive until the Presentation is dismissed
 * by [ScreenCaptureService].
 */
@Composable
private fun TapCaptureOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            val change = event.changes.firstOrNull() ?: continue
                            if (size.width <= 0 || size.height <= 0) continue
                            val normX = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val normY = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                            AppLog.i(TAG, "tap captured normX=$normX normY=$normY")
                            change.consume()
                            TouchRecordingManager.onTapRecorded(normX, normY)
                            break
                        }
                    }
                }
            },
    )
}
