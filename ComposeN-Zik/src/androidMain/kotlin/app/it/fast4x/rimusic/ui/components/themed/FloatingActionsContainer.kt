package app.it.fast4x.rimusic.ui.components.themed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch

import app.it.fast4x.rimusic.ui.components.FabItem
import app.it.fast4x.rimusic.ui.components.MultiFloatingActionsButton
import app.it.fast4x.rimusic.utils.ScrollingInfo
import app.it.fast4x.rimusic.utils.floatActionIconOffsetXkey
import app.it.fast4x.rimusic.utils.floatActionIconOffsetYkey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.scrollingInfo
import app.it.fast4x.rimusic.utils.smoothScrollToTop
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassFloatingActionsBottomPadding
import app.n_zik.android.components.navigation.FloatingActionGap
import app.n_zik.android.components.navigation.floatingActionsHorizontalPadding
import app.n_zik.android.components.navigation.movableFloatingAction

/** Clearance follows the destination's actual mini-player and navigation reserves. */
@Composable
fun floatingActionsBottomPadding(windowInsets: WindowInsets = LocalPlayerAwareWindowInsets.current): Dp =
    glassFloatingActionsBottomPadding(0.dp, windowInsets)

@ExperimentalMaterial3Api
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun BoxScope.MultiFloatingActionsContainer(
    modifier: Modifier = Modifier,
    useAsActionsMenu: Boolean = false,
    iconId: Int,
    onClick: () -> Unit,
    onClickSettings: (() -> Unit)? = null,
    onClickSearch: (() -> Unit)? = null
) {
    val bottomPadding = floatingActionsBottomPadding()

    Row(
        horizontalArrangement = Arrangement.spacedBy(FloatingActionGap),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(floatingActionsHorizontalPadding())
            .padding(bottom = bottomPadding + FloatingActionGap)
    ) {

        MultiFloatingActionsButton(
            useAsActionsMenu = useAsActionsMenu,
            fabIcon = painterResource(iconId),
            items =
                arrayListOf(
                    FabItem(
                        icon = painterResource(R.drawable.settings),
                        label = stringResource(R.string.txt_settings),
                        onFabItemClicked = {
                            if (onClickSettings != null) {
                                onClickSettings()
                            }
                        }
                    ),
                    FabItem(
                        icon = painterResource(R.drawable.search),
                        label = stringResource(R.string.search),
                        onFabItemClicked = {
                            if (onClickSearch != null) {
                                onClickSearch()
                            }
                        }
                    )
                )
            ,
            onClick = { onClick() }
        )
    }

}

@ExperimentalAnimationApi
@Composable
fun BoxScope.FloatingActionsContainerWithScrollToTop(
    lazyGridState: LazyGridState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    iconId: Int? = null,
    onClick: (() -> Unit)? = null,
    windowInsets: WindowInsets = LocalPlayerAwareWindowInsets.current,
    reservedEndSpace: Dp = 0.dp,
) {
    val transitionState = remember {
        MutableTransitionState<ScrollingInfo?>(ScrollingInfo())
    }.apply { targetState = if (visible) lazyGridState.scrollingInfo() else null }

    FloatingActions(
        transitionState = transitionState,
        onScrollToTop = lazyGridState::smoothScrollToTop,
        iconId = iconId,
        onClick = onClick,
        windowInsets = windowInsets,
        reservedEndSpace = reservedEndSpace,
        modifier = modifier
    )
}

@ExperimentalAnimationApi
@Composable
fun BoxScope.FloatingActionsContainerWithScrollToTop(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    iconId: Int? = null,
    onClick: (() -> Unit)? = null,
    windowInsets: WindowInsets = LocalPlayerAwareWindowInsets.current,
    reservedEndSpace: Dp = 0.dp,
) {
    val transitionState = remember {
        MutableTransitionState<ScrollingInfo?>(ScrollingInfo())
    }.apply { targetState = if (visible) lazyListState.scrollingInfo() else null }

    FloatingActions(
        transitionState = transitionState,
        onScrollToTop = lazyListState::smoothScrollToTop,
        iconId = iconId,
        onClick = onClick,
        windowInsets = windowInsets,
        reservedEndSpace = reservedEndSpace,
        modifier = modifier
    )
}

@ExperimentalAnimationApi
@Composable
fun BoxScope.FloatingActionsContainerWithScrollToTop(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    iconId: Int? = null,
    onClick: (() -> Unit)? = null,
    windowInsets: WindowInsets = LocalPlayerAwareWindowInsets.current,
    reservedEndSpace: Dp = 0.dp,
) {
    val transitionState = remember {
        MutableTransitionState<ScrollingInfo?>(ScrollingInfo())
    }.apply { targetState = if (visible) scrollState.scrollingInfo() else null }


    FloatingActions(
        transitionState = transitionState,
        iconId = iconId,
        onClick = onClick,
        windowInsets = windowInsets,
        reservedEndSpace = reservedEndSpace,
        modifier = modifier
    )
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@ExperimentalAnimationApi
@Composable
fun BoxScope.FloatingActions(
    transitionState: MutableTransitionState<ScrollingInfo?>,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
    onScrollToTop: (suspend () -> Unit)? = null,
    iconId: Int? = null,
    onClick: (() -> Unit)? = null,
    reservedEndSpace: Dp = 0.dp,
) {
    val transition = rememberTransition(transitionState, "")
    val bottomPadding = floatingActionsBottomPadding(windowInsets)

    val offsetX = rememberPreference(floatActionIconOffsetXkey, 0F)
    val offsetY = rememberPreference(floatActionIconOffsetYkey, 0F)

    Row(
        horizontalArrangement = Arrangement.spacedBy(FloatingActionGap),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(floatingActionsHorizontalPadding(windowInsets))
            .padding(end = reservedEndSpace, bottom = bottomPadding + FloatingActionGap)
    ) {
        onScrollToTop?.let {
            transition.AnimatedVisibility(
                visible = { it?.isScrollingDown == false && it.isFar },
                enter = slideInVertically(tween(500, if (iconId == null) 0 else 100)) { it },
                exit = slideOutVertically(tween(500, 0)) { it },
            ) {
                val coroutineScope = rememberCoroutineScope()
                PrimaryButton(
                    iconId = R.drawable.chevron_up,
                    onClick = {
                        coroutineScope.launch {
                            onScrollToTop()
                        }
                    },
                    enabled = transition.targetState?.isScrollingDown == false && transition.targetState?.isFar == true,
                    modifier = Modifier.movableFloatingAction(offsetX, offsetY)
                )
            }
        }

        iconId?.let {
            onClick?.let {
                transition.AnimatedVisibility(
                    visible = { true },
                    enter = slideInVertically(tween(500, 0)) { it },
                    exit = slideOutVertically(tween(500, 100)) { it },
                ) {
                    PrimaryButton(
                        iconId = iconId,
                        onClick = onClick,
                        enabled = true,
                        modifier = Modifier.movableFloatingAction(offsetX, offsetY)
                    )
                }
            }
        }
    }
}
