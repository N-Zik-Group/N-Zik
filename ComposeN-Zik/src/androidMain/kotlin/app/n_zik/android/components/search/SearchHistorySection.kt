package app.n_zik.android.components.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.themed.TitleMiniSection
import app.it.fast4x.rimusic.utils.secondary
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape

/** Filtered saved searches with animated entry and exit, query recall, and deletion. */
@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchHistorySection(
    visible: Boolean,
    queries: List<String>,
    onQueryClick: (String) -> Unit,
    onQueryDelete: (String) -> Unit,
    onQueryDeleteAll: () -> Unit,
    onQueryEdit: ((String) -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchHistoryIconPainter = painterResource(R.drawable.search_circle)
    val clearHistoryIconPainter = painterResource(R.drawable.trash)
    val displayedQueries = rememberSearchHistoryRows(queries)
    AnimatedVisibility(
        visibleState = rememberSearchHistoryVisibility(visible),
        enter = slideInVertically(tween(200), initialOffsetY = { -it / 4 }) +
                expandVertically(tween(200), expandFrom = Alignment.Top) +
                fadeIn(tween(200)),
        exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
    ) {
        Column {
            // Inset one step inside the search bar's margins (24dp)
            TitleMiniSection(
                title = stringResource(R.string.searches_saved_searches),
                modifier = Modifier.padding(start = 24.dp)
            )

            displayedQueries.forEach { query ->
                // Row block inset inside the search bar's width (24dp margins)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(uiRoundnessShape()).clickable(onClick = {
                            onQueryClick(query)
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                        })
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(all = 16.dp)
                ) {
                    Spacer(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(20.dp)
                            .paint(
                                painter = searchHistoryIconPainter,
                                colorFilter = ColorFilter.tint(colorPalette().textDisabled)
                            )
                    )

                    BasicText(
                        text = query,
                        style = typography().s.secondary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .weight(1f)
                    )

                    Image(
                        painter = clearHistoryIconPainter,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().textDisabled),
                        modifier = Modifier
                            .clip(uiRoundnessShape()).combinedClickable(
                                onClick = { onQueryDelete(query) },
                                onLongClick = onQueryDeleteAll
                            )
                            .padding(horizontal = 8.dp)
                            .size(20.dp)
                    )
                    if (onQueryEdit != null) {
                        Image(
                            painter = painterResource(R.drawable.pencil),
                            contentDescription = stringResource(R.string.search),
                            colorFilter = ColorFilter.tint(colorPalette().textDisabled),
                            modifier = Modifier.clip(uiRoundnessShape())
                                .clickable { onQueryEdit(query) }
                                .padding(horizontal = 8.dp).size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
