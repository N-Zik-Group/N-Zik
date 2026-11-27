package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.models.Playlist
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.homePlaylistsOrderKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.showMonthlyPlaylistsKey
import app.n_zik.android.utils.showPinnedPlaylistsKey
import app.n_zik.android.utils.showYtPlaylistsKey
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.home.YouTubeLibraryFilter
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.playlistLinkKind
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.core.rewind.RewindPlaylists.rewindDisplayName
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.browse.autoLibraryPlaylistScope
import app.n_zik.android.playback.services.automotive.browse.loadAutoLibraryPlaylists
import app.n_zik.android.playback.services.automotive.browse.showAutoDevicePlaylists
import app.n_zik.android.playback.services.automotive.browse.visibleAutoLibraryPlaylists
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import kotlinx.coroutines.flow.first

private val rewindFolders = setOf(
    AutoSessionConstants.ID_PLAYLISTS_REWIND,
    AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL,
    AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH,
    AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR,
)

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
        if (parentId in rewindFolders && !isRewindEnabled(context)) return emptyList()
        return when (parentId) {
            PlayerServiceModern.PLAYLIST -> libraryRoot(context, database)
            AutoSessionConstants.ID_PLAYLISTS_YT -> {
                val previews = database.playlistTable.allAsPreview().first()
                YouTubeLibraryFilter.entries.map { filter ->
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
                RewindPlaylists.Filter.entries.map { filter ->
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
        val deviceIds = database.songPlaylistMapTable.onDeviceOnlyPlaylistIds().first().toSet()
        val playlists = loadAutoLibraryPlaylists(context, database, parentId).map { preview ->
            browsableMediaItem(
                "${PlayerServiceModern.PLAYLIST}/${preview.playlist.id}",
                context.rewindDisplayName(preview.playlist.name),
                preview.songCount.toString(),
                drawableUri(context, localPlaylistIcon(preview.playlist, preview.playlist.id in deviceIds)),
                MediaMetadata.MEDIA_TYPE_PLAYLIST,
            )
        }
        return listOf(AutoSessionConstants.shuffleItem(context, "${parentId}_SHUFFLE")) + playlists
    }

    private suspend fun libraryRoot(context: Context, database: Database): List<MediaItem> {
        val previews = database.playlistTable.allAsPreview().first()
        val deviceIds = if (showAutoDevicePlaylists(context)) {
            database.songPlaylistMapTable.onDeviceOnlyPlaylistIds().first().toSet()
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
        val order = homeCategoryOrder(context, homePlaylistsOrderKey, listOf("all", "pinned_playlists", "rewind", "yt_playlists", "on_device"))
        return (listOf("all") + order.filterNot { it == "all" })
            .mapNotNull { id ->
                val visible = when (id) {
                    "on_device" -> showAutoDevicePlaylists(context)
                    "pinned_playlists" -> context.preferences.getBoolean(showPinnedPlaylistsKey, true)
                    "rewind" -> isRewindEnabled(context) && context.preferences.getBoolean(showMonthlyPlaylistsKey, true)
                    "yt_playlists" -> context.preferences.getBoolean(showYtPlaylistsKey, true)
                    else -> true
                }
                categories[id].takeIf { visible }
            }
    }

    private fun isRewindEnabled(context: Context): Boolean =
        DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true)

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
