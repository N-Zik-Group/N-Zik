package app.n_zik.android.ui.components.themed


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.isPlaylistBookmarked

import app.n_zik.android.cleanPrefix
import app.n_zik.android.models.Playlist
import app.n_zik.android.ui.items.ItemArtworkPlaceholder
import app.n_zik.android.ui.items.PlaylistItem
import app.n_zik.android.ui.items.PlaylistReadOnlyBadge
import app.n_zik.android.ui.items.rememberPlaylistSongThumbnails
import app.n_zik.android.thumbnailShape
import app.n_zik.android.core.coil.thumbnail

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
    coverOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val thumbnails = if (thumbnailUrl == null) {
        rememberPlaylistSongThumbnails(playlist.id, thumbnailSizePx / 2)
    } else null

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
            } else if (thumbnails == null) {
                ItemArtworkPlaceholder(thumbnailSizeDp, alternative, thumbnailShape())
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
        thumbnailOverlay = {
            Box(Modifier.fillMaxSize(), content = coverOverlay)
            PlaylistReadOnlyBadge(playlist)
        },
        songCount = songCount,
        name = cleanPrefix(playlist.name),
        channelName = null,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
        showInfo = showName,
        disableScrollingText = disableScrollingText,
        browseId = playlist.browseId,
        isYoutubePlaylist = isPlaylistBookmarked(playlist),
        isEditable = playlist.isEditable
    )
}


