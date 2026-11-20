package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.enums.PlaylistsType
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.core.database.isYouTubePlaylistSource
import app.n_zik.android.core.rewind.RewindPlaylists

internal data class HomeLibraryPlaylistScope(
    val type: PlaylistsType,
    val youtubeFilter: YouTubeLibraryFilter,
    val query: String,
    val showRewind: Boolean,
    val showPinned: Boolean,
    val monthlyEnabled: Boolean,
    val yearlyEnabled: Boolean,
    val rewindFilter: RewindPlaylists.Filter,
) {
    fun visible(candidates: List<PlaylistPreview>): List<PlaylistPreview> = candidates.filter { preview ->
        val playlist = preview.playlist
        playlist.name.contains(query, ignoreCase = true) && when (type) {
            PlaylistsType.YTPlaylist -> matchesYouTubeLibraryFilter(playlist, youtubeFilter)
            PlaylistsType.Playlist, PlaylistsType.OnDevice ->
                (type != PlaylistsType.OnDevice || !isYouTubePlaylistSource(playlist.browseId, playlist.isYoutubePlaylist)) &&
                (!RewindPlaylists.isRewind(playlist.name) || showRewind) &&
                (!playlist.name.startsWith(PINNED_PREFIX, true) || showPinned)
            PlaylistsType.MonthlyPlaylist -> RewindPlaylists.isShown(
                playlist.name, monthlyEnabled, yearlyEnabled, rewindFilter,
            )
            PlaylistsType.PinnedPlaylist -> playlist.name.startsWith(PINNED_PREFIX, true)
        }
    }
}

/** Hidden selections never broaden a bulk action or fall back to all visible rows. */
internal fun homeLibraryActionTargets(
    visible: List<PlaylistPreview>,
    selected: List<PlaylistPreview>,
): List<PlaylistPreview> {
    if (selected.isEmpty()) return visible
    val selectedIds = selected.mapTo(HashSet()) { it.playlist.id }
    return visible.filter { it.playlist.id in selectedIds }
}
