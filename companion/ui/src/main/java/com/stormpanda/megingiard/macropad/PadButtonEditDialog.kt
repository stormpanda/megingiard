package com.stormpanda.megingiard.macropad

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.math.DesignViewport
import com.stormpanda.megingiard.math.MPE_FINE_STEP_PX
import com.stormpanda.megingiard.math.MPE_NORMAL_STEP_PX
import com.stormpanda.megingiard.math.MPE_RESIZE_FINE_STEP_PX
import com.stormpanda.megingiard.math.MPE_RESIZE_NORMAL_STEP_PX
import com.stormpanda.megingiard.math.ViewportMath
import com.stormpanda.megingiard.math.calculateGamepadButtonMove
import com.stormpanda.megingiard.math.calculateGamepadButtonResize
import com.stormpanda.megingiard.math.formatButtonDimensionPx
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCardRow
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadPill
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadTextFieldCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoColumnGrid
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.MaterialSymbol
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.handle2DAdjustmentKeyEvent
import com.stormpanda.megingiard.ui.launchDirectionalRepeat
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState
import com.stormpanda.megingiard.ui.toHexLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import java.util.UUID
import kotlin.math.roundToInt
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

private const val TAG = "PadButtonEditDialog"

private const val PBD_DEFAULT_CENTER_POS = 0.5f
private const val PBD_PERCENT_MULTIPLIER = 100f
private const val PBD_DURATION_STEP_MS = 10
private const val PBD_DURATION_MIN_MS = 10
private const val PBD_DURATION_MAX_MS = 200
private const val PBD_AMPLITUDE_STEP = 5
private const val PBD_AMPLITUDE_MIN = 5
private const val PBD_AMPLITUDE_MAX = 100
private val PBD_COLOR_PREVIEW_SIZE = 36.dp
private val PBD_ICON_SIZE_DP = 20.dp
private val PBD_PICKER_ICON_SIZE = 24.dp

private data class ButtonColorCardEntry(
    val target: EditorColorTarget,
    val icon: ImageVector,
    val preview: @Composable () -> Unit,
    val description: String,
)

private data class ButtonTouchRecordingObservation(
    val recordedTap: Pair<Float, Float>?,
    val recordedGesture: List<TouchSample>?,
    val awaitingTap: Boolean,
    val awaitingGesture: Boolean,
    val recordingRequested: Boolean,
    val isIdle: Boolean,
)

internal class Button2DAdjusterState(
    private val coroutineScope: CoroutineScope,
    private val resolveButton: (String) -> PadButton?,
    private val onButtonChange: (PadButton) -> Unit,
    private val resolveDesignCanvas: () -> LayoutCanvasSpec,
    private val resolveHostViewport: () -> DesignViewport?,
    private val resolveSnapEnabled: () -> Boolean,
) {
    var isR2Held by mutableStateOf(false)
        private set
    private var isL2Held by mutableStateOf(false)
    private var activeDirectionKey by mutableIntStateOf(0)
    private var activeRepeatJob by mutableStateOf<Job?>(null)

    fun stopMovingImmediate() {
        activeRepeatJob?.cancel()
        activeRepeatJob = null
        activeDirectionKey = 0
        isL2Held = false
        isR2Held = false
    }

    private fun moveButton(
        btnId: String,
        dx: Int,
        dy: Int,
    ) {
        val targetBtn = resolveButton(btnId) ?: return
        val otherButtons = MacroPadState.activeLayout.value?.buttons ?: emptyList()
        val designCanvas = resolveDesignCanvas()
        val hostViewport = resolveHostViewport()
        val minNormX = hostViewport?.minNormX ?: 0f
        val maxNormX = hostViewport?.maxNormX ?: 1f
        val minNormY = hostViewport?.minNormY ?: 0f
        val maxNormY = hostViewport?.maxNormY ?: 1f
        val stepMultiplier = if (isL2Held) MPE_FINE_STEP_PX else MPE_NORMAL_STEP_PX
        val (newX, newY) =
            calculateGamepadButtonMove(
                currentNormX = targetBtn.posX,
                currentNormY = targetBtn.posY,
                dirX = dx,
                dirY = dy,
                stepMultiplierPx = stepMultiplier,
                movingButtonId = btnId,
                otherButtons = otherButtons,
                canvasW = designCanvas.widthPx.toFloat(),
                canvasH = designCanvas.heightPx.toFloat(),
                alignmentSnappingEnabled = resolveSnapEnabled(),
                minNormX = minNormX,
                maxNormX = maxNormX,
                minNormY = minNormY,
                maxNormY = maxNormY,
            )
        if (newX != targetBtn.posX || newY != targetBtn.posY) {
            onButtonChange(targetBtn.copy(posX = newX, posY = newY))
        }
    }

    private fun resizeButton(
        btnId: String,
        dx: Int,
        dy: Int,
    ) {
        val targetBtn = resolveButton(btnId) ?: return
        val designCanvas = resolveDesignCanvas()
        val hostViewport = resolveHostViewport()
        val minNormX = hostViewport?.minNormX ?: 0f
        val maxNormX = hostViewport?.maxNormX ?: 1f
        val minNormY = hostViewport?.minNormY ?: 0f
        val maxNormY = hostViewport?.maxNormY ?: 1f
        val stepPx = if (isL2Held) MPE_RESIZE_FINE_STEP_PX else MPE_RESIZE_NORMAL_STEP_PX
        val isTrackpoint = targetBtn.action is PadAction.TrackpointMove
        val minWidthPx = designCanvas.minButtonSizePx
        val minHeightPx =
            if (targetBtn.action is PadAction.ScrollWheel) {
                designCanvas.largeControlMinSizePx
            } else {
                designCanvas.minButtonSizePx
            }
        val res =
            calculateGamepadButtonResize(
                currentNormX = targetBtn.posX,
                currentNormY = targetBtn.posY,
                currentWidthPx = targetBtn.widthPx,
                currentHeightPx = targetBtn.heightPx,
                dirX = dx,
                dirY = dy,
                stepPx = stepPx,
                designCanvasW = designCanvas.widthPx.toFloat(),
                designCanvasH = designCanvas.heightPx.toFloat(),
                minWidthPx = minWidthPx,
                minHeightPx = minHeightPx,
                lockAspect = isTrackpoint,
                minNormX = minNormX,
                maxNormX = maxNormX,
                minNormY = minNormY,
                maxNormY = maxNormY,
            )
        if (res.newWidthPx != targetBtn.widthPx || res.newHeightPx != targetBtn.heightPx ||
            res.newNormX != targetBtn.posX || res.newNormY != targetBtn.posY
        ) {
            onButtonChange(
                targetBtn.copy(
                    posX = res.newNormX,
                    posY = res.newNormY,
                    widthPx = res.newWidthPx,
                    heightPx = res.newHeightPx,
                ),
            )
        }
    }

    private fun dispatchAdjust(
        btnId: String,
        dx: Int,
        dy: Int,
    ) {
        if (isR2Held) {
            resizeButton(btnId, dx, dy)
        } else {
            moveButton(btnId, dx, dy)
        }
    }

    private fun startMoving(
        btnId: String,
        keyCode: Int,
        dx: Int,
        dy: Int,
    ) {
        if (activeDirectionKey == keyCode && activeRepeatJob?.isActive == true) return
        activeRepeatJob?.cancel()
        activeDirectionKey = keyCode
        dispatchAdjust(btnId, dx, dy)
        activeRepeatJob =
            coroutineScope.launchDirectionalRepeat(
                keyCode = keyCode,
                isActiveCheck = { activeDirectionKey == keyCode },
            ) {
                dispatchAdjust(btnId, dx, dy)
            }
    }

    private fun stopMoving(keyCode: Int) {
        if (activeDirectionKey == keyCode) {
            activeRepeatJob?.cancel()
            activeRepeatJob = null
            activeDirectionKey = 0
        }
    }

    fun handleKeyEvent(
        keyEvent: ComposeKeyEvent,
        buttonId: String,
        isAdjusting: Boolean,
        onDismiss: () -> Unit,
    ): Boolean =
        handle2DAdjustmentKeyEvent(
            keyEvent = keyEvent,
            isAdjusting = isAdjusting,
            onStartAdjusting = { keyCode, dirX, dirY -> startMoving(buttonId, keyCode, dirX, dirY) },
            onStopAdjusting = { keyCode -> stopMoving(keyCode) },
            onDismissAdjustment = {
                stopMovingImmediate()
                onDismiss()
            },
            onModifierKeyDown = { keyCode ->
                when (keyCode) {
                    KeyEvent.KEYCODE_BUTTON_L2 -> {
                        isL2Held = true
                        true
                    }

                    KeyEvent.KEYCODE_BUTTON_R2 -> {
                        if (!isR2Held) AppLog.d(TAG, "gamepad resize modifier active (R2)")
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
                        if (isR2Held) AppLog.d(TAG, "gamepad resize modifier released (R2)")
                        isR2Held = false
                        true
                    }

                    else -> {
                        false
                    }
                }
            },
        )
}

@Composable
internal fun rememberButton2DAdjusterState(
    resolveButton: (String) -> PadButton?,
    onButtonChange: (PadButton) -> Unit,
): Button2DAdjusterState {
    val coroutineScope = rememberCoroutineScope()
    val buttonAlignmentSnapping by MacroPadSettings.buttonAlignmentSnapping.collectAsStateWithLifecycle()
    val activeLayout by MacroPadState.activeLayout.collectAsStateWithLifecycle()
    val hostSecondaryCanvas by MacroPadState.hostSecondaryCanvas.collectAsStateWithLifecycle()
    val designCanvas = activeLayout?.effectiveDesignCanvas ?: THOR_BOTTOM_CANVAS_SPEC
    val hostViewport =
        remember(hostSecondaryCanvas, designCanvas) {
            hostSecondaryCanvas
                ?.takeIf { it.widthPx > 0 && it.heightPx > 0 }
                ?.let { host ->
                    ViewportMath.calculateDesignViewport(
                        hostWidthPx = host.widthPx.toFloat(),
                        hostHeightPx = host.heightPx.toFloat(),
                        designWidthPx = designCanvas.widthPx.toFloat(),
                        designHeightPx = designCanvas.heightPx.toFloat(),
                    )
                }
        }

    val resolveButtonState = rememberUpdatedState(resolveButton)
    val onButtonChangeState = rememberUpdatedState(onButtonChange)
    val designCanvasState = rememberUpdatedState(designCanvas)
    val hostViewportState = rememberUpdatedState(hostViewport)
    val snapState = rememberUpdatedState(buttonAlignmentSnapping)

    val state =
        remember(coroutineScope) {
            Button2DAdjusterState(
                coroutineScope = coroutineScope,
                resolveButton = { id -> resolveButtonState.value(id) },
                onButtonChange = { btn -> onButtonChangeState.value(btn) },
                resolveDesignCanvas = { designCanvasState.value },
                resolveHostViewport = { hostViewportState.value },
                resolveSnapEnabled = { snapState.value },
            )
        }

    DisposableEffect(state) {
        onDispose {
            state.stopMovingImmediate()
        }
    }

    return state
}

@Composable
internal fun Button2DAdjusterPill(
    isAdjusting: Boolean,
    isR2Held: Boolean,
    isFocused: Boolean,
) {
    if (isAdjusting) {
        GamepadPill(
            text =
                if (isR2Held) {
                    stringResource(R.string.gamepad_action_resizing)
                } else {
                    stringResource(R.string.gamepad_action_moving)
                },
            isAccent = true,
        )
    } else {
        GamepadPill(
            text = stringResource(R.string.gamepad_action_move),
            isHighlighted = isFocused,
        )
    }
}

@Composable
internal fun describeButtonColorOption(
    option: ColorOption?,
    resolvedColor: Color,
): String =
    when (option) {
        null -> stringResource(R.string.layout_settings_color_layout_default)
        is ColorOption.Neutral -> stringResource(R.string.layout_settings_color_neutral)
        is ColorOption.Accent -> stringResource(R.string.layout_settings_color_accent)
        is ColorOption.Custom -> resolvedColor.toHexLabel()
    }

/**
 * Deck sub-page allowing the user to select the Button Type (ActionGroup) before editing button details.
 */
@Composable
internal fun ChooseButtonTypeSubPageContent(onSelectType: (ActionGroup) -> Unit) {
    val profile by MacroPadState.activeProfile.collectAsStateWithLifecycle()
    val hasMacros = profile?.macros?.isNotEmpty() == true
    val availableGroups =
        remember(hasMacros) {
            ActionGroup.entries.filter { group ->
                group.actions().any { category ->
                    category.isAvailable(hasMacros)
                }
            }
        }

    GamepadTwoColumnGrid(
        items = availableGroups,
    ) { group, _, cardModifier ->
        GamepadActionCard(
            title = stringResource(group.labelResId),
            description = stringResource(group.descriptionResId),
            icon = group.icon,
            alwaysShowFullDescription = true,
            onClick = { onSelectType(group) },
            modifier = cardModifier,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditButtonSubPageContent(
    button: PadButton?, // null → create new
    savedButton: PadButton? = null,
    accentColor: Color,
    initialAction: PadAction? = null,
    selectedIcon: String? = null,
    onOpenIconPicker: (currentDraft: PadButton) -> Unit,
    onOpenAppPicker: (currentDraft: PadButton) -> Unit,
    onOpenColorSubMenu: (currentDraft: PadButton, target: EditorColorTarget) -> Unit,
    onOpenKeyboardPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenGamepadPicker: ((currentDraft: PadButton, slotIndex: Int) -> Unit)? = null,
    onOpenMousePicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenTouchPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenMirrorPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenToolsPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenLayoutPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenMediaPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onOpenMacroPicker: ((currentDraft: PadButton) -> Unit)? = null,
    onDuplicate: ((PadButton) -> Unit)? = null,
    onCopyToLayout: ((PadButton) -> Unit)? = null,
    onDelete: ((PadButton) -> Unit)? = null,
    onDiscard: () -> Unit = {},
    onSave: (PadButton) -> Unit,
) {
    val context = LocalContext.current
    val activeLayout by MacroPadState.activeLayout.collectAsStateWithLifecycle()
    val designCanvas = activeLayout?.effectiveDesignCanvas ?: THOR_BOTTOM_CANVAS_SPEC
    val stableButtonId = remember(button?.id) { button?.id ?: UUID.randomUUID().toString() }

    LaunchedEffect(stableButtonId) {
        MacroPadState.setSelectedButtonId(stableButtonId)
    }

    val allProfiles by MacroPadState.profiles.collectAsStateWithLifecycle()
    val profile by MacroPadState.activeProfile.collectAsStateWithLifecycle()
    val swapFaceButtons by MacroPadSettings.gamepadSwapFaceButtons.collectAsStateWithLifecycle()

    val initAction =
        button?.action
            ?: initialAction
            ?: PadAction.GamepadButton(
                btnCode = GamepadKeycodes.BTN_SOUTH,
                label = gamepadCodeDisplayShortLabel(GamepadKeycodes.BTN_SOUTH, swapFaceButtons),
            )
    val resolvedInitDefaultLabel =
        initAction.defaultButtonLabel(
            context = context,
            allProfiles = allProfiles,
            activeProfile = profile,
            swapFaceButtons = swapFaceButtons,
        )
    val initLabel = button?.label ?: resolvedInitDefaultLabel
    val initIconName =
        if (button != null) {
            button.iconName ?: selectedIcon
        } else if (!initAction.supportsCustomIcon()) {
            null
        } else {
            selectedIcon ?: initAction.defaultIconName()
        }
    var label by remember(button) { mutableStateOf(initLabel) }
    var iconName by remember(button, selectedIcon) { mutableStateOf(selectedIcon ?: initIconName) }
    val initialSized =
        remember(button, initAction, designCanvas) {
            val base =
                button ?: PadButton(
                    id = stableButtonId,
                    posX = PBD_DEFAULT_CENTER_POS,
                    posY = PBD_DEFAULT_CENTER_POS,
                    widthPx = designCanvas.baseButtonSizePx,
                    heightPx = designCanvas.baseButtonSizePx,
                    label = initLabel,
                    iconName = initIconName,
                    action = initAction,
                )
            base.withActionSizing(initAction, designCanvas)
        }
    var buttonShape by remember(button) { mutableStateOf(initialSized.buttonShape) }
    var widthPx by remember(button) { mutableFloatStateOf(initialSized.widthPx) }
    var heightPx by remember(button) { mutableFloatStateOf(initialSized.heightPx) }
    var action by remember(button) { mutableStateOf(initAction) }
    var iconFilled by remember(button) { mutableStateOf(button?.iconFilled ?: true) }
    var hapticStrength by remember(button) { mutableStateOf(button?.hapticStrength ?: HapticStrength.OFF) }

    var hapticCustomDurationMs by remember(button) {
        mutableIntStateOf(
            if (button?.hapticStrength in listOf(HapticStrength.LIGHT, HapticStrength.MEDIUM, HapticStrength.STRONG)) {
                HF_PRESET_DURATION_MS
            } else {
                button?.hapticCustomDurationMs ?: HF_PRESET_DURATION_MS
            },
        )
    }
    var hapticCustomAmplitude by remember(button) {
        mutableIntStateOf(button?.hapticStrength?.defaultCustomAmplitude() ?: (button?.hapticCustomAmplitude ?: HF_LIGHT_AMPLITUDE_USER))
    }
    var buttonTextColor by remember(button) { mutableStateOf(button?.buttonTextColor) }
    var buttonBorderColor by remember(button) { mutableStateOf(button?.buttonBorderColor) }
    var buttonBgColor by remember(button) { mutableStateOf(button?.buttonBgColor) }
    var invisible by remember(button) { mutableStateOf(button?.invisible ?: (activeLayout?.invisibleButtons ?: false)) }

    val globalAccentInt by SettingsManager.accentColor.collectAsStateWithLifecycle()
    val globalAccentColor = Color(globalAccentInt)

    val macros = profile?.macros ?: emptyList()
    val layouts = profile?.layouts ?: emptyList()
    val recordedTap by TouchRecordingManager.recordedTap.collectAsStateWithLifecycle()
    val recordedGesture by TouchRecordingManager.recordedSingleGesture.collectAsStateWithLifecycle()
    val touchRecordingState by TouchRecordingManager.state.collectAsStateWithLifecycle()
    val recordingRequested by TouchRecordingManager.recordingRequested.collectAsStateWithLifecycle()
    val awaitingButtonTap by MacroPadNavState.awaitingButtonTapRecording.collectAsStateWithLifecycle()
    val awaitingButtonGesture by MacroPadNavState.awaitingButtonGestureRecording.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        snapshotFlow {
            ButtonTouchRecordingObservation(
                recordedTap = recordedTap,
                recordedGesture = recordedGesture,
                awaitingTap = awaitingButtonTap,
                awaitingGesture = awaitingButtonGesture,
                recordingRequested = recordingRequested,
                isIdle = touchRecordingState is TouchRecordingState.Idle,
            )
        }.collectLatest { obs ->
            if (obs.awaitingTap) {
                val tap = obs.recordedTap
                if (tap != null) {
                    MacroPadNavState.setAwaitingButtonTapRecording(false)
                    TouchRecordingManager.consumeRecordedTap()
                    TouchRecordingManager.resetState()
                    if (action is PadAction.TouchTap) {
                        val (rx, ry) = tap
                        AppLog.i(TAG, "Recorded touch tap applied to button: normX=$rx normY=$ry")
                        action = PadAction.TouchTap(normX = rx, normY = ry, isRecorded = true)
                    }
                } else if (!obs.recordingRequested && obs.isIdle) {
                    MacroPadNavState.setAwaitingButtonTapRecording(false)
                }
            }

            if (obs.awaitingGesture) {
                val gesture = obs.recordedGesture
                if (gesture != null) {
                    MacroPadNavState.setAwaitingButtonGestureRecording(false)
                    TouchRecordingManager.consumeRecordedSingleGesture()
                    TouchRecordingManager.resetState()
                    if (action is PadAction.SingleTouchGesture) {
                        AppLog.i(TAG, "Recorded single touch gesture applied to button: samples=${gesture.size}")
                        action = PadAction.SingleTouchGesture(samples = gesture)
                    }
                } else if (!obs.recordingRequested && obs.isIdle) {
                    MacroPadNavState.setAwaitingButtonGestureRecording(false)
                }
            }
        }
    }

    var posX by remember(button) { mutableFloatStateOf(button?.posX ?: PBD_DEFAULT_CENTER_POS) }
    var posY by remember(button) { mutableFloatStateOf(button?.posY ?: PBD_DEFAULT_CENTER_POS) }
    var isAdjusting by remember { mutableStateOf(false) }

    val currentButton =
        remember(
            stableButtonId,
            posX,
            posY,
            label,
            iconName,
            iconFilled,
            buttonShape,
            widthPx,
            heightPx,
            action,
            hapticStrength,
            hapticCustomDurationMs,
            hapticCustomAmplitude,
            buttonTextColor,
            buttonBorderColor,
            buttonBgColor,
            invisible,
        ) {
            PadButton(
                id = stableButtonId,
                label = label,
                iconName = iconName,
                iconFilled = iconFilled,
                posX = posX,
                posY = posY,
                widthPx = widthPx,
                heightPx = heightPx,
                buttonShape = buttonShape,
                action = action,
                hapticStrength = hapticStrength,
                hapticCustomDurationMs = hapticCustomDurationMs,
                hapticCustomAmplitude = hapticCustomAmplitude,
                buttonTextColor = buttonTextColor,
                buttonBorderColor = buttonBorderColor,
                buttonBgColor = buttonBgColor,
                invisible = invisible,
            )
        }

    val adjusterState =
        rememberButton2DAdjusterState(
            resolveButton = { currentButton },
            onButtonChange = { updated ->
                posX = updated.posX
                posY = updated.posY
                widthPx = updated.widthPx
                heightPx = updated.heightPx
            },
        )

    LaunchedEffect(isAdjusting) {
        MacroPadState.setAdjustingButtonViaGamepad(isAdjusting)
        if (isAdjusting) {
            MacroPadState.setSelectedButtonId(stableButtonId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            MacroPadState.setAdjustingButtonViaGamepad(false)
        }
    }

    fun onActionChanged(newAction: PadAction) {
        AppLog.d(TAG, "onActionChanged: $newAction")
        val updated =
            currentButton.withActionDefaults(
                newAction = newAction,
                context = context,
                allProfiles = allProfiles,
                activeProfile = profile,
                swapFaceButtons = swapFaceButtons,
                canvas = designCanvas,
            )
        action = updated.action
        label = updated.label
        iconName = updated.iconName
        widthPx = updated.widthPx
        heightPx = updated.heightPx
        buttonShape = updated.buttonShape
    }

    val isConfirmEnabled =
        when {
            action is PadAction.ScrollWheel || action is PadAction.TrackpointMove -> {
                true
            }

            action is PadAction.AppLauncher -> {
                (action as PadAction.AppLauncher).packageName.isNotBlank() && label.isNotBlank()
            }

            action is PadAction.Macro -> {
                label.isNotBlank() && macros.any { it.id == (action as PadAction.Macro).macroId }
            }

            action is PadAction.SwitchToLayout -> {
                label.isNotBlank() &&
                    layouts.any { it.id == (action as PadAction.SwitchToLayout).layoutId }
            }

            action is PadAction.SwitchToProfile -> {
                label.isNotBlank() &&
                    allProfiles.any { it.id == (action as PadAction.SwitchToProfile).profileId }
            }

            action is PadAction.TouchTap -> {
                (action as PadAction.TouchTap).isRecorded && label.isNotBlank()
            }

            action is PadAction.SingleTouchGesture -> {
                (action as PadAction.SingleTouchGesture).samples.isNotEmpty() && label.isNotBlank()
            }

            else -> {
                label.isNotBlank()
            }
        }

    val isNew = savedButton == null

    LaunchedEffect(currentButton) {
        MacroPadNavState.setButtonDraft(currentButton)
    }

    val hasChanges =
        isNew || currentButton != savedButton

    val layoutTextOpt = activeLayout?.buttonTextColor ?: ColorOption.Neutral
    val layoutBorderOpt = activeLayout?.buttonBorderColor ?: ColorOption.Neutral
    val layoutBgOpt = activeLayout?.buttonBgColor ?: ColorOption.Neutral

    val effectiveTextOpt = buttonTextColor ?: layoutTextOpt
    val effectiveBorderOpt = buttonBorderColor ?: layoutBorderOpt
    val effectiveBgOpt = buttonBgColor ?: layoutBgOpt

    val currentText = resolveColorOption(effectiveTextOpt, globalAccentColor, MP_AMBIENT_NEUTRAL_TEXT)
    val currentBorder = resolveColorOption(effectiveBorderOpt, globalAccentColor, MP_AMBIENT_NEUTRAL_BORDER)
    val currentBg = resolveBgColorOption(effectiveBgOpt, globalAccentColor)

    val showLabel = action !is PadAction.ScrollWheel && action !is PadAction.TrackpointMove
    val showIconPicker = action.supportsCustomIcon()

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = hasChanges,
            onSave = {
                if (isConfirmEnabled) {
                    AppLog.d(TAG, "Confirm button edit: id=${currentButton.id} label=${currentButton.label} action=${currentButton.action}")
                    onSave(currentButton)
                }
            },
            onDiscard = onDiscard,
        )

    // Label & Icon input
    if (showLabel) {
        GamepadTextFieldCard(
            title = stringResource(R.string.macropad_editor_button_label),
            description = stringResource(R.string.macropad_editor_button_label_desc),
            placeholder = stringResource(R.string.macropad_editor_button_label_placeholder),
            value = label,
            onValueChange = { label = it },
            icon = Icons.Rounded.Edit,
            modifier = Modifier.firstDeckItem(),
        )
    }

    if (showIconPicker) {
        GamepadActionCard(
            title = stringResource(R.string.macropad_icon_picker_title),
            description = if (iconName != null) iconName!! else stringResource(R.string.macropad_icon_picker_search),
            icon = Icons.Rounded.Image,
            actionLeadingContent = {
                if (iconName != null) {
                    MaterialSymbol(
                        name = iconName!!,
                        size = PBD_PICKER_ICON_SIZE,
                        tint = accentColor,
                        filled = iconFilled,
                    )
                }
            },
            onClick = { onOpenIconPicker(currentButton) },
        )
    }

    ActionPicker(
        current = action,
        isFirstItem = !showLabel,
        onOpenMacroPicker = {
            onOpenMacroPicker?.invoke(currentButton)
        },
        onOpenAppPicker = {
            onOpenAppPicker(currentButton)
        },
        onOpenKeyboardPicker = {
            onOpenKeyboardPicker?.invoke(currentButton)
        },
        onOpenGamepadPicker = { slotIndex ->
            onOpenGamepadPicker?.invoke(currentButton, slotIndex)
        },
        onOpenMousePicker = {
            onOpenMousePicker?.invoke(currentButton)
        },
        onOpenTouchPicker = {
            onOpenTouchPicker?.invoke(currentButton)
        },
        onOpenMirrorPicker = {
            onOpenMirrorPicker?.invoke(currentButton)
        },
        onOpenToolsPicker = {
            onOpenToolsPicker?.invoke(currentButton)
        },
        onOpenLayoutPicker = {
            onOpenLayoutPicker?.invoke(currentButton)
        },
        onOpenMediaPicker = {
            onOpenMediaPicker?.invoke(currentButton)
        },
        onRecordTouchTap = {
            MacroPadNavState.setButtonDraft(currentButton)
            AppStateManager.suspendCurrentAndDismiss()
            TouchRecordingManager.requestRecording(TouchRecordingMode.TAP)
            MacroPadNavState.setAwaitingButtonGestureRecording(false)
            MacroPadNavState.setAwaitingButtonTapRecording(true)
        },
        onRecordTouchGesture = {
            MacroPadNavState.setButtonDraft(currentButton)
            AppStateManager.suspendCurrentAndDismiss()
            TouchRecordingManager.requestRecording(TouchRecordingMode.SINGLE_GESTURE)
            MacroPadNavState.setAwaitingButtonTapRecording(false)
            MacroPadNavState.setAwaitingButtonGestureRecording(true)
        },
        onChange = ::onActionChanged,
    )

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_shape_size),
        color = accentColor,
    )

    if (action !is PadAction.ScrollWheel && action !is PadAction.TrackpointMove) {
        GamepadChoiceCard(
            title = stringResource(R.string.macropad_editor_button_shape),
            description = stringResource(R.string.macropad_btn_shape_desc),
            selectedText = buttonShape.displayLabel(),
            icon = Icons.Rounded.CropFree,
            onPrevious = {
                val nextShape = ButtonShape.entries.cycle(buttonShape, BumperDirection.PREV)
                AppLog.d(TAG, "buttonShape changed: $buttonShape -> $nextShape")
                buttonShape = nextShape
            },
            onNext = {
                val nextShape = ButtonShape.entries.cycle(buttonShape, BumperDirection.NEXT)
                AppLog.d(TAG, "buttonShape changed: $buttonShape -> $nextShape")
                buttonShape = nextShape
            },
        )
    }

    val adjustFocusRequester = remember { FocusRequester() }
    GamepadFocusCard(
        cardFocusRequester = adjustFocusRequester,
        onClick = {
            if (isAdjusting) {
                adjusterState.stopMovingImmediate()
                isAdjusting = false
            } else {
                isAdjusting = true
            }
        },
        itemKey = "adjust_position_size",
        isAdjusting = isAdjusting,
        onFocusChanged = { isFocused ->
            if (!isFocused && isAdjusting) {
                adjusterState.stopMovingImmediate()
                isAdjusting = false
            }
        },
        onCustomKeyEvent = { keyEvent ->
            adjusterState.handleKeyEvent(
                keyEvent = keyEvent,
                buttonId = stableButtonId,
                isAdjusting = isAdjusting,
                onDismiss = { isAdjusting = false },
            )
        },
    ) { isFocused ->
        GamepadCardRow(
            title = stringResource(R.string.macropad_editor_adjust_position_size),
            description =
                stringResource(
                    R.string.macropad_editor_adjust_position_size_value,
                    formatButtonDimensionPx(widthPx),
                    formatButtonDimensionPx(heightPx),
                    (posX * PBD_PERCENT_MULTIPLIER).roundToInt(),
                    (posY * PBD_PERCENT_MULTIPLIER).roundToInt(),
                ),
            icon = Icons.Rounded.OpenWith,
            trailingContent = {
                Button2DAdjusterPill(
                    isAdjusting = isAdjusting,
                    isR2Held = adjusterState.isR2Held,
                    isFocused = isFocused,
                )
            },
        )
    }

    if (action !is PadAction.ScrollWheel && action !is PadAction.TrackpointMove) {
        GamepadSectionHeader(
            text = stringResource(R.string.macropad_editor_section_haptic),
            color = accentColor,
        )

        val applyHapticStrength: (HapticStrength) -> Unit = { selectedStrength ->
            if (selectedStrength in listOf(HapticStrength.LIGHT, HapticStrength.MEDIUM, HapticStrength.STRONG)) {
                hapticCustomDurationMs = HF_PRESET_DURATION_MS
                hapticCustomAmplitude = selectedStrength.defaultCustomAmplitude()
            }
            hapticStrength = selectedStrength
        }

        val hapticSelectedText = stringResource(hapticStrength.labelResId())

        GamepadChoiceCard(
            title = stringResource(R.string.macropad_editor_section_haptic),
            description = stringResource(R.string.macropad_btn_haptic_desc),
            selectedText = hapticSelectedText,
            icon = Icons.Rounded.Vibration,
            onPrevious = { applyHapticStrength(HapticStrength.entries.cycle(hapticStrength, BumperDirection.PREV)) },
            onNext = { applyHapticStrength(HapticStrength.entries.cycle(hapticStrength, BumperDirection.NEXT)) },
        )

        if (hapticStrength == HapticStrength.CUSTOM) {
            GamepadStepperCard(
                title = stringResource(R.string.macropad_haptic_custom_duration),
                description = stringResource(R.string.macropad_btn_haptic_duration_desc),
                valueText = "$hapticCustomDurationMs ms",
                icon = Icons.Rounded.Vibration,
                onDecrement = {
                    hapticCustomDurationMs =
                        (hapticCustomDurationMs - PBD_DURATION_STEP_MS)
                            .coerceIn(PBD_DURATION_MIN_MS, PBD_DURATION_MAX_MS)
                },
                onIncrement = {
                    hapticCustomDurationMs =
                        (hapticCustomDurationMs + PBD_DURATION_STEP_MS)
                            .coerceIn(PBD_DURATION_MIN_MS, PBD_DURATION_MAX_MS)
                },
            )

            GamepadStepperCard(
                title = stringResource(R.string.macropad_haptic_custom_amplitude),
                description = stringResource(R.string.macropad_btn_haptic_amplitude_desc),
                valueText = "$hapticCustomAmplitude%",
                icon = Icons.Rounded.Vibration,
                onDecrement = {
                    hapticCustomAmplitude =
                        (hapticCustomAmplitude - PBD_AMPLITUDE_STEP)
                            .coerceIn(PBD_AMPLITUDE_MIN, PBD_AMPLITUDE_MAX)
                },
                onIncrement = {
                    hapticCustomAmplitude =
                        (hapticCustomAmplitude + PBD_AMPLITUDE_STEP)
                            .coerceIn(PBD_AMPLITUDE_MIN, PBD_AMPLITUDE_MAX)
                },
            )
        }
    }

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_button_colors),
        color = accentColor,
    )

    val previewLabel = stringResource(R.string.macropad_editor_button_preview_text)
    val previewShape = resolvePadButtonPreviewShape(buttonShape)
    val previewContentScale = ButtonShapeGeometry.contentScaleFor(buttonShape)
    val previewIconSize = PBD_ICON_SIZE_DP * previewContentScale
    val buttonPreviewLeading: (textColor: Color, borderColor: Color, bgColor: Color, isIconOnly: Boolean) -> @Composable () -> Unit =
        { tColor, bColor, bgCol, iconOnly ->
            {
                PadButtonFace(
                    width = PBD_COLOR_PREVIEW_SIZE,
                    height = PBD_COLOR_PREVIEW_SIZE,
                    shape = previewShape,
                    isIconOnly = iconOnly || buttonShape == ButtonShape.ICON_ONLY,
                    borderColor = bColor,
                    bgColor = bgCol,
                ) {
                    val currentIcon = iconName
                    if (currentIcon != null) {
                        MaterialSymbol(
                            name = currentIcon,
                            size = previewIconSize,
                            tint = tColor,
                            filled = iconFilled,
                        )
                    } else {
                        Text(
                            text = previewLabel,
                            color = tColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier =
                                Modifier.graphicsLayer {
                                    scaleX = previewContentScale
                                    scaleY = previewContentScale
                                },
                        )
                    }
                }
            }
        }

    listOf(
        ButtonColorCardEntry(
            target = EditorColorTarget.TEXT,
            icon = Icons.Rounded.FormatColorText,
            preview = buttonPreviewLeading(currentText, Color.Transparent, Color.Transparent, true),
            description = describeButtonColorOption(buttonTextColor, currentText),
        ),
        ButtonColorCardEntry(
            target = EditorColorTarget.BORDER,
            icon = Icons.Rounded.Palette,
            preview = buttonPreviewLeading(Color.Transparent, currentBorder, Color.Transparent, false),
            description = describeButtonColorOption(buttonBorderColor, currentBorder),
        ),
        ButtonColorCardEntry(
            target = EditorColorTarget.BG,
            icon = Icons.Rounded.FormatColorFill,
            preview = buttonPreviewLeading(Color.Transparent, Color.Transparent, currentBg, false),
            description = describeButtonColorOption(buttonBgColor, currentBg),
        ),
    ).forEach { entry ->
        GamepadActionCard(
            title = stringResource(entry.target.titleResId),
            description = entry.description,
            icon = entry.icon,
            actionLeadingContent = entry.preview,
            onClick = { onOpenColorSubMenu(currentButton, entry.target) },
        )
    }

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_visibility_behavior),
        color = accentColor,
    )

    GamepadToggleCard(
        title = stringResource(R.string.layout_settings_invisible_buttons),
        description = stringResource(R.string.layout_settings_invisible_buttons_desc),
        checked = invisible,
        icon = Icons.Rounded.VisibilityOff,
        onCheckedChange = { invisible = it },
    )

    if (button != null) {
        GamepadSectionHeader(
            text = stringResource(R.string.macropad_editor_manage_buttons),
            color = accentColor,
        )

        if (onDuplicate != null) {
            GamepadActionCard(
                title = stringResource(R.string.macropad_editor_copy_button_duplicate),
                description = stringResource(R.string.macropad_editor_duplicate_button_desc),
                icon = Icons.Rounded.ContentCopy,
                onClick = { onDuplicate(button) },
            )
        }

        if (onCopyToLayout != null) {
            GamepadActionCard(
                title = stringResource(R.string.macropad_editor_copy_to_layout),
                description = stringResource(R.string.macropad_editor_copy_button_to_layout_desc),
                icon = Icons.Rounded.Share,
                onClick = { onCopyToLayout(button) },
            )
        }
    }

    // ── Save / Save & Delete Section ─────────────────────────────────
    val hasDelete = button != null && onDelete != null
    GamepadSectionHeader(
        text =
            stringResource(
                if (hasDelete) {
                    R.string.macropad_editor_section_save_and_delete
                } else {
                    R.string.macropad_editor_section_save
                },
            ),
        color = accentColor,
    )

    // ── Save & Exit Action Row ───────────────────────────────────────
    GamepadSaveExitActionRow(
        title = stringResource(if (isNew) R.string.macropad_editor_create_button_title else R.string.macropad_editor_save_button_title),
        description =
            stringResource(
                if (isNew) R.string.macropad_editor_create_button_desc else R.string.macropad_editor_save_button_desc,
            ),
        pulseOnChanges = hasChanges,
        saveActionText = stringResource(if (isNew) R.string.gamepad_action_create else R.string.gamepad_action_save),
        saveIcon = Icons.Rounded.Save,
        enabled = isConfirmEnabled,
        showExitPrompt = promptState.showExitPrompt,
        onDismissPrompt = promptState.dismissPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
    )

    // ── Button Deletion (Last Item) ──────────────────────────────────
    if (button != null && onDelete != null) {
        GamepadTwoStepConfirmCard(
            title = stringResource(R.string.macropad_editor_delete_button),
            confirmTitle = stringResource(R.string.macropad_button_delete_confirm_title),
            description =
                stringResource(
                    R.string.macropad_editor_delete_button_desc,
                    button.label.ifBlank { button.action.displayLabel() },
                ),
            actionText = stringResource(R.string.gamepad_action_delete),
            confirmActionText = stringResource(R.string.gamepad_action_confirm),
            icon = Icons.Rounded.Delete,
            isDestructive = true,
            onConfirm = { onDelete(button) },
        )
    }
}

@Composable
internal fun ButtonColorSubPageContent(
    button: PadButton,
    savedButton: PadButton?,
    activeLayout: PadLayout?,
    target: EditorColorTarget,
    onColorOptionChanged: (ColorOption?) -> Unit,
    onOpenColorWheel: (title: String, breadcrumbs: List<String>, initialColor: Color, inFlightButton: PadButton) -> Unit,
) {
    ColorOptionSubPageContent(
        currentOption = button.getColorOption(target),
        layoutDefaultOption = activeLayout?.getColorOption(target) ?: ColorOption.Neutral,
        defaultNeutralColor = target.defaultNeutralColor,
        isBgTarget = target == EditorColorTarget.BG,
        selectColorWheelTitle = stringResource(target.selectWheelTitleResId),
        colorWheelBreadcrumbs =
            listOf(
                stringResource(R.string.macropad_editor_section_buttons),
                button.label.ifBlank { stringResource(R.string.macropad_editor_section_button_settings) },
                stringResource(target.titleResId),
                stringResource(R.string.gamepad_action_custom_color),
            ),
        onColorOptionChanged = onColorOptionChanged,
        onOpenColorWheel = { title, breadcrumbs, initialColor ->
            onOpenColorWheel(title, breadcrumbs, initialColor, button)
        },
    )
}
