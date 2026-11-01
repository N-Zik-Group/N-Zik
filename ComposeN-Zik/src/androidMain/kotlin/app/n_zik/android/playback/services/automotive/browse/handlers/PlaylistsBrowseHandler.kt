package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.PINNED_PREFIX
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.homePlaylistsOrderKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.showPinnedPlaylistsKey
import app.it.fast4x.rimusic.utils.showYtPlaylistsKey
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.home.YOUTUBE_LIBRARY_FILTER_KEY
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
import app.n_zik.android.playback.services.automotive.browse.visibleAutoLibraryPlaylists
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import kotlinx.coroutines.flow.first

class PlaylistsBrowseHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId == PlayerServiceModern.PLAYLIST ||
        parentId.startsWith("PLAYLISTS_") || parentId.startsWith("ID_PLAYLISTS_")

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?,
    ): List<MediaItem> = when (parentId) {
        PlayerServiceModern.PLAYLIST -> libraryRoot(context, database)
        AutoSessionConstants.ID_PLAYLISTS_YT -> {
            val selected = context.preferences.getEnum(YOUTUBE_LIBRARY_FILTER_KEY, YouTubeLibraryFilter.All)
            val previews = database.playlistTable.allAsPreview().first()
            (listOf(selected) + YouTubeLibraryFilter.entries.filter { it != selected }).map { filter ->
                val id = youtubeGroupId(filter)
                folder(context, id, filter.labelRes, filter.iconRes,
                    visibleAutoLibraryPlaylists(autoLibraryPlaylistScope(context, id), id, previews).size)
            }
        }
        AutoSessionConstants.ID_PLAYLISTS_REWIND -> {
            val selected = context.preferences.getEnum(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, RewindPlaylists.Filter.All)
            val previews = database.playlistTable.allAsPreview().first()
            (listOf(selected) + RewindPlaylists.Filter.entries.filter { it != selected }).map { filter ->
                val id = rewindGroupId(filter)
                folder(context, id, rewindGroupLabel(filter), rewindGroupIcon(filter),
                    visibleAutoLibraryPlaylists(autoLibraryPlaylistScope(context, id), id, previews).size)
            }
        }
        AutoSessionConstants.ID_PLAYLISTS_LOCAL,
        AutoSessionConstants.ID_PLAYLISTS_PINNED,
        AutoSessionConstants.ID_PLAYLISTS_YT_ALL,
        AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS,
        AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS,
        AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL,
        AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH,
        AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR -> {
            val playlists = loadAutoLibraryPlaylists(context, database, parentId).map { preview ->
                browsableMediaItem(
                    "${PlayerServiceModern.PLAYLIST}/${preview.playlist.id}",
                    context.rewindDisplayName(preview.playlist.name),
                    preview.songCount.toString(),
                    drawableUri(context, localPlaylistIcon(preview.playlist)),
                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                )
            }
            listOf(AutoSessionConstants.shuffleItem(context, "${parentId}_SHUFFLE")) + playlists
        }
        else -> emptyList()
    }

    private suspend fun libraryRoot(context: Context, database: Database): List<MediaItem> {
        val previews = database.playlistTable.allAsPreview().first()
        fun count(id: String) = visibleAutoLibraryPlaylists(autoLibraryPlaylistScope(context, id), id, previews).size
        val categories = mapOf(
            "all" to folder(context, AutoSessionConstants.ID_PLAYLISTS_LOCAL, R.string.library,
                R.drawable.library, count(AutoSessionConstants.ID_PLAYLISTS_LOCAL)),
            "pinned_playlists" to folder(context, AutoSessionConstants.ID_PLAYLISTS_PINNED, R.string.pinned_playlists,
                R.drawable.pin_filled, count(AutoSessionConstants.ID_PLAYLISTS_PINNED)),
            "rewind" to folder(context, AutoSessionConstants.ID_PLAYLISTS_REWIND, R.string.rewind,
                R.drawable.musical_notes, count(AutoSessionConstants.ID_PLAYLISTS_REWIND)),
            "yt_playlists" to folder(context, AutoSessionConstants.ID_PLAYLISTS_YT, R.string.ytm_playlists,
                R.drawable.ytmusic, count(AutoSessionConstants.ID_PLAYLISTS_YT)),
        )
        return homeCategoryOrder(context, homePlaylistsOrderKey, listOf("all", "pinned_playlists", "rewind", "yt_playlists"))
            .mapNotNull { id ->
                val visible = when (id) {
                    "pinned_playlists" -> context.preferences.getBoolean(showPinnedPlaylistsKey, true)
                    "rewind" -> context.preferences.getBoolean(showMonthlyPlaylistsKey, true)
                    "yt_playlists" -> context.preferences.getBoolean(showYtPlaylistsKey, true)
                    else -> true
                }
                categories[id].takeIf { visible }
            }
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
        RewindPlaylists.Filter.All -> R.string.all
    }

    private fun rewindGroupIcon(filter: RewindPlaylists.Filter): Int = when (filter) {
        RewindPlaylists.Filter.Month -> R.drawable.stat_month
        RewindPlaylists.Filter.Year -> R.drawable.stat_year
        RewindPlaylists.Filter.All -> R.drawable.musical_notes
    }

    private fun localPlaylistIcon(playlist: Playlist): Int = when {
        playlistLinkKind(playlist.browseId) == PlaylistLinkKind.YouTubePodcast -> R.drawable.podcast
        playlist.name.startsWith(PINNED_PREFIX, true) -> R.drawable.pin_filled
        RewindPlaylists.isMonthly(playlist.name) -> R.drawable.stat_month
        RewindPlaylists.isYearly(playlist.name) -> R.drawable.stat_year
        RewindPlaylists.isAlltime(playlist.name) -> R.drawable.musical_notes
        playlist.browseId?.startsWith("SPOTIFY_IMPORT") == true -> R.drawable.spotify
        playlist.browseId?.startsWith("RIPLAY_IMPORT") == true -> R.drawable.riplay
        playlistLinkKind(playlist.browseId) == PlaylistLinkKind.YouTubePlaylist -> R.drawable.ytmusic
        else -> R.drawable.ic_launcher
    }
}
