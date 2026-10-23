package app.n_zik.android.components.musicbrainz.insights

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.R
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.core.coil.ImageCacheFactory
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint

/** Builds a synthetic [Innertube.AlbumItem] from a local album so the standard online album menu can be reused for MusicBrainz-sourced rows. */
fun Album.toInnertube(): Innertube.AlbumItem =
    Innertube.AlbumItem(
        info = Innertube.Info(
            name = title,
            endpoint = NavigationEndpoint.Endpoint.Browse(browseId = id)
        ),
        authors = null,
        year = year,
        songCount = null,
        playlistId = null,
        description = null,
        thumbnail = null
    )

/** Builds a synthetic [Innertube.ArtistItem] from a local artist so the standard online artist menu can be reused for MusicBrainz-sourced rows. */
fun Artist.toInnertube(): Innertube.ArtistItem =
    Innertube.ArtistItem(
        info = Innertube.Info(
            name = name,
            endpoint = NavigationEndpoint.Endpoint.Browse(browseId = id)
        ),
        subscribersCountText = null,
        songCount = null,
        channelId = null,
        description = null,
        thumbnail = null
    )

/**
 * Artist row for relations (thumbnail or initial, name, relation type).
 */
@Composable
fun RelatedArtistRow(
    artist: Artist,
    relationType: String,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(uiRoundnessShape())
            .then(
                if (onClick != null || onLongClick != null) Modifier.combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = { onLongClick?.invoke() }
                ) else Modifier
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (artist.thumbnailUrl != null) {
            ImageCacheFactory.Thumbnail(
                thumbnailUrl = artist.thumbnailUrl,
                modifier = Modifier
                    .size(40.dp)
                    .clip(artistThumbnailShape())
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(artistThumbnailShape())
                    .background(colorPalette().background1),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = artist.name?.firstOrNull()?.uppercase() ?: "?",
                    style = typography().xs,
                    color = colorPalette().text
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cleanPrefix(artist.name ?: stringResource(R.string.unknown_artist)),
                style = typography().xs,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = relationType.replaceFirstChar { it.uppercase() },
                style = typography().xxs,
                color = colorPalette().textSecondary,
                maxLines = 1
            )
        }
    }
}

/**
 * External link row (label + open-in-new icon).
 */
@Composable
fun ExternalLinkRow(
    label: String,
    url: String,
    @DrawableRes iconId: Int = R.drawable.up_right_arrow,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val intent = remember(url) {
        Intent(Intent.ACTION_VIEW, url.toUri())
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(uiRoundnessShape())
            .clickable {
                runCatching { context.startActivity(intent) }
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = null,
            tint = colorPalette().textSecondary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = label,
            style = typography().xs.semiBold,
            modifier = Modifier.weight(1f)
        )

        Icon(
            painter = painterResource(R.drawable.up_right_arrow),
            contentDescription = null,
            tint = colorPalette().textSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}
