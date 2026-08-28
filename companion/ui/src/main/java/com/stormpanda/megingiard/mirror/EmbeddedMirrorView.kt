package com.stormpanda.megingiard.mirror

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.MacroPadMediaRepository
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.privd.PrivdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "EmbeddedMirrorView"

@Composable
fun EmbeddedMirrorView(
    modifier: Modifier = Modifier,
    surfaceOwner: String = MasterSurfaceRegistry.OWNER_MACROPAD,
    surfacePriority: Int = MasterSurfaceRegistry.PRIORITY_MACROPAD,
    overrideCutouts: List<ScreenCutout>? = null,
    showLayoutBackground: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val capturing by ScreenCaptureManager.isCapturing.collectAsState()
    val isFrozen by ScreenCaptureManager.isFrozen.collectAsState()
    val frozenBitmap by ScreenCaptureManager.frozenBitmap.collectAsState()
    val cutouts by ScreenCaptureManager.cutouts.collectAsState()
    val edgeBlendWidthDp by ScreenCaptureManager.edgeBlendWidthDp.collectAsState()
    val maxFps by ScreenCaptureManager.maxFps.collectAsState()
    val scale by ScreenCaptureManager.scale.collectAsState()
    val offsetX by ScreenCaptureManager.offsetX.collectAsState()
    val offsetY by ScreenCaptureManager.offsetY.collectAsState()
    val srcWidth by ScreenCaptureManager.captureSourceWidth.collectAsState()
    val srcHeight by ScreenCaptureManager.captureSourceHeight.collectAsState()
    val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()
    val isMirrorEditorBackgroundHidden by AppStateManager.isMirrorEditorBackgroundHidden.collectAsState()
    val isViewportEditActive by AppStateManager.isViewportEditActive.collectAsState()
    val layout by MacroPadState.activeLayout.collectAsState()
    val screenshotRequested by ScreenCaptureManager.screenshotRequested.collectAsState()

    val effectiveCutouts = overrideCutouts ?: cutouts
    val effectiveShowLayoutBackground = showLayoutBackground && !(isViewportEditActive && isMirrorEditorBackgroundHidden)

    var bgBitmap by remember(layout?.backgroundImagePath, layout?.backgroundImageVersion, effectiveShowLayoutBackground) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(layout?.backgroundImagePath, layout?.backgroundImageVersion, effectiveShowLayoutBackground) {
        if (!effectiveShowLayoutBackground) {
            bgBitmap = null
            return@LaunchedEffect
        }
        val path = layout?.backgroundImagePath
        if (path != null) {
            withContext(Dispatchers.IO) {
                try {
                    bgBitmap = MacroPadMediaRepository.loadScaledBitmap(context, path)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to load background image for EmbeddedMirrorView", e)
                    bgBitmap = null
                }
            }
        } else {
            bgBitmap = null
        }
    }

    val containerHolder =
        remember {
            object {
                var container: MultiCutoutContainer? = null
                var textureView: ThrottledTextureView? = null
                var masterSurface: Surface? = null
                var currentRoutedSurface: Surface? = null
                var gpuMotionSmoother: GpuMotionSmoother? = null

                fun updateSurfaceRouting(
                    width: Int,
                    height: Int,
                    activeCutouts: List<ScreenCutout>,
                    isMouseActive: Boolean,
                ) {
                    val master = masterSurface ?: return
                    val smoothingCutout = if (!isMouseActive) activeCutouts.firstOrNull { it.motionSmoothing } else null
                    val effectiveStrength = smoothingCutout?.motionSmoothingStrength ?: 0

                    var smoother = gpuMotionSmoother
                    if (smoother == null && width > 0 && height > 0) {
                        AppLog.i(
                            TAG,
                            "[$surfaceOwner] Initializing GpuMotionSmoother unified pipeline for master Surface (strength=$effectiveStrength)",
                        )
                        smoother = GpuMotionSmoother(master, width, height, effectiveStrength)
                        gpuMotionSmoother = smoother
                        val inSurface = smoother.inputSurface
                        if (inSurface != null) {
                            currentRoutedSurface = inSurface
                            MasterSurfaceRegistry.registerMasterSurface(surfaceOwner, inSurface, surfacePriority)
                        }
                    } else if (smoother != null) {
                        smoother.updateStrength(effectiveStrength)
                        val inSurface = smoother.inputSurface
                        if (inSurface != null && currentRoutedSurface != inSurface) {
                            currentRoutedSurface = inSurface
                            MasterSurfaceRegistry.registerMasterSurface(surfaceOwner, inSurface, surfacePriority)
                        }
                    } else {
                        currentRoutedSurface = master
                        MasterSurfaceRegistry.registerMasterSurface(surfaceOwner, master, surfacePriority)
                    }
                }
            }
        }

    // React to screenshot requests
    LaunchedEffect(screenshotRequested) {
        if (screenshotRequested) {
            val target = ScreenCaptureManager.pendingScreenshotTarget.value ?: ScreenshotTarget.TOP
            if (target == ScreenshotTarget.TOP && !PrivdClient.isConnected) {
                val tv = containerHolder.textureView
                if (tv != null && tv.width > 0 && tv.height > 0) {
                    try {
                        val bitmap = tv.bitmap
                        if (bitmap != null) {
                            ScreenCaptureManager.showScreenshotPreview(bitmap)
                        }
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Failed to capture TextureView bitmap for screenshot", e)
                    }
                }
                ScreenCaptureManager.consumeScreenshotRequest()
            }
        }
    }

    // React to freeze state changes
    LaunchedEffect(isFrozen) {
        val tv = containerHolder.textureView
        val mcc = containerHolder.container
        if (isFrozen && tv != null && tv.width > 0 && tv.height > 0) {
            try {
                val bitmap = tv.bitmap
                if (bitmap != null) {
                    ScreenCaptureManager.setFrozenBitmap(bitmap)
                    mcc?.isFrozen = true
                    mcc?.frozenBitmap = bitmap
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to freeze mirror frame", e)
            }
        } else if (!isFrozen) {
            mcc?.isFrozen = false
            mcc?.frozenBitmap = null
        }
    }

    DisposableEffect(surfaceOwner, surfacePriority) {
        onDispose {
            val surfaceToClear = containerHolder.currentRoutedSurface ?: containerHolder.masterSurface
            containerHolder.gpuMotionSmoother?.release()
            containerHolder.gpuMotionSmoother = null
            containerHolder.currentRoutedSurface = null
            MasterSurfaceRegistry.unregisterMasterSurface(surfaceOwner, surfaceToClear)
            containerHolder.masterSurface?.release()
            containerHolder.masterSurface = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val w = if (srcWidth > 0) srcWidth else 1920
            val h = if (srcHeight > 0) srcHeight else 1080
            val mcc =
                MultiCutoutContainer(ctx, w, h).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                }
            containerHolder.container = mcc

            val tv =
                ThrottledTextureView(ctx).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                }
            containerHolder.textureView = tv
            mcc.addView(tv)

            tv.surfaceTextureListener =
                object : TextureView.SurfaceTextureListener {
                    private var lastUpdateTime = 0L

                    override fun onSurfaceTextureAvailable(
                        st: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        val currentSrcW =
                            if (ScreenCaptureManager.captureSourceWidth.value >
                                0
                            ) {
                                ScreenCaptureManager.captureSourceWidth.value
                            } else {
                                1920
                            }
                        val currentSrcH =
                            if (ScreenCaptureManager.captureSourceHeight.value >
                                0
                            ) {
                                ScreenCaptureManager.captureSourceHeight.value
                            } else {
                                1080
                            }
                        st.setDefaultBufferSize(currentSrcW, currentSrcH)
                        val surface = Surface(st)
                        containerHolder.masterSurface = surface
                        try {
                            val fps = ScreenCaptureManager.maxFps.value
                            AppLog.i(TAG, "Setting initial surface frame rate to $fps FPS")
                            surface.setFrameRate(fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                        } catch (e: Exception) {
                            AppLog.e(TAG, "Error setting initial surface frame rate", e)
                        }
                        AppLog.d(TAG, "master TextureView surface available for $surfaceOwner")
                        containerHolder.updateSurfaceRouting(
                            currentSrcW,
                            currentSrcH,
                            effectiveCutouts,
                            overrideCutouts != null || AppStateManager.isFullscreenMouseActive.value,
                        )
                    }

                    override fun onSurfaceTextureSizeChanged(
                        st: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {}

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        AppLog.d(TAG, "master TextureView surface destroyed for $surfaceOwner")
                        val surfaceToClear = containerHolder.currentRoutedSurface ?: containerHolder.masterSurface
                        containerHolder.gpuMotionSmoother?.release()
                        containerHolder.gpuMotionSmoother = null
                        containerHolder.currentRoutedSurface = null
                        MasterSurfaceRegistry.unregisterMasterSurface(surfaceOwner, surfaceToClear)
                        containerHolder.masterSurface?.release()
                        containerHolder.masterSurface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                        val now = SystemClock.elapsedRealtime()
                        val fps = ScreenCaptureManager.maxFps.value.coerceIn(10, 60)
                        val interval = 1000L / fps
                        if (now - lastUpdateTime >= interval) {
                            mcc.invalidate()
                            lastUpdateTime = now
                        }
                    }
                }

            mcc
        },
        update = { mcc ->
            mcc.cutouts = effectiveCutouts
            mcc.isFrozen = isFrozen
            mcc.frozenBitmap = frozenBitmap
            mcc.viewportScale = if (overrideCutouts != null) 1f else scale
            mcc.viewportOffsetX = if (overrideCutouts != null) 0f else offsetX
            mcc.viewportOffsetY = if (overrideCutouts != null) 0f else offsetY
            mcc.bgBitmap = bgBitmap
            mcc.useAsMask = effectiveShowLayoutBackground && layout?.useBackgroundImageAsMask == true
            mcc.bgImageScale = if (effectiveShowLayoutBackground) layout?.bgImageScale ?: 1f else 1f
            mcc.bgImageOffsetX = if (effectiveShowLayoutBackground) layout?.bgImageOffsetX ?: 0f else 0f
            mcc.bgImageOffsetY = if (effectiveShowLayoutBackground) layout?.bgImageOffsetY ?: 0f else 0f
            mcc.bgImageDim = if (effectiveShowLayoutBackground) layout?.backgroundImageDim ?: 0f else 0f
            mcc.ambientDim = if (overrideCutouts == null) layout?.ambientDim ?: 0f else 0f

            val tv = containerHolder.textureView
            if (tv != null) {
                tv.maxFps = maxFps
                containerHolder.masterSurface?.let { surface ->
                    if (surface.isValid) {
                        try {
                            surface.setFrameRate(maxFps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                        } catch (e: Exception) {
                            AppLog.e(TAG, "Error updating surface frame rate", e)
                        }
                    }
                }
            }

            val currentSrcW = if (srcWidth > 0) srcWidth else 1920
            val currentSrcH = if (srcHeight > 0) srcHeight else 1080
            containerHolder.updateSurfaceRouting(
                currentSrcW,
                currentSrcH,
                effectiveCutouts,
                overrideCutouts != null || isFullscreenMouseActive,
            )
        },
    )
}
