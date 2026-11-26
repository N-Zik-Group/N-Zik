package app.n_zik.android.core.database

import android.database.SQLException
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import timber.log.Timber

/** Attaches the selected online album to this track while retaining its other editions. */
internal suspend fun Database.linkTrackAlbumManually(song: Song, album: Album): Boolean {
    val browseId = album.id.selectedBrowseId() ?: return false
    if (song.isLocal || isDeviceAlbumId(browseId)) return false

    return try {
        transaction { mapIgnore(album, song) }
        true
    } catch (error: SQLException) {
        Timber.tag("ManualTrackLinks").w(error, "Unable to save the track's album link")
        false
    }
}

/** Attaches the selected online artist to this track while retaining other credits. */
internal suspend fun Database.linkTrackArtistManually(song: Song, artist: Artist): Boolean {
    val browseId = artist.id.selectedBrowseId() ?: return false
    if (song.isLocal || isDeviceArtistId(browseId)) return false

    return try {
        transaction {
            mapIgnore(artist, song)
            songArtistMapTable.deletePairDirect(song.id, browseId)
        }
        true
    } catch (error: SQLException) {
        Timber.tag("ManualTrackLinks").w(error, "Unable to save the track's artist link")
        false
    }
}

private val onlineBrowseId = Regex("[A-Za-z0-9_-]+")

private fun String.selectedBrowseId(): String? =
    removePrefix(MODIFIED_PREFIX).takeIf { startsWith(MODIFIED_PREFIX) && onlineBrowseId.matches(it) }
