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
import android.os.SystemClock
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.input.TouchInjector
import com.stormpanda.megingiard.macropad.TouchRecordingManager
import com.stormpanda.megingiard.macropad.TouchRecordingMode
import com.stormpanda.megingiard.macropad.TouchSample
import com.stormpanda.megingiard.mirror.projectCoordinates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay

private const val TAG = "RecordingMirrorPresent"

private const val RMP_VIRTUAL_DISPLAY_NAME = "RecordingCapture"

/** Duration of the immediate feedback tap injected right after the position is recorded. */
private const val RMP_FEEDBACK_TAP_DURATION_MS = 50L

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
    private val mediaProjection: MediaProjection?,
) : Presentation(context, display, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen) {

    private var virtualDisplay: VirtualDisplay? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    /** True if this Presentation started [TouchInjector]; false if it was already running. */
    private var injectorStartedByUs = false

    /** Prevent the system from dismissing this Presentation on back press. */
    override fun cancel() {
        AppLog.d(TAG, "cancel() ignored")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "onCreate display=${display?.displayId} src=${srcWidth}x${srcHeight}")

        // Start TouchInjector now so it is ready by the time the user taps.
        if (!TouchInjector.isRunning) {
            TouchInjector.start(context)
            injectorStartedByUs = true
            AppLog.d(TAG, "TouchInjector started by RecordingMirrorPresentation")
        }

        val lifecycleOwner = MirrorPresentationLifecycleOwner(context.applicationContext as Application)
        window?.decorView?.apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
        }

        setOnDismissListener {
            AppLog.i(TAG, "dismissed → scope cancelled, lifecycle destroyed")
            scope.cancel()
            if (injectorStartedByUs) {
                TouchInjector.stop()
                injectorStartedByUs = false
                AppLog.d(TAG, "TouchInjector stopped by RecordingMirrorPresentation")
            }
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
                if (mediaProjection != null) {
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
                } else {
                    AppLog.i(TAG, "surfaceCreated in privileged mode → sending surface to direct server")
                    DirectMirrorSurfaceBridge.sendToDirectServer(holder.surface)
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
                val mode by TouchRecordingManager.recordingMode.collectAsState()
                if (mode == TouchRecordingMode.GESTURE) {
                    GestureCaptureOverlay(
                        contentWidth  = finalWidth,
                        contentHeight = finalHeight,
                    )
                } else {
                    TapCaptureOverlay(
                        contentWidth  = finalWidth,
                        contentHeight = finalHeight,
                    )
                }
            }
        }
        container.addView(composeView)
        setContentView(container)
    }
}

/**
 * A transparent full-screen Box that captures the first touch-down event and delivers
 * its normalised position to [TouchRecordingManager]. The tap position is mapped through
 * the letterbox geometry so that tapping exactly on a pixel in the mirrored content area
 * records the corresponding normalised coordinate on the primary display.
 *
 * If the tap lands on the black letterbox bars, the event is ignored and the overlay
 * waits for the next tap. After a tap is recorded a brief DOWN→UP is also injected on
 * the primary display for immediate visual feedback.
 *
 * @param contentWidth  Width of the mirrored content area (letterboxed) in pixels.
 * @param contentHeight Height of the mirrored content area (letterboxed) in pixels.
 */
@Composable
private fun TapCaptureOverlay(contentWidth: Int, contentHeight: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Capture tap inside the restricted scope, break out immediately.
                // delay() is only allowed in the outer pointerInput scope, not in
                // awaitPointerEventScope (which is a restricted coroutine scope).
                var captured: Pair<Float, Float>? = null
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            val change = event.changes.firstOrNull() ?: continue
                            if (size.width <= 0 || size.height <= 0) continue
                            // Map tap through letterbox geometry. Taps on the black bars
                            // return null from projectCoordinates — ignore them.
                            val result = projectCoordinates(
                                touchX   = change.position.x,
                                touchY   = change.position.y,
                                screenW  = size.width.toFloat(),
                                screenH  = size.height.toFloat(),
                                sw       = contentWidth.toFloat(),
                                sh       = contentHeight.toFloat(),
                                scale    = 1f,
                                offsetX  = 0f,
                                offsetY  = 0f,
                            ) ?: continue  // tap on letterbox bar — ignore, wait for next tap
                            val (normX, normY) = result
                            AppLog.i(TAG, "tap captured normX=$normX normY=$normY")
                            change.consume()
                            captured = Pair(normX, normY)
                            break
                        }
                    }
                }
                // Now in the outer pointerInput scope — delay() is allowed here.
                val (normX, normY) = captured ?: return@pointerInput
                TouchInjector.injectTouch(TouchAction.DOWN, normX, normY)
                delay(RMP_FEEDBACK_TAP_DURATION_MS)
                TouchInjector.injectTouch(TouchAction.UP, normX, normY)
                TouchRecordingManager.onTapRecorded(normX, normY)
            },
    )
}

/**
 * A transparent full-screen Box that records a continuous multi-touch gesture.
 * Recording starts when the first finger touches the screen, and stops as soon as
 * all fingers are lifted. Captures coordinates, maps them through letterbox geometry
 * (clamping active fingers that drag out of bounds), feeds them live to TouchInjector,
 * and passes the completed TouchSample list to TouchRecordingManager.
 *
 * @param contentWidth  Width of the mirrored content area (letterboxed) in pixels.
 * @param contentHeight Height of the mirrored content area (letterboxed) in pixels.
 */
@Composable
private fun GestureCaptureOverlay(contentWidth: Int, contentHeight: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var recordingStarted = false
                var startEpochMs = 0L
                val samples = mutableListOf<TouchSample>()
                // Track active pointers to know when all pointers are released
                val activePointerIds = mutableSetOf<Long>()

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val now = SystemClock.elapsedRealtime()
                        
                        val changes = event.changes
                        if (changes.isEmpty()) continue

                        // Process changes to track pointer down/move/up
                        for (change in changes) {
                            val pointerId = change.id.value
                            val position = change.position
                            val isPressed = change.pressed
                            val wasPressed = change.previousPressed
                            val isAlreadyTracked = activePointerIds.contains(pointerId)
                            
                            // Map coordinates through letterbox geometry.
                            val result = projectCoordinates(
                                touchX = position.x,
                                touchY = position.y,
                                screenW = size.width.toFloat(),
                                screenH = size.height.toFloat(),
                                sw = contentWidth.toFloat(),
                                sh = contentHeight.toFloat(),
                                scale = 1f,
                                offsetX = 0f,
                                offsetY = 0f,
                            )

                            if (result == null && !isAlreadyTracked) {
                                // Ignore this touch event entirely if it's not already being tracked and is out of bounds
                                continue
                            }

                            val (normX, normY) = if (result != null) {
                                result
                            } else {
                                // Manual calculation and clamping since it is already tracked but went out of bounds
                                val screenCenterX = size.width.toFloat() / 2f
                                val screenCenterY = size.height.toFloat() / 2f
                                val svCenterX = contentWidth.toFloat() / 2f
                                val svCenterY = contentHeight.toFloat() / 2f
                                val svX = (position.x - screenCenterX) + svCenterX
                                val svY = (position.y - screenCenterY) + svCenterY
                                val nx = (svX / contentWidth.toFloat()).coerceIn(0f, 1f)
                                val ny = (svY / contentHeight.toFloat()).coerceIn(0f, 1f)
                                Pair(nx, ny)
                            }

                            if (!recordingStarted) {
                                // Start of the gesture: first pointer touches the screen
                                recordingStarted = true
                                startEpochMs = now
                                AppLog.i(TAG, "gesture recording started")
                            }

                            val offsetMs = now - startEpochMs

                            val action = when {
                                isPressed && !wasPressed -> {
                                    activePointerIds.add(pointerId)
                                    TouchAction.DOWN
                                }
                                isPressed && wasPressed -> {
                                    TouchAction.MOVE
                                }
                                !isPressed && wasPressed -> {
                                    activePointerIds.remove(pointerId)
                                    TouchAction.UP
                                }
                                else -> continue
                            }

                            // Keep track of our sample
                            samples.add(
                                TouchSample(
                                    offsetMs = offsetMs,
                                    pointerId = pointerId.toInt(),
                                    action = action,
                                    normX = normX,
                                    normY = normY
                                )
                            )

                            // Inject touch live so the user can see what they are doing!
                            TouchInjector.injectTouch(pointerId.toInt(), action, normX, normY)

                            change.consume()
                        }

                        // Check if all pointers are released
                        if (recordingStarted && activePointerIds.isEmpty()) {
                            AppLog.i(TAG, "gesture recording finished with ${samples.size} samples")
                            TouchRecordingManager.onGestureRecorded(samples)
                            break
                        }
                    }
                }
            }
    )
}
