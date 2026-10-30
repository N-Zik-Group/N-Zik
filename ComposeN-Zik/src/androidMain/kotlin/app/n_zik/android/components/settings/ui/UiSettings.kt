package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.settings.common.settingsTopContentPadding

import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlinx.coroutines.flow.first
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.settings.HistorySortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeLibrarySortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeLibraryToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomePlaylistsSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeTabsSettingsDialog
import app.n_zik.android.components.dialog.settings.LocalPlaylistSortSettingsDialog
import app.n_zik.android.components.dialog.settings.LocalPlaylistToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.QuickPicksContentSettingsDialog
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.settings.BurgerMenuSettingsCard
import app.n_zik.android.components.settings.common.SettingsCardSlot
import app.n_zik.android.components.settings.common.rememberSettingsSearchFocus
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.settingsSearchContent
import app.n_zik.android.components.settings.common.settingsSearchDescription
import app.n_zik.android.components.settings.common.settingsSearchTitle
import app.n_zik.android.components.settings.common.visibleCardTitleRes
import app.n_zik.android.components.tab.Search
import app.n_zik.android.R

/** Interface settings orchestration; cards own their preferences and actions. */
@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Suppress("UNUSED_PARAMETER") // Kept for the existing navigation call contract.
@Composable
fun UiSettings(navController: NavController, focus: String = "") {
    val search = Search(initialVisible = true)
    val searchFocus = rememberSettingsSearchFocus(search.isFocused, search.inputValue.isBlank())
    val context = LocalContext.current
    val isHistoryPaused by rememberPreference(pauseSearchHistoryKey, false)
    val history = rememberSearchHistory(SearchHistoryStore.PAGE_SETTINGS_UI, isHistoryPaused)
    val historyMatches = filterSearchHistory(history.value, search.inputValue)
    val historyVisible = search.isFocused && historyMatches.isNotEmpty()

    fun recordSearchQuery(query: String = search.inputValue) {
        history.value = SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_SETTINGS_UI, query)
    }

    // Scroll to the Burger menu card when the screen was opened with focus=burger
    // (spec-customizable-burger-menu: long press on the Settings burger item).
    val scrollState = rememberScrollState()
    var containerTopY by remember { mutableFloatStateOf(-1f) }
    var burgerCardTopY by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(focus) {
        if (focus != "burger") return@LaunchedEffect
        // The card may be off-screen but is still laid out: wait until its position is
        // measured (read inside snapshotFlow, see the same mechanism in OtherSettings).
        val offset = snapshotFlow {
            if (burgerCardTopY < 0f || containerTopY < 0f) -1f
            else burgerCardTopY - containerTopY + scrollState.value
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

        settingsSearchTitle(searchFocus, R.string.user_interface, R.drawable.ic_launcher_monochrome)

        settingsSearchDescription(searchFocus) {
            SettingsDescription(
                text = stringResource(R.string.ui_settings_description),
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
                history.value = SearchHistoryStore.delete(context.preferences, SearchHistoryStore.PAGE_SETTINGS_UI, it)
            },
            onQueryDeleteAll = {
                history.value = SearchHistoryStore.deleteAll(context.preferences, SearchHistoryStore.PAGE_SETTINGS_UI)
            }
        )

        val hasSearchResults = search.inputValue.isBlank() || visibleCardTitleRes(context, 1, search.inputValue).isNotEmpty()
        LaunchedEffect(hasSearchResults) {
            if (!hasSearchResults) scrollState.scrollTo(0)
        }
        UiSettingsContent(
            search = search,
            navController = navController,
            onCardPosition = { _, top -> burgerCardTopY = top },
            cardsContainer = { cards -> settingsSearchContent(searchFocus, hasResults = hasSearchResults, query = search.inputValue, content = cards) },
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun UiSettingsContent(
    search: Search,
    navController: NavController,
    selectedCards: Set<Int>? = null,
    cardModifier: @Composable (Int) -> Modifier = { Modifier },
    onCardPosition: (String, Float) -> Unit = { _, _ -> },
    cardsContainer: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    app.n_zik.android.components.dialog.settings.DockToolbarSettingsDialog.Render()
    HomeTabsSettingsDialog.Render()
    HomePlaylistsSettingsDialog.Render()
    HomeSongsSettingsDialog.Render()
    HomeArtistsSettingsDialog.Render()
    HomeAlbumsSettingsDialog.Render()
    QuickPicksContentSettingsDialog.Render()
    HomeSongsToolbarSettingsDialog.Render()
    HomeArtistsToolbarSettingsDialog.Render()
    HomeAlbumsToolbarSettingsDialog.Render()
    HomeLibraryToolbarSettingsDialog.Render()
    HomeSongsSortSettingsDialog.Render()
    HomeAlbumsSortSettingsDialog.Render()
    HomeArtistsSortSettingsDialog.Render()
    HomeLibrarySortSettingsDialog.Render()
    HistorySortSettingsDialog.Render()
    LocalPlaylistSortSettingsDialog.Render()
    LocalPlaylistToolbarSettingsDialog.Render()
    cardsContainer {
        if (selectedCards == null || R.string.user_interface in selectedCards) {
            SettingsCardSlot(R.string.user_interface, modifier = { cardModifier(R.string.user_interface) }) {
                UiInterfaceCard(search)
            }
        }
        if (selectedCards == null || R.string.floating_dock_settings in selectedCards) {
            SettingsCardSlot(R.string.floating_dock_settings, modifier = { cardModifier(R.string.floating_dock_settings) }) {
                UiFloatingDockCard(search)
            }
        }
        if (selectedCards == null || R.string.theme in selectedCards) {
            SettingsCardSlot(R.string.theme, modifier = { cardModifier(R.string.theme) }) {
                UiThemeCard(search)
            }
        }
        if (selectedCards == null || R.string.settings_use_font_type in selectedCards) {
            SettingsCardSlot(R.string.settings_use_font_type, modifier = { cardModifier(R.string.settings_use_font_type) }) {
                UiFontCard(search)
            }
        }
        if (selectedCards == null || R.string.swipe_to_action in selectedCards) {
            SettingsCardSlot(R.string.swipe_to_action, modifier = { cardModifier(R.string.swipe_to_action) }) {
                UiSwipeCard(search)
            }
        }
        if (selectedCards == null || R.string.home_tabs_settings in selectedCards) {
            SettingsCardSlot(R.string.home_tabs_settings, modifier = { cardModifier(R.string.home_tabs_settings) }) {
                UiHomeTabsCard(search)
            }
        }
        if (selectedCards == null || R.string.burger_menu in selectedCards) {
            SettingsCardSlot(R.string.burger_menu, modifier = { cardModifier(R.string.burger_menu) }) {
                val burgerSearchContextMatch = search.inputValue.isBlank() ||
                    stringResource(R.string.burger_menu).contains(search.inputValue, true) ||
                    stringResource(R.string.burger_menu_customize).contains(search.inputValue, true)
                AnimatedVisibility(
                    visible = burgerSearchContextMatch,
                    enter = settingsCardEnter,
                    exit = settingsCardExit,
                ) {
                    BurgerMenuSettingsCard(
                        modifier = Modifier.testTag("ui.card.burger")
                            .onGloballyPositioned { onCardPosition("burger", it.positionInRoot().y) }
                    )
                }
            }
        }
        if (selectedCards == null || R.string.toolbar in selectedCards) {
            SettingsCardSlot(R.string.toolbar, modifier = { cardModifier(R.string.toolbar) }) {
                UiToolbarsCard(search)
            }
        }
        if (selectedCards == null || R.string.sort_menu in selectedCards) {
            SettingsCardSlot(R.string.sort_menu, modifier = { cardModifier(R.string.sort_menu) }) {
                UiSortMenusCard(search)
            }
        }
        if (selectedCards == null || R.string.settings_reset in selectedCards) {
            SettingsCardSlot(R.string.settings_reset, modifier = { cardModifier(R.string.settings_reset) }) {
                UiResetCard(search)
            }
        }
    }
}
