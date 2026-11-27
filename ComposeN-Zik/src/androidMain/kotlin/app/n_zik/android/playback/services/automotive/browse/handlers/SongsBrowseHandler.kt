package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.home.onDevice.getProfileLocalSongs
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.FilterBy
import app.n_zik.android.enums.DurationInMinutes
import app.n_zik.android.enums.MaxTopPlaylistItems
import app.n_zik.android.enums.SongSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.DislikedExclusion
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.hasCachedStream
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.MaxTopPlaylistItemsCustomValueKey
import app.n_zik.android.utils.MaxTopPlaylistItemsKey
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.excludeSongsWithDurationLimitKey
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.homeSongsOrderKey
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.showCachedPlaylistKey
import app.n_zik.android.utils.showDownloadedPlaylistKey
import app.n_zik.android.utils.showFavoritesPlaylistKey
import app.n_zik.android.utils.showMyTopPlaylistKey
import app.n_zik.android.utils.showOnDevicePlaylistKey
import kotlinx.coroutines.flow.first

class SongsBrowseHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId == PlayerServiceModern.SONG ||
        parentId.startsWith("SONGS_")

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?,
    ): List<MediaItem> {
        val excludedIds = DislikedExclusion.excludedSongIds(context, database)
        if (parentId == PlayerServiceModern.SONG) {
            val prefs = context.preferences
            val categories = linkedMapOf(
                "all" to SongFolder(AutoSessionConstants.ID_SONGS_ALL, R.string.common_option_all, R.drawable.musical_notes),
                "favorites" to SongFolder(AutoSessionConstants.ID_SONGS_FAVORITES, R.string.library_favorites_title, R.drawable.heart, prefs.getBoolean(showFavoritesPlaylistKey, true)),
                // Rejected items remain reachable in Android Auto so the user can undo a dislike.
                "disliked" to SongFolder(AutoSessionConstants.ID_SONGS_DISLIKED, R.string.library_disliked_title, R.drawable.heart_dislike),
                "cached" to SongFolder(AutoSessionConstants.ID_SONGS_CACHED, R.string.library_cached_title, R.drawable.download, prefs.getBoolean(showCachedPlaylistKey, true)),
                "downloaded" to SongFolder(AutoSessionConstants.ID_SONGS_DOWNLOADED, R.string.library_downloaded_title, R.drawable.downloaded, prefs.getBoolean(showDownloadedPlaylistKey, true)),
                "top" to SongFolder(AutoSessionConstants.ID_SONGS_TOP, R.string.playlist_top, R.drawable.trending, prefs.getBoolean(showMyTopPlaylistKey, true)),
                "on_device" to SongFolder(AutoSessionConstants.ID_SONGS_ONDEVICE, R.string.library_on_device_title, R.drawable.folder, showDeviceSongs(context)),
            )
            return homeCategoryOrder(context, homeSongsOrderKey, categories.keys.toList()).mapNotNull { key ->
                val folder = categories[key]?.takeIf { it.visible } ?: return@mapNotNull null
                val count = loadAutoHomeSongs(context, database, downloadHelper, binder, folder.id, excludedIds).size
                browsableMediaItem(folder.id, context.getString(folder.label), count.toString(),
                    drawableUri(context, folder.icon), MediaMetadata.MEDIA_TYPE_PLAYLIST)
            }
        }
        val songs = loadAutoHomeSongs(context, database, downloadHelper, binder, parentId, excludedIds)
        if (autoOriginParent(parentId) == parentId && autoOriginAvailable(parentId)) {
            return autoOriginFolders(context, parentId, songs, Song::isYoutubeSong)
        }
        if (songs.isEmpty()) return emptyList()
        return listOf(AutoSessionConstants.shuffleItem(context, "${parentId}_SHUFFLE")) +
            songs.map { CancellableAutoMediaItemMapper.mapSongToMediaItem(it, parentId, loadArtwork = it.isLocal) }
    }

    private data class SongFolder(val id: String, val label: Int, val icon: Int, val visible: Boolean = true)
}

private fun showDeviceSongs(context: Context): Boolean =
    context.isDeviceLibraryEnabled() && context.preferences.getBoolean(showOnDevicePlaylistKey, true)

/** Browse, counts and playback resolve the same category, including its playback exclusions. */
internal suspend fun loadAutoHomeSongs(
    context: Context,
    database: Database,
    downloadHelper: MyDownloadHelper,
    binder: PlayerServiceModern.Binder?,
    parentId: String,
    excludedIds: Set<String>,
): List<Song> {
    val prefs = context.preferences
    val category = autoOriginParent(parentId)
    val explicitOrigin = autoOriginSelection(parentId)
    if (category != parentId && (explicitOrigin == null || !autoOriginAvailable(category))) return emptyList()
    val (sortKey, orderKey) = when (category) {
        AutoSessionConstants.ID_SONGS_FAVORITES -> Preference.HOME_SONGS_FAVORITES_SORT_BY to Preference.HOME_SONGS_FAVORITES_SORT_ORDER
        AutoSessionConstants.ID_SONGS_DISLIKED -> Preference.HOME_SONGS_DISLIKED_SORT_BY to Preference.HOME_SONGS_DISLIKED_SORT_ORDER
        AutoSessionConstants.ID_SONGS_CACHED -> Preference.HOME_SONGS_OFFLINE_SORT_BY to Preference.HOME_SONGS_OFFLINE_SORT_ORDER
        AutoSessionConstants.ID_SONGS_DOWNLOADED -> Preference.HOME_SONGS_DOWNLOADED_SORT_BY to Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER
        AutoSessionConstants.ID_SONGS_TOP -> Preference.HOME_SONGS_TOP_SORT_BY to Preference.HOME_SONGS_TOP_SORT_ORDER
        else -> Preference.HOME_SONGS_SORT_BY to Preference.HOME_SONGS_SORT_ORDER
    }
    val sort = prefs.getEnum(sortKey.key, sortKey.default)
    val order = prefs.getEnum(orderKey.key, orderKey.default)
    val sortByDownload = sort == SongSortBy.Downloaded
    val baseSort = if (sortByDownload) SongSortBy.Title else sort
    val baseOrder = if (sortByDownload && category != AutoSessionConstants.ID_SONGS_DOWNLOADED) SortOrder.Ascending else order
    val downloadedIds = if (sortByDownload || category == AutoSessionConstants.ID_SONGS_DOWNLOADED) {
        downloadHelper.getDownloadManager(context)
        downloadHelper.downloads.value.filterValues { it.state == Download.STATE_COMPLETED }.keys
    } else emptySet()
    val localIds = if (category == AutoSessionConstants.ID_SONGS_ALL && context.isDeviceLibraryEnabled()) {
        context.scanDeviceLibrary().songIds
    } else emptySet()
    val songs = when (category) {
        AutoSessionConstants.ID_SONGS_ALL -> database.songTable.sortAll(baseSort, baseOrder,
            excludeHidden = localIds.isEmpty()).first().filter {
                if (it.isLocal) it.id in localIds && it.totalPlayTimeMs >= 0 else it.totalPlayTimeMs > 0
            }
        AutoSessionConstants.ID_SONGS_FAVORITES -> database.songTable.sortFavorites(baseSort, baseOrder).first()
        AutoSessionConstants.ID_SONGS_DISLIKED -> database.songTable.sortAll(baseSort, baseOrder, dislikedOnly = true).first()
        AutoSessionConstants.ID_SONGS_CACHED -> database.formatTable.sortAllWithSongs(baseSort, baseOrder, excludeHidden = true)
            .first().filter { binder?.cache?.hasCachedStream(it.song.id, it.format.contentLength) == true }.map { it.song }
        AutoSessionConstants.ID_SONGS_DOWNLOADED -> database.songTable.sortAll(baseSort, baseOrder).first()
            .filter { it.id in downloadedIds }
        AutoSessionConstants.ID_SONGS_TOP -> loadAutoTopSongs(context, database)
        AutoSessionConstants.ID_SONGS_ONDEVICE -> {
            if (!showDeviceSongs(context)) return emptyList()
            val deviceSort = Preference.HOME_ON_DEVICE_SONGS_SORT_BY
            val deviceOrder = Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER
            val storedById = database.songTable.allOnDevice().first().associateBy(Song::id)
            context.getProfileLocalSongs(prefs.getEnum(deviceSort.key, deviceSort.default),
                prefs.getEnum(deviceOrder.key, deviceOrder.default)).first().keys.map { storedById[it.id] ?: it }
        }
        else -> return emptyList()
    }
    val disliked = category == AutoSessionConstants.ID_SONGS_DISLIKED
    val source = explicitOrigin ?: FilterBy.All
    val parentalControl = prefs.getBoolean(parentalControlEnabledKey, false)
    val filtered = songs.filter {
        val eligible = if (disliked) it.likedAt == -1L else it.likedAt != -1L && it.id !in excludedIds
        val localAllowed = category == AutoSessionConstants.ID_SONGS_ALL || category == AutoSessionConstants.ID_SONGS_ONDEVICE || !it.isLocal
        eligible && localAllowed && source.matchesOrigin(it.isYoutubeSong) &&
            (!parentalControl || !it.title.startsWith(EXPLICIT_PREFIX, ignoreCase = true))
    }
    if (!sortByDownload || category in setOf(AutoSessionConstants.ID_SONGS_ONDEVICE, AutoSessionConstants.ID_SONGS_DOWNLOADED)) {
        return filtered
    }
    return if (order == SortOrder.Ascending) filtered.sortedByDescending { it.id in downloadedIds }
        else filtered.sortedBy { it.id in downloadedIds }
}

private suspend fun loadAutoTopSongs(context: Context, database: Database): List<Song> {
    val prefs = context.preferences
    val key = Preference.HOME_SONGS_TOP_PLAYLIST_PERIOD
    val from = prefs.getEnum(key.key, key.default).timeStampInMillis()
    val count = prefs.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`)
        .toInt(prefs.getInt(MaxTopPlaylistItemsCustomValueKey, 10))
    val durationLimit = prefs.getEnum(excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled)
    return database.eventTable.findSongsMostPlayedBetween(from = from, limit = count).first().filter { song ->
        durationLimit == DurationInMinutes.Disabled ||
            song.durationText?.let { durationTextToMillis(it) < durationLimit.asMillis } == true
    }
}
