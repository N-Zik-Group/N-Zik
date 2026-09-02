package app.n_zik.android.components.ui.screens.home.quickpicks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
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
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.song.SongItemMenu
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.n_zik.android.isVideoEnabled
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.ShimmerHost
import app.it.fast4x.rimusic.ui.components.themed.TextPlaceholder
import app.it.fast4x.rimusic.ui.components.themed.Title
import app.it.fast4x.rimusic.ui.items.*
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.parseArtists
import app.it.fast4x.rimusic.utils.playVideo
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.HomePage
import kotlin.random.Random
import app.it.fast4x.rimusic.ui.items.AlbumItemPlaceholder

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
    isLoading: Boolean = false
) {
    if (section.items.isNotEmpty() && section.items.firstOrNull()?.key != null) {
        val isSongOnly = section.items.all { item -> item is Innertube.SongItem }

        Title(
            title = titleOverride ?: section.title,
            enableClick = false,
            onClick = null,
            verticalPadding = 16.dp
        )

        if (isSongOnly) {
            val songItems = section.items.filterIsInstance<Innertube.SongItem>()
            LazyHorizontalGrid(
                rows = GridCells.Fixed(3),
                flingBehavior = ScrollableDefaults.flingBehavior(),
                contentPadding = endPaddingValues,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimensions.itemsVerticalPadding * 3 * 9)
            ) {
                items(songItems, key = { it.key ?: it.hashCode() }, contentType = { "song" }) { item ->
                    val binder = LocalPlayerServiceBinder.current
                    SongItem(
                        song = item.asSong ?: Song.makePlaceholder(""),
                        navController = navController,
                        onClick = {
                            val mediaItem = item.asMediaItem
                            binder?.stopRadio()
                            binder?.player?.forcePlay(mediaItem)
                            binder?.player?.addMediaItems(songItems.map { s -> s.asMediaItem })
                        },
                        modifier = Modifier.width(itemInHorizontalGridWidth)
                    )
                }
                
                if (isLoading) {
                    items(3, key = { "shimmer_$it" }, contentType = { "placeholder" }) {
                        ShimmerHost {
                            AlbumItemPlaceholder(
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        } else {
            LazyRow(contentPadding = endPaddingValues) {
                items(section.items, key = { it?.key ?: it.hashCode() }, contentType = { "item" }) { item ->
                    when (item) {
                        is Innertube.SongItem -> {
                            val binder = LocalPlayerServiceBinder.current
                            val menuState = LocalMenuState.current
                            val song = item.asSong ?: Song.makePlaceholder("")
                            AlbumItem(
                                thumbnailUrl = item.thumbnail?.url,
                                title = item.info?.name,
                                authors = item.authors?.parseArtists()?.joinToString(", "),
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
                                    ),
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
                                modifier = Modifier
                                    .clip(uiRoundnessShape())
                                    .combinedClickable(
                                        onClick = { onAlbumClick(item.key) },
                                        onLongClick = {
                                            menuState.display { OnlineAlbumItemMenu(navController = navController, album = item).MenuComponent() }
                                        }
                                    ),
                                disableScrollingText = disableScrollingText
                            )
                        }
                        is Innertube.ArtistItem -> {
                            val menuState = LocalMenuState.current
                            ArtistItem(
                                artist = item,
                                thumbnailSizePx = songThumbnailSizePx,
                                thumbnailSizeDp = songThumbnailSizeDp,
                                alternative = false,
                                modifier = Modifier
                                    .width(200.dp)
                                    .clip(uiRoundnessShape())
                                    .combinedClickable(
                                        onClick = { onArtistClick(item.key) },
                                        onLongClick = {
                                            menuState.display { OnlineArtistItemMenu(navController = navController, artist = item).MenuComponent() }
                                        }
                                    ),
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
                                modifier = Modifier
                                    .clip(uiRoundnessShape())
                                    .combinedClickable(
                                        onClick = { onPlaylistClick(item.key) },
                                        onLongClick = {
                                            menuState.display { OnlinePlaylistItemMenu(navController = navController, playlist = item).MenuComponent() }
                                        }
                                    ),
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
                                disableScrollingText = disableScrollingText,
                                alternative = true,
                                modifier = Modifier
                                    .clip(uiRoundnessShape())
                                    .combinedClickable(
                                        onClick = {
                                            binder?.stopRadio()
                                            if (isVideoEnabled())
                                                binder?.player?.playVideo(item.asMediaItem)
                                            else
                                                binder?.player?.forcePlay(item.asMediaItem)
                                        },
                                        onLongClick = { menuState.display { VideoItemMenu(navController = navController, song = item.asSong).MenuComponent() } }
                                    )
                            )
                        }
                        null -> {}
                    }
                }
                
                if (isLoading) {
                    items(3, key = { "shimmer_$it" }, contentType = { "placeholder" }) {
                        ShimmerHost {
                            AlbumItemPlaceholder(
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
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
    val chipColor by remember { derivedStateOf<Color> { Color(255, Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)) } }
    BaseChipItemColored(
        title = chip.title,
        stripeColor = chipColor,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun BaseChipItemColored(
    title: String,
    stripeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(5.dp)
            .width(160.dp)
            .height(56.dp)
            .clip(thumbnailShape())
            .background(colorPalette().background4)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(stripeColor)
        )
        BasicText(
            text = title,
            style = typography().xs.semiBold.copy(color = colorPalette().text),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}
