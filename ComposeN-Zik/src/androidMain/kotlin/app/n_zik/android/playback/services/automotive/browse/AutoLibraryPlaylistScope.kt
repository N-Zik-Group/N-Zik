package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import app.it.fast4x.rimusic.enums.PlaylistSortBy
import app.it.fast4x.rimusic.enums.PlaylistsType
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.utils.Preference
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.showPinnedPlaylistsKey
import app.n_zik.android.components.ui.screens.home.HomeLibraryPlaylistScope
import app.n_zik.android.components.ui.screens.home.YouTubeLibraryFilter
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.utils.DataStoreUtils
import kotlinx.coroutines.flow.first

/** The same saved-source membership is used for counters, browse rows and aggregate playback. */
internal fun autoLibraryPlaylistScope(context: Context, parentId: String): HomeLibraryPlaylistScope {
    val type = when (parentId) {
        AutoSessionConstants.ID_PLAYLISTS_YT,
        AutoSessionConstants.ID_PLAYLISTS_YT_ALL,
        AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS,
        AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS -> PlaylistsType.YTPlaylist
        AutoSessionConstants.ID_PLAYLISTS_PINNED -> PlaylistsType.PinnedPlaylist
        AutoSessionConstants.ID_PLAYLISTS_REWIND,
        AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL,
        AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH,
        AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR -> PlaylistsType.MonthlyPlaylist
        else -> PlaylistsType.Playlist
    }
    return HomeLibraryPlaylistScope(
        type = type,
        youtubeFilter = when (parentId) {
            AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS -> YouTubeLibraryFilter.Playlists
            AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS -> YouTubeLibraryFilter.Podcasts
            else -> YouTubeLibraryFilter.All
        },
        query = "",
        showRewind = context.preferences.getBoolean(showMonthlyPlaylistsKey, true),
        showPinned = context.preferences.getBoolean(showPinnedPlaylistsKey, true),
        monthlyEnabled = DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true),
        yearlyEnabled = DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true),
        rewindFilter = RewindPlaylists.Filter.All,
    )
}

/** A disabled Rewind subgroup stays empty even when the other generation type is enabled. */
internal fun visibleAutoLibraryPlaylists(
    scope: HomeLibraryPlaylistScope,
    parentId: String,
    previews: List<PlaylistPreview>,
): List<PlaylistPreview> {
    val filter = when (parentId) {
        AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH -> RewindPlaylists.Filter.Month
        AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR -> RewindPlaylists.Filter.Year
        else -> null
    }
    return scope.visible(previews).let { visible ->
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
    val (sortKey, orderKey) = when (scope.type) {
        PlaylistsType.Playlist -> Preference.HOME_LIBRARY_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER
        PlaylistsType.YTPlaylist -> Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER
        PlaylistsType.PinnedPlaylist -> Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER
        PlaylistsType.MonthlyPlaylist -> Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_ORDER
    }
    val sortBy = context.preferences.getEnum(sortKey.key, PlaylistSortBy.Name)
    val sortOrder = context.preferences.getEnum(orderKey.key, SortOrder.Ascending)
    return visibleAutoLibraryPlaylists(scope, parentId, database.playlistTable.sortPreviews(sortBy, sortOrder).first())
}
