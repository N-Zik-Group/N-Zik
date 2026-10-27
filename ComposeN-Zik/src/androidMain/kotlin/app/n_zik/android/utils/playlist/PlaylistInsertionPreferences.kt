package app.n_zik.android.utils.playlist

import android.content.Context
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.utils.preferences

/** Stable stored values independent of translated labels and display sort order. */
enum class PlaylistInsertionPosition(val storedValue: String) {
    Beginning("beginning"), End("end")
}

/** Uses the active profile's preferences and retains only the application context. */
class PlaylistInsertionPreferences(context: Context) {
    private val context = context.applicationContext

    fun get(playlistId: Long): PlaylistInsertionPosition {
        val stored = runCatching {
            context.preferences.getString(key(playlistId), null)
        }.getOrNull()
        return PlaylistInsertionPosition.entries.firstOrNull { it.storedValue == stored }
            ?: PlaylistInsertionPosition.End
    }

    fun set(playlistId: Long, position: PlaylistInsertionPosition) {
        context.preferences.edit().putString(key(playlistId), position.storedValue).apply()
    }

    internal fun key(playlistId: Long): String = "playlist_insertion_position_$playlistId"
}

/** Remote-linked playlists append remotely, so they must not advertise local prepend ordering. */
fun Playlist.supportsInsertionPosition(): Boolean = isEditable && !isYoutubePlaylist &&
    (browseId.isNullOrBlank() || browseId.startsWith(MODIFIED_PREFIX))
