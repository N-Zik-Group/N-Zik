package app.n_zik.android.components.settings.common

import android.content.SharedPreferences
import androidx.core.content.edit
import app.it.fast4x.rimusic.utils.Preference
import app.it.fast4x.rimusic.utils.filterContentTypeKey
import app.it.fast4x.rimusic.utils.floatActionIconOffsetXkey
import app.it.fast4x.rimusic.utils.floatActionIconOffsetYkey
import app.it.fast4x.rimusic.utils.multiFloatActionIconOffsetXkey
import app.it.fast4x.rimusic.utils.multiFloatActionIconOffsetYkey

// Selected values are independent of each menu's configurable catalogue and visibility.
// Removing them reuses the actual readers, including context-dependent playlist defaults.
private val interfaceReaderSettingKeys = setOf(
    Preference.HOME_ARTIST_ITEM_SIZE.key,
    Preference.HOME_ALBUM_ITEM_SIZE.key,
    Preference.HOME_LIBRARY_ITEM_SIZE.key,
    Preference.HOME_SONGS_TOP_PLAYLIST_PERIOD.key,
    Preference.HOME_SONGS_SORT_BY.key,
    Preference.HOME_SONGS_FAVORITES_SORT_BY.key,
    Preference.HOME_SONGS_OFFLINE_SORT_BY.key,
    Preference.HOME_SONGS_DOWNLOADED_SORT_BY.key,
    Preference.HOME_SONGS_TOP_SORT_BY.key,
    Preference.HOME_SONGS_DISLIKED_SORT_BY.key,
    Preference.HOME_ON_DEVICE_SONGS_SORT_BY.key,
    Preference.HOME_ARTISTS_FAVORITES_SORT_BY.key,
    Preference.HOME_ARTISTS_LIBRARY_SORT_BY.key,
    Preference.HOME_ARTISTS_DISLIKED_SORT_BY.key,
    Preference.HOME_ALBUMS_FAVORITES_SORT_BY.key,
    Preference.HOME_ALBUMS_LIBRARY_SORT_BY.key,
    Preference.HOME_ALBUMS_DISLIKED_SORT_BY.key,
    Preference.HOME_LIBRARY_PLAYLIST_SORT_BY.key,
    Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_BY.key,
    Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY.key,
    Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_BY.key,
    Preference.HISTORY_SORT_BY.key,
    Preference.HOME_SONGS_SORT_ORDER.key,
    Preference.HOME_SONGS_FAVORITES_SORT_ORDER.key,
    Preference.HOME_SONGS_OFFLINE_SORT_ORDER.key,
    Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER.key,
    Preference.HOME_SONGS_TOP_SORT_ORDER.key,
    Preference.HOME_SONGS_DISLIKED_SORT_ORDER.key,
    Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER.key,
    Preference.HOME_ARTISTS_FAVORITES_SORT_ORDER.key,
    Preference.HOME_ARTISTS_LIBRARY_SORT_ORDER.key,
    Preference.HOME_ARTISTS_DISLIKED_SORT_ORDER.key,
    Preference.HOME_ALBUMS_FAVORITES_SORT_ORDER.key,
    Preference.HOME_ALBUMS_LIBRARY_SORT_ORDER.key,
    Preference.HOME_ALBUMS_DISLIKED_SORT_ORDER.key,
    Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER.key,
    Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER.key,
    Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER.key,
    Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_ORDER.key,
    Preference.HISTORY_SORT_ORDER.key,
    Preference.SEARCH_RESULT_GRID_STATES.key,
    filterContentTypeKey,
    floatActionIconOffsetXkey,
    floatActionIconOffsetYkey,
    multiFloatActionIconOffsetXkey,
    multiFloatActionIconOffsetYkey,
)

private val playlistPresentationKeys = listOf(
    Regex("^PlaylistSongsSortBy_-?[0-9]+$"),
    Regex("^PlaylistSongsSortOrder_-?[0-9]+$"),
    Regex("^playlist_insertion_position_-?[0-9]+$"),
)
private val playlistRefreshKey = Regex("^playlist_refresh_on_open_-?[0-9]+$")

/** Restores active-profile presentation controls without deleting saved user content. */
internal fun resetInterfaceReaderDefaults(preferences: SharedPreferences) {
    val playlistKeys = preferences.all.keys.filter { key -> playlistPresentationKeys.any { it.matches(key) } }
    preferences.edit {
        interfaceReaderSettingKeys.forEach { remove(it) }
        playlistKeys.forEach { remove(it) }
    }
}

/** Restores source-dependent refresh behavior while preserving playlists and sync state. */
internal fun resetPlaylistRefreshDefaults(preferences: SharedPreferences) {
    val keys = preferences.all.keys.filter(playlistRefreshKey::matches)
    preferences.edit { keys.forEach { remove(it) } }
}
