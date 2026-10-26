package app.n_zik.android.components.settings.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.n_zik.android.components.search.SearchNoResults

/**
 * Search focus behavior, shared by the settings pages (global page and every
 * per-tab page), the local search screen and the history screen: focusing
 * the search bar collapses the page title (the bar rises to the title's
 * level), fades the content out in place (plain alpha, no layout movement),
 * and lifts the shared 16dp bar top padding to 0 so the bar reaches the top.
 * The content only fades while the query is blank - filtered cards act as
 * search results and stay fully visible.
 */
@Stable
class SettingsSearchFocus(
    val titleVisible: Boolean,
    private val contentAlphaState: State<Float>,
    private val barTopPaddingState: State<Dp>,
) {
    val contentAlpha: Float get() = contentAlphaState.value

    // PaddingValues is read during measurement, avoiding page-wide recomposition per frame.
    val barPadding: PaddingValues = object : PaddingValues {
        override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 16.dp
        override fun calculateTopPadding(): Dp = barTopPaddingState.value
        override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 16.dp
        override fun calculateBottomPadding(): Dp = 16.dp
    }
}

@Composable
fun rememberSettingsSearchFocus(
    isSearchFocused: Boolean,
    contentIsBlank: Boolean = false,
): SettingsSearchFocus {
    val contentAlpha = animateFloatAsState(
        targetValue = if (isSearchFocused && contentIsBlank) 0f else 1f,
        animationSpec = tween(250),
        label = "settingsSearchFocusContentAlpha"
    )
    val barTopPadding = animateDpAsState(
        targetValue = if (isSearchFocused) 0.dp else 16.dp,
        animationSpec = tween(250),
        label = "settingsSearchFocusBarTopPadding"
    )
    return remember(isSearchFocused, contentAlpha, barTopPadding) {
        SettingsSearchFocus(!isSearchFocused, contentAlpha, barTopPadding)
    }
}

/**
 * Page title of a settings page: fades out and collapses while the search bar
 * is focused, so the bar rises to the title's level (same [HeaderWithIcon]
 * parameters as the plain settings page headers).
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ColumnScope.settingsSearchTitle(
    focus: SettingsSearchFocus,
    titleRes: Int,
    iconRes: Int,
) {
    AnimatedVisibility(
        visible = focus.titleVisible,
        enter = expandVertically(tween(250)) + fadeIn(tween(250)),
        exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
    ) {
        HeaderWithIcon(
            title = stringResource(titleRes),
            iconId = iconRes,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {}
        )
    }
}

/**
 * Content wrapper of a settings page: a plain alpha fade while the search bar
 * is focused (no layout removal, so nothing moves or collapses).
 */
@Composable
fun settingsSearchContent(
    focus: SettingsSearchFocus,
    hasResults: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.graphicsLayer { alpha = focus.contentAlpha }) {
        if (hasResults) content() else SearchNoResults(fillRemainingViewport = true)
    }
}

/**
 * Header slot of a settings page (the description above the search bar): fades
 * out AND collapses while the search bar is focused, freeing its space so the
 * bar can rise to the very top. The cards below keep [settingsSearchContent]
 * (plain fade, no layout movement).
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun settingsSearchDescription(
    focus: SettingsSearchFocus,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = focus.titleVisible,
        enter = expandVertically(tween(250)) + fadeIn(tween(250)),
        exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
    ) {
        Column(modifier = Modifier.graphicsLayer { alpha = focus.contentAlpha }) {
            content()
        }
    }
}
