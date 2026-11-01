package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import androidx.media3.exoplayer.offline.Download
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.download.utils.MyDownloadHelper
import kotlinx.coroutines.flow.first

/** Loads the phone's playlist order for both browse snapshots and playback actions. */
internal suspend fun loadAutoPlaylistSongs(
    context: Context,
    database: Database,
    downloadHelper: MyDownloadHelper,
    playlistId: Long,
): List<Song> {
    val isRewind = database.playlistTable.findById(playlistId).first()
        ?.name?.let(RewindPlaylists::isRewind) == true
    val sortBy = context.preferences.getEnum(
        "PlaylistSongsSortBy_$playlistId",
        if (isRewind) PlaylistSongSortBy.RewindTop else PlaylistSongSortBy.Title,
    )
    val sortOrder = context.preferences.getEnum("PlaylistSongsSortOrder_$playlistId", SortOrder.Ascending)
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
