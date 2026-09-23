// Ported from RiPlay (AGPL-3.0) — base: it.fast4x.riplay.extensions.musicbrainz.ui.AlbumInsightsScreen
package app.n_zik.android.components.musicbrainz.insights

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.components.tab.ItemSize
import app.it.fast4x.rimusic.ui.components.themed.Loader
import app.it.fast4x.rimusic.ui.items.AlbumItem
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.onOverlay
import app.it.fast4x.rimusic.ui.styling.overlay
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.musicbrainz.KeywordChips
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.Preference.HOME_ALBUM_ITEM_SIZE
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.thumbnailShape
import app.n_zik.android.uiRoundnessShape

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumInsightsScreen(
    navController: NavController,
    albumId: String
) {
    val viewModel: AlbumInsightsViewModel = viewModel(factory = AlbumInsightsViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val gridItemSize = ItemSize.init(HOME_ALBUM_ITEM_SIZE)

    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }

    val songIds = remember(state.tracks) { state.tracks.map { it.id } }
    val likeStatesMap by remember(songIds) {
        LikeStateManager.getLikeStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val playlistStatesMap by remember(songIds) {
        PlaylistStateManager.getPlaylistStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorPalette().background0),
            contentAlignment = Alignment.Center
        ) {
            Loader()
        }
    } else {
        val album = state.album ?: return
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colorPalette().background0),
            contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "header") {
                AlbumHeader(
                    album = album,
                    artist = state.artist,
                    onArtistClick = state.artist?.id?.let { id ->
                        { navController.navigate("artist/$id") }
                    }
                )
            }

            val bio = album.wikipediaInfo?.takeIf { it.isNotBlank() } ?: album.description?.takeIf { it.isNotBlank() }
            if (bio != null) {
                item(key = "info") {
                    InfoCard(title = stringResource(R.string.information), icon = R.drawable.information) {
                        Text(
                            text = bio,
                            style = typography().xs
                        )
                    }
                }
            }

            val keywords = album.keywords
            if (keywords.isNotEmpty()) {
                item(key = "keywords") {
                    InfoCard(title = stringResource(R.string.mb_insights_tags_genres), icon = R.drawable.sparkles) {
                        KeywordChips(keywords)
                    }
                }
            }

            if (album.rating != null) {
                item(key = "rating") {
                    InfoCard(title = stringResource(R.string.mb_insights_popularity), icon = R.drawable.star_brilliant) {
                        RatingBar(rating = album.rating, votes = album.ratingVotes)
                    }
                }
            }

            state.stats?.let { stats ->
                if (stats.tracksCount > 0) {
                    item(key = "stats") {
                        InfoCard(title = stringResource(R.string.mb_insights_your_listening), icon = R.drawable.equalizer) {
                            AlbumStatsRow(stats)
                        }
                    }
                }
            }

            if (state.tracks.isNotEmpty()) {
                item(key = "tracks") {
                    InfoCard(
                        title = stringResource(R.string.mb_stat_songs),
                        icon = R.drawable.musical_notes
                    ) {
                        state.tracks.forEachIndexed { index, song ->
                            SwipeablePlaylistItem(
                                mediaItem = song.asMediaItem,
                                onPlayNext = {
                                    binder?.player?.addNext(song.asMediaItem)
                                }
                            ) {
                                SongItem(
                                    song = song,
                                    isLiked = likeStatesMap[song.id],
                                    inPlaylist = playlistStatesMap[song.id],
                                    navController = navController,
                                    showThumbnail = true,
                                    backgroundColor = colorPalette().background2,
                                    thumbnailOverlay = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(thumbnailShape())
                                                .background(colorPalette().overlay)
                                        ) {
                                            BasicText(
                                                text = "${index + 1}",
                                                style = typography().s.semiBold.center.color(colorPalette().onOverlay),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        }
                                    },
                                    onClick = {
                                        binder?.stopRadio()
                                        binder?.player?.forcePlay(song.asMediaItem)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (state.topTracks.isNotEmpty()) {
                item(key = "top_songs") {
                    InfoCard(
                        title = stringResource(R.string.mb_insights_top_songs),
                        icon = R.drawable.musical_notes
                    ) {
                        state.topTracks.forEachIndexed { index, song ->
                            SwipeablePlaylistItem(
                                mediaItem = song.asMediaItem,
                                onPlayNext = {
                                    binder?.player?.addNext(song.asMediaItem)
                                }
                            ) {
                                SongItem(
                                    song = song,
                                    isLiked = likeStatesMap[song.id],
                                    inPlaylist = playlistStatesMap[song.id],
                                    navController = navController,
                                    showThumbnail = true,
                                    backgroundColor = colorPalette().background2,
                                    thumbnailOverlay = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(thumbnailShape())
                                                .background(colorPalette().overlay)
                                        ) {
                                            BasicText(
                                                text = "${index + 1}",
                                                style = typography().s.semiBold.center.color(colorPalette().onOverlay),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        }
                                    },
                                    onClick = {
                                        binder?.stopRadio()
                                        binder?.player?.forcePlay(song.asMediaItem)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (state.otherAlbums.isNotEmpty()) {
                item(key = "other_albums") {
                    InfoCard(title = stringResource(R.string.mb_insights_other_albums), icon = R.drawable.album) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(state.otherAlbums, key = { it.id }) { otherAlbum ->
                                AlbumItem(
                                    album = otherAlbum,
                                    thumbnailSizePx = gridItemSize.size.px,
                                    thumbnailSizeDp = gridItemSize.size.dp,
                                    alternative = true,
                                    showAuthors = true,
                                    showInfo = true,
                                    yearCentered = true,
                                    disableScrollingText = false,
                                    modifier = Modifier
                                        .clip(uiRoundnessShape())
                                        .combinedClickable(
                                            onClick = {
                                                navController.navigate("album/${otherAlbum.id}")
                                            },
                                            onLongClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.display {
                                                    OnlineAlbumItemMenu(
                                                        navController = navController,
                                                        album = otherAlbum.toInnertube()
                                                    ).MenuComponent()
                                                }
                                            }
                                        ),
                                    thumbnailOverlay = {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            otherAlbum.rating?.let { rating ->
                                                Row(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                        .background(Color.Black.copy(alpha = 0.6f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.star_brilliant),
                                                        contentDescription = null,
                                                        tint = Color(0xFFFFC107),
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    BasicText(
                                                        text = String.format("%.1f", rating),
                                                        style = typography().xxs.semiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (state.singlesAndEps.isNotEmpty()) {
                item(key = "singles_and_eps") {
                    InfoCard(title = stringResource(R.string.mb_insights_singles_eps), icon = R.drawable.album) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(state.singlesAndEps, key = { it.id }) { single ->
                                AlbumItem(
                                    album = single,
                                    thumbnailSizePx = gridItemSize.size.px,
                                    thumbnailSizeDp = gridItemSize.size.dp,
                                    alternative = true,
                                    showAuthors = true,
                                    showInfo = true,
                                    yearCentered = true,
                                    disableScrollingText = false,
                                    modifier = Modifier
                                        .clip(uiRoundnessShape())
                                        .combinedClickable(
                                            onClick = {
                                                navController.navigate("album/${single.id}")
                                            },
                                            onLongClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.display {
                                                    OnlineAlbumItemMenu(
                                                        navController = navController,
                                                        album = single.toInnertube()
                                                    ).MenuComponent()
                                                }
                                            }
                                        ),
                                    thumbnailOverlay = {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            single.rating?.let { rating ->
                                                Row(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                        .background(Color.Black.copy(alpha = 0.6f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.star_brilliant),
                                                        contentDescription = null,
                                                        tint = Color(0xFFFFC107),
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    BasicText(
                                                        text = String.format("%.1f", rating),
                                                        style = typography().xxs.semiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (state.externalLinks.isNotEmpty() || album.wikipediaUrl != null || album.mbId != null) {
                item(key = "links") {
                    InfoCard(title = stringResource(R.string.mb_insights_links), icon = R.drawable.link) {
                        album.wikipediaUrl?.let {
                            ExternalLinkRow(
                                stringResource(R.string.mb_wikipedia),
                                it,
                                R.drawable.globe
                            )
                        }
                        album.mbId?.let {
                            ExternalLinkRow(
                                stringResource(R.string.mb_musicbrainz),
                                "https://musicbrainz.org/release-group/$it",
                                R.drawable.globe
                            )
                        }
                        state.externalLinks.forEach { link ->
                            ExternalLinkRow(
                                link.platform,
                                link.url
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    album: Album,
    artist: Artist?,
    onArtistClick: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ImageCacheFactory.Thumbnail(
            thumbnailUrl = album.thumbnailUrl,
            modifier = Modifier
                .size(200.dp)
                .clip(thumbnailShape())
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = cleanPrefix(album.title.orEmpty()),
            style = typography().l.semiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = cleanPrefix(artist?.name ?: album.authorsText.orEmpty()),
            style = typography().m,
            color = colorPalette().textSecondary,
            modifier = if (onArtistClick != null) Modifier
                .clip(uiRoundnessShape())
                .clickable { onArtistClick() } else Modifier
        )

        Spacer(Modifier.height(8.dp))

        val infoText = buildList {
            (album.originalYear?.toString() ?: album.year)?.let { add(it) }
            album.albumType?.let { add(it) }
        }.joinToString(" • ")

        if (infoText.isNotBlank()) {
            Text(
                text = infoText,
                style = typography().s,
                color = colorPalette().textSecondary
            )
        }
    }
}

@Composable
private fun AlbumStatsRow(stats: AlbumStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(stringResource(R.string.mb_stat_songs), stats.tracksCount.toString())
        StatItem(stringResource(R.string.mb_stat_listens), stats.playCount.toString())
        StatItem(stringResource(R.string.mb_stat_liked_songs), stats.likedSongsCount.toString())
        StatItem(stringResource(R.string.mb_stat_time), formatPlayTime(stats.totalPlayTimeMs))
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = typography().xs.semiBold
        )
        Text(
            text = label,
            style = typography().xxs,
            color = colorPalette().textSecondary
        )
    }
}

private fun formatPlayTime(ms: Long): String {
    val hours = ms / 3_600_000
    return when {
        hours < 1 -> "${ms / 60_000}min"
        hours < 24 -> "${hours}h"
        else -> "${hours / 24}d"
    }
}
