package app.n_zik.android.components.settings.network

import app.n_zik.android.components.tab.Search
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.produceState
import app.n_zik.android.core.coil.ImageCacheFactory
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import androidx.compose.ui.text.input.TextFieldValue
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
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.preferences
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import app.n_zik.android.components.settings.OfflineModeSettingsCard
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import app.n_zik.android.components.settings.common.SettingsGroupSpacer
import app.n_zik.android.components.settings.common.SettingsDescription
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@ExperimentalAnimationApi
@UnstableApi
@Composable
fun NetworkSettings(
    navController: NavController,
    focus: String = ""
) {
    val search = Search(initialVisible = true)
    val searchFocus = rememberSettingsSearchFocus(search.isFocused, search.inputValue.isBlank())
    val context = LocalContext.current

    val isHistoryPaused by rememberPreference(pauseSearchHistoryKey, false)
    val history = rememberSearchHistory(SearchHistoryStore.PAGE_SETTINGS_NETWORK, isHistoryPaused)
    val historyMatches = filterSearchHistory(history.value, search.inputValue)
    val historyVisible = search.isFocused && historyMatches.isNotEmpty()

    fun recordSearchQuery(query: String = search.inputValue) {
        history.value = SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_SETTINGS_NETWORK, query)
    }

    var restartService by rememberSaveable { mutableStateOf(false) }
    val audioDialog = rememberSaveable { mutableStateOf(false) }
    val imageDialog = rememberSaveable { mutableStateOf(false) }
    val networkQuality by produceState(initialValue = ImageCacheFactory.getCurrentNetworkQuality()) {
        while (true) {
            value = ImageCacheFactory.getCurrentNetworkQuality()
            delay(5000)
        }
    }

    val scrollState = rememberScrollState()
    var containerTopY by remember { mutableFloatStateOf(-1f) }
    var pcServerCardTopY by remember { mutableFloatStateOf(-1f) }
    var offlineCardTopY by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(focus) {
        if (focus != "pcserver" && focus != "offline") return@LaunchedEffect

        val focusedOffline = focus == "offline"
        val offset = snapshotFlow {
            val cardTopY = if (focusedOffline) offlineCardTopY else pcServerCardTopY
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
        settingsSearchTitle(searchFocus, R.string.tab_network, R.drawable.network)

        settingsSearchDescription(searchFocus) {
            SettingsDescription(
                text = stringResource(R.string.network_settings_description),
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
                history.value = SearchHistoryStore.delete(context.preferences, SearchHistoryStore.PAGE_SETTINGS_NETWORK, it)
            },
            onQueryDeleteAll = {
                history.value = SearchHistoryStore.deleteAll(context.preferences, SearchHistoryStore.PAGE_SETTINGS_NETWORK)
            }
        )

        val hasSearchResults = search.inputValue.isBlank() || visibleCardTitleRes(context, 6, search.inputValue).isNotEmpty()
        LaunchedEffect(hasSearchResults) {
            if (!hasSearchResults) scrollState.scrollTo(0)
        }
        settingsSearchContent(searchFocus, hasResults = hasSearchResults) {
            NetworkServerCard(search.inputValue, navController,
                Modifier.onGloballyPositioned { pcServerCardTopY = it.positionInRoot().y })
            NetworkStatusCard(networkQuality)
            NetworkQualityCard(search.inputValue, restartService, audioDialog, imageDialog) { restartService = it }
            NetworkConnectionCard(search.inputValue)

            val offlineSearchMatch = search.inputValue.isBlank() ||
                stringResource(R.string.offline_mode).contains(search.inputValue, true) ||
                stringResource(R.string.offline_mode_description).contains(search.inputValue, true)
            if (offlineSearchMatch) {
                OfflineModeSettingsCard(
                    modifier = Modifier.onGloballyPositioned { offlineCardTopY = it.positionInRoot().y }
                )
            }

            NetworkDownloadCard(search.inputValue)
            NetworkResetCard(search.inputValue) { restartService = true }

        }

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )

    }
}
