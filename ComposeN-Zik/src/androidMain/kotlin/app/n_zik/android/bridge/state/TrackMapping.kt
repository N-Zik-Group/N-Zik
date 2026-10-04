package app.n_zik.android.bridge.state

import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.hasExplicitPrefix
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX

/** Pure conversion from the phone's queue items to the contract's `Track` (§1.1). */
internal object TrackMapping {

    /** Same id convention as `MediaItem.asSong`: media ids may carry a path prefix. */
    fun trackIdOf(mediaId: String): String = mediaId.split("/").lastOrNull { it.isNotEmpty() } ?: mediaId

    /**
     * The phone's `isCustomImage` predicate (its `SongItem.kt` 251-253, its `MiniPlayer.kt` 631):
     * the local custom artworks the phone shows with `Crop` instead of `FillHeight`.
     */
    fun isCustomArtwork(url: String?): Boolean =
        url?.startsWith("file://") == true ||
            url?.contains("app_covers") == true ||
            url?.startsWith(MODIFIED_PREFIX) == true

    fun sourceOf(trackId: String): TrackSource =
        if (trackId.contains(LOCAL_KEY_PREFIX)) TrackSource.LOCAL else TrackSource.ONLINE

    /**
     * Converts the `durationText` extra (`m:ss` or `h:mm:ss`) to milliseconds; `null` when it
     * is missing, malformed or zero (the app stores `00:00` for an unknown duration).
     */
    fun durationTextToMs(durationText: String?): Long? {
        val parts = durationText?.trim()?.split(":") ?: return null
        if (parts.size !in 2..3) return null
        val numbers = parts.map { part -> part.toLongOrNull()?.takeIf { it >= 0 } ?: return null }
        val seconds = numbers.last()
        val minutes = numbers[numbers.size - 2]
        if (seconds >= 60 || (numbers.size == 3 && minutes >= 60)) return null
        val hours = if (numbers.size == 3) numbers.first() else 0L
        val totalMs = ((hours * 60 + minutes) * 60 + seconds) * 1_000
        return totalMs.takeIf { it > 0 }
    }

    /** Media3 metadata may hold the literal text `"null"` (see `MediaItem.asSong`). */
    private fun CharSequence?.cleanText(): String? = this?.toString()?.takeUnless { it == "null" }

    fun track(
        mediaId: String,
        title: CharSequence?,
        artist: CharSequence?,
        hasArtwork: Boolean,
        durationText: String?,
        isLiked: Boolean,
        isDownloaded: Boolean,
        playerDurationMs: Long? = null,
        isExplicit: Boolean = false,
        /** Since 1.7: the like tri-state; derived from [isLiked] when absent. */
        like: TrackLike? = null,
        /** Since 1.7.1: the song's total play time in ms (the library reads; the WS queue keeps 0). */
        totalPlayTimeMs: Long = 0L,
        /** Since 1.7.1: the song's play count (the library reads; the WS queue keeps 0). */
        playCount: Int = 0,
        /** Since 1.7.1: the phone's active download state (the library reads; the WS queue keeps `none`). */
        downloadState: TrackDownloadState = TrackDownloadState.NONE,
        /** Since 1.7.1: the download progress, 0..1, only while [downloadState] is `downloading`. */
        downloadProgress: Float? = null,
        /** Since 1.7.1: in the phone's streaming cache (the library reads; the WS queue keeps false). */
        isCached: Boolean = false,
        /** Since 1.7.2: the track's artwork url, for the [isCustomArtwork] flag (both the queue and the library reads). */
        artworkUrl: String? = null,
    ): TrackDto {
        val id = trackIdOf(mediaId)
        val source = sourceOf(id)
        val rawTitle = title.cleanText().orEmpty()
        val explicitTitle = isExplicitTitle(rawTitle)
        val likeState = like ?: if (isLiked) TrackLike.LIKED else TrackLike.NEUTRAL
        return TrackDto(
            id = id,
            // Queue titles keep `e:` or the explicit mark of `Song.asMediaItem`: sent clean, flagged apart
            title = if (explicitTitle) cleanPrefix(rawTitle) else rawTitle,
            artists = artist.cleanText(),
            // The metadata text first; the player's own duration when the track has none (current item)
            durationMs = durationTextToMs(durationText) ?: playerDurationMs?.takeIf { it > 0 },
            source = source,
            // Only an online track can be "available offline" (contract §1.1)
            isDownloaded = source == TrackSource.ONLINE && isDownloaded,
            isLiked = likeState == TrackLike.LIKED,
            like = likeState,
            hasArtwork = hasArtwork,
            isExplicit = isExplicit || explicitTitle,
            totalPlayTimeMs = totalPlayTimeMs,
            playCount = playCount,
            downloadState = downloadState,
            downloadProgress = downloadProgress,
            isCached = isCached,
            isCustomArtwork = isCustomArtwork(artworkUrl),
        )
    }

    /** `e:` prefix (database title) or the explicit mark `Song.asMediaItem` puts in its place. */
    fun isExplicitTitle(title: String): Boolean = title.hasExplicitPrefix() || title.startsWith(EXPLICIT_MARK)

    // "🅴 " written by `Song.asMediaItem` (rim/utils/Utils.kt:257) and removed by `cleanPrefix`
    private const val EXPLICIT_MARK = "🅴 "
}
