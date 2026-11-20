package app.n_zik.android.components.ui.screens.home.quickpicks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.glassMoodCardBackground
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.song.SongItemMenu
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.ShimmerHost
import app.n_zik.android.ui.components.themed.TextPlaceholder
import app.n_zik.android.ui.components.themed.Title
import app.n_zik.android.ui.items.AlbumItem
import app.n_zik.android.ui.items.ArtistItem
import app.n_zik.android.ui.items.ArtistItemPlaceholder
import app.n_zik.android.ui.items.PlaylistItem
import app.n_zik.android.ui.items.VideoItem
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.utils.semiBold
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import app.n_zik.android.core.database.BookmarkStateManager
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.HomePage
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlin.random.Random
import app.n_zik.android.ui.items.AlbumItemPlaceholder
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.utils.shimmerEffect
import app.n_zik.android.ui.items.PlaylistItemPlaceholder
import app.n_zik.android.ui.items.VideoItemPlaceholder
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.enums.DownloadedStateMedia
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@UnstableApi
@Composable
fun HomeBottomShimmer(
    albumThumbnailSizeDp: Dp,
    artistThumbnailSizeDp: Dp,
    endPaddingValues: PaddingValues
) {
    Column {
        ShimmerHost {
            repeat(3) {
                // Album/Playlist Section Placeholder
                TextPlaceholder(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 16.dp, bottom = 8.dp)
                )
                LazyRow(contentPadding = endPaddingValues) {
                    items(5, key = { it }, contentType = { "placeholder" }) {
                        AlbumItemPlaceholder(
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                // Artist Section Placeholder
                TextPlaceholder(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 16.dp, bottom = 8.dp)
                )
                LazyRow(contentPadding = endPaddingValues) {
                    items(5, key = { it }, contentType = { "placeholder" }) {
                        ArtistItemPlaceholder(
                            thumbnailSizeDp = artistThumbnailSizeDp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YtmSectionItems(
    section: HomePage.Section,
    titleOverride: String? = null,
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
    if (section.items.isNotEmpty() && section.items.firstOrNull()?.key != null) {
        val isSongOnly = section.items.all { item -> item is Innertube.SongItem }

        Column {
            if (showTitle) {
                Title(
                    title = titleOverride ?: section.title,
                    enableClick = false,
                    onClick = null,
                    verticalPadding = 16.dp
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

                // Download state cache
                val downloadsMapState by MyDownloadHelper.downloads.collectAsStateWithLifecycle(initialValue = MyDownloadHelper.downloads.value, context = NzikDispatchers.DATA)
                val downloadedIds by remember {
                    derivedStateOf {
                        downloadsMapState.values
                            .filter { it.state == Download.STATE_COMPLETED }
                            .mapTo(HashSet()) { it.request.id }
                    }
                }
                val downloadStatesMap by remember {
                    derivedStateOf {
                        songItems.associate { item ->
                            val songId = item.key ?: ""
                            songId to when {
                                songId in downloadedIds -> DownloadedStateMedia.DOWNLOADED
                                else -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                            }
                        }
                    }
                }

                CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(3),
                    flingBehavior = ScrollableDefaults.flingBehavior(),
                    contentPadding = endPaddingValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.itemsVerticalPadding * 3 * 9)
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
                        val binder = LocalPlayerServiceBinder.current
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
                        items(if (forcePlaceholder) 9 else 3, key = { "shimmer_$it" }, contentType = { "placeholder" }) { index ->
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
                                SongItemPlaceholder(Modifier.width(itemInHorizontalGridWidth).animateItem())
                            }
                        }
                    }
                }
                } // CompositionLocalProvider
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
                val cards = remember(entries) { ytmShelfCards(entries) }
                LazyRow(contentPadding = endPaddingValues) {
                    items(entries, key = { it.key ?: it.hashCode() }, contentType = { "item" }) { item ->
                        if (forcePlaceholder) {
                            YtmShelfCardPlaceholder(
                                ytmShelfCards(listOf(item)).single(), albumThumbnailSizeDp, playlistThumbnailSizeDp,
                                Modifier.animateItem(), item = item,
                            )
                            return@items
                        }
                        when (item) {
                            is Innertube.SongItem -> {
                                val binder = LocalPlayerServiceBinder.current
                                val menuState = LocalMenuState.current
                                val song = item.asSong ?: Song.makePlaceholder("")
                                AlbumItem(
                                    thumbnailUrl = item.thumbnail?.url,
                                    title = item.info?.name,
                                    authors = item.authors?.artistEntryNames()?.joinToString(", "),
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
                                val menuState = LocalMenuState.current
                                AlbumItem(
                                    album = item,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true,
                                    bookmarkState = sectionAlbumBookmarkStatesMap[item.key],
                                    observeBookmarkState = false,
                                    modifier = Modifier
                                        .clip(uiRoundnessShape())
                                        .combinedClickable(
                                            onClick = { onAlbumClick(item.key) },
                                            onLongClick = {
                                                menuState.display { OnlineAlbumItemMenu(navController = navController, album = item).MenuComponent() }
                                            }
                                        ).animateItem(),
                                    disableScrollingText = disableScrollingText
                                )
                            }
                            is Innertube.ArtistItem -> {
                                val menuState = LocalMenuState.current
                                // Album card format (album-size thumbnail, name centered
                                // below) so artist items match the album geometry of
//
                                // the other cards.
                                ArtistItem(
                                    artist = item,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true,
                                    bookmarkState = sectionArtistBookmarkStatesMap[item.key],
                                    observeBookmarkState = false,
                                    modifier = Modifier
                                        .clip(uiRoundnessShape())
                                        .combinedClickable(
                                            onClick = { onArtistClick(item.key) },
                                            onLongClick = {
                                                menuState.display { OnlineArtistItemMenu(navController = navController, artist = item).MenuComponent() }
                                            }
                                        ).animateItem(),
                                    disableScrollingText = disableScrollingText
                                )
                            }
                            is Innertube.PlaylistItem -> {
                                val menuState = LocalMenuState.current
                                PlaylistItem(
                                    playlist = item,
                                    thumbnailSizePx = playlistThumbnailSizePx,
                                    thumbnailSizeDp = playlistThumbnailSizeDp,
                                    alternative = true,
                                    showSongsCount = false,
                                    isYoutubePlaylist = true,
                                    isBookmarked = sectionPlaylistBookmarkStatesMap[item.key],
                                    modifier = Modifier
                                        .clip(uiRoundnessShape())
                                        .combinedClickable(
                                            onClick = { onPlaylistClick(item.key) },
                                            onLongClick = {
                                                menuState.display { OnlinePlaylistItemMenu(navController = navController, playlist = item).MenuComponent() }
                                            }
                                        ).animateItem(),
                                    disableScrollingText = disableScrollingText
                                )
                            }
                            is Innertube.VideoItem -> {
                                val binder = LocalPlayerServiceBinder.current
                                val menuState = LocalMenuState.current
                                VideoItem(
                                    video = item,
                                    thumbnailHeightDp = albumThumbnailSizeDp,
                                    thumbnailWidthDp = (albumThumbnailSizeDp * 16 / 9),
                                    likeState = sectionVideoLikeStatesMap[item.key],
                                    disableScrollingText = disableScrollingText,
                                    alternative = true,
                                    modifier = Modifier
                                        .clip(uiRoundnessShape())
                                        .combinedClickable(
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
                    
                    if ((!forcePlaceholder && isLoading) || (forcePlaceholder && entries.isEmpty())) {
                        items(if (forcePlaceholder) 5 else 3, key = { "shimmer_$it" }, contentType = { "placeholder" }) { index ->
                            ShimmerHost {
                                YtmShelfCardPlaceholder(
                                    cards[index % cards.size], albumThumbnailSizeDp, playlistThumbnailSizeDp,
                                    Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class YtmShelfPlaceholderKind { Album, Artist, Playlist, Video, Song }

internal data class YtmShelfCard(
    val kind: YtmShelfPlaceholderKind,
    val metadataLines: Int = 0,
    val showTitle: Boolean = true,
)

internal fun ytmShelfCards(
    items: List<Innertube.Item?>,
    videosAsAlbums: Boolean = false,
): List<YtmShelfCard> = items.mapNotNull { item ->
    when (item) {
        is Innertube.ArtistItem -> YtmShelfCard(YtmShelfPlaceholderKind.Artist, visibleLineCount(item.subscribersCountText))
        is Innertube.PlaylistItem -> YtmShelfCard(
            YtmShelfPlaceholderKind.Playlist, visibleLineCount(item.channel?.name), showTitle = !item.title.isNullOrBlank(),
        )
        is Innertube.VideoItem -> if (videosAsAlbums) {
            YtmShelfCard(YtmShelfPlaceholderKind.Album, visibleLineCount(item.authors.artistEntryNames().joinToString(", ")))
        } else {
            YtmShelfCard(YtmShelfPlaceholderKind.Video, visibleLineCount(item.authors.artistEntryNames().joinToString(", "), item.viewsText))
        }
        is Innertube.AlbumItem -> YtmShelfCard(YtmShelfPlaceholderKind.Album, visibleLineCount(item.year))
        is Innertube.SongItem -> YtmShelfCard(YtmShelfPlaceholderKind.Album, visibleLineCount(item.authors.artistEntryNames().joinToString(", ")))
        else -> null
    }
}.ifEmpty { listOf(YtmShelfCard(YtmShelfPlaceholderKind.Album)) }

private fun visibleLineCount(vararg metadata: String?): Int = metadata.count { !it.isNullOrBlank() }

@Composable
internal fun YtmShelfCardPlaceholder(
    card: YtmShelfCard,
    albumSize: Dp,
    playlistSize: Dp,
    modifier: Modifier = Modifier,
    item: Innertube.Item? = null,
) {
    when (card.kind) {
        YtmShelfPlaceholderKind.Artist -> ArtistItemPlaceholder(
            albumSize, modifier, alternative = true, showSubscribers = card.metadataLines > 0,
            title = item?.title, subscribers = (item as? Innertube.ArtistItem)?.subscribersCountText,
        )
        YtmShelfPlaceholderKind.Playlist -> PlaylistItemPlaceholder(
            playlistSize, modifier, alternative = true, showChannel = card.metadataLines > 0,
            showTitle = card.showTitle, title = item?.title, channel = (item as? Innertube.PlaylistItem)?.channel?.name,
        )
        YtmShelfPlaceholderKind.Video -> {
            val video = item as? Innertube.VideoItem
            VideoItemPlaceholder(albumSize, albumSize * 16 / 9, modifier, alternative = true,
                metadataLines = card.metadataLines, title = video?.title,
                uploader = video?.authors.artistEntryNames().joinToString(", ").takeIf { video != null },
                views = video?.viewsText)
        }
        YtmShelfPlaceholderKind.Album, YtmShelfPlaceholderKind.Song -> {
            val secondary = when (item) {
                is Innertube.AlbumItem -> item.year
                is Innertube.SongItem -> item.authors.artistEntryNames().joinToString(", ")
                is Innertube.VideoItem -> item.authors.artistEntryNames().joinToString(", ")
                else -> null
            }
            AlbumItemPlaceholder(albumSize, modifier, alternative = true, metadataLines = card.metadataLines,
                title = item?.title, metadata = secondary?.let(::listOf),
                finalMetadataIsYear = item !is Innertube.SongItem && item !is Innertube.VideoItem)
        }
    }
}

@Composable
fun MoodItemColored(
    mood: Innertube.Mood.Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val moodColor by remember { derivedStateOf { Color(mood.stripeColor) } }
    BaseChipItemColored(
        title = mood.title,
        stripeColor = moodColor,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun ChipItemColored(
    chip: Innertube.Chip,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chipColor = remember { Color(255, Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)) }
    BaseChipItemColored(
        title = chip.title,
        stripeColor = chipColor,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun BaseChipItemColored(
    title: String?,
    stripeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shimmer: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .moodCardBounds()
            .glassMoodCardBackground(
                backgroundColor = colorPalette().background0,
                cardColor = colorPalette().background4,
                shape = thumbnailShape(),
            )
            .clickable(enabled = !shimmer, onClick = onClick)
    ) {
        val stripe = Modifier.width(5.dp).fillMaxHeight()
        Box(if (shimmer) stripe.shimmerEffect() else stripe.background(stripeColor))
        val style = typography().xs.semiBold.copy(color = colorPalette().text)
        val textModifier = Modifier.padding(horizontal = 10.dp)
        if (shimmer) ItemTextPlaceholder(title, style, textModifier, maxLines = 2)
        else BasicText(title.orEmpty(), style = style, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = textModifier)
    }
}

@Composable
internal fun MoodCardPlaceholder(modifier: Modifier = Modifier, title: String? = null) {
    BaseChipItemColored(title, Color.Transparent, onClick = {}, modifier = modifier, shimmer = true)
}

@Composable
private fun Modifier.moodCardBounds(): Modifier = padding(5.dp).width(160.dp).height(56.dp).clip(thumbnailShape())
