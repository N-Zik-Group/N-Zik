// Ported from RiPlay (AGPL-3.0) — base: it.fast4x.riplay.extensions.musicbrainz.ui.ArtistInsightsScreen
package app.n_zik.android.components.musicbrainz.insights

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.it.fast4x.rimusic.cleanPrefix
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
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.colorPalette
import app.n_zik.android.thumbnailShape
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.musicbrainz.KeywordChips
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.musicbrainz.utils.toFlagEmoji
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.Preference.HOME_ALBUM_ITEM_SIZE
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.uiRoundnessShape

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistInsightsScreen(
    navController: NavController,
    artistId: String
) {
    val viewModel: ArtistInsightsViewModel = viewModel(factory = ArtistInsightsViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val gridItemSize = ItemSize.init(HOME_ALBUM_ITEM_SIZE)

    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
    }

    val songIds = remember(state.topTracks) { state.topTracks.map { it.id } }
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
        val artist = state.artist ?: return
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colorPalette().background0),
            contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "header") {
                ArtistHeader(artist = artist)
            }

            // Prefer the Wikipedia bio; fall back to the stored YouTube description.
            val bio = artist.wikipediaBio ?: artist.description
            if (!bio.isNullOrBlank()) {
                item(key = "bio") {
                    InfoCard(title = stringResource(R.string.mb_insights_biography), icon = R.drawable.information) {
                        Text(
                            text = bio,
                            style = typography().xs
                        )
                    }
                }
            }

            if (state.relations.isNotEmpty()) {
                item(key = "relations") {
                    InfoCard(title = stringResource(R.string.mb_insights_members_collabs), icon = R.drawable.people) {
                        state.relations.forEach { rel ->
                            RelatedArtistRow(
                                artist = rel.artist,
                                relationType = rel.relationType,
                                onLongClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.display {
                                        OnlineArtistItemMenu(
                                            navController = navController,
                                            artist = rel.artist.toInnertube()
                                        ).MenuComponent()
                                    }
                                },
                                onClick = rel.localArtistId?.let { id ->
                                    { navController.navigate("artist/$id") }
                                }
                            )
                        }
                    }
                }
            }

            val keywords = artist.keywords
            if (keywords.isNotEmpty()) {
                item(key = "keywords") {
                    InfoCard(title = stringResource(R.string.mb_insights_tags_genres), icon = R.drawable.sparkles) {
                        KeywordChips(keywords)
                    }
                }
            }

            if (artist.rating != null) {
                item(key = "rating") {
                    InfoCard(title = stringResource(R.string.mb_insights_popularity), icon = R.drawable.star_brilliant) {
                        RatingBar(rating = artist.rating, votes = artist.ratingVotes)
                    }
                }
            }

            state.stats?.let { stats ->
                item(key = "stats") {
                    InfoCard(title = stringResource(R.string.mb_insights_your_listening), icon = R.drawable.equalizer) {
                        ArtistStatsRow(stats)
                    }
                }
            }

            if (state.topTracks.isNotEmpty()) {
                item(key = "top_songs") {
                    InfoCard(title = stringResource(R.string.mb_insights_top_songs), icon = R.drawable.musical_notes) {
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

            if (state.topAlbums.isNotEmpty()) {
                item(key = "top_albums") {
                    InfoCard(title = stringResource(R.string.mb_insights_top_albums), icon = R.drawable.album) {
                        state.topAlbums.forEachIndexed { index, album ->
                            AlbumItem(
                                album = album,
                                thumbnailSizePx = 0,
                                thumbnailSizeDp = Dimensions.thumbnails.song,
                                alternative = false,
                                showAuthors = true,
                                showInfo = true,
                                yearCentered = false,
                                disableScrollingText = false,
                                modifier = Modifier
                                    .clip(uiRoundnessShape())
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("album/${album.id}")
                                        },
                                        onLongClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.display {
                                                OnlineAlbumItemMenu(
                                                    navController = navController,
                                                    album = album.toInnertube()
                                                ).MenuComponent()
                                            }
                                        }
                                    ),
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
                                }
                            )
                        }
                    }
                }
            }

            if (state.albums.isNotEmpty()) {
                item(key = "albums") {
                    InfoCard(title = stringResource(R.string.mb_insights_local_library), icon = R.drawable.library) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(state.albums, key = { it.id }) { album ->
                                AlbumItem(
                                    album = album,
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
                                                navController.navigate("album/${album.id}")
                                            },
                                            onLongClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.display {
                                                    OnlineAlbumItemMenu(
                                                        navController = navController,
                                                        album = album.toInnertube()
                                                    ).MenuComponent()
                                                }
                                            }
                                        ),
                                    thumbnailOverlay = {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            album.rating?.let { rating ->
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

            if (state.externalLinks.isNotEmpty() || artist.wikipediaUrl != null || artist.mbId != null) {
                item(key = "links") {
                    InfoCard(title = stringResource(R.string.mb_insights_links), icon = R.drawable.link) {
                        artist.wikipediaUrl?.let {
                            ExternalLinkRow(
                                stringResource(R.string.mb_wikipedia),
                                it,
                                R.drawable.globe
                            )
                        }
                        artist.mbId?.let {
                            ExternalLinkRow(
                                stringResource(R.string.mb_musicbrainz),
                                "https://musicbrainz.org/artist/$it",
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
private fun ArtistHeader(artist: Artist) {
    val configuration = LocalConfiguration.current
    val isNarrow = configuration.screenWidthDp < 600

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ImageCacheFactory.Thumbnail(
            thumbnailUrl = artist.thumbnailUrl,
            modifier = Modifier
                .size(if (isNarrow) 160.dp else 200.dp)
                .clip(artistThumbnailShape())
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = cleanPrefix(artist.name.orEmpty()),
            style = typography().l.semiBold
        )

        Spacer(Modifier.height(8.dp))

        val infoText = buildList {
            artist.countryCode?.let {
                add("$it ${it.toFlagEmoji()}")
            }
            artist.beginYear?.let { year ->
                add("$year")
            }
            artist.artistType?.let { add(it) }
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
private fun ArtistStatsRow(stats: ArtistStats) {
    Row(modifier = Modifier.fillMaxWidth()) {
        StatItem(stringResource(R.string.mb_stat_listens), stats.playCount.toString(), modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
        StatItem(stringResource(R.string.mb_stat_albums), stats.distinctAlbumsCount.toString(), modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
        StatItem(stringResource(R.string.mb_stat_liked_songs), stats.likedSongsCount.toString(), modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
        StatItem(stringResource(R.string.mb_stat_bookmarked_albums), stats.bookmarkedAlbumsCount.toString(), modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
        StatItem(stringResource(R.string.mb_stat_time), formatPlayTime(stats.totalPlayTimeMs), modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = value,
            style = typography().xs.semiBold
        )
        Text(
            text = label,
            style = typography().xxs,
            color = colorPalette().textSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(iterations = Int.MAX_VALUE)
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
