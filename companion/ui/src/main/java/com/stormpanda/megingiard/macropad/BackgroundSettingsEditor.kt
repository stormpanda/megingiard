package com.stormpanda.megingiard.macropad

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.math.ViewportMath
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadSaveExitActionRow
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.rememberSaveExitPromptState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TAG = "BackgroundSettingsEditor"

private const val BSE_DIM_MAX = 0.95f
private const val BSE_DIM_STEP = 0.05f
private const val BSE_PERCENT_DIVISOR = 100f

private val BSE_PREVIEW_IMAGE_ROUNDING = 8.dp
private val BSE_ICON_SIZE_48 = 48.dp

private const val BSE_BOTTOM_SCREEN_ASPECT_RATIO = 31f / 27f // 1240 x 1080
private const val BSE_PREVIEW_WIDTH_FRACTION = 0.5f

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LayoutBackgroundSubPageContent(
    layout: PadLayout,
    profileName: String,
    accentColor: Color,
    onOpenScrape: () -> Unit,
    onDiscard: () -> Unit = {},
    onConfirm: (
        backgroundImagePath: String?,
        useAsMask: Boolean,
        bgImageChanged: Boolean,
        bgScale: Float,
        bgOffsetX: Float,
        bgOffsetY: Float,
        bgImageDim: Float,
    ) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentBgPath by remember(layout) { mutableStateOf(layout.backgroundImagePath) }
    var useAsMask by remember(layout) { mutableStateOf(layout.useBackgroundImageAsMask) }
    var bgScale by remember(layout) { mutableFloatStateOf(layout.bgImageScale) }
    var bgOffsetX by remember(layout) { mutableFloatStateOf(layout.bgImageOffsetX) }
    var bgOffsetY by remember(layout) { mutableFloatStateOf(layout.bgImageOffsetY) }
    var bgImageDim by remember(layout) { mutableFloatStateOf(layout.backgroundImageDim) }
    var isCropActive by remember { mutableStateOf(false) }

    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(isCropActive) {
        MacroPadState.setCroppingBackground(isCropActive)
    }

    DisposableEffect(Unit) {
        onDispose {
            MacroPadState.setCroppingBackground(false)
        }
    }

    val previewLayout by MacroPadState.previewLayout.collectAsState()
    LaunchedEffect(previewLayout?.bgImageScale, previewLayout?.bgImageOffsetX, previewLayout?.bgImageOffsetY) {
        val pl = previewLayout ?: return@LaunchedEffect
        bgScale = pl.bgImageScale
        bgOffsetX = pl.bgImageOffsetX
        bgOffsetY = pl.bgImageOffsetY
    }

    val bgImageDimFilter =
        remember(bgImageDim) {
            val brightness = 1f - bgImageDim
            val matrix =
                ColorMatrix(
                    floatArrayOf(
                        brightness,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        brightness,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        brightness,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                    ),
                )
            ColorFilter.colorMatrix(matrix)
        }

    val pickedUri by BackgroundPickerManager.pickedUri.collectAsState()
    LaunchedEffect(pickedUri) {
        val uri = pickedUri ?: return@LaunchedEffect
        pendingImageUri = uri
        currentBgPath = null
        bgScale = 1f
        bgOffsetX = 0f
        bgOffsetY = 0f
        BackgroundPickerManager.clearPickedUri()
    }

    LaunchedEffect(pendingImageUri, currentBgPath) {
        withContext(Dispatchers.IO) {
            val pathOrUri = pendingImageUri?.toString() ?: currentBgPath
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

    // Stream in-flight background settings to bottom-screen preview in real-time
    LaunchedEffect(pendingImageUri, currentBgPath, useAsMask, bgScale, bgOffsetX, bgOffsetY, bgImageDim) {
        val effectivePath = pendingImageUri?.toString() ?: currentBgPath
        val inFlightLayout =
            layout.copy(
                backgroundImagePath = effectivePath,
                useBackgroundImageAsMask = useAsMask,
                bgImageScale = bgScale,
                bgImageOffsetX = bgOffsetX,
                bgImageOffsetY = bgOffsetY,
                backgroundImageDim = bgImageDim,
            )
        MacroPadState.setPreviewLayout(inFlightLayout)
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
                cardBgColor = Color.Black,
                shape = RoundedCornerShape(BSE_PREVIEW_IMAGE_ROUNDING),
            ) {
                val bitmap = previewBitmap
                if (bitmap != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cw = size.width
                        val ch = size.height
                        val iw = bitmap.width.toFloat()
                        val ih = bitmap.height.toFloat()
                        if (cw > 0f && ch > 0f && iw > 0f && ih > 0f) {
                            val scaleBase = ViewportMath.calculateAspectFillScale(cw, ch, iw, ih)
                            val ws = iw * scaleBase
                            val hs = ih * scaleBase

                            val maxTx = ((ws * bgScale - cw) / 2f).coerceAtLeast(0f)
                            val maxTy = ((hs * bgScale - ch) / 2f).coerceAtLeast(0f)
                            val clampedX = (bgOffsetX * cw).coerceIn(-maxTx, maxTx)
                            val clampedY = (bgOffsetY * ch).coerceIn(-maxTy, maxTy)

                            drawImage(
                                image = bitmap,
                                dstOffset =
                                    IntOffset(
                                        ((cw - ws * bgScale) / 2f + clampedX).toInt(),
                                        ((ch - hs * bgScale) / 2f + clampedY).toInt(),
                                    ),
                                dstSize =
                                    IntSize(
                                        (ws * bgScale).toInt(),
                                        (hs * bgScale).toInt(),
                                    ),
                                colorFilter = bgImageDimFilter,
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = stringResource(R.string.layout_settings_bg_image_none),
                            tint = colors.onSurfaceSecondary.copy(alpha = 0.38f),
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

    GamepadActionCard(
        title = stringResource(R.string.layout_settings_bg_image_scrape),
        description = stringResource(R.string.macropad_editor_bg_steamgriddb_desc),
        actionText = stringResource(R.string.gamepad_action_search),
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

    GamepadActionCard(
        title = stringResource(R.string.layout_settings_bg_image_browse_local),
        description = stringResource(R.string.macropad_editor_bg_storage_desc),
        actionText = stringResource(R.string.gamepad_action_browse),
        icon = Icons.Rounded.Folder,
        onClick = { BackgroundPickerManager.requestImagePicker() },
    )

    if (previewBitmap != null) {
        GamepadSectionHeader(
            text = stringResource(R.string.layout_settings_bg_image_crop),
            color = accentColor,
        )

        GamepadToggleCard(
            title = stringResource(R.string.layout_settings_bg_image_crop),
            description = stringResource(R.string.layout_settings_bg_image_crop_desc),
            checked = isCropActive,
            icon = Icons.Rounded.Crop,
            onCheckedChange = { isCropActive = it },
        )

        GamepadSliderCard(
            title = stringResource(R.string.layout_settings_bg_image_dimming_title),
            description = stringResource(R.string.help_bg_settings_dimming_desc),
            value = bgImageDim,
            valueRange = 0f..BSE_DIM_MAX,
            step = BSE_DIM_STEP,
            icon = Icons.Rounded.BrightnessMedium,
            valueLabel = "${(bgImageDim * BSE_PERCENT_DIVISOR).roundToInt()}%",
            onValueChange = { newVal ->
                bgImageDim = (newVal * BSE_PERCENT_DIVISOR).roundToInt() / BSE_PERCENT_DIVISOR
            },
        )

        GamepadToggleCard(
            title = stringResource(R.string.layout_settings_bg_image_use_as_mask),
            description = stringResource(R.string.layout_settings_bg_image_use_as_mask_desc),
            checked = useAsMask,
            icon = Icons.Rounded.Layers,
            onCheckedChange = { useAsMask = it },
        )
    }

    // ── Save / Save & Delete Section ─────────────────────────────────
    val hasDelete = previewBitmap != null
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

    val hasChanges =
        pendingImageUri != null ||
            currentBgPath != layout.backgroundImagePath ||
            useAsMask != layout.useBackgroundImageAsMask ||
            kotlin.math.abs(bgScale - layout.bgImageScale) > 0.001f ||
            kotlin.math.abs(bgOffsetX - layout.bgImageOffsetX) > 0.001f ||
            kotlin.math.abs(bgOffsetY - layout.bgImageOffsetY) > 0.001f ||
            kotlin.math.abs(bgImageDim - layout.backgroundImageDim) > 0.001f

    val promptState =
        rememberSaveExitPromptState(
            hasChanges = hasChanges,
            onSave = {
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        isCropActive = false
                        MacroPadState.setCroppingBackground(false)
                        var bgChanged = false
                        val pending = pendingImageUri
                        val finalBgPath =
                            if (pending != null) {
                                bgChanged = true
                                MacroPadMediaRepository.saveBackgroundImage(context, layout.id, pending)
                            } else if (currentBgPath == null && layout.backgroundImagePath != null) {
                                bgChanged = true
                                MacroPadMediaRepository.deleteBackgroundImage(context, layout.id)
                                null
                            } else {
                                currentBgPath
                            }
                        onConfirm(finalBgPath, useAsMask, bgChanged, bgScale, bgOffsetX, bgOffsetY, bgImageDim)
                        isSaving = false
                    }
                }
            },
            onDiscard = {
                isCropActive = false
                MacroPadState.setCroppingBackground(false)
                pendingImageUri = null
                currentBgPath = layout.backgroundImagePath
                useAsMask = layout.useBackgroundImageAsMask
                bgScale = layout.bgImageScale
                bgOffsetX = layout.bgImageOffsetX
                bgOffsetY = layout.bgImageOffsetY
                bgImageDim = layout.backgroundImageDim
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

    if (previewBitmap != null) {
        GamepadTwoStepConfirmCard(
            title = stringResource(R.string.layout_settings_bg_image_none),
            confirmTitle = stringResource(R.string.macropad_editor_bg_delete_confirm_title),
            description = stringResource(R.string.macropad_editor_bg_storage_desc),
            actionText = stringResource(R.string.gamepad_action_clear),
            confirmActionText = stringResource(R.string.gamepad_action_confirm),
            icon = Icons.Rounded.Delete,
            isDestructive = true,
            onConfirm = {
                isCropActive = false
                MacroPadState.setCroppingBackground(false)
                pendingImageUri = null
                currentBgPath = null
                previewBitmap = null
                bgScale = 1f
                bgOffsetX = 0f
                bgOffsetY = 0f
            },
        )
    }
}
