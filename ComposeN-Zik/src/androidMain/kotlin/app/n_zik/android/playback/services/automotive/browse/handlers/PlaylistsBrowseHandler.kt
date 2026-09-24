package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.PINNED_PREFIX

import app.it.fast4x.rimusic.enums.PlaylistSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.utils.*
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_PLAYLISTS_LOCAL
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_PLAYLISTS_PINNED

import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_PLAYLISTS_YT
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import kotlinx.coroutines.flow.first

class PlaylistsBrowseHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId == PlayerServiceModern.PLAYLIST ||
            parentId.startsWith("PLAYLISTS_") || parentId.startsWith("ID_PLAYLISTS_")

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        return when (parentId) {
            PlayerServiceModern.PLAYLIST -> {
                val showPinnedPlaylists = try { context.preferences.getBoolean(showPinnedPlaylistsKey, true) } catch (e: Exception) { true }
                val pinnedCount = database.playlistTable.allAsPreview().first().filter { it.playlist.name.startsWith(PINNED_PREFIX, true) }.size
                val localCount = database.playlistTable.allAsPreview().first().filter { !it.playlist.isYoutubePlaylist && !it.playlist.name.startsWith(PINNED_PREFIX, true) }.size
                val ytCount = database.playlistTable.allAsPreview().first().filter { it.playlist.isYoutubePlaylist }.size
                val playlists = mutableListOf<MediaItem>()
                playlists.add(browsableMediaItem(ID_PLAYLISTS_LOCAL, context.getString(R.string.library), localCount.toString(), drawableUri(context, R.drawable.library), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS))
                if (showPinnedPlaylists) {
                    playlists.add(browsableMediaItem(ID_PLAYLISTS_PINNED, context.getString(R.string.pinned_playlists), pinnedCount.toString(), drawableUri(context, R.drawable.pin_filled), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS))
                }
                playlists.add(browsableMediaItem(ID_PLAYLISTS_YT, context.getString(R.string.ytm_playlists), ytCount.toString(), drawableUri(context, R.drawable.ytmusic), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS))
                playlists
            }
            ID_PLAYLISTS_LOCAL -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_PLAYLISTS_LOCAL_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_LIBRARY_PLAYLIST_SORT_BY.key, PlaylistSortBy.SongCount)
                val sortOrder = context.preferences.getEnum(Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER.key, SortOrder.Ascending)
                val playlists = database.playlistTable.sortPreviews(sortBy, sortOrder).first()
                    .filter { !it.playlist.isYoutubePlaylist && !it.playlist.name.startsWith(PINNED_PREFIX, true) }
                    .map { preview -> browsableMediaItem("${PlayerServiceModern.PLAYLIST}/${preview.playlist.id}", preview.playlist.name, preview.songCount.toString(), drawableUri(context, R.drawable.library), MediaMetadata.MEDIA_TYPE_PLAYLIST) }
                listOf(shuffleItem) + playlists
            }
            ID_PLAYLISTS_YT -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_PLAYLISTS_YT_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_BY.key, PlaylistSortBy.SongCount)
                val sortOrder = context.preferences.getEnum(Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER.key, SortOrder.Ascending)
                val playlists = database.playlistTable.sortPreviews(sortBy, sortOrder).first()
                    .filter { it.playlist.isYoutubePlaylist }
                    .map { preview -> browsableMediaItem("${PlayerServiceModern.PLAYLIST}/${preview.playlist.id}", preview.playlist.name, preview.songCount.toString(), drawableUri(context, R.drawable.library), MediaMetadata.MEDIA_TYPE_PLAYLIST) }
                listOf(shuffleItem) + playlists
            }

            ID_PLAYLISTS_PINNED -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_PLAYLISTS_PINNED_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY.key, PlaylistSortBy.SongCount)
                val sortOrder = context.preferences.getEnum(Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER.key, SortOrder.Ascending)
                val playlists = database.playlistTable.sortPreviews(sortBy, sortOrder).first()
                    .filter { it.playlist.name.startsWith(PINNED_PREFIX, true) }
                    .map { preview -> browsableMediaItem("${PlayerServiceModern.PLAYLIST}/${preview.playlist.id}", preview.playlist.name, preview.songCount.toString(), drawableUri(context, R.drawable.library), MediaMetadata.MEDIA_TYPE_PLAYLIST) }
                listOf(shuffleItem) + playlists
            }
            else -> emptyList()
        }
    }
}
