package app.n_zik.android.playback.services

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.appContext

/**
 * Session-only facade: publishes the signed provider URI
 * ([ArtworkContentProvider.uriFor]) as the artwork URI of local tracks and of
 * custom covers (streams included), whatever path built the queue (Android Auto
 * callbacks or the phone's legacy `asMediaItem`).
 *
 * The legacy (MediaSessionCompat) metadata read by Android Auto is built from
 * [getMediaMetadata]; its home media card loads that URI in the Android Auto
 * process, where MediaStore / resource / file URIs cannot be decoded (see
 * [LocalArtworkRules]). Only the session sees this facade — the phone UI keeps
 * reading the original metadata. Embedded artwork bytes are left untouched.
 *
 * The facade also applies the unknown-metadata placeholders
 * ([SessionMetadataFallbacks]): tracks without title/artist tags must not
 * render a blank label on the session surfaces (Android Auto home card, media
 * notification — both read this [getMediaMetadata] through the session).
 */
@UnstableApi
class SessionArtworkPlayer(upstream: Player) : ForwardingPlayer(upstream) {

    override fun getMediaMetadata(): MediaMetadata {
        val metadata = super.getMediaMetadata()
        val mediaId = currentMediaItem?.mediaId
        val providerArtworkUri = mediaId?.takeIf {
            LocalArtworkRules.needsProviderArtwork(
                it,
                metadata.artworkUri?.toString(),
                ArtworkContentProvider.AUTHORITY
            )
        }?.let { ArtworkContentProvider.uriFor(LocalArtworkRules.songIdOf(it)) }
        val needsTitleFallback = SessionMetadataFallbacks.needsFallback(metadata.title)
        val needsArtistFallback = SessionMetadataFallbacks.needsFallback(metadata.artist)
        if (providerArtworkUri == null && !needsTitleFallback && !needsArtistFallback) return metadata
        val context = appContext()
        return metadata.buildUpon()
            .apply {
                providerArtworkUri?.let { setArtworkUri(it) }
                if (needsTitleFallback) setTitle(context.getString(R.string.unknown_title))
                if (needsArtistFallback) setArtist(context.getString(R.string.unknown_artist))
            }
            .build()
    }
}
