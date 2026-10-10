package app.n_zik.android.bridge.library

import app.n_zik.android.bridge.BridgeContract
import app.n_zik.android.bridge.state.TrackDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Kind of list a [ListRef] designates (contract §10.4, since 1.10.0). */
@Serializable
internal enum class ListKind {
    @SerialName("songs") SONGS,
    @SerialName("playlist") PLAYLIST,
    @SerialName("album") ALBUM,
    @SerialName("artist") ARTIST,
}

/**
 * A reference to a whole track list of the phone (contract §10.4, since 1.10.0): the same
 * parameters as the list route it names (`/library/songs`, `/library/playlists/{id}/songs`,
 * `/library/albums/{id}/songs`, `/library/artists/{id}/songs`), without pagination. The
 * server resolves the FULL list, exactly as the route would page it.
 */
@Serializable
internal data class ListRef(
    val kind: ListKind,
    /** Playlist, album or artist id; ignored for `songs`. */
    val id: String? = null,
    val filter: String? = null,
    val sort: String? = null,
    val reverse: Boolean = false,
    val period: String? = null,
    /** The search of the list (`query` of `/library/songs`, `text` of a playlist's songs). */
    val text: String? = null,
)

/** Outcome of [LibraryLists.resolve]. */
internal sealed interface ListResolution {
    data class Found(val tracks: List<TrackDto>) : ListResolution

    /** The playlist, album or artist does not exist: `404 NOT_FOUND`. */
    data object Unknown : ListResolution

    /** A parameter is out of its enum or bounds: `400 BAD_REQUEST`. */
    data object Invalid : ListResolution
}

/**
 * Resolves a [ListRef] into the full ordered list the client shows (contract §10.4), through
 * the same [LibraryProvider] reads and the same search as the paged list routes, so an index
 * in the client's list is an index in this list.
 */
internal object LibraryLists {

    suspend fun resolve(provider: LibraryProvider, ref: ListRef): ListResolution {
        val text = ref.text?.trim()?.takeIf { it.isNotEmpty() }
        if (text != null && text.length > BridgeContract.LIBRARY_QUERY_MAX_LENGTH) return ListResolution.Invalid
        val tracks = when (ref.kind) {
            ListKind.SONGS -> {
                val filter = LibraryQueries.parseSongFilter(ref.filter) ?: return ListResolution.Invalid
                val sort = LibraryQueries.parseSongSort(ref.sort) ?: return ListResolution.Invalid
                val period = when (ref.period) {
                    null -> null
                    else -> LibraryQueries.parseTopPeriod(ref.period) ?: return ListResolution.Invalid
                }
                provider.songs(filter, sort, ref.reverse, period)
            }
            ListKind.PLAYLIST -> {
                val sort = LibraryQueries.parsePlaylistSongSort(ref.sort) ?: return ListResolution.Invalid
                val id = LibraryQueries.parsePlaylistId(ref.id) ?: return ListResolution.Unknown
                provider.playlistSongs(id, sort, ref.reverse) ?: return ListResolution.Unknown
            }
            ListKind.ALBUM -> provider.albumSongs(ref.id.orEmpty()) ?: return ListResolution.Unknown
            ListKind.ARTIST -> provider.artistSongs(ref.id.orEmpty()) ?: return ListResolution.Unknown
        }
        return ListResolution.Found(LibraryQueries.searchSongs(tracks, text))
    }

    /** Position of [trackId] in [tracks], `-1` when absent (contract §10.4, `library.locate`). */
    fun indexOf(tracks: List<TrackDto>, trackId: String): Int = tracks.indexOfFirst { it.id == trackId }
}
