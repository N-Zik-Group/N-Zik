package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.components.menu.libraryOriginFilters
import app.n_zik.android.components.menu.originIconRes
import app.n_zik.android.components.menu.originLabelRes
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.enums.FilterBy
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.YouTubeArtistProvenance
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.preferences
import kotlinx.coroutines.flow.first

private const val ORIGIN_SEPARATOR = "_ORIGIN_"

internal fun autoOriginParent(category: String): String = category.substringBefore(ORIGIN_SEPARATOR)

internal fun autoOriginSelection(category: String): FilterBy? {
    val value = category.substringAfter(ORIGIN_SEPARATOR, "")
    return libraryOriginFilters.firstOrNull { it.name == value }
}

internal fun autoOriginAvailable(category: String): Boolean = when (autoOriginParent(category)) {
    AutoSessionConstants.ID_ALBUMS_LIBRARY,
    AutoSessionConstants.ID_ALBUMS_FAVORITES,
    AutoSessionConstants.ID_ALBUMS_DISLIKED,
    AutoSessionConstants.ID_SONGS_FAVORITES,
    AutoSessionConstants.ID_SONGS_DISLIKED -> isYouTubeSyncEnabled()
    AutoSessionConstants.ID_ARTISTS_LIBRARY,
    AutoSessionConstants.ID_ARTISTS_FAVORITES,
    AutoSessionConstants.ID_ARTISTS_DISLIKED -> YouTubeArtistProvenance.showOrigin.value
    else -> false
}

internal fun <T> autoOriginFolders(
    context: Context,
    category: String,
    items: List<T>,
    isYouTube: (T) -> Boolean,
): List<MediaItem> = libraryOriginFilters.map { filter ->
    AutoMediaItemMapper.browsableMediaItem(
        "$category$ORIGIN_SEPARATOR${filter.name}",
        context.getString(filter.originLabelRes),
        items.count { filter.matchesOrigin(isYouTube(it)) }.toString(),
        AutoMediaItemMapper.drawableUri(context, filter.originIconRes),
        MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        category,
    )
}

internal fun FilterBy.matchesOrigin(youtube: Boolean): Boolean = when (this) {
    FilterBy.All -> true
    FilterBy.Local -> !youtube
    FilterBy.YoutubeLibrary -> youtube
}

internal suspend fun loadAutoHomeAlbums(context: Context, database: Database, category: String): List<Album> {
    val prefs = context.preferences
    val base = autoOriginParent(category)
    val explicitOrigin = autoOriginSelection(category)
    if (base != category && (explicitOrigin == null || !autoOriginAvailable(base))) return emptyList()
    val source = explicitOrigin ?: FilterBy.All
    val localIds = if (base == AutoSessionConstants.ID_ALBUMS_LIBRARY && context.isDeviceLibraryEnabled()) {
        context.scanDeviceLibrary().albumIds
    } else emptySet()
    val albums = when (base) {
        AutoSessionConstants.ID_ALBUMS_LIBRARY -> database.albumTable.sortInLibrary(
            prefs.getEnum(Preference.HOME_ALBUMS_LIBRARY_SORT_BY.key, Preference.HOME_ALBUMS_LIBRARY_SORT_BY.default),
            prefs.getEnum(Preference.HOME_ALBUMS_LIBRARY_SORT_ORDER.key, Preference.HOME_ALBUMS_LIBRARY_SORT_ORDER.default),
            includeDeviceGroups = localIds.isNotEmpty(),
        ).first()
        AutoSessionConstants.ID_ALBUMS_FAVORITES -> database.albumTable.sortBookmarked(
            prefs.getEnum(Preference.HOME_ALBUMS_FAVORITES_SORT_BY.key, Preference.HOME_ALBUMS_FAVORITES_SORT_BY.default),
            prefs.getEnum(Preference.HOME_ALBUMS_FAVORITES_SORT_ORDER.key, Preference.HOME_ALBUMS_FAVORITES_SORT_ORDER.default),
        ).first()
        AutoSessionConstants.ID_ALBUMS_DISLIKED -> database.albumTable.allDisliked().first()
        else -> emptyList()
    }
    return albums.filter { album ->
        (!isDeviceAlbumId(album.id) || album.id in localIds) &&
            (base == AutoSessionConstants.ID_ALBUMS_DISLIKED || album.dislikedAt == null) &&
            source.matchesOrigin(album.isYoutubeAlbum)
    }
}

internal suspend fun loadAutoHomeArtists(context: Context, database: Database, category: String): List<Artist> {
    val prefs = context.preferences
    val base = autoOriginParent(category)
    val explicitOrigin = autoOriginSelection(category)
    if (base != category && (explicitOrigin == null || !autoOriginAvailable(base))) return emptyList()
    val source = explicitOrigin ?: FilterBy.All
    val localIds = if (base == AutoSessionConstants.ID_ARTISTS_LIBRARY && context.isDeviceLibraryEnabled()) {
        context.scanDeviceLibrary().artistIds
    } else emptySet()
    val artists = when (base) {
        AutoSessionConstants.ID_ARTISTS_LIBRARY -> database.artistTable.sortInLibrary(
            prefs.getEnum(Preference.HOME_ARTISTS_LIBRARY_SORT_BY.key, Preference.HOME_ARTISTS_LIBRARY_SORT_BY.default),
            prefs.getEnum(Preference.HOME_ARTISTS_LIBRARY_SORT_ORDER.key, Preference.HOME_ARTISTS_LIBRARY_SORT_ORDER.default),
            includeDeviceGroups = localIds.isNotEmpty(),
        ).first()
        AutoSessionConstants.ID_ARTISTS_FAVORITES -> database.artistTable.sortFollowing(
            prefs.getEnum(Preference.HOME_ARTISTS_FAVORITES_SORT_BY.key, Preference.HOME_ARTISTS_FAVORITES_SORT_BY.default),
            prefs.getEnum(Preference.HOME_ARTISTS_FAVORITES_SORT_ORDER.key, Preference.HOME_ARTISTS_FAVORITES_SORT_ORDER.default),
        ).first()
        AutoSessionConstants.ID_ARTISTS_DISLIKED -> database.artistTable.allDisliked().first()
        else -> emptyList()
    }
    return artists.filter { artist ->
        (!isDeviceArtistId(artist.id) || artist.id in localIds) &&
            (base == AutoSessionConstants.ID_ARTISTS_DISLIKED || artist.dislikedAt == null) &&
            source.matchesOrigin(artist.isYoutubeArtist)
    }
}
