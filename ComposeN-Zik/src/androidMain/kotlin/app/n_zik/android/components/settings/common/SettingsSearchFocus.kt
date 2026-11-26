package app.n_zik.android.components.settings.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.constrainHeight
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.search.SearchHeaderVisibility
import app.n_zik.android.components.search.rememberSearchResultOpacity
import app.n_zik.android.ui.components.themed.HeaderWithIcon

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
    val contentAlpha = rememberSearchResultOpacity(!isSearchFocused || !contentIsBlank)
    return remember(isSearchFocused, contentAlpha) {
        SettingsSearchFocus(!isSearchFocused, contentAlpha)
    }
}

/**
 * Page title of a settings page: fades out and collapses while the search bar
 * is focused, so the bar rises to the title's level (same [HeaderWithIcon]
 * parameters as the plain settings page headers).
 */
@Composable
fun ColumnScope.settingsSearchTitle(
    focus: SettingsSearchFocus,
    titleRes: Int,
    iconRes: Int,
) {
    SearchHeaderVisibility(focus.titleVisible) {
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

/** Keeps state composed; song lists can retain their slots while hidden settings release space. */
internal fun Modifier.settingsSearchContentVisibility(
    alpha: Float,
    retainSpace: Boolean = false,
): Modifier =
    graphicsLayer { this.alpha = alpha }
        .then(if (alpha <= 0f) Modifier.clearAndSetSemantics { } else Modifier)
        .then(if (retainSpace) Modifier else Modifier.clipToBounds())
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = if (retainSpace) placeable.height else (placeable.height * alpha).roundToInt()
            layout(placeable.width, constraints.constrainHeight(height)) {
                if (alpha > 0f) placeable.placeRelative(0, 0)
            }
        }

/**
 * Fades and collapses the content while the search field is focused.
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
@Composable
fun settingsSearchDescription(
    focus: SettingsSearchFocus,
    content: @Composable () -> Unit,
) {
    SearchHeaderVisibility(focus.titleVisible) {
        Column(modifier = Modifier.graphicsLayer { alpha = focus.contentAlpha }) {
            content()
        }
    }
}
