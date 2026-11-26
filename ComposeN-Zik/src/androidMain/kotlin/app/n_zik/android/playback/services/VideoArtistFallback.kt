package app.n_zik.android.playback.services

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.cleanPrefix
import app.n_zik.android.models.Artist
import app.n_zik.android.models.SongArtistMap
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.isGenericReleaseChannel
import app.n_zik.android.utils.splitArtistNames
import app.n_zik.android.utils.usableTrackArtistName
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.utils.from
import timber.log.Timber

private const val TAG = "StreamResolver"

/** The search result must identify the exact video before its owner can be saved. */
internal fun sameVideo(result: Innertube.VideoItem, videoId: String): Boolean =
    result.info?.endpoint?.videoId == videoId

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

/** Searches by title, then atomically stores the channel of the exact video ID. */
internal suspend fun resolveVideoArtistFallback(
    videoId: String,
    queueTitle: String?,
    dbTitle: String?,
    stillCurrent: () -> Boolean = { true },
) {
    val query = queueTitle?.let(::cleanPrefix)?.takeIf(String::isNotBlank)
        ?: dbTitle?.let(::cleanPrefix)?.takeIf(String::isNotBlank)
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
    val match = results.firstOrNull { sameVideo(it, videoId) }
    if (match == null) {
        Timber.tag(TAG).w("[Video Artist] no match for $videoId (${results.size} results)")
        return
    }

    val channel = channelWithBrowseId(match)
    val browseId = channel?.endpoint?.browseId?.takeIf(String::isNotBlank)
    val name = usableTrackArtistName(channel?.name)
    if (browseId == null || name == null) {
        // The matched result has no author with a usable channel identity
        // (only the fallback text author, or a blank name) — nothing to
//
        // copy, no write.
        Timber.tag(TAG).d("[Video Artist] $videoId: matched '${match.info?.name}' has no channel to copy, no write")
        return
    }

    if (!stillCurrent()) return
    var copied = false
    Database.transaction {
        if (!stillCurrent()) return@transaction
        val linked = artistTable.findBySongIdDirect(videoId)
        if (linked.any { !isGenericReleaseChannel(it.name) || it.id.startsWith(MODIFIED_PREFIX) }) return@transaction
        val song = songTable.findByIdDirect(videoId) ?: return@transaction
        val existing = artistTable.findByIdDirect(browseId)
        val storedName = usableTrackArtistName(existing?.name)
        val canonicalName = storedName ?: name
        val storedCredit = song.artistsText
        val storedNames = usableTrackArtistName(storedCredit).splitArtistNames()
        if (storedCredit?.startsWith(MODIFIED_PREFIX, ignoreCase = true) != true &&
            storedNames.isNotEmpty() && storedNames.none { it.equals(canonicalName, ignoreCase = true) }
        ) return@transaction
        when {
            existing == null -> artistTable.insertIgnore(Artist(id = browseId, name = name))
            storedName == null -> artistTable.upsert(existing.copy(name = name))
        }
        songArtistMapTable.insertIgnore(SongArtistMap(songId = videoId, artistId = browseId))
        linked.filterNot { it.id == browseId }.forEach { songArtistMapTable.deletePairDirect(videoId, it.id) }
        if (storedCredit?.startsWith(MODIFIED_PREFIX, ignoreCase = true) != true &&
            storedNames.size <= 1 && storedCredit != canonicalName
        ) {
            songTable.updateArtists(videoId, canonicalName)
        }
        copied = true
    }
    if (copied) Timber.tag(TAG).d("[Video Artist] %s: copied exact channel %s", videoId, browseId)
}
