package com.stormpanda.megingiard.keyboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.input.MouseInjector
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.QUICK_MENU_BAR_INSET
import com.stormpanda.megingiard.viewmodel.KeyboardViewModel

// ---------------------------------------------------------------------------
// Layout constants
// ---------------------------------------------------------------------------
private val KEY_PADDING_H = 2.dp
private const val KB_TRACKPOINT_OVERLAY_ALPHA = 0.82f
private const val KB_TRACKPOINT_FADE_MS = 200

private const val TAG = "KeyboardScreen"

@Composable
fun KeyboardScreen(
    modifier: Modifier = Modifier,
    forcedLayout: KbLayout? = null,
) {
    val viewModel: KeyboardViewModel = viewModel()
    val context = LocalContext.current
    val kbLayoutSetting by viewModel.kbLayout.collectAsState()
    val kbLayout = forcedLayout ?: kbLayoutSetting
    val kbRepeatEnabled by viewModel.kbRepeatEnabled.collectAsState()
    val kbTrackpointEnabled by viewModel.kbTrackpointEnabled.collectAsState()
    val kbFullscreen by viewModel.kbFullscreen.collectAsState()
    val kbMouseBtnPos by viewModel.kbMouseBtnPos.collectAsState()
    val isQuickMenuOpen by viewModel.isQuickMenuOpen.collectAsState()
    val isQuickMenuOpenState = rememberUpdatedState(isQuickMenuOpen)
    val colors = LocalAppColors.current
    val accentColor = colors.accent
    val controller = viewModel.controller

    // Sub-mode and layout tracking
    val keyboardMode by viewModel.keyboardMode.collectAsState()

    // Modifier states for dynamic label rendering
    val lshiftState by KeyboardState.stateFor("lshift").collectAsState()
    val rshiftState by KeyboardState.stateFor("rshift").collectAsState()
    val capsState by KeyboardState.stateFor("caps").collectAsState()
    val altGrState by KeyboardState.stateFor("ralt").collectAsState()
    val isShiftActive = lshiftState != ModifierState.INACTIVE || rshiftState != ModifierState.INACTIVE
    val isCapsActive = capsState != ModifierState.INACTIVE
    val isAltGrActive = altGrState != ModifierState.INACTIVE

    // Start injectors via ViewModel (waits for overlay to close).
    LaunchedEffect(Unit) {
        viewModel.startInjectors(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAndReset()
        }
    }

    val layout =
        remember(kbLayout, keyboardMode) {
            when (kbLayout) {
                KbLayout.QWERTY -> qwertyLayout(keyboardMode)
                KbLayout.AZERTY -> azertyLayout(keyboardMode)
                KbLayout.QWERTZ -> qwertzLayout(keyboardMode)
            }
        }

    // Key bounds: id → root-space Rect, populated by KeyCap.onGloballyPositioned
    val keyBounds = remember { mutableMapOf<String, KeyBounds>() }
    // Outer Box layout coords — used to convert pointer positions to root space
    val boxCoordsState = remember { mutableStateOf<LayoutCoordinates?>(null) }

    // UI state from controller
    val pressedKeys by controller.pressedKeys.collectAsState()
    val trackpointVisible by controller.trackpointVisible.collectAsState()

    val trackpointAlpha by animateFloatAsState(
        targetValue = if (trackpointVisible) KB_TRACKPOINT_OVERLAY_ALPHA else 0f,
        animationSpec = tween(KB_TRACKPOINT_FADE_MS),
        label = "trackpointAlpha",
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        // Transparent Top Spacer to let Zelda wallpaper/ambient background shine through
        Spacer(modifier = Modifier.weight(1f))

        // Gboard Container (top toolbar, grid, bottom toolbar)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(Color(0xFF17191D)),
        ) {
            // 1. Top Toolbar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                ToolbarIcon(imageVector = Icons.Rounded.GridView, contentDescription = "Apps")
                ToolbarIcon(imageVector = Icons.Rounded.EmojiEmotions, contentDescription = "Emoji")
                GifToolbarIcon()
                ToolbarIcon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    onClick = {
                        AppStateManager.setFullscreenKeyboardActive(false)
                        AppStateManager.setGlobalSettingsOpen(true)
                    },
                )
                ToolbarIcon(imageVector = Icons.Rounded.Translate, contentDescription = "Translate")
                ToolbarIcon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = "Theme",
                    onClick = {
                        AppStateManager.setFullscreenKeyboardActive(false)
                        AppStateManager.setBackgroundSettingsActive(true)
                    },
                )
                ToolbarIcon(imageVector = Icons.AutoMirrored.Rounded.Assignment, contentDescription = "Clipboard")
                ToolbarIcon(imageVector = Icons.Rounded.Mic, contentDescription = "Voice Search")
            }

            // 2. Keyboard Grid (isolated touch interception)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .onGloballyPositioned { boxCoordsState.value = it }
                        .pointerInput(layout) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    if (isQuickMenuOpenState.value) {
                                        if (event.type == PointerEventType.Press) {
                                            if (event.changes.none { it.isConsumed }) {
                                                viewModel.closeQuickMenu()
                                            }
                                        }
                                        event.changes.forEach { it.consume() }
                                        continue
                                    }

                                    val boxCoords = boxCoordsState.value ?: continue
                                    for (change in event.changes) {
                                        val pid = change.id.value

                                        if (controller.isTrackpointPointer(pid)) {
                                            when {
                                                change.pressed && event.type == PointerEventType.Move -> {
                                                    val delta = change.positionChange()
                                                    controller.onKeyMove(pid, null, delta.x, delta.y, layout, kbRepeatEnabled)
                                                    change.consume()
                                                }

                                                !change.pressed && change.previousPressed -> {
                                                    controller.onKeyUp(pid, layout, kbRepeatEnabled)
                                                    change.consume()
                                                }
                                            }
                                            continue
                                        }

                                        if (change.isConsumed) continue

                                        val rootPos = boxCoords.localToRoot(change.position)
                                        val keyId =
                                            keyBounds.entries
                                                .filter { (id, _) -> findKeyInLayout(layout, id) != null }
                                                .firstOrNull { (_, r) -> r.contains(rootPos.x, rootPos.y) }
                                                ?.key

                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                if (!change.previousPressed) {
                                                    if (keyId != null && keyId.startsWith("mode_switch")) {
                                                        val targetMode =
                                                            when (keyId) {
                                                                "mode_switch", "mode_switch_1" -> KeyboardMode.SYMBOLS_1
                                                                "mode_switch_abc" -> KeyboardMode.LETTERS
                                                                "mode_switch_2", "mode_switch_1234" -> KeyboardMode.SYMBOLS_2
                                                                else -> KeyboardMode.LETTERS
                                                            }
                                                        viewModel.setKeyboardMode(targetMode)
                                                        change.consume()
                                                    } else if (keyId == "globe") {
                                                        viewModel.cycleKbLayout()
                                                        change.consume()
                                                    } else {
                                                        if (controller.onKeyDown(pid, keyId, layout, kbRepeatEnabled)) {
                                                            change.consume()
                                                        }
                                                    }
                                                }
                                            }

                                            PointerEventType.Move -> {
                                                val delta = change.positionChange()
                                                if (keyId != null && (keyId.startsWith("mode_switch") || keyId == "globe")) {
                                                    change.consume()
                                                } else {
                                                    if (controller.onKeyMove(pid, keyId, delta.x, delta.y, layout, kbRepeatEnabled)) {
                                                        change.consume()
                                                    }
                                                }
                                            }

                                            PointerEventType.Release -> {
                                                if (!change.pressed && change.previousPressed) {
                                                    controller.onKeyUp(pid, layout, kbRepeatEnabled)
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
            ) {
                // Crossfade layout switch
                Crossfade(targetState = layout, label = "Layout Switch") { activeLayout ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        activeLayout.forEachIndexed { rowIndex, row ->
                            // Dynamically inject trackpoint key if enabled
                            val finalRow =
                                if (rowIndex == 1 && keyboardMode == KeyboardMode.LETTERS && kbTrackpointEnabled) {
                                    row.toMutableList().apply {
                                        val gIndex = indexOfFirst { it.id == "g" }
                                        if (gIndex != -1) {
                                            add(gIndex + 1, KeyDef("tp", "●", 0, type = KeyType.TRACKPOINT, widthWeight = 1.0f))
                                        }
                                    }
                                } else {
                                    row
                                }

                            Row(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(KEY_PADDING_H),
                            ) {
                                finalRow.forEach { key ->
                                    val modState by KeyboardState.stateFor(key.id).collectAsState()
                                    KeyCap(
                                        keyDef = key,
                                        isPressed = key.id in pressedKeys,
                                        modifierState = modState,
                                        accentColor = accentColor,
                                        isShiftActive = isShiftActive,
                                        isCapsActive = isCapsActive,
                                        isAltGrActive = isAltGrActive,
                                        modifier = Modifier.weight(key.widthWeight),
                                        onBoundsUpdate = { bounds -> keyBounds[key.id] = bounds },
                                    )
                                }
                            }
                        }
                    }
                }

                // Trackpoint overlay: mouse buttons own their pointerInput; outer Box handles trackpoint moves.
                if (trackpointAlpha > 0f) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .align(Alignment.Center)
                                .alpha(trackpointAlpha)
                                .background(colors.keyBackground, RoundedCornerShape(8.dp))
                                .border(2.dp, colors.navQuickMenuBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.cd_keyboard_trackpoint),
                            color = colors.onAccent.copy(alpha = 0.25f),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                        )
                        if (trackpointVisible) {
                            if (kbMouseBtnPos == KbMouseBtnPos.LEFT || kbMouseBtnPos == KbMouseBtnPos.BOTH) {
                                MouseButtonColumn(
                                    accentColor = accentColor,
                                    onLmbDown = { MouseInjector.leftDown() },
                                    onLmbUp = { MouseInjector.leftUp() },
                                    onRmbDown = { MouseInjector.rightDown() },
                                    onRmbUp = { MouseInjector.rightUp() },
                                    modifier =
                                        Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(start = 8.dp),
                                )
                            }
                            if (kbMouseBtnPos == KbMouseBtnPos.RIGHT || kbMouseBtnPos == KbMouseBtnPos.BOTH) {
                                MouseButtonColumn(
                                    accentColor = accentColor,
                                    onLmbDown = { MouseInjector.leftDown() },
                                    onLmbUp = { MouseInjector.leftUp() },
                                    onRmbDown = { MouseInjector.rightDown() },
                                    onRmbUp = { MouseInjector.rightUp() },
                                    mirrored = true,
                                    modifier =
                                        Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 3. Bottom Toolbar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { AppStateManager.setFullscreenKeyboardActive(false) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse Keyboard",
                        tint = colors.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = "Pin",
                    tint = colors.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = "Switcher",
                    tint = colors.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolbarIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = colors.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun GifToolbarIcon(onClick: () -> Unit = {}) {
    val colors = LocalAppColors.current
    Box(
        modifier =
            Modifier
                .size(width = 36.dp, height = 24.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.5.dp, colors.onSurface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "GIF",
            color = colors.onSurface.copy(alpha = 0.8f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
