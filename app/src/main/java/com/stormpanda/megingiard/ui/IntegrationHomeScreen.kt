package com.stormpanda.megingiard.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Airplay
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.CompanionViewMode
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.focus.rom.EmulatorDetectionFunnel
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.macropad.ProfileAssociation
import com.stormpanda.megingiard.mirror.ScreenCaptureManager
import com.stormpanda.megingiard.mirror.ScreenCutout
import com.stormpanda.megingiard.privd.PrivdManager
import com.stormpanda.megingiard.privd.PrivdState
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val TAG = "IntegrationHomeScreen"

private val IH_CARD_SHAPE = RoundedCornerShape(16.dp)
private val IH_DECK_CARD_SHAPE = RoundedCornerShape(14.dp)
private val IH_PADDING_CARD = 16.dp
private val IH_PADDING_SCREEN = 20.dp
private val IH_PADDING_HEADER_TOP = 12.dp
private val IH_PADDING_HEADER_BOTTOM = 8.dp
private val IH_SCROLL_TOP_MARGIN = 8.dp
private val IH_SCROLL_BOTTOM_MARGIN = 24.dp
private val IH_SPACING_CARD = 12.dp
private val IH_SPACING_SECTION = 16.dp
private val IH_SPACING_GRID = 12.dp
private val IH_SPACING_STATUS = 8.dp
private val IH_STATUS_DOT_SIZE = 10.dp
private val IH_STATUS_ICON_SIZE = 22.dp
private val IH_HERO_ICON_SIZE = 32.dp
private val IH_HERO_ICON_BG_SIZE = 56.dp
private val IH_DECK_ICON_BG_SIZE = 44.dp
private val IH_DECK_ICON_SIZE = 24.dp

private const val IH_BATTERY_LOW_THRESHOLD = 20
private val IH_BATTERY_ICON_SIZE = 20.dp
private val IH_BATTERY_SPACING = 6.dp

private const val IH_AMBIENT_PRIMARY_ALPHA = 0.22f
private const val IH_AMBIENT_SECONDARY_ALPHA = 0.12f
private const val IH_COLOR_TRANSITION_DURATION_MS = 800
private const val IH_CLOCK_UPDATE_INTERVAL_MS = 1000L
private const val IH_BATTERY_MAX = 100

private val IH_BORDER_WIDTH = 1.dp
private val IH_BUTTON_CORNER_RADIUS = 10.dp
private val IH_SCROLL_FADE_TOP_HEIGHT = 20.dp
private val IH_SCROLL_FADE_BOTTOM_HEIGHT = 36.dp
private const val IH_SCROLL_FADE_BOTTOM_ALPHA = 0.7f
private const val IH_HIGHLIGHT_ALPHA = 0.15f
private const val IH_INACTIVE_DOT_ALPHA = 0.4f
private val IH_BUTTON_SPACING = 10.dp

private val IH_BATTERY_LOW_COLOR = Color(0xFFE57373)
private val IH_STATUS_ACTIVE_COLOR = Color(0xFF81C784)

@Composable
fun IntegrationHomeScreen(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current

    val clientPackage by AppStateManager.externalClientPackage.collectAsState()
    val isClientActive by AppStateManager.isExternalClientActive.collectAsState()
    val activeProfile by MacroPadState.activeProfile.collectAsState()
    val hoveredAppLabel by AppStateManager.hoveredAppLabel.collectAsState()

    val privdState by PrivdManager.state.collectAsState()
    val isCapturing by ScreenCaptureManager.isCapturing.collectAsState()
    val isAccessibilityActive by AppStateManager.isAccessibilityActive.collectAsState()
    val isFullscreenKeyboardActive by AppStateManager.isFullscreenKeyboardActive.collectAsState()
    val isFullscreenMouseActive by AppStateManager.isFullscreenMouseActive.collectAsState()

    val hoveredPrimaryColor by AppStateManager.hoveredAppPrimaryColor.collectAsState()
    val hoveredSecondaryColor by AppStateManager.hoveredAppSecondaryColor.collectAsState()
    val hoveredPackage by AppStateManager.hoveredAppPackageName.collectAsState()
    val hoveredRomPath by AppStateManager.hoveredRomPath.collectAsState()
    val hoveredSystemId by AppStateManager.hoveredSystemId.collectAsState()
    val activeSession by EmulatorDetectionFunnel.activeSession.collectAsState()
    val lastDetectedSession by EmulatorDetectionFunnel.lastDetectedSession.collectAsState()

    val isGameFocus = isClientActive && clientPackage?.startsWith(MegingiardIpcContract.GAMEFOCUS_PACKAGE) == true

    val targetPrimary =
        remember(isGameFocus, hoveredPrimaryColor) {
            if (isGameFocus && hoveredPrimaryColor != null) {
                Color(hoveredPrimaryColor!!).copy(alpha = IH_AMBIENT_PRIMARY_ALPHA)
            } else {
                colors.appBackground
            }
        }
    val targetSecondary =
        remember(isGameFocus, hoveredSecondaryColor) {
            if (isGameFocus && hoveredSecondaryColor != null) {
                Color(hoveredSecondaryColor!!).copy(alpha = IH_AMBIENT_SECONDARY_ALPHA)
            } else {
                colors.appBackground
            }
        }

    val animatedPrimary by animateColorAsState(
        targetValue = targetPrimary,
        animationSpec = tween(durationMillis = IH_COLOR_TRANSITION_DURATION_MS),
        label = "ambientPrimary",
    )
    val animatedSecondary by animateColorAsState(
        targetValue = targetSecondary,
        animationSpec = tween(durationMillis = IH_COLOR_TRANSITION_DURATION_MS),
        label = "ambientSecondary",
    )

    val batteryState = rememberBatteryState()
    var timeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        while (true) {
            timeText = LocalDateTime.now().format(formatter)
            delay(IH_CLOCK_UPDATE_INTERVAL_MS)
        }
    }

    DisposableEffect(Unit) {
        AppLog.d(TAG, "IntegrationHomeScreen: ON_START")
        onDispose {
            AppLog.d(TAG, "IntegrationHomeScreen: ON_DISPOSE")
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(colors.appBackground, animatedSecondary, animatedPrimary),
                            ),
                    )
                },
    ) {
        // Top Header Row (Clock on Left, Battery on Right)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = IH_PADDING_SCREEN,
                        top = IH_PADDING_HEADER_TOP,
                        end = IH_PADDING_SCREEN,
                        bottom = IH_PADDING_HEADER_BOTTOM,
                    ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Clock (Upper Left)
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurfaceSecondary,
                fontWeight = FontWeight.Bold,
            )

            // Battery Indicator (Upper Right)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IH_BATTERY_SPACING),
            ) {
                val batteryIcon =
                    when {
                        batteryState.isCharging -> Icons.Rounded.BatteryChargingFull
                        batteryState.percentage <= IH_BATTERY_LOW_THRESHOLD -> Icons.Rounded.BatteryAlert
                        else -> Icons.Rounded.BatteryFull
                    }
                Icon(
                    imageVector = batteryIcon,
                    contentDescription = null,
                    tint =
                        if (batteryState.percentage <= IH_BATTERY_LOW_THRESHOLD &&
                            !batteryState.isCharging
                        ) {
                            IH_BATTERY_LOW_COLOR
                        } else {
                            colors.onSurfaceSecondary
                        },
                    modifier = Modifier.size(IH_BATTERY_ICON_SIZE),
                )
                Text(
                    text = "${batteryState.percentage}%",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurfaceSecondary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            val scrollState = rememberScrollState()
            val canScrollUp by remember { derivedStateOf { scrollState.value > 0 } }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = IH_PADDING_SCREEN, end = IH_PADDING_SCREEN),
                verticalArrangement = Arrangement.spacedBy(IH_SPACING_SECTION),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(top = IH_SCROLL_TOP_MARGIN),
                        verticalArrangement = Arrangement.spacedBy(IH_SPACING_SECTION),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 1. Featured Hero Game / Companion Card
                        val profiles by MacroPadState.profiles.collectAsState()

                        val targetPkg = hoveredPackage ?: activeSession?.packageName ?: lastDetectedSession?.packageName
                        val targetLabel =
                            hoveredAppLabel ?: activeSession?.gameTitle ?: lastDetectedSession?.let { s ->
                                s.romPath?.let { File(it).name } ?: s.gameTitle
                            }
                        val targetRom = hoveredRomPath ?: activeSession?.romPath ?: lastDetectedSession?.romPath
                        val targetSystem = hoveredSystemId ?: activeSession?.systemId ?: lastDetectedSession?.systemId

                        val associatedProfile =
                            remember(profiles, targetPkg, targetRom, targetSystem) {
                                if (targetPkg == null) {
                                    null
                                } else {
                                    profiles.firstOrNull { profile ->
                                        profile.association?.romFileName != null &&
                                            profile.matches(targetPkg, targetRom, targetSystem)
                                    } ?: profiles.firstOrNull { profile ->
                                        profile.association?.romFileName == null &&
                                            profile.matches(targetPkg, targetRom, targetSystem)
                                    }
                                }
                            }

                        HeroCompanionCard(
                            targetPackage = targetPkg,
                            targetLabel = targetLabel,
                            targetRomPath = targetRom,
                            targetSystemId = targetSystem,
                            associatedProfile = associatedProfile,
                            activeProfile = activeProfile,
                            profiles = profiles,
                            colors = colors,
                        )

                        // 2. Companion Tools Quick Deck (2x2 Grid of Cards)
                        CompanionToolsDeck(
                            activeProfile = activeProfile,
                            isCapturing = isCapturing,
                            isFullscreenKeyboardActive = isFullscreenKeyboardActive,
                            isFullscreenMouseActive = isFullscreenMouseActive,
                            colors = colors,
                        )

                        // 3. Companion System Status & Actions Card
                        CompanionStatusCard(
                            privdState = privdState,
                            isCapturing = isCapturing,
                            isAccessibilityActive = isAccessibilityActive,
                            colors = colors,
                        )

                        Spacer(modifier = Modifier.height(IH_SCROLL_BOTTOM_MARGIN))
                    }
                }
            }

            // Top fade indicator
            if (canScrollUp) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(IH_SCROLL_FADE_TOP_HEIGHT)
                            .background(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(colors.appBackground, Color.Transparent),
                                    ),
                            ),
                )
            }

            // Bottom fade indicator
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(IH_SCROLL_FADE_BOTTOM_HEIGHT)
                        .drawBehind {
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = IH_SCROLL_FADE_BOTTOM_ALPHA)),
                                    ),
                            )
                        },
            )
        }
    }
}

@Composable
private fun HeroCompanionCard(
    targetPackage: String?,
    targetLabel: String?,
    targetRomPath: String?,
    targetSystemId: String?,
    associatedProfile: PadProfile?,
    activeProfile: PadProfile?,
    profiles: List<PadProfile>,
    colors: AppColors,
) {
    val context = LocalContext.current
    var expandedDropdown by remember { mutableStateOf(false) }
    val unassignedProfiles = remember(profiles) { profiles.filter { it.association == null } }

    val hasActiveGame = targetLabel != null || targetPackage != null

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(IH_BORDER_WIDTH, brush = rememberQuickMenuBezelBrush(), shape = IH_CARD_SHAPE),
        shape = IH_CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Column(
            modifier = Modifier.padding(IH_PADDING_CARD),
            verticalArrangement = Arrangement.spacedBy(IH_SPACING_CARD),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IH_SPACING_CARD),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(IH_HERO_ICON_BG_SIZE)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = IH_HIGHLIGHT_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (hasActiveGame) Icons.Rounded.SportsEsports else Icons.Rounded.Gamepad,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(IH_HERO_ICON_SIZE),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            if (hasActiveGame) {
                                (
                                    targetLabel ?: stringResource(
                                        R.string.integration_home_hovered_game,
                                    )
                                )
                            } else {
                                stringResource(R.string.integration_home_hero_idle_title)
                            },
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                    )

                    if (hasActiveGame) {
                        val sysInfo =
                            listOfNotNull(
                                targetSystemId?.uppercase(),
                                targetRomPath?.substringAfterLast('/'),
                            ).joinToString(" • ")
                        if (sysInfo.isNotEmpty()) {
                            Text(
                                text = sysInfo,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceSecondary,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.integration_home_hero_idle_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                }
            }

            if (hasActiveGame && targetPackage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.integration_home_linked_profile),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary,
                        )
                        Text(
                            text = associatedProfile?.name ?: stringResource(R.string.integration_home_no_profile_active),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (associatedProfile != null) colors.accent else colors.onSurfaceSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (associatedProfile != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(IH_BUTTON_SPACING)) {
                            Button(
                                onClick = {
                                    MacroPadState.setActiveProfileId(associatedProfile.id)
                                    AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                                },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.onAccent,
                                    ),
                                shape = RoundedCornerShape(IH_BUTTON_CORNER_RADIUS),
                            ) {
                                Text(
                                    text = stringResource(R.string.integration_home_launch_controls),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    MacroPadState.setActiveProfileId(associatedProfile.id)
                                    AppStateManager.setEditorActive(true)
                                },
                                border = BorderStroke(IH_BORDER_WIDTH, colors.controlOverlayBorder),
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = colors.onSurface,
                                    ),
                                shape = RoundedCornerShape(IH_BUTTON_CORNER_RADIUS),
                            ) {
                                Text(
                                    text = stringResource(R.string.integration_home_edit_layout),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(IH_BUTTON_SPACING)) {
                            Button(
                                onClick = {
                                    val newProfileId = UUID.randomUUID().toString()
                                    val defaultLayoutId = UUID.randomUUID().toString()
                                    val assoc =
                                        ProfileAssociation(
                                            packageName = targetPackage,
                                            systemId = targetSystemId,
                                            romFileName = targetRomPath?.substringAfterLast('/'),
                                        )
                                    val newProfile =
                                        PadProfile(
                                            id = newProfileId,
                                            name = targetLabel ?: context.getString(R.string.integration_home_new_profile),
                                            association = assoc,
                                            layouts =
                                                listOf(
                                                    PadLayout(
                                                        id = defaultLayoutId,
                                                        name = context.getString(R.string.integration_home_default_layout),
                                                        mirrorCutouts = listOf(ScreenCutout.createDefault()),
                                                    ),
                                                ),
                                            activeLayoutId = defaultLayoutId,
                                        )
                                    MacroPadState.addProfile(newProfile)
                                    MacroPadState.setActiveProfileId(newProfileId)
                                    AppStateManager.setEditorActive(true)
                                },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.onAccent,
                                    ),
                                shape = RoundedCornerShape(IH_BUTTON_CORNER_RADIUS),
                            ) {
                                Text(
                                    text = stringResource(R.string.integration_home_create_profile),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Box {
                                OutlinedButton(
                                    onClick = { expandedDropdown = true },
                                    border = BorderStroke(IH_BORDER_WIDTH, colors.controlOverlayBorder),
                                    colors =
                                        ButtonDefaults.outlinedButtonColors(
                                            contentColor = colors.onSurface,
                                        ),
                                    shape = RoundedCornerShape(IH_BUTTON_CORNER_RADIUS),
                                ) {
                                    Text(
                                        text = stringResource(R.string.integration_home_link_existing),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }

                                DropdownMenu(
                                    expanded = expandedDropdown,
                                    onDismissRequest = { expandedDropdown = false },
                                    modifier = Modifier.background(colors.surface),
                                ) {
                                    if (unassignedProfiles.isEmpty()) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(R.string.integration_home_no_unassigned_profiles),
                                                    color = colors.onSurfaceSecondary,
                                                )
                                            },
                                            onClick = { expandedDropdown = false },
                                        )
                                    } else {
                                        unassignedProfiles.forEach { profile ->
                                            DropdownMenuItem(
                                                text = { Text(profile.name, color = colors.onSurface) },
                                                onClick = {
                                                    expandedDropdown = false
                                                    val assoc =
                                                        ProfileAssociation(
                                                            packageName = targetPackage,
                                                            systemId = targetSystemId,
                                                            romFileName = targetRomPath?.substringAfterLast('/'),
                                                        )
                                                    val updatedProfile = profile.copy(association = assoc)
                                                    MacroPadState.updateProfile(updatedProfile)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (activeProfile != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.integration_home_active_profile),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceSecondary,
                        )
                        Text(
                            text = activeProfile.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Button(
                        onClick = {
                            AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD)
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.onAccent,
                            ),
                        shape = RoundedCornerShape(IH_BUTTON_CORNER_RADIUS),
                    ) {
                        Text(
                            text = stringResource(R.string.integration_home_launch_controls),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionToolsDeck(
    activeProfile: PadProfile?,
    isCapturing: Boolean,
    isFullscreenKeyboardActive: Boolean,
    isFullscreenMouseActive: Boolean,
    colors: AppColors,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(IH_SPACING_GRID),
    ) {
        Text(
            text = stringResource(R.string.integration_home_deck_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceSecondary,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(IH_SPACING_GRID),
        ) {
            // Card 1: MacroPad
            ToolCard(
                title = stringResource(R.string.integration_home_tool_macropad),
                subtitle = activeProfile?.name ?: stringResource(R.string.integration_home_macropad_desc),
                icon = Icons.Rounded.Gamepad,
                isActive = activeProfile != null,
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = { AppStateManager.setCompanionViewMode(CompanionViewMode.MACROPAD) },
            )

            // Card 2: Touchpad
            ToolCard(
                title = stringResource(R.string.integration_home_tool_touchpad),
                subtitle = stringResource(R.string.integration_home_touchpad_desc),
                icon = Icons.Rounded.TouchApp,
                isActive = isFullscreenMouseActive,
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = { AppStateManager.setFullscreenMouseActive(!isFullscreenMouseActive) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(IH_SPACING_GRID),
        ) {
            // Card 3: Virtual Keyboard
            ToolCard(
                title = stringResource(R.string.integration_home_tool_keyboard),
                subtitle = stringResource(R.string.integration_home_keyboard_desc),
                icon = Icons.Rounded.Keyboard,
                isActive = isFullscreenKeyboardActive,
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = { AppStateManager.setFullscreenKeyboardActive(!isFullscreenKeyboardActive) },
            )

            // Card 4: Screen Mirror
            ToolCard(
                title = stringResource(R.string.integration_home_tool_mirror),
                subtitle =
                    if (isCapturing) {
                        stringResource(
                            R.string.integration_home_status_active,
                        )
                    } else {
                        stringResource(R.string.integration_home_status_inactive)
                    },
                icon = Icons.Rounded.Airplay,
                isActive = isCapturing,
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (isCapturing) {
                        AppStateManager.requestMirrorStop()
                    } else {
                        AppStateManager.requestMirrorStart()
                    }
                },
            )
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    colors: AppColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            modifier
                .border(IH_BORDER_WIDTH, brush = rememberQuickMenuBezelBrush(), shape = IH_DECK_CARD_SHAPE)
                .clickable { onClick() },
        shape = IH_DECK_CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Row(
            modifier = Modifier.padding(IH_PADDING_CARD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IH_SPACING_CARD),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(IH_DECK_ICON_BG_SIZE)
                        .clip(CircleShape)
                        .background(if (isActive) colors.accent.copy(alpha = IH_HIGHLIGHT_ALPHA) else colors.controlOverlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) colors.accent else colors.onSurfaceSecondary,
                    modifier = Modifier.size(IH_DECK_ICON_SIZE),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) colors.accent else colors.onSurfaceSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CompanionStatusCard(
    privdState: PrivdState,
    isCapturing: Boolean,
    isAccessibilityActive: Boolean,
    colors: AppColors,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(IH_BORDER_WIDTH, brush = rememberQuickMenuBezelBrush(), shape = IH_CARD_SHAPE),
        shape = IH_CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Column(
            modifier = Modifier.padding(IH_PADDING_CARD),
            verticalArrangement = Arrangement.spacedBy(IH_SPACING_CARD),
        ) {
            Text(
                text = stringResource(R.string.integration_home_status_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceSecondary,
                fontWeight = FontWeight.SemiBold,
            )

            // Privileged Daemon Row
            StatusRow(
                label = stringResource(R.string.integration_home_status_privd),
                icon = Icons.Rounded.LockOpen,
                isActive = privdState == PrivdState.RUNNING,
                activeLabel = stringResource(R.string.integration_home_status_running),
                inactiveLabel = stringResource(R.string.integration_home_status_stopped),
                colors = colors,
            )

            // Mirror Session Row
            StatusRow(
                label = stringResource(R.string.integration_home_status_mirror),
                icon = Icons.Rounded.Airplay,
                isActive = isCapturing,
                activeLabel = stringResource(R.string.integration_home_status_active),
                inactiveLabel = stringResource(R.string.integration_home_status_inactive),
                colors = colors,
            )

            // Accessibility Service Row
            StatusRow(
                label = stringResource(R.string.integration_home_status_accessibility),
                icon = Icons.Rounded.Accessibility,
                isActive = isAccessibilityActive,
                activeLabel = stringResource(R.string.integration_home_status_active),
                inactiveLabel = stringResource(R.string.integration_home_status_inactive),
                colors = colors,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IH_BUTTON_SPACING),
            ) {
                OutlinedButton(
                    onClick = { AppStateManager.setGlobalSettingsOpen(true) },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(IH_BORDER_WIDTH, colors.controlOverlayBorder),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.onSurface,
                        ),
                    shape = RoundedCornerShape(IH_BUTTON_CORNER_RADIUS),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(IH_STATUS_ICON_SIZE).padding(end = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.integration_home_settings),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                OutlinedButton(
                    onClick = { AppStateManager.openQuickMenu() },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(IH_BORDER_WIDTH, colors.controlOverlayBorder),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.onSurface,
                        ),
                    shape = RoundedCornerShape(IH_BUTTON_CORNER_RADIUS),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(IH_STATUS_ICON_SIZE).padding(end = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.integration_home_help),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private data class BatteryState(
    val percentage: Int,
    val isCharging: Boolean,
)

@Composable
private fun rememberBatteryState(): BatteryState {
    val context = LocalContext.current
    var batteryState by remember { mutableStateOf(BatteryState(IH_BATTERY_MAX, false)) }

    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging =
                        status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    val pct = if (level >= 0 && scale > 0) (level * IH_BATTERY_MAX / scale) else IH_BATTERY_MAX
                    batteryState = BatteryState(pct, isCharging)
                }
            }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        if (stickyIntent != null) {
            val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging =
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) (level * IH_BATTERY_MAX / scale) else IH_BATTERY_MAX
            batteryState = BatteryState(pct, isCharging)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    return batteryState
}

@Composable
private fun StatusRow(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    activeLabel: String,
    inactiveLabel: String,
    colors: AppColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IH_SPACING_STATUS),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onSurfaceSecondary,
                modifier = Modifier.size(IH_STATUS_ICON_SIZE),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IH_SPACING_STATUS),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(IH_STATUS_DOT_SIZE)
                        .clip(CircleShape)
                        .background(
                            if (isActive) IH_STATUS_ACTIVE_COLOR else colors.onSurfaceSecondary.copy(alpha = IH_INACTIVE_DOT_ALPHA),
                        ),
            )
            Text(
                text = if (isActive) activeLabel else inactiveLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) colors.accent else colors.onSurfaceSecondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
