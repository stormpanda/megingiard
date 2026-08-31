package com.stormpanda.megingiard.mirror

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.DialogToastManager
import com.stormpanda.megingiard.ui.DialogToastPill
import com.stormpanda.megingiard.ui.GamepadPill
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalFirstContentRequester
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.handle2DAdjustmentKeyEvent
import com.stormpanda.megingiard.ui.handleAdjustmentKeyEvent
import com.stormpanda.megingiard.ui.isBackKey
import com.stormpanda.megingiard.ui.launchDirectionalRepeat
import com.stormpanda.megingiard.ui.rememberBezelBrush
import com.stormpanda.megingiard.ui.rememberGamepadBringIntoViewSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

private const val TAG = "MirrorEditorTopOverlay"

// ── Proportional compact styling (25% smaller than default primary overlay cards) ──
private val METO_TOOLBOX_WIDTH = 220.dp
private val METO_CONTAINER_CORNER = 12.dp
private val METO_CONTAINER_ELEVATION = 16.dp
private val METO_TOOLBOX_PADDING_START = 24.dp
private val METO_TOOLBOX_PADDING_VERTICAL = 20.dp
private val METO_INNER_PADDING_H = 8.dp
private val METO_INNER_PADDING_V = 8.dp
private val METO_ITEM_SPACING = 6.dp

private val METO_TOGGLE_BUTTON_SIZE = 20.dp
private val METO_TOGGLE_ICON_SIZE = 14.dp

private val METO_CARD_CORNER = 8.dp
private val METO_CARD_MIN_HEIGHT = 38.dp
private val METO_CARD_PADDING_H = 8.dp
private val METO_CARD_PADDING_V = 5.dp

private val METO_ICON_BOX_SIZE = 26.dp
private val METO_ICON_SIZE = 16.dp
private val METO_ICON_BOX_CORNER = 6.dp
private val METO_ROW_SPACING = 8.dp

private val METO_TEXT_SIZE_TITLE = 11.sp
private val METO_TEXT_SIZE_PILL = 9.5.sp
private val METO_PILL_CORNER = 12.dp
private val METO_PILL_PADDING_H = 7.dp
private val METO_PILL_PADDING_V = 2.dp

private val METO_HANDLE_WIDTH = 40.dp
private val METO_HANDLE_HEIGHT = 4.dp
private val METO_HANDLE_V_PADDING_BOTTOM = 8.dp
private val METO_HANDLE_V_PADDING_TOP = 4.dp

private val METO_FOCUS_BORDER_WIDTH = 2.dp
private val METO_DEFAULT_BORDER_WIDTH = 1.dp
private val METO_FOCUS_ELEVATION = 4.dp

private const val METO_SURFACE_ALPHA = 0.70f
private const val METO_INITIAL_FOCUS_DELAY_MS = 100L
private val METO_SCROLL_EXTRA_PADDING = 0.dp

private const val METO_NORMAL_STEP_PX = 10
private const val METO_FINE_STEP_PX = 1
private const val METO_ADJUST_TOAST_DURATION_MS = 5000L

private const val METO_FALLBACK_SRC_WIDTH = 1920f
private const val METO_FALLBACK_SRC_HEIGHT = 1080f
private const val METO_FALLBACK_SEC_WIDTH = 1240f
private const val METO_FALLBACK_SEC_HEIGHT = 1080f

/**
 * Top-Screen (Display 0) Overlay for the Screen Mirroring Editor.
 *
 * Renders the live crop bounds of the selected cutout via [CropSelectorOverlay],
 * while hosting a sleek, controller-navigable vertical toolbox docked and draggable in 2D anywhere on screen.
 * Supports full minimize/collapse animation, single-card cycling, and strict focus transfer on Save / Discard.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun MirrorEditorTopOverlay(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppLog.d(TAG, "MirrorEditorTopOverlay: composition")
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val activeLayout by MacroPadState.activeLayout.collectAsState()
    val layout = activeLayout ?: return

    // Track baseline saved state; all modifications are in-flight until explicitly saved
    var savedCutouts by remember(layout.id) { mutableStateOf(layout.mirrorCutouts) }
    val currentCutouts = layout.mirrorCutouts
    val hasChanges = currentCutouts != savedCutouts

    val selectedCutoutId by AppStateManager.selectedCutoutId.collectAsState()
    val cutouts = layout.mirrorCutouts
    val selectedCutout = cutouts.find { it.id == selectedCutoutId } ?: cutouts.firstOrNull()

    var showExitPrompt by remember { mutableStateOf(false) }
    var isMinimized by remember { mutableStateOf(false) }

    val inputModeManager = LocalInputModeManager.current
    val firstItemFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }
    val collapseButtonFocusRequester = remember { FocusRequester() }
    val bringIntoViewSpec = rememberGamepadBringIntoViewSpec(extraPadding = METO_SCROLL_EXTRA_PADDING)

    fun handleBackAction(): Boolean {
        AppLog.d(TAG, "handleBackAction: hasChanges=$hasChanges, showExitPrompt=$showExitPrompt, isMinimized=$isMinimized")
        if (hasChanges) {
            if (!showExitPrompt) {
                showExitPrompt = true
                saveFocusRequester.requestFocus()
                return true
            } else {
                showExitPrompt = false
                saveFocusRequester.requestFocus()
                return true
            }
        } else {
            // No in-flight changes -> leave editing mode immediately
            onCancel()
            return true
        }
    }

    // Intercept hardware Back / Controller B-Button via standard BackHandler
    BackHandler {
        handleBackAction()
    }

    // Auto-select the first cutout if none is currently selected
    LaunchedEffect(layout.id, cutouts.size) {
        if (selectedCutoutId == null && cutouts.isNotEmpty()) {
            val firstId = cutouts.first().id
            AppLog.d(TAG, "Auto-selecting initial cutout: $firstId")
            AppStateManager.setSelectedCutoutId(firstId)
        }
    }

    // Request initial focus and keyboard input mode on presentation
    LaunchedEffect(Unit) {
        inputModeManager.requestInputMode(InputMode.Keyboard)
        try {
            firstItemFocusRequester.requestFocus()
            AppLog.d(TAG, "MirrorEditorTopOverlay: initial focus requested")
        } catch (_: IllegalStateException) {
            delay(METO_INITIAL_FOCUS_DELAY_MS)
            try {
                firstItemFocusRequester.requestFocus()
                AppLog.d(TAG, "MirrorEditorTopOverlay: initial focus retry succeeded")
            } catch (_: IllegalStateException) {
                AppLog.w(TAG, "MirrorEditorTopOverlay: firstItemFocusRequester unattached on initial focus")
            }
        }
    }

    // Listen to focus recovery events from PrimaryOverlayInputBridge
    LaunchedEffect(Unit) {
        PrimaryOverlayInputBridge.focusRecoveryEvents.collect { keyCode ->
            inputModeManager.requestInputMode(InputMode.Keyboard)
            try {
                if (showExitPrompt) {
                    saveFocusRequester.requestFocus()
                } else {
                    firstItemFocusRequester.requestFocus()
                }
                AppLog.d(TAG, "MirrorEditorTopOverlay: focus recovered on keyCode=$keyCode")
            } catch (_: IllegalStateException) {
                AppLog.w(TAG, "MirrorEditorTopOverlay: focus requester unattached on focus recovery")
            }
        }
    }

    val activeToast by DialogToastManager.currentToast.collectAsState()
    val captureSourceWidth by ScreenCaptureManager.captureSourceWidth.collectAsState()
    val captureSourceHeight by ScreenCaptureManager.captureSourceHeight.collectAsState()
    val srcWidth = if (captureSourceWidth > 0) captureSourceWidth.toFloat() else METO_FALLBACK_SRC_WIDTH
    val srcHeight = if (captureSourceHeight > 0) captureSourceHeight.toFloat() else METO_FALLBACK_SRC_HEIGHT

    val surfaceWidth by ScreenCaptureManager.surfaceWidth.collectAsState()
    val surfaceHeight by ScreenCaptureManager.surfaceHeight.collectAsState()
    val secScreenW = if (surfaceWidth > 0f) surfaceWidth else METO_FALLBACK_SEC_WIDTH
    val secScreenH = if (surfaceHeight > 0f) surfaceHeight else METO_FALLBACK_SEC_HEIGHT

    var topHToggle by remember(selectedCutout?.id) { mutableIntStateOf(0) }
    var topVToggle by remember(selectedCutout?.id) { mutableIntStateOf(0) }
    var bottomHToggle by remember(selectedCutout?.id) { mutableIntStateOf(0) }
    var bottomVToggle by remember(selectedCutout?.id) { mutableIntStateOf(0) }

    fun updateCutout(
        cutoutId: String,
        transform: (ScreenCutout, List<ScreenCutout>) -> ScreenCutout?,
    ) {
        val currentProfile = MacroPadState.activeProfile.value ?: return
        val currentLayout = currentProfile.layouts.firstOrNull { it.id == currentProfile.activeLayoutId } ?: return
        val cur = currentLayout.mirrorCutouts.firstOrNull { it.id == cutoutId } ?: return
        val others = currentLayout.mirrorCutouts.filter { it.id != cutoutId }
        val updated = transform(cur, others) ?: return
        if (updated == cur) return
        val updatedList = currentLayout.mirrorCutouts.map { if (it.id == cur.id) updated else it }
        MacroPadState.updateLayout(currentLayout.copy(mirrorCutouts = updatedList))
    }

    fun moveTopCutout(
        cutoutId: String,
        dx: Int,
        dy: Int,
    ) = updateCutout(cutoutId) { cur, _ ->
        val stepX = 1f / srcWidth
        val stepY = 1f / srcHeight
        cur.copy(
            srcX = (cur.srcX + dx * stepX).coerceIn(0f, 1f - cur.srcWidth),
            srcY = (cur.srcY + dy * stepY).coerceIn(0f, 1f - cur.srcHeight),
        )
    }

    fun resizeTopCutout(
        cutoutId: String,
        dx: Int,
        dy: Int,
    ) = updateCutout(cutoutId) { cur, others ->
        if (cur.aspectRatioMode == AspectRatioMode.BOTTOM) {
            val stepDelta =
                if (dx != 0) {
                    dx
                } else if (dy != 0) {
                    -dy
                } else {
                    0
                }
            if (stepDelta == 0) return@updateCutout null
            val cutoutRatio = (cur.destWidth * secScreenW) / (cur.destHeight * secScreenH)
            val normCropRatio = cutoutRatio * (srcHeight / srcWidth)
            val geom =
                calculateProportionalResizedBounds(
                    normX = cur.srcX,
                    normY = cur.srcY,
                    normW = cur.srcWidth,
                    normH = cur.srcHeight,
                    screenWidth = srcWidth,
                    screenHeight = srcHeight,
                    stepDelta = stepDelta,
                    targetNormRatio = normCropRatio,
                )
            return@updateCutout cur.copy(srcX = geom.x, srcY = geom.y, srcWidth = geom.w, srcHeight = geom.h)
        }

        val resized =
            calculateResizedBounds(
                normX = cur.srcX,
                normY = cur.srcY,
                normW = cur.srcWidth,
                normH = cur.srcHeight,
                screenWidth = srcWidth,
                screenHeight = srcHeight,
                dx = dx,
                dy = dy,
                hToggle = topHToggle,
                vToggle = topVToggle,
            )
        topHToggle = resized.hToggle
        topVToggle = resized.vToggle

        var updated =
            cur.copy(
                srcX = resized.x,
                srcY = resized.y,
                srcWidth = resized.width,
                srcHeight = resized.height,
            )
        if (updated.aspectRatioMode == AspectRatioMode.TOP) {
            val cropRatio = (updated.srcWidth * srcWidth) / (updated.srcHeight * srcHeight)
            val (newDestW, newDestH) =
                adjustDestSizeToAspectRatio(
                    destX = updated.destX,
                    destY = updated.destY,
                    destWidth = updated.destWidth,
                    destHeight = updated.destHeight,
                    cropRatio = cropRatio,
                    screenW = secScreenW,
                    screenH = secScreenH,
                )
            if (!isCutoutGeometryValid(updated.destX, updated.destY, newDestW, newDestH, others)) {
                return@updateCutout null
            }
            updated = updated.copy(destWidth = newDestW, destHeight = newDestH)
        }
        updated
    }

    fun moveBottomCutout(
        cutoutId: String,
        dx: Int,
        dy: Int,
    ) = updateCutout(cutoutId) { cur, _ ->
        val stepX = 1f / secScreenW
        val stepY = 1f / secScreenH
        val (clampedX, clampedY) =
            clampCutoutDrag(
                cutoutId = cur.id,
                originalX = cur.destX,
                originalY = cur.destY,
                targetX = cur.destX + dx * stepX,
                targetY = cur.destY + dy * stepY,
                width = cur.destWidth,
                height = cur.destHeight,
                allCutouts = cutouts,
            )
        cur.copy(destX = clampedX, destY = clampedY)
    }

    fun resizeBottomCutout(
        cutoutId: String,
        dx: Int,
        dy: Int,
    ) = updateCutout(cutoutId) { cur, others ->
        if (cur.aspectRatioMode == AspectRatioMode.TOP) {
            val stepDelta =
                if (dx != 0) {
                    dx
                } else if (dy != 0) {
                    -dy
                } else {
                    0
                }
            if (stepDelta == 0) return@updateCutout null
            val cropRatio = (cur.srcWidth * srcWidth) / (cur.srcHeight * srcHeight)
            val normRatio = cropRatio * (secScreenH / secScreenW)
            val geom =
                calculateProportionalResizedBounds(
                    normX = cur.destX,
                    normY = cur.destY,
                    normW = cur.destWidth,
                    normH = cur.destHeight,
                    screenWidth = secScreenW,
                    screenHeight = secScreenH,
                    stepDelta = stepDelta,
                    targetNormRatio = normRatio,
                    others = others,
                )
            return@updateCutout cur.copy(destX = geom.x, destY = geom.y, destWidth = geom.w, destHeight = geom.h)
        }

        val resized =
            calculateResizedBounds(
                normX = cur.destX,
                normY = cur.destY,
                normW = cur.destWidth,
                normH = cur.destHeight,
                screenWidth = secScreenW,
                screenHeight = secScreenH,
                dx = dx,
                dy = dy,
                hToggle = bottomHToggle,
                vToggle = bottomVToggle,
                others = others,
            )
        bottomHToggle = resized.hToggle
        bottomVToggle = resized.vToggle

        var updated =
            cur.copy(
                destX = resized.x,
                destY = resized.y,
                destWidth = resized.width,
                destHeight = resized.height,
            )
        if (updated.aspectRatioMode == AspectRatioMode.BOTTOM) {
            updated =
                adjustSourceCropToAspectRatio(
                    cutout = updated,
                    screenW = secScreenW,
                    screenH = secScreenH,
                    srcW = srcWidth,
                    srcH = srcHeight,
                )
        }
        updated
    }

    // Root key handler to reliably catch Controller B / Back button
    val rootKeyModifier =
        Modifier.onKeyEvent { keyEvent ->
            val keyCode = keyEvent.nativeKeyEvent.keyCode
            if (keyEvent.type == KeyEventType.KeyUp && isBackKey(keyCode)) {
                handleBackAction()
            } else {
                false
            }
        }

    CompositionLocalProvider(
        LocalBringIntoViewSpec provides bringIntoViewSpec,
        LocalFirstContentRequester provides firstItemFocusRequester,
    ) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .then(rootKeyModifier)
                    .background(Color.Transparent),
        ) {
            // ── 1. Live Crop Bounds & Scrim Background (Display 0) ────────────────
            if (selectedCutout != null) {
                CropSelectorOverlay(
                    cutoutId = selectedCutout.id,
                )
            }

            // ── 2. Docked Vertical Controller Toolbox with 2D Drag & Minimize ───
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            top = METO_TOOLBOX_PADDING_VERTICAL,
                            bottom = METO_TOOLBOX_PADDING_VERTICAL,
                        ),
            ) {
                val boxMaxHeight = constraints.maxHeight
                val boxMaxWidth = constraints.maxWidth
                var surfaceHeightPx by remember { mutableIntStateOf(0) }
                val maxOffsetX = (boxMaxWidth - with(density) { METO_TOOLBOX_WIDTH.toPx() }).coerceAtLeast(0f)

                var offsetX by remember {
                    mutableFloatStateOf(with(density) { METO_TOOLBOX_PADDING_START.toPx() })
                }
                var offsetY by remember {
                    mutableFloatStateOf(0f)
                }

                Surface(
                    modifier =
                        Modifier
                            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                            .width(METO_TOOLBOX_WIDTH)
                            .shadow(METO_CONTAINER_ELEVATION, RoundedCornerShape(METO_CONTAINER_CORNER))
                            .clip(RoundedCornerShape(METO_CONTAINER_CORNER))
                            .border(
                                width = METO_DEFAULT_BORDER_WIDTH,
                                brush = rememberBezelBrush(),
                                shape = RoundedCornerShape(METO_CONTAINER_CORNER),
                            ).animateContentSize(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            ).onSizeChanged { size ->
                                surfaceHeightPx = size.height
                                val currentMaxOffsetY = (boxMaxHeight - size.height).coerceAtLeast(0).toFloat()
                                if (offsetY > currentMaxOffsetY) {
                                    offsetY = currentMaxOffsetY
                                }
                            },
                    color = colors.surface.copy(alpha = METO_SURFACE_ALPHA),
                    shape = RoundedCornerShape(METO_CONTAINER_CORNER),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = METO_INNER_PADDING_V),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // ── Menu Content Area (Unified vertical scroll container) ──
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isMinimized) {
                                            Modifier.height(METO_CARD_MIN_HEIGHT)
                                        } else {
                                            Modifier
                                        },
                                    ).verticalScroll(rememberScrollState())
                                    .padding(horizontal = METO_INNER_PADDING_H),
                            verticalArrangement = Arrangement.spacedBy(METO_ITEM_SPACING),
                        ) {
                            // Item 0: Target Cutout Carousel Selector
                            TargetCutoutCarouselCard(
                                cutouts = cutouts,
                                selectedCutout = selectedCutout,
                                onSelectCutout = { id ->
                                    AppStateManager.setSelectedCutoutId(id)
                                },
                                cardFocusRequester = firstItemFocusRequester,
                                modifier =
                                    Modifier
                                        .firstDeckItem()
                                        .focusProperties {
                                            up = collapseButtonFocusRequester
                                        },
                            )

                            // Item 1: Fixed Aspect Ratio Mode
                            AspectRatioCard(
                                selectedCutout = selectedCutout,
                                srcWidth = srcWidth,
                                srcHeight = srcHeight,
                                secScreenW = secScreenW,
                                secScreenH = secScreenH,
                                onUpdate = { updatedCutout ->
                                    val updatedList =
                                        cutouts.map {
                                            if (it.id == updatedCutout.id) updatedCutout else it
                                        }
                                    MacroPadState.updateLayout(layout.copy(mirrorCutouts = updatedList))
                                },
                            )

                            // Item 2: Shape Mode
                            ShapeToggleCard(
                                selectedCutout = selectedCutout,
                                onUpdate = { updatedCutout ->
                                    val updatedList =
                                        cutouts.map {
                                            if (it.id == updatedCutout.id) updatedCutout else it
                                        }
                                    MacroPadState.updateLayout(layout.copy(mirrorCutouts = updatedList))
                                },
                            )

                            // Item 3: Adjust Top Cutout Coordinates (Source Screen)
                            AdjustCoordinatesCard(
                                title = stringResource(R.string.mirror_editor_adjust_top_cutout),
                                icon = Icons.Rounded.Crop,
                                selectedCutout = selectedCutout,
                                onMove = { dx, dy ->
                                    selectedCutout?.id?.let { moveTopCutout(it, dx, dy) }
                                },
                                onResize = { dx, dy ->
                                    selectedCutout?.id?.let { resizeTopCutout(it, dx, dy) }
                                },
                            )

                            // Item 4: Adjust Bottom Cutout Coordinates (Target Screen)
                            AdjustCoordinatesCard(
                                title = stringResource(R.string.mirror_editor_adjust_bottom_cutout),
                                icon = Icons.Rounded.OpenWith,
                                selectedCutout = selectedCutout,
                                onMove = { dx, dy ->
                                    selectedCutout?.id?.let { moveBottomCutout(it, dx, dy) }
                                },
                                onResize = { dx, dy ->
                                    selectedCutout?.id?.let { resizeBottomCutout(it, dx, dy) }
                                },
                            )

                            // Item 5: Temporarily Hide Background
                            HideBackgroundCard(
                                layout = layout,
                            )

                            // Item 6: Add Cutout
                            ToolboxActionCard(
                                title = stringResource(R.string.mirror_editor_add_cutout),
                                icon = Icons.Rounded.Add,
                                onClick = {
                                    val slot = CutoutPlacementHelper.findAvailableSlot(cutouts)
                                    if (slot == null) {
                                        DialogToastManager.show(context.getString(R.string.mirror_editor_no_space))
                                    } else {
                                        val newId = UUID.randomUUID().toString()
                                        val initialCutout =
                                            ScreenCutout(
                                                id = newId,
                                                name =
                                                    context.getString(
                                                        R.string.settings_mirror_cutout_default_name_fmt,
                                                        cutouts.size + 1,
                                                    ),
                                                srcX = 0.25f,
                                                srcY = 0.25f,
                                                srcWidth = 0.5f,
                                                srcHeight = 0.5f,
                                                destX = slot.destX,
                                                destY = slot.destY,
                                                destWidth = slot.destWidth,
                                                destHeight = slot.destHeight,
                                                aspectRatioMode = AspectRatioMode.BOTTOM,
                                            )
                                        val newCutout =
                                            adjustSourceCropToAspectRatio(
                                                cutout = initialCutout,
                                                screenW = secScreenW,
                                                screenH = secScreenH,
                                                srcW = srcWidth,
                                                srcH = srcHeight,
                                            )
                                        MacroPadState.updateLayout(layout.copy(mirrorCutouts = cutouts + newCutout))
                                        AppStateManager.setSelectedCutoutId(newId)
                                    }
                                },
                            )

                            // Item 7: Delete Cutout
                            DeleteCutoutCard(
                                selectedCutout = selectedCutout,
                                onDelete = { cutoutId ->
                                    val updatedList = cutouts.filterNot { it.id == cutoutId }
                                    MacroPadState.updateLayout(layout.copy(mirrorCutouts = updatedList))
                                    AppStateManager.setSelectedCutoutId(updatedList.firstOrNull()?.id)
                                },
                            )

                            // Item 8: Save Changes / Save & Discard Exit Row
                            ToolboxSaveExitRow(
                                showExitPrompt = showExitPrompt,
                                hasChanges = hasChanges,
                                saveFocusRequester = saveFocusRequester,
                                onSave = {
                                    if (showExitPrompt) {
                                        savedCutouts = currentCutouts
                                        MacroPadState.saveMirrorCutouts(layout.id, currentCutouts)
                                        onDone()
                                    } else {
                                        savedCutouts = currentCutouts
                                        MacroPadState.saveMirrorCutouts(layout.id, currentCutouts)
                                        DialogToastManager.show(context.getString(R.string.mirror_editor_saved_toast))
                                    }
                                },
                                onDiscard = {
                                    MacroPadState.updateLayout(layout.copy(mirrorCutouts = savedCutouts))
                                    onCancel()
                                },
                                onDismissPrompt = {
                                    showExitPrompt = false
                                },
                            )
                        }

                        // Bottom Drag Handle (Outside Scroll Container) with 2D Drag & Collapse Toggle
                        ToolboxDragHandle(
                            isMinimized = isMinimized,
                            onToggleMinimize = { isMinimized = !isMinimized },
                            onDrag = { dx, dy ->
                                val currentMaxOffsetY = (boxMaxHeight - surfaceHeightPx).coerceAtLeast(0).toFloat()
                                offsetX = (offsetX + dx).coerceIn(0f, maxOffsetX)
                                offsetY = (offsetY + dy).coerceIn(0f, currentMaxOffsetY)
                            },
                            toggleButtonFocusRequester = collapseButtonFocusRequester,
                            firstItemFocusRequester = firstItemFocusRequester,
                        )
                    }
                }
            }

            // ── 3. Toast Notifications (Display 0 Top) ───────────────────────────
            DialogToastPill(
                toast = activeToast,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-Components for the Vertical Toolbox
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bottom drag handle anchored at the bottom of the toolbox container for dragging the menu
 * in 2D across Display 0, housing the minimize / expand toggle button on its right flank.
 */
@Composable
private fun ToolboxDragHandle(
    isMinimized: Boolean,
    onToggleMinimize: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    toggleButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    firstItemFocusRequester: FocusRequester? = null,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val buttonBg by animateColorAsState(
        targetValue = if (isFocused) colors.accent.copy(alpha = 0.25f) else colors.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(150),
        label = "toggleButtonBg",
    )
    val buttonBorderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accent else Color.Transparent,
        animationSpec = tween(150),
        label = "toggleButtonBorder",
    )
    val iconTint by animateColorAsState(
        targetValue = if (isFocused) colors.accent else colors.onSurfaceSecondary,
        animationSpec = tween(150),
        label = "toggleButtonIconTint",
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }.padding(
                    start = METO_INNER_PADDING_H,
                    end = METO_INNER_PADDING_H,
                    top = METO_HANDLE_V_PADDING_TOP,
                    bottom = METO_HANDLE_V_PADDING_BOTTOM,
                ),
        contentAlignment = Alignment.Center,
    ) {
        // Centered Capsule Drag Handle
        Box(
            modifier =
                Modifier
                    .width(METO_HANDLE_WIDTH)
                    .height(METO_HANDLE_HEIGHT)
                    .clip(RoundedCornerShape(50))
                    .background(colors.onSurfaceSecondary.copy(alpha = 0.4f)),
        )

        // Minimize / Expand Toggle Button anchored to the right
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(METO_TOGGLE_BUTTON_SIZE)
                    .clip(CircleShape)
                    .background(buttonBg)
                    .border(METO_DEFAULT_BORDER_WIDTH, buttonBorderColor, CircleShape)
                    .focusRequester(toggleButtonFocusRequester)
                    .then(
                        if (firstItemFocusRequester != null) {
                            Modifier.focusProperties {
                                down = firstItemFocusRequester
                            }
                        } else {
                            Modifier
                        },
                    ).onKeyEvent { keyEvent ->
                        val keyCode = keyEvent.nativeKeyEvent.keyCode
                        if (keyEvent.type == KeyEventType.KeyUp &&
                            (
                                keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                    keyCode == KeyEvent.KEYCODE_ENTER
                            )
                        ) {
                            onToggleMinimize()
                            true
                        } else {
                            false
                        }
                    }.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onToggleMinimize,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isMinimized) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess,
                contentDescription = stringResource(if (isMinimized) R.string.mirror_editor_expand else R.string.mirror_editor_minimize),
                tint = iconTint,
                modifier = Modifier.size(METO_TOGGLE_ICON_SIZE),
            )
        }
    }
}

/**
 * Base focusable card container scaled proportionally 25% smaller than default primary overlay cards.
 */
@Composable
private fun ToolboxCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    enabled: Boolean = true,
    isFocusedOverride: Boolean = false,
    isDestructive: Boolean = false,
    cardBgColor: Color? = null,
    onLeftKey: (() -> Unit)? = null,
    onRightKey: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onCustomKeyEvent: ((ComposeKeyEvent) -> Boolean)? = null,
    icon: ImageVector,
    title: String,
    trailingContent: (@Composable (isFocused: Boolean) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val effectivelyFocused = isFocused || isFocusedOverride

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (effectivelyFocused) METO_FOCUS_BORDER_WIDTH else METO_DEFAULT_BORDER_WIDTH,
        animationSpec = tween(150),
        label = "cardBorderWidth",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (effectivelyFocused) (if (isDestructive) colors.error else colors.accent) else colors.subduedBorder,
        animationSpec = tween(150),
        label = "cardBorderColor",
    )
    val animatedBgColor =
        if (cardBgColor != null) {
            cardBgColor
        } else {
            val targetBg =
                if (effectivelyFocused) {
                    colors.surface.copy(alpha = 0.90f)
                } else {
                    colors.surface.copy(alpha = 0.40f)
                }
            val bg by animateColorAsState(
                targetValue = targetBg,
                animationSpec = tween(150),
                label = "cardBgColor",
            )
            bg
        }
    val animatedElevation by animateDpAsState(
        targetValue = if (effectivelyFocused) METO_FOCUS_ELEVATION else 0.dp,
        animationSpec = tween(150),
        label = "cardElevation",
    )

    var lastCustomConsumedDownKeyCode by remember { mutableIntStateOf(0) }

    val keyModifier =
        Modifier.onKeyEvent { keyEvent ->
            val keyCode = keyEvent.nativeKeyEvent.keyCode
            if (keyEvent.type == KeyEventType.KeyDown) {
                if (onCustomKeyEvent != null && onCustomKeyEvent(keyEvent)) {
                    lastCustomConsumedDownKeyCode = keyCode
                    return@onKeyEvent true
                }
                lastCustomConsumedDownKeyCode = 0
            } else if (keyEvent.type == KeyEventType.KeyUp) {
                if (lastCustomConsumedDownKeyCode == keyCode && keyCode != 0) {
                    lastCustomConsumedDownKeyCode = 0
                    onCustomKeyEvent?.invoke(keyEvent)
                    return@onKeyEvent true
                }
                if (onCustomKeyEvent != null && onCustomKeyEvent(keyEvent)) {
                    return@onKeyEvent true
                }
            }

            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (keyEvent.type == KeyEventType.KeyUp && enabled) {
                        onClick()
                    }
                    true
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (keyEvent.type == KeyEventType.KeyUp && enabled && onLeftKey != null) {
                        onLeftKey()
                        true
                    } else {
                        false
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (keyEvent.type == KeyEventType.KeyUp && enabled && onRightKey != null) {
                        onRightKey()
                        true
                    } else {
                        false
                    }
                }

                else -> {
                    false
                }
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = METO_CARD_MIN_HEIGHT)
                .graphicsLayer {
                    this.shadowElevation = animatedElevation.toPx()
                    this.shape = RoundedCornerShape(METO_CARD_CORNER)
                    this.clip = false
                }.drawBehind {
                    val outline = RoundedCornerShape(METO_CARD_CORNER).createOutline(size, layoutDirection, this)
                    drawOutline(
                        outline = outline,
                        brush = SolidColor(animatedBgColor),
                        style = Fill,
                    )
                    drawOutline(
                        outline = outline,
                        brush = SolidColor(animatedBorderColor),
                        style = Stroke(width = animatedBorderWidth.toPx()),
                    )
                }.focusRequester(cardFocusRequester)
                .onFocusChanged { state ->
                    onFocusChanged?.invoke(state.isFocused)
                }.then(keyModifier)
                .focusable(enabled = enabled, interactionSource = interactionSource)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = METO_CARD_PADDING_H, vertical = METO_CARD_PADDING_V),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconBg =
                when {
                    isDestructive -> colors.error.copy(alpha = 0.15f)
                    effectivelyFocused -> colors.accent.copy(alpha = 0.15f)
                    else -> colors.surfaceVariant
                }
            val iconTint =
                when {
                    isDestructive -> colors.error
                    effectivelyFocused -> colors.accent
                    else -> colors.onSurfaceSecondary
                }

            Box(
                modifier =
                    Modifier
                        .size(METO_ICON_BOX_SIZE)
                        .clip(RoundedCornerShape(METO_ICON_BOX_CORNER))
                        .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(METO_ICON_SIZE),
                )
            }

            Spacer(modifier = Modifier.width(METO_ROW_SPACING))

            Text(
                text = title,
                color =
                    if (isDestructive) {
                        colors.error
                    } else if (enabled) {
                        colors.onSurface
                    } else {
                        colors.onSurfaceSecondary.copy(alpha = 0.4f)
                    },
                fontSize = METO_TEXT_SIZE_TITLE,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(METO_ROW_SPACING))
                trailingContent(effectivelyFocused)
            }
        }
    }
}

@Composable
private fun TargetCutoutCarouselCard(
    cutouts: List<ScreenCutout>,
    selectedCutout: ScreenCutout?,
    onSelectCutout: (String) -> Unit,
    modifier: Modifier = Modifier,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    var isAdjusting by remember { mutableStateOf(false) }
    val currentIdx = if (selectedCutout != null) cutouts.indexOfFirst { it.id == selectedCutout.id } else -1
    val hasCutouts = cutouts.isNotEmpty()

    LaunchedEffect(hasCutouts) {
        if (!hasCutouts && isAdjusting) {
            isAdjusting = false
        }
    }

    val titleText =
        if (!hasCutouts) {
            stringResource(R.string.mirror_editor_no_cutouts)
        } else {
            selectedCutout?.name?.ifBlank { "Cutout ${currentIdx + 1}" } ?: "Cutout ${currentIdx + 1}"
        }

    val readoutText = if (hasCutouts) "${currentIdx + 1}/${cutouts.size}" else "-"

    fun selectPrevious() {
        if (!hasCutouts) return
        selectedCutout?.let { onSelectCutout(cutouts.cycle(it, BumperDirection.PREV).id) }
    }

    fun selectNext() {
        if (!hasCutouts) return
        selectedCutout?.let { onSelectCutout(cutouts.cycle(it, BumperDirection.NEXT).id) }
    }

    ToolboxCard(
        onClick = {
            if (hasCutouts) {
                val nextState = !isAdjusting
                AppLog.d(TAG, "TargetCutoutCarouselCard: adjustment mode=$nextState")
                isAdjusting = nextState
            }
        },
        isFocusedOverride = isAdjusting,
        onCustomKeyEvent = { keyEvent ->
            handleAdjustmentKeyEvent(
                keyEvent = keyEvent,
                isAdjusting = isAdjusting,
                onAdjustLeft = { selectPrevious() },
                onAdjustRight = { selectNext() },
                onDismissAdjustment = { isAdjusting = false },
            )
        },
        onFocusChanged = { focused ->
            if (!focused) {
                isAdjusting = false
            }
            onFocusChanged?.invoke(focused)
        },
        cardFocusRequester = cardFocusRequester,
        enabled = hasCutouts,
        icon = Icons.Rounded.FilterCenterFocus,
        title = titleText,
        modifier = modifier,
    ) { isFocused ->
        val capsuleBorderColor = if (isAdjusting) colors.accent else colors.subduedBorder
        val capsuleBorderWidth = if (isAdjusting) 1.5.dp else 1.dp
        val capsuleBg = if (isAdjusting) colors.accent.copy(alpha = 0.15f) else colors.surfaceVariant
        val arrowTint = if (isAdjusting || isFocused) colors.accent else colors.onSurfaceSecondary

        Row(
            modifier =
                Modifier
                    .background(capsuleBg, RoundedCornerShape(METO_PILL_CORNER))
                    .border(capsuleBorderWidth, capsuleBorderColor, RoundedCornerShape(METO_PILL_CORNER))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable(enabled = hasCutouts) { selectPrevious() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.gamepad_previous),
                    tint = arrowTint,
                    modifier = Modifier.size(14.dp),
                )
            }

            Text(
                text = readoutText,
                color = if (isAdjusting) colors.accent else colors.onSurface,
                fontSize = METO_TEXT_SIZE_PILL,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable(enabled = hasCutouts) { selectNext() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.gamepad_next),
                    tint = arrowTint,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun AspectRatioCard(
    selectedCutout: ScreenCutout?,
    srcWidth: Float,
    srcHeight: Float,
    secScreenW: Float,
    secScreenH: Float,
    onUpdate: (ScreenCutout) -> Unit,
    modifier: Modifier = Modifier,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val currentMode = selectedCutout?.aspectRatioMode ?: AspectRatioMode.FREE
    val enabled = selectedCutout != null

    val modeLabel =
        when (currentMode) {
            AspectRatioMode.FREE -> stringResource(R.string.mirror_editor_aspect_ratio_free)
            AspectRatioMode.TOP -> stringResource(R.string.mirror_editor_aspect_ratio_top)
            AspectRatioMode.BOTTOM -> stringResource(R.string.mirror_editor_aspect_ratio_bottom)
        }

    fun cycleMode(forward: Boolean) {
        val cutout = selectedCutout ?: return
        val modes = AspectRatioMode.entries
        val nextMode = modes.cycle(cutout.aspectRatioMode, if (forward) BumperDirection.NEXT else BumperDirection.PREV)

        var updatedCutout =
            cutout.copy(
                aspectRatioMode = nextMode,
                keepAspectRatio = (nextMode == AspectRatioMode.TOP),
            )

        if (nextMode == AspectRatioMode.TOP) {
            val cropRatio = (updatedCutout.srcWidth * srcWidth) / (updatedCutout.srcHeight * srcHeight)
            val (newDestW, newDestH) =
                adjustDestSizeToAspectRatio(
                    destX = updatedCutout.destX,
                    destY = updatedCutout.destY,
                    destWidth = updatedCutout.destWidth,
                    destHeight = updatedCutout.destHeight,
                    cropRatio = cropRatio,
                    screenW = secScreenW,
                    screenH = secScreenH,
                )
            updatedCutout = updatedCutout.copy(destWidth = newDestW, destHeight = newDestH)
        } else if (nextMode == AspectRatioMode.BOTTOM) {
            updatedCutout =
                adjustSourceCropToAspectRatio(
                    cutout = updatedCutout,
                    screenW = secScreenW,
                    screenH = secScreenH,
                    srcW = srcWidth,
                    srcH = srcHeight,
                )
        }

        onUpdate(updatedCutout)
    }

    ToolboxCard(
        onClick = { cycleMode(forward = true) },
        onLeftKey = { cycleMode(forward = false) },
        onRightKey = { cycleMode(forward = true) },
        onFocusChanged = onFocusChanged,
        cardFocusRequester = cardFocusRequester,
        enabled = enabled,
        icon = Icons.Rounded.AspectRatio,
        title = stringResource(R.string.mirror_editor_aspect_ratio_mode),
        modifier = modifier,
    ) { isFocused ->
        GamepadPill(
            text = modeLabel,
            isHighlighted = isFocused,
        )
    }
}

@Composable
private fun ShapeToggleCard(
    selectedCutout: ScreenCutout?,
    onUpdate: (ScreenCutout) -> Unit,
    modifier: Modifier = Modifier,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val isCircle = selectedCutout?.shape == CutoutShape.CIRCLE
    val enabled = selectedCutout != null

    val shapeLabel =
        if (isCircle) {
            stringResource(R.string.mirror_editor_toolbar_shape_circle)
        } else {
            stringResource(R.string.mirror_editor_toolbar_shape_rect)
        }

    fun toggleShape() {
        val cutout = selectedCutout ?: return
        val nextShape = if (cutout.shape == CutoutShape.CIRCLE) CutoutShape.RECTANGLE else CutoutShape.CIRCLE
        onUpdate(cutout.copy(shape = nextShape))
    }

    ToolboxCard(
        onClick = { toggleShape() },
        onFocusChanged = onFocusChanged,
        cardFocusRequester = cardFocusRequester,
        enabled = enabled,
        icon = if (isCircle) Icons.Rounded.Circle else Icons.Rounded.CropSquare,
        title = stringResource(R.string.mirror_editor_shape_mode),
        modifier = modifier,
    ) { isFocused ->
        GamepadPill(
            text = shapeLabel,
            isHighlighted = isFocused,
        )
    }
}

@Composable
private fun AdjustCoordinatesCard(
    title: String,
    icon: ImageVector,
    selectedCutout: ScreenCutout?,
    onMove: (Int, Int) -> Unit,
    onResize: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnResize by rememberUpdatedState(onResize)
    var isAdjusting by remember { mutableStateOf(false) }
    var isR2Held by remember { mutableStateOf(false) }
    var isL2Held by remember { mutableStateOf(false) }
    val isR2HeldState = rememberUpdatedState(isR2Held)
    val isL2HeldState = rememberUpdatedState(isL2Held)
    val enabled = selectedCutout != null

    var activeDirectionKey by remember { mutableIntStateOf(0) }
    var activeRepeatJob by remember { mutableStateOf<Job?>(null) }

    fun stopAdjustingImmediate() {
        activeRepeatJob?.cancel()
        activeRepeatJob = null
        activeDirectionKey = 0
        isR2Held = false
        isL2Held = false
    }

    fun dispatchAction(
        dirX: Int,
        dirY: Int,
    ) {
        val stepSize = if (isL2HeldState.value) METO_FINE_STEP_PX else METO_NORMAL_STEP_PX
        val dx = dirX * stepSize
        val dy = dirY * stepSize
        if (isR2HeldState.value) {
            currentOnResize(dx, dy)
        } else {
            currentOnMove(dx, dy)
        }
    }

    fun startAdjusting(
        keyCode: Int,
        dirX: Int,
        dirY: Int,
    ) {
        if (activeDirectionKey == keyCode && activeRepeatJob?.isActive == true) return
        activeRepeatJob?.cancel()
        activeDirectionKey = keyCode
        dispatchAction(dirX, dirY)
        activeRepeatJob =
            coroutineScope.launchDirectionalRepeat(
                keyCode = keyCode,
                isActiveCheck = { activeDirectionKey == keyCode },
            ) {
                dispatchAction(dirX, dirY)
            }
    }

    fun stopAdjusting(keyCode: Int) {
        if (activeDirectionKey == keyCode) {
            activeRepeatJob?.cancel()
            activeRepeatJob = null
            activeDirectionKey = 0
        }
    }

    LaunchedEffect(selectedCutout?.id) {
        if (isAdjusting) {
            stopAdjustingImmediate()
            isAdjusting = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAdjustingImmediate()
        }
    }

    ToolboxCard(
        onClick = {
            if (isAdjusting) {
                stopAdjustingImmediate()
                isAdjusting = false
            } else {
                isAdjusting = true
                isR2Held = false
                isL2Held = false
                DialogToastManager.show(
                    message = context.getString(R.string.mirror_editor_adjust_toast),
                    durationMs = METO_ADJUST_TOAST_DURATION_MS,
                    icon = icon,
                )
            }
        },
        isFocusedOverride = isAdjusting,
        enabled = enabled,
        cardFocusRequester = cardFocusRequester,
        onFocusChanged = { focused ->
            if (!focused && isAdjusting) {
                stopAdjustingImmediate()
                isAdjusting = false
            }
            onFocusChanged?.invoke(focused)
        },
        cardBgColor = if (isAdjusting) colors.accent.copy(alpha = 0.25f) else null,
        icon = icon,
        title = title,
        onCustomKeyEvent = { event ->
            handle2DAdjustmentKeyEvent(
                keyEvent = event,
                isAdjusting = isAdjusting,
                onStartAdjusting = { keyCode, dirX, dirY -> startAdjusting(keyCode, dirX, dirY) },
                onStopAdjusting = { keyCode -> stopAdjusting(keyCode) },
                onDismissAdjustment = {
                    stopAdjustingImmediate()
                    isAdjusting = false
                },
                onModifierKeyDown = { keyCode ->
                    when (keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L2 -> {
                            isL2Held = true
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_R2 -> {
                            isR2Held = true
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
                onModifierKeyUp = { keyCode ->
                    when (keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L2 -> {
                            isL2Held = false
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_R2 -> {
                            isR2Held = false
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun DeleteCutoutCard(
    selectedCutout: ScreenCutout?,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    var isConfirming by remember { mutableStateOf(false) }
    val enabled = selectedCutout != null

    LaunchedEffect(selectedCutout?.id) {
        isConfirming = false
    }

    ToolboxCard(
        onClick = {
            val cutoutId = selectedCutout?.id ?: return@ToolboxCard
            if (isConfirming) {
                onDelete(cutoutId)
                isConfirming = false
            } else {
                isConfirming = true
            }
        },
        onFocusChanged = onFocusChanged,
        onCustomKeyEvent = { event ->
            if (isConfirming && isBackKey(event.nativeKeyEvent.keyCode)) {
                if (event.type == KeyEventType.KeyUp) {
                    isConfirming = false
                }
                true
            } else {
                false
            }
        },
        icon = Icons.Rounded.Delete,
        title =
            if (isConfirming) {
                stringResource(
                    R.string.gamepad_action_confirm,
                )
            } else {
                stringResource(R.string.macropad_delete_cutout_title)
            },
        isDestructive = true,
        enabled = enabled,
        cardFocusRequester = cardFocusRequester,
        modifier = modifier,
    )
}

@Composable
private fun HideBackgroundCard(
    layout: PadLayout?,
    modifier: Modifier = Modifier,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val isHidden by AppStateManager.isMirrorEditorBackgroundHidden.collectAsState()
    val hasBackground = !layout?.backgroundImagePath.isNullOrEmpty()

    val label =
        if (!hasBackground) {
            stringResource(R.string.mirror_editor_bg_none)
        } else if (isHidden) {
            stringResource(R.string.mirror_editor_bg_hidden)
        } else {
            stringResource(R.string.mirror_editor_bg_visible)
        }

    fun toggle() {
        if (!hasBackground) return
        AppStateManager.toggleMirrorEditorBackgroundHidden()
    }

    ToolboxCard(
        onClick = { toggle() },
        onLeftKey = { if (hasBackground && isHidden) AppStateManager.setMirrorEditorBackgroundHidden(false) },
        onRightKey = { if (hasBackground && !isHidden) AppStateManager.setMirrorEditorBackgroundHidden(true) },
        onFocusChanged = onFocusChanged,
        cardFocusRequester = cardFocusRequester,
        enabled = hasBackground,
        icon = if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
        title = stringResource(R.string.mirror_editor_hide_background),
        modifier = modifier,
    ) { isFocused ->
        GamepadPill(
            text = label,
            isHighlighted = isFocused && hasBackground,
        )
    }
}

@Composable
private fun ToolboxActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionBadge: String? = null,
    cardFocusRequester: FocusRequester = remember { FocusRequester() },
    isAccent: Boolean = false,
    isDestructive: Boolean = false,
    cardBgColor: Color? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    ToolboxCard(
        onClick = onClick,
        icon = icon,
        title = title,
        isDestructive = isDestructive,
        cardBgColor = cardBgColor,
        cardFocusRequester = cardFocusRequester,
        onFocusChanged = onFocusChanged,
        modifier = modifier,
        trailingContent =
            if (actionBadge != null) {
                { isFocused ->
                    GamepadPill(
                        text = actionBadge,
                        isAccent = isAccent,
                        isDestructive = isDestructive,
                        isHighlighted = isFocused,
                    )
                }
            } else {
                null
            },
    )
}

@Composable
private fun ToolboxSaveExitRow(
    showExitPrompt: Boolean,
    hasChanges: Boolean,
    saveFocusRequester: FocusRequester,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismissPrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var isSaveFocused by remember { mutableStateOf(false) }
    var isDiscardFocused by remember { mutableStateOf(false) }
    val isRowFocused = isSaveFocused || isDiscardFocused

    LaunchedEffect(isRowFocused, showExitPrompt) {
        if (showExitPrompt && !isRowFocused) {
            delay(100)
            if (showExitPrompt && !isSaveFocused && !isDiscardFocused) {
                AppLog.d(TAG, "ToolboxSaveExitRow focus settled outside -> dismissing prompt")
                onDismissPrompt()
            }
        }
    }

    val splitFraction by animateFloatAsState(
        targetValue = if (showExitPrompt) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "toolboxSaveExitSplitFraction",
    )

    val isPromptActive = showExitPrompt || splitFraction > 0.05f
    val effectiveTitle =
        if (isPromptActive) {
            stringResource(R.string.gamepad_action_save)
        } else {
            stringResource(R.string.mirror_editor_save_changes)
        }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        val totalWidth = maxWidth
        val targetCardWidth = ((totalWidth - METO_ITEM_SPACING) / 2f).coerceAtLeast(0.dp)
        val currentSpacing = METO_ITEM_SPACING * splitFraction
        val card2VisibleWidth = targetCardWidth * splitFraction
        val card1VisibleWidth =
            (
                totalWidth - (
                    if (splitFraction > 0.001f) {
                        card2VisibleWidth + currentSpacing
                    } else {
                        0.dp
                    }
                )
            ).coerceAtLeast(0.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Card 1: Save Changes / Save & Exit (Persistent! Never unmounts!)
            ToolboxActionCard(
                title = effectiveTitle,
                icon = Icons.Rounded.Save,
                actionBadge = if (!isPromptActive && !hasChanges) stringResource(R.string.mirror_editor_saved_badge) else null,
                isAccent = true,
                cardBgColor = if (hasChanges) colors.accent.copy(alpha = 0.20f) else null,
                cardFocusRequester = saveFocusRequester,
                onFocusChanged = { isSaveFocused = it },
                onClick = onSave,
                modifier = Modifier.width(card1VisibleWidth),
            )

            if (splitFraction > 0.001f) {
                Spacer(modifier = Modifier.width(currentSpacing))

                Box(
                    modifier =
                        Modifier
                            .width(card2VisibleWidth)
                            .clipToBounds()
                            .graphicsLayer {
                                alpha = splitFraction
                            },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    ToolboxActionCard(
                        title = stringResource(R.string.gamepad_action_discard),
                        icon = Icons.Rounded.Close,
                        isDestructive = true,
                        cardBgColor = colors.error.copy(alpha = 0.15f),
                        onClick = onDiscard,
                        onFocusChanged = { isDiscardFocused = it },
                        modifier = Modifier.requiredWidth(targetCardWidth),
                    )
                }
            }
        }
    }
}
