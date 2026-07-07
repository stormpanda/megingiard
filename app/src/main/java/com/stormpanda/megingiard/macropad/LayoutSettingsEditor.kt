package com.stormpanda.megingiard.macropad

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Palette
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.ColorWheelPicker
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.AppDivider
import com.stormpanda.megingiard.ui.AppTextField
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

private const val TAG = "LayoutSettingsEditor"

@Composable
internal fun LayoutSettingsEditor(
    title: String,
    layoutId: String,
    initialName: String,
    initialButtonTextColor: ColorOption,
    initialButtonBorderColor: ColorOption,
    initialButtonBgColor: ColorOption,
    initialBackgroundImagePath: String?,
    initialUseAsMask: Boolean,
    accentColor: Color,
    existingNames: List<String>,
    onConfirm: (String, ColorOption, ColorOption, ColorOption, String?, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nameText by remember { mutableStateOf(initialName) }
    var textColorOption by remember { mutableStateOf(initialButtonTextColor) }
    var borderColorOption by remember { mutableStateOf(initialButtonBorderColor) }
    var bgColorOption by remember { mutableStateOf(initialButtonBgColor) }

    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentBgPath by remember { mutableStateOf(initialBackgroundImagePath) }
    var useAsMask by remember { mutableStateOf(initialUseAsMask) }

    var activeColorPickerTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }
    var activePaletteDialogTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }
    var showHelpMenu by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val normalizedName = nameText.trim()
    val isDuplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val hasError = normalizedName.isEmpty() || isDuplicate
    val isConfirmEnabled = !hasError && !isSaving

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImageUri = uri
            currentBgPath = null
        }
    }

    val recentColors by MacroPadSettings.recentColors.collectAsState()
    val globalAccentInt by SettingsManager.accentColor.collectAsState()
    val globalAccentColor = Color(globalAccentInt)

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
                    HelpIconButton(onClick = { showHelpMenu = true })
                    Spacer(Modifier.width(8.dp))
                    TextButton(

                    onClick = {
                        if (isConfirmEnabled) {
                            isSaving = true
                            scope.launch {
                                var bgChanged = false
                                val finalBgPath = withContext(Dispatchers.IO) {
                                    val uri = pendingImageUri
                                    if (uri != null) {
                                        val backgroundsDir = File(context.filesDir, "backgrounds")
                                        if (!backgroundsDir.exists()) {
                                            backgroundsDir.mkdirs()
                                        }
                                        val destFile = File(backgroundsDir, "bg_$layoutId")
                                        try {
                                            context.contentResolver.openInputStream(uri)?.use { input ->
                                                destFile.outputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            bgChanged = true
                                            "backgrounds/bg_$layoutId"
                                        } catch (e: Exception) {
                                            AppLog.e(TAG, "Failed to copy background image Uri $uri", e)
                                            null
                                        }
                                    } else if (currentBgPath == null) {
                                        val backgroundsDir = File(context.filesDir, "backgrounds")
                                        val destFile = File(backgroundsDir, "bg_$layoutId")
                                        if (destFile.exists()) {
                                            try {
                                                destFile.delete()
                                                bgChanged = true
                                            } catch (e: Exception) {
                                                AppLog.e(TAG, "Failed to delete background file", e)
                                            }
                                        }
                                        null
                                    } else {
                                        currentBgPath
                                    }
                                }
                                onConfirm(
                                    normalizedName,
                                    textColorOption,
                                    borderColorOption,
                                    bgColorOption,
                                    finalBgPath,
                                    useAsMask,
                                    bgChanged
                                )
                                isSaving = false
                            }
                        }
                    },
                    enabled = isConfirmEnabled
                ) {
                    Text(
                        text = stringResource(R.string.macropad_editor_done),
                        color = if (isConfirmEnabled) accentColor else colors.onSurfaceSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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

            // Layout Name
            AppTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text(stringResource(R.string.quick_menu_layout_name_hint), color = colors.onSurfaceSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = hasError,
                supportingText = {
                    when {
                        normalizedName.isEmpty() -> Text(stringResource(R.string.settings_name_error_empty))
                        isDuplicate -> Text(stringResource(R.string.settings_name_error_duplicate))
                    }
                },
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.layout_settings_bg_section_title), accentColor)
            Spacer(Modifier.height(12.dp))

            // Background Image Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.layout_settings_bg_image_title),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    val statusText = when {
                        pendingImageUri != null -> stringResource(R.string.layout_settings_bg_image_pending)
                        currentBgPath != null -> stringResource(R.string.layout_settings_bg_image_active)
                        else -> stringResource(R.string.layout_settings_bg_image_none)
                    }
                    Text(
                        text = statusText,
                        color = colors.onSurfaceSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pendingImageUri != null || currentBgPath != null) {
                        IconButton(
                            onClick = {
                                pendingImageUri = null
                                currentBgPath = null
                                useAsMask = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.layout_settings_bg_image_remove),
                                tint = colors.error
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        onClick = { launcher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text(
                            text = if (pendingImageUri != null || currentBgPath != null) {
                                stringResource(R.string.layout_settings_bg_image_change)
                            } else {
                                stringResource(R.string.layout_settings_bg_image_choose)
                            },
                            color = colors.onAccent
                        )
                    }
                }
            }

            if (pendingImageUri != null || currentBgPath != null) {
                Spacer(Modifier.height(16.dp))
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

            Spacer(Modifier.height(24.dp))
            AppDivider()
            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.layout_settings_colors_section_title), accentColor)
            Spacer(Modifier.height(16.dp))

            // Color Option Rows
            ColorPickerRow(
                label = stringResource(R.string.layout_settings_color_text),
                option = textColorOption,
                defaultNeutralColor = MP_AMBIENT_NEUTRAL_TEXT,
                globalAccentColor = globalAccentColor,
                onWheelClick = { activeColorPickerTarget = ColorPickerTarget.TEXT },
                onPaletteClick = { activePaletteDialogTarget = ColorPickerTarget.TEXT }
            )

            Spacer(Modifier.height(20.dp))

            ColorPickerRow(
                label = stringResource(R.string.layout_settings_color_border),
                option = borderColorOption,
                defaultNeutralColor = MP_AMBIENT_NEUTRAL_BORDER,
                globalAccentColor = globalAccentColor,
                onWheelClick = { activeColorPickerTarget = ColorPickerTarget.BORDER },
                onPaletteClick = { activePaletteDialogTarget = ColorPickerTarget.BORDER }
            )

            Spacer(Modifier.height(20.dp))

            ColorPickerRow(
                label = stringResource(R.string.layout_settings_color_bg),
                option = bgColorOption,
                defaultNeutralColor = MP_AMBIENT_NEUTRAL_BG,
                globalAccentColor = globalAccentColor,
                onWheelClick = { activeColorPickerTarget = ColorPickerTarget.BG },
                onPaletteClick = { activePaletteDialogTarget = ColorPickerTarget.BG }
            )

            Spacer(Modifier.height(40.dp))
        }
    }

    // Color Wheel overlays
        val activeWheelTarget = activeColorPickerTarget
        if (activeWheelTarget != null) {
            val initialColor = when (activeWheelTarget) {
                ColorPickerTarget.TEXT -> resolveColorOption(textColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_TEXT)
                ColorPickerTarget.BORDER -> resolveColorOption(borderColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_BORDER)
                ColorPickerTarget.BG -> resolveColorOption(bgColorOption, globalAccentColor, MP_AMBIENT_NEUTRAL_BG)
            }
            ColorWheelPicker(
                initialColor = initialColor,
                onColorSelected = { selectedColor ->
                    val customOpt = ColorOption.Custom(selectedColor.toArgb())
                    when (activeWheelTarget) {
                        ColorPickerTarget.TEXT -> textColorOption = customOpt
                        ColorPickerTarget.BORDER -> borderColorOption = customOpt
                        ColorPickerTarget.BG -> bgColorOption = customOpt
                    }
                    MacroPadSettings.addRecentColor(selectedColor.toArgb())
                    activeColorPickerTarget = null
                },
                onDismiss = { activeColorPickerTarget = null }
            )
        }

        // Palette Overlay Dialogs
        val activePaletteTarget = activePaletteDialogTarget
        if (activePaletteTarget != null) {
            val defaultNeutralColor = when (activePaletteTarget) {
                ColorPickerTarget.TEXT -> MP_AMBIENT_NEUTRAL_TEXT
                ColorPickerTarget.BORDER -> MP_AMBIENT_NEUTRAL_BORDER
                ColorPickerTarget.BG -> MP_AMBIENT_NEUTRAL_BG
            }
            QuickColorSelectionDialog(
                title = when (activePaletteTarget) {
                    ColorPickerTarget.TEXT -> stringResource(R.string.layout_settings_select_text_color)
                    ColorPickerTarget.BORDER -> stringResource(R.string.layout_settings_select_border_color)
                    ColorPickerTarget.BG -> stringResource(R.string.layout_settings_select_bg_color)
                },
                neutralColor = defaultNeutralColor,
                accentColor = globalAccentColor,
                recentColors = recentColors,
                onSelected = { opt ->
                    when (activePaletteTarget) {
                        ColorPickerTarget.TEXT -> textColorOption = opt
                        ColorPickerTarget.BORDER -> borderColorOption = opt
                        ColorPickerTarget.BG -> bgColorOption = opt
                    }
                    if (opt is ColorOption.Custom) {
                        MacroPadSettings.addRecentColor(opt.argb)
                    }
                    activePaletteDialogTarget = null
                },
                onDismiss = { activePaletteDialogTarget = null }
            )
        }

        LayoutSettingsHelpModal(
            visible = showHelpMenu,
            onDismiss = { showHelpMenu = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Components & Helpers
// ─────────────────────────────────────────────────────────────────────────────

private enum class ColorPickerTarget { TEXT, BORDER, BG }

@Composable
private fun ColorPickerRow(
    label: String,
    option: ColorOption,
    defaultNeutralColor: Color,
    globalAccentColor: Color,
    onWheelClick: () -> Unit,
    onPaletteClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            val previewColor = when (option) {
                ColorOption.Neutral -> defaultNeutralColor
                ColorOption.Accent -> globalAccentColor
                is ColorOption.Custom -> Color(option.argb)
            }
            // Circular color wheel button (click to open color wheel)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(1.dp, colors.accentBorder, CircleShape)
                    .clickable(onClick = onWheelClick)
            )

            Spacer(Modifier.width(12.dp))

            // Palette button (click to open quick select dialog)
            IconButton(
                onClick = onPaletteClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(colors.surfaceVariant, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = colors.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickColorSelectionDialog(
    title: String,
    neutralColor: Color,
    accentColor: Color,
    recentColors: List<Int>,
    onSelected: (ColorOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss)
            .blockPointerEvents(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(colors.surface, RoundedCornerShape(12.dp))
                .clickable(enabled = true, onClick = {})
                .padding(16.dp)
        ) {
            Text(
                text = title,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            // System Styles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Neutral option
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { onSelected(ColorOption.Neutral) }
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(neutralColor)
                            .border(1.dp, colors.divider, CircleShape)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.layout_settings_color_neutral),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Accent option
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { onSelected(ColorOption.Accent) }
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                            .border(1.dp, colors.divider, CircleShape)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.layout_settings_color_accent),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.layout_settings_recent_colors),
                color = colors.onSurfaceSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))

            if (recentColors.isEmpty()) {
                Text(
                    text = stringResource(R.string.layout_settings_no_recent_colors),
                    color = colors.onSurfaceSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                ) {
                    items(recentColors) { argb ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(1.dp, colors.divider, CircleShape)
                                .clickable { onSelected(ColorOption.Custom(argb)) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
            }
        }
    }
}

@Composable
private fun LayoutSettingsHelpModal(visible: Boolean, onDismiss: () -> Unit) {
    HelpModal(
        visible = visible,
        title = stringResource(R.string.help_layout_settings_title),
        onDismiss = onDismiss
    ) {
        HelpIntro(stringResource(R.string.help_layout_settings_intro))

        HelpSection(stringResource(R.string.help_layout_settings_sec_properties))
        HelpEntry(
            label = stringResource(R.string.help_layout_settings_name_title),
            description = stringResource(R.string.help_layout_settings_name_desc)
        )
        HelpEntry(
            label = stringResource(R.string.help_layout_settings_bg_title),
            description = stringResource(R.string.help_layout_settings_bg_desc)
        )
        HelpEntry(
            label = stringResource(R.string.help_layout_settings_mask_title),
            description = stringResource(R.string.help_layout_settings_mask_desc)
        )

        HelpSection(stringResource(R.string.help_layout_settings_sec_colors))
        HelpEntry(
            label = stringResource(R.string.help_layout_settings_colors_title),
            description = stringResource(R.string.help_layout_settings_colors_desc)
        )
        HelpEntry(
            label = stringResource(R.string.help_layout_settings_palette_title),
            description = stringResource(R.string.help_layout_settings_palette_desc)
        )
    }
}
