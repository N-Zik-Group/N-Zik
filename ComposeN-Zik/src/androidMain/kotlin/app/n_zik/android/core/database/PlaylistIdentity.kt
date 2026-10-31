package app.n_zik.android.core.database

import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.MODIFIED_PREFIX

enum class PlaylistLinkKind { Local, YouTubePlaylist, YouTubePodcast }

private val youtubePlaylistId = Regex("(?:PL|RD|OLAK5uy_)[A-Za-z0-9_-]+")

/** Recognizes supported YouTube identifiers, leaving provider markers and arbitrary local IDs local. */
fun playlistLinkKind(browseId: String?): PlaylistLinkKind {
    val id = browseId?.removePrefix(MODIFIED_PREFIX) ?: return PlaylistLinkKind.Local
    return when {
        isPodcastBrowseId(id) -> PlaylistLinkKind.YouTubePodcast
        id.startsWith("VL") && id.length > 2 -> PlaylistLinkKind.YouTubePlaylist
        id in listOf("LM", "SE") || youtubePlaylistId.matches(id) -> PlaylistLinkKind.YouTubePlaylist
        else -> PlaylistLinkKind.Local
    }
}

/** Bookmark controls represent linked sources, excluding modified copies and special account lists. */
fun canBookmarkPlaylist(playlist: Playlist?): Boolean {
    val id = playlist?.browseId ?: return false
    return playlistLinkKind(id) != PlaylistLinkKind.Local && !id.startsWith(MODIFIED_PREFIX) &&
        id.removePrefix("VL") !in listOf("LM", "SE")
}

/** Linked editable playlists retain manual sync; editable podcast imports retain their local edits. */
fun canRefreshPlaylist(playlist: Playlist?): Boolean = when (playlistLinkKind(playlist?.browseId)) {
    PlaylistLinkKind.Local -> false
    PlaylistLinkKind.YouTubePlaylist -> true
    PlaylistLinkKind.YouTubePodcast -> playlist?.isEditable == false &&
        playlist.browseId?.startsWith(MODIFIED_PREFIX) == false
}

/** Identifies podcast browse endpoints without converting them into playlist IDs. */
fun isPodcastBrowseId(browseId: String?): Boolean = browseId?.startsWith("MPSP") == true

/** Source identity is independent of the mutable remote-library bookmark flag. */
fun isYouTubePlaylistSource(browseId: String?, remoteSource: Boolean = false): Boolean =
    remoteSource || playlistLinkKind(browseId) != PlaylistLinkKind.Local

/** Podcast snapshots are bookmarked by presence; other playlists keep their remote-library contract. */
fun isPlaylistBookmarked(
    playlist: Playlist?,
    remoteBookmark: Boolean = playlist?.isYoutubePlaylist == true,
): Boolean = playlist != null && canBookmarkPlaylist(playlist) &&
    (isPodcastBrowseId(playlist.browseId) || remoteBookmark)

internal fun PlaylistBookmarkState.isBookmarked(): Boolean =
    canBookmarkPlaylist(Playlist(name = "", browseId = browseId)) &&
        (isPodcastBrowseId(browseId) || isYoutubePlaylist)

/** Read-only podcast snapshots share the remote playlist lock without enabling account sync. */
fun isReadOnlyPlaylistSource(playlist: Playlist): Boolean =
    !playlist.isEditable && playlistLinkKind(playlist.browseId) != PlaylistLinkKind.Local

/** User membership edits are separate from playback order and trusted source refresh. */
fun canEditPlaylistMembership(playlist: Playlist): Boolean = !isReadOnlyPlaylistSource(playlist)

/** Rechecks an existing target inside the caller's database transaction. */
fun canEditStoredPlaylistMembership(playlistId: Long): Boolean =
    Database.playlistTable.getAll().firstOrNull { it.id == playlistId }
        ?.let(::canEditPlaylistMembership) == true

/** Legacy target pickers dispatch callbacks only after rechecking their current stored row. */
fun dispatchPlaylistMembershipEdit(playlistId: Long, edit: () -> Unit) {
    Database.asyncTransaction {
        editStoredPlaylistMembership(playlistId) { edit() }
    }
}

/** Applies user additions/removals atomically against the current target's membership policy. */
fun editStoredPlaylistMembership(playlistId: Long, edit: Database.() -> Unit): Boolean {
    var edited = false
    Database.syncTransaction {
        if (canEditStoredPlaylistMembership(playlistId)) {
            edit()
            edited = true
        }
    }
    return edited
}
