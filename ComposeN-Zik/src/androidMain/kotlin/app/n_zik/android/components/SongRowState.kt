package app.n_zik.android.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.LocalDownloadHelper
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.core.database.Database
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.models.Info
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.hasCachedStream
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.isNetworkAvailableComposable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

@Stable
@UnstableApi
internal class SongRowDownloads(
    private val downloads: State<Map<String, Download>>,
    private val progresses: State<Map<String, Float>>,
    private val online: Boolean,
    private val preview: Boolean,
) {
    fun state(id: String): Int = when {
        preview -> Download.STATE_COMPLETED
        !online -> Download.STATE_STOPPED
        else -> rawState(id)
    }

    fun rawState(id: String): Int = downloads.value[id]?.state ?: Download.STATE_STOPPED

    fun completedIds(): Set<String> = downloads.value.values
        .filter { it.state == Download.STATE_COMPLETED }
        .mapTo(HashSet()) { it.request.id }

    fun progress(id: String): Float = progresses.value[id] ?: 0f

    fun isDownloaded(id: String): Boolean = downloads.value[id]?.state == Download.STATE_COMPLETED

    fun mediaState(id: String, cached: Boolean): DownloadedStateMedia = when {
        isDownloaded(id) && cached -> DownloadedStateMedia.CACHED_AND_DOWNLOADED
        isDownloaded(id) -> DownloadedStateMedia.DOWNLOADED
        cached -> DownloadedStateMedia.CACHED
        else -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
    }
}

@UnstableApi
internal val LocalSongRowDownloads = compositionLocalOf<SongRowDownloads?> { null }

internal val LocalSongRowCachedIds = compositionLocalOf<Set<String>?> { null }

@Composable
@UnstableApi
internal fun rememberSongRowDownloads(): SongRowDownloads {
    val preview = LocalThemePreview.current != null
    if (preview) return remember {
        SongRowDownloads(mutableStateOf(emptyMap()), mutableStateOf(emptyMap()), online = false, preview = true)
    }
    val downloader = LocalDownloadHelper.current
    val online = isNetworkAvailableComposable()
    val downloads = downloader.downloads.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
    val progress = downloader.progresses.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
    return remember(downloads, progress, online) { SongRowDownloads(downloads, progress, online, preview = false) }
}

@Composable
@UnstableApi
internal fun rememberSongRowCache(ids: List<String>): State<Set<String>> {
    val cache = LocalPlayerServiceBinder.current?.cache.takeIf { LocalThemePreview.current == null }
    return remember(cache, ids) {
        if (cache == null || ids.isEmpty()) flowOf(emptySet())
        else combine(ids.chunked(500).map { Database.formatTable.observeSongRowCacheEntries(it) }) { chunks ->
            buildSet {
                for (chunk in chunks) {
                    for (row in chunk) {
                        if (cache.hasCachedStream(row.songId, row.contentLength)) add(row.songId)
                    }
                }
            }
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(emptySet(), context = NzikDispatchers.DATA)
}

@Composable
internal fun rememberSongRowMetadata(ids: List<String>): State<Map<String, Song>> {
    val preview = LocalThemePreview.current
    return remember(ids, preview) {
        if (preview != null || ids.isEmpty()) flowOf(emptyMap())
        else combine(ids.chunked(500).map { Database.songTable.findByIds(it) }) { batches ->
            batches.asSequence().flatMap { it.asSequence() }.associateBy(Song::id)
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
}

/** Collects track-linked artist rows alongside a list's existing metadata query. */
@Composable
internal fun rememberSongRowArtistCredits(ids: List<String>): State<Map<String, List<Info>>> {
    val preview = LocalThemePreview.current
    return remember(ids, preview) {
        if (preview != null || ids.isEmpty()) flowOf(emptyMap())
        else combine(ids.distinct().chunked(500).map(Database.songTable::findArtistCredits)) { batches ->
            batches.asSequence().flatten().groupBy({ it.songId }, { Info(it.artistId, it.name) })
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
}

internal fun Song.rowLikeState(): Boolean? = when {
    likedAt == -1L -> false
    (likedAt ?: 0L) > 0L -> true
    else -> null
}
