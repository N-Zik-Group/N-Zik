package app.n_zik.android.bridge.state

import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX

/** Pure conversion from the phone's queue items to the contract's `Track` (§1.1). */
internal object TrackMapping {

    /** Same id convention as `MediaItem.asSong`: media ids may carry a path prefix. */
    fun trackIdOf(mediaId: String): String = mediaId.split("/").lastOrNull { it.isNotEmpty() } ?: mediaId

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
    ): TrackDto {
        val id = trackIdOf(mediaId)
        val source = sourceOf(id)
        return TrackDto(
            id = id,
            title = title.cleanText().orEmpty(),
            artists = artist.cleanText(),
            durationMs = durationTextToMs(durationText),
            source = source,
            // Only an online track can be "available offline" (contract §1.1)
            isDownloaded = source == TrackSource.ONLINE && isDownloaded,
            isLiked = isLiked,
            hasArtwork = hasArtwork,
        )
    }
}
