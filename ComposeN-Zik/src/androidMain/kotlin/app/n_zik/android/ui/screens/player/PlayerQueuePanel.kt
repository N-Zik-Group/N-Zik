package app.n_zik.android.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.FullGlassOpticsProgress
import app.n_zik.android.components.glass.GlassQueuePanel
import app.n_zik.android.components.glass.LocalGlassOpticsEnabled
import app.n_zik.android.components.glass.LocalGlassOpticsTransitionProgress
import app.n_zik.android.components.glass.LocalGlassUnderlayCovered
import app.n_zik.android.components.glass.LocalQueuePlayerBackdrop
import app.n_zik.android.components.glass.NativeQueueRows
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.glassQueuePanelBackground
import app.n_zik.android.components.glass.usesQueueToolbarGlass
import app.n_zik.android.components.menu.MenuDragHandleBar
import app.n_zik.android.components.player.queuePanelAtExpandedEndpoint
import app.n_zik.android.components.player.queuePanelDragHandle
import app.n_zik.android.components.player.rememberPlayerQueueBackdrop
import app.n_zik.android.enums.QueueLoopType
import app.n_zik.android.enums.QueueType
import app.n_zik.android.topUiRoundnessShape
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTextApi::class, ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
internal fun BoxScope.PlayerQueuePanel(
    navController: NavController,
    showQueueState: MutableState<Boolean>,
    visibleState: MutableState<Boolean>,
    fullyCoveringState: MutableState<Boolean>,
    queueLoopState: MutableState<QueueLoopType>,
    queueType: QueueType,
    backdrop: Backdrop,
    inheritedOpticsEnabled: Boolean,
    inheritedUnderlayCovered: Boolean,
    queueNavigationSurface: Color?,
    preview: Boolean,
    onDiscoverClick: () -> Unit,
    onClosePlayer: () -> Unit,
) {
    var showQueue by showQueueState
    var isQueuePanelVisible by visibleState
    var isQueueFullyCovering by fullyCoveringState
    // Inline resizable queue panel
    val queuePanelHeightFraction = remember { Animatable(0f) }
    val queuePanelCoroutineScope = rememberCoroutineScope()

    LaunchedEffect(showQueue) {
        if (showQueue) {
            isQueuePanelVisible = true
            queuePanelHeightFraction.snapTo(0f)
            queuePanelHeightFraction.animateTo(
                targetValue = if (preview) 0.91f else 0.65f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
            )
        } else if (isQueuePanelVisible) {
            queuePanelHeightFraction.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
            )
            isQueuePanelVisible = false
        }
    }

    if (isQueuePanelVisible) {
        val queueUnderlay = rememberPlayerQueueBackdrop(backdrop) {
            queuePanelHeightFraction.value
        }
        // The queue is the foreground surface; a global modal still covers it.
        CompositionLocalProvider(
            LocalGlassOpticsEnabled provides inheritedOpticsEnabled,
            LocalGlassUnderlayCovered provides inheritedUnderlayCovered,
            LocalGlassOpticsTransitionProgress provides FullGlassOpticsProgress,
            LocalQueuePlayerBackdrop provides queueUnderlay,
        ) {
            BackHandler { showQueue = false }

            val density = LocalDensity.current
            val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() }
            val statusBarTopPx = with(density) {
                WindowInsets.systemBars
                    .only(WindowInsetsSides.Top)
                    .asPaddingValues()
                    .calculateTopPadding()
                    .roundToPx()
            }
            val maxFraction = ((screenHeightPx - statusBarTopPx).toFloat() / screenHeightPx).coerceAtMost(1f)

            // Scrim — read fraction inside graphicsLayer to avoid recomposition
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (queuePanelHeightFraction.value / 0.65f).coerceIn(0f, 1f) * 0.5f
                    }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showQueue = false }
            )

            // Queue panel — height controlled via Modifier.layout to avoid recomposition
            val queuePanelBackground = if (queueType == QueueType.Modern) Color.Transparent else colorPalette().background2
            GlassQueuePanel(
                panelBackgroundColor = queuePanelBackground,
                panelShape = topUiRoundnessShape(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .layout { measurable, constraints ->
                        // Read fraction in LAYOUT phase (not composition) → no recomposition
                        val f = queuePanelHeightFraction.value.coerceIn(0.01f, 1f)
                        val panelHeight = (constraints.maxHeight * f).toInt().coerceAtLeast(1)
                        isQueueFullyCovering = queuePanelAtExpandedEndpoint(panelHeight, constraints.maxHeight, maxFraction, queuePanelBackground.alpha)
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = panelHeight, maxHeight = panelHeight)
                        )
                        layout(placeable.width, panelHeight) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .clip(topUiRoundnessShape())
                    .glassQueuePanelBackground(queuePanelBackground, topUiRoundnessShape())
                    .windowInsetsPadding((if (usesQueueToolbarGlass()) WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal) else WindowInsets.navigationBars).union(
                            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                        ))
            ) {
                // Queue content - padding top for drag handle
                Box(
                    modifier = Modifier
                        .padding(top = 48.dp)
                ) {
                    NativeQueueRows {
                        val dismissQueue: (QueueLoopType) -> Unit = {
                            queueLoopState.value = it
                            showQueue = false
                        }
                        Queue(
                            navController = navController,
                            onSearchOpened = {
                                queuePanelCoroutineScope.launch {
                                    queuePanelHeightFraction.animateTo(
                                        maxFraction,
                                        spring(dampingRatio = 0.8f, stiffness = 300f),
                                    )
                                }
                            },
                            onDismiss = dismissQueue,
                            onDiscoverClick = onDiscoverClick,
                            onDestinationOpened = {
                                dismissQueue(it)
                                onClosePlayer()
                            },
                        )
                    }
                }

                // Drag handle - overlays at top
                val handleAlpha = if (queueType == QueueType.Modern) 0.5f else 1f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.TopCenter)
                        .clip(topUiRoundnessShape())
                        .background(colorPalette().background0.copy(alpha = handleAlpha))
                        .queuePanelDragHandle(
                            queuePanelHeightFraction, maxFraction, screenHeightPx, queuePanelCoroutineScope,
                        ) {
                            showQueue = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MenuDragHandleBar(color = colorPalette().textSecondary)
                }

                // QueueToolBar at bottom of panel — slides down and fades out when closing
                QueueToolBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            val toolbarProgress = ((queuePanelHeightFraction.value - 0.55f) / 0.1f).coerceIn(0f, 1f)
                            translationY = with(density) { (1f - toolbarProgress) * 100.dp.toPx() }
                            alpha = toolbarProgress
                        }
                )
            }
        }
    }

    // Nav bar background for queue
    if (queueNavigationSurface != null) {
        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(navBarHeight)
                .align(Alignment.BottomCenter)
                .background(queueNavigationSurface)
        )
    }
}
