package com.stormpanda.megingiard.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG = "GamepadScaffold"

private val GS_SIDEBAR_WIDTH = 180.dp
private val GS_SIDEBAR_ITEM_HEIGHT = 40.dp
private val GS_SIDEBAR_CORNER = 10.dp
private val GS_SIDEBAR_SHAPE = RoundedCornerShape(GS_SIDEBAR_CORNER)
private val GS_SIDEBAR_ICON_SIZE = 20.dp
private val GS_FOCUS_STROKE_WIDTH = 1.5.dp
private val GS_ZERO_BORDER_WIDTH = 0.dp
private val GS_SIDEBAR_ITEM_PADDING_H = 12.dp
private val GS_SIDEBAR_ITEM_SPACING = 10.dp
private val GS_SIDEBAR_PADDING = 8.dp
private val GS_SIDEBAR_SPACING = 4.dp
private val GS_DECK_PADDING_H = 16.dp
private val GS_DECK_PADDING_V = 12.dp
private val GS_DECK_SPACING = 10.dp
private val GS_DECK_SCROLL_EXTRA_PADDING = 64.dp
private const val GS_SIDEBAR_SELECTED_FOCUSED_ALPHA = 0.35f
private const val GS_SIDEBAR_SELECTED_ALPHA = 0.2f
private const val GS_CARD_FOCUSED_BG_ALPHA = 0.95f
private const val GS_SIDEBAR_BG_ALPHA = 0.5f
private const val GS_INITIAL_FOCUS_DELAY_MS = 50L
private const val GS_BREADCRUMB_FADE_DURATION_MS = 150

val LocalActiveCategoryRequester = compositionLocalOf<FocusRequester?> { null }
val LocalFirstContentRequester = compositionLocalOf<FocusRequester?> { null }
val LocalTransferFocusToDeck = compositionLocalOf<(() -> Unit)?> { null }
val LocalLastFocusedDeckTracker = compositionLocalOf<((key: Any, requester: FocusRequester) -> Unit)?> { null }
val LocalDeckCardRegistry = compositionLocalOf<((key: Any, requester: FocusRequester?) -> Unit)?> { null }
val LocalResetLastFocusedTracker = compositionLocalOf<(() -> Unit)?> { null }
val LocalDeckBackInterceptor = compositionLocalOf<((interceptor: () -> Boolean, active: Boolean) -> Unit)?> { null }
val LocalBreadcrumbConsumer = compositionLocalOf<((List<String>) -> Unit)?> { null }
val LocalSidebarFocusEnabled = compositionLocalOf { true }
val LocalSuppressCategoryAutoSelect = compositionLocalOf { false }

/**
 * Modifier extension to mark a composable card as the primary focus target when entering the right deck from the sidebar.
 */
@Composable
fun Modifier.firstDeckItem(isFirst: Boolean = true): Modifier {
    val requester = LocalFirstContentRequester.current
    return if (requester != null && isFirst) this.focusRequester(requester) else this
}

/**
 * Creates a custom [BringIntoViewSpec] that scrolls with extra top/bottom padding
 * so adjacent menu items or deck headers remain visible during gamepad D-pad navigation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberGamepadBringIntoViewSpec(extraPadding: Dp = GS_DECK_SCROLL_EXTRA_PADDING): BringIntoViewSpec {
    val density = LocalDensity.current
    val extraPaddingPx = with(density) { extraPadding.toPx() }
    return remember(extraPaddingPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val topThreshold = extraPaddingPx
                val bottomThreshold = (containerSize - extraPaddingPx).coerceAtLeast(topThreshold)

                return when {
                    offset < topThreshold -> offset - topThreshold
                    offset + size > bottomThreshold -> (offset + size) - bottomThreshold
                    else -> 0f
                }
            }
        }
    }
}

/**
 * Pinned breadcrumb header with subtle in-place crossfade transition when navigating sub-menus.
 */
@Composable
fun GamepadBreadcrumbHeader(
    breadcrumbs: List<String>,
    modifier: Modifier = Modifier,
) {
    val accentColor = LocalAppColors.current.accent
    AnimatedContent(
        targetState = breadcrumbs,
        transitionSpec = {
            fadeIn(animationSpec = tween(GS_BREADCRUMB_FADE_DURATION_MS)) togetherWith
                fadeOut(animationSpec = tween(GS_BREADCRUMB_FADE_DURATION_MS))
        },
        label = "BreadcrumbFade",
        modifier = modifier,
    ) { crumbs ->
        if (crumbs.isNotEmpty()) {
            GamepadSectionHeader(
                text = crumbs.joinToString("  ›  ") { it.uppercase(Locale.ROOT) },
                color = accentColor,
            )
        }
    }
}

/**
 * Unified right-pane deck container used across root sidebar categories and nested subpages.
 *
 * Automatically manages:
 * - Top breadcrumb / category header trail formatted with ' › ' in uppercase (when rendered inside standalone deck).
 * - Standardized 16.dp horizontal padding and 10.dp vertical spacing.
 * - Gamepad [BringIntoViewSpec] focus scrolling behavior.
 * - Optional vertical scrolling container vs non-scrollable fill (e.g. for LazyLists or custom canvas).
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun GamepadDeck(
    breadcrumbs: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val breadcrumbConsumer = LocalBreadcrumbConsumer.current
    if (breadcrumbConsumer != null && breadcrumbs.isNotEmpty()) {
        DisposableEffect(breadcrumbs) {
            breadcrumbConsumer(breadcrumbs)
            onDispose { }
        }
    }
    val showLocalHeader = breadcrumbConsumer == null && breadcrumbs.isNotEmpty()

    val bringIntoViewSpec = rememberGamepadBringIntoViewSpec()
    val parentRequester = LocalFirstContentRequester.current
    val localRequester = remember { FocusRequester() }
    val effectiveRequester = parentRequester ?: localRequester
    val inputModeManager = LocalInputModeManager.current

    if (parentRequester == null) {
        LaunchedEffect(Unit) {
            if (!PrimaryOverlayInputBridge.isGamepadInputActive.value) {
                AppLog.d(TAG, "GamepadDeck: skipping standalone initial focus while touch input active")
                return@LaunchedEffect
            }
            inputModeManager.requestInputMode(InputMode.Keyboard)
            try {
                effectiveRequester.requestFocus()
                AppLog.d(TAG, "GamepadDeck: standalone deck initial focus requested")
            } catch (_: IllegalStateException) {
                delay(GS_INITIAL_FOCUS_DELAY_MS)
                if (PrimaryOverlayInputBridge.isGamepadInputActive.value) {
                    try {
                        effectiveRequester.requestFocus()
                        AppLog.d(TAG, "GamepadDeck: standalone deck initial focus retry succeeded")
                    } catch (_: IllegalStateException) {
                        AppLog.d(TAG, "GamepadDeck: focus requester unattached on standalone deck initial focus")
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            PrimaryOverlayInputBridge.focusRecoveryEvents.collect { keyCode ->
                inputModeManager.requestInputMode(InputMode.Keyboard)
                try {
                    effectiveRequester.requestFocus()
                    AppLog.d(TAG, "GamepadDeck: focus recovered on standalone deck")
                } catch (_: IllegalStateException) {
                    AppLog.d(TAG, "GamepadDeck: effectiveRequester unattached on standalone focus recovery")
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalBringIntoViewSpec provides bringIntoViewSpec,
        LocalFirstContentRequester provides effectiveRequester,
    ) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(
                        horizontal = GS_DECK_PADDING_H,
                        vertical = if (showLocalHeader) GS_DECK_PADDING_V else GS_ZERO_BORDER_WIDTH,
                    ),
            verticalArrangement = Arrangement.spacedBy(GS_DECK_SPACING),
        ) {
            if (showLocalHeader) {
                GamepadSectionHeader(
                    text = breadcrumbs.joinToString("  ›  ") { it.uppercase(Locale.ROOT) },
                    color = LocalAppColors.current.accent,
                )
            }

            content()
        }
    }
}

/**
 * Convenience overload of [GamepadDeck] for root sidebar categories with a single title.
 */
@Composable
fun GamepadDeck(
    title: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) = GamepadDeck(
    breadcrumbs = if (title.isNotBlank()) listOf(title) else emptyList(),
    modifier = modifier,
    scrollable = scrollable,
    content = content,
)

/**
 * Unified gamepad-first category sidebar item tile used across split-screen dialogs and editors.
 */
@Composable
fun GamepadCategoryTile(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val activeCategoryRequester = LocalActiveCategoryRequester.current
    val resetLastFocused = LocalResetLastFocusedTracker.current
    val sidebarFocusEnabled = LocalSidebarFocusEnabled.current
    val currentSuppressAutoSelect by rememberUpdatedState(LocalSuppressCategoryAutoSelect.current)

    val animatedBg by animateColorAsState(
        targetValue =
            when {
                selected && isFocused -> colors.accent.copy(alpha = GS_SIDEBAR_SELECTED_FOCUSED_ALPHA)
                selected -> colors.accent.copy(alpha = GS_SIDEBAR_SELECTED_ALPHA)
                isFocused -> colors.surface.copy(alpha = GS_CARD_FOCUSED_BG_ALPHA)
                else -> Color.Transparent
            },
        label = "catBg",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accent else Color.Transparent,
        label = "catBorder",
    )

    val requesterModifier =
        if (activeCategoryRequester != null && selected) {
            Modifier.focusRequester(activeCategoryRequester)
        } else {
            Modifier
        }

    val transferFocusToDeck = LocalTransferFocusToDeck.current

    val wrappedOnClick: () -> Unit = {
        onClick()
        transferFocusToDeck?.invoke()
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(GS_SIDEBAR_ITEM_HEIGHT)
                .drawBehind {
                    val cornerRadius = CornerRadius(GS_SIDEBAR_CORNER.toPx())
                    drawRoundRect(
                        color = animatedBg,
                        cornerRadius = cornerRadius,
                    )
                    if (isFocused) {
                        val stroke = GS_FOCUS_STROKE_WIDTH.toPx()
                        val half = stroke / 2f
                        drawRoundRect(
                            color = animatedBorderColor,
                            topLeft = Offset(half, half),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = cornerRadius,
                            style = Stroke(width = stroke),
                        )
                    }
                }.focusProperties {
                    canFocus = sidebarFocusEnabled
                }.onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        if (!selected && !currentSuppressAutoSelect && PrimaryOverlayInputBridge.isGamepadInputActive.value) {
                            resetLastFocused?.invoke()
                            onClick()
                        }
                    }
                }.then(requesterModifier)
                .onPreviewKeyEvent { keyEvent ->
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            transferFocusToDeck?.invoke()
                        }
                        true
                    } else {
                        false
                    }
                }.primaryOverlayFocusable(
                    onClick = wrappedOnClick,
                    onTouchClick = onClick,
                    shape = GS_SIDEBAR_SHAPE,
                    borderWidth = GS_ZERO_BORDER_WIDTH,
                    interactionSource = interactionSource,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = GS_SIDEBAR_ITEM_PADDING_H),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GS_SIDEBAR_ITEM_SPACING),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected || isFocused) colors.accent else colors.onSurfaceSecondary,
                modifier = Modifier.size(GS_SIDEBAR_ICON_SIZE),
            )
            Text(
                text = title,
                color = if (selected || isFocused) colors.onSurface else colors.onSurfaceSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun resolveNavigationDepth(
    navigationKey: Any?,
    isCustomBackActive: Boolean,
): Int =
    when (navigationKey) {
        is Collection<*> -> navigationKey.size
        is Boolean -> if (navigationKey) 1 else 0
        else -> if (isCustomBackActive) 1 else 0
    }

/**
 * Standardized split-screen two-pane scaffold for primary screen settings and editors.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun GamepadTwoPaneScaffold(
    sidebarContent: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    breadcrumbs: List<String> = emptyList(),
    isCustomBackActive: Boolean = false,
    onCustomBack: (() -> Unit)? = null,
    categoryKey: Any? = null,
    navigationKey: Any? = null,
    savedFocusKeys: Map<Int, Any>? = null,
    onRecordFocusedKey: ((depth: Int, key: Any) -> Unit)? = null,
    onRemoveFocusedKey: ((depth: Int) -> Unit)? = null,
    scrollableDeck: Boolean = true,
) {
    val colors = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
    val inputModeManager = LocalInputModeManager.current
    val activeCategoryRequester = remember { FocusRequester() }
    val firstContentRequester = remember { FocusRequester() }
    val activeDeckCardRequesters = remember { mutableMapOf<Any, FocusRequester>() }
    val savedFocusKeyByDepth = remember { mutableMapOf<Int, Any>().apply { if (savedFocusKeys != null) putAll(savedFocusKeys) } }
    val initialDepth = resolveNavigationDepth(navigationKey, isCustomBackActive)
    var currentDepth by remember { mutableIntStateOf(initialDepth) }
    var isDeckFocused by remember { mutableStateOf(false) }
    var isSidebarFocused by remember { mutableStateOf(false) }
    var lastActivePaneIsDeck by rememberSaveable { mutableStateOf(isCustomBackActive) }
    var suppressCategoryAutoSelect by remember { mutableStateOf(false) }
    var registeredBreadcrumbs by remember { mutableStateOf<List<String>>(emptyList()) }
    val effectiveBreadcrumbs = if (breadcrumbs.isNotEmpty()) breadcrumbs else registeredBreadcrumbs

    LaunchedEffect(savedFocusKeys) {
        if (savedFocusKeys != null) {
            savedFocusKeyByDepth.putAll(savedFocusKeys)
        }
    }

    fun tryRequestFocus(requester: FocusRequester): Boolean =
        try {
            requester.requestFocus()
            true
        } catch (_: IllegalStateException) {
            false
        }

    fun focusDeck(
        depth: Int,
        restoreSaved: Boolean,
        allowCardFallback: Boolean,
        allowSidebarFallback: Boolean,
    ): Boolean {
        inputModeManager.requestInputMode(InputMode.Keyboard)
        if (restoreSaved) {
            val savedKey = savedFocusKeyByDepth[depth]
            if (savedKey != null) {
                val savedRequester = activeDeckCardRequesters[savedKey]
                if (savedRequester != null) {
                    if (tryRequestFocus(savedRequester)) {
                        AppLog.d(TAG, "GamepadTwoPaneScaffold: restored focus to card '$savedKey' at depth $depth")
                        return true
                    }
                    savedFocusKeyByDepth.remove(depth)
                    onRemoveFocusedKey?.invoke(depth)
                }
                if (!allowCardFallback) {
                    return false
                }
            }
        }
        if (tryRequestFocus(firstContentRequester)) {
            AppLog.d(TAG, "GamepadTwoPaneScaffold: focused first content item at depth $depth")
            return true
        }
        if (allowCardFallback) {
            val fallbackRequester = activeDeckCardRequesters.values.firstOrNull()
            if (fallbackRequester != null && tryRequestFocus(fallbackRequester)) {
                AppLog.d(TAG, "GamepadTwoPaneScaffold: fallback focus on active deck card at depth $depth")
                return true
            }
        }
        if (allowSidebarFallback && depth == 0 && tryRequestFocus(activeCategoryRequester)) {
            AppLog.d(TAG, "GamepadTwoPaneScaffold: fallback focus on sidebar category at depth 0")
            return true
        }
        return false
    }

    val transferFocusToDeck: () -> Unit = {
        if (!focusDeck(depth = currentDepth, restoreSaved = true, allowCardFallback = true, allowSidebarFallback = false)) {
            coroutineScope.launch {
                delay(GS_INITIAL_FOCUS_DELAY_MS)
                focusDeck(depth = currentDepth, restoreSaved = true, allowCardFallback = true, allowSidebarFallback = false)
            }
        }
    }

    val backInterceptors = remember { mutableStateListOf<() -> Boolean>() }

    val handleBackNavigation: () -> Boolean = {
        val intercepted = backInterceptors.lastOrNull()?.invoke() ?: false
        if (intercepted) {
            true
        } else if (isCustomBackActive && onCustomBack != null) {
            onCustomBack()
            true
        } else if (isDeckFocused && tryRequestFocus(activeCategoryRequester)) {
            lastActivePaneIsDeck = false
            AppLog.d(TAG, "GamepadTwoPaneScaffold: back navigated from deck to sidebar category")
            true
        } else {
            false
        }
    }

    BackHandler(enabled = isCustomBackActive || isDeckFocused) {
        handleBackNavigation()
    }

    val bringIntoViewSpec = rememberGamepadBringIntoViewSpec()
    val effectiveNavKey = navigationKey ?: isCustomBackActive
    var hasInitializedFocus by rememberSaveable { mutableStateOf(false) }
    var hasMountedThisInstance by remember { mutableStateOf(false) }
    var previousNavKey by remember { mutableStateOf(effectiveNavKey) }
    var previousCategoryKey by remember { mutableStateOf(categoryKey) }
    val isTransitionPending =
        !hasMountedThisInstance ||
            categoryKey != previousCategoryKey ||
            effectiveNavKey != previousNavKey
    val effectiveSuppressAutoSelect = suppressCategoryAutoSelect || isTransitionPending

    CompositionLocalProvider(
        LocalActiveCategoryRequester provides activeCategoryRequester,
        LocalFirstContentRequester provides firstContentRequester,
        LocalTransferFocusToDeck provides transferFocusToDeck,
        LocalDeckBackInterceptor provides { interceptor, active ->
            if (active) {
                if (!backInterceptors.contains(interceptor)) {
                    backInterceptors.add(interceptor)
                }
            } else {
                backInterceptors.remove(interceptor)
            }
        },
        LocalBreadcrumbConsumer provides { newCrumbs -> registeredBreadcrumbs = newCrumbs },
        LocalLastFocusedDeckTracker provides { key, req ->
            savedFocusKeyByDepth[currentDepth] = key
            activeDeckCardRequesters[key] = req
            onRecordFocusedKey?.invoke(currentDepth, key)
        },
        LocalDeckCardRegistry provides { key, req ->
            if (req != null) {
                activeDeckCardRequesters[key] = req
            } else {
                activeDeckCardRequesters.remove(key)
            }
        },
        LocalResetLastFocusedTracker provides {
            savedFocusKeyByDepth.remove(currentDepth)
            onRemoveFocusedKey?.invoke(currentDepth)
        },
        LocalSidebarFocusEnabled provides !isCustomBackActive,
        LocalSuppressCategoryAutoSelect provides effectiveSuppressAutoSelect,
        LocalBringIntoViewSpec provides bringIntoViewSpec,
    ) {
        LaunchedEffect(effectiveNavKey, categoryKey) {
            if (!hasMountedThisInstance) {
                val wasPreviouslyInitialized = hasInitializedFocus
                hasMountedThisInstance = true
                hasInitializedFocus = true
                previousNavKey = effectiveNavKey
                previousCategoryKey = categoryKey

                if (!PrimaryOverlayInputBridge.isGamepadInputActive.value) {
                    AppLog.d(TAG, "GamepadTwoPaneScaffold: skipping initial focus request while touch input active")
                } else {
                    suppressCategoryAutoSelect = true
                    try {
                        delay(GS_INITIAL_FOCUS_DELAY_MS)
                        if (PrimaryOverlayInputBridge.isGamepadInputActive.value) {
                            inputModeManager.requestInputMode(InputMode.Keyboard)
                            if (isCustomBackActive || (wasPreviouslyInitialized && lastActivePaneIsDeck)) {
                                focusDeck(
                                    depth = currentDepth,
                                    restoreSaved = true,
                                    allowCardFallback = true,
                                    allowSidebarFallback = !isCustomBackActive,
                                )
                            } else if (tryRequestFocus(activeCategoryRequester)) {
                                AppLog.d(TAG, "GamepadTwoPaneScaffold: initial focus requested on active category")
                            } else {
                                AppLog.d(TAG, "GamepadTwoPaneScaffold: focus requester unattached on initial focus")
                            }
                        }
                    } finally {
                        suppressCategoryAutoSelect = false
                    }
                }
            } else if (categoryKey != previousCategoryKey) {
                previousCategoryKey = categoryKey
                previousNavKey = effectiveNavKey
                val newDepth = resolveNavigationDepth(effectiveNavKey, isCustomBackActive)
                currentDepth = newDepth

                val depthsToClear = savedFocusKeyByDepth.keys.toList()
                savedFocusKeyByDepth.clear()
                depthsToClear.forEach { depth -> onRemoveFocusedKey?.invoke(depth) }

                if (!PrimaryOverlayInputBridge.isGamepadInputActive.value) {
                    AppLog.d(TAG, "GamepadTwoPaneScaffold: category changed via touch, skipping keyboard focus request")
                } else {
                    suppressCategoryAutoSelect = true
                    try {
                        if (isCustomBackActive || lastActivePaneIsDeck) {
                            if (!focusDeck(
                                    depth = newDepth,
                                    restoreSaved = false,
                                    allowCardFallback = false,
                                    allowSidebarFallback = false,
                                )
                            ) {
                                delay(GS_INITIAL_FOCUS_DELAY_MS)
                                if (PrimaryOverlayInputBridge.isGamepadInputActive.value) {
                                    focusDeck(
                                        depth = newDepth,
                                        restoreSaved = false,
                                        allowCardFallback = true,
                                        allowSidebarFallback = !isCustomBackActive,
                                    )
                                }
                            }
                        } else {
                            inputModeManager.requestInputMode(InputMode.Keyboard)
                            if (!tryRequestFocus(activeCategoryRequester)) {
                                delay(GS_INITIAL_FOCUS_DELAY_MS)
                                if (PrimaryOverlayInputBridge.isGamepadInputActive.value) {
                                    tryRequestFocus(activeCategoryRequester)
                                }
                            }
                            AppLog.d(TAG, "GamepadTwoPaneScaffold: focused active sidebar category on categoryKey change")
                        }
                    } finally {
                        suppressCategoryAutoSelect = false
                    }
                }
            } else if (effectiveNavKey != previousNavKey) {
                previousNavKey = effectiveNavKey
                val newDepth = resolveNavigationDepth(effectiveNavKey, isCustomBackActive)
                val isBackTransition = newDepth < currentDepth
                currentDepth = newDepth
                if (isCustomBackActive) {
                    lastActivePaneIsDeck = true
                }

                // Clean up deeper depth key references when backing out
                savedFocusKeyByDepth.keys.filter { it > newDepth }.forEach { deeperDepth ->
                    savedFocusKeyByDepth.remove(deeperDepth)
                    onRemoveFocusedKey?.invoke(deeperDepth)
                }

                if (!PrimaryOverlayInputBridge.isGamepadInputActive.value) {
                    AppLog.d(
                        TAG,
                        "GamepadTwoPaneScaffold: sub-page transition via touch (depth=$newDepth), skipping keyboard focus",
                    )
                } else {
                    suppressCategoryAutoSelect = true
                    try {
                        if (!focusDeck(
                                depth = newDepth,
                                restoreSaved = isBackTransition,
                                allowCardFallback = false,
                                allowSidebarFallback = false,
                            )
                        ) {
                            delay(GS_INITIAL_FOCUS_DELAY_MS)
                            if (PrimaryOverlayInputBridge.isGamepadInputActive.value) {
                                focusDeck(
                                    depth = newDepth,
                                    restoreSaved = isBackTransition,
                                    allowCardFallback = true,
                                    allowSidebarFallback = isBackTransition,
                                )
                            }
                        }
                    } finally {
                        suppressCategoryAutoSelect = false
                    }
                }
            }
        }

        val currentIsCustomBackActive by rememberUpdatedState(isCustomBackActive)
        val currentTransferFocusToDeck by rememberUpdatedState(transferFocusToDeck)

        LaunchedEffect(Unit) {
            PrimaryOverlayInputBridge.focusRecoveryEvents.collect { keyCode ->
                AppLog.d(
                    TAG,
                    "GamepadTwoPaneScaffold: focusRecoveryEvent keyCode=$keyCode, isDeckFocused=$isDeckFocused, isSidebarFocused=$isSidebarFocused",
                )
                inputModeManager.requestInputMode(InputMode.Keyboard)
                // Only recover focus if focus was completely lost (neither deck nor sidebar is currently focused)
                if (!isDeckFocused && !isSidebarFocused) {
                    suppressCategoryAutoSelect = true
                    try {
                        if (currentIsCustomBackActive || lastActivePaneIsDeck) {
                            currentTransferFocusToDeck()
                            AppLog.d(TAG, "GamepadTwoPaneScaffold: focus recovered to deck")
                        } else {
                            try {
                                activeCategoryRequester.requestFocus()
                                AppLog.d(TAG, "GamepadTwoPaneScaffold: focus recovered to active category")
                            } catch (_: IllegalStateException) {
                                AppLog.d(TAG, "GamepadTwoPaneScaffold: activeCategoryRequester unattached on focus recovery")
                            }
                        }
                    } finally {
                        suppressCategoryAutoSelect = false
                    }
                }
            }
        }

        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(colors.appBackground)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.isBackKeyDown()) {
                            handleBackNavigation()
                        } else {
                            false
                        }
                    },
        ) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                // Left Category Sidebar Rail
                Column(
                    modifier =
                        Modifier
                            .width(GS_SIDEBAR_WIDTH)
                            .fillMaxHeight()
                            .background(colors.surfaceVariant.copy(alpha = GS_SIDEBAR_BG_ALPHA))
                            .padding(GS_SIDEBAR_PADDING)
                            .onFocusChanged { focusState ->
                                isSidebarFocused = focusState.hasFocus
                                if (focusState.hasFocus && !effectiveSuppressAutoSelect) {
                                    lastActivePaneIsDeck = false
                                }
                            }.focusProperties {
                                canFocus = !isCustomBackActive
                                enter = {
                                    if (!isCustomBackActive) {
                                        FocusRequester.Default
                                    } else {
                                        FocusRequester.Cancel
                                    }
                                }
                                exit = { direction ->
                                    if (direction == FocusDirection.Right || direction == FocusDirection.Left) {
                                        FocusRequester.Cancel
                                    } else {
                                        FocusRequester.Default
                                    }
                                }
                            },
                ) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(GS_SIDEBAR_SPACING),
                    ) {
                        sidebarContent()
                    }
                }

                // Right Content Deck
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                ) {
                    if (effectiveBreadcrumbs.isNotEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = GS_DECK_PADDING_H,
                                        vertical = GS_DECK_PADDING_V,
                                    ),
                        ) {
                            GamepadBreadcrumbHeader(
                                breadcrumbs = effectiveBreadcrumbs,
                            )
                        }
                    }

                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (scrollableDeck) GS_DECK_PADDING_H else GS_ZERO_BORDER_WIDTH,
                                    vertical =
                                        if (scrollableDeck) {
                                            if (effectiveBreadcrumbs.isNotEmpty()) GS_ZERO_BORDER_WIDTH else GS_DECK_PADDING_V
                                        } else {
                                            GS_ZERO_BORDER_WIDTH
                                        },
                                ).onFocusChanged { focusState ->
                                    isDeckFocused = focusState.hasFocus
                                    if (focusState.hasFocus) {
                                        lastActivePaneIsDeck = true
                                    }
                                }.focusProperties {
                                    exit = { direction ->
                                        if (direction == FocusDirection.Left) {
                                            if (!isCustomBackActive) {
                                                activeCategoryRequester
                                            } else {
                                                FocusRequester.Cancel
                                            }
                                        } else if (direction == FocusDirection.Right ||
                                            direction == FocusDirection.Up || direction == FocusDirection.Down
                                        ) {
                                            FocusRequester.Cancel
                                        } else {
                                            FocusRequester.Default
                                        }
                                    }
                                }.then(
                                    if (scrollableDeck) {
                                        Modifier.verticalScroll(rememberScrollState())
                                    } else {
                                        Modifier
                                    },
                                ),
                        verticalArrangement = if (scrollableDeck) Arrangement.spacedBy(GS_DECK_SPACING) else Arrangement.Top,
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
