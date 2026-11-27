package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.ondevice.groupByDeviceAvailability
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.components.ui.screens.home.availableDevicePlaylistSongCounts
import app.n_zik.android.components.ui.screens.home.projectDevicePlaylistPreviews
import app.n_zik.android.utils.showOnDevicePlaylistsKey
import app.n_zik.android.utils.showMonthlyPlaylistsKey
import app.n_zik.android.utils.showPinnedPlaylistsKey
import app.n_zik.android.utils.showYtPlaylistsKey
import app.n_zik.android.enums.PlaylistSortBy
import app.n_zik.android.enums.PlaylistsType
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.preferences
import app.n_zik.android.components.ui.screens.home.HomeLibraryPlaylistScope
import app.n_zik.android.components.ui.screens.home.YouTubeLibraryFilter
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import kotlinx.coroutines.flow.first

private fun autoPlaylistType(parentId: String): PlaylistsType = when (parentId) {
        AutoSessionConstants.ID_PLAYLISTS_YT,
        AutoSessionConstants.ID_PLAYLISTS_YT_ALL,
        AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS,
        AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS -> PlaylistsType.YTPlaylist
        AutoSessionConstants.ID_PLAYLISTS_ON_DEVICE -> PlaylistsType.OnDevice
        AutoSessionConstants.ID_PLAYLISTS_PINNED -> PlaylistsType.PinnedPlaylist
        AutoSessionConstants.ID_PLAYLISTS_REWIND,
        AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL,
        AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH,
        AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR -> PlaylistsType.MonthlyPlaylist
        else -> PlaylistsType.Playlist
}

internal fun autoPlaylistCategoryVisible(context: Context, parentId: String): Boolean = when (autoPlaylistType(parentId)) {
    PlaylistsType.Playlist -> true
    PlaylistsType.OnDevice -> showAutoDevicePlaylists(context)
    PlaylistsType.PinnedPlaylist -> context.preferences.getBoolean(showPinnedPlaylistsKey, true)
    PlaylistsType.YTPlaylist -> context.preferences.getBoolean(showYtPlaylistsKey, true)
    PlaylistsType.MonthlyPlaylist ->
        RewindPlaylists.visibility(context).enabled && context.preferences.getBoolean(showMonthlyPlaylistsKey, true)
}

/** The same saved-source membership is used for counters, browse rows and aggregate playback. */
internal fun autoLibraryPlaylistScope(context: Context, parentId: String): HomeLibraryPlaylistScope {
    val rewind = RewindPlaylists.visibility(context)
    return HomeLibraryPlaylistScope(
        type = autoPlaylistType(parentId),
        youtubeFilter = when (parentId) {
            AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS -> YouTubeLibraryFilter.Playlists
            AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS -> YouTubeLibraryFilter.Podcasts
            else -> YouTubeLibraryFilter.All
        },
        query = "",
        monthlyEnabled = rewind.monthlyEnabled,
        yearlyEnabled = rewind.yearlyEnabled,
        rewindFilter = RewindPlaylists.Filter.All,
        rewindEnabled = rewind.enabled,
    )
}

/** A disabled Rewind subgroup stays empty even when the other generation type is enabled. */
internal fun visibleAutoLibraryPlaylists(
    scope: HomeLibraryPlaylistScope,
    parentId: String,
    previews: List<PlaylistPreview>,
    devicePlaylistIds: Set<Long> = emptySet(),
): List<PlaylistPreview> {
    val filter = when (parentId) {
        AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH -> RewindPlaylists.Filter.Month
        AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR -> RewindPlaylists.Filter.Year
        else -> null
    }
    return scope.visible(previews, devicePlaylistIds).let { visible ->
        if (filter == null) visible else visible.filter { RewindPlaylists.matches(filter, it.playlist.cleanName()) }
    }
}

/** Reads each phone category's sort settings without mutating its preferences. */
internal suspend fun loadAutoLibraryPlaylists(
    context: Context,
    database: Database,
    parentId: String,
    availableSongIds: Set<String>? = null,
): List<PlaylistPreview> {
    if (!autoPlaylistCategoryVisible(context, parentId)) return emptyList()
    val scope = autoLibraryPlaylistScope(context, parentId)
    val (sortKey, orderKey) = when (scope.type) {
        PlaylistsType.OnDevice -> Preference.HOME_ON_DEVICE_PLAYLISTS_SORT_BY to Preference.HOME_ON_DEVICE_PLAYLISTS_SORT_ORDER
        PlaylistsType.Playlist -> Preference.HOME_LIBRARY_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER
        PlaylistsType.YTPlaylist -> Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER
        PlaylistsType.PinnedPlaylist -> Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER
        PlaylistsType.MonthlyPlaylist -> Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_ORDER
    }
    val sortBy = context.preferences.getEnum(sortKey.key, PlaylistSortBy.Name)
    val sortOrder = context.preferences.getEnum(orderKey.key, SortOrder.Ascending)
    val deviceOnly = scope.type == PlaylistsType.OnDevice
    val groupByDevice = sortBy == PlaylistSortBy.OnDevice && context.isDeviceLibraryEnabled() &&
        (scope.type == PlaylistsType.Playlist || scope.type == PlaylistsType.PinnedPlaylist)
    val databaseSort = if (sortBy == PlaylistSortBy.OnDevice) PlaylistSortBy.Name else sortBy
    val databaseOrder = if (groupByDevice) SortOrder.Ascending else sortOrder
    val deviceCounts = if (deviceOnly || groupByDevice) {
        autoDevicePlaylistCounts(database, availableSongIds ?: context.scanDeviceLibrary().songIds)
    } else emptyMap()
    val visible = visibleAutoLibraryPlaylists(
        scope, parentId, database.playlistTable.sortPreviews(databaseSort, databaseOrder).first(), deviceCounts.keys,
    )
    if (deviceOnly) {
        return projectDevicePlaylistPreviews(visible, deviceCounts, sortOrder.takeIf { sortBy == PlaylistSortBy.SongCount })
    }
    return if (groupByDevice) visible.groupByDeviceAvailability(sortOrder) { it.playlist.id in deviceCounts } else visible
}

internal suspend fun autoDevicePlaylistCounts(database: Database, availableSongIds: Set<String>): Map<Long, Int> =
    availableDevicePlaylistSongCounts(database.songPlaylistMapTable.observeDeviceTrackMappings().first(), availableSongIds)

internal fun showAutoDevicePlaylists(context: Context): Boolean =
    context.isDeviceLibraryEnabled() && context.preferences.getBoolean(showOnDevicePlaylistsKey, true)
