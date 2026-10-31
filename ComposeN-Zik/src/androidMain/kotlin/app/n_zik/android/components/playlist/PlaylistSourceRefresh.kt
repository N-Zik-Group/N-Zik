package app.n_zik.android.components.playlist

import androidx.media3.common.MediaItem
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.canRefreshPlaylist
import app.n_zik.android.core.database.isPodcastBrowseId
import app.n_zik.android.core.database.isReadOnlyPlaylistSource
import app.n_zik.android.utils.coroutines.NzikDispatchers
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Fetches the complete source before permitting any local membership changes. */
internal suspend fun loadPlaylistSource(browseId: String): List<MediaItem>? {
    if (isPodcastBrowseId(browseId)) {
        return loadCompletePodcastEpisodes(browseId).map { it.asMediaItem }
    }
    val page = YtMusic.getPlaylist(playlistId = browseId.removePrefix(MODIFIED_PREFIX)).getOrNull()
        ?: return null
    val songs = page.songs.toMutableList()
    val seen = mutableSetOf<String>()
    var continuation = page.songsContinuation
    while (continuation != null) {
        val token = continuation
        if (seen.size >= 1000 || !seen.add(token)) return null
        val next = YtMusic.getPlaylistContinuation(token).getOrNull() ?: return null
        songs.addAll(next.songs)
        continuation = next.continuation
    }
    return songs.map(Innertube.SongItem::asMediaItem)
}

/** Refresh preserves local order for surviving songs and appends newly discovered source songs. */
internal suspend fun refreshPlaylistSource(
    playlist: Playlist,
    load: suspend (String) -> List<MediaItem>? = ::loadPlaylistSource,
): Boolean = withContext(NzikDispatchers.DATA) {
    val browseId = playlist.browseId ?: return@withContext false
    if (!canRefreshPlaylist(playlist)) return@withContext false
    val source = load(browseId)?.distinctBy(MediaItem::mediaId)?.takeIf { it.isNotEmpty() }
        ?: return@withContext false
    var refreshed = false
    Database.transaction {
        val latest = playlistTable.findById(playlist.id).first() ?: return@transaction
        if (latest.browseId != browseId || latest.isEditable != playlist.isEditable ||
            !canRefreshPlaylist(latest)) return@transaction
        val ordered = if (isReadOnlyPlaylistSource(latest)) {
            val byId = source.associateBy(MediaItem::mediaId)
            val surviving = songPlaylistMapTable.songsByPositionDirect(latest.id)
                .mapNotNull { byId[it.id] }
            val survivingIds = surviving.mapTo(mutableSetOf(), MediaItem::mediaId)
            surviving + source.filterNot { it.mediaId in survivingIds }
        } else source
        source.forEach { mediaItem ->
            val sourceTitle = mediaItem.mediaMetadata.title?.toString()
            if (!sourceTitle.isNullOrBlank() && sourceTitle != "null") {
                val fetched = mediaItem.asSong
                val existing = songTable.findByIdDirect(fetched.id)
                if (existing != null && !existing.title.startsWith(MODIFIED_PREFIX, ignoreCase = true) &&
                    existing.title != fetched.title) {
                    songTable.updateTitle(existing.id, fetched.title)
                }
            }
        }
        songPlaylistMapTable.clear(latest.id)
        mapIgnore(latest, *ordered.toTypedArray())
        refreshed = true
    }
    refreshed
}

/** Starts a source-only refresh; automatic opening refresh avoids transient toast noise. */
fun launchPlaylistSourceRefresh(playlist: Playlist?, scope: CoroutineScope, silent: Boolean = false) {
    if (playlist == null || !canRefreshPlaylist(playlist)) return
    scope.launch(NzikDispatchers.DATA) {
        try {
            val refreshed = refreshPlaylistSource(playlist)
            if (!silent) {
                if (refreshed) Toaster.done() else Toaster.e(R.string.error)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("PlaylistRefresh").e(error, "Unable to refresh playlist source")
            if (!silent) Toaster.e(R.string.error)
        }
    }
}
