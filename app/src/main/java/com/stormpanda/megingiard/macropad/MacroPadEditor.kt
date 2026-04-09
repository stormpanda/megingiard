package com.stormpanda.megingiard.macropad

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TripOrigin
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.keyboard.KeyInjector
import com.stormpanda.megingiard.ui.LocalAppColors
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val ED_TOP_BAR_HEIGHT = 56.dp
private val ED_PADDING        = 16.dp
private val ED_ITEM_PADDING   = 12.dp
private val ED_GRID_TOGGLE_SIZE = 36.dp
private val ED_GRID_TOGGLE_MARGIN = 8.dp

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
    val macros by MacroState.macros.collectAsState()
    var showMacroListEditor by remember { mutableStateOf(false) }
    var showAddButton       by remember { mutableStateOf(false) }
    var showAddMacroButton  by remember { mutableStateOf(false) }
    var editingButton       by remember { mutableStateOf<PadButton?>(null) }
    var editingButtonActive by remember { mutableStateOf(false) }

    // Intercept system Back when an overlay is visible, so Back closes the overlay
    // instead of dismissing the whole editor dialog.
    val anyOverlayVisible = showMacroListEditor || showAddButton || showAddMacroButton || editingButtonActive
    BackHandler(enabled = anyOverlayVisible) {
        when {
            showMacroListEditor -> showMacroListEditor = false
            showAddButton       -> showAddButton = false
            showAddMacroButton  -> showAddMacroButton = false
            editingButtonActive -> { editingButtonActive = false; editingButton = null }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                profile            = profile,
                accentColor        = colors.accent,
                hasMacros          = macros.isNotEmpty(),
                onManageMacros     = { showMacroListEditor = true },
                onAddButton        = { showAddButton = true },
                onAddMacroButton   = { showAddMacroButton = true },
                onEditButton       = { btn -> editingButton = btn; editingButtonActive = true },
                modifier           = Modifier.padding(innerPadding),
            )
        }
    }

    // Render MacroListEditor as a full-screen inline overlay (same window — no nested Dialog)
    if (showMacroListEditor) {
        MacroListEditor(
            onDone = { showMacroListEditor = false },
        )
    }

    // Add button overlay
    if (showAddButton && profile != null) {
        ButtonEditDialog(
            button         = null,
            accentColor    = colors.accent,
            enableKeyboard = profile.enableKeyboard,
            enableGamepad  = profile.enableGamepad,
            enableMouse    = profile.enableMouse,
            onConfirm      = { newBtn ->
                MacroPadState.updateProfile(profile.copy(buttons = profile.buttons + newBtn))
                showAddButton = false
            },
            onDismiss      = { showAddButton = false },
        )
    }

    // Add macro button overlay
    if (showAddMacroButton && profile != null) {
        val firstMacroId = MacroState.macros.value.firstOrNull()?.id ?: ""
        ButtonEditDialog(
            button         = null,
            accentColor    = colors.accent,
            enableKeyboard = profile.enableKeyboard,
            enableGamepad  = profile.enableGamepad,
            enableMouse    = profile.enableMouse,
            initialAction  = PadAction.Macro(firstMacroId),
            onConfirm      = { newBtn ->
                MacroPadState.updateProfile(profile.copy(buttons = profile.buttons + newBtn))
                showAddMacroButton = false
            },
            onDismiss      = { showAddMacroButton = false },
        )
    }

    // Edit existing button overlay
    if (editingButtonActive && editingButton != null && profile != null) {
        ButtonEditDialog(
            button         = editingButton,
            accentColor    = colors.accent,
            enableKeyboard = profile.enableKeyboard,
            enableGamepad  = profile.enableGamepad,
            enableMouse    = profile.enableMouse,
            onConfirm      = { updated ->
                MacroPadState.updateProfile(
                    profile.copy(buttons = profile.buttons.map { if (it.id == updated.id) updated else it })
                )
                editingButtonActive = false
                editingButton = null
            },
            onDismiss      = { editingButtonActive = false; editingButton = null },
        )
    }
    } // end Box
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
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceSecondary)
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
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.macropad_editor_rename), tint = colors.onSurfaceSecondary)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.macropad_editor_delete_profile), tint = colors.onSurfaceSecondary)
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
    profile:          PadProfile,
    accentColor:      Color,
    hasMacros:        Boolean,
    onManageMacros:   () -> Unit,
    onAddButton:      () -> Unit,
    onAddMacroButton: () -> Unit,
    onEditButton:     (PadButton) -> Unit,
    modifier:         Modifier = Modifier,
) {
    val colors     = LocalAppColors.current
    var gridMode   by remember { mutableStateOf(GridMode.OFF) }
    val profileRef by rememberUpdatedState(profile)

    val lazyListState = rememberLazyListState()
    // Items before buttons: toolbar(0), canvas(1), divider_1(2), devices(3), divider_2(4) → offset = 5
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val offset     = 5
        val newButtons = profileRef.buttons.toMutableList()
        val fromIdx    = (from.index - offset).coerceIn(0, newButtons.lastIndex)
        val toIdx      = (to.index - offset).coerceIn(0, newButtons.lastIndex)
        newButtons.add(toIdx, newButtons.removeAt(fromIdx))
        MacroPadState.updateProfile(profileRef.copy(buttons = newButtons))
    }

    LazyColumn(
        state          = lazyListState,
        modifier       = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = ED_PADDING),
    ) {
        // 1. Action toolbar (Add Button / Macros… / Add Macro Button)
        item(key = "toolbar") {
            EditorToolbar(
                profile          = profile,
                accentColor      = accentColor,
                hasMacros        = hasMacros,
                onManageMacros   = onManageMacros,
                onAddButton      = onAddButton,
                onAddMacroButton = onAddMacroButton,
                modifier         = Modifier
                    .padding(horizontal = ED_PADDING)
                    .padding(bottom = ED_PADDING),
            )
        }

        // 2. Pad canvas with grid toggle overlay
        item(key = "canvas") {
            Box {
                PadCanvas(profile = profile, accentColor = accentColor, gridMode = gridMode)

                val gridIcon = when (gridMode) {
                    GridMode.OFF         -> Icons.Rounded.GridOff
                    GridMode.RECTANGULAR -> Icons.Rounded.Grid4x4
                    GridMode.RADIAL      -> Icons.Rounded.TripOrigin
                }
                val gridCd = when (gridMode) {
                    GridMode.OFF         -> stringResource(R.string.macropad_editor_grid_off)
                    GridMode.RECTANGULAR -> stringResource(R.string.macropad_editor_grid_rectangular)
                    GridMode.RADIAL      -> stringResource(R.string.macropad_editor_grid_radial)
                }
                IconButton(
                    onClick = {
                        gridMode = when (gridMode) {
                            GridMode.OFF         -> GridMode.RECTANGULAR
                            GridMode.RECTANGULAR -> GridMode.RADIAL
                            GridMode.RADIAL      -> GridMode.OFF
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(ED_GRID_TOGGLE_MARGIN)
                        .size(ED_GRID_TOGGLE_SIZE)
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.8f)),
                ) {
                    Icon(
                        gridIcon,
                        contentDescription = gridCd,
                        tint     = if (gridMode == GridMode.OFF) colors.onSurfaceSecondary else accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // 3. Divider
        item(key = "divider_1") {
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = ED_PADDING))
        }

        // 4. Simulated-device checkboxes — shown in a horizontal row
        item(key = "devices") {
            DevicesSection(
                profile     = profile,
                accentColor = accentColor,
                modifier    = Modifier
                    .padding(horizontal = ED_PADDING)
                    .padding(vertical = 4.dp),
            )
        }

        // 5. Divider
        item(key = "divider_2") {
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = ED_PADDING))
        }

        // 6. Button list — tap to edit, drag handle to reorder
        if (profile.buttons.isEmpty()) {
            item(key = "empty") {
                Text(
                    text     = stringResource(R.string.macropad_editor_add_button),
                    color    = colors.onSurfaceSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(horizontal = ED_PADDING)
                        .padding(vertical = 8.dp),
                )
            }
        } else {
            itemsIndexed(profile.buttons, key = { _, btn -> btn.id }) { _, btn ->
                ReorderableItem(reorderState, key = btn.id) { isDragging ->
                    ButtonListItem(
                        btn                = btn,
                        accentColor        = accentColor,
                        enableKeyboard     = profile.enableKeyboard,
                        enableGamepad      = profile.enableGamepad,
                        enableMouse        = profile.enableMouse,
                        isDragging         = isDragging,
                        onEdit             = { onEditButton(btn) },
                        onUpdate           = { updated ->
                            MacroPadState.updateProfile(
                                profile.copy(buttons = profile.buttons.map { if (it.id == btn.id) updated else it })
                            )
                        },
                        onDelete           = {
                            MacroPadState.updateProfile(
                                profile.copy(buttons = profile.buttons.filter { it.id != btn.id })
                            )
                        },
                        dragHandleModifier = Modifier.draggableHandle(),
                        modifier           = Modifier.padding(horizontal = ED_PADDING),
                    )
                    HorizontalDivider(
                        color    = colors.divider,
                        modifier = Modifier.padding(horizontal = ED_PADDING),
                    )
                }
            }
        }
    }
}

@Composable
private fun DevicesSection(
    profile:     PadProfile,
    accentColor: Color,
    modifier:    Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier) {
        Text(
            text       = stringResource(R.string.macropad_editor_devices),
            color      = accentColor,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            DeviceCheckboxRow(
                label           = stringResource(R.string.macropad_editor_device_keyboard),
                checked         = profile.enableKeyboard,
                accentColor     = accentColor,
                onCheckedChange = { MacroPadState.updateProfile(profile.copy(enableKeyboard = it)) },
                modifier        = Modifier.weight(1f),
            )
            DeviceCheckboxRow(
                label           = stringResource(R.string.macropad_editor_device_gamepad),
                checked         = profile.enableGamepad,
                accentColor     = accentColor,
                onCheckedChange = { MacroPadState.updateProfile(profile.copy(enableGamepad = it)) },
                modifier        = Modifier.weight(1f),
            )
            DeviceCheckboxRow(
                label           = stringResource(R.string.macropad_editor_device_mouse),
                checked         = profile.enableMouse,
                accentColor     = accentColor,
                onCheckedChange = { MacroPadState.updateProfile(profile.copy(enableMouse = it)) },
                modifier        = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DeviceCheckboxRow(
    label:           String,
    checked:         Boolean,
    accentColor:     Color,
    onCheckedChange: (Boolean) -> Unit,
    modifier:        Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
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
// Toolbar: add button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorToolbar(
    profile:         PadProfile,
    accentColor:     Color,
    hasMacros:       Boolean,
    onManageMacros:  () -> Unit,
    onAddButton:     () -> Unit,
    onAddMacroButton: () -> Unit,
    modifier:        Modifier = Modifier,
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ED_ITEM_PADDING),
    ) {
        // Add Button
        EditorActionChip(
            label       = stringResource(R.string.macropad_editor_add_button),
            icon        = Icons.Rounded.Add,
            accentColor = accentColor,
            onClick     = onAddButton,
            modifier    = Modifier.weight(1f),
        )
        // Manage Macros
        EditorActionChip(
            label       = stringResource(R.string.macropad_editor_manage_macros),
            icon        = Icons.Rounded.Edit,
            accentColor = accentColor,
            onClick     = onManageMacros,
            modifier    = Modifier.weight(1f),
        )
        // Add Macro Button
        EditorActionChip(
            label       = stringResource(R.string.macropad_editor_add_macro_button),
            icon        = Icons.Rounded.PlayArrow,
            accentColor = accentColor,
            enabled     = hasMacros,
            onClick     = onAddMacroButton,
            modifier    = Modifier.weight(1f),
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
    enabled:     Boolean = true,
) {
    val effectiveColor = if (enabled) accentColor else accentColor.copy(alpha = 0.38f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, effectiveColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = effectiveColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = effectiveColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Button list item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ButtonListItem(
    btn:                PadButton,
    accentColor:        Color,
    enableKeyboard:     Boolean,
    enableGamepad:      Boolean,
    enableMouse:        Boolean,
    isDragging:         Boolean,
    onEdit:             () -> Unit,
    onUpdate:           (PadButton) -> Unit,
    onDelete:           () -> Unit,
    dragHandleModifier: Modifier,
    modifier:           Modifier = Modifier,
) {
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
        is PadAction.Macro                       -> !enableGamepad
        is PadAction.AmbientPeek                 -> false
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isDeviceDisabled) 0.38f else 1f)
            .background(if (isDragging) colors.surfaceVariant else Color.Transparent)
            .clickable { onEdit() }
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
                if (btn.iconName != null) {
                    MaterialSymbol(
                        name = btn.iconName,
                        size = 18.dp,
                        tint = colors.onSurface,
                        filled = iconsFilledState.value,
                    )
                } else {
                    Text(btn.label.take(2), color = colors.onSurface, fontSize = 10.sp)
                }
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

        if (!isTrackpoint && btn.action !is PadAction.ScrollWheel) {
            Text(
                text = "${btn.buttonSize.cols}×${btn.buttonSize.rows}",
                color = colors.onSurfaceSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 4.dp),
            )
        }

        IconButton(onClick = { showDelete = true }) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.macropad_editor_delete_button), tint = colors.onSurfaceSecondary)
        }
        Icon(
            imageVector        = Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.cd_drag_reorder),
            tint               = colors.onSurfaceSecondary,
            modifier           = Modifier
                .padding(horizontal = 12.dp)
                .then(dragHandleModifier),
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
// Helpers & small UI
// ─────────────────────────────────────────────────────────────────────────────
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

