package app.n_zik.android.components.playlist

import io.ktor.client.statement.bodyAsText
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.Thumbnail
import app.n_zik.innertube.models.v0624.podcasts.BrowsePodcastsResponse0624
import app.n_zik.innertube.models.v0624.podcasts.Continuation
import app.n_zik.innertube.requests.parseContinuationPodcastEpisodes
import app.n_zik.innertube.requests.parsePodcastData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

// Podcast menu decorations evolve independently of the shelf data used below.
private val podcastResponseJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** Decodes optional upstream enum additions without weakening complete-page validation. */
internal fun decodePodcastBrowseResponse(body: String): BrowsePodcastsResponse0624 =
    podcastResponseJson.decodeFromString<BrowsePodcastsResponse0624>(body)

private suspend fun browsePodcastPage(browseId: String?, continuation: String?): BrowsePodcastsResponse0624 =
    decodePodcastBrowseResponse(
        Innertube.browse(browseId = browseId, continuation = continuation, setLogin = true).bodyAsText()
    )

/** A malformed continuation cannot be mistaken for a complete source snapshot. */
private fun continuationToken(entries: List<Continuation>?): String? {
    if (entries.isNullOrEmpty()) return null
    require(entries.size == 1) { "Ambiguous podcast continuation" }
    return requireNotNull(entries.single().nextContinuationData?.continuation)
        .also { require(it.isNotBlank()) { "Empty podcast continuation" } }
}

internal class PodcastContinuationFailure(
    val availableEpisodes: List<Innertube.Podcast.EpisodeItem>,
    cause: Exception,
) : IllegalStateException("Could not complete podcast pagination", cause)

private fun List<Innertube.Podcast.EpisodeItem>.validatedEpisodes(): List<Innertube.Podcast.EpisodeItem> {
    require(all { it.videoId.isNotBlank() && it.title.isNotBlank() }) { "Malformed podcast episode" }
    return this
}

/** Loads every podcast shelf page or fails before the caller can replace saved membership. */
internal suspend fun loadCompletePodcastEpisodes(
    browseId: String,
    browse: suspend (String?, String?) -> BrowsePodcastsResponse0624 = ::browsePodcastPage,
): List<Innertube.Podcast.EpisodeItem> = loadCompletePodcastPage(browseId, browse).listEpisode

/** Shares complete-snapshot validation between online browsing, imports and saved feeds. */
internal suspend fun loadCompletePodcastPage(
    browseId: String,
    browse: suspend (String?, String?) -> BrowsePodcastsResponse0624 = ::browsePodcastPage,
): Innertube.Podcast = loadCompletePodcastPage(browseId, onEpisodeCount = null, browse = browse)

internal suspend fun loadCompletePodcastPage(
    browseId: String,
    onEpisodeCount: ((Int) -> Unit)?,
    browse: suspend (String?, String?) -> BrowsePodcastsResponse0624 = ::browsePodcastPage,
): Innertube.Podcast {
    require(browseId.startsWith("MPSP")) { "Not a podcast browse endpoint" }
    currentCoroutineContext().ensureActive()
    val response = browse(browseId, null)
    val renderer = requireNotNull(response.contents?.twoColumnBrowseResultsRenderer)
    val header = requireNotNull(renderer.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
        ?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer) { "Missing podcast header" }
    val title = requireNotNull(header.title?.runs?.firstOrNull()?.text)
    require(title.isNotBlank()) { "Missing podcast title" }
    val creator = header.straplineTextOne?.runs.orEmpty().firstOrNull { !it.text.isNullOrBlank() }
    val author = creator?.text
    val authorBrowseId = creator?.navigationEndpoint?.browseEndpoint?.browseID
        ?.takeIf { it.startsWith("UC") && it.length > 2 }
    val shelf = requireNotNull(renderer.secondaryContents?.sectionListRenderer?.contents
        ?.firstOrNull()?.musicShelfRenderer) { "Missing podcast shelf" }
    val episodes = parsePodcastData(requireNotNull(shelf.contents), author).validatedEpisodes().toMutableList()
    val seen = mutableSetOf<String>()
    try {
        var next = continuationToken(shelf.continuations)
        currentCoroutineContext().ensureActive()
        onEpisodeCount?.invoke(episodes.size)
        while (next != null) {
            currentCoroutineContext().ensureActive()
            require(seen.size < 1000) { "Podcast continuation limit exceeded" }
            require(seen.add(next)) { "Repeated podcast continuation" }
            val continuation = requireNotNull(browse(null, next).continuationContents?.musicShelfContinuation) {
                "Missing podcast continuation shelf"
            }
            currentCoroutineContext().ensureActive()
            episodes.addAll(parseContinuationPodcastEpisodes(requireNotNull(continuation.contents), author).validatedEpisodes())
            next = continuationToken(continuation.continuations)
            onEpisodeCount?.invoke(episodes.size)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        throw PodcastContinuationFailure(episodes.toList(), error)
    }
    currentCoroutineContext().ensureActive()
    return Innertube.Podcast(
        title = title,
        author = author,
        authorBrowseId = authorBrowseId,
        authorThumbnail = header.straplineThumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails
            ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url,
        thumbnail = response.background?.musicThumbnailRenderer?.thumbnail?.thumbnails.orEmpty().map {
            Thumbnail(url = it.url.orEmpty(), width = it.width?.toInt(), height = it.height?.toInt())
        },
        description = header.description?.musicDescriptionShelfRenderer?.description?.runs
            ?.joinToString("") { it.text.orEmpty() }.orEmpty(),
        listEpisode = episodes,
    )
}
