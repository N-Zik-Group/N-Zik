package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import app.n_zik.android.MONTHLY_PREFIX
import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.glassImageRim
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.thumbnailShape
import app.n_zik.android.ui.items.rememberPlaylistSongThumbnails
import app.n_zik.android.ui.styling.overlay
import app.n_zik.android.utils.checkFileExists

@Composable
internal fun RewindArtwork(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onError: (() -> Unit)? = null,
    shape: Shape = RectangleShape,
) {
    ImageCacheFactory.AsyncImage(
        thumbnailUrl = imageUrl,
        shape = shape,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = {
            if (!imageUrl.isNullOrBlank()) onError?.invoke()
        }
    )
}

@Composable
internal fun RewindArtworkWithFallback(
    imageUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    background: Color = rewindColors.value.purple,
    foreground: Color = rewindColors.value.cream,
    onError: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(if (circular) CircleShape else RoundedCornerShape(8.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        val textScale = LocalRewindTextScale.current
        Text(
            text = title.trim().take(2).uppercase().ifBlank { "♪" },
            color = foreground.copy(alpha = 0.70f),
            fontSize = textScale.size(18.sp),
            fontWeight = FontWeight.Black
        )
        RewindArtwork(
            imageUrl = imageUrl,
            shape = if (circular) CircleShape else RoundedCornerShape(8.dp),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            onError = onError
        )
    }
}

/**
 * Origin indicator overlay for a playlist artwork, mirroring the statistics screen playlist
 * rows: the N-Zik launcher icon on local playlists, the pin on pinned playlists, the month
 * icon on monthly playlists, and the source icon on imported or YouTube playlists.
 *
 * Spec 2 adds the dedicated origin icons of the generated rewind-* playlists
 * (monthly -> month, yearly -> year, all-time -> notes, accent tint).
 */
@Composable
internal fun RewindPlaylistOriginIcon(
    name: String,
    browseId: String?,
    isYoutubePlaylist: Boolean,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val (painter, tint) = when {
        name.startsWith(PINNED_PREFIX, true) ->
            painterResource(R.drawable.pin_filled) to colorPalette().accent

        name.startsWith(MONTHLY_PREFIX, true) ->
            painterResource(R.drawable.stat_month) to colorPalette().accent

        RewindPlaylists.isMonthly(name) ->
            painterResource(R.drawable.stat_month) to colorPalette().accent

        RewindPlaylists.isYearly(name) ->
            painterResource(R.drawable.stat_year) to colorPalette().accent

        RewindPlaylists.isAlltime(name) ->
            painterResource(R.drawable.musical_notes) to colorPalette().accent

        browseId == "SPOTIFY_IMPORT" || browseId?.startsWith("SPOTIFY_IMPORT") == true ->
            painterResource(R.drawable.spotify) to Color.Unspecified

        browseId == "RIPLAY_IMPORT" || browseId?.startsWith("RIPLAY_IMPORT") == true ->
            painterResource(R.drawable.riplay) to Color.Unspecified

        isYoutubePlaylist || browseId?.startsWith("VL") == true ->
            painterResource(R.drawable.ytmusic) to Color.Red

        else ->
            painterResource(R.drawable.ic_launcher) to Color.Unspecified
    }
    Icon(
        painter = painter,
        contentDescription = stringResource(R.string.accessibility_origin_indicator),
        tint = tint,
        modifier = modifier.size(iconSize)
    )
}

/**
 * Playlist "logo" used on the top playlists slide — a 2x2 mosaic of the four
 * most-played track covers (a single cover when they all match), dimmed by the
 * app scrim with the origin indicator (N-Zik logo / pin / source icon)
 * top-right. The rank number is not drawn here: it lives in the row, like the
 * Deep Cuts slide.
 *
 * Same pipeline as the statistics screen and Home Library: custom playlist
 * thumbnail file, else the four most-played song thumbnails, rendered via
 * ImageCacheFactory.Thumbnail.
 */
@Composable
internal fun RewindPlaylistArtwork(
    playlistId: Long,
    title: String,
    name: String,
    browseId: String?,
    isYoutubePlaylist: Boolean,
    // Named sizeDp (not "size") so the parameter does not shadow Modifier.size().
    sizeDp: Dp = 46.dp,
    shape: Shape = thumbnailShape(),
    // When true, a playlist without artwork shows the app launcher box (ImageCacheFactory's
    // fallback) instead of the initials placeholder.
    emptyFallback: Boolean = false,
    // When false, the built-in dimming overlay is skipped — for callers that paint their
    // own scrim over the artwork (the rewind home month cells and year rows).
    dimOverlay: Boolean = true,
    modifier: Modifier = Modifier
) {
    val quadrant = sizeDp / 2
    val context = LocalContext.current
    val customThumbnail = remember(context, playlistId) {
        checkFileExists(context, "thumbnail/playlist_$playlistId")
    }
    val thumbnails = customThumbnail?.let(::listOf) ?: rememberPlaylistSongThumbnails(playlistId).orEmpty()

    val showFallback = emptyFallback && thumbnails.isEmpty()
    Box(
        modifier = modifier
            .clip(shape)
            .glassImageRim(shape)
            .background(rewindColors.value.ink),
        contentAlignment = Alignment.Center
    ) {
        when {
            showFallback ->
                Image(
                    painter = painterResource(R.drawable.ic_launcher_box),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            thumbnails.isEmpty() ->
                Text(
                    text = title.trim().take(2).uppercase().ifBlank { "♪" },
                    color = rewindColors.value.cream.copy(alpha = 0.70f),
                    fontSize = LocalRewindTextScale.current.size(18.sp),
                    fontWeight = FontWeight.Black
                )
            thumbnails.toSet().size == 1 ->
                ImageCacheFactory.Thumbnail(
                    shape = null,
                    thumbnailUrl = thumbnails.first(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            else ->
                listOf(
                    Alignment.TopStart,
                    Alignment.TopEnd,
                    Alignment.BottomStart,
                    Alignment.BottomEnd
                ).forEachIndexed { index, alignment ->
                    val thumbnail = thumbnails.getOrNull(index)
                    if (thumbnail != null) {
                        ImageCacheFactory.Thumbnail(
                            shape = null,
                            thumbnailUrl = thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(alignment)
                                .size(quadrant)
                        )
                    }
                }
        }
        if (!showFallback) {
            // Same dimming overlay as the statistics screen, without the rank: the rank
            // number is rendered by the row itself (Deep Cuts style). Skipped by callers
            // that paint their own scrim over the artwork.
            if (dimOverlay)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(colorPalette().overlay)
                )
            // The statistics screen also paints an origin indicator (N-Zik logo, pin,
            // source icon) over the artwork; it is kept top-right on the deck artworks.
            RewindPlaylistOriginIcon(
                name = name,
                browseId = browseId,
                isYoutubePlaylist = isYoutubePlaylist,
                iconSize = sizeDp * 0.30f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(sizeDp * 0.05f)
            )
        }
    }
}
