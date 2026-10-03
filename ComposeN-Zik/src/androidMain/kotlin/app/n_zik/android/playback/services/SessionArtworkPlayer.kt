package app.n_zik.android.playback.services

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

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
 */
@UnstableApi
class SessionArtworkPlayer(upstream: Player) : ForwardingPlayer(upstream) {

    override fun getMediaMetadata(): MediaMetadata {
        val metadata = super.getMediaMetadata()
        val mediaId = currentMediaItem?.mediaId ?: return metadata
        if (!LocalArtworkRules.needsProviderArtwork(
                mediaId,
                metadata.artworkUri?.toString(),
                ArtworkContentProvider.AUTHORITY
            )
        ) return metadata
        return metadata.buildUpon()
            .setArtworkUri(ArtworkContentProvider.uriFor(LocalArtworkRules.songIdOf(mediaId)))
            .build()
    }
}
