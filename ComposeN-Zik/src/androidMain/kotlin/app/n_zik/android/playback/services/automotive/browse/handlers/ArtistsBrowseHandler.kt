package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.ondevice.isDeviceArtistId
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.homeArtistsOrderKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.showFavoritesArtistKey
import app.n_zik.android.utils.showOnDeviceArtistsKey
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import kotlinx.coroutines.flow.first
import app.n_zik.android.enums.ArtistSortBy

class ArtistsBrowseHandler internal constructor(
    private val onDevice: AutoDeviceGroupsBrowseHandler = AutoDeviceGroupsBrowseHandler(),
) : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId == PlayerServiceModern.ARTIST ||
            parentId.startsWith("ARTISTS_") || parentId.startsWith("ID_ARTISTS_")

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        return when (parentId) {
            PlayerServiceModern.ARTIST -> {
                // Mirror the phone's HomeArtist tabs: same order (homeArtistsOrderKey)
                // and visibility (showFavoritesArtistKey) as the in-app settings
//
                // dialog. Disliked artists stay always hidden from All/Favorites
                // and always shown in their dedicated folder — opening one + the
//
                // DISLIKE_ARTIST command removes its dislike (toggle).
                val showFavoritesArtist = try { context.preferences.getBoolean(showFavoritesArtistKey, true) } catch (e: Exception) { true }
                val onDeviceCount = if (context.isDeviceLibraryEnabled() && context.preferences.getBoolean(showOnDeviceArtistsKey, true)) {
                    onDevice.artists(context, database).size
                } else null
                val libraryCount = database.artistTable.allInLibrary().first().count { it.dislikedAt == null && !isDeviceArtistId(it.id) }
                val favoritesCount = database.artistTable.allFollowing().first().count { it.dislikedAt == null && !isDeviceArtistId(it.id) }
                val dislikedCount = database.artistTable.allDisliked().first().size
                val categoryItems = mutableMapOf(
                    "all" to browsableMediaItem(AutoSessionConstants.ID_ARTISTS_LIBRARY, context.getString(R.string.library_title), libraryCount.toString(), drawableUri(context, R.drawable.artist), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                    "favorites" to browsableMediaItem(AutoSessionConstants.ID_ARTISTS_FAVORITES, context.getString(R.string.library_favorites_title), favoritesCount.toString(), drawableUri(context, R.drawable.heart), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                    "disliked" to browsableMediaItem(AutoSessionConstants.ID_ARTISTS_DISLIKED, context.getString(R.string.library_disliked_title), dislikedCount.toString(), drawableUri(context, R.drawable.heart_dislike), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
                )
                if (onDeviceCount != null) {
                    categoryItems["on_device"] = browsableMediaItem(
                        AutoSessionConstants.ID_ARTISTS_ONDEVICE, context.getString(R.string.library_on_device_title),
                        onDeviceCount.toString(), drawableUri(context, R.drawable.folder), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
                    )
                }
                homeCategoryOrder(context, homeArtistsOrderKey, listOf("all", "favorites", "disliked", "on_device"))
                    .mapNotNull { id -> if (id == "favorites" && !showFavoritesArtist) null else categoryItems[id] }
            }
            AutoSessionConstants.ID_ARTISTS_LIBRARY -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_ARTISTS_LIBRARY_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_ARTISTS_LIBRARY_SORT_BY.key, ArtistSortBy.Name)
                val sortOrder = context.preferences.getEnum(Preference.HOME_ARTISTS_LIBRARY_SORT_ORDER.key, SortOrder.Ascending)
                val artists = database.artistTable.sortInLibrary(sortBy, sortOrder).first().filter { it.dislikedAt == null && !isDeviceArtistId(it.id) }.map { artist -> CancellableAutoMediaItemMapper.mapArtistToMediaItem(PlayerServiceModern.ARTIST, artist.id, artist.name ?: "", artist.thumbnailUrl) }
                listOf(shuffleItem) + artists
            }
            AutoSessionConstants.ID_ARTISTS_FAVORITES -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_ARTISTS_FAVORITES_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_ARTISTS_FAVORITES_SORT_BY.key, ArtistSortBy.Name)
                val sortOrder = context.preferences.getEnum(Preference.HOME_ARTISTS_FAVORITES_SORT_ORDER.key, SortOrder.Ascending)
                val artists = database.artistTable.sortFollowing(sortBy, sortOrder).first().filter { it.dislikedAt == null && !isDeviceArtistId(it.id) }.map { artist -> CancellableAutoMediaItemMapper.mapArtistToMediaItem(PlayerServiceModern.ARTIST, artist.id, artist.name ?: "", artist.thumbnailUrl) }
                listOf(shuffleItem) + artists
            }
            AutoSessionConstants.ID_ARTISTS_DISLIKED -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_ARTISTS_DISLIKED_SHUFFLE)
                val artists = database.artistTable.allDisliked().first()
                listOf(shuffleItem) + artists.map { artist -> CancellableAutoMediaItemMapper.mapArtistToMediaItem(PlayerServiceModern.ARTIST, artist.id, artist.name ?: "", artist.thumbnailUrl) }
            }
            else -> emptyList()
        }
    }
}
