package app.n_zik.android.components.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.secondary
import app.n_zik.android.R
import app.n_zik.android.typography

private val LocalSearchViewport = staticCompositionLocalOf<State<Rect>?> { null }

/** Supplies finite screen bounds to empty results placed inside scrolling content. */
@Composable
fun SearchResultsViewport(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val bounds = remember { mutableStateOf(Rect.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { bounds.value = it.boundsInWindow() }) {
        CompositionLocalProvider(LocalSearchViewport provides bounds) { content() }
    }
}

/** Centers the empty state below the search controls, including in a vertical scroller. */
@Composable
fun SearchNoResults(
    modifier: Modifier = Modifier,
    fillRemainingViewport: Boolean = false,
    text: String = stringResource(R.string.no_results_found),
) {
    SearchEmptySpace(modifier, fillRemainingViewport) {
        BasicText(
            text = text,
            style = typography().xs.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, textAlign = TextAlign.Center),
            modifier = Modifier.padding(horizontal = 24.dp).testTag("search_no_results_text"),
        )
    }
}

/** Keeps the empty viewport stable while search feedback is announced by a toast. */
@Composable
fun SearchEmptySpace(
    modifier: Modifier = Modifier,
    fillRemainingViewport: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val viewport = LocalSearchViewport.current
    val density = LocalDensity.current
    var top by remember { mutableFloatStateOf(Float.NaN) }
    val sizing = if (fillRemainingViewport && viewport != null) {
        val bounds = viewport.value
        val available = if (top.isNaN()) 0f else
            (bounds.bottom - top.coerceAtLeast(bounds.top)).coerceIn(0f, bounds.height)
        Modifier.fillMaxWidth().height(with(density) { available.toDp() })
    } else Modifier.fillMaxSize()
    Box(
        modifier.then(sizing)
            .onGloballyPositioned { top = it.positionInWindow().y }
            .testTag("search_no_results_area"),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
