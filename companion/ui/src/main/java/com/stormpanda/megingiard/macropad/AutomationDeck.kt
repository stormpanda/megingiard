package com.stormpanda.megingiard.macropad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Anchor
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.mirror.CutoutMaskManager
import com.stormpanda.megingiard.mirror.HudPresenceManager
import com.stormpanda.megingiard.mirror.HudPresenceState
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadPill
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.firstDeckItem

private const val TAG = "AutomationDeck"
private val AD_PILL_SPACING = 6.dp

/**
 * Deck content composable for the [EditorSection.AUTOMATION] section.
 *
 * Provides a centralized hub for managing profile-wide automatic layout switching,
 * inspecting layout visual anchor calibration status, and navigating directly
 * into per-layout anchor configuration.
 */
@Composable
internal fun AutomationDeckContent(
    profile: PadProfile?,
    activeLayout: PadLayout?,
    accentColor: Color,
    onOpenAnchorSettings: (layoutId: String) -> Unit,
    onToggleAutoSwitch: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val presenceRevision by HudPresenceManager.presenceRevision.collectAsStateWithLifecycle()

    LaunchedEffect(profile?.id) {
        AppLog.d(TAG, "AutomationDeckContent mounted for profile: ${profile?.name} (${profile?.id})")
    }

    if (profile == null) return

    val layouts = profile.layouts
    val calibratedLayouts =
        remember(profile.id, layouts, presenceRevision) {
            layouts.filter { lay -> CutoutMaskManager.isLayoutAnchorCalibrated(context, lay.id) }
        }
    val calibratedCount = calibratedLayouts.size
    val totalCount = layouts.size

    // ── Master Profile Automation Toggle ──────────────────────────────
    GamepadToggleCard(
        modifier = Modifier.firstDeckItem(),
        title = stringResource(R.string.settings_profile_auto_layout_switching_title),
        description = stringResource(R.string.settings_profile_auto_layout_switching_desc),
        checked = profile.autoLayoutSwitching,
        icon = Icons.Rounded.AutoAwesome,
        itemKey = "automation_profile_master_toggle",
        onCheckedChange = onToggleAutoSwitch,
    )

    // ── Layout Reference Anchors Section ──────────────────────────────
    GamepadSectionHeader(
        text = stringResource(R.string.automation_layout_anchors_section_title),
        color = accentColor,
    )

    if (calibratedCount == 0) {
        GamepadInfoBox(
            text = stringResource(R.string.automation_no_anchors_warning),
            icon = Icons.Rounded.Warning,
            iconTint = accentColor,
        )
    } else {
        GamepadInfoBox(
            text = stringResource(R.string.automation_anchors_status_summary, calibratedCount, totalCount),
            icon = Icons.Rounded.Info,
            iconTint = accentColor,
        )
    }

    layouts.forEach { layout ->
        val isCalibrated =
            remember(layout.id, presenceRevision) {
                CutoutMaskManager.isLayoutAnchorCalibrated(context, layout.id)
            }
        val signature =
            remember(layout.id, presenceRevision) {
                CutoutMaskManager.getLayoutAnchorSignature(context, layout.id)
            }
        val isActive = layout.id == activeLayout?.id
        val presenceState =
            remember(layout.id, presenceRevision) {
                HudPresenceManager.getLayoutPresenceState(layout.id)
            }

        val cardDesc =
            when {
                isCalibrated && signature != null && signature.points.isNotEmpty() -> {
                    stringResource(R.string.layout_settings_visual_anchor_status_calibrated, signature.points.size)
                }

                isCalibrated -> {
                    stringResource(R.string.automation_anchor_calibrated_badge)
                }

                else -> {
                    stringResource(R.string.automation_anchor_not_configured)
                }
            }

        GamepadActionCard(
            title = layout.name,
            description = cardDesc,
            icon = Icons.Rounded.Anchor,
            itemKey = "automation_anchor_layout_${layout.id}",
            onClick = { onOpenAnchorSettings(layout.id) },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AD_PILL_SPACING),
                ) {
                    if (isActive) {
                        if (isCalibrated && layout.visualAnchor.enabled) {
                            if (presenceState == HudPresenceState.LOST) {
                                GamepadPill(
                                    text = stringResource(R.string.automation_anchor_lost_badge),
                                    isHighlighted = false,
                                )
                            } else {
                                GamepadPill(
                                    text = stringResource(R.string.automation_anchor_present_badge),
                                    isHighlighted = true,
                                )
                            }
                        }
                        GamepadPill(
                            text = stringResource(R.string.automation_anchor_active_badge),
                            isAccent = true,
                        )
                    } else {
                        if (isCalibrated) {
                            GamepadPill(
                                text = stringResource(R.string.automation_anchor_calibrated_badge),
                                isHighlighted = false,
                            )
                        } else {
                            GamepadPill(
                                text = stringResource(R.string.automation_anchor_configure_badge),
                                isHighlighted = false,
                            )
                        }
                    }
                }
            },
        )
    }
}
