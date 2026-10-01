package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.utils.Preference
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.homeArtistsOrderKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.showFavoritesArtistKey
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import kotlinx.coroutines.flow.first
import app.it.fast4x.rimusic.enums.ArtistSortBy

class ArtistsBrowseHandler : BrowseHandler {
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
                // dialog. Disliked artists stay always hidden from All/Favorites
                // and always shown in their dedicated folder — opening one + the
                // DISLIKE_ARTIST command removes its dislike (toggle).
                val showFavoritesArtist = try { context.preferences.getBoolean(showFavoritesArtistKey, true) } catch (e: Exception) { true }
                val libraryCount = database.artistTable.allInLibrary().first().count { it.dislikedAt == null }
                val favoritesCount = database.artistTable.allFollowing().first().count { it.dislikedAt == null }
                val dislikedCount = database.artistTable.allDisliked().first().size
                val categoryItems = mapOf(
                    "all" to browsableMediaItem(AutoSessionConstants.ID_ARTISTS_LIBRARY, context.getString(R.string.library), libraryCount.toString(), drawableUri(context, R.drawable.artist), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                    "favorites" to browsableMediaItem(AutoSessionConstants.ID_ARTISTS_FAVORITES, context.getString(R.string.favorites), favoritesCount.toString(), drawableUri(context, R.drawable.heart), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                    "disliked" to browsableMediaItem(AutoSessionConstants.ID_ARTISTS_DISLIKED, context.getString(R.string.disliked), dislikedCount.toString(), drawableUri(context, R.drawable.heart_dislike), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
                )
                homeCategoryOrder(context, homeArtistsOrderKey, listOf("all", "favorites", "disliked"))
                    .mapNotNull { id -> if (id == "favorites" && !showFavoritesArtist) null else categoryItems[id] }
            }
            AutoSessionConstants.ID_ARTISTS_LIBRARY -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_ARTISTS_LIBRARY_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_ARTISTS_LIBRARY_SORT_BY.key, ArtistSortBy.Name)
                val sortOrder = context.preferences.getEnum(Preference.HOME_ARTISTS_LIBRARY_SORT_ORDER.key, SortOrder.Ascending)
                val artists = database.artistTable.sortInLibrary(sortBy, sortOrder).first().filter { it.dislikedAt == null }.map { artist -> SessionMediaItemMapper.mapArtistToMediaItem(PlayerServiceModern.ARTIST, artist.id, artist.name ?: "", artist.thumbnailUrl) }
                listOf(shuffleItem) + artists
            }
            AutoSessionConstants.ID_ARTISTS_FAVORITES -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_ARTISTS_FAVORITES_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_ARTISTS_FAVORITES_SORT_BY.key, ArtistSortBy.Name)
                val sortOrder = context.preferences.getEnum(Preference.HOME_ARTISTS_FAVORITES_SORT_ORDER.key, SortOrder.Ascending)
                val artists = database.artistTable.sortFollowing(sortBy, sortOrder).first().filter { it.dislikedAt == null }.map { artist -> SessionMediaItemMapper.mapArtistToMediaItem(PlayerServiceModern.ARTIST, artist.id, artist.name ?: "", artist.thumbnailUrl) }
                listOf(shuffleItem) + artists
            }
            AutoSessionConstants.ID_ARTISTS_DISLIKED -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_ARTISTS_DISLIKED_SHUFFLE)
                val artists = database.artistTable.allDisliked().first()
                listOf(shuffleItem) + artists.map { artist -> SessionMediaItemMapper.mapArtistToMediaItem(PlayerServiceModern.ARTIST, artist.id, artist.name ?: "", artist.thumbnailUrl) }
            }
            else -> emptyList()
        }
    }
}
