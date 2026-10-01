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
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.core.rewind.RewindPlaylists.rewindDisplayName
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
                val showRewind = try { context.preferences.getBoolean(showMonthlyPlaylistsKey, true) } catch (e: Exception) { true }
                // Same gate as the phone's Library (HomeLibrary toggleMap): YT
                // playlists can come from imports (not only live sync), so the
                // folder follows the showYtPlaylists setting alone.
                val showYtPlaylists = try { context.preferences.getBoolean(showYtPlaylistsKey, true) } catch (e: Exception) { true }
                val previews = database.playlistTable.allAsPreview().first()
                val pinnedCount = previews.count { it.playlist.name.startsWith(PINNED_PREFIX, true) }
                // Same filter as the phone's "All" tab (HomeLibrary / PlaylistsType.Playlist):
                // every playlist — YT included (the phone's All tab lists them too), rewind
                // gated by the showRewind toggle and pinned by the showPinnedPlaylists toggle.
                val localCount = previews.count { (!RewindPlaylists.isRewind(it.playlist.name) || showRewind) && (!it.playlist.name.startsWith(PINNED_PREFIX, true) || showPinnedPlaylists) }
                val ytCount = previews.count { it.playlist.isYoutubePlaylist }
                val rewindCount = previews.count { RewindPlaylists.isRewind(it.playlist.name) }
                // Order and visibility mirror the in-app HomePlaylists settings
                // (homePlaylistsOrderKey + the HomePlaylistsSettingsDialog toggles).
                val categoryItems = mapOf(
                    "all" to browsableMediaItem(ID_PLAYLISTS_LOCAL, context.getString(R.string.library), localCount.toString(), drawableUri(context, R.drawable.library), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                    "pinned_playlists" to browsableMediaItem(ID_PLAYLISTS_PINNED, context.getString(R.string.pinned_playlists), pinnedCount.toString(), drawableUri(context, R.drawable.pin_filled), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                    "rewind" to browsableMediaItem(AutoSessionConstants.ID_PLAYLISTS_REWIND, context.getString(R.string.rewind), rewindCount.toString(), drawableUri(context, R.drawable.musical_notes), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                    "yt_playlists" to browsableMediaItem(ID_PLAYLISTS_YT, context.getString(R.string.ytm_playlists), ytCount.toString(), drawableUri(context, R.drawable.ytmusic), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                )
                homeCategoryOrder(context, homePlaylistsOrderKey, listOf("all", "pinned_playlists", "rewind", "yt_playlists"))
                    .mapNotNull { id ->
                        when (id) {
                            "pinned_playlists" -> if (showPinnedPlaylists) categoryItems[id] else null
                            "rewind" -> if (showRewind) categoryItems[id] else null
                            "yt_playlists" -> if (showYtPlaylists) categoryItems[id] else null
                            else -> categoryItems[id]
                        }
                    }
            }
            AutoSessionConstants.ID_PLAYLISTS_REWIND -> {
                // Read-only of the phone's Month/Year/All chip (REWIND_PLAYLISTS_FILTER_KEY,
                // default Month): the selected sub-group comes first, the other two keep
                // the canonical order. AA never writes this key.
                val selected = runCatching {
                    RewindPlaylists.Filter.valueOf(context.preferences.getString(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, null).orEmpty())
                }.getOrDefault(RewindPlaylists.Filter.Month)
                val ordered = listOf(selected) + RewindPlaylists.Filter.values().filter { it != selected }
                val previews = database.playlistTable.allAsPreview().first()
                ordered.map { filter ->
                    browsableMediaItem(
                        rewindGroupId(filter),
                        context.getString(rewindGroupLabel(filter)),
                        previews.count { preview -> RewindPlaylists.matches(filter, preview.playlist.name) }.toString(),
                        drawableUri(context, rewindGroupIcon(filter)),
                        MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                    )
                }
            }
            AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH -> rewindGroup(context, database, RewindPlaylists.Filter.Month, AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH_SHUFFLE)
            AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR -> rewindGroup(context, database, RewindPlaylists.Filter.Year, AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR_SHUFFLE)
            AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL -> rewindGroup(context, database, RewindPlaylists.Filter.All, AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL_SHUFFLE)
            ID_PLAYLISTS_LOCAL -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_PLAYLISTS_LOCAL_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_LIBRARY_PLAYLIST_SORT_BY.key, PlaylistSortBy.SongCount)
                val sortOrder = context.preferences.getEnum(Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER.key, SortOrder.Ascending)
                val showPinnedPlaylists = try { context.preferences.getBoolean(showPinnedPlaylistsKey, true) } catch (e: Exception) { true }
                val showRewind = try { context.preferences.getBoolean(showMonthlyPlaylistsKey, true) } catch (e: Exception) { true }
                // Same filter as the phone's "All" tab (HomeLibrary / PlaylistsType.Playlist):
                // every playlist — YT included, rewind and pinned gated by their phone toggles.
                val playlists = database.playlistTable.sortPreviews(sortBy, sortOrder).first()
                    .filter { (!RewindPlaylists.isRewind(it.playlist.name) || showRewind) && (!it.playlist.name.startsWith(PINNED_PREFIX, true) || showPinnedPlaylists) }
                    .map { preview ->
                        // Spec 2: generated rewind-* playlists show their localized display
                        // name; the origin icon mirrors the phone's PlaylistItem overlay.
                        val iconRes = localPlaylistIcon(preview.playlist.name, preview.playlist.browseId, preview.playlist.isYoutubePlaylist)
                        browsableMediaItem("${PlayerServiceModern.PLAYLIST}/${preview.playlist.id}", context.rewindDisplayName(preview.playlist.name), preview.songCount.toString(), drawableUri(context, iconRes), MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    }
                listOf(shuffleItem) + playlists
            }
            ID_PLAYLISTS_YT -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_PLAYLISTS_YT_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_BY.key, PlaylistSortBy.SongCount)
                val sortOrder = context.preferences.getEnum(Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER.key, SortOrder.Ascending)
                val playlists = database.playlistTable.sortPreviews(sortBy, sortOrder).first()
                    .filter { it.playlist.isYoutubePlaylist }
                    .map { preview -> browsableMediaItem("${PlayerServiceModern.PLAYLIST}/${preview.playlist.id}", preview.playlist.name, preview.songCount.toString(), drawableUri(context, R.drawable.ytmusic), MediaMetadata.MEDIA_TYPE_PLAYLIST) }
                listOf(shuffleItem) + playlists
            }

            ID_PLAYLISTS_PINNED -> {
                val shuffleItem = AutoSessionConstants.shuffleItem(context, AutoSessionConstants.ID_PLAYLISTS_PINNED_SHUFFLE)
                val sortBy = context.preferences.getEnum(Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY.key, PlaylistSortBy.SongCount)
                val sortOrder = context.preferences.getEnum(Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER.key, SortOrder.Ascending)
                val playlists = database.playlistTable.sortPreviews(sortBy, sortOrder).first()
                    .filter { it.playlist.name.startsWith(PINNED_PREFIX, true) }
                    .map { preview -> browsableMediaItem("${PlayerServiceModern.PLAYLIST}/${preview.playlist.id}", preview.playlist.name, preview.songCount.toString(), drawableUri(context, R.drawable.pin_filled), MediaMetadata.MEDIA_TYPE_PLAYLIST) }
                listOf(shuffleItem) + playlists
            }
            else -> emptyList()
        }
    }

    /**
     * Content of a Rewind sub-group: its shuffle item + the generated playlists of that
     * type, shown with their localized display name (rewindDisplayName) and sorted with
     * the phone's monthly-playlists sort keys (HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_*,
     * default Name). An empty group is just the shuffle item (no crash).
     */
    private suspend fun rewindGroup(
        context: Context,
        database: Database,
        filter: RewindPlaylists.Filter,
        shuffleId: String
    ): List<MediaItem> {
        val shuffleItem = AutoSessionConstants.shuffleItem(context, shuffleId)
        val sortBy = context.preferences.getEnum(Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_BY.key, PlaylistSortBy.Name)
        val sortOrder = context.preferences.getEnum(Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_ORDER.key, SortOrder.Ascending)
        val playlists = database.playlistTable.sortPreviews(sortBy, sortOrder).first()
            .filter { preview -> RewindPlaylists.matches(filter, preview.playlist.name) }
            .map { preview ->
                browsableMediaItem(
                    "${PlayerServiceModern.PLAYLIST}/${preview.playlist.id}",
                    context.rewindDisplayName(preview.playlist.name),
                    preview.songCount.toString(),
                    drawableUri(context, rewindGroupIcon(filter)),
                    MediaMetadata.MEDIA_TYPE_PLAYLIST
                )
            }
        return listOf(shuffleItem) + playlists
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

    /**
     * Icon of a Rewind sub-group, mirroring the phone's overlay origin indicator:
     * Month -> stat_month, Year -> stat_year, All (alltime) -> musical_notes.
     */
    private fun rewindGroupIcon(filter: RewindPlaylists.Filter): Int = when (filter) {
        RewindPlaylists.Filter.Month -> R.drawable.stat_month
        RewindPlaylists.Filter.Year -> R.drawable.stat_year
        RewindPlaylists.Filter.All -> R.drawable.musical_notes
    }

    /**
     * Icon of a single generated rewind-* playlist by name (used where rewind
     * playlists are mixed into a generic list, e.g. the Library folder) — same
     * mapping as [rewindGroupIcon]. Only called for rewind names.
     */
    private fun rewindPlaylistIcon(name: String): Int = when {
        RewindPlaylists.isMonthly(name) -> R.drawable.stat_month
        RewindPlaylists.isYearly(name) -> R.drawable.stat_year
        else -> R.drawable.musical_notes
    }

    /**
     * Icon of a Library-folder playlist, mirroring the phone's PlaylistItem origin
     * icon logic: pinned first, then generated rewind-* by type, then Spotify/RiPlay
     * imports with their source logo bitmap, YouTube playlists with the ytmusic
     * icon, plain local playlists with the NZik logo.
     */
    private fun localPlaylistIcon(name: String, browseId: String?, isYoutube: Boolean = false): Int = when {
        name.startsWith(PINNED_PREFIX, true) -> R.drawable.pin_filled
        RewindPlaylists.isRewind(name) -> rewindPlaylistIcon(name)
        browseId?.startsWith("SPOTIFY_IMPORT") == true -> R.drawable.spotify
        browseId?.startsWith("RIPLAY_IMPORT") == true -> R.drawable.riplay
        isYoutube || browseId?.startsWith("VL") == true -> R.drawable.ytmusic
        else -> R.drawable.ic_launcher
    }
}
