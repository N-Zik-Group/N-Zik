package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.utils.showOnDevicePlaylistsKey
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

/** The same saved-source membership is used for counters, browse rows and aggregate playback. */
internal fun autoLibraryPlaylistScope(context: Context, parentId: String): HomeLibraryPlaylistScope {
    val type = when (parentId) {
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
    val rewind = RewindPlaylists.visibility(context)
    return HomeLibraryPlaylistScope(
        type = type,
        youtubeFilter = when (parentId) {
            AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS -> YouTubeLibraryFilter.Playlists
            AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS -> YouTubeLibraryFilter.Podcasts
            else -> YouTubeLibraryFilter.All
        },
        query = "",
        monthlyEnabled = rewind.monthlyEnabled,
        yearlyEnabled = rewind.yearlyEnabled,
        rewindFilter = RewindPlaylists.Filter.All,
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
    val scopedPreviews = if (scope.type == PlaylistsType.OnDevice) previews.filter { it.playlist.id in devicePlaylistIds } else previews
    return scope.visible(scopedPreviews).let { visible ->
        if (filter == null) visible else visible.filter { RewindPlaylists.matches(filter, it.playlist.name) }
    }
}

/** Reads each phone category's sort settings without mutating its preferences. */
internal suspend fun loadAutoLibraryPlaylists(
    context: Context,
    database: Database,
    parentId: String,
): List<PlaylistPreview> {
    val scope = autoLibraryPlaylistScope(context, parentId)
    if (scope.type == PlaylistsType.OnDevice && !showAutoDevicePlaylists(context)) return emptyList()
    val (sortKey, orderKey) = when (scope.type) {
        PlaylistsType.OnDevice -> Preference.HOME_ON_DEVICE_PLAYLISTS_SORT_BY to Preference.HOME_ON_DEVICE_PLAYLISTS_SORT_ORDER
        PlaylistsType.Playlist -> Preference.HOME_LIBRARY_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER
        PlaylistsType.YTPlaylist -> Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER
        PlaylistsType.PinnedPlaylist -> Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER
        PlaylistsType.MonthlyPlaylist -> Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_ORDER
    }
    val sortBy = context.preferences.getEnum(sortKey.key, PlaylistSortBy.Name)
    val sortOrder = context.preferences.getEnum(orderKey.key, SortOrder.Ascending)
    val devicePlaylistIds = if (scope.type == PlaylistsType.OnDevice) {
        database.songPlaylistMapTable.onDeviceOnlyPlaylistIds().first().toSet()
    } else emptySet()
    return visibleAutoLibraryPlaylists(scope, parentId, database.playlistTable.sortPreviews(sortBy, sortOrder).first(), devicePlaylistIds)
}

internal fun showAutoDevicePlaylists(context: Context): Boolean =
    context.isDeviceLibraryEnabled() && context.preferences.getBoolean(showOnDevicePlaylistsKey, true)
