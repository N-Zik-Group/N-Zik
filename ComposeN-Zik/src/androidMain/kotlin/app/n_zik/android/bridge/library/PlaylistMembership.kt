package app.n_zik.android.bridge.library

import app.n_zik.android.core.database.Database
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

private const val TAG = "BridgeLibrary"

/**
 * `Track.inPlaylist` (contract §1.1, since 1.12.0): which songs sit in at least one of the phone's
 * playlists, read in batched queries — the phone's own `SongPlaylistMapTable.songsInPlaylists`, in
 * chunks under SQLite's 999 bound parameters — never one query per track.
 */
internal object PlaylistMembership {

    /** Under SQLite's 999 bound parameters per query. */
    const val CHUNK_SIZE = 900

    /**
     * The subset of [songIds] mapped to a playlist, through [query] (one call per chunk). A failed read
     * is an empty set: every track then reads `inPlaylist = false`, logged as a warning.
     */
    suspend fun read(songIds: Collection<String>, query: suspend (List<String>) -> List<String>): Set<String> {
        if (songIds.isEmpty()) return emptySet()
        return try {
            songIds.distinct().chunked(CHUNK_SIZE).flatMapTo(HashSet()) { query(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not read the playlist membership")
            emptySet()
        }
    }

    /** [read] against the phone's database. */
    suspend fun readFromDatabase(songIds: Collection<String>): Set<String> =
        read(songIds) { chunk -> Database.songPlaylistMapTable.songsInPlaylists(chunk).first() }
}
