package com.stormpanda.megingiard.mirror

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.R
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.ui.LocalAppColors

// ---------------------------------------------------------------------------
// Control panel layout constants
// ---------------------------------------------------------------------------
private val CONTROL_BUTTON_SIZE = 72.dp
private val CONTROL_ICON_SIZE = 36.dp
private val CONTROL_BUTTON_GAP = 16.dp
private val CONTROL_PILL_H_PADDING = 12.dp
private val CONTROL_PILL_V_PADDING = 10.dp

/**
 * The overlay control pill shown while a capture session is active.
 * Contains Stop, Freeze/Unfreeze, Lock/Unlock, and Touch Projection toggle buttons.
 */
@Composable
internal fun MirrorControlPanel(
    visible: Boolean,
    isCapturing: Boolean,
    isFrozen: Boolean,
    isLocked: Boolean,
    isTouchProjectionActive: Boolean,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current

    AnimatedVisibility(
        visible = visible && isCapturing,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(colors.controlOverlay, RoundedCornerShape(50))
                    .border(2.dp, colors.mirrorPillBorder, RoundedCornerShape(50))
                    .padding(
                        horizontal = CONTROL_PILL_H_PADDING,
                        vertical = CONTROL_PILL_V_PADDING
                    ),
                horizontalArrangement = Arrangement.spacedBy(CONTROL_BUTTON_GAP)
            ) {
                // Stop
                IconButton(
                    onClick = {
                        SettingsManager.saveMirrorSessionState()
                        AppStateManager.setUserDeclinedCapture(true)
                        context.stopService(Intent(context, ScreenCaptureService::class.java))
                        ScreenCaptureManager.setCapturing(false)
                        ScreenCaptureManager.resetMirrorSessionState()
                    },
                    modifier = Modifier
                        .size(CONTROL_BUTTON_SIZE)
                        .background(colors.buttonBody, RoundedCornerShape(50))
                        .border(2.dp, colors.navPillBorder, RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = stringResource(R.string.cd_stop_mirroring),
                        tint = colors.buttonIconTint,
                        modifier = Modifier.size(CONTROL_ICON_SIZE)
                    )
                }

                // Freeze / Unfreeze — disabled when Touch Projection is active
                // (projecting touches to a frozen display would be meaningless).
                IconButton(
                    onClick = { ScreenCaptureManager.toggleFrozen() },
                    enabled = !isTouchProjectionActive,
                    modifier = Modifier
                        .size(CONTROL_BUTTON_SIZE)
                        .background(
                            color = when {
                                isTouchProjectionActive -> colors.buttonBody.copy(alpha = 0.12f)
                                isFrozen -> colors.buttonBody
                                else -> colors.buttonBody.copy(alpha = 0.35f)
                            },
                            shape = RoundedCornerShape(50)
                        )
                        .border(
                            width = 2.dp,
                            color = colors.navPillBorder.copy(
                                alpha = colors.navPillBorder.alpha * when {
                                    isTouchProjectionActive -> 0.12f
                                    isFrozen -> 1f
                                    else -> 0.4f
                                }
                            ),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = if (isFrozen) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = stringResource(
                            if (isFrozen) R.string.cd_unfreeze else R.string.cd_freeze
                        ),
                        tint = colors.buttonIconTint.copy(alpha = if (isTouchProjectionActive) 0.38f else 1f),
                        modifier = Modifier.size(CONTROL_ICON_SIZE)
                    )
                }

                // Lock / Unlock (also deactivates touch projection when unlocking,
                // since projection requires lock as a precondition).
                IconButton(
                    onClick = { ScreenCaptureManager.toggleLocked() },
                    modifier = Modifier
                        .size(CONTROL_BUTTON_SIZE)
                        .background(
                            color = if (isLocked) colors.buttonBody else colors.buttonBody.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(50)
                        )
                        .border(
                            width = 2.dp,
                            color = colors.navPillBorder.copy(alpha = colors.navPillBorder.alpha * if (isLocked) 1f else 0.4f),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = stringResource(
                            if (isLocked) R.string.cd_unlock_view else R.string.cd_lock_view
                        ),
                        tint = colors.buttonIconTint,
                        modifier = Modifier.size(CONTROL_ICON_SIZE)
                    )
                }

                // Touch Projection on / off — disabled when stream is frozen
                // (touches on a still image cannot be forwarded meaningfully).
                IconButton(
                    onClick = { ScreenCaptureManager.toggleTouchProjection() },
                    enabled = !isFrozen,
                    modifier = Modifier
                        .size(CONTROL_BUTTON_SIZE)
                        .background(
                            color = when {
                                isFrozen -> colors.buttonBody.copy(alpha = 0.12f)
                                isTouchProjectionActive -> colors.buttonBody
                                else -> colors.buttonBody.copy(alpha = 0.35f)
                            },
                            shape = RoundedCornerShape(50)
                        )
                        .border(
                            width = 2.dp,
                            color = colors.navPillBorder.copy(
                                alpha = colors.navPillBorder.alpha * when {
                                    isFrozen -> 0.12f
                                    isTouchProjectionActive -> 1f
                                    else -> 0.4f
                                }
                            ),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = stringResource(
                            if (isTouchProjectionActive) R.string.cd_touch_projection_off
                            else R.string.cd_touch_projection_on
                        ),
                        tint = colors.buttonIconTint.copy(alpha = if (isFrozen) 0.38f else 1f),
                        modifier = Modifier.size(CONTROL_ICON_SIZE)
                    )
                }
            }
        }
    }
}
