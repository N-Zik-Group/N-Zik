package app.n_zik.android.components.player

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.QueueLoopType
import app.it.fast4x.rimusic.enums.QueueType
import app.it.fast4x.rimusic.ui.screens.player.Queue
import app.it.fast4x.rimusic.ui.screens.player.QueueToolBar
import app.it.fast4x.rimusic.utils.queueLoopTypeKey
import app.it.fast4x.rimusic.utils.queueTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.topUiRoundnessShape
import kotlinx.coroutines.launch

/**
 * Exact copy of the player's inline queue panel (Player.kt:2315-2482).
 *
 * Shows [Queue] as a resizable panel on top of the current page,
 * with drag-to-resize, scrim, toolbar animation, and nav bar strip.
 */
@SuppressLint("SuspiciousIndentation")
@OptIn(ExperimentalTextApi::class, ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@UnstableApi
@Composable
fun MiniPlayerQueueOverlay(
    showSheet: Boolean,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val binder = LocalPlayerServiceBinder.current
    val queueType by rememberPreference(queueTypeKey, QueueType.Modern)
    val queueLoopState = rememberPreference(queueLoopTypeKey, QueueLoopType.Default)

    // Inline resizable queue panel — exact copy of Player.kt:2315-2482
    val queuePanelHeightFraction = remember { Animatable(0f) }
    var isQueuePanelVisible by remember { mutableStateOf(false) }
    val queuePanelCoroutineScope = rememberCoroutineScope()

    LaunchedEffect(showSheet) {
        if (showSheet) {
            isQueuePanelVisible = true
            queuePanelHeightFraction.snapTo(0f)
            queuePanelHeightFraction.animateTo(
                targetValue = 0.65f,
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
        BackHandler { onDismiss() }

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

        Box(modifier = Modifier.fillMaxSize()) {

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
                ) { onDismiss() }
        )

        // Queue panel — height controlled via Modifier.layout to avoid recomposition
        val queuePanelBackground = if (queueType == QueueType.Modern) Color.Transparent else colorPalette().background2
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .layout { measurable, constraints ->
                    // Read fraction in LAYOUT phase (not composition) → no recomposition
                    val f = queuePanelHeightFraction.value.coerceIn(0.01f, 1f)
                    val panelHeight = (constraints.maxHeight * f).toInt().coerceAtLeast(1)
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = panelHeight, maxHeight = panelHeight)
                    )
                    layout(placeable.width, panelHeight) {
                        placeable.placeRelative(0, 0)
                    }
                }
                .clip(topUiRoundnessShape())
                .background(queuePanelBackground)
                .padding(
                    WindowInsets.navigationBars.union(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                    ).asPaddingValues()
                )
        ) {
            // Queue content - padding top for drag handle
            Box(
                modifier = Modifier
                    .padding(top = 48.dp)
            ) {
                Queue(
                    navController = navController,
                    onDismiss = {
                        queueLoopState.value = it
                        onDismiss()
                    },
                    onDiscoverClick = {
                        binder?.service?.nzikRadio?.toggleDiscover()
                    }
                )
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
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                queuePanelCoroutineScope.launch {
                                    val currentFraction = queuePanelHeightFraction.value
                                    when {
                                        currentFraction > 0.85f -> queuePanelHeightFraction.animateTo(
                                            maxFraction,
                                            spring(dampingRatio = 0.8f, stiffness = 300f)
                                        )
                                        currentFraction < 0.4f -> onDismiss()
                                        else -> queuePanelHeightFraction.animateTo(
                                            0.65f,
                                            spring(dampingRatio = 0.8f, stiffness = 300f)
                                        )
                                    }
                                }
                            },
                            onVerticalDrag = { _, dragAmount ->
                                queuePanelCoroutineScope.launch {
                                    val newFraction = (queuePanelHeightFraction.value - dragAmount / screenHeightPx)
                                        .coerceIn(0.1f, maxFraction)
                                    queuePanelHeightFraction.snapTo(newFraction)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )
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

        // Nav bar background for queue
        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(navBarHeight)
                .align(Alignment.BottomCenter)
                .background(colorPalette().background1)
        )
        } // Box(fillMaxSize)
    }
}
