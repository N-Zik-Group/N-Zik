package app.n_zik.android.components.settings.general

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.settings.AppShortcutsSettingsDialog
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsGroupSpacer
import androidx.compose.ui.text.input.TextFieldValue
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.settings.common.rememberSettingsSearchFocus
import app.n_zik.android.components.settings.common.settingsSearchContent
import app.n_zik.android.components.settings.common.settingsSearchDescription
import app.n_zik.android.components.settings.common.settingsSearchTitle
import app.n_zik.android.components.settings.common.visibleCardTitleRes
import app.n_zik.android.components.tab.Search
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * General settings tab (host-independent): header, description, per-tab search bar
 * and the 10 cards filtered at the card level by the registry. Owns the
 * `restartService`/`restartActivity` flags passed to the cards that set them.
 *
 * Settings-only focus behavior: focusing the search bar fades out and
 * collapses the title (layout removal, so the bar rises to the top); the
 * description and the cards do a plain alpha fade in place (no layout
 * movement), while the query is empty; the filtered cards act as search
 * results and stay visible once the query is non-empty.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GeneralSettings(
    focus: String = "",
) {
    val context = LocalContext.current
    // Settings per-tab: the search bar is static (visible from the very first
    // frame, no appearance animation), below the description, like the base
    // settings tabs.
    val search = Search(initialVisible = true)
    val visibleCards = visibleCardTitleRes(context, 0, search.inputValue)
    val searchFocus = rememberSettingsSearchFocus(search.isFocused, search.inputValue.isBlank())

    val isHistoryPaused by rememberPreference(pauseSearchHistoryKey, false)
    val history = rememberSearchHistory(SearchHistoryStore.PAGE_SETTINGS_GENERAL, isHistoryPaused)
    val historyMatches = filterSearchHistory(history.value, search.inputValue)
    val historyVisible = search.isFocused && historyMatches.isNotEmpty()

    fun recordSearchQuery(query: String = search.inputValue) {
        history.value = SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_SETTINGS_GENERAL, query)
    }

    var restartService by rememberSaveable { mutableStateOf(false) }
    var restartActivity by rememberSaveable { mutableStateOf(false) }

    // Scroll to the Picture in picture card when the screen was opened with focus=pip
    // (spec-customizable-burger-menu: long press on the PiP burger item).
    val scrollState = rememberScrollState()
    var containerTopY by remember { mutableFloatStateOf(-1f) }
    var pipCardTopY by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(focus) {
        if (focus != "pip") return@LaunchedEffect
        // The card may be off-screen but is still laid out: wait until its position is
        // measured (read inside snapshotFlow, see the same mechanism in OtherSettings).
        val offset = snapshotFlow {
            if (pipCardTopY < 0f || containerTopY < 0f) -1f
            else pipCardTopY - containerTopY + scrollState.value
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
        settingsSearchTitle(searchFocus, R.string.tab_general, R.drawable.ic_launcher_monochrome)

        settingsSearchDescription(searchFocus) {
            SettingsDescription(
                text = stringResource(R.string.general_settings_description),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // Search Section
        search.SearchBar(
            this,
            barPadding = searchFocus.barPadding,
            onSearch = { recordSearchQuery() }
        )

        SearchHistorySection(
            visible = historyVisible,
            queries = historyMatches,
            onQueryClick = { search.input = TextFieldValue(it); recordSearchQuery(it); search.isFocused = false },
            onQueryDelete = {
                history.value = SearchHistoryStore.delete(context.preferences, SearchHistoryStore.PAGE_SETTINGS_GENERAL, it)
            },
            onQueryDeleteAll = {
                history.value = SearchHistoryStore.deleteAll(context.preferences, SearchHistoryStore.PAGE_SETTINGS_GENERAL)
            }
        )

        val hasSearchResults = search.inputValue.isBlank() || visibleCardTitleRes(context, 0, search.inputValue).isNotEmpty()
        LaunchedEffect(hasSearchResults) {
            if (!hasSearchResults) scrollState.scrollTo(0)
        }
        settingsSearchContent(searchFocus, hasResults = hasSearchResults) {
            GeneralLanguageCard(visible = visibleCards.contains(R.string.languages))
            GeneralPlaybackCard(visible = visibleCards.contains(R.string.playback))
            GeneralPlayerControlsCard(
                visible = visibleCards.contains(R.string.player_controls),
                restartService = restartService,
                onRestartServiceSet = { restartService = true },
                onRestartServiceClear = { restartService = false }
            )
            GeneralQueueManagementCard(visible = visibleCards.contains(R.string.queue_management))
            GeneralAppBehaviorCard(
                visible = visibleCards.contains(R.string.app_behavior),
                restartService = restartService,
                onRestartServiceSet = { restartService = true },
                onRestartServiceClear = { restartService = false },
                restartActivity = restartActivity,
                onRestartActivitySet = { restartActivity = true },
                onRestartActivityClear = { restartActivity = false }
            )
            GeneralAudioEffectsCard(
                visible = visibleCards.contains(R.string.audio_effects),
                restartService = restartService,
                onRestartServiceSet = { restartService = true },
                onRestartServiceClear = { restartService = false }
            )
            GeneralGesturesCard(
                visible = visibleCards.contains(R.string.gestures_events),
                restartService = restartService,
                onRestartServiceSet = { restartService = true },
                onRestartServiceClear = { restartService = false }
            )
            GeneralPipCard(
                visible = visibleCards.contains(R.string.picture_in_picture),
                restartActivity = restartActivity,
                onRestartActivitySet = { restartActivity = true },
                onRestartActivityClear = { restartActivity = false },
                onCardTopYChanged = { pipCardTopY = it }
            )

            GeneralAppShortcutsCard(visible = visibleCards.contains(R.string.app_shortcuts_settings))

            GeneralResetCard(
                visible = visibleCards.contains(R.string.settings_reset),
                onRestartServiceSet = { restartService = true }
            )
        }

        AppShortcutsSettingsDialog.Render()

        RestartPlayerService(restartService, onRestart = { restartService = false })

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )
    }
}
