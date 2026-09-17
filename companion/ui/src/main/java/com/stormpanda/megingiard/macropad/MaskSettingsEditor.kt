package com.stormpanda.megingiard.macropad

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.math.ViewportMath
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.dimColorFilter
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TAG = "MaskSettingsEditor"

private fun BackgroundScaleMode.labelResId(): Int =
    when (this) {
        BackgroundScaleMode.FILL -> R.string.bg_scale_mode_fill
        BackgroundScaleMode.FIT -> R.string.bg_scale_mode_fit
        BackgroundScaleMode.STRETCH -> R.string.bg_scale_mode_stretch
    }

private const val MSE_DIM_MAX = 0.95f
private const val MSE_DIM_STEP = 0.05f
private const val MSE_PERCENT_DIVISOR = 100f

private val MSE_PREVIEW_PADDING = 12.dp
private val MSE_PREVIEW_IMAGE_ROUNDING = 8.dp
private val MSE_PREVIEW_SHAPE = RoundedCornerShape(MSE_PREVIEW_IMAGE_ROUNDING)
private val MSE_ICON_SIZE_48 = 48.dp

private const val MSE_BOTTOM_SCREEN_ASPECT_RATIO = 31f / 27f // 1240 x 1080
private const val MSE_PREVIEW_WIDTH_FRACTION = 0.5f

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LayoutMaskSubPageContent(
    layout: PadLayout,
    profileName: String,
    accentColor: Color,
    onDiscard: () -> Unit = {},
    onConfirm: (
        maskImagePath: String?,
        maskImageChanged: Boolean,
        maskScale: Float,
        maskOffsetX: Float,
        maskOffsetY: Float,
        maskImageDim: Float,
        maskScaleMode: BackgroundScaleMode,
    ) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentMaskPath by remember(layout) { mutableStateOf(layout.maskImagePath) }
    var maskScale by remember(layout) { mutableFloatStateOf(layout.maskImageScale) }
    var maskOffsetX by remember(layout) { mutableFloatStateOf(layout.maskImageOffsetX) }
    var maskOffsetY by remember(layout) { mutableFloatStateOf(layout.maskImageOffsetY) }
    var maskImageDim by remember(layout) { mutableFloatStateOf(layout.maskImageDim) }
    var maskScaleMode by remember(layout) { mutableStateOf(layout.maskScaleMode) }
    var isCropActive by remember { mutableStateOf(false) }

    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(layout.id) {
        AppLog.d(TAG, "LayoutMaskSubPageContent opened for profile: $profileName, layout: ${layout.name}")
    }

    LaunchedEffect(isCropActive) {
        MacroPadState.setCroppingMask(isCropActive)
    }

    DisposableEffect(Unit) {
        onDispose {
            MacroPadState.setCroppingMask(false)
        }
    }

    LaunchedEffect(maskScaleMode) {
        val pl = MacroPadState.previewLayout.value ?: layout
        MacroPadState.setPreviewLayout(pl.copy(maskScaleMode = maskScaleMode))
    }

    val previewLayout by MacroPadState.previewLayout.collectAsStateWithLifecycle()
    LaunchedEffect(previewLayout?.maskImageScale, previewLayout?.maskImageOffsetX, previewLayout?.maskImageOffsetY) {
        val pl = previewLayout ?: return@LaunchedEffect
        maskScale = pl.maskImageScale
        maskOffsetX = pl.maskImageOffsetX
        maskOffsetY = pl.maskImageOffsetY
    }

    val maskImageDimFilter =
        remember(maskImageDim) {
            dimColorFilter(maskImageDim)
        }

    LaunchedEffect(pendingImageUri, currentMaskPath) {
        val pathOrUri = pendingImageUri?.toString() ?: currentMaskPath
        AppLog.d(TAG, "Loading mask bitmap for: $pathOrUri")
        withContext(Dispatchers.IO) {
            val bitmap =
                if (pathOrUri != null) {
                    MacroPadMediaRepository.loadScaledBitmap(context, pathOrUri)
                } else {
                    null
                }
            withContext(Dispatchers.Main) {
                previewBitmap = bitmap?.asImageBitmap()
                if (bitmap == null) {
                    isCropActive = false
                }
            }
        }
    }

    val pickedUri by BackgroundPickerManager.pickedUri.collectAsStateWithLifecycle()
    LaunchedEffect(pickedUri) {
        val uri = pickedUri ?: return@LaunchedEffect
        AppLog.d(TAG, "Mask image picked from system picker: $uri")
        pendingImageUri = uri
        currentMaskPath = null
        BackgroundPickerManager.clearPickedUri()
    }

    // Stream in-flight mask settings to bottom-screen preview in real-time
    LaunchedEffect(pendingImageUri, currentMaskPath, maskScale, maskOffsetX, maskOffsetY, maskImageDim, maskScaleMode) {
        val effectivePath = pendingImageUri?.toString() ?: currentMaskPath
        val inFlightLayout =
            (MacroPadState.previewLayout.value ?: layout).copy(
                maskImagePath = effectivePath,
                maskImageScale = maskScale,
                maskImageOffsetX = maskOffsetX,
                maskImageOffsetY = maskOffsetY,
                maskImageDim = maskImageDim,
                maskScaleMode = maskScaleMode,
            )
        MacroPadState.setPreviewLayout(inFlightLayout)
    }

    // 1. Preview Frame
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(MSE_PREVIEW_WIDTH_FRACTION),
        ) {
            GamepadFocusCard(
                onClick = null,
                modifier =
                    Modifier
                        .aspectRatio(MSE_BOTTOM_SCREEN_ASPECT_RATIO)
                        .firstDeckItem(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(MSE_PREVIEW_PADDING)
                            .clip(MSE_PREVIEW_SHAPE)
                            .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = previewBitmap
                    if (bitmap != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cw = size.width
                            val ch = size.height
                            val iw = bitmap.width.toFloat()
                            val ih = bitmap.height.toFloat()
                            if (cw > 0f && ch > 0f && iw > 0f && ih > 0f) {
                                val (dstOffset, dstSize) =
                                    when (maskScaleMode) {
                                        BackgroundScaleMode.STRETCH -> {
                                            IntOffset.Zero to IntSize(cw.toInt(), ch.toInt())
                                        }

                                        BackgroundScaleMode.FIT, BackgroundScaleMode.FILL -> {
                                            val scaleBase =
                                                if (maskScaleMode == BackgroundScaleMode.FIT) {
                                                    ViewportMath.calculateAspectFitScale(cw, ch, iw, ih)
                                                } else {
                                                    ViewportMath.calculateAspectFillScale(cw, ch, iw, ih)
                                                }
                                            val ws = iw * scaleBase
                                            val hs = ih * scaleBase
                                            val maxTx = ((ws * maskScale - cw) / 2f).coerceAtLeast(0f)
                                            val maxTy = ((hs * maskScale - ch) / 2f).coerceAtLeast(0f)
                                            val clampedX = (maskOffsetX * cw).coerceIn(-maxTx, maxTx)
                                            val clampedY = (maskOffsetY * ch).coerceIn(-maxTy, maxTy)
                                            IntOffset(
                                                ((cw - ws * maskScale) / 2f + clampedX).toInt(),
                                                ((ch - hs * maskScale) / 2f + clampedY).toInt(),
                                            ) to IntSize((ws * maskScale).toInt(), (hs * maskScale).toInt())
                                        }
                                    }
                                drawImage(
                                    image = bitmap,
                                    dstOffset = dstOffset,
                                    dstSize = dstSize,
                                    colorFilter = maskImageDimFilter,
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = stringResource(R.string.layout_settings_mask_image_none),
                            tint = colors.onSurfaceSecondary.copy(alpha = 0.38f),
                            modifier = Modifier.size(MSE_ICON_SIZE_48),
                        )
                    }
                }
            }
        }
    }

    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_artwork_source),
        color = accentColor,
    )

    GamepadActionCard(
        title = stringResource(R.string.layout_settings_mask_image_browse_local),
        description = stringResource(R.string.macropad_editor_bg_storage_desc),
        icon = Icons.Rounded.Folder,
        onClick = { BackgroundPickerManager.requestImagePicker() },
        modifier = Modifier.firstDeckItem(),
    )

    if (previewBitmap != null) {
        GamepadSectionHeader(
            text = stringResource(R.string.layout_settings_mask_image_adjustments),
            color = accentColor,
        )

        GamepadChoiceCard(
            title = stringResource(R.string.layout_settings_bg_scale_mode),
            description = stringResource(R.string.layout_settings_mask_scale_mode_desc),
            selectedText = stringResource(maskScaleMode.labelResId()),
            icon = Icons.Rounded.AspectRatio,
            onPrevious = {
                val newMode = BackgroundScaleMode.entries.cycle(maskScaleMode, BumperDirection.PREV)
                maskScaleMode = newMode
                if (newMode == BackgroundScaleMode.STRETCH) {
                    isCropActive = false
                }
            },
            onNext = {
                val newMode = BackgroundScaleMode.entries.cycle(maskScaleMode, BumperDirection.NEXT)
                maskScaleMode = newMode
                if (newMode == BackgroundScaleMode.STRETCH) {
                    isCropActive = false
                }
            },
        )

        GamepadToggleCard(
            title = stringResource(R.string.layout_settings_bg_image_crop),
            description =
                if (maskScaleMode == BackgroundScaleMode.STRETCH) {
                    stringResource(R.string.layout_settings_mask_image_crop_disabled_stretch)
                } else {
                    stringResource(R.string.layout_settings_mask_image_crop_desc)
                },
            checked = isCropActive && maskScaleMode != BackgroundScaleMode.STRETCH,
            enabled = maskScaleMode != BackgroundScaleMode.STRETCH,
            icon = Icons.Rounded.Crop,
            onCheckedChange = { isCropActive = it },
        )

        GamepadSliderCard(
            title = stringResource(R.string.layout_settings_bg_image_dimming_title),
            description = stringResource(R.string.help_bg_settings_dimming_desc),
            value = maskImageDim,
            valueRange = 0f..MSE_DIM_MAX,
            step = MSE_DIM_STEP,
            fineStep = 0.01f,
            icon = Icons.Rounded.BrightnessMedium,
            valueLabel = "${(maskImageDim * MSE_PERCENT_DIVISOR).roundToInt()}%",
            onValueChange = { newVal ->
                maskImageDim = (newVal * MSE_PERCENT_DIVISOR).roundToInt() / MSE_PERCENT_DIVISOR
            },
        )
    }

    // ── Save & Delete Section ─────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_save_and_delete),
        color = accentColor,
    )

    val hasChanges =
        pendingImageUri != null ||
            currentMaskPath != layout.maskImagePath ||
            maskScaleMode != layout.maskScaleMode ||
            kotlin.math.abs(maskScale - layout.maskImageScale) > 0.001f ||
            kotlin.math.abs(maskOffsetX - layout.maskImageOffsetX) > 0.001f ||
            kotlin.math.abs(maskOffsetY - layout.maskImageOffsetY) > 0.001f ||
            kotlin.math.abs(maskImageDim - layout.maskImageDim) > 0.001f

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = hasChanges,
            onSave = {
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        isCropActive = false
                        MacroPadState.setCroppingMask(false)
                        var maskChanged = false
                        val pending = pendingImageUri
                        val finalMaskPath =
                            if (pending != null) {
                                maskChanged = true
                                MacroPadMediaRepository.saveMaskImage(context, layout.id, pending)
                            } else if (currentMaskPath == null && layout.maskImagePath != null) {
                                maskChanged = true
                                MacroPadMediaRepository.deleteMaskImage(context, layout.id)
                                null
                            } else {
                                currentMaskPath
                            }
                        onConfirm(finalMaskPath, maskChanged, maskScale, maskOffsetX, maskOffsetY, maskImageDim, maskScaleMode)
                        isSaving = false
                    }
                }
            },
            onDiscard = {
                isCropActive = false
                MacroPadState.setCroppingMask(false)
                pendingImageUri = null
                currentMaskPath = layout.maskImagePath
                maskScale = layout.maskImageScale
                maskOffsetX = layout.maskImageOffsetX
                maskOffsetY = layout.maskImageOffsetY
                maskImageDim = layout.maskImageDim
                maskScaleMode = layout.maskScaleMode
                MacroPadState.clearPreviewLayout()
                onDiscard()
            },
        )

    GamepadSaveExitActionRow(
        title = stringResource(R.string.macropad_editor_save_layout_title),
        description = stringResource(R.string.macropad_editor_save_layout_desc),
        pulseOnChanges = hasChanges,
        saveActionText = stringResource(R.string.gamepad_action_save),
        saveIcon = Icons.Rounded.Save,
        enabled = !isSaving,
        showExitPrompt = promptState.showExitPrompt,
        onDismissPrompt = promptState.dismissPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
    )

    GamepadTwoStepConfirmCard(
        title = stringResource(R.string.macropad_editor_remove_mask_image),
        confirmTitle = stringResource(R.string.macropad_editor_mask_delete_confirm_title),
        description = stringResource(R.string.macropad_editor_remove_mask_image_desc),
        actionText = stringResource(R.string.gamepad_action_clear),
        confirmActionText = stringResource(R.string.gamepad_action_confirm),
        icon = Icons.Rounded.Delete,
        isDestructive = true,
        enabled = previewBitmap != null,
        onConfirm = {
            isCropActive = false
            MacroPadState.setCroppingMask(false)
            pendingImageUri = null
            currentMaskPath = null
            previewBitmap = null
            maskScale = 1f
            maskOffsetX = 0f
            maskOffsetY = 0f
        },
    )
}
