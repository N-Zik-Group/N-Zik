package app.n_zik.android.components.settings.ai

import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlinx.coroutines.flow.first
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.settings.QuickPicksContentSettingsDialog
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.settings.RewindSettingsCard
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsGroupSpacer
import app.n_zik.android.components.settings.common.rememberSettingsSearchFocus
import app.n_zik.android.components.settings.common.settingsSearchContent
import app.n_zik.android.components.settings.common.settingsSearchDescription
import app.n_zik.android.components.settings.common.settingsSearchTitle
import app.n_zik.android.components.settings.common.visibleCardTitleRes
import app.n_zik.android.components.tab.Search

/** Coordinates AI cards, history and existing deep links. */
@UnstableApi
@Composable
fun AIRecommendationSettings(
    navController: NavController,
    ytLoggedIn: Boolean = false,
    focus: String = ""
) {
    val context = LocalContext.current
    val search = Search(initialVisible = true)
    val searchFocus = rememberSettingsSearchFocus(search.isFocused, search.inputValue.isBlank())

    val isHistoryPaused by rememberPreference(pauseSearchHistoryKey, false)
    val history = rememberSearchHistory(SearchHistoryStore.PAGE_SETTINGS_AI, isHistoryPaused)
    val historyMatches = filterSearchHistory(history.value, search.inputValue)
    val historyVisible = search.isFocused && historyMatches.isNotEmpty()

    fun recordSearchQuery(query: String = search.inputValue) {
        history.value = SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_SETTINGS_AI, query)
    }


    val scrollState = rememberScrollState()
    var containerTopY by remember { mutableFloatStateOf(-1f) }
    var rewindCardTopY by remember { mutableFloatStateOf(-1f) }
    var statisticsCardTopY by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(focus) {
        if (focus != "rewind" && focus != "statistics") return@LaunchedEffect


        val focusedRewind = focus == "rewind"
        val offset = snapshotFlow {
            val cardTopY = if (focusedRewind) rewindCardTopY else statisticsCardTopY
            if (cardTopY < 0f || containerTopY < 0f) -1f
            else cardTopY - containerTopY + scrollState.value
        }.first { it >= 0f }
        scrollState.animateScrollTo(offset.roundToInt())
    }

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .onGloballyPositioned { containerTopY = it.positionInRoot().y }
    ) {

        QuickPicksContentSettingsDialog.Render()

        settingsSearchTitle(searchFocus, if (!ytLoggedIn) R.string.ai_recommendations else R.string.home, if (!ytLoggedIn) R.drawable.sparkles else R.drawable.ytmusic)

        settingsSearchDescription(searchFocus) {
            SettingsDescription(
                text = stringResource(R.string.quick_picks_description),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        search.SearchBar(this, barPadding = searchFocus.barPadding, onSearch = { recordSearchQuery() })

        SearchHistorySection(
            visible = historyVisible,
            queries = historyMatches,
            onQueryClick = { search.input = TextFieldValue(it); recordSearchQuery(it); search.isFocused = false },
            onQueryDelete = {
                history.value = SearchHistoryStore.delete(context.preferences, SearchHistoryStore.PAGE_SETTINGS_AI, it)
            },
            onQueryDeleteAll = {
                history.value = SearchHistoryStore.deleteAll(context.preferences, SearchHistoryStore.PAGE_SETTINGS_AI)
            }
        )

        val hasSearchResults = search.inputValue.isBlank() || visibleCardTitleRes(context, 3, search.inputValue).isNotEmpty()
        LaunchedEffect(hasSearchResults) {
            if (!hasSearchResults) scrollState.scrollTo(0)
        }
        settingsSearchContent(searchFocus, hasResults = hasSearchResults) {

            AiGeneralCard(search)


            AiQuickPicksCard(search)


            Box(modifier = Modifier.onGloballyPositioned { rewindCardTopY = it.positionInRoot().y }) {
                RewindSettingsCard(onOpenRewind = { navController.navigate(NavRoutes.rewindHome.name) })
            }

            AiRecommendationsCard(search)

            AiStatisticsCard(search, navController, Modifier.onGloballyPositioned { statisticsCardTopY = it.positionInRoot().y })

            AiTopPlaylistsCard(search)

            AiDataCard(search)

            AiResetCard(search)
        }

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )

    }
}
