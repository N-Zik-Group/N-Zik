package app.n_zik.android.components.settings.ai

import kotlin.math.roundToInt
import androidx.compose.foundation.background
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
import app.n_zik.android.components.settings.common.SettingsCardSlot
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
        AIRecommendationSettingsContent(
            search = search,
            navController = navController,
            onCardPosition = { id, top ->
                when (id) {
                    "rewind" -> rewindCardTopY = top
                    "statistics" -> statisticsCardTopY = top
                }
            },
            cardsContainer = { cards -> settingsSearchContent(searchFocus, hasResults = hasSearchResults) { cards() } },
        )


        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )

    }
}

@UnstableApi
@Composable
internal fun AIRecommendationSettingsContent(
    search: Search,
    navController: NavController,
    selectedCards: Set<Int>? = null,
    cardModifier: @Composable (Int) -> Modifier = { Modifier },
    onCardPosition: (String, Float) -> Unit = { _, _ -> },
    cardsContainer: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    QuickPicksContentSettingsDialog.Render()
    cardsContainer {
        if (selectedCards == null || R.string.tab_general in selectedCards) {
            SettingsCardSlot(R.string.tab_general, modifier = { cardModifier(R.string.tab_general) }) {
                AiGeneralCard(search, showRewindMaster = selectedCards == null)
            }
        }
        if (selectedCards == null || R.string.quick_picks in selectedCards) {
            SettingsCardSlot(R.string.quick_picks, modifier = { cardModifier(R.string.quick_picks) }) {
                AiQuickPicksCard(search)
            }
        }
        if (selectedCards == null || R.string.rw_settings_title in selectedCards) {
            SettingsCardSlot(R.string.rw_settings_title, modifier = { cardModifier(R.string.rw_settings_title).onGloballyPositioned { onCardPosition("rewind", it.positionInRoot().y) } }) {
                RewindSettingsCard(onOpenRewind = { navController.navigate(NavRoutes.rewindHome.name) })
            }
        }
        if (selectedCards == null || R.string.smart_recommendations in selectedCards) {
            SettingsCardSlot(R.string.smart_recommendations, modifier = { cardModifier(R.string.smart_recommendations) }) {
                AiRecommendationsCard(search)
            }
        }
        if (selectedCards == null || R.string.statistics in selectedCards) {
            SettingsCardSlot(R.string.statistics, modifier = { cardModifier(R.string.statistics).onGloballyPositioned { onCardPosition("statistics", it.positionInRoot().y) } }) {
                AiStatisticsCard(search, navController)
            }
        }
        if (selectedCards == null || R.string.playlist_top in selectedCards) {
            SettingsCardSlot(R.string.playlist_top, modifier = { cardModifier(R.string.playlist_top) }) {
                AiTopPlaylistsCard(search)
            }
        }
        if (selectedCards == null || R.string.tab_data in selectedCards) {
            SettingsCardSlot(R.string.tab_data, modifier = { cardModifier(R.string.tab_data) }) {
                AiDataCard(search)
            }
        }
        if (selectedCards == null || R.string.settings_reset in selectedCards) {
            SettingsCardSlot(R.string.settings_reset, modifier = { cardModifier(R.string.settings_reset) }) {
                AiResetCard(search)
            }
        }
    }
}
