@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.components.ui.screens.home.quickpicks

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import it.fast4x.innertube.models.bodies.QueueBody
import it.fast4x.innertube.requests.queue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    var lastYouTubeLoggedIn by rememberSaveable { mutableStateOf(isYouTubeLoggedIn()) }

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

    val currentYouTubeLoggedIn = isYouTubeLoggedIn()
    LaunchedEffect(currentYouTubeLoggedIn) {
        if (currentYouTubeLoggedIn != lastYouTubeLoggedIn) {
            lastYouTubeLoggedIn = currentYouTubeLoggedIn
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
                Innertube.queue(QueueBody(videoIds = itemsToFetch))?.onSuccess { queueItems ->
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

    val scrollState = rememberScrollState()
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

            Column(
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
            ) {
                WelcomeMessage()

                val showTips by rememberPreference(showTipsKey, true)

                if (!state.loadedQuickPicks.value) {
                    Box(modifier = Modifier.fillMaxWidth().height(Dimensions.itemsVerticalPadding * 3 * 9), contentAlignment = Alignment.Center) {
                        LoadingIndicator(color = colorPalette().accent, modifier = Modifier.fillMaxHeight(0.5f).aspectRatio(1f))
                    }
                } else {
                    if (UiType.ViMusic.isCurrent())
                        HeaderWithIcon(
                            title = if (!isYouTubeLoggedIn()) stringResource(R.string.quick_picks) else stringResource(R.string.home),
                            iconId = R.drawable.search,
                            enabled = true,
                            showIcon = !showSearchTab,
                            modifier = Modifier,
                            onClick = onSearchClick
                        )

                    if (showTips) {
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

                        QuickPicksGrid(
                            recommendations = state.recommendations.value,
                            trendingList = state.trendingList.value,
                            playEventType = playEventType,
                            itemInHorizontalGridWidth = itemInHorizontalGridWidth,
                            navController = navController,
                            endPaddingValues = endPaddingValues,
                            onSongClick = { binder?.startRadio(it, true) }
                        )
                    }
                }

                val displayedSectionTitles = remember { mutableSetOf<String>() }
                val ytmSections = state.homePageInit.value?.sections.orEmpty()

                val artistsState = persistList<Artist>("home/quickpicks/local/artists")
                val artists by remember { Database.artistTable.sortFollowingByName().distinctUntilChanged() }.collectAsState(artistsState.value, Dispatchers.IO)
                LaunchedEffect(artists) { artistsState.value = artists }

                val newReleaseAlbumsFiltered = remember(state.discoverPageInit.value, artists) {
                    state.discoverPageInit.value?.newReleaseAlbums?.filter { album ->
                        artists.any { it.name == album.authors?.firstOrNull()?.name }
                    }.orEmpty()
                }

                val monthlyPlaylistsState = persistList<PlaylistPreview>("home/quickpicks/local/monthlyPlaylists")
                val monthlyPlaylists by remember {
                    Database.playlistTable.allAsPreview().distinctUntilChanged().map { list -> list.filter { it.playlist.name.startsWith(MONTHLY_PREFIX, true) } }
                }.collectAsState(monthlyPlaylistsState.value, Dispatchers.IO)
                LaunchedEffect(monthlyPlaylists) { monthlyPlaylistsState.value = monthlyPlaylists }

                val maxTopPlaylistItems by rememberPreference(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`)
                val myTopSongsState = persistList<Song>("home/quickpicks/local/myTopSongs")
                val myTopSongs by remember { Database.eventTable.findSongsMostPlayedBetween(from = 0L, limit = maxTopPlaylistItems.toInt()) }.collectAsState(myTopSongsState.value, Dispatchers.IO)
                LaunchedEffect(myTopSongs) { myTopSongsState.value = myTopSongs }

                // Read section order and toggles from preferences
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

                // Render sections in configured order
                sectionOrder.forEach { sectionId ->
                    when (sectionId) {
                        "tips" -> { /* Tips are always shown at top, handled separately */ }
                        "charts" -> {
                            if (showCharts) {
                                ChartsSection(showCharts, state.chartsPageInit.value, selectedCountryCode, { selectedCountryCode = it }, navController, onPlaylistClick, onArtistClick, endPaddingValues, playlistThumbnailSizePx, playlistThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, disableScrollingText, parentalControlEnabled, displayedSectionTitles, itemInHorizontalGridWidth)
                            }
                        }
                        "related_albums" -> {
                            if (showRelatedAlbums) {
                                RelatedAlbumsSection(state.relatedPageResult.value?.getOrNull(), showRelatedAlbums, onAlbumClick, navController, albumThumbnailSizePx, albumThumbnailSizeDp, disableScrollingText, endPaddingValues, sectionTextModifier, displayedSectionTitles)
                            }
                        }
                        "similar_artists" -> {
                            if (showSimilarArtists) {
                                SimilarArtistsSection(state.relatedPageResult.value?.getOrNull(), showSimilarArtists, onArtistClick, navController, artistThumbnailSizePx, artistThumbnailSizeDp, disableScrollingText, endPaddingValues, sectionTextModifier, displayedSectionTitles)
                            }
                        }
                        "new_albums_artists" -> {
                            if (showNewAlbumsArtists) {
                                NewAlbumsOfYourArtistsSection(state.discoverPageInit.value, artists, newReleaseAlbumsFiltered, showNewAlbumsArtists, onAlbumClick, navController, albumThumbnailSizePx, albumThumbnailSizeDp, disableScrollingText, endPaddingValues, sectionTextModifier)
                            }
                        }
                        "new_albums" -> {
                            if (showNewAlbums) {
                                NewAlbumsSection(state.discoverPageInit.value, showNewAlbums, onAlbumClick, navController, albumThumbnailSizePx, albumThumbnailSizeDp, disableScrollingText, endPaddingValues, displayedSectionTitles)
                            }
                        }
                        "playlists_might_like" -> {
                            if (showPlaylistMightLike) {
                                YtmSectionByTitle(ytmSections, { it.contains("Playlist you might like", ignoreCase = true) }, stringResource(R.string.playlists_you_might_like), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "moods_genres" -> {
                            if (showMoodsAndGenres) {
                                MoodsAndGenresSection(showMoodsAndGenres, state.discoverPageInit.value, onMoodClick, navController, gridsContentPadding, displayedSectionTitles)
                            }
                        }
                        "monthly_playlists" -> {
                            if (showMonthlyPlaylists) {
                                MonthlyPlaylistsSection(showMonthlyPlaylists, monthlyPlaylists, navController, endPaddingValues, playlistThumbnailSizeDp, playlistThumbnailSizePx, disableScrollingText)
                            }
                        }
                        "my_top" -> {
                            if (showMyTop) {
                                MyTopSection(showMyTop, myTopSongs, navController, endPaddingValues, sectionTextModifier, itemInHorizontalGridWidth)
                            }
                        }
                        "fresh_finds_old_favorites" -> {
                            if (showFreshFindsOldFavorites) {
                                YtmSectionByTitle(ytmSections, { it.contains("Fresh finds", ignoreCase = true) || it.contains("Old favorites", ignoreCase = true) }, stringResource(R.string.fresh_finds_old_favorites), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "mixed_for_you" -> {
                            if (showMixedForYou) {
                                YtmSectionByTitle(ytmSections, { it.contains("Mixed for you", ignoreCase = true) }, stringResource(R.string.mixed_for_you), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "forgotten_favorites" -> {
                            if (showForgottenFavorites) {
                                YtmSectionByTitle(ytmSections, { it.contains("Forgotten favorites", ignoreCase = true) }, stringResource(R.string.forgotten_favorites), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "your_daily_discover" -> {
                            if (showYourDailyDiscover) {
                                YtmSectionByTitle(ytmSections, { it.contains("Your daily discover", ignoreCase = true) }, stringResource(R.string.your_daily_discover), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "fresh_new_music" -> {
                            if (showFreshNewMusic) {
                                YtmSectionByTitle(ytmSections, { it.contains("Fresh new music", ignoreCase = true) }, stringResource(R.string.fresh_new_music), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "new_releases" -> {
                            if (showNewReleases) {
                                YtmSectionByTitle(ytmSections, { it.contains("New release", ignoreCase = true) && !it.contains("Fresh new music", ignoreCase = true) }, stringResource(R.string.new_releases), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "albums_for_you" -> {
                            if (showAlbumsForYou) {
                                YtmSectionByTitle(ytmSections, { it.contains("Albums for you", ignoreCase = true) }, stringResource(R.string.albums_for_you), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "todays_biggest_hits" -> {
                            if (showTodaysBiggestHits) {
                                YtmSectionByTitle(ytmSections, { it.contains("Today's biggest hits", ignoreCase = true) }, stringResource(R.string.todays_biggest_hits), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "all_hits" -> {
                            if (showAllHits) {
                                YtmSectionByTitle(ytmSections, { it.contains("All hits", ignoreCase = true) }, stringResource(R.string.all_hits), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "featured_playlists" -> {
                            if (showFeaturedPlaylists) {
                                YtmSectionByTitle(ytmSections, { it.contains("Featured playlists", ignoreCase = true) }, stringResource(R.string.featured_playlists_for_you), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "trending_community_playlists" -> {
                            if (showTrendingCommunityPlaylists) {
                                YtmSectionByTitle(ytmSections, { it.contains("Trending community playlists", ignoreCase = true) }, stringResource(R.string.trending_community_playlists), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "from_the_community" -> {
                            if (showFromTheCommunity) {
                                YtmSectionByTitle(ytmSections, { it.contains("From the community", ignoreCase = true) }, stringResource(R.string.from_the_community), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "trending_songs_for_you" -> {
                            if (showTrendingSongsForYou) {
                                YtmSectionByTitle(ytmSections, { it.contains("Trending songs for you", ignoreCase = true) }, stringResource(R.string.trending_songs_for_you), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "top_music_videos" -> {
                            if (showTopMusicVideos) {
                                YtmSectionByTitle(ytmSections, { it.contains("Top music videos", ignoreCase = true) }, stringResource(R.string.top_music_videos), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "cover_and_remixes" -> {
                            if (showCoverAndRemixes) {
                                YtmSectionByTitle(ytmSections, { it.contains("Cover", ignoreCase = true) || it.contains("remix", ignoreCase = true) }, stringResource(R.string.cover_and_remixes), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "trending_in_shorts" -> {
                            if (showTrendingInShorts) {
                                YtmSectionByTitle(ytmSections, { it.contains("Trending in Shorts", ignoreCase = true) }, stringResource(R.string.trending_in_shorts), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "music_videos_for_you" -> {
                            if (showMusicVideosForYou) {
                                YtmSectionByTitle(ytmSections, { it.contains("Music videos for you", ignoreCase = true) }, stringResource(R.string.music_videos_for_you), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "live_performances" -> {
                            if (showLivePerformances) {
                                YtmSectionByTitle(ytmSections, { it.contains("Live performances", ignoreCase = true) }, stringResource(R.string.live_performances), itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, displayedSectionTitles, isLoading = showLoader)
                            }
                        }
                        "moods" -> {
                            if (showMoods) {
                                MoodsSection(state.homePageInit.value, onChipClick, gridsContentPadding, displayedSectionTitles)
                            }
                        }
                        "generic_ytm_sections" -> {
                            if (showGenericYtmSections) {
                                GenericYtmSections(state.homePageInit.value, displayedSectionTitles, itemInHorizontalGridWidth, albumThumbnailSizePx, albumThumbnailSizeDp, songThumbnailSizePx, songThumbnailSizeDp, playlistThumbnailSizePx, playlistThumbnailSizeDp, disableScrollingText, endPaddingValues, navController, onAlbumClick, onArtistClick, onPlaylistClick, isLoading = showLoader)
                            }
                        }
                    }
                }
                
                // HomeBottomShimmer removed as shimmers are now inline in their respective positions

                if (state.relatedPageResult.value?.exceptionOrNull() != null) {
                    Spacer(modifier = Modifier.height(50.dp))
                    BasicText(text = stringResource(R.string.page_not_been_loaded), style = typography().s.secondary.center, modifier = Modifier.fillMaxWidth().padding(all = 16.dp))
                } else {
                    if (!isYouTubeLoggedIn()) {
                        Spacer(modifier = Modifier.height(50.dp))
                        BasicText(text = stringResource(R.string.log_in_to_ytm), style = typography().s.secondary.center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onSettingsClick))
                    } else if (MainApplication.cookieStatus in listOf(MainApplication.CookieStatus.INVALID, MainApplication.CookieStatus.EXPIRED)) {
                        Spacer(modifier = Modifier.height(50.dp))
                        BasicText(text = stringResource(R.string.error_cookie_invalid), style = typography().s.secondary.center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onSettingsClick))
                    }
                }
                Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
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
