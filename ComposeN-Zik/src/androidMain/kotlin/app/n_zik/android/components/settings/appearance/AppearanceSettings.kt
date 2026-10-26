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

    var showthumbnail by rememberPreference(showthumbnailKey, true)
    var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)
    val isHistoryPaused by rememberPreference(pauseSearchHistoryKey, false)
    var playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.AnimatedGradient
    )
    var showvisthumbnail by rememberPreference(showvisthumbnailKey, true)
    var thumbnailpause by rememberPreference(thumbnailpauseKey, true)
    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
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

    val restartState = rememberSaveable { mutableStateOf(false) }
    var restartService by restartState

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

        if (playerBackgroundColors != PlayerBackgroundColors.BlurredCoverColor)
            showthumbnail = true
        if (!showthumbnail) {showlyricsthumbnail = false}
        if (playerType == PlayerType.Modern) {
            showlyricsthumbnail = false
            showvisthumbnail = false
            thumbnailpause = false

        }
        PlayerActionBarSettingsDialog.Render()
        MiniPlayerButtonsSettingsDialog.Render()
        val hasSearchResults = search.inputValue.isBlank() || visibleCardTitleRes(context, 2, search.inputValue).isNotEmpty()
        LaunchedEffect(hasSearchResults) {
            if (!hasSearchResults) scrollState.scrollTo(0)
        }
        settingsSearchContent(searchFocus, hasResults = hasSearchResults) {
            Column {
            AppearancePresetsCard(search)
            AppearanceCoverCard(search)
            AppearanceTimelineCard(search)
            AppearanceColorsCard(search)
            AppearanceBehaviorCard(search)
            }

            AppearanceLyricsCard(search)

            AppearanceActionBarCard(search)

            AppearanceFullscreenLyricsCard(search)

            AppearanceNotificationCard(search, restartState)

            AppearanceResetCard(search, restartState)
        }

        RestartPlayerService(restartService, onRestart = { restartService = false })

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )
    }
}
