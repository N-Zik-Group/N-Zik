package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.offline.Download
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.utils.*
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.kreate.android.me.knighthat.utils.getLocalSongs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import app.it.fast4x.rimusic.enums.OnDeviceSongSortBy

class SongsBrowseHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId == PlayerServiceModern.SONG ||
            parentId.startsWith("SONGS_")

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        return when (parentId) {
            PlayerServiceModern.SONG -> {
                val showFavoritesPlaylist = try { context.preferences.getBoolean(showFavoritesPlaylistKey, true) } catch (e: Exception) { true }
                val showDownloadedPlaylist = try { context.preferences.getBoolean(showDownloadedPlaylistKey, true) } catch (e: Exception) { true }
                val showCachedPlaylist = try { context.preferences.getBoolean(showCachedPlaylistKey, true) } catch (e: Exception) { true }
                val showOnDevicePlaylist = try { context.preferences.getBoolean(showOnDevicePlaylistKey, true) } catch (e: Exception) { true }
                val allCount = database.songTable.sortAll(SongSortBy.DateAdded, SortOrder.Descending, excludeHidden = true).first().size
                val favoritesCount = database.songTable.allFavorites().first().size
                val downloadedCount = getCountDownloadedSongs(downloadHelper, context).first()
                val onDeviceCount = database.songTable.allOnDevice().first().size
                val cachedCount = getCountCachedSongs(database, binder).first()
                val topCount = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt(context.preferences.getInt(MaxTopPlaylistItemsCustomValueKey, 10))).first().size
                val songs = mutableListOf(
                    browsableMediaItem(AutoSessionConstants.ID_SONGS_ALL, context.getString(R.string.all), allCount.toString(), drawableUri(context, R.drawable.musical_notes), MediaMetadata.MEDIA_TYPE_PLAYLIST)
                )
                if (showFavoritesPlaylist) {
                    songs.add(browsableMediaItem(AutoSessionConstants.ID_SONGS_FAVORITES, context.getString(R.string.favorites), favoritesCount.toString(), drawableUri(context, R.drawable.heart), MediaMetadata.MEDIA_TYPE_PLAYLIST))
                }
                if (showCachedPlaylist) {
                    songs.add(browsableMediaItem(AutoSessionConstants.ID_SONGS_CACHED, context.getString(R.string.cached), cachedCount.toString(), drawableUri(context, R.drawable.download), MediaMetadata.MEDIA_TYPE_PLAYLIST))
                }
                if (showDownloadedPlaylist) {
                    songs.add(browsableMediaItem(AutoSessionConstants.ID_SONGS_DOWNLOADED, context.getString(R.string.downloaded), downloadedCount.toString(), drawableUri(context, R.drawable.downloaded), MediaMetadata.MEDIA_TYPE_PLAYLIST))
                }
                songs.add(browsableMediaItem(AutoSessionConstants.ID_SONGS_TOP, context.getString(R.string.playlist_top), topCount.toString(), drawableUri(context, R.drawable.trending), MediaMetadata.MEDIA_TYPE_PLAYLIST))
                if (showOnDevicePlaylist) {
                    songs.add(browsableMediaItem(AutoSessionConstants.ID_SONGS_ONDEVICE, context.getString(R.string.on_device), onDeviceCount.toString(), drawableUri(context, R.drawable.devices), MediaMetadata.MEDIA_TYPE_PLAYLIST))
                }
                songs
            }
            AutoSessionConstants.ID_SONGS_TOP -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_TOP_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_TOP_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_TOP_SORT_ORDER.key, SortOrder.Ascending)
                val topIds = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt(context.preferences.getInt(MaxTopPlaylistItemsCustomValueKey, 10))).first().map { it.id }.toSet()
                val songs = database.songTable.sortAll(sortBy, sortOrder, excludeHidden = true).first().filter { it.id in topIds }
                listOf(shuffleItem) + songs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
            }
            AutoSessionConstants.ID_SONGS_ALL -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_ALL_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_SORT_ORDER.key, SortOrder.Ascending)
                val songs = database.songTable.sortAll(sortBy, sortOrder, excludeHidden = true).first().map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
                listOf(shuffleItem) + songs
            }
            AutoSessionConstants.ID_SONGS_FAVORITES -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_FAVORITES_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_ORDER.key, SortOrder.Ascending)
                val songs = database.songTable.sortFavorites(sortBy, sortOrder).first().map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
                listOf(shuffleItem) + songs
            }
            AutoSessionConstants.ID_SONGS_DOWNLOADED -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_DOWNLOADED_SHUFFLE)
                downloadHelper.getDownloadManager(context)
                val downloads = downloadHelper.downloads.value
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER.key, SortOrder.Ascending)
                val songs = database.songTable.sortAll(sortBy, sortOrder, excludeHidden = false).first()
                    .filter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED }
                listOf(shuffleItem) + songs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
            }
            AutoSessionConstants.ID_SONGS_ONDEVICE -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_ONDEVICE_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_BY.key, OnDeviceSongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER.key, SortOrder.Ascending)
                val songs = context.getLocalSongs(sortBy, sortOrder).first().keys.toList()
                listOf(shuffleItem) + songs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
            }
            AutoSessionConstants.ID_SONGS_CACHED -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_CACHED_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_ORDER.key, SortOrder.Ascending)
                val songs = database.formatTable.sortAllWithSongs(sortBy, sortOrder).first()
                    .filter { itf -> itf.format.contentLength != null && (if (binder != null) binder.cache.isCached(itf.song.id, 0L, itf.format.contentLength ?: 0L) else false) }
                    .map { itf -> itf.song }
                listOf(shuffleItem) + songs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
            }
            else -> emptyList()
        }
    }

    private fun getCountCachedSongs(database: Database, binder: PlayerServiceModern.Binder?): Flow<Int> = database.formatTable.allWithSongs().map { flist ->
        if (binder == null) return@map 0
        flist.filter { itf ->
            val contentLength = itf.format.contentLength
            contentLength != null && binder.cache.isCached(itf.song.id, 0L, contentLength)
        }.size
    }

    private fun getCountDownloadedSongs(downloadHelper: MyDownloadHelper, context: Context): Flow<Int> {
        downloadHelper.getDownloadManager(context)
        return downloadHelper.downloads.map { dm -> dm.filter { ite -> ite.value.state == Download.STATE_COMPLETED }.size }
    }
}
