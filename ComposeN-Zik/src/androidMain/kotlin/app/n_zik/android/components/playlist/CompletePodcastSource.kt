package app.n_zik.android.components.playlist

import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.v0624.podcasts.BrowsePodcastsResponse0624
import it.fast4x.innertube.models.v0624.podcasts.Continuation
import it.fast4x.innertube.requests.parseContinuationPodcastEpisodes
import it.fast4x.innertube.requests.parsePodcastData
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A malformed continuation cannot be mistaken for a complete source snapshot. */
private fun continuationToken(entries: List<Continuation>?): String? {
    if (entries.isNullOrEmpty()) return null
    require(entries.size == 1) { "Ambiguous podcast continuation" }
    return requireNotNull(entries.single().nextContinuationData?.continuation)
        .also { require(it.isNotBlank()) { "Empty podcast continuation" } }
}

/** Loads every podcast shelf page or fails before the caller can replace saved membership. */
internal suspend fun loadCompletePodcastEpisodes(
    browseId: String,
    browse: suspend (String?, String?) -> BrowsePodcastsResponse0624 = { id, continuation ->
        Innertube.browse(browseId = id, continuation = continuation, setLogin = true).body()
    },
): List<Innertube.Podcast.EpisodeItem> {
    require(browseId.startsWith("MPSP")) { "Not a podcast browse endpoint" }
    currentCoroutineContext().ensureActive()
    val response = browse(browseId, null)
    val renderer = requireNotNull(response.contents?.twoColumnBrowseResultsRenderer)
    val header = renderer.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
        ?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer
    require(!header?.title?.runs?.firstOrNull()?.text.isNullOrBlank()) { "Missing podcast header" }
    val author = header?.straplineTextOne?.runs?.firstOrNull()?.text.orEmpty()
    val shelf = requireNotNull(renderer.secondaryContents?.sectionListRenderer?.contents
        ?.firstOrNull()?.musicShelfRenderer) { "Missing podcast shelf" }
    val episodes = parsePodcastData(requireNotNull(shelf.contents), author).toMutableList()
    val seen = mutableSetOf<String>()
    var next = continuationToken(shelf.continuations)
    while (next != null) {
        currentCoroutineContext().ensureActive()
        require(seen.size < 1000) { "Podcast continuation limit exceeded" }
        require(seen.add(next)) { "Repeated podcast continuation" }
        val continuation = requireNotNull(browse(null, next).continuationContents?.musicShelfContinuation) {
            "Missing podcast continuation shelf"
        }
        episodes.addAll(parseContinuationPodcastEpisodes(requireNotNull(continuation.contents), author))
        next = continuationToken(continuation.continuations)
    }
    currentCoroutineContext().ensureActive()
    require(episodes.all { it.videoId.isNotBlank() && it.title.isNotBlank() }) { "Malformed podcast episode" }
    return episodes
}
