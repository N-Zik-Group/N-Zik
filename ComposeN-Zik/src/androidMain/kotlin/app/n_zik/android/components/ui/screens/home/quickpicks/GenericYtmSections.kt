package app.n_zik.android.components.ui.screens.home.quickpicks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import app.n_zik.android.components.menu.song.SongItemMenu
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.n_zik.android.components.SongItem
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.core.database.BookmarkStateManager
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.models.Song
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.ShimmerHost
import app.n_zik.android.ui.components.themed.TextPlaceholder
import app.n_zik.android.ui.components.themed.TitleMiniSection
import app.n_zik.android.ui.items.AlbumItem
import app.n_zik.android.ui.items.ArtistItem
import app.n_zik.android.ui.items.PlaylistItem
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.items.VideoItem
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.color
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.semiBold
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.HomePage

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
                                    observeBookmarkState = false,
                                    disableScrollingText = disableScrollingText,
                                    modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                        onClick = { navController.navigate("${NavRoutes.album.name}/${item.key}") },
                                        onLongClick = { menuState.display { OnlineAlbumItemMenu(navController = navController, album = item).MenuComponent() } }
                                    ).animateItem()
                                )
                            }
                            is Innertube.ArtistItem -> {
                                // Match the album card geometry for artists.
                                ArtistItem(
                                    artist = item,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true,
                                    bookmarkState = sectionArtistBookmarkStatesMap[item.key],
                                    observeBookmarkState = false,
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
                                // Retrospectives use square album artwork and retain video actions.
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
                }
            }
        }

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
    }
}
