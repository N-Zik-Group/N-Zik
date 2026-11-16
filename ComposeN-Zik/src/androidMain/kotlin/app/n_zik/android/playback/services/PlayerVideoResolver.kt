package app.n_zik.android.playback.services

import kotlin.math.abs

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.cleanPrefix
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.isExplicit
import app.n_zik.android.utils.isVideo
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.MusicVideoPositionMap
import app.n_zik.innertube.requests.albumPage
import app.n_zik.innertube.requests.albumVideoItems
import app.n_zik.innertube.requests.musicVideoCounterpart
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.requests.song
import app.n_zik.innertube.utils.from

private const val TAG = "PlayerVideoResolver"
private const val MAX_COUNTERPART_DURATION_DELTA_MS = 3_000L
private val youtubeVideoId = Regex("[A-Za-z0-9_-]{11}")
private val titleDecoration = Regex(
    """\s*[\[(]\s*(?:official (?:music )?video|music video|clip officiel|video oficial)\s*[\])]\s*$""",
    RegexOption.IGNORE_CASE,
)
private val artistTitleSeparator = Regex("""\s+[-–—]\s+|\s*:\s*""")

/** A direct stream selected by InnerTubeX. The signed URL and headers are session data. */
internal class PlayerVideoStream(
    val uri: Uri,
    val headers: Map<String, String>,
    val mimeType: String?,
    val durationMs: Long?,
    private val positionMap: MusicVideoPositionMap? = null,
) {
    fun positionForAudio(audioPositionMs: Long): Long {
        val audioPosition = audioPositionMs.coerceAtLeast(0)
        val position = positionMap?.mapPosition(audioPosition) ?: audioPosition
        return durationMs?.let { position.coerceIn(0, it) } ?: position.coerceAtLeast(0)
    }
}

internal fun canResolvePlayerVideo(mediaItem: MediaItem): Boolean =
    youtubeVideoId.matches(mediaItem.mediaId)

/** Finds a clip that can be rendered against the current audio playback clock. */
internal suspend fun resolvePlayerVideo(
    mediaItem: MediaItem,
    audioDurationMs: Long = C.TIME_UNSET,
): PlayerVideoStream? = withContext(NzikDispatchers.DATA) {
    if (!canResolvePlayerVideo(mediaItem)) return@withContext null

    try {
        val metadata = mediaItem.mediaMetadata
        val knownDuration = audioDurationMs.takeIf { it > 0 }
            ?: metadata.extras?.getString("durationText")?.let(::durationTextToMillis)
            ?: 0L
        resolvePlayableVideo(mediaItem, knownDuration)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // Extractor errors can include a signed URL or request details.
        Timber.tag(TAG).w("Video resolution failed (%s)", error::class.simpleName)
        null
    }
}

private suspend fun resolvePlayableVideo(mediaItem: MediaItem, audioDurationMs: Long): PlayerVideoStream? {
    val sourceId = mediaItem.mediaId
    val attemptedIds = mutableSetOf<String>()
    suspend fun resolveCandidate(
        videoId: String,
        positionMap: MusicVideoPositionMap? = null,
        checkDuration: Boolean = false,
    ): PlayerVideoStream? {
        if (!attemptedIds.add(videoId)) return null
        val stream = tryVideoLookup("video stream") {
            InnerTubeXPlayer.extractPlayerVideo(videoId, mediaItem.isExplicit)
        } ?: return null
        val uri = stream.videoUrl?.toUri()?.takeIf { it.scheme == "https" } ?: return null
        val durationMs = stream.mediaMetadata?.durationSeconds?.takeIf { it > 0 }?.times(1_000)
        if (checkDuration && durationMs != null && abs(audioDurationMs - durationMs) > MAX_COUNTERPART_DURATION_DELTA_MS) {
            return null
        }
        return PlayerVideoStream(uri, stream.headers, stream.videoMimeType, durationMs, positionMap)
    }

    val cachedVideoType = playbackDataCache[sourceId]?.videoDetails?.musicVideoType
    if (mediaItem.isVideo || isPlayableMusicVideoType(cachedVideoType)) {
        resolveCandidate(sourceId)?.let { return it }
    }
    if (cachedVideoType == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" ||
        cachedVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"
    ) return null

    val metadata = mediaItem.mediaMetadata
    val linked = tryVideoLookup("linked clip") { Innertube.musicVideoCounterpart(sourceId) }
    if (linked != null && linked.targetVideoId != sourceId && youtubeVideoId.matches(linked.targetVideoId) &&
        (linked.targetMusicVideoType == null || isOfficialMusicVideoType(linked.targetMusicVideoType) ||
            linked.targetMusicVideoType?.contains("LIVE", ignoreCase = true) == true)
    ) {
        resolveCandidate(linked.targetVideoId, linked.positionMap)?.let { return it }
    }

    val title = metadata.title?.toString()?.let(::cleanPrefix)?.trim().orEmpty()
    if (title.isEmpty()) return null
    val albumId = metadata.extras?.getString("albumId")?.takeIf(String::isNotBlank)
        ?: tryVideoLookup("track album") {
            Innertube.song(sourceId)?.getOrNull()?.album?.endpoint?.browseId
        }
    if (!albumId.isNullOrBlank()) {
        val direct = tryVideoLookup("album video shelf") { Innertube.albumVideoItems(albumId) }.orEmpty()
        selectAlbumVideoCounterpart(sourceId, title, direct, attemptedIds)
            ?.let { resolveCandidate(it) }?.let { return it }

        val playlist = tryVideoLookup("album playlist") {
            Innertube.albumPage(albumId)?.getOrNull()?.songsPage?.items
        }.orEmpty()
        selectAlbumVideoCounterpart(sourceId, title, playlist, attemptedIds)
            ?.let { resolveCandidate(it) }?.let { return it }
    }

    val artist = metadata.artist?.toString()?.let(::cleanPrefix)?.trim().orEmpty()
    if (artist.isEmpty() || audioDurationMs <= 0) return null
    val artistNames = metadata.extras?.getStringArrayList("artistNames").orEmpty().ifEmpty { listOf(artist) }
    val artistIds = metadata.extras?.getStringArrayList("artistIds").orEmpty().toSet()
    for (query in listOf("$title $artist official music video", "$title $artist")) {
        val results = Innertube.searchPage(
            query = query,
            params = Innertube.SearchFilter.Video.value,
            fromMusicShelfRendererContent = Innertube.VideoItem::from,
        )?.getOrNull()?.items.orEmpty()
        currentCoroutineContext().ensureActive()
        val unseenResults = results.filterNot { it.info?.endpoint?.videoId in attemptedIds }
        selectPlayerVideoCounterpart(title, artistNames, artistIds, audioDurationMs, unseenResults)
            ?.let { resolveCandidate(it, checkDuration = true) }?.let { return it }
    }
    return null
}

private suspend fun <T> tryVideoLookup(stage: String, lookup: suspend () -> T?): T? = try {
    lookup().also { currentCoroutineContext().ensureActive() }
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Timber.tag(TAG).d("Video lookup failed at %s (%s)", stage, error::class.simpleName)
    null
}

/** Selects the clip release associated with the source song's album. */
internal fun selectAlbumVideoCounterpart(
    sourceId: String,
    title: String,
    tracks: List<Innertube.SongItem>,
    attemptedIds: Set<String> = emptySet(),
): String? {
    val trackTitle = coreVideoTitle(title)
    return tracks.filter { track ->
        track.key != sourceId && track.key !in attemptedIds && youtubeVideoId.matches(track.key) &&
            isPlayableMusicVideoType(track.info?.endpoint?.type) &&
            (tracks.size == 1 || track.title?.let(::coreVideoTitle).equals(trackTitle, ignoreCase = true))
    }.map(Innertube.SongItem::key).distinct().singleOrNull()
}

/** Matches a unique official clip using title, artist and approximate duration. */
internal fun selectPlayerVideoCounterpart(
    title: String,
    artistNames: List<String>,
    artistIds: Set<String>,
    audioDurationMs: Long,
    results: List<Innertube.VideoItem>,
): String? {
    if (title.isBlank() || audioDurationMs <= 0) return null
    val coreTitle = coreVideoTitle(title)
    return results.mapNotNull { candidate ->
        val videoId = candidate.info?.endpoint?.videoId
        val durationMs = candidate.durationText?.let(::durationTextToMillis) ?: 0L
        val matchesArtist = candidate.authors.orEmpty().any { author ->
            val id = author.endpoint?.browseId
            if (!id.isNullOrBlank() && artistIds.isNotEmpty()) id in artistIds
            else artistNames.any { author.name?.trim().equals(it.trim(), ignoreCase = true) }
        }
        if (
            videoId != null && youtubeVideoId.matches(videoId) &&
            isOfficialMusicVideoType(candidate.info?.endpoint?.type) &&
            candidate.title?.let { searchVideoTitle(it, artistNames) }.equals(coreTitle, ignoreCase = true) &&
            matchesArtist && durationMs > 0 &&
            abs(audioDurationMs - durationMs) <= MAX_COUNTERPART_DURATION_DELTA_MS
        ) videoId else null
    }.distinct().singleOrNull()
}

private fun coreVideoTitle(title: String): String = title.trim().replace(titleDecoration, "").trim()

private fun isOfficialMusicVideoType(type: String?): Boolean = when (type?.uppercase()?.removePrefix("MUSIC_VIDEO_TYPE_")) {
    "OMV", "OFFICIAL_SOURCE_MUSIC_VIDEO", "OFFICIAL_SOURCE" -> true
    else -> false
}

private fun isPlayableMusicVideoType(type: String?): Boolean = when (type?.uppercase()?.removePrefix("MUSIC_VIDEO_TYPE_")) {
    "UGC", "USER_GENERATED", "VIDEO", "VIDEOS", "LIVE" -> true
    else -> isOfficialMusicVideoType(type)
}

private fun searchVideoTitle(title: String, artistNames: List<String>): String {
    val parts = title.split(artistTitleSeparator, limit = 2)
    val withoutArtist = parts.getOrNull(1)?.takeIf {
        artistNames.any { artist -> artist.trim().equals(parts[0].trim(), ignoreCase = true) }
    }
    return coreVideoTitle(withoutArtist ?: title)
}
