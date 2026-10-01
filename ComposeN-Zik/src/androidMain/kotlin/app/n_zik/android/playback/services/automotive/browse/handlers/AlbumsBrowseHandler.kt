package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.utils.Preference
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.homeAlbumsOrderKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.showFavoritesAlbumKey
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import kotlinx.coroutines.flow.first
import app.it.fast4x.rimusic.enums.AlbumSortBy

class AlbumsBrowseHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId == PlayerServiceModern.ALBUM ||
            parentId.startsWith("ALBUMS_") || parentId.startsWith("ID_ALBUMS_")

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        return when (parentId) {
            PlayerServiceModern.ALBUM -> {
                // Mirror the phone's HomeAlbum tabs: same order (homeAlbumsOrderKey)
                // and visibility (showFavoritesAlbumKey) as the in-app settings
                // dialog. Disliked albums stay always hidden from All/Favorites
                // and always shown in their dedicated folder — opening one + the
                // DISLIKE_ALBUM command removes its dislike (toggle).
                val showFavoritesAlbum = try { context.preferences.getBoolean(showFavoritesAlbumKey, true) } catch (e: Exception) { true }
                val libraryCount = database.albumTable.allInLibrary().first().count { it.dislikedAt == null }
                val favoritesCount = database.albumTable.allBookmarked().first().count { it.dislikedAt == null }
                val dislikedCount = database.albumTable.allDisliked().first().size
                val categoryItems = mapOf(
                    "all" to browsableMediaItem(AutoSessionConstants.ID_ALBUMS_LIBRARY, context.getString(R.string.library), libraryCount.toString(), drawableUri(context, R.drawable.album), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                    "favorites" to browsableMediaItem(AutoSessionConstants.ID_ALBUMS_FAVORITES, context.getString(R.string.favorites), favoritesCount.toString(), drawableUri(context, R.drawable.heart), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                    "disliked" to browsableMediaItem(AutoSessionConstants.ID_ALBUMS_DISLIKED, context.getString(R.string.disliked), dislikedCount.toString(), drawableUri(context, R.drawable.heart_dislike), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                )
                homeCategoryOrder(context, homeAlbumsOrderKey, listOf("all", "favorites", "disliked"))
                    .mapNotNull { id -> if (id == "favorites" && !showFavoritesAlbum) null else categoryItems[id] }
            }
            AutoSessionConstants.ID_ALBUMS_LIBRARY -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_ALBUMS_LIBRARY_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_ALBUMS_LIBRARY_SORT_BY.key, AlbumSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_ALBUMS_LIBRARY_SORT_ORDER.key, SortOrder.Ascending)
                val albums = database.albumTable.sortInLibrary(sortBy, sortOrder).first().filter { it.dislikedAt == null }.map { album -> SessionMediaItemMapper.mapAlbumToMediaItem(PlayerServiceModern.ALBUM, album.id, album.title ?: "", album.authorsText, album.thumbnailUrl) }
                listOf(shuffleItem) + albums
            }
            AutoSessionConstants.ID_ALBUMS_FAVORITES -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_ALBUMS_FAVORITES_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_ALBUMS_FAVORITES_SORT_BY.key, AlbumSortBy.Title)
                val sortOrder = context.preferences.getEnum(Preference.HOME_ALBUMS_FAVORITES_SORT_ORDER.key, SortOrder.Ascending)
                val albums = database.albumTable.sortBookmarked(sortBy, sortOrder).first().filter { it.dislikedAt == null }.map { album -> SessionMediaItemMapper.mapAlbumToMediaItem(PlayerServiceModern.ALBUM, album.id, album.title ?: "", album.authorsText, album.thumbnailUrl) }
                listOf(shuffleItem) + albums
            }
            AutoSessionConstants.ID_ALBUMS_DISLIKED -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_ALBUMS_DISLIKED_SHUFFLE)
                val albums = database.albumTable.allDisliked().first()
                listOf(shuffleItem) + albums.map { album -> SessionMediaItemMapper.mapAlbumToMediaItem(PlayerServiceModern.ALBUM, album.id, album.title ?: "", album.authorsText, album.thumbnailUrl) }
            }
            else -> emptyList()
        }
    }
}
