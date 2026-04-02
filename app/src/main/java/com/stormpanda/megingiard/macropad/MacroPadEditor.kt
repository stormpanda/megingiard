package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val ED_TOP_BAR_HEIGHT  = 56.dp
private val ED_PADDING         = 16.dp
private val ED_ITEM_PADDING    = 12.dp
private val ED_BUTTON_UNIT_DP  = 60.dp   // 1.0 (1×1) = this size on the pad canvas
private val ED_BTN_SQUARE_RADIUS        = 4.dp
private const val ED_BTN_DISABLED_ALPHA = 0.38f

// Minimum fraction distance from pad edge for button centres
private const val ED_EDGE_MARGIN = 0.05f

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen MacroPad layout editor.
 *
 * Opened from [MacroPadToolSettings]. Allows the user to:
 * - Create, rename, and delete profiles
 * - Add, configure, reposition, and delete buttons
 * - Toggle the trackpoint area
 *
 * All changes are persisted immediately via [MacroPadState].
 *
 * @param onDone  Called when the user taps "Done" to close the editor.
 */
@Composable
fun MacroPadEditor(onDone: () -> Unit) {
    val context     = LocalContext.current
    val profiles    by MacroPadState.profiles.collectAsState()
    val activeId    by MacroPadState.activeProfileId.collectAsState()
    val colors      = LocalAppColors.current

    // Stop all uinput virtual devices while the editor is open.
    // keyinjector_arm64 registers as a hardware keyboard via uinput, which causes
    // Android to suppress the soft IME — making text fields un-typeable.
    // We restart all injectors when the editor is dismissed (user returns to use mode).
    DisposableEffect(Unit) {
        KeyInjector.stop()
        GamepadInjector.stop()
        MouseInjector.stop()
        onDispose {
            val ap = MacroPadState.activeProfile.value
            CoroutineScope(Dispatchers.IO).launch {
                if (ap?.enableKeyboard != false) KeyInjector.start(context)
                if (ap?.enableGamepad != false) GamepadInjector.start(context)
                if (ap?.enableMouse != false) MouseInjector.start(context)
            }
        }
    }

    val profile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    Scaffold(
        containerColor = colors.appBackground,
        topBar = {
            EditorTopBar(
                profiles    = profiles,
                activeId    = activeId,
                accentColor = colors.accent,
                onSelectProfile  = { MacroPadState.setActiveProfileId(it) },
                onNewProfile     = {
                    val newProfile = PadProfile(
                        id   = UUID.randomUUID().toString(),
                        name = it,
                    )
                    MacroPadState.addProfile(newProfile)
                },
                onRenameProfile  = { id, name -> MacroPadState.renameProfile(id, name) },
                onDeleteProfile  = { MacroPadState.deleteProfile(it) },
                onDone           = onDone,
            )
        }
    ) { innerPadding ->
        if (profile == null) {
            // No profile yet — show prompt
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = stringResource(R.string.macropad_no_profile),
                    color = colors.onSurfaceSecondary,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(ED_PADDING),
                )
            }
        } else {
            EditorBody(
                profile     = profile,
                accentColor = colors.accent,
                modifier    = Modifier.padding(innerPadding),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorTopBar(
    profiles:        List<PadProfile>,
    activeId:        String?,
    accentColor:     Color,
    onSelectProfile: (String) -> Unit,
    onNewProfile:    (String) -> Unit,
    onRenameProfile: (String, String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDone:          () -> Unit,
) {
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var showNewDialog       by remember { mutableStateOf(false) }
    var showRenameDialog    by remember { mutableStateOf(false) }
    var showDeleteConfirm   by remember { mutableStateOf(false) }

    val activeProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    val colors        = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ED_TOP_BAR_HEIGHT)
            .background(colors.surface)
            .padding(horizontal = ED_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Profile selector
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { profileMenuExpanded = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = activeProfile?.name ?: stringResource(R.string.macropad_editor_new_profile_name),
                    color    = colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceSecondary)
            }

            DropdownMenu(
                expanded          = profileMenuExpanded,
                onDismissRequest  = { profileMenuExpanded = false },
                modifier          = Modifier.background(colors.surface),
            ) {
                profiles.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text  = p.name,
                                color = if (p.id == activeId) accentColor else colors.onSurface,
                                fontSize = 14.sp,
                            )
                        },
                        onClick = {
                            onSelectProfile(p.id)
                            profileMenuExpanded = false
                        },
                    )
                }
                HorizontalDivider(color = colors.divider)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_macropad_new_profile), color = accentColor, fontSize = 14.sp) },
                    onClick = { profileMenuExpanded = false; showNewDialog = true },
                )
            }
        }

        // Rename & delete buttons (only when a profile exists)
        if (activeProfile != null) {
            IconButton(onClick = { showRenameDialog = true }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.macropad_editor_rename), tint = colors.onSurfaceSecondary)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.macropad_editor_delete_profile), tint = colors.onSurfaceSecondary)
            }
        }

        Spacer(Modifier.width(4.dp))

        // Done
        TextButton(onClick = onDone) {
            Text(stringResource(R.string.macropad_editor_done), color = accentColor, fontWeight = FontWeight.SemiBold)
        }
    }

    // ── Dialogs ──

    if (showNewDialog) {
        NameInputDialog(
            title         = stringResource(R.string.settings_macropad_new_profile),
            initialValue  = "",
            accentColor   = accentColor,
            onConfirm     = { onNewProfile(it); showNewDialog = false },
            onDismiss     = { showNewDialog = false },
        )
    }

    if (showRenameDialog && activeProfile != null) {
        NameInputDialog(
            title        = stringResource(R.string.macropad_editor_rename),
            initialValue = activeProfile.name,
            accentColor  = accentColor,
            onConfirm    = { onRenameProfile(activeProfile.id, it); showRenameDialog = false },
            onDismiss    = { showRenameDialog = false },
        )
    }

    if (showDeleteConfirm && activeProfile != null) {
        AlertDialog(
            containerColor = colors.surface,
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text(stringResource(R.string.macropad_editor_delete_profile), color = colors.onSurface) },
            text    = { Text(stringResource(R.string.macropad_editor_confirm_delete), color = colors.onSurfaceSecondary) },
            confirmButton = {
                TextButton(onClick = { onDeleteProfile(activeProfile.id); showDeleteConfirm = false }) {
                    Text(stringResource(R.string.macropad_editor_confirm), color = Color(0xFFCF6679))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Body
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorBody(
    profile:     PadProfile,
    accentColor: Color,
    modifier:    Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = ED_PADDING),
        verticalArrangement = Arrangement.spacedBy(ED_PADDING),
    ) {
        // Device checkboxes
        Column(modifier = Modifier.padding(horizontal = ED_PADDING)) {
            Text(
                text  = stringResource(R.string.macropad_editor_devices),
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            DeviceCheckboxRow(
                label       = stringResource(R.string.macropad_editor_device_keyboard),
                checked     = profile.enableKeyboard,
                accentColor = accentColor,
                onCheckedChange = {
                    MacroPadState.updateProfile(profile.copy(enableKeyboard = it))
                },
            )
            DeviceCheckboxRow(
                label       = stringResource(R.string.macropad_editor_device_gamepad),
                checked     = profile.enableGamepad,
                accentColor = accentColor,
                onCheckedChange = {
                    MacroPadState.updateProfile(profile.copy(enableGamepad = it))
                },
            )
            DeviceCheckboxRow(
                label       = stringResource(R.string.macropad_editor_device_mouse),
                checked     = profile.enableMouse,
                accentColor = accentColor,
                onCheckedChange = {
                    MacroPadState.updateProfile(profile.copy(enableMouse = it))
                },
            )
        }

        // Pad canvas — full width, no horizontal padding so it matches use-mode
        PadCanvas(profile = profile, accentColor = accentColor)

        Column(
            modifier = Modifier.padding(horizontal = ED_PADDING),
            verticalArrangement = Arrangement.spacedBy(ED_PADDING),
        ) {
            HorizontalDivider(color = colors.divider)

            // Toolbar: Add button
            EditorToolbar(profile = profile, accentColor = accentColor)

            HorizontalDivider(color = colors.divider)

            // Button list — tap to edit
            ButtonList(profile = profile, accentColor = accentColor)
        }
    }
}

@Composable
private fun DeviceCheckboxRow(
    label:           String,
    checked:         Boolean,
    accentColor:     Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = CheckboxDefaults.colors(
                checkedColor   = accentColor,
                checkmarkColor = colors.appBackground,
                uncheckedColor = colors.onSurfaceSecondary,
            ),
        )
        Text(label, color = colors.onSurface, fontSize = 14.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pad canvas — drag buttons to reposition
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PadCanvas(profile: PadProfile, accentColor: Color) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val colors     = LocalAppColors.current

    val padModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .border(1.dp, colors.accentBorder, RoundedCornerShape(4.dp))
        .clip(RoundedCornerShape(4.dp))
        .background(colors.surface)
        .onSizeChanged { canvasSize = it }

    Box(modifier = padModifier) {
        // Render each button as a draggable chip
        profile.buttons.forEach { btn ->
            DraggableButton(
                btn            = btn,
                canvasSize     = canvasSize,
                accentColor    = accentColor,
                enableKeyboard = profile.enableKeyboard,
                enableGamepad  = profile.enableGamepad,
                enableMouse    = profile.enableMouse,
                onPositionChanged = { nx, ny ->
                    MacroPadState.updateProfile(
                        profile.copy(
                            buttons = profile.buttons.map { b ->
                                if (b.id == btn.id) b.copy(posX = nx, posY = ny) else b
                            }
                        )
                    )
                },
            )
        }

    }
}

@Composable
private fun DraggableButton(
    btn:               PadButton,
    canvasSize:        IntSize,
    accentColor:       Color,
    enableKeyboard:    Boolean,
    enableGamepad:     Boolean,
    enableMouse:       Boolean,
    onPositionChanged: (Float, Float) -> Unit,
) {
    val colors = LocalAppColors.current
    // rememberUpdatedState lets the pointerInput closure (keyed only on btn.id +
    // canvasSize) see the live btn even though its lambda is NOT restarted when
    // btn.posX/posY change between drags.
    val currentBtn = rememberUpdatedState(btn)
    // Always call the latest onPositionChanged so PadCanvas's stale-profile
    // closure (captured by pointerInput) doesn't revert sibling button positions.
    val currentOnPositionChanged = rememberUpdatedState(onPositionChanged)
    // Anchor position captured at the moment the finger goes down.
    var startPosX by remember(btn.id) { mutableFloatStateOf(btn.posX) }
    var startPosY by remember(btn.id) { mutableFloatStateOf(btn.posY) }
    var dragOffsetX by remember(btn.id) { mutableFloatStateOf(0f) }
    var dragOffsetY by remember(btn.id) { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val isTrackpoint = btn.action is PadAction.TrackpointMove
    val isDeviceDisabled = when (btn.action) {
        is PadAction.KeyboardKey                 -> !enableKeyboard
        is PadAction.GamepadButton               -> !enableGamepad
        is PadAction.MouseButton,
        is PadAction.ScrollWheel,
        is PadAction.TrackpointMove,
        is PadAction.MouseLeftClick,
        is PadAction.MouseRightClick             -> !enableMouse
    }
    val tpMultiplier = if (isTrackpoint) (btn.action as PadAction.TrackpointMove).size.multiplier else 1f
    val chipWidthPx  = with(density) {
        if (isTrackpoint) (ED_BUTTON_UNIT_DP * tpMultiplier).toPx()
        else (ED_BUTTON_UNIT_DP * btn.buttonSize.cols).toPx()
    }
    val chipHeightPx = with(density) {
        if (isTrackpoint) (ED_BUTTON_UNIT_DP * tpMultiplier).toPx()
        else (ED_BUTTON_UNIT_DP * btn.buttonSize.rows).toPx()
    }

    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
    val h = canvasSize.height.toFloat().coerceAtLeast(1f)

    // Top-left position in canvas pixels (centre adjusted by half-chip)
    val left = btn.posX * w - chipWidthPx / 2f
    val top  = btn.posY * h - chipHeightPx / 2f

    val chipShape = if (isTrackpoint) CircleShape else when (btn.buttonShape) {
        ButtonShape.SQUARE -> RoundedCornerShape(ED_BTN_SQUARE_RADIUS)
        ButtonShape.CIRCLE -> when (btn.buttonSize) {
            ButtonSize.SIZE_2X2                      -> CircleShape
            ButtonSize.SIZE_2X1, ButtonSize.SIZE_1X2 -> RoundedCornerShape(percent = 50)
            ButtonSize.SIZE_1X1                      -> CircleShape
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .width(if (isTrackpoint) ED_BUTTON_UNIT_DP * tpMultiplier else ED_BUTTON_UNIT_DP * btn.buttonSize.cols)
            .height(if (isTrackpoint) ED_BUTTON_UNIT_DP * tpMultiplier else ED_BUTTON_UNIT_DP * btn.buttonSize.rows)
            .drawWithContent {
                if (isDeviceDisabled) {
                    val p = Paint().apply {
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                        this.alpha = ED_BTN_DISABLED_ALPHA
                    }
                    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), p)
                    drawContent()
                    drawContext.canvas.restore()
                } else {
                    drawContent()
                }
            }
            .clip(chipShape)
            .background(accentColor.copy(alpha = 0.25f))
            .border(1.dp, accentColor, chipShape)
            .pointerInput(btn.id, canvasSize) {
                detectDragGestures(
                    onDragStart = {
                        // Capture the current (live) position as anchor so the
                        // accumulated delta is always relative to this drag's start.
                        startPosX = currentBtn.value.posX
                        startPosY = currentBtn.value.posY
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        dragOffsetX += drag.x
                        dragOffsetY += drag.y
                        currentOnPositionChanged.value(
                            (startPosX + dragOffsetX / w).coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN),
                            (startPosY + dragOffsetY / h).coerceIn(ED_EDGE_MARGIN, 1f - ED_EDGE_MARGIN),
                        )
                    },
                )
            }
    ) {
        if (btn.action is PadAction.TrackpointMove) {
            Text("●", color = accentColor, fontSize = 14.sp)
        } else if (btn.action is PadAction.ScrollWheel) {
            // Show mini scroll icon in editor chip
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(Icons.Filled.KeyboardArrowUp,   contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(14.dp))
                Icon(Icons.Filled.KeyboardArrowUp,   contentDescription = null, tint = colors.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.height(2.dp))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(14.dp))
            }
        } else {
            Text(btn.label, color = colors.onSurface, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Toolbar: add button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorToolbar(profile: PadProfile, accentColor: Color) {
    var showButtonDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ED_ITEM_PADDING),
    ) {
        // Add Button
        EditorActionChip(
            label       = stringResource(R.string.macropad_editor_add_button),
            icon        = Icons.Filled.Add,
            accentColor = accentColor,
            onClick     = { showButtonDialog = true },
            modifier    = Modifier.weight(1f),
        )
    }

    if (showButtonDialog) {
        ButtonEditDialog(
            button         = null,  // null = new button
            accentColor    = accentColor,
            enableKeyboard = profile.enableKeyboard,
            enableGamepad  = profile.enableGamepad,
            enableMouse    = profile.enableMouse,
            onConfirm      = { newBtn ->
                MacroPadState.updateProfile(
                    profile.copy(buttons = profile.buttons + newBtn)
                )
                showButtonDialog = false
            },
            onDismiss      = { showButtonDialog = false },
        )
    }
}

@Composable
private fun EditorActionChip(
    label:       String,
    icon:        ImageVector,
    accentColor: Color,
    onClick:     () -> Unit,
    modifier:    Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = accentColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Button list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ButtonList(profile: PadProfile, accentColor: Color) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (profile.buttons.isEmpty()) {
            Text(
                text     = stringResource(R.string.macropad_editor_add_button),
                color    = colors.onSurfaceSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        profile.buttons.forEach { btn ->
            ButtonListItem(
                btn            = btn,
                accentColor    = accentColor,
                enableKeyboard = profile.enableKeyboard,
                enableGamepad  = profile.enableGamepad,
                enableMouse    = profile.enableMouse,
                onUpdate       = { updated ->
                    MacroPadState.updateProfile(
                        profile.copy(buttons = profile.buttons.map { if (it.id == btn.id) updated else it })
                    )
                },
                onDelete       = {
                    MacroPadState.updateProfile(
                        profile.copy(buttons = profile.buttons.filter { it.id != btn.id })
                    )
                },
            )
            HorizontalDivider(color = colors.divider)
        }
    }
}

@Composable
private fun ButtonListItem(
    btn:            PadButton,
    accentColor:    Color,
    enableKeyboard: Boolean,
    enableGamepad:  Boolean,
    enableMouse:    Boolean,
    onUpdate:       (PadButton) -> Unit,
    onDelete:       () -> Unit,
) {
    var showEdit    by remember { mutableStateOf(false) }
    var showDelete  by remember { mutableStateOf(false) }
    val colors      = LocalAppColors.current

    val isTrackpoint = btn.action is PadAction.TrackpointMove
    val isDeviceDisabled = when (btn.action) {
        is PadAction.KeyboardKey                 -> !enableKeyboard
        is PadAction.GamepadButton               -> !enableGamepad
        is PadAction.MouseButton,
        is PadAction.ScrollWheel,
        is PadAction.TrackpointMove,
        is PadAction.MouseLeftClick,
        is PadAction.MouseRightClick             -> !enableMouse
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDeviceDisabled) 0.38f else 1f)
            .clickable { showEdit = true }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shape indicator
        val chipShape = if (isTrackpoint || btn.buttonShape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(4.dp)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(chipShape)
                .background(accentColor.copy(alpha = 0.2f))
                .border(1.dp, accentColor, chipShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isTrackpoint) {
                Text("●", color = colors.onSurface, fontSize = 10.sp)
            } else {
                Text(btn.label.take(2), color = colors.onSurface, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (isTrackpoint) {
                val sizeLabel = when ((btn.action as PadAction.TrackpointMove).size) {
                    TrackpointSize.SMALL  -> stringResource(R.string.macropad_trackpoint_size_small)
                    TrackpointSize.MEDIUM -> stringResource(R.string.macropad_trackpoint_size_medium)
                    TrackpointSize.LARGE  -> stringResource(R.string.macropad_trackpoint_size_large)
                }
                Text(stringResource(R.string.macropad_action_trackpoint), color = colors.onSurface, fontSize = 14.sp, maxLines = 1)
                Text(sizeLabel, color = colors.onSurfaceSecondary, fontSize = 12.sp, maxLines = 1)
            } else {
                Text(btn.label, color = colors.onSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(btn.action.displayLabel(), color = colors.onSurfaceSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        IconButton(onClick = { showDelete = true }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.macropad_editor_delete_button), tint = colors.onSurfaceSecondary)
        }
    }

    if (showEdit) {
        ButtonEditDialog(
            button         = btn,
            accentColor    = accentColor,
            enableKeyboard = enableKeyboard,
            enableGamepad  = enableGamepad,
            enableMouse    = enableMouse,
            onConfirm      = { onUpdate(it); showEdit = false },
            onDismiss      = { showEdit = false },
        )
    }

    if (showDelete) {
        AlertDialog(
            containerColor   = colors.surface,
            onDismissRequest = { showDelete = false },
            title   = { Text(stringResource(R.string.macropad_editor_delete_button), color = colors.onSurface) },
            text    = {
                Text(
                    if (isTrackpoint) stringResource(R.string.macropad_action_trackpoint) else btn.label,
                    color = colors.onSurfaceSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDelete = false }) {
                    Text(stringResource(R.string.macropad_editor_confirm), color = Color(0xFFCF6679))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Button Edit Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ButtonEditDialog(
    button:         PadButton?,    // null → create new
    accentColor:    Color,
    enableKeyboard: Boolean = true,
    enableGamepad:  Boolean = true,
    enableMouse:    Boolean = true,
    onConfirm:      (PadButton) -> Unit,
    onDismiss:      () -> Unit,
) {
    var label         by remember { mutableStateOf(button?.label ?: "") }
    var buttonShape   by remember { mutableStateOf(button?.buttonShape ?: ButtonShape.CIRCLE) }
    var buttonSize    by remember { mutableStateOf(button?.buttonSize ?: ButtonSize.SIZE_1X1) }
    var showSizeMenu  by remember { mutableStateOf(false) }
    var action        by remember { mutableStateOf(button?.action ?: PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space")) }
    val colors        = LocalAppColors.current

    fun onActionChanged(newAction: PadAction) {
        action = newAction
        if (newAction is PadAction.ScrollWheel) {
            buttonSize = ButtonSize.SIZE_1X2
            label = ""
        }
        if (newAction is PadAction.TrackpointMove) {
            label = ""
            buttonShape = ButtonShape.CIRCLE
        }
    }

    val isConfirmEnabled = label.isNotBlank() || action is PadAction.ScrollWheel || action is PadAction.TrackpointMove

    AlertDialog(
        containerColor   = colors.surface,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text  = if (button == null) stringResource(R.string.macropad_editor_add_button)
                        else if (button.action is PadAction.TrackpointMove) stringResource(R.string.macropad_action_trackpoint)
                        else button.label,
                color = colors.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Label input and shape — hidden for ScrollWheel and TrackpointMove
                if (action !is PadAction.ScrollWheel && action !is PadAction.TrackpointMove) {
                    OutlinedTextField(
                        value         = label,
                        onValueChange = { label = it },
                        label         = { Text(stringResource(R.string.macropad_editor_button_label), color = colors.onSurfaceSecondary) },
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = accentColor,
                            unfocusedBorderColor = colors.accentBorder,
                            focusedTextColor     = colors.onSurface,
                            unfocusedTextColor   = colors.onSurface,
                            cursorColor          = accentColor,
                        ),
                    )

                    SectionLabel(stringResource(R.string.macropad_editor_button_shape), accentColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ButtonShape.entries.forEach { shape ->
                            val selected = shape == buttonShape
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(if (shape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(8.dp))
                                    .background(if (selected) accentColor.copy(alpha = 0.3f) else colors.surface)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) accentColor else colors.accentBorder,
                                        shape = if (shape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(8.dp),
                                    )
                                    .clickable { buttonShape = shape },
                            ) {
                                Text(
                                    text  = if (shape == ButtonShape.CIRCLE)
                                        stringResource(R.string.macropad_editor_shape_circle)
                                    else
                                        stringResource(R.string.macropad_editor_shape_square),
                                    color = if (selected) accentColor else colors.onSurfaceSecondary,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                // Size section — TrackpointSize picker for trackpoint, locked label for ScrollWheel, dropdown otherwise
                SectionLabel(
                    text = if (action is PadAction.TrackpointMove)
                        stringResource(R.string.macropad_editor_trackpoint_size)
                    else
                        stringResource(R.string.macropad_editor_button_size),
                    accentColor = accentColor,
                )
                if (action is PadAction.ScrollWheel) {
                    Text(
                        text     = ButtonSize.SIZE_1X2.displayLabel(),
                        color    = colors.onSurfaceSecondary,
                        fontSize = 14.sp,
                    )
                } else if (action is PadAction.TrackpointMove) {
                    val tpAction = action as PadAction.TrackpointMove
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrackpointSize.entries.forEach { sz ->
                            val selected = sz == tpAction.size
                            val szLabel = when (sz) {
                                TrackpointSize.SMALL  -> stringResource(R.string.macropad_trackpoint_size_small)
                                TrackpointSize.MEDIUM -> stringResource(R.string.macropad_trackpoint_size_medium)
                                TrackpointSize.LARGE  -> stringResource(R.string.macropad_trackpoint_size_large)
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) accentColor.copy(alpha = 0.3f) else colors.surface)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) accentColor else colors.accentBorder,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .clickable { action = PadAction.TrackpointMove(sz) }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(szLabel, color = if (selected) accentColor else colors.onSurfaceSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.accentBorder, RoundedCornerShape(8.dp))
                                .clickable { showSizeMenu = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(buttonSize.displayLabel(), color = colors.onSurface, fontSize = 14.sp)
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = colors.onSurfaceSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded         = showSizeMenu,
                            onDismissRequest = { showSizeMenu = false },
                            modifier         = Modifier.background(colors.surface),
                        ) {
                            ButtonSize.entries.forEach { size ->
                                DropdownMenuItem(
                                    text    = { Text(size.displayLabel(), color = colors.onSurface) },
                                    onClick = { buttonSize = size; showSizeMenu = false },
                                )
                            }
                        }
                    }
                }

                // Action picker
                SectionLabel(stringResource(R.string.macropad_editor_action), accentColor)
                ActionPicker(
                    current        = action,
                    accentColor    = accentColor,
                    enableKeyboard = enableKeyboard,
                    enableGamepad  = enableGamepad,
                    enableMouse    = enableMouse,
                    onChange       = ::onActionChanged,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isConfirmEnabled) {
                        val result = button?.copy(
                            label       = label,
                            buttonShape = buttonShape,
                            buttonSize  = buttonSize,
                            action      = action,
                        ) ?: PadButton(
                            id          = UUID.randomUUID().toString(),
                            label       = label,
                            posX        = 0.5f,
                            posY        = 0.5f,
                            buttonShape = buttonShape,
                            buttonSize  = buttonSize,
                            action      = action,
                        )
                        onConfirm(result)
                    }
                },
                enabled = isConfirmEnabled,
            ) {
                Text(stringResource(R.string.macropad_editor_done), color = if (isConfirmEnabled) accentColor else colors.onSurfaceSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Action picker
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActionPicker(
    current:        PadAction,
    accentColor:    Color,
    enableKeyboard: Boolean = true,
    enableGamepad:  Boolean = true,
    enableMouse:    Boolean = true,
    onChange:       (PadAction) -> Unit,
) {
    // Action category selection
    var categoryExpanded by remember { mutableStateOf(false) }
    val colors           = LocalAppColors.current

    val categoryLabel = stringResource(current.categoryResId())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Category row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, colors.accentBorder, RoundedCornerShape(8.dp))
                .clickable { categoryExpanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(categoryLabel, color = colors.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceSecondary)
        }

        DropdownMenu(
            expanded         = categoryExpanded,
            onDismissRequest = { categoryExpanded = false },
            modifier         = Modifier.background(colors.surface),
        ) {
            ActionCategory.entries.forEach { cat ->
                val catEnabled = when (cat) {
                    ActionCategory.KEYBOARD_KEY   -> enableKeyboard
                    ActionCategory.GAMEPAD_BUTTON -> enableGamepad
                    ActionCategory.MOUSE_BUTTON,
                    ActionCategory.SCROLL_WHEEL,
                    ActionCategory.TRACKPOINT     -> enableMouse
                }
                if (catEnabled) {
                    DropdownMenuItem(
                        text = { Text(stringResource(cat.labelResId()), color = colors.onSurface, fontSize = 14.sp) },
                        onClick = {
                            categoryExpanded = false
                            onChange(cat.defaultAction())
                        },
                    )
                }
            }
        }

        // Category-specific detail picker
        when (current) {
            is PadAction.KeyboardKey    -> KeyboardKeyPicker(current, accentColor, onChange)
            is PadAction.GamepadButton  -> GamepadButtonPicker(current, accentColor, onChange)
            is PadAction.MouseButton    -> MouseButtonPicker(current, accentColor, onChange)
            is PadAction.ScrollWheel,
            is PadAction.TrackpointMove,
            is PadAction.MouseLeftClick,
            is PadAction.MouseRightClick -> { /* no further config needed */ }
        }
    }
}

@Composable
private fun KeyboardKeyPicker(
    current: PadAction.KeyboardKey,
    accentColor: Color,
    onChange: (PadAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors   = LocalAppColors.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.label, color = accentColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = accentColor)
        }

        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(colors.surface),
        ) {
            KEYBOARD_KEY_PRESETS.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = if (code == current.keycode) accentColor else colors.onSurface, fontSize = 14.sp) },
                    onClick = { onChange(PadAction.KeyboardKey(code, label)); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun MouseButtonPicker(
    current: PadAction.MouseButton,
    accentColor: Color,
    onChange: (PadAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors   = LocalAppColors.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.button.displayLabel(), color = accentColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = accentColor)
        }

        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(colors.surface),
        ) {
            MouseButton.entries.forEach { btn ->
                DropdownMenuItem(
                    text    = { Text(btn.displayLabel(), color = if (btn == current.button) accentColor else colors.onSurface, fontSize = 14.sp) },
                    onClick = { onChange(PadAction.MouseButton(btn)); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun GamepadButtonPicker(
    current: PadAction.GamepadButton,
    accentColor: Color,
    onChange: (PadAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors   = LocalAppColors.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.label, color = accentColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = accentColor)
        }

        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(colors.surface),
        ) {
            GamepadKeycodes.PRESETS.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label, color = if (preset.code == current.btnCode) accentColor else colors.onSurface, fontSize = 14.sp) },
                    onClick = { onChange(PadAction.GamepadButton(preset.code, preset.shortLabel)); expanded = false },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers & small UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, accentColor: Color) {
    Text(text, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun NameInputDialog(
    title:        String,
    initialValue: String,
    accentColor:  Color,
    onConfirm:    (String) -> Unit,
    onDismiss:    () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val colors = LocalAppColors.current

    AlertDialog(
        containerColor   = colors.surface,
        onDismissRequest = onDismiss,
        title   = { Text(title, color = colors.onSurface) },
        text    = {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = colors.accentBorder,
                    focusedTextColor     = colors.onSurface,
                    unfocusedTextColor   = colors.onSurface,
                    cursorColor          = accentColor,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.macropad_editor_done), color = if (text.isNotBlank()) accentColor else colors.onSurfaceSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = colors.onSurfaceSecondary)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Action category enum (for the category dropdown)
// ─────────────────────────────────────────────────────────────────────────────

private enum class ActionCategory { KEYBOARD_KEY, GAMEPAD_BUTTON, MOUSE_BUTTON, SCROLL_WHEEL, TRACKPOINT }

private fun ActionCategory.labelResId(): Int = when (this) {
    ActionCategory.KEYBOARD_KEY    -> R.string.macropad_action_keyboard_key
    ActionCategory.GAMEPAD_BUTTON  -> R.string.macropad_action_gamepad_button
    ActionCategory.MOUSE_BUTTON    -> R.string.macropad_action_mouse_button
    ActionCategory.SCROLL_WHEEL    -> R.string.macropad_action_scroll_wheel
    ActionCategory.TRACKPOINT      -> R.string.macropad_action_trackpoint
}

private fun ActionCategory.defaultAction(): PadAction = when (this) {
    ActionCategory.KEYBOARD_KEY   -> PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space")
    ActionCategory.GAMEPAD_BUTTON -> PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
    ActionCategory.MOUSE_BUTTON   -> PadAction.MouseButton(MouseButton.LEFT)
    ActionCategory.SCROLL_WHEEL   -> PadAction.ScrollWheel
    ActionCategory.TRACKPOINT     -> PadAction.TrackpointMove()
}

private fun PadAction.categoryResId(): Int = when (this) {
    is PadAction.KeyboardKey        -> R.string.macropad_action_keyboard_key
    is PadAction.GamepadButton      -> R.string.macropad_action_gamepad_button
    is PadAction.MouseButton        -> R.string.macropad_action_mouse_button
    is PadAction.ScrollWheel        -> R.string.macropad_action_scroll_wheel
    is PadAction.TrackpointMove     -> R.string.macropad_action_trackpoint
    // Legacy
    is PadAction.MouseLeftClick     -> R.string.macropad_action_mouse_button
    is PadAction.MouseRightClick    -> R.string.macropad_action_mouse_button
}

@Composable
private fun PadAction.displayLabel(): String {
    val context = LocalContext.current
    return when (this) {
        is PadAction.KeyboardKey     -> context.getString(R.string.macropad_display_keyboard_key, label)
        is PadAction.GamepadButton   -> context.getString(R.string.macropad_display_gamepad_button, label)
        is PadAction.MouseButton     -> context.getString(R.string.macropad_display_mouse_button, button.displayLabel())
        is PadAction.ScrollWheel     -> context.getString(R.string.macropad_display_scroll_wheel)
        is PadAction.TrackpointMove  -> context.getString(R.string.macropad_display_trackpoint)
        // Legacy
        is PadAction.MouseLeftClick  -> context.getString(R.string.macropad_display_mouse_button, "Left")
        is PadAction.MouseRightClick -> context.getString(R.string.macropad_display_mouse_button, "Right")
    }
}

@Composable
private fun ButtonSize.displayLabel(): String = when (this) {
    ButtonSize.SIZE_1X1 -> stringResource(R.string.macropad_button_size_1x1)
    ButtonSize.SIZE_2X1 -> stringResource(R.string.macropad_button_size_2x1)
    ButtonSize.SIZE_1X2 -> stringResource(R.string.macropad_button_size_1x2)
    ButtonSize.SIZE_2X2 -> stringResource(R.string.macropad_button_size_2x2)
}

private fun MouseButton.displayLabel(): String = when (this) {
    MouseButton.LEFT   -> "Left"
    MouseButton.RIGHT  -> "Right"
    MouseButton.MIDDLE -> "Middle"
    MouseButton.MOUSE4 -> "Mouse 4"
    MouseButton.MOUSE5 -> "Mouse 5"
}

// ─────────────────────────────────────────────────────────────────────────────
// Keyboard key preset list (common keys for MacroPad use)
// ─────────────────────────────────────────────────────────────────────────────

private val KEYBOARD_KEY_PRESETS: List<Pair<Int, String>> = listOf(
    // ── Special / control keys ────────────────────────────────────────────────
    LinuxKeycodes.KEY_SPACE      to "Space",
    LinuxKeycodes.KEY_ENTER      to "Enter",
    LinuxKeycodes.KEY_ESC        to "Esc",
    LinuxKeycodes.KEY_TAB        to "Tab",
    LinuxKeycodes.KEY_BACKSPACE  to "Backspace",
    LinuxKeycodes.KEY_CAPSLOCK   to "CapsLock",
    LinuxKeycodes.KEY_LEFTCTRL   to "Ctrl",
    LinuxKeycodes.KEY_RIGHTCTRL  to "Ctrl R",
    LinuxKeycodes.KEY_LEFTSHIFT  to "Shift",
    LinuxKeycodes.KEY_RIGHTSHIFT to "Shift R",
    LinuxKeycodes.KEY_LEFTALT    to "Alt",
    LinuxKeycodes.KEY_RIGHTALT   to "AltGr",
    LinuxKeycodes.KEY_LEFTMETA   to "Meta / Win",
    // ── Navigation ────────────────────────────────────────────────────────────
    LinuxKeycodes.KEY_UP         to "↑",
    LinuxKeycodes.KEY_DOWN       to "↓",
    LinuxKeycodes.KEY_LEFT       to "←",
    LinuxKeycodes.KEY_RIGHT      to "→",
    LinuxKeycodes.KEY_HOME       to "Home",
    LinuxKeycodes.KEY_END        to "End",
    LinuxKeycodes.KEY_PAGEUP     to "PgUp",
    LinuxKeycodes.KEY_PAGEDOWN   to "PgDn",
    LinuxKeycodes.KEY_INSERT     to "Insert",
    LinuxKeycodes.KEY_DELETE     to "Delete",
    LinuxKeycodes.KEY_SYSRQ     to "PrintScrn",
    // ── F-keys ────────────────────────────────────────────────────────────────
    LinuxKeycodes.KEY_F1         to "F1",
    LinuxKeycodes.KEY_F2         to "F2",
    LinuxKeycodes.KEY_F3         to "F3",
    LinuxKeycodes.KEY_F4         to "F4",
    LinuxKeycodes.KEY_F5         to "F5",
    LinuxKeycodes.KEY_F6         to "F6",
    LinuxKeycodes.KEY_F7         to "F7",
    LinuxKeycodes.KEY_F8         to "F8",
    LinuxKeycodes.KEY_F9         to "F9",
    LinuxKeycodes.KEY_F10        to "F10",
    LinuxKeycodes.KEY_F11        to "F11",
    LinuxKeycodes.KEY_F12        to "F12",
    // ── Number row ────────────────────────────────────────────────────────────
    LinuxKeycodes.KEY_1          to "1",
    LinuxKeycodes.KEY_2          to "2",
    LinuxKeycodes.KEY_3          to "3",
    LinuxKeycodes.KEY_4          to "4",
    LinuxKeycodes.KEY_5          to "5",
    LinuxKeycodes.KEY_6          to "6",
    LinuxKeycodes.KEY_7          to "7",
    LinuxKeycodes.KEY_8          to "8",
    LinuxKeycodes.KEY_9          to "9",
    LinuxKeycodes.KEY_0          to "0",
    LinuxKeycodes.KEY_MINUS      to "-",
    LinuxKeycodes.KEY_EQUAL      to "=",
    // ── Letters A–Z ───────────────────────────────────────────────────────────
    LinuxKeycodes.KEY_A          to "A",
    LinuxKeycodes.KEY_B          to "B",
    LinuxKeycodes.KEY_C          to "C",
    LinuxKeycodes.KEY_D          to "D",
    LinuxKeycodes.KEY_E          to "E",
    LinuxKeycodes.KEY_F          to "F",
    LinuxKeycodes.KEY_G          to "G",
    LinuxKeycodes.KEY_H          to "H",
    LinuxKeycodes.KEY_I          to "I",
    LinuxKeycodes.KEY_J          to "J",
    LinuxKeycodes.KEY_K          to "K",
    LinuxKeycodes.KEY_L          to "L",
    LinuxKeycodes.KEY_M          to "M",
    LinuxKeycodes.KEY_N          to "N",
    LinuxKeycodes.KEY_O          to "O",
    LinuxKeycodes.KEY_P          to "P",
    LinuxKeycodes.KEY_Q          to "Q",
    LinuxKeycodes.KEY_R          to "R",
    LinuxKeycodes.KEY_S          to "S",
    LinuxKeycodes.KEY_T          to "T",
    LinuxKeycodes.KEY_U          to "U",
    LinuxKeycodes.KEY_V          to "V",
    LinuxKeycodes.KEY_W          to "W",
    LinuxKeycodes.KEY_X          to "X",
    LinuxKeycodes.KEY_Y          to "Y",
    LinuxKeycodes.KEY_Z          to "Z",
    // ── Symbols / punctuation ─────────────────────────────────────────────────
    LinuxKeycodes.KEY_GRAVE      to "`",
    LinuxKeycodes.KEY_LEFTBRACE  to "[",
    LinuxKeycodes.KEY_RIGHTBRACE to "]",
    LinuxKeycodes.KEY_BACKSLASH  to "\\",
    LinuxKeycodes.KEY_SEMICOLON  to ";",
    LinuxKeycodes.KEY_APOSTROPHE to "'",
    LinuxKeycodes.KEY_COMMA      to ",",
    LinuxKeycodes.KEY_DOT        to ".",
    LinuxKeycodes.KEY_SLASH      to "/",
    LinuxKeycodes.KEY_102ND      to "< >",
)
