package app.n_zik.android.playback.services.automotive.browse.handlers

import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.utils.*
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.playlistPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import app.it.fast4x.rimusic.enums.OnDeviceSongSortBy
import app.kreate.android.me.knighthat.utils.getLocalSongs
import app.it.fast4x.rimusic.MODIFIED_PREFIX

class PlaylistDetailHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean {
        if (!parentId.startsWith("${PlayerServiceModern.PLAYLIST}/")) return false
        val parts = parentId.split("/")
        return parts.size == 2
    }

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        val parts = parentId.split("/")
        val actualParentId = parts[0]
        val playlistId = parts[1]
        val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_PLAYLIST_SHUFFLE)
        val listFlow = when (playlistId) {
            AutoSessionConstants.ID_FAVORITES -> {
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_ORDER.key, SortOrder.Ascending)
                database.songTable.sortFavorites(sortBy, sortOrder)
            }
            AutoSessionConstants.ID_CACHED -> {
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_ORDER.key, SortOrder.Ascending)
                database.formatTable.sortAllWithSongs(sortBy, sortOrder).map { flist ->
                    flist.fastFilter { itf -> val contentLength = itf.format.contentLength; contentLength != null && (if (binder != null) binder.cache.isCached(itf.song.id, 0L, contentLength) else false) }.fastMap { itf -> itf.song }
                }
            }
            AutoSessionConstants.ID_TOP -> {
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_TOP_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_TOP_SORT_ORDER.key, SortOrder.Ascending)
                val topIds = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt()).first().map { it.id }.toSet()
                database.songTable.sortAll(sortBy, sortOrder, excludeHidden = true).map { songs -> songs.filter { it.id in topIds } }
            }
            AutoSessionConstants.ID_ONDEVICE -> {
                val sortBy = context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_BY.key, OnDeviceSongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER.key, SortOrder.Ascending)
                context.getLocalSongs(sortBy, sortOrder).map { songs -> songs.keys.toList() }
            }
            AutoSessionConstants.ID_DOWNLOADED -> {
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER.key, SortOrder.Ascending)
                val downloads = downloadHelper.downloads.value
                database.songTable.sortAll(sortBy, sortOrder, excludeHidden = false).map { songs ->
                    songs.fastFilter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED }
                }
            }
            else -> {
                if (playlistId.toLongOrNull() != null) {
                    val sortBy = context.preferences.getEnum("PlaylistSongsSortBy_$playlistId", PlaylistSongSortBy.Title)
                    val sortOrder = context.preferences.getEnum("PlaylistSongsSortOrder_$playlistId", SortOrder.Ascending)
                    database.songPlaylistMapTable.sortSongs(playlistId.toLong(), sortBy, sortOrder)
                } else {
                    val playlistPage = Innertube.playlistPage(browseId = playlistId.removePrefix(MODIFIED_PREFIX))?.getOrNull()
                    val songs = playlistPage?.songsPage?.items?.map { item -> item.asSong } ?: emptyList()
                    AutoSearchState.searchedSongs = (AutoSearchState.searchedSongs + songs).distinctBy { s -> s.id }
                    kotlinx.coroutines.flow.flowOf(songs)
                }
            }
        }
        return listOf(shuffleItem) + listFlow.first().map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, actualParentId) }
    }
}
