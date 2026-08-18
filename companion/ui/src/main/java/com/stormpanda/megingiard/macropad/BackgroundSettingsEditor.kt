package com.stormpanda.megingiard.macropad

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.BitmapUtils
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.math.ViewportMath
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppModalDialog
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadConfirmDialog
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import com.stormpanda.megingiard.ui.firstDeckItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val TAG = "BackgroundSettingsEditor"

private val BSE_PREVIEW_IMAGE_ROUNDING = 8.dp
private val BSE_BORDER_WIDTH_1 = 1.dp
private val BSE_ICON_SIZE_48 = 48.dp
private val BSE_ICON_SIZE_40 = 40.dp
private val BSE_ICON_SIZE_72 = 72.dp
private val BSE_SPACING_8 = 8.dp
private val BSE_SPACING_12 = 12.dp
private val BSE_SPACING_16 = 16.dp
private val BSE_SPACING_40 = 40.dp

private const val BSE_PREVIEW_MODAL_WIDTH_FRACTION = 0.95f
private val BSE_PREVIEW_MODAL_CORNER_RADIUS = 12.dp
private const val BSE_PREVIEW_MODAL_BG_ALPHA = 0.7f

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
    var showPreviewModal by remember { mutableStateOf(false) }
    var showApiTokenMissingDialog by remember { mutableStateOf(false) }
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
                showApiTokenMissingDialog = true
            } else {
                onOpenScrape()
            }
        },
    )

    GamepadActionCard(
        title = stringResource(R.string.layout_settings_bg_image_browse_local),
        description = stringResource(R.string.macropad_editor_bg_storage_desc),
        actionText = stringResource(R.string.gamepad_action_browse),
        icon = Icons.Rounded.Folder,
        onClick = { BackgroundPickerManager.requestImagePicker() },
    )

    if (previewBitmap != null) {
        GamepadActionCard(
            title = stringResource(R.string.layout_settings_bg_image_none),
            description = stringResource(R.string.macropad_editor_bg_storage_desc),
            actionText = stringResource(R.string.gamepad_action_clear),
            isDestructive = true,
            icon = Icons.Rounded.Delete,
            onClick = {
                pendingImageUri = null
                currentBgPath = null
                previewBitmap = null
                bgScale = 1f
                bgOffsetX = 0f
                bgOffsetY = 0f
            },
        )

        GamepadSectionHeader(
            text = stringResource(R.string.layout_settings_bg_image_crop),
            color = accentColor,
        )

        GamepadActionCard(
            title = stringResource(R.string.layout_settings_bg_image_crop),
            description = stringResource(R.string.macropad_editor_appearance_desc),
            actionText = stringResource(R.string.gamepad_action_crop),
            icon = Icons.Rounded.Crop,
            onClick = { showPreviewModal = true },
        )

        GamepadStepperCard(
            title = stringResource(R.string.layout_settings_bg_image_dimming_title),
            description = stringResource(R.string.help_bg_settings_dimming_desc),
            valueText = "${(bgImageDim * 100).roundToInt()}%",
            icon = Icons.Rounded.BrightnessMedium,
            onDecrement = {
                val newVal = (bgImageDim - 0.05f).coerceIn(0f, 0.95f)
                bgImageDim = (newVal * 100).roundToInt() / 100f
            },
            onIncrement = {
                val newVal = (bgImageDim + 0.05f).coerceIn(0f, 0.95f)
                bgImageDim = (newVal * 100).roundToInt() / 100f
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

    GamepadActionCard(
        title = stringResource(R.string.macropad_editor_done),
        description = stringResource(R.string.macropad_editor_appearance_desc),
        actionText = stringResource(R.string.macropad_editor_done),
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

    if (showPreviewModal && previewBitmap != null) {
        ImageCropDialog(
            bitmap = previewBitmap!!,
            aspectRatio = BSE_BOTTOM_SCREEN_ASPECT_RATIO,
            initialScale = bgScale,
            initialOffsetX = bgOffsetX,
            initialOffsetY = bgOffsetY,
            onConfirmCrop = { scale, ox, oy ->
                bgScale = scale
                bgOffsetX = ox
                bgOffsetY = oy
                showPreviewModal = false
            },
            onDismiss = { showPreviewModal = false },
        )
    }

    if (showApiTokenMissingDialog) {
        GamepadConfirmDialog(
            title = stringResource(R.string.steamgriddb_token_missing_title),
            message = stringResource(R.string.steamgriddb_token_missing_message),
            confirmText = stringResource(R.string.steamgriddb_token_missing_go_settings),
            cancelText = stringResource(R.string.settings_color_cancel),
            onConfirm = {
                showApiTokenMissingDialog = false
                AppStateManager.setGlobalSettingsOpen(true)
            },
            onDismiss = { showApiTokenMissingDialog = false },
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
private fun ImageCropDialog(
    bitmap: ImageBitmap,
    aspectRatio: Float,
    initialScale: Float,
    initialOffsetX: Float,
    initialOffsetY: Float,
    onConfirmCrop: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current

    var scale by remember { mutableFloatStateOf(initialScale) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var offsetXState by remember { mutableFloatStateOf(0f) }
    var offsetYState by remember { mutableFloatStateOf(0f) }
    var isInitialized by remember { mutableStateOf(false) }
    var hasInteracted by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pinchIconTransition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pinchIconAlpha",
    )

    LaunchedEffect(containerSize) {
        if (containerSize.width > 0 && containerSize.height > 0 && !isInitialized) {
            offsetXState = initialOffsetX * containerSize.width
            offsetYState = initialOffsetY * containerSize.height
            isInitialized = true
        }
    }

    AppModalDialog(
        onDismiss = onDismiss,
        widthFraction = BSE_PREVIEW_MODAL_WIDTH_FRACTION,
        cornerRadius = BSE_PREVIEW_MODAL_CORNER_RADIUS,
        contentPadding = BSE_SPACING_16,
        scrimAlpha = BSE_PREVIEW_MODAL_BG_ALPHA,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.macropad_editor_cancel),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = stringResource(R.string.layout_settings_crop_image_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )

            TextButton(
                onClick = {
                    val finalOffsetX = if (containerSize.width > 0) offsetXState / containerSize.width else 0f
                    val finalOffsetY = if (containerSize.height > 0) offsetYState / containerSize.height else 0f
                    onConfirmCrop(scale, finalOffsetX, finalOffsetY)
                },
            ) {
                Text(
                    text = stringResource(R.string.macropad_editor_done),
                    color = colors.accent,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(BSE_SPACING_16))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(BSE_PREVIEW_IMAGE_ROUNDING))
                    .clipToBounds()
                    .background(Color.Black)
                    .onSizeChanged { containerSize = it }
                    .pointerInput(bitmap, isInitialized) {
                        if (!isInitialized) return@pointerInput
                        detectTransformGestures { _, pan, zoom, _ ->
                            hasInteracted = true
                            val imageSize = IntSize(bitmap.width, bitmap.height)
                            val newScale = (scale * zoom).coerceIn(BSE_CROP_MIN_SCALE, BSE_CROP_MAX_SCALE)
                            scale = newScale

                            val (maxTx, maxTy) = getMaxOffsets(containerSize, imageSize, newScale)
                            offsetXState = (offsetXState + pan.x).coerceIn(-maxTx, maxTx)
                            offsetYState = (offsetYState + pan.y).coerceIn(-maxTy, maxTy)
                        }
                    },
            contentAlignment = Alignment.Center,
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

            if (!hasInteracted) {
                Box(
                    modifier =
                        Modifier
                            .size(BSE_ICON_SIZE_72)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pinch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier =
                            Modifier
                                .size(BSE_ICON_SIZE_40)
                                .graphicsLayer {
                                    this.alpha = alpha
                                },
                    )
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = BSE_SPACING_12)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GamepadStepperCard(
                title = stringResource(R.string.gamepad_action_zoom),
                description = stringResource(R.string.layout_settings_crop_image_instructions),
                valueText = "${((scale * 10f).roundToInt() / 10f)}x",
                icon = Icons.Rounded.ZoomIn,
                onDecrement = {
                    hasInteracted = true
                    scale = ((scale - 0.1f) * 10f).roundToInt() / 10f
                    scale = scale.coerceIn(BSE_CROP_MIN_SCALE, BSE_CROP_MAX_SCALE)
                },
                onIncrement = {
                    hasInteracted = true
                    scale = ((scale + 0.1f) * 10f).roundToInt() / 10f
                    scale = scale.coerceIn(BSE_CROP_MIN_SCALE, BSE_CROP_MAX_SCALE)
                },
            )

            GamepadActionCard(
                title = stringResource(R.string.gamepad_action_reset),
                description = stringResource(R.string.help_bg_settings_crop_desc),
                actionText = stringResource(R.string.gamepad_action_reset),
                icon = Icons.Rounded.Refresh,
                onClick = {
                    scale = 1.0f
                    offsetXState = 0f
                    offsetYState = 0f
                    hasInteracted = true
                },
            )
        }
    }
}

@Composable
private fun BackgroundSettingsHelpModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.layout_settings_bg_section_title),
        onDismiss = onDismiss,
    ) {
        HelpIntro(stringResource(R.string.help_bg_settings_intro))

        HelpSection(stringResource(R.string.help_layout_settings_sec_properties))
        HelpEntry(
            label = stringResource(R.string.help_layout_settings_bg_title),
            description = stringResource(R.string.help_layout_settings_bg_desc),
        )
        HelpEntry(
            label = stringResource(R.string.help_bg_settings_dimming_title),
            description = stringResource(R.string.help_bg_settings_dimming_desc),
        )
        HelpEntry(
            label = stringResource(R.string.help_bg_settings_crop_title),
            description = stringResource(R.string.help_bg_settings_crop_desc),
        )
        HelpEntry(
            label = stringResource(R.string.help_layout_settings_mask_title),
            description = stringResource(R.string.help_layout_settings_mask_desc),
        )
    }
}
