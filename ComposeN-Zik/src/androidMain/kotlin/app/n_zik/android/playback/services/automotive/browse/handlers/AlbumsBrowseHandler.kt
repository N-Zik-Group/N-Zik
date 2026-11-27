package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.utils.homeAlbumsOrderKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.showFavoritesAlbumKey
import app.n_zik.android.utils.showOnDeviceAlbumsKey
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri

class AlbumsBrowseHandler internal constructor(
    private val onDevice: AutoDeviceGroupsBrowseHandler = AutoDeviceGroupsBrowseHandler(),
) : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId == PlayerServiceModern.ALBUM ||
            parentId.startsWith("ALBUMS_") || parentId.startsWith("ID_ALBUMS_")

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        return when (autoOriginParent(parentId)) {
            PlayerServiceModern.ALBUM -> {
                // Mirror the phone's HomeAlbum tabs: same order (homeAlbumsOrderKey)
                // and visibility (showFavoritesAlbumKey) as the in-app settings
//
                // dialog. Disliked albums stay always hidden from All/Favorites
                // and always shown in their dedicated folder — opening one + the
//
                // DISLIKE_ALBUM command removes its dislike (toggle).
                val showFavoritesAlbum = try { context.preferences.getBoolean(showFavoritesAlbumKey, true) } catch (e: Exception) { true }
                val onDeviceCount = if (context.isDeviceLibraryEnabled() && context.preferences.getBoolean(showOnDeviceAlbumsKey, true)) {
                    onDevice.albums(context, database).size
                } else null
                val libraryCount = loadAutoHomeAlbums(context, database, AutoSessionConstants.ID_ALBUMS_LIBRARY).size
                val favoritesCount = loadAutoHomeAlbums(context, database, AutoSessionConstants.ID_ALBUMS_FAVORITES).size
                val dislikedCount = loadAutoHomeAlbums(context, database, AutoSessionConstants.ID_ALBUMS_DISLIKED).size
                val categoryItems = mutableMapOf(
                    "all" to browsableMediaItem(AutoSessionConstants.ID_ALBUMS_LIBRARY, context.getString(R.string.common_option_all), libraryCount.toString(), drawableUri(context, R.drawable.album), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                    "favorites" to browsableMediaItem(AutoSessionConstants.ID_ALBUMS_FAVORITES, context.getString(R.string.library_favorites_title), favoritesCount.toString(), drawableUri(context, R.drawable.heart), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                    "disliked" to browsableMediaItem(AutoSessionConstants.ID_ALBUMS_DISLIKED, context.getString(R.string.library_disliked_title), dislikedCount.toString(), drawableUri(context, R.drawable.heart_dislike), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                )
                if (onDeviceCount != null) {
                    categoryItems["on_device"] = browsableMediaItem(
                        AutoSessionConstants.ID_ALBUMS_ONDEVICE, context.getString(R.string.library_on_device_title),
                        onDeviceCount.toString(), drawableUri(context, R.drawable.folder), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                    )
                }
                homeCategoryOrder(context, homeAlbumsOrderKey, listOf("all", "favorites", "disliked", "on_device"))
                    .mapNotNull { id -> if (id == "favorites" && !showFavoritesAlbum) null else categoryItems[id] }
            }
            AutoSessionConstants.ID_ALBUMS_LIBRARY,
            AutoSessionConstants.ID_ALBUMS_FAVORITES,
            AutoSessionConstants.ID_ALBUMS_DISLIKED -> {
                val collections = loadAutoHomeAlbums(context, database, parentId)
                if (autoOriginParent(parentId) == parentId && autoOriginAvailable(parentId)) {
                    return autoOriginFolders(context, parentId, collections) { it.isYoutubeAlbum }
                }
                val entries = collections.map { album ->
                    val category = if (isDeviceAlbumId(album.id)) AutoSessionConstants.ID_ALBUMS_ONDEVICE
                        else PlayerServiceModern.ALBUM
                    CancellableAutoMediaItemMapper.mapAlbumToMediaItem(
                        category, album.id, album.title.orEmpty(), album.authorsText, album.thumbnailUrl,
                    )
                }
                listOf(AutoSessionConstants.shuffleItem(context, "${parentId}_SHUFFLE")) + entries
            }
            else -> emptyList()
        }
    }
}
