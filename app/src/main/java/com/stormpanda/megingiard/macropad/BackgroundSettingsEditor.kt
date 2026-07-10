package com.stormpanda.megingiard.macropad

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Pinch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.steamgriddb.SteamGridDbScrapeDialog
import com.stormpanda.megingiard.ui.FullScreenTopBar
import com.stormpanda.megingiard.ui.HelpEntry
import com.stormpanda.megingiard.ui.HelpIconButton
import com.stormpanda.megingiard.ui.HelpIntro
import com.stormpanda.megingiard.ui.HelpModal
import com.stormpanda.megingiard.ui.HelpSection
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.blockPointerEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "BackgroundSettingsEditor"


private const val BSE_PREVIEW_MODAL_WIDTH_FRACTION = 0.85f
private val BSE_PREVIEW_MODAL_CORNER_RADIUS = 12.dp
private const val BSE_PREVIEW_MODAL_BG_ALPHA = 0.6f
private val BSE_PREVIEW_IMAGE_ROUNDING = 8.dp
private val BSE_SPACING_16 = 16.dp
private const val BSE_CROP_MIN_SCALE = 1f
private const val BSE_CROP_MAX_SCALE = 5f

@Composable
internal fun BackgroundSettingsEditor(
    title: String,
    layoutId: String,
    initialBackgroundImagePath: String?,
    initialUseAsMask: Boolean,
    initialBgImageScale: Float = 1f,
    initialBgImageOffsetX: Float = 0f,
    initialBgImageOffsetY: Float = 0f,
    accentColor: Color,
    onConfirm: (bgImagePath: String?, useAsMask: Boolean, bgChanged: Boolean, bgScale: Float, bgOffsetX: Float, bgOffsetY: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val windowManager = remember { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentBgPath by remember { mutableStateOf(initialBackgroundImagePath) }
    var useAsMask by remember { mutableStateOf(initialUseAsMask) }

    var bgScale by remember { mutableFloatStateOf(initialBgImageScale) }
    var bgOffsetX by remember { mutableFloatStateOf(initialBgImageOffsetX) }
    var bgOffsetY by remember { mutableFloatStateOf(initialBgImageOffsetY) }

    val bounds = remember { windowManager.currentWindowMetrics.bounds }
    val aspectRatio = remember(bounds) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (h > 0f) w / h else 16f / 9f
    }

    var previewBitmap by remember(pendingImageUri, currentBgPath) { mutableStateOf<ImageBitmap?>(null) }
    var showPreviewModal by remember { mutableStateOf(false) }
    var showScrapeDialog by remember { mutableStateOf(false) }
    var showApiTokenMissingDialog by remember { mutableStateOf(false) }
    var showHelpMenu by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(pendingImageUri, currentBgPath) {
        if (pendingImageUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(pendingImageUri!!).use { input ->
                        val decoded = BitmapFactory.decodeStream(input)
                        previewBitmap = decoded?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to decode pending image uri $pendingImageUri", e)
                    previewBitmap = null
                }
            }
        } else if (currentBgPath != null) {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(context.filesDir, currentBgPath!!)
                    if (file.exists()) {
                        val decoded = BitmapFactory.decodeFile(file.absolutePath)
                        previewBitmap = decoded?.asImageBitmap()
                    } else {
                        previewBitmap = null
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to decode current background image $currentBgPath", e)
                    previewBitmap = null
                }
            }
        } else {
            previewBitmap = null
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImageUri = uri
            currentBgPath = null
            bgScale = 1f
            bgOffsetX = 0f
            bgOffsetY = 0f
        }
    }

    BackHandler(onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().blockPointerEvents(),
            containerColor = colors.appBackground,
            topBar = {
                FullScreenTopBar(
                    title = title,
                    onDismiss = onDismiss,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                if (!isSaving) {
                                    isSaving = true
                                    scope.launch {
                                        var bgChanged = false
                                        val finalBgPath = withContext(Dispatchers.IO) {
                                            val pending = pendingImageUri
                                            if (pending != null) {
                                                bgChanged = true
                                                val backgroundsDir = File(context.filesDir, "backgrounds")
                                                if (!backgroundsDir.exists()) {
                                                    backgroundsDir.mkdirs()
                                                }
                                                val destFile = File(backgroundsDir, "bg_$layoutId")
                                                try {
                                                    context.contentResolver.openInputStream(pending).use { input ->
                                                        destFile.outputStream().use { output ->
                                                            input?.copyTo(output)
                                                        }
                                                    }
                                                    "backgrounds/bg_$layoutId"
                                                } catch (e: Exception) {
                                                    AppLog.e(TAG, "Failed to copy background image Uri $pending", e)
                                                    null
                                                }
                                            } else if (currentBgPath == null && initialBackgroundImagePath != null) {
                                                bgChanged = true
                                                val backgroundsDir = File(context.filesDir, "backgrounds")
                                                val destFile = File(backgroundsDir, "bg_$layoutId")
                                                if (destFile.exists()) {
                                                    try {
                                                        destFile.delete()
                                                    } catch (e: Exception) {
                                                        AppLog.e(TAG, "Failed to delete background file", e)
                                                    }
                                                }
                                                null
                                            } else {
                                                currentBgPath
                                            }
                                        }
                                        onConfirm(finalBgPath, useAsMask, bgChanged, bgScale, bgOffsetX, bgOffsetY)
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSaving
                        ) {
                            Text(
                                text = stringResource(R.string.macropad_editor_done),
                                color = if (!isSaving) accentColor else colors.onSurfaceSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        HelpIconButton(onClick = { showHelpMenu = true })
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(16.dp))

                val configuration = LocalConfiguration.current
                val screenHeight = configuration.screenHeightDp.dp
                val previewHeight = screenHeight * 0.4f

                // 1. Preview Frame
                Box(
                    modifier = Modifier
                        .height(previewHeight)
                        .aspectRatio(aspectRatio)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceVariant)
                        .border(1.dp, colors.divider.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = previewBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(R.string.layout_settings_bg_image_preview_desc),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = stringResource(R.string.layout_settings_bg_image_none),
                            tint = colors.onSurfaceSecondary.copy(alpha = 0.38f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 2. Action row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Crop button
                    Button(
                        onClick = { showPreviewModal = true },
                        enabled = previewBitmap != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceVariant,
                            contentColor = colors.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.layout_settings_bg_image_crop), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    // Delete button
                    Button(
                        onClick = {
                            pendingImageUri = null
                            currentBgPath = null
                            useAsMask = false
                            bgScale = 1f
                            bgOffsetX = 0f
                            bgOffsetY = 0f
                        },
                        enabled = previewBitmap != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.error.copy(alpha = 0.15f),
                            contentColor = colors.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.macropad_editor_delete_button), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    // Choose / Change Image
                    Button(
                        onClick = { launcher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = colors.onAccent
                        ),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (previewBitmap != null) {
                                stringResource(R.string.layout_settings_bg_image_change)
                            } else {
                                stringResource(R.string.layout_settings_bg_image_choose)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Scrape from SteamGridDB
                    Button(
                        onClick = {
                            val token = SettingsManager.steamGridDbApiToken.value
                            if (token.isBlank()) {
                                showApiTokenMissingDialog = true
                            } else {
                                showScrapeDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = colors.onAccent
                        ),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.layout_settings_bg_image_scrape), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 3. Mask option
                if (pendingImageUri != null || currentBgPath != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.layout_settings_bg_image_use_as_mask),
                                color = colors.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.layout_settings_bg_image_use_as_mask_desc),
                                color = colors.onSurfaceSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = useAsMask,
                            onCheckedChange = { useAsMask = it }
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }

        BackgroundSettingsHelpModal(
            visible = showHelpMenu,
            onDismiss = { showHelpMenu = false }
        )

        if (showPreviewModal && previewBitmap != null) {
            ImageCropDialog(
                bitmap = previewBitmap!!,
                aspectRatio = aspectRatio,
                initialScale = bgScale,
                initialOffsetX = bgOffsetX,
                initialOffsetY = bgOffsetY,
                onConfirmCrop = { scale, ox, oy ->
                    bgScale = scale
                    bgOffsetX = ox
                    bgOffsetY = oy
                    showPreviewModal = false
                },
                onDismiss = { showPreviewModal = false }
            )
        }

        if (showScrapeDialog) {
            SteamGridDbScrapeDialog(
                initialSearchQuery = "",
                onImageSelected = { uri ->
                    pendingImageUri = uri
                    currentBgPath = null
                    bgScale = 1f
                    bgOffsetX = 0f
                    bgOffsetY = 0f
                },
                onDismiss = { showScrapeDialog = false },
                accentColor = accentColor
            )
        }

        if (showApiTokenMissingDialog) {
            AlertDialog(
                onDismissRequest = { showApiTokenMissingDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.steamgriddb_token_missing_title),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.steamgriddb_token_missing_message),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showApiTokenMissingDialog = false
                            AppStateManager.setGlobalSettingsOpen(true)
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.steamgriddb_token_missing_go_settings),
                            color = colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApiTokenMissingDialog = false }) {
                        Text(
                            text = stringResource(R.string.settings_color_cancel),
                            color = colors.onSurfaceSecondary
                        )
                    }
                },
                containerColor = colors.surface,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

private fun getMaxOffsets(
    containerSize: IntSize,
    imageSize: IntSize,
    scale: Float
): Pair<Float, Float> {
    if (containerSize.width <= 0 || containerSize.height <= 0 || imageSize.width <= 0 || imageSize.height <= 0) {
        return 0f to 0f
    }
    val cw = containerSize.width.toFloat()
    val ch = containerSize.height.toFloat()
    val iw = imageSize.width.toFloat()
    val ih = imageSize.height.toFloat()

    val scaleBase = maxOf(cw / iw, ch / ih)
    val wFull = iw * scaleBase * scale
    val hFull = ih * scaleBase * scale

    val maxTx = ((wFull - cw) / 2f).coerceAtLeast(0f)
    val maxTy = ((hFull - ch) / 2f).coerceAtLeast(0f)

    return maxTx to maxTy
}

@Composable
private fun ImageCropDialog(
    bitmap: ImageBitmap,
    aspectRatio: Float,
    initialScale: Float,
    initialOffsetX: Float,
    initialOffsetY: Float,
    onConfirmCrop: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    onDismiss: () -> Unit
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
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pinchIconAlpha"
    )

    LaunchedEffect(containerSize) {
        if (containerSize.width > 0 && containerSize.height > 0 && !isInitialized) {
            offsetXState = initialOffsetX * containerSize.width
            offsetYState = initialOffsetY * containerSize.height
            isInitialized = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = BSE_PREVIEW_MODAL_BG_ALPHA))
            .clickable(onClick = onDismiss)
            .blockPointerEvents(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(BSE_PREVIEW_MODAL_WIDTH_FRACTION)
                .background(colors.surface, RoundedCornerShape(BSE_PREVIEW_MODAL_CORNER_RADIUS))
                .clickable(enabled = true, onClick = {})
                .padding(BSE_SPACING_16),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.macropad_editor_cancel),
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = stringResource(R.string.layout_settings_crop_image_title),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                TextButton(
                    onClick = {
                        val finalOffsetX = if (containerSize.width > 0) offsetXState / containerSize.width else 0f
                        val finalOffsetY = if (containerSize.height > 0) offsetYState / containerSize.height else 0f
                        onConfirmCrop(scale, finalOffsetX, finalOffsetY)
                    }
                ) {
                    Text(
                        text = stringResource(R.string.macropad_editor_done),
                        color = colors.accent,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(BSE_SPACING_16))

            Box(
                modifier = Modifier
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
                contentAlignment = Alignment.Center
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
                            val scaleBase = maxOf(cw / iw, ch / ih)
                            val ws = iw * scaleBase
                            val hs = ih * scaleBase
                            
                            drawImage(
                                image = bitmap,
                                dstOffset = IntOffset(
                                    ((cw - ws * scale) / 2f + clampedX).toInt(),
                                    ((ch - hs * scale) / 2f + clampedY).toInt()
                                ),
                                dstSize = IntSize(
                                    (ws * scale).toInt(),
                                    (hs * scale).toInt()
                                )
                            )
                        }
                    }
                }

                if (!hasInteracted) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Pinch,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer {
                                    this.alpha = alpha
                                }
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.layout_settings_crop_image_instructions),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun BackgroundSettingsHelpModal(visible: Boolean, onDismiss: () -> Unit) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.layout_settings_bg_section_title),
        onDismiss = onDismiss
    ) {
        HelpIntro(stringResource(R.string.help_layout_settings_intro))

        HelpSection(stringResource(R.string.help_layout_settings_sec_properties))
        HelpEntry(
            label = stringResource(R.string.help_layout_settings_bg_title),
            description = stringResource(R.string.help_layout_settings_bg_desc)
        )
        HelpEntry(
            label = stringResource(R.string.help_layout_settings_mask_title),
            description = stringResource(R.string.help_layout_settings_mask_desc)
        )
    }
}
