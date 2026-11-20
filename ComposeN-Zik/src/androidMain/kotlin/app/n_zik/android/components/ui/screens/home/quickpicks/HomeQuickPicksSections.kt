package app.n_zik.android.components.ui.screens.home.quickpicks

import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.glass.GlassActionGroup
import app.n_zik.android.components.glass.NativeIconActionSize
import app.n_zik.android.components.glass.NativeSectionIconAction
import app.n_zik.android.components.glass.GlassActionGroupItem
import app.n_zik.android.components.glass.GlassActionCellWidth
import app.n_zik.android.components.glass.GlassActionCellHeight
import app.n_zik.android.components.glass.GlassActionIconSize
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.core.database.BookmarkStateManager
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.playlist.LocalPlaylistItemMenu
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import app.n_zik.android.components.menu.song.SongItemMenu
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.PlayEventsType
import app.n_zik.android.models.Song
import app.n_zik.android.models.Artist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.themed.TextPlaceholder
import app.n_zik.android.ui.components.themed.Title
import app.n_zik.android.ui.components.themed.TitleMiniSection
import app.n_zik.android.ui.items.AlbumItem
import app.n_zik.android.ui.items.ArtistItem
import app.n_zik.android.ui.items.PlaylistItem
import app.n_zik.android.ui.items.VideoItem
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.onOverlay
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.color
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.weight
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.HomePage
import app.n_zik.android.utils.coroutines.NzikDispatchers
import timber.log.Timber
import app.n_zik.android.components.ui.InlineCircularLoader
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import app.n_zik.android.ui.items.SongItemPlaceholders
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.components.ShimmerHost
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.LocalDownloadStatesMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download

@Composable
fun QuickPicksHeader(
    playEventType: PlayEventsType,
    onPlayEventTypeChange: (PlayEventsType) -> Unit,
    onDiceClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val menuState = LocalMenuState.current
    
    @Composable
    fun MenuIcon(@DrawableRes icon: Int) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .iconBadgeBackground(
                    color = colorPalette().accent.copy(alpha = 0.1f),
                    shape = uiRoundnessShape()
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                tint = colorPalette().accent,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
    
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.recommendations_for_you_title),
                style = TextStyle(
                    fontSize = typography().l.semiBold.fontSize,
                    fontWeight = typography().l.semiBold.fontWeight,
                    color = colorPalette().text,
                    textAlign = TextAlign.Start
                ),
                modifier = Modifier.weight(1f)
            )
            
            val glass = rememberGlassSettings()
            val onSortClick: () -> Unit = {
                menuState.display {
                    ListMenu.Menu(title = stringResource(R.string.library_sort_order_title)) {
                        ListMenu.Entry(
                            icon = { MenuIcon(R.drawable.chevron_up) },
                            text = stringResource(R.string.home_sort_most_played_song_label),
                            onClick = {
                                onPlayEventTypeChange(PlayEventsType.MostPlayed)
                                menuState.hide()
                            }
                        )
                        ListMenu.Entry(
                            icon = { MenuIcon(R.drawable.chevron_down) },
                            text = stringResource(R.string.home_sort_last_played_song_label),
                            onClick = {
                                onPlayEventTypeChange(PlayEventsType.LastPlayed)
                                menuState.hide()
                            }
                        )
                        ListMenu.Entry(
                            icon = { MenuIcon(R.drawable.random) },
                            text = stringResource(R.string.home_sort_random_label),
                            onClick = {
                                onPlayEventTypeChange(PlayEventsType.CasualPlayed)
                                menuState.hide()
                            }
                        )
                    }
                }
            }
            if (glass.usesGlassMaterial(GlassArea.ActionButtons)) {
                GlassActionGroup(glass, GlassArea.ActionButtons, separatorBefore = setOf(2)) {
                    if (isLoading) {
                        Box(Modifier.size(GlassActionCellWidth * 2, GlassActionCellHeight), contentAlignment = Alignment.Center) {
                            InlineCircularLoader(Modifier.size(GlassActionIconSize), color = colorPalette().textSecondary)
                        }
                    } else {
                        GlassActionGroupItem(onClick = onDiceClick) {
                            Icon(painterResource(R.drawable.dice), stringResource(R.string.glass_effect_action_shuffle), tint = colorPalette().text, modifier = Modifier.size(GlassActionIconSize))
                        }
                        GlassActionGroupItem(onClick = onPlayAllClick) {
                            Icon(painterResource(R.drawable.play), stringResource(R.string.glass_effect_action_play_all), tint = colorPalette().text, modifier = Modifier.size(GlassActionIconSize))
                        }
                    }
                    GlassActionGroupItem(onClick = onSortClick) {
                        Icon(painterResource(R.drawable.settings), stringResource(R.string.library_sort_order_title), tint = colorPalette().text, modifier = Modifier.size(GlassActionIconSize))
                    }
                }
            } else {
                if (isLoading) {
                    Box(Modifier.size(NativeIconActionSize * 2, NativeIconActionSize), contentAlignment = Alignment.Center) {
                        InlineCircularLoader(Modifier.size(24.dp), color = colorPalette().textSecondary)
                    }
                } else {
                    NativeSectionIconAction(R.drawable.dice, stringResource(R.string.glass_effect_action_shuffle),
                        endSpacing = 12.dp, color = colorPalette().text,
                        settings = glass, onClick = onDiceClick)
                    NativeSectionIconAction(R.drawable.play, stringResource(R.string.glass_effect_action_play_all),
                        endSpacing = 12.dp, color = colorPalette().text,
                        settings = glass, onClick = onPlayAllClick)
                }
                NativeSectionIconAction(R.drawable.settings, stringResource(R.string.library_sort_order_title),
                        color = colorPalette().text,
                        settings = glass, onClick = onSortClick)
            }
        }

        BasicText(
            text = playEventType.text,
            style = typography().xxs.secondary,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun QuickPicksGrid(
    recommendations: List<Song>,
    trendingList: List<Song>,
    playEventType: PlayEventsType,
    itemInHorizontalGridWidth: Dp,
    navController: NavController,
    endPaddingValues: PaddingValues,
    isLoading: Boolean,
    onSongClick: (Song) -> Unit,
    scrollToStartTrigger: Int = 0,
    forcePlaceholder: Boolean = false,
) {
    val preview = LocalThemePreview.current
    val quickPicksLazyGridState = rememberLazyGridState()

    val songs = remember(recommendations) { recommendations.distinctBy { it.id } }
    val loadingSlots = quickPicksLoadingSlots(songs.size, isLoading || forcePlaceholder && songs.isEmpty())
    val rows = if (songs.isNotEmpty() || !loadingSlots.isEmpty()) 3 else 1
    val songIds = remember(songs) { songs.map { it.id } }
    val likeStatesMap by remember(songIds) {
        if (preview != null) kotlinx.coroutines.flow.flowOf(emptyMap()) else LikeStateManager.getLikeStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val playlistStatesMap by remember(songIds) {
        if (preview != null) kotlinx.coroutines.flow.flowOf(emptyMap()) else PlaylistStateManager.getPlaylistStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    // Download state cache
    val downloadsMapState by MyDownloadHelper.downloads.collectAsStateWithLifecycle(initialValue = MyDownloadHelper.downloads.value, context = NzikDispatchers.DATA)
    val downloadedIds by remember {
        derivedStateOf {
            downloadsMapState.values
                .filter { it.state == Download.STATE_COMPLETED }
                .mapTo(HashSet()) { it.request.id }
        }
    }
    val downloadStatesMap by remember(songs) {
        derivedStateOf {
            songs.associate { song ->
                song.id to when {
                    song.id in downloadedIds -> DownloadedStateMedia.DOWNLOADED
                    else -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                }
            }
        }
    }

    LaunchedEffect(scrollToStartTrigger) {
        if (scrollToStartTrigger > 0) {
            quickPicksLazyGridState.animateScrollToItem(0)
        }
    }

    CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
    LazyHorizontalGrid(
        state = quickPicksLazyGridState,
        rows = GridCells.Fixed(rows),
        flingBehavior = ScrollableDefaults.flingBehavior(),
        contentPadding = endPaddingValues,
        modifier = Modifier.fillMaxWidth().height(Dimensions.itemsVerticalPadding * rows * 9)
    ) {
        items(songs, key = { it.id }, contentType = { "song" }) { song ->
            if (forcePlaceholder) {
                SongItemPlaceholder(
                    modifier = Modifier.width(itemInHorizontalGridWidth).animateItem(),
                    title = song.title,
                    artist = song.artistsText,
                )
            } else {
                SongItem(
                    song = song,
                    isLiked = likeStatesMap[song.id],
                    inPlaylist = playlistStatesMap[song.id],
                    navController = navController,
                    onClick = { onSongClick(song) },
                    modifier = Modifier.width(itemInHorizontalGridWidth).animateItem(),
                    thumbnailOverlay = {
                        if (playEventType != PlayEventsType.CasualPlayed &&
                            trendingList.any { it.id == song.id }) {
                            Image(
                                painter = painterResource(R.drawable.star_brilliant),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colorPalette().accent),
                                modifier = Modifier
                                    .size(23.dp)
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                            )
                        }
                    }
                )
            }
        }
        items(loadingSlots.count(), key = { "quick-picks-loading-${loadingSlots.first + it}" },
            contentType = { "song-placeholder" }) {
            SongItemPlaceholders(count = 1, modifier = Modifier.width(itemInHorizontalGridWidth).animateItem())
        }
    }
    } // CompositionLocalProvider
}

internal fun quickPicksLoadingSlots(songCount: Int, loading: Boolean): IntRange =
    if (loading) songCount until 6 else IntRange.EMPTY


@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun YtmSectionByTitle(
    ytmSections: List<HomePage.Section>,
    titlePredicate: (String) -> Boolean,
    titleOverride: String,
    itemInHorizontalGridWidth: Dp,
    albumThumbnailSizePx: Int,
    albumThumbnailSizeDp: Dp,
    songThumbnailSizePx: Int,
    songThumbnailSizeDp: Dp,
    playlistThumbnailSizePx: Int,
    playlistThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    endPaddingValues: PaddingValues,
    navController: NavController,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    displayedSectionTitles: MutableSet<String>,
    isLoading: Boolean = false,
    showTitle: Boolean = true,
    emptyPlaceholderKind: YtmShelfPlaceholderKind = YtmShelfPlaceholderKind.Album,
    emptyMetadataLines: Int = if (emptyPlaceholderKind == YtmShelfPlaceholderKind.Video) 1 else 0,
    forcePlaceholder: Boolean = false,
) {
    val matching = ytmSections.filter { titlePredicate(it.title) }
    val items = matching.flatMap { it.items }.filterNotNull().distinctBy { it.key }

    // Shimmer: shown while loading and no items yet
    AnimatedVisibility(
        visible = (isLoading || forcePlaceholder) && items.isEmpty(),
        enter = EnterTransition.None,
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Column {
            if (showTitle) {
                Title(
                    title = titleOverride,
                    enableClick = false,
                    onClick = null,
                    verticalPadding = 16.dp
                )
            }
            if (emptyPlaceholderKind == YtmShelfPlaceholderKind.Song) {
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(3),
                    contentPadding = endPaddingValues,
                    modifier = Modifier.fillMaxWidth().height(Dimensions.itemsVerticalPadding * 3 * 9),
                ) {
                    items(9, contentType = { "placeholder" }) {
                        SongItemPlaceholders(count = 1, modifier = Modifier.width(itemInHorizontalGridWidth))
                    }
                }
            } else LazyRow(contentPadding = endPaddingValues) {
                items(5, key = { it }, contentType = { "placeholder" }) {
                    AnimatedVisibility(
                        visible = isLoading || forcePlaceholder,
                        enter = slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(300, delayMillis = it * 50, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(250, delayMillis = it * 50)),
                        exit = slideOutVertically(
                            targetOffsetY = { -it / 3 },
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200))
                    ) {
                        ShimmerHost {
                            YtmShelfCardPlaceholder(YtmShelfCard(emptyPlaceholderKind, emptyMetadataLines),
                                albumThumbnailSizeDp, playlistThumbnailSizeDp)
                        }
                    }
                }
            }
        }
    }

    if (items.isEmpty()) return

    matching.forEach { displayedSectionTitles.add(it.title) }
    val section = matching.first().copy(
        items = items
    )
    Timber.tag("HomeQuickPicksSections").d("YTM Section found: $titleOverride (${items.size} items)")
    // Content: fades in when items arrive
    AnimatedVisibility(
        visible = items.isNotEmpty(),
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        YtmSectionItems(
            section = section,
            titleOverride = titleOverride,
            itemInHorizontalGridWidth = itemInHorizontalGridWidth,
            albumThumbnailSizePx = albumThumbnailSizePx,
            albumThumbnailSizeDp = albumThumbnailSizeDp,
            songThumbnailSizePx = songThumbnailSizePx,
            songThumbnailSizeDp = songThumbnailSizeDp,
            playlistThumbnailSizePx = playlistThumbnailSizePx,
            playlistThumbnailSizeDp = playlistThumbnailSizeDp,
            disableScrollingText = disableScrollingText,
            endPaddingValues = endPaddingValues,
            navController = navController,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onPlaylistClick = onPlaylistClick,
            isLoading = isLoading,
            showTitle = showTitle,
            forcePlaceholder = forcePlaceholder,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun NewAlbumsOfYourArtistsSection(
    discoverPageInit: Innertube.DiscoverPage?,
    artists: List<Artist>,
    newReleaseAlbumsFiltered: List<Innertube.AlbumItem>,
    showNewAlbumsArtists: Boolean,
    onAlbumClick: (String) -> Unit,
    navController: NavController,
    albumThumbnailSizePx: Int,
    albumThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    endPaddingValues: PaddingValues,
    sectionTextModifier: Modifier,
    showTitle: Boolean = true,
    forcePlaceholder: Boolean = false,
) {
    val menuState = LocalMenuState.current
    if (showNewAlbumsArtists && (forcePlaceholder || discoverPageInit != null)) {
        if (forcePlaceholder || (newReleaseAlbumsFiltered.isNotEmpty() && artists.isNotEmpty())) {
            Timber.tag("HomeQuickPicksSections").d("Local New Albums of your Artists found (${newReleaseAlbumsFiltered.size} items)")
            Column {
                if (showTitle) {
                    BasicText(
                        text = stringResource(R.string.recommendations_followed_artists_new_albums_title),
                        style = typography().l.semiBold,
                        modifier = sectionTextModifier
                    )
                }

                val filteredAlbumKeys = remember(newReleaseAlbumsFiltered) { newReleaseAlbumsFiltered.map { it.key } }
                val filteredBookmarkStatesMap by remember(filteredAlbumKeys) {
                    BookmarkStateManager.getAlbumBookmarkStates(filteredAlbumKeys)
                }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

                if (forcePlaceholder) {
                    ShelfCardPlaceholders(newReleaseAlbumsFiltered, YtmShelfPlaceholderKind.Album, albumThumbnailSizeDp, endPaddingValues)
                } else LazyRow(contentPadding = endPaddingValues) {
                    items(
                        items = newReleaseAlbumsFiltered.distinctBy { it.key },
                        key = { it.key },
                        contentType = { "album" }) {
                        AlbumItem(
                            album = it,
                            thumbnailSizePx = albumThumbnailSizePx,
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            alternative = true,
                            bookmarkState = filteredBookmarkStatesMap[it.key],
                            modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                onClick = { onAlbumClick(it.key) },
                                onLongClick = { menuState.display { OnlineAlbumItemMenu(navController = navController, album = it).MenuComponent() } }
                            ).animateItem(),
                            disableScrollingText = disableScrollingText
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun NewAlbumsSection(
    discoverPageInit: Innertube.DiscoverPage?,
    showNewAlbums: Boolean,
    onAlbumClick: (String) -> Unit,
    navController: NavController,
    albumThumbnailSizePx: Int,
    albumThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    endPaddingValues: PaddingValues,
    displayedSectionTitles: MutableSet<String>,
    showTitle: Boolean = true,
    forcePlaceholder: Boolean = false,
) {
    val menuState = LocalMenuState.current
    if (showNewAlbums) {
        if (forcePlaceholder || discoverPageInit != null) {
            val albums = discoverPageInit?.newReleaseAlbums.orEmpty()
            if (forcePlaceholder || albums.isNotEmpty()) {
                displayedSectionTitles.add("New albums")
                Column {
                    if (showTitle) {
                        HomeSectionTitle(
                            title = stringResource(R.string.recommendations_new_albums_title),
                            onClick = { navController.navigate(NavRoutes.newAlbums.name) },
                            verticalPadding = 16.dp,
                        )
                    }

                    val newAlbumsKeys = remember(albums) { albums.map { it.key } }
                    val newAlbumsBookmarkStatesMap by remember(newAlbumsKeys) {
                        BookmarkStateManager.getAlbumBookmarkStates(newAlbumsKeys)
                    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

                    if (forcePlaceholder) {
                        ShelfCardPlaceholders(albums, YtmShelfPlaceholderKind.Album, albumThumbnailSizeDp, endPaddingValues)
                    } else LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = albums.distinctBy { it.key },
                            key = { it.key },
                            contentType = { "album" }) {
                            AlbumItem(
                                album = it,
                                thumbnailSizePx = albumThumbnailSizePx,
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                alternative = true,
                                bookmarkState = newAlbumsBookmarkStatesMap[it.key],
                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                    onClick = { onAlbumClick(it.key) },
                                    onLongClick = { menuState.display { OnlineAlbumItemMenu(navController = navController, album = it).MenuComponent() } }
                                ).animateItem(),
                                disableScrollingText = disableScrollingText
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun RelatedAlbumsSection(
    relatedInit: Innertube.RelatedPage?,
    showRelatedAlbums: Boolean,
    onAlbumClick: (String) -> Unit,
    navController: NavController,
    albumThumbnailSizePx: Int,
    albumThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    endPaddingValues: PaddingValues,
    sectionTextModifier: Modifier,
    displayedSectionTitles: MutableSet<String>,
    forcePlaceholder: Boolean = false,
) {
    val menuState = LocalMenuState.current
    if (showRelatedAlbums) {
        val albums = relatedInit?.albums.orEmpty()
        if (forcePlaceholder || relatedInit?.albums != null) {
            Column {
                Timber.tag("HomeQuickPicksSections").d("Related Section found: Albums (${albums.size} items)")
                displayedSectionTitles.add("Related albums")
                BasicText(
                    text = stringResource(R.string.album_related_title),
                    style = typography().l.semiBold,
                    modifier = sectionTextModifier
                )

                val relatedAlbumKeys = remember(albums) { albums.map { it.key } }
                val relatedAlbumBookmarkStatesMap by remember(relatedAlbumKeys) {
                    BookmarkStateManager.getAlbumBookmarkStates(relatedAlbumKeys)
                }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

                if (forcePlaceholder) {
                    ShelfCardPlaceholders(albums, YtmShelfPlaceholderKind.Album, albumThumbnailSizeDp, endPaddingValues)
                } else LazyRow(contentPadding = endPaddingValues) {
                    items(
                        items = albums.distinctBy { it.key },
                        key = Innertube.AlbumItem::key,
                        contentType = { "album" }
                    ) { album ->
                        AlbumItem(
                            album = album,
                            thumbnailSizePx = albumThumbnailSizePx,
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            alternative = true,
                            bookmarkState = relatedAlbumBookmarkStatesMap[album.key],
                            modifier = Modifier
                                .clip(uiRoundnessShape()).combinedClickable(
                                    onClick = { onAlbumClick(album.key) },
                                    onLongClick = { menuState.display { OnlineAlbumItemMenu(navController = navController, album = album).MenuComponent() } }
                                ).animateItem(),
                            disableScrollingText = disableScrollingText
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun SimilarArtistsSection(
    relatedInit: Innertube.RelatedPage?,
    showSimilarArtists: Boolean,
    onArtistClick: (String) -> Unit,
    navController: NavController,
    artistThumbnailSizePx: Int,
    artistThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    endPaddingValues: PaddingValues,
    sectionTextModifier: Modifier,
    displayedSectionTitles: MutableSet<String>,
    forcePlaceholder: Boolean = false,
) {
    val menuState = LocalMenuState.current
    if (showSimilarArtists) {
        val artists = relatedInit?.artists.orEmpty()
        if (forcePlaceholder || relatedInit?.artists != null) {
            Column {
                Timber.tag("HomeQuickPicksSections").d("Related Section found: Similar Artists (${artists.size} items)")
                displayedSectionTitles.add("Similar artists")
                BasicText(
                    text = stringResource(R.string.artist_similar_title),
                    style = typography().l.semiBold,
                    modifier = sectionTextModifier
                )

                val similarArtistKeys = remember(artists) { artists.map { it.key } }
                val similarArtistBookmarkStatesMap by remember(similarArtistKeys) {
                    BookmarkStateManager.getArtistBookmarkStates(similarArtistKeys)
                }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

                if (forcePlaceholder) {
                    ShelfCardPlaceholders(artists, YtmShelfPlaceholderKind.Artist, artistThumbnailSizeDp, endPaddingValues)
                } else LazyRow(contentPadding = endPaddingValues) {
                    items(
                        items = artists.distinctBy { it.key },
                        key = Innertube.ArtistItem::key,
                        contentType = { "artist" }
                    ) { artist ->
                        ArtistItem(
                            artist = artist,
                            thumbnailSizePx = artistThumbnailSizePx,
                            thumbnailSizeDp = artistThumbnailSizeDp,
                            alternative = true,
                            bookmarkState = similarArtistBookmarkStatesMap[artist.key],
                            modifier = Modifier
                                .clip(uiRoundnessShape()).combinedClickable(
                                    onClick = { onArtistClick(artist.key) },
                                    onLongClick = { menuState.display { OnlineArtistItemMenu(navController = navController, artist = artist).MenuComponent() } }
                                ).animateItem(),
                            disableScrollingText = disableScrollingText
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun MyTopSection(
    showMyTopPlaylist: Boolean,
    myTopSongs: List<Song>,
    navController: NavController,
    endPaddingValues: PaddingValues,
    sectionTextModifier: Modifier,
    itemInHorizontalGridWidth: Dp,
    forcePlaceholder: Boolean = false,
) {
    val binder = LocalPlayerServiceBinder.current

    val songIds = remember(myTopSongs) { myTopSongs.map { it.id } }
    val likeStatesMap by remember(songIds) {
        LikeStateManager.getLikeStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val playlistStatesMap by remember(songIds) {
        PlaylistStateManager.getPlaylistStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    if (showMyTopPlaylist) {
        if (forcePlaceholder || myTopSongs.isNotEmpty()) {
            Timber.tag("HomeQuickPicksSections").d("Local Section found: My Top (${myTopSongs.size} items)")
            Column {
                BasicText(
                    text = stringResource(R.string.recommendations_personal_top_title),
                    style = typography().l.semiBold,
                    modifier = sectionTextModifier
                )

                LazyRow(contentPadding = endPaddingValues) {
                    if (forcePlaceholder) items(myTopSongs.size.coerceAtLeast(5)) {
                        SongItemPlaceholders(count = 1, modifier = Modifier.width(itemInHorizontalGridWidth))
                    } else items(
                        items = myTopSongs.distinctBy { it.id },
                        key = { it.id },
                        contentType = { "song" }
                    ) { song ->
                        SongItem(
                            song = song,
                            isLiked = likeStatesMap[song.id],
                            inPlaylist = playlistStatesMap[song.id],
                            navController = navController,
                            onClick = { binder?.startRadio(song, true) },
                    modifier = Modifier.width(itemInHorizontalGridWidth).animateItem(),
                        )
                }
                }
            } // Column
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun RewindSection(
    showRewind: Boolean,
    rewindPlaylists: List<PlaylistPreview>,
    navController: NavController,
    endPaddingValues: PaddingValues,
    sectionTextModifier: Modifier,
    playlistThumbnailSizePx: Int,
    playlistThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    forcePlaceholder: Boolean = false,
) {
    val menuState = LocalMenuState.current
    // Spec 2: the generated rewind-* playlists as a local section — auto-hidden when none
    // exist (same pattern as the other local sections).
    if (showRewind) {
        if (forcePlaceholder || rewindPlaylists.isNotEmpty()) {
            Timber.tag("HomeQuickPicksSections").d("Local Section found: Rewind (${rewindPlaylists.size} items)")
            Column {
                BasicText(
                    text = stringResource(R.string.rewind_title),
                    style = typography().l.semiBold,
                    modifier = sectionTextModifier
                )

                if (forcePlaceholder) {
                    ShelfCardPlaceholders(emptyList(), YtmShelfPlaceholderKind.Playlist, playlistThumbnailSizeDp, endPaddingValues,
                        cardCount = rewindPlaylists.size)
                } else LazyRow(contentPadding = endPaddingValues) {
                    items(
                        items = rewindPlaylists.distinctBy { it.playlist.id },
                        key = { it.playlist.id },
                        contentType = { "rewindPlaylist" }
                    ) { preview ->
                        // Same card as the library's local playlist list (2026-10-24):
                        // the four-track cover collage (or custom thumbnail), the origin
                        // icon and the song-count badge at the bottom right.
                        PlaylistItem(
                            playlist = preview,
                            thumbnailSizePx = playlistThumbnailSizePx,
                            thumbnailSizeDp = playlistThumbnailSizeDp,
                            // alternative = true: square card, localized name centered
                            // below — the same layout as the library's playlist grid.
                            alternative = true,
                            modifier = Modifier
                                .clip(uiRoundnessShape())
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("${NavRoutes.localPlaylist.name}/${preview.playlist.id}")
                                    },
                                    onLongClick = {
                                        menuState.display {
                                            LocalPlaylistItemMenu(
                                                navController = navController,
                                                playlistPreview = preview
                                            ).MenuComponent()
                                        }
                                    }
                                )
                                .animateItem(),
                            isYoutubePlaylist = preview.playlist.isYoutubePlaylist,
                            isEditable = preview.playlist.isEditable,
                            disableScrollingText = disableScrollingText
                        )
                    }
                }
            } // Column
        }
    }
}

@UnstableApi
@Composable
fun MoodsSection(
    homePageInit: HomePage?,
    onChipClick: (Innertube.Chip) -> Unit,
    gridsContentPadding: PaddingValues,
    displayedSectionTitles: MutableSet<String>,
    forcePlaceholder: Boolean = false,
) {
    if (forcePlaceholder || homePageInit?.chips != null) {
        val chips = homePageInit?.chips.orEmpty()
        if (forcePlaceholder || chips.isNotEmpty()) {
            displayedSectionTitles.add("Moods")
            Column {
                Title(
                    title = stringResource(R.string.recommendations_moods_title),
                    verticalPadding = 16.dp,
                )

                LazyHorizontalGrid(
                    rows = GridCells.Fixed(4),
                    flingBehavior = ScrollableDefaults.flingBehavior(),
                    contentPadding = gridsContentPadding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.itemsVerticalPadding * 4 * 8)
                ) {
                    if (forcePlaceholder) items(chips.size.takeIf { it > 0 } ?: 8) { index ->
                        MoodCardPlaceholder(title = chips.getOrNull(index)?.title)
                    } else items(chips, key = { it.hashCode() }, contentType = { "chip" }) { chip ->
                        ChipItemColored(chip = chip, onClick = { onChipClick(chip) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun MoodsAndGenresSection(
    showMoodsAndGenres: Boolean,
    discoverPageInit: Innertube.DiscoverPage?,
    onMoodClick: (Innertube.Mood.Item) -> Unit,
    navController: NavController,
    gridsContentPadding: PaddingValues,
    displayedSectionTitles: MutableSet<String>,
    forcePlaceholder: Boolean = false,
) {
    if (showMoodsAndGenres) {
        val moods = discoverPageInit?.moods.orEmpty()
        if (forcePlaceholder || moods.isNotEmpty()) {
            displayedSectionTitles.add("Moods and genres")
            displayedSectionTitles.add("Moods & genres")
            Column {
                HomeSectionTitle(
                    title = stringResource(R.string.recommendations_moods_and_genres_title),
                    onClick = { navController.navigate(NavRoutes.moodsPage.name) },
                    verticalPadding = 16.dp,
                )

                val moodAngGenresLazyGridState = rememberLazyGridState()
                LazyHorizontalGrid(
                    state = moodAngGenresLazyGridState,
                    rows = GridCells.Fixed(4),
                    flingBehavior = ScrollableDefaults.flingBehavior(),
                    contentPadding = gridsContentPadding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.itemsVerticalPadding * 4 * 8)
                ) {
                    if (forcePlaceholder) items(moods.size.takeIf { it > 0 } ?: 8) { index ->
                        MoodCardPlaceholder(title = moods.getOrNull(index)?.title)
                    } else items(
                        items = moods.sortedBy { it.title },
                        key = { it.endpoint.params ?: it.title },
                        contentType = { "mood" }
                    ) {
                        MoodItemColored(
                            mood = it,
                            onClick = { it.endpoint.browseId?.let { _ -> onMoodClick(it) } },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShelfCardPlaceholders(
    entries: List<Innertube.Item>,
    fallback: YtmShelfPlaceholderKind,
    thumbnailSize: Dp,
    contentPadding: PaddingValues,
    cardCount: Int = entries.size,
) {
    val cards = if (entries.isNotEmpty()) ytmShelfCards(entries) else listOf(YtmShelfCard(fallback))
    LazyRow(contentPadding = contentPadding) {
        items(cardCount.takeIf { it > 0 } ?: 5, contentType = { "placeholder" }) { index ->
            ShimmerHost {
                YtmShelfCardPlaceholder(cards[index % cards.size], thumbnailSize, thumbnailSize, item = entries.getOrNull(index))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun GenericYtmSections(
    homePageInit: HomePage?,
    displayedSectionTitles: MutableSet<String>,
    itemInHorizontalGridWidth: Dp,
    albumThumbnailSizePx: Int,
    albumThumbnailSizeDp: Dp,
    songThumbnailSizePx: Int,
    songThumbnailSizeDp: Dp,
    playlistThumbnailSizePx: Int,
    playlistThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    endPaddingValues: PaddingValues,
    navController: NavController,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    isLoading: Boolean = false,
    showTitle: Boolean = true,
    forcePlaceholder: Boolean = false,
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    homePageInit?.sections?.forEach { section ->
        if (section.items.isEmpty() || section.items.firstOrNull()?.key == null) return@forEach
        
        val title = section.title
        val videosAsAlbums = title.contains("retrospective", ignoreCase = true) ||
            title.contains("rétrospective", ignoreCase = true)
        val browseId = section.endpoint?.browseId

        if (displayedSectionTitles.contains(title)) return@forEach
        if (browseId == "FEmusic_new_releases_albums") return@forEach
        if (browseId == "FEmusic_charts") return@forEach
        if (browseId == "FEmusic_moods_and_genres") return@forEach
        
        // Skip all specifically handled sections
        if (title.contains("Quick picks", ignoreCase = true)) return@forEach
        if (title.contains("Fresh finds", ignoreCase = true)) return@forEach
        if (title.contains("Old favorites", ignoreCase = true)) return@forEach
        if (title.contains("Mixed for you", ignoreCase = true)) return@forEach
        if (title.contains("Forgotten favorites", ignoreCase = true)) return@forEach
        if (title.contains("Your daily discover", ignoreCase = true)) return@forEach
        if (title.contains("New release", ignoreCase = true)) return@forEach
        if (title.contains("Fresh new music", ignoreCase = true)) return@forEach
        if (title.contains("Albums for you", ignoreCase = true)) return@forEach
        if (title.contains("Today's biggest hits", ignoreCase = true)) return@forEach
        if (title.contains("All hits", ignoreCase = true)) return@forEach
        if (title.contains("Featured playlists", ignoreCase = true)) return@forEach
        if (title.contains("Trending community playlists", ignoreCase = true)) return@forEach
        if (title.contains("From the community", ignoreCase = true)) return@forEach
        if (title.contains("Trending songs for you", ignoreCase = true)) return@forEach
        if (title.contains("Cover", ignoreCase = true)) return@forEach
        if (title.contains("remix", ignoreCase = true)) return@forEach
        if (title.contains("Music videos for you", ignoreCase = true)) return@forEach
        if (title.contains("Live performances", ignoreCase = true)) return@forEach
        if (title.contains("Charts", ignoreCase = true)) return@forEach
        if (title.contains("New albums", ignoreCase = true)) return@forEach
        if (title.contains("Related albums", ignoreCase = true)) return@forEach
        if (title.contains("Similar artists", ignoreCase = true)) return@forEach
        if (title.contains("Playlist you might like", ignoreCase = true)) return@forEach
        if (title.contains("Top music videos", ignoreCase = true)) return@forEach
        if (title.contains("Trending in Shorts", ignoreCase = true)) return@forEach
        if (title.contains("Trending in Shorts", ignoreCase = true)) return@forEach
        if (title.contains("Moods", ignoreCase = true)) return@forEach
        if (title.contains("Genre", ignoreCase = true)) return@forEach

        val isSongOnly = section.items.all { item -> item is Innertube.SongItem }

        Column {
            if (showTitle) {
                TitleMiniSection(section.label ?: "", modifier = Modifier.padding(horizontal = 12.dp).padding(top = 16.dp, bottom = 4.dp))

                BasicText(
                    text = section.title,
                    style = typography().l.semiBold.color(colorPalette().text),
                    modifier = Modifier.padding(horizontal = 12.dp).padding(vertical = 4.dp)
                )
            }

            if (isSongOnly) {
                val songItems = section.items.filterIsInstance<Innertube.SongItem>()
                val sectionSongIds = remember(songItems) { songItems.mapNotNull { it.key } }
                val sectionLikeStatesMap by remember(sectionSongIds) {
                    LikeStateManager.getLikeStates(sectionSongIds)
                }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
                val sectionPlaylistStatesMap by remember(sectionSongIds) {
                    PlaylistStateManager.getPlaylistStates(sectionSongIds)
                }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

                LazyHorizontalGrid(
                rows = GridCells.Fixed(3),
                flingBehavior = ScrollableDefaults.flingBehavior(),
                contentPadding = endPaddingValues,
                modifier = Modifier.fillMaxWidth().height(Dimensions.itemsVerticalPadding * 3 * 9)
            ) {
                items(songItems, key = { it.key ?: it.hashCode() }, contentType = { "song" }) { item ->
                    val song = item.asSong
                    if (forcePlaceholder) {
                        SongItemPlaceholder(
                            modifier = Modifier.width(itemInHorizontalGridWidth).animateItem(),
                            title = song?.cleanTitle(),
                            artist = song?.cleanArtistsText(),
                        )
                        return@items
                    }
                    SongItem(
                        song = song ?: Song.makePlaceholder(""),
                        isLiked = sectionLikeStatesMap[item.key],
                        inPlaylist = sectionPlaylistStatesMap[item.key],
                        navController = navController,
                        onClick = {
                            val mediaItem = item.asMediaItem
                            binder?.stopRadio()
                            binder?.player?.forcePlay(mediaItem)
                            binder?.player?.addMediaItems(songItems.map { s -> s.asMediaItem })
                        },
                        modifier = Modifier.width(itemInHorizontalGridWidth).animateItem()
                    )
                }
                
                if ((!forcePlaceholder && isLoading) || (forcePlaceholder && songItems.isEmpty())) {
                    items(
                        if (forcePlaceholder) 9 else 3,
                        key = { "shimmer_$it" }, contentType = { "placeholder" },
                    ) { index ->
                        AnimatedVisibility(
                            visible = isLoading || forcePlaceholder,
                            enter = slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec = tween(300, delayMillis = index * 50, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(250, delayMillis = index * 50)),
                            exit = slideOutVertically(
                                targetOffsetY = { -it / 3 },
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(200))
                        ) {
                            SongItemPlaceholder(
                                modifier = Modifier.width(itemInHorizontalGridWidth).animateItem(),
                            )
                        }
                    }
                }
            }
        } else {
            val sectionAlbumKeys = remember(section.items) { section.items.filterIsInstance<Innertube.AlbumItem>().map { it.key } }
            val sectionAlbumBookmarkStatesMap by remember(sectionAlbumKeys) {
                BookmarkStateManager.getAlbumBookmarkStates(sectionAlbumKeys)
            }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
            val sectionArtistKeys = remember(section.items) { section.items.filterIsInstance<Innertube.ArtistItem>().map { it.key } }
            val sectionArtistBookmarkStatesMap by remember(sectionArtistKeys) {
                BookmarkStateManager.getArtistBookmarkStates(sectionArtistKeys)
            }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
            val sectionPlaylistKeys = remember(section.items) { section.items.filterIsInstance<Innertube.PlaylistItem>().map { it.key } }
            val sectionPlaylistBookmarkStatesMap by remember(sectionPlaylistKeys) {
                BookmarkStateManager.getPlaylistBookmarkStates(sectionPlaylistKeys)
            }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
            val sectionVideoKeys = remember(section.items) { section.items.filterIsInstance<Innertube.VideoItem>().map { it.key } }
            val sectionVideoLikeStatesMap by remember(sectionVideoKeys) {
                LikeStateManager.getLikeStates(sectionVideoKeys)
            }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

            val entries = section.items.filterNotNull()
            val cards = remember(entries, videosAsAlbums) { ytmShelfCards(entries, videosAsAlbums) }
            LazyRow(contentPadding = endPaddingValues) {
                items(entries, key = { it.hashCode() }, contentType = { "item" }) { item ->
                    if (forcePlaceholder) {
                        YtmShelfCardPlaceholder(
                            ytmShelfCards(listOf(item), videosAsAlbums).single(), albumThumbnailSizeDp, playlistThumbnailSizeDp,
                            Modifier.animateItem(), item = item,
                        )
                        return@items
                    }
                    when (item) {
                        is Innertube.SongItem -> {
                            val song = item.asSong ?: Song.makePlaceholder("")
                            AlbumItem(
                                thumbnailUrl = item.thumbnail?.url,
                                title = item.info?.name,
                                authors = item.authors.artistEntryNames().joinToString(", "),
                                year = null,
                                thumbnailSizePx = albumThumbnailSizePx,
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                alternative = true,
                                showAuthors = true,
                                modifier = Modifier
                                    .clip(uiRoundnessShape())
                                    .combinedClickable(
                                        onClick = {
                                            val mediaItem = item.asMediaItem
                                            binder?.stopRadio()
                                            binder?.player?.forcePlay(mediaItem)
                                        },
                                        onLongClick = {
                                            menuState.display { SongItemMenu(navController = navController, song = song).MenuComponent() }
                                        }
                                    ).animateItem(),
                                disableScrollingText = disableScrollingText
                            )
                        }
                        is Innertube.AlbumItem -> {
                            AlbumItem(
                                album = item,
                                alternative = true,
                                thumbnailSizePx = albumThumbnailSizePx,
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                bookmarkState = sectionAlbumBookmarkStatesMap[item.key],
                                disableScrollingText = disableScrollingText,
                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                    onClick = { navController.navigate("${NavRoutes.album.name}/${item.key}") },
                                    onLongClick = { menuState.display { OnlineAlbumItemMenu(navController = navController, album = item).MenuComponent() } }
                                ).animateItem()
                            )
                        }
                        is Innertube.ArtistItem -> {
                            // Album card format (album-size thumbnail, name centered below)
                            // so artist items match the album geometry of the other cards.
                            ArtistItem(
                                artist = item,
                                thumbnailSizePx = albumThumbnailSizePx,
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                alternative = true,
                                bookmarkState = sectionArtistBookmarkStatesMap[item.key],
                                disableScrollingText = disableScrollingText,
                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                    onClick = { navController.navigate("${NavRoutes.artist.name}/${item.key}") },
                                    onLongClick = { menuState.display { OnlineArtistItemMenu(navController = navController, artist = item).MenuComponent() } }
                                ).animateItem()
                            )
                        }
                        is Innertube.PlaylistItem -> {
                            PlaylistItem(
                                playlist = item,
                                alternative = true,
                                thumbnailSizePx = playlistThumbnailSizePx,
                                thumbnailSizeDp = playlistThumbnailSizeDp,
                                isBookmarked = sectionPlaylistBookmarkStatesMap[item.key],
                                disableScrollingText = disableScrollingText,
                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                    onClick = { navController.navigate("${NavRoutes.playlist.name}/${item.key}") },
                                    onLongClick = { menuState.display { OnlinePlaylistItemMenu(navController = navController, playlist = item).MenuComponent() } }
                                ).animateItem()
                            )
                        }
                        is Innertube.VideoItem -> {
                            // User request 2026-09-30: the YTM "Retrospective" section uses
                            // the album format (square thumbnail, title/artist centered
//
                            // below) instead of the wide video card; playback and the
                            // long-press menu stay video-based (same as the SongItem branch).
                            if (videosAsAlbums) {
                                AlbumItem(
                                    thumbnailUrl = item.thumbnail?.url,
                                    title = item.info?.name,
                                    authors = item.authors.artistEntryNames().joinToString(", "),
                                    year = null,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true,
                                    showAuthors = true,
                                    modifier = Modifier
                                        .clip(uiRoundnessShape())
                                        .combinedClickable(
                                            onClick = {
                                                binder?.stopRadio()
                                                binder?.player?.forcePlay(item.asMediaItem)
                                            },
                                            onLongClick = { menuState.display { VideoItemMenu(navController = navController, song = item.asSong).MenuComponent() } }
                                        ).animateItem(),
                                    disableScrollingText = disableScrollingText
                                )
                            } else {
                                VideoItem(
                                    video = item,
                                    thumbnailHeightDp = albumThumbnailSizeDp,
                                    thumbnailWidthDp = (albumThumbnailSizeDp * 16 / 9),
                                    likeState = sectionVideoLikeStatesMap[item.key],
                                    disableScrollingText = disableScrollingText,
                                    alternative = true,
                                    modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                        onClick = {
                                            binder?.stopRadio()
                                            binder?.player?.forcePlay(item.asMediaItem)
                                        },
                                        onLongClick = { menuState.display { VideoItemMenu(navController = navController, song = item.asSong).MenuComponent() } }
                                    ).animateItem()
                                )
                            }
                        }
                    }
                }
                
                if ((!forcePlaceholder && isLoading) || (forcePlaceholder && entries.isEmpty())) {
                    items(if (forcePlaceholder) 5 else 3, key = { "shimmer_$it" }, contentType = { "placeholder" }) { index ->
                        AnimatedVisibility(
                            visible = isLoading || forcePlaceholder,
                            enter = slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec = tween(300, delayMillis = index * 50, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(250, delayMillis = index * 50)),
                            exit = slideOutVertically(
                                targetOffsetY = { -it / 3 },
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(200))
                        ) {
                            ShimmerHost {
                                YtmShelfCardPlaceholder(cards[index % cards.size], albumThumbnailSizeDp, playlistThumbnailSizeDp)
                            }
                        }
                    }
                }
            } // LazyRow
        } // Column
    } // forEach

    if ((isLoading || forcePlaceholder) && homePageInit?.sections.isNullOrEmpty()) {
        repeat(4) {
            Column {
                TextPlaceholder(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 16.dp, bottom = 8.dp)
                )
                LazyRow(contentPadding = endPaddingValues) {
                    items(4, key = { "generic_shimmer_${it}" }, contentType = { "placeholder" }) {
                        ShimmerHost {
                            YtmShelfCardPlaceholder(YtmShelfCard(YtmShelfPlaceholderKind.Album), albumThumbnailSizeDp, playlistThumbnailSizeDp)
                        }
                    }
                }
            }
        }
    }
} // GenericYtmSections
}
