package app.n_zik.android.components.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.tab.selectionActionTargets
import app.n_zik.android.core.database.Database
import app.n_zik.android.enums.PlaylistSortBy
import app.n_zik.android.enums.PlaylistsType
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.models.SongPlaylistMap
import app.n_zik.android.core.database.isYouTubePlaylistSource
import app.n_zik.android.core.ondevice.observeDeviceLibrary
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

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

    fun visible(candidates: List<PlaylistPreview>, onDevicePlaylistIds: Set<Long>? = null): List<PlaylistPreview> = candidates.filter { preview ->
        val playlist = preview.playlist
        matchesName(playlist.name) && when (type) {
            PlaylistsType.YTPlaylist -> matchesYouTubeLibraryFilter(playlist, youtubeFilter)
            PlaylistsType.Playlist -> true
            PlaylistsType.OnDevice -> playlist.matchesOnDevicePlaylist(onDevicePlaylistIds)
            PlaylistsType.MonthlyPlaylist -> RewindPlaylists.isShown(
                playlist.name, monthlyEnabled, yearlyEnabled, rewindFilter,
            )
            PlaylistsType.PinnedPlaylist -> playlist.name.startsWith(PINNED_PREFIX, true)
        }
    }
}

internal fun Playlist.matchesOnDevicePlaylist(playlistIds: Set<Long>?): Boolean =
    (playlistIds == null || id in playlistIds) && !isYouTubePlaylistSource(browseId, isYoutubePlaylist)

/** Only an unsearched position-sorted projection can safely rewrite global positions. */
internal fun canReorderHomePlaylists(
    query: String,
    sortBy: PlaylistSortBy,
    sortOrder: SortOrder,
    locked: Boolean,
): Boolean = query.isBlank() && sortBy == PlaylistSortBy.Custom && sortOrder == SortOrder.Ascending && !locked

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

@Composable
internal fun rememberAvailableDevicePlaylistIds(): Set<Long>? {
    val context = LocalContext.current
    val profile = getActiveProfile(context)
    return key(profile) {
        val availablePlaylists = remember(context) {
            combine(
                context.observeDeviceLibrary(),
                Database.songPlaylistMapTable.observeDeviceTrackMappings(),
            ) { scan, mappings -> availableDevicePlaylistIds(mappings, scan.songIds) }.distinctUntilChanged()
        }
        availablePlaylists.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA).value
    }
}
