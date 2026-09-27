package app.n_zik.android.playback.services

import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.SongArtistMap
import app.n_zik.android.core.database.Database
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import timber.log.Timber

private const val TAG = "StreamResolver"

/**
 * Video-artist fallback (spec-video-artist-search-fallback.md).
 *
 * For a VIDEO the queue (`Innertube.nextPage`) never returns authors — the
 * byline channel of a video is not a YTM artist page — so a video played from
 * the library gets no artist row + link. This fallback runs when the queue
 * returned 0 authors AND no artist link exists in the DB yet: it does a YTM
 * VIDEO search by title, verifies the result is the SAME video, and copies
 * the video's channel as the artist (Artist row + SongArtistMap link).
 *
 * The empty-page guard in [StreamResolver.mergePageValue] is a prerequisite:
 * without it the copied name would be wiped on the next artist cache fetch.
 */

/**
 * Identity check between a search result and the video being played.
 *
 * STRICT: the result's `videoId` equals ours — the video id is the arbiter of
 * identity. FALLBACK (conservative, all-or-nothing): exact title (ignoreCase,
 * prefix-cleaned by the caller — YTM result names carry no prefix) AND
 * identical stored thumbnail. A null stored title or stored thumbnail
 * disallows the fallback (strict only) — in case of doubt nothing moves.
 * Defensive against a null [Innertube.VideoItem.info] / null thumbnail.
 */
internal fun sameVideo(
    result: Innertube.VideoItem,
    videoId: String,
    storedTitle: String?,
    storedThumbnailUrl: String?
): Boolean {
    // Strict identity first: a matching video id is a perfect match.
    if (result.info?.endpoint?.videoId == videoId) return true
    // Conservative fallback: exact title AND identical stored thumbnail.
    return storedTitle != null &&
        storedTitle.equals(result.info?.name, ignoreCase = true) &&
        storedThumbnailUrl != null &&
        result.thumbnail?.url == storedThumbnailUrl
}

/**
 * The channel to copy: the FIRST author entry of the search result with a
 * non-null `browseId`. Authors without an endpoint are the
 * `VideoItem.from` fallbackAuthor (plain text run, no browseEndpoint) and
 * must be skipped — an artist row without a browseId is unusable.
 */
internal fun channelWithBrowseId(
    result: Innertube.VideoItem
): Innertube.Info<NavigationEndpoint.Endpoint.Browse>? =
    result.authors?.firstOrNull { it.endpoint?.browseId != null }

/**
 * Resolves the artist of a video through a YTM video search and copies the
 * matched video's channel as its artist.
 *
 * Search query: the queue title ([queueTitle]) if non-blank, else the stored
 * DB title ([dbTitle]) with its prefix cleaned. The same cleaned title is
 * used for the [sameVideo] fallback comparison, since YTM result names carry
 * no prefix. A blank query means nothing to search. On a match, in one
 * SUSPENDING transaction (the `[IDs]` loop re-reads the link right after this
 * call returns, so the write must be committed before this function does):
 * the Artist row is created (absent — via insertIgnore, so a concurrent
 * writer committing between the read and the write is never clobbered),
 * repaired (present with a blank name — rows wiped by the empty-page bug) or
 * left alone (present with a non-blank name — the stored name is
 * authoritative); the SongArtistMap link is inserted with insertIgnore
 * (idempotent). The song's own `artistsText` is not touched (owned by the
 * queue/MediaItem upserts + NameConvergence).
 *
 * A match without a usable channel (no browseId, or a blank channel name) is
 * a no-write: a nameless row would be kept by the empty-page guard and the
 * inserted link would stop this fallback from ever re-trying. Network
 * failure or no match: a log, no write, no crash.
 */
internal suspend fun resolveVideoArtistFallback(
    videoId: String,
    queueTitle: String?,
    dbTitle: String?,
    storedThumbnailUrl: String?
) {
    val storedTitle = dbTitle?.let { cleanPrefix(it) }
    val query = queueTitle?.takeIf { it.isNotBlank() } ?: storedTitle?.takeIf { it.isNotBlank() }
    if (query.isNullOrBlank()) {
        Timber.tag(TAG).d("[Video Artist] $videoId: no usable title, video search skipped")
        return
    }

    Timber.tag(TAG).d("[Video Artist] $videoId: video search for '$query'")
    // searchPage is built on runCatchingNonCancellable: it never throws — a
    // failed or cancelled search comes back as a null page, so no try/catch.
    val page = Innertube.searchPage(
        query = query,
        params = Innertube.SearchFilter.Video.value,
        fromMusicShelfRendererContent = Innertube.VideoItem::from
    )
    val results = page?.getOrNull()?.items
    if (results == null) {
        Timber.tag(TAG).w("[Video Artist] $videoId: video search did not return a response, no write")
        return
    }
    val match = results.firstOrNull { sameVideo(it, videoId, storedTitle, storedThumbnailUrl) }
    if (match == null) {
        Timber.tag(TAG).w("[Video Artist] no match for $videoId (${results.size} results)")
        return
    }

    val channel = channelWithBrowseId(match)
    val browseId = channel?.endpoint?.browseId
    if (channel == null || browseId.isNullOrBlank() || channel.name.isNullOrBlank()) {
        // The matched result has no author with a usable channel identity
        // (only the fallback text author, or a blank name) — nothing to
        // copy, no write.
        Timber.tag(TAG).d("[Video Artist] $videoId: matched '${match.info?.name}' has no channel to copy, no write")
        return
    }

    val strictMatch = match.info?.endpoint?.videoId == videoId
    Timber.tag(TAG).d(
        "[Video Artist] $videoId: match (${if (strictMatch) "strict videoId" else "title+thumbnail"}): " +
            "'${match.info?.name}' channel '${channel.name}' ($browseId)"
    )

    Database.transaction {
        val existing = artistTable.findByIdDirect(browseId)
        when {
            existing == null ->
                // insertIgnore instead of a full-row upsert: if a concurrent
                // writer committed this row between the read and this write,
                // the insert is a no-op instead of a clobber. The video
                // thumbnail is NOT the channel avatar.
                artistTable.insertIgnore(Artist(id = browseId, name = channel.name, thumbnailUrl = null))
            existing.name.isNullOrBlank() ->
                // Repairs rows wiped by the earlier empty-page bug (name NULL).
                artistTable.upsert(existing.copy(name = channel.name))
            else ->
                // Non-blank stored name is authoritative: leave the row alone.
                Unit
        }
        songArtistMapTable.insertIgnore(SongArtistMap(songId = videoId, artistId = browseId))
    }
    Timber.tag(TAG).d("[Video Artist] $videoId: channel '${channel.name}' ($browseId) copied")
}
