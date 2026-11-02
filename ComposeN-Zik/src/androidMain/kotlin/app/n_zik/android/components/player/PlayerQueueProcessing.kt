package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class PlayerQueueRow(
    val window: Timeline.Window,
    val song: Song,
    val title: String,
    val artists: String,
)

internal data class NormalizedPlayerQueue(
    val source: List<Timeline.Window>,
    val rows: List<PlayerQueueRow>,
    val songs: List<Song>,
)

internal data class PlayerQueueSnapshot(
    val source: List<Timeline.Window>,
    val input: String,
    val rows: List<PlayerQueueRow>,
    val songs: List<Song>,
    val songIds: List<String>,
    val allSongs: List<Song>,
    val rowsByUid: Map<Any, PlayerQueueRow>,
) {
    companion object {
        val Empty = PlayerQueueSnapshot(emptyList(), "", emptyList(), emptyList(),
            emptyList(), emptyList(), emptyMap())
    }
}

/** Resolves bulk actions against current detached windows, even while visual normalization is pending. */
@OptIn(UnstableApi::class)
internal fun playerQueueBulkSongs(
    snapshot: PlayerQueueSnapshot,
    windows: List<Timeline.Window>,
    selected: List<Song>,
    songOf: (MediaItem) -> Song = { it.asSong },
): List<Song> {
    val songs = if (snapshot.source === windows) snapshot.allSongs else {
        val cached = snapshot.source.zip(snapshot.allSongs).associateBy { it.first.uid }
        windows.map { window ->
            cached[window.uid]?.takeIf { it.first.mediaItem === window.mediaItem }?.second
                ?: songOf(window.mediaItem)
        }
    }
    if (selected.isEmpty()) return songs
    val currentById = songs.associateBy { it.id }
    return selected.mapNotNull { currentById[it.id] }
}

/** Event-time lookup keeps retained visual rows from acting on obsolete queue positions. */
@OptIn(UnstableApi::class)
internal fun findPlayerQueueWindowIndex(timeline: Timeline, uid: Any): Int {
    val window = Timeline.Window()
    for (index in 0 until timeline.windowCount) {
        if (timeline.getWindow(index, window).uid == uid) return index
    }
    return -1
}

/** Receives detached timeline windows captured on UI; never calls a Player off UI. */
@OptIn(UnstableApi::class)
internal suspend fun normalizePlayerQueue(
    windows: List<Timeline.Window>,
    previous: NormalizedPlayerQueue? = null,
    songOf: (MediaItem) -> Song = { it.asSong },
): NormalizedPlayerQueue = withContext(NzikDispatchers.MEDIA) {
    val previousByUid = previous?.rows?.associateBy { it.window.uid }.orEmpty()
    val rows = windows.map { window ->
        currentCoroutineContext().ensureActive()
        val cached = previousByUid[window.uid]
        if (cached != null && cached.window.mediaItem === window.mediaItem) {
            // A drag changes only order; keep converted/cleaned metadata and Song identity.
            cached.copy(window = window)
        } else {
            val song = songOf(window.mediaItem)
            PlayerQueueRow(window, song, song.cleanTitle(), song.cleanArtistsText())
        }
    }
    NormalizedPlayerQueue(windows, rows, rows.map { it.song })
}

/** Preserves contains(ignoreCase=true), including whitespace and prefix cleaning semantics. */
internal suspend fun filterPlayerQueue(
    normalized: NormalizedPlayerQueue,
    input: String,
): PlayerQueueSnapshot = withContext(NzikDispatchers.MEDIA) {
    val rows = ArrayList<PlayerQueueRow>()
    val songs = ArrayList<Song>()
    val songIds = ArrayList<String>()
    val rowsByUid = LinkedHashMap<Any, PlayerQueueRow>()
    for (row in normalized.rows) {
        currentCoroutineContext().ensureActive()
        if (input.isEmpty() || row.title.contains(input, ignoreCase = true) ||
            row.artists.contains(input, ignoreCase = true)) {
            rows.add(row)
            songs.add(row.song)
            songIds.add(row.song.id)
            rowsByUid[row.window.uid] = row
        }
    }
    PlayerQueueSnapshot(normalized.source, input, rows, songs, songIds,
        normalized.songs, rowsByUid)
}

private class QueueSnapshotIdentityKey(private val value: Any?) {
    override fun equals(other: Any?): Boolean = other is QueueSnapshotIdentityKey && value === other.value
    override fun hashCode(): Int = System.identityHashCode(value)
}

/** Last valid visual rows remain while cancellable work publishes the newest queue/query. */
@Composable
internal fun rememberPlayerQueueSnapshot(
    windows: List<Timeline.Window>,
    input: String,
    normalizeQueue: suspend (List<Timeline.Window>, NormalizedPlayerQueue?) -> NormalizedPlayerQueue =
        { source, previous -> normalizePlayerQueue(source, previous) },
    filterQueue: suspend (NormalizedPlayerQueue, String) -> PlayerQueueSnapshot = ::filterPlayerQueue,
): PlayerQueueSnapshot {
    var normalized by remember { mutableStateOf<NormalizedPlayerQueue?>(null, referentialEqualityPolicy()) }
    var result by remember { mutableStateOf(PlayerQueueSnapshot.Empty, referentialEqualityPolicy()) }
    val sourceKey = QueueSnapshotIdentityKey(windows)
    LaunchedEffect(sourceKey) {
        normalized = normalizeQueue(windows, normalized)
    }
    LaunchedEffect(sourceKey, QueueSnapshotIdentityKey(normalized), input) {
        val current = normalized
        if (current != null && current.source === windows) {
            result = filterQueue(current, input)
        }
    }
    return result
}

internal suspend fun playerQueueDuration(
    mediaItems: List<MediaItem>,
    parseDuration: (String) -> Long = ::durationTextToMillis,
): Long = withContext(NzikDispatchers.MEDIA) {
    var total = 0L
    for (item in mediaItems) {
        currentCoroutineContext().ensureActive()
        total += item.mediaMetadata.extras?.getString("durationText")?.let(parseDuration) ?: 0L
    }
    total
}

/** Parent recompositions reuse the result; disabled duration performs no metadata parsing. */
@Composable
fun rememberPlayerQueueDuration(mediaItems: List<MediaItem>, enabled: Boolean): Long {
    val duration by produceState(0L, mediaItems, enabled) {
        if (enabled) value = playerQueueDuration(mediaItems)
    }
    return if (enabled) duration else 0L
}
