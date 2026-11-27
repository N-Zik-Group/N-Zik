@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.components.ui.screens.home.quickpicks

import app.n_zik.android.utils.preferences
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewDemo
import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.compose.persist.LocalPersistMap
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.components.navigation.LocalSideRailContentInsets
import app.n_zik.android.compose.persist.persist
import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.MainApplication
import app.n_zik.android.enums.Countries
import app.n_zik.android.enums.LocalRecommandationsNumber
import app.n_zik.android.enums.MaxTopPlaylistItems
import app.n_zik.android.enums.PlayEventsType
import app.n_zik.android.models.Artist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.themed.HeaderWithIcon
import app.n_zik.android.ui.components.themed.MultiFloatingActionsContainer
import app.n_zik.android.components.settings.accounts.isYouTubeLoggedIn
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.px
import app.n_zik.android.utils.MaxTopPlaylistItemsCustomValueKey
import app.n_zik.android.utils.MaxTopPlaylistItemsKey
import app.n_zik.android.utils.WelcomeMessage
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.center
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.isNetworkAvailableComposable
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.playEventsTypeKey
import app.n_zik.android.utils.quickPicksSectionOrderKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.selectedCountryCodeKey
import app.n_zik.android.utils.showAlbumsForYouKey
import app.n_zik.android.utils.showAllHitsKey
import app.n_zik.android.utils.showChartsKey
import app.n_zik.android.utils.showCoverAndRemixesKey
import app.n_zik.android.utils.showFeaturedPlaylistsKey
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.showForgottenFavoritesKey
import app.n_zik.android.utils.showFreshFindsOldFavoritesKey
import app.n_zik.android.utils.showFreshNewMusicKey
import app.n_zik.android.utils.showFromTheCommunityKey
import app.n_zik.android.utils.showGenericYtmSectionsKey
import app.n_zik.android.utils.showLivePerformancesKey
import app.n_zik.android.utils.showMixedForYouKey
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.rememberDataStoreBooleanPreference
import app.n_zik.android.utils.showMonthlyPlaylistsKey
import app.n_zik.android.utils.showMoodsAndGenresKey
import app.n_zik.android.utils.showMoodsKey
import app.n_zik.android.utils.showMusicVideosForYouKey
import app.n_zik.android.utils.showMyTopPlaylistKey
import app.n_zik.android.utils.showNewAlbumsArtistsKey
import app.n_zik.android.utils.showNewAlbumsKey
import app.n_zik.android.utils.showNewReleasesKey
import app.n_zik.android.utils.showPlaylistMightLikeKey
import app.n_zik.android.utils.showRelatedAlbumsKey
import app.n_zik.android.utils.showSearchTabKey
import app.n_zik.android.utils.showSimilarArtistsKey
import app.n_zik.android.utils.showTipsKey
import app.n_zik.android.utils.showTodaysBiggestHitsKey
import app.n_zik.android.utils.showTopMusicVideosKey
import app.n_zik.android.utils.showTrendingCommunityPlaylistsKey
import app.n_zik.android.utils.showTrendingInShortsKey
import app.n_zik.android.utils.showTrendingSongsForYouKey
import app.n_zik.android.utils.showYourDailyDiscoverKey
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassFloatingActions
import app.n_zik.android.components.glass.glassFloatingActionContent
import app.n_zik.android.components.glass.rememberGlassFloatingActionBackdrop
import app.n_zik.android.components.navigation.TopOverlayContentPadding
import androidx.compose.ui.unit.Dp
import app.n_zik.android.components.OfflineContent
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.typography
import app.n_zik.android.playback.utils.Shuffler
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.queue
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.components.pullrefresh.OfflineGatedPullRefresh
import android.content.Context
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun HomeQuickPicks(
    navController: NavController,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onMoodClick: (mood: Innertube.Mood.Item) -> Unit,
    onChipClick: (chip: Innertube.Chip) -> Unit,
    onSettingsClick: () -> Unit,
    topContentInsetProvider: () -> Dp = { 0.dp },
) {
    val resetRevision = LocalPersistMap.current?.let { quickPicksResetRevision(it).intValue } ?: 0
    key(resetRevision) {
        val preview = LocalThemePreview.current
        val windowInsets = LocalPlayerAwareWindowInsets.current
        val context = LocalContext.current
        val isOnline = if (preview != null) true else NetworkQualityHelper.isNetworkAvailableComposable(context).value

        var playEventType by rememberPreference(playEventsTypeKey, PlayEventsType.MostPlayed)
        var selectedCountryCode by rememberPreference(selectedCountryCodeKey, Countries.ZZ)
        val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
        val localRecommandationsNumber by rememberPreference(
            key = "LocalRecommandationsNumber",
            defaultValue = LocalRecommandationsNumber.SixQ
        )
        val localCount = localRecommandationsNumber.value

        val state = rememberHomeQuickPicksState(
            playEventType = playEventType,
            selectedCountryCode = selectedCountryCode,
            parentalControlEnabled = parentalControlEnabled,
            localCount = localCount,
            resetRevision = resetRevision,
        )

        var lastPlayEventType by rememberSaveable { mutableStateOf(playEventType) }
        var lastSelectedCountry by rememberSaveable { mutableStateOf(selectedCountryCode) }
        var currentYouTubeLoggedIn by rememberSaveable { mutableStateOf(false) }

        if (preview == null) QuickPicksInitialLoadEffect(
            stateKey = state,
            resolveLoggedIn = { withContext(NzikDispatchers.DATA) { isYouTubeLoggedIn() } },
            onLoggedInResolved = { currentYouTubeLoggedIn = it },
            load = {
                val invalidate = playEventType != lastPlayEventType || selectedCountryCode != lastSelectedCountry
                lastPlayEventType = playEventType
                lastSelectedCountry = selectedCountryCode
                state.load(invalidate = invalidate)
            },
        )

        val fetchedHome = state.homePageResult.value?.getOrNull()
        LaunchedEffect(state, state.loadedData.value, fetchedHome) {
            val page = state.homePageInit.value ?: return@LaunchedEffect
            if (!state.loadedData.value || !state.isCurrentAccount()) return@LaunchedEffect
            val itemsToFetch = page.sections.flatMap { it.items }
                .filterIsInstance<Innertube.VideoItem>()
                .filter { it.durationText == null }
                .map { it.key }
                .distinct()
            if (itemsToFetch.isEmpty()) return@LaunchedEffect

            val durations = withContext(NzikDispatchers.DATA) {
                Innertube.queue(videoIds = itemsToFetch)?.getOrNull()
                    ?.associate { it.key to it.durationText }.orEmpty()
            }
            if (durations.isNotEmpty() && state.homePageInit.value === page && state.isCurrentAccount()) {
                state.homePageInit.value = page.copy(sections = page.sections.map { section ->
                    section.copy(items = section.items.map { item ->
                        when (item) {
                            is Innertube.VideoItem -> durations[item.key]?.let { item.copy(durationText = it) } ?: item
                            is Innertube.SongItem -> durations[item.key]?.let { item.copy(durationText = it) } ?: item
                            else -> item
                        }
                    })
                })
            }
        }

        val scrollState = rememberLazyListState()

        LaunchedEffect(state.refreshKey.value) {
            if (state.refreshKey.value > 0) {
                scrollState.animateScrollToItem(0)
            }
        }

        OfflineGatedPullRefresh(
            isOnline = isOnline,
            isRefreshing = state.refreshing.value,
            onRefresh = { if (preview == null) state.refresh() }
        ) {
            HomeQuickPicksContent(
                state = state,
                scrollState = scrollState,
                isOnline = isOnline,
                refresh = { if (preview == null) state.refresh(manual = false) },
                navController = navController,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick,
                onSearchClick = onSearchClick,
                onMoodClick = onMoodClick,
                onChipClick = onChipClick,
                onSettingsClick = onSettingsClick,
                onPlayEventTypeChange = { playEventType = it },
                onSelectedCountryCodeChange = { selectedCountryCode = it },
                currentYouTubeLoggedIn = currentYouTubeLoggedIn,
                playEventType = playEventType,
                selectedCountryCode = selectedCountryCode,
                parentalControlEnabled = parentalControlEnabled,
                windowInsets = windowInsets,
                topContentInsetProvider = topContentInsetProvider,
            )
        }
    }
}

@Composable
private fun <T> collectQuickPicksLocalShelf(
    slot: String,
    revision: Int,
    cache: MutableState<T?>,
    flow: Flow<T>,
): T? {
    val completedRevision = persist("home/quickpicks/local/$slot/revision", 0)
    val result = key(revision) {
        flow.collectAsStateWithLifecycle<T?>(
            initialValue = cache.value.takeIf { completedRevision.value == revision },
            context = NzikDispatchers.DATA,
        ).value
    }
    LaunchedEffect(revision, result) {
        if (result != null) {
            cache.value = result
            completedRevision.value = revision
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
internal @Composable
fun HomeQuickPicksContent(
    state: HomeQuickPicksState,
    scrollState: LazyListState,
    isOnline: Boolean,
    refresh: () -> Unit,
    navController: NavController,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onMoodClick: (mood: Innertube.Mood.Item) -> Unit,
    onChipClick: (chip: Innertube.Chip) -> Unit,
    onSettingsClick: () -> Unit,
    onPlayEventTypeChange: (PlayEventsType) -> Unit,
    onSelectedCountryCodeChange: (Countries) -> Unit,
    currentYouTubeLoggedIn: Boolean,
    playEventType: PlayEventsType,
    selectedCountryCode: Countries,
    parentalControlEnabled: Boolean,
    windowInsets: WindowInsets,
    topContentInsetProvider: () -> Dp = { 0.dp },
) {
    val preview = LocalThemePreview.current
    val binder = LocalPlayerServiceBinder.current
    val scope = rememberCoroutineScope()
    var isQuickPicksLoading by remember { mutableStateOf(false) }
    val forceShimmer = forceLoadingShimmerPreview()

    val showLoader = preview == null && isOnline && state.homePageInit.value == null &&
        state.homePageResult.value == null
    val chartsPending = preview == null && isOnline && state.chartsPageInit.value == null &&
        state.chartsPageResult.value == null
    val discoverPending = preview == null && isOnline && state.discoverPageInit.value == null &&
        state.discoverPageResult.value == null
    val relatedPending = preview == null && isOnline && state.relatedPageResult.value == null

    // Recover online shelves without resetting the content already on screen.
    var wasOnline by remember { mutableStateOf(true) }
    LaunchedEffect(isOnline) {
        if (isOnline && !wasOnline) {
            Timber.tag("HomeQuickPicks").d("Network restored, refreshing Quick Picks")
            refresh()
        }
        wasOnline = isOnline
    }

    val railPadding = LocalSideRailContentInsets.current.asPaddingValues()
    val endPaddingValues = windowInsets.only(WindowInsetsSides.End)
        .add(LocalSideRailContentInsets.current.only(WindowInsetsSides.Start)).asPaddingValues()
    val gridsContentPadding = PaddingValues(start = 12.dp + endPaddingValues.calculateStartPadding(LocalLayoutDirection.current), end = endPaddingValues.calculateEndPadding(LocalLayoutDirection.current))
    val sectionTextModifier = Modifier.padding(horizontal = 12.dp).padding(top = 16.dp, bottom = 8.dp).padding(endPaddingValues)
    val showSearchTab by rememberPreference(showSearchTabKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val itemInHorizontalGridWidth = maxWidth * (if (isLandscape && maxWidth * 0.475f >= 320.dp) 0.375f else 0.7f)
            val gridDirection = LocalLayoutDirection.current
            val viewportWidth = maxWidth - endPaddingValues.calculateStartPadding(gridDirection) -
                endPaddingValues.calculateEndPadding(gridDirection)
            val initialSlots = visibleQuickPicksSlots(viewportWidth.value, itemInHorizontalGridWidth.value)
            val manualRevision = state.manualRefreshRevision.value
            val history = state.trendingList.value
            val related = state.relatedPageResult.value
            val personalized = state.ytmQuickPicks.value
            val historyPending = !state.loadedQuickPicks.value
            val onlinePending = state.onlineRecommendationsPending(isOnline, currentYouTubeLoggedIn)
            LaunchedEffect(state, history, related, personalized, historyPending, onlinePending, manualRevision, initialSlots) {
                if (preview != null) return@LaunchedEffect
                val suggestions = (personalized + related?.getOrNull()?.songs.orEmpty().map { it.asSong })
                    .filter { !parentalControlEnabled || !it.title.startsWith(EXPLICIT_PREFIX, true) }
                val slots = fillQuickPicksRecommendationSlots(
                    previous = state.recommendations.value,
                    history = history,
                    online = suggestions,
                    historyPending = historyPending,
                    onlinePending = onlinePending,
                    keepFirstHistorySong = playEventType != PlayEventsType.CasualPlayed,
                    seed = history.map(Song::id).hashCode() xor manualRevision,
                    initialSlots = initialSlots,
                )
                state.publishRecommendations(manualRevision, slots)
            }
            val albumThumbnailSizeDp = 108.dp
            val albumThumbnailSizePx = albumThumbnailSizeDp.px
            val artistThumbnailSizeDp = 92.dp
            val artistThumbnailSizePx = artistThumbnailSizeDp.px
            val playlistThumbnailSizeDp = 108.dp
            val playlistThumbnailSizePx = playlistThumbnailSizeDp.px
            val songThumbnailSizeDp = Dimensions.thumbnails.song
            val songThumbnailSizePx = songThumbnailSizeDp.px

            val showTips by rememberPreference(showTipsKey, true)
            val displayedSectionTitles = remember(manualRevision) { mutableSetOf<String>() }
            val ytmSections = state.homePageInit.value?.sections.orEmpty()

            val artistsState = persist<List<Artist>?>("home/quickpicks/local/artists")
            val artistsFlow = remember(preview) {
                if (preview != null) kotlinx.coroutines.flow.flowOf(emptyList())
                else Database.artistTable.sortFollowingByName().distinctUntilChanged().catch { error ->
                    Timber.tag("HomeQuickPicks").w(error, "Could not read followed artists")
                    emit(artistsState.value.orEmpty())
                }
            }
            val artistsResult = collectQuickPicksLocalShelf("artists", manualRevision, artistsState, artistsFlow)
            val artists = artistsResult.orEmpty()

            val newReleaseAlbumsFiltered = remember(state.discoverPageInit.value, artists) {
                state.discoverPageInit.value?.newReleaseAlbums?.filter { album ->
                    artists.any { it.name == album.authors?.firstOrNull()?.name }
                }.orEmpty()
            }

            val maxTopPlaylistItems by rememberPreference(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`)
            val maxTopPlaylistItemsCustomValue by rememberPreference(MaxTopPlaylistItemsCustomValueKey, 10)
            val myTopSongsState = persist<List<Song>?>("home/quickpicks/local/myTopSongs")
            val myTopSongsFlow = remember(preview, maxTopPlaylistItems, maxTopPlaylistItemsCustomValue) {
                if (preview != null) kotlinx.coroutines.flow.flowOf(ThemePreviewDemo.songs.take(8))
                else Database.eventTable.findSongsMostPlayedBetween(
                    from = 0L,
                    limit = maxTopPlaylistItems.toInt(maxTopPlaylistItemsCustomValue),
                ).catch { error ->
                    Timber.tag("HomeQuickPicks").w(error, "Could not read top songs")
                    emit(myTopSongsState.value.orEmpty())
                }
            }
            val myTopSongsResult = collectQuickPicksLocalShelf("myTopSongs", manualRevision, myTopSongsState, myTopSongsFlow)
            val myTopSongs = myTopSongsResult.orEmpty()

            val showRewindPlaylists by rememberPreference(showMonthlyPlaylistsKey, true)
            val rewindEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_ENABLED, true)
            val rewindMonthlyEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true)
            val rewindYearlyEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true)
            val showRewind = rewindEnabled && showRewindPlaylists
            val rewindState = persist<List<PlaylistPreview>?>("home/quickpicks/local/rewind")
            val rewindFlow = remember(preview) {
                if (preview != null) kotlinx.coroutines.flow.flowOf(emptyList())
                else Database.playlistTable.sortPreviewsByName()
                    .map { list ->
                        list.filter { item ->
                            !item.playlist.isYoutubePlaylist &&
                                !item.playlist.name.startsWith(PINNED_PREFIX, true) &&
                                RewindPlaylists.isRewind(item.playlist.name)
                        }
                    }
                    .distinctUntilChanged()
                    .catch { error ->
                        Timber.tag("HomeQuickPicks").w(error, "Could not read Rewind playlists")
                        emit(rewindState.value.orEmpty())
                    }
            }
            val rewindResult = collectQuickPicksLocalShelf("rewind", manualRevision, rewindState, rewindFlow)
            val rewindPlaylists = rewindResult.orEmpty().filter { item ->
                RewindPlaylists.visibleInLibrary(item.playlist.name, rewindEnabled, rewindMonthlyEnabled, rewindYearlyEnabled)
            }

            val sectionOrder = rememberQuickPicksSectionOrder()
            val showCharts by rememberPreference(showChartsKey, true)
            val showRelatedAlbums by rememberPreference(showRelatedAlbumsKey, true)
            val showSimilarArtists by rememberPreference(showSimilarArtistsKey, true)
            val showNewAlbumsArtists by rememberPreference(showNewAlbumsArtistsKey, true)
            val showNewAlbums by rememberPreference(showNewAlbumsKey, true)
            val showPlaylistMightLike by rememberPreference(showPlaylistMightLikeKey, true)
            val showMoodsAndGenres by rememberPreference(showMoodsAndGenresKey, true)
            val showMyTop by rememberPreference(showMyTopPlaylistKey, true)
            val showFreshFindsOldFavorites by rememberPreference(showFreshFindsOldFavoritesKey, true)
            val showMixedForYou by rememberPreference(showMixedForYouKey, true)
            val showForgottenFavorites by rememberPreference(showForgottenFavoritesKey, true)
            val showYourDailyDiscover by rememberPreference(showYourDailyDiscoverKey, true)
            val showFreshNewMusic by rememberPreference(showFreshNewMusicKey, true)
            val showNewReleases by rememberPreference(showNewReleasesKey, true)
            val showAlbumsForYou by rememberPreference(showAlbumsForYouKey, true)
            val showTodaysBiggestHits by rememberPreference(showTodaysBiggestHitsKey, true)
            val showAllHits by rememberPreference(showAllHitsKey, true)
            val showFeaturedPlaylists by rememberPreference(showFeaturedPlaylistsKey, true)
            val showTrendingCommunityPlaylists by rememberPreference(showTrendingCommunityPlaylistsKey, true)
            val showFromTheCommunity by rememberPreference(showFromTheCommunityKey, true)
            val showTrendingSongsForYou by rememberPreference(showTrendingSongsForYouKey, true)
            val showTopMusicVideos by rememberPreference(showTopMusicVideosKey, true)
            val showCoverAndRemixes by rememberPreference(showCoverAndRemixesKey, true)
            val showTrendingInShorts by rememberPreference(showTrendingInShortsKey, true)
            val showMusicVideosForYou by rememberPreference(showMusicVideosForYouKey, true)
            val showLivePerformances by rememberPreference(showLivePerformancesKey, true)
            val showMoods by rememberPreference(showMoodsKey, true)
            val showGenericYtmSections by rememberPreference(showGenericYtmSectionsKey, true)

            val hasYtmSection: (String) -> Boolean = { predicate ->
                ytmSections.any { section ->
                    section.title.contains(predicate, ignoreCase = true) && section.items.any { it?.key != null }
                }
            }

            val floatingBackdrop = rememberGlassFloatingActionBackdrop()

            LazyColumn(
                state = scrollState,
                contentPadding = remember(topContentInsetProvider) { TopOverlayContentPadding(topContentInsetProvider) },
                modifier = Modifier
                    .glassFloatingActionContent(floatingBackdrop)
                    .background(colorPalette().background0)
                    .fillMaxHeight()
            ) {
                item(key = "welcome") {
                    Box(Modifier.padding(railPadding)) { WelcomeMessage() }
                }


                if (showTips) {
                    item(key = "quick_picks_header") {
                        QuickPicksHeader(
                            modifier = Modifier.padding(railPadding),
                            playEventType = playEventType,
                            onPlayEventTypeChange = onPlayEventTypeChange,
                            onDiceClick = {
                                scope.launch {
                                    isQuickPicksLoading = true
                                    // Shuffler.play() is fire-and-forget (issue #606 M2): when it's actually
                                    // invoked, isQuickPicksLoading is cleared from its onComplete once work
//
                                    // actually finishes. shufflerWillClear guards the `finally` below so an
                                    // exception thrown before reaching Shuffler.play() still clears the flag.
                                    var shufflerWillClear = false
                                    try {
                                        delay(50)
                                        val relatedInit = state.relatedPageResult.value?.getOrNull()
                                        val allItems = listOfNotNull(state.trending.value?.asMediaItem) + (relatedInit?.songs?.map { it.asMediaItem } ?: emptyList())
                                        val b = binder
                                        if (b != null) {
                                            shufflerWillClear = true
                                            Shuffler.play(b, allItems, onComplete = { isQuickPicksLoading = false })
                                        }
                                    } finally {
                                        if (!shufflerWillClear) isQuickPicksLoading = false
                                    }
                                }
                            },
                            onPlayAllClick = {
                                scope.launch {
                                    isQuickPicksLoading = true
                                    delay(50)
                                    try {
                                        binder?.stopRadio()
                                        state.trending.value?.let { binder?.player?.forcePlay(it.asMediaItem) }
                                        val relatedInit = state.relatedPageResult.value?.getOrNull()
                                        binder?.player?.addMediaItems(relatedInit?.songs?.map { it.asMediaItem } ?: emptyList())
                                    } finally {
                                        isQuickPicksLoading = false
                                    }
                                }
                            },
                            isLoading = isQuickPicksLoading
                        )
                    }

                    item(key = "quick_picks_grid") {
                        QuickPicksGrid(
                            recommendations = state.recommendations.value,
                            trendingList = state.trendingList.value,
                            playEventType = playEventType,
                            itemInHorizontalGridWidth = itemInHorizontalGridWidth,
                            navController = navController,
                            endPaddingValues = endPaddingValues,
                            isLoading = preview == null && (historyPending || onlinePending),
                            initialSlots = initialSlots,
                            forcePlaceholder = forceShimmer,
                            onSongClick = { song ->
                                if (song.isLocal) {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlay(song.asMediaItem)
                                } else {
                                    binder?.startRadio(song, true)
                                }
                            },
                            scrollToStartTrigger = state.refreshKey.value
                        )
                    }
                }

                // Render sections in configured order — offline, only the local
                // sections are kept; the YTM ones are replaced by the offline message.
                sectionOrder.filter { isOnline || it in localSectionIds }.forEach { sectionId ->
                    when (sectionId) {
                        "tips" -> { /* Tips are always shown at top, handled separately */ }
                        "charts" -> {
                            item(key = "charts") {
                                AnimatedVisibility(visible = showCharts, modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    ChartsSection(showCharts, state.chartsPageInit.value, selectedCountryCode, onSelectedCountryCodeChange, navController, onPlaylistClick, onArtistClick, endPaddingValues, playlistThumbnailSizePx, playlistThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, disableScrollingText, parentalControlEnabled, displayedSectionTitles, itemInHorizontalGridWidth, forcePlaceholder = forceShimmer || chartsPending)
                                }
                            }
                        }
                        "related_albums" -> {
                            val hasRelatedAlbums = state.relatedPageResult.value?.getOrNull()?.albums?.isNotEmpty() == true
                            item(key = "related_albums") {
                                AnimatedVisibility(visible = showRelatedAlbums && (hasRelatedAlbums || forceShimmer || relatedPending), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    RelatedAlbumsSection(state.relatedPageResult.value?.getOrNull(), showRelatedAlbums, onAlbumClick, navController, albumThumbnailSizePx, albumThumbnailSizeDp, disableScrollingText, endPaddingValues, sectionTextModifier, displayedSectionTitles, forcePlaceholder = forceShimmer || relatedPending)
                                }
                            }
                        }
                        "similar_artists" -> {
                            val hasSimilarArtists = state.relatedPageResult.value?.getOrNull()?.artists?.isNotEmpty() == true
                            item(key = "similar_artists") {
                                AnimatedVisibility(visible = showSimilarArtists && (hasSimilarArtists || forceShimmer || relatedPending), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    SimilarArtistsSection(state.relatedPageResult.value?.getOrNull(), showSimilarArtists, onArtistClick, navController, artistThumbnailSizePx, artistThumbnailSizeDp, disableScrollingText, endPaddingValues, sectionTextModifier, displayedSectionTitles, forcePlaceholder = forceShimmer || relatedPending)
                                }
                            }
                        }
                        "new_albums_artists" -> {
                            val hasNewAlbumsArtists = newReleaseAlbumsFiltered.isNotEmpty()
                            val newAlbumsArtistsPending = artistsResult == null ||
                                (artists.isNotEmpty() && discoverPending)
                            item(key = "new_albums_artists") {
                                AnimatedVisibility(visible = showNewAlbumsArtists && (hasNewAlbumsArtists || forceShimmer || newAlbumsArtistsPending), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    NewAlbumsOfYourArtistsSection(state.discoverPageInit.value, artists, newReleaseAlbumsFiltered, showNewAlbumsArtists, onAlbumClick, navController, albumThumbnailSizePx, albumThumbnailSizeDp, disableScrollingText, endPaddingValues, sectionTextModifier, showTitle = true, forcePlaceholder = forceShimmer || newAlbumsArtistsPending)
                                }
                            }
                        }
                        "new_albums" -> {
                            val hasNewAlbums = state.discoverPageInit.value?.newReleaseAlbums?.isNotEmpty() == true
                            item(key = "new_albums") {
                                AnimatedVisibility(visible = showNewAlbums && (hasNewAlbums || forceShimmer || discoverPending), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    NewAlbumsSection(state.discoverPageInit.value, showNewAlbums, onAlbumClick, navController, albumThumbnailSizePx, albumThumbnailSizeDp, disableScrollingText, endPaddingValues, displayedSectionTitles, showTitle = true, forcePlaceholder = forceShimmer || discoverPending)
                                }
                            }
                        }
                        "playlists_might_like" -> {
                            item(key = "playlists_might_like") {
                                AnimatedVisibility(visible = showPlaylistMightLike && (forceShimmer || showLoader || hasYtmSection("Playlist you might like")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Playlist you might like", ignoreCase = true) }, stringResource(R.string.recommendations_playlists_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer, emptyPlaceholderKind = YtmShelfPlaceholderKind.Playlist)
                                }
                            }
                        }
                        "moods_genres" -> {
                            val hasMoodsGenres = state.discoverPageInit.value?.moods?.isNotEmpty() == true
                            item(key = "moods_genres") {
                                AnimatedVisibility(visible = showMoodsAndGenres && (hasMoodsGenres || forceShimmer || discoverPending), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    MoodsAndGenresSection(showMoodsAndGenres, state.discoverPageInit.value, onMoodClick, navController, gridsContentPadding, displayedSectionTitles, forcePlaceholder = forceShimmer || discoverPending)
                                }
                            }
                        }
                        "my_top" -> {
                            val hasMyTop = myTopSongs.isNotEmpty()
                            val myTopPending = isOnline && myTopSongsResult == null
                            item(key = "my_top") {
                                AnimatedVisibility(visible = showMyTop && (hasMyTop || forceShimmer || myTopPending), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    MyTopSection(showMyTop, myTopSongs, navController, endPaddingValues, sectionTextModifier, itemInHorizontalGridWidth, forcePlaceholder = forceShimmer || myTopPending)
                                }
                            }
                        }
                        "rewind" -> {
                            // Spec 2: generated rewind-* playlists — hidden when the toggle
                            // is off or no playlist exists yet (auto-hide pattern)
                            val hasRewind = rewindPlaylists.isNotEmpty()
                            val rewindPending = isOnline && rewindResult == null
                            item(key = "rewind") {
                                AnimatedVisibility(visible = showRewind && (hasRewind || forceShimmer || rewindPending), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    RewindSection(showRewind, rewindPlaylists, navController, endPaddingValues, sectionTextModifier, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, forcePlaceholder = forceShimmer || rewindPending)
                                }
                            }
                        }
                        "fresh_finds_old_favorites" -> {
                            item(key = "fresh_finds_old_favorites") {
                                AnimatedVisibility(visible = showFreshFindsOldFavorites && (forceShimmer || showLoader || hasYtmSection("Fresh finds") || hasYtmSection("Old favorites")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Fresh finds", ignoreCase = true) || it.contains("Old favorites", ignoreCase = true) }, stringResource(R.string.recommendations_fresh_finds_old_favorites_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer)
                                }
                            }
                        }
                        "mixed_for_you" -> {
                            item(key = "mixed_for_you") {
                                AnimatedVisibility(visible = showMixedForYou && (forceShimmer || showLoader || hasYtmSection("Mixed for you")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Mixed for you", ignoreCase = true) }, stringResource(R.string.recommendations_mixed_for_you_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer)
                                }
                            }
                        }
                        "forgotten_favorites" -> {
                            item(key = "forgotten_favorites") {
                                AnimatedVisibility(visible = showForgottenFavorites && (forceShimmer || showLoader || hasYtmSection("Forgotten favorites")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Forgotten favorites", ignoreCase = true) }, stringResource(R.string.recommendations_forgotten_favorites_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer)
                                }
                            }
                        }
                        "your_daily_discover" -> {
                            item(key = "your_daily_discover") {
                                AnimatedVisibility(visible = showYourDailyDiscover && (forceShimmer || showLoader || hasYtmSection("Your daily discover")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Your daily discover", ignoreCase = true) }, stringResource(R.string.recommendations_daily_discover_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer)
                                }
                            }
                        }
                        "fresh_new_music" -> {
                            item(key = "fresh_new_music") {
                                AnimatedVisibility(visible = showFreshNewMusic && (forceShimmer || showLoader || hasYtmSection("Fresh new music")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Fresh new music", ignoreCase = true) }, stringResource(R.string.recommendations_fresh_new_music_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer, emptyPlaceholderKind = YtmShelfPlaceholderKind.Playlist)
                                }
                            }
                        }
                        "new_releases" -> {
                            item(key = "new_releases") {
                                AnimatedVisibility(visible = showNewReleases && (forceShimmer || showLoader || hasYtmSection("New release")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("New release", ignoreCase = true) && !it.contains("Fresh new music", ignoreCase = true) }, stringResource(R.string.recommendations_new_releases_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer, emptyMetadataLines = 1)
                                }
                            }
                        }
                        "albums_for_you" -> {
                            item(key = "albums_for_you") {
                                AnimatedVisibility(visible = showAlbumsForYou && (forceShimmer || showLoader || hasYtmSection("Albums for you")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Albums for you", ignoreCase = true) }, stringResource(R.string.recommendations_albums_for_you_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer)
                                }
                            }
                        }
                        "todays_biggest_hits" -> {
                            item(key = "todays_biggest_hits") {
                                AnimatedVisibility(visible = showTodaysBiggestHits && (forceShimmer || showLoader || hasYtmSection("Today's biggest hits")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Today's biggest hits", ignoreCase = true) }, stringResource(R.string.recommendations_todays_biggest_hits_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer)
                                }
                            }
                        }
                        "all_hits" -> {
                            item(key = "all_hits") {
                                AnimatedVisibility(visible = showAllHits && (forceShimmer || showLoader || hasYtmSection("All hits")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("All hits", ignoreCase = true) }, stringResource(R.string.recommendations_all_hits_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer)
                                }
                            }
                        }
                        "featured_playlists" -> {
                            item(key = "featured_playlists") {
                                AnimatedVisibility(visible = showFeaturedPlaylists && (forceShimmer || showLoader || hasYtmSection("Featured playlists")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Featured playlists", ignoreCase = true) }, stringResource(R.string.recommendations_featured_playlists_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer, emptyPlaceholderKind = YtmShelfPlaceholderKind.Playlist)
                                }
                            }
                        }
                        "trending_community_playlists" -> {
                            item(key = "trending_community_playlists") {
                                AnimatedVisibility(visible = showTrendingCommunityPlaylists && (forceShimmer || showLoader || hasYtmSection("Trending community playlists")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Trending community playlists", ignoreCase = true) }, stringResource(R.string.recommendations_trending_community_playlists_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer, emptyPlaceholderKind = YtmShelfPlaceholderKind.Playlist)
                                }
                            }
                        }
                        "from_the_community" -> {
                            item(key = "from_the_community") {
                                AnimatedVisibility(visible = showFromTheCommunity && (forceShimmer || showLoader || hasYtmSection("From the community")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("From the community", ignoreCase = true) }, stringResource(R.string.recommendations_community_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer, emptyPlaceholderKind = YtmShelfPlaceholderKind.Playlist)
                                }
                            }
                        }
                        "trending_songs_for_you" -> {
                            item(key = "trending_songs_for_you") {
                                AnimatedVisibility(visible = showTrendingSongsForYou && (forceShimmer || showLoader || hasYtmSection("Trending songs for you")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Trending songs for you", ignoreCase = true) }, stringResource(R.string.recommendations_trending_songs_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer, emptyPlaceholderKind = YtmShelfPlaceholderKind.Song)
                                }
                            }
                        }
                        "top_music_videos" -> {
                            item(key = "top_music_videos") {
                                AnimatedVisibility(visible = showTopMusicVideos && (forceShimmer || showLoader || hasYtmSection("Top music videos")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Top music videos", ignoreCase = true) }, stringResource(R.string.recommendations_top_music_videos_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer, emptyPlaceholderKind = YtmShelfPlaceholderKind.Video)
                                }
                            }
                        }
                        "cover_and_remixes" -> {
                            item(key = "cover_and_remixes") {
                                AnimatedVisibility(visible = showCoverAndRemixes && (forceShimmer || showLoader || hasYtmSection("Cover") || hasYtmSection("remix")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Cover", ignoreCase = true) || it.contains("remix", ignoreCase = true) }, stringResource(R.string.recommendations_covers_and_remixes_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer)
                                }
                            }
                        }
                        "trending_in_shorts" -> {
                            item(key = "trending_in_shorts") {
                                AnimatedVisibility(visible = showTrendingInShorts && (forceShimmer || showLoader || hasYtmSection("Trending in Shorts")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Trending in Shorts", ignoreCase = true) }, stringResource(R.string.recommendations_trending_shorts_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer, emptyPlaceholderKind = YtmShelfPlaceholderKind.Video)
                                }
                            }
                        }
                        "music_videos_for_you" -> {
                            item(key = "music_videos_for_you") {
                                AnimatedVisibility(visible = showMusicVideosForYou && (forceShimmer || showLoader || hasYtmSection("Music videos for you")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Music videos for you", ignoreCase = true) }, stringResource(R.string.recommendations_music_videos_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer, emptyPlaceholderKind = YtmShelfPlaceholderKind.Video)
                                }
                            }
                        }
                        "live_performances" -> {
                            item(key = "live_performances") {
                                AnimatedVisibility(visible = showLivePerformances && (forceShimmer || showLoader || hasYtmSection("Live performances")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Live performances", ignoreCase = true) }, stringResource(R.string.recommendations_live_performances_title), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer, emptyPlaceholderKind = YtmShelfPlaceholderKind.Video)
                                }
                            }
                        }
                        "moods" -> {
                            val hasMoods = state.homePageInit.value?.chips?.isNotEmpty() == true
                            item(key = "moods") {
                                AnimatedVisibility(visible = showMoods && (hasMoods || forceShimmer || showLoader), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    MoodsSection(state.homePageInit.value, onChipClick, gridsContentPadding, displayedSectionTitles, forcePlaceholder = forceShimmer || showLoader)
                                }
                            }
                        }
                        "generic_ytm_sections" -> {
                            // Handled after the loop
                        }
                    }
                }

                if (isOnline && showGenericYtmSections) {
                    val genericHomePage = state.homePageInit.value
                    if (genericHomePage != null && genericHomePage.sections.isNotEmpty()) {
                        genericYtmSectionItems(genericHomePage) { sectionPage ->
                            GenericYtmSections(sectionPage, displayedSectionTitles, itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer)
                        }
                    } else {
                        item(key = "generic_ytm_sections", contentType = "generic_ytm_placeholder") {
                            GenericYtmSections(genericHomePage, displayedSectionTitles, itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, isLoading = showLoader, showTitle = true, forcePlaceholder = forceShimmer)
                        }
                    }
                }
                
                if (!isOnline) {
                    item(key = "offline_content") {
                        Spacer(modifier = Modifier.height(50.dp))
                        OfflineContent(modifier = Modifier.fillMaxWidth())
                    }
                } else if (state.relatedPageResult.value?.exceptionOrNull() != null ||
                    (state.homePageInit.value == null && state.homePageResult.value?.isFailure == true)
                ) {
                    item(key = "error_related") {
                        Spacer(modifier = Modifier.height(50.dp))
                        BasicText(text = stringResource(R.string.recommendations_load_failed_message), style = typography().s.secondary.center, modifier = Modifier.fillMaxWidth().padding(all = 16.dp).clickable(onClick = refresh))
                    }
                } else {
                    if (!currentYouTubeLoggedIn) {
                        item(key = "login_prompt") {
                            Spacer(modifier = Modifier.height(50.dp))
                            BasicText(text = stringResource(R.string.home_youtube_login_description), style = typography().s.secondary.center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onSettingsClick))
                        }
                    } else if (MainApplication.cookieStatus in listOf(MainApplication.CookieStatus.INVALID, MainApplication.CookieStatus.EXPIRED)) {
                        item(key = "cookie_error") {
                            Spacer(modifier = Modifier.height(50.dp))
                            BasicText(text = stringResource(R.string.account_youtube_cookie_invalid), style = typography().s.secondary.center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onSettingsClick))
                        }
                    }
                }
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }
            }

            GlassFloatingActions(floatingBackdrop) {
                val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
                if (showFloatingIcon)
                    Box(modifier = Modifier.fillMaxSize()) {
                        MultiFloatingActionsContainer(iconId = R.drawable.search, onClick = onSearchClick, onClickSettings = onSettingsClick, onClickSearch = onSearchClick)
                    }
            }
        }
    }

// Sections fed by local data (Room) — the only ones rendered while offline.
private val localSectionIds = setOf("my_top", "rewind")

private val defaultQuickPicksSectionOrder = listOf(
    "tips",
    "fresh_finds_old_favorites",
    "mixed_for_you",
    "forgotten_favorites",
    "your_daily_discover",
    "fresh_new_music",
    "new_releases",
    "new_albums_artists",
    "new_albums",
    "albums_for_you",
    "related_albums",
    "my_top",
    "rewind",
    "similar_artists",
    "todays_biggest_hits",
    "all_hits",
    "playlists_might_like",
    "featured_playlists",
    "trending_community_playlists",
    "from_the_community",
    "trending_songs_for_you",
    "top_music_videos",
    "cover_and_remixes",
    "trending_in_shorts",
    "music_videos_for_you",
    "live_performances",
    "moods",
    "moods_genres",
    "generic_ytm_sections",
    "charts"
)

@Composable
private fun rememberQuickPicksSectionOrder(): List<String> {
    val context = LocalContext.current
    return remember {
        val prefs = context.preferences
        val orderSerialized = prefs.getString(quickPicksSectionOrderKey, "") ?: ""
        if (orderSerialized.isBlank()) {
            defaultQuickPicksSectionOrder
        } else {
            try {
                val arr = JSONArray(orderSerialized)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                val validIds = defaultQuickPicksSectionOrder
                val result = list.filter { it in validIds }.toMutableList()
                for (id in validIds) {
                    if (id !in result) result.add(id)
                }
                result
            } catch (_: Exception) {
                defaultQuickPicksSectionOrder
            }
        }
    }
}
