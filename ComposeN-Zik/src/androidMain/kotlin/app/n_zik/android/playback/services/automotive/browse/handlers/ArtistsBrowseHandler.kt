package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.ondevice.isDeviceArtistId
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.utils.homeArtistsOrderKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.showOnDeviceArtistsKey
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri

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
        return when (autoOriginParent(parentId)) {
            PlayerServiceModern.ARTIST -> {
                // Mirror the phone's HomeArtist tabs: same order (homeArtistsOrderKey)
                // and visibility (showFavoritesArtistKey) as the in-app settings
//
                // dialog. Disliked artists stay always hidden from All/Favorites
                // and always shown in their dedicated folder — opening one + the
//
                // DISLIKE_ARTIST command removes its dislike (toggle).
                val showFavorites = autoHomeCollectionVisible(context, AutoSessionConstants.ID_ARTISTS_FAVORITES)
                val showDisliked = autoHomeCollectionVisible(context, AutoSessionConstants.ID_ARTISTS_DISLIKED)
                val onDeviceCount = if (context.isDeviceLibraryEnabled() && context.preferences.getBoolean(showOnDeviceArtistsKey, true)) {
                    onDevice.artists(context, database).size
                } else null
                val libraryCount = countAutoFolder(AutoSessionConstants.ID_ARTISTS_LIBRARY) {
                    loadAutoHomeArtists(context, database, AutoSessionConstants.ID_ARTISTS_LIBRARY).size
                }
                val favoritesCount = if (showFavorites) countAutoFolder(AutoSessionConstants.ID_ARTISTS_FAVORITES) {
                    loadAutoHomeArtists(context, database, AutoSessionConstants.ID_ARTISTS_FAVORITES).size
                } else null
                val dislikedCount = if (showDisliked) countAutoFolder(AutoSessionConstants.ID_ARTISTS_DISLIKED) {
                    loadAutoHomeArtists(context, database, AutoSessionConstants.ID_ARTISTS_DISLIKED).size
                } else null
                val categoryItems = mutableMapOf(
                    "all" to browsableMediaItem(AutoSessionConstants.ID_ARTISTS_LIBRARY, context.getString(R.string.common_option_all), libraryCount, drawableUri(context, R.drawable.artist), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                    "favorites" to browsableMediaItem(AutoSessionConstants.ID_ARTISTS_FAVORITES, context.getString(R.string.library_favorites_title), favoritesCount, drawableUri(context, R.drawable.heart), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                    "disliked" to browsableMediaItem(AutoSessionConstants.ID_ARTISTS_DISLIKED, context.getString(R.string.library_disliked_title), dislikedCount, drawableUri(context, R.drawable.heart_dislike), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
                )
                if (onDeviceCount != null) {
                    categoryItems["on_device"] = browsableMediaItem(
                        AutoSessionConstants.ID_ARTISTS_ONDEVICE, context.getString(R.string.library_on_device_title),
                        onDeviceCount.toString(), drawableUri(context, R.drawable.folder), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
                    )
                }
                homeCategoryOrder(context, homeArtistsOrderKey, listOf("all", "favorites", "disliked", "on_device"))
                    .mapNotNull { id ->
                        when (id) {
                            "favorites" -> categoryItems[id].takeIf { showFavorites }
                            "disliked" -> categoryItems[id].takeIf { showDisliked }
                            else -> categoryItems[id]
                        }
                    }
            }
            AutoSessionConstants.ID_ARTISTS_LIBRARY,
            AutoSessionConstants.ID_ARTISTS_FAVORITES,
            AutoSessionConstants.ID_ARTISTS_DISLIKED -> {
                if (!autoHomeCollectionVisible(context, autoOriginParent(parentId))) return emptyList()
                val collections = loadAutoHomeArtists(context, database, parentId)
                if (autoOriginParent(parentId) == parentId && autoOriginAvailable(parentId)) {
                    return autoOriginFolders(context, parentId, collections) { it.isYoutubeArtist }
                }
                val entries = collections.map { artist ->
                    val category = if (isDeviceArtistId(artist.id)) AutoSessionConstants.ID_ARTISTS_ONDEVICE
                        else PlayerServiceModern.ARTIST
                    CancellableAutoMediaItemMapper.mapArtistToMediaItem(
                        category, artist.id, artist.name.orEmpty(), artist.thumbnailUrl,
                    )
                }
                listOf(AutoSessionConstants.shuffleItem(context, "${parentId}_SHUFFLE")) + entries
            }
            else -> emptyList()
        }
    }
}
