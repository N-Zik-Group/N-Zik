package app.n_zik.android.components.musicbrainz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.musicbrainz.models.ExternalLink
import app.n_zik.android.ui.components.themed.IconButton

/**
 * Row of clickable external link icons (social networks, streaming services).
 */
@Composable
fun ExternalLinksSection(links: List<ExternalLink>?) {
    if (links.isNullOrEmpty()) return

    val uriHandler = LocalUriHandler.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        links.forEach { link ->
            if (link.platform.isBlank()) return@forEach

            IconButton(
                modifier = Modifier.size(20.dp),
                onClick = { uriHandler.openUri(link.url) },
                icon = when (link.platform) {
                    "home" -> R.drawable.link
                    "youtube" -> R.drawable.logo_youtube
                    "instagram" -> R.drawable.logo_instagram
                    "facebook" -> R.drawable.logo_facebook
                    "twitter" -> R.drawable.logo_twitter
                    "spotify" -> R.drawable.logo_spotify
                    "applemusic" -> R.drawable.logo_apple
                    "deezer" -> R.drawable.logo_deezer
                    "soundcloud" -> R.drawable.logo_soundcloud
                    "discogs" -> R.drawable.logo_discogs
                    "rateyourmusic" -> R.drawable.star_brilliant
                    else -> R.drawable.globe
                },
                color = colorPalette().text
            )
        }
    }
}
