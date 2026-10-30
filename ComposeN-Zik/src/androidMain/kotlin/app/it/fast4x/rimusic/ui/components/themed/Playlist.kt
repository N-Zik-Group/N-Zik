package app.it.fast4x.rimusic.ui.components.themed

import app.n_zik.android.core.database.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.colorPalette
import app.n_zik.android.core.database.Database

import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.n_zik.android.core.coil.thumbnail
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.res.stringResource

@Composable
fun Playlist(
    playlist: Playlist,
    songCount: Int,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    disableScrollingText: Boolean,
    thumbnailUrl: String? = null,
) {
    val thumbnails by remember {
        Database.songPlaylistMapTable
                .sortSongsByPlayTime( playlist.id )
                .distinctUntilChanged()
                .map { list ->
                    // Same null-filtered selection as the base PlaylistItem, so the
                    // header collage matches the library/stats collages.
                    list.mapNotNull { it.thumbnailUrl }.takeLast( 4 ).map { url ->
                        url.thumbnail( thumbnailSizePx / 2 )
                    }
                }
    }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)

    PlaylistItem(
        thumbnailContent = {
            if (thumbnailUrl != null) {
                ImageCacheFactory.AsyncImage(
                    shape = null,
                    thumbnailUrl = thumbnailUrl.thumbnail(thumbnailSizePx),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else if (thumbnails.size in 1..3) {
                // Base ThumbnailRenderer contract: fewer than four covers render a
                // single full-size cover instead of a partial two-by-two collage.
                ImageCacheFactory.AsyncImage(
                    shape = null,
                    thumbnailUrl = thumbnails.first().thumbnail(thumbnailSizePx),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    //modifier = it KOTLIN 2
                )
            } else if (thumbnails.size >= 4) {
                Box(
                    modifier = Modifier // KOTLIN 2
                        .fillMaxSize()
                ) {
                    listOf(
                        Alignment.TopStart,
                        Alignment.TopEnd,
                        Alignment.BottomStart,
                        Alignment.BottomEnd
                    ).forEachIndexed { index, alignment ->
                        ImageCacheFactory.AsyncImage(
                            shape = null,
                            thumbnailUrl = thumbnails.getOrNull(index),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(alignment)
                                .size(thumbnailSizeDp / 2)
                        )
                    }
                }
            }
        },
        // Locked badge as caller overlay so it renders on top of the base
        // composable's dark scrim.
        thumbnailOverlay = {
            if (playlist.isYoutubePlaylist && !playlist.isEditable){
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Image(
                        painter = painterResource(R.drawable.locked),
                        colorFilter = ColorFilter.tint(Color.Red),
                        modifier = Modifier
                            .padding(all = 5.dp)
                            .background(colorPalette().text, CircleShape)
                            .padding(all = 5.dp)
                            .size(18.dp),
                        contentDescription = stringResource(R.string.cd_background_image),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        },
        songCount = songCount,
        name = cleanPrefix(playlist.name),
        channelName = null,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
        disableScrollingText = disableScrollingText,
        browseId = playlist.browseId,
        isYoutubePlaylist = playlist.isYoutubePlaylist,
        isEditable = playlist.isEditable
    )
}


