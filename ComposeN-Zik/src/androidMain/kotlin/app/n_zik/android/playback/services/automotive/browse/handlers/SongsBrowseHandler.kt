package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.home.onDevice.getProfileLocalSongs
import app.n_zik.android.components.ui.screens.home.projectHomeLibrarySongs
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.core.ondevice.groupByDeviceAvailability
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
import app.n_zik.android.utils.excludeDislikedSongsKey
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
            fun shown(id: String) = autoSongCategoryVisible(context, id)
            val categories = linkedMapOf(
                "all" to SongFolder(AutoSessionConstants.ID_SONGS_ALL, R.string.common_option_all, R.drawable.musical_notes),
                "favorites" to SongFolder(AutoSessionConstants.ID_SONGS_FAVORITES, R.string.library_favorites_title, R.drawable.heart, shown(AutoSessionConstants.ID_SONGS_FAVORITES)),
                "disliked" to SongFolder(AutoSessionConstants.ID_SONGS_DISLIKED, R.string.library_disliked_title, R.drawable.heart_dislike, shown(AutoSessionConstants.ID_SONGS_DISLIKED)),
                "cached" to SongFolder(AutoSessionConstants.ID_SONGS_CACHED, R.string.library_cached_title, R.drawable.download, shown(AutoSessionConstants.ID_SONGS_CACHED)),
                "downloaded" to SongFolder(AutoSessionConstants.ID_SONGS_DOWNLOADED, R.string.library_downloaded_title, R.drawable.downloaded, shown(AutoSessionConstants.ID_SONGS_DOWNLOADED)),
                "top" to SongFolder(AutoSessionConstants.ID_SONGS_TOP, R.string.playlist_top, R.drawable.trending, shown(AutoSessionConstants.ID_SONGS_TOP)),
                "on_device" to SongFolder(AutoSessionConstants.ID_SONGS_ONDEVICE, R.string.library_on_device_title, R.drawable.folder, shown(AutoSessionConstants.ID_SONGS_ONDEVICE)),
            )
            return homeCategoryOrder(context, homeSongsOrderKey, categories.keys.toList()).mapNotNull { key ->
                val folder = categories[key]?.takeIf { it.visible } ?: return@mapNotNull null
                val count = countAutoFolder(folder.id) {
                    loadAutoHomeSongs(context, database, downloadHelper, binder, folder.id, excludedIds).size
                }
                browsableMediaItem(folder.id, context.getString(folder.label), count,
                    drawableUri(context, folder.icon), MediaMetadata.MEDIA_TYPE_PLAYLIST)
            }
        }
        if (!autoSongCategoryVisible(context, autoOriginParent(parentId))) return emptyList()
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

private fun autoSongCategoryVisible(context: Context, id: String): Boolean {
    val prefs = context.preferences
    return when (id) {
        AutoSessionConstants.ID_SONGS_FAVORITES -> prefs.getBoolean(showFavoritesPlaylistKey, true)
        AutoSessionConstants.ID_SONGS_DISLIKED -> prefs.getEnum(excludeDislikedSongsKey, DislikeMode.Enabled).isEnabled
        AutoSessionConstants.ID_SONGS_CACHED -> prefs.getBoolean(showCachedPlaylistKey, true)
        AutoSessionConstants.ID_SONGS_DOWNLOADED -> prefs.getBoolean(showDownloadedPlaylistKey, true)
        AutoSessionConstants.ID_SONGS_TOP -> prefs.getBoolean(showMyTopPlaylistKey, true)
        AutoSessionConstants.ID_SONGS_ONDEVICE -> context.isDeviceLibraryEnabled() && prefs.getBoolean(showOnDevicePlaylistKey, true)
        else -> true
    }
}

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
    if (!autoSongCategoryVisible(context, category)) return emptyList()
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
    val groupByDevice = sort == SongSortBy.OnDevice && explicitOrigin != FilterBy.YoutubeLibrary &&
        context.isDeviceLibraryEnabled() && when (category) {
            AutoSessionConstants.ID_SONGS_ALL, AutoSessionConstants.ID_SONGS_FAVORITES,
            AutoSessionConstants.ID_SONGS_DISLIKED -> true
            else -> false
        }
    val baseSort = if (sortByDownload || sort == SongSortBy.OnDevice) SongSortBy.Title else sort
    val baseOrder = if (groupByDevice || (sortByDownload && category != AutoSessionConstants.ID_SONGS_DOWNLOADED)) {
        SortOrder.Ascending
    } else order
    val downloadedIds = if (sortByDownload || category == AutoSessionConstants.ID_SONGS_DOWNLOADED) {
        downloadHelper.getDownloadManager(context)
        downloadHelper.downloads.value.filterValues { it.state == Download.STATE_COMPLETED }.keys
    } else emptySet()
    val useDeviceTracks = explicitOrigin != FilterBy.YoutubeLibrary && context.isDeviceLibraryEnabled() &&
        (category == AutoSessionConstants.ID_SONGS_ALL || category == AutoSessionConstants.ID_SONGS_FAVORITES ||
            category == AutoSessionConstants.ID_SONGS_DISLIKED || category == AutoSessionConstants.ID_SONGS_TOP)
    val localIds = if (useDeviceTracks) {
        context.scanDeviceLibrary().songIds
    } else emptySet()
    val songs = when (category) {
        AutoSessionConstants.ID_SONGS_ALL -> {
            val candidates = database.songTable.sortAll(baseSort, baseOrder, excludeHidden = !useDeviceTracks).first()
            projectHomeLibrarySongs(candidates, localIds, excludeHidden = true)
        }
        AutoSessionConstants.ID_SONGS_FAVORITES -> database.songTable.sortFavorites(baseSort, baseOrder).first()
        AutoSessionConstants.ID_SONGS_DISLIKED -> database.songTable.sortAll(
            baseSort, baseOrder, excludeHidden = !useDeviceTracks, dislikedOnly = true,
        ).first()
        AutoSessionConstants.ID_SONGS_CACHED -> database.formatTable.sortAllWithSongs(baseSort, baseOrder, excludeHidden = true)
            .first().filter { binder?.cache?.hasCachedStream(it.song.id, it.format.contentLength) == true }.map { it.song }
        AutoSessionConstants.ID_SONGS_DOWNLOADED -> database.songTable.sortAll(baseSort, baseOrder).first()
            .filter { it.id in downloadedIds }
        AutoSessionConstants.ID_SONGS_TOP -> loadAutoTopSongs(context, database, localIds)
        AutoSessionConstants.ID_SONGS_ONDEVICE -> {
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
        val eligible = when {
            disliked -> it.likedAt == -1L
            category == AutoSessionConstants.ID_SONGS_ONDEVICE -> it.id !in excludedIds
            else -> it.likedAt != -1L && it.id !in excludedIds
        }
        val localAllowed = !it.isLocal || category == AutoSessionConstants.ID_SONGS_ONDEVICE || it.id in localIds
        eligible && localAllowed && source.matchesOrigin(it.isYoutubeSong) &&
            (!parentalControl || !it.title.hasExplicitPrefix())
    }
    if (groupByDevice) return filtered.groupByDeviceAvailability(order) { it.id in localIds }
    if (!sortByDownload || category in setOf(AutoSessionConstants.ID_SONGS_ONDEVICE, AutoSessionConstants.ID_SONGS_DOWNLOADED)) {
        return filtered
    }
    return if (order == SortOrder.Ascending) filtered.sortedByDescending { it.id in downloadedIds }
        else filtered.sortedBy { it.id in downloadedIds }
}

private suspend fun loadAutoTopSongs(context: Context, database: Database, localIds: Set<String>): List<Song> {
    val prefs = context.preferences
    val key = Preference.HOME_SONGS_TOP_PLAYLIST_PERIOD
    val from = prefs.getEnum(key.key, key.default).timeStampInMillis()
    val count = prefs.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`)
        .toInt(prefs.getInt(MaxTopPlaylistItemsCustomValueKey, 10))
    val durationLimit = prefs.getEnum(excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled)
    return database.eventTable.findSongsMostPlayedBetween(from = from).first()
        .asSequence()
        .filter { !it.isLocal || it.id in localIds }
        .take(count)
        .filter { song ->
            durationLimit == DurationInMinutes.Disabled ||
                song.durationText?.let { durationTextToMillis(it) < durationLimit.asMillis } == true
        }
        .toList()
}
