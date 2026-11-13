package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import androidx.media3.common.MediaItem
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.ondevice.DeviceLibrarySnapshot
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.AlbumSortBy
import app.n_zik.android.enums.ArtistSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_ALBUMS_ONDEVICE
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_ARTISTS_ONDEVICE
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.showOnDeviceAlbumsKey
import app.n_zik.android.utils.showOnDeviceArtistsKey
import kotlinx.coroutines.flow.first

/** Dedicated IDs keep device-only detail and playback separate from existing detail routes. */
internal class AutoDeviceGroupsBrowseHandler(
    private val scan: suspend (Context) -> DeviceLibrarySnapshot = { it.scanDeviceLibrary() },
) : BrowseHandler {

    override fun handles(parentId: String): Boolean =
        parentId == ID_ALBUMS_ONDEVICE || parentId == ID_ARTISTS_ONDEVICE || detailParent(parentId) != null

    fun detailParent(mediaId: String): String? {
        val category = mediaId.substringBefore('/')
        val groupId = mediaId.substringAfter('/', "")
        return category.takeIf {
            (it == ID_ALBUMS_ONDEVICE || it == ID_ARTISTS_ONDEVICE) && groupId.isNotEmpty() && '/' !in groupId
        }
    }

    private fun visible(context: Context, category: String): Boolean =
        context.isDeviceLibraryEnabled() && context.preferences.getBoolean(
            if (category == ID_ALBUMS_ONDEVICE) showOnDeviceAlbumsKey else showOnDeviceArtistsKey, true,
        )

    suspend fun albums(context: Context, database: Database): List<Album> {
        if (!visible(context, ID_ALBUMS_ONDEVICE)) return emptyList()
        val ids = scan(context).albumIds
        if (ids.isEmpty()) return emptyList()
        val sort = context.preferences.getEnum(Preference.HOME_ON_DEVICE_ALBUMS_SORT_BY.key, AlbumSortBy.Title)
        val order = context.preferences.getEnum(Preference.HOME_ON_DEVICE_ALBUMS_SORT_ORDER.key, SortOrder.Ascending)
        return database.albumTable.sortInLibrary(sort, order).first()
            .filter { it.id in ids && it.dislikedAt == null }
    }

    suspend fun artists(context: Context, database: Database): List<Artist> {
        if (!visible(context, ID_ARTISTS_ONDEVICE)) return emptyList()
        val ids = scan(context).artistIds
        if (ids.isEmpty()) return emptyList()
        val sort = context.preferences.getEnum(Preference.HOME_ON_DEVICE_ARTISTS_SORT_BY.key, ArtistSortBy.Name)
        val order = context.preferences.getEnum(Preference.HOME_ON_DEVICE_ARTISTS_SORT_ORDER.key, SortOrder.Ascending)
        return database.artistTable.sortInLibrary(sort, order).first()
            .filter { it.id in ids && it.dislikedAt == null }
    }

    suspend fun songs(parentId: String, context: Context, database: Database): List<Song> {
        val category = detailParent(parentId) ?: return emptyList()
        if (!visible(context, category)) return emptyList()
        val groupId = parentId.substringAfter('/')
        val snapshot = scan(context)
        val album = category == ID_ALBUMS_ONDEVICE
        val membership = if (album) snapshot.albumSongIds else snapshot.artistSongIds
        val currentSongIds = membership[groupId] ?: return emptyList()
        val songs = if (album) database.songAlbumMapTable.allSongsOf(groupId).first()
            else database.songArtistMapTable.allSongsBy(groupId).first()
        return songs.filter { it.id in currentSongIds }
    }

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?,
    ): List<MediaItem> = when (parentId) {
        ID_ALBUMS_ONDEVICE -> albums(context, database).map { album ->
            CancellableAutoMediaItemMapper.mapAlbumToMediaItem(
                parentId, album.id, album.title.orEmpty(), album.authorsText, album.thumbnailUrl,
            )
        }
        ID_ARTISTS_ONDEVICE -> artists(context, database).map { artist ->
            CancellableAutoMediaItemMapper.mapArtistToMediaItem(
                parentId, artist.id, artist.name.orEmpty(), artist.thumbnailUrl,
            )
        }
        else -> songs(parentId, context, database).map { song ->
            CancellableAutoMediaItemMapper.mapSongToMediaItem(song, parentId, loadArtwork = true)
        }
    }
}
