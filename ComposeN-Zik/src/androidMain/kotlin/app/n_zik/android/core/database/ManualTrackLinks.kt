package app.n_zik.android.core.database

import android.content.SharedPreferences
import android.database.SQLException
import app.n_zik.android.Dependencies
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.appContext
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.models.SongArtistMap
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.preferences
import timber.log.Timber

/** Attaches the selected online album to this track while retaining its other editions. */
internal suspend fun Database.linkTrackAlbumManually(song: Song, album: Album, previousId: String? = null): Boolean {
    val browseId = album.id.selectedBrowseId(previousId) ?: return false
    if (song.isLocal || isDeviceAlbumId(browseId)) return false

    return try {
        transaction {
            val position = previousId?.let { songAlbumMapTable.findPairPosition(song.id, it) }
            mapIgnore(album, song, position ?: -1)
            if (previousId != null && previousId != album.id) {
                songAlbumMapTable.deletePairDirect(song.id, previousId)
            }
        }
        true
    } catch (error: SQLException) {
        Timber.tag("ManualTrackLinks").w(error, "Unable to save the track's album link")
        false
    }
}

/** Attaches the selected online artist to this track while retaining other credits. */
internal suspend fun Database.linkTrackArtistManually(song: Song, artist: Artist, previousId: String? = null): Boolean {
    val browseId = artist.id.selectedBrowseId(previousId) ?: return false
    if (song.isLocal || isDeviceArtistId(browseId)) return false

    return try {
        transaction {
            if (previousId != null && previousId != artist.id) {
                songArtistMapTable.deletePairDirect(song.id, previousId)
            }
            if (artist.id.startsWith(MODIFIED_PREFIX)) {
                songArtistMapTable.deletePairDirect(song.id, browseId)
            }
            artistTable.insertIgnore(artist)
            songTable.insertIgnore(song)
            songArtistMapTable.insertIgnore(SongArtistMap(song.id, artist.id))
        }
        true
    } catch (error: SQLException) {
        Timber.tag("ManualTrackLinks").w(error, "Unable to save the track's artist link")
        false
    }
}

private val onlineBrowseId = Regex("[A-Za-z0-9_-]+")

private fun String.selectedBrowseId(previousId: String?): String? =
    removePrefix(MODIFIED_PREFIX).takeIf { (startsWith(MODIFIED_PREFIX) || previousId != null) && onlineBrowseId.matches(it) }

internal fun trackBrowseIdOriginKey(songId: String, browseId: String): String =
    "track_browse_origin_${songId.length}:$songId$browseId"

internal fun trackBrowseIdOrigin(songId: String, browseId: String): String? {
    if (!Dependencies.isInitialized) return null
    return appContext().preferences.trackBrowseIdOrigin(songId, browseId)
}

internal fun SharedPreferences.trackBrowseIdOrigin(songId: String, browseId: String): String? =
    getString(trackBrowseIdOriginKey(songId, browseId), null) ?: getString("old_browse_id_$browseId", null)
