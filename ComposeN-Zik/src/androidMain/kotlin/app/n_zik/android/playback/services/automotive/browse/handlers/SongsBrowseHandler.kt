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
import app.n_zik.android.playback.services.automotive.DislikedExclusion
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
        // Triple dislike exclusion mirroring the phone shuffler (Shuffler):
        // disliked songs + songs by disliked artists + songs from disliked
        // albums, each gated by its phone DislikeMode (default Enabled).
        // Every song category is filtered with it; the Disliked category
        // itself is never filtered. Empty set = no filter.
        val excludedIds = DislikedExclusion.excludedSongIds(context, database)
        return when (parentId) {
            PlayerServiceModern.SONG -> {
                val showFavoritesPlaylist = try { context.preferences.getBoolean(showFavoritesPlaylistKey, true) } catch (e: Exception) { true }
                val showDownloadedPlaylist = try { context.preferences.getBoolean(showDownloadedPlaylistKey, true) } catch (e: Exception) { true }
                val showCachedPlaylist = try { context.preferences.getBoolean(showCachedPlaylistKey, true) } catch (e: Exception) { true }
                val showOnDevicePlaylist = try { context.preferences.getBoolean(showOnDevicePlaylistKey, true) } catch (e: Exception) { true }
                val showTopPlaylist = try { context.preferences.getBoolean(showMyTopPlaylistKey, true) } catch (e: Exception) { true }
                // Phone/AA consistency: every song category below is filtered with
                // the shuffler exclusions (excludedIds); the excluded songs only
                // live in the dedicated Disliked folder. Exception: Top is
                // excluded at the SQL level — EventTable.findSongsMostPlayedBetween
                // already drops likedAt == -1 (pre-existing, identical on the
                // phone) — so its in-memory exclusion guard is redundant (kept
                // defensively) and Top does NOT re-include them when the songs
                // DislikeMode is Disabled.
                val dislikedSongs = database.songTable.allDisliked().first().filter { it.likedAt == -1L }
                val allCount = database.songTable.sortAll(SongSortBy.DateAdded, SortOrder.Descending, excludeHidden = true).first().count { it.id !in excludedIds }
                val favoritesCount = database.songTable.allFavorites().first().count { it.id !in excludedIds }
                val downloadedCount = getCountDownloadedSongs(downloadHelper, context, excludedIds).first()
                val onDeviceCount = database.songTable.allOnDevice().first().count { it.id !in excludedIds }
                val cachedCount = getCountCachedSongs(database, binder, excludedIds).first()
                val topCount = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt(context.preferences.getInt(MaxTopPlaylistItemsCustomValueKey, 10))).first().count { it.id !in excludedIds }
                val dislikedCount = dislikedSongs.size
                // Order and visibility mirror the in-app HomeSongs settings
                // (homeSongsOrderKey + the HomeSongsSettingsDialog toggles).
                // Deliberate visibility divergence from the phone: there the
                // Disliked chip is hidden when the songs DislikeMode is
                // disabled, while AA always shows this folder (user decision:
                // the Disliked category is always shown, never filtered).
                val categoryItems = mapOf(
                    "all" to browsableMediaItem(AutoSessionConstants.ID_SONGS_ALL, context.getString(R.string.all), allCount.toString(), drawableUri(context, R.drawable.musical_notes), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                    "favorites" to browsableMediaItem(AutoSessionConstants.ID_SONGS_FAVORITES, context.getString(R.string.favorites), favoritesCount.toString(), drawableUri(context, R.drawable.heart), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                    "disliked" to browsableMediaItem(AutoSessionConstants.ID_SONGS_DISLIKED, context.getString(R.string.disliked), dislikedCount.toString(), drawableUri(context, R.drawable.heart_dislike), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                    "cached" to browsableMediaItem(AutoSessionConstants.ID_SONGS_CACHED, context.getString(R.string.cached), cachedCount.toString(), drawableUri(context, R.drawable.download), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                    "downloaded" to browsableMediaItem(AutoSessionConstants.ID_SONGS_DOWNLOADED, context.getString(R.string.downloaded), downloadedCount.toString(), drawableUri(context, R.drawable.downloaded), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                    "top" to browsableMediaItem(AutoSessionConstants.ID_SONGS_TOP, context.getString(R.string.playlist_top), topCount.toString(), drawableUri(context, R.drawable.trending), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                    "on_device" to browsableMediaItem(AutoSessionConstants.ID_SONGS_ONDEVICE, context.getString(R.string.on_device), onDeviceCount.toString(), drawableUri(context, R.drawable.devices), MediaMetadata.MEDIA_TYPE_PLAYLIST)
                )
                homeCategoryOrder(context, homeSongsOrderKey, listOf("all", "favorites", "disliked", "cached", "downloaded", "top", "on_device"))
                    .mapNotNull { id ->
                        when (id) {
                            "favorites" -> if (showFavoritesPlaylist) categoryItems[id] else null
                            "cached" -> if (showCachedPlaylist) categoryItems[id] else null
                            "downloaded" -> if (showDownloadedPlaylist) categoryItems[id] else null
                            "top" -> if (showTopPlaylist) categoryItems[id] else null
                            "on_device" -> if (showOnDevicePlaylist) categoryItems[id] else null
                            else -> categoryItems[id]
                        }
                    }
            }
            AutoSessionConstants.ID_SONGS_TOP -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_TOP_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_TOP_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_TOP_SORT_ORDER.key, SortOrder.Ascending)
                val topIds = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt(context.preferences.getInt(MaxTopPlaylistItemsCustomValueKey, 10))).first().map { it.id }.toSet()
                val songs = database.songTable.sortAll(sortBy, sortOrder, excludeHidden = true).first().filter { it.id in topIds && it.id !in excludedIds }
                listOf(shuffleItem) + songs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
            }
            AutoSessionConstants.ID_SONGS_ALL -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_ALL_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_SORT_ORDER.key, SortOrder.Ascending)
                val songs = database.songTable.sortAll(sortBy, sortOrder, excludeHidden = true).first().filter { it.id !in excludedIds }.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
                listOf(shuffleItem) + songs
            }
            AutoSessionConstants.ID_SONGS_FAVORITES -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_FAVORITES_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_ORDER.key, SortOrder.Ascending)
                val songs = database.songTable.sortFavorites(sortBy, sortOrder).first().filter { it.id !in excludedIds }.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
                listOf(shuffleItem) + songs
            }
            AutoSessionConstants.ID_SONGS_DOWNLOADED -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_DOWNLOADED_SHUFFLE)
                downloadHelper.getDownloadManager(context)
                val downloads = downloadHelper.downloads.value
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER.key, SortOrder.Ascending)
                val songs = database.songTable.sortAll(sortBy, sortOrder, excludeHidden = false).first()
                    .filter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED && song.id !in excludedIds }
                listOf(shuffleItem) + songs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
            }
            AutoSessionConstants.ID_SONGS_ONDEVICE -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_ONDEVICE_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_BY.key, OnDeviceSongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER.key, SortOrder.Ascending)
                val songs = context.getLocalSongs(sortBy, sortOrder).first().keys.filter { it.id !in excludedIds }.toList()
                listOf(shuffleItem) + songs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
            }
            AutoSessionConstants.ID_SONGS_CACHED -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_CACHED_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_ORDER.key, SortOrder.Ascending)
                val songs = database.formatTable.sortAllWithSongs(sortBy, sortOrder).first()
                    .filter { itf -> itf.song.id !in excludedIds && itf.format.contentLength != null && (if (binder != null) binder.cache.isCached(itf.song.id, 0L, itf.format.contentLength ?: 0L) else false) }
                    .map { itf -> itf.song }
                listOf(shuffleItem) + songs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
            }
            AutoSessionConstants.ID_SONGS_DISLIKED -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_SONGS_DISLIKED_SHUFFLE)
                val songs = DislikedExclusion.dislikedSongs(context, database)
                listOf(shuffleItem) + songs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
            }
            else -> emptyList()
        }
    }

    private fun getCountCachedSongs(database: Database, binder: PlayerServiceModern.Binder?, dislikedIds: Set<String>): Flow<Int> = database.formatTable.allWithSongs().map { flist ->
        if (binder == null) return@map 0
        flist.filter { itf ->
            val contentLength = itf.format.contentLength
            itf.song.id !in dislikedIds && contentLength != null && binder.cache.isCached(itf.song.id, 0L, contentLength)
        }.size
    }

    private fun getCountDownloadedSongs(downloadHelper: MyDownloadHelper, context: Context, dislikedIds: Set<String>): Flow<Int> {
        downloadHelper.getDownloadManager(context)
        return downloadHelper.downloads.map { dm -> dm.filter { ite -> ite.value.state == Download.STATE_COMPLETED && ite.key !in dislikedIds }.size }
    }
}
