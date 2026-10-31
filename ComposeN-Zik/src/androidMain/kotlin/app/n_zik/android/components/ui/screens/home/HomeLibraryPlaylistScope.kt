package app.n_zik.android.components.ui.screens.home

import app.it.fast4x.rimusic.PINNED_PREFIX
import app.it.fast4x.rimusic.enums.PlaylistsType
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.rewind.RewindPlaylists
import kotlinx.coroutines.flow.first

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
            PlaylistsType.Playlist -> (!RewindPlaylists.isRewind(playlist.name) || showRewind) &&
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

/** Playback and export resolve the same scoped playlist IDs and deduplicate shared songs. */
internal suspend fun loadHomeLibraryActionSongs(
    targets: List<PlaylistPreview>,
    loadSongs: suspend (Long) -> List<Song> = { Database.songPlaylistMapTable.allSongsOf(it).first() },
): List<Song> {
    val seen = HashSet<String>()
    return buildList {
        targets.forEach { preview ->
            loadSongs(preview.playlist.id).forEach { song -> if (seen.add(song.id)) add(song) }
        }
    }
}
