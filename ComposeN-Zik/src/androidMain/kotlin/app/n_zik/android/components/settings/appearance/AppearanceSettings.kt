package app.n_zik.android.components.settings.appearance

import app.n_zik.android.components.settings.common.LocalSettingsResetRestart
import app.n_zik.android.components.settings.common.settingsPlayerRestartRequest
import app.n_zik.android.components.settings.common.RestartPlayerService
import app.n_zik.android.components.settings.common.settingsTopContentPadding

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.enums.AnimatedGradient
import app.n_zik.android.components.dialog.settings.PlayerActionBarSettingsDialog
import app.n_zik.android.components.dialog.settings.MiniPlayerButtonsSettingsDialog
import app.n_zik.android.components.dialog.settings.appearanceToolbarSettingsDialogs
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.pauseSearchHistoryKey
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.settings.common.filterSettingsSearchHistory
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.settings.common.SettingsCardSlot
import app.n_zik.android.components.settings.common.rememberSettingsSearchFocus
import app.n_zik.android.components.settings.common.visibleCardTitleRes
import app.n_zik.android.components.settings.common.settingsSearchContent
import app.n_zik.android.components.settings.common.settingsSearchDescription
import app.n_zik.android.components.settings.common.settingsSearchTitle
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.components.tab.Search
import androidx.compose.runtime.LaunchedEffect
import app.n_zik.android.components.settings.common.SettingsDescription

@ExperimentalAnimationApi
@UnstableApi
@Composable
fun AppearanceSettings(
    navController: NavController,
) {

    val isHistoryPaused by rememberPreference(pauseSearchHistoryKey, false)
    val scrollState = rememberScrollState()
    val search = Search(initialVisible = true)
    val searchFocus = rememberSettingsSearchFocus(search.isFocused, search.inputValue.isBlank())

    val context = LocalContext.current

    val history = rememberSearchHistory(SearchHistoryStore.PAGE_SETTINGS_APPEARANCE, isHistoryPaused)
    val historyMatches = filterSettingsSearchHistory(history.value, search.inputValue)
    val historyVisible = search.isFocused && historyMatches.isNotEmpty()

    fun recordSearchQuery(query: String = search.inputValue) {
        history.value = SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_SETTINGS_APPEARANCE, query)
    }

    Column(
        modifier = Modifier
            .background(colorPalette().background0)

            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .settingsTopContentPadding()

    ) {
        settingsSearchTitle(searchFocus, R.string.player_appearance, R.drawable.color_palette)

        settingsSearchDescription(searchFocus) {
            SettingsDescription(
                text = stringResource(R.string.settings_player_appearance_page_description),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        search.SearchBar(this, barPadding = searchFocus.barPadding, onSearch = { recordSearchQuery() })

        SearchHistorySection(
            visible = historyVisible,
            queries = historyMatches,
            allQueries = history.value,
            onQueryClick = { search.input = TextFieldValue(it); recordSearchQuery(it); search.isFocused = false },
            onQueryDelete = {
                history.value = SearchHistoryStore.delete(context.preferences, SearchHistoryStore.PAGE_SETTINGS_APPEARANCE, it)
            },
            onQueryDeleteAll = {
                history.value = SearchHistoryStore.deleteAll(context.preferences, SearchHistoryStore.PAGE_SETTINGS_APPEARANCE)
            }
        )

        val hasSearchResults = search.inputValue.isBlank() || visibleCardTitleRes(context, 2, search.inputValue).isNotEmpty()
        LaunchedEffect(hasSearchResults) {
            if (!hasSearchResults) scrollState.scrollTo(0)
        }
        AppearanceSettingsContent(
            search = search,
            navController = navController,
            cardsContainer = { cards -> settingsSearchContent(searchFocus, hasResults = hasSearchResults, query = search.inputValue, content = cards) },
        )

    }
}

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun AppearanceSettingsContent(
    search: Search,
    navController: NavController,
    selectedCards: Set<Int>? = null,
    cardModifier: @Composable (Int) -> Modifier = { Modifier },
    cardsContainer: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    val requestPlayerRestart = settingsPlayerRestartRequest()
    val onResetRestartRequired = LocalSettingsResetRestart.current ?: requestPlayerRestart
    PlayerActionBarSettingsDialog.Render()
    appearanceToolbarSettingsDialogs.forEach { it.Render() }
    MiniPlayerButtonsSettingsDialog.Render()
    cardsContainer {
        if (selectedCards == null || R.string.player_presets_and_layout in selectedCards) {
            SettingsCardSlot(R.string.player_presets_and_layout, modifier = { cardModifier(R.string.player_presets_and_layout) }) {
                AppearancePresetsCard(search)
            }
        }
        if (selectedCards == null || R.string.settings_player_artwork_section_title in selectedCards) {
            SettingsCardSlot(R.string.settings_player_artwork_section_title, modifier = { cardModifier(R.string.settings_player_artwork_section_title) }) {
                AppearanceCoverCard(search)
            }
        }
        if (selectedCards == null || R.string.settings_player_background_section_title in selectedCards) {
            SettingsCardSlot(R.string.settings_player_background_section_title, modifier = { cardModifier(R.string.settings_player_background_section_title) }) {
                AppearanceColorsCard(search)
            }
        }
        if (selectedCards == null || R.string.settings_player_timeline_controls_section_title in selectedCards) {
            SettingsCardSlot(R.string.settings_player_timeline_controls_section_title, modifier = { cardModifier(R.string.settings_player_timeline_controls_section_title) }) {
                AppearanceTimelineCard(search)
            }
        }
        if (selectedCards == null || R.string.settings_section_player_action_bar_title in selectedCards) {
            SettingsCardSlot(R.string.settings_section_player_action_bar_title, modifier = { cardModifier(R.string.settings_section_player_action_bar_title) }) {
                AppearanceActionBarCard(search)
            }
        }
        if (selectedCards == null || R.string.player_behavior_and_visuals in selectedCards) {
            SettingsCardSlot(R.string.player_behavior_and_visuals, modifier = { cardModifier(R.string.player_behavior_and_visuals) }) {
                AppearanceBehaviorCard(search)
            }
        }
        if (selectedCards == null || R.string.lyrics_title in selectedCards) {
            SettingsCardSlot(R.string.lyrics_title, modifier = { cardModifier(R.string.lyrics_title) }) {
                AppearanceLyricsCard(search)
            }
        }
        if (selectedCards == null || R.string.settings_section_fullscreen_lyrics_title in selectedCards) {
            SettingsCardSlot(R.string.settings_section_fullscreen_lyrics_title, modifier = { cardModifier(R.string.settings_section_fullscreen_lyrics_title) }) {
                AppearanceFullscreenLyricsCard(search)
            }
        }
        if (selectedCards == null || R.string.settings_section_notification_player_title in selectedCards) {
            SettingsCardSlot(R.string.settings_section_notification_player_title, modifier = { cardModifier(R.string.settings_section_notification_player_title) }) {
                AppearanceNotificationCard(search, requestPlayerRestart)
            }
        }
        if (selectedCards == null || R.string.settings_reset in selectedCards) {
            SettingsCardSlot(R.string.settings_reset, modifier = { cardModifier(R.string.settings_reset) }) {
                AppearanceResetCard(search, onResetRestartRequired)
            }
        }
    }
    if (selectedCards == null) RestartPlayerService()
}
