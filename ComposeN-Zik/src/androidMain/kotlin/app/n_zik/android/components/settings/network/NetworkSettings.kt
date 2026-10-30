package app.n_zik.android.components.settings.network

import app.n_zik.android.components.settings.common.settingsTopContentPadding

import app.n_zik.android.components.tab.Search
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.n_zik.android.components.settings.common.SettingsCardSlot
import app.n_zik.android.components.settings.common.SettingsSourceFooter
import app.n_zik.android.components.settings.common.rememberSettingsSearchFocus
import app.n_zik.android.components.settings.common.visibleCardTitleRes
import app.n_zik.android.components.settings.common.settingsSearchContent
import app.n_zik.android.components.settings.common.settingsSearchDescription
import app.n_zik.android.components.settings.common.settingsSearchTitle
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.preferences
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import app.n_zik.android.components.settings.OfflineModeSettingsCard
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
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
            .settingsTopContentPadding()
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
        NetworkSettingsContent(
            search, navController,
            onCardPosition = { id, y -> if (id == "offline") offlineCardTopY = y else pcServerCardTopY = y },
            cardsContainer = { cards -> settingsSearchContent(searchFocus, hasResults = hasSearchResults, query = search.inputValue) { cards() } }
        )


    }
}

@ExperimentalAnimationApi
@UnstableApi
@Composable
internal fun NetworkSettingsContent(
    search: Search,
    navController: NavController,
    selectedCards: Set<Int>? = null,
    cardModifier: @Composable (Int) -> Modifier = { Modifier },
    onCardPosition: (String, Float) -> Unit = { _, _ -> },
    cardsContainer: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    var restartService by rememberSaveable { mutableStateOf(false) }
    val audioDialog = rememberSaveable { mutableStateOf(false) }
    val imageDialog = rememberSaveable { mutableStateOf(false) }
    val networkQuality by produceState(initialValue = ImageCacheFactory.getCurrentNetworkQuality()) {
        while (true) {
            value = ImageCacheFactory.getCurrentNetworkQuality()
            delay(5000)
        }
    }

    cardsContainer {
        if (selectedCards == null) {
            SettingsCardSlot(R.string.network_status, modifier = { cardModifier(R.string.network_status) }) { NetworkStatusCard(networkQuality) }
        }
        if (selectedCards == null || R.string.quality in selectedCards) {
            SettingsCardSlot(R.string.quality, modifier = { cardModifier(R.string.quality) }) {
                NetworkQualityCard(search.inputValue, audioDialog, imageDialog,
                    networkQuality = networkQuality,
                    includeDownloadQuality = selectedCards == null) { restartService = it }
            }
        }
        if (selectedCards != null && R.string.settings_download_quality in selectedCards) {
            SettingsCardSlot(R.string.settings_download_quality, modifier = { cardModifier(R.string.settings_download_quality) }) {
                NetworkDownloadQualityCard(search.inputValue, networkQuality) { restartService = it }
            }
        }
        if (selectedCards == null || R.string.connection_settings in selectedCards) {
            SettingsCardSlot(R.string.connection_settings, modifier = { cardModifier(R.string.connection_settings) }) { NetworkConnectionCard(search.inputValue) }
        }
        if (selectedCards == null || R.string.offline_mode in selectedCards) {
            SettingsCardSlot(R.string.offline_mode, modifier = { cardModifier(R.string.offline_mode) }) { val matches = search.inputValue.isBlank() || stringResource(R.string.offline_mode).contains(search.inputValue, true) || stringResource(R.string.offline_mode_description).contains(search.inputValue, true)
                if (matches) OfflineModeSettingsCard(Modifier.onGloballyPositioned { onCardPosition("offline", it.positionInRoot().y) }) }
        }
        if (selectedCards == null || R.string.bridge_server in selectedCards) {
            SettingsCardSlot(R.string.bridge_server, modifier = { cardModifier(R.string.bridge_server) }) { NetworkServerCard(search.inputValue, navController, Modifier.onGloballyPositioned { onCardPosition("pcserver", it.positionInRoot().y) }) }
        }
        if (selectedCards == null || R.string.download in selectedCards) {
            SettingsCardSlot(R.string.download, modifier = { cardModifier(R.string.download) }) { NetworkDownloadCard(search.inputValue) }
        }
        if (selectedCards == null || R.string.settings_reset in selectedCards) {
            SettingsCardSlot(R.string.settings_reset, modifier = { cardModifier(R.string.settings_reset) }) { NetworkResetCard(search.inputValue) { restartService = true } }
        }
        // The reset-only System host also needs the restart action after restoring defaults.
        SettingsCardSlot(SettingsSourceFooter) {
            RestartPlayerService(restartService, onRestart = { restartService = false })
        }
    }
}
