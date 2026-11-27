package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.models.Playlist
import app.n_zik.android.utils.homePlaylistsOrderKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.libraryPlaylistChipOrder
import app.n_zik.android.components.ui.screens.home.YouTubeLibraryFilter
import app.n_zik.android.components.ui.screens.home.YOUTUBE_LIBRARY_FILTER_KEY
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.playlistLinkKind
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.core.rewind.RewindPlaylists.rewindDisplayName
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.browse.autoPlaylistCategoryVisible
import app.n_zik.android.playback.services.automotive.browse.autoLibraryPlaylistScope
import app.n_zik.android.playback.services.automotive.browse.autoDevicePlaylistCounts
import app.n_zik.android.playback.services.automotive.browse.loadAutoLibraryPlaylists
import app.n_zik.android.playback.services.automotive.browse.showAutoDevicePlaylists
import app.n_zik.android.playback.services.automotive.browse.visibleAutoLibraryPlaylists
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract
import app.n_zik.android.utils.getEnum
import kotlinx.coroutines.flow.first
import java.io.File

class PlaylistsBrowseHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId == PlayerServiceModern.PLAYLIST ||
        parentId.startsWith("PLAYLISTS_") || parentId.startsWith("ID_PLAYLISTS_")

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?,
    ): List<MediaItem> {
        if (!autoPlaylistCategoryVisible(context, parentId)) return emptyList()
        return when (parentId) {
            PlayerServiceModern.PLAYLIST -> libraryRoot(context, database)
            AutoSessionConstants.ID_PLAYLISTS_YT -> {
                val previews = database.playlistTable.allAsPreview().first()
                val selected = context.preferences.getEnum(YOUTUBE_LIBRARY_FILTER_KEY, YouTubeLibraryFilter.All)
                YouTubeLibraryFilter.entries.sortedByDescending { it == selected }.map { filter ->
                    val id = youtubeGroupId(filter)
                    folder(context, id, filter.labelRes, filter.iconRes,
                        visibleAutoLibraryPlaylists(autoLibraryPlaylistScope(context, id), id, previews).size)
                }
            }
            AutoSessionConstants.ID_PLAYLISTS_REWIND -> {
                val rewind = RewindPlaylists.visibility(context)
                if (!RewindPlaylists.rowVisible(rewind.monthlyEnabled, rewind.yearlyEnabled)) {
                    return playlistItems(context, database, parentId)
                }
                val previews = database.playlistTable.allAsPreview().first()
                val selected = context.preferences.getEnum(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, RewindPlaylists.Filter.All)
                RewindPlaylists.Filter.entries.sortedByDescending { it == selected }.map { filter ->
                    val id = rewindGroupId(filter)
                    folder(context, id, rewindGroupLabel(filter), rewindGroupIcon(filter),
                        visibleAutoLibraryPlaylists(autoLibraryPlaylistScope(context, id), id, previews).size)
                }
            }
            AutoSessionConstants.ID_PLAYLISTS_LOCAL,
            AutoSessionConstants.ID_PLAYLISTS_ON_DEVICE,
            AutoSessionConstants.ID_PLAYLISTS_PINNED,
            AutoSessionConstants.ID_PLAYLISTS_YT_ALL,
            AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS,
            AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS,
            AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL,
            AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH,
            AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR -> playlistItems(context, database, parentId)
            else -> emptyList()
        }
    }

    private suspend fun playlistItems(context: Context, database: Database, parentId: String): List<MediaItem> {
        val deviceOnly = parentId == AutoSessionConstants.ID_PLAYLISTS_ON_DEVICE
        val previews = loadAutoLibraryPlaylists(context, database, parentId)
        if (deviceOnly && previews.isEmpty()) return emptyList()
        val deviceIds = if (deviceOnly) emptySet() else database.songPlaylistMapTable.onDeviceOnlyPlaylistIds().first().toSet()
        val playlists = previews.map { preview ->
            val playlist = preview.playlist
            val playlistId = "${PlayerServiceModern.PLAYLIST}/${playlist.id}"
            val countResource = if (playlistLinkKind(playlist.browseId) == PlaylistLinkKind.YouTubePodcast) {
                R.plurals.sync_detail_episodes
            } else R.plurals.library_song_count
            val artworkFile = File(context.filesDir, "thumbnail/playlist_${playlist.id}")
            val artwork = if (artworkFile.isFile) {
                ArtworkContentProvider.uriForSource(context, "playlist:${playlist.id}", artworkFile)
            } else drawableUri(context, localPlaylistIcon(playlist, deviceOnly || playlist.id in deviceIds))
            browsableMediaItem(
                if (deviceOnly) "$playlistId/${AutoMediaIdContract.ON_DEVICE_PLAYLIST_ROW}" else playlistId,
                context.rewindDisplayName(playlist.cleanName()),
                context.resources.getQuantityString(countResource, preview.songCount, preview.songCount),
                artwork,
                MediaMetadata.MEDIA_TYPE_PLAYLIST,
            ).let { item ->
                val author = playlist.displayAuthorName()?.takeIf(String::isNotBlank)
                if (author == null) item else item.buildUpon()
                    .setMediaMetadata(item.mediaMetadata.buildUpon().setArtist(author).build()).build()
            }
        }
        return listOf(AutoSessionConstants.shuffleItem(context, "${parentId}_SHUFFLE")) + playlists
    }

    private suspend fun libraryRoot(context: Context, database: Database): List<MediaItem> {
        val previews = database.playlistTable.allAsPreview().first()
        val deviceIds = if (showAutoDevicePlaylists(context)) {
            autoDevicePlaylistCounts(database, context.scanDeviceLibrary().songIds).keys
        } else emptySet()
        fun count(id: String) = visibleAutoLibraryPlaylists(autoLibraryPlaylistScope(context, id), id, previews, deviceIds).size
        val categories = mapOf(
            "all" to folder(context, AutoSessionConstants.ID_PLAYLISTS_LOCAL, R.string.common_option_all,
                R.drawable.library, count(AutoSessionConstants.ID_PLAYLISTS_LOCAL)),
            "on_device" to folder(context, AutoSessionConstants.ID_PLAYLISTS_ON_DEVICE, R.string.library_on_device_title,
                R.drawable.folder, count(AutoSessionConstants.ID_PLAYLISTS_ON_DEVICE)),
            "pinned_playlists" to folder(context, AutoSessionConstants.ID_PLAYLISTS_PINNED, R.string.playlist_pinned_title,
                R.drawable.pin_filled, count(AutoSessionConstants.ID_PLAYLISTS_PINNED)),
            "rewind" to folder(context, AutoSessionConstants.ID_PLAYLISTS_REWIND, R.string.rewind_title,
                R.drawable.musical_notes, count(AutoSessionConstants.ID_PLAYLISTS_REWIND)),
            "yt_playlists" to folder(context, AutoSessionConstants.ID_PLAYLISTS_YT, R.string.library_youtube_playlists_label,
                R.drawable.ytmusic, count(AutoSessionConstants.ID_PLAYLISTS_YT)),
        )
        val order = libraryPlaylistChipOrder(context.preferences.getString(homePlaylistsOrderKey, "").orEmpty())
        return order.mapNotNull(categories::get).filter { autoPlaylistCategoryVisible(context, it.mediaId) }
    }

    private fun folder(context: Context, id: String, label: Int, icon: Int, count: Int): MediaItem =
        browsableMediaItem(id, context.getString(label), count.toString(), drawableUri(context, icon),
            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)

    private fun youtubeGroupId(filter: YouTubeLibraryFilter): String = when (filter) {
        YouTubeLibraryFilter.All -> AutoSessionConstants.ID_PLAYLISTS_YT_ALL
        YouTubeLibraryFilter.Playlists -> AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS
        YouTubeLibraryFilter.Podcasts -> AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS
    }

    private fun rewindGroupId(filter: RewindPlaylists.Filter): String = when (filter) {
        RewindPlaylists.Filter.Month -> AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH
        RewindPlaylists.Filter.Year -> AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR
        RewindPlaylists.Filter.All -> AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL
    }

    private fun rewindGroupLabel(filter: RewindPlaylists.Filter): Int = when (filter) {
        RewindPlaylists.Filter.Month -> R.string.rewind_filter_month
        RewindPlaylists.Filter.Year -> R.string.rewind_filter_year
        RewindPlaylists.Filter.All -> R.string.common_option_all
    }

    private fun rewindGroupIcon(filter: RewindPlaylists.Filter): Int = when (filter) {
        RewindPlaylists.Filter.Month -> R.drawable.stat_month
        RewindPlaylists.Filter.Year -> R.drawable.stat_year
        RewindPlaylists.Filter.All -> R.drawable.musical_notes
    }

    private fun localPlaylistIcon(playlist: Playlist, onDevice: Boolean): Int {
        val rewindIcon = RewindPlaylists.iconForPlaylist(playlist.cleanName())
        return when {
        onDevice -> R.drawable.folder
        playlistLinkKind(playlist.browseId) == PlaylistLinkKind.YouTubePodcast -> R.drawable.podcast
        playlist.name.startsWith(PINNED_PREFIX, true) -> R.drawable.pin_filled
        rewindIcon != null -> rewindIcon
        playlist.browseId?.startsWith("SPOTIFY_IMPORT") == true -> R.drawable.spotify
        playlist.browseId?.startsWith("RIPLAY_IMPORT") == true -> R.drawable.riplay
        playlistLinkKind(playlist.browseId) == PlaylistLinkKind.YouTubePlaylist -> R.drawable.ytmusic
        else -> R.drawable.ic_launcher
        }
    }
}
