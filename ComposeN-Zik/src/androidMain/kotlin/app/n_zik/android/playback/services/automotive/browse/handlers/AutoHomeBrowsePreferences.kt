package app.n_zik.android.playback.services.automotive.browse.handlers

import app.n_zik.android.components.ui.screens.home.YOUTUBE_LIBRARY_FILTER_KEY
import app.n_zik.android.core.ondevice.DEVICE_LIBRARY_ENABLED_KEY
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.MaxTopPlaylistItemsCustomValueKey
import app.n_zik.android.utils.MaxTopPlaylistItemsKey
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.enableQuickPicksPageKey
import app.n_zik.android.utils.excludeDislikedAlbumsKey
import app.n_zik.android.utils.excludeDislikedArtistsKey
import app.n_zik.android.utils.excludeDislikedSongsKey
import app.n_zik.android.utils.excludeSongsWithDurationLimitKey
import app.n_zik.android.utils.filterByKey
import app.n_zik.android.utils.homeAlbumsOrderKey
import app.n_zik.android.utils.homeArtistsOrderKey
import app.n_zik.android.utils.homePlaylistsOrderKey
import app.n_zik.android.utils.homeSongsOrderKey
import app.n_zik.android.utils.homeTabsOrderKey
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.showCachedPlaylistKey
import app.n_zik.android.utils.showDownloadedPlaylistKey
import app.n_zik.android.utils.showFavoritesAlbumKey
import app.n_zik.android.utils.showFavoritesArtistKey
import app.n_zik.android.utils.showFavoritesPlaylistKey
import app.n_zik.android.utils.showMonthlyPlaylistsKey
import app.n_zik.android.utils.showMyTopPlaylistKey
import app.n_zik.android.utils.showOnDeviceAlbumsKey
import app.n_zik.android.utils.showOnDeviceArtistsKey
import app.n_zik.android.utils.showOnDevicePlaylistKey
import app.n_zik.android.utils.showOnDevicePlaylistsKey
import app.n_zik.android.utils.showPinnedPlaylistsKey
import app.n_zik.android.utils.showYtPlaylistsKey

private val homeBrowsePreferenceKeys = setOf(
    homeTabsOrderKey, enableQuickPicksPageKey, homeSongsOrderKey, homeArtistsOrderKey,
    homeAlbumsOrderKey, homePlaylistsOrderKey, filterByKey, YOUTUBE_LIBRARY_FILTER_KEY,
    RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, DEVICE_LIBRARY_ENABLED_KEY, parentalControlEnabledKey,
    showFavoritesPlaylistKey, showCachedPlaylistKey, showDownloadedPlaylistKey, showMyTopPlaylistKey,
    showOnDevicePlaylistKey, showFavoritesArtistKey, showOnDeviceArtistsKey, showFavoritesAlbumKey,
    showOnDeviceAlbumsKey, showPinnedPlaylistsKey, showMonthlyPlaylistsKey, showYtPlaylistsKey,
    showOnDevicePlaylistsKey, excludeDislikedSongsKey, excludeDislikedArtistsKey, excludeDislikedAlbumsKey,
    Preference.HOME_SONGS_TOP_PLAYLIST_PERIOD.key, MaxTopPlaylistItemsKey, MaxTopPlaylistItemsCustomValueKey,
    excludeSongsWithDurationLimitKey,
    "hometab_songs_enabled", "hometab_artists_enabled", "hometab_albums_enabled", "hometab_playlists_enabled",
)

private val homeSortKey = Regex("Home[A-Za-z]+Sort(?:By|Order)|PlaylistSongsSort(?:By|Order)_-?[0-9]+")

internal fun isAutoHomeBrowsePreference(key: String?): Boolean =
    key == null || key in homeBrowsePreferenceKeys || homeSortKey.matches(key)

internal fun isAutoRewindPreference(key: String?): Boolean = when (key) {
    null, DataStoreUtils.KEY_REWIND_ENABLED, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED,
    DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED -> true
    else -> false
}
