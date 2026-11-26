package app.n_zik.android.playback.services

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.song.isArtistIdForSong
import app.n_zik.android.core.database.PlaybackTrackRow
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.listentogether.ListenTogetherPlayerBridge
import app.n_zik.android.models.Info
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.utils.ARTIST_LINKED_NAMES_EXTRA
import app.n_zik.android.utils.DERIVED_PLAYBACK_ALBUM_EXTRA
import app.n_zik.android.utils.EXPLICIT_BUNDLE_TAG
import app.n_zik.android.utils.alignInfosToDisplayOrder
import app.n_zik.android.utils.artistLinksWithStoredFallback
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.displayTrackTitle
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.playbackAlbumId
import app.n_zik.android.utils.resolveTrackArtistName
import app.n_zik.android.utils.usableTrackArtistName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal const val COMMITTED_TRACK_IDENTITY_EXTRA = "committedTrackIdentity"

private data class TrackIdentityPatch(
    val start: Int,
    val endExclusive: Int,
    val items: List<MediaItem>,
)

internal fun SongTable.observePlaybackIdentity(ids: List<String>): Flow<Map<String, PlaybackTrackRow>> = flow {
    val queries = ids.distinct().chunked(500).map(::observePlaybackTracks)
    if (queries.isEmpty()) {
        emit(emptyMap())
        return@flow
    }
    emitAll(combine(queries) { batches ->
        batches.asSequence().flatten().associateBy { it.song.id }
    })
}.distinctUntilChanged()

/** Projects committed identity onto an item while retaining its playback configuration. */
@UnstableApi
internal fun MediaItem.withCurrentTrackIdentity(
    row: PlaybackTrackRow,
    unknownArtist: String,
    originalId: (String) -> String?,
): MediaItem {
    if (ListenTogetherPlayerBridge.isListenTogetherItem(this)) return this
    val metadata = mediaMetadata
    val existing = metadata.extras
    val extras = Bundle(existing ?: Bundle()).apply { putBoolean(COMMITTED_TRACK_IDENTITY_EXTRA, true) }
    val storedTitle = displayTrackTitle(row.song.title)
    val title = storedTitle ?: metadata.title?.toString()
    if (storedTitle != null) {
        extras.putBoolean("isTitleModified", row.song.title.startsWith(MODIFIED_PREFIX, ignoreCase = true))
        extras.putBoolean(EXPLICIT_BUNDLE_TAG, row.song.title.hasExplicitPrefix())
        extras.putBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT", row.song.title.hasExplicitPrefix())
    }

    val storedArtist = usableTrackArtistName(row.song.artistsText, unknownArtist)
    val storedLinks = row.artists.map { Info(it.id, it.name) }.filter {
        isArtistIdForSong(it.id, isLocal) && usableTrackArtistName(it.name, unknownArtist) != null
    }
    val fallbackArtist = metadata.artist?.toString()
    val fallbackLinks = artistLinksWithStoredFallback(emptyList(), originalId)
    val links = if (storedLinks.isNotEmpty()) storedLinks else fallbackLinks.filter { link ->
        storedArtist == null || storedArtist.split(',').any {
            it.trim().equals(link.name?.let(::cleanPrefix)?.trim(), ignoreCase = true)
        }
    }
    val richerFallback = usableTrackArtistName(fallbackArtist, unknownArtist)?.takeIf {
        !row.song.artistsText.orEmpty().startsWith(MODIFIED_PREFIX, ignoreCase = true) &&
            it.count { c -> c == ',' } > storedArtist.orEmpty().count { c -> c == ',' }
    }
    val artist = resolveTrackArtistName(richerFallback, row.song.artistsText, links, unknownArtist)
        ?: usableTrackArtistName(fallbackArtist, unknownArtist) ?: fallbackArtist
    val orderedLinks = alignInfosToDisplayOrder(links, artist)
    val ids = ArrayList(orderedLinks.map(Info::id))
    val linkedNames = ArrayList(orderedLinks.map { cleanPrefix(it.name.orEmpty()) })
    val artistNames = ArrayList(artist.orEmpty().split(',').map(String::trim).filter(String::isNotBlank))
    extras.putStringArrayList("artistIds", ids)
    extras.putStringArrayList(ARTIST_LINKED_NAMES_EXTRA, linkedNames)
    extras.putStringArrayList("artistNames", artistNames)
    if (storedArtist != null) {
        extras.putBoolean("isArtistModified", row.song.artistsText?.startsWith(MODIFIED_PREFIX, ignoreCase = true) == true)
    }

    val previousAlbumId = existing?.getString("albumId")
    val albumId = playbackAlbumId(row.albums, null, originalId)
    val albumTitle = row.albums.firstOrNull { it.id == albumId }?.title?.let(::displayTrackTitle)
        ?: metadata.albumTitle?.toString().takeIf { albumId == previousAlbumId }
    if (albumId != null && previousAlbumId == null) extras.putBoolean(DERIVED_PLAYBACK_ALBUM_EXTRA, true)
    extras.putString("albumId", albumId)
    val previousDuration = existing?.getString("durationText")
    val duration = row.song.durationText?.takeIf { durationTextToMillis(it) > 0 } ?: previousDuration
    if (duration != previousDuration) extras.putString("durationText", duration)
    val storedArtwork = row.song.thumbnailUrl?.let(::cleanPrefix)?.takeIf(String::isNotBlank)
    val artworkUri = if (metadata.artworkUri?.toString().isNullOrBlank() && storedArtwork != null) {
        extras.putString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA, storedArtwork)
        extras.putBoolean("isCoverModified", row.song.thumbnailUrl?.startsWith(MODIFIED_PREFIX, ignoreCase = true) == true)
        Uri.parse(storedArtwork)
    } else {
        metadata.artworkUri
    }

    if (existing?.getBoolean(COMMITTED_TRACK_IDENTITY_EXTRA) == true &&
        title == metadata.title?.toString() && artist == metadata.artist?.toString() &&
        albumTitle == metadata.albumTitle?.toString() && albumId == previousAlbumId && artworkUri == metadata.artworkUri &&
        duration == previousDuration &&
        ids == existing?.getStringArrayList("artistIds").orEmpty() &&
        linkedNames == existing?.getStringArrayList(ARTIST_LINKED_NAMES_EXTRA).orEmpty() &&
        artistNames == existing?.getStringArrayList("artistNames").orEmpty() &&
        listOf("isTitleModified", "isArtistModified", EXPLICIT_BUNDLE_TAG, "androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT")
            .all { extras.getBoolean(it) == existing?.getBoolean(it, false).let { flag -> flag == true } }
    ) return this
    return buildUpon().setMediaMetadata(
        metadata.buildUpon().setTitle(title).setArtist(artist).setAlbumTitle(albumTitle)
            .setArtworkUri(artworkUri).setExtras(extras).build()
    ).build()
}

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
internal fun Player.bindPlaybackIdentity(
    scope: CoroutineScope,
    songs: SongTable,
    ownsProfile: () -> Boolean,
    unknownArtist: () -> String,
    originalId: (String) -> String?,
    onCurrentItem: (MediaItem?) -> Unit,
) = scope.launch(NzikDispatchers.UI) {
    var observedIds = emptyList<String>()
    var rows = emptyMap<String, PlaybackTrackRow>()
    var projectingQueue = false
    var projectionPending = false
    var playlistRevision = 0L
    var projectionRevision = 0L

    fun deferDuringBuffering(): Boolean {
        projectionPending = playWhenReady && playbackState == Player.STATE_BUFFERING
        return projectionPending
    }

    suspend fun projectQueue() {
        if (!ownsProfile()) return
        val requestRevision = ++projectionRevision
        if (deferDuringBuffering()) return
        val sourceRevision = playlistRevision
        val rowSnapshot = rows
        // Media3 timelines are immutable. Only capture the reference on the player thread.
        val timeline = currentTimeline
        val unknown = unknownArtist()
        val patch = projectTrackIdentityPatch(timeline, rowSnapshot, unknown, originalId)
        if (requestRevision != projectionRevision || sourceRevision != playlistRevision ||
            rows !== rowSnapshot || !ownsProfile() || deferDuringBuffering()
        ) return
        if (patch != null) {
            // A timeline change republishes the full queue to Android media controllers.
            projectingQueue = true
            try {
                replaceMediaItems(patch.start, patch.endExclusive, patch.items)
            } finally {
                projectingQueue = false
            }
        }
        onCurrentItem(currentMediaItem)
    }

    callbackFlow {
        var refresh: Job? = null

        fun scheduleProjection() {
            refresh?.cancel()
            refresh = launch(NzikDispatchers.UI) { projectQueue() }
        }

        fun sendTrackIds() {
            val ids = (0 until mediaItemCount).map { getMediaItemAt(it).sessionArtistTrackId() }.distinct()
            if (ids == observedIds && rows.isNotEmpty()) {
                // A caller can replace a snapshot in place without changing the set of track IDs.
                scheduleProjection()
            }
            trySend(ids)
        }
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && !projectingQueue) {
                    playlistRevision++
                    sendTrackIds()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (projectionPending && playbackState != Player.STATE_BUFFERING) scheduleProjection()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (projectionPending && !playWhenReady) scheduleProjection()
            }
        }
        addListener(listener)
        sendTrackIds()
        awaitClose { removeListener(listener) }
    }.distinctUntilChanged().collectLatest { ids ->
        observedIds = ids
        rows = emptyMap()
        songs.observePlaybackIdentity(ids)
            .flowOn(NzikDispatchers.DATA)
            .catch { error -> Timber.tag("PlaybackTrackIdentity").w(error, "Unable to observe playback identity") }
            .collectLatest { latest ->
                rows = latest
                projectQueue()
            }
    }
}

@OptIn(UnstableApi::class)
private suspend fun projectTrackIdentityPatch(
    timeline: Timeline,
    rows: Map<String, PlaybackTrackRow>,
    unknownArtist: String,
    originalId: (String) -> String?,
): TrackIdentityPatch? = withContext(NzikDispatchers.MEDIA) {
    if (rows.isEmpty()) return@withContext null
    val window = Timeline.Window()
    val owner = currentCoroutineContext()
    var firstChanged = -1
    var lastChanged = -1
    val projected = List(timeline.windowCount) { index ->
        owner.ensureActive()
        val item = timeline.getWindow(index, window).mediaItem
        val updated = rows[item.sessionArtistTrackId()]?.let { row ->
            item.withCurrentTrackIdentity(row, unknownArtist, originalId)
        } ?: item
        if (updated !== item) {
            if (firstChanged == -1) firstChanged = index
            lastChanged = index
        }
        updated
    }
    if (firstChanged == -1) return@withContext null
    val endExclusive = lastChanged + 1
    TrackIdentityPatch(firstChanged, endExclusive, projected.subList(firstChanged, endExclusive))
}
