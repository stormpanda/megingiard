package com.stormpanda.megingiard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "DialogToastManager"

private const val DTM_TOAST_DURATION_MS = 3000L
private const val DTM_ANIM_DURATION_MS = 250
private const val DTM_BG_ALPHA = 0.16f
private const val DTM_BORDER_ALPHA = 0.45f
private const val DTM_CORNER_DP = 16
private const val DTM_BORDER_WIDTH_DP = 1
private const val DTM_PADDING_H_DP = 12
private const val DTM_PADDING_V_DP = 5
private const val DTM_SPACING_DP = 6
private const val DTM_ICON_SIZE_DP = 14
private const val DTM_FONT_SIZE_SP = 13
private const val DTM_MAX_LINES = 3
private const val DTM_LINE_HEIGHT_SP = 17

/**
 * Data model for a toast message displayed inside an overlay dialog header.
 */
data class DialogToast(
    val message: String,
    val icon: ImageVector? = Icons.Rounded.CheckCircle,
    val isError: Boolean = false,
)

/**
 * Singleton state holder for in-dialog toast notifications.
 * Displays temporary animated toast feedback inside [PrimaryOverlayContainer] or [FullScreenTopBar] headers.
 */
object DialogToastManager {
    private val _currentToast = MutableStateFlow<DialogToast?>(null)
    val currentToast: StateFlow<DialogToast?> = _currentToast.asStateFlow()

    private var toastJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun show(
        message: String,
        durationMs: Long = DTM_TOAST_DURATION_MS,
        icon: ImageVector? = Icons.Rounded.CheckCircle,
        isError: Boolean = false,
    ) {
        AppLog.d(TAG, "show: '$message' (isError=$isError)")
        toastJob?.cancel()
        _currentToast.value = DialogToast(message, icon, isError)
        toastJob =
            scope.launch {
                delay(durationMs)
                _currentToast.value = null
            }
    }

    fun clear() {
        toastJob?.cancel()
        _currentToast.value = null
    }
}

/**
 * Animated pill chip rendering the active in-dialog toast message.
 */
@Composable
fun DialogToastPill(
    toast: DialogToast?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    AnimatedVisibility(
        visible = toast != null,
        enter =
            fadeIn(animationSpec = tween(DTM_ANIM_DURATION_MS)) +
                slideInVertically(animationSpec = tween(DTM_ANIM_DURATION_MS)) { -it / 2 },
        exit =
            fadeOut(animationSpec = tween(DTM_ANIM_DURATION_MS)) +
                slideOutVertically(animationSpec = tween(DTM_ANIM_DURATION_MS)) { -it / 2 },
        modifier = modifier,
    ) {
        if (toast != null) {
            val pillColor = if (toast.isError) colors.error else colors.accent
            Row(
                modifier =
                    Modifier
                        .background(
                            color = pillColor.copy(alpha = DTM_BG_ALPHA),
                            shape = RoundedCornerShape(DTM_CORNER_DP.dp),
                        ).border(
                            width = DTM_BORDER_WIDTH_DP.dp,
                            color = pillColor.copy(alpha = DTM_BORDER_ALPHA),
                            shape = RoundedCornerShape(DTM_CORNER_DP.dp),
                        ).padding(
                            horizontal = DTM_PADDING_H_DP.dp,
                            vertical = DTM_PADDING_V_DP.dp,
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DTM_SPACING_DP.dp),
            ) {
                if (toast.icon != null) {
                    Icon(
                        imageVector = toast.icon,
                        contentDescription = null,
                        tint = pillColor,
                        modifier = Modifier.size(DTM_ICON_SIZE_DP.dp),
                    )
                }
                Text(
                    text = toast.message,
                    color = colors.onSurface,
                    fontSize = DTM_FONT_SIZE_SP.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = DTM_LINE_HEIGHT_SP.sp,
                    maxLines = DTM_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}
