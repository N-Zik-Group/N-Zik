package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.enums.PlaylistSongSortBy
import app.n_zik.android.enums.SongSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Song
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.preferences
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.isYouTubePlaylistSource
import app.n_zik.android.core.ondevice.groupByDeviceAvailability
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.download.utils.MyDownloadHelper
import kotlinx.coroutines.flow.first

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
): List<Song> {
    val playlist = database.playlistTable.findById(playlistId).first()
    val isRewind = playlist?.name?.let(RewindPlaylists::isRewind) == true
    val sortBy = context.preferences.getEnum(
        "PlaylistSongsSortBy_$playlistId",
        if (isRewind) PlaylistSongSortBy.RewindTop else PlaylistSongSortBy.Title,
    )
    val sortOrder = context.preferences.getEnum("PlaylistSongsSortOrder_$playlistId", SortOrder.Ascending)
    if (sortBy == PlaylistSongSortBy.OnDevice) {
        val groupByDevice = !onDeviceOnly && context.isDeviceLibraryEnabled() && playlist != null &&
            !isYouTubePlaylistSource(playlist.browseId, playlist.isYoutubePlaylist)
        val songs = database.songPlaylistMapTable.sortSongs(
            playlistId, PlaylistSongSortBy.Title, if (groupByDevice) SortOrder.Ascending else sortOrder,
        ).first()
        if (!groupByDevice) return songs
        val availableIds = context.scanDeviceLibrary().songIds
        return songs.groupByDeviceAvailability(sortOrder) { it.id in availableIds }
    }
    if (sortBy != PlaylistSongSortBy.Downloaded) {
        return database.songPlaylistMapTable.sortSongs(playlistId, sortBy, sortOrder).first()
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
