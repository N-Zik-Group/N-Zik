package app.n_zik.android.components.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.n_zik.android.ui.components.themed.TitleMiniSection
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.utils.secondary
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassControlOverflowColumn
import app.n_zik.android.components.glass.GlassSectionAction
import app.n_zik.android.components.glass.GlassSectionActions
import app.n_zik.android.components.glass.GlassTitleActionRow
import app.n_zik.android.components.glass.NativeSectionIconAction
import app.n_zik.android.components.glass.PageButton
import app.n_zik.android.components.navigation.header.HeaderActionCanvasSize
import app.n_zik.android.components.glass.glassOverflowFade
import app.n_zik.android.components.glass.glassNativeActionContour
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.typography

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
    shimmer: Boolean = false,
    allQueries: List<String> = queries,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val queryAlignment = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
    val searchHistoryIconPainter = painterResource(R.drawable.search_circle)
    val displayedQueries = rememberSearchHistoryRows(queries)
    val canInteract = visible && !shimmer
    val rowCandidates = if (queries.isEmpty()) displayedQueries else allQueries
    val glassSettings = rememberGlassSettings()
    val overflow = glassSettings.usesGlassMaterial(GlassArea.ActionButtons)
    GlassControlOverflowColumn {
        AnimatedVisibility(
            visibleState = rememberSearchHistoryVisibility(visible),
            enter = slideInVertically(tween(200), initialOffsetY = { -it / 4 }) +
                    expandVertically(tween(200), expandFrom = Alignment.Top,
                        clip = !overflow) +
                    (if (overflow) EnterTransition.None else fadeIn(tween(200))),
            exit = shrinkVertically(tween(150), clip = !overflow) +
                (if (overflow) ExitTransition.None else fadeOut(tween(150))),
        ) {
            GlassControlOverflowColumn(if (overflow) glassOverflowFade(200, 150) else Modifier) {
                // Inset one step inside the search bar's margins (24dp)
                TitleMiniSection(
                    title = stringResource(R.string.search_saved_history_title),
                    modifier = Modifier.padding(start = 24.dp)
                )

                rowCandidates.forEach { query ->
                    val matches = query in displayedQueries
                    val rowEnabled = canInteract && query in queries
                    key(query) {
                        SearchHeaderVisibility(matches) {
                            // Row block inset inside the search bar's width (24dp margins)
                            GlassTitleActionRow(
                                modifier = Modifier.fillMaxWidth(),
                                verticalPadding = 16.dp,
                                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 16.dp),
                                hasTrailingActions = true,
                                onClick = if (!rowEnabled) null else ({
                                    onQueryClick(query)
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                }),
                            ) {
                                if (glassSettings.usesGlassMaterial(GlassArea.ActionButtons)) {
                                    GlassSectionActions(listOf(GlassSectionAction(
                                        icon = R.drawable.search_circle,
                                        contentDescription = query.takeIf { rowEnabled },
                                        enabled = rowEnabled,
                                        onClick = {
                                            onQueryClick(query)
                                            keyboardController?.hide()
                                            focusManager.clearFocus(force = true)
                                        },
                                    )), settings = glassSettings)
                                } else Spacer(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .size(20.dp)
                                        .glassNativeActionContour(glassSettings)
                                        .paint(
                                            painter = searchHistoryIconPainter,
                                            colorFilter = ColorFilter.tint(colorPalette().textDisabled)
                                        )
                                )

                                val queryStyle = typography().s.secondary.copy(textAlign = queryAlignment)
                                val queryModifier = Modifier.padding(horizontal = 8.dp).weight(1f)
                                if (shimmer) MeasuredMetadataPlaceholder(query, queryStyle, queryModifier)
                                else BasicText(query, style = queryStyle, modifier = queryModifier)

                                if (glassSettings.usesGlassMaterial(GlassArea.ActionButtons)) {
                                    GlassSectionActions(
                                        actions = buildList {
                                            add(GlassSectionAction(
                                                icon = R.drawable.trash,
                                                contentDescription = stringResource(R.string.common_action_delete),
                                                onLongClick = onQueryDeleteAll,
                                                enabled = rowEnabled,
                                                onClick = { onQueryDelete(query) },
                                            ))
                                            if (onQueryEdit != null) {
                                                add(GlassSectionAction(
                                                    icon = R.drawable.pencil,
                                                    contentDescription = stringResource(R.string.search_title),
                                                    enabled = rowEnabled,
                                                    onClick = { onQueryEdit(query) },
                                                ))
                                            }
                                        },
                                        settings = glassSettings,
                                    )
                                } else {
                                    NativeSectionIconAction(
                                        icon = R.drawable.trash,
                                        contentDescription = stringResource(R.string.common_action_delete),
                                        canvasSize = HeaderActionCanvasSize,
                                        color = colorPalette().textDisabled,
                                        settings = glassSettings,
                                        onClick = { onQueryDelete(query) },
                                        onLongClick = onQueryDeleteAll,
                                        enabled = rowEnabled,
                                    )
                                    if (onQueryEdit != null) {
                                        NativeSectionIconAction(
                                            icon = R.drawable.pencil,
                                            contentDescription = stringResource(R.string.search_title),
                                            canvasSize = HeaderActionCanvasSize,
                                            color = colorPalette().textDisabled,
                                            settings = glassSettings,
                                            enabled = rowEnabled,
                                            onClick = { onQueryEdit(query) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (displayedQueries.isNotEmpty()) {
                    val canClear = visible && queries.isNotEmpty() && !shimmer
                    val palette = colorPalette()
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                        PageButton(
                            onClick = { if (canClear) onQueryDeleteAll() },
                            enabled = canClear,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                            settings = glassSettings,
                            modifier = Modifier.testTag("search.history.clear.all"),
                        ) {
                            Text(
                                stringResource(R.string.search_history_clear_all_button),
                                style = typography().s.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
