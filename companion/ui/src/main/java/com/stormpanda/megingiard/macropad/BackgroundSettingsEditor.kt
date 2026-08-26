package com.stormpanda.megingiard.macropad

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Pinch
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.BitmapUtils
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.math.ViewportMath
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.firstDeckItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val TAG = "BackgroundSettingsEditor"

private const val BSE_DIM_MAX = 0.95f
private const val BSE_DIM_STEP = 0.05f
private const val BSE_PERCENT_DIVISOR = 100f

private val BSE_PREVIEW_IMAGE_ROUNDING = 8.dp
private val BSE_ICON_SIZE_48 = 48.dp
private val BSE_SPACING_8 = 8.dp
private val BSE_SPACING_16 = 16.dp

private const val BSE_BOTTOM_SCREEN_ASPECT_RATIO = 31f / 27f // 1240 x 1080
private const val BSE_PREVIEW_WIDTH_FRACTION = 0.5f

private const val BSE_CROP_MIN_SCALE = 1.0f
private const val BSE_CROP_MAX_SCALE = 5.0f

@Composable
internal fun LayoutBackgroundSubPageContent(
    layout: PadLayout,
    profileName: String,
    accentColor: Color,
    onOpenScrape: () -> Unit,
    onOpenCrop: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
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

    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }

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
            val (targetW, targetH) = BitmapUtils.getScreenTargetDimensions(context)
            val bitmap =
                when {
                    pendingImageUri != null -> {
                        BitmapUtils.decodeScaledBitmapFromUri(context, pendingImageUri!!, targetW, targetH)
                    }

                    currentBgPath != null -> {
                        val path = currentBgPath!!
                        if (path.startsWith("/")) {
                            BitmapUtils.decodeScaledBitmap(File(path), targetW, targetH)
                        } else {
                            MacroPadMediaRepository.loadScaledBitmap(context, path)
                        }
                    }

                    else -> {
                        null
                    }
                }
            withContext(Dispatchers.Main) {
                previewBitmap = bitmap?.asImageBitmap()
            }
        }
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

        GamepadActionCard(
            title = stringResource(R.string.layout_settings_bg_image_crop),
            description = stringResource(R.string.macropad_editor_appearance_desc),
            actionText = stringResource(R.string.gamepad_action_crop),
            icon = Icons.Rounded.Crop,
            onClick = { onOpenCrop(bgScale, bgOffsetX, bgOffsetY) },
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

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_save_layout_title),
        description = stringResource(R.string.macropad_editor_save_layout_desc),
        actionText = stringResource(R.string.gamepad_action_save),
        icon = Icons.Rounded.Save,
        enabled = !isSaving,
        onClick = {
            if (!isSaving) {
                isSaving = true
                scope.launch {
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

private fun getMaxOffsets(
    containerSize: IntSize,
    imageSize: IntSize,
    scale: Float,
): Pair<Float, Float> {
    val scaleBase =
        ViewportMath.calculateAspectFillScale(
            containerSize.width.toFloat(),
            containerSize.height.toFloat(),
            imageSize.width.toFloat(),
            imageSize.height.toFloat(),
        )
    return ViewportMath.getMaxOffsets(
        containerSize.width.toFloat(),
        containerSize.height.toFloat(),
        imageSize.width.toFloat() * scaleBase,
        imageSize.height.toFloat() * scaleBase,
        scale,
    )
}

@Composable
internal fun BackgroundCropSubPageContent(
    layout: PadLayout,
    initialScale: Float,
    initialOffsetX: Float,
    initialOffsetY: Float,
    accentColor: Color,
    onConfirmCrop: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current

    var cropBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var scale by remember(initialScale) { mutableFloatStateOf(initialScale) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var offsetXState by remember { mutableFloatStateOf(0f) }
    var offsetYState by remember { mutableFloatStateOf(0f) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(layout.backgroundImagePath, layout.backgroundImageVersion) {
        withContext(Dispatchers.IO) {
            val (targetW, targetH) = BitmapUtils.getScreenTargetDimensions(context)
            val path = layout.backgroundImagePath
            val bitmap =
                if (path != null) {
                    if (path.startsWith("/")) {
                        BitmapUtils.decodeScaledBitmap(File(path), targetW, targetH)
                    } else {
                        MacroPadMediaRepository.loadScaledBitmap(context, path)
                    }
                } else {
                    null
                }
            withContext(Dispatchers.Main) {
                cropBitmap = bitmap?.asImageBitmap()
            }
        }
    }

    LaunchedEffect(containerSize) {
        if (containerSize.width > 0 && containerSize.height > 0 && !isInitialized) {
            offsetXState = initialOffsetX * containerSize.width
            offsetYState = initialOffsetY * containerSize.height
            isInitialized = true
        }
    }

    // Top Section: Preview on Left (55%), Hints on Right (45%)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BSE_SPACING_16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(0.55f),
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
                val bitmap = cropBitmap
                if (bitmap != null) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .onSizeChanged { containerSize = it }
                                .pointerInput(bitmap, isInitialized) {
                                    if (!isInitialized) return@pointerInput
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val imageSize = IntSize(bitmap.width, bitmap.height)
                                        val newScale = (scale * zoom).coerceIn(BSE_CROP_MIN_SCALE, BSE_CROP_MAX_SCALE)
                                        scale = newScale

                                        val (maxTx, maxTy) = getMaxOffsets(containerSize, imageSize, newScale)
                                        offsetXState = (offsetXState + pan.x).coerceIn(-maxTx, maxTx)
                                        offsetYState = (offsetYState + pan.y).coerceIn(-maxTy, maxTy)
                                    }
                                },
                    ) {
                        if (isInitialized && containerSize.width > 0 && containerSize.height > 0) {
                            val imageSize = IntSize(bitmap.width, bitmap.height)
                            val (maxTx, maxTy) = getMaxOffsets(containerSize, imageSize, scale)
                            val clampedX = offsetXState.coerceIn(-maxTx, maxTx)
                            val clampedY = offsetYState.coerceIn(-maxTy, maxTy)

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cw = size.width
                                val ch = size.height
                                val iw = bitmap.width.toFloat()
                                val ih = bitmap.height.toFloat()
                                if (cw > 0f && ch > 0f && iw > 0f && ih > 0f) {
                                    val scaleBase = ViewportMath.calculateAspectFillScale(cw, ch, iw, ih)
                                    val ws = iw * scaleBase
                                    val hs = ih * scaleBase

                                    drawImage(
                                        image = bitmap,
                                        dstOffset =
                                            IntOffset(
                                                ((cw - ws * scale) / 2f + clampedX).toInt(),
                                                ((ch - hs * scale) / 2f + clampedY).toInt(),
                                            ),
                                        dstSize =
                                            IntSize(
                                                (ws * scale).toInt(),
                                                (hs * scale).toInt(),
                                            ),
                                    )
                                }
                            }
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

        Column(
            modifier = Modifier.weight(0.45f),
            verticalArrangement = Arrangement.spacedBy(BSE_SPACING_8),
        ) {
            CropHintItem(
                icon = Icons.Rounded.Pinch,
                title = stringResource(R.string.macropad_editor_bg_crop_zoom_hint_title),
                description = stringResource(R.string.macropad_editor_bg_crop_zoom_hint_desc),
                accentColor = accentColor,
            )
            CropHintItem(
                icon = Icons.Rounded.OpenWith,
                title = stringResource(R.string.macropad_editor_bg_crop_pan_hint_title),
                description = stringResource(R.string.macropad_editor_bg_crop_pan_hint_desc),
                accentColor = accentColor,
            )
        }
    }

    val initialPixelOffsetX = if (containerSize.width > 0) initialOffsetX * containerSize.width else 0f
    val initialPixelOffsetY = if (containerSize.height > 0) initialOffsetY * containerSize.height else 0f
    val hasCropChanges =
        isInitialized &&
            (
                kotlin.math.abs(scale - initialScale) > 0.001f ||
                    kotlin.math.abs(offsetXState - initialPixelOffsetX) > 0.5f ||
                    kotlin.math.abs(offsetYState - initialPixelOffsetY) > 0.5f
            )

    // ── Save Section ─────────────────────────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.macropad_editor_section_save),
        color = accentColor,
    )

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_bg_crop_save_title),
        description = stringResource(R.string.macropad_editor_bg_crop_save_desc),
        actionText = stringResource(R.string.gamepad_action_save),
        icon = Icons.Rounded.Save,
        pulseOnChanges = hasCropChanges,
        onClick = {
            val finalOffsetX = if (containerSize.width > 0) offsetXState / containerSize.width else 0f
            val finalOffsetY = if (containerSize.height > 0) offsetYState / containerSize.height else 0f
            onConfirmCrop(scale, finalOffsetX, finalOffsetY)
        },
    )
}

@Composable
private fun CropHintItem(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color,
) {
    val colors = LocalAppColors.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(8.dp))
                .border(1.dp, colors.subduedBorder, RoundedCornerShape(8.dp))
                .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(24.dp),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceSecondary,
            )
        }
    }
}
