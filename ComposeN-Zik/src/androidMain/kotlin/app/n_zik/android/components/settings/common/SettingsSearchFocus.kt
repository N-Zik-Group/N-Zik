package app.n_zik.android.components.settings.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon

// Online Search uses 16dp around the bar before its full-width result list.
internal val SettingsSearchBarPadding = PaddingValues(16.dp)

/**
 * Search focus behavior, shared by the settings pages (global page and every
 * per-tab page), the local search screen and the history screen: focusing
 * the search bar collapses the page title (the bar rises to the title's
 * level), fades the content out in place, then releases its measured space,
 * and keeps the same 16dp search bar margins as Online Search.
 * The content only fades while the query is blank - filtered cards act as
 * search results and stay fully visible.
 */
@Stable
class SettingsSearchFocus(
    val titleVisible: Boolean,
    private val contentAlphaState: State<Float>,
) {
    val contentAlpha: Float get() = contentAlphaState.value

    val barPadding: PaddingValues get() = SettingsSearchBarPadding
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
    return remember(isSearchFocused, contentAlpha) {
        SettingsSearchFocus(!isSearchFocused, contentAlpha)
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

/** Keeps state composed, but fully faded children occupy no scroll range or input space. */
internal fun Modifier.settingsSearchContentVisibility(alpha: Float): Modifier =
    graphicsLayer { this.alpha = alpha }
        .then(if (alpha <= 0f) Modifier.clearAndSetSemantics { } else Modifier)
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, if (alpha > 0f) placeable.height else 0) {
                if (alpha > 0f) placeable.placeRelative(0, 0)
            }
        }

/**
 * Content wrapper of a settings page: a plain alpha fade while the search bar
 * is focused, releasing its height after the fade finishes.
 */
@Composable
fun settingsSearchContent(
    focus: SettingsSearchFocus,
    hasResults: Boolean = true,
    query: String = "",
    content: @Composable () -> Unit,
) {
    SettingsSearchEmptyFeedback(query, !hasResults)
    Column(modifier = Modifier.settingsSearchContentVisibility(focus.contentAlpha)) {
        if (hasResults) content()
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
