package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.tab.selectionActionTargets
import app.n_zik.android.enums.PlaylistsType
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.models.SongPlaylistMap
import app.n_zik.android.core.database.isYouTubePlaylistSource
import app.n_zik.android.core.rewind.RewindPlaylists

internal data class HomeLibraryPlaylistScope(
    val type: PlaylistsType,
    val youtubeFilter: YouTubeLibraryFilter,
    val query: String,
    val monthlyEnabled: Boolean,
    val yearlyEnabled: Boolean,
    val rewindFilter: RewindPlaylists.Filter,
) {
    fun matchesName(name: String, displayName: (String) -> String = ::cleanPrefix): Boolean =
        query.isBlank() || displayName(name).contains(query.trim(), ignoreCase = true)

    fun canOpenPlaylistPicker(hasTracks: Boolean, selectionActive: Boolean): Boolean =
        hasTracks || (type == PlaylistsType.Playlist && query.isBlank() && !selectionActive)

    fun visible(candidates: List<PlaylistPreview>): List<PlaylistPreview> = candidates.filter { preview ->
        val playlist = preview.playlist
        matchesName(playlist.name) && when (type) {
            PlaylistsType.YTPlaylist -> matchesYouTubeLibraryFilter(playlist, youtubeFilter)
            PlaylistsType.Playlist -> true
            PlaylistsType.OnDevice -> !isYouTubePlaylistSource(playlist.browseId, playlist.isYoutubePlaylist)
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
    selectionActive: Boolean,
): List<PlaylistPreview> =
    selectionActionTargets(selectionActive, selected, visible, { it.playlist.id }, { it.playlist.id })

/** Keeps persisted playlist membership separate from the tracks the current scan can use. */
internal fun availableDevicePlaylistIds(mappings: List<SongPlaylistMap>, availableSongIds: Set<String>): Set<Long> =
    mappings.mapNotNullTo(hashSetOf()) { mapping ->
        mapping.playlistId.takeIf { mapping.songId in availableSongIds }
    }
