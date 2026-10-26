package app.n_zik.android.components.settings.other

import androidx.compose.animation.AnimatedVisibility
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
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.settings.ExtensionsSettingsCard
import app.n_zik.android.components.settings.MaintenanceSettingsCard
import app.n_zik.android.components.settings.RescueCenterSettingsCard
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsGroupSpacer
import app.n_zik.android.components.settings.common.rememberSettingsSearchFocus
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.settingsSearchContent
import app.n_zik.android.components.settings.common.settingsSearchDescription
import app.n_zik.android.components.settings.common.settingsSearchTitle
import app.n_zik.android.components.settings.common.visibleCardTitleRes
import app.n_zik.android.components.tab.Search
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun OtherSettings(
    focus: String = ""
) {
    val search = Search(initialVisible = true)
    val searchFocus = rememberSettingsSearchFocus(search.isFocused, search.inputValue.isBlank())

    val context = LocalContext.current

    val isHistoryPaused by rememberPreference(pauseSearchHistoryKey, false)
    val history = rememberSearchHistory(SearchHistoryStore.PAGE_SETTINGS_OTHER, isHistoryPaused)
    val historyMatches = filterSearchHistory(history.value, search.inputValue)
    val historyVisible = search.isFocused && historyMatches.isNotEmpty()

    fun recordSearchQuery(query: String = search.inputValue) {
        history.value = SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_SETTINGS_OTHER, query)
    }

    val scrollState = rememberScrollState()
    var containerTopY by remember { mutableFloatStateOf(-1f) }
    var maintenanceCardTopY by remember { mutableFloatStateOf(-1f) }
    var extensionsCardTopY by remember { mutableFloatStateOf(-1f) }
    var rescueCardTopY by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(focus) {
        if (focus != "debug" && focus != "maintenance" && focus != "extensions" && focus != "rescue") return@LaunchedEffect

        val focusedRescue = focus == "rescue"
        val focusedExtensions = focus == "extensions"
        val offset = snapshotFlow {
            val cardTopY = when {
                focusedRescue -> rescueCardTopY
                focusedExtensions -> extensionsCardTopY
                else -> maintenanceCardTopY
            }
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

        settingsSearchTitle(searchFocus, R.string.tab_miscellaneous, R.drawable.equalizer)

        settingsSearchDescription(searchFocus) {
            SettingsDescription(
                text = stringResource(R.string.other_settings_description),
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
                history.value = SearchHistoryStore.delete(context.preferences, SearchHistoryStore.PAGE_SETTINGS_OTHER, it)
            },
            onQueryDeleteAll = {
                history.value = SearchHistoryStore.deleteAll(context.preferences, SearchHistoryStore.PAGE_SETTINGS_OTHER)
            }
        )

        val hasSearchResults = search.inputValue.isBlank() || visibleCardTitleRes(context, 7, search.inputValue).isNotEmpty()
        LaunchedEffect(hasSearchResults) {
            if (!hasSearchResults) scrollState.scrollTo(0)
        }
        settingsSearchContent(searchFocus, hasResults = hasSearchResults) {
            OtherOnDeviceCard(search)

            OtherHeadUnitCard(search)

            OtherServiceLifetimeCard(search)

            OtherProxyCard(search)

            OtherParentalControlCard(search)

            val searchCtx_Maintenance = search.inputValue.isBlank() || stringResource(R.string.maintenance).contains(search.inputValue, true) || stringResource(R.string.maintenance_open).contains(search.inputValue, true) || stringResource(R.string.enable_log_debug).contains(search.inputValue, true) || stringResource(R.string.if_enabled_create_a_log_file_to_highlight_errors).contains(search.inputValue, true) || stringResource(R.string.export_logs).contains(search.inputValue, true) || stringResource(R.string.export_debug_log_description).contains(search.inputValue, true)
            AnimatedVisibility(
                visible = searchCtx_Maintenance,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
                Box(modifier = Modifier.onGloballyPositioned { maintenanceCardTopY = it.positionInRoot().y }) {
                    MaintenanceSettingsCard()
                }
            }

            val searchCtx_Extensions = search.inputValue.isBlank() || stringResource(R.string.modules_title).contains(search.inputValue, true) || stringResource(R.string.module_ffmpeg).contains(search.inputValue, true) || stringResource(R.string.module_ffmpeg_description).contains(search.inputValue, true)
            AnimatedVisibility(
                visible = searchCtx_Extensions,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
                Box(modifier = Modifier.onGloballyPositioned { extensionsCardTopY = it.positionInRoot().y }) {
                    ExtensionsSettingsCard()
                }
            }

            val searchCtx_Rescue = search.inputValue.isBlank() || stringResource(R.string.rescue_center).contains(search.inputValue, true) || stringResource(R.string.rescue_center_description).contains(search.inputValue, true)
            AnimatedVisibility(
                visible = searchCtx_Rescue,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
                Box(modifier = Modifier.onGloballyPositioned { rescueCardTopY = it.positionInRoot().y }) {
                    RescueCenterSettingsCard()
                }
            }

            OtherResetCard(search)
        }

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )

    }
}
