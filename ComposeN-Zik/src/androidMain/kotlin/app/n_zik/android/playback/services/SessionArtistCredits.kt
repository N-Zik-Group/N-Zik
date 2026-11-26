package app.n_zik.android.playback.services

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.components.song.isArtistIdForSong
import app.n_zik.android.models.Info
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract
import app.n_zik.android.utils.artistLinksWithStoredFallback
import app.n_zik.android.utils.resolveTrackArtistName

internal data class SessionArtistCredit(val stored: String?, val artists: List<Info>) {
    fun resolve(
        item: MediaItem,
        metadata: MediaMetadata,
        unknownArtist: String,
        originalId: (String) -> String?,
    ): String? {
        val itemMetadata = item.mediaMetadata
        val embedded = metadata.artist?.toString()
        val artist = if (itemMetadata.extras?.getBoolean("isArtistModified") == true) {
            (itemMetadata.artist?.toString() ?: embedded)?.let {
                if (it.startsWith(MODIFIED_PREFIX)) it else MODIFIED_PREFIX + it
            }
        } else embedded
        val links = item.artistLinksWithStoredFallback(artists, originalId)
            .filter { isArtistIdForSong(it.id, item.isLocal) }
        return resolveTrackArtistName(artist, stored, links, unknownArtist)
    }
}

@UnstableApi
internal fun MediaItem.sessionArtistTrackId(): String =
    AutoMediaIdContract.parseSelection(mediaId)?.songId
        ?: localConfiguration?.customCacheKey
        ?: mediaId.substringAfterLast('/')
