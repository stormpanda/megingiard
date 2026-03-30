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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.settings.SettingsManager
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val ED_BG              = Color(0xFF121212)
private val ED_SURFACE         = Color(0xFF1C1C1E)
private val ED_TEXT            = Color.White
private val ED_TEXT_SECONDARY  = Color.White.copy(alpha = 0.6f)
private val ED_DIVIDER         = Color.White.copy(alpha = 0.12f)
private val ED_BORDER          = Color.White.copy(alpha = 0.20f)

private val ED_TOP_BAR_HEIGHT  = 56.dp
private val ED_PADDING         = 16.dp
private val ED_ITEM_PADDING    = 12.dp
private val ED_BUTTON_UNIT_DP  = 60.dp   // 1.0 (1×1) = this size on the pad canvas

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
    val accentColor by SettingsManager.accentColor.collectAsState()

    // Stop all uinput virtual devices while the editor is open.
    // keyinjector_arm64 registers as a hardware keyboard via uinput, which causes
    // Android to suppress the soft IME — making text fields un-typeable.
    // We restart all injectors when the editor is dismissed (user returns to use mode).
    DisposableEffect(Unit) {
        KeyInjector.stop()
        GamepadInjector.stop()
        MouseInjector.stop()
        onDispose {
            CoroutineScope(Dispatchers.IO).launch {
                KeyInjector.start(context)
                GamepadInjector.start(context)
                MouseInjector.start(context)
            }
        }
    }

    val profile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    Scaffold(
        containerColor = ED_BG,
        topBar = {
            EditorTopBar(
                profiles    = profiles,
                activeId    = activeId,
                accentColor = accentColor,
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
                    color = ED_TEXT_SECONDARY,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(ED_PADDING),
                )
            }
        } else {
            EditorBody(
                profile     = profile,
                accentColor = accentColor,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ED_TOP_BAR_HEIGHT)
            .background(ED_SURFACE)
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
                    color    = ED_TEXT,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = ED_TEXT_SECONDARY)
            }

            DropdownMenu(
                expanded          = profileMenuExpanded,
                onDismissRequest  = { profileMenuExpanded = false },
                modifier          = Modifier.background(ED_SURFACE),
            ) {
                profiles.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text  = p.name,
                                color = if (p.id == activeId) accentColor else ED_TEXT,
                                fontSize = 14.sp,
                            )
                        },
                        onClick = {
                            onSelectProfile(p.id)
                            profileMenuExpanded = false
                        },
                    )
                }
                HorizontalDivider(color = ED_DIVIDER)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_macropad_new_profile), color = accentColor, fontSize = 14.sp) },
                    onClick = { profileMenuExpanded = false; showNewDialog = true },
                )
            }
        }

        // Rename & delete buttons (only when a profile exists)
        if (activeProfile != null) {
            IconButton(onClick = { showRenameDialog = true }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.macropad_editor_rename), tint = ED_TEXT_SECONDARY)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.macropad_editor_delete_profile), tint = ED_TEXT_SECONDARY)
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
            containerColor = ED_SURFACE,
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text(stringResource(R.string.macropad_editor_delete_profile), color = ED_TEXT) },
            text    = { Text(stringResource(R.string.macropad_editor_confirm_delete), color = ED_TEXT_SECONDARY) },
            confirmButton = {
                TextButton(onClick = { onDeleteProfile(activeProfile.id); showDeleteConfirm = false }) {
                    Text(stringResource(R.string.macropad_editor_confirm), color = Color(0xFFCF6679))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = ED_TEXT_SECONDARY)
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ED_PADDING),
        verticalArrangement = Arrangement.spacedBy(ED_PADDING),
    ) {
        // Pad canvas — interactive drag area
        PadCanvas(profile = profile, accentColor = accentColor)

        HorizontalDivider(color = ED_DIVIDER)

        // Toolbar: Add button + Trackpoint toggle
        EditorToolbar(profile = profile, accentColor = accentColor)

        HorizontalDivider(color = ED_DIVIDER)

        // Button list — tap to edit
        ButtonList(profile = profile, accentColor = accentColor)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pad canvas — drag buttons to reposition
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PadCanvas(profile: PadProfile, accentColor: Color) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val padModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .border(1.dp, ED_BORDER, if (profile.padShape == PadShape.CIRCLE) CircleShape else RoundedCornerShape(8.dp))
        .clip(if (profile.padShape == PadShape.CIRCLE) CircleShape else RoundedCornerShape(8.dp))
        .background(ED_SURFACE)
        .onSizeChanged { canvasSize = it }

    Box(modifier = padModifier) {
        // Render each button as a draggable chip
        profile.buttons.forEach { btn ->
            DraggableButton(
                btn         = btn,
                canvasSize  = canvasSize,
                accentColor = accentColor,
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

        // Trackpoint indicator
        if (profile.hasTrackpoint) {
            DraggableTrackpoint(
                posX        = profile.trackpointPosX,
                posY        = profile.trackpointPosY,
                size        = profile.trackpointSize,
                canvasSize  = canvasSize,
                accentColor = accentColor,
                onPositionChanged = { nx, ny ->
                    MacroPadState.updateProfile(profile.copy(trackpointPosX = nx, trackpointPosY = ny))
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
    onPositionChanged: (Float, Float) -> Unit,
) {
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
    val chipWidthPx  = with(density) { (ED_BUTTON_UNIT_DP * btn.buttonSize.cols).toPx() }
    val chipHeightPx = with(density) { (ED_BUTTON_UNIT_DP * btn.buttonSize.rows).toPx() }

    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
    val h = canvasSize.height.toFloat().coerceAtLeast(1f)

    // Top-left position in canvas pixels (centre adjusted by half-chip)
    val left = btn.posX * w - chipWidthPx / 2f
    val top  = btn.posY * h - chipHeightPx / 2f

    // Circle shape only makes visual sense for square (1×1) buttons
    val chipShape = if (btn.buttonShape == ButtonShape.CIRCLE && btn.buttonSize == ButtonSize.SIZE_1X1)
        CircleShape else RoundedCornerShape(8.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .width(ED_BUTTON_UNIT_DP * btn.buttonSize.cols)
            .height(ED_BUTTON_UNIT_DP * btn.buttonSize.rows)
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
        Text(btn.label, color = ED_TEXT, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DraggableTrackpoint(
    posX: Float,
    posY: Float,
    size: Float,
    canvasSize: IntSize,
    accentColor: Color,
    onPositionChanged: (Float, Float) -> Unit,
) {
    val currentPosX = rememberUpdatedState(posX)
    val currentPosY = rememberUpdatedState(posY)
    val currentOnPositionChanged = rememberUpdatedState(onPositionChanged)
    var startPosX by remember { mutableFloatStateOf(posX) }
    var startPosY by remember { mutableFloatStateOf(posY) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val chipSizePx = with(density) { (ED_BUTTON_UNIT_DP * size).toPx() }

    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
    val h = canvasSize.height.toFloat().coerceAtLeast(1f)

    val left = posX * w - chipSizePx / 2f
    val top  = posY * h - chipSizePx / 2f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(ED_BUTTON_UNIT_DP * size)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.15f))
            .border(2.dp, accentColor, CircleShape)
            .pointerInput(canvasSize) {
                detectDragGestures(
                    onDragStart = {
                        startPosX = currentPosX.value
                        startPosY = currentPosY.value
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
        Text("●", color = accentColor, fontSize = 14.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Toolbar: add button + trackpoint toggle
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

        // Trackpoint toggle
        val trackpointLabel = if (profile.hasTrackpoint)
            stringResource(R.string.macropad_editor_remove_trackpoint)
        else
            stringResource(R.string.macropad_editor_add_trackpoint)

        EditorActionChip(
            label       = trackpointLabel,
            icon        = Icons.Filled.Add,
            accentColor = if (profile.hasTrackpoint) ED_TEXT_SECONDARY else accentColor,
            onClick     = {
                MacroPadState.updateProfile(profile.copy(hasTrackpoint = !profile.hasTrackpoint))
            },
            modifier    = Modifier.weight(1f),
        )
    }

    if (showButtonDialog) {
        ButtonEditDialog(
            button      = null,  // null = new button
            accentColor = accentColor,
            onConfirm   = { newBtn ->
                MacroPadState.updateProfile(
                    profile.copy(buttons = profile.buttons + newBtn)
                )
                showButtonDialog = false
            },
            onDismiss   = { showButtonDialog = false },
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (profile.buttons.isEmpty()) {
            Text(
                text     = stringResource(R.string.macropad_editor_add_button),
                color    = ED_TEXT_SECONDARY,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        profile.buttons.forEach { btn ->
            ButtonListItem(
                btn         = btn,
                accentColor = accentColor,
                onUpdate    = { updated ->
                    MacroPadState.updateProfile(
                        profile.copy(buttons = profile.buttons.map { if (it.id == btn.id) updated else it })
                    )
                },
                onDelete    = {
                    MacroPadState.updateProfile(
                        profile.copy(buttons = profile.buttons.filter { it.id != btn.id })
                    )
                },
            )
            HorizontalDivider(color = ED_DIVIDER)
        }
    }
}

@Composable
private fun ButtonListItem(
    btn:         PadButton,
    accentColor: Color,
    onUpdate:    (PadButton) -> Unit,
    onDelete:    () -> Unit,
) {
    var showEdit    by remember { mutableStateOf(false) }
    var showDelete  by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showEdit = true }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shape indicator
        val chipShape = if (btn.buttonShape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(4.dp)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(chipShape)
                .background(accentColor.copy(alpha = 0.2f))
                .border(1.dp, accentColor, chipShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(btn.label.take(2), color = ED_TEXT, fontSize = 10.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(btn.label, color = ED_TEXT, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(btn.action.displayLabel(), color = ED_TEXT_SECONDARY, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        IconButton(onClick = { showDelete = true }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.macropad_editor_delete_button), tint = ED_TEXT_SECONDARY)
        }
    }

    if (showEdit) {
        ButtonEditDialog(
            button      = btn,
            accentColor = accentColor,
            onConfirm   = { onUpdate(it); showEdit = false },
            onDismiss   = { showEdit = false },
        )
    }

    if (showDelete) {
        AlertDialog(
            containerColor   = ED_SURFACE,
            onDismissRequest = { showDelete = false },
            title   = { Text(stringResource(R.string.macropad_editor_delete_button), color = ED_TEXT) },
            text    = { Text(btn.label, color = ED_TEXT_SECONDARY) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDelete = false }) {
                    Text(stringResource(R.string.macropad_editor_confirm), color = Color(0xFFCF6679))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.macropad_editor_cancel), color = ED_TEXT_SECONDARY)
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
    button:      PadButton?,    // null → create new
    accentColor: Color,
    onConfirm:   (PadButton) -> Unit,
    onDismiss:   () -> Unit,
) {
    var label         by remember { mutableStateOf(button?.label ?: "") }
    var buttonShape   by remember { mutableStateOf(button?.buttonShape ?: ButtonShape.CIRCLE) }
    var buttonSize    by remember { mutableStateOf(button?.buttonSize ?: ButtonSize.SIZE_1X1) }
    var showSizeMenu  by remember { mutableStateOf(false) }
    var action        by remember { mutableStateOf(button?.action ?: PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space")) }

    AlertDialog(
        containerColor   = ED_SURFACE,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text  = if (button == null) stringResource(R.string.macropad_editor_add_button)
                        else button.label,
                color = ED_TEXT,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Label input
                OutlinedTextField(
                    value         = label,
                    onValueChange = { label = it },
                    label         = { Text(stringResource(R.string.macropad_editor_button_label), color = ED_TEXT_SECONDARY) },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = accentColor,
                        unfocusedBorderColor = ED_BORDER,
                        focusedTextColor     = ED_TEXT,
                        unfocusedTextColor   = ED_TEXT,
                        cursorColor          = accentColor,
                    ),
                )

                // Button shape selector
                SectionLabel(stringResource(R.string.macropad_editor_button_shape), accentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ButtonShape.entries.forEach { shape ->
                        val selected = shape == buttonShape
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(if (shape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(8.dp))
                                .background(if (selected) accentColor.copy(alpha = 0.3f) else ED_SURFACE)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) accentColor else ED_BORDER,
                                    shape = if (shape == ButtonShape.CIRCLE) CircleShape else RoundedCornerShape(8.dp),
                                )
                                .clickable { buttonShape = shape },
                        ) {
                            Text(
                                text  = if (shape == ButtonShape.CIRCLE)
                                    stringResource(R.string.macropad_editor_shape_circle)
                                else
                                    stringResource(R.string.macropad_editor_shape_square),
                                color = if (selected) accentColor else ED_TEXT_SECONDARY,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // Button size dropdown (1×1 / 2×1 / 1×2 / 2×2)
                SectionLabel(stringResource(R.string.macropad_editor_button_size), accentColor)
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ED_SURFACE)
                            .border(1.dp, ED_BORDER, RoundedCornerShape(8.dp))
                            .clickable { showSizeMenu = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(buttonSize.displayLabel(), color = ED_TEXT, fontSize = 14.sp)
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = ED_TEXT_SECONDARY,
                        )
                    }
                    DropdownMenu(
                        expanded         = showSizeMenu,
                        onDismissRequest = { showSizeMenu = false },
                        modifier         = Modifier.background(ED_SURFACE),
                    ) {
                        ButtonSize.entries.forEach { size ->
                            DropdownMenuItem(
                                text    = { Text(size.displayLabel(), color = ED_TEXT) },
                                onClick = { buttonSize = size; showSizeMenu = false },
                            )
                        }
                    }
                }

                // Action picker
                SectionLabel(stringResource(R.string.macropad_editor_action), accentColor)
                ActionPicker(
                    current     = action,
                    accentColor = accentColor,
                    onChange    = { action = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (label.isNotBlank()) {
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
                enabled = label.isNotBlank(),
            ) {
                Text(stringResource(R.string.macropad_editor_done), color = if (label.isNotBlank()) accentColor else ED_TEXT_SECONDARY)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = ED_TEXT_SECONDARY)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Action picker
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActionPicker(
    current:     PadAction,
    accentColor: Color,
    onChange:    (PadAction) -> Unit,
) {
    // Action category selection
    var categoryExpanded by remember { mutableStateOf(false) }

    val categoryLabel = stringResource(current.categoryResId())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Category row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, ED_BORDER, RoundedCornerShape(8.dp))
                .clickable { categoryExpanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(categoryLabel, color = ED_TEXT, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = ED_TEXT_SECONDARY)
        }

        DropdownMenu(
            expanded         = categoryExpanded,
            onDismissRequest = { categoryExpanded = false },
            modifier         = Modifier.background(ED_SURFACE),
        ) {
            ActionCategory.entries.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(stringResource(cat.labelResId()), color = ED_TEXT, fontSize = 14.sp) },
                    onClick = {
                        categoryExpanded = false
                        onChange(cat.defaultAction())
                    },
                )
            }
        }

        // Category-specific detail picker
        when (current) {
            is PadAction.KeyboardKey  -> KeyboardKeyPicker(current, accentColor, onChange)
            is PadAction.GamepadButton -> GamepadButtonPicker(current, accentColor, onChange)
            is PadAction.MouseLeftClick,
            is PadAction.MouseRightClick,
            is PadAction.TrackpointMove -> { /* no further config needed */ }
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
            modifier         = Modifier.background(ED_SURFACE),
        ) {
            KEYBOARD_KEY_PRESETS.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = if (code == current.keycode) accentColor else ED_TEXT, fontSize = 14.sp) },
                    onClick = { onChange(PadAction.KeyboardKey(code, label)); expanded = false },
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
            modifier         = Modifier.background(ED_SURFACE),
        ) {
            GamepadKeycodes.PRESETS.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label, color = if (preset.code == current.btnCode) accentColor else ED_TEXT, fontSize = 14.sp) },
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

    AlertDialog(
        containerColor   = ED_SURFACE,
        onDismissRequest = onDismiss,
        title   = { Text(title, color = ED_TEXT) },
        text    = {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = ED_BORDER,
                    focusedTextColor     = ED_TEXT,
                    unfocusedTextColor   = ED_TEXT,
                    cursorColor          = accentColor,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.macropad_editor_done), color = if (text.isNotBlank()) accentColor else ED_TEXT_SECONDARY)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.macropad_editor_cancel), color = ED_TEXT_SECONDARY)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Action category enum (for the category dropdown)
// ─────────────────────────────────────────────────────────────────────────────

private enum class ActionCategory { KEYBOARD_KEY, GAMEPAD_BUTTON, MOUSE_LEFT, MOUSE_RIGHT, TRACKPOINT }

private fun ActionCategory.labelResId(): Int = when (this) {
    ActionCategory.KEYBOARD_KEY    -> R.string.macropad_action_keyboard_key
    ActionCategory.GAMEPAD_BUTTON  -> R.string.macropad_action_gamepad_button
    ActionCategory.MOUSE_LEFT      -> R.string.macropad_action_mouse_left
    ActionCategory.MOUSE_RIGHT     -> R.string.macropad_action_mouse_right
    ActionCategory.TRACKPOINT      -> R.string.macropad_action_trackpoint
}

private fun ActionCategory.defaultAction(): PadAction = when (this) {
    ActionCategory.KEYBOARD_KEY   -> PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space")
    ActionCategory.GAMEPAD_BUTTON -> PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
    ActionCategory.MOUSE_LEFT     -> PadAction.MouseLeftClick
    ActionCategory.MOUSE_RIGHT    -> PadAction.MouseRightClick
    ActionCategory.TRACKPOINT     -> PadAction.TrackpointMove
}

private fun PadAction.categoryResId(): Int = when (this) {
    is PadAction.KeyboardKey   -> R.string.macropad_action_keyboard_key
    is PadAction.GamepadButton -> R.string.macropad_action_gamepad_button
    is PadAction.MouseLeftClick  -> R.string.macropad_action_mouse_left
    is PadAction.MouseRightClick -> R.string.macropad_action_mouse_right
    is PadAction.TrackpointMove  -> R.string.macropad_action_trackpoint
}

@Composable
private fun PadAction.displayLabel(): String {
    val context = LocalContext.current
    return when (this) {
        is PadAction.KeyboardKey     -> context.getString(R.string.macropad_display_keyboard_key, label)
        is PadAction.GamepadButton   -> context.getString(R.string.macropad_display_gamepad_button, label)
        is PadAction.MouseLeftClick  -> context.getString(R.string.macropad_display_mouse_left)
        is PadAction.MouseRightClick -> context.getString(R.string.macropad_display_mouse_right)
        is PadAction.TrackpointMove  -> context.getString(R.string.macropad_display_trackpoint)
    }
}

@Composable
private fun ButtonSize.displayLabel(): String = when (this) {
    ButtonSize.SIZE_1X1 -> stringResource(R.string.macropad_button_size_1x1)
    ButtonSize.SIZE_2X1 -> stringResource(R.string.macropad_button_size_2x1)
    ButtonSize.SIZE_1X2 -> stringResource(R.string.macropad_button_size_1x2)
    ButtonSize.SIZE_2X2 -> stringResource(R.string.macropad_button_size_2x2)
}

// ─────────────────────────────────────────────────────────────────────────────
// Keyboard key preset list (common keys for MacroPad use)
// ─────────────────────────────────────────────────────────────────────────────

private val KEYBOARD_KEY_PRESETS: List<Pair<Int, String>> = listOf(
    LinuxKeycodes.KEY_SPACE      to "Space",
    LinuxKeycodes.KEY_ENTER      to "Enter",
    LinuxKeycodes.KEY_ESC        to "Esc",
    LinuxKeycodes.KEY_TAB        to "Tab",
    LinuxKeycodes.KEY_BACKSPACE  to "Backspace",
    LinuxKeycodes.KEY_LEFTCTRL   to "Ctrl",
    LinuxKeycodes.KEY_LEFTSHIFT  to "Shift",
    LinuxKeycodes.KEY_LEFTALT    to "Alt",
    LinuxKeycodes.KEY_LEFTMETA   to "Meta / Win",
    LinuxKeycodes.KEY_UP         to "↑",
    LinuxKeycodes.KEY_DOWN       to "↓",
    LinuxKeycodes.KEY_LEFT       to "←",
    LinuxKeycodes.KEY_RIGHT      to "→",
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
)
