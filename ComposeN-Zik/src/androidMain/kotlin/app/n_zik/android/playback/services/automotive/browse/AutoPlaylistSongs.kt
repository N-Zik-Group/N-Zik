package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.components.ui.screens.home.matchesOnDevicePlaylist
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.enums.PlaylistSongSortBy
import app.n_zik.android.enums.SongSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Song
import app.n_zik.android.models.Playlist
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.isYouTubePlaylistSource
import app.n_zik.android.core.ondevice.groupByDeviceAvailability
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.isUnmatched
import app.n_zik.android.ui.screens.localplaylist.defaultPlaylistSongSort
import kotlinx.coroutines.flow.first

/** Importers tag unresolved provider rows before they have any listening history. */
internal fun Song.isUnmatchedPlaylistImport(): Boolean = totalPlayTimeMs == 1L && isUnmatched

/** Orders legacy Favorites without changing which entries the legacy route exposes. */
internal suspend fun loadAutoDeviceSortedFavorites(context: Context, database: Database, order: SortOrder): List<Song> {
    val enabled = context.isDeviceLibraryEnabled()
    val songs = database.songTable.sortFavorites(SongSortBy.Title, if (enabled) SortOrder.Ascending else order).first()
    if (!enabled) return songs
    val availableIds = context.scanDeviceLibrary().songIds
    return songs.groupByDeviceAvailability(order) { it.id in availableIds }
}

/** Loads the phone's playlist order for both browse snapshots and playback actions. */
internal suspend fun loadAutoPlaylistSongs(
    context: Context,
    database: Database,
    downloadHelper: MyDownloadHelper,
    playlistId: Long,
    onDeviceOnly: Boolean = false,
    availableSongIds: Set<String>? = null,
): List<Song> {
    val playlist = database.playlistTable.findById(playlistId).first() ?: return emptyList()
    val isRewind = RewindPlaylists.isReservedName(playlist.name)
    if (isRewind && !RewindPlaylists.visibility(context).visibleInLibrary(playlist.name)) return emptyList()
    if (onDeviceOnly && (!context.isDeviceLibraryEnabled() || !playlist.matchesOnDevicePlaylist(null))) return emptyList()
    val parentalControl = context.preferences.getBoolean(parentalControlEnabledKey, false)
    val songs = sortAutoPlaylistSongs(context, database, downloadHelper, playlist, isRewind, onDeviceOnly)
        .filterNot { it.isUnmatchedPlaylistImport() || (parentalControl && it.title.hasExplicitPrefix()) }
    if (!onDeviceOnly) return songs
    val availableIds = availableSongIds ?: context.scanDeviceLibrary().songIds
    return songs.filter { it.id in availableIds }
}

private suspend fun sortAutoPlaylistSongs(
    context: Context,
    database: Database,
    downloadHelper: MyDownloadHelper,
    playlist: Playlist,
    isRewind: Boolean,
    onDeviceOnly: Boolean,
): List<Song> {
    val playlistId = playlist.id
    val preferences = context.preferences
    val savedSort = preferences.getString("PlaylistSongsSortBy_$playlistId", null)
    val knownDefault = defaultPlaylistSongSort(playlist.browseId, isRewind)
    val sortBy = PlaylistSongSortBy.entries.firstOrNull { it.name == savedSort } ?: knownDefault
    val sortOrder = preferences.getEnum("PlaylistSongsSortOrder_$playlistId", SortOrder.Ascending)
    if (sortBy == PlaylistSongSortBy.OnDevice) {
        val groupByDevice = !onDeviceOnly && context.isDeviceLibraryEnabled() &&
            !isYouTubePlaylistSource(playlist.browseId, playlist.isYoutubePlaylist)
        val songs = database.songPlaylistMapTable.sortSongs(
            playlistId, PlaylistSongSortBy.Title, if (groupByDevice) SortOrder.Ascending else sortOrder,
        ).first()
        if (!groupByDevice) return songs
        val availableIds = context.scanDeviceLibrary().songIds
        return songs.groupByDeviceAvailability(sortOrder) { it.id in availableIds }
    }
    if (sortBy != PlaylistSongSortBy.Downloaded) {
        val songs = database.songPlaylistMapTable.sortSongs(playlistId, sortBy, sortOrder).first()
        if (savedSort != null || sortBy != PlaylistSongSortBy.Title) return songs
        val inferredSort = defaultPlaylistSongSort(playlist.browseId, isRewind, songs)
        return if (inferredSort == sortBy) songs
            else database.songPlaylistMapTable.sortSongs(playlistId, inferredSort, sortOrder).first()
    }

    // Downloaded reverses group priority only; titles stay ascending within both groups.
    val downloadedIds = downloadHelper.downloads.value.values
        .filter { it.state == Download.STATE_COMPLETED }
        .mapTo(hashSetOf()) { it.request.id }
    val songs = database.songPlaylistMapTable
        .sortSongs(playlistId, PlaylistSongSortBy.Title, SortOrder.Ascending).first()
    return if (sortOrder == SortOrder.Ascending) songs.sortedByDescending { it.id in downloadedIds }
        else songs.sortedBy { it.id in downloadedIds }
}
