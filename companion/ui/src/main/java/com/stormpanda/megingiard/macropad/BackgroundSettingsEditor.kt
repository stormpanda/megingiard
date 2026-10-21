package com.stormpanda.megingiard.macropad

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "BgSettingsEditor"

internal enum class LayerImageKind {
    BACKGROUND,
    MASK,
}

private fun BackgroundScaleMode.labelResId(): Int =
    when (this) {
        BackgroundScaleMode.FILL -> R.string.bg_scale_mode_fill
        BackgroundScaleMode.FIT -> R.string.bg_scale_mode_fit
        BackgroundScaleMode.STRETCH -> R.string.bg_scale_mode_stretch
    }

private const val BSE_DIM_MAX = 0.95f
private const val BSE_DIM_STEP = 0.05f
private const val BSE_DIM_FINE_STEP = 0.01f
private const val BSE_FLOAT_EPSILON = 0.001f
private const val BSE_PERCENT_DIVISOR = 100f
private const val BSE_DISABLED_ICON_ALPHA = 0.38f

private val BSE_PREVIEW_PADDING = 12.dp
private val BSE_PREVIEW_IMAGE_ROUNDING = 8.dp
private val BSE_PREVIEW_SHAPE = RoundedCornerShape(BSE_PREVIEW_IMAGE_ROUNDING)
private val BSE_ICON_SIZE_48 = 48.dp

private const val BSE_BOTTOM_SCREEN_ASPECT_RATIO = 31f / 27f // 1240 x 1080
private const val BSE_PREVIEW_WIDTH_FRACTION = 0.5f

@Composable
internal fun LayoutBackgroundSubPageContent(
    layout: PadLayout,
    profileName: String,
    accentColor: Color,
    onOpenScrape: () -> Unit,
    onDiscard: () -> Unit = {},
    onConfirm: (
        backgroundImagePath: String?,
        bgImageChanged: Boolean,
        bgScale: Float,
        bgOffsetX: Float,
        bgOffsetY: Float,
        bgImageDim: Float,
        bgScaleMode: BackgroundScaleMode,
    ) -> Unit,
) {
    LayerImageSettingsEditor(
        kind = LayerImageKind.BACKGROUND,
        layout = layout,
        profileName = profileName,
        accentColor = accentColor,
        onOpenScrape = onOpenScrape,
        onDiscard = onDiscard,
        onConfirm = onConfirm,
    )
}

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
    LayerImageSettingsEditor(
        kind = LayerImageKind.MASK,
        layout = layout,
        profileName = profileName,
        accentColor = accentColor,
        onOpenScrape = null,
        onDiscard = onDiscard,
        onConfirm = onConfirm,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LayerImageSettingsEditor(
    kind: LayerImageKind,
    layout: PadLayout,
    profileName: String,
    accentColor: Color,
    onOpenScrape: (() -> Unit)?,
    onDiscard: () -> Unit = {},
    onConfirm: (
        imagePath: String?,
        imageChanged: Boolean,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        imageDim: Float,
        scaleMode: BackgroundScaleMode,
    ) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val isMask = kind == LayerImageKind.MASK

    val initialPath = if (isMask) layout.maskImagePath else layout.backgroundImagePath
    val initialScale = if (isMask) layout.maskImageScale else layout.bgImageScale
    val initialOffsetX = if (isMask) layout.maskImageOffsetX else layout.bgImageOffsetX
    val initialOffsetY = if (isMask) layout.maskImageOffsetY else layout.bgImageOffsetY
    val initialDim = if (isMask) layout.maskImageDim else layout.backgroundImageDim
    val initialScaleMode = if (isMask) layout.maskScaleMode else layout.bgScaleMode

    val previewLayout by MacroPadState.previewLayout.collectAsStateWithLifecycle()
    val activeDraft = previewLayout?.takeIf { it.id == layout.id } ?: layout

    var pendingImageUri by remember(layout, kind) { mutableStateOf<Uri?>(null) }
    var isCropActive by remember(layout, kind) { mutableStateOf(false) }

    val currentImagePath =
        pendingImageUri?.toString()
            ?: if (isMask) activeDraft.maskImagePath else activeDraft.backgroundImagePath
    val imageScale = if (isMask) activeDraft.maskImageScale else activeDraft.bgImageScale
    val imageOffsetX = if (isMask) activeDraft.maskImageOffsetX else activeDraft.bgImageOffsetX
    val imageOffsetY = if (isMask) activeDraft.maskImageOffsetY else activeDraft.bgImageOffsetY
    val imageDim = if (isMask) activeDraft.maskImageDim else activeDraft.backgroundImageDim
    val scaleMode = if (isMask) activeDraft.maskScaleMode else activeDraft.bgScaleMode

    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadedImagePath by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val pickedUri by BackgroundPickerManager.pickedUri.collectAsStateWithLifecycle()
    val isLoadingBitmap = pickedUri != null || (currentImagePath != null && loadedImagePath != currentImagePath)

    fun updateDraft(transform: (PadLayout) -> PadLayout) {
        val base = MacroPadState.previewLayout.value?.takeIf { it.id == layout.id } ?: layout
        MacroPadState.setPreviewLayout(transform(base))
    }

    fun setCropping(active: Boolean) {
        if (isMask) {
            MacroPadState.setCroppingMask(active)
        } else {
            MacroPadState.setCroppingBackground(active)
        }
    }

    LaunchedEffect(layout, kind) {
        AppLog.d(TAG, "LayerImageSettingsEditor($kind) opened for profile: $profileName, layout: ${layout.name}")
        MacroPadState.setPreviewLayout(layout)
    }

    LaunchedEffect(isCropActive) {
        setCropping(isCropActive)
    }

    DisposableEffect(Unit) {
        onDispose {
            setCropping(false)
        }
    }

    val imageDimFilter =
        remember(imageDim) {
            dimColorFilter(imageDim)
        }

    LaunchedEffect(currentImagePath) {
        AppLog.d(TAG, "Loading $kind bitmap for: $currentImagePath")
        if (currentImagePath == null) {
            previewBitmap = null
            loadedImagePath = null
            isCropActive = false
            return@LaunchedEffect
        }
        val bitmap = MacroPadMediaRepository.loadScaledBitmap(context, currentImagePath)
        previewBitmap = bitmap?.asImageBitmap()
        loadedImagePath = currentImagePath
        if (bitmap == null) {
            isCropActive = false
        }
    }

    LaunchedEffect(pickedUri) {
        val uri = pickedUri ?: return@LaunchedEffect
        AppLog.d(TAG, "$kind image picked from system picker: $uri")
        pendingImageUri = uri
        updateDraft { base ->
            if (isMask) {
                base.copy(maskImagePath = uri.toString())
            } else {
                base.copy(backgroundImagePath = uri.toString())
            }
        }
        BackgroundPickerManager.clearPickedUri()
    }

    // 1. Preview Frame
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(BSE_PREVIEW_WIDTH_FRACTION),
        ) {
            GamepadFocusCard(
                onClick = null,
                modifier =
                    Modifier
                        .aspectRatio(BSE_BOTTOM_SCREEN_ASPECT_RATIO)
                        .firstDeckItem(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(BSE_PREVIEW_PADDING)
                            .clip(BSE_PREVIEW_SHAPE)
                            .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = previewBitmap
                    if (bitmap != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawAdjustedBitmap(
                                bitmap = bitmap,
                                scaleMode = scaleMode,
                                userScale = imageScale,
                                offsetX = imageOffsetX,
                                offsetY = imageOffsetY,
                                colorFilter = imageDimFilter,
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (isMask) Icons.Rounded.Layers else Icons.Rounded.Image,
                            contentDescription =
                                stringResource(
                                    if (isMask) R.string.layout_settings_mask_image_none else R.string.layout_settings_bg_image_none,
                                ),
                            tint = colors.onSurfaceSecondary.copy(alpha = BSE_DISABLED_ICON_ALPHA),
                            modifier = Modifier.size(BSE_ICON_SIZE_48),
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

    if (onOpenScrape != null) {
        GamepadActionCard(
            title = stringResource(R.string.layout_settings_bg_image_scrape),
            description = stringResource(R.string.macropad_editor_bg_steamgriddb_desc),
            icon = Icons.Rounded.Search,
            onClick = {
                if (SettingsManager.steamGridDbApiToken.value.isBlank()) {
                    Toast.makeText(context, R.string.steamgriddb_token_missing_message, Toast.LENGTH_LONG).show()
                } else {
                    onOpenScrape()
                }
            },
            modifier = Modifier.firstDeckItem(),
        )
    }

    GamepadActionCard(
        title =
            stringResource(
                if (isMask) R.string.layout_settings_mask_image_browse_local else R.string.layout_settings_bg_image_browse_local,
            ),
        description = stringResource(R.string.macropad_editor_bg_storage_desc),
        icon = Icons.Rounded.Folder,
        onClick = { BackgroundPickerManager.requestImagePicker() },
        modifier = if (onOpenScrape == null) Modifier.firstDeckItem() else Modifier,
    )

    if (previewBitmap != null) {
        GamepadSectionHeader(
            text =
                stringResource(
                    if (isMask) R.string.layout_settings_mask_image_adjustments else R.string.layout_settings_bg_image_adjustments,
                ),
            color = accentColor,
        )

        GamepadChoiceCard(
            title = stringResource(R.string.layout_settings_bg_scale_mode),
            description =
                stringResource(
                    if (isMask) R.string.layout_settings_mask_scale_mode_desc else R.string.layout_settings_bg_scale_mode_desc,
                ),
            selectedText = stringResource(scaleMode.labelResId()),
            icon = Icons.Rounded.AspectRatio,
            onPrevious = {
                val newMode = BackgroundScaleMode.entries.cycle(scaleMode, BumperDirection.PREV)
                updateDraft { base ->
                    if (isMask) base.copy(maskScaleMode = newMode) else base.copy(bgScaleMode = newMode)
                }
                if (newMode == BackgroundScaleMode.STRETCH) {
                    isCropActive = false
                }
            },
            onNext = {
                val newMode = BackgroundScaleMode.entries.cycle(scaleMode, BumperDirection.NEXT)
                updateDraft { base ->
                    if (isMask) base.copy(maskScaleMode = newMode) else base.copy(bgScaleMode = newMode)
                }
                if (newMode == BackgroundScaleMode.STRETCH) {
                    isCropActive = false
                }
            },
        )

        GamepadToggleCard(
            title = stringResource(R.string.layout_settings_bg_image_crop),
            description =
                if (scaleMode == BackgroundScaleMode.STRETCH) {
                    stringResource(
                        if (isMask) {
                            R.string.layout_settings_mask_image_crop_disabled_stretch
                        } else {
                            R.string.layout_settings_bg_image_crop_disabled_stretch
                        },
                    )
                } else {
                    stringResource(
                        if (isMask) R.string.layout_settings_mask_image_crop_desc else R.string.layout_settings_bg_image_crop_desc,
                    )
                },
            checked = isCropActive && scaleMode != BackgroundScaleMode.STRETCH,
            enabled = scaleMode != BackgroundScaleMode.STRETCH,
            icon = Icons.Rounded.Crop,
            onCheckedChange = { isCropActive = it },
        )

        GamepadSliderCard(
            title = stringResource(R.string.layout_settings_bg_image_dimming_title),
            description = stringResource(R.string.help_bg_settings_dimming_desc),
            value = imageDim,
            valueRange = 0f..BSE_DIM_MAX,
            step = BSE_DIM_STEP,
            fineStep = BSE_DIM_FINE_STEP,
            icon = Icons.Rounded.BrightnessMedium,
            valueLabel = "${(imageDim * BSE_PERCENT_DIVISOR).roundToInt()}%",
            onValueChange = { newVal ->
                val roundedDim = (newVal * BSE_PERCENT_DIVISOR).roundToInt() / BSE_PERCENT_DIVISOR
                updateDraft { base ->
                    if (isMask) base.copy(maskImageDim = roundedDim) else base.copy(backgroundImageDim = roundedDim)
                }
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
            currentImagePath != initialPath ||
            scaleMode != initialScaleMode ||
            abs(imageScale - initialScale) > BSE_FLOAT_EPSILON ||
            abs(imageOffsetX - initialOffsetX) > BSE_FLOAT_EPSILON ||
            abs(imageOffsetY - initialOffsetY) > BSE_FLOAT_EPSILON ||
            abs(imageDim - initialDim) > BSE_FLOAT_EPSILON

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = hasChanges,
            onSave = {
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        isCropActive = false
                        setCropping(false)
                        var imageChanged = false
                        val pending = pendingImageUri
                        val finalPath =
                            if (pending != null) {
                                imageChanged = true
                                if (isMask) {
                                    MacroPadMediaRepository.saveMaskImage(context, layout.id, pending)
                                } else {
                                    MacroPadMediaRepository.saveBackgroundImage(context, layout.id, pending)
                                }
                            } else if (currentImagePath == null && initialPath != null) {
                                imageChanged = true
                                if (isMask) {
                                    MacroPadMediaRepository.deleteMaskImage(context, layout.id)
                                } else {
                                    MacroPadMediaRepository.deleteBackgroundImage(context, layout.id)
                                }
                                null
                            } else {
                                currentImagePath
                            }
                        pendingImageUri = null
                        updateDraft { base ->
                            if (isMask) base.copy(maskImagePath = finalPath) else base.copy(backgroundImagePath = finalPath)
                        }
                        onConfirm(finalPath, imageChanged, imageScale, imageOffsetX, imageOffsetY, imageDim, scaleMode)
                        isSaving = false
                    }
                }
            },
            onDiscard = {
                isCropActive = false
                setCropping(false)
                pendingImageUri = null
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
        enabled = !isSaving && !isLoadingBitmap,
        showExitPrompt = promptState.showExitPrompt,
        onDismissPrompt = promptState.dismissPrompt,
        saveFocusRequester = promptState.focusRequester,
        bringIntoViewRequester = promptState.bringIntoViewRequester,
        onSave = promptState.onSave,
        onDiscard = promptState.onDiscard,
    )

    GamepadTwoStepConfirmCard(
        title =
            stringResource(
                if (isMask) R.string.macropad_editor_remove_mask_image else R.string.macropad_editor_remove_bg_image,
            ),
        confirmTitle =
            stringResource(
                if (isMask) R.string.macropad_editor_mask_delete_confirm_title else R.string.macropad_editor_bg_delete_confirm_title,
            ),
        description =
            stringResource(
                if (isMask) R.string.macropad_editor_remove_mask_image_desc else R.string.macropad_editor_remove_bg_image_desc,
            ),
        actionText = stringResource(R.string.gamepad_action_clear),
        confirmActionText = stringResource(R.string.gamepad_action_confirm),
        icon = Icons.Rounded.Delete,
        isDestructive = true,
        enabled = previewBitmap != null,
        onConfirm = {
            isCropActive = false
            setCropping(false)
            pendingImageUri = null
            previewBitmap = null
            loadedImagePath = null
            updateDraft { base ->
                if (isMask) {
                    base.copy(
                        maskImagePath = null,
                        maskImageScale = 1f,
                        maskImageOffsetX = 0f,
                        maskImageOffsetY = 0f,
                    )
                } else {
                    base.copy(
                        backgroundImagePath = null,
                        bgImageScale = 1f,
                        bgImageOffsetX = 0f,
                        bgImageOffsetY = 0f,
                    )
                }
            }
        },
    )
}
