package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.components.rememberSongRowArtistCredits
import app.n_zik.android.components.song.resolveSongDisplayMetadata
import app.n_zik.android.components.song.isArtistIdForSong
import app.n_zik.android.core.database.Database
import app.n_zik.android.models.Info
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.SessionDurationMetadata
import app.n_zik.android.playback.services.automotive.session.enrichAutoQueueDurations
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.resolveTrackArtistName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class PlayerQueueRow(
    val window: Timeline.Window,
    val song: Song,
    val title: String,
    val artists: String,
    val displaySong: Song = song,
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
    fun initialPlaceholderCount(windowCount: Int): Int = if (source.isEmpty()) windowCount else 0

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

@Composable
internal fun rememberPlayerQueueArtists(songIds: List<String>): Map<String, List<Info>> =
    rememberSongRowArtistCredits(songIds).value

private fun resolvePlayerQueueArtist(
    song: Song,
    artistLinks: List<Info>,
    unknownArtist: String,
): Song {
    if (artistLinks.isEmpty()) return song
    val links = artistLinks.filter { isArtistIdForSong(it.id, song.isLocal) }
    val artist = resolveTrackArtistName(null, song.artistsText, links, unknownArtist)
    if (artist == null || artist == song.cleanArtistsText()) return song
    return song.copy(artistsText = artist).apply { playCount = song.playCount }
}

/** Last valid visual rows remain while cancellable work publishes the newest queue/query. */
@Composable
internal fun rememberPlayerQueueSnapshot(
    windows: List<Timeline.Window>,
    input: String,
    normalizeQueue: suspend (List<Timeline.Window>, NormalizedPlayerQueue?) -> NormalizedPlayerQueue =
        { source, previous -> normalizePlayerQueue(source, previous) },
    filterQueue: suspend (NormalizedPlayerQueue, String) -> PlayerQueueSnapshot = ::filterPlayerQueue,
    storedSongs: Map<String, Song> = emptyMap(),
    storedArtists: Map<String, List<Info>> = emptyMap(),
    unknownArtist: String = "Unknown Artist",
): PlayerQueueSnapshot {
    var normalized by remember { mutableStateOf<NormalizedPlayerQueue?>(null, referentialEqualityPolicy()) }
    var result by remember { mutableStateOf(PlayerQueueSnapshot.Empty, referentialEqualityPolicy()) }
    val sourceKey = QueueSnapshotIdentityKey(windows)
    LaunchedEffect(sourceKey) {
        normalized = normalizeQueue(windows, normalized)
    }
    LaunchedEffect(sourceKey, QueueSnapshotIdentityKey(normalized), input, storedSongs, storedArtists, unknownArtist) {
        val current = normalized
        if (current != null && current.source === windows) {
            val displayQueue = if (storedSongs.isEmpty() && storedArtists.isEmpty()) current else withContext(NzikDispatchers.MEDIA) {
                val rows = current.rows.map { row ->
                    currentCoroutineContext().ensureActive()
                    val id = row.song.id
                    val song = resolveSongDisplayMetadata(row.song, storedSongs[id])
                    val displaySong = resolvePlayerQueueArtist(song, storedArtists[id].orEmpty(), unknownArtist)
                    if (song === row.song && displaySong === song) row else row.copy(
                        song = song,
                        title = displaySong.cleanTitle(),
                        artists = displaySong.cleanArtistsText(),
                        displaySong = displaySong,
                    )
                }
                NormalizedPlayerQueue(current.source, rows, rows.map { it.song })
            }
            result = filterQueue(displayQueue, input)
        }
    }
    return result
}

internal sealed interface PlayerQueueDuration {
    data object Loading : PlayerQueueDuration
    data object Unavailable : PlayerQueueDuration
    data class Known(val milliseconds: Long) : PlayerQueueDuration
    data class AtLeast(val milliseconds: Long) : PlayerQueueDuration
}

@OptIn(UnstableApi::class)
internal suspend fun playerQueueDuration(
    timeline: Timeline,
    findSongs: suspend (List<String>) -> List<Song>,
): PlayerQueueDuration = withContext(NzikDispatchers.MEDIA) {
    // A Timeline is immutable. Each Window is populated into scratch storage owned by this calculation.
    val scratch = Timeline.Window()
    val missing = ArrayList<MediaItem>()
    var total = 0L
    for (index in 0 until timeline.windowCount) {
        currentCoroutineContext().ensureActive()
        val window = timeline.getWindow(index, scratch)
        val metadata = window.mediaItem.mediaMetadata
        val length = window.durationMs.takeIf { !window.isPlaceholder && !window.isDynamic && it > 0 }
            ?: metadata.durationMs?.takeIf { it > 0 }
            ?: SessionDurationMetadata.fromDurationText(metadata.extras?.getString("durationText"))
        if (length == null) {
            missing.add(window.mediaItem)
        } else {
            if (length > Long.MAX_VALUE - total) return@withContext PlayerQueueDuration.Unavailable
            total += length
        }
    }
    var incomplete = false
    if (missing.isNotEmpty()) {
        for (item in enrichAutoQueueDurations(missing, findSongs)) {
            currentCoroutineContext().ensureActive()
            val length = item.mediaMetadata.durationMs?.takeIf { it > 0 }
            if (length == null) {
                incomplete = true
                continue
            }
            if (length > Long.MAX_VALUE - total) return@withContext PlayerQueueDuration.Unavailable
            total += length
        }
    }
    when {
        !incomplete -> PlayerQueueDuration.Known(total)
        total > 0L -> PlayerQueueDuration.AtLeast(total)
        else -> PlayerQueueDuration.Unavailable
    }
}

@Composable
internal fun rememberPlayerQueueDuration(
    timeline: Timeline,
    mediaItems: List<MediaItem>,
    enabled: Boolean,
    profile: String,
): PlayerQueueDuration {
    if (!enabled) return PlayerQueueDuration.Unavailable
    if (timeline.isEmpty) return PlayerQueueDuration.Known(0L)
    val membership = remember(mediaItems) { mediaItems.groupingBy { it.mediaId }.eachCount() }
    return key(membership, profile) {
        val duration by produceState<PlayerQueueDuration>(
            PlayerQueueDuration.Loading,
            QueueSnapshotIdentityKey(timeline),
        ) {
            val updated = playerQueueDuration(timeline) { ids ->
                if (Database.FILE_NAME == Database.fileNameForProfile(profile)) {
                    Database.songTable.findByIdsDirect(ids)
                } else {
                    emptyList()
                }
            }
            if (updated is PlayerQueueDuration.Known || value !is PlayerQueueDuration.Known) {
                value = updated
            }
        }
        duration
    }
}
