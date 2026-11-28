package app.n_zik.android.components.playlist

import androidx.media3.common.MediaItem
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.appContext
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.models.Playlist
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asPodcastMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.canRefreshPlaylist
import app.n_zik.android.core.database.isPodcastBrowseId
import app.n_zik.android.core.database.isReadOnlyPlaylistSource
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Fetches the complete source before permitting any local membership changes. */
internal suspend fun loadPlaylistSource(browseId: String): List<MediaItem>? {
    if (isPodcastBrowseId(browseId)) {
        return loadCompletePodcastEpisodes(browseId).map { it.asPodcastMediaItem(browseId) }
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

private val playlistSourceRefreshLocks = List(32) { Mutex() }

/** Refresh preserves local order for surviving songs and appends newly discovered source songs. */
internal suspend fun refreshPlaylistSource(
    playlist: Playlist,
    refreshAllowed: () -> Boolean = { true },
    load: suspend (String) -> List<MediaItem>? = ::loadPlaylistSource,
): Boolean = withContext(NzikDispatchers.DATA) {
    val browseId = playlist.browseId ?: return@withContext false
    if (!refreshAllowed() || !canRefreshPlaylist(playlist)) return@withContext false
    val context = appContext()
    val owner = ProfileWorkOwner.capture(context) ?: return@withContext false
    val lock = playlistSourceRefreshLocks[(owner to playlist.id).hashCode() and (playlistSourceRefreshLocks.size - 1)]
    lock.withLock {
        if (!refreshAllowed() || !ProfileWorkOwner.isBound(context, owner)) return@withLock false
        publishPlaylistSource(playlist, browseId, requestAllowed = {
            refreshAllowed() && ProfileWorkOwner.isBound(context, owner)
        }, load)
    }
}

private suspend fun publishPlaylistSource(
    playlist: Playlist,
    browseId: String,
    requestAllowed: () -> Boolean,
    load: suspend (String) -> List<MediaItem>?,
): Boolean {
    val source = load(browseId)?.filterNot(MediaItem::isLocal)?.distinctBy(MediaItem::mediaId)?.takeIf { it.isNotEmpty() }
        ?: return false
    var refreshed = false
    Database.transaction {
        val latest = playlistTable.findById(playlist.id).first() ?: return@transaction
        if (!requestAllowed() || latest.browseId != browseId || latest.isEditable != playlist.isEditable ||
            !canRefreshPlaylist(latest)) return@transaction
        val sourceById = source.associateBy(MediaItem::mediaId)
        val mappings = songPlaylistMapTable.mappingsByPosition(latest.id)
        val previousById = mappings.associateBy { it.songId }
        val orderedIds = if (isReadOnlyPlaylistSource(latest)) {
            mappings.map { it.songId }.filter(sourceById::containsKey) + sourceById.keys.filterNot(previousById::containsKey)
        } else sourceById.keys.toList()
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
        mappings.filterNot { it.songId in sourceById }.forEach { mapping ->
            songPlaylistMapTable.deleteBySongId(mapping.songId, latest.id)
        }
        orderedIds.forEachIndexed { index, songId ->
            val previous = previousById[songId]
            if (previous == null) {
                songTable.insertIgnore(sourceById.getValue(songId).asSong)
                songPlaylistMapTable.mapAtPosition(songId, latest.id, index)
            } else if (previous.position != index) {
                songPlaylistMapTable.updatePosition(latest.id, songId, index)
            }
        }
        refreshed = true
    }
    return refreshed
}

/** Starts a source-only refresh; automatic opening refresh avoids transient toast noise. */
fun launchPlaylistSourceRefresh(playlist: Playlist?, scope: CoroutineScope, silent: Boolean = false) {
    if (playlist == null || !canRefreshPlaylist(playlist)) return
    scope.launch(NzikDispatchers.DATA) {
        try {
            val refreshed = refreshPlaylistSource(playlist)
            if (!silent) {
                if (refreshed) Toaster.done() else Toaster.e(R.string.common_error_title)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("PlaylistRefresh").e(error, "Unable to refresh playlist source")
            if (!silent) Toaster.e(R.string.common_error_title)
        }
    }
}
