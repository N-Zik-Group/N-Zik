package app.n_zik.android.utils.playlist

import android.content.Context
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.core.database.canRefreshPlaylist
import app.n_zik.android.core.database.isReadOnlyPlaylistSource

/** Per-profile source refresh preference, independent of account synchronization. */
class PlaylistRefreshPreferences(context: Context) {
    private val context = context.applicationContext

    fun get(playlist: Playlist): Boolean = canRefreshPlaylist(playlist) &&
        context.preferences.getBoolean(key(playlist.id), isReadOnlyPlaylistSource(playlist))

    fun set(playlistId: Long, enabled: Boolean) {
        context.preferences.edit().putBoolean(key(playlistId), enabled).apply()
    }

    companion object {
        fun key(playlistId: Long): String = "playlist_refresh_on_open_$playlistId"
    }
}
