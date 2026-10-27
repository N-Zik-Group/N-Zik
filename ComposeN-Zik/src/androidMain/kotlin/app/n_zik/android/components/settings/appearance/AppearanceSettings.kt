package app.n_zik.android.components.settings.appearance

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.it.fast4x.rimusic.enums.AnimatedGradient
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.enums.PlayerType
import app.n_zik.android.components.dialog.settings.PlayerActionBarSettingsDialog
import app.n_zik.android.components.dialog.settings.MiniPlayerButtonsSettingsDialog
import androidx.compose.ui.text.input.TextFieldValue
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.settings.common.SettingsCardSlot
import app.n_zik.android.components.settings.common.SettingsSourceFooter
import app.n_zik.android.components.settings.common.rememberSettingsSearchFocus
import app.n_zik.android.components.settings.common.visibleCardTitleRes
import app.n_zik.android.components.settings.common.settingsSearchContent
import app.n_zik.android.components.settings.common.settingsSearchDescription
import app.n_zik.android.components.settings.common.settingsSearchTitle
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.showvisthumbnailKey
import app.it.fast4x.rimusic.utils.thumbnailpauseKey
import app.n_zik.android.components.tab.Search
import androidx.compose.runtime.LaunchedEffect
import app.n_zik.android.components.settings.common.SettingsGroupSpacer
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
    val historyMatches = filterSearchHistory(history.value, search.inputValue)
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

    ) {
        settingsSearchTitle(searchFocus, R.string.player_appearance, R.drawable.color_palette)

        settingsSearchDescription(searchFocus) {
            SettingsDescription(
                text = stringResource(R.string.appearance_settings_description),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        search.SearchBar(this, barPadding = searchFocus.barPadding, onSearch = { recordSearchQuery() })

        SearchHistorySection(
            visible = historyVisible,
            queries = historyMatches,
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

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
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
    normalizePreferences: Boolean = true,
    cardModifier: @Composable (Int) -> Modifier = { Modifier },
    cardsContainer: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    var showthumbnail by rememberPreference(showthumbnailKey, true)
    var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)
    var playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.AnimatedGradient
    )
    var showvisthumbnail by rememberPreference(showvisthumbnailKey, true)
    var thumbnailpause by rememberPreference(thumbnailpauseKey, true)
    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    val restartState = rememberSaveable { mutableStateOf(false) }
    var restartService by restartState
    if (normalizePreferences && (selectedCards == null || R.string.player_presets_and_layout in selectedCards || R.string.cover_and_thumbnail in selectedCards)) {
    if (playerBackgroundColors != PlayerBackgroundColors.BlurredCoverColor)
        showthumbnail = true
    if (!showthumbnail) {showlyricsthumbnail = false}
    if (playerType == PlayerType.Modern) {
        showlyricsthumbnail = false
        showvisthumbnail = false
        thumbnailpause = false

    }
    }
    PlayerActionBarSettingsDialog.Render()
    MiniPlayerButtonsSettingsDialog.Render()
    cardsContainer {
        if (selectedCards == null || R.string.player_presets_and_layout in selectedCards) {
            SettingsCardSlot(R.string.player_presets_and_layout, modifier = { cardModifier(R.string.player_presets_and_layout) }) {
                AppearancePresetsCard(search)
            }
        }
        if (selectedCards == null || R.string.cover_and_thumbnail in selectedCards) {
            SettingsCardSlot(R.string.cover_and_thumbnail, modifier = { cardModifier(R.string.cover_and_thumbnail) }) {
                AppearanceCoverCard(search)
            }
        }
        if (selectedCards == null || R.string.colors_and_background in selectedCards) {
            SettingsCardSlot(R.string.colors_and_background, modifier = { cardModifier(R.string.colors_and_background) }) {
                AppearanceColorsCard(search)
            }
        }
        if (selectedCards == null || R.string.timeline_and_controls in selectedCards) {
            SettingsCardSlot(R.string.timeline_and_controls, modifier = { cardModifier(R.string.timeline_and_controls) }) {
                AppearanceTimelineCard(search)
            }
        }
        if (selectedCards == null || R.string.player_action_bar in selectedCards) {
            SettingsCardSlot(R.string.player_action_bar, modifier = { cardModifier(R.string.player_action_bar) }) {
                AppearanceActionBarCard(search)
            }
        }
        if (selectedCards == null || R.string.player_behavior_and_visuals in selectedCards) {
            SettingsCardSlot(R.string.player_behavior_and_visuals, modifier = { cardModifier(R.string.player_behavior_and_visuals) }) {
                AppearanceBehaviorCard(search)
            }
        }
        if (selectedCards == null || R.string.txt_lyrics in selectedCards) {
            SettingsCardSlot(R.string.txt_lyrics, modifier = { cardModifier(R.string.txt_lyrics) }) {
                AppearanceLyricsCard(search)
            }
        }
        if (selectedCards == null || R.string.full_screen_lyrics_components in selectedCards) {
            SettingsCardSlot(R.string.full_screen_lyrics_components, modifier = { cardModifier(R.string.full_screen_lyrics_components) }) {
                AppearanceFullscreenLyricsCard(search)
            }
        }
        if (selectedCards == null || R.string.notification_player in selectedCards) {
            SettingsCardSlot(R.string.notification_player, modifier = { cardModifier(R.string.notification_player) }) {
                AppearanceNotificationCard(search, restartState)
            }
        }
        if (selectedCards == null || R.string.settings_reset in selectedCards) {
            SettingsCardSlot(R.string.settings_reset, modifier = { cardModifier(R.string.settings_reset) }) {
                AppearanceResetCard(search, restartState)
            }
        }
    }
    SettingsCardSlot(SettingsSourceFooter) {
        RestartPlayerService(restartService, onRestart = { restartService = false })
    }
}
