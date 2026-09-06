@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.components.ui.screens.home.quickpicks

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.it.fast4x.compose.persist.persistList
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.n_zik.android.MainApplication
import app.it.fast4x.rimusic.MONTHLY_PREFIX
import app.it.fast4x.rimusic.enums.*
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.*
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.core.database.Database
import app.n_zik.android.typography
import app.n_zik.android.playback.utils.Shuffler
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.queue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.random.Random
import app.n_zik.android.components.AppPullToRefreshBox
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
    onSettingsClick: () -> Unit
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

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
        localCount = localCount
    )

    var lastPlayEventType by rememberSaveable { mutableStateOf(playEventType) }
    var lastSelectedCountry by rememberSaveable { mutableStateOf(selectedCountryCode) }
    var currentYouTubeLoggedIn by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        currentYouTubeLoggedIn = withContext(Dispatchers.IO) { isYouTubeLoggedIn() }
    }

    var lastYouTubeLoggedIn by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(playEventType, selectedCountryCode) {
        if (playEventType != lastPlayEventType || selectedCountryCode != lastSelectedCountry) {
            state.loadedQuickPicks.value = false
            state.loadedData.value = false
            state.relatedPageResult.value = null
            state.trending.value = null
            state.trendingList.value = emptyList()
            delay(100)
            lastPlayEventType = playEventType
            lastSelectedCountry = selectedCountryCode
        }
        state.loadData()
    }

    LaunchedEffect(Unit) {
        val loggedIn = withContext(Dispatchers.IO) { isYouTubeLoggedIn() }
        if (loggedIn != lastYouTubeLoggedIn) {
            lastYouTubeLoggedIn = loggedIn
            currentYouTubeLoggedIn = loggedIn
            state.homePageResult.value = null
            state.homePageInit.value = null
            state.discoverPageResult.value = null
            state.discoverPageInit.value = null
            state.chartsPageResult.value = null
            state.chartsPageInit.value = null
            state.ytmQuickPicks.value = emptyList()
            state.loadedQuickPicks.value = false
            state.loadedData.value = false
            state.loadData()
            Timber.tag("HomeQuickPicks").d("YouTube login state changed. Data cleared.")
        }
    }

    LaunchedEffect(state.loadedData.value) {
        if (state.loadedData.value) {
            val itemsToFetch = state.homePageInit.value?.sections?.flatMap { it.items }
                ?.filterIsInstance<Innertube.VideoItem>()
                ?.filter { it.durationText == null }
                ?.map { it.key }
                ?.distinct()
                ?: emptyList()
            if (itemsToFetch.isNotEmpty()) {
                Innertube.queue(videoIds = itemsToFetch)?.onSuccess { queueItems ->
                    val durationsMap = queueItems?.associate { it.key to it.durationText }.orEmpty()
                    if (durationsMap.isNotEmpty()) {
                        state.homePageInit.value = state.homePageInit.value?.copy(
                            sections = state.homePageInit.value?.sections?.map { section ->
                                section.copy(
                                    items = section.items.map { item ->
                                        when (item) {
                                            is Innertube.VideoItem -> {
                                                val duration = durationsMap[item.key]
                                                if (duration != null) item.copy(durationText = duration)
                                                else item
                                            }
                                            is Innertube.SongItem -> {
                                                val duration = durationsMap[item.key]
                                                if (duration != null) item.copy(durationText = duration)
                                                else item
                                            }
                                            else -> item
                                        }
                                    }
                                )
                            } ?: emptyList()
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(state.trendingList.value, state.relatedPageResult.value, localCount, playEventType, state.ytmQuickPicks.value) {
        val relatedInit = state.relatedPageResult.value?.getOrNull()
        val mainIds = state.trendingList.value.map { it.id }.toSet()
        val seed = (state.trendingList.value.joinToString { it.id } + (relatedInit?.songs?.joinToString { it.key } ?: "")).hashCode()
        val random = Random(seed)

        val relatedSongsSource = relatedInit?.songs
            ?.map { it.asSong }
            ?.filter { !parentalControlEnabled || !it.title.startsWith(EXPLICIT_PREFIX, true) }
            ?.distinctBy { it.id }
            .orEmpty()

        val candidateList = if (playEventType == PlayEventsType.MostPlayed || playEventType == PlayEventsType.LastPlayed) {
            val first = state.trendingList.value.firstOrNull()
            val others = state.trendingList.value.drop(1)
            val pool = (others + state.ytmQuickPicks.value + relatedSongsSource).distinctBy { it.id }
            (listOfNotNull(first) + pool.shuffled(random))
        } else {
            val locals = state.trendingList.value.take(localCount)
            val pool = (locals + state.ytmQuickPicks.value + relatedSongsSource).distinctBy { it.id }
            pool.shuffled(random)
        }

        val finalLocalCount = candidateList.count { it.id in mainIds }
        val finalYtmQuickPicksCount = candidateList.count { song -> state.ytmQuickPicks.value.any { it.id == song.id } && song.id !in mainIds }
        val finalRelatedCount = candidateList.size - finalLocalCount - finalYtmQuickPicksCount

        Timber.tag("HomeQuickPicks").d("Assembling Quick Picks -> Local: $finalLocalCount, YTM Related: $finalRelatedCount, YouTube QuickPicks: $finalYtmQuickPicksCount (Total: ${candidateList.size})")

        val oldIds = state.recommendations.value.map { it.id }.toSet()
        val newIds = candidateList.map { it.id }.toSet()

        if (state.recommendations.value.isEmpty() || oldIds != newIds) {
            state.recommendations.value = candidateList
        }
    }

    val scrollState = rememberLazyListState()

    LaunchedEffect(state.refreshKey.value) {
        if (state.refreshKey.value > 0) {
            scrollState.animateScrollToItem(0)
        }
    }

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()
    val gridsContentPadding = PaddingValues(start = 12.dp, end = endPaddingValues.calculateEndPadding(LocalLayoutDirection.current))
    val sectionTextModifier = Modifier.padding(horizontal = 12.dp).padding(top = 16.dp, bottom = 8.dp).padding(endPaddingValues)
    val showSearchTab by rememberPreference(showSearchTabKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val scope = rememberCoroutineScope()
    var isQuickPicksLoading by remember { mutableStateOf(false) }

    var showLoader by remember { mutableStateOf(!state.loadedData.value) }
    LaunchedEffect(state.loadedData.value) {
        if (state.loadedData.value) {
            delay(600)
            showLoader = false
        } else {
            showLoader = true
        }
    }

    AppPullToRefreshBox(
        isRefreshing = state.refreshing.value,
        onRefresh = { state.refresh() }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val itemInHorizontalGridWidth = maxWidth * (if (isLandscape && maxWidth * 0.475f >= 320.dp) 0.375f else 0.7f)
            val albumThumbnailSizeDp = 108.dp
            val albumThumbnailSizePx = albumThumbnailSizeDp.px
            val artistThumbnailSizeDp = 92.dp
            val artistThumbnailSizePx = artistThumbnailSizeDp.px
            val playlistThumbnailSizeDp = 108.dp
            val playlistThumbnailSizePx = playlistThumbnailSizeDp.px
            val songThumbnailSizeDp = Dimensions.thumbnails.song
            val songThumbnailSizePx = songThumbnailSizeDp.px

            val showTips by rememberPreference(showTipsKey, true)
            val displayedSectionTitles = remember { mutableSetOf<String>() }
            val ytmSections = state.homePageInit.value?.sections.orEmpty()

            var stableHomePageInit by remember { mutableStateOf(state.homePageInit.value) }
            if (state.homePageInit.value != null) {
                stableHomePageInit = state.homePageInit.value
            }

            val artistsState = persistList<Artist>("home/quickpicks/local/artists")
            val artists by remember { Database.artistTable.sortFollowingByName().distinctUntilChanged() }.collectAsStateWithLifecycle(artistsState.value, context = Dispatchers.IO)
            LaunchedEffect(artists) { artistsState.value = artists }

            val newReleaseAlbumsFiltered = remember(state.discoverPageInit.value, artists) {
                state.discoverPageInit.value?.newReleaseAlbums?.filter { album ->
                    artists.any { it.name == album.authors?.firstOrNull()?.name }
                }.orEmpty()
            }

            val monthlyPlaylistsState = persistList<PlaylistPreview>("home/quickpicks/local/monthlyPlaylists")
            val monthlyPlaylists by remember {
                Database.playlistTable.allAsPreview().distinctUntilChanged().map { list -> list.filter { it.playlist.name.startsWith(MONTHLY_PREFIX, true) } }
            }.collectAsStateWithLifecycle(monthlyPlaylistsState.value, context = Dispatchers.IO)
            LaunchedEffect(monthlyPlaylists) { monthlyPlaylistsState.value = monthlyPlaylists }

            val maxTopPlaylistItems by rememberPreference(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`)
            val myTopSongsState = persistList<Song>("home/quickpicks/local/myTopSongs")
            val myTopSongs by remember { Database.eventTable.findSongsMostPlayedBetween(from = 0L, limit = maxTopPlaylistItems.toInt()) }.collectAsStateWithLifecycle(myTopSongsState.value, context = Dispatchers.IO)
            LaunchedEffect(myTopSongs) { myTopSongsState.value = myTopSongs }

            val sectionOrder = rememberQuickPicksSectionOrder()
            val showCharts by rememberPreference(showChartsKey, true)
            val showRelatedAlbums by rememberPreference(showRelatedAlbumsKey, true)
            val showSimilarArtists by rememberPreference(showSimilarArtistsKey, true)
            val showNewAlbumsArtists by rememberPreference(showNewAlbumsArtistsKey, true)
            val showNewAlbums by rememberPreference(showNewAlbumsKey, true)
            val showPlaylistMightLike by rememberPreference(showPlaylistMightLikeKey, true)
            val showMoodsAndGenres by rememberPreference(showMoodsAndGenresKey, true)
            val showMonthlyPlaylists by rememberPreference(showMonthlyPlaylistInQuickPicksKey, true)
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
            val isViMusicUi = UiType.ViMusic.isCurrent()

            val hasYtmSection: (String) -> Boolean = { predicate ->
                ytmSections.any { section ->
                    section.title.contains(predicate, ignoreCase = true) && section.items.any { it?.key != null }
                }
            }

            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxHeight()
            ) {
                item(key = "welcome") {
                    WelcomeMessage()
                }

                if (!state.loadedQuickPicks.value) {
                    item(key = "loading") {
                        Box(modifier = Modifier.fillMaxWidth().height(Dimensions.itemsVerticalPadding * 3 * 9), contentAlignment = Alignment.Center) {
                            LoadingIndicator(color = colorPalette().accent, modifier = Modifier.fillMaxHeight(0.5f).aspectRatio(1f))
                        }
                    }
                } else {
                    if (isViMusicUi) {
                        item(key = "header") {
                            HeaderWithIcon(
                                title = if (!currentYouTubeLoggedIn) stringResource(R.string.quick_picks) else stringResource(R.string.home),
                                iconId = R.drawable.search,
                                enabled = true,
                                showIcon = !showSearchTab,
                                modifier = Modifier,
                                onClick = onSearchClick
                            )
                        }
                    }

                    if (showTips) {
                        item(key = "quick_picks_header") {
                            QuickPicksHeader(
                                playEventType = playEventType,
                                onPlayEventTypeChange = { playEventType = it },
                                onDiceClick = {
                                    scope.launch {
                                        isQuickPicksLoading = true
                                        delay(50)
                                        try {
                                            val relatedInit = state.relatedPageResult.value?.getOrNull()
                                            val allItems = listOfNotNull(state.trending.value?.asMediaItem) + (relatedInit?.songs?.map { it.asMediaItem } ?: emptyList())
                                            binder?.let { Shuffler.play(it, allItems) }
                                        } finally {
                                            isQuickPicksLoading = false
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
                                onSongClick = { binder?.startRadio(it, true) },
                                scrollToStartTrigger = state.refreshKey.value
                            )
                        }
                    }
                }

                // Render sections in configured order
                sectionOrder.forEach { sectionId ->
                    when (sectionId) {
                        "tips" -> { /* Tips are always shown at top, handled separately */ }
                        "charts" -> {
                            val hasChartsContent = state.chartsPageInit.value?.let { it.playlists?.isNotEmpty() == true || it.songs?.isNotEmpty() == true || it.artists?.isNotEmpty() == true } ?: false
                            item(key = "charts") {
                                AnimatedVisibility(visible = showCharts && hasChartsContent, modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    ChartsSection(showCharts, state.chartsPageInit.value, selectedCountryCode, { selectedCountryCode = it }, navController, onPlaylistClick, onArtistClick, endPaddingValues, playlistThumbnailSizePx, playlistThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, disableScrollingText, parentalControlEnabled, displayedSectionTitles, itemInHorizontalGridWidth)
                                }
                            }
                        }
                        "related_albums" -> {
                            val hasRelatedAlbums = state.relatedPageResult.value?.getOrNull()?.albums?.isNotEmpty() == true
                            item(key = "related_albums") {
                                AnimatedVisibility(visible = showRelatedAlbums && hasRelatedAlbums, modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    RelatedAlbumsSection(state.relatedPageResult.value?.getOrNull(), showRelatedAlbums, onAlbumClick, navController, albumThumbnailSizePx, albumThumbnailSizeDp, disableScrollingText, endPaddingValues, sectionTextModifier, displayedSectionTitles)
                                }
                            }
                        }
                        "similar_artists" -> {
                            val hasSimilarArtists = state.relatedPageResult.value?.getOrNull()?.artists?.isNotEmpty() == true
                            item(key = "similar_artists") {
                                AnimatedVisibility(visible = showSimilarArtists && hasSimilarArtists, modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    SimilarArtistsSection(state.relatedPageResult.value?.getOrNull(), showSimilarArtists, onArtistClick, navController, artistThumbnailSizePx, artistThumbnailSizeDp, disableScrollingText, endPaddingValues, sectionTextModifier, displayedSectionTitles)
                                }
                            }
                        }
                        "new_albums_artists" -> {
                            val hasNewAlbumsArtists = newReleaseAlbumsFiltered.isNotEmpty() && artists.isNotEmpty()
                            item(key = "new_albums_artists") {
                                AnimatedVisibility(visible = showNewAlbumsArtists && hasNewAlbumsArtists, modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    NewAlbumsOfYourArtistsSection(state.discoverPageInit.value, artists, newReleaseAlbumsFiltered, showNewAlbumsArtists, onAlbumClick, navController, albumThumbnailSizePx, albumThumbnailSizeDp, disableScrollingText, endPaddingValues, sectionTextModifier, showTitle = true)
                                }
                            }
                        }
                        "new_albums" -> {
                            val hasNewAlbums = state.discoverPageInit.value?.newReleaseAlbums?.isNotEmpty() == true
                            item(key = "new_albums") {
                                AnimatedVisibility(visible = showNewAlbums && hasNewAlbums, modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    NewAlbumsSection(state.discoverPageInit.value, showNewAlbums, onAlbumClick, navController, albumThumbnailSizePx, albumThumbnailSizeDp, disableScrollingText, endPaddingValues, displayedSectionTitles, showTitle = true)
                                }
                            }
                        }
                        "playlists_might_like" -> {
                            item(key = "playlists_might_like") {
                                AnimatedVisibility(visible = showPlaylistMightLike && (showLoader || hasYtmSection("Playlist you might like")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Playlist you might like", ignoreCase = true) }, stringResource(R.string.playlists_you_might_like), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "moods_genres" -> {
                            val hasMoodsGenres = state.discoverPageInit.value?.moods?.isNotEmpty() == true
                            item(key = "moods_genres") {
                                AnimatedVisibility(visible = showMoodsAndGenres && hasMoodsGenres, modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    MoodsAndGenresSection(showMoodsAndGenres, state.discoverPageInit.value, onMoodClick, navController, gridsContentPadding, displayedSectionTitles)
                                }
                            }
                        }
                        "monthly_playlists" -> {
                            val hasMonthlyPlaylists = monthlyPlaylists.isNotEmpty()
                            item(key = "monthly_playlists") {
                                AnimatedVisibility(visible = showMonthlyPlaylists && hasMonthlyPlaylists, modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    MonthlyPlaylistsSection(showMonthlyPlaylists, monthlyPlaylists, navController, endPaddingValues, playlistThumbnailSizeDp, playlistThumbnailSizePx, disableScrollingText)
                                }
                            }
                        }
                        "my_top" -> {
                            val hasMyTop = myTopSongs.isNotEmpty()
                            item(key = "my_top") {
                                AnimatedVisibility(visible = showMyTop && hasMyTop, modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    MyTopSection(showMyTop, myTopSongs, navController, endPaddingValues, sectionTextModifier, itemInHorizontalGridWidth)
                                }
                            }
                        }
                        "fresh_finds_old_favorites" -> {
                            item(key = "fresh_finds_old_favorites") {
                                AnimatedVisibility(visible = showFreshFindsOldFavorites && (showLoader || hasYtmSection("Fresh finds") || hasYtmSection("Old favorites")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Fresh finds", ignoreCase = true) || it.contains("Old favorites", ignoreCase = true) }, stringResource(R.string.fresh_finds_old_favorites), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "mixed_for_you" -> {
                            item(key = "mixed_for_you") {
                                AnimatedVisibility(visible = showMixedForYou && (showLoader || hasYtmSection("Mixed for you")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Mixed for you", ignoreCase = true) }, stringResource(R.string.mixed_for_you), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "forgotten_favorites" -> {
                            item(key = "forgotten_favorites") {
                                AnimatedVisibility(visible = showForgottenFavorites && (showLoader || hasYtmSection("Forgotten favorites")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Forgotten favorites", ignoreCase = true) }, stringResource(R.string.forgotten_favorites), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "your_daily_discover" -> {
                            item(key = "your_daily_discover") {
                                AnimatedVisibility(visible = showYourDailyDiscover && (showLoader || hasYtmSection("Your daily discover")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Your daily discover", ignoreCase = true) }, stringResource(R.string.your_daily_discover), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "fresh_new_music" -> {
                            item(key = "fresh_new_music") {
                                AnimatedVisibility(visible = showFreshNewMusic && (showLoader || hasYtmSection("Fresh new music")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Fresh new music", ignoreCase = true) }, stringResource(R.string.fresh_new_music), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "new_releases" -> {
                            item(key = "new_releases") {
                                AnimatedVisibility(visible = showNewReleases && (showLoader || hasYtmSection("New release")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("New release", ignoreCase = true) && !it.contains("Fresh new music", ignoreCase = true) }, stringResource(R.string.new_releases), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "albums_for_you" -> {
                            item(key = "albums_for_you") {
                                AnimatedVisibility(visible = showAlbumsForYou && (showLoader || hasYtmSection("Albums for you")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Albums for you", ignoreCase = true) }, stringResource(R.string.albums_for_you), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "todays_biggest_hits" -> {
                            item(key = "todays_biggest_hits") {
                                AnimatedVisibility(visible = showTodaysBiggestHits && (showLoader || hasYtmSection("Today's biggest hits")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Today's biggest hits", ignoreCase = true) }, stringResource(R.string.todays_biggest_hits), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "all_hits" -> {
                            item(key = "all_hits") {
                                AnimatedVisibility(visible = showAllHits && (showLoader || hasYtmSection("All hits")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("All hits", ignoreCase = true) }, stringResource(R.string.all_hits), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "featured_playlists" -> {
                            item(key = "featured_playlists") {
                                AnimatedVisibility(visible = showFeaturedPlaylists && (showLoader || hasYtmSection("Featured playlists")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Featured playlists", ignoreCase = true) }, stringResource(R.string.featured_playlists_for_you), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "trending_community_playlists" -> {
                            item(key = "trending_community_playlists") {
                                AnimatedVisibility(visible = showTrendingCommunityPlaylists && (showLoader || hasYtmSection("Trending community playlists")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Trending community playlists", ignoreCase = true) }, stringResource(R.string.trending_community_playlists), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "from_the_community" -> {
                            item(key = "from_the_community") {
                                AnimatedVisibility(visible = showFromTheCommunity && (showLoader || hasYtmSection("From the community")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("From the community", ignoreCase = true) }, stringResource(R.string.from_the_community), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "trending_songs_for_you" -> {
                            item(key = "trending_songs_for_you") {
                                AnimatedVisibility(visible = showTrendingSongsForYou && (showLoader || hasYtmSection("Trending songs for you")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Trending songs for you", ignoreCase = true) }, stringResource(R.string.trending_songs_for_you), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "top_music_videos" -> {
                            item(key = "top_music_videos") {
                                AnimatedVisibility(visible = showTopMusicVideos && (showLoader || hasYtmSection("Top music videos")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Top music videos", ignoreCase = true) }, stringResource(R.string.top_music_videos), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "cover_and_remixes" -> {
                            item(key = "cover_and_remixes") {
                                AnimatedVisibility(visible = showCoverAndRemixes && (showLoader || hasYtmSection("Cover") || hasYtmSection("remix")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Cover", ignoreCase = true) || it.contains("remix", ignoreCase = true) }, stringResource(R.string.cover_and_remixes), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "trending_in_shorts" -> {
                            item(key = "trending_in_shorts") {
                                AnimatedVisibility(visible = showTrendingInShorts && (showLoader || hasYtmSection("Trending in Shorts")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Trending in Shorts", ignoreCase = true) }, stringResource(R.string.trending_in_shorts), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "music_videos_for_you" -> {
                            item(key = "music_videos_for_you") {
                                AnimatedVisibility(visible = showMusicVideosForYou && (showLoader || hasYtmSection("Music videos for you")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Music videos for you", ignoreCase = true) }, stringResource(R.string.music_videos_for_you), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "live_performances" -> {
                            item(key = "live_performances") {
                                AnimatedVisibility(visible = showLivePerformances && (showLoader || hasYtmSection("Live performances")), modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    YtmSectionByTitle(ytmSections, { it.contains("Live performances", ignoreCase = true) }, stringResource(R.string.live_performances), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader, showTitle = true)
                                }
                            }
                        }
                        "moods" -> {
                            val hasMoods = state.homePageInit.value?.chips?.isNotEmpty() == true
                            item(key = "moods") {
                                AnimatedVisibility(visible = showMoods && hasMoods, modifier = Modifier.animateItem(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    MoodsSection(state.homePageInit.value, onChipClick, gridsContentPadding, displayedSectionTitles)
                                }
                            }
                        }
                        "generic_ytm_sections" -> {
                            // Handled after the loop
                        }
                    }
                }

                if (showGenericYtmSections) {
                    item(key = "generic_ytm_sections") {
                        GenericYtmSections(stableHomePageInit, displayedSectionTitles, itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, isLoading = showLoader, showTitle = true)
                    }
                }
                
                // HomeBottomShimmer removed as shimmers are now inline in their respective positions

                if (state.relatedPageResult.value?.exceptionOrNull() != null) {
                    item(key = "error_related") {
                        Spacer(modifier = Modifier.height(50.dp))
                        BasicText(text = stringResource(R.string.page_not_been_loaded), style = typography().s.secondary.center, modifier = Modifier.fillMaxWidth().padding(all = 16.dp))
                    }
                } else {
                    if (!currentYouTubeLoggedIn) {
                        item(key = "login_prompt") {
                            Spacer(modifier = Modifier.height(50.dp))
                            BasicText(text = stringResource(R.string.log_in_to_ytm), style = typography().s.secondary.center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onSettingsClick))
                        }
                    } else if (MainApplication.cookieStatus in listOf(MainApplication.CookieStatus.INVALID, MainApplication.CookieStatus.EXPIRED)) {
                        item(key = "cookie_error") {
                            Spacer(modifier = Modifier.height(50.dp))
                            BasicText(text = stringResource(R.string.error_cookie_invalid), style = typography().s.secondary.center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onSettingsClick))
                        }
                    }
                }
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }
            }

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if (showFloatingIcon)
                Box(modifier = Modifier.fillMaxSize()) {
                    MultiFloatingActionsContainer(iconId = R.drawable.search, onClick = onSearchClick, onClickSettings = onSettingsClick, onClickSearch = onSearchClick)
                }
        }
    }
}

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
    "monthly_playlists",
    "my_top",
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
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
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
