package app.n_zik.android.playback.services.automotive.browse.handlers

import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.playback.services.hasCachedStream
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap

import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.R
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.enums.MaxTopPlaylistItems
import app.n_zik.android.enums.SongSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.utils.MaxTopPlaylistItemsCustomValueKey
import app.n_zik.android.utils.MaxTopPlaylistItemsKey
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.withPodcastSource
import app.n_zik.android.components.menu.playlist.OnlinePlaylistContent
import app.n_zik.android.components.menu.playlist.loadOnlinePlaylistContent
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.database.podcastSourceBrowseId
import app.n_zik.android.playback.services.automotive.browse.loadAutoDeviceSortedFavorites
import app.n_zik.android.playback.services.automotive.browse.loadAutoPlaylistSongs
import app.n_zik.android.playback.services.automotive.browse.showAutoDevicePlaylists
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import app.n_zik.android.enums.OnDeviceSongSortBy
import app.n_zik.android.components.ui.screens.home.onDevice.getProfileLocalSongs
import app.n_zik.android.MODIFIED_PREFIX
import timber.log.Timber

class PlaylistDetailHandler internal constructor(
    private val loadOnlineContent: suspend (String, () -> Boolean) -> OnlinePlaylistContent? =
        { browseId, allowed -> loadOnlinePlaylistContent(browseId, fetchAllowed = allowed) },
) : BrowseHandler {
    override fun handles(parentId: String): Boolean {
        if (!parentId.startsWith("${PlayerServiceModern.PLAYLIST}/")) return false
        val parts = parentId.split("/")
        return parts.size == 2 || (parts.size == 3 && parts[1].toLongOrNull() != null && AutoMediaIdContract.isPlaylistSection(parts[2]))
    }

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        val parts = parentId.split("/")
        val playlistId = parts[1]
        playlistId.toLongOrNull()?.let { localId ->
            val section = parts.getOrNull(2)
            if (section == AutoMediaIdContract.ON_DEVICE_PLAYLIST_ROW && !showAutoDevicePlaylists(context)) return emptyList()
            val deviceOnly = AutoMediaIdContract.isDevicePlaylistSection(section)
            val songs = loadAutoPlaylistSongs(context, database, downloadHelper, localId, onDeviceOnly = deviceOnly)
            val deviceSongs = if (section == null && context.isDeviceLibraryEnabled() && songs.any { it.isLocal }) {
                loadAutoPlaylistSongs(context, database, downloadHelper, localId, onDeviceOnly = true)
            } else emptyList()
            if (section == null && deviceSongs.isNotEmpty()) {
                fun folder(sectionId: String, label: Int, icon: Int, count: Int) = browsableMediaItem(
                    "$parentId/$sectionId", context.getString(label), count.toString(),
                    drawableUri(context, icon), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS, parentId,
                )
                return listOf(
                    folder(AutoMediaIdContract.PLAYLIST_SECTION_ALL, R.string.common_option_all, R.drawable.library, songs.size),
                    folder(AutoMediaIdContract.PLAYLIST_SECTION_DEVICE, R.string.library_on_device_title, R.drawable.folder, deviceSongs.size),
                )
            }
            if (songs.isEmpty()) return emptyList()
            val action = if (deviceOnly) AutoMediaIdContract.devicePlaylistAction(localId) else playlistId
            val browseId = database.playlistTable.findById(localId).first()?.browseId
            return listOf(AutoSessionConstants.shuffleItem(context, AutoMediaIdContract.playlistShuffle(action))) +
                songs.map { song ->
                    CancellableAutoMediaItemMapper.mapSongToMediaItem(song, parentId, loadArtwork = song.isLocal)
                        .withPodcastSource(browseId)
                }
        }
        // The shuffle item must carry the playlist id so queue resolution can
        // find the right song list (issue #777).
        val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoMediaIdContract.playlistShuffle(playlistId))
        val listFlow = when (playlistId) {
            AutoSessionConstants.ID_FAVORITES -> {
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_ORDER.key, SortOrder.Ascending)
                if (sortBy == SongSortBy.OnDevice) {
                    flowOf(loadAutoDeviceSortedFavorites(context, database, sortOrder))
                } else database.songTable.sortFavorites(sortBy, sortOrder)
            }
            AutoSessionConstants.ID_CACHED -> {
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_ORDER.key, SortOrder.Ascending)
                database.formatTable.sortAllWithSongs(sortBy, sortOrder).map { flist ->
                    flist.fastFilter { itf -> binder?.cache?.hasCachedStream(itf.song.id, itf.format.contentLength) == true }.fastMap { itf -> itf.song }
                }
            }
            AutoSessionConstants.ID_TOP -> {
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_TOP_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_TOP_SORT_ORDER.key, SortOrder.Ascending)
                val topIds = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt(context.preferences.getInt(MaxTopPlaylistItemsCustomValueKey, 10))).first().map { it.id }.toSet()
                database.songTable.sortAll(sortBy, sortOrder, excludeHidden = true).map { songs -> songs.filter { it.id in topIds } }
            }
            AutoSessionConstants.ID_ONDEVICE -> {
                val sortBy = context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_BY.key, OnDeviceSongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER.key, SortOrder.Ascending)
                context.getProfileLocalSongs(sortBy, sortOrder).map { songs -> songs.keys.toList() }
            }
            AutoSessionConstants.ID_DOWNLOADED -> {
                val sortBy = context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_BY.key, SongSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER.key, SortOrder.Ascending)
                val downloads = downloadHelper.downloads.value
                database.songTable.sortAll(sortBy, sortOrder, excludeHidden = false).map { songs ->
                    songs.fastFilter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED }
                }
            }
            else -> return onlinePlaylistItems(parentId, playlistId, context, AutoAccessPolicy(context, database, downloadHelper, binder))
        }
        return listOf(shuffleItem) + listFlow.first().map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, parentId, loadArtwork = song.isLocal) }
    }

    private suspend fun onlinePlaylistItems(
        parentId: String,
        playlistId: String,
        context: Context,
        access: AutoAccessPolicy,
    ): List<MediaItem> {
        val profile = getActiveProfile(context)
        val canLoad = { access.online && profile == getActiveProfile(context) }
        if (!canLoad()) return emptyList()
        val browseId = playlistId.removePrefix(MODIFIED_PREFIX)
        val content = loadOnlineContent(browseId, canLoad)
        currentCoroutineContext().ensureActive()
        if (!canLoad()) return emptyList()
        if (content != null && content.songs == null) {
            Timber.tag("PlaylistDetailHandler").w("Using available tracks after an online collection continuation failed")
        }
        val parentalControl = context.preferences.getBoolean(parentalControlEnabledKey, false)
        val songs = content?.availableSongs.orEmpty()
            .filterNot { parentalControl && it.title.hasExplicitPrefix() }
            .distinctBy { it.id }
        val podcastId = podcastSourceBrowseId(browseId)
        val rows = songs.map { song ->
            CancellableAutoMediaItemMapper.mapSongToMediaItem(song, parentId).withPodcastSource(podcastId)
        }
        currentCoroutineContext().ensureActive()
        if (!canLoad()) return emptyList()
        AutoSearchState.playlistSongsById = AutoSearchState.playlistSongsById + (playlistId to songs)
        if (rows.isEmpty()) return emptyList()
        return listOf(AutoSessionConstants.shuffleItem(context, AutoMediaIdContract.playlistShuffle(playlistId))) + rows
    }
}
