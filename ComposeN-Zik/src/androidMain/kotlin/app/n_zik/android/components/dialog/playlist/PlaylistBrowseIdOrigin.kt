package app.n_zik.android.components.dialog.playlist

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.appContext
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.playlistLinkKind
import app.n_zik.android.models.Playlist
import app.n_zik.android.utils.preferences

internal const val UNLINKED_PLAYLIST_ID_PREFIX = "LOCAL_Playlist_"

internal data class PlaylistBrowseIdOrigin(val id: String?)

internal fun playlistBrowseIdOriginKey(playlistId: Long) = "playlist_original_browse_id_$playlistId"

/** A recorded empty value means the playlist originally had no source. */
internal fun originalPlaylistBrowseId(
    browseId: String?,
    previousId: (String) -> String?,
): PlaylistBrowseIdOrigin? {
    var id = browseId
    val visited = mutableSetOf<String>()
    while (!id.isNullOrBlank() && (id.startsWith(MODIFIED_PREFIX) || id.startsWith(UNLINKED_PLAYLIST_ID_PREFIX))) {
        if (!visited.add(id)) return null
        id = previousId(id) ?: return null
    }
    return PlaylistBrowseIdOrigin(id?.takeIf(String::isNotBlank))
}

internal fun readPlaylistBrowseIdOrigin(playlist: Playlist): PlaylistBrowseIdOrigin? {
    val preferences = appContext().preferences
    return recordedPlaylistBrowseIdOrigin(
        playlist.browseId,
        preferences.getString(playlistBrowseIdOriginKey(playlist.id), null),
    ) { id -> preferences.getString("old_browse_id_$id", null) }
}

internal fun recordedPlaylistBrowseIdOrigin(
    browseId: String?,
    savedOrigin: String?,
    previousId: (String) -> String?,
): PlaylistBrowseIdOrigin? =
    if (savedOrigin != null) PlaylistBrowseIdOrigin(savedOrigin.takeIf(String::isNotBlank))
    else originalPlaylistBrowseId(browseId, previousId)

internal fun canChangePlaylistBrowseId(
    playlist: Playlist?,
    readOrigin: (Playlist) -> PlaylistBrowseIdOrigin? = ::readPlaylistBrowseIdOrigin,
): Boolean {
    val browseId = playlist?.browseId ?: return false
    return playlistLinkKind(browseId) == PlaylistLinkKind.YouTubePlaylist ||
        (browseId.startsWith(UNLINKED_PLAYLIST_ID_PREFIX) &&
            playlistLinkKind(readOrigin(playlist)?.id) == PlaylistLinkKind.YouTubePlaylist)
}
